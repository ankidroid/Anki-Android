/***************************************************************************************
 * Copyright (c) 2011 Norbert Nagold <norbert.nagold@gmail.com>                         *
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

package com.ichi2.libanki.sync;

import android.database.Cursor;
import android.database.SQLException;
import android.util.Log;

import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.R;
import com.ichi2.async.Connection;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Sched;
import com.ichi2.libanki.Utils;
import com.ichi2.utils.ConvUtils;

import org.apache.http.HttpResponse;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.lang.OutOfMemoryError;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class Syncer {
    // Mapping of column type names to Cursor types for API < 11
    public static final int TYPE_NULL = 0;
    public static final int TYPE_INTEGER = 1;
    public static final int TYPE_FLOAT = 2;
    public static final int TYPE_STRING = 3;
    public static final int TYPE_BLOB = 4;

    Collection mCol;
    BasicHttpSyncer mServer;
    long mRMod;
    long mRScm;
    int mMaxUsn;
    int mMediaUsn;
    long mLMod;
    long mLScm;
    int mMinUsn;
    boolean mLNewer;
    JSONObject mRChg;

    private LinkedList<String> mTablesLeft;
    private Cursor mCursor;


    public Syncer(Collection col, BasicHttpSyncer server) {
        mCol = col;
        mServer = server;
    }


    /** Returns 'noChanges', 'fullSync', or 'success'. */
    public Object[] sync() {
        return sync(null);
    }


    public Object[] sync(Connection con) {
        // if the deck has any pending changes, flush them first and bump mod time
        mCol.save();
        // do a cleanup (unbury cards) --> anki desktop does it by closing the collection
        mCol.cleanup();
        // step 1: login & metadata
        HttpResponse ret = mServer.meta();
        if (ret == null) {
            return null;
        }
        int returntype = ret.getStatusLine().getStatusCode();
        if (returntype == 403) {
            return new Object[] { "badAuth" };
        } else if (returntype != 200) {
            return new Object[] { "error", returntype, ret.getStatusLine().getReasonPhrase() };
        }
        long rts;
        long lts;
        try {
            mCol.getDb().getDatabase().beginTransaction();
            try {
                JSONArray ra = new JSONArray(mServer.stream2String(ret.getEntity().getContent()));
                mRMod = ra.getLong(0);
                mRScm = ra.getLong(1);
                mMaxUsn = ra.getInt(2);
                rts = ra.getLong(3);
                mMediaUsn = ra.getInt(4);

                Log.i(AnkiDroidApp.TAG, "Sync: getting meta data");
                JSONArray la = meta();
                mLMod = la.getLong(0);
                mLScm = la.getLong(1);
                mMinUsn = la.getInt(2);
                lts = la.getLong(3);
                long diff = Math.abs(rts - lts);
                if (diff > 300) {
                    return new Object[] { "clockOff", diff };
                }
                if (mLMod == mRMod) {
                    Log.i(AnkiDroidApp.TAG, "Sync: no changes - returning");
                    return new Object[] { "noChanges" };
                } else if (mLScm != mRScm) {
                    Log.i(AnkiDroidApp.TAG, "Sync: full sync necessary - returning");
                    return new Object[] { "fullSync" };
                }
                mLNewer = mLMod > mRMod;
                // step 2: deletions
                publishProgress(con, R.string.sync_deletions_message);

                Log.i(AnkiDroidApp.TAG, "Sync: collection removed data");
                JSONObject lrem = removed();
                JSONObject o = new JSONObject();
                o.put("minUsn", mMinUsn);
                o.put("lnewer", mLNewer);
                o.put("graves", lrem);

                Log.i(AnkiDroidApp.TAG, "Sync: sending and receiving removed data");
                JSONObject rrem = mServer.start(o);
                if (rrem == null) {
                    Log.i(AnkiDroidApp.TAG, "Sync: error - returning");
                    return null;
                }
                if (rrem.has("errorType")) {
                    Log.i(AnkiDroidApp.TAG, "Sync: error - returning");
                    return new Object[] { "error", rrem.get("errorType"), rrem.get("errorReason") };
                }

                Log.i(AnkiDroidApp.TAG, "Sync: applying removed data");
                remove(rrem);
                // ... and small objects
                publishProgress(con, R.string.sync_small_objects_message);

                Log.i(AnkiDroidApp.TAG, "Sync: collection small changes");
                JSONObject lchg = changes();
                JSONObject sch = new JSONObject();
                sch.put("changes", lchg);

                Log.i(AnkiDroidApp.TAG, "Sync: sending and receiving small changes");
                JSONObject rchg = mServer.applyChanges(sch);
                if (rchg == null) {
                    Log.i(AnkiDroidApp.TAG, "Sync: error - returning");
                    return null;
                }
                if (rchg.has("errorType")) {
                    Log.i(AnkiDroidApp.TAG, "Sync: error - returning");
                    return new Object[] { "error", rchg.get("errorType"), rchg.get("errorReason") };
                }

                Log.i(AnkiDroidApp.TAG, "Sync: merging small changes");
                mergeChanges(lchg, rchg);
                // step 3: stream large tables from server
                publishProgress(con, R.string.sync_download_chunk);
                while (true) {
                    Log.i(AnkiDroidApp.TAG, "Sync: downloading chunked data");
                    JSONObject chunk = mServer.chunk();
                    if (chunk == null) {
                        Log.i(AnkiDroidApp.TAG, "Sync: error - returning");
                        return null;
                    }
                    if (chunk.has("errorType")) {
                        Log.i(AnkiDroidApp.TAG, "Sync: error - returning");
                        return new Object[] { "error", chunk.get("errorType"), chunk.get("errorReason") };
                    }
                    Log.i(AnkiDroidApp.TAG, "Sync: applying chunked data");
                    applyChunk(chunk);
                    if (chunk.getBoolean("done")) {
                        break;
                    }
                }
                // step 4: stream to server
                publishProgress(con, R.string.sync_upload_chunk);
                while (true) {
                    Log.i(AnkiDroidApp.TAG, "Sync: collecting chunked data");
                    JSONObject chunk = chunk();
                    JSONObject sech = new JSONObject();
                    sech.put("chunk", chunk);
                    Log.i(AnkiDroidApp.TAG, "Sync: sending chunked data");
                    mServer.applyChunk(sech);
                    if (chunk.getBoolean("done")) {
                        break;
                    }
                }
                // step 5: sanity check during beta testing
                JSONArray c = sanityCheck();
                JSONArray s = mServer.sanityCheck();
                if (c == null || s == null || s.getString(0).equals("error")) {
                    return new Object[] { "sanityCheckError", null };
                }
                boolean error = false;
                for (int i = 0; i < s.getJSONArray(0).length(); i++) {
                    if (c.getJSONArray(0).getLong(i) != s.getJSONArray(0).getLong(i)) {
                        error = true;
                    }
                }
                for (int i = 1; i < s.length(); i++) {
                    if (c.getLong(i) != s.getLong(i)) {
                        error = true;
                    }
                }
                if (error) {
                    return new Object[] { "sanityCheckError", "sanity check failed: local: " + c.toString() + ", remote: " + s.toString() };
                }
                // finalize
                publishProgress(con, R.string.sync_finish_message);
                Log.i(AnkiDroidApp.TAG, "Sync: sending finish command");
                long mod = mServer.finish();
                if (mod == 0) {
                    return new Object[] { "finishError" };
                }
                Log.i(AnkiDroidApp.TAG, "Sync: finishing");
                finish(mod);

                publishProgress(con, R.string.sync_writing_db);
                mCol.getDb().getDatabase().setTransactionSuccessful();
            } finally {
                mCol.getDb().getDatabase().endTransaction();
            }
        } catch (JSONException e) {
            throw new RuntimeException(e);
        } catch (IllegalStateException e) {
            throw new RuntimeException(e);
        } catch (OutOfMemoryError e) {
			AnkiDroidApp.saveExceptionReportFile(e, "Syncer-sync");
        	return new Object[] { "OutOfMemoryError" };
        } catch (IOException e) {
			AnkiDroidApp.saveExceptionReportFile(e, "Syncer-sync");
        	return new Object[] { "IOException" };
        }
        return new Object[] { "success" };
    }


    private void publishProgress(Connection con, int id) {
        if (con != null) {
            con.publishProgress(id);
        }
    }


    public JSONArray meta() {
        JSONArray o = new JSONArray();
        o.put(mCol.getMod());
        o.put(mCol.getScm());
        o.put(mCol.getUsnForSync());
        o.put(Utils.intNow());
        return o;
    }


    /** Bundle up small objects. */
    public JSONObject changes() {
        JSONObject o = new JSONObject();
        try {
            o.put("models", getModels());
            o.put("decks", getDecks());
            o.put("tags", getTags());
            if (mLNewer) {
                o.put("conf", getConf());
                o.put("crt", mCol.getCrt());
            }
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        return o;
    }


    public JSONObject applyChanges(JSONObject changes) {
        mRChg = changes;
        JSONObject lchg = changes();
        // merge our side before returning
        mergeChanges(lchg, mRChg);
        return lchg;
    }


    public void mergeChanges(JSONObject lchg, JSONObject rchg) {
        try {
            // then the other objects
            mergeModels(rchg.getJSONArray("models"));
            mergeDecks(rchg.getJSONArray("decks"));
            mergeTags(rchg.getJSONArray("tags"));
            if (rchg.has("conf")) {
                mergeConf(rchg.getJSONObject("conf"));
            }
            // this was left out of earlier betas
            if (rchg.has("crt")) {
                mCol.setCrt(rchg.getLong("crt"));
            }
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        prepareToChunk();
    }


    public JSONArray sanityCheck() {
        boolean ok = true;
        if (mCol.getDb().queryScalar("SELECT count() FROM cards WHERE nid NOT IN (SELECT id FROM notes)", false) != 0) {
        	ok = false;
            Log.e(AnkiDroidApp.TAG, "Sync - SanityCheck: there are cards without mother notes");
        }
        if (mCol.getDb().queryScalar(
                        "SELECT count() FROM notes WHERE id NOT IN (SELECT DISTINCT nid FROM cards)", false) != 0) {
        	ok = false;
            Log.e(AnkiDroidApp.TAG, "Sync - SanityCheck: there are notes without cards");
        }
        if (mCol.getDb().queryScalar("SELECT count() FROM cards WHERE usn = -1", false) != 0) {
        	ok = false;
            Log.e(AnkiDroidApp.TAG, "Sync - SanityCheck: there are unsynced cards");        	
        }
        if (mCol.getDb().queryScalar("SELECT count() FROM notes WHERE usn = -1", false) != 0) {
        	ok = false;
            Log.e(AnkiDroidApp.TAG, "Sync - SanityCheck: there are unsynced notes");        	
        }
        if (mCol.getDb().queryScalar("SELECT count() FROM revlog WHERE usn = -1", false) != 0) {
        	ok = false;
            Log.e(AnkiDroidApp.TAG, "Sync - SanityCheck: there are unsynced revlogs");        	
        }
        if (mCol.getDb().queryScalar("SELECT count() FROM graves WHERE usn = -1", false) != 0) {
        	ok = false;
            Log.e(AnkiDroidApp.TAG, "Sync - SanityCheck: there are unsynced graves");
        }
        try {
            for (JSONObject g : mCol.getDecks().all()) {
                if (g.getInt("usn") == -1) {
                	ok = false;
                    Log.e(AnkiDroidApp.TAG, "Sync - SanityCheck: unsynced deck: " + g.getString("name"));
                }
            }
            for (Integer usn : mCol.getTags().allItems().values()) {
                if (usn == -1) {
                	ok = false;
                    Log.e(AnkiDroidApp.TAG, "Sync - SanityCheck: there are unsynced tags");
                }
            }
            boolean found = false;
            for (JSONObject m : mCol.getModels().all()) {
                if (mCol.getServer()) {
                    // the web upgrade was mistakenly setting usn
                    if (m.getInt("usn") < 0) {
                        m.put("usn", 0);
                        found = true;
                    }
                } else {
                    if (m.getInt("usn") == -1) {
                        ok = false;
                        Log.e(AnkiDroidApp.TAG, "Sync - SanityCheck: unsynced model: " + m.getString("name"));
                    }
                }
            }
            if (found) {
                mCol.getModels().save();
            }
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        if (!ok) {
            return null;
        }
        mCol.getSched().reset();
        // check for missing parent decks
        mCol.getSched().deckDueList(Sched.DECK_INFORMATION_SIMPLE_COUNTS);
        // return summary of deck
        JSONArray ja = new JSONArray();
        JSONArray sa = new JSONArray();
        for (int c : mCol.getSched().counts()) {
            sa.put(c);
        }
        ja.put(sa);
        ja.put(mCol.getDb().queryScalar("SELECT count() FROM cards"));
        ja.put(mCol.getDb().queryScalar("SELECT count() FROM notes"));
        ja.put(mCol.getDb().queryScalar("SELECT count() FROM revlog"));
        ja.put(mCol.getDb().queryScalar("SELECT count() FROM graves"));
        ja.put(mCol.getModels().all().size());
        ja.put(mCol.getDecks().all().size());
        ja.put(mCol.getDecks().allConf().size());
        return ja;
    }


    private String usnLim() {
        if (mCol.getServer()) {
            return "usn >= " + mMinUsn;
        } else {
            return "usn = -1";
        }
    }


    public long finish() {
        return finish(0);
    }


    private long finish(long mod) {
        if (mod == 0) {
            // server side; we decide new mod time
            mod = Utils.intNow(1000);
        }
        mCol.setLs(mod);
        mCol.setUsnAfterSync(mMaxUsn + 1);
        // ensure we save the mod time even if no changes made
        mCol.getDb().setMod(true);
        mCol.save(null, mod);
        return mod;
    }


    /**
     * Chunked syncing ********************************************************************
     */

    private void prepareToChunk() {
        mTablesLeft = new LinkedList<String>();
        mTablesLeft.add("revlog");
        mTablesLeft.add("cards");
        mTablesLeft.add("notes");
        mCursor = null;
    }


    private Cursor cursorForTable(String table) {
        String lim = usnLim();
        if (table.equals("revlog")) {
            return mCol
                    .getDb()
                    .getDatabase()
                    .rawQuery(
                            String.format(
                                    "SELECT id, cid, %d, ease, ivl, lastIvl, factor, time, type FROM revlog WHERE %s",
                                    mMaxUsn, lim), null);
        } else if (table.equals("cards")) {
            return mCol
                    .getDb()
                    .getDatabase()
                    .rawQuery(
                            String.format(
                                    "SELECT id, nid, did, ord, mod, %d, type, queue, due, ivl, factor, reps, lapses, left, odue, odid, flags, data FROM cards WHERE %s",
                                    mMaxUsn, lim), null);
        } else {
            return mCol
                    .getDb()
                    .getDatabase()
                    .rawQuery(
                            String.format(
                                    "SELECT id, guid, mid, mod, %d, tags, flds, '', '', flags, data FROM notes WHERE %s",
                                    mMaxUsn, lim), null);
        }
    }


    private List<Integer> columnTypesForQuery(String table) {
        if (table.equals("revlog")) {
            return Arrays.asList(TYPE_INTEGER, TYPE_INTEGER, TYPE_INTEGER, TYPE_INTEGER, TYPE_INTEGER, TYPE_INTEGER,
                    TYPE_INTEGER, TYPE_INTEGER, TYPE_INTEGER);
        } else if (table.equals("cards")) {
            return Arrays.asList(TYPE_INTEGER, TYPE_INTEGER, TYPE_INTEGER, TYPE_INTEGER, TYPE_INTEGER, TYPE_INTEGER,
                    TYPE_INTEGER, TYPE_INTEGER, TYPE_INTEGER, TYPE_INTEGER, TYPE_INTEGER, TYPE_INTEGER, TYPE_INTEGER,
                    TYPE_INTEGER, TYPE_INTEGER, TYPE_INTEGER, TYPE_INTEGER, TYPE_STRING);
        } else {
            return Arrays.asList(TYPE_INTEGER, TYPE_STRING, TYPE_INTEGER, TYPE_INTEGER, TYPE_INTEGER, TYPE_STRING,
                    TYPE_STRING, TYPE_STRING, TYPE_STRING, TYPE_INTEGER, TYPE_STRING);
        }
    }


    public JSONObject chunk() {
        JSONObject buf = new JSONObject();
        try {
            buf.put("done", false);
            int lim = 2500;
            List<Integer> colTypes = null;
            while (!mTablesLeft.isEmpty() && lim > 0) {
                String curTable = mTablesLeft.getFirst();
                if (mCursor == null) {
                    mCursor = cursorForTable(curTable);
                    colTypes = columnTypesForQuery(curTable);
                }
                JSONArray rows = new JSONArray();
                int count = mCursor.getColumnCount();
                while (mCursor.moveToNext() && mCursor.getPosition() <= lim) {
                    JSONArray r = new JSONArray();
                    for (int i = 0; i < count; i++) {
                        switch (colTypes.get(i).intValue()) {
                            case TYPE_STRING:
                                r.put(mCursor.getString(i));
                                break;
                            case TYPE_FLOAT:
                                r.put(mCursor.getDouble(i));
                                break;
                            case TYPE_INTEGER:
                                r.put(mCursor.getLong(i));
                                break;
                        }
                    }
                    rows.put(r);
                }
                int fetched = rows.length();
                if (fetched != lim) {
                    // table is empty
                    mTablesLeft.removeFirst();
                    mCursor.close();
                    mCursor = null;
                    // if we're the client, mark the objects as having been sent
                    if (!mCol.getServer()) {
                        mCol.getDb().execute("UPDATE " + curTable + " SET usn=" + mMaxUsn + " WHERE usn=-1");
                    }
                }
                buf.put(curTable, rows);
                lim -= fetched;
            }
            if (mTablesLeft.isEmpty()) {
                buf.put("done", true);
            }
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        return buf;
    }


    public void applyChunk(JSONObject chunk) {
        try {
            if (chunk.has("revlog")) {
                mergeRevlog(chunk.getJSONArray("revlog"));
            }
            if (chunk.has("cards")) {
                mergeCards(chunk.getJSONArray("cards"));
            }
            if (chunk.has("notes")) {
                mergeNotes(chunk.getJSONArray("notes"));
            }
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }


    /**
     * Deletions ********************************************************************
     */

    private JSONObject removed() {
        JSONArray cards = new JSONArray();
        JSONArray notes = new JSONArray();
        JSONArray decks = new JSONArray();
        Cursor cur = null;
        try {
            cur = mCol
                    .getDb()
                    .getDatabase()
                    .rawQuery(
                            "SELECT oid, type FROM graves WHERE usn"
                                    + (mCol.getServer() ? (" >= " + mMinUsn) : (" = -1")), null);
            while (cur.moveToNext()) {
                int type = cur.getInt(1);
                switch (type) {
                    case Sched.REM_CARD:
                        cards.put(cur.getLong(0));
                        break;
                    case Sched.REM_NOTE:
                        notes.put(cur.getLong(0));
                        break;
                    case Sched.REM_DECK:
                        decks.put(cur.getLong(0));
                        break;
                }
            }
        } finally {
            if (cur != null && !cur.isClosed()) {
                cur.close();
            }
        }
        if (!mCol.getServer()) {
            mCol.getDb().execute("UPDATE graves SET usn=" + mMaxUsn + " WHERE usn=-1");
        }
        JSONObject o = new JSONObject();
        try {
            o.put("cards", cards);
            o.put("notes", notes);
            o.put("decks", decks);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        return o;
    }


    public JSONObject start(int minUsn, boolean lnewer, JSONObject graves) {
        mMaxUsn = mCol.getUsnForSync();
        mMinUsn = minUsn;
        mLNewer = !lnewer;
        JSONObject lgraves = removed();
        remove(graves);
        return lgraves;
    }


    private void remove(JSONObject graves) {
        // pretend to be the server so we don't set usn = -1
        boolean wasServer = mCol.getServer();
        mCol.setServer(true);
        try {
            // notes first, so we don't end up with duplicate graves
            mCol._remNotes(Utils.jsonArrayToLongArray(graves.getJSONArray("notes")));
            // then cards
            mCol.remCards(Utils.jsonArrayToLongArray(graves.getJSONArray("cards")), false);
            // and decks
            JSONArray decks = graves.getJSONArray("decks");
            for (int i = 0; i < decks.length(); i++) {
                mCol.getDecks().rem(decks.getLong(i), false, false);
            }
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        mCol.setServer(wasServer);
    }


    /**
     * Models ********************************************************************
     */

    private JSONArray getModels() {
        JSONArray result = new JSONArray();
        try {
            if (mCol.getServer()) {
                for (JSONObject m : mCol.getModels().all()) {
                    if (m.getInt("usn") >= mMinUsn) {
                        result.put(m);
                    }
                }
            } else {
                for (JSONObject m : mCol.getModels().all()) {
                    if (m.getInt("usn") == -1) {
                        m.put("usn", mMaxUsn);
                        result.put(m);
                    }
                }
                mCol.getModels().save();
            }
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        return result;
    }


    private void mergeModels(JSONArray rchg) {
        for (int i = 0; i < rchg.length(); i++) {
            try {
                JSONObject r = rchg.getJSONObject(i);
                JSONObject l;
                l = mCol.getModels().get(r.getLong("id"));
                // if missing locally or server is newer, update
                if (l == null || r.getLong("mod") > l.getLong("mod")) {
                    mCol.getModels().update(r);
                }
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
        }
    }


    /**
     * Decks ********************************************************************
     */

    private JSONArray getDecks() {
        JSONArray result = new JSONArray();
        try {
            if (mCol.getServer()) {
                JSONArray decks = new JSONArray();
                for (JSONObject g : mCol.getDecks().all()) {
                    if (g.getInt("usn") >= mMinUsn) {
                        decks.put(g);
                    }
                }
                JSONArray dconfs = new JSONArray();
                for (JSONObject g : mCol.getDecks().allConf()) {
                    if (g.getInt("usn") >= mMinUsn) {
                        dconfs.put(g);
                    }
                }
                result.put(decks);
                result.put(dconfs);
            } else {
                JSONArray decks = new JSONArray();
                for (JSONObject g : mCol.getDecks().all()) {
                    if (g.getInt("usn") == -1) {
                        g.put("usn", mMaxUsn);
                        decks.put(g);
                    }
                }
                JSONArray dconfs = new JSONArray();
                for (JSONObject g : mCol.getDecks().allConf()) {
                    if (g.getInt("usn") == -1) {
                        g.put("usn", mMaxUsn);
                        dconfs.put(g);
                    }
                }
                mCol.getDecks().save();
                result.put(decks);
                result.put(dconfs);
            }
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        return result;
    }


    private void mergeDecks(JSONArray rchg) {
        try {
            JSONArray decks = rchg.getJSONArray(0);
            for (int i = 0; i < decks.length(); i++) {
                JSONObject r = decks.getJSONObject(i);
                JSONObject l = mCol.getDecks().get(r.getLong("id"), false);
                // if missing locally or server is newer, update
                if (l == null || r.getLong("mod") > l.getLong("mod")) {
                    mCol.getDecks().update(r);
                }
            }
            JSONArray confs = rchg.getJSONArray(1);
            for (int i = 0; i < confs.length(); i++) {
                JSONObject r = confs.getJSONObject(i);
                JSONObject l = mCol.getDecks().getConf(r.getLong("id"));
                // if missing locally or server is newer, update
                if (l == null || r.getLong("mod") > l.getLong("mod")) {
                    mCol.getDecks().updateConf(r);
                }
            }
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }


    /**
     * Tags ********************************************************************
     */

    private JSONArray getTags() {
        JSONArray result = new JSONArray();
        if (mCol.getServer()) {
            for (Map.Entry<String, Integer> t : mCol.getTags().allItems().entrySet()) {
                if (t.getValue() >= mMinUsn) {
                    result.put(t.getKey());
                }
            }
        } else {
            for (Map.Entry<String, Integer> t : mCol.getTags().allItems().entrySet()) {
                if (t.getValue() == -1) {
                    String tag = t.getKey();
                    mCol.getTags().allItems().put(tag, mMaxUsn);
                    result.put(tag);
                }
            }
            mCol.getTags().save();
        }
        return result;
    }


    private void mergeTags(JSONArray tags) {
        ArrayList<String> list = new ArrayList<String>();
        for (int i = 0; i < tags.length(); i++) {
            try {
                list.add(tags.getString(i));
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
        }
        mCol.getTags().register(list, mMaxUsn);
    }


    /**
     * Cards/notes/revlog ********************************************************************
     */

    private void mergeRevlog(JSONArray logs) {
        for (int i = 0; i < logs.length(); i++) {
            try {
                mCol.getDb().execute("INSERT OR IGNORE INTO revlog VALUES (?,?,?,?,?,?,?,?,?)",
                        ConvUtils.jsonArray2Objects(logs.getJSONArray(i)));
            } catch (SQLException e) {
                throw new RuntimeException(e);
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
        }

    }


    private ArrayList<Object[]> newerRows(JSONArray data, String table, int modIdx) {
        long[] ids = new long[data.length()];
        try {
            for (int i = 0; i < data.length(); i++) {
                ids[i] = data.getJSONArray(i).getLong(0);
            }
            HashMap<Long, Long> lmods = new HashMap<Long, Long>();
            Cursor cur = null;
            try {
                cur = mCol
                        .getDb()
                        .getDatabase()
                        .rawQuery(
                                "SELECT id, mod FROM " + table + " WHERE id IN " + Utils.ids2str(ids) + " AND "
                                        + usnLim(), null);
                while (cur.moveToNext()) {
                    lmods.put(cur.getLong(0), cur.getLong(1));
                }
            } finally {
                if (cur != null && !cur.isClosed()) {
                    cur.close();
                }
            }
            ArrayList<Object[]> update = new ArrayList<Object[]>();
            for (int i = 0; i < data.length(); i++) {
                JSONArray r = data.getJSONArray(i);
                if (!lmods.containsKey(r.getLong(0)) || lmods.get(r.getLong(0)) < r.getLong(modIdx)) {
                    update.add(ConvUtils.jsonArray2Objects(r));
                }
            }
            return update;
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }


    private void mergeCards(JSONArray cards) {
        for (Object[] r : newerRows(cards, "cards", 4)) {
            mCol.getDb().execute("INSERT OR REPLACE INTO cards VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)", r);
        }
    }


    private void mergeNotes(JSONArray notes) {
        for (Object[] n : newerRows(notes, "notes", 4)) {
            mCol.getDb().execute("INSERT OR REPLACE INTO notes VALUES (?,?,?,?,?,?,?,?,?,?,?)", n);
            mCol.updateFieldCache(new long[] { (Long) n[0] });
        }
    }


    /**
     * Col config ********************************************************************
     */

    private JSONObject getConf() {
        return mCol.getConf();
    }


    private void mergeConf(JSONObject conf) {
        mCol.setConf(conf);
    }


    public int getmMediaUsn() {
        return mMediaUsn;
    }

}
