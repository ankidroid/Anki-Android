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
import android.graphics.drawable.Drawable;
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
import android.text.Editable;
import android.text.Html;
import android.text.Html.TagHandler;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.SpannedString;
import android.text.TextUtils;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;
import android.util.Log;
import android.util.TypedValue;
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
import com.ichi2.anki.receiver.SdCardReceiver;
import com.ichi2.async.DeckTask;
import com.ichi2.libanki.Card;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Note;
import com.ichi2.libanki.Sched;
import com.ichi2.libanki.Sound;
import com.ichi2.libanki.Utils;
import com.ichi2.themes.HtmlColors;
import com.ichi2.themes.StyledDialog;
import com.ichi2.themes.StyledOpenCollectionDialog;
import com.ichi2.themes.StyledProgressDialog;
import com.ichi2.themes.Themes;
import com.ichi2.utils.DiffEngine;
import com.ichi2.widget.WidgetStatus;

import org.amr.arabic.ArabicUtilities;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.xml.sax.XMLReader;

import java.io.File;
import java.io.FileOutputStream;
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

public class Reviewer extends AnkiActivity {

    /**
     * Whether to save the content of the card in the file system.
     * <p>
     * Set this to true for debugging only.
     */
    private static final boolean SAVE_CARD_CONTENT = false;

    /**
     * Result codes that are returned when this activity finishes.
     */
    public static final int RESULT_DEFAULT = 50;
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

    /** to be sento to and from the card editor */
    private static Card sEditorCard;

    private static boolean sDisplayAnswer = false;

    /** The percentage of the absolute font size specified in the deck. */
    private int mDisplayFontSize = 100;

    /** Pattern for font-size style declarations */
    private static final Pattern fFontSizePattern = Pattern.compile(
            "font-size\\s*:\\s*([0-9.]+)\\s*((?:px|pt|in|cm|mm|pc|%|em))\\s*;?", Pattern.CASE_INSENSITIVE);
    /** Pattern for opening/closing span/div tags */
    private static final Pattern fSpanDivPattern = Pattern.compile(
            "<(/?)(span|div)", Pattern.CASE_INSENSITIVE);
    /** The relative CSS measurement units for pattern search */
    private static final Set<String> fRelativeCssUnits = new HashSet<String>(
            Arrays.asList(new String[]{ "%", "em" }));

    /**
     * Broadcast that informs us when the sd card is about to be unmounted
     */
    private BroadcastReceiver mUnmountReceiver = null;

    private boolean mInBackground = false;

    /**
     * Variables to hold preferences
     */
    private boolean mPrefHideDueCount;
    private boolean mShowTimer;
    private boolean mPrefWhiteboard;
    private boolean mPrefWriteAnswers;
    private boolean mPrefTextSelection;
    private boolean mInputWorkaround;
    private boolean mLongClickWorkaround;
    private boolean mPrefFullscreenReview;
    private boolean mZoomEnabled;
    private String mCollectionFilename;
    private int mRelativeButtonSize;
    private boolean mDoubleScrolling;
    private boolean mScrollingButtons;
    private boolean mGesturesEnabled;
    private boolean mShakeEnabled = false;
    private int mShakeIntensity;
    private boolean mShakeActionStarted = false;
    private boolean mPrefFixArabic;
    private boolean mPrefForceQuickUpdate;
    // Android WebView
    private boolean mSpeakText;
    private boolean mInvertedColors = false;
    private int mCurrentBackgroundColor;
    private boolean mBlackWhiteboard = true;
    private boolean mNightMode = false;
    private boolean mShowProgressBars;
    private boolean mPrefFadeScrollbars;
    private boolean mPrefUseTimer;
    private boolean mPrefCenterVertically;
    private boolean mShowAnimations = false;
    private boolean mSimpleInterface = false;
    private boolean mCurrentSimpleInterface = false;
    private ArrayList<String> mSimpleInterfaceExcludeTags;
    private int mAvailableInCardWidth;

    // Preferences from the collection
    private boolean mShowNextReviewTime;
    private boolean mShowRemainingCardCount;

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
    private Bundle mSavedInstanceState;
    private ProgressBar mProgressBar;

    private Card mCurrentCard;
    private int mCurrentEase;

    private boolean mShowWhiteboard = false;

    private int mNextTimeTextColor;
    private int mNextTimeTextRecomColor;

    private int mForegroundColor;
    private boolean mChangeBorderStyle;

    private int mButtonHeight = 0;

    private boolean mConfigurationChanged = false;
    private int mShowChosenAnswerLength = 2000;

    private int mStatisticBarsMax;
    private int mStatisticBarsHeight;

    private long mSavedTimer = 0;

    /**
     * Whether to use a single {@link WebView} and update its content.
     *
     * <p>If false, we will instead use two WebViews and switch them when changing the content. This is needed because
     * of a bug in some versions of Android.
     */
    private boolean mUseQuickUpdate = false;
    /**
     * Maps font names into {@link AnkiFont} objects corresponding to them.
     *
     * <p>Should not be accessed directly but via {@link #getCustomFontsMap()}, as it is lazily initialized.
     */
    private Map<String, AnkiFont> mCustomFontsMap;
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

    private boolean mIsXScrolling = false;
    private boolean mIsYScrolling = false;

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
            if (mProgressDialog.isShowing()) {
                mProgressDialog.dismiss();
            }
        }


        @Override
        public void onPostExecute(DeckTask.TaskData result) {
            refreshActionBar();
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

            if (sDisplayAnswer) {
                displayCardAnswer();
            } else {
                displayCardQuestion();
                initTimer();
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
                closeReviewer(RESULT_NO_MORE_CARDS, true);
            }
            mShakeActionStarted = false;
        }
    };

    private DeckTask.TaskListener mAnswerCardHandler = new DeckTask.TaskListener() {
        private boolean mNoMoreCards;


        @Override
        public void onPreExecute() {
            mProgressBar.setVisibility(View.VISIBLE);
            mCardTimer.stop();
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

            mCurrentCard = values[0].getCard();
            if (mCurrentCard == null) {
                // If the card is null means that there are no more cards scheduled for review.
                mNoMoreCards = true;
                mProgressDialog = StyledProgressDialog.show(Reviewer.this, "",
                        getResources().getString(R.string.saving_changes), true);
                setOutAnimation(false);
            } else {
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

            // Since reps are incremented on fetch of next card, we will miss counting the
            // last rep since there isn't a next card. We manually account for it here.
            if (mNoMoreCards) {
                mSched.setReps(mSched.getReps() + 1);
            }

            Long[] elapsed = AnkiDroidApp.getCol().timeboxReached();
            if (elapsed != null) {
                int nCards = elapsed[1].intValue();
                int nMins = elapsed[0].intValue() / 60;
                String mins = res.getQuantityString(R.plurals.timebox_reached_minutes, nMins, nMins);
                String timeboxMessage = res.getQuantityString(R.plurals.timebox_reached, nCards, nCards, mins);
                Themes.showThemedToast(Reviewer.this, timeboxMessage, true);
                AnkiDroidApp.getCol().startTimebox();
            }

            // if (mChosenAnswer.getText().equals("")) {
            // setDueMessage();
            // }
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
                closeReviewer(RESULT_NO_MORE_CARDS, true);
            }
            if (mProgressDialog != null && mProgressDialog.isShowing()) {
                mProgressDialog.dismiss();
            }
            // set the correct mark/unmark icon on action bar
            refreshActionBar();
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

    private void setFullScreen(boolean fullScreen) {
        WindowManager.LayoutParams attrs = getWindow().getAttributes();
        if (fullScreen) {
            attrs.flags |= WindowManager.LayoutParams.FLAG_FULLSCREEN;
        } else {
            attrs.flags &= (~WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }
        getWindow().setAttributes(attrs);
    }


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

        // Remove the status bar and title bar
        if (mPrefFullscreenReview) {
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                    WindowManager.LayoutParams.FLAG_FULLSCREEN);
            // Do not hide the title bar in Honeycomb, since it contains the action bar.
            if (AnkiDroidApp.SDK_VERSION <= 11) {
                requestWindowFeature(Window.FEATURE_NO_TITLE);
            }
        }

        mChangeBorderStyle = Themes.getTheme() == Themes.THEME_ANDROID_LIGHT
                || Themes.getTheme() == Themes.THEME_ANDROID_DARK;

        // The hardware buttons should control the music volume while reviewing.
        setVolumeControlStream(AudioManager.STREAM_MUSIC);

        Collection col = AnkiDroidApp.getCol();
        if (col == null) {
            reloadCollection(savedInstanceState);
            return;
        } else {
            mSched = col.getSched();
            mCollectionFilename = col.getPath();

            mBaseUrl = Utils.getBaseUrl(col.getMedia().getDir());
            restorePreferences();
            setFullScreen(mPrefFullscreenReview);

            registerExternalStorageListener();

            if (mNightMode) {
                mCurrentBackgroundColor = Themes.getNightModeCardBackground(this);
            } else {
                mCurrentBackgroundColor = Color.WHITE;
            }

            mUseQuickUpdate = shouldUseQuickUpdate();

            initLayout(R.layout.flashcard);

            try {
                String[] title = mSched.getCol().getDecks().current().getString("name").split("::");
                AnkiDroidApp.getCompat().setTitle(this, title[title.length - 1], mInvertedColors);
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
            AnkiDroidApp.getCompat().setSubtitle(this, "", mInvertedColors);

            if (mPrefTextSelection) {
                clipboardSetText("");
            }

            // Load the template for the card
            try {
                mCardTemplate = Utils.convertStreamToString(getAssets().open("card_template.html"));
            } catch (IOException e) {
                e.printStackTrace();
            }

            // Initialize text-to-speech. This is an asynchronous operation.
            if (mSpeakText) {
                ReadText.initializeTts(this);
            }

            // Get last whiteboard state
            if (mPrefWhiteboard && mCurrentCard != null && MetaDB.getWhiteboardState(this, mCurrentCard.getDid()) == 1) {
                mShowWhiteboard = true;
                mWhiteboard.setVisibility(View.VISIBLE);
            }

            // Load the first card and start reviewing. Uses the answer card
            // task to load a card, but since we send null
            // as the card to answer, no card will be answered.
            DeckTask.launchDeckTask(DeckTask.TASK_TYPE_ANSWER_CARD, mAnswerCardHandler, new DeckTask.TaskData(mSched,
                    null, 0));

            // Since we aren't actually answering a card, decrement the rep count
            mSched.setReps(mSched.getReps() - 1);
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
        restartTimer();
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
        	if (AnkiDroidApp.colIsOpen()) {
                WidgetStatus.update(this, mSched.progressToday(null, mCurrentCard, true));
        	}

            // } catch (JSONException e) {
            // throw new RuntimeException(e);
            // }
            UIUtils.saveCollectionInBackground();
        }
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.i(AnkiDroidApp.TAG, "Reviewer - onDestroy()");
        if (mSpeakText) {
            ReadText.releaseTts();
        }
        if (mUnmountReceiver != null) {
            unregisterReceiver(mUnmountReceiver);
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


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
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
            MenuItem item = menu.add(Menu.NONE, MENU_SEARCH, Menu.NONE, res.getString(R.string.menu_select));
            item.setIcon(R.drawable.ic_menu_search);
            item.setEnabled(Lookup.isAvailable());
        }
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
    	Resources res = getResources();
        MenuItem item = menu.findItem(MENU_MARK);
        if (mCurrentCard != null && mCurrentCard.note().hasTag("marked")) {
            item.setTitle(R.string.menu_unmark_card);
            item.setIcon(R.drawable.ic_menu_marked);
        } else {
            item.setTitle(R.string.menu_mark_card);
            item.setIcon(R.drawable.ic_menu_mark);
        }
        item = menu.findItem(MENU_UNDO);
        if (AnkiDroidApp.colIsOpen() && AnkiDroidApp.getCol().undoAvailable()) {
            item.setEnabled(true);
            item.setIcon(R.drawable.ic_menu_revert);
        } else {
            item.setEnabled(false);
            item.setIcon(R.drawable.ic_menu_revert_disabled);
        }
        item = menu.findItem(MENU_SEARCH);
    	if (item != null) {
    		item.setTitle(clipboardHasText() ? Lookup.getSearchStringTitle() : res.getString(R.string.menu_select));
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


    private void reloadCollection(Bundle savedInstanceState) {
    	mSavedInstanceState = savedInstanceState;
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
                        if (AnkiDroidApp.colIsOpen()) {
                            onCreate(mSavedInstanceState);
                        } else {
                            finish();
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
                new DeckTask.TaskData(AnkiDroidApp.getCurrentAnkiDroidDirectory()
                        + AnkiDroidApp.COLLECTION_PATH, 0, true));
    }


    // These three methods use a deprecated API - they should be updated to possibly use its more modern version.
    private boolean clipboardHasText() {
        return mClipboard != null && mClipboard.hasText();
    }


    private void clipboardSetText(CharSequence text) {
        if (mClipboard != null) {
            try {
                mClipboard.setText(text);
            } catch (NullPointerException e) {
                // Workaround for https://code.google.com/p/ankidroid/issues/detail?id=1746
                // Some devices end up with an unusable clipboard. If so, we must disable it or AnkiDroid will
                // crash if it tries to use it.
                Log.e(AnkiDroidApp.TAG, "Clipboard error. Disabling text selection setting.");
                AnkiDroidApp.getSharedPrefs(getBaseContext()).edit().putBoolean("textSelection", false).commit();
            }
        }
    }


    private CharSequence clipboardGetText() {
        if (mClipboard != null) {
            return mClipboard.getText();
        } else {
        	return "";
        }
    }


    @Override
    public void onOptionsMenuClosed(Menu menu) {
        if (mPrefFullscreenReview) {
            // Restore top bar
            mTextBarRed.setVisibility(View.VISIBLE);
            mTextBarBlack.setVisibility(View.VISIBLE);
            mTextBarBlue.setVisibility(View.VISIBLE);
            mChosenAnswer.setVisibility(View.VISIBLE);
            if (mShowTimer) {
                mCardTimer.setVisibility(View.VISIBLE);
            }
            if (mShowProgressBars) {
                mProgressBars.setVisibility(View.VISIBLE);
            }

            // Restore fullscreen preference
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                    WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }
        setFullScreen(mPrefFullscreenReview);
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
                    MetaDB.storeWhiteboardState(this, mCurrentCard.getDid(), 1);
                } else {
                    // Hide whiteboard
                    mWhiteboard.setVisibility(View.GONE);
                    item.setTitle(R.string.show_whiteboard);
                    MetaDB.storeWhiteboardState(this, mCurrentCard.getDid(), 0);
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
                fillFlashcard(mShowAnimations);
            }
        }
        if (mPrefTextSelection) {
            clipboardSetText("");
        }
    }


    // ----------------------------------------------------------------------------
    // CUSTOM METHODS
    // ----------------------------------------------------------------------------

    /**
     * Show/dismiss dialog when sd card is ejected/remounted (collection is saved by SdCardReceiver)
     */
    private void registerExternalStorageListener() {
        if (mUnmountReceiver == null) {
            mUnmountReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (intent.getAction().equals(SdCardReceiver.MEDIA_EJECT)) {
                        finish();
                    }
                }
            };
            IntentFilter iFilter = new IntentFilter();
            iFilter.addAction(SdCardReceiver.MEDIA_EJECT);
            registerReceiver(mUnmountReceiver, iFilter);
        }
    }


    private void stopTimer() {
        // Stop visible timer and card timer
    	if (mCardTimer != null) {
            mCardTimer.stop();
    	}
        if (mCurrentCard != null) {
             mCurrentCard.stopTimer();
        }
    }


    private void restartTimer() {
        // Restart visible timer and card timer
        if (mCurrentCard != null) {
            mCardTimer.setBase(SystemClock.elapsedRealtime() - mCurrentCard.timeTaken());
            mCardTimer.start();
            mCurrentCard.resumeTimer();
        }
    }


     private void undo() {
    	if (mSched.getCol().undoAvailable()) {
            setNextCardAnimation(true);
            if (mProgressDialog != null && mProgressDialog.isShowing()) {
                mProgressDialog.setMessage(getResources().getString(R.string.saving_changes));
            } else {
                mProgressDialog = StyledProgressDialog.show(Reviewer.this, "",
                        getResources().getString(R.string.saving_changes), true);
            }
            DeckTask.launchDeckTask(DeckTask.TASK_TYPE_UNDO, mAnswerCardHandler, new DeckTask.TaskData(mSched));
    	}
    }


    private void finishNoStorageAvailable() {
        Reviewer.this.setResult(DeckPicker.RESULT_MEDIA_EJECTED);
        finish();
    }


    private boolean editCard() {
        Intent editCard = new Intent(Reviewer.this, CardEditor.class);
        editCard.putExtra(CardEditor.EXTRA_CALLER, CardEditor.CALLER_REVIEWER);
        sEditorCard = mCurrentCard;
        setOutAnimation(true);
        startActivityForResultWithAnimation(editCard, EDIT_CURRENT_CARD, ActivityTransitionAnimation.LEFT);
        return true;
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
        if (Lookup.lookUp(clipboardGetText().toString())) {
            clipboardSetText("");
        }
        return true;
    }

    private void showLookupButtonIfNeeded() {
        if (mPrefTextSelection && mClipboard != null) {
            if (clipboardGetText().length() != 0 && Lookup.isAvailable() && mLookUpIcon.getVisibility() != View.VISIBLE) {
                mLookUpIcon.setVisibility(View.VISIBLE);
                enableViewAnimation(mLookUpIcon, ViewAnimation.fade(ViewAnimation.FADE_IN, mFadeDuration, 0));
            } else if (mLookUpIcon.getVisibility() == View.VISIBLE) {
                mLookUpIcon.setVisibility(View.GONE);
                enableViewAnimation(mLookUpIcon, ViewAnimation.fade(ViewAnimation.FADE_OUT, mFadeDuration, 0));
            }
        }
    }

    private void hideLookupButton() {
        if (mPrefTextSelection && mLookUpIcon.getVisibility() != View.GONE) {
            mLookUpIcon.setVisibility(View.GONE);
            enableViewAnimation(mLookUpIcon, ViewAnimation.fade(ViewAnimation.FADE_OUT, mFadeDuration, 0));
            clipboardSetText("");
        }
    }

    private void showDeleteNoteDialog() {
        Dialog dialog;
        Resources res = getResources();
        StyledDialog.Builder builder = new StyledDialog.Builder(this);
        builder.setTitle(res.getString(R.string.delete_card_title));
        builder.setIcon(R.drawable.ic_dialog_alert);
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
            hideLookupButton();
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
        if (AnkiDroidApp.SDK_VERSION <= 7 && (mCard != null)) {
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

        if (!mShowNextReviewTime) {
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

        if (!mShowRemainingCardCount) {
            mTextBarRed.setVisibility(View.GONE);
            mTextBarBlack.setVisibility(View.GONE);
            mTextBarBlue.setVisibility(View.GONE);
        }

        if (mShowProgressBars) {
            mSessionProgressTotalBar = (View) findViewById(R.id.daily_bar);
            mSessionProgressBar = (View) findViewById(R.id.session_progress);
            mProgressBars = (LinearLayout) findViewById(R.id.progress_bars);
        }

        mCardTimer = (Chronometer) findViewById(R.id.card_time);
        if (mShowProgressBars && mProgressBars.getVisibility() != View.VISIBLE) {
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
        webView.addJavascriptInterface(new JavaScriptInterface(this), "ankidroid");
        if (AnkiDroidApp.SDK_VERSION > 7) {
            webView.setFocusableInTouchMode(false);
        }
        AnkiDroidApp.getCompat().setScrollbarFadingEnabled(webView, mPrefFadeScrollbars);
        Log.i(AnkiDroidApp.TAG, "Focusable = " + webView.isFocusable() + ", Focusable in touch mode = " + webView.isFocusableInTouchMode());

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
        mTextBarRed.setTextColor(invert ? res.getColor(R.color.night_blue) : res.getColor(R.color.blue));
        mTextBarBlack.setTextColor(invert ? res.getColor(R.color.night_red) : res.getColor(R.color.red));
        mTextBarBlue.setTextColor(invert ? res.getColor(R.color.night_green) : res.getColor(R.color.green));
        mAnswerField.setTextColor(mForegroundColor);

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
        AnkiDroidApp.getCompat().setActionBarBackground(this, invert ? R.color.white_background_night : R.color.actionbar_background);
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
        if (mShowNextReviewTime) {
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
        if (mShowTimer) {
            switchVisibility(mCardTimer, visible, true);
        }
        if (mShowProgressBars) {
            switchVisibility(mProgressBars, visible, true);
        }
        if (mShowRemainingCardCount) {
            switchVisibility(mTextBarRed, visible, true);
            switchVisibility(mTextBarBlack, visible, true);
            switchVisibility(mTextBarBlue, visible, true);
        }
        switchVisibility(mChosenAnswer, visible, true);
    }


    private void initControls() {
        mCardFrame.setVisibility(View.VISIBLE);
        if (mShowRemainingCardCount) {
            mTextBarRed.setVisibility(View.VISIBLE);
            mTextBarBlack.setVisibility(View.VISIBLE);
            mTextBarBlue.setVisibility(View.VISIBLE);
        }
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
        mPrefWhiteboard = preferences.getBoolean("whiteboard", false);
        mPrefWriteAnswers = preferences.getBoolean("writeAnswers", true);
        mPrefTextSelection = preferences.getBoolean("textSelection", true);
        mLongClickWorkaround = preferences.getBoolean("textSelectionLongclickWorkaround", false);
        // mDeckFilename = preferences.getString("deckFilename", "");
        mNightMode = preferences.getBoolean("invertedColors", false);
        mInvertedColors = mNightMode;
        mBlackWhiteboard = preferences.getBoolean("blackWhiteboard", true);
        mPrefFullscreenReview = preferences.getBoolean("fullscreenReview", false);
        mZoomEnabled = preferences.getBoolean("zoom", false);
        mDisplayFontSize = preferences.getInt("relativeDisplayFontSize", 100);// Card.DEFAULT_FONT_SIZE_RATIO);
        mRelativeButtonSize = preferences.getInt("answerButtonSize", 100);
        mInputWorkaround = preferences.getBoolean("inputWorkaround", false);
        mPrefFixArabic = preferences.getBoolean("fixArabicText", false);
        mPrefForceQuickUpdate = preferences.getBoolean("forceQuickUpdate", false);
        mSpeakText = preferences.getBoolean("tts", false);
        mShowProgressBars = preferences.getBoolean("progressBars", true);
        mPrefFadeScrollbars = preferences.getBoolean("fadeScrollbars", false);
        mPrefUseTimer = preferences.getBoolean("timeoutAnswer", false);
        mWaitAnswerSecond = preferences.getInt("timeoutAnswerSeconds", 20);
        mWaitQuestionSecond = preferences.getInt("timeoutQuestionSeconds", 60);
        mScrollingButtons = preferences.getBoolean("scrolling_buttons", false);
        mDoubleScrolling = preferences.getBoolean("double_scrolling", false);
        mPrefCenterVertically =  preferences.getBoolean("centerVertically", false);

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
        if (mPrefTextSelection && mLongClickWorkaround) {
            mGestureLongclick = GESTURE_LOOKUP;
        }
        mShowAnimations = preferences.getBoolean("themeAnimations", false);
        if (mShowAnimations) {
            int animationDuration = preferences.getInt("animationDuration", 500);
            mAnimationDurationTurn = animationDuration;
            mAnimationDurationMove = animationDuration;
        }

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

        // These are preferences we pull out of the collection instead of SharedPreferences
        try {
            mShowNextReviewTime = AnkiDroidApp.getCol().getConf().getBoolean("estTimes");
            mShowRemainingCardCount = AnkiDroidApp.getCol().getConf().getBoolean("dueCounts");
        } catch (JSONException e) {
            throw new RuntimeException();
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
	            mSimpleCard = new ScrollTextView(this);
	            Themes.setRegularFont(mSimpleCard);
	            mSimpleCard.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 10, getResources().getDisplayMetrics()) * mDisplayFontSize / 100);
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
	            if (!mUseQuickUpdate) {
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
    }


    private void updateScreenCounts() {
        if (mCurrentCard == null) {
            return;
        }

        try {
            String[] title = mSched.getCol().getDecks().get(mCurrentCard.getDid()).getString("name").split("::");
            AnkiDroidApp.getCompat().setTitle(this, title[title.length - 1], mInvertedColors);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }

        int[] counts = mSched.counts(mCurrentCard);

        int eta = mSched.eta(counts, false);
        AnkiDroidApp.getCompat().setSubtitle(this, getResources().getQuantityString(R.plurals.reviewer_window_title, eta, eta), mInvertedColors);

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
            if (mFlipCardLayout.isEnabled() == true && mFlipCardLayout.getVisibility() == View.VISIBLE) {
                mFlipCardLayout.performClick();
            }
        }
    };

    private void initTimer() {
        mShowTimer = mCurrentCard.showTimer();
        if (mShowTimer && mCardTimer.getVisibility() == View.INVISIBLE) {
        	switchVisibility(mCardTimer, View.VISIBLE);
        } else if (!mShowTimer && mCardTimer.getVisibility() != View.INVISIBLE) {
        	switchVisibility(mCardTimer, View.INVISIBLE);
        }
        mCardTimer.setBase(SystemClock.elapsedRealtime());
        mCardTimer.start();
    }

    private void displayCardQuestion() {
    	// show timer, if activated in the deck's preferences
    	initTimer();

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
        question = typeAnsQuestionFilter(question);

        if (mPrefFixArabic) {
            question = ArabicUtilities.reshapeSentence(question, true);
        }

        Log.i(AnkiDroidApp.TAG, "question: '" + question + "'");

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
                    Log.i(AnkiDroidApp.TAG, "correct answer = " + correctAnswer);

                    // Obtain the diff and send it to updateCard
                    DiffEngine diff = new DiffEngine();

                    StringBuffer span = new StringBuffer();
                    span.append("<span style=\"font-family: '").append(mTypeFont)
                    .append("'; font-size: ").append(mTypeSize).append("px\">");
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

        Lookup.initialize(this, mCurrentCard.getDid());

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

            // don't play question sound again when displaying answer
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

            // CSS class for card-specific styling
            String cardClass = "card card" + (mCurrentCard.getOrd()+1);

            if (mPrefCenterVertically) {
                cardClass += " vertically_centered";
            }

            Log.i(AnkiDroidApp.TAG, "content card = \n" + content);
            StringBuilder style = new StringBuilder();
            style.append(mCustomFontStyle);
            Log.i(AnkiDroidApp.TAG, "::style::" + style);

            if (mNightMode) {
                content = HtmlColors.invertColors(content);
                cardClass += " night_mode";
            }

            content = SmpToHtmlEntity(content);
            mCardContent = new SpannedString(mCardTemplate.replace("::content::", content).replace("::style::",
                    style.toString()).replace("::class::", cardClass));
            Log.i(AnkiDroidApp.TAG, "base url = " + mBaseUrl);

            if (SAVE_CARD_CONTENT) {
                try {
                    FileOutputStream f = new FileOutputStream(
                            new File(AnkiDroidApp.getCurrentAnkiDroidDirectory(), "card.html"));
                    try {
                        f.write(mCardContent.toString().getBytes());
                    } finally {
                        f.close();
                    }
                } catch (IOException e) {
                    Log.d(AnkiDroidApp.TAG, "failed to save card", e);
                }
            }
            fillFlashcard(mShowAnimations);
        }

        if (!mConfigurationChanged) {
            playSounds();
    	}
	}

    /**
     * Converts characters in Unicode Supplementary Multilingual Plane (SMP) to their equivalent Html Entities.
     * This is done because webview has difficulty displaying these characters.
     * @param text
     * @return
     */
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

    /**
     * Plays sounds (or TTS, if configured) for current shown side of card
     */
    private void playSounds() {
        try {
            // first check, if sound is activated for the current deck
            if (getConfigForCurrentCard().getBoolean("autoplay")) {
                // We need to play the sounds from the proper side of the card
                if (!mSpeakText) {
                    Sound.playSounds(sDisplayAnswer ? MetaDB.LANGUAGES_QA_ANSWER : MetaDB.LANGUAGES_QA_QUESTION);
                } else {
                    // If the question is displayed or if the question should be replayed, read the question
                    if (!sDisplayAnswer || getConfigForCurrentCard().getBoolean("replayq")) {
                        readCardText(mCurrentCard, MetaDB.LANGUAGES_QA_QUESTION);
                    }
                    if (sDisplayAnswer) {
                        readCardText(mCurrentCard, MetaDB.LANGUAGES_QA_ANSWER);
                    }
                }
            }
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }


    /**
     * Reads the text (using TTS) for the given side of a card.
     *
     * @param card The card to play TTS for
     * @param cardSide The side of the current card to play TTS for
     */
    private static void readCardText(final Card card, final int cardSide) {
        if (MetaDB.LANGUAGES_QA_QUESTION == cardSide) {
            ReadText.textToSpeech(Utils.stripHTML(card.getQuestion(true)), getDeckIdForCard(card), card.getOrd(),
                    MetaDB.LANGUAGES_QA_QUESTION);
        } else if (MetaDB.LANGUAGES_QA_ANSWER == cardSide) {
            ReadText.textToSpeech(Utils.stripHTML(card.getPureAnswerForReading()), getDeckIdForCard(card),
                    card.getOrd(),
                    MetaDB.LANGUAGES_QA_ANSWER);
        }
    }


    /**
     * Returns the configuration for the current {@link Card}.
     *
     * @return The configuration for the current {@link Card}
     */
    private JSONObject getConfigForCurrentCard() {
        return mSched.getCol().getDecks().confForDid(getDeckIdForCard(mCurrentCard));
    }


    /**
     * Returns the deck ID of the given {@link Card}.
     *
     * @param card The {@link Card} to get the deck ID
     * @return The deck ID of the {@link Card}
     */
    private static long getDeckIdForCard(final Card card) {
        // Try to get the configuration by the original deck ID (available in case of a cram deck),
        // else use the direct deck ID (in case of a 'normal' deck.
        return card.getODid() == 0 ? card.getDid() : card.getODid();
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
            if (mCurrentSimpleInterface && mSimpleCard != null) {
                mSimpleCard.setText(mCardContent);
            } else if (!mUseQuickUpdate && mCard != null && mNextCard != null) {
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
                if (AnkiDroidApp.SDK_VERSION <= 7) {
                    mCard.setFocusableInTouchMode(true);
                }
            } else if (mCard != null) {
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


    /**
     * Returns the CSS used to handle custom fonts.
     * <p>
     * Custom fonts live in fonts directory in the directory used to store decks.
     * <p>
     * Each font is mapped to the font family by the same name as the name of the font fint without the extension.
     */
    private String getCustomFontsStyle() {
        StringBuilder builder = new StringBuilder();
        for (AnkiFont font : getCustomFontsMap().values()) {
            builder.append(font.getDeclaration());
            builder.append('\n');
        }
        return builder.toString();
    }


    /** Returns the CSS used to set the default font. */
    private String getDefaultFontStyle() {
        if (mCustomDefaultFontCss == null) {
            SharedPreferences preferences = AnkiDroidApp.getSharedPrefs(getBaseContext());
            AnkiFont defaultFont = getCustomFontsMap().get(preferences.getString("defaultFont", null));
            if (defaultFont != null) {
                mCustomDefaultFontCss = "BODY { " + defaultFont.getCSS() + " }\n";
            } else {
                String defaultFontName = Themes.getReviewerFontName();
                if (TextUtils.isEmpty(defaultFontName)) {
                    mCustomDefaultFontCss = "";
                } else {
                    mCustomDefaultFontCss = String.format(
                            "BODY {"
                            + "font-family: '%s';"
                            + "font-weight: normal;"
                            + "font-style: normal;"
                            + "font-stretch: normal;"
                            + "}\n", defaultFontName);
                }
            }
        }
        return mCustomDefaultFontCss;
    }


    public static Card getEditorCard() {
        return sEditorCard;
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
     * Parses content in question and answer to see, whether someone has hard coded the font size in a card layout.
     * If this is so, then the font size must be replaced with one corrected by the relative font size. If a relative
     * CSS unit measure is used (e.g. 'em'), then only the outer tag 'span' or 'div' tag in a hierarchy of such tags
     * is adjusted.
     * This is not bullet-proof, a combination of font-size in span and in css classes will break this logic, but let's
     * just avoid building an HTML parser for this feature.
     * Anything that threatens common sense will break this logic, eg nested span/divs with CSS classes having font-size
     * declarations with relative units (40% dif inside 120% div inside 60% div). Broken HTML also breaks this.
     * Feel free to improve, but please keep it short and fast.
     *
     * @param content The HTML content that will be font-size-adjusted.
     * @param percentage The relative font size percentage defined in preferences
     * @return
     */
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

        if (mPrefWhiteboard) {
            mWhiteboard.setEnabled(false);
        }

        if (typeAnswer()) {
            mAnswerField.setEnabled(false);
        }
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


    /**
     * @return true if the device is a Nook
     */
    private boolean isNookDevice() {
        for (String s : new String[] { "nook" }) {
            if (android.os.Build.DEVICE.toLowerCase().indexOf(s) != -1
                    || android.os.Build.MODEL.toLowerCase().indexOf(s) != -1) {
                return true;
            }
        }
        return false;
    }


    /**
     * Returns a map from custom fonts names to the corresponding {@link AnkiFont} object.
     *
     * <p>The list of constructed lazily the first time is needed.
     */
    private Map<String, AnkiFont> getCustomFontsMap() {
        if (mCustomFontsMap == null) {
            List<AnkiFont> fonts = Utils.getCustomFonts(getBaseContext());
            mCustomFontsMap = new HashMap<String, AnkiFont>();
            for (AnkiFont f : fonts) {
                mCustomFontsMap.put(f.getName(), f);
            }
        }
        return mCustomFontsMap;
    }


    /**
     * Returns true if we should update the content of a single {@link WebView} (called quick update) instead of switch
     * between two instances.
     *
     * <p>Webview switching is needed in some versions of Android when using custom fonts because of a memory leak in
     * WebView.
     *
     * <p>It is also needed to solve a refresh issue on Nook devices.
     *
     * @return true if we should use a single WebView
     */
    private boolean shouldUseQuickUpdate() {
        if (mPrefForceQuickUpdate) {
            // The user has requested us to use quick update in the preferences.
            return true;
        }
        // Otherwise, use quick update only if there are no custom fonts.
        return getCustomFontsMap().size() == 0 && !isNookDevice();
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
        private Reviewer mCtx;

        JavaScriptInterface(Reviewer ctx) {
            mCtx = ctx;
        }

        /**
         * This is not called on the UI thread. Send a message that will be handled on the UI thread.
         */
        public void playSound(String soundPath) {
            Message msg = Message.obtain();
            msg.obj = soundPath;
            mHandler.sendMessage(msg);
        }
        public int getAvailableWidth() {
            if (mCtx.mAvailableInCardWidth == 0) {
                mCtx.mAvailableInCardWidth = mCtx.calcAvailableInCardWidth();
            }
            return mCtx.mAvailableInCardWidth;
        }
    }

    /** Calculate the width that is available to the webview for content */
    public int calcAvailableInCardWidth() {
        // The available width of the webview equals to the container's width, minus the container's padding
        // divided by the default scale factor used by the WebView, and minus the WebView's padding
        if (mCard != null && mCardFrame != null) {
            return Math.round((mCardFrame.getWidth() - mCardFrame.getPaddingLeft() - mCardFrame.getPaddingRight()
                    - mCard.getPaddingLeft() - mCard.getPaddingRight()) / mCard.getScale());
        }
        return 0;
    }

    private void closeReviewer(int result, boolean saveDeck) {
        mTimeoutHandler.removeCallbacks(mShowAnswerTask);
        mTimeoutHandler.removeCallbacks(mShowQuestionTask);
        mTimerHandler.removeCallbacks(removeChosenAnswerText);
        longClickHandler.removeCallbacks(longClickTestRunnable);
        longClickHandler.removeCallbacks(startLongClickAction);

        Reviewer.this.setResult(result);

        setOutAnimation(true);

        // updateBigWidget(!mCardFrame.isEnabled());

        if (saveDeck) {
            UIUtils.saveCollectionInBackground();
        }
        finish();
        if (AnkiDroidApp.SDK_VERSION > 4) {
            ActivityTransitionAnimation.slide(Reviewer.this, ActivityTransitionAnimation.RIGHT);
        }
    }

    private void refreshActionBar() {
        AnkiDroidApp.getCompat().invalidateOptionsMenu(Reviewer.this);
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
            showLookupButtonIfNeeded();
            return false;
        }
    }


    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (gestureDetector.onTouchEvent(event))
            return true;
        else
            return false;
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

    private TagHandler mSimpleInterfaceTagHandler = new TagHandler () {

        public void handleTag(boolean opening, String tag, Editable output,
                XMLReader xmlReader) {
//            if(tag.equalsIgnoreCase("div")) {
//            	output.append("\n");
//            } else
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

    private Spanned convertToSimple(String text) {
    	return Html.fromHtml(text, mSimpleInterfaceImagegetter, mSimpleInterfaceTagHandler);
    }
}
