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
import androidx.fragment.app.FragmentActivity
import com.ichi2.utils.dismissSafely
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import java.lang.ref.WeakReference

/**
 * Manages showing and hiding progress dialogs.
 *
 * If a dialog is already showing when a new dialog is requested, the new dialog will not be displayed
 * and a warning is logged. This is tracked by the boolean progressDialogShown.
 *
 * This implementation uses AnkiProgressDialogFragment, which is based on DialogFragment because:
 * - DialogFragment survives configuration changes (e.g., screen rotation)
 * - DialogFragment follows Material Design guidelines
 * - DialogFragment allows for a customizable UI with lifecycle management
 *
 * @see AnkiProgressDialogFragment
 */
object AnkiProgressManager {
    private var dialogJob: Job? = null
    private var dialog: WeakReference<AnkiProgressDialogFragment>? = null

    /** Used to avoid showing extra progress dialogs when one already shown. */
    private var progressDialogShown = false

    /**
     * Checks if a progress dialog is currently being shown.
     * @return true if a progress dialog is being shown, false otherwise.
     */
    fun isProgressDialogShown(): Boolean = progressDialogShown

    /**
     * Sets the state of whether a progress dialog is being shown.
     * @param shown true if a progress dialog is being shown, false otherwise.
     */
    fun setProgressDialogShown(shown: Boolean) {
        progressDialogShown = shown
    }

    /**
     * Shows a progress dialog and executes the given operation.
     * The dialog is shown after a delay and dismissed when the operation completes.
     *
     * Progress info is polled from the backend via the
     * monitorProgress method in CoroutineHelpers
     *
     * Starts the progress dialog after a delay (default value is 600 ms)
     * so that quick operations don't just show flashes of a dialog.
     *
     * @param activity The FragmentActivity where the dialog will be shown
     * @param message The message to display in the progress dialog
     * @param cancellationConfig Configuration parameters for the dialog: cancelability, button text, cancel callback
     * @param delayMillis Delay before showing the dialog (to avoid flashing for quick operations)
     * @param op The operation to execute while showing the progress dialog
     */
    suspend fun <T> withProgress(
        activity: FragmentActivity,
        message: String,
        cancellationConfig: ProgressDialogCancellationConfig = ProgressDialogCancellationConfig(),
        delayMillis: Long = 600,
        op: suspend (AnkiProgressDialogFragment) -> T,
    ): T =
        coroutineScope {
            val dialog = AnkiProgressDialogFragment.newInstance(message = message, cancellationConfig = cancellationConfig)
            this@AnkiProgressManager.dialog = WeakReference(dialog)

            activity.window.setFlags(
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            )

            dialogJob =
                launch {
                    delay(delayMillis)
                    if (!isProgressDialogShown()) {
                        Timber.i("Displaying progress dialog: ${delayMillis}ms elapsed")
                        dialog.show(activity.supportFragmentManager, AnkiProgressDialogFragment.TAG)
                        setProgressDialogShown(true)
                    } else {
                        // TODO: It would be a bug if a progress dialog is shown when one is
                        //  already displayed. Handle this in a better way.
                        Timber.w("A progress dialog is already displayed, not displaying another")
                    }
                }

            try {
                op(dialog)
            } finally {
                dialogJob?.cancel()
                dialog.dismissSafely()
                setProgressDialogShown(false)
                activity.window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
            }
        }
}
