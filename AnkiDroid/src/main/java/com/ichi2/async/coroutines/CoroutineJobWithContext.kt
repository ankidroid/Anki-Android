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

import android.content.Context
import java.lang.ref.WeakReference

abstract class CoroutineJobWithContext<CTX : Context, Params, Progress, Result>(
    taskName: String,
    context: CTX?
) : CoroutineJob<Params, Progress, Result>(taskName) {

    private val mContext = WeakReference(context)

    abstract suspend fun actualOnProgressUpdate(context: CTX, vararg progress: Progress?)
    abstract suspend fun actualOnPostExecute(result: Result?, context: CTX)
    abstract suspend fun actualOnPreExecute(context: CTX)

    override suspend fun onProgressUpdate(vararg progress: Progress?) {
        mContext.get()?.let { actualOnProgressUpdate(it, *progress) }
    }

    override suspend fun onPostExecute(result: Result?) {
        mContext.get()?.let { actualOnPostExecute(result, it) }
    }

    override suspend fun onPreExecute() {
        mContext.get()?.let { actualOnPreExecute(it) }
    }
}
