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

package com.ichi2.anki.services

import android.app.Notification
import android.content.Context
import android.content.Intent
import android.os.Build
import android.text.format.Formatter
import androidx.core.app.NotificationCompat
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.ichi2.anki.*
import com.ichi2.anki.servicelayer.ScopedStorageService.PREF_MIGRATION_DESTINATION
import com.ichi2.anki.servicelayer.ScopedStorageService.PREF_MIGRATION_SOURCE
import com.ichi2.anki.servicelayer.ScopedStorageService.isLegacyStorage
import com.ichi2.anki.servicelayer.ScopedStorageService.userMigrationIsInProgress
import com.ichi2.anki.servicelayer.scopedstorage.MigrateEssentialFiles
import com.ichi2.anki.servicelayer.scopedstorage.MoveConflictedFile
import com.ichi2.anki.servicelayer.scopedstorage.migrateuserdata.MigrateUserData
import com.ichi2.compat.CompatHelper
import com.ichi2.utils.FileUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber
import java.io.File
import kotlin.properties.ReadOnlyProperty

/**
 * A service which migrates the AnkiDroid collection from a legacy directory to an app-private directory.
 */
class MigrationService : ServiceWithALifecycleScope(), ServiceWithASimpleBinder<MigrationService> {

    sealed interface Progress {
        object CalculatingTransferSize : Progress

        data class Transferring(val transferredBytes: Long, val totalBytes: Long) : Progress {
            val ratio get() = if (totalBytes == 0L) 1f else transferredBytes.toFloat() / totalBytes
        }

        sealed interface Done : Progress
        object Success : Done
        data class Failure(val e: Exception) : Done
    }

    var flowOfProgress: MutableStateFlow<Progress> = MutableStateFlow(Progress.CalculatingTransferSize)

    private lateinit var migrateUserDataTask: MigrateUserData

    private var serviceHasBeenStarted = false

    // To simplify things by allowing binding to the service at any time,
    // make sure the service has the correct progress emitted even if it is not going to be started.
    override fun onCreate() {
        if (userMigrationHasSucceeded) flowOfProgress.tryEmit(Progress.Success)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Timber.w("onStartCommand(%s, ...)", intent)

        if (serviceHasBeenStarted) {
            Timber.w("MigrationService.onStartCommand has been called twice")
            return START_STICKY
        }

        serviceHasBeenStarted = true

        lifecycleScope.launch(Dispatchers.IO) {
            flowOfProgress.emit(Progress.CalculatingTransferSize)

            try {
                migrateUserDataTask = MigrateUserData
                    .createInstance(AnkiDroidApp.getSharedPrefs(this@MigrationService))

                val totalBytesToTransfer = getRemainingTransferSize(migrateUserDataTask)
                var transferredBytes = 0L

                migrateUserDataTask.migrateFiles(progressListener = { deltaTransferredBytes ->
                    transferredBytes += deltaTransferredBytes
                    flowOfProgress.tryEmit(Progress.Transferring(transferredBytes, totalBytesToTransfer))
                })

                // TODO BEFORE-MERGE This is probably not supposed to be here
                AnkiDroidApp.getSharedPrefs(this@MigrationService).edit {
                    remove(PREF_MIGRATION_DESTINATION)
                    remove(PREF_MIGRATION_SOURCE)
                }

                flowOfProgress.emit(Progress.Success)
            } catch (e: Exception) {
                CrashReportService.sendExceptionReport(e, "Storage migration failed")
                flowOfProgress.emit(Progress.Failure(e))
            }
        }

        lifecycleScope.launch {
            flowOfProgress.collect { progress ->
                startForeground(2, makeMigrationProgressNotification(progress))

                // TODO BEFORE-RELEASE Replace the toast with a dialog that is shown either
                //   * now, if deck picker is shown,
                //   * whenever the deck picker is shown, if not, perhaps even after app death.
                if (progress is Progress.Success) {
                    UIUtils.showThemedToast(this@MigrationService, R.string.migration_successful_message, true)
                }

                if (progress is Progress.Done) {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                        @Suppress("DEPRECATION")
                        stopForeground(false)
                    } else {
                        stopForeground(STOP_FOREGROUND_DETACH)
                    }

                    stopSelf()
                }
            }
        }

        return START_STICKY
    }

    // TODO BEFORE-RELEASE! This is inadequate, instead of calculating the remaining transfer size
    //   every time migration is started,
    //   we should be calculating this size only when starting migration for the first time,
    //   and keeping track of the transferred and the remaining files.
    // TODO BEFORE-RELEASE! Between this call and the subsequent migration
    //   the contents of the folder can change. This can lead to inconsistent readings in the UI.
    private fun getRemainingTransferSize(task: MigrateUserData): Long {
        val ignoredFiles = MigrateEssentialFiles.iterateEssentialFiles(task.source) +
            File(task.source.directory, MoveConflictedFile.CONFLICT_DIRECTORY)
        val ignoredSpace = ignoredFiles.sumOf { FileUtil.getSize(it) }
        val folderSize = FileUtil.DirectoryContentInformation.fromDirectory(task.source.directory).totalBytes
        val remainingSpaceToMigrate = folderSize - ignoredSpace
        Timber.d("folder size: %d, safe: %d, remaining: %d", folderSize, ignoredSpace, remainingSpaceToMigrate)
        return remainingSpaceToMigrate
    }

    override fun onBind(intent: Intent) = SimpleBinder(this)

    /**
     * A file was expected at the provided location, but wasn't found
     * If it exists in the old location, attempt to migrate it.
     * Block until migrated.
     *
     * @return Whether the migration was successful (or unnecessary)
     */
    fun migrateFileImmediately(expectedFileLocation: File): Boolean {
        try {
            migrateUserDataTask.migrateFileImmediately(expectedFileLocation)
        } catch (e: Exception) {
            Timber.w(e, "Failed to migrate file")
        }
        return expectedFileLocation.exists()
    }
}

private fun Context.makeMigrationProgressNotification(progress: MigrationService.Progress): Notification {
    val builder = NotificationCompat.Builder(this, Channel.SCOPED_STORAGE_MIGRATION.id)
        .setSmallIcon(R.drawable.ic_star_notify)
        .setContentTitle(getString(R.string.migration__migrating_data))
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setSilent(true)

    when (progress) {
        is MigrationService.Progress.CalculatingTransferSize -> {
            builder.setOngoing(true)
            builder.setProgress(0, 0, true)
            builder.setContentText(getString(R.string.migration__calculating_transfer_size))
        }

        is MigrationService.Progress.Transferring -> {
            val transferredSizeText = Formatter.formatShortFileSize(this, progress.transferredBytes)
            val totalSizeText = Formatter.formatShortFileSize(this, progress.totalBytes)

            builder.setOngoing(true)
            builder.setProgress(Int.MAX_VALUE, (progress.ratio * Int.MAX_VALUE).toInt(), false)
            builder.setContentText(getString(R.string.migration__transferred_x_of_y, transferredSizeText, totalSizeText))
        }

        is MigrationService.Progress.Success -> {
            builder.setProgress(100, 100, false)
            builder.setContentText(getString(R.string.migration_successful_message))
        }

        // TODO BEFORE-RELEASE Add a “Get help” button
        is MigrationService.Progress.Failure -> {
            val copyDebugInfoIntent = IntentHandler
                .copyStringToClipboardIntent(this, progress.e.stackTraceToString())
            val copyDebugInfoPendingIntent = CompatHelper.compat
                .getImmutableActivityIntent(this, 1, copyDebugInfoIntent, 0)

            builder.addAction(R.drawable.ic_star_notify, getString(R.string.feedback_copy_debug), copyDebugInfoPendingIntent)
            builder.setContentText(getString(R.string.migration__failed, progress.e))
        }
    }

    return builder.build()
}

/**
 * A delegate for a property that yields:
 *   * the [MigrationService] if the migration is in progress, and when the owner is started,
 *   * or `null` otherwise.
 *
 * Note: binding to the service happens fast, but not immediately,
 * so expect this property to be `null` when reading right after `onStart()`.
 */
fun <O> O.migrationServiceWhileStartedOrNull(): ReadOnlyProperty<Any?, MigrationService?>
        where O : Context, O : LifecycleOwner {
    var service: MigrationService? = null

    lifecycleScope.launch {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            if (userMigrationIsInProgress(this@migrationServiceWhileStartedOrNull)) {
                try {
                    withBoundTo<MigrationService> {
                        service = it
                        suspendCancellableCoroutine {}
                    }
                } finally {
                    service = null
                }
            }
        }
    }

    return ReadOnlyProperty { _, _ -> service }
}

/**
 * This assumes that the service is only created if the migration is, was, or is going to run,
 * that is when it can "succeed" at all.
 *
 * See also the logic in [com.ichi2.anki.DeckPicker.shouldOfferToUpgrade]
 */
private val Context.userMigrationHasSucceeded get() =
    !userMigrationIsInProgress(this) && !isLegacyStorage(this)
