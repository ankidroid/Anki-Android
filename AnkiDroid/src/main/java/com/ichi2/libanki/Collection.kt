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

// "FunctionName": many libAnki functions used to have leading _s
@file:Suppress("FunctionName")

package com.ichi2.libanki

import androidx.annotation.CheckResult
import androidx.annotation.VisibleForTesting
import androidx.annotation.WorkerThread
import anki.card_rendering.EmptyCardsReport
import anki.collection.OpChanges
import anki.collection.OpChangesWithCount
import anki.config.ConfigKey
import anki.search.SearchNode
import anki.sync.SyncAuth
import anki.sync.SyncStatusResponse
import com.ichi2.libanki.Utils.ids2str
import com.ichi2.libanki.backend.model.toBackendNote
import com.ichi2.libanki.backend.model.toProtoBuf
import com.ichi2.libanki.exception.ConfirmModSchemaException
import com.ichi2.libanki.exception.InvalidSearchException
import com.ichi2.libanki.sched.DummyScheduler
import com.ichi2.libanki.sched.Scheduler
import com.ichi2.libanki.utils.Time
import com.ichi2.libanki.utils.TimeManager
import com.ichi2.utils.*
import net.ankiweb.rsdroid.Backend
import net.ankiweb.rsdroid.RustCleanup
import net.ankiweb.rsdroid.exceptions.BackendInvalidInputException
import timber.log.Timber
import java.io.*
import java.util.*

// Anki maintains a cache of used tags so it can quickly present a list of tags
// for autocomplete and in the browser. For efficiency, deletions are not
// tracked, so unused tags can only be removed from the list with a DB check.
//
// This module manages the tag cache and tags for notes.
@KotlinCleanup("inline function in init { } so we don't need to init `crt` etc... at the definition")
@WorkerThread
open class Collection(
    /**
     *  @param Path The path to the collection.anki2 database. Must be unicode and openable with [File].
     */
    val path: String,
    private var debugLog: Boolean, // Not in libAnki.
    /**
     * Outside of libanki, you should not access the backend directly for collection operations.
     * Operations that work on a closed collection (eg importing), or do not require a collection
     * at all (eg translations) are the exception.
     */
    val backend: Backend
) {
    /** Access backend translations */
    val tr = backend.tr

    @get:JvmName("isDbClosed")
    val dbClosed: Boolean
        get() {
            return dbInternal == null
        }

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

    /**
     * Getters/Setters ********************************************************** *************************************
     */

    val media: Media

    lateinit var decks: Decks
        protected set

    val tags: Tags

    lateinit var config: Config

    @KotlinCleanup("see if we can inline a function inside init {} and make this `val`")
    lateinit var sched: Scheduler
        protected set

    private var mStartTime: Long
    private var mStartReps: Int

    val mod: Long
        get() = db.queryLongScalar("select mod from col")

    val crt: Long
        get() = db.queryLongScalar("select crt from col")

    val scm: Long
        get() = db.queryLongScalar("select scm from col")

    private val lastSync: Long
        get() = db.queryLongScalar("select ls from col")

    fun usn(): Int {
        return -1
    }

    var ls: Long = 0
    // END: SQL table columns

    private var mLogHnd: PrintWriter? = null

    init {
        media = Media(this)
        tags = Tags(this)
        val created = reopen()
        log(path, VersionUtils.pkgVersionName)
        mStartReps = 0
        mStartTime = 0
        _loadScheduler()
        if (created) {
            sched.useNewTimezoneCode()
            config.set("schedVer", 2)
            // we need to reload the scheduler: this was previously loaded as V1
            _loadScheduler()
        }
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
        // schedVer was not set on legacy v1 collections
        val ver = config.get("schedVer") ?: 1
        return if (listOf(1, 2).contains(ver)) {
            ver
        } else {
            throw RuntimeException("Unsupported scheduler version")
        }
    }

    fun _loadScheduler() {
        val ver = schedVer()
        if (ver == 1) {
            sched = DummyScheduler(this)
        } else {
            if (!backend.getConfigBool(ConfigKey.Bool.SCHED_2021)) {
                backend.setConfigBool(ConfigKey.Bool.SCHED_2021, true, undoable = false)
            }
            sched = Scheduler(this)
            config.set("localOffset", sched.currentTimezoneOffset())
        }
    }

    /**
     * Disconnect from DB.
     * Python implementation has a save argument for legacy reasons;
     * AnkiDroid always saves as changes are made.
     */
    @Synchronized
    fun close(downgrade: Boolean = false, forFullSync: Boolean = false) {
        if (!dbClosed) {
            if (!forFullSync) {
                backend.closeCollection(downgrade)
            }
            dbInternal = null
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
            _openLog()
            if (afterFullSync) {
                _loadScheduler()
            }
            created
        } else {
            false
        }
    }

    fun load() {
        notetypes = Notetypes(this)
        decks = Decks(this)
        config = Config(backend)
    }

    /** Note: not in libanki.  Mark schema modified to force a full
     * sync, but with the confirmation checking function disabled This
     * is equivalent to `modSchema(False)` in Anki. A distinct method
     * is used so that the type does not states that an exception is
     * thrown when in fact it is never thrown.
     */
    open fun modSchemaNoCheck() {
        db.execute(
            "update col set scm=?, mod=?",
            TimeManager.time.intTimeMS(),
            TimeManager.time.intTimeMS()
        )
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
    fun schemaChanged(): Boolean {
        return scm > lastSync
    }

    /**
     * Object creation helpers **************************************************
     * *********************************************
     */
    fun getCard(id: Long): Card {
        return Card(this, id)
    }

    fun updateCards(cards: Iterable<Card>, skipUndoEntry: Boolean = false): OpChanges {
        return backend.updateCards(cards.map { it.toBackendCard() }, skipUndoEntry)
    }

    fun updateCard(card: Card, skipUndoEntry: Boolean = false): OpChanges {
        return updateCards(listOf(card), skipUndoEntry)
    }

    fun getNote(id: Long): Note {
        return Note(this, id)
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
        return newNote(notetypes.current(forDeck))
    }

    /**
     * Return a new note with a specific model
     * @param m The model to use for the new note
     * @return The new note
     */
    fun newNote(m: NotetypeJson): Note {
        return Note(this, m)
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

    /**
     * Return a list of card ids
     * @throws InvalidSearchException
     */
    fun findCards(
        search: String,
        order: SortOrder = SortOrder.NoOrdering()
    ): List<CardId> {
        val adjustedOrder = if (order is SortOrder.UseCollectionOrdering) {
            SortOrder.BuiltinSortKind(
                config.get("sortType") ?: "noteFld",
                config.get("sortBackwards") ?: false
            )
        } else {
            order
        }
        return try {
            backend.searchCards(search, adjustedOrder.toProtoBuf())
        } catch (e: BackendInvalidInputException) {
            throw InvalidSearchException(e)
        }
    }

    fun findNotes(
        query: String,
        order: SortOrder = SortOrder.NoOrdering()
    ): List<Long> {
        val adjustedOrder = if (order is SortOrder.UseCollectionOrdering) {
            SortOrder.BuiltinSortKind(
                config.get("noteSortType") ?: "noteFld",
                config.get("browserNoteSortBackwards") ?: false
            )
        } else {
            order
        }
        val noteIDsList = try {
            backend.searchNotes(query, adjustedOrder.toProtoBuf())
        } catch (e: BackendInvalidInputException) {
            throw InvalidSearchException(e)
        }
        return noteIDsList
    }

    data class CardIdToNoteId(val id: Long, val nid: Long)

    /** Return a list of card ids  */
    @RustCleanup("Remove in V16.") // Not in libAnki
    fun findOneCardByNote(query: String, order: SortOrder): List<CardId> {
        // This function shouldn't exist and CardBrowser should be modified to use Notes,
        // so not much effort was expended here

        val noteIds = findNotes(query, order)

        // select the card with the lowest `ord` to show
        val cursor = db.query(
            """
    SELECT c.id, card_with_min_ord.nid
    FROM (
      SELECT nid, MIN(ord) AS ord
      FROM cards
      WHERE nid IN ${Utils.ids2str(noteIds)} 
      GROUP BY nid
    ) AS card_with_min_ord
    JOIN cards AS c ON card_with_min_ord.nid = c.nid AND card_with_min_ord.ord = c.ord
            """.trimMargin()
        )
        val resultList = mutableListOf<CardIdToNoteId>()

        cursor.use { cur ->
            while (cur.moveToNext()) {
                val id = cur.getLong(cur.getColumnIndex("id"))
                val nid = cur.getLong(cur.getColumnIndex("nid"))
                resultList.add(CardIdToNoteId(id, nid))
            }
        }

        // sort resultList by nid
        val noteIdMap = noteIds.mapIndexed { index, id -> id to index }.toMap()
        val sortedResultList = resultList.sortedBy { noteIdMap[it.nid] }
        // Extract ids from sortedResultList
        return sortedResultList.map { it.id }
    }

    @RustCleanup("Calling code should handle returned OpChanges")
    fun findReplace(nids: List<Long>, src: String, dst: String, regex: Boolean = false, field: String? = null, fold: Boolean = true): Int {
        return backend.findAndReplace(nids, src, dst, regex, !fold, field ?: "").count
    }

    /*
      Stats ******************************************************************** ***************************
     */

    // card stats
    // stats

    /*
     * Timeboxing *************************************************************** ********************************
     */

    fun startTimebox() {
        mStartTime = TimeManager.time.intTime()
        mStartReps = sched.reps
    }

    data class TimeboxReached(val secs: Int, val reps: Int)

    /* Return (elapsedTime, reps) if timebox reached, or null.
    * Automatically restarts timebox if expired. */
    fun timeboxReached(): TimeboxReached? {
        if (sched.timeboxSecs() == 0) {
            // timeboxing disabled
            return null
        }
        val elapsed = TimeManager.time.intTime() - mStartTime
        val limit = sched.timeboxSecs()
        return if (elapsed > limit) {
            TimeboxReached(
                limit,
                sched.reps - mStartReps
            ).also {
                startTimebox()
            }
        } else {
            null
        }
    }

    /*
     * Undo ********************************************************************* **************************
     */

    /** eg "Undo suspend card" if undo available */
    fun undoLabel(): String? {
        val action = undoStatus().undo
        return action?.let { tr.undoUndoAction(it) }
    }

    fun undoAvailable(): Boolean {
        val status = undoStatus()
        return status.undo != null
    }

    fun redoLabel(): String? {
        val action = undoStatus().redo
        return action?.let { tr.undoRedoAction(it) }
    }

    fun redoAvailable(): Boolean {
        return undoStatus().redo != null
    }

    @RustCleanup("switch to removeNotes")
    fun remNotes(ids: LongArray) {
        removeNotes(nids = ids.asIterable())
    }

    fun removeNotes(nids: Iterable<NoteId> = listOf(), cids: Iterable<CardId> = listOf()): OpChangesWithCount {
        return backend.removeNotes(noteIds = nids, cardIds = cids)
    }

    fun removeCardsAndOrphanedNotes(cardIds: Iterable<Long>) {
        backend.removeCards(cardIds)
    }

    fun addNote(note: Note, deckId: DeckId): OpChanges {
        val resp = backend.addNote(note.toBackendNote(), deckId)
        note.id = resp.noteId
        return resp.changes
    }

    /** allowEmpty is ignored in the new schema */
    @RustCleanup("Remove this in favour of addNote() above; call addNote() inside undoableOp()")
    fun addNote(note: Note): Int {
        addNote(note, note.notetype.did)
        return note.numberOfCards()
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
                "or mid not in " + Utils.ids2str(notetypes.ids()) + " limit 1"
        ) > 0
        // notes without cards or models
        if (badNotes) {
            return false
        }
        // invalid ords
        for (m in notetypes.all()) {
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
        if (!debugLog) {
            return
        }
        Timber.i("Opening Collection Log")
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
        if (!debugLog) return
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

    lateinit var notetypes: Notetypes
        protected set

    //endregion

    fun crtGregorianCalendar(): GregorianCalendar {
        return Time.gregorianCalendar((crt * 1000))
    }

    /** Not in libAnki  */
    @CheckResult
    fun filterToValidCards(cards: LongArray?): List<Long> {
        return db.queryLongList("select id from cards where id in " + Utils.ids2str(cards))
    }

    fun setDeck(cids: Iterable<CardId>, did: DeckId): OpChangesWithCount {
        return backend.setDeck(cardIds = cids, deckId = did)
    }

    /** Save (flush) the note to the DB. Unlike note.flush(), this is undoable. This should
     * not be used for adding new notes. */
    fun updateNote(note: Note): OpChanges {
        return backend.updateNotes(notes = listOf(note.toBackendNote()), skipUndoEntry = false)
    }

    fun updateNotes(notes: Iterable<Note>): OpChanges {
        return backend.updateNotes(notes = notes.map { it.toBackendNote() }, skipUndoEntry = false)
    }

    fun emptyCids(): List<CardId> {
        return getEmptyCards().notesList.flatMap { it.cardIdsList }
    }

    /** Fixes and optimizes the database. If any errors are encountered, a list of
     * problems is returned. Throws if DB is unreadable. */
    fun fixIntegrity(): List<String> {
        return backend.checkDatabase()
    }

    /** Change the flag color of the specified cards. flag=0 removes flag. */
    fun setUserFlagForCards(cids: Iterable<Long>, flag: Int) {
        backend.setFlag(cardIds = cids, flag = flag)
    }

    fun getEmptyCards(): EmptyCardsReport {
        return backend.getEmptyCards()
    }

    fun syncStatus(auth: SyncAuth): SyncStatusResponse {
        return backend.syncStatus(input = auth)
    }

    /** Takes raw input from TypeScript frontend and returns suitable translations. */
    fun i18nResourcesRaw(input: ByteArray): ByteArray {
        return backend.i18nResourcesRaw(input = input)
    }

    // Python code has a cardsOfNote, but not vice-versa yet
    fun notesOfCards(cids: Iterable<CardId>): List<NoteId> {
        return db.queryLongList("select distinct nid from cards where id in ${ids2str(cids)}")
    }

    /**
     * returns the list of cloze ordinals in a note
     *
     * `"{{c1::A}} {{c3::B}}" => [1, 3]`
     */
    fun clozeNumbersInNote(n: Note): List<Int> {
        // the call appears to be non-deterministic. Sort ascending
        return backend.clozeNumbersInNote(n.toBackendNote())
            .sorted()
    }
}
