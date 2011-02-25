/***************************************************************************************
 * Copyright (c) 2009 Edu Zamora <edu.zasu@gmail.com>                                   *
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
import android.content.ContextWrapper;
import android.content.res.Resources;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.os.AsyncTask;
import android.util.Log;

import com.ichi2.anki.AnkiDatabaseManager;
import com.ichi2.anki.AnkiDb;
import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.AnkiDroidProxy;
import com.ichi2.anki.Deck;
import com.ichi2.anki.Feedback;
import com.ichi2.anki.R;
import com.ichi2.anki.SyncClient;
import com.ichi2.anki.Utils;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class Connection extends AsyncTask<Connection.Payload, Object, Connection.Payload> {

    public static final int TASK_TYPE_LOGIN = 0;
    public static final int TASK_TYPE_GET_SHARED_DECKS = 1;
    public static final int TASK_TYPE_GET_PERSONAL_DECKS = 2;
    public static final int TASK_TYPE_SYNC_ALL_DECKS = 3;
    public static final int TASK_TYPE_SYNC_DECK = 4;
    public static final int TASK_TYPE_SYNC_DECK_FROM_PAYLOAD = 5;
    public static final int TASK_TYPE_SEND_CRASH_REPORT = 6;
    public static final int TASK_TYPE_DOWNLOAD_MEDIA = 7;

    private static Context sContext;

    private static Connection sInstance;
    private TaskListener mListener;
    
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

        return (Connection) sInstance.execute(data);
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


    public static Connection login(TaskListener listener, Payload data) {
        data.taskType = TASK_TYPE_LOGIN;
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

    public static void cancelGetDecks() {
    	sInstance.cancel(true);
    }


    public static Connection syncAllDecks(TaskListener listener, Payload data) {
        data.taskType = TASK_TYPE_SYNC_ALL_DECKS;
        return launchConnectionTask(listener, data);
    }


    public static Connection syncDeck(TaskListener listener, Payload data) {
        data.taskType = TASK_TYPE_SYNC_DECK;
        return launchConnectionTask(listener, data);
    }


    public static Connection syncDeckFromPayload(TaskListener listener, Payload data) {
        data.taskType = TASK_TYPE_SYNC_DECK_FROM_PAYLOAD;
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

    
    @Override
    protected Payload doInBackground(Payload... params) {
        Payload data = params[0];

        switch (data.taskType) {
            case TASK_TYPE_LOGIN:
                return doInBackgroundLogin(data);

            case TASK_TYPE_GET_SHARED_DECKS:
                return doInBackgroundGetSharedDecks(data);

            case TASK_TYPE_GET_PERSONAL_DECKS:
                return doInBackgroundGetPersonalDecks(data);

            case TASK_TYPE_SYNC_ALL_DECKS:
                return doInBackgroundSyncAllDecks(data);

            case TASK_TYPE_SYNC_DECK:
                return doInBackgroundSyncDeck(data);

            case TASK_TYPE_SYNC_DECK_FROM_PAYLOAD:
                return doInBackgroundSyncDeckFromPayload(data);

            case TASK_TYPE_SEND_CRASH_REPORT:
                return doInBackgroundSendFeedback(data);
                
            case TASK_TYPE_DOWNLOAD_MEDIA:
                return doInBackgroundDownloadMissingMedia(data);

            default:
                return null;
        }
    }


    private Payload doInBackgroundLogin(Payload data) {
        try {
            String username = (String) data.data[0];
            String password = (String) data.data[1];
            AnkiDroidProxy server = new AnkiDroidProxy(username, password);

            int status = server.connect();
            if (status != AnkiDroidProxy.LOGIN_OK) {
                data.success = false;
                data.returnType = status;
            }
        } catch (Exception e) {
            data.success = false;
            data.exception = e;
            Log.e(AnkiDroidApp.TAG, "Error trying to log in");
        }
        return data;
    }


    private Payload doInBackgroundGetSharedDecks(Payload data) {
        try {
            data.result = AnkiDroidProxy.getSharedDecks();
        } catch (Exception e) {
            data.success = false;
            data.exception = e;
            Log.e(AnkiDroidApp.TAG, "Error getting shared decks = " + e.getMessage());
            e.printStackTrace();
        }
        return data;
    }


    private Payload doInBackgroundGetPersonalDecks(Payload data) {
        try {
            String username = (String) data.data[0];
            String password = (String) data.data[1];
            AnkiDroidProxy server = new AnkiDroidProxy(username, password);
            data.result = server.getPersonalDecks();
        } catch (Exception e) {
            data.success = false;
            data.exception = e;
            Log.e(AnkiDroidApp.TAG, "Error getting personal decks = " + e.getMessage());
            e.printStackTrace();
        }
        return data;
    }


    private Payload doInBackgroundSyncAllDecks(Payload data) {
        Log.i(AnkiDroidApp.TAG, "doInBackgroundSyncAllDecks");
        ArrayList<HashMap<String, String>> decksChangelogs = new ArrayList<HashMap<String, String>>();

        String username = (String) data.data[0];
        String password = (String) data.data[1];
        //Log.i(AnkiDroidApp.TAG, "username = " + username);
        //Log.i(AnkiDroidApp.TAG, "password = " + password);

        ArrayList<HashMap<String, String>> decksToSync = (ArrayList<HashMap<String, String>>) data.data[2];
        for (HashMap<String, String> deckToSync : decksToSync) {
            Log.i(AnkiDroidApp.TAG, "Synchronizing deck");
            String deckPath = deckToSync.get("filepath");
            try {
                Deck deck = Deck.openDeck(deckPath);

                Payload syncDeckData = new Payload(new Object[] { username, password, deck, deckPath, null });
                syncDeckData = doInBackgroundSyncDeck(syncDeckData);
                decksChangelogs.add((HashMap<String, String>) syncDeckData.result);
            } catch (Exception e) {
                Log.e(AnkiDroidApp.TAG, "Exception e = " + e.getMessage());
                // Probably, there was an error trying to open the deck, so we can not retrieve the deck name from it
                String deckName = deckPath.substring(deckPath.lastIndexOf("/") + 1);
                deckName = deckName.substring(0, deckName.length() - ".anki".length());

                // Create sync changelog and add it to the list
                HashMap<String, String> deckChangelog = new HashMap<String, String>();
                deckChangelog.put("deckName", deckName);
                deckChangelog.put("message", sContext.getResources().getString(R.string.sync_log_error_message));

                decksChangelogs.add(deckChangelog);
            }
        }

        data.result = decksChangelogs;
        return data;
    }

    

    private Payload doInBackgroundSyncDeck(Payload data) {
        Resources res = sContext.getResources();
        HashMap<String, String> syncChangelog = new HashMap<String, String>();
        String username = (String) data.data[0];
        String password = (String) data.data[1];
        Deck deck = (Deck) data.data[2];
        String deckPath = (String) data.data[3];
        String syncName = deckPath.substring(deckPath.lastIndexOf("/") + 1, deckPath.length() - 5);
        String conflictResolution = (String) data.data[4];

        syncChangelog.put("deckName", syncName);

        AnkiDb ankiDB = AnkiDatabaseManager.getDatabase(deckPath);
        ankiDB.getDatabase().beginTransaction();
        try {
            // Log.i(AnkiDroidApp.TAG, "Starting sync: username = " + username + ", password = " + password
            //         + ", deckPath = " + deckPath + ", syncName = " + syncName);
            AnkiDroidProxy server = new AnkiDroidProxy(username, password);

            publishProgress(syncName, res.getString(R.string.sync_connecting_message));
            int connectResult = server.connect();
            if (connectResult != AnkiDroidProxy.LOGIN_OK) {
                if (connectResult == AnkiDroidProxy.LOGIN_INVALID_USER_PASS) {
                    syncChangelog.put("message", res.getString(R.string.invalid_username_password));
                } else if (connectResult == AnkiDroidProxy.LOGIN_CLOCKS_UNSYNCED) {
                    syncChangelog.put("message", String.format(res.getString(R.string.sync_log_clocks_unsynchronized),
                            (new Double(server.getTimediff())).longValue()));
                } else if (connectResult == AnkiDroidProxy.LOGIN_OLD_VERSION) {
                    syncChangelog.put("message", String.format(res.getString(R.string.sync_log_old_version), res.getString(R.string.link_ankidroid)));
                }
                data.result = syncChangelog;
                data.success = false;
                return data;
            }

            // Exists on server?
            if (!server.hasDeck(syncName)) {
                Log.i(AnkiDroidApp.TAG, "AnkiOnline does not have this deck: Creating it...");
                server.createDeck(syncName);
            }
            publishProgress(syncName, res.getString(R.string.sync_syncing_message, new Object[] { syncName }));
            SyncClient client = new SyncClient(deck);
            client.setServer(server);
            server.setDeckName(syncName);
            
            // Check conflicts
            double localMod = deck.getModified();
            double localSync = deck.getLastSync();
            double remoteMod = server.modified();
            double remoteSync = server.lastSync();
            double minSync = Math.min(localSync, remoteSync);
            if ((localMod != remoteMod) && (minSync > 0) &&
                    (localMod > minSync) && (remoteMod > minSync)) {
                if (conflictResolution == null) {
                    Log.i(AnkiDroidApp.TAG, "Syncing needs conflict resolution user input...");
                    data.success = false;
                    data.returnType = AnkiDroidProxy.SYNC_CONFLICT_RESOLUTION;
                    syncChangelog.put("message", res.getString(R.string.sync_log_conflict_resolution_required));
                    data.result = syncChangelog;
                    return data;
                }
            }
            if (client.prepareSync(server.getTimediff())) {
                if (deck.getLastSync() <= 0) {
                    if (client.getRemoteTime() > client.getLocalTime()) {
                        conflictResolution = "keepRemote";
                    } else {
                        conflictResolution = "keepLocal";
                    }
                }

                // summary
                JSONArray sums = null;
                if (conflictResolution == null) {
                    publishProgress(syncName, res.getString(R.string.sync_summary_from_server_message));
                    sums = client.summaries();
                }

                if ((conflictResolution != null) || client.needFullSync(sums)) {
                    Log.i(AnkiDroidApp.TAG, "DECK NEEDS FULL SYNC");

                    publishProgress(syncName, res.getString(R.string.sync_preparing_full_sync_message));

                    if (conflictResolution != null) {
                        if (conflictResolution.equals("keepLocal")) {
                            client.setRemoteTime(0.0);
                        } else if (conflictResolution.equals("keepRemote")) {
                            client.setLocalTime(0.0);
                        }
                    }
                    
                    String syncFrom = client.prepareFullSync();

                    HashMap<String, String> result = new HashMap<String, String>();
                    if ("fromLocal".equalsIgnoreCase(syncFrom)) {
                        publishProgress(syncName, res.getString(R.string.sync_uploading_message));
                        result = SyncClient.fullSyncFromLocal(password, username, deck, syncName);
                        if (result.containsKey("code") && result.get("code").equals("200")) {
                            syncChangelog.put("message", res.getString(R.string.sync_log_uploading_message));
                        }
                        ankiDB.getDatabase().setTransactionSuccessful();
                        ankiDB.getDatabase().endTransaction();
                    } else if ("fromServer".equalsIgnoreCase(syncFrom)) {
                        publishProgress(syncName, res.getString(R.string.sync_downloading_message));
                        ankiDB.getDatabase().endTransaction();
                        if (deck != null) {
                            deck.closeDeck();
                        }
                        result = SyncClient.fullSyncFromServer(password, username, syncName, deckPath);
                        if (result.containsKey("code") && result.get("code").equals("200")) {
                            syncChangelog.put("message", res.getString(R.string.sync_log_downloading_message));
                        }
                    }

                    publishProgress(syncName, res.getString(R.string.sync_complete_message));
                    // Pass error (if any) to UI
                    if (!result.containsKey("code") || !result.get("code").equals("200")) {
                        if (result.containsKey("message")) {
                        syncChangelog.put("message", String.format(
                                res.getString(R.string.sync_log_error_specific),
                                result.get("code"), result.get("message")));
                        } else {
                            syncChangelog.put("message", res.getString(R.string.sync_log_error_message));
                        }
                    }
                } else {
                    Log.i(AnkiDroidApp.TAG, "DECK DOES NOT NEED FULL SYNC");

                    publishProgress(syncName, res.getString(R.string.sync_determining_differences_message));

                    JSONObject payload = client.genPayload(sums);
                    int factsAddedOnLocal = payload.getJSONArray("added-cards").length();
                    if (factsAddedOnLocal == 1) {
                        syncChangelog.put("message", res.getString(R.string.sync_log_fact_to_server_message));
                    } else if (factsAddedOnLocal > 1) {
                        syncChangelog.put("message",
                                res.getString(R.string.sync_log_facts_to_server_message, factsAddedOnLocal));
                    }

                    publishProgress(syncName, res.getString(R.string.sync_transferring_payload_message));
                    JSONObject payloadReply = client.getServer().applyPayload(payload);
                    int factsAddedOnServer = payloadReply.getJSONArray("added-cards").length();
                    if (factsAddedOnServer == 1) {
                        syncChangelog.put("message", res.getString(R.string.sync_log_fact_from_server_message));
                    } else if (payloadReply.getJSONArray("added-cards").length() > 1) {
                        syncChangelog.put("message",
                                res.getString(R.string.sync_log_facts_from_server_message, factsAddedOnServer));
                    }

                    publishProgress(syncName, res.getString(R.string.sync_applying_reply_message));
                    client.applyPayloadReply(payloadReply);
                    client.getServer().finish();
                    deck.reset();

                    deck.setLastLoaded(deck.getModified());
                    deck.commitToDB();
                    Log.i(AnkiDroidApp.TAG, String.format(Utils.ENGLISH_LOCALE, "Modified: %f, LastSync: %f, LastLoaded: %f", deck.getModified(), deck.getLastSync(), deck.getLastLoaded()));

                    ankiDB.getDatabase().setTransactionSuccessful();
                    publishProgress(syncName, res.getString(R.string.sync_complete_message));
                }
            } else {
                Log.i(AnkiDroidApp.TAG, "NO CHANGES.");
                publishProgress(syncName, res.getString(R.string.sync_no_changes_message));
                syncChangelog.put("message", res.getString(R.string.sync_log_no_changes_message));
            }
        } catch (Exception e) {
            Log.e(AnkiDroidApp.TAG, "Error synchronizing deck = " + e.getMessage());
            Log.e(AnkiDroidApp.TAG, Log.getStackTraceString(e));
            syncChangelog.put("message", res.getString(R.string.sync_log_error_message));
            data.success = false;
            data.exception = e;
        } finally {
            if (ankiDB.getDatabase() != null && ankiDB.getDatabase().inTransaction()) {
                ankiDB.getDatabase().endTransaction();
            }

            if (deck != null) {
                deck.closeDeck();
            }
        }

        data.result = syncChangelog;
        return data;
    }


    private Payload doInBackgroundSyncDeckFromPayload(Payload data) {
        Log.i(AnkiDroidApp.TAG, "SyncDeckFromPayload");
        Deck deck = (Deck) data.data[0];
        SyncClient client = new SyncClient(deck);
        BufferedReader bufPython;
        try {
            bufPython = new BufferedReader(new FileReader("/sdcard/jsonObjectPython.txt"));
            JSONObject payloadReply = new JSONObject(bufPython.readLine());
            client.applyPayloadReply(payloadReply);
            deck.setLastLoaded(deck.getModified());
            deck.commitToDB();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        Log.i(AnkiDroidApp.TAG, "Synchronization from payload finished!");
        return data;
    }


    private Payload doInBackgroundSendFeedback(Payload data) {
        Log.i(AnkiDroidApp.TAG, "doInBackgroundSendFeedback");
        String feedbackUrl = (String) data.data[0];
        String errorUrl = (String) data.data[1];
        String feedback  = (String) data.data[2];
        ArrayList<HashMap<String, String>> errors  = (ArrayList<HashMap<String, String>>) data.data[3];
        String groupId  = ((Long) data.data[4]).toString();
        Application app  = (Application) data.data[5];

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
                Payload reply = Feedback.postFeedback(errorUrl, postType, error.get("name"), groupId, i, app);
                if (reply.success) {
                    publishProgress(postType, i, Feedback.STATE_SUCCESSFUL, reply.returnType, reply.result);
                } else {
                    publishProgress(postType, i, Feedback.STATE_FAILED, reply.returnType, reply.result);
                }
            }
        }

        app = null;

        return data;
    }
    
    /**
     * Downloads any missing media files according to the mediaURL deckvar.
     * @param data
     * @return The return type contains data.resultType and an array of Integer
     * in data.data. data.data[0] is the number of total missing media, data.data[1] is the number
     * of downloaded ones.
     */
    private Payload doInBackgroundDownloadMissingMedia(Payload data) {
        Log.i(AnkiDroidApp.TAG, "DownloadMissingMedia");
        Deck deck = (Deck) data.data[0];
        
        data.success = false;
        data.data = new Integer[] {0, 0, 0};
        if (!deck.hasKey("mediaURL")) {
            data.success = true;
            return data;
        }
        String urlbase = deck.getVar("mediaURL");
        if (urlbase.equals("")) {
            data.success = true;
            return data;
        }

        String mdir = deck.mediaDir(true);
        int totalMissing = 0;
        int missing = 0;
        int grabbed = 0;

        HashMap<String, String> missingPaths = new HashMap<String, String>();
        HashMap<String, String> missingSums = new HashMap<String, String>();
        Cursor cursor = null;
        try {
            cursor = deck.getDB().getDatabase().rawQuery("SELECT filename, originalPath FROM media", null);
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
            deck.closeDeck();
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
        publishProgress(Boolean.FALSE, new Integer(totalMissing), new Integer(0));

        URL url = null;
        HttpURLConnection connection = null;
        String path = null;
        String sum = null;
        int readbytes = 0;
        byte[] buf = new byte[4096];
        for (String file : missingPaths.keySet()) {
            
            try {
                android.net.Uri uri = android.net.Uri.parse(urlbase + file);
                url = new URI(uri.getScheme(), uri.getHost(), uri.getPath(), null).toURL();
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
                    if (sum.equals("") || sum.equals(Utils.fileChecksum(path))) {
                        grabbed++;
                    } else {
                        // Download corrupted, delete file
                        Log.i(AnkiDroidApp.TAG, "Downloaded media file " + path + " failed checksum.");
                        File f = new File(path);
                        f.delete();
                        missing++;
                    }
                } else {
                    Log.e(AnkiDroidApp.TAG, "Connection error (" + connection.getResponseCode() +
                            ") while retrieving media file " + urlbase + file);
                    Log.e(AnkiDroidApp.TAG, "Connection message: " + connection.getResponseMessage());
                    if (missingSums.get(file).equals("")) {
                        // Ignore and keep going
                        missing++;
                    } else {
                        data.success = false;
                        data.data[0] = file;
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
                    data.data[0] = file;
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
                    data.data[0] = file;
                    return data;
                }
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
            publishProgress(Boolean.TRUE, new Integer(totalMissing), new Integer(grabbed + missing));
        }

        data.data[1] = new Integer(grabbed);
        data.data[2] = new Integer(missing);
        data.success = true;
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

    public static class Payload {
        public int taskType;
        public Object[] data;
        public Object result;
        public boolean success;
        public int returnType;
        public Exception exception;


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
}
