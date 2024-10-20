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
import com.ichi2.annotations.NeedsTest
import com.ichi2.libanki.Consts.MODEL_CLOZE
import com.ichi2.libanki.Utils.checksum
import com.ichi2.libanki.backend.BackendUtils
import com.ichi2.libanki.backend.BackendUtils.toJsonBytes
import com.ichi2.libanki.exception.ConfirmModSchemaException
import com.ichi2.libanki.utils.LibAnkiAlias
import com.ichi2.libanki.utils.NotInLibAnki
import com.ichi2.libanki.utils.TimeManager
import com.ichi2.libanki.utils.append
import com.ichi2.libanki.utils.index
import com.ichi2.libanki.utils.insert
import com.ichi2.libanki.utils.len
import com.ichi2.libanki.utils.remove
import com.ichi2.libanki.utils.set
import com.ichi2.utils.Assert
import com.ichi2.utils.HashUtil
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
typealias Template = JSONObject // Dict<str, Union3<str, int, Unit>>

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
    fun save(notetype: NotetypeJson) {
        // legacy code expects preserve_usn=false behaviour, but that
        // causes a backup entry to be created, which invalidates the
        // v2 review history. So we manually update the usn/mtime here
        notetype.put("mod", TimeManager.time.intTime())
        notetype.put("usn", col.usn())
        update(notetype, preserveUsnAndMtime = true)
    }

    /*
    # Caching
    #############################################################
    # A lot of existing code expects to be able to quickly and
    # frequently obtain access to an entire notetype, so we currently
    # need to cache responses from the backend. Please do not
    # access the cache directly!
     */

    @LibAnkiAlias("_update_cache")
    private fun updateCache(nt: NotetypeJson) {
        _cache[nt.id] = nt
    }

    @LibAnkiAlias("_remove_from_cache")
    private fun removeFromCache(ntid: int) {
        _cache.remove(ntid)
    }

    @LibAnkiAlias("_get_cached")
    private fun getCached(ntid: int): NotetypeJson? {
        return _cache[ntid]
    }

    @NeedsTest("14827: styles are updated after syncing style changes")
    @LibAnkiAlias("_clear_cache")
    fun clearCache() = _cache.clear()

    /*
    # Listing note types
    #############################################################
     */

    @LibAnkiAlias("all_names_and_ids")
    fun allNamesAndIds(): Sequence<NoteTypeNameID> {
        return col.backend.getNotetypeNames().map {
            NoteTypeNameID(it.name, it.id)
        }.asSequence()
    }

    /* legacy */

    fun ids(): Set<int> {
        return allNamesAndIds().map { it.id }.toSet()
    }

    // only used by importing code
    fun have(id: int): Boolean = allNamesAndIds().any { it.id == id }

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
        return get(allNamesAndIds().first().id)!!
    }

    fun setCurrent(notetype: NotetypeJson) {
        col.config.set("curModel", notetype.id)
    }

    /*
    # Retrieving and creating models
    #############################################################
     */

    @LibAnkiAlias("id_for_name")
    fun idForName(name: String): Long? {
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
        var nt = getCached(id)
        if (nt == null) {
            try {
                nt = NotetypeJson(
                    BackendUtils.fromJsonBytes(
                        col.backend.getNotetypeLegacy(id)
                    )
                )
                updateCache(nt)
            } catch (e: BackendNotFoundException) {
                return null
            }
        }
        return nt
    }

    /** Get all models */
    fun all(): List<NotetypeJson> {
        return allNamesAndIds().map { get(it.id)!! }.toMutableList()
    }

    /** Get model with NAME. */
    fun byName(name: String): NotetypeJson? {
        val id = idForName(name)
        return id?.let { get(it) }
    }

    /** Create a new non-cloze model, and return it. */
    fun new(name: String): NotetypeJson {
        // caller should call save() after modifying
        val nt = newBasicNotetype()
        nt.flds = JSONArray()
        nt.tmpls = JSONArray()
        nt.name = name
        return nt
    }

    fun newBasicNotetype(): NotetypeJson {
        return NotetypeJson(
            BackendUtils.fromJsonBytes(
                col.backend.getStockNotetypeLegacy(StockNotetype.Kind.KIND_BASIC)
            )
        )
    }

    /** Delete model, and all its cards/notes. */
    fun rem(notetype: NotetypeJson) {
        remove(notetype.id)
    }

    /** Modifies schema. */
    fun remove(id: int) {
        removeFromCache(id)
        col.backend.removeNotetype(id)
    }

    fun add(notetype: NotetypeJson) {
        save(notetype)
    }

    fun ensureNameUnique(notetype: NotetypeJson) {
        val existingId = idForName(notetype.name)
        existingId?.let {
            if (it != notetype.id) {
                // Python uses a float time, but it doesn't really matter, the goal is just a random id.
                notetype.name += "-" + checksum(TimeManager.time.intTimeMS().toString()).substring(0, 5)
            }
        }
    }

    /** Add or update an existing model. Use .save() instead. */
    fun update(notetype: NotetypeJson, preserveUsnAndMtime: Boolean = true) {
        removeFromCache(notetype.id)
        ensureNameUnique(notetype)
        notetype.id = col.backend.addOrUpdateNotetype(
            json = toJsonBytes(notetype),
            preserveUsnAndMtime = preserveUsnAndMtime,
            skipChecks = preserveUsnAndMtime
        )
        setCurrent(notetype)
        mutateAfterWrite(notetype)
    }

    @LibAnkiAlias("_mutate_after_write")
    private fun mutateAfterWrite(nt: NotetypeJson) {
        // existing code expects the note type to be mutated to reflect
        // the changes made when adding, such as ordinal assignment :-(
        val updated = get(nt.id)!!
        nt.update(updated)
    }

    /*
    # Tools
    ##################################################
     */

    @NotInLibAnki
    fun nids(model: NotetypeJson): List<int> = nids(model.getLong("id"))

    /** Note ids for M. */
    fun nids(ntid: int): List<int> {
        return col.db.queryLongList("select id from notes where mid = ?", ntid)
    }

    /** Number of note using M. */
    fun useCount(notetype: NotetypeJson): Int {
        return col.db.queryLongScalar("select count() from notes where mid = ?", notetype.id).toInt()
    }

    @RustCleanup("not in libAnki any more - may not be needed")
    fun tmplUseCount(notetype: NotetypeJson, ord: Int): Int {
        return col.db.queryScalar(
            "select count() from cards, notes where cards.nid = notes.id and notes.mid = ? and cards.ord = ?",
            notetype.id,
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
    fun copy(notetype: NotetypeJson): NotetypeJson {
        val m2 = notetype.deepClone()
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
    @LibAnkiAlias("new_field")
    fun newField(name: String): Field {
        val nt = newBasicNotetype()
        val field = nt.flds.getJSONObject(0)
        field.put("name", name)
        field.put("ord", JSONObject.NULL)
        return field
    }

    /** Modifies schema */
    @LibAnkiAlias("add_field")
    fun addField(notetype: NotetypeJson, field: Field) {
        notetype.flds.append(field)
    }

    /** Modifies schema. */
    @LibAnkiAlias("remove_field")
    fun removeField(notetype: NotetypeJson, field: Field) {
        notetype.flds.remove(field)
    }

    /** Modifies schema. */
    @LibAnkiAlias("reposition_field")
    fun repositionField(notetype: NotetypeJson, field: Field, idx: Int) {
        val oldidx = notetype.flds.index(field).get()
        if (oldidx == idx) {
            return
        }

        notetype.flds.remove(field)
        notetype.flds.insert(idx, field)
    }

    @LibAnkiAlias("rename_field")
    fun renameField(notetype: NotetypeJson, field: Field, newName: String) {
        assert(notetype.flds.jsonObjectIterable().contains(field))
        field["name"] = newName
    }

    /** Modifies schema. */
    @LibAnkiAlias("set_sort_index")
    fun setSortIndex(nt: NotetypeJson, idx: Int) {
        assert(0 <= idx && idx < len(nt.flds))
        nt.sortf = idx
    }

    /*
     legacy
     */
    @RustCleanup("legacy")
    fun addFieldLegacy(notetype: NotetypeJson, field: Field) {
        addField(notetype, field)
        if (notetype.id != 0L) {
            save(notetype)
        }
    }

    @RustCleanup("legacy")
    fun remFieldLegacy(notetype: NotetypeJson, field: Field) {
        removeField(notetype, field)
        save(notetype)
    }

    @RustCleanup("legacy")
    fun moveFieldLegacy(notetype: NotetypeJson, field: Field, idx: Int) {
        repositionField(notetype, field, idx)
        save(notetype)
    }

    @RustCleanup("legacy")
    fun renameFieldLegacy(notetype: NotetypeJson, field: Field, newName: String) {
        renameField(notetype, field, newName)
        save(notetype)
    }

    /**
     * similar to Anki's addField; but thanks to assumption that
     * model is new, it never has to throw
     * [ConfirmModSchemaException]
     */
    @RustCleanup("Since Kotlin doesn't have throws, this may not be needed")
    fun addFieldInNewModel(notetype: NotetypeJson, field: JSONObject) {
        Assert.that(isModelNew(notetype), "Model was assumed to be new, but is not")
        try {
            addFieldLegacy(notetype, field)
        } catch (e: ConfirmModSchemaException) {
            Timber.w(e, "Unexpected mod schema")
            CrashReportService.sendExceptionReport(e, "addFieldInNewModel: Unexpected mod schema")
            throw IllegalStateException("ConfirmModSchemaException should not be thrown", e)
        }
    }

    fun addTemplateInNewModel(notetype: NotetypeJson, template: JSONObject) {
        // similar to addTemplate, but doesn't throw exception;
        // asserting the model is new.
        Assert.that(isModelNew(notetype), "Model was assumed to be new, but is not")

        try {
            addTemplate(notetype, template)
        } catch (e: ConfirmModSchemaException) {
            Timber.w(e, "Unexpected mod schema")
            CrashReportService.sendExceptionReport(e, "addTemplateInNewModel: Unexpected mod schema")
            throw IllegalStateException("ConfirmModSchemaException should not be thrown", e)
        }
    }

    fun addFieldModChanged(notetype: NotetypeJson, field: JSONObject) {
        // similar to Anki's addField; but thanks to assumption that
        // mod is already changed, it never has to throw
        // ConfirmModSchemaException.
        Assert.that(col.schemaChanged(), "Mod was assumed to be already changed, but is not")
        addFieldLegacy(notetype, field)
    }

    fun addTemplateModChanged(notetype: NotetypeJson, template: JSONObject) {
        // similar to addTemplate, but doesn't throw exception;
        // asserting the model is new.
        Assert.that(col.schemaChanged(), "Mod was assumed to be already changed, but is not")
        addTemplate(notetype, template)
    }

    /*
    # Adding & changing templates
    ##################################################
     */

    @RustCleanup("Check JSONObject.NULL")
    @LibAnkiAlias("new_template")
    fun newTemplate(name: String): Template {
        val nt = newBasicNotetype()
        val template = nt.tmpls.getJSONObject(0)
        template["name"] = name
        template["qfmt"] = ""
        template["afmt"] = ""
        template.put("ord", JSONObject.NULL)
        return template
    }

    /** Modifies schema. */
    @LibAnkiAlias("add_template")
    fun add_template(notetype: NotetypeJson, template: Template) {
        notetype.tmpls.append(template)
    }

    /** Modifies schema */
    @LibAnkiAlias("remove_template")
    fun removeTemplate(notetype: NotetypeJson, template: Template) {
        assert(len(notetype.tmpls) > 1)
        notetype.tmpls.remove(template)
    }

    /** Modifies schema. */
    @LibAnkiAlias("reposition_template")
    fun repositionTemplate(notetype: NotetypeJson, template: Template, idx: Int) {
        val oldidx = notetype.tmpls.index(template).get()
        if (oldidx == idx) {
            return
        }

        notetype.tmpls.remove(template)
        notetype.tmpls.insert(idx, template)
    }

    /** legacy */

    fun addTemplate(notetype: NotetypeJson, template: Template) {
        add_template(notetype, template)
        if (notetype.id != 0L) {
            save(notetype)
        }
    }

    fun remTemplate(notetype: NotetypeJson, template: Template) {
        removeTemplate(notetype, template)
        save(notetype)
    }

    fun moveTemplate(notetype: NotetypeJson, template: Template, idx: Int) {
        repositionTemplate(notetype, template, idx)
        save(notetype)
    }

    /*
    # Model changing
    ##########################################################################
    # - maps are ord->ord, and there should not be duplicate targets
    # - newModel should be same as m if model is not changing
     */

    /**
     * Modifies the backend schema. Ask the user to confirm schema changes before calling
     *
     * A compatibility wrapper that converts legacy-style arguments and
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
        noteType: NotetypeJson,
        nid: NoteId,
        newModel: NotetypeJson,
        fmap: Map<Int, Int?>,
        cmap: Map<Int, Int?>
    ): OpChanges {
        val fieldMap = convertLegacyMap(fmap, newModel.fieldsNames.size)
        val templateMap =
            if (cmap.isEmpty() || noteType.type == MODEL_CLOZE || newModel.type == MODEL_CLOZE) {
                listOf()
            } else {
                convertLegacyMap(cmap, newModel.templatesNames.size)
            }
        val isCloze = newModel.isCloze || noteType.isCloze
        return col.backend.changeNotetype(
            noteIds = listOf(nid),
            newFields = fieldMap,
            newTemplates = templateMap,
            oldNotetypeId = noteType.id,
            newNotetypeId = newModel.id,
            currentSchema = col.scm,
            oldNotetypeName = noteType.name,
            isCloze = isCloze
        )
    }

    /** Convert old->new map to list of old indexes/nulls */
    private fun convertLegacyMap(map: Map<Int, Int?>, newSize: Int): Iterable<Int> {
        val newToOld = map.entries.filter { it.value != null }.associate { (k, v) -> v to k }
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
    fun scmhash(notetype: NotetypeJson): String {
        var s = ""
        for (f in notetype.flds.jsonObjectIterable()) {
            s += f["name"]
        }
        for (t in notetype.tmpls.jsonObjectIterable()) {
            s += t["name"]
        }
        return checksum(s)
    }

    /*
     * Other stuff NOT IN LIBANKI
     * ***********************************************************************************************
     */

    fun count(): Int {
        return allNamesAndIds().count()
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

        fun newTemplate(name: String): JSONObject = JSONObject(DEFAULT_TEMPLATE).also {
            it.put("name", name)
        }

        private const val DEFAULT_TEMPLATE =
            (
                "{\"name\": \"\", " + "\"ord\": null, " + "\"qfmt\": \"\", " +
                    "\"afmt\": \"\", " + "\"did\": null, " + "\"bqfmt\": \"\"," + "\"bafmt\": \"\"," + "\"bfont\": \"\"," +
                    "\"bsize\": 0 }"
                )

        /** "Mapping of field name -> (ord, field).  */
        fun fieldMap(notetype: NotetypeJson): Map<String, Pair<Int, JSONObject>> {
            val flds = notetype.getJSONArray("flds")
            // TreeMap<Integer, String> map = new TreeMap<Integer, String>();
            val result: MutableMap<String, Pair<Int, JSONObject>> = HashUtil.hashMapInit(flds.length())
            for (f in flds.jsonObjectIterable()) {
                result[f.getString("name")] = Pair(f.getInt("ord"), f)
            }
            return result
        }

        // not in anki
        fun isModelNew(notetype: NotetypeJson): Boolean {
            return notetype.getLong("id") == 0L
        }

        fun _updateTemplOrds(notetype: NotetypeJson) {
            val tmpls = notetype.getJSONArray("tmpls")
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
