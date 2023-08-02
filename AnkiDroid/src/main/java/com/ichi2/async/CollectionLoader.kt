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
import androidx.lifecycle.lifecycleScope
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.CollectionHelper
import com.ichi2.anki.CrashReportService
import com.ichi2.libanki.Collection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

object CollectionLoader {
    fun interface Callback {
        fun execute(col: Collection?)
    }

    fun load(lifecycleOwner: LifecycleOwner, callback: Callback) {
        lifecycleOwner.lifecycleScope.launch {
            val col = withContext(Dispatchers.IO) {
                // load collection
                try {
                    Timber.d("CollectionLoader accessing collection")
                    val col = CollectionHelper.instance.getCol(AnkiDroidApp.instance.applicationContext)
                    Timber.i("CollectionLoader obtained collection")
                    col
                } catch (e: RuntimeException) {
                    Timber.e(e, "loadInBackground - RuntimeException on opening collection")
                    CrashReportService.sendExceptionReport(e, "CollectionLoader.load")
                    null
                }
            }
            if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.CREATED)) {
                callback.execute(col)
            }
        }
    }
}
