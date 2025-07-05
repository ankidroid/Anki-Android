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

import androidx.annotation.VisibleForTesting
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import anki.collection.OpChanges
import anki.notetypes.copy
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.common.annotations.NeedsTest
import com.ichi2.anki.common.json.NamedJSONComparator
import com.ichi2.anki.observability.undoableOp
import com.ichi2.anki.utils.InitStatus
import com.ichi2.anki.utils.ViewModelDelayedInitializer
import com.ichi2.anki.utils.ext.require
import com.ichi2.libanki.CardTemplates
import com.ichi2.libanki.Fields
import com.ichi2.libanki.NoteId
import com.ichi2.libanki.NoteTypeId
import com.ichi2.libanki.NotetypeJson
import com.ichi2.libanki.Notetypes
import com.ichi2.libanki.exception.ConfirmModSchemaException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.launch
import timber.log.Timber

private typealias TemplateIndex = Int
private typealias FieldIndex = Int

/**
 * [ViewModel] for [ChangeNoteTypeDialog]
 *
 * Supports bulk editing the [Note Type][NotetypeJson] of multiple [notes][com.ichi2.libanki.Note].
 *
 * A user selects a new note type ([setOutputNoteTypeId]), then:
 *
 * For each [Field][com.ichi2.libanki.Fields] in the new note type:
 *   * Select the source field from the current note type, or (nothing) if the field should be blank
 *
 * For each [Card Template][CardTemplates] in the new note type:
 *  * Select the source Card Template to transfer [scheduling information][com.ichi2.libanki.Card] from
 *
 * There are complexities in moving to and from a Cloze Note Type, expressed in [ConversionType]:
 *   * Standard Note Types are `1 -> {0,1}`: A Card Template may or may not generate a card, but
 *     a note type may have many templates
 *   * Cloze Note Types are `1 -> N`: There is One Card Template which can generate multiple cards
 *
 * Changing the type of a note requires a one-way sync.
 */
// Improvement: make availableNoteTypes responsive to collection updates
// TODO: save state to SavedStateHandle
class ChangeNoteTypeViewModel(
    stateHandle: SavedStateHandle,
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

    // This should be lateinit, but isn't due to `value class`
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
    internal lateinit var fieldChangeMap: MutableMap<FieldIndex, FieldIndex?>

    @VisibleForTesting
    internal lateinit var templateChangeMap: MutableMap<TemplateIndex, TemplateIndex?>

    // Flows
    // ************************************************

    /** A trigger for when [fieldChangeMap] is changed */
    private val fieldMappingChanged = MutableSharedFlow<Unit>()

    /** A trigger for when [templateChangeMap] is changed */
    private val templateMappingChanged = MutableSharedFlow<Unit>()

    /** Flow to track initialization status */
    override val initStatus: MutableStateFlow<InitStatus> = MutableStateFlow(InitStatus.PENDING)

    /** Flow which closes the dialog */
    val closeDialogFlow = MutableStateFlow<Unit?>(null)

    // lateinit flows
    // ************************************************

    /**
     * A flow emitting the note type selected by the user
     *
     * @see setOutputNoteTypeId
     */
    lateinit var outputNoteTypeFlow: MutableStateFlow<NotetypeJson>

    /** A flow emitting whether the current change is between cloze and regular note types */
    lateinit var conversionTypeFlow: StateFlow<ConversionType>

    /**
     * Whether [updateTemplateMapping] can be called
     *
     * Templates may only be updated if the input and output note type are non-cloze
     */
    lateinit var canChangeTemplatesFlow: StateFlow<Boolean>

    // Derived Flows
    // ************************************************

    /**
     * A list of the names of the templates which would be discarded
     *
     * e.g. `["Card 1", "Card 3"]`
     */
    val discardedTemplatesFlow =
        templateMappingChanged
            .map {
                val mappedIndices = templateChangeMap.values.toSet()
                inputNoteType.templatesNames.filterIndexed { index, _ -> index !in mappedIndices }
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = emptyList(),
            )

    /**
     * A list of the names of the fields which will be discarded
     *
     * e.g. `["Front", "Extra"]`
     */
    val discardedFieldsFlow =
        fieldMappingChanged
            .map {
                val mappedIndices = fieldChangeMap.values.toSet()
                inputNoteType.fieldsNames.filterIndexed { index, _ -> index !in mappedIndices }
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = emptyList(),
            )

    // Derived state
    // ************************************************

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
            outputNoteTypeFlow = MutableStateFlow(inputNoteType)
            conversionTypeFlow =
                outputNoteTypeFlow
                    .transform { newNoteType ->
                        val source = _inputNoteType ?: return@transform
                        emit(ConversionType.fromNoteTypes(current = source, new = newNoteType))
                    }.stateIn(
                        scope = viewModelScope,
                        started = SharingStarted.Eagerly,
                        initialValue = ConversionType.fromNoteTypes(current = inputNoteType, new = outputNoteType),
                    )
            canChangeTemplatesFlow =
                conversionTypeFlow
                    .map {
                        it == ConversionType.REGULAR_TO_REGULAR
                    }.stateIn(
                        scope = viewModelScope,
                        started = SharingStarted.Eagerly,
                        initialValue = !inputNoteType.isCloze,
                    )

            resetNotetypeMaps(selectedNoteType = inputNoteType)
        }
    }

    /**
     * Performs the [changeNoteTypeOfNotes] operation
     *
     * @return the number of notes affected
     *
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

            undoableOp {
                notetypes.changeNoteTypeOfNotes(
                    fromId = inputNoteType.id,
                    toId = outputNoteType.id,
                    noteIds = noteIds,
                    fieldMap = fieldChangeMap,
                    templateMap = templateChangeMap,
                )
            }

            closeDialogFlow.emit(Unit)

            return@async noteIds.size
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
        fieldChangeMap.put(outputFieldIndex, mappedFrom.toBackendInt())
        // Increment the counter to notify observers that field mapping has changed
        fieldMappingChanged.emit(Unit)
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
        val updatedValue = mappedFrom.toBackendInt()

        // a card can only be mapped once, change all 'other' values to null
        if (updatedValue != null) {
            val keysToMapToNothing = templateChangeMap.filterValues { it == updatedValue }.keys
            assert(keysToMapToNothing.size <= 1) { "a card was mapped multiple times" }
            keysToMapToNothing.forEach { templateChangeMap[it] = null }
        }

        templateChangeMap.put(outputTemplateIndex, updatedValue)
        // Increment the counter to notify observers that card mapping has changed
        templateMappingChanged.emit(Unit)
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
            resetNotetypeMaps(newNoteType)
        }

    /**
     * Resets [fieldChangeMap] and [templateChangeMap] to the default values for [selectedNoteType]
     */
    private suspend fun resetNotetypeMaps(selectedNoteType: NotetypeJson) {
        fun buildFieldMap(
            inputFields: Fields,
            outputFields: Fields,
        ): MutableMap<Int, Int?> {
            // initially match ords by name
            val currentOrdMapping =
                outputFields
                    .mapNotNull { inputField ->
                        inputFields
                            .find { it.name == inputField.name }
                            ?.let { selectedField -> inputField.ord to selectedField.ord as Int? }
                    }.toMap(HashMap())

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

        fun buildTemplateMap(
            inputTemplates: CardTemplates,
            outputTemplates: CardTemplates,
        ): MutableMap<Int, Int?> =
            outputTemplates.indices
                .associateWith { idx ->
                    if (idx < inputTemplates.size) idx else null
                }.toMutableMap()

        fieldChangeMap = buildFieldMap(inputNoteType.fields, selectedNoteType.fields)
        templateChangeMap = buildTemplateMap(inputNoteType.templates, selectedNoteType.templates)
        templateMappingChanged.emit(Unit)
        fieldMappingChanged.emit(Unit)
    }

    enum class Tab(
        val position: Int,
    ) {
        Fields(0),
        Templates(1),
    }

    companion object {
        const val ARG_NOTE_IDS = "ARG_NOTE_IDS"
    }
}

enum class ConversionType {
    REGULAR_TO_REGULAR,
    REGULAR_TO_CLOZE,
    CLOZE_TO_REGULAR,
    CLOZE_TO_CLOZE,
    ;

    companion object {
        fun fromNoteTypes(
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

fun Notetypes.changeNoteTypeOfNotes(
    fromId: NoteTypeId,
    toId: NoteTypeId,
    noteIds: List<NoteId>,
    fieldMap: Map<TemplateIndex, TemplateIndex?>,
    templateMap: Map<TemplateIndex, TemplateIndex?>,
): OpChanges {
    val info = changeNotetypeInfo(oldNoteTypeId = fromId, newNoteTypeId = toId)

    // The `newFields` and `newTemplates` lists are relative to the new notetype's
    // field/template count.
    //
    // Each value represents the index in the previous notetype.
    // -1 indicates the original value will be discarded.
    val input =
        info.input.copy {
            this.noteIds.addAll(noteIds)
            fieldMap.forEach { (key, value) -> newFields[key] = value ?: -1 }
            // moving to and from cloze only allows field mappings
            if (newTemplates.any()) {
                templateMap.forEach { (key, value) -> newTemplates[key] = value ?: -1 }
            }
        }

    if (fromId == toId && info.input.newFieldsList == input.newFieldsList && info.input.newTemplatesList == input.newTemplatesList) {
        Timber.i("change note types: no changes to save")
        throw IllegalArgumentException("no changes to save")
    }

    return this.changeNotetypeOfNotes(input)
}

/**
 * A user selection when changing fields or templates,
 * either an [index][Index], or [(nothing)][NOTHING]
 */
sealed class SelectedIndex {
    data object NOTHING : SelectedIndex()

    data class Index(
        val index: Int,
    ) : SelectedIndex()

    fun toBackendInt(): FieldIndex? =
        when (this) {
            is NOTHING -> null
            is Index -> this.index
        }

    companion object {
        fun from(index: Int): SelectedIndex = Index(index)
    }
}
