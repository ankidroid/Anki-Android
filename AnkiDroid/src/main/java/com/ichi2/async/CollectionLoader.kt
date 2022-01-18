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

package com.ichi2.async

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.CollectionHelper
import com.ichi2.libanki.Collection
import timber.log.Timber

// #7108: AsyncTask
@Suppress("Deprecation")
class CollectionLoader private constructor(private val lifecycleOwner: LifecycleOwner, private val callback: Callback) : android.os.AsyncTask<Void?, Void?, Collection?>() {
    interface Callback {
        fun execute(col: Collection?)
    }

    override fun doInBackground(vararg params: Void?): Collection? {
        // Don't touch collection if lockCollection flag is set
        if (CollectionHelper.getInstance().isCollectionLocked) {
            Timber.w("onStartLoading() :: Another thread has requested to keep the collection closed.")
            return null
        }
        // load collection
        return try {
            Timber.d("CollectionLoader accessing collection")
            val col = CollectionHelper.getInstance().getCol(AnkiDroidApp.getInstance().applicationContext)
            Timber.i("CollectionLoader obtained collection")
            col
        } catch (e: RuntimeException) {
            Timber.e(e, "loadInBackground - RuntimeException on opening collection")
            AnkiDroidApp.sendExceptionReport(e, "CollectionLoader.loadInBackground")
            null
        }
    }

    override fun onPostExecute(col: Collection?) {
        @Suppress("Deprecation")
        super.onPostExecute(col)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.CREATED)) {
            callback.execute(col)
        }
    }

    companion object {
        @JvmStatic
        fun load(lifecycleOwner: LifecycleOwner, callback: Callback) {
            val loader = CollectionLoader(lifecycleOwner, callback)
            @Suppress("Deprecation")
            loader.execute()
        }
    }
}
