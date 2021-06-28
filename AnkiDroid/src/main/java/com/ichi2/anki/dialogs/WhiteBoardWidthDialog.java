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

import android.content.Context;
import android.content.SharedPreferences;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.SeekBar;

import com.afollestad.materialdialogs.MaterialDialog;
import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.R;
import com.ichi2.ui.FixedTextView;

import java.util.function.Consumer;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class WhiteBoardWidthDialog {

    private final Context mContext;
    private Integer mWbStrokeWidth;
    private FixedTextView mWbStrokeWidthText;
    public @Nullable
    Consumer<Integer> mOnStrokeWidthChanged;


    public WhiteBoardWidthDialog(@NonNull Context context, @NonNull int wbStrokeWidth) {
       this.mContext = context;
       this.mWbStrokeWidth = wbStrokeWidth;
    }


    public final SeekBar.OnSeekBarChangeListener seekBarChangeListener = new SeekBar.OnSeekBarChangeListener() {
        @Override
        public void onProgressChanged(SeekBar seekBar, int value, boolean b) {
            mWbStrokeWidth = value;
            mWbStrokeWidthText.setText("" + value);
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
            // intentionally blank
        }


        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
            // intentionally blank
        }
    };

    public void showStrokeWidthDialog() {
        LinearLayout layout = new LinearLayout(mContext);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(6, 6, 6, 6);

        mWbStrokeWidthText = new FixedTextView(mContext);
        mWbStrokeWidthText.setGravity(Gravity.CENTER_HORIZONTAL);
        mWbStrokeWidthText.setTextSize(30);
        mWbStrokeWidthText.setText("" + mWbStrokeWidth);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);

        layout.addView(mWbStrokeWidthText, params);

        SeekBar seekBar = new SeekBar(mContext);
        seekBar.setProgress(mWbStrokeWidth);
        seekBar.setOnSeekBarChangeListener(seekBarChangeListener);

        layout.addView(seekBar, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        new MaterialDialog.Builder(mContext)
                .title(R.string.whiteboard_stroke_width)
                .positiveText(R.string.save)
                .negativeText(R.string.dialog_cancel)
                .customView(layout, true)
                .onPositive((dialog, which) -> {
                    if (mOnStrokeWidthChanged != null) {
                        mOnStrokeWidthChanged.accept(mWbStrokeWidth);
                    }
                })
                .show();
    }

    public void onStrokeWidthChanged(Consumer<Integer> c) {
        this.mOnStrokeWidthChanged = c;
    }
}
