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

import com.ichi2.anki.R
import com.ichi2.anki.exception.ConfirmModSchemaException
import com.ichi2.libanki.Consts.MODEL_CLOZE
import com.ichi2.libanki.Utils.*
import com.ichi2.libanki.backend.ModelsBackend
import com.ichi2.libanki.backend.ModelsBackendImpl
import com.ichi2.libanki.backend.NoteTypeNameID
import com.ichi2.libanki.backend.NoteTypeNameIDUseCount
import com.ichi2.libanki.utils.*
import com.ichi2.utils.JSONArray
import com.ichi2.utils.JSONObject
import net.ankiweb.rsdroid.BackendV1
import net.ankiweb.rsdroid.RustCleanup
import net.ankiweb.rsdroid.exceptions.BackendNotFoundException
import timber.log.Timber
import java.util.*
import kotlin.collections.HashMap

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

fun NoteType.deepcopy(): NoteType = NoteType(JSONObject(this))

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

class ModelsV16(col: Collection, backend: BackendV1) : ModelManager(col) {
    /*
    # Saving/loading registry
    #############################################################
     */

    private var _cache: Dict<int, NoteType> = Dict()
    private val modelsBackend: ModelsBackend = ModelsBackendImpl(backend)

    init {
        _cache = Dict()
    }

    /** Save changes made to provided note type. */
    @RustCleanup("templates is not needed, m should be non-null")
    override fun save(m: NoteType?, @Suppress("UNUSED_PARAMETER") templates: Boolean) {
        if (m == null) {
            Timber.w("a null model is no longer supported - data is automatically flushed")
            return
        }
        update(m, preserve_usn_and_mtime = false)
    }

    @RustCleanup("not required - java only")
    override fun load(@Suppress("UNUSED_PARAMETER") json: String) {
    }

    /** legacy */
    override fun flush() {
        // intentionally left blank
    }

    @RustCleanup("not necessary in V16")
    override fun ensureNotEmpty(): Boolean {
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

    private fun _get_cached(ntid: int): Optional<NoteType> {
        return _cache.getOptional(ntid)
    }

    fun _clear_cache() {
        _cache = Dict()
    }

    /*
    # Listing note types
    #############################################################
     */

    fun all_names_and_ids(): Sequence<NoteTypeNameID> {
        return modelsBackend.get_notetype_names()
    }

    fun all_use_counts(): Sequence<NoteTypeNameIDUseCount> {
        return modelsBackend.get_notetype_names_and_counts()
    }

    /* legacy */

    fun allNames(): List<str> {
        return all_names_and_ids().map { it.name }.toMutableList()
    }

    override fun ids(): Set<int> {
        return all_names_and_ids().map { it.id }.toSet()
    }

    // only used by importing code
    override fun have(id: int): bool = all_names_and_ids().any { it.id == id }

    /*
    # Current note type
    #############################################################
     */

    /** Get current model.*/
    @RustCleanup("Check the -1 fallback - copied from the Java")
    override fun current(forDeck: bool): NoteType {
        var m = get(col.decks.current().getLongOrNull("mid"))
        if (!forDeck || m == null) {
            m = get(col.get_config("curModel", -1L)!!)
        }
        if (m != null) {
            return m
        }
        return get(all_names_and_ids().first().id)!!
    }

    override fun setCurrent(m: NoteType) {
        col.set_config("curModel", m.id)
    }

    /*
    # Retrieving and creating models
    #############################################################
     */

    fun id_for_name(name: str): Optional<int> {
        try {
            return modelsBackend.get_notetype_id_by_name(name)
        } catch (e: BackendNotFoundException) {
            return Optional.empty()
        }
    }

    /** "Get model with ID, or None." */
    override fun get(id: int): NoteType? {
        return get(id as int?)
    }

    /** Externally, we do not want to pass in a null id */
    private fun get(id: int?): NoteType? {
        if (id == null) {
            return null
        }
        var nt = _get_cached(id)
        if (!nt.isPresent) {
            try {
                nt = Optional.of(modelsBackend.get_notetype_legacy(id))
                _update_cache(nt.get())
            } catch (e: BackendNotFoundException) {
                return null
            }
        }
        return nt.orElse(null)
    }

    /** Get all models */
    override fun all(): List<NoteType> {
        return all_names_and_ids().map { get(it.id)!! }.toMutableList()
    }

    /** Get model with NAME. */
    override fun byName(name: str): NoteType? {
        val id = id_for_name(name)
        if (id.isPresent) {
            return get(id.get())
        } else {
            return null
        }
    }

    @RustCleanup("When we're kotlin only, rename to 'new', name existed due to Java compat")
    override fun newModel(name: str): NoteType = new(name)

    /** Create a new model, and return it. */
    fun new(name: str): NoteType {
        // caller should call save() after modifying
        val nt = modelsBackend.get_stock_notetype_legacy()
        nt.flds = JSONArray()
        nt.tmpls = JSONArray()
        nt.name = name
        return nt
    }

    /** Delete model, and all its cards/notes. */
    override fun rem(m: NoteType) {
        remove(m.id)
    }

    fun remove_all_notetypes() {
        for (nt in all_names_and_ids()) {
            _remove_from_cache(nt.id)
            modelsBackend.remove_notetype(nt.id)
        }
    }

    /** Modifies schema. */
    fun remove(id: int) {
        _remove_from_cache(id)
        modelsBackend.remove_notetype(id)
    }

    override fun add(m: NoteType) {
        save(m)
    }

    @RustCleanup("Python uses .time()")
    fun ensureNameUnique(m: NoteType) {
        val existing_id = id_for_name(m.name)
        if (existing_id.isPresent && existing_id.get() != m.id) {
            /*
            >>> pp(anki.utils.checksum(str(time.time()))[:5])   = '07a29'
            >>> pp(anki.utils.checksum(str(time.time())))       = '07a2939b5546263476ba9c7eca7489fa95af4a18'
             */
            m.name += "-" + checksum(col.time.intTimeMS().toString()).substring(0, 5)
        }
    }

    /** Add or update an existing model. Use .save() instead. */
    override fun update(m: NoteType, preserve_usn_and_mtime: Boolean) {
        _remove_from_cache(m.id)
        ensureNameUnique(m)
        m.id = modelsBackend.add_or_update_notetype(model = m, preserve_usn_and_mtime = preserve_usn_and_mtime)
        setCurrent(m)
        _mutate_after_write(m)
    }

    private fun _mutate_after_write(nt: NoteType) {
        // existing code expects the note type to be mutated to reflect
        // the changes made when adding, such as ordinal assignment :-(
        val updated = get(nt.id)!!
        nt.update(updated)
    }

    /*
    # Tools
    ##################################################
     */

    @RustCleanup("use nids(int)")
    override fun nids(m: Model): List<int> = nids(m.getLong("id"))

    /** Note ids for M. */
    fun nids(ntid: int): List<int> {
        return col.db.queryLongList("select id from notes where mid = ?", ntid)
    }

    /** Number of note using M. */
    override fun useCount(m: NoteType): Int {
        return col.db.queryLongScalar("select count() from notes where mid = ?", m.id).toInt()
    }

    @RustCleanup("not in libAnki any more - may not be needed")
    override fun tmplUseCount(m: NoteType, ord: Int): Int {
        return col.db.queryScalar("select count() from cards, notes where cards.nid = notes.id and notes.mid = ? and cards.ord = ?", m.id, ord)
    }

    /*
    # Copying
    ##################################################
     */

    /** Copy, save and return. */
    override fun copy(m: NoteType): NoteType {
        val m2 = m.deepcopy()
        m2.name = col.context.getString(R.string.copy_note_type_name, m2.name)
        m2.id = 0
        add(m2)
        return m2
    }

    /*
    # Fields
    ##################################################
     */

    /** Mapping of field name : (ord, field). */
    fun fieldMap(m: NoteType): Map<str, Tuple<int, Field>> {
        return m.flds.jsonObjectIterable().map {
            f ->
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
    fun new_field(name: str): Field {
        val nt = modelsBackend.get_stock_notetype_legacy()
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
    override fun setSortIdx(m: NoteType, idx: Int) = set_sort_index(m, idx)

    /** Modifies schema. */
    fun set_sort_index(nt: NoteType, idx: Int) {

        assert(0 <= idx && idx < len(nt.flds))
        nt.sortf = idx
    }

    /*
     legacy
     */

    override fun newField(name: str) = new_field(name)

    @RustCleanup("remove")
    override fun beforeUpload() {
        // intentionally blank - not needed
    }

    @RustCleanup("Unused ")
    override fun setChanged() {
        // intentionally blank - not needed
    }

    @RustCleanup("Only exists for interface compatibility")
    override fun getModels(): Map<Long, NoteType> = all().map { Pair(it.id, it) }.toMap()

    override fun addField(m: NoteType, field: Field) {
        add_field(m, field)
        if (m.id != 0L) {
            save(m)
        }
    }

    override fun remField(m: NoteType, field: Field) {
        remove_field(m, field)
        save(m)
    }

    override fun moveField(m: NoteType, field: Field, idx: Int) {
        reposition_field(m, field, idx)
        save(m)
    }

    override fun renameField(m: NoteType, field: Field, newName: str) {
        rename_field(m, field, newName)
        save(m)
    }

    /*
    # Adding & changing templates
    ##################################################
     */

    @RustCleanup("Check JSONObject.NULL")
    fun new_template(name: str): Template {
        val nt = modelsBackend.get_stock_notetype_legacy()
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
    fun newTemplate(name: str): Template = new_template(name)

    override fun addTemplate(m: NoteType, template: Template) {
        add_template(m, template)
        if (m.id != 0L) {
            save(m)
        }
    }

    override fun remTemplate(m: NoteType, template: Template) {
        remove_template(m, template)
        save(m)
    }

    override fun moveTemplate(m: NoteType, template: Template, idx: Int) {
        reposition_template(m, template, idx)
        save(m)
    }

    override fun change(m: NoteType, nid: Long, newModel: NoteType, fmap: Map<Int, Int>?, cmap: Map<Int, Int>?) {
        change(m, listOf(nid), newModel, Optional.ofNullable(fmap), Optional.ofNullable(cmap))
    }

    fun template_use_count(ntid: int, ord: int): int {
        return col.db.queryLongScalar(
            """
select count() from cards, notes where cards.nid = notes.id
and notes.mid = ? and cards.ord = ?""",
            ntid,
            ord,
        )
    }

    /*
    # Model changing
    ##########################################################################
    # - maps are ord->ord, and there should not be duplicate targets
    # - newModel should be self if model is not changing
     */

    @Throws(ConfirmModSchemaException::class)
    fun change(
        m: NoteType,
        nids: List<int>,
        newModel: NoteType,
        fmap: Optional<Map<Int, Int?>>,
        cmap: Optional<Map<Int, Int?>>,
    ) {
        col.modSchema()
        assert(newModel.id == m.id || (fmap.isPresent && cmap.isPresent))
        if (fmap.isPresent) {
            _changeNotes(nids, newModel, fmap.get())
        }
        if (cmap.isPresent) {
            _changeCards(nids, m, newModel, cmap.get())
        }
        modelsBackend.after_note_updates(nids, mark_modified = true)
    }

    private fun _changeNotes(nids: List<int>, newModel: NoteType, map: Map<Int, Int?>) {
        val d = mutableListOf<Array<Any>>()

        val cursor = col.db.query("select id, flds from notes where id in " + ids2str(nids))
        cursor.use {
            while (cursor.moveToNext()) {
                val nid = cursor.getLong(0)
                val fldsString = cursor.getString(1)

                var flds = splitFields(fldsString)
                // Kotlin: we can't expand a list via index, so use a HashMap
                val newflds = HashMap<Int, str>()
                for ((old, new) in list(map.entries)) {
                    if (new == null) {
                        continue
                    }
                    newflds[new] = flds[old]
                }
                flds = Array(flds.size) { "" }
                newflds.forEach {
                    (i, fld) ->
                    flds[i] = fld
                }
                val fldsAsString = joinFields(flds)
                d.append(arrayOf(fldsAsString, newModel.id, col.time.intTime(), col.usn(), nid,))
            }
        }
        col.db.executeMany("update notes set flds=?,mid=?,mod=?,usn=? where id = ?", d)
    }

    private fun _changeCards(
        nids: List<int>,
        oldModel: NoteType,
        newModel: NoteType,
        map: Map<Int, Int?>,
    ) {
        val d = mutableListOf<Array<Any>>()
        val deleted = mutableListOf<Long>()
        val c = col.db.query(
            "select id, ord from cards where nid in " + ids2str(nids)
        )
        c.use {
            while (c.moveToNext()) {
                val cid = c.getLong(0)
                val ord = c.getInt(1)
                // if the src model is a cloze, we ignore the map, as the gui
                // doesn't currently support mapping them
                var new: Int?
                if (oldModel.type == MODEL_CLOZE) {
                    new = ord
                    if (newModel.type != MODEL_CLOZE) {
                        // if we're mapping to a regular note, we need to check if
                        // the destination ord is valid
                        if (len(newModel.tmpls) <= ord) {
                            new = null
                        }
                    }
                } else {
                    // mapping from a regular note, so the map should be valid
                    new = map[ord]
                }
                if (new != null) {
                    d.append(arrayOf(new, col.usn(), col.time.intTime(), cid))
                } else {
                    deleted.append(cid)
                }
            }
        }
        col.db.executeMany("update cards set ord=?,usn=?,mod=? where id=?", d)
        modelsBackend.remove_cards_and_orphaned_notes(deleted)
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
        print("_availClozeOrds() is deprecated; use note.cloze_numbers_in_fields()")
        return modelsBackend.cloze_numbers_in_note(listOf(flds))
    }

    /*
     * Other stuff NOT IN LIBANKI
     * ***********************************************************************************************
     */

    override fun count(): Int {
        return all_names_and_ids().count()
    }

    override fun _addTemplate(m: Model, template: JSONObject) {
        addTemplate(m, template)
    }

    override fun _addField(m: Model, field: JSONObject) {
        addField(m, field)
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
