/*
 *  Copyright (c) 2025 Eric Li <ericli3690@gmail.com>
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

package com.ichi2.anki.reviewreminders

import android.app.Dialog
import android.os.Bundle
import android.widget.TimePicker
import androidx.appcompat.app.AlertDialog
import androidx.core.os.BundleCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import com.ichi2.anki.R
import com.ichi2.utils.customView
import com.ichi2.utils.negativeButton
import com.ichi2.utils.positiveButton

class TimePickerDialog : DialogFragment() {
    private lateinit var timePicker: TimePicker

    /**
     * The initial time to display on this TimePicker, retrieved from arguments and set by [getInstance].
     */
    private val reviewReminderTime: ReviewReminderTime by lazy {
        requireNotNull(
            BundleCompat.getParcelable(requireArguments(), REVIEW_REMINDER_TIME_ARGUMENTS_KEY, ReviewReminderTime::class.java),
        ) {
            "Review reminder time cannot be null"
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        super.onCreateDialog(savedInstanceState)
        val contentView = layoutInflater.inflate(R.layout.time_picker_dialog, null)

        val dialog =
            AlertDialog
                .Builder(requireActivity())
                .positiveButton(R.string.dialog_ok) { onSubmit() }
                .negativeButton(R.string.dialog_cancel)
                .customView(contentView)
                .create()

        timePicker = contentView.findViewById(R.id.time_picker)
        timePicker.hour = reviewReminderTime.hour
        timePicker.minute = reviewReminderTime.minute

        return dialog
    }

    private fun onSubmit() {
        val newTime = ReviewReminderTime(timePicker.hour, timePicker.minute)
        setFragmentResult(
            AddEditReminderDialog.TIME_FRAGMENT_RESULT_REQUEST_KEY,
            Bundle().apply {
                putParcelable(AddEditReminderDialog.TIME_FRAGMENT_RESULT_REQUEST_KEY, newTime)
            },
        )
    }

    companion object {
        /**
         * Arguments key for the initial time to display on this TimePicker.
         */
        private const val REVIEW_REMINDER_TIME_ARGUMENTS_KEY = "review_reminder_time"

        /**
         * Creates a new instance of this dialog with the given initial time.
         */
        fun getInstance(reviewReminderTime: ReviewReminderTime): TimePickerDialog =
            TimePickerDialog().apply {
                arguments =
                    Bundle().apply {
                        putParcelable(REVIEW_REMINDER_TIME_ARGUMENTS_KEY, reviewReminderTime)
                    }
            }
    }
}
