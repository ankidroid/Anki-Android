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

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit
import com.ichi2.anki.*
import com.ichi2.anki.AnkiDroidApp.Companion.isAppInForeground
import com.ichi2.anki.AnkiDroidApp.Companion.pendingMigrationCompletedDialogOnActivityStart
import com.ichi2.anki.dialogs.MigrationSuccessDialogFragment
import com.ichi2.anki.servicelayer.ScopedStorageService.PREF_MIGRATION_DESTINATION
import com.ichi2.anki.servicelayer.ScopedStorageService.PREF_MIGRATION_SOURCE
import com.ichi2.anki.servicelayer.scopedstorage.MigrateEssentialFiles
import com.ichi2.anki.servicelayer.scopedstorage.MoveConflictedFile
import com.ichi2.anki.servicelayer.scopedstorage.migrateuserdata.MigrateUserData
import com.ichi2.anki.servicelayer.scopedstorage.migrateuserdata.NumberOfBytes
import com.ichi2.anki.servicelayer.scopedstorage.migrateuserdata.toKB
import com.ichi2.anki.servicelayer.scopedstorage.migrateuserdata.toMB
import com.ichi2.compat.CompatHelper.Companion.compat
import com.ichi2.utils.FileUtil
import com.ichi2.utils.Repeater
import com.ichi2.utils.runOnUiThread
import timber.log.Timber
import java.io.File
import kotlin.concurrent.thread

/**
 * A service which migrates the AnkiDroid collection from a legacy directory to an app-private directory.
 */
class MigrationService : Service() {
    private lateinit var migrateUserDataTask: MigrateUserData
    private lateinit var migrateDataThread: Thread
    private lateinit var notificationUpdater: Repeater
    private var isStarted = false

    /** the current progress (may be ahead of the notification) */
    var currentProgress: NumberOfBytes = 0
        private set

    /** the current progress which is displayed on the notification */
    var notificationDisplayedProgress: NumberOfBytes = 0
        private set

    /**
     * The total bytes required to be transferred. 0 on error
     * Note: currently this is recalculated each time the service is started
     */
    var totalToTransfer: NumberOfBytes? = null
        private set

    var migrationCompletedListener: (() -> Unit)? = null

    private inner class MigrateUserDataProgressListener(val context: Context) {
        private var notification: Notification? = null

        fun initNotification(totalToTransfer: NumberOfBytes?) {
            // startForeground must be called within 5 seconds. Otherwise a crash occurs:
            // `Context.startForegroundService() did not then call Service.startForeground()`
            val sourceSize = totalToTransfer ?: 0 // TODO: error handling
            notification = Notification.createInstance(context, sourceSize).also {
                Timber.i("Running in foreground with notification")
                startForeground(it.id, it.build())
            }

            notificationUpdater = Repeater.createAndStart(delayMs = 2000L) {
                notificationDisplayedProgress = currentProgress
                notification?.notifyUpdate(currentProgress)
            }
        }

        fun onProgressUpdate(value: NumberOfBytes?) {
            /** @see notificationUpdater for where this is used */
            currentProgress += value ?: 0
        }

        fun onResult(result: Boolean) {
            if (result) {
                Timber.i("Marking migration as completed")
                AnkiDroidApp.getSharedPrefs(context).edit {
                    remove(PREF_MIGRATION_DESTINATION)
                    remove(PREF_MIGRATION_SOURCE)
                }
                migrationCompletedListener?.invoke()
            }
            notification?.notifyCompletion(result)

            // display a toast to the user.
            displayMigrationCompleted(result)

            stopSelf()
        }

        private fun displayMigrationCompleted(result: Boolean) {
            val activity = AnkiDroidApp.currentActivity
            if (isAppInForeground && activity is AppCompatActivity) {
                val dialog = MigrationSuccessDialogFragment()
                runOnUiThread {
                    dialog.show(activity.supportFragmentManager, "MigrationCompletedDialog")
                }
            } else {
                pendingMigrationCompletedDialogOnActivityStart = true
            }
            val message =
                if (result) R.string.migration_successful_message else R.string.migration_failed_message
            // fixes: "Can't toast on a thread that has not called Looper.prepare()"
            runOnUiThread {
                UIUtils.showThemedToast(context, message, true)
            }
        }

        fun onError(e: Exception) {
            notificationUpdater.terminate()
            notification?.notifyError(e)
        }
    }

    private class Notification private constructor(
        private val context: Context,
        private val manager: NotificationManagerCompat,
        private val sourceSize: NumberOfBytes
    ) {
        private var notificationBuilder: NotificationCompat.Builder = NotificationCompat.Builder(
            context,
            Channel.SCOPED_STORAGE_MIGRATION.id
        )
            .setSmallIcon(R.drawable.ic_star_notify)
            .setContentTitle(context.resources.getString(R.string.migrating_data_message))
            .setContentText(context.resources.getString(R.string.migration_transferred_size, 0f, sourceSize / 1024f))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setSilent(true)
            .setProgress(100, 0, false)

        /** The id of the notification for in-progress user data migration. */
        val id = 2

        fun build() = this.notificationBuilder.build()
        fun notifyUpdate(currentProgress: NumberOfBytes) {
            Timber.v("update: %d", currentProgress)
            notificationBuilder.setProgress(sourceSize.toKB(), currentProgress.toKB(), false)
            notificationBuilder.setContentText(
                context.resources.getString(
                    R.string.migration_transferred_size,
                    currentProgress.toMB().toFloat(),
                    sourceSize.toMB().toFloat()
                )
            )
            manager.notify(id, notificationBuilder.build())
        }

        fun notifyCompletion(result: Boolean) {
            val titleRes = if (result) R.string.migration_successful_message else R.string.migration_failed_message
            val notificationTitle = context.resources.getString(titleRes)
            notificationBuilder.setContentTitle(notificationTitle)
                .setOngoing(false)
                .hideProgressBar()
            manager.notify(id, notificationBuilder.build())
        }

        fun notifyError(e: Exception) {
            // TODO: Add a button for 'Get Help'
            val copyIntent = IntentHandler.copyStringToClipboardIntent(this.context, e.toString())

            val copyDebugIntent = compat.getImmutableActivityIntent(this.context, COPY_DEBUG, copyIntent, 0)
            notificationBuilder.setContentTitle(context.getString(R.string.migration_failed_message))
                .setContentText(e.toString())
                .setOngoing(false)
                .hideProgressBar()
                .addAction(R.drawable.ic_star_notify, context.getString(R.string.feedback_copy_debug), copyDebugIntent)

            manager.notify(id, notificationBuilder.build())
        }

        companion object {
            const val COPY_DEBUG: Int = 1
            fun createInstance(context: Context, sourceSize: NumberOfBytes): Notification {
                val notificationManager = NotificationManagerCompat.from(context)
                return Notification(context, notificationManager, sourceSize)
            }
        }
    }

    private fun getRestartBehavior() = START_STICKY

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // If a service is called twice, onStartCommand is called twice
        if (isStarted) {
            Timber.v("rejected onStartCommand")
            return getRestartBehavior()
        }
        isStarted = true
        Timber.d("onStartCommand")

        val migrateUserDataTask = try {
            MigrateUserData.createInstance(AnkiDroidApp.getSharedPrefs(this))
        } catch (e: MigrateUserData.MissingDirectoryException) {
            // TODO: Log and handle - likely SD card removal
            throw e
        } catch (e: Exception) {
            stopSelf()
            return getRestartBehavior()
        }

        // a migration is not taking place
        if (migrateUserDataTask == null) {
            Timber.w("MigrationService started when a migration was not taking place")
            stopSelf()
            return getRestartBehavior()
        }

        this.migrateUserDataTask = migrateUserDataTask
        this.migrateDataThread = thread(name = "Storage Migration") {
            this.totalToTransfer = getRemainingTransferSize(migrateUserDataTask)
            val listener = MigrateUserDataProgressListener(this)
            listener.initNotification(totalToTransfer)
            try {
                val result = migrateUserDataTask.migrateFiles { bytesTransferred -> listener.onProgressUpdate(bytesTransferred) }
                listener.onResult(result)
            } catch (e: Exception) {
                CrashReportService.sendExceptionReport(e, "Storage Migration Failed")
                listener.onError(e)
            }
        }

        return getRestartBehavior()
    }

    override fun onDestroy() {
        Timber.d("onDestroy")
        if (::migrateUserDataTask.isInitialized) { migrateUserDataTask.executor.terminate() }
        if (::notificationUpdater.isInitialized) { notificationUpdater.terminate() }
        super.onDestroy()
    }

    private fun getRemainingTransferSize(
        task: MigrateUserData
    ): NumberOfBytes? {
        return try {
            val ignoredFiles = MigrateEssentialFiles.iterateEssentialFiles(task.source) +
                File(task.source.directory, MoveConflictedFile.CONFLICT_DIRECTORY)
            val ignoredSpace = ignoredFiles.sumOf { FileUtil.getSize(it) }
            val folderSize = FileUtil.DirectoryContentInformation.fromDirectory(task.source.directory).totalBytes
            val remainingSpaceToMigrate = folderSize - ignoredSpace
            Timber.d("folder size: %d, safe: %d, remaining: %d", folderSize, ignoredSpace, remainingSpaceToMigrate)
            return remainingSpaceToMigrate
        } catch (e: Exception) {
            Timber.w(e, "Failed to get directory size")
            null
        }
    }

    /**
     * Class used for the client Binder.  Because we know this service always
     * runs in the same process as its clients, we don't need to deal with IPC.
     *
     * See: https://developer.android.com/guide/components/bound-services#Binder
     */
    inner class LocalBinder : Binder(), SimpleBinder<MigrationService> {
        @Suppress("unused")
        override fun getService(): MigrationService = this@MigrationService
    }

    override fun onBind(intent: Intent): IBinder = LocalBinder()

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

/** Hides a progress bar if previously shown on a notification */
private fun NotificationCompat.Builder.hideProgressBar(): NotificationCompat.Builder =
    this.setProgress(0, 0, false)
