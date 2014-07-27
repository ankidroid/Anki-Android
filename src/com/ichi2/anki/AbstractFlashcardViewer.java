/****************************************************************************************
 * Copyright (c) 2011 Kostas Spyropoulos <inigo.aldana@gmail.com>                       *
 * Copyright (c) 2014 Bruno Romero de Azevedo <brunodea@inf.ufsm.br>                    *
 * Copyright (c) 2014 Roland Sieker <ospalh@gmail.com>                                  *
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

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.os.Vibrator;
import android.support.v4.widget.DrawerLayout;
import android.text.ClipboardManager;
import android.text.Editable;
import android.text.Html;
import android.text.Html.TagHandler;
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
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.JsResult;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.Chronometer;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.ichi2.anim.ActivityTransitionAnimation;
import com.ichi2.anim.ViewAnimation;
import com.ichi2.anki.exception.APIVersionException;
import com.ichi2.anki.receiver.SdCardReceiver;
import com.ichi2.anki.reviewer.ReviewerExtRegistry;
import com.ichi2.async.DeckTask;
import com.ichi2.libanki.Card;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Consts;
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
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class AbstractFlashcardViewer extends NavigationDrawerActivity {

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

    protected static boolean sDisplayAnswer = false;

    /** The percentage of the absolute font size specified in the deck. */
    private int mDisplayFontSize = 100;

    /** The percentage of the original image size in the deck. */
    private int mDisplayImageSize = 100;

    /** Pattern for font-size style declarations */
    private static final Pattern fFontSizePattern = Pattern.compile(
            "font-size\\s*:\\s*([0-9.]+)\\s*((?:px|pt|in|cm|mm|pc|%|em))\\s*;?", Pattern.CASE_INSENSITIVE);
    /** Pattern for opening/closing span/div tags */
    private static final Pattern fSpanDivPattern = Pattern.compile("<(/?)(span|div)", Pattern.CASE_INSENSITIVE);
    /** The relative CSS measurement units for pattern search */
    private static final Set<String> fRelativeCssUnits = new HashSet<String>(Arrays.asList(new String[] { "%", "em" }));

    /**
     * Broadcast that informs us when the sd card is about to be unmounted
     */
    private BroadcastReceiver mUnmountReceiver = null;

    private boolean mInBackground = false;
    private boolean mReloadingCollection = false;

    /**
     * Variables to hold preferences
     */
    private boolean mPrefHideDueCount;
    private boolean mShowTimer;
    protected boolean mPrefWhiteboard;
    private boolean mPrefWriteAnswers;
    private boolean mInputWorkaround;
    private boolean mLongClickWorkaround;
    private boolean mPrefFullscreenReview;
    private String mCollectionFilename;
    private int mRelativeImageSize;
    private int mRelativeButtonSize;
    private boolean mDoubleScrolling;
    private boolean mScrollingButtons;
    private boolean mGesturesEnabled;
    private boolean mPrefFixArabic;
    // Android WebView
    private boolean mSpeakText;
    private boolean mDisableClipboard = false;
    protected boolean mInvertedColors = false;
    private int mCurrentBackgroundColor;
    private boolean mBlackWhiteboard = true;
    private boolean mNightMode = false;
    private boolean mPrefSafeDisplay;
    protected boolean mPrefUseTimer;
    private boolean mPrefCenterVertically;
    private boolean mSimpleInterface = false;
    private boolean mCurrentSimpleInterface = false;
    private ArrayList<String> mSimpleInterfaceExcludeTags;
    private int mAvailableInCardWidth;

    // Preferences from the collection
    private boolean mShowNextReviewTime;
    private boolean mShowRemainingCardCount;

    // Answer card & cloze deletion variables
    /**
     * The correct answer in the compare to field if answer should be given by learner. Null if no answer is expected.
     */
    private String mTypeCorrect;
    /** The font name attribute of the type answer field for formatting */
    private String mTypeFont;
    /** The font size attribute of the type answer field for formatting */
    private int mTypeSize;
    private String mTypeWarning;

    private boolean mIsSelecting = false;
    private boolean mTouchStarted = false;
    private boolean mInAnswer = false;
    private boolean mAnswerSoundsAdded = false;

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
    private TextView mTextBarNew;
    private TextView mTextBarLearn;
    private TextView mTextBarReview;
    private TextView mChosenAnswer;
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
    protected LinearLayout mFlipCardLayout;
    protected LinearLayout mEase1Layout;
    protected LinearLayout mEase2Layout;
    protected LinearLayout mEase3Layout;
    protected LinearLayout mEase4Layout;
    protected RelativeLayout mTopBarLayout;
    private Chronometer mCardTimer;
    protected Whiteboard mWhiteboard;
    private ClipboardManager mClipboard;
    private StyledProgressDialog mProgressDialog;
    private StyledOpenCollectionDialog mOpenCollectionDialog;
    private ProgressBar mProgressBar;

    protected Card mCurrentCard;
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
     * <p>
     * If false, we will instead use two WebViews and switch them when changing the content. This is needed because of a
     * bug in some versions of Android.
     */
    private boolean mUseQuickUpdate = false;

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
    private int mGestureDoubleTap;
    private int mGestureTapLeft;
    private int mGestureTapRight;
    private int mGestureTapTop;
    private int mGestureTapBottom;
    private int mGestureLongclick;

    protected static final int GESTURE_NOTHING = 0;
    private static final int GESTURE_SHOW_ANSWER = 1;
    private static final int GESTURE_ANSWER_EASE1 = 2;
    private static final int GESTURE_ANSWER_EASE2 = 3;
    private static final int GESTURE_ANSWER_EASE3 = 4;
    private static final int GESTURE_ANSWER_EASE4 = 5;
    private static final int GESTURE_ANSWER_RECOMMENDED = 6;
    private static final int GESTURE_ANSWER_BETTER_THAN_RECOMMENDED = 7;
    private static final int GESTURE_UNDO = 8;
    public static final int GESTURE_EDIT = 9;
    protected static final int GESTURE_MARK = 10;
    protected static final int GESTURE_LOOKUP = 11;
    private static final int GESTURE_BURY = 12;
    private static final int GESTURE_SUSPEND = 13;
    protected static final int GESTURE_DELETE = 14;
    protected static final int GESTURE_CLEAR_WHITEBOARD = 15;
    protected static final int GESTURE_PLAY_MEDIA = 16;
    protected static final int GESTURE_EXIT = 17;

    private Spanned mCardContent;
    private String mBaseUrl;

    private int mFadeDuration = 300;

    private Method mSetTextIsSelectable = null;

    protected Sched mSched;

    private ReviewerExtRegistry mExtensions;

    // private int zEase;

    // ----------------------------------------------------------------------------
    // LISTENERS
    // ----------------------------------------------------------------------------

    private static Handler sHandler = new Handler() {

        @Override
        public void handleMessage(Message msg) {
            Sound.stopSounds();
            Sound.playSound((String) msg.obj, null);
        }
    };

    private final Handler longClickHandler = new Handler();
    private final Runnable longClickTestRunnable = new Runnable() {
        @Override
        public void run() {
            Log.i(AnkiDroidApp.TAG, "onEmulatedLongClick");
            Vibrator vibratorManager = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            vibratorManager.vibrate(50);
            longClickHandler.postDelayed(startLongClickAction, 300);
        }
    };
    private final Runnable startLongClickAction = new Runnable() {
        @Override
        public void run() {
            executeCommand(mGestureLongclick);
        }
    };

    private View.OnClickListener mCardStatisticsListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            Log.i(AnkiDroidApp.TAG, "Show card statistics");
            stopTimer();
            // Themes.htmlOkDialog(AbstractReviewer.this, getResources().getString(R.string.card_browser_card_details),
            // mCurrentCard.getCardDetails(AbstractReviewer.this, false), new DialogInterface.OnClickListener() {
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
            // Explicitly hide the soft keyboard. It *should* be hiding itself automatically, but sometimes failed to do
            // so.
            InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            inputMethodManager.hideSoftInputFromWindow(mAnswerField.getWindowToken(), 0);
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
            if (!mDisableClipboard && !mLongClickWorkaround) {
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
                    AbstractFlashcardViewer.this.finishWithoutAnimation();
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

    public DeckTask.TaskListener mMarkCardHandler = new DeckTask.TaskListener() {
        @Override
        public void onPreExecute() {
            Resources res = getResources();
            mProgressDialog = StyledProgressDialog.show(AbstractFlashcardViewer.this, "",
                    res.getString(R.string.saving_changes), true);
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


        @Override
        public void onCancelled() {
            // TODO Auto-generated method stub

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


        @Override
        public void onCancelled() {
            // TODO Auto-generated method stub

        }
    };

    private DeckTask.TaskListener mUpdateCardHandler = new DeckTask.TaskListener() {
        private boolean mNoMoreCards;


        @Override
        public void onPreExecute() {
            Resources res = getResources();
            try {
                mProgressDialog = StyledProgressDialog.show(AbstractFlashcardViewer.this, "",
                        res.getString(R.string.saving_changes), true);
            } catch (IllegalArgumentException e) {
                Log.e(AnkiDroidApp.TAG, "AbstractReviewer: Error on showing progress dialog: " + e);
            }
        }


        @Override
        public void onProgressUpdate(DeckTask.TaskData... values) {
            if (mCurrentCard != values[0].getCard()) {
                /*
                 * Before updating mCurrentCard, we check whether it is changing or not. If the current card changes,
                 * then we need to display it as a new card, without showing the answer.
                 */
                sDisplayAnswer = false;
            }
            mCurrentCard = values[0].getCard();
            if (mCurrentCard == null) {
                // If the card is null means that there are no more cards scheduled for review.
                mNoMoreCards = true;
                mProgressDialog = StyledProgressDialog.show(AbstractFlashcardViewer.this, "",
                        getResources().getString(R.string.saving_changes), true);
                return;
            }
            if (mPrefWhiteboard) {
                mWhiteboard.clear();
            }

            if (sDisplayAnswer) {
                Sound.resetSounds(); // load sounds from scratch, to expose any edit changes
                mAnswerSoundsAdded = false; // causes answer sounds to be reloaded
                generateQuestionSoundList(); // questions must be intentionally regenerated
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
                Log.e(AnkiDroidApp.TAG, "AbstractReviewer: Error on dismissing progress dialog: " + e);
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
        }


        @Override
        public void onCancelled() {
            // TODO Auto-generated method stub

        }
    };

    protected DeckTask.TaskListener mAnswerCardHandler = new DeckTask.TaskListener() {
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
                Themes.showThemedToast(AbstractFlashcardViewer.this, leechMessage, true);
            }

            mCurrentCard = values[0].getCard();
            if (mCurrentCard == null) {
                // If the card is null means that there are no more cards scheduled for review.
                mNoMoreCards = true;
                mProgressDialog = StyledProgressDialog.show(AbstractFlashcardViewer.this, "",
                        getResources().getString(R.string.saving_changes), true);
            } else {
                // Start reviewing next card
                updateTypeAnswerInfo();
                mProgressBar.setVisibility(View.INVISIBLE);
                AbstractFlashcardViewer.this.unblockControls();
                AbstractFlashcardViewer.this.displayCardQuestion();
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
                Themes.showThemedToast(AbstractFlashcardViewer.this, timeboxMessage, true);
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


        @Override
        public void onCancelled() {
            // TODO Auto-generated method stub

        }
    };


    /**
     * Extract type answer/cloze text and font/size
     */
    private void updateTypeAnswerInfo() {
        mTypeCorrect = null;
        String q = mCurrentCard.q(false);
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
                mTypeWarning = getResources().getString(R.string.empty_card_warning);
            } else {
                mTypeWarning = getResources().getString(R.string.unknown_type_field_warning, fld);
            }
        } else if (mTypeCorrect.equals("")) {
            mTypeCorrect = null;
        } else {
            mTypeWarning = null;
        }
    }


    /**
     * Format question field when it contains typeAnswer or clozes. If there was an error during type text extraction, a
     * warning is displayed
     *
     * @param buf The question text
     * @return The formatted question text
     */
    private String typeAnsQuestionFilter(String buf) {
        Matcher m = sTypeAnsPat.matcher(buf);
        if (mTypeWarning != null) {
            return m.replaceFirst(mTypeWarning);
        }
        if (mPrefWriteAnswers)
        {
            return m.replaceFirst("<span id=typeans class=\"typePrompt\">........</span>");
        } else {
            return m.replaceFirst("<span id=typeans class=\"typePrompt typeOff\">........</span>");
        }
    }


    /**
     * Fill the placeholder for the type comparison. Show the correct answer, and the comparison if appropriate.
     *
     * @param buf The answer text
     * @param userAnswer Text typed by the user, or empty.
     * @param correctAnswer The correct answer, taken from the note.
     * @return The formatted answer text
     */
    private String typeAnsAnswerFilter(String buf, String userAnswer, String correctAnswer) {
        Matcher m = sTypeAnsPat.matcher(buf);
        DiffEngine diffEngine = new DiffEngine();
        StringBuilder sb = new StringBuilder();
        sb.append("<div");
        if (!mPrefWriteAnswers) {
            sb.append(" class=\"typeOff\"");
        }
        sb.append("><code id=typeans>");
        if (!TextUtils.isEmpty(userAnswer)) {
            // The user did type something.
            if (userAnswer.equals(correctAnswer)) {
                // and it was right.
                sb.append(diffEngine.wrapGood(correctAnswer));
                sb.append("\u2714");  // Heavy check mark
            } else {
                // Answer not correct.
                // Only use the complex diff code when needed, that is when we have some typed text that is not
                // exactly the same as the correct text.
                String[] diffedStrings = diffEngine.diffedHtmlStrings(correctAnswer, userAnswer);
                // We know we get back two strings.
                sb.append(diffedStrings[0]);
                sb.append("<br>&darr;<br>");
                sb.append(diffedStrings[1]);
            }
        } else {
            if (mPrefWriteAnswers) {
                sb.append(diffEngine.wrapMissing(correctAnswer));
            } else {
                sb.append(correctAnswer);
            }
        }
        sb.append("</code></div>");
        return m.replaceFirst(sb.toString());
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

    private Handler mTimerHandler = new Handler();

    private Runnable removeChosenAnswerText = new Runnable() {
        @Override
        public void run() {
            mChosenAnswer.setText("");
            setDueMessage();
        }
    };

    protected int mWaitAnswerSecond;
    protected int mWaitQuestionSecond;


    // ----------------------------------------------------------------------------
    // ANDROID METHODS
    // ----------------------------------------------------------------------------

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Create the extensions as early as possible, so that they can be offered events.
        mExtensions = new ReviewerExtRegistry(getBaseContext());

        Themes.applyTheme(this);
        super.onCreate(savedInstanceState);
        Log.i(AnkiDroidApp.TAG, "AbstractReviewer - onCreate");

        mChangeBorderStyle = Themes.getTheme() == Themes.THEME_ANDROID_LIGHT
                || Themes.getTheme() == Themes.THEME_ANDROID_DARK;

        // create inherited navigation drawer layout here so that it can be used by parent class
        setContentView(R.layout.flashcard);
        mDrawerLayout = (DrawerLayout) findViewById(R.id.reviewer_drawer_layout);
        mDrawerList = (ListView) findViewById(R.id.reviewer_left_drawer);
        initNavigationDrawer();

        // The hardware buttons should control the music volume while reviewing.
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        // Try to load the collection
        Collection col = AnkiDroidApp.getCol();
        if (col == null) {
            // Reload the collection asynchronously, let onPostExecute method call initActivity()
            mReloadingCollection = true;
            reloadCollection();
            return;
        } else {
            // If collection was not null then we can safely call initActivity() directly
            initActivity(col);
        }
    }


    protected abstract void setTitle();


    // Finish initializing the activity after the collection has been correctly loaded
    protected void initActivity(Collection col) {
        mSched = col.getSched();
        mCollectionFilename = col.getPath();
        mBaseUrl = Utils.getBaseUrl(col.getMedia().dir());

        restorePreferences();

        if (mPrefFullscreenReview) {
            UIUtils.setFullScreen(this);
        }

        registerExternalStorageListener();

        if (mNightMode) {
            mCurrentBackgroundColor = Themes.getNightModeCardBackground(this);
        } else {
            mCurrentBackgroundColor = Color.WHITE;
        }

        mUseQuickUpdate = shouldUseQuickUpdate();

        initLayout();

        setTitle();

        if (!mDisableClipboard) {
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
        long deckID = mSched.getCol().getDecks().current().optLong("id", -1);
        if (mPrefWhiteboard && deckID != -1 && MetaDB.getWhiteboardState(this, deckID) == 1) {
            mShowWhiteboard = true;
            mWhiteboard.setVisibility(View.VISIBLE);
        }

        // Initialize dictionary lookup feature
        Lookup.initialize(this);
    }


    private void deselectAllNavigationItems() {
        // Deselect all entries in navigation drawer since the Reviewer isn't included in ND
        for (int i=0; i< mDrawerList.getCount(); i++) {
            mDrawerList.setItemChecked(i, false);
        }
        setTitle();
        updateScreenCounts();
    }


    // Saves deck each time Reviewer activity loses focus
    @Override
    protected void onPause() {
        super.onPause();
        Log.i(AnkiDroidApp.TAG, "AbstractReviewer - onPause()");

        mTimeoutHandler.removeCallbacks(mShowAnswerTask);
        mTimeoutHandler.removeCallbacks(mShowQuestionTask);
        longClickHandler.removeCallbacks(longClickTestRunnable);
        longClickHandler.removeCallbacks(startLongClickAction);

        stopTimer();
        Sound.stopSounds();
    }


    @Override
    protected void onResume() {
        mInBackground = false;
        super.onResume();
        restartTimer();

        if (!mReloadingCollection) {
            // Do any tasks which depend on initActivity() completing successfully below
            deselectAllNavigationItems();
        }
    }


    @Override
    protected void onStop() {
        mInBackground = true;
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
        Log.i(AnkiDroidApp.TAG, "AbstractReviewer - onDestroy()");
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
            Log.i(AnkiDroidApp.TAG, "AbstractReviewer - onBackPressed()");
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


    private void updateBigWidget(boolean showProgressDialog) {
        // if (DeckManager.deckIsOpenedInBigWidget(DeckManager.getMainDeckPath())) {
        // Log.i(AnkiDroidApp.TAG, "Reviewer: updateBigWidget");
        // AnkiDroidWidgetBig.setCard(mCurrentCard);
        // AnkiDroidWidgetBig.updateWidget(AnkiDroidWidgetBig.UpdateService.VIEW_SHOW_QUESTION, showProgressDialog);
        // }
    }


    private void reloadCollection() {
        DeckTask.launchDeckTask(DeckTask.TASK_TYPE_OPEN_COLLECTION, new DeckTask.TaskListener() {

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
                    initActivity(AnkiDroidApp.getCol());
                } else {
                    finish();
                }

                // Do any tasks below which were postponed in onCreate() or onResume() due to reloadCollection()
                deselectAllNavigationItems();
                mReloadingCollection = false;
            }


            @Override
            public void onPreExecute() {
                mOpenCollectionDialog = StyledOpenCollectionDialog.show(AbstractFlashcardViewer.this, getResources()
                        .getString(R.string.open_collection), new OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface arg0) {
                        finish();
                    }
                });
            }


            @Override
            public void onProgressUpdate(DeckTask.TaskData... values) {
            }


            @Override
            public void onCancelled() {
                // TODO Auto-generated method stub

            }
        }, new DeckTask.TaskData(AnkiDroidApp.getCurrentAnkiDroidDirectory() + AnkiDroidApp.COLLECTION_PATH, 0, true));
    }


    // These three methods use a deprecated API - they should be updated to possibly use its more modern version.
    private boolean clipboardHasText() {
        return mClipboard != null && mClipboard.hasText();
    }


    private void clipboardSetText(CharSequence text) {
        if (mClipboard != null) {
            try {
                mClipboard.setText(text);
            } catch (Exception e) {
                // https://code.google.com/p/ankidroid/issues/detail?id=1746
                // https://code.google.com/p/ankidroid/issues/detail?id=1820
                // Some devices or external applications make the clipboard throw exceptions. If this happens, we
                // must disable it or AnkiDroid will crash if it tries to use it.
                Log.e(AnkiDroidApp.TAG, "Clipboard error. Disabling text selection setting.");
                mDisableClipboard = true;
            }
        }
    }


    /**
     * Returns the text stored in the clipboard or the empty string if the clipboard is empty or contains something that
     * cannot be convered to text.
     *
     * @return the text in clipboard or the empty string.
     */
    private CharSequence clipboardGetText() {
        CharSequence text = mClipboard != null ? mClipboard.getText() : null;
        return text != null ? text : "";
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.reviewer, menu);
        Resources res = getResources();
        if (mCurrentCard != null && mCurrentCard.note().hasTag("marked")) {
            menu.findItem(R.id.action_mark_card).setTitle(R.string.menu_unmark_card).setIcon(R.drawable.ic_menu_marked);
        } else {
            menu.findItem(R.id.action_mark_card).setTitle(R.string.menu_mark_card).setIcon(R.drawable.ic_menu_mark);
        }
        if (AnkiDroidApp.colIsOpen() && AnkiDroidApp.getCol().undoAvailable()) {
            menu.findItem(R.id.action_undo).setEnabled(true).setIcon(R.drawable.ic_menu_revert);
        } else {
            menu.findItem(R.id.action_undo).setEnabled(false).setIcon(R.drawable.ic_menu_revert_disabled);
        }
        if (mPrefWhiteboard) {
            if (mShowWhiteboard) {
                menu.findItem(R.id.action_whiteboard).setVisible(true).setTitle(R.string.hide_whiteboard);
                menu.findItem(R.id.action_clear_whiteboard).setVisible(true);
            } else {
                menu.findItem(R.id.action_whiteboard).setVisible(true).setTitle(R.string.show_whiteboard);
                menu.findItem(R.id.action_clear_whiteboard).setVisible(false);
            }
        }
        if (AnkiDroidApp.SDK_VERSION < 11 && !mDisableClipboard) {
            menu.findItem(R.id.action_search_dictionary).setVisible(true).setEnabled(!mShowWhiteboard)
                    .setTitle(clipboardHasText() ? Lookup.getSearchStringTitle() : res.getString(R.string.menu_select));
        }
        return super.onCreateOptionsMenu(menu);
    }


    @Override
    public boolean onMenuOpened(int feature, Menu menu) {
        AnkiDroidApp.getCompat().invalidateOptionsMenu(this);
        return super.onMenuOpened(feature, menu);
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // The action bar home/up action should open or close the drawer.
        // ActionBarDrawerToggle will take care of this.
        if (mDrawerToggle.onOptionsItemSelected(item)) {
            return true;
        }

        switch (item.getItemId()) {

            case android.R.id.home:
                closeReviewer(AnkiDroidApp.RESULT_TO_HOME, true);
                return true;

            case R.id.action_undo:
                undo();
                return true;

            case R.id.action_clear_whiteboard:
                mWhiteboard.clear();
                return true;

            case R.id.action_mark_card:
                DeckTask.launchDeckTask(DeckTask.TASK_TYPE_MARK_CARD, mMarkCardHandler, new DeckTask.TaskData(mSched,
                        mCurrentCard, 0));
                return true;

            case R.id.action_replay:
                playSounds(true);
                return true;

            case R.id.action_edit:
                return editCard();

            case R.id.action_bury_card:
                DeckTask.launchDeckTask(DeckTask.TASK_TYPE_DISMISS_NOTE, mDismissCardHandler, new DeckTask.TaskData(
                        mSched, mCurrentCard, 4));
                return true;

            case R.id.action_bury_note:
                DeckTask.launchDeckTask(DeckTask.TASK_TYPE_DISMISS_NOTE, mDismissCardHandler, new DeckTask.TaskData(
                        mSched, mCurrentCard, 0));
                return true;

            case R.id.action_suspend_card:
                DeckTask.launchDeckTask(DeckTask.TASK_TYPE_DISMISS_NOTE, mDismissCardHandler, new DeckTask.TaskData(
                        mSched, mCurrentCard, 1));
                return true;

            case R.id.action_suspend_note:
                DeckTask.launchDeckTask(DeckTask.TASK_TYPE_DISMISS_NOTE, mDismissCardHandler, new DeckTask.TaskData(
                        mSched, mCurrentCard, 2));
                return true;

            case R.id.action_delete:
                showDeleteNoteDialog();
                return true;

            case R.id.action_whiteboard:
                // Load parent deck ID -- we don't remember the whiteboard states for subdecks
                long deckID;
                try {
                    deckID = mSched.getCol().getDecks().current().getLong("id");
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
                // Toggle mShowWhiteboard value
                mShowWhiteboard = !mShowWhiteboard;
                if (mShowWhiteboard) {
                    // Show whiteboard
                    mWhiteboard.setVisibility(View.VISIBLE);
                    item.setTitle(R.string.hide_whiteboard);
                    MetaDB.storeWhiteboardState(this, deckID, 1);
                } else {
                    // Hide whiteboard
                    mWhiteboard.setVisibility(View.GONE);
                    item.setTitle(R.string.show_whiteboard);
                    MetaDB.storeWhiteboardState(this, deckID, 0);
                }
                refreshActionBar();
                return true;

            case R.id.action_search_dictionary:
                lookUpOrSelectText();
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
            if (resultCode != RESULT_CANCELED) {
                Log.i(AnkiDroidApp.TAG, "Saving card...");
                DeckTask.launchDeckTask(DeckTask.TASK_TYPE_UPDATE_FACT, mUpdateCardHandler, new DeckTask.TaskData(
                        mSched, mCurrentCard, true));
            } else {
                fillFlashcard();
            }
        }
        if (!mDisableClipboard) {
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
            if (mProgressDialog != null && mProgressDialog.isShowing()) {
                mProgressDialog.setMessage(getResources().getString(R.string.saving_changes));
            } else {
                mProgressDialog = StyledProgressDialog.show(AbstractFlashcardViewer.this, "",
                        getResources().getString(R.string.saving_changes), true);
            }
            DeckTask.launchDeckTask(DeckTask.TASK_TYPE_UNDO, mAnswerCardHandler, new DeckTask.TaskData(mSched));
        }
    }


    private void finishNoStorageAvailable() {
        AbstractFlashcardViewer.this.setResult(DeckPicker.RESULT_MEDIA_EJECTED);
        finish();
    }


    protected boolean editCard() {
        Intent editCard = new Intent(AbstractFlashcardViewer.this, CardEditor.class);
        editCard.putExtra(CardEditor.EXTRA_CALLER, CardEditor.CALLER_REVIEWER);
        sEditorCard = mCurrentCard;
        startActivityForResultWithAnimation(editCard, EDIT_CURRENT_CARD, ActivityTransitionAnimation.LEFT);
        return true;
    }


    protected void generateQuestionSoundList() {
        Sound.addSounds(mBaseUrl, mCurrentCard.qSimple(), Sound.SOUNDS_QUESTION);
    }


    protected void lookUpOrSelectText() {
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
        if (!mDisableClipboard && mClipboard != null) {
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
        if (!mDisableClipboard && mLookUpIcon.getVisibility() != View.GONE) {
            mLookUpIcon.setVisibility(View.GONE);
            enableViewAnimation(mLookUpIcon, ViewAnimation.fade(ViewAnimation.FADE_OUT, mFadeDuration, 0));
            clipboardSetText("");
        }
    }


    protected void showDeleteNoteDialog() {
        Dialog dialog;
        Resources res = getResources();
        StyledDialog.Builder builder = new StyledDialog.Builder(this);
        builder.setTitle(res.getString(R.string.delete_card_title));
        builder.setIcon(R.drawable.ic_dialog_alert);
        builder.setMessage(String.format(res.getString(R.string.delete_note_message),
                Utils.stripHTML(mCurrentCard.q(true))));
        builder.setPositiveButton(res.getString(R.string.dialog_positive_delete), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                DeckTask.launchDeckTask(DeckTask.TASK_TYPE_DISMISS_NOTE, mDismissCardHandler, new DeckTask.TaskData(
                        mSched, mCurrentCard, 3));
            }
        });
        builder.setNegativeButton(res.getString(R.string.dialog_cancel), null);
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
            AnkiDroidApp.saveExceptionReportFile(e, "AbstractReviewer-getRecommendedEase");
            closeReviewer(DeckPicker.RESULT_DB_ERROR, true);
            return 0;
        }
    }


    private void answerCard(int ease) {
        if (mInAnswer) {
            return;
        }
        mIsSelecting = false;
        hideLookupButton();
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

        DeckTask.launchDeckTask(DeckTask.TASK_TYPE_ANSWER_CARD, mAnswerCardHandler, new DeckTask.TaskData(mSched,
                mCurrentCard, mCurrentEase));
    }


    // Set the content view to the one provided and initialize accessors.
    protected void initLayout() {
        mMainLayout = findViewById(R.id.main_layout);
        Themes.setContentStyle(mMainLayout, Themes.CALLER_REVIEWER);

        mCardContainer = (FrameLayout) findViewById(R.id.flashcard_frame);

        mTopBarLayout = (RelativeLayout) findViewById(R.id.top_bar);
        mTopBarLayout.setOnClickListener(mCardStatisticsListener);

        mCardFrame = (FrameLayout) findViewById(R.id.flashcard);
        mTouchLayer = (FrameLayout) findViewById(R.id.touch_layer);
        mTouchLayer.setOnTouchListener(mGestureListener);
        if (!mDisableClipboard && mLongClickWorkaround) {
            mTouchLayer.setOnLongClickListener(mLongClickListener);
        }
        if (!mDisableClipboard) {
            mClipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        }
        mCardFrame.removeAllViews();
        if (!mChangeBorderStyle) {
            findViewById(R.id.flashcard_border).setVisibility(View.VISIBLE);
        }
        // hunt for input issue 720, like android issue 3341
        if (AnkiDroidApp.SDK_VERSION == 7 && (mCard != null)) {
            mCard.setFocusableInTouchMode(true);
        }

        // Initialize swipe
        gestureDetector = new GestureDetector(new MyGestureDetector());

        mProgressBar = (ProgressBar) findViewById(R.id.flashcard_progressbar);

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

        mTextBarNew = (TextView) findViewById(R.id.new_number);
        mTextBarLearn = (TextView) findViewById(R.id.learn_number);
        mTextBarReview = (TextView) findViewById(R.id.review_number);

        if (!mShowRemainingCardCount) {
            mTextBarNew.setVisibility(View.GONE);
            mTextBarLearn.setVisibility(View.GONE);
            mTextBarReview.setVisibility(View.GONE);
        }

        mCardTimer = (Chronometer) findViewById(R.id.card_time);

        mChosenAnswer = (TextView) findViewById(R.id.choosen_answer);

        if (mPrefWhiteboard) {
            mWhiteboard = new Whiteboard(this, mInvertedColors, mBlackWhiteboard);
            FrameLayout.LayoutParams lp2 = new FrameLayout.LayoutParams(
                    android.view.ViewGroup.LayoutParams.FILL_PARENT, android.view.ViewGroup.LayoutParams.FILL_PARENT);
            mWhiteboard.setLayoutParams(lp2);
            FrameLayout fl = (FrameLayout) findViewById(R.id.whiteboard);
            fl.addView(mWhiteboard);

            mWhiteboard.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    if (mShowWhiteboard) {
                        return false;
                    }
                    return gestureDetector.onTouchEvent(event);
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


    @SuppressLint("NewApi") // because of setDisplayZoomControls.
    private WebView createWebView() {
        WebView webView = new MyWebView(this);
        webView.setWillNotCacheDrawing(true);
        webView.setScrollBarStyle(View.SCROLLBARS_OUTSIDE_OVERLAY);
        boolean zoom_ctrls = false;
        if (AnkiDroidApp.SDK_VERSION > 11) {
            zoom_ctrls = true;
            webView.getSettings().setDisplayZoomControls(false);
        }
        webView.getSettings().setBuiltInZoomControls(zoom_ctrls);
        webView.getSettings().setSupportZoom(true);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.setWebChromeClient(new AnkiDroidWebChromeClient());
        if (AnkiDroidApp.SDK_VERSION > 7) {
            webView.setFocusableInTouchMode(false);
        }
        if (mPrefSafeDisplay) {
            AnkiDroidApp.getCompat().setScrollbarFadingEnabled(webView, false);
        }
        Log.i(AnkiDroidApp.TAG,
                "Focusable = " + webView.isFocusable() + ", Focusable in touch mode = "
                        + webView.isFocusableInTouchMode());

        // Filter any links using the custom "playsound" protocol defined in Sound.java.
        // We play sounds through these links when a user taps the sound icon.
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (url.startsWith("playsound:")) {
                    // Send a message that will be handled on the UI thread.
                    Message msg = Message.obtain();
                    String soundPath = url.replaceFirst("playsound:", "");
                    msg.obj = soundPath;
                    sHandler.sendMessage(msg);
                    return true;
                }
                try {
                    new URL(url);  // dummy variable to check if the string looks like an url
                } catch (MalformedURLException mue) {
                    // Ignore malformed urls by handling them and then doing nothing.
                    return true;
                }
                if (url.startsWith("file"))
                {
                    return false;  // Let the webview load files, i.e. local images.
                }
                Log.d(AnkiDroidApp.TAG, "Opening external link \"" + url + "\" with an Intent");
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                startActivity(intent);
                return true;
            }
        });

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
        mTextBarNew.setTextColor(invert ? res.getColor(R.color.new_count_night) : res.getColor(R.color.new_count));
        mTextBarLearn
                .setTextColor(invert ? res.getColor(R.color.learn_count_night) : res.getColor(R.color.learn_count));
        mTextBarReview.setTextColor(invert ? res.getColor(R.color.review_count_night) : res
                .getColor(R.color.review_count));
        mAnswerField.setTextColor(mForegroundColor);

        if (mSimpleCard != null) {
            mSimpleCard.setBackgroundColor(mCurrentBackgroundColor);
            mSimpleCard.setTextColor(mForegroundColor);
        }
        if (mCard != null) {
            mCard.setBackgroundColor(mCurrentBackgroundColor);
        }
        AnkiDroidApp.getCompat().setActionBarBackground(this,
                invert ? R.color.white_background_night : R.color.actionbar_background);
    }


    protected void showEaseButtons() {
        Resources res = getResources();

        // hide flipcard button
        mFlipCardLayout.setVisibility(View.GONE);

        int buttonCount;
        try {
            buttonCount = mSched.answerButtons(mCurrentCard);
        } catch (RuntimeException e) {
            AnkiDroidApp.saveExceptionReportFile(e, "AbstractReviewer-showEaseButtons");
            closeReviewer(DeckPicker.RESULT_DB_ERROR, true);
            return;
        }

        // Set correct label for each button
        switch (buttonCount) {
            case 2:
                mEase1.setText(res.getString(R.string.ease1_successive));
                mEase2.setText(res.getString(R.string.ease3_successive));
                mEase1Layout.setVisibility(View.VISIBLE);
                mEase2Layout.setVisibility(View.VISIBLE);
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
                mEase1Layout.setVisibility(View.VISIBLE);
                mEase2Layout.setVisibility(View.VISIBLE);
                mEase3Layout.setVisibility(View.VISIBLE);
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
                mEase1Layout.setVisibility(View.VISIBLE);
                mEase2Layout.setVisibility(View.VISIBLE);
                mEase3Layout.setVisibility(View.VISIBLE);
                mEase4Layout.setVisibility(View.VISIBLE);
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


    protected void hideEaseButtons() {
        mEase1Layout.setVisibility(View.GONE);
        mEase2Layout.setVisibility(View.GONE);
        mEase3Layout.setVisibility(View.GONE);
        mEase4Layout.setVisibility(View.GONE);

        if (mFlipCardLayout.getVisibility() != View.VISIBLE) {
            mFlipCardLayout.setVisibility(View.VISIBLE);
            mFlipCardLayout.requestFocus();
        } else if (typeAnswer()) {
            mAnswerField.requestFocus();
        }
    }


    private void switchTopBarVisibility(int visible) {
        if (mShowTimer) {
            mCardTimer.setVisibility(visible);
        }
        if (mShowRemainingCardCount) {
            mTextBarNew.setVisibility(visible);
            mTextBarLearn.setVisibility(visible);
            mTextBarReview.setVisibility(visible);
        }
        mChosenAnswer.setVisibility(visible);
    }


    private void initControls() {
        mCardFrame.setVisibility(View.VISIBLE);
        if (mShowRemainingCardCount) {
            mTextBarNew.setVisibility(View.VISIBLE);
            mTextBarLearn.setVisibility(View.VISIBLE);
            mTextBarReview.setVisibility(View.VISIBLE);
        }
        mChosenAnswer.setVisibility(View.VISIBLE);
        mFlipCardLayout.setVisibility(View.VISIBLE);

        if (mPrefWhiteboard) {
            mWhiteboard.setVisibility(mShowWhiteboard ? View.VISIBLE : View.GONE);
        }
        mAnswerField.setVisibility(typeAnswer() ? View.VISIBLE : View.GONE);
        mAnswerField.setOnEditorActionListener(new EditText.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    mFlipCardLayout.performClick();
                }
                return false; // We don’t “handle” this. Let Android hide the input method.
            }
        });
    }


    private SharedPreferences restorePreferences() {
        SharedPreferences preferences = AnkiDroidApp.getSharedPrefs(getBaseContext());
        mPrefHideDueCount = preferences.getBoolean("hideDueCount", false);
        mPrefWhiteboard = preferences.getBoolean("whiteboard", false);
        mPrefWriteAnswers = !preferences.getBoolean("writeAnswersDisable", false);
        mLongClickWorkaround = preferences.getBoolean("textSelectionLongclickWorkaround", false);
        // mDeckFilename = preferences.getString("deckFilename", "");
        mNightMode = preferences.getBoolean("invertedColors", false);
        mInvertedColors = mNightMode;
        mBlackWhiteboard = preferences.getBoolean("blackWhiteboard", true);
        mPrefFullscreenReview = preferences.getBoolean("fullscreenReview", false);
        mDisplayFontSize = preferences.getInt("relativeDisplayFontSize", 100);// Card.DEFAULT_FONT_SIZE_RATIO);
        mRelativeImageSize = preferences.getInt("relativeImageSize", 100);
        mRelativeButtonSize = preferences.getInt("answerButtonSize", 100);
        mInputWorkaround = preferences.getBoolean("inputWorkaround", false);
        mPrefFixArabic = preferences.getBoolean("fixArabicText", false);
        mSpeakText = preferences.getBoolean("tts", false);
        mPrefSafeDisplay = preferences.getBoolean("safeDisplay", false);
        mPrefUseTimer = preferences.getBoolean("timeoutAnswer", false);
        mWaitAnswerSecond = preferences.getInt("timeoutAnswerSeconds", 20);
        mWaitQuestionSecond = preferences.getInt("timeoutQuestionSeconds", 60);
        mScrollingButtons = preferences.getBoolean("scrolling_buttons", false);
        mDoubleScrolling = preferences.getBoolean("double_scrolling", false);
        mPrefCenterVertically = preferences.getBoolean("centerVertically", false);

        mGesturesEnabled = AnkiDroidApp.initiateGestures(this, preferences);
        if (mGesturesEnabled) {
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
        if (mLongClickWorkaround) {
            mGestureLongclick = GESTURE_LOOKUP;
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
                mSimpleCard.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 10, getResources()
                        .getDisplayMetrics())
                        * mDisplayFontSize / 100);
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
            if (mSimpleCard.getVisibility() != View.VISIBLE || (mCard != null && mCard.getVisibility() == View.VISIBLE)) {
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
                }
            }
            if (mCard.getVisibility() != View.VISIBLE
                    || (mSimpleCard != null && mSimpleCard.getVisibility() == View.VISIBLE)) {
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

        // Clean answer field
        if (typeAnswer()) {
            mAnswerField.setText("");
        }

        if (mPrefWhiteboard) {
            mWhiteboard.clear();
        }
    }


    protected void updateScreenCounts() {
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
        AnkiDroidApp.getCompat().setSubtitle(this,
                getResources().getQuantityString(R.plurals.reviewer_window_title, eta, eta), mInvertedColors);

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

        mTextBarNew.setText(newCount);
        mTextBarLearn.setText(lrnCount);
        mTextBarReview.setText(revCount);
    }

    /*
     * Handler for the delay in auto showing question and/or answer One toggle for both question and answer, could set
     * longer delay for auto next question
     */
    protected Handler mTimeoutHandler = new Handler();

    protected Runnable mShowQuestionTask = new Runnable() {
        public void run() {
            // Assume hitting the "Again" button when auto next question
            if (mEase1Layout.isEnabled() && mEase1Layout.getVisibility() == View.VISIBLE) {
                mEase1Layout.performClick();
            }
        }
    };

    protected Runnable mShowAnswerTask = new Runnable() {
        @Override
        public void run() {
            if (mFlipCardLayout.isEnabled() && mFlipCardLayout.getVisibility() == View.VISIBLE) {
                mFlipCardLayout.performClick();
            }
        }
    };


    protected void initTimer() {
        mShowTimer = mCurrentCard.showTimer();
        if (mShowTimer && mCardTimer.getVisibility() == View.INVISIBLE) {
            mCardTimer.setVisibility(View.VISIBLE);
        } else if (!mShowTimer && mCardTimer.getVisibility() != View.INVISIBLE) {
            mCardTimer.setVisibility(View.INVISIBLE);
        }
        mCardTimer.setBase(SystemClock.elapsedRealtime());
        mCardTimer.start();
    }


    protected void displayCardQuestion() {
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

        String question;
        if (mCurrentSimpleInterface) {
            question = mCurrentCard.qSimple();
        } else {
            question = mCurrentCard.q();
        }
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


    /**
     * Clean up the correct answer text, so it can be used for the comparison with the typed text
     *
     * @param answer The content of the field the text typed by the user is compared to.
     * @return The correct answer text, with actual HTML and media references removed, and HTML entities unescaped.
     */
    protected String cleanCorrectAnswer(String answer) {
        if (answer == null || answer.equals("")) {
            return "";
        }
        answer = answer.trim();
        Matcher matcher = sSpanPattern.matcher(Utils.stripHTMLMedia(answer));
        String answerText = matcher.replaceAll("");
        matcher = sBrPattern.matcher(answerText);
        answerText = matcher.replaceAll("\n");
        matcher = Sound.sSoundPattern.matcher(answerText);
        answerText = matcher.replaceAll("");
        try {
            return AnkiDroidApp.getCompat().nfcNormalized(answerText);
        } catch (APIVersionException e) {
            return answerText;
        }
    }

    /**
     * Clean up the typed answer text, so it can be used for the comparison with the correct answer
     *
     * @param answer The answer text typed by the user.
     * @return The typed answer text, cleaned up.
     */
    protected String cleanTypedAnswer(String answer) {
        if (answer == null || answer.equals("")) {
            return "";
        }
        try {
            return AnkiDroidApp.getCompat().nfcNormalized(answer.trim());
        } catch (APIVersionException e) {
            return answer.trim();
        }
    }


    protected void displayCardAnswer() {
        Log.i(AnkiDroidApp.TAG, "displayCardAnswer");

        // prevent answering (by e.g. gestures) before card is loaded
        if (mCurrentCard == null) {
            return;
        }

        sDisplayAnswer = true;

        String answer;
        if (mCurrentSimpleInterface) {
            answer = mCurrentCard.aSimple();
        } else {
            answer = mCurrentCard.a();
        }

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

            mAnswerField.setVisibility(View.GONE);
            if (mPrefFixArabic) {
                // reshape
                mTypeCorrect = ArabicUtilities.reshapeSentence(mTypeCorrect, true);
            }
            // Clean up the user answer and the correct answer
            String userAnswer = cleanTypedAnswer(mAnswerField.getText().toString());
            String correctAnswer = cleanCorrectAnswer(mTypeCorrect);
            Log.i(AnkiDroidApp.TAG, "correct answer = " + correctAnswer);
            Log.i(AnkiDroidApp.TAG, "user answer = " + userAnswer);

            answer = typeAnsAnswerFilter(answer, userAnswer, correctAnswer);
            displayString = enrichWithQADiv(answer, true);
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


    /**
     * getAnswerFormat returns the answer part of this card's template as entered by user, without any parsing
     */
    public String getAnswerFormat() {
        try {
            JSONObject model = mCurrentCard.model();
            JSONObject template;
            if (model.getInt("type") == Consts.MODEL_STD) {
                template = model.getJSONArray("tmpls").getJSONObject(mCurrentCard.getOrd());
            } else {
                template = model.getJSONArray("tmpls").getJSONObject(0);
            }

            return template.getString("afmt");
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }


    private void updateCard(String content) {
        Log.i(AnkiDroidApp.TAG, "updateCard");

        if (mCurrentSimpleInterface) {
            fillFlashcard();
        } else {

            // Check whether there is a hard coded font-size in the content and apply the relative font size
            // Check needs to be done before CSS is applied to content;
            content = recalculateHardCodedFontSize(content, mDisplayFontSize);

            // Add CSS for font color and font size
            if (mCurrentCard == null) {
                mCard.getSettings().setDefaultFontSize(calculateDynamicFontSize(content));
            }

            if (sDisplayAnswer) {
                // don't add answer sounds multiple times, such as when reshowing card after exiting editor
                // additionally, this condition reduces computation time
                if (!mAnswerSoundsAdded) {
                    String afmt = getAnswerFormat();
                    String answerSoundSource = content;
                    if (afmt.contains("{{FrontSide}}")) { // don't grab front side audio
                        String fromFrontSide = mCurrentCard._getQA(false).get("q");
                        answerSoundSource = content.replace(fromFrontSide, "");
                    }
                    Sound.addSounds(mBaseUrl, answerSoundSource, Sound.SOUNDS_ANSWER);
                    mAnswerSoundsAdded = true;
                }
            } else {
                // reset sounds each time first side of card is displayed, which may happen repeatedly without ever
                // leaving the card (such as when edited)
                Sound.resetSounds();
                mAnswerSoundsAdded = false;
                Sound.addSounds(mBaseUrl, content, Sound.SOUNDS_QUESTION);
            }

            content = Sound.expandSounds(mBaseUrl, content);

            // In order to display the bold style correctly, we have to change
            // font-weight to 700
            content = content.replace("font-weight:600;", "font-weight:700;");

            // CSS class for card-specific styling
            String cardClass = "card card" + (mCurrentCard.getOrd() + 1);

            if (mPrefCenterVertically) {
                cardClass += " vertically_centered";
            }

            Log.i(AnkiDroidApp.TAG, "content card = \n" + content);
            StringBuilder style = new StringBuilder();
            mExtensions.updateCssStyle(style);

            // Scale images.
            if (mRelativeImageSize != 100) {
                style.append(String.format("img { zoom: %s }\n", mRelativeImageSize / 100.0));
            }
            Log.i(AnkiDroidApp.TAG, "::style::" + style);

            if (mNightMode) {
                content = HtmlColors.invertColors(content);
                cardClass += " night_mode";
            }

            content = SmpToHtmlEntity(content);
            mCardContent = new SpannedString(mCardTemplate.replace("::content::", content)
                    .replace("::style::", style.toString()).replace("::class::", cardClass));
            Log.i(AnkiDroidApp.TAG, "base url = " + mBaseUrl);

            if (SAVE_CARD_CONTENT) {
                try {
                    FileOutputStream f = new FileOutputStream(new File(AnkiDroidApp.getCurrentAnkiDroidDirectory(),
                            "card.html"));
                    try {
                        f.write(mCardContent.toString().getBytes());
                    } finally {
                        f.close();
                    }
                } catch (IOException e) {
                    Log.d(AnkiDroidApp.TAG, "failed to save card", e);
                }
            }
            fillFlashcard();
        }

        if (!mConfigurationChanged) {
            playSounds(false); // Play sounds if appropriate
        }
    }


    /**
     * Converts characters in Unicode Supplementary Multilingual Plane (SMP) to their equivalent Html Entities. This is
     * done because webview has difficulty displaying these characters.
     *
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
     * Plays sounds (or TTS, if configured) for currently shown side of card.
     *
     * @param doAudioReplay indicates an anki desktop-like replay call is desired, whose behavior is identical to
     *            pressing the keyboard shortcut R on the desktop
     */
    protected void playSounds(boolean doAudioReplay) {
        boolean replayQuestion = getConfigForCurrentCard().optBoolean("replayq", true);
        boolean autoPlayEnabled;
        try {
            autoPlayEnabled = getConfigForCurrentCard().getBoolean("autoplay");
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        if (autoPlayEnabled || doAudioReplay) {
            // We need to play the sounds from the proper side of the card
            if (!mSpeakText) { // Text to speech not in effect here
                if (doAudioReplay && replayQuestion && sDisplayAnswer) {
                    // only when all of the above are true will question be played with answer, to match desktop
                    Sound.playSounds(Sound.SOUNDS_QUESTION_AND_ANSWER);
                } else if (sDisplayAnswer) {
                    Sound.playSounds(Sound.SOUNDS_ANSWER);
                } else { // question is displayed
                    Sound.playSounds(Sound.SOUNDS_QUESTION);
                }
            } else { // Text to speech is in affect here
                // If the question is displayed or if the question should be replayed, read the question
                if (!sDisplayAnswer || doAudioReplay && replayQuestion) {
                    readCardText(mCurrentCard, Sound.SOUNDS_QUESTION);
                }
                if (sDisplayAnswer) {
                    readCardText(mCurrentCard, Sound.SOUNDS_ANSWER);
                }
            }
        }
    }


    /**
     * Reads the text (using TTS) for the given side of a card.
     *
     * @param card The card to play TTS for
     * @param cardSide The side of the current card to play TTS for
     */
    private static void readCardText(final Card card, final int cardSide) {
        if (Sound.SOUNDS_QUESTION == cardSide) {
            ReadText.textToSpeech(Utils.stripHTML(card.q(true)), getDeckIdForCard(card), card.getOrd(),
                    Sound.SOUNDS_QUESTION);
        } else if (Sound.SOUNDS_ANSWER == cardSide) {
            ReadText.textToSpeech(Utils.stripHTML(card.getPureAnswerForReading()), getDeckIdForCard(card),
                    card.getOrd(), Sound.SOUNDS_ANSWER);
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


    public void fillFlashcard() {
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
            if (AnkiDroidApp.SDK_VERSION == 7) {
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
        if (mShowTimer && mCardTimer.getVisibility() == View.INVISIBLE) {
            switchTopBarVisibility(View.VISIBLE);
        }
        if (!sDisplayAnswer) {
            updateForNewCard();
            if (mShowWhiteboard) {
                mWhiteboard.clear();
            }
        }
    }


    public void showFlashcard(boolean visible) {
        mCardContainer.setVisibility(visible ? View.VISIBLE : View.INVISIBLE);
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
        StringBuilder sb = new StringBuilder();
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
     * unit measure is used (e.g. 'em'), then only the outer tag 'span' or 'div' tag in a hierarchy of such tags is
     * adjusted. This is not bullet-proof, a combination of font-size in span and in css classes will break this logic,
     * but let's just avoid building an HTML parser for this feature. Anything that threatens common sense will break
     * this logic, eg nested span/divs with CSS classes having font-size declarations with relative units (40% dif
     * inside 120% div inside 60% div). Broken HTML also breaks this. Feel free to improve, but please keep it short and
     * fast.
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
        return sb.toString();
    }


    /**
     * @return true if the AnkiDroid preference for writing answer is true and if the Anki Deck CardLayout specifies a
     *         field to query
     */
    private final boolean typeAnswer() {
        return mPrefWriteAnswers && null != mTypeCorrect;
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
        return Math.max(DYNAMIC_FONT_MIN_SIZE, DYNAMIC_FONT_MAX_SIZE - realContent.length() / DYNAMIC_FONT_FACTOR);
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
     * Returns true if we should update the content of a single {@link WebView} (called quick update) instead of switch
     * between two instances.
     * <p>
     * Webview switching is needed in some versions of Android when using custom fonts because of a memory leak in
     * WebView.
     * <p>
     * It is also needed to solve a refresh issue on Nook devices.
     *
     * @return true if we should use a single WebView
     */
    private boolean shouldUseQuickUpdate() {
        return !mPrefSafeDisplay;
    }


    protected void executeCommand(int which) {
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
                if (mSched.getCol().undoAvailable()) {
                    undo();
                }
                break;
            case GESTURE_EDIT:
                editCard();
                break;
            case GESTURE_MARK:
                DeckTask.launchDeckTask(DeckTask.TASK_TYPE_MARK_CARD, mMarkCardHandler, new DeckTask.TaskData(mSched,
                        mCurrentCard, 0));
                break;
            case GESTURE_LOOKUP:
                lookUpOrSelectText();
                break;
            case GESTURE_BURY:
                DeckTask.launchDeckTask(DeckTask.TASK_TYPE_DISMISS_NOTE, mDismissCardHandler, new DeckTask.TaskData(
                        mSched, mCurrentCard, 0));
                break;
            case GESTURE_SUSPEND:
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
                playSounds(true);
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


    protected void closeReviewer(int result, boolean saveDeck) {
        mTimeoutHandler.removeCallbacks(mShowAnswerTask);
        mTimeoutHandler.removeCallbacks(mShowQuestionTask);
        mTimerHandler.removeCallbacks(removeChosenAnswerText);
        longClickHandler.removeCallbacks(longClickTestRunnable);
        longClickHandler.removeCallbacks(startLongClickAction);

        AbstractFlashcardViewer.this.setResult(result);

        // updateBigWidget(!mCardFrame.isEnabled());

        if (saveDeck) {
            UIUtils.saveCollectionInBackground();
        }
        finish();
        ActivityTransitionAnimation.slide(AbstractFlashcardViewer.this, ActivityTransitionAnimation.RIGHT);
    }


    private void refreshActionBar() {
        AnkiDroidApp.getCompat().invalidateOptionsMenu(AbstractFlashcardViewer.this);
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
        protected void onScrollChanged(int horiz, int vert, int oldHoriz, int oldVert) {
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
            @Override
            public void run() {
                mIsXScrolling = false;
            }
        };
        private final Runnable scrollYRunnable = new Runnable() {
            @Override
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
        return gestureDetector.onTouchEvent(event);
    }

    class ScrollTextView extends TextView {

        public ScrollTextView(Context context) {
            super(context);
        }


        @Override
        protected void onScrollChanged(int horiz, int vert, int oldHoriz, int oldVert) {
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
            @Override
            public void run() {
                mIsXScrolling = false;
            }
        };
        private final Runnable scrollYRunnable = new Runnable() {
            @Override
            public void run() {
                mIsYScrolling = false;
            }
        };

    }

    private TagHandler mSimpleInterfaceTagHandler = new TagHandler() {

        @Override
        public void handleTag(boolean opening, String tag, Editable output, XMLReader xmlReader) {
            // if (tag.equalsIgnoreCase("div")) {
            // output.append("\n");
            // } else
            if (tag.equalsIgnoreCase("strike") || tag.equals("s")) {
                int len = output.length();
                if (opening) {
                    output.setSpan(new StrikethroughSpan(), len, len, Spanned.SPAN_MARK_MARK);
                } else {
                    Object obj = getLast(output, StrikethroughSpan.class);
                    int where = output.getSpanStart(obj);

                    output.removeSpan(obj);

                    if (where != len) {
                        output.setSpan(new StrikethroughSpan(), where, len, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }
                }
            }
        }


        private Object getLast(Editable text, Class kind) {
            Object[] objs = text.getSpans(0, text.length(), kind);

            if (objs.length == 0) {
                return null;
            } else {
                for (int i = objs.length; i > 0; i--) {
                    if (text.getSpanFlags(objs[i - 1]) == Spanned.SPAN_MARK_MARK) {
                        return objs[i - 1];
                    }
                }
                return null;
            }
        }
    };

    private Html.ImageGetter mSimpleInterfaceImagegetter = new Html.ImageGetter() {

        @Override
        public Drawable getDrawable(String source) {
            String path = AnkiDroidApp.getCurrentAnkiDroidDirectory() + "/collection.media/" + source;
            if ((new File(path)).exists()) {
                Drawable d = Drawable.createFromPath(path);
                d.setBounds(0, 0, d.getIntrinsicWidth(), d.getIntrinsicHeight());
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
