/****************************************************************************************
 * Copyright (c) 2011 Kostas Spyropoulos <inigo.aldana@gmail.com>                       *
 * Copyright (c) 2014 Bruno Romero de Azevedo <brunodea@inf.ufsm.br>                    *
 * Copyright (c) 2014–15 Roland Sieker <ospalh@gmail.com>                               *
 * Copyright (c) 2015 Timothy Rae <perceptualchaos2@gmail.com>                          *
 * Copyright (c) 2016 Mark Carter <mark@marcardar.com>                                  *
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
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.os.Vibrator;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.GestureDetectorCompat;
import android.support.v7.app.ActionBar;
import android.text.ClipboardManager;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.SpannedString;
import android.text.TextUtils;
import android.text.style.UnderlineSpan;
import android.util.TypedValue;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
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
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.afollestad.materialdialogs.MaterialDialog;
import com.afollestad.materialdialogs.util.TypefaceHelper;
import com.ichi2.anim.ActivityTransitionAnimation;
import com.ichi2.anim.ViewAnimation;
import com.ichi2.anki.receiver.SdCardReceiver;
import com.ichi2.anki.reviewer.ReviewerExtRegistry;
import com.ichi2.async.DeckTask;
import com.ichi2.compat.CompatHelper;
import com.ichi2.libanki.Card;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Consts;
import com.ichi2.libanki.Note;
import com.ichi2.libanki.Sched;
import com.ichi2.libanki.Sound;
import com.ichi2.libanki.Utils;
import com.ichi2.themes.HtmlColors;
import com.ichi2.themes.Themes;
import com.ichi2.utils.DiffEngine;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import timber.log.Timber;

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
    public static final int DECK_OPTIONS = 1;

    /** Constant for class attribute signaling answer */
    public static final String ANSWER_CLASS = "answer";

    /** Constant for class attribute signaling question */
    public static final String QUESTION_CLASS = "question";

    /** Max size of the font for dynamic calculation of font size */
    private static final int DYNAMIC_FONT_MAX_SIZE = 14;

    /** Min size of the font for dynamic calculation of font size */
    private static final int DYNAMIC_FONT_MIN_SIZE = 3;
    private static final int DYNAMIC_FONT_FACTOR = 5;

    public static final int EASE_1 = 1;
    public static final int EASE_2 = 2;
    public static final int EASE_3 = 3;
    public static final int EASE_4 = 4;

    /** Maximum time in milliseconds to wait before accepting answer button presses. */
    private static final int DOUBLE_TAP_IGNORE_THRESHOLD = 200;

    /** Time to wait in milliseconds before resuming fullscreen mode **/
    protected static final int INITIAL_HIDE_DELAY = 200;

    /** Regex pattern used in removing tags from text before diff */
    private static final Pattern sSpanPattern = Pattern.compile("</?span[^>]*>");
    private static final Pattern sBrPattern = Pattern.compile("<br\\s?/?>");

    // Type answer patterns
    private static final Pattern sTypeAnsPat = Pattern.compile("\\[\\[type:(.+?)\\]\\]");
    private static final Pattern sTypeAnsTyped = Pattern.compile("typed=([^&]*)");

    /** to be sent to and from the card editor */
    private static Card sEditorCard;

    protected static boolean sDisplayAnswer = false;

    private boolean mTtsInitialized = false;
    private boolean mReplayOnTtsInit = false;

    protected static final int MENU_DISABLED = 3;


    /**
     * Broadcast that informs us when the sd card is about to be unmounted
     */
    private BroadcastReceiver mUnmountReceiver = null;

    /**
     * Variables to hold preferences
     */
    private boolean mPrefHideDueCount;
    private boolean mPrefShowETA;
    private boolean mShowTimer;
    protected boolean mPrefWhiteboard;
    private boolean mShowTypeAnswerField;
    private boolean mInputWorkaround;
    private boolean mLongClickWorkaround;
    private int mPrefFullscreenReview;
    private int mCardZoom;
    private int mImageZoom;
    private int mRelativeButtonSize;
    private boolean mDoubleScrolling;
    private boolean mScrollingButtons;
    private boolean mGesturesEnabled;
    // Android WebView
    protected boolean mSpeakText;
    protected boolean mDisableClipboard = false;
    protected boolean mNightMode = false;
    private boolean mPrefSafeDisplay;
    protected boolean mPrefUseTimer;
    private boolean mPrefCenterVertically;
    protected boolean mUseInputTag;

    // Preferences from the collection
    private boolean mShowNextReviewTime;
    private boolean mShowRemainingCardCount;

    // Answer card & cloze deletion variables
    private String mTypeCorrect = null;
    // The correct answer in the compare to field if answer should be given by learner. Null if no answer is expected.
    private String mTypeInput = "";  // What the learner actually typed
    private String mTypeFont = "";  // Font face of the compare to field
    private int mTypeSize = 0;  // Its font size
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
    private WebView mNextCard;
    private FrameLayout mCardFrame;
    private FrameLayout mTouchLayer;
    private TextView mTextBarNew;
    private TextView mTextBarLearn;
    private TextView mTextBarReview;
    private TextView mChosenAnswer;
    protected TextView mNext1;
    protected TextView mNext2;
    protected TextView mNext3;
    protected TextView mNext4;
    private Button mFlipCard;
    protected EditText mAnswerField;
    protected TextView mEase1;
    protected TextView mEase2;
    protected TextView mEase3;
    protected TextView mEase4;
    protected LinearLayout mFlipCardLayout;
    protected LinearLayout mEase1Layout;
    protected LinearLayout mEase2Layout;
    protected LinearLayout mEase3Layout;
    protected LinearLayout mEase4Layout;
    protected RelativeLayout mTopBarLayout;
    private Chronometer mCardTimer;
    protected Whiteboard mWhiteboard;
    private ClipboardManager mClipboard;

    protected Card mCurrentCard;
    private int mCurrentEase;

    private boolean mButtonHeightSet = false;

    private boolean mConfigurationChanged = false;
    private int mShowChosenAnswerLength = 2000;

    /**
     * A record of the last time the "show answer" or ease buttons were pressed. We keep track
     * of this time to ignore accidental button presses.
     */
    private long mLastClickTime;




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
    private GestureDetectorCompat gestureDetector;

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

    /**
     * Custom button allocation
     */
    protected Map<Integer, Integer> mCustomButtons = new HashMap<>();

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
    private static final int GESTURE_BURY_CARD = 12;
    private static final int GESTURE_SUSPEND_CARD = 13;
    protected static final int GESTURE_DELETE = 14;
    protected static final int GESTURE_PLAY_MEDIA = 16;
    protected static final int GESTURE_EXIT = 17;
    private static final int GESTURE_BURY_NOTE = 18;
    private static final int GESTURE_SUSPEND_NOTE = 19;


    private Spanned mCardContent;
    private String mBaseUrl;

    private int mFadeDuration = 300;

    protected Sched mSched;

    private ReviewerExtRegistry mExtensions;

    private Sound mSoundPlayer = new Sound();

    private long mUseTimerDynamicMS;

    // private int zEase;

    // ----------------------------------------------------------------------------
    // LISTENERS
    // ----------------------------------------------------------------------------

    private Handler mHandler = new Handler() {

        @Override
        public void handleMessage(Message msg) {
            mSoundPlayer.stopSounds();
            mSoundPlayer.playSound((String) msg.obj, null);
        }
    };

    private final Handler longClickHandler = new Handler();
    private final Runnable longClickTestRunnable = new Runnable() {
        @Override
        public void run() {
            Timber.i("AbstractFlashcardViewer:: onEmulatedLongClick");
            // Show hint about lookup function if dictionary available and Webview version supports text selection
            if (!mDisableClipboard && Lookup.isAvailable() && CompatHelper.isHoneycomb()) {
                String lookupHint = getResources().getString(R.string.lookup_hint);
                UIUtils.showThemedToast(AbstractFlashcardViewer.this, lookupHint, false);
            }
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


    // Handler for the "show answer" button
    private View.OnClickListener mFlipCardListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            Timber.i("AbstractFlashcardViewer:: Show answer button pressed");
            // Ignore what is most likely an accidental double-tap.
            if (SystemClock.elapsedRealtime() - mLastClickTime < DOUBLE_TAP_IGNORE_THRESHOLD) {
                return;
            }
            mLastClickTime = SystemClock.elapsedRealtime();
            mTimeoutHandler.removeCallbacks(mShowAnswerTask);
            displayCardAnswer();
        }
    };

    private View.OnClickListener mSelectEaseHandler = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            // Ignore what is most likely an accidental double-tap.
            if (SystemClock.elapsedRealtime() - mLastClickTime < DOUBLE_TAP_IGNORE_THRESHOLD) {
                return;
            }
            mLastClickTime = SystemClock.elapsedRealtime();
            mTimeoutHandler.removeCallbacks(mShowQuestionTask);
            switch (view.getId()) {
                case R.id.flashcard_layout_ease1:
                    Timber.i("AbstractFlashcardViewer:: EASE_1 pressed");
                    answerCard(EASE_1);
                    break;
                case R.id.flashcard_layout_ease2:
                    Timber.i("AbstractFlashcardViewer:: EASE_2 pressed");
                    answerCard(EASE_2);
                    break;
                case R.id.flashcard_layout_ease3:
                    Timber.i("AbstractFlashcardViewer:: EASE_3 pressed");
                    answerCard(EASE_3);
                    break;
                case R.id.flashcard_layout_ease4:
                    Timber.i("AbstractFlashcardViewer:: EASE_4 pressed");
                    answerCard(EASE_4);
                    break;
                default:
                    mCurrentEase = 0;
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
                    default:
                        longClickHandler.removeCallbacks(longClickTestRunnable);
                        mTouchStarted = false;
                }
            }
            try {
                if (event != null) {
                    mCard.dispatchTouchEvent(event);
                }
            } catch (NullPointerException e) {
                Timber.e(e, "Error on dispatching touch event");
                if (mInputWorkaround) {
                    Timber.e(e, "Error on using InputWorkaround");
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
            Timber.i("AbstractFlashcardViewer:: onLongClick");
            Vibrator vibratorManager = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            vibratorManager.vibrate(50);
            longClickHandler.postDelayed(startLongClickAction, 300);
            return true;
        }
    };


    protected DeckTask.TaskListener mDismissCardHandler = new DeckTask.TaskListener() {
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
        }
    };


    private DeckTask.TaskListener mUpdateCardHandler = new DeckTask.TaskListener() {
        private boolean mNoMoreCards;


        @Override
        public void onPreExecute() {
            showProgressBar();
        }


        @Override
        public void onProgressUpdate(DeckTask.TaskData... values) {
            boolean cardChanged = false;
            if (mCurrentCard != values[0].getCard()) {
                /*
                 * Before updating mCurrentCard, we check whether it is changing or not. If the current card changes,
                 * then we need to display it as a new card, without showing the answer.
                 */
                sDisplayAnswer = false;
                cardChanged = true;  // Keep track of that so we can run a bit of new-card code
            }
            mCurrentCard = values[0].getCard();
            if (mCurrentCard == null) {
                // If the card is null means that there are no more cards scheduled for review.
                mNoMoreCards = true;
                showProgressBar();
                return;
            }
            if (mPrefWhiteboard && mWhiteboard != null) {
                mWhiteboard.clear();
            }

            if (sDisplayAnswer) {
                mSoundPlayer.resetSounds(); // load sounds from scratch, to expose any edit changes
                mAnswerSoundsAdded = false; // causes answer sounds to be reloaded
                generateQuestionSoundList(); // questions must be intentionally regenerated
                displayCardAnswer();
            } else {
                if (cardChanged) {
                    updateTypeAnswerInfo();
                }
                displayCardQuestion();
                mCurrentCard.startTimer();
                initTimer();
            }
            hideProgressBar();
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
        }
    };

    protected DeckTask.TaskListener mAnswerCardHandler = new DeckTask.TaskListener() {
        private boolean mNoMoreCards;


        @Override
        public void onPreExecute() {
            blockControls();
        }


        @Override
        public void onProgressUpdate(DeckTask.TaskData... values) {
            Resources res = getResources();

            if (mSched == null) {
                // TODO: proper testing for restored activity
                finishWithoutAnimation();
                return;
            }

            mCurrentCard = values[0].getCard();
            if (mCurrentCard == null) {
                // If the card is null means that there are no more cards scheduled for review.
                mNoMoreCards = true;
            } else {
                // Start reviewing next card
                updateTypeAnswerInfo();
                hideProgressBar();
                AbstractFlashcardViewer.this.unblockControls();
                AbstractFlashcardViewer.this.displayCardQuestion();
            }

            // Since reps are incremented on fetch of next card, we will miss counting the
            // last rep since there isn't a next card. We manually account for it here.
            if (mNoMoreCards) {
                mSched.setReps(mSched.getReps() + 1);
            }

            Long[] elapsed = getCol().timeboxReached();
            if (elapsed != null) {
                // AnkiDroid is always counting one rep ahead, so we decrement it before displaying
                // it to the user.
                int nCards = elapsed[1].intValue() - 1;
                int nMins = elapsed[0].intValue() / 60;
                String mins = res.getQuantityString(R.plurals.timebox_reached_minutes, nMins, nMins);
                String timeboxMessage = res.getQuantityString(R.plurals.timebox_reached, nCards, nCards, mins);
                UIUtils.showThemedToast(AbstractFlashcardViewer.this, timeboxMessage, true);
                getCol().startTimebox();
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
                closeReviewer(RESULT_NO_MORE_CARDS, true);
            }
            // set the correct mark/unmark icon on action bar
            refreshActionBar();
            findViewById(R.id.root_layout).requestFocus();
        }


        @Override
        public void onCancelled() {
        }
    };


    /**
     * Extract type answer/cloze text and font/size
     */
    private void updateTypeAnswerInfo() {
        mTypeCorrect = null;
        mTypeInput = "";
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
                    mTypeCorrect = mCurrentCard.note().getItem(name);
                    if (clozeIdx != 0) {
                        // narrow to cloze
                        mTypeCorrect = contentForCloze(mTypeCorrect, clozeIdx);
                    }
                    mTypeFont = (String) (ja.getJSONObject(i).get("font"));
                    mTypeSize = (int) (ja.getJSONObject(i).get("size"));
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
        StringBuilder sb = new StringBuilder();
        if (mUseInputTag) {
            // These functions are definde in the JavaScript file assets/scripts/card.js. We get the text back in
            // shouldOverrideUrlLoading() in createWebView() in this file.
            sb.append("<center>\n<input type=text name=typed id=typeans onfocus=\"taFocus();\" " +
                      "onblur=\"taBlur(this);\" onKeyPress=\"return taKey(this, event)\" autocomplete=\"off\" ");
            // We have to watch out. For the preview we don’t know the font or font size. Skip those there. (Anki
            // desktop just doesn’t show the input tag there. Do it with standard values here instead.)
            if (mTypeFont != null && !TextUtils.isEmpty(mTypeFont) && mTypeSize > 0) {
                sb.append("style=\"font-family: '").append(mTypeFont).append("'; font-size: ")
                        .append(Integer.toString(mTypeSize)).append("px;\" ");
            }
            sb.append(">\n</center>\n");
        } else {
            sb.append("<span id=typeans class=\"typePrompt");
            if (!mShowTypeAnswerField) {
                sb.append(" typeOff");
            }
            sb.append("\">........</span>");
        }
        return m.replaceAll(sb.toString());
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
        if (!mUseInputTag && !mShowTypeAnswerField) {
            sb.append(" class=\"typeOff\"");
        }
        sb.append("><code id=typeans>");
        if (!TextUtils.isEmpty(userAnswer)) {
            // The user did type something.
            if (userAnswer.equals(correctAnswer)) {
                // and it was right.
                sb.append(DiffEngine.wrapGood(correctAnswer));
                sb.append("\u2714"); // Heavy check mark
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
            if (mShowTypeAnswerField) {
                sb.append(DiffEngine.wrapMissing(correctAnswer));
            } else {
                sb.append(correctAnswer);
            }
        }
        sb.append("</code></div>");
        return m.replaceAll(sb.toString());
    }


    /**
     * Return the correct answer to use for {{type::cloze::NN}} fields.
     *
     * @param txt The field text with the clozes
     * @param idx The index of the cloze to use
     * @return A string with a comma-separeted list of unique cloze strings with the corret index.
     */

    private String contentForCloze(String txt, int idx) {
        Pattern re = Pattern.compile("\\{\\{c" + idx + "::(.+?)\\}\\}");
        Matcher m = re.matcher(txt);
        Set<String> matches = new LinkedHashSet<>();
        // LinkedHashSet: make entries appear only once, like Anki desktop (see also issue #2208), and keep the order
        // they appear in.
        String groupOne;
        int colonColonIndex = -1;
        while (m.find()) {
            groupOne = m.group(1);
            colonColonIndex = groupOne.indexOf("::");
            if (colonColonIndex > -1) {
                // Cut out the hint.
                groupOne = groupOne.substring(0, colonColonIndex);
            }
            matches.add(groupOne);
        }
        // Now do what the pythonic ", ".join(matches) does in a tricky way
        String prefix = "";
        StringBuilder resultBuilder = new StringBuilder();
        for (String match : matches) {
            resultBuilder.append(prefix);
            resultBuilder.append(match);
            prefix = ", ";
        }
        return resultBuilder.toString();
    }

    private Handler mTimerHandler = new Handler();

    private Runnable removeChosenAnswerText = new Runnable() {
        @Override
        public void run() {
            mChosenAnswer.setText("");
        }
    };

    protected int mWaitAnswerSecond;
    protected int mWaitQuestionSecond;

    protected int getDefaultEase() {
        if (getCol().getSched().answerButtons(mCurrentCard) == 4) {
            return EASE_3;
        } else {
            return EASE_2;
        }
    }


    // ----------------------------------------------------------------------------
    // ANDROID METHODS
    // ----------------------------------------------------------------------------

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Timber.d("onCreate()");
        // Create the extensions as early as possible, so that they can be offered events.
        mExtensions = new ReviewerExtRegistry(getBaseContext());
        restorePreferences();
        super.onCreate(savedInstanceState);
        setContentView(getContentViewAttr(mPrefFullscreenReview));
        View mainView = findViewById(android.R.id.content);
        initNavigationDrawer(mainView);
        // Open collection asynchronously
        startLoadingCollection();
    }

    protected int getContentViewAttr(int fullscreenMode) {
        return R.layout.reviewer;
    }

    @ Override
    public void onConfigurationChanged(Configuration config) {
        // called when screen rotated, etc, since recreating the Webview is too expensive
        super.onConfigurationChanged(config);
        refreshActionBar();
    }


    protected abstract void setTitle();


    // Finish initializing the activity after the collection has been correctly loaded
    @Override
    protected void onCollectionLoaded(Collection col) {
        super.onCollectionLoaded(col);
        mSched = col.getSched();
        mBaseUrl = Utils.getBaseUrl(col.getMedia().dir());

        registerExternalStorageListener();

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

        // Initialize dictionary lookup feature
        Lookup.initialize(this);

        updateScreenCounts();
        supportInvalidateOptionsMenu();
    }


    // Saves deck each time Reviewer activity loses focus
    @Override
    protected void onPause() {
        super.onPause();
        Timber.d("onPause()");

        mTimeoutHandler.removeCallbacks(mShowAnswerTask);
        mTimeoutHandler.removeCallbacks(mShowQuestionTask);
        longClickHandler.removeCallbacks(longClickTestRunnable);
        longClickHandler.removeCallbacks(startLongClickAction);

        pauseTimer();
        mSoundPlayer.stopSounds();

        // Prevent loss of data in Cookies
        CompatHelper.getCompat().flushWebViewCookies();
    }


    @Override
    protected void onResume() {
        super.onResume();
        resumeTimer();
        // Set the context for the Sound manager
        mSoundPlayer.setContext(new WeakReference<Activity>(this));
        // Reset the activity title
        setTitle();
        updateScreenCounts();
        selectNavigationItem(-1);
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        Timber.d("onDestroy()");
        if (mSpeakText) {
            ReadText.releaseTts();
        }
        if (mUnmountReceiver != null) {
            unregisterReceiver(mUnmountReceiver);
        }
        // WebView.destroy() should be called after the end of use
        // http://developer.android.com/reference/android/webkit/WebView.html#destroy()
        mCardFrame.removeAllViews();
        destroyWebView(mCard);
        destroyWebView(mNextCard);
    }


    @Override
    public void onBackPressed() {
        if (isDrawerOpen()) {
            super.onBackPressed();
        } else {
            Timber.i("Back key pressed");
            closeReviewer(RESULT_DEFAULT, false);
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // Hardware buttons for scrolling
        if (keyCode == KeyEvent.KEYCODE_PAGE_UP) {
            mCard.pageUp(false);
            if (mDoubleScrolling) {
                mCard.pageUp(false);
            }
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_PAGE_DOWN) {
            mCard.pageDown(false);
            if (mDoubleScrolling) {
                mCard.pageDown(false);
            }
            return true;
        }
        if (mScrollingButtons && keyCode == KeyEvent.KEYCODE_PICTSYMBOLS) {
            mCard.pageUp(false);
            if (mDoubleScrolling) {
                mCard.pageUp(false);
            }
            return true;
        }
        if (mScrollingButtons && keyCode == KeyEvent.KEYCODE_SWITCH_CHARSET) {
            mCard.pageDown(false);
            if (mDoubleScrolling) {
                mCard.pageDown(false);
            }
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }


    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (mAnswerField != null && !mAnswerField.isFocused()) {
	        if (!sDisplayAnswer) {
	            if (keyCode == KeyEvent.KEYCODE_SPACE || keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) {
	                displayCardAnswer();
	                return true;
	            }
	        }
        }
        return super.onKeyUp(keyCode, event);
    }


    // These three methods use a deprecated API - they should be updated to possibly use its more modern version.
    protected boolean clipboardHasText() {
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
                Timber.e("Clipboard error. Disabling text selection setting.");
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
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == DeckPicker.RESULT_DB_ERROR) {
            closeReviewer(DeckPicker.RESULT_DB_ERROR, false);
        }

        if (resultCode == DeckPicker.RESULT_MEDIA_EJECTED) {
            finishNoStorageAvailable();
        }

        /* Reset the schedule and reload the latest card off the top of the stack if required.
           The card could have been rescheduled, the deck could have changed, or a change of
           note type could have lead to the card being deleted */
        if (data != null && data.hasExtra("reloadRequired")) {
            getCol().getSched().reset();
            DeckTask.launchDeckTask(DeckTask.TASK_TYPE_ANSWER_CARD, mAnswerCardHandler,
                    new DeckTask.TaskData(null, 0));
        }

        if (requestCode == EDIT_CURRENT_CARD) {
            if (resultCode == RESULT_OK) {
                // content of note was changed so update the note and current card
                Timber.i("AbstractFlashcardViewer:: Saving card...");
                DeckTask.launchDeckTask(DeckTask.TASK_TYPE_UPDATE_FACT, mUpdateCardHandler,
                        new DeckTask.TaskData(mCurrentCard, true));
            } else if (resultCode == RESULT_CANCELED && !(data!=null && data.hasExtra("reloadRequired"))) {
                // nothing was changed by the note editor so just redraw the card
                fillFlashcard();
            }
        } else if (requestCode == DECK_OPTIONS && resultCode == RESULT_OK) {
            getCol().getSched().reset();
            DeckTask.launchDeckTask(DeckTask.TASK_TYPE_ANSWER_CARD, mAnswerCardHandler,
                    new DeckTask.TaskData(null, 0));
        }
        if (!mDisableClipboard) {
            clipboardSetText("");
        }
    }


    // ----------------------------------------------------------------------------
    // CUSTOM METHODS
    // ----------------------------------------------------------------------------

    // Get the did of the parent deck (ignoring any subdecks)
    protected long getParentDid() {
        long deckID;
        try {
            deckID = getCol().getDecks().current().getLong("id");
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        return deckID;
    }


    public GestureDetectorCompat getGestureDetector() {
        return gestureDetector;
    }


    /**
     * Show/dismiss dialog when sd card is ejected/remounted (collection is saved by SdCardReceiver)
     */
    private void registerExternalStorageListener() {
        if (mUnmountReceiver == null) {
            mUnmountReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (intent.getAction().equals(SdCardReceiver.MEDIA_EJECT)) {
                        finishWithoutAnimation();
                    }
                }
            };
            IntentFilter iFilter = new IntentFilter();
            iFilter.addAction(SdCardReceiver.MEDIA_EJECT);
            registerReceiver(mUnmountReceiver, iFilter);
        }
    }


    private void pauseTimer() {
        if (mCurrentCard != null) {
            mCurrentCard.stopTimer();
        }
        // We also stop the UI timer so it doesn't trigger the tick listener while paused. Letting
        // it run would trigger the time limit condition (red, stopped timer) in the background.
        if (mCardTimer != null) {
            mCardTimer.stop();
        }
    }


    private void resumeTimer() {
        if (mCurrentCard != null) {
            // Resume the card timer first. It internally accounts for the time gap between
            // suspend and resume.
            mCurrentCard.resumeTimer();
            // Then update and resume the UI timer. Set the base time as if the timer had started
            // timeTaken() seconds ago.
            mCardTimer.setBase(SystemClock.elapsedRealtime() - mCurrentCard.timeTaken());
            // Don't start the timer if we have already reached the time limit or it will tick over
            if ((SystemClock.elapsedRealtime() - mCardTimer.getBase()) < mCurrentCard.timeLimit()) {
                mCardTimer.start();
            }
        }
    }


    protected void undo() {
        if (getCol().undoAvailable()) {
            DeckTask.launchDeckTask(DeckTask.TASK_TYPE_UNDO, mAnswerCardHandler);
        }
    }


    private void finishNoStorageAvailable() {
        AbstractFlashcardViewer.this.setResult(DeckPicker.RESULT_MEDIA_EJECTED);
        finishWithoutAnimation();
    }


    protected boolean editCard() {
        Intent editCard = new Intent(AbstractFlashcardViewer.this, NoteEditor.class);
        editCard.putExtra(NoteEditor.EXTRA_CALLER, NoteEditor.CALLER_REVIEWER);
        sEditorCard = mCurrentCard;
        startActivityForResultWithAnimation(editCard, EDIT_CURRENT_CARD, ActivityTransitionAnimation.LEFT);
        return true;
    }


    protected void generateQuestionSoundList() {
        mSoundPlayer.addSounds(mBaseUrl, mCurrentCard.qSimple(), Sound.SOUNDS_QUESTION);
    }


    protected void lookUpOrSelectText() {
        if (clipboardHasText()) {
            Timber.d("Clipboard has text = " + clipboardHasText());
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
        Resources res = getResources();
        new MaterialDialog.Builder(this)
                .title(res.getString(R.string.delete_card_title))
                .iconAttr(R.attr.dialogErrorIcon)
                .content(String.format(res.getString(R.string.delete_note_message),
                        Utils.stripHTML(mCurrentCard.q(true))))
                .positiveText(res.getString(R.string.dialog_positive_delete))
                .negativeText(res.getString(R.string.dialog_cancel))
                .callback(new MaterialDialog.ButtonCallback() {
                    @Override
                    public void onPositive(MaterialDialog dialog) {
                        Timber.i("AbstractFlashcardViewer:: OK button pressed to delete note %d", mCurrentCard.getNid());
                        dismiss(Collection.DismissType.DELETE_NOTE);
                    }
                })
                .build().show();
    }


    private int getRecommendedEase(boolean easy) {
        try {
            switch (mSched.answerButtons(mCurrentCard)) {
                case 2:
                    return EASE_2;
                case 3:
                    return easy ? EASE_3 : EASE_2;
                case 4:
                    return easy ? EASE_4 : EASE_3;
                default:
                    return 0;
            }
        } catch (RuntimeException e) {
            AnkiDroidApp.sendExceptionReport(e, "AbstractReviewer-getRecommendedEase");
            closeReviewer(DeckPicker.RESULT_DB_ERROR, true);
            return 0;
        }
    }


    protected void answerCard(int ease) {
        if (mInAnswer) {
            return;
        }
        mIsSelecting = false;
        hideLookupButton();
        int buttonNumber = getCol().getSched().answerButtons(mCurrentCard);
        // Detect invalid ease for current card (e.g. by using keyboard shortcut or gesture).
        if (buttonNumber < ease) {
            return;
        }
        // Set the dots appearing below the toolbar
        switch (ease) {
            case EASE_1:
                mChosenAnswer.setText("\u2022");
                mChosenAnswer.setTextColor(ContextCompat.getColor(this, R.color.material_red_500));
                break;
            case EASE_2:
                mChosenAnswer.setText("\u2022\u2022");
                mChosenAnswer.setTextColor(ContextCompat.getColor(this, buttonNumber == 4 ?
                        R.color.material_blue_grey_600:
                        R.color.material_green_500));
                break;
            case EASE_3:
                mChosenAnswer.setText("\u2022\u2022\u2022");
                mChosenAnswer.setTextColor(ContextCompat.getColor(this, buttonNumber == 4 ?
                        R.color.material_green_500 :
                        R.color.material_light_blue_500));
                break;
            case EASE_4:
                mChosenAnswer.setText("\u2022\u2022\u2022\u2022");
                mChosenAnswer.setTextColor(ContextCompat.getColor(this, R.color.material_light_blue_500));
                break;
        }

        // remove chosen answer hint after a while
        mTimerHandler.removeCallbacks(removeChosenAnswerText);
        mTimerHandler.postDelayed(removeChosenAnswerText, mShowChosenAnswerLength);
        mSoundPlayer.stopSounds();
        mCurrentEase = ease;

        DeckTask.launchDeckTask(DeckTask.TASK_TYPE_ANSWER_CARD, mAnswerCardHandler,
                new DeckTask.TaskData(mCurrentCard, mCurrentEase));
    }


    // Set the content view to the one provided and initialize accessors.
    protected void initLayout() {
        mMainLayout = findViewById(R.id.main_layout);
        mCardContainer = (FrameLayout) findViewById(R.id.flashcard_frame);

        mTopBarLayout = (RelativeLayout) findViewById(R.id.top_bar);
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

        // Initialize swipe
        gestureDetector = new GestureDetectorCompat(this, new MyGestureDetector());

        mEase1 = (TextView) findViewById(R.id.ease1);
        mEase1.setTypeface(TypefaceHelper.get(this, "Roboto-Medium"));
        mEase1Layout = (LinearLayout) findViewById(R.id.flashcard_layout_ease1);
        mEase1Layout.setOnClickListener(mSelectEaseHandler);

        mEase2 = (TextView) findViewById(R.id.ease2);
        mEase2.setTypeface(TypefaceHelper.get(this, "Roboto-Medium"));
        mEase2Layout = (LinearLayout) findViewById(R.id.flashcard_layout_ease2);
        mEase2Layout.setOnClickListener(mSelectEaseHandler);

        mEase3 = (TextView) findViewById(R.id.ease3);
        mEase3.setTypeface(TypefaceHelper.get(this, "Roboto-Medium"));
        mEase3Layout = (LinearLayout) findViewById(R.id.flashcard_layout_ease3);
        mEase3Layout.setOnClickListener(mSelectEaseHandler);

        mEase4 = (TextView) findViewById(R.id.ease4);
        mEase4.setTypeface(TypefaceHelper.get(this, "Roboto-Medium"));
        mEase4Layout = (LinearLayout) findViewById(R.id.flashcard_layout_ease4);
        mEase4Layout.setOnClickListener(mSelectEaseHandler);

        mNext1 = (TextView) findViewById(R.id.nextTime1);
        mNext2 = (TextView) findViewById(R.id.nextTime2);
        mNext3 = (TextView) findViewById(R.id.nextTime3);
        mNext4 = (TextView) findViewById(R.id.nextTime4);
        mNext1.setTypeface(TypefaceHelper.get(this, "Roboto-Regular"));
        mNext2.setTypeface(TypefaceHelper.get(this, "Roboto-Regular"));
        mNext3.setTypeface(TypefaceHelper.get(this, "Roboto-Regular"));
        mNext4.setTypeface(TypefaceHelper.get(this, "Roboto-Regular"));

        if (!mShowNextReviewTime) {
            mNext1.setVisibility(View.GONE);
            mNext2.setVisibility(View.GONE);
            mNext3.setVisibility(View.GONE);
            mNext4.setVisibility(View.GONE);
        }

        mFlipCard = (Button) findViewById(R.id.flip_card);
        mFlipCard.setTypeface(TypefaceHelper.get(this, "Roboto-Medium"));
        mFlipCardLayout = (LinearLayout) findViewById(R.id.flashcard_layout_flip);
        mFlipCardLayout.setOnClickListener(mFlipCardListener);

        if (!mButtonHeightSet && mRelativeButtonSize != 100) {
            ViewGroup.LayoutParams params = mFlipCardLayout.getLayoutParams();
            params.height = params.height * mRelativeButtonSize / 100;
            params = mEase1Layout.getLayoutParams();
            params.height = params.height * mRelativeButtonSize / 100;
            params = mEase2Layout.getLayoutParams();
            params.height = params.height * mRelativeButtonSize / 100;
            params = mEase3Layout.getLayoutParams();
            params.height = params.height * mRelativeButtonSize / 100;
            params = mEase4Layout.getLayoutParams();
            params.height = params.height * mRelativeButtonSize / 100;
            mButtonHeightSet = true;
        }

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

        mAnswerField = (EditText) findViewById(R.id.answer_field);

        mLookUpIcon = findViewById(R.id.lookup_button);
        mLookUpIcon.setVisibility(View.GONE);
        mLookUpIcon.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View arg0) {
                Timber.i("AbstractFlashcardViewer:: Lookup button pressed");
                if (clipboardHasText()) {
                    lookUp();
                }
            }

        });
        initControls();

        // Position answer buttons
        String answerButtonsPosition = AnkiDroidApp.getSharedPrefs(this).getString(
                getString(R.string.answer_buttons_position_preference),
                "bottom"
        );
        LinearLayout answerArea = (LinearLayout) findViewById(R.id.bottom_area_layout);
        RelativeLayout.LayoutParams answerAreaParams = (RelativeLayout.LayoutParams) answerArea.getLayoutParams();
        RelativeLayout.LayoutParams cardContainerParams = (RelativeLayout.LayoutParams) mCardContainer.getLayoutParams();

        switch (answerButtonsPosition) {
            case "top":
                cardContainerParams.addRule(RelativeLayout.BELOW, R.id.bottom_area_layout);
                answerAreaParams.addRule(RelativeLayout.BELOW, R.id.top_bar);
                answerArea.removeView(mAnswerField);
                answerArea.addView(mAnswerField, 1);
                break;
            case "bottom":
                cardContainerParams.addRule(RelativeLayout.ABOVE, R.id.bottom_area_layout);
                cardContainerParams.addRule(RelativeLayout.BELOW, R.id.top_bar);
                answerAreaParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
                break;
        }
        answerArea.setLayoutParams(answerAreaParams);
        mCardContainer.setLayoutParams(cardContainerParams);
    }


    @SuppressLint({"NewApi", "SetJavaScriptEnabled"})
    // because of setDisplayZoomControls.
    private WebView createWebView() {
        WebView webView = new MyWebView(this);
        webView.setWillNotCacheDrawing(true);
        webView.setScrollBarStyle(View.SCROLLBARS_OUTSIDE_OVERLAY);
        if (CompatHelper.isHoneycomb()) {
            // Disable the on-screen zoom buttons for API > 11
            webView.getSettings().setDisplayZoomControls(false);
        }
        webView.getSettings().setBuiltInZoomControls(true);
        webView.getSettings().setSupportZoom(true);
        // Start at the most zoomed-out level
        webView.getSettings().setLoadWithOverviewMode(true);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.setWebChromeClient(new AnkiDroidWebChromeClient());
        // Problems with focus and input tags is the reason we keep the old type answer mechanism for old Androids.
        webView.setFocusableInTouchMode(mUseInputTag);
        webView.setScrollbarFadingEnabled(true);
        Timber.d("Focusable = %s, Focusable in touch mode = %s", webView.isFocusable(), webView.isFocusableInTouchMode());

        webView.setWebViewClient(new WebViewClient() {
            // Filter any links using the custom "playsound" protocol defined in Sound.java.
            // We play sounds through these links when a user taps the sound icon.
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (url.startsWith("playsound:")) {
                    // Send a message that will be handled on the UI thread.
                    Message msg = Message.obtain();
                    String soundPath = url.replaceFirst("playsound:", "");
                    msg.obj = soundPath;
                    mHandler.sendMessage(msg);
                    return true;
                }
                if (url.startsWith("file") || url.startsWith("data:")) {
                    return false; // Let the webview load files, i.e. local images.
                }
                if (url.startsWith("typeblurtext:")) {
                    // Store the text the javascript has send us…
                    mTypeInput = URLDecoder.decode(url.replaceFirst("typeblurtext:", ""));
                    // … and show the “SHOW ANSWER” button again.
                    mFlipCardLayout.setVisibility(View.VISIBLE);
                    return true;
                }
                if (url.startsWith("typeentertext:")) {
                    // Store the text the javascript has send us…
                    mTypeInput = URLDecoder.decode(url.replaceFirst("typeentertext:", ""));
                    // … and show the answer.
                    mFlipCardLayout.performClick();
                    return true;
                }
                if (url.equals("signal:typefocus")) {
                    // Hide the “SHOW ANSWER” button when the input has focus. The soft keyboard takes up enough space
                    // by itself.
                    mFlipCardLayout.setVisibility(View.GONE);
                    return true;
                }
                Intent intent = null;
                try {
                    if (url.startsWith("intent:")) {
                        intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME);
                    } else if (url.startsWith("android-app:")) {
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1) {
                            intent = Intent.parseUri(url, 0);
                            intent.setData(null);
                            intent.setPackage(Uri.parse(url).getHost());
                        } else {
                            intent = Intent.parseUri(url, Intent.URI_ANDROID_APP_SCHEME);
                        }
                    }
                    if (intent != null) {
                        if (getPackageManager().resolveActivity(intent, 0) == null) {
                            String packageName = intent.getPackage();
                            if (packageName == null) {
                                Timber.d("Not using resolved intent uri because not available: %s", intent);
                                intent = null;
                            } else {
                                Timber.d("Resolving intent uri to market uri because not available: %s", intent);
                                intent = new Intent(Intent.ACTION_VIEW,
                                        Uri.parse("market://details?id=" + packageName));
                                if (getPackageManager().resolveActivity(intent, 0) == null) {
                                    intent = null;
                                }
                            }
                        } else {
                            // https://developer.chrome.com/multidevice/android/intents says that we should remove this
                            intent.addCategory(Intent.CATEGORY_BROWSABLE);
                        }
                    }
                } catch (Throwable t) {
                    Timber.w("Unable to parse intent uri: %s because: %s", url, t.getMessage());
                }
                if (intent == null) {
                    Timber.d("Opening external link \"%s\" with an Intent", url);
                    intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                } else {
                    Timber.d("Opening resolved external link \"%s\" with an Intent: %s", url, intent);
                }
                try {
                    startActivityWithoutAnimation(intent);
                } catch (ActivityNotFoundException e) {
                    e.printStackTrace(); // Don't crash if the intent is not handled
                }
                return true;
            }


            // Run any post-load events in javascript that rely on the window being completely loaded.
            @Override
            public void onPageFinished(WebView view, String url) {
                Timber.d("onPageFinished triggered");
                view.loadUrl("javascript:onPageFinished();");
            }
        });
        // Set transparent color to prevent flashing white when night mode enabled
        webView.setBackgroundColor(Color.argb(1, 0, 0, 0));
        return webView;
    }

    private void destroyWebView(WebView webView) {
        if (webView != null) {
            webView.stopLoading();
            webView.setWebChromeClient(null);
            webView.setWebViewClient(null);
            webView.destroy();
        }
    }


    protected void showEaseButtons() {
        Resources res = getResources();

        // hide flipcard button
        mFlipCardLayout.setVisibility(View.GONE);

        int buttonCount;
        try {
            buttonCount = mSched.answerButtons(mCurrentCard);
        } catch (RuntimeException e) {
            AnkiDroidApp.sendExceptionReport(e, "AbstractReviewer-showEaseButtons");
            closeReviewer(DeckPicker.RESULT_DB_ERROR, true);
            return;
        }

        // Set correct label and background resource for each button
        // Note that it's necessary to set the resource dynamically as the ease2 / ease3 buttons
        // (which libanki expects ease to be 2 and 3) can either be hard, good, or easy - depending on num buttons shown
        final int[] background = Themes.getResFromAttr(this, new int [] {
                R.attr.againButtonRef,
                R.attr.hardButtonRef,
                R.attr.goodButtonRef,
                R.attr.easyButtonRef});
        final int[] textColor = Themes.getColorFromAttr(this, new int [] {
                R.attr.againButtonTextColor,
                R.attr.hardButtonTextColor,
                R.attr.goodButtonTextColor,
                R.attr.easyButtonTextColor});
        mEase1Layout.setVisibility(View.VISIBLE);
        mEase1Layout.setBackgroundResource(background[0]);
        mEase4Layout.setBackgroundResource(background[3]);
        switch (buttonCount) {
            case 2:
                // Ease 2 is "good"
                mEase2Layout.setVisibility(View.VISIBLE);
                mEase2Layout.setBackgroundResource(background[2]);
                mEase2.setText(R.string.ease_button_good);
                mEase2.setTextColor(textColor[2]);
                mNext2.setTextColor(textColor[2]);
                mEase2Layout.requestFocus();
                break;
            case 3:
                // Ease 2 is good
                mEase2Layout.setVisibility(View.VISIBLE);
                mEase2Layout.setBackgroundResource(background[2]);
                mEase2.setText(R.string.ease_button_good);
                mEase2.setTextColor(textColor[2]);
                mNext2.setTextColor(textColor[2]);
                // Ease 3 is easy
                mEase3Layout.setVisibility(View.VISIBLE);
                mEase3Layout.setBackgroundResource(background[3]);
                mEase3.setText(R.string.ease_button_easy);
                mEase3.setTextColor(textColor[3]);
                mNext3.setTextColor(textColor[3]);
                mEase2Layout.requestFocus();
                break;
            default:
                mEase2Layout.setVisibility(View.VISIBLE);
                // Ease 2 is "hard"
                mEase2Layout.setVisibility(View.VISIBLE);
                mEase2Layout.setBackgroundResource(background[1]);
                mEase2.setText(R.string.ease_button_hard);
                mEase2.setTextColor(textColor[1]);
                mNext2.setTextColor(textColor[1]);
                mEase2Layout.requestFocus();
                // Ease 3 is good
                mEase3Layout.setVisibility(View.VISIBLE);
                mEase3Layout.setBackgroundResource(background[2]);
                mEase3.setText(R.string.ease_button_good);
                mEase3.setTextColor(textColor[2]);
                mNext3.setTextColor(textColor[2]);
                mEase4Layout.setVisibility(View.VISIBLE);
                mEase3Layout.requestFocus();
        }

        // Show next review time
        if (mShowNextReviewTime) {
            mNext1.setText(mSched.nextIvlStr(this, mCurrentCard, 1));
            mNext2.setText(mSched.nextIvlStr(this, mCurrentCard, 2));
            if (buttonCount > 2) {
                mNext3.setText(mSched.nextIvlStr(this, mCurrentCard, 3));
            }
            if (buttonCount > 3) {
                mNext4.setText(mSched.nextIvlStr(this, mCurrentCard, 4));
            }
        }
    }


    protected void hideEaseButtons() {
        mEase1Layout.setVisibility(View.GONE);
        mEase2Layout.setVisibility(View.GONE);
        mEase3Layout.setVisibility(View.GONE);
        mEase4Layout.setVisibility(View.GONE);
        mFlipCardLayout.setVisibility(View.VISIBLE);
        if (typeAnswer()) {
            mAnswerField.requestFocus();
        } else {
            mFlipCardLayout.requestFocus();
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


    protected void initControls() {
        mCardFrame.setVisibility(View.VISIBLE);
        if (mShowRemainingCardCount) {
            mTextBarNew.setVisibility(View.VISIBLE);
            mTextBarLearn.setVisibility(View.VISIBLE);
            mTextBarReview.setVisibility(View.VISIBLE);
        }
        mChosenAnswer.setVisibility(View.VISIBLE);
        mFlipCardLayout.setVisibility(View.VISIBLE);

        mAnswerField.setVisibility(typeAnswer() ? View.VISIBLE : View.GONE);
        mAnswerField.setOnEditorActionListener(new EditText.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    displayCardAnswer();
                    return true;
                }
                return false;
            }
        });
        mAnswerField.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (event.getAction() == KeyEvent.ACTION_UP &&
                        (keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER)) {
                    displayCardAnswer();
                    return true;
                }
                return false;
            }
        });
    }


    protected SharedPreferences restorePreferences() {
        SharedPreferences preferences = AnkiDroidApp.getSharedPrefs(getBaseContext());
        mPrefHideDueCount = preferences.getBoolean("hideDueCount", false);
        mPrefShowETA = preferences.getBoolean("showETA", true);
        mUseInputTag = preferences.getBoolean("useInputTag", false) && (CompatHelper.getSdkVersion() >= 15);
        mShowTypeAnswerField = (!preferences.getBoolean("writeAnswersDisable", false)) && !mUseInputTag;
        // On newer Androids, ignore this setting, which sholud be hidden in the prefs anyway.
        mDisableClipboard = preferences.getString("dictionary", "0").equals("0");
        mLongClickWorkaround = preferences.getBoolean("textSelectionLongclickWorkaround", false);
        // mDeckFilename = preferences.getString("deckFilename", "");
        mNightMode = preferences.getBoolean("invertedColors", false);
        mPrefFullscreenReview = Integer.parseInt(preferences.getString("fullscreenMode", "0"));
        mCardZoom = preferences.getInt("cardZoom", 100);
        mImageZoom = preferences.getInt("imageZoom", 100);
        mRelativeButtonSize = preferences.getInt("answerButtonSize", 100);
        mInputWorkaround = preferences.getBoolean("inputWorkaround", false);
        mSpeakText = preferences.getBoolean("tts", false);
        mPrefSafeDisplay = preferences.getBoolean("safeDisplay", false);
        mPrefUseTimer = preferences.getBoolean("timeoutAnswer", false);
        mWaitAnswerSecond = preferences.getInt("timeoutAnswerSeconds", 20);
        mWaitQuestionSecond = preferences.getInt("timeoutQuestionSeconds", 60);
        mScrollingButtons = preferences.getBoolean("scrolling_buttons", false);
        mDoubleScrolling = preferences.getBoolean("double_scrolling", false);
        mPrefCenterVertically = preferences.getBoolean("centerVertically", false);

        mGesturesEnabled = AnkiDroidApp.initiateGestures(preferences);
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

        mCustomButtons.put(R.id.action_undo, Integer.parseInt(preferences.getString("customButtonUndo", "2")));
        mCustomButtons.put(R.id.action_mark_card, Integer.parseInt(preferences.getString("customButtonMarkCard", "2")));
        mCustomButtons.put(R.id.action_edit, Integer.parseInt(preferences.getString("customButtonEditCard", "1")));
        mCustomButtons.put(R.id.action_add_note_reviewer, Integer.parseInt(preferences.getString("customButtonAddCard", "3")));
        mCustomButtons.put(R.id.action_replay, Integer.parseInt(preferences.getString("customButtonReplay", "1")));
        mCustomButtons.put(R.id.action_clear_whiteboard, Integer.parseInt(preferences.getString("customButtonClearWhiteboard", "1")));
        mCustomButtons.put(R.id.action_hide_whiteboard, Integer.parseInt(preferences.getString("customButtonShowHideWhiteboard", "2")));
        mCustomButtons.put(R.id.action_select_tts, Integer.parseInt(preferences.getString("customButtonSelectTts", "0")));
        mCustomButtons.put(R.id.action_open_deck_options, Integer.parseInt(preferences.getString("customButtonDeckOptions", "0")));
        mCustomButtons.put(R.id.action_bury, Integer.parseInt(preferences.getString("customButtonBury", "0")));
        mCustomButtons.put(R.id.action_suspend, Integer.parseInt(preferences.getString("customButtonSuspend", "0")));
        mCustomButtons.put(R.id.action_delete, Integer.parseInt(preferences.getString("customButtonDelete", "0")));

        if (mLongClickWorkaround) {
            mGestureLongclick = GESTURE_LOOKUP;
        }

        if (preferences.getBoolean("keepScreenOn", false)) {
            this.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }

        // These are preferences we pull out of the collection instead of SharedPreferences
        try {
            mShowNextReviewTime = getCol().getConf().getBoolean("estTimes");
            mShowRemainingCardCount = getCol().getConf().getBoolean("dueCounts");
        } catch (JSONException e) {
            throw new RuntimeException();
        }

        return preferences;
    }


    private void setInterface() {
        if (mCurrentCard == null) {
            return;
        }
        if (mCard == null) {
            mCard = createWebView();
            mCardFrame.addView(mCard);
            if (!mUseQuickUpdate) {
                mNextCard = createWebView();
                mNextCard.setVisibility(View.GONE);
                mCardFrame.addView(mNextCard, 0);
            }
        }
        if (mCard.getVisibility() != View.VISIBLE) {
            mCard.setVisibility(View.VISIBLE);
        }
    }


    private void updateForNewCard() {
        updateScreenCounts();

        // Clean answer field
        if (typeAnswer()) {
            mAnswerField.setText("");
        }

        if (mPrefWhiteboard && mWhiteboard != null) {
            mWhiteboard.clear();
        }
    }


    protected void updateScreenCounts() {
        ActionBar actionBar = getSupportActionBar();
        if (mCurrentCard == null) return;
        int[] counts = mSched.counts(mCurrentCard);

        if (actionBar != null) {
            try {
                String[] title = getCol().getDecks().get(mCurrentCard.getDid()).getString("name").split("::");
                actionBar.setTitle(title[title.length - 1]);
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
            if (mPrefShowETA) {
                int eta = mSched.eta(counts, false);
                actionBar.setSubtitle(getResources().getQuantityString(R.plurals.reviewer_window_title, eta, eta));
            }
        }

        SpannableString newCount = new SpannableString(String.valueOf(counts[0]));
        SpannableString lrnCount = new SpannableString(String.valueOf(counts[1]));
        SpannableString revCount = new SpannableString(String.valueOf(counts[2]));
        if (mPrefHideDueCount) {
            revCount = new SpannableString("???");
        }

        switch (mSched.countIdx(mCurrentCard)) {
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
        @Override
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
        final TypedValue typedValue = new TypedValue();
        mShowTimer = mCurrentCard.showTimer();
        if (mShowTimer && mCardTimer.getVisibility() == View.INVISIBLE) {
            mCardTimer.setVisibility(View.VISIBLE);
        } else if (!mShowTimer && mCardTimer.getVisibility() != View.INVISIBLE) {
            mCardTimer.setVisibility(View.INVISIBLE);
        }
        // Set normal timer color
        getTheme().resolveAttribute(android.R.attr.textColor, typedValue, true);
        mCardTimer.setTextColor(typedValue.data);

        mCardTimer.setBase(SystemClock.elapsedRealtime());
        mCardTimer.start();

        // Stop and highlight the timer if it reaches the time limit.
        getTheme().resolveAttribute(R.attr.maxTimerColor, typedValue, true);
        final int limit = mCurrentCard.timeLimit();
        mCardTimer.setOnChronometerTickListener(new Chronometer.OnChronometerTickListener() {
            @Override
            public void onChronometerTick(Chronometer chronometer) {
                long elapsed = SystemClock.elapsedRealtime() - chronometer.getBase();
                if (elapsed >= limit) {
                    chronometer.setTextColor(typedValue.data);
                    chronometer.stop();
                }
            }
        });
    }


    protected void displayCardQuestion() {
        Timber.d("displayCardQuestion()");
        sDisplayAnswer = false;

        setInterface();

        String question;
        String displayString = "";
        if (mCurrentCard.isEmpty()) {
            displayString = getResources().getString(R.string.empty_card_warning);
        } else {
            question = mCurrentCard.q();
            question = getCol().getMedia().escapeImages(question);
            question = typeAnsQuestionFilter(question);

            Timber.d("question: '%s'", question);
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

        // If the user wants to show the answer automatically
        if (mPrefUseTimer) {
            long delay = mWaitAnswerSecond * 1000 + mUseTimerDynamicMS;
            if (delay > 0) {
                mTimeoutHandler.removeCallbacks(mShowAnswerTask);
                mTimeoutHandler.postDelayed(mShowAnswerTask, delay);
            }
        }

        Timber.i("AbstractFlashcardViewer:: Question successfully shown for card id %d", mCurrentCard.getId());
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
        return Utils.nfcNormalized(answerText);
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
        return Utils.nfcNormalized(answer.trim());
    }


    protected void displayCardAnswer() {
        Timber.d("displayCardAnswer()");

        // prevent answering (by e.g. gestures) before card is loaded
        if (mCurrentCard == null) {
            return;
        }

        // Explicitly hide the soft keyboard. It *should* be hiding itself automatically,
        // but sometimes failed to do so (e.g. if an OnKeyListener is attached).
        if (typeAnswer()) {
            InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            inputMethodManager.hideSoftInputFromWindow(mAnswerField.getWindowToken(), 0);
        }

        sDisplayAnswer = true;

        String answer = mCurrentCard.a();

        mSoundPlayer.stopSounds();
        answer = getCol().getMedia().escapeImages(answer);

        mAnswerField.setVisibility(View.GONE);
        // Clean up the user answer and the correct answer
        String userAnswer;
        if (mUseInputTag) {
            userAnswer = cleanTypedAnswer(mTypeInput);
        } else {
            userAnswer = cleanTypedAnswer(mAnswerField.getText().toString());
        }
        String correctAnswer = cleanCorrectAnswer(mTypeCorrect);
        Timber.d("correct answer = %s", correctAnswer);
        Timber.d("user answer = %s", userAnswer);

        answer = typeAnsAnswerFilter(answer, userAnswer, correctAnswer);

        mIsSelecting = false;
        updateCard(enrichWithQADiv(answer, true));
        showEaseButtons();
        // If the user wants to show the next question automatically
        if (mPrefUseTimer) {
            long delay = mWaitQuestionSecond * 1000 + mUseTimerDynamicMS;
            if (delay > 0) {
                mTimeoutHandler.removeCallbacks(mShowQuestionTask);
                mTimeoutHandler.postDelayed(mShowQuestionTask, delay);
            }
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

    private void addAnswerSounds(String answer) {
        // don't add answer sounds multiple times, such as when reshowing card after exiting editor
        // additionally, this condition reduces computation time
        if (!mAnswerSoundsAdded) {
            String answerSoundSource = removeFrontSideAudio(answer);
            mSoundPlayer.addSounds(mBaseUrl, answerSoundSource, Sound.SOUNDS_ANSWER);
            mAnswerSoundsAdded = true;
        }
    }


    private void updateCard(String content) {
        Timber.d("updateCard()");

        mUseTimerDynamicMS = 0;

        // Add CSS for font color and font size
        if (mCurrentCard == null) {
            mCard.getSettings().setDefaultFontSize(calculateDynamicFontSize(content));
        }

        if (sDisplayAnswer) {
            addAnswerSounds(content);
        } else {
            // reset sounds each time first side of card is displayed, which may happen repeatedly without ever
            // leaving the card (such as when edited)
            mSoundPlayer.resetSounds();
            mAnswerSoundsAdded = false;
            mSoundPlayer.addSounds(mBaseUrl, content, Sound.SOUNDS_QUESTION);
            if (mPrefUseTimer && !mAnswerSoundsAdded && getConfigForCurrentCard().optBoolean("autoplay", false)) {
                addAnswerSounds(mCurrentCard.a());
            }
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

        Timber.d("content card = \n %s", content);
        StringBuilder style = new StringBuilder();
        mExtensions.updateCssStyle(style);

        // Zoom cards
        if (mCardZoom != 100) {
            style.append(String.format("body { zoom: %s }\n", mCardZoom / 100.0));
        }

        // Zoom images
        if (mImageZoom != 100) {
            style.append(String.format("img { zoom: %s }\n", mImageZoom / 100.0));
        }

        Timber.d("::style::", style);

        if (mNightMode) {
            // Enable the night-mode class
            cardClass += " night_mode";
            // If card styling doesn't contain any mention of the night_mode class then do color inversion as fallback
            // TODO: find more robust solution that won't match unrelated classes like "night_mode_old"
            if (!mCurrentCard.css().contains(".night_mode")) {
                content = HtmlColors.invertColors(content);
            }
        }

        content = SmpToHtmlEntity(content);
        mCardContent = new SpannedString(mCardTemplate.replace("::content::", content)
                .replace("::style::", style.toString()).replace("::class::", cardClass));
        Timber.d("base url = %s", mBaseUrl);

        if (SAVE_CARD_CONTENT) {
            try {
                FileOutputStream f = new FileOutputStream(new File(CollectionHelper.getCurrentAnkiDroidDirectory(this),
                        "card.html"));
                try {
                    f.write(mCardContent.toString().getBytes());
                } finally {
                    f.close();
                }
            } catch (IOException e) {
                Timber.d(e, "failed to save card");
            }
        }
        fillFlashcard();

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
            m.appendReplacement(sb, Matcher.quoteReplacement(a));
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

        if (getConfigForCurrentCard().optBoolean("autoplay", false) || doAudioReplay) {
            // Use TTS if TTS preference enabled and no other sound source
            boolean useTTS = mSpeakText &&
                    !(sDisplayAnswer && mSoundPlayer.hasAnswer()) && !(!sDisplayAnswer && mSoundPlayer.hasQuestion());
            // We need to play the sounds from the proper side of the card
            if (!useTTS) { // Text to speech not in effect here
                if (doAudioReplay && replayQuestion && sDisplayAnswer) {
                    // only when all of the above are true will question be played with answer, to match desktop
                    mSoundPlayer.playSounds(Sound.SOUNDS_QUESTION_AND_ANSWER);
                } else if (sDisplayAnswer) {
                    mSoundPlayer.playSounds(Sound.SOUNDS_ANSWER);
                    if (mPrefUseTimer) {
                        mUseTimerDynamicMS = mSoundPlayer.getSoundsLength(Sound.SOUNDS_ANSWER);
                    }
                } else { // question is displayed
                    mSoundPlayer.playSounds(Sound.SOUNDS_QUESTION);
                    // If the user wants to show the answer automatically
                    if (mPrefUseTimer) {
                        mUseTimerDynamicMS = mSoundPlayer.getSoundsLength(Sound.SOUNDS_QUESTION_AND_ANSWER);
                    }
                }
            } else { // Text to speech is in effect here
                // If the question is displayed or if the question should be replayed, read the question
                if (mTtsInitialized) {
                    if (!sDisplayAnswer || doAudioReplay && replayQuestion) {
                        readCardText(mCurrentCard, Sound.SOUNDS_QUESTION);
                    }
                    if (sDisplayAnswer) {
                        readCardText(mCurrentCard, Sound.SOUNDS_ANSWER);
                    }
                } else {
                    mReplayOnTtsInit = true;
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
            ReadText.textToSpeech(Utils.stripHTML(card.getPureAnswer()), getDeckIdForCard(card),
                    card.getOrd(), Sound.SOUNDS_ANSWER);
        }
    }


    /**
     * Shows the dialogue for selecting TTS for the current card and cardside.
     */
    protected void showSelectTtsDialogue() {
        if (mTtsInitialized) {
            if (!sDisplayAnswer) {
                ReadText.selectTts(Utils.stripHTML(mCurrentCard.q(true)), getDeckIdForCard(mCurrentCard), mCurrentCard.getOrd(),
                        Sound.SOUNDS_QUESTION);
            }
            else {
                ReadText.selectTts(Utils.stripHTML(mCurrentCard.getPureAnswer()), getDeckIdForCard(mCurrentCard),
                        mCurrentCard.getOrd(), Sound.SOUNDS_ANSWER);
            }
        }
    }


    /**
     * Returns the configuration for the current {@link Card}.
     *
     * @return The configuration for the current {@link Card}
     */
    private JSONObject getConfigForCurrentCard() {
        return getCol().getDecks().confForDid(getDeckIdForCard(mCurrentCard));
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
        Timber.d("fillFlashcard()");
        Timber.d("base url = %s", mBaseUrl);
        if (!mUseQuickUpdate && mCard != null && mNextCard != null) {
            CompatHelper.getCompat().setHTML5MediaAutoPlay(mNextCard.getSettings(), getConfigForCurrentCard().optBoolean("autoplay"));
            mNextCard.loadDataWithBaseURL(mBaseUrl + "__viewer__.html", mCardContent.toString(), "text/html", "utf-8", null);
            mNextCard.setVisibility(View.VISIBLE);
            mCardFrame.removeView(mCard);
            destroyWebView(mCard);
            mCard = mNextCard;
            mNextCard = createWebView();
            mNextCard.setVisibility(View.GONE);
            mCardFrame.addView(mNextCard, 0);
        } else if (mCard != null) {
            CompatHelper.getCompat().setHTML5MediaAutoPlay(mCard.getSettings(), getConfigForCurrentCard().optBoolean("autoplay"));
            mCard.loadDataWithBaseURL(mBaseUrl + "__viewer__.html", mCardContent.toString(), "text/html", "utf-8", null);
        }
        if (mShowTimer && mCardTimer.getVisibility() == View.INVISIBLE) {
            switchTopBarVisibility(View.VISIBLE);
        }
        if (!sDisplayAnswer) {
            updateForNewCard();
        }
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
     * @return true if the AnkiDroid preference for writing answer is true and if the Anki Deck CardLayout specifies a
     *         field to query
     */
    private boolean typeAnswer() {
        return mShowTypeAnswerField && null != mTypeCorrect;
    }


    /**
     * Calculates a dynamic font size depending on the length of the contents taking into account that the input string
     * contains html-tags, which will not be displayed and therefore should not be taken into account.
     *
     * @param htmlContent
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
            case EASE_1:
                mEase1Layout.setClickable(true);
                mEase2Layout.setEnabled(true);
                mEase3Layout.setEnabled(true);
                mEase4Layout.setEnabled(true);
                break;

            case EASE_2:
                mEase1Layout.setEnabled(true);
                mEase2Layout.setClickable(true);
                mEase3Layout.setEnabled(true);
                mEase4Layout.setEnabled(true);
                break;

            case EASE_3:
                mEase1Layout.setEnabled(true);
                mEase2Layout.setEnabled(true);
                mEase3Layout.setClickable(true);
                mEase4Layout.setEnabled(true);
                break;

            case EASE_4:
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

        if (mPrefWhiteboard && mWhiteboard != null) {
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
            case EASE_1:
                mEase1Layout.setClickable(false);
                mEase2Layout.setEnabled(false);
                mEase3Layout.setEnabled(false);
                mEase4Layout.setEnabled(false);
                break;

            case EASE_2:
                mEase1Layout.setEnabled(false);
                mEase2Layout.setClickable(false);
                mEase3Layout.setEnabled(false);
                mEase4Layout.setEnabled(false);
                break;

            case EASE_3:
                mEase1Layout.setEnabled(false);
                mEase2Layout.setEnabled(false);
                mEase3Layout.setClickable(false);
                mEase4Layout.setEnabled(false);
                break;

            case EASE_4:
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

        if (mPrefWhiteboard && mWhiteboard != null) {
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
            shiftPressEvent.dispatch(mCard);
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
                    answerCard(EASE_1);
                } else {
                    displayCardAnswer();
                }
                break;
            case GESTURE_ANSWER_EASE2:
                if (sDisplayAnswer) {
                    answerCard(EASE_2);
                } else {
                    displayCardAnswer();
                }
                break;
            case GESTURE_ANSWER_EASE3:
                if (sDisplayAnswer) {
                    answerCard(EASE_3);
                } else {
                    displayCardAnswer();
                }
                break;
            case GESTURE_ANSWER_EASE4:
                if (sDisplayAnswer) {
                    answerCard(EASE_4);
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
                } else {
                    displayCardAnswer();
                }
                break;
            case GESTURE_EXIT:
                closeReviewer(RESULT_DEFAULT, false);
                break;
            case GESTURE_UNDO:
                if (getCol().undoAvailable()) {
                    undo();
                }
                break;
            case GESTURE_EDIT:
                editCard();
                break;
            case GESTURE_MARK:
                onMark(mCurrentCard);
                break;
            case GESTURE_LOOKUP:
                lookUpOrSelectText();
                break;
            case GESTURE_BURY_CARD:
                dismiss(Collection.DismissType.BURY_CARD);
                break;
            case GESTURE_BURY_NOTE:
                dismiss(Collection.DismissType.BURY_NOTE);
                break;
            case GESTURE_SUSPEND_CARD:
                dismiss(Collection.DismissType.SUSPEND_CARD);
                break;
            case GESTURE_SUSPEND_NOTE:
                dismiss(Collection.DismissType.SUSPEND_NOTE);
                break;
            case GESTURE_DELETE:
                showDeleteNoteDialog();
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
            Timber.i("AbstractFlashcardViewer:: onJsAlert: %s", message);
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

        if (saveDeck) {
            UIUtils.saveCollectionInBackground(this);
        }
        finishWithAnimation(ActivityTransitionAnimation.RIGHT);
    }


    protected void refreshActionBar() {
        supportInvalidateOptionsMenu();
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
            // Go back to immersive mode if the user had temporarily exited it (and then execute swipe gesture)
            if (mPrefFullscreenReview > 0 &&
                    CompatHelper.getCompat().isImmersiveSystemUiVisible(AbstractFlashcardViewer.this)) {
                delayedHide(INITIAL_HIDE_DELAY);
            }
            if (mGesturesEnabled) {
                try {
                    float dy = e2.getY() - e1.getY();
                    float dx = e2.getX() - e1.getX();

                    if (Math.abs(dx) > Math.abs(dy)) {
                        // horizontal swipe if moved further in x direction than y direction
                        if (dx > AnkiDroidApp.sSwipeMinDistance
                                && Math.abs(velocityX) > AnkiDroidApp.sSwipeThresholdVelocity
                                && !mIsXScrolling && !mIsSelecting) {
                            // right
                            executeCommand(mGestureSwipeRight);
                        } else if (dx < -AnkiDroidApp.sSwipeMinDistance
                                && Math.abs(velocityX) > AnkiDroidApp.sSwipeThresholdVelocity
                                && !mIsXScrolling && !mIsSelecting) {
                            // left
                            executeCommand(mGestureSwipeLeft);
                        }
                    } else {
                        // otherwise vertical swipe
                        if (dy > AnkiDroidApp.sSwipeMinDistance
                                && Math.abs(velocityY) > AnkiDroidApp.sSwipeThresholdVelocity
                                && !mIsYScrolling) {
                            // down
                            executeCommand(mGestureSwipeDown);
                        } else if (dy < -AnkiDroidApp.sSwipeMinDistance
                                && Math.abs(velocityY) > AnkiDroidApp.sSwipeThresholdVelocity
                                && !mIsYScrolling) {
                            // up
                            executeCommand(mGestureSwipeUp);
                        }
                    }
                } catch (Exception e) {
                    Timber.e(e, "onFling Exception");
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
            // Go back to immersive mode if the user had temporarily exited it (and ignore the tap gesture)
            if (mPrefFullscreenReview > 0 &&
                    CompatHelper.getCompat().isImmersiveSystemUiVisible(AbstractFlashcardViewer.this)) {
                delayedHide(INITIAL_HIDE_DELAY);
                return true;
            }
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


    protected final Handler mFullScreenHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            if (mPrefFullscreenReview > 0) {
                CompatHelper.getCompat().setFullScreen(AbstractFlashcardViewer.this);
            }
        }
    };

    protected void delayedHide(int delayMillis) {
        mFullScreenHandler.removeMessages(0);
        mFullScreenHandler.sendEmptyMessageDelayed(0, delayMillis);
    }

    /**
     * Removes first occurrence in answerContent of any audio that is present due to use of
     * {{FrontSide}} on the answer.
     * @param answerContent     The content from which to remove front side audio.
     * @return                  The content stripped of audio due to {{FrontSide}} inclusion.
     */
    private String removeFrontSideAudio(String answerContent) {
        String answerFormat = getAnswerFormat();
        if (answerFormat.contains("{{FrontSide}}")) { // possible audio removal necessary
            String frontSideFormat = mCurrentCard._getQA(false).get("q");
            Matcher audioReferences = Sound.sSoundPattern.matcher(frontSideFormat);
            // remove the first instance of audio contained in "{{FrontSide}}"
            while (audioReferences.find()) {
                answerContent = answerContent.replaceFirst(Pattern.quote(audioReferences.group()), "");
            }
        }
        return answerContent;
    }

    /**
     * Public method to start new video player activity
     */
    public void playVideo(String path) {
        Intent videoPlayer = new Intent(this, VideoPlayer.class);
        videoPlayer.putExtra("path", path);
        startActivityWithoutAnimation(videoPlayer);
    }

    /** Callback for when TTS has been initialized. */
    public void ttsInitialized() {
        mTtsInitialized = true;
        if (mReplayOnTtsInit) {
            playSounds(true);
        }
    }

    protected void onMark(Card card) {
        Note note = card.note();
        if (note.hasTag("marked")) {
            note.delTag("marked");
        } else {
            note.addTag("marked");
        }
        note.flush();
        refreshActionBar();
    }

    protected void dismiss(Collection.DismissType type) {
        DeckTask.launchDeckTask(DeckTask.TASK_TYPE_DISMISS, mDismissCardHandler,
                new DeckTask.TaskData(new Object[]{mCurrentCard, type}));
    }
}
