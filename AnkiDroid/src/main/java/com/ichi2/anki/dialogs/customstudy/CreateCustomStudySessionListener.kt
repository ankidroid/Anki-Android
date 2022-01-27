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

import com.ichi2.anki.StudyOptionsFragment.DeckStudyData
import com.ichi2.async.TaskListenerWithContext

class CreateCustomStudySessionListener(callback: Callback?) : TaskListenerWithContext<CreateCustomStudySessionListener.Callback?, Void?, DeckStudyData?>(callback) {
    interface Callback {
        fun hideProgressBar()
        fun onCreateCustomStudySession()
        fun showProgressBar()
    }

    override fun actualOnPreExecute(callback: Callback) {
        callback.showProgressBar()
    }

    override fun actualOnPostExecute(callback: Callback, result: DeckStudyData?) {
        callback.hideProgressBar()
        callback.onCreateCustomStudySession()
    }
}
