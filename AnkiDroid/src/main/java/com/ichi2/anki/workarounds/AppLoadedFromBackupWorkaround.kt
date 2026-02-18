/*
 *  Copyright (c) 2022 David Allison <davidallisongithub@gmail.com>
 *
 *  Licensed under GPL v3+
 */

package com.ichi2.anki.workarounds

import android.app.Activity
import android.os.Bundle
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.CrashReportService
import com.ichi2.anki.R
import com.ichi2.anki.exception.ManuallyReportedException
import com.ichi2.anki.showThemedToast
import com.ichi2.themes.Themes
import timber.log.Timber

/**
 * Handles an issue where the app is loaded via `bmgr`, which means [AnkiDroidApp.onCreate]
 * may not be called.
 *
 * Instead of killing the process (which caused a backup loop in #19050),
 * we now attempt recovery safely.
 */
object AppLoadedFromBackupWorkaround {
    /**
     * Checks if the app was started without a valid [AnkiDroidApp] instance and attempts recovery.
     *
     * @param savedInstanceState The bundle from the Activity's onCreate.
     * @param activitySuperOnCreate A lambda that calls the Activity's `super.onCreate(savedInstanceState)`.
     *                              This is required to prevent a `SuperNotCalledException`.
     *
     * @return `true` if the recovery flow was triggered and the calling Activity should terminate.
     *         `false` if the app started normally or recovery was successful.
     */
    fun Activity.showedActivityFailedScreen(
        savedInstanceState: Bundle?,
        activitySuperOnCreate: (Bundle?) -> Unit,
    ): Boolean {
        // Normal startup: The application is already initialized.
        if (AnkiDroidApp.isInitialized) {
            return false
        }

        Timber.w("Activity started with no application instance — attempting recovery")

        // --- Recovery Attempt ---
        // The OS has instantiated the Application object, but onCreate() may not have been called.
        // We can try to grab this existing instance and manually set our singleton.
        try {
            val app = this.application as? AnkiDroidApp
            if (app != null) {
                // Use reflection to set the static instance.
                AnkiDroidApp.internalSetInstanceValue(app)

                // Verify if recovery was successful.
                if (AnkiDroidApp.isInitialized) {
                    Timber.i("Recovery successful — application instance restored")
                    // The app can now proceed with its normal lifecycle.
                    // We must still run app.onCreate() to initialize other components.
                    app.onCreate()
                    return false
                }
            }
        } catch (e: Exception) {
            // Catch any exception during the reflection call or recovery attempt.
            Timber.e(e, "Recovery attempt failed")
        }

        // --- Recovery Failed ---
        // If we reach this point, recovery was not possible. We must exit gracefully.
        Timber.e("Recovery failed — showing backup in progress message and finishing activity.")

        // 1. Inform the user.
        showThemedToast(
            this,
            getString(R.string.ankidroid_cannot_open_after_backup_try_again),
            false,
        )

        // 2. Log the failure for diagnostics without crashing.
        CrashReportService.sendExceptionReport(
            ManuallyReportedException("19050: Activity started with no application instance and recovery failed."),
            origin = "showedActivityFailedScreen",
            context = this,
        )

        // 3. Ensure the Activity has a theme to avoid visual glitches.
        Themes.setTheme(this)

        // 4. Call super.onCreate() to satisfy the Android framework and avoid SuperNotCalledException.
        activitySuperOnCreate(savedInstanceState)

        // 5. Close all activities in the task gracefully. DO NOT kill the process.
        finishAffinity()

        // 6. Signal to the calling Activity that it should not continue its own onCreate logic.
        return true
    }
}
