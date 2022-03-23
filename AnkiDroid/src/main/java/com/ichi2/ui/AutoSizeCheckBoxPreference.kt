/*
 Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>

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
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.ichi2.utils.ViewGroupUtils;

import androidx.annotation.NonNull;

// extending androidx.preference didn't work:
// java.lang.ClassCastException: com.ichi2.ui.AutoSizeCheckBoxPreference cannot be cast to android.preference.Preference
@SuppressWarnings("deprecation") // TODO Tracked in https://github.com/ankidroid/Anki-Android/issues/5019
public class AutoSizeCheckBoxPreference extends android.preference.CheckBoxPreference {
    @SuppressWarnings("unused")
    public AutoSizeCheckBoxPreference(Context context) {
        super(context);
    }

    @SuppressWarnings("unused")
    public AutoSizeCheckBoxPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }


    @SuppressWarnings("unused")
    public AutoSizeCheckBoxPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }


    @SuppressWarnings("unused")
    public AutoSizeCheckBoxPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }


    @Override
    protected void onBindView(@NonNull View view) {
        makeMultiline(view);
        super.onBindView(view);
    }


    protected void makeMultiline(@NonNull View view) {
        // https://stackoverflow.com/q/4267939/13121290
        if (view instanceof ViewGroup) {
            for (View child : ViewGroupUtils.getAllChildren((ViewGroup) view)) {
                makeMultiline(child);
            }
        } else if (view instanceof TextView) {
            TextView t = (TextView) view;
            t.setSingleLine(false);
            t.setEllipsize(null);
        }
    }
}
