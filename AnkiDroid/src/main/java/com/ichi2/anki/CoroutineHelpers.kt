/***************************************************************************************
 * Copyright (c) 2022 Ankitects Pty Ltd <https://apps.ankiweb.net>                       *
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
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.coroutineScope
import anki.collection.Progress
import com.ichi2.anki.UIUtils.showSimpleSnackbar
import com.ichi2.libanki.CollectionV16
import com.ichi2.themes.StyledProgressDialog
import kotlinx.coroutines.*
import net.ankiweb.rsdroid.Backend
import net.ankiweb.rsdroid.BackendException
import net.ankiweb.rsdroid.exceptions.BackendInterruptedException
import timber.log.Timber

/**
 * Launch a job that catches any uncaught errors and reports them to the user.
 * Errors from the backend contain localized text that is often suitable to show to the user as-is.
 * Other errors should ideally be handled in the block.
 */
fun AnkiActivity.launchCatchingTask(
    block: suspend CoroutineScope.() -> Unit
): Job {
    return lifecycle.coroutineScope.launch {
        try {
            block()
        } catch (exc: BackendInterruptedException) {
            Timber.e("caught: %s", exc)
            showSimpleSnackbar(this@launchCatchingTask, exc.localizedMessage, false)
        } catch (exc: BackendException) {
            Timber.e("caught: %s", exc)
            showError(this@launchCatchingTask, exc.localizedMessage!!)
        } catch (exc: Exception) {
            Timber.e("caught: %s", exc)
            showError(this@launchCatchingTask, exc.toString())
        }
    }
}

private fun showError(context: Context, msg: String) {
    AlertDialog.Builder(context)
        .setTitle(R.string.vague_error)
        .setMessage(msg)
        .setPositiveButton(R.string.dialog_ok) { _, _ -> }
        .show()
}

/** Launch a catching task that requires a collection with the new schema enabled. */
fun AnkiActivity.launchCatchingCollectionTask(block: suspend CoroutineScope.(col: CollectionV16) -> Unit): Job {
    val col = CollectionHelper.getInstance().getCol(baseContext).newBackend
    return launchCatchingTask {
        block(col)
    }
}

/** Run a blocking call in a background thread pool. */
suspend fun <T> runInBackground(block: suspend CoroutineScope.() -> T): T {
    return withContext(Dispatchers.IO) {
        block()
    }
}

/** In most cases, you'll want [AnkiActivity.runInBackgroundWithProgress]
 * instead. This lower-level routine can be used to integrate your own
 * progress UI.
 */
suspend fun <T> Backend.withProgress(
    extractProgress: ProgressContext.() -> Unit,
    updateUi: ProgressContext.() -> Unit,
    block: suspend CoroutineScope.() -> T,
): T {
    return coroutineScope {
        val monitor = launch {
            monitorProgress(this@withProgress, extractProgress, updateUi)
        }
        try {
            block()
        } finally {
            monitor.cancel()
        }
    }
}

/**
 * Run the provided operation in the background, showing a progress
 * window. Progress info is polled from the backend.
 */
suspend fun <T> AnkiActivity.runInBackgroundWithProgress(
    backend: Backend,
    extractProgress: ProgressContext.() -> Unit,
    onCancel: ((Backend) -> Unit)? = { it.setWantsAbort() },
    op: suspend () -> T
): T = withProgressDialog(
    context = this@runInBackgroundWithProgress,
    onCancel = if (onCancel != null) {
        fun() { onCancel(backend) }
    } else {
        null
    }
) { dialog ->
    backend.withProgress(
        extractProgress = extractProgress,
        updateUi = { updateDialog(dialog) }
    ) {
        runInBackground { op() }
    }
}

/**
 * Run the provided operation in the background, showing a progress
 * window with the provided message.
 */
suspend fun <T> AnkiActivity.runInBackgroundWithProgress(
    message: String = "",
    op: suspend () -> T
): T = withProgressDialog(
    context = this@runInBackgroundWithProgress,
    onCancel = null
) { dialog ->
    @Suppress("Deprecation") // ProgressDialog deprecation
    dialog.setMessage(message)
    runInBackground {
        op()
    }
}

private suspend fun <T> withProgressDialog(
    context: AnkiActivity,
    onCancel: (() -> Unit)?,
    @Suppress("Deprecation") // ProgressDialog deprecation
    op: suspend (android.app.ProgressDialog) -> T
): T {
    val dialog = StyledProgressDialog.show(
        context, null,
        null, onCancel != null
    )
    onCancel?.let {
        dialog.setOnCancelListener { it() }
    }
    return try {
        op(dialog)
    } finally {
        dialog.dismiss()
    }
}

/**
 * Poll the backend for progress info every 100ms until cancelled by caller.
 * Calls extractProgress() to gather progress info and write it into
 * [ProgressContext]. Calls updateUi() to update the UI with the extracted
 * progress.
 */
private suspend fun monitorProgress(
    backend: Backend,
    extractProgress: ProgressContext.() -> Unit,
    updateUi: ProgressContext.() -> Unit,
) {
    var state = ProgressContext(Progress.getDefaultInstance())
    while (true) {
        state.progress = backend.latestProgress()
        state.extractProgress()
        // on main thread, so op can update UI
        withContext(Dispatchers.Main) {
            state.updateUi()
        }
        delay(100)
    }
}

/** Holds the current backend progress, and text/amount properties
 * that can be written to in order to update the UI.
 */
data class ProgressContext(
    var progress: Progress,
    var text: String = "",
    /** If set, shows progress bar with a of b complete. */
    var amount: Pair<Int, Int>? = null,
)

@Suppress("Deprecation") // ProgressDialog deprecation
private fun ProgressContext.updateDialog(dialog: android.app.ProgressDialog) {
    // ideally this would show a progress bar, but MaterialDialog does not support
    // setting progress after starting with indeterminate progress, so we just use
    // this for now
    // this code has since been updated to ProgressDialog, and the above not rechecked
    val progressText = amount?.let {
        " ${it.first}/${it.second}"
    } ?: ""
    @Suppress("Deprecation") // ProgressDialog deprecation
    dialog.setMessage(text + progressText)
}
