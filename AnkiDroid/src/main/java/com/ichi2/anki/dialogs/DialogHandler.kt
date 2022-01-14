/****************************************************************************************
 * Copyright (c) 2015 Timothy Rae <perceptualchaos2@gmail.com>                          *
 *                                                                                      *
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

package com.ichi2.anki.dialogs

import android.os.Handler
import android.os.Message
import androidx.annotation.VisibleForTesting
import com.ichi2.anki.*
import com.ichi2.anki.analytics.UsageAnalytics
import com.ichi2.async.Connection
import com.ichi2.utils.HandlerUtils.getDefaultLooper
import com.ichi2.utils.KotlinCleanup
import timber.log.Timber
import java.lang.ref.WeakReference
import java.util.*
import kotlin.math.max
import kotlin.math.min

/**
 * We're not allowed to commit fragment transactions from Loader.onLoadCompleted(),
 * and it's unsafe to commit them from an AsyncTask onComplete event, so we work
 * around this by using a message handler.
 */
@KotlinCleanup("for better null handling")
class DialogHandler(activity: AnkiActivity) : Handler(getDefaultLooper()) {
    // Use weak reference to main activity to prevent leaking the activity when it's closed
    val mActivity: WeakReference<AnkiActivity> = WeakReference(activity)
    override fun handleMessage(msg: Message) {
        val msgData = msg.data
        val messageName = MESSAGE_NAME_LIST[msg.what]
        UsageAnalytics.sendAnalyticsScreenView(messageName)
        Timber.i("Handling Message: %s", messageName)
        if (msg.what == MSG_SHOW_COLLECTION_LOADING_ERROR_DIALOG) {
            // Collection could not be opened
            (mActivity.get() as DeckPicker?)!!.showDatabaseErrorDialog(DatabaseErrorDialog.DIALOG_LOAD_FAILED)
        } else if (msg.what == MSG_SHOW_COLLECTION_IMPORT_REPLACE_DIALOG) {
            // Handle import of collection package APKG
            (mActivity.get() as DeckPicker?)!!.showImportDialog(ImportDialog.DIALOG_IMPORT_REPLACE_CONFIRM, msgData.getString("importPath"))
        } else if (msg.what == MSG_SHOW_COLLECTION_IMPORT_ADD_DIALOG) {
            // Handle import of deck package APKG
            (mActivity.get() as DeckPicker?)!!.showImportDialog(ImportDialog.DIALOG_IMPORT_ADD_CONFIRM, msgData.getString("importPath"))
        } else if (msg.what == MSG_SHOW_SYNC_ERROR_DIALOG) {
            val id = msgData.getInt("dialogType")
            val message = msgData.getString("dialogMessage")
            (mActivity.get() as DeckPicker?)!!.showSyncErrorDialog(id, message)
        } else if (msg.what == MSG_SHOW_MEDIA_CHECK_COMPLETE_DIALOG) {
            // Media check results
            val id = msgData.getInt("dialogType")
            if (id != MediaCheckDialog.DIALOG_CONFIRM_MEDIA_CHECK) {
                val checkList: MutableList<List<String>> = ArrayList(3)
                checkList.add(msgData.getStringArrayList("nohave")!!)
                checkList.add(msgData.getStringArrayList("unused")!!)
                checkList.add(msgData.getStringArrayList("invalid")!!)
                (mActivity.get() as DeckPicker?)!!.showMediaCheckDialog(id, checkList)
            }
        } else if (msg.what == MSG_SHOW_DATABASE_ERROR_DIALOG) {
            // Database error dialog
            (mActivity.get() as DeckPicker?)!!.showDatabaseErrorDialog(msgData.getInt("dialogType"))
        } else if (msg.what == MSG_SHOW_FORCE_FULL_SYNC_DIALOG) {
            // Confirmation dialog for forcing full sync
            val dialog = ConfirmationDialog()
            val confirm = Runnable {
                // Bypass the check once the user confirms
                CollectionHelper.getInstance().getCol(AnkiDroidApp.getInstance()).modSchemaNoCheck()
            }
            dialog.setConfirm(confirm)
            dialog.setArgs(msgData.getString("message"))
            mActivity.get()!!.showDialogFragment(dialog)
        } else if (msg.what == MSG_DO_SYNC) {
            val preferences = AnkiDroidApp.getSharedPrefs(mActivity.get())
            val res = mActivity.get()!!.resources
            val col = mActivity.get()!!.col
            val hkey = preferences.getString("hkey", "")
            val millisecondsSinceLastSync = col.time.intTimeMS() - preferences.getLong("lastSyncTime", 0)
            val limited = millisecondsSinceLastSync < INTENT_SYNC_MIN_INTERVAL
            if (!limited && hkey!!.isNotEmpty() && Connection.isOnline()) {
                (mActivity.get() as DeckPicker?)!!.sync()
            } else {
                val err = res.getString(R.string.sync_error)
                if (limited) {
                    val remainingTimeInSeconds = max((INTENT_SYNC_MIN_INTERVAL - millisecondsSinceLastSync) / 1000, 1)
                    // getQuantityString needs an int
                    val remaining = min(Int.MAX_VALUE.toLong(), remainingTimeInSeconds).toInt()
                    val message = res.getQuantityString(R.plurals.sync_automatic_sync_needs_more_time, remaining, remaining)
                    mActivity.get()!!.showSimpleNotification(err, message, NotificationChannels.Channel.SYNC)
                } else {
                    mActivity.get()!!.showSimpleNotification(err, res.getString(R.string.youre_offline), NotificationChannels.Channel.SYNC)
                }
            }
            mActivity.get()!!.finishWithoutAnimation()
        }
    }

    /**
     * Read and handle Message which was stored via storeMessage()
     */
    fun readMessage() {
        Timber.d("Reading persistent message")
        if (sStoredMessage != null) {
            Timber.i("Dispatching persistent message: %d", sStoredMessage!!.what)
            sendMessage(sStoredMessage!!)
        }
        sStoredMessage = null
    }

    companion object {
        const val INTENT_SYNC_MIN_INTERVAL = (
            2 * 60000 // 2min minimum sync interval
            ).toLong()

        /**
         * Handler messages
         */
        const val MSG_SHOW_COLLECTION_LOADING_ERROR_DIALOG = 0
        const val MSG_SHOW_COLLECTION_IMPORT_REPLACE_DIALOG = 1
        const val MSG_SHOW_COLLECTION_IMPORT_ADD_DIALOG = 2
        const val MSG_SHOW_SYNC_ERROR_DIALOG = 3
        const val MSG_SHOW_MEDIA_CHECK_COMPLETE_DIALOG = 5
        const val MSG_SHOW_DATABASE_ERROR_DIALOG = 6
        const val MSG_SHOW_FORCE_FULL_SYNC_DIALOG = 7
        const val MSG_DO_SYNC = 8
        val MESSAGE_NAME_LIST = arrayOf(
            "CollectionLoadErrorDialog",
            "ImportReplaceDialog",
            "ImportAddDialog",
            "SyncErrorDialog",
            "ExportCompleteDialog",
            "MediaCheckCompleteDialog",
            "DatabaseErrorDialog",
            "ForceFullSyncDialog",
            "DoSyncDialog"
        )
        private var sStoredMessage: Message? = null

        /**
         * Store a persistent message to static variable
         * @param message Message to store
         */
        @JvmStatic
        fun storeMessage(message: Message?) {
            Timber.d("Storing persistent message")
            sStoredMessage = message
        }

        @VisibleForTesting(otherwise = VisibleForTesting.NONE)
        fun discardMessage() {
            sStoredMessage = null
        }
    }
}
