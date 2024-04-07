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

import android.content.Context
import android.content.SharedPreferences
import android.database.sqlite.SQLiteFullException
import android.os.Build
import android.os.Environment
import android.os.Parcelable
import androidx.annotation.CheckResult
import androidx.annotation.RequiresApi
import androidx.core.content.edit
import com.ichi2.anki.servicelayer.PreferenceUpgradeService
import com.ichi2.anki.servicelayer.PreferenceUpgradeService.setPreferencesUpToDate
import com.ichi2.anki.servicelayer.ScopedStorageService.isLegacyStorage
import com.ichi2.anki.ui.windows.permissions.Full30and31PermissionsFragment
import com.ichi2.anki.ui.windows.permissions.PermissionsFragment
import com.ichi2.anki.ui.windows.permissions.PermissionsUntil29Fragment
import com.ichi2.anki.ui.windows.permissions.TiramisuPermissionsFragment
import com.ichi2.utils.Permissions
import com.ichi2.utils.VersionUtils.pkgVersionName
import kotlinx.parcelize.Parcelize
import net.ankiweb.rsdroid.BackendException
import timber.log.Timber
import java.lang.Exception

/** Utilities for launching the first activity (currently the DeckPicker)  */
object InitialActivity {
    /** Returns null on success  */
    @CheckResult
    fun getStartupFailureType(context: Context): StartupFailure? {
        // A WebView failure means that we skip `AnkiDroidApp`, and therefore haven't loaded the collection
        if (AnkiDroidApp.webViewFailedToLoad()) {
            return StartupFailure.WEBVIEW_FAILED
        }

        val failure = try {
            CollectionManager.getColUnsafe()
            return null
        } catch (e: BackendException.BackendDbException.BackendDbLockedException) {
            Timber.w(e)
            StartupFailure.DATABASE_LOCKED
        } catch (e: BackendException.BackendDbException.BackendDbFileTooNewException) {
            Timber.w(e)
            StartupFailure.FUTURE_ANKIDROID_VERSION
        } catch (e: SQLiteFullException) {
            Timber.w(e)
            StartupFailure.DISK_FULL
        } catch (e: Exception) {
            Timber.w(e)
            CrashReportService.sendExceptionReport(e, "InitialActivity::getStartupFailureType")
            StartupFailure.DB_ERROR
        }

        if (!AnkiDroidApp.isSdCardMounted) {
            return StartupFailure.SD_CARD_NOT_MOUNTED
        } else if (!CollectionHelper.isCurrentAnkiDroidDirAccessible(context)) {
            return StartupFailure.DIRECTORY_NOT_ACCESSIBLE
        }

        return failure
    }

    /** @return Whether any preferences were upgraded
     */
    fun upgradePreferences(context: Context, previousVersionCode: Long): Boolean {
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

sealed class AnkiDroidFolder(val permissionSet: PermissionSet) {
    /**
     * AnkiDroid will use the folder ~/AnkiDroid by default
     * To access it, we must first get [permissionSet].permissions.
     * This folder is not deleted when the user uninstalls the app, which reduces the risk of data loss,
     * but increase the risk of space used on their storage when they don't want to.
     * It can not be used on the play store starting with Sdk 30.
     **/
    class PublicFolder(requiredPermissions: PermissionSet) : AnkiDroidFolder(requiredPermissions)

    /**
     * AnkiDroid will use the app-private folder: `~/Android/data/com.ichi2.anki[.A]/files/AnkiDroid`.
     * The user may delete when they uninstall the app, risking data loss.
     * No permission dialog is required.
     * Google will not allow [android.Manifest.permission.MANAGE_EXTERNAL_STORAGE], so this is default on the Play Store.
     */
    data object AppPrivateFolder : AnkiDroidFolder(PermissionSet.APP_PRIVATE)

    fun hasRequiredPermissions(context: Context): Boolean {
        return Permissions.hasAllPermissions(context, permissionSet.permissions)
    }
}

@Parcelize
enum class PermissionSet(val permissions: List<String>, val permissionsFragment: Class<out PermissionsFragment>?) : Parcelable {
    LEGACY_ACCESS(Permissions.legacyStorageAccessPermissions, PermissionsUntil29Fragment::class.java),

    @RequiresApi(Build.VERSION_CODES.R)
    EXTERNAL_MANAGER(listOf(Permissions.MANAGE_EXTERNAL_STORAGE), Full30and31PermissionsFragment::class.java),

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    TIRAMISU_EXTERNAL_MANAGER(
        permissions = listOf(Permissions.MANAGE_EXTERNAL_STORAGE),
        permissionsFragment = TiramisuPermissionsFragment::class.java
    ),

    APP_PRIVATE(emptyList(), null);
}

/**
 * Returns in which folder AnkiDroid data is saved.
 * [AnkiDroidFolder.PublicFolder] is preferred, as it reduce risk of data loss.
 * When impossible, we use the app-private directory.
 * See https://github.com/ankidroid/Anki-Android/issues/5304 for more context.
 */
internal fun selectAnkiDroidFolder(
    canManageExternalStorage: Boolean,
    currentFolderIsAccessibleAndLegacy: Boolean
): AnkiDroidFolder {
    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q || currentFolderIsAccessibleAndLegacy) {
        // match AnkiDroid behaviour before scoped storage - force the use of ~/AnkiDroid,
        // since it's fast & safe up to & including 'Q'
        // If a user upgrades their OS from Android 10 to 11 then storage speed is severely reduced
        // and a user should use one of the below options to provide faster speeds
        return AnkiDroidFolder.PublicFolder(PermissionSet.LEGACY_ACCESS)
    }

    // If the user can manage external storage, we can access the safe folder & access is fast
    return if (canManageExternalStorage) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            AnkiDroidFolder.PublicFolder(PermissionSet.TIRAMISU_EXTERNAL_MANAGER)
        } else {
            AnkiDroidFolder.PublicFolder(PermissionSet.EXTERNAL_MANAGER)
        }
    } else {
        return AnkiDroidFolder.AppPrivateFolder
    }
}

fun selectAnkiDroidFolder(context: Context): AnkiDroidFolder {
    val canAccessLegacyStorage = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || Environment.isExternalStorageLegacy()
    val currentFolderIsAccessibleAndLegacy = canAccessLegacyStorage && isLegacyStorage(context, setCollectionPath = false) == true

    return selectAnkiDroidFolder(
        canManageExternalStorage = Permissions.canManageExternalStorage(context),
        currentFolderIsAccessibleAndLegacy = currentFolderIsAccessibleAndLegacy
    )
}
