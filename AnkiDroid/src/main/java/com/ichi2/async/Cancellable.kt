// noinspection MissingCopyrightHeader
/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.ichi2.async

interface Cancellable {
    /**
     * <p>Attempts to cancel execution of this task.  This attempt will
     * fail if the task has already completed, already been cancelled,
     * or could not be cancelled for some other reason. If successful,
     * and this task has not started when <tt>cancel</tt> is called,
     * this task should never run. If the task has already started,
     * then the <tt>mayInterruptIfRunning</tt> parameter determines
     * whether the thread executing this task should be interrupted in
     * an attempt to stop the task.</p>
     *
     * <p>Calling this method will result in {@link android.os.AsyncTask#onCancelled(Object)} being
     * invoked on the UI thread after {@link android.os.AsyncTask#doInBackground(Object[])} returns.
     * Calling this method guarantees that onPostExecute(Object) is never
     * subsequently invoked, even if <tt>cancel</tt> returns false, but
     * {@link android.os.AsyncTask#onPostExecute} has not yet run.  To finish the
     * task as early as possible, check {@link android.os.AsyncTask#isCancelled()} periodically from
     * {@link android.os.AsyncTask#doInBackground(Object[])}.</p>
     *
     * <p>This only requests cancellation. It never waits for a running
     * background task to terminate, even if <tt>mayInterruptIfRunning</tt> is
     * true.</p>
     *
     * @param mayInterruptIfRunning <tt>true</tt> if the thread executing this
     *        task should be interrupted; otherwise, in-progress tasks are allowed
     *        to complete.
     *
     * @return <tt>false</tt> if the task could not be cancelled,
     *         typically because it has already completed normally;
     *         <tt>true</tt> otherwise
     *
     * @see android.os.AsyncTask#isCancelled()
     * @see android.os.AsyncTask#onCancelled(Object)
     */
    fun cancel(mayInterruptIfRunning: Boolean): Boolean

    /** Cancel the current task.
     * @return whether cancelling did occur.
     */
    fun safeCancel(): Boolean
}
