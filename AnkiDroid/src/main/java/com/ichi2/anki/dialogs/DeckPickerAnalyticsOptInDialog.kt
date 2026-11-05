// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2018 Mike Hardy <mike@mikehardy.net>

package com.ichi2.anki.dialogs

import android.os.Bundle
import androidx.annotation.MainThread
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.FragmentActivity
import com.ichi2.anki.CommonString
import com.ichi2.anki.analytics.AnalyticsDialogFragment
import com.ichi2.anki.analytics.AnkiDroidUsageAnalytics
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
            title(CommonString.analytics_dialog_title)
            message(CommonString.analytics_summ)
            checkBoxPrompt(CommonString.analytics_title, isCheckedDefault = false) {}
            positiveButton(CommonString.dialog_continue) {
                AnkiDroidUsageAnalytics.isEnabled = (it as AlertDialog).getCheckBoxPrompt().isChecked
            }
            cancelable(true)
        }
    }

    companion object {
        private const val TAG = "DeckPickerAnalyticsOptInDialog"

        /** Shows one prompt per activity */
        @MainThread
        fun show(activity: FragmentActivity) {
            val manager = activity.supportFragmentManager
            if (manager.findFragmentByTag(TAG) != null) return

            // Register immediately so repeated calls cannot enqueue duplicate prompts.
            DeckPickerAnalyticsOptInDialog().showNow(manager, TAG)
        }
    }
}
