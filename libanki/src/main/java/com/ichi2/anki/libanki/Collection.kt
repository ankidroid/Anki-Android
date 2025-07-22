/*
 * Copyright (c) 2011 Norbert Nagold <norbert.nagold@gmail.com>
 * Copyright (c) 2012 Kostas Spyropoulos <inigo.aldana@gmail.com>
 * Copyright (c) 2024 David Allison <davidallisongithub@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General private License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General private License for more details.
 *
 * You should have received a copy of the GNU General private License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 *  This file incorporates code under the following license
 *  https://github.com/ankitects/anki/blob/33a923797afc9655c3b4f79847e1705a1f998d03/pylib/anki/browser.py
 *
 *    Copyright: Ankitects Pty Ltd and contributors
 *    License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
 */

// "FunctionName": many libAnki functions used to have leading _s
@file:Suppress("FunctionName")

package com.ichi2.anki.libanki

import androidx.annotation.CheckResult
import androidx.annotation.VisibleForTesting
import androidx.annotation.WorkerThread
import anki.card_rendering.EmptyCardsReport
import anki.collection.OpChanges
import anki.collection.OpChangesWithCount
import anki.config.ConfigKey
import anki.config.Preferences
import anki.config.copy
import anki.image_occlusion.GetImageForOcclusionResponse
import anki.image_occlusion.GetImageOcclusionNoteResponse
import anki.import_export.CsvMetadata
import anki.import_export.ExportAnkiPackageOptions
import anki.import_export.ExportLimit
import anki.import_export.ImportAnkiPackageOptions
import anki.import_export.ImportCsvRequest
import anki.import_export.ImportResponse
import anki.import_export.csvMetadataRequest
import anki.notes.AddNoteRequest
import anki.search.BrowserColumns
import anki.search.BrowserRow
import anki.search.SearchNode
import anki.search.SearchNode.Group.Joiner
import anki.stats.CardStatsResponse
import anki.stats.CardStatsResponse.StatsRevlogEntry
import anki.sync.SyncAuth
import anki.sync.SyncStatusResponse
import com.ichi2.anki.common.time.TimeManager
import com.ichi2.anki.common.utils.annotation.KotlinCleanup
import com.ichi2.anki.libanki.CollectionFiles.FolderBasedCollection
import com.ichi2.anki.libanki.CollectionFiles.InMemory
import com.ichi2.anki.libanki.Storage.OpenDbArgs
import com.ichi2.anki.libanki.Utils.ids2str
import com.ichi2.anki.libanki.backend.model.toBackendNote
import com.ichi2.anki.libanki.backend.model.toProtoBuf
import com.ichi2.anki.libanki.exception.ConfirmModSchemaException
import com.ichi2.anki.libanki.exception.InvalidSearchException
import com.ichi2.anki.libanki.sched.DummyScheduler
import com.ichi2.anki.libanki.sched.Scheduler
import com.ichi2.anki.libanki.utils.LibAnkiAlias
import com.ichi2.anki.libanki.utils.NotInLibAnki
import net.ankiweb.rsdroid.Backend
import net.ankiweb.rsdroid.RustCleanup
import net.ankiweb.rsdroid.exceptions.BackendInvalidInputException
import org.intellij.lang.annotations.Language
import timber.log.Timber
import java.io.File

typealias ImportLogWithChanges = anki.import_export.ImportResponse

@NotInLibAnki // Literal["AND", "OR"]
enum class SearchJoiner {
    AND,
    OR,
}

// Anki maintains a cache of used tags so it can quickly present a list of tags
// for autocomplete and in the browser. For efficiency, deletions are not
// tracked, so unused tags can only be removed from the list with a DB check.
//
// This module manages the tag cache and tags for notes.
@KotlinCleanup("inline function in init { } so we don't need to init `crt` etc... at the definition")
@RustCleanup("combine with BackendImportExport")
@RustCleanup("Config is not fully implemented")
@WorkerThread
class Collection(
    /**
     *  The path to the folder containing collection.anki2 database. Must be unicode and openable with [File].
     */

    val collectionFiles: CollectionFiles,
    /**
     * Outside of libanki, you should not access the backend directly for collection operations.
     * Operations that work on a closed collection (eg importing), or do not require a collection
     * at all (eg translations) are the exception.
     */
    val backend: Backend,
    databaseBuilder: (Backend) -> DB,
) {
    val colDb: File
        get() = collectionFiles.requireDiskBasedCollection().colDb

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

    private val lastSync: Long
        get() = db.queryLongScalar("select ls from col")

    var ls: Long = 0
    // END: SQL table columns

    init {
        media = Media(this)
        tags = Tags(this)
        val created = reopen(databaseBuilder = databaseBuilder)
        _loadScheduler()
        if (created) {
            config.set("schedVer", 2)
            // we need to reload the scheduler: this was previously loaded as V1
            _loadScheduler()
        }
    }

    /**
     * Scheduler
     * ***********************************************************
     */

    /**
     * For backwards compatibility, the v3 scheduler currently returns 2.
     * Use the separate [v3Scheduler] method to check if it is active.
     */
    @LibAnkiAlias("sched_ver")
    fun schedVer(): Int {
        @RustCleanup("move outside this method")
        @LibAnkiAlias("_supported_scheduler_versions")
        val supportedSchedulerVersions = listOf(1, 2)

        // for backwards compatibility, v3 is represented as 2
        val ver = config.get("schedVer") ?: 1
        if (ver in supportedSchedulerVersions) {
            return ver
        } else {
            throw RuntimeException("Unsupported scheduler version")
        }
    }

    @RustCleanup("doesn't match upstream")
    fun _loadScheduler() {
        val ver = schedVer()
        if (ver == 1) {
            sched = DummyScheduler(this)
        } else if (ver == 2) {
            if (!backend.getConfigBool(ConfigKey.Bool.SCHED_2021)) {
                backend.setConfigBool(ConfigKey.Bool.SCHED_2021, true, undoable = false)
            }
            sched = Scheduler(this)
            if (config.get<Int>("creationOffset") == null) {
                val prefs =
                    getPreferences().copy {
                        scheduling = scheduling.copy { newTimezone = true }
                    }
                setPreferences(prefs)
            }
        }
    }

    @LibAnkiAlias("v3_scheduler")
    fun v3Scheduler(): Boolean = schedVer() == 2 && backend.getConfigBool(ConfigKey.Bool.SCHED_2021)

    /**
     * @throws RuntimeException [enabled] requested, but not using the [schedVer][v2 scheduler]
     */
    @LibAnkiAlias("set_v3_scheduler")
    fun setV3Scheduler(enabled: Boolean) {
        if (this.v3Scheduler() != enabled) {
            if (enabled && schedVer() != 2) {
                throw RuntimeException("must upgrade to v2 scheduler first")
            }
            config.setBool(ConfigKey.Bool.SCHED_2021, enabled)
            _loadScheduler()
        }
    }

    /*
     * DB-related
     * ***********************************************************
     */

    // legacy properties; these will likely go away in the future

    val mod: Long
        get() = db.queryLongScalar("select mod from col")

    @RustCleanup("remove")
    @NotInLibAnki
    val scm: Long
        get() = db.queryLongScalar("select scm from col")

    /**
     * Disconnect from DB.
     * Python implementation has a save argument for legacy reasons;
     * AnkiDroid always saves as changes are made.
     */
    @Synchronized
    @LibAnkiAlias("close")
    @RustCleanup("doesn't match upstream")
    fun close(
        downgrade: Boolean = false,
        forFullSync: Boolean = false,
    ) {
        if (!dbClosed) {
            if (!forFullSync) {
                backend.closeCollection(downgrade)
            }
            dbInternal = null
            Timber.i("Collection closed")
        }
    }

    @LibAnkiAlias("close_for_full_sync")
    fun closeForFullSync() {
        // save and cleanup, but backend will take care of collection close
        if (dbInternal != null) {
            clearCaches()
            dbInternal = null
        }
    }

    @LibAnkiAlias("_clear_caches")
    private fun clearCaches() {
        notetypes.clearCache()
    }

    /** True if DB was created */
    @RustCleanup("doesn't match upstream")
    @LibAnkiAlias("reopen")
    fun reopen(
        afterFullSync: Boolean = false,
        databaseBuilder: (Backend) -> DB,
    ): Boolean {
        val reopenArgs =
            when (collectionFiles) {
                is InMemory, is CollectionFiles.InMemoryWithMedia -> OpenDbArgs.InMemory
                is FolderBasedCollection -> {
                    OpenDbArgs.Path(collectionFiles.colDb)
                }
            }
        Timber.i("(Re)opening Database: %s", reopenArgs)
        return if (dbClosed) {
            val (database, created) =
                Storage.openDB(
                    args = reopenArgs,
                    backend = backend,
                    afterFullSync = afterFullSync,
                    buildDatabase = databaseBuilder,
                )
            dbInternal = database
            load()
            if (afterFullSync) {
                _loadScheduler()
            }
            created
        } else {
            false
        }
    }

    @NotInLibAnki
    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    fun load() {
        notetypes = Notetypes(this)
        decks = Decks(this)
        config = Config(backend)
    }

    /** Mark schema modified to force a full
     * sync, but with the confirmation checking function disabled This
     * is equivalent to `modSchema(False)` in Anki. A distinct method
     * is used so that the type does not states that an exception is
     * thrown when in fact it is never thrown.
     */
    @NotInLibAnki
    fun modSchemaNoCheck() {
        db.execute(
            "update col set scm=?, mod=?",
            TimeManager.time.intTimeMS(),
            TimeManager.time.intTimeMS(),
        )
    }

    /** Mark schema modified to cause a one-way sync.
     * ConfirmModSchemaException will be thrown if the user needs to be prompted to confirm the action.
     * If the user chooses to confirm then modSchemaNoCheck should be called, after which the exception can
     * be safely ignored, and the outer code called again.
     *
     * @throws ConfirmModSchemaException
     */
    @LibAnkiAlias("mod_schema")
    fun modSchema() {
        if (!schemaChanged()) {
            /* In Android we can't show a dialog which blocks the main UI thread
             Therefore we can't wait for the user to confirm if they want to do
             a one-way sync here, and we instead throw an exception asking the outer
             code to handle the user's choice */
            throw ConfirmModSchemaException()
        }
        modSchemaNoCheck()
    }

    /** `true` if schema changed since last sync. */
    @LibAnkiAlias("schema_changed")
    @RustCleanup("doesn't match upstream")
    fun schemaChanged(): Boolean = scm > lastSync

    @LibAnkiAlias("usn")
    fun usn(): Int = -1

    /*
     * Import/export
     * ***********************************************************
     */

    /**
     * (Maybe) create a colpkg backup, while keeping the collection open. If the
     * configured backup interval has not elapsed, and force=false, no backup will be created,
     * and this routine will return false.
     *
     * There must not be an active transaction.
     *
     * If `waitForCompletion` is true, block until the backup completes. Otherwise this routine
     * returns quickly, and the backup can be awaited on a background thread with awaitBackupCompletion()
     * to check for success.
     *
     * Backups are automatically expired according to the user's settings.
     */
    @LibAnkiAlias("create_backup")
    fun createBackup(
        backupFolder: String,
        force: Boolean,
        waitForCompletion: Boolean,
    ): Boolean {
        // ensure any pending transaction from legacy code/add-ons has been committed
        val created =
            backend.createBackup(
                backupFolder = backupFolder,
                force = force,
                waitForCompletion = waitForCompletion,
            )
        return created
    }

    /**
     * If a backup is running, block until it completes, throwing if it fails, or already
     * failed, and the status has not yet been checked. On failure, an error is only returned
     * once; subsequent calls are a no-op until another backup is run.
     *
     * @throws Exception if backup creation failed, no-op after first throw
     */
    @LibAnkiAlias("await_backup_completion")
    fun awaitBackupCompletion() {
        backend.awaitBackupCompletion()
    }

    // export_collection_package is in AnkiDroid: BackendExporting.kt

    @LibAnkiAlias("import_anki_package")
    @RustCleanup("different input parameters - OK?")
    fun importAnkiPackage(
        packagePath: String,
        options: ImportAnkiPackageOptions,
    ): ImportResponse = backend.importAnkiPackage(packagePath, options)

    @LibAnkiAlias("export_anki_package")
    fun exportAnkiPackage(
        outPath: String,
        options: ExportAnkiPackageOptions,
        limit: ExportLimit,
    ): Int =
        backend.exportAnkiPackage(
            outPath = outPath,
            options = options,
            limit = limit,
        )

    @LibAnkiAlias("get_csv_metadata")
    fun getCsvMetadata(
        path: String,
        delimiter: CsvMetadata.Delimiter?,
    ): CsvMetadata {
        val request =
            csvMetadataRequest {
                this.path = path
                delimiter?.let { this.delimiter = delimiter }
            }
        return backend.getCsvMetadata(request)
    }

    @LibAnkiAlias("import_csv")
    @RustCleanup("not quite the same")
    fun importCsv(request: ImportCsvRequest): ImportLogWithChanges =
        backend.importCsv(
            path = request.path,
            metadata = request.metadata,
        )

    @LibAnkiAlias("export_note_csv")
    fun exportNoteCsv(
        outPath: String,
        limit: ExportLimit,
        withHtml: Boolean,
        withTags: Boolean,
        withDeck: Boolean,
        withNotetype: Boolean,
        withGuid: Boolean,
    ): Int =
        backend.exportNoteCsv(
            outPath = outPath,
            withHtml = withHtml,
            withTags = withTags,
            withDeck = withDeck,
            withNotetype = withNotetype,
            withGuid = withGuid,
            limit = limit,
        )

    @LibAnkiAlias("export_card_csv")
    fun exportCardCsv(
        outPath: String,
        limit: ExportLimit,
        withHtml: Boolean,
    ): Int =
        backend.exportCardCsv(
            outPath = outPath,
            withHtml = withHtml,
            limit = limit,
        )

    @LibAnkiAlias("import_json_file")
    fun importJsonFile(path: String): ImportLogWithChanges = backend.importJsonFile(path)

    @LibAnkiAlias("import_json_string")
    fun importJsonString(json: String): ImportLogWithChanges = backend.importJsonString(json)

    @LibAnkiAlias("export_dataset_for_research")
    fun exportDatasetForResearch(
        targetPath: String,
        minEntries: Int = 0,
    ) {
        backend.exportDataset(minEntries = minEntries, targetPath = targetPath)
    }

    /*
     * Image Occlusion
     * ***********************************************************
     */

    @CheckResult
    @LibAnkiAlias("get_image_for_occlusion")
    @RustCleanup("path should be nullable")
    fun getImageForOcclusion(path: String): GetImageForOcclusionResponse = backend.getImageForOcclusion(path = path)

    /** Add notetype if missing. */
    @LibAnkiAlias("add_image_occlusion_notetype")
    fun addImageOcclusionNoteType() {
        backend.addImageOcclusionNotetype()
    }

    @CheckResult
    @LibAnkiAlias("add_image_occlusion_note")
    fun addImageOcclusionNote(
        noteTypeId: NoteTypeId,
        imagePath: String,
        occlusions: String,
        header: String,
        backExtra: String,
        tags: List<String>,
    ): OpChanges =
        backend.addImageOcclusionNote(
            notetypeId = noteTypeId,
            imagePath = imagePath,
            occlusions = occlusions,
            header = header,
            backExtra = backExtra,
            tags = tags,
        )

    @CheckResult
    @LibAnkiAlias("get_image_occlusion_note")
    fun getImageOcclusionNote(noteId: NoteId): GetImageOcclusionNoteResponse = backend.getImageOcclusionNote(noteId = noteId)

    @CheckResult
    @LibAnkiAlias("update_image_occlusion_note")
    @RustCleanup("parameters should all be nullable")
    fun updateImageOcclusionNote(
        noteId: NoteId,
        occlusions: String,
        header: String,
        backExtra: String,
        tags: List<String>,
    ): OpChanges =
        backend.updateImageOcclusionNote(
            noteId = noteId,
            occlusions = occlusions,
            header = header,
            backExtra = backExtra,
            tags = tags,
        )

    /*
     * Object helpers
     * ***********************************************************
     */

    @CheckResult
    @LibAnkiAlias("get_card")
    fun getCard(id: CardId): Card = Card(this, id)

    /** Save card changes to database. */
    @LibAnkiAlias("update_cards")
    fun updateCards(
        cards: Iterable<Card>,
        skipUndoEntry: Boolean = false,
    ): OpChanges = backend.updateCards(cards.map { it.toBackendCard() }, skipUndoEntry)

    /** Save card changes to database. */
    @LibAnkiAlias("update_card")
    fun updateCard(
        card: Card,
        skipUndoEntry: Boolean = false,
    ): OpChanges = updateCards(listOf(card), skipUndoEntry)

    @CheckResult
    @LibAnkiAlias("get_note")
    fun getNote(id: NoteId): Note = Note(this, id)

    /** Save note changes to database. */
    @CheckResult
    @LibAnkiAlias("update_notes")
    fun updateNotes(
        notes: Iterable<Note>,
        skipUndoEntry: Boolean = false,
    ): OpChanges =
        backend.updateNotes(
            notes = notes.map { it.toBackendNote() },
            skipUndoEntry = skipUndoEntry,
        )

    /**
     * Save note changes to database.
     */
    @CheckResult
    @LibAnkiAlias("update_note")
    fun updateNote(
        note: Note,
        skipUndoEntry: Boolean = false,
    ): OpChanges = backend.updateNotes(notes = listOf(note.toBackendNote()), skipUndoEntry = skipUndoEntry)

    /*
     * Utils
     * ***********************************************************
     */

    @CheckResult
    @LibAnkiAlias("nextID")
    @RustCleanup("Python returns 'Any' - may fail for Double?")
    @Deprecated("not implemented", level = DeprecationLevel.HIDDEN)
    fun nextId(
        type: String,
        inc: Boolean = true,
    ): Long = TODO()

    /*
     * Notes
     * ***********************************************************
     */

    /**
     * Return a new note with a specific model
     * @param notetype The model to use for the new note
     * @return The new note
     */
    @LibAnkiAlias("new_note")
    fun newNote(notetype: NotetypeJson): Note = Note.fromNotetypeId(this, notetype.id)

    @LibAnkiAlias("add_note")
    fun addNote(
        note: Note,
        deckId: DeckId,
    ): OpChanges {
        val out = backend.addNote(note.toBackendNote(), deckId)
        note.id = out.noteId
        return out.changes
    }

    @LibAnkiAlias("add_notes")
    @RustCleanup("Implement")
    @Deprecated("Needs implementation", level = DeprecationLevel.HIDDEN)
    fun addNotes(requests: List<AddNoteRequest>): OpChanges? = TODO()
//    {
//        val out = backend.addNotes(requests = requests)
//        for ((idx, request) in requests.withIndex()) {
//            request.note!!.id = out.getNids(idx)
//        }
//        return out.changes
//    }

    @LibAnkiAlias("remove_notes")
    @RustCleanup("remove cids and pass in []")
    fun removeNotes(
        noteIds: Iterable<NoteId> = listOf(),
        cardIds: Iterable<CardId> = listOf(),
    ): OpChangesWithCount =
        backend.removeNotes(noteIds = noteIds, cardIds = cardIds).also {
            Timber.d("removeNotes: %d changes", it.count)
        }

    @LibAnkiAlias("remove_notes_by_card")
    fun removeNotesByCard(cardIds: Iterable<CardId>) {
        backend.removeNotes(noteIds = emptyList(), cardIds = cardIds)
    }

    @CheckResult
    @LibAnkiAlias("card_ids_of_note")
    fun cardIdsOfNote(nid: NoteId): List<CardId> = backend.cardsOfNote(nid = nid)

    /**
     * Get starting deck and notetype for add screen.
     * An option in the preferences controls whether this will be based on the current deck
     * or current notetype.
     */
    @CheckResult
    @LibAnkiAlias("defaults_for_adding")
    fun defaultsForAdding(currentReviewCard: Card? = null): anki.notes.DeckAndNotetype {
        val homeDeck = currentReviewCard?.currentDeckId() ?: 0L
        return backend.defaultsForAdding(homeDeckOfCurrentReviewCard = homeDeck)
    }

    /**
     * If 'change deck depending on notetype' is enabled in the preferences,
     * return the last deck used with the provided notetype, if any..
     */
    @CheckResult
    @LibAnkiAlias("default_deck_for_notetype")
    @RustCleanup("check if the == 0L logic is necessary")
    fun defaultDeckForNoteType(noteTypeId: NoteTypeId): DeckId? {
        if (config.getBool(ConfigKey.Bool.ADDING_DEFAULTS_TO_CURRENT_DECK)) {
            return null
        }

        val result = backend.defaultDeckForNotetype(ntid = noteTypeId)
        if (result == 0L) return null
        return result
    }

    @CheckResult
    @LibAnkiAlias("note_count")
    fun noteCount(): Int = db.queryScalar("SELECT count() FROM notes")

    /*
     * Cards
     * ***********************************************************
     */

    /**
     * Returns whether the collection contains no cards.
     */
    @LibAnkiAlias("is_empty")
    val isEmpty: Boolean
        get() = db.queryScalar("SELECT 1 FROM cards LIMIT 1") == 0

    @CheckResult
    @LibAnkiAlias("card_count")
    fun cardCount(): Int = db.queryScalar("SELECT count() FROM cards")

    /**
     * You probably want [removeNotesByCard] instead.
     *
     * @return the number of deleted cards. **Note:** if an invalid/duplicate [CardId] is provided,
     * the output count may be less than the input.
     */
    @RustCleanup("maybe deprecate this")
    @LibAnkiAlias("remove_cards_and_orphaned_notes")
    fun removeCardsAndOrphanedNotes(cardIds: Iterable<CardId>): OpChangesWithCount = backend.removeCards(cardIds)

    @LibAnkiAlias("set_deck")
    fun setDeck(
        cardIds: Iterable<CardId>,
        deckId: DeckId,
    ): OpChangesWithCount = backend.setDeck(cardIds = cardIds, deckId = deckId)

    @CheckResult
    @LibAnkiAlias("get_empty_cards")
    fun getEmptyCards(): EmptyCardsReport = backend.getEmptyCards()

    /*
     * Card generation & field checksums/sort fields
     * ***********************************************************
     */

    /** If notes modified directly in database, call this afterwards. */
    @LibAnkiAlias("after_note_updates")
    fun afterNoteUpdates(
        noteIds: List<NoteId>,
        markModified: Boolean,
        generateCards: Boolean = true,
    ) {
        backend.afterNoteUpdates(
            nids = noteIds,
            generateCards = generateCards,
            markNotesModified = markModified,
        )
    }

    /*
     * Finding cards
     * ***********************************************************
     */

    /**
     * Return a list of card ids
     * @throws InvalidSearchException
     */
    @CheckResult
    @RustCleanup("does not match libAnki; also fix docs")
    @LibAnkiAlias("find_cards")
    fun findCards(
        search: String,
        order: SortOrder = SortOrder.NoOrdering(),
    ): List<CardId> {
        val adjustedOrder =
            if (order is SortOrder.UseCollectionOrdering) {
                SortOrder.BuiltinSortKind(
                    config.get("sortType") ?: "noteFld",
                    config.get("sortBackwards") ?: false,
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

    @CheckResult
    @RustCleanup("does not match upstream")
    @LibAnkiAlias("find_notes")
    fun findNotes(
        query: String,
        order: SortOrder = SortOrder.NoOrdering(),
    ): List<NoteId> {
        val adjustedOrder =
            if (order is SortOrder.UseCollectionOrdering) {
                SortOrder.BuiltinSortKind(
                    config.get("noteSortType") ?: "noteFld",
                    config.get("browserNoteSortBackwards") ?: false,
                )
            } else {
                order
            }
        val noteIDsList =
            try {
                backend.searchNotes(query, adjustedOrder.toProtoBuf())
            } catch (e: BackendInvalidInputException) {
                throw InvalidSearchException(e)
            }
        return noteIDsList
    }

    // @LibAnkiAlias("_build_sort_mode")
    // private fun buildSortMode()

    /**
     * @return An [OpChangesWithCount] representing the number of affected notes
     */
    @CheckResult
    @LibAnkiAlias("find_and_replace")
    fun findAndReplace(
        nids: List<NoteId>,
        search: String,
        replacement: String,
        regex: Boolean = false,
        field: String? = null,
        matchCase: Boolean = false,
    ): OpChangesWithCount = backend.findAndReplace(nids, search, replacement, regex, matchCase, field ?: "")

    @LibAnkiAlias("field_names_for_note_ids")
    fun fieldNamesForNoteIds(nids: List<NoteId>): List<String> = backend.fieldNamesForNotes(nids)

    // returns array of ("dupestr", [nids])
    // @LibAnkiAlias("find_dupes")
    // fun findDupes(fieldName: String, search: String = ""): List<Pair<String, List<Any>>>

    /*
     * Search Strings
     * ***********************************************************
     */

    /**
     * Construct a search string from the provided search nodes. For example:
     * ```kotlin
     *       import anki.search.searchNode
     *       import anki.search.SearchNode
     *       import anki.search.SearchNodeKt.group
     *
     *       val node = searchNode {
     *           group = SearchNodeKt.group {
     *               joiner = SearchNode.Group.Joiner.AND
     *               nodes += searchNode { deck = "a **test** deck" }
     *               nodes += searchNode {
     *                   negated = searchNode {
     *                       tag = "foo"
     *                   }
     *               }
     *               nodes += searchNode { flag = SearchNode.Flag.FLAG_GREEN }
     *           }
     *       }
     *       // yields "deck:a \*\*test\*\* deck" -tag:foo flag:3
     *       val text = col.buildSearchString(node)
     *   }
     * ```
     */
    @RustCleanup("support SearchJoiner argument")
    @LibAnkiAlias("build_search_string")
    fun buildSearchString(
        node: SearchNode,
        joiner: SearchJoiner = SearchJoiner.AND,
    ): String = backend.buildSearchString(node)

    /**
     * Join provided search nodes and strings into a single [SearchNode].
     * If a single [SearchNode] is provided, it is returned as-is.
     * At least one node must be provided.
     *
     * @throws IllegalArgumentException if no nodes are provided
     */
    @Deprecated("not implemented")
    @RustCleanup("input upstream is either ")
    @LibAnkiAlias("group_searches")
    fun groupSearches(
        nodes: List<SearchNode>,
        joiner: SearchJoiner = SearchJoiner.AND,
    ): Nothing = TODO()

    /**
     * AND or OR `additional_term` to `existing_term`, without wrapping `existing_term` in brackets.
     * Used by the Browse screen to avoid adding extra brackets when joining.
     * If you're building a search query yourself, you probably don't need this.
     */
    @LibAnkiAlias("join_searches")
    fun joinSearches(
        existingNode: SearchNode,
        additionalNode: SearchNode,
        operator: SearchJoiner,
    ): String {
        val searchString =
            backend.joinSearchNodes(
                joiner = toPbSearchSeparator(operator),
                existingNode = existingNode,
                additionalNode = additionalNode,
            )
        return searchString
    }

    /**
     * If nodes of the same type as `replacement_node` are found in existing_node, replace them.
     *
     * You can use this to replace any "deck" clauses in a search with a different deck for example.
     */
    @LibAnkiAlias("replace_in_search_node")
    fun replaceInSearchNode(
        existingNode: SearchNode,
        replacementNode: SearchNode,
    ): String = backend.replaceSearchNode(existingNode = existingNode, replacementNode = replacementNode)

    @LibAnkiAlias("_pb_search_separator")
    fun toPbSearchSeparator(operator: SearchJoiner): SearchNode.Group.Joiner =
        when (operator) {
            SearchJoiner.AND -> Joiner.AND
            SearchJoiner.OR -> Joiner.OR
        }

    /*
     * Browser Table
     * ***********************************************************
     */

    @LibAnkiAlias("all_browser_columns")
    fun allBrowserColumns(): List<BrowserColumns.Column> = backend.allBrowserColumns()

    @LibAnkiAlias("get_browser_column")
    fun getBrowserColumn(key: String): BrowserColumns.Column? {
        for (column in backend.allBrowserColumns()) {
            if (column.key == key) {
                return column
            }
        }
        return null
    }

    /**
     * Returns a [BrowserRow], cells dependent on [Backend.setActiveBrowserColumns]
     *
     * WARN: As this is a latency-sensitive call, most callers should use [Backend.browserRowForId]
     *
     * @param id Either a [CardId] or a [NoteId], depending on the value of
     * [ConfigKey.Bool.BROWSER_TABLE_SHOW_NOTES_MODE]
     *
     * @see [setBrowserCardColumns]
     * @see [setBrowserNoteColumns]
     */
    // For performance, this does not match upstream:
    // https://github.com/ankitects/anki/blob/1fb1cbbf85c48a54c05cb4442b1b424a529cac60/pylib/anki/collection.py#L869-L881
    @LibAnkiAlias("browser_row_for_id")
    fun browserRowForId(id: Long): BrowserRow = backend.browserRowForId(id)

    /** Return the stored card column names and ensure the backend columns are set and in sync. */
    @LibAnkiAlias("load_browser_card_columns")
    fun loadBrowserCardColumns(): List<String> {
        val columns = config.get<List<String>>(BrowserConfig.ACTIVE_CARD_COLUMNS_KEY, BrowserDefaults.CARD_COLUMNS)!!
        backend.setActiveBrowserColumns(columns)
        return columns
    }

    @LibAnkiAlias("set_browser_card_columns")
    fun setBrowserCardColumns(columns: List<String>) {
        config.set(BrowserConfig.ACTIVE_CARD_COLUMNS_KEY, columns)
        backend.setActiveBrowserColumns(columns)
    }

    /** Return the stored note column names and ensure the backend columns are set and in sync. */
    @LibAnkiAlias("load_browser_note_columns")
    fun loadBrowserNoteColumns(): List<String> {
        val columns =
            config.get<List<String>>(
                BrowserConfig.ACTIVE_NOTE_COLUMNS_KEY,
                BrowserDefaults.NOTE_COLUMNS,
            )!!
        backend.setActiveBrowserColumns(columns)
        return columns
    }

    @LibAnkiAlias("set_browser_note_columns")
    fun setBrowserNoteColumns(columns: List<String>) {
        config.set(BrowserConfig.ACTIVE_NOTE_COLUMNS_KEY, columns)
        backend.setActiveBrowserColumns(columns)
    }

    /*
     * Stats
     * ***********************************************************
     */

    // def stats(self) -> anki.stats.CollectionStats:

    /**
     * Returns the data required to show card stats.
     *
     * If you wish to display the stats in a HTML table like Anki does,
     * you can use the .js file directly - see this add-on for an example:
     * https://ankiweb.net/shared/info/2179254157
     */
    @CheckResult
    @LibAnkiAlias("card_stats_data")
    fun cardStatsData(cardId: CardId): CardStatsResponse = backend.cardStats(cardId)

    @CheckResult
    @LibAnkiAlias("get_review_logs")
    fun getReviewLogs(cardId: CardId): List<StatsRevlogEntry> = backend.getReviewLogs(cardId)

    @RustCleanup("check sched.studiedToday")
    @CheckResult
    @LibAnkiAlias("studied_today")
    fun studiedToday(): String = backend.studiedToday()

    /*
     * Undo
     * ***********************************************************
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

    fun redoAvailable(): Boolean = undoStatus().redo != null

    lateinit var notetypes: Notetypes
        protected set

    /*
     * ***********************************************************
     */

    @NotInLibAnki
    @CheckResult
    fun filterToValidCards(cards: LongArray?): List<Long> = db.queryLongList("select id from cards where id in " + ids2str(cards))

    /** Fixes and optimizes the database. If any errors are encountered, a list of
     * problems is returned. Throws if DB is unreadable. */
    fun fixIntegrity(): List<String> = backend.checkDatabase()

    /** Change the flag color of the specified cards. flag=0 removes flag. */
    @CheckResult
    fun setUserFlagForCards(
        cids: Iterable<Long>,
        flag: Int,
    ): OpChangesWithCount = backend.setFlag(cardIds = cids, flag = flag)

    @Suppress("unused")
    fun syncStatus(auth: SyncAuth): SyncStatusResponse = backend.syncStatus(input = auth)

    /** Takes raw input from TypeScript frontend and returns suitable translations. */
    fun i18nResourcesRaw(input: ByteArray): ByteArray = backend.i18nResourcesRaw(input = input)

    // Python code has a cardsOfNote, but not vice-versa yet
    fun notesOfCards(cids: Iterable<CardId>): List<NoteId> = db.queryLongList("select distinct nid from cards where id in ${ids2str(cids)}")

    /**
     * returns the list of cloze ordinals in a note
     *
     * `"{{c1::A}} {{c3::B}}" => [1, 3]`
     */
    fun clozeNumbersInNote(n: Note): List<Int> {
        // the call appears to be non-deterministic. Sort ascending
        return backend
            .clozeNumbersInNote(n.toBackendNote())
            .sorted()
    }

    fun getImageForOcclusionRaw(input: ByteArray): ByteArray = backend.getImageForOcclusionRaw(input = input)

    fun getImageOcclusionNoteRaw(input: ByteArray): ByteArray = backend.getImageOcclusionNoteRaw(input = input)

    fun getImageOcclusionFieldsRaw(input: ByteArray): ByteArray = backend.getImageOcclusionFieldsRaw(input = input)

    fun addImageOcclusionNoteRaw(input: ByteArray): ByteArray = backend.addImageOcclusionNoteRaw(input = input)

    fun updateImageOcclusionNoteRaw(input: ByteArray): ByteArray = backend.updateImageOcclusionNoteRaw(input = input)

    fun congratsInfoRaw(input: ByteArray): ByteArray = backend.congratsInfoRaw(input = input)

    fun setWantsAbortRaw(input: ByteArray): ByteArray = backend.setWantsAbortRaw(input = input)

    fun latestProgressRaw(input: ByteArray): ByteArray = backend.latestProgressRaw(input = input)

    fun getSchedulingStatesWithContextRaw(input: ByteArray): ByteArray = backend.getSchedulingStatesWithContextRaw(input = input)

    fun setSchedulingStatesRaw(input: ByteArray): ByteArray = backend.setSchedulingStatesRaw(input = input)

    fun getChangeNotetypeInfoRaw(input: ByteArray): ByteArray = backend.getChangeNotetypeInfoRaw(input = input)

    fun changeNotetypeRaw(input: ByteArray): ByteArray = backend.changeNotetypeRaw(input = input)

    fun importJsonStringRaw(input: ByteArray): ByteArray = backend.importJsonStringRaw(input = input)

    fun importJsonFileRaw(input: ByteArray): ByteArray = backend.importJsonFileRaw(input = input)

    fun getIgnoredBeforeCountRaw(input: ByteArray): ByteArray = backend.getIgnoredBeforeCountRaw(input = input)

    fun getRetentionWorkloadRaw(input: ByteArray): ByteArray = backend.getRetentionWorkloadRaw(input = input)

    fun evaluateParamsLegacyRaw(input: ByteArray): ByteArray = backend.evaluateParamsLegacyRaw(input = input)

    /**
     * Converts Markdown ([text]) to HTML
     *
     * @param text Markdown to format as HTML
     * @param sanitize whether to sanitize the HTML using
     * [ammonia](https://docs.rs/ammonia/latest/ammonia/). `img` tags are also stripped
     */
    @Language("HTML")
    @LibAnkiAlias("render_markdown")
    fun renderMarkdown(
        text: String,
        sanitize: Boolean,
    ): String = backend.renderMarkdown(markdown = text, sanitize = sanitize)

    fun compareAnswer(
        expected: String,
        provided: String,
        combining: Boolean = true,
    ): String = backend.compareAnswer(expected = expected, provided = provided, combining = combining)

    fun extractClozeForTyping(
        text: String,
        ordinal: Int,
    ): String = backend.extractClozeForTyping(text = text, ordinal = ordinal)

    fun getPreferences(): Preferences = backend.getPreferences()

    fun setPreferences(preferences: Preferences): OpChanges = backend.setPreferences(preferences)

    /*
     * Timeboxing
     * ***********************************************************
     * Note: this will likely be removed in a future version of libAnki
     */

    private var startTime: Long = 0L
    private var startReps: Int = 0

    @LibAnkiAlias("startTimebox")
    fun startTimebox() {
        startTime = TimeManager.time.intTime()
        startReps = sched.numberOfAnswersRecorded
    }

    data class TimeboxReached(
        val secs: Int,
        val reps: Int,
    )

    /**
     * Return (elapsedTime, reps) if timebox reached, or null.
     * Automatically restarts timebox if expired.
     */
    @LibAnkiAlias("timeboxReached")
    fun timeboxReached(): TimeboxReached? {
        if (sched.timeboxSecs() == 0) {
            // timeboxing disabled
            return null
        }
        val elapsed = TimeManager.time.intTime() - startTime
        val limit = sched.timeboxSecs()
        return if (elapsed > limit) {
            TimeboxReached(
                limit,
                sched.numberOfAnswersRecorded - startReps,
            ).also {
                startTimebox()
            }
        } else {
            null
        }
    }
}

@NotInLibAnki
fun EmptyCardsReport.emptyCids(): List<CardId> = notesList.flatMap { it.cardIdsList }

/**
 * @return [File] referencing the media folder (`collection.media`)
 *
 * @throws UnsupportedOperationException if the collection is in-memory
 */
fun Collection.requireMediaFolder() = collectionFiles.requireMediaFolder()

/**
 * [File] referencing the media folder (`collection.media`)
 *
 * (testing) `null` if the collection is in-memory
 */
val Collection.mediaFolder: File? get() = collectionFiles.mediaFolder
