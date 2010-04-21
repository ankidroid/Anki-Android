package com.ichi2.anki;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Locale;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.database.Cursor;
import android.util.Log;

import com.ichi2.utils.FileUtils;

public class SyncClient {

	private static final String TAG = "AnkiDroid";
	
	//Used to format doubles with English's decimal separator system 
	private static final Locale ENGLISH_LOCALE = new Locale("en_US");
	
	/**
	 * Connection settings
	 */
	private static final String SYNC_URL = "http://anki.ichi2.net/sync/";
	//78.46.104.28
	private static final String SYNC_HOST = "anki.ichi2.net"; 
	private static final String SYNC_PORT = "80";
	
	private static final int CHUNK_SIZE = 32768;
	
	/**
	 * Constants used on the multipart message
	 */
	private static final String MIME_BOUNDARY = "Anki-sync-boundary";
	private final String END = "\r\n";
	private final String TWO_HYPHENS = "--";
	
	private Deck deck;
	
	public SyncClient(Deck deck)
	{
		this.deck = deck;
	}
	
    /**
     * Anki Desktop -> libanki/anki/sync.py, SyncTools - summary
     * @param lastSync
     */
    public JSONObject summary(double lastSync)
    {
    	Log.i(TAG, "Summary Local");
    	deck.lastSync = lastSync;
    	deck.commitToDB();
    	
    	String lastSyncString = String.format(ENGLISH_LOCALE, "%f", lastSync);
    	//Cards
    	JSONArray cards = cursorToJSONArray(AnkiDb.database.rawQuery("SELECT id, modified FROM cards WHERE modified > " + lastSyncString, null));
    	//Cards - delcards
    	JSONArray delcards = cursorToJSONArray(AnkiDb.database.rawQuery("SELECT cardId, deletedTime FROM cardsDeleted WHERE deletedTime > " + lastSyncString, null));
    	
    	//Facts
    	JSONArray facts = cursorToJSONArray(AnkiDb.database.rawQuery("SELECT id, modified FROM facts WHERE modified > " + lastSyncString, null));
    	//Facts - delfacts
    	JSONArray delfacts = cursorToJSONArray(AnkiDb.database.rawQuery("SELECT factId, deletedTime FROM factsDeleted WHERE deletedTime > " + lastSyncString, null));
    	
    	//Models
    	JSONArray models = cursorToJSONArray(AnkiDb.database.rawQuery("SELECT id, modified FROM models WHERE modified > " + lastSyncString, null));
    	//Models - delmodels
    	JSONArray delmodels = cursorToJSONArray(AnkiDb.database.rawQuery("SELECT modelId, deletedTime FROM modelsDeleted WHERE deletedTime > " + lastSyncString, null));

    	//Media
    	JSONArray media = cursorToJSONArray(AnkiDb.database.rawQuery("SELECT id, created FROM media WHERE created > " + lastSyncString, null));
    	//Media - delmedia
    	JSONArray delmedia = cursorToJSONArray(AnkiDb.database.rawQuery("SELECT mediaId, deletedTime FROM mediaDeleted WHERE deletedTime > " + lastSyncString, null));

    	JSONObject summary = new JSONObject();
    	try {
			summary.put("cards", cards);
	    	summary.put("delcards", delcards);
	    	summary.put("facts", facts);
	    	summary.put("delfacts", delfacts);
	    	summary.put("models", models);
	    	summary.put("delmodels", delmodels);
	    	summary.put("media", media);
	    	summary.put("delmedia", delmedia);
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		Log.i(TAG, "Summary Local = ");
		Utils.printJSONObject(summary, false);
		
    	return summary;
    }
    
    private JSONArray cursorToJSONArray(Cursor cursor)
    {
    	JSONArray jsonArray = new JSONArray();
    	while (cursor.moveToNext())
    	{
    		JSONArray element = new JSONArray();
    		
    		try {
    			element.put(cursor.getLong(0));
				element.put(cursor.getDouble(1));
			} catch (JSONException e) {
				Log.i(TAG, "JSONException = " + e.getMessage());
			}
			jsonArray.put(element);
    	}
    	
    	cursor.close();
    	
    	return jsonArray;
    }
    
	public void fullSyncFromLocal(String password, String username, String deckName, String deckPath)
	{
		URL url;
		try {
			Log.i(TAG, "Fullup");
			url = new URL(SYNC_URL + "fullup");
			HttpURLConnection conn = (HttpURLConnection)url.openConnection();

			conn.setDoInput(true);
			conn.setDoOutput(true);
			conn.setUseCaches(false);
			conn.setRequestMethod("POST");

			conn.setRequestProperty("Connection", "close");
			conn.setRequestProperty("Charset", "UTF-8");
			conn.setRequestProperty("Content-Type", "multipart/form-data;boundary="+ MIME_BOUNDARY);
			conn.setRequestProperty("Host", SYNC_HOST);

			DataOutputStream ds = new DataOutputStream(conn.getOutputStream());
			Log.i(TAG, "Pass");
			ds.writeBytes(TWO_HYPHENS + MIME_BOUNDARY + END);
			ds.writeBytes("Content-Disposition: form-data; name=\"p\"" + END + END + password + END);
			Log.i(TAG, "User");
			ds.writeBytes(TWO_HYPHENS + MIME_BOUNDARY + END);
			ds.writeBytes("Content-Disposition: form-data; name=\"u\"" + END + END + username + END);
			Log.i(TAG, "DeckName");
			ds.writeBytes(TWO_HYPHENS + MIME_BOUNDARY + END);
			ds.writeBytes("Content-Disposition: form-data; name=\"d\"" + END + END + deckName + END);
			Log.i(TAG, "Deck");
			ds.writeBytes(TWO_HYPHENS + MIME_BOUNDARY + END);
			ds.writeBytes("Content-Disposition: form-data; name=\"deck\";filename=\"deck\"" + END);
			ds.writeBytes("Content-Type: application/octet-stream" + END);
			ds.writeBytes(END);

			FileInputStream fStream = new FileInputStream(deckPath);
			byte[] buffer = new byte[CHUNK_SIZE];
			int length = -1;

			Deflater deflater = new Deflater(Deflater.BEST_SPEED);
			DeflaterOutputStream dos = new DeflaterOutputStream(ds, deflater);

			Log.i(TAG, "Writing buffer...");
			while((length = fStream.read(buffer)) != -1) {
				dos.write(buffer, 0, length);
				Log.i(TAG, "Length = " + length);
			}
			dos.finish();

			ds.writeBytes(END);
			ds.writeBytes(TWO_HYPHENS + MIME_BOUNDARY + TWO_HYPHENS + END);
			Log.i(TAG, "Closing streams...");

			ds.flush();
			ds.close();

			// Ensure we got the HTTP 200 response code
			int responseCode = conn.getResponseCode();
			if (responseCode != 200) {
				Log.i(TAG, "Response code = 200");
				//throw new Exception(String.format("Received the response code %d from the URL %s", responseCode, url));
			} else
			{
				Log.i(TAG, "Response code = " + responseCode);
			}

			// Read the response
			InputStream is = conn.getInputStream();
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			byte[] bytes = new byte[1024];
			int bytesRead;
			while((bytesRead = is.read(bytes)) != -1) {
				baos.write(bytes, 0, bytesRead);
			}
			byte[] bytesReceived = baos.toByteArray();
			baos.close();

			is.close();
			String response = new String(bytesReceived);
			
			Log.i(TAG, "Finished!");
		} catch (MalformedURLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public void fullSyncFromServer(String password, String username, String deckName, String deckPath)
	{
		Log.i(TAG, "password = " + password + ", user = " + username +  ", d = " + deckName);

		try {
			String data = "p=" + URLEncoder.encode(password,"UTF-8") + "&u=" + URLEncoder.encode(username,"UTF-8") + "&d=" + URLEncoder.encode(deckName, "UTF-8");

			Log.i(TAG, "Data json = " + data);
			HttpPost httpPost = new HttpPost(SYNC_URL + "fulldown");
			StringEntity entity = new StringEntity(data);
			httpPost.setEntity(entity);
			httpPost.setHeader("Content-type", "application/x-www-form-urlencoded");
			DefaultHttpClient httpClient = new DefaultHttpClient();
			HttpResponse response = httpClient.execute(httpPost);
			Log.i(TAG, "Response = " + response.toString());
			HttpEntity entityResponse = response.getEntity();
			Log.i(TAG, "Entity's response = " + entityResponse.toString());
			InputStream content = entityResponse.getContent();
			Log.i(TAG, "Content = " + content.toString());
			FileUtils.writeToFile(new InflaterInputStream(content), deckPath);
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

}
