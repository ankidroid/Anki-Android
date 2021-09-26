/*
 *  Copyright (c) 2021 Farjad Ilyas <ilyasfarjad@gmail.com>
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
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.ichi2.anki.CollectionHelper
import com.ichi2.anki.NotificationChannels
import com.ichi2.anki.R
import com.ichi2.async.*
import com.ichi2.async.CollectionTask.MigrateUserData.MOVE
import com.ichi2.utils.FileUtil
import timber.log.Timber
import java.io.File

class MigrationService : Service() {

    companion object {
        /** The id of the notification for in-progress user data migration.  */
        private const val MIGRATION_NOTIFY_ID = 2
    }

    lateinit var notificationBuilder: NotificationCompat.Builder
    lateinit var notificationManager: NotificationManagerCompat
    lateinit var listener: TaskListenerWithContext<Context, Int, Boolean>
    var cancelled = false

    private inner class MigrateUserDataListener(sourceSize: Long?) : TaskListenerWithContext<Context, Int?, Boolean?>(this) {
        // All integer variables store data size in kilobytes
        private var mSourceSize = 0
        private var mCurrentProgress: Int
        private val mUpdateInterval = 2000
        private var mIncreaseSinceLastUpdate: Int
        private var mMostRecentUpdateTime = 0L

        override fun actualOnPreExecute(context: Context) {
            notificationManager = NotificationManagerCompat.from(context)
            if (!notificationManager.areNotificationsEnabled()) {
                Timber.v("MigrateUserDataListener - notifications disabled, returning")
                return
            }

            val channel = NotificationChannels.Channel.GENERAL

            notificationBuilder = NotificationCompat.Builder(
                context,
                NotificationChannels.getId(channel)
            )
                .setSmallIcon(R.drawable.ic_stat_notify)
                .setContentTitle(context.resources.getString(R.string.migrating_data_message))
                .setContentText(context.resources.getString(R.string.migration_transferred_size, 0f, mSourceSize / 1024f))
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .setSilent(true)
                .setProgress(100, 0, false)

            startForeground(MIGRATION_NOTIFY_ID, notificationBuilder.build())
        }

        override fun actualOnProgressUpdate(context: Context, value: Int?) {
            super.actualOnProgressUpdate(context, value)

            if (value == null) {
                return
            }

            // Convert progress in bytes to kilobytes
            mIncreaseSinceLastUpdate += value

            // Update Progress Bar progress if progress > 1% of max
            val currentTime = CollectionHelper.getInstance().getTimeSafe(context).intTimeMS()
            if (currentTime - mMostRecentUpdateTime > mUpdateInterval) {
                mMostRecentUpdateTime = currentTime
                mCurrentProgress += mIncreaseSinceLastUpdate
                mIncreaseSinceLastUpdate = 0
                notificationBuilder.setProgress(mSourceSize, mCurrentProgress, false)
                notificationBuilder.setContentText(
                    context.resources.getString(
                        R.string.migration_transferred_size,
                        mCurrentProgress / 1024f, mSourceSize / 1024f
                    )
                )
                notificationManager.notify(MIGRATION_NOTIFY_ID, notificationBuilder.build())
            }
        }

        override fun actualOnPostExecute(context: Context, result: Boolean?) {
            if (result == true) {
                notificationBuilder.setContentTitle(context.resources.getString(R.string.migration_successful_message))
            } else {
                notificationBuilder.setContentTitle(context.resources.getString(R.string.migration_failed_message))
            }

            notificationBuilder.setProgress(0, 0, false).setOngoing(false)
            notificationManager.notify(MIGRATION_NOTIFY_ID, notificationBuilder.build())

            stopSelf()
        }

        override fun onCancelled() {
            cancelled = true
            stopSelf()
        }

        init {

            // If sourceSize is not null, convert source size from bytes to kilobytes
            mSourceSize = if (sourceSize == null) {
                0
            } else {
                (sourceSize / 1024).toInt()
            }
            mCurrentProgress = 0
            mIncreaseSinceLastUpdate = 0
        }
    }

    override fun onCreate() {
        val sourceDir = File(CollectionHelper.getMigrationSourcePath(this))
        val destDir = File(CollectionHelper.getDefaultAnkiDroidDirectory(this))
        CoroutineTask(
            CollectionTask.MigrateUserData(sourceDir, destDir, MOVE),
            MigrateUserDataListener(FileUtil.getDirectorySize(sourceDir))
        ).execute()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }
}
