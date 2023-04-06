/****************************************************************************************
 * Copyright (c) 2015 Timothy Rae <perceptualchaos2@gmail.com>                          *
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

package com.ichi2.anki

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Message
import androidx.annotation.CheckResult
import androidx.annotation.VisibleForTesting
import androidx.core.content.FileProvider
import com.google.android.material.snackbar.Snackbar
import com.ichi2.anki.dialogs.DialogHandler.Companion.storeMessage
import com.ichi2.anki.dialogs.DialogHandlerMessage
import com.ichi2.anki.servicelayer.ScopedStorageService
import com.ichi2.anki.services.ReminderService
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.themes.Themes.disableXiaomiForceDarkMode
import com.ichi2.utils.FileUtil
import com.ichi2.utils.ImportUtils.handleFileImport
import com.ichi2.utils.ImportUtils.isInvalidViewIntent
import com.ichi2.utils.ImportUtils.showImportUnsuccessfulDialog
import com.ichi2.utils.NetworkUtils
import com.ichi2.utils.Permissions.hasStorageAccessPermission
import com.ichi2.utils.copyToClipboard
import com.ichi2.utils.trimToLength
import timber.log.Timber
import java.io.File
import java.util.function.Consumer
import kotlin.math.max
import kotlin.math.min

/**
 * Class which handles how the application responds to different intents, forcing it to always be single task,
 * but allowing custom behavior depending on the intent
 *
 * @author Tim
 */
class IntentHandler : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Note: This is our entry point from the launcher with intent: android.intent.action.MAIN
        Timber.d("onCreate()")
        super.onCreate(savedInstanceState)
        disableXiaomiForceDarkMode(this)
        setContentView(R.layout.progress_bar)
        val intent = intent
        Timber.v(intent.toString())
        val reloadIntent = Intent(this, DeckPicker::class.java)
        reloadIntent.setDataAndType(getIntent().data, getIntent().type)
        val action = intent.action
        // #6157 - We want to block actions that need permissions we don't have, but not the default case
        // as this requires nothing
        val runIfStoragePermissions = Consumer { runnable: Runnable -> performActionIfStorageAccessible(runnable, reloadIntent, action) }
        when (getLaunchType(intent)) {
            LaunchType.FILE_IMPORT -> runIfStoragePermissions.accept(Runnable { handleFileImport(intent, reloadIntent, action) })
            LaunchType.SYNC -> runIfStoragePermissions.accept(Runnable { handleSyncIntent(reloadIntent, action) })
            LaunchType.REVIEW -> runIfStoragePermissions.accept(Runnable { handleReviewIntent(intent) })
            LaunchType.DEFAULT_START_APP_IF_NEW -> {
                Timber.d("onCreate() performing default action")
                launchDeckPickerIfNoOtherTasks(reloadIntent)
            }
            LaunchType.COPY_DEBUG_INFO -> {
                copyDebugInfoToClipboard(intent)
                finish()
            }
        }
    }

    private fun copyDebugInfoToClipboard(intent: Intent) {
        Timber.i("Copying debug info to clipboard")
        if (!this.copyToClipboard(intent.getStringExtra(CLIPBOARD_INTENT_EXTRA_DATA)!!)) {
            Timber.w("Failed to obtain ClipboardManager")
            showSnackbar(R.string.something_wrong, Snackbar.LENGTH_SHORT)
            return
        }

        showSnackbar(R.string.about_ankidroid_successfully_copied_debug_info, Snackbar.LENGTH_SHORT)
    }

    /**
     * Execute the runnable if one of the two following conditions are satisfied:
     *
     *  * AnkiDroid is using an app-private directory to store user data
     *  * AnkiDroid is using a legacy directory to store user data but has access to it since storage permission
     * has been granted (as long as AnkiDroid targeted API < 30, requested legacy storage, and has not been uninstalled since)
     *
     */
    private fun performActionIfStorageAccessible(runnable: Runnable, reloadIntent: Intent, action: String?) {
        if (!ScopedStorageService.isLegacyStorage(this) || hasStorageAccessPermission(this)) {
            Timber.i("User has storage permissions. Running intent: %s", action)
            runnable.run()
        } else {
            Timber.i("No Storage Permission, cancelling intent '%s'", action)
            showSnackbar(getString(R.string.intent_handler_failed_no_storage_permission))
            launchDeckPickerIfNoOtherTasks(reloadIntent)
        }
    }

    private fun handleReviewIntent(intent: Intent) {
        val deckId = intent.getLongExtra(ReminderService.EXTRA_DECK_ID, 0)
        Timber.i("Handling intent to review deck '%d'", deckId)
        val reviewIntent = Intent(this, Reviewer::class.java)
        CollectionHelper.instance.getCol(this)!!.decks.select(deckId)
        startActivity(reviewIntent)
        AnkiActivity.finishActivityWithFade(this)
    }

    private fun handleSyncIntent(reloadIntent: Intent, action: String?) {
        Timber.i("Handling Sync Intent")
        sendDoSyncMsg()
        reloadIntent.action = action
        reloadIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        startActivity(reloadIntent)
        AnkiActivity.finishActivityWithFade(this)
    }

    private fun handleFileImport(intent: Intent, reloadIntent: Intent, action: String?) {
        Timber.i("Handling file import")
        val importResult = handleFileImport(this, intent)
        // Start DeckPicker if we correctly processed ACTION_VIEW
        if (importResult.isSuccess) {
            try {
                val file = File(intent.data!!.path!!)
                val fileUri = applicationContext?.let {
                    FileProvider.getUriForFile(
                        it,
                        it.applicationContext?.packageName + ".apkgfileprovider",
                        File(it.getExternalFilesDir(FileUtil.getDownloadDirectory()), file.name)
                    )
                }
                // TODO move the file deletion on a background thread
                contentResolver.delete(fileUri!!, null, null)
                Timber.i("onCreate() import successful and downloaded file deleted")
            } catch (e: Exception) {
                Timber.w(e, "onCreate() import successful and cannot delete file")
            }

            reloadIntent.action = action
            reloadIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            startActivity(reloadIntent)
            AnkiActivity.finishActivityWithFade(this)
        } else {
            Timber.i("File import failed")
            // Don't import the file if it didn't load properly or doesn't have apkg extension
            showImportUnsuccessfulDialog(this, importResult.humanReadableMessage, true)
        }
    }

    private fun launchDeckPickerIfNoOtherTasks(reloadIntent: Intent) {
        // Launcher intents should start DeckPicker if no other task exists,
        // otherwise go to previous task
        Timber.i("Launching DeckPicker")
        reloadIntent.action = Intent.ACTION_MAIN
        reloadIntent.addCategory(Intent.CATEGORY_LAUNCHER)
        reloadIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivityIfNeeded(reloadIntent, 0)
        finish()
    }

    // COULD_BE_BETTER: Also extract the parameters into here to reduce coupling
    @VisibleForTesting
    enum class LaunchType {
        DEFAULT_START_APP_IF_NEW, FILE_IMPORT, SYNC, REVIEW, COPY_DEBUG_INFO
    }

    companion object {
        private const val CLIPBOARD_INTENT = "com.ichi2.anki.COPY_DEBUG_INFO"
        private const val CLIPBOARD_INTENT_EXTRA_DATA = "clip_data"

        private fun isValidViewIntent(intent: Intent): Boolean {
            // Negating a negative because we want to call specific attention to the fact that it's invalid
            // #6312 - Smart Launcher provided an empty ACTION_VIEW, no point in importing here.
            return !isInvalidViewIntent(intent)
        }

        @VisibleForTesting
        @CheckResult
        fun getLaunchType(intent: Intent): LaunchType {
            val action = intent.action
            return if (Intent.ACTION_VIEW == action && isValidViewIntent(intent)) {
                LaunchType.FILE_IMPORT
            } else if ("com.ichi2.anki.DO_SYNC" == action) {
                LaunchType.SYNC
            } else if (intent.hasExtra(ReminderService.EXTRA_DECK_ID)) {
                LaunchType.REVIEW
            } else if (action == CLIPBOARD_INTENT) {
                LaunchType.COPY_DEBUG_INFO
            } else {
                LaunchType.DEFAULT_START_APP_IF_NEW
            }
        }

        /**
         * Send a Message to AnkiDroidApp so that the DialogMessageHandler forces a sync
         */
        fun sendDoSyncMsg() {
            // Store the message in AnkiDroidApp message holder, which is loaded later in AnkiActivity.onResume
            storeMessage(DoSync().toMessage())
        }

        fun copyStringToClipboardIntent(context: Context, textToCopy: String) =
            Intent(context, IntentHandler::class.java).also {
                it.action = CLIPBOARD_INTENT
                // max length for an intent is 500KB.
                // 25000 * 2 (bytes per char) = 50,000 bytes <<< 500KB
                it.putExtra(CLIPBOARD_INTENT_EXTRA_DATA, textToCopy.trimToLength(25000))
            }

        class DoSync : DialogHandlerMessage(
            which = WhichDialogHandler.MSG_DO_SYNC,
            analyticName = "DoSyncDialog"
        ) {
            override fun handleAsyncMessage(deckPicker: DeckPicker) {
                val preferences = AnkiDroidApp.getSharedPrefs(deckPicker)
                val res = deckPicker.resources
                val hkey = preferences.getString("hkey", "")
                val millisecondsSinceLastSync = millisecondsSinceLastSync(preferences)
                val limited = millisecondsSinceLastSync < INTENT_SYNC_MIN_INTERVAL
                if (!limited && hkey!!.isNotEmpty() && NetworkUtils.isOnline) {
                    deckPicker.sync()
                } else {
                    val err = res.getString(R.string.sync_error)
                    if (limited) {
                        val remainingTimeInSeconds = max((INTENT_SYNC_MIN_INTERVAL - millisecondsSinceLastSync) / 1000, 1)
                        // getQuantityString needs an int
                        val remaining = min(Int.MAX_VALUE.toLong(), remainingTimeInSeconds).toInt()
                        val message = res.getQuantityString(R.plurals.sync_automatic_sync_needs_more_time, remaining, remaining)
                        deckPicker.showSimpleNotification(err, message, Channel.SYNC)
                    } else {
                        deckPicker.showSimpleNotification(err, res.getString(R.string.youre_offline), Channel.SYNC)
                    }
                }
                deckPicker.finishWithoutAnimation()
            }

            override fun toMessage(): Message = emptyMessage(this.what)

            companion object {
                const val INTENT_SYNC_MIN_INTERVAL = (
                    2 * 60000 // 2min minimum sync interval
                    ).toLong()
            }
        }
    }
}
