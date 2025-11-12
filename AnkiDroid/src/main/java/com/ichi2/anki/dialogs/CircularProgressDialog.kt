/*
 * Copyright (c) 2025 AnkiDroid Contributors
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

package com.ichi2.anki.dialogs

import android.content.Context
import android.content.DialogInterface
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.ichi2.anki.R

class CircularProgressDialog(
    context: Context,
) {
    private val dialog: AlertDialog
    private val progressIndicator: CircularProgressIndicator
    private val percentageText: TextView
    private val messageText: TextView

    private var isDeterminate = false
    private var currentProgress = 0
    private var maxProgress = 100

    init {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_circular_progress, null)
        progressIndicator = view.findViewById(R.id.circular_progress_indicator)
        percentageText = view.findViewById(R.id.progress_percentage_text)
        messageText = view.findViewById(R.id.progress_message_text)

        dialog =
            AlertDialog
                .Builder(context)
                .setView(view)
                .setCancelable(false)
                .create()
    }

    fun setMessage(message: String) {
        messageText.text = message
        messageText.visibility = if (message.isNotEmpty()) View.VISIBLE else View.GONE
        updateAccessibility()
    }

    fun setMessage(
        @StringRes messageId: Int,
    ) {
        setMessage(messageText.context.getString(messageId))
    }

    fun setCancelable(cancelable: Boolean) {
        dialog.setCancelable(cancelable)
    }

    fun setOnCancelListener(listener: DialogInterface.OnCancelListener?) {
        dialog.setOnCancelListener(listener)
    }

    fun setButton(
        @StringRes textId: Int,
        listener: DialogInterface.OnClickListener,
    ) {
        dialog.setButton(DialogInterface.BUTTON_NEGATIVE, messageText.context.getString(textId), listener)
    }

    fun setIndeterminate(determinate: Boolean) {
        isDeterminate = !determinate
        progressIndicator.isIndeterminate = !isDeterminate

        if (isDeterminate) {
            percentageText.visibility = View.VISIBLE
            updateProgress()
        } else {
            percentageText.visibility = View.GONE
        }
        updateAccessibility()
    }

    fun setProgress(progress: Int) {
        currentProgress = progress.coerceIn(0, maxProgress)
        if (isDeterminate) {
            updateProgress()
        }
    }

    fun setMax(max: Int) {
        maxProgress = max.coerceAtLeast(1)
        if (isDeterminate) {
            updateProgress()
        }
    }

    fun setProgress(
        current: Int,
        total: Int,
    ) {
        setMax(total)
        setProgress(current)
    }

    private fun updateProgress() {
        val percentage =
            if (maxProgress > 0) {
                (currentProgress * 100) / maxProgress
            } else {
                0
            }

        progressIndicator.setProgressCompat(percentage, true)
        percentageText.text = messageText.context.getString(R.string.progress_percentage, percentage)
        updateAccessibility()
    }

    private fun updateAccessibility() {
        val contentDescription =
            if (isDeterminate) {
                val percentage =
                    if (maxProgress > 0) {
                        (currentProgress * 100) / maxProgress
                    } else {
                        0
                    }
                messageText.context.getString(
                    R.string.progress_accessibility_determinate,
                    messageText.text,
                    percentage,
                )
            } else {
                messageText.context.getString(
                    R.string.progress_accessibility_indeterminate,
                    messageText.text,
                )
            }
        progressIndicator.contentDescription = contentDescription
    }

    fun show() {
        if (!dialog.isShowing) {
            dialog.show()
        }
    }

    fun dismiss() {
        if (dialog.isShowing) {
            dialog.dismiss()
        }
    }

    fun isShowing(): Boolean = dialog.isShowing

    companion object {
        fun show(
            context: Context,
            message: String,
        ): CircularProgressDialog =
            CircularProgressDialog(context).apply {
                setMessage(message)
                setIndeterminate(true)
                show()
            }

        fun show(
            context: Context,
            @StringRes messageId: Int,
        ): CircularProgressDialog = show(context, context.getString(messageId))
    }
}
