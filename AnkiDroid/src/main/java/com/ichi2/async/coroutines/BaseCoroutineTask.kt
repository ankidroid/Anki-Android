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

import com.ichi2.anki.CrashReportService
import com.ichi2.utils.MethodLogger

/** This class replicates the behaviour of BaseAsyncTask, once completely migrated, BaseAsyncTask can be removed */

// It would be good to remove this class completely and migrated the codebase to more coroutine-based-style
open class BaseCoroutineTask<Params, Progress, Result>(taskName: String) :
    CoroutineTask<Params, Progress, Result>(taskName) {
    override suspend fun onPreExecute() {
        if (DEBUG) {
            MethodLogger.log()
        }
        super.onPreExecute()
    }

    override suspend fun onPostExecute(result: Result?) {
        if (DEBUG) {
            MethodLogger.log()
        }
        if (isCancelled) {
            CrashReportService.sendExceptionReport(
                "onPostExecute called with task cancelled. This should never occur !",
                "BaseCoroutineTask - onPostExecute"
            )
        }
        super.onPostExecute(result)
    }

    override suspend fun onProgressUpdate(values: Array<Progress?>) {
        if (DEBUG) {
            MethodLogger.log()
        }
        super.onProgressUpdate(values)
    }

    override fun onCancelled() {
        if (DEBUG) {
            MethodLogger.log()
        }
        super.onCancelled()
    }

    override suspend fun doInBackground(params: Array<Params>): Result? {
        if (DEBUG) {
            MethodLogger.log()
        }
        return null
    }

    companion object {
        /** Set this to `true` to enable detailed debugging for this class.  */
        private const val DEBUG = false
    }

    init {
        if (DEBUG) {
            MethodLogger.log()
        }
    }
}
