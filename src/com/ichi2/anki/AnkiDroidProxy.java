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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.InflaterInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

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

import android.util.Log;

import com.ichi2.utils.Base64;
import com.ichi2.utils.FileUtils;
import com.ichi2.utils.StringUtils;

public class AnkiDroidProxy {

	private static final String TAG = "AnkiDroid";

	//Used to format doubles with English's decimal separator system 
	private static final Locale ENGLISH_LOCALE = new Locale("en_US");
	
	private static final int CHUNK_SIZE = 32768;
	
	/**
	 * Connection settings
	 */
	private static final String SYNC_URL = "http://anki.ichi2.net/sync/";
	//78.46.104.28
	private static final String SYNC_HOST = "anki.ichi2.net"; 
	private static final String SYNC_PORT = "80";
	
	private String username;
	private String password;

	private JSONArray decks;
	private String deckName;


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
	
	public AnkiDroidProxy(String user, String password)
	{
		this.username = user;
		this.password = password;
		this.deckName = "";
	}
	
    public JSONArray getDecks()
    {
    	Log.i(TAG, "user = " + username + ", password = " + password);
    	JSONArray decks = new JSONArray();
    	
    	try {
        	String data = "p=" + URLEncoder.encode(password,"UTF-8") + "&client=ankiqt-0.9.9.8.6&u=" + URLEncoder.encode(username,"UTF-8") + "&d=None&sources=" + URLEncoder.encode("[]","UTF-8") + "&libanki=0.9.9.8.6&pversion=5";

        	Log.i(TAG, "Data json = " + data);
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
			decks = new JSONArray(StringUtils.convertStreamToString(new InflaterInputStream(content)));
			Log.i(TAG, "String content = " + decks);
			
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		} catch (ClientProtocolException e)
		{
			Log.i(TAG, "ClientProtocolException = " + e.getMessage());
		} catch (IOException e)
		{
			Log.i(TAG, "IOException = " + e.getMessage());
		} catch (JSONException e) {
			Log.i(TAG, "JSONException = " + e.getMessage());
			e.printStackTrace();
		}
		
		return decks;
    }
    
    public void createDeck()
    {
    	Log.i(TAG, "user = " + username + ", password = " + password);

    	try {
        	String data = "p=" + URLEncoder.encode(password,"UTF-8") + "&u=" + URLEncoder.encode(username,"UTF-8") + "&d=None&name=" + URLEncoder.encode("test","UTF-8");

        	Log.i(TAG, "Data json = " + data);
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
			Log.i(TAG, "String content = " + StringUtils.convertStreamToString(new InflaterInputStream(content)));
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		} catch (ClientProtocolException e)
		{
			Log.i(TAG, "ClientProtocolException = " + e.getMessage());
		} catch (IOException e)
		{
			Log.i(TAG, "IOException = " + e.getMessage());
		}
    }
    
    
    /**
     * Anki Desktop -> libanki/anki/sync.py, HttpSyncServerProxy - summary
     * @param lastSync
     */
	public JSONObject summary(double lastSync) {
    	
		Log.i(TAG, "Summary Server");
		
		Log.i(TAG, "user = " + username + ", password = " + password + ", lastSync = " + lastSync);
		JSONObject summaryServer = new JSONObject();
 
    	try {
    		// FIXME: Try to do the connection without encoding the lastSync in Base 64
        	String data = "p=" + URLEncoder.encode(password,"UTF-8") + "&u=" + URLEncoder.encode(username,"UTF-8") + "&d=" + URLEncoder.encode(deckName, "UTF-8") + "&lastSync=" + URLEncoder.encode(Base64.encodeBytes(StringUtils.compress(String.format(ENGLISH_LOCALE,"%f",lastSync).getBytes())), "UTF-8") + "&base64=" + URLEncoder.encode("true", "UTF-8");
        	
        	Log.i(TAG, "Data json = " + data);
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
			summaryServer = new JSONObject(StringUtils.convertStreamToString(new InflaterInputStream(content)));
			Log.i(TAG, "Summary server = ");
			Utils.printJSONObject(summaryServer);
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		} catch (ClientProtocolException e)
		{
			Log.i(TAG, "ClientProtocolException = " + e.getMessage());
		} catch (IOException e)
		{
			Log.i(TAG, "IOException = " + e.getMessage());
		} catch (JSONException e) 
		{
			Log.i(TAG, "JSONException = " + e.getMessage());
		}
		
		return summaryServer;
	}
	
    /**
     * Anki Desktop -> libanki/anki/sync.py, HttpSyncServerProxy - applyPayload
     * @param lastSync
     */
	public JSONObject applyPayload(JSONObject payload) 
	{
		Log.i(TAG, "applyPayload");
		Log.i(TAG, "user = " + username + ", password = " + password + ", payload = " + payload.toString());
		JSONObject payloadReply = new JSONObject();
		
		try {
			// FIXME: Try to do the connection without encoding the payload in Base 64
			String data = "p=" + URLEncoder.encode(password,"UTF-8") + "&u=" + URLEncoder.encode(username,"UTF-8") + "&d=" + URLEncoder.encode(deckName, "UTF-8") + "&payload=" + URLEncoder.encode(Base64.encodeBytes(StringUtils.compress(payload.toString().getBytes())), "UTF-8") + "&base64=" + URLEncoder.encode("true", "UTF-8");

			Log.i(TAG, "Data json = " + data);
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
			String contentString = StringUtils.convertStreamToString(new InflaterInputStream(content));
			Log.i(TAG, "Payload response = ");
			payloadReply = new JSONObject(contentString);
			Utils.printJSONObject(payloadReply, false);
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		} catch (ClientProtocolException e)
		{
			Log.i(TAG, "ClientProtocolException = " + e.getMessage());
		} catch (IOException e)
		{
			Log.i(TAG, "IOException = " + e.getMessage());
		} catch (JSONException e) 
		{
			Log.i(TAG, "JSONException = " + e.getMessage()); 
		} 
		
		return payloadReply;
	}
	
	
	
	/**
	 * Get shared decks
	 */
	
	public static List<SharedDeck> getSharedDecks() throws Exception {
		
		try {
			if(sharedDecks == null)
			{
				sharedDecks = new ArrayList<SharedDeck>();
				
				HttpGet httpGet = new HttpGet("http://anki.ichi2.net/file/search");
				httpGet.setHeader("Accept-Encoding", "identity");
				httpGet.setHeader("Host", "anki.ichi2.net");
				DefaultHttpClient defaultHttpClient = new DefaultHttpClient();

				HttpResponse httpResponse = defaultHttpClient.execute(httpGet);
				String response = StringUtils.convertStreamToString(httpResponse.getEntity().getContent());
				//Log.i(TAG, "Content = " + response);
				sharedDecks.addAll(handleResult(response));
			}
		} catch (Exception e)
		{
			sharedDecks = null;
			throw new Exception();
		}
		
		return sharedDecks;
	}
	
    private static List<SharedDeck> handleResult(String result) throws JSONException
    {
    	List<SharedDeck> sharedDecks = new ArrayList<SharedDeck>();
    	
		JSONArray jsonSharedDecks = new JSONArray(result);
		
		if(jsonSharedDecks != null)
		{
			//Log.i(TAG, "Number of shared decks = " + jsonSharedDecks.length());
			
			for(int i = 0; i < jsonSharedDecks.length(); i++)
			{
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
				
				//sharedDeck.prettyLog();
				
				sharedDecks.add(sharedDeck);
			}
		}
		
		return sharedDecks;
    }

	public static String downloadSharedDeck(SharedDeck sharedDeck) throws ClientProtocolException, IOException {
    	Log.i(TAG, "Downloading deck " + sharedDeck.getId());
    	
		HttpGet httpGet = new HttpGet("http://anki.ichi2.net/file/get?id=" + sharedDeck.getId());
		httpGet.setHeader("Accept-Encoding", "identity");
		httpGet.setHeader("Host", "anki.ichi2.net");
		httpGet.setHeader("Connection", "close");
		DefaultHttpClient httpClient = new DefaultHttpClient();
		HttpResponse httpResponse = httpClient.execute(httpGet);
		Log.i(TAG, "Connection finished!");
		InputStream is = httpResponse.getEntity().getContent();
		String deckFilename = handleFile(is, sharedDeck);
		is.close();
		
		return deckFilename;
	}
	
	private static String handleFile(InputStream source, SharedDeck sharedDeck) throws IOException
	{
		String deckFilename = "";
		
		ZipInputStream zipInputStream = null;
		if(sharedDeck.getFileName().endsWith(".zip"))
		{
			zipInputStream = new ZipInputStream(source);
			
			String title = sharedDeck.getTitle();
			title = title.replace("^", "");
			title = title.substring(0, java.lang.Math.min(title.length(), 40));
			
			if(new File(AnkiDroidApp.getStorageDirectory() + "/" + title + ".anki").exists())
				title += System.currentTimeMillis();
			
			String partialDeckPath = AnkiDroidApp.getStorageDirectory() + "/" + title;
			deckFilename = partialDeckPath + ".anki";
			
			ZipEntry zipEntry = null;
			while((zipEntry = zipInputStream.getNextEntry()) != null)
			{
				//Log.i(TAG, "zipEntry = " + zipEntry.getName());
				
				if("shared.anki".equalsIgnoreCase(zipEntry.getName()))
				{
					FileUtils.writeToFile(zipInputStream, deckFilename);
				}
				else if(zipEntry.getName().startsWith("shared.media/", 0))
				{
					//Log.i(TAG, "Folder created = " + new File(AnkiDroidApp.getStorageDirectory() + title + ".media/").mkdir());
					//Log.i(TAG, "Destination = " + AnkiDroidApp.getStorageDirectory() + "/" + title + ".media/" + zipEntry.getName().replace("shared.media/", ""));
					FileUtils.writeToFile(zipInputStream, partialDeckPath + ".media/" + zipEntry.getName().replace("shared.media/", ""));
				}
			}
			zipInputStream.close();
		}
		
		return deckFilename;
	}
	
}
