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

package com.ichi2.async.coroutines

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/** This class is equivalent to AsyncTask, it keeps the functionality while using Kotlin-Coroutines beneath */

// It would be good to remove this class completely and migrated the codebase to more coroutine-based-style
abstract class CoroutineJob<Params, Progress, Result>(private val taskName: String) {

    companion object {
        enum class Status {
            PENDING, RUNNING, FINISHED
        }
    }

    var status: Status = Status.PENDING
    private var preJob: Job? = null
    private var bgJob: Deferred<Result>? = null
    abstract suspend fun doInBackground(params: Array<Params>): Result
    open suspend fun onProgressUpdate(progress: Progress) {}
    open suspend fun onPostExecute(result: Result) {}
    open suspend fun onPreExecute() {}
    open fun onCancelled() {}
    protected var isCancelled = false

    /**
     * Executes background task parallel with other background tasks in the queue using
     * default thread pool,
     */
    suspend fun execute(
        params: Array<Params>,
        bgDispatcher: CoroutineDispatcher = Dispatchers.Default,
        mainDispatcher: CoroutineDispatcher = Dispatchers.Main,
    ) {
        if (status != Status.PENDING) {
            when (status) {
                Status.RUNNING -> throw IllegalStateException("Cannot execute task:" + " the task is already running.")
                Status.FINISHED -> throw IllegalStateException(
                    "Cannot execute task:" +
                        " the task has already been executed " +
                        "(a task can be executed only once)"
                )
                else -> {
                }
            }
        }

        status = Status.RUNNING
        // it can be used to setup UI - it should have access to Main Thread
        // backgroundJob and preJob executes concurrently
        coroutineScope {
            preJob = launch(mainDispatcher) {
                Timber.d("$taskName onPreExecute started")
                onPreExecute()
                Timber.d("$taskName onPreExecute finished")
                bgJob = async(bgDispatcher) {
                    Timber.d("$taskName doInBackground started")
                    doInBackground(params)
                }
            }
            preJob!!.join()
            if (!isCancelled) {
                withContext(mainDispatcher) {
                    onPostExecute(bgJob!!.await())
                    Timber.d("$taskName doInBackground finished")
                    status = Status.FINISHED
                }
            }
        }
    }

    fun cancel(mayInterruptIfRunning: Boolean): Boolean {
        if (preJob == null || bgJob == null) {
            Timber.d("$taskName has already been cancelled/finished/not yet started.")
            return false
        }
        if (mayInterruptIfRunning || (!preJob!!.isActive && !bgJob!!.isActive)) {
            isCancelled = true
            status = Status.FINISHED
            preJob?.cancel(CancellationException("PreExecute: Coroutine Task cancelled"))
            bgJob?.cancel(CancellationException("doInBackground: Coroutine Task cancelled"))
            onCancelled()
            Timber.d("$taskName has been cancelled.")
            return true
        }
        return false
    }
}
