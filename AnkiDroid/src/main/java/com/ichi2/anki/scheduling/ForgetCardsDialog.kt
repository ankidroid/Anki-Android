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
import android.net.Uri
import android.os.Bundle
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.R
import com.ichi2.anki.launchCatchingTask
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.anki.utils.openUrl
import com.ichi2.anki.withProgress
import com.ichi2.annotations.NeedsTest
import com.ichi2.libanki.CardId
import com.ichi2.libanki.sched.Scheduler
import com.ichi2.libanki.undoableOp
import com.ichi2.utils.create
import com.ichi2.utils.negativeButton
import com.ichi2.utils.positiveButton
import com.ichi2.utils.titleWithHelpIcon
import timber.log.Timber

/**
 * Dialog for [Scheduler.forgetCards]
 *
 * Previously known as 'Reset Cards'.
 * Moves cards back to the [new queue](https://docs.ankiweb.net/getting-started.html#types-of-cards)
 *
 * docs:
 * * https://docs.ankiweb.net/studying.html#editing-and-more
 * * https://docs.ankiweb.net/browsing.html#cards
 */
// TODO: Rename the labels opening this class once Anki Desktop's translations support plurals
@NeedsTest("all")
class ForgetCardsDialog : DialogFragment() {

    /**
     * Resets cards back to their original positions in the new queue
     *
     * This only works if the card was first studied using SchedV3 with backend >= 2.1.50+
     *
     * If `false`, cards are moved to the end of the new queue
     */
    private var restoreOriginalPositionIfPossible = true
        set(value) {
            Timber.i("restoreOriginalPositionIfPossible: %b", value)
            field = value
        }

    /**
     * Set the review and failure counters back to zero.
     *
     * This does not affect CardInfo/review history
     */
    private var resetRepetitionAndLapseCounts = false
        set(value) {
            Timber.i("resetRepetitionAndLapseCounts: %b", value)
            field = value
        }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        if (savedInstanceState != null) {
            restoreOriginalPositionIfPossible = savedInstanceState.getBoolean(ARG_RESTORE_ORIGINAL, true)
            resetRepetitionAndLapseCounts = savedInstanceState.getBoolean(ARG_RESET_REPETITION, false)
        }
        val contentView = layoutInflater.inflate(R.layout.dialog_forget_cards, null).apply {
            findViewById<MaterialCheckBox>(R.id.restore_original_position)!!.apply {
                isChecked = restoreOriginalPositionIfPossible
                setOnCheckedChangeListener { _, isChecked ->
                    restoreOriginalPositionIfPossible = isChecked
                }
                text = TR.schedulingRestorePosition()
            }
            findViewById<MaterialCheckBox>(R.id.reset_lapse_counts)!!.apply {
                isChecked = resetRepetitionAndLapseCounts
                setOnCheckedChangeListener { _, isChecked ->
                    resetRepetitionAndLapseCounts = isChecked
                }
                text = TR.schedulingResetCounts()
            }
        }
        return MaterialAlertDialogBuilder(requireContext()).create {
            // BUG: this is 'Reset Card'/'Forget Card' in Anki Desktop (24.04)
            // title(text = TR.actionsForgetCard().toSentenceCase(R.string.sentence_forget_cards))
            // "Reset card progress" is less explicit on the singular/plural dimension
            titleWithHelpIcon(stringRes = R.string.reset_card_dialog_title) {
                requireActivity().openUrl(Uri.parse(getString(R.string.link_help_forget_cards)))
            }
            positiveButton(R.string.dialog_ok) {
                parentFragmentManager.setFragmentResult(
                    REQUEST_KEY_FORGET,
                    bundleOf(
                        ARG_RESTORE_ORIGINAL to restoreOriginalPositionIfPossible,
                        ARG_RESET_REPETITION to resetRepetitionAndLapseCounts
                    )
                )
            }
            negativeButton(R.string.dialog_cancel)
            setView(contentView)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(ARG_RESTORE_ORIGINAL, restoreOriginalPositionIfPossible)
        outState.putBoolean(ARG_RESET_REPETITION, resetRepetitionAndLapseCounts)
    }

    companion object {
        const val REQUEST_KEY_FORGET = "request_key_forget_cards"
        const val ARG_RESTORE_ORIGINAL = "key_restore_original"
        const val ARG_RESET_REPETITION = "key_reset_repetition"
    }
}

/**
 * Listen for requests from [ForgetCardsDialog] and triggers backend calls to reset progress for
 * current selected/reviewed card/cards. Callers need to supply the list of cards ids.
 *
 * @param cardsIdsProducer lambda which returns the list of cards for which to reset the progress
 */
internal fun AnkiActivity.registerOnForgetHandler(cardsIdsProducer: suspend () -> List<CardId>) {
    supportFragmentManager.setFragmentResultListener(ForgetCardsDialog.REQUEST_KEY_FORGET, this) { _, bundle: Bundle ->
        forgetCards(
            cardsIdsProducer = cardsIdsProducer,
            restoreOriginalPositionIfPossible = bundle.getBoolean(ForgetCardsDialog.ARG_RESTORE_ORIGINAL),
            resetRepetitionAndLapseCounts = bundle.getBoolean(ForgetCardsDialog.ARG_RESET_REPETITION)
        )
    }
}

private fun AnkiActivity.forgetCards(
    cardsIdsProducer: suspend () -> List<CardId>,
    restoreOriginalPositionIfPossible: Boolean,
    resetRepetitionAndLapseCounts: Boolean
) = launchCatchingTask {
    val cardsIds = cardsIdsProducer()
    Timber.i(
        "forgetting %d cards, restorePosition = %b, resetCounts = %b",
        cardsIds.size,
        restoreOriginalPositionIfPossible,
        resetRepetitionAndLapseCounts
    )
    // NICE_TO_HAVE: Display a snackbar if the activity is recreated while this executes
    withProgress {
        undoableOp {
            sched.forgetCards(
                cardsIds,
                restorePosition = restoreOriginalPositionIfPossible,
                resetCounts = resetRepetitionAndLapseCounts
            )
        }
    }
    Timber.d("forgot %d cards", cardsIds.size)
    showSnackbar(
        resources.getQuantityString(
            R.plurals.reset_cards_dialog_acknowledge,
            cardsIds.size,
            cardsIds.size
        ),
        Snackbar.LENGTH_SHORT
    )
}
