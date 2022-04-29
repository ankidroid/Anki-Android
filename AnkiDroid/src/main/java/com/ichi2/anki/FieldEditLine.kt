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
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.util.SparseArray;
import android.view.AbsSavedState;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.TextView;

import com.ichi2.ui.AnimationUtil;

import java.util.Locale;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.core.view.ViewCompat;
import androidx.vectordrawable.graphics.drawable.VectorDrawableCompat;

public class FieldEditLine extends FrameLayout {
    private FieldEditText mEditText;
    private TextView mLabel;
    private ImageButton mToggleSticky;
    private ImageButton mMediaButton;
    private ImageButton mExpandButton;

    private String mName;
    private ExpansionState mExpansionState;

    private boolean mEnableAnimation = true;


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


    public FieldEditLine(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }


    private void init() {
        LayoutInflater.from(getContext()).inflate(R.layout.card_multimedia_editline, this, true);
        this.mEditText = findViewById(R.id.id_note_editText);
        this.mLabel = findViewById(R.id.id_label);
        this.mToggleSticky = findViewById(R.id.id_toggle_sticky_button);
        this.mMediaButton = findViewById(R.id.id_media_button);
        ConstraintLayout constraintLayout = findViewById(R.id.constraint_layout);
        this.mExpandButton = findViewById(R.id.id_expand_button);
        // 7433 -
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            mEditText.setId(ViewCompat.generateViewId());
            mToggleSticky.setId(ViewCompat.generateViewId());
            mMediaButton.setId(ViewCompat.generateViewId());
            mExpandButton.setId(ViewCompat.generateViewId());
            mEditText.setNextFocusForwardId(mToggleSticky.getId());
            mToggleSticky.setNextFocusForwardId(mMediaButton.getId());
            mMediaButton.setNextFocusForwardId(mExpandButton.getId());
            ConstraintSet constraintSet = new ConstraintSet();
            constraintSet.clone(constraintLayout);
            constraintSet.connect(mToggleSticky.getId(), ConstraintSet.END, mMediaButton.getId(), ConstraintSet.START);
            constraintSet.connect(mMediaButton.getId(), ConstraintSet.END, mExpandButton.getId(), ConstraintSet.START);
            constraintSet.applyTo(constraintLayout);
        }

        this.mExpansionState = ExpansionState.EXPANDED;

        setExpanderBackgroundImage();
        mExpandButton.setOnClickListener((v) -> toggleExpansionState());
        mEditText.init();
        mLabel.setPadding((int) UIUtils.getDensityAdjustedValue(getContext(), 3.4f), 0, 0, 0);
    }


    private void toggleExpansionState() {
        switch (mExpansionState) {
            case EXPANDED: {
                AnimationUtil.collapseView(mEditText, mEnableAnimation);
                mExpansionState = ExpansionState.COLLAPSED;
                break;
            }
            case COLLAPSED: {
                AnimationUtil.expandView(mEditText, mEnableAnimation);
                mExpansionState = ExpansionState.EXPANDED;
                break;
            }
            default:
        }
        setExpanderBackgroundImage();
    }


    private void setExpanderBackgroundImage() {
        switch (mExpansionState) {
            case COLLAPSED:
                mExpandButton.setBackground(getBackgroundImage(R.drawable.ic_expand_more_black_24dp_xml));
                break;
            case EXPANDED:
                mExpandButton.setBackground(getBackgroundImage(R.drawable.ic_expand_less_black_24dp));
                break;
        }
    }


    private Drawable getBackgroundImage(@DrawableRes int idRes) {
        return VectorDrawableCompat.create(this.getResources(), idRes, getContext().getTheme());
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

    public void setContent(String content, boolean replaceNewline) {
        mEditText.setContent(content, replaceNewline);
    }


    public void setOrd(int i) {
        mEditText.setOrd(i);
    }

    public void setEnableAnimation(boolean value) {
        this.mEnableAnimation = value;
    }

    public String getName() {
        return mName;
    }


    public ImageButton getMediaButton() {
        return mMediaButton;
    }

    public ImageButton getToggleSticky() {
        return mToggleSticky;
    }

    public View getLastViewInTabOrder() {
        return mExpandButton;
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
        savedState.mToggleStickyId = getToggleSticky().getId();
        savedState.mMediaButtonId = getMediaButton().getId();
        savedState.mExpandButtonId = mExpandButton.getId();

        for (int i = 0; i < getChildCount(); i++) {
            getChildAt(i).saveHierarchyState(savedState.mChildrenStates);
        }

        savedState.mExpansionState = mExpansionState;

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
        int toggleStickyId = mToggleSticky.getId();
        int mediaButtonId = mMediaButton.getId();
        int expandButtonId = mExpandButton.getId();

        mEditText.setId(ss.mEditTextId);
        mToggleSticky.setId(ss.mToggleStickyId);
        mMediaButton.setId(ss.mMediaButtonId);
        mExpandButton.setId(ss.mExpandButtonId);


        super.onRestoreInstanceState(ss.getSuperState());
        for (int i = 0; i < getChildCount(); i++) {
            getChildAt(i).restoreHierarchyState(ss.mChildrenStates);
        }

        mEditText.setId(editTextId);
        mToggleSticky.setId(toggleStickyId);
        mMediaButton.setId(mediaButtonId);
        mExpandButton.setId(expandButtonId);

        if (mExpansionState != ss.mExpansionState) {
            toggleExpansionState();
        }

        this.mExpansionState = ss.mExpansionState;
    }


    static class SavedState extends BaseSavedState {
        private SparseArray<Parcelable> mChildrenStates;
        private int mEditTextId;
        private int mToggleStickyId;
        private int mMediaButtonId;
        public int mExpandButtonId;
        private ExpansionState mExpansionState;

        SavedState(Parcelable superState) {
            super(superState);
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            super.writeToParcel(out, flags);
            out.writeSparseArray(mChildrenStates);
            out.writeInt(mEditTextId);
            out.writeInt(mToggleStickyId);
            out.writeInt(mMediaButtonId);
            out.writeInt(mExpandButtonId);
            out.writeSerializable(mExpansionState);
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
            this.mToggleStickyId = in.readInt();
            this.mMediaButtonId = in.readInt();
            this.mExpandButtonId = in.readInt();
            this.mExpansionState = (ExpansionState) in.readSerializable();
        }
    }


    public enum ExpansionState {
        EXPANDED,
        COLLAPSED
    }
}
