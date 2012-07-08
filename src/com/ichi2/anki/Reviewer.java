/****************************************************************************************
 * Copyright (c) 2011 Kostas Spyropoulos <inigo.aldana@gmail.com>                       *
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
// TODO: implement own menu? http://www.codeproject.com/Articles/173121/Android-Menus-My-Way

package com.ichi2.anki;

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
import android.widget.ProgressBar;
import android.widget.TextView;

import com.ichi2.anim.ActivityTransitionAnimation;
import com.ichi2.anim.Animation3D;
import com.ichi2.anim.ViewAnimation;
import com.ichi2.anki2.R;
import com.ichi2.async.DeckTask;
import com.ichi2.libanki.Card;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Models;
import com.ichi2.libanki.Note;
import com.ichi2.libanki.Sched;
import com.ichi2.libanki.Sound;
import com.ichi2.libanki.Utils;
import com.ichi2.themes.StyledDialog;
import com.ichi2.themes.StyledOpenCollectionDialog;
import com.ichi2.themes.StyledProgressDialog;
import com.ichi2.themes.Themes;
import com.ichi2.utils.DiffEngine;
import com.ichi2.widget.WidgetStatus;

import org.amr.arabic.ArabicUtilities;
import org.json.JSONArray;
import org.json.JSONException;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Reviewer extends AnkiActivity {
    /**
     * Result codes that are returned when this activity finishes.
     */
    public static final int RESULT_DEFAULT = 50;
    public static final int RESULT_SESSION_COMPLETED = 51;
    public static final int RESULT_NO_MORE_CARDS = 52;
    public static final int RESULT_EDIT_CARD_RESET = 53;
    public static final int RESULT_DECK_CLOSED = 55;

    /**
     * Available options performed by other activities.
     */
    public static final int EDIT_CURRENT_CARD = 0;

    /** Constant for class attribute signaling answer */
    public static final String ANSWER_CLASS = "answer";

    /** Constant for class attribute signaling question */
    public static final String QUESTION_CLASS = "question";

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
    private static final int MENU_REMOVE_SUSPEND_CARD = 32;
    private static final int MENU_REMOVE_SUSPEND_NOTE = 33;
    private static final int MENU_REMOVE_DELETE = 34;
    private static final int MENU_SEARCH = 4;
    private static final int MENU_MARK = 5;
    private static final int MENU_UNDO = 6;

    public static final int EASE_FAILED = 1;
    public static final int EASE_HARD = 2;
    public static final int EASE_MID = 3;
    public static final int EASE_EASY = 4;

    /** Regex pattern used in removing tags from text before diff */
    private static final Pattern sSpanPattern = Pattern.compile("</?span[^>]*>");
    private static final Pattern sBrPattern = Pattern.compile("<br\\s?/?>");

    // Type answer pattern
    private static final Pattern sTypeAnsPat = Pattern.compile("\\[\\[type:(.+?)\\]\\]");
    private static final String sTypeAnswerForm = "<center>\n<input type=text id=typeans onkeypress=\"_typeAnsPress();\"\n   style=\"font-family: '%s'; font-size: %spx;\">\n</center>\n";

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
    private static final Pattern sHebrewVowelsPattern = Pattern
            .compile("[[\\u0591-\\u05BD][\\u05BF\\u05C1\\u05C2\\u05C4\\u05C5\\u05C7]]");
    // private static final Pattern sBracketsPattern = Pattern.compile("[()\\[\\]{}]");
    // private static final Pattern sNumeralsPattern = Pattern.compile("[0-9][0-9%]+");

    /** to be sento to and from the card editor */
    private static Card sEditorCard;

    private static boolean sDisplayAnswer = false;

    /** The percentage of the absolute font size specified in the deck. */
    private int mDisplayFontSize = 100;

    /** The absolute CSS measurement units inclusive semicolon for pattern search */
    private static final String[] ABSOLUTE_CSS_UNITS = { "px;", "pt;", "in;", "cm;", "mm;", "pc;" };

    /** The relative CSS measurement units inclusive semicolon for pattern search */
    private static final String[] RELATIVE_CSS_UNITS = { "%;", "em;" };

    /**
     * Broadcast that informs us when the sd card is about to be unmounted
     */
    private BroadcastReceiver mUnmountReceiver = null;

    private boolean mInBackground = false;

    /**
     * Variables to hold preferences
     */
    private boolean mPrefOvertime;
    private boolean mPrefHideDueCount;
    private boolean mPrefTimer;
    private boolean mPrefWhiteboard;
    private boolean mPrefWriteAnswers;
    private boolean mPrefTextSelection;
    private boolean mInputWorkaround;
    private boolean mLongClickWorkaround;
    private boolean mPrefFullscreenReview;
    private boolean mshowNextReviewTime;
    private boolean mZoomEnabled;
    private String mCollectionFilename;
    private int mRelativeButtonSize;
    private boolean mDoubleScrolling;
    private boolean mScrollingButtons;
    private boolean mGesturesEnabled;
    private boolean mShakeEnabled = false;
    private int mShakeIntensity;
    private boolean mShakeActionStarted = false;
    private boolean mPrefFixHebrew; // Apply manual RTL for hebrew text - bug in Android WebView
    private boolean mPrefFixArabic;
    // Android WebView
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
    private boolean mCurrentSimpleInterface = false;
    private ArrayList<String> mSimpleInterfaceExcludeTags;
    private String mLocale;

    // Answer card & cloze deletion variables
    /** The correct answer in the compare to field if answer should be given by learner.
     * Null if no answer is expected. */
    private String mTypeCorrect;
    /** The font name attribute of the type answer field for formatting */
    private String mTypeFont;
    /** The font size attribute of the type answer field for formatting */
    private int mTypeSize;
    private String mTypeWarning;

    private boolean mIsSelecting = false;
    private boolean mTouchStarted = false;
    private boolean mInAnswer = false;

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
    private View mSessionProgressTotalBar;
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
    private StyledOpenCollectionDialog mOpenCollectionDialog;
    private Menu mOptionsMenu;
    private ProgressBar mProgressBar;

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
    private List<AnkiFont> mCustomFontFiles;
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
    private static final int GESTURE_SHOW_ANSWER = 1;
    private static final int GESTURE_ANSWER_EASE1 = 2;
    private static final int GESTURE_ANSWER_EASE2 = 3;
    private static final int GESTURE_ANSWER_EASE3 = 4;
    private static final int GESTURE_ANSWER_EASE4 = 5;
    private static final int GESTURE_ANSWER_RECOMMENDED = 6;
    private static final int GESTURE_ANSWER_BETTER_THAN_RECOMMENDED = 7;
    private static final int GESTURE_UNDO = 8;
    private static final int GESTURE_EDIT = 9;
    private static final int GESTURE_MARK = 10;
    private static final int GESTURE_LOOKUP = 11;
    private static final int GESTURE_BURY = 12;
    private static final int GESTURE_SUSPEND = 13;
    private static final int GESTURE_DELETE = 14;
    private static final int GESTURE_CLEAR_WHITEBOARD = 15;
    private static final int GESTURE_PLAY_MEDIA = 16;
    private static final int GESTURE_EXIT = 17;

    private Spanned mCardContent;
    private String mBaseUrl;

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

    private Sched mSched;

    // private int zEase;

    // ----------------------------------------------------------------------------
    // LISTENERS
    // ----------------------------------------------------------------------------

    /**
     * From http://stackoverflow.com/questions/2317428/android-i-want-to-shake-it Thilo Koehler
     */
    private final SensorEventListener mSensorListener = new SensorEventListener() {
        public void onSensorChanged(SensorEvent se) {

            float x = se.values[0];
            float y = se.values[1];
            float z = se.values[2] / 2;
            mAccelLast = mAccelCurrent;
            mAccelCurrent = (float) Math.sqrt((double) (x * x + y * y + z * z));
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
            // Themes.htmlOkDialog(Reviewer.this, getResources().getString(R.string.card_browser_card_details),
            // mCurrentCard.getCardDetails(Reviewer.this, false), new DialogInterface.OnClickListener() {
            // @Override
            // public void onClick(DialogInterface dialog, int which) {
            // restartTimer();
            // }
            // }, new OnCancelListener() {
            // @Override
            // public void onCancel(DialogInterface arg0) {
            // restartTimer();
            // }
            // }).show();
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
            switch (view.getId()) {
                case R.id.flashcard_layout_ease1:
                    answerCard(EASE_FAILED);
                    break;
                case R.id.flashcard_layout_ease2:
                    answerCard(EASE_HARD);
                    break;
                case R.id.flashcard_layout_ease3:
                    answerCard(EASE_MID);
                    break;
                case R.id.flashcard_layout_ease4:
                    answerCard(EASE_EASY);
                    break;
                default:
                    mCurrentEase = 0;
                    return;
            }
        }
    };

    private View.OnTouchListener mGestureListener = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View v, MotionEvent event) {
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
                    Reviewer.this.finishWithoutAnimation();
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
            mProgressDialog = StyledProgressDialog
                    .show(Reviewer.this, "", res.getString(R.string.saving_changes), true);
        }


        @Override
        public void onProgressUpdate(DeckTask.TaskData... values) {
            updateMenuItems();
            if (mProgressDialog.isShowing()) {
                mProgressDialog.dismiss();
            }
        }


        @Override
        public void onPostExecute(DeckTask.TaskData result) {
            if (!result.getBoolean()) {
                // RuntimeException occured on marking cards
                closeReviewer(DeckPicker.RESULT_DB_ERROR, true);
            }
        }
    };

    private DeckTask.TaskListener mDismissCardHandler = new DeckTask.TaskListener() {
        @Override
        public void onPreExecute() {
        }


        @Override
        public void onProgressUpdate(DeckTask.TaskData... values) {
            mAnswerCardHandler.onProgressUpdate(values);
        }


        @Override
        public void onPostExecute(DeckTask.TaskData result) {
            if (!result.getBoolean()) {
                closeReviewer(DeckPicker.RESULT_DB_ERROR, false);
            }
            mAnswerCardHandler.onPostExecute(result);
        }
    };

    private DeckTask.TaskListener mUpdateCardHandler = new DeckTask.TaskListener() {
        private boolean mNoMoreCards;


        @Override
        public void onPreExecute() {
            Resources res = getResources();
            try {
                mProgressDialog = StyledProgressDialog.show(Reviewer.this, "", res.getString(R.string.saving_changes),
                        true);
            } catch (IllegalArgumentException e) {
                Log.e(AnkiDroidApp.TAG, "Reviewer: Error on showing progress dialog: " + e);
            }
        }


        @Override
        public void onProgressUpdate(DeckTask.TaskData... values) {
            mCurrentCard = values[0].getCard();
            if (mCurrentCard == null) {
                // If the card is null means that there are no more cards scheduled for review.
                mNoMoreCards = true;
                mProgressDialog = StyledProgressDialog.show(Reviewer.this, "",
                        getResources().getString(R.string.saving_changes), true);
                setOutAnimation(false);
                return;
            }
            if (mPrefWhiteboard) {
                mWhiteboard.clear();
            }

            if (mPrefTimer) {
                mCardTimer.setBase(SystemClock.elapsedRealtime());
                mCardTimer.start();
            }
            if (sDisplayAnswer) {
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
                closeReviewer(DeckPicker.RESULT_DB_ERROR, false);
                return;
            }
            if (mNoMoreCards) {
                mShowCongrats = true;
                closeReviewer(RESULT_NO_MORE_CARDS, true);
            }
            mShakeActionStarted = false;
            // String str = result.getString();
            // if (str != null) {
            // if (str.equals(Decks.UNDO_TYPE_SUSPEND_CARD)) {
            // Themes.showThemedToast(Reviewer.this, getResources().getString(R.string.card_unsuspended), true);
            // } else if (str.equals("redo suspend")) {
            // Themes.showThemedToast(Reviewer.this, getResources().getString(R.string.card_suspended), true);
            // }
            // }
            mInEditor = false;
        }
    };

    private DeckTask.TaskListener mAnswerCardHandler = new DeckTask.TaskListener() {
        private boolean mSessionComplete;
        private boolean mNoMoreCards;


        @Override
        public void onPreExecute() {
            mProgressBar.setVisibility(View.VISIBLE);
            if (mPrefTimer) {
                mCardTimer.stop();
            }
            blockControls();
        }


        @Override
        public void onProgressUpdate(DeckTask.TaskData... values) {
            Resources res = getResources();

            // if in background, actualise widget
            // if (mInBackground) {
            // updateBigWidget(false);
            // }

            if (mSched == null) {
                // TODO: proper testing for restored activity
                finish();
                return;
            }

            int leech = values[0].getInt();
            // 0: normal; 1: leech; 2: leech & suspended
            if (leech > 0) {
                String leechMessage;
                if (leech == 2) {
                    leechMessage = res.getString(R.string.leech_suspend_notification);
                } else {
                    leechMessage = res.getString(R.string.leech_notification);
                }
                Themes.showThemedToast(Reviewer.this, leechMessage, true);
            }

            // // TODO: sessionLimithandling
            // long sessionRepLimit = 100;//deck.getSessionRepLimit();
            // long sessionTime = 1000;//deck.getSessionTimeLimit();
            String sessionMessage = null;
            // if ((sessionRepLimit > 0) && (mSessionCurrReps >= sessionRepLimit)) {
            // sessionComplete = true;
            // sessionMessage = res.getString(R.string.session_question_limit_reached);
            // } else if ((sessionTime > 0) && (System.currentTimeMillis() >= mSessionTimeLimit)) {
            // // session time limit reached, flag for halt once async task has completed.
            // sessionComplete = true;
            // sessionMessage = res.getString(R.string.session_time_limit_reached);
            // } else if (mIsLastCard) {
            // noMoreCards = true;
            // mProgressDialog = StyledProgressDialog.show(Reviewer.this, "", getResources()
            // .getString(R.string.saving_changes), true);
            // setOutAnimation(true);
            // } else {
            // session limits not reached, show next card



            mCurrentCard = values[0].getCard();
            boolean timebox_reached = Collection.currentCollection().timeboxReached() != null ? true : false;
            if (timebox_reached && mPrefOvertime && !Collection.currentCollection().getOvertime()) {
                Collection.currentCollection().setOvertime(true);
            }
            //String timebox_message = "Timebox finished!";
            if (mCurrentCard == null) {
                // If the card is null means that there are no more cards scheduled for review.
                mNoMoreCards = true;
                mProgressDialog = StyledProgressDialog.show(Reviewer.this, "",
                        getResources().getString(R.string.saving_changes), true);
                setOutAnimation(false);
            } else if (timebox_reached && !mPrefOvertime) {
                //SharedPreferences preferences = PrefSettings.getSharedPrefs(getActivity().getBaseContext()); //getActivity().getBaseContext()
                //boolean overtime = preferences.getBoolean("overtime", true);

                mSessionComplete = true;
                mProgressDialog = StyledProgressDialog.show(Reviewer.this, "",
                        getResources().getString(R.string.saving_changes), true);
                setOutAnimation(false);

                sessionMessage = getResources().getString(R.string.timebox_reached);
            } else {
                if (timebox_reached) {
                    sessionMessage = getResources().getString(R.string.timebox_reached);
                }
                // Start reviewing next card
                if (mPrefWriteAnswers) {
                    // only bother query deck if needed
                    updateTypeAnswerInfo();
                } else {
                    mTypeCorrect = null;
                }
                mProgressBar.setVisibility(View.INVISIBLE);
                Reviewer.this.unblockControls();
                Reviewer.this.displayCardQuestion();
            }
            // if (mChosenAnswer.getText().equals("")) {
            // setDueMessage();
            // }

            // Show a message to user if a session limit has been reached.
            if (sessionMessage != null) {
                Themes.showThemedToast(Reviewer.this, sessionMessage, true);
            }
        }


        @Override
        public void onPostExecute(DeckTask.TaskData result) {
            if (!result.getBoolean()) {
                // RuntimeException occured on answering cards
                closeReviewer(DeckPicker.RESULT_DB_ERROR, false);
                return;
            }
            // Check for no more cards before session complete. If they are both true, no more cards will take
            // precedence when returning to study options.
            if (mNoMoreCards) {
                mShowCongrats = true;
                closeReviewer(RESULT_NO_MORE_CARDS, true);
            } else if (mSessionComplete) {
                closeReviewer(RESULT_SESSION_COMPLETED, true);
            }
            if (mProgressDialog != null && mProgressDialog.isShowing()) {
                mProgressDialog.dismiss();
            }
        }
    };


    /**
     * Extract type answer/cloze text and font/size
     */
    private void updateTypeAnswerInfo() {
        mTypeCorrect = null;
        String q = mCurrentCard.getQuestion(false);
        Matcher m = sTypeAnsPat.matcher(q);
        int clozeIdx = 0;
        if (!m.find()) {
            return;
        }
        String fld = m.group(1);
        // if it's a cloze, extract data
        if (fld.startsWith("cloze:", 0)) {
            // get field and cloze position
            clozeIdx = mCurrentCard.getOrd() + 1;
            fld = fld.split(":")[1];
        }
        // loop through fields for a match
        try {
            JSONArray ja = mCurrentCard.model().getJSONArray("flds");
            for (int i = 0; i < ja.length(); i++) {
                String name = (String) (ja.getJSONObject(i).get("name"));
                if (name.equals(fld)) {
                    mTypeCorrect = mCurrentCard.note().getitem(name);
                    if (clozeIdx != 0) {
                        // narrow to cloze
                        mTypeCorrect = contentForCloze(mTypeCorrect, clozeIdx);
                    }
                    mTypeFont = (String) (ja.getJSONObject(i).get("font"));
                    mTypeSize = (Integer) (ja.getJSONObject(i).get("size"));
                    break;
                }
            }
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        if (mTypeCorrect == null) {
            if (clozeIdx != 0) {
                mTypeWarning = "Please run Tools>Maintenance>Empty Cards";
            } else {
                mTypeWarning = "Type answer: unknown field " + fld;
            }
        } else if (mTypeCorrect.equals("")) {
            mTypeCorrect = null;
        } else {
            mTypeWarning = null;
        }
    }


    /**
     * Format question field when it contains typeAnswer or clozes.
     * If there was an error during type text extraction, a warning is displayed
     * @param buf The question text
     * @return The formatted question text
     */
    private String typeAnsQuestionFilter(String buf) {
        Matcher m = sTypeAnsPat.matcher(buf);
        if (mTypeWarning != null) {
            return m.replaceFirst(mTypeWarning);
        }
        return m.replaceFirst("");
    }
    /**
     * Format answer field when it contains typeAnswer or clozes
     * @param buf The answer text
     * @return The formatted answer text
     */
    private String typeAnsAnswerFilter(String buf) {
        Matcher m = sTypeAnsPat.matcher(buf);
        return m.replaceFirst("");
    }

    private String contentForCloze(String txt, int idx) {
        Pattern re = Pattern.compile("\\{\\{c" + idx + "::(.+?)\\}\\}");
        Matcher m = re.matcher(txt);
        if (!m.find()) {
            return null;
        }
        String result = m.group(1);
        while (m.find()) {
            result += ", " + m.group(1);
        }
        return result;
    }

    // DeckTask.TaskListener mSaveAndResetDeckHandler = new DeckTask.TaskListener() {
    // @Override
    // public void onPreExecute() {
    // if (mProgressDialog != null && mProgressDialog.isShowing()) {
    // mProgressDialog.setMessage(getResources().getString(R.string.saving_changes));
    // } else {
    // mProgressDialog = StyledProgressDialog.show(Reviewer.this, "", getResources()
    // .getString(R.string.saving_changes), true);
    // }
    // }
    //
    //
    // @Override
    // public void onPostExecute(DeckTask.TaskData result) {
    // if (mProgressDialog.isShowing()) {
    // try {
    // mProgressDialog.dismiss();
    // } catch (Exception e) {
    // Log.e(AnkiDroidApp.TAG, "onPostExecute - Dialog dismiss Exception = " + e.getMessage());
    // }
    // }
    // finish();
    // if (Integer.valueOf(android.os.Build.VERSION.SDK) > 4) {
    // if (mShowCongrats) {
    // ActivityTransitionAnimation.slide(Reviewer.this, ActivityTransitionAnimation.FADE);
    // } else {
    // ActivityTransitionAnimation.slide(Reviewer.this, ActivityTransitionAnimation.RIGHT);
    // }
    // }
    // }
    //
    //
    // @Override
    // public void onProgressUpdate(DeckTask.TaskData... values) {
    // // Pass
    // }
    // };

    private Handler mTimerHandler = new Handler();

    private Runnable removeChosenAnswerText = new Runnable() {
        public void run() {
            mChosenAnswer.setText("");
            setDueMessage();
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

        mChangeBorderStyle = Themes.getTheme() == Themes.THEME_ANDROID_LIGHT
                || Themes.getTheme() == Themes.THEME_ANDROID_DARK;

        // The hardware buttons should control the music volume while reviewing.
        setVolumeControlStream(AudioManager.STREAM_MUSIC);

        Collection col = Collection.currentCollection();
        if (col == null) {
            reloadCollection(savedInstanceState);
            return;
        } else {
            mSched = col.getSched();
            mCollectionFilename = col.getPath();

            mBaseUrl = Utils.getBaseUrl(col.getMedia().getDir());
            restorePreferences();

            try {
                String[] title = mSched.getCol().getDecks().current().getString("name").split("::");
                setTitle(title[title.length - 1]);
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
            UIUtils.setActionBarSubtitle(this, "");

            // Remove the status bar and title bar
            if (mPrefFullscreenReview) {
                getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                        WindowManager.LayoutParams.FLAG_FULLSCREEN);
                // Do not hide the title bar in Honeycomb, since it contains the action bar.
                if (!AnkiDroidApp.isHoneycombOrLater()) {
                    requestWindowFeature(Window.FEATURE_NO_TITLE);
                }
            }

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
                Lookup.initialize(this, mCollectionFilename);
            }

            // Load the template for the card
            try {
                mCardTemplate = Utils.convertStreamToString(getAssets().open("card_template.html"));
            } catch (IOException e) {
                e.printStackTrace();
            }

            // Initialize session limits
            // long timelimit = deck.getSessionTimeLimit() * 1000;
            // Log.i(AnkiDroidApp.TAG, "SessionTimeLimit: " + timelimit + " ms.");
            // mSessionTimeLimit = System.currentTimeMillis() + timelimit;
            mSessionCurrReps = 0;

            // Initialize text-to-speech. This is an asynchronous operation.
            if (mSpeakText && Integer.valueOf(android.os.Build.VERSION.SDK) > 3) {
                ReadText.initializeTts(this, mCollectionFilename);
            }

            // Get last whiteboard state
            if (mPrefWhiteboard && MetaDB.getWhiteboardState(this, mCollectionFilename) == 1) {
                mShowWhiteboard = true;
                mWhiteboard.setVisibility(View.VISIBLE);
            }

            // Load the first card and start reviewing. Uses the answer card
            // task to load a card, but since we send null
            // as the card to answer, no card will be answered.
            DeckTask.launchDeckTask(DeckTask.TASK_TYPE_ANSWER_CARD, mAnswerCardHandler, new DeckTask.TaskData(mSched,
                    null, 0));
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

    }


    @Override
    protected void onResume() {
        mInBackground = false;
        super.onResume();
        // Decks deck = DeckManager.getMainDeck();
        // if (deck == null) {
        // Log.e(AnkiDroidApp.TAG, "Reviewer: Deck already closed, returning to study options");
        // closeReviewer(RESULT_DECK_CLOSED, false);
        // return;
        // }

        // check if deck is already opened in big widget. If yes, reload card (to make sure it's not answered yet)
        // if (DeckManager.deckIsOpenedInBigWidget(deck.getDeckPath()) && mCurrentCard != null && !mInEditor) {
        // Log.i(AnkiDroidApp.TAG, "Reviewer: onResume: get card from big widget");
        // blockControls();
        // AnkiDroidWidgetBig.updateWidget(AnkiDroidWidgetBig.UpdateService.VIEW_NOT_SPECIFIED, true);
        // DeckTask.launchDeckTask(DeckTask.TASK_TYPE_ANSWER_CARD, mAnswerCardHandler, new DeckTask.TaskData(0, deck,
        // null));
        // } else {
        // restartTimer();
        // }
        //
        if (mShakeEnabled) {
            mSensorManager.registerListener(mSensorListener,
                    mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_NORMAL);
        }
    }


    @Override
    protected void onStop() {
        mInBackground = true;
        if (mShakeEnabled) {
            mSensorManager.unregisterListener(mSensorListener);
        }
        super.onStop();
        // Decks deck = DeckManager.getMainDeck();
        // if (!isFinishing()) {
        // // Save changes
        // updateBigWidget(!mCardFrame.isEnabled());
        // DeckTask.waitToFinish();
        // if (deck != null) {
        // deck.commitToDB();
        // }
        // }

        if (!isFinishing()) {
            // try {
            WidgetStatus.update(this, mSched.progressToday(null, mCurrentCard, true));
            
            // } catch (JSONException e) {
            // throw new RuntimeException(e);
            // }
            UIUtils.saveCollectionInBackground(mSched.getCol());
        }
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
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0) {
            Log.i(AnkiDroidApp.TAG, "Reviewer - onBackPressed()");
            closeReviewer(RESULT_DEFAULT, false);
            return true;
        }
        /** Enhancement 722: Hardware buttons for scrolling, I.Z. */
        if (!mCurrentSimpleInterface) {
            if (keyCode == 92) {
                mCard.pageUp(false);
                if (mDoubleScrolling) {
                    mCard.pageUp(false);
                }
                return true;
            }
            if (keyCode == 93) {
                mCard.pageDown(false);
                if (mDoubleScrolling) {
                    mCard.pageDown(false);
                }
                return true;
            }
            if (mScrollingButtons && keyCode == 94) {
                mCard.pageUp(false);
                if (mDoubleScrolling) {
                    mCard.pageUp(false);
                }
                return true;
            }
            if (mScrollingButtons && keyCode == 95) {
                mCard.pageDown(false);
                if (mDoubleScrolling) {
                    mCard.pageDown(false);
                }
                return true;
            }
        }

        return super.onKeyDown(keyCode, event);
    }


//    @Override
//    public void onConfigurationChanged(Configuration newConfig) {
//        super.onConfigurationChanged(newConfig);
//        setLanguage(mLocale);
//        Log.i(AnkiDroidApp.TAG, "onConfigurationChanged");
//
//        mConfigurationChanged = true;
//
//        long savedTimer = mCardTimer.getBase();
//        CharSequence savedAnswerField = mAnswerField.getText();
//        boolean cardVisible = mCardContainer.getVisibility() == View.VISIBLE;
//        int lookupButtonVis = mLookUpIcon.getVisibility();
//
//        // Reload layout
//        initLayout(R.layout.flashcard);
//
//        if (mRelativeButtonSize != 100) {
//            mFlipCard.setHeight(mButtonHeight);
//            mEase1.setHeight(mButtonHeight);
//            mEase2.setHeight(mButtonHeight);
//            mEase3.setHeight(mButtonHeight);
//            mEase4.setHeight(mButtonHeight);
//        }
//
//        // Modify the card template to indicate the new available width and refresh card
//        mCardTemplate = mCardTemplate.replaceFirst("var availableWidth = \\d*;", "var availableWidth = "
//                + getAvailableWidthInCard() + ";");
//
//        if (typeAnswer()) {
//            mAnswerField.setText(savedAnswerField);
//        }
//        if (mPrefWhiteboard) {
//            mWhiteboard.rotate();
//        }
//        if (mInvertedColors) {
//            invertColors(true);
//        }
//
//        // If the card hasn't loaded yet, don't refresh it
//        // Also skipping the counts (because we don't know which one to underline)
//        // They will be updated when the card loads anyway
//        if (mCurrentCard != null) {
//            if (cardVisible) {
//                fillFlashcard(false);
//                if (mPrefTimer) {
//                    mCardTimer.setBase(savedTimer);
//                    mCardTimer.start();
//                }
//                if (sDisplayAnswer) {
//                    updateForNewCard();
//                }
//            } else {
//                mCardContainer.setVisibility(View.INVISIBLE);
//                switchVisibility(mProgressBars, View.INVISIBLE);
//                switchVisibility(mCardTimer, View.INVISIBLE);
//            }
//            if (sDisplayAnswer) {
//                showEaseButtons();
//            }
//        }
//        mLookUpIcon.setVisibility(lookupButtonVis);
//        mConfigurationChanged = false;
//    }


    private void updateMenuItems() {
        if (mOptionsMenu == null) {
            return;
        }
        MenuItem item = mOptionsMenu.findItem(MENU_MARK);
        if (mCurrentCard.note().hasTag("marked")) {
            item.setTitle(R.string.menu_unmark_card);
            item.setIcon(R.drawable.ic_menu_marked);
        } else {
            item.setTitle(R.string.menu_mark_card);
            item.setIcon(R.drawable.ic_menu_mark);
        }
        item = mOptionsMenu.findItem(MENU_UNDO);
        if (mSched.getCol().undoAvailable()) {
            item.setEnabled(true);
            item.setIcon(R.drawable.ic_menu_revert);
        } else {
            item.setEnabled(false);
            item.setIcon(R.drawable.ic_menu_revert_disabled);
        }
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        mOptionsMenu = menu;
        MenuItem item;
        Resources res = getResources();

        UIUtils.addMenuItemInActionBar(menu, Menu.NONE, MENU_MARK, Menu.NONE, R.string.menu_mark_card,
                R.drawable.ic_menu_mark);
        UIUtils.addMenuItemInActionBar(menu, Menu.NONE, MENU_UNDO, Menu.NONE, R.string.undo,
                R.drawable.ic_menu_revert_disabled);
        UIUtils.addMenuItem(menu, Menu.NONE, MENU_EDIT, Menu.NONE, R.string.menu_edit_card, R.drawable.ic_menu_edit);
        if (mPrefWhiteboard) {
            if (mShowWhiteboard) {
                UIUtils.addMenuItemInActionBar(menu, Menu.NONE, MENU_WHITEBOARD, Menu.NONE, R.string.hide_whiteboard,
                        R.drawable.ic_menu_compose);
            } else {
                UIUtils.addMenuItemInActionBar(menu, Menu.NONE, MENU_WHITEBOARD, Menu.NONE, R.string.show_whiteboard,
                        R.drawable.ic_menu_compose);
            }
            UIUtils.addMenuItemInActionBar(menu, Menu.NONE, MENU_CLEAR_WHITEBOARD, Menu.NONE,
                    R.string.clear_whiteboard, R.drawable.ic_menu_clear_playlist);
        }

        SubMenu removeDeckSubMenu = menu.addSubMenu(Menu.NONE, MENU_REMOVE, Menu.NONE, R.string.menu_dismiss_note);
        removeDeckSubMenu.setIcon(R.drawable.ic_menu_stop);
        removeDeckSubMenu.add(Menu.NONE, MENU_REMOVE_BURY, Menu.NONE, R.string.menu_bury_note);
        removeDeckSubMenu.add(Menu.NONE, MENU_REMOVE_SUSPEND_CARD, Menu.NONE, R.string.menu_suspend_card);
        removeDeckSubMenu.add(Menu.NONE, MENU_REMOVE_SUSPEND_NOTE, Menu.NONE, R.string.menu_suspend_note);
        removeDeckSubMenu.add(Menu.NONE, MENU_REMOVE_DELETE, Menu.NONE, R.string.menu_delete_note);
        if (mPrefTextSelection) {
            item = menu.add(Menu.NONE, MENU_SEARCH, Menu.NONE, res.getString(R.string.menu_select));
            item.setIcon(R.drawable.ic_menu_search);
        }
        return true;
    }


    private void updateBigWidget(boolean showProgressDialog) {
        // if (DeckManager.deckIsOpenedInBigWidget(DeckManager.getMainDeckPath())) {
        // Log.i(AnkiDroidApp.TAG, "Reviewer: updateBigWidget");
        // AnkiDroidWidgetBig.setCard(mCurrentCard);
        // AnkiDroidWidgetBig.updateWidget(AnkiDroidWidgetBig.UpdateService.VIEW_SHOW_QUESTION, showProgressDialog);
        // }
    }


    private void reloadCollection(final Bundle savedInstanceState) {
        DeckTask.launchDeckTask(
                DeckTask.TASK_TYPE_OPEN_COLLECTION,
                new DeckTask.TaskListener() {

                    @Override
                    public void onPostExecute(DeckTask.TaskData result) {
                        if (mOpenCollectionDialog.isShowing()) {
                            try {
                            	mOpenCollectionDialog.dismiss();
                            } catch (Exception e) {
                                Log.e(AnkiDroidApp.TAG, "onPostExecute - Dialog dismiss Exception = " + e.getMessage());
                            }
                        }
                        Collection col = result.getCollection();
                        Collection.putCurrentCollection(col);
                        if (col == null) {
                            finish();
                            // finishNoStorageAvailable();
                        } else {
                            onCreate(savedInstanceState);
                        }
                    }


                    @Override
                    public void onPreExecute() {
                    	mOpenCollectionDialog = StyledOpenCollectionDialog.show(Reviewer.this, getResources().getString(R.string.open_collection), new OnCancelListener() {
                                    @Override
                                    public void onCancel(DialogInterface arg0) {
                                        finish();
                                    }
                                });
                    }


                    @Override
                    public void onProgressUpdate(DeckTask.TaskData... values) {
                    }
                },
                new DeckTask.TaskData(AnkiDroidApp.getSharedPrefs(getBaseContext()).getString("deckPath",
                        AnkiDroidApp.getDefaultAnkiDroidDirectory())
                        + AnkiDroidApp.COLLECTION_PATH, 0, true));
    }


    // These three methods use a deprecated API - they should be updated to possibly use its more modern version.
    private boolean clipboardHasText() {
        return mClipboard.hasText();
    }


    private void clipboardSetText(CharSequence text) {
        mClipboard.setText(text);
    }


    private CharSequence clipboardGetText() {
        return mClipboard.getText();
    }


    // @Override
    // public boolean onPrepareOptionsMenu(Menu menu) {
    // MenuItem item = menu.findItem(MENU_MARK);
    // if (mCurrentCard == null) {
    // return false;
    // }
    // if (mCurrentCard.note().hasTag("marked")) {
    // item.setTitle(R.string.menu_unmark_card);
    // item.setIcon(R.drawable.ic_menu_marked);
    // } else {
    // item.setTitle(R.string.menu_mark_card);
    // item.setIcon(R.drawable.ic_menu_mark);
    // }
    // // if (mCurrentCard.isMarked()) {
    // // item.setTitle(R.string.menu_marked);
    // // item.setIcon(R.drawable.ic_menu_star_on);
    // // } else {
    // // item.setTitle(R.string.menu_mark_card);
    // // item.setIcon(R.drawable.ic_menu_star_off);
    // // }
    // if (mPrefTextSelection) {
    // item = menu.findItem(MENU_SEARCH);
    // if (clipboardHasText()) {
    // item.setTitle(Lookup.getSearchStringTitle());
    // item.setEnabled(Lookup.isAvailable());
    // } else {
    // item.setTitle(getResources().getString(R.string.menu_select));
    // item.setEnabled(true);
    // }
    // }
    // if (mPrefFullscreenReview) {
    // // Temporarily remove top bar to avoid annoying screen flickering
    // mTextBarRed.setVisibility(View.GONE);
    // mTextBarBlack.setVisibility(View.GONE);
    // mTextBarBlue.setVisibility(View.GONE);
    // mChosenAnswer.setVisibility(View.GONE);
    // if (mPrefTimer) {
    // mCardTimer.setVisibility(View.GONE);
    // }
    // if (mShowProgressBars) {
    // mProgressBars.setVisibility(View.GONE);
    // }
    //
    // getWindow().setFlags(0, WindowManager.LayoutParams.FLAG_FULLSCREEN);
    // }
    // menu.findItem(MENU_UNDO).setEnabled(mSched.getCol().undoAvailable());
    // return true;
    // }

    @Override
    public void onOptionsMenuClosed(Menu menu) {
        if (mPrefFullscreenReview) {
            // Restore top bar
            mTextBarRed.setVisibility(View.VISIBLE);
            mTextBarBlack.setVisibility(View.VISIBLE);
            mTextBarBlue.setVisibility(View.VISIBLE);
            mChosenAnswer.setVisibility(View.VISIBLE);
            if (mPrefTimer) {
                mCardTimer.setVisibility(View.VISIBLE);
            }
            if (mShowProgressBars) {
                mProgressBars.setVisibility(View.VISIBLE);
            }

            // Restore fullscreen preference
            getWindow()
                    .setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }
    }


    /** Handles item selections. */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {

            case android.R.id.home:
                closeReviewer(AnkiDroidApp.RESULT_TO_HOME, true);
                return true;

            case MENU_WHITEBOARD:
                // Toggle mShowWhiteboard value
                mShowWhiteboard = !mShowWhiteboard;
                if (mShowWhiteboard) {
                    // Show whiteboard
                    mWhiteboard.setVisibility(View.VISIBLE);
                    item.setTitle(R.string.hide_whiteboard);
                    MetaDB.storeWhiteboardState(this, mCollectionFilename, 1);
                } else {
                    // Hide whiteboard
                    mWhiteboard.setVisibility(View.GONE);
                    item.setTitle(R.string.show_whiteboard);
                    MetaDB.storeWhiteboardState(this, mCollectionFilename, 0);
                }
                return true;

            case MENU_CLEAR_WHITEBOARD:
                mWhiteboard.clear();
                return true;

            case MENU_EDIT:
                return editCard();

            case MENU_REMOVE_BURY:
                setNextCardAnimation(false);
                DeckTask.launchDeckTask(DeckTask.TASK_TYPE_DISMISS_NOTE, mDismissCardHandler, new DeckTask.TaskData(
                        mSched, mCurrentCard, 0));
                return true;

            case MENU_REMOVE_SUSPEND_CARD:
                setNextCardAnimation(false);
                DeckTask.launchDeckTask(DeckTask.TASK_TYPE_DISMISS_NOTE, mDismissCardHandler, new DeckTask.TaskData(
                        mSched, mCurrentCard, 1));
                return true;

            case MENU_REMOVE_SUSPEND_NOTE:
                setNextCardAnimation(false);
                DeckTask.launchDeckTask(DeckTask.TASK_TYPE_DISMISS_NOTE, mDismissCardHandler, new DeckTask.TaskData(
                        mSched, mCurrentCard, 2));
                return true;

            case MENU_REMOVE_DELETE:
                showDeleteNoteDialog();
                return true;

            case MENU_SEARCH:
                lookUpOrSelectText();
                return true;

            case MENU_MARK:
                DeckTask.launchDeckTask(DeckTask.TASK_TYPE_MARK_CARD, mMarkCardHandler, new DeckTask.TaskData(mSched,
                        mCurrentCard, 0));
                return true;

            case MENU_UNDO:
                undo();
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == DeckPicker.RESULT_DB_ERROR) {
            closeReviewer(DeckPicker.RESULT_DB_ERROR, false);
        }

        if (resultCode == AnkiDroidApp.RESULT_TO_HOME) {
            closeReviewer(AnkiDroidApp.RESULT_TO_HOME, true);
        } else if (resultCode == DeckPicker.RESULT_MEDIA_EJECTED) {
            finishNoStorageAvailable();
        }
        if (requestCode == EDIT_CURRENT_CARD) {
            setInAnimation(true);
            if (resultCode != RESULT_CANCELED) {
                Log.i(AnkiDroidApp.TAG, "Saving card...");
                DeckTask.launchDeckTask(DeckTask.TASK_TYPE_UPDATE_FACT, mUpdateCardHandler, new DeckTask.TaskData(
                        mSched, mCurrentCard, true));
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
        // return (DeckManager.getMainDeck() != null) && (DeckManager.getMainDeck().name().compareTo("cram") == 0);
        return false;
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
                    Log.i(AnkiDroidApp.TAG, "mUnmountReceiver - Action = Media Eject");
                    finishNoStorageAvailable();
                }
            };
            IntentFilter iFilter = new IntentFilter();
            iFilter.addAction(Intent.ACTION_MEDIA_UNMOUNTED);

            // ACTION_MEDIA_EJECT is never invoked (probably due to an android bug
            // iFilter.addAction(Intent.ACTION_MEDIA_EJECT);
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
            // mCurrentCard.stopTimer();
        }
    }


    private void restartTimer() {
        if (mCurrentCard != null) {
            // mCurrentCard.resumeTimer();
        }
        if (mPrefTimer && mSavedTimer != 0) {
            mCardTimer.setBase(SystemClock.elapsedRealtime() - mSavedTimer);
            mCardTimer.start();
        }
    }


    private void undo() {
        setNextCardAnimation(true);
        if (mProgressDialog != null && mProgressDialog.isShowing()) {
            mProgressDialog.setMessage(getResources().getString(R.string.saving_changes));
        } else {
            mProgressDialog = StyledProgressDialog.show(Reviewer.this, "",
                    getResources().getString(R.string.saving_changes), true);
        }
        DeckTask.launchDeckTask(DeckTask.TASK_TYPE_UNDO, mAnswerCardHandler, new DeckTask.TaskData(mSched));
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
        Reviewer.this.setResult(DeckPicker.RESULT_MEDIA_EJECTED);
        finish();
    }


    private boolean editCard() {
        if (isCramming()) {
            Themes.showThemedToast(Reviewer.this, getResources().getString(R.string.cram_edit_warning), true);
            return false;
        } else {
            mInEditor = true;
            Intent editCard = new Intent(Reviewer.this, CardEditor.class);
            editCard.putExtra(CardEditor.EXTRA_CALLER, CardEditor.CALLER_REVIEWER);
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


    private void showDeleteNoteDialog() {
        Dialog dialog;
        Resources res = getResources();
        StyledDialog.Builder builder = new StyledDialog.Builder(this);
        builder.setTitle(res.getString(R.string.delete_card_title));
        builder.setIcon(android.R.drawable.ic_dialog_alert);
        builder.setMessage(String.format(res.getString(R.string.delete_note_message),
                Utils.stripHTML(mCurrentCard.getQuestion(true))));
        builder.setPositiveButton(res.getString(R.string.yes), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                setNextCardAnimation(false);
                DeckTask.launchDeckTask(DeckTask.TASK_TYPE_DISMISS_NOTE, mDismissCardHandler, new DeckTask.TaskData(
                        mSched, mCurrentCard, 3));
            }
        });
        builder.setNegativeButton(res.getString(R.string.no), null);
        dialog = builder.create();
        dialog.show();
    }


    private int getRecommendedEase(boolean easy) {
    	try {
            switch (mSched.answerButtons(mCurrentCard)) {
            case 2:
                return EASE_HARD;
            case 3:
                return easy ? EASE_MID : EASE_HARD;
            case 4:
                return easy ? EASE_EASY : EASE_MID;
            default:
                return 0;
            }    		
    	} catch (RuntimeException e) {
			AnkiDroidApp.saveExceptionReportFile(e, "Reviewer-getRecommendedEase");
            closeReviewer(DeckPicker.RESULT_DB_ERROR, true);
    		return 0;
    	}
    }

    private void answerCard(int ease) {
        if (mInAnswer) {
            return;
        }
        mIsSelecting = false;
        if (mPrefTextSelection) {
            clipboardSetText("");
            if (mLookUpIcon.getVisibility() == View.VISIBLE) {
                mLookUpIcon.setVisibility(View.GONE);
                enableViewAnimation(mLookUpIcon, ViewAnimation.fade(ViewAnimation.FADE_OUT, mFadeDuration, 0));
            }
        }
        switch (ease) {
            case EASE_FAILED:
                mChosenAnswer.setText("\u2022");
                mChosenAnswer.setTextColor(mNext1.getTextColors());
                // if ((deck.getDueCount() + deck.getNewCountToday()) == 1) {
                // mIsLastCard = true;
                // }
                break;
            case EASE_HARD:
                mChosenAnswer.setText("\u2022\u2022");
                mChosenAnswer.setTextColor(mNext2.getTextColors());
                break;
            case EASE_MID:
                mChosenAnswer.setText("\u2022\u2022\u2022");
                mChosenAnswer.setTextColor(mNext3.getTextColors());
                break;
            case EASE_EASY:
                mChosenAnswer.setText("\u2022\u2022\u2022\u2022");
                mChosenAnswer.setTextColor(mNext4.getTextColors());
                break;
        }

        // remove chosen answer hint after a while
        mTimerHandler.removeCallbacks(removeChosenAnswerText);
        mTimerHandler.postDelayed(removeChosenAnswerText, mShowChosenAnswerLength);
        Sound.stopSounds();
        mCurrentEase = ease;

        // Increment number reps counter
        mSessionCurrReps++;

        setNextCardAnimation(false);
        DeckTask.launchDeckTask(DeckTask.TASK_TYPE_ANSWER_CARD, mAnswerCardHandler, new DeckTask.TaskData(mSched,
                mCurrentCard, mCurrentEase));
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
        if (!mChangeBorderStyle) {
            ((View) findViewById(R.id.flashcard_border)).setVisibility(View.VISIBLE);
        }
        // hunt for input issue 720, like android issue 3341
        if (!AnkiDroidApp.isFroyoOrLater() && (mCard != null)) {
            mCard.setFocusableInTouchMode(true);
        }

        // Initialize swipe
        gestureDetector = new GestureDetector(new MyGestureDetector());

        mProgressBar = (ProgressBar) findViewById(R.id.flashcard_progressbar);

        // initialise shake detection
        if (mShakeEnabled) {
            mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
            mSensorManager.registerListener(mSensorListener,
                    mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_NORMAL);
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
            ((TextView) findViewById(R.id.nextTimeflip)).setVisibility(View.GONE);
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
            mSessionProgressTotalBar = (View) findViewById(R.id.daily_bar);
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
            FrameLayout.LayoutParams lp2 = new FrameLayout.LayoutParams(LayoutParams.FILL_PARENT,
                    LayoutParams.FILL_PARENT);
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
        Log.i(AnkiDroidApp.TAG,
                "Focusable = " + webView.isFocusable() + ", Focusable in touch mode = "
                        + webView.isFocusableInTouchMode());
        if (mSetScrollbarBarFading != null) {
            try {
                mSetScrollbarBarFading.invoke(webView, false);
            } catch (Throwable e) {
                Log.i(AnkiDroidApp.TAG,
                        "setScrollbarFadingEnabled could not be set due to a too low Android version (< 2.1)");
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
        mTextBarBlue.setTextColor(invert ? res.getColor(R.color.textbar_blue_color_inv) : res
                .getColor(R.color.textbar_blue_color));

        if (mSimpleCard != null) {
            mSimpleCard.setBackgroundColor(mCurrentBackgroundColor);
            mSimpleCard.setTextColor(mForegroundColor);
        }
        if (mCard != null) {
            mCard.setBackgroundColor(mCurrentBackgroundColor);        	
        }

        int fgColor = R.color.studyoptions_progressbar_frame_light;
        int bgColor = R.color.studyoptions_progressbar_background_nightmode;
        findViewById(R.id.progress_bars_border1).setBackgroundResource(fgColor);
        findViewById(R.id.progress_bars_border2).setBackgroundResource(fgColor);
        findViewById(R.id.progress_bars_back1).setBackgroundResource(bgColor);
        findViewById(R.id.progress_bars_back2).setBackgroundResource(bgColor);

    }

    private void showEaseButtons() {
        Resources res = getResources();

        // hide flipcard button
        switchVisibility(mFlipCardLayout, View.GONE);

        int buttonCount;
        try {
        	buttonCount = mSched.answerButtons(mCurrentCard);
    	} catch (RuntimeException e) {
    		AnkiDroidApp.saveExceptionReportFile(e, "Reviewer-showEaseButtons");
            closeReviewer(DeckPicker.RESULT_DB_ERROR, true);
            return;
    	}

        // Set correct label for each button
        switch (buttonCount) {
        case 2:
            mEase1.setText(res.getString(R.string.ease1_successive));
            mEase2.setText(res.getString(R.string.ease3_successive));
            switchVisibility(mEase1Layout, View.VISIBLE);
            switchVisibility(mEase2Layout, View.VISIBLE);
            mEase2Layout.requestFocus();
            mNext2.setTextColor(mNextTimeTextRecomColor);
            mEase2.setTextColor(mNextTimeTextRecomColor);
            mNext3.setTextColor(mNextTimeTextColor);
            mEase3.setTextColor(mNextTimeTextColor);
            break;
        case 3:
            mEase1.setText(res.getString(R.string.ease1_successive));
            mEase2.setText(res.getString(R.string.ease3_successive));
            mEase3.setText(res.getString(R.string.ease3_learning));
            switchVisibility(mEase1Layout, View.VISIBLE);
            switchVisibility(mEase2Layout, View.VISIBLE);
            switchVisibility(mEase3Layout, View.VISIBLE);
            mEase2Layout.requestFocus();
            mNext2.setTextColor(mNextTimeTextRecomColor);
            mEase2.setTextColor(mNextTimeTextRecomColor);
            mNext3.setTextColor(mNextTimeTextColor);
            mEase3.setTextColor(mNextTimeTextColor);
            break;
        default:
            mEase1.setText(res.getString(R.string.ease1_successive));
            mEase2.setText(res.getString(R.string.ease2_successive));
            mEase3.setText(res.getString(R.string.ease3_successive));
            mEase4.setText(res.getString(R.string.ease3_learning));
            switchVisibility(mEase1Layout, View.VISIBLE);
            switchVisibility(mEase2Layout, View.VISIBLE);
            switchVisibility(mEase3Layout, View.VISIBLE);
            switchVisibility(mEase4Layout, View.VISIBLE);
            mEase3Layout.requestFocus();
            mNext2.setTextColor(mNextTimeTextColor);
            mEase2.setTextColor(mNextTimeTextColor);
            mNext3.setTextColor(mNextTimeTextRecomColor);
            mEase3.setTextColor(mNextTimeTextRecomColor);
        }

        // Show next review time
        if (mshowNextReviewTime) {
            mNext1.setText(mSched.nextIvlStr(mCurrentCard, 1));
            mNext2.setText(mSched.nextIvlStr(mCurrentCard, 2));
        if (buttonCount > 2) {
                mNext3.setText(mSched.nextIvlStr(mCurrentCard, 3));
        }
        if (buttonCount > 3) {
                mNext4.setText(mSched.nextIvlStr(mCurrentCard, 4));
        }
        }
    }


    private void hideEaseButtons() {
        switchVisibility(mEase1Layout, View.GONE);
        switchVisibility(mEase2Layout, View.GONE);
        switchVisibility(mEase3Layout, View.GONE);
        switchVisibility(mEase4Layout, View.GONE);

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
                enableViewAnimation(view,
                        ViewAnimation.fade(ViewAnimation.FADE_IN, duration, mShowAnimations ? duration : 0));
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
        SharedPreferences preferences = AnkiDroidApp.getSharedPrefs(getBaseContext());
        mPrefHideDueCount = preferences.getBoolean("hideDueCount", false);
        mPrefOvertime = preferences.getBoolean("overtime", true);
        mPrefTimer = preferences.getBoolean("timer", true);
        mPrefWhiteboard = preferences.getBoolean("whiteboard", false);
        mPrefWriteAnswers = preferences.getBoolean("writeAnswers", true);
        mPrefTextSelection = preferences.getBoolean("textSelection", true);
        mLongClickWorkaround = preferences.getBoolean("textSelectionLongclickWorkaround", false);
        // mDeckFilename = preferences.getString("deckFilename", "");
        mNightMode = preferences.getBoolean("invertedColors", false);
        mInvertedColors = mNightMode;
        mBlackWhiteboard = preferences.getBoolean("blackWhiteboard", true);
        mSwapQA = preferences.getBoolean("swapqa", false);
        mPrefFullscreenReview = preferences.getBoolean("fullscreenReview", false);
        mshowNextReviewTime = preferences.getBoolean("showNextReviewTime", true);
        mZoomEnabled = preferences.getBoolean("zoom", false);
        mDisplayFontSize = preferences.getInt("relativeDisplayFontSize", 100);// Card.DEFAULT_FONT_SIZE_RATIO);
        mRelativeButtonSize = preferences.getInt("answerButtonSize", 100);
        mInputWorkaround = preferences.getBoolean("inputWorkaround", false);
        mPrefFixHebrew = preferences.getBoolean("fixHebrewText", false);
        mPrefFixArabic = preferences.getBoolean("fixArabicText", false);
        mSpeakText = preferences.getBoolean("tts", false);
        mPlaySoundsAtStart = preferences.getBoolean("playSoundsAtStart", true);
        mShowProgressBars = preferences.getBoolean("progressBars", true);
        mPrefUseTimer = preferences.getBoolean("timeoutAnswer", false);
        mWaitAnswerSecond = preferences.getInt("timeoutAnswerSeconds", 20);
        mWaitQuestionSecond = preferences.getInt("timeoutQuestionSeconds", 60);
        mScrollingButtons = preferences.getBoolean("scrolling_buttons", false);
        mDoubleScrolling = preferences.getBoolean("double_scrolling", false);

        mGesturesEnabled = AnkiDroidApp.initiateGestures(this, preferences);
        if (mGesturesEnabled) {
            mGestureShake = Integer.parseInt(preferences.getString("gestureShake", "0"));
            if (mGestureShake != 0) {
                mShakeEnabled = true;
            }
            mShakeIntensity = preferences.getInt("minShakeIntensity", 70);

            mGestureSwipeUp = Integer.parseInt(preferences.getString("gestureSwipeUp", "9"));
            mGestureSwipeDown = Integer.parseInt(preferences.getString("gestureSwipeDown", "0"));
            mGestureSwipeLeft = Integer.parseInt(preferences.getString("gestureSwipeLeft", "8"));
            mGestureSwipeRight = Integer.parseInt(preferences.getString("gestureSwipeRight", "17"));
            mGestureDoubleTap = Integer.parseInt(preferences.getString("gestureDoubleTap", "7"));
            mGestureTapLeft = Integer.parseInt(preferences.getString("gestureTapLeft", "3"));
            mGestureTapRight = Integer.parseInt(preferences.getString("gestureTapRight", "6"));
            mGestureTapTop = Integer.parseInt(preferences.getString("gestureTapTop", "12"));
            mGestureTapBottom = Integer.parseInt(preferences.getString("gestureTapBottom", "2"));
            mGestureLongclick = Integer.parseInt(preferences.getString("gestureLongclick", "11"));
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
        if (mSimpleInterface) {
        	String tags = preferences.getString("simpleInterfaceExcludeTags", "").replace(",", " ");
        	mSimpleInterfaceExcludeTags = new ArrayList<String>();
        	for (String t : tags.split(" ")) {
        		if (t.length() > 0) {
        			mSimpleInterfaceExcludeTags.add(t);
        		}
        	}
        }

        return preferences;
    }

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
	            mSimpleCard = new TextView(this);
	            Themes.setRegularFont(mSimpleCard);
	            mSimpleCard.setTextSize(mSimpleCard.getTextSize() * mDisplayFontSize / 100);
	            mSimpleCard.setGravity(Gravity.CENTER);
	            try {
	                mSetTextIsSelectable = TextView.class.getMethod("setTextIsSelectable", boolean.class);
	            } catch (Throwable e) {
	                Log.i(AnkiDroidApp.TAG,
	                        "mSetTextIsSelectable could not be found due to a too low Android version (< 3.0)");
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

    private void setDueMessage() {
        // Decks deck = DeckManager.getMainDeck();
        // if (mCurrentCard != null && deck != null && deck.getScheduler().equals("reviewEarly") &&
        // mCurrentCard.getType() != Card.TYPE_FAILED) {
        // mChosenAnswer.setTextColor(mForegroundColor);
        // mChosenAnswer.setText(Utils.fmtTimeSpan(mCurrentCard.getCombinedDue() - Utils.now(), Utils.TIME_FORMAT_IN));
        // }
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

        try {
            String[] title = mSched.getCol().getDecks().get(mCurrentCard.getDid()).getString("name").split("::");
            setTitle(title[title.length - 1]);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }

        int[] counts = mSched.counts(mCurrentCard);

        int eta = mSched.eta(counts, false);
        UIUtils.setActionBarSubtitle(this, getResources().getQuantityString(R.plurals.reviewer_window_title, eta, eta));

        //SharedPreferences preferences = PrefSettings.getSharedPrefs(getBaseContext());
        //boolean hideDueCount = preferences.getBoolean("hideDueCount", true);

        SpannableString newCount = new SpannableString(String.valueOf(counts[0]));
        SpannableString lrnCount = new SpannableString(String.valueOf(counts[1]));
        SpannableString revCount = new SpannableString(String.valueOf(counts[2]));
        if (mPrefHideDueCount) {
            revCount = new SpannableString("???");
        }

        switch (mCurrentCard.getQueue()) {
            case Card.TYPE_NEW:
                newCount.setSpan(new UnderlineSpan(), 0, newCount.length(), 0);
                break;
            case Card.TYPE_LRN:
                lrnCount.setSpan(new UnderlineSpan(), 0, lrnCount.length(), 0);
                break;
            case Card.TYPE_REV:
                revCount.setSpan(new UnderlineSpan(), 0, revCount.length(), 0);
                break;
        }

        mTextBarRed.setText(newCount);
        mTextBarBlack.setText(lrnCount);
        mTextBarBlue.setText(revCount);
    }


    private void updateStatisticBars() {
        if (mStatisticBarsMax == 0) {
            View view = findViewById(R.id.progress_bars_back1);
            mStatisticBarsMax = view.getWidth();
            mStatisticBarsHeight = view.getHeight();
        }
        float[] progress = mSched.progressToday(null, mCurrentCard, false);
        Utils.updateProgressBars(mSessionProgressBar,
                (int) (mStatisticBarsMax * progress[0]), mStatisticBarsHeight);
        Utils.updateProgressBars(mSessionProgressTotalBar,
                (int) (mStatisticBarsMax * progress[1]), mStatisticBarsHeight);
    }

    /*
     * Handler for the delay in auto showing question and/or answer One toggle for both question and answer, could set
     * longer delay for auto next question
     */
    private Handler mTimeoutHandler = new Handler();

    private Runnable mShowQuestionTask = new Runnable() {
        public void run() {
            // Assume hitting the "Again" button when auto next question
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
            if (mFlipCardLayout.isEnabled() == true && mFlipCardLayout.getVisibility() == View.VISIBLE) {
                mFlipCardLayout.performClick();
            }
        }
    };


    private void displayCardQuestion() {
        sDisplayAnswer = false;

        if (mButtonHeight == 0 && mRelativeButtonSize != 100) {
            mButtonHeight = mFlipCard.getHeight() * mRelativeButtonSize / 100;
            mFlipCard.setHeight(mButtonHeight);
            mEase1.setHeight(mButtonHeight);
            mEase2.setHeight(mButtonHeight);
            mEase3.setHeight(mButtonHeight);
            mEase4.setHeight(mButtonHeight);
        }

        setInterface();

        String question = mCurrentCard.getQuestion(mCurrentSimpleInterface);
        // preventing rendering {{type:Field}} if type answer is not enabled in preferences
        //if (typeAnswer()) {
            question = typeAnsQuestionFilter(question);
        //}
        updateMenuItems();

        if (mPrefFixArabic) {
            question = ArabicUtilities.reshapeSentence(question, true);
        }

        Log.i(AnkiDroidApp.TAG, "question: '" + question + "'");

        String displayString = "";

        if (mCurrentSimpleInterface) {
            mCardContent = Html.fromHtml(question);
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

            if (mSpeakText && AnkiDroidApp.isDonutOrLater()) {
                // ReadText.setLanguageInformation(Model.getModel(DeckManager.getMainDeck(),
                // mCurrentCard.getCardModelId(), false).getId(), mCurrentCard.getCardModelId());
            }
        }

        updateCard(displayString);
        hideEaseButtons();

        // If the user want to show answer automatically
        if (mPrefUseTimer) {
            mTimeoutHandler.removeCallbacks(mShowAnswerTask);
            mTimeoutHandler.postDelayed(mShowAnswerTask, mWaitAnswerSecond * 1000);
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

        String answer = mCurrentCard.getAnswer(mCurrentSimpleInterface);
        // preventing rendering {{type:Field}} if type answer is not enabled in preferences
        //if (typeAnswer()) {
            answer = typeAnsAnswerFilter(answer);
        //}

        String displayString = "";

        if (mCurrentSimpleInterface) {
            mCardContent = Html.fromHtml(answer);
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
                    Log.i(AnkiDroidApp.TAG, "correct answer = " + correctAnswer);

                    // Obtain the diff and send it to updateCard
                    DiffEngine diff = new DiffEngine();

                    StringBuffer span = new StringBuffer();
                    span.append("<span style=\"font-family: '").append(mTypeFont)
                    .append("'; font-size: ").append(mTypeSize).append("px\">");
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
        }

        mIsSelecting = false;
        updateCard(displayString);
        showEaseButtons();

        // If the user want to show next question automatically
        if (mPrefUseTimer) {
            mTimeoutHandler.removeCallbacks(mShowQuestionTask);
            mTimeoutHandler.postDelayed(mShowQuestionTask, mWaitQuestionSecond * 1000);
        }
    }


    private void updateCard(String content) {
        Log.i(AnkiDroidApp.TAG, "updateCard");

        if (mCurrentSimpleInterface) {
            fillFlashcard(mShowAnimations);
            return;
        }

        // mBaseUrl = Utils.getBaseUrl();

        // Check whether there is a hard coded font-size in the content and apply the relative font size
        // Check needs to be done before CSS is applied to content;
        content = recalculateHardCodedFontSize(content, mDisplayFontSize);

        // Add CSS for font color and font size
        if (mCurrentCard == null) {
            mCard.getSettings().setDefaultFontSize(calculateDynamicFontSize(content));
        }

        // Log.i(AnkiDroidApp.TAG, "Initial content card = \n" + content);
        // content = Image.parseImages(deckFilename, content);
        // Log.i(AnkiDroidApp.TAG, "content after parsing images = \n" +
        // content);

        // don't play question sound again when displaying answer
        int questionStartsAt = content.indexOf("<a name=\"question\"></a><hr/>");
        String question = "";
        String answer = "";

        Sound.resetSounds();

        int qa = MetaDB.LANGUAGES_QA_QUESTION;
        if (sDisplayAnswer) {
            qa = MetaDB.LANGUAGES_QA_ANSWER;
        }
        answer = Sound.parseSounds(mBaseUrl, content, mSpeakText, qa);

        content = question + answer;

        // In order to display the bold style correctly, we have to change
        // font-weight to 700
        content = content.replace("font-weight:600;", "font-weight:700;");

        // Find hebrew text
        if (isHebrewFixEnabled()) {
            content = applyFixForHebrew(content);
        }
//
//        // Chess notation FEN handling
//        if (this.isFenConversionEnabled()) {
//            content = fenToChessboard(content);
//        }

        Log.i(AnkiDroidApp.TAG, "content card = \n" + content);
        StringBuilder style = new StringBuilder();
        style.append(mCustomFontStyle);
        // style.append(getDeckStyle(mCurrentCard.mDeck.getDeckPath()));
        Log.i(AnkiDroidApp.TAG, "::style::" + style);

        if (mNightMode) {
            content = Models.invertColors(content);
        }
        // Calculate available width and provide it to javascript for image resizing.
        mCardTemplate = mCardTemplate.replaceFirst("var availableWidth = \\d*;", 
                String.format(Locale.US, "var availableWidth = %d;", getAvailableWidthInCard()));

        mCardContent = new SpannedString(mCardTemplate.replace("::content::", content).replace("::style::",
                style.toString()));
        // Log.i(AnkiDroidApp.TAG, "card html = \n" + card);
        Log.i(AnkiDroidApp.TAG, "base url = " + mBaseUrl);

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
        if (!mSpeakText) {
            Sound.playSounds(qa);
        } else {
            if (sDisplayAnswer) {
                ReadText.textToSpeech(Utils.stripHTML(mCurrentCard.getAnswer(mCurrentSimpleInterface)), qa);
            } else {
                ReadText.textToSpeech(Utils.stripHTML(mCurrentCard.getQuestion(mCurrentSimpleInterface)), qa);
            }
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
        mNextAnimation = reverse ? ANIMATION_SLIDE_OUT_TO_RIGHT : ANIMATION_SLIDE_OUT_TO_LEFT;
        if (mCardContainer.getVisibility() == View.VISIBLE && mShowAnimations) {
            fillFlashcard(true);
        }
    }


    public void fillFlashcard(boolean flip) {
        if (!flip) {
            Log.i(AnkiDroidApp.TAG, "base url = " + mBaseUrl);
            if (mCurrentSimpleInterface) {
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
                    rotation = new Animation3D(mCardContainer.getWidth(), mCardContainer.getHeight(), 9,
                            Animation3D.ANIMATION_TURN, true, true, this);
                    rotation.setDuration(mAnimationDurationTurn);
                    rotation.setInterpolator(new AccelerateDecelerateInterpolator());
                    break;
                case ANIMATION_NEXT_CARD_FROM_LEFT:
                    directionToLeft = false;
                case ANIMATION_NEXT_CARD_FROM_RIGHT:
                    rotation = new Animation3D(mCardContainer.getWidth(), mCardContainer.getHeight(), 0,
                            Animation3D.ANIMATION_EXCHANGE_CARD, directionToLeft, true, this);
                    rotation.setDuration(mAnimationDurationMove);
                    rotation.setInterpolator(new AccelerateDecelerateInterpolator());
                    break;
                case ANIMATION_SLIDE_OUT_TO_RIGHT:
                    directionToLeft = false;
                case ANIMATION_SLIDE_OUT_TO_LEFT:
                    fillFlashcard(false);
                    rotation = new Animation3D(mCardContainer.getWidth(), mCardContainer.getHeight(), 0,
                            Animation3D.ANIMATION_SLIDE_OUT_CARD, directionToLeft, true, this);
                    rotation.setDuration(mAnimationDurationMove);
                    rotation.setInterpolator(new AccelerateInterpolator());
                    switchTopBarVisibility(View.INVISIBLE);
                    break;
                case ANIMATION_SLIDE_IN_FROM_LEFT:
                    directionToLeft = false;
                case ANIMATION_SLIDE_IN_FROM_RIGHT:
                    fillFlashcard(false);
                    rotation = new Animation3D(mCardContainer.getWidth(), mCardContainer.getHeight(), 0,
                            Animation3D.ANIMATION_SLIDE_IN_CARD, directionToLeft, true, this);
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


    private String getDeckStyle(String deckPath) {
        File styleFile = new File(Utils.removeExtension(deckPath) + ".css");
        if (!styleFile.exists() || !styleFile.canRead()) {
            return "";
        }
        StringBuilder style = new StringBuilder();
        try {
            BufferedReader styleReader = new BufferedReader(new InputStreamReader(new FileInputStream(styleFile)));
            try {
                while (true) {
                    String line = styleReader.readLine();
                    if (line == null) {
                        break;
                    }
                    style.append(line);
                    style.append('\n');
                }
            } finally {
                styleReader.close();
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
     * Each font is mapped to the font family by the same name as the name of the font fint without the extension.
     */
    private String getCustomFontsStyle() {
        StringBuilder builder = new StringBuilder();
        for (AnkiFont font : mCustomFontFiles) {
//            File fontfile = new File(fontPath);
//            Typeface tf = Typeface.createFromAsset(getAssets(), "fonts/" + fontfile.getName());
//            if (tf.isBold()) {
//                weight = "font-weight: bold;";
//            } else {
//                weight = "font-weight: normal;";
//            }
//            if (tf.isItalic()) {
//                style = "font-style: italic;";
//            } else {
//                style = "font-style: normal;";
//            }
//            family = Utils.removeExtension(fontfile.getName()).replaceAll("-.*$", "");
//            String fontFace = String.format("@font-face {font-family: \"%s\"; %s %s src: url(\"file://%s\");}",
//                    family, weight, style, fontPath);
//            Log.d(AnkiDroidApp.TAG, "adding to style: " + fontFace);
            builder.append(font.getStyle());
            builder.append('\n');
        }
        return builder.toString();
    }


    /** Returns the CSS used to set the default font. */
    private String getDefaultFontStyle() {
        if (mCustomDefaultFontCss == null) {
            SharedPreferences preferences = AnkiDroidApp.getSharedPrefs(getBaseContext());
            String defaultFont = preferences.getString("defaultFont", null);
            if (defaultFont == null || "".equals(defaultFont)) {
                defaultFont = Themes.getReviewerFontName();
                if (defaultFont == null || "".equals(defaultFont)) {
                    mCustomDefaultFontCss = "";
                } else {
                    mCustomDefaultFontCss = "BODY .question BODY .answer { font-family: '" + defaultFont + "' font-weight: normal; font-style: normal; font-stretch: normal; }\n";
                }
            } else {
                mCustomDefaultFontCss = "BODY .question, BODY .answer { font-family: '" + defaultFont + "' font-weight: normal; font-style: normal; font-stretch: normal; }\n";
            }
        }
        return mCustomDefaultFontCss;
    }


    public static Card getEditorCard() {
        return sEditorCard;
    }


    private boolean isHebrewFixEnabled() {
        return mPrefFixHebrew;
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
     * Parses content in question and answer to see, whether someone has hard coded the font size in a card layout. If
     * this is so, then the font size must be replaced with one corrected by the relative font size. If a relative CSS
     * unit measure is used (e.g. 'em'), then only hierarchy in 'span' tag is taken into account.
     * 
     * @param content
     * @param percentage - the relative font size percentage defined in preferences
     * @return
     */
    private String recalculateHardCodedFontSize(String content, int percentage) {
        if (null == content || 0 == content.trim().length()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(content);

        boolean fontSizeFound = true; // whether the previous loop found a valid font-size attribute
        int spanTagDepth = 0; // to find out whether a relative CSS unit measure is within another one
        int outerRelativeSpanTagDepth = 100; // the hierarchy depth of the current outer relative span
        int start = 0;
        int posSpan = 0;
        int posFontSize = 0;
        int posUnit = 0;
        int intSize; // for absolute css measurement values
        double doubleSize; // for relative css measurement values
        boolean isRelativeUnit = true; // true if em or %
        String sizeS;

        // formatter for decimal numbers
        DecimalFormatSymbols symbols = new DecimalFormatSymbols();
        symbols.setDecimalSeparator('.');
        DecimalFormat dFormat = new DecimalFormat("0.##", symbols);

        while (fontSizeFound) {
            posFontSize = sb.indexOf("font-size:", start);
            if (-1 == posFontSize) {
                fontSizeFound = false;
                continue;
            } else {
                // check whether </span> are found and decrease spanTagDepth accordingly
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
                // only absolute and relative measures are taken into account. E.g. 'xx-small', 'inherit' etc. are not
                // taken into account
                fontSizeFound = false;
                continue;
            } else if (17 < (posUnit - posFontSize)) { // assuming max 1 blank and 5 digits
                // only take into account if font-size measurement is close, because theoretically "font-size:" could be
                // part of text
                continue;
            } else {
                spanTagDepth += 1; // because we assume that font-sizes always are declared in span tags
                start = posUnit + 3; // needs to be more than posPx due to decimals
                sizeS = sb.substring(posFontSize + 10, posUnit).trim();
                if (isRelativeUnit) {
                    if (outerRelativeSpanTagDepth >= spanTagDepth) {
                        outerRelativeSpanTagDepth = spanTagDepth;
                        try {
                            doubleSize = dFormat.parse(sizeS).doubleValue();
                        } catch (ParseException e) {
                            continue; // ignore this one
                        }
                        doubleSize = doubleSize * percentage / 100;
                        sizeS = dFormat.format(doubleSize);
                    } // else do nothing as relative sizes within relative sizes should not be changed
                } else {
                    try {
                        intSize = Integer.parseInt(sizeS);
                    } catch (NumberFormatException e) {
                        start = posFontSize + 10;
                        continue; // ignore this one
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
     * @return true if the AnkiDroid preference for writing answer is true and if the Anki Deck CardLayout specifies a
     *         field to query
     */
    private final boolean typeAnswer() {
        if (mPrefWriteAnswers && null != mTypeCorrect) {
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
        return Math.max(DYNAMIC_FONT_MIN_SIZE, DYNAMIC_FONT_MAX_SIZE
                - (int) (realContent.length() / DYNAMIC_FONT_FACTOR));
    }


    private void unblockControls() {
        mCardFrame.setEnabled(true);
        mFlipCardLayout.setEnabled(true);

        switch (mCurrentEase) {
            case EASE_FAILED:
                mEase1Layout.setClickable(true);
                mEase2Layout.setEnabled(true);
                mEase3Layout.setEnabled(true);
                mEase4Layout.setEnabled(true);
                break;

            case EASE_HARD:
                mEase1Layout.setEnabled(true);
                mEase2Layout.setClickable(true);
                mEase3Layout.setEnabled(true);
                mEase4Layout.setEnabled(true);
                break;

            case EASE_MID:
                mEase1Layout.setEnabled(true);
                mEase2Layout.setEnabled(true);
                mEase3Layout.setClickable(true);
                mEase4Layout.setEnabled(true);
                break;

            case EASE_EASY:
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
        mInAnswer = false;
    }


    private void blockControls() {
        mCardFrame.setEnabled(false);
        mFlipCardLayout.setEnabled(false);
        mTouchLayer.setVisibility(View.INVISIBLE);
        mInAnswer = true;

        switch (mCurrentEase) {
            case EASE_FAILED:
                mEase1Layout.setClickable(false);
                mEase2Layout.setEnabled(false);
                mEase3Layout.setEnabled(false);
                mEase4Layout.setEnabled(false);
                break;

            case EASE_HARD:
                mEase1Layout.setEnabled(false);
                mEase2Layout.setClickable(false);
                mEase3Layout.setEnabled(false);
                mEase4Layout.setEnabled(false);
                break;

            case EASE_MID:
                mEase1Layout.setEnabled(false);
                mEase2Layout.setEnabled(false);
                mEase3Layout.setClickable(false);
                mEase4Layout.setEnabled(false);
                break;

            case EASE_EASY:
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
     * Select Text in the webview and automatically sends the selected text to the clipboard. From
     * http://cosmez.blogspot.com/2010/04/webview-emulateshiftheld-on-android.html
     */
    private void selectAndCopyText() {
        try {
            KeyEvent shiftPressEvent = new KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SHIFT_LEFT, 0, 0);
            if (mCurrentSimpleInterface) {
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
        for (String s : new String[] { "nook" }) {
            if (android.os.Build.DEVICE.toLowerCase().indexOf(s) != -1
                    || android.os.Build.MODEL.toLowerCase().indexOf(s) != -1) {
                return true;
            }
        }
        try {
            // this must not be executed on nook (causes fc)
            mSetScrollbarBarFading = WebView.class.getMethod("setScrollbarFadingEnabled", boolean.class);
        } catch (Throwable e) {
            Log.i(AnkiDroidApp.TAG,
                    "setScrollbarFadingEnabled could not be found due to a too low Android version (< 2.1)");
        }
        if (mCustomFontFiles.size() != 0) {
            return true;
        }
        return false;
    }


    /**
     * Setup media. Try to detect if we're using dropbox and set the mediaPrefix accordingly. Then set the media
     * directory.
     * 
     * @param deck The deck that we've just opened
     */
    // public static String setupMedia(Decks deck) {
    // String mediaLoc = deck.getStringVar("mediaURL");
    // if (mediaLoc != null) {
    // mediaLoc = mediaLoc.replace("\\", "/");
    // if (mediaLoc.contains("/Dropbox/Public/Anki")) {
    // // We're using dropbox
    // deck.setMediaPrefix(AnkiDroidApp.getDropboxDir());
    // }
    // }
    // return deck.mediaDir();
    // }

    private String applyFixForHebrew(String text) {
        Matcher m = sHebrewPattern.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String hebrewText = m.group();
            // Some processing before we reverse the Hebrew text
            // 1. Remove all Hebrew vowels as they cannot be displayed properly
            Matcher mv = sHebrewVowelsPattern.matcher(hebrewText);
            hebrewText = mv.replaceAll("");
            // 2. Flip open parentheses, brackets and curly brackets with closed
            // ones and vice-versa
            // Matcher mp = sBracketsPattern.matcher(hebrewText);
            // StringBuffer sbg = new StringBuffer();
            // int bracket[] = new int[1];
            // while (mp.find()) {
            // bracket[0] = mp.group().codePointAt(0);
            // if ((bracket[0] & 0x28) == 0x28) {
            // // flip open/close ( and )
            // bracket[0] ^= 0x01;
            // } else if (bracket[0] == 0x5B || bracket[0] == 0x5D || bracket[0]
            // == 0x7B || bracket[0] == 0x7D) {
            // // flip open/close [, ], { and }
            // bracket[0] ^= 0x06;
            // }
            // mp.appendReplacement(sbg, new String(bracket, 0, 1));
            // }
            // mp.appendTail(sbg);
            // hebrewText = sbg.toString();
            // for (int i = 0; i < hebrewText.length(); i++) {
            // Log.i(AnkiDroidApp.TAG, "flipped brackets: " +
            // hebrewText.codePointAt(i));
            // }
            // 3. Reverse all numerical groups (so when they get reversed again
            // they show LTR)
            // Matcher mn = sNumeralsPattern.matcher(hebrewText);
            // sbg = new StringBuffer();
            // while (mn.find()) {
            // StringBuffer sbn = new StringBuffer(m.group());
            // mn.appendReplacement(sbg, sbn.reverse().toString());
            // }
            // mn.appendTail(sbg);

            // for (int i = 0; i < sbg.length(); i++) {
            // Log.i(AnkiDroidApp.TAG, "LTR numerals: " + sbg.codePointAt(i));
            // }
            // hebrewText = sbg.toString();//reverse().toString();
            m.appendReplacement(sb, hebrewText);
        }
        m.appendTail(sb);
        return sb.toString();
    }


    private void executeCommand(int which) {
        switch (which) {
            case GESTURE_NOTHING:
                break;
            case GESTURE_SHOW_ANSWER:
                if (!sDisplayAnswer) {
                    displayCardAnswer();
                }
                break;
            case GESTURE_ANSWER_EASE1:
                if (sDisplayAnswer) {
                    answerCard(EASE_FAILED);
                } else {
                    displayCardAnswer();
                }
                break;
            case GESTURE_ANSWER_EASE2:
                if (sDisplayAnswer) {
                    answerCard(EASE_HARD);
                } else {
                    displayCardAnswer();
                }
                break;
            case GESTURE_ANSWER_EASE3:
                if (sDisplayAnswer) {
                    answerCard(EASE_MID);
                } else {
                    displayCardAnswer();
                }
                break;
            case GESTURE_ANSWER_EASE4:
                if (sDisplayAnswer) {
                    answerCard(EASE_EASY);
                } else {
                    displayCardAnswer();
                }
                break;
            case GESTURE_ANSWER_RECOMMENDED:
                if (sDisplayAnswer) {
                    answerCard(getRecommendedEase(false));
                } else {
                    displayCardAnswer();
                }
                break;
            case GESTURE_ANSWER_BETTER_THAN_RECOMMENDED:
                if (sDisplayAnswer) {
                    answerCard(getRecommendedEase(true));
                }
                break;
            case GESTURE_EXIT:
                closeReviewer(RESULT_DEFAULT, false);
                break;
            case GESTURE_UNDO:
            	if(mSched.getCol().undoAvailable()) {
                	undo();            		
            	}
            	break;
            case GESTURE_EDIT:
                editCard();
                break;
     		case GESTURE_MARK:
     			DeckTask.launchDeckTask(DeckTask.TASK_TYPE_MARK_CARD, mMarkCardHandler, new DeckTask.TaskData(mSched, mCurrentCard, 0));
     			break;
            case GESTURE_LOOKUP:
                lookUpOrSelectText();
                break;
         	case GESTURE_BURY:
                setNextCardAnimation(false);
                DeckTask.launchDeckTask(DeckTask.TASK_TYPE_DISMISS_NOTE, mDismissCardHandler, new DeckTask.TaskData(
                        mSched, mCurrentCard, 0));
                break;
         	case GESTURE_SUSPEND:
                setNextCardAnimation(false);
                DeckTask.launchDeckTask(DeckTask.TASK_TYPE_DISMISS_NOTE, mDismissCardHandler, new DeckTask.TaskData(
                        mSched, mCurrentCard, 1));
                break;
            case GESTURE_DELETE:
                showDeleteNoteDialog();
                break;
            case GESTURE_CLEAR_WHITEBOARD:
                if (mPrefWhiteboard) {
                    mWhiteboard.clear();
                }
                break;
            case GESTURE_PLAY_MEDIA:
                playSounds();
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

        JavaScriptInterface() {
        }


        /**
         * This is not called on the UI thread. Send a message that will be handled on the UI thread.
         */
        public void playSound(String soundPath) {
            Message msg = Message.obtain();
            msg.obj = soundPath;
            mHandler.sendMessage(msg);
        }
    }


    private void closeReviewer(int result, boolean saveDeck) {
        Collection.currentCollection().setOvertime(false);
        mTimeoutHandler.removeCallbacks(mShowAnswerTask);
        mTimeoutHandler.removeCallbacks(mShowQuestionTask);
        mTimerHandler.removeCallbacks(removeChosenAnswerText);
        longClickHandler.removeCallbacks(longClickTestRunnable);
        longClickHandler.removeCallbacks(startLongClickAction);

        Reviewer.this.setResult(result);

        setOutAnimation(true);

        // updateBigWidget(!mCardFrame.isEnabled());

        // if (saveDeck) {
        // DeckTask.launchDeckTask(DeckTask.TASK_TYPE_SAVE_DECK, mSaveAndResetDeckHandler, new
        // DeckTask.TaskData(DeckManager.getMainDeck(), 0));
        // } else {
        if (saveDeck) {
            UIUtils.saveCollectionInBackground(mSched.getCol());
        }
        finish();
        if (UIUtils.getApiLevel() > 4) {
            ActivityTransitionAnimation.slide(Reviewer.this, ActivityTransitionAnimation.RIGHT);
        }
        // }
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
    }

    class MyGestureDetector extends SimpleOnGestureListener {
        private boolean mIsXScrolling = false;
        private boolean mIsYScrolling = false;


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
                    mIsXScrolling = false;
                    mIsYScrolling = false;
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
            if (!mCurrentSimpleInterface) {
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

}
