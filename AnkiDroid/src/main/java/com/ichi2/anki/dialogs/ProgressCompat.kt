/*
 * Copyright (c) 2025 Shaan Narendran <shaannaren06@gmail.com>
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
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.ichi2.anki.R

/**
 * Replacement for [android.app.ProgressDialog] deprecation on API 26+. This class should be used
 * instead of the platform [android.app.ProgressDialog].
 *
 * @see android.app.ProgressDialog
 */
class ProgressCompat(
    private val context: Context,
) {
    companion object {
        private val PROGRESS_PATTERN = Regex("(\\d+)\\s*/\\s*(\\d+)")
    }

    private var dialog: AlertDialog? = null
    private var circularIndicator: CircularProgressIndicator? = null
    private var messageView: TextView? = null
    private var pendingMessage: CharSequence? = null
    private var negativeButtonAction: Pair<String, (DialogInterface, Int) -> Unit>? = null
    private var onCancelListener: DialogInterface.OnCancelListener? = null
    private var isCancelable = false
    private var max = 100
    private var progress = 0

    private val handler by lazy { Handler(Looper.getMainLooper()) }

    private inline fun runOnUi(crossinline action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) action() else handler.post { action() }
    }

    fun setCancelable(flag: Boolean) {
        isCancelable = flag
    }

    fun setOnCancelListener(listener: DialogInterface.OnCancelListener?) {
        onCancelListener = listener
    }

    fun setButton(
        whichButton: Int,
        text: CharSequence,
        listener: (DialogInterface, Int) -> Unit,
    ) {
        if (whichButton == DialogInterface.BUTTON_NEGATIVE) {
            negativeButtonAction = text.toString() to listener
        }
    }

    /**
     * Updates the message text.
     *
     * This method attempts to parse progress numbers from the [message] string.
     * If the message contains a pattern like "50 / 100", the progress bar will
     * automatically update to match those values.
     *
     * @param message The text to display, or null to clear.
     */
    fun setMessage(message: CharSequence?) =
        runOnUi {
            pendingMessage = message
            messageView?.text = message

            message?.let { msg ->
                PROGRESS_PATTERN.find(msg)?.destructured?.let { (curr, total) ->
                    runCatching {
                        val t = total.toInt()
                        if (max != t) setMaxInternal(t)
                        setProgressInternal(curr.toInt())
                    }
                    return@runOnUi
                }
                // If no digits found, ensure we are in indeterminate mode (spinner)
                if (circularIndicator?.isIndeterminate == false && progress == 0) {
                    circularIndicator?.isIndeterminate = true
                }
            }
        }

    fun setMax(max: Int) = runOnUi { setMaxInternal(max) }

    private fun setMaxInternal(max: Int) {
        this.max = max
        circularIndicator?.max = max
    }

    fun setProgress(value: Int) = runOnUi { setProgressInternal(value) }

    private fun setProgressInternal(value: Int) {
        progress = value
        circularIndicator?.apply {
            if (isIndeterminate) isIndeterminate = false
            setProgressCompat(value, true)
        }
    }

    fun incrementProgressBy(diff: Int) = runOnUi { setProgressInternal(progress + diff) }

    var isIndeterminate: Boolean
        get() = circularIndicator?.isIndeterminate ?: false
        set(value) = runOnUi { circularIndicator?.isIndeterminate = value }

    fun show() =
        runOnUi {
            if (dialog?.isShowing == true) return@runOnUi

            val view = LayoutInflater.from(context).inflate(R.layout.dialog_circular_progress, null)
            circularIndicator =
                view.findViewById<CircularProgressIndicator>(R.id.circular_progress).apply {
                    isIndeterminate = false
                    max = this@ProgressCompat.max
                    setProgressCompat(progress, false)
                }
            messageView = view.findViewById<TextView>(R.id.progress_message).apply { text = pendingMessage }

            dialog =
                MaterialAlertDialogBuilder(context)
                    .setView(view)
                    .setCancelable(isCancelable)
                    .setOnCancelListener(onCancelListener)
                    .apply {
                        negativeButtonAction?.let { (text, listener) -> setNegativeButton(text, listener) }
                    }.create()

            dialog?.show()
        }

    fun dismiss() =
        runOnUi {
            runCatching { dialog?.dismiss() }
        }
}
