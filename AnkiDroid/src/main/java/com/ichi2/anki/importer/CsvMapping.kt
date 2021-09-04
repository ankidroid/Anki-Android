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

package com.ichi2.anki.importer

import android.content.Context
import android.os.Parcelable
import com.ichi2.anki.R
import com.ichi2.anki.importer.CsvFieldMappingBehavior.MapToNothing
import com.ichi2.utils.SequenceUtil.takeWhileIncludingFirstNonMatch
import kotlinx.parcelize.Parcelize

/**
 * Model class for the mapping between a csv with a number of columns, and a note type
 *
 * Dispatches events on changes
 *
 * Changes:
 * # Mapping changed (minor change)
 * # Number of columns changed (CSV delimiter can cause this to change). Major + minor change
 * # Note Type Changed (Major + minor change)
 *
 * @param csvColumnCount The number of columns in the CSV (non-empty)
 * @param noteType The note type that the mapping is associated with (fields must be non-empty)
 */
internal class CsvMapping(private var csvColumnCount: Int, private var noteType: NoteType) {
    /**
     * A list of delegates to be executed if a mapping from CSV field to note type field changes.
     *
     * Consumers should subscribe for changes to [csvMap]
     */
    var onChange: MutableList<Runnable> = mutableListOf()

    /**
     * Specifies that the available values has changed, or the number of fields has changed.
     *
     * Consumers should subscribe if they will need to rebuild the list from [csvMap] and [availableOptions]
     */
    var onMajorChange: Runnable? = null

    lateinit var availableOptions: List<CsvFieldMappingBehavior>

    /** The internal state of the map from CSV field (index) and behavior */
    private lateinit var internalMapping: MutableList<CsvFieldMappingBehavior>

    /**
     * Read-only copy of the map from CSV fields to Note Type fields
     * The index is the index of the column in the CSV
     * The value is the [CsvFieldMappingBehavior] that
     */
    val csvMap: List<CsvFieldMappingBehavior> get() = internalMapping

    init {
        if (csvColumnCount <= 0) throw IllegalArgumentException("more than one CSV column must be provided")
        if (noteType.fields.isEmpty()) throw IllegalArgumentException("more than one note type field must be provided")

        init()
    }

    private fun init() {
        internalMapping = getDefaultBehaviorMapping().take(csvColumnCount).toMutableList()
        availableOptions = getDefaultBehaviorMapping()
            .takeWhileIncludingFirstNonMatch { it !is MapToNothing }
            .toList()
    }

    val size get() = internalMapping.size

    operator fun get(i: Int): CsvFieldMappingBehavior = internalMapping[i]

    /**
     * Inserts the [CsvFieldMappingBehavior] at the specified position
     * If the behaviour already existed and was not [CsvFieldMappingBehavior.MapToNothing] then the
     * existing value will be replaced with [CsvFieldMappingBehavior.MapToNothing], and the value
     * will be inserted at the provided position
     *
     * @param index the index to set the value to
     * @param behaviorToSelect the [CsvFieldMappingBehavior] to select
     *
     * raises: [onChange]
     */
    fun setMapping(index: Int, behaviorToSelect: CsvFieldMappingBehavior) {
        if (internalMapping[index] == behaviorToSelect) {
            // nothing to do
            return
        }

        // If the mapping already exists: it can only be added once, remove it.
        if (behaviorToSelect !is MapToNothing) {
            // we remove by replacing it with 'nothing'
            val foundValueIndex = internalMapping.indexOf(behaviorToSelect)
            if (foundValueIndex != -1) {
                internalMapping[foundValueIndex] = MapToNothing
            }
        }

        internalMapping[index] = behaviorToSelect
        dispatchMinorChanges()
    }

    private fun getDefaultBehaviorMapping() = sequence {
        yieldAll(noteType.fields.map { CsvFieldMappingBehavior.MapToField(it) })
        yield(CsvFieldMappingBehavior.MapToTags)
        // all remaining CSV fields are mapped to nothing
        while (true) {
            yield(MapToNothing)
        }
    }

    /**
     * Sets the note type to map csv fields to. This resets the mapping.
     * raises: [onMajorChange] and [onChange]
     */
    fun setModel(noteType: NoteType) {
        if (noteType.fields.isEmpty()) throw IllegalArgumentException("more than one note type field must be provided")

        this.noteType = noteType
        init()

        onMajorChange?.run()
        dispatchMinorChanges()
    }

    /**
     * Sets the number of fields in the CSV. This currently resets the mapping, but this is subject to change in the future
     * fires: [onMajorChange] and [onChange]
     */
    fun setFieldCount(fieldCount: Int) {
        if (csvColumnCount <= 0) throw IllegalArgumentException("more than one CSV column must be provided")

        this.csvColumnCount = fieldCount
        init()

        onMajorChange?.run()
        dispatchMinorChanges()
    }

    private fun dispatchMinorChanges() = onChange.forEach { it.run() }

    abstract class NoteType {
        abstract val name: String
        abstract val fields: List<FieldName>
    }
}

/** Defines how a field in the CSV should be handled */
internal abstract class CsvFieldMappingBehavior : Parcelable {
    @Parcelize
    data class MapToField(val field: FieldName) : CsvFieldMappingBehavior() {
        override fun toDisplayString(ctx: Context): String = ctx.getString(R.string.import_mapped_to_field, field)
    }
    @Parcelize
    object MapToTags : CsvFieldMappingBehavior() {
        override fun toDisplayString(ctx: Context): String = ctx.getString(R.string.import_mapped_to_tags)
    }
    @Parcelize
    object MapToNothing : CsvFieldMappingBehavior() {
        override fun toDisplayString(ctx: Context): String = ctx.getString(R.string.import_mapped_to_nothing)
    }
    abstract fun toDisplayString(ctx: Context): String
}
