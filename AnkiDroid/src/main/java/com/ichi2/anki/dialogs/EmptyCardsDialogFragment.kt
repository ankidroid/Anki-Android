/*
 *  Copyright (c) 2025 David Allison <davidallisongithub@gmail.com>
 *  Copyright (c) 2025 lukstbit <52494258+lukstbit@users.noreply.github.com>
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

import android.app.Dialog
import android.os.Bundle
import android.view.View
import android.widget.CheckBox
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import anki.card_rendering.EmptyCardsReport
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.DeckPicker
import com.ichi2.anki.R
import com.ichi2.anki.dialogs.EmptyCardsUiState.EmptyCardsSearchFailure
import com.ichi2.anki.dialogs.EmptyCardsUiState.EmptyCardsSearchResult
import com.ichi2.anki.dialogs.EmptyCardsUiState.SearchingForEmptyCards
import com.ichi2.anki.launchCatchingTask
import com.ichi2.anki.ui.internationalization.toSentenceCase
import com.ichi2.anki.withProgress
import com.ichi2.libanki.emptyCids
import com.ichi2.utils.message
import com.ichi2.utils.positiveButton
import com.ichi2.utils.show
import com.ichi2.utils.title
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * A dialog that searches for empty cards and presents the user with the option to delete them.
 * A user may 'keep notes', which retains the first card of each note, even if the note is empty.
 */
class EmptyCardsDialogFragment : DialogFragment() {
    private val viewModel by viewModels<EmptyCardsViewModel>()
    private val loadingContainer: View?
        get() = dialog?.findViewById(R.id.loading_container)
    private val loadingMessage: TextView?
        get() = dialog?.findViewById(R.id.text)
    private val emptyCardsResultsContainer: View?
        get() = dialog?.findViewById(R.id.empty_cards_results_container)
    private val emptyCardsResultsMessage: TextView?
        get() = dialog?.findViewById(R.id.empty_cards_results_message)
    private val keepNotesWithNoValidCards: CheckBox?
        get() = dialog?.findViewById(R.id.preserve_notes)

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        bindToState()
        viewModel.searchForEmptyCards()
        val dialogView = layoutInflater.inflate(R.layout.dialog_empty_cards, null)
        dialogView.findViewById<CheckBox>(R.id.preserve_notes)?.text =
            TR.emptyCardsPreserveNotesCheckbox()

        return AlertDialog
            .Builder(requireContext())
            .show {
                setTitle(
                    TR
                        .emptyCardsWindowTitle()
                        .toSentenceCase(context, R.string.sentence_empty_cards),
                )
                setPositiveButton(R.string.dialog_ok) { _, _ ->
                    val state = viewModel.uiState.value
                    if (state is EmptyCardsSearchResult) {
                        // this dialog is only shown from DeckPicker so we use it directly to avoid
                        // fragment result listeners and the possibility of the search report
                        // exceeding the result Bundle's limits
                        if (state.emptyCardsReport.emptyCids().isEmpty()) return@setPositiveButton
                        (requireActivity() as DeckPicker).startDeletingEmptyCards(
                            state.emptyCardsReport,
                            keepNotesWithNoValidCards?.isChecked ?: true,
                        )
                    }
                }
                setNegativeButton(R.string.dialog_cancel) { _, _ ->
                    Timber.i("Empty cards dialog cancelled")
                }
                setView(dialogView)
            }.also {
                // the initial start state is a loading state as we are looking for the empty cards,
                // so there's no "action" for ok just yet
                it.positiveButton.isEnabled = false
            }
    }

    private fun bindToState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    when (state) {
                        is SearchingForEmptyCards -> {
                            loadingMessage?.text = getString(R.string.emtpy_cards_finding)
                            loadingContainer?.isVisible = true
                            emptyCardsResultsContainer?.isVisible = false
                            (dialog as? AlertDialog)?.positiveButton?.apply {
                                isEnabled = false
                                text = getString(R.string.dialog_ok)
                            }
                        }

                        is EmptyCardsSearchResult -> {
                            loadingContainer?.isVisible = false
                            val emptyCards = state.emptyCardsReport.emptyCids()
                            if (emptyCards.isEmpty()) {
                                emptyCardsResultsMessage?.text = TR.emptyCardsNotFound()
                                // nothing to delete so also hide the preserve notes check box
                                keepNotesWithNoValidCards?.isVisible = false
                                (dialog as? AlertDialog)?.positiveButton?.text =
                                    getString(R.string.dialog_ok)
                            } else {
                                keepNotesWithNoValidCards?.isVisible = true
                                emptyCardsResultsMessage?.text =
                                    getString(R.string.empty_cards_count, emptyCards.size)
                                (dialog as? AlertDialog)?.positiveButton?.text =
                                    getString(R.string.dialog_positive_delete)
                            }
                            (dialog as? AlertDialog)?.positiveButton?.isEnabled = true
                            emptyCardsResultsContainer?.isVisible = true
                        }

                        is EmptyCardsSearchFailure -> {
                            // the dialog is informational so there's nothing to do but show the
                            // error and exit
                            AlertDialog.Builder(requireActivity()).show {
                                title(R.string.vague_error)
                                message(text = state.throwable.toString())
                                positiveButton(R.string.dialog_ok) { }
                            }
                            dismissNow()
                        }
                    }
                }
            }
        }
    }

    companion object {
        const val TAG = "EmptyCardsDialog"
    }
}

// TODO the fragment should just send a fragment result to the DeckPicker with the report and keep
//  notes flag(currently the report can exceed the transport Bundle capacity so we call it directly)
fun DeckPicker.startDeletingEmptyCards(
    report: EmptyCardsReport,
    keepNotes: Boolean,
) {
    Timber.i(
        "Starting to delete found %d empty cards, keepNotes: %b",
        report.emptyCids().size,
        keepNotes,
    )
    launchCatchingTask {
        withProgress(TR.emptyCardsDeleting()) {
            viewModel.deleteEmptyCards(report, keepNotes).join()
        }
    }
}
