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

// remove "LeakingThis" this after CollectionV16 is inlined
// "FunctionName": many libAnki functions used to have leading _s
@file:Suppress("LeakingThis", "FunctionName")

package com.ichi2.libanki

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.content.res.Resources
import androidx.annotation.CheckResult
import androidx.annotation.VisibleForTesting
import androidx.annotation.WorkerThread
import anki.search.SearchNode
import anki.search.SearchNodeKt
import anki.search.searchNode
import com.ichi2.anki.CrashReportService
import com.ichi2.anki.R
import com.ichi2.anki.UIUtils
import com.ichi2.anki.exception.ConfirmModSchemaException
import com.ichi2.async.CancelListener
import com.ichi2.async.CancelListener.Companion.isCancelled
import com.ichi2.async.CollectionTask
import com.ichi2.async.ProgressSender
import com.ichi2.libanki.TemplateManager.TemplateRenderContext.TemplateRenderOutput
import com.ichi2.libanki.exception.UnknownDatabaseVersionException
import com.ichi2.libanki.sched.AbstractSched
import com.ichi2.libanki.sched.Sched
import com.ichi2.libanki.sched.SchedV2
import com.ichi2.libanki.sched.SchedV3
import com.ichi2.libanki.template.ParsedNode
import com.ichi2.libanki.template.TemplateError
import com.ichi2.libanki.utils.NotInLibAnki
import com.ichi2.libanki.utils.Time
import com.ichi2.libanki.utils.TimeManager
import com.ichi2.utils.*
import net.ankiweb.rsdroid.Backend
import net.ankiweb.rsdroid.RustCleanup
import org.jetbrains.annotations.Contract
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import timber.log.Timber
import java.io.*
import java.util.*
import java.util.concurrent.LinkedBlockingDeque
import java.util.regex.Pattern
import kotlin.math.max
import kotlin.random.Random

// Anki maintains a cache of used tags so it can quickly present a list of tags
// for autocomplete and in the browser. For efficiency, deletions are not
// tracked, so unused tags can only be removed from the list with a DB check.
//
// This module manages the tag cache and tags for notes.
@KotlinCleanup("Fix @Contract annotations to work in Kotlin")
@KotlinCleanup("TextUtils -> Kotlin isNotEmpty()")
@KotlinCleanup("inline function in init { } so we don't need to init `crt` etc... at the definition")
@KotlinCleanup("ids.size != 0")
@WorkerThread
open class Collection(
    /**
     * @return The context that created this Collection.
     */
    val context: Context,
    /**
     *  @param Path The path to the collection.anki2 database. Must be unicode and openable with [File].
     */
    val path: String,
    var server: Boolean,
    private var debugLog: Boolean, // Not in libAnki.
    /**
     * Outside of libanki, you should not access the backend directly for collection operations.
     * Operations that work on a closed collection (eg importing), or do not require a collection
     * at all (eg translations) are the exception.
     */
    val backend: Backend
) : CollectionGetter {
    /** Access backend translations */
    val tr = backend.tr

    @get:JvmName("isDbClosed")
    val dbClosed: Boolean
        get() {
            return dbInternal == null
        }

    open val newBackend: CollectionV16
        get() = throw Exception("invalid call to newBackend on old backend")

    open val newMedia: BackendMedia
        get() = throw Exception("invalid call to newMedia on old backend")

    open val newModels: ModelsV16
        get() = throw Exception("invalid call to newModels on old backend")

    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    fun debugEnsureNoOpenPointers() {
        val result = backend.getActiveSequenceNumbers()
        if (result.isNotEmpty()) {
            val numbers = result.toString()
            throw IllegalStateException("Contained unclosed sequence numbers: $numbers")
        }
    }

    // a lot of legacy code does not check for nullability
    val db: DB
        get() = dbInternal!!

    var dbInternal: DB? = null

    /** whether the v3 scheduler is enabled */
    open var v3Enabled: Boolean = false

    /**
     * Getters/Setters ********************************************************** *************************************
     */

    // private double mLastSave;
    val media: Media

    lateinit var decks: Decks
        protected set

    @KotlinCleanup("change to lazy")
    private var _models: ModelManager? = null
    val tags: Tags

    @KotlinCleanup(
        "move accessor methods here, maybe reconsider return type." +
            "See variable: conf"
    )
    protected var config: Config? = null

    @KotlinCleanup("see if we can inline a function inside init {} and make this `val`")
    lateinit var sched: AbstractSched
        protected set

    private var mStartTime: Long
    private var mStartReps: Int

    // BEGIN: SQL table columns
    open var crt: Long = 0
    open var mod: Long = 0
    open var scm: Long = 0

    @RustCleanup("remove")
    var dirty: Boolean = false
    private var mUsn = 0
    var ls: Long = 0
    // END: SQL table columns

    /* this getter is only for syncing routines, use usn() instead elsewhere */
    val usnForSync
        get() = mUsn

    // API 21: Use a ConcurrentLinkedDeque
    @KotlinCleanup("consider making this immutable")
    private lateinit var undo: LinkedBlockingDeque<UndoAction>

    private var mLogHnd: PrintWriter? = null

    init {
        media = initMedia()
        tags = initTags()
        val created = reopen()
        log(path, VersionUtils.pkgVersionName)
        // mLastSave = getTime().now(); // assigned but never accessed - only leaving in for upstream comparison
        clearUndo()
        if (crt == 0L) {
            crt = UIUtils.getDayStart(TimeManager.time) / 1000
        }
        mStartReps = 0
        mStartTime = 0
        _loadScheduler()
        if (!get_config("newBury", false)!!) {
            set_config("newBury", true)
        }
        if (created) {
            Storage.addNoteTypes(col, backend)
            col.onCreate()
            col.save()
        }
    }

    protected open fun initMedia(): Media {
        return Media(this, server)
    }

    protected open fun initDecks(): Decks {
        return Decks(this)
    }

    protected open fun initConf(): Config {
        return Config(backend)
    }

    protected open fun initTags(): Tags {
        return Tags(this)
    }

    protected open fun initModels(): ModelManager {
        val models = Models(this)
        models.load(loadColumn("models"))
        return models
    }

    fun name(): String {
        // TODO:
        return File(path).name.replace(".anki2", "")
    }

    /**
     * Scheduler
     * ***********************************************************
     */
    fun schedVer(): Int {
        val ver = get_config("schedVer", fDefaultSchedulerVersion)!!
        return if (fSupportedSchedulerVersions.contains(ver)) {
            ver
        } else {
            throw RuntimeException("Unsupported scheduler version")
        }
    }

    // Note: Additional members in the class duplicate this
    fun _loadScheduler() {
        val ver = schedVer()
        if (ver == 1) {
            sched = Sched(this)
        } else if (ver == 2) {
            sched = if (v3Enabled) {
                SchedV3(this.newBackend)
            } else {
                SchedV2(this)
            }
            if (!server) {
                set_config("localOffset", sched._current_timezone_offset())
            }
        }
    }

    @Throws(ConfirmModSchemaException::class)
    fun changeSchedulerVer(ver: Int) {
        if (ver == schedVer()) {
            return
        }
        if (!fSupportedSchedulerVersions.contains(ver)) {
            throw RuntimeException("Unsupported scheduler version")
        }
        modSchema()
        @SuppressLint("VisibleForTests")
        val v2Sched = SchedV2(this)
        clearUndo()
        if (ver == 1) {
            v2Sched.moveToV1()
        } else {
            v2Sched.moveToV2()
        }
        set_config("schedVer", ver)
        _loadScheduler()
    }

    /**
     * DB-related *************************************************************** ********************************
     */
    open fun load() {
        // Read in deck table columns
        db.query("""SELECT crt, mod, scm, dty, usn, ls, conf, dconf, tags FROM col""")
            .use { cursor ->
                if (!cursor.moveToFirst()) {
                    return
                }
                crt = cursor.getLong(0)
                mod = cursor.getLong(1)
                scm = cursor.getLong(2)
                dirty = cursor.getInt(3) == 1 // No longer used
                mUsn = cursor.getInt(4)
                ls = cursor.getLong(5)
            }
        decks = initDecks()
        config = initConf()
    }

    @KotlinCleanup("make sChunk lazy and remove this")
    private val chunk: Int
        // reduce the actual size a little bit.
        // In case db is not an instance of DatabaseChangeDecorator, sChunk evaluated on default window size
        get() {
            if (sChunk != 0) {
                return sChunk
            }
            // This is valid for the framework sqlite as far back as Android 5 / SDK21
            // https://github.com/aosp-mirror/platform_frameworks_base/blob/ba35a77c7c4494c9eb74e87d8eaa9a7205c426d2/core/res/res/values/config.xml#L1141
            val cursorWindowSize = SQLITE_WINDOW_SIZE_KB * 1024

            // reduce the actual size a little bit.
            // In case db is not an instance of DatabaseChangeDecorator, sChunk evaluated on default window size
            sChunk = (cursorWindowSize * 15.0 / 16.0).toInt()
            return sChunk
        }

    private fun loadColumn(columnName: String): String {
        var pos = 1
        val buf = StringBuilder()
        while (true) {
            db.query(
                "SELECT substr($columnName, ?, ?) FROM col",
                pos.toString(),
                chunk.toString()
            ).use { cursor ->
                if (!cursor.moveToFirst()) {
                    return buf.toString()
                }
                val res = cursor.getString(0)
                if (res.isEmpty()) {
                    return buf.toString()
                }
                buf.append(res)
                if (res.length < chunk) {
                    return buf.toString()
                }
                pos += chunk
            }
        }
    }

    /**
     * Mark DB modified. DB operations and the deck/tag/model managers do this automatically, so this is only necessary
     * if you modify properties of this object or the conf dict.
     */
    @RustCleanup("no longer required in v16 - all update immediately")
    fun setMod() {
        db.mod = true
    }

    /**
     * Flush state to DB, updating mod time.
     */
    open fun flush(mod: Long = 0) {
        Timber.i("flush - Saving information to DB...")
        this.mod = if (mod == 0L) TimeManager.time.intTimeMS() else mod
        val values = ContentValues().apply {
            put("crt", this@Collection.crt)
            put("mod", this@Collection.mod)
            put("scm", scm)
            put("dty", if (dirty) 1 else 0)
            put("usn", mUsn)
            put("ls", ls)
        }
        db.update("col", values)
    }

    protected open fun flushConf(): Boolean {
        return true
    }

    /**
     * Flush, commit DB, and take out another write lock.
     */
    @Synchronized
    @Suppress("UNUSED_PARAMETER") // name is required by tests and likely should be used
    fun save(name: String? = null, mod: Long = 0) {
        // let the managers conditionally flush
        models.flush()
        // and flush deck + bump mod if db has been changed
        if (db.mod) {
            flush(mod)
            db.commit()
            db.mod = false
        }
        // undoing non review operation is handled differently in ankidroid
//        _markOp(name);
        // mLastSave = getTime().now(); // assigned but never accessed - only leaving in for upstream comparison
    }

    /**
     * Disconnect from DB.
     */
    @Synchronized
    fun close(save: Boolean = true, downgrade: Boolean = false, forFullSync: Boolean = false) {
        if (!dbClosed) {
            try {
                if (save) {
                    db.executeInTransaction { this.save() }
                } else {
                    db.safeEndInTransaction()
                }
            } catch (e: RuntimeException) {
                Timber.w(e)
                CrashReportService.sendExceptionReport(e, "closeDB")
            }
            if (!forFullSync) {
                backend.closeCollection(downgrade)
            }
            dbInternal = null
            media.close()
            _closeLog()
            Timber.i("Collection closed")
        }
    }

    /** True if DB was created */
    fun reopen(afterFullSync: Boolean = false): Boolean {
        Timber.i("(Re)opening Database: %s", path)
        return if (dbClosed) {
            val (db_, created) = Storage.openDB(path, backend, afterFullSync)
            dbInternal = db_
            load()
            media.connect()
            _openLog()
            if (afterFullSync) {
                _loadScheduler()
            }
            created
        } else {
            false
        }
    }

    /** Note: not in libanki.  Mark schema modified to force a full
     * sync, but with the confirmation checking function disabled This
     * is equivalent to `modSchema(False)` in Anki. A distinct method
     * is used so that the type does not states that an exception is
     * thrown when in fact it is never thrown.
     */
    open fun modSchemaNoCheck() {
        scm = TimeManager.time.intTimeMS()
        setMod()
    }

    /** Mark schema modified to force a full sync.
     * ConfirmModSchemaException will be thrown if the user needs to be prompted to confirm the action.
     * If the user chooses to confirm then modSchemaNoCheck should be called, after which the exception can
     * be safely ignored, and the outer code called again.
     *
     * @throws ConfirmModSchemaException
     */
    @Throws(ConfirmModSchemaException::class)
    fun modSchema() {
        if (!schemaChanged()) {
            /* In Android we can't show a dialog which blocks the main UI thread
             Therefore we can't wait for the user to confirm if they want to do
             a full sync here, and we instead throw an exception asking the outer
             code to handle the user's choice */
            throw ConfirmModSchemaException()
        }
        modSchemaNoCheck()
    }

    /** True if schema changed since last sync.  */
    open fun schemaChanged(): Boolean {
        return scm > ls
    }

    @KotlinCleanup("maybe change to getter")
    open fun usn(): Int {
        return if (server) {
            mUsn
        } else {
            -1
        }
    }

    /**
     * Object creation helpers **************************************************
     * *********************************************
     */
    fun getCard(id: Long): Card {
        return Card(this, id)
    }

    fun getNote(id: Long): Note {
        return Note(this, id)
    }

    /**
     * Utils ******************************************************************** ***************************
     */
    fun nextID(typeParam: String): Int {
        val type = "next" + Character.toUpperCase(typeParam[0]) + typeParam.substring(1)
        val id: Int = try {
            get_config_int(type)
        } catch (e: JSONException) {
            Timber.w(e)
            1
        }
        set_config(type, id + 1)
        return id
    }

    /**
     * Rebuild the queue and reload data after DB modified.
     */
    fun reset() {
        sched.deferReset()
    }

    /**
     * Deletion logging ********************************************************* **************************************
     */
    fun _logRem(ids: Iterable<Long>, @Consts.REM_TYPE type: Int) {
        for (id in ids) {
            val values = ContentValues().apply {
                put("usn", usn())
                put("oid", id)
                put("type", type)
            }
            db.insert("graves", values)
        }
    }

    /**
     * Notes ******************************************************************** ***************************
     */
    fun noteCount(): Int {
        return db.queryScalar("SELECT count() FROM notes")
    }

    /**
     * Return a new note with the model derived from the deck or the configuration
     * @param forDeck When true it uses the model specified in the deck (mid), otherwise it uses the model specified in
     * the configuration (curModel)
     * @return The new note
     */
    fun newNote(forDeck: Boolean = true): Note {
        return newNote(models.current(forDeck)!!)
    }

    /**
     * Return a new note with a specific model
     * @param m The model to use for the new note
     * @return The new note
     */
    fun newNote(m: Model): Note {
        return Note(this, m)
    }

    /**
     * Add a note and cards to the collection. If allowEmpty, at least one card is generated.
     * @param note  The note to add to the collection
     * @param allowEmpty Whether we accept to add it even if it should generate no card. Useful to import note even if buggy
     * @return Number of card added
     * @return Number of card added.
     */
    open fun addNote(note: Note, allowEmpty: Models.AllowEmpty = Models.AllowEmpty.ONLY_CLOZE): Int {
        // check we have card models available, then save
        val cms = findTemplates(note, allowEmpty)
        // Todo: upstream, we accept to add a not even if it generates no card. Should be ported to ankidroid
        if (cms.isEmpty()) {
            return 0
        }
        note.flush()
        // deck conf governs which of these are used
        val due = nextID("pos")
        // add cards
        var ncards = 0
        for (template in cms) {
            _newCard(note, template, due)
            ncards += 1
        }
        return ncards
    }

    open fun remNotes(ids: LongArray) {
        val list = db
            .queryLongList("SELECT id FROM cards WHERE nid IN " + Utils.ids2str(ids))
        removeCardsAndOrphanedNotes(list)
    }

    /**
     * Bulk delete notes by ID. Don't call this directly.
     */
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun _remNotes(ids: kotlin.collections.Collection<Long>) {
        if (ids.isEmpty()) {
            return
        }
        val strids = Utils.ids2str(ids)
        // we need to log these independently of cards, as one side may have
        // more card templates
        _logRem(ids, Consts.REM_NOTE)
        db.execute("DELETE FROM notes WHERE id IN $strids")
    }

    /*
      Card creation ************************************************************ ***********************************
     */

    /**
     * @param note A note
     * @param allowEmpty whether we allow to have a card which is actually empty if it is necessary to return a non-empty list
     * @return (active), non-empty templates.
     */
    fun findTemplates(
        note: Note,
        allowEmpty: Models.AllowEmpty = Models.AllowEmpty.ONLY_CLOZE
    ): ArrayList<JSONObject> {
        val model = note.model()
        val avail = Models.availOrds(model, note.fields, allowEmpty)
        return _tmplsFromOrds(model, avail)
    }

    /**
     * @param model A note type
     * @param avail Ords of cards from this note type.
     * @return One template by element i of avail, for the i-th card. For standard template, avail should contains only existing ords.
     * for cloze, avail should contains only non-negative numbers, and the i-th card is a copy of the first card, with a different ord.
     */
    @KotlinCleanup("extract 'ok' and return without the return if")
    private fun _tmplsFromOrds(model: Model, avail: ArrayList<Int>): ArrayList<JSONObject> {
        val tmpls: JSONArray
        return if (model.isStd) {
            tmpls = model.getJSONArray("tmpls")
            val ok = ArrayList<JSONObject>(avail.size)
            for (ord in avail) {
                ok.add(tmpls.getJSONObject(ord))
            }
            ok
        } else {
            // cloze - generate temporary templates from first
            val template0 = model.getJSONArray("tmpls").getJSONObject(0)
            val ok = ArrayList<JSONObject>(avail.size)
            for (ord in avail) {
                val t = template0.deepClone()
                t.put("ord", ord)
                ok.add(t)
            }
            ok
        }
    }

    /**
     * Generate cards for non-empty templates, return ids to remove.
     */
    @KotlinCleanup("Check CollectionTask<Int?, Int> - should be fine")
    @KotlinCleanup("change to ArrayList!")
    fun genCards(nids: kotlin.collections.Collection<Long>, model: Model): ArrayList<Long>? {
        return genCards<CollectionTask<Int, Int>>(nids.toLongArray(), model)
    }

    fun genCards(nids: kotlin.collections.Collection<Long>, mid: NoteTypeId): ArrayList<Long>? {
        return genCards(nids, models.get(mid)!!)
    }

    fun genCards(
        nid: NoteId,
        model: Model
    ): ArrayList<Long>? {
        return genCards("($nid)", model, task = null)
    }

    fun <T> genCards(
        nid: NoteId,
        model: Model,
        task: T? = null
    ): ArrayList<Long>? where T : ProgressSender<Int>?, T : CancelListener? {
        return genCards("($nid)", model, task)
    }

    /**
     * @param nids All ids of nodes of a note type
     * @param task Task to check for cancellation and update number of card processed
     * @return Cards that should be removed because they should not be generated
     */
    fun <T> genCards(
        nids: LongArray,
        model: Model,
        task: T? = null
    ): ArrayList<Long>? where T : ProgressSender<Int>?, T : CancelListener? {
        // build map of (nid,ord) so we don't create dupes
        val snids = Utils.ids2str(nids)
        return genCards(snids, model, task)
    }

    /**
     * @param snids All ids of nodes of a note type, separated by comma
     * @param model
     * @param task Task to check for cancellation and update number of card processed
     * @return Cards that should be removed because they should not be generated
     * @param <T>
     </T> */
    @KotlinCleanup("see if we can cleanup if (!have.containsKey(nid)) { to a default dict or similar?")
    @KotlinCleanup("use task framework to handle cancellation, don't return null")
    fun <T> genCards(
        snids: String,
        model: Model,
        task: T?
    ): ArrayList<Long>? where T : ProgressSender<Int>?, T : CancelListener? {
        val nbCount = noteCount()
        // For each note, indicates ords of cards it contains
        val have = HashUtil.HashMapInit<Long, HashMap<Int, Long>>(nbCount)
        // For each note, the deck containing all of its cards, or 0 if siblings in multiple deck
        val dids = HashUtil.HashMapInit<Long, Long>(nbCount)
        // For each note, an arbitrary due of one of its due card processed, if any exists
        val dues = HashUtil.HashMapInit<Long, Long>(nbCount)
        var nodes: List<ParsedNode?>? = null
        if (model.getInt("type") != Consts.MODEL_CLOZE) {
            nodes = model.parsedNodes()
        }
        db.query("select id, nid, ord, (CASE WHEN odid != 0 THEN odid ELSE did END), (CASE WHEN odid != 0 THEN odue ELSE due END), type from cards where nid in $snids")
            .use { cur ->
                while (cur.moveToNext()) {
                    if (isCancelled(task)) {
                        Timber.v("Empty card cancelled")
                        return null
                    }
                    val id = cur.getLong(0)
                    val nid = cur.getLong(1)
                    val ord = cur.getInt(2)
                    val did = cur.getLong(3)
                    val due = cur.getLong(4)

                    @Consts.CARD_TYPE val type = cur.getInt(5)

                    // existing cards
                    if (!have.containsKey(nid)) {
                        have[nid] = HashMap()
                    }
                    have[nid]!![ord] = id
                    // and their dids
                    if (dids.containsKey(nid)) {
                        if (dids[nid] != 0L && !Utils.equals(dids[nid], did)) {
                            // cards are in two or more different decks; revert to model default
                            dids[nid] = 0L
                        }
                    } else {
                        // first card or multiple cards in same deck
                        dids[nid] = did
                    }
                    if (!dues.containsKey(nid) && type == Consts.CARD_TYPE_NEW) {
                        dues[nid] = due
                    }
                }
            }
        // build cards for each note
        val data = ArrayList<Array<Any>>()

        @Suppress("UNUSED_VARIABLE")
        var ts = TimeManager.time.maxID(db)
        val now = TimeManager.time.intTime()
        val rem =
            ArrayList<Long>(db.queryScalar("SELECT count() FROM notes where id in $snids"))
        val usn = usn()
        db.query("SELECT id, flds FROM notes WHERE id IN $snids").use { cur ->
            while (cur.moveToNext()) {
                if (isCancelled(task)) {
                    Timber.v("Empty card cancelled")
                    return null
                }
                val nid = cur.getLong(0)
                val flds = cur.getString(1)
                val avail =
                    Models.availOrds(model, Utils.splitFields(flds), nodes, Models.AllowEmpty.TRUE)
                task?.doProgress(avail.size)
                var did = dids[nid]
                // use sibling due if there is one, else use a new id
                val due = dues.getOrElse(nid) { nextID("pos").toLong() }
                if (did == null || did == 0L) {
                    did = model.did
                }
                // add any missing cards
                val tmpls = _tmplsFromOrds(model, avail)
                for (t in tmpls) {
                    val tord = t.getInt("ord")
                    val doHave = have.containsKey(nid) && have[nid]!!.containsKey(tord)
                    if (!doHave) {
                        // check deck is not a cram deck
                        var ndid: DeckId
                        try {
                            ndid = t.optLong("did", 0)
                            if (ndid != 0L) {
                                did = ndid
                            }
                        } catch (e: JSONException) {
                            Timber.w(e)
                            // do nothing
                        }
                        if (decks.isDyn(did!!)) {
                            did = 1L
                        }
                        // if the deck doesn't exist, use default instead
                        did = decks.get(did).getLong("id")
                        // give it a new id instead
                        data.add(arrayOf(ts, nid, did, tord, now, usn, due))
                        ts += 1
                    }
                }
                // note any cards that need removing
                if (have.containsKey(nid)) {
                    for ((key, value) in have[nid]!!) {
                        if (!avail.contains(key)) {
                            rem.add(value)
                        }
                    }
                }
            }
        }
        // bulk update
        db.executeMany(
            "INSERT INTO cards VALUES (?,?,?,?,?,?,0,0,?,0,0,0,0,0,0,0,0,\"\")",
            data
        )
        return rem
    }

    /**
     * Create a new card.
     */
    private fun _newCard(
        note: Note,
        template: JSONObject,
        due: Int,
        @Suppress("SameParameterValue") parameterDid: DeckId = 0L,
        flush: Boolean = true
    ): Card {
        val card = Card(this)
        return getNewLinkedCard(card, note, template, due, parameterDid, flush)
    }

    // This contains the original libanki implementation of _newCard, with the added parameter that
    // you pass the Card object in. This allows you to work on 'Card' subclasses that may not have
    // actual backing store (for instance, if you are previewing unsaved changes on templates)
    // TODO: use an interface that we implement for card viewing, vs subclassing an active model to workaround libAnki
    @KotlinCleanup("use card.nid in the query to remove the need for a few variables.")
    fun getNewLinkedCard(
        card: Card,
        note: Note,
        template: JSONObject,
        due: Int,
        parameterDid: DeckId,
        flush: Boolean
    ): Card {
        val nid = note.id
        card.nid = nid
        val ord = template.getInt("ord")
        card.ord = ord
        var did =
            db.queryLongScalar("select did from cards where nid = ? and ord = ?", nid, ord)
        // Use template did (deck override) if valid, otherwise did in argument, otherwise model did
        if (did == 0L) {
            did = template.optLong("did", 0)
            if (did > 0 && decks.get(did, false) != null) {
                // did is valid
            } else if (parameterDid != 0L) {
                did = parameterDid
            } else {
                did = note.model().optLong("did", 0)
            }
        }
        card.did = did
        // if invalid did, use default instead
        val deck = decks.get(card.did)
        if (deck.isDyn) {
            // must not be a filtered deck
            card.did = Consts.DEFAULT_DECK_ID
        } else {
            card.did = deck.getLong("id")
        }
        card.due = _dueForDid(card.did, due).toLong()
        if (flush) {
            card.flush()
        }
        return card
    }

    private fun _dueForDid(did: DeckId, due: Int): Int {
        val conf = decks.confForDid(did)
        // in order due?
        return if (conf.getJSONObject("new")
            .getInt("order") == Consts.NEW_CARDS_DUE
        ) {
            due
        } else {
            // random mode; seed with note ts so all cards of this note get
            // the same random number
            val r = Random(due.toLong())
            r.nextInt(max(due, 1000) - 1) + 1
        }
    }

    /**
     * Cards ******************************************************************** ***************************
     */
    val isEmpty: Boolean
        get() = db.queryScalar("SELECT 1 FROM cards LIMIT 1") == 0

    fun cardCount(): Int {
        return db.queryScalar("SELECT count() FROM cards")
    }

    // NOT IN LIBANKI //
    fun cardCount(vararg dids: Long): Int {
        return db.queryScalar("SELECT count() FROM cards WHERE did IN " + Utils.ids2str(dids))
    }

    fun isEmptyDeck(vararg dids: Long): Boolean {
        return cardCount(*dids) == 0
    }

    /**
     * Bulk delete cards by ID.
     */
    open fun removeCardsAndOrphanedNotes(cardIds: Iterable<Long>) {
        removeCardsAndOrphanedNotes(cardIds, true)
    }

    /**
     * Bulk delete cards by ID.
     */
    fun removeCardsAndOrphanedNotes(ids: Iterable<Long>, notes: Boolean) {
        if (!ids.iterator().hasNext()) {
            return
        }
        val sids = Utils.ids2str(ids)
        var nids: List<Long> = db.queryLongList("SELECT nid FROM cards WHERE id IN $sids")
        // remove cards
        _logRem(ids, Consts.REM_CARD)
        db.execute("DELETE FROM cards WHERE id IN $sids")
        // then notes
        if (!notes) {
            return
        }
        nids = db.queryLongList(
            "SELECT id FROM notes WHERE id IN " + Utils.ids2str(nids) +
                " AND id NOT IN (SELECT nid FROM cards)"
        )
        _remNotes(nids)
    }

    fun emptyCids(): List<Long> {
        val rem: MutableList<Long> = ArrayList()
        for (m in models.all()) {
            rem.addAll(genCards(models.nids(m), m)!!)
        }
        return rem
    }

    /** Returned data from [_fieldData] */
    private data class FieldData(val nid: NoteId, val modelId: NoteTypeId, val flds: String)

    /**
     * Field checksums and sorting fields ***************************************
     * ********************************************************
     */
    private fun _fieldData(snids: String): ArrayList<FieldData> {
        val result = ArrayList<FieldData>(
            db.queryScalar("SELECT count() FROM notes WHERE id IN$snids")
        )
        db.query("SELECT id, mid, flds FROM notes WHERE id IN $snids").use { cur ->
            while (cur.moveToNext()) {
                result.add(FieldData(nid = cur.getLong(0), modelId = cur.getLong(1), flds = cur.getString(2)))
            }
        }
        return result
    }

    /** Update field checksums and sort cache, after find&replace, etc.
     * @param nids
     */
    fun updateFieldCache(nids: kotlin.collections.Collection<Long>) {
        val snids = Utils.ids2str(nids)
        updateFieldCache(snids)
    }

    /** Update field checksums and sort cache, after find&replace, etc.
     * @param nids
     */
    fun updateFieldCache(nids: LongArray) {
        val snids = Utils.ids2str(nids)
        updateFieldCache(snids)
    }

    /** Update field checksums and sort cache, after find&replace, etc.
     * @param snids comma separated nids
     */
    fun updateFieldCache(snids: String) {
        val data = _fieldData(snids)
        val r = ArrayList<Array<Any>>(data.size)
        for (o in data) {
            val fields = Utils.splitFields(o.flds)
            val model = models.get(o.modelId)
                ?: // note point to invalid model
                continue
            val csumAndStrippedFieldField = Utils.sfieldAndCsum(fields, models.sortIdx(model))
            r.add(arrayOf(csumAndStrippedFieldField.first, csumAndStrippedFieldField.second, o.nid))
        }
        // apply, relying on calling code to bump usn+mod
        db.executeMany("UPDATE notes SET sfld=?, csum=? WHERE id=?", r)
    }
    /*
      Q/A generation *********************************************************** ************************************
     */
    /**
     * Returns hash of id, question, answer.
     */
    fun _renderQA(
        cid: CardId,
        model: Model,
        did: DeckId,
        ord: Int,
        tags: String,
        flist: Array<String>,
        flags: Int
    ): HashMap<String, String> {
        return _renderQA(cid, model, did, ord, tags, flist, flags, false, null, null)
    }

    @RustCleanup("#8951 - Remove FrontSide added to the front")
    fun _renderQA(
        cid: CardId,
        model: Model,
        did: DeckId,
        ord: Int,
        tags: String,
        flist: Array<String>,
        flags: Int,
        browser: Boolean,
        qfmtParam: String?,
        afmtParam: String?
    ): HashMap<String, String> {
        // data is [cid, nid, mid, did, ord, tags, flds, cardFlags]
        // unpack fields and create dict
        var qfmt = qfmtParam
        var afmt = afmtParam
        val fmap = Models.fieldMap(model)
        val maps: Set<Map.Entry<String, Pair<Int, JSONObject>>> = fmap.entries
        val fields: MutableMap<String, String> = HashUtil.HashMapInit(maps.size + 8)
        for ((key, value) in maps) {
            fields[key] = flist[value.first]
        }
        val cardNum = ord + 1
        fields["Tags"] = tags.trim { it <= ' ' }
        fields["Type"] = model.getString("name")
        fields["Deck"] = decks.name(did)
        val baseName = Decks.basename(fields["Deck"]!!)
        fields["Subdeck"] = baseName
        fields["CardFlag"] = _flagNameFromCardFlags(flags)
        val template: JSONObject = if (model.isStd) {
            model.getJSONArray("tmpls").getJSONObject(ord)
        } else {
            model.getJSONArray("tmpls").getJSONObject(0)
        }
        fields["Card"] = template.getString("name")
        fields[String.format(Locale.US, "c%d", cardNum)] = "1"
        // render q & a
        val d = HashUtil.HashMapInit<String, String>(2)
        d["id"] = cid.toString()
        qfmt = if (qfmt.isNullOrEmpty()) template.getString("qfmt") else qfmt
        afmt = if (afmt.isNullOrEmpty()) template.getString("afmt") else afmt
        for (p in arrayOf<Pair<String, String>>(Pair("q", qfmt!!), Pair("a", afmt!!))) {
            val type = p.first
            var format = p.second
            if ("q" == type) {
                format = fClozePatternQ.matcher(format)
                    .replaceAll(String.format(Locale.US, "{{$1cq-%d:", cardNum))
                format = fClozeTagStart.matcher(format)
                    .replaceAll(String.format(Locale.US, "<%%cq:%d:", cardNum))
                fields["FrontSide"] = ""
            } else {
                format = fClozePatternA.matcher(format)
                    .replaceAll(String.format(Locale.US, "{{$1ca-%d:", cardNum))
                format = fClozeTagStart.matcher(format)
                    .replaceAll(String.format(Locale.US, "<%%ca:%d:", cardNum))
                // the following line differs from libanki // TODO: why?
                fields["FrontSide"] =
                    d["q"]!! // fields.put("FrontSide", mMedia.stripAudio(d.get("q")));
            }
            var html: String
            html = try {
                ParsedNode.parse_inner(format).render(fields, "q" == type, context)
            } catch (er: TemplateError) {
                Timber.w(er)
                er.message(context)
            }
            if (!browser) {
                // browser don't show image. So compiling LaTeX actually remove information.
                val svg = model.optBoolean("latexsvg", false)
                html = LaTeX.mungeQA(html, this, svg)
            }
            d[type] = html
            // empty cloze?
            if ("q" == type && model.isCloze) {
                if (Models._availClozeOrds(model, flist, false).isEmpty()) {
                    val link = String.format(
                        """<a href="%s">%s</a>""",
                        context.resources.getString(R.string.link_ankiweb_docs_cloze_deletion),
                        "help"
                    )
                    println(link)
                    d["q"] = context.getString(R.string.empty_cloze_warning, link)
                }
            }
        }
        return d
    }

    private fun _flagNameFromCardFlags(flags: Int): String {
        val flag = flags and 0b111
        return if (flag == 0) {
            ""
        } else {
            "flag$flag"
        }
    }
    /*
      Finding cards ************************************************************ ***********************************
     */

    /**
     * Construct a search string from the provided search nodes. For example:
     * */
    /*
            import anki.search.searchNode
            import anki.search.SearchNode
            import anki.search.SearchNodeKt.group

            val node = searchNode {
                group = SearchNodeKt.group {
                    joiner = SearchNode.Group.Joiner.AND
                    nodes += searchNode { deck = "a **test** deck" }
                    nodes += searchNode {
                        negated = searchNode {
                            tag = "foo"
                        }
                    }
                    nodes += searchNode { flag = SearchNode.Flag.FLAG_GREEN }
                }
            }
            // yields "deck:a \*\*test\*\* deck" -tag:foo flag:3
            val text = col.buildSearchString(node)
        }
    */
    fun buildSearchString(node: SearchNode): String {
        return backend.buildSearchString(node)
    }

    /** Return a list of card ids  */
    @KotlinCleanup("set reasonable defaults")
    fun findCards(search: String): List<Long> {
        return findCards(search, SortOrder.NoOrdering())
    }

    /**
     * @return A list of card ids
     * @throws com.ichi2.libanki.exception.InvalidSearchException Invalid search string
     */
    open fun findCards(search: String, order: SortOrder): List<Long> {
        return Finder(this).findCards(search, order)
    }

    /** Return a list of card ids  */
    @RustCleanup("Remove in V16.") // Not in libAnki
    fun findOneCardByNote(query: String?): List<Long> {
        return Finder(this).findOneCardByNote(query!!)
    }

    /** Return a list of note ids
     * @param order only used in overridden V16 findNotes() method
     * */
    open fun findNotes(query: String, order: SortOrder = SortOrder.NoOrdering()): List<Long> {
        return Finder(this).findNotes(query)
    }

    fun findReplace(nids: List<Long?>, src: String, dst: String): Int {
        return Finder.findReplace(this, nids, src, dst)
    }

    fun findReplace(nids: List<Long?>, src: String, dst: String, regex: Boolean): Int {
        return Finder.findReplace(this, nids, src, dst, regex)
    }

    fun findReplace(nids: List<Long?>, src: String, dst: String, field: String?): Int {
        return Finder.findReplace(this, nids, src, dst, field = field)
    }

    fun findReplace(
        nids: List<Long?>,
        src: String,
        dst: String,
        regex: Boolean,
        field: String?,
        fold: Boolean
    ): Int {
        return Finder.findReplace(this, nids, src, dst, regex, field, fold)
    }

    fun findDupes(fieldName: String?, search: String? = ""): List<Pair<String, List<Long>>> {
        return Finder.findDupes(this, fieldName, search)
    }

    @KotlinCleanup("inline in Finder.java after conversion to Kotlin")
    fun buildFindDupesString(fieldName: String, search: String): String {
        return buildSearchString(
            searchNode {
                group = SearchNodeKt.group {
                    joiner = SearchNode.Group.Joiner.AND
                    if (search.isNotEmpty()) {
                        nodes += searchNode { literalText = search }
                    }
                    nodes += searchNode { this.fieldName = fieldName }
                }
            }
        )
    }

    /*
      Stats ******************************************************************** ***************************
     */

    // card stats
    // stats

    /*
     * Timeboxing *************************************************************** ********************************
     */

    var timeLimit: Long
        get() = get_config_long("timeLim")
        set(seconds) {
            set_config("timeLim", seconds)
        }

    fun startTimebox() {
        mStartTime = TimeManager.time.intTime()
        mStartReps = sched.reps
    }

    /* Return (elapsedTime, reps) if timebox reached, or null. */
    fun timeboxReached(): Pair<Int, Int>? {
        if (get_config_long("timeLim") == 0L) {
            // timeboxing disabled
            return null
        }
        val elapsed = TimeManager.time.intTime() - mStartTime
        return if (elapsed > get_config_long("timeLim")) {
            Pair(
                get_config_int("timeLim"),
                sched.reps - mStartReps
            )
        } else {
            null
        }
    }

    /*
     * Undo ********************************************************************* **************************
     */

    /* Note from upstream:
     * this data structure is a mess, and will be updated soon
     * in the review case, [1, "Review", [firstReviewedCard, secondReviewedCard, ...], wasLeech]
     * in the checkpoint case, [2, "action name"]
     * wasLeech should have been recorded for each card, not globally
     */
    fun clearUndo() {
        undo = LinkedBlockingDeque<UndoAction>()
    }

    /** Undo menu item name, or "" if undo unavailable.  */
    @VisibleForTesting
    fun undoType(): UndoAction? {
        return if (!undo.isEmpty()) {
            undo.last
        } else {
            null
        }
    }

    open fun undoName(res: Resources): String {
        val type = undoType()
        return type?.name(res) ?: ""
    }

    open fun undoAvailable(): Boolean {
        Timber.d("undoAvailable() undo size: %s", undo.size)
        return !undo.isEmpty()
    }

    open fun undo(): Card? {
        val lastUndo: UndoAction = undo.removeLast()
        Timber.d("undo() of type %s", lastUndo.javaClass)
        return lastUndo.undo(this)
    }

    /**
     * In the legacy schema, this adds the undo action to the undo list.
     * In the new schema, this action is not useful, as the backend stores its own
     * undo information, and will clear the [undo] list when the backend has an undo
     * operation available. If you find an action is not undoable with the new backend,
     * you probably need to be calling the relevant backend method to perform it,
     * instead of trying to do it with raw SQL. */
    @BlocksSchemaUpgrade("audit all UI actions that call this, and make sure they call a backend method")
    @RustCleanup("this will be unnecessary after legacy schema dropped")
    fun markUndo(undoAction: UndoAction) {
        Timber.d("markUndo() of type %s", undoAction.javaClass)
        undo.add(undoAction)
        while (undo.size > UNDO_SIZE_MAX) {
            undo.removeFirst()
        }
    }

    open fun onCreate() {
        sched.useNewTimezoneCode()
        set_config("schedVer", 2)
        // we need to reload the scheduler: this was previously loaded as V1
        _loadScheduler()
    }

    open fun render_output(c: Card, reload: Boolean, browser: Boolean): TemplateRenderOutput? {
        return render_output_legacy(c, reload, browser)
    }

    @RustCleanup("Hack for Card Template Previewer, needs review")
    fun render_output_legacy(c: Card, reload: Boolean, browser: Boolean): TemplateRenderOutput {
        val f = c.note(reload)
        val m = c.model()
        val t = c.template()
        val did: DeckId = if (c.isInDynamicDeck) {
            c.oDid
        } else {
            c.did
        }
        val qa: HashMap<String, String> = if (browser) {
            val bqfmt = t.optString("bqfmt")
            val bafmt = t.optString("bafmt")
            _renderQA(
                cid = c.id,
                model = m,
                did = did,
                ord = c.ord,
                tags = f.stringTags(),
                flist = f.fields,
                flags = c.internalGetFlags(),
                browser = browser,
                qfmtParam = bqfmt,
                afmtParam = bafmt
            )
        } else {
            _renderQA(
                cid = c.id,
                model = m,
                did = did,
                ord = c.ord,
                tags = f.stringTags(),
                flist = f.fields,
                flags = c.internalGetFlags()
            )
        }
        return TemplateRenderOutput(
            question_text = qa["q"]!!,
            answer_text = qa["a"]!!,
            question_av_tags = listOf(),
            answer_av_tags = listOf(),
            css = c.model().getString("css")
        )
    }

    @VisibleForTesting
    class UndoReview(private val wasLeech: Boolean, private val clonedCard: Card) :
        UndoAction(R.string.undo_action_review) {
        override fun undo(col: Collection): Card {
            col.sched.undoReview(clonedCard, wasLeech)
            return clonedCard
        }
    }

    fun markReview(card: Card) {
        val wasLeech = card.note().hasTag("leech")
        val clonedCard = card.clone()
        markUndo(UndoReview(wasLeech, clonedCard))
    }

    /**
     * DB maintenance *********************************************************** ************************************
     */
    /*
     * Basic integrity check. Only used by unit tests.
     */
    @KotlinCleanup("have getIds() return a list of mids and define idsToStr over it")
    fun basicCheck(): Boolean {
        // cards without notes
        if (db.queryScalar("select 1 from cards where nid not in (select id from notes) limit 1") > 0) {
            return false
        }
        val badNotes = db.queryScalar(
            "select 1 from notes where id not in (select distinct nid from cards) " +
                "or mid not in " + Utils.ids2str(models.ids()) + " limit 1"
        ) > 0
        // notes without cards or models
        if (badNotes) {
            return false
        }
        // invalid ords
        for (m in models.all()) {
            // ignore clozes
            if (m.getInt("type") != Consts.MODEL_STD) {
                continue
            }
            // Make a list of valid ords for this model
            val tmpls = m.getJSONArray("tmpls")
            val badOrd = db.queryScalar(
                "select 1 from cards where (ord < 0 or ord >= ?) and nid in ( " +
                    "select id from notes where mid = ?) limit 1",
                tmpls.length(),
                m.getLong("id")
            ) > 0
            if (badOrd) {
                return false
            }
        }
        return true
    }

    fun log(vararg objects: Any?) {
        if (!debugLog) return

        val unixTime = TimeManager.time.intTime()

        val outerTraceElement = Thread.currentThread().stackTrace[3]
        val fileName = outerTraceElement.fileName
        val methodName = outerTraceElement.methodName

        val objectsString = objects
            .map { if (it is LongArray) Arrays.toString(it) else it }
            .joinToString(", ")

        writeLog("[$unixTime] $fileName:$methodName() $objectsString")
    }

    private fun writeLog(s: String) {
        mLogHnd?.let {
            try {
                it.println(s)
            } catch (e: Exception) {
                Timber.w(e, "Failed to write to collection log")
            }
        }
        Timber.d(s)
    }

    private fun _openLog() {
        Timber.i("Opening Collection Log")
        if (!debugLog) {
            return
        }
        try {
            val lpath = File(path.replaceFirst("\\.anki2$".toRegex(), ".log"))
            if (lpath.exists() && lpath.length() > 10 * 1024 * 1024) {
                val lpath2 = File("$lpath.old")
                if (lpath2.exists()) {
                    lpath2.delete()
                }
                lpath.renameTo(lpath2)
            }
            mLogHnd = PrintWriter(BufferedWriter(FileWriter(lpath, true)), true)
        } catch (e: IOException) {
            // turn off logging if we can't open the log file
            Timber.e("Failed to open collection.log file - disabling logging")
            debugLog = false
        }
    }

    private fun _closeLog() {
        Timber.i("Closing Collection Log")
        mLogHnd?.close()
        mLogHnd = null
    }

    /**
     * Card Flags *****************************************************************************************************
     */
    fun setUserFlag(flag: Int, cids: List<Long>) {
        assert(flag in (0..7))
        db.execute(
            "update cards set flags = (flags & ~?) | ?, usn=?, mod=? where id in " + Utils.ids2str(
                cids
            ),
            7,
            flag,
            usn(),
            TimeManager.time.intTime()
        )
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
    val models: ModelManager
        get() {
            if (_models == null) {
                _models = initModels()
            }
            return _models!!
        }

    /** Check if this collection is valid.  */
    fun validCollection(): Boolean {
        // TODO: more validation code
        return models.validateModel()
    }

    // region JSON-Related Config
    // Anki Desktop has a get_config and set_config method handling an "Any"
    // We're not dynamically typed, so add additional methods for each JSON type that
    // we can handle
    // methods with a default can be named `get_config` as the `defaultValue` argument defines the return type
    // NOTE: get_config("key", 1) and get_config("key", 1L) will return different types
    fun has_config(key: String): Boolean {
        // not in libAnki
        return config!!.has(key)
    }

    fun has_config_not_null(key: String): Boolean {
        // not in libAnki
        return has_config(key) && !config!!.isNull(key)
    }

    /** @throws JSONException object does not exist or can't be cast
     */
    fun get_config_boolean(key: String): Boolean {
        return config!!.getBoolean(key)
    }

    /** @throws JSONException object does not exist or can't be cast
     */
    fun get_config_long(key: String): Long {
        return config!!.getLong(key)
    }

    /** @throws JSONException object does not exist or can't be cast
     */
    fun get_config_int(key: String): Int {
        return config!!.getInt(key)
    }

    /** @throws JSONException object does not exist or can't be cast
     */
    @Suppress("unused")
    fun get_config_double(key: String): Double {
        return config!!.getDouble(key)
    }

    /**
     * Edits to this object are not persisted to preferences.
     * @throws JSONException object does not exist or can't be cast
     */
    @Suppress("unused")
    fun get_config_object(key: String): JSONObject {
        return config!!.getJSONObject(key).deepClone()
    }

    /** Edits to the array are not persisted to the preferences
     * @throws JSONException object does not exist or can't be cast
     */
    fun get_config_array(key: String): JSONArray {
        return config!!.getJSONArray(key).deepClone()
    }

    /**
     * If the value is null in the JSON, a string of "null" will be returned
     * @throws JSONException object does not exist, or can't be cast
     */
    fun get_config_string(key: String): String {
        return config!!.getString(key)
    }

    @Contract("_, !null -> !null")
    fun get_config(key: String, defaultValue: Boolean?): Boolean? {
        return if (config!!.isNull(key)) {
            defaultValue
        } else {
            config!!.getBoolean(key)
        }
    }

    @Contract("_, !null -> !null")
    fun get_config(key: String, defaultValue: Long?): Long? {
        return if (config!!.isNull(key)) {
            defaultValue
        } else {
            config!!.getLong(key)
        }
    }

    @Contract("_, !null -> !null")
    fun get_config(key: String, defaultValue: Int?): Int? {
        return if (config!!.isNull(key)) {
            defaultValue
        } else {
            config!!.getInt(key)
        }
    }

    @Contract("_, !null -> !null")
    fun get_config(key: String, defaultValue: Double?): Double? {
        return if (config!!.isNull(key)) {
            defaultValue
        } else {
            config!!.getDouble(key)
        }
    }

    @Contract("_, !null -> !null")
    fun get_config(key: String, defaultValue: String?): String? {
        return if (config!!.isNull(key)) {
            defaultValue
        } else {
            config!!.getString(key)
        }
    }

    /** Edits to the config are not persisted to the preferences  */
    @Contract("_, !null -> !null")
    fun get_config(key: String, defaultValue: JSONObject?): JSONObject? {
        return if (config!!.isNull(key)) {
            if (defaultValue == null) null else defaultValue.deepClone()
        } else {
            config!!.getJSONObject(key).deepClone()
        }
    }

    /** Edits to the array are not persisted to the preferences  */
    @Contract("_, !null -> !null")
    fun get_config(key: String, defaultValue: JSONArray?): JSONArray? {
        return if (config!!.isNull(key)) {
            if (defaultValue == null) null else JSONArray(defaultValue)
        } else {
            JSONArray(config!!.getJSONArray(key))
        }
    }

    fun set_config(key: String, value: Boolean) {
        setMod()
        config!!.put(key, value)
    }

    fun set_config(key: String, value: Long) {
        setMod()
        config!!.put(key, value)
    }

    fun set_config(key: String, value: Int) {
        setMod()
        config!!.put(key, value)
    }

    fun set_config(key: String, value: Double) {
        setMod()
        config!!.put(key, value)
    }

    fun set_config(key: String, value: String?) {
        setMod()
        config!!.put(key, value!!)
    }

    fun set_config(key: String, value: JSONArray?) {
        setMod()
        config!!.put(key, value!!)
    }

    fun set_config(key: String, value: JSONObject?) {
        setMod()
        config!!.put(key, value!!)
    }

    fun set_config(key: String, value: Any?) {
        setMod()
        config!!.put(key, value)
    }

    fun remove_config(key: String) {
        setMod()
        config!!.remove(key)
    }

    //endregion

    fun setUsnAfterSync(usn: Int) {
        mUsn = usn
    }

    fun crtGregorianCalendar(): GregorianCalendar {
        return Time.gregorianCalendar((crt * 1000))
    }

    /** Not in libAnki  */
    @CheckResult
    fun filterToValidCards(cards: LongArray?): List<Long> {
        return db.queryLongList("select id from cards where id in " + Utils.ids2str(cards))
    }

    @Throws(UnknownDatabaseVersionException::class)
    fun queryVer(): Int {
        return try {
            db.queryScalar("select ver from col")
        } catch (e: Exception) {
            throw UnknownDatabaseVersionException(e)
        }
    }

    open fun setDeck(cids: LongArray, did: Long) {
        db.execute(
            "update cards set did=?,usn=?,mod=? where id in " + Utils.ids2str(cids),
            did,
            usn(),
            TimeManager.time.intTime()
        )
    }

    class CheckDatabaseResult(private val oldSize: Long) {
        private val mProblems: MutableList<String?> = ArrayList()
        var cardsWithFixedHomeDeckCount = 0
        private var mNewSize: Long = 0

        /** When the database was locked  */
        var databaseLocked = false
            private set

        /** When the check failed with an error (or was locked)  */
        var failed = false
        fun addAll(strings: List<String?>?) {
            mProblems.addAll(strings!!)
        }

        fun hasProblems(): Boolean {
            return mProblems.isNotEmpty()
        }

        val problems: List<String?>
            get() = mProblems

        fun setNewSize(size: Long) {
            mNewSize = size
        }

        val sizeChangeInKb: Double
            get() = (oldSize - mNewSize) / 1024.0

        fun markAsFailed(): CheckDatabaseResult {
            failed = true
            return this
        }

        fun markAsLocked(): CheckDatabaseResult {
            setLocked(true)
            return markAsFailed()
        }

        private fun setLocked(@Suppress("SameParameterValue") value: Boolean) {
            databaseLocked = value
        }
    }

    /**
     * Allows a collection to be used as a CollectionGetter
     * @return Itself.
     */
    override val col: Collection
        get() = this

    /** https://stackoverflow.com/questions/62150333/lateinit-property-mock-object-has-not-been-initialized */
    @VisibleForTesting
    fun setScheduler(sched: AbstractSched) {
        this.sched = sched
    }

    companion object {
        @KotlinCleanup("Use kotlin's regex methods")
        private val fClozePatternQ = Pattern.compile("\\{\\{(?!type:)(.*?)cloze:")
        private val fClozePatternA = Pattern.compile("\\{\\{(.*?)cloze:")
        private val fClozeTagStart = Pattern.compile("<%cloze:")

        /**
         * This is only used for collections which were created before
         * the new collections default was v2
         * In that case, 'schedVer' is not set, so this default is used.
         * See: #8926
         */
        private const val fDefaultSchedulerVersion = 1
        private val fSupportedSchedulerVersions = listOf(1, 2)

        // other options
        const val DEFAULT_CONF = (
            "{" +
                // review options
                "\"activeDecks\": [1], " + "\"curDeck\": 1, " + "\"newSpread\": " + Consts.NEW_CARDS_DISTRIBUTE + ", " +
                "\"collapseTime\": 1200, " + "\"timeLim\": 0, " + "\"estTimes\": true, " + "\"dueCounts\": true, \"dayLearnFirst\":false, " +
                // other config
                "\"curModel\": null, " + "\"nextPos\": 1, " + "\"sortType\": \"noteFld\", " +
                "\"sortBackwards\": false, \"addToCur\": true }"
            ) // add new to currently selected deck?
        private const val UNDO_SIZE_MAX = 20
        private var sChunk = 0

        private const val SQLITE_WINDOW_SIZE_KB = 2048
    }
}

/**
 * @throws JSONException object can't be cast
 */
@NotInLibAnki
fun Collection.get_config_int(key: String, defaultValue: Int): Int {
    if (has_config_not_null(key)) {
        return get_config_int(key)
    }
    return defaultValue
}
