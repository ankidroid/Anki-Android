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
package com.ichi2.ui;

import android.content.Context;
import android.content.res.TypedArray;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.widget.CompoundButton;

import com.ichi2.anki.R;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatCheckBox;

import static com.ichi2.ui.CheckBoxTriStates.State.*;

/**
 * Based on https://gist.github.com/kevin-barrientos/d75a5baa13a686367d45d17aaec7f030.
 */
public class CheckBoxTriStates extends AppCompatCheckBox {

    public enum State {
        INDETERMINATE,
        UNCHECKED,
        CHECKED
    }


    private State mState;
    private boolean mCycleBackToIndeterminate;

    /**
     * This is the listener set to the super class which is going to be invoked each
     * time the check state has changed.
     */
    private final OnCheckedChangeListener mPrivateListener = new CompoundButton.OnCheckedChangeListener() {

        // checkbox status is changed from unchecked to checked.
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            toggle();
        }
    };

    /**
     * Holds a reference to the listener set by a client, if any.
     */
    private OnCheckedChangeListener mClientListener;

    /**
     * This flag is needed to avoid accidentally changing the current {@link #mState} when
     * {@link #onRestoreInstanceState(Parcelable)} calls {@link #setChecked(boolean)}
     * invoking our {@link #mPrivateListener} and therefore changing the real state.
     */
    private boolean mRestoring;


    public CheckBoxTriStates(Context context) {
        super(context);
        init(context, null);
    }


    public CheckBoxTriStates(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }


    public CheckBoxTriStates(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }


    public State getState() {
        return mState;
    }


    public void setState(State state) {
        if (!this.mRestoring && this.mState != state) {
            this.mState = state;

            if (this.mClientListener != null) {
                this.mClientListener.onCheckedChanged(this, this.isChecked());
            }

            updateBtn();
        }
    }


    @Override
    public void toggle() {
        switch (mState) {
            case INDETERMINATE:
                setState(UNCHECKED);
                break;
            case UNCHECKED:
                setState(CHECKED);
                break;
            case CHECKED:
                if (mCycleBackToIndeterminate) {
                    setState(INDETERMINATE);
                } else {
                    setState(UNCHECKED);
                }
                break;
        }
    }


    @Override
    public void setChecked(boolean checked) {
        mState = checked ? CHECKED : UNCHECKED;
    }


    @Override
    public boolean isChecked() {
        return mState != UNCHECKED;
    }


    public void setCycleBackToIndeterminate(boolean cycleBackToIndeterminate) {
        mCycleBackToIndeterminate = cycleBackToIndeterminate;
    }


    @Override
    public void setOnCheckedChangeListener(@Nullable OnCheckedChangeListener listener) {

        // we never truly set the listener to the client implementation, instead we only hold
        // a reference to it and invoke it when needed.
        if (this.mPrivateListener != listener) {
            this.mClientListener = listener;
        }

        // always use our implementation
        super.setOnCheckedChangeListener(mPrivateListener);
    }


    @Override
    public Parcelable onSaveInstanceState() {
        Parcelable superState = super.onSaveInstanceState();

        SavedState ss = new SavedState(superState);

        ss.mState = mState;
        ss.mCycleBackToIndeterminate = mCycleBackToIndeterminate;

        return ss;
    }


    @Override
    public void onRestoreInstanceState(Parcelable state) {
        this.mRestoring = true; // indicates that the ui is restoring its state
        SavedState ss = (SavedState) state;
        super.onRestoreInstanceState(ss.getSuperState());
        setState(ss.mState);
        setCycleBackToIndeterminate(ss.mCycleBackToIndeterminate);
        requestLayout();
        this.mRestoring = false;
    }


    private void init(@NonNull Context context, @Nullable AttributeSet attrs) {
        mCycleBackToIndeterminate = true;
        if (attrs != null) {
            TypedArray a = context.getTheme().obtainStyledAttributes(
                    attrs, R.styleable.CheckBoxTriStates, 0, 0);
            mCycleBackToIndeterminate = a.getBoolean(R.styleable.CheckBoxTriStates_cycle_back_to_indeterminate,
                    mCycleBackToIndeterminate);
        }
        mState = UNCHECKED;
        updateBtn();
        setOnCheckedChangeListener(this.mPrivateListener);
    }


    private void updateBtn() {
        int btnDrawable;
        switch (mState) {
            case UNCHECKED:
                btnDrawable = R.drawable.ic_baseline_check_box_outline_blank_24;
                break;
            case CHECKED:
                btnDrawable = R.drawable.ic_baseline_check_box_24;
                break;
            default:
                btnDrawable = R.drawable.ic_baseline_indeterminate_check_box_24;
        }
        setButtonDrawable(btnDrawable);
    }


    private static class SavedState extends BaseSavedState {
        State mState;
        boolean mCycleBackToIndeterminate;

        SavedState(Parcelable superState) {
            super(superState);
        }


        private SavedState(Parcel in) {
            super(in);
            mState = State.values()[in.readInt()];
            mCycleBackToIndeterminate = in.readInt() != 0;
        }


        @Override
        public void writeToParcel(Parcel out, int flags) {
            super.writeToParcel(out, flags);
            out.writeValue(mState);
            out.writeInt(mCycleBackToIndeterminate ? 1 : 0);
        }


        @Override
        public String toString() {
            return "CheckboxTriState.SavedState{"
                    + Integer.toHexString(System.identityHashCode(this))
                    + " state=" + mState
                    + " cycleBackToIndeterminate=" + mCycleBackToIndeterminate + "}";
        }


        @SuppressWarnings("hiding")
        public static final Parcelable.Creator<SavedState> CREATOR =
                new Parcelable.Creator<SavedState>() {
                    @Override
                    public SavedState createFromParcel(Parcel in) {
                        return new SavedState(in);
                    }


                    @Override
                    public SavedState[] newArray(int size) {
                        return new SavedState[size];
                    }
                };

    }
}