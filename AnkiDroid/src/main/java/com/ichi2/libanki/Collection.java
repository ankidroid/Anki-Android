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

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Context;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabaseLockedException;
import android.text.TextUtils;
import android.util.Pair;

import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.R;
import com.ichi2.anki.UIUtils;
import com.ichi2.anki.analytics.UsageAnalytics;
import com.ichi2.anki.exception.ConfirmModSchemaException;
import com.ichi2.async.CollectionTask;
import com.ichi2.libanki.exception.NoSuchDeckException;
import com.ichi2.libanki.hooks.ChessFilter;
import com.ichi2.libanki.sched.AbstractSched;
import com.ichi2.libanki.sched.Sched;
import com.ichi2.libanki.sched.SchedV2;
import com.ichi2.libanki.template.Template;
import com.ichi2.libanki.utils.Time;
import com.ichi2.upgrade.Upgrade;
import com.ichi2.utils.FunctionalInterfaces;
import com.ichi2.utils.VersionUtils;

import com.ichi2.utils.JSONArray;
import com.ichi2.utils.JSONException;
import com.ichi2.utils.JSONObject;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.regex.Pattern;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteStatement;
import timber.log.Timber;

import com.ichi2.async.TaskData;

import static com.ichi2.libanki.Collection.DismissType.REVIEW;

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

    private AbstractSched mSched;

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

    private LinkedList<Undoable> mUndo;

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
        FLAG(R.string.card_browser_flag),
        REPOSITION_CARDS(R.string.undo_action_reposition_card),
        RESCHEDULE_CARDS(R.string.undo_action_reschedule_card),
        RESET_CARDS(R.string.undo_action_reset_card);

        public int undoNameId;

        DismissType(int undoNameId) {
            this.undoNameId = undoNameId;
        }
    }

    private static final int UNDO_SIZE_MAX = 20;

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
            mConf.put("newBury", true);
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

    // Note: Additional members in the class duplicate this
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
        modSchema();
        SchedV2 v2Sched = new SchedV2(this);
        clearUndo();
        if (ver == 1) {
            v2Sched.moveToV1();
        } else {
            v2Sched.moveToV2();
        }
        mConf.put("schedVer", ver);
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
            mConf = new JSONObject(cursor.getString(6));
            deckConf = cursor.getString(7);
            mTags.load(cursor.getString(8));
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        // getModels().load(loadColumn("models")); This code has been
        // moved to `CollectionHelper::loadLazyCollection` for
        // efficiency Models are loaded lazily on demand. The
        // application layer can asynchronously pre-fetch those parts;
        // otherwise they get loaded when required.
        mDecks.load(loadColumn("decks"), deckConf);
    }

    public String loadColumn(String columnName) {
        int pos = 1;
        int chunk = 1024*1024; // 1 mb, a little less than what a cursor line may contain
        StringBuffer buf = new StringBuffer("");

        while (true) {
            try (Cursor cursor = mDb.getDatabase().query("SELECT substr(" + columnName + ", ?, ?) FROM col",
                    new String[] {Integer.toString(pos), Integer.toString(chunk)})) {
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

    public synchronized void save(String name) {
        save(name, 0);
    }


    public synchronized void save(String name, long mod) {
        // let the managers conditionally flush
        getModels().flush();
        mDecks.flush();
        mTags.flush();
        // and flush deck + bump mod if db has been changed
        if (mDb.getMod()) {
            flush(mod);
            mDb.commit();
            mDb.setMod(false);
        }
        // undoing non review operation is handled differently in ankidroid
//        _markOp(name);
        //mLastSave = Utils.now(); // assigned but never accessed - only leaving in for upstream comparison
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
                mDb.getDatabase().disableWriteAheadLogging();
            }
            mDb.close();
            mDb = null;
            mMedia.close();
            _closeLog();
            Timber.i("Collection closed");
        }
    }


    public void reopen() {
        Timber.i("Reopening Database");
        if (mDb == null) {
            mDb = new DB(mPath);
            mMedia.connect();
            _openLog();
        }
    }


    /** Note: not in libanki.  Mark schema modified to force a full
     * sync, but with the confirmation checking function disabled This
     * is equivalent to `modSchema(False)` in Anki. A distinct method
     * is used so that the type does not states that an exception is
     * thrown when in fact it is never thrown.
     */
    public void modSchemaNoCheck() {
        mScm = Utils.intTime(1000);
        setMod();
    }

    /** Mark schema modified to force a full sync.
     * ConfirmModSchemaException will be thrown if the user needs to be prompted to confirm the action.
     * If the user chooses to confirm then modSchemaNoCheck should be called, after which the exception can
     * be safely ignored, and the outer code called again.
     *
     * @throws ConfirmModSchemaException */
    public void modSchema() throws ConfirmModSchemaException {
        if (!schemaChanged()) {
            /* In Android we can't show a dialog which blocks the main UI thread
             Therefore we can't wait for the user to confirm if they want to do
             a full sync here, and we instead throw an exception asking the outer
             code to handle the user's choice */
            throw new ConfirmModSchemaException();
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
        getModels().beforeUpload();
        mTags.beforeUpload();
        mDecks.beforeUpload();
        modSchemaNoCheck();
        mLs = mScm;
        Timber.i("Compacting database before full upload");
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
        mConf.put(type, id + 1);
        return id;
    }


    /**
     * Rebuild the queue and reload data after DB modified.
     */
    public void reset() {
        mSched.deferReset();
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

    public void _logRem(java.util.Collection<Long> ids, int type) {
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
        return newNote(getModels().current(forDeck));
    }

    /**
     * Return a new note with a specific model
     * @param m The model to use for the new note
     * @return The new note
     */
    public Note newNote(Model m) {
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
                .queryLongList("SELECT id FROM cards WHERE nid IN " + Utils.ids2str(ids));
        remCards(list);
    }


    /**
     * Bulk delete notes by ID. Don't call this directly.
     */
    public void _remNotes(java.util.Collection<Long> ids) {
        if (ids.size() == 0) {
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
        Model model = note.model();
        ArrayList<Integer> avail = getModels().availOrds(model, note.getFields());
        return _tmplsFromOrds(model, avail);
    }


    private ArrayList<JSONObject> _tmplsFromOrds(JSONObject model, ArrayList<Integer> avail) {
        ArrayList<JSONObject> ok = new ArrayList<>();
        JSONArray tmpls;
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
        return ok;
    }


    /**
     * Generate cards for non-empty templates, return ids to remove.
     */
	public ArrayList<Long> genCards(List<Long> nids) {
	    return genCards(Utils.collection2Array(nids));
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
                @Consts.CARD_TYPE int type = cur.getInt(7);

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
                if (!dues.containsKey(nid) && type == Consts.CARD_TYPE_NEW) {
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
                Model model = getModels().get(mid);
                ArrayList<Integer> avail = getModels().availOrds(model, Utils.splitFields(flds));
                long did = dids.get(nid);
                // use sibling due if there is one, else use a new id
                long due;
                if (dues.containsKey(nid)) {
                    due = dues.get(nid);
                } else {
                    due = nextID("pos");
                }
                if (did == 0) {
                    did = model.getLong("did");
                }
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
	    if (type == Consts.CARD_TYPE_NEW) {
	        cms = findTemplates(note);
	    } else if (type == Consts.CARD_TYPE_LRN) {
	        cms = new ArrayList<>();
	        for (Card c : note.cards()) {
	            cms.add(c.template());
	        }
	    } else {
	        cms = new ArrayList<>();
            JSONArray ja = note.model().getJSONArray("tmpls");
            for (int i = 0; i < ja.length(); ++i) {
                cms.add(ja.getJSONObject(i));
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
        return getNewLinkedCard(card, note, template, due, parameterDid, flush);
    }

    // This contains the original libanki implementation of _newCard, with the added parameter that
    // you pass the Card object in. This allows you to work on 'Card' subclasses that may not have
    // actual backing store (for instance, if you are previewing unsaved changes on templates)
    // TODO: use an interface that we implement for card viewing, vs subclassing an active model to workaround libAnki
    public Card getNewLinkedCard(Card card, Note note, JSONObject template, int due, int parameterDid, boolean flush) {
        long nid = note.getId();
        int ord = -1;
        long did;
        card.setNid(nid);
        ord = template.getInt("ord");
        card.setOrd(ord);
        did = mDb.queryScalar("select did from cards where nid = ? and ord = ?", nid, ord);
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
        // if invalid did, use default instead
        Deck deck = mDecks.get(card.getDid());
        if (deck.getInt("dyn") == 1) {
            // must not be a filtered deck
            card.setDid(1);
        } else {
            card.setDid(deck.getLong("id"));
        }
        card.setDue(_dueForDid(card.getDid(), due));
        if (flush) {
            card.flush();
        }
        return card;
    }


    public int _dueForDid(long did, int due) {
        DeckConfig conf = mDecks.confForDid(did);
        // in order due?
        if (conf.getJSONObject("new").getInt("order") == Consts.NEW_CARDS_DUE) {
            return due;
        } else {
            // random mode; seed with note ts so all cards of this note get
            // the same random number
            Random r = new Random();
            r.setSeed(due);
            return r.nextInt(Math.max(due, 1000) - 1) + 1;
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
    public void remCards(List<Long> ids) {
        remCards(ids, true);
    }

    public void remCards(java.util.Collection<Long> ids, boolean notes) {
        if (ids.size() == 0) {
            return;
        }
        String sids = Utils.ids2str(ids);
        List<Long> nids = mDb.queryLongList("SELECT nid FROM cards WHERE id IN " + sids);
        // remove cards
        _logRem(ids, Consts.REM_CARD);
        mDb.execute("DELETE FROM cards WHERE id IN " + sids);
        // then notes
        if (!notes) {
        	return;
        }
        nids = mDb.queryLongList("SELECT id FROM notes WHERE id IN " + Utils.ids2str(nids)
                        + " AND id NOT IN (SELECT nid FROM cards)");
        _remNotes(nids);
    }


    public List<Long> emptyCids() {
        List<Long> rem = new ArrayList<>();
        for (Model m : getModels().all()) {
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
            Model model = getModels().get((Long) o[1]);
            if (model == null) {
                // note point to invalid model
                continue;
            }
            r.add(new Object[] { Utils.stripHTMLMedia(fields[getModels().sortIdx(model)]), Utils.fieldChecksum(fields[0]), o[0] });
        }
        // apply, relying on calling code to bump usn+mod
        mDb.executeMany("UPDATE notes SET sfld=?, csum=? WHERE id=?", r);
    }


    /**
     * Q/A generation *********************************************************** ************************************
     */

    /**
     * Returns hash of id, question, answer.
     */
    public HashMap<String, String> _renderQA(long cid, Model model, long did, int ord, String tags, String[] flist, int flags) {
        return _renderQA(cid, model, did, ord, tags, flist, flags, false, null, null);
    }


    public HashMap<String, String> _renderQA(long cid, Model model, long did, int ord, String tags, String[] flist, int flags, boolean browser, String qfmt, String afmt) {
        // data is [cid, nid, mid, did, ord, tags, flds, cardFlags]
        // unpack fields and create dict
        Map<String, String> fields = new HashMap<>();
        Map<String, Pair<Integer, JSONObject>> fmap = getModels().fieldMap(model);
        for (String name : fmap.keySet()) {
            fields.put(name, flist[fmap.get(name).first]);
        }
        int cardNum = ord + 1;
        fields.put("Tags", tags.trim());
        fields.put("Type", (String) model.get("name"));
        fields.put("Deck", mDecks.name(did));
        String baseName = Decks.basename(fields.get("Deck"));
        fields.put("Subdeck", baseName);
        fields.put("CardFlag", _flagNameFromCardFlags(flags));
        JSONObject template;
        if (model.getInt("type") == Consts.MODEL_STD) {
            template = model.getJSONArray("tmpls").getJSONObject(ord);
        } else {
            template = model.getJSONArray("tmpls").getJSONObject(0);
        }
        fields.put("Card", template.getString("name"));
        fields.put(String.format(Locale.US, "c%d", cardNum), "1");
        // render q & a
        HashMap<String, String> d = new HashMap<>();
        d.put("id", Long.toString(cid));
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
            String html = new Template(format, fields).render();
            html = ChessFilter.fenToChessboard(html, getContext());
            if (!browser) {
                // browser don't show image. So compiling LaTeX actually remove information.
                html = LaTeX.mungeQA(html, this, model);
            }
            d.put(type, html);
            // empty cloze?
            if ("q".equals(type) && model.getInt("type") == Consts.MODEL_CLOZE) {
                if (getModels()._availClozeOrds(model, flist, false).size() == 0) {
                    String link = String.format("<a href=%s#cloze>%s</a>", Consts.HELP_SITE, "help");
                    d.put("q", mContext.getString(R.string.empty_cloze_warning, link));
                }
            }
        }
        return d;
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
                data.add(new Object[] { cur.getLong(0), cur.getLong(1),
                        getModels().get(cur.getLong(2)), cur.getLong(3), cur.getInt(4),
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

    public List<Long> findCards(String search, boolean order) {
        return findCards(search, order, null);
    }

    public List<Long> findCards(String search, boolean order, CollectionTask task) {
        return new Finder(this).findCards(search, order, task);
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
        mConf.put("timeLim", seconds);
    }


    public long getTimeLimit() {
        long timebox = 0;
        timebox = mConf.getLong("timeLim");
        return timebox;
    }


    public void startTimebox() {
        mStartTime = Utils.now();
        mStartReps = mSched.getReps();
    }


    /* Return (elapsedTime, reps) if timebox reached, or null. */
    public Long[] timeboxReached() {
        if (mConf.getLong("timeLim") == 0) {
            // timeboxing disabled
            return null;
        }
        double elapsed = Utils.now() - mStartTime;
        if (elapsed > mConf.getLong("timeLim")) {
            return new Long[] { mConf.getLong("timeLim"), (long) (mSched.getReps() - mStartReps) };
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
    @VisibleForTesting
    public DismissType undoType() {
        if (mUndo.size() > 0) {
            return mUndo.getLast().getDismissType();
        }
        return null;
    }
    public String undoName(Resources res) {
        DismissType type = undoType();
        if (type != null) {
            return res.getString(type.undoNameId);
        }
        return "";
    }

    public boolean undoAvailable() {
        Timber.d("undoAvailable() undo size: %s", mUndo.size());
        return mUndo.size() > 0;
    }

    public long undo() {
        Undoable lastUndo = mUndo.removeLast();
        Timber.d("undo() of type %s", lastUndo.getDismissType());
        return lastUndo.undo(this);
    }

    public void markUndo(Undoable undo) {
        Timber.d("markUndo() of type %s", undo.getDismissType());
        mUndo.add(undo);
        while (mUndo.size() > UNDO_SIZE_MAX) {
            mUndo.removeFirst();
        }
    }

    public void markReview(Card card) {
        boolean wasLeech = card.note().hasTag("leech");
        Card clonedCard = card.clone();
        Undoable undoableReview = new Undoable(REVIEW) {
            public long undo(Collection col) {
                // remove leech tag if it didn't have it before
                if (!wasLeech && clonedCard.note().hasTag("leech")) {
                    clonedCard.note().delTag("leech");
                    clonedCard.note().flush();
                }
                Timber.i("Undo Review of card %d, leech: %b", clonedCard.getId(), wasLeech);
                // write old data
                clonedCard.flush(false);
                // and delete revlog entry
                long last = col.getDb().queryLongScalar("SELECT id FROM revlog WHERE cid = ? ORDER BY id DESC LIMIT 1", new Object[] {clonedCard.getId()});
                col.getDb().execute("DELETE FROM revlog WHERE id = " + last);
                // restore any siblings
                col.getDb().execute("update cards set queue=type,mod=?,usn=? where queue=" + Consts.QUEUE_TYPE_SIBLING_BURIED + " and nid=?",
                        new Object[] {Utils.intTime(), col.usn(), clonedCard.getNid()});
                // and finally, update daily count
                @Consts.CARD_QUEUE int n = clonedCard.getQueue() == Consts.QUEUE_TYPE_DAY_LEARN_RELEARN ? Consts.QUEUE_TYPE_LRN : clonedCard.getQueue();
                String type = (new String[]{"new", "lrn", "rev"})[n];
                col.getSched()._updateStats(clonedCard, type, -1);
                col.getSched().setReps(col.getSched().getReps() - 1);
                return clonedCard.getId();
            }
        };
        markUndo(undoableReview);
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
        boolean badNotes = mDb.queryScalar(
                "select 1 from notes where id not in (select distinct nid from cards) " +
                "or mid not in " +  Utils.ids2str(getModels().ids()) + " limit 1") > 0;
        // notes without cards or models
        if (badNotes) {
            return false;
        }
        // invalid ords
        for (JSONObject m : getModels().all()) {
            // ignore clozes
            if (m.getInt("type") != Consts.MODEL_STD) {
                continue;
            }
            // Make a list of valid ords for this model
            JSONArray tmpls = m.getJSONArray("tmpls");

            boolean badOrd = mDb.queryScalar("select 1 from cards where (ord < 0 or ord >= ?) and nid in ( " +
                                             "select id from notes where mid = ?) limit 1",
                                             tmpls.length(), m.getLong("id")) > 0;
            if (badOrd) {
                return false;
            }
        }
        return true;
    }


    /** Fix possible problems and rebuild caches. */
    public CheckDatabaseResult fixIntegrity(CollectionTask.ProgressCallback progressCallback) {
        File file = new File(mPath);
        CheckDatabaseResult result = new CheckDatabaseResult(file.length());
        final int[] currentTask = {1};
        int totalTasks = (getModels().all().size() * 4) + 27; // a few fixes are in all-models loops, the rest are one-offs
        Runnable notifyProgress = () -> fixIntegrityProgress(progressCallback, currentTask[0]++, totalTasks);
        FunctionalInterfaces.Consumer<FunctionalInterfaces.FunctionThrowable<Runnable, List<String>, JSONException>> executeIntegrityTask =
                (FunctionalInterfaces.FunctionThrowable<Runnable, List<String>, JSONException> function) -> {
                    //DEFECT: notifyProgress will lag if an exception is thrown.
                    try {
                        mDb.getDatabase().beginTransaction();
                        result.addAll(function.apply(notifyProgress));
                        mDb.getDatabase().setTransactionSuccessful();
                    } catch (Exception e) {
                        Timber.e(e, "Failed to execute integrity check");
                        AnkiDroidApp.sendExceptionReport(e, "fixIntegrity");
                    } finally {
                        try {
                            mDb.getDatabase().endTransaction();
                        } catch (Exception e) {
                            Timber.e(e, "Failed to end integrity check transaction");
                            AnkiDroidApp.sendExceptionReport(e, "fixIntegrity - endTransaction");
                        }
                    }
                };
        try {
            mDb.getDatabase().beginTransaction();
            save();
            notifyProgress.run();

            if (!mDb.getDatabase().isDatabaseIntegrityOk()) {
                return result.markAsFailed();
            }
            mDb.getDatabase().setTransactionSuccessful();
        } catch (SQLiteDatabaseLockedException ex) {
            Timber.e("doInBackgroundCheckDatabase - Database locked");
            return result.markAsLocked();
        } catch (RuntimeException e) {
            Timber.e(e, "doInBackgroundCheckDatabase - RuntimeException on marking card");
            AnkiDroidApp.sendExceptionReport(e, "doInBackgroundCheckDatabase");
            return result.markAsFailed();
        } finally {
            //if the database was locked, we never got the transaction.
            if (mDb.getDatabase().inTransaction()) {
                mDb.getDatabase().endTransaction();
            }
        }

        executeIntegrityTask.consume(this::deleteNotesWithMissingModel);
        // for each model
        for (JSONObject m : getModels().all()) {
            executeIntegrityTask.consume((callback) -> deleteCardsWithInvalidModelOrdinals(callback, m));
            executeIntegrityTask.consume((callback) -> deleteNotesWithWrongFieldCounts(callback, m));
        }
        executeIntegrityTask.consume(this::deleteNotesWithMissingCards);
        executeIntegrityTask.consume(this::deleteCardsWithMissingNotes);
        executeIntegrityTask.consume(this::removeOriginalDuePropertyWhereInvalid);
        executeIntegrityTask.consume(this::removeDynamicPropertyFromNonDynamicDecks);
        executeIntegrityTask.consume(this::removeDeckOptionsFromDynamicDecks);
        executeIntegrityTask.consume(this::resetInvalidDeckOptions);
        executeIntegrityTask.consume(this::rebuildTags);
        executeIntegrityTask.consume(this::updateFieldCache);
        executeIntegrityTask.consume(this::fixNewCardDuePositionOverflow);
        executeIntegrityTask.consume(this::resetNewCardInsertionPosition);
        executeIntegrityTask.consume(this::fixExcessiveReviewDueDates);
        // v2 sched had a bug that could create decimal intervals
        executeIntegrityTask.consume(this::fixDecimalCardsData);
        executeIntegrityTask.consume(this::fixDecimalRevLogData);
        executeIntegrityTask.consume(this::restoreMissingDatabaseIndices);
        executeIntegrityTask.consume(this::ensureModelsAreNotEmpty);
        executeIntegrityTask.consume((progressNotifier) -> this.ensureCardsHaveHomeDeck(progressNotifier, result));
        // and finally, optimize (unable to be done inside transaction).
        try {
            optimize(notifyProgress);
        } catch (Exception e) {
            Timber.e(e, "optimize");
            AnkiDroidApp.sendExceptionReport(e, "fixIntegrity - optimize");
        }
        file = new File(mPath);
        long newSize = file.length();
        result.setNewSize(newSize);
        // if any problems were found, force a full sync
        if (result.hasProblems()) {
            modSchemaNoCheck();
        }
        logProblems(result.getProblems());
        return result;
    }


    private List<String> resetInvalidDeckOptions(Runnable notifyProgress) {
        Timber.d("resetInvalidDeckOptions");
        //6454
        notifyProgress.run();

        //obtain a list of all valid dconf IDs
        List<DeckConfig> allConf = getDecks().allConf();
        HashSet<Long> configIds  = new HashSet<>();

        for (DeckConfig conf : allConf) {
            configIds.add(conf.getLong("id"));
        }

        notifyProgress.run();

        int changed = 0;

        for (Deck d : getDecks().all()) {
            //dynamic decks do not have dconf
            if (Decks.isDynamic(d)) {
                continue;
            }

            if (!configIds.contains(d.getLong("conf"))) {
                Timber.d("Reset %s's config to default", d.optString("name", "unknown deck"));
                d.put("conf", Consts.DEFAULT_DECK_CONFIG_ID);
                changed++;
            }
        }

        List<String> ret = new ArrayList<>();

        if (changed > 0) {
            ret.add("Fixed " + changed + " decks with invalid config");
            getDecks().save();
        }

        return ret;
    }


    /**
     * #5932 - a card may not have a home deck if:
     * <ul>>
     * <li>It is in a dynamic deck, and has odid = 0.</li>
     * <li>It is in a dynamic deck, and the odid refers to a dynamic deck.</li>
     * </ul>
     * Both of these cases can be fixed by moving the decks to a known-good deck
     */
    private List<String> ensureCardsHaveHomeDeck(Runnable notifyProgress, CheckDatabaseResult result) {
        Timber.d("ensureCardsHaveHomeDeck()");

        notifyProgress.run();

        //get the deck Ids to query
        Long[] dynDeckIds = getDecks().allDynamicDeckIds();
        //make it mutable
        List<Long> dynIdsAndZero = new ArrayList<>(Arrays.asList(dynDeckIds));
        dynIdsAndZero.add(0L);

        ArrayList<Long> cardIds = mDb.queryLongList("select id from cards where did in " +
                Utils.ids2str(dynDeckIds) +
                "and odid in " +
                Utils.ids2str(dynIdsAndZero));

        notifyProgress.run();

        if (cardIds.isEmpty()) {
            return Collections.emptyList();
        }

        //we use a ! prefix to keep it at the top of the deck list
        String recoveredDeckName = "! " + mContext.getString(R.string.check_integrity_recovered_deck_name);
        Long nextDeckId = getDecks().id(recoveredDeckName , true);

        if (nextDeckId == null) {
            throw new IllegalStateException("Unable to create deck");
        }

        getDecks().flush();

        mDb.execute("update cards " +
                        "set did = " + nextDeckId + ", " +
                        "odid = 0," +
                        "mod = " +  Utils.intTime() + ", " +
                        "usn = " + usn() + " " +
                        "where did in " +
                        Utils.ids2str(dynDeckIds) +
                        "and odid in " +
                        Utils.ids2str(dynIdsAndZero));


        result.setCardsWithFixedHomeDeckCount(cardIds.size());

        String message = String.format(Locale.US, "Fixed %d cards with no home deck", cardIds.size());
        return Collections.singletonList(message);
    }


    private ArrayList<String> ensureModelsAreNotEmpty(Runnable notifyProgress) {
        Timber.d("ensureModelsAreNotEmpty()");
        ArrayList<String> problems = new ArrayList<>();
        notifyProgress.run();
        if (getModels().ensureNotEmpty()) {
            problems.add("Added missing note type.");
        }
        return problems;
    }


    private ArrayList<String> restoreMissingDatabaseIndices(Runnable notifyProgress) {
        Timber.d("restoreMissingDatabaseIndices");
        ArrayList<String> problems = new ArrayList<>();
        // DB must have indices. Older versions of AnkiDroid didn't create them for new collections.
        notifyProgress.run();
        int ixs = mDb.queryScalar("select count(name) from sqlite_master where type = 'index'");
        if (ixs < 7) {
            problems.add("Indices were missing.");
            Storage.addIndices(mDb);
        }
        return problems;
    }

    private ArrayList<String> fixDecimalCardsData(Runnable notifyProgress) {
        Timber.d("fixDecimalCardsData");
        ArrayList<String> problems = new ArrayList<>();
        notifyProgress.run();
        SupportSQLiteStatement s = mDb.getDatabase().compileStatement(
                "update cards set ivl=round(ivl),due=round(due) where ivl!=round(ivl) or due!=round(due)");
        int rowCount = s.executeUpdateDelete();
        if (rowCount > 0) {
            problems.add("Fixed " + rowCount + " cards with v2 scheduler bug.");
        }
        return problems;
    }


    private ArrayList<String> fixDecimalRevLogData(Runnable notifyProgress) {
        Timber.d("fixDecimalRevLogData()");
        ArrayList<String> problems = new ArrayList<>();
        notifyProgress.run();
        SupportSQLiteStatement s = mDb.getDatabase().compileStatement(
                "update revlog set ivl=round(ivl),lastIvl=round(lastIvl) where ivl!=round(ivl) or lastIvl!=round(lastIvl)");
        int rowCount = s.executeUpdateDelete();
        if (rowCount > 0) {
            problems.add("Fixed " + rowCount + " review history entries with v2 scheduler bug.");
        }
        return problems;
    }


    private ArrayList<String> fixExcessiveReviewDueDates(Runnable notifyProgress) {
        Timber.d("fixExcessiveReviewDueDates()");
        ArrayList<String> problems = new ArrayList<>();
        notifyProgress.run();
        // reviews should have a reasonable due #
        ArrayList<Long> ids = mDb.queryLongList("SELECT id FROM cards WHERE queue = " + Consts.QUEUE_TYPE_REV + " AND due > 100000");
        notifyProgress.run();
        if (ids.size() > 0) {
            problems.add("Reviews had incorrect due date.");
            mDb.execute("UPDATE cards SET due = " + mSched.getToday() + ", ivl = 1, mod = " +  Utils.intTime() +
                    ", usn = " + usn() + " WHERE id IN " + Utils.ids2str(Utils.collection2Array(ids)));
        }
        return problems;
    }


    private List<String> resetNewCardInsertionPosition(Runnable notifyProgress) throws JSONException {
        Timber.d("resetNewCardInsertionPosition");
        notifyProgress.run();
        // new card position
        mConf.put("nextPos", mDb.queryScalar("SELECT max(due) + 1 FROM cards WHERE type = " + Consts.CARD_TYPE_NEW));
        return Collections.emptyList();
    }


    private List<String> fixNewCardDuePositionOverflow(Runnable notifyProgress) {
        Timber.d("fixNewCardDuePositionOverflow");
        // new cards can't have a due position > 32 bits
        notifyProgress.run();
        mDb.execute("UPDATE cards SET due = 1000000, mod = " + Utils.intTime() + ", usn = " + usn()
                + " WHERE due > 1000000 AND type = " + Consts.CARD_TYPE_NEW);
        return Collections.emptyList();
    }


    private List<String> updateFieldCache(Runnable notifyProgress) {
        Timber.d("updateFieldCache");
        // field cache
        for (Model m : getModels().all()) {
            notifyProgress.run();
            updateFieldCache(Utils.collection2Array(getModels().nids(m)));
        }
        return Collections.emptyList();
    }


    private List<String> rebuildTags(Runnable notifyProgress) {
        Timber.d("rebuildTags");
        // tags
        notifyProgress.run();
        mTags.registerNotes();
        return Collections.emptyList();
    }


    private ArrayList<String> removeDeckOptionsFromDynamicDecks(Runnable notifyProgress) {
        Timber.d("removeDeckOptionsFromDynamicDecks()");
        ArrayList<String> problems = new ArrayList<>();
        //#5708 - a dynamic deck should not have "Deck Options"
        notifyProgress.run();
        int fixCount = 0;
        for (long id : mDecks.allDynamicDeckIds()) {
            try {
                if (mDecks.hasDeckOptions(id)) {
                    mDecks.removeDeckOptions(id);
                    fixCount++;
                }
            } catch (NoSuchDeckException e) {
                Timber.e("Unable to find dynamic deck %d", id);
            }
        }
        if (fixCount > 0) {
            mDecks.save();
            problems.add(String.format(Locale.US, "%d dynamic deck(s) had deck options.", fixCount));
        }
        return problems;
    }


    private ArrayList<String> removeDynamicPropertyFromNonDynamicDecks(Runnable notifyProgress) {
        Timber.d("removeDynamicPropertyFromNonDynamicDecks()");
        ArrayList<String> problems = new ArrayList<>();
        ArrayList<Long> dids = new ArrayList<>();
        for (long id : mDecks.allIds()) {
            if (!mDecks.isDyn(id)) {
                dids.add(id);
            }
        }
        notifyProgress.run();
        // cards with odid set when not in a dyn deck
        ArrayList<Long> ids = mDb.queryLongList(
                "select id from cards where odid > 0 and did in " + Utils.ids2str(dids));
        notifyProgress.run();
        if (ids.size() != 0) {
            problems.add("Fixed " + ids.size() + " card(s) with invalid properties.");
            mDb.execute("update cards set odid=0, odue=0 where id in " + Utils.ids2str(ids));
        }
        return problems;
    }


    private ArrayList<String> removeOriginalDuePropertyWhereInvalid(Runnable notifyProgress) {
        Timber.d("removeOriginalDuePropertyWhereInvalid()");
        ArrayList<String> problems = new ArrayList<>();
        notifyProgress.run();
        // cards with odue set when it shouldn't be
        ArrayList<Long> ids = mDb.queryLongList(
                "select id from cards where odue > 0 and (type= " + Consts.CARD_TYPE_LRN + " or queue=" + Consts.QUEUE_TYPE_REV + ") and not odid");
        notifyProgress.run();
        if (ids.size() != 0) {
            problems.add("Fixed " + ids.size() + " card(s) with invalid properties.");
            mDb.execute("update cards set odue=0 where id in " + Utils.ids2str(ids));
        }
        return problems;
    }


    private ArrayList<String> deleteCardsWithMissingNotes(Runnable notifyProgress) {
        Timber.d("deleteCardsWithMissingNotes()");
        ArrayList<String> problems = new ArrayList<>();
        ArrayList<Long> ids;// cards with missing notes
        notifyProgress.run();
        ids = mDb.queryLongList(
                "SELECT id FROM cards WHERE nid NOT IN (SELECT id FROM notes)");
        notifyProgress.run();
        if (ids.size() != 0) {
            problems.add("Deleted " + ids.size() + " card(s) with missing note.");
            remCards(ids);
        }
        return problems;
    }


    private ArrayList<String> deleteNotesWithMissingCards(Runnable notifyProgress) {
        Timber.d("deleteNotesWithMissingCards()");
        ArrayList<String> problems = new ArrayList<>();
        ArrayList<Long> ids;
        notifyProgress.run();
        // delete any notes with missing cards
        ids = mDb.queryLongList(
                "SELECT id FROM notes WHERE id NOT IN (SELECT DISTINCT nid FROM cards)");
        notifyProgress.run();
        if (ids.size() != 0) {
            problems.add("Deleted " + ids.size() + " note(s) with missing no cards.");
            _remNotes(ids);
        }
        return problems;
    }


    private ArrayList<String> deleteNotesWithWrongFieldCounts(Runnable notifyProgress, JSONObject m) throws JSONException {
        Timber.d("deleteNotesWithWrongFieldCounts");
        ArrayList<String> problems = new ArrayList<>();
        ArrayList<Long> ids;// notes with invalid field counts
        ids = new ArrayList<>();
        Cursor cur = null;
        try {
            notifyProgress.run();
            cur = mDb.getDatabase().query("select id, flds from notes where mid = " + m.getLong("id"), null);
            Timber.i("cursor size: %d", cur.getCount());
            int currentRow = 0;

            //Since we loop through all rows, we only want one exception
            @Nullable Exception firstException = null;
            while (cur.moveToNext()) {
                try {
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
                } catch (IllegalStateException ex) {
                    // DEFECT: Theory that is this an OOM is discussed in #5852
                    // We store one exception to stop excessive logging
                    Timber.i(ex,  "deleteNotesWithWrongFieldCounts - Exception on row %d. Columns: %d", currentRow, cur.getColumnCount());
                    if (firstException == null) {
                        String details = String.format(Locale.ROOT, "deleteNotesWithWrongFieldCounts row: %d col: %d",
                                currentRow,
                                cur.getColumnCount());
                        AnkiDroidApp.sendExceptionReport(ex, details);
                        firstException = ex;
                    }
                }
                currentRow++;
            }
            Timber.i("deleteNotesWithWrongFieldCounts - completed successfully");
            notifyProgress.run();
            if (ids.size() > 0) {
                problems.add("Deleted " + ids.size() + " note(s) with wrong field count.");
                _remNotes(ids);
            }
        } finally {
            if (cur != null && !cur.isClosed()) {
                cur.close();
            }
        }
        return problems;
    }


    private ArrayList<String> deleteCardsWithInvalidModelOrdinals(Runnable notifyProgress, JSONObject m) throws JSONException {
        Timber.d("deleteCardsWithInvalidModelOrdinals()");
        ArrayList<String> problems = new ArrayList<>();
        notifyProgress.run();
        if (m.getInt("type") == Consts.MODEL_STD) {
            ArrayList<Integer> ords = new ArrayList<>();
            JSONArray tmpls = m.getJSONArray("tmpls");
            for (int t = 0; t < tmpls.length(); t++) {
                ords.add(tmpls.getJSONObject(t).getInt("ord"));
            }
            // cards with invalid ordinal
            ArrayList<Long> ids = mDb.queryLongList(
                    "SELECT id FROM cards WHERE ord NOT IN " + Utils.ids2str(ords) + " AND nid IN ( " +
                            "SELECT id FROM notes WHERE mid = ?)", m.getLong("id"));
            if (ids.size() > 0) {
                problems.add("Deleted " + ids.size() + " card(s) with missing template.");
                remCards(ids);
            }
        }
        return problems;
    }


    private ArrayList<String> deleteNotesWithMissingModel(Runnable notifyProgress) {
        Timber.d("deleteNotesWithMissingModel()");
        ArrayList<String> problems = new ArrayList<>();
        // note types with a missing model
        notifyProgress.run();
        ArrayList<Long> ids = mDb.queryLongList(
                "SELECT id FROM notes WHERE mid NOT IN " + Utils.ids2str(getModels().ids()));
        notifyProgress.run();
        if (ids.size() != 0) {
            problems.add("Deleted " + ids.size() + " note(s) with missing note type.");
            _remNotes(ids);
        }
        return problems;
    }


    public void optimize(Runnable progressCallback) {
        Timber.i("executing VACUUM statement");
        progressCallback.run();
        mDb.execute("VACUUM");
        Timber.i("executing ANALYZE statement");
        progressCallback.run();
        mDb.execute("ANALYZE");
    }


    private void fixIntegrityProgress(CollectionTask.ProgressCallback progressCallback, int current, int total) {
        progressCallback.publishProgress(new TaskData(
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
    private void logProblems(List<String> integrityCheckProblems) {

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
     * Card Flags *****************************************************************************************************
     */
    public void setUserFlag(int flag, long[] cids)  {
        assert (0<= flag && flag <= 7);
        mDb.execute("update cards set flags = (flags & ~?) | ?, usn=?, mod=? where id in " + Utils.ids2str(cids),
                    0b111, flag, usn(), Utils.intTime());
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


    /**
     * On first call, load the model if it was not loaded.
     *
     * Synchronized to ensure that loading does not occur twice.
     * Normally the first call occurs in the background when
     * collection is loaded.  The only exception being if the user
     * perform an action (e.g. review) so quickly that
     * loadModelsInBackground had no time to be called. In this case
     * it will instantly finish. Note that loading model is a
     * bottleneck anyway, so background call lose all interest.
     *
     * @return The model manager
     */
    public synchronized Models getModels() {
        if (mModels == null) {
            mModels = new Models(this);
            mModels.load(loadColumn("models"));
        }
        return mModels;
    }

    /** Check if this collection is valid. */
    public boolean validCollection() {
    	//TODO: more validation code
    	return getModels().validateModel();
    }

    public JSONObject getConf() {
        return mConf;
    }


    public void setConf(JSONObject conf) {
        // Anki sometimes set sortBackward to 0/1 instead of
        // False/True. This should be repaired before setting mConf;
        // otherwise this may save a faulty value in mConf, and if
        // it's done just before the value is read, this may lead to
        // bug #5523. This bug should occur only for people using anki
        // prior to version 2.16 and has been corrected with
        // dae/anki#347
        Upgrade.upgradeJSONIfNecessary(this, conf, "sortBackwards", false);
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


    public AbstractSched getSched() {
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

    /** Not in libAnki */

    //This duplicates _loadScheduler (but returns the value and sets the report limit).
    public AbstractSched createScheduler(int reportLimit) {
        int ver = schedVer();
        if (ver == 1) {
            mSched = new Sched(this);
        } else if (ver == 2) {
            mSched = new SchedV2(this);
        }
        mSched.setReportLimit(reportLimit);
        return mSched;
    }


    //This duplicates _loadScheduler (but returns the value and sets the report limit).
    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    public void replaceSchedulerForTests(Time time) {
        int ver = schedVer();
        if (ver == 1) {
            throw new IllegalStateException("Not Implemented");
        } else if (ver == 2) {
            mSched = new SchedV2(this, time);
        }
    }

    /** Allows a mock db to be inserted for testing */
    @VisibleForTesting
    public void setDb(DB database) {
        this.mDb = database;
    }

    public static class CheckDatabaseResult {
        private final List<String> mProblems = new ArrayList<>();
        private long mOldSize;
        private int mFixedCardsWithNoHomeDeckCount;
        private long mNewSize;
        /** When the database was locked */
        private boolean mLocked = false;
        /** When the check failed with an error (or was locked) */
        private boolean mFailed = false;


        public CheckDatabaseResult(long oldSize) {
            mOldSize = oldSize;
        }

        public void addAll(List<String> strings) {
            mProblems.addAll(strings);
        }

        public void setCardsWithFixedHomeDeckCount(int count) {
            this.mFixedCardsWithNoHomeDeckCount = count;
        }

        public boolean hasProblems() {
            return mProblems.size() > 0;
        }


        public List<String> getProblems() {
            return mProblems;
        }


        public int getCardsWithFixedHomeDeckCount() {
            return mFixedCardsWithNoHomeDeckCount;
        }

        public void setNewSize(long size) {
            this.mNewSize = size;
        }

        public double getSizeChangeInKb() {
            return (mOldSize - mNewSize) / 1024.0;
        }


        public void setFailed(boolean failedIntegrity) {
            this.mFailed = failedIntegrity;
        }

        public CheckDatabaseResult markAsFailed() {
            this.setFailed(true);
            return this;
        }

        public CheckDatabaseResult markAsLocked() {
            this.setLocked(true);
            return markAsFailed();
        }

        private void setLocked(boolean value) {
            mLocked = value;
        }

        public boolean getDatabaseLocked() {
            return mLocked;
        }


        public boolean getFailed() {
            return mFailed;
        }
    }
}
