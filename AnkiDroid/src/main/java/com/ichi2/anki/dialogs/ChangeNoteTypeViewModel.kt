/*
 *  Copyright (c) 2025 Hari Srinivasan <harisrini21@gmail.com>
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

package com.ichi2.anki.dialogs

import androidx.annotation.CheckResult
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import anki.collection.OpChanges
import anki.notetypes.ChangeNotetypeRequest
import anki.notetypes.copy
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.common.annotations.NeedsTest
import com.ichi2.anki.common.json.NamedJSONComparator
import com.ichi2.anki.dialogs.ChangeNoteTypeException.Kind.NO_CHANGES
import com.ichi2.anki.libanki.CardTemplates
import com.ichi2.anki.libanki.NoteId
import com.ichi2.anki.libanki.NoteTypeId
import com.ichi2.anki.libanki.NotetypeJson
import com.ichi2.anki.libanki.exception.ConfirmModSchemaException
import com.ichi2.anki.observability.undoableOp
import com.ichi2.anki.utils.InitStatus
import com.ichi2.anki.utils.ViewModelDelayedInitializer
import com.ichi2.anki.utils.ext.require
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.launch
import timber.log.Timber

private typealias TemplateIndex = Int
private typealias FieldIndex = Int

/**
 * [ViewModel] for [ChangeNoteTypeDialog]
 *
 * Supports bulk editing the [Note Type][NotetypeJson] of multiple [notes][com.ichi2.anki.libanki.Note].
 *
 * A user selects a new note type ([setOutputNoteTypeId]), then:
 *
 * For each [Field][com.ichi2.anki.libanki.Fields] in the new note type:
 *   * Select the source field from the current note type, or (nothing) if the field should be blank
 *
 * For each [Card Template][CardTemplates] in the new note type:
 *  * Select the source Card Template to transfer [scheduling information][com.ichi2.anki.libanki.Card] from
 *
 * There are complexities in moving to and from a Cloze Note Type, expressed in [ConversionType]:
 *   * Standard Note Types are `1 -> {0,1}`: A Card Template may or may not generate a card, but
 *     a note type may have many templates
 *   * Cloze Note Types are `1 -> N`: There is One Card Template which can generate multiple cards
 *
 * Changing the type of a note requires a one-way sync.
 */
// Future improvement: make availableNoteTypes responsive to collection updates
class ChangeNoteTypeViewModel(
    private val stateHandle: SavedStateHandle,
) : ViewModel(),
    ViewModelDelayedInitializer {
    /**
     * IDs of notes to be modified
     *
     * non-empty & distinct
     */
    val noteIds: List<NoteId> =
        stateHandle.require<LongArray>(ARG_NOTE_IDS).toList().also {
            require(it.isNotEmpty()) { "$ARG_NOTE_IDS was empty" }
            require(it.distinct().size == it.size) { "$ARG_NOTE_IDS was not distinct" }
        }

    // unchanged after init { }
    // ************************************************

    // This should be lateinit, but isn't due to NotetypeJson being `value class`
    private var _inputNoteType: NotetypeJson? = null

    /** The note type of the notes to be modified */
    val inputNoteType: NotetypeJson
        get() = _inputNoteType!!

    /**
     * The note types which a user can provide to [setOutputNoteTypeId]
     */
    lateinit var availableNoteTypes: List<NotetypeJson>

    // UI variables
    // ************************************************

    /** @see Tab */
    var currentTab = Tab.Fields

    // User-modifiable state
    // ************************************************

    @VisibleForTesting
    internal lateinit var fieldChangeMapFlow: MutableStateFlow<Map<FieldIndex, FieldIndex?>>

    @VisibleForTesting
    internal lateinit var templateChangeMapFlow: MutableStateFlow<Map<TemplateIndex, TemplateIndex?>>

    /**
     * A flow emitting the note type selected by the user
     *
     * @see setOutputNoteTypeId
     */
    lateinit var outputNoteTypeFlow: MutableStateFlow<NotetypeJson>

    // Flows
    // ************************************************

    /** Flow to track initialization status */
    override val flowOfInitStatus: MutableStateFlow<InitStatus> = MutableStateFlow(InitStatus.Pending)

    /** Flow which closes the dialog */
    val closeDialogFlow = MutableStateFlow<Unit?>(null)

    // lateinit flows
    // ************************************************

    /** A flow emitting whether the current change is between cloze and regular note types */
    val conversionTypeFlow by lazy {
        outputNoteTypeFlow
            .transform { newNoteType ->
                val source = _inputNoteType ?: return@transform
                emit(ConversionType.fromNoteTypeChange(current = source, new = newNoteType))
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = ConversionType.fromNoteTypeChange(current = inputNoteType, new = outputNoteType),
            )
    }

    /**
     * Whether [updateTemplateMapping] can be called
     *
     * Templates may only be updated if the input and output note type are non-cloze
     */
    val canChangeTemplatesFlow by lazy {
        conversionTypeFlow
            .map {
                it == ConversionType.REGULAR_TO_REGULAR
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = !inputNoteType.isCloze,
            )
    }

    // Derived Flows
    // ************************************************

    /**
     * A list of the names of the templates which would be discarded
     *
     * e.g. `["Card 1", "Card 3"]`
     */
    val discardedTemplatesFlow by lazy {
        templateChangeMapFlow
            .map { map ->
                val mappedIndices = map.values.toSet()
                inputNoteType.templatesNames.filterIndexed { index, _ -> index !in mappedIndices }
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = emptyList(),
            )
    }

    /**
     * A list of the names of the fields which will be discarded
     *
     * e.g. `["Front", "Extra"]`
     */
    val discardedFieldsFlow by lazy {
        fieldChangeMapFlow
            .map { map ->
                val mappedIndices = map.values.toSet()
                inputNoteType.fieldsNames.filterIndexed { index, _ -> index !in mappedIndices }
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = emptyList(),
            )
    }

    // Derived state
    // ************************************************

    val fieldChangeMap: Map<FieldIndex, FieldIndex?>
        get() = fieldChangeMapFlow.value

    val templateChangeMap: Map<TemplateIndex, TemplateIndex?>
        get() = templateChangeMapFlow.value

    /** The notes which [executeChangeNoteTypeAsync] will affect */
    val noteCount
        get() = noteIds.size

    override val scope: CoroutineScope
        get() = viewModelScope

    /**
     * The note type which [noteIds] will be converted to
     *
     * @see setOutputNoteTypeId
     */
    val outputNoteType
        get() = outputNoteTypeFlow.value

    init {
        delayedInit {
            _inputNoteType = withCol { getNote(noteIds.first()) }.notetype
            availableNoteTypes = withCol { notetypes.all().sortedWith(NamedJSONComparator.INSTANCE) }

            // delayed init of outputNoteType and dependent properties
            val outputNoteType =
                stateHandle.get<NoteTypeId>(STATE_OUTPUT_NOTE_TYPE_ID).let { id ->
                    if (id == null || id == inputNoteType.id) {
                        inputNoteType
                    } else {
                        Timber.d("restoring output note type: %d", id)
                        withCol { notetypes.get(id) } ?: inputNoteType
                    }
                }
            outputNoteTypeFlow = MutableStateFlow(outputNoteType)
            outputNoteTypeFlow
                .onEach { noteType ->
                    stateHandle[STATE_OUTPUT_NOTE_TYPE_ID] = noteType.id
                }.launchIn(viewModelScope)

            // restore template & field mapping
            stateHandle.get<Map<FieldIndex, FieldIndex?>>(STATE_FIELD_MAP)?.let { fieldMap ->
                fieldChangeMapFlow = MutableStateFlow(fieldMap)
            }

            stateHandle.get<Map<TemplateIndex, TemplateIndex?>>(STATE_TEMPLATE_MAP)?.let { fieldMap ->
                templateChangeMapFlow = MutableStateFlow(fieldMap)
            }

            if (!this::fieldChangeMapFlow.isInitialized || !this::templateChangeMapFlow.isInitialized) {
                Timber.d("initializing maps")
                fieldChangeMapFlow = MutableStateFlow(rebuildFieldMap(selectedNoteType = outputNoteType))
                templateChangeMapFlow = MutableStateFlow(rebuildTemplateMap(selectedNoteType = outputNoteType))
            } else {
                Timber.d("maps restored from SavedStateHandle")
            }

            // persist state updates
            fieldChangeMapFlow
                .onEach { stateHandle[STATE_FIELD_MAP] = it }
                .launchIn(viewModelScope)

            templateChangeMapFlow
                .onEach { stateHandle[STATE_TEMPLATE_MAP] = it }
                .launchIn(viewModelScope)
        }
    }

    /**
     * Performs the [changeNoteTypeOfNotes] operation
     *
     * @return the number of notes affected
     *
     * @throws ChangeNoteTypeException no changes are made
     * @throws ConfirmModSchemaException if a one-way sync dialog needs to be accepted
     */
    @NeedsTest("one way sync")
    @NeedsTest("closeDialogFlow")
    fun executeChangeNoteTypeAsync() =
        viewModelScope.async {
            Timber.d("Changing note type from '%s' to '%s'", inputNoteType.name, outputNoteType.name)
            Timber.d("Field map: %s", fieldChangeMap)
            Timber.d("Card map: %s", templateChangeMap)

            withCol { modSchema() }

            val changes =
                changeNoteTypeOfNotes(
                    noteIds = noteIds,
                    sourceId = inputNoteType.id,
                    targetId = outputNoteType.id,
                    fieldMap = fieldChangeMap,
                    templateMap = templateChangeMap,
                )

            undoableOp { changes }

            closeDialogFlow.emit(Unit)

            return@async noteIds.size
        }

    /**
     * For a given list of Note Ids, change the note type from [sourceId] to [targetId].
     *
     * [fieldMap] maps [fields][NotetypeJson.fields] between the types
     *
     * [templateMap] maps [fields][NotetypeJson.templates] between the types
     *
     * @throws ChangeNoteTypeException If no changes are made
     */
    @CheckResult
    private suspend fun changeNoteTypeOfNotes(
        noteIds: List<NoteId>,
        sourceId: NoteTypeId,
        targetId: NoteTypeId,
        fieldMap: Map<FieldIndex, FieldIndex?>,
        templateMap: Map<TemplateIndex, TemplateIndex?>,
    ): OpChanges =
        withCol {
            val info = notetypes.changeNotetypeInfo(oldNoteTypeId = sourceId, newNoteTypeId = targetId)

            // The `newFields` and `newTemplates` lists are relative to the new notetype's
            // field/template count.
            //
            // Each value represents the index in the previous notetype.
            // -1 indicates the original value will be discarded.
            val input: ChangeNotetypeRequest =
                info.input.copy {
                    this.noteIds.addAll(noteIds)
                    fieldMap.forEach { (key, value) -> newFields[key] = value ?: -1 }
                    // moving to and from cloze only allows field mappings
                    if (newTemplates.any()) {
                        templateMap.forEach { (key, value) -> newTemplates[key] = value ?: -1 }
                    }
                }

            if (sourceId == targetId &&
                info.input.newFieldsList == input.newFieldsList &&
                info.input.newTemplatesList == input.newTemplatesList
            ) {
                Timber.i("change note types: no changes to save")
                throw ChangeNoteTypeException(NO_CHANGES, "No changes to save")
            }

            return@withCol notetypes.changeNotetypeOfNotes(input)
        }

    /**
     * Updates the input field which is used o populate an output field
     *
     * example:
     * ```
     * input  = ["A0", "A1", "A2"]
     * output = ["B0", "B1", "B2"]
     *
     * // the following means "B1" will receive its value from "A2"
     * updateFieldMapping(outputFieldIndex = 1, mappedFrom = SelectedIndex.from(2))
     * ```
     */
    fun updateFieldMapping(
        outputFieldIndex: Int,
        mappedFrom: SelectedIndex,
    ) = viewModelScope.launch {
        Timber.d("Updating field mapping: '%d' -> '%s'", outputFieldIndex, mappedFrom)
        fieldChangeMapFlow.value =
            fieldChangeMap
                .toMutableMap()
                .apply { this[outputFieldIndex] = mappedFrom.toNullableInt() }
    }

    /**
     * Updates the input template which is used o populate an output template
     *
     * example:
     * ```
     * input  = ["Card 0", "Card 1", "Card 2"]
     * output = ["Card A", "Card B", "Card C"]
     *
     * // the following means "Card B" will receive its value from "Card 2"
     * updateTemplateMapping(outputTemplateIndex = 1, mappedFrom = SelectedIndex.from(2))
     * ```
     */
    fun updateTemplateMapping(
        outputTemplateIndex: Int,
        mappedFrom: SelectedIndex,
    ) = viewModelScope.launch {
        require(canChangeTemplatesFlow.value) { "changing templates was disabled" }

        Timber.d("Updating card mapping: %d -> %s", outputTemplateIndex, mappedFrom)
        val updatedValue = mappedFrom.toNullableInt()

        val updatedMap = templateChangeMap.toMutableMap()
        // a card can only be mapped once, change all 'other' values to null
        if (updatedValue != null) {
            val keysToMapToNothing = updatedMap.filterValues { it == updatedValue }.keys
            assert(keysToMapToNothing.size <= 1) { "a card was mapped multiple times" }
            keysToMapToNothing.forEach { updatedMap[it] = null }
        }

        updatedMap.put(outputTemplateIndex, updatedValue)

        templateChangeMapFlow.value = updatedMap
    }

    /**
     * Updates the selected note type and resets template and field mappings
     *
     * @param id The id of the selected note type
     * @throws IllegalArgumentException if [id] is not found
     */
    fun setOutputNoteTypeId(id: NoteTypeId) =
        viewModelScope.async {
            val newNoteType = requireNotNull(availableNoteTypes.find { it.id == id }) { "note type $id not found" }
            Timber.i("updating selected note type to ${newNoteType.id}")
            outputNoteTypeFlow.value = newNoteType
            // Initialize maps immediately after note type selection
            fieldChangeMapFlow.value = rebuildFieldMap(newNoteType)
            templateChangeMapFlow.value = rebuildTemplateMap(newNoteType)
        }

    private fun rebuildFieldMap(selectedNoteType: NotetypeJson): Map<Int, Int?> {
        val inputFields = inputNoteType.fields
        val outputFields = selectedNoteType.fields

        // initially match ords by name
        val currentOrdMapping =
            outputFields
                .mapNotNull { inputField ->
                    inputFields
                        .find { it.name == inputField.name }
                        ?.let { selectedField -> inputField.ord to selectedField.ord as Int? }
                }.toMap(HashMap())

        // the fill in any unmapped ords
        val unmappedOrds = (0 until outputFields.size).filter { ord -> ord !in currentOrdMapping }
        for (unmappedOrd in unmappedOrds) {
            currentOrdMapping[unmappedOrd] =
                when {
                    // we've exhausted all the input ords
                    currentOrdMapping.size >= inputFields.size -> null
                    // if [ord] -> [ord] is unused, map it
                    !currentOrdMapping.containsValue(unmappedOrd) -> unmappedOrd
                    // otherwise, pick the first unmapped input field
                    else -> (0 until inputFields.size).firstOrNull { !currentOrdMapping.containsValue(it) }
                }
        }
        return currentOrdMapping
    }

    /** Assign index [0] from the old to the new type etc.. */
    private fun rebuildTemplateMap(selectedNoteType: NotetypeJson): Map<Int, Int?> {
        val inputTemplates = inputNoteType.templates
        val outputTemplates = selectedNoteType.templates
        return outputTemplates.indices
            .associateWith { idx ->
                if (idx < inputTemplates.size) idx else null
            }
    }

    enum class Tab(
        val position: Int,
    ) {
        Fields(0),
        Templates(1),
    }

    companion object {
        const val ARG_NOTE_IDS = "ARG_NOTE_IDS"
        const val STATE_FIELD_MAP = "fieldMap"
        const val STATE_TEMPLATE_MAP = "templateMap"
        const val STATE_OUTPUT_NOTE_TYPE_ID = "outputNoteType"
    }
}

/** How the regular/cloze status of a note type is affected */
enum class ConversionType {
    REGULAR_TO_REGULAR,
    REGULAR_TO_CLOZE,
    CLOZE_TO_REGULAR,
    CLOZE_TO_CLOZE,
    ;

    companion object {
        fun fromNoteTypeChange(
            current: NotetypeJson,
            new: NotetypeJson,
        ): ConversionType {
            val isCurrent = current.isCloze
            val isNew = new.isCloze
            return when {
                isCurrent && isNew -> CLOZE_TO_CLOZE
                isCurrent && !isNew -> CLOZE_TO_REGULAR
                !isCurrent && isNew -> REGULAR_TO_CLOZE
                else -> REGULAR_TO_REGULAR
            }
        }
    }
}

/**
 * A user selection when mapping fields or templates when changing note type
 * either an [index][Index], or [(nothing)][NOTHING]
 */
sealed interface SelectedIndex {
    /** The field/template will be discarded in the new note type */
    data object NOTHING : SelectedIndex

    /** The field/template will be mapped to [index] in the new new note type */
    data class Index(
        val index: Int,
    ) : SelectedIndex

    /** Converts the structure to a nullable int, which is easier to serialize */
    fun toNullableInt(): Int? =
        when (this) {
            is NOTHING -> null
            is Index -> this.index
        }

    companion object {
        fun from(index: Int): SelectedIndex = Index(index)
    }
}

/** An expected exception when changing note types */
class ChangeNoteTypeException(
    val kind: Kind,
    message: String,
) : IllegalStateException(message) {
    enum class Kind {
        NO_CHANGES,
    }
}
