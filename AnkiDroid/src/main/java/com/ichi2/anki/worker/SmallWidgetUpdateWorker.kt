/****************************************************************************************
 * Copyright (c) 2025 Fox - Real Code Fixes <foxyfnaf.93yt@gmail.com>                          *
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

package com.ichi2.anki.worker

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.ichi2.widget.WidgetStatus
import timber.log.Timber

class SmallWidgetUpdateWorker(
    val appContext: Context,
    workerParams: WorkerParameters,
) : Worker(appContext, workerParams) {
    override fun doWork(): Result {
        try {
            WidgetStatus.updateInBackground(appContext)
            return Result.success()
        }catch (e: Exception){
            val message = e.message ?: "Unknown error"
            Timber.d(message)
            return Result.failure()
        }
    }
}
