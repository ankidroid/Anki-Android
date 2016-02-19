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

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.PowerManager;

import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.CollectionHelper;
import com.ichi2.anki.R;
import com.ichi2.anki.exception.MediaSyncException;
import com.ichi2.anki.exception.UnknownHttpResponseException;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.sync.FullSyncer;
import com.ichi2.libanki.sync.HttpSyncer;
import com.ichi2.libanki.sync.MediaSyncer;
import com.ichi2.libanki.sync.RemoteMediaServer;
import com.ichi2.libanki.sync.RemoteServer;
import com.ichi2.libanki.sync.Syncer;

import org.apache.http.HttpResponse;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.lang.ref.WeakReference;

import timber.log.Timber;

public class Connection extends BaseAsyncTask<Connection.Payload, Object, Connection.Payload> {

    public static final int TASK_TYPE_LOGIN = 0;
    public static final int TASK_TYPE_SYNC = 1;
    public static final int CONN_TIMEOUT = 30000;


    private static Connection sInstance;
    private TaskListener mListener;
    private static boolean sIsCancelled;
    private static boolean sIsCancellable;

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
        mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Connection");
    }

    private static Connection launchConnectionTask(TaskListener listener, Payload data) {

        if (!isOnline()) {
            data.success = false;
            listener.onDisconnected();
            return null;
        }

        try {
            if ((sInstance != null) && (sInstance.getStatus() != AsyncTask.Status.FINISHED)) {
                sInstance.get();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        sInstance = new Connection();
        sInstance.mListener = listener;

        sInstance.execute(data);
        return sInstance;
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
    @Override
    protected void onPreExecute() {
        super.onPreExecute();
        // Acquire the wake lock before syncing to ensure CPU remains on until the sync completes.
        mWakeLock.acquire();
        if (mListener != null) {
            mListener.onPreExecute();
        }
    }


    /*
     * Runs on GUI thread
     */
    @Override
    protected void onPostExecute(Payload data) {
        super.onPostExecute(data);
        // Sync has ended so release the wake lock
        mWakeLock.release();
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
        data.taskType = TASK_TYPE_LOGIN;
        return launchConnectionTask(listener, data);
    }


    public static Connection sync(TaskListener listener, Payload data) {
        data.taskType = TASK_TYPE_SYNC;
        return launchConnectionTask(listener, data);
    }


    @Override
    protected Payload doInBackground(Payload... params) {
        super.doInBackground(params);
        if (params.length != 1) {
            throw new IllegalArgumentException();
        }
        return doOneInBackground(params[0]);
    }


    private Payload doOneInBackground(Payload data) {
        switch (data.taskType) {
            case TASK_TYPE_LOGIN:
                return doInBackgroundLogin(data);

            case TASK_TYPE_SYNC:
                return doInBackgroundSync(data);

            default:
                return null;
        }
    }


    private Payload doInBackgroundLogin(Payload data) {
        String username = (String) data.data[0];
        String password = (String) data.data[1];
        HttpSyncer server = new RemoteServer(this, null);
        HttpResponse ret;
        try {
            ret = server.hostKey(username, password);
        } catch (UnknownHttpResponseException e) {
            data.success = false;
            data.result = new Object[] { "error", e.getResponseCode(), e.getMessage() };
            return data;
        } catch (Exception e2) {
            // Ask user to report all bugs which aren't timeout errors
            if (!timeoutOccured(e2)) {
                AnkiDroidApp.sendExceptionReport(e2, "doInBackgroundLogin");
            }
            data.success = false;
            data.result = new Object[] {"connectionError" };
            return data;
        }
        String hostkey = null;
        boolean valid = false;
        if (ret != null) {
            data.returnType = ret.getStatusLine().getStatusCode();
            Timber.d("doInBackgroundLogin - response from server: %d, (%s)", data.returnType, ret.getStatusLine().getReasonPhrase());
            if (data.returnType == 200) {
                try {
                    JSONObject jo = (new JSONObject(server.stream2String(ret.getEntity().getContent())));
                    hostkey = jo.getString("key");
                    valid = (hostkey != null) && (hostkey.length() > 0);
                } catch (JSONException e) {
                    valid = false;
                } catch (IllegalStateException | IOException e) {
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


    private boolean timeoutOccured(Exception e) {
        String msg = e.getMessage();
        return msg.contains("UnknownHostException") ||
                msg.contains("HttpHostConnectException") ||
                msg.contains("SSLException while building HttpClient") ||
                msg.contains("SocketTimeoutException") ||
                msg.contains("ClientProtocolException") ||
                msg.contains("TimeoutException");
    }


    private Payload doInBackgroundSync(Payload data) {
        sIsCancellable = true;
        Timber.d("doInBackgroundSync()");
        // Block execution until any previous background task finishes, or timeout after 5s
        boolean ok = DeckTask.waitToFinish(5);

        String hkey = (String) data.data[0];
        boolean media = (Boolean) data.data[1];
        String conflictResolution = (String) data.data[2];
        // Use safe version that catches exceptions so that full sync is still possible
        Collection col = CollectionHelper.getInstance().getColSafe(AnkiDroidApp.getInstance());

        boolean colCorruptFullSync = false;
        if (!CollectionHelper.getInstance().colIsOpen() || !ok) {
            if (conflictResolution != null && conflictResolution.equals("download")) {
                colCorruptFullSync = true;
            } else {
                data.success = false;
                data.result = new Object[] { "genericError" };
                return data;
            }
        }
        try {
            CollectionHelper.getInstance().lockCollection();
            HttpSyncer server = new RemoteServer(this, hkey);
            Syncer client = new Syncer(col, server);

            // run sync and check state
            boolean noChanges = false;
            if (conflictResolution == null) {
                Timber.i("Sync - starting sync");
                publishProgress(R.string.sync_prepare_syncing);
                Object[] ret = client.sync(this);
                data.message = client.getSyncMsg();
                if (ret == null) {
                    data.success = false;
                    data.result = new Object[] { "genericError" };
                    return data;
                }
                String retCode = (String) ret[0];
                if (!retCode.equals("noChanges") && !retCode.equals("success")) {
                    data.success = false;
                    data.result = ret;
                    // Check if there was a sanity check error
                    if (retCode.equals("sanityCheckError")) {
                        // Force full sync next time
                        col.modSchemaNoCheck();
                        col.save();
                    }
                    return data;
                }
                // save and note success state
                if (retCode.equals("noChanges")) {
                    // publishProgress(R.string.sync_no_changes_message);
                    noChanges = true;
                } else {
                    // publishProgress(R.string.sync_database_acknowledge);
                }
            } else {
                try {
                    // Disable sync cancellation for full-sync
                    sIsCancellable = false;
                    server = new FullSyncer(col, hkey, this);
                    if (conflictResolution.equals("upload")) {
                        Timber.i("Sync - fullsync - upload collection");
                        publishProgress(R.string.sync_preparing_full_sync_message);
                        Object[] ret = server.upload();
                        col.reopen();
                        if (ret == null) {
                            data.success = false;
                            data.result = new Object[] { "genericError" };
                            return data;
                        }
                        if (!ret[0].equals(HttpSyncer.ANKIWEB_STATUS_OK)) {
                            data.success = false;
                            data.result = ret;
                            return data;
                        }
                    } else if (conflictResolution.equals("download")) {
                        Timber.i("Sync - fullsync - download collection");
                        publishProgress(R.string.sync_downloading_message);
                        Object[] ret = server.download();
                        if (ret == null) {
                            data.success = false;
                            data.result = new Object[] { "genericError" };
                            return data;
                        }
                        if (ret[0].equals("success")) {
                            data.success = true;
                            col.reopen();
                        }
                        if (!ret[0].equals("success")) {
                            data.success = false;
                            data.result = ret;
                            if (!colCorruptFullSync) {
                                col.reopen();
                            }
                            return data;
                        }
                    }
                } catch (OutOfMemoryError e) {
                    AnkiDroidApp.sendExceptionReport(e, "doInBackgroundSync-fullSync");
                    data.success = false;
                    data.result = new Object[] { "OutOfMemoryError" };
                    return data;
                } catch (RuntimeException e) {
                    if (timeoutOccured(e)) {
                        data.result = new Object[] {"connectionError" };
                    } else if (e.getMessage().equals("UserAbortedSync")) {
                        data.result = new Object[] {"UserAbortedSync" };
                    } else {
                        AnkiDroidApp.sendExceptionReport(e, "doInBackgroundSync-fullSync");
                        data.result = new Object[] { "IOException" };
                    }
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
                server = new RemoteMediaServer(col, hkey, this);
                MediaSyncer mediaClient = new MediaSyncer(col, (RemoteMediaServer) server, this);
                String ret;
                try {
                    ret = mediaClient.sync();
                    if (ret == null) {
                        mediaError = AnkiDroidApp.getAppResources().getString(R.string.sync_media_error);
                    } else {
                        if (ret.equals("noChanges")) {
                            publishProgress(R.string.sync_media_no_changes);
                            noMediaChanges = true;
                        }
                        if (ret.equals("sanityFailed")) {
                            mediaError = AnkiDroidApp.getAppResources().getString(R.string.sync_media_sanity_failed);
                        } else {
                            publishProgress(R.string.sync_media_success);
                        }
                    }
                } catch (RuntimeException e) {
                    if (timeoutOccured(e)) {
                        data.result = new Object[] {"connectionError" };
                    } else if (e.getMessage().equals("UserAbortedSync")) {
                        data.result = new Object[] {"UserAbortedSync" };
                    } else {
                        AnkiDroidApp.sendExceptionReport(e, "doInBackgroundSync-mediaSync");
                    }
                    mediaError = e.getLocalizedMessage();
                }
            }
            if (noChanges && (!media || noMediaChanges)) {
                data.success = false;
                data.result = new Object[] { "noChanges" };
                return data;
            } else {
                data.success = true;
                data.data = new Object[] { conflictResolution, col, mediaError };
                return data;
            }
        } catch (MediaSyncException e) {
            Timber.e("Media sync rejected by server");
            data.success = false;
            data.result = new Object[] {"mediaSyncServerError"};
            AnkiDroidApp.sendExceptionReport(e, "doInBackgroundSync");
            return data;
        } catch (UnknownHttpResponseException e) {
            Timber.e("doInBackgroundSync -- unknown response code error");
            e.printStackTrace();
            data.success = false;
            Integer code = e.getResponseCode();
            String msg = e.getLocalizedMessage();
            data.result = new Object[] { "error", code , msg };
            return data;
        } catch (Exception e) {
            // Global error catcher.
            // Try to give a human readable error, otherwise print the raw error message
            Timber.e("doInBackgroundSync error");
            e.printStackTrace();
            data.success = false;
            if (timeoutOccured(e)) {
                data.result = new Object[]{"connectionError"};
            } else if (e.getMessage().equals("UserAbortedSync")) {
                data.result = new Object[] {"UserAbortedSync" };
            } else {
                AnkiDroidApp.sendExceptionReport(e, "doInBackgroundSync");
                data.result = new Object[] {e.getLocalizedMessage()};
            }
            return data;
        } finally {
            // don't bump mod time unless we explicitly save
            if (col != null) {
                col.close(false);
            }
            CollectionHelper.getInstance().unlockCollection();
        }
    }


    public void publishProgress(int id) {
        super.publishProgress(id);
    }


    public void publishProgress(String message) {
        super.publishProgress(message);
    }


    public void publishProgress(int id, long up, long down) {
        super.publishProgress(id, up, down);
    }

    public static boolean isOnline() {
        ConnectivityManager cm = (ConnectivityManager) AnkiDroidApp.getInstance().getApplicationContext()
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = cm.getActiveNetworkInfo();
        if (netInfo == null || !netInfo.isConnected() || !netInfo.isAvailable()) {
            return false;
        }
        return true;
    }


    public static interface TaskListener {
        public void onPreExecute();


        public void onProgressUpdate(Object... values);


        public void onPostExecute(Payload data);


        public void onDisconnected();
    }

    public static interface CancellableTaskListener extends TaskListener {
        public void onCancelled();
    }

    public static class Payload {
        public int taskType;
        public Object[] data;
        public Object result;
        public boolean success;
        public int returnType;
        public Exception exception;
        public String message;
        public Collection col;


        public Payload() {
            data = null;
            success = true;
        }


        public Payload(Object[] data) {
            this.data = data;
            success = true;
        }


        public Payload(int taskType, Object[] data) {
            this.taskType = taskType;
            this.data = data;
            success = true;
        }

        public Payload(int taskType, Object[] data, String path) {
            this.taskType = taskType;
            this.data = data;
            success = true;
        }
    }

    public synchronized static void cancel() {
        Timber.d("Cancelled Connection task");
        sInstance.cancel(true);
        sIsCancelled = true;
    }

    public synchronized static boolean isCancellable() {
        return sIsCancellable;
    }

    public class CancelCallback {
        private WeakReference<ThreadSafeClientConnManager> mConnectionManager = null;


        public void setConnectionManager(ThreadSafeClientConnManager connectionManager) {
            mConnectionManager = new WeakReference<>(connectionManager);
        }


        public void cancelAllConnections() {
            Timber.d("cancelAllConnections()");
            if (mConnectionManager != null) {
                ThreadSafeClientConnManager connectionManager = mConnectionManager.get();
                if (connectionManager != null) {
                    connectionManager.shutdown();
                }
            }
        }
    }
}
