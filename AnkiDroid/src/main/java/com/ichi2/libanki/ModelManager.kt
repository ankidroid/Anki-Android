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

import com.ichi2.anki.exception.ConfirmModSchemaException
import com.ichi2.utils.JSONObject

abstract class ModelManager {

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
    abstract fun current(forDeck: Boolean): Model?
    abstract fun setCurrent(m: Model)

    /** get model with ID, or null.  */
    abstract fun get(id: Long): Model?
    /** get all models  */
    abstract fun all(): ArrayList<Model>

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
    abstract fun update(m: Model)
    abstract fun have(id: Long): Boolean
    abstract fun ids(): Set<Long>

    /*
      Tools ***********************************************************************************************
     */

    /** Note ids for M  */
    abstract fun nids(m: Model): ArrayList<Long>

    /**
     * Number of notes using m
     * @param m The model to the count the notes of.
     * @return The number of notes with that model.
     */
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
    abstract fun addFieldInNewModel(m: Model, field: JSONObject)
    abstract fun addFieldModChanged(m: Model, field: JSONObject)
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
    abstract fun addTemplateInNewModel(m: Model, template: JSONObject)
    abstract fun addTemplateModChanged(m: Model, template: JSONObject)
    /**
     * Removing a template
     *
     * @return False if removing template would leave orphan notes.
     * @throws ConfirmModSchemaException
     */
    @Throws(ConfirmModSchemaException::class)
    abstract fun remTemplate(m: Model, template: JSONObject): Boolean
    /**
     * Extracted from remTemplate so we can test if removing templates is safe without actually removing them
     * This method will either give you all the card ids for the ordinals sent in related to the model sent in *or*
     * it will return null if the result of deleting the ordinals is unsafe because it would leave notes with no cards
     *
     * @param modelId long id of the JSON model
     * @param ords array of ints, each one is the ordinal a the card template in the given model
     * @return null if deleting ords would orphan notes, long[] of related card ids to delete if it is safe
     */
    abstract fun getCardIdsForModel(modelId: Long, ords: IntArray): List<Long>?

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
    abstract fun change(m: Model, nid: Long, newModel: Model, fmap: Map<Int, Int>, cmap: Map<Int, Int>)

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

    abstract fun getModels(): HashMap<Long, Model>

    /** @return Number of models */
    abstract fun count(): Int

    /** Validate model entries.  */
    abstract fun validateModel(): Boolean
}
