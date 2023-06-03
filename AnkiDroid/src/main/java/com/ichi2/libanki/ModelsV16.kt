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
 *
 * This file incorporates code under the following license
 * https://github.com/ankitects/anki/blob/c4db4bd2913234d077aa289543da6405a62f53dc/pylib/anki/models.py
 *
 *    Copyright: Ankitects Pty Ltd and contributors
 *    License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
 *
 */

@file:Suppress("LiftReturnOrAssignment", "FunctionName", "unused")

package com.ichi2.libanki

import anki.collection.OpChanges
import anki.collection.OpChangesWithId
import anki.notetypes.Notetype
import anki.notetypes.NotetypeNameId
import anki.notetypes.NotetypeNameIdUseCount
import anki.notetypes.StockNotetype
import com.google.protobuf.ByteString
import com.ichi2.anki.R
import com.ichi2.libanki.Consts.MODEL_CLOZE
import com.ichi2.libanki.Utils.checksum
import com.ichi2.libanki.backend.BackendUtils
import com.ichi2.libanki.backend.BackendUtils.to_json_bytes
import com.ichi2.libanki.utils.*
import com.ichi2.utils.jsonObjectIterable
import net.ankiweb.rsdroid.RustCleanup
import net.ankiweb.rsdroid.exceptions.BackendNotFoundException
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber

class NoteTypeNameID(val name: String, val id: NoteTypeId)
class NoteTypeNameIDUseCount(val id: Long, val name: String, val useCount: UInt)
class BackendNote(val fields: MutableList<String>)

private typealias int = Long

// # types
private typealias Field = JSONObject // Dict<str, Any>
private typealias Template = JSONObject // Dict<str, Union3<str, int, Unit>>

typealias NoteType = Model

/** Python method
 * https://docs.python.org/3/library/stdtypes.html?highlight=dict#dict.update
 *
 * Update the dictionary with the provided key/value pairs, overwriting existing keys
 */
fun NoteType.update(updateFrom: NoteType) {
    for (k in updateFrom.keys()) {
        put(k, updateFrom[k])
    }
}

fun NoteType.deepcopy(): NoteType = NoteType(this.deepClone())

var NoteType.flds: JSONArray
    get() = getJSONArray("flds")
    set(value) {
        put("flds", value)
    }

var NoteType.tmpls: JSONArray
    get() = getJSONArray("tmpls")
    set(value) {
        put("tmpls", value)
    }

var NoteType.id: int
    get() = getLong("id")
    set(value) {
        put("id", value)
    }

var NoteType.name: String
    get() = getString("name")
    set(value) {
        put("name", value)
    }

/** Integer specifying which field is used for sorting in the browser */
var NoteType.sortf: Int
    get() = getInt("sortf")
    set(value) {
        put("sortf", value)
    }

// TODO: Not constrained
@Consts.MODEL_TYPE
var NoteType.type: Int
    get() = getInt("type")
    set(value) {
        put("type", value)
    }

class ModelsV16 : ModelManager() {
    /*
    # Saving/loading registry
    #############################################################
     */

    private var _cache: Dict<int, NoteType> = Dict()

    init {
        _cache = Dict()
    }

    /** Save changes made to provided note type. */
    @RustCleanup("templates is not needed, m should be non-null")
    override fun save(col: Collection, m: NoteType?, @Suppress("UNUSED_PARAMETER") templates: Boolean) {
        if (m == null) {
            Timber.w("a null model is no longer supported - data is automatically flushed")
            return
        }
        // legacy code expects preserve_usn=false behaviour, but that
        // causes a backup entry to be created, which invalidates the
        // v2 review history. So we manually update the usn/mtime here
        m.put("mod", TimeManager.time.intTime())
        m.put("usn", col.usn())
        update(col, m, preserve_usn_and_mtime = true)
    }

    @RustCleanup("not required - java only")
    override fun load(@Suppress("UNUSED_PARAMETER") json: String) {
    }

    /** legacy */
    override fun flush(col: Collection) {
        // intentionally left blank
    }

    @RustCleanup("not necessary in V16")
    override fun ensureNotEmpty(col: Collection): Boolean {
        Timber.w("ensureNotEmpty is not necessary in V16")
        return false
    }

    /*
    # Caching
    #############################################################
    # A lot of existing code expects to be able to quickly and
    # frequently obtain access to an entire notetype, so we currently
    # need to cache responses from the backend. Please do not
    # access the cache directly!
     */

    private fun _update_cache(nt: NoteType) {
        _cache[nt.id] = nt
    }

    private fun _remove_from_cache(ntid: int) {
        _cache.remove(ntid)
    }

    private fun _get_cached(ntid: int): NoteType? {
        return _cache.get(ntid)
    }

    fun _clear_cache() {
        _cache = Dict()
    }

    /*
    # Listing note types
    #############################################################
     */

    fun all_names_and_ids(col: Collection): Sequence<NoteTypeNameID> {
        return col.backend.getNotetypeNames().map {
            NoteTypeNameID(it.name, it.id)
        }.asSequence()
    }

    fun all_use_counts(col: Collection): Sequence<NoteTypeNameIDUseCount> {
        return col.backend.getNotetypeNamesAndCounts().map {
            NoteTypeNameIDUseCount(it.id, it.name, it.useCount.toUInt())
        }.asSequence()
    }

    /* legacy */

    override fun allNames(col: Collection): List<String> {
        return all_names_and_ids(col).map {
            it.name
        }.toMutableList()
    }

    override fun ids(col: Collection): Set<int> {
        return all_names_and_ids(col).map { it.id }.toSet()
    }

    // only used by importing code
    override fun have(col: Collection, id: int): bool = all_names_and_ids(col).any { it.id == id }

    /*
    # Current note type
    #############################################################
     */

    /** Get current model.*/
    @RustCleanup("Check the -1 fallback - copied from the Java")
    override fun current(col: Collection, forDeck: bool): NoteType {
        var m = get(col, col.decks.current().getLongOrNull("mid"))
        if (!forDeck || m == null) {
            m = get(col, col.get_config("curModel", -1L)!!)
        }
        if (m != null) {
            return m
        }
        return get(col, all_names_and_ids(col).first().id)!!
    }

    override fun setCurrent(col: Collection, m: NoteType) {
        col.set_config("curModel", m.id)
    }

    /*
    # Retrieving and creating models
    #############################################################
     */

    fun id_for_name(col: Collection, name: str): Long? {
        return try {
            col.backend.getNotetypeIdByName(name)
        } catch (e: BackendNotFoundException) {
            null
        }
    }

    /** "Get model with ID, or None." */
    override fun get(col: Collection, id: int): NoteType? {
        return get(col, id as int?)
    }

    /** Externally, we do not want to pass in a null id */
    private fun get(col: Collection, id: int?): NoteType? {
        if (id == null) {
            return null
        }
        var nt = _get_cached(id)
        if (nt == null) {
            try {
                nt = NoteType(
                    BackendUtils.from_json_bytes(
                        col.backend.getNotetypeLegacy(id)
                    )
                )
                _update_cache(nt)
            } catch (e: BackendNotFoundException) {
                return null
            }
        }
        return nt
    }

    /** Get all models */
    override fun all(col: Collection): List<NoteType> {
        return all_names_and_ids(col).map { get(col, it.id)!! }.toMutableList()
    }

    /** Get model with NAME. */
    override fun byName(col: Collection, name: str): NoteType? {
        val id = id_for_name(col, name)
        return id?.let { get(col, it) }
    }

    @RustCleanup("When we're kotlin only, rename to 'new', name existed due to Java compat")
    override fun newModel(col: Collection, name: str): NoteType = new(col, name)

    /** Create a new non-cloze model, and return it. */
    fun new(col: Collection, name: str): NoteType {
        // caller should call save() after modifying
        val nt = newBasicNotetype(col)
        nt.flds = JSONArray()
        nt.tmpls = JSONArray()
        nt.name = name
        return nt
    }

    private fun newBasicNotetype(col: Collection): NoteType {
        return NoteType(
            BackendUtils.from_json_bytes(
                col.backend.getStockNotetypeLegacy(StockNotetype.Kind.BASIC)
            )
        )
    }

    /** Delete model, and all its cards/notes. */
    override fun rem(col: Collection, m: NoteType) {
        remove(col, m.id)
    }

    fun remove_all_notetypes(col: Collection) {
        for (nt in all_names_and_ids(col)) {
            _remove_from_cache(nt.id)
            col.backend.removeNotetype(nt.id)
        }
    }

    /** Modifies schema. */
    fun remove(col: Collection, id: int) {
        _remove_from_cache(id)
        col.backend.removeNotetype(id)
    }

    override fun add(col: Collection, m: NoteType) {
        save(col, m)
    }

    fun ensureNameUnique(col: Collection, m: NoteType) {
        val existingId = id_for_name(col, m.name)
        existingId?.let {
            if (it != m.id) {
                // Python uses a float time, but it doesn't really matter, the goal is just a random id.
                m.name += "-" + checksum(TimeManager.time.intTimeMS().toString()).substring(0, 5)
            }
        }
    }

    /** Add or update an existing model. Use .save() instead. */
    override fun update(col: Collection, m: NoteType, preserve_usn_and_mtime: Boolean) {
        _remove_from_cache(m.id)
        ensureNameUnique(col, m)
        m.id = col.backend.addOrUpdateNotetype(
            json = to_json_bytes(m),
            preserveUsnAndMtime = preserve_usn_and_mtime,
            skipChecks = preserve_usn_and_mtime
        )
        setCurrent(col, m)
        _mutate_after_write(col, m)
    }

    private fun _mutate_after_write(col: Collection, nt: NoteType) {
        // existing code expects the note type to be mutated to reflect
        // the changes made when adding, such as ordinal assignment :-(
        val updated = get(col, nt.id)!!
        nt.update(updated)
    }

    /*
    # Tools
    ##################################################
     */

    @RustCleanup("use nids(int)")
    override fun nids(col: Collection, m: Model): List<int> = nids(col, m.getLong("id"))

    /** Note ids for M. */
    fun nids(col: Collection, ntid: int): List<int> {
        return col.db.queryLongList("select id from notes where mid = ?", ntid)
    }

    /** Number of note using M. */
    override fun useCount(col: Collection, m: NoteType): Int {
        return col.db.queryLongScalar("select count() from notes where mid = ?", m.id).toInt()
    }

    @RustCleanup("not in libAnki any more - may not be needed")
    override fun tmplUseCount(col: Collection, m: NoteType, ord: Int): Int {
        return col.db.queryScalar(
            "select count() from cards, notes where cards.nid = notes.id and notes.mid = ? and cards.ord = ?",
            m.id,
            ord
        )
    }

    /*
    # Copying
    ##################################################
     */

    /** Copy, save and return. */
    override fun copy(col: Collection, m: NoteType): NoteType {
        val m2 = m.deepcopy()
        m2.name = col.context.getString(R.string.copy_note_type_name, m2.name)
        m2.id = 0
        add(col, m2)
        return m2
    }

    /*
    # Fields
    ##################################################
     */

    /** Mapping of field name : (ord, field). */
    fun fieldMap(m: NoteType): Map<str, Tuple<int, Field>> {
        return m.flds.jsonObjectIterable().map { f ->
            Pair(f.getString("name"), Pair(f.getLong("ord"), f))
        }.toMap()
    }

    fun fieldNames(m: NoteType): List<str> {
        return m.flds.jsonObjectIterable().map { it.getString("name") }.toMutableList()
    }

    override fun sortIdx(m: NoteType): Int {
        return m.sortf
    }

    /*
    # Adding & changing fields
    ##################################################
     */

    @RustCleanup("Check JSONObject.NULL")
    fun new_field(col: Collection, name: str): Field {
        val nt = newBasicNotetype(col)
        val field = nt.flds.getJSONObject(0)
        field.put("name", name)
        field.put("ord", JSONObject.NULL)
        return field
    }

    /** Modifies schema */
    fun add_field(m: NoteType, field: Field) {
        m.flds.append(field)
    }

    /** Modifies schema. */
    fun remove_field(m: NoteType, field: Field) {
        m.flds.remove(field)
    }

    /** Modifies schema. */
    fun reposition_field(m: NoteType, field: Field, idx: Int) {
        val oldidx = m.flds.index(field).get()
        if (oldidx == idx) {
            return
        }

        m.flds.remove(field)
        m.flds.insert(idx, field)
    }

    fun rename_field(m: NoteType, field: Field, new_name: str) {
        assert(m.flds.jsonObjectIterable().contains(field))
        field["name"] = new_name
    }

    /** name exists for compat with java */
    @RustCleanup("remove - use set_sort_index")
    override fun setSortIdx(col: Collection, m: NoteType, idx: Int) = set_sort_index(m, idx)

    /** Modifies schema. */
    fun set_sort_index(nt: NoteType, idx: Int) {
        assert(0 <= idx && idx < len(nt.flds))
        nt.sortf = idx
    }

    /*
     legacy
     */

    override fun newField(col: Collection, name: str) = new_field(col, name)

    @RustCleanup("remove")
    override fun beforeUpload(col: Collection) {
        // intentionally blank - not needed
    }

    @RustCleanup("Unused ")
    override fun setChanged() {
        // intentionally blank - not needed
    }

    @RustCleanup("Only exists for interface compatibility")
    override fun getModels(col: Collection): Map<Long, NoteType> = all(col).map { Pair(it.id, it) }.toMap()

    override fun addField(col: Collection, m: NoteType, field: Field) {
        add_field(m, field)
        if (m.id != 0L) {
            save(col, m)
        }
    }

    override fun remField(col: Collection, m: NoteType, field: Field) {
        remove_field(m, field)
        save(col, m)
    }

    override fun moveField(col: Collection, m: NoteType, field: Field, idx: Int) {
        reposition_field(m, field, idx)
        save(col, m)
    }

    override fun renameField(col: Collection, m: NoteType, field: Field, newName: str) {
        rename_field(m, field, newName)
        save(col, m)
    }

    /*
    # Adding & changing templates
    ##################################################
     */

    @RustCleanup("Check JSONObject.NULL")
    fun new_template(col: Collection, name: str): Template {
        val nt = newBasicNotetype(col)
        val template = nt.tmpls.getJSONObject(0)
        template["name"] = name
        template["qfmt"] = ""
        template["afmt"] = ""
        template.put("ord", JSONObject.NULL)
        return template
    }

    /** Modifies schema. */
    fun add_template(m: NoteType, template: Template) {
        m.tmpls.append(template)
    }

    /** Modifies schema */
    fun remove_template(m: NoteType, template: Template) {
        assert(len(m.tmpls) > 1)
        m.tmpls.remove(template)
    }

    /** Modifies schema. */
    fun reposition_template(m: NoteType, template: Template, idx: Int) {
        val oldidx = m.tmpls.index(template).get()
        if (oldidx == idx) {
            return
        }

        m.tmpls.remove(template)
        m.tmpls.insert(idx, template)
    }

    /** legacy */
    fun newTemplate(col: Collection, name: str): Template = new_template(col, name)

    override fun addTemplate(col: Collection, m: NoteType, template: Template) {
        add_template(m, template)
        if (m.id != 0L) {
            save(col, m)
        }
    }

    override fun remTemplate(col: Collection, m: NoteType, template: Template) {
        remove_template(m, template)
        save(col, m)
    }

    override fun moveTemplate(col: Collection, m: NoteType, template: Template, idx: Int) {
        reposition_template(m, template, idx)
        save(col, m)
    }

    /*
    # Model changing
    ##########################################################################
    # - maps are ord->ord, and there should not be duplicate targets
    # - newModel should be same as m if model is not changing
     */

    /** A compatibility wrapper that converts legacy-style arguments and
     * feeds them into a backend request, so that AnkiDroid's editor-bound
     * notetype changing can be used. Changing the notetype via the editor is
     * not ideal: it doesn't let users re-order fields in a 2 element note,
     * doesn't provide a warning to users about fields/cards that will be removed,
     * and doesn't allow mapping one source field to multiple target fields. In
     * the future, it may be worth removing this routine and exposing the
     * change_notetype.html page to the user instead. The editor could remove
     * the field-reordering code, and when saving a note where the notetype
     * has been changed, the separate change_notetype screen could be shown.
     * It would also be a good idea to expose change notetype as a bulk action
     * in the browsing screen, so that the user can change the notetype of
     * multiple notes at once.
     * */
    override fun change(
        col: Collection,
        m: NoteType,
        nid: NoteId,
        newModel: NoteType,
        fmap: Map<Int, Int?>,
        cmap: Map<Int, Int?>
    ) {
        col.modSchema()
        val fieldMap = convertLegacyMap(fmap, newModel.fieldsNames.size)
        val templateMap =
            if (cmap.isEmpty() || m.type == MODEL_CLOZE || newModel.type == MODEL_CLOZE) {
                listOf()
            } else {
                convertLegacyMap(cmap, newModel.templatesNames.size)
            }
        col.backend.changeNotetype(
            noteIds = listOf(nid),
            newFields = fieldMap,
            newTemplates = templateMap,
            oldNotetypeId = m.id,
            newNotetypeId = newModel.id,
            currentSchema = col.scm,
            oldNotetypeName = m.name
        )
    }

    /** Convert old->new map to list of old indexes/nulls */
    private fun convertLegacyMap(map: Map<Int, Int?>, newSize: Int): Iterable<Int> {
        val newToOld = map.entries.filter({ it.value != null }).associate { (k, v) -> v to k }
        val output = mutableListOf<Int>()
        for (idx in 0 until newSize) {
            output.append(newToOld[idx] ?: -1)
        }
        return output
    }

    /*
    # Schema hash
    ##########################################################################
     */

    /** Return a hash of the schema, to see if models are compatible. */
    override fun scmhash(m: NoteType): str {
        var s = ""
        for (f in m.flds.jsonObjectIterable()) {
            s += f["name"]
        }
        for (t in m.tmpls.jsonObjectIterable()) {
            s += t["name"]
        }
        return checksum(s)
    }

    /*
    # Cloze
    ##########################################################################
     */

    @Suppress("UNUSED_PARAMETER")
    fun _availClozeOrds(
        m: NoteType,
        flds: str,
        allowEmpty: bool = true
    ): kotlin.collections.Collection<Int> {
        TODO("should no longer be needed")
//        print("_availClozeOrds() is deprecated; use note.cloze_numbers_in_fields()")
//        return modelsBackend.cloze_numbers_in_note(listOf(flds))
    }

    /*
     * Other stuff NOT IN LIBANKI
     * ***********************************************************************************************
     */

    override fun count(col: Collection): Int {
        return all_names_and_ids(col).count()
    }

    override fun _addTemplate(col: Collection, m: Model, template: JSONObject) {
        addTemplate(col, m, template)
    }

    override fun _addField(col: Collection, m: Model, field: JSONObject) {
        addField(col, m, field)
    }
}

/**
 * @return null if the key doesn't exist, or the value is not a long. The long value of the key
 * otherwise
 *
 * This better approximates `JSON.get` in the Python
 */
private fun Deck.getLongOrNull(key: String): int? {
    if (!has(key)) {
        return null
    }
    try {
        return getLong(key)
    } catch (ex: Exception) {
        return null
    }
}

// These take and return bytes that the frontend TypeScript code will encode/decode.
fun CollectionV16.getNotetypeNamesRaw(input: ByteArray): ByteArray {
    return backend.getNotetypeNamesRaw(input)
}

fun CollectionV16.getFieldNamesRaw(input: ByteArray): ByteArray {
    return backend.getFieldNamesRaw(input)
}

fun CollectionV16.updateNotetype(updatedNotetype: Notetype): OpChanges {
    return backend.updateNotetype(input = updatedNotetype)
}

fun CollectionV16.removeNotetype(notetypeId: Long): OpChanges {
    return backend.removeNotetype(ntid = notetypeId)
}

fun CollectionV16.addNotetype(newNotetype: Notetype): OpChangesWithId {
    return backend.addNotetype(input = newNotetype)
}

fun CollectionV16.getNotetypeNameIdUseCount(): List<NotetypeNameIdUseCount> {
    return backend.getNotetypeNamesAndCounts()
}

fun CollectionV16.getNotetype(notetypeId: Long): Notetype {
    return backend.getNotetype(ntid = notetypeId)
}

fun CollectionV16.getNotetypeNames(): List<NotetypeNameId> {
    return backend.getNotetypeNames()
}

fun CollectionV16.addNotetypeLegacy(json: ByteString): OpChangesWithId {
    return backend.addNotetypeLegacy(json = json)
}

fun CollectionV16.getStockNotetypeLegacy(kind: StockNotetype.Kind): ByteString {
    return backend.getStockNotetypeLegacy(kind = kind)
}
