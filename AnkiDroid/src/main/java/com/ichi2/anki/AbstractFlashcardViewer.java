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
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;

import androidx.annotation.CheckResult;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.VisibleForTesting;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.content.ContextCompat;
import androidx.appcompat.app.ActionBar;
import androidx.webkit.WebViewAssetLoader;

import android.text.TextUtils;
import android.util.Pair;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.JsResult;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebView.HitTestResult;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.Chronometer;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.afollestad.materialdialogs.MaterialDialog;
import com.drakeet.drawer.FullDraggableContainer;
import com.google.android.material.snackbar.Snackbar;
import com.ichi2.anim.ViewAnimation;
import com.ichi2.anki.cardviewer.GestureTapProcessor;
import com.ichi2.anki.cardviewer.MissingImageHandler;
import com.ichi2.anki.dialogs.TagsDialog;
import com.ichi2.anki.multimediacard.AudioView;
import com.ichi2.anki.cardviewer.CardAppearance;
import com.ichi2.anki.receiver.SdCardReceiver;
import com.ichi2.anki.reviewer.CardMarker;
import com.ichi2.anki.cardviewer.CardTemplate;
import com.ichi2.anki.reviewer.ReviewerCustomFonts;
import com.ichi2.anki.reviewer.ReviewerUi;
import com.ichi2.anki.cardviewer.TypedAnswer;
import com.ichi2.async.CollectionTask;
import com.ichi2.async.TaskListener;
import com.ichi2.async.TaskManager;
import com.ichi2.compat.CompatHelper;
import com.ichi2.libanki.Decks;
import com.ichi2.libanki.Model;
import com.ichi2.libanki.sched.AbstractSched;
import com.ichi2.libanki.Card;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Consts;
import com.ichi2.libanki.DeckConfig;
import com.ichi2.libanki.Note;
import com.ichi2.libanki.Sound;
import com.ichi2.libanki.Utils;
import com.ichi2.libanki.template.MathJax;
import com.ichi2.libanki.template.TemplateFilters;
import com.ichi2.themes.HtmlColors;
import com.ichi2.themes.Themes;
import com.ichi2.ui.FixedEditText;
import com.ichi2.utils.AdaptionUtil;
import com.ichi2.utils.AndroidUiUtils;
import com.ichi2.utils.AssetHelper;
import com.ichi2.utils.ClipboardUtil;
import com.ichi2.utils.BooleanGetter;
import com.ichi2.utils.CardGetter;
import com.ichi2.utils.DiffEngine;
import com.ichi2.utils.FunctionalInterfaces.Consumer;
import com.ichi2.utils.FunctionalInterfaces.Function;

import com.ichi2.utils.JSONArray;
import com.ichi2.utils.JSONException;
import com.ichi2.utils.JSONObject;
import com.ichi2.utils.MaxExecFunction;
import com.ichi2.utils.WebViewDebugging;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.ref.WeakReference;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import timber.log.Timber;

import static com.ichi2.anki.cardviewer.CardAppearance.calculateDynamicFontSize;
import static com.ichi2.anki.cardviewer.ViewerCommand.*;
import static com.ichi2.anki.reviewer.CardMarker.*;
import static com.ichi2.libanki.Sound.SoundSide;

import com.github.zafarkhaja.semver.Version;
import static com.ichi2.anim.ActivityTransitionAnimation.Direction.*;

@SuppressWarnings({"PMD.AvoidThrowingRawExceptionTypes","PMD.FieldDeclarationsShouldBeAtStartOfClass"})
public abstract class AbstractFlashcardViewer extends NavigationDrawerActivity implements ReviewerUi, CommandProcessor, TagsDialog.TagsDialogListener {

    /**
     * Result codes that are returned when this activity finishes.
     */
    public static final int RESULT_DEFAULT = 50;
    public static final int RESULT_NO_MORE_CARDS = 52;
    public static final int RESULT_ABORT_AND_SYNC = 53;

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
    @VisibleForTesting
    protected static final int DOUBLE_TAP_IGNORE_THRESHOLD = 200;

    /** Time to wait in milliseconds before resuming fullscreen mode **/
    protected static final int INITIAL_HIDE_DELAY = 200;

    // Type answer patterns
    private static final Pattern sTypeAnsPat = Pattern.compile("\\[\\[type:(.+?)]]");

    /** to be sent to and from the card editor */
    private static Card sEditorCard;

    protected static boolean sDisplayAnswer = false;

    private boolean mTtsInitialized = false;
    private boolean mReplayOnTtsInit = false;

    protected static final int MENU_DISABLED = 3;

    // js api developer contact
    private String mCardSuppliedDeveloperContact  = "";
    private String mCardSuppliedApiVersion = "";

    private static final String sCurrentJsApiVersion = "0.0.1";
    private static final String sMinimumJsApiVersion = "0.0.1";

    // JS API ERROR CODE
    private static final int ankiJsErrorCodeDefault = 0;
    private static final int ankiJsErrorCodeMarkCard = 1;
    private static final int ankiJsErrorCodeFlagCard = 2;

    /**
     * Broadcast that informs us when the sd card is about to be unmounted
     */
    private BroadcastReceiver mUnmountReceiver = null;

    /**
     * Variables to hold preferences
     */
    private CardAppearance mCardAppearance;
    private boolean mPrefShowTopbar;
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
    private boolean mDoNotUseCodeFormatting;

    // Default short animation duration, provided by Android framework
    protected int mShortAnimDuration;

    // Preferences from the collection
    private boolean mShowNextReviewTime;

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

    private CardTemplate mCardTemplate;

    /**
     * Variables to hold layout objects that we need to update or handle events for
     */
    private View mLookUpIcon;
    private WebView mCardWebView;
    private FrameLayout mCardFrame;
    private FrameLayout mTouchLayer;
    private TextView mChosenAnswer;
    protected TextView mNext1;
    protected TextView mNext2;
    protected TextView mNext3;
    protected TextView mNext4;
    protected FixedEditText mAnswerField;
    protected TextView mEase1;
    protected TextView mEase2;
    protected TextView mEase3;
    protected TextView mEase4;
    protected LinearLayout mFlipCardLayout;
    protected LinearLayout mEaseButtonsLayout;
    protected LinearLayout mEase1Layout;
    protected LinearLayout mEase2Layout;
    protected LinearLayout mEase3Layout;
    protected LinearLayout mEase4Layout;
    protected FrameLayout mPreviewButtonsLayout;
    protected ImageView mPreviewPrevCard;
    protected ImageView mPreviewNextCard;
    protected TextView mPreviewToggleAnswerText;
    protected RelativeLayout mTopBarLayout;
    private Chronometer mCardTimer;
    protected Whiteboard mWhiteboard;
    private android.content.ClipboardManager mClipboard;

    protected Card mCurrentCard;
    private int mCurrentEase;

    private boolean mButtonHeightSet = false;

    private static final int sShowChosenAnswerLength = 2000;

    /**
     * A record of the last time the "show answer" or ease buttons were pressed. We keep track
     * of this time to ignore accidental button presses.
     */
    @VisibleForTesting
    protected long mLastClickTime;

    /**
     * Swipe Detection
     */
    private GestureDetector mGestureDetector;
    private MyGestureDetector mGestureDetectorImpl;
    private boolean mLinkOverridesTouchGesture;

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
    private int mGestureLongclick;
    private int mGestureVolumeUp;
    private int mGestureVolumeDown;
    private GestureTapProcessor mGestureTapProcessor = new GestureTapProcessor();

    private String mCardContent;
    private String mBaseUrl;
    private String mViewerUrl;
    private WebViewAssetLoader mAssetLoader;

    private final int mFadeDuration = 300;

    protected AbstractSched mSched;

    private final Sound mSoundPlayer = new Sound();

    /** Time taken o play all medias in mSoundPlayer */
    private long mUseTimerDynamicMS;

    /** File of the temporary mic record **/
    protected AudioView mMicToolBar;
    protected String mTempAudioPath;

    /**
     * Last card that the WebView Renderer crashed on.
     * If we get 2 crashes on the same card, then we likely have an infinite loop and want to exit gracefully.
     */
    @Nullable
    private Long mLastCrashingCardId = null;

    /** Reference to the parent of the cardFrame to allow regeneration of the cardFrame in case of crash */
    private ViewGroup mCardFrameParent;

    /** Lock to allow thread-safe regeneration of mCard */
    private final ReadWriteLock mCardLock = new ReentrantReadWriteLock();

    /** whether controls are currently blocked, and how long we expect them to be */
    private ReviewerUi.ControlBlock mControlBlocked = ControlBlock.SLOW;

    /** Handle Mark/Flag state of cards */
    private CardMarker mCardMarker;

    /** Handle providing help for "Image Not Found" */
    private static final MissingImageHandler mMissingImageHandler = new MissingImageHandler();

    /** Preference: Whether the user wants to focus "type in answer" */
    private boolean mFocusTypeAnswer;

    // ----------------------------------------------------------------------------
    // LISTENERS
    // ----------------------------------------------------------------------------

    private final Handler mHandler = new Handler() {

        @Override
        public void handleMessage(Message msg) {
            mSoundPlayer.stopSounds();
            mSoundPlayer.playSound((String) msg.obj, null, null, getSoundErrorListener());
        }
    };

    private final Handler mLongClickHandler = new Handler();
    private final Runnable mLongClickTestRunnable = new Runnable() {
        @Override
        public void run() {
            Timber.i("AbstractFlashcardViewer:: onEmulatedLongClick");
            // Show hint about lookup function if dictionary available
            if (!mDisableClipboard && Lookup.isAvailable()) {
                String lookupHint = getResources().getString(R.string.lookup_hint);
                UIUtils.showThemedToast(AbstractFlashcardViewer.this, lookupHint, false);
            }
            CompatHelper.getCompat().vibrate(AnkiDroidApp.getInstance().getApplicationContext(), 50);
            mLongClickHandler.postDelayed(mStartLongClickAction, 300);
        }
    };
    private final Runnable mStartLongClickAction = new Runnable() {
        @Override
        public void run() {
            executeCommand(mGestureLongclick);
        }
    };


    // Handler for the "show answer" button
    private final View.OnClickListener mFlipCardListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            Timber.i("AbstractFlashcardViewer:: Show answer button pressed");
            // Ignore what is most likely an accidental double-tap.
            if (getElapsedRealTime() - mLastClickTime < DOUBLE_TAP_IGNORE_THRESHOLD) {
                return;
            }
            mLastClickTime = getElapsedRealTime();
            mTimeoutHandler.removeCallbacks(mShowAnswerTask);
            displayCardAnswer();
        }
    };


    // Event handler for eases (answer buttons)
    class SelectEaseHandler implements View.OnClickListener, View.OnTouchListener {
        // maximum screen distance from initial touch where we will consider a click related to the touch
        private static final int CLICK_ACTION_THRESHOLD = 200;

        private Card mPrevCard = null;
        private boolean mHasBeenTouched = false;
        private float mTouchX;
        private float mTouchY;

        public SelectEaseHandler() {}

        @Override
        public boolean onTouch(View view, MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                // Save states when button pressed
                mPrevCard = mCurrentCard;
                mHasBeenTouched = true;
                // We will need to check if a touch is followed by a click
                // Since onTouch always come before onClick, we should check if
                // the touch is going to be a click by storing the start coordinates
                // and comparing with the end coordinates of the touch
                mTouchX = event.getRawX();
                mTouchY = event.getRawY();
            } else if (event.getAction() == MotionEvent.ACTION_UP) {
                float diffX = Math.abs(event.getRawX() - mTouchX);
                float diffY = Math.abs(event.getRawY() - mTouchY);
                // If a click is not coming then we reset the touch
                if (diffX > CLICK_ACTION_THRESHOLD || diffY > CLICK_ACTION_THRESHOLD) {
                    mHasBeenTouched = false;
                }
            }
            return false;
        }

        @Override
        public void onClick(View view) {
            // Try to perform intended action only if the button has been pressed for current card,
            // or if the button was not touched,
            if (mPrevCard == mCurrentCard || !mHasBeenTouched)  {
                // Only perform if the click was not an accidental double-tap
                if (getElapsedRealTime() - mLastClickTime >= DOUBLE_TAP_IGNORE_THRESHOLD) {
                    // For whatever reason, performClick does not return a visual feedback anymore
                    if (!mHasBeenTouched) {
                        view.setPressed(true);
                    }
                    mLastClickTime = getElapsedRealTime();
                    mTimeoutHandler.removeCallbacks(mShowQuestionTask);
                    int id = view.getId();
                    if (id == R.id.flashcard_layout_ease1) {
                        Timber.i("AbstractFlashcardViewer:: EASE_1 pressed");
                        answerCard(Consts.BUTTON_ONE);
                    } else if (id == R.id.flashcard_layout_ease2) {
                        Timber.i("AbstractFlashcardViewer:: EASE_2 pressed");
                        answerCard(Consts.BUTTON_TWO);
                    } else if (id == R.id.flashcard_layout_ease3) {
                        Timber.i("AbstractFlashcardViewer:: EASE_3 pressed");
                        answerCard(Consts.BUTTON_THREE);
                    } else if (id == R.id.flashcard_layout_ease4) {
                        Timber.i("AbstractFlashcardViewer:: EASE_4 pressed");
                        answerCard(Consts.BUTTON_FOUR);
                    } else {
                        mCurrentEase = 0;
                    }
                    if (!mHasBeenTouched) {
                        view.setPressed(false);
                    }
                }
            }
            // We will have to reset the touch after every onClick event
            // Do not return early without considering this
            mHasBeenTouched = false;
        }
    }

    private final SelectEaseHandler mEaseHandler = new SelectEaseHandler();


    @VisibleForTesting
    protected long getElapsedRealTime() {
        return SystemClock.elapsedRealtime();
    }


    private final View.OnTouchListener mGestureListener = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            if (mGestureDetector.onTouchEvent(event)) {
                return true;
            }
            if (!mDisableClipboard) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        mTouchStarted = true;
                        mLongClickHandler.postDelayed(mLongClickTestRunnable, 800);
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_MOVE:
                        if (mTouchStarted) {
                            mLongClickHandler.removeCallbacks(mLongClickTestRunnable);
                            mTouchStarted = false;
                        }
                        break;
                    default:
                        mLongClickHandler.removeCallbacks(mLongClickTestRunnable);
                        mTouchStarted = false;
                        break;
                }
            }

            if (!mGestureDetectorImpl.eventCanBeSentToWebView(event)) {
                return false;
            }
            //Gesture listener is added before mCard is set
            processCardAction(cardWebView -> {
                if (cardWebView == null) return;
                cardWebView.dispatchTouchEvent(event);
            });
            return false;
        }
    };

    @SuppressLint("CheckResult")
    //This is intentionally package-private as it removes the need for synthetic accessors
    void processCardAction(Consumer<WebView> cardConsumer) {
        processCardFunction(cardWebView -> {
            cardConsumer.consume(cardWebView);
            return true;
        });
    }

    @CheckResult
    private <T> T processCardFunction(Function<WebView, T> cardFunction) {
        Lock readLock = mCardLock.readLock();
        try {
            readLock.lock();
            return cardFunction.apply(mCardWebView);
        } finally {
            readLock.unlock();
        }
    }


    protected final NextCardHandler<BooleanGetter> mDismissCardHandler = new NextCardHandler() { /* superclass is sufficient */ };


    private final TaskListener<CardGetter, BooleanGetter> mUpdateCardHandler = new TaskListener<CardGetter, BooleanGetter>() {
        private boolean mNoMoreCards;


        @Override
        public void onPreExecute() {
            showProgressBar();
        }


        @Override
        public void onProgressUpdate(CardGetter cardGetter) {
            Card card = cardGetter.getCard();
            if (mCurrentCard != card) {
                /*
                 * Before updating mCurrentCard, we check whether it is changing or not. If the current card changes,
                 * then we need to display it as a new card, without showing the answer.
                 */
                sDisplayAnswer = false;
            }

            mCurrentCard = card;
            TaskManager.launchCollectionTask(new CollectionTask.PreloadNextCard()); // Tasks should always be launched from GUI. So in
                                                                    // listener and not in background
            if (mCurrentCard == null) {
                // If the card is null means that there are no more cards scheduled for review.
                mNoMoreCards = true;
                showProgressBar();
                return;
            }
            if (mPrefWhiteboard && mWhiteboard != null) {
                mWhiteboard.clear();
            }

            updateTypeAnswerInfo();
            if (sDisplayAnswer) {
                mSoundPlayer.resetSounds(); // load sounds from scratch, to expose any edit changes
                mAnswerSoundsAdded = false; // causes answer sounds to be reloaded
                generateQuestionSoundList(); // questions must be intentionally regenerated
                displayCardAnswer();
            } else {
                displayCardQuestion();
                mCurrentCard.startTimer();
                initTimer();
            }
            hideProgressBar();
        }


        @Override
        public void onPostExecute(BooleanGetter result) {
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

    abstract class NextCardHandler<Result extends BooleanGetter> extends TaskListener<Card, Result> {
        private boolean mNoMoreCards;


        @Override
        public void onPreExecute() {
            dealWithTimeBox();
        }


        @Override
        public void onProgressUpdate(Card card) {
            displayNext(card);
        }

        protected void displayNext(Card nextCard) {

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
        }

        private void dealWithTimeBox() {
            Resources res = getResources();
            Pair<Integer, Integer> elapsed = getCol().timeboxReached();
            if (elapsed != null) {
                int nCards = elapsed.second;
                int nMins = elapsed.first / 60;
                String mins = res.getQuantityString(R.plurals.in_minutes, nMins, nMins);
                String timeboxMessage = res.getQuantityString(R.plurals.timebox_reached, nCards, nCards, mins);
                new MaterialDialog.Builder(AbstractFlashcardViewer.this)
                        .title(res.getString(R.string.timebox_reached_title))
                        .content(timeboxMessage)
                        .positiveText(R.string.dialog_continue)
                        .negativeText(R.string.close)
                        .cancelable(true)
                        .onNegative((materialDialog, dialogAction) -> finishWithAnimation(END))
                        .onPositive((materialDialog, dialogAction) -> getCol().startTimebox())
                        .cancelListener(materialDialog -> getCol().startTimebox())
                        .show();
            }
        }


        @Override
        public void onPostExecute(Result result) {
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
            focusDefaultLayout();
        }
    }


    private void focusDefaultLayout() {
        if (!AndroidUiUtils.isRunningOnTv(this)) {
            findViewById(R.id.root_layout).requestFocus();
        } else {
            View flip = findViewById(R.id.answer_options_layout);
            if (flip == null) {
                return;
            }
            Timber.d("Requesting focus for flip button");
            flip.requestFocus();
        }
    }


    protected NextCardHandler<BooleanGetter> mAnswerCardHandler (boolean quick) {
        return new NextCardHandler() {
            @Override
            public void onPreExecute() {
                super.onPreExecute();
                blockControls(quick);
            }
        };
    }


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
        String fldTag = m.group(1);
        // if it's a cloze, extract data
        if (fldTag.startsWith("cloze:")) {
            // get field and cloze position
            clozeIdx = mCurrentCard.getOrd() + 1;
            fldTag = fldTag.split(":")[1];
        }
        // loop through fields for a match
        JSONArray flds = mCurrentCard.model().getJSONArray("flds");
        for (JSONObject fld: flds.jsonObjectIterable()) {
            String name = fld.getString("name");
            if (name.equals(fldTag)) {
                mTypeCorrect = mCurrentCard.note().getItem(name);
                if (clozeIdx != 0) {
                    // narrow to cloze
                    mTypeCorrect = contentForCloze(mTypeCorrect, clozeIdx);
                }
                mTypeFont = fld.getString("font");
                mTypeSize = fld.getInt("size");
                break;
            }
        }
        if (mTypeCorrect == null) {
            if (clozeIdx != 0) {
                mTypeWarning = getResources().getString(R.string.empty_card_warning);
            } else {
                mTypeWarning = getResources().getString(R.string.unknown_type_field_warning, fldTag);
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
                        .append(mTypeSize).append("px;\" ");
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
        sb.append(mDoNotUseCodeFormatting ? "<div><span id=\"typeans\">" : "<div><code id=\"typeans\">");


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
        sb.append(mDoNotUseCodeFormatting ? "</span></div>" : "</code></div>");
        return m.replaceAll(sb.toString());
    }

    /**
     * Return the correct answer to use for {{type::cloze::NN}} fields.
     *
     * @param txt The field text with the clozes
     * @param idx The index of the cloze to use
     * @return If the cloze strings are the same, return a single cloze string, otherwise, return
     *         a string with a comma-separeted list of strings with the correct index.
     */
    @VisibleForTesting
    protected String contentForCloze(String txt, int idx) {
        @SuppressWarnings("RegExpRedundantEscape") // In Android, } should be escaped
        Pattern re = Pattern.compile("\\{\\{c" + idx + "::(.+?)\\}\\}");
        Matcher m = re.matcher(txt);
        List<String> matches = new ArrayList<>();

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

        Set<String> uniqMatches = new HashSet<>(matches); // Allow to check whether there are distinct strings

        // Make it consistent with the Desktop version (see issue #8229)
        if (uniqMatches.size() == 1) {
            return matches.get(0);
        } else {
            return TextUtils.join(", ", matches);
        }
    }

    private final Handler mTimerHandler = new Handler();

    private final Runnable mRemoveChosenAnswerText = new Runnable() {
        @Override
        public void run() {
            mChosenAnswer.setText("");
        }
    };

    protected int mPrefWaitAnswerSecond;
    protected int mPrefWaitQuestionSecond;


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

        mShortAnimDuration = getResources().getInteger(android.R.integer.config_shortAnimTime);
    }

    protected int getContentViewAttr(int fullscreenMode) {
        return R.layout.reviewer;
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PROTECTED)
    boolean isFullscreen() {
        return !getSupportActionBar().isShowing();
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

        String mediaDir = col.getMedia().dir();
        mBaseUrl = Utils.getBaseUrl(mediaDir);
        mViewerUrl = mBaseUrl + "__viewer__.html";

        mAssetLoader = new WebViewAssetLoader.Builder()
            .addPathHandler("/", path -> {
                try {
                    File file = new File(mediaDir, path);
                    FileInputStream is = new FileInputStream(file);

                    String mimeType = AssetHelper.guessMimeType(path);

                    HashMap<String, String> headers = new HashMap<>();
                    headers.put("Access-Control-Allow-Origin", "*");

                    WebResourceResponse response = new WebResourceResponse(mimeType, null, is);
                    response.setResponseHeaders(headers);
                    return response;
                } catch (Exception e) {
                    Timber.w(e, "Error trying to open path in asset loader");
                }

                return null;
            })
            .build();

        registerExternalStorageListener();

        restoreCollectionPreferences();

        initLayout();

        setTitle();

        if (!mDisableClipboard) {
            clearClipboard();
        }

        // Load the template for the card
        try {
            String data = Utils.convertStreamToString(getAssets().open("card_template.html"));
            mCardTemplate = new CardTemplate(data);
        } catch (IOException e) {
            Timber.w(e);
        }

        // Initialize text-to-speech. This is an asynchronous operation.
        if (mSpeakText) {
            ReadText.initializeTts(this, new ReadTextListener());
        }

        // Initialize dictionary lookup feature
        Lookup.initialize(this);

        updateActionBar();
        supportInvalidateOptionsMenu();
    }

    // Saves deck each time Reviewer activity loses focus
    @Override
    protected void onPause() {
        super.onPause();
        Timber.d("onPause()");

        mTimeoutHandler.removeCallbacks(mShowAnswerTask);
        mTimeoutHandler.removeCallbacks(mShowQuestionTask);
        mLongClickHandler.removeCallbacks(mLongClickTestRunnable);
        mLongClickHandler.removeCallbacks(mStartLongClickAction);

        pauseTimer();
        mSoundPlayer.stopSounds();

        // Prevent loss of data in Cookies
        CookieManager.getInstance().flush();
    }


    @Override
    protected void onResume() {
        super.onResume();
        resumeTimer();
        // Set the context for the Sound manager
        mSoundPlayer.setContext(new WeakReference<>(this));
        // Reset the activity title
        setTitle();
        updateActionBar();
        selectNavigationItem(-1);
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Tells the scheduler there is no more current cards. 0 is
        // not a valid id.
        if (mSched != null) {
            mSched.discardCurrentCard();
        }
        Timber.d("onDestroy()");
        if (mSpeakText) {
            ReadText.releaseTts(this);
        }
        if (mUnmountReceiver != null) {
            unregisterReceiver(mUnmountReceiver);
        }
        // WebView.destroy() should be called after the end of use
        // http://developer.android.com/reference/android/webkit/WebView.html#destroy()
        if (mCardFrame != null) {
            mCardFrame.removeAllViews();
        }
        destroyWebView(mCardWebView); //OK to do without a lock
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
        if (processCardFunction(cardWebView -> processHardwareButtonScroll(keyCode, cardWebView))) {
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }


    @Override
    @Nullable
    protected Long getCurrentCardId() {
        if (mCurrentCard == null) {
            return null;
        }
        return mCurrentCard.getId();
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

    protected boolean clipboardHasText() {
        return !TextUtils.isEmpty(ClipboardUtil.getText(mClipboard));
    }

    /** We use the clipboard here for the lookup dictionary functionality
     * If the clipboard has data and we're using the functionality, then */
    private void clearClipboard() {
        if (mClipboard == null) {
            return;
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                mClipboard.clearPrimaryClip();
            } else {
                if (!mClipboard.hasPrimaryClip()) {
                    return;
                }

                CharSequence descriptionLabel = ClipboardUtil.getDescriptionLabel(mClipboard.getPrimaryClip());
                if (!"Cleared".contentEquals(descriptionLabel)) {
                    mClipboard.setPrimaryClip(ClipData.newPlainText("Cleared", ""));
                }
            }
        } catch (Exception e) {
            // TODO: This may no longer be relevant

            // https://code.google.com/p/ankidroid/issues/detail?id=1746
            // https://code.google.com/p/ankidroid/issues/detail?id=1820
            // Some devices or external applications make the clipboard throw exceptions. If this happens, we
            // must disable it or AnkiDroid will crash if it tries to use it.
            Timber.e("Clipboard error. Disabling text selection setting.");
            mDisableClipboard = true;
        }
    }


    /**
     * Returns the text stored in the clipboard or the empty string if the clipboard is empty or contains something that
     * cannot be convered to text.
     *
     * @return the text in clipboard or the empty string.
     */
    private CharSequence clipboardGetText() {
        CharSequence text = ClipboardUtil.getText(mClipboard);
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
            performReload();
        }

        if (requestCode == EDIT_CURRENT_CARD) {
            if (resultCode == RESULT_OK) {
                // content of note was changed so update the note and current card
                Timber.i("AbstractFlashcardViewer:: Saving card...");
                TaskManager.launchCollectionTask(
                        new CollectionTask.UpdateNote(sEditorCard, true, canAccessScheduler()),
                        mUpdateCardHandler);
                onEditedNoteChanged();
            } else if (resultCode == RESULT_CANCELED && !(data!=null && data.hasExtra("reloadRequired"))) {
                // nothing was changed by the note editor so just redraw the card
                redrawCard();
            }
        } else if (requestCode == DECK_OPTIONS && resultCode == RESULT_OK) {
            performReload();
        }
        if (!mDisableClipboard) {
            clearClipboard();
        }
    }

    /**
     * Whether the class should use collection.getSched() when performing tasks.
     * The aim of this method is to completely distinguish FlashcardViewer from Reviewer
     *
     * This is partially implemented, the end goal is that the FlashcardViewer will not have any coupling to getSched
     *
     * Currently, this is used for note edits - in a reviewing context, this should show the next card.
     * In a previewing context, the card should not change.
     */
    protected boolean canAccessScheduler() {
        return false;
    }


    protected void onEditedNoteChanged() {

    }


    /** An action which may invalidate the current list of cards has been performed */
    protected abstract void performReload();


    // ----------------------------------------------------------------------------
    // CUSTOM METHODS
    // ----------------------------------------------------------------------------

    // Get the did of the parent deck (ignoring any subdecks)
    protected long getParentDid() {
        return getCol().getDecks().selected();
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


    public GestureDetector getGestureDetector() {
        return mGestureDetector;
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
            mCardTimer.setBase(getElapsedRealTime() - mCurrentCard.timeTaken());
            // Don't start the timer if we have already reached the time limit or it will tick over
            if ((getElapsedRealTime() - mCardTimer.getBase()) < mCurrentCard.timeLimit()) {
                mCardTimer.start();
            }
        }
    }


    protected void undo() {
        if (isUndoAvailable()) {
            TaskManager.launchCollectionTask(new CollectionTask.Undo(), mAnswerCardHandler(false));
        }
    }


    private void finishNoStorageAvailable() {
        AbstractFlashcardViewer.this.setResult(DeckPicker.RESULT_MEDIA_EJECTED);
        finishWithoutAnimation();
    }


    protected void editCard() {
        if (mCurrentCard == null) {
            // This should never occurs. It means the review button was pressed while there is no more card in the reviewer.
            return;
        }
        Intent editCard = new Intent(AbstractFlashcardViewer.this, NoteEditor.class);
        editCard.putExtra(NoteEditor.EXTRA_CALLER, NoteEditor.CALLER_REVIEWER);
        sEditorCard = mCurrentCard;
        startActivityForResultWithAnimation(editCard, EDIT_CURRENT_CARD, START);
    }


    protected void generateQuestionSoundList() {
        mSoundPlayer.addSounds(mBaseUrl, mCurrentCard.qSimple(), SoundSide.QUESTION);
    }


    protected void lookUpOrSelectText() {
        if (clipboardHasText()) {
            Timber.d("Clipboard has text = %b", clipboardHasText());
            lookUp();
        } else {
            selectAndCopyText();
        }
    }


    private void lookUp() {
        mLookUpIcon.setVisibility(View.GONE);
        mIsSelecting = false;
        if (Lookup.lookUp(clipboardGetText().toString())) {
            clearClipboard();
        }
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
            clearClipboard();
        }
    }


    protected void showDeleteNoteDialog() {
        Resources res = getResources();
        new MaterialDialog.Builder(this)
                .title(res.getString(R.string.delete_card_title))
                .iconAttr(R.attr.dialogErrorIcon)
                .content(res.getString(R.string.delete_note_message,
                        Utils.stripHTML(mCurrentCard.q(true))))
                .positiveText(R.string.dialog_positive_delete)
                .negativeText(R.string.dialog_cancel)
                .onPositive((dialog, which) -> {
                    Timber.i("AbstractFlashcardViewer:: OK button pressed to delete note %d", mCurrentCard.getNid());
                    mSoundPlayer.stopSounds();
                    dismiss(new CollectionTask.DeleteNote(mCurrentCard));
                })
                .build().show();
    }


    private int getRecommendedEase(boolean easy) {
        try {
            switch (getAnswerButtonCount()) {
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


    protected void answerCard(@Consts.BUTTON_TYPE int ease) {
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
        mTimerHandler.removeCallbacks(mRemoveChosenAnswerText);
        mTimerHandler.postDelayed(mRemoveChosenAnswerText, sShowChosenAnswerLength);
        mSoundPlayer.stopSounds();
        mCurrentEase = ease;

        TaskManager.launchCollectionTask(new CollectionTask.AnswerAndGetCard(mCurrentCard, mCurrentEase), mAnswerCardHandler(true));
    }


    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            // assign correct gesture code
            int gesture = COMMAND_NOTHING;

            switch (event.getKeyCode()) {
                case KeyEvent.KEYCODE_VOLUME_UP:
                    gesture = mGestureVolumeUp;
                    break;
                case KeyEvent.KEYCODE_VOLUME_DOWN:
                    gesture = mGestureVolumeDown;
                    break;
            }

            // Execute gesture's command, but only consume event if action is assigned. We want the volume buttons to work normally otherwise.
            if (gesture != COMMAND_NOTHING) {
                executeCommand(gesture);
                return true;
            }
        }

        return super.dispatchKeyEvent(event);
    }


    // Set the content view to the one provided and initialize accessors.
    protected void initLayout() {
        FrameLayout mCardContainer = findViewById(R.id.flashcard_frame);

        mTopBarLayout = findViewById(R.id.top_bar);

        ImageView mark = mTopBarLayout.findViewById(R.id.mark_icon);
        ImageView flag = mTopBarLayout.findViewById(R.id.flag_icon);
        mCardMarker = new CardMarker(mark, flag);

        mCardFrame = findViewById(R.id.flashcard);
        mCardFrameParent = (ViewGroup) mCardFrame.getParent();
        mTouchLayer = findViewById(R.id.touch_layer);
        mTouchLayer.setOnTouchListener(mGestureListener);
        if (!mDisableClipboard) {
            mClipboard = (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        }
        mCardFrame.removeAllViews();

        // Initialize swipe
        mGestureDetectorImpl = mLinkOverridesTouchGesture ? new LinkDetectingGestureDetector() : new MyGestureDetector();
        mGestureDetector = new GestureDetector(this, mGestureDetectorImpl);

        mEaseButtonsLayout = findViewById(R.id.ease_buttons);

        mEase1 = findViewById(R.id.ease1);
        mEase1Layout = findViewById(R.id.flashcard_layout_ease1);
        mEase1Layout.setOnClickListener((View view) -> mEaseHandler.onClick(view));
        mEase1Layout.setOnTouchListener((View view, MotionEvent event) -> mEaseHandler.onTouch(view, event));

        mEase2 = findViewById(R.id.ease2);
        mEase2Layout = findViewById(R.id.flashcard_layout_ease2);
        mEase2Layout.setOnClickListener((View view) -> mEaseHandler.onClick(view));
        mEase2Layout.setOnTouchListener((View view, MotionEvent event) -> mEaseHandler.onTouch(view, event));

        mEase3 = findViewById(R.id.ease3);
        mEase3Layout = findViewById(R.id.flashcard_layout_ease3);
        mEase3Layout.setOnClickListener((View view) -> mEaseHandler.onClick(view));
        mEase3Layout.setOnTouchListener((View view, MotionEvent event) -> mEaseHandler.onTouch(view, event));

        mEase4 = findViewById(R.id.ease4);
        mEase4Layout = findViewById(R.id.flashcard_layout_ease4);
        mEase4Layout.setOnClickListener((View view) -> mEaseHandler.onClick(view));
        mEase4Layout.setOnTouchListener((View view, MotionEvent event) -> mEaseHandler.onTouch(view, event));

        mNext1 = findViewById(R.id.nextTime1);
        mNext2 = findViewById(R.id.nextTime2);
        mNext3 = findViewById(R.id.nextTime3);
        mNext4 = findViewById(R.id.nextTime4);

        if (!mShowNextReviewTime) {
            mNext1.setVisibility(View.GONE);
            mNext2.setVisibility(View.GONE);
            mNext3.setVisibility(View.GONE);
            mNext4.setVisibility(View.GONE);
        }

        Button mFlipCard = findViewById(R.id.flip_card);
        mFlipCardLayout = findViewById(R.id.flashcard_layout_flip);
        mFlipCardLayout.setOnClickListener(mFlipCardListener);

        if (animationEnabled()) {
            mFlipCard.setBackgroundResource(Themes.getResFromAttr(this, R.attr.hardButtonRippleRef));
        }

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

        mPreviewButtonsLayout = findViewById(R.id.preview_buttons_layout);
        mPreviewPrevCard = findViewById(R.id.preview_previous_flashcard);
        mPreviewNextCard = findViewById(R.id.preview_next_flashcard);
        mPreviewToggleAnswerText = findViewById(R.id.preview_flip_flashcard);

        mCardTimer = findViewById(R.id.card_time);

        mChosenAnswer = findViewById(R.id.choosen_answer);

        mAnswerField = findViewById(R.id.answer_field);

        mLookUpIcon = findViewById(R.id.lookup_button);
        mLookUpIcon.setVisibility(View.GONE);
        mLookUpIcon.setOnClickListener(arg0 -> {
            Timber.i("AbstractFlashcardViewer:: Lookup button pressed");
            if (clipboardHasText()) {
                lookUp();
            }
        });
        initControls();

        // Position answer buttons
        String answerButtonsPosition = AnkiDroidApp.getSharedPrefs(this).getString(
                getString(R.string.answer_buttons_position_preference),
                "bottom"
        );
        LinearLayout answerArea = findViewById(R.id.bottom_area_layout);
        RelativeLayout.LayoutParams answerAreaParams = (RelativeLayout.LayoutParams) answerArea.getLayoutParams();
        RelativeLayout.LayoutParams cardContainerParams = (RelativeLayout.LayoutParams) mCardContainer.getLayoutParams();

        switch (answerButtonsPosition) {
            case "top":
                cardContainerParams.addRule(RelativeLayout.BELOW, R.id.bottom_area_layout);
                answerAreaParams.addRule(RelativeLayout.BELOW, R.id.mic_tool_bar_layer);
                answerArea.removeView(mAnswerField);
                answerArea.addView(mAnswerField, 1);
                break;
            case "bottom":
                cardContainerParams.addRule(RelativeLayout.ABOVE, R.id.bottom_area_layout);
                cardContainerParams.addRule(RelativeLayout.BELOW, R.id.mic_tool_bar_layer);
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
    protected WebView createWebView() {
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

        webView.setWebViewClient(new CardViewerWebClient(mAssetLoader));
        // Set transparent color to prevent flashing white when night mode enabled
        webView.setBackgroundColor(Color.argb(1, 0, 0, 0));

        // Javascript interface for calling AnkiDroid functions in webview, see card.js
        webView.addJavascriptInterface(javaScriptFunction(), "AnkiDroidJS");

        return webView;
    }

    /** If a card is displaying the question, flip it, otherwise answer it */
    private void flipOrAnswerCard(int cardOrdinal) {
        if (!sDisplayAnswer) {
            displayCardAnswer();
            return;
        }
        performClickWithVisualFeedback(cardOrdinal);
    }


    private boolean webViewRendererLastCrashedOnCard(long cardId) {
        return mLastCrashingCardId != null && mLastCrashingCardId == cardId;
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
        processCardAction(cardWebView -> cardWebView.loadUrl(url));
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
        mFlipCardLayout.setClickable(false);
        mEaseButtonsLayout.setVisibility(View.VISIBLE);

        Runnable after = () -> mFlipCardLayout.setVisibility(View.GONE);

        // hide "Show Answer" button
        if (animationDisabled()) {
            after.run();
        } else {
            mFlipCardLayout.setAlpha(1);
            mFlipCardLayout.animate().alpha(0).setDuration(mShortAnimDuration).withEndAction(after);
        }
    }


    protected void hideEaseButtons() {
        Runnable after = this::actualHideEaseButtons;

        boolean easeButtonsVisible = mEaseButtonsLayout.getVisibility() == View.VISIBLE;
        mFlipCardLayout.setClickable(true);
        mFlipCardLayout.setVisibility(View.VISIBLE);

        if (animationDisabled() || !easeButtonsVisible) {
            after.run();
        } else {
            mFlipCardLayout.setAlpha(0);
            mFlipCardLayout.animate().alpha(1).setDuration(mShortAnimDuration).withEndAction(after);
        }

        focusAnswerCompletionField();
    }


    private void actualHideEaseButtons() {
        mEaseButtonsLayout.setVisibility(View.GONE);
        mEase1Layout.setVisibility(View.GONE);
        mEase2Layout.setVisibility(View.GONE);
        mEase3Layout.setVisibility(View.GONE);
        mEase4Layout.setVisibility(View.GONE);
        mNext1.setText("");
        mNext2.setText("");
        mNext3.setText("");
        mNext4.setText("");
    }


    /**
     * Focuses the appropriate field for an answer
     * And allows keyboard shortcuts to go to the default handlers.
     * */
    private void focusAnswerCompletionField() {
        // This does not handle mUseInputTag (the WebView contains an input field with a typable answer).
        // In this case, the user can use touch to focus the field if necessary.
        if (typeAnswer() && mFocusTypeAnswer) {
            mAnswerField.focusWithKeyboard();
        } else {
            mFlipCardLayout.requestFocus();
        }
    }


    protected void switchTopBarVisibility(int visible) {
        if (mShowTimer) {
            mCardTimer.setVisibility(visible);
        }
        mChosenAnswer.setVisibility(visible);
    }


    protected void initControls() {
        mCardFrame.setVisibility(View.VISIBLE);
        mChosenAnswer.setVisibility(View.VISIBLE);
        mFlipCardLayout.setVisibility(View.VISIBLE);

        mAnswerField.setVisibility(typeAnswer() ? View.VISIBLE : View.GONE);
        mAnswerField.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                displayCardAnswer();
                return true;
            }
            return false;
        });
        mAnswerField.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_UP &&
                    (keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER)) {
                displayCardAnswer();
                return true;
            }
            return false;
        });
    }


    protected SharedPreferences restorePreferences() {
        SharedPreferences preferences = AnkiDroidApp.getSharedPrefs(getBaseContext());

        mUseInputTag = preferences.getBoolean("useInputTag", false);
        mDoNotUseCodeFormatting = preferences.getBoolean("noCodeFormatting", false);
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
        mPrefShowTopbar = preferences.getBoolean("showTopbar", true);
        mFocusTypeAnswer = preferences.getBoolean("autoFocusTypeInAnswer", false);

        mGesturesEnabled = AnkiDroidApp.initiateGestures(preferences);
        mLinkOverridesTouchGesture = preferences.getBoolean("linkOverridesTouchGesture", false);
        if (mGesturesEnabled) {
            mGestureSwipeUp = Integer.parseInt(preferences.getString("gestureSwipeUp", "9"));
            mGestureSwipeDown = Integer.parseInt(preferences.getString("gestureSwipeDown", "0"));
            mGestureSwipeLeft = Integer.parseInt(preferences.getString("gestureSwipeLeft", "8"));
            mGestureSwipeRight = Integer.parseInt(preferences.getString("gestureSwipeRight", "17"));
            mGestureDoubleTap = Integer.parseInt(preferences.getString("gestureDoubleTap", "7"));
            mGestureTapProcessor.init(preferences);
            mGestureLongclick = Integer.parseInt(preferences.getString("gestureLongclick", "11"));
            mGestureVolumeUp = Integer.parseInt(preferences.getString("gestureVolumeUp", "0"));
            mGestureVolumeDown = Integer.parseInt(preferences.getString("gestureVolumeDown", "0"));
        }

        if (preferences.getBoolean("keepScreenOn", false)) {
            this.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }

        return preferences;
    }


    protected void restoreCollectionPreferences() {

        // These are preferences we pull out of the collection instead of SharedPreferences
        try {
            mShowNextReviewTime = getCol().getConf().getBoolean("estTimes");

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
            Timber.w(npe);
            Intent deckPicker = new Intent(this, DeckPicker.class);
            deckPicker.putExtra("collectionLoadError", true); // don't currently do anything with this
            deckPicker.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivityWithAnimation(deckPicker, START);
        }
    }


    private void setInterface() {
        if (mCurrentCard == null) {
            return;
        }
        recreateWebView();
    }

    protected void recreateWebView() {
        if (mCardWebView == null) {
            mCardWebView = createWebView();
            WebViewDebugging.initializeDebugging(AnkiDroidApp.getSharedPrefs(this));
            mCardFrame.addView(mCardWebView);
            mGestureDetectorImpl.onWebViewCreated(mCardWebView);
        }
        if (mCardWebView.getVisibility() != View.VISIBLE) {
            mCardWebView.setVisibility(View.VISIBLE);
        }
    }


    private void updateForNewCard() {
        updateActionBar();

        // Clean answer field
        if (typeAnswer()) {
            mAnswerField.setText("");
        }

        if (mPrefWhiteboard && mWhiteboard != null) {
            mWhiteboard.clear();
        }
    }


    protected void updateActionBar() {
        updateDeckName();
    }

    protected void updateDeckName() {
        if (mCurrentCard == null) return;
        ActionBar actionBar = getSupportActionBar();

        if (actionBar != null) {
            String title = Decks.basename(getCol().getDecks().get(mCurrentCard.getDid()).getString("name"));
            actionBar.setTitle(title);
        }

        if (!mPrefShowTopbar) {
            mTopBarLayout.setVisibility(View.GONE);
        }
    }

    /*
     * Handler for the delay in auto showing question and/or answer One toggle for both question and answer, could set
     * longer delay for auto next question
     */
    protected final Handler mTimeoutHandler = new Handler();

    protected final Runnable mShowQuestionTask = new Runnable() {
        @Override
        public void run() {
            // Assume hitting the "Again" button when auto next question
            if (mEase1Layout.isEnabled() && mEase1Layout.getVisibility() == View.VISIBLE) {
                mEase1Layout.performClick();
            }
        }
    };

    protected final Runnable mShowAnswerTask = new Runnable() {
        @Override
        public void run() {
            if (mFlipCardLayout.isEnabled() && mFlipCardLayout.getVisibility() == View.VISIBLE) {
                mFlipCardLayout.performClick();
            }
        }
    };

    class ReadTextListener implements ReadText.ReadTextListener {
        public void onDone() {
            if(!mUseTimer) {
                return;
            }
            if (ReadText.getmQuestionAnswer() == SoundSide.QUESTION) {
                long delay = mWaitAnswerSecond * 1000;
                if (delay > 0) {
                    mTimeoutHandler.postDelayed(mShowAnswerTask, delay);
                }
            } else if (ReadText.getmQuestionAnswer() == SoundSide.ANSWER) {
                long delay = mWaitQuestionSecond * 1000;
                if (delay > 0) {
                    mTimeoutHandler.postDelayed(mShowQuestionTask, delay);
                }
            }
        }
    }

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

        mCardTimer.setBase(getElapsedRealTime());
        mCardTimer.start();

        // Stop and highlight the timer if it reaches the time limit.
        getTheme().resolveAttribute(R.attr.maxTimerColor, typedValue, true);
        final int limit = mCurrentCard.timeLimit();
        mCardTimer.setOnChronometerTickListener(chronometer -> {
            long elapsed = getElapsedRealTime() - chronometer.getBase();
            if (elapsed >= limit) {
                chronometer.setTextColor(typedValue.data);
                chronometer.stop();
            }
        });
    }

    protected void displayCardQuestion() {
        displayCardQuestion(false);

        // js api initialisation / reset
        jsApiInit();
    }

    /** String, as it will be displayed in the web viewer. Sound/video removed, image escaped...
     Or warning if required*/
    private String displayString(boolean reload) {
        if (mCurrentCard.isEmpty()) {
            return getResources().getString(R.string.empty_card_warning);
        } else {
            String question = mCurrentCard.q(reload);
            question = getCol().getMedia().escapeImages(question);
            question = typeAnsQuestionFilter(question);

            Timber.v("question: '%s'", question);

            return CardAppearance.enrichWithQADiv(question, false);
        }
    }

    protected void displayCardQuestion(boolean reload) {
        Timber.d("displayCardQuestion()");
        sDisplayAnswer = false;

        setInterface();

        String displayString = displayString(reload);
        if (!mCurrentCard.isEmpty() && typeAnswer()) {
            // Show text entry based on if the user wants to write the answer
            mAnswerField.setVisibility(View.VISIBLE);
        } else {
            mAnswerField.setVisibility(View.GONE);
        }

        //if (mSpeakText) {
        // ReadText.setLanguageInformation(Model.getModel(DeckManager.getMainDeck(),
        // mCurrentCard.getCardModelId(), false).getId(), mCurrentCard.getCardModelId());
        //}

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
                if (!mSpeakText) {
                    mTimeoutHandler.postDelayed(mShowAnswerTask, delay);
                }
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
        // #7294 Required in case the animation end action does not fire:
        actualHideEaseButtons();
        Timber.d("displayCardAnswer()");
        mMissingImageHandler.onCardSideChange();

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
                if (!mSpeakText) {
                    mTimeoutHandler.postDelayed(mShowQuestionTask, delay);
                }
            }
        }
    }


    /** Scroll the currently shown flashcard vertically
     *
     * @param dy amount to be scrolled
     */
    public void scrollCurrentCardBy(int dy) {
        processCardAction(cardWebView -> {
            if (dy != 0 && cardWebView.canScrollVertically(dy)) {
                cardWebView.scrollBy(0, dy);
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
        processCardAction(cardWebView -> cardWebView.dispatchTouchEvent(eDown));

        MotionEvent eUp = MotionEvent.obtain(eDown.getDownTime(),
                SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, x, y,
                1, 1, 0, 1, 1, 0, 0);
        processCardAction(cardWebView -> cardWebView.dispatchTouchEvent(eUp));

    }


    /**
     * getAnswerFormat returns the answer part of this card's template as entered by user, without any parsing
     */
    public String getAnswerFormat() {
        Model model = mCurrentCard.model();
        JSONObject template;
        if (model.isStd()) {
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
            mSoundPlayer.addSounds(mBaseUrl, answerSoundSource, SoundSide.ANSWER);
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
            processCardAction(cardWebView -> cardWebView.getSettings().setDefaultFontSize(calculateDynamicFontSize(newContent)));
        }

        if (sDisplayAnswer) {
            addAnswerSounds(newContent);
        } else {
            // reset sounds each time first side of card is displayed, which may happen repeatedly without ever
            // leaving the card (such as when edited)
            mSoundPlayer.resetSounds();
            mAnswerSoundsAdded = false;
            mSoundPlayer.addSounds(mBaseUrl, newContent, SoundSide.QUESTION);
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

        if (MathJax.textContainsMathjax(content)) {
            cardClass += " mathjax-needs-to-render";
        }

        if (isInNightMode()) {
            if (!mCardAppearance.hasUserDefinedNightMode(mCurrentCard)) {
                content = HtmlColors.invertColors(content);
            }
        }

        mCardContent = mCardTemplate.render(content, style, cardClass);
        Timber.d("base url = %s", mBaseUrl);

        if (AnkiDroidApp.getSharedPrefs(this).getBoolean("html_javascript_debugging", false)) {
            try {
                try (FileOutputStream f = new FileOutputStream(new File(CollectionHelper.getCurrentAnkiDroidDirectory(this),
                        "card.html"))) {
                    f.write(mCardContent.getBytes());
                }
            } catch (IOException e) {
                Timber.d(e, "failed to save card");
            }
        }
        fillFlashcard();

        playSounds(false); // Play sounds if appropriate
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
                    playSounds(SoundSide.QUESTION_AND_ANSWER);
                } else if (sDisplayAnswer) {
                    playSounds(SoundSide.ANSWER);
                    if (mUseTimer) {
                        mUseTimerDynamicMS = mSoundPlayer.getSoundsLength(SoundSide.ANSWER);
                    }
                } else { // question is displayed
                    playSounds(SoundSide.QUESTION);
                    // If the user wants to show the answer automatically
                    if (mUseTimer) {
                        mUseTimerDynamicMS = mSoundPlayer.getSoundsLength(SoundSide.QUESTION_AND_ANSWER);
                    }
                }
            } else { // Text to speech is in effect here
                // If the question is displayed or if the question should be replayed, read the question
                if (mTtsInitialized) {
                    if (!sDisplayAnswer || doAudioReplay && replayQuestion) {
                        readCardText(mCurrentCard, SoundSide.QUESTION);
                    }
                    if (sDisplayAnswer) {
                        readCardText(mCurrentCard, SoundSide.ANSWER);
                    }
                } else {
                    mReplayOnTtsInit = true;
                }
            }
        }
    }

    private void playSounds(SoundSide questionAndAnswer) {
        mSoundPlayer.playSounds(questionAndAnswer, getSoundErrorListener());
    }

    private Sound.OnErrorListener getSoundErrorListener() {
        return (mp, what, extra, path) -> {
            Timber.w("Media Error: (%d, %d). Calling OnCompletionListener", what, extra);
            try {
                File file = new File(path);
                if (!file.exists()) {
                    mMissingImageHandler.processMissingSound(file, this::displayCouldNotFindMediaSnackbar);
                }
            } catch (Exception e) {
                Timber.w(e);
                return false;
            }

            return false;
        };
    }


    /**
     * Reads the text (using TTS) for the given side of a card.
     *
     * @param card     The card to play TTS for
     * @param cardSide The side of the current card to play TTS for
     */
    private void readCardText(final Card card, final SoundSide cardSide) {
        final String cardSideContent;
        if (SoundSide.QUESTION == cardSide) {
            cardSideContent = card.q(true);
        } else if (SoundSide.ANSWER == cardSide) {
            cardSideContent = card.getPureAnswer();
        } else {
            Timber.w("Unrecognised cardSide");
            return;
        }
        String clozeReplacement = this.getString(R.string.reviewer_tts_cloze_spoken_replacement);
        ReadText.readCardSide(cardSide, cardSideContent, getDeckIdForCard(card), card.getOrd(), clozeReplacement);
    }

    /**
     * Shows the dialogue for selecting TTS for the current card and cardside.
     */
    protected void showSelectTtsDialogue() {
        if (mTtsInitialized) {
            if (!sDisplayAnswer) {
                ReadText.selectTts(getTextForTts(mCurrentCard.q(true)), getDeckIdForCard(mCurrentCard), mCurrentCard.getOrd(),
                        SoundSide.QUESTION);
            } else {
                ReadText.selectTts(getTextForTts(mCurrentCard.getPureAnswer()), getDeckIdForCard(mCurrentCard),
                        mCurrentCard.getOrd(), SoundSide.ANSWER);
            }
        }
    }


    private String getTextForTts(String text) {
        String clozeReplacement = this.getString(R.string.reviewer_tts_cloze_spoken_replacement);
        String clozeReplaced = text.replace(TemplateFilters.CLOZE_DELETION_REPLACEMENT, clozeReplacement);
        return Utils.stripHTML(clozeReplaced);
    }


    /**
     * Returns the configuration for the current {@link Card}.
     *
     * @return The configuration for the current {@link Card}
     */
    private DeckConfig getConfigForCurrentCard() {
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
        final String cardContent = mCardContent;
        processCardAction(cardWebView -> loadContentIntoCard(cardWebView, cardContent));
        mGestureDetectorImpl.onFillFlashcard();
        if (mShowTimer && mCardTimer.getVisibility() == View.INVISIBLE) {
            switchTopBarVisibility(View.VISIBLE);
        }
        if (!sDisplayAnswer) {
            updateForNewCard();
        }
    }


    private void loadContentIntoCard(WebView card, String content) {
        if (card != null) {
            card.getSettings().setMediaPlaybackRequiresUserGesture(!getConfigForCurrentCard().optBoolean("autoplay"));
            card.loadDataWithBaseURL(mViewerUrl, content, "text/html", "utf-8", null);
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
        mControlBlocked = ControlBlock.UNBLOCKED;
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


    /**
     * @param quick Whether we expect the control to come back quickly
     */
    @VisibleForTesting
    protected void blockControls(boolean quick) {
        if (quick) {
            mControlBlocked = ControlBlock.QUICK;
        } else {
            mControlBlocked = ControlBlock.SLOW;
        }
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
        if (isControlBlocked() && which != COMMAND_EXIT) {
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
                if (!isUndoAvailable()) {
                    return false;
                }
                undo();
                return true;
            case COMMAND_EDIT:
                editCard();
                return true;
            case COMMAND_CARD_INFO:
                openCardInfo();
                return true;
            case COMMAND_TAG:
                showTagsDialog();
                return true;
            case COMMAND_MARK:
                onMark(mCurrentCard);
                return true;
            case COMMAND_LOOKUP:
                lookUpOrSelectText();
                return true;
            case COMMAND_BURY_CARD:
                dismiss(new CollectionTask.BuryCard(mCurrentCard));
                return true;
            case COMMAND_BURY_NOTE:
                dismiss(new CollectionTask.BuryNote(mCurrentCard));
                return true;
            case COMMAND_SUSPEND_CARD:
                dismiss(new CollectionTask.SuspendCard(mCurrentCard));
                return true;
            case COMMAND_SUSPEND_NOTE:
                dismiss(new CollectionTask.SuspendNote(mCurrentCard));
                return true;
            case COMMAND_DELETE:
                showDeleteNoteDialog();
                return true;
            case COMMAND_PLAY_MEDIA:
                playSounds(true);
                return true;
            case COMMAND_TOGGLE_FLAG_RED:
                toggleFlag(FLAG_RED);
                return true;
            case COMMAND_TOGGLE_FLAG_ORANGE:
                toggleFlag(FLAG_ORANGE);
                return true;
            case COMMAND_TOGGLE_FLAG_GREEN:
                toggleFlag(FLAG_GREEN);
                return true;
            case COMMAND_TOGGLE_FLAG_BLUE:
                toggleFlag(FLAG_BLUE);
                return true;
            case COMMAND_UNSET_FLAG:
                onFlag(mCurrentCard, FLAG_NONE);
                return true;
            case COMMAND_ANSWER_FIRST_BUTTON:
                return answerCardIfVisible(Consts.BUTTON_ONE);
            case COMMAND_ANSWER_SECOND_BUTTON:
                return answerCardIfVisible(Consts.BUTTON_TWO);
            case COMMAND_ANSWER_THIRD_BUTTON:
                return answerCardIfVisible(Consts.BUTTON_THREE);
            case COMMAND_ANSWER_FOURTH_BUTTON:
                return answerCardIfVisible(Consts.BUTTON_FOUR);
            case COMMAND_ANSWER_RECOMMENDED:
                return answerCardIfVisible(getRecommendedEase(false));
            case COMMAND_PAGE_UP:
                onPageUp();
                return true;
            case COMMAND_PAGE_DOWN:
                onPageDown();
                return true;
            case COMMAND_ABORT_AND_SYNC:
                abortAndSync();
                return true;
            case COMMAND_RECORD_VOICE:
                recordVoice();
                return true;
            case COMMAND_REPLAY_VOICE:
                replayVoice();
                return true;
            case COMMAND_TOGGLE_WHITEBOARD:
                toggleWhiteboard();
                return true;
            default:
                Timber.w("Unknown command requested: %s", which);
                return false;
        }
    }


    protected void replayVoice() {
        // intentionally blank
    }


    protected void recordVoice() {
        // intentionally blank
    }


    protected void toggleWhiteboard() {
        // intentionally blank
    }

    private void abortAndSync() {
        closeReviewer(RESULT_ABORT_AND_SYNC, true);
    }


    protected void openCardInfo() {
        if (mCurrentCard == null) {
            UIUtils.showThemedToast(this, getString(R.string.multimedia_editor_something_wrong), true);
            return;
        }
        Intent intent = new Intent(this, CardInfo.class);
        intent.putExtra("cardId", mCurrentCard.getId());
        startActivityWithAnimation(intent, FADE);
    }


    /** Displays a snackbar which does not obscure the answer buttons */
    protected void showSnackbar(String mainText, @StringRes int buttonText, OnClickListener onClickListener) {
        // BUG: Moving from full screen to non-full screen obscures the buttons

        Snackbar sb = UIUtils.getSnackbar(this, mainText, Snackbar.LENGTH_LONG, buttonText, onClickListener, mCardWebView, null);

        View easeButtons = findViewById(R.id.answer_options_layout);
        View previewButtons = findViewById(R.id.preview_buttons_layout);

        View upperView = previewButtons != null && previewButtons.getVisibility() != View.GONE ? previewButtons : easeButtons;

        // we need to check for View.GONE as setting the anchor does not seem to respect this property
        // (there's a gap even if the view is invisible)

        if (upperView != null && upperView.getVisibility() != View.GONE) {
            View sbView = sb.getView();
            CoordinatorLayout.LayoutParams layoutParams = (CoordinatorLayout.LayoutParams) sbView.getLayoutParams();
            layoutParams.setAnchorId(upperView.getId());
            layoutParams.anchorGravity = Gravity.TOP;
            layoutParams.gravity = Gravity.TOP;
            sbView.setLayoutParams(layoutParams);
        }

        sb.show();
    }


    private void onPageUp() {
        //pageUp performs a half scroll, we want a full page
        processCardAction(cardWebView -> {
            cardWebView.pageUp(false);
            cardWebView.pageUp(false);
        });
    }

    private void onPageDown() {
        processCardAction(cardWebView -> {
            cardWebView.pageDown(false);
            cardWebView.pageDown(false);
        });
    }


    private void toggleFlag(@FlagDef int flag) {
        if (mCurrentCard.userFlag() == flag) {
            Timber.i("Toggle flag: unsetting flag");
            onFlag(mCurrentCard, FLAG_NONE);
        } else {
            Timber.i("Toggle flag: Setting flag to %d", flag);
            onFlag(mCurrentCard, flag);
        }
    }

    private boolean answerCardIfVisible(@Consts.BUTTON_TYPE int ease) {
        if (!sDisplayAnswer) {
            return false;
        }
        performClickWithVisualFeedback(ease);
        return true;
    }

    protected void performClickWithVisualFeedback(int ease) {
        // Delay could potentially be lower - testing with 20 left a visible "click"
        switch (ease) {
            case EASE_1:
                performClickWithVisualFeedback(mEase1Layout);
                break;
            case EASE_2:
                performClickWithVisualFeedback(mEase2Layout);
                break;
            case EASE_3:
                performClickWithVisualFeedback(mEase3Layout);
                break;
            case EASE_4:
                performClickWithVisualFeedback(mEase4Layout);
                break;
        }
    }


    private void performClickWithVisualFeedback(LinearLayout easeLayout) {
        easeLayout.requestFocus();
        easeLayout.performClick();
    }


    @VisibleForTesting
    protected boolean isUndoAvailable() {
        return getCol().undoAvailable();
    }

    // ----------------------------------------------------------------------------
    // INNER CLASSES
    // ----------------------------------------------------------------------------

    /**
     * Provides a hook for calling "alert" from javascript. Useful for debugging your javascript.
     */
    public static final class AnkiDroidWebChromeClient extends WebChromeClient {
        @Override
        public boolean onJsAlert(WebView view, String url, String message, JsResult result) {
            Timber.i("AbstractFlashcardViewer:: onJsAlert: %s", message);
            result.confirm();
            return true;
        }
    }


    protected void closeReviewer(int result, boolean saveDeck) {
        // Stop the mic recording if still pending
        if (mMicToolBar != null) {
            mMicToolBar.notifyStopRecord();
        }
        // Remove the temporary audio file
        if (mTempAudioPath != null) {
            File tempAudioPathToDelete = new File(mTempAudioPath);
            if (tempAudioPathToDelete.exists()) {
                tempAudioPathToDelete.delete();
            }
        }

        mTimeoutHandler.removeCallbacks(mShowAnswerTask);
        mTimeoutHandler.removeCallbacks(mShowQuestionTask);
        mTimerHandler.removeCallbacks(mRemoveChosenAnswerText);
        mLongClickHandler.removeCallbacks(mLongClickTestRunnable);
        mLongClickHandler.removeCallbacks(mStartLongClickAction);

        AbstractFlashcardViewer.this.setResult(result);

        if (saveDeck) {
            UIUtils.saveCollectionInBackground();
        }
        finishWithAnimation(END);
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
        public void loadDataWithBaseURL(@Nullable String baseUrl, String data, @Nullable String mimeType, @Nullable String encoding, @Nullable String historyUrl) {
            if (!AbstractFlashcardViewer.this.isDestroyed()) {
                super.loadDataWithBaseURL(baseUrl, data, mimeType, encoding, historyUrl);
            } else {
                Timber.w("Not loading card - Activity is in the process of being destroyed.");
            }
        }


        @Override
        protected void onScrollChanged(int horiz, int vert, int oldHoriz, int oldVert) {
            super.onScrollChanged(horiz, vert, oldHoriz, oldVert);
            if (Math.abs(horiz - oldHoriz) > Math.abs(vert - oldVert)) {
                mIsXScrolling = true;
                mScrollHandler.removeCallbacks(mScrollXRunnable);
                mScrollHandler.postDelayed(mScrollXRunnable, 300);
            } else {
                mIsYScrolling = true;
                mScrollHandler.removeCallbacks(mScrollYRunnable);
                mScrollHandler.postDelayed(mScrollYRunnable, 300);
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                ViewParent scrollParent = findScrollParent(this);
                if (scrollParent != null) {
                    scrollParent.requestDisallowInterceptTouchEvent(true);
                }
            }
            return super.onTouchEvent(event);
        }


        @Override
        protected void onOverScrolled(int scrollX, int scrollY, boolean clampedX, boolean clampedY) {
            if (clampedX) {
                ViewParent scrollParent = findScrollParent(this);
                if (scrollParent != null) {
                    scrollParent.requestDisallowInterceptTouchEvent(false);
                }
            }
            super.onOverScrolled(scrollX, scrollY, clampedX, clampedY);
        }


        private ViewParent findScrollParent(View current) {
            ViewParent parent = current.getParent();
            if (parent == null) {
                return null;
            }
            if (parent instanceof FullDraggableContainer) {
                return parent;
            } else if (parent instanceof View) {
                return findScrollParent((View) parent);
            }
            return null;
        }

        private final Handler mScrollHandler = new Handler();
        private final Runnable mScrollXRunnable = () -> mIsXScrolling = false;
        private final Runnable mScrollYRunnable = () -> mIsYScrolling = false;

    }

    class MyGestureDetector extends SimpleOnGestureListener {
        //Android design spec for the size of the status bar.
        private static final int NO_GESTURE_BORDER_DIP = 24;

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            Timber.d("onFling");

            //#5741 - A swipe from the top caused delayedHide to be triggered,
            //accepting a gesture and quickly disabling the status bar, which wasn't ideal.
            //it would be lovely to use e1.getEdgeFlags(), but alas, it doesn't work.
            if (isTouchingEdge(e1)) {
                Timber.d("ignoring edge fling");
                return false;
            }

            // Go back to immersive mode if the user had temporarily exited it (and then execute swipe gesture)
            AbstractFlashcardViewer.this.onFling();
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


        private boolean isTouchingEdge(MotionEvent e1) {
            int height = mTouchLayer.getHeight();
            int width = mTouchLayer.getWidth();
            float margin = NO_GESTURE_BORDER_DIP * getResources().getDisplayMetrics().density + 0.5f;
            return e1.getX() < margin || e1.getY() < margin || height - e1.getY() < margin || width - e1.getX() < margin;
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
                mLongClickHandler.removeCallbacks(mLongClickTestRunnable);
                mTouchStarted = false;
            }
            return false;
        }


        @Override
        public boolean onSingleTapConfirmed(MotionEvent e) {
            // Go back to immersive mode if the user had temporarily exited it (and ignore the tap gesture)
            if (onSingleTap()) {
                return true;
            }
            executeTouchCommand(e);
            return false;
        }


        protected void executeTouchCommand(@NonNull MotionEvent e) {
            if (mGesturesEnabled && !mIsSelecting) {
                int height = mTouchLayer.getHeight();
                int width = mTouchLayer.getWidth();
                float posX = e.getX();
                float posY = e.getY();

                int gesture = mGestureTapProcessor.getCommandFromTap(height, width, posX, posY);

                executeCommand(gesture);
            }
            mIsSelecting = false;
            showLookupButtonIfNeeded();
        }


        public void onWebViewCreated(@NonNull WebView webView) {
            //intentionally blank
        }

        public void onFillFlashcard() {
            //intentionally blank
        }

        public boolean eventCanBeSentToWebView(@NonNull MotionEvent event) {
            return true;
        }
    }


    protected boolean onSingleTap() {
        return false;
    }


    protected void onFling() {

    }


    /** #6141 - blocks clicking links from executing "touch" gestures.
     * COULD_BE_BETTER: Make base class static and move this out of the CardViewer */
    class LinkDetectingGestureDetector extends AbstractFlashcardViewer.MyGestureDetector {
        /** A list of events to process when listening to WebView touches  */
        private final HashSet<MotionEvent> mDesiredTouchEvents = new HashSet<>(2);
        /** A list of events we sent to the WebView (to block double-processing) */
        private final HashSet<MotionEvent> mDispatchedTouchEvents = new HashSet<>(2);

        @Override
        public void onFillFlashcard() {
            Timber.d("Removing pending touch events for gestures");
            mDesiredTouchEvents.clear();
            mDispatchedTouchEvents.clear();
        }

        @Override
        public boolean eventCanBeSentToWebView(@NonNull MotionEvent event) {
            //if we processed the event, we don't want to perform it again
            return !mDispatchedTouchEvents.remove(event);
        }


        @Override
        protected void executeTouchCommand(@NonNull MotionEvent downEvent) {
            downEvent.setAction(MotionEvent.ACTION_DOWN);
            MotionEvent upEvent = MotionEvent.obtainNoHistory(downEvent);
            upEvent.setAction(MotionEvent.ACTION_UP);

            //mark the events we want to process
            mDesiredTouchEvents.add(downEvent);
            mDesiredTouchEvents.add(upEvent);

            //mark the events to can guard against double-processing
            mDispatchedTouchEvents.add(downEvent);
            mDispatchedTouchEvents.add(upEvent);

            Timber.d("Dispatching touch events");
            processCardAction(cardWebView -> {
                cardWebView.dispatchTouchEvent(downEvent);
                cardWebView.dispatchTouchEvent(upEvent);
            });
        }


        @SuppressLint("ClickableViewAccessibility")
        @Override
        public void onWebViewCreated(@NonNull WebView webView) {
            Timber.d("Initializing WebView touch handler");
            webView.setOnTouchListener((webViewAsView, motionEvent) -> {
                if (!mDesiredTouchEvents.remove(motionEvent)) {
                    return false;
                }

                //We need an associated up event so the WebView doesn't keep a selection
                //But we don't want to handle this as a touch event.
                if (motionEvent.getAction() == MotionEvent.ACTION_UP) {
                    return true;
                }

                WebView cardWebView = (WebView) webViewAsView;
                HitTestResult result;
                try {
                    result = cardWebView.getHitTestResult();
                } catch (Exception e) {
                    Timber.w(e, "Cannot obtain HitTest result");
                    return true;
                }

                if (isLinkClick(result)) {
                    Timber.v("Detected link click - ignoring gesture dispatch");
                    return true;
                }

                Timber.v("Executing continuation for click type: %d", result == null ? -178 : result.getType());
                super.executeTouchCommand(motionEvent);
                return true;
            });
        }


        private boolean isLinkClick(HitTestResult result) {
            if (result == null) {
                return false;
            }
            int type = result.getType();
            return type == HitTestResult.SRC_ANCHOR_TYPE
                    || type == HitTestResult.SRC_IMAGE_ANCHOR_TYPE;
        }
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
            Matcher audioReferences = Sound.SOUND_PATTERN.matcher(frontSideFormat);
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


    protected @FlagDef int getFlagToDisplay() {
        return mCurrentCard.userFlag();
    }


    protected void onFlag(Card card, @FlagDef int flag) {
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

    protected void dismiss(CollectionTask.DismissNote dismiss) {
        blockControls(false);
        TaskManager.launchCollectionTask(dismiss, mDismissCardHandler);
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
        private WebViewAssetLoader mLoader;

        CardViewerWebClient(WebViewAssetLoader loader) {
            super();

            mLoader = loader;
        }

        @Override
        @TargetApi(Build.VERSION_CODES.N)
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            String url = request.getUrl().toString();
            Timber.d("Obtained URL from card: '%s'", url);
            return filterUrl(url);
        }


        @Nullable
        @Override
        @SuppressWarnings("deprecation") // required for lower APIs (I think)
        public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
            // response is null if nothing required
            if (isLoadedFromProtocolRelativeUrl(url)) {
                mMissingImageHandler.processInefficientImage(AbstractFlashcardViewer.this::displayMediaUpgradeRequiredSnackbar);
            }

            if (isLoadedFromHttpUrl(url)) {
                //shouldInterceptRequest is not running on the UI thread.
                AbstractFlashcardViewer.this.runOnUiThread(() -> mDisplayMediaLoadedFromHttpWarningSnackbar.execOnceForReference(mCurrentCard));
            }

            return null;
        }


        @Override
        @TargetApi(Build.VERSION_CODES.N)
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            Uri url = request.getUrl();
            WebResourceResponse result = mLoader.shouldInterceptRequest(url);

            if (result != null) {
                return result;
            }

            if (!AdaptionUtil.hasWebBrowser(getBaseContext())) {
                String scheme = url.getScheme().trim();
                if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) {
                    String response = getResources().getString(R.string.no_outgoing_link_in_cardbrowser);
                    return new WebResourceResponse("text/html", "utf-8", new ByteArrayInputStream(response.getBytes()));
                }
            }

            if (isLoadedFromProtocolRelativeUrl(request.getUrl().toString())) {
                mMissingImageHandler.processInefficientImage(AbstractFlashcardViewer.this::displayMediaUpgradeRequiredSnackbar);
            }

            if (isLoadedFromHttpUrl(url)) {
                //shouldInterceptRequest is not running on the UI thread.
                AbstractFlashcardViewer.this.runOnUiThread(() -> mDisplayMediaLoadedFromHttpWarningSnackbar.execOnceForReference(mCurrentCard));
            }

            return null;
        }

        protected boolean isLoadedFromHttpUrl(String url) {
            return url.trim().toLowerCase().startsWith("http");
        }

        protected boolean isLoadedFromHttpUrl(Uri uri) {
            return uri.getScheme().equalsIgnoreCase("http");
        }

        protected boolean isLoadedFromProtocolRelativeUrl(String url) {
            // a URL provided as "//wikipedia.org" is currently transformed to file://wikipedia.org, we can catch this
            // because <img src="x.png"> maps to file:///.../x.png
            return url.startsWith("file://") && !url.startsWith("file:///");
        }

        @Override
        public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
            super.onReceivedError(view, request, error);
            mMissingImageHandler.processFailure(request, AbstractFlashcardViewer.this::displayCouldNotFindMediaSnackbar);
        }


        @Override
        public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse errorResponse) {
            super.onReceivedHttpError(view, request, errorResponse);
            mMissingImageHandler.processFailure(request, AbstractFlashcardViewer.this::displayCouldNotFindMediaSnackbar);
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
                msg.obj = url.replaceFirst("playsound:", "");
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
            // Show options menu from WebView
            if (url.startsWith("signal:anki_show_options_menu")) {
                if (isFullscreen()) {
                    openOptionsMenu();
                } else {
                    UIUtils.showThemedToast(AbstractFlashcardViewer.this, getString(R.string.ankidroid_turn_on_fullscreen_options_menu), true);
                }
                return true;
            }

            // Show Navigation Drawer from WebView
            if (url.startsWith("signal:anki_show_navigation_drawer")) {
                if (isFullscreen()) {
                    AbstractFlashcardViewer.this.onNavigationPressed();
                } else {
                    UIUtils.showThemedToast(AbstractFlashcardViewer.this, getString(R.string.ankidroid_turn_on_fullscreen_nav_drawer), true);
                }
                return true;
            }

            // card.html reload
            if (url.startsWith("signal:reload_card_html")) {
                redrawCard();
                return true;
            }
            // mark card using javascript
            if (url.startsWith("signal:mark_current_card")) {
                if (isAnkiApiNull("markCard")) {
                    showDeveloperContact(ankiJsErrorCodeDefault);
                    return true;
                } else if (mJsApiListMap.get("markCard")) {
                    executeCommand(COMMAND_MARK);
                } else {
                    // see 02-string.xml
                    showDeveloperContact(ankiJsErrorCodeMarkCard);
                }
                return true;
            }
            // flag card (blue, green, orange, red) using javascript from AnkiDroid webview
            if (url.startsWith("signal:flag_")) {
                if (isAnkiApiNull("toggleFlag")) {
                    showDeveloperContact(ankiJsErrorCodeDefault);
                    return true;
                } else if (!mJsApiListMap.get("toggleFlag")) {
                    // see 02-string.xml
                    showDeveloperContact(ankiJsErrorCodeFlagCard);
                    return true;
                }

                String mFlag = url.replaceFirst("signal:flag_","");
                switch (mFlag) {
                    case "none": executeCommand(COMMAND_UNSET_FLAG);
                        return true;
                    case "red": executeCommand(COMMAND_TOGGLE_FLAG_RED);
                        return true;
                    case "orange": executeCommand(COMMAND_TOGGLE_FLAG_ORANGE);
                        return true;
                    case "green": executeCommand(COMMAND_TOGGLE_FLAG_GREEN);
                        return true;
                    case "blue": executeCommand(COMMAND_TOGGLE_FLAG_BLUE);
                        return true;
                    default:
                        Timber.d("No such Flag found.");
                        return true;
                }
            }

            // Show toast using JS
            if (url.startsWith("signal:anki_show_toast:")) {
                String msg = url.replaceFirst("signal:anki_show_toast:", "");
                String msgDecode = decodeUrl(msg);
                UIUtils.showThemedToast(AbstractFlashcardViewer.this, msgDecode, true);
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
                /*
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
                Timber.w(e); // Don't crash if the intent is not handled
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
            Timber.d("Java onPageFinished triggered: %s", url);

            // onPageFinished will be called multiple times if the WebView redirects by setting window.location.href
            if (url.equals(mViewerUrl)) {
                Timber.d("New URL, drawing flags, marks, and triggering JS onPageFinished: %s", url);
                drawFlag();
                drawMark();
                view.loadUrl("javascript:onPageFinished();");
            }
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
                if (mCardWebView == null || !mCardWebView.equals(view)) {
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
                destroyWebView(mCardWebView);
                mCardWebView = null;
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
                mLastCrashingCardId = mCurrentCard.getId();


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
        private void displayRenderLoopDialog(Card currentCard, RenderProcessGoneDetail detail) {
            String cardInformation = Long.toString(currentCard.getId());
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

    private final MaxExecFunction mDisplayMediaLoadedFromHttpWarningSnackbar = new MaxExecFunction(3, () -> {
        OnClickListener onClickListener = (v) -> openUrl(Uri.parse(getString(R.string.link_faq_external_http_content)));
        showSnackbar(getString(R.string.cannot_load_http_resource), R.string.help, onClickListener);
    });

    private void displayCouldNotFindMediaSnackbar(String filename) {
        OnClickListener onClickListener = (v) -> openUrl(Uri.parse(getString(R.string.link_faq_missing_media)));
        showSnackbar(getString(R.string.card_viewer_could_not_find_image, filename), R.string.help, onClickListener);
    }

    private void displayMediaUpgradeRequiredSnackbar() {
        OnClickListener onClickListener = (v) -> openUrl(Uri.parse(getString(R.string.link_faq_invalid_protocol_relative)));
        showSnackbar(getString(R.string.card_viewer_media_relative_protocol), R.string.help, onClickListener);
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
            CardViewerWebClient c = ((CardViewerWebClient) this.mCardWebView.getWebViewClient());
            if (c == null) {
                throw new IllegalStateException("Couldn't obtain WebView - maybe it wasn't created yet");
            }
            c.filterUrl(url);
        } else {
            throw new IllegalStateException("Can't get WebViewClient due to Android API");
        }
    }

    // Check if value null
    private boolean isAnkiApiNull(String api) {
        return mJsApiListMap.get(api) == null;
    }

    /*
     * see 02-strings.xml
     * Show Error code when mark card or flag card unsupported
     * 1 - mark card
     * 2 - flag card
     *
     * show developer contact if js api used in card is deprecated
     */
    private void showDeveloperContact(int errorCode) {
        String errorMsg = getString(R.string.anki_js_error_code, errorCode);

        View parentLayout = findViewById(android.R.id.content);
        String snackbarMsg = getString(R.string.api_version_developer_contact, mCardSuppliedDeveloperContact, errorMsg);


        Snackbar snackbar = UIUtils.showSnackbar(this,
                snackbarMsg,
                false,
                R.string.reviewer_invalid_api_version_visit_documentation,
                view -> openUrl(Uri.parse("https://github.com/ankidroid/Anki-Android/wiki")),
                parentLayout,
                null);
        TextView snackbarTextView = snackbar.getView().findViewById(com.google.android.material.R.id.snackbar_text);
        snackbarTextView.setMaxLines(3);
        snackbar.show();
    }

    /**
     * Supplied api version must be equal to current api version to call mark card, toggle flag functions etc.
     */
    private boolean requireApiVersion(String apiVer, String apiDevContact) {
        try {

            if (TextUtils.isEmpty(apiDevContact)) {
                return false;
            }

            Version mVersionCurrent = Version.valueOf(sCurrentJsApiVersion);
            Version mVersionSupplied = Version.valueOf(apiVer);

            /*
            * if api major version equals to supplied major version then return true and also check for minor version and patch version
            * show toast for update and contact developer if need updates
            * otherwise return false
            */
            if (mVersionSupplied.equals(mVersionCurrent)) {
                return true;
            } else if (mVersionSupplied.lessThan(mVersionCurrent)) {
                UIUtils.showThemedToast(AbstractFlashcardViewer.this, getString(R.string.update_js_api_version, mCardSuppliedDeveloperContact), false);

                return mVersionSupplied.greaterThanOrEqualTo(Version.valueOf(sMinimumJsApiVersion));
            } else {
                UIUtils.showThemedToast(AbstractFlashcardViewer.this, getString(R.string.valid_js_api_version, mCardSuppliedDeveloperContact), false);
                return false;
            }
        } catch (Exception e) {
          Timber.w(e, "requireApiVersion::exception");
        }
        return false;
    }

    @VisibleForTesting
    void loadInitialCard() {
        TaskManager.launchCollectionTask(new CollectionTask.GetCard(), mAnswerCardHandler(false));
    }

    public ReviewerUi.ControlBlock getControlBlocked() {
        return mControlBlocked;
    }

    public boolean isDisplayingAnswer() {
        return sDisplayAnswer;
    }

    public boolean isControlBlocked() {
        return getControlBlocked() != ControlBlock.UNBLOCKED;
    }

    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    static void setEditorCard(Card card) {
        //I don't see why we don't do this by intent.
        sEditorCard = card;
    }

    @VisibleForTesting
    String getCorrectTypedAnswer() {
        return mTypeCorrect;
    }

    @VisibleForTesting
    String getCardContent() {
        return mCardContent;
    }

    protected void showTagsDialog() {
        ArrayList<String> tags = new ArrayList<>(getCol().getTags().all());
        ArrayList<String> selTags = new ArrayList<>(mCurrentCard.note().getTags());
        TagsDialog dialog = TagsDialog.newInstance(TagsDialog.DialogType.ADD_TAG, selTags, tags);
        showDialogFragment(dialog);
    }

    @Override
    public void onSelectedTags(List<String> selectedTags, int option) {
        if (!mCurrentCard.note().getTags().equals(selectedTags)) {
            String tagString = TextUtils.join(" ", selectedTags);
            Note note = mCurrentCard.note();
            note.setTagsFromStr(tagString);
            note.flush();
            // Reload current card to reflect tag changes
            displayCardQuestion(true);
        }
    }

    // init or reset api list
    private void jsApiInit() {
        mCardSuppliedApiVersion = "";
        mCardSuppliedDeveloperContact = "";

        for (String api : mApiList) {
            mJsApiListMap.put(api, false);
        }
    }

 /*
 Javascript Interface class for calling Java function from AnkiDroid WebView
see card.js for available functions
 */
    // list of api that can be accessed
    private final String[] mApiList = {"toggleFlag", "markCard"};
    // JS api list enable/disable status
    private final HashMap<String, Boolean> mJsApiListMap = new HashMap<>(mApiList.length);
    public JavaScriptFunction javaScriptFunction() {
        return new JavaScriptFunction();
    }

    public class JavaScriptFunction {

        // if supplied api version match then enable api
        private void enableJsApi() {
            for (String api : mApiList) {
                mJsApiListMap.put(api, true);
            }
        }

        @JavascriptInterface
        public String init(String jsonData) {
            JSONObject data;
            String apiStatusJson = "";

            try {
                data = new JSONObject(jsonData);
                if (!(data == JSONObject.NULL)) {
                    mCardSuppliedApiVersion = data.optString("version", "");
                    mCardSuppliedDeveloperContact  = data.optString("developer", "");

                    if (requireApiVersion(mCardSuppliedApiVersion, mCardSuppliedDeveloperContact)) {
                        enableJsApi();
                    }

                    apiStatusJson = JSONObject.fromMap(mJsApiListMap).toString();
                }

            } catch (JSONException j) {
                Timber.w(j);
                UIUtils.showThemedToast(AbstractFlashcardViewer.this, getString(R.string.invalid_json_data, j.getLocalizedMessage()), false);
            }
            return apiStatusJson;
        }

        // This method and the one belows return "default" values when there is no count nor ETA.
        // Javascript may expect ETA and Counts to be set, this ensure it does not bug too much by providing a value of correct type
        // but with a clearly incorrect value.
        // It's overridden in the Reviewer, where those values are actually defined.
        @JavascriptInterface
        public String ankiGetNewCardCount() {
            return "-1";
        }


        @JavascriptInterface
        public String ankiGetLrnCardCount() {
            return "-1";
        }


        @JavascriptInterface
        public String ankiGetRevCardCount() {
            return "-1";
        }


        @JavascriptInterface
        public int ankiGetETA() {
            return -1;
        }

        @JavascriptInterface
        public boolean ankiGetCardMark() {
            return shouldDisplayMark();
        }

        
        @JavascriptInterface
        public int ankiGetCardFlag() {
            return mCurrentCard.userFlag();
        }

        @JavascriptInterface
        public String ankiGetNextTime1() { return (String) mNext1.getText(); }

        @JavascriptInterface
        public String ankiGetNextTime2() { return (String) mNext2.getText(); }

        @JavascriptInterface
        public String ankiGetNextTime3() { return (String) mNext3.getText(); }

        @JavascriptInterface
        public String ankiGetNextTime4() { return (String) mNext4.getText(); }
        
        @JavascriptInterface
        public int ankiGetCardReps() {
            return mCurrentCard.getReps();
        }

        @JavascriptInterface
        public int ankiGetCardInterval() {
            return mCurrentCard.getIvl();
        }

        /** Returns the ease as an int (percentage * 10). Default: 2500 (250%). Minimum: 1300 (130%) */
        @JavascriptInterface
        public int ankiGetCardFactor() {
            return mCurrentCard.getFactor();
        }

        /** Returns the last modified time as a Unix timestamp in seconds. Example: 1477384099 */
        @JavascriptInterface
        public long ankiGetCardMod() {
            return mCurrentCard.getMod();
        }

        /** Returns the ID of the card. Example: 1477380543053 */
        @JavascriptInterface
        public long ankiGetCardId() {
             return mCurrentCard.getId();
         }

        /** Returns the ID of the note which generated the card. Example: 1590418157630 */
        @JavascriptInterface
        public long ankiGetCardNid() {
            return mCurrentCard.getNid();
        }

        @JavascriptInterface
        @Consts.CARD_TYPE
        public int ankiGetCardType() {
            return mCurrentCard.getType();
        }

        /** Returns the ID of the deck which contains the card. Example: 1595967594978 */
        @JavascriptInterface
        public long ankiGetCardDid() {
            return mCurrentCard.getDid();
        }

        @JavascriptInterface
        public int ankiGetCardLeft() {
            return mCurrentCard.getLeft();
        }

        /** Returns the ID of the home deck for the card if it is filtered, or 0 if not filtered. Example: 1595967594978 */
        @JavascriptInterface
        public long ankiGetCardODid() {
            return mCurrentCard.getODid();
        }

        @JavascriptInterface
        public long ankiGetCardODue() {
            return mCurrentCard.getODue();
        }

        @JavascriptInterface
        @Consts.CARD_QUEUE
        public int ankiGetCardQueue() {
            return mCurrentCard.getQueue();
        }

        @JavascriptInterface
        public int ankiGetCardLapses() {
             return mCurrentCard.getLapses();
         }

        @JavascriptInterface
        public long ankiGetCardDue() {
            return mCurrentCard.getDue();
         }

        @JavascriptInterface
        public boolean ankiIsInFullscreen() {
            return isFullscreen();
        }

        @JavascriptInterface
        public boolean ankiIsTopbarShown() {
            return mPrefShowTopbar;
        }

        @JavascriptInterface
        public boolean ankiIsInNightMode() {
            return isInNightMode();
        }

        @JavascriptInterface
        public boolean ankiIsDisplayingAnswer() { return isDisplayingAnswer(); };

        @JavascriptInterface
        public String ankiGetDeckName() {
            return Decks.basename(getCol().getDecks().get(mCurrentCard.getDid()).getString("name"));
        }

        @JavascriptInterface
        public boolean ankiIsActiveNetworkMetered() {
            try {
                ConnectivityManager cm = (ConnectivityManager) AnkiDroidApp.getInstance().getApplicationContext()
                        .getSystemService(Context.CONNECTIVITY_SERVICE);
                if (cm == null) {
                    Timber.w("ConnectivityManager not found - assuming metered connection");
                    return true;
                }
                return cm.isActiveNetworkMetered();
            } catch (Exception e) {
                Timber.w(e, "Exception obtaining metered connection - assuming metered connection");
                return true;
            }
        }
    }
}
