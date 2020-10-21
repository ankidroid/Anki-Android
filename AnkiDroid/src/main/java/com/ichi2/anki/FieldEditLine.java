/*
 *  Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki;

import android.content.Context;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.util.SparseArray;
import android.view.AbsSavedState;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.TextView;

import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.view.ViewCompat;

public class FieldEditLine extends FrameLayout {
    private FieldEditText mEditText;
    private TextView mLabel;
    private ImageButton mMediaButton;

    private String mName;


    public FieldEditLine(@NonNull Context context) {
        super(context);
        init();
    }


    public FieldEditLine(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }


    public FieldEditLine(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }


    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public FieldEditLine(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }


    private void init() {
        LayoutInflater.from(getContext()).inflate(R.layout.card_multimedia_editline, this, true);
        this.mEditText = findViewById(R.id.id_note_editText);
        this.mLabel = findViewById(R.id.id_label);
        this.mMediaButton = findViewById(R.id.id_media_button);
        // 7433 -
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            mEditText.setId(ViewCompat.generateViewId());
            mMediaButton.setId(ViewCompat.generateViewId());
            mEditText.setNextFocusForwardId(mMediaButton.getId());
        }

        mEditText.init();
        mLabel.setPadding((int) UIUtils.getDensityAdjustedValue(getContext(), 3.4f), 0, 0, 0);
    }




    public void setActionModeCallbacks(ActionMode.Callback callback) {
        mEditText.setCustomSelectionActionModeCallback(callback);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            mEditText.setCustomInsertionActionModeCallback(callback);
        }
    }


    public void setTypeface(@Nullable Typeface typeface) {
        if (typeface != null) {
            mEditText.setTypeface(typeface);
        }
    }


    public void setName(String name) {
        mName = name;
        mEditText.setContentDescription(name);
        mLabel.setText(name);
    }

    public void setHintLocale(@Nullable Locale hintLocale) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && hintLocale != null) {
            mEditText.setHintLocale(hintLocale);
        }
    }

    public void setContent(String content) {
        mEditText.setContent(content);
    }


    public void setOrd(int i) {
        mEditText.setOrd(i);
    }

    public String getName() {
        return mName;
    }


    public ImageButton getMediaButton() {
        return mMediaButton;
    }

    public FieldEditText getEditText() {
        return mEditText;
    }

    public void loadState(AbsSavedState state) {
        this.onRestoreInstanceState(state);
    }


    @Override
    protected void dispatchSaveInstanceState(SparseArray<Parcelable> container) {
        dispatchFreezeSelfOnly(container);
    }


    @Override
    protected void dispatchRestoreInstanceState(SparseArray<Parcelable> container) {
        dispatchThawSelfOnly(container);
    }


    @Nullable
    @Override
    protected Parcelable onSaveInstanceState() {
        Parcelable state = super.onSaveInstanceState();

        SavedState savedState = new SavedState(state);
        savedState.mChildrenStates = new SparseArray<>();
        savedState.mEditTextId = getEditText().getId();
        savedState.mMediaButtonId = getMediaButton().getId();

        for (int i = 0; i < getChildCount(); i++) {
            getChildAt(i).saveHierarchyState(savedState.mChildrenStates);
        }

        return savedState;
    }


    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        if (!(state instanceof SavedState)) {
            super.onRestoreInstanceState(state);
            return;
        }

        SavedState ss = (SavedState) state;

        int editTextId = mEditText.getId();
        int mediaButtonId = mMediaButton.getId();

        mEditText.setId(ss.mEditTextId);
        mMediaButton.setId(ss.mMediaButtonId);


        super.onRestoreInstanceState(ss.getSuperState());
        for (int i = 0; i < getChildCount(); i++) {
            getChildAt(i).restoreHierarchyState(ss.mChildrenStates);
        }

        mEditText.setId(editTextId);
        mMediaButton.setId(mediaButtonId);
    }


    static class SavedState extends BaseSavedState {
        private SparseArray<Parcelable> mChildrenStates;
        private int mEditTextId;
        private int mMediaButtonId;

        SavedState(Parcelable superState) {
            super(superState);
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            super.writeToParcel(out, flags);
            out.writeSparseArray(mChildrenStates);
            out.writeInt(mEditTextId);
            out.writeInt(mMediaButtonId);
        }

        //required field that makes Parcelables from a Parcel
        public static final Parcelable.Creator<SavedState> CREATOR =
            new ClassLoaderCreator<SavedState>() {
                @Override
                public SavedState createFromParcel(Parcel in, ClassLoader loader) {
                    return new SavedState(in, loader);
                }


                @Override
                public SavedState createFromParcel(Parcel source) {
                    throw new IllegalStateException();
                }


                public SavedState[] newArray(int size) {
                    return new SavedState[size];
                }
            };

        private SavedState(Parcel in, ClassLoader loader) {
            super(in);
            this.mChildrenStates = in.readSparseArray(loader);
            this.mEditTextId = in.readInt();
            this.mMediaButtonId = in.readInt();
        }
    }
}
