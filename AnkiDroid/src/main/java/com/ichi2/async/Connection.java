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

import android.app.Application;
import android.content.Context;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;

import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.Feedback;
import com.ichi2.anki.R;
import com.ichi2.anki.exception.UnknownHttpResponseException;
import com.ichi2.anki.exception.UnsupportedSyncException;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Decks;
import com.ichi2.libanki.Utils;
import com.ichi2.libanki.sync.FullSyncer;
import com.ichi2.libanki.sync.HttpSyncer;
import com.ichi2.libanki.sync.MediaSyncer;
import com.ichi2.libanki.sync.RemoteMediaServer;
import com.ichi2.libanki.sync.RemoteServer;
import com.ichi2.libanki.sync.Syncer;

import org.apache.commons.httpclient.contrib.ssl.EasyX509TrustManager;
import org.apache.http.HttpResponse;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;

public class Connection extends BaseAsyncTask<Connection.Payload, Object, Connection.Payload> {

    public static final int TASK_TYPE_LOGIN = 0;
    public static final int TASK_TYPE_SYNC = 1;
    public static final int TASK_TYPE_GET_SHARED_DECKS = 2;
    public static final int TASK_TYPE_GET_PERSONAL_DECKS = 3;
    public static final int TASK_TYPE_SEND_CRASH_REPORT = 4;
    public static final int TASK_TYPE_DOWNLOAD_MEDIA = 5;
    public static final int TASK_TYPE_REGISTER = 6;
    public static final int TASK_TYPE_UPGRADE_DECKS = 7;
    public static final int TASK_TYPE_DOWNLOAD_SHARED_DECK = 8;
    public static final int CONN_TIMEOUT = 30000;

    private static Context sContext;

    private static Connection sInstance;
    private TaskListener mListener;
    private CancelCallback mCancelCallback;

    public static final int RETURN_TYPE_OUT_OF_MEMORY = -1;

    public static final String CONFLICT_RESOLUTION = "ConflictResolutionRequired";


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
        if (mCancelCallback != null) {
            mCancelCallback.cancelAllConnections();
        }
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


    public static boolean taskIsCancelled() {
        return sInstance.isCancelled();
    }


    public static void cancelTask() {
        try {
            if (sInstance != null && sInstance.getStatus() != AsyncTask.Status.FINISHED) {
                sInstance.cancel(true);
            }
        } catch (Exception e) {
            return;
        }
    }


    public static Connection login(TaskListener listener, Payload data) {
        data.taskType = TASK_TYPE_LOGIN;
        return launchConnectionTask(listener, data);
    }


    public static Connection register(TaskListener listener, Payload data) {
        data.taskType = TASK_TYPE_REGISTER;
        return launchConnectionTask(listener, data);
    }


    public static Connection getSharedDecks(TaskListener listener, Payload data) {
        data.taskType = TASK_TYPE_GET_SHARED_DECKS;
        return launchConnectionTask(listener, data);
    }


    public static Connection getPersonalDecks(TaskListener listener, Payload data) {
        data.taskType = TASK_TYPE_GET_PERSONAL_DECKS;
        return launchConnectionTask(listener, data);
    }


    public static Connection sync(TaskListener listener, Payload data) {
        data.taskType = TASK_TYPE_SYNC;
        return launchConnectionTask(listener, data);
    }


    public static Connection sendFeedback(TaskListener listener, Payload data) {
        data.taskType = TASK_TYPE_SEND_CRASH_REPORT;
        return launchConnectionTask(listener, data);
    }


    public static Connection downloadMissingMedia(TaskListener listener, Payload data) {
        data.taskType = TASK_TYPE_DOWNLOAD_MEDIA;
        return launchConnectionTask(listener, data);
    }


    public static Connection upgradeDecks(TaskListener listener, Payload data) {
        data.taskType = TASK_TYPE_UPGRADE_DECKS;
        return launchConnectionTask(listener, data);
    }


    public static Connection downloadSharedDeck(TaskListener listener, Payload data) {
        data.taskType = TASK_TYPE_DOWNLOAD_SHARED_DECK;
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

            case TASK_TYPE_REGISTER:
                return doInBackgroundRegister(data);

                // case TASK_TYPE_GET_SHARED_DECKS:
                // return doInBackgroundGetSharedDecks(data);
                //
                // case TASK_TYPE_GET_PERSONAL_DECKS:
                // return doInBackgroundGetPersonalDecks(data);
                //
                // case TASK_TYPE_SYNC_ALL_DECKS:
                // return doInBackgroundSyncAllDecks(data);

            case TASK_TYPE_SYNC:
                return doInBackgroundSync(data);

            case TASK_TYPE_SEND_CRASH_REPORT:
                return doInBackgroundSendFeedback(data);

            case TASK_TYPE_DOWNLOAD_MEDIA:
                return doInBackgroundDownloadMissingMedia(data);

            case TASK_TYPE_UPGRADE_DECKS:
                throw new RuntimeException("Upgrade decks no longer supported");

            case TASK_TYPE_DOWNLOAD_SHARED_DECK:
                return doInBackgroundDownloadSharedDeck(data);

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
                AnkiDroidApp.saveExceptionReportFile(e2, "doInBackgroundLogin");
            }
            data.success = false;
            data.result = new Object[] {"connectionError" };
            return data;
        }
        String hostkey = null;
        boolean valid = false;
        if (ret != null) {
            data.returnType = ret.getStatusLine().getStatusCode();
            Log.i(AnkiDroidApp.TAG, "doInBackgroundLogin - response from server: " + data.returnType + " (" + ret.getStatusLine().getReasonPhrase() + ")");
            if (data.returnType == 200) {
                try {
                    JSONObject jo = (new JSONObject(server.stream2String(ret.getEntity().getContent())));
                    hostkey = jo.getString("key");
                    valid = (hostkey != null) && (hostkey.length() > 0);
                } catch (JSONException e) {
                    valid = false;
                } catch (IllegalStateException e) {
                    throw new RuntimeException(e);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        } else {
            Log.e(AnkiDroidApp.TAG, "doInBackgroundLogin - empty response from server");
        }
        if (valid) {
            data.success = true;
            data.data = new String[] { username, hostkey };
        } else {
            data.success = false;
        }
        return data;
    }


    private Payload doInBackgroundRegister(Payload data) {
        String username = (String) data.data[0];
        String password = (String) data.data[1];
        HttpSyncer server = new RemoteServer(this, null);
        HttpResponse ret;
        try {
            ret = server.register(username, password);
        } catch (UnknownHttpResponseException e) {
            data.success = false;
            data.result = new Object[] { "error", e.getResponseCode(), e.getMessage() };
            return data;
        }
        String hostkey = null;
        boolean valid = false;
        String status = null;
        if (ret != null) {
            data.returnType = ret.getStatusLine().getStatusCode();
            if (data.returnType == 200) {
                try {
                    JSONObject jo = (new JSONObject(server.stream2String(ret.getEntity().getContent())));
                    status = jo.getString("status");
                    if (status.equals("ok")) {
                        hostkey = jo.getString("hkey");
                        valid = (hostkey != null) && (hostkey.length() > 0);
                    }
                } catch (JSONException e) {
                } catch (IllegalStateException e) {
                    throw new RuntimeException(e);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        if (valid) {
            data.success = true;
            data.data = new String[] { username, hostkey };
        } else {
            data.success = false;
            data.data = new String[] { status != null ? status : AnkiDroidApp.getAppResources().getString(
                    R.string.connection_error_message) };
        }
        return data;
    }


    private boolean timeoutOccured(Exception e) {
        String msg = e.getMessage();
        return msg.contains("UnknownHostException") ||
                msg.contains("HttpHostConnectException") ||
                msg.contains("SSLException while building HttpClient") ||
                msg.contains("SocketTimeoutException") ||
                msg.contains("ClientProtocolException");
    }


    private Payload doInBackgroundSync(Payload data) {
        // for for doInBackgroundLoadDeckCounts if any
        Log.v(AnkiDroidApp.TAG, "doInBackgroundSync()");
        // Block execution until any previous background task finishes, or timeout after 5s
        boolean ok = DeckTask.waitToFinish(5);

        String hkey = (String) data.data[0];
        boolean media = (Boolean) data.data[1];
        String conflictResolution = (String) data.data[2];
        int mediaUsn = (Integer) data.data[3];

        boolean colCorruptFullSync = false;
        Collection col = AnkiDroidApp.getCol();
        if (!AnkiDroidApp.colIsOpen() || !ok) {
            if (conflictResolution != null && conflictResolution.equals("download")) {
                colCorruptFullSync = true;
            } else {
                data.success = false;
                data.result = new Object[] { "genericError" };
                return data;
            }
        }
        String path = AnkiDroidApp.getCollectionPath();
        try {
            AnkiDroidApp.setSyncInProgress(true);
            HttpSyncer server = new RemoteServer(this, hkey);
            Syncer client = new Syncer(col, server);

            // run sync and check state
            boolean noChanges = false;
            if (conflictResolution == null) {
                Log.i(AnkiDroidApp.TAG, "Sync - starting sync");
                publishProgress(R.string.sync_prepare_syncing);
                Object[] ret = client.sync(this);
                data.message = client.getSyncMsg();
                mediaUsn = client.getmMediaUsn();
                if (ret == null) {
                    data.success = false;
                    data.result = new Object[] { "genericError" };
                    return data;
                }
                String retCode = (String) ret[0];
                if (!retCode.equals("noChanges") && !retCode.equals("success")) {
                    data.success = false;
                    data.result = ret;
                    // note mediaUSN for later
                    data.data = new Object[] { mediaUsn };
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
                    server = new FullSyncer(col, hkey, this);
                    if (conflictResolution.equals("upload")) {
                        Log.i(AnkiDroidApp.TAG, "Sync - fullsync - upload collection");
                        publishProgress(R.string.sync_preparing_full_sync_message);
                        Object[] ret = server.upload();
                        if (ret == null) {
                            data.success = false;
                            data.result = new Object[] { "genericError" };
                            AnkiDroidApp.openCollection(path);
                            return data;
                        }
                        if (!ret[0].equals(HttpSyncer.ANKIWEB_STATUS_OK)) {
                            data.success = false;
                            data.result = ret;
                            AnkiDroidApp.openCollection(path);
                            return data;
                        }
                    } else if (conflictResolution.equals("download")) {
                        Log.i(AnkiDroidApp.TAG, "Sync - fullsync - download collection");
                        publishProgress(R.string.sync_downloading_message);
                        Object[] ret = server.download();
                        if (ret == null) {
                            data.success = false;
                            data.result = new Object[] { "genericError" };
                            AnkiDroidApp.openCollection(path);
                            return data;
                        }
                        if (!ret[0].equals("success")) {
                            data.success = false;
                            data.result = ret;
                            if (!colCorruptFullSync) {
                                AnkiDroidApp.openCollection(path);
                            }
                            return data;
                        }
                    }
                    col = AnkiDroidApp.openCollection(path);
                } catch (OutOfMemoryError e) {
                    AnkiDroidApp.saveExceptionReportFile(e, "doInBackgroundSync-fullSync");
                    data.success = false;
                    data.result = new Object[] { "OutOfMemoryError" };
                    data.data = new Object[] { mediaUsn };
                    return data;
                } catch (RuntimeException e) {
                    if (timeoutOccured(e)) {
                        data.result = new Object[] {"connectionError" };
                    } else {
                        AnkiDroidApp.saveExceptionReportFile(e, "doInBackgroundSync-fullSync");
                        data.result = new Object[] { "IOException" };
                    }
                    data.success = false;
                    data.data = new Object[] { mediaUsn };
                    return data;
                }
            }

            // clear undo to avoid non syncing orphans (because undo resets usn too
            if (!noChanges) {
                col.clearUndo();
            }
            // then move on to media sync
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
                } catch (UnsupportedSyncException e) {
                    mediaError = AnkiDroidApp.getAppResources().getString(R.string.sync_media_unsupported);
                    AnkiDroidApp.getSharedPrefs(sContext).edit().putBoolean("syncFetchesMedia", false).commit();
                    AnkiDroidApp.saveExceptionReportFile(e, "doInBackgroundSync-mediaSync");
                } catch (RuntimeException e) {
                    if (timeoutOccured(e)) {
                        data.result = new Object[] {"connectionError" };
                    } else {
                        AnkiDroidApp.saveExceptionReportFile(e, "doInBackgroundSync-mediaSync");
                    }
                    mediaError = e.getLocalizedMessage();
                }
            }
            if (noChanges && noMediaChanges) {
                data.success = false;
                data.result = new Object[] { "noChanges" };
                return data;
            } else {
                data.success = true;
                Object[] dc = col.getSched().deckCounts();
                data.result = dc[0];
                data.data = new Object[] { conflictResolution, col, dc[1], dc[2], mediaError };
                return data;
            }
        } catch (UnknownHttpResponseException e) {
            Log.e(AnkiDroidApp.TAG, "doInBackgroundSync -- unknown response code error");
            e.printStackTrace();
            data.success = false;
            Integer code = e.getResponseCode();
            String msg = e.getLocalizedMessage();
            data.result = new Object[] { "error", code , msg };
            data.data = new Object[] { mediaUsn };
            return data;
        } catch (Exception e) {
            // Global error catcher.
            // Try to give a human readable error, otherwise print the raw error message
            Log.e(AnkiDroidApp.TAG, "doInBackgroundSync error");
            e.printStackTrace();
            data.success = false;
            if (timeoutOccured(e)) {
                data.result = new Object[] {"connectionError" };
            } else {
                AnkiDroidApp.saveExceptionReportFile(e, "doInBackgroundSync");
                data.result = new Object[] {e.getLocalizedMessage()};
            }
            return data;
        } finally {
            // Close collection to roll back any sync failures and
            Log.i(AnkiDroidApp.TAG, "doInBackgroundSync -- closing collection on outer finally statement");
            col.close(false);
            AnkiDroidApp.setSyncInProgress(false);
            Log.i(AnkiDroidApp.TAG, "doInBackgroundSync -- reopening collection on outer finally statement");
            AnkiDroidApp.openCollection(AnkiDroidApp.getCollectionPath());
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


    private Payload doInBackgroundSendFeedback(Payload data) {
        Log.i(AnkiDroidApp.TAG, "doInBackgroundSendFeedback");
        String feedbackUrl = (String) data.data[0];
        String errorUrl = (String) data.data[1];
        String feedback = (String) data.data[2];
        ArrayList<HashMap<String, String>> errors = (ArrayList<HashMap<String, String>>) data.data[3];
        String groupId = data.data[4].toString();
        Application app = (Application) data.data[5];
        boolean deleteAfterSending = (Boolean) data.data[6];

        String postType = null;
        if (feedback.length() > 0) {
            if (errors.size() > 0) {
                postType = Feedback.TYPE_ERROR_FEEDBACK;
            } else {
                postType = Feedback.TYPE_FEEDBACK;
            }
            publishProgress(postType, 0, Feedback.STATE_UPLOADING);
            Payload reply = Feedback.postFeedback(feedbackUrl, postType, feedback, groupId, 0, null);
            if (reply.success) {
                publishProgress(postType, 0, Feedback.STATE_SUCCESSFUL, reply.returnType, reply.result);
            } else {
                publishProgress(postType, 0, Feedback.STATE_FAILED, reply.returnType, reply.result);
            }
        }

        for (int i = 0; i < errors.size(); i++) {
            HashMap<String, String> error = errors.get(i);
            if (error.containsKey("state") && error.get("state").equals(Feedback.STATE_WAITING)) {
                postType = Feedback.TYPE_STACKTRACE;
                publishProgress(postType, i, Feedback.STATE_UPLOADING);
                Payload reply = Feedback.postFeedback(errorUrl, postType, error.get("filename"), groupId, i, app);
                if (reply.success) {
                    publishProgress(postType, i, Feedback.STATE_SUCCESSFUL, reply.returnType, reply.result);
                } else {
                    publishProgress(postType, i, Feedback.STATE_FAILED, reply.returnType, reply.result);
                }
                if (deleteAfterSending && (reply.success || reply.returnType == 200)) {
                    File file = new File(app.getFilesDir() + "/" + error.get("filename"));
                    file.delete();
                }
            }
        }

        app = null;

        return data;
    }


    /**
     * Downloads any missing media files according to the mediaURL deckvar.
     *
     * @param data
     * @return The return type contains data.resultType and an array of Integer in data.data. data.data[0] is the number
     *         of total missing media, data.data[1] is the number of downloaded ones.
     */
    private Payload doInBackgroundDownloadMissingMedia(Payload data) {
        Log.i(AnkiDroidApp.TAG, "DownloadMissingMedia");
        HashMap<String, String> missingPaths = new HashMap<String, String>();
        HashMap<String, String> missingSums = new HashMap<String, String>();

        data.result = (Decks) data.data[0]; // pass it to the return object so we close the deck in the deck picker
        String syncName = "";// deck.getDeckName();

        data.success = false;
        data.data = new Object[] { 0, 0, 0 };
        // if (!deck.hasKey("mediaURL")) {
        // data.success = true;
        // return data;
        // }
        String urlbase = "";// deck.getVar("mediaURL");
        if (urlbase.equals("")) {
            data.success = true;
            return data;
        }

        String mdir = "";// deck.mediaDir(true);
        int totalMissing = 0;
        int missing = 0;
        int grabbed = 0;

        Cursor cursor = null;
        try {
            cursor = null;// deck.getDB().getDatabase().rawQuery("SELECT filename, originalPath FROM media", null);
            String path = null;
            String f = null;
            while (cursor.moveToNext()) {
                f = cursor.getString(0);
                path = mdir + "/" + f;
                File file = new File(path);
                if (!file.exists()) {
                    missingPaths.put(f, path);
                    missingSums.put(f, cursor.getString(1));
                    Log.i(AnkiDroidApp.TAG, "Missing file: " + f);
                }
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        totalMissing = missingPaths.size();
        data.data[0] = totalMissing;
        if (totalMissing == 0) {
            data.success = true;
            return data;
        }
        publishProgress(Boolean.FALSE, totalMissing, 0, syncName);

        URL url = null;
        HttpURLConnection connection = null;
        String path = null;
        String sum = null;
        int readbytes = 0;
        byte[] buf = new byte[4096];
        for (String file : missingPaths.keySet()) {

            try {
                android.net.Uri uri = android.net.Uri.parse(Uri.encode(urlbase, ":/@%") + Uri.encode(file));
                url = new URI(uri.toString()).toURL();
                connection = (HttpURLConnection) url.openConnection();
                connection.connect();
                if (connection.getResponseCode() == 200) {
                    path = missingPaths.get(file);
                    InputStream is = connection.getInputStream();
                    BufferedInputStream bis = new BufferedInputStream(is, 4096);
                    FileOutputStream fos = new FileOutputStream(path);
                    while ((readbytes = bis.read(buf, 0, 4096)) != -1) {
                        fos.write(buf, 0, readbytes);
                        Log.i(AnkiDroidApp.TAG, "Downloaded " + readbytes + " file: " + path);
                    }
                    fos.close();

                    // Verify with checksum
                    sum = missingSums.get(file);
                    if (true) {// sum.equals("") || sum.equals(Utils.fileChecksum(path))) {
                        grabbed++;
                    } else {
                        // Download corrupted, delete file
                        Log.i(AnkiDroidApp.TAG, "Downloaded media file " + path + " failed checksum.");
                        File f = new File(path);
                        f.delete();
                        missing++;
                    }
                } else {
                    Log.e(AnkiDroidApp.TAG, "Connection error (" + connection.getResponseCode()
                            + ") while retrieving media file " + urlbase + file);
                    Log.e(AnkiDroidApp.TAG, "Connection message: " + connection.getResponseMessage());
                    if (missingSums.get(file).equals("")) {
                        // Ignore and keep going
                        missing++;
                    } else {
                        data.success = false;
                        data.data = new Object[] { file };
                        return data;
                    }
                }
                connection.disconnect();
            } catch (URISyntaxException e) {
                Log.e(AnkiDroidApp.TAG, Log.getStackTraceString(e));
            } catch (MalformedURLException e) {
                Log.e(AnkiDroidApp.TAG, Log.getStackTraceString(e));
                Log.e(AnkiDroidApp.TAG, "MalformedURLException while download media file " + path);
                if (missingSums.get(file).equals("")) {
                    // Ignore and keep going
                    missing++;
                } else {
                    data.success = false;
                    data.data = new Object[] { file };
                    return data;
                }
            } catch (IOException e) {
                Log.e(AnkiDroidApp.TAG, Log.getStackTraceString(e));
                Log.e(AnkiDroidApp.TAG, "IOException while download media file " + path);
                if (missingSums.get(file).equals("")) {
                    // Ignore and keep going
                    missing++;
                } else {
                    data.success = false;
                    data.data = new Object[] { file };
                    return data;
                }
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
            publishProgress(Boolean.TRUE, totalMissing, grabbed + missing, syncName);
        }

        data.data[1] = grabbed;
        data.data[2] = missing;
        data.success = true;
        return data;
    }


    private Payload doInBackgroundDownloadSharedDeck(Payload data) {
        String url = (String) data.data[0];
        String colFilename = AnkiDroidApp.getCurrentAnkiDroidDirectory() + "/tmpImportFile.apkg";
        URL fileUrl;
        URLConnection conn;
        InputStream cont = null;
        try {
            fileUrl = new URL(url);
            if (url.startsWith("https")) {
                SSLContext context = SSLContext.getInstance("TLS");
                context.init(null, new TrustManager[] { new EasyX509TrustManager(null) }, null);
                HttpsURLConnection httpsConn = (HttpsURLConnection) fileUrl.openConnection();
                httpsConn.setSSLSocketFactory(context.getSocketFactory());
                conn = httpsConn;
            } else {
                conn = fileUrl.openConnection();
            }
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            cont = conn.getInputStream();
        } catch (MalformedURLException e) {
            Log.e(AnkiDroidApp.TAG, "doInBackgroundDownloadSharedDeck: ", e);
            data.success = false;
            return data;
        } catch (IOException e) {
            Log.e(AnkiDroidApp.TAG, "doInBackgroundDownloadSharedDeck: ", e);
            data.success = false;
            return data;
        } catch (NoSuchAlgorithmException e) {
            Log.e(AnkiDroidApp.TAG, "doInBackgroundDownloadSharedDeck: ", e);
            data.success = false;
            return data;
        } catch (KeyStoreException e) {
            Log.e(AnkiDroidApp.TAG, "doInBackgroundDownloadSharedDeck: ", e);
            return data;
        } catch (KeyManagementException e) {
            Log.e(AnkiDroidApp.TAG, "doInBackgroundDownloadSharedDeck: ", e);
            data.success = false;
            return data;
        }
        if (cont == null) {
            data.success = false;
            return data;
        }
        File file = new File(colFilename);
        OutputStream output = null;
        try {
            file.createNewFile();
            output = new BufferedOutputStream(new FileOutputStream(file));
            byte[] buf = new byte[Utils.CHUNK_SIZE];
            int len;
            int count = 0;
            while ((len = cont.read(buf)) >= 0) {
                output.write(buf, 0, len);
                count += len;
                publishProgress(new Object[] { count / 1024 });
            }
            output.close();
        } catch (IOException e) {
            try {
                if (output != null) {
                    output.close();
                }
            } catch (IOException e1) {
                // do nothing
            }
            // no write access or sd card full
            file.delete();
            data.success = false;
            return data;
        }
        data.success = true;
        data.result = colFilename;
        return data;
    }


    public static boolean isOnline() {
        ConnectivityManager cm = (ConnectivityManager) sContext.getSystemService(Context.CONNECTIVITY_SERVICE);

        if (cm.getActiveNetworkInfo() != null) {
            return cm.getActiveNetworkInfo().isConnectedOrConnecting();
        } else {
            return false;
        }
    }


    public static void setContext(Context applicationContext) {
        sContext = applicationContext;
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
    }

    // public static void cancelGetSharedDecks() {
    // HttpSyncer.resetSharedDecks();
    // sInstance.cancel(true);
    // }

    // private Payload doInBackgroundGetSharedDecks(Payload data) {
    // // DeckManager.closeMainDeck();
    // try {
    // data.result = HttpSyncer.getSharedDecks();
    // } catch (OutOfMemoryError e) {
    // data.success = false;
    // data.returnType = RETURN_TYPE_OUT_OF_MEMORY;
    // Log.e(AnkiDroidApp.TAG, "doInBackgroundGetSharedDecks: OutOfMemoryError: " + e);
    // } catch (Exception e) {
    // data.success = false;
    // data.exception = e;
    // Log.e(AnkiDroidApp.TAG, "doInBackgroundGetSharedDecks - Error getting shared decks = " + e.getMessage());
    // Log.e(AnkiDroidApp.TAG, Log.getStackTraceString(e));
    // }
    // return data;
    // }
    //
    //
    // private Payload doInBackgroundGetPersonalDecks(Payload data) {
    // Resources res = sContext.getResources();
    // // DeckManager.closeMainDeck();
    // try {
    // String username = (String) data.data[0];
    // String password = (String) data.data[1];
    // HttpSyncer server = new HttpSyncer(username, password);
    //
    // int connectResult = server.connect(false);
    // if (connectResult != HttpSyncer.LOGIN_OK) {
    // if (connectResult == HttpSyncer.LOGIN_INVALID_USER_PASS) {
    // data.result = res.getString(R.string.invalid_username_password);
    // } else if (connectResult == HttpSyncer.LOGIN_OLD_VERSION) {
    // data.result = String.format(res.getString(R.string.sync_log_old_version),
    // res.getString(R.string.link_ankidroid));
    // } else if (connectResult == HttpSyncer.LOGIN_TOO_BUSY) {
    // data.result = res.getString(R.string.sync_too_busy);
    // } else {
    // data.result = res.getString(R.string.login_generic_error);
    // }
    // data.success = false;
    // return data;
    // }
    //
    // data.result = server.getPersonalDecks();
    // } catch (Exception e) {
    // data.success = false;
    // data.result = null;
    // data.exception = e;
    // Log.e(AnkiDroidApp.TAG, "doInBackgroundGetPersonalDecks - Error getting personal decks = " + e.getMessage());
    // Log.e(AnkiDroidApp.TAG, Log.getStackTraceString(e));
    // }
    // return data;
    // }

    public static final class OldAnkiDeckFilter implements FileFilter {
        @Override
        public boolean accept(File pathname) {
            return pathname.isFile() && pathname.getName().endsWith(".anki");
        }
    }

    public class CancelCallback {
        private WeakReference<ThreadSafeClientConnManager> mConnectionManager = null;


        public void setConnectionManager(ThreadSafeClientConnManager connectionManager) {
            mConnectionManager = new WeakReference<ThreadSafeClientConnManager>(connectionManager);
        }


        public void cancelAllConnections() {
            if (mConnectionManager != null) {
                ThreadSafeClientConnManager connectionManager = mConnectionManager.get();
                if (connectionManager != null) {
                    connectionManager.shutdown();
                }
            }
        }
    }
}
