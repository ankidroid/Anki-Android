// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2023 Brayan Oliveira <69634269+brayandso@users.noreply.github.com>

package com.ichi2.anki

import com.ichi2.anki.libanki.exception.InvalidSearchException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.ankiweb.rsdroid.BackendException
import timber.log.Timber
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * Runs a suspend function that catches any uncaught errors and reports them to the user.
 * Errors from the backend contain localized text that is often suitable to show to the user as-is.
 * Other errors should ideally be handled in the block.
 *
 * @param context Coroutine context passed to [launch]
 * @param errorMessageHandler Called after an exception is caught and logged, input is either
 * `Exception.localizedMessage` or `Exception.toString()`
 * @param block code to execute inside [launch]
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
        } catch (exception: Exception) {
            Timber.w(exception)
            val message =
                when (exception) {
                    is BackendException, is InvalidSearchException -> exception.localizedMessage
                    else -> null
                } ?: exception.toString()
            errorMessageHandler.invoke(message)
        }
    }
