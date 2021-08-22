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
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.annotation.RequiresApi
import androidx.annotation.VisibleForTesting
import androidx.core.os.bundleOf
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import com.ichi2.anki.CollectionHelper
import com.ichi2.anki.DeckSpinnerSelection
import com.ichi2.anki.R
import com.ichi2.anki.UIUtils
import com.ichi2.anki.dialogs.DeckSelectionDialog
import com.ichi2.anki.importer.ImporterOptionSelectionFragment.SpinnerSelectionListener.Companion.SpinnerItemConfig
import com.ichi2.anki.ui.NoteTypeSpinnerUtils
import com.ichi2.libanki.Collection
import com.ichi2.libanki.Consts
import com.ichi2.libanki.did
import com.ichi2.libanki.importer.TextImporter
import com.ichi2.libanki.ntid
import timber.log.Timber
import java.util.*

/** Allows a user to modify the options for importing a text file before the import takes place */
@RequiresApi(api = Build.VERSION_CODES.O) // TextImporter -> FileObj
internal open class ImporterOptionSelectionFragment : Fragment(R.layout.import_csv_option_selection), DeckSelectionDialog.DeckSelectionListener {

    // region UI Components

    private lateinit var deckSpinner: DeckSpinnerSelection

    /** Control which contains the tag that will be applied to modified notes
     * Only usable if importMode is set to [ImportConflictMode.UPDATE]
     */
    protected lateinit var modifiedNotesTag: TextView
    protected lateinit var fieldDelimiterButton: Button
    @VisibleForTesting
    lateinit var noteTypeSpinner: Spinner

    // See if we can remove this. Associated with [noteTypeSpinner]
    @VisibleForTesting
    lateinit var noteTypeIds: ArrayList<Long>
    private lateinit var importButton: Button

    // endregion UI Components

    /** The mutable bundle of all options which the screen is responsible for */
    @VisibleForTesting
    lateinit var importOptions: ImportOptions
    @VisibleForTesting
    lateinit var csvMapping: CsvMapping
    private lateinit var collection: Collection
    /**
     * The libAnki class responsible for importing
     * number of CSV fields, and whether a mapping is valid
     */
    private lateinit var importer: TextImporter

    private var csvFieldCount: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        // collection loaded? Let's assume we have one
        collection = CollectionHelper.getInstance().getCol(context)
        val filePath = requireArguments().getString(ARG_REQUIRED_PATH)!!
        this.importer = getImporterInstance(collection, filePath)

        val csvFieldCount = importer.fields()

        val ntid: ntid = collection.models.current()!!.getLong("id")

        val csvMapping = CsvMapping(csvFieldCount, queryNoteTypeFromId(ntid))

        childFragmentManager.fragmentFactory = FieldMappingFragment.Factory(csvMapping)

        super.onCreate(savedInstanceState)

        this.csvMapping = csvMapping
        this.csvFieldCount = csvFieldCount

        importOptions = ImportOptions(
            path = filePath,
            noteTypeId = ntid,
            deck = collection.conf.optLong("curDeck", Consts.DEFAULT_DECK_ID),
            delimiterChar = importer.delimiter,
            importMode = ImportConflictMode.IGNORE,
            allowHtml = AllowHtml.INCLUDE_HTML,
            importModeTag = "",
            mapping = csvMapping.csvMap.toMutableList()
        )
    }

    protected open fun getImporterInstance(collection: Collection, filePath: String) =
        TextImporter(collection, filePath)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val context = requireContext()

        // setup deck picker
        this.deckSpinner = DeckSpinnerSelection(this, collection, view.findViewById(R.id.deck_selector)).apply {
            initializeNoteEditorDeckSpinner(null, false)
        }

        // setup type picker
        this.noteTypeSpinner = view.findViewById(R.id.note_type_spinner)
        noteTypeIds = NoteTypeSpinnerUtils.setupNoteTypeSpinner(context, noteTypeSpinner, collection)
        noteTypeSpinner.onItemSelectedListener = SpinnerSelectionListener(noteTypeIds) { mid -> setNoteTypeId(mid) }

        // setup the "field delimiter" button
        this.fieldDelimiterButton = view.findViewById<Button>(R.id.btn_csv_field_delimiter).apply {
            setOnClickListener { openFieldDelimiterDialog { setFieldDelimiterOverride(it) } }
        }

        // setup "Allow HTML"
        view.findViewById<CheckBox>(R.id.check_allow_html).apply {
            setOnCheckedChangeListener { _, isChecked -> setAllowHtml(isChecked) }
        }

        // Setup "Import Mode"
        val importModeSpinner: Spinner = view.findViewById(R.id.import_conflict_mode)
        val toDisplayString = { it: ImportConflictMode -> resources.getString(it.resourceId) }
        val itemConfig = SpinnerItemConfig(ImportConflictMode.values().toList(), toDisplayString, this::setImportMode)
        SpinnerSelectionListener.setupAdapter(importModeSpinner, context, itemConfig)

        // setup "Tag Modified Notes"
        modifiedNotesTag = view.findViewById<TextView>(R.id.text_tag_modified_notes).apply {
            doOnTextChanged { text, _, _, _ -> setModifiedNotesTag(text.toString(), updateText = false) }
        }

        // setup "import csv"
        importButton = view.findViewById<Button>(R.id.import_csv_button).apply {
            setOnClickListener {
                if (!it.isActivated) {
                    UIUtils.showThemedToast(context, getString(R.string.importing_first_field_must_be_mapped, noteTypeFirstFieldName()), true)
                    return@setOnClickListener
                }

                closeFragmentWithResult(importOptions)
            }
        }

        setupInitialValues(importOptions)

        csvMapping.onChange.add { this.checkButtonStatus() }
    }

    private fun noteTypeFirstFieldName() = collection.models[importOptions.noteTypeId]!!.getJSONArray("flds").getJSONObject(0).getString("name")

    private fun checkButtonStatus() {
        importer.setMapping(csvMapping.csvMap)
        importer.setModel(collection.models[importOptions.noteTypeId])
        // we use "activated" as "enabled" removes events, and we want to handle the "deactivated" state
        // as we want to let the user know why the process can't continue
        importButton.isActivated = importer.mappingOk()
    }

    private fun setupInitialValues(importOptions: ImportOptions) {
        setDeckId(importOptions.deck, force = true)
        setNoteTypeId(importOptions.noteTypeId, force = true)
        setDelimiterButtonText(importOptions.delimiterChar)
        checkButtonStatus()
    }

    fun setFieldDelimiterOverride(fieldDelimiterCharacter: DelimiterChar) {
        setDelimiterButtonText(fieldDelimiterCharacter)
        importer.delimiter = fieldDelimiterCharacter
        setFieldCount(importer.fields())
        importOptions.delimiterChar = fieldDelimiterCharacter
    }

    private fun setDelimiterButtonText(delimiterChar: DelimiterChar) {
        fieldDelimiterButton.text = getString(R.string.import_fields_separated_by, delimiterChar.toDisplayString(requireContext()))
    }

    private fun setFieldCount(fieldCount: Int) {
        if (csvFieldCount == fieldCount) {
            return
        }

        csvFieldCount = fieldCount
        csvMapping.setFieldCount(csvFieldCount)
    }

    private fun openFieldDelimiterDialog(onNewDelimiter: (DelimiterChar) -> Unit) {
        FieldDelimiterDialog.show(requireContext(), importOptions.delimiterChar.toString()) {
            onNewDelimiter(it)
        }
    }

    override fun onDeckSelected(deck: DeckSelectionDialog.SelectableDeck?) {
        if (deck == null) {
            return
        }
        // We need this call in case a new deck has been added
        deckSpinner.initializeNoteEditorDeckSpinner(null, false)
        setDeckId(deck.deckId)
    }

    fun setDeckId(deckId: did, force: Boolean = false) {
        if (!force && importOptions.deck == deckId) {
            return
        }
        importOptions.deck = deckId
        deckSpinner.selectDeckById(deckId, false)
    }

    fun setImportMode(importMode: ImportConflictMode) {
        importOptions.importMode = importMode
        setTagModifiedNotesEnabled(importMode == ImportConflictMode.UPDATE)
    }

    private fun setTagModifiedNotesEnabled(value: Boolean) {
        // we use enabled instead of visible as it's jarring to see a change in item positions
        // it appears that one more field mapping is added
        modifiedNotesTag.isEnabled = value
    }

    fun setModifiedNotesTag(value: String, updateText: Boolean = true) {
        if (importOptions.importModeTag == value) {
            return
        }
        importOptions.importModeTag = value
        if (updateText) {
            modifiedNotesTag.text = value
        }
    }

    fun setNoteTypeId(ntid: ntid, force: Boolean = false) {
        if (!force && importOptions.noteTypeId == ntid) {
            return
        }
        importOptions.noteTypeId = ntid
        noteTypeSpinner.setSelection(noteTypeIds.indexOf(ntid))
        // setup "Field Mapping" - this uses the csvMapping model, so if we set that up, nothing else
        // is required
        csvMapping.setModel(queryNoteTypeFromId(ntid))
        checkButtonStatus()
    }

    fun setAllowHtml(allowHtml: Boolean) {
        importOptions.allowHtml = AllowHtml.fromBoolean(allowHtml)
    }

    fun closeFragmentWithResult(importOptions: ImportOptions): Bundle {
        Timber.i("closing options fragment: success")
        importOptions.mapping = csvMapping.csvMap.toMutableList()
        // see ImporterHostFragment - this detects the result and replaces the fragment
        val bundleResult = bundleOf(RESULT_BUNDLE_OPTIONS to importOptions)
        setFragmentResult(RESULT_KEY, bundleResult)
        return bundleResult
    }

    private fun queryNoteTypeFromId(ntid: Long): CollectionNoteType {
        val model = collection.models[ntid]!!
        return CollectionNoteType(model.getString("name"), model.fieldsNames)
    }

    private inner class CollectionNoteType(override val name: String, override val fields: List<FieldName>) : CsvMapping.NoteType()

    private class SpinnerSelectionListener<T>(private val lookupValues: List<T>, private val onValueSelected: (T) -> Unit) :
        AdapterView.OnItemSelectedListener {
        override fun onNothingSelected(parent: AdapterView<*>?) {
            // intentionally blank
        }

        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
            val item = lookupValues[position]
            onValueSelected(item)
        }

        companion object {
            /** Wraps a collection and functions to operate on it
             * This removes the mismatch of the adapter accepting a string, and the onItemSelected
             */
            class SpinnerItemConfig<T>(val items: List<T>, val toDisplayString: (T) -> String, val onItemSelected: (T) -> Unit)

            fun <T> setupAdapter(spinner: Spinner, context: Context, values: SpinnerItemConfig<T>): ArrayAdapter<String> {
                val displayStrings = values.items.map { values.toDisplayString(it) }
                val arrayAdapter = ArrayAdapter(context, R.layout.multiline_spinner_item, displayStrings)
                spinner.adapter = arrayAdapter
                spinner.onItemSelectedListener = SpinnerSelectionListener(values.items) {
                    values.onItemSelected(it)
                }
                return arrayAdapter
            }
        }
    }

    companion object {
        /** The key identifying the fragment result */
        const val RESULT_KEY = "ImportOptions"
        /**
         * The key of the options in the resulting bundle
         * This is an extra of type [ImportOptions]
         */
        const val RESULT_BUNDLE_OPTIONS = "importOptions"

        /**
         *
         */
        const val ARG_REQUIRED_PATH = "filePath"
    }
}
