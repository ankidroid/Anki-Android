/****************************************************************************************
 *                                                                                      *
 * Copyright (c) 2025 Shridhar Goel <shridhar.goel@gmail.com>                           *
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

import android.view.WindowManager
import androidx.annotation.StringRes
import androidx.fragment.app.FragmentActivity
import com.ichi2.anki.AnkiDroidApp
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import java.lang.ref.WeakReference

/**
 * Manages showing and hiding progress dialogs with coordination to prevent multiple dialogs
 * from being shown at once. If a progress dialog is already being shown when another is requested,
 * the new dialog will not be displayed and a warning is logged.
 *
 * This implementation uses AnkiProgressDialogFragment, which is based on DialogFragment
 * instead of the deprecated ProgressDialog because:
 * - ProgressDialog was deprecated in API level 26 (Android 8.0)
 * - DialogFragment survives configuration changes (e.g., screen rotation)
 * - DialogFragment follows Material Design guidelines
 * - DialogFragment allows for a customizable UI with lifecycle management
 *
 * @see AnkiProgressDialogFragment
 */
object AnkiProgressManager {
    private var dialogJob: Job? = null
    private var dialog: WeakReference<AnkiProgressDialogFragment>? = null

    /**
     * Shows a progress dialog and executes the given operation.
     * The dialog is shown after a delay and dismissed when the operation completes.
     *
     * Progress info is polled from the backend.
     * Starts the progress dialog after a delay (default value is 600 ms)
     * so that quick operations don't just show flashes of a dialog.
     *
     * @param activity The FragmentActivity where the dialog will be shown
     * @param message The message to display in the progress dialog
     * @param cancelableViaBackButton Whether the dialog can be canceled by the user
     * @param cancelButtonTextResId Optional resource ID for custom cancel button text
     * @param onCancel Optional callback to invoke when dialog is canceled
     * @param delayMillis Delay before showing the dialog (to avoid flashing for quick operations)
     * @param op The operation to execute while showing the progress dialog
     */
    suspend fun <T> withProgress(
        activity: FragmentActivity,
        message: String,
        cancelableViaBackButton: Boolean = false,
        @StringRes cancelButtonTextResId: Int? = null,
        onCancel: (() -> Unit)? = null,
        delayMillis: Long = 600,
        op: suspend (AnkiProgressDialogFragment) -> T,
    ): T =
        coroutineScope {
            dialogJob?.cancel()
            dialog?.get()?.dismissAllowingStateLoss()

            val dialog =
                AnkiProgressDialogFragment.newInstance(
                    message = message,
                    cancelableViaBackButton = cancelableViaBackButton,
                    cancelButtonTextResId = cancelButtonTextResId,
                    onCancelListener = onCancel,
                )
            this@AnkiProgressManager.dialog = WeakReference(dialog)

            activity.window.setFlags(
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            )

            dialogJob =
                launch {
                    delay(delayMillis)
                    if (!AnkiDroidApp.instance.progressDialogShown) {
                        Timber.i("Displaying progress dialog: ${delayMillis}ms elapsed")
                        dialog.show(activity.supportFragmentManager, AnkiProgressDialogFragment.TAG)
                        AnkiDroidApp.instance.progressDialogShown = true
                    } else {
                        Timber.w("A progress dialog is already displayed, not displaying another")
                    }
                }

            try {
                op(dialog)
            } finally {
                dialogJob?.cancel()
                dismissDialogIfShowing(dialog)
                AnkiDroidApp.instance.progressDialogShown = false
                activity.window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
            }
        }

    private fun dismissDialogIfShowing(dialog: AnkiProgressDialogFragment) {
        try {
            if (dialog.isAdded) {
                dialog.dismiss()
            }
        } catch (e: Exception) {
            Timber.w(e)
        }
    }
}
