/***************************************************************************************
 * Copyright (c) 2011 Norbert Nagold <norbert.nagold@gmail.com>                         *
 * Copyright (c) 2014 Timothy Rae <perceptualchaos2@gmail.com>                          *
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
import android.util.Pair;


import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.R;
import com.ichi2.anki.analytics.UsageAnalytics;
import com.ichi2.anki.exception.UnknownHttpResponseException;
import com.ichi2.async.Connection;
import com.ichi2.libanki.DB;
import com.ichi2.libanki.Model;
import com.ichi2.libanki.backend.model.TagUsnTuple;
import com.ichi2.libanki.sched.AbstractSched;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Consts;
import com.ichi2.libanki.Utils;

import com.ichi2.libanki.Deck;
import com.ichi2.libanki.DeckConfig;
import com.ichi2.libanki.sched.Counts;
import com.ichi2.utils.HashUtil;
import com.ichi2.utils.JSONArray;
import com.ichi2.utils.JSONException;
import com.ichi2.utils.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import androidx.annotation.NonNull;
import okhttp3.Response;
import timber.log.Timber;
import static com.ichi2.libanki.sync.Syncer.ConnectionResultType.*;

@SuppressWarnings({"deprecation", // tracking HTTP transport change in github already
                    "PMD.ExcessiveClassLength","PMD.AvoidThrowingRawExceptionTypes","PMD.AvoidReassigningParameters",
                    "PMD.NPathComplexity","PMD.MethodNamingConventions","PMD.ExcessiveMethodLength",
                    "PMD.SwitchStmtsShouldHaveDefault","PMD.EmptyIfStmt","PMD.SingularField"})
public class Syncer {
    // Mapping of column type names to Cursor types for API < 11
    public static final int TYPE_NULL = 0;
    public static final int TYPE_INTEGER = 1;
    public static final int TYPE_FLOAT = 2;
    public static final int TYPE_STRING = 3;
    public static final int TYPE_BLOB = 4;

    /** The libAnki value of `sched.mReportLimit` */
    private static final int SYNC_SCHEDULER_REPORT_LIMIT = 1000;

    private final Collection mCol;
    private final RemoteServer mRemoteServer;
    //private long mRScm;
    private int mMaxUsn;

    private final HostNum mHostNum;
    //private long mLScm;
    private int mMinUsn;
    private boolean mLNewer;
    private String mSyncMsg;

    private LinkedList<String> mTablesLeft;
    private Cursor mCursor;


    public Syncer(Collection col, RemoteServer server, HostNum hostNum) {
        mCol = col;
        mRemoteServer = server;
        mHostNum = hostNum;
    }


    public enum ConnectionResultType {
        BAD_AUTH("badAuth"),
        NO_CHANGES("noChanges"),
        CLOCK_OFF("clockOff"),
        FULL_SYNC("fullSync"),
        DB_ERROR("dbError"),
        BASIC_CHECK_FAILED("basicCheckFailed"),
        OVERWRITE_ERROR("overwriteError"),
        REMOTE_DB_ERROR("remoteDbError"),
        SD_ACCESS_ERROR("sdAccessError"),
        FINISH_ERROR("finishError"),
        IO_EXCEPTION("IOException"),
        GENERIC_ERROR("genericError"),
        OUT_OF_MEMORY_ERROR("outOfMemoryError"),
        SANITY_CHECK_ERROR("sanityCheckError"),
        SERVER_ABORT("serverAbort"),
        MEDIA_SYNC_SERVER_ERROR("mediaSyncServerError"),
        CUSTOM_SYNC_SERVER_URL("customSyncServerUrl"),
        USER_ABORTED_SYNC("userAbortedSync"),
        SUCCESS("success"),
        ARBITRARY_STRING("arbitraryString"), // arbitrary error message received from sync

        MEDIA_SANITY_FAILED("sanityFailed"),
        CORRUPT("corrupt"),
        OK("OK"),

        // The next three ones are the only that can be returned during login
        UPGRADE_REQUIRED("upgradeRequired"),
        CONNECTION_ERROR("connectionError"),
        ERROR("error");

        private final String mMessage;


        ConnectionResultType(String message) {
            this.mMessage = message;
        }


        public String toString() {
            return mMessage;
        }
    }


    public Pair<ConnectionResultType, Object> sync(Connection con) throws UnknownHttpResponseException {
        mSyncMsg = "";
        // if the deck has any pending changes, flush them first and bump mod time
        mCol.getSched()._updateCutoff();
        mCol.save();
        // step 1: login & metadata
        Response ret = mRemoteServer.meta();
        if (ret == null) {
            return null;
        }
        int returntype = ret.code();
        if (returntype == 403) {
            return new Pair<>(BAD_AUTH, null);
        }
        try {
            mCol.getDb().getDatabase().beginTransaction();
            try {
                Timber.i("Sync: getting meta data from server");
                JSONObject rMeta = new JSONObject(ret.body().string());
                mCol.log("rmeta", rMeta);
                mSyncMsg = rMeta.getString("msg");
                if (!rMeta.getBoolean("cont")) {
                    // Don't add syncMsg; it can be fetched by UI code using the accessor
                    return new Pair<> (SERVER_ABORT, null);
                } else {
                    // don't abort, but ui should show messages after sync finishes
                    // and require confirmation if it's non-empty
                }
                throwExceptionIfCancelled(con);
                long rscm = rMeta.getLong("scm");
                int rts = rMeta.getInt("ts");
                long rMod = rMeta.getLong("mod");
                mMaxUsn = rMeta.getInt("usn");
                // skip uname, AnkiDroid already stores and shows it
                trySetHostNum(rMeta);
                Timber.i("Sync: building local meta data");
                JSONObject lMeta = meta();
                mCol.log("lmeta", lMeta);
                long lMod = lMeta.getLong("mod");
                mMinUsn = lMeta.getInt("usn");
                long lscm = lMeta.getLong("scm");
                int lts = lMeta.getInt("ts");

                long diff = Math.abs(rts - lts);
                if (diff > 300) {
                    mCol.log("clock off");
                    return new Pair<> (CLOCK_OFF, diff);
                }
                if (lMod == rMod) {
                    Timber.i("Sync: no changes - returning");
                    mCol.log("no changes");
                    return new Pair<>( NO_CHANGES , null);
                } else if (lscm != rscm) {
                    Timber.i("Sync: full sync necessary - returning");
                    mCol.log("schema diff");
                    return new Pair<>( FULL_SYNC , null);
                }
                mLNewer = lMod > rMod;
                // step 1.5: check collection is valid
                if (!mCol.basicCheck()) {
                    mCol.log("basic check");
                    return new Pair<>( BASIC_CHECK_FAILED , null);
                }
                throwExceptionIfCancelled(con);
                // step 2: deletions
                publishProgress(con, R.string.sync_deletions_message);

                Timber.i("Sync: collection removed data");
                JSONObject lrem = removed();
                JSONObject o = new JSONObject();
                o.put("minUsn", mMinUsn);
                o.put("lnewer", mLNewer);
                o.put("graves", lrem);

                Timber.i("Sync: sending and receiving removed data");
                JSONObject rrem = mRemoteServer.start(o);
                Timber.i("Sync: applying removed data");
                throwExceptionIfCancelled(con);
                remove(rrem);
                // ... and small objects
                publishProgress(con, R.string.sync_small_objects_message);

                Timber.i("Sync: collection small changes");
                JSONObject lchg = changes();
                JSONObject sch = new JSONObject();
                sch.put("changes", lchg);

                Timber.i("Sync: sending and receiving small changes");
                JSONObject rchg = mRemoteServer.applyChanges(sch);
                throwExceptionIfCancelled(con);
                Timber.i("Sync: merging small changes");
                try {
                    mergeChanges(lchg, rchg);
                } catch (UnexpectedSchemaChange e) {
                    Timber.w(e);
                    mRemoteServer.abort();
                    _forceFullSync();
                }
                // step 3: stream large tables from server
                publishProgress(con, R.string.sync_download_chunk);
                while (true) {
                    throwExceptionIfCancelled(con);
                    Timber.i("Sync: downloading chunked data");
                    JSONObject chunk = mRemoteServer.chunk();
                    mCol.log("server chunk", chunk);
                    Timber.i("Sync: applying chunked data");
                    applyChunk(chunk);
                    if (chunk.getBoolean("done")) {
                        break;
                    }
                }
                // step 4: stream to server
                publishProgress(con, R.string.sync_upload_chunk);
                while (true) {
                    throwExceptionIfCancelled(con);
                    Timber.i("Sync: collecting chunked data");
                    JSONObject chunk = chunk();
                    mCol.log("client chunk", chunk);
                    JSONObject sech = new JSONObject();
                    sech.put("chunk", chunk);
                    Timber.i("Sync: sending chunked data");
                    mRemoteServer.applyChunk(sech);
                    if (chunk.getBoolean("done")) {
                        break;
                    }
                }
                // step 5: sanity check
                JSONObject c = sanityCheck();
                JSONObject sanity = mRemoteServer.sanityCheck2(c);
                if (sanity == null || !"ok".equals(sanity.optString("status", "bad"))) {
                    return sanityCheckError(c, sanity);
                }
                // finalize
                publishProgress(con, R.string.sync_finish_message);
                Timber.i("Sync: sending finish command");
                long mod = mRemoteServer.finish();
                if (mod == 0) {
                    return new Pair<>( FINISH_ERROR , null);
                }
                Timber.i("Sync: finishing");
                finish(mod);

                publishProgress(con, R.string.sync_writing_db);
                mCol.getDb().getDatabase().setTransactionSuccessful();
            } finally {
                DB.safeEndInTransaction(mCol.getDb());
            }
        } catch (IllegalStateException e) {
            throw new RuntimeException(e);
        } catch (OutOfMemoryError e) {
            AnkiDroidApp.sendExceptionReport(e, "Syncer-sync");
            Timber.w(e);
            return new Pair<>( OUT_OF_MEMORY_ERROR , null);
        } catch (IOException e) {
            AnkiDroidApp.sendExceptionReport(e, "Syncer-sync");
            Timber.w(e);
            return new Pair<>( IO_EXCEPTION , null);
        }
        return new Pair<>( SUCCESS , null);
    }


    private void trySetHostNum(JSONObject rMeta) {
        //We perform this as old version of the sync server may not provide the hostNum
        //And it's fine to continue without one.
        try {
            if (rMeta.has("hostNum")) {
                mHostNum.setHostNum(rMeta.getInt("hostNum"));
            }
        } catch (Exception e) {
            Timber.w(e, "Failed to set hostNum");
        }
    }

    @NonNull
    protected Pair<ConnectionResultType, Object> sanityCheckError(JSONObject c, JSONObject sanity) {
        mCol.log("sanity check failed", c, sanity);
        UsageAnalytics.sendAnalyticsEvent(UsageAnalytics.Category.SYNC, "sanityCheckError");
        _forceFullSync();
        return new Pair<> (SANITY_CHECK_ERROR, null);
    }

    private void _forceFullSync() {
        // roll back and force full sync
        mCol.modSchemaNoCheck();
        mCol.save();
    }

    private void publishProgress(Connection con, int id) {
        if (con != null) {
            con.publishProgress(id);
        }
    }


    public JSONObject meta() throws JSONException {
        JSONObject j = new JSONObject();
        j.put("mod", mCol.getMod());
        j.put("scm", mCol.getScm());
        j.put("usn", mCol.getUsnForSync());
        j.put("ts", mCol.getTime().intTime());
        j.put("musn", 0);
        j.put("msg", "");
        j.put("cont", true);
        return j;
    }


    /** Bundle up small objects. */
    public JSONObject changes() {
        JSONObject o = new JSONObject();
        o.put("models", getModels());
        o.put("decks", getDecks());
        o.put("tags", getTags());
        if (mLNewer) {
            o.put("conf", getConf());
            o.put("crt", mCol.getCrt());
        }
        return o;
    }


    public JSONObject applyChanges(JSONObject changes) throws UnexpectedSchemaChange {
        JSONObject lchg = changes();
        // merge our side before returning
        mergeChanges(lchg, changes);
        return lchg;
    }


    public void mergeChanges(JSONObject lchg, JSONObject rchg) throws UnexpectedSchemaChange {
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
        prepareToChunk();
    }


    public JSONObject sanityCheck() {
        JSONObject result = new JSONObject();
        try {
            if (mCol.getDb().queryScalar("SELECT count() FROM cards WHERE nid NOT IN (SELECT id FROM notes)") != 0) {
                Timber.e("Sync - SanityCheck: there are cards without mother notes");
                result.put("client", "missing notes");
                return result;
            }
            if (mCol.getDb().queryScalar("SELECT count() FROM notes WHERE id NOT IN (SELECT DISTINCT nid FROM cards)") != 0) {
                Timber.e("Sync - SanityCheck: there are notes without cards");
                result.put("client", "missing cards");
                return result;
            }
            if (mCol.getDb().queryScalar("SELECT count() FROM cards WHERE usn = -1") != 0) {
                Timber.e("Sync - SanityCheck: there are unsynced cards");
                result.put("client", "cards had usn = -1");
                return result;
            }
            if (mCol.getDb().queryScalar("SELECT count() FROM notes WHERE usn = -1") != 0) {
                Timber.e("Sync - SanityCheck: there are unsynced notes");
                result.put("client", "notes had usn = -1");
                return result;
            }
            if (mCol.getDb().queryScalar("SELECT count() FROM revlog WHERE usn = -1") != 0) {
                Timber.e("Sync - SanityCheck: there are unsynced revlogs");
                result.put("client", "revlog had usn = -1");
                return result;
            }
            if (mCol.getDb().queryScalar("SELECT count() FROM graves WHERE usn = -1") != 0) {
                Timber.e("Sync - SanityCheck: there are unsynced graves");
                result.put("client", "graves had usn = -1");
                return result;
            }
            for (Deck g : mCol.getDecks().all()) {
                if (g.getInt("usn") == -1) {
                    Timber.e("Sync - SanityCheck: unsynced deck: %s", g.getString("name"));
                    result.put("client", "deck had usn = -1");
                    return result;
                }
            }
            if (mCol.getTags().minusOneValue()) {
                Timber.e("Sync - SanityCheck: there are unsynced tags");
                result.put("client", "tag had usn = -1");
                return result;
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
                        Timber.e("Sync - SanityCheck: unsynced model: %s", m.getString("name"));
                        result.put("client", "model had usn = -1");
                        return result;
                    }
                }
            }
            if (found) {
                mCol.getModels().save();
            }
            // check for missing parent decks
            mCol.getSched().deckDueList();
            // return summary of deck
            JSONArray check = new JSONArray();
            JSONArray counts = new JSONArray();

            //#5666 - not in libAnki
            //We modified mReportLimit inside the scheduler, and this causes issues syncing dynamic decks.
            AbstractSched syncScheduler = mCol.createScheduler(SYNC_SCHEDULER_REPORT_LIMIT);
            syncScheduler.resetCounts();
            Counts counts_ = syncScheduler.counts();
            counts.put(counts_.getNew());
            counts.put(counts_.getLrn());
            counts.put(counts_.getRev());
            check.put(counts);
            check.put(mCol.getDb().queryScalar("SELECT count() FROM cards"));
            check.put(mCol.getDb().queryScalar("SELECT count() FROM notes"));
            check.put(mCol.getDb().queryScalar("SELECT count() FROM revlog"));
            check.put(mCol.getDb().queryScalar("SELECT count() FROM graves"));
            check.put(mCol.getModels().all().size());
            check.put(mCol.getDecks().all().size());
            check.put(mCol.getDecks().allConf().size());
            result.put("client", check);
            return result;
        } catch (JSONException e) {
            Timber.e(e, "Syncer.sanityCheck()");
            throw new RuntimeException(e);
        }
    }


    // private Map<String, Object> sanityCheck2(JSONArray client) {
    // Object server = sanityCheck();
    // Map<String, Object> result = new HashMap<String, Object>();
    // if (client.equals(server)) {
    // result.put("status", "ok");
    // } else {
    // result.put("status", "bad");
    // result.put("c", client);
    // result.put("s", server);
    // }
    // return result;
    // }

    private Pair<String, Object[]> usnLim() {
        if (mCol.getServer()) {
            return new Pair<>("usn >= ?", new Object[] {mMinUsn});
        } else {
            return new Pair<>("usn = -1", null);
        }
    }


    public long finish() {
        return finish(0);
    }


    private long finish(long mod) {
        if (mod == 0) {
            // server side; we decide new mod time
            mod = mCol.getTime().intTimeMS();
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
        mTablesLeft = new LinkedList<>();
        mTablesLeft.add("revlog");
        mTablesLeft.add("cards");
        mTablesLeft.add("notes");
        mCursor = null;
    }


    private Cursor cursorForTable(String table) {
        Pair<String, Object[]> limAndArg = usnLim();
        if ("revlog".equals(table)) {
            return mCol
                    .getDb()
                    .query("SELECT id, cid, " + mMaxUsn + ", ease, ivl, lastIvl, factor, time, type FROM revlog WHERE " + limAndArg.first, limAndArg.second);
        } else if ("cards".equals(table)) {
            return mCol
                    .getDb()
                    .query("SELECT id, nid, did, ord, mod, " + mMaxUsn + ", type, queue, due, ivl, factor, reps, lapses, left, odue, odid, flags, data FROM cards WHERE " + limAndArg.first, limAndArg.second);
        } else {
            return mCol
                    .getDb()
                    .query("SELECT id, guid, mid, mod, " + mMaxUsn + ", tags, flds, '', '', flags, data FROM notes WHERE " + limAndArg.first, limAndArg.second);
        }
    }


    private List<Integer> columnTypesForQuery(String table) {
        if ("revlog".equals(table)) {
            return Arrays.asList(TYPE_INTEGER, TYPE_INTEGER, TYPE_INTEGER, TYPE_INTEGER, TYPE_INTEGER, TYPE_INTEGER,
                    TYPE_INTEGER, TYPE_INTEGER, TYPE_INTEGER);
        } else if ("cards".equals(table)) {
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
        buf.put("done", false);
        int lim = 250;
        List<Integer> colTypes = null;
        while (!mTablesLeft.isEmpty() && lim > 0) {
            String curTable = mTablesLeft.getFirst();
            if (mCursor == null) {
                mCursor = cursorForTable(curTable);
            }
            colTypes = columnTypesForQuery(curTable);
            JSONArray rows = new JSONArray();
            int count = mCursor.getColumnCount();
            int fetched = 0;
            while (mCursor.moveToNext()) {
                JSONArray r = new JSONArray();
                for (int i = 0; i < count; i++) {
                    switch (colTypes.get(i)) {
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
                if (++fetched == lim) {
                    break;
                }
            }
            if (fetched != lim) {
                // table is empty
                mTablesLeft.removeFirst();
                mCursor.close();
                mCursor = null;
                // if we're the client, mark the objects as having been sent
                if (!mCol.getServer()) {
                    mCol.getDb().execute("UPDATE " + curTable + " SET usn=? WHERE usn=-1", mMaxUsn);
                }
            }
            buf.put(curTable, rows);
            lim -= fetched;
        }
        if (mTablesLeft.isEmpty()) {
            buf.put("done", true);
        }
        return buf;
    }


    public void applyChunk(JSONObject chunk) {
        if (chunk.has("revlog")) {
            mergeRevlog(chunk.getJSONArray("revlog"));
        }
        if (chunk.has("cards")) {
            mergeCards(chunk.getJSONArray("cards"));
        }
        if (chunk.has("notes")) {
            mergeNotes(chunk.getJSONArray("notes"));
        }
    }


    /**
     * Deletions ********************************************************************
     */

    private JSONObject removed() {
        JSONArray cards = new JSONArray();
        JSONArray notes = new JSONArray();
        JSONArray decks = new JSONArray();
        Pair<String, Object[]> limAndArgs = usnLim();
        try (Cursor cur = mCol
                    .getDb()
                    .query("SELECT oid, type FROM graves WHERE " + limAndArgs.first, limAndArgs.second)) {
            while (cur.moveToNext()) {
                @Consts.REM_TYPE int type = cur.getInt(1);
                switch (type) {
                    case Consts.REM_CARD:
                        cards.put(cur.getLong(0));
                        break;
                    case Consts.REM_NOTE:
                        notes.put(cur.getLong(0));
                        break;
                    case Consts.REM_DECK:
                        decks.put(cur.getLong(0));
                        break;
                }
            }
        }
        if (!mCol.getServer()) {
            mCol.getDb().execute("UPDATE graves SET usn=" + mMaxUsn + " WHERE usn=-1");
        }
        JSONObject o = new JSONObject();
        o.put("cards", cards);
        o.put("notes", notes);
        o.put("decks", decks);
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
        // notes first, so we don't end up with duplicate graves
        mCol._remNotes(graves.getJSONArray("notes").toLongList());
        // then cards
        mCol.remCards(graves.getJSONArray("cards").toLongList(), false);
        // and decks
        JSONArray decks = graves.getJSONArray("decks");
        for (Long did: decks.longIterable()) {
            mCol.getDecks().rem(did, false, false);
        }
        mCol.setServer(wasServer);
    }


    /**
     * Models ********************************************************************
     */

    private JSONArray getModels() {
        JSONArray result = new JSONArray();
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
        return result;
    }


    private void mergeModels(JSONArray rchg) throws UnexpectedSchemaChange {
        for (JSONObject model: rchg.jsonObjectIterable()) {
            Model r = new Model(model);
            Model l = mCol.getModels().get(r.getLong("id"));
            // if missing locally or server is newer, update
            if (l == null || r.getLong("mod") > l.getLong("mod")) {
                // This is a hack to detect when the note type has been altered
                // in an import without a full sync being forced. A future
                // syncing algorithm should handle this in a better way.
                if (l != null) {
                    if (l.getJSONArray("flds").length() != r.getJSONArray("flds").length()) {
                        throw new UnexpectedSchemaChange();
                    }
                    if (l.getJSONArray("tmpls").length() != r.getJSONArray("tmpls").length()) {
                        throw new UnexpectedSchemaChange();
                    }
                }
                mCol.getModels().update(r);
            }
        }
    }


    /**
     * Decks ********************************************************************
     */

    private JSONArray getDecks() {
        JSONArray result = new JSONArray();
        if (mCol.getServer()) {
            JSONArray decks = new JSONArray();
            for (Deck g : mCol.getDecks().all()) {
                if (g.getInt("usn") >= mMinUsn) {
                    decks.put(g);
                }
            }
            JSONArray dconfs = new JSONArray();
            for (DeckConfig g : mCol.getDecks().allConf()) {
                if (g.getInt("usn") >= mMinUsn) {
                    dconfs.put(g);
                }
            }
            result.put(decks);
            result.put(dconfs);
        } else {
            JSONArray decks = new JSONArray();
            for (Deck g : mCol.getDecks().all()) {
                if (g.getInt("usn") == -1) {
                    g.put("usn", mMaxUsn);
                    decks.put(g);
                }
            }
            JSONArray dconfs = new JSONArray();
            for (DeckConfig g : mCol.getDecks().allConf()) {
                if (g.getInt("usn") == -1) {
                    g.put("usn", mMaxUsn);
                    dconfs.put(g);
                }
            }
            mCol.getDecks().save();
            result.put(decks);
            result.put(dconfs);
        }
        return result;
    }


    private void mergeDecks(JSONArray rchg) {
        JSONArray decks = rchg.getJSONArray(0);
        for (JSONObject deck: decks.jsonObjectIterable()) {
            Deck r = new Deck(deck);
            Deck l = mCol.getDecks().get(r.getLong("id"), false);
            // if missing locally or server is newer, update
            if (l == null || r.getLong("mod") > l.getLong("mod")) {
                mCol.getDecks().update(r);
            }
        }
        JSONArray confs = rchg.getJSONArray(1);
        for (JSONObject deckConfig: confs.jsonObjectIterable()) {
            DeckConfig r = new DeckConfig(deckConfig, DeckConfig.Source.DECK_CONFIG);
            DeckConfig l = mCol.getDecks().getConf(r.getLong("id"));
            // if missing locally or server is newer, update
            if (l == null || r.getLong("mod") > l.getLong("mod")) {
                mCol.getDecks().updateConf(r);
            }
        }
    }


    /**
     * Tags ********************************************************************
     */

    private JSONArray getTags() {
        JSONArray result = new JSONArray();
        if (mCol.getServer()) {
            for (TagUsnTuple t : mCol.getTags().allItems()) {
                if (t.getUsn() >= mMinUsn) {
                    result.put(t.getTag());
                }
            }
        } else {
            for (TagUsnTuple t : mCol.getTags().allItems()) {
                if (t.getUsn() == -1) {
                    String tag = t.getTag();
                    mCol.getTags().add(t.getTag(), mMaxUsn);
                    result.put(tag);
                }
            }
            mCol.getTags().save();
        }
        return result;
    }


    private void mergeTags(JSONArray tags) {
        mCol.getTags().register(tags.toStringList(), mMaxUsn);
    }


    /**
     * Cards/notes/revlog ********************************************************************
     */

    private void mergeRevlog(JSONArray logs) {
        for (JSONArray log: logs.jsonArrayIterable()) {
            try {
                mCol.getDb().execute("INSERT OR IGNORE INTO revlog VALUES (?,?,?,?,?,?,?,?,?)",
                        Utils.jsonArray2Objects(log));
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }

    }


    private ArrayList<Object[]> newerRows(JSONArray data, String table, int modIdx) {
        long[] ids = new long[data.length()];
        for (int i = 0; i < data.length(); i++) {
            ids[i] = data.getJSONArray(i).getLong(0);
        }
        Pair<String, Object[]> limAndArg = usnLim();
        Map<Long, Long> lmods = HashUtil.HashMapInit(mCol
                    .getDb()
                    .queryScalar(
                            "SELECT count() FROM " + table + " WHERE id IN " + Utils.ids2str(ids) + " AND "
                                    +  limAndArg.first, limAndArg.second));
        try (Cursor cur = mCol
                    .getDb()
                    .query(
                            "SELECT id, mod FROM " + table + " WHERE id IN " + Utils.ids2str(ids) + " AND "
                                    +  limAndArg.first, limAndArg.second)) {
            while (cur.moveToNext()) {
                lmods.put(cur.getLong(0), cur.getLong(1));
            }
        }
        ArrayList<Object[]> update = new ArrayList<>(data.length());
        for (JSONArray r: data.jsonArrayIterable()) {
            if (!lmods.containsKey(r.getLong(0)) || lmods.get(r.getLong(0)) < r.getLong(modIdx)) {
                update.add(Utils.jsonArray2Objects(r));
            }
        }
        mCol.log(table, data);
        return update;
    }


    private void mergeCards(JSONArray cards) {
        for (Object[] r : newerRows(cards, "cards", 4)) {
            mCol.getDb().execute("INSERT OR REPLACE INTO cards VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)", r);
        }
    }


    private void mergeNotes(JSONArray notes) {
        for (Object[] n : newerRows(notes, "notes", 4)) {
            mCol.getDb().execute("INSERT OR REPLACE INTO notes VALUES (?,?,?,?,?,?,?,?,?,?,?)", n);
            mCol.updateFieldCache(new long[]{((Number) n[0]).longValue()});
        }
    }


    public String getSyncMsg() {
        return mSyncMsg;
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

    /**
     * If the user asked to cancel the sync then we just throw a Runtime exception which should be gracefully handled
     * @param con
     */
    private void throwExceptionIfCancelled(Connection con) {
        if (Connection.getIsCancelled()) {
            Timber.i("Sync was cancelled");
            publishProgress(con, R.string.sync_cancelled);
            try {
                mRemoteServer.abort();
            } catch (UnknownHttpResponseException ignored) {
                Timber.w(ignored);
            }
            throw new RuntimeException(USER_ABORTED_SYNC.toString());
        }
    }

    private static class UnexpectedSchemaChange extends Exception {
    }

}
