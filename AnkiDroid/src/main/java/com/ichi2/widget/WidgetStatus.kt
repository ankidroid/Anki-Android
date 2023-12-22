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
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.MetaDB
import com.ichi2.anki.preferences.Preferences
import com.ichi2.anki.preferences.sharedPrefs
import com.ichi2.libanki.sched.Counts
import com.ichi2.widget.AnkiDroidWidgetSmall.UpdateService
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import timber.log.Timber

data class SmallWidgetStatus(var due: Int, var eta: Int)

/**
 * The status of the widget.
 */
object WidgetStatus {
    private var enabled = false
    private var status = SmallWidgetStatus(0, 0)
    private var updateJob: Job? = null

    /**
     * Request the widget to update its status.
     * TODO Mike - we can reduce battery usage by widget users by removing updatePeriodMillis from metadata
     *             and replacing it with an alarm we set so device doesn't wake to update the widget, see:
     *             https://developer.android.com/guide/topics/appwidgets/#MetaData
     */
    fun updateInBackground(context: Context) {
        val preferences = context.sharedPrefs()
        enabled = preferences.getBoolean("widgetSmallEnabled", false)
        val notificationEnabled =
            preferences.getString(Preferences.MINIMUM_CARDS_DUE_FOR_NOTIFICATION, "1000001")!!
                .toInt() < 1000000
        val canExecuteTask = updateJob == null || updateJob?.isActive == false
        if ((enabled || notificationEnabled) && canExecuteTask) {
            Timber.d("WidgetStatus.update(): updating")
            updateJob = launchUpdateJob(context)
        } else {
            Timber.d("WidgetStatus.update(): already running or not enabled")
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun launchUpdateJob(context: Context): Job {
        return GlobalScope.launch {
            try {
                updateStatus(context)
            } catch (exc: java.lang.Exception) {
                Timber.w("failure in widget update: %s", exc)
            }
        }
    }

    suspend fun updateStatus(context: Context) {
        if (!AnkiDroidApp.isSdCardMounted) {
            return
        }
        updateCounts()
        MetaDB.storeSmallWidgetStatus(context, status)
        if (enabled) {
            UpdateService().doUpdate(context)
        }
        (context.applicationContext as AnkiDroidApp).scheduleNotification()
    }

    /** Returns the status of each of the decks.  */
    fun fetchSmall(context: Context): IntArray {
        return MetaDB.getWidgetSmallStatus(context)
    }

    fun fetchDue(context: Context): Int {
        return MetaDB.getNotificationStatus(context)
    }

    private suspend fun updateCounts() {
        val total = Counts()
        status =
            CollectionManager.withCol {
                // Only count the top-level decks in the total
                val nodes = sched.deckDueTree().children
                for (node in nodes) {
                    total.addNew(node.newCount)
                    total.addLrn(node.lrnCount)
                    total.addRev(node.revCount)
                }
                val eta = sched.eta(total, false)
                SmallWidgetStatus(total.count(), eta)
            }
    }
}
