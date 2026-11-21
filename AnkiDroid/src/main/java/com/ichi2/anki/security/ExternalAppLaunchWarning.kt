// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.security

import android.app.Activity
import androidx.appcompat.app.AlertDialog
import com.ichi2.anki.CommonString
import com.ichi2.anki.R
import com.ichi2.anki.common.destinations.PreferencesDestination
import com.ichi2.anki.preferences.toIntent
import com.ichi2.utils.iconAttr
import com.ichi2.utils.negativeButton
import com.ichi2.utils.positiveButton
import com.ichi2.utils.show

/**
 * Shows a dialog when a card tries to open another app and is blocked.
 * The dialog has a button to open the setting which allows it.
 *
 * Rules:
 * - only one dialog is shown at a time
 * - after the user dismisses it, it is not shown again for the same card
 * - [onDialogShown] / [onDialogDismissed] tell the host to pause and resume auto-advance,
 *   so cards are not graded behind the dialog
 */
class ExternalAppLaunchWarning(
    private val activity: Activity,
    private val onDialogShown: () -> Unit = {},
    private val onDialogDismissed: () -> Unit = {},
) {
    private var dialog: AlertDialog? = null

    /** True after the user dismisses a warning. Reset by [onCardChanged]. */
    private var suppressUntilCardChange = false

    fun show() {
        if (dialog?.isShowing == true) return
        if (suppressUntilCardChange) return
        onDialogShown()
        dialog =
            AlertDialog.Builder(activity).show {
                setTitle(CommonString.card_external_launch_blocked_title)
                setMessage(CommonString.card_external_launch_blocked_message)
                iconAttr(R.drawable.ic_warning)
                positiveButton(CommonString.settings) {
                    // Close the dialog before opening Settings, so it is not
                    // still on screen when the user comes back.
                    it.dismiss()
                    activity.startActivity(PreferencesDestination.Advanced.toIntent(activity))
                }
                negativeButton(CommonString.dialog_cancel)
                setOnCancelListener {
                    // A WebView navigation may be dispatched before the posted dismissal callback.
                    // Suppress it immediately so a cancelled warning cannot reopen.
                    suppressUntilCardChange = true
                }
                setOnDismissListener {
                    suppressUntilCardChange = true
                    onDialogDismissed()
                }
            }
    }

    /** Allows the warning again when a new card is shown. */
    fun onCardChanged() {
        suppressUntilCardChange = false
    }
}
