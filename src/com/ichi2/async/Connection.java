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

import android.content.Context;
import android.content.res.Resources;
import android.net.ConnectivityManager;
import android.os.AsyncTask;
import android.util.Log;

import com.ichi2.anki.AnkiDatabaseManager;
import com.ichi2.anki.AnkiDb;
import com.ichi2.anki.AnkiDroidProxy;
import com.ichi2.anki.Deck;
import com.ichi2.anki.R;
import com.ichi2.anki.SyncClient;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

public class Connection extends AsyncTask<Connection.Payload, Object, Connection.Payload> {
    public static final String TAG = "Connection";
    public static Context context;

    public static final int TASK_TYPE_LOGIN = 0;
    public static final int TASK_TYPE_GET_SHARED_DECKS = 1;
    public static final int TASK_TYPE_GET_PERSONAL_DECKS = 2;
    public static final int TASK_TYPE_SYNC_ALL_DECKS = 3;
    public static final int TASK_TYPE_SYNC_DECK = 4;
    public static final int TASK_TYPE_SYNC_DECK_FROM_PAYLOAD = 5;

    private static Connection instance;
    private TaskListener listener;


    private static Connection launchConnectionTask(TaskListener listener, Payload data) {

        if (!isOnline()) {
            data.success = false;
            listener.onDisconnected();
            return null;
        }

        try {
            if ((instance != null) && (instance.getStatus() != AsyncTask.Status.FINISHED)) {
                instance.get();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        instance = new Connection();
        instance.listener = listener;

        return (Connection) instance.execute(data);
    }


    /*
     * Runs on GUI thread
     */
    @Override
    protected void onPreExecute() {
        if (listener != null) {
            listener.onPreExecute();
        }
    }


    /*
     * Runs on GUI thread
     */
    @Override
    public void onPostExecute(Payload data) {
        if (listener != null) {
            listener.onPostExecute(data);
        }
    }


    /*
     * Runs on GUI thread
     */
    @Override
    public void onProgressUpdate(Object... values) {
        if (listener != null) {
            listener.onProgressUpdate(values);
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
            Log.e(TAG, "Error trying to log in");
        }
        return data;
    }


    private Payload doInBackgroundGetSharedDecks(Payload data) {
        try {
            data.result = AnkiDroidProxy.getSharedDecks();
        } catch (Exception e) {
            data.success = false;
            data.exception = e;
            Log.e(TAG, "Error getting shared decks = " + e.getMessage());
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
            Log.e(TAG, "Error getting personal decks = " + e.getMessage());
            e.printStackTrace();
        }
        return data;
    }


    private Payload doInBackgroundSyncAllDecks(Payload data) {
        Log.i(TAG, "doInBackgroundSyncAllDecks");
        ArrayList<HashMap<String, String>> decksChangelogs = new ArrayList<HashMap<String, String>>();

        String username = (String) data.data[0];
        String password = (String) data.data[1];
        Log.i(TAG, "username = " + username);
        Log.i(TAG, "password = " + password);

        ArrayList<HashMap<String, String>> decksToSync = (ArrayList<HashMap<String, String>>) data.data[2];
        for (int i = 0; i < decksToSync.size(); i++) {
            Log.i(TAG, "Synchronizing deck " + i);
            String deckPath = decksToSync.get(i).get("filepath");
            try {
                Deck deck = Deck.openDeck(deckPath);

                Payload syncDeckData = new Payload(new Object[] { username, password, deck, deckPath });
                syncDeckData = doInBackgroundSyncDeck(syncDeckData);
                decksChangelogs.add((HashMap<String, String>) syncDeckData.result);
            } catch (Exception e) {
                Log.e(TAG, "Exception e = " + e.getMessage());
                // Probably, there was an error trying to open the deck, so we can not retrieve the deck name from it
                String deckName = deckPath.substring(deckPath.lastIndexOf("/") + 1);
                deckName = deckName.substring(0, deckName.length() - ".anki".length());

                // Create sync changelog and add it to the list
                HashMap<String, String> deckChangelog = new HashMap<String, String>();
                deckChangelog.put("deckName", deckName);
                deckChangelog.put("message", context.getResources().getString(R.string.sync_log_error_message));

                decksChangelogs.add(deckChangelog);
            }
        }

        data.result = decksChangelogs;
        return data;
    }


    private Payload doInBackgroundSyncDeck(Payload data) {
        Resources res = context.getResources();
        HashMap<String, String> syncChangelog = new HashMap<String, String>();
        String username = (String) data.data[0];
        String password = (String) data.data[1];
        Deck deck = (Deck) data.data[2];
        String deckPath = (String) data.data[3];
        String syncName = deck.getSyncName();
        if (syncName == null || syncName.equalsIgnoreCase("")) {
            syncName = deckPath.substring(deckPath.lastIndexOf("/") + 1);
            syncName = syncName.substring(0, syncName.length() - ".anki".length());
            Log.i(TAG, "syncName = *" + syncName + "*");
            deck.setSyncName(syncName);
        }
        syncChangelog.put("deckName", syncName);

        AnkiDb ankiDB = AnkiDatabaseManager.getDatabase(deckPath);
        ankiDB.database.beginTransaction();
        try {
            Log.i(TAG, "Starting sync: username = " + username + ", password = " + password + ", deckPath = "
                    + deckPath + ", syncName = " + syncName);
            AnkiDroidProxy server = new AnkiDroidProxy(username, password);

            publishProgress(syncName, res.getString(R.string.sync_connecting_message));
            server.connect();

            if (!server.hasDeck(syncName)) {
                Log.i(TAG, "AnkiOnline does not have this deck: Creating it...");
                server.createDeck(syncName);
            }
            int timediff = (int) (server.getTimestamp() - (System.currentTimeMillis() / 1000));
            if (timediff > 300) {
                Log.i(TAG, "The clock is unsynchronized!");
                // TODO: Control what happens when the clocks are unsynchronized
            }
            publishProgress(syncName, res.getString(R.string.sync_syncing_message, new Object[] { syncName }));
            SyncClient client = new SyncClient(deck);
            client.setServer(server);
            server.setDeckName(syncName);
            if (client.prepareSync()) {
                publishProgress(syncName, res.getString(R.string.sync_summary_from_server_message));
                JSONArray sums = client.summaries();

                if (client.needFullSync(sums)) {
                    Log.i(TAG, "DECK NEEDS FULL SYNC");

                    publishProgress(syncName, res.getString(R.string.sync_preparing_full_sync_message));
                    String syncFrom = client.prepareFullSync();

                    if ("fromLocal".equalsIgnoreCase(syncFrom)) {
                        publishProgress(syncName, res.getString(R.string.sync_uploading_message));
                        SyncClient.fullSyncFromLocal(password, username, syncName, deckPath);
                        syncChangelog.put("message", res.getString(R.string.sync_log_uploading_message));
                    } else if ("fromServer".equalsIgnoreCase(syncFrom)) {
                        publishProgress(syncName, res.getString(R.string.sync_downloading_message));
                        SyncClient.fullSyncFromServer(password, username, syncName, deckPath);
                        syncChangelog.put("message", res.getString(R.string.sync_log_downloading_message));
                    }
                    ankiDB.database.setTransactionSuccessful();
                    ankiDB.database.endTransaction();
                    deck.closeDeck();

                    deck = Deck.openDeck(deckPath);
                    client.setDeck(deck);

                    publishProgress(syncName, res.getString(R.string.sync_complete_message));
                } else {
                    Log.i(TAG, "DECK DOES NOT NEED FULL SYNC");

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

                    deck.lastLoaded = deck.modified;
                    deck.commitToDB();

                    ankiDB.database.setTransactionSuccessful();
                    publishProgress(syncName, res.getString(R.string.sync_complete_message));
                }
            } else {
                Log.i(TAG, "NO CHANGES.");
                publishProgress(syncName, res.getString(R.string.sync_no_changes_message));
                syncChangelog.put("message", res.getString(R.string.sync_log_no_changes_message));
            }
        } catch (Exception e) {
            Log.e(TAG, "Error synchronizing deck = " + e.getMessage());
            e.printStackTrace();
            syncChangelog.put("message", res.getString(R.string.sync_log_error_message));
            data.success = false;
            data.exception = e;
        } finally {
            if (ankiDB.database != null && ankiDB.database.inTransaction()) {
                ankiDB.database.endTransaction();
            }

            if (deck != null) {
                deck.closeDeck();
            }
        }

        data.result = syncChangelog;
        return data;
    }


    private Payload doInBackgroundSyncDeckFromPayload(Payload data) {
        Log.i(TAG, "SyncDeckFromPayload");
        Deck deck = (Deck) data.data[0];
        SyncClient client = new SyncClient(deck);
        BufferedReader bufPython;
        try {
            bufPython = new BufferedReader(new FileReader("/sdcard/jsonObjectPython.txt"));
            JSONObject payloadReply = new JSONObject(bufPython.readLine());
            client.applyPayloadReply(payloadReply);
            deck.lastLoaded = deck.modified;
            deck.commitToDB();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        Log.i(TAG, "Synchronization from payload finished!");
        return data;
    }


    public static boolean isOnline() {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

        if (cm.getActiveNetworkInfo() != null) {
            return cm.getActiveNetworkInfo().isConnectedOrConnecting();
        } else {
            return false;
        }
    }


    public static void setContext(Context applicationContext) {
        context = applicationContext;
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
