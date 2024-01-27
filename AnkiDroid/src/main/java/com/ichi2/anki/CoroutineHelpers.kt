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

import android.app.Activity
import android.content.Context
import android.content.DialogInterface
import android.view.WindowManager
import android.view.WindowManager.BadTokenException
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.coroutineScope
import androidx.lifecycle.viewModelScope
import anki.collection.Progress
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.libanki.Collection
import com.ichi2.utils.message
import com.ichi2.utils.negativeButton
import com.ichi2.utils.positiveButton
import com.ichi2.utils.show
import com.ichi2.utils.title
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.ankiweb.rsdroid.Backend
import net.ankiweb.rsdroid.BackendException
import net.ankiweb.rsdroid.exceptions.BackendInterruptedException
import net.ankiweb.rsdroid.exceptions.BackendNetworkException
import net.ankiweb.rsdroid.exceptions.BackendSyncException
import timber.log.Timber
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Runs a suspend function that catches any uncaught errors and reports them to the user.
 * Errors from the backend contain localized text that is often suitable to show to the user as-is.
 * Other errors should ideally be handled in the block.
 */
fun CoroutineScope.launchCatching(
    context: CoroutineContext = EmptyCoroutineContext,
    errorMessageHandler: suspend (String) -> Unit,
    block: suspend () -> Unit
): Job {
    return launch(context) {
        try {
            block()
        } catch (cancellationException: CancellationException) {
            // CancellationException should be re-thrown to propagate it to the parent coroutine
            throw cancellationException
        } catch (backendException: BackendException) {
            Timber.w(backendException)
            val message = backendException.localizedMessage ?: backendException.toString()
            errorMessageHandler.invoke(message)
        } catch (exception: Exception) {
            Timber.w(exception)
            errorMessageHandler.invoke(exception.toString())
        }
    }
}

interface OnErrorListener {
    val onError: MutableSharedFlow<String>
}

fun <T> T.launchCatching(
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    block: suspend T.() -> Unit
): Job where T : ViewModel, T : OnErrorListener {
    return viewModelScope.launchCatching(
        dispatcher,
        { onError.emit(it) },
        { block() }
    )
}

/**
 * Runs a suspend function that catches any uncaught errors and reports them to the user.
 * Errors from the backend contain localized text that is often suitable to show to the user as-is.
 * Other errors should ideally be handled in the block.
 *
 * TODO: `Throwable.getLocalizedMessage()` might be null, and `BackendException` constructor
 *   accepts a null message, so `exc.localizedMessage!!` is probably dangerous.
 *   If not, add a comment explaining why, or refactor to have a method that returns
 *   a non-null localized message.
 */
suspend fun <T> FragmentActivity.runCatchingTask(
    errorMessage: String? = null,
    block: suspend () -> T?
): T? {
    // appends the pre-coroutine stack to the error message. Example:
    // at com.ichi2.anki.CoroutineHelpersKt.launchCatchingTask(CoroutineHelpers.kt:188)
    // at com.ichi2.anki.CoroutineHelpersKt.launchCatchingTask$default(CoroutineHelpers.kt:184)
    // at com.ichi2.anki.BackendBackupsKt.performBackupInBackground(BackendBackups.kt:26)
    //  This is only performed in DEBUG mode to reduce performance impact
    val callerTrace = if (BuildConfig.DEBUG) {
        Thread.currentThread().stackTrace
            .drop(14)
            .joinToString(prefix = "\tat ", separator = "\n\tat ")
    } else {
        null
    }

    try {
        return block()
    } catch (exc: Exception) {
        when (exc) {
            is CancellationException -> {
                throw exc // CancellationException should be re-thrown to propagate it to the parent coroutine
            }
            is BackendInterruptedException -> {
                Timber.w(exc, errorMessage)
                exc.localizedMessage?.let { showSnackbar(it) }
            }
            is BackendNetworkException, is BackendSyncException -> {
                // these exceptions do not generate worthwhile crash reports
                showError(this, exc.localizedMessage!!, exc, false)
            }
            is BackendException -> {
                Timber.e(exc, errorMessage)
                if (callerTrace != null) Timber.e(callerTrace)
                showError(this, exc.localizedMessage!!, exc)
            }
            else -> {
                Timber.e(exc, errorMessage)
                if (callerTrace != null) Timber.e(callerTrace)
                showError(this, exc.toString(), exc)
            }
        }
    }
    return null
}

/**
 * Returns CoroutineExceptionHandler which catches any uncaught exceptions and reports it to user
 * Errors from the backend contain localized text that is often suitable to show to the user as-is.
 * Other errors should ideally be handled in the block.
 *
 * Typically you'll want to use [launchCatchingTask] instead; this routine is mainly useful for
 * launching tasks in an activity that is not a lifecycleOwner.
 *
 * @return [CoroutineExceptionHandler]
 * @see [FragmentActivity.launchCatchingTask]
 */
fun getCoroutineExceptionHandler(activity: Activity, errorMessage: String? = null) =
    CoroutineExceptionHandler { _, throwable ->
        // No need to check for cancellation-exception, it does not gets caught by CoroutineExceptionHandler
        when (throwable) {
            is BackendInterruptedException -> {
                Timber.e(throwable, errorMessage)
                throwable.localizedMessage?.let { activity.showSnackbar(it) }
            }
            is BackendException -> {
                Timber.e(throwable, errorMessage)
                showError(activity, throwable.localizedMessage!!, throwable)
            }
            else -> {
                Timber.e(throwable, errorMessage)
                showError(activity, throwable.toString(), throwable)
            }
        }
    }

/**
 * Launch a job that catches any uncaught errors and reports them to the user.
 * Errors from the backend contain localized text that is often suitable to show to the user as-is.
 * Other errors should ideally be handled in the block.
 */
fun FragmentActivity.launchCatchingTask(
    errorMessage: String? = null,
    block: suspend CoroutineScope.() -> Unit
): Job {
    return lifecycle.coroutineScope.launch {
        runCatchingTask(errorMessage) { block() }
    }
}

/** See [FragmentActivity.launchCatchingTask] */
fun Fragment.launchCatchingTask(
    errorMessage: String? = null,
    block: suspend CoroutineScope.() -> Unit
): Job {
    return lifecycle.coroutineScope.launch {
        requireActivity().runCatchingTask(errorMessage) { block() }
    }
}

private fun showError(context: Context, msg: String, exception: Throwable, crashReport: Boolean = true) {
    try {
        AlertDialog.Builder(context).show {
            title(R.string.vague_error)
            message(text = msg)
            positiveButton(R.string.dialog_ok)
            if (crashReport) {
                setOnDismissListener {
                    CrashReportService.sendExceptionReport(
                        exception,
                        origin = context::class.java.simpleName
                    )
                }
            }
        }
    } catch (ex: BadTokenException) {
        // issue 12718: activity provided by `context` was not running
        Timber.w(ex, "unable to display error dialog")
    }
}

/** In most cases, you'll want [AnkiActivity.withProgress]
 * instead. This lower-level routine can be used to integrate your own
 * progress UI.
 */
suspend fun <T> Backend.withProgress(
    extractProgress: ProgressContext.() -> Unit,
    updateUi: ProgressContext.() -> Unit,
    block: suspend CoroutineScope.() -> T
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
 * Run the provided operation, showing a progress window until it completes.
 * Progress info is polled from the backend.
 *
 * Starts the progress dialog after 600ms so that quick operations don't just show
 * flashes of a dialog.
 */
suspend fun <T> FragmentActivity.withProgress(
    extractProgress: ProgressContext.() -> Unit,
    onCancel: ((Backend) -> Unit)? = { it.setWantsAbort() },
    @StringRes manualCancelButton: Int? = null,
    op: suspend () -> T
): T {
    val backend = CollectionManager.getBackend()
    return withProgressDialog(
        context = this@withProgress,
        onCancel = if (onCancel != null) {
            fun() { onCancel(backend) }
        } else {
            null
        },
        manualCancelButton = manualCancelButton
    ) { dialog ->
        backend.withProgress(
            extractProgress = extractProgress,
            updateUi = { updateDialog(dialog) }
        ) {
            op()
        }
    }
}

/**
 * Run the provided operation, showing a progress window with the provided
 * message until the operation completes.
 *
 * Starts the progress dialog after 600ms so that quick operations don't just show
 * flashes of a dialog.
 */
suspend fun <T> Activity.withProgress(
    message: String = resources.getString(R.string.dialog_processing),
    op: suspend () -> T
): T = withProgressDialog(
    context = this@withProgress,
    onCancel = null
) { dialog ->
    @Suppress("Deprecation") // ProgressDialog deprecation
    dialog.setMessage(message)
    op()
}

/** @see withProgress(String, ...) */
suspend fun <T> Fragment.withProgress(message: String, block: suspend () -> T): T =
    requireActivity().withProgress(message, block)

/** @see withProgress(String, ...) */
suspend fun <T> Activity.withProgress(@StringRes messageId: Int, block: suspend () -> T): T =
    withProgress(resources.getString(messageId), block)

/** @see withProgress(String, ...) */
suspend fun <T> Fragment.withProgress(@StringRes messageId: Int, block: suspend () -> T): T =
    requireActivity().withProgress(messageId, block)

@Suppress("Deprecation") // ProgressDialog deprecation
suspend fun <T> withProgressDialog(
    context: Activity,
    onCancel: (() -> Unit)?,
    delayMillis: Long = 600,
    @StringRes manualCancelButton: Int? = null,
    op: suspend (android.app.ProgressDialog) -> T
): T = coroutineScope {
    val dialog = android.app.ProgressDialog(context, R.style.AppCompatProgressDialogStyle).apply {
        setCancelable(onCancel != null)
        if (manualCancelButton != null) {
            setCancelable(false)
            setButton(DialogInterface.BUTTON_NEGATIVE, context.getString(manualCancelButton)) { _, _ ->
                onCancel?.let { it() }
            }
        } else {
            onCancel?.let {
                setOnCancelListener { it() }
            }
        }
    }
    // disable taps immediately
    context.window.setFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE, WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
    // reveal the dialog after 600ms
    var dialogIsOurs = false
    val dialogJob = launch {
        delay(delayMillis)
        if (!AnkiDroidApp.instance.progressDialogShown) {
            dialog.show()
            AnkiDroidApp.instance.progressDialogShown = true
            dialogIsOurs = true
        }
    }
    try {
        op(dialog)
    } finally {
        dialogJob.cancel()
        dialog.dismiss()
        context.window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
        if (dialogIsOurs) {
            AnkiDroidApp.instance.progressDialogShown = false
        }
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
    updateUi: ProgressContext.() -> Unit
) {
    val state = ProgressContext(Progress.getDefaultInstance())
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
    var amount: Pair<Int, Int>? = null
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

/**
 * If a full sync is not already required, confirm the user wishes to proceed.
 * If the user agrees, the schema is bumped and the routine will return true.
 * On false, calling routine should abort.
 */
suspend fun AnkiActivity.userAcceptsSchemaChange(col: Collection): Boolean {
    if (col.schemaChanged()) {
        return true
    }
    return suspendCoroutine { coroutine ->
        AlertDialog.Builder(this).show {
            message(text = col.tr.deckConfigWillRequireFullSync()) // generic message
            positiveButton(R.string.dialog_ok) {
                col.modSchemaNoCheck()
                coroutine.resume(true)
            }
            negativeButton(R.string.dialog_cancel) { coroutine.resume(false) }
            setOnCancelListener { coroutine.resume(false) }
        }
    }
}

suspend fun AnkiActivity.userAcceptsSchemaChange(): Boolean {
    if (withCol { schemaChanged() }) {
        return true
    }
    val hasAcceptedSchemaChange = suspendCoroutine { coroutine ->
        AlertDialog.Builder(this).show {
            message(text = TR.deckConfigWillRequireFullSync().replace("\\s+".toRegex(), " "))
            positiveButton(R.string.dialog_ok) { coroutine.resume(true) }
            negativeButton(R.string.dialog_cancel) { coroutine.resume(false) }
            setOnCancelListener { coroutine.resume(false) }
        }
    }
    if (hasAcceptedSchemaChange) {
        withCol { modSchemaNoCheck() }
    }
    return hasAcceptedSchemaChange
}

/**
 * Ensures that current continuation is not [cancelled][CancellableContinuation.isCancelled].
 *
 * @throws [CancellationException] if canceled. This does not contain the original cancellation cause
*/
fun <T> CancellableContinuation<T>.ensureActive() {
    // we can't use .isActive here, or the exception would take precedence over a resumed exception
    if (isCancelled) throw CancellationException()
}
