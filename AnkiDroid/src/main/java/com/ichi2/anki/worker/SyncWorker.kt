// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.worker

import android.app.Notification
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import anki.collection.Progress
import anki.sync.SyncCollectionResponse
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.CommonString
import com.ichi2.anki.NotificationChannel
import com.ichi2.anki.R
import com.ichi2.anki.cancelSync
import com.ichi2.anki.common.permissions.canPostNotifications
import com.ichi2.anki.notifications.NotificationId
import com.ichi2.anki.setLastSyncTimeToNow
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.sync.SyncAuth
import com.ichi2.anki.sync.syncCollection
import com.ichi2.anki.utils.ext.trySetForeground
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import timber.log.Timber
import kotlin.coroutines.cancellation.CancellationException

/**
 * Held while a background collection sync runs. A profile switch holds it until
 * the process dies, so a sync cannot start part way through a restart.
 */
val syncLock = Mutex()

/**
 * Syncs collection and media in the background.
 *
 * The collection will be blocked while synchronizing, so any component that
 * depends on it should be blocked as well until the task ends.
 *
 * Media is synced by enqueueing a [SyncMediaWorker] work, which doesn't block the collection.
 *
 * Note that one-way syncs will be ignored, since they require user input.
 *
 * That is useful when the user isn't interacting with
 * the app, like when doing an automatic sync after leaving the app.
 */
class SyncWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {
    private val workManager = WorkManager.getInstance(context)
    private val cancelIntent = WorkManager.getInstance(context).createCancelPendingIntent(id)
    private val notificationManager: NotificationManagerCompat? =
        if (canPostNotifications(context)) {
            NotificationManagerCompat.from(context)
        } else {
            null
        }

    override suspend fun doWork(): Result {
        Timber.v("SyncWorker::doWork")
        return syncLock.withLockUnlessSwitching { doWorkHoldingLock() }
    }

    private suspend fun doWorkHoldingLock(): Result {
        trySetForeground(getForegroundInfo())

        val auth = inputData.toSyncAuth() ?: return Result.failure()
        val shouldSyncMedia = inputData.getBoolean(SYNC_MEDIA_KEY, false)

        try {
            syncCollection(auth, shouldSyncMedia)
        } catch (cancellationException: CancellationException) {
            Timber.w(cancellationException, "SyncWorker cancelled (user tapped Cancel or WorkManager cancelled)")
            notificationManager?.cancel(NotificationId.SYNC)
            Timber.d("SyncWorker: progress notification cancelled after worker cancellation")
            cancelSync(CollectionManager.getBackend())
            throw cancellationException
        } catch (throwable: Throwable) {
            Timber.w(throwable, "SyncWorker failed")
            notify {
                setContentTitle(applicationContext.getString(CommonString.sync_error))
                throwable.localizedMessage?.let { message ->
                    setContentText(message)
                }
            }
            Timber.d("SyncWorker: showing failure notification")
            return Result.failure()
        }
        Timber.d("SyncWorker: cancelling progress notification (sync completed)")
        notificationManager?.cancel(NotificationId.SYNC)

        Timber.d("SyncWorker: success")
        setLastSyncTimeToNow()
        return Result.success()
    }

    private suspend fun syncCollection(
        auth: SyncAuth,
        syncMedia: Boolean,
    ) {
        Timber.v("SyncWorker::syncCollection")
        val response =
            coroutineScope {
                val monitor =
                    launch(Dispatchers.IO) {
                        val backend = CollectionManager.getBackend()
                        var syncProgress: Progress.NormalSync? = null
                        while (true) {
                            val progress = backend.latestProgress() // avoid sending repeated notifications
                            if (progress.hasNormalSync() && syncProgress != progress.normalSync) {
                                syncProgress = progress.normalSync
                                val text = syncProgress.run { "$added\n$removed" }
                                notify(getProgressNotification(text))
                            }
                            delay(SyncMediaWorker.NOTIFICATION_UPDATE_RATE_MS)
                        }
                    }
                try {
                    withCol {
                        syncCollection(auth, syncMedia = false)
                    }
                } finally {
                    Timber.d("Collection sync completed. Cancelling monitor...")
                    // this doesn't wait for completion; the outer `coroutineScope` does.
                    monitor.cancel()
                }
            }
        Timber.i("Sync required: %s", response.required)
        when (response.required) {
            // a successful sync returns this value
            SyncCollectionResponse.ChangesRequired.NO_CHANGES -> {
                withCol { _loadScheduler() } // scheduler version may have changed
                if (!syncMedia) return
                val syncAuth =
                    if (response.hasNewEndpoint() && response.newEndpoint.isNotEmpty()) {
                        Prefs.currentSyncUri = response.newEndpoint
                        auth.withEndpoint(response.newEndpoint)
                    } else {
                        auth
                    }
                syncMedia(syncAuth)
            }
            SyncCollectionResponse.ChangesRequired.FULL_SYNC,
            SyncCollectionResponse.ChangesRequired.FULL_DOWNLOAD,
            SyncCollectionResponse.ChangesRequired.FULL_UPLOAD,
            -> {
                Timber.d("One-way sync required: Skipping background sync")
            }
            SyncCollectionResponse.ChangesRequired.UNRECOGNIZED,
            SyncCollectionResponse.ChangesRequired.NORMAL_SYNC,
            null,
            -> {
                TODO("should never happen")
            }
        }
    }

    private fun syncMedia(auth: SyncAuth) {
        Timber.i("Enqueuing SyncMediaWorker")
        workManager.enqueueUniqueWork(
            UniqueWorkNames.SYNC_MEDIA,
            ExistingWorkPolicy.KEEP,
            SyncMediaWorker.getWorkRequest(auth),
        )
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val cancelTitle = applicationContext.getString(CommonString.dialog_cancel)
        val notification =
            buildNotification {
                setContentTitle(TR.syncSyncing())
                setOngoing(true)
                setProgress(0, 0, true)
                addAction(R.drawable.close_icon, cancelTitle, cancelIntent)
                foregroundServiceBehavior = NotificationCompat.FOREGROUND_SERVICE_DEFERRED
            }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NotificationId.SYNC, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NotificationId.SYNC, notification)
        }
    }

    private fun notify(notification: Notification) {
        notificationManager?.notify(NotificationId.SYNC, notification)
    }

    private fun notify(builder: NotificationCompat.Builder.() -> Unit) {
        notify(buildNotification(builder))
    }

    private fun buildNotification(block: NotificationCompat.Builder.() -> Unit): Notification =
        NotificationCompat
            .Builder(applicationContext, NotificationChannel.SYNC.id)
            .apply {
                priority = NotificationCompat.PRIORITY_LOW
                setSmallIcon(R.drawable.ic_star_notify)
                setCategory(NotificationCompat.CATEGORY_PROGRESS)
                setSilent(true)
                block()
            }.build()

    private fun getProgressNotification(progress: CharSequence): Notification {
        val cancelTitle = applicationContext.getString(CommonString.dialog_cancel)

        return buildNotification {
            setContentTitle(TR.syncSyncing())
            setContentText(progress)
            setOngoing(true)
            addAction(R.drawable.close_icon, cancelTitle, cancelIntent)
        }
    }

    companion object {
        private const val SYNC_MEDIA_KEY = "syncMedia"

        fun start(
            context: Context,
            syncAuth: SyncAuth,
            syncMedia: Boolean,
        ) {
            val constraints =
                Constraints
                    .Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

            val data =
                Data
                    .Builder()
                    .putAll(syncAuth.toWorkData())
                    .putBoolean(SYNC_MEDIA_KEY, syncMedia)
                    .build()

            val request =
                OneTimeWorkRequestBuilder<SyncWorker>()
                    .setInputData(data)
                    .setConstraints(constraints)
                    .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                    .build()

            WorkManager
                .getInstance(context)
                .enqueueUniqueWork(UniqueWorkNames.SYNC, ExistingWorkPolicy.KEEP, request)
        }

        fun cancel(context: Context) {
            WorkManager
                .getInstance(context)
                .cancelUniqueWork(UniqueWorkNames.SYNC)
        }
    }
}
