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
import java.util.zip.InflaterInputStream;

public class AnkiDroidProxy {

	// Sync protocol version
	private static final String SYNC_VERSION = "2";

    /**
     * Connection settings
     */
    // ankiweb.net hosted at 78.46.104.19
    public static final String SYNC_HOST = "ankiweb.net";
    public static final String SYNC_URL = "http://" + SYNC_HOST + "/sync/";
    public static final String SYNC_SEARCH = "http://" + SYNC_HOST + "/file/search";

    /**
     * Synchronization.
     */
    public static final int LOGIN_OK = 0;
    public static final int LOGIN_INVALID_USER_PASS = 1;

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


    public AnkiDroidProxy(String user, String password) {
        mUsername = user;
        mPassword = password;
        mDeckName = "";
        mDecks = null;
    }


    public void setDeckName(String deckName) {
        mDeckName = deckName;
    }


    public double getTimestamp() {
        return mTimestamp;
    }


    public int connect() {
        if (mDecks == null) {
            String decksString = getDecks();
            try {
                JSONObject jsonDecks = new JSONObject(decksString);
                if ("OK".equalsIgnoreCase(jsonDecks.getString("status"))) {
                    mDecks = jsonDecks.getJSONObject("decks");
                    Log.i(AnkiDroidApp.TAG, "Server decks = " + mDecks.toString());
                    mTimestamp = jsonDecks.getDouble("timestamp");
                    Log.i(AnkiDroidApp.TAG, "Server timestamp = " + mTimestamp);
                    return LOGIN_OK;
                } else if ("invalidUserPass".equalsIgnoreCase(jsonDecks.getString("status"))) {
                    return LOGIN_INVALID_USER_PASS;
                }
            } catch (JSONException e) {
                Log.i(AnkiDroidApp.TAG, "JSONException = " + e.getMessage());
            }
        }

        return LOGIN_OK;
    }


    public boolean hasDeck(String name) {
        connect();
        Iterator decksIterator = mDecks.keys();
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
            JSONArray deckInfo = mDecks.getJSONArray(mDeckName);
            lastModified = deckInfo.getDouble(0);
        } catch (JSONException e) {
            Log.i(AnkiDroidApp.TAG, "JSONException = " + e.getMessage());
        }

        return lastModified;
    }


    public double lastSync() {
        double lastSync = 0;

        connect();
        try {
            JSONArray deckInfo = mDecks.getJSONArray(mDeckName);
            lastSync = deckInfo.getDouble(1);
        } catch (JSONException e) {
            Log.i(AnkiDroidApp.TAG, "JSONException = " + e.getMessage());
        }
        return lastSync;
    }


    public String getDecks() {
        // Log.i(AnkiDroidApp.TAG, "getDecks - user = " + username + ", password = " + password);
        String decksServer = "{}";

		PackageManager pm = mCurContext.getPackageManager();
		PackageInfo pi = pm.getPackageInfo(mCurContext.getPackageName(), 0);

        try {
            String data = "p=" + URLEncoder.encode(mPassword, "UTF-8") + "&client="
				+ URLEncoder("ankidroid-" + pi.versionName, "UTF-8") + "&u=" + URLEncoder.encode(mUsername, "UTF-8")
			   	+ "&v=" + URLEncoder.encode(SYNC_VERSION, "UTF-8") + "&d=None&sources="
			   	+ URLEncoder.encode("[]", "UTF-8") + "&libanki=" + URLEncoder(AnkiDroidApp.LIBANKI_VERSION, "UTF-8")
			   	+ "&pversion=5";

            // Log.i(AnkiDroidApp.TAG, "Data json = " + data);
            HttpPost httpPost = new HttpPost(SYNC_URL + "getDecks");
            StringEntity entity = new StringEntity(data);
            httpPost.setEntity(entity);
            httpPost.setHeader("Accept-Encoding", "identity");
            httpPost.setHeader("Content-type", "application/x-www-form-urlencoded");
            DefaultHttpClient httpClient = new DefaultHttpClient();
            HttpResponse response = httpClient.execute(httpPost);
            Log.i(AnkiDroidApp.TAG, "Response = " + response.toString());
            HttpEntity entityResponse = response.getEntity();
            Log.i(AnkiDroidApp.TAG, "Entity's response = " + entityResponse.toString());
            InputStream content = entityResponse.getContent();
            Log.i(AnkiDroidApp.TAG, "Content = " + content.toString());
            decksServer = Utils.convertStreamToString(new InflaterInputStream(content));
            Log.i(AnkiDroidApp.TAG, "String content = " + decksServer);

        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        } catch (ClientProtocolException e) {
            Log.i(AnkiDroidApp.TAG, "ClientProtocolException = " + e.getMessage());
        } catch (IOException e) {
            Log.i(AnkiDroidApp.TAG, "IOException = " + e.getMessage());
        }

        return decksServer;
    }


    public List<String> getPersonalDecks() {
        ArrayList<String> personalDecks = new ArrayList<String>();

        connect();

        Iterator decksIterator = mDecks.keys();
        while (decksIterator.hasNext()) {
            personalDecks.add((String) decksIterator.next());
        }

        return personalDecks;
    }


    public void createDeck(String name) {
        Log.i(AnkiDroidApp.TAG, "createDeck");
        // Log.i(AnkiDroidApp.TAG, "user = " + username + ", password = " + password);

        try {
            String data = "p=" + URLEncoder.encode(mPassword, "UTF-8") + "&u=" + URLEncoder.encode(mUsername, "UTF-8")
				+ "&v=" + URLEncoder.encode(SYNC_VERSION, "UTF-8") + "&d=None&name=" + URLEncoder.encode(name, "UTF-8");

            // Log.i(AnkiDroidApp.TAG, "Data json = " + data);
            HttpPost httpPost = new HttpPost(SYNC_URL + "createDeck");
            StringEntity entity = new StringEntity(data);
            httpPost.setEntity(entity);
            httpPost.setHeader("Accept-Encoding", "identity");
            httpPost.setHeader("Content-type", "application/x-www-form-urlencoded");
            DefaultHttpClient httpClient = new DefaultHttpClient();
            HttpResponse response = httpClient.execute(httpPost);
            Log.i(AnkiDroidApp.TAG, "Response = " + response.toString());
            HttpEntity entityResponse = response.getEntity();
            Log.i(AnkiDroidApp.TAG, "Entity's response = " + entityResponse.toString());
            InputStream content = entityResponse.getContent();
            Log.i(AnkiDroidApp.TAG, "Content = " + content.toString());
            Log.i(AnkiDroidApp.TAG, "String content = " + Utils.convertStreamToString(new InflaterInputStream(content)));

            // Add created deck to the list of decks on server
            mDecks.put(name, new JSONArray("[0,0]"));
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        } catch (ClientProtocolException e) {
            Log.i(AnkiDroidApp.TAG, "ClientProtocolException = " + e.getMessage());
            e.printStackTrace();
        } catch (IOException e) {
            Log.i(AnkiDroidApp.TAG, "IOException = " + e.getMessage());
            e.printStackTrace();
        } catch (JSONException e) {
            Log.i(AnkiDroidApp.TAG, "JSONException = " + e.getMessage());
            e.printStackTrace();
        }
    }


    /**
     * Anki Desktop -> libanki/anki/sync.py, HttpSyncServerProxy - summary
     * 
     * @param lastSync
     */
    public JSONObject summary(double lastSync) {

        Log.i(AnkiDroidApp.TAG, "Summary Server");

        // Log.i(AnkiDroidApp.TAG, "user = " + username + ", password = " + password + ", lastSync = " + lastSync);
        JSONObject summaryServer = new JSONObject();

        try {
            // FIXME: Try to do the connection without encoding the lastSync in Base 64
            String data = "p=" + URLEncoder.encode(mPassword, "UTF-8")
                    + "&u=" + URLEncoder.encode(mUsername, "UTF-8")
                    + "&d=" + URLEncoder.encode(mDeckName, "UTF-8")
					+ "&v=" + URLEncoder.encode(SYNC_VERSION, "UTF-8")
                    + "&lastSync="
                    + URLEncoder.encode(Base64.encodeBytes(Utils.compress(String.format(Utils.ENGLISH_LOCALE, "%f", lastSync)
                            .getBytes())), "UTF-8") + "&base64=" + URLEncoder.encode("true", "UTF-8");

            // Log.i(AnkiDroidApp.TAG, "Data json = " + data);
            HttpPost httpPost = new HttpPost(SYNC_URL + "summary");
            StringEntity entity = new StringEntity(data);
            httpPost.setEntity(entity);
            httpPost.setHeader("Accept-Encoding", "identity");
            httpPost.setHeader("Content-type", "application/x-www-form-urlencoded");
            DefaultHttpClient httpClient = new DefaultHttpClient();
            HttpResponse response = httpClient.execute(httpPost);
            Log.i(AnkiDroidApp.TAG, "Response = " + response.toString());
            HttpEntity entityResponse = response.getEntity();
            Log.i(AnkiDroidApp.TAG, "Entity's response = " + entityResponse.toString());
            InputStream content = entityResponse.getContent();
            Log.i(AnkiDroidApp.TAG, "Content = " + content.toString());
            summaryServer = new JSONObject(Utils.convertStreamToString(new InflaterInputStream(content)));
            Log.i(AnkiDroidApp.TAG, "Summary server = ");
            Utils.printJSONObject(summaryServer);
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        } catch (ClientProtocolException e) {
            Log.i(AnkiDroidApp.TAG, "ClientProtocolException = " + e.getMessage());
        } catch (IOException e) {
            Log.i(AnkiDroidApp.TAG, "IOException = " + e.getMessage());
        } catch (JSONException e) {
            Log.i(AnkiDroidApp.TAG, "JSONException = " + e.getMessage());
        }

        return summaryServer;
    }


    /**
     * Anki Desktop -> libanki/anki/sync.py, HttpSyncServerProxy - applyPayload
     *
     * @param payload
     */
    public JSONObject applyPayload(JSONObject payload) {
        Log.i(AnkiDroidApp.TAG, "applyPayload");
        // Log.i(AnkiDroidApp.TAG, "user = " + username + ", password = " + password + ", payload = " +
        // payload.toString());
        JSONObject payloadReply = new JSONObject();

        try {
            // FIXME: Try to do the connection without encoding the payload in Base 64
            String data = "p=" + URLEncoder.encode(mPassword, "UTF-8") + "&u=" + URLEncoder.encode(mUsername, "UTF-8")
				+ "&v=" + URLEncoder.encode(SYNC_VERSION, "UTF-8") + "&d=" + URLEncoder.encode(mDeckName, "UTF-8")
			   	+ "&payload=" + URLEncoder.encode(Base64.encodeBytes(Utils.compress(payload.toString().getBytes())), "UTF-8")
				+ "&base64=" + URLEncoder.encode("true", "UTF-8");

            // Log.i(AnkiDroidApp.TAG, "Data json = " + data);
            HttpPost httpPost = new HttpPost(SYNC_URL + "applyPayload");
            StringEntity entity = new StringEntity(data);
            httpPost.setEntity(entity);
            httpPost.setHeader("Accept-Encoding", "identity");
            httpPost.setHeader("Content-type", "application/x-www-form-urlencoded");
            DefaultHttpClient httpClient = new DefaultHttpClient();
            HttpResponse response = httpClient.execute(httpPost);
            Log.i(AnkiDroidApp.TAG, "Response = " + response.toString());
            HttpEntity entityResponse = response.getEntity();
            Log.i(AnkiDroidApp.TAG, "Entity's response = " + entityResponse.toString());
            InputStream content = entityResponse.getContent();
            Log.i(AnkiDroidApp.TAG, "Content = " + content.toString());
            String contentString = Utils.convertStreamToString(new InflaterInputStream(content));
            Log.i(AnkiDroidApp.TAG, "Payload response = ");
            payloadReply = new JSONObject(contentString);
            Utils.printJSONObject(payloadReply, false);
            Utils.saveJSONObject(payloadReply);
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        } catch (ClientProtocolException e) {
            Log.i(AnkiDroidApp.TAG, "ClientProtocolException = " + e.getMessage());
        } catch (IOException e) {
            Log.i(AnkiDroidApp.TAG, "IOException = " + e.getMessage());
        } catch (JSONException e) {
            Log.i(AnkiDroidApp.TAG, "JSONException = " + e.getMessage());
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
