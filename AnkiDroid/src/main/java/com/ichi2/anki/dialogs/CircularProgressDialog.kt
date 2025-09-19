/***************************************************************************************
 * Copyright (c) 2024 Ankitects Pty Ltd <https://apps.ankiweb.net>                       *
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

import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.ichi2.anki.R
import kotlin.math.max
import kotlin.math.min

/**
 * A custom progress dialog that displays a circular progress bar with percentage
 * instead of the deprecated ProgressDialog with raw numbers.
 * 
 * This dialog shows:
 * - A title text
 * - A circular progress indicator that fills based on percentage
 * - A percentage text in the center of the circle
 */
class CircularProgressDialog(
    context: Context,
    style: Int = R.style.AppCompatProgressDialogStyle
) : AlertDialog(context, style) {

    private val progressIndicator: CircularProgressIndicator
    private val progressPercentage: TextView
    private val progressTitle: TextView
    private val progressDetails: TextView

    init {
        val view = LayoutInflater.from(context).inflate(R.layout.circular_progress_dialog, null)
        setView(view)
        
        progressIndicator = view.findViewById(R.id.circular_progress)
        progressPercentage = view.findViewById(R.id.progress_percentage)
        progressTitle = view.findViewById(R.id.progress_title)
        progressDetails = view.findViewById(R.id.progress_details)
        
        // Set initial state
        setProgress(0)
        setCancelable(true)
    }

    /**
     * Set the progress as a percentage (0-100)
     */
    fun setProgress(percentage: Int) {
        val clampedPercentage = max(0, min(100, percentage))
        progressIndicator.progress = clampedPercentage
        progressPercentage.text = context.getString(R.string.percentage_format, clampedPercentage)
    }

    /**
     * Set the progress based on current and total values
     * Automatically calculates percentage and handles edge cases
     */
    fun setProgress(current: Long, total: Long) {
        val percentage = calculatePercentage(current, total)
        setProgress(percentage)
        
        // Optionally show raw numbers for debugging (hidden by default)
        if (progressDetails.visibility == View.VISIBLE) {
            progressDetails.text = context.getString(R.string.progress_format, current, total)
        }
    }

    /**
     * Set the title text of the dialog
     */
    override fun setTitle(title: CharSequence?) {
        progressTitle.text = title ?: context.getString(R.string.dialog_processing)
    }

    /**
     * Set the title text using a string resource
     */
    override fun setTitle(titleId: Int) {
        progressTitle.text = context.getString(titleId)
    }

    /**
     * Set a message (currently maps to title for consistency)
     */
    override fun setMessage(message: CharSequence?) {
        setTitle(message)
    }

    /**
     * Show/hide the raw progress details (for debugging)
     */
    fun setShowProgressDetails(show: Boolean) {
        progressDetails.visibility = if (show) View.VISIBLE else View.GONE
    }

    /**
     * Calculate percentage from current and total values with edge case handling
     */
    private fun calculatePercentage(current: Long, total: Long): Int {
        return when {
            total <= 0 -> 0  // Avoid division by zero
            current <= 0 -> 0  // No progress yet
            current >= total -> 100  // Complete
            else -> ((current.toDouble() / total.toDouble()) * 100).toInt()
        }
    }

    companion object {
        /**
         * Create a new CircularProgressDialog instance
         */
        fun create(
            context: Context,
            title: String? = null,
            cancelable: Boolean = true,
            onCancelListener: DialogInterface.OnCancelListener? = null
        ): CircularProgressDialog {
            return CircularProgressDialog(context).apply {
                title?.let { setTitle(it) }
                setCancelable(cancelable)
                onCancelListener?.let { setOnCancelListener(it) }
            }
        }
    }
}
