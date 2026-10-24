// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.security

import android.app.Activity
import androidx.appcompat.app.AlertDialog
import com.ichi2.anki.CommonString
import com.ichi2.anki.common.destinations.PreferencesDestination
import com.ichi2.anki.preferences.toIntent
import com.ichi2.utils.negativeButton
import com.ichi2.utils.positiveButton
import com.ichi2.utils.show

/**
 * Explains that a card was blocked from launching an external app, with a shortcut to the setting
 * which allows it.
 */
fun showExternalAppLaunchBlockedDialog(activity: Activity) {
    AlertDialog.Builder(activity).show {
        setMessage(CommonString.card_external_launch_denied_snackbar)
        positiveButton(CommonString.settings) {
            // Dismiss first: the dialog's premise is resolved once the user has acted on the
            // setting, so it should not still be showing when they return.
            it.dismiss()
            activity.startActivity(PreferencesDestination.Advanced.toIntent(activity))
        }
        negativeButton(CommonString.dialog_cancel)
    }
}
