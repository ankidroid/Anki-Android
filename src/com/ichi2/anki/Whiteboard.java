/****************************************************************************************
 * Copyright (c) 2009 Andrew <andrewdubya@gmail.com>                                    *
 * Copyright (c) 2009 Nicolas Raoul <nicolas.raoul@gmail.com>                           *
 * Copyright (c) 2009 Edu Zamora <edu.zasu@gmail.com>                                   *
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

package com.ichi2.anki;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import com.tomgibara.android.veecheck.util.PrefSettings;

/**
 * Whiteboard allowing the user to draw the card's answer on the touchscreen.
 */
public class Whiteboard extends View {

    private static final float TOUCH_TOLERANCE = 4;

    private Paint mPaint;
    private Bitmap mBitmap;
    private Canvas mCanvas;
    private Path mPath;
    private Paint mBitmapPaint;

    private int mBackgroundColor;
    private int mForegroundColor;

    private boolean mLocked;
    private boolean mRecreateBitmap = false;

    private float mX;
    private float mY;
    
    private boolean mInvertedColors = false;


    public Whiteboard(Context context, AttributeSet attrs) {
        super(context, attrs);

        if (!mInvertedColors) {
            mForegroundColor = context.getResources().getColor(R.color.wb_fg_color);
            mBackgroundColor = context.getResources().getColor(R.color.wb_bg_color);
        } else {
            mForegroundColor = context.getResources().getColor(R.color.wb_fg_color_inv);
            mBackgroundColor = context.getResources().getColor(R.color.wb_bg_color_inv);
        }

        mPaint = new Paint();
        mPaint.setAntiAlias(true);
        mPaint.setDither(true);
        mPaint.setColor(mForegroundColor);
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeJoin(Paint.Join.ROUND);
        mPaint.setStrokeCap(Paint.Cap.ROUND);
        int wbStrokeWidth = PrefSettings.getSharedPrefs(context).getInt("whiteBoardStrokeWidth", 6);
        mPaint.setStrokeWidth((float) wbStrokeWidth);

        createBitmap();

        mPath = new Path();
        mBitmapPaint = new Paint(Paint.DITHER_FLAG);
    }


    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(mBackgroundColor);
        canvas.drawBitmap(mBitmap, 0, 0, mBitmapPaint);
        canvas.drawPath(mPath, mPaint);
    }


    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        // We want to create the bitmap again only when the screen has been rotated,
        // not when the size changes in the transition between the front and the back
        // of a card (that would made the Whiteboard to disappear)
        if (mRecreateBitmap) {
            createBitmap();
            super.onSizeChanged(w, h, oldw, oldh);
            mRecreateBitmap = false;
        }
    }


    /**
     * Handle touch screen motion events.
     * @param event The motion event.
     * @return True if the event was handled, false otherwise.
     */
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        boolean handled = false;

        if (!mLocked) {
            float x = event.getX();
            float y = event.getY();
            handled = true;

            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    touchStart(x, y);
                    invalidate();
                    break;
                case MotionEvent.ACTION_MOVE:
                    touchMove(x, y);
                    invalidate();
                    break;
                case MotionEvent.ACTION_UP:
                    touchUp();
                    invalidate();
                    break;
                default:
                    handled = false;
            }
        }

        return handled;
    }


    /**
     * Create a new bitmap that fits the new screen layout.
     * The content of the whiteboard does not survive screen rotation.
     */
    public void rotate() {
        mRecreateBitmap = true;
    }


    /**
     * Clear the whiteboard.
     */
    public void clear() {
        mBitmap.eraseColor(mBackgroundColor);
        unlock();
    }


    private void unlock() {
        mLocked = false;
    }

    // XXX: Unused
    // If we don't need to lock the whiteboard, then we should remove mLocked too
//    public void lock() {
//        mLocked = true;
//    }
    public void setInvertedColor(boolean inverted) {
        mInvertedColors = inverted;
    }


    private void createBitmap(int w, int h, Bitmap.Config conf) {
        mBitmap = Bitmap.createBitmap(w, h, conf);
        mCanvas = new Canvas(mBitmap);
        clear();
    }


    private void createBitmap() {
        createBitmap(AnkiDroidApp.getDisplayWidth(), AnkiDroidApp.getDisplayHeight(), Bitmap.Config.ARGB_8888);
    }


    private void touchStart(float x, float y) {
        mPath.reset();
        mPath.moveTo(x, y);
        mX = x;
        mY = y;
    }


    private void touchMove(float x, float y) {
        float dx = Math.abs(x - mX);
        float dy = Math.abs(y - mY);
        if (dx >= TOUCH_TOLERANCE || dy >= TOUCH_TOLERANCE) {
            mPath.quadTo(mX, mY, (x + mX) / 2, (y + mY) / 2);
            mX = x;
            mY = y;
        }
    }


    private void touchUp() {
        mPath.lineTo(mX, mY);
        // commit the path to our offscreen
        mCanvas.drawPath(mPath, mPaint);
        // kill this so we don't double draw
        mPath.reset();
    }
}
