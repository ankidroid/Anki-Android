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
import android.content.res.Resources;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import com.ichi2.anki.AnkiDatabaseManager;
import com.ichi2.anki.AnkiDb;
import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.Pair;
import com.ichi2.anki.R;
import com.ichi2.anki.UIUtils;
import com.ichi2.async.DeckTask;
import com.samskivert.mustache.Mustache;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Anki maintains a cache of used tags so it can quickly present a list of tags
// for autocomplete and in the browser. For efficiency, deletions are not
// tracked, so unused tags can only be removed from the list with a DB check.
//
// This module manages the tag cache and tags for notes.

public class Collection {

    // collection schema & syncing vars
    public static final int SCHEMA_VERSION = 11;
    public static final String SYNC_URL = "https://ankiweb.net/";
    public static final int SYNC_VER = 5;
    public static final String HELP_SITE = "http://ankisrs.net/docs/dev/manual.html";

    private AnkiDb mDb;
    private boolean mServer;
    private double mLastSave;
    private Media mMedia;
    private Decks mDecks;
    private Models mModels;
    private Tags mTags;

    private Sched mSched;

    private double mStartTime;
    private int mStartReps;
    private boolean mOvertime;

    private int mRepsToday;

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

    // Cloze regex
    private static final Pattern fClozePattern = Pattern.compile("\\{\\{cloze:");
    private static final Pattern fAltClozePattern = Pattern.compile("<%cloze:");
    // other options
    public static final String defaultConf = "{"
            +
            // review options
            "'activeDecks': [1], " + "'curDeck': 1, " + "'newSpread': " + Sched.NEW_CARDS_DISTRIBUTE + ", "
            + "'collapseTime': 1200, " + "'timeLim': 0, " + "'estTimes': True, " + "'dueCounts': True, "
            +
            // other config
            "'curModel': None, " + "'nextPos': 1, " + "'sortType': \"noteFld\", "
            + "'sortBackwards': False, 'addToCur': True }"; // add new to currently selected deck?

    public static final int UNDO_REVIEW = 0;
    public static final int UNDO_EDIT_NOTE = 1;
    public static final int UNDO_BURY_NOTE = 2;
    public static final int UNDO_SUSPEND_CARD = 3;
    public static final int UNDO_SUSPEND_NOTE = 4;
    public static final int UNDO_DELETE_NOTE = 5;
    public static final int UNDO_MARK_NOTE = 6;
    private static final int[] fUndoNames = new int[]{
        R.string.undo_action_review,
        R.string.undo_action_edit,
        R.string.undo_action_bury,
        R.string.undo_action_suspend_card,
        R.string.undo_action_suspend_note,
        R.string.undo_action_delete,
        R.string.undo_action_mark};
    
    private static final int UNDO_SIZE_MAX = 20;

    public Collection(AnkiDb db, String path) {
        this(db, path, false);
    }


    public Collection(AnkiDb db, String path, boolean server) {
        mDb = db;
        mServer = server;
        mLastSave = Utils.now();
        clearUndo();
        mPath = path;
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
        mOvertime = false;
        mSched = new Sched(this);
        // check for improper shutdown
        cleanup();
    }


    public String name() {
        String n = (new File(mPath)).getName().replace(".anki2", "");
        // TODO:
        return n;
    }


    /**
     * DB-related *************************************************************** ********************************
     */

    public boolean load() {
        Cursor cursor = null;
        try {
            // Read in deck table columns
            cursor = mDb.getDatabase().rawQuery(
                    "SELECT crt, mod, scm, dty, usn, ls, conf, " + "models, decks, dconf, tags FROM col", null);
            if (!cursor.moveToFirst()) {
                return false;
            }
            mCrt = cursor.getLong(0);
            mMod = cursor.getLong(1);
            mScm = cursor.getLong(2);
            mDty = cursor.getInt(3) == 1;
            mUsn = cursor.getInt(4);
            mLs = cursor.getLong(5);
            try {
                mConf = new JSONObject(cursor.getString(6));
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
            mModels.load(cursor.getString(7));
            mDecks.load(cursor.getString(8), cursor.getString(9));
            mTags.load(cursor.getString(10));
            return true;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
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
        Log.i(AnkiDroidApp.TAG, "flush - Saving information to DB...");
        mMod = (mod == 0 ? Utils.intNow(1000) : mod);
        ContentValues values = new ContentValues();
        values.put("crt", mCrt);
        values.put("mod", mMod);
        values.put("scm", mScm);
        values.put("dty", mDty ? 1 : 0);
        values.put("usn", mUsn);
        values.put("ls", mLs);
        values.put("conf", mConf.toString());
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
        mLastSave = Utils.now();
    }


    /** Save if 5 minutes has passed since last save. */
    public void autosave() {
        if ((Utils.now() - mLastSave) > 300) {
            save();
        }
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
        // if (wait) {
        // Wait for any thread working on the deck to finish.
        // DeckTask.waitToFinish();
        // }
        if (mDb != null) {
            cleanup();
            try {
                SQLiteDatabase db = getDb().getDatabase();
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
                    lock();
                }            	
            } catch (RuntimeException e) {
    			AnkiDroidApp.saveExceptionReportFile(e, "closeDB");
            }
            AnkiDatabaseManager.closeDatabase(mPath);
            mDb = null;
            mMedia.close();
            Log.i(AnkiDroidApp.TAG, "Collection closed");
        }
    }


    public void reopen() {
        if (mDb == null) {
            mDb = AnkiDatabaseManager.getDatabase(mPath);
            mMedia.connect();
        }
    }


    /** Mark schema modified. Call this first so user can abort if necessary. */
    public void modSchema() {
        modSchema(true);
    }


    public void modSchema(boolean check) {
        if (!schemaChanged()) {
            if (check) {
                // TODO: ask user
            }
        }
        mScm = Utils.intNow(1000);
    }


    /** True if schema changed since last sync. */
    public boolean schemaChanged() {
        return mScm > mLs;
    }


    /**
     * Signal there are temp. suspended cards that need cleaning up on close.
     */
    public void setDirty() {
        mDty = true;
    }
    public void setDirty(boolean dty) {
        mDty = dty;
    }


    /**
     * Unsuspend any temporarily suspended cards.
     */
    public void cleanup() {
        if (mDty) {
            mSched.unburyCards();
            mDty = false;
        }
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
        String[] tables = new String[] { "notes", "cards", "revlog", "graves" };
        for (String t : tables) {
            mDb.execute("UPDATE " + t + " SET usn=0 WHERE usn=-1");
        }
        mUsn += 1;
        mModels.beforeUpload();
        mTags.beforeUpload();
        mDecks.beforeUpload();
        modSchema();
        mLs = mScm;
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
        type = "next" + type.toUpperCase();
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
            mDb.insert("graves", null, values);
        }
    }


    /**
     * Notes ******************************************************************** ***************************
     */

    public int noteCount() {
        return (int) mDb.queryScalar("SELECT count() FROM notes");
    }


    public Note newNote() {
        return newNote(mModels.current());
    }


    /**
     * Return a new note with the current model.
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
        _logRem(ids, Sched.REM_NOTE);
        mDb.execute("DELETE FROM notes WHERE id IN " + strids);
    }


    /**
     * Card creation ************************************************************ ***********************************
     */

    /**
     * @return (active), non-empty templates.
     */
    private ArrayList<JSONObject> findTemplates(Note note) {
        JSONObject model = note.model();
        ArrayList<Integer> avail = mModels.availOrds(model, Utils.joinFields(note.getFields()));
        return _tmplsFromOrds(model, avail);
    }


    private ArrayList<JSONObject> _tmplsFromOrds(JSONObject model, ArrayList<Integer> avail) {
        ArrayList<JSONObject> ok = new ArrayList<JSONObject>();
        JSONArray tmpls;
        try {
            if (model.getInt("type") == Sched.MODEL_STD) {
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
        HashMap<Long, HashMap<Integer, Long>> have = new HashMap<Long, HashMap<Integer, Long>>();
        HashMap<Long, Long> dids = new HashMap<Long, Long>();
        Cursor cur = null;
        try {
            cur = mDb.getDatabase().rawQuery("SELECT id, nid, ord, did FROM cards WHERE nid IN " + snids, null);
            while (cur.moveToNext()) {
                // existing cards
                long nid = cur.getLong(1);
                if (!have.containsKey(nid)) {
                    have.put(nid, new HashMap<Integer, Long>());
                }
                have.get(nid).put(cur.getInt(2), cur.getLong(0));
                // and their dids
                long did = cur.getLong(3);
                if (dids.containsKey(nid)) {
                    if (dids.get(nid) != 0 && dids.get(nid) != did) {
                        // cards are in two or more different decks; revert to model default
                        dids.put(nid, 0l);
                    }
                } else {
                    // first card or multiple cards in same deck
                    dids.put(nid, did);
                }
            }
        } finally {
            if (cur != null && !cur.isClosed()) {
                cur.close();
            }
        }
        // build cards for each note
        ArrayList<Object[]> data = new ArrayList<Object[]>();
        long ts = Utils.maxID(mDb);
        long now = Utils.intNow();
        ArrayList<Long> rem = new ArrayList<Long>();
        int usn = usn();
        cur = null;
        try {
            cur = mDb.getDatabase().rawQuery("SELECT id, mid, flds FROM notes WHERE id IN " + snids, null);
            while (cur.moveToNext()) {
                JSONObject model = mModels.get(cur.getLong(1));
                ArrayList<Integer> avail = mModels.availOrds(model, cur.getString(2));
                long nid = cur.getLong(0);
                long did = dids.get(nid);
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
                        // we'd like to use the same due# as sibling cards, but we can't retrieve that quickly, so we
                        // give it a new id instead
                        data.add(new Object[] { ts, nid, did, tord, now, usn, nextID("pos") });
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
	public List<Card> previewCards(Note note) {
	    return previewCards(note, 0);
	}
	public List<Card> previewCards(Note note, int type) {
	    ArrayList<JSONObject> cms = null;
	    if (type == 0) {
	        cms = findTemplates(note);
	    } else if (type == 1) {
	        cms = new ArrayList<JSONObject>();
	        for (Card c : note.cards()) {
	            cms.add(c.template());
	        }
	    } else {
	        cms = new ArrayList<JSONObject>();
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
	        return new ArrayList<Card>();
	    }
	    List<Card> cards = new ArrayList<Card>();
	    for (JSONObject template : cms) {
	        cards.add(_newCard(note, template, 1, false));
	    }
	    return cards;
	}


    /**
     * Create a new card.
     */
    private Card _newCard(Note note, JSONObject template, int due) {
        return _newCard(note, template, due, true);
    }


    private Card _newCard(Note note, JSONObject template, int due, boolean flush) {
        Card card = new Card(this);
        card.setNid(note.getId());
        try {
            card.setOrd(template.getInt("ord"));
        } catch (JSONException e) {
            new RuntimeException(e);
        }
        long did;
        try {
            did = template.getLong("did");
        } catch (JSONException e) {
            did = 0;
        }
        try {
            card.setDid(did != 0 ? did : note.model().getLong("did"));
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
            if (conf.getJSONObject("new").getInt("order") == Sched.NEW_CARDS_DUE) {
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
        return mDb.queryScalar("SELECT 1 FROM cards LIMIT 1", false) == 0;
    }


    public int cardCount() {
        return mDb.queryScalar("SELECT count() FROM cards");
    }


    // NOT IN LIBANKI //
    public int cardCount(long[] ls) {
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
        _logRem(ids, Sched.REM_CARD);
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
		List<Long> rem = new ArrayList<Long>();
		for (JSONObject m : getModels().all()) {
			rem.addAll(genCards(getModels().nids(m)));
		}
	return rem;
    }

    // emptyCardReport

    /**
     * Field checksums and sorting fields ***************************************
     * ********************************************************
     */

    private ArrayList<Object[]> _fieldData(String snids) {
        ArrayList<Object[]> result = new ArrayList<Object[]>();
        Cursor cur = null;
        try {
            cur = mDb.getDatabase().rawQuery("SELECT id, mid, flds FROM notes WHERE id IN " + snids, null);
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
        ArrayList<Object[]> r = new ArrayList<Object[]>();
        for (Object[] o : _fieldData(snids)) {
            String[] fields = Utils.splitFields((String) o[2]);
            JSONObject model = mModels.get((Long) o[1]);
            if (model == null) {
                // note point to invalid model
                continue;
            }
            r.add(new Object[] { Utils.stripHTML(fields[mModels.sortIdx(model)]), Utils.fieldChecksum(fields[0]), o[0] });
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
        if (type.equals("card")) {
            where = "AND c.id IN " + Utils.ids2str(ids);
        } else if (type.equals("fact")) {
            where = "AND f.id IN " + Utils.ids2str(ids);
        } else if (type.equals("model")) {
            where = "AND m.id IN " + Utils.ids2str(ids);
        } else if (type.equals("all")) {
            where = "";
        } else {
            throw new RuntimeException();
        }
        ArrayList<HashMap<String, String>> result = new ArrayList<HashMap<String, String>>();
        for (Object[] row : _qaData(where)) {
            result.add(_renderQA(row));
        }
        return result;
    }


    /**
     * Returns hash of id, question, answer.
     */
    public HashMap<String, String> _renderQA(Object[] data) {
        return _renderQA(data, null);
    }


    public HashMap<String, String> _renderQA(Object[] data, List<String> args) {
        // data is [cid, nid, mid, did, ord, tags, flds]
        // unpack fields and create dict
        String[] flist = Utils.splitFields((String) data[6]);
        Map<String, String> fields = new HashMap<String, String>();
        long modelId = (Long) data[2];
        JSONObject model = mModels.get(modelId);
        Map<String, Pair<Integer, JSONObject>> fmap = mModels.fieldMap(model);
        for (String fname : fmap.keySet()) {
            fields.put(fname, flist[fmap.get(fname).first]);
        }
        fields.put("Tags", (String) data[5]);
        try {
            fields.put("Type", (String) model.get("name"));
            fields.put("Deck", mDecks.name((Long) data[3]));
            JSONObject template;
            if (model.getInt("type") == Sched.MODEL_STD) {
                template = model.getJSONArray("tmpls").getJSONObject((Integer) data[4]);
            } else {
                template = model.getJSONArray("tmpls").getJSONObject(0);
            }
            fields.put("Card", template.getString("name"));
            fields.put("c" + (((Integer) data[4]) + 1), "1");

            // render q & a
            HashMap<String, String> d = new HashMap<String, String>();
            d.put("id", Long.toString((Long) data[0]));
            String qfmt = template.getString("qfmt");
            String afmt = template.getString("afmt");
            String html;
            String format;

            // runFilter mungeFields for type "q"
            Models.fieldParser fparser = new Models.fieldParser(fields);
            Matcher m = fClozePattern.matcher(qfmt);
            format = m.replaceFirst("{{cq:" + String.valueOf(((Integer) data[4]) + 1) + ":");
            m = fAltClozePattern.matcher(format);
            format = m.replaceFirst("<%%cq:" + String.valueOf(((Integer) data[4]) + 1) + ":");
            html = mModels.getCmpldTemplate(format).execute(fparser);
            html = (String) AnkiDroidApp.getHooks().runFilter("mungeQA", html, "q", fields, model, data, this);
            d.put("q", html);
            // empty cloze?
            if (model.getInt("type") == Sched.MODEL_CLOZE) {
                if (getModels()._availClozeOrds(model, (String) data[6], false).size() == 0) {
                    d.put("q", "Please edit this note and add some cloze deletions.");
                }
            }
            fields.put("FrontSide", mMedia.stripAudio(d.get("q")));

            // runFilter mungeFields for type "a"
            fparser = new Models.fieldParser(fields);
            m = fClozePattern.matcher(afmt);
            format = m.replaceFirst("{{ca:" + String.valueOf(((Integer) data[4]) + 1) + ":");
            m = fAltClozePattern.matcher(format);
            format = m.replaceFirst("<%%ca:" + String.valueOf(((Integer) data[4]) + 1) + ":");
            html = mModels.getCmpldTemplate(format).execute(fparser);
            html = (String) AnkiDroidApp.getHooks().runFilter("mungeQA", html, "a", fields, model, data, this);
            d.put("a", html);
            // empty cloze?
            if (model.getInt("type") == Sched.MODEL_CLOZE) {
                if (getModels()._availClozeOrds(model, (String) data[6], false).size() == 0) {
                    d.put("q",
                            AnkiDroidApp.getAppResources().getString(
                                    com.ichi2.anki.R.string.empty_cloze_warning,
                                    "<a href=" + HELP_SITE + "#cloze>" +
                                    AnkiDroidApp.getAppResources().getString(
                                            com.ichi2.anki.R.string.help_cloze) + "</a>"));
                }
            }

            return d;
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }


    /**
     * Return [cid, nid, mid, did, ord, tags, flds] db query
     */
    public ArrayList<Object[]> _qaData() {
        return _qaData("");
    }


    public ArrayList<Object[]> _qaData(String where) {
        ArrayList<Object[]> data = new ArrayList<Object[]>();
        Cursor cur = null;
        try {
            cur = mDb.getDatabase().rawQuery(
                    "SELECT c.id, n.id, n.mid, c.did, c.ord, "
                            + "n.tags, n.flds FROM cards c, notes n WHERE c.nid == n.id " + where, null);
            while (cur.moveToNext()) {
                data.add(new Object[] { cur.getLong(0), cur.getLong(1), cur.getLong(2), cur.getLong(3), cur.getInt(4),
                        cur.getString(5), cur.getString(6) });
            }
        } finally {
            if (cur != null && !cur.isClosed()) {
                cur.close();
            }
        }
        return data;
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


    /** Return a list of card ids */
    public List<Long> findCards(String search, Boolean order) {
        return new Finder(this).findCards(search, order.toString());
    }
    
    public ArrayList<HashMap<String, String>> findCardsForCardBrowser(String search, String order, HashMap<String, String> deckNames) {
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


    public void setOvertime(boolean overtime) {
        mOvertime = overtime;
    }


    public boolean getOvertime() {
        return mOvertime;
    }


    public void startTimebox() {
        mStartTime = Utils.now();
        mStartReps = mRepsToday;
    }


    /* Return (elapsedTime, reps) if timebox reached, or null. */
    public Long[] timeboxReached() {
        try {
            if (mConf.getLong("timeLim") == 0) {
                // timeboxing disabled
                return null;
            }
            double elapsed = Utils.now() - mStartTime;
            if (elapsed > mConf.getLong("timeLim") && !mOvertime) {
                return new Long[] { mConf.getLong("timeLim"), (long) (mRepsToday - mStartReps) };
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
        mUndo = new LinkedList<Object[]>();
    }


    /** Undo menu item name, or "" if undo unavailable. */
    public String undoName(Resources res) {
        if (mUndo.size() > 0) {
            int undoType = ((Integer) mUndo.getLast()[0]).intValue();
            if (undoType >= 0 && undoType < fUndoNames.length) {
                return res.getString(fUndoNames[undoType]);
            }
        }
        return "";
    }


    public boolean undoAvailable() {
        return mUndo.size() > 0;
    }


    public long undo() {
    	Object[] data = mUndo.removeLast();
    	switch ((Integer)data[0]) {
    	case UNDO_REVIEW:
            Card c = (Card) data[1];
            // write old data
            c.flush(false);
            // and delete revlog entry
            long last = mDb.queryLongScalar("SELECT id FROM revlog WHERE cid = " + c.getId() + " ORDER BY id DESC LIMIT 1");
            mDb.execute("DELETE FROM revlog WHERE id = " + last);
            // and finally, update daily count
            int n = c.getQueue() == 3 ? 1 : c.getQueue();
            String type = (new String[] { "new", "lrn", "rev" })[n];
            mSched._updateStats(c, type, -1);
            mSched.mReps -= 1;
            return c.getId();

    	case UNDO_EDIT_NOTE:
    		Note note = (Note) data[1];
    		note.flush(note.getMod(), false);
    		long cid = (Long) data[2];
            Card card = null;
            if ((Boolean) data[3]) {
                Card newCard = getCard(cid);
                if (getDecks().active().contains(newCard.getDid())) {
                    card = newCard;
                    card.load();
                    // reload qa-cache
                    card.getQuestion(true);
                }
            }
            if (card == null) {
            	card = getSched().getCard();
            }
            if (card != null) {
            	return card.getId();
            }
    		return 0;

    	case UNDO_BURY_NOTE:
    		setDirty((Boolean)data[1]);
    		for (Card cc : (ArrayList<Card>)data[2]) {
    			cc.flush(false);
    		}
    		return (Long) data[3];

    	case UNDO_SUSPEND_CARD:
    		Card suspendedCard = (Card)data[1];
    		suspendedCard.flush(false);
    		return suspendedCard.getId();

    	case UNDO_SUSPEND_NOTE:
    		for (Card ccc : (ArrayList<Card>) data[1]) {
    			ccc.flush(false);
    		}
    		return (Long) data[2];

    	case UNDO_DELETE_NOTE:
    		ArrayList<Long> ids = new ArrayList<Long>();
    		Note note2 = (Note)data[1];
    		note2.flush(note2.getMod(), false);
    		ids.add(note2.getId());
        		for (Card c4 : (ArrayList<Card>) data[2]) {
        			c4.flush(false);
    			ids.add(c4.getId());
        		}
    		mDb.execute("DELETE FROM graves WHERE oid IN " + Utils.ids2str(Utils.arrayList2array(ids)));
    		return (Long) data[3];

    	case UNDO_MARK_NOTE:
    		Note note3 = getNote((Long) data[1]);
    		note3.setTagsFromStr((String) data[2]);
    		note3.flush(note3.getMod(), false);
    		return (Long) data[3];

        default:
        	return 0;
    	}
    }


    public void markUndo(int type, Object[] o) {
    	switch(type) {
    	case UNDO_REVIEW:
    		mUndo.add(new Object[]{type, ((Card)o[0]).clone()});
    		break;
    	case UNDO_EDIT_NOTE:
    		mUndo.add(new Object[]{type, ((Note)o[0]).clone(), o[1], o[2]});
    		break;
    	case UNDO_BURY_NOTE:
    		mUndo.add(new Object[]{type, o[0], o[1], o[2]});
    		break;
    	case UNDO_SUSPEND_CARD:
    		mUndo.add(new Object[]{type, ((Card)o[0]).clone()});
    		break;
    	case UNDO_SUSPEND_NOTE:
    		mUndo.add(new Object[]{type, o[0], o[1]});
    		break;
    	case UNDO_DELETE_NOTE:
    		mUndo.add(new Object[]{type, o[0], o[1], o[2]});
    		break;
    	case UNDO_MARK_NOTE:
    		mUndo.add(new Object[]{type, o[0], o[1], o[2]});
    		break;
    	}
    	while (mUndo.size() > UNDO_SIZE_MAX) {
    		mUndo.removeFirst();
    	}
    }

    /**
     * DB maintenance *********************************************************** ************************************
     */

    /** Fix possible problems and rebuild caches. */
    public long fixIntegrity() {
        File file = new File(mPath);
        ArrayList<String> problems = new ArrayList<String>();
        long oldSize = file.length();
        try {
            mDb.getDatabase().beginTransaction();
            try {
                save();
                if (!mDb.queryString("PRAGMA integrity_check").equals("ok")) {
                    return -1;
                }
                // note types with a missing model
                ArrayList<Long> ids = mDb.queryColumn(Long.class,
                        "SELECT id FROM notes WHERE mid NOT IN " + Utils.ids2str(mModels.ids()), 0);
                if (ids.size() != 0) {
                	problems.add("Deleted " + ids.size() + " note(s) with missing note type.");
	                _remNotes(Utils.arrayList2array(ids));
                }
                // cards with invalid ordinal
                for (JSONObject m : mModels.all()) {
                	// ignore clozes
                	if (m.getInt("type") != Sched.MODEL_STD) {
                		continue;
                	}
                	ArrayList<Integer> ords = new ArrayList<Integer>();
                	JSONArray tmpls = m.getJSONArray("tmpls");
                	for (int t = 0; t < tmpls.length(); t++) {
                		ords.add(tmpls.getJSONObject(t).getInt("ord"));
                	}
                	ids = mDb.queryColumn(Long.class,
                            "SELECT id FROM cards WHERE ord NOT IN " + Utils.ids2str(ords) + " AND nid IN (SELECT id FROM notes WHERE mid = " + m.getLong("id") + ")", 0);
                	if (ids.size() > 0) {
                    	problems.add("Deleted " + ids.size() + " card(s) with missing template.");
    	                remCards(Utils.arrayList2array(ids));   		
                	}
                }
                // delete any notes with missing cards
                ids = mDb.queryColumn(Long.class,
                        "SELECT id FROM notes WHERE id NOT IN (SELECT DISTINCT nid FROM cards)", 0);
                if (ids.size() != 0) {
                	problems.add("Deleted " + ids.size() + " note(s) with missing no cards.");
	                _remNotes(Utils.arrayList2array(ids));
                }

                ids = mDb.queryColumn(Long.class,
                        "SELECT id FROM cards WHERE nid NOT IN (SELECT id FROM notes)", 0);
                if (ids.size() != 0) {
                    problems.add("Deleted " + ids.size() + " card(s) with missing note.");
                    remCards(Utils.arrayList2array(ids));
                }
                // tags
                mTags.registerNotes();
                // field cache
                for (JSONObject m : mModels.all()) {
                    updateFieldCache(Utils.arrayList2array(mModels.nids(m)));
                }
                // new cards can't have a due position > 32 bits
                mDb.execute("UPDATE cards SET due = 1000000, mod = " + Utils.intNow() + ", usn = " + usn()
                        + " WHERE due > 1000000 AND queue = 0");
                // new card position
                mConf.put("nextPos", mDb.queryScalar("SELECT max(due) + 1 FROM cards WHERE type = 0", false));
                // reviews should have a reasonable due
                ids = mDb.queryColumn(Long.class, "SELECT id FROM cards WHERE queue = 2 AND due > 10000", 0);
                if (ids.size() > 0) {
                	problems.add("Reviews had incorrect due date.");
                    mDb.execute("UPDATE cards SET due = 0, mod = " + Utils.intNow() + ", usn = " + usn()
                            + " WHERE id IN " + Utils.ids2str(Utils.arrayList2array(ids)));
                }
                mDb.getDatabase().setTransactionSuccessful();
            } catch (JSONException e) {
                throw new RuntimeException(e);
            } finally {
                mDb.getDatabase().endTransaction();
            }
        } catch (RuntimeException e) {
            Log.e(AnkiDroidApp.TAG, "doInBackgroundCheckDatabase - RuntimeException on marking card: " + e);
            AnkiDroidApp.saveExceptionReportFile(e, "doInBackgroundCheckDatabase");
            return -1;
        }
        // and finally, optimize
        optimize();
        file = new File(mPath);
        long newSize = file.length();
        // if any problems were found, force a full sync
        if (problems.size() > 0) {
        	modSchema(false);
        }
        // TODO: report problems
        return (long) ((oldSize - newSize) / 1024);
    }


    public void optimize() {
        Log.i(AnkiDroidApp.TAG, "executing VACUUM statement");
        mDb.execute("VACUUM");
        Log.i(AnkiDroidApp.TAG, "executing ANALYZE statement");
        mDb.execute("ANALYZE");
    }


    /**
     * Getters/Setters ********************************************************** *************************************
     */

    public AnkiDb getDb() {
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


    public void setScm(long scm) {
        mScm = scm;
    }


    public boolean getServer() {
        return mServer;
    }


    public void setLs(long ls) {
        mLs = ls;
    }


    public long getLs() {
        return mLs;
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

}
