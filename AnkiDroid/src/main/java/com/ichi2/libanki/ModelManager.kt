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

import androidx.annotation.WorkerThread
import com.ichi2.anki.CrashReportService
import com.ichi2.anki.exception.ConfirmModSchemaException
import com.ichi2.utils.Assert
import net.ankiweb.rsdroid.RustCleanup
import org.json.JSONObject
import timber.log.Timber

@WorkerThread
abstract class ModelManager(protected val col: Collection) {

    /*
     * Saving/loading registry
     * ***********************************************************************************************
     */

    /** Load registry from JSON. */
    abstract fun load(json: String)

    /** Mark M modified if provided, and schedule registry flush. */
    fun save() = save(null)
    fun save(m: Model?) = save(m, false)

    /**
     * Save a model
     * @param m model to save
     * @param templates flag which (when true) re-generates the cards for each note which uses the model
     */
    abstract fun save(m: Model?, templates: Boolean)

    /** Flush the registry if any models were changed. */
    abstract fun flush()

    abstract fun ensureNotEmpty(): Boolean

    /*
      Retrieving and creating models
      ***********************************************************************************************
     */

    /**
     * Get current model.
     * @return The model, or null if not found in the deck and in the configuration.
     */
    fun current() = current(true)

    /**
     * Get current model.
     * @param forDeck If true, it tries to get the deck specified in deck by mid, otherwise or if the former is not
     * found, it uses the configuration`s field curModel.
     * @return The model, or null if not found in the deck and in the configuration.
     */
    abstract fun current(forDeck: Boolean = true): Model?
    abstract fun setCurrent(m: Model)

    /** get model with ID, or null.  */
    abstract fun get(id: Long): Model?

    /** get all models  */
    abstract fun all(): List<Model>

    /** get the names of all models */
    abstract fun allNames(): List<String>

    /** get model with NAME.  */
    abstract fun byName(name: String): Model?

    /** Create a new model, save it in the registry, and return it.  */
    // Called `new` in Anki's code. New is a reserved word in java,
    // not in python. Thus the method has to be renamed.
    abstract fun newModel(name: String): Model

    /** Delete model, and all its cards/notes.
     * @throws ConfirmModSchemaException
     */
    @Throws(ConfirmModSchemaException::class)
    abstract fun rem(m: Model)

    abstract fun add(m: Model)

    /** Add or update an existing model. Used for syncing and merging.  */
    open fun update(m: Model) = update(m, true)

    /**
     * Add or update an existing model. Used for syncing and merging.
     *
     * preserve_usn_and_mtime=True should only be required in two cases:
     * syncing (which is now handled on the Rust end)
     * importing apkg files (which will be handled by Rust in the future)
     */
    abstract fun update(m: Model, preserve_usn_and_mtime: Boolean = true)

    abstract fun have(id: Long): Boolean
    abstract fun ids(): Set<Long>

    /*
      Tools ***********************************************************************************************
     */

    /** Note ids for M  */
    abstract fun nids(m: Model): List<Long>

    /**
     * Number of notes using m
     * @param m The model to the count the notes of.
     * @return The number of notes with that model.
     */
    @RustCleanup("use all_use_counts()")
    abstract fun useCount(m: Model): Int

    /**
     * Number of notes using m
     * @param m The model to the count the notes of.
     * @param ord The index of the card template
     * @return The number of notes with that model.
     */
    abstract fun tmplUseCount(m: Model, ord: Int): Int

    /*
      Copying ***********************************************************************************************
     */

    /** Copy, save and return.  */
    abstract fun copy(m: Model): Model

    /*
     * Fields ***********************************************************************************************
     */

    abstract fun newField(name: String): JSONObject

    abstract fun sortIdx(m: Model): Int

    @Throws(ConfirmModSchemaException::class)
    abstract fun setSortIdx(m: Model, idx: Int)

    @Throws(ConfirmModSchemaException::class)
    abstract fun addField(m: Model, field: JSONObject)

    @Throws(ConfirmModSchemaException::class)
    abstract fun remField(m: Model, field: JSONObject)

    @Throws(ConfirmModSchemaException::class)
    abstract fun moveField(m: Model, field: JSONObject, idx: Int)

    @Throws(ConfirmModSchemaException::class)
    abstract fun renameField(m: Model, field: JSONObject, newName: String)

    /*
     * Templates ***********************************************************************************************
     */

    @Throws(ConfirmModSchemaException::class)
    abstract fun addTemplate(m: Model, template: JSONObject)

    /**
     * Removing a template
     *
     * @throws ConfirmModSchemaException
     */
    @Throws(ConfirmModSchemaException::class)
    abstract fun remTemplate(m: Model, template: JSONObject)

    abstract fun moveTemplate(m: Model, template: JSONObject, idx: Int)
    /*
      Model changing ***********************************************************************************************
     */

    /**
     * Change a model
     * @param m The model to change.
     * @param nid The notes that the change applies to.
     * @param newModel For replacing the old model with another one. Should be self if the model is not changing
     * @param fmap Map for switching fields. This is ord->ord and there should not be duplicate targets
     * @param cmap Map for switching cards. This is ord->ord and there should not be duplicate targets
     * @throws ConfirmModSchemaException
     */
    @Throws(ConfirmModSchemaException::class)
    abstract fun change(m: Model, nid: NoteId, newModel: Model, fmap: Map<Int, Int?>, cmap: Map<Int, Int?>)

    /*
      Schema hash ***********************************************************************************************
     */
    /** Return a hash of the schema, to see if models are compatible.  */
    abstract fun scmhash(m: Model): String

    /*
     * Sync handling ***********************************************************************************************
     */

    abstract fun beforeUpload()

    /*
     * Other stuff NOT IN LIBANKI
     * ***********************************************************************************************
     */

    abstract fun setChanged()

    abstract fun getModels(): Map<Long, Model>

    /** @return Number of models */
    abstract fun count(): Int

    /** Validate model entries.  */
    @RustCleanup("remove from Java and replace with unit test")
    fun validateModel(): Boolean {
        for (model in all()) {
            if (!validateBrackets(model)) {
                return false
            }
        }
        return true
    }

    /** Check if there is a right bracket for every left bracket.  */
    private fun validateBrackets(value: JSONObject): Boolean {
        val s = value.toString()
        var count = 0
        var inQuotes = false
        val ar = s.toCharArray()
        for (i in ar.indices) {
            val c = ar[i]
            // if in quotes, do not count
            if (c == '"' && (i == 0 || ar[i - 1] != '\\')) {
                inQuotes = !inQuotes
                continue
            }
            if (inQuotes) {
                continue
            }
            when (c) {
                '{' -> count++
                '}' -> {
                    count--
                    if (count < 0) {
                        return false
                    }
                }
            }
        }
        return count == 0
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
    open fun getCardIdsForModel(modelId: NoteTypeId, ords: IntArray): List<Long>? {
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

    /**
     * similar to Anki's addField; but thanks to assumption that
     * model is new, it never has to throw
     * [ConfirmModSchemaException]
     */
    @RustCleanup("Since Kotlin doesn't have throws, this may not be needed")
    fun addFieldInNewModel(m: Model, field: JSONObject) {
        Assert.that(Models.isModelNew(m), "Model was assumed to be new, but is not")
        try {
            _addField(m, field)
        } catch (e: ConfirmModSchemaException) {
            Timber.w(e, "Unexpected mod schema")
            CrashReportService.sendExceptionReport(e, "addFieldInNewModel: Unexpected mod schema")
            throw IllegalStateException("ConfirmModSchemaException should not be thrown", e)
        }
    }

    fun addTemplateInNewModel(m: Model, template: JSONObject) {
        // similar to addTemplate, but doesn't throw exception;
        // asserting the model is new.
        Assert.that(Models.isModelNew(m), "Model was assumed to be new, but is not")

        try {
            _addTemplate(m, template)
        } catch (e: ConfirmModSchemaException) {
            Timber.w(e, "Unexpected mod schema")
            CrashReportService.sendExceptionReport(e, "addTemplateInNewModel: Unexpected mod schema")
            throw IllegalStateException("ConfirmModSchemaException should not be thrown", e)
        }
    }

    /** Add template without schema mod */
    protected abstract fun _addTemplate(m: Model, template: JSONObject)

    /** Add field without schema mod */
    protected abstract fun _addField(m: Model, field: JSONObject)

    fun addFieldModChanged(m: Model, field: JSONObject) {
        // similar to Anki's addField; but thanks to assumption that
        // mod is already changed, it never has to throw
        // ConfirmModSchemaException.
        Assert.that(col.schemaChanged(), "Mod was assumed to be already changed, but is not")
        _addField(m, field)
    }

    fun addTemplateModChanged(m: Model, template: JSONObject) {
        // similar to addTemplate, but doesn't throw exception;
        // asserting the model is new.
        Assert.that(col.schemaChanged(), "Mod was assumed to be already changed, but is not")
        _addTemplate(m, template)
    }
}
