/*
 *  Copyright (c) 2025 Raiyyan <f20241312@pilani.bits-pilani.ac.in>
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

package com.ichi2.anki.services

import android.app.Activity
import android.os.Environment
import androidx.appcompat.app.AlertDialog
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.FatalInitializationError
import com.ichi2.anki.InitialActivity.StartupFailure.InitializationError
import com.ichi2.anki.R
import com.ichi2.anki.dialogs.FatalErrorDialog
import com.ichi2.anki.exception.SystemStorageException
import com.ichi2.utils.cancelable
import com.ichi2.utils.create
import com.ichi2.utils.message
import com.ichi2.utils.positiveButton
import com.ichi2.utils.title
import timber.log.Timber
import java.io.File

/**
 * Validates storage permissions and accessibility before activities that require
 * storage access can proceed.
 *
 * This validator is designed to handle issues:
 * - #19553: ManageSpaceActivity crash when getExternalFilesDir returns null
 * - #19551: Update Legacy Storage when getExternalFilesDir returns null
 * - #19552: Consolidate validation across entry points
 *
 * Related to issue #13207: External storage not existing
 */
object StoragePermissionsValidator {
    /**
     * Checks if storage is accessible and safe to proceed.
     *
     * @param activity The activity requesting validation
     * @return true if storage is accessible and safe to proceed, false otherwise
     *
     * If validation fails, an error dialog is shown to the user and this returns false.
     * The calling activity should stop initialization when this returns false.
     */
    fun verifyStoragePermissions(activity: Activity): Boolean {
        try {
            // This is the check that causes the crash in issue #19553
            val externalFilesDir = activity.getExternalFilesDir(null)

            if (externalFilesDir == null) {
                Timber.w("getExternalFilesDir returned null")

                // Check if legacy storage path exists as a fallback (issue #19551)
                val legacyPath = File(Environment.getExternalStorageDirectory(), "AnkiDroid")
                if (legacyPath.exists() && legacyPath.canRead()) {
                    Timber.i("Legacy AnkiDroid directory exists and is accessible: ${legacyPath.absolutePath}")
                    // Legacy path exists - we could potentially allow access
                    // For now, still fail safely to prevent crashes, but log this information
                    // Future enhancement: could allow continued operation with legacy storage
                }

                showStorageErrorDialog(activity)
                return false
            }

            Timber.v("Storage validation successful: ${externalFilesDir.absolutePath}")
            return true
        } catch (e: SystemStorageException) {
            // Handle SystemStorageException from CollectionHelper calls
            Timber.e(e, "SystemStorageException during storage validation")
            showStorageErrorDialog(activity, e)
            return false
        } catch (e: Exception) {
            // Catch any other unexpected exceptions to prevent crashes
            Timber.e(e, "Unexpected exception during storage validation")
            showStorageErrorDialog(activity)
            return false
        }
    }

    /**
     * Shows an error dialog to the user when storage is inaccessible.
     * Reuses the existing error dialog infrastructure from DeckPicker/InitialActivity.
     */
    private fun showStorageErrorDialog(
        activity: Activity,
        exception: SystemStorageException? = null,
    ) {
        Timber.i("Showing storage error dialog")

        when (activity) {
            is AnkiActivity -> {
                // Use the existing FatalErrorDialog pattern for AnkiActivity instances
                if (exception != null) {
                    val failure = InitializationError(FatalInitializationError.StorageError(exception))
                    FatalErrorDialog.build(activity, failure).show()
                } else {
                    // Build a simple error dialog
                    showSimpleStorageErrorDialog(activity)
                }
            }
            else -> {
                // For non-AnkiActivity instances, use a simple AlertDialog
                showSimpleStorageErrorDialog(activity)
            }
        }
    }

    /**
     * Shows a simple storage error dialog for activities that don't extend AnkiActivity
     */
    private fun showSimpleStorageErrorDialog(activity: Activity) {
        AlertDialog
            .Builder(activity)
            .create {
                title(R.string.sd_card_access_error_title)
                message(R.string.sd_card_access_error_message)
                positiveButton(R.string.close) {
                    activity.finish()
                }
                cancelable(false)
            }.show()
    }
}
