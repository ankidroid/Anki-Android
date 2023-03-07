/*
 *  Copyright (c) 2021 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.annotation.CheckResult
import androidx.core.content.edit
import com.ichi2.anki.permissions.PermissionManager
import com.ichi2.anki.permissions.PermissionsRequestResults
import com.ichi2.anki.permissions.finishActivityAndShowAppPermissionManagementScreen
import com.ichi2.anki.servicelayer.PreferenceUpgradeService
import com.ichi2.anki.servicelayer.PreferenceUpgradeService.setPreferencesUpToDate
import com.ichi2.annotations.NeedsTest
import com.ichi2.utils.VersionUtils.pkgVersionName
import timber.log.Timber

/** Utilities for launching the first activity (currently the DeckPicker)  */
object InitialActivity {
    /** Returns null on success  */
    @CheckResult
    fun getStartupFailureType(context: Context): StartupFailure? {
        // A WebView failure means that we skip `AnkiDroidApp`, and therefore haven't loaded the collection
        if (AnkiDroidApp.webViewFailedToLoad()) {
            return StartupFailure.WEBVIEW_FAILED
        }

        // If we're OK, return null
        if (CollectionHelper.instance.getColSafe(context, reportException = false) != null) {
            return null
        }
        if (!AnkiDroidApp.isSdCardMounted) {
            return StartupFailure.SD_CARD_NOT_MOUNTED
        } else if (!CollectionHelper.isCurrentAnkiDroidDirAccessible(context)) {
            return StartupFailure.DIRECTORY_NOT_ACCESSIBLE
        }

        return when (CollectionHelper.lastOpenFailure) {
            CollectionHelper.CollectionOpenFailure.FILE_TOO_NEW -> StartupFailure.FUTURE_ANKIDROID_VERSION
            CollectionHelper.CollectionOpenFailure.CORRUPT -> StartupFailure.DB_ERROR
            CollectionHelper.CollectionOpenFailure.LOCKED -> StartupFailure.DATABASE_LOCKED
            CollectionHelper.CollectionOpenFailure.DISK_FULL -> StartupFailure.DISK_FULL
            null -> {
                // if getColSafe returned null, this should never happen
                null
            }
        }
    }

    /** @return Whether any preferences were upgraded
     */
    fun upgradePreferences(context: Context?, previousVersionCode: Long): Boolean {
        return PreferenceUpgradeService.upgradePreferences(context, previousVersionCode)
    }

    /**
     * @return Whether a fresh install occurred and a "fresh install" setup for preferences was performed
     * This only refers to a fresh install from the preferences perspective, not from the Anki data perspective.
     *
     * NOTE: A user can wipe app data, which will mean this returns true WITHOUT deleting their collection.
     * The above note will need to be reevaluated after scoped storage migration takes place
     *
     *
     * On the other hand, restoring an app backup can cause this to return true before the Anki collection is created
     * in practice, this doesn't occur due to CollectionHelper.getCol creating a new collection, and it's called before
     * this in the startup script
     */
    @CheckResult
    fun performSetupFromFreshInstallOrClearedPreferences(preferences: SharedPreferences): Boolean {
        if (!wasFreshInstall(preferences)) {
            Timber.d("Not a fresh install [preferences]")
            return false
        }
        Timber.i("Fresh install")
        setPreferencesUpToDate(preferences)
        setUpgradedToLatestVersion(preferences)
        return true
    }

    /**
     * true if the app was launched the first time
     * false if the app was launched for the second time after a successful initialisation
     * false if the app was launched after an update
     */
    fun wasFreshInstall(preferences: SharedPreferences) =
        "" == preferences.getString("lastVersion", "")

    /** Sets the preference stating that the latest version has been applied  */
    fun setUpgradedToLatestVersion(preferences: SharedPreferences) {
        Timber.i("Marked prefs as upgraded to latest version: %s", pkgVersionName)
        preferences.edit { putString("lastVersion", pkgVersionName) }
    }

    /** @return false: The app has been upgraded since the last launch OR the app was launched for the first time.
     * Implementation detail:
     * This is not called in the case of performSetupFromFreshInstall returning true.
     * So this should not use the default value
     */
    fun isLatestVersion(preferences: SharedPreferences): Boolean {
        return preferences.getString("lastVersion", "") == pkgVersionName
    }

    enum class StartupFailure {
        SD_CARD_NOT_MOUNTED, DIRECTORY_NOT_ACCESSIBLE, FUTURE_ANKIDROID_VERSION,
        DB_ERROR, DATABASE_LOCKED, WEBVIEW_FAILED, DISK_FULL
    }
}

/**
 * Whether we should try a startup with a permission dialog + folder which is safe from uninstalling
 * or go straight into AnkiDroid
 */
sealed interface AnkiDroidFolder {
    /**
     * AnkiDroid will use the folder ~/AnkiDroid by default
     * To access it, we must first get [requiredPermissions].
     * This folder is not deleted when the user uninstalls the app, which reduces the risk of data loss,
     * but increase the risk of space used on their storage when they don't want to.
     * It can not be used on the play store starting with Sdk 30.
     **/
    class PublicFolder(val requiredPermissions: Array<String>) : AnkiDroidFolder
}

/**
 * Returns in which folder AnkiDroid data is saved.
 * [AnkiDroidFolder.PublicFolder] is preferred, as it reduce risk of data loss.
 * When impossible, we use the app-specific directory.
 * See https://github.com/ankidroid/Anki-Android/issues/5304 for more context.
 */
fun selectAnkiDroidFolder(): AnkiDroidFolder {
    // match previous AnkiDroid behaviour - force the use of ~/AnkiDroid, since it's fast & safe.
    return AnkiDroidFolder.PublicFolder(
        arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
    )
}

/**
 * Logic related to [DeckPicker] startup - required permissions for storage
 * Handles: Accept, Deny + Permanent Deny of permissions
 *
 * Designed to allow expansion for more complex logic
 */
@NeedsTest("New User: Accepts permission")
@NeedsTest("New User: Denies permission then accepts")
@NeedsTest("New User: Denies permission then denies permanently")
@NeedsTest("New User: Denies permission permanently")
@NeedsTest("Existing User: Permission Granted")
@NeedsTest("Existing User: System removed permission")
@NeedsTest("Existing User: Changes Deck")
class StartupStoragePermissionManager private constructor(
    private val deckPicker: DeckPicker,
    permissions: Array<String>,
    useCallbackIfActivityRecreated: Boolean
) {
    private var timesRequested: Int = 0

    /**
     * Show "Please grant AnkiDroid the ‘Storage’ permission to continue" and open Android settings
     * for AnkiDroid's permissions
     */
    private fun onPermissionPermanentlyDenied() {
        // User denied access to file storage  so show error toast and display "App Info"
        UIUtils.showThemedToast(deckPicker, R.string.startup_no_storage_permission, false)
        // note: this may not be defined on some Phones. In which case we still have a toast
        deckPicker.finishActivityAndShowAppPermissionManagementScreen()
    }

    private fun onRegularStartup() {
        deckPicker.invalidateOptionsMenu()
        deckPicker.handleStartup()
    }

    private fun retryPermissionRequest(displayError: Boolean) {
        if (timesRequested < 3) {
            displayStoragePermissionDialog()
        } else {
            if (displayError) {
                Timber.w("doing nothing - app is probably broken")
                CrashReportService.sendExceptionReport("Multiple errors obtaining permissions", "InitialActivity::permissionManager")
            }
            onPermissionPermanentlyDenied()
        }
    }

    private val permissionManager = PermissionManager.register(
        activity = deckPicker,
        permissions = permissions,
        useCallbackIfActivityRecreated = useCallbackIfActivityRecreated,
        callback = { permissionDialogResultRaw ->
            val permissionDialogResult = PermissionsRequestResults.from(deckPicker, permissionDialogResultRaw)
            with(permissionDialogResult) {
                when {
                    allGranted -> onRegularStartup()
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && hasPermanentlyDeniedPermissions -> onPermissionPermanentlyDenied()
                    // try again (recurse), we need the permission
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && hasTemporarilyDeniedPermissions -> retryPermissionRequest(displayError = false)
                    hasRejectedPermissions -> retryPermissionRequest(displayError = false)
                    cancelled -> {
                        if (timesRequested == 1) {
                            UIUtils.showThemedToast(deckPicker, R.string.something_wrong, false)
                        }
                        retryPermissionRequest(displayError = true)
                    }
                }
            }
        }
    )

    fun displayStoragePermissionDialog() {
        timesRequested++
        permissionManager.launchPermissionDialog()
    }

    fun checkPermissions() = permissionManager.checkPermissions()

    companion object {
        /**
         * This **must** be called unconditionally, as part of initialization path,
         * typically as a field initializer due to the use of [PermissionManager.register]
         * */
        fun register(
            deckPicker: DeckPicker,
            useCallbackIfActivityRecreated: Boolean

        ): StartupStoragePermissionManager {
            val permissionRequest = selectAnkiDroidFolder()
            val permissions = permissionRequest as AnkiDroidFolder.PublicFolder
            return StartupStoragePermissionManager(
                deckPicker,
                permissions.requiredPermissions,
                useCallbackIfActivityRecreated = useCallbackIfActivityRecreated
            )
        }
    }
}
