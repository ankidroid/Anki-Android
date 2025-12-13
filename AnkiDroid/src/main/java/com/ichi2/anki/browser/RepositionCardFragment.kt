/*
 * Copyright (c) 2025 lukstbit <52494258+lukstbit@users.noreply.github.com>
 *
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
package com.ichi2.anki.browser

import android.app.Dialog
import android.content.res.Configuration
import android.os.Bundle
import android.view.WindowManager
import android.widget.CheckBox
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.ichi2.anki.CardBrowser
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.R
import com.ichi2.anki.ui.internationalization.toSentenceCase
import com.ichi2.anki.utils.ext.window
import com.ichi2.utils.create
import com.ichi2.utils.customView
import com.ichi2.utils.negativeButton
import com.ichi2.utils.positiveButton
import com.ichi2.utils.textAsIntOrNull
import com.ichi2.utils.title

/**
 * Follows desktop implementation to show the same ui with all the options to reposition new
 * (currently selected) cards in [CardBrowser].
 * See https://github.com/ankitects/anki/blob/44e01ea063e6d1b812ace9c001f7ba4a8ccf4479/qt/aqt/forms/reposition.ui#L14
 * See https://github.com/ankitects/anki/blob/1fb1cbbf85c48a54c05cb4442b1b424a529cac60/qt/aqt/operations/scheduling.py#L107
 */
class RepositionCardFragment : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialogView = layoutInflater.inflate(R.layout.fragment_reposition_card, null)
        dialogView.findViewById<TextView>(R.id.queue_limits_label).text =
            """
            ${TR.browsingQueueTop(requireArguments().getInt(ARG_QUEUE_TOP))}
            ${TR.browsingQueueTop(requireArguments().getInt(ARG_QUEUE_BOTTOM))} 
            """.trimIndent()
        dialogView.findViewById<TextInputLayout>(R.id.start_input_layout).hint =
            TR.browsingStartPosition().removeSuffix(":")
        dialogView.findViewById<TextInputLayout>(R.id.step_input_layout).hint =
            TR.browsingStep().removeSuffix(":")

        val startInputEditText = dialogView.findViewById<TextInputEditText>(R.id.start_input)
        val stepInputEditText = dialogView.findViewById<TextInputEditText>(R.id.step_input)

        startInputEditText.requestFocus()
        startInputEditText.selectAll()

        val randomCheck =
            dialogView.findViewById<CheckBox>(R.id.randomize_order_check)?.apply {
                text = TR.browsingRandomizeOrder()
                isChecked = requireArguments().getBoolean(ARG_RANDOM)
            } ?: error("Unexpected missing random checkbox!")
        val shiftPositionCheck =
            dialogView.findViewById<CheckBox>(R.id.shift_position_check)?.apply {
                text = TR.browsingShiftPositionOfExistingCards()
                isChecked = requireArguments().getBoolean(ARG_SHIFT)
            } ?: error("Unexpected missing shift position checkbox!")
        val title =
            TR
                .browsingRepositionNewCards()
                .toSentenceCase(requireContext(), R.string.sentence_reposition_new_cards)
        val dialog =
            AlertDialog.Builder(requireContext()).create {
                title(text = title)
                customView(dialogView)
                negativeButton(R.string.dialog_cancel)
                positiveButton(R.string.dialog_ok) {
                    val position =
                        startInputEditText.textAsIntOrNull() ?: return@positiveButton
                    val step =
                        stepInputEditText.textAsIntOrNull() ?: return@positiveButton
                    setFragmentResult(
                        REQUEST_REPOSITION_NEW_CARDS,
                        bundleOf(
                            ARG_POSITION to position,
                            ARG_STEP to step,
                            ARG_RANDOM to randomCheck.isChecked,
                            ARG_SHIFT to shiftPositionCheck.isChecked,
                        ),
                    )
                }
            }

        // Only automatically show the keyboard in portrait mode
        // In landscape mode, the keyboard would take up too much screen space and hide the dialog
        val isPortrait = resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
        if (isPortrait) {
            dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        }

        return dialog
    }

    companion object {
        const val REQUEST_REPOSITION_NEW_CARDS = "request_repositions_new_cards"
        private const val ARG_QUEUE_TOP = "arg_queue_top"
        private const val ARG_QUEUE_BOTTOM = "arg_queue_bottom"
        const val ARG_POSITION = "arg_position"
        const val ARG_STEP = "arg_step"
        const val ARG_RANDOM = "arg_random"
        const val ARG_SHIFT = "arg_shift"

        fun newInstance(
            queueTop: Int,
            queueBottom: Int,
            random: Boolean,
            shift: Boolean,
        ) = RepositionCardFragment().apply {
            arguments =
                bundleOf(
                    ARG_QUEUE_TOP to queueTop,
                    ARG_QUEUE_BOTTOM to queueBottom,
                    ARG_RANDOM to random,
                    ARG_SHIFT to shift,
                )
        }
    }
}
