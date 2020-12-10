/*
 * Copyright (c) 2009 Andrew <andrewdubya@gmail.com>
 * Copyright (c) 2009 Nicolas Raoul <nicolas.raoul@gmail.com>
 * Copyright (c) 2009 Edu Zamora <edu.zasu@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.Point;

import androidx.annotation.CheckResult;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.core.content.ContextCompat;
import timber.log.Timber;

import android.os.Environment;
import android.view.Display;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;

import com.ichi2.libanki.utils.Time;
import com.ichi2.libanki.utils.TimeUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.lang.ref.WeakReference;
import java.util.Stack;

/**
 * Whiteboard allowing the user to draw the card's answer on the touchscreen.
 */
@SuppressLint("ViewConstructor")
public class Whiteboard extends View {

    private static final float TOUCH_TOLERANCE = 4;

    private final Paint mPaint;
    private final UndoStack mUndo = new UndoStack();
    private Bitmap mBitmap;
    private Canvas mCanvas;
    private final Path mPath;
    private final Paint mBitmapPaint;
    private final WeakReference<AbstractFlashcardViewer> mCardViewer;

    private float mX;
    private float mY;
    private float mSecondFingerX0;
    private float mSecondFingerY0;
    private float mSecondFingerX;
    private float mSecondFingerY;

    private int mSecondFingerPointerId;

    private boolean mSecondFingerWithinTapTolerance;
    private boolean mCurrentlyDrawing = false;
    private boolean mUndoModeActive = false;
    private final int foregroundColor;
    private final LinearLayout mColorPalette;

    @Nullable
    private OnPaintColorChangeListener mOnPaintColorChangeListener;

    public Whiteboard(AbstractFlashcardViewer cardViewer, boolean inverted) {
        super(cardViewer, null);
        mCardViewer = new WeakReference<>(cardViewer);

        Button whitePenColorButton = cardViewer.findViewById(R.id.pen_color_white);
        Button blackPenColorButton = cardViewer.findViewById(R.id.pen_color_black);

        if (!inverted) {
                whitePenColorButton.setVisibility(View.GONE);
                blackPenColorButton.setOnClickListener(this::onClick);
                foregroundColor = Color.BLACK;
        } else {
                blackPenColorButton.setVisibility(View.GONE);
                whitePenColorButton.setOnClickListener(this::onClick);
                foregroundColor = Color.WHITE;
        }

        mPaint = new Paint();
        mPaint.setAntiAlias(true);
        mPaint.setDither(true);
        mPaint.setColor(foregroundColor);
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeJoin(Paint.Join.ROUND);
        mPaint.setStrokeCap(Paint.Cap.ROUND);
        int wbStrokeWidth = AnkiDroidApp.getSharedPrefs(cardViewer).getInt("whiteBoardStrokeWidth", 6);
        mPaint.setStrokeWidth((float) wbStrokeWidth);
        createBitmap();
        mPath = new Path();
        mBitmapPaint = new Paint(Paint.DITHER_FLAG);

        // selecting pen color to draw
        mColorPalette = cardViewer.findViewById(R.id.whiteboard_pen_color);

        cardViewer.findViewById(R.id.pen_color_red).setOnClickListener(this::onClick);
        cardViewer.findViewById(R.id.pen_color_green).setOnClickListener(this::onClick);
        cardViewer.findViewById(R.id.pen_color_blue).setOnClickListener(this::onClick);
        cardViewer.findViewById(R.id.pen_color_yellow).setOnClickListener(this::onClick);
    }


    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(0);
        canvas.drawBitmap(mBitmap, 0, 0, mBitmapPaint);
        canvas.drawPath(mPath, mPaint);
    }


    /** Handle motion events to draw using the touch screen or to interact with the flashcard behind
     * the whiteboard by using a second finger.
     *
     * @param event The motion event.
     * @return True if the event was handled, false otherwise
     */
    public boolean handleTouchEvent(MotionEvent event) {
        return handleDrawEvent(event) || handleMultiTouchEvent(event);
    }


    /**
     * Handle motion events to draw using the touch screen. Only simple touch events are processed,
     * a multitouch event aborts to current stroke.
     *
     * @param event The motion event.
     * @return True if the event was handled, false otherwise or when drawing was aborted due to
     *              detection of a multitouch event.
     */
    private boolean handleDrawEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                drawStart(x, y);
                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE:
                if (mCurrentlyDrawing) {
                    for (int i = 0; i < event.getHistorySize(); i++) {
                        drawAlong(event.getHistoricalX(i), event.getHistoricalY(i));
                    }
                    drawAlong(x, y);
                    invalidate();
                    return true;
                }
                return false;
            case MotionEvent.ACTION_UP:
                if (mCurrentlyDrawing) {
                    drawFinish();
                    invalidate();
                    return true;
                }
                return false;
            case MotionEvent.ACTION_POINTER_DOWN:
                if (mCurrentlyDrawing) {
                    drawAbort();
                }
                return false;
            default:
                return false;
        }
    }

    // Parse multitouch input to scroll the card behind the whiteboard or click on elements
    private boolean handleMultiTouchEvent(MotionEvent event) {
        if (event.getPointerCount() == 2) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_POINTER_DOWN:
                    reinitializeSecondFinger(event);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    return trySecondFingerScroll(event);
                case MotionEvent.ACTION_POINTER_UP:
                    return trySecondFingerClick(event);
                default:
                    return false;
            }
        }
        return false;
    }


    /**
     * Clear the whiteboard.
     */
    public void clear() {
        mUndoModeActive = false;
        mBitmap.eraseColor(0);
        mUndo.clear();
        invalidate();
        if (mCardViewer.get() != null) {
            mCardViewer.get().supportInvalidateOptionsMenu();
        }
    }


    /**
     * Undo the last stroke
     */
    public void undo() {
        mUndo.pop();
        mUndo.apply();
        if (undoEmpty() && mCardViewer.get() != null) {
            mCardViewer.get().supportInvalidateOptionsMenu();
        }
    }


    /** @return Whether there are strokes to undo */
    public boolean undoEmpty() {
        return mUndo.empty();
    }

    /**
     * @return true if the undo queue has had any strokes added to it since the last clear
     */
    public boolean isUndoModeActive() {
        return mUndoModeActive;
    }

    private void createBitmap(int w, int h) {
        mBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        mCanvas = new Canvas(mBitmap);
        clear();
    }


    private void createBitmap() {
        // To fix issue #1336, just make the whiteboard big and square.
        final Point p = getDisplayDimensions();
        int bitmapSize = Math.max(p.x, p.y);
        createBitmap(bitmapSize, bitmapSize);
    }

    private void drawStart(float x, float y) {
        mCurrentlyDrawing = true;
        mPath.reset();
        mPath.moveTo(x, y);
        mX = x;
        mY = y;
    }


    private void drawAlong(float x, float y) {
        float dx = Math.abs(x - mX);
        float dy = Math.abs(y - mY);
        if (dx >= TOUCH_TOLERANCE || dy >= TOUCH_TOLERANCE) {
            mPath.quadTo(mX, mY, (x + mX) / 2, (y + mY) / 2);
            mX = x;
            mY = y;
        }
    }


    private void drawFinish() {
        mCurrentlyDrawing = false;
        PathMeasure pm = new PathMeasure(mPath, false);
        mPath.lineTo(mX, mY);
        Paint paint = new Paint(mPaint);
        WhiteboardAction action = pm.getLength() > 0 ? new DrawPath(new Path(mPath), paint) : new DrawPoint(mX, mY, paint);
        action.apply(mCanvas);
        mUndo.add(action);
        mUndoModeActive = true;
        // kill the path so we don't double draw
        mPath.reset();
        if (mUndo.size() == 1 && mCardViewer.get() != null) {
            mCardViewer.get().supportInvalidateOptionsMenu();
        }
    }


    private void drawAbort() {
        drawFinish();
        undo();
    }


    // call this with an ACTION_POINTER_DOWN event to start a new round of detecting drag or tap with
    // a second finger
    private void reinitializeSecondFinger(MotionEvent event) {
        mSecondFingerWithinTapTolerance = true;
        mSecondFingerPointerId = event.getPointerId(event.getActionIndex());
        mSecondFingerX0 = event.getX(event.findPointerIndex(mSecondFingerPointerId));
        mSecondFingerY0 = event.getY(event.findPointerIndex(mSecondFingerPointerId));
    }

    private boolean updateSecondFinger(MotionEvent event) {
        int pointerIndex = event.findPointerIndex(mSecondFingerPointerId);
        if (pointerIndex > -1) {
            mSecondFingerX = event.getX(pointerIndex);
            mSecondFingerY = event.getY(pointerIndex);
            float dx = Math.abs(mSecondFingerX0 - mSecondFingerX);
            float dy = Math.abs(mSecondFingerY0 - mSecondFingerY);
            if (dx >= TOUCH_TOLERANCE || dy >= TOUCH_TOLERANCE) {
                mSecondFingerWithinTapTolerance = false;
            }
            return true;
        }
        return false;
    }

    // call this with an ACTION_POINTER_UP event to check whether it matches a tap of the second finger
    // if so, forward a click action and return true
    private boolean trySecondFingerClick(MotionEvent event) {
        if (mSecondFingerPointerId == event.getPointerId(event.getActionIndex())) {
            updateSecondFinger(event);
            AbstractFlashcardViewer cardViewer = mCardViewer.get();
            if (mSecondFingerWithinTapTolerance && cardViewer != null) {
                cardViewer.tapOnCurrentCard((int) mSecondFingerX, (int) mSecondFingerY);
                return true;
            }
        }
        return false;
    }

    // call this with an ACTION_MOVE event to check whether it is within the threshold for a tap of the second finger
    // in this case perform a scroll action
    private boolean trySecondFingerScroll(MotionEvent event) {
        if (updateSecondFinger(event) && !mSecondFingerWithinTapTolerance) {
            int dy = (int) (mSecondFingerY0 - mSecondFingerY);
            AbstractFlashcardViewer cardViewer = mCardViewer.get();
            if (dy != 0 && cardViewer != null) {
                cardViewer.scrollCurrentCardBy(dy);
                mSecondFingerX0 = mSecondFingerX;
                mSecondFingerY0 = mSecondFingerY;
            }
            return true;
        }
        return false;
    }

    private static Point getDisplayDimensions() {
        Display display = ((WindowManager) AnkiDroidApp.getInstance().getApplicationContext().
                getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
        Point point = new Point();
        display.getSize(point);
        return point;
    }


    public void onClick(View view) {

        int id = view.getId();
        if (id == R.id.pen_color_white) {
            setPenColor(Color.WHITE);
        } else if (id == R.id.pen_color_black) {
            setPenColor(Color.BLACK);
        } else if (id == R.id.pen_color_red) {
            int redPenColor = ContextCompat.getColor(getContext(), R.color.material_red_500);
            setPenColor(redPenColor);
        } else if (id == R.id.pen_color_green) {
            int greenPenColor = ContextCompat.getColor(getContext(), R.color.material_green_500);
            setPenColor(greenPenColor);
        } else if (id == R.id.pen_color_blue) {
            int bluePenColor = ContextCompat.getColor(getContext(), R.color.material_blue_500);
            setPenColor(bluePenColor);
        } else if (id == R.id.pen_color_yellow) {
            int yellowPenColor = ContextCompat.getColor(getContext(), R.color.material_yellow_500);
            setPenColor(yellowPenColor);
        }
    }


    public void setPenColor(int color) {
        Timber.d("Setting pen color to %d", color);
        mPaint.setColor(color);
        mColorPalette.setVisibility(View.GONE);
        if (mOnPaintColorChangeListener != null) {
            mOnPaintColorChangeListener.onPaintColorChange(color);
        }
    }

    @VisibleForTesting
    public int getPenColor() {
        return mPaint.getColor();
    }


    public void setOnPaintColorChangeListener(@Nullable OnPaintColorChangeListener mOnPaintColorChangeListener) {
        this.mOnPaintColorChangeListener = mOnPaintColorChangeListener;
    }


    /**
     * Keep a stack of all points and paths so that the last stroke can be undone
     * pop() removes the last stroke from the stack, and apply() redraws it to whiteboard.
     */
    private class UndoStack {
        private final Stack<WhiteboardAction> mStack = new Stack<>();

        public void add(WhiteboardAction action) {
            mStack.add(action);
        }

        public void clear() {
            mStack.clear();
        }

        public int size() {
            return mStack.size();
        }

        public void pop() {
            mStack.pop();
        }

        public void apply() {
            mBitmap.eraseColor(0);

            for (WhiteboardAction action : mStack) {
                action.apply(mCanvas);
            }
            invalidate();
        }

        public boolean empty() {
            return mStack.empty();
        }
    }

    private interface WhiteboardAction {
        void apply(@NonNull Canvas canvas);
    }

    private static class DrawPoint implements WhiteboardAction {

        private final float mX;
        private final float mY;
        private final Paint mPaint;


        public DrawPoint(float x, float y, Paint paint) {
            mX = x;
            mY = y;
            mPaint = paint;
        }


        @Override
        public void apply(@NonNull Canvas canvas) {
            canvas.drawPoint(mX, mY, mPaint);
        }
    }

    private static class DrawPath implements WhiteboardAction {
        private final Path mPath;
        private final Paint mPaint;

        public DrawPath(Path path, Paint paint) {
            mPath = path;
            mPaint = paint;
        }


        @Override
        public void apply(@NonNull Canvas canvas) {
            canvas.drawPath(mPath, mPaint);
        }
    }

    public boolean isCurrentlyDrawing() {
        return mCurrentlyDrawing;
    }

    @SuppressWarnings( {"deprecation", "RedundantSuppression"}) // TODO Tracked in https://github.com/ankidroid/Anki-Android/issues/5304
    protected String saveWhiteboard(Time time) throws FileNotFoundException {

        Bitmap bitmap = Bitmap.createBitmap(this.getWidth(), this.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        File pictures = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        File ankiDroidFolder = new File(pictures, "AnkiDroid");

        if (!ankiDroidFolder.exists()) {
            //noinspection ResultOfMethodCallIgnored
            ankiDroidFolder.mkdirs();
        }

        String baseFileName = "Whiteboard";
        String timeStamp = TimeUtils.getTimestamp(time);
        String finalFileName = baseFileName + timeStamp + ".png";

        File saveWhiteboardImageFile = new File(ankiDroidFolder, finalFileName);

        if (foregroundColor != Color.BLACK) {
            canvas.drawColor(Color.BLACK);
        } else {
            canvas.drawColor(Color.WHITE);
        }

        this.draw(canvas);
        bitmap.compress(Bitmap.CompressFormat.JPEG, 95, new FileOutputStream(saveWhiteboardImageFile));
        return saveWhiteboardImageFile.getAbsolutePath();
    }

    @VisibleForTesting
    @CheckResult
    protected int getForegroundColor() {
        return foregroundColor;
    }

    public interface OnPaintColorChangeListener {
        void onPaintColorChange(@Nullable Integer color);
    }
}
