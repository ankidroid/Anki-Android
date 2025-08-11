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
import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.net.Uri
import android.view.WindowManager
import android.view.WindowManager.BadTokenException
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.coroutineScope
import androidx.lifecycle.viewModelScope
import anki.collection.Progress
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.CrashReportData.Companion.throwIfDialogUnusable
import com.ichi2.anki.CrashReportData.Companion.toCrashReportData
import com.ichi2.anki.CrashReportData.HelpAction
import com.ichi2.anki.CrashReportData.HelpAction.AnkiBackendLink
import com.ichi2.anki.CrashReportData.HelpAction.OpenDeckOptions
import com.ichi2.anki.common.annotations.UseContextParameter
import com.ichi2.anki.exception.StorageAccessException
import com.ichi2.anki.libanki.Collection
import com.ichi2.anki.pages.DeckOptionsDestination
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.anki.utils.openUrl
import com.ichi2.utils.create
import com.ichi2.utils.message
import com.ichi2.utils.negativeButton
import com.ichi2.utils.neutralButton
import com.ichi2.utils.positiveButton
import com.ichi2.utils.setupEnterKeyHandler
import com.ichi2.utils.show
import com.ichi2.utils.title
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
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
import org.jetbrains.annotations.VisibleForTesting
import timber.log.Timber
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/** Overridable reference to [Dispatchers.IO]. Useful if tests can't use it */
// COULD_BE_BETTER: this shouldn't be necessary, but TestClass::runWith needs it
@VisibleForTesting
var ioDispatcher: CoroutineDispatcher = Dispatchers.IO

/** Whether [showError] should throw an exception on failure */
@VisibleForTesting
var throwOnShowError = false

/**
 * Runs a suspend function that catches any uncaught errors and reports them to the user.
 * Errors from the backend contain localized text that is often suitable to show to the user as-is.
 * Other errors should ideally be handled in the block.
 */
fun CoroutineScope.launchCatching(
    context: CoroutineContext = EmptyCoroutineContext,
    errorMessageHandler: suspend (String) -> Unit,
    block: suspend CoroutineScope.() -> Unit,
): Job =
    launch(context) {
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

interface OnErrorListener {
    val onError: MutableSharedFlow<String>
}

fun <T, U> T.launchCatchingIO(block: suspend T.() -> U): Job where T : ViewModel, T : OnErrorListener =
    viewModelScope.launchCatching(
        ioDispatcher,
        { onError.emit(it) },
        { block() },
    )

fun <T> T.launchCatchingIO(
    errorMessageHandler: suspend (String) -> Unit,
    block: suspend CoroutineScope.() -> Unit,
): Job where T : ViewModel =
    viewModelScope.launchCatching(
        ioDispatcher,
        errorMessageHandler,
    ) { block() }

fun <T> CoroutineScope.asyncIO(block: suspend CoroutineScope.() -> T): Deferred<T> = async(ioDispatcher, block = block)

fun <T> ViewModel.asyncIO(block: suspend CoroutineScope.() -> T): Deferred<T> = viewModelScope.asyncIO(block)

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
suspend fun <T> FragmentActivity.runCatching(
    errorMessage: String? = null,
    skipCrashReport: ((Exception) -> Boolean)? = null,
    block: suspend () -> T?,
): T? {
    // appends the pre-coroutine stack to the error message. Example:
    // at com.ichi2.anki.CoroutineHelpersKt.launchCatchingTask(CoroutineHelpers.kt:188)
    // at com.ichi2.anki.CoroutineHelpersKt.launchCatchingTask$default(CoroutineHelpers.kt:184)
    // at com.ichi2.anki.BackendBackupsKt.performBackupInBackground(BackendBackups.kt:26)
    //  This is only performed in DEBUG mode to reduce performance impact
    val callerTrace =
        if (BuildConfig.DEBUG) {
            Thread
                .currentThread()
                .stackTrace
                .drop(14)
                .joinToString(prefix = "\tat ", separator = "\n\tat ")
        } else {
            null
        }

    try {
        return block()
    } catch (exc: Exception) {
        if (skipCrashReport?.invoke(exc) == true) {
            Timber.i("Showing error dialog but not sending a crash report.")
            showError(exc.localizedMessage!!, exc.toCrashReportData(this, reportException = false))
            return null
        }
        when (exc) {
            is CancellationException -> {
                throw exc // CancellationException should be re-thrown to propagate it to the parent coroutine
            }
            is BackendInterruptedException -> {
                Timber.w(exc, errorMessage)
                exc.localizedMessage?.let { showSnackbar(it) }
            }
            is BackendNetworkException, is BackendSyncException, is StorageAccessException -> {
                // these exceptions do not generate worthwhile crash reports
                Timber.i("Showing error dialog but not sending a crash report.")
                showError(exc.localizedMessage!!, exc.toCrashReportData(this, reportException = false))
            }
            is BackendException -> {
                Timber.e(exc, errorMessage)
                if (callerTrace != null) Timber.e(callerTrace)
                showError(exc.localizedMessage!!, exc.toCrashReportData(this))
            }
            else -> {
                Timber.e(exc, errorMessage)
                if (callerTrace != null) Timber.e(callerTrace)
                showError(exc)
            }
        }
    }
    return null
}

/**
 * Launch a job that catches any uncaught errors and reports them to the user.
 * Errors from the backend contain localized text that is often suitable to show to the user as-is.
 * Other errors should ideally be handled in the block.
 */
fun FragmentActivity.launchCatchingTask(
    errorMessage: String? = null,
    skipCrashReport: ((Exception) -> Boolean)? = null,
    block: suspend CoroutineScope.() -> Unit,
): Job =
    lifecycle.coroutineScope.launch {
        runCatching(errorMessage, skipCrashReport = skipCrashReport) { block() }
    }

/**
 * Launch a job that catches any uncaught errors and reports them to the user.
 * Errors from the backend contain localized text that is often suitable to show to the user as-is.
 * Other errors should ideally be handled in the block.
 */
fun <T> FragmentActivity.asyncCatching(
    errorMessage: String? = null,
    skipCrashReport: ((Exception) -> Boolean)? = null,
    block: suspend CoroutineScope.() -> T,
): Deferred<T?> =
    lifecycle.coroutineScope.async {
        runCatching(errorMessage, skipCrashReport = skipCrashReport) { block() }
    }

/** See [FragmentActivity.launchCatchingTask] */
fun Fragment.launchCatchingTask(
    errorMessage: String? = null,
    skipCrashReport: ((Exception) -> Boolean)? = null,
    block: suspend CoroutineScope.() -> Unit,
): Job =
    lifecycle.coroutineScope.launch {
        requireActivity().runCatching(errorMessage, skipCrashReport = skipCrashReport) { block() }
    }

/**
 * Displays an error dialog with title 'Error' and provided [message].
 * May report the error when the dialog is dismissed
 *
 * @param message Message to display to user
 * @param crashReportData Crash report data which may be reported when the dialog is dismissed.
 */
fun Context.showError(
    message: String,
    crashReportData: CrashReportData?,
) {
    crashReportData.throwIfDialogUnusable(message)

    Timber.i("Error dialog displayed")

    try {
        AlertDialog
            .Builder(this)
            .create {
                title(R.string.vague_error)
                message(text = message)
                positiveButton(R.string.dialog_ok)
                if (crashReportData?.helpAction != null) {
                    neutralButton(R.string.help)
                }
                if (crashReportData?.reportableException == true) {
                    Timber.w("sending crash report on close")
                    setOnDismissListener { crashReportData.sendCrashReport() }
                }
            }.apply {
                // setup the help link. Link is non-null if neutralButton exists.
                setOnShowListener {
                    neutralButton?.setOnClickListener {
                        lifecycle.coroutineScope.launch {
                            val shouldDismiss = crashReportData!!.helpAction!!.execute(context = context)
                            if (shouldDismiss) {
                                dismiss()
                            }
                        }
                    }
                }
                setupEnterKeyHandler()
                show()
            }
    } catch (ex: BadTokenException) {
        // issue 12718: activity provided by `context` was not running
        Timber.w(ex, "unable to display error dialog")
        crashReportData?.sendCrashReport()
    }
}

/**
 * @return Whether the dialog should be dismissed
 */
suspend fun HelpAction.execute(context: Context): Boolean {
    when (this) {
        is AnkiBackendLink -> {
            context.openUrl(this.link)
            return false
        }
        OpenDeckOptions -> {
            // if we're in the error dialog, we have no context of the deck which caused the exception
            // assume it's the current deck
            val openCurrentDeckOptions = DeckOptionsDestination.fromCurrentDeck()
            context.startActivity(openCurrentDeckOptions.toIntent(context))
            // dismiss the dialog - the user should have resolved the issue
            return true
        }
    }
}

/** In most cases, you'll want [AnkiActivity.withProgress]
 * instead. This lower-level routine can be used to integrate your own
 * progress UI.
 */
suspend fun <T> Backend.withProgress(
    extractProgress: ProgressContext.() -> Unit,
    updateUi: ProgressContext.() -> Unit,
    block: suspend CoroutineScope.() -> T,
): T =
    coroutineScope {
        val monitor =
            launch {
                monitorProgress(this@withProgress, extractProgress, updateUi)
            }
        try {
            block()
        } finally {
            monitor.cancel()
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
    op: suspend () -> T,
): T {
    val backend = CollectionManager.getBackend()
    return withProgressDialog(
        context = this@withProgress,
        onCancel =
            if (onCancel != null) {
                fun() {
                    onCancel(backend)
                }
            } else {
                null
            },
        manualCancelButton = manualCancelButton,
    ) { dialog ->
        backend.withProgress(
            extractProgress = extractProgress,
            updateUi = { updateDialog(dialog) },
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
    op: suspend () -> T,
): T =
    withProgressDialog(
        context = this@withProgress,
        onCancel = null,
    ) { dialog ->
        @Suppress("Deprecation") // ProgressDialog deprecation
        dialog.setMessage(message)
        op()
    }

/** @see withProgress(String, ...) */
suspend fun <T> Fragment.withProgress(
    message: String = getString(R.string.dialog_processing),
    block: suspend () -> T,
): T = requireActivity().withProgress(message, block)

/** @see withProgress(String, ...) */
suspend fun <T> Activity.withProgress(
    @StringRes messageId: Int,
    block: suspend () -> T,
): T = withProgress(resources.getString(messageId), block)

/** @see withProgress(String, ...) */
suspend fun <T> Fragment.withProgress(
    @StringRes messageId: Int,
    block: suspend () -> T,
): T = requireActivity().withProgress(messageId, block)

@Suppress("Deprecation") // ProgressDialog deprecation
suspend fun <T> withProgressDialog(
    context: Activity,
    onCancel: (() -> Unit)?,
    delayMillis: Long = 600,
    @StringRes manualCancelButton: Int? = null,
    op: suspend (android.app.ProgressDialog) -> T,
): T =
    coroutineScope {
        val dialog =
            android.app.ProgressDialog(context, R.style.AppCompatProgressDialogStyle).apply {
                setCancelable(onCancel != null)
                if (manualCancelButton != null) {
                    setCancelable(false)
                    setButton(DialogInterface.BUTTON_NEGATIVE, context.getString(manualCancelButton)) { _, _ ->
                        Timber.i("Progress dialog cancelled via cancel button")
                        onCancel?.let { it() }
                    }
                } else {
                    onCancel?.let {
                        setOnCancelListener {
                            Timber.i("Progress dialog cancelled via cancel listener")
                            it()
                        }
                    }
                }
            }
        // disable taps immediately
        context.window.setFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE, WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
        // reveal the dialog after 600ms
        var dialogIsOurs = false
        val dialogJob =
            launch {
                delay(delayMillis)
                if (!AnkiDroidApp.instance.progressDialogShown) {
                    Timber.i(
                        """Displaying progress dialog: ${delayMillis}ms elapsed; 
                |cancellable: ${onCancel != null}; 
                |manualCancel: ${manualCancelButton != null}
                |
                        """.trimMargin(),
                    )
                    dialog.show()
                    AnkiDroidApp.instance.progressDialogShown = true
                    dialogIsOurs = true
                } else {
                    Timber.w(
                        """A progress dialog is already displayed, not displaying progress dialog: 
                |cancellable: ${onCancel != null}; 
                |manualCancel: ${manualCancelButton != null}
                |
                        """.trimMargin(),
                    )
                }
            }
        try {
            op(dialog)
        } finally {
            dialogJob.cancel()
            dismissDialogIfShowing(dialog)
            context.window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
            if (dialogIsOurs) {
                AnkiDroidApp.instance.progressDialogShown = false
            }
        }
    }

private fun dismissDialogIfShowing(dialog: Dialog) {
    try {
        if (dialog.isShowing) {
            dialog.dismiss()
        }
    } catch (e: Exception) {
        Timber.w(e)
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
    val state = ProgressContext(Progress.getDefaultInstance())
    while (true) {
        state.progress =
            withContext(Dispatchers.IO) {
                backend.latestProgress()
            }
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
    val progressText =
        amount?.let {
            " ${it.first}/${it.second}"
        } ?: ""
    @Suppress("Deprecation") // ProgressDialog deprecation
    dialog.setMessage(text + progressText)
}

/**
 * If a one-way sync is not already required, confirm the user wishes to proceed.
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

/**
 * Returns whether we are allowed to change the schema.
 *
 * If changing the schema would require the next sync to be a full sync, and it's not already required, ask
 * the user whether or not they still allow the schema change.
 */
suspend fun AnkiActivity.userAcceptsSchemaChange(): Boolean {
    if (withCol { schemaChanged() }) {
        return true
    }
    val hasAcceptedSchemaChange =
        suspendCoroutine { coroutine ->
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

/**
 * Displays an error dialog with title 'Error' and [throwable] data.
 * May report the error when the dialog is dismissed
 *
 * @param throwable The exception to display to the user
 * @param reportException If `true`, report the exception when the dialog is dismissed
 */
private fun Activity.showError(
    throwable: Throwable,
    reportException: Boolean = true,
) = showError(throwable.toString(), throwable.toCrashReportData(context = this, reportException))

data class CrashReportData(
    val exception: Throwable,
    val origin: String,
    val reportableException: Boolean,
) {
    /**
     * Optional link to a help page regarding the error
     *
     * For example: https://docs.ankiweb.net/templates/errors.html#no-cloze-filter-on-cloze-notetype
     * Or opening the deck options
     */
    val helpAction: HelpAction?
        get() = HelpAction.from(exception)

    fun shouldReportException(): Boolean {
        if (!reportableException) return false
        if (exception.isInvalidFsrsParametersException()) return false
        return true
    }

    fun sendCrashReport() {
        if (!shouldReportException()) {
            Timber.i("skipped crash report due to further validation")
            return
        }
        CrashReportService.sendExceptionReport(exception, origin)
    }

    /**
     * Optional action to provide more information about an error
     *
     * Examples:
     * - Open https://docs.ankiweb.net/templates/errors.html#no-cloze-filter-on-cloze-notetype
     * - Open the deck options
     */
    sealed class HelpAction {
        data class AnkiBackendLink(
            val link: Uri,
        ) : HelpAction()

        /** Open the deck options for the current deck */
        data object OpenDeckOptions : HelpAction()

        companion object {
            fun from(e: Throwable): HelpAction? {
                val link =
                    try {
                        (e as? BackendException)
                            ?.getDesktopHelpPageLink(CollectionManager.getBackend())
                            ?.toUri()
                    } catch (e: Exception) {
                        Timber.w(e)
                        null
                    }

                if (link != null) return AnkiBackendLink(link)
                if (e.isInvalidFsrsParametersException()) return OpenDeckOptions

                return null
            }
        }
    }

    companion object {
        @UseContextParameter("context")
        fun Throwable.toCrashReportData(
            context: Context,
            reportException: Boolean = true,
        ) = CrashReportData(
            exception = this,
            // Appears as 'ManageNotetypes'
            origin = context::class.java.simpleName,
            reportableException = reportException,
        )

        /**
         * If [throwOnShowError] is set, throws the exception from the crash report data
         *
         * So unit tests can fail if an unexpected exception is thrown
         *
         * Note: this occurs regardless of the status of [reportableException]
         *
         * @param message The message of the thrown [IllegalStateException]
         * @throws IllegalStateException with [exception] as an innerException if the receiver
         *  is non-null
         */
        fun CrashReportData?.throwIfDialogUnusable(message: String) {
            if (!throwOnShowError) return
            val message = "throwOnShowError: $message"
            if (this == null) throw IllegalStateException(message)
            throw IllegalStateException(message, exception)
        }

        private fun Throwable.isInvalidFsrsParametersException(): Boolean =
            // `TR` may fail in an error-reporting context
            try {
                when (message) {
                    // https://github.com/ankitects/anki/blob/f3b4284afbb38b894164cd4de3e7b690f2bc62a5/rslib/src/scheduler/fsrs/error.rs#L18
                    "invalid params provided" -> true
                    TR.deckConfigInvalidParameters() -> true
                    else -> false
                }
            } catch (_: Throwable) {
                false
            }
    }
}
