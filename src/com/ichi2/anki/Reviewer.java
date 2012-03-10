/***************************************************************************************
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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.ParseException;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Typeface;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.os.Vibrator;
import android.text.ClipboardManager;
import android.text.Html;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.SpannedString;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;
import android.util.Log;
import android.view.GestureDetector;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.SubMenu;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.inputmethod.InputMethodManager;
import android.webkit.JsResult;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.Chronometer;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.FrameLayout.LayoutParams;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.ichi2.anim.ActivityTransitionAnimation;
import com.ichi2.anim.Animation3D;
import com.ichi2.anim.ViewAnimation;
import com.ichi2.compat.Compat;
import com.ichi2.themes.StyledDialog;
import com.ichi2.themes.StyledProgressDialog;
import com.ichi2.themes.Themes;
import com.ichi2.utils.DiffEngine;
import com.ichi2.utils.RubyParser;
import com.ichi2.widget.AnkiDroidWidgetBig;
import com.ichi2.widget.WidgetStatus;
import com.tomgibara.android.veecheck.util.PrefSettings;
import com.zeemote.zc.event.ButtonEvent;
import com.zeemote.zc.event.IButtonListener;
import com.zeemote.zc.util.JoystickToButtonAdapter;

import org.amr.arabic.ArabicUtilities;

public class Reviewer extends AnkiActivity implements IButtonListener{
    /**
     * Result codes that are returned when this activity finishes.
     */
    public static final int RESULT_DEFAULT = 50;
    public static final int RESULT_SESSION_COMPLETED = 51;
    public static final int RESULT_NO_MORE_CARDS = 52;
    public static final int RESULT_EDIT_CARD_RESET = 53;
    public static final int RESULT_ANSWERING_ERROR = 54;
    public static final int RESULT_DECK_CLOSED = 55;

    /**
     * Possible values for update card handler
     */
    public static final int UPDATE_CARD_NEW_CARD = 0;
    public static final int UPDATE_CARD_SHOW_QUESTION = 1;
    public static final int UPDATE_CARD_SHOW_ANSWER = 2;
    
    /**
     * Available options performed by other activities.
     */
    public static final int EDIT_CURRENT_CARD = 0;

    /** Constant for class attribute signaling answer */
    static final String ANSWER_CLASS = "answer";

    /** Constant for class attribute signaling question */
    static final String QUESTION_CLASS = "question";

    /** Max size of the font for dynamic calculation of font size */
    private static final int DYNAMIC_FONT_MAX_SIZE = 14;

    /** Min size of the font for dynamic calculation of font size */
    private static final int DYNAMIC_FONT_MIN_SIZE = 3;
    private static final int DYNAMIC_FONT_FACTOR = 5;

    private static final int TOTAL_WIDTH_PADDING = 10;

    /**
     * Menus
     */
    private static final int MENU_WHITEBOARD = 0;
    private static final int MENU_CLEAR_WHITEBOARD = 1;
    private static final int MENU_EDIT = 2;
    private static final int MENU_REMOVE = 3;
    private static final int MENU_REMOVE_BURY = 31;
    private static final int MENU_REMOVE_SUSPEND = 32;
    private static final int MENU_REMOVE_DELETE = 33;
    private static final int MENU_SEARCH = 4;
    private static final int MENU_MARK = 5;
    private static final int MENU_UNDO = 6;
    private static final int MENU_REDO = 7;

    /** Zeemote messages */
    private static final int MSG_ZEEMOTE_BUTTON_A = 0x110;
    private static final int MSG_ZEEMOTE_BUTTON_B = MSG_ZEEMOTE_BUTTON_A+1;
    private static final int MSG_ZEEMOTE_BUTTON_C = MSG_ZEEMOTE_BUTTON_A+2;
    private static final int MSG_ZEEMOTE_BUTTON_D = MSG_ZEEMOTE_BUTTON_A+3;
    private static final int MSG_ZEEMOTE_STICK_UP = MSG_ZEEMOTE_BUTTON_A+4;
    private static final int MSG_ZEEMOTE_STICK_DOWN = MSG_ZEEMOTE_BUTTON_A+5;
    private static final int MSG_ZEEMOTE_STICK_LEFT = MSG_ZEEMOTE_BUTTON_A+6;
    private static final int MSG_ZEEMOTE_STICK_RIGHT = MSG_ZEEMOTE_BUTTON_A+7;
    
    /** Regex pattern used in removing tags from text before diff */
    private static final Pattern sSpanPattern = Pattern.compile("</?span[^>]*>");
    private static final Pattern sBrPattern = Pattern.compile("<br\\s?/?>");

    /** Regex patterns used in identifying and fixing Hebrew words, so we can reverse them */
    private static final Pattern sHebrewPattern = Pattern.compile(
            // Two cases caught below:
            // Either a series of characters, starting from a hebrew character...
            "([[\\u0591-\\u05F4][\\uFB1D-\\uFB4F]]" +
            // ...followed by hebrew characters, punctuation, parenthesis, spaces, numbers or numerical symbols...
            "[[\\u0591-\\u05F4][\\uFB1D-\\uFB4F],.?!;:\"'\\[\\](){}+\\-*/%=0-9\\s]*" +
            // ...and ending with hebrew character, punctuation or numerical symbol
            "[[\\u0591-\\u05F4][\\uFB1D-\\uFB4F],.?!;:0-9%])|" +
            // or just a single Hebrew character
            "([[\\u0591-\\u05F4][\\uFB1D-\\uFB4F]])");
    private static final Pattern sHebrewVowelsPattern = Pattern.compile(
            "[[\\u0591-\\u05BD][\\u05BF\\u05C1\\u05C2\\u05C4\\u05C5\\u05C7]]");
    // private static final Pattern sBracketsPattern = Pattern.compile("[()\\[\\]{}]");
    // private static final Pattern sNumeralsPattern = Pattern.compile("[0-9][0-9%]+");
    private static final Pattern sFenPattern = Pattern.compile("\\[fen ?([^\\]]*)\\]([^\\[]+)\\[/fen\\]");
    private static final Pattern sFenOrientationPattern = Pattern.compile("orientation *= *\"?(black|white)\"?");

    /** Hide Question In Answer choices */
    private static final int HQIA_DO_HIDE = 0;
    private static final int HQIA_DO_SHOW = 1;
    private static final int HQIA_CARD_MODEL = 2;

    private static Card sEditorCard; // To be assigned as the currentCard or a new card to be sent to and from editor

    private static boolean sDisplayAnswer =  false; // Indicate if "show answer" button has been pressed

    /** The percentage of the absolute font size specified in the deck. */
    private int mDisplayFontSize = CardModel.DEFAULT_FONT_SIZE_RATIO;
    
    /** The absolute CSS measurement units inclusive semicolon for pattern search */
    private static final String[] ABSOLUTE_CSS_UNITS = {"px;", "pt;", "in;", "cm;", "mm;", "pc;"};
    
    /** The relative CSS measurement units inclusive semicolon for pattern search */
    private static final String[] RELATIVE_CSS_UNITS = {"%;", "em;"};

    /**
     * Broadcast that informs us when the sd card is about to be unmounted
     */
    private BroadcastReceiver mUnmountReceiver = null;

    private boolean mInBackground = false;

    /**
     * Variables to hold preferences
     */
    private boolean mPrefTimer;
    private boolean mPrefWhiteboard;
    private boolean mPrefWriteAnswers;
    private boolean mPrefTextSelection;
    private boolean mInputWorkaround;
    private boolean mLongClickWorkaround;
    private boolean mPrefFullscreenReview;
    private boolean mshowNextReviewTime;
    private boolean mZoomEnabled;    
    private boolean mPrefUseRubySupport; // Parse for ruby annotations
    private String mDeckFilename;
    private int mPrefHideQuestionInAnswer; // Hide the question when showing the answer
    private int mRelativeButtonSize;
    private boolean mDoubleScrolling;
    private boolean mScrollingButtons;
    private boolean mGesturesEnabled;
    private boolean mShakeEnabled = false;
    private int mShakeIntensity;
    private boolean mShakeActionStarted = false;
    private boolean mPrefFixHebrew; // Apply manual RTL for hebrew text - bug in Android WebView
    private boolean mPrefConvertFen;
    private boolean mPrefFixArabic;
    private boolean mSpeakText;
    private boolean mPlaySoundsAtStart;
    private boolean mInvertedColors = false;
    private int mCurrentBackgroundColor;
    private boolean mBlackWhiteboard = true;
    private boolean mSwapQA = false;
    private boolean mNightMode = false;
    private boolean mIsLastCard = false;
    private boolean mShowProgressBars;
    private boolean mPrefUseTimer;
    private boolean mShowAnimations = false;
    private boolean mSimpleInterface = false;
    private String mLocale;

    private boolean mIsSelecting = false;
    private boolean mTouchStarted = false;
    private boolean mIsAnswering = false;

//    @SuppressWarnings("unused")
//    private boolean mUpdateNotifications; // TODO use Veecheck only if this is true

    private String mCardTemplate;
    
    private String mMediaDir;

    private boolean mInEditor = false;
    
    /**
     * Variables to hold layout objects that we need to update or handle events for
     */
    private View mMainLayout;
    private View mLookUpIcon;
    private FrameLayout mCardContainer;
    private WebView mCard;
    private TextView mSimpleCard;
    private WebView mNextCard;
    private FrameLayout mCardFrame;
    private FrameLayout mTouchLayer;
    private TextView mTextBarRed;
    private TextView mTextBarBlack;
    private TextView mTextBarBlue;
    private TextView mChosenAnswer;
    private LinearLayout mProgressBars;
    private View mSessionYesBar;
    private View mSessionProgressBar;
    private TextView mNext1;
    private TextView mNext2;
    private TextView mNext3;
    private TextView mNext4; 
    private Button mFlipCard;
    private EditText mAnswerField;
    private Button mEase1;
    private Button mEase2;
    private Button mEase3;
    private Button mEase4;
    private LinearLayout mFlipCardLayout;
    private LinearLayout mEase1Layout;
    private LinearLayout mEase2Layout;
    private LinearLayout mEase3Layout;
    private LinearLayout mEase4Layout;
    private Chronometer mCardTimer;
    private Whiteboard mWhiteboard;
	private ClipboardManager mClipboard;
    private StyledProgressDialog mProgressDialog;

    private Card mCurrentCard;
    private int mCurrentEase;
    private long mSessionTimeLimit;
    private int mSessionCurrReps;
    private float mScaleInPercent;
    private boolean mShowWhiteboard = false;
    
    private int mNextTimeTextColor;
    private int mNextTimeTextRecomColor;
    private int mForegroundColor;
    private boolean mChangeBorderStyle;
    
    private int mButtonHeight = 0;
    
    private boolean mConfigurationChanged = false;
    private int mShowChosenAnswerLength = 2000;
    
	private boolean mShowCongrats = false;

    private int mStatisticBarsMax;
    private int mStatisticBarsHeight;

    private long mSavedTimer = 0;

    private boolean mRefreshWebview = false;
    private String[] mCustomFontFiles;
    private String mCustomDefaultFontCss;
    private String mCustomFontStyle;

	/** 
	 * Shake Detection
	 */
	private SensorManager mSensorManager;
	private float mAccel; // acceleration apart from gravity
	private float mAccelCurrent; // current acceleration including gravity
	private float mAccelLast; // last acceleration including gravity

	/**
     * Swipe Detection
     */    
 	private GestureDetector gestureDetector;
 	View.OnTouchListener gestureListener;

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
 	private int mGestureLongclick;

 	private static final int GESTURE_NOTHING = 0;
 	private static final int GESTURE_ANSWER_EASE1 = 1;
 	private static final int GESTURE_ANSWER_EASE2 = 2;
 	private static final int GESTURE_ANSWER_EASE3 = 3;
 	private static final int GESTURE_ANSWER_EASE4 = 4;
 	private static final int GESTURE_ANSWER_RECOMMENDED = 5;
 	private static final int GESTURE_ANSWER_BETTER_THAN_RECOMMENDED = 6;
 	private static final int GESTURE_UNDO = 7;
 	private static final int GESTURE_REDO = 8;
 	private static final int GESTURE_EDIT = 9;
 	private static final int GESTURE_MARK = 10;
 	private static final int GESTURE_LOOKUP = 11;
 	private static final int GESTURE_BURY= 12;
 	private static final int GESTURE_SUSPEND = 13;
 	private static final int GESTURE_DELETE = 14;
 	private static final int GESTURE_CLEAR_WHITEBOARD = 15;
 	private static final int GESTURE_EXIT = 16;

 	private Spanned mCardContent = new SpannedString("");
 	private String mBaseUrl = "";

 	private static final int ANIMATION_NO_ANIMATION = 0;
 	private static final int ANIMATION_TURN = 1;
 	private static final int ANIMATION_NEXT_CARD_FROM_RIGHT = 2;
 	private static final int ANIMATION_NEXT_CARD_FROM_LEFT = 3;
 	private static final int ANIMATION_SLIDE_OUT_TO_LEFT = 4;
 	private static final int ANIMATION_SLIDE_OUT_TO_RIGHT = 5;
 	private static final int ANIMATION_SLIDE_IN_FROM_RIGHT = 6;
 	private static final int ANIMATION_SLIDE_IN_FROM_LEFT = 7;

 	private int mNextAnimation = 0;
    private int mAnimationDurationTurn = 500;
    private int mAnimationDurationMove = 500;

    private int mFadeDuration = 300;

	private Method mSetScrollbarBarFading = null;
	private Method mSetTextIsSelectable = null;

 	/**
 	 * Zeemote controller
 	 */
	protected JoystickToButtonAdapter adapter;
	private int mZeemoteA;
	private int mZeemoteB;
	private int mZeemoteC;
	private int mZeemoteD;
	private int mZeemoteUp;
	private int mZeemoteDown;
	private int mZeemoteLeft;
	private int mZeemoteRight;
	private boolean mZeemoteShowAnswer;
	
	private static final int ZEEMOTE_ACTION_NONE = 0;
	private static final int ZEEMOTE_ACTION_SHOW_ANSWER = 1;
	private static final int ZEEMOTE_ACTION_ANSWER1 = 2;
	private static final int ZEEMOTE_ACTION_ANSWER2 = 3;
	private static final int ZEEMOTE_ACTION_ANSWER3 = 4;
	private static final int ZEEMOTE_ACTION_ANSWER4 = 5;
	private static final int ZEEMOTE_ACTION_ANSWER_RECOMMENDED = 6;
	private static final int ZEEMOTE_ACTION_ANSWER_BETTER = 7;
	private static final int ZEEMOTE_ACTION_UNDO = 8;
	private static final int ZEEMOTE_ACTION_REDO = 9;
	private static final int ZEEMOTE_ACTION_MARK = 10;
	private static final int ZEEMOTE_ACTION_BURY = 11;
	private static final int ZEEMOTE_ACTION_SUSPEND = 12;
	private static final int ZEEMOTE_ACTION_EXIT = 13;
	private static final int ZEEMOTE_ACTION_PLAY = 14;

//    private int zEase;
    
    /**
     * The answer in the compare to field for the current card if answer should be given by learner.
     * Null if the CardLayout in the deck says do not type answer. See also Card.getComparedFieldAnswer().
    */
    private String comparedFieldAnswer = null;
    
    /** The class attribute of the comparedField for formatting */
    private String comparedFieldClass = null;

    /** Used to perform operation in a platform specific way. */
    private Compat mCompat;

    // ----------------------------------------------------------------------------
    // LISTENERS
    // ----------------------------------------------------------------------------

    /**
     * From http://stackoverflow.com/questions/2317428/android-i-want-to-shake-it
     * Thilo Koehler
     */
 	private final SensorEventListener mSensorListener = new SensorEventListener() {
 	    public void onSensorChanged(SensorEvent se) {

 	      float x = se.values[0];
 	      float y = se.values[1];
 	      float z = se.values[2] / 2;
 	      mAccelLast = mAccelCurrent;
 	      mAccelCurrent = (float) Math.sqrt((double) (x*x + y*y + z*z));
 	      float delta = mAccelCurrent - mAccelLast;
 	      mAccel = mAccel * 0.9f + delta; // perform low-cut filter
 	      if (!mShakeActionStarted && mAccel >= (mShakeIntensity / 10)) {
 	    	  mShakeActionStarted = true;
 	    	  executeCommand(mGestureShake);
 	      }
 	    }

 	    public void onAccuracyChanged(Sensor sensor, int accuracy) {
 	    }
 	  };

 	  
    private Handler mHandler = new Handler() {

        @Override
        public void handleMessage(Message msg) {
            Sound.stopSounds();
            Sound.playSound((String) msg.obj, null);
        }
    };


    private final Handler longClickHandler = new Handler();
    private final Runnable longClickTestRunnable = new Runnable() {
        public void run() {
    		Log.i(AnkiDroidApp.TAG, "onEmulatedLongClick");
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


    private View.OnClickListener mCardStatisticsListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            Log.i(AnkiDroidApp.TAG, "Show card statistics");
            stopTimer();
			Themes.htmlOkDialog(Reviewer.this, getResources().getString(R.string.card_browser_card_details), mCurrentCard.getCardDetails(Reviewer.this, false), new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					restartTimer();
				}
			}, new OnCancelListener() {
				@Override
				public void onCancel(DialogInterface arg0) {
					restartTimer();
				}
			}).show();
        }
    };


    // Handler for the "show answer" button
    private View.OnClickListener mFlipCardListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            Log.i(AnkiDroidApp.TAG, "Flip card changed:");
            mTimeoutHandler.removeCallbacks(mShowAnswerTask);
            displayCardAnswer();
        }
    };


    private View.OnClickListener mSelectEaseHandler = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
        	mTimeoutHandler.removeCallbacks(mShowQuestionTask);
        	if (mIsAnswering) {
        		return;
        	}
            switch (view.getId()) {
                case R.id.flashcard_layout_ease1:
                    answerCard(Card.EASE_FAILED);
                    break;
                case R.id.flashcard_layout_ease2:
                	answerCard(Card.EASE_HARD);
                    break;
                case R.id.flashcard_layout_ease3:
                	answerCard(Card.EASE_MID);
                    break;
                case R.id.flashcard_layout_ease4:
                	answerCard(Card.EASE_EASY);
                    break;
                default:
                	mCurrentEase = Card.EASE_NONE;
                    return;
            }
        }
    };


    private View.OnTouchListener mGestureListener = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View v, MotionEvent event) {
        	if (mIsAnswering) {
        		return true;
        	}
            if (gestureDetector.onTouchEvent(event)) {
                return true;
            }
            if (mPrefTextSelection && !mLongClickWorkaround) {
            	switch (event.getAction()) {
            	case MotionEvent.ACTION_DOWN:
            		mTouchStarted = true;
            		longClickHandler.postDelayed(longClickTestRunnable, 800);
            		break;
            	case MotionEvent.ACTION_UP:
            	case MotionEvent.ACTION_MOVE:
                    if(mTouchStarted) {
                        longClickHandler.removeCallbacks(longClickTestRunnable);
                    	mTouchStarted = false;
                    }
            		break;
            	}
            }
            try {
                if (event != null) {
                	if (mSimpleInterface) {
                		mSimpleCard.dispatchTouchEvent(event);
                	} else {
        	            mCard.dispatchTouchEvent(event);	
                	}   	            
                }            	
            } catch (NullPointerException e) {
            	Log.e(AnkiDroidApp.TAG, "Error on dispatching touch event: " + e);
            	if (mInputWorkaround) {
                	Log.e(AnkiDroidApp.TAG, "Error on using InputWorkaround: " + e + " --> disabled");
                	PrefSettings.getSharedPrefs(getBaseContext()).edit().putBoolean("inputWorkaround", false).commit();            		
                	finishWithoutAnimation();
            	}
            }
            return false;
        }
    };

    
    private View.OnLongClickListener mLongClickListener = new View.OnLongClickListener() {

    	@Override
    	public boolean onLongClick(View view) {
    		if (mIsSelecting) {
    			return false;
    		}
    		Log.i(AnkiDroidApp.TAG, "onLongClick");
    		Vibrator vibratorManager = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
    		vibratorManager.vibrate(50);
            longClickHandler.postDelayed(startLongClickAction, 300);
    		return true;
    	}
    };


    private DeckTask.TaskListener mMarkCardHandler = new DeckTask.TaskListener() {
        @Override
        public void onPreExecute() {
        	Resources res = getResources();
            mProgressDialog = StyledProgressDialog.show(Reviewer.this, "", res.getString(R.string.saving_changes), true);
        }

        @Override
        public void onProgressUpdate(DeckTask.TaskData... values) {
            mCurrentCard = values[0].getCard();
            mCompat.invalidateOptionsMenu(Reviewer.this);
        }


        @Override
        public void onPostExecute(DeckTask.TaskData result) {
            if (!result.getBoolean()) {
            	// RuntimeException occured on marking cards
                closeReviewer(RESULT_ANSWERING_ERROR, true);
            }
            mProgressDialog.dismiss();
        }
    };

    private DeckTask.TaskListener mDismissCardHandler = new DeckTask.TaskListener() {
    	boolean mSessionComplete;
    	boolean mNoMoreCards;

        @Override
        public void onPreExecute() {
        }


        @Override
        public void onProgressUpdate(DeckTask.TaskData... values) {
        	boolean[] results = postAnswerCard(values);
        	mSessionComplete = results[0];
        	mNoMoreCards = results[1];
        }


        @Override
        public void onPostExecute(DeckTask.TaskData result) {
            if (!result.getBoolean()) {
            	// RuntimeException occured on dismissing cards
                Reviewer.this.setResult(RESULT_ANSWERING_ERROR);
                closeReviewer(RESULT_ANSWERING_ERROR, true);
                return;
            }
            // Check for no more cards before session complete. If they are both true,
            // no more cards will take precedence when returning to study options.
            if (mNoMoreCards) {
                mShowCongrats = true;
                closeReviewer(RESULT_NO_MORE_CARDS, true);
            } else if (mSessionComplete) {
                closeReviewer(RESULT_SESSION_COMPLETED, true);
            }
        }
    };

    private DeckTask.TaskListener mUpdateCardHandler = new DeckTask.TaskListener() {
        @Override
        public void onPreExecute() {
        	Resources res = getResources();
		try {
	        	mProgressDialog = StyledProgressDialog.show(Reviewer.this, "", res.getString(R.string.saving_changes), true);
		} catch (IllegalArgumentException e) {
			Log.e(AnkiDroidApp.TAG, "Reviewer: Error on showing progress dialog: " + e);
		}
        }


        @Override
        public void onProgressUpdate(DeckTask.TaskData... values) {
            mCurrentCard = values[0].getCard();
            mCompat.invalidateOptionsMenu(Reviewer.this);
            int showQuestion = values[0].getInt();
            if (mPrefWhiteboard) {
                mWhiteboard.clear();
            }

            if (mPrefTimer) {
                mCardTimer.setBase(SystemClock.elapsedRealtime());
                mCardTimer.start();
            }
            if (showQuestion == UPDATE_CARD_SHOW_ANSWER) {
                displayCardAnswer();            	
            } else {
                displayCardQuestion();
            }
            try {
                if (mProgressDialog != null && mProgressDialog.isShowing()) {
			mProgressDialog.dismiss();
                }
            } catch (IllegalArgumentException e) {
                Log.e(AnkiDroidApp.TAG, "Reviewer: Error on dismissing progress dialog: " + e);
                mProgressDialog = null;
            }
        }


        @Override
        public void onPostExecute(DeckTask.TaskData result) {
            if (!result.getBoolean()) {
            	// RuntimeException occured on update cards
                closeReviewer(RESULT_ANSWERING_ERROR, true);
                return;
            }
            mShakeActionStarted = false;
            String str = result.getString();
            if (str != null) {
                if (str.equals(Deck.UNDO_TYPE_SUSPEND_CARD)) {
                	Themes.showThemedToast(Reviewer.this, getResources().getString(R.string.card_unsuspended), true);
                } else if (str.equals("redo suspend")) {
                	Themes.showThemedToast(Reviewer.this, getResources().getString(R.string.card_suspended), true);           	
                }            	
            }
            mInEditor = false;
        }
    };

    private DeckTask.TaskListener mAnswerCardHandler = new DeckTask.TaskListener() {
        private boolean mSessionComplete;
        private boolean mNoMoreCards;


        @Override
        public void onPreExecute() {
            Reviewer.this.setProgressBarIndeterminateVisibility(true);
            if (mPrefTimer) {
                mCardTimer.stop();
            }
            blockControls();
        }


        @Override
        public void onProgressUpdate(DeckTask.TaskData... values) {
        	boolean[] results = postAnswerCard(values);
        	mSessionComplete = results[0];
        	mNoMoreCards = results[1];
        }


        @Override
        public void onPostExecute(DeckTask.TaskData result) {
            if (!result.getBoolean()) {
            	// RuntimeException occured on answering cards
                closeReviewer(RESULT_ANSWERING_ERROR, true);
                return;
            }
            // Check for no more cards before session complete. If they are both true,
            // no more cards will take precedence when returning to study options.
            if (mNoMoreCards) {
                mShowCongrats = true;
                closeReviewer(RESULT_NO_MORE_CARDS, true);
            } else if (mSessionComplete) {
                closeReviewer(RESULT_SESSION_COMPLETED, true);
            }
        }
    };


    DeckTask.TaskListener mSaveAndResetDeckHandler = new DeckTask.TaskListener() {
        @Override
        public void onPreExecute() {
        	if (mProgressDialog != null && mProgressDialog.isShowing()) {
        		mProgressDialog.setMessage(getResources().getString(R.string.saving_changes));
        	} else {
                mProgressDialog = StyledProgressDialog.show(Reviewer.this, "", getResources()
                        .getString(R.string.saving_changes), true);
        	}
        }
        @Override
        public void onPostExecute(DeckTask.TaskData result) {
        	if (mProgressDialog.isShowing()) {
                try {
                    mProgressDialog.dismiss();
                } catch (Exception e) {
                    Log.e(AnkiDroidApp.TAG, "onPostExecute - Dialog dismiss Exception = " + e.getMessage());
                }
            }
            if (mShowCongrats) {
            	finishWithAnimation(ActivityTransitionAnimation.FADE);
            } else {
            	finishWithAnimation(ActivityTransitionAnimation.RIGHT);
            }
        }
        @Override
        public void onProgressUpdate(DeckTask.TaskData... values) {
            // Pass
        }
    };


    private Handler mTimerHandler = new Handler();

    private Runnable removeChosenAnswerText=new Runnable() {
    	public void run() {
    		mChosenAnswer.setText("");
    		setDueMessage();
    	}
    };
    
    
    
    //Zeemote handler
	Handler ZeemoteHandler = new Handler() {
		public void handleMessage(Message msg){
			switch(msg.what){
			case MSG_ZEEMOTE_STICK_UP:
				executeZeemoteCommand(mZeemoteUp);
				break;
			case MSG_ZEEMOTE_STICK_DOWN:
				executeZeemoteCommand(mZeemoteDown);
				break;
			case MSG_ZEEMOTE_STICK_LEFT:
				executeZeemoteCommand(mZeemoteLeft);
				break;
			case MSG_ZEEMOTE_STICK_RIGHT:
				executeZeemoteCommand(mZeemoteRight);
				break;
			case MSG_ZEEMOTE_BUTTON_A:
				executeZeemoteCommand(mZeemoteA);
                break;
			case MSG_ZEEMOTE_BUTTON_B:
				executeZeemoteCommand(mZeemoteB);
				break;
			case MSG_ZEEMOTE_BUTTON_C:
				executeZeemoteCommand(mZeemoteC);
				break;
			case MSG_ZEEMOTE_BUTTON_D:
				executeZeemoteCommand(mZeemoteD);
				break;
			}
			super.handleMessage(msg);
		}
	};
	private int mWaitAnswerSecond;
	private int mWaitQuestionSecond;

    
    
    // ----------------------------------------------------------------------------
    // ANDROID METHODS
    // ----------------------------------------------------------------------------

    @Override
    protected void onCreate(Bundle savedInstanceState) {
    	Themes.applyTheme(this);
        super.onCreate(savedInstanceState);
        Log.i(AnkiDroidApp.TAG, "Reviewer - onCreate");

        mChangeBorderStyle = Themes.getTheme() == Themes.THEME_ANDROID_LIGHT || Themes.getTheme() == Themes.THEME_ANDROID_DARK;
        mCompat = Utils.createCompat();

        // The hardware buttons should control the music volume while reviewing.
        setVolumeControlStream(AudioManager.STREAM_MUSIC);

        // Make sure a deck is loaded before continuing.
        Deck deck = DeckManager.getMainDeck();
        if (deck == null) {
        	finishNoStorageAvailable();
			return;
        } else {
            mMediaDir = setupMedia(deck);
            restorePreferences();

            deck.resetUndo();
            // Remove the status bar and title bar
            if (mPrefFullscreenReview) {
                getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                        WindowManager.LayoutParams.FLAG_FULLSCREEN);
                // Do not hide the title bar in Honeycomb, since it contains the action bar.
                if (!AnkiDroidApp.isHoneycombOrLater()) {
                    requestWindowFeature(Window.FEATURE_NO_TITLE);
                }
            }

            requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);

            registerExternalStorageListener();

            if (mNightMode) {
            	mCurrentBackgroundColor = Themes.getNightModeCardBackground(this);
            } else {
            	mCurrentBackgroundColor = Color.WHITE;
            }

            mRefreshWebview = getRefreshWebviewAndInitializeWebviewVariables();

            initLayout(R.layout.flashcard);
            if (mPrefTextSelection) {
                clipboardSetText("");
                Lookup.initialize(this, mDeckFilename);
            }

            // Load the template for the card and set on it the available width for images
            try {
                mCardTemplate = Utils.convertStreamToString(getAssets().open("card_template.html"));
                mCardTemplate = mCardTemplate.replaceFirst("var availableWidth = \\d*;", "var availableWidth = "
                        + getAvailableWidthInCard() + ";");
            } catch (IOException e) {
                e.printStackTrace();
            }

            // Initialize session limits
            long timelimit = deck.getSessionTimeLimit() * 1000;
            Log.i(AnkiDroidApp.TAG, "SessionTimeLimit: " + timelimit + " ms.");
            mSessionTimeLimit = System.currentTimeMillis() + timelimit;
            mSessionCurrReps = 0;

            // Initialize text-to-speech. This is an asynchronous operation.
            if (mSpeakText && AnkiDroidApp.isDonutOrLater()) {
            	ReadText.initializeTts(this, mDeckFilename);
            }

            // Get last whiteboard state
            if (mPrefWhiteboard && MetaDB.getWhiteboardState(this, mDeckFilename) == 1) {
            	mShowWhiteboard = true;
            	mWhiteboard.setVisibility(View.VISIBLE);
            }

            // Load the first card and start reviewing. Uses the answer card task to load a card, but since we send null
            // as the card to answer, no card will be answered.
            DeckTask.launchDeckTask(DeckTask.TASK_TYPE_ANSWER_CARD, mAnswerCardHandler, new DeckTask.TaskData(0,
                    deck, null));
        }
    }


    // Saves deck each time Reviewer activity loses focus
    @Override
    protected void onPause() {
        super.onPause();
        Log.i(AnkiDroidApp.TAG, "Reviewer - onPause()");

    	mTimeoutHandler.removeCallbacks(mShowAnswerTask);
    	mTimeoutHandler.removeCallbacks(mShowQuestionTask);
    	longClickHandler.removeCallbacks(longClickTestRunnable);
    	longClickHandler.removeCallbacks(startLongClickAction);

        stopTimer();

        if (mShakeEnabled) {
            mSensorManager.unregisterListener(mSensorListener);    	  
        }

        Sound.stopSounds();

        if ((AnkiDroidApp.zeemoteController() != null) && (AnkiDroidApp.zeemoteController().isConnected())){ 
        	Log.d("Zeemote","Removing listener in onPause");
        	AnkiDroidApp.zeemoteController().removeButtonListener(this);
        	AnkiDroidApp.zeemoteController().removeJoystickListener(adapter);
    		adapter.removeButtonListener(this);
    		adapter = null;
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    @Override
    protected void onResume() {
    	mInBackground = false;
      super.onResume();
      Deck deck = DeckManager.getMainDeck();
      if (deck == null) {
    	  Log.e(AnkiDroidApp.TAG, "Reviewer: Deck already closed, returning to study options");
    	  closeReviewer(RESULT_DECK_CLOSED, false);
    	  return;
      }

      // check if deck is already opened in big widget. If yes, reload card (to make sure it's not answered yet)
      if (DeckManager.deckIsOpenedInBigWidget(deck.getDeckPath()) && mCurrentCard != null && !mInEditor) {
    	  Log.i(AnkiDroidApp.TAG, "Reviewer: onResume: get card from big widget");
    	  blockControls();
    	  AnkiDroidWidgetBig.updateWidget(AnkiDroidWidgetBig.UpdateService.VIEW_NOT_SPECIFIED, true);
    	  DeckTask.launchDeckTask(DeckTask.TASK_TYPE_ANSWER_CARD, mAnswerCardHandler, new DeckTask.TaskData(0, deck, null));
      } else {
    	  restartTimer();
      }

      if (mShakeEnabled) {
          mSensorManager.registerListener(mSensorListener, mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_NORMAL);    	  
      }
      if ((AnkiDroidApp.zeemoteController() != null) && (AnkiDroidApp.zeemoteController().isConnected())){
    	  Log.d("Zeemote","Adding listener in onResume");
    	  AnkiDroidApp.zeemoteController().addButtonListener(this);
      	  adapter = new JoystickToButtonAdapter();
      	  AnkiDroidApp.zeemoteController().addJoystickListener(adapter);
      	  adapter.addButtonListener(this);
      	  getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
      }
    }

    @Override
    protected void onStop() {
    	mInBackground = true;
      if (mShakeEnabled) {
          mSensorManager.unregisterListener(mSensorListener);
      }
      super.onStop();
      Deck deck = DeckManager.getMainDeck();
      if (!isFinishing()) {
          // Save changes
          updateBigWidget(!mCardFrame.isEnabled());
          DeckTask.waitToFinish();
          if (deck != null) {
	         	deck.commitToDB();
          }
      }
    WidgetStatus.update(this, WidgetStatus.getDeckStatus(deck));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.i(AnkiDroidApp.TAG, "Reviewer - onDestroy()");
        if (mUnmountReceiver != null) {
            unregisterReceiver(mUnmountReceiver);
        }
        if (mSpeakText && AnkiDroidApp.isDonutOrLater()) {
            ReadText.releaseTts();        	
        }
    }


    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event)  {
        if (keyCode == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0) {
        	Log.i(AnkiDroidApp.TAG, "Reviewer - onBackPressed()");
        	closeReviewer(RESULT_DEFAULT, false);
        	return true;
        }
         /** Enhancement 722: Hardware buttons for scrolling, I.Z. */
        if (!mSimpleInterface) {
            if (keyCode == 92)  {
            	mCard.pageUp(false);
            	if (mDoubleScrolling)  {
            		mCard.pageUp(false);
            	}
            	return true;
            }
            if (keyCode == 93)  {
            	mCard.pageDown(false);
            	if (mDoubleScrolling)  {
            		mCard.pageDown(false);
            	}
            	return true;
            }
            if (mScrollingButtons && keyCode == 94)  {
            	mCard.pageUp(false);
            	if (mDoubleScrolling)  {
            		mCard.pageUp(false);
            	}
            	return true;
            }
            if (mScrollingButtons && keyCode == 95)  {
            	mCard.pageDown(false);
            	if (mDoubleScrolling)  {
            		mCard.pageDown(false);
            	}
            	return true;
            }        	
        }

        return super.onKeyDown(keyCode, event);
    }


    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        setLanguage(mLocale);
        Log.i(AnkiDroidApp.TAG, "onConfigurationChanged");

        mConfigurationChanged = true;

        long savedTimer = mCardTimer.getBase();
        CharSequence savedAnswerField = mAnswerField.getText();
        boolean cardVisible = mCardContainer.getVisibility() == View.VISIBLE;
        int lookupButtonVis = mLookUpIcon.getVisibility();

        // Reload layout
        initLayout(R.layout.flashcard);
        
       	if (mRelativeButtonSize != 100) {
       		mFlipCard.setHeight(mButtonHeight);
       		mEase1.setHeight(mButtonHeight);
       		mEase2.setHeight(mButtonHeight);
       		mEase3.setHeight(mButtonHeight);
       		mEase4.setHeight(mButtonHeight);        	
       	}

        // Modify the card template to indicate the new available width and refresh card
        mCardTemplate = mCardTemplate.replaceFirst("var availableWidth = \\d*;", "var availableWidth = "
                + getAvailableWidthInCard() + ";");

        if (typeAnswer()) {
            mAnswerField.setText(savedAnswerField);
        }
        if (mPrefWhiteboard) {
            mWhiteboard.rotate();
        }
        if (mInvertedColors) {
            invertColors(true);
        }

        // If the card hasn't loaded yet, don't refresh it
        // Also skipping the counts (because we don't know which one to underline)
        // They will be updated when the card loads anyway
        if (mCurrentCard != null) {
        	if (cardVisible) {
                fillFlashcard(false);
                if (mPrefTimer) {
                    mCardTimer.setBase(savedTimer);
                    mCardTimer.start();
                }        		
        		if (sDisplayAnswer) {
        			updateForNewCard();
            	}
        	} else {
        		mCardContainer.setVisibility(View.INVISIBLE);
        		switchVisibility(mProgressBars, View.INVISIBLE);
        		switchVisibility(mCardTimer, View.INVISIBLE);
        	}
    		if (sDisplayAnswer) {
        		showEaseButtons();
        	}
        }
        mLookUpIcon.setVisibility(lookupButtonVis);
        mConfigurationChanged = false;
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuItem item;
        Resources res = getResources();
        if (mPrefWhiteboard) {
            if (mShowWhiteboard) {
                Utils.addMenuItemInActionBar(menu, Menu.NONE, MENU_WHITEBOARD, Menu.NONE,
                        R.string.hide_whiteboard, R.drawable.ic_menu_compose);
            } else {
                Utils.addMenuItemInActionBar(menu, Menu.NONE, MENU_WHITEBOARD, Menu.NONE,
                        R.string.show_whiteboard, R.drawable.ic_menu_compose);            	
            }
            Utils.addMenuItemInActionBar(menu, Menu.NONE, MENU_CLEAR_WHITEBOARD, Menu.NONE,
                    R.string.clear_whiteboard, R.drawable.ic_menu_clear_playlist);
        }
        Utils.addMenuItem(menu, Menu.NONE, MENU_EDIT, Menu.NONE, R.string.menu_edit_card,
                R.drawable.ic_menu_edit);

        SubMenu removeDeckSubMenu = menu.addSubMenu(Menu.NONE, MENU_REMOVE, Menu.NONE, R.string.menu_remove_card);
        removeDeckSubMenu.setIcon(R.drawable.ic_menu_stop);
        removeDeckSubMenu.add(Menu.NONE, MENU_REMOVE_BURY, Menu.NONE, R.string.menu_bury_card);
        removeDeckSubMenu.add(Menu.NONE, MENU_REMOVE_SUSPEND, Menu.NONE, R.string.menu_suspend_card);
        removeDeckSubMenu.add(Menu.NONE, MENU_REMOVE_DELETE, Menu.NONE, R.string.card_browser_delete_card);
        if (mPrefTextSelection) {
            item = menu.add(Menu.NONE, MENU_SEARCH, Menu.NONE, res.getString(R.string.menu_select));
            item.setIcon(R.drawable.ic_menu_search);
        }
        item = menu.add(Menu.NONE, MENU_MARK, Menu.NONE, R.string.menu_mark_card);
        Utils.addMenuItemInActionBar(menu, Menu.NONE, MENU_UNDO, Menu.NONE, R.string.undo,
                R.drawable.ic_menu_revert);
        Utils.addMenuItemInActionBar(menu, Menu.NONE, MENU_REDO, Menu.NONE, R.string.redo,
                R.drawable.ic_menu_redo);
        return true;
    }


    private void updateBigWidget(boolean showProgressDialog) {
    	if (DeckManager.deckIsOpenedInBigWidget(DeckManager.getMainDeckPath())) {
      	  	Log.i(AnkiDroidApp.TAG, "Reviewer: updateBigWidget");
      	  	AnkiDroidWidgetBig.setCard(mCurrentCard);
      	  	AnkiDroidWidgetBig.updateWidget(AnkiDroidWidgetBig.UpdateService.VIEW_SHOW_QUESTION, showProgressDialog);
    	}
    }


    //These three methods use a deprecated API - they should be updated to possibly use its more modern version.
    private boolean clipboardHasText() {
        return mClipboard.hasText();
    }

    private void clipboardSetText(CharSequence text) {
        mClipboard.setText(text);
    }

    private CharSequence clipboardGetText() {
        return mClipboard.getText();
    }


    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem item = menu.findItem(MENU_MARK);
        // No options menu while the card is loading.
        if (mCurrentCard == null) {
        	return false;
        }
        if (mCurrentCard.isMarked()){
            item.setTitle(R.string.menu_marked);
            item.setIcon(R.drawable.ic_menu_star_on);
        } else {
            item.setTitle(R.string.menu_mark_card);
            item.setIcon(R.drawable.ic_menu_star_off);
        }
        if (mPrefTextSelection) {
            item = menu.findItem(MENU_SEARCH);
            if (clipboardHasText()) {
            	item.setTitle(Lookup.getSearchStringTitle());
        		item.setEnabled(Lookup.isAvailable());
            } else {
            	item.setTitle(getResources().getString(R.string.menu_select));
        		item.setEnabled(true);
            }
        }
        menu.findItem(MENU_UNDO).setEnabled(DeckManager.getMainDeck().undoAvailable());
        menu.findItem(MENU_REDO).setEnabled(DeckManager.getMainDeck().redoAvailable());
        return true;
    }


    /** Handles item selections. */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_WHITEBOARD:
                // Toggle mShowWhiteboard value
                mShowWhiteboard = !mShowWhiteboard;
                if (mShowWhiteboard) {
                    // Show whiteboard
                    mWhiteboard.setVisibility(View.VISIBLE);
                    item.setTitle(R.string.hide_whiteboard);
                    MetaDB.storeWhiteboardState(this, mDeckFilename, 1);
                } else {
                    // Hide whiteboard
                    mWhiteboard.setVisibility(View.GONE);
                    item.setTitle(R.string.show_whiteboard);
                    MetaDB.storeWhiteboardState(this, mDeckFilename, 0);
                }
                return true;

            case MENU_CLEAR_WHITEBOARD:
                mWhiteboard.clear();
                return true;

            case MENU_EDIT:
            	return editCard();

            case MENU_REMOVE_BURY:
            	setNextCardAnimation(false);
                DeckTask.launchDeckTask(DeckTask.TASK_TYPE_BURY_CARD, mDismissCardHandler, new DeckTask.TaskData(0,
                        DeckManager.getMainDeck(), mCurrentCard));
                return true;

            case MENU_REMOVE_SUSPEND:
            	setNextCardAnimation(false);
                DeckTask.launchDeckTask(DeckTask.TASK_TYPE_SUSPEND_CARD, mDismissCardHandler, new DeckTask.TaskData(0,
                        DeckManager.getMainDeck(), mCurrentCard));
                return true;

            case MENU_REMOVE_DELETE:
                showDeleteCardDialog();
                return true;

            case MENU_SEARCH:
            	lookUpOrSelectText();
                return true;

            case MENU_MARK:
                DeckTask.launchDeckTask(DeckTask.TASK_TYPE_MARK_CARD, mMarkCardHandler, new DeckTask.TaskData(0,
                        DeckManager.getMainDeck(), mCurrentCard));
                return true;

            case MENU_UNDO:
            	setNextCardAnimation(true);
            	DeckTask.launchDeckTask(DeckTask.TASK_TYPE_UNDO, mUpdateCardHandler, new DeckTask.TaskData(UPDATE_CARD_SHOW_QUESTION,
                        DeckManager.getMainDeck(), mCurrentCard.getId(), false));
                return true;

            case MENU_REDO:
                DeckTask.launchDeckTask(DeckTask.TASK_TYPE_REDO, mUpdateCardHandler, new DeckTask.TaskData(UPDATE_CARD_SHOW_QUESTION,
                        DeckManager.getMainDeck(), mCurrentCard.getId(), false));
                return true;
        }
        return false;
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == EDIT_CURRENT_CARD) {
        	setInAnimation(true);
            if (resultCode == RESULT_OK || resultCode == RESULT_EDIT_CARD_RESET) {
                Log.i(AnkiDroidApp.TAG, "Saving card...");
                int showQuestion = sDisplayAnswer ? UPDATE_CARD_SHOW_ANSWER : UPDATE_CARD_SHOW_QUESTION;
                if (resultCode == RESULT_EDIT_CARD_RESET) {
                	showQuestion = UPDATE_CARD_NEW_CARD;
                }
                DeckTask.launchDeckTask(DeckTask.TASK_TYPE_UPDATE_FACT, mUpdateCardHandler, new DeckTask.TaskData(showQuestion,
                        DeckManager.getMainDeck(), mCurrentCard));
            } else if (resultCode == StudyOptions.CONTENT_NO_EXTERNAL_STORAGE) {
                finishNoStorageAvailable();
            } else {
            	mInEditor = false;
            	fillFlashcard(mShowAnimations);
            }
        }
        if (mPrefTextSelection) {
        	clipboardSetText("");
        }
    }

    private boolean isCramming() {
        return (DeckManager.getMainDeck() != null) && (DeckManager.getMainDeck().name().compareTo("cram") == 0);
    }

    // ----------------------------------------------------------------------------
    // CUSTOM METHODS
    // ----------------------------------------------------------------------------

    /**
     * Registers an intent to listen for ACTION_MEDIA_EJECT notifications. The intent will call
     * closeExternalStorageFiles() if the external media is going to be ejected, so applications can clean up any files
     * they have open.
     */
    private void registerExternalStorageListener() {
        if (mUnmountReceiver == null) {
            mUnmountReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    String action = intent.getAction();
                    if (action.equals(Intent.ACTION_MEDIA_EJECT)) {
                        Log.i(AnkiDroidApp.TAG, "mUnmountReceiver - Action = Media Eject");
                        finishNoStorageAvailable();
                    }
                }
            };
            IntentFilter iFilter = new IntentFilter();
            iFilter.addAction(Intent.ACTION_MEDIA_EJECT);
            iFilter.addDataScheme("file");
            registerReceiver(mUnmountReceiver, iFilter);
        }
    }


    private void stopTimer() {
        // Stop visible timer and card timer 
        if (mPrefTimer) {
            mSavedTimer = SystemClock.elapsedRealtime() - mCardTimer.getBase();
            mCardTimer.stop();
        }
        if (mCurrentCard != null) {
           mCurrentCard.stopTimer();
        }
    }


    private void restartTimer() {
        if (mCurrentCard != null) {
            mCurrentCard.resumeTimer();
        }
        if (mPrefTimer && mSavedTimer != 0) {
            mCardTimer.setBase(SystemClock.elapsedRealtime() - mSavedTimer);
            mCardTimer.start();
        }
    }


    private void setLanguage(String language) {
    	Locale locale;
    	if (language.equals("")) {
        	locale = Locale.getDefault();
    	} else {
        	locale = new Locale(language);
    	}
        Configuration config = new Configuration();
        config.locale = locale;
        this.getResources().updateConfiguration(config, this.getResources().getDisplayMetrics());
    }


    private void finishNoStorageAvailable() {
    	Reviewer.this.setResult(StudyOptions.CONTENT_NO_EXTERNAL_STORAGE);
    	finishWithoutAnimation();
    }


    private boolean editCard() {
        if (isCramming()) {
        	Themes.showThemedToast(Reviewer.this, getResources().getString(R.string.cram_edit_warning), true);
            return false;
        } else {
        	mInEditor = true;
            Intent editCard = new Intent(Reviewer.this, CardEditor.class);
            editCard.putExtra(CardEditor.EXTRA_CALLER, CardEditor.CALLER_REVIEWER);
            editCard.putExtra(CardEditor.EXTRA_DECKPATH, DeckManager.getMainDeckPath());
        	sEditorCard = mCurrentCard;
        	setOutAnimation(true);
            startActivityForResultWithAnimation(editCard, EDIT_CURRENT_CARD, ActivityTransitionAnimation.LEFT);
            return true;
        }
    }


    private void lookUpOrSelectText() {
        if (clipboardHasText()) {
            Log.i(AnkiDroidApp.TAG, "Clipboard has text = " + clipboardHasText());
            lookUp();
    	} else {
        	selectAndCopyText();
    	}
    }


    private boolean lookUp() {
    	mLookUpIcon.setVisibility(View.GONE);
	    mIsSelecting = false;
	    if (Lookup.lookUp(clipboardGetText().toString(), mCurrentCard)) {
	        clipboardSetText("");
	    }
	    return true;
    }


    private void showDeleteCardDialog() {
        Dialog dialog;
        Resources res = getResources();
        StyledDialog.Builder builder = new StyledDialog.Builder(this);
        builder.setTitle(res.getString(R.string.delete_card_title));
        builder.setIcon(android.R.drawable.ic_dialog_alert);
        builder.setMessage(String.format(res.getString(R.string.delete_card_message), Utils.stripHTML(mCurrentCard.getQuestion()), Utils.stripHTML(mCurrentCard.getAnswer())));
        builder.setPositiveButton(res.getString(R.string.yes),
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                    	setNextCardAnimation(false);
                        DeckTask.launchDeckTask(DeckTask.TASK_TYPE_DELETE_CARD, mDismissCardHandler, new DeckTask.TaskData(0, DeckManager.getMainDeck(), mCurrentCard));
                    }
                });
        builder.setNegativeButton(res.getString(R.string.no), null);
        dialog = builder.create();
        dialog.show();
    }


    private void answerCard(int ease) {
        mIsSelecting = false;
        mIsAnswering = true;
        if (mPrefTextSelection) {
            clipboardSetText("");
            if (mLookUpIcon.getVisibility() == View.VISIBLE) {
                mLookUpIcon.setVisibility(View.GONE);
                enableViewAnimation(mLookUpIcon, ViewAnimation.fade(ViewAnimation.FADE_OUT, mFadeDuration, 0));
            }        	
        }
        Deck deck = DeckManager.getMainDeck();
    	switch (ease) {
    		case Card.EASE_FAILED:
    		    mChosenAnswer.setText("\u2022");
    		    mChosenAnswer.setTextColor(mNext1.getTextColors());
    	    	if ((deck.getDueCount() + deck.getNewCountToday()) == 1) {
    	    		mIsLastCard  = true;
                }
    			break;
    		case Card.EASE_HARD:
                mChosenAnswer.setText("\u2022\u2022");
                mChosenAnswer.setTextColor(mNext2.getTextColors());
    			break;
    		case Card.EASE_MID:
                mChosenAnswer.setText("\u2022\u2022\u2022");
                mChosenAnswer.setTextColor(mNext3.getTextColors());
    			break;
    		case Card.EASE_EASY:
                mChosenAnswer.setText("\u2022\u2022\u2022\u2022");
                mChosenAnswer.setTextColor(mNext4.getTextColors());
    			break;
    	}
    	mTimerHandler.removeCallbacks(removeChosenAnswerText);
    	mTimerHandler.postDelayed(removeChosenAnswerText, mShowChosenAnswerLength);
    	Sound.stopSounds();
    	mCurrentEase = ease;
        // Increment number reps counter
        mSessionCurrReps++;
        setNextCardAnimation(false);
        DeckTask.launchDeckTask(DeckTask.TASK_TYPE_ANSWER_CARD, mAnswerCardHandler, new DeckTask.TaskData(
                mCurrentEase, deck, mCurrentCard));
    }

    // Set the content view to the one provided and initialize accessors.
    private void initLayout(Integer layout) {
        setContentView(layout);

        mMainLayout = findViewById(R.id.main_layout);
        Themes.setContentStyle(mMainLayout, Themes.CALLER_REVIEWER);

        mCardContainer = (FrameLayout) findViewById(R.id.flashcard_frame);
		setInAnimation(false);

        findViewById(R.id.top_bar).setOnClickListener(mCardStatisticsListener);

        mCardFrame = (FrameLayout) findViewById(R.id.flashcard);
        mTouchLayer = (FrameLayout) findViewById(R.id.touch_layer);
        mTouchLayer.setOnTouchListener(mGestureListener);
    	if (mPrefTextSelection && mLongClickWorkaround) {
            mTouchLayer.setOnLongClickListener(mLongClickListener);
    	}
        if (mPrefTextSelection) {
            mClipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        }
        mCardFrame.removeAllViews();
        if (mSimpleInterface) {
        	mSimpleCard = new TextView(this);
        	Themes.setRegularFont(mSimpleCard);
        	mSimpleCard.setTextSize(mSimpleCard.getTextSize() * mDisplayFontSize / 100);
        	mSimpleCard.setGravity(Gravity.CENTER);
	        try {
	        	mSetTextIsSelectable = TextView.class.getMethod("setTextIsSelectable", boolean.class);
	        } catch (Throwable e) {
	        	Log.i(AnkiDroidApp.TAG, "mSetTextIsSelectable could not be found due to a too low Android version (< 3.0)");
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
        } else {
            mCard = createWebView();
            mCardFrame.addView(mCard);
        }
        if (!mChangeBorderStyle) {
            ((View)findViewById(R.id.flashcard_border)).setVisibility(View.VISIBLE);        	
        }
        
        if (mRefreshWebview && !mSimpleInterface) {
            mNextCard = createWebView();
            mNextCard.setVisibility(View.GONE);
            mCardFrame.addView(mNextCard, 0);

            mCustomFontStyle = getCustomFontsStyle() + getDefaultFontStyle();
        }

        // hunt for input issue 720, like android issue 3341
        if (!AnkiDroidApp.isFroyoOrLater() && !mSimpleInterface) {
            mCard.setFocusableInTouchMode(true);
        }
        
        // Initialize swipe
        gestureDetector = new GestureDetector(new MyGestureDetector());
        
        // Initialize shake detection
        if (mShakeEnabled) {
            mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
            mSensorManager.registerListener(mSensorListener, mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_NORMAL);
            mAccel = 0.00f;
            mAccelCurrent = SensorManager.GRAVITY_EARTH;
            mAccelLast = SensorManager.GRAVITY_EARTH;	
        }

        Resources res = getResources();

        mEase1 = (Button) findViewById(R.id.ease1);
        mEase1.setTextColor(res.getColor(R.color.next_time_failed_color));
        mEase1Layout = (LinearLayout) findViewById(R.id.flashcard_layout_ease1);
        mEase1Layout.setOnClickListener(mSelectEaseHandler);

        mEase2 = (Button) findViewById(R.id.ease2);
        mEase2.setTextColor(res.getColor(R.color.next_time_usual_color));
        mEase2Layout = (LinearLayout) findViewById(R.id.flashcard_layout_ease2);
        mEase2Layout.setOnClickListener(mSelectEaseHandler);

        mEase3 = (Button) findViewById(R.id.ease3);
        mEase3Layout = (LinearLayout) findViewById(R.id.flashcard_layout_ease3);
        mEase3Layout.setOnClickListener(mSelectEaseHandler);

        mEase4 = (Button) findViewById(R.id.ease4);
        mEase4Layout = (LinearLayout) findViewById(R.id.flashcard_layout_ease4);
        mEase4Layout.setOnClickListener(mSelectEaseHandler);

        mNext1 = (TextView) findViewById(R.id.nextTime1);
        mNext2 = (TextView) findViewById(R.id.nextTime2);
        mNext3 = (TextView) findViewById(R.id.nextTime3);
        mNext4 = (TextView) findViewById(R.id.nextTime4);

        mNext1.setTextColor(res.getColor(R.color.next_time_failed_color));
        mNext2.setTextColor(res.getColor(R.color.next_time_usual_color));

        if (!mshowNextReviewTime) {
        	((TextView)findViewById(R.id.nextTimeflip)).setVisibility(View.GONE);
            mNext1.setVisibility(View.GONE);
            mNext2.setVisibility(View.GONE);
            mNext3.setVisibility(View.GONE);
            mNext4.setVisibility(View.GONE);
        }

        mFlipCard = (Button) findViewById(R.id.flip_card);
        mFlipCardLayout = (LinearLayout) findViewById(R.id.flashcard_layout_flip);
        mFlipCardLayout.setOnClickListener(mFlipCardListener);


        mTextBarRed = (TextView) findViewById(R.id.red_number);
        mTextBarBlack = (TextView) findViewById(R.id.black_number);
        mTextBarBlue = (TextView) findViewById(R.id.blue_number);

        if (mShowProgressBars) {
        	mSessionYesBar = (View) findViewById(R.id.daily_bar);
            mSessionProgressBar = (View) findViewById(R.id.session_progress);
            mProgressBars = (LinearLayout) findViewById(R.id.progress_bars);
        }

        mCardTimer = (Chronometer) findViewById(R.id.card_time);
    	if (mPrefTimer && (mConfigurationChanged)) {
    		switchVisibility(mCardTimer, View.VISIBLE);
    	}
    	if (mShowProgressBars && (mConfigurationChanged)) {
    		switchVisibility(mProgressBars, View.VISIBLE);
    	}

        mChosenAnswer = (TextView) findViewById(R.id.choosen_answer);

        if (mPrefWhiteboard) {       	
            mWhiteboard = new Whiteboard(this, mInvertedColors, mBlackWhiteboard);
            FrameLayout.LayoutParams lp2 = new FrameLayout.LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT);
            mWhiteboard.setLayoutParams(lp2);
            FrameLayout fl = (FrameLayout) findViewById(R.id.whiteboard);
            fl.addView(mWhiteboard);
            
            mWhiteboard.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    if (mShowWhiteboard) {
                        return false;
                    }
                    if (gestureDetector.onTouchEvent(event)) {
                        return true;
                    }
                    return false;
                }
            });            
        }
        mAnswerField = (EditText) findViewById(R.id.answer_field);

        mNextTimeTextColor = getResources().getColor(R.color.next_time_usual_color);
        mNextTimeTextRecomColor = getResources().getColor(R.color.next_time_recommended_color);        	
        mForegroundColor = getResources().getColor(R.color.next_time_usual_color);        	
        if (mInvertedColors) {
            invertColors(true);
        }

        mLookUpIcon = findViewById(R.id.lookup_button);
        mLookUpIcon.setVisibility(View.GONE);
        mLookUpIcon.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View arg0) {
				if (clipboardHasText()) {
					lookUp();
				}
			}
        	
        });
        initControls();
    }


    private WebView createWebView() {
        WebView webView = new MyWebView(this);
        webView.setWillNotCacheDrawing(true);
        webView.setScrollBarStyle(WebView.SCROLLBARS_OUTSIDE_OVERLAY);
        if (mZoomEnabled) {
            webView.getSettings().setBuiltInZoomControls(true);
        }
        webView.getSettings().setJavaScriptEnabled(true);
        webView.setWebChromeClient(new AnkiDroidWebChromeClient());
        webView.addJavascriptInterface(new JavaScriptInterface(), "interface");
        if (AnkiDroidApp.isFroyoOrLater()) {
            webView.setFocusableInTouchMode(false);
        }
        Log.i(AnkiDroidApp.TAG, "Focusable = " + webView.isFocusable() + ", Focusable in touch mode = " + webView.isFocusableInTouchMode());
        if (mSetScrollbarBarFading != null) {
            try {
            	mSetScrollbarBarFading.invoke(webView, false);
            } catch (Throwable e) {
            	Log.i(AnkiDroidApp.TAG, "setScrollbarFadingEnabled could not be set due to a too low Android version (< 2.1)");
            	mSetScrollbarBarFading = null;
            }
        }
        mScaleInPercent = webView.getScale();
        return webView;
    }


    private void invertColors(boolean invert) {
        Resources res = getResources();        

        int[] colors = Themes.setNightMode(this, mMainLayout, invert);
        mForegroundColor = colors[0];
        mNextTimeTextColor = mForegroundColor;
        mNextTimeTextRecomColor = colors[1];

        mFlipCard.setTextColor(mForegroundColor);
        mNext4.setTextColor(mNextTimeTextColor);
        mEase4.setTextColor(mNextTimeTextColor);
        mCardTimer.setTextColor(mForegroundColor);
        mTextBarBlack.setTextColor(mForegroundColor);
        mTextBarBlue.setTextColor(invert ? res.getColor(R.color.textbar_blue_color_inv) : res.getColor(R.color.textbar_blue_color));

        if (mSimpleInterface) {
            mSimpleCard.setBackgroundColor(mCurrentBackgroundColor);
            mSimpleCard.setTextColor(mForegroundColor);
        } else {
            mCard.setBackgroundColor(mCurrentBackgroundColor);        	
        }

        int fgColor = R.color.studyoptions_progressbar_frame_light;
        int bgColor = R.color.studyoptions_progressbar_background_nightmode;
        findViewById(R.id.progress_bars_border1).setBackgroundResource(fgColor);
        findViewById(R.id.progress_bars_border2).setBackgroundResource(fgColor);
        findViewById(R.id.progress_bars_back1).setBackgroundResource(bgColor);
        findViewById(R.id.progress_bars_back2).setBackgroundResource(bgColor);

    }


    private boolean[] postAnswerCard(DeckTask.TaskData... values) {
        Resources res = getResources();
        boolean sessionComplete = false;
        boolean noMoreCards = false;

        // if in background, actualise widget
    	if (mInBackground) {
    		updateBigWidget(false);
        }

        // Check to see if session rep or time limit has been reached
        Deck deck = DeckManager.getMainDeck();
        if (deck == null) {
        	return new boolean[] {false, false};
        }
        long sessionRepLimit = deck.getSessionRepLimit();
        long sessionTime = deck.getSessionTimeLimit();
        String sessionMessage = null;
        String leechMessage;
        Log.i(AnkiDroidApp.TAG, "reviewer leech flag: " + values[0].isPreviousCardLeech() + " " + values[0].isPreviousCardSuspended());

        if (values[0].isPreviousCardLeech()) {
            if (values[0].isPreviousCardSuspended()) {
                leechMessage = res.getString(R.string.leech_suspend_notification);
            } else {
                leechMessage = res.getString(R.string.leech_notification);
            }
            Themes.showThemedToast(Reviewer.this, leechMessage, true);
        }

        if ((sessionRepLimit > 0) && (mSessionCurrReps >= sessionRepLimit)) {
        	sessionComplete = true;
            sessionMessage = res.getString(R.string.session_question_limit_reached);
        } else if ((sessionTime > 0) && (System.currentTimeMillis() >= mSessionTimeLimit)) {
            // session time limit reached, flag for halt once async task has completed.
        	sessionComplete = true;
            sessionMessage = res.getString(R.string.session_time_limit_reached);
        } else if (mIsLastCard) {
        	noMoreCards = true;
            mProgressDialog = StyledProgressDialog.show(Reviewer.this, "", getResources()
                    .getString(R.string.saving_changes), true);
            setOutAnimation(true);
        } else {
            // session limits not reached, show next card
        	mCurrentCard = values[0].getCard();
            mCompat.invalidateOptionsMenu(Reviewer.this);

            // If the card is null means that there are no more cards scheduled for review.
            if (mCurrentCard == null) {
            	noMoreCards = true;
                mProgressDialog = StyledProgressDialog.show(Reviewer.this, "", getResources()
                        .getString(R.string.saving_changes), true);
                setOutAnimation(false);
                return new boolean[] {sessionComplete, noMoreCards};
            }

            // Start reviewing next card
            if (mPrefWriteAnswers) { //only bother query deck if needed
            	String[] answer = mCurrentCard.getComparedFieldAnswer();
            	comparedFieldAnswer = answer[0];
            	comparedFieldClass = answer[1];
            } else {
            	comparedFieldAnswer = null;
            }
            Reviewer.this.setProgressBarIndeterminateVisibility(false);
            // Reviewer.this.enableControls();
            Reviewer.this.unblockControls();
            Reviewer.this.displayCardQuestion();
        }
        if (mChosenAnswer.getText().equals("")) {
            setDueMessage();
        }
        // Show a message to user if a session limit has been reached.
        if (sessionMessage != null) {
        	Themes.showThemedToast(Reviewer.this, sessionMessage, true);
        }
        return new boolean[] {sessionComplete, noMoreCards};
    }


    private void showEaseButtons() {
        Resources res = getResources();

        // hide flipcard button
        switchVisibility(mFlipCardLayout, View.GONE);

        // Set correct label for each button
        if (mCurrentCard.isRev()) {
            mEase1.setText(res.getString(R.string.ease1_successive));
            mEase2.setText(res.getString(R.string.ease2_successive));
            mEase3.setText(res.getString(R.string.ease3_successive));
            mEase4.setText(res.getString(R.string.ease4_successive));
        } else {
            mEase1.setText(res.getString(R.string.ease1_learning));
            mEase2.setText(res.getString(R.string.ease2_learning));
            mEase3.setText(res.getString(R.string.ease3_learning));
            mEase4.setText(res.getString(R.string.ease4_learning));
        }

        // Show buttons
        switchVisibility(mEase1Layout, View.VISIBLE);
        switchVisibility(mEase2Layout, View.VISIBLE);
        switchVisibility(mEase3Layout, View.VISIBLE);
        switchVisibility(mEase4Layout, View.VISIBLE);
        
        // Focus default button
        if (mCurrentCard.isRev()) {
            mEase3Layout.requestFocus();
            mNext2.setTextColor(mNextTimeTextColor);
            mEase2.setTextColor(mNextTimeTextColor);
            mNext3.setTextColor(mNextTimeTextRecomColor);
            mEase3.setTextColor(mNextTimeTextRecomColor);
        } else {
            mEase2Layout.requestFocus();
            mNext2.setTextColor(mNextTimeTextRecomColor);
            mEase2.setTextColor(mNextTimeTextRecomColor);
            mNext3.setTextColor(mNextTimeTextColor);
            mEase3.setTextColor(mNextTimeTextColor);
        }

        // Show next review time
        if (mshowNextReviewTime) {
        mNext1.setText(nextInterval(1));
        mNext2.setText(nextInterval(2));
        mNext3.setText(nextInterval(3));
        mNext4.setText(nextInterval(4));
        switchVisibility(mNext1, View.VISIBLE);
        switchVisibility(mNext2, View.VISIBLE);
        switchVisibility(mNext3, View.VISIBLE);
        switchVisibility(mNext4, View.VISIBLE);
        }
    }


    private void hideEaseButtons() {
    	switchVisibility(mEase1Layout, View.GONE);
    	switchVisibility(mEase2Layout, View.GONE);
    	switchVisibility(mEase3Layout, View.GONE);
    	switchVisibility(mEase4Layout, View.GONE);
    	if (mshowNextReviewTime) {
    		int visibility = typeAnswer() ? View.GONE : View.INVISIBLE;
    		switchVisibility(mNext1, visibility);
    		switchVisibility(mNext2, visibility);
    		switchVisibility(mNext3, visibility);
    		switchVisibility(mNext4, visibility);
    	}
    	if (mFlipCardLayout.getVisibility() != View.VISIBLE) {
    		switchVisibility(mFlipCardLayout, View.VISIBLE);
    		mFlipCardLayout.requestFocus();
    	} else if (typeAnswer()) {
            mAnswerField.requestFocus();

            // Show soft keyboard
            InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            inputMethodManager.showSoftInput(mAnswerField, InputMethodManager.SHOW_FORCED);
    	}
    }


    private void switchVisibility(View view, int visible) {
    	switchVisibility(view, visible, mShowAnimations && !mConfigurationChanged);
    }
    private void switchVisibility(View view, int visible, boolean fade) {
    	view.setVisibility(visible);
    	if (fade) {
    		int duration = mShowAnimations ? mAnimationDurationTurn / 2 : mFadeDuration;
    		if (visible == View.VISIBLE) {
        		enableViewAnimation(view, ViewAnimation.fade(ViewAnimation.FADE_IN, duration, mShowAnimations ? duration : 0));
    		} else {
        		enableViewAnimation(view, ViewAnimation.fade(ViewAnimation.FADE_OUT, duration, 0));
    		}
    	}
    }


    private void switchTopBarVisibility(int visible) {
    	if (mPrefTimer) {
    		switchVisibility(mCardTimer, visible, true);
    	}
    	if (mShowProgressBars) {
    		switchVisibility(mProgressBars, visible, true);
    	}
    	switchVisibility(mTextBarRed, visible, true);
    	switchVisibility(mTextBarBlack, visible, true);
    	switchVisibility(mTextBarBlue, visible, true);
    	switchVisibility(mChosenAnswer, visible, true);
    }


    private void initControls() {
        mCardFrame.setVisibility(View.VISIBLE);
        mTextBarRed.setVisibility(View.VISIBLE);
        mTextBarBlack.setVisibility(View.VISIBLE);
        mTextBarBlue.setVisibility(View.VISIBLE);
        mChosenAnswer.setVisibility(View.VISIBLE);
        mFlipCardLayout.setVisibility(View.VISIBLE);
        
        if (mPrefWhiteboard) {
            mWhiteboard.setVisibility(mShowWhiteboard ? View.VISIBLE : View.GONE);            
        }
        mAnswerField.setVisibility(typeAnswer() ? View.VISIBLE : View.GONE);
    }


    private SharedPreferences restorePreferences() {
        SharedPreferences preferences = PrefSettings.getSharedPrefs(getBaseContext());
        mPrefTimer = preferences.getBoolean("timer", true);
        mPrefWhiteboard = preferences.getBoolean("whiteboard", false);
        mPrefWriteAnswers = preferences.getBoolean("writeAnswers", false);
        mPrefTextSelection = preferences.getBoolean("textSelection", true);
        mLongClickWorkaround = preferences.getBoolean("textSelectionLongclickWorkaround", false);
        mDeckFilename = preferences.getString("deckFilename", "");
        mNightMode = preferences.getBoolean("invertedColors", false);
    	mInvertedColors = mNightMode;
        mBlackWhiteboard = preferences.getBoolean("blackWhiteboard", true);
        mSwapQA = preferences.getBoolean("swapqa", false);
        mPrefUseRubySupport = preferences.getBoolean("useRubySupport", false);
        mPrefFullscreenReview = preferences.getBoolean("fullscreenReview", true);
        mshowNextReviewTime = preferences.getBoolean("showNextReviewTime", true);
        mZoomEnabled = preferences.getBoolean("zoom", false);
        mDisplayFontSize = preferences.getInt("relativeDisplayFontSize", CardModel.DEFAULT_FONT_SIZE_RATIO);
        mRelativeButtonSize = preferences.getInt("answerButtonSize", 100);
        mPrefHideQuestionInAnswer = Integer.parseInt(preferences.getString("hideQuestionInAnswer",
                Integer.toString(HQIA_DO_SHOW)));
        mInputWorkaround = preferences.getBoolean("inputWorkaround", false);
        mPrefFixHebrew = preferences.getBoolean("fixHebrewText", false);
        mPrefFixArabic = preferences.getBoolean("fixArabicText", false);
        mPrefConvertFen = preferences.getBoolean("convertFenText", false);
        mSpeakText = preferences.getBoolean("tts", false);
        mPlaySoundsAtStart = preferences.getBoolean("playSoundsAtStart", true);
        mShowProgressBars = preferences.getBoolean("progressBars", true);
        mPrefUseTimer = preferences.getBoolean("timeoutAnswer", false);
        mWaitAnswerSecond = preferences.getInt("timeoutAnswerSeconds", 20);
        mWaitQuestionSecond = preferences.getInt("timeoutQuestionSeconds", 60);
        mScrollingButtons = preferences.getBoolean("scrolling_buttons", false);
        mDoubleScrolling =  preferences.getBoolean("double_scrolling", false);
        mGesturesEnabled = preferences.getBoolean("swipe", false);
        if (mGesturesEnabled) {
         	mGestureShake = Integer.parseInt(preferences.getString("gestureShake", "0"));
         	if (mGestureShake != 0) {
         		mShakeEnabled = true;
         	}
            mShakeIntensity = preferences.getInt("minShakeIntensity", 70);

            mGestureSwipeUp = Integer.parseInt(preferences.getString("gestureSwipeUp", "0"));
         	mGestureSwipeDown = Integer.parseInt(preferences.getString("gestureSwipeDown", "0"));
         	mGestureSwipeLeft = Integer.parseInt(preferences.getString("gestureSwipeLeft", "13"));
         	mGestureSwipeRight = Integer.parseInt(preferences.getString("gestureSwipeRight", "0"));
         	mGestureDoubleTap = Integer.parseInt(preferences.getString("gestureDoubleTap", "0"));
         	mGestureTapLeft = Integer.parseInt(preferences.getString("gestureTapLeft", "0"));
         	mGestureTapRight = Integer.parseInt(preferences.getString("gestureTapRight", "0"));
         	mGestureTapTop = Integer.parseInt(preferences.getString("gestureTapTop", "0"));
         	mGestureTapBottom = Integer.parseInt(preferences.getString("gestureTapBottom", "0"));
         	mGestureLongclick = Integer.parseInt(preferences.getString("gestureLongclick", "0"));
        }
        mShowAnimations = preferences.getBoolean("themeAnimations", false);
        if (mShowAnimations) {
            int animationDuration = preferences.getInt("animationDuration", 500);
           	mAnimationDurationTurn = animationDuration;
           	mAnimationDurationMove = animationDuration;
        }
        mLocale = preferences.getString("language", "");

        // allow screen orientation in reviewer only when fix preference is not set
        if (preferences.getBoolean("fixOrientation", false)) {
            if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            } else if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            }
        }

        if (preferences.getBoolean("keepScreenOn", false)) {
        	this.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }

        mSimpleInterface = preferences.getBoolean("simpleInterface", false);
        
        mZeemoteA = Integer.parseInt(preferences.getString("zeemoteActionA", "14"));
        mZeemoteB = Integer.parseInt(preferences.getString("zeemoteActionB", "13"));
        mZeemoteC = Integer.parseInt(preferences.getString("zeemoteActionC", "8"));
        mZeemoteD = Integer.parseInt(preferences.getString("zeemoteActionD", "1"));
        mZeemoteUp = Integer.parseInt(preferences.getString("zeemoteActionUp", "5"));
        mZeemoteDown = Integer.parseInt(preferences.getString("zeemoteActionDown", "2"));
        mZeemoteLeft = Integer.parseInt(preferences.getString("zeemoteActionLeft", "3"));
        mZeemoteRight = Integer.parseInt(preferences.getString("zeemoteActionRight", "4"));
        mZeemoteShowAnswer = preferences.getBoolean("zeemoteShowAnswer", false);

        return preferences;
    }


    private void setDueMessage() {
    	Deck deck = DeckManager.getMainDeck();
		if (mCurrentCard != null && deck != null && deck.getScheduler().equals("reviewEarly") && mCurrentCard.getType() != Card.TYPE_FAILED) {
    		mChosenAnswer.setTextColor(mForegroundColor);
    		mChosenAnswer.setText(Utils.fmtTimeSpan(mCurrentCard.getCombinedDue() - Utils.now(), Utils.TIME_FORMAT_IN));				
		}
    }


    private void updateForNewCard() {
    	updateScreenCounts();
    	if (mShowProgressBars) {
            updateStatisticBars();
    	}

        // Clean answer field
        if (typeAnswer()) {
            mAnswerField.setText("");
        }

        if (mPrefWhiteboard && !mShowAnimations) {
            mWhiteboard.clear();
        }

        if (mPrefTimer) {
            mCardTimer.setBase(SystemClock.elapsedRealtime());
            mCardTimer.start();
        }
    }


    private void updateScreenCounts() {
    	if (mCurrentCard == null) {
    		return;
    	}
        Deck deck = DeckManager.getMainDeck();
        int eta = deck.getETA();
        if (deck.hasFinishScheduler() || eta < 1) {
            setTitle(deck.getDeckName());
        } else {
            setTitle(getResources().getQuantityString(R.plurals.reviewer_window_title, eta, deck.getDeckName(), eta));        	
        }

        int _failedSoonCount = deck.getFailedSoonCount();
        int _revCount = deck.getRevCount();
        int _newCount = deck.getNewCountToday();
        
        SpannableString failedSoonCount = new SpannableString(String.valueOf(_failedSoonCount));
        SpannableString revCount = new SpannableString(String.valueOf(_revCount));
        SpannableString newCount = new SpannableString(String.valueOf(_newCount));

        boolean isDue = true; // mCurrentCard.isDue();
        int type = mCurrentCard.getType();

        if (isDue && (type == Card.TYPE_NEW)) {
            newCount.setSpan(new UnderlineSpan(), 0, newCount.length(), 0);
        }
        if (isDue && (type == Card.TYPE_REV)) {
            revCount.setSpan(new UnderlineSpan(), 0, revCount.length(), 0);
        }
        if (isDue && (type == Card.TYPE_FAILED)) {
            failedSoonCount.setSpan(new UnderlineSpan(), 0, failedSoonCount.length(), 0);
        }

        mTextBarRed.setText(failedSoonCount);
        mTextBarBlack.setText(revCount);
        mTextBarBlue.setText(newCount);
    }


    private void updateStatisticBars() {
        if (mStatisticBarsMax == 0) {
            View view = findViewById(R.id.progress_bars_back1);
            mStatisticBarsMax = view.getWidth();
            mStatisticBarsHeight = view.getHeight();
        }
        Deck deck = DeckManager.getMainDeck();
        Utils.updateProgressBars(this, mSessionProgressBar, deck.getSessionProgress(), mStatisticBarsMax, mStatisticBarsHeight, true, false);
        Utils.updateProgressBars(this, mSessionYesBar, deck.getProgress(false), mStatisticBarsMax, mStatisticBarsHeight, true);
    }

    /* Handler for the delay in auto showing question and/or answer
     * One toggle for both question and answer, could set longer
     * delay for auto next question
     */
    private Handler mTimeoutHandler = new Handler();

    private Runnable mShowQuestionTask = new Runnable() {
        public void run() {
            //Assume hitting the "Again" button when auto next question
            if (mEase1Layout.isEnabled() == true && mEase1Layout.getVisibility() == View.VISIBLE) {
		mEase1Layout.performClick();
            }
        }
    };

    private Runnable mShowAnswerTask = new Runnable() {
        public void run() {
            if (mPrefTimer) {
                mCardTimer.stop();
            }
            if (mFlipCardLayout.isEnabled() == true && mFlipCardLayout.getVisibility() == View.VISIBLE && !mIsAnswering) {
		mFlipCardLayout.performClick();
            }
        }
    };

    private void displayCardQuestion() {
        sDisplayAnswer = false;
        mIsAnswering = false;

        if (mButtonHeight == 0 && mRelativeButtonSize != 100) {
        	mButtonHeight = mFlipCard.getHeight() * mRelativeButtonSize / 100;
        	mFlipCard.setHeight(mButtonHeight);
        	mEase1.setHeight(mButtonHeight);
        	mEase2.setHeight(mButtonHeight);
        	mEase3.setHeight(mButtonHeight);
        	mEase4.setHeight(mButtonHeight);        	
        }

        String question = getQuestion();

        if(mPrefFixArabic) {
        	question = ArabicUtilities.reshapeSentence(question, true);
        }
        Log.i(AnkiDroidApp.TAG, "question: '" + question + "'");

        String displayString = "";

        if (mSimpleInterface) {
        	mCardContent = Html.fromHtml(question);
        	if (mCardContent.length() == 0) {
        		SpannableString hint = new SpannableString(getResources().getString(R.string.simple_interface_hint, R.string.card_details_question));
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
            // Show an horizontal line as separation when question is shown in answer
            if (isQuestionDisplayed()) {
                displayString = displayString + "<hr/>";
            }

            if (mSpeakText && AnkiDroidApp.isDonutOrLater()) {
                ReadText.setLanguageInformation(Model.getModel(DeckManager.getMainDeck(), mCurrentCard.getCardModelId(), false).getId(), mCurrentCard.getCardModelId());          
            }
        }

        updateCard(displayString);
        hideEaseButtons();

        // If the user want to show answer automatically
        if (mPrefUseTimer) {
            mTimeoutHandler.removeCallbacks(mShowAnswerTask);
            mTimeoutHandler.postDelayed(mShowAnswerTask, mWaitAnswerSecond * 1000  );            
        }
    }


    private void displayCardAnswer() {
        Log.i(AnkiDroidApp.TAG, "displayCardAnswer");

        // prevent answering (by e.g. gestures) before card is loaded
        if (mCurrentCard == null) {
        	return;
        }

        sDisplayAnswer = true;
        setFlipCardAnimation();

        String answer = getAnswer(), question = getQuestion();

        String displayString = "";
        
        if (mSimpleInterface) {
        	SpannableStringBuilder sb = new SpannableStringBuilder();
		if (isQuestionDisplayed()) {
	        	Spanned ques = Html.fromHtml(question);
	        	if (ques.length() == 0) {
	        		ques = new SpannableString(getResources().getString(R.string.simple_interface_hint, R.string.card_details_question));
	        		((SpannableString)ques).setSpan(new StyleSpan(Typeface.ITALIC), 0, mCardContent.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
			}
	        	sb.append(ques);
	        	sb.append("\n─────\n");
        	}

        	Spanned ans = Html.fromHtml(answer);
        	if (ans.length() == 0) {
        		SpannableString hint = new SpannableString(getResources().getString(R.string.simple_interface_hint, R.string.card_details_answer));
        		hint.setSpan(new StyleSpan(Typeface.ITALIC), 0, hint.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        		ans = hint;
        	}
        	sb.append(ans);
        	mCardContent = sb;
        } else {
            Sound.stopSounds();

            if(mPrefFixArabic) {
            	// reshape
            	answer = ArabicUtilities.reshapeSentence(answer, true);
            	question = ArabicUtilities.reshapeSentence(question, true);
            }

            // If the user wrote an answer
            if (typeAnswer()) {
                mAnswerField.setVisibility(View.GONE);
                if (mCurrentCard != null) {
                    // Obtain the user answer and the correct answer
                    String userAnswer = mAnswerField.getText().toString();         
                    Matcher matcher = sSpanPattern.matcher(Utils.stripHTMLMedia(ArabicUtilities.reshapeSentence(comparedFieldAnswer, true)));
                    String correctAnswer = matcher.replaceAll("");
                    matcher = sBrPattern.matcher(correctAnswer);
                    correctAnswer = matcher.replaceAll("\n");
                    matcher = Sound.sSoundPattern.matcher(correctAnswer);
                    correctAnswer = matcher.replaceAll("");
                    matcher = Image.sImagePattern.matcher(correctAnswer);
                    correctAnswer = matcher.replaceAll("");
                    Log.i(AnkiDroidApp.TAG, "correct answer = " + correctAnswer);

                    // Obtain the diff and send it to updateCard
                    DiffEngine diff = new DiffEngine();

                    StringBuffer span = new StringBuffer();
                    span.append("<span class=\"").append(comparedFieldClass).append("\">");
                    span.append(diff.diff_prettyHtml(diff.diff_main(userAnswer, correctAnswer)));
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

            // Depending on preferences do or do not show the question
            if (isQuestionDisplayed()) {
                StringBuffer sb = new StringBuffer();
                sb.append(enrichWithQADiv(question, false));
                sb.append("<a name=\"question\"></a><hr/>");
                sb.append(displayString);
                displayString = sb.toString();
            }
        }

        mIsSelecting = false;
        updateCard(displayString);
        showEaseButtons();

        // If the user want to show next question automatically
        if (mPrefUseTimer) {
            mTimeoutHandler.removeCallbacks(mShowQuestionTask);
            mTimeoutHandler.postDelayed(mShowQuestionTask, mWaitQuestionSecond * 1000  );            
        }
    }


    private void updateCard(String content) {
        Log.i(AnkiDroidApp.TAG, "updateCard");

        if (mSimpleInterface) {
        	fillFlashcard(mShowAnimations);
        	return;
        }

        mBaseUrl = "";
        Boolean isJapaneseModel = false;
        
        //Check whether there is a hard coded font-size in the content and apply the relative font size
        //Check needs to be done before CSS is applied to content;
        content = recalculateHardCodedFontSize(content, mDisplayFontSize);
        
        // Add CSS for font color and font size
        if (mCurrentCard != null) {
        	final String japaneseModelTag = "Japanese";
        	
            Deck currentDeck = DeckManager.getMainDeck();
            Model myModel = Model.getModel(currentDeck, mCurrentCard.getCardModelId(), false);
		if (myModel == null) {
			Log.e(AnkiDroidApp.TAG, "updateCard - no Model could be fetched. Closing Reviewer and showing db-error dialog");
	                closeReviewer(RESULT_ANSWERING_ERROR, true);			
		}
            mBaseUrl = Utils.getBaseUrl(mMediaDir, myModel, currentDeck);
            int nightBackground = Themes.getNightModeCardBackground(this);
            content = myModel.getCSSForFontColorSize(mCurrentCard.getCardModelId(), mDisplayFontSize, mNightMode, nightBackground) + Model.invertColors(content, mNightMode);
            isJapaneseModel = myModel.hasTag(japaneseModelTag);
            mCurrentBackgroundColor = myModel.getBackgroundColor(mCurrentCard.getCardModelId());
        } else {
        	mCard.getSettings().setDefaultFontSize(calculateDynamicFontSize(content));
            mBaseUrl = Utils.urlEncodeMediaDir(mDeckFilename.replace(".anki", ".media/"));
        }

        Log.i(AnkiDroidApp.TAG, "Initial content card = \n" + content);
        // content = Image.parseImages(deckFilename, content);
        Log.i(AnkiDroidApp.TAG, "content after parsing images = \n" + content);

        // don't play question sound again when displaying answer 
        int questionStartsAt = content.indexOf("<a name=\"question\"></a><hr/>");
        String question = "";
        String answer = "";

        Sound.resetSounds();

        if (isQuestionDisplayed()) {
        	if (sDisplayAnswer && (questionStartsAt != -1)) {
                question = Sound.parseSounds(mBaseUrl, content.substring(0, questionStartsAt), mSpeakText, MetaDB.LANGUAGES_QA_QUESTION);
                answer = Sound.parseSounds(mBaseUrl, content.substring(questionStartsAt, content.length()), mSpeakText, MetaDB.LANGUAGES_QA_ANSWER);
        	} else {
                question = Sound.parseSounds(mBaseUrl, content.substring(0, content.length() - 5), mSpeakText, MetaDB.LANGUAGES_QA_QUESTION) + "<hr/>";
        	}
        } else {
            int qa = MetaDB.LANGUAGES_QA_QUESTION;
        	if (sDisplayAnswer) {
                qa = MetaDB.LANGUAGES_QA_ANSWER;
        	}
        	answer = Sound.parseSounds(mBaseUrl, content, mSpeakText, qa);
        }

        // Parse out the LaTeX images
        question = LaTeX.parseLaTeX(DeckManager.getMainDeck(), question);
        answer = LaTeX.parseLaTeX(DeckManager.getMainDeck(), answer);

       
        // If ruby annotation support is activated, then parse and handle:
        // Strip kanji in question, add furigana in answer
        if (mPrefUseRubySupport && isJapaneseModel) {
          	content = RubyParser.ankiStripKanji(question) + RubyParser.ankiRubyToMarkup(answer);
        } else {
        	content = question + answer;
        }

        // In order to display the bold style correctly, we have to change
        // font-weight to 700
        content = content.replace("font-weight:600;", "font-weight:700;");

        // Find hebrew text
        if (isHebrewFixEnabled()) {
            content = applyFixForHebrew(content);
        }
        
        // Chess notation FEN handling
        if (this.isFenConversionEnabled()) {
        	content = fenToChessboard(content);
        }
		
        Log.i(AnkiDroidApp.TAG, "content card = \n" + content);
        StringBuilder style = new StringBuilder();
        style.append(mCustomFontStyle);
        style.append(getDeckStyle(mCurrentCard.mDeck.getDeckPath()));
        Log.i(AnkiDroidApp.TAG, "::style::" + style);
        mCardContent = new SpannedString(mCardTemplate.replace("::content::", content).replace("::style::", style.toString()));
        Log.i(AnkiDroidApp.TAG, "card html = \n" + card);
        Log.i(AnkiDroidApp.TAG, "base url = " + mBaseUrl );

    	fillFlashcard(mShowAnimations);

        if (!mConfigurationChanged && mPlaySoundsAtStart)
            playSounds();
    }

    /**
     * Plays sounds (or TTS, if configured) for current shown side of card 
     */
    private void playSounds() {
        int qa = sDisplayAnswer ? MetaDB.LANGUAGES_QA_ANSWER : MetaDB.LANGUAGES_QA_QUESTION;

        // We need to play the sounds from the proper side of the card
        if (!mSpeakText)
            Sound.playSounds(qa);
        else {
            if (sDisplayAnswer)
                ReadText.textToSpeech(Utils.stripHTML(getAnswer()), qa);
            else
                ReadText.textToSpeech(Utils.stripHTML(getQuestion()), qa);
        }
    }

    private void setFlipCardAnimation() {
    	mNextAnimation = ANIMATION_TURN;
    }
    private void setNextCardAnimation(boolean reverse) {
    	if (mCardContainer.getVisibility() == View.INVISIBLE) {
    		setInAnimation(reverse);
    	} else {
    		mNextAnimation = reverse ? ANIMATION_NEXT_CARD_FROM_LEFT : ANIMATION_NEXT_CARD_FROM_RIGHT;
    	}
    }
    private void setInAnimation(boolean reverse) {
		mNextAnimation = reverse ? ANIMATION_SLIDE_IN_FROM_LEFT : ANIMATION_SLIDE_IN_FROM_RIGHT;
    }
    private void setOutAnimation(boolean reverse) {
		mNextAnimation = reverse ? ANIMATION_SLIDE_OUT_TO_RIGHT: ANIMATION_SLIDE_OUT_TO_LEFT;
    	if (mCardContainer.getVisibility() == View.VISIBLE && mShowAnimations) {
        	fillFlashcard(true);
    	}
    }


    public void fillFlashcard(boolean flip) {
    	if (!flip) {
	        Log.i(AnkiDroidApp.TAG, "base url = " + mBaseUrl);
        	if (mSimpleInterface) {
        		mSimpleCard.setText(mCardContent);
        	} else if (mRefreshWebview) {
	            mNextCard.setBackgroundColor(mCurrentBackgroundColor);
	            mNextCard.loadDataWithBaseURL(mBaseUrl, mCardContent.toString(), "text/html", "utf-8", null);
	            mNextCard.setVisibility(View.VISIBLE);
	            mCardFrame.removeView(mCard);
	            mCard.destroy();
	            mCard = mNextCard;
	            mNextCard = createWebView();
	            mNextCard.setVisibility(View.GONE);
	            mCardFrame.addView(mNextCard, 0);
	            // hunt for input issue 720, like android issue 3341
	            if (!AnkiDroidApp.isFroyoOrLater()) {
	            	mCard.setFocusableInTouchMode(true);
	            }
	        } else {
	            mCard.loadDataWithBaseURL(mBaseUrl, mCardContent.toString(), "text/html", "utf-8", null);
	            mCard.setBackgroundColor(mCurrentBackgroundColor);
	        }
	        if (mChangeBorderStyle) {
		        switch (mCurrentBackgroundColor) {
		        case Color.WHITE:
		        	if (mInvertedColors) {
		        		mInvertedColors = false;
		            	invertColors(false);	        		
		        	}
			        break;
		        case Color.BLACK:
			        if (!mInvertedColors) {
		            	mInvertedColors = true;
		            	invertColors(true);
			        }
			        break;
		        default:
		            if (Themes.getTheme() != Themes.THEME_BLUE) {
		                mMainLayout.setBackgroundColor(mCurrentBackgroundColor);
		            }
	            	if (mInvertedColors != mNightMode) {
	    	            mInvertedColors = mNightMode;
	    	            invertColors(mNightMode);            		
	            	}
		        }	        	
	        }
	        if (!mShowAnimations && mCardTimer.getVisibility() == View.INVISIBLE) {
    	    	switchTopBarVisibility(View.VISIBLE);
	        }
    		if (!sDisplayAnswer) {
        		updateForNewCard();
        		if (mShowWhiteboard) {
    				mWhiteboard.clear();
        		}
        		setNextCardAnimation(false);
    		}
    	} else {
    		Animation3D rotation;
    		boolean directionToLeft = true;
    		switch (mNextAnimation) {
    		case ANIMATION_TURN:
    			rotation = new Animation3D(mCardContainer.getWidth(), mCardContainer.getHeight(), 9, Animation3D.ANIMATION_TURN, true, true, this);
    			rotation.setDuration(mAnimationDurationTurn);
    			rotation.setInterpolator(new AccelerateDecelerateInterpolator());
    			break;
    		case ANIMATION_NEXT_CARD_FROM_LEFT:
    			directionToLeft = false;
    		case ANIMATION_NEXT_CARD_FROM_RIGHT:
    			rotation = new Animation3D(mCardContainer.getWidth(), mCardContainer.getHeight(), 0, Animation3D.ANIMATION_EXCHANGE_CARD, directionToLeft, true, this);
    			rotation.setDuration(mAnimationDurationMove);
    			rotation.setInterpolator(new AccelerateDecelerateInterpolator());
    			break;
    		case ANIMATION_SLIDE_OUT_TO_RIGHT:
    			directionToLeft = false;
    		case ANIMATION_SLIDE_OUT_TO_LEFT:
        		fillFlashcard(false);
    			rotation = new Animation3D(mCardContainer.getWidth(), mCardContainer.getHeight(), 0, Animation3D.ANIMATION_SLIDE_OUT_CARD, directionToLeft, true, this);
    			rotation.setDuration(mAnimationDurationMove);
    			rotation.setInterpolator(new AccelerateInterpolator());
    	    	switchTopBarVisibility(View.INVISIBLE);
    			break;
    		case ANIMATION_SLIDE_IN_FROM_LEFT:
    			directionToLeft = false;
    		case ANIMATION_SLIDE_IN_FROM_RIGHT:
        		fillFlashcard(false);
    			rotation = new Animation3D(mCardContainer.getWidth(), mCardContainer.getHeight(), 0, Animation3D.ANIMATION_SLIDE_IN_CARD, directionToLeft, true, this);
    			rotation.setDuration(mAnimationDurationMove);
    			rotation.setInterpolator(new DecelerateInterpolator());
    	    	switchTopBarVisibility(View.VISIBLE);
    			break;
    		case ANIMATION_NO_ANIMATION:
    		default:
    			return;
    		}

    		rotation.reset();
    		mCardContainer.setDrawingCacheEnabled(true);
    		mCardContainer.setDrawingCacheBackgroundColor(Themes.getBackgroundColor());
	    	mCardContainer.clearAnimation();
	    	mCardContainer.startAnimation(rotation);
    	}
    }


    public void showFlashcard(boolean visible) {
    	mCardContainer.setVisibility(visible ? View.VISIBLE : View.INVISIBLE); 
    }


    private String getQuestion() {
    	if (mSwapQA) {
    		return mCurrentCard.getAnswer();
    	} else {
    		return mCurrentCard.getQuestion();
    	}
    }


    private String getAnswer() {
    	if (mSwapQA) {
    		return mCurrentCard.getQuestion();
    	} else {
    		return mCurrentCard.getAnswer();
    	}
    }


    private String getDeckStyle(String deckPath) {
      File styleFile = new File(Utils.removeExtension(deckPath) + ".css");
      if (!styleFile.exists() || !styleFile.canRead()) {
        return "";
      }
      StringBuilder style = new StringBuilder();
      try {
        BufferedReader styleReader =
          new BufferedReader(new InputStreamReader(new FileInputStream(styleFile)));
        while (true) {
          String line = styleReader.readLine();
          if (line == null) {
            break;
          }
          style.append(line);
          style.append('\n');
        }
      } catch (IOException e) {
        Log.e(AnkiDroidApp.TAG, "Error reading style file: " + styleFile.getAbsolutePath(), e);
        return "";
      }
      return style.toString();
    }


    /**
     * Returns the CSS used to handle custom fonts.
     * <p>
     * Custom fonts live in fonts directory in the directory used to store decks.
     * <p>
     * Each font is mapped to the font family by the same name as the name of the font fint without
     * the extension.
     */
    private String getCustomFontsStyle() {
      StringBuilder builder = new StringBuilder();
      for (String fontPath : mCustomFontFiles) {
        String fontFace = String.format(
            "@font-face {font-family: \"%s\"; src: url(\"file://%s\");}",
            Utils.removeExtension((new File(fontPath)).getName()), fontPath);
        Log.d(AnkiDroidApp.TAG, "adding to style: " + fontFace);
        builder.append(fontFace);
        builder.append('\n');
      }
      return builder.toString();
    }


    /** Returns the CSS used to set the default font. */
    private String getDefaultFontStyle() {
    	if (mCustomDefaultFontCss == null) {
            SharedPreferences preferences = PrefSettings.getSharedPrefs(getBaseContext());
            String defaultFont = preferences.getString("defaultFont", null);
            if (defaultFont == null || "".equals(defaultFont)) {
            	mCustomDefaultFontCss = "";
            } else {
                mCustomDefaultFontCss = "BODY .question, BODY .answer { font-family: '" + defaultFont + "' }\n";            	
            }
    	}
    	return mCustomDefaultFontCss;
    }


    private boolean isQuestionDisplayed() {
        switch (mPrefHideQuestionInAnswer) {
            case HQIA_DO_HIDE:
                return false;

            case HQIA_DO_SHOW:
                return true;

            case HQIA_CARD_MODEL:
                return (Model.getModel(DeckManager.getMainDeck(), mCurrentCard.getCardModelId(), false).getCardModel(
                        mCurrentCard.getCardModelId()).isQuestionInAnswer());

            default:
                return true;
        }
    }


    public static Card getEditorCard() {
        return sEditorCard;
    }

    private boolean isHebrewFixEnabled() {
        return mPrefFixHebrew;
    }
    
    private boolean isFenConversionEnabled() {
    	return mPrefConvertFen;
    }


    /**
     * Adds a div html tag around the contents to have an indication, where answer/question is displayed
     *
     * @param content
     * @param isAnswer if true then the class attribute is set to "answer", "question" otherwise.
     * @return
     */
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
    
    /**
     * Parses content in question and answer to see, whether someone has hard coded
     * the font size in a card layout. If this is so, then the font size must be
     * replaced with one corrected by the relative font size.
     * If a relative CSS unit measure is used (e.g. 'em'), then only hierarchy in 'span' tag is taken into account.
     * @param content
     * @param percentage - the relative font size percentage defined in preferences
     * @return
     */
    private String recalculateHardCodedFontSize(String content, int percentage) {
    	if (null == content || 0 == content.trim().length()) {
    		return "";
    	}
    	StringBuilder sb = new StringBuilder(content);
    	
    	boolean fontSizeFound = true; //whether the previous loop found a valid font-size attribute
    	int spanTagDepth = 0; //to find out whether a relative CSS unit measure is within another one
    	int outerRelativeSpanTagDepth = 100; //the hierarchy depth of the current outer relative span
    	int start = 0;
    	int posSpan = 0;
    	int posFontSize = 0;
    	int posUnit = 0;
    	int intSize; //for absolute css measurement values
    	double doubleSize; //for relative css measurement values
    	boolean isRelativeUnit = true; //true if em or %
    	String sizeS;
    	
    	//formatter for decimal numbers
    	DecimalFormatSymbols symbols = new DecimalFormatSymbols();
		symbols.setDecimalSeparator('.');
		DecimalFormat dFormat = new DecimalFormat("0.##", symbols);

    	while (fontSizeFound) {
    		posFontSize = sb.indexOf("font-size:", start);
    		if (-1 == posFontSize) {
    			fontSizeFound = false;
    			continue;
    		} else {
    			//check whether </span> are found and decrease spanTagDepth accordingly
        		posSpan = sb.indexOf("</span>", start);
        		while (-1 != posSpan && posSpan < posFontSize) {
        			spanTagDepth -= 1;
        			posSpan = sb.indexOf("</span>", posSpan + 7);
        		}
    			start = posFontSize + 10;
    			for (int a = 0; a < ABSOLUTE_CSS_UNITS.length; a++) {
    				posUnit = sb.indexOf(ABSOLUTE_CSS_UNITS[a], start);
    				if (-1 != posUnit) {
    					isRelativeUnit = false;
    					break;
    				}
    			}
        		if (-1 == posUnit) {
        			for (int a = 0; a < RELATIVE_CSS_UNITS.length; a++) {
        				posUnit = sb.indexOf(RELATIVE_CSS_UNITS[a], start);
        				if (-1 != posUnit) {
        					isRelativeUnit = true;
        					break;
        				}
        			}
        		}
    		}
    		if (-1 == posUnit) {
    			//only absolute and relative measures are taken into account. E.g. 'xx-small', 'inherit' etc. are not taken into account
    			fontSizeFound = false;
    			continue;
    		} else if (17 < (posUnit - posFontSize)) { //assuming max 1 blank and 5 digits
    			//only take into account if font-size measurement is close, because theoretically "font-size:" could be part of text
    			continue; 
    		} else {
    			spanTagDepth += 1; //because we assume that font-sizes always are declared in span tags
    			start = posUnit +3; // needs to be more than posPx due to decimals
    			sizeS = sb.substring(posFontSize + 10, posUnit).trim();
    			if (isRelativeUnit) {
    				if (outerRelativeSpanTagDepth >= spanTagDepth) {
    					outerRelativeSpanTagDepth = spanTagDepth;
		    			try {
		    				doubleSize = dFormat.parse(sizeS).doubleValue();
		    			} catch (ParseException e) {
		    				continue; //ignore this one
		    			}
		    			doubleSize = doubleSize * percentage / 100;
		    			sizeS = dFormat.format(doubleSize);
    				} //else do nothing as relative sizes within relative sizes should not be changed
    			} else {
	    			try {
	    				intSize = Integer.parseInt(sizeS);
	    			} catch (NumberFormatException e) {
	    				start = posFontSize + 10;
	    				continue; //ignore this one
	    			}
	    			intSize = intSize * percentage / 100;
	    			sizeS = Integer.toString(intSize);
    			}
	    		sb.replace(posFontSize + 10, posUnit, sizeS);
    		}
    	}
    	return sb.toString();
    }
    
    /**
     * 
     * @return true if the AnkiDroid preference for writing answer is true and if the Anki Deck CardLayout specifies a field to query
     */
    private final boolean typeAnswer() {
    	if (mPrefWriteAnswers && null != comparedFieldAnswer) {
    		return true;
    	}
    	return false;
    }


    /**
     * Calculates a dynamic font size depending on the length of the contents taking into account that the input string
     * contains html-tags, which will not be displayed and therefore should not be taken into account.
     *
     * @param htmlContents
     * @return font size respecting MIN_DYNAMIC_FONT_SIZE and MAX_DYNAMIC_FONT_SIZE
     */
    private static int calculateDynamicFontSize(String htmlContent) {
        // Replace each <br> with 15 spaces, each <hr> with 30 spaces, then
        // remove all html tags and spaces
        String realContent = htmlContent.replaceAll("\\<br.*?\\>", " ");
        realContent = realContent.replaceAll("\\<hr.*?\\>", " ");
        realContent = realContent.replaceAll("\\<.*?\\>", "");
        realContent = realContent.replaceAll("&nbsp;", " ");
        return Math.max(DYNAMIC_FONT_MIN_SIZE,
                DYNAMIC_FONT_MAX_SIZE - (int) (realContent.length() / DYNAMIC_FONT_FACTOR));
    }


    private void unblockControls() {
        mCardFrame.setEnabled(true);
        mFlipCardLayout.setEnabled(true);

        switch (mCurrentEase) {
            case Card.EASE_FAILED:
                mEase1Layout.setClickable(true);
                mEase2Layout.setEnabled(true);
                mEase3Layout.setEnabled(true);
                mEase4Layout.setEnabled(true);
                break;

            case Card.EASE_HARD:
                mEase1Layout.setEnabled(true);
                mEase2Layout.setClickable(true);
                mEase3Layout.setEnabled(true);
                mEase4Layout.setEnabled(true);
                break;

            case Card.EASE_MID:
                mEase1Layout.setEnabled(true);
                mEase2Layout.setEnabled(true);
                mEase3Layout.setClickable(true);
                mEase4Layout.setEnabled(true);
                break;

            case Card.EASE_EASY:
                mEase1Layout.setEnabled(true);
                mEase2Layout.setEnabled(true);
                mEase3Layout.setEnabled(true);
                mEase4Layout.setClickable(true);
                break;

            default:
                mEase1Layout.setEnabled(true);
                mEase2Layout.setEnabled(true);
                mEase3Layout.setEnabled(true);
                mEase4Layout.setEnabled(true);
                break;
        }

        if (mPrefTimer) {
            mCardTimer.setEnabled(true);
        }

        if (mPrefWhiteboard) {
            mWhiteboard.setEnabled(true);
        }

        if (typeAnswer()) {
            mAnswerField.setEnabled(true);
        }
        mTouchLayer.setVisibility(View.VISIBLE);
    }


    private void blockControls() {
        mCardFrame.setEnabled(false);
        mFlipCardLayout.setEnabled(false);
        mTouchLayer.setVisibility(View.INVISIBLE);

        if (animationDisabled()) {
        	// Do nothing, better no animation on E-Ink display
        } else {
        	switch (mCurrentEase) {
            	case Card.EASE_FAILED:
            		mEase1Layout.setClickable(false);
            		mEase2Layout.setEnabled(false);
            		mEase3Layout.setEnabled(false);
            		mEase4Layout.setEnabled(false);
            		break;

            	case Card.EASE_HARD:
            		mEase1Layout.setEnabled(false);
            		mEase2Layout.setClickable(false);
            		mEase3Layout.setEnabled(false);
            		mEase4Layout.setEnabled(false);
            		break;

            	case Card.EASE_MID:
            		mEase1Layout.setEnabled(false);
            		mEase2Layout.setEnabled(false);
            		mEase3Layout.setClickable(false);
            		mEase4Layout.setEnabled(false);
            		break;

            	case Card.EASE_EASY:
            		mEase1Layout.setEnabled(false);
            		mEase2Layout.setEnabled(false);
            		mEase3Layout.setEnabled(false);
            		mEase4Layout.setClickable(false);
            		break;

            	default:
            		mEase1Layout.setEnabled(false);
            		mEase2Layout.setEnabled(false);
            		mEase3Layout.setEnabled(false);
            		mEase4Layout.setEnabled(false);
            		break;
        	}
        }

        if (mPrefTimer) {
            mCardTimer.setEnabled(false);
        }

        if (mPrefWhiteboard) {
            mWhiteboard.setEnabled(false);
        }

        if (typeAnswer()) {
            mAnswerField.setEnabled(false);
        }
    }


    private int getAvailableWidthInCard() {
        // The width available is equals to
        // the screen's width divided by the default scale factor used by the WebView, because this scale factor will be
        // applied later
        // and minus the padding
        int availableWidth = (int) (AnkiDroidApp.getDisplayWidth() / mScaleInPercent) - TOTAL_WIDTH_PADDING;
        Log.i(AnkiDroidApp.TAG, "availableWidth = " + availableWidth);
        return availableWidth;
    }


    /**
     * Select Text in the webview and automatically sends the selected text to the clipboard.
     * From http://cosmez.blogspot.com/2010/04/webview-emulateshiftheld-on-android.html
     */
    private void selectAndCopyText() {
        try {
            KeyEvent shiftPressEvent = new KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SHIFT_LEFT, 0, 0);
            if (mSimpleInterface) {
            	shiftPressEvent.dispatch(mSimpleCard);
            } else {            	
                shiftPressEvent.dispatch(mCard);
            }
            shiftPressEvent.isShiftPressed();
            mIsSelecting = true;
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }


    public boolean getRefreshWebviewAndInitializeWebviewVariables() {
    	mCustomFontFiles = Utils.getCustomFonts(getBaseContext());
		for (String s : new String[] {"nook"}) {
			if  (android.os.Build.DEVICE.toLowerCase().indexOf(s) != -1 || android.os.Build.MODEL.toLowerCase().indexOf(s) != -1) {
				return true;
			}
		}
        try {
        	// this must not be executed on nook (causes fc)
        	mSetScrollbarBarFading = WebView.class.getMethod("setScrollbarFadingEnabled", boolean.class);
        } catch (Throwable e) {
        	Log.i(AnkiDroidApp.TAG, "setScrollbarFadingEnabled could not be found due to a too low Android version (< 2.1)");
        }
		if (mCustomFontFiles.length != 0) {
			return true;
		}
		return false;
    }


    /**
     * Setup media.
     * Try to detect if we're using dropbox and set the mediaPrefix accordingly. Then set the media directory.
     * @param deck The deck that we've just opened
     */
    public static String setupMedia(Deck deck) {
        String mediaLoc = deck.getVar("mediaLocation");
        if (mediaLoc != null) {
            mediaLoc = mediaLoc.replace("\\", "/");
            if (mediaLoc.contains("/Dropbox/Public/Anki")) {
                // We're using dropbox
                deck.setMediaPrefix(AnkiDroidApp.getDropboxDir());
            }
        }
        return deck.mediaDir();
    }

    
    private String applyFixForHebrew(String text) {
        Matcher m = sHebrewPattern.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String hebrewText = m.group();
            // Some processing before we reverse the Hebrew text
            // 1. Remove all Hebrew vowels as they cannot be displayed properly
            Matcher mv = sHebrewVowelsPattern.matcher(hebrewText);
            hebrewText = mv.replaceAll("");
            // 2. Flip open parentheses, brackets and curly brackets with closed ones and vice-versa
            // Matcher mp = sBracketsPattern.matcher(hebrewText);
            // StringBuffer sbg = new StringBuffer();
            // int bracket[] = new int[1];
            // while (mp.find()) {
            //     bracket[0] = mp.group().codePointAt(0);
            //     if ((bracket[0] & 0x28) == 0x28) {
            //         // flip open/close ( and )
            //         bracket[0] ^= 0x01;
            //     } else if (bracket[0] == 0x5B || bracket[0] == 0x5D || bracket[0] == 0x7B || bracket[0] == 0x7D) {
            //         // flip open/close [, ], { and }
            //         bracket[0] ^= 0x06;
            //     }
            //     mp.appendReplacement(sbg, new String(bracket, 0, 1));
            // }
            // mp.appendTail(sbg);
            // hebrewText = sbg.toString();
            // for (int i = 0; i < hebrewText.length(); i++) {
            //     Log.i(AnkiDroidApp.TAG, "flipped brackets: " + hebrewText.codePointAt(i));
            // }
            // 3. Reverse all numerical groups (so when they get reversed again they show LTR)
            // Matcher mn = sNumeralsPattern.matcher(hebrewText);
            // sbg = new StringBuffer();
            // while (mn.find()) {
            //     StringBuffer sbn = new StringBuffer(m.group());
            //     mn.appendReplacement(sbg, sbn.reverse().toString());
            // }
            // mn.appendTail(sbg);

            // for (int i = 0; i < sbg.length(); i++) {
            //     Log.i(AnkiDroidApp.TAG, "LTR numerals: " + sbg.codePointAt(i));
            // }
            // hebrewText = sbg.toString();//reverse().toString();
            m.appendReplacement(sb, hebrewText); 
        }
        m.appendTail(sb);
        return sb.toString();
    }
    
    private String fenToChessboard(String text) {
        Matcher mf = sFenPattern.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (mf.find()) {
        	if (mf.group(1).length() == 0) {
        		mf.appendReplacement(sb, "<script type=\"text/javascript\">document.write(renderFen('" + mf.group(2) + "',false));</script>");
        	} else {
        		Matcher mo = sFenOrientationPattern.matcher(mf.group(1));
        		if (mo.find() && mo.group(1).equalsIgnoreCase("black")) {
            		mf.appendReplacement(sb, "<script type=\"text/javascript\">document.write(renderFen('" + mf.group(2) + "',1));</script>");
        		} else {
            		mf.appendReplacement(sb, "<script type=\"text/javascript\">document.write(renderFen('" + mf.group(2) + "',false));</script>");
        		}
        	}
        }
        mf.appendTail(sb);
        return sb.toString();
    }

    private void executeZeemoteCommand(int which){
    	switch(which) {
    	case ZEEMOTE_ACTION_NONE : 
    		break;
    	case ZEEMOTE_ACTION_SHOW_ANSWER :
				if (!sDisplayAnswer) {
					displayCardAnswer(); 
				}				
    		break;
    	case ZEEMOTE_ACTION_ANSWER1 : 
    			if (sDisplayAnswer) {
    				answerCard(Card.EASE_FAILED);
    			} else {
    				if (mZeemoteShowAnswer) displayCardAnswer();
    			}
    			break;
    	case ZEEMOTE_ACTION_ANSWER2 : 
    			if (sDisplayAnswer) {
    				answerCard(Card.EASE_HARD);
    			} else {
    				if (mZeemoteShowAnswer) displayCardAnswer();
    			}
    		break;
    	case ZEEMOTE_ACTION_ANSWER3 : 
    			if (sDisplayAnswer) {
    				answerCard(Card.EASE_MID);
    			} else {
    				if (mZeemoteShowAnswer) displayCardAnswer();
    			}
    		break;
    	case ZEEMOTE_ACTION_ANSWER4 : 
    			if (sDisplayAnswer) {
    				answerCard(Card.EASE_EASY);
    			} else {
    				if (mZeemoteShowAnswer) displayCardAnswer();
    			}
    		break;
    	case ZEEMOTE_ACTION_ANSWER_RECOMMENDED : 
    			if (sDisplayAnswer) {
    				if (mCurrentCard.isRev()) {
    					answerCard(Card.EASE_MID);
    				} else {
    					answerCard(Card.EASE_HARD);
    				}
    			} else {
    				if (mZeemoteShowAnswer) displayCardAnswer();
    			}
    		break;
    	case ZEEMOTE_ACTION_ANSWER_BETTER : 
    			if (sDisplayAnswer) {
    				if (mCurrentCard.isRev()) {
    					answerCard(Card.EASE_EASY);
    				} else {
    					answerCard(Card.EASE_MID);
    				}
    			} else {
    				if (mZeemoteShowAnswer) displayCardAnswer();
    			}
    		break;
    	case ZEEMOTE_ACTION_UNDO :
    			executeCommand(GESTURE_UNDO);
    		break;
    	case ZEEMOTE_ACTION_REDO : 
    			executeCommand(GESTURE_REDO);
    		break;
    	case ZEEMOTE_ACTION_MARK : 
    			executeCommand(GESTURE_MARK);
    		break;
    	case ZEEMOTE_ACTION_BURY : 
    			executeCommand(GESTURE_BURY);
    		break;
    	case ZEEMOTE_ACTION_SUSPEND : 
    			executeCommand(GESTURE_SUSPEND);
    		break;
    	case ZEEMOTE_ACTION_EXIT : 
    			executeCommand(GESTURE_EXIT);
    		break;
    	case ZEEMOTE_ACTION_PLAY : 
            playSounds();
    		break;
    	}
    }

    private void executeCommand(int which) {
    	switch(which) {
    	case GESTURE_NOTHING:
    		break;
    	case GESTURE_ANSWER_EASE1:
			if (sDisplayAnswer) {
				answerCard(Card.EASE_FAILED);
			} else {
		        displayCardAnswer();
			}
    		break;
    	case GESTURE_ANSWER_EASE2:
			if (sDisplayAnswer) {
				answerCard(Card.EASE_HARD);
			} else {
		        displayCardAnswer();
			}    		
    		break;
    	case GESTURE_ANSWER_EASE3:
			if (sDisplayAnswer) {
				answerCard(Card.EASE_MID);
			} else {
		        displayCardAnswer();
			}
    		break;
    	case GESTURE_ANSWER_EASE4:
			if (sDisplayAnswer) {
				answerCard(Card.EASE_EASY);
			} else {
		        displayCardAnswer();
			}
    		break;
    	case GESTURE_ANSWER_RECOMMENDED:
			if (sDisplayAnswer) {
				if (mCurrentCard.isRev()) {
					answerCard(Card.EASE_MID);
				} else {
					answerCard(Card.EASE_HARD);
				}
			} else {
				displayCardAnswer();
			}
    		break;
    	case GESTURE_ANSWER_BETTER_THAN_RECOMMENDED:
			if (sDisplayAnswer) {
				if (mCurrentCard.isRev()) {
					answerCard(Card.EASE_EASY);
				} else {
					answerCard(Card.EASE_MID);
				}
			}
    		break;
    	case GESTURE_EXIT:
       	 	closeReviewer(RESULT_DEFAULT, false);
    		break;
    	case GESTURE_UNDO:
    		if (DeckManager.getMainDeck().undoAvailable()) {
    			setNextCardAnimation(true);
        		DeckTask.launchDeckTask(DeckTask.TASK_TYPE_UNDO, mUpdateCardHandler, new DeckTask.TaskData(UPDATE_CARD_SHOW_QUESTION,
                        DeckManager.getMainDeck(), mCurrentCard.getId(), false));    			
    		}
    		break;
    	case GESTURE_REDO:
    		if (DeckManager.getMainDeck().redoAvailable()) {
                DeckTask.launchDeckTask(DeckTask.TASK_TYPE_REDO, mUpdateCardHandler, new DeckTask.TaskData(UPDATE_CARD_SHOW_QUESTION,
                        DeckManager.getMainDeck(), mCurrentCard.getId(), false));    			
    		}
    		break;
    	case GESTURE_EDIT:
        	editCard();
    		break;
    	case GESTURE_MARK:
            DeckTask.launchDeckTask(DeckTask.TASK_TYPE_MARK_CARD, mMarkCardHandler, new DeckTask.TaskData(0,
                    DeckManager.getMainDeck(), mCurrentCard));
    		break;
    	case GESTURE_LOOKUP:
    		lookUpOrSelectText();
    		break;
    	case GESTURE_BURY:
        	setNextCardAnimation(false);
            DeckTask.launchDeckTask(DeckTask.TASK_TYPE_BURY_CARD, mAnswerCardHandler, new DeckTask.TaskData(0,
                    DeckManager.getMainDeck(), mCurrentCard));
    		break;
    	case GESTURE_SUSPEND:
        	setNextCardAnimation(false);
    		DeckTask.launchDeckTask(DeckTask.TASK_TYPE_SUSPEND_CARD, mAnswerCardHandler, new DeckTask.TaskData(0,
                    DeckManager.getMainDeck(), mCurrentCard));
    		break;
    	case GESTURE_DELETE:
    		showDeleteCardDialog();
    		break;
    	case GESTURE_CLEAR_WHITEBOARD:
            if (mPrefWhiteboard) {            	
        		mWhiteboard.clear();
            }
    		break;
    	}
    }

    // ----------------------------------------------------------------------------
    // INNER CLASSES
    // ----------------------------------------------------------------------------

    /**
     * Provides a hook for calling "alert" from javascript. Useful for debugging your javascript.
     */
    public final class AnkiDroidWebChromeClient extends WebChromeClient {
        @Override
        public boolean onJsAlert(WebView view, String url, String message, JsResult result) {
            Log.i(AnkiDroidApp.TAG, message);
            result.confirm();
            return true;
        }
    }

    public final class JavaScriptInterface {

        JavaScriptInterface() { }


        /**
         * This is not called on the UI thread. Send a message that will be handled on the UI thread.
         */
        public void playSound(String soundPath) {
            Message msg = Message.obtain();
            msg.obj = soundPath;
            mHandler.sendMessage(msg);
        }
    }


    private String nextInterval(int ease) {
        Resources res = getResources();

        if (ease == 1){
        	return res.getString(R.string.soon);
        } else {
        	return Utils.fmtTimeSpan(mCurrentCard.nextInterval(mCurrentCard, ease) * 86400, Utils.TIME_FORMAT_DEFAULT);
        }
    }


    private void closeReviewer(int result, boolean saveDeck) {
    	mTimeoutHandler.removeCallbacks(mShowAnswerTask);
		mTimeoutHandler.removeCallbacks(mShowQuestionTask);
		mTimerHandler.removeCallbacks(removeChosenAnswerText);
		longClickHandler.removeCallbacks(longClickTestRunnable);
		longClickHandler.removeCallbacks(startLongClickAction);

		Reviewer.this.setResult(result);

		setOutAnimation(true);    		

		updateBigWidget(!mCardFrame.isEnabled());

        if (saveDeck) {
            DeckTask.launchDeckTask(DeckTask.TASK_TYPE_SAVE_DECK, mSaveAndResetDeckHandler, new DeckTask.TaskData(DeckManager.getMainDeck(), 0));
    	} else {
    		finishWithAnimation(ActivityTransitionAnimation.RIGHT);
    	}
    }

    /** Fixing bug 720: <input> focus, thanks to pablomouzo on android issue 7189*/
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
    }

    class MyGestureDetector extends SimpleOnGestureListener {
     	private boolean mIsXScrolling = false;
     	private boolean mIsYScrolling = false;

    	@Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            if (mGesturesEnabled) {
        		try {
        			if (e2.getY() - e1.getY() > StudyOptions.sSwipeMinDistance && Math.abs(velocityY) > StudyOptions.sSwipeThresholdVelocity && Math.abs(e1.getX() - e2.getX()) < StudyOptions.sSwipeMaxOffPath && !mIsYScrolling) {
                        // down
        				executeCommand(mGestureSwipeDown);
       		        } else if (e1.getY() - e2.getY() > StudyOptions.sSwipeMinDistance && Math.abs(velocityY) > StudyOptions.sSwipeThresholdVelocity && Math.abs(e1.getX() - e2.getX()) < StudyOptions.sSwipeMaxOffPath && !mIsYScrolling) {
                        // up
        				executeCommand(mGestureSwipeUp);
       		        } else if (e2.getX() - e1.getX() > StudyOptions.sSwipeMinDistance && Math.abs(velocityX) > StudyOptions.sSwipeThresholdVelocity && Math.abs(e1.getY() - e2.getY()) < StudyOptions.sSwipeMaxOffPath && !mIsXScrolling && !mIsSelecting) {
                      	 // right
       		        	executeCommand(mGestureSwipeRight);
                    } else if (e1.getX() - e2.getX() > StudyOptions.sSwipeMinDistance && Math.abs(velocityX) > StudyOptions.sSwipeThresholdVelocity && Math.abs(e1.getY() - e2.getY()) < StudyOptions.sSwipeMaxOffPath && !mIsXScrolling && !mIsSelecting) {
                    	// left
                    	executeCommand(mGestureSwipeLeft);
                    }
               		mIsXScrolling = false;        		
               		mIsYScrolling = false;        		
                 }
                 catch (Exception e) {
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
            if(mTouchStarted) {
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
    		if (mPrefTextSelection && mClipboard != null) {
                if (clipboardGetText().length() != 0 && Lookup.isAvailable()) {
                	if (mLookUpIcon.getVisibility() != View.VISIBLE) {
            			mLookUpIcon.setVisibility(View.VISIBLE);
                        enableViewAnimation(mLookUpIcon, ViewAnimation.fade(ViewAnimation.FADE_IN, mFadeDuration, 0));                		
                	}
                } else {
                	if (mLookUpIcon.getVisibility() == View.VISIBLE) {
                        mLookUpIcon.setVisibility(View.GONE);
                        enableViewAnimation(mLookUpIcon, ViewAnimation.fade(ViewAnimation.FADE_OUT, mFadeDuration, 0));                		
                	}
                }                
    		}
    		return false;
    	}
    	
        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
        	if (!mSimpleInterface) {
            	if (mCard.getScrollY() != 0) {
            		mIsYScrolling = true;        		
            	}
            	if (mCard.getScrollX() != 0) {
            		mIsXScrolling = true;
            	}        		
        	}
            return super.onScroll(e1, e2, distanceX, distanceY);
        }

    }
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (gestureDetector.onTouchEvent(event))
	        return true;
	    else
	    	return false;
    }


	@Override
	public void buttonPressed(ButtonEvent arg0) {
		Log.d("Zeemote","Button pressed, id: "+arg0.getButtonID());
	}


	@Override
	public void buttonReleased(ButtonEvent arg0) {
		Log.d("Zeemote","Button released, id: "+arg0.getButtonID());
		Message msg = Message.obtain();
		msg.what = MSG_ZEEMOTE_BUTTON_A + arg0.getButtonID(); //Button A = 0, Button B = 1...
		if ((msg.what >= MSG_ZEEMOTE_BUTTON_A) && (msg.what <= MSG_ZEEMOTE_BUTTON_D)) { //make sure messages from future buttons don't get throug
			this.ZeemoteHandler.sendMessage(msg);
		}
		if (arg0.getButtonID()==-1)
		{
			msg.what = MSG_ZEEMOTE_BUTTON_D+arg0.getButtonGameAction();
			if ((msg.what >= MSG_ZEEMOTE_STICK_UP) && (msg.what <= MSG_ZEEMOTE_STICK_RIGHT)) { //make sure messages from future buttons don't get throug
				this.ZeemoteHandler.sendMessage(msg);
			}
		}
	}
}
