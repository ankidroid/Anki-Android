/***************************************************************************************
 *                                                                                      *
 * Copyright (c) 2011 Kostas Spyropoulos <inigo.aldana@gmail.com>
 * Copyright (c) 2013 Jolta Technologies					                            *
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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.amr.arabic.ArabicUtilities;
import org.xml.sax.XMLReader;

import com.ichi2.anim.ActivityTransitionAnimation;
import com.ichi2.anim.Animation3D;
import com.ichi2.anki.Reviewer.AnkiDroidWebChromeClient;
import com.ichi2.anki.Reviewer.JavaScriptInterface;
import com.ichi2.anki.Reviewer.MyGestureDetector;
import com.ichi2.anki.Reviewer.ScrollTextView;
import com.ichi2.async.DeckTask;
import com.ichi2.libanki.Card;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Note;
import com.ichi2.libanki.Sound;
import com.ichi2.libanki.Utils;
import com.ichi2.themes.HtmlColors;
import com.ichi2.themes.Themes;
import com.ichi2.utils.DiffEngine;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Vibrator;
import android.provider.MediaStore;
import android.support.v7.app.ActionBarActivity;
import android.text.ClipboardManager;
import android.text.Editable;
import android.text.Html;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.SpannedString;
import android.text.Html.TagHandler;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.util.Log;
import android.view.Display;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.View.OnClickListener;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.inputmethod.InputMethodManager;
import android.webkit.JavascriptInterface;
import android.webkit.JsResult;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.Chronometer;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.FrameLayout.LayoutParams;

public class PreviewClass extends ActionBarActivity {
	
	private Card mCurrentCard;
	private LinearLayout mFlipCardLayout;
	private FrameLayout mCardFrame;
	private TextView mChosenAnswer;
	private EditText mAnswerField;
	private View mMainLayout;
	private FrameLayout mCardContainer;
	private FrameLayout mTouchLayer;
	private boolean mChangeBorderStyle;
	private WebView mCard;
    private LinearLayout bottom_area_layout;
    private LinearLayout mEase1Layout;
    private LinearLayout mEase2Layout;
    private LinearLayout mEase3Layout;
    private LinearLayout mEase4Layout;
    private TextView mNext1;
    private TextView mNext2;
    private TextView mNext3;
    private TextView mNext4;
    private Whiteboard mWhiteboard;
    private boolean mPrefWhiteboard;
    private boolean mBlackWhiteboard = true;
    private boolean mshowNextReviewTime;
    private boolean mInvertedColors = false;
    private boolean mShowWhiteboard = false;
    private int mNextTimeTextColor;
    private int mNextTimeTextRecomColor;
    private int mForegroundColor;
    private boolean mPrefWriteAnswers;
    private String mTypeCorrect;
    private String mCardTemplate;
    private static boolean sDisplayAnswer = false;
    private String mTypeWarning;
    private boolean mCurrentSimpleInterface = false;
    private Spanned mCardContent;
    private boolean mPrefFixArabic;
    private boolean mSpeakText;
    private WebView mNextCard;
    public static final String ANSWER_CLASS = "answer";
    private TextView mSimpleCard;
    private String mBaseUrl;
    public static final String QUESTION_CLASS = "question";
    private int mDisplayFontSize = 100;
    private static final int DYNAMIC_FONT_MAX_SIZE = 14;
    private boolean mNightMode = false;
    private boolean mIsXScrolling = false;
    private boolean mIsYScrolling = false;
    private Map<String, AnkiFont> mCustomFonts;
    private String mCustomDefaultFontCss;
    /** Min size of the font for dynamic calculation of font size */
    private static final int DYNAMIC_FONT_MIN_SIZE = 3;
    private static final int DYNAMIC_FONT_FACTOR = 5;
    private static final Pattern sTypeAnsPat = Pattern.compile("\\[\\[type:(.+?)\\]\\]");
    private static final Pattern fFontSizePattern = Pattern.compile(
            "font-size\\s*:\\s*([0-9.]+)\\s*((?:px|pt|in|cm|mm|pc|%|em))\\s*;?", Pattern.CASE_INSENSITIVE);
    private static final Pattern fSpanDivPattern = Pattern.compile(
            "<(/?)(span|div)", Pattern.CASE_INSENSITIVE);
    private static final Set<String> fRelativeCssUnits = new HashSet<String>(
            Arrays.asList(new String[]{ "%", "em" }));
    private String mCustomFontStyle;
    private boolean mZoomEnabled;
    private int mCurrentBackgroundColor;
    private boolean mInputWorkaround;
    private boolean mRefreshWebview = true;
    private boolean mPrefFullscreenReview=true;
    private boolean mAnswerSoundsAdded = false;
    private Button mFlipCard;
    private static final Pattern sSpanPattern = Pattern.compile("</?span[^>]*>");
    private static final Pattern sBrPattern = Pattern.compile("<br\\s?/?>");
    private String mTypeFont;
    private RelativeLayout top_bar;



	 @Override
	    protected void onCreate(Bundle savedInstanceState) {
	        // Log.i(AnkiDroidApp.TAG, "CardEditor: onCreate");

	        super.onCreate(savedInstanceState);
	        mCurrentCard =CardEditor.mCurrentEditedCard;
	        mRefreshWebview = getRefreshWebviewAndInitializeWebviewVariables();
	        initLayout(R.layout.flashcard);

	        Collection col = AnkiDroidApp.getCol();
	        if (col == null) {
	            //reloadCollection(savedInstanceState);
	            return;
	        }
	        mPrefFullscreenReview = AnkiDroidApp.getSharedPrefs(getBaseContext()).getBoolean("fullscreenReview", false);
	        mGesturesEnabled = AnkiDroidApp.initiateGestures(this, AnkiDroidApp.getSharedPrefs(getBaseContext()));
	        setFullScreen(true);
	        mBaseUrl = Utils.getBaseUrl(col.getMedia().getDir());

	        try {
                mCardTemplate = Utils.convertStreamToString(getAssets().open("card_template.html"));
            } catch (IOException e) {
                e.printStackTrace();
            }
	        PreviewClass.this.displayCardQuestion();
	        PreviewClass.this.displayCardAnswer();

	 }
	    private void setFullScreen(boolean fullScreen) {
	        WindowManager.LayoutParams attrs = getWindow().getAttributes();
	        if (fullScreen) {
	            attrs.flags |= WindowManager.LayoutParams.FLAG_FULLSCREEN;
	        } else {
	            attrs.flags &= (~WindowManager.LayoutParams.FLAG_FULLSCREEN);
	        }
	        getWindow().setAttributes(attrs);
	    }


	    private boolean mGesturesEnabled;
	    /**
	     * Gesture Allocation
	     */
		private int mGestureSwipeUp;
		private int mGestureSwipeDown;
		private int mGestureSwipeLeft;
		private int mGestureSwipeRight;
		private int mGestureShake;
		private int mGestureDoubleTap;
		private int mGestureTapLeft;
		private int mGestureTapRight;
		private int mGestureTapTop;
		private int mGestureTapBottom;
		private boolean mIsSelecting = false;
		private GestureDetector gestureDetector;
		private boolean mPrefTextSelection;
		private boolean mLongClickWorkaround;
		private boolean mTouchStarted = false;
		private int mGestureLongclick;

		 private final Handler longClickHandler = new Handler();


	    class MyGestureDetector extends SimpleOnGestureListener {

	        @Override
	        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {

	            if (mGesturesEnabled) {
	                try {
	                    if (e2.getY() - e1.getY() > AnkiDroidApp.sSwipeMinDistance
	                            && Math.abs(velocityY) > AnkiDroidApp.sSwipeThresholdVelocity
	                            && Math.abs(e1.getX() - e2.getX()) < AnkiDroidApp.sSwipeMaxOffPath && !mIsYScrolling) {
	                        // down
	                        executeCommand(mGestureSwipeDown);
	                    } else if (e1.getY() - e2.getY() > AnkiDroidApp.sSwipeMinDistance
	                            && Math.abs(velocityY) > AnkiDroidApp.sSwipeThresholdVelocity
	                            && Math.abs(e1.getX() - e2.getX()) < AnkiDroidApp.sSwipeMaxOffPath && !mIsYScrolling) {
	                        // up
	                        executeCommand(mGestureSwipeUp);
	                    } else if (e2.getX() - e1.getX() > AnkiDroidApp.sSwipeMinDistance
	                            && Math.abs(velocityX) > AnkiDroidApp.sSwipeThresholdVelocity
	                            && Math.abs(e1.getY() - e2.getY()) < AnkiDroidApp.sSwipeMaxOffPath && !mIsXScrolling
	                            && !mIsSelecting) {
	                        // right
	                        executeCommand(mGestureSwipeRight);
	                    } else if (e1.getX() - e2.getX() > AnkiDroidApp.sSwipeMinDistance
	                            && Math.abs(velocityX) > AnkiDroidApp.sSwipeThresholdVelocity
	                            && Math.abs(e1.getY() - e2.getY()) < AnkiDroidApp.sSwipeMaxOffPath && !mIsXScrolling
	                            && !mIsSelecting) {
	                        // left
	                        executeCommand(mGestureSwipeLeft);
	                    }
	                } catch (Exception e) {
	                    Log.e(AnkiDroidApp.TAG, "onFling Exception = " + e.getMessage());
	                }
	            }
	            return false;
	        }


	        @Override
	        public boolean onDoubleTap(MotionEvent e) {
	            if (mGesturesEnabled) {
	                executeCommand(mGestureDoubleTap);
	            }
	            return true;
	        }


	        @Override
	        public boolean onSingleTapUp(MotionEvent e) {
	            if (mTouchStarted) {
	                longClickHandler.removeCallbacks(longClickTestRunnable);
	                mTouchStarted = false;
	            }
	            return false;
	        }


	        @Override
	        public boolean onSingleTapConfirmed(MotionEvent e) {
	            if (mGesturesEnabled && !mIsSelecting) {
	                int height = mTouchLayer.getHeight();
	                int width = mTouchLayer.getWidth();
	                float posX = e.getX();
	                float posY = e.getY();
	                if (posX > posY / height * width) {
	                    if (posY > height * (1 - posX / width)) {
	                        executeCommand(mGestureTapRight);
	                    } else {
	                        executeCommand(mGestureTapTop);
	                    }
	                } else {
	                    if (posY > height * (1 - posX / width)) {
	                        executeCommand(mGestureTapBottom);
	                    } else {
	                        executeCommand(mGestureTapLeft);
	                    }
	                }
	            }
	            mIsSelecting = false;
	           // showLookupButtonIfNeeded();
	            return false;
	        }
	    }

	  private void initLayout(Integer layout) {
	        setContentView(layout);

	        mMainLayout = findViewById(R.id.main_layout);
	        Themes.setContentStyle(mMainLayout, Themes.CALLER_REVIEWER);

	        mCardContainer = (FrameLayout) findViewById(R.id.flashcard_frame);
	        //setInAnimation(false);



	        mCardFrame = (FrameLayout) findViewById(R.id.flashcard);
	        mTouchLayer = (FrameLayout) findViewById(R.id.touch_layer);
	        mTouchLayer.setOnTouchListener(mGestureListener);
	        gestureDetector = new GestureDetector(new MyGestureDetector());

	        mCardFrame.removeAllViews();
	        mCardFrame.setVisibility(View.GONE);
	        mTouchLayer.setVisibility(View.GONE);
	        if (!mChangeBorderStyle) {
	            ((View) findViewById(R.id.flashcard_border)).setVisibility(View.VISIBLE);
	        }
	        // hunt for input issue 720, like android issue 3341
	        if (AnkiDroidApp.SDK_VERSION == 7 && (mCard != null)) {
	            mCard.setFocusableInTouchMode(true);
	        }

	        Resources res = getResources();
	        mEase1Layout = (LinearLayout) findViewById(R.id.flashcard_layout_ease1);
	        mEase2Layout = (LinearLayout) findViewById(R.id.flashcard_layout_ease2);

	        mEase3Layout = (LinearLayout) findViewById(R.id.flashcard_layout_ease3);
	        mEase4Layout = (LinearLayout) findViewById(R.id.flashcard_layout_ease4);
	        mNext1 = (TextView) findViewById(R.id.nextTime1);
	        mNext2 = (TextView) findViewById(R.id.nextTime2);
	        mNext3 = (TextView) findViewById(R.id.nextTime3);
	        mNext4 = (TextView) findViewById(R.id.nextTime4);

	        mNext1.setTextColor(res.getColor(R.color.next_time_failed_color));
	        mNext2.setTextColor(res.getColor(R.color.next_time_usual_color));

	        if (!mshowNextReviewTime) {
	            ((TextView) findViewById(R.id.nextTimeflip)).setVisibility(View.GONE);
	            mNext1.setVisibility(View.GONE);
	            mNext2.setVisibility(View.GONE);
	            mNext3.setVisibility(View.GONE);
	            mNext4.setVisibility(View.GONE);
	        }

	         	mFlipCard = (Button) findViewById(R.id.flip_card);
	    		mFlipCardLayout = (LinearLayout) findViewById(R.id.flashcard_layout_flip);
	    		mFlipCardLayout.setOnClickListener(mFlipCardListener);

	    		mFlipCard.setVisibility(View.GONE);
	    		mFlipCardLayout.setVisibility(View.GONE);

	        mChosenAnswer = (TextView) findViewById(R.id.choosen_answer);

	        if (mPrefWhiteboard) {
	            mWhiteboard = new Whiteboard(this, mInvertedColors, mBlackWhiteboard);
	            FrameLayout.LayoutParams lp2 = new FrameLayout.LayoutParams(LayoutParams.FILL_PARENT,
	                    LayoutParams.FILL_PARENT);
	            mWhiteboard.setLayoutParams(lp2);
	            FrameLayout fl = (FrameLayout) findViewById(R.id.whiteboard);
	            fl.addView(mWhiteboard);

	        }
	        mAnswerField = (EditText) findViewById(R.id.answer_field);

	        mNextTimeTextColor = getResources().getColor(R.color.next_time_usual_color);
	        mNextTimeTextRecomColor = getResources().getColor(R.color.next_time_recommended_color);
	        mForegroundColor = getResources().getColor(R.color.next_time_usual_color);

	        try{
	        	top_bar=(RelativeLayout)findViewById(R.id.top_bar);
	        	top_bar.setVisibility(View.GONE);
	        }
	        catch(Exception er)
	        {
	        	er.printStackTrace();
	        }
	        initControls();


	    }


	  private final Runnable longClickTestRunnable = new Runnable() {
	        public void run() {
	            // Log.i(AnkiDroidApp.TAG, "onEmulatedLongClick");
	            Vibrator vibratorManager = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
	            vibratorManager.vibrate(50);
	            longClickHandler.postDelayed(startLongClickAction, 300);
	        }
	    };
	    private final Runnable startLongClickAction = new Runnable() {
	        public void run() {
	            executeCommand(mGestureLongclick);
	        }
	    };


	 private void executeCommand(int which) {

	    }





	  private View.OnTouchListener mGestureListener = new View.OnTouchListener() {
	        @Override
	        public boolean onTouch(View v, MotionEvent event) {
	            if (gestureDetector.onTouchEvent(event)) {
	                return true;
	            }
	            mPrefTextSelection= AnkiDroidApp.getSharedPrefs(getBaseContext()).getBoolean("textSelection", true);
	            mLongClickWorkaround = AnkiDroidApp.getSharedPrefs(getBaseContext()).getBoolean("textSelectionLongclickWorkaround", false);
	            if (mPrefTextSelection && !mLongClickWorkaround) {
	                switch (event.getAction()) {
	                    case MotionEvent.ACTION_DOWN:
	                        mTouchStarted = true;
	                        longClickHandler.postDelayed(longClickTestRunnable, 800);
	                        break;
	                    case MotionEvent.ACTION_UP:
	                    case MotionEvent.ACTION_MOVE:
	                        if (mTouchStarted) {
	                            longClickHandler.removeCallbacks(longClickTestRunnable);
	                            mTouchStarted = false;
	                        }
	                        break;
	                }
	            }
	            try {
	                if (event != null) {
	                    if (mCurrentSimpleInterface) {
	                        mSimpleCard.dispatchTouchEvent(event);
	                    } else {
	                        mCard.dispatchTouchEvent(event);
	                    }
	                }
	            } catch (NullPointerException e) {
	                Log.e(AnkiDroidApp.TAG, "Error on dispatching touch event: " + e);
	                if (mInputWorkaround) {
	                    Log.e(AnkiDroidApp.TAG, "Error on using InputWorkaround: " + e + " --> disabled");
	                    AnkiDroidApp.getSharedPrefs(getBaseContext()).edit().putBoolean("inputWorkaround", false).commit();
	                    PreviewClass.this.finishWithoutAnimation();
	                }
	            }
	            return false;
	        }
	    };
	    public void finishWithoutAnimation() {
	        super.finish();
	        disableActivityAnimation();
	    }

	    private void disableActivityAnimation() {
	        ActivityTransitionAnimation.slide(this, ActivityTransitionAnimation.NONE);
	    }


	    // Handler for the "show answer" button
	    private View.OnClickListener mFlipCardListener = new View.OnClickListener() {
	        @Override
	        public void onClick(View view) {
	            // Log.i(AnkiDroidApp.TAG, "Flip card changed:");
	            displayCardAnswer();
	        }
	    };


	    private String typeAnsAnswerFilter(String buf) {
	        Matcher m = sTypeAnsPat.matcher(buf);
	        return m.replaceFirst("");
	    }

	    private void displayCardAnswer() {
	        // Log.i(AnkiDroidApp.TAG, "displayCardAnswer");

	        // prevent answering (by e.g. gestures) before card is loaded
	        if (mCurrentCard == null) {
	            return;
	        }
	        sDisplayAnswer = true;

	        String answer = mCurrentCard.getAnswer(mCurrentSimpleInterface);
	        answer = typeAnsAnswerFilter(answer);

	        String displayString = "";

	        if (mCurrentSimpleInterface) {
	            mCardContent = convertToSimple(answer);
	            if (mCardContent.length() == 0) {
	                SpannableString hint = new SpannableString(getResources().getString(R.string.simple_interface_hint,
	                        R.string.card_details_answer));
	                hint.setSpan(new StyleSpan(Typeface.ITALIC), 0, mCardContent.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
	                mCardContent = hint;
	            }
	        } else {
	            Sound.stopSounds();

	            if (mPrefFixArabic) {
	                // reshape
	                answer = ArabicUtilities.reshapeSentence(answer, true);
	            }

	            // If the user wrote an answer
	            if (typeAnswer()) {
	                mAnswerField.setVisibility(View.GONE);
	                if (mCurrentCard != null) {
	                    if (mPrefFixArabic) {
	                        // reshape
	                        mTypeCorrect = ArabicUtilities.reshapeSentence(mTypeCorrect, true);
	                    }
	                    // Obtain the user answer and the correct answer
	                    String userAnswer = mAnswerField.getText().toString();
	                    Matcher matcher = sSpanPattern.matcher(Utils.stripHTMLMedia(mTypeCorrect));
	                    String correctAnswer = matcher.replaceAll("");
	                    matcher = sBrPattern.matcher(correctAnswer);
	                    correctAnswer = matcher.replaceAll("\n");
	                    matcher = Sound.sSoundPattern.matcher(correctAnswer);
	                    correctAnswer = matcher.replaceAll("");
	                    // Log.i(AnkiDroidApp.TAG, "correct answer = " + correctAnswer);

	                    // Obtain the diff and send it to updateCard
	                    DiffEngine diff = new DiffEngine();

	                    StringBuffer span = new StringBuffer();
	                    span.append("<span style=\"font-family: '").append(mTypeFont)
	                    .append("'; font-size: ").append(12).append("px\">");
	                    span.append(diff.diff_prettyHtml(diff.diff_main(userAnswer, correctAnswer), mNightMode));
	                    span.append("</span>");
	                    span.append("<br/>").append(answer);
	                    displayString = enrichWithQADiv(span.toString(), true);
	                }

	                // Hide soft keyboard
	                InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
	                inputMethodManager.hideSoftInputFromWindow(mAnswerField.getWindowToken(), 0);
	            } else {
	                displayString = enrichWithQADiv(answer, true);
	            }
	        }

	        updateCard(displayString);

	    }


	    private void initControls() {
	        mCardFrame.setVisibility(View.VISIBLE);
	        //mFlipCardLayout.setVisibility(View.VISIBLE);
	        mChosenAnswer.setVisibility(View.VISIBLE);

	        if (mPrefWhiteboard) {
	            mWhiteboard.setVisibility(mShowWhiteboard ? View.VISIBLE : View.GONE);
	        }
	        mAnswerField.setVisibility(typeAnswer() ? View.VISIBLE : View.GONE);
	    }

	    private String typeAnsQuestionFilter(String buf) {
	        Matcher m = sTypeAnsPat.matcher(buf);
	        if (mTypeWarning != null) {
	            return m.replaceFirst(mTypeWarning);
	        }
	        return m.replaceFirst("");
	    }

	    private boolean mSimpleInterface = false;
	    private ArrayList<String> mSimpleInterfaceExcludeTags;
	    private Method mSetTextIsSelectable = null;

	    private void setInterface() {
	    	if (mCurrentCard == null) {
	    		return;
	    	}
	    	if (mSimpleInterface) {
	        	Note note = mCurrentCard.note();
	        	mCurrentSimpleInterface = true;
	        	for (String s : mSimpleInterfaceExcludeTags) {
	        		if (note.hasTag(s)) {
	        			mCurrentSimpleInterface = false;
	        			break;
	        		}
	        	}
	    	}
			if (mCurrentSimpleInterface) {
				if (mSimpleCard == null) {
		            mSimpleCard = new ScrollTextView(this);
		            Themes.setRegularFont(mSimpleCard);
		            mSimpleCard.setTextSize(mSimpleCard.getTextSize() * mDisplayFontSize / 100);
		            mSimpleCard.setGravity(Gravity.CENTER);
		            try {
		                mSetTextIsSelectable = TextView.class.getMethod("setTextIsSelectable", boolean.class);
		            } catch (Throwable e) {
		                // Log.i(AnkiDroidApp.TAG, "mSetTextIsSelectable could not be found due to a too low Android version (< 3.0)");
		                mSetTextIsSelectable = null;
		            }
		            if (mSetTextIsSelectable != null) {
		                try {
		                    mSetTextIsSelectable.invoke(mSimpleCard, true);
		                } catch (Exception e) {
		                    Log.e(AnkiDroidApp.TAG, e.toString());
		                }
		            }
		            mSimpleCard.setClickable(true);
		            mCardFrame.addView(mSimpleCard);

		            mSimpleCard.setBackgroundColor(mCurrentBackgroundColor);
		            mSimpleCard.setTextColor(mForegroundColor);
				}
				if (mSimpleCard.getVisibility() != View.VISIBLE || (mCard != null && mCard.getVisibility() == View .VISIBLE)) {
					mSimpleCard.setVisibility(View.VISIBLE);
					mCard.setVisibility(View.GONE);
				}
			} else {
				if (mCard == null) {
		            mCard = createWebView();
		            mCardFrame.addView(mCard);
		            if (mRefreshWebview) {
		                mNextCard = createWebView();
		                mNextCard.setVisibility(View.GONE);
		                mCardFrame.addView(mNextCard, 0);
			            mCard.setBackgroundColor(mCurrentBackgroundColor);

		                mCustomFontStyle = getCustomFontsStyle() + getDefaultFontStyle();
		            }
				}
				if (mCard.getVisibility() != View.VISIBLE || (mSimpleCard != null && mSimpleCard.getVisibility() == View .VISIBLE)) {
					mSimpleCard.setVisibility(View.GONE);
					mCard.setVisibility(View.VISIBLE);
				}
			}
	    }
	    private String getCustomFontsStyle() {
	        StringBuilder builder = new StringBuilder();
	        for (AnkiFont font : mCustomFonts.values()) {
	            builder.append(font.getDeclaration());
	            builder.append('\n');
	        }
	        return builder.toString();
	    }
	    private String getDefaultFontStyle() {
	        if (mCustomDefaultFontCss == null) {
	            SharedPreferences preferences = AnkiDroidApp.getSharedPrefs(getBaseContext());
	            String defaultFont = preferences.getString("defaultFont", null);
	            if (defaultFont != null && !"".equals(defaultFont) && mCustomFonts.containsKey(defaultFont)) {
	                mCustomDefaultFontCss = "BODY .question, BODY .answer { " + mCustomFonts.get(defaultFont).getCSS() + " }\n";
	            } else {
	                defaultFont = Themes.getReviewerFontName();
	                if (defaultFont == null || "".equals(defaultFont)) {
	                    mCustomDefaultFontCss = "";
	                } else {
	                    mCustomDefaultFontCss = "BODY .question BODY .answer { font-family: '" + defaultFont +
	                            "' font-weight: normal; font-style: normal; font-stretch: normal; }\n";
	                }
	            }
	        }
	        return mCustomDefaultFontCss;
	    }


	    private void displayCardQuestion() {
	    	// show timer, if activated in the deck's preferences

	        sDisplayAnswer = false;

	        setInterface();
	        String question = mCurrentCard.getQuestion(mCurrentSimpleInterface);
	        question = typeAnsQuestionFilter(question);

	        if (mPrefFixArabic) {
	            question = ArabicUtilities.reshapeSentence(question, true);
	        }

	        // Log.i(AnkiDroidApp.TAG, "question: '" + question + "'");

	        String displayString = "";

	        if (mCurrentSimpleInterface) {
	            mCardContent = convertToSimple(question);
	            if (mCardContent.length() == 0) {
	                SpannableString hint = new SpannableString(getResources().getString(R.string.simple_interface_hint,
	                        R.string.card_details_question));
	                hint.setSpan(new StyleSpan(Typeface.ITALIC), 0, mCardContent.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
	                mCardContent = hint;
	            }
	        } else {
	            // If the user wants to write the answer
	            if (typeAnswer()) {
	                mAnswerField.setVisibility(View.VISIBLE);

	                // Show soft keyboard
	                InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
	                inputMethodManager.showSoftInput(mAnswerField, InputMethodManager.SHOW_FORCED);
	            }

	            displayString = enrichWithQADiv(question, false);

	            if (mSpeakText) {
	                // ReadText.setLanguageInformation(Model.getModel(DeckManager.getMainDeck(),
	                // mCurrentCard.getCardModelId(), false).getId(), mCurrentCard.getCardModelId());
	            }
	        }

	        updateCard(displayString);



	    }
	    private boolean mShowAnimations = false;

	    private String recalculateHardCodedFontSize(String content, int percentage) {
	        if (percentage == 100 || null == content || 0 == content.trim().length()) {
	            return content.trim();
	        }
	        StringBuffer sb = new StringBuffer();
	        int tagDepth = 0; // to find out whether a relative CSS unit measure is within another one
	        int lastRelUnitnTagDepth = 100; // the hierarchy depth of the current outer relative span
	        double doubleSize; // for relative css measurement values

	        int lastMatch = 0;
	        String contentPart;
	        Matcher m2;
	        Matcher m = fFontSizePattern.matcher(content);
	        while (m.find()) {
	            contentPart = content.substring(lastMatch, m.start());
	            m2 = fSpanDivPattern.matcher(contentPart);
	            while (m2.find()) {
	                if (m2.group(1).equals("/")) {
	                    --tagDepth;
	                } else {
	                    ++tagDepth;
	                }
	                if (tagDepth < lastRelUnitnTagDepth) {
	                    // went outside of previous scope
	                    lastRelUnitnTagDepth = 100;
	                }
	            }
	            lastMatch = m.end();

	            try {
	                doubleSize = Double.parseDouble(m.group(1));
	                doubleSize = doubleSize * percentage / 100;
	            } catch (NumberFormatException e) {
	                continue; // ignore this one
	            }

	            if (fRelativeCssUnits.contains(m.group(2))) {
	                // handle relative units
	                if (lastRelUnitnTagDepth < tagDepth) {
	                    m.appendReplacement(sb, m.group());
	                    continue;
	                }
	                lastRelUnitnTagDepth = tagDepth;
	            }
	            m.appendReplacement(sb, String.format(Locale.US, "font-size:%.2f%s;", doubleSize, m.group(2)));
	        }
	        m.appendTail(sb);
	        String a = sb.toString();
	        return a;
	    }

	    private static int calculateDynamicFontSize(String htmlContent) {
	        // Replace each <br> with 15 spaces, each <hr> with 30 spaces, then
	        // remove all html tags and spaces
	        String realContent = htmlContent.replaceAll("\\<br.*?\\>", " ");
	        realContent = realContent.replaceAll("\\<hr.*?\\>", " ");
	        realContent = realContent.replaceAll("\\<.*?\\>", "");
	        realContent = realContent.replaceAll("&nbsp;", " ");
	        return Math.max(DYNAMIC_FONT_MIN_SIZE, DYNAMIC_FONT_MAX_SIZE
	                - (int) (realContent.length() / DYNAMIC_FONT_FACTOR));
	    }



	    private void updateCard(String content) {
	        // Log.i(AnkiDroidApp.TAG, "updateCard");

	      //  Lookup.initialize(this, mCurrentCard.getDid());

	        if (mCurrentSimpleInterface) {
	            fillFlashcard(mShowAnimations);
	        } else {

	            // Check whether there is a hard coded font-size in the content and apply the relative font size
	            // Check needs to be done before CSS is applied to content;
	            content = recalculateHardCodedFontSize(content, mDisplayFontSize);

	            // Add CSS for font color and font size
	            if (mCurrentCard == null) {
	                mCard.getSettings().setDefaultFontSize(calculateDynamicFontSize(content));
	            }

	            String question = "";
	            String answer = "";
	            int qa = -1; // prevent uninitialized variable errors

	            if (sDisplayAnswer) {
	                qa = MetaDB.LANGUAGES_QA_ANSWER;
	                answer = mCurrentCard.getPureAnswerForReading();
	                if (!mAnswerSoundsAdded) {
	                    Sound.addSounds(mBaseUrl, answer, qa);
	                    mAnswerSoundsAdded = true;
	                }
	            } else {
	                Sound.resetSounds(); // reset sounds on first side of card
	                mAnswerSoundsAdded = false;
	                qa = MetaDB.LANGUAGES_QA_QUESTION;
	                question = mCurrentCard.getQuestion(mCurrentSimpleInterface);
	                Sound.addSounds(mBaseUrl, question, qa);
	            }

	            content = Sound.expandSounds(mBaseUrl, content, mSpeakText, qa);

	            // In order to display the bold style correctly, we have to change
	            // font-weight to 700
	            content = content.replace("font-weight:600;", "font-weight:700;");

	            // Log.i(AnkiDroidApp.TAG, "content card = \n" + content);
	            StringBuilder style = new StringBuilder();
	            style.append(mCustomFontStyle);
	            // Log.i(AnkiDroidApp.TAG, "::style::" + style);

	            if (mNightMode) {
	                content = HtmlColors.invertColors(content);
	            }

	            content = SmpToHtmlEntity(content);
	            mCardContent = new SpannedString(mCardTemplate.replace("::content::", content).replace("::style::",
	                    style.toString()));
	            // Log.i(AnkiDroidApp.TAG, "base url = " + mBaseUrl);

	            fillFlashcard(mShowAnimations);
	        }


		}
	    private String SmpToHtmlEntity(String text) {
	        StringBuffer sb = new StringBuffer();
	        Matcher m = Pattern.compile("([^\u0000-\uFFFF])").matcher(text);
	        while (m.find()) {
	            String a = "&#x" + Integer.toHexString(m.group(1).codePointAt(0)) + ";";
	            m.appendReplacement(sb, a);
	        }
	        m.appendTail(sb);
	        return sb.toString();
	    }
	    public boolean getRefreshWebviewAndInitializeWebviewVariables() {
	        List<AnkiFont> fonts = Utils.getCustomFonts(getBaseContext());
	        mCustomFonts = new HashMap<String, AnkiFont>();
	        for (AnkiFont f : fonts) {
	            mCustomFonts.put(f.getName(), f);
	        }
	        for (String s : new String[] { "nook" }) {
	            if (android.os.Build.DEVICE.toLowerCase().indexOf(s) != -1
	                    || android.os.Build.MODEL.toLowerCase().indexOf(s) != -1) {
	                return true;
	            }
	        }
	        if (mCustomFonts.size() != 0) {
	            return true;
	        }
	        return false;
	    }


	    public void fillFlashcard(boolean flip) {

	        if (!flip) {
	        	Display display = getWindowManager().getDefaultDisplay();
	        	Integer width= display.getWidth();
	        	Integer height= display.getHeight()/2;
	        	String file_contents= mCardContent.toString();
	        	file_contents=file_contents.replace("newImg.width", width.toString());
	        	file_contents=file_contents.replace("newImg.height", height.toString());
	        	file_contents=file_contents.replace("<head>","<head><meta name='viewport' content='width=device-width,initial-scale=1,minimum-scale=1,user-scalable=yes'/>");

	            // Log.i(AnkiDroidApp.TAG, "base url = " + mBaseUrl);
	            if (mCurrentSimpleInterface && mSimpleCard != null) {
	                mSimpleCard.setText(mCardContent);
	            } else if (mRefreshWebview && mCard != null && mNextCard != null) {
	                mNextCard.setBackgroundColor(mCurrentBackgroundColor);
	                //newImg.width

	                mNextCard.loadDataWithBaseURL(mBaseUrl, file_contents, "text/html", "utf-8", null);
	                mNextCard.setVisibility(View.VISIBLE);
	                mCardFrame.removeView(mCard);
	                mCard.destroy();
	                mCard = mNextCard;
	                mNextCard = createWebView();
	                mNextCard.setVisibility(View.GONE);
	                mCardFrame.addView(mNextCard, 0);
	                // hunt for input issue 720, like android issue 3341
	                if (AnkiDroidApp.SDK_VERSION == 7) {
	                    mCard.setFocusableInTouchMode(true);
	                }
	            } else if (mCard != null) {
	                mCard.loadDataWithBaseURL(mBaseUrl, file_contents, "text/html", "utf-8", null);
	                mCard.setBackgroundColor(mCurrentBackgroundColor);
	            }
	            if (mChangeBorderStyle) {}

	        } else {
	            Animation3D rotation;
	            boolean directionToLeft = true;



	            mCardContainer.setDrawingCacheEnabled(true);
	            mCardContainer.setDrawingCacheBackgroundColor(Themes.getBackgroundColor());
	            mCardContainer.clearAnimation();
	            //mCardContainer.startAnimation(rotation);
	        }
	    }

	    private boolean mPrefFadeScrollbars;

	    private int mAvailableInCardWidth;

	    @SuppressLint("NewApi")
		private WebView createWebView() {

	        WebView webView = new MyWebView(this);
	        webView.setWillNotCacheDrawing(true);
	        webView.setScrollBarStyle(WebView.SCROLLBARS_OUTSIDE_OVERLAY);
	        SharedPreferences preferences = AnkiDroidApp.getSharedPrefs(getBaseContext());
	        mZoomEnabled = preferences.getBoolean("zoom", false);
	        if (true) { //mZoomEnabled
	        	//webView.getSettings().setLoadWithOverviewMode(true);
	        	//webView.getSettings().setUseWideViewPort(true);
	        	webView.getSettings().setDefaultZoom(WebSettings.ZoomDensity.FAR);
	            webView.setInitialScale(100);
	            webView.getSettings().setBuiltInZoomControls(true);
	            webView.getSettings().setSupportZoom(true);
	        }
	        webView.getSettings().setJavaScriptEnabled(true);
	        webView.setWebChromeClient(new AnkiDroidWebChromeClient());
	        webView.addJavascriptInterface(new JavaScriptInterface(this), "ankidroid");
	        if (AnkiDroidApp.SDK_VERSION > 7) {
	            webView.setFocusableInTouchMode(false);
	        }
	        AnkiDroidApp.getCompat().setScrollbarFadingEnabled(webView, mPrefFadeScrollbars);
	        // Log.i(AnkiDroidApp.TAG, "Focusable = " + webView.isFocusable() + ", Focusable in touch mode = " + webView.isFocusableInTouchMode());

	        return webView;
	    }
	    public final class JavaScriptInterface {
	        private PreviewClass mCtx;

	        JavaScriptInterface(PreviewClass ctx) {
	            mCtx = ctx;
	        }
	        @JavascriptInterface
	        public int getAvailableWidth() {
	            if (mCtx.mAvailableInCardWidth == 0) {
	                mCtx.mAvailableInCardWidth = mCtx.calcAvailableInCardWidth();
	            }
	            return mCtx.mAvailableInCardWidth;
	        }
	    }
	    public int calcAvailableInCardWidth() {
	        // The available width of the webview equals to the container's width, minus the container's padding
	        // divided by the default scale factor used by the WebView, and minus the WebView's padding
	        if (mCard != null && mCardFrame != null) {
	            return Math.round((mCardFrame.getWidth() - mCardFrame.getPaddingLeft() - mCardFrame.getPaddingRight()
	                    - mCard.getPaddingLeft() - mCard.getPaddingRight()) / mCard.getScale());
	        }
	        return 0;
	    }

	    private static String enrichWithQADiv(String content, boolean isAnswer) {
	        StringBuffer sb = new StringBuffer();
	        sb.append("<div class=\"");
	        if (isAnswer) {
	            sb.append(ANSWER_CLASS);
	        } else {
	            sb.append(QUESTION_CLASS);
	        }
	        sb.append("\">");
	        sb.append(content);
	        sb.append("</div>");
	        return sb.toString();
	    }
	    private Html.ImageGetter mSimpleInterfaceImagegetter = new Html.ImageGetter () {

	        public Drawable getDrawable(String source) {
	            String path = AnkiDroidApp.getCurrentAnkiDroidDirectory() + "/collection.media/" + source;
	            if ((new File(path)).exists()) {
	                Drawable d = Drawable.createFromPath(path);
	                d.setBounds(0,0,d.getIntrinsicWidth(),d.getIntrinsicHeight());
	                return d;
	            } else {
	            	return null;
	            }
	        }
	    };

	    private TagHandler mSimpleInterfaceTagHandler = new TagHandler () {

	        public void handleTag(boolean opening, String tag, Editable output,
	                XMLReader xmlReader) {
//	            if(tag.equalsIgnoreCase("div")) {
//	            	output.append("\n");
//	            } else
	        	if(tag.equalsIgnoreCase("strike") || tag.equals("s")) {
	                int len = output.length();
	                if(opening) {
	                    output.setSpan(new StrikethroughSpan(), len, len, Spannable.SPAN_MARK_MARK);
	                } else {
	                    Object obj = getLast(output, StrikethroughSpan.class);
	                    int where = output.getSpanStart(obj);

	                    output.removeSpan(obj);

	                    if (where != len) {
	                        output.setSpan(new StrikethroughSpan(), where, len, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
	                    }
	                }
	            }
	        }

	        private Object getLast(Editable text, Class kind) {
	            Object[] objs = text.getSpans(0, text.length(), kind);

	            if (objs.length == 0) {
	                return null;
	            } else {
	                for(int i = objs.length;i>0;i--) {
	                    if(text.getSpanFlags(objs[i-1]) == Spannable.SPAN_MARK_MARK) {
	                        return objs[i-1];
	                    }
	                }
	                return null;
	            }
	        }
	    };
	    private Spanned convertToSimple(String text) {
	    	text = text.replaceAll("</div>$", "").replaceAll("(</div>)*<div>", "<br>");
	    	return Html.fromHtml(text, mSimpleInterfaceImagegetter, mSimpleInterfaceTagHandler);
	    }

	    private final boolean typeAnswer() {
	        if (mPrefWriteAnswers && null != mTypeCorrect) {
	            return true;
	        }
	        return false;
	    }

	    public final class AnkiDroidWebChromeClient extends WebChromeClient {
	        @Override
	        public boolean onJsAlert(WebView view, String url, String message, JsResult result) {
	            // Log.i(AnkiDroidApp.TAG, message);
	            result.confirm();
	            return true;
	        }
	    }

	    class ScrollTextView extends TextView {

			public ScrollTextView(Context context) {
				super(context);
			}

			@Override
			protected void onScrollChanged (int horiz, int vert, int oldHoriz, int oldVert) {
				super.onScrollChanged(horiz, vert, oldHoriz, oldVert);
				if (Math.abs(horiz - oldHoriz) > Math.abs(vert - oldVert)) {
		        	mIsXScrolling = true;
					scrollHandler.removeCallbacks(scrollXRunnable);
					scrollHandler.postDelayed(scrollXRunnable, 300);
				} else {
		        	mIsYScrolling = true;
					scrollHandler.removeCallbacks(scrollYRunnable);
					scrollHandler.postDelayed(scrollYRunnable, 300);
				}
			}

		    private final Handler scrollHandler = new Handler();
		    private final Runnable scrollXRunnable = new Runnable() {
		        public void run() {
		        	mIsXScrolling = false;
		        }
		    };
		    private final Runnable scrollYRunnable = new Runnable() {
		        public void run() {
		        	mIsYScrolling = false;
		        }
		    };

	    }

/** Fixing bug 720: <input> focus, thanks to pablomouzo on android issue 7189 */
class MyWebView extends WebView {

    public MyWebView(Context context) {
        super(context);
    }


    @Override
    public boolean onCheckIsTextEditor() {
        if (mInputWorkaround) {
            return true;
        } else {
            return super.onCheckIsTextEditor();
        }
    }

	@Override
	protected void onScrollChanged (int horiz, int vert, int oldHoriz, int oldVert) {
		super.onScrollChanged(horiz, vert, oldHoriz, oldVert);
		if (Math.abs(horiz - oldHoriz) > Math.abs(vert - oldVert)) {
        	mIsXScrolling = true;
			scrollHandler.removeCallbacks(scrollXRunnable);
			scrollHandler.postDelayed(scrollXRunnable, 300);
		} else {
        	mIsYScrolling = true;
			scrollHandler.removeCallbacks(scrollYRunnable);
			scrollHandler.postDelayed(scrollYRunnable, 300);
		}
	}

    private final Handler scrollHandler = new Handler();
    private final Runnable scrollXRunnable = new Runnable() {
        public void run() {
        	mIsXScrolling = false;
        }
    };
    private final Runnable scrollYRunnable = new Runnable() {
        public void run() {
        	mIsYScrolling = false;
        }
    };

}
}
