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

package com.ichi2.async;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.os.PowerManager;
import android.util.Pair;

import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.CollectionHelper;
import com.ichi2.anki.R;
import com.ichi2.anki.exception.MediaSyncException;
import com.ichi2.anki.exception.UnknownHttpResponseException;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.sync.CustomSyncServerUrlException;
import com.ichi2.libanki.sync.FullSyncer;
import com.ichi2.libanki.sync.HostNum;
import com.ichi2.libanki.sync.HttpSyncer;
import com.ichi2.libanki.sync.MediaSyncer;
import com.ichi2.libanki.sync.RemoteMediaServer;
import com.ichi2.libanki.sync.RemoteServer;
import com.ichi2.libanki.sync.Syncer;
import com.ichi2.utils.Permissions;

import com.ichi2.utils.JSONException;
import com.ichi2.utils.JSONObject;

import java.io.IOException;
import java.util.Arrays;

import androidx.annotation.NonNull;
import okhttp3.Response;
import timber.log.Timber;

import static com.ichi2.libanki.sync.Syncer.ConnectionResultType;
import static com.ichi2.libanki.sync.Syncer.ConnectionResultType.*;
import static com.ichi2.async.Connection.ConflictResolution.*;

public class Connection extends BaseAsyncTask<Connection.Payload, Object, Connection.Payload> {

    private static final int LOGIN = 0;
    private static final int SYNC = 1;
    public static final int CONN_TIMEOUT = 30000;


    private static Connection sInstance;
    private TaskListener mListener;
    private static boolean sIsCancelled;
    private static boolean sIsCancellable;

    private static boolean sAllowSyncOnNoConnection;

    /**
     * Before syncing, we acquire a wake lock and then release it once the sync is complete.
     * This ensures that the device remains awake until the sync is complete. Without it,
     * the process will be paused and the sync can fail due to timing conflicts with AnkiWeb.
     */
    private final PowerManager.WakeLock mWakeLock;

    public static synchronized boolean getIsCancelled() {
        return sIsCancelled;
    }

    public Connection() {
        sIsCancelled = false;
        sIsCancellable = false;
        Context context = AnkiDroidApp.getInstance().getApplicationContext();
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                AnkiDroidApp.getAppResources().getString(R.string.app_name) + ":Connection");
    }

    @SuppressWarnings("deprecation") // #7108: AsyncTask
    private static Connection launchConnectionTask(TaskListener listener, Payload data) {

        if (!isOnline()) {
            data.success = false;
            listener.onDisconnected();
            return null;
        }

        try {
            if ((sInstance != null) && (sInstance.getStatus() != android.os.AsyncTask.Status.FINISHED)) {
                sInstance.get();
            }
        } catch (Exception e) {
            Timber.w(e);
        }

        sInstance = new Connection();
        sInstance.mListener = listener;

        sInstance.execute(data);
        return sInstance;
    }


    public static boolean getAllowSyncOnNoConnection() {
        return sAllowSyncOnNoConnection;
    }


    public static void setAllowSyncOnNoConnection(boolean value) {
        sAllowSyncOnNoConnection = value;
    }


    /*
     * Runs on GUI thread
     */
    @Override
    protected void onCancelled() {
        super.onCancelled();
        Timber.i("Connection onCancelled() method called");
        // Sync has ended so release the wake lock
        mWakeLock.release();
        if (mListener instanceof CancellableTaskListener) {
            ((CancellableTaskListener) mListener).onCancelled();
        }
    }


    /*
     * Runs on GUI thread
     */
    @SuppressLint("WakelockTimeout")
    @Override
    protected void onPreExecute() {
        super.onPreExecute();
        // Acquire the wake lock before syncing to ensure CPU remains on until the sync completes.
        if (Permissions.canUseWakeLock(AnkiDroidApp.getInstance().getApplicationContext())) {
            mWakeLock.acquire();
        }
        if (mListener != null) {
            mListener.onPreExecute();
        }
    }


    /*
     * Runs on GUI thread
     */
    @SuppressWarnings("deprecation") // #7108: AsyncTask
    @Override
    protected void onPostExecute(Payload data) {
        super.onPostExecute(data);
        // Sync has ended so release the wake lock
        if (mWakeLock.isHeld()) {
            mWakeLock.release();
        }
        if (mListener != null) {
            mListener.onPostExecute(data);
        }
    }


    /*
     * Runs on GUI thread
     */
    @Override
    protected void onProgressUpdate(Object... values) {
        super.onProgressUpdate(values);
        if (mListener != null) {
            mListener.onProgressUpdate(values);
        }
    }


    public static Connection login(TaskListener listener, Payload data) {
        data.mTaskType = LOGIN;
        return launchConnectionTask(listener, data);
    }


    public static Connection sync(TaskListener listener, Payload data) {
        data.mTaskType = SYNC;
        return launchConnectionTask(listener, data);
    }


    @SuppressWarnings("deprecation") // #7108: AsyncTask
    @Override
    protected Payload doInBackground(Payload... params) {
        super.doInBackground(params);
        if (params.length != 1) {
            throw new IllegalArgumentException();
        }
        return doOneInBackground(params[0]);
    }


    private Payload doOneInBackground(Payload data) {
        switch (data.mTaskType) {
            case LOGIN:
                return doInBackgroundLogin(data);

            case SYNC:
                return doInBackgroundSync(data);

            default:
                return null;
        }
    }


    private Payload doInBackgroundLogin(Payload data) {
        String username = (String) data.data[0];
        String password = (String) data.data[1];
        HostNum hostNum = (HostNum) data.data[2];
        RemoteServer server = new RemoteServer(this, null, hostNum);
        Response ret;
        try {
            ret = server.hostKey(username, password);
        } catch (UnknownHttpResponseException e) {
            Timber.w(e);
            data.success = false;
            data.resultType = ERROR;
            data.result = new Object[] {e.getResponseCode(), e.getMessage()};
            return data;
        } catch (CustomSyncServerUrlException e2) {
            Timber.w(e2);
            data.success = false;
            data.resultType = CUSTOM_SYNC_SERVER_URL;
            data.result = new Object[] {e2};
            return data;
        } catch (Exception e2) {
            Timber.w(e2);
            // Ask user to report all bugs which aren't timeout errors
            if (!timeoutOccurred(e2)) {
                AnkiDroidApp.sendExceptionReport(e2, "doInBackgroundLogin");
            }
            data.success = false;
            data.resultType = CONNECTION_ERROR;
            data.result = new Object[] {e2};
            return data;
        }
        String hostkey = null;
        boolean valid = false;
        if (ret != null) {
            data.returnType = ret.code();
            Timber.d("doInBackgroundLogin - response from server: %d, (%s)", data.returnType, ret.message());
            if (data.returnType == 200) {
                try {
                    JSONObject response = new JSONObject(ret.body().string());
                    hostkey = response.getString("key");
                    valid = (hostkey != null) && (hostkey.length() > 0);
                } catch (JSONException e) {
                    Timber.w(e);
                    valid = false;
                } catch (IllegalStateException | IOException | NullPointerException e) {
                    throw new RuntimeException(e);
                }
            }
        } else {
            Timber.e("doInBackgroundLogin - empty response from server");
        }
        if (valid) {
            data.success = true;
            data.data = new String[] { username, hostkey };
        } else {
            data.success = false;
        }
        return data;
    }


    private boolean timeoutOccurred(Exception e) {
        String msg = e.getMessage();
        if (msg == null) {
            return false;
        }
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
                msg.contains("TimeoutException");
    }

    public enum ConflictResolution {
        FULL_DOWNLOAD("download"),
        FULL_UPLOAD("upload");

        // Useful for path /download and /upload
        @NonNull private final String mString;
        ConflictResolution(@NonNull String string) {
            mString = string;
        }

        public @NonNull String toString() {
            return mString;
        }
    }


    /**
     * Add generic error value to the payload
     * @param data Some payload that should be transformed
     * @return the original payload
     */
    private static Payload returnGenericError(Payload data) {
        data.success = false;
        data.resultType = GENERIC_ERROR;
        data.result = new Object[0];
        return data;
    }

    /**
     * In the payload, success means that the sync did occur correctly and that a change did occur.
     * So success can be false without error, if no change occurred at all.*/
    private Payload doInBackgroundSync(Payload data) {
        sIsCancellable = true;
        Timber.d("doInBackgroundSync()");
        // Block execution until any previous background task finishes, or timeout after 5s
        boolean ok = TaskManager.waitToFinish(5);

        // Unique key allowing to identify the user to AnkiWeb without password
        String hkey = (String) data.data[0];
        // Whether media should be synced too
        boolean media = (Boolean) data.data[1];
        // If normal sync can't occur, what to do
        ConflictResolution conflictResolution = (ConflictResolution) data.data[2];
        // A number AnkiWeb told us to send back. Probably to choose the best server for the user
        HostNum hostNum = (HostNum) data.data[3];
        // Use safe version that catches exceptions so that full sync is still possible
        Collection col = CollectionHelper.getInstance().getColSafe(AnkiDroidApp.getInstance());

        boolean colCorruptFullSync = false;
        if (!CollectionHelper.getInstance().colIsOpen() || !ok) {
            if (FULL_DOWNLOAD == conflictResolution) {
                colCorruptFullSync = true;
            } else {
                return returnGenericError(data);
            }
        }
        try {
            CollectionHelper.getInstance().lockCollection();
            RemoteServer remoteServer = new RemoteServer(this, hkey, hostNum);
            Syncer client = new Syncer(col, remoteServer, hostNum);

            // run sync and check state
            boolean noChanges = false;
            if (conflictResolution == null) {
                Timber.i("Sync - starting sync");
                publishProgress(R.string.sync_prepare_syncing);
                Pair<ConnectionResultType, Object> ret = client.sync(this);
                data.message = client.getSyncMsg();
                if (ret == null) {
                    return returnGenericError(data);
                }
                if (NO_CHANGES != ret.first && SUCCESS != ret.first) {
                    data.success = false;
                    data.resultType = ret.first;
                    data.result = new Object[]{ret.second};
                    // Check if there was a sanity check error
                    if (SANITY_CHECK_ERROR == ret.first) {
                        // Force full sync next time
                        col.modSchemaNoCheck();
                        col.save();
                    }
                    return data;
                }
                // save and note success state
                if (NO_CHANGES == ret.first) {
                    // publishProgress(R.string.sync_no_changes_message);
                    noChanges = true;
                }
            } else {
                try {
                    // Disable sync cancellation for full-sync
                    sIsCancellable = false;
                    FullSyncer fullSyncServer = new FullSyncer(col, hkey, this, hostNum);
                    switch (conflictResolution) {
                    case FULL_UPLOAD: {
                        Timber.i("Sync - fullsync - upload collection");
                        publishProgress(R.string.sync_preparing_full_sync_message);
                        Pair<ConnectionResultType, Object[]> ret = fullSyncServer.upload();
                        col.reopen();
                        if (ret == null) {
                            return returnGenericError(data);
                        }
                        if (ret.first == ARBITRARY_STRING && !ret.second[0].equals(HttpSyncer.ANKIWEB_STATUS_OK)) {
                            data.success = false;
                            data.resultType = ret.first;
                            data.result = ret.second;
                            return data;
                        }
                        break;
                    }
                    case FULL_DOWNLOAD: {
                        Timber.i("Sync - fullsync - download collection");
                        publishProgress(R.string.sync_downloading_message);
                        ConnectionResultType ret = fullSyncServer.download();
                        if (ret == null) {
                            Timber.w("Sync - fullsync - unknown error");
                            return returnGenericError(data);
                        }
                        if (SUCCESS == ret) {
                            data.success = true;
                            col.reopen();
                        }
                        if (SUCCESS != ret) {
                            Timber.w("Sync - fullsync - download failed");
                            data.success = false;
                            data.resultType = ret;
                            if (!colCorruptFullSync) {
                                col.reopen();
                            }
                            return data;
                        }
                        break;
                    }
                    default:
                    }
                } catch (OutOfMemoryError e) {
                    Timber.w(e);
                    AnkiDroidApp.sendExceptionReport(e, "doInBackgroundSync-fullSync");
                    data.success = false;
                    data.resultType = OUT_OF_MEMORY_ERROR;
                    data.result = new Object[0];
                    return data;
                } catch (RuntimeException e) {
                    Timber.w(e);
                    if (timeoutOccurred(e)) {
                        data.resultType = CONNECTION_ERROR;
                    } else if (USER_ABORTED_SYNC.toString().equals(e.getMessage())) {
                        data.resultType = USER_ABORTED_SYNC;
                    } else {
                        AnkiDroidApp.sendExceptionReport(e, "doInBackgroundSync-fullSync");
                        data.resultType = IO_EXCEPTION;
                    }
                    data.result = new Object[]{e};
                    data.success = false;
                    return data;
                }
            }

            // clear undo to avoid non syncing orphans (because undo resets usn too
            if (!noChanges) {
                col.clearUndo();
            }
            // then move on to media sync
            sIsCancellable = true;
            boolean noMediaChanges = false;
            String mediaError = null;
            if (media) {
                RemoteMediaServer mediaServer = new RemoteMediaServer(col, hkey, this, hostNum);
                MediaSyncer mediaClient = new MediaSyncer(col, mediaServer, this);
                Pair<ConnectionResultType, String> ret;
                try {
                    Timber.i("Sync - Performing media sync");
                    ret = mediaClient.sync();
                    if (ret == null || ret.first == null) {
                        mediaError = AnkiDroidApp.getAppResources().getString(R.string.sync_media_error);
                    } else {
                        if (CORRUPT == ret.first) {
                            mediaError = AnkiDroidApp.getAppResources().getString(R.string.sync_media_db_error);
                            noMediaChanges = true;
                        }
                        if (NO_CHANGES == ret.first) {
                            publishProgress(R.string.sync_media_no_changes);
                            noMediaChanges = true;
                        }
                        if (MEDIA_SANITY_FAILED == ret.first) {
                            mediaError = AnkiDroidApp.getAppResources().getString(R.string.sync_media_sanity_failed);
                        } else {
                            publishProgress(R.string.sync_media_success);
                        }
                    }
                } catch (RuntimeException e) {
                    Timber.w(e);
                    if (timeoutOccurred(e)) {
                        data.resultType = CONNECTION_ERROR;
                        data.result = new Object[]{e};
                    } else if (USER_ABORTED_SYNC.toString().equals(e.getMessage())) {
                        data.resultType = USER_ABORTED_SYNC;
                        data.result = new Object[]{e};
                    }
                    mediaError = AnkiDroidApp.getAppResources().getString(R.string.sync_media_error) + "\n\n" + e.getLocalizedMessage();
                }
            }
            if (noChanges && (!media || noMediaChanges)) {
                // This means that there is no change at all, neither media nor collection. Not that there was an error.
                data.success = false;
                data.resultType = NO_CHANGES;
                data.result = new Object[0];
            } else {
                data.success = true;
                data.data = new Object[] { conflictResolution, col, mediaError };
            }
            return data;
        } catch (MediaSyncException e) {
            Timber.e("Media sync rejected by server");
            data.success = false;
            data.resultType = MEDIA_SYNC_SERVER_ERROR;
            data.result = new Object[]{e};
            AnkiDroidApp.sendExceptionReport(e, "doInBackgroundSync");
            return data;
        } catch (UnknownHttpResponseException e) {
            Timber.e(e, "doInBackgroundSync -- unknown response code error");
            data.success = false;
            int code = e.getResponseCode();
            String msg = e.getLocalizedMessage();
            data.resultType = ERROR;
            data.result = new Object[] {code , msg};
            return data;
        } catch (Exception e) {
            // Global error catcher.
            // Try to give a human readable error, otherwise print the raw error message
            Timber.e(e, "doInBackgroundSync error");
            data.success = false;
            if (timeoutOccurred(e)) {
                data.resultType = CONNECTION_ERROR;
                data.result = new Object[]{e};
            } else if (USER_ABORTED_SYNC.toString().equals(e.getMessage())) {
                data.resultType = USER_ABORTED_SYNC;
                data.result = new Object[]{e};
            } else {
                AnkiDroidApp.sendExceptionReport(e, "doInBackgroundSync");
                data.resultType = ARBITRARY_STRING;
                data.result = new Object[] {e.getLocalizedMessage(), e};
            }
            return data;
        } finally {
            Timber.i("Sync Finished - Closing Collection");
            // don't bump mod time unless we explicitly save
            if (col != null) {
                col.close(false);
            }
            CollectionHelper.getInstance().unlockCollection();
        }
    }


    @SuppressWarnings("deprecation") // #7108: AsyncTask
    public void publishProgress(int id) {
        super.publishProgress(id);
    }


    @SuppressWarnings("deprecation") // #7108: AsyncTask
    public void publishProgress(String message) {
        super.publishProgress(message);
    }


    @SuppressWarnings("deprecation") // #7108: AsyncTask
    public void publishProgress(int id, long up, long down) {
        super.publishProgress(id, up, down);
    }

    @SuppressWarnings("deprecation")
    public static boolean isOnline() {
        if (sAllowSyncOnNoConnection) {
            return true;
        }
        ConnectivityManager cm = (ConnectivityManager) AnkiDroidApp.getInstance().getApplicationContext()
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) {
            return false;
        }
        /* NetworkInfo is deprecated in API 29 so we have to check separately for higher API Levels */
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Network network = cm.getActiveNetwork();
            if (network == null) {
                return false;
            }
            NetworkCapabilities networkCapabilities = cm.getNetworkCapabilities(network);
            if (networkCapabilities == null) {
                return false;
            }
            boolean isInternetSuspended = !networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_SUSPENDED);
            return networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    && networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                    && !isInternetSuspended;
        } else {
            android.net.NetworkInfo networkInfo = cm.getActiveNetworkInfo();
            return networkInfo != null && networkInfo.isConnected();
        }
    }


    public interface TaskListener {
        void onPreExecute();


        void onProgressUpdate(Object... values);


        void onPostExecute(Payload data);


        void onDisconnected();
    }

    public interface CancellableTaskListener extends TaskListener {
        void onCancelled();
    }

    public static class Payload {
        private int mTaskType;
        @NonNull public Object[] data;
        public ConnectionResultType resultType;
        public Object[] result;
        public boolean success;
        public int returnType;
        public Exception exception;
        public String message;
        public Collection col;


        public Payload(@NonNull Object[] data) {
            this.data = data;
            success = true;
        }


        @Override
        public String toString() {
            return "Payload{" +
                    "mTaskType=" + mTaskType +
                    ", data=" + Arrays.toString(data) +
                    ", resultType=" + resultType +
                    ", result=" + Arrays.toString(result) +
                    ", success=" + success +
                    ", returnType=" + returnType +
                    ", exception=" + exception +
                    ", message='" + message + '\'' +
                    '}';
        }
    }

    @SuppressWarnings("deprecation") // #7108: AsyncTask
    public synchronized static void cancel() {
        Timber.d("Cancelled Connection task");
        sInstance.cancel(true);
        sIsCancelled = true;
    }

    public synchronized static boolean isCancellable() {
        return sIsCancellable;
    }
}
