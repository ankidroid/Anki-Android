// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2024 Brayan Oliveira <brayandso.dev@gmail.com>

package com.ichi2.anki.worker

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.annotation.VisibleForTesting
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import anki.sync.MediaSyncProgress
import anki.sync.SyncAuth
import anki.sync.syncAuth
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.NotificationChannel
import com.ichi2.anki.R
import com.ichi2.anki.cancelMediaSync
import com.ichi2.anki.notifications.NotificationId
import com.ichi2.anki.receiver.CopyToClipboardReceiver
import com.ichi2.anki.ui.internationalization.sentenceCase
import com.ichi2.anki.utils.ext.trySetForeground
import com.ichi2.utils.Permissions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import net.ankiweb.rsdroid.Backend
import timber.log.Timber

class SyncMediaWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {
    private val cancelIntent = WorkManager.getInstance(context).createCancelPendingIntent(id)
    private val notificationManager: NotificationManagerCompat? =
        if (Permissions.canPostNotifications(context)) {
            NotificationManagerCompat.from(context)
        } else {
            null
        }

    override suspend fun doWork(): Result {
        Timber.v("SyncMediaWorker::doWork")

        try {
            val auth =
                syncAuth {
                    hkey = inputData.getString(HKEY_KEY)!!
                    inputData.getString(ENDPOINT_KEY)?.let {
                        endpoint = it
                    }
                }

            // The collection must be open, but we should not block collection operations while
            // `syncMedia` is executing, the app should be usable during a background media sync
            val backend = CollectionManager.getColUnsafe().backend
            backend.syncMedia(input = auth)

            delay(1000) // avoid notifications if sync occurs too quickly
            if (backend.mediaSyncStatus().active) {
                Timber.i("Showing SyncMediaWorker's notification")
                trySetForeground(getForegroundInfo())
                monitorProgress(backend)
            }
        } catch (cancellationException: CancellationException) {
            Timber.w(cancellationException, "SyncMediaWorker cancelled (user tapped Cancel or WorkManager cancelled)")
            notificationManager?.cancel(NotificationId.SYNC_MEDIA)
            Timber.d("SyncMediaWorker: progress notification cancelled after worker cancellation")
            cancelMediaSync(CollectionManager.getBackend())
            throw cancellationException
        } catch (throwable: Throwable) {
            Timber.w(throwable, "SyncMediaWorker failed")
            notify {
                // TODO: add a contentIntent, so tapping the notification opens the app
                //  (ideally showing the media sync log: an AlertDialog in DeckPicker)
                setContentTitle(TR.syncMediaFailed())
                throwable.localizedMessage?.let { message ->
                    setContentText(message)
                    setStyle(
                        NotificationCompat
                            .BigTextStyle()
                            .bigText(message),
                    )
                    addAction(
                        R.drawable.baseline_content_copy_24,
                        with(applicationContext) { TR.sentenceCase.copyToClipboard },
                        getCopyToClipboardIntent(message),
                    )
                }
            }
            Timber.d("SyncMediaWorker: showing failure notification")
            return Result.failure()
        }
        Timber.d("SyncMediaWorker: cancelling progress notification (sync completed)")
        notificationManager?.cancel(NotificationId.SYNC_MEDIA)

        Timber.d("SyncMediaWorker: success")
        return Result.success()
    }

    private suspend fun monitorProgress(backend: Backend) {
        var syncProgress: MediaSyncProgress? = null
        while (true) {
            val status = backend.mediaSyncStatus()
            if (!status.active) {
                Timber.i("Ended media sync notification updates")
                break
            }
            // avoid sending repeated notifications
            if (syncProgress != status.progress) {
                syncProgress = status.progress
                // TODO display better the result. Using setContentText leads to
                //  truncated text if it has more than two lines.
                // `added`, `removed` and `checked` already come translated from the
                val notificationText = syncProgress.run { "$added $removed $checked" }
                notify(getProgressNotification(notificationText))
            }
            delay(NOTIFICATION_UPDATE_RATE_MS)
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val title = applicationContext.getString(R.string.syncing_media)
        val cancelTitle = TR.syncAbortButton()
        val notification =
            buildNotification {
                setContentTitle(title)
                setOngoing(true)
                setProgress(0, 0, true)
                addAction(R.drawable.close_icon, cancelTitle, cancelIntent)
                foregroundServiceBehavior = NotificationCompat.FOREGROUND_SERVICE_DEFERRED
            }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NotificationId.SYNC_MEDIA, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NotificationId.SYNC_MEDIA, notification)
        }
    }

    @VisibleForTesting
    internal fun getCopyToClipboardIntent(text: String): PendingIntent {
        val intent =
            Intent(applicationContext, CopyToClipboardReceiver::class.java).apply {
                putExtra(CopyToClipboardReceiver.EXTRA_SYNC_ERROR_LOG, text.take(MAX_ERROR_TEXT_LENGTH))
            }
        return PendingIntent.getBroadcast(
            applicationContext,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun notify(notification: Notification) = notificationManager?.notify(NotificationId.SYNC_MEDIA, notification)

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
        val title = applicationContext.getString(R.string.syncing_media)
        val cancelTitle = TR.syncAbortButton()

        return buildNotification {
            setContentTitle(title)
            setContentText(progress)
            setOngoing(true)
            addAction(R.drawable.close_icon, cancelTitle, cancelIntent)
        }
    }

    companion object {
        private const val HKEY_KEY = "hkey"
        private const val ENDPOINT_KEY = "endpoint"
        const val NOTIFICATION_UPDATE_RATE_MS = 500L

        /**
         * Maximum length of the error text placed in [getCopyToClipboardIntent].
         *
         * The notification and its intents must fit in the Binder transaction buffer (~1MB),
         * which an unusually large message (e.g. from a [StackOverflowError]) may exceed
         */
        @VisibleForTesting
        const val MAX_ERROR_TEXT_LENGTH = 100_000

        fun getWorkRequest(auth: SyncAuth): OneTimeWorkRequest {
            val constraints =
                Constraints
                    .Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

            val data =
                Data
                    .Builder()
                    .putString(HKEY_KEY, auth.hkey)
                    .putString(ENDPOINT_KEY, auth.endpoint)
                    .build()

            return OneTimeWorkRequestBuilder<SyncMediaWorker>()
                .setInputData(data)
                .setConstraints(constraints)
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
        }

        fun start(
            context: Context,
            auth: SyncAuth,
        ) {
            Timber.i("Launching background media sync")
            val request = getWorkRequest(auth)

            WorkManager
                .getInstance(context)
                .enqueueUniqueWork(UniqueWorkNames.SYNC_MEDIA, ExistingWorkPolicy.KEEP, request)
        }
    }
}
