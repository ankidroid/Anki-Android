/*
 *  Copyright (c) 2023 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki.dialogs

import android.content.Context
import android.os.Build
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.WhichButton
import com.afollestad.materialdialogs.actions.setActionButtonEnabled
import com.afollestad.materialdialogs.checkbox.checkBoxPrompt
import com.ichi2.anki.*
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.servicelayer.ScopedStorageService
import com.ichi2.compat.CompatHelper.Companion.getPackageInfoCompat
import com.ichi2.compat.PackageInfoFlagsCompat
import com.ichi2.libanki.utils.TimeManager
import com.ichi2.utils.*
import timber.log.Timber

/**
 * Prompts a user to backup via either sync or 'export collection'
 *
 * If dismissed, it will not appear for a period of time (~2 weeks): [calculateNextTimeToShowDialog]
 * After 2 dismissals, the user may hide the dialog permanently.
 *
 * This exists to inform the user their data is at risk when in a scoped folder.
 *
 * See [shouldShowDialog] for the criteria to display the dialog
 */
class BackupPromptDialog private constructor(private val windowContext: Context) {

    private lateinit var materialDialog: MaterialDialog

    /**
     * After 2 dismissals, allow ignoring
     * Note: this is 0-based - the dialog has not been dismissed on the first viewing
     */
    private val allowUserToPermanentlyDismissDialog: Boolean
        get() = timesDialogDismissed > 1

    /** Whether the user has selected 'don't show again' */
    private var userCheckedDoNotShowAgain = false

    private var timesDialogDismissed: Int
        get() = AnkiDroidApp.getSharedPrefs(windowContext).getInt("backupPromptDismissedCount", 0)
        set(value) = AnkiDroidApp.getSharedPrefs(windowContext).edit { putInt("backupPromptDismissedCount", value) }

    private var dialogPermanentlyDismissed: Boolean
        get() = AnkiDroidApp.getSharedPrefs(windowContext).getBoolean("backupPromptDisabled", false)
        set(disablePermanently) {
            AnkiDroidApp.getSharedPrefs(windowContext).edit {
                putBoolean("backupPromptDisabled", disablePermanently)
                if (disablePermanently) {
                    remove("backupPromptDismissedCount")
                    remove("timeToShowBackupDialog")
                }
            }
        }

    private var nextTimeToShowDialog: Long
        get() = AnkiDroidApp.getSharedPrefs(windowContext).getLong("timeToShowBackupDialog", 0)
        set(value) { AnkiDroidApp.getSharedPrefs(windowContext).edit { putLong("timeToShowBackupDialog", value) } }

    private fun onDismiss() {
        Timber.i("BackupPromptDialog dismissed")
        if (userCheckedDoNotShowAgain) {
            showPermanentlyDismissDialog(
                this.windowContext,
                onCancel = { dialogPermanentlyDismissed = true },
                onDisableReminder = {
                    userCheckedDoNotShowAgain = false
                    onDismiss()
                }
            )
        } else {
            timesDialogDismissed += 1
            nextTimeToShowDialog = calculateNextTimeToShowDialog()
        }
    }

    private fun onBackup() {
        nextTimeToShowDialog = calculateNextTimeToShowDialog()
    }

    private fun calculateNextTimeToShowDialog(): Long {
        val now = TimeManager.time.intTimeMS()
        val fixedDayCount = 12
        val oneToFourDays = (1..4).random() // 13-16 days
        return now + (fixedDayCount + oneToFourDays) * ONE_DAY_IN_MS
    }

    private fun build(isLoggedIn: Boolean, performBackup: () -> Unit) {
        this.materialDialog = MaterialDialog(windowContext).apply {
            icon(R.drawable.ic_baseline_backup_24)
            title(R.string.backup_your_collection)
            message(R.string.backup_collection_message)
            positiveButton(if (isLoggedIn) R.string.button_sync else R.string.button_backup) {
                Timber.i("User selected 'backup'")
                onBackup()
                performBackup()
            }
            if (allowUserToPermanentlyDismissDialog) {
                checkBoxPrompt(R.string.button_do_not_show_again) { checked ->
                    Timber.d("Don't show again checked: %b", checked)
                    userCheckedDoNotShowAgain = checked
                    setActionButtonEnabled(WhichButton.POSITIVE, !checked)
                }
            }
            negativeButton(R.string.button_backup_later) { onDismiss() }
            cancelable(false)
        }
    }

    companion object {
        private const val ONE_DAY_IN_MS = 1000 * 60 * 60 * 24

        /** @return Whether the dialog was shown */
        suspend fun showIfAvailable(deckPicker: DeckPicker): Boolean {
            val backupPrompt = BackupPromptDialog(deckPicker)
            if (!backupPrompt.shouldShowDialog()) {
                return false
            }

            val isLoggedIn = isLoggedIn()
            backupPrompt.apply {
                build(isLoggedIn) {
                    if (isLoggedIn) {
                        deckPicker.sync(conflict = null)
                    } else {
                        deckPicker.exportCollection(includeMedia = true)
                    }
                }
                materialDialog.show()
            }
            return true
        }

        /** Explains to the user they should sync as they risk to have data deleted or inaccessible (depending on whether legacy storage permission is kept) */
        fun showPermanentlyDismissDialog(context: Context, onCancel: () -> Unit, onDisableReminder: () -> Unit) {
            // TODO this ignores f-droid uesrs / full builds for now - handle in #13431
            val message = if (userIsPreservingLegacyStorage(context)) R.string.dismiss_backup_warning_upgrade else R.string.dismiss_backup_warning_new_user

            AlertDialog.Builder(context).show {
                title(R.string.dismiss_backup_warning_title)
                message(message)
                iconAttr(R.attr.dialogErrorIcon)
                positiveButton(R.string.dialog_cancel) { onCancel() }
                negativeButton(R.string.button_disable_reminder) { onDisableReminder() }
            }
        }

        /**
         * Whether the user still has the legacy storage permissions
         * ([Manifest.permission.READ_EXTERNAL_STORAGE] and [Manifest.permission.WRITE_EXTERNAL_STORAGE])
         * on Android 11 or later, where the permission was removed from Play store releases
         * @return true if the user Android's version is ≥ 11 and upgraded the app without removing the permission,
         * or false if the user has removed the permission, uninstalled the app or Android's version is < 11
         */
        fun userIsPreservingLegacyStorage(context: Context): Boolean {
            // TODO: Confirm this is correct after 13261 is merged.
            return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                Permissions.hasStorageAccessPermission(context)
        }
    }

    private suspend fun shouldShowDialog(): Boolean = !userIsNewToAnkiDroid() && canProvideBackupOption() && timeToShowDialogAgain()

    private fun canProvideBackupOption(): Boolean {
        if (isLoggedIn()) {
            // If we're unable to sync, there's no point in showing the dialog
            if (!canSync(windowContext)) {
                return false
            }
            // Show dialog to sync if user hasn't synced in a while
            val preferences = AnkiDroidApp.getSharedPrefs(windowContext)
            return millisecondsSinceLastSync(preferences) >= ONE_DAY_IN_MS * 7
        }

        // Non-legacy locations may be deleted by the user on uninstall
        val collectionIsSafeAfterUninstall = ScopedStorageService.isLegacyStorage(windowContext)
        if (!collectionIsSafeAfterUninstall) {
            return true
        }

        // The user may have upgraded, in which it's unsafe to uninstall as Android
        // will permanently revoke access to the legacy folder
        // The collection won't be lost, but it will be inaccessible.
        return userIsPreservingLegacyStorage(this.windowContext)
    }

    private fun timeToShowDialogAgain(): Boolean =
        nextTimeToShowDialog <= TimeManager.time.intTimeMS()

    private suspend fun userIsNewToAnkiDroid(): Boolean {
        // A user is new if the app was installed > 7 days ago  OR if they have no cards
        val firstInstallTime = getFirstInstallTime() ?: 0
        if (TimeManager.time.intTimeMS() - firstInstallTime >= ONE_DAY_IN_MS * 7) {
            return false
        }

        // if for some reason the user has no cards after 7 days, don't bother
        return withCol {
            this.cardCount() == 0
        }
    }

    /** The time at which the app was first installed. Units are as per [System.currentTimeMillis()]. */
    private fun getFirstInstallTime(): Long? {
        return try {
            return windowContext.packageManager.getPackageInfoCompat(
                windowContext.packageName,
                PackageInfoFlagsCompat.of(0)
            )?.firstInstallTime
        } catch (exception: Exception) {
            Timber.w("failed to get first install time")
            null
        }
    }
}
