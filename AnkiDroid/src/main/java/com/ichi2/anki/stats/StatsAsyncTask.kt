/*
 *  Copyright (c) 2021 David Allison <davidallisongithub@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.stats;

import android.view.View;

import timber.log.Timber;

/**
 * Async task which handles cancellation (#6192)
 */
@SuppressWarnings("deprecation") // #7108: AsyncTask
public abstract class StatsAsyncTask<TResult> extends android.os.AsyncTask<View, Void, TResult> {

    @Override
    protected final TResult doInBackground(View... views) {
        try {
            return doInBackgroundSafe(views);
        } catch (Exception e) {
            if (this.isCancelled()) {
                Timber.w(e, "ignored exception in cancelled stats task");
                return null;
            }
            throw e;
        }
    }

    protected abstract TResult doInBackgroundSafe(View... views);
}
