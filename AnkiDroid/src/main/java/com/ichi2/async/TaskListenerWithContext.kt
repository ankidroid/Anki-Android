/****************************************************************************************
 * Copyright (c) 2020 Arthur Milchior <arthur@milchior.fr>                              *
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

import java.lang.ref.WeakReference

/** Similar to task listener, but if the context disappear, no action are executed.
 * We ensure that the context can't disappear during the execution of the methods. */
abstract class TaskListenerWithContext<CTX, Progress, Result> protected constructor(context: CTX) :
    TaskListener<Progress, Result>() where CTX : Any {
    private val mContext: WeakReference<CTX>
    override fun onPreExecute() {
        val context = mContext.get()
        context?.let { actualOnPreExecute(it) }
    }

    override fun onProgressUpdate(value: Progress) {
        val context = mContext.get()
        context?.let { actualOnProgressUpdate(it, value) }
    }

    /**
     * Invoked when the background task publishes an update.
     * <p>
     * The semantics of the update data depends on the task itself.
     * Assumes context exists.
     */
    open fun actualOnProgressUpdate(context: CTX, value: Progress) {
        // most implementations do nothing with this, provide them a default implementation
    }

    /** Invoked before the task is started. Assumes context exists. */
    abstract fun actualOnPreExecute(context: CTX)
    override fun onPostExecute(result: Result) {
        val context = mContext.get()
        context?.let { actualOnPostExecute(it, result) }
    }

    /**
     * Invoked after the task has completed.
     * <p>
     * The semantics of the result depends on the task itself.
     */
    abstract fun actualOnPostExecute(context: CTX, result: Result)
    override fun onCancelled() {
        val context = mContext.get()
        context?.let { actualOnCancelled(it) }
    }

    /** Assumes context exists. */
    open fun actualOnCancelled(context: CTX) {
        // most implementations do nothing with this, provide them a default implementation
    }

    init {
        mContext = WeakReference(context)
    }
}
