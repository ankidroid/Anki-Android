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

package com.ichi2.anki;

import android.util.Log;

import com.ichi2.async.Connection.Payload;
import com.ichi2.libanki.Utils;
import com.ichi2.utils.Base64;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.zip.InflaterInputStream;

public class AnkiDroidProxy {

    // Sync protocol version
    public static final String SYNC_VERSION = "2";

    // The possible values for the status response from the AnkiWeb server.
    private static final String ANKIWEB_STATUS_OK = "OK";
    private static final String ANKIWEB_STATUS_INVALID_USER_PASS = "invalidUserPass";
    private static final String ANKIWEB_STATUS_OLD_VERSION = "oldVersion";
    private static final String ANKIWEB_STATUS_TOO_BUSY =
        "AnkiWeb is too busy right now. Please try again later.";
    /**
     * Connection settings
     */
    // ankiweb.net hosted at 78.46.104.19
    public static final String SYNC_HOST = "dev.ankiweb.net";
    public static final String SYNC_URL = "http://" + SYNC_HOST + "/sync/";
    public static final String SYNC_SEARCH = "http://" + SYNC_HOST + "/file/search";

    /**
     * Synchronization.
     */
    public static final int LOGIN_ERROR = -1;
    public static final int LOGIN_OK = 0;
    public static final int LOGIN_INVALID_USER_PASS = 1;
    public static final int LOGIN_CLOCKS_UNSYNCED = 2;
    public static final int SYNC_CONFLICT_RESOLUTION = 3;
    public static final int LOGIN_OLD_VERSION = 4;
    /** The server is too busy to serve the request. */
    public static final int LOGIN_TOO_BUSY = 5;
    public static final int DB_ERROR = 6;

    /**
     * Shared deck's fields
     */
    private static final int SD_ID = 0;
    private static final int SD_USERNAME = 1;
    private static final int SD_TITLE = 2;
    private static final int SD_DESCRIPTION = 3;
    private static final int SD_TAGS = 4;
    private static final int SD_VERSION = 5;
    private static final int SD_FACTS = 6;
    private static final int SD_SIZE = 7;
    private static final int SD_COUNT = 8;
    private static final int SD_MODIFIED = 9;
    private static final int SD_FNAME = 10;

    /**
     * List to hold the shared decks
     */
    private static List<SharedDeck> sSharedDecks;

    private String mUsername;
    private String mPassword;
    private String mDeckName;

    private JSONObject mDecks;
    private double mTimestamp;
    private double mTimediff;


    public AnkiDroidProxy(String user, String password) {
        mUsername = user;
        mPassword = password;
        mDeckName = "";
        mDecks = null;
        mTimediff = 0.0;
    }


    public void setDeckName(String deckName) {
        mDeckName = deckName;
    }


    public double getTimestamp() {
        return mTimestamp;
    }


    public double getTimediff() {
        return mTimediff;
    }


    public int connect(boolean checkClocks) {
        if (mDecks == null) {
            String decksString = getDecks();
            try {
                JSONObject jsonDecks = new JSONObject(decksString);
                String status = jsonDecks.getString("status");
                if (ANKIWEB_STATUS_OK.equalsIgnoreCase(status)) {
                    mDecks = jsonDecks.getJSONObject("decks");
                    Log.i(AnkiDroidApp.TAG, "Server decks = " + mDecks.toString());
                    mTimestamp = jsonDecks.getDouble("timestamp");
                    mTimediff = Math.abs(mTimestamp - Utils.now());
                    Log.i(AnkiDroidApp.TAG, "Server timestamp = " + mTimestamp);
                    if (checkClocks && (mTimediff > 300)) {
                        Log.e(AnkiDroidApp.TAG, "connect - The clock of the device and that of the server are unsynchronized!");
                        return LOGIN_CLOCKS_UNSYNCED;
                    }
                    return LOGIN_OK;
                } else if (ANKIWEB_STATUS_INVALID_USER_PASS.equalsIgnoreCase(status)) {
                    return LOGIN_INVALID_USER_PASS;
                } else if (ANKIWEB_STATUS_OLD_VERSION.equalsIgnoreCase(status)) {
                    return LOGIN_OLD_VERSION;
                } else if (ANKIWEB_STATUS_TOO_BUSY.equalsIgnoreCase(status)) {
                    return LOGIN_TOO_BUSY;
                } else {
                    Log.e(AnkiDroidApp.TAG, "connect - unexpected status: " + status);
                    return LOGIN_ERROR;
                }
            } catch (JSONException e) {
                Log.e(AnkiDroidApp.TAG, "connect - JSONException = " + e.getMessage());
                return LOGIN_ERROR;
            }
        }

        return LOGIN_OK;
    }


    /**
     * Returns true if the server has the given deck.
     * <p>
     * It assumes connect() has already been called and will fail if it was not or the connection
     * was unsuccessful.
     *
     * @param name the name of the deck to look for
     * @return true if the server has the given deck, false otherwise
     */
    public boolean hasDeck(String name) {
        // We assume that gets have already been loading by doing a connect.
        if (mDecks == null) throw new IllegalStateException("Should have called connect first");
        @SuppressWarnings("unchecked") Iterator<String> decksIterator = (Iterator<String>) mDecks.keys();
        while (decksIterator.hasNext()) {
            String serverDeckName = decksIterator.next();
            if (name.equalsIgnoreCase(serverDeckName)) {
                return true;
            }
        }

        return false;
    }


    public double modified() {
        double lastModified = 0;

        // TODO: Why do we need to run connect?
        if (connect(false) != LOGIN_OK) {
            return -1.0;
        }
        try {
            JSONArray deckInfo = mDecks.getJSONArray(mDeckName);
            lastModified = deckInfo.getDouble(0);
        } catch (JSONException e) {
            Log.e(AnkiDroidApp.TAG, "modified - JSONException = " + e.getMessage());
            return -1.0;
        }

        return lastModified;
    }


    public double lastSync() {
        double lastSync = 0;

        // TODO: Why do we need to run connect?
        if (connect(false) != LOGIN_OK) {
            return -1.0;
        }
        try {
            JSONArray deckInfo = mDecks.getJSONArray(mDeckName);
            lastSync = deckInfo.getDouble(1);
        } catch (JSONException e) {
            Log.e(AnkiDroidApp.TAG, "lastSync - JSONException = " + e.getMessage());
            return -1.0;
        }
        return lastSync;
    }


    public boolean finish() {
        try {
            String data = "p=" + URLEncoder.encode(mPassword, "UTF-8") + "&u=" + URLEncoder.encode(mUsername, "UTF-8")
                    + "&v=" + URLEncoder.encode(SYNC_VERSION, "UTF-8") + "&d=" + URLEncoder.encode(mDeckName, "UTF-8");
            HttpPost httpPost = new HttpPost(SYNC_URL + "finish");
            StringEntity entity = new StringEntity(data);
            httpPost.setEntity(entity);
            httpPost.setHeader("Accept-Encoding", "identity");
            httpPost.setHeader("Content-type", "application/x-www-form-urlencoded");
            DefaultHttpClient httpClient = new DefaultHttpClient();
            HttpResponse response = httpClient.execute(httpPost);
            HttpEntity entityResponse = response.getEntity();
            int respCode = response.getStatusLine().getStatusCode();
            if (respCode != 200) {
                Log.e(AnkiDroidApp.TAG, "AnkiDroidProxy.finish error: " + respCode + " " +
                        response.getStatusLine().getReasonPhrase());
                return false;
            }
            InputStream content = entityResponse.getContent();
            String contentString = Utils.convertStreamToString(new InflaterInputStream(content));
            Log.i(AnkiDroidApp.TAG, "finish: " + contentString);
            return true;
        } catch (UnsupportedEncodingException e) {
            Log.e(AnkiDroidApp.TAG, "UnsupportedEncodingException = " + e.getMessage());
            Log.e(AnkiDroidApp.TAG, Log.getStackTraceString(e));
            return false;
        } catch (ClientProtocolException e) {
            Log.e(AnkiDroidApp.TAG, "ClientProtocolException = " + e.getMessage());
            Log.e(AnkiDroidApp.TAG, Log.getStackTraceString(e));
            return false;
        } catch (IOException e) {
            Log.e(AnkiDroidApp.TAG, "IOException = " + e.getMessage());
            Log.e(AnkiDroidApp.TAG, Log.getStackTraceString(e));
            return false;
        }
    }


    public String getDecks() {
        String decksServer = "{}";

        try {
            String data = "p=" + URLEncoder.encode(mPassword, "UTF-8") + "&client="
                    + URLEncoder.encode("ankidroid-" + AnkiDroidApp.getPkgVersion(), "UTF-8") + "&u="
                    + URLEncoder.encode(mUsername, "UTF-8") + "&v=" + URLEncoder.encode(SYNC_VERSION, "UTF-8")
                    + "&d=None&sources=" + URLEncoder.encode("[]", "UTF-8") + "&libanki="
                    + URLEncoder.encode(AnkiDroidApp.LIBANKI_VERSION, "UTF-8") + "&pversion=5";

            // Log.i(AnkiDroidApp.TAG, "Data json = " + data);
            HttpPost httpPost = new HttpPost(SYNC_URL + "getDecks");
            StringEntity entity = new StringEntity(data);
            httpPost.setEntity(entity);
            httpPost.setHeader("Accept-Encoding", "identity");
            httpPost.setHeader("Content-type", "application/x-www-form-urlencoded");
            DefaultHttpClient httpClient = new DefaultHttpClient();
            HttpResponse response = httpClient.execute(httpPost);
            int respCode = response.getStatusLine().getStatusCode();
            if (respCode != 200) {
                Log.e(AnkiDroidApp.TAG, "getDecks error: " + respCode + " " +
                        response.getStatusLine().getReasonPhrase());
                return decksServer;
            }
            HttpEntity entityResponse = response.getEntity();
            InputStream content = entityResponse.getContent();
            decksServer = Utils.convertStreamToString(new InflaterInputStream(content));
            Log.i(AnkiDroidApp.TAG, "getDecks response = " + decksServer);

        } catch (UnsupportedEncodingException e) {
            Log.e(AnkiDroidApp.TAG, "getDecks - UnsupportedEncodingException = " + e.getMessage());
            Log.e(AnkiDroidApp.TAG, "getDecks - " + Log.getStackTraceString(e));
        } catch (ClientProtocolException e) {
            Log.e(AnkiDroidApp.TAG, "getDecks - ClientProtocolException = " + e.getMessage());
            Log.e(AnkiDroidApp.TAG, "getDecks - " + Log.getStackTraceString(e));
        } catch (IOException e) {
            Log.e(AnkiDroidApp.TAG, "getDecks - IOException = " + e.getMessage());
            Log.e(AnkiDroidApp.TAG, "getDecks - " + Log.getStackTraceString(e));
        }

        return decksServer;
    }


    public List<String> getPersonalDecks() {
        ArrayList<String> personalDecks = new ArrayList<String>();
        @SuppressWarnings("unchecked") Iterator<String> decksIterator = (Iterator<String>) mDecks.keys();
        while (decksIterator.hasNext()) {
            personalDecks.add((String) decksIterator.next());
        }

        return personalDecks;
    }


    public Payload createDeck(String name) {
        Log.i(AnkiDroidApp.TAG, "createDeck");
        
        Payload result = new Payload();

        try {
            String data = "p=" + URLEncoder.encode(mPassword, "UTF-8") + "&u=" + URLEncoder.encode(mUsername, "UTF-8")
                    + "&v=" + URLEncoder.encode(SYNC_VERSION, "UTF-8") + "&d=None&name="
                    + URLEncoder.encode(name, "UTF-8");

            HttpPost httpPost = new HttpPost(SYNC_URL + "createDeck");
            StringEntity entity = new StringEntity(data);
            httpPost.setEntity(entity);
            httpPost.setHeader("Accept-Encoding", "identity");
            httpPost.setHeader("Content-type", "application/x-www-form-urlencoded");
            DefaultHttpClient httpClient = new DefaultHttpClient();
            HttpResponse response = httpClient.execute(httpPost);
            int respCode = response.getStatusLine().getStatusCode();
            HttpEntity entityResponse = response.getEntity();
            InputStream content = entityResponse.getContent();
            if (respCode != 200) {
                String reason = response.getStatusLine().getReasonPhrase();
                Log.i(AnkiDroidApp.TAG, "Failed to create Deck: " + respCode + " " + reason);
                result.success = false;
                result.returnType = respCode;
                result.result = reason;
                return result;
            } else {
                Log.i(AnkiDroidApp.TAG, "createDeck - response = " + Utils.convertStreamToString(new InflaterInputStream(content)));
                result.success = true;
                result.returnType = 200;
                // Add created deck to the list of decks on server
                mDecks.put(name, new JSONArray("[0,0]"));
                return result;
            }
        } catch (UnsupportedEncodingException e) {
            Log.e(AnkiDroidApp.TAG, "createDeck - UnsupportedEncodingException = " + e.getMessage());
            Log.e(AnkiDroidApp.TAG, Log.getStackTraceString(e));
            result.result = e.getMessage();
        } catch (ClientProtocolException e) {
            Log.e(AnkiDroidApp.TAG, "createDeck - ClientProtocolException = " + e.getMessage());
            Log.e(AnkiDroidApp.TAG, Log.getStackTraceString(e));
            result.result = e.getMessage();
        } catch (IOException e) {
            Log.e(AnkiDroidApp.TAG, "createDeck - IOException = " + e.getMessage());
            Log.e(AnkiDroidApp.TAG, Log.getStackTraceString(e));
            result.result = e.getMessage();
        } catch (JSONException e) {
            Log.e(AnkiDroidApp.TAG, "createDeck - JSONException = " + e.getMessage());
            Log.e(AnkiDroidApp.TAG, Log.getStackTraceString(e));
            result.result = e.getMessage();
        }
        result.success = false;
        result.returnType = -1;
        return result;
    }


    /**
     * Anki Desktop -> libanki/anki/sync.py, HttpSyncServerProxy - summary
     * 
     * @param lastSync
     */
    public JSONObject summary(double lastSync) {

        Log.i(AnkiDroidApp.TAG, "Summary Server");

        JSONObject summaryServer = new JSONObject();

        try {
            String data = "p=" + URLEncoder.encode(mPassword, "UTF-8")
                    + "&u=" + URLEncoder.encode(mUsername, "UTF-8")
                    + "&d=" + URLEncoder.encode(mDeckName, "UTF-8")
                    + "&v=" + URLEncoder.encode(SYNC_VERSION, "UTF-8")
                    + "&lastSync="
                    + URLEncoder.encode(Base64.encodeBytes(Utils.compress(String.format(Utils.ENGLISH_LOCALE, "%f",
                            lastSync).getBytes())), "UTF-8") + "&base64=" + URLEncoder.encode("true", "UTF-8");

            // Log.i(AnkiDroidApp.TAG, "Data json = " + data);
            HttpPost httpPost = new HttpPost(SYNC_URL + "summary");
            StringEntity entity = new StringEntity(data);
            httpPost.setEntity(entity);
            httpPost.setHeader("Accept-Encoding", "identity");
            httpPost.setHeader("Content-type", "application/x-www-form-urlencoded");
            DefaultHttpClient httpClient = new DefaultHttpClient();
            HttpResponse response = httpClient.execute(httpPost);
            int respCode = response.getStatusLine().getStatusCode();
            if (respCode != 200) {
                Log.e(AnkiDroidApp.TAG, "Error getting server summary: " + respCode + " " + response.getStatusLine().getReasonPhrase());
                return null;
            }
            HttpEntity entityResponse = response.getEntity();
            InputStream content = entityResponse.getContent();
            summaryServer = new JSONObject(Utils.convertStreamToString(new InflaterInputStream(content)));
            Log.i(AnkiDroidApp.TAG, "Summary server = ");
            Utils.printJSONObject(summaryServer);
            return summaryServer;
        } catch (UnsupportedEncodingException e) {
            Log.e(AnkiDroidApp.TAG, Log.getStackTraceString(e));
        } catch (ClientProtocolException e) {
            Log.e(AnkiDroidApp.TAG, "ClientProtocolException = " + e.getMessage());
            Log.e(AnkiDroidApp.TAG, Log.getStackTraceString(e));
        } catch (IOException e) {
            Log.e(AnkiDroidApp.TAG, "IOException = " + e.getMessage());
            Log.e(AnkiDroidApp.TAG, Log.getStackTraceString(e));
        } catch (JSONException e) {
            Log.e(AnkiDroidApp.TAG, "JSONException = " + e.getMessage());
            Log.e(AnkiDroidApp.TAG, Log.getStackTraceString(e));
        } catch (OutOfMemoryError e) {
            Log.e(AnkiDroidApp.TAG, "OutOfMemoryError = " + e.getMessage());
            Log.e(AnkiDroidApp.TAG, Log.getStackTraceString(e));
        }
        return null;
    }


    /**
     * Anki Desktop -> libanki/anki/sync.py, HttpSyncServerProxy - applyPayload
     * 
     * @param payload
     * @throws JSONException 
     */
    public JSONObject applyPayload(JSONObject payload) throws JSONException {
        Log.i(AnkiDroidApp.TAG, "applyPayload");
        JSONObject payloadReply = new JSONObject();

        try {
            // FIXME: Try to do the connection without encoding the payload in Base 64
            String data = "p=" + URLEncoder.encode(mPassword, "UTF-8") + "&u=" + URLEncoder.encode(mUsername, "UTF-8")
                    + "&v=" + URLEncoder.encode(SYNC_VERSION, "UTF-8") + "&d=" + URLEncoder.encode(mDeckName, "UTF-8")
                    + "&payload="
                    + URLEncoder.encode(Base64.encodeBytes(Utils.compress(payload.toString().getBytes())), "UTF-8")
                    + "&base64=" + URLEncoder.encode("true", "UTF-8");

            // Log.i(AnkiDroidApp.TAG, "Data json = " + data);
            HttpPost httpPost = new HttpPost(SYNC_URL + "applyPayload");
            StringEntity entity = new StringEntity(data);
            httpPost.setEntity(entity);
            httpPost.setHeader("Accept-Encoding", "identity");
            httpPost.setHeader("Content-type", "application/x-www-form-urlencoded");
            DefaultHttpClient httpClient = new DefaultHttpClient();
            HttpResponse response = httpClient.execute(httpPost);
            int respCode = response.getStatusLine().getStatusCode();
            if (respCode != 200) {
                Log.e(AnkiDroidApp.TAG, "applyPayload error: " + respCode + " " +
                        response.getStatusLine().getReasonPhrase());
                return null;
            }
            HttpEntity entityResponse = response.getEntity();
            InputStream content = entityResponse.getContent();
            String contentString = Utils.convertStreamToString(new InflaterInputStream(content));
            Log.i(AnkiDroidApp.TAG, "Payload response = ");
            payloadReply = new JSONObject(contentString);
            Utils.printJSONObject(payloadReply, false);
            //Utils.saveJSONObject(payloadReply); //XXX: do we really want to append all JSON objects forever? I don't think so.
        } catch (UnsupportedEncodingException e) {
            Log.e(AnkiDroidApp.TAG, "UnsupportedEncodingException = " + e.getMessage());
            Log.e(AnkiDroidApp.TAG, Log.getStackTraceString(e));
            return null;
        } catch (ClientProtocolException e) {
            Log.e(AnkiDroidApp.TAG, "ClientProtocolException = " + e.getMessage());
            Log.e(AnkiDroidApp.TAG, Log.getStackTraceString(e));
            return null;
        } catch (IOException e) {
            Log.e(AnkiDroidApp.TAG, "IOException = " + e.getMessage());
            Log.e(AnkiDroidApp.TAG, Log.getStackTraceString(e));
            return null;
        }

        return payloadReply;
    }


    /**
     * Get shared decks.
     */
    public static List<SharedDeck> getSharedDecks() throws Exception {

        try {
            if (sSharedDecks == null) {
                sSharedDecks = new ArrayList<SharedDeck>();

                HttpGet httpGet = new HttpGet(SYNC_SEARCH);
                httpGet.setHeader("Accept-Encoding", "identity");
                httpGet.setHeader("Host", SYNC_HOST);
                DefaultHttpClient defaultHttpClient = new DefaultHttpClient();

                HttpResponse httpResponse = defaultHttpClient.execute(httpGet);
                String response = Utils.convertStreamToString(httpResponse.getEntity().getContent());
                // Log.i(AnkiDroidApp.TAG, "Content = " + response);
                sSharedDecks.addAll(getSharedDecksListFromJSONArray(new JSONArray(response)));
            }
        } catch (Exception e) {
            sSharedDecks = null;
            throw new Exception();
        }

        return sSharedDecks;
    }


    public static void resetSharedDecks() {
    	sSharedDecks = null;
    }


    private static List<SharedDeck> getSharedDecksListFromJSONArray(JSONArray jsonSharedDecks) throws JSONException {
        List<SharedDeck> sharedDecks = new ArrayList<SharedDeck>();

        if (jsonSharedDecks != null) {
            // Log.i(AnkiDroidApp.TAG, "Number of shared decks = " + jsonSharedDecks.length());

            int nbDecks = jsonSharedDecks.length();
            for (int i = 0; i < nbDecks; i++) {
                JSONArray jsonSharedDeck = jsonSharedDecks.getJSONArray(i);

                SharedDeck sharedDeck = new SharedDeck();
                sharedDeck.setId(jsonSharedDeck.getInt(SD_ID));
                sharedDeck.setUsername(jsonSharedDeck.getString(SD_USERNAME));
                sharedDeck.setTitle(jsonSharedDeck.getString(SD_TITLE));
                sharedDeck.setDescription(jsonSharedDeck.getString(SD_DESCRIPTION));
                sharedDeck.setTags(jsonSharedDeck.getString(SD_TAGS));
                sharedDeck.setVersion(jsonSharedDeck.getInt(SD_VERSION));
                sharedDeck.setFacts(jsonSharedDeck.getInt(SD_FACTS));
                sharedDeck.setSize(jsonSharedDeck.getInt(SD_SIZE));
                sharedDeck.setCount(jsonSharedDeck.getInt(SD_COUNT));
                sharedDeck.setModified(jsonSharedDeck.getDouble(SD_MODIFIED));
                sharedDeck.setFileName(jsonSharedDeck.getString(SD_FNAME));

                // sharedDeck.prettyLog();

                sharedDecks.add(sharedDeck);
            }
        }

        return sharedDecks;
    }

}
