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

import com.ichi2.anki.dialogs.customstudy.CustomStudyDialog.CustomStudyListener;
import com.ichi2.libanki.Collection;
import com.ichi2.utils.ExtendedFragmentFactory;

import java.util.function.Supplier;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

public class CustomStudyDialogFactory extends ExtendedFragmentFactory {

    final Supplier<Collection> mCollectionSupplier;
    final CustomStudyListener mCustomStudyListener;

    public CustomStudyDialogFactory(Supplier<Collection> collectionSupplier, CustomStudyListener customStudyListener) {
        this.mCollectionSupplier = collectionSupplier;
        this.mCustomStudyListener = customStudyListener;
    }

    @NonNull
    @Override
    public Fragment instantiate(@NonNull ClassLoader classLoader, @NonNull String className) {
        Class<? extends Fragment> cls = loadFragmentClass(classLoader, className);
        if (cls == CustomStudyDialog.class) {
            return newCustomStudyDialog();
        }
        return super.instantiate(classLoader, className);
    }

    @NonNull
    public CustomStudyDialog newCustomStudyDialog() {
        return new CustomStudyDialog(mCollectionSupplier.get(), mCustomStudyListener);
    }
}
