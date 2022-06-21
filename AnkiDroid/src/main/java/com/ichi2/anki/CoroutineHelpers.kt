/***************************************************************************************
 * Copyright (c) 2022 Ankitects Pty Ltd <http://apps.ankiweb.net>                       *
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
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.coroutineScope
import anki.collection.Progress
import com.ichi2.anki.UIUtils.showSimpleSnackbar
import com.ichi2.libanki.CollectionV16
import com.ichi2.libanki.getProgress
import kotlinx.coroutines.*
import net.ankiweb.rsdroid.BackendException
import timber.log.Timber

/**
 * Launch a job that catches any uncaught errors and reports them to the user.
 * Errors from the backend contain localized text that is often suitable to show to the user as-is.
 * Other errors should ideally be handled in the block.
 */
// TODO: require user confirmation before message disappears
fun LifecycleOwner.catchingLifecycleScope(activity: Activity, block: suspend CoroutineScope.() -> Unit): Job {
    return lifecycle.coroutineScope.launch {
        try {
            block()
        } catch (exc: BackendException) {
            showSimpleSnackbar(activity, exc.localizedMessage, false)
        } catch (exc: Exception) {
            // TODO: localize
            showSimpleSnackbar(activity, "An error occurred: {exc}", false)
        }
    }
}

suspend fun <T> runInBackground(block: suspend CoroutineScope.() -> T): T {
    return withContext(Dispatchers.IO) {
        block()
    }
}

suspend fun <T> runInBackgroundWithProgress(
    col: CollectionV16,
    onProgress: (Progress) -> Unit,
    op: suspend (CollectionV16) -> T
): T = coroutineScope {
    val monitor = launch { monitorProgress(col, onProgress) }
    try {
        runInBackground { op(col) }
    } finally {
        monitor.cancel()
    }
}

suspend fun monitorProgress(col: CollectionV16, op: (Progress) -> Unit) {
    while (true) {
        try {
            val progress = col.getProgress()
            // on main thread, so op can update UI
            withContext(Dispatchers.Main) {
                op(progress)
            }
        } catch (exc: Exception) {
            Timber.e("exception in monitorProgress: $exc")
            return
        }
        delay(100)
    }
}
