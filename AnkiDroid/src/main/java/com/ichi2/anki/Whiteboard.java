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
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.PointF;
import android.support.v4.content.ContextCompat;
import android.view.Display;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

import java.lang.ref.WeakReference;
import java.util.Stack;

/**
 * Whiteboard allowing the user to draw the card's answer on the touchscreen.
 */
public class Whiteboard extends View {

    private static final float TOUCH_TOLERANCE = 4;

    private Paint mPaint;
    private UndoStack mUndo = new UndoStack();
    private Bitmap mBitmap;
    private Canvas mCanvas;
    private Path mPath;
    private Paint mBitmapPaint;
    private WeakReference<AnkiActivity> mActivity;

    private float mX;
    private float mY;

    private boolean mInvertedColors = false;
    private boolean mMonochrome = true;
    private boolean mUndoModeActive = false;


    public Whiteboard(AnkiActivity context, boolean inverted, boolean monochrome) {
        super(context, null);
        mActivity = new WeakReference<>(context);
        mInvertedColors = inverted;
        mMonochrome = monochrome;

        int foregroundColor;
        if (!mInvertedColors) {
            if (mMonochrome) {
                foregroundColor = Color.BLACK;
            } else {
                foregroundColor = ContextCompat.getColor(context, R.color.wb_fg_color);
            }
        } else {
            if (mMonochrome) {
                foregroundColor = Color.WHITE;
            } else {
                foregroundColor = ContextCompat.getColor(context, R.color.wb_fg_color_inv);
            }
        }

        mPaint = new Paint();
        mPaint.setAntiAlias(true);
        mPaint.setDither(true);
        mPaint.setColor(foregroundColor);
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeJoin(Paint.Join.ROUND);
        mPaint.setStrokeCap(Paint.Cap.ROUND);
        int wbStrokeWidth = AnkiDroidApp.getSharedPrefs(context).getInt("whiteBoardStrokeWidth", 6);
        mPaint.setStrokeWidth((float) wbStrokeWidth);
        createBitmap();
        mPath = new Path();
        mBitmapPaint = new Paint(Paint.DITHER_FLAG);
    }


    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(0);
        canvas.drawBitmap(mBitmap, 0, 0, mBitmapPaint);
        canvas.drawPath(mPath, mPaint);
    }


    /**
     * Handle touch screen motion events.
     * 
     * @param event The motion event.
     * @return True if the event was handled, false otherwise.
     */
    public boolean handleTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                touchStart(x, y);
                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE:
                for (int i = 0; i < event.getHistorySize(); i++) {
                    touchMove(event.getHistoricalX(i), event.getHistoricalY(i));
                }
                touchMove(x, y);
                invalidate();
                return true;
            case MotionEvent.ACTION_UP:
                touchUp();
                invalidate();
                return true;
            default:
                return false;
        }
    }


    /**
     * Clear the whiteboard.
     */
    public void clear() {
        mUndoModeActive = false;
        mBitmap.eraseColor(0);
        mUndo.clear();
        invalidate();
        if (mActivity.get() != null) {
            mActivity.get().supportInvalidateOptionsMenu();
        }
    }


    /**
     * Undo the last stroke
     */
    public void undo() {
        mUndo.pop();
        mUndo.apply();
        if (undoSize() == 0 && mActivity.get() != null) {
            mActivity.get().supportInvalidateOptionsMenu();
        }
    }

    /**
     * @return the number of strokes currently on the undo queue
     */
    public int undoSize() {
        return mUndo.size();
    }

    /**
     * @return true if the undo queue has had any strokes added to it since the last clear
     */
    public boolean isUndoModeActive() {
        return mUndoModeActive;
    }

    private void createBitmap(int w, int h, Bitmap.Config conf) {
        mBitmap = Bitmap.createBitmap(w, h, conf);
        mCanvas = new Canvas(mBitmap);
        clear();
    }


    private void createBitmap() {
        // To fix issue #1336, just make the whiteboard big and square.
        int bitmapSize = Math.max(getDisplayWidth(), getDisplayHeight());
        if (mMonochrome && !mInvertedColors) {
            createBitmap(bitmapSize, bitmapSize, Bitmap.Config.ALPHA_8);
        } else {
            createBitmap(bitmapSize, bitmapSize, Bitmap.Config.ARGB_4444);
        }
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
        PathMeasure pm = new PathMeasure(mPath, false);
        mPath.lineTo(mX, mY);
        if(pm.getLength() > 0){
            mCanvas.drawPath(mPath, mPaint);
            mUndo.add(mPath);
        } else {
            mCanvas.drawPoint(mX, mY, mPaint);
            mUndo.add(mX, mY);
        }
        mUndoModeActive = true;
        // kill the path so we don't double draw
        mPath.reset();
        if (mUndo.size() == 1 && mActivity.get() != null) {
            mActivity.get().supportInvalidateOptionsMenu();
        }
    }


    private static int getDisplayHeight() {
        Display display = ((WindowManager) AnkiDroidApp.getInstance().getApplicationContext().
                getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
        return display.getHeight();
    }


    private static int getDisplayWidth() {
        Display display = ((WindowManager) AnkiDroidApp.getInstance().getApplicationContext().
                getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
        return display.getWidth();
    }

    /**
     * Keep a stack of all points and paths so that the last stroke can be undone
     * pop() removes the last stroke from the stack, and apply() redraws it to whiteboard.
     */
    private class UndoStack {
        private final Stack<Path> mPathStack = new Stack<>();
        private final Stack<PointF> mPointStack = new Stack<>();
        private final Stack<Integer> mWhichStack = new Stack<>();

        public void add(Path path) {
            mPathStack.add(new Path(path));
            mWhichStack.add(0);
        }
        public void add(float x, float y) {
            mPointStack.add(new PointF(x, y));
            mWhichStack.add(1);
        }

        public void clear() {
            mPathStack.clear();
            mPointStack.clear();
            mWhichStack.clear();
        }

        public int size() {
            return mWhichStack.size();
        }

        public void pop() {
            if (mWhichStack.size() == 0) return;
            switch (mWhichStack.peek()) {
                case 0:
                    mPathStack.pop();
                    break;
                case 1:
                    mPointStack.pop();
                    break;
            }
            mWhichStack.pop();
        }

        public void apply() {
            mBitmap.eraseColor(0);
            for (Path path : mPathStack) {
                mCanvas.drawPath(path, mPaint);
            }
            for (PointF point : mPointStack) {
                mCanvas.drawPoint(point.x, point.y, mPaint);
            }
            invalidate();
        }
    }
}
