package com.ichi2.anki.export

import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.compose.runtime.*
import androidx.compose.ui.platform.ComposeView
import androidx.core.os.BundleCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import anki.cards.cardIds
import anki.generic.Empty
import anki.import_export.ExportLimit
import anki.import_export.exportLimit
import anki.notes.noteIds
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.DeckSpinnerSelection
import com.ichi2.anki.R
import com.ichi2.anki.common.time.TimeManager
import com.ichi2.anki.common.time.getTimestamp
import com.ichi2.anki.exportApkgPackage
import com.ichi2.anki.exportCollectionPackage
import com.ichi2.anki.exportSelectedCards
import com.ichi2.anki.exportSelectedNotes
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.libanki.DeckNameId
import com.ichi2.anki.requireAnkiActivity
import com.ichi2.compat.CompatHelper.Companion.getSerializableCompat
import java.io.File

class ExportDialogFragment : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val activity = requireActivity()
        val extraDid = arguments?.getLong(ARG_DECK_ID, -1)
        val extraType: ExportType? = arguments?.getSerializableCompat(ARG_TYPE)

        val exportFormats = listOf(
            "${CollectionManager.TR.exportingAnkiCollectionPackage()} (.colpkg)",
            "${CollectionManager.TR.exportingAnkiDeckPackage()} (.apkg)",
            "${CollectionManager.TR.exportingNotesInPlainText()} (.txt)",
            "${CollectionManager.TR.exportingCardsInPlainText()} (.txt)",
        )

        return AlertDialog.Builder(activity).setView(
                ComposeView(activity).apply {
                    setContent {
                        val selectedFormatIndex = remember {
                            mutableIntStateOf(if ((extraDid != null && extraDid != -1L) || extraType != null) 1 else 0)
                        }
                        val decks = remember { mutableStateOf<List<DeckNameId>>(emptyList()) }
                        val selectedDeck = remember { mutableStateOf<DeckNameId?>(null) }
                        val decksLoading = remember { mutableStateOf(true) }

                        val showDeckSelector =
                            remember { mutableStateOf(selectedFormatIndex.value != 0 && extraType == null) }
                        val showSelectedNotesLabel =
                            remember { mutableStateOf(selectedFormatIndex.value != 0 && extraType != null) }

                        val collectionState = remember { mutableStateOf(CollectionExportState()) }
                        val apkgState = remember { mutableStateOf(ApkgExportState()) }
                        val notesState = remember { mutableStateOf(NotesExportState()) }
                        val cardsState = remember { mutableStateOf(CardsExportState()) }

                        LaunchedEffect(Unit) {
                            decksLoading.value = true
                            val allDecks = mutableListOf(
                                DeckNameId(
                                    requireActivity().getString(R.string.card_browser_all_decks),
                                    DeckSpinnerSelection.ALL_DECKS_ID,
                                ),
                            )
                            allDecks.addAll(withCol { it.decks.allNamesAndIds(false) })
                            decks.value = allDecks

                            val preselectedDeck = if (extraDid != null) {
                                allDecks.find { it.id == extraDid } ?: allDecks.first()
                            } else {
                                allDecks.first()
                            }
                            selectedDeck.value = preselectedDeck
                            decksLoading.value = false
                        }


                        ExportDialog(
                            exportFormats = exportFormats,
                            selectedFormat = exportFormats[selectedFormatIndex.value],
                            onFormatSelected = { format ->
                                val index = exportFormats.indexOf(format)
                                selectedFormatIndex.value = index
                                showDeckSelector.value = index != 0 && extraType == null
                                showSelectedNotesLabel.value = index != 0 && extraType != null
                            },
                            decks = decks.value,
                            selectedDeck = selectedDeck.value,
                            onDeckSelected = { deck -> selectedDeck.value = deck },
                            decksLoading = decksLoading.value,
                            showDeckSelector = showDeckSelector.value,
                            showSelectedNotesLabel = showSelectedNotesLabel.value,
                            collectionState = collectionState.value,
                            onCollectionStateChanged = { collectionState.value = it },
                            apkgState = apkgState.value,
                            onApkgStateChanged = { apkgState.value = it },
                            notesState = notesState.value,
                            onNotesStateChanged = { notesState.value = it },
                            cardsState = cardsState.value,
                            onCardsStateChanged = { cardsState.value = it },
                        )
                    }

                    // Set positive button action here, capturing the state
                    (dialog as? AlertDialog)?.let { d ->
                        d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                            if (selectedFormatIndex.value != 0 && decksLoading.value) return@setOnClickListener

                            when (selectedFormatIndex.value) {
                                0 -> handleCollectionExport(collectionState.value)
                                1 -> handleAnkiPackageExport(apkgState.value, selectedDeck.value)
                                2 -> handleNotesInPlainTextExport(
                                    notesState.value,
                                    selectedDeck.value
                                )

                                3 -> handleCardsInPlainTextExport(
                                    cardsState.value,
                                    selectedDeck.value
                                )
                            }
                            d.dismiss()
                        }
                    }

                },
            ).setNegativeButton(R.string.dialog_cancel, null)
            .setPositiveButton(R.string.dialog_ok, null) // Listener is set in Compose content
            .create()
    }

    private fun handleCollectionExport(state: CollectionExportState) {
        val exportPath = File(
            getExportRootFile(),
            "${CollectionManager.TR.exportingCollection()}-${getTimestamp(TimeManager.time)}.colpkg",
        ).path
        requireAnkiActivity().exportCollectionPackage(
            exportPath,
            state.includeMedia,
            state.supportOlderVersions
        )
    }

    private fun handleAnkiPackageExport(
        state: ApkgExportState,
        selectedDeck: DeckNameId?,
    ) {
        val limits = buildExportLimit(selectedDeck)
        var packagePrefix = getNonCollectionNamePrefix(selectedDeck)
        packagePrefix = packagePrefix.replace("/", "_")
        val exportPath = File(
            getExportRootFile(),
            "$packagePrefix-${getTimestamp(TimeManager.time)}.apkg",
        ).path
        requireAnkiActivity().exportApkgPackage(
            exportPath = exportPath,
            withScheduling = state.includeScheduling,
            withDeckConfigs = state.includeDeckConfigs,
            withMedia = state.includeMedia,
            limit = limits,
            legacy = state.supportOlderVersions,
        )
    }

    private fun getNonCollectionNamePrefix(selectedDeck: DeckNameId?): String =
        when (arguments?.getSerializableCompat<ExportType>(ARG_TYPE)) {
            ExportType.Notes, ExportType.Cards -> CollectionManager.TR.exportingSelectedNotes()
            else -> selectedDeck?.name
                ?: requireActivity().getString(R.string.card_browser_all_decks)
        }

    private fun handleNotesInPlainTextExport(
        state: NotesExportState,
        selectedDeck: DeckNameId?,
    ) {
        val exportLimit = buildExportLimit(selectedDeck)
        val exportPath = File(
            getExportRootFile(),
            "${getNonCollectionNamePrefix(selectedDeck)}-${getTimestamp(TimeManager.time)}.txt",
        ).path
        requireAnkiActivity().exportSelectedNotes(
            exportPath = exportPath,
            withHtml = state.includeHtml,
            withTags = state.includeTags,
            withDeck = state.includeDeckName,
            withNotetype = state.includeNotetypeName,
            withGuid = state.includeGuid,
            limit = exportLimit,
        )
    }

    private fun handleCardsInPlainTextExport(
        state: CardsExportState,
        selectedDeck: DeckNameId?,
    ) {
        val exportLimit = buildExportLimit(selectedDeck)
        val exportPath = File(
            getExportRootFile(),
            "${getNonCollectionNamePrefix(selectedDeck)}-${getTimestamp(TimeManager.time)}.txt",
        ).path
        requireAnkiActivity().exportSelectedCards(
            exportPath = exportPath,
            withHtml = state.includeHtml,
            limit = exportLimit,
        )
    }

    private fun buildExportLimit(selectedDeck: DeckNameId?): ExportLimit =
        when (arguments?.getSerializableCompat<ExportType>(ARG_TYPE)) {
            ExportType.Notes -> {
                val selectedNotesIds = arguments?.let {
                    BundleCompat.getParcelableArrayList(it, ARG_EXPORTED_IDS, Long::class.java)
                } ?: error("Requested export for selected notes but no notes ids were passed in!")
                exportLimit { noteIds = noteIds { this.noteIds.addAll(selectedNotesIds.toList()) } }
            }

            ExportType.Cards -> {
                val selectedCardIds = arguments?.let {
                    BundleCompat.getParcelableArrayList(it, ARG_EXPORTED_IDS, Long::class.java)
                } ?: error("Requested export for selected cards but no cards ids were passed in!")
                exportLimit { cardIds = cardIds { this.cids.addAll(selectedCardIds) } }
            }

            else -> {
                if (selectedDeck == null || selectedDeck.id == DeckSpinnerSelection.ALL_DECKS_ID) {
                    exportLimit { this.wholeCollection = Empty.getDefaultInstance() }
                } else {
                    exportLimit { this.deckId = selectedDeck.id }
                }
            }
        }

    private fun getExportRootFile() = File(requireActivity().externalCacheDir, "export").also {
        it.mkdirs()
    }

    enum class ExportType {
        Notes, Cards,
    }

    companion object {
        private const val ARG_DECK_ID = "arg_deck_id"
        private const val ARG_TYPE = "arg_type"
        private const val ARG_EXPORTED_IDS = "arg_exported_ids"

        fun newInstance(): ExportDialogFragment = ExportDialogFragment()

        fun newInstance(did: DeckId) = ExportDialogFragment().apply {
            arguments = bundleOf(ARG_DECK_ID to did)
        }

        fun newInstance(
            type: ExportType,
            ids: List<Long>,
        ) = ExportDialogFragment().apply {
            arguments = bundleOf(
                ARG_TYPE to type,
                ARG_EXPORTED_IDS to ids,
            )
        }
    }
}
