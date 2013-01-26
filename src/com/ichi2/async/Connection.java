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
import android.database.sqlite.SQLiteDatabaseCorruptException;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;

import com.ichi2.anki.AnkiDatabaseManager;
import com.ichi2.anki.AnkiDb;
import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.Feedback;
import com.ichi2.anki.R;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Decks;
import com.ichi2.libanki.Sched;
import com.ichi2.libanki.Utils;
import com.ichi2.libanki.sync.FullSyncer;
import com.ichi2.libanki.sync.BasicHttpSyncer;
import com.ichi2.libanki.sync.MediaSyncer;
import com.ichi2.libanki.sync.RemoteMediaServer;
import com.ichi2.libanki.sync.RemoteServer;
import com.ichi2.libanki.sync.Syncer;

import org.apache.commons.httpclient.contrib.ssl.EasyX509TrustManager;
import org.apache.http.HttpResponse;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.json.JSONException;
import org.json.JSONObject;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.OutOfMemoryError;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class Connection extends AsyncTask<Connection.Payload, Object, Connection.Payload> {

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
        if (mListener != null) {
            mListener.onPreExecute();
        }
    }


    /*
     * Runs on GUI thread
     */
    @Override
    protected void onPostExecute(Payload data) {
        if (mListener != null) {
            mListener.onPostExecute(data);
        }
    }


    /*
     * Runs on GUI thread
     */
    @Override
    protected void onProgressUpdate(Object... values) {
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
        if (params.length != 1)
            throw new IllegalArgumentException();
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
                return doInBackgroundUpgradeDecks(data);

            case TASK_TYPE_DOWNLOAD_SHARED_DECK:
            	return doInBackgroundDownloadSharedDeck(data);

            default:
                return null;
        }
    }


    private Payload doInBackgroundLogin(Payload data) {
        String username = (String) data.data[0];
        String password = (String) data.data[1];
        BasicHttpSyncer server = new RemoteServer(this, null);
        HttpResponse ret = server.hostKey(username, password);
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


    private Payload doInBackgroundUpgradeDecks(Payload data) {
        // Enable http request canceller
        mCancelCallback = new CancelCallback();

        String path = (String) data.data[0];
        File ankiDir = new File(path);
        if (!ankiDir.isDirectory()) {
            data.success = false;
            data.data = new Object[] { "wrong anki directory" };
            return data;
        }

        // step 1: gather all .anki files into a zip, without media.
        // we must store them as 1.anki, 2.anki and provide a map so we don't run into
        // encoding issues with the zip file.
        File[] fileList = ankiDir.listFiles(new OldAnkiDeckFilter());
        List<String> corruptFiles = new ArrayList<String>();
        JSONObject map = new JSONObject();
        byte[] buf = new byte[1024];
        String zipFilename = path + "/upload.zip";
        String colFilename = path + AnkiDroidApp.COLLECTION_PATH;
        try {
            ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFilename));
            int n = 1;
            for (File f : fileList) {
                String deckPath = f.getAbsolutePath();
                // set journal mode to delete
                try {
                    AnkiDb d = AnkiDatabaseManager.getDatabase(deckPath);
                    d.queryString("PRAGMA journal_mode = DELETE");
                } catch (SQLiteDatabaseCorruptException e) {
                    // ignore invalid .anki files
                    corruptFiles.add(f.getName());
                    continue;
                } finally {
                    AnkiDatabaseManager.closeDatabase(deckPath);
                }
                // zip file
                String tmpName = n + ".anki";
                FileInputStream in = new FileInputStream(deckPath);
                ZipEntry ze = new ZipEntry(tmpName);
                zos.putNextEntry(ze);
                int len;
                while ((len = in.read(buf)) >= 0) {
                    zos.write(buf, 0, len);
                }
                zos.closeEntry();
                map.put(tmpName, f.getName());
                n++;
            }
            // if all .anki files were found corrupted, abort
            if (fileList.length == corruptFiles.size()) {
                data.success = false;
                data.data = new Object[] { sContext.getString(R.string.upgrade_deck_web_upgrade_failed) };
                return data;
            }
            ZipEntry ze = new ZipEntry("map.json");
            zos.putNextEntry(ze);
            InputStream in = new ByteArrayInputStream(Utils.jsonToString(map).getBytes("UTF-8"));
            int len;
            while ((len = in.read(buf)) >= 0) {
                zos.write(buf, 0, len);
            }
            zos.closeEntry();
            zos.close();
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        File zipFile = new File(zipFilename);
        // step 1.1: if it's over 50MB compressed, it must be upgraded by the user
        if (zipFile.length() > 50 * 1024 * 1024) {
            data.success = false;
            data.data = new Object[] { sContext.getString(R.string.upgrade_deck_web_upgrade_exceeds) };
            return data;
        }
        // step 2: upload zip file to upgrade service and get token
        BasicHttpSyncer h = new BasicHttpSyncer(null, null);
        // note: server doesn't expect it to be gzip compressed, because the zip file is compressed
        // enable cancelling
        publishProgress(R.string.upgrade_decks_upload, null, true);
        try {
            HttpResponse resp = h.req("upgrade/upload", new FileInputStream(zipFile), 0, false, null, mCancelCallback);
            if (resp == null && !isCancelled()) {
                data.success = false;
                data.data = new Object[] { sContext.getString(R.string.upgrade_deck_web_upgrade_failed) };
                return data;
            }
            String result;
            String key = null;
            if (!isCancelled()) {
                result = h.stream2String(resp.getEntity().getContent());
                if (result != null && result.startsWith("ok:")) {
                    key = result.split(":")[1];
                } else {
                    data.success = false;
                    data.data = new Object[] { sContext.getString(R.string.upgrade_deck_web_upgrade_failed) };
                    return data;
                }
            }
            while (!isCancelled()) {
                result = h.stream2String(h.req("upgrade/status?key=" + key).getEntity().getContent());
                if (result.equals("error")) {
                    data.success = false;
                    data.data = new Object[] { "error" };
                    return data;
                } else if (result.startsWith("waiting:")) {
                    publishProgress(R.string.upgrade_decks_upload, result.split(":")[1]);
                } else if (result.equals("upgrading")) {
                    publishProgress(new Object[] { R.string.upgrade_decks_upgrade_started });
                } else if (result.equals("ready")) {
                    break;
                } else {
                    data.success = false;
                    data.data = new Object[] { sContext.getString(R.string.upgrade_deck_web_upgrade_failed) };
                    return data;
                }
                Thread.sleep(1000);
            }
            // step 4: fetch upgraded file. this will return the .anki2 file directly, with
            // gzip compression if the client says it can handle it
            if (!isCancelled()) {
                publishProgress(new Object[] { R.string.upgrade_decks_downloading });
                resp = h.req("upgrade/download?key=" + key, null, 6, true, null, mCancelCallback);
                // uploads/downloads have finished so disable cancelling
            }
            publishProgress(R.string.upgrade_decks_downloading, null, false);
            if (isCancelled()) {
                return null;
            }
            if (resp == null) {
                data.success = false;
                data.data = new Object[] { sContext.getString(R.string.upgrade_deck_web_upgrade_failed) };
                return data;
            }
            // step 5: check the received file is valid
            InputStream cont = resp.getEntity().getContent();
            if (!h.writeToFile(cont, colFilename)) {
                data.success = false;
                data.data = new Object[] { sContext.getString(R.string.upgrade_deck_web_upgrade_sdcard,
                        new File(colFilename).length() / 1048576 + 1) };
                (new File(colFilename)).delete();
                return data;
            }
            // check the received file is ok
            publishProgress(new Object[] { R.string.sync_check_download_file });
            publishProgress(R.string.sync_check_download_file);
            try {
                AnkiDb d = AnkiDatabaseManager.getDatabase(colFilename);
                if (!d.queryString("PRAGMA integrity_check").equalsIgnoreCase("ok")) {
                    data.success = false;
                    data.data = new Object[] { sContext.getResources() };
                    return data;
                }
            } finally {
                AnkiDatabaseManager.closeDatabase(colFilename);
            }
            Collection col = AnkiDroidApp.openCollection(colFilename);
            ArrayList<String> decks = col.getDecks().allNames(false);
            ArrayList<String> failed = new ArrayList<String>();
            ArrayList<File> mediaDirs = new ArrayList<File>();
            for (File f : fileList) {
                String name = f.getName().replaceFirst("\\.anki$", "");
                if (!decks.contains(name)) {
                    failed.add(name);
                } else {
                    mediaDirs.add(new File(f.getAbsolutePath().replaceFirst("\\.anki$", ".media")));
                }
            }
            File newMediaDir = new File(col.getMedia().getDir());

            // step 6. move media files to new media directory
            publishProgress(new Object[] { R.string.upgrade_decks_media });
            ArrayList<String> failedMedia = new ArrayList<String>();
            File curMediaDir = null;
            for ( File mediaDir : mediaDirs) {
                curMediaDir = mediaDir;
                // Check if media directory exists and is local
                if (!curMediaDir.exists() || !curMediaDir.isDirectory()) {
                    // If not try finding it in dropbox 1.2.x
                    curMediaDir = new File(AnkiDroidApp.getDropboxDir(), mediaDir.getName());
                    if (!curMediaDir.exists() || !curMediaDir.isDirectory()) {
                        // No media for this deck
                        continue;
                    }
                }
                // Found media dir, copy files
                for (File m : curMediaDir.listFiles()) {
                    try {
                        Utils.copyFile(m, new File(newMediaDir, m.getName()));
                    } catch (IOException e) {
                        failedMedia.add(curMediaDir.getName().replaceFirst("\\.media$", ".anki"));
                        break;
                    }
                }
            }

            data.data = new Object[] { failed, failedMedia, newMediaDir.getAbsolutePath()};
            data.success = true;
            return data;
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } catch (IllegalStateException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            (new File(zipFilename)).delete();
        }
    }


    private Payload doInBackgroundRegister(Payload data) {
        String username = (String) data.data[0];
        String password = (String) data.data[1];
        BasicHttpSyncer server = new RemoteServer(this, null);
        HttpResponse ret = server.register(username, password);
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
            data.data = new String[] { status != null ? status : AnkiDroidApp.getAppResources().getString(R.string.connection_error_message)};
        }
        return data;
    }


    private Payload doInBackgroundSync(Payload data) {
        // for for doInBackgroundLoadDeckCounts if any
        DeckTask.waitToFinish();

        String hkey = (String) data.data[0];
        boolean media = (Boolean) data.data[1];
        String conflictResolution = (String) data.data[2];
        int mediaUsn = (Integer) data.data[3];

        boolean colCorruptFullSync = false;
        Collection col = AnkiDroidApp.getCol();
        if (!AnkiDroidApp.colIsOpen()) {
        	if (conflictResolution != null && conflictResolution.equals("download")) {
        		colCorruptFullSync = true;
        	} else {
                data.success = false;
                data.result = new Object[] { "genericError" };
                return data;
        	}
        }
        String path = AnkiDroidApp.getCollectionPath();

        BasicHttpSyncer server = new RemoteServer(this, hkey);
        Syncer client = new Syncer(col, server);

        // run sync and check state
        boolean noChanges = false;
        if (conflictResolution == null) {
            Log.i(AnkiDroidApp.TAG, "Sync - starting sync");
            publishProgress(R.string.sync_prepare_syncing);
            Object[] ret = client.sync(this);
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
                return data;
            }
            // save and note success state
            if (retCode.equals("noChanges")) {
                // publishProgress(R.string.sync_no_changes_message);
                noChanges = true;
            } else {
                // publishProgress(R.string.sync_database_success);
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
                    if (!((String) ret[0]).equals(BasicHttpSyncer.ANKIWEB_STATUS_OK)) {
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
                    if (!((String) ret[0]).equals("success")) {
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
                data.result = new Object[]{"OutOfMemoryError"};
                data.data = new Object[] { mediaUsn };
                return data;
            } catch (RuntimeException e) {
            	AnkiDroidApp.saveExceptionReportFile(e, "doInBackgroundSync-fullSync");
                data.success = false;
                data.result = new Object[]{"IOException"};
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
            server = new RemoteMediaServer(hkey, this);
            MediaSyncer mediaClient = new MediaSyncer(col, (RemoteMediaServer) server);
            String ret;
            try {
                ret = mediaClient.sync(mediaUsn, this);
                if (ret == null) {
                    mediaError = AnkiDroidApp.getAppResources().getString(R.string.sync_media_error);
                } else {
                    if (ret.equals("noChanges")) {
                        publishProgress(R.string.sync_media_no_changes);
                        noMediaChanges = true;
                    } if (ret.equals("sanityFailed")) {
                        mediaError = AnkiDroidApp.getAppResources().getString(R.string.sync_media_sanity_failed);
                    } else {
                        publishProgress(R.string.sync_media_success);
                    }
                }
            } catch (RuntimeException e) {
               AnkiDroidApp.saveExceptionReportFile(e, "doInBackgroundSync-mediaSync");
               mediaError = e.getLocalizedMessage();
            }
        }
        if (noChanges && noMediaChanges) {
            data.success = false;
            data.result = new Object[] { "noChanges" };
            return data;
        } else {
            data.success = true;
            TreeSet<Object[]> decks = col.getSched().deckDueTree(Sched.DECK_INFORMATION_SIMPLE_COUNTS);
            int[] counts = new int[] { 0, 0, 0 };
            for (Object[] deck : decks) {
                if (((String[]) deck[0]).length == 1) {
                    counts[0] += (Integer) deck[2];
                    counts[1] += (Integer) deck[3];
                    counts[2] += (Integer) deck[4];
                }
            }
            Object[] dc = col.getSched().deckCounts();
            data.result = dc[0];
            data.data = new Object[] { conflictResolution, col, dc[1], dc[2], mediaError };
            return data;
        }
    }

    

    public void publishProgress(int id) {
        super.publishProgress(id);
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
        String groupId = ((Long) data.data[4]).toString();
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

        Decks deck = (Decks) data.data[0];
        data.result = deck; // pass it to the return object so we close the deck in the deck picker
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
        data.data[0] = new Integer(totalMissing);
        if (totalMissing == 0) {
            data.success = true;
            return data;
        }
        publishProgress(Boolean.FALSE, new Integer(totalMissing), new Integer(0), syncName);

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
            publishProgress(Boolean.TRUE, new Integer(totalMissing), new Integer(grabbed + missing), syncName);
        }

        data.data[1] = new Integer(grabbed);
        data.data[2] = new Integer(missing);
        data.success = true;
        return data;
    }


    private Payload doInBackgroundDownloadSharedDeck(Payload data) {
        String url = (String) data.data[0];
        String colFilename = AnkiDroidApp.getCurrentAnkiDroidDirectory() + "/tmpImportFile.apkg";
        URL fileUrl;
        HttpsURLConnection conn;
        InputStream cont = null;
		try {
			fileUrl = new URL(url);
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, new TrustManager[] { new EasyX509TrustManager(null) }, null);
	        conn = (HttpsURLConnection)fileUrl.openConnection();
            conn.setSSLSocketFactory(context.getSocketFactory());
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
                output.close();
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
            if (pathname.isFile() && pathname.getName().endsWith(".anki")) {
                return true;
            }
            return false;
        }
    }

    public class CancelCallback {
        private WeakReference<ThreadSafeClientConnManager> mConnectionManager;

        public void setConnectionManager(ThreadSafeClientConnManager connectionManager) {
            mConnectionManager = new WeakReference<ThreadSafeClientConnManager>(connectionManager);
        }

        public void cancelAllConnections() {
            ThreadSafeClientConnManager connectionManager = mConnectionManager.get();
            if (connectionManager != null) {
                connectionManager.shutdown();
            }
        }
    }
}
