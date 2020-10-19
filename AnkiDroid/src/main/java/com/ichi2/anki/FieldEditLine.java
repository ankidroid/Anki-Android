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
import android.util.AttributeSet;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.TextView;

import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

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
}
