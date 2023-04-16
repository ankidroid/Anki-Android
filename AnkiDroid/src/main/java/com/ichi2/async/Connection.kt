/****************************************************************************************
 * Copyright (c) 2009 Edu Zamora <edu.zasu@gmail.com>                                   *
 * Copyright (c) 2011 Kostas Spyropoulos <inigo.aldana@gmail.com>                       *
 * Copyright (c) 2012 Norbert Nagold <norbert.nagold@gmail.com>                         *
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

package com.ichi2.async

import android.annotation.SuppressLint
import android.content.Context
import android.os.PowerManager
import android.os.PowerManager.WakeLock
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.CollectionHelper
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.CrashReportService
import com.ichi2.anki.R
import com.ichi2.anki.exception.MediaSyncException
import com.ichi2.anki.exception.UnknownHttpResponseException
import com.ichi2.async.Connection.ConflictResolution.*
import com.ichi2.libanki.Collection
import com.ichi2.libanki.sync.CustomSyncServerUrlException
import com.ichi2.libanki.sync.FullSyncer
import com.ichi2.libanki.sync.HostNum
import com.ichi2.libanki.sync.HttpSyncer
import com.ichi2.libanki.sync.MediaSyncer
import com.ichi2.libanki.sync.RemoteMediaServer
import com.ichi2.libanki.sync.RemoteServer
import com.ichi2.libanki.sync.Syncer
import com.ichi2.libanki.sync.Syncer.ConnectionResultType.*
import com.ichi2.utils.KotlinCleanup
import com.ichi2.utils.NetworkUtils
import com.ichi2.utils.Permissions
import okhttp3.Response
import org.json.JSONException
import org.json.JSONObject
import timber.log.Timber
import java.io.IOException

@Suppress("DEPRECATION") // #7108: AsyncTask
@KotlinCleanup("Simplify null comparison, !! -> ?.")
@KotlinCleanup("IDE-lint")
class Connection : BaseAsyncTask<Connection.Payload, Any, Connection.Payload>() {

    @KotlinCleanup("lateinit")
    private var mListener: TaskListener? = null

    /**
     * Before syncing, we acquire a wake lock and then release it once the sync is complete.
     * This ensures that the device remains awake until the sync is complete. Without it,
     * the process will be paused and the sync can fail due to timing conflicts with AnkiWeb.
     */
    private val mWakeLock: WakeLock

    /*
     * Runs on GUI thread
     */
    override fun onCancelled() {
        super.onCancelled()
        Timber.i("Connection onCancelled() method called")
        // Sync has ended so release the wake lock
        mWakeLock.release()
        if (mListener is CancellableTaskListener) {
            (mListener as CancellableTaskListener).onCancelled()
        }
    }

    /*
     * Runs on GUI thread
     */
    @SuppressLint("WakelockTimeout")
    override fun onPreExecute() {
        super.onPreExecute()
        // Acquire the wake lock before syncing to ensure CPU remains on until the sync completes.
        if (Permissions.canUseWakeLock(AnkiDroidApp.instance.applicationContext)) {
            mWakeLock.acquire()
        }
        if (mListener != null) {
            mListener!!.onPreExecute()
        }
    }

    /*
     * Runs on GUI thread
     */
    override fun onPostExecute(result: Payload) {
        super.onPostExecute(result)
        // Sync has ended so release the wake lock
        if (mWakeLock.isHeld) {
            mWakeLock.release()
        }
        if (mListener != null) {
            mListener!!.onPostExecute(result)
        }
    }

    /*
     * Runs on GUI thread
     */
    override fun onProgressUpdate(vararg values: Any) {
        super.onProgressUpdate(*values)
        if (mListener != null) {
            mListener!!.onProgressUpdate(*values)
        }
    }

    override fun doInBackground(vararg arg0: Payload): Payload? {
        super.doInBackground(*arg0)
        require(arg0.size == 1)
        return doOneInBackground(arg0[0])
    }

    private fun doOneInBackground(data: Payload): Payload? {
        return when (data.taskType) {
            LOGIN -> doInBackgroundLogin(data)
            SYNC -> doInBackgroundSync(data)
            else -> null
        }
    }

    @KotlinCleanup("use scoped function")
    private fun doInBackgroundLogin(data: Payload): Payload {
        val username = data.data[0] as String
        val password = data.data[1] as String
        val hostNum = data.data[2] as HostNum
        val server = RemoteServer(this, null, hostNum)
        val ret: Response?
        try {
            ret = server.hostKey(username, password)
        } catch (e: UnknownHttpResponseException) {
            Timber.w(e)
            data.success = false
            data.resultType = ERROR
            data.result = arrayOf(e.responseCode, e.message)
            return data
        } catch (e2: CustomSyncServerUrlException) {
            Timber.w(e2)
            data.success = false
            data.resultType = CUSTOM_SYNC_SERVER_URL
            data.result = arrayOf(e2)
            return data
        } catch (e2: Exception) {
            Timber.w(e2)
            // Ask user to report all bugs which aren't timeout errors
            if (!timeoutOccurred(e2)) {
                CrashReportService.sendExceptionReport(e2, "doInBackgroundLogin")
            }
            data.success = false
            data.resultType = CONNECTION_ERROR
            data.result = arrayOf(e2)
            return data
        }
        var hostkey: String? = null
        var valid = false
        if (ret != null) {
            data.returnType = ret.code
            Timber.d(
                "doInBackgroundLogin - response from server: %d, (%s)",
                data.returnType,
                ret.message
            )
            if (data.returnType == 200) {
                try {
                    val response = JSONObject(ret.body!!.string())
                    hostkey = response.getString("key")
                    valid = hostkey.isNotEmpty()
                } catch (e: JSONException) {
                    Timber.w(e)
                    valid = false
                } catch (e: IllegalStateException) {
                    throw RuntimeException(e)
                } catch (e: IOException) {
                    throw RuntimeException(e)
                } catch (e: NullPointerException) {
                    throw RuntimeException(e)
                }
            }
        } else {
            Timber.e("doInBackgroundLogin - empty response from server")
        }
        if (valid) {
            data.success = true
            data.data = arrayOf(username, hostkey)
        } else {
            data.success = false
        }
        return data
    }

    @KotlinCleanup("Extract strings to a array and use .any { msg.contains }")
    private fun timeoutOccurred(e: Exception): Boolean {
        val msg = e.message ?: return false
        return msg.contains("UnknownHostException") ||
            msg.contains("HttpHostConnectException") ||
            msg.contains("SSLException while building HttpClient") ||
            msg.contains("SocketTimeoutException") ||
            msg.contains("ClientProtocolException") ||
            msg.contains("deadline reached") ||
            msg.contains("interrupted") ||
            msg.contains("Failed to connect") ||
            msg.contains("InterruptedIOException") ||
            msg.contains("stream was reset") ||
            msg.contains("Connection reset") ||
            msg.contains("connection abort") ||
            msg.contains("Broken pipe") ||
            msg.contains("ConnectionShutdownException") ||
            msg.contains("CLEARTEXT communication") ||
            msg.contains("TimeoutException")
    }

    enum class ConflictResolution( // Useful for path /download and /upload
        private val str: String
    ) {
        FULL_DOWNLOAD("download"), FULL_UPLOAD("upload");

        override fun toString(): String {
            return str
        }
    }

    /**
     * In the payload, success means that the sync did occur correctly and that a change did occur.
     * So success can be false without error, if no change occurred at all. */
    @KotlinCleanup("Make colCorruptFullSync a val")
    @KotlinCleanup("Scoped function")
    private fun doInBackgroundSync(data: Payload): Payload {
        isCancellable = true
        Companion.isCancelled = false
        Timber.d("doInBackgroundSync()")
        // Block execution until any previous background task finishes, or timeout after 5s
        val ok = TaskManager.waitToFinish(5)

        // Unique key allowing to identify the user to AnkiWeb without password
        val hkey = data.data[0] as String
        // Whether media should be synced too
        val media = data.data[1] as Boolean
        // If normal sync can't occur, what to do
        val conflictResolution = data.data[2] as ConflictResolution?
        // A number AnkiWeb told us to send back. Probably to choose the best server for the user
        val hostNum = data.data[3] as HostNum
        // Use safe version that catches exceptions so that full sync is still possible
        val col = CollectionHelper.instance.getColSafe(AnkiDroidApp.instance)
        var colCorruptFullSync = false
        if (!CollectionHelper.instance.colIsOpen() || !ok) {
            colCorruptFullSync = if (FULL_DOWNLOAD == conflictResolution) {
                true
            } else {
                return genericError(data)
            }
        }
        return try {
            CollectionHelper.instance.lockCollection()
            val remoteServer = RemoteServer(this, hkey, hostNum)
            val client = Syncer(col!!, remoteServer, hostNum)

            // run sync and check state
            var noChanges = false
            if (conflictResolution == null) {
                Timber.i("Sync - starting sync")
                publishProgress(R.string.sync_prepare_syncing)
                val ret = client.sync(this)
                data.message = client.syncMsg
                if (ret == null) {
                    return genericError(data)
                }
                if (NETWORK_ERROR == ret.first) {
                    data.success = false
                    data.resultType = ret.first
                    data.result = arrayOf(ret.second)
                    return data
                }
                if (NO_CHANGES != ret.first && SUCCESS != ret.first) {
                    data.success = false
                    data.resultType = ret.first
                    data.result = arrayOf(ret.second)
                    // Check if there was a sanity check error
                    if (SANITY_CHECK_ERROR == ret.first) {
                        // Force full sync next time
                        col.modSchemaNoCheck()
                        col.save()
                    }
                    return data
                }
                // save and note success state
                if (NO_CHANGES == ret.first) {
                    // publishProgress(R.string.sync_no_changes_message);
                    noChanges = true
                }
            } else {
                try {
                    // Disable sync cancellation for full-sync
                    isCancellable = false
                    val fullSyncServer = FullSyncer(col, hkey, this, hostNum)
                    when (conflictResolution) {
                        FULL_UPLOAD -> {
                            Timber.i("Sync - fullsync - upload collection")
                            publishProgress(R.string.sync_preparing_full_sync_message)
                            val ret = fullSyncServer.upload()
                            col.reopen()
                            if (ret == null) {
                                return genericError(data)
                            }
                            if (ret.first == ARBITRARY_STRING && ret.second!![0] != HttpSyncer.ANKIWEB_STATUS_OK) {
                                data.success = false
                                data.resultType = ret.first
                                data.result = ret.second!!
                                return data
                            }
                        }
                        FULL_DOWNLOAD -> {
                            Timber.i("Sync - fullsync - download collection")
                            publishProgress(R.string.sync_downloading_message)
                            val ret = fullSyncServer.download()
                            if (SUCCESS == ret) {
                                data.success = true
                                // Note: we don't set afterFullSync here, as that assumes the new schema
                                // has already reopened the collection in the backend.
                                col.reopen()
                            }
                            if (SUCCESS != ret) {
                                Timber.w("Sync - fullsync - download failed")
                                data.success = false
                                data.resultType = ret
                                if (!colCorruptFullSync) {
                                    col.reopen()
                                }
                                return data
                            }
                        }
                    }
                } catch (e: OutOfMemoryError) {
                    Timber.w(e)
                    CrashReportService.sendExceptionReport(e, "doInBackgroundSync-fullSync")
                    data.success = false
                    data.resultType = OUT_OF_MEMORY_ERROR
                    data.result = arrayOfNulls(0)
                    return data
                } catch (e: RuntimeException) {
                    Timber.w(e)
                    if (timeoutOccurred(e)) {
                        data.resultType = CONNECTION_ERROR
                    } else if (USER_ABORTED_SYNC.toString() == e.message) {
                        data.resultType = USER_ABORTED_SYNC
                    } else {
                        CrashReportService.sendExceptionReport(e, "doInBackgroundSync-fullSync")
                        data.resultType = IO_EXCEPTION
                    }
                    data.result = arrayOf(e)
                    data.success = false
                    return data
                }
            }

            // clear undo to avoid non syncing orphans (because undo resets usn too
            if (!noChanges) {
                col.clearUndo()
            }
            // then move on to media sync
            isCancellable = true
            var noMediaChanges = false
            var mediaError: String? = null
            if (media) {
                val mediaServer = RemoteMediaServer(col, hkey, this, hostNum)
                val mediaClient = MediaSyncer(col, mediaServer, this)
                val ret: Pair<Syncer.ConnectionResultType, String?>
                try {
                    Timber.i("Sync - Performing media sync")
                    ret = mediaClient.sync()
                    if (CORRUPT == ret.first) {
                        mediaError = AnkiDroidApp.appResources
                            .getString(R.string.sync_media_db_error)
                        noMediaChanges = true
                    }
                    if (NO_CHANGES == ret.first) {
                        publishProgress(R.string.sync_media_no_changes)
                        noMediaChanges = true
                    }
                    if (MEDIA_SANITY_FAILED == ret.first) {
                        mediaError = AnkiDroidApp.appResources
                            .getString(R.string.sync_media_sanity_failed)
                    } else {
                        publishProgress(R.string.sync_media_success)
                    }
                } catch (e: RuntimeException) {
                    Timber.w(e)
                    if (timeoutOccurred(e)) {
                        data.resultType = CONNECTION_ERROR
                        data.result = arrayOf(e)
                    } else if (USER_ABORTED_SYNC.toString() == e.message) {
                        data.resultType = USER_ABORTED_SYNC
                        data.result = arrayOf(e)
                    }
                    val downloadedCount = mediaClient.getDownloadCount()
                    val uploadedCount = mediaClient.getUploadCount()
                    mediaError = if (downloadedCount == 0 && uploadedCount == 0) {
                        "${AnkiDroidApp.appResources.getString(R.string.sync_media_error)}\n\n${e.localizedMessage}"
                    } else {
                        "${AnkiDroidApp.appResources.getQuantityString(R.plurals.sync_media_partial_downloaded_files,downloadedCount, downloadedCount)}\n" +
                            "${AnkiDroidApp.appResources.getQuantityString(R.plurals.sync_media_partial_uploaded_files,uploadedCount, uploadedCount)}\n" +
                            "\n${e.localizedMessage}"
                    }
                }
            }
            if (noChanges && (!media || noMediaChanges)) {
                // This means that there is no change at all, neither media nor collection. Not that there was an error.
                data.success = false
                data.resultType = NO_CHANGES
                data.result = arrayOfNulls(0)
            } else {
                data.success = true
                data.data = arrayOf(conflictResolution, col, mediaError)
            }
            data
        } catch (e: MediaSyncException) {
            Timber.e("Media sync rejected by server")
            data.success = false
            data.resultType = MEDIA_SYNC_SERVER_ERROR
            data.result = arrayOf(e)
            CrashReportService.sendExceptionReport(e, "doInBackgroundSync")
            data
        } catch (e: UnknownHttpResponseException) {
            Timber.e(e, "doInBackgroundSync -- unknown response code error")
            data.success = false
            val code = e.responseCode
            val msg = e.localizedMessage
            data.resultType = ERROR
            data.result = arrayOf(code, msg)
            data
        } catch (e: Exception) {
            // Global error catcher.
            // Try to give a human readable error, otherwise print the raw error message
            Timber.e(e, "doInBackgroundSync error")
            data.success = false
            if (timeoutOccurred(e)) {
                data.resultType = CONNECTION_ERROR
                data.result = arrayOf(e)
            } else if (USER_ABORTED_SYNC.toString() == e.message) {
                data.resultType = USER_ABORTED_SYNC
                data.result = arrayOf(e)
            } else {
                CrashReportService.sendExceptionReport(e, "doInBackgroundSync")
                data.resultType = ARBITRARY_STRING
                data.result = arrayOf(e.localizedMessage, e)
            }
            data
        } finally {
            Timber.i("Sync Finished - Closing Collection")
            // don't bump mod time unless we explicitly save
            CollectionManager.closeCollectionBlocking(false)
            CollectionHelper.instance.unlockCollection()
        }
    }

    // #7108: AsyncTask
    fun publishProgress(id: Int) {
        super.publishProgress(id)
    }

    // #7108: AsyncTask
    fun publishProgress(message: String?) {
        super.publishProgress(message)
    }

    // #7108: AsyncTask
    fun publishProgress(id: Int, up: Long, down: Long) {
        super.publishProgress(id, up, down)
    }

    interface TaskListener {
        fun onPreExecute()
        fun onProgressUpdate(vararg values: Any?)
        fun onPostExecute(data: Payload)
        fun onDisconnected()
    }

    interface CancellableTaskListener : TaskListener {
        fun onCancelled()
    }

    class Payload(var data: Array<Any?>) {
        var taskType = 0
        var resultType: Syncer.ConnectionResultType? = null
        var result: Array<Any?> = arrayOf()
        var success = true
        var returnType = 0
        var exception: Exception? = null
        var message: String? = null
        var col: Collection? = null

        @KotlinCleanup("use formatted string")
        override fun toString(): String {
            return "Payload{" +
                "mTaskType=" + taskType +
                ", data=" + data.contentToString() +
                ", resultType=" + resultType +
                ", result=" + result.contentToString() +
                ", success=" + success +
                ", returnType=" + returnType +
                ", exception=" + exception +
                ", message='" + message + '\'' +
                '}'
        }
    }

    companion object {
        private const val LOGIN = 0
        private const val SYNC = 1
        const val CONN_TIMEOUT = 30000
        private var sInstance: Connection? = null

        @get:Synchronized
        var isCancelled = false
            private set

        @get:Synchronized
        var isCancellable = false
            private set
        var allowLoginSyncOnNoConnection = false

        // #7108: AsyncTask
        @KotlinCleanup("Scoped function")
        private fun launchConnectionTask(listener: TaskListener, data: Payload): Connection? {
            if (!NetworkUtils.isOnline && !allowLoginSyncOnNoConnection) {
                data.success = false
                listener.onDisconnected()
                return null
            }
            try {
                if (sInstance != null && sInstance!!.status != Status.FINISHED) {
                    sInstance!!.get()
                }
            } catch (e: Exception) {
                Timber.w(e)
            }
            sInstance = Connection()
            sInstance!!.mListener = listener
            sInstance!!.execute(data)
            return sInstance
        }

        fun login(listener: TaskListener, data: Payload): Connection? {
            data.taskType = LOGIN
            return launchConnectionTask(listener, data)
        }

        fun sync(listener: TaskListener, data: Payload): Connection? {
            data.taskType = SYNC
            return launchConnectionTask(listener, data)
        }

        /**
         * Add generic error value to the payload
         * @param data Some payload that should be transformed
         * @return the original payload
         */
        private fun genericError(data: Payload): Payload {
            return data.apply {
                success = false
                resultType = GENERIC_ERROR
                result = arrayOfNulls(0)
            }
        }

        @Synchronized // #7108: AsyncTask
        fun cancel() {
            Timber.d("Cancelled Connection task")
            sInstance!!.cancel(true)
            isCancelled = true
        }
    }

    init {
        val context = AnkiDroidApp.instance.applicationContext
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        mWakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            AnkiDroidApp.appResources.getString(R.string.app_name) + ":Connection"
        )
    }
}
