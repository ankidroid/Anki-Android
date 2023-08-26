/*
 * Copyright (c) 2009 Daniel Svärd <daniel.svard@gmail.com>                             *
 * Copyright (c) 2010 Rick Gruber-Riemer <rick@vanosten.net>                            *
 * Copyright (c) 2011 Norbert Nagold <norbert.nagold@gmail.com>                         *
 * Copyright (c) 2011 Kostas Spyropoulos <inigo.aldana@gmail.com>                       *
 * Copyright (c) 2021 David Allison <davidallisongithub@gmail.com>
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

// This file is called models.py in the desktop code for legacy reasons.

@file:Suppress("LiftReturnOrAssignment", "FunctionName")

package com.ichi2.libanki

import anki.collection.OpChanges
import anki.collection.OpChangesWithId
import anki.notetypes.Notetype
import anki.notetypes.NotetypeNameId
import anki.notetypes.NotetypeNameIdUseCount
import anki.notetypes.StockNotetype
import com.google.protobuf.ByteString
import com.ichi2.anki.CrashReportService
import com.ichi2.anki.exception.ConfirmModSchemaException
import com.ichi2.libanki.Consts.MODEL_CLOZE
import com.ichi2.libanki.Utils.checksum
import com.ichi2.libanki.backend.BackendUtils
import com.ichi2.libanki.backend.BackendUtils.to_json_bytes
import com.ichi2.libanki.utils.*
import com.ichi2.utils.Assert
import com.ichi2.utils.HashUtil
import com.ichi2.utils.KotlinCleanup
import com.ichi2.utils.jsonObjectIterable
import net.ankiweb.rsdroid.RustCleanup
import net.ankiweb.rsdroid.exceptions.BackendNotFoundException
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber

class NoteTypeNameID(val name: String, val id: NoteTypeId)

private typealias int = Long

// # types
private typealias Field = JSONObject // Dict<str, Any>
private typealias Template = JSONObject // Dict<str, Union3<str, int, Unit>>

class Notetypes(val col: Collection) {
    /*
    # Saving/loading registry
    #############################################################
     */

    private var _cache: HashMap<int, NotetypeJson> = HashMap()

    init {
        _cache = HashMap()
    }

    /** Save changes made to provided note type. */
    @RustCleanup("templates is not needed, m should be non-null")
    fun save(m: NotetypeJson?, @Suppress("UNUSED_PARAMETER") templates: Boolean = true) {
        if (m == null) {
            Timber.w("a null model is no longer supported - data is automatically flushed")
            return
        }
        // legacy code expects preserve_usn=false behaviour, but that
        // causes a backup entry to be created, which invalidates the
        // v2 review history. So we manually update the usn/mtime here
        m.put("mod", TimeManager.time.intTime())
        m.put("usn", col.usn())
        update(m, preserve_usn_and_mtime = true)
    }

    @RustCleanup("not required - java only")
    fun load(@Suppress("UNUSED_PARAMETER") json: String) {
    }

    /*
    # Caching
    #############################################################
    # A lot of existing code expects to be able to quickly and
    # frequently obtain access to an entire notetype, so we currently
    # need to cache responses from the backend. Please do not
    # access the cache directly!
     */

    private fun _update_cache(nt: NotetypeJson) {
        _cache[nt.id] = nt
    }

    private fun _remove_from_cache(ntid: int) {
        _cache.remove(ntid)
    }

    private fun _get_cached(ntid: int): NotetypeJson? {
        return _cache.get(ntid)
    }

    /*
    # Listing note types
    #############################################################
     */

    fun all_names_and_ids(): Sequence<NoteTypeNameID> {
        return col.backend.getNotetypeNames().map {
            NoteTypeNameID(it.name, it.id)
        }.asSequence()
    }

    /* legacy */

    fun ids(): Set<int> {
        return all_names_and_ids().map { it.id }.toSet()
    }

    // only used by importing code
    fun have(id: int): Boolean = all_names_and_ids().any { it.id == id }

    /*
    # Current note type
    #############################################################
     */

    /** Get current model.*/
    @RustCleanup("Should use defaultsForAdding() instead")
    fun current(forDeck: Boolean = true): NotetypeJson {
        var m = get(col.decks.current().getLongOrNull("mid"))
        if (!forDeck || m == null) {
            m = get(col.config.get("curModel") ?: 1L)
        }
        if (m != null) {
            return m
        }
        return get(all_names_and_ids().first().id)!!
    }

    fun setCurrent(m: NotetypeJson) {
        col.config.set("curModel", m.id)
    }

    /*
    # Retrieving and creating models
    #############################################################
     */

    fun id_for_name(name: String): Long? {
        return try {
            col.backend.getNotetypeIdByName(name)
        } catch (e: BackendNotFoundException) {
            null
        }
    }

    /** "Get model with ID, or None." */
    fun get(id: int): NotetypeJson? {
        return get(id as int?)
    }

    /** Externally, we do not want to pass in a null id */
    private fun get(id: int?): NotetypeJson? {
        if (id == null) {
            return null
        }
        var nt = _get_cached(id)
        if (nt == null) {
            try {
                nt = NotetypeJson(
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
    fun all(): List<NotetypeJson> {
        return all_names_and_ids().map { get(it.id)!! }.toMutableList()
    }

    /** Get model with NAME. */
    fun byName(name: String): NotetypeJson? {
        val id = id_for_name(name)
        return id?.let { get(it) }
    }

    @RustCleanup("When we're kotlin only, rename to 'new', name existed due to Java compat")
    fun newModel(name: String): NotetypeJson = new(name)

    /** Create a new non-cloze model, and return it. */
    fun new(name: String): NotetypeJson {
        // caller should call save() after modifying
        val nt = newBasicNotetype()
        nt.flds = JSONArray()
        nt.tmpls = JSONArray()
        nt.name = name
        return nt
    }

    private fun newBasicNotetype(): NotetypeJson {
        return NotetypeJson(
            BackendUtils.from_json_bytes(
                col.backend.getStockNotetypeLegacy(StockNotetype.Kind.KIND_BASIC)
            )
        )
    }

    /** Delete model, and all its cards/notes. */
    fun rem(m: NotetypeJson) {
        remove(m.id)
    }

    /** Modifies schema. */
    fun remove(id: int) {
        _remove_from_cache(id)
        col.backend.removeNotetype(id)
    }

    fun add(m: NotetypeJson) {
        save(m)
    }

    fun ensureNameUnique(m: NotetypeJson) {
        val existingId = id_for_name(m.name)
        existingId?.let {
            if (it != m.id) {
                // Python uses a float time, but it doesn't really matter, the goal is just a random id.
                m.name += "-" + checksum(TimeManager.time.intTimeMS().toString()).substring(0, 5)
            }
        }
    }

    /** Add or update an existing model. Use .save() instead. */
    fun update(m: NotetypeJson, preserve_usn_and_mtime: Boolean = true) {
        _remove_from_cache(m.id)
        ensureNameUnique(m)
        m.id = col.backend.addOrUpdateNotetype(
            json = to_json_bytes(m),
            preserveUsnAndMtime = preserve_usn_and_mtime,
            skipChecks = preserve_usn_and_mtime
        )
        setCurrent(m)
        _mutate_after_write(m)
    }

    private fun _mutate_after_write(nt: NotetypeJson) {
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
    fun nids(m: com.ichi2.libanki.NotetypeJson): List<int> = nids(m.getLong("id"))

    /** Note ids for M. */
    fun nids(ntid: int): List<int> {
        return col.db.queryLongList("select id from notes where mid = ?", ntid)
    }

    /** Number of note using M. */
    fun useCount(m: NotetypeJson): Int {
        return col.db.queryLongScalar("select count() from notes where mid = ?", m.id).toInt()
    }

    @RustCleanup("not in libAnki any more - may not be needed")
    fun tmplUseCount(m: NotetypeJson, ord: Int): Int {
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

    /** Copy, save and return.
     * This code is currently only used by unit tests. If the  GUI starts to use it, the signature
     * should be updated so that a translated name is passed in. */
    fun copy(m: NotetypeJson): NotetypeJson {
        val m2 = m.deepcopy()
        m2.name = "${m2.name} copy"
        // m2.name = col.context.getString(R.string.copy_note_type_name, m2.name)
        m2.id = 0
        add(m2)
        return m2
    }

    /*
    # Adding & changing fields
    ##################################################
     */

    @RustCleanup("Check JSONObject.NULL")
    fun new_field(name: String): Field {
        val nt = newBasicNotetype()
        val field = nt.flds.getJSONObject(0)
        field.put("name", name)
        field.put("ord", JSONObject.NULL)
        return field
    }

    /** Modifies schema */
    fun add_field(m: NotetypeJson, field: Field) {
        m.flds.append(field)
    }

    /** Modifies schema. */
    fun remove_field(m: NotetypeJson, field: Field) {
        m.flds.remove(field)
    }

    /** Modifies schema. */
    fun reposition_field(m: NotetypeJson, field: Field, idx: Int) {
        val oldidx = m.flds.index(field).get()
        if (oldidx == idx) {
            return
        }

        m.flds.remove(field)
        m.flds.insert(idx, field)
    }

    fun rename_field(m: NotetypeJson, field: Field, new_name: String) {
        assert(m.flds.jsonObjectIterable().contains(field))
        field["name"] = new_name
    }

    /** name exists for compat with java */
    @RustCleanup("remove - use set_sort_index")
    fun setSortIdx(m: NotetypeJson, idx: Int) = set_sort_index(m, idx)

    /** Modifies schema. */
    fun set_sort_index(nt: NotetypeJson, idx: Int) {
        assert(0 <= idx && idx < len(nt.flds))
        nt.sortf = idx
    }

    /*
     legacy
     */

    fun newField(name: String) = new_field(name)

    @RustCleanup("Only exists for interface compatibility")
    fun getModels(): Map<Long, NotetypeJson> = all().map { Pair(it.id, it) }.toMap()

    fun addField(m: NotetypeJson, field: Field) {
        add_field(m, field)
        if (m.id != 0L) {
            save(m)
        }
    }

    fun remField(m: NotetypeJson, field: Field) {
        remove_field(m, field)
        save(m)
    }

    fun moveField(m: NotetypeJson, field: Field, idx: Int) {
        reposition_field(m, field, idx)
        save(m)
    }

    fun renameField(m: NotetypeJson, field: Field, newName: String) {
        rename_field(m, field, newName)
        save(m)
    }

    /**
     * similar to Anki's addField; but thanks to assumption that
     * model is new, it never has to throw
     * [ConfirmModSchemaException]
     */
    @RustCleanup("Since Kotlin doesn't have throws, this may not be needed")
    fun addFieldInNewModel(m: com.ichi2.libanki.NotetypeJson, field: JSONObject) {
        Assert.that(Notetypes.isModelNew(m), "Model was assumed to be new, but is not")
        try {
            _addField(m, field)
        } catch (e: ConfirmModSchemaException) {
            Timber.w(e, "Unexpected mod schema")
            CrashReportService.sendExceptionReport(e, "addFieldInNewModel: Unexpected mod schema")
            throw IllegalStateException("ConfirmModSchemaException should not be thrown", e)
        }
    }

    fun addTemplateInNewModel(m: com.ichi2.libanki.NotetypeJson, template: JSONObject) {
        // similar to addTemplate, but doesn't throw exception;
        // asserting the model is new.
        Assert.that(Notetypes.isModelNew(m), "Model was assumed to be new, but is not")

        try {
            _addTemplate(m, template)
        } catch (e: ConfirmModSchemaException) {
            Timber.w(e, "Unexpected mod schema")
            CrashReportService.sendExceptionReport(e, "addTemplateInNewModel: Unexpected mod schema")
            throw IllegalStateException("ConfirmModSchemaException should not be thrown", e)
        }
    }

    fun addFieldModChanged(m: com.ichi2.libanki.NotetypeJson, field: JSONObject) {
        // similar to Anki's addField; but thanks to assumption that
        // mod is already changed, it never has to throw
        // ConfirmModSchemaException.
        Assert.that(col.schemaChanged(), "Mod was assumed to be already changed, but is not")
        _addField(m, field)
    }

    fun addTemplateModChanged(m: com.ichi2.libanki.NotetypeJson, template: JSONObject) {
        // similar to addTemplate, but doesn't throw exception;
        // asserting the model is new.
        Assert.that(col.schemaChanged(), "Mod was assumed to be already changed, but is not")
        _addTemplate(m, template)
    }

    /*
    # Adding & changing templates
    ##################################################
     */

    @RustCleanup("Check JSONObject.NULL")
    fun new_template(name: String): Template {
        val nt = newBasicNotetype()
        val template = nt.tmpls.getJSONObject(0)
        template["name"] = name
        template["qfmt"] = ""
        template["afmt"] = ""
        template.put("ord", JSONObject.NULL)
        return template
    }

    /** Modifies schema. */
    fun add_template(m: NotetypeJson, template: Template) {
        m.tmpls.append(template)
    }

    /** Modifies schema */
    fun remove_template(m: NotetypeJson, template: Template) {
        assert(len(m.tmpls) > 1)
        m.tmpls.remove(template)
    }

    /** Modifies schema. */
    fun reposition_template(m: NotetypeJson, template: Template, idx: Int) {
        val oldidx = m.tmpls.index(template).get()
        if (oldidx == idx) {
            return
        }

        m.tmpls.remove(template)
        m.tmpls.insert(idx, template)
    }

    /** legacy */
    fun newTemplate(name: String): Template = new_template(name)

    fun addTemplate(m: NotetypeJson, template: Template) {
        add_template(m, template)
        if (m.id != 0L) {
            save(m)
        }
    }

    fun remTemplate(m: NotetypeJson, template: Template) {
        remove_template(m, template)
        save(m)
    }

    fun moveTemplate(m: NotetypeJson, template: Template, idx: Int) {
        reposition_template(m, template, idx)
        save(m)
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
    fun change(
        m: NotetypeJson,
        nid: NoteId,
        newModel: NotetypeJson,
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
    fun scmhash(m: NotetypeJson): String {
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
     * Other stuff NOT IN LIBANKI
     * ***********************************************************************************************
     */

    fun count(): Int {
        return all_names_and_ids().count()
    }

    fun _addTemplate(m: com.ichi2.libanki.NotetypeJson, template: JSONObject) {
        addTemplate(m, template)
    }

    fun _addField(m: com.ichi2.libanki.NotetypeJson, field: JSONObject) {
        addField(m, field)
    }

    /**
     * Extracted from remTemplate so we can test if removing templates is safe without actually removing them
     * This method will either give you all the card ids for the ordinals sent in related to the model sent in *or*
     * it will return null if the result of deleting the ordinals is unsafe because it would leave notes with no cards
     *
     * @param modelId long id of the JSON model
     * @param ords array of ints, each one is the ordinal a the card template in the given model
     * @return null if deleting ords would orphan notes, long[] of related card ids to delete if it is safe
     */
    fun getCardIdsForModel(modelId: NoteTypeId, ords: IntArray): List<Long>? {
        val cardIdsToDeleteSql = "select c2.id from cards c2, notes n2 where c2.nid=n2.id and n2.mid = ? and c2.ord  in " + Utils.ids2str(ords)
        val cids: List<Long> = col.db.queryLongList(cardIdsToDeleteSql, modelId)
        // Timber.d("cardIdsToDeleteSql was ' %s' and got %s", cardIdsToDeleteSql, Utils.ids2str(cids));
        Timber.d("getCardIdsForModel found %s cards to delete for model %s and ords %s", cids.size, modelId, Utils.ids2str(ords))

        // all notes with this template must have at least two cards, or we could end up creating orphaned notes
        val noteCountPreDeleteSql = "select count(distinct(nid)) from cards where nid in (select id from notes where mid = ?)"
        val preDeleteNoteCount: Int = col.db.queryScalar(noteCountPreDeleteSql, modelId)
        Timber.d("noteCountPreDeleteSql was '%s'", noteCountPreDeleteSql)
        Timber.d("preDeleteNoteCount is %s", preDeleteNoteCount)
        val noteCountPostDeleteSql = "select count(distinct(nid)) from cards where nid in (select id from notes where mid = ?) and ord not in " + Utils.ids2str(ords)
        Timber.d("noteCountPostDeleteSql was '%s'", noteCountPostDeleteSql)
        val postDeleteNoteCount: Int = col.db.queryScalar(noteCountPostDeleteSql, modelId)
        Timber.d("postDeleteNoteCount would be %s", postDeleteNoteCount)
        if (preDeleteNoteCount != postDeleteNoteCount) {
            Timber.d("There will be orphan notes if these cards are deleted.")
            return null
        }
        Timber.d("Deleting these cards will not orphan notes.")
        return cids
    }

    // These are all legacy and should be removed when possible
    companion object {
        const val NOT_FOUND_NOTE_TYPE = -1L

        @KotlinCleanup("direct return and use scope function")
        fun newTemplate(name: String?): JSONObject {
            val t = JSONObject(defaultTemplate)
            t.put("name", name)
            return t
        }

        private const val defaultTemplate =
            (
                "{\"name\": \"\", " + "\"ord\": null, " + "\"qfmt\": \"\", " +
                    "\"afmt\": \"\", " + "\"did\": null, " + "\"bqfmt\": \"\"," + "\"bafmt\": \"\"," + "\"bfont\": \"\"," +
                    "\"bsize\": 0 }"
                )

        /** "Mapping of field name -> (ord, field).  */
        fun fieldMap(m: com.ichi2.libanki.NotetypeJson): Map<String, Pair<Int, JSONObject>> {
            val flds = m.getJSONArray("flds")
            // TreeMap<Integer, String> map = new TreeMap<Integer, String>();
            val result: MutableMap<String, Pair<Int, JSONObject>> = HashUtil.HashMapInit(flds.length())
            for (f in flds.jsonObjectIterable()) {
                result[f.getString("name")] = Pair(f.getInt("ord"), f)
            }
            return result
        }

        // not in anki
        fun isModelNew(m: com.ichi2.libanki.NotetypeJson): Boolean {
            return m.getLong("id") == 0L
        }

        fun _updateTemplOrds(m: com.ichi2.libanki.NotetypeJson) {
            val tmpls = m.getJSONArray("tmpls")
            for (i in 0 until tmpls.length()) {
                val f = tmpls.getJSONObject(i)
                f.put("ord", i)
            }
        }
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
fun Collection.getNotetypeNamesRaw(input: ByteArray): ByteArray {
    return backend.getNotetypeNamesRaw(input)
}

fun Collection.getFieldNamesRaw(input: ByteArray): ByteArray {
    return backend.getFieldNamesRaw(input)
}

fun Collection.updateNotetype(updatedNotetype: Notetype): OpChanges {
    return backend.updateNotetype(input = updatedNotetype)
}

fun Collection.removeNotetype(notetypeId: Long): OpChanges {
    return backend.removeNotetype(ntid = notetypeId)
}

fun Collection.addNotetype(newNotetype: Notetype): OpChangesWithId {
    return backend.addNotetype(input = newNotetype)
}

fun Collection.getNotetypeNameIdUseCount(): List<NotetypeNameIdUseCount> {
    return backend.getNotetypeNamesAndCounts()
}

fun Collection.getNotetype(notetypeId: Long): Notetype {
    return backend.getNotetype(ntid = notetypeId)
}

fun Collection.getNotetypeNames(): List<NotetypeNameId> {
    return backend.getNotetypeNames()
}

fun Collection.addNotetypeLegacy(json: ByteString): OpChangesWithId {
    return backend.addNotetypeLegacy(json = json)
}

fun Collection.getStockNotetypeLegacy(kind: StockNotetype.Kind): ByteString {
    return backend.getStockNotetypeLegacy(kind = kind)
}
