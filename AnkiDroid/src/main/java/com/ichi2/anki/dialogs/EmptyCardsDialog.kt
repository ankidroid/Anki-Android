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
import androidx.appcompat.app.AlertDialog
import androidx.core.os.BundleCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.R
import com.ichi2.anki.deckpicker.EmptyCards
import com.ichi2.utils.show

class EmptyCardsDialog : DialogFragment() {
    private val emptyCards: EmptyCards by lazy {
        requireNotNull(BundleCompat.getSerializable(requireArguments(), ARG_REPORT, EmptyCards::class.java)) {
            ARG_REPORT
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
        AlertDialog.Builder(requireContext()).show {
            setTitle(TR.emptyCardsWindowTitle())
            setMessage(getString(R.string.empty_cards_count, emptyCards.size))
            setPositiveButton(R.string.dialog_positive_delete) { _, _ -> deleteEmptyCards() }
            setNegativeButton(R.string.dialog_cancel) { _, _ -> }
        }

    private fun deleteEmptyCards() {
        setFragmentResult(
            REQUEST_KEY,
            bundleOf(
                RESULT_REPORT_KEY to emptyCards,
            ),
        )
    }

    companion object {
        const val REQUEST_KEY: String = "emptyCardsDialog"
        const val RESULT_REPORT_KEY = "bundle"

        private const val ARG_REPORT = "report"

        fun createInstance(emptyCardsReport: EmptyCards): DialogFragment =
            EmptyCardsDialog().apply {
                arguments =
                    bundleOf(
                        ARG_REPORT to emptyCardsReport,
                    )
            }
    }
}
