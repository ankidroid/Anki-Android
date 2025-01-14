/*
 *  Copyright (c) 2025 David Allison <davidallisongithub@gmail.com>
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
import android.widget.CheckBox
import android.widget.TextView
import androidx.core.os.BundleCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.R
import com.ichi2.anki.deckpicker.EmptyCardsReport
import com.ichi2.utils.create
import timber.log.Timber

/**
 * A dialog confirming deletion of an [EmptyCardsReport].
 *
 * A user may 'keep notes', which retains the first card of each note, even if the note is empty
 */
class EmptyCardsDialog : DialogFragment() {
    private val emptyCards: EmptyCardsReport by lazy {
        requireNotNull(BundleCompat.getSerializable(requireArguments(), ARG_REPORT, EmptyCardsReport::class.java)) {
            ARG_REPORT
        }
    }

    private val keepNotesWithNoValidCards
        get() = dialog!!.findViewById<CheckBox>(R.id.empty_cards_preserve_notes).isChecked

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view =
            requireActivity().layoutInflater.inflate(R.layout.dialog_empty_cards, null).apply {
                findViewById<TextView>(R.id.dialog_message)!!.text = getString(R.string.empty_cards_count, emptyCards.size)
                findViewById<CheckBox>(R.id.empty_cards_preserve_notes)!!.apply {
                    text = TR.emptyCardsPreserveNotesCheckbox()
                    isChecked = true
                }
            }

        return MaterialAlertDialogBuilder(requireContext()).create {
            setTitle(TR.emptyCardsWindowTitle())
            setPositiveButton(TR.actionsDelete()) { _, _ -> deleteEmptyCards() }
            setNegativeButton(R.string.dialog_cancel) { _, _ -> Timber.i("Empty Cards dialog cancelled") }
            setView(view)
        }
    }

    private fun deleteEmptyCards() {
        Timber.i("empty cards dialog: 'delete'; keepNotes: %b", keepNotesWithNoValidCards)
        setFragmentResult(
            REQUEST_KEY,
            bundleOf(
                RESULT_REPORT_KEY to emptyCards,
                RESULT_KEEP_NOTES_KEY to keepNotesWithNoValidCards,
            ),
        )
    }

    companion object {
        const val REQUEST_KEY: String = "emptyCardsDialog"
        const val RESULT_REPORT_KEY = "bundle"
        const val RESULT_KEEP_NOTES_KEY = "keepNotesWithNoValidCards"

        private const val ARG_REPORT = "report"

        fun createInstance(emptyCardsReport: EmptyCardsReport): DialogFragment =
            EmptyCardsDialog().apply {
                arguments =
                    bundleOf(
                        ARG_REPORT to emptyCardsReport,
                    )
            }
    }
}
