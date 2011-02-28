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

import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteStatement;
import android.util.Log;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.FileEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URLEncoder;
import java.sql.Date;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

public class SyncClient {

    private enum Keys {
        models, facts, cards, media
    };

    /**
     * Constants used on the multipart message
     */
    private static final String MIME_BOUNDARY = "Anki-sync-boundary";
    private static final String END = "\r\n";
    private static final String TWO_HYPHENS = "--";

    private Deck mDeck;
    private AnkiDroidProxy mServer;
    private double mLocalTime;
    private double mRemoteTime;


    public SyncClient(Deck deck) {
        mDeck = deck;
        mServer = null;
        mLocalTime = 0;
        mRemoteTime = 0;
    }


    public AnkiDroidProxy getServer() {
        return mServer;
    }


    public void setServer(AnkiDroidProxy server) {
        mServer = server;
    }

    public double getRemoteTime() {
        return mRemoteTime;
    }

    public void setRemoteTime(double time) {
        mRemoteTime = time;
    }

    public double getLocalTime() {
        return mLocalTime;
    }

    public void setLocalTime(double time) {
        mLocalTime = time;
    }

    public void setDeck(Deck deck) {
        mDeck = deck;
    }


    /**
     * Anki Desktop -> libanki/anki/sync.py, prepareSync
     * 
     * @return
     */
    public boolean prepareSync(double timediff) {
        Log.i(AnkiDroidApp.TAG, "prepareSync = " + String.format(Utils.ENGLISH_LOCALE, "%f", mDeck.getLastSync()));

        mLocalTime = mDeck.getModified();
        mRemoteTime = mServer.modified();

        Log.i(AnkiDroidApp.TAG, "localTime = " + mLocalTime);
        Log.i(AnkiDroidApp.TAG, "remoteTime = " + mRemoteTime);

        if (mLocalTime == mRemoteTime) {
            return false;
        }

        double l = mDeck.getLastSync();
        Log.i(AnkiDroidApp.TAG, "lastSync local = " + l);
        double r = mServer.lastSync();
        Log.i(AnkiDroidApp.TAG, "lastSync remote = " + r);

        // Set lastSync to the lower of the two sides, and account for slow clocks & assume it took up to 10 seconds
        // for the reply to arrive
        mDeck.setLastSync(Math.min(l, r) - timediff - 10);

        return true;
    }


    public JSONArray summaries() throws JSONException {

        JSONArray summaries = new JSONArray();
        JSONObject sum = summary(mDeck.getLastSync());
        if (sum == null) {
            return null;
        }
        summaries.put(sum);
        sum = mServer.summary(mDeck.getLastSync());
        summaries.put(sum);
        if (sum == null) {
            return null;
        }

        return summaries;
    }


    /**
     * Anki Desktop -> libanki/anki/sync.py, SyncTools - summary
     * 
     * @param lastSync
     * @throws JSONException 
     */
    public JSONObject summary(double lastSync) throws JSONException {
        Log.i(AnkiDroidApp.TAG, "Summary Local");
        mDeck.setLastSync(lastSync);
        mDeck.commitToDB();

        AnkiDb ankiDB = AnkiDatabaseManager.getDatabase(mDeck.getDeckPath());

        String lastSyncString = String.format(Utils.ENGLISH_LOCALE, "%f", lastSync);
        // Cards
        JSONArray cards = cursorToJSONArray(ankiDB.getDatabase().rawQuery(
                "SELECT id, modified FROM cards WHERE modified > " + lastSyncString, null));
        // Cards - delcards
        JSONArray delcards = cursorToJSONArray(ankiDB.getDatabase().rawQuery(
                "SELECT cardId, deletedTime FROM cardsDeleted WHERE deletedTime > " + lastSyncString, null));

        // Facts
        JSONArray facts = cursorToJSONArray(ankiDB.getDatabase().rawQuery(
                "SELECT id, modified FROM facts WHERE modified > " + lastSyncString, null));
        // Facts - delfacts
        JSONArray delfacts = cursorToJSONArray(ankiDB.getDatabase().rawQuery(
                "SELECT factId, deletedTime FROM factsDeleted WHERE deletedTime > " + lastSyncString, null));

        // Models
        JSONArray models = cursorToJSONArray(ankiDB.getDatabase().rawQuery(
                "SELECT id, modified FROM models WHERE modified > " + lastSyncString, null));
        // Models - delmodels
        JSONArray delmodels = cursorToJSONArray(ankiDB.getDatabase().rawQuery(
                "SELECT modelId, deletedTime FROM modelsDeleted WHERE deletedTime > " + lastSyncString, null));

        // Media
        JSONArray media = cursorToJSONArray(ankiDB.getDatabase().rawQuery(
                "SELECT id, created FROM media WHERE created > " + lastSyncString, null));
        // Media - delmedia
        JSONArray delmedia = cursorToJSONArray(ankiDB.getDatabase().rawQuery(
                "SELECT mediaId, deletedTime FROM mediaDeleted WHERE deletedTime > " + lastSyncString, null));

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
            Log.e(AnkiDroidApp.TAG, "SyncClient.summary - JSONException = " + e.getMessage());
            return null;
        }

        Log.i(AnkiDroidApp.TAG, "Summary Local = ");
        Utils.printJSONObject(summary, false);

        return summary;
    }


    private JSONArray cursorToJSONArray(Cursor cursor) throws JSONException {
        JSONArray jsonArray = new JSONArray();
        while (cursor.moveToNext()) {
            JSONArray element = new JSONArray();

            element.put(cursor.getLong(0));
            element.put(cursor.getDouble(1));
            jsonArray.put(element);
        }

        cursor.close();

        return jsonArray;
    }


    /**
     * Anki Desktop -> libanki/anki/sync.py, SyncTools - genPayload
     * @throws JSONException 
     */
    public JSONObject genPayload(JSONArray summaries) throws JSONException {
        // Log.i(AnkiDroidApp.TAG, "genPayload");
        // Ensure global stats are available (queue may not be built)
        preSyncRefresh();

        JSONObject payload = new JSONObject();

        Keys[] keys = Keys.values();

        for (int i = 0; i < keys.length; i++) {
            // Log.i(AnkiDroidApp.TAG, "Key " + keys[i].name());
            String key = keys[i].name();
            // Handle models, facts, cards and media
            JSONArray diff = diffSummary((JSONObject) summaries.get(0), (JSONObject) summaries.get(1), key);
            payload.put("added-" + key, getObjsFromKey((JSONArray) diff.get(0), key));
            payload.put("deleted-" + key, diff.get(1));
            payload.put("missing-" + key, diff.get(2));
            deleteObjsFromKey((JSONArray) diff.get(3), key);
        }

        // If the last modified deck was the local one, handle the remainder
        if (mLocalTime > mRemoteTime) {

            payload.put("stats", bundleStats());
            payload.put("history", bundleHistory());
            payload.put("sources", bundleSources());
            // Finally, set new lastSync and bundle the deck info
            payload.put("deck", bundleDeck());
        }

        Log.i(AnkiDroidApp.TAG, "Payload =");
        Utils.printJSONObject(payload, true);

        return payload;
    }


    /**
     * Anki Desktop -> libanki/anki/sync.py, SyncTools - payloadChanges
     * @throws JSONException 
     */
    private Object[] payloadChanges(JSONObject payload) throws JSONException {
        Object[] h = new Object[8];

        h[0] = payload.getJSONObject("added-facts").getJSONArray("facts").length();
        h[1] = payload.getJSONArray("missing-facts").length();
        h[2] = payload.getJSONArray("added-cards").length();
        h[3] = payload.getJSONArray("missing-cards").length();
        h[4] = payload.getJSONArray("added-models").length();
        h[5] = payload.getJSONArray("missing-models").length();

        if (mLocalTime > mRemoteTime) {
            h[6] = "all";
            h[7] = 0;
        } else {
            h[6] = 0;
            h[7] = "all";
        }
        return h;
    }


    public String payloadChangeReport(JSONObject payload) throws JSONException {
        return AnkiDroidApp.getAppResources().getString(R.string.change_report_format, payloadChanges(payload));
    }


    public void applyPayloadReply(JSONObject payloadReply) throws JSONException {
        Log.i(AnkiDroidApp.TAG, "applyPayloadReply");
        Keys[] keys = Keys.values();

        for (int i = 0; i < keys.length; i++) {
            String key = keys[i].name();
            updateObjsFromKey(payloadReply, key);
            // JSONObject crasher = null;
            // crasher.get("nothing");
        }

        if (!payloadReply.isNull("deck")) {
            updateDeck(payloadReply.getJSONObject("deck"));
            updateStats(payloadReply.getJSONObject("stats"));
            updateHistory(payloadReply.getJSONArray("history"));
            if (!payloadReply.isNull("sources")) {
                updateSources(payloadReply.getJSONArray("sources"));
            }
            mDeck.commitToDB();
        }

        mDeck.commitToDB();

        // Rebuild priorities on client

        // Get card ids
        JSONArray cards = payloadReply.getJSONArray("added-cards");
        int len = cards.length();
        long[] cardIds = new long[len];
        for (int i = 0; i < len; i++) {
            cardIds[i] = cards.getJSONArray(i).getLong(0);
        }
        mDeck.updateCardTags(cardIds);
        rebuildPriorities(cardIds);

        long missingFacts = missingFacts();
        if (missingFacts != 0l) {
            Log.e(AnkiDroidApp.TAG, "Facts missing after sync (" + missingFacts + " facts)!");
        }
        assert missingFacts == 0l;
    }

    private long missingFacts() {
        try {
            return mDeck.getDB().queryScalar("SELECT count() FROM cards WHERE factId NOT IN (SELECT id FROM facts)");
        } catch (Exception e) {
            return 0;
        }
    }


    /**
     * Anki Desktop -> libanki/anki/sync.py, SyncTools - preSyncRefresh
     */
    private void preSyncRefresh() {
        Stats.globalStats(mDeck);
    }


    private void rebuildPriorities(long[] cardIds) {
        rebuildPriorities(cardIds, null);
    }
    private void rebuildPriorities(long[] cardIds, String[] suspend) {
        //try {
            mDeck.updateAllPriorities(true, false);
            mDeck.updatePriorities(cardIds, suspend, false);
        //} catch (SQLException e) {
        //    Log.e(TAG, "SQLException e = " + e.getMessage());
        //}
    }


    /**
     * Anki Desktop -> libanki/anki/sync.py, SyncTools - diffSummary
     * @throws JSONException 
     */
    private JSONArray diffSummary(JSONObject summaryLocal, JSONObject summaryServer, String key) throws JSONException {
        JSONArray locallyEdited = new JSONArray();
        JSONArray locallyDeleted = new JSONArray();
        JSONArray remotelyEdited = new JSONArray();
        JSONArray remotelyDeleted = new JSONArray();

        Log.i(AnkiDroidApp.TAG, "\ndiffSummary - Key = " + key);
        Log.i(AnkiDroidApp.TAG, "\nSummary local = ");
        Utils.printJSONObject(summaryLocal, false);
        Log.i(AnkiDroidApp.TAG, "\nSummary server = ");
        Utils.printJSONObject(summaryServer, false);

        // Hash of all modified ids
        HashSet<Long> ids = new HashSet<Long>();

        // Build a hash (id item key, modification time) of the modifications on server (null -> deleted)
        HashMap<Long, Double> remoteMod = new HashMap<Long, Double>();
        putExistingItems(ids, remoteMod, summaryServer.getJSONArray(key));
        HashMap<Long, Double> rdeletedIds = putDeletedItems(ids, remoteMod, summaryServer.getJSONArray("del" + key));

        // Build a hash (id item, modification time) of the modifications on client (null -> deleted)
        HashMap<Long, Double> localMod = new HashMap<Long, Double>();
        putExistingItems(ids, localMod, summaryLocal.getJSONArray(key));
        HashMap<Long, Double> ldeletedIds = putDeletedItems(ids, localMod, summaryLocal.getJSONArray("del" + key));

        Iterator<Long> idsIterator = ids.iterator();
        while (idsIterator.hasNext()) {
            Long id = idsIterator.next();
            Double localModTime = localMod.get(id);
            Double remoteModTime = remoteMod.get(id);

            Log.i(AnkiDroidApp.TAG, "\nid = " + id + ", localModTime = " + localModTime + ", remoteModTime = " + remoteModTime);
            // Changed/Existing on both sides
            if (localModTime != null && remoteModTime != null) {
                Log.i(AnkiDroidApp.TAG, "localModTime not null AND remoteModTime not null");
                if (localModTime < remoteModTime) {
                    Log.i(AnkiDroidApp.TAG, "Remotely edited");
                    remotelyEdited.put(id);
                } else if (localModTime > remoteModTime) {
                    Log.i(AnkiDroidApp.TAG, "Locally edited");
                    locallyEdited.put(id);
                }
            }
            // If it's missing on server or newer here, sync
            else if (localModTime != null && remoteModTime == null) {
                Log.i(AnkiDroidApp.TAG, "localModTime not null AND remoteModTime null");
                if (!rdeletedIds.containsKey(id) || rdeletedIds.get(id) < localModTime) {
                    Log.i(AnkiDroidApp.TAG, "Locally edited");
                    locallyEdited.put(id);
                } else {
                    Log.i(AnkiDroidApp.TAG, "Remotely deleted");
                    remotelyDeleted.put(id);
                }
            }
            // If it's missing locally or newer there, sync
            else if (remoteModTime != null && localModTime == null) {
                Log.i(AnkiDroidApp.TAG, "remoteModTime not null AND localModTime null");
                if (!ldeletedIds.containsKey(id) || ldeletedIds.get(id) < remoteModTime) {
                    Log.i(AnkiDroidApp.TAG, "Remotely edited");
                    remotelyEdited.put(id);
                } else {
                    Log.i(AnkiDroidApp.TAG, "Locally deleted");
                    locallyDeleted.put(id);
                }
            }
            // Deleted or not modified in both sides
            else {
                Log.i(AnkiDroidApp.TAG, "localModTime null AND remoteModTime null");
                if (ldeletedIds.containsKey(id) && !rdeletedIds.containsKey(id)) {
                    Log.i(AnkiDroidApp.TAG, "Locally deleted");
                    locallyDeleted.put(id);
                } else if (rdeletedIds.containsKey(id) && !ldeletedIds.containsKey(id)) {
                    Log.i(AnkiDroidApp.TAG, "Remotely deleted");
                    remotelyDeleted.put(id);
                }
            }
        }

        JSONArray diff = new JSONArray();
        diff.put(locallyEdited);
        diff.put(locallyDeleted);
        diff.put(remotelyEdited);
        diff.put(remotelyDeleted);

        return diff;
    }


    private void putExistingItems(HashSet<Long> ids, HashMap<Long, Double> dictExistingItems, JSONArray existingItems) throws JSONException {
        int nbItems = existingItems.length();
        for (int i = 0; i < nbItems; i++) {
            JSONArray itemModified = existingItems.getJSONArray(i);
            Long idItem = itemModified.getLong(0);
            Double modTimeItem = itemModified.getDouble(1);
            dictExistingItems.put(idItem, modTimeItem);
            ids.add(idItem);
        }
    }


    private HashMap<Long, Double> putDeletedItems(HashSet<Long> ids, HashMap<Long, Double> dictDeletedItems,
            JSONArray deletedItems) throws JSONException {
        HashMap<Long, Double> deletedIds = new HashMap<Long, Double>();
        int nbItems = deletedItems.length();
        for (int i = 0; i < nbItems; i++) {
            JSONArray itemModified = deletedItems.getJSONArray(i);
            Long idItem = itemModified.getLong(0);
            Double modTimeItem = itemModified.getDouble(1);
            dictDeletedItems.put(idItem, null);
            deletedIds.put(idItem, modTimeItem);
            ids.add(idItem);
        }

        return deletedIds;
    }


    private Object getObjsFromKey(JSONArray ids, String key) throws JSONException {
        if ("models".equalsIgnoreCase(key)) {
            return getModels(ids);
        } else if ("facts".equalsIgnoreCase(key)) {
            return getFacts(ids);
        } else if ("cards".equalsIgnoreCase(key)) {
            return getCards(ids);
        } else if ("media".equalsIgnoreCase(key)) {
            return getMedia(ids);
        }

        return null;
    }


    private void deleteObjsFromKey(JSONArray ids, String key) throws JSONException {
        if ("models".equalsIgnoreCase(key)) {
            deleteModels(ids);
        } else if ("facts".equalsIgnoreCase(key)) {
            mDeck.deleteFacts(Utils.jsonArrayToListString(ids));
        } else if ("cards".equalsIgnoreCase(key)) {
            mDeck.deleteCards(Utils.jsonArrayToListString(ids));
        } else if ("media".equalsIgnoreCase(key)) {
            deleteMedia(ids);
        }
    }


    private void updateObjsFromKey(JSONObject payloadReply, String key) throws JSONException {
        if ("models".equalsIgnoreCase(key)) {
            Log.i(AnkiDroidApp.TAG, "updateModels");
            updateModels(payloadReply.getJSONArray("added-models"));
        } else if ("facts".equalsIgnoreCase(key)) {
            Log.i(AnkiDroidApp.TAG, "updateFacts");
            updateFacts(payloadReply.getJSONObject("added-facts"));
        } else if ("cards".equalsIgnoreCase(key)) {
            Log.i(AnkiDroidApp.TAG, "updateCards");
            updateCards(payloadReply.getJSONArray("added-cards"));
        } else if ("media".equalsIgnoreCase(key)) {
            Log.i(AnkiDroidApp.TAG, "updateMedia");
            updateMedia(payloadReply.getJSONArray("added-media"));
        }
    }


    /**
     * Models
     */

    // TODO: Include the case with updateModified
    /**
     * Anki Desktop -> libanki/anki/sync.py, SyncTools - getModels
     * 
     * @param ids
     * @return
     * @throws JSONException 
     */
    private JSONArray getModels(JSONArray ids) throws JSONException// , boolean updateModified)
    {
        JSONArray models = new JSONArray();

        int nbIds = ids.length();
        for (int i = 0; i < nbIds; i++) {
            models.put(bundleModel(ids.getLong(i)));
        }

        return models;
    }


    /**
     * Anki Desktop -> libanki/anki/sync.py, SyncTools - bundleModel
     * 
     * @param id
     * @return
     * @throws JSONException 
     */
    private JSONObject bundleModel(Long id) throws JSONException// , boolean updateModified
    {
        JSONObject model = new JSONObject();
        Cursor cursor = AnkiDatabaseManager.getDatabase(mDeck.getDeckPath()).getDatabase().rawQuery(
                "SELECT * FROM models WHERE id = " + id, null);
        if (cursor.moveToFirst()) {
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
        }
        cursor.close();

        Log.i(AnkiDroidApp.TAG, "Model = ");
        Utils.printJSONObject(model, false);

        return model;
    }


    /**
     * Anki Desktop -> libanki/anki/sync.py, SyncTools - bundleFieldModel
     * 
     * @param id
     * @return
     * @throws JSONException 
     */
    private JSONArray bundleFieldModels(Long id) throws JSONException {
        JSONArray fieldModels = new JSONArray();

        Cursor cursor = AnkiDatabaseManager.getDatabase(mDeck.getDeckPath()).getDatabase().rawQuery(
                "SELECT * FROM fieldModels WHERE modelId = " + id, null);
        while (cursor.moveToNext()) {
            JSONObject fieldModel = new JSONObject();

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

            fieldModels.put(fieldModel);
        }
        cursor.close();

        return fieldModels;
    }


    private JSONArray bundleCardModels(Long id) throws JSONException {
        JSONArray cardModels = new JSONArray();

        Cursor cursor = AnkiDatabaseManager.getDatabase(mDeck.getDeckPath()).getDatabase().rawQuery(
                "SELECT * FROM cardModels WHERE modelId = " + id, null);
        while (cursor.moveToNext()) {
            JSONObject cardModel = new JSONObject();

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

            cardModels.put(cardModel);
        }
        cursor.close();

        return cardModels;
    }


    private void deleteModels(JSONArray ids) throws JSONException {
        Log.i(AnkiDroidApp.TAG, "deleteModels");
        int len = ids.length();
        for (int i = 0; i < len; i++) {
            mDeck.deleteModel(ids.getString(i));
        }
    }


    private void updateModels(JSONArray models) throws JSONException {
        ArrayList<String> insertedModelsIds = new ArrayList<String>();
        AnkiDb ankiDB = AnkiDatabaseManager.getDatabase(mDeck.getDeckPath());

        String sql = "INSERT OR REPLACE INTO models"
                    + " (id, deckId, created, modified, tags, name, description, features, spacing, initialSpacing, source)"
                    + " VALUES(?,?,?,?,?,?,?,?,?,?,?)";
        SQLiteStatement statement = ankiDB.getDatabase().compileStatement(sql);
        int len = models.length();
        for (int i = 0; i < len; i++) {
            JSONObject model = models.getJSONObject(i);

            // id
            String id = model.getString("id");
            statement.bindString(1, id);
            // deckId
            statement.bindLong(2, model.getLong("deckId"));
            // created
            statement.bindDouble(3, model.getDouble("created"));
            // modified
            statement.bindDouble(4, model.getDouble("modified"));
            // tags
            statement.bindString(5, model.getString("tags"));
            // name
            statement.bindString(6, model.getString("name"));
            // description
            statement.bindString(7, model.getString("name"));
            // features
            statement.bindString(8, model.getString("features"));
            // spacing
            statement.bindDouble(9, model.getDouble("spacing"));
            // initialSpacing
            statement.bindDouble(10, model.getDouble("initialSpacing"));
            // source
            statement.bindLong(11, model.getLong("source"));

            statement.execute();

            insertedModelsIds.add(id);

            mergeFieldModels(id, model.getJSONArray("fieldModels"));
            mergeCardModels(id, model.getJSONArray("cardModels"));
        }
        statement.close();

        // Delete inserted models from modelsDeleted
        ankiDB.getDatabase().execSQL("DELETE FROM modelsDeleted WHERE modelId IN " + Utils.ids2str(insertedModelsIds));
    }


    private void mergeFieldModels(String modelId, JSONArray fieldModels) throws JSONException {
        ArrayList<String> ids = new ArrayList<String>();
        
        String sql = "INSERT OR REPLACE INTO fieldModels VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        SQLiteStatement statement = mDeck.getDB().getDatabase().compileStatement(sql);
        int len = fieldModels.length();
        for (int i = 0; i < len; i++) {
            JSONObject fieldModel = fieldModels.getJSONObject(i);

            // id
            String id = fieldModel.getString("id");
            statement.bindString(1, id);
            // ordinal
            statement.bindString(2, fieldModel.getString("ordinal"));
            // modelId
            statement.bindLong(3, fieldModel.getLong("modelId"));
            // name
            statement.bindString(4, fieldModel.getString("name"));
            // description
            statement.bindString(5, fieldModel.getString("description"));
            // features
            statement.bindString(6, fieldModel.getString("features"));
            // required
            statement.bindLong(7, Utils.booleanToInt(fieldModel.getBoolean("required")));
            // unique
            statement.bindLong(8, Utils.booleanToInt(fieldModel.getBoolean("unique")));
            // numeric
            statement.bindLong(9, Utils.booleanToInt(fieldModel.getBoolean("numeric")));
            // quizFontFamily
            if (fieldModel.isNull("quizFontFamily")) {
                statement.bindNull(10);
            } else {
                statement.bindString(10, fieldModel.getString("quizFontFamily"));
            }
            // quizFontSize
            if (fieldModel.isNull("quizFontSize")) {
                statement.bindNull(11);
            } else {
                statement.bindString(11, fieldModel.getString("quizFontSize"));
            }
            // quizFontColour
            if (fieldModel.isNull("quizFontColour")) {
                statement.bindNull(12);
            } else {
                statement.bindString(12, fieldModel.getString("quizFontColour"));
            }
            // editFontFamily
            if (fieldModel.isNull("editFontFamily")) {
                statement.bindNull(13);
            } else {
                statement.bindString(13, fieldModel.getString("editFontFamily"));
            }
            // editFontSize
            statement.bindString(14, fieldModel.getString("editFontSize"));

            statement.execute();

            ids.add(id);
        }
        statement.close();

        // Delete field models that were not returned by the server
        ArrayList<String> fieldModelsIds = mDeck.getDB().queryColumn(String.class,
                "SELECT id FROM fieldModels WHERE modelId = " + modelId, 0);
        if (fieldModelsIds != null) {
            for (String fieldModelId : fieldModelsIds) {
                if (!ids.contains(fieldModelId)) {
                    mDeck.deleteFieldModel(modelId, fieldModelId);
                }
            }
        }
    }


    private void mergeCardModels(String modelId, JSONArray cardModels) throws JSONException {
        ArrayList<String> ids = new ArrayList<String>();

        String sql = "INSERT OR REPLACE INTO cardModels (id, ordinal, modelId, name, description, active, qformat, "
                + "aformat, lformat, qedformat, aedformat, questionInAnswer, questionFontFamily, questionFontSize, "
                + "questionFontColour, questionAlign, answerFontFamily, answerFontSize, answerFontColour, answerAlign, "
                + "lastFontFamily, lastFontSize, lastFontColour, editQuestionFontFamily, editQuestionFontSize, "
                + "editAnswerFontFamily, editAnswerFontSize, allowEmptyAnswer, typeAnswer) "
                + "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        SQLiteStatement statement = mDeck.getDB().getDatabase().compileStatement(sql);
        int len = cardModels.length();
        for (int i = 0; i < len; i++) {
            JSONObject cardModel = cardModels.getJSONObject(i);

            // id
            String id = cardModel.getString("id");
            statement.bindString(1, id);
            // ordinal
            statement.bindString(2, cardModel.getString("ordinal"));
            // modelId
            statement.bindLong(3, cardModel.getLong("modelId"));
            // name
            statement.bindString(4, cardModel.getString("name"));
            // description
            statement.bindString(5, cardModel.getString("description"));
            // active
            statement.bindLong(6, Utils.booleanToInt(cardModel.getBoolean("active")));
            // qformat
            statement.bindString(7, cardModel.getString("qformat"));
            // aformat
            statement.bindString(8, cardModel.getString("aformat"));
            // lformat
            if (cardModel.isNull("lformat")) {
                statement.bindNull(9);
            } else {
                statement.bindString(9, cardModel.getString("lformat"));
            }
            // qedformat
            if (cardModel.isNull("qedformat")) {
                statement.bindNull(10);
            } else {
                statement.bindString(10, cardModel.getString("qedformat"));
            }
            // aedformat
            if (cardModel.isNull("aedformat")) {
                statement.bindNull(11);
            } else {
                statement.bindString(11, cardModel.getString("aedformat"));
            }
            // questionInAnswer
            statement.bindLong(12, Utils.booleanToInt(cardModel.getBoolean("questionInAnswer")));
            // questionFontFamily
            statement.bindString(13, cardModel.getString("questionFontFamily"));
            // questionFontSize
            statement.bindString(14, cardModel.getString("questionFontSize"));
            // questionFontColour
            statement.bindString(15, cardModel.getString("questionFontColour"));
            // questionAlign
            statement.bindString(16, cardModel.getString("questionAlign"));
            // answerFontFamily
            statement.bindString(17, cardModel.getString("answerFontFamily"));
            // answerFontSize
            statement.bindString(18, cardModel.getString("answerFontSize"));
            // answerFontColour
            statement.bindString(19, cardModel.getString("answerFontColour"));
            // answerAlign
            statement.bindString(20, cardModel.getString("answerAlign"));
            // lastFontFamily
            statement.bindString(21, cardModel.getString("lastFontFamily"));
            // lastFontSize
            statement.bindString(22, cardModel.getString("lastFontSize"));
            // lastFontColour
            statement.bindString(23, cardModel.getString("lastFontColour"));
            // editQuestionFontFamily
            if (cardModel.isNull("editQuestionFontFamily")) {
                statement.bindNull(24);
            } else {
                statement.bindString(24, cardModel.getString("editQuestionFontFamily"));
            }
            // editQuestionFontSize
            if (cardModel.isNull("editQuestionFontSize")) {
                statement.bindNull(25);
            } else {
                statement.bindString(25, cardModel.getString("editQuestionFontSize"));
            }
            // editAnswerFontFamily
            if (cardModel.isNull("editAnswerFontFamily")) {
                statement.bindNull(26);
            } else {
                statement.bindString(26, cardModel.getString("editAnswerFontFamily"));
            }
            // editAnswerFontSize
            if (cardModel.isNull("editAnswerFontSize")) {
                statement.bindNull(27);
            } else {
                statement.bindString(27, cardModel.getString("editAnswerFontSize"));
            }
            // allowEmptyAnswer
            if (cardModel.isNull("allowEmptyAnswer")) {
                cardModel.put("allowEmptyAnswer", true);
            }
            statement.bindLong(28, Utils.booleanToInt(cardModel.getBoolean("allowEmptyAnswer")));
            // typeAnswer
            statement.bindString(29, cardModel.getString("typeAnswer"));

            statement.execute();

            ids.add(id);
        }
        statement.close();

        // Delete card models that were not returned by the server
        ArrayList<String> cardModelsIds = mDeck.getDB().queryColumn(String.class, "SELECT id FROM cardModels WHERE modelId = "
                + modelId, 0);
        if (cardModelsIds != null) {
            for (String cardModelId : cardModelsIds) {
                if (!ids.contains(cardModelId)) {
                    mDeck.deleteCardModel(modelId, cardModelId);
                }
            }
        }
    }


    /**
     * Facts
     */

    // TODO: Take into account the updateModified boolean (modified = time.time() or modified = "modified"... what does
    // exactly do that?)
    /**
     * Anki Desktop -> libanki/anki/sync.py, SyncTools - getFacts
     * @throws JSONException 
     */
    private JSONObject getFacts(JSONArray ids) throws JSONException// , boolean updateModified)
    {
        Log.i(AnkiDroidApp.TAG, "getFacts");

        JSONObject facts = new JSONObject();

        JSONArray factsArray = new JSONArray();
        JSONArray fieldsArray = new JSONArray();

        int len = ids.length();
        for (int i = 0; i < len; i++) {
            Long id = ids.getLong(i);
            factsArray.put(getFact(id));
            putFields(fieldsArray, id);
        }

        facts.put("facts", factsArray);
        facts.put("fields", fieldsArray);

        Log.i(AnkiDroidApp.TAG, "facts = ");
        Utils.printJSONObject(facts, false);

        return facts;
    }


    private JSONArray getFact(Long id) throws JSONException {
        JSONArray fact = new JSONArray();

        // TODO: Take into account the updateModified boolean (modified = time.time() or modified = "modified"... what
        // does exactly do that?)
        Cursor cursor = AnkiDatabaseManager.getDatabase(mDeck.getDeckPath()).getDatabase()
                .rawQuery("SELECT id, modelId, created, modified, tags, spaceUntil, lastCardId FROM facts WHERE id = "
                        + id, null);
        if (cursor.moveToFirst()) {
            fact.put(cursor.getLong(0));
            fact.put(cursor.getLong(1));
            fact.put(cursor.getDouble(2));
            fact.put(cursor.getDouble(3));
            fact.put(cursor.getString(4));
            fact.put(cursor.getDouble(5));
            fact.put(cursor.getLong(6));
        }
        cursor.close();

        return fact;
    }


    private void putFields(JSONArray fields, Long id) {
        Cursor cursor = AnkiDatabaseManager.getDatabase(mDeck.getDeckPath()).getDatabase().rawQuery(
                "SELECT * FROM fields WHERE factId = " + id, null);
        while (cursor.moveToNext()) {
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


    private void updateFacts(JSONObject factsDict) throws JSONException {
        AnkiDb ankiDB = AnkiDatabaseManager.getDatabase(mDeck.getDeckPath());
        JSONArray facts = factsDict.getJSONArray("facts");
        int lenFacts = facts.length();

        if (lenFacts > 0) {
            JSONArray fields = factsDict.getJSONArray("fields");
            int lenFields = fields.length();

            // Grab fact ids
            // They will be used later to recalculate the count of facts and to delete them from DB
            ArrayList<String> factIds = new ArrayList<String>();
            for (int i = 0; i < lenFacts; i++) {
                factIds.add(facts.getJSONArray(i).getString(0));
            }
            String factIdsString = Utils.ids2str(factIds);

            // Update facts
            String sqlFact = "INSERT OR REPLACE INTO facts (id, modelId, created, modified, tags, spaceUntil, lastCardId)"
                            + " VALUES(?,?,?,?,?,?,?)";
            SQLiteStatement statement = ankiDB.getDatabase().compileStatement(sqlFact);
            for (int i = 0; i < lenFacts; i++) {
                JSONArray fact = facts.getJSONArray(i);

                // id
                statement.bindLong(1, fact.getLong(0));
                // modelId
                statement.bindLong(2, fact.getLong(1));
                // created
                statement.bindDouble(3, fact.getDouble(2));
                // modified
                statement.bindDouble(4, fact.getDouble(3));
                // tags
                statement.bindString(5, fact.getString(4));
                // spaceUntil
                if (fact.getString(5) == null) {
                    statement.bindString(6, "");
                } else {
                    statement.bindString(6, fact.getString(5));
                }
                // lastCardId
                if (!fact.isNull(6)) {
                    statement.bindLong(7, fact.getLong(6));
                } else {
                    statement.bindNull(7);
                }

                statement.execute();
                
            }
            statement.close();

            // Update fields (and delete first the local ones, since ids may have changed)
            ankiDB.getDatabase().execSQL("DELETE FROM fields WHERE factId IN " + factIdsString);

            String sqlFields = "INSERT INTO fields (id, factId, fieldModelId, ordinal, value) VALUES(?,?,?,?,?)";
            statement = ankiDB.getDatabase().compileStatement(sqlFields);
            for (int i = 0; i < lenFields; i++) {
                JSONArray field = fields.getJSONArray(i);

                // id
                statement.bindLong(1, field.getLong(0));
                // factId
                statement.bindLong(2, field.getLong(1));
                // fieldModelId
                statement.bindLong(3, field.getLong(2));
                // ordinal
                statement.bindString(4, field.getString(3));
                // value
                statement.bindString(5, field.getString(4));

                statement.execute();
            }
            statement.close();

            // Delete inserted facts from deleted
            ankiDB.getDatabase().execSQL("DELETE FROM factsDeleted WHERE factId IN " + factIdsString);
        }
    }


    /**
     * Cards
     */

    /**
     * Anki Desktop -> libanki/anki/sync.py, SyncTools - getCards
     * @throws JSONException 
     */
    private JSONArray getCards(JSONArray ids) throws JSONException {
        JSONArray cards = new JSONArray();

        // SELECT id, factId, cardModelId, created, modified, tags, ordinal, priority, interval, lastInterval, due,
        // lastDue, factor,
        // firstAnswered, reps, successive, averageTime, reviewTime, youngEase0, youngEase1, youngEase2, youngEase3,
        // youngEase4,
        // matureEase0, matureEase1, matureEase2, matureEase3, matureEase4, yesCount, noCount, question, answer,
        // lastFactor, spaceUntil, relativeDelay,
        // type, combinedDue FROM cards WHERE id IN " + ids2str(ids)
        Cursor cursor = AnkiDatabaseManager.getDatabase(mDeck.getDeckPath()).getDatabase().rawQuery(
                "SELECT * FROM cards WHERE id IN " + Utils.ids2str(ids), null);
        while (cursor.moveToNext()) {
            JSONArray card = new JSONArray();

            // id
            card.put(cursor.getLong(0));
            // factId
            card.put(cursor.getLong(1));
            // cardModelId
            card.put(cursor.getLong(2));
            // created
            card.put(cursor.getDouble(3));
            // modified
            card.put(cursor.getDouble(4));
            // tags
            card.put(cursor.getString(5));
            // ordinal
            card.put(cursor.getInt(6));
            // priority
            card.put(cursor.getInt(9));
            // interval
            card.put(cursor.getDouble(10));
            // lastInterval
            card.put(cursor.getDouble(11));
            // due
            card.put(cursor.getDouble(12));
            // lastDue
            card.put(cursor.getDouble(13));
            // factor
            card.put(cursor.getDouble(14));
            // firstAnswered
            card.put(cursor.getDouble(16));
            // reps
            card.put(cursor.getString(17));
            // successive
            card.put(cursor.getInt(18));
            // averageTime
            card.put(cursor.getDouble(19));
            // reviewTime
            card.put(cursor.getDouble(20));
            // youngEase0
            card.put(cursor.getInt(21));
            // youngEase1
            card.put(cursor.getInt(22));
            // youngEase2
            card.put(cursor.getInt(23));
            // youngEase3
            card.put(cursor.getInt(24));
            // youngEase4
            card.put(cursor.getInt(25));
            // matureEase0
            card.put(cursor.getInt(26));
            // matureEase1
            card.put(cursor.getInt(27));
            // matureEase2
            card.put(cursor.getInt(28));
            // matureEase3
            card.put(cursor.getInt(29));
            // matureEase4
            card.put(cursor.getInt(30));
            // yesCount
            card.put(cursor.getInt(31));
            // noCount
            card.put(cursor.getInt(32));
            // question
            card.put(cursor.getString(7));
            // answer
            card.put(cursor.getString(8));
            // lastFactor
            card.put(cursor.getDouble(15));
            // spaceUntil
            card.put(cursor.getDouble(33));
            // type
            card.put(cursor.getInt(36));
            // combinedDue
            card.put(cursor.getDouble(37));
            // relativeDelay
            card.put(cursor.getInt(34));

            cards.put(card);
        }
        cursor.close();

        return cards;
    }


    private void updateCards(JSONArray cards) throws JSONException {
        int len = cards.length();
        if (len > 0) {
            AnkiDb ankiDB = AnkiDatabaseManager.getDatabase(mDeck.getDeckPath());
            ArrayList<String> ids = new ArrayList<String>();
            for (int i = 0; i < len; i++) {
                ids.add(cards.getJSONArray(i).getString(0));
            }
            String idsString = Utils.ids2str(ids);

            String sql = "INSERT OR REPLACE INTO cards (id, factId, cardModelId, created, modified, tags, ordinal, "
                    + "priority, interval, lastInterval, due, lastDue, factor, firstAnswered, reps, successive, "
                    + "averageTime, reviewTime, youngEase0, youngEase1, youngEase2, youngEase3, youngEase4, "
                    + "matureEase0, matureEase1, matureEase2, matureEase3, matureEase4, yesCount, noCount, question, "
                    + "answer, lastFactor, spaceUntil, type, combinedDue, relativeDelay, isDue) "
                    + "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?, 0)";
            SQLiteStatement statement = ankiDB.getDatabase().compileStatement(sql);
            for (int i = 0; i < len; i++) {
                JSONArray card = cards.getJSONArray(i);

                // id
                statement.bindLong(1, card.getLong(0));
                // factId
                statement.bindLong(2, card.getLong(1));
                // cardModelId
                statement.bindLong(3, card.getLong(2));
                // created
                statement.bindDouble(4, card.getDouble(3));
                // modified
                statement.bindDouble(5, card.getDouble(4));
                // tags
                statement.bindString(6, card.getString(5));
                // ordinal
                statement.bindLong(7, card.getInt(6));
                // priority
                statement.bindLong(8, card.getInt(7));
                // interval
                statement.bindDouble(9, card.getDouble(8));
                // lastInterval
                statement.bindDouble(10, card.getDouble(9));
                // due
                statement.bindDouble(11, card.getDouble(10));
                // lastDue
                statement.bindDouble(12, card.getDouble(11));
                // factor
                statement.bindDouble(13, card.getDouble(12));
                // firstAnswered
                statement.bindDouble(14, card.getDouble(13));
                // reps
                statement.bindLong(15, card.getInt(14));
                // successive
                statement.bindLong(16, card.getInt(15));
                // averageTime
                statement.bindDouble(17, card.getDouble(16));
                // reviewTime
                statement.bindDouble(18, card.getDouble(17));
                // youngEase0
                statement.bindLong(19, card.getInt(18));
                // youngEase1
                statement.bindLong(20, card.getInt(19));
                // youngEase2
                statement.bindLong(21, card.getInt(20));
                // youngEase3
                statement.bindLong(22, card.getInt(21));
                // youngEase4
                statement.bindLong(23, card.getInt(22));
                // matureEase0
                statement.bindLong(24, card.getInt(23));
                // matureEase1
                statement.bindLong(25, card.getInt(24));
                // matureEase2
                statement.bindLong(26, card.getInt(25));
                // matureEase3
                statement.bindLong(27, card.getInt(26));
                // matureEase4
                statement.bindLong(28, card.getInt(27));
                // yesCount
                statement.bindLong(29, card.getInt(28));
                // noCount
                statement.bindLong(30, card.getInt(29));
                // question
                statement.bindString(31, card.getString(30));
                // answer
                statement.bindString(32, card.getString(31));
                // lastFactor
                statement.bindDouble(33, card.getDouble(32));
                // spaceUntil
                statement.bindDouble(34, card.getDouble(33));
                // type
                statement.bindLong(35, card.getInt(34));
                // combinedDue
                statement.bindDouble(36, card.getDouble(35));
                // relativeDelay
                statement.bindString(37, genType(card));

                statement.execute();
            }
            statement.close();

            ankiDB.getDatabase().execSQL("DELETE FROM cardsDeleted WHERE cardId IN " + idsString);
        }
    }
    private String genType(JSONArray row) throws JSONException {
        if (row.length() >= 37) {
            return row.getString(36);
        }
        if (row.getInt(15) != 0) {
            return "1";
        } else if (row.getInt(14) != 0) {
            return "0";
        }
        return "2";
    }


    /**
     * Media
     */

    /**
     * Anki Desktop -> libanki/anki/sync.py, SyncTools - getMedia
     * @throws JSONException 
     */
    private JSONArray getMedia(JSONArray ids) throws JSONException {
        JSONArray media = new JSONArray();

        Cursor cursor = AnkiDatabaseManager.getDatabase(mDeck.getDeckPath()).getDatabase().rawQuery(
                "SELECT id, filename, size, created, originalPath, description FROM media WHERE id IN "
                        + Utils.ids2str(ids), null);
        while (cursor.moveToNext()) {
            JSONArray m = new JSONArray();

            // id
            m.put(cursor.getLong(0));
            // filename
            m.put(cursor.getString(1));
            // size
            m.put(cursor.getInt(2));
            // created
            m.put(cursor.getDouble(3));
            // originalPath
            m.put(cursor.getString(4));
            // description
            m.put(cursor.getString(5));

            media.put(m);
        }
        cursor.close();

        return media;
    }


    private void deleteMedia(JSONArray ids) throws JSONException {
        Log.i(AnkiDroidApp.TAG, "deleteMedia");

        String idsString = Utils.ids2str(ids);

        // Get filenames
        // files below is never used, so it's commented out
        // ArrayList<String> files = mDeck.getDB().queryColumn(String.class, "SELECT filename FROM media WHERE id IN "
        //         + idsString, 0);

        // Note the media to delete (Insert the media to delete into mediaDeleted)
        double now = Utils.now();
        String sqlInsert = "INSERT INTO mediaDeleted SELECT id, " + String.format(Utils.ENGLISH_LOCALE, "%f", now)
                + " FROM media WHERE media.id = ?";
        SQLiteStatement statement = mDeck.getDB().getDatabase().compileStatement(sqlInsert);
        int len = ids.length();
        for (int i = 0; i < len; i++) {
            Log.i(AnkiDroidApp.TAG, "Inserting media " + ids.getLong(i) + " into mediaDeleted");
            statement.bindLong(1, ids.getLong(i));
            statement.executeInsert();
        }
        statement.close();

        // Delete media
        Log.i(AnkiDroidApp.TAG, "Deleting media in = " + idsString);
        mDeck.getDB().getDatabase().execSQL("DELETE FROM media WHERE id IN " + idsString);
    }


    private void updateMedia(JSONArray media) throws JSONException {
        AnkiDb ankiDB = AnkiDatabaseManager.getDatabase(mDeck.getDeckPath());
        ArrayList<String> mediaIds = new ArrayList<String>();

        String sql = "INSERT OR REPLACE INTO media (id, filename, size, created, originalPath, description) "
                    + "VALUES(?,?,?,?,?,?)";
        SQLiteStatement statement = ankiDB.getDatabase().compileStatement(sql);
        int len = media.length();
        for (int i = 0; i < len; i++) {
            JSONArray m = media.getJSONArray(i);

            // Grab media ids, to delete them later
            String id = m.getString(0);
            mediaIds.add(id);

            // id
            statement.bindString(1, id);
            // filename
            statement.bindString(2, m.getString(1));
            // size
            statement.bindString(3, m.getString(2));
            // created
            statement.bindDouble(4, m.getDouble(3));
            // originalPath
            statement.bindString(5, m.getString(4));
            // description
            statement.bindString(6, m.getString(5));

            statement.execute();
        }
        statement.close();

        ankiDB.getDatabase().execSQL("DELETE FROM mediaDeleted WHERE mediaId IN " + Utils.ids2str(mediaIds));
    }


    /**
     * Deck/Stats/History/Sources
     * @throws JSONException 
     */

    private JSONObject bundleDeck() throws JSONException {
        JSONObject bundledDeck = new JSONObject();

        // Ensure modified is not greater than server time
        if ((mServer != null) && (mServer.getTimestamp() != 0.0)) {
            mDeck.setModified(Math.min(mDeck.getModified(), mServer.getTimestamp()));
            Log.i(AnkiDroidApp.TAG, String.format(Utils.ENGLISH_LOCALE, "Modified: %f", mDeck.getModified()));
        }
        // And ensure lastSync is greater than modified
        mDeck.setLastSync(Math.max(Utils.now(), mDeck.getModified() + 1));
        Log.i(AnkiDroidApp.TAG, String.format(Utils.ENGLISH_LOCALE, "LastSync: %f", mDeck.getLastSync()));

        bundledDeck = mDeck.bundleJson(bundledDeck);

        // AnkiDroid Deck.java does not have:
        // css, forceMediaDir, lastSessionStart, lastTags, needLock, newCardOrder, newCardSpacing, newCardsPerDay,
        // progressHandlerCalled,
        // progressHandlerEnabled, revCardOrder, sessionRepLimit, sessionStartReps, sessionStartTime,
        // sessionTimeLimit, tmpMediaDir

        // Our bundleDeck also doesn't need all those fields that store the scheduler Methods

        // Add meta information of the deck (deckVars table)
        JSONArray meta = new JSONArray();
        Cursor cursor = AnkiDatabaseManager.getDatabase(mDeck.getDeckPath()).getDatabase().rawQuery(
                "SELECT * FROM deckVars", null);
        while (cursor.moveToNext()) {
            JSONArray deckVar = new JSONArray();
            deckVar.put(cursor.getString(0));
            deckVar.put(cursor.getString(1));
            meta.put(deckVar);
        }
        cursor.close();
        bundledDeck.put("meta", meta);

        Log.i(AnkiDroidApp.TAG, "Deck =");
        Utils.printJSONObject(bundledDeck, false);

        return bundledDeck;
    }


    private void updateDeck(JSONObject deckPayload) throws JSONException {
        JSONArray meta = deckPayload.getJSONArray("meta");

        // Update meta information
        String sqlMeta = "INSERT OR REPLACE INTO deckVars (key, value) VALUES(?,?)";
        SQLiteStatement statement = AnkiDatabaseManager.getDatabase(mDeck.getDeckPath()).getDatabase()
                .compileStatement(sqlMeta);
        int lenMeta = meta.length();
        for (int i = 0; i < lenMeta; i++) {
            JSONArray deckVar = meta.getJSONArray(i);

            // key
            statement.bindString(1, deckVar.getString(0));
            // value
            statement.bindString(2, deckVar.getString(1));

            statement.execute();
        }
        statement.close();

        // Update deck
        mDeck.updateFromJson(deckPayload);
    }


    private JSONObject bundleStats() throws JSONException {
        Log.i(AnkiDroidApp.TAG, "bundleStats");

        JSONObject bundledStats = new JSONObject();

        // Get daily stats since the last day the deck was synchronized
        Date lastDay = new Date(java.lang.Math.max(0, (long) (mDeck.getLastSync() - 60 * 60 * 24) * 1000));
        Log.i(AnkiDroidApp.TAG, "lastDay = " + lastDay.toString());
        ArrayList<Long> ids = mDeck.getDB().queryColumn(Long.class,
                "SELECT id FROM stats WHERE type = 1 and day >= \"" + lastDay.toString() + "\"", 0);

        Stats stat = new Stats(mDeck);
        // Put global stats
        bundledStats.put("global", Stats.globalStats(mDeck).bundleJson());
        // Put daily stats
        JSONArray dailyStats = new JSONArray();
        if (ids != null) {
            for (Long id : ids) {
                // Update stat with the values of the stat with id ids.get(i)
                stat.fromDB(id);
                // Bundle this stat and add it to dailyStats
                dailyStats.put(stat.bundleJson());
            }
        }
        bundledStats.put("daily", dailyStats);

        Log.i(AnkiDroidApp.TAG, "Stats =");
        Utils.printJSONObject(bundledStats, false);

        return bundledStats;
    }


    private void updateStats(JSONObject stats) throws JSONException {
        // Update global stats
        Stats globalStats = Stats.globalStats(mDeck);
        globalStats.updateFromJson(stats.getJSONObject("global"));

        // Update daily stats
        Stats stat = new Stats(mDeck);
        JSONArray remoteDailyStats = stats.getJSONArray("daily");
        int len = remoteDailyStats.length();
        for (int i = 0; i < len; i++) {
            // Get a specific daily stat
            JSONObject remoteStat = remoteDailyStats.getJSONObject(i);
            Date dailyStatDate = Utils.ordinalToDate(remoteStat.getInt("day"));

            // If exists a statistic for this day, get it
            try {
                Long id = AnkiDatabaseManager.getDatabase(mDeck.getDeckPath()).queryScalar(
                        "SELECT id FROM stats WHERE type = 1 AND day = \"" + dailyStatDate.toString() + "\"");
                stat.fromDB(id);
            } catch (SQLException e) {
                // If it does not exist, create a statistic for this day
                stat.create(Stats.STATS_DAY, dailyStatDate);
            }

            // Update daily stat
            stat.updateFromJson(remoteStat);
        }
    }


    private JSONArray bundleHistory() throws JSONException {
        double delay = 0.0;
        JSONArray bundledHistory = new JSONArray();
        Cursor cursor = AnkiDatabaseManager.getDatabase(mDeck.getDeckPath()).getDatabase().rawQuery(
                        "SELECT cardId, time, lastInterval, nextInterval, ease, delay, lastFactor, nextFactor, reps, "
                        + "thinkingTime, yesCount, noCount FROM reviewHistory "
                        + "WHERE time > " + String.format(Utils.ENGLISH_LOCALE, "%f", mDeck.getLastSync()), null);
        while (cursor.moveToNext()) {
            JSONArray review = new JSONArray();

            // cardId
            review.put(cursor.getLong(0));
            // time
            review.put(cursor.getDouble(1));
            // lastInterval
            review.put(cursor.getDouble(2));
            // nextInterval
            review.put(cursor.getDouble(3));
            // ease
            review.put(cursor.getInt(4));
            // delay
            delay = cursor.getDouble(5);
            review.put(delay);
            // lastFactor
            review.put(cursor.getDouble(6));
            // nextFactor
            review.put(cursor.getDouble(7));
            // reps
            review.put(cursor.getDouble(8));
            // thinkingTime
            review.put(cursor.getDouble(9));
            // yesCount
            review.put(cursor.getDouble(10));
            // noCount
            review.put(cursor.getDouble(11));

            Log.i(AnkiDroidApp.TAG, String.format(Utils.ENGLISH_LOCALE, "Debugging issue 372: delay = %f, review = %s", delay, review.toString()));
            bundledHistory.put(review);
        }
        cursor.close();

        Log.i(AnkiDroidApp.TAG, "Last sync = " + String.format(Utils.ENGLISH_LOCALE, "%f", mDeck.getLastSync()));
        Log.i(AnkiDroidApp.TAG, "Bundled history = " + bundledHistory.toString());
        return bundledHistory;
    }


    private void updateHistory(JSONArray history) throws JSONException {
        String sql = "INSERT OR IGNORE INTO reviewHistory (cardId, time, lastInterval, nextInterval, ease, delay, "
                    + "lastFactor, nextFactor, reps, thinkingTime, yesCount, noCount) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)";
        SQLiteStatement statement = AnkiDatabaseManager.getDatabase(mDeck.getDeckPath()).getDatabase().compileStatement(sql);
        int len = history.length();
        for (int i = 0; i < len; i++) {
            JSONArray h = history.getJSONArray(i);

            // cardId
            statement.bindLong(1, h.getLong(0));
            // time
            statement.bindDouble(2, h.getDouble(1));
            // lastInterval
            statement.bindDouble(3, h.getDouble(2));
            // nextInterval
            statement.bindDouble(4, h.getDouble(3));
            // ease
            statement.bindString(5, h.getString(4));
            // delay
            statement.bindDouble(6, h.getDouble(5));
            // lastFactor
            statement.bindDouble(7, h.getDouble(6));
            // nextFactor
            statement.bindDouble(8, h.getDouble(7));
            // reps
            statement.bindDouble(9, h.getDouble(8));
            // thinkingTime
            statement.bindDouble(10, h.getDouble(9));
            // yesCount
            statement.bindDouble(11, h.getDouble(10));
            // noCount
            statement.bindDouble(12, h.getDouble(11));

            statement.execute();
        }
        statement.close();
    }


    private JSONArray bundleSources() throws JSONException {
        JSONArray bundledSources = new JSONArray();

        Cursor cursor = AnkiDatabaseManager.getDatabase(mDeck.getDeckPath()).getDatabase().rawQuery(
                "SELECT * FROM sources", null);
        while (cursor.moveToNext()) {
            JSONArray source = new JSONArray();

            // id
            source.put(cursor.getLong(0));
            // name
            source.put(cursor.getString(1));
            // created
            source.put(cursor.getDouble(2));
            // lastSync
            source.put(cursor.getDouble(3));
            // syncPeriod
            source.put(cursor.getInt(4));

            bundledSources.put(source);
        }
        cursor.close();

        Log.i(AnkiDroidApp.TAG, "Bundled sources = " + bundledSources);
        return bundledSources;
    }


    private void updateSources(JSONArray sources) throws JSONException {
        String sql = "INSERT OR REPLACE INTO sources VALUES(?,?,?,?,?)";
        SQLiteStatement statement = AnkiDatabaseManager.getDatabase(mDeck.getDeckPath()).getDatabase().compileStatement(sql);
        int len = sources.length();
        for (int i = 0; i < len; i++) {
            JSONArray source = sources.getJSONArray(i);
            statement.bindLong(1, source.getLong(0));
            statement.bindString(2, source.getString(1));
            statement.bindDouble(3, source.getDouble(2));
            statement.bindDouble(4, source.getDouble(3));
            statement.bindString(5, source.getString(4));
            statement.execute();
        }
        statement.close();
    }


    /**
     * Full sync
     */

    /**
     * Anki Desktop -> libanki/anki/sync.py, SyncTools - needFullSync
     * 
     * @param sums
     * @return
     * @throws JSONException 
     */
    @SuppressWarnings("unchecked")
    public boolean needFullSync(JSONArray sums) throws JSONException {
        Log.i(AnkiDroidApp.TAG, "needFullSync - lastSync = " + mDeck.getLastSync());

        if (mDeck.getLastSync() <= 0) {
            Log.i(AnkiDroidApp.TAG, "deck.lastSync <= 0");
            return true;
        }

        int len = sums.length();
        for (int i = 0; i < len; i++) {
        
            JSONObject summary = sums.getJSONObject(i);
            Iterator keys = summary.keys();
            while (keys.hasNext()) {
                String key = (String) keys.next();
                JSONArray l = (JSONArray) summary.get(key);
                Log.i(AnkiDroidApp.TAG, "Key " + key + ", length = " + l.length());
                if (l.length() > 500) {
                    Log.i(AnkiDroidApp.TAG, "Length of key > 500");
                    return true;
                }
            }
        }

        AnkiDb ankiDB = AnkiDatabaseManager.getDatabase(mDeck.getDeckPath());

        if (ankiDB.queryScalar("SELECT count() FROM reviewHistory WHERE time > " + mDeck.getLastSync()) > 500) {
            Log.i(AnkiDroidApp.TAG, "reviewHistory since lastSync > 500");
            return true;
        }
        Date lastDay = new Date(java.lang.Math.max(0, (long) (mDeck.getLastSync() - 60 * 60 * 24) * 1000));

        Log.i(AnkiDroidApp.TAG, "lastDay = " + lastDay.toString() + ", lastDayInMillis = " + lastDay.getTime());

        Log.i(AnkiDroidApp.TAG, "Count stats = " + ankiDB.queryScalar("SELECT count() FROM stats WHERE day >= \"" + lastDay.toString() + "\""));
        if (ankiDB.queryScalar("SELECT count() FROM stats WHERE day >= \"" + lastDay.toString() + "\"") > 100) {
            Log.i(AnkiDroidApp.TAG, "stats since lastDay > 100");
            return true;
        }

        return false;
    }


    public String prepareFullSync() {
        // Ensure modified is not greater than server time
        mDeck.setModified(Math.min(mDeck.getModified(), mServer.getTimestamp()));
        mDeck.commitToDB();
        // The deck is closed after the full sync is completed
        // mDeck.closeDeck();

        if (mLocalTime > mRemoteTime) {
            return "fromLocal";
        } else {
            return "fromServer";
        }
    }


    public static HashMap<String, String> fullSyncFromLocal(String password, String username, Deck deck, String deckName) {
        HashMap<String, String> result = new HashMap<String, String>();
        Throwable exc = null;
        try {
            Log.i(AnkiDroidApp.TAG, "Fullup");
            // We need to write the output to a temporary file, so that FileEntity knows the length
            String tmpPath = (new File(deck.getDeckPath())).getParent();
            File tmpFile = new File(tmpPath + "/fulluploadPayload.tmp");
            if (tmpFile.exists()) {
                tmpFile.delete();
            }
            Log.i(AnkiDroidApp.TAG, "Writing temporary payload file...");
            tmpFile.createNewFile();
            DataOutputStream tmp = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(tmpFile)));
            tmp.writeBytes(TWO_HYPHENS + MIME_BOUNDARY + END);
            tmp.writeBytes("Content-Disposition: form-data; name=\"p\"" + END + END + password + END);
            tmp.writeBytes(TWO_HYPHENS + MIME_BOUNDARY + END);
            tmp.writeBytes("Content-Disposition: form-data; name=\"u\"" + END + END + username + END);
            tmp.writeBytes(TWO_HYPHENS + MIME_BOUNDARY + END);
            tmp.writeBytes("Content-Disposition: form-data; name=\"d\"" + END + END);
            tmp.write(deckName.getBytes("UTF-8"));
            tmp.writeBytes(END);
            tmp.writeBytes(TWO_HYPHENS + MIME_BOUNDARY + END);
            tmp.writeBytes("Content-Disposition: form-data; name=\"deck\"; filename=\"deck\"" + END);
            tmp.writeBytes("Content-Type: application/octet-stream" + END);
            tmp.writeBytes(END);

            String deckPath = deck.getDeckPath();
            FileInputStream fStream = new FileInputStream(deckPath);
            byte[] buffer = new byte[Utils.CHUNK_SIZE];
            int length = -1;
            Deflater deflater = new Deflater(Deflater.BEST_SPEED);
            DeflaterOutputStream dos = new DeflaterOutputStream(tmp, deflater);
            while ((length = fStream.read(buffer)) != -1) {
                dos.write(buffer, 0, length);
                Log.i(AnkiDroidApp.TAG, "Length = " + length);
            }
            dos.finish();
            fStream.close();

            tmp.writeBytes(END);
            tmp.writeBytes(TWO_HYPHENS + MIME_BOUNDARY + TWO_HYPHENS + END + END);
            tmp.flush();
            tmp.close();
            Log.i(AnkiDroidApp.TAG, "Payload file ready, size: " + tmpFile.length());

            HttpPost httpPost = new HttpPost(AnkiDroidProxy.SYNC_URL + "fullup?v=" +
                    URLEncoder.encode(AnkiDroidProxy.SYNC_VERSION, "UTF-8"));
            httpPost.setHeader("Content-type", "multipart/form-data; boundary=" + MIME_BOUNDARY);
            httpPost.addHeader("Host", AnkiDroidProxy.SYNC_HOST);
            httpPost.setEntity(new FileEntity(tmpFile, "application/octet-stream"));
            DefaultHttpClient httpClient = new DefaultHttpClient();
            HttpResponse resp = httpClient.execute(httpPost);
            
            // Ensure we got the HTTP 200 response code
            String response = Utils.convertStreamToString(resp.getEntity().getContent());
            int responseCode = resp.getStatusLine().getStatusCode();
            Log.i(AnkiDroidApp.TAG, "Response code = " + responseCode);
            // Read the response
			if (response.substring(0,2).equals("OK")) {
				// Update lastSync
			    deck.setLastSync(Double.parseDouble(response.substring(3, response.length()-3)));
			    deck.commitToDB();
			    // Make sure we don't set modified later than lastSync when we do closeDeck later:
			    deck.setLastLoaded(deck.getModified());
			    // Remove temp file
			    tmpFile.delete();
			}
            Log.i(AnkiDroidApp.TAG, "Finished!");
            result.put("code", String.valueOf(responseCode));
            result.put("message", response);
        } catch (ClientProtocolException e) {
            Log.e(AnkiDroidApp.TAG, Log.getStackTraceString(e));
            result.put("code", "ClientProtocolException");
            exc = e;
        } catch (UnsupportedEncodingException e) {
            Log.e(AnkiDroidApp.TAG, Log.getStackTraceString(e));
            result.put("code", "UnsupportedEncodingException");
            exc = e;
        } catch (MalformedURLException e) {
            Log.e(AnkiDroidApp.TAG, Log.getStackTraceString(e));
            result.put("code", "MalformedURLException");
            exc = e;
        } catch (IOException e) {
            Log.e(AnkiDroidApp.TAG, Log.getStackTraceString(e));
            result.put("code", "IOException");
            exc = e;
        }
        
        if (exc != null) {
            // Sometimes the exception has null message and we have to get it from its cause
            while (exc.getMessage() == null && exc.getCause() != null) {
                exc = exc.getCause();
            }
            result.put("message", exc.getMessage());
        }
        return result;
    }


    public static HashMap<String, String> fullSyncFromServer(String password, String username, String deckName, String deckPath) {
        HashMap<String, String> result = new HashMap<String, String>();
        Throwable exc = null;
        try {
            String data = "p=" + URLEncoder.encode(password, "UTF-8") + "&u=" + URLEncoder.encode(username, "UTF-8")
                    + "&d=" + URLEncoder.encode(deckName, "UTF-8");

            // Log.i(AnkiDroidApp.TAG, "Data json = " + data);
            HttpPost httpPost = new HttpPost(AnkiDroidProxy.SYNC_URL + "fulldown");
            StringEntity entity = new StringEntity(data);
            httpPost.setEntity(entity);
            httpPost.setHeader("Content-type", "application/x-www-form-urlencoded");
            DefaultHttpClient httpClient = new DefaultHttpClient();
            HttpResponse response = httpClient.execute(httpPost);
            HttpEntity entityResponse = response.getEntity();
            InputStream content = entityResponse.getContent();
            int responseCode = response.getStatusLine().getStatusCode();
            String tempDeckPath = deckPath + ".tmp";
            if (responseCode == 200) {
                Utils.writeToFile(new InflaterInputStream(content), tempDeckPath);
                File newFile = new File(tempDeckPath);
                //File oldFile = new File(deckPath);
                if (newFile.renameTo(new File(deckPath))) {
                    result.put("code", "200");
                } else {
                    result.put("code", "PermissionError");
                    result.put("message", "Can't overwrite old deck with downloaded from server");
                }
            } else {
                result.put("code", String.valueOf(responseCode));
                result.put("message", Utils.convertStreamToString(content));
            }
        } catch (UnsupportedEncodingException e) {
            Log.e(AnkiDroidApp.TAG, Log.getStackTraceString(e));
            result.put("code", "UnsupportedEncodingException");
            exc = e;
        } catch (ClientProtocolException e) {
            Log.e(AnkiDroidApp.TAG, Log.getStackTraceString(e));
            result.put("code", "ClientProtocolException");
            exc = e;
        } catch (IOException e) {
            Log.e(AnkiDroidApp.TAG, Log.getStackTraceString(e));
            result.put("code", "IOException");
            exc = e;
        }
    
        if (exc != null) {
            // Sometimes the exception has null message and we have to get it from its cause
            while (exc.getMessage() == null && exc.getCause() != null) {
                exc = exc.getCause();
            }
            result.put("message", exc.getMessage());
        }
        return result;
    }

}
