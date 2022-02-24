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

    public static final double ICON_SCALE_TEXT = 0.70;
    public static final double ICON_SCALE_BARE = 0.40;
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
        invalidateSize();

    }


    private void invalidateSize() {
        // This goes out of bounds - it seems to be fine
        int size = (int) (getIntrinsicWidth() * getIconScale());

        mPaint.setTextSize((float) (size * 0.8));

        int left = (int) getLeft();
        int bottom = (int) getBottom();

        int right = left + size;
        int top = bottom - size;
        if (mBadge != null) {
            mBadge.setBounds(left, top, right, bottom);
        }

        float vcenter = (top + bottom) / 2.0f;

        mTextX = (left + right) / 2.0f;
        mTextY = vcenter - (mPaint.descent() + mPaint.ascent()) / 2;
    }


    private double getBottom() {
        int h = getIntrinsicHeight();
        if (isShowingText()) {
            return h * 0.45;
        } else {
            return (h * getIconScale());
        }
    }


    private double getLeft() {
        int w = getIntrinsicWidth();
        if (isShowingText()) {
            return w * 0.55;
        } else {
            return w - (w * getIconScale());
        }
    }


    private double getIconScale() {
        if (isShowingText()) {
            return ICON_SCALE_TEXT;
        } else {
            return ICON_SCALE_BARE;
        }
    }


    private boolean isShowingText() {
        return mText != null && mText.length() > 0;
    }


    public void setText(char c) {
        this.mText = new String(new char[] {c});
        invalidateSize();
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
