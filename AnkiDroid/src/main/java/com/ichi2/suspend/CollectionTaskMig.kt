/****************************************************************************************
 * Copyright (c) 2022 Saurav Rao <sauravrao637@gmail.com>                               *
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

package com.ichi2.suspend

import android.util.Pair
import com.ichi2.libanki.Collection
import com.ichi2.libanki.Model
import com.ichi2.utils.JSONObject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.*

/*
 * This is responsible for handling asynchronous tasks related to notes, Decks and Collections
 * This file is part of migration for asyncTask to coroutines for CollectionTask.java
 * After successful migration this will be renamed to CollectionTask
 */
object CollectionTaskMig {
    // TODO add tests for this
    suspend fun countModels(col: Collection, dispatcher: CoroutineDispatcher = Dispatchers.IO): Pair<List<Model>, ArrayList<Int>>? {
        return withContext(dispatcher) {
            Timber.d("counting models in background")
            val models = col.models.all()
            val cardCount = ArrayList<Int>()
            Collections.sort(models, Comparator { a: JSONObject, b: JSONObject -> a.getString("name").compareTo(b.getString("name")) } as Comparator<JSONObject>)
            for (n in models) {
                if (!isActive) {
                    Timber.e("countModels :: Cancelled")
                    return@withContext null
                }
                cardCount.add(col.models.useCount(n))
            }
            Pair(models, cardCount)
        }
    }
}
