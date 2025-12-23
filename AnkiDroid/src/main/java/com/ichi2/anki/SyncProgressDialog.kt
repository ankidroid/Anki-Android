package com.ichi2.anki

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AlertDialog

/**
 * Custom dialog for displaying sync progress with an enhanced circular progress indicator.
 *
 * Shows:
 * - Custom circular progress bar with radial gradient and tick marks
 * - Percentage text in the center
 * - Status message
 * - Optional detailed counts (Added/Modified and Removed)
 */
class SyncProgressDialog(
    context: Context,
    private val onCancel: (() -> Unit)? = null,
    cancelButtonText: String? = null,
) {
    private val dialog: AlertDialog
    private val customCircularProgress: CustomCircularProgressView
    private val statusText: TextView
    private val detailsText: TextView

    init {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_sync_progress, null)
        customCircularProgress = view.findViewById(R.id.custom_circular_progress)
        statusText = view.findViewById(R.id.sync_status_text)
        detailsText = view.findViewById(R.id.sync_details_text)

        val builder =
            AlertDialog
                .Builder(context)
                .setView(view)
                .setCancelable(onCancel != null)

        if (cancelButtonText != null && onCancel != null) {
            builder
                .setCancelable(false)
                .setNegativeButton(cancelButtonText) { _, _ ->
                    onCancel.invoke()
                }
        }

        dialog = builder.create()

        if (onCancel != null && cancelButtonText == null) {
            dialog.setOnCancelListener {
                onCancel.invoke()
            }
        }
    }

    /**
     * Updates the progress display with percentage and details.
     *
     * @param percentage Progress percentage (0-100)
     * @param details Optional detailed text (e.g., "Added/Modified: 150 / 200\nRemoved: 0 / 0")
     */
    fun updateProgress(
        percentage: Int,
        details: String? = null,
    ) {
        customCircularProgress.setProgress(percentage, animate = true)

        // Always show details if provided
        if (details != null && details.isNotEmpty()) {
            detailsText.text = details
        }
    }

    /**
     * Updates the status message.
     *
     * @param message Status message to display
     */
    fun setStatusMessage(message: String) {
        statusText.text = message
    }

    /**
     * Shows the dialog.
     */
    fun show() {
        dialog.show()
    }

    /**
     * Dismisses the dialog.
     */
    fun dismiss() {
        dialog.dismiss()
    }

    /**
     * Checks if the dialog is showing.
     */
    fun isShowing(): Boolean = dialog.isShowing
}
