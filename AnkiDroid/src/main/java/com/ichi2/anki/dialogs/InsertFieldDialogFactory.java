/****************************************************************************************
 * Copyright (c) 2021 Akshay Jadhav <jadhavakshay0701@gmail.com>                        *
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

package com.ichi2.anki.dialogs;
import com.ichi2.utils.ExtendedFragmentFactory;
import com.ichi2.anki.dialogs.InsertFieldDialog.InsertFieldListener;
import java.io.Serializable;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

public class InsertFieldDialogFactory extends ExtendedFragmentFactory implements Serializable {

    final InsertFieldListener mInsertFieldListener;


    public InsertFieldDialogFactory(InsertFieldListener insertFieldListener) {
        this.mInsertFieldListener = insertFieldListener;
    }


    @NonNull
    @Override
    public Fragment instantiate(@NonNull ClassLoader classLoader, @NonNull String className) {
        Class<? extends Fragment> cls = loadFragmentClass(classLoader, className);
        if (cls == InsertFieldDialog.class) {
            return newInsertFieldDialog();
        }
        return super.instantiate(classLoader, className);
    }

    @NonNull
    public InsertFieldDialog newInsertFieldDialog() {
        return new InsertFieldDialog(mInsertFieldListener);
    }
}
