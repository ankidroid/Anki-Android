/****************************************************************************************
 * Copyright (c) 2022 Mohd Raghib <raghib.khan76@gmail.com>                             *
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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Complete class not yet implemented*/
abstract class BaseCoroutinesTask<Params, Progress, Result> {

    open fun onPreExecute() {}

    abstract fun onPostExecute(result: Result?)

    open fun onProgressUpdate(vararg values: Progress) {}

    open fun onCancelled(result: Boolean) {}

    abstract fun doInBackground(vararg params: Params?): Result?

    open fun doProgress(value: Progress?) {}

//    protected fun publishProgress(vararg values: Progress?) {}

    fun execute(vararg params: Params?) {
//            onPreExecute()
        CoroutineScope(Dispatchers.Default).launch {
            val result = doInBackground(*params)
            withContext(Dispatchers.Main) {
                onPostExecute(result)
            }
        }
    }
}
//
//    fun cancel(mayInterruptIfRunning: Boolean) {
//    }
