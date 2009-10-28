/**
 * 
 */
package com.ichi2.anki;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

public class Whiteboard extends View
{
	private Paint mPaint;

	private Bitmap mBitmap;

	private Canvas mCanvas;

	private Path mPath;

	private Paint mBitmapPaint;

	public int mBackgroundColor, mForegroundColor, mExtraHeight;

	private boolean mLocked;
	
	private boolean mRecreateBitmap = false;

	public Whiteboard(Context context, AttributeSet attrs)
	{
		super(context, attrs);

		mBackgroundColor = context.getResources().getColor(R.color.wb_bg_color);
		mForegroundColor = context.getResources().getColor(R.color.wb_fg_color);

		mPaint = new Paint();
		mPaint.setAntiAlias(true);
		mPaint.setDither(true);
		mPaint.setColor(mForegroundColor);
		mPaint.setStyle(Paint.Style.STROKE);
		mPaint.setStrokeJoin(Paint.Join.ROUND);
		mPaint.setStrokeCap(Paint.Cap.ROUND);
		mPaint.setStrokeWidth(8);

		/*
		 * TODO: This bitmap size is arbitrary (taken from fingerpaint). It
		 * should be set to the size of the Whiteboard view.
		 */
		createBitmap(320, 480, Bitmap.Config.ARGB_8888);
		mPath = new Path();
		mBitmapPaint = new Paint(Paint.DITHER_FLAG);
	}

	protected void createBitmap(int w, int h, Bitmap.Config conf)
	{
		mBitmap = Bitmap.createBitmap(w, h, conf);
		mCanvas = new Canvas(mBitmap);
		clear();
	}

	@Override
	protected void onSizeChanged(int w, int h, int oldw, int oldh)
	{
		// We want to create the bitmap again only when the screen has been rotated, not when the size changes in the transition
		// between the front and the back of a card (that would made the Whiteboard to disappear)
		if(mRecreateBitmap)
		{
			mBitmap = Bitmap.createBitmap(w, h + mExtraHeight, Bitmap.Config.ARGB_8888);
			mCanvas = new Canvas(mBitmap);
			super.onSizeChanged(w, h, oldw, oldh);
			mRecreateBitmap = false;
		}
	}

	public void rotate(int height) 
	{
		mRecreateBitmap = true;
		mExtraHeight = height;
	}
	
	public void clear()
	{
		mBitmap.eraseColor(mBackgroundColor);
		unlock();
	}

	@Override
	protected void onDraw(Canvas canvas)
	{
		super.onDraw(canvas);
		canvas.drawColor(mBackgroundColor);
		canvas.drawBitmap(mBitmap, 0, 0, mBitmapPaint);
		canvas.drawPath(mPath, mPaint);
	}

	private float mX, mY;

	private static final float TOUCH_TOLERANCE = 4;

	private void touch_start(float x, float y)
	{
		mPath.reset();
		mPath.moveTo(x, y);
		mX = x;
		mY = y;
	}

	private void touch_move(float x, float y)
	{
		float dx = Math.abs(x - mX);
		float dy = Math.abs(y - mY);
		if (dx >= TOUCH_TOLERANCE || dy >= TOUCH_TOLERANCE)
		{
			mPath.quadTo(mX, mY, (x + mX) / 2, (y + mY) / 2);
			mX = x;
			mY = y;
		}
	}

	private void touch_up()
	{
		mPath.lineTo(mX, mY);
		// commit the path to our offscreen
		mCanvas.drawPath(mPath, mPaint);
		// kill this so we don't double draw
		mPath.reset();
	}

	@Override
	public boolean onTouchEvent(MotionEvent event)
	{
		if (!mLocked)
		{
			float x = event.getX();
			float y = event.getY();

			switch (event.getAction())
			{
			case MotionEvent.ACTION_DOWN:
				touch_start(x, y);
				invalidate();
				break;
			case MotionEvent.ACTION_MOVE:
				touch_move(x, y);
				invalidate();
				break;
			case MotionEvent.ACTION_UP:
				touch_up();
				invalidate();
				break;
			}
		}

		return true;
	}

	public void unlock()
	{
		mLocked = false;
	}

	public void lock()
	{
		mLocked = true;
	}
}