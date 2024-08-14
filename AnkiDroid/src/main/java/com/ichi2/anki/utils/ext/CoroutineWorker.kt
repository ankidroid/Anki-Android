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
package com.ichi2.anki.utils.ext

import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import timber.log.Timber

/**
 * try-catch a [CoroutineWorker.setForeground] call, which may throw if the app isn't able
 * to run in the foreground at the point
 */
suspend fun CoroutineWorker.trySetForeground(foregroundInfo: ForegroundInfo) {
    return try {
        setForeground(foregroundInfo)
    } catch (error: Throwable) {
        Timber.w(error)
    }
}
