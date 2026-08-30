// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2018 Mike Hardy <mike@mikehardy.net>

package com.ichi2.anki.dialogs

import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import com.ichi2.anki.S
import com.ichi2.anki.analytics.AnalyticsDialogFragment
import com.ichi2.anki.analytics.AnkiDroidUsageAnalytics
import com.ichi2.anki.utils.ext.dismissAllDialogFragments
import com.ichi2.utils.cancelable
import com.ichi2.utils.checkBoxPrompt
import com.ichi2.utils.create
import com.ichi2.utils.getCheckBoxPrompt
import com.ichi2.utils.message
import com.ichi2.utils.positiveButton
import com.ichi2.utils.title

class DeckPickerAnalyticsOptInDialog : AnalyticsDialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): AlertDialog {
        super.onCreateDialog(savedInstanceState)
        return AlertDialog.Builder(requireActivity()).create {
            title(S.analytics_dialog_title)
            message(S.analytics_summ)
            checkBoxPrompt(S.analytics_title, isCheckedDefault = false) {}
            positiveButton(S.dialog_continue) {
                AnkiDroidUsageAnalytics.isEnabled = (it as AlertDialog).getCheckBoxPrompt().isChecked
                activity?.dismissAllDialogFragments()
            }
            cancelable(true)
            setOnCancelListener { activity?.dismissAllDialogFragments() }
        }
    }

    companion object {
        fun newInstance(): DeckPickerAnalyticsOptInDialog = DeckPickerAnalyticsOptInDialog()
    }
}
