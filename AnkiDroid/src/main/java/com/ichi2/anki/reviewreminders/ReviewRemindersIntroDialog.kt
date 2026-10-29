// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.reviewreminders

import android.os.Bundle
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import com.ichi2.anki.CommonString
import com.ichi2.anki.analytics.AnalyticsDialogFragment
import com.ichi2.anki.databinding.DialogReviewRemindersIntroBinding
import com.ichi2.utils.create
import com.ichi2.utils.customView
import com.ichi2.utils.positiveButton
import com.ichi2.utils.title

/**
 * Introduces review reminders to a user who had a notification in the legacy notifications system,
 * which review reminders replace.
 *
 * @see com.ichi2.anki.settings.PrefsRepository.shouldShowReviewRemindersIntro
 */
class ReviewRemindersIntroDialog : AnalyticsDialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): AlertDialog =
        AlertDialog.Builder(requireContext()).create {
            title(CommonString.review_reminders_title)
            customView(DialogReviewRemindersIntroBinding.inflate(LayoutInflater.from(context)).root)
            positiveButton(CommonString.dialog_ok)
        }
}
