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
import com.ichi2.anki.CollectionHelper
import com.ichi2.anki.MetaDB
import com.ichi2.anki.preferences.Preferences
import com.ichi2.async.BaseAsyncTask
import com.ichi2.libanki.sched.Counts
import com.ichi2.utils.KotlinCleanup
import com.ichi2.widget.AnkiDroidWidgetSmall.UpdateService
import timber.log.Timber

/**
 * The status of the widget.
 */
object WidgetStatus {
    private var sSmallWidgetEnabled = false

    @Suppress("deprecation") // #7108: AsyncTask
    private var sUpdateDeckStatusAsyncTask: android.os.AsyncTask<Context?, Void?, Context?>? = null

    /**
     * Request the widget to update its status.
     * TODO Mike - we can reduce battery usage by widget users by removing updatePeriodMillis from metadata
     *             and replacing it with an alarm we set so device doesn't wake to update the widget, see:
     *             https://developer.android.com/guide/topics/appwidgets/#MetaData
     */
    @Suppress("deprecation") // #7108: AsyncTask
    fun update(context: Context?) {
        val preferences = AnkiDroidApp.getSharedPrefs(context)
        sSmallWidgetEnabled = preferences.getBoolean("widgetSmallEnabled", false)
        val notificationEnabled = preferences.getString(Preferences.MINIMUM_CARDS_DUE_FOR_NOTIFICATION, "1000001")!!.toInt() < 1000000
        val canExecuteTask = sUpdateDeckStatusAsyncTask == null || sUpdateDeckStatusAsyncTask!!.status == android.os.AsyncTask.Status.FINISHED
        if ((sSmallWidgetEnabled || notificationEnabled) && canExecuteTask) {
            Timber.d("WidgetStatus.update(): updating")
            sUpdateDeckStatusAsyncTask = UpdateDeckStatusAsyncTask()
            sUpdateDeckStatusAsyncTask!!.execute(context)
        } else {
            Timber.d("WidgetStatus.update(): already running or not enabled")
        }
    }

    /** Returns the status of each of the decks.  */
    fun fetchSmall(context: Context): IntArray {
        return MetaDB.getWidgetSmallStatus(context)
    }

    fun fetchDue(context: Context): Int {
        return MetaDB.getNotificationStatus(context)
    }

    private class UpdateDeckStatusAsyncTask : BaseAsyncTask<Context?, Void?, Context?>() {
        @Suppress("deprecation") // #7108: AsyncTask
        override fun doInBackground(vararg arg0: Context?): Context? {
            super.doInBackground(*arg0)
            Timber.d("WidgetStatus.UpdateDeckStatusAsyncTask.doInBackground()")
            val context = arg0[0]
            if (!AnkiDroidApp.isSdCardMounted) {
                return context
            }
            try {
                updateCounts(context!!)
            } catch (e: Exception) {
                Timber.e(e, "Could not update widget")
            }
            return context
        }

        @Suppress("deprecation") // #7108: AsyncTask
        @KotlinCleanup("make result non-null")
        override fun onPostExecute(result: Context?) {
            super.onPostExecute(result)
            Timber.d("WidgetStatus.UpdateDeckStatusAsyncTask.onPostExecute()")
            MetaDB.storeSmallWidgetStatus(result!!, sSmallWidgetStatus)
            if (sSmallWidgetEnabled) {
                UpdateService().doUpdate(result)
            }
            (result.applicationContext as? AnkiDroidApp)?.scheduleNotification()
        }

        private fun updateCounts(context: Context) {
            val total = Counts()
            val col = CollectionHelper.instance.getCol(context)!!

            // Only count the top-level decks in the total
            val nodes = col.sched.deckDueTree(col).map { it.value }
            for (node in nodes) {
                total.addNew(node.newCount)
                total.addLrn(node.lrnCount)
                total.addRev(node.revCount)
            }
            val eta = col.sched.eta(col, total, false)
            sSmallWidgetStatus = Pair(total.count(), eta)
        }

        companion object {
            // due, eta
            private var sSmallWidgetStatus = Pair(0, 0)
        }
    }
}
