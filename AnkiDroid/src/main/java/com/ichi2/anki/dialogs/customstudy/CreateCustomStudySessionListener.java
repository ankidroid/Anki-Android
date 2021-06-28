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
package com.ichi2.anki.dialogs.customstudy;

import com.ichi2.anki.StudyOptionsFragment;
import com.ichi2.anki.dialogs.customstudy.CustomStudyDialog.CustomStudyListener;
import com.ichi2.async.TaskListenerWithContext;

import androidx.annotation.NonNull;

import static com.ichi2.anki.dialogs.customstudy.CreateCustomStudySessionListener.*;

class CreateCustomStudySessionListener extends TaskListenerWithContext<Callback, Void, StudyOptionsFragment.DeckStudyData> {

    public interface Callback {
        void hideProgressBar();
        void onCreateCustomStudySession();
        void showProgressBar();
    }

    public CreateCustomStudySessionListener(Callback callback) {
        super(callback);
    }


    @Override
    public void actualOnPreExecute(@NonNull Callback callback) {
        callback.showProgressBar();
    }


    @Override
    public void actualOnPostExecute(@NonNull Callback callback, StudyOptionsFragment.DeckStudyData v) {
        callback.hideProgressBar();
        callback.onCreateCustomStudySession();
    }
}
