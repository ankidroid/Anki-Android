/****************************************************************************************
 * Copyright (c) 2018 Mike Hardy <mike@mikehardy.net>                                   *
 * Copyright (c) 2021 David Allison <davidallisongithub@gmail.com>                      *
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

package com.ichi2.preferences;

import android.content.Context;
import android.os.Bundle;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceDialogFragmentCompat;

public class ConfirmationPreferenceCompat extends androidx.preference.DialogPreference {

    @NonNull
    private Runnable mCancelHandler = () -> { /* do nothing by default */ };
    @NonNull
    private Runnable mOkHandler = () -> { /* do nothing by default */ };

    public ConfirmationPreferenceCompat(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public ConfirmationPreferenceCompat(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }


    public ConfirmationPreferenceCompat(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ConfirmationPreferenceCompat(Context context) {
        super(context);
    }

    public void setCancelHandler(@NonNull Runnable cancelHandler) {
        this.mCancelHandler = cancelHandler;
    }


    public void setOkHandler(@NonNull Runnable okHandler) {
        this.mOkHandler = okHandler;
    }

    public static class ConfirmationDialogFragmentCompat extends PreferenceDialogFragmentCompat {

        @Override
        public ConfirmationPreferenceCompat getPreference() {
            return (ConfirmationPreferenceCompat) super.getPreference();
        }

        @Override
        public void onDialogClosed(boolean positiveResult) {
            if (positiveResult) {
                getPreference().mOkHandler.run();
            } else {
                getPreference().mCancelHandler.run();
            }
        }

        public static ConfirmationDialogFragmentCompat newInstance(@NonNull String key) {
            ConfirmationDialogFragmentCompat fragment = new ConfirmationDialogFragmentCompat();
            Bundle b = new Bundle(1);
            b.putString(ARG_KEY, key);
            fragment.setArguments(b);
            return fragment;
        }
    }
}