package com.ichi2.anki;

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.database.SQLException;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.Chronometer;
import android.widget.CompoundButton;
import android.widget.ToggleButton;

public class FlashCard extends Activity {
	public static final String OPT_DB = "DATABASE";
	
	public String card_template;

	AnkiDb.Card currentCard;
	
	// Variables to hold layout objects that we need to update or handle events for.
	WebView mCard;
	ToggleButton mToggleWhiteboard;
	Button mShowAnswer, mSelectRemembered, mSelectNotRemembered;
	Chronometer mTimer;
	Whiteboard mWhiteboard;
	
	// Handler for the 'Show Answer' button.
	View.OnClickListener mShowAnswerHandler = new View.OnClickListener() {
		public void onClick(View view) {
			displayCardAnswer();
		}
	};
	
	// Handler for the Whiteboard toggle button.
	CompoundButton.OnCheckedChangeListener mToggleOverlayHandler = new CompoundButton.OnCheckedChangeListener() {
		public void onCheckedChanged(CompoundButton btn, boolean state) {
			setOverlayState(state);
		}
	};
	
	// Handlers for buttons that allow user to select how well they did.
	View.OnClickListener mSelectRememberedHandler = new View.OnClickListener() {
		public void onClick(View view) {
			// Space this card because it has been successfully remembered.
			currentCard.space();
			nextCard();
			displayCardQuestion();
		}
	};
	View.OnClickListener mSelectNotRememberedHandler = new View.OnClickListener() {
		public void onClick(View view) {
			// Reset this card because it has not been successfully remembered.
			currentCard.reset();
			nextCard();
			displayCardQuestion();
		}
	};

	public void onCreate(Bundle savedInstanceState) throws SQLException {
		super.onCreate(savedInstanceState);
		Bundle extras = getIntent().getExtras();

		initResourceValues();
		
		// Open the database that was requested.
		AnkiDb.openDatabase(extras.getString(OPT_DB));

		// Initialize the current view to the portrait layout.
        requestWindowFeature(Window.FEATURE_NO_TITLE);
		initLayout(R.layout.flashcard_portrait);

		// Start by getting the first card and displaying it.
		nextCard();
		displayCardQuestion();
	}
	
	// Retrieve resource values.
	public void initResourceValues() {
		Resources r = getResources();
		card_template = r.getString(R.string.card_template);
	}
	
	// Set the content view to the one provided and initialize accessors.
	public void initLayout(Integer layout) {
		setContentView(layout);

		mCard = (WebView)findViewById(R.id.flashcard);
		mShowAnswer = (Button)findViewById(R.id.show_answer);
		mSelectRemembered = (Button)findViewById(R.id.select_remembered);
		mSelectNotRemembered = (Button)findViewById(R.id.select_notremembered);
		mTimer = (Chronometer)findViewById(R.id.card_time);
		mToggleWhiteboard = (ToggleButton)findViewById(R.id.toggle_overlay);
		mWhiteboard = (Whiteboard)findViewById(R.id.whiteboard);
		
		mShowAnswer.setOnClickListener(mShowAnswerHandler);
		mSelectRemembered.setOnClickListener(mSelectRememberedHandler);
		mSelectNotRemembered.setOnClickListener(mSelectNotRememberedHandler);
		mToggleWhiteboard.setOnCheckedChangeListener(mToggleOverlayHandler);
	}
	
	public void setOverlayState(boolean enabled) {
		mWhiteboard.setVisibility((enabled) ? View.VISIBLE : View.GONE);
	}
	
	// Get the next card.
	public void nextCard() {
		// TODO: Use un-implemented spaced repetition :)
		currentCard = AnkiDb.Card.smallestIntervalCard();
	}
	
	// Set up the display for the current card.
	public void displayCardQuestion() {
		if (currentCard == null) {
			nextCard();
		}
		
		if (currentCard == null) {
			// error :(
			updateCard("Unable to find a card!");
		} else {
			mWhiteboard.clear();
			mSelectRemembered.setVisibility(View.GONE);
			mSelectNotRemembered.setVisibility(View.GONE);
			mShowAnswer.setVisibility(View.VISIBLE);
			mShowAnswer.requestFocus();
			mTimer.setBase(SystemClock.elapsedRealtime());
			mTimer.start();
			updateCard(currentCard.question);
		}
	}
	
	public void updateCard(String content) {
		String card = card_template.replace("::content::", content);
		mCard.loadDataWithBaseURL("", card, "text/html", "utf-8", null);
	}
	
	// Display the card answer.
	public void displayCardAnswer() {
		mTimer.stop();
		mSelectRemembered.setVisibility(View.VISIBLE);
		mSelectNotRemembered.setVisibility(View.VISIBLE);
		mSelectRemembered.requestFocus();
		mShowAnswer.setVisibility(View.GONE);
		updateCard(currentCard.answer);
	}
	
	// Whiteboard class to allow for drawing on top of a card. This
	// code was taken from the fingerpaint demo app.
	public static class Whiteboard extends View {
        private Paint	mPaint;
        private Bitmap  mBitmap;
        private Canvas  mCanvas;
        private Path    mPath;
        private Paint   mBitmapPaint;
        public  int		mBackgroundColor, mForegroundColor;

        public Whiteboard(Context context, AttributeSet attrs) {
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

	        /* TODO: This bitmap size is arbitrary (taken from fingerpaint).
	         * It should be set to the size of the Whiteboard view. */ 
	        createBitmap(320, 480, Bitmap.Config.ARGB_8888);
            mPath = new Path();
            mBitmapPaint = new Paint(Paint.DITHER_FLAG);			
		}
		
		protected void createBitmap(int w, int h, Bitmap.Config conf) {
			mBitmap = Bitmap.createBitmap(w, h, conf);
			mCanvas = new Canvas(mBitmap);
			clear();
		}
		
        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        	createBitmap(w, h, Bitmap.Config.ARGB_8888);
            super.onSizeChanged(w, h, oldw, oldh);
        }

        public void clear() {
       		mBitmap.eraseColor(mBackgroundColor);
        }
        
        @Override
        protected void onDraw(Canvas canvas) {
        	super.onDraw(canvas);
            canvas.drawColor(mBackgroundColor); 
            canvas.drawBitmap(mBitmap, 0, 0, mBitmapPaint);
            canvas.drawPath(mPath, mPaint);
        }
 
        private float mX, mY;
        private static final float TOUCH_TOLERANCE = 4;

        private void touch_start(float x, float y) {
            mPath.reset();
            mPath.moveTo(x, y);
            mX = x;
            mY = y;
        }
        private void touch_move(float x, float y) {
            float dx = Math.abs(x - mX);
            float dy = Math.abs(y - mY);
            if (dx >= TOUCH_TOLERANCE || dy >= TOUCH_TOLERANCE) {
                mPath.quadTo(mX, mY, (x + mX)/2, (y + mY)/2);
                mX = x;
                mY = y;
            }
        }
        private void touch_up() {
            mPath.lineTo(mX, mY);
            // commit the path to our offscreen
            mCanvas.drawPath(mPath, mPaint);
            // kill this so we don't double draw
            mPath.reset();
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            float x = event.getX();
            float y = event.getY();

            switch (event.getAction()) {
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
            return true;
        }
	}
}

