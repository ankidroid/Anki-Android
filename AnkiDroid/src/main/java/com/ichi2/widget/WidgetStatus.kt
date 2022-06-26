/***************************************************************************************
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 *                                                                                      *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/

package com.ichi2.widget

import android.content.Context
import android.util.Pair
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.CollectionHelper
import com.ichi2.anki.MetaDB
import com.ichi2.anki.Preferences
import com.ichi2.libanki.sched.Counts
import com.ichi2.utils.KotlinCleanup
import com.ichi2.widget.AnkiDroidWidgetSmall.UpdateService
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * The status of the widget.
 */
object WidgetStatus {
    private lateinit var updateDeckStatusJob: Job
    private lateinit var backgroundDispatcher: CoroutineDispatcher
    private lateinit var mainDispatcher: CoroutineDispatcher

    private var sSmallWidgetStatus = Pair(0, 0)
    private var sSmallWidgetEnabled = false

    /**
     * Request the widget to update its status.
     * TODO Mike - we can reduce battery usage by widget users by removing updatePeriodMillis from metadata
     *             and replacing it with an alarm we set so device doesn't wake to update the widget, see:
     *             https://developer.android.com/guide/topics/appwidgets/#MetaData
     */
    fun update(
        context: Context,
        scope: CoroutineScope,
        mainDispatcher: CoroutineDispatcher = Dispatchers.Main,
        backgroundDispatcher: CoroutineDispatcher = Dispatchers.Default
    ) {
        this.mainDispatcher = mainDispatcher
        this.backgroundDispatcher = backgroundDispatcher
        val preferences = AnkiDroidApp.getSharedPrefs(context)
        sSmallWidgetEnabled = preferences.getBoolean("widgetSmallEnabled", false)
        val notificationEnabled = preferences.getString(Preferences.MINIMUM_CARDS_DUE_FOR_NOTIFICATION, "1000001")!!.toInt() < 1000000
        // A new job can execute only if either it hasn't been initialized yet or
        // if initialized, it is not currently running i.e finished or cancelled
        val canExecuteJob = !this::updateDeckStatusJob.isInitialized || !updateDeckStatusJob.isActive
        if ((sSmallWidgetEnabled || notificationEnabled) && canExecuteJob) {
            updateDeckStatusJob = scope.launch { updateDeckStatus(context) }
        } else {
            Timber.d("WidgetStatus.update(): already running or not enabled")
        }
    }

    /** Returns the status of each of the decks.  */
    @JvmStatic
    @KotlinCleanup("make context non-null")
    fun fetchSmall(context: Context?): IntArray {
        return MetaDB.getWidgetSmallStatus(context!!)
    }

    @KotlinCleanup("make context non-null")
    fun fetchDue(context: Context?): Int {
        return MetaDB.getNotificationStatus(context!!)
    }

    private suspend fun updateDeckStatus(context: Context) = withContext(backgroundDispatcher) {
        Timber.d("WidgetStatus.UpdateDeckStatusAsyncTask.doInBackground()")
        if (AnkiDroidApp.isSdCardMounted()) {
            try {
                updateCounts(context)
            } catch (e: Exception) {
                Timber.e(e, "Could not update widget")
            }
        }
        withContext(mainDispatcher) {
            Timber.d("WidgetStatus.UpdateDeckStatusAsyncTask.onPostExecute()")
            MetaDB.storeSmallWidgetStatus(context, sSmallWidgetStatus)
            if (sSmallWidgetEnabled) {
                UpdateService().doUpdate(context)
            }
            (context.applicationContext as? AnkiDroidApp)?.scheduleNotification()
        }
    }

    private fun updateCounts(context: Context) {
        val total = Counts()
        val col = CollectionHelper.getInstance().getCol(context)
        // Ensure queues are reset if we cross over to the next day.
        col.sched._checkDay()

        // Only count the top-level decks in the total
        val nodes = col.sched.deckDueTree().map { it.value }
        for (node in nodes) {
            total.addNew(node.newCount)
            total.addLrn(node.lrnCount)
            total.addRev(node.revCount)
        }
        val eta = col.sched.eta(total, false)
        sSmallWidgetStatus = Pair(total.count(), eta)
    }
}
