package com.ichi2.anki.dialogs

import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AppCompatDialogFragment
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.core.os.bundleOf
import androidx.fragment.app.activityViewModels
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.ichi2.anki.R
import com.ichi2.anki.browser.BrowserColumnSelectionFragment
import com.ichi2.anki.browser.CardBrowserViewModel
import com.ichi2.anki.model.CardsOrNotes
import com.ichi2.anki.theme.AnkiDroidTheme
import timber.log.Timber

class BrowserOptionsDialog : AppCompatDialogFragment() {
    private val viewModel: CardBrowserViewModel by activityViewModels()

    private var cardsOrNotes: CardsOrNotes by mutableStateOf(CardsOrNotes.CARDS)
    private var isTruncated: Boolean by mutableStateOf(false)
    private var shouldIgnoreAccents: Boolean by mutableStateOf(false)

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        cardsOrNotes =
            when (requireArguments().getBoolean(CARDS_OR_NOTES_KEY)) {
                true -> CardsOrNotes.CARDS
                else -> CardsOrNotes.NOTES
            }
        isTruncated = requireArguments().getBoolean(IS_TRUNCATED_KEY)
        shouldIgnoreAccents = viewModel.shouldIgnoreAccents

        return MaterialAlertDialogBuilder(requireActivity())
            .setTitle(R.string.browser_options_dialog_heading)
            .setNegativeButton(R.string.dialog_cancel, null)
            .setPositiveButton(R.string.dialog_ok) { _, _ ->
                viewModel.setCardsOrNotes(cardsOrNotes)
                viewModel.setTruncated(isTruncated)
                viewModel.setIgnoreAccents(shouldIgnoreAccents)
            }.setView(
                ComposeView(requireActivity()).apply {
                    setContent {
                        AnkiDroidTheme {
                            BrowserOptions(
                                onCardsModeSelected = { cardsOrNotes = CardsOrNotes.CARDS },
                                onNotesModeSelected = { cardsOrNotes = CardsOrNotes.NOTES },
                                initialMode = if (cardsOrNotes == CardsOrNotes.CARDS) 0 else 1,
                                onTruncateChanged = { isTruncated = it },
                                initialTruncate = isTruncated,
                                onIgnoreAccentsChanged = { shouldIgnoreAccents = it },
                                initialIgnoreAccents = shouldIgnoreAccents,
                                onManageColumnsClicked = {
                                    val dialog = BrowserColumnSelectionFragment.createInstance(viewModel.cardsOrNotes)
                                    dialog.show(requireActivity().supportFragmentManager, null)
                                },
                                onRenameFlagClicked = {
                                    val flagRenameDialog = FlagRenameDialog()
                                    flagRenameDialog.show(parentFragmentManager, "FlagRenameDialog")
                                    dismiss()
                                },
                            )
                        }
                    }
                },
            ).create()
    }

    companion object {
        private const val CARDS_OR_NOTES_KEY = "cardsOrNotes"
        private const val IS_TRUNCATED_KEY = "isTruncated"

        fun newInstance(
            cardsOrNotes: CardsOrNotes,
            isTruncated: Boolean,
        ): BrowserOptionsDialog {
            Timber.i("BrowserOptionsDialog::newInstance")
            return BrowserOptionsDialog().apply {
                arguments =
                    bundleOf(
                        CARDS_OR_NOTES_KEY to (cardsOrNotes == CardsOrNotes.CARDS),
                        IS_TRUNCATED_KEY to isTruncated,
                    )
            }
        }
    }
}
