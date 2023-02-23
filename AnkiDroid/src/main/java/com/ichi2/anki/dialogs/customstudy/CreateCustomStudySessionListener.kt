/*
 Copyright (c) 2021 Tarek Mohamed Abdalla <tarekkma@gmail.com>

 This program is free software; you can redistribute it and/or modify it under
 the terms of the GNU General Public License as published by the Free Software
 Foundation; either version 3 of the License, or (at your option) any later
 version.

 This program is distributed in the hope that it will be useful, but WITHOUT ANY
 WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 PARTICULAR PURPOSE. See the GNU General Public License for more details.

 You should have received a copy of the GNU General Public License along with
 this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.dialogs.customstudy

import com.ichi2.anki.CollectionManager
import com.ichi2.async.updateValuesFromDeck
import timber.log.Timber

class CreateCustomStudySessionListener(val callback: Callback) {
    interface Callback {
        fun hideProgressBar()
        fun onCreateCustomStudySession()
        fun showProgressBar()
    }

    fun onPreExecute() {
        callback.showProgressBar()
    }

    fun onPostExecute() {
        callback.hideProgressBar()
        callback.onCreateCustomStudySession()
    }
}

// TODO: See if listener can be simplified more
suspend fun rebuildCram(listener: CreateCustomStudySessionListener) {
    listener.onPreExecute()
    CollectionManager.withCol {
        Timber.d("doInBackground - rebuildCram()")
        sched.rebuildDyn(decks.selected())
        updateValuesFromDeck(this, true)
    }
    listener.onPostExecute()
}
