/*
 *  Copyright (c) 2021 David Allison <davidallisongithub@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.libanki

import android.content.Context
import android.content.res.Resources
import anki.card_rendering.EmptyCardsReport
import anki.collection.OpChanges
import anki.config.ConfigKey
import com.ichi2.libanki.backend.*
import com.ichi2.libanki.backend.model.toBackendNote
import com.ichi2.libanki.backend.model.toProtoBuf
import com.ichi2.libanki.exception.InvalidSearchException
import com.ichi2.libanki.utils.TimeManager
import net.ankiweb.rsdroid.Backend
import net.ankiweb.rsdroid.RustCleanup
import net.ankiweb.rsdroid.exceptions.BackendInvalidInputException
import timber.log.Timber

class CollectionV16(
    context: Context,
    path: String,
    server: Boolean,
    log: Boolean,
    backend: Backend
) : Collection(context, path, server, log, backend) {

    override fun initTags(): TagManager {
        return TagsV16(this)
    }

    override fun initDecks(deckConf: String?): DeckManager {
        return DecksV16(this)
    }

    override fun initModels(): ModelManager {
        return ModelsV16(this)
    }

    override fun initConf(conf: String): ConfigManager {
        return initConfV16()
    }

    private fun initConfV16(): ConfigV16 {
        return ConfigV16(RustConfigBackend(backend))
    }

    override fun initMedia(): BackendMedia {
        return BackendMedia(this, server)
    }

    override val newBackend: CollectionV16
        get() = this

    override val newMedia: BackendMedia
        get() = this.media as BackendMedia

    override val newTags: TagsV16
        get() = this.tags as TagsV16

    override val newModels: ModelsV16
        get() = this.models as ModelsV16

    override val newDecks: DecksV16
        get() = this.decks as DecksV16

    /** True if the V3 scheduled is enabled when schedVer is 2. */
    override var v3Enabled: Boolean
        get() = backend.getConfigBool(ConfigKey.Bool.SCHED_2021)
        set(value) {
            backend.setConfigBool(ConfigKey.Bool.SCHED_2021, value, undoable = false)
            _loadScheduler()
        }

    override fun load() {
        config = initConfV16()
        decks = initDecks(null)
    }

    override fun flush(mod: Long) {
        // no-op
    }

    override var mod: Long = 0
        get() = db.queryLongScalar("select mod from col")

    override var crt: Long = 0
        get() = db.queryLongScalar("select crt from col")

    override var scm: Long = 0
        get() = db.queryLongScalar("select scm from col")

    var lastSync: Long = 0
        get() = db.queryLongScalar("select ls from col")

    override fun usn(): Int {
        return -1
    }

    override fun schemaChanged(): Boolean {
        return scm > lastSync
    }

    /** col.conf is now unused, handled by [ConfigV16] which has a separate table */
    override fun flushConf(): Boolean = false

    @RustCleanup("Remove this once syncing is in the backend")
    override fun onCreate() {
        super.onCreate()
        // set USN to -1, as was previously done in AnkiDroid.
        // This shouldn't cause issues at 0, as it will either be the first sync, or a full sync.
        // but it's useful to match 100% for regression tests

        // we reverse so "Basic" is last and conf."curModel" is correct
        val all = models.all().reversed()
        for (m in all) {
            models.save(m) // equivalent to m.put("usn", -1)
        }
    }

    override fun render_output(
        c: Card,
        reload: Boolean,
        browser: Boolean
    ): TemplateManager.TemplateRenderContext.TemplateRenderOutput {
        return TemplateManager.TemplateRenderContext.from_existing_card(c, browser).render()
    }

    override fun findCards(
        search: String,
        order: SortOrder
    ): List<Long> {
        val adjustedOrder = if (order is SortOrder.UseCollectionOrdering) {
            @Suppress("DEPRECATION")
            SortOrder.BuiltinSortKind(
                get_config("sortType", null as String?) ?: "noteFld",
                get_config("sortBackwards", false) ?: false
            )
        } else {
            order
        }
        val cardIdsList = try {
            backend.searchCards(search, adjustedOrder.toProtoBuf())
        } catch (e: BackendInvalidInputException) {
            throw InvalidSearchException(e)
        }
        return cardIdsList
    }

    override fun findNotes(
        query: String,
        order: SortOrder
    ): List<Long> {
        val adjustedOrder = if (order is SortOrder.UseCollectionOrdering) {
            @Suppress("DEPRECATION")
            SortOrder.BuiltinSortKind(
                get_config("noteSortType", null as String?) ?: "noteFld",
                get_config("browserNoteSortBackwards", false) ?: false
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

    /** Takes raw input from TypeScript frontend and returns suitable translations. */
    fun i18nResourcesRaw(input: ByteArray): ByteArray {
        return backend.i18nResourcesRaw(input = input)
    }

    /** Fixes and optimizes the database. If any errors are encountered, a list of
     * problems is returned. Throws if DB is unreadable. */
    fun fixIntegrity(): List<String> {
        return backend.checkDatabase()
    }

    override fun modSchemaNoCheck() {
        db.execute(
            "update col set scm=?, mod=?",
            TimeManager.time.intTimeMS(),
            TimeManager.time.intTimeMS()
        )
    }

    override fun undoAvailable(): Boolean {
        val status = undoStatus()
        Timber.i("undo: %s, %s", status, super.undoAvailable())
        if (status.undo != null) {
            // any legacy undo state is invalid after a backend op
            clearUndo()
            return true
        }
        // if no backend undo state, try legacy undo state
        return super.undoAvailable()
    }

    override fun undoName(res: Resources): String {
        val status = undoStatus()
        return status.undo ?: super.undoName(res)
    }

    /** Provided for legacy code/tests; new code should call undoNew() directly
     * so that OpChanges can be observed.
     */
    override fun undo(): Card? {
        if (undoStatus().undo != null) {
            undoNew()
            return null
        }
        return super.undo()
    }

    override fun remNotes(ids: LongArray) {
        backend.removeNotes(noteIds = ids.asIterable(), cardIds = listOf())
    }

    override fun setDeck(cids: LongArray, did: Long) {
        backend.setDeck(cardIds = cids.asIterable(), deckId = did)
    }

    /** Save (flush) the note to the DB. Unlike note.flush(), this is undoable. */
    fun updateNote(note: Note) {
        backend.updateNotes(notes = listOf(note.toBackendNote()), skipUndoEntry = false)
    }

    /** Change the flag color of the specified cards. flag=0 removes flag. */
    fun setUserFlagForCards(cids: Iterable<Long>, flag: Int) {
        backend.setFlag(cardIds = cids, flag = flag)
    }

    fun addNote(note: Note, deckId: DeckId): OpChanges {
        val resp = backend.addNote(note.toBackendNote(), deckId)
        note.id = resp.noteId
        return resp.changes
    }

    fun getEmptyCards(): EmptyCardsReport {
        return backend.getEmptyCards()
    }

    override fun removeCardsAndOrphanedNotes(cardIds: Iterable<Long>) {
        backend.removeCards(cardIds)
    }

    /** allowEmpty is ignored in the new schema */
    @RustCleanup("Remove this in favour of addNote() above; call addNote() inside undoableOp()")
    override fun addNote(note: Note, allowEmpty: Models.AllowEmpty): Int {
        addNote(note, note.model().did)
        return note.numberOfCards()
    }
}
