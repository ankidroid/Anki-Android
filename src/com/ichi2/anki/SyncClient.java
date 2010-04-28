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
import java.sql.Date;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
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
import android.database.SQLException;
import android.database.sqlite.SQLiteStatement;
import android.util.Log;

public class SyncClient {

	private static final String TAG = "AnkiDroid";
	
	//Used to format doubles with English's decimal separator system 
	private static final Locale ENGLISH_LOCALE = new Locale("en_US");
	
	/**
	 * Connection settings
	 */
	
	private static final String SYNC_URL = "http://anki.ichi2.net/sync/";
	private static final String SYNC_HOST = "anki.ichi2.net"; //78.46.104.28
	private static final String SYNC_PORT = "80";
	
	
	//Test
	/*
	private static final String SYNC_URL = "http://192.168.2.103:8001/sync/";
	private static final String SYNC_HOST = "192.168.2.103";
	private static final String SYNC_PORT = "8001";
	*/
	private static final int CHUNK_SIZE = 32768;
	
	private enum Keys {models, facts, cards, media};
	
	/**
	 * Constants used on the multipart message
	 */
	private static final String MIME_BOUNDARY = "Anki-sync-boundary";
	private final String END = "\r\n";
	private final String TWO_HYPHENS = "--";
	
	private Deck deck;
	private AnkiDroidProxy server;
	private double localTime;
	private double remoteTime;
	
	public SyncClient(Deck deck)
	{
		this.deck = deck;
		this.server = null;
		this.localTime = 0;
		this.remoteTime = 0;
	}
	
	public AnkiDroidProxy getServer()
	{
		return server;
	}
	
	public void setServer(AnkiDroidProxy server) 
	{
		this.server = server;
	}
	
	public void setDeck(Deck deck) 
	{
		this.deck = deck;
	}
	
    /**
     * Anki Desktop -> libanki/anki/sync.py, prepareSync
     * @return
     */
    public boolean prepareSync()
    {
    	Log.i(TAG, "prepareSync = " + String.format(ENGLISH_LOCALE, "%f", deck.lastSync));
    	
    	localTime = deck.modified;
    	remoteTime = server.modified();
    	
    	Log.i(TAG, "localTime = " + localTime);
    	Log.i(TAG, "remoteTime = " + remoteTime);
    	
    	if(localTime == remoteTime)
    		return false;
    	
    	double l = deck.lastSync;
    	Log.i(TAG, "lastSync local = " + String.format(ENGLISH_LOCALE, "%f", l));
    	double r =  server.lastSync();
    	Log.i(TAG, "lastSync remote = " + String.format(ENGLISH_LOCALE, "%f", r));
    	
    	if(l != r)
    	{
    		deck.lastSync = java.lang.Math.min(l, r) - 600;
    		Log.i(TAG, "deck.lastSync = min(l,r) - 600");
    	}
    	else
    	{
    		deck.lastSync = l;
    	}
    	
    	Log.i(TAG, "deck.lastSync = " + deck.lastSync);
    	return true;
    }
    
	public JSONArray summaries()
	{
		Log.i(TAG, "summaries = " + String.format(ENGLISH_LOCALE, "%f", deck.lastSync));

		JSONArray summaries = new JSONArray();
		summaries.put(summary(deck.lastSync));
		summaries.put(server.summary(deck.lastSync));

		return summaries;
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
			Log.i(TAG, "JSONException = " + e.getMessage());
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

	/**
	 * Anki Desktop -> libanki/anki/sync.py, SyncTools - genPayload
	 */
	public JSONObject genPayload(JSONArray summaries)
	{
		//Log.i(TAG, "genPayload");
		//Ensure global stats are available (queue may not be built)
		preSyncRefresh();
		
		JSONObject payload = new JSONObject();
		
		Keys[] keys = Keys.values();
		
		for(int i = 0; i < keys.length; i++)
		{
			//Log.i(TAG, "Key " + keys[i].name());
			String key = keys[i].name();
			try {
				// Handle models, facts, cards and media
				JSONArray diff = diffSummary((JSONObject)summaries.get(0), (JSONObject)summaries.get(1), key);
				payload.put("added-" + key, getObjsFromKey((JSONArray)diff.get(0), key));
				payload.put("deleted-" + key, diff.get(1));
				payload.put("missing-" + key, diff.get(2));
				deleteObjsFromKey((JSONArray)diff.get(3), key);
			} catch (JSONException e) {
				Log.i(TAG, "JSONException = " + e.getMessage());
			}
		}
		
		// If the last modified deck was the local one, handle the remainder
		if(localTime > remoteTime)
		{
			
			try {
				payload.put("deck", bundleDeck());
				payload.put("stats", bundleStats());
				payload.put("history",bundleHistory());
				payload.put("sources", bundleSources());
				deck.lastSync = deck.modified;
			} catch (JSONException e) {
				Log.i(TAG, "JSONException = " + e.getMessage());
			}
		}
		
		Log.i(TAG, "Payload =");
		Utils.printJSONObject(payload, true);
		
		return payload;
	}
	
	/**
	 * Anki Desktop -> libanki/anki/sync.py, SyncTools - payloadChanges
	 */
	private Object[] payloadChanges(JSONObject payload)
	{
		Object[] h = new Object[8];
		
		try {
			h[0] = payload.getJSONObject("added-facts").getJSONArray("facts").length();
			h[1] = payload.getJSONArray("missing-facts").length();
			h[2] = payload.getJSONArray("added-cards").length();
			h[3] = payload.getJSONArray("missing-cards").length();
			h[4] = payload.getJSONArray("added-models").length();
			h[5] = payload.getJSONArray("missing-models").length();
			
			if(localTime > remoteTime)
			{
				h[6] = "all";
				h[7] = 0;
			}
			else
			{
				h[6] = 0;
				h[7] = "all";
			}
		} catch (JSONException e) {
			Log.i(TAG, "JSONException = " + e.getMessage());
		}

		return h;
	}
	
	public String payloadChangeReport(JSONObject payload)
	{
		return AnkiDroidApp.getAppResources().getString(R.string.change_report_format, payloadChanges(payload));
	}
	
	public void applyPayloadReply(JSONObject payloadReply)
	{
		Log.i(TAG, "applyPayloadReply");
		Keys[] keys = Keys.values();
		
		for(int i = 0; i < keys.length; i++)
		{
			String key = keys[i].name();
			updateObjsFromKey(payloadReply, key);
		}
		
		try {
			if(!payloadReply.isNull("deck"))
			{
				updateDeck(payloadReply.getJSONObject("deck"));
				updateStats(payloadReply.getJSONObject("stats"));
				updateHistory(payloadReply.getJSONArray("history"));
				if(!payloadReply.isNull("sources"))
				{
					updateSources(payloadReply.getJSONArray("sources"));
				}
			}
		} catch (JSONException e) {
			Log.i(TAG, "JSONException = " + e.getMessage());
		}
		
		deck.commitToDB();
		
		// Rebuild priorities on client
		
		// Get card ids
		try {
			JSONArray cards = payloadReply.getJSONArray("added-cards");
			int len = cards.length();
			long[] cardIds = new long[len];
			for(int i = 0; i < len; i++)
			{
				cardIds[i] = cards.getJSONArray(i).getLong(0);
			}
			//TODO: updateCardTags
			rebuildPriorities(cardIds);
			// Rebuild due counts
			deck.rebuildCounts(false);
		} catch (JSONException e) {
			Log.i(TAG, "JSONException = " + e.getMessage());
		}
	}
	
	/**
	 * Anki Desktop -> libanki/anki/sync.py, SyncTools - preSyncRefresh
	 */
	private void preSyncRefresh()
	{
		Stats.globalStats(deck);
	}
	
	private void rebuildPriorities(long[] cardIds)
	{
		try {
			// TODO: Implement updateAllPriorities
			//deck.updateAllPriorities(true, false);
			deck.updatePriorities(cardIds, null, false);
		} catch (SQLException e)
		{
			Log.e(TAG, "SQLException e = " + e.getMessage());
		}
	}
	
	/**
	 * Anki Desktop -> libanki/anki/sync.py, SyncTools - diffSummary
	 */
	private JSONArray diffSummary(JSONObject summaryLocal, JSONObject summaryServer, String key)
	{
		JSONArray locallyEdited = new JSONArray();
		JSONArray locallyDeleted = new JSONArray();
		JSONArray remotelyEdited = new JSONArray();
		JSONArray remotelyDeleted = new JSONArray();
		
		Log.i(TAG, "\ndiffSummary - Key = " + key);
		Log.i(TAG, "\nSummary local = ");
		Utils.printJSONObject(summaryLocal, false);
		Log.i(TAG, "\nSummary server = ");
		Utils.printJSONObject(summaryServer, false);
		
		//Hash of all modified ids
		HashSet<Long> ids = new HashSet<Long>();
		
		try {
			//Build a hash (id item key, modification time) of the modifications on server (null -> deleted)
			HashMap<Long, Double> remoteMod = new HashMap<Long, Double>();
			putExistingItems(ids, remoteMod, summaryServer.getJSONArray(key));
			HashMap<Long, Double> rdeletedIds = putDeletedItems(ids, remoteMod, summaryServer.getJSONArray("del" + key));
			
			//Build a hash (id item, modification time) of the modifications on client (null -> deleted)
			HashMap<Long, Double> localMod = new HashMap<Long, Double>();
			putExistingItems(ids, localMod, summaryLocal.getJSONArray(key));
			HashMap<Long, Double> ldeletedIds = putDeletedItems(ids, localMod, summaryLocal.getJSONArray("del" + key));
			
			Iterator<Long> idsIterator = ids.iterator();
			while(idsIterator.hasNext())
			{
				Long id = idsIterator.next();
				Double localModTime = localMod.get(id);
				Double remoteModTime = remoteMod.get(id);
				
				Log.i(TAG, "\nid = " + id + ", localModTime = " + localModTime + ", remoteModTime = " + remoteModTime);
				//Changed/Existing on both sides
				if(localModTime != null && remoteModTime != null)
				{
					Log.i(TAG, "localModTime not null AND remoteModTime not null");
					if(localModTime < remoteModTime)
					{
						Log.i(TAG, "Remotely edited");
						remotelyEdited.put(id);
					}
					else if(localModTime > remoteModTime)
					{
						Log.i(TAG, "Locally edited");
						locallyEdited.put(id);
					}
				}
				// If it's missing on server or newer here, sync
				else if(localModTime != null  && remoteModTime == null)
				{
					Log.i(TAG, "localModTime not null AND remoteModTime null");
					if(!rdeletedIds.containsKey(id) || rdeletedIds.get(id) < localModTime)
					{
						Log.i(TAG, "Locally edited");
						locallyEdited.put(id);
					}
					else
					{
						Log.i(TAG, "Remotely deleted");
						remotelyDeleted.put(id);
					}
				}
				// If it's missing locally or newer there, sync
				else if(remoteModTime != null && localModTime == null)
				{
					Log.i(TAG, "remoteModTime not null AND localModTime null");
					if(!ldeletedIds.containsKey(id) || ldeletedIds.get(id) < remoteModTime)
					{
						Log.i(TAG, "Remotely edited");
						remotelyEdited.put(id);
					}
					else
					{
						Log.i(TAG, "Locally deleted");
						locallyDeleted.put(id);
					}
				}
				//Deleted or not modified in both sides
				else
				{
					Log.i(TAG, "localModTime null AND remoteModTime null");
					if(ldeletedIds.containsKey(id) && !rdeletedIds.containsKey(id))
					{
						Log.i(TAG, "Locally deleted");
						locallyDeleted.put(id);
					}
					else if(rdeletedIds.containsKey(id) && !ldeletedIds.containsKey(id))
					{
						Log.i(TAG, "Remotely deleted");
						remotelyDeleted.put(id);
					}
				}
			}
		} catch (JSONException e) {
			Log.i(TAG, "JSONException = " + e.getMessage());
		}

		JSONArray diff = new JSONArray();
		diff.put(locallyEdited);
		diff.put(locallyDeleted);
		diff.put(remotelyEdited);
		diff.put(remotelyDeleted);
		
		return diff;
	}
	
	private void putExistingItems(HashSet<Long> ids, HashMap<Long, Double> dictExistingItems, JSONArray existingItems) 
	{
		for(int i = 0; i < existingItems.length(); i++)
		{
			try {
				JSONArray itemModified = existingItems.getJSONArray(i);
				Long idItem = itemModified.getLong(0);
				Double modTimeItem = itemModified.getDouble(1);
				dictExistingItems.put(idItem, modTimeItem);
				ids.add(idItem);
			} catch (JSONException e) {
				Log.i(TAG, "JSONException = " + e.getMessage());
			}
		}
	}

	private HashMap<Long, Double> putDeletedItems(HashSet<Long> ids, HashMap<Long, Double> dictDeletedItems, JSONArray deletedItems)
	{
		HashMap<Long, Double> deletedIds = new HashMap<Long, Double>(); 
		for(int i = 0; i < deletedItems.length(); i++)
		{
			try {
				JSONArray itemModified = deletedItems.getJSONArray(i);
				Long idItem = itemModified.getLong(0);
				Double modTimeItem = itemModified.getDouble(1);
				dictDeletedItems.put(idItem, null);
				deletedIds.put(idItem, modTimeItem);
				ids.add(idItem);
			} catch (JSONException e) {
				Log.i(TAG, "JSONException = " + e.getMessage());
			}
		}
		
		return deletedIds;
	}
	
	private Object getObjsFromKey(JSONArray ids, String key)
	{
		if("models".equalsIgnoreCase(key))
		{
			return getModels(ids);
		}
		else if("facts".equalsIgnoreCase(key))
		{
			return getFacts(ids);
		}
		else if("cards".equalsIgnoreCase(key))
		{
			return getCards(ids);
		}
		else if("media".equalsIgnoreCase(key))
		{
			return getMedia(ids);
		}
		
		return null;
	}
	
	private void deleteObjsFromKey(JSONArray ids, String key) throws JSONException
	{
		if("models".equalsIgnoreCase(key))
		{
			deleteModels(ids);
		}
		else if("facts".equalsIgnoreCase(key))
		{
			deck.deleteFacts(Utils.jsonArrayToListString(ids));
		}
		else if("cards".equalsIgnoreCase(key))
		{
			deck.deleteCards(Utils.jsonArrayToListString(ids));
		}
		else if("media".equalsIgnoreCase(key))
		{
			deleteMedia(ids);
		}
	}
	
	private void updateObjsFromKey(JSONObject payloadReply, String key)
	{
		try {
			if("models".equalsIgnoreCase(key))
			{
				Log.i(TAG, "updateModels");
				updateModels(payloadReply.getJSONArray("added-models"));
			}
			else if("facts".equalsIgnoreCase(key))
			{
				Log.i(TAG, "updateFacts");
				updateFacts(payloadReply.getJSONObject("added-facts"));
			}
			else if("cards".equalsIgnoreCase(key))
			{
				Log.i(TAG, "updateCards");
				updateCards(payloadReply.getJSONArray("added-cards"));
			}
			else if("media".equalsIgnoreCase(key))
			{
				Log.i(TAG, "updateMedia");
				updateMedia(payloadReply.getJSONArray("added-media"));
			}
		} catch (JSONException e) {
			Log.i(TAG, "JSONException = " + e.getMessage());
		}
	}
	
	/**
	 * Models
	 */
	
	//TODO: Include the case with updateModified
	/**
	 * Anki Desktop -> libanki/anki/sync.py, SyncTools - getModels
	 * @param ids
	 * @return
	 */
	private JSONArray getModels(JSONArray ids)//, boolean updateModified)
	{
		JSONArray models = new JSONArray();
		
		for(int i = 0; i < ids.length(); i++)
		{
			try {
				models.put(bundleModel(ids.getLong(i)));
			} catch (JSONException e) {
				Log.i(TAG, "JSONException = " + e.getMessage());
			}
		}
		
		return models;
	}
	

	/**
	 * Anki Desktop -> libanki/anki/sync.py, SyncTools - bundleModel
	 * @param id
	 * @return
	 */
	private JSONObject bundleModel(Long id)//, boolean updateModified
	{
		JSONObject model = new JSONObject();
		Cursor cursor = AnkiDb.database.rawQuery("SELECT * FROM models WHERE id = " + id, null);
		if(cursor.moveToFirst())
		{
			try {
				model.put("id", cursor.getLong(0));
				model.put("deckId", cursor.getInt(1));
				model.put("created", cursor.getDouble(2));
				model.put("modified", cursor.getDouble(3));
				model.put("tags", cursor.getString(4));
				model.put("name", cursor.getString(5));
				model.put("description", cursor.getString(6));
				model.put("features", cursor.getDouble(7));
				model.put("spacing", cursor.getDouble(8));
				model.put("initialSpacing", cursor.getDouble(9));
				model.put("source", cursor.getInt(10));
				model.put("fieldModels", bundleFieldModels(id));
				model.put("cardModels", bundleCardModels(id));
			} catch (JSONException e) {
				Log.i(TAG, "JSONException = " + e.getMessage());
			}
		}
		cursor.close();
		
		Log.i(TAG, "Model = ");
		Utils.printJSONObject(model, false);
		
		return model;
	}
	
	/**
	 * Anki Desktop -> libanki/anki/sync.py, SyncTools - bundleFieldModel
	 * @param id
	 * @return
	 */
	private JSONArray bundleFieldModels(Long id)
	{
		JSONArray fieldModels = new JSONArray();
		
		Cursor cursor = AnkiDb.database.rawQuery("SELECT * FROM fieldModels WHERE modelId = " + id, null);
		while(cursor.moveToNext())
		{
			JSONObject fieldModel = new JSONObject();
			
			try {
				fieldModel.put("id", cursor.getLong(0));
				fieldModel.put("ordinal", cursor.getInt(1));
				fieldModel.put("modelId", cursor.getLong(2));
				fieldModel.put("name", cursor.getString(3));
				fieldModel.put("description", cursor.getString(4));
				fieldModel.put("features", cursor.getString(5));
				fieldModel.put("required", cursor.getString(6));
				fieldModel.put("unique", cursor.getString(7));
				fieldModel.put("numeric", cursor.getString(8));
				fieldModel.put("quizFontFamily", cursor.getString(9));
				fieldModel.put("quizFontSize", cursor.getInt(10));
				fieldModel.put("quizFontColour", cursor.getString(11));
				fieldModel.put("editFontFamily", cursor.getString(12));
				fieldModel.put("editFontSize", cursor.getInt(13));
			} catch (JSONException e) {
				Log.i(TAG, "JSONException = " + e.getMessage());
			}

			fieldModels.put(fieldModel);
		}
		cursor.close();
		
		return fieldModels;
	}
	
	
	private JSONArray bundleCardModels(Long id)
	{
		JSONArray cardModels = new JSONArray();
		
		Cursor cursor = AnkiDb.database.rawQuery("SELECT * FROM cardModels WHERE modelId = " + id, null);
		while(cursor.moveToNext())
		{
			JSONObject cardModel = new JSONObject();
			
			try {
				cardModel.put("id", cursor.getLong(0));
				cardModel.put("ordinal", cursor.getInt(1));
				cardModel.put("modelId", cursor.getLong(2));
				cardModel.put("name", cursor.getString(3));
				cardModel.put("description", cursor.getString(4));
				cardModel.put("active", cursor.getString(5));
				cardModel.put("qformat", cursor.getString(6));
				cardModel.put("aformat", cursor.getString(7));
				cardModel.put("lformat", cursor.getString(8));
				cardModel.put("qedformat", cursor.getString(9));
				cardModel.put("aedformat", cursor.getString(10));
				cardModel.put("questionInAnswer", cursor.getString(11));
				cardModel.put("questionFontFamily", cursor.getString(12));
				cardModel.put("questionFontSize ", cursor.getInt(13));
				cardModel.put("questionFontColour", cursor.getString(14));
				cardModel.put("questionAlign", cursor.getInt(15));
				cardModel.put("answerFontFamily", cursor.getString(16));
				cardModel.put("answerFontSize", cursor.getInt(17));
				cardModel.put("answerFontColour", cursor.getString(18));
				cardModel.put("answerAlign", cursor.getInt(19));
				cardModel.put("lastFontFamily", cursor.getString(20));
				cardModel.put("lastFontSize", cursor.getInt(21));
				cardModel.put("lastFontColour", cursor.getString(22));
				cardModel.put("editQuestionFontFamily", cursor.getString(23));
				cardModel.put("editQuestionFontSize", cursor.getInt(24));
				cardModel.put("editAnswerFontFamily", cursor.getString(25));
				cardModel.put("editAnswerFontSize", cursor.getInt(26));
				cardModel.put("allowEmptyAnswer", cursor.getString(27));
				cardModel.put("typeAnswer", cursor.getString(28));
			} catch (JSONException e) {
				Log.i(TAG, "JSONException = " + e.getMessage());
			}
			
			cardModels.put(cardModel);
		}
		cursor.close();
		
		return cardModels;
	}
	
	private void deleteModels(JSONArray ids)
	{
		Log.i(TAG, "deleteModels");
		int len = ids.length();
		for(int i = 0; i < len; i++)
		{
			try {
				deck.deleteModel(ids.getString(i));
			} catch (JSONException e) {
				Log.i(TAG, "JSONException = " + e.getMessage());
			}
		}
	}
	
	private void updateModels(JSONArray models)
	{
		ArrayList<String> insertedModelsIds = new ArrayList<String>();
		
		String sql = "INSERT OR REPLACE INTO models (id, deckId, created, modified, tags, name, description, features, spacing, initialSpacing, source) VALUES(?,?,?,?,?,?,?,?,?,?,?)";
		SQLiteStatement statement = AnkiDb.database.compileStatement(sql);
		int len = models.length();
		for(int i = 0; i < len; i++)
		{
			try {
				JSONObject model = models.getJSONObject(i);
				
				//id
				String id = model.getString("id");
				statement.bindString(1, id);
				//deckId
				statement.bindLong(2, model.getLong("deckId"));
				//created
				statement.bindDouble(3, model.getDouble("created"));
				//modified
				statement.bindDouble(4, model.getDouble("modified"));
				//tags
				statement.bindString(5, model.getString("tags"));
				//name
				statement.bindString(6, model.getString("name"));
				//description
				statement.bindString(7, model.getString("name"));
				//features
				statement.bindString(8, model.getString("features"));
				//spacing
				statement.bindDouble(9, model.getDouble("spacing"));
				//initialSpacing
				statement.bindDouble(10, model.getDouble("initialSpacing"));
				//source
				statement.bindLong(11, model.getLong("source"));
				
				statement.execute();
				
				insertedModelsIds.add(id);
				
				mergeFieldModels(id, model.getJSONArray("fieldModels"));
				mergeCardModels(id, model.getJSONArray("cardModels"));
			} catch (JSONException e) {
				Log.i(TAG, "JSONException = " + e.getMessage());
			}
		}
		statement.close();
		
		// Delete inserted models from modelsDeleted
		AnkiDb.database.execSQL("DELETE FROM modelsDeleted WHERE modelId IN " + Utils.ids2str(insertedModelsIds));
	}
	
	private void mergeFieldModels(String modelId, JSONArray fieldModels)
	{
		ArrayList<String> ids = new ArrayList<String>();
		
		String sql = "INSERT OR REPLACE INTO fieldModels VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
		SQLiteStatement statement = AnkiDb.database.compileStatement(sql);
		int len = fieldModels.length();
		for(int i = 0; i < len; i++)
		{
			try {
				JSONObject fieldModel = fieldModels.getJSONObject(i);
				
				//id
				String id = fieldModel.getString("id");
				statement.bindString(1, id);
				//ordinal
				statement.bindString(2, fieldModel.getString("ordinal"));
				//modelId
				statement.bindLong(3, fieldModel.getLong("modelId"));
				//name
				statement.bindString(4, fieldModel.getString("name"));
				//description
				statement.bindString(5, fieldModel.getString("description"));
				//features
				statement.bindString(6, fieldModel.getString("features"));
				//required
				statement.bindLong(7, Utils.booleanToInt(fieldModel.getBoolean("required")));
				//unique
				statement.bindLong(8, Utils.booleanToInt(fieldModel.getBoolean("unique")));
				//numeric
				statement.bindLong(9, Utils.booleanToInt(fieldModel.getBoolean("numeric")));
				//quizFontFamily
				if(fieldModel.isNull("quizFontFamily"))
				{
					statement.bindNull(10);
				}
				else
				{
					statement.bindString(10, fieldModel.getString("quizFontFamily"));
				}
				//quizFontSize
				if(fieldModel.isNull("quizFontSize"))
				{
					statement.bindNull(11);
				}
				else
				{
					statement.bindString(11, fieldModel.getString("quizFontSize"));
				}
				//quizFontColour
				if(fieldModel.isNull("quizFontColour"))
				{
					statement.bindNull(12);
				}
				else
				{
					statement.bindString(12, fieldModel.getString("quizFontColour"));
				}
				//editFontFamily
				if(fieldModel.isNull("editFontFamily"))
				{
					statement.bindNull(13);
				}
				else
				{
					statement.bindString(13, fieldModel.getString("editFontFamily"));
				}
				//editFontSize
				statement.bindString(14, fieldModel.getString("editFontSize"));
				
				statement.execute();
				
				ids.add(id);
			} catch (JSONException e) {
				Log.i(TAG, "JSONException");
			}
		}
		statement.close();
		
		//Delete field models that were not returned by the server
		ArrayList<String> fieldModelsIds = AnkiDb.queryColumn(String.class, 
																"SELECT id FROM fieldModels WHERE modelId = " + modelId, 
																0);
		int lenFieldModelsIds = fieldModelsIds.size();
		for(int i = 0; i < lenFieldModelsIds; i++)
		{
			String fieldModelId = fieldModelsIds.get(i);
			if(!ids.contains(fieldModelId))
			{
				deck.deleteFieldModel(modelId, fieldModelId);
			}
		}
	}
	
	private void mergeCardModels(String modelId, JSONArray cardModels)
	{
		ArrayList<String> ids = new ArrayList<String>();
		
		String sql = "INSERT OR REPLACE INTO cardModels (id, ordinal, modelId, name, description, active, qformat, aformat, lformat, " +
						"qedformat, aedformat, questionInAnswer, questionFontFamily, questionFontSize, questionFontColour, questionAlign, " +
						"answerFontFamily, answerFontSize, answerFontColour, answerAlign, lastFontFamily, lastFontSize, lastFontColour, " +
						"editQuestionFontFamily, editQuestionFontSize, editAnswerFontFamily, editAnswerFontSize, allowEmptyAnswer, typeAnswer) " +
						"VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
		SQLiteStatement statement = AnkiDb.database.compileStatement(sql);
		int len = cardModels.length();
		for(int i = 0; i < len; i++)
		{
			try {
				JSONObject cardModel = cardModels.getJSONObject(i);
				
				//id
				String id = cardModel.getString("id");
				statement.bindString(1, id);
				//ordinal
				statement.bindString(2, cardModel.getString("ordinal"));
				//modelId
				statement.bindLong(3, cardModel.getLong("modelId"));
				//name
				statement.bindString(4, cardModel.getString("name"));
				//description
				statement.bindString(5, cardModel.getString("description"));
				//active
				statement.bindLong(6, Utils.booleanToInt(cardModel.getBoolean("active")));
				//qformat
				statement.bindString(7, cardModel.getString("qformat"));
				//aformat
				statement.bindString(8, cardModel.getString("aformat"));
				//lformat
				if(cardModel.isNull("lformat"))
				{
					statement.bindNull(9);
				}
				else
				{
					statement.bindString(9, cardModel.getString("lformat"));
				}
				//qedformat
				if(cardModel.isNull("qedformat"))
				{
					statement.bindNull(10);
				}
				else
				{
					statement.bindString(10, cardModel.getString("qedformat"));
				}
				//aedformat
				if(cardModel.isNull("aedformat"))
				{
					statement.bindNull(11);
				}
				else
				{
					statement.bindString(11, cardModel.getString("aedformat"));
				}
				//questionInAnswer
				statement.bindLong(12, Utils.booleanToInt(cardModel.getBoolean("questionInAnswer")));
				//questionFontFamily
				statement.bindString(13, cardModel.getString("questionFontFamily"));
				//questionFontSize
				statement.bindString(14, cardModel.getString("questionFontSize"));
				//questionFontColour
				statement.bindString(15, cardModel.getString("questionFontColour"));
				//questionAlign
				statement.bindString(16, cardModel.getString("questionAlign"));
				//answerFontFamily
				statement.bindString(17, cardModel.getString("answerFontFamily"));
				//answerFontSize
				statement.bindString(18, cardModel.getString("answerFontSize"));
				//answerFontColour
				statement.bindString(19, cardModel.getString("answerFontColour"));
				//answerAlign
				statement.bindString(20, cardModel.getString("answerAlign"));
				//lastFontFamily
				statement.bindString(21, cardModel.getString("lastFontFamily"));
				//lastFontSize
				statement.bindString(22, cardModel.getString("lastFontSize"));
				//lastFontColour
				statement.bindString(23, cardModel.getString("lastFontColour"));
				//editQuestionFontFamily
				if(cardModel.isNull("editQuestionFontFamily"))
				{
					statement.bindNull(24);
				}
				else
				{
					statement.bindString(24, cardModel.getString("editQuestionFontFamily"));
				}
				//editQuestionFontSize
				if(cardModel.isNull("editQuestionFontSize"))
				{
					statement.bindNull(25);
				}
				else
				{
					statement.bindString(25, cardModel.getString("editQuestionFontSize"));
				}
				//editAnswerFontFamily
				if(cardModel.isNull("editAnswerFontFamily"))
				{
					statement.bindNull(26);
				}
				else
				{
					statement.bindString(26, cardModel.getString("editAnswerFontFamily"));
				}
				//editAnswerFontSize
				if(cardModel.isNull("editAnswerFontSize"))
				{
					statement.bindNull(27);
				}
				else
				{
					statement.bindString(27, cardModel.getString("editAnswerFontSize"));
				}
				//allowEmptyAnswer
				if(cardModel.isNull("allowEmptyAnswer"))
				{
					cardModel.put("allowEmptyAnswer", true);
				}
				statement.bindLong(28, Utils.booleanToInt(cardModel.getBoolean("allowEmptyAnswer")));
				//typeAnswer
				statement.bindString(29, cardModel.getString("typeAnswer"));
				
				statement.execute();
				
				ids.add(id);
			} catch (JSONException e) {
				Log.i(TAG, "JSONException = " + e.getMessage());
			}
		}
		statement.close();
		
		// Delete card models that were not returned by the server
		ArrayList<String> cardModelsIds = AnkiDb.queryColumn(String.class, 
															"SELECT id FROM cardModels WHERE modelId = " + modelId, 
															0);
		int lenCardModelsIds = cardModelsIds.size();
		for(int i = 0; i < lenCardModelsIds; i++)
		{
			String cardModelId = cardModelsIds.get(i);
			if(!ids.contains(cardModelId))
			{
				deck.deleteCardModel(modelId, cardModelId);
			}
		}
	}
	
	/**
	 * Facts
	 */
	
	// TODO: Take into account the updateModified boolean (modified = time.time() or modified = "modified"... what does exactly do that?)
	/**
	 * Anki Desktop -> libanki/anki/sync.py, SyncTools - getFacts
	 */
	private JSONObject getFacts(JSONArray ids)//, boolean updateModified)
	{
		Log.i(TAG, "getFacts");
		
		JSONObject facts = new JSONObject();
		
		JSONArray factsArray = new JSONArray();
		JSONArray fieldsArray = new JSONArray();
		
		for(int i = 0; i < ids.length(); i++)
		{
			try {
				Long id = ids.getLong(i);
				factsArray.put(getFact(id));
				putFields(fieldsArray, id);
			} catch (JSONException e) {
				Log.i(TAG, "JSONException = " + e.getMessage());
			}
		}
		
		try {
			facts.put("facts", factsArray);
			facts.put("fields", fieldsArray);
		} catch (JSONException e) {
			Log.i(TAG, "JSONException = " + e.getMessage());
		}
		
		Log.i(TAG, "facts = ");
		Utils.printJSONObject(facts, false);
		
		return facts;
	}
	
	private JSONArray getFact(Long id)
	{
		JSONArray fact = new JSONArray();
		
		// TODO: Take into account the updateModified boolean (modified = time.time() or modified = "modified"... what does exactly do that?)
		Cursor cursor = AnkiDb.database.rawQuery("SELECT id, modelId, created, modified, tags, spaceUntil, lastCardId FROM facts WHERE id = " + id, null);
		if(cursor.moveToFirst())
		{
			try {
				fact.put(cursor.getLong(0));
				fact.put(cursor.getLong(1));
				fact.put(cursor.getDouble(2));
				fact.put(cursor.getDouble(3));
				fact.put(cursor.getString(4));
				fact.put(cursor.getDouble(5));
				fact.put(cursor.getLong(6));
			} catch (JSONException e) {
				Log.i(TAG, "JSONException = " + e.getMessage());
			}
		}
		cursor.close();
		
		return fact;
	}
	
	private void putFields(JSONArray fields, Long id)
	{
		Cursor cursor = AnkiDb.database.rawQuery("SELECT * FROM fields WHERE factId = " + id, null);
		while(cursor.moveToNext())
		{
			JSONArray field = new JSONArray();
			
			field.put(cursor.getLong(0));
			field.put(cursor.getLong(1));
			field.put(cursor.getLong(2));
			field.put(cursor.getInt(3));
			field.put(cursor.getString(4));
			
			fields.put(field);
		}
		cursor.close();
	}
	
	private void updateFacts(JSONObject factsDict)
	{
		try {
			JSONArray facts = factsDict.getJSONArray("facts");
			int lenFacts = facts.length();
			
			if(lenFacts > 0)
			{
				JSONArray fields = factsDict.getJSONArray("fields");
				int lenFields = fields.length();
				
				// Grab fact ids
				// They will be used later to recalculate the count of facts and to delete them from DB
				ArrayList<String> factIds = new ArrayList<String>();
				for(int i = 0; i < lenFacts; i++)
				{
					factIds.add(facts.getJSONArray(i).getString(0));
				}
				String factIdsString = Utils.ids2str(factIds);
				
				// Recalculate fact count
				deck.factCount += lenFacts - AnkiDb.queryScalar("SELECT COUNT(*) FROM facts WHERE id IN " + factIdsString);
				
				// Update facts
				String sqlFact = "INSERT OR REPLACE INTO facts (id, modelId, created, modified, tags, spaceUntil, lastCardId) VALUES(?,?,?,?,?,?,?)";
				SQLiteStatement statement = AnkiDb.database.compileStatement(sqlFact);
				for(int i = 0; i < lenFacts; i++)
				{
					JSONArray fact = facts.getJSONArray(i);
					
					//id
					statement.bindLong(1, fact.getLong(0));
					//modelId
					statement.bindLong(2, fact.getLong(1));
					//created
					statement.bindDouble(3, fact.getDouble(2));
					//modified
					statement.bindDouble(4, fact.getDouble(3));
					//tags
					statement.bindString(5, fact.getString(4));
					//spaceUntil
					statement.bindDouble(6, fact.getDouble(5));
					//lastCardId
					if(!fact.isNull(6))
					{
						statement.bindLong(7, fact.getLong(6));
					}
					else
					{
						statement.bindNull(7);
					}
					
					statement.execute();
				}
				statement.close();
				
				// Update fields (and delete first the local ones, since ids may have changed)
				AnkiDb.database.execSQL("DELETE FROM fields WHERE factId IN " + factIdsString);
				
				String sqlFields = "INSERT INTO fields (id, factId, fieldModelId, ordinal, value) VALUES(?,?,?,?,?)";
				statement = AnkiDb.database.compileStatement(sqlFields);
				for(int i = 0; i < lenFields; i++)
				{
					JSONArray field = fields.getJSONArray(i);
					
					//id
					statement.bindLong(1, field.getLong(0));
					//factId
					statement.bindLong(2, field.getLong(1));
					//fieldModelId
					statement.bindLong(3, field.getLong(2));
					//ordinal
					statement.bindString(4, field.getString(3));
					//value
					statement.bindString(5, field.getString(4));
					
					statement.execute();
				}
				statement.close();
				
				// Delete inserted facts from deleted
				AnkiDb.database.execSQL("DELETE FROM factsDeleted WHERE factId IN " + factIdsString);
			}
		} catch (JSONException e) {
			Log.i(TAG, "JSONException = " + e.getMessage());
		}
	}
	
	/**
	 * Cards
	 */
	
	/**
	 * Anki Desktop -> libanki/anki/sync.py, SyncTools - getCards
	 */
	private JSONArray getCards(JSONArray ids)
	{
		JSONArray cards = new JSONArray();
		
		//SELECT id, factId, cardModelId, created, modified, tags, ordinal, priority, interval, lastInterval, due, lastDue, factor, 
		//firstAnswered, reps, successive, averageTime, reviewTime, youngEase0, youngEase1, youngEase2, youngEase3, youngEase4, 
		//matureEase0, matureEase1, matureEase2, matureEase3, matureEase4, yesCount, noCount, question, answer, lastFactor, spaceUntil, 
		//type, combinedDue FROM cards WHERE id IN " + ids2str(ids)
		Cursor cursor = AnkiDb.database.rawQuery("SELECT * FROM cards WHERE id IN " + Utils.ids2str(ids), null);
		while(cursor.moveToNext())
		{
			try {
				JSONArray card = new JSONArray();
				
				//id
				card.put(cursor.getLong(0));
				//factId
				card.put(cursor.getLong(1));
				//cardModelId
				card.put(cursor.getLong(2));
				//created
				card.put(cursor.getDouble(3));
				//modified
				card.put(cursor.getDouble(4));
				//tags
				card.put(cursor.getString(5));
				//ordinal
				card.put(cursor.getInt(6));
				//priority
				card.put(cursor.getInt(9));
				//interval
				card.put(cursor.getDouble(10));
				//lastInterval
				card.put(cursor.getDouble(11));
				//due
				card.put(cursor.getDouble(12));
				//lastDue
				card.put(cursor.getDouble(13));
				//factor
				card.put(cursor.getDouble(14));
				//firstAnswered
				card.put(cursor.getDouble(16));
				//reps
				card.put(cursor.getString(17));
				//successive
				card.put(cursor.getInt(18));
				//averageTime
				card.put(cursor.getDouble(19));
				//reviewTime
				card.put(cursor.getDouble(20));
				//youngEase0
				card.put(cursor.getInt(21));
				//youngEase1
				card.put(cursor.getInt(22));
				//youngEase2
				card.put(cursor.getInt(23));
				//youngEase3
				card.put(cursor.getInt(24));
				//youngEase4
				card.put(cursor.getInt(25));
				//matureEase0
				card.put(cursor.getInt(26));
				//matureEase1
				card.put(cursor.getInt(27));
				//matureEase2
				card.put(cursor.getInt(28));
				//matureEase3
				card.put(cursor.getInt(29));
				//matureEase4
				card.put(cursor.getInt(30));
				//yesCount
				card.put(cursor.getInt(31));
				//noCount
				card.put(cursor.getInt(32));
				//question
				card.put(cursor.getString(7));
				//answer
				card.put(cursor.getString(8));
				//lastFactor
				card.put(cursor.getDouble(15));
				//spaceUntil
				card.put(cursor.getDouble(33));
				//type
				card.put(cursor.getInt(36));
				//combinedDue
				card.put(cursor.getInt(37));
				
				cards.put(card);
			} catch (JSONException e) {
				Log.i(TAG, "JSONException = " + e.getMessage());
			}
		}
		cursor.close();
			
		return cards;
	}
	
	private void updateCards(JSONArray cards)
	{
		int len = cards.length();
		if(len > 0)
		{
			ArrayList<String> ids = new ArrayList<String>();
			for(int i = 0; i < len; i++)
			{
				try {
					ids.add(cards.getJSONArray(i).getString(0));
				} catch (JSONException e) {
					Log.i(TAG, "JSONException = " + e.getMessage());
				}
			}
			String idsString = Utils.ids2str(ids);
			
			deck.cardCount += len - AnkiDb.queryScalar("SELECT COUNT(*) FROM cards WHERE id IN " + idsString);
		
			String sql = "INSERT OR REPLACE INTO cards (id, factId, cardModelId, created, modified, tags, ordinal, priority, interval, lastInterval, due, lastDue, " +
							"factor, firstAnswered, reps, successive, averageTime, reviewTime, youngEase0, youngEase1, youngEase2, youngEase3, youngEase4, " +
							"matureEase0, matureEase1, matureEase2, matureEase3, matureEase4, yesCount, noCount, question, answer, lastFactor, spaceUntil, " +
							"type, combinedDue, relativeDelay, isDue) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?, 0, 0)";
			SQLiteStatement statement = AnkiDb.database.compileStatement(sql);
			for(int i = 0; i < len; i++)
			{
				try {
					JSONArray card = cards.getJSONArray(i);
					
					//id
					statement.bindLong(1, card.getLong(0));
					//factId
					statement.bindLong(2, card.getLong(1));
					//cardModelId
					statement.bindLong(3, card.getLong(2));
					//created
					statement.bindDouble(4, card.getDouble(3));
					//modified
					statement.bindDouble(5, card.getDouble(4));
					//tags
					statement.bindString(6, card.getString(5));
					//ordinal
					statement.bindString(7, card.getString(6));
					//priority
					statement.bindString(8, card.getString(7));
					//interval
					statement.bindDouble(9, card.getDouble(8));
					//lastInterval
					statement.bindDouble(10, card.getDouble(9));
					//due
					statement.bindDouble(11, card.getDouble(10));
					//lastDue
					statement.bindDouble(12, card.getDouble(11));
					//factor
					statement.bindDouble(13, card.getDouble(12));
					//firstAnswered
					statement.bindDouble(14, card.getDouble(13));
					//reps
					statement.bindString(15, card.getString(14));
					//successive
					statement.bindString(16, card.getString(15));
					//averageTime
					statement.bindDouble(17, card.getDouble(16));
					//reviewTime
					statement.bindDouble(18, card.getDouble(17));
					//youngEase0
					statement.bindString(19, card.getString(18));
					//youngEase1
					statement.bindString(20, card.getString(19));
					//youngEase2
					statement.bindString(21, card.getString(20));
					//youngEase3
					statement.bindString(22, card.getString(21));
					//youngEase4
					statement.bindString(23, card.getString(22));
					//matureEase0
					statement.bindString(24, card.getString(23));
					//matureEase1
					statement.bindString(25, card.getString(24));
					//matureEase2
					statement.bindString(26, card.getString(25));
					//matureEase3
					statement.bindString(27, card.getString(26));
					//matureEase4
					statement.bindString(28, card.getString(27));
					//yesCount
					statement.bindString(29, card.getString(28));
					//noCount
					statement.bindString(30, card.getString(29));
					//question
					statement.bindString(31, card.getString(30));
					//answer
					statement.bindString(32, card.getString(31));
					//lastFactor
					statement.bindDouble(33, card.getDouble(32));
					//spaceUntil
					statement.bindDouble(34, card.getDouble(33));
					//type
					statement.bindString(35, card.getString(34));
					//combinedDue
					statement.bindString(36, card.getString(35));
					
					statement.execute();
				} catch (JSONException e) {
					Log.i(TAG, "JSONException = " + e.getMessage());
				}
			}
			statement.close();
			
			AnkiDb.database.execSQL("DELETE FROM cardsDeleted WHERE cardId IN " + idsString);
		}
	}
	
	/**
	 * Media
	 */
	
	/**
	 * Anki Desktop -> libanki/anki/sync.py, SyncTools - getMedia
	 */
	private JSONArray getMedia(JSONArray ids)
	{
		JSONArray media = new JSONArray();
		
		Cursor cursor = AnkiDb.database.rawQuery("SELECT id, filename, size, created, originalPath, description FROM media WHERE id IN " + Utils.ids2str(ids), null);
		while(cursor.moveToNext())
		{
			try {
				JSONArray m = new JSONArray();
				
				//id
				m.put(cursor.getLong(0));
				//filename
				m.put(cursor.getString(1));
				//size
				m.put(cursor.getInt(2));
				//created
				m.put(cursor.getDouble(3));
				//originalPath
				m.put(cursor.getString(4));
				//description
				m.put(cursor.getString(5));
				
				media.put(m);
			} catch (JSONException e) {
				Log.i(TAG, "JSONException = " + e.getMessage());
			}
		}
		cursor.close();
		
		return media;
	}
	
	private void deleteMedia(JSONArray ids)
	{
		Log.i(TAG, "deleteMedia");
		
		String idsString = Utils.ids2str(ids);
		
		// Get filenames
		ArrayList<String> files = AnkiDb.queryColumn(String.class, 
													"SELECT filename FROM media WHERE id IN " + idsString, 
													0);
		
		// Note the media to delete (Insert the media to delete into mediaDeleted)
		double now = System.currentTimeMillis() / 1000.0;
		String sqlInsert = "INSERT INTO mediaDeleted SELECT id, " + String.format(ENGLISH_LOCALE, "%f", now) + " FROM media WHERE media.id = ?";
		SQLiteStatement statement = AnkiDb.database.compileStatement(sqlInsert);
		int len = ids.length();
		for(int i = 0; i < len; i++)
		{
			try {
				Log.i(TAG, "Inserting media " + ids.getLong(i) + " into mediaDeleted");
				statement.bindLong(1, ids.getLong(i));
				statement.executeInsert();
			} catch (JSONException e) {
				Log.i(TAG, "JSONException = " + e.getMessage());
			}
		}
		statement.close();
		
		// Delete media
		Log.i(TAG, "Deleting media in = " + idsString);
		AnkiDb.database.execSQL("DELETE FROM media WHERE id IN " + idsString);
	}
	
	private void updateMedia(JSONArray media)
	{
		ArrayList<String> mediaIds = new ArrayList<String>();
		String sql = "INSERT OR REPLACE INTO media (id, filename, size, created, originalPath, description) VALUES(?,?,?,?,?,?)";
		SQLiteStatement statement = AnkiDb.database.compileStatement(sql);
		int len = media.length();
		for(int i = 0; i < len; i++)
		{
			try {
				JSONArray m = media.getJSONArray(i);
				
				// Grab media ids, to delete them later
				String id = m.getString(0);
				mediaIds.add(id);
				
				//id
				statement.bindString(1, id);
				//filename
				statement.bindString(2, m.getString(1));
				//size
				statement.bindString(3, m.getString(2));
				//created
				statement.bindDouble(4, m.getDouble(3));
				//originalPath
				statement.bindString(5, m.getString(4));
				//description
				statement.bindString(6, m.getString(5));
				
				statement.execute();
			} catch (JSONException e) {
				Log.i(TAG, "JSONException = " + e.getMessage());
			}
		}
		statement.close();
		
		AnkiDb.database.execSQL("DELETE FROM mediaDeleted WHERE mediaId IN " + Utils.ids2str(mediaIds));
	}
	
	/**
	 * Deck/Stats/History/Sources
	 */
	
	private JSONObject bundleDeck()
	{
		JSONObject bundledDeck = new JSONObject();
		
		try {
			bundledDeck.put("averageFactor", deck.averageFactor);
			bundledDeck.put("cardCount", deck.cardCount);
			bundledDeck.put("collapseTime", deck.collapseTime);
			bundledDeck.put("created", deck.created);
			//bundledDeck.put("currentModelId", testDeck.currentModelId);
			bundledDeck.put("delay0", deck.delay0);
			bundledDeck.put("delay1", deck.delay1);
			bundledDeck.put("delay2", deck.delay2);
			bundledDeck.put("description", deck.description);
			bundledDeck.put("easyIntervalMax", deck.easyIntervalMax);
			bundledDeck.put("easyIntervalMin", deck.easyIntervalMin);
			bundledDeck.put("factCount", deck.factCount);
			bundledDeck.put("failedCardMax", deck.failedCardMax);
			bundledDeck.put("failedNowCount", deck.failedNowCount);
			bundledDeck.put("failedSoonCount", deck.failedSoonCount);
			bundledDeck.put("hardIntervalMax", deck.hardIntervalMax);
			bundledDeck.put("hardIntervalMin", deck.hardIntervalMin);
			bundledDeck.put("highPriority", deck.highPriority);
			bundledDeck.put("id", deck.id);
			bundledDeck.put("lastLoaded", deck.lastLoaded);
			bundledDeck.put("lastSync", deck.lastSync);
			bundledDeck.put("lowPriority", deck.lowPriority);
			bundledDeck.put("medPriority", deck.medPriority);
			bundledDeck.put("midIntervalMax", deck.midIntervalMax);
			bundledDeck.put("midIntervalMin", deck.midIntervalMin);
			bundledDeck.put("modified", deck.modified);
			bundledDeck.put("newCardModulus", deck.newCardModulus);
			bundledDeck.put("newCount", deck.newCount);
			bundledDeck.put("newCountToday", deck.newCountToday);
			bundledDeck.put("newEarly", deck.newEarly);
			bundledDeck.put("revCount", deck.revCount);
			bundledDeck.put("reviewEarly", deck.reviewEarly);
			bundledDeck.put("suspended", deck.suspended);
			bundledDeck.put("undoEnabled", deck.undoEnabled);
			bundledDeck.put("utcOffset", deck.utcOffset);
			
			//AnkiDroid Deck.java does not have:
			//css, forceMediaDir, lastSessionStart, lastTags, needLock, newCardOrder, newCardSpacing, newCardsPerDay, progressHandlerCalled,
			//progressHandlerEnabled, revCardOrder, sessionRepLimit, sessionStartReps, sessionStartTime, sessionTimeLimit, tmpMediaDir
			
			// Add meta information of the deck (deckVars table)
			JSONArray meta = new JSONArray();
			Cursor cursor = AnkiDb.database.rawQuery("SELECT * FROM deckVars", null);
			while(cursor.moveToNext())
			{
				JSONArray deckVar = new JSONArray();
				deckVar.put(cursor.getString(0));
				deckVar.put(cursor.getString(1));
				meta.put(deckVar);
			}
			cursor.close();
			bundledDeck.put("meta", meta);
			
		} catch (JSONException e) {
			Log.i(TAG, "JSONException = " + e.getMessage());
		}
		
		Log.i(TAG, "Deck =");
		Utils.printJSONObject(bundledDeck, false);
		
		return bundledDeck;
	}
	
	private void updateDeck(JSONObject deckPayload)
	{
		try {
			JSONArray meta = deckPayload.getJSONArray("meta");
			
			// Update meta information
			String sqlMeta = "INSERT OR REPLACE INTO deckVars (key, value) VALUES(?,?)";
			SQLiteStatement statement = AnkiDb.database.compileStatement(sqlMeta);
			int lenMeta = meta.length();
			for(int i = 0; i < lenMeta; i++)
			{
				JSONArray deckVar = meta.getJSONArray(i);
				
				//key
				statement.bindString(1, deckVar.getString(0));
				//value
				statement.bindString(2, deckVar.getString(1));
				
				statement.execute();
			}
			statement.close();
			
			// Update deck
			deck.averageFactor = deckPayload.getDouble("averageFactor");
			deck.cardCount = deckPayload.getInt("cardCount");
			deck.collapseTime = deckPayload.getDouble("collapseTime");
			deck.created = deckPayload.getDouble("created");
			//css
			deck.currentModelId = deckPayload.getLong("currentModelId");
			deck.delay0 = deckPayload.getDouble("delay0");
			deck.delay1 = deckPayload.getDouble("delay1");
			deck.delay2 = deckPayload.getDouble("delay2");
			deck.description = deckPayload.getString("description");
			deck.easyIntervalMax = deckPayload.getDouble("easyIntervalMax");
			deck.easyIntervalMin = deckPayload.getDouble("easyIntervalMin");
			deck.factCount = deckPayload.getInt("factCount");
			deck.failedCardMax = deckPayload.getInt("failedCardMax");
			deck.failedNowCount = deckPayload.getInt("failedNowCount");
			deck.failedSoonCount = deckPayload.getInt("failedSoonCount");
			//forceMediaDir
			deck.hardIntervalMax = deckPayload.getDouble("hardIntervalMax");
			deck.hardIntervalMin = deckPayload.getDouble("hardIntervalMin");
			deck.highPriority = deckPayload.getString("highPriority");
			deck.id = deckPayload.getLong("id");
			//key
			deck.lastLoaded = deckPayload.getDouble("lastLoaded");
			//lastSessionStart
			deck.lastSync = deckPayload.getDouble("modified");
			//lastTags
			deck.lowPriority = deckPayload.getString("lowPriority");
			deck.medPriority = deckPayload.getString("medPriority");
			deck.midIntervalMax = deckPayload.getDouble("midIntervalMax");
			deck.midIntervalMin = deckPayload.getDouble("midIntervalMin");
			deck.modified = deckPayload.getDouble("modified");
			//needLock
			deck.newCardModulus = deckPayload.getInt("newCardModulus");
			//newCardOrder
			//newCardSpacings
			//newCardsPerDay
			deck.newCount = deckPayload.getInt("newCount");
			deck.newCountToday = deckPayload.getInt("newCountToday");
			deck.newEarly = deckPayload.getBoolean("newEarly");
			//revCardOrder
			deck.revCount = deckPayload.getInt("revCount");
			deck.reviewEarly = deckPayload.getBoolean("reviewEarly");
			//sessionRepLimit
			//sessionStartReps
			//sessionStartTime
			//sessionTimeLimit
			deck.suspended = deckPayload.getString("suspended");
			//tmpMediaDir
			deck.undoEnabled = deckPayload.getBoolean("undoEnabled");
			deck.utcOffset = deckPayload.getDouble("utcOffset");
			
			deck.commitToDB();
			
			deck.updateDynamicIndices();
		} catch (JSONException e) {
			Log.i(TAG, "JSONException = " + e.getMessage());
		}
		
	}
	
	private JSONObject bundleStats()
	{
		Log.i(TAG, "bundleStats");
		
		JSONObject bundledStats = new JSONObject();
		
		// Get daily stats since the last day the deck was synchronized
		Date lastDay = new Date(java.lang.Math.max(0, (long)(deck.lastSync - 60*60*24) * 1000));
		Log.i(TAG, "lastDay = " + lastDay.toString());
		ArrayList<Long> ids = AnkiDb.queryColumn(Long.class, 
												"SELECT id FROM stats WHERE type = 1 and day >= \"" + lastDay.toString() + "\"", 
												0);
		
		try {
			Stats stat = new Stats();
			// Put global stats
			bundledStats.put("global", bundleStat(Stats.globalStats(deck)));
			// Put daily stats
			JSONArray dailyStats = new JSONArray();
			int len = ids.size();
			for(int i = 0; i < len; i++)
			{
				// Update stat with the values of the stat with id ids.get(i)
				stat.fromDB(ids.get(i));
				// Bundle this stat and add it to dailyStats
				dailyStats.put(bundleStat(stat));
			}
			bundledStats.put("daily", dailyStats);
		} catch (SQLException e) {
			Log.i(TAG, "SQLException = " + e.getMessage());
		} catch (JSONException e) {
			Log.i(TAG, "JSONException = " + e.getMessage());
		}
		
		Log.i(TAG, "Stats =");
		Utils.printJSONObject(bundledStats, false);
		
		return bundledStats;
	}
	
	private JSONObject bundleStat(Stats stat)
	{
		JSONObject bundledStat = new JSONObject();
		
		try {
			bundledStat.put("type", stat.type);
			bundledStat.put("day", Utils.dateToOrdinal(stat.day));
			bundledStat.put("reps", stat.reps);
			bundledStat.put("averageTime", stat.averageTime);
			bundledStat.put("reviewTime", stat.reviewTime);
			bundledStat.put("distractedTime", stat.distractedTime);
			bundledStat.put("distractedReps", stat.distractedReps);
			bundledStat.put("newEase0", stat.newEase0);
			bundledStat.put("newEase1", stat.newEase1);
			bundledStat.put("newEase2", stat.newEase2);
			bundledStat.put("newEase3", stat.newEase3);
			bundledStat.put("newEase4", stat.newEase4);
			bundledStat.put("youngEase0", stat.youngEase0);
			bundledStat.put("youngEase1", stat.youngEase1);
			bundledStat.put("youngEase2", stat.youngEase2);
			bundledStat.put("youngEase3", stat.youngEase3);
			bundledStat.put("youngEase4", stat.youngEase4);
			bundledStat.put("matureEase0", stat.matureEase0);
			bundledStat.put("matureEase1", stat.matureEase1);
			bundledStat.put("matureEase2", stat.matureEase2);
			bundledStat.put("matureEase3", stat.matureEase3);
			bundledStat.put("matureEase4", stat.matureEase4);
			
		} catch (JSONException e) {
			Log.i(TAG, "JSONException = " + e.getMessage());
		}
		
		return bundledStat;
	}
	
	private void updateStats(JSONObject stats)
	{
		try {
			//Update global stats
			Stats globalStats = Stats.globalStats(deck);
			updateStat(globalStats, stats.getJSONObject("global"));
			
			//Update daily stats
			Stats stat = new Stats();
			JSONArray remoteDailyStats = stats.getJSONArray("daily");
			int len = remoteDailyStats.length();
			for(int i = 0; i < len; i++)
			{
				//Get a specific daily stat
				JSONObject remoteStat = remoteDailyStats.getJSONObject(i);
				Date dailyStatDate = Utils.ordinalToDate(remoteStat.getInt("day"));
				
				//If exists a statistic for this day, get it
				try {
					Long id = AnkiDb.queryScalar("SELECT id FROM stats WHERE type = 1 AND day = \"" + dailyStatDate.toString() + "\"");
					stat.fromDB(id);
				} catch (SQLException e)
				{
					//If it does not exist, create a statistic for this day
					stat.create(Stats.STATS_DAY, dailyStatDate);
				}
				
				//Update daily stat
				updateStat(stat, remoteStat);
			}
		} catch (JSONException e) {
			Log.i(TAG, "JSONException = " + e.getMessage());
		}
	}
	
	private void updateStat(Stats stat, JSONObject remoteStat)
	{
		try {
			stat.averageTime = remoteStat.getDouble("averageTime");
			stat.day = Utils.ordinalToDate(remoteStat.getInt("day"));
			stat.distractedReps = remoteStat.getInt("distractedReps");
			stat.distractedTime = remoteStat.getDouble("distractedTime");
			stat.matureEase0 = remoteStat.getInt("matureEase0");
			stat.matureEase1 = remoteStat.getInt("matureEase1");
			stat.matureEase2 = remoteStat.getInt("matureEase2");
			stat.matureEase3 = remoteStat.getInt("matureEase3");
			stat.matureEase4 = remoteStat.getInt("matureEase4");
			stat.newEase0 = remoteStat.getInt("newEase0");
			stat.newEase1 = remoteStat.getInt("newEase1");
			stat.newEase2 = remoteStat.getInt("newEase2");
			stat.newEase3 = remoteStat.getInt("newEase3");
			stat.newEase4 = remoteStat.getInt("newEase4");
			stat.reps = remoteStat.getInt("reps");
			stat.reviewTime = remoteStat.getDouble("reviewTime");
			stat.type = remoteStat.getInt("type");
			stat.youngEase0 = remoteStat.getInt("youngEase0");
			stat.youngEase1 = remoteStat.getInt("youngEase1");
			stat.youngEase2 = remoteStat.getInt("youngEase2");
			stat.youngEase3 = remoteStat.getInt("youngEase3");
			stat.youngEase4 = remoteStat.getInt("youngEase4");
			
			stat.toDB();
		} catch (JSONException e) {
			Log.i(TAG, "JSONException = " + e.getMessage());
		}
	}
	
	private JSONArray bundleHistory()
	{
		JSONArray bundledHistory = new JSONArray();
		Cursor cursor = AnkiDb.database.rawQuery("SELECT cardId, time, lastInterval, nextInterval, ease, delay, lastFactor, nextFactor, reps, thinkingTime, yesCount, noCount FROM reviewHistory WHERE time > " + String.format(ENGLISH_LOCALE, "%f", deck.lastSync), null);
		while(cursor.moveToNext())
		{
			try {
				JSONArray review = new JSONArray();
				
				//cardId
				review.put(cursor.getLong(0));
				//time
				review.put(cursor.getDouble(1));
				//lastInterval
				review.put(cursor.getDouble(2));
				//nextInterval
				review.put(cursor.getDouble(3));
				//ease
				review.put(cursor.getInt(4));
				//delay
				review.put(cursor.getDouble(5));
				//lastFactor
				review.put(cursor.getDouble(6));
				//nextFactor
				review.put(cursor.getDouble(7));
				//reps
				review.put(cursor.getDouble(8));
				//thinkingTime
				review.put(cursor.getDouble(9));
				//yesCount
				review.put(cursor.getDouble(10));
				//noCount
				review.put(cursor.getDouble(11));
				
				bundledHistory.put(review);
			} catch (JSONException e) {
				Log.i(TAG, "JSONException = " + e.getMessage());
			}
		}
		cursor.close();
		
		Log.i(TAG, "Last sync = " + String.format(ENGLISH_LOCALE, "%f", deck.lastSync));
		Log.i(TAG, "Bundled history = " + bundledHistory.toString());
		return bundledHistory;
	}
	
	private void updateHistory(JSONArray history)
	{
		String sql = "INSERT OR IGNORE INTO reviewHistory (cardId, time, lastInterval, nextInterval, ease, delay, lastFactor, nextFactor, reps, thinkingTime, yesCount, noCount) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)";
		SQLiteStatement statement = AnkiDb.database.compileStatement(sql);
		int len = history.length();
		for(int i = 0; i < len; i++)
		{
			try {
				JSONArray h = history.getJSONArray(i);
				
				//cardId
				statement.bindLong(1, h.getLong(0));
				//time
				statement.bindDouble(2, h.getDouble(1));
				//lastInterval
				statement.bindDouble(3, h.getDouble(2));
				//nextInterval
				statement.bindDouble(4, h.getDouble(3));
				//ease
				statement.bindString(5, h.getString(4));
				//delay
				statement.bindDouble(6, h.getDouble(5));
				//lastFactor
				statement.bindDouble(7, h.getDouble(6));
				//nextFactor
				statement.bindDouble(8, h.getDouble(7));
				//reps
				statement.bindDouble(9, h.getDouble(8));
				//thinkingTime
				statement.bindDouble(10, h.getDouble(9));
				//yesCount
				statement.bindDouble(11, h.getDouble(10));
				//noCount
				statement.bindDouble(12, h.getDouble(11));
				
				statement.execute();
			} catch (JSONException e) {
				Log.i(TAG, "JSONException = " + e.getMessage());
			}
		}
		statement.close();
	}
	
	private JSONArray bundleSources()
	{
		JSONArray bundledSources = new JSONArray();
		
		Cursor cursor = AnkiDb.database.rawQuery("SELECT * FROM sources", null);
		while(cursor.moveToNext())
		{
			try {
				JSONArray source = new JSONArray();
				
				//id
				source.put(cursor.getLong(0));
				//name
				source.put(cursor.getString(1));
				//created
				source.put(cursor.getDouble(2));
				//lastSync
				source.put(cursor.getDouble(3));
				//syncPeriod
				source.put(cursor.getInt(4));
				
				bundledSources.put(source);
			} catch (JSONException e) {
				Log.i(TAG, "JSONException = " + e.getMessage());
			}
		}
		cursor.close();
		
		Log.i(TAG, "Bundled sources = " + bundledSources);
		return bundledSources;
	}
	
	private void updateSources(JSONArray sources)
	{
		String sql = "INSERT OR REPLACE INTO sources VALUES(?,?,?,?,?)";
		SQLiteStatement statement = AnkiDb.database.compileStatement(sql);
		int len = sources.length();
		for(int i = 0; i < len; i++)
		{
			try {
				JSONArray source = sources.getJSONArray(i);
				statement.bindLong(1, source.getLong(0));
				statement.bindString(2, source.getString(1));
				statement.bindDouble(3, source.getDouble(2));
				statement.bindDouble(4, source.getDouble(3));
				statement.bindString(5, source.getString(4));
				statement.execute();
			} catch (JSONException e) {
				Log.i(TAG, "JSONException = " + e.getMessage());
			}
		}
		statement.close();
	}
	
    /**
     * Full sync
     */

	/**
	 * Anki Desktop -> libanki/anki/sync.py, SyncTools - needFullSync
	 * @param sums
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public boolean needFullSync(JSONArray sums)
	{
		if(deck.lastSync <= 0)
		{
			Log.i(TAG, "deck.lastSync <= 0");
			return true;
		}
		
		for(int i = 0; i < sums.length(); i++)
		{
			try {
				JSONObject summary = sums.getJSONObject(i);
				Iterator keys = summary.keys();
				while(keys.hasNext())
				{
					String key = (String)keys.next();
					JSONArray l = (JSONArray)summary.get(key);
					Log.i(TAG, "Key " + key + ", length = " + l.length());
					if(l.length() > 500)
					{
						return true;
					}
				}
			} catch (JSONException e) {
				Log.i(TAG, "JSONException = " + e.getMessage());
			}
			
		}
		
		Log.i(TAG, "Count reviewHistory = " + AnkiDb.queryScalar("SELECT count() FROM reviewHistory WHERE time > " + deck.lastSync));
		if(AnkiDb.queryScalar("SELECT count() FROM reviewHistory WHERE time > " + deck.lastSync) > 500)
		{
			return true;
		}
		Log.i(TAG, "lastSync = " + deck.lastSync);
		Date lastDay = new Date(java.lang.Math.max(0, (long)(deck.lastSync - 60*60*24) * 1000));
		
		Log.i(TAG, "lastDay = " + lastDay.toString() + ", lastDayInMillis = " + lastDay.getTime());
		
		Log.i(TAG, "Count stats = " + AnkiDb.queryScalar("SELECT count() FROM stats WHERE day >= \"" + lastDay.toString() + "\""));
		if(AnkiDb.queryScalar("SELECT count() FROM stats WHERE day >= \"" + lastDay.toString() + "\"") > 100)
		{
			return true;
		}
		
		return false;
	}
	
	public String prepareFullSync()
	{
		deck.lastSync = System.currentTimeMillis() / 1000.0;
		deck.commitToDB();
		deck.closeDeck();
		
		if(localTime > remoteTime)
		{
			return "fromLocal";
		}
		else
		{
			return "fromServer";
		}
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
				Log.i(TAG, "Response code = " + responseCode);
				//throw new Exception(String.format("Received the response code %d from the URL %s", responseCode, url));
			} else
			{
				Log.i(TAG, "Response code = 200");
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
			Log.i(TAG, "MalformedURLException = " + e.getMessage());
		} catch (IOException e) {
			Log.i(TAG, "IOException = " + e.getMessage());
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
			Utils.writeToFile(new InflaterInputStream(content), deckPath);
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
