package com.ichi2.anki.dialogs.customstudy;

import com.ichi2.anki.dialogs.customstudy.CustomStudyDialog.CustomStudyListener;
import com.ichi2.libanki.Collection;
import com.ichi2.utils.ExtendedFragmentFactory;
import com.ichi2.utils.FunctionalInterfaces.Supplier;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

public class CustomStudyDialogFactory extends ExtendedFragmentFactory {

    final Supplier<Collection> mCollectionSupplier;
    final CustomStudyListener mCustomStudyListener;

    public CustomStudyDialogFactory(Supplier<Collection> mCollectionSupplier, CustomStudyListener mCustomStudyListener) {
        this.mCollectionSupplier = mCollectionSupplier;
        this.mCustomStudyListener = mCustomStudyListener;
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
