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
import android.annotation.TargetApi;
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

import androidx.annotation.CheckResult;
import androidx.annotation.IdRes;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.core.content.ContextCompat;
import androidx.core.view.GestureDetectorCompat;
import androidx.appcompat.app.ActionBar;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.SpannedString;
import android.text.TextUtils;
import android.text.style.UnderlineSpan;
import android.util.TypedValue;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.JsResult;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.Chronometer;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.afollestad.materialdialogs.MaterialDialog;
import com.afollestad.materialdialogs.util.TypefaceHelper;
import com.ichi2.anim.ActivityTransitionAnimation;
import com.ichi2.anim.ViewAnimation;
import com.ichi2.anki.cardviewer.CardAppearance;
import com.ichi2.anki.receiver.SdCardReceiver;
import com.ichi2.anki.reviewer.CardMarker;
import com.ichi2.anki.reviewer.ReviewerCustomFonts;
import com.ichi2.anki.reviewer.ReviewerUi;
import com.ichi2.anki.cardviewer.TypedAnswer;
import com.ichi2.async.CollectionTask;
import com.ichi2.compat.CompatHelper;
import com.ichi2.libanki.Decks;
import com.ichi2.libanki.sched.AbstractSched;
import com.ichi2.libanki.Card;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Consts;
import com.ichi2.libanki.Note;
import com.ichi2.libanki.Sound;
import com.ichi2.libanki.Utils;
import com.ichi2.libanki.template.Template;
import com.ichi2.themes.HtmlColors;
import com.ichi2.themes.Themes;
import com.ichi2.utils.AdaptionUtil;
import com.ichi2.utils.DiffEngine;
import com.ichi2.utils.FunctionalInterfaces.Consumer;
import com.ichi2.utils.FunctionalInterfaces.Function;

import com.ichi2.utils.JSONArray;
import com.ichi2.utils.JSONException;
import com.ichi2.utils.JSONObject;
import com.ichi2.utils.WebViewDebugging;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.ref.WeakReference;
import java.net.URLDecoder;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import timber.log.Timber;

import static com.ichi2.anki.cardviewer.CardAppearance.calculateDynamicFontSize;
import static com.ichi2.anki.cardviewer.ViewerCommand.*;

@SuppressWarnings({"PMD.AvoidThrowingRawExceptionTypes","PMD.FieldDeclarationsShouldBeAtStartOfClass"})
public abstract class AbstractFlashcardViewer extends NavigationDrawerActivity implements ReviewerUi, CommandProcessor {

    /**
     * Result codes that are returned when this activity finishes.
     */
    public static final int RESULT_DEFAULT = 50;
    public static final int RESULT_NO_MORE_CARDS = 52;

    /**
     * Available options performed by other activities.
     */
    public static final int EDIT_CURRENT_CARD = 0;
    public static final int DECK_OPTIONS = 1;

    public static final int EASE_1 = 1;
    public static final int EASE_2 = 2;
    public static final int EASE_3 = 3;
    public static final int EASE_4 = 4;

    /** Maximum time in milliseconds to wait before accepting answer button presses. */
    private static final int DOUBLE_TAP_IGNORE_THRESHOLD = 200;

    /** Time to wait in milliseconds before resuming fullscreen mode **/
    protected static final int INITIAL_HIDE_DELAY = 200;

    // Type answer patterns
    private static final Pattern sTypeAnsPat = Pattern.compile("\\[\\[type:(.+?)\\]\\]");

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
    private CardAppearance mCardAppearance;
    private boolean mPrefHideDueCount;
    private boolean mPrefShowETA;
    private boolean mShowTimer;
    protected boolean mPrefWhiteboard;
    private int mPrefFullscreenReview;
    private int mRelativeButtonSize;
    private boolean mDoubleScrolling;
    private boolean mScrollingButtons;
    private boolean mGesturesEnabled;
    // Android WebView
    protected boolean mSpeakText;
    protected boolean mDisableClipboard = false;

    protected boolean mOptUseGeneralTimerSettings;

    protected boolean mUseTimer;
    protected int mWaitAnswerSecond;
    protected int mWaitQuestionSecond;

    protected boolean mPrefUseTimer;

    protected boolean mOptUseTimer;
    protected int mOptWaitAnswerSecond;
    protected int mOptWaitQuestionSecond;

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
    private View mLookUpIcon;
    private WebView mCard;
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
    @SuppressWarnings("deprecation") // Tracked separately as #5023 on github
    private android.text.ClipboardManager mClipboard;

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

    private Spanned mCardContent;
    private String mBaseUrl;

    private int mFadeDuration = 300;

    protected AbstractSched mSched;

    private Sound mSoundPlayer = new Sound();

    private long mUseTimerDynamicMS;

    /**
     * Last card that the WebView Renderer crashed on.
     * If we get 2 crashes on the same card, then we likely have an infinite loop and want to exit gracefully.
     */
    @Nullable
    private Long lastCrashingCardId = null;

    /** Reference to the parent of the cardFrame to allow regeneration of the cardFrame in case of crash */
    private ViewGroup mCardFrameParent;

    /** Lock to allow thread-safe regeneration of mCard */
    private ReadWriteLock mCardLock = new ReentrantReadWriteLock();

    /** whether controls are currently blocked */
    private boolean mControlBlocked = true;

    /** Handle Mark/Flag state of cards */
    private CardMarker mCardMarker;
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
            // Show hint about lookup function if dictionary available
            if (!mDisableClipboard && Lookup.isAvailable()) {
                String lookupHint = getResources().getString(R.string.lookup_hint);
                UIUtils.showThemedToast(AbstractFlashcardViewer.this, lookupHint, false);
            }
            CompatHelper.getCompat().vibrate(AnkiDroidApp.getInstance().getApplicationContext(), 50);
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
                    break;
            }
        }
    };

    private View.OnTouchListener mGestureListener = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            if (gestureDetector.onTouchEvent(event)) {
                return true;
            }
            if (!mDisableClipboard) {
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
                        break;
                }
            }
            //Gesture listener is added before mCard is set
            processCardAction(card -> {
                if (card == null) return;
                card.dispatchTouchEvent(event);
            });
            return false;
        }
    };

    @SuppressLint("CheckResult")
    private void processCardAction(Consumer<WebView> cardConsumer) {
        processCardFunction(card -> {
            cardConsumer.consume(card);
            return true;
        });
    }

    @CheckResult
    private <T> T processCardFunction(Function<WebView, T> cardFunction) {
        Lock readLock = mCardLock.readLock();
        try {
            readLock.lock();
            return cardFunction.apply(mCard);
        } finally {
            readLock.unlock();
        }
    }


    protected CollectionTask.TaskListener mDismissCardHandler = new NextCardHandler() { /* superclass is sufficient */ };


    private CollectionTask.TaskListener mUpdateCardHandler = new CollectionTask.TaskListener() {
        private boolean mNoMoreCards;


        @Override
        public void onPreExecute() {
            showProgressBar();
        }


        @Override
        public void onProgressUpdate(CollectionTask.TaskData... values) {
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
        public void onPostExecute(CollectionTask.TaskData result) {
            if (!result.getBoolean()) {
                // RuntimeException occurred on update cards
                closeReviewer(DeckPicker.RESULT_DB_ERROR, false);
                return;
            }
            if (mNoMoreCards) {
                closeReviewer(RESULT_NO_MORE_CARDS, true);
            }
        }
    };

    abstract class NextCardHandler extends CollectionTask.TaskListener {
        private boolean mNoMoreCards;


        @Override
        public void onPreExecute() { /* do nothing */}


        @Override
        public void onProgressUpdate(CollectionTask.TaskData... values) {
            displayNext(values[0].getCard());
        }

        protected void displayNext(Card nextCard) {

            Resources res = getResources();

            if (mSched == null) {
                // TODO: proper testing for restored activity
                finishWithoutAnimation();
                return;
            }

            mCurrentCard = nextCard;
            if (mCurrentCard == null) {
                // If the card is null means that there are no more cards scheduled for review.
                mNoMoreCards = true; // other handlers use this, toggle state every time through
            } else {
                mNoMoreCards = false; // other handlers use this, toggle state every time through
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
                String mins = res.getQuantityString(R.plurals.in_minutes, nMins, nMins);
                String timeboxMessage = res.getQuantityString(R.plurals.timebox_reached, nCards, nCards, mins);
                UIUtils.showThemedToast(AbstractFlashcardViewer.this, timeboxMessage, true);
                getCol().startTimebox();
            }
        }


        @Override
        public void onPostExecute(CollectionTask.TaskData result) {
            postNextCardDisplay(result.getBoolean());
        }

        protected void postNextCardDisplay(boolean displaySuccess) {
            if (!displaySuccess) {
                // RuntimeException occurred on answering cards
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
    }


    protected CollectionTask.TaskListener mAnswerCardHandler = new NextCardHandler() {


        @Override
        public void onPreExecute() {
            blockControls();
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
        if (mTypeCorrect == null) {
            if (clozeIdx != 0) {
                mTypeWarning = getResources().getString(R.string.empty_card_warning);
            } else {
                mTypeWarning = getResources().getString(R.string.unknown_type_field_warning, fld);
            }
        } else if ("".equals(mTypeCorrect)) {
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
            // These functions are defined in the JavaScript file assets/scripts/card.js. We get the text back in
            // shouldOverrideUrlLoading() in createWebView() in this file.
            sb.append("<center>\n<input type=\"text\" name=\"typed\" id=\"typeans\" onfocus=\"taFocus();\" " +
                    "onblur=\"taBlur(this);\" onKeyPress=\"return taKey(this, event)\" autocomplete=\"off\" ");
            // We have to watch out. For the preview we don’t know the font or font size. Skip those there. (Anki
            // desktop just doesn’t show the input tag there. Do it with standard values here instead.)
            if (mTypeFont != null && !TextUtils.isEmpty(mTypeFont) && mTypeSize > 0) {
                sb.append("style=\"font-family: '").append(mTypeFont).append("'; font-size: ")
                        .append(Integer.toString(mTypeSize)).append("px;\" ");
            }
            sb.append(">\n</center>\n");
        } else {
            sb.append("<span id=\"typeans\" class=\"typePrompt");
            if (mUseInputTag) {
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
    @VisibleForTesting
    String typeAnsAnswerFilter(String buf, String userAnswer, String correctAnswer) {
        Matcher m = sTypeAnsPat.matcher(buf);
        DiffEngine diffEngine = new DiffEngine();
        StringBuilder sb = new StringBuilder();
        sb.append("<div><code id=\"typeans\">");

        // We have to use Matcher.quoteReplacement because the inputs here might have $ or \.

        if (!TextUtils.isEmpty(userAnswer)) {
            // The user did type something.
            if (userAnswer.equals(correctAnswer)) {
                // and it was right.
                sb.append(Matcher.quoteReplacement(DiffEngine.wrapGood(correctAnswer)));
                sb.append("<span id=\"typecheckmark\">\u2714</span>"); // Heavy check mark
            } else {
                // Answer not correct.
                // Only use the complex diff code when needed, that is when we have some typed text that is not
                // exactly the same as the correct text.
                String[] diffedStrings = diffEngine.diffedHtmlStrings(correctAnswer, userAnswer);
                // We know we get back two strings.
                sb.append(Matcher.quoteReplacement(diffedStrings[0]));
                sb.append("<br><span id=\"typearrow\">&darr;</span><br>");
                sb.append(Matcher.quoteReplacement(diffedStrings[1]));
            }
        } else {
            if (!mUseInputTag) {
                sb.append(Matcher.quoteReplacement(DiffEngine.wrapMissing(correctAnswer)));
            } else {
                sb.append(Matcher.quoteReplacement(correctAnswer));
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

    protected int mPrefWaitAnswerSecond;
    protected int mPrefWaitQuestionSecond;

    protected int getDefaultEase() {
        if (getAnswerButtonCount() == 4) {
            return EASE_3;
        } else {
            return EASE_2;
        }
    }


    protected int getAnswerButtonCount() {
        return getCol().getSched().answerButtons(mCurrentCard);
    }


    // ----------------------------------------------------------------------------
    // ANDROID METHODS
    // ----------------------------------------------------------------------------

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Timber.d("onCreate()");
        SharedPreferences preferences = restorePreferences();
        mCardAppearance = CardAppearance.create(new ReviewerCustomFonts(this.getBaseContext()), preferences);
        super.onCreate(savedInstanceState);
        setContentView(getContentViewAttr(mPrefFullscreenReview));

        // Make ACTION_PROCESS_TEXT for in-app searching possible on > Android 4.0
        getDelegate().setHandleNativeActionModesEnabled(true);

        View mainView = findViewById(android.R.id.content);
        initNavigationDrawer(mainView);
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

        restoreCollectionPreferences();

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
        if (mCardFrame != null) {
            mCardFrame.removeAllViews();
        }
        destroyWebView(mCard); //OK to do without a lock
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
        if (processCardFunction(card -> processHardwareButtonScroll(keyCode, card))) {
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }


    private boolean processHardwareButtonScroll(int keyCode, WebView card) {
        if (keyCode == KeyEvent.KEYCODE_PAGE_UP) {
            card.pageUp(false);
            if (mDoubleScrolling) {
                card.pageUp(false);
            }
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_PAGE_DOWN) {
            card.pageDown(false);
            if (mDoubleScrolling) {
                card.pageDown(false);
            }
            return true;
        }
        if (mScrollingButtons && keyCode == KeyEvent.KEYCODE_PICTSYMBOLS) {
            card.pageUp(false);
            if (mDoubleScrolling) {
                card.pageUp(false);
            }
            return true;
        }
        if (mScrollingButtons && keyCode == KeyEvent.KEYCODE_SWITCH_CHARSET) {
            card.pageDown(false);
            if (mDoubleScrolling) {
                card.pageDown(false);
            }
            return true;
        }
        return false;
    }


    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (answerFieldIsFocused()) {
            return super.onKeyUp(keyCode, event);
        }
        if (!sDisplayAnswer) {
            if (keyCode == KeyEvent.KEYCODE_SPACE || keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) {
                displayCardAnswer();
                return true;
            }
        }
        return super.onKeyUp(keyCode, event);
    }


    protected boolean answerFieldIsFocused() {
        return mAnswerField != null && mAnswerField.isFocused();
    }


    @SuppressWarnings("deprecation") // Tracked separately as #5023 on github
    protected boolean clipboardHasText() {
        return mClipboard != null && mClipboard.hasText();
    }


    @SuppressWarnings("deprecation") // Tracked separately as #5023 on github
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
    @SuppressWarnings("deprecation") // Tracked separately as #5023 on github
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
            CollectionTask.launchCollectionTask(CollectionTask.TASK_TYPE_ANSWER_CARD, mAnswerCardHandler,
                    new CollectionTask.TaskData(null, 0));
        }

        if (requestCode == EDIT_CURRENT_CARD) {
            if (resultCode == RESULT_OK) {
                // content of note was changed so update the note and current card
                Timber.i("AbstractFlashcardViewer:: Saving card...");
                CollectionTask.launchCollectionTask(CollectionTask.TASK_TYPE_UPDATE_NOTE, mUpdateCardHandler,
                        new CollectionTask.TaskData(sEditorCard, true));
            } else if (resultCode == RESULT_CANCELED && !(data!=null && data.hasExtra("reloadRequired"))) {
                // nothing was changed by the note editor so just redraw the card
                redrawCard();
            }
        } else if (requestCode == DECK_OPTIONS && resultCode == RESULT_OK) {
            getCol().getSched().reset();
            CollectionTask.launchCollectionTask(CollectionTask.TASK_TYPE_ANSWER_CARD, mAnswerCardHandler,
                    new CollectionTask.TaskData(null, 0));
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
        long deckID = getCol().getDecks().selected();
        return deckID;
    }

    private void redrawCard() {
        //#3654 We can call this from ActivityResult, which could mean that the card content hasn't yet been set
        //if the activity was destroyed. In this case, just wait until onCollectionLoaded callback succeeds.
        if (hasLoadedCardContent()) {
            fillFlashcard();
        } else {
            Timber.i("Skipping card redraw - card still initialising.");
        }
    }

    /** Whether the callback to onCollectionLoaded has loaded card content */
    private boolean hasLoadedCardContent() {
        return mCardContent != null;
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
            blockControls();
            CollectionTask.launchCollectionTask(CollectionTask.TASK_TYPE_UNDO, mAnswerCardHandler);
        }
    }


    private void finishNoStorageAvailable() {
        AbstractFlashcardViewer.this.setResult(DeckPicker.RESULT_MEDIA_EJECTED);
        finishWithoutAnimation();
    }


    protected boolean editCard() {
        if (mCurrentCard == null) {
            // This should never occurs. It means the review button was pressed while there is no more card in the reviewer.
            return true;
        }
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
                .onPositive((dialog, which) -> {
                    Timber.i("AbstractFlashcardViewer:: OK button pressed to delete note %d", mCurrentCard.getNid());
                    mSoundPlayer.stopSounds();
                    dismiss(Collection.DismissType.DELETE_NOTE);
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
                mChosenAnswer.setTextColor(ContextCompat.getColor(this, buttonNumber == Consts.BUTTON_FOUR ?
                        R.color.material_blue_grey_600:
                        R.color.material_green_500));
                break;
            case EASE_3:
                mChosenAnswer.setText("\u2022\u2022\u2022");
                mChosenAnswer.setTextColor(ContextCompat.getColor(this, buttonNumber == Consts.BUTTON_FOUR ?
                        R.color.material_green_500 :
                        R.color.material_light_blue_500));
                break;
            case EASE_4:
                mChosenAnswer.setText("\u2022\u2022\u2022\u2022");
                mChosenAnswer.setTextColor(ContextCompat.getColor(this, R.color.material_light_blue_500));
                break;
            default:
                Timber.w("Unknown easy type %s", ease);
                break;
        }

        // remove chosen answer hint after a while
        mTimerHandler.removeCallbacks(removeChosenAnswerText);
        mTimerHandler.postDelayed(removeChosenAnswerText, mShowChosenAnswerLength);
        mSoundPlayer.stopSounds();
        mCurrentEase = ease;

        CollectionTask.launchCollectionTask(CollectionTask.TASK_TYPE_ANSWER_CARD, mAnswerCardHandler,
                new CollectionTask.TaskData(mCurrentCard, mCurrentEase));
    }


    // Set the content view to the one provided and initialize accessors.
    @SuppressWarnings("deprecation") // Tracked separately as #5023 on github for clipboard
    protected void initLayout() {
        FrameLayout mCardContainer = (FrameLayout) findViewById(R.id.flashcard_frame);

        mTopBarLayout = (RelativeLayout) findViewById(R.id.top_bar);

        ImageView mark = mTopBarLayout.findViewById(R.id.mark_icon);
        ImageView flag = mTopBarLayout.findViewById(R.id.flag_icon);
        mCardMarker = new CardMarker(mark, flag);

        mCardFrame = (FrameLayout) findViewById(R.id.flashcard);
        mCardFrameParent = (ViewGroup) mCardFrame.getParent();
        mTouchLayer = (FrameLayout) findViewById(R.id.touch_layer);
        mTouchLayer.setOnTouchListener(mGestureListener);
        if (!mDisableClipboard) {
            mClipboard = (android.text.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
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

        Button mFlipCard = (Button) findViewById(R.id.flip_card);
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
            default:
                Timber.w("Unknown answerButtonsPosition: %s", answerButtonsPosition);
                break;
        }
        answerArea.setLayoutParams(answerAreaParams);
        mCardContainer.setLayoutParams(cardContainerParams);
    }


    @SuppressLint("SetJavaScriptEnabled") // they request we review carefully because of XSS security, we have
    private WebView createWebView() {
        WebView webView = new MyWebView(this);
        webView.setScrollBarStyle(View.SCROLLBARS_OUTSIDE_OVERLAY);
        webView.getSettings().setDisplayZoomControls(false);
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

        webView.setWebViewClient(new CardViewerWebClient());
        // Set transparent color to prevent flashing white when night mode enabled
        webView.setBackgroundColor(Color.argb(1, 0, 0, 0));
        return webView;
    }

    /** If a card is displaying the question, flip it, otherwise answer it */
    private void flipOrAnswerCard(int cardOrdinal) {
        if (!sDisplayAnswer) {
           displayCardAnswer();
           return;
        }
        answerCard(cardOrdinal);
    }

    private boolean webViewRendererLastCrashedOnCard(long cardId) {
        return lastCrashingCardId != null && lastCrashingCardId == cardId;
    }


    private boolean canRecoverFromWebViewRendererCrash() {
        // DEFECT
        // If we don't have a card to render, we're in a bad state. The class doesn't currently track state
        // well enough to be able to know exactly where we are in the initialisation pipeline.
        // so it's best to mark the crash as non-recoverable.
        // We should fix this, but it's very unlikely that we'll ever get here. Logs will tell

        // Revisit webViewCrashedOnCard() if changing this. Logic currently assumes we have a card.
        return mCurrentCard != null;
    }

    //#5780 - Users could OOM the WebView Renderer. This triggers the same symptoms
    @VisibleForTesting()
    @SuppressWarnings("unused")
    public void crashWebViewRenderer() {
        loadUrlInViewer("chrome://crash");
    }


    /** Used to set the "javascript:" URIs for IPC */
    private void loadUrlInViewer(final String url) {
        processCardAction(card -> card.loadUrl(url));
    }

    private <T extends View> T inflateNewView(@IdRes int id) {
        int layoutId = getContentViewAttr(mPrefFullscreenReview);
        ViewGroup content = (ViewGroup) LayoutInflater.from(AbstractFlashcardViewer.this).inflate(layoutId, null, false);
        T ret = content.findViewById(id);
        ((ViewGroup) ret.getParent()).removeView(ret); //detach the view from its parent
        content.removeAllViews();
        return ret;
    }

    private void destroyWebView(WebView webView) {
        try {
            if (webView != null) {
                webView.stopLoading();
                webView.setWebChromeClient(null);
                webView.setWebViewClient(null);
                webView.destroy();
            }
        } catch (NullPointerException npe) {
            Timber.e(npe, "WebView became null on destruction");
        }
    }

    protected boolean shouldShowNextReviewTime() {
        return mShowNextReviewTime;
    }

    protected void displayAnswerBottomBar() {
        // hide flipcard button
        mFlipCardLayout.setVisibility(View.GONE);
    }


    protected void hideEaseButtons() {
        mEase1Layout.setVisibility(View.GONE);
        mEase2Layout.setVisibility(View.GONE);
        mEase3Layout.setVisibility(View.GONE);
        mEase4Layout.setVisibility(View.GONE);
        mFlipCardLayout.setVisibility(View.VISIBLE);
        focusAnswerCompletionField();
    }

    /**
     * Focuses the appropriate field for an answer
     * And allows keyboard shortcuts to go to the default handlers.
     * */
    private void focusAnswerCompletionField() {
        // This does not handle mUseInputTag (the WebView contains an input field with a typable answer).
        // In this case, the user can use touch to focus the field if necessary.
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
        mUseInputTag = preferences.getBoolean("useInputTag", false);
        // On newer Androids, ignore this setting, which should be hidden in the prefs anyway.
        mDisableClipboard = "0".equals(preferences.getString("dictionary", "0"));
        // mDeckFilename = preferences.getString("deckFilename", "");
        mPrefFullscreenReview = Integer.parseInt(preferences.getString("fullscreenMode", "0"));
        mRelativeButtonSize = preferences.getInt("answerButtonSize", 100);
        mSpeakText = preferences.getBoolean("tts", false);
        mPrefUseTimer = preferences.getBoolean("timeoutAnswer", false);
        mPrefWaitAnswerSecond = preferences.getInt("timeoutAnswerSeconds", 20);
        mPrefWaitQuestionSecond = preferences.getInt("timeoutQuestionSeconds", 60);
        mScrollingButtons = preferences.getBoolean("scrolling_buttons", false);
        mDoubleScrolling = preferences.getBoolean("double_scrolling", false);

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

        if (preferences.getBoolean("keepScreenOn", false)) {
            this.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }

        return preferences;
    }


    private void restoreCollectionPreferences() {

        // These are preferences we pull out of the collection instead of SharedPreferences
        try {
            mShowNextReviewTime = getCol().getConf().getBoolean("estTimes");
            mShowRemainingCardCount = getCol().getConf().getBoolean("dueCounts");

            // Dynamic don't have review options; attempt to get deck-specific auto-advance options
            // but be prepared to go with all default if it's a dynamic deck
            JSONObject revOptions = new JSONObject();
            long selectedDid = getCol().getDecks().selected();
            if (!getCol().getDecks().isDyn(selectedDid)) {
                revOptions = getCol().getDecks().confForDid(selectedDid).getJSONObject("rev");
            }

            mOptUseGeneralTimerSettings = revOptions.optBoolean("useGeneralTimeoutSettings", true);
            mOptUseTimer = revOptions.optBoolean("timeoutAnswer", false);
            mOptWaitAnswerSecond = revOptions.optInt("timeoutAnswerSeconds", 20);
            mOptWaitQuestionSecond = revOptions.optInt("timeoutQuestionSeconds", 60);
        } catch (JSONException e) {
            Timber.e(e, "Unable to restoreCollectionPreferences");
            throw new RuntimeException(e);
        } catch (NullPointerException npe) {
            // NPE on collection only happens if the Collection is broken, follow AnkiActivity example
            Intent deckPicker = new Intent(this, DeckPicker.class);
            deckPicker.putExtra("collectionLoadError", true); // don't currently do anything with this
            deckPicker.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivityWithAnimation(deckPicker, ActivityTransitionAnimation.LEFT);
        }
    }


    private void setInterface() {
        if (mCurrentCard == null) {
            return;
        }
        recreateWebView();
    }

    private void recreateWebView() {
        if (mCard == null) {
            mCard = createWebView();
            WebViewDebugging.initializeDebugging(AnkiDroidApp.getSharedPrefs(this));
            mCardFrame.addView(mCard);
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
        if (mCurrentCard == null) return;
        ActionBar actionBar = getSupportActionBar();
        int[] counts = mSched.counts(mCurrentCard);

        if (actionBar != null) {
            String title = Decks.basename(getCol().getDecks().get(mCurrentCard.getDid()).getString("name"));
            actionBar.setTitle(title);
            if (mPrefShowETA) {
                int eta = mSched.eta(counts, false);
                actionBar.setSubtitle(Utils.remainingTime(AnkiDroidApp.getInstance(), eta * 60));
            }
        }

        SpannableString newCount = new SpannableString(String.valueOf(counts[0]));
        SpannableString lrnCount = new SpannableString(String.valueOf(counts[1]));
        SpannableString revCount = new SpannableString(String.valueOf(counts[2]));
        if (mPrefHideDueCount) {
            revCount = new SpannableString("???");
        }

        switch (mSched.countIdx(mCurrentCard)) {
            case Consts.CARD_TYPE_NEW:
                newCount.setSpan(new UnderlineSpan(), 0, newCount.length(), 0);
                break;
            case Consts.CARD_TYPE_LRN:
                lrnCount.setSpan(new UnderlineSpan(), 0, lrnCount.length(), 0);
                break;
            case Consts.CARD_TYPE_REV:
                revCount.setSpan(new UnderlineSpan(), 0, revCount.length(), 0);
                break;
            default:
                Timber.w("Unknown card type %s", mSched.countIdx(mCurrentCard));
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

            Timber.v("question: '%s'", question);
            // Show text entry based on if the user wants to write the answer
            if (typeAnswer()) {
                mAnswerField.setVisibility(View.VISIBLE);
            } else {
                mAnswerField.setVisibility(View.GONE);
            }

            displayString = CardAppearance.enrichWithQADiv(question, false);

            //if (mSpeakText) {
            // ReadText.setLanguageInformation(Model.getModel(DeckManager.getMainDeck(),
            // mCurrentCard.getCardModelId(), false).getId(), mCurrentCard.getCardModelId());
            //}
        }

        updateCard(displayString);
        hideEaseButtons();

        // Check if it should use the general 'Timeout settings' or the ones specific to this deck
        if (mOptUseGeneralTimerSettings) {
            mUseTimer = mPrefUseTimer;
            mWaitAnswerSecond = mPrefWaitAnswerSecond;
            mWaitQuestionSecond = mPrefWaitQuestionSecond;
        } else {
            mUseTimer = mOptUseTimer;
            mWaitAnswerSecond = mOptWaitAnswerSecond;
            mWaitQuestionSecond = mOptWaitQuestionSecond;
        }

        // If the user wants to show the answer automatically
        if (mUseTimer) {
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
        return TypedAnswer.cleanCorrectAnswer(answer);
    }


    /**
     * Clean up the typed answer text, so it can be used for the comparison with the correct answer
     *
     * @param answer The answer text typed by the user.
     * @return The typed answer text, cleaned up.
     */
    protected String cleanTypedAnswer(String answer) {
        if (answer == null || "".equals(answer)) {
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
        updateCard(CardAppearance.enrichWithQADiv(answer, true));
        displayAnswerBottomBar();
        // If the user wants to show the next question automatically
        if (mUseTimer) {
            long delay = mWaitQuestionSecond * 1000 + mUseTimerDynamicMS;
            if (delay > 0) {
                mTimeoutHandler.removeCallbacks(mShowQuestionTask);
                mTimeoutHandler.postDelayed(mShowQuestionTask, delay);
            }
        }
    }


    /** Scroll the currently shown flashcard vertically
     *
     * @param dy amount to be scrolled
     */
    public void scrollCurrentCardBy(int dy) {
        processCardAction(card -> {
            if (dy != 0 && card.canScrollVertically(dy)) {
                card.scrollBy(0, dy);
            }
        });
    }


    /** Tap onto the currently shown flashcard at position x and y
     *
     * @param x horizontal position of the event
     * @param y vertical position of the event
     */
    public void tapOnCurrentCard(int x, int y) {
        // assemble suitable ACTION_DOWN and ACTION_UP events and forward them to the card's handler
        MotionEvent eDown = MotionEvent.obtain(SystemClock.uptimeMillis(),
                SystemClock.uptimeMillis(), MotionEvent.ACTION_DOWN, x, y,
                1, 1, 0, 1, 1, 0, 0);
        processCardAction(card -> card.dispatchTouchEvent(eDown));

        MotionEvent eUp = MotionEvent.obtain(eDown.getDownTime(),
                SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, x, y,
                1, 1, 0, 1, 1, 0, 0);
        processCardAction(card -> card.dispatchTouchEvent(eUp));

    }


    /**
     * getAnswerFormat returns the answer part of this card's template as entered by user, without any parsing
     */
    public String getAnswerFormat() {
        JSONObject model = mCurrentCard.model();
        JSONObject template;
        if (model.getInt("type") == Consts.MODEL_STD) {
            template = model.getJSONArray("tmpls").getJSONObject(mCurrentCard.getOrd());
        } else {
            template = model.getJSONArray("tmpls").getJSONObject(0);
        }

        return template.getString("afmt");
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

    protected boolean isInNightMode() {
        return mCardAppearance.isNightMode();
    }


    private void updateCard(final String newContent) {
        Timber.d("updateCard()");

        mUseTimerDynamicMS = 0;

        // Add CSS for font color and font size
        if (mCurrentCard == null) {
            processCardAction(card -> card.getSettings().setDefaultFontSize(calculateDynamicFontSize(newContent)));
        }

        if (sDisplayAnswer) {
            addAnswerSounds(newContent);
        } else {
            // reset sounds each time first side of card is displayed, which may happen repeatedly without ever
            // leaving the card (such as when edited)
            mSoundPlayer.resetSounds();
            mAnswerSoundsAdded = false;
            mSoundPlayer.addSounds(mBaseUrl, newContent, Sound.SOUNDS_QUESTION);
            if (mUseTimer && !mAnswerSoundsAdded && getConfigForCurrentCard().optBoolean("autoplay", false)) {
                addAnswerSounds(mCurrentCard.a());
            }
        }

        String content = Sound.expandSounds(mBaseUrl, newContent);

        content = CardAppearance.fixBoldStyle(content);

        Timber.v("content card = \n %s", content);

        String style = mCardAppearance.getStyle();
        Timber.v("::style:: / %s", style);

        // CSS class for card-specific styling
        String cardClass = mCardAppearance.getCardClass(mCurrentCard.getOrd() + 1, Themes.getCurrentTheme(this));
        if (Template.textContainsMathjax(content)) {
            cardClass += " mathjax-needs-to-render";
        }

        if (isInNightMode()) {
            // If card styling doesn't contain any mention of the night_mode class then do color inversion as fallback
            // TODO: find more robust solution that won't match unrelated classes like "night_mode_old"
            if (!mCurrentCard.css().contains(".night_mode")) {
                content = HtmlColors.invertColors(content);
            }
        }


        content = CardAppearance.convertSmpToHtmlEntity(content);
        mCardContent = new SpannedString(mCardTemplate.replace("::content::", content)
                .replace("::style::", style).replace("::class::", cardClass));
        Timber.d("base url = %s", mBaseUrl);

        if (AnkiDroidApp.getSharedPrefs(this).getBoolean("html_javascript_debugging", false)) {
            try {
                try (FileOutputStream f = new FileOutputStream(new File(CollectionHelper.getCurrentAnkiDroidDirectory(this),
                        "card.html"))) {
                    f.write(mCardContent.toString().getBytes());
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
                    if (mUseTimer) {
                        mUseTimerDynamicMS = mSoundPlayer.getSoundsLength(Sound.SOUNDS_ANSWER);
                    }
                } else { // question is displayed
                    mSoundPlayer.playSounds(Sound.SOUNDS_QUESTION);
                    // If the user wants to show the answer automatically
                    if (mUseTimer) {
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
     * @param card     The card to play TTS for
     * @param cardSide The side of the current card to play TTS for
     */
    private static void readCardText(final Card card, final int cardSide) {
        final String cardSideContent;
        if (Sound.SOUNDS_QUESTION == cardSide) {
            cardSideContent = card.q(true);
        } else if (Sound.SOUNDS_ANSWER == cardSide) {
            cardSideContent = card.getPureAnswer();
        } else {
            Timber.w("Unrecognised cardSide");
            return;
        }

        ReadText.readCardSide(cardSide, cardSideContent, getDeckIdForCard(card), card.getOrd());
    }

    /**
     * Shows the dialogue for selecting TTS for the current card and cardside.
     */
    protected void showSelectTtsDialogue() {
        if (mTtsInitialized) {
            if (!sDisplayAnswer) {
                ReadText.selectTts(Utils.stripHTML(mCurrentCard.q(true)), getDeckIdForCard(mCurrentCard), mCurrentCard.getOrd(),
                        Sound.SOUNDS_QUESTION);
            } else {
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
        if (mCardContent == null) {
            Timber.w("fillFlashCard() called with no card content");
            return;
        }
        final String cardContent = mCardContent.toString();
        processCardAction(card -> loadContentIntoCard(card, cardContent));
        if (mShowTimer && mCardTimer.getVisibility() == View.INVISIBLE) {
            switchTopBarVisibility(View.VISIBLE);
        }
        if (!sDisplayAnswer) {
            updateForNewCard();
        }
    }


    private void loadContentIntoCard(WebView card, String content) {
        if (card != null) {
            CompatHelper.getCompat().setHTML5MediaAutoPlay(card.getSettings(), getConfigForCurrentCard().optBoolean("autoplay"));
            card.loadDataWithBaseURL(mBaseUrl + "__viewer__.html", content, "text/html", "utf-8", null);
        }
    }


    public static Card getEditorCard() {
        return sEditorCard;
    }


    /**
     * @return true if the AnkiDroid preference for writing answer is true and if the Anki Deck CardLayout specifies a
     *         field to query
     */
    private boolean typeAnswer() {
        return !mUseInputTag && null != mTypeCorrect;
    }


    private void unblockControls() {
        mControlBlocked = false;
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
        invalidateOptionsMenu();
    }


    private void blockControls() {
        mControlBlocked = true;
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
        invalidateOptionsMenu();
    }


    /**
     * Select Text in the webview and automatically sends the selected text to the clipboard. From
     * http://cosmez.blogspot.com/2010/04/webview-emulateshiftheld-on-android.html
     */
    @SuppressWarnings("deprecation") // Tracked separately in Github as #5024
    private void selectAndCopyText() {
        try {
            KeyEvent shiftPressEvent = new KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SHIFT_LEFT, 0, 0);
            processCardAction(shiftPressEvent::dispatch);
            shiftPressEvent.isShiftPressed();
            mIsSelecting = true;
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    public boolean executeCommand(@ViewerCommandDef int which) {
        if (mControlBlocked) {
            return false;
        }
        switch (which) {
            case COMMAND_NOTHING:
                return true;
            case COMMAND_SHOW_ANSWER:
                if (sDisplayAnswer) {
                    return false;
                }
                displayCardAnswer();
                return true;
            case COMMAND_FLIP_OR_ANSWER_EASE1:
                flipOrAnswerCard(EASE_1);
                return true;
            case COMMAND_FLIP_OR_ANSWER_EASE2:
                flipOrAnswerCard(EASE_2);
                return true;
            case COMMAND_FLIP_OR_ANSWER_EASE3:
                flipOrAnswerCard(EASE_3);
                return true;
            case COMMAND_FLIP_OR_ANSWER_EASE4:
                flipOrAnswerCard(EASE_4);
                return true;
            case COMMAND_FLIP_OR_ANSWER_RECOMMENDED:
                flipOrAnswerCard(getRecommendedEase(false));
                return true;
            case COMMAND_FLIP_OR_ANSWER_BETTER_THAN_RECOMMENDED:
                flipOrAnswerCard(getRecommendedEase(true));
                return true;
            case COMMAND_EXIT:
                closeReviewer(RESULT_DEFAULT, false);
                return true;
            case COMMAND_UNDO:
                if (!getCol().undoAvailable()) {
                    return false;
                }
                undo();
                return true;
            case COMMAND_EDIT:
                editCard();
                return true;
            case COMMAND_MARK:
                onMark(mCurrentCard);
                return true;
            case COMMAND_LOOKUP:
                lookUpOrSelectText();
                return true;
            case COMMAND_BURY_CARD:
                dismiss(Collection.DismissType.BURY_CARD);
                return true;
            case COMMAND_BURY_NOTE:
                dismiss(Collection.DismissType.BURY_NOTE);
                return true;
            case COMMAND_SUSPEND_CARD:
                dismiss(Collection.DismissType.SUSPEND_CARD);
                return true;
            case COMMAND_SUSPEND_NOTE:
                dismiss(Collection.DismissType.SUSPEND_NOTE);
                return true;
            case COMMAND_DELETE:
                showDeleteNoteDialog();
                return true;
            case COMMAND_PLAY_MEDIA:
                playSounds(true);
                return true;
            default:
                Timber.w("Unknown command requested: %s", which);
                return false;
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
        String newAnswerContent = answerContent;
        if (answerFormat.contains("{{FrontSide}}")) { // possible audio removal necessary
            String frontSideFormat = mCurrentCard._getQA(false).get("q");
            Matcher audioReferences = Sound.sSoundPattern.matcher(frontSideFormat);
            // remove the first instance of audio contained in "{{FrontSide}}"
            while (audioReferences.find()) {
                newAnswerContent = newAnswerContent.replaceFirst(Pattern.quote(audioReferences.group()), "");
            }
        }
        return newAnswerContent;
    }

    /**
     * Public method to start new video player activity
     */
    public void playVideo(String path) {
        Timber.i("Launching Video: %s", path);
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

    private void drawMark() {
        if (mCurrentCard == null) {
            return;
        }
        mCardMarker.displayMark(shouldDisplayMark());
    }


    protected boolean shouldDisplayMark() {
        return mCurrentCard.note().hasTag("marked");
    }


    protected void onMark(Card card) {
        if (card == null) {
            return;
        }
        Note note = card.note();
        if (note.hasTag("marked")) {
            note.delTag("marked");
        } else {
            note.addTag("marked");
        }
        note.flush();
        refreshActionBar();
        drawMark();
    }

    private void drawFlag() {
        if (mCurrentCard == null) {
            return;
        }
        mCardMarker.displayFlag(getFlagToDisplay());
    }


    protected int getFlagToDisplay() {
        return mCurrentCard.getUserFlag();
    }


    protected void onFlag(Card card, int flag) {
        if (card == null) {
            return;
        }
        card.setUserFlag(flag);
        card.flush();
        refreshActionBar();
        drawFlag();
        /* Following code would allow to update value of {{cardFlag}}.
           Anki does not update this value when a flag is changed, so
           currently this code would do something that anki itself
           does not do. I hope in the future Anki will correct that
           and this code may becomes useful.

        card._getQA(true); //force reload. Useful iff {{cardFlag}} occurs in the template
        if (sDisplayAnswer) {
            displayCardAnswer();
        } else {
            displayCardQuestion();
            } */
    }

    protected void dismiss(Collection.DismissType type) {
        blockControls();
        CollectionTask.launchCollectionTask(CollectionTask.TASK_TYPE_DISMISS, mDismissCardHandler,
                new CollectionTask.TaskData(new Object[]{mCurrentCard, type}));
    }

    /** Signals from a WebView represent actions with no parameters */
    @VisibleForTesting
    static class WebViewSignalParserUtils {
        /** A signal which we did not know how to handle */
        public static final int SIGNAL_UNHANDLED = 0;
        /** A known signal which should perform a noop */
        public static final int SIGNAL_NOOP = 1;

        public static final int TYPE_FOCUS = 2;
        /** Tell the app that we no longer want to focus the WebView and should instead return keyboard focus to a
         * native answer input method. */
        public static final int RELINQUISH_FOCUS = 3;

        public static final int SHOW_ANSWER = 4;
        public static final int ANSWER_ORDINAL_1 = 5;
        public static final int ANSWER_ORDINAL_2 = 6;
        public static final int ANSWER_ORDINAL_3 = 7;
        public static final int ANSWER_ORDINAL_4 = 8;

        public static int getSignalFromUrl(String url) {
            switch (url) {
                case "signal:typefocus": return TYPE_FOCUS;
                case "signal:relinquishFocus": return RELINQUISH_FOCUS;
                case "signal:show_answer": return SHOW_ANSWER;
                case "signal:answer_ease1": return ANSWER_ORDINAL_1;
                case "signal:answer_ease2": return ANSWER_ORDINAL_2;
                case "signal:answer_ease3": return ANSWER_ORDINAL_3;
                case "signal:answer_ease4": return ANSWER_ORDINAL_4;
                default: break;
            }

            if (url.startsWith("signal:answer_ease")) {
                Timber.w("Unhandled signal: ease value: %s", url);
                return SIGNAL_NOOP;
            }

            return SIGNAL_UNHANDLED; //unknown, or not a signal.
        }
    }

    protected class CardViewerWebClient extends WebViewClient {
        @Override
        @TargetApi(Build.VERSION_CODES.N)
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            String url = request.getUrl().toString();
            Timber.d("Obtained URL from card: '%s'", url);
            return filterUrl(url);
        }


        @Override
        @TargetApi(Build.VERSION_CODES.N)
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            WebResourceResponse webResourceResponse = null;
            if (!AdaptionUtil.hasWebBrowser(getBaseContext())) {
                String scheme = request.getUrl().getScheme().trim();
                if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) {
                    String response = getResources().getString(R.string.no_outgoing_link_in_cardbrowser);
                    webResourceResponse = new WebResourceResponse("text/html", "utf-8", new ByteArrayInputStream(response.getBytes()));
                }
            }
            return webResourceResponse;
        }


        @Override
        @SuppressWarnings("deprecation") // tracked as #5017 in github
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            return filterUrl(url);
        }


        // Filter any links using the custom "playsound" protocol defined in Sound.java.
        // We play sounds through these links when a user taps the sound icon.
        private boolean filterUrl(String url) {
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
                mTypeInput = decodeUrl(url.replaceFirst("typeblurtext:", ""));
                // … and show the “SHOW ANSWER” button again.
                mFlipCardLayout.setVisibility(View.VISIBLE);
                return true;
            }
            if (url.startsWith("typeentertext:")) {
                // Store the text the javascript has send us…
                mTypeInput = decodeUrl(url.replaceFirst("typeentertext:", ""));
                // … and show the answer.
                mFlipCardLayout.performClick();
                return true;
            }
            int signalOrdinal = WebViewSignalParserUtils.getSignalFromUrl(url);
            switch (signalOrdinal) {
                case WebViewSignalParserUtils.SIGNAL_UNHANDLED:
                    break; //continue parsing
                case WebViewSignalParserUtils.SIGNAL_NOOP:
                    return true;
                case WebViewSignalParserUtils.TYPE_FOCUS:
                    // Hide the “SHOW ANSWER” button when the input has focus. The soft keyboard takes up enough
                    // space by itself.
                    mFlipCardLayout.setVisibility(View.GONE);
                    return true;
                case WebViewSignalParserUtils.RELINQUISH_FOCUS:
                    //#5811 - The WebView could be focused via mouse. Allow components to return focus to Android.
                    focusAnswerCompletionField();
                    return true;
                /**
                 *  Call displayCardAnswer() and answerCard() from anki deck template using javascript
                 *  See card.js in assets/scripts folder
                 */
                case WebViewSignalParserUtils.SHOW_ANSWER:
                    // display answer when showAnswer() called from card.js
                    if (!sDisplayAnswer) {
                        displayCardAnswer();
                    }
                    return true;
                case WebViewSignalParserUtils.ANSWER_ORDINAL_1:
                    flipOrAnswerCard(EASE_1);
                    return true;
                case WebViewSignalParserUtils.ANSWER_ORDINAL_2:
                    flipOrAnswerCard(EASE_2);
                    return true;
                case WebViewSignalParserUtils.ANSWER_ORDINAL_3:
                    flipOrAnswerCard(EASE_3);
                    return true;
                case WebViewSignalParserUtils.ANSWER_ORDINAL_4:
                    flipOrAnswerCard(EASE_4);
                    return true;
                default:
                    //We know it was a signal, but forgot a case in the case statement.
                    //This is not the same as SIGNAL_UNHANDLED, where it isn't a known signal.
                    Timber.w("Unhandled signal case: %d", signalOrdinal);
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


        private String decodeUrl(String url) {
            try {
                return URLDecoder.decode(url, "UTF-8");
            } catch (UnsupportedEncodingException e) {
                Timber.e(e, "UTF-8 isn't supported as an encoding?");
            } catch (Exception e) {
                Timber.e(e, "Exception decoding: '%s'", url);
                UIUtils.showThemedToast(AbstractFlashcardViewer.this, getString(R.string.card_viewer_url_decode_error), true);
            }
            return "";
        }


        // Run any post-load events in javascript that rely on the window being completely loaded.
        @Override
        public void onPageFinished(WebView view, String url) {
            Timber.d("onPageFinished triggered");
            drawFlag();
            drawMark();
            view.loadUrl("javascript:onPageFinished();");
        }


        /** Fix: #5780 - WebView Renderer OOM crashes reviewer */
        @Override
        @TargetApi(Build.VERSION_CODES.O)
        public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
            Timber.i("Obtaining write lock for card");
            Lock writeLock = mCardLock.writeLock();
            Timber.i("Obtained write lock for card");
            try {
                writeLock.lock();
                if (mCard == null || !mCard.equals(view)) {
                    //A view crashed that wasn't ours.
                    //We have nothing to handle. Returning false is a desire to crash, so return true.
                    Timber.i("Unrelated WebView Renderer terminated. Crashed: %b",  detail.didCrash());
                    return true;
                }

                Timber.e("WebView Renderer process terminated. Crashed: %b",  detail.didCrash());

                //Destroy the current WebView (to ensure WebView is GCed).
                //Otherwise, we get the following error:
                //"crash wasn't handled by all associated webviews, triggering application crash"
                mCardFrame.removeAllViews();
                mCardFrameParent.removeView(mCardFrame);
                //destroy after removal from the view - produces logcat warnings otherwise
                destroyWebView(mCard);
                mCard = null;
                //inflate a new instance of mCardFrame
                mCardFrame = inflateNewView(R.id.flashcard);
                //Even with the above, I occasionally saw the above error. Manually trigger the GC.
                //I'll keep this line unless I see another crash, which would point to another underlying issue.
                System.gc();

                //We only want to show one message per branch.

                //It's not necessarily an OOM crash, false implies a general code which is for "system terminated".
                int errorCauseId = detail.didCrash() ? R.string.webview_crash_unknown : R.string.webview_crash_oom;
                String errorCauseString = getResources().getString(errorCauseId);

                if (!canRecoverFromWebViewRendererCrash()) {
                    Timber.e("Unrecoverable WebView Render crash");
                    String errorMessage = getResources().getString(R.string.webview_crash_fatal, errorCauseString);
                    UIUtils.showThemedToast(AbstractFlashcardViewer.this, errorMessage, false);
                    finishWithoutAnimation();
                    return true;
                }

                if (webViewRendererLastCrashedOnCard(mCurrentCard.getId())) {
                    Timber.e("Web Renderer crash loop on card: %d", mCurrentCard.getId());
                    displayRenderLoopDialog(mCurrentCard, detail);
                    return true;
                }

                // If we get here, the error is non-fatal and we should re-render the WebView
                // This logic may need to be better defined. The card could have changed by the time we get here.
                lastCrashingCardId = mCurrentCard.getId();


                String nonFatalError = getResources().getString(R.string.webview_crash_nonfatal, errorCauseString);
                UIUtils.showThemedToast(AbstractFlashcardViewer.this, nonFatalError, false);

                //we need to add at index 0 so gestures still go through.
                mCardFrameParent.addView(mCardFrame, 0);

                recreateWebView();
            } finally {
                writeLock.unlock();
                Timber.d("Relinquished writeLock");
            }
            displayCardQuestion();

            //We handled the crash and can continue.
            return true;
        }


        @TargetApi(Build.VERSION_CODES.O)
        private void displayRenderLoopDialog(Card mCurrentCard, RenderProcessGoneDetail detail) {
            String cardInformation = Long.toString(mCurrentCard.getId());
            Resources res = getResources();

            String errorDetails = detail.didCrash()
                    ? res.getString(R.string.webview_crash_unknwon_detailed)
                    : res.getString(R.string.webview_crash_oom_details);
            new MaterialDialog.Builder(AbstractFlashcardViewer.this)
                    .title(res.getString(R.string.webview_crash_loop_dialog_title))
                    .content(res.getString(R.string.webview_crash_loop_dialog_content, cardInformation, errorDetails))
                    .positiveText(R.string.dialog_ok)
                    .cancelable(false)
                    .canceledOnTouchOutside(false)
                    .onPositive((materialDialog, dialogAction) -> finishWithoutAnimation())
                    .show();
        }
    }

    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    protected String getTypedInputText() {
        return mTypeInput;
    }

    @SuppressLint("WebViewApiAvailability")
    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    void handleUrlFromJavascript(String url) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            //WebViewCompat recommended here, but I'll avoid the dependency as it's test code
            CardViewerWebClient c = ((CardViewerWebClient) this.mCard.getWebViewClient());
            if (c == null) {
                throw new IllegalStateException("Couldn't obtain WebView - maybe it wasn't created yet");
            }
            c.filterUrl(url);
        } else {
            throw new IllegalStateException("Can't get WebViewClient due to Android API");
        }
    }

    @VisibleForTesting
    void loadInitialCard() {
        CollectionTask.launchCollectionTask(CollectionTask.TASK_TYPE_ANSWER_CARD, mAnswerCardHandler,
                new CollectionTask.TaskData(null, 0));
    }

    public boolean getControlBlocked() {
        return mControlBlocked;
    }


    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    static void setEditorCard(Card card) {
        //I don't see why we don't do this by intent.
        sEditorCard = card;
    }
}
