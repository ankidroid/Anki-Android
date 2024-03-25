/*
 *  Copyright (c) 2024 Brayan Oliveira <brayandso.dev@gmail.com>
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
import anki.sync.MediaSyncProgress
import anki.sync.SyncAuth
import anki.sync.syncAuth
import com.ichi2.anki.Channel
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.R
import com.ichi2.anki.cancelMediaSync
import com.ichi2.anki.notifications.NotificationId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import timber.log.Timber

class SyncMediaWorker(
    context: Context,
    parameters: WorkerParameters
) : CoroutineWorker(context, parameters) {

    private val workManager = WorkManager.getInstance(context)
    private val notificationManager = NotificationManagerCompat.from(context)

    override suspend fun doWork(): Result {
        Timber.v("SyncMediaWorker::doWork")
        setForeground(getForegroundInfo())

        try {
            val syncAuth = syncAuth {
                hkey = inputData.getString(HKEY_KEY)!!
                inputData.getString(ENDPOINT_KEY)?.let {
                    endpoint = it
                }
            }
            withCol { backend.syncMedia(syncAuth) }
            val backend = CollectionManager.getBackend()
            var syncProgress: MediaSyncProgress? = null
            while (true) {
                val status = backend.mediaSyncStatus()
                if (!status.active) {
                    hideNotification()
                    return Result.success()
                }
                // avoid sending repeated notifications
                if (syncProgress != status.progress) {
                    syncProgress = status.progress
                    // TODO display better the result. In tests, using setContentText lead to
                    //  truncated text if it had more than two lines.
                    // `added`, `removed` and `checked` already come translated from the backend
                    val notificationText = syncProgress.run { "$added $removed $checked" }
                    notify(getProgressNotification(notificationText))
                }
                delay(100)
            }
        } catch (cancellationException: CancellationException) {
            Timber.w(cancellationException)
            cancelMediaSync(CollectionManager.getBackend())
            hideNotification()
            throw cancellationException
        } catch (throwable: Throwable) {
            Timber.w(throwable)
            notify(buildNotification { setContentTitle(CollectionManager.TR.syncMediaFailed()) })
            return Result.failure()
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val notification = buildNotification {
            setContentTitle(applicationContext.getString(R.string.syncing_media))
            setOngoing(true)
            setProgress(0, 0, true)
            foregroundServiceBehavior = NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NotificationId.SYNC_MEDIA, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NotificationId.SYNC_MEDIA, notification)
        }
    }

    private fun notify(notification: Notification) =
        notificationManager.notify(NotificationId.SYNC_MEDIA, notification)

    private fun hideNotification() =
        notificationManager.cancel(NotificationId.SYNC_MEDIA)

    private fun buildNotification(block: NotificationCompat.Builder.() -> Unit): Notification {
        return NotificationCompat.Builder(applicationContext, Channel.SYNC.id).apply {
            priority = NotificationCompat.PRIORITY_LOW
            setSmallIcon(R.drawable.ic_star_notify)
            setCategory(NotificationCompat.CATEGORY_PROGRESS)
            setSilent(true)
            contentView
            block()
        }.build()
    }

    private fun getProgressNotification(progress: CharSequence): Notification {
        val title = applicationContext.getString(R.string.syncing_media)

        val cancelTitle = applicationContext.getString(R.string.dialog_cancel)
        val cancelIntent = workManager.createCancelPendingIntent(id)

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

        fun start(context: Context, syncAuth: SyncAuth) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val data = Data.Builder()
                .putString(HKEY_KEY, syncAuth.hkey)
                .putString(ENDPOINT_KEY, syncAuth.endpoint)
                .build()

            val request = OneTimeWorkRequestBuilder<SyncMediaWorker>()
                .setInputData(data)
                .setConstraints(constraints)
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()

            WorkManager.getInstance(context)
                .beginUniqueWork(UniqueWorkNames.SYNC_MEDIA, ExistingWorkPolicy.KEEP, request)
                .enqueue()
        }
    }
}
