/*
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.export

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.annotation.IdRes
import androidx.annotation.LayoutRes
import androidx.appcompat.app.AlertDialog
import androidx.core.os.BundleCompat
import androidx.core.os.bundleOf
import androidx.core.text.HtmlCompat
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import anki.cards.cardIds
import anki.generic.Empty
import anki.import_export.ExportLimit
import anki.import_export.exportLimit
import anki.notes.noteIds
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.ichi2.anki.ALL_DECKS_ID
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.R
import com.ichi2.anki.common.annotations.NeedsTest
import com.ichi2.anki.common.time.TimeManager
import com.ichi2.anki.common.time.getTimestamp
import com.ichi2.anki.exportApkgPackage
import com.ichi2.anki.exportCollectionPackage
import com.ichi2.anki.exportSelectedCards
import com.ichi2.anki.exportSelectedNotes
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.libanki.DeckNameId
import com.ichi2.anki.requireAnkiActivity
import com.ichi2.anki.ui.BasicItemSelectedListener
import com.ichi2.compat.CompatHelper.Companion.getSerializableCompat
import com.ichi2.utils.negativeButton
import com.ichi2.utils.positiveButton
import kotlinx.coroutines.launch
import java.io.File

/**
 * Shows the possible options for exporting(collection, decks or notes/card selection).
 * Intended to replicate the desktop UI.
 */
class ExportDialogFragment : DialogFragment() {
    private lateinit var exportTypeSelector: Spinner
    private lateinit var deckSelector: Spinner
    private lateinit var loadingIndicator: CircularProgressIndicator
    private lateinit var selectedLabel: TextView
    private lateinit var decksSelectorContainer: FrameLayout
    private lateinit var collectionIncludeMedia: CheckBox
    private lateinit var apkgIncludeSchedule: CheckBox
    private lateinit var apkgIncludeDeckConfigs: CheckBox
    private lateinit var apkgIncludeMedia: CheckBox
    private lateinit var notesIncludeHtml: CheckBox
    private lateinit var notesIncludeTags: CheckBox
    private lateinit var notesIncludeDeckName: CheckBox
    private lateinit var notesIncludeNotetypeName: CheckBox
    private lateinit var notesIncludeUniqueIdentifier: CheckBox
    private lateinit var cardsIncludeHtml: CheckBox
    private lateinit var apkgExportLegacyCheckbox: CheckBox
    private lateinit var collectionExportLegacyCheckbox: CheckBox

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialogView =
            requireActivity().layoutInflater.inflate(R.layout.dialog_export_options, null).apply {
                initializeCommonUi()
                initializeCollectionExportUi()
                initializeApkgExportUi()
                initializeNotesExportUi()
                initializeCardsExportUi()
            }
        val extraDid = arguments?.getLong(ARG_DECK_ID, -1) // 0 is for "All decks"
        val extraType: ExportType? = arguments?.getSerializableCompat(ARG_TYPE)
        initializeDecks(extraDid)
        // start with the option for exporting a collection like on desktop unless we received a
        // deck id or a type of selection(plus selected ids), in this case preselect apkg export
        if ((extraDid != null && extraDid != -1L) || extraType != null) {
            exportTypeSelector.setSelection(ExportConfiguration.Apkg.index)
            showExtrasOptionsFor(dialogView, ExportConfiguration.Apkg)
        } else {
            exportTypeSelector.setSelection(ExportConfiguration.Collection.index)
            showExtrasOptionsFor(dialogView, ExportConfiguration.Collection)
        }
        return AlertDialog
            .Builder(requireActivity())
            .setView(dialogView)
            .negativeButton(R.string.dialog_cancel)
            .positiveButton(R.string.dialog_ok) {
                val selectedIndex = exportTypeSelector.selectedItemPosition
                // just to be safe, if not exporting a collection and the decks spinner is not
                // enabled(the user was really fast or fetching the decks is delayed for some
                // reason) then simply return
                if (selectedIndex != 0 && !deckSelector.isEnabled) return@positiveButton
                when (ExportConfiguration.from(selectedIndex)) {
                    ExportConfiguration.Collection -> handleCollectionExport()
                    ExportConfiguration.Apkg -> handleAnkiPackageExport()
                    ExportConfiguration.Notes -> handleNotesInPlainTextExport()
                    ExportConfiguration.Cards -> handleCardsInPlainTextExport()
                }
            }.create()
    }

    /**
     * @param did the target deck id
     * @return returns the position of the deck with id inside the decks adapter or defaults to
     * 0("All decks") if a position wasn't found
     */
    private fun findDeckPosition(did: DeckId): Int {
        var position = 0
        val adapter = deckSelector.adapter as DeckDisplayAdapter
        while (position < adapter.count) {
            if (adapter.getItem(position).id == did) {
                return position
            }
            position++
        }
        return if (position >= adapter.count) 0 else position
    }

    /**
     * Asynchronously initializes the decks selector. Expects to be called after the views were
     * initialized.
     *
     * @param selectedDeck the id of deck to select from the list of decks
     */
    private fun initializeDecks(selectedDeck: DeckId? = null) {
        lifecycleScope.launch {
            deckSelector.isEnabled = false
            // add "All decks" option on first position to replicate desktop
            val allDecks =
                mutableListOf(
                    DeckNameId(
                        requireActivity().getString(R.string.card_browser_all_decks),
                        ALL_DECKS_ID,
                    ),
                )
            allDecks.addAll(withCol { decks.allNamesAndIds(false) })
            deckSelector.adapter =
                DeckDisplayAdapter(
                    requireContext(),
                    android.R.layout.simple_spinner_item,
                    allDecks,
                ).apply {
                    setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                }
            if (selectedDeck != null) {
                deckSelector.setSelection(findDeckPosition(selectedDeck))
            }
            loadingIndicator.isVisible = false
            deckSelector.isEnabled = true
        }
    }

    private fun View.initializeCommonUi(): Unit =
        with(CollectionManager.TR) {
            // parse the backend text for these labels as html because they contain html bold tags
            findViewById<TextView>(R.id.export_label_type).text =
                HtmlCompat.fromHtml(exportingExportFormat(), HtmlCompat.FROM_HTML_MODE_LEGACY)
            findViewById<TextView>(R.id.export_label_include).text =
                HtmlCompat.fromHtml(exportingInclude(), HtmlCompat.FROM_HTML_MODE_LEGACY)
            exportTypeSelector =
                findViewById<Spinner>(R.id.export_type_selector).apply {
                    val exportTypesAdapter =
                        ArrayAdapter(
                            requireActivity(),
                            android.R.layout.simple_spinner_item,
                            listOf(
                                "${exportingAnkiCollectionPackage()} (.colpkg)",
                                "${exportingAnkiDeckPackage()} (.apkg)",
                                "${exportingNotesInPlainText()} (.txt)",
                                "${exportingCardsInPlainText()} (.txt)",
                            ),
                        ).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
                    adapter = exportTypesAdapter
                    onItemSelectedListener =
                        BasicItemSelectedListener { position, _ ->
                            showExtrasOptionsFor(this@initializeCommonUi, ExportConfiguration.from(position))
                        }
                }
            selectedLabel =
                findViewById<TextView>(R.id.selected_label).apply { text = exportingSelectedNotes() }
            loadingIndicator = findViewById(R.id.loading_decks_indicator)
            deckSelector = findViewById(R.id.decks_selector)
            decksSelectorContainer = findViewById(R.id.decks_selector_container)
        }

    /**
     * Initializes the views representing the extra options available when exporting a collection.
     */
    @NeedsTest("Checkbox value is provided to the correct export functions (true/false)")
    private fun View.initializeCollectionExportUi() =
        with(CollectionManager.TR) {
            collectionIncludeMedia =
                findViewById<CheckBox>(R.id.export_extras_collection_media).apply {
                    text = exportingIncludeMedia()
                }
            collectionExportLegacyCheckbox =
                findViewById<CheckBox>(R.id.export_legacy_checkbox_collection).apply {
                    text = exportingSupportOlderAnkiVersions()
                }
        }

    /**
     * Initializes the views representing the extra options available when exporting an Anki package.
     */
    @NeedsTest("Checkbox value is provided to the correct export functions (true/false)")
    private fun View.initializeApkgExportUi() =
        with(CollectionManager.TR) {
            apkgIncludeMedia =
                findViewById<CheckBox>(R.id.export_apkg_media).apply {
                    text = exportingIncludeMedia()
                }
            apkgIncludeDeckConfigs =
                findViewById<CheckBox>(R.id.export_apkg_deck_configs).apply {
                    text = exportingIncludeDeckConfigs()
                }
            apkgIncludeSchedule =
                findViewById<CheckBox>(R.id.export_apkg_schedule).apply {
                    text = exportingIncludeSchedulingInformation()
                }
            apkgExportLegacyCheckbox =
                findViewById<CheckBox>(R.id.export_legacy_checkbox_apkg).apply {
                    text = exportingSupportOlderAnkiVersions()
                }
        }

    /**
     * Initializes the views representing the extra options available when exporting notes.
     */
    private fun View.initializeNotesExportUi() =
        with(CollectionManager.TR) {
            notesIncludeHtml =
                findViewById<CheckBox>(R.id.notes_include_html).apply {
                    text = exportingIncludeHtmlAndMediaReferences()
                }
            notesIncludeTags =
                findViewById<CheckBox>(R.id.notes_include_tags).apply { text = exportingIncludeTags() }
            notesIncludeDeckName =
                findViewById<CheckBox>(R.id.notes_include_deck_name).apply {
                    text = exportingIncludeDeck()
                }
            notesIncludeNotetypeName =
                findViewById<CheckBox>(R.id.notes_include_notetype_name).apply {
                    text = exportingIncludeNotetype()
                }
            notesIncludeUniqueIdentifier =
                findViewById<CheckBox>(R.id.notes_include_unique_identifier).apply {
                    text = exportingIncludeGuid()
                }
        }

    /**
     * Initializes the views representing the extra options available when exporting cards.
     */
    private fun View.initializeCardsExportUi() =
        with(CollectionManager.TR) {
            cardsIncludeHtml =
                findViewById<CheckBox>(R.id.cards_include_html).apply {
                    text = exportingIncludeHtmlAndMediaReferences()
                }
        }

    /**
     * Displays the view containing the export extra options for the requested export type.
     */
    private fun showExtrasOptionsFor(
        container: View,
        targetConfig: ExportConfiguration,
    ) {
        // if we export as collection there's no deck/selected items to choose from
        if (targetConfig.layoutId == R.id.export_extras_collection) {
            decksSelectorContainer.isVisible = false
            selectedLabel.isVisible = false
        } else {
            if (arguments?.getSerializableCompat<ExportType>(ARG_TYPE) != null) {
                decksSelectorContainer.isVisible = false
                selectedLabel.isVisible = true
            } else {
                decksSelectorContainer.isVisible = true
                selectedLabel.isVisible = false
            }
        }
        ExportConfiguration.entries.forEach { config ->
            container.findViewById<View>(config.layoutId).isVisible = config.layoutId == targetConfig.layoutId
        }
    }

    private fun handleCollectionExport() {
        val includeMedia = collectionIncludeMedia.isChecked
        val legacy = collectionExportLegacyCheckbox.isChecked
        val exportPath =
            File(
                getExportRootFile(),
                "${CollectionManager.TR.exportingCollection()}-${getTimestamp(TimeManager.time)}.colpkg",
            ).path
        requireAnkiActivity().exportCollectionPackage(exportPath, includeMedia, legacy)
    }

    private fun handleAnkiPackageExport() {
        val includeSchedule = apkgIncludeSchedule.isChecked
        val includeDeckConfigs = apkgIncludeDeckConfigs.isChecked
        val includeMedia = apkgIncludeMedia.isChecked
        val legacy = apkgExportLegacyCheckbox.isChecked
        val limits = buildExportLimit()
        var packagePrefix = getNonCollectionNamePrefix()
        // files can't have `/` in their names
        packagePrefix = packagePrefix.replace("/", "_")
        val exportPath =
            File(
                getExportRootFile(),
                "$packagePrefix-${getTimestamp(TimeManager.time)}.apkg",
            ).path
        requireAnkiActivity().exportApkgPackage(
            exportPath = exportPath,
            withScheduling = includeSchedule,
            withDeckConfigs = includeDeckConfigs,
            withMedia = includeMedia,
            limit = limits,
            legacy = legacy,
        )
    }

    /**
     * Builds the prefix for the name of the exported file. This will be  either a deck's name or a
     * localized "SelectedNotes" text.
     */
    private fun getNonCollectionNamePrefix(): String =
        when (arguments?.getSerializableCompat<ExportType>(ARG_TYPE)) {
            ExportType.Notes, ExportType.Cards -> CollectionManager.TR.exportingSelectedNotes()
            // notes/cards weren't selected so export the chosen deck(s)
            null -> (deckSelector.adapter as DeckDisplayAdapter).getItem(deckSelector.selectedItemPosition).name
        }

    private fun handleNotesInPlainTextExport() {
        val includeHtml = notesIncludeHtml.isChecked
        val includeTags = notesIncludeTags.isChecked
        val includeDeckName = notesIncludeDeckName.isChecked
        val includeNotetype = notesIncludeNotetypeName.isChecked
        val includeUniqueIdentifier = notesIncludeUniqueIdentifier.isChecked
        val exportLimit = buildExportLimit()
        val exportPath =
            File(
                getExportRootFile(),
                "${getNonCollectionNamePrefix()}-${getTimestamp(TimeManager.time)}.txt",
            ).path
        requireAnkiActivity().exportSelectedNotes(
            exportPath = exportPath,
            withHtml = includeHtml,
            withTags = includeTags,
            withDeck = includeDeckName,
            withNotetype = includeNotetype,
            withGuid = includeUniqueIdentifier,
            limit = exportLimit,
        )
    }

    private fun handleCardsInPlainTextExport() {
        val includeHtml = cardsIncludeHtml.isChecked
        val exportLimit = buildExportLimit()
        val exportPath =
            File(
                getExportRootFile(),
                "${getNonCollectionNamePrefix()}-${getTimestamp(TimeManager.time)}.txt",
            ).path
        requireAnkiActivity().exportSelectedCards(
            exportPath = exportPath,
            withHtml = includeHtml,
            limit = exportLimit,
        )
    }

    /**
     * Builds the [ExportLimit] to be used when exporting. This will either restrict the export to
     * the selected notes/cards or, if those are not present, to the selected
     * deck(or all decks).
     *
     * @return an [ExportLimit] with the export constraints
     */
    private fun buildExportLimit(): ExportLimit =
        when (arguments?.getSerializableCompat<ExportType>(ARG_TYPE)) {
            ExportType.Notes -> {
                val selectedNotesIds =
                    arguments?.let {
                        BundleCompat.getParcelableArrayList(it, ARG_EXPORTED_IDS, Long::class.java)
                    } ?: error("Requested export for selected notes but no notes ids were passed in!")
                exportLimit { noteIds = noteIds { this.noteIds.addAll(selectedNotesIds.toList()) } }
            }

            ExportType.Cards -> {
                val selectedCardIds =
                    arguments?.let {
                        BundleCompat.getParcelableArrayList(it, ARG_EXPORTED_IDS, Long::class.java)
                    } ?: error("Requested export for selected cards but no cards ids were passed in!")
                exportLimit { cardIds = cardIds { this.cids.addAll(selectedCardIds) } }
            }
            // notes/cards weren't selected so export the chosen decks
            null -> {
                val deckNameId =
                    (deckSelector.adapter as DeckDisplayAdapter)
                        .getItem(deckSelector.selectedItemPosition)
                if (deckNameId.id == ALL_DECKS_ID) {
                    exportLimit { this.wholeCollection = Empty.getDefaultInstance() }
                } else {
                    exportLimit { this.deckId = deckNameId.id }
                }
            }
        }

    private fun getExportRootFile() =
        File(requireActivity().externalCacheDir, "export").also {
            it.mkdirs()
        }

    /**
     * An extension of [ArrayAdapter] which handles displaying a list of [DeckNameId] by their names
     * and which can also be queried for the [DeckNameId] for a position through [ArrayAdapter.getItem].
     */
    private class DeckDisplayAdapter(
        context: Context,
        @LayoutRes rowLayout: Int,
        private val decks: List<DeckNameId>,
    ) : ArrayAdapter<DeckNameId>(context, rowLayout, decks) {
        override fun getItem(position: Int): DeckNameId = decks[position]

        override fun getView(
            position: Int,
            convertView: View?,
            parent: ViewGroup,
        ): View =
            super.getView(position, convertView, parent).apply {
                findViewById<TextView>(android.R.id.text1).text = decks[position].name
            }

        override fun getDropDownView(
            position: Int,
            convertView: View?,
            parent: ViewGroup,
        ): View =
            super.getDropDownView(position, convertView, parent).apply {
                findViewById<TextView>(android.R.id.text1).text = decks[position].name
            }
    }

    /**
     * Holds information about the type of export.
     *
     * @param index the order of this export type in the list of possible options
     * @param layoutId the extra options views available for this export type
     */
    private enum class ExportConfiguration(
        val index: Int,
        @IdRes val layoutId: Int,
    ) {
        Collection(0, R.id.export_extras_collection),
        Apkg(1, R.id.export_extras_apkg),
        Notes(2, R.id.export_extras_notes),
        Cards(3, R.id.export_extras_cards),
        ;

        companion object {
            fun from(index: Int) = entries.first { it.index == index }
        }
    }

    /**
     * Identifier for the list of ids that can be passed to [ExportDialogFragment]. Currently either
     * notes or cards.
     */
    enum class ExportType {
        Notes,
        Cards,
    }

    companion object {
        private const val ARG_DECK_ID = "arg_deck_id"
        private const val ARG_TYPE = "arg_type"
        private const val ARG_EXPORTED_IDS = "arg_exported_ids"

        /**
         * Create a new instance of this dialog without any initial constraints(for example when
         * trying to export from [com.ichi2.anki.DeckPicker]'s menu option).
         */
        fun newInstance(): ExportDialogFragment = ExportDialogFragment()

        /**
         * Create a new instance of this dialog targeting a specific deck.
         */
        fun newInstance(did: DeckId) =
            ExportDialogFragment().apply {
                arguments = bundleOf(ARG_DECK_ID to did)
            }

        /**
         * Create a new instance of this dialog targeting a selection of cards or notes for export.
         */
        fun newInstance(
            type: ExportType,
            ids: List<Long>,
        ) = ExportDialogFragment().apply {
            arguments =
                bundleOf(
                    ARG_TYPE to type,
                    ARG_EXPORTED_IDS to ids,
                )
        }
    }
}
