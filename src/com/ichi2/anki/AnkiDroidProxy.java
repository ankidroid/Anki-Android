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
import java.util.Locale;
import java.util.zip.InflaterInputStream;

public class AnkiDroidProxy {

    private static final String TAG = "AnkiDroid";

    // Used to format doubles with English's decimal separator system
    private static final Locale ENGLISH_LOCALE = new Locale("en_US");

    private static final int CHUNK_SIZE = 32768;

    /**
     * Connection settings
     */

    private static final String SYNC_URL = "http://anki.ichi2.net/sync/";
    // 78.46.104.28
    private static final String SYNC_HOST = "anki.ichi2.net";
    private static final String SYNC_PORT = "80";

    // Test
    /*
     * private static final String SYNC_URL = "http://192.168.2.103:8001/sync/"; private static final String SYNC_HOST =
     * "192.168.2.103"; private static final String SYNC_PORT = "8001";
     */
    private String username;
    private String password;
    private String deckName;

    private JSONObject decks;
    private double timestamp;

    /**
     * Shared deck's fields
     */
    private static final int R_ID = 0;
    private static final int R_USERNAME = 1;
    private static final int R_TITLE = 2;
    private static final int R_DESCRIPTION = 3;
    private static final int R_TAGS = 4;
    private static final int R_VERSION = 5;
    private static final int R_FACTS = 6;
    private static final int R_SIZE = 7;
    private static final int R_COUNT = 8;
    private static final int R_MODIFIED = 9;
    private static final int R_FNAME = 10;

    /**
     * List to hold the shared decks
     */
    private static List<SharedDeck> sharedDecks;

    /**
     * Synchronization
     */

    public static final int LOGIN_OK = 0;
    public static final int LOGIN_INVALID_USER_PASS = 1;


    public AnkiDroidProxy(String user, String password) {
        username = user;
        this.password = password;
        deckName = "";
        decks = null;
    }


    public void setDeckName(String deckName) {
        this.deckName = deckName;
    }


    public double getTimestamp() {
        return timestamp;
    }


    public int connect() {
        if (decks == null) {
            String decksString = getDecks();
            try {
                JSONObject jsonDecks = new JSONObject(decksString);
                if ("OK".equalsIgnoreCase(jsonDecks.getString("status"))) {
                    decks = jsonDecks.getJSONObject("decks");
                    Log.i(TAG, "Server decks = " + decks.toString());
                    timestamp = jsonDecks.getDouble("timestamp");
                    Log.i(TAG, "Server timestamp = " + timestamp);
                    return LOGIN_OK;
                } else if ("invalidUserPass".equalsIgnoreCase(jsonDecks.getString("status"))) {
                    return LOGIN_INVALID_USER_PASS;
                }
            } catch (JSONException e) {
                Log.i(TAG, "JSONException = " + e.getMessage());
            }
        }

        return LOGIN_OK;
    }


    public boolean hasDeck(String name) {
        connect();
        Iterator decksIterator = decks.keys();
        while (decksIterator.hasNext()) {
            String serverDeckName = (String) decksIterator.next();
            if (name.equalsIgnoreCase(serverDeckName)) {
                return true;
            }
        }

        return false;
    }


    public double modified() {
        double lastModified = 0;

        connect();
        try {
            JSONArray deckInfo = decks.getJSONArray(deckName);
            lastModified = deckInfo.getDouble(0);
        } catch (JSONException e) {
            Log.i(TAG, "JSONException = " + e.getMessage());
        }

        return lastModified;
    }


    public double lastSync() {
        double lastSync = 0;

        connect();
        try {
            JSONArray deckInfo = decks.getJSONArray(deckName);
            lastSync = deckInfo.getDouble(1);
        } catch (JSONException e) {
            Log.i(TAG, "JSONException = " + e.getMessage());
        }
        return lastSync;
    }


    public String getDecks() {
        // Log.i(TAG, "getDecks - user = " + username + ", password = " + password);
        String decksServer = "{}";

        try {
            String data = "p=" + URLEncoder.encode(password, "UTF-8") + "&client=ankidroid-0.4&u="
                    + URLEncoder.encode(username, "UTF-8") + "&d=None&sources=" + URLEncoder.encode("[]", "UTF-8")
                    + "&libanki=0.9.9.8.6&pversion=5";

            // Log.i(TAG, "Data json = " + data);
            HttpPost httpPost = new HttpPost(SYNC_URL + "getDecks");
            StringEntity entity = new StringEntity(data);
            httpPost.setEntity(entity);
            httpPost.setHeader("Accept-Encoding", "identity");
            httpPost.setHeader("Content-type", "application/x-www-form-urlencoded");
            DefaultHttpClient httpClient = new DefaultHttpClient();
            HttpResponse response = httpClient.execute(httpPost);
            Log.i(TAG, "Response = " + response.toString());
            HttpEntity entityResponse = response.getEntity();
            Log.i(TAG, "Entity's response = " + entityResponse.toString());
            InputStream content = entityResponse.getContent();
            Log.i(TAG, "Content = " + content.toString());
            decksServer = Utils.convertStreamToString(new InflaterInputStream(content));
            Log.i(TAG, "String content = " + decksServer);

        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        } catch (ClientProtocolException e) {
            Log.i(TAG, "ClientProtocolException = " + e.getMessage());
        } catch (IOException e) {
            Log.i(TAG, "IOException = " + e.getMessage());
        }

        return decksServer;
    }


    public List<String> getPersonalDecks() {
        ArrayList<String> personalDecks = new ArrayList<String>();

        connect();

        Iterator decksIterator = decks.keys();
        while (decksIterator.hasNext()) {
            personalDecks.add((String) decksIterator.next());
        }

        return personalDecks;
    }


    public void createDeck(String name) {
        Log.i(TAG, "createDeck");
        // Log.i(TAG, "user = " + username + ", password = " + password);

        try {
            String data = "p=" + URLEncoder.encode(password, "UTF-8") + "&u=" + URLEncoder.encode(username, "UTF-8")
                    + "&d=None&name=" + URLEncoder.encode(name, "UTF-8");

            // Log.i(TAG, "Data json = " + data);
            HttpPost httpPost = new HttpPost(SYNC_URL + "createDeck");
            StringEntity entity = new StringEntity(data);
            httpPost.setEntity(entity);
            httpPost.setHeader("Accept-Encoding", "identity");
            httpPost.setHeader("Content-type", "application/x-www-form-urlencoded");
            DefaultHttpClient httpClient = new DefaultHttpClient();
            HttpResponse response = httpClient.execute(httpPost);
            Log.i(TAG, "Response = " + response.toString());
            HttpEntity entityResponse = response.getEntity();
            Log.i(TAG, "Entity's response = " + entityResponse.toString());
            InputStream content = entityResponse.getContent();
            Log.i(TAG, "Content = " + content.toString());
            Log.i(TAG, "String content = " + Utils.convertStreamToString(new InflaterInputStream(content)));

            // Add created deck to the list of decks on server
            decks.put(name, new JSONArray("[0,0]"));
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        } catch (ClientProtocolException e) {
            Log.i(TAG, "ClientProtocolException = " + e.getMessage());
            e.printStackTrace();
        } catch (IOException e) {
            Log.i(TAG, "IOException = " + e.getMessage());
            e.printStackTrace();
        } catch (JSONException e) {
            Log.i(TAG, "JSONException = " + e.getMessage());
            e.printStackTrace();
        }
    }


    /**
     * Anki Desktop -> libanki/anki/sync.py, HttpSyncServerProxy - summary
     * 
     * @param lastSync
     */
    public JSONObject summary(double lastSync) {

        Log.i(TAG, "Summary Server");

        // Log.i(TAG, "user = " + username + ", password = " + password + ", lastSync = " + lastSync);
        JSONObject summaryServer = new JSONObject();

        try {
            // FIXME: Try to do the connection without encoding the lastSync in Base 64
            String data = "p="
                    + URLEncoder.encode(password, "UTF-8")
                    + "&u="
                    + URLEncoder.encode(username, "UTF-8")
                    + "&d="
                    + URLEncoder.encode(deckName, "UTF-8")
                    + "&lastSync="
                    + URLEncoder.encode(Base64.encodeBytes(Utils.compress(String.format(ENGLISH_LOCALE, "%f", lastSync)
                            .getBytes())), "UTF-8") + "&base64=" + URLEncoder.encode("true", "UTF-8");

            // Log.i(TAG, "Data json = " + data);
            HttpPost httpPost = new HttpPost(SYNC_URL + "summary");
            StringEntity entity = new StringEntity(data);
            httpPost.setEntity(entity);
            httpPost.setHeader("Accept-Encoding", "identity");
            httpPost.setHeader("Content-type", "application/x-www-form-urlencoded");
            DefaultHttpClient httpClient = new DefaultHttpClient();
            HttpResponse response = httpClient.execute(httpPost);
            Log.i(TAG, "Response = " + response.toString());
            HttpEntity entityResponse = response.getEntity();
            Log.i(TAG, "Entity's response = " + entityResponse.toString());
            InputStream content = entityResponse.getContent();
            Log.i(TAG, "Content = " + content.toString());
            summaryServer = new JSONObject(Utils.convertStreamToString(new InflaterInputStream(content)));
            Log.i(TAG, "Summary server = ");
            Utils.printJSONObject(summaryServer);
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        } catch (ClientProtocolException e) {
            Log.i(TAG, "ClientProtocolException = " + e.getMessage());
        } catch (IOException e) {
            Log.i(TAG, "IOException = " + e.getMessage());
        } catch (JSONException e) {
            Log.i(TAG, "JSONException = " + e.getMessage());
        }

        return summaryServer;
    }


    /**
     * Anki Desktop -> libanki/anki/sync.py, HttpSyncServerProxy - applyPayload
     * 
     * @param lastSync
     */
    public JSONObject applyPayload(JSONObject payload) {
        Log.i(TAG, "applyPayload");
        // Log.i(TAG, "user = " + username + ", password = " + password + ", payload = " + payload.toString());
        JSONObject payloadReply = new JSONObject();

        try {
            // FIXME: Try to do the connection without encoding the payload in Base 64
            String data = "p=" + URLEncoder.encode(password, "UTF-8") + "&u=" + URLEncoder.encode(username, "UTF-8")
                    + "&d=" + URLEncoder.encode(deckName, "UTF-8") + "&payload="
                    + URLEncoder.encode(Base64.encodeBytes(Utils.compress(payload.toString().getBytes())), "UTF-8")
                    + "&base64=" + URLEncoder.encode("true", "UTF-8");

            // Log.i(TAG, "Data json = " + data);
            HttpPost httpPost = new HttpPost(SYNC_URL + "applyPayload");
            StringEntity entity = new StringEntity(data);
            httpPost.setEntity(entity);
            httpPost.setHeader("Accept-Encoding", "identity");
            httpPost.setHeader("Content-type", "application/x-www-form-urlencoded");
            DefaultHttpClient httpClient = new DefaultHttpClient();
            HttpResponse response = httpClient.execute(httpPost);
            Log.i(TAG, "Response = " + response.toString());
            HttpEntity entityResponse = response.getEntity();
            Log.i(TAG, "Entity's response = " + entityResponse.toString());
            InputStream content = entityResponse.getContent();
            Log.i(TAG, "Content = " + content.toString());
            String contentString = Utils.convertStreamToString(new InflaterInputStream(content));
            Log.i(TAG, "Payload response = ");
            payloadReply = new JSONObject(contentString);
            Utils.printJSONObject(payloadReply, false);
            Utils.saveJSONObject(payloadReply);
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        } catch (ClientProtocolException e) {
            Log.i(TAG, "ClientProtocolException = " + e.getMessage());
        } catch (IOException e) {
            Log.i(TAG, "IOException = " + e.getMessage());
        } catch (JSONException e) {
            Log.i(TAG, "JSONException = " + e.getMessage());
        }

        return payloadReply;
    }


    /**
     * Get shared decks
     */

    public static List<SharedDeck> getSharedDecks() throws Exception {

        try {
            if (sharedDecks == null) {
                sharedDecks = new ArrayList<SharedDeck>();

                HttpGet httpGet = new HttpGet("http://anki.ichi2.net/file/search");
                httpGet.setHeader("Accept-Encoding", "identity");
                httpGet.setHeader("Host", "anki.ichi2.net");
                DefaultHttpClient defaultHttpClient = new DefaultHttpClient();

                HttpResponse httpResponse = defaultHttpClient.execute(httpGet);
                String response = Utils.convertStreamToString(httpResponse.getEntity().getContent());
                // Log.i(TAG, "Content = " + response);
                sharedDecks.addAll(getSharedDecksListFromJSONArray(new JSONArray(response)));
            }
        } catch (Exception e) {
            sharedDecks = null;
            throw new Exception();
        }

        return sharedDecks;
    }


    private static List<SharedDeck> getSharedDecksListFromJSONArray(JSONArray jsonSharedDecks) throws JSONException {
        List<SharedDeck> sharedDecks = new ArrayList<SharedDeck>();

        if (jsonSharedDecks != null) {
            // Log.i(TAG, "Number of shared decks = " + jsonSharedDecks.length());

            int nbDecks = jsonSharedDecks.length();
            for (int i = 0; i < nbDecks; i++) {
                JSONArray jsonSharedDeck = jsonSharedDecks.getJSONArray(i);

                SharedDeck sharedDeck = new SharedDeck();
                sharedDeck.setId(jsonSharedDeck.getInt(R_ID));
                sharedDeck.setUsername(jsonSharedDeck.getString(R_USERNAME));
                sharedDeck.setTitle(jsonSharedDeck.getString(R_TITLE));
                sharedDeck.setDescription(jsonSharedDeck.getString(R_DESCRIPTION));
                sharedDeck.setTags(jsonSharedDeck.getString(R_TAGS));
                sharedDeck.setVersion(jsonSharedDeck.getInt(R_VERSION));
                sharedDeck.setFacts(jsonSharedDeck.getInt(R_FACTS));
                sharedDeck.setSize(jsonSharedDeck.getInt(R_SIZE));
                sharedDeck.setCount(jsonSharedDeck.getInt(R_COUNT));
                sharedDeck.setModified(jsonSharedDeck.getDouble(R_MODIFIED));
                sharedDeck.setFileName(jsonSharedDeck.getString(R_FNAME));

                // sharedDeck.prettyLog();

                sharedDecks.add(sharedDeck);
            }
        }

        return sharedDecks;
    }

}
