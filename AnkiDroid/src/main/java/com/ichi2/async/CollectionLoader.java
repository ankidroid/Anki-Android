/****************************************************************************************
 * Copyright (c) 2015 Timothy Rae <perceptualchaos2@gmail.com>                          *
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

import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;

import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.CollectionHelper;
import com.ichi2.libanki.Collection;

import timber.log.Timber;


@SuppressWarnings("deprecation") // #7108: AsyncTask
public final class CollectionLoader extends android.os.AsyncTask<Void, Void, Collection> {
    private final LifecycleOwner mLifecycleOwner;
    private final Callback mCallback;

    public interface Callback {
        void execute(Collection col);
    }

    public static void load(LifecycleOwner lifecycleOwner, Callback callback) {
        CollectionLoader loader = new CollectionLoader(lifecycleOwner, callback);
        loader.execute();
    }

    private CollectionLoader(LifecycleOwner lifecycleOwner, Callback callback) {
        mLifecycleOwner = lifecycleOwner;
        mCallback = callback;
    }

    @Override
    protected Collection doInBackground(Void... params) {
        // Don't touch collection if lockCollection flag is set
        if (CollectionHelper.getInstance().isCollectionLocked()) {
            Timber.w("onStartLoading() :: Another thread has requested to keep the collection closed.");
            return null;
        }
        // load collection
        try {
            Timber.d("CollectionLoader accessing collection");
            Collection col = CollectionHelper.getInstance().getCol(AnkiDroidApp.getInstance().getApplicationContext());
            Timber.i("CollectionLoader obtained collection");
            return col;
        } catch (RuntimeException e) {
            Timber.e(e, "loadInBackground - RuntimeException on opening collection");
            AnkiDroidApp.sendExceptionReport(e, "CollectionLoader.loadInBackground");
            return null;
        }
    }

    @Override
    protected void onPostExecute(Collection col) {
        super.onPostExecute(col);
        if (mLifecycleOwner.getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.CREATED)) {
            mCallback.execute(col);
        }
    }

}