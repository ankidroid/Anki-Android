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
	
	public void setServer(AnkiDroidProxy server) 
	{
		this.server = server;
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

	/**
	 * Anki Desktop -> libanki/anki/sync.py, SyncTools - genPayload
	 */
	public JSONObject genPayload(JSONArray summaries)
	{
		//Log.i(TAG, "genPayload");
		//Ensure global stats are available (queue may not be built)
		//preSyncRefresh();
		
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
				//deleteObjsFromKey((JSONArray)diff.get(3), key);
			} catch (JSONException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
		// If the last modified deck was the local one, handle the remainder
		if(localTime > remoteTime)
		{
			/*
			try {
				payload.put("deck", bundleDeck());
				payload.put("stats", bundleStats());
				payload.put("history",bundleHistory());
				payload.put("sources", bundleSources());
				deck.lastSync = deck.modified;
			} catch (JSONException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}*/
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
			//return getMedia(ids);
		}
		
		return null;
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
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		cursor.close();
			
		return cards;
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
