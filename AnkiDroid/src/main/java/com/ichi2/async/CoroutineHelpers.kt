/****************************************************************************************
 * Copyright (c) 2022 Divyansh Kushwaha <kushwaha.divyansh.dxn@gmail.com>               *
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

/*
* This file contains extension functions for different coroutine related actions.
*/
package com.ichi2.async

import android.app.Activity
import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.coroutineScope
import com.ichi2.anki.CrashReportService
import com.ichi2.anki.snackbar.showSnackbar
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Launch a job that catches any uncaught errors, informs the user and prints it to Log.
 * Errors from the backend contain localized text that is often suitable to show to the user as-is.
 * Other errors should ideally be handled in the block.
 */
fun LifecycleOwner.catchingLifecycleScope(
    activity: Activity,
    errorMessage: String? = null,
    block: suspend CoroutineScope.() -> Unit
): Job = lifecycle.coroutineScope.launch {
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // TODO: localize
        Timber.w(e, errorMessage)
        activity.showSnackbar("An error occurred: $e")
        CrashReportService.sendExceptionReport(e, activity::class.java.simpleName)
    }
}

/**
 * @see [LifecycleOwner.catchingLifecycleScope]
 */
fun Fragment.catchingLifecycleScope(
    errorMessage: String? = null,
    block: suspend CoroutineScope.() -> Unit
) = (this as LifecycleOwner).catchingLifecycleScope(requireActivity(), errorMessage, block)

fun Activity.catchingLifecycleScope(
    errorMessage: String? = null,
    block: suspend CoroutineScope.() -> Unit
) = (this as LifecycleOwner).catchingLifecycleScope(this, errorMessage, block)
