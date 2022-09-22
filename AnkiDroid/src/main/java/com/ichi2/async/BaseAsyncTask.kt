/****************************************************************************************
 * Copyright (c) 2013 Flavio Lerda <flerda@gmail.com>                                   *
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

package com.ichi2.async

import com.ichi2.anki.CrashReportService
import com.ichi2.utils.MethodLogger.log
import com.ichi2.utils.Threads

@Suppress("deprecation") // #7108: AsyncTask
open class BaseAsyncTask<Params, Progress, Result> : android.os.AsyncTask<Params, Progress, Result>(), ProgressSenderAndCancelListener<Progress> {
    override fun onPreExecute() {
        if (DEBUG) {
            log()
        }
        Threads.checkMainThread()
        super.onPreExecute()
    }

    override fun onPostExecute(result: Result) {
        if (DEBUG) {
            log()
        }
        if (isCancelled) {
            CrashReportService.sendExceptionReport("onPostExecute called with task cancelled. This should never occur !", "BaseAsyncTask - onPostExecute")
        }
        Threads.checkMainThread()
        super.onPostExecute(result)
    }

    override fun onProgressUpdate(vararg values: Progress) {
        if (DEBUG) {
            log()
        }
        Threads.checkMainThread()
        super.onProgressUpdate(*values)
    }

    override fun onCancelled() {
        if (DEBUG) {
            log()
        }
        Threads.checkMainThread()
        super.onCancelled()
    }

    override fun doInBackground(vararg arg0: Params): Result? {
        if (DEBUG) {
            log()
        }
        Threads.checkNotMainThread()
        return null
    }

    override fun doProgress(value: Progress?) {
        publishProgress(value)
    }

    companion object {
        /** Set this to `true` to enable detailed debugging for this class.  */
        private const val DEBUG = false
    }

    init {
        if (DEBUG) {
            log()
        }
        Threads.checkMainThread()
    }
}
