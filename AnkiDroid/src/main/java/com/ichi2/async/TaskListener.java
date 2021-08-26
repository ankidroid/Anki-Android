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

package com.ichi2.async;

/**
 * Listener for the status and result of a {@link CollectionTask}.
 * <p>
 * Its methods are guaranteed to be invoked on the main thread.
 * <p>
 * Their semantics is equivalent to the methods of {@link android.os.AsyncTask}.
 */
public abstract class TaskListener<Progress, Result> {

    /** Invoked before the task is started. */
    public abstract void onPreExecute();


    /**
     * Invoked after the task has completed.
     * <p>
     * The semantics of the result depends on the task itself.
     */
    public abstract void onPostExecute(Result result);


    /**
     * Invoked when the background task publishes an update.
     * <p>
     * The semantics of the update data depends on the task itself.
     */
    public void onProgressUpdate(Progress value) {
        // most implementations do nothing with this, provide them a default implementation
    }

    public void onCancelled() {
        // most implementations do nothing with this, provide them a default implementation
    }
}
