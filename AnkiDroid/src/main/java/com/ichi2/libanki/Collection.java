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
import com.ichi2.async.CancelListener;
import com.ichi2.async.CollectionTask;
import com.ichi2.libanki.TemplateManager.TemplateRenderContext.TemplateRenderOutput;
import com.ichi2.libanki.backend.DroidBackend;
import com.ichi2.async.ProgressSender;
import com.ichi2.async.TaskManager;
import com.ichi2.libanki.backend.exception.BackendNotSupportedException;
import com.ichi2.libanki.exception.NoSuchDeckException;
import com.ichi2.libanki.exception.UnknownDatabaseVersionException;
import com.ichi2.libanki.hooks.ChessFilter;
import com.ichi2.libanki.sched.AbstractSched;
import com.ichi2.libanki.sched.Sched;
import com.ichi2.libanki.sched.SchedV2;
import com.ichi2.libanki.template.ParsedNode;
import com.ichi2.libanki.template.TemplateError;
import com.ichi2.libanki.utils.Time;
import com.ichi2.upgrade.Upgrade;
import com.ichi2.utils.FunctionalInterfaces;
import com.ichi2.utils.HashUtil;
import com.ichi2.utils.KotlinCleanup;
import com.ichi2.utils.VersionUtils;

import com.ichi2.utils.JSONArray;
import com.ichi2.utils.JSONException;
import com.ichi2.utils.JSONObject;

import net.ankiweb.rsdroid.RustCleanup;

import org.jetbrains.annotations.Contract;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import androidx.annotation.CheckResult;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteStatement;
import timber.log.Timber;

import static com.ichi2.async.CancelListener.isCancelled;

// Anki maintains a cache of used tags so it can quickly present a list of tags
// for autocomplete and in the browser. For efficiency, deletions are not
// tracked, so unused tags can only be removed from the list with a DB check.
//
// This module manages the tag cache and tags for notes.

@SuppressWarnings({"PMD.ExcessiveClassLength", "PMD.AvoidThrowingRawExceptionTypes","PMD.AvoidReassigningParameters",
        "PMD.NPathComplexity","PMD.MethodNamingConventions","PMD.AvoidBranchingStatementAsLastInLoop",
        "PMD.SwitchStmtsShouldHaveDefault","PMD.CollapsibleIfStatements","PMD.EmptyIfStmt","PMD.ExcessiveMethodLength"})
public class Collection implements CollectionGetter {

    private final Context mContext;

    private DB mDb;
    private boolean mServer;
    //private double mLastSave;
    private final Media mMedia;
    protected DeckManager mDecks;
    protected ModelManager mModels;
    protected TagManager mTags;
    protected ConfigManager mConf;

    private AbstractSched mSched;

    private long mStartTime;
    private int mStartReps;

    // BEGIN: SQL table columns
    private long mCrt;
    private long mMod;
    private long mScm;
    private boolean mDty;
    private int mUsn;
    private long mLs;
    // END: SQL table columns

    // API 21: Use a ConcurrentLinkedDeque
    private LinkedBlockingDeque<UndoAction> mUndo;

    private final String mPath;
    protected final DroidBackend mDroidBackend;
    private boolean mDebugLog;
    private PrintWriter mLogHnd;

    private static final Pattern fClozePatternQ = Pattern.compile("\\{\\{(?!type:)(.*?)cloze:");
    private static final Pattern fClozePatternA = Pattern.compile("\\{\\{(.*?)cloze:");
    private static final Pattern fClozeTagStart = Pattern.compile("<%cloze:");

    /**
     * This is only used for collections which were created before
     * the new collections default was v2
     * In that case, 'schedVer' is not set, so this default is used.
     * See: #8926
     * */
    private static final int fDefaultSchedulerVersion = 1;
    private static final List<Integer> fSupportedSchedulerVersions = Arrays.asList(1, 2);

    // Not in libAnki.
    private final Time mTime;

    // other options
    public static final String DEFAULT_CONF = "{"
            +
            // review options
            "\"activeDecks\": [1], " + "\"curDeck\": 1, " + "\"newSpread\": " + Consts.NEW_CARDS_DISTRIBUTE + ", "
            + "\"collapseTime\": 1200, " + "\"timeLim\": 0, " + "\"estTimes\": true, " + "\"dueCounts\": true, \"dayLearnFirst\":false, "
            +
            // other config
            "\"curModel\": null, " + "\"nextPos\": 1, " + "\"sortType\": \"noteFld\", "
            + "\"sortBackwards\": false, \"addToCur\": true }"; // add new to currently selected deck?

    private static final int UNDO_SIZE_MAX = 20;

    @VisibleForTesting
    public Collection(Context context, DB db, String path, boolean server, boolean log, @NonNull Time time, @NonNull DroidBackend droidBackend) {
        mContext = context;
        mDebugLog = log;
        mDb = db;
        mPath = path;
        mTime = time;
        mDroidBackend = droidBackend;
        _openLog();
        log(path, VersionUtils.getPkgVersionName());
        mServer = server;
        //mLastSave = getTime().now(); // assigned but never accessed - only leaving in for upstream comparison
        clearUndo();
        mMedia = new Media(this, server);
        mTags = initTags();
        load();
        if (mCrt == 0) {
            mCrt = UIUtils.getDayStart(getTime()) / 1000;
        }
        mStartReps = 0;
        mStartTime = 0;
        _loadScheduler();
        if (!get_config("newBury", false)) {
            set_config("newBury", true);
        }
    }



    protected DeckManager initDecks(String deckConf) {
        DeckManager deckManager = new Decks(this);
        // getModels().load(loadColumn("models")); This code has been
        // moved to `CollectionHelper::loadLazyCollection` for
        // efficiency Models are loaded lazily on demand. The
        // application layer can asynchronously pre-fetch those parts;
        // otherwise they get loaded when required.
        deckManager.load(loadColumn("decks"), deckConf);
        return deckManager;
    }


    @NonNull
    protected ConfigManager initConf(String conf) {
        return new Config(conf);
    }

    @NonNull
    protected TagManager initTags() {
        return new Tags(this);
    }

    @NonNull
    protected ModelManager initModels() {
        Models models = new Models(this);
        models.load(loadColumn("models"));
        return models;
    }


    public String name() {
        // TODO:
        return (new File(mPath)).getName().replace(".anki2", "");
    }


    /**
     * Scheduler
     * ***********************************************************
     */


    public int schedVer() {
        int ver = get_config("schedVer", fDefaultSchedulerVersion);
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
            if (!getServer() && isUsingRustBackend()) {
                try {
                    set_config("localOffset", getSched()._current_timezone_offset());
                } catch (BackendNotSupportedException e) {
                    throw e.alreadyUsingRustBackend();
                }
            }
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
        @SuppressLint("VisibleForTests")
        SchedV2 v2Sched = new SchedV2(this);
        clearUndo();
        if (ver == 1) {
            v2Sched.moveToV1();
        } else {
            v2Sched.moveToV2();
        }
        set_config("schedVer", ver);
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
            cursor = mDb.query(
                    "SELECT crt, mod, scm, dty, usn, ls, " +
                    "conf, dconf, tags FROM col");
            if (!cursor.moveToFirst()) {
                return;
            }
            mCrt = cursor.getLong(0);
            mMod = cursor.getLong(1);
            mScm = cursor.getLong(2);
            mDty = cursor.getInt(3) == 1; // No longer used
            mUsn = cursor.getInt(4);
            mLs = cursor.getLong(5);
            mConf = initConf(cursor.getString(6));
            deckConf = cursor.getString(7);
            mTags.load(cursor.getString(8));
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        mDecks = initDecks(deckConf);
    }

    private static int sChunk = 0;

    private int getChunk() {
        if (sChunk != 0) {
            return sChunk;
        }
        // This is valid for the framework sqlite as far back as Android 5 / SDK21
        // https://github.com/aosp-mirror/platform_frameworks_base/blob/ba35a77c7c4494c9eb74e87d8eaa9a7205c426d2/core/res/res/values/config.xml#L1141
        final int WINDOW_SIZE_KB = 2048;
        int cursorWindowSize = WINDOW_SIZE_KB * 1024;

        // reduce the actual size a little bit.
        // In case db is not an instance of DatabaseChangeDecorator, sChunk evaluated on default window size
        sChunk = (int) (cursorWindowSize * 15. / 16.);
        return sChunk;
    }

    public String loadColumn(String columnName) {
        int pos = 1;
        StringBuilder buf = new StringBuilder();

        while (true) {
            try (Cursor cursor = mDb.query("SELECT substr(" + columnName + ", ?, ?) FROM col",
                    Integer.toString(pos), Integer.toString(getChunk()))) {
                if (!cursor.moveToFirst()) {
                    return buf.toString();
                }
                String res = cursor.getString(0);
                if (res.length() == 0) {
                      break;
                }
                buf.append(res);
                if (res.length() < getChunk()) {
                    break;
                }
                pos += getChunk();
            }
        }
        return buf.toString();
    }

    /**
     * Mark DB modified. DB operations and the deck/tag/model managers do this automatically, so this is only necessary
     * if you modify properties of this object or the conf dict.
     */
    @RustCleanup("no longer required in v16 - all update immediately")
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
        mMod = (mod == 0 ? getTime().intTimeMS() : mod);
        ContentValues values = new ContentValues();
        values.put("crt", mCrt);
        values.put("mod", mMod);
        values.put("scm", mScm);
        values.put("dty", mDty ? 1 : 0);
        values.put("usn", mUsn);
        values.put("ls", mLs);
        if (flushConf()) {
            values.put("conf", Utils.jsonToString(getConf()));
        }
        mDb.update("col", values);
    }


    protected boolean flushConf() {
        return true;
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
        //mLastSave = getTime().now(); // assigned but never accessed - only leaving in for upstream comparison
    }

    /**
     * Disconnect from DB.
     */
    public synchronized void close() {
        close(true);
    }

    public synchronized void close(boolean save) {
        close(save, false);
    }

    public synchronized void close(boolean save, boolean downgrade) {
        if (mDb != null) {
            try {
                SupportSQLiteDatabase db = mDb.getDatabase();
                if (save) {
                    mDb.executeInTransaction(this::save);
                } else {
                    DB.safeEndInTransaction(db);
                }
            } catch (RuntimeException e) {
                Timber.w(e);
                AnkiDroidApp.sendExceptionReport(e, "closeDB");
            }
            if (!mServer) {
                mDb.getDatabase().disableWriteAheadLogging();
            }
            mDroidBackend.closeCollection(mDb, downgrade);
            mDb = null;
            mMedia.close();
            _closeLog();
            Timber.i("Collection closed");
        }
    }


    public void reopen() {
        Timber.i("Reopening Database");
        if (mDb == null) {
            mDb = mDroidBackend.openCollectionDatabase(mPath);
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
        mScm = getTime().intTimeMS();
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
        // downgrade the collection
        close(true, true);
    }


    /**
     * Object creation helpers **************************************************
     * *********************************************
     */

    public Card getCard(long id) {
        return new Card(this, id);
    }

    public Card.Cache getCardCache(long id) {
        return new Card.Cache(this, id);
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
            id = get_config_int(type);
        } catch (JSONException e) {
            Timber.w(e);
            id = 1;
        }
        set_config(type, id + 1);
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

    public void _logRem(java.util.Collection<Long> ids, @Consts.REM_TYPE int type) {
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
    public @NonNull Note newNote() {
        return newNote(true);
    }

    /**
     * Return a new note with the model derived from the deck or the configuration
     * @param forDeck When true it uses the model specified in the deck (mid), otherwise it uses the model specified in
     *                the configuration (curModel)
     * @return The new note
     */
    public @NonNull Note newNote(boolean forDeck) {
        return newNote(getModels().current(forDeck));
    }

    /**
     * Return a new note with a specific model
     * @param m The model to use for the new note
     * @return The new note
     */
    public @NonNull Note newNote(Model m) {
        return new Note(this, m);
    }


    /**
     * @param note A note to add if it generates card
     * @return Number of card added.
     */
    public int addNote(@NonNull Note note) {
        return addNote(note, Models.AllowEmpty.ONLY_CLOZE);
    }

    /**
     * Add a note and cards to the collection. If allowEmpty, at least one card is generated.
     * @param note  The note to add to the collection
     * @param allowEmpty Whether we accept to add it even if it should generate no card. Useful to import note even if buggy
     * @return Number of card added
     */
    public int addNote(@NonNull Note note, Models.AllowEmpty allowEmpty) {
        // check we have card models available, then save
        ArrayList<JSONObject> cms = findTemplates(note, allowEmpty);
        // Todo: upstream, we accept to add a not even if it generates no card. Should be ported to ankidroid
        if (cms.isEmpty()) {
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
        if (ids.isEmpty()) {
            return;
        }
        String strids = Utils.ids2str(ids);
        // we need to log these independently of cards, as one side may have
        // more card templates
        _logRem(ids, Consts.REM_NOTE);
        mDb.execute("DELETE FROM notes WHERE id IN " + strids);
    }


    /*
      Card creation ************************************************************ ***********************************
     */

    public ArrayList<JSONObject> findTemplates(Note note) {
        return findTemplates(note, Models.AllowEmpty.ONLY_CLOZE);
    }

    /**
     * @param note A note
     * @param allowEmpty whether we allow to have a card which is actually empty if it is necessary to return a non-empty list
     * @return (active), non-empty templates.
     */
    public ArrayList<JSONObject> findTemplates(Note note, Models.AllowEmpty allowEmpty) {
        Model model = note.model();
        ArrayList<Integer> avail = Models.availOrds(model, note.getFields(), allowEmpty);
        return _tmplsFromOrds(model, avail);
    }


    /**
     * @param model A note type
     * @param avail Ords of cards from this note type.
     * @return One template by element i of avail, for the i-th card. For standard template, avail should contains only existing ords.
     * for cloze, avail should contains only non-negative numbers, and the i-th card is a copy of the first card, with a different ord.
     */
    private ArrayList<JSONObject> _tmplsFromOrds(Model model, ArrayList<Integer> avail) {
        JSONArray tmpls;
        if (model.isStd()) {
            tmpls = model.getJSONArray("tmpls");
            ArrayList<JSONObject> ok = new ArrayList<>(avail.size());
            for (Integer ord : avail) {
                ok.add(tmpls.getJSONObject(ord));
            }
            return ok;
        } else {
            // cloze - generate temporary templates from first
            JSONObject template0 = model.getJSONArray("tmpls").getJSONObject(0);
            ArrayList<JSONObject> ok = new ArrayList<>(avail.size());
            for (int ord : avail) {
                JSONObject t = template0.deepClone();
                t.put("ord", ord);
                ok.add(t);
            }
            return ok;
        }
    }


    /**
     * Generate cards for non-empty templates, return ids to remove.
     */
	public ArrayList<Long> genCards(java.util.Collection<Long> nids, @NonNull Model model) {
	    return genCards(Utils.collection2Array(nids), model);
	}

    public <T extends ProgressSender<Integer> & CancelListener> ArrayList<Long> genCards(java.util.Collection<Long> nids, @NonNull Model model, @Nullable T task) {
       return genCards(Utils.collection2Array(nids), model, task);
    }

    public ArrayList<Long> genCards(java.util.Collection<Long> nids, long mid) {
        return genCards(nids, getModels().get(mid));
    }

    public ArrayList<Long> genCards(long[] nids, @NonNull Model model) {
        return genCards(nids, model, null);
    }

    public ArrayList<Long> genCards(long nid, @NonNull Model model) {
        return genCards(nid, model, null);
    }

    public <T extends ProgressSender<Integer> & CancelListener> ArrayList<Long> genCards(long nid, @NonNull Model model, @Nullable T task) {
        return genCards("(" + nid + ")", model, task);
    }

    /**
     * @param nids All ids of nodes of a note type
     * @param task Task to check for cancellation and update number of card processed
     * @return Cards that should be removed because they should not be generated
     */
    public <T extends ProgressSender<Integer> & CancelListener> ArrayList<Long> genCards(long[] nids, @NonNull Model model, @Nullable T task) {
        // build map of (nid,ord) so we don't create dupes
        String snids = Utils.ids2str(nids);
        return genCards(snids, model, task);
    }

    /**
     * @param snids All ids of nodes of a note type, separated by comma
     * @param model
     * @param task Task to check for cancellation and update number of card processed
     * @return Cards that should be removed because they should not be generated
     * @param <T>
     */
    public <T extends ProgressSender<Integer> & CancelListener> ArrayList<Long> genCards(String snids, @NonNull Model model, @Nullable T task) {
        int nbCount = noteCount();
        // For each note, indicates ords of cards it contains
        HashMap<Long, HashMap<Integer, Long>> have = HashUtil.HashMapInit(nbCount);
        // For each note, the deck containing all of its cards, or 0 if siblings in multiple deck
        HashMap<Long, Long> dids = HashUtil.HashMapInit(nbCount);
        // For each note, an arbitrary due of one of its due card processed, if any exists
        HashMap<Long, Long> dues = HashUtil.HashMapInit(nbCount);
        List<ParsedNode> nodes = null;
        if (model.getInt("type") != Consts.MODEL_CLOZE) {
            nodes = model.parsedNodes();
        }
        try (Cursor cur = mDb.query("select id, nid, ord, (CASE WHEN odid != 0 THEN odid ELSE did END), (CASE WHEN odid != 0 THEN odue ELSE due END), type from cards where nid in " + snids)) {
            while (cur.moveToNext()) {
                if (isCancelled(task)) {
                    Timber.v("Empty card cancelled");
                    return null;
                }
                @NonNull Long id = cur.getLong(0);
                @NonNull Long nid = cur.getLong(1);
                @NonNull Integer ord = cur.getInt(2);
                @NonNull Long did = cur.getLong(3);
                @NonNull Long due = cur.getLong(4);
                @Consts.CARD_TYPE int type = cur.getInt(5);

                // existing cards
                if (!have.containsKey(nid)) {
                    have.put(nid, new HashMap<>());
                }
                have.get(nid).put(ord, id);
                // and their dids
                if (dids.containsKey(nid)) {
                    if (dids.get(nid) != 0 && !Utils.equals(dids.get(nid), did)) {
                        // cards are in two or more different decks; revert to model default
                        dids.put(nid, 0L);
                    }
                } else {
                    // first card or multiple cards in same deck
                    dids.put(nid, did);
                }
                if (!dues.containsKey(nid) && type == Consts.CARD_TYPE_NEW) {
                    dues.put(nid, due);
                }
            }
        }
        // build cards for each note
        ArrayList<Object[]> data = new ArrayList<>();
        long ts = getTime().maxID(mDb);
        long now = getTime().intTime();
        ArrayList<Long> rem = new ArrayList<>(mDb.queryScalar("SELECT count() FROM notes where id in " + snids));
        int usn = usn();
        try (Cursor cur = mDb.query("SELECT id, flds FROM notes WHERE id IN " + snids)) {
            while (cur.moveToNext()) {
                if (isCancelled(task)) {
                    Timber.v("Empty card cancelled");
                    return null;
                }
                @NonNull Long nid = cur.getLong(0);
                String flds = cur.getString(1);
                ArrayList<Integer> avail = Models.availOrds(model, Utils.splitFields(flds), nodes, Models.AllowEmpty.TRUE);
                if (task != null) {
                    task.doProgress(avail.size());
                }
                Long did = dids.get(nid);
                // use sibling due if there is one, else use a new id
                @NonNull Long due;
                if (dues.containsKey(nid)) {
                    due = dues.get(nid);
                } else {
                    due = (long) nextID("pos");
                }
                if (did == null || did == 0L) {
                    did = model.getLong("did");
                }
                // add any missing cards
                ArrayList<JSONObject> tmpls = _tmplsFromOrds(model, avail);
                for (JSONObject t : tmpls) {
                    int tord = t.getInt("ord");
                    boolean doHave = have.containsKey(nid) && have.get(nid).containsKey(tord);
                    if (!doHave) {
                        // check deck is not a cram deck
                        long ndid;
                        try {
                            ndid = t.optLong("did", 0);
                            if (ndid != 0) {
                                did = ndid;
                            }
                        } catch (JSONException e) {
                            Timber.w(e);
                            // do nothing
                        }
                        if (getDecks().isDyn(did)) {
                            did = 1L;
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
        }
        // bulk update
        mDb.executeMany("INSERT INTO cards VALUES (?,?,?,?,?,?,0,0,?,0,0,0,0,0,0,0,0,\"\")", data);
        return rem;
    }

    /**
     * Create a new card.
     */
    private Card _newCard(Note note, JSONObject template, int due) {
        boolean flush = true;
        return _newCard(note, template, due, flush);
    }

    private Card _newCard(Note note, JSONObject template, int due, long did) {
        boolean flush = true;
        return _newCard(note, template, due, did, flush);
    }

    private Card _newCard(Note note, JSONObject template, int due, boolean flush) {
        long did = 0L;
        return _newCard(note, template, due, did, flush);
    }

    private Card _newCard(Note note, JSONObject template, int due, long parameterDid, boolean flush) {
        Card card = new Card(this);
        return getNewLinkedCard(card, note, template, due, parameterDid, flush);
    }

    // This contains the original libanki implementation of _newCard, with the added parameter that
    // you pass the Card object in. This allows you to work on 'Card' subclasses that may not have
    // actual backing store (for instance, if you are previewing unsaved changes on templates)
    // TODO: use an interface that we implement for card viewing, vs subclassing an active model to workaround libAnki
    public Card getNewLinkedCard(Card card, Note note, JSONObject template, int due, long parameterDid, boolean flush) {
        long nid = note.getId();
        card.setNid(nid);
        int ord = template.getInt("ord");
        card.setOrd(ord);
        long did = mDb.queryLongScalar("select did from cards where nid = ? and ord = ?", nid, ord);
        // Use template did (deck override) if valid, otherwise did in argument, otherwise model did
        if (did == 0) {
            did = template.optLong("did", 0);
            if (did > 0 && mDecks.get(did, false) != null) {
            } else if (parameterDid != 0) {
                did = parameterDid;
            } else {
                did = note.model().optLong("did", 0);
            }
        }
        card.setDid(did);
        // if invalid did, use default instead
        Deck deck = mDecks.get(card.getDid());
        if (deck.isDyn()) {
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
    public int cardCount(Long... dids) {
        return mDb.queryScalar("SELECT count() FROM cards WHERE did IN " + Utils.ids2str(dids));
    }

    public boolean isEmptyDeck(Long... dids) {
        return cardCount(dids) == 0;
    }


    /**
     * Bulk delete cards by ID.
     */
    public void remCards(List<Long> ids) {
        remCards(ids, true);
    }

    public void remCards(java.util.Collection<Long> ids, boolean notes) {
        if (ids.isEmpty()) {
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


    public <T extends ProgressSender<Integer> & CancelListener> List<Long> emptyCids(@Nullable T task) {
        List<Long> rem = new ArrayList<>();
        for (Model m : getModels().all()) {
            rem.addAll(genCards(getModels().nids(m), m, task));
        }
        return rem;
    }


    public String emptyCardReport(List<Long> cids) {
        StringBuilder rep = new StringBuilder();
        try (Cursor cur = mDb.query("select group_concat(ord+1), count(), flds from cards c, notes n "
                                           + "where c.nid = n.id and c.id in " + Utils.ids2str(cids) + " group by nid")) {
            while (cur.moveToNext()) {
                String ords = cur.getString(0);
                //int cnt = cur.getInt(1);  // present but unused upstream as well
                String flds = cur.getString(2);
                rep.append(String.format("Empty card numbers: %s\nFields: %s\n\n", ords, flds.replace("\u001F", " / ")));
            }
        }
        return rep.toString();
    }

    /**
     * Field checksums and sorting fields ***************************************
     * ********************************************************
     */

    private ArrayList<Object[]> _fieldData(String snids) {
        ArrayList<Object[]> result = new ArrayList<>(mDb.queryScalar("SELECT count() FROM notes WHERE id IN" + snids));
        try (Cursor cur = mDb.query("SELECT id, mid, flds FROM notes WHERE id IN " + snids)) {
            while (cur.moveToNext()) {
                result.add(new Object[] { cur.getLong(0), cur.getLong(1), cur.getString(2) });
            }
        }
        return result;
    }


    /** Update field checksums and sort cache, after find&replace, etc.
     * @param nids*/
    public void updateFieldCache(java.util.Collection<Long> nids) {
        String snids = Utils.ids2str(nids);
        updateFieldCache(snids);
    }

    /** Update field checksums and sort cache, after find&replace, etc.
     * @param nids*/
    public void updateFieldCache(long[] nids) {
        String snids = Utils.ids2str(nids);
        updateFieldCache(snids);
    }

    /** Update field checksums and sort cache, after find&replace, etc.
     * @param snids comma separated nids*/
    public void updateFieldCache(String snids) {
        ArrayList<Object[]> data = _fieldData(snids);
        ArrayList<Object[]> r = new ArrayList<>(data.size());
        for (Object[] o : data) {
            String[] fields = Utils.splitFields((String) o[2]);
            Model model = getModels().get((Long) o[1]);
            if (model == null) {
                // note point to invalid model
                continue;
            }
            Pair<String, Long> csumAndStrippedFieldField = Utils.sfieldAndCsum(fields, getModels().sortIdx(model));
            r.add(new Object[] {csumAndStrippedFieldField.first, csumAndStrippedFieldField.second, o[0] });
        }
        // apply, relying on calling code to bump usn+mod
        mDb.executeMany("UPDATE notes SET sfld=?, csum=? WHERE id=?", r);
    }


    /*
      Q/A generation *********************************************************** ************************************
     */

    /**
     * Returns hash of id, question, answer.
     */
    @NonNull
    public HashMap<String, String> _renderQA(long cid, Model model, long did, int ord, String tags, String[] flist, int flags) {
        return _renderQA(cid, model, did, ord, tags, flist, flags, false, null, null);
    }


    @RustCleanup("#8951 - Remove FrontSide added to the front")
    @NonNull
    public HashMap<String, String> _renderQA(long cid, Model model, long did, int ord, String tags, String[] flist, int flags, boolean browser, String qfmt, String afmt) {
        // data is [cid, nid, mid, did, ord, tags, flds, cardFlags]
        // unpack fields and create dict
        Map<String, Pair<Integer, JSONObject>> fmap = Models.fieldMap(model);
        Set<Map.Entry<String, Pair<Integer, JSONObject>>> maps = fmap.entrySet();
        Map<String, String> fields = HashUtil.HashMapInit(maps.size() + 8);
        for (Map.Entry<String, Pair<Integer, JSONObject>> entry : maps) {
            fields.put(entry.getKey(), flist[entry.getValue().first]);
        }
        int cardNum = ord + 1;
        fields.put("Tags", tags.trim());
        fields.put("Type", model.getString("name"));
        fields.put("Deck", mDecks.name(did));
        String baseName = Decks.basename(fields.get("Deck"));
        fields.put("Subdeck", baseName);
        fields.put("CardFlag", _flagNameFromCardFlags(flags));
        JSONObject template;
        if (model.isStd()) {
            template = model.getJSONArray("tmpls").getJSONObject(ord);
        } else {
            template = model.getJSONArray("tmpls").getJSONObject(0);
        }
        fields.put("Card", template.getString("name"));
        fields.put(String.format(Locale.US, "c%d", cardNum), "1");
        // render q & a
        HashMap<String, String> d = HashUtil.HashMapInit(2);
        d.put("id", Long.toString(cid));
        qfmt = TextUtils.isEmpty(qfmt) ? template.getString("qfmt") : qfmt;
        afmt = TextUtils.isEmpty(afmt) ? template.getString("afmt") : afmt;
        for (Pair<String, String> p : new Pair[]{new Pair<>("q", qfmt), new Pair<>("a", afmt)}) {
            String type = p.first;
            String format = p.second;
            if ("q".equals(type)) {
                format = fClozePatternQ.matcher(format).replaceAll(String.format(Locale.US, "{{$1cq-%d:", cardNum));
                format = fClozeTagStart.matcher(format).replaceAll(String.format(Locale.US, "<%%cq:%d:", cardNum));
                fields.put("FrontSide", "");
            } else {
                format = fClozePatternA.matcher(format).replaceAll(String.format(Locale.US, "{{$1ca-%d:", cardNum));
                format = fClozeTagStart.matcher(format).replaceAll(String.format(Locale.US, "<%%ca:%d:", cardNum));
                // the following line differs from libanki // TODO: why?
                fields.put("FrontSide", d.get("q")); // fields.put("FrontSide", mMedia.stripAudio(d.get("q")));
            }
            String html;
            try {
                html = ParsedNode.parse_inner(format).render(fields, "q".equals(type), getContext());
            } catch (TemplateError er) {
                Timber.w(er);
                html = er.message(getContext());
            }
            html = ChessFilter.fenToChessboard(html, getContext());
            if (!browser) {
                // browser don't show image. So compiling LaTeX actually remove information.
                html = LaTeX.mungeQA(html, this, model);
            }
            d.put(type, html);
            // empty cloze?
            if ("q".equals(type) && model.isCloze()) {
                if (Models._availClozeOrds(model, flist, false).isEmpty()) {
                    String link = String.format("<a href=\"%s\">%s</a>", mContext.getResources().getString(R.string.link_ankiweb_docs_cloze_deletion), "help");
                    System.out.println(link);
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
        try (Cursor cur = mDb.query(
                    "SELECT c.id, n.id, n.mid, c.did, c.ord, "
                            + "n.tags, n.flds, c.flags FROM cards c, notes n WHERE c.nid == n.id " + where)) {
            while (cur.moveToNext()) {
                data.add(new Object[] { cur.getLong(0), cur.getLong(1),
                        getModels().get(cur.getLong(2)), cur.getLong(3), cur.getInt(4),
                        cur.getString(5), cur.getString(6), cur.getInt(7)});
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

    /*
      Finding cards ************************************************************ ***********************************
     */

    /** Return a list of card ids */
    @KotlinCleanup("set reasonable defaults")
    public List<Long> findCards(String search) {
        return findCards(search, new SortOrder.NoOrdering());
    }

    /**
     * @return A list of card ids
     * @throws com.ichi2.libanki.exception.InvalidSearchException Invalid search string
     */
    public List<Long> findCards(String search, @NonNull SortOrder order) {
        return new Finder(this).findCards(search, order);
    }
    /**
     * @return A list of card ids
     * @throws com.ichi2.libanki.exception.InvalidSearchException Invalid search string
     */
    public List<Long> findCards(String search, @NonNull SortOrder order, CollectionTask.PartialSearch task) {
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


    /*
      Stats ******************************************************************** ***************************
     */

    // cardstats
    // stats

    /**
     * Timeboxing *************************************************************** ********************************
     */

    public void setTimeLimit(long seconds) {
        set_config("timeLim", seconds);
    }


    public long getTimeLimit() {
        return get_config_long("timeLim");
    }


    public void startTimebox() {
        mStartTime = getTime().intTime();
        mStartReps = mSched.getReps();
    }


    /* Return (elapsedTime, reps) if timebox reached, or null. */
    public Pair<Integer, Integer> timeboxReached() {
        if (get_config_long("timeLim") == 0) {
            // timeboxing disabled
            return null;
        }
        long elapsed = getTime().intTime() - mStartTime;
        if (elapsed > get_config_long("timeLim")) {
            return new Pair<>(get_config_int("timeLim"), mSched.getReps() - mStartReps);
        }
        return null;
    }


    /**
     * Undo ********************************************************************* **************************
     */

    /* Note from upstream:
     * this data structure is a mess, and will be updated soon
     * in the review case, [1, "Review", [firstReviewedCard, secondReviewedCard, ...], wasLeech]
     * in the checkpoint case, [2, "action name"]
     * wasLeech should have been recorded for each card, not globally
     */
    public void clearUndo() {
        mUndo = new LinkedBlockingDeque<>();
    }


    /** Undo menu item name, or "" if undo unavailable. */
    @VisibleForTesting
    public @Nullable UndoAction undoType() {
        if (!mUndo.isEmpty()) {
            return mUndo.getLast();
        }
        return null;
    }
    public String undoName(Resources res) {
        UndoAction type = undoType();
        if (type != null) {
            return type.name(res);
        }
        return "";
    }

    public boolean undoAvailable() {
        Timber.d("undoAvailable() undo size: %s", mUndo.size());
        return !mUndo.isEmpty();
    }

    public @Nullable Card undo() {
        UndoAction lastUndo = mUndo.removeLast();
        Timber.d("undo() of type %s", lastUndo.getClass());
        return lastUndo.undo(this);
    }

    public void markUndo(@NonNull UndoAction undo) {
        Timber.d("markUndo() of type %s", undo.getClass());
        mUndo.add(undo);
        while (mUndo.size() > UNDO_SIZE_MAX) {
            mUndo.removeFirst();
        }
    }


    public void onCreate() {
        mDroidBackend.useNewTimezoneCode(this);
        set_config("schedVer", 2);
        // we need to reload the scheduler: this was previously loaded as V1
        _loadScheduler();
    }


    @Nullable
    public TemplateRenderOutput render_output(@NonNull Card c, boolean reload, boolean browser) {
        return render_output_legacy(c, reload, browser);
    }


    @NonNull
    @RustCleanup("Hack for Card Template Previewer, needs review")
    public TemplateRenderOutput render_output_legacy(@NonNull Card c, boolean reload, boolean browser) {
        Note f = c.note(reload);
        Model m = c.model();
        JSONObject t = c.template();
        long did;
        if (c.isInDynamicDeck()) {
            did = c.getODid();
        } else {
            did = c.getDid();
        }
        HashMap<String, String> qa;
        if (browser) {
            String bqfmt = t.getString("bqfmt");
            String bafmt = t.getString("bafmt");
            qa = _renderQA(c.getId(), m, did, c.getOrd(), f.stringTags(), f.getFields(), c.internalGetFlags(), browser, bqfmt, bafmt);
        } else {
            qa = _renderQA(c.getId(), m, did, c.getOrd(), f.stringTags(), f.getFields(), c.internalGetFlags());
        }

        return new TemplateRenderOutput(
                qa.get("q"),
                qa.get("a"),
                null,
                null,
                c.model().getString("css"));
    }


    @VisibleForTesting
    public static class UndoReview extends UndoAction {
        private final boolean mWasLeech;
        @NonNull private final Card mClonedCard;
        public UndoReview(boolean wasLeech, @NonNull Card clonedCard) {
            super(R.string.undo_action_review);
            mClonedCard = clonedCard;
            mWasLeech = wasLeech;
        }

        @NonNull
        @Override
        public Card undo(@NonNull Collection col) {
            col.getSched().undoReview(mClonedCard, mWasLeech);
            return mClonedCard;
        }
    }

    public void markReview(Card card) {
        boolean wasLeech = card.note().hasTag("leech");
        Card clonedCard = card.clone();
        markUndo(new UndoReview(wasLeech, clonedCard));
    }

    /**
     * DB maintenance *********************************************************** ************************************
     */


    /*
     * Basic integrity check for syncing. True if ok.
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
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
    public CheckDatabaseResult fixIntegrity(TaskManager.ProgressCallback<String> progressCallback) {
        File file = new File(mPath);
        CheckDatabaseResult result = new CheckDatabaseResult(file.length());
        final int[] currentTask = {1};
        int totalTasks = (getModels().all().size() * 4) + 27; // a few fixes are in all-models loops, the rest are one-offs
        Runnable notifyProgress = () -> fixIntegrityProgress(progressCallback, currentTask[0]++, totalTasks);
        Consumer<FunctionalInterfaces.FunctionThrowable<Runnable, List<String>, JSONException>> executeIntegrityTask =
                function -> {
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
            Timber.w(ex,"doInBackgroundCheckDatabase - Database locked");
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

        executeIntegrityTask.accept(this::deleteNotesWithMissingModel);
        // for each model
        for (Model m : getModels().all()) {
            executeIntegrityTask.accept((callback) -> deleteCardsWithInvalidModelOrdinals(callback, m));
            executeIntegrityTask.accept((callback) -> deleteNotesWithWrongFieldCounts(callback, m));
        }
        executeIntegrityTask.accept(this::deleteNotesWithMissingCards);
        executeIntegrityTask.accept(this::deleteCardsWithMissingNotes);
        executeIntegrityTask.accept(this::removeOriginalDuePropertyWhereInvalid);
        executeIntegrityTask.accept(this::removeDynamicPropertyFromNonDynamicDecks);
        executeIntegrityTask.accept(this::removeDeckOptionsFromDynamicDecks);
        executeIntegrityTask.accept(this::resetInvalidDeckOptions);
        executeIntegrityTask.accept(this::rebuildTags);
        executeIntegrityTask.accept(this::updateFieldCache);
        executeIntegrityTask.accept(this::fixNewCardDuePositionOverflow);
        executeIntegrityTask.accept(this::resetNewCardInsertionPosition);
        executeIntegrityTask.accept(this::fixExcessiveReviewDueDates);
        // v2 sched had a bug that could create decimal intervals
        executeIntegrityTask.accept(this::fixDecimalCardsData);
        executeIntegrityTask.accept(this::fixDecimalRevLogData);
        executeIntegrityTask.accept(this::restoreMissingDatabaseIndices);
        executeIntegrityTask.accept(this::ensureModelsAreNotEmpty);
        executeIntegrityTask.accept((progressNotifier) -> this.ensureCardsHaveHomeDeck(progressNotifier, result));
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
        HashSet<Long> configIds  = HashUtil.HashSetInit(allConf.size());

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

        List<String> ret = new ArrayList<>(1);

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
        Long nextDeckId = getDecks().id_safe(recoveredDeckName);

        getDecks().flush();

        mDb.execute("update cards " +
                        "set did = ?, " +
                        "odid = 0," +
                        "mod = ?, " +
                        "usn = ? " +
                        "where did in " +
                        Utils.ids2str(dynDeckIds) +
                        "and odid in " +
                        Utils.ids2str(dynIdsAndZero), nextDeckId, getTime().intTime(), usn());


        result.setCardsWithFixedHomeDeckCount(cardIds.size());

        String message = String.format(Locale.US, "Fixed %d cards with no home deck", cardIds.size());
        return Collections.singletonList(message);
    }


    private ArrayList<String> ensureModelsAreNotEmpty(Runnable notifyProgress) {
        Timber.d("ensureModelsAreNotEmpty()");
        ArrayList<String> problems = new ArrayList<>(1);
        notifyProgress.run();
        if (getModels().ensureNotEmpty()) {
            problems.add("Added missing note type.");
        }
        return problems;
    }


    private ArrayList<String> restoreMissingDatabaseIndices(Runnable notifyProgress) {
        Timber.d("restoreMissingDatabaseIndices");
        ArrayList<String> problems = new ArrayList<>(1);
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
        ArrayList<String> problems = new ArrayList<>(1);
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
        ArrayList<String> problems = new ArrayList<>(1);
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
        ArrayList<String> problems = new ArrayList<>(1);
        notifyProgress.run();
        // reviews should have a reasonable due #
        ArrayList<Long> ids = mDb.queryLongList("SELECT id FROM cards WHERE queue = " + Consts.QUEUE_TYPE_REV + " AND due > 100000");
        notifyProgress.run();
        if (!ids.isEmpty()) {
            problems.add("Reviews had incorrect due date.");
            mDb.execute("UPDATE cards SET due = ?, ivl = 1, mod = ?, usn = ? WHERE id IN " + Utils.ids2str(ids), mSched.getToday(), getTime().intTime(), usn());
        }
        return problems;
    }


    private List<String> resetNewCardInsertionPosition(Runnable notifyProgress) throws JSONException {
        Timber.d("resetNewCardInsertionPosition");
        notifyProgress.run();
        // new card position
        set_config("nextPos", mDb.queryScalar("SELECT max(due) + 1 FROM cards WHERE type = " + Consts.CARD_TYPE_NEW));
        return Collections.emptyList();
    }


    private List<String> fixNewCardDuePositionOverflow(Runnable notifyProgress) {
        Timber.d("fixNewCardDuePositionOverflow");
        // new cards can't have a due position > 32 bits
        notifyProgress.run();
        mDb.execute("UPDATE cards SET due = 1000000, mod = ?, usn = ? WHERE due > 1000000 AND type = " + Consts.CARD_TYPE_NEW, getTime().intTime(), usn());
        return Collections.emptyList();
    }


    private List<String> updateFieldCache(Runnable notifyProgress) {
        Timber.d("updateFieldCache");
        // field cache
        for (Model m : getModels().all()) {
            notifyProgress.run();
            updateFieldCache(getModels().nids(m));
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
        ArrayList<String> problems = new ArrayList<>(1);
        //#5708 - a dynamic deck should not have "Deck Options"
        notifyProgress.run();
        int fixCount = 0;
        for (long id : mDecks.allDynamicDeckIds()) {
            try {
                if (hasDeckOptions(id)) {
                    removeDeckOptions(id);
                    fixCount++;
                }
            } catch (NoSuchDeckException e) {
                Timber.w(e, "Unable to find dynamic deck %d", id);
            }
        }
        if (fixCount > 0) {
            mDecks.save();
            problems.add(String.format(Locale.US, "%d dynamic deck(s) had deck options.", fixCount));
        }
        return problems;
    }


    private Deck getDeckOrFail(long deckId) throws NoSuchDeckException {
        Deck deck = getDecks().get(deckId, false);
        if (deck == null) {
            throw new NoSuchDeckException(deckId);
        }
        return deck;
    }

    private boolean hasDeckOptions(long deckId) throws NoSuchDeckException {
        return getDeckOrFail(deckId).has("conf");
    }


    private void removeDeckOptions(long deckId) throws NoSuchDeckException {
        getDeckOrFail(deckId).remove("conf");
    }


    private ArrayList<String> removeDynamicPropertyFromNonDynamicDecks(Runnable notifyProgress) {
        Timber.d("removeDynamicPropertyFromNonDynamicDecks()");
        ArrayList<String> problems = new ArrayList<>(1);
        ArrayList<Long> dids = new ArrayList<>(mDecks.count());
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
        ArrayList<String> problems = new ArrayList<>(1);
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
        ArrayList<String> problems = new ArrayList<>(1);
        notifyProgress.run();
        // cards with missing notes
        ArrayList<Long> ids = mDb.queryLongList(
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
        ArrayList<String> problems = new ArrayList<>(1);
        notifyProgress.run();
        // delete any notes with missing cards
        ArrayList<Long> ids = mDb.queryLongList(
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
        ArrayList<String> problems = new ArrayList<>(1);
        // notes with invalid field counts
        ArrayList<Long> ids = new ArrayList<>();
        notifyProgress.run();
        try (Cursor cur = mDb.query("select id, flds from notes where mid = ?",  m.getLong("id"))) {
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
            if (!ids.isEmpty()) {
                problems.add("Deleted " + ids.size() + " note(s) with wrong field count.");
                _remNotes(ids);
            }
        }
        return problems;
    }


    private ArrayList<String> deleteCardsWithInvalidModelOrdinals(Runnable notifyProgress, Model m) throws JSONException {
        Timber.d("deleteCardsWithInvalidModelOrdinals()");
        ArrayList<String> problems = new ArrayList<>(1);
        notifyProgress.run();
        if (m.isStd()) {
            JSONArray tmpls = m.getJSONArray("tmpls");
            ArrayList<Integer> ords = new ArrayList<>(tmpls.length());
            for (JSONObject tmpl: tmpls.jsonObjectIterable()) {
                ords.add(tmpl.getInt("ord"));
            }
            // cards with invalid ordinal
            ArrayList<Long> ids = mDb.queryLongList(
                    "SELECT id FROM cards WHERE ord NOT IN " + Utils.ids2str(ords) + " AND nid IN ( " +
                            "SELECT id FROM notes WHERE mid = ?)", m.getLong("id"));
            if (!ids.isEmpty()) {
                problems.add("Deleted " + ids.size() + " card(s) with missing template.");
                remCards(ids);
            }
        }
        return problems;
    }


    private ArrayList<String> deleteNotesWithMissingModel(Runnable notifyProgress) {
        Timber.d("deleteNotesWithMissingModel()");
        ArrayList<String> problems = new ArrayList<>(1);
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


    private void fixIntegrityProgress(TaskManager.ProgressCallback<String> progressCallback, int current, int total) {
        progressCallback.publishProgress(
                progressCallback.getResources().getString(R.string.check_db_message) + " " + current + " / " + total);
    }


    /*
      Logging
      ***********************************************************
     */

    /**
     * Track database corruption problems and post analytics events for tracking
     *
     * @param integrityCheckProblems list of problems, the first 10 will be used
     */
    private void logProblems(List<String> integrityCheckProblems) {

        if (!integrityCheckProblems.isEmpty()) {
            StringBuffer additionalInfo = new StringBuffer();
            for (int i = 0; ((i < 10) && (integrityCheckProblems.size()) > i); i++) {
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
        String s = String.format("[%s] %s:%s(): %s", getTime().intTime(), trace.getFileName(), trace.getMethodName(),
                TextUtils.join(",  ", args));
        writeLog(s);
    }


    private void writeLog(String s) {
        if (mLogHnd != null) {
            try {
                mLogHnd.println(s);
            } catch (Exception e) {
                Timber.w(e, "Failed to write to collection log");
            }
        }
        Timber.d(s);
    }


    private void _openLog() {
        Timber.i("Opening Collection Log");
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
        Timber.i("Closing Collection Log");
        if (mLogHnd != null) {
            mLogHnd.close();
            mLogHnd = null;
        }
    }

    /**
     * Card Flags *****************************************************************************************************
     */
    public void setUserFlag(int flag, List<Long> cids)  {
        assert (0<= flag && flag <= 7);
        mDb.execute("update cards set flags = (flags & ~?) | ?, usn=?, mod=? where id in " + Utils.ids2str(cids),
                    0b111, flag, usn(), getTime().intTime());
    }

    /**
     * Getters/Setters ********************************************************** *************************************
     */

    public DB getDb() {
        return mDb;
    }


    public DeckManager getDecks() {
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
    public final synchronized ModelManager getModels() {
        if (mModels == null) {
            mModels = initModels();
        }
        return mModels;
    }

    /** Check if this collection is valid. */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean validCollection() {
    	//TODO: more validation code
    	return getModels().validateModel();
    }

    public JSONObject getConf() {
        return mConf.getJson();
    }


    public void setConf(JSONObject conf) {
        // Anki sometimes set sortBackward to 0/1 instead of
        // False/True. This should be repaired before setting mConf;
        // otherwise this may save a faulty value in mConf, and if
        // it's done just before the value is read, this may lead to
        // bug #5523. This bug should occur only for people using anki
        // prior to version 2.16 and has been corrected with
        // dae/anki#347
        Upgrade.upgradeJSONIfNecessary(this, "sortBackwards", false);
        mConf.setJson(conf);
    }

    // region JSON-Related Config

    // Anki Desktop has a get_config and set_config method handling an "Any"
    // We're not dynamically typed, so add additional methods for each JSON type that
    // we can handle

    // methods with a default can be named `get_config` as the `defaultValue` argument defines the return type
    // NOTE: get_config("key", 1) and get_config("key", 1L) will return different types


    public boolean has_config(@NonNull String key) {
        // not in libAnki
        return mConf.has(key);
    }

    public boolean has_config_not_null(String key) {
        // not in libAnki
        return has_config(key) && !mConf.isNull(key);
    }

    /** @throws JSONException object does not exist or can't be cast */
    public boolean get_config_boolean(@NonNull String key) {
        return mConf.getBoolean(key);
    }

    /** @throws JSONException object does not exist or can't be cast */
    public long get_config_long(@NonNull String key) {
        return mConf.getLong(key);
    }

    /** @throws JSONException object does not exist or can't be cast */
    public int get_config_int(@NonNull String key) {
        return mConf.getInt(key);
    }

    /** @throws JSONException object does not exist or can't be cast */
    public double get_config_double(@NonNull String key) {
        return mConf.getDouble(key);
    }

    /**
     * Edits to this object are not persisted to preferences.
     * @throws JSONException object does not exist or can't be cast
     */
    public JSONObject get_config_object(@NonNull String key) {
        return new JSONObject(mConf.getJSONObject(key));
    }

    /** Edits to the array are not persisted to the preferences
     * @throws JSONException object does not exist or can't be cast
     */
    @NonNull
    public JSONArray get_config_array(@NonNull String key) {
        return new JSONArray(mConf.getJSONArray(key));
    }

    /**
     * If the value is null in the JSON, a string of "null" will be returned
     * @throws JSONException object does not exist, or can't be cast
     */
    @NonNull
    public String get_config_string(@NonNull String key) {
        return mConf.getString(key);
    }

    @Nullable
    @Contract("_, !null -> !null")
    public Boolean get_config(@NonNull String key, @Nullable Boolean defaultValue) {
        if (mConf.isNull(key)) {
            return defaultValue;
        }
        return mConf.getBoolean(key);
    }

    @Nullable
    @Contract("_, !null -> !null")
    public Long get_config(@NonNull String key, @Nullable Long defaultValue) {
        if (mConf.isNull(key)) {
            return defaultValue;
        }
        return mConf.getLong(key);
    }

    @Nullable
    @Contract("_, !null -> !null")
    public Integer get_config(@NonNull String key, @Nullable Integer defaultValue) {
        if (mConf.isNull(key)) {
            return defaultValue;
        }
        return mConf.getInt(key);
    }

    @Contract("_, !null -> !null")
    public Double get_config(@NonNull String key, @Nullable Double defaultValue) {
        if (mConf.isNull(key)) {
            return defaultValue;
        }
        return mConf.getDouble(key);
    }

    @Nullable
    @Contract("_, !null -> !null")
    public String get_config(@NonNull String key, @Nullable String defaultValue) {
        if (mConf.isNull(key)) {
            return defaultValue;
        }
        return mConf.getString(key);
    }

    /** Edits to the config are not persisted to the preferences */
    @Nullable
    @Contract("_, !null -> !null")
    public JSONObject get_config(@NonNull String key, @Nullable JSONObject defaultValue) {
        if (mConf.isNull(key)) {
            return defaultValue == null ? null : new JSONObject(defaultValue);
        }
        return new JSONObject(mConf.getJSONObject(key));
    }

    /** Edits to the array are not persisted to the preferences */
    @Nullable
    @Contract("_, !null -> !null")
    public JSONArray get_config(@NonNull String key, @Nullable JSONArray defaultValue) {
        if (mConf.isNull(key)) {
            return defaultValue == null ? null : new JSONArray(defaultValue);
        }
        return new JSONArray(mConf.getJSONArray(key));
    }

    public void set_config(@NonNull String key, boolean value) {
        setMod();
        mConf.put(key, value);
    }

    public void set_config(@NonNull String key, long value) {
        setMod();
        mConf.put(key, value);
    }

    public void set_config(@NonNull String key, int value) {
        setMod();
        mConf.put(key, value);
    }

    public void set_config(@NonNull String key, double value) {
        setMod();
        mConf.put(key, value);
    }

    public void set_config(@NonNull String key, String value) {
        setMod();
        mConf.put(key, value);
    }

    public void set_config(@NonNull String key, JSONArray value) {
        setMod();
        mConf.put(key, value);
    }

    public void set_config(@NonNull String key, JSONObject value) {
        setMod();
        mConf.put(key, value);
    }

    public void remove_config(@NonNull String key) {
        setMod();
        mConf.remove(key);
    }

    //endregion

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


    public TagManager getTags() {
        return mTags;
    }


    public long getCrt() {
        return mCrt;
    }

    public Calendar crtCalendar() {
        return Time.calendar(getCrt() * 1000);
    }

    public GregorianCalendar crtGregorianCalendar() {
        return Time.gregorianCalendar(getCrt() * 1000);
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
    @CheckResult
    public List<Long> filterToValidCards(long[] cards) {
        return getDb().queryLongList("select id from cards where id in " + Utils.ids2str(cards));
    }

    public int queryVer() throws UnknownDatabaseVersionException {
        try {
            return getDb().queryScalar("select ver from col");
        } catch (Exception e) {
            throw new UnknownDatabaseVersionException(e);
        }
    }

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

    public boolean isUsingRustBackend() {
        return mDroidBackend.isUsingRustBackend();
    }

    public DroidBackend getBackend() {
        return mDroidBackend;
    }

    /** Allows a mock db to be inserted for testing */
    @VisibleForTesting
    public void setDb(DB database) {
        this.mDb = database;
    }

    public static class CheckDatabaseResult {
        private final List<String> mProblems = new ArrayList<>();
        private final long mOldSize;
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
            return !mProblems.isEmpty();
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

    @NonNull
    public Time getTime() {
        return mTime;
    }


    /**
     * Allows a collection to be used as a CollectionGetter
     * @return Itself.
     */
    public Collection getCol() {
        return this;
    }
}
