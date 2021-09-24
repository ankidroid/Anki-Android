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

@file:Suppress("FunctionName")

package com.ichi2.libanki.backend

import BackendProto.Backend
import android.content.res.Resources
import com.ichi2.libanki.NoteType
import com.ichi2.libanki.backend.BackendUtils.from_json_bytes
import com.ichi2.libanki.backend.BackendUtils.to_json_bytes
import net.ankiweb.rsdroid.BackendV1
import net.ankiweb.rsdroid.RustCleanup
import java.util.*

private typealias ntid = Long

class NoteTypeNameID(val name: String, val id: ntid)
class NoteTypeNameIDUseCount(val id: Long, val name: String, val useCount: UInt)
class BackendNote(val fields: MutableList<String>)

interface ModelsBackend {
    fun get_notetype_names(): Sequence<NoteTypeNameID>
    fun get_notetype_names_and_counts(): Sequence<NoteTypeNameIDUseCount>
    fun get_notetype_legacy(id: Long): NoteType
    fun get_notetype_id_by_name(name: String): Optional<Long>
    fun get_stock_notetype_legacy(): NoteType
    fun cloze_numbers_in_note(flds: List<String>): List<Int>
    fun remove_notetype(id: ntid)
    fun add_or_update_notetype(model: NoteType, preserve_usn_and_mtime: Boolean): Long
    @RustCleanup("This should be in col")
    fun after_note_updates(nids: List<Long>, mark_modified: Boolean, generate_cards: Boolean = true)
    @RustCleanup("This should be in col")
    /** "You probably want .remove_notes_by_card() instead." */
    fun remove_cards_and_orphaned_notes(card_ids: List<Long>)
}

@Suppress("unused")
class ModelsBackendImpl(private val backend: BackendV1) : ModelsBackend {
    override fun get_notetype_names(): Sequence<NoteTypeNameID> {
        return backend.notetypeNames.entriesList.map {
            NoteTypeNameID(it.name, it.id)
        }.asSequence()
    }

    override fun get_notetype_names_and_counts(): Sequence<NoteTypeNameIDUseCount> {
        return backend.notetypeNamesAndCounts.entriesList.map {
            NoteTypeNameIDUseCount(it.id, it.name, it.useCount.toUInt())
        }.asSequence()
    }

    override fun get_notetype_legacy(id: Long): NoteType {
        return NoteType(from_json_bytes(backend.getNotetypeLegacy(id)))
    }

    override fun get_notetype_id_by_name(name: String): Optional<Long> {
        return try {
            Optional.of(backend.getNotetypeIDByName(name).ntid)
        } catch (ex: Resources.NotFoundException) {
            Optional.empty()
        }
    }

    override fun get_stock_notetype_legacy(): NoteType {
        val fromJsonBytes = from_json_bytes(backend.getStockNotetypeLegacy(Backend.StockNoteType.STOCK_NOTE_TYPE_BASIC))
        return NoteType(fromJsonBytes)
    }

    override fun cloze_numbers_in_note(flds: List<String>): List<Int> {
        val note = Backend.Note.newBuilder().addAllFields(flds).build()
        return backend.clozeNumbersInNote(note).numbersList
    }

    override fun remove_notetype(id: ntid) {
        backend.removeNotetype(id)
    }

    override fun add_or_update_notetype(model: NoteType, preserve_usn_and_mtime: Boolean): ntid {
        val toJsonBytes = to_json_bytes(model)
        return backend.addOrUpdateNotetype(toJsonBytes, preserve_usn_and_mtime).ntid
    }

    override fun after_note_updates(nids: List<Long>, mark_modified: Boolean, generate_cards: Boolean) {
        backend.afterNoteUpdates(nids, mark_modified, generate_cards)
    }

    override fun remove_cards_and_orphaned_notes(card_ids: List<Long>) {
        backend.removeCards(card_ids)
    }
}
