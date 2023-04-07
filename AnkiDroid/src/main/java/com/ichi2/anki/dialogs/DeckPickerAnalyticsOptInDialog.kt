/****************************************************************************************
 * Copyright (c) 2018 Mike Hardy <mike@mikehardy.net>                                   *
 *                                                                                      *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 *                                                                                      *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/

package com.ichi2.anki.dialogs

import android.os.Bundle
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.checkbox.checkBoxPrompt
import com.afollestad.materialdialogs.checkbox.isCheckPromptChecked
import com.ichi2.anki.DeckPicker
import com.ichi2.anki.R
import com.ichi2.anki.analytics.AnalyticsDialogFragment
import com.ichi2.anki.analytics.UsageAnalytics

class DeckPickerAnalyticsOptInDialog : AnalyticsDialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): AlertDialog{
        super.onCreate(savedInstanceState)
        return MaterialDialog(requireActivity()).show {
            title(R.string.analytics_dialog_title)
            message(R.string.analytics_summ)
            checkBoxPrompt(R.string.analytics_title, isCheckedDefault = true, onToggle = null)
            positiveButton(R.string.dialog_continue) {
                UsageAnalytics.isEnabled = it.isCheckPromptChecked()
                (activity as DeckPicker).dismissAllDialogFragments()
            }
            cancelable(true)
            setOnCancelListener { (activity as DeckPicker).dismissAllDialogFragments() }
        }
    }

    companion object {
        fun newInstance(): DeckPickerAnalyticsOptInDialog {
            return DeckPickerAnalyticsOptInDialog()
        }
    }
}
