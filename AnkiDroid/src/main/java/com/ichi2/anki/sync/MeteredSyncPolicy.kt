// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.sync

import android.content.Context
import androidx.appcompat.app.AlertDialog
import com.ichi2.anki.R
import com.ichi2.anki.settings.Prefs
import com.ichi2.utils.NetworkUtils.isActiveNetworkMetered
import com.ichi2.utils.checkBoxPrompt
import com.ichi2.utils.message
import com.ichi2.utils.negativeButton
import com.ichi2.utils.positiveButton
import com.ichi2.utils.show

/**
 * Single source of truth for the "warn the user before syncing on a metered connection"
 * preference. All reads of [Prefs.allowSyncOnMeteredConnections] should go through here
 * so that the gating policy lives in one place.
 */
object MeteredSyncPolicy {
    /** True when sync should be blocked/prompted because the network is metered. */
    fun shouldBlock(): Boolean = !Prefs.allowSyncOnMeteredConnections && isActiveNetworkMetered()

    /** Persist the user's "don't ask again" choice from the warning dialog. */
    fun setAlwaysAllow(allow: Boolean) {
        Prefs.allowSyncOnMeteredConnections = allow
    }

    /**
     * Run [onConfirm] immediately if the network is unmetered, metered-network sync is
     * allowed, or [skipPrompt] is set; otherwise show a warning dialog and run [onConfirm] when
     * 'Continue' is pressed.
     *
     * @param skipPrompt `true` if the user has already accepted a metered sync earlier in this
     *   attempt (e.g. retry after conflict resolution); skips the warning.
     * @param onDialogShown invoked only if the warning dialog is displayed
     */
    context(context: Context)
    fun confirmThen(
        skipPrompt: Boolean = false,
        onDialogShown: () -> Unit,
        onConfirm: () -> Unit,
    ) {
        if (skipPrompt || !shouldBlock()) {
            onConfirm()
            return
        }
        AlertDialog.Builder(context).show {
            message(R.string.metered_sync_data_warning)
            positiveButton(R.string.dialog_continue) { onConfirm() }
            negativeButton(R.string.dialog_cancel)
            checkBoxPrompt(R.string.button_do_not_show_again) { checked ->
                setAlwaysAllow(checked)
            }
        }
        onDialogShown()
    }
}
