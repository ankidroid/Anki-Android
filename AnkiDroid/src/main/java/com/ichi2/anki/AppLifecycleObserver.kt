/****************************************************************************************
 * Copyright (c) 2025 Fox Programmer <foxyfnaf.93yt@gmail.com>                          *
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

import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.ichi2.anki.worker.SmallWidgetUpdateWorker
import java.util.concurrent.TimeUnit

class AppLifecycleObserver(
    private val appContext: Context,
) : DefaultLifecycleObserver {
    override fun onStop(owner: LifecycleOwner) {
        super.onStop(owner)

        val workRequest =
            PeriodicWorkRequestBuilder<SmallWidgetUpdateWorker>(15, TimeUnit.MINUTES)
                .build()

        WorkManager.getInstance(appContext).enqueueUniquePeriodicWork(
            "Small Widget Update",
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest,
        )
    }
}
