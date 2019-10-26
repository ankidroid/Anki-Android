/****************************************************************************************
 * Copyright (c) 2011 Norbert Nagold <norbert.nagold@gmail.com>                         *
 * Copyright (c) 2012 Kostas Spyropoulos <inigo.aldana@gmail.com>                       *
 *                                                                                      *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General private License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General private License for more details.             *
 *                                                                                      *
 * You should have received a copy of the GNU General private License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/

package com.ichi2.libanki;

import android.content.ContentValues;
import android.content.Context;
import android.content.res.Resources;
import android.database.Cursor;
import android.text.TextUtils;
import android.util.Pair;

import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.CardUtils;
import com.ichi2.anki.R;
import com.ichi2.anki.UIUtils;
import com.ichi2.anki.analytics.UsageAnalytics;
import com.ichi2.anki.exception.ConfirmModSchemaException;
import com.ichi2.async.DeckTask;
import com.ichi2.compat.CompatHelper;
import com.ichi2.libanki.hooks.Hooks;
import com.ichi2.libanki.template.Template;
import com.ichi2.utils.VersionUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.regex.Pattern;

import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteStatement;
import timber.log.Timber;

// Anki maintains a cache of used tags so it can quickly present a list of tags
// for autocomplete and in the browser. For efficiency, deletions are not
// tracked, so unused tags can only be removed from the list with a DB check.
//
// This module manages the tag cache and tags for notes.

@SuppressWarnings({"PMD.ExcessiveClassLength", "PMD.AvoidThrowingRawExceptionTypes","PMD.AvoidReassigningParameters",
        "PMD.NPathComplexity","PMD.MethodNamingConventions","PMD.AvoidBranchingStatementAsLastInLoop",
        "PMD.SwitchStmtsShouldHaveDefault","PMD.CollapsibleIfStatements","PMD.EmptyIfStmt","PMD.ExcessiveMethodLength"})
public class Collection {

    private Context mContext;

    private DB mDb;
    private boolean mServer;
    //private double mLastSave;
    private Media mMedia;
    private Decks mDecks;
    private Models mModels;
    private Tags mTags;

    private Sched mSched;

    private double mStartTime;
    private int mStartReps;

    // BEGIN: SQL table columns
    private long mCrt;
    private long mMod;
    private long mScm;
    private boolean mDty;
    private int mUsn;
    private long mLs;
    private JSONObject mConf;
    // END: SQL table columns

    private LinkedList<Object[]> mUndo;

    private String mPath;
    private boolean mDebugLog;
    private PrintWriter mLogHnd;

    private static final Pattern fClozePatternQ = Pattern.compile("\\{\\{(?!type:)(.*?)cloze:");
    private static final Pattern fClozePatternA = Pattern.compile("\\{\\{(.*?)cloze:");
    private static final Pattern fClozeTagStart = Pattern.compile("<%cloze:");

    private static final int fDefaultSchedulerVersion = 1;
    private static final List<Integer> fSupportedSchedulerVersions = Arrays.asList(1, 2);

    // other options
    public static final String defaultConf = "{"
            +
            // review options
            "'activeDecks': [1], " + "'curDeck': 1, " + "'newSpread': " + Consts.NEW_CARDS_DISTRIBUTE + ", "
            + "'collapseTime': 1200, " + "'timeLim': 0, " + "'estTimes': True, " + "'dueCounts': True, "
            +
            // other config
            "'curModel': null, " + "'nextPos': 1, " + "'sortType': \"noteFld\", "
            + "'sortBackwards': False, 'addToCur': True }"; // add new to currently selected deck?

    public enum DismissType {
        REVIEW(R.string.undo_action_review),
        BURY_CARD(R.string.undo_action_bury_card),
        BURY_NOTE(R.string.undo_action_bury_note),
        SUSPEND_CARD(R.string.undo_action_suspend_card),
        SUSPEND_CARD_MULTI(R.string.card_browser_toggle_suspend_card),
        SUSPEND_NOTE(R.string.undo_action_suspend_note),
        DELETE_NOTE(R.string.undo_action_delete),
        DELETE_NOTE_MULTI(R.string.undo_action_delete_multi),
        CHANGE_DECK_MULTI(R.string.undo_action_change_deck_multi),
        MARK_NOTE_MULTI(R.string.card_browser_toggle_mark_card),
        REPOSITION_CARDS(R.string.undo_action_reposition_card),
        RESCHEDULE_CARDS(R.string.undo_action_reschedule_card),
        RESET_CARDS(R.string.undo_action_reset_card);

        public int undoNameId;

        DismissType(int undoNameId) {
            this.undoNameId = undoNameId;
        }
    }

    private static final int UNDO_SIZE_MAX = 20;

    public Collection(Context context, DB db, String path) {
        this(context, db, path, false);
    }

    public Collection(Context context, DB db, String path, boolean server) {
        this(context, db, path, server, false);
    }

    public Collection(Context context, DB db, String path, boolean server, boolean log) {
        mContext = context;
        mDebugLog = log;
        mDb = db;
        mPath = path;
        _openLog();
        log(path, VersionUtils.getPkgVersionName());
        mServer = server;
        //mLastSave = Utils.now(); // assigned but never accessed - only leaving in for upstream comparison
        clearUndo();
        mMedia = new Media(this, server);
        mModels = new Models(this);
        mDecks = new Decks(this);
        mTags = new Tags(this);
        load();
        if (mCrt == 0) {
            mCrt = UIUtils.getDayStart() / 1000;
        }
        mStartReps = 0;
        mStartTime = 0;
        _loadScheduler();
        if (!mConf.optBoolean("newBury", false)) {
            try {
                mConf.put("newBury", true);
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
            setMod();
        }
    }


    public String name() {
        String n = (new File(mPath)).getName().replace(".anki2", "");
        // TODO:
        return n;
    }


    /**
     * Scheduler
     * ***********************************************************
     */


    public int schedVer() {
        int ver = mConf.optInt("schedVer", fDefaultSchedulerVersion);
        if (fSupportedSchedulerVersions.contains(ver)) {
            return ver;
        } else {
            throw new RuntimeException("Unsupported scheduler version");
        }
    }

    private void _loadScheduler() {
        int ver = schedVer();
        if (ver == 1) {
            mSched = new Sched(this);
        } else if (ver == 2) {
            mSched = new SchedV2(this);
        }
    }

    public void changeSchedulerVer(Integer ver) throws ConfirmModSchemaException {
        if (ver == schedVer()) {
            return;
        }
        if (!fSupportedSchedulerVersions.contains(ver)) {
            throw new RuntimeException("Unsupported scheduler version");
        }
        modSchema(true);
        SchedV2 v2Sched = new SchedV2(this);
        clearUndo();
        if (ver == 1) {
            v2Sched.moveToV1();
        } else {
            v2Sched.moveToV2();
        }
        try {
            mConf.put("schedVer", ver);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        setMod();
        _loadScheduler();
    }


    /**
     * DB-related *************************************************************** ********************************
     */

    public void load() {
        Cursor cursor = null;
        String deckConf = "";
        try {
            // Read in deck table columns
            cursor = mDb.getDatabase().query(
                    "SELECT crt, mod, scm, dty, usn, ls, " +
                    "conf, dconf, tags FROM col", null);
            if (!cursor.moveToFirst()) {
                return;
            }
            mCrt = cursor.getLong(0);
            mMod = cursor.getLong(1);
            mScm = cursor.getLong(2);
            mDty = cursor.getInt(3) == 1; // No longer used
            mUsn = cursor.getInt(4);
            mLs = cursor.getLong(5);
            try {
                mConf = new JSONObject(cursor.getString(6));
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
            deckConf = cursor.getString(7);
            mTags.load(cursor.getString(8));
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        mModels.load(loadColumn("models"));
        mDecks.load(loadColumn("decks"), deckConf);
    }

    public String loadColumn(String columnName) {
        int pos = 1;
        int chunk = 256*1024;
        StringBuffer buf = new StringBuffer("");

        while (true) {
            Cursor cursor = null;
            try {
                cursor = mDb.getDatabase().query(
                        "SELECT substr(" + columnName + ", ?, ?) FROM col",
                        new String[]{Integer.toString(pos), Integer.toString(chunk)});
                if (!cursor.moveToFirst()) {
                    return buf.toString();
                }
                String res = cursor.getString(0);
                if (res.length() == 0) {
                      break;
                }
                buf.append(res);
                if (res.length() < chunk) {
                    break;
                }
                pos += chunk;
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        }
        return buf.toString();
    }

    /**
     * Mark DB modified. DB operations and the deck/tag/model managers do this automatically, so this is only necessary
     * if you modify properties of this object or the conf dict.
     */
    public void setMod() {
        mDb.setMod(true);
    }


    public void flush() {
        flush(0);
    }


    /**
     * Flush state to DB, updating mod time.
     */
    public void flush(long mod) {
        Timber.i("flush - Saving information to DB...");
        mMod = (mod == 0 ? Utils.intTime(1000) : mod);
        ContentValues values = new ContentValues();
        values.put("crt", mCrt);
        values.put("mod", mMod);
        values.put("scm", mScm);
        values.put("dty", mDty ? 1 : 0);
        values.put("usn", mUsn);
        values.put("ls", mLs);
        values.put("conf", Utils.jsonToString(mConf));
        mDb.update("col", values);
    }


    /**
     * Flush, commit DB, and take out another write lock.
     */
    public synchronized void save() {
        save(null, 0);
    }


    public synchronized void save(long mod) {
        save(null, mod);
    }


    public synchronized void save(String name, long mod) {
        // let the managers conditionally flush
        mModels.flush();
        mDecks.flush();
        mTags.flush();
        // and flush deck + bump mod if db has been changed
        if (mDb.getMod()) {
            flush(mod);
            mDb.commit();
            lock();
            mDb.setMod(false);
        }
        // undoing non review operation is handled differently in ankidroid
//        _markOp(name);
        //mLastSave = Utils.now(); // assigned but never accessed - only leaving in for upstream comparison
    }


    /** make sure we don't accidentally bump mod time */
    public void lock() {
        // make sure we don't accidentally bump mod time
        boolean mod = mDb.getMod();
        mDb.execute("UPDATE col SET mod=mod");
        mDb.setMod(mod);
    }


    /**
     * Disconnect from DB.
     */
    public synchronized void close() {
        close(true);
    }


    public synchronized void close(boolean save) {
        if (mDb != null) {
            try {
                SupportSQLiteDatabase db = mDb.getDatabase();
                if (save) {
                    db.beginTransaction();
                    try {
                        save();
                        db.setTransactionSuccessful();
                    } finally {
                        db.endTransaction();
                    }
                } else {
                    if (db.inTransaction()) {
                        db.endTransaction();
                    }
                }
            } catch (RuntimeException e) {
                AnkiDroidApp.sendExceptionReport(e, "closeDB");
            }
            if (!mServer) {
                CompatHelper.getCompat().disableDatabaseWriteAheadLogging(mDb.getDatabase());
            }
            mDb.close();
            mDb = null;
            mMedia.close();
            _closeLog();
            Timber.i("Collection closed");
        }
    }


    public void reopen() {
        if (mDb == null) {
            mDb = new DB(mPath);
            mMedia.connect();
            _openLog();
        }
    }


    /** Note: not in libanki.
     * Mark schema modified to force a full sync, but with the confirmation checking function disabled
     * This is a convenience method which doesn't throw ConfirmModSchemaException
     */
    public void modSchemaNoCheck() {
        mScm = Utils.intTime(1000);
        setMod();
    }

    /** Mark schema modified to force a full sync.
     * ConfirmModSchemaException will be thrown if the user needs to be prompted to confirm the action.
     * If the user chooses to confirm then modSchema(false) should be called, after which the exception can
     * be safely ignored, and the outer code called again.
     *
     * @throws ConfirmModSchemaException */
    public void modSchema() throws ConfirmModSchemaException {
        modSchema(true);
    }

    /** Mark schema modified to force a full sync.
     * If check==true and the schema has not already been marked modified then ConfirmModSchemaException will be thrown.
     * If the user chooses to confirm then modSchema(false) should be called, after which the exception can
     * be safely ignored, and the outer code called again.
     *
     * @param check
     * @throws ConfirmModSchemaException
     */
    public void modSchema(boolean check) throws ConfirmModSchemaException {
        if (!schemaChanged()) {
            if (check) {
                /* In Android we can't show a dialog which blocks the main UI thread
                 Therefore we can't wait for the user to confirm if they want to do
                 a full sync here, and we instead throw an exception asking the outer
                 code to handle the user's choice */
                throw new ConfirmModSchemaException();
            }
        }
        modSchemaNoCheck();
    }


    /** True if schema changed since last sync. */
    public boolean schemaChanged() {
        return mScm > mLs;
    }


    public int usn() {
        if (mServer) {
            return mUsn;
        } else {
            return -1;
        }
    }


    /** called before a full upload */
    public void beforeUpload() {
        String[] tables = new String[] { "notes", "cards", "revlog" };
        for (String t : tables) {
            mDb.execute("UPDATE " + t + " SET usn=0 WHERE usn=-1");
        }
        // we can save space by removing the log of deletions
        mDb.execute("delete from graves");
        mUsn += 1;
        mModels.beforeUpload();
        mTags.beforeUpload();
        mDecks.beforeUpload();
        modSchemaNoCheck();
        mLs = mScm;
        // ensure db is compacted before upload
        mDb.execute("vacuum");
        mDb.execute("analyze");
        close();
    }


    /**
     * Object creation helpers **************************************************
     * *********************************************
     */

    public Card getCard(long id) {
        return new Card(this, id);
    }


    public Note getNote(long id) {
        return new Note(this, id);
    }


    /**
     * Utils ******************************************************************** ***************************
     */

    public int nextID(String type) {
        type = "next" + Character.toUpperCase(type.charAt(0)) + type.substring(1);
        int id;
        try {
            id = mConf.getInt(type);
        } catch (JSONException e) {
            id = 1;
        }
        try {
            mConf.put(type, id + 1);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        return id;
    }


    /**
     * Rebuild the queue and reload data after DB modified.
     */
    public void reset() {
        mSched.reset();
    }


    /**
     * Deletion logging ********************************************************* **************************************
     */

    public void _logRem(long[] ids, int type) {
        for (long id : ids) {
            ContentValues values = new ContentValues();
            values.put("usn", usn());
            values.put("oid", id);
            values.put("type", type);
            mDb.insert("graves", values);
        }
    }


    /**
     * Notes ******************************************************************** ***************************
     */

    public int noteCount() {
        return mDb.queryScalar("SELECT count() FROM notes");
    }

    /**
     * Return a new note with the default model from the deck
     * @return The new note
     */
    public Note newNote() {
        return newNote(true);
    }

    /**
     * Return a new note with the model derived from the deck or the configuration
     * @param forDeck When true it uses the model specified in the deck (mid), otherwise it uses the model specified in
     *                the configuration (curModel)
     * @return The new note
     */
    public Note newNote(boolean forDeck) {
        return newNote(mModels.current(forDeck));
    }

    /**
     * Return a new note with a specific model
     * @param m The model to use for the new note
     * @return The new note
     */
    public Note newNote(JSONObject m) {
        return new Note(this, m);
    }


    /**
     * Add a note to the collection. Return number of new cards.
     */
    public int addNote(Note note) {
        // check we have card models available, then save
        ArrayList<JSONObject> cms = findTemplates(note);
        if (cms.size() == 0) {
            return 0;
        }
        note.flush();
        // deck conf governs which of these are used
        int due = nextID("pos");
        // add cards
        int ncards = 0;
        for (JSONObject template : cms) {
            _newCard(note, template, due);
            ncards += 1;
        }
        return ncards;
    }


    public void remNotes(long[] ids) {
        ArrayList<Long> list = mDb
                .queryColumn(Long.class, "SELECT id FROM cards WHERE nid IN " + Utils.ids2str(ids), 0);
        long[] cids = new long[list.size()];
        int i = 0;
        for (long l : list) {
            cids[i++] = l;
        }
        remCards(cids);
    }


    /**
     * Bulk delete facts by ID. Don't call this directly.
     */
    public void _remNotes(long[] ids) {
        if (ids.length == 0) {
            return;
        }
        String strids = Utils.ids2str(ids);
        // we need to log these independently of cards, as one side may have
        // more card templates
        _logRem(ids, Consts.REM_NOTE);
        mDb.execute("DELETE FROM notes WHERE id IN " + strids);
    }


    /**
     * Card creation ************************************************************ ***********************************
     */

    /**
     * @return (active), non-empty templates.
     */
    public ArrayList<JSONObject> findTemplates(Note note) {
        JSONObject model = note.model();
        ArrayList<Integer> avail = mModels.availOrds(model, Utils.joinFields(note.getFields()));
        return _tmplsFromOrds(model, avail);
    }


    private ArrayList<JSONObject> _tmplsFromOrds(JSONObject model, ArrayList<Integer> avail) {
        ArrayList<JSONObject> ok = new ArrayList<>();
        JSONArray tmpls;
        try {
            if (model.getInt("type") == Consts.MODEL_STD) {
                tmpls = model.getJSONArray("tmpls");
                for (int i = 0; i < tmpls.length(); i++) {
                    JSONObject t = tmpls.getJSONObject(i);
                    if (avail.contains(t.getInt("ord"))) {
                        ok.add(t);
                    }
                }
            } else {
                // cloze - generate temporary templates from first
                for (int ord : avail) {
                    JSONObject t = new JSONObject(model.getJSONArray("tmpls").getString(0));
                    t.put("ord", ord);
                    ok.add(t);
                }
            }
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        return ok;
    }


    /**
     * Generate cards for non-empty templates, return ids to remove.
     */
	public ArrayList<Long> genCards(List<Long> nids) {
	    return genCards(Utils.arrayList2array(nids));
	}
    public ArrayList<Long> genCards(long[] nids) {
        // build map of (nid,ord) so we don't create dupes
        String snids = Utils.ids2str(nids);
        HashMap<Long, HashMap<Integer, Long>> have = new HashMap<>();
        HashMap<Long, Long> dids = new HashMap<>();
        HashMap<Long, Long> dues = new HashMap<>();
        Cursor cur = null;
        try {
            cur = mDb.getDatabase().query("select id, nid, ord, did, due, odue, odid, type from cards where nid in " + snids, null);
            while (cur.moveToNext()) {
                long id = cur.getLong(0);
                long nid = cur.getLong(1);
                int ord = cur.getInt(2);
                long did = cur.getLong(3);
                long due = cur.getLong(4);
                long odue = cur.getLong(5);
                long odid = cur.getLong(6);
                long type = cur.getLong(7);

                // existing cards
                if (!have.containsKey(nid)) {
                    have.put(nid, new HashMap<Integer, Long>());
                }
                have.get(nid).put(ord, id);
                // if in a filtered deck, add new cards to original deck
                if (odid != 0) {
                    did = odid;
                }
                // and their dids
                if (dids.containsKey(nid)) {
                    if (dids.get(nid) != 0 && dids.get(nid) != did) {
                        // cards are in two or more different decks; revert to model default
                        dids.put(nid, 0L);
                    }
                } else {
                    // first card or multiple cards in same deck
                    dids.put(nid, did);
                }
                // save due
                if (odid != 0) {
                    due = odue;
                }
                if (!dues.containsKey(nid) && type == 0) {
                    dues.put(nid, due);
                }
            }
        } finally {
            if (cur != null && !cur.isClosed()) {
                cur.close();
            }
        }
        // build cards for each note
        ArrayList<Object[]> data = new ArrayList<>();
        long ts = Utils.maxID(mDb);
        long now = Utils.intTime();
        ArrayList<Long> rem = new ArrayList<>();
        int usn = usn();
        cur = null;
        try {
            cur = mDb.getDatabase().query("SELECT id, mid, flds FROM notes WHERE id IN " + snids, null);
            while (cur.moveToNext()) {
                long nid = cur.getLong(0);
                long mid = cur.getLong(1);
                String flds = cur.getString(2);
                JSONObject model = mModels.get(mid);
                ArrayList<Integer> avail = mModels.availOrds(model, flds);
                long did = dids.get(nid);
                long due = dues.get(nid);
                if (did == 0) {
                    did = model.getLong("did");
                }
                due = dues.get(nid);
                // add any missing cards
                for (JSONObject t : _tmplsFromOrds(model, avail)) {
                    int tord = t.getInt("ord");
                    boolean doHave = have.containsKey(nid) && have.get(nid).containsKey(tord);
                    if (!doHave) {
                        // check deck is not a cram deck
                        long ndid;
                        try {
                            ndid = t.getLong("did");
                            if (ndid != 0) {
                                did = ndid;
                            }
                        } catch (JSONException e) {
                            // do nothing
                        }
                        if (getDecks().isDyn(did)) {
                            did = 1;
                        }
                        // if the deck doesn't exist, use default instead
                        did = mDecks.get(did).getLong("id");
                        // use sibling due if there is one, else use a new id
                        if (due == 0) {
                            due = nextID("pos");
                        }
                        // give it a new id instead
                        data.add(new Object[] { ts, nid, did, tord, now, usn, due});
                        ts += 1;
                    }
                }
                // note any cards that need removing
                if (have.containsKey(nid)) {
                    for (Map.Entry<Integer, Long> n : have.get(nid).entrySet()) {
                        if (!avail.contains(n.getKey())) {
                            rem.add(n.getValue());
                        }
                    }
                }
            }
        } catch (JSONException e) {
            throw new RuntimeException(e);
        } finally {
            if (cur != null && !cur.isClosed()) {
                cur.close();
            }
        }
        // bulk update
        mDb.executeMany("INSERT INTO cards VALUES (?,?,?,?,?,?,0,0,?,0,0,0,0,0,0,0,0,\"\")", data);
        return rem;
    }


	/**
	 * Return cards of a note, without saving them
	 * @param note The note whose cards are going to be previewed
     * @param type 0 - when previewing in add dialog, only non-empty
     *             1 - when previewing edit, only existing
     *             2 - when previewing in models dialog, all templates
     * @return list of cards
	 */
	public List<Card> previewCards(Note note, int type) {
        int did = 0;
        return previewCards(note, type, did);
    }

    public List<Card> previewCards(Note note, int type, int did) {
	    ArrayList<JSONObject> cms = null;
	    if (type == 0) {
	        cms = findTemplates(note);
	    } else if (type == 1) {
	        cms = new ArrayList<>();
	        for (Card c : note.cards()) {
	            cms.add(c.template());
	        }
	    } else {
	        cms = new ArrayList<>();
	        try {
                JSONArray ja = note.model().getJSONArray("tmpls");
                for (int i = 0; i < ja.length(); ++i) {
                    cms.add(ja.getJSONObject(i));
                }
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
	    }
	    if (cms.isEmpty()) {
	        return new ArrayList<>();
	    }
	    List<Card> cards = new ArrayList<>();
	    for (JSONObject template : cms) {
	        cards.add(_newCard(note, template, 1, did, false));
	    }
	    return cards;
	}
    public List<Card> previewCards(Note note) {
        return previewCards(note, 0);
    }

    /**
     * Create a new card.
     */
    private Card _newCard(Note note, JSONObject template, int due) {
        boolean flush = true;
        return _newCard(note, template, due, flush);
    }

    private Card _newCard(Note note, JSONObject template, int due, int did) {
        boolean flush = true;
        return _newCard(note, template, due, did, flush);
    }

    private Card _newCard(Note note, JSONObject template, int due, boolean flush) {
        int did = 0;
        return _newCard(note, template, due, did, flush);
    }

    private Card _newCard(Note note, JSONObject template, int due, int parameterDid, boolean flush) {
        Card card = new Card(this);
        long nid = note.getId();
        int ord = -1;
        long did;
        card.setNid(nid);
        try {
            ord = template.getInt("ord");
            card.setOrd(ord);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        did = mDb.queryScalar("select did from cards where nid = " + nid + " and ord = " + ord);
        // Use template did (deck override) if valid, otherwise did in argument, otherwise model did
        if (did == 0) {
            did = template.optLong("did", 0);
            if (did > 0 && mDecks.getDecks().containsKey(did)) {
            } else if (parameterDid != 0) {
                did = parameterDid;
            } else {
                did = note.model().optLong("did", 0);
            }
        }
        card.setDid(did);
        try {
            // if invalid did, use default instead
            JSONObject deck = mDecks.get(card.getDid());
            if (deck.getInt("dyn") == 1) {
            	// must not be a filtered deck
            	card.setDid(1);
            } else {
                card.setDid(deck.getLong("id"));
            }
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        card.setDue(_dueForDid(card.getDid(), due));
        if (flush) {
            card.flush();
        }
        return card;
    }


    public int _dueForDid(long did, int due) {
        JSONObject conf = mDecks.confForDid(did);
        // in order due?
        try {
            if (conf.getJSONObject("new").getInt("order") == Consts.NEW_CARDS_DUE) {
                return due;
            } else {
                // random mode; seed with note ts so all cards of this note get
                // the same random number
                Random r = new Random();
                r.setSeed(due);
                return r.nextInt(Math.max(due, 1000) - 1) + 1;
            }
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }


    /**
     * Cards ******************************************************************** ***************************
     */

    public boolean isEmpty() {
        return mDb.queryScalar("SELECT 1 FROM cards LIMIT 1") == 0;
    }


    public int cardCount() {
        return mDb.queryScalar("SELECT count() FROM cards");
    }


    // NOT IN LIBANKI //
    public int cardCount(Long[] ls) {
        return mDb.queryScalar("SELECT count() FROM cards WHERE did IN " + Utils.ids2str(ls));
    }


    /**
     * Bulk delete cards by ID.
     */
    public void remCards(long[] ids) {
    	remCards(ids, true);
    }
    public void remCards(long[] ids, boolean notes) {
        if (ids.length == 0) {
            return;
        }
        String sids = Utils.ids2str(ids);
        long[] nids = Utils
                .arrayList2array(mDb.queryColumn(Long.class, "SELECT nid FROM cards WHERE id IN " + sids, 0));
        // remove cards
        _logRem(ids, Consts.REM_CARD);
        mDb.execute("DELETE FROM cards WHERE id IN " + sids);
        // then notes
        if (!notes) {
        	return;
        }
        nids = Utils
                .arrayList2array(mDb.queryColumn(Long.class, "SELECT id FROM notes WHERE id IN " + Utils.ids2str(nids)
                        + " AND id NOT IN (SELECT nid FROM cards)", 0));
        _remNotes(nids);
    }


    public List<Long> emptyCids() {
        List<Long> rem = new ArrayList<>();
        for (JSONObject m : getModels().all()) {
            rem.addAll(genCards(getModels().nids(m)));
        }
        return rem;
    }


    public String emptyCardReport(List<Long> cids) {
        StringBuilder rep = new StringBuilder();
        Cursor cur = null;
        try {
            cur = mDb.getDatabase().query("select group_concat(ord+1), count(), flds from cards c, notes n "
                                           + "where c.nid = n.id and c.id in " + Utils.ids2str(cids) + " group by nid", null);
            while (cur.moveToNext()) {
                String ords = cur.getString(0);
                //int cnt = cur.getInt(1);  // present but unused upstream as well
                String flds = cur.getString(2);
                rep.append(String.format("Empty card numbers: %s\nFields: %s\n\n", ords, flds.replace("\u001F", " / ")));
            }
        } finally {
            if (cur != null && !cur.isClosed()) {
                cur.close();
            }
        }
        return rep.toString();
    }

    /**
     * Field checksums and sorting fields ***************************************
     * ********************************************************
     */

    private ArrayList<Object[]> _fieldData(String snids) {
        ArrayList<Object[]> result = new ArrayList<>();
        Cursor cur = null;
        try {
            cur = mDb.getDatabase().query("SELECT id, mid, flds FROM notes WHERE id IN " + snids, null);
            while (cur.moveToNext()) {
                result.add(new Object[] { cur.getLong(0), cur.getLong(1), cur.getString(2) });
            }
        } finally {
            if (cur != null && !cur.isClosed()) {
                cur.close();
            }
        }
        return result;
    }


    /** Update field checksums and sort cache, after find&replace, etc. */
    public void updateFieldCache(long[] nids) {
        String snids = Utils.ids2str(nids);
        ArrayList<Object[]> r = new ArrayList<>();
        for (Object[] o : _fieldData(snids)) {
            String[] fields = Utils.splitFields((String) o[2]);
            JSONObject model = mModels.get((Long) o[1]);
            if (model == null) {
                // note point to invalid model
                continue;
            }
            r.add(new Object[] { Utils.stripHTMLMedia(fields[mModels.sortIdx(model)]), Utils.fieldChecksum(fields[0]), o[0] });
        }
        // apply, relying on calling code to bump usn+mod
        mDb.executeMany("UPDATE notes SET sfld=?, csum=? WHERE id=?", r);
    }


    /**
     * Q/A generation *********************************************************** ************************************
     */

    public ArrayList<HashMap<String, String>> renderQA() {
        return renderQA(null, "card");
    }


    public ArrayList<HashMap<String, String>> renderQA(int[] ids, String type) {
        String where;
        if ("card".equals(type)) {
            where = "AND c.id IN " + Utils.ids2str(ids);
        } else if ("fact".equals(type)) {
            where = "AND f.id IN " + Utils.ids2str(ids);
        } else if ("model".equals(type)) {
            where = "AND m.id IN " + Utils.ids2str(ids);
        } else if ("all".equals(type)) {
            where = "";
        } else {
            throw new RuntimeException();
        }
        ArrayList<HashMap<String, String>> result = new ArrayList<>();
        for (Object[] row : _qaData(where)) {
            result.add(_renderQA(row));
        }
        return result;
    }


    /**
     * Returns hash of id, question, answer.
     */
    public HashMap<String, String> _renderQA(Object[] data) {
        return _renderQA(data, null, null);
    }


    public HashMap<String, String> _renderQA(Object[] data, String qfmt, String afmt) {
        // data is [cid, nid, mid, did, ord, tags, flds, cardFlags]
        // unpack fields and create dict
        String[] flist = Utils.splitFields((String) data[6]);
        Map<String, String> fields = new HashMap<>();
        JSONObject model = mModels.get((Long) data[2]);
        Map<String, Pair<Integer, JSONObject>> fmap = mModels.fieldMap(model);
        for (String name : fmap.keySet()) {
            fields.put(name, flist[fmap.get(name).first]);
        }
        try {
            int cardNum = ((Integer) data[4]) + 1;
            fields.put("Tags", ((String) data[5]).trim());
            fields.put("Type", (String) model.get("name"));
            fields.put("Deck", mDecks.name((Long) data[3]));
            String[] parents = fields.get("Deck").split("::", -1);
            fields.put("Subdeck", parents[parents.length-1]);
            fields.put("CardFlag", _flagNameFromCardFlags((Integer) data[7]));
            JSONObject template;
            if (model.getInt("type") == Consts.MODEL_STD) {
                template = model.getJSONArray("tmpls").getJSONObject((Integer) data[4]);
            } else {
                template = model.getJSONArray("tmpls").getJSONObject(0);
            }
            fields.put("Card", template.getString("name"));
            fields.put(String.format(Locale.US, "c%d", cardNum), "1");
            // render q & a
            HashMap<String, String> d = new HashMap<>();
            d.put("id", Long.toString((Long) data[0]));
            qfmt = TextUtils.isEmpty(qfmt) ? template.getString("qfmt") : qfmt;
            afmt = TextUtils.isEmpty(afmt) ? template.getString("afmt") : afmt;
            for (Pair<String, String> p : new Pair[]{new Pair<>("q", qfmt), new Pair<>("a", afmt)}) {
                String type = p.first;
                String format = p.second;
                if ("q".equals(type)) {
                    format = fClozePatternQ.matcher(format).replaceAll(String.format(Locale.US, "{{$1cq-%d:", cardNum));
                    format = fClozeTagStart.matcher(format).replaceAll(String.format(Locale.US, "<%%cq:%d:", cardNum));
                } else {
                    format = fClozePatternA.matcher(format).replaceAll(String.format(Locale.US, "{{$1ca-%d:", cardNum));
                    format = fClozeTagStart.matcher(format).replaceAll(String.format(Locale.US, "<%%ca:%d:", cardNum));
                    // the following line differs from libanki // TODO: why?
                    fields.put("FrontSide", d.get("q")); // fields.put("FrontSide", mMedia.stripAudio(d.get("q")));
                }
                fields = (Map<String, String>) Hooks.runFilter("mungeFields", fields, model, data, this);
                String html = new Template(format, fields).render();
                d.put(type, (String) Hooks.runFilter("mungeQA", html, type, fields, model, data, this));
                // empty cloze?
                if ("q".equals(type) && model.getInt("type") == Consts.MODEL_CLOZE) {
                    if (getModels()._availClozeOrds(model, (String) data[6], false).size() == 0) {
                        String link = String.format("<a href=%s#cloze>%s</a>", Consts.HELP_SITE, "help");
                        d.put("q", mContext.getString(R.string.empty_cloze_warning, link));
                    }
                }
            }
            return d;
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }


    /**
     * Return [cid, nid, mid, did, ord, tags, flds, flags] db query
     */
    public ArrayList<Object[]> _qaData() {
        return _qaData("");
    }


    public ArrayList<Object[]> _qaData(String where) {
        ArrayList<Object[]> data = new ArrayList<>();
        Cursor cur = null;
        try {
            cur = mDb.getDatabase().query(
                    "SELECT c.id, n.id, n.mid, c.did, c.ord, "
                            + "n.tags, n.flds, c.flags FROM cards c, notes n WHERE c.nid == n.id " + where, null);
            while (cur.moveToNext()) {
                data.add(new Object[] { cur.getLong(0), cur.getLong(1), cur.getLong(2), cur.getLong(3), cur.getInt(4),
                        cur.getString(5), cur.getString(6), cur.getInt(7)});
            }
        } finally {
            if (cur != null && !cur.isClosed()) {
                cur.close();
            }
        }
        return data;
    }

	public String _flagNameFromCardFlags(int flags){
		int flag = flags & 0b111;
		if (flag == 0) {
			return "";
		}
		return "flag"+flag;
	}

    /**
     * Finding cards ************************************************************ ***********************************
     */

    /** Return a list of card ids */
    public List<Long> findCards(String search) {
        return new Finder(this).findCards(search, null);
    }


    /** Return a list of card ids */
    public List<Long> findCards(String search, String order) {
        return new Finder(this).findCards(search, order);
    }


    public List<Map<String, String>> findCardsForCardBrowser(String search, boolean order, Map<String, String> deckNames) {
        return new Finder(this).findCardsForCardBrowser(search, order, deckNames);
    }


    /** Return a list of note ids */
    public List<Long> findNotes(String query) {
        return new Finder(this).findNotes(query);
    }


    public int findReplace(List<Long> nids, String src, String dst) {
        return Finder.findReplace(this, nids, src, dst);
    }


    public int findReplace(List<Long> nids, String src, String dst, boolean regex) {
        return Finder.findReplace(this, nids, src, dst, regex);
    }


    public int findReplace(List<Long> nids, String src, String dst, String field) {
        return Finder.findReplace(this, nids, src, dst, field);
    }


    public int findReplace(List<Long> nids, String src, String dst, boolean regex, String field, boolean fold) {
        return Finder.findReplace(this, nids, src, dst, regex, field, fold);
    }


    public List<Pair<String, List<Long>>> findDupes(String fieldName) {
        return Finder.findDupes(this, fieldName, "");
    }


    public List<Pair<String, List<Long>>> findDupes(String fieldName, String search) {
        return Finder.findDupes(this, fieldName, search);
    }


    /**
     * Stats ******************************************************************** ***************************
     */

    // cardstats
    // stats

    /**
     * Timeboxing *************************************************************** ********************************
     */

    public void setTimeLimit(long seconds) {
        try {
            mConf.put("timeLim", seconds);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }


    public long getTimeLimit() {
        long timebox = 0;
        try {
            timebox = mConf.getLong("timeLim");
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        return timebox;
    }


    public void startTimebox() {
        mStartTime = Utils.now();
        mStartReps = mSched.getReps();
    }


    /* Return (elapsedTime, reps) if timebox reached, or null. */
    public Long[] timeboxReached() {
        try {
            if (mConf.getLong("timeLim") == 0) {
                // timeboxing disabled
                return null;
            }
            double elapsed = Utils.now() - mStartTime;
            if (elapsed > mConf.getLong("timeLim")) {
                return new Long[] { mConf.getLong("timeLim"), (long) (mSched.getReps() - mStartReps) };
            }
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        return null;
    }


    /**
     * Undo ********************************************************************* **************************
     */

    /**
     * [type, undoName, data] type 1 = review; type 2 =
     */
    public void clearUndo() {
        mUndo = new LinkedList<>();
    }


    /** Undo menu item name, or "" if undo unavailable. */
    public String undoName(Resources res) {
        if (mUndo.size() > 0) {
            DismissType type = (DismissType) mUndo.getLast()[0];
            return res.getString(type.undoNameId);
        }
        return "";
    }


    public boolean undoAvailable() {
        Timber.d("undoAvailable() undo size: %s", mUndo.size());
        return mUndo.size() > 0;
    }


    public long undo() {
    	Object[] data = mUndo.removeLast();
        Timber.d("undo() of type %s", data[0]);
    	switch ((DismissType) data[0]) {
            case REVIEW: {
                Card c = (Card) data[1];
                // remove leech tag if it didn't have it before
                Boolean wasLeech = (Boolean) data[2];
                if (!wasLeech && c.note().hasTag("leech")) {
                    c.note().delTag("leech");
                    c.note().flush();
                }
                // write old data
                c.flush(false);
                // and delete revlog entry
                long last = mDb.queryLongScalar("SELECT id FROM revlog WHERE cid = " + c.getId() + " ORDER BY id DESC LIMIT 1");
                mDb.execute("DELETE FROM revlog WHERE id = " + last);
                // restore any siblings
                mDb.execute("update cards set queue=type,mod=?,usn=? where queue=-2 and nid=?",
                        new Object[]{Utils.intTime(), usn(), c.getNid()});
                // and finally, update daily count
                int n = c.getQueue() == 3 ? 1 : c.getQueue();
                String type = (new String[]{"new", "lrn", "rev"})[n];
                mSched._updateStats(c, type, -1);
                mSched.setReps(mSched.getReps() - 1);
                return c.getId();
            }

            case BURY_NOTE:
                for (Card cc : (ArrayList<Card>) data[2]) {
                    cc.flush(false);
                }
                return (Long) data[3];

            case SUSPEND_CARD: {
                Card suspendedCard = (Card) data[1];
                suspendedCard.flush(false);
                return suspendedCard.getId();
            }

            case SUSPEND_CARD_MULTI: {
                Card[] cards = (Card[]) data[1];
                boolean[] originalSuspended = (boolean[]) data[2];
                List<Long> toSuspendIds = new ArrayList<>();
                List<Long> toUnsuspendIds = new ArrayList<>();
                for (int i = 0; i < cards.length; i++) {
                    Card card = cards[i];
                    if (originalSuspended[i]) {
                        toSuspendIds.add(card.getId());
                    } else {
                        toUnsuspendIds.add(card.getId());
                    }
                }

                // unboxing
                long[] toSuspendIdsArray = new long[toSuspendIds.size()];
                long[] toUnsuspendIdsArray = new long[toUnsuspendIds.size()];
                for (int i = 0; i < toSuspendIds.size(); i++) {
                    toSuspendIdsArray[i] = toSuspendIds.get(i);
                }
                for (int i = 0; i < toUnsuspendIds.size(); i++) {
                    toUnsuspendIdsArray[i] = toUnsuspendIds.get(i);
                }

                getSched().suspendCards(toSuspendIdsArray);
                getSched().unsuspendCards(toUnsuspendIdsArray);

                return -1;  // don't fetch new card
            }

            case SUSPEND_NOTE:
                for (Card ccc : (ArrayList<Card>) data[1]) {
                    ccc.flush(false);
                }
                return (Long) data[2];

            case MARK_NOTE_MULTI: {
                List<Note> originalMarked = (List<Note>) data[1];
                List<Note> originalUnmarked = (List<Note>) data[2];
                CardUtils.markAll(originalMarked, true);
                CardUtils.markAll(originalUnmarked, false);
                return -1;  // don't fetch new card
            }

            case DELETE_NOTE: {
                ArrayList<Long> ids = new ArrayList<>();
                Note note = (Note) data[1];
                note.flush(note.getMod(), false);
                ids.add(note.getId());
                for (Card c : (ArrayList<Card>) data[2]) {
                    c.flush(false);
                    ids.add(c.getId());
                }
                mDb.execute("DELETE FROM graves WHERE oid IN " + Utils.ids2str(Utils.arrayList2array(ids)));
                return (Long) data[3];
            }

            case DELETE_NOTE_MULTI: {
                // undo all of these at once instead of one-by-one
                ArrayList<Long> ids = new ArrayList<>();
                List<Card> allCards = (ArrayList<Card>) data[2];
                Note[] notes = (Note[]) data[1];
                for (Note n : notes) {
                    n.flush(n.getMod(), false);
                    ids.add(n.getId());
                }
                for (Card c : allCards) {
                    c.flush(false);
                    ids.add(c.getId());
                }
                mDb.execute("DELETE FROM graves WHERE oid IN " + Utils.ids2str(Utils.arrayList2array(ids)));
                return -1;  // don't fetch new card
            }

            case CHANGE_DECK_MULTI: {
                Card[] cards = (Card[]) data[1];
                long[] originalDid = (long[]) data[2];
                // move cards to original deck
                for (int i = 0; i < cards.length; i++) {
                    Card card = cards[i];
                    card.load();
                    card.setDid(originalDid[i]);
                    Note note = card.note();
                    note.flush();
                    card.flush();
                }
                return -1;  // don't fetch new card
            }

            case BURY_CARD: {
                for (Card cc : (ArrayList<Card>) data[2]) {
                    cc.flush(false);
                }
                return (Long) data[3];
            }

            case RESET_CARDS:
            case RESCHEDULE_CARDS:
            case REPOSITION_CARDS:
                Timber.d("Undoing action of type %s on %d cards", data[0], ((Card[])data[1]).length);
                Card[] cards = (Card[]) data[1];
                for (int i = 0; i < cards.length; i++) {
                    Card card = cards[i];
                    card.flush(false);
                }
                return 0;

            default:
                return 0;
        }
    }


    public void markUndo(DismissType type, Object[] o) {
        Timber.d("markUndo() of type %s", type);
        switch (type) {
            case REVIEW:
                mUndo.add(new Object[]{type, ((Card) o[0]).clone(), o[1]});
                break;
            case BURY_CARD:
                mUndo.add(new Object[]{type, o[0], o[1], o[2]});
                break;
            case BURY_NOTE:
                mUndo.add(new Object[]{type, o[0], o[1], o[2]});
                break;
            case SUSPEND_CARD:
                mUndo.add(new Object[]{type, ((Card) o[0]).clone()});
                break;
            case SUSPEND_CARD_MULTI:
                mUndo.add(new Object[]{type, o[0], o[1]});
                break;
            case MARK_NOTE_MULTI:
                mUndo.add(new Object[]{type, o[0], o[1]});
                break;
            case SUSPEND_NOTE:
                mUndo.add(new Object[]{type, o[0], o[1]});
                break;
            case DELETE_NOTE:
                mUndo.add(new Object[]{type, o[0], o[1], o[2]});
                break;
            case DELETE_NOTE_MULTI:
                mUndo.add(new Object[]{type, o[0], o[1]});
                break;
            case CHANGE_DECK_MULTI:
                mUndo.add(new Object[]{type, o[0], o[1]});
                break;
            case RESET_CARDS:
            case REPOSITION_CARDS:
            case RESCHEDULE_CARDS:
                // Card array is cloned in DeckTask, which pays attention to memory pressure
                mUndo.add(new Object[]{type, o[0]});
                break;
            default:
                Timber.e("markUndo() received unknown type? %s", type);
                break;
        }
        while (mUndo.size() > UNDO_SIZE_MAX) {
            mUndo.removeFirst();
        }
    }


    public void markReview(Card card) {
        markUndo(DismissType.REVIEW, new Object[]{card, card.note().hasTag("leech")});
    }

    /**
     * DB maintenance *********************************************************** ************************************
     */


    /*
     * Basic integrity check for syncing. True if ok.
     */
    public boolean basicCheck() {
        // cards without notes
        if (mDb.queryScalar("select 1 from cards where nid not in (select id from notes) limit 1") > 0) {
            return false;
        }
        boolean badNotes = mDb.queryScalar(String.format(Locale.US,
                "select 1 from notes where id not in (select distinct nid from cards) " +
                "or mid not in %s limit 1", Utils.ids2str(mModels.ids()))) > 0;
        // notes without cards or models
        if (badNotes) {
            return false;
        }
        try {
            // invalid ords
            for (JSONObject m : mModels.all()) {
                // ignore clozes
                if (m.getInt("type") != Consts.MODEL_STD) {
                    continue;
                }
                // Make a list of valid ords for this model
                JSONArray tmpls = m.getJSONArray("tmpls");
                int[] ords = new int[tmpls.length()];
                for (int t = 0; t < tmpls.length(); t++) {
                    ords[t] = tmpls.getJSONObject(t).getInt("ord");
                }

                boolean badOrd = mDb.queryScalar(String.format(Locale.US,
                        "select 1 from cards where ord not in %s and nid in ( " +
                        "select id from notes where mid = %d) limit 1",
                        Utils.ids2str(ords), m.getLong("id"))) > 0;
                if (badOrd) {
                    return false;
                }
            }
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        return true;
    }


    /** Fix possible problems and rebuild caches. */
    public long fixIntegrity(DeckTask.ProgressCallback progressCallback) {
        File file = new File(mPath);
        ArrayList<String> problems = new ArrayList<>();
        long oldSize = file.length();
        int currentTask = 1;
        int totalTasks = (mModels.all().size() * 4) + 20; // 4 things are in all-models loops, 20 things are one-offs
        try {
            mDb.getDatabase().beginTransaction();
            try {
                save();
                fixIntegrityProgress(progressCallback, currentTask++, totalTasks);
                if (!"ok".equals(mDb.queryString("PRAGMA integrity_check"))) {
                    return -1;
                }
                // note types with a missing model
                fixIntegrityProgress(progressCallback, currentTask++, totalTasks);
                ArrayList<Long> ids = mDb.queryColumn(Long.class,
                        "SELECT id FROM notes WHERE mid NOT IN " + Utils.ids2str(mModels.ids()), 0);
                fixIntegrityProgress(progressCallback, currentTask++, totalTasks);
                if (ids.size() != 0) {
                	problems.add("Deleted " + ids.size() + " note(s) with missing note type.");
	                _remNotes(Utils.arrayList2array(ids));
                }
                // for each model
                for (JSONObject m : mModels.all()) {
                    // cards with invalid ordinal
                    fixIntegrityProgress(progressCallback, currentTask++, totalTasks);
                    if (m.getInt("type") == Consts.MODEL_STD) {
                        ArrayList<Integer> ords = new ArrayList<>();
                        JSONArray tmpls = m.getJSONArray("tmpls");
                        for (int t = 0; t < tmpls.length(); t++) {
                            ords.add(tmpls.getJSONObject(t).getInt("ord"));
                        }
                        ids = mDb.queryColumn(Long.class,
                                "SELECT id FROM cards WHERE ord NOT IN " + Utils.ids2str(ords) + " AND nid IN ( " +
                                "SELECT id FROM notes WHERE mid = " + m.getLong("id") + ")", 0);
                        if (ids.size() > 0) {
                            problems.add("Deleted " + ids.size() + " card(s) with missing template.");
                            remCards(Utils.arrayList2array(ids));
                        }
                    }
                    // notes with invalid field counts
                    ids = new ArrayList<>();
                    Cursor cur = null;
                    try {
                        fixIntegrityProgress(progressCallback, currentTask++, totalTasks);
                        cur = mDb.getDatabase().query("select id, flds from notes where mid = " + m.getLong("id"), null);
                        while (cur.moveToNext()) {
                            String flds = cur.getString(1);
                            long id = cur.getLong(0);
                            int fldsCount = 0;
                            for (int i = 0; i < flds.length(); i++) {
                                if (flds.charAt(i) == 0x1f) {
                                    fldsCount++;
                                }
                            }
                            if (fldsCount + 1 != m.getJSONArray("flds").length()) {
                                ids.add(id);
                            }
                        }
                        fixIntegrityProgress(progressCallback, currentTask++, totalTasks);
                        if (ids.size() > 0) {
                            problems.add("Deleted " + ids.size() + " note(s) with wrong field count.");
                            _remNotes(Utils.arrayList2array(ids));
                        }
                    } finally {
                        if (cur != null && !cur.isClosed()) {
                            cur.close();
                        }
                    }
                }
                fixIntegrityProgress(progressCallback, currentTask++, totalTasks);
                // delete any notes with missing cards
                ids = mDb.queryColumn(Long.class,
                        "SELECT id FROM notes WHERE id NOT IN (SELECT DISTINCT nid FROM cards)", 0);
                fixIntegrityProgress(progressCallback, currentTask++, totalTasks);
                if (ids.size() != 0) {
                	problems.add("Deleted " + ids.size() + " note(s) with missing no cards.");
	                _remNotes(Utils.arrayList2array(ids));
                }
                // cards with missing notes
                fixIntegrityProgress(progressCallback, currentTask++, totalTasks);
                ids = mDb.queryColumn(Long.class,
                        "SELECT id FROM cards WHERE nid NOT IN (SELECT id FROM notes)", 0);
                fixIntegrityProgress(progressCallback, currentTask++, totalTasks);
                if (ids.size() != 0) {
                    problems.add("Deleted " + ids.size() + " card(s) with missing note.");
                    remCards(Utils.arrayList2array(ids));
                }
                // cards with odue set when it shouldn't be
                fixIntegrityProgress(progressCallback, currentTask++, totalTasks);
                ids = mDb.queryColumn(Long.class,
                        "select id from cards where odue > 0 and (type=1 or queue=2) and not odid", 0);
                fixIntegrityProgress(progressCallback, currentTask++, totalTasks);
                if (ids.size() != 0) {
                    problems.add("Fixed " + ids.size() + " card(s) with invalid properties.");
                    mDb.execute("update cards set odue=0 where id in " + Utils.ids2str(ids));
                }
                // cards with odid set when not in a dyn deck
                ArrayList<Long> dids = new ArrayList<>();
                for (long id : mDecks.allIds()) {
                    if (!mDecks.isDyn(id)) {
                        dids.add(id);
                    }
                }
                fixIntegrityProgress(progressCallback, currentTask++, totalTasks);
                ids = mDb.queryColumn(Long.class,
                        "select id from cards where odid > 0 and did in " + Utils.ids2str(dids), 0);
                fixIntegrityProgress(progressCallback, currentTask++, totalTasks);
                if (ids.size() != 0) {
                    problems.add("Fixed " + ids.size() + " card(s) with invalid properties.");
                    mDb.execute("update cards set odid=0, odue=0 where id in " + Utils.ids2str(ids));
                }
                // tags
                fixIntegrityProgress(progressCallback, currentTask++, totalTasks);
                mTags.registerNotes();
                // field cache
                for (JSONObject m : mModels.all()) {
                    fixIntegrityProgress(progressCallback, currentTask++, totalTasks);
                    updateFieldCache(Utils.arrayList2array(mModels.nids(m)));
                }
                // new cards can't have a due position > 32 bits
                fixIntegrityProgress(progressCallback, currentTask++, totalTasks);
                mDb.execute("UPDATE cards SET due = 1000000, mod = " + Utils.intTime() + ", usn = " + usn()
                        + " WHERE due > 1000000 AND type = 0");
                // new card position
                mConf.put("nextPos", mDb.queryScalar("SELECT max(due) + 1 FROM cards WHERE type = 0"));
                // reviews should have a reasonable due #
                fixIntegrityProgress(progressCallback, currentTask++, totalTasks);
                ids = mDb.queryColumn(Long.class, "SELECT id FROM cards WHERE queue = 2 AND due > 100000", 0);
                fixIntegrityProgress(progressCallback, currentTask++, totalTasks);
                if (ids.size() > 0) {
                	problems.add("Reviews had incorrect due date.");
                    mDb.execute("UPDATE cards SET due = " + mSched.getToday() + ", ivl = 1, mod = " +  Utils.intTime() +
                            ", usn = " + usn() + " WHERE id IN " + Utils.ids2str(Utils.arrayList2array(ids)));
                }
                // v2 sched had a bug that could create decimal intervals
                fixIntegrityProgress(progressCallback, currentTask++, totalTasks);
                SupportSQLiteStatement s = mDb.getDatabase().compileStatement(
                        "update cards set ivl=round(ivl),due=round(due) where ivl!=round(ivl) or due!=round(due)");
                int rowCount = s.executeUpdateDelete();
                if (rowCount > 0) {
                    problems.add("Fixed " + rowCount + " cards with v2 scheduler bug.");
                }
                fixIntegrityProgress(progressCallback, currentTask++, totalTasks);
                s = mDb.getDatabase().compileStatement(
                        "update revlog set ivl=round(ivl),lastIvl=round(lastIvl) where ivl!=round(ivl) or lastIvl!=round(lastIvl)");
                rowCount = s.executeUpdateDelete();
                if (rowCount > 0) {
                    problems.add("Fixed " + rowCount + " review history entries with v2 scheduler bug.");
                }
                mDb.getDatabase().setTransactionSuccessful();
                // DB must have indices. Older versions of AnkiDroid didn't create them for new collections.
                fixIntegrityProgress(progressCallback, currentTask++, totalTasks);
                int ixs = mDb.queryScalar("select count(name) from sqlite_master where type = 'index'");
                if (ixs < 7) {
                    problems.add("Indices were missing.");
                    Storage.addIndices(mDb);
                }
            } catch (JSONException e) {
                throw new RuntimeException(e);
            } finally {
                mDb.getDatabase().endTransaction();
            }
        } catch (RuntimeException e) {
            Timber.e(e, "doInBackgroundCheckDatabase - RuntimeException on marking card");
            AnkiDroidApp.sendExceptionReport(e, "doInBackgroundCheckDatabase");
            return -1;
        }
        // models
        if (mModels.ensureNotEmpty()) {
            problems.add("Added missing note type.");
        }
        // and finally, optimize
        optimize(progressCallback, currentTask, totalTasks);
        file = new File(mPath);
        long newSize = file.length();
        // if any problems were found, force a full sync
        if (problems.size() > 0) {
            modSchemaNoCheck();
        }
        logProblems(problems);
        return (oldSize - newSize) / 1024;
    }


    public void optimize(DeckTask.ProgressCallback progressCallback, int currentTask, int totalTasks) {
        Timber.i("executing VACUUM statement");
        fixIntegrityProgress(progressCallback, currentTask++, totalTasks);
        mDb.execute("VACUUM");
        Timber.i("executing ANALYZE statement");
        fixIntegrityProgress(progressCallback, currentTask++, totalTasks);
        mDb.execute("ANALYZE");
    }


    private void fixIntegrityProgress(DeckTask.ProgressCallback progressCallback, int current, int total) {
        progressCallback.publishProgress(new DeckTask.TaskData(
                progressCallback.getResources().getString(R.string.check_db_message) + " " + current + " / " + total));
    }


    /**
     * Logging
     * ***********************************************************
     */

    /**
     * Track database corruption problems and post analytics events for tracking
     *
     * @param integrityCheckProblems list of problems, the first 10 will be used
     */
    private void logProblems(ArrayList<String> integrityCheckProblems) {

        if (integrityCheckProblems.size() > 0) {
            StringBuffer additionalInfo = new StringBuffer();
            for (int i = 0; ((i < 10) && (integrityCheckProblems.size() > i)); i++) {
                additionalInfo.append(integrityCheckProblems.get(i)).append("\n");
                // log analytics event so we can see trends if user allows it
                UsageAnalytics.sendAnalyticsEvent("DatabaseCorruption", integrityCheckProblems.get(i));
            }
            Timber.i("fixIntegrity() Problem list (limited to first 10):\n%s", additionalInfo);
        } else {
            Timber.i("fixIntegrity() no problems found");
        }
    }

    public void log(Object... args) {
        if (!mDebugLog) {
            return;
        }
        StackTraceElement trace = Thread.currentThread().getStackTrace()[3];
        // Overwrite any args that need special handling for an appropriate string representation
        for (int i = 0; i < args.length; i++) {
            if (args[i] instanceof long[]) {
                args[i] = Arrays.toString((long []) args[i]);
            }
        }
        String s = String.format("[%s] %s:%s(): %s", Utils.intTime(), trace.getFileName(), trace.getMethodName(),
                TextUtils.join(",  ", args));
        mLogHnd.println(s);
        Timber.d(s);
    }


    private void _openLog() {
        if (!mDebugLog) {
            return;
        }
        try {
            File lpath = new File(mPath.replaceFirst("\\.anki2$", ".log"));
            if (lpath.exists() && lpath.length() > 10*1024*1024) {
                File lpath2 = new File(lpath + ".old");
                if (lpath2.exists()) {
                    lpath2.delete();
                }
                lpath.renameTo(lpath2);
            }
            mLogHnd = new PrintWriter(new BufferedWriter(new FileWriter(lpath, true)), true);
        } catch (IOException e) {
            // turn off logging if we can't open the log file
            Timber.e("Failed to open collection.log file - disabling logging");
            mDebugLog = false;
        }
    }


    private void _closeLog() {
        if (mLogHnd != null) {
            mLogHnd.close();
            mLogHnd = null;
        }
    }

    /**
     * Getters/Setters ********************************************************** *************************************
     */

    public DB getDb() {
        return mDb;
    }


    public Decks getDecks() {
        return mDecks;
    }


    public Media getMedia() {
        return mMedia;
    }


    public Models getModels() {
        return mModels;
    }

    /** Check if this collection is valid. */
    public boolean validCollection() {
    	//TODO: more validation code
    	return mModels.validateModel();
    }

    public JSONObject getConf() {
        return mConf;
    }


    public void setConf(JSONObject conf) {
        mConf = conf;
    }


    public long getScm() {
        return mScm;
    }


    public boolean getServer() {
        return mServer;
    }


    public void setLs(long ls) {
        mLs = ls;
    }


    public void setUsnAfterSync(int usn) {
        mUsn = usn;
    }


    public long getMod() {
        return mMod;
    }


    /* this getter is only for syncing routines, use usn() instead elsewhere */
    public int getUsnForSync() {
        return mUsn;
    }


    public Tags getTags() {
        return mTags;
    }


    public long getCrt() {
        return mCrt;
    }


    public void setCrt(long crt) {
        mCrt = crt;
    }


    public Sched getSched() {
        return mSched;
    }


    public String getPath() {
        return mPath;
    }


    public void setServer(boolean server) {
        mServer = server;
    }

    public boolean getDirty() {
        return mDty;
    }

    /**
     * @return The context that created this Collection.
     */
    public Context getContext() {
        return mContext;
    }

}
