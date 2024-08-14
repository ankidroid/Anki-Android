/*
 *  Copyright (c) 2024 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki.scheduling

import android.app.Dialog
import android.os.Bundle
import androidx.annotation.CheckResult
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.R
import com.ichi2.anki.launchCatchingTask
import com.ichi2.anki.requireAnkiActivity
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.anki.withProgress
import com.ichi2.annotations.NeedsTest
import com.ichi2.libanki.CardId
import com.ichi2.libanki.sched.Scheduler
import com.ichi2.utils.create
import com.ichi2.utils.negativeButton
import com.ichi2.utils.positiveButton
import com.ichi2.utils.title
import timber.log.Timber

/**
 * Dialog for [Scheduler.forgetCards]
 *
 * Previously known as 'Reset Cards'
 *
 * @see ForgetCardsViewModel
 */
// TODO: Rename the labels opening this class once Anki Desktop's translations support plurals
@NeedsTest("all")
class ForgetCardsDialog : DialogFragment() {
    val viewModel: ForgetCardsViewModel by activityViewModels<ForgetCardsViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val cardIds = requireNotNull(requireArguments().getLongArray(ARG_CARD_IDS)) { ARG_CARD_IDS }
        viewModel.init(cardIds.toList())
        Timber.d("Reset cards dialog: %d card(s)", cardIds.size)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return MaterialAlertDialogBuilder(requireContext()).create {
            // BUG: this is 'Reset Card'/'Forget Card' in Anki Desktop (24.04)
            // title(text = TR.actionsForgetCard().toSentenceCase(R.string.sentence_forget_cards))
            // "Reset card progress" is less explicit on the singular/plural dimension
            title(text = getString(R.string.reset_card_dialog_title))
            positiveButton(R.string.dialog_ok) { launchForgetCards() }
            negativeButton(R.string.dialog_cancel)
            setView(R.layout.dialog_forget_cards)
        }.apply {
            show() // needed for setupDialog
        }
    }

    override fun setupDialog(dialog: Dialog, style: Int) {
        super.setupDialog(dialog, style)
        val alertDialog = dialog as AlertDialog

        alertDialog.findViewById<MaterialCheckBox>(R.id.restore_original_position)!!.apply {
            isChecked = viewModel.restoreOriginalPositionIfPossible
            setOnCheckedChangeListener { _, isChecked ->
                viewModel.restoreOriginalPositionIfPossible = isChecked
            }
            text = TR.schedulingRestorePosition()
        }
        alertDialog.findViewById<MaterialCheckBox>(R.id.reset_lapse_counts)!!.apply {
            isChecked = viewModel.resetRepetitionAndLapseCounts
            setOnCheckedChangeListener { _, isChecked ->
                viewModel.resetRepetitionAndLapseCounts = isChecked
            }
            text = TR.schedulingResetCounts()
        }
    }

    private fun launchForgetCards() = requireAnkiActivity().forgetCards(viewModel)

    companion object {
        const val ARG_CARD_IDS = "ARGS_CARD_IDS"

        @CheckResult
        fun newInstance(cardIds: List<CardId>) = ForgetCardsDialog().apply {
            arguments = bundleOf(ARG_CARD_IDS to cardIds.toLongArray())
            Timber.i("Showing 'forget cards' dialog for %d cards", cardIds.size)
        }
    }
}

// this can outlive the lifetime of the fragment
private fun AnkiActivity.forgetCards(viewModel: ForgetCardsViewModel) = this.launchCatchingTask {
    // NICE_TO_HAVE: Display a snackbar if the activity is recreated while this executes
    val count = withProgress(resources.getString(R.string.dialog_processing)) {
        // this should be run on the viewModel
        viewModel.resetCardsAsync().await()
    }

    Timber.d("forgot %d cards", count)

    showSnackbar(
        resources.getQuantityString(
            R.plurals.reset_cards_dialog_acknowledge,
            count,
            count
        ),
        Snackbar.LENGTH_SHORT
    )
}
