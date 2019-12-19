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

package com.ichi2.async;

import android.os.AsyncTask;

import com.ichi2.utils.MethodLogger;
import com.ichi2.utils.Threads;

public class BaseAsyncTask<Params, Progress, Result> extends AsyncTask<Params, Progress, Result> {

    /** Set this to {@code true} to enable detailed debugging for this class. */
    private static final boolean DEBUG = false;


    public BaseAsyncTask() {
        if (DEBUG) {
            MethodLogger.log();
        }
        Threads.checkMainThread();
    }


    @Override
    protected void onPreExecute() {
        if (DEBUG) {
            MethodLogger.log();
        }
        Threads.checkMainThread();
        super.onPreExecute();
    }


    @Override
    protected void onPostExecute(Result result) {
        if (DEBUG) {
            MethodLogger.log();
        }
        Threads.checkMainThread();
        super.onPostExecute(result);
    }


    @Override
    protected void onProgressUpdate(Progress... values) {
        if (DEBUG) {
            MethodLogger.log();
        }
        Threads.checkMainThread();
        super.onProgressUpdate(values);
    }


    @Override
    protected void onCancelled() {
        if (DEBUG) {
            MethodLogger.log();
        }
        Threads.checkMainThread();
        super.onCancelled();
    }


    @Override
    protected Result doInBackground(Params... arg0) {
        if (DEBUG) {
            MethodLogger.log();
        }
        Threads.checkNotMainThread();
        return null;
    }

}
