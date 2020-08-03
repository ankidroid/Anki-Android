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

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.DrawableWrapper;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

@RequiresApi(api = Build.VERSION_CODES.M)
public class BadgeDrawable extends DrawableWrapper {

    private final Paint mPaint;

    private Drawable mBadge;
    private String mText;
    private float mTextX;
    private float mTextY;


    /**
     * Creates a new wrapper around the specified drawable.
     *
     * @param dr the drawable to wrap
     */
    public BadgeDrawable(@Nullable Drawable dr) {
        super(dr);
        mPaint = new Paint();
        mPaint.setTypeface(Typeface.DEFAULT_BOLD);
        mPaint.setTextAlign(Paint.Align.CENTER);
        mPaint.setColor(Color.WHITE);
    }

    public void setBadgeDrawable(@NonNull Drawable view) {
        mBadge = view;

        // This goes out of bounds - it seems to be fine
        int mSize = (int) (getIntrinsicWidth() * 0.70);

        mPaint.setTextSize((float) (mSize * 0.8));

        int left = (int) (getIntrinsicWidth() * 0.55);
        int bottom = (int) (getIntrinsicHeight() * 0.45);

        int right = left + mSize;
        int top = bottom - mSize;
        mBadge.setBounds(left, top, right, bottom);

        float vcenter = (top + bottom) / 2.0f;

        mTextX = (left + right) / 2.0f;
        mTextY = vcenter - (mPaint.descent() + mPaint.ascent()) / 2;

    }

    public void setText(char c) {
        this.mText = new String(new char[] {c});
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        super.draw(canvas);
        if (mBadge != null) {
            mBadge.draw(canvas);

            if (mText != null) {
                canvas.drawText(mText, mTextX, mTextY, mPaint);
            }
        }
    }
}
