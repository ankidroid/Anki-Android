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
import com.afollestad.materialdialogs.DialogAction
import com.afollestad.materialdialogs.MaterialDialog
import com.ichi2.anki.DeckPicker
import com.ichi2.anki.R
import com.ichi2.anki.analytics.AnalyticsDialogFragment
import com.ichi2.anki.analytics.UsageAnalytics

class DeckPickerAnalyticsOptInDialog : AnalyticsDialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): MaterialDialog {
        super.onCreate(savedInstanceState)
        val res = resources
        return MaterialDialog.Builder(requireActivity())
            .title(res.getString(R.string.analytics_dialog_title))
            .content(res.getString(R.string.analytics_summ))
            .checkBoxPrompt(res.getString(R.string.analytics_title), true, null)
            .positiveText(R.string.dialog_continue)
            .onPositive { dialog: MaterialDialog, _: DialogAction? ->
                UsageAnalytics.setEnabled(dialog.isPromptCheckBoxChecked)
                (activity as DeckPicker?)!!.dismissAllDialogFragments()
            }
            .cancelable(true)
            .cancelListener { (activity as DeckPicker?)!!.dismissAllDialogFragments() }
            .show()
    }

    companion object {
        @JvmStatic
        fun newInstance(): DeckPickerAnalyticsOptInDialog {
            return DeckPickerAnalyticsOptInDialog()
        }
    }
}
