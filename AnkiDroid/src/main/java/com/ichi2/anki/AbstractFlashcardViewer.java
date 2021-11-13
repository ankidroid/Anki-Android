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
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;

import androidx.annotation.CheckResult;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.VisibleForTesting;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.appcompat.app.ActionBar;
import androidx.webkit.WebViewAssetLoader;

import android.text.TextUtils;
import android.util.Pair;
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
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import com.afollestad.materialdialogs.MaterialDialog;
import com.drakeet.drawer.FullDraggableContainer;
import com.google.android.material.snackbar.Snackbar;
import com.ichi2.anim.ViewAnimation;
import com.ichi2.anki.cardviewer.CardHtml;
import com.ichi2.anki.cardviewer.HtmlGenerator;
import com.ichi2.anki.cardviewer.Side;
import com.ichi2.anki.cardviewer.GestureProcessor;
import com.ichi2.anki.cardviewer.MissingImageHandler;
import com.ichi2.anki.cardviewer.OnRenderProcessGoneDelegate;
import com.ichi2.anki.cardviewer.TTS;
import com.ichi2.anki.cardviewer.TypeAnswer;
import com.ichi2.anki.cardviewer.ViewerCommand;
import com.ichi2.anki.dialogs.tags.TagsDialog;
import com.ichi2.anki.dialogs.tags.TagsDialogFactory;
import com.ichi2.anki.dialogs.tags.TagsDialogListener;
import com.ichi2.anki.cardviewer.CardAppearance;
import com.ichi2.anki.receiver.SdCardReceiver;
import com.ichi2.anki.reviewer.AutomaticAnswer;
import com.ichi2.anki.reviewer.AutomaticAnswerAction;
import com.ichi2.anki.reviewer.EaseButton;
import com.ichi2.anki.reviewer.FullScreenMode;
import com.ichi2.anki.reviewer.PreviousAnswerIndicator;
import com.ichi2.anki.reviewer.ReviewerUi;
import com.ichi2.anki.servicelayer.AnkiMethod;
import com.ichi2.anki.servicelayer.LanguageHintService;
import com.ichi2.anki.servicelayer.NoteService;
import com.ichi2.anki.servicelayer.SchedulerService;
import com.ichi2.anki.servicelayer.SchedulerService.NextCard;
import com.ichi2.anki.servicelayer.TaskListenerBuilder;
import com.ichi2.anki.servicelayer.UndoService;
import com.ichi2.async.CollectionTask;
import com.ichi2.async.TaskListener;
import com.ichi2.async.TaskManager;
import com.ichi2.compat.CompatHelper;
import com.ichi2.libanki.Decks;
import com.ichi2.libanki.SoundOrVideoTag;
import com.ichi2.libanki.TTSTag;
import com.ichi2.libanki.sched.AbstractSched;
import com.ichi2.libanki.Card;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Consts;
import com.ichi2.libanki.DeckConfig;
import com.ichi2.libanki.Note;
import com.ichi2.libanki.Sound;
import com.ichi2.libanki.Utils;
import com.ichi2.themes.Themes;
import com.ichi2.ui.FixedEditText;
import com.ichi2.utils.AdaptionUtil;
import com.ichi2.utils.AndroidUiUtils;
import com.ichi2.utils.AssetHelper;
import com.ichi2.utils.ClipboardUtil;
import com.ichi2.utils.Computation;

import com.ichi2.utils.HandlerUtils;
import com.ichi2.utils.HashUtil;
import com.ichi2.utils.MaxExecFunction;
import com.ichi2.utils.WebViewDebugging;

import net.ankiweb.rsdroid.RustCleanup;

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
import java.util.Objects;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import kotlin.Unit;
import timber.log.Timber;

import static com.ichi2.anki.cardviewer.ViewerCommand.*;
import static com.ichi2.libanki.Sound.SoundSide;

import static com.ichi2.anim.ActivityTransitionAnimation.Direction.*;

@SuppressWarnings({"PMD.AvoidThrowingRawExceptionTypes","PMD.FieldDeclarationsShouldBeAtStartOfClass"})
public abstract class AbstractFlashcardViewer extends NavigationDrawerActivity implements ReviewerUi, CommandProcessor, TagsDialogListener, WhiteboardMultiTouchMethods, AutomaticAnswer.AutomaticallyAnswered {

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

    /** Time to wait in milliseconds before resuming fullscreen mode **/
    protected static final int INITIAL_HIDE_DELAY = 200;

    /** to be sent to and from the card editor */
    private static Card sEditorCard;

    protected static boolean sDisplayAnswer = false;

    private boolean mTtsInitialized = false;
    private boolean mReplayOnTtsInit = false;

    protected static final int MENU_DISABLED = 3;

    private AnkiDroidJsAPI mAnkiDroidJsAPI;

    /**
     * Broadcast that informs us when the sd card is about to be unmounted
     */
    private BroadcastReceiver mUnmountReceiver = null;

    private TagsDialogFactory mTagsDialogFactory;

    /**
     * Variables to hold preferences
     */
    protected boolean mPrefShowTopbar;
    private FullScreenMode mPrefFullscreenReview = FullScreenMode.getDEFAULT();
    private int mRelativeButtonSize;
    private boolean mDoubleScrolling;
    private boolean mScrollingButtons;
    private boolean mGesturesEnabled;
    private boolean mLargeAnswerButtons;
    private int mDoubleTapTimeInterval = DEFAULT_DOUBLE_TAP_TIME_INTERVAL;
    // Android WebView
    protected boolean mDisableClipboard = false;

    @NonNull protected AutomaticAnswer mAutomaticAnswer = AutomaticAnswer.defaultInstance(this);

    protected TypeAnswer mTypeAnswer;

    /** Generates HTML content */
    private HtmlGenerator mHtmlGenerator;

    // Default short animation duration, provided by Android framework
    protected int mShortAnimDuration;
    private boolean mBackButtonPressedToReturn = false;

    // Preferences from the collection
    private boolean mShowNextReviewTime;

    private boolean mIsSelecting = false;
    private boolean mTouchStarted = false;
    private boolean mInAnswer = false;
    private boolean mAnswerSoundsAdded = false;

    /**
     * Variables to hold layout objects that we need to update or handle events for
     */
    private View mLookUpIcon;
    private WebView mCardWebView;
    private FrameLayout mCardFrame;
    private FrameLayout mTouchLayer;
    protected FixedEditText mAnswerField;
    protected LinearLayout mFlipCardLayout;
    protected LinearLayout mEaseButtonsLayout;
    protected EaseButton mEaseButton1;
    protected EaseButton mEaseButton2;
    protected EaseButton mEaseButton3;
    protected EaseButton mEaseButton4;
    protected RelativeLayout mTopBarLayout;
    private android.content.ClipboardManager mClipboard;
    private PreviousAnswerIndicator mPreviousAnswerIndicator;

    protected Card mCurrentCard;
    private int mCurrentEase;

    private int mInitialFlipCardHeight;
    private boolean mButtonHeightSet = false;

    public static final String DOUBLE_TAP_TIME_INTERVAL = "doubleTapTimeInterval";
    public static final int DEFAULT_DOUBLE_TAP_TIME_INTERVAL = 200;

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
    protected final GestureProcessor mGestureProcessor = new GestureProcessor(this);

    private String mCardContent;
    private String mBaseUrl;
    private String mViewerUrl;
    private WebViewAssetLoader mAssetLoader;

    private final int mFadeDuration = 300;

    protected AbstractSched mSched;

    protected final Sound mSoundPlayer = new Sound();

    /**
     * Time taken to play all medias in mSoundPlayer
     * This is 0 if we have "Read card" enabled, as we can't calculate the duration.
     */
    private long mUseTimerDynamicMS;

    /** Reference to the parent of the cardFrame to allow regeneration of the cardFrame in case of crash */
    private ViewGroup mCardFrameParent;

    /** Lock to allow thread-safe regeneration of mCard */
    private final ReadWriteLock mCardLock = new ReentrantReadWriteLock();

    /** whether controls are currently blocked, and how long we expect them to be */
    private ReviewerUi.ControlBlock mControlBlocked = ControlBlock.SLOW;

    /** Handle providing help for "Image Not Found" */
    private static final MissingImageHandler mMissingImageHandler = new MissingImageHandler();

    /** Preference: Whether the user wants press back twice to return to the main screen" */
    private boolean mExitViaDoubleTapBack;

    @VisibleForTesting
    final OnRenderProcessGoneDelegate mOnRenderProcessGoneDelegate = new OnRenderProcessGoneDelegate(this);
    protected final TTS mTTS = new TTS();

    // ----------------------------------------------------------------------------
    // LISTENERS
    // ----------------------------------------------------------------------------

    private final Handler mLongClickHandler = HandlerUtils.newHandler();
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
            mGestureProcessor.onLongTap();
        }
    };


    // Handler for the "show answer" button
    private final View.OnClickListener mFlipCardListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            Timber.i("AbstractFlashcardViewer:: Show answer button pressed");
            // Ignore what is most likely an accidental double-tap.
            if (getElapsedRealTime() - mLastClickTime < mDoubleTapTimeInterval) {
                return;
            }
            mLastClickTime = getElapsedRealTime();
            mAutomaticAnswer.onShowAnswer();
            displayCardAnswer();
        }
    };


    // Event handler for eases (answer buttons)
    public class SelectEaseHandler implements View.OnClickListener, View.OnTouchListener {
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
                if (getElapsedRealTime() - mLastClickTime >= mDoubleTapTimeInterval) {
                    // For whatever reason, performClick does not return a visual feedback anymore
                    if (!mHasBeenTouched) {
                        view.setPressed(true);
                    }
                    mLastClickTime = getElapsedRealTime();
                    mAutomaticAnswer.onSelectEase();
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
            cardConsumer.accept(cardWebView);
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


    private final TaskListener<Card, Computation<?>> mUpdateCardHandler = new TaskListener<Card, Computation<?>>() {
        private boolean mNoMoreCards;


        @Override
        public void onPreExecute() {
            showProgressBar();
        }


        @Override
        public void onProgressUpdate(Card card) {
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

            onCardEdited(mCurrentCard);
            if (sDisplayAnswer) {
                mSoundPlayer.resetSounds(); // load sounds from scratch, to expose any edit changes
                mAnswerSoundsAdded = false; // causes answer sounds to be reloaded
                generateQuestionSoundList(); // questions must be intentionally regenerated
                displayCardAnswer();
            } else {
                displayCardQuestion();
            }
            hideProgressBar();
        }


        @Override
        public void onPostExecute(Computation<?> result) {
            if (!result.succeeded()) {
                // RuntimeException occurred on update cards
                closeReviewer(DeckPicker.RESULT_DB_ERROR, false);
                return;
            }
            if (mNoMoreCards) {
                closeReviewer(RESULT_NO_MORE_CARDS, true);
            }
        }
    };


    /** Operation after a card has been updated due to being edited. Called before display[Question/Answer] */
    protected void onCardEdited(Card card) {
        // intentionally blank
    }


    class NextCardHandler<Result extends Computation<? extends NextCard<?>>> extends TaskListener<Unit, Result> {
        @Override
        public void onPreExecute() {
            dealWithTimeBox();
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
            if (mSched == null) {
                // TODO: proper testing for restored activity
                finishWithoutAnimation();
                return;
            }

            boolean displaySuccess = result.succeeded();
            if (!displaySuccess) {
                // RuntimeException occurred on answering cards
                closeReviewer(DeckPicker.RESULT_DB_ERROR, false);
                return;
            }

            NextCard<?> nextCardAndResult = Objects.requireNonNull(result.getValue());

            if (nextCardAndResult.hasNoMoreCards()) {
                closeReviewer(RESULT_NO_MORE_CARDS, true);
                return;
            }

            mCurrentCard = nextCardAndResult.nextScheduledCard();

            // Start reviewing next card
            hideProgressBar();
            AbstractFlashcardViewer.this.unblockControls();
            AbstractFlashcardViewer.this.displayCardQuestion();
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

    protected TaskListenerBuilder<Unit, Computation<? extends NextCard<?>>> answerCardHandler(boolean quick) {
        return nextCardHandler()
                .alsoExecuteBefore(() -> blockControls(quick));
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
        restorePreferences();

        mTagsDialogFactory = new TagsDialogFactory(this).attachToActivity(this);

        super.onCreate(savedInstanceState);
        setContentView(getContentViewAttr(mPrefFullscreenReview));

        // Make ACTION_PROCESS_TEXT for in-app searching possible on > Android 4.0
        getDelegate().setHandleNativeActionModesEnabled(true);

        View mainView = findViewById(android.R.id.content);
        initNavigationDrawer(mainView);

        mPreviousAnswerIndicator = new PreviousAnswerIndicator(findViewById(R.id.choosen_answer));

        mShortAnimDuration = getResources().getInteger(android.R.integer.config_shortAnimTime);
    }

    @NonNull
    protected FullScreenMode getFullscreenMode() {
        return mPrefFullscreenReview;
    }

    protected int getContentViewAttr(FullScreenMode fullscreenMode) {
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

        restoreCollectionPreferences(col);

        initLayout();

        setTitle();

        if (!mDisableClipboard) {
            clearClipboard();
        }

        mHtmlGenerator = HtmlGenerator.createInstance(this, this.mTypeAnswer, mBaseUrl);

        // Initialize text-to-speech. This is an asynchronous operation.
        mTTS.initialize(this, new ReadTextListener());

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

        mAutomaticAnswer.disable();
        mLongClickHandler.removeCallbacks(mLongClickTestRunnable);
        mLongClickHandler.removeCallbacks(mStartLongClickAction);

        mSoundPlayer.stopSounds();

        // Prevent loss of data in Cookies
        CookieManager.getInstance().flush();
    }


    @Override
    protected void onResume() {
        super.onResume();
        // Set the context for the Sound manager
        mSoundPlayer.setContext(new WeakReference<>(this));
        mAutomaticAnswer.enable();
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
        mTTS.releaseTts(this);
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
            if (!mExitViaDoubleTapBack || mBackButtonPressedToReturn) {
                closeReviewer(RESULT_DEFAULT, false);
            } else {
                UIUtils.showThemedToast(this, getString(R.string.back_pressed_once_reviewer), true);
            }
            mBackButtonPressedToReturn = true;
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


    protected void undo() {
        if (isUndoAvailable()) {
            new UndoService.Undo().runWithHandler(answerCardHandler(false));
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
        List<SoundOrVideoTag> tags = Sound.extractTagsFromLegacyContent(mCurrentCard.qSimple());
        mSoundPlayer.addSounds(mBaseUrl, tags, SoundSide.QUESTION);
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
                    deleteNoteWithoutConfirmation();
                })
                .build().show();
    }

    /** Consumers should use {@link #showDeleteNoteDialog()}  */
    private void deleteNoteWithoutConfirmation() {
        dismiss(new SchedulerService.DeleteNote(mCurrentCard), () -> UIUtils.showThemedToast(this, R.string.deleted_note, true));
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
        // Temporarily sets the answer indicator dots appearing below the toolbar
        mPreviousAnswerIndicator.displayAnswerIndicator(ease, buttonNumber);

        mSoundPlayer.stopSounds();
        mCurrentEase = ease;

        new SchedulerService.AnswerAndGetCard(mCurrentCard, mCurrentEase).runWithHandler(answerCardHandler(true));
    }

    // Set the content view to the one provided and initialize accessors.
    protected void initLayout() {
        FrameLayout cardContainer = findViewById(R.id.flashcard_frame);

        mTopBarLayout = findViewById(R.id.top_bar);

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

        mEaseButton1 = new EaseButton(EASE_1, findViewById(R.id.flashcard_layout_ease1), findViewById(R.id.ease1), findViewById(R.id.nextTime1));
        mEaseButton1.setListeners(mEaseHandler);
        mEaseButton2 = new EaseButton(EASE_2, findViewById(R.id.flashcard_layout_ease2), findViewById(R.id.ease2), findViewById(R.id.nextTime2));
        mEaseButton2.setListeners(mEaseHandler);
        mEaseButton3 = new EaseButton(EASE_3, findViewById(R.id.flashcard_layout_ease3), findViewById(R.id.ease3), findViewById(R.id.nextTime3));
        mEaseButton3.setListeners(mEaseHandler);
        mEaseButton4 = new EaseButton(EASE_4, findViewById(R.id.flashcard_layout_ease4), findViewById(R.id.ease4), findViewById(R.id.nextTime4));
        mEaseButton4.setListeners(mEaseHandler);

        if (!mShowNextReviewTime) {
            mEaseButton1.hideNextReviewTime();
            mEaseButton2.hideNextReviewTime();
            mEaseButton3.hideNextReviewTime();
            mEaseButton4.hideNextReviewTime();
        }

        Button flipCard = findViewById(R.id.flip_card);
        mFlipCardLayout = findViewById(R.id.flashcard_layout_flip);
        mFlipCardLayout.setOnClickListener(mFlipCardListener);

        if (animationEnabled()) {
            flipCard.setBackgroundResource(Themes.getResFromAttr(this, R.attr.hardButtonRippleRef));
        }

        if (!mButtonHeightSet && mRelativeButtonSize != 100) {
            ViewGroup.LayoutParams params = mFlipCardLayout.getLayoutParams();
            params.height = params.height * mRelativeButtonSize / 100;
            mEaseButton1.setButtonScale(mRelativeButtonSize);
            mEaseButton2.setButtonScale(mRelativeButtonSize);
            mEaseButton3.setButtonScale(mRelativeButtonSize);
            mEaseButton4.setButtonScale(mRelativeButtonSize);
            mButtonHeightSet = true;
        }

        mInitialFlipCardHeight = mFlipCardLayout.getLayoutParams().height;
        if (mLargeAnswerButtons) {
            ViewGroup.LayoutParams params = mFlipCardLayout.getLayoutParams();
            params.height = mInitialFlipCardHeight * 2;
        }

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
        RelativeLayout.LayoutParams cardContainerParams = (RelativeLayout.LayoutParams) cardContainer.getLayoutParams();

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
        cardContainer.setLayoutParams(cardContainerParams);
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

        // This setting toggles file system access within WebView
        // The default configuration is already true in apps targeting API <= 29
        // Hence, this setting is only useful for apps targeting API >= 30
        webView.getSettings().setAllowFileAccess(true);

        // Problems with focus and input tags is the reason we keep the old type answer mechanism for old Androids.
        webView.setFocusableInTouchMode(mTypeAnswer.useInputTag());
        webView.setScrollbarFadingEnabled(true);
        Timber.d("Focusable = %s, Focusable in touch mode = %s", webView.isFocusable(), webView.isFocusableInTouchMode());

        webView.setWebViewClient(new CardViewerWebClient(mAssetLoader));
        // Set transparent color to prevent flashing white when night mode enabled
        webView.setBackgroundColor(Color.argb(1, 0, 0, 0));

        // Javascript interface for calling AnkiDroid functions in webview, see card.js
        mAnkiDroidJsAPI = javaScriptFunction();
        webView.addJavascriptInterface(mAnkiDroidJsAPI, "AnkiDroidJS");

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

        if (mLargeAnswerButtons) {
            mEaseButtonsLayout.setOrientation(LinearLayout.VERTICAL);
            mEaseButtonsLayout.removeAllViewsInLayout();

            mEaseButton1.detachFromParent();
            mEaseButton2.detachFromParent();
            mEaseButton3.detachFromParent();
            mEaseButton4.detachFromParent();

            LinearLayout row1 = new LinearLayout(getBaseContext());
            row1.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout row2 = new LinearLayout(getBaseContext());
            row2.setOrientation(LinearLayout.HORIZONTAL);

            switch (getAnswerButtonCount()) {
                case 2:
                    mEaseButton1.setHeight(mInitialFlipCardHeight * 2);
                    mEaseButton2.setHeight(mInitialFlipCardHeight * 2);
                    mEaseButton1.addTo(row2);
                    mEaseButton2.addTo(row2);
                    mEaseButtonsLayout.addView(row2);
                    break;
                case 3:
                    mEaseButton3.addTo(row1);
                    mEaseButton1.addTo(row2);
                    mEaseButton2.addTo(row2);
                    ViewGroup.LayoutParams params;
                    params = new LinearLayout.LayoutParams(Resources.getSystem().getDisplayMetrics().widthPixels / 2, mEaseButton4.getHeight());
                    ((LinearLayout.LayoutParams) params).setMarginStart(Resources.getSystem().getDisplayMetrics().widthPixels / 2);
                    row1.setLayoutParams(params);
                    mEaseButtonsLayout.addView(row1);
                    mEaseButtonsLayout.addView(row2);
                    break;
                default:
                    mEaseButton2.addTo(row1);
                    mEaseButton4.addTo(row1);
                    mEaseButton1.addTo(row2);
                    mEaseButton3.addTo(row2);
                    mEaseButtonsLayout.addView(row1);
                    mEaseButtonsLayout.addView(row2);
                    break;
            }
        }

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
        mEaseButton1.hide();
        mEaseButton2.hide();
        mEaseButton3.hide();
        mEaseButton4.hide();
    }


    /**
     * Focuses the appropriate field for an answer
     * And allows keyboard shortcuts to go to the default handlers.
     * */
    private void focusAnswerCompletionField() {
        // This does not handle mUseInputTag (the WebView contains an input field with a typable answer).
        // In this case, the user can use touch to focus the field if necessary.
        if (mTypeAnswer.autoFocusEditText()) {
            mAnswerField.focusWithKeyboard();
        } else {
            mFlipCardLayout.requestFocus();
        }
    }


    protected void switchTopBarVisibility(int visible) {
        mPreviousAnswerIndicator.setVisibility(visible);
    }


    protected void initControls() {
        mCardFrame.setVisibility(View.VISIBLE);
        mPreviousAnswerIndicator.setVisibility(View.VISIBLE);
        mFlipCardLayout.setVisibility(View.VISIBLE);

        mAnswerField.setVisibility(mTypeAnswer.validForEditText() ? View.VISIBLE : View.GONE);
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

        mTypeAnswer = TypeAnswer.createInstance(preferences);
        // On newer Androids, ignore this setting, which should be hidden in the prefs anyway.
        mDisableClipboard = "0".equals(preferences.getString("dictionary", "0"));
        // mDeckFilename = preferences.getString("deckFilename", "");
        mPrefFullscreenReview = FullScreenMode.fromPreference(preferences);
        mRelativeButtonSize = preferences.getInt("answerButtonSize", 100);
        mTTS.setEnabled(preferences.getBoolean("tts", false));
        mScrollingButtons = preferences.getBoolean("scrolling_buttons", false);
        mDoubleScrolling = preferences.getBoolean("double_scrolling", false);
        mPrefShowTopbar = preferences.getBoolean("showTopbar", true);
        mLargeAnswerButtons = preferences.getBoolean("showLargeAnswerButtons", false);
        mDoubleTapTimeInterval = preferences.getInt(DOUBLE_TAP_TIME_INTERVAL, DEFAULT_DOUBLE_TAP_TIME_INTERVAL);
        mExitViaDoubleTapBack = preferences.getBoolean("exitViaDoubleTapBack", false);

        mGesturesEnabled = preferences.getBoolean("gestures", false);
        mLinkOverridesTouchGesture = preferences.getBoolean("linkOverridesTouchGesture", false);
        if (mGesturesEnabled) {
            mGestureProcessor.init(preferences);
        }

        if (preferences.getBoolean("keepScreenOn", false)) {
            this.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }

        return preferences;
    }


    protected void restoreCollectionPreferences(Collection col) {

        // These are preferences we pull out of the collection instead of SharedPreferences
        try {
            mShowNextReviewTime = col.get_config_boolean("estTimes");
            SharedPreferences preferences = AnkiDroidApp.getSharedPrefs(getBaseContext());
            mAutomaticAnswer = AutomaticAnswer.createInstance(this, preferences, col);
        } catch (Exception ex) {
            Timber.w(ex);
            onCollectionLoadError();
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

    /** A new card has been loaded into the Viewer, or the question has been re-shown */
    protected void updateForNewCard() {
        updateActionBar();

        // Clean answer field
        if (mTypeAnswer.validForEditText()) {
            mAnswerField.setText("");
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

    @Override
    public void automaticShowQuestion(@NonNull AutomaticAnswerAction action) {
        // Assume hitting the "Again" button when auto next question
        mEaseButton1.performSafeClick();
    }

    @Override
    public void automaticShowAnswer() {
        if (mFlipCardLayout.isEnabled() && mFlipCardLayout.getVisibility() == View.VISIBLE) {
            mFlipCardLayout.performClick();
        }
    }

    class ReadTextListener implements ReadText.ReadTextListener {
        public void onDone(SoundSide playedSide) {
            Timber.d("done reading text");
            if (playedSide == SoundSide.QUESTION) {
                mAutomaticAnswer.scheduleAutomaticDisplayAnswer();
            } else if (playedSide == SoundSide.ANSWER) {
                mAutomaticAnswer.scheduleAutomaticDisplayQuestion();
            }
        }
    }


    public void displayCardQuestion() {
        displayCardQuestion(false);

        // js api initialisation / reset
        mAnkiDroidJsAPI.init();
    }

    protected void displayCardQuestion(boolean reload) {
        Timber.d("displayCardQuestion()");
        sDisplayAnswer = false;
        mBackButtonPressedToReturn = false;

        setInterface();

        mTypeAnswer.setInput("");
        mTypeAnswer.updateInfo(mCurrentCard, getResources());

        if (!mCurrentCard.isEmpty() && mTypeAnswer.validForEditText()) {
            // Show text entry based on if the user wants to write the answer
            mAnswerField.setVisibility(View.VISIBLE);
            LanguageHintService.applyLanguageHint(mAnswerField, mTypeAnswer.getLanguageHint());
        } else {
            mAnswerField.setVisibility(View.GONE);
        }

        CardHtml content = mHtmlGenerator.generateHtml(mCurrentCard, reload, Side.FRONT);
        updateCard(content);
        hideEaseButtons();

        mAutomaticAnswer.onDisplayQuestion();
        // If Card-based TTS is enabled, we "automatic display" after the TTS has finished as we don't know the duration
        if (!mTTS.isEnabled()) {
            mAutomaticAnswer.scheduleAutomaticDisplayAnswer(mUseTimerDynamicMS);
        }


        Timber.i("AbstractFlashcardViewer:: Question successfully shown for card id %d", mCurrentCard.getId());
    }

    protected void displayCardAnswer() {
        // #7294 Required in case the animation end action does not fire:
        actualHideEaseButtons();
        Timber.d("displayCardAnswer()");
        mMissingImageHandler.onCardSideChange();
        mBackButtonPressedToReturn = false;

        // prevent answering (by e.g. gestures) before card is loaded
        if (mCurrentCard == null) {
            return;
        }

        // TODO needs testing: changing a card's model without flipping it back to the front
        //  (such as editing a card, then editing the card template)
        mTypeAnswer.updateInfo(mCurrentCard, getResources());

        // Explicitly hide the soft keyboard. It *should* be hiding itself automatically,
        // but sometimes failed to do so (e.g. if an OnKeyListener is attached).
        if (mTypeAnswer.validForEditText()) {
            InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            inputMethodManager.hideSoftInputFromWindow(mAnswerField.getWindowToken(), 0);
        }

        sDisplayAnswer = true;


        mSoundPlayer.stopSounds();
        mAnswerField.setVisibility(View.GONE);
        // Clean up the user answer and the correct answer
        if (!mTypeAnswer.useInputTag()) {
            mTypeAnswer.setInput(mAnswerField.getText().toString());
        }

        mIsSelecting = false;
        CardHtml answerContent = mHtmlGenerator.generateHtml(mCurrentCard, false, Side.BACK);
        updateCard(answerContent);
        displayAnswerBottomBar();

        mAutomaticAnswer.onDisplayAnswer();
        // If Card-based TTS is enabled, we "automatic display" after the TTS has finished as we don't know the duration
        if (!mTTS.isEnabled()) {
            mAutomaticAnswer.scheduleAutomaticDisplayQuestion(mUseTimerDynamicMS);
        }
    }


    @Override
    public void scrollCurrentCardBy(int dy) {
        processCardAction(cardWebView -> {
            if (dy != 0 && cardWebView.canScrollVertically(dy)) {
                cardWebView.scrollBy(0, dy);
            }
        });
    }


    @Override
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

    @RustCleanup("mAnswerSoundsAdded is no longer necessary once we transition as it's a fast operation")
    private void addAnswerSounds(Supplier<List<SoundOrVideoTag>> answerSounds) {
        // don't add answer sounds multiple times, such as when reshowing card after exiting editor
        // additionally, this condition reduces computation time
        if (!mAnswerSoundsAdded) {
            mSoundPlayer.addSounds(mBaseUrl, answerSounds.get(), SoundSide.ANSWER);
            mAnswerSoundsAdded = true;
        }
    }

    protected boolean isInNightMode() {
        return CardAppearance.isInNightMode(AnkiDroidApp.getSharedPrefs(this));
    }


    private void updateCard(final CardHtml content) {
        Timber.d("updateCard()");

        mUseTimerDynamicMS = 0;


        if (sDisplayAnswer) {
            addAnswerSounds(() -> content.getSoundTags(Side.BACK));
        } else {
            // reset sounds each time first side of card is displayed, which may happen repeatedly without ever
            // leaving the card (such as when edited)
            mSoundPlayer.resetSounds();
            mAnswerSoundsAdded = false;
            mSoundPlayer.addSounds(mBaseUrl, content.getSoundTags(Side.FRONT), SoundSide.QUESTION);
            if (mAutomaticAnswer.isEnabled() && !mAnswerSoundsAdded && getConfigForCurrentCard().optBoolean("autoplay", false)) {
                addAnswerSounds(() -> content.getSoundTags(Side.BACK));
            }
        }

        mCardContent = content.getTemplateHtml();
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
            boolean useTTS = mTTS.isEnabled() &&
                    !(sDisplayAnswer && mSoundPlayer.hasAnswer()) && !(!sDisplayAnswer && mSoundPlayer.hasQuestion());
            // We need to play the sounds from the proper side of the card
            if (!useTTS) { // Text to speech not in effect here
                if (doAudioReplay && replayQuestion && sDisplayAnswer) {
                    // only when all of the above are true will question be played with answer, to match desktop
                    playSounds(SoundSide.QUESTION_AND_ANSWER);
                } else if (sDisplayAnswer) {
                    playSounds(SoundSide.ANSWER);
                    if (mAutomaticAnswer.isEnabled()) {
                        mUseTimerDynamicMS = mSoundPlayer.getSoundsLength(SoundSide.ANSWER);
                    }
                } else { // question is displayed
                    playSounds(SoundSide.QUESTION);
                    // If the user wants to show the answer automatically
                    if (mAutomaticAnswer.isEnabled()) {
                        mUseTimerDynamicMS = mSoundPlayer.getSoundsLength(SoundSide.QUESTION_AND_ANSWER);
                    }
                }
            } else { // Text to speech is in effect here
                // If the question is displayed or if the question should be replayed, read the question
                if (mTtsInitialized) {
                    if (!sDisplayAnswer || doAudioReplay && replayQuestion) {
                        readCardTts(SoundSide.QUESTION);
                    }
                    if (sDisplayAnswer) {
                        readCardTts(SoundSide.ANSWER);
                    }
                } else {
                    mReplayOnTtsInit = true;
                }
            }
        }
    }


    private void readCardTts(SoundSide soundSide) {
        List<TTSTag> tags = CardHtml.legacyGetTtsTags(mCurrentCard, soundSide, this);
        if (tags != null) {
            mTTS.readCardText(tags, mCurrentCard, soundSide);
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
     * Shows the dialogue for selecting TTS for the current card and cardside.
     */
    protected void showSelectTtsDialogue() {
        if (mTtsInitialized) {
            mTTS.selectTts(this, mCurrentCard, sDisplayAnswer ? SoundSide.ANSWER : SoundSide.QUESTION);
        }
    }


    /**
     * Returns the configuration for the current {@link Card}.
     *
     * @return The configuration for the current {@link Card}
     */
    private DeckConfig getConfigForCurrentCard() {
        return getCol().getDecks().confForDid(CardUtils.getDeckIdForCard(mCurrentCard));
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


    protected void unblockControls() {
        mControlBlocked = ControlBlock.UNBLOCKED;
        mCardFrame.setEnabled(true);
        mFlipCardLayout.setEnabled(true);

        mEaseButton1.unblockBasedOnEase(mCurrentEase);
        mEaseButton2.unblockBasedOnEase(mCurrentEase);
        mEaseButton3.unblockBasedOnEase(mCurrentEase);
        mEaseButton4.unblockBasedOnEase(mCurrentEase);

        if (mTypeAnswer.validForEditText()) {
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

        mEaseButton1.blockBasedOnEase(mCurrentEase);
        mEaseButton2.blockBasedOnEase(mCurrentEase);
        mEaseButton3.blockBasedOnEase(mCurrentEase);
        mEaseButton4.blockBasedOnEase(mCurrentEase);

        if (mTypeAnswer.validForEditText()) {
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

    protected boolean buryCard() {
        return dismiss(new SchedulerService.BuryCard(mCurrentCard), () -> UIUtils.showThemedToast(this, R.string.buried_card, true));
    }

    protected boolean suspendCard() {
        return dismiss(new SchedulerService.SuspendCard(mCurrentCard), () -> UIUtils.showThemedToast(this, R.string.suspended_card, true));
    }

    protected boolean suspendNote() {
        return dismiss(new SchedulerService.SuspendNote(mCurrentCard), () -> UIUtils.showThemedToast(this, R.string.suspended_note, true));
    }

    protected boolean buryNote() {
        return dismiss(new SchedulerService.BuryNote(mCurrentCard), () -> UIUtils.showThemedToast(this, R.string.buried_note, true));
    }

    public boolean executeCommand(@NonNull ViewerCommand which) {
        //noinspection ConstantConditions - remove this once we move to kotlin
        if (which == null) {
            Timber.w("command should not be null");
            which = COMMAND_NOTHING;
        }
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
            case COMMAND_LOOKUP:
                lookUpOrSelectText();
                return true;
            case COMMAND_BURY_CARD:
                return buryCard();
            case COMMAND_BURY_NOTE:
                return buryNote();
            case COMMAND_SUSPEND_CARD:
                return suspendCard();
            case COMMAND_SUSPEND_NOTE:
                return suspendNote();
            case COMMAND_DELETE:
                showDeleteNoteDialog();
                return true;
            case COMMAND_PLAY_MEDIA:
                playSounds(true);
                return true;
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

    protected void performClickWithVisualFeedback(int ease) {
        // Delay could potentially be lower - testing with 20 left a visible "click"
        switch (ease) {
            case EASE_1:
                mEaseButton1.performClickWithVisualFeedback();
                break;
            case EASE_2:
                mEaseButton2.performClickWithVisualFeedback();
                break;
            case EASE_3:
                mEaseButton3.performClickWithVisualFeedback();
                break;
            case EASE_4:
                mEaseButton4.performClickWithVisualFeedback();
                break;
        }
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
        mAutomaticAnswer.disable();
        mPreviousAnswerIndicator.stopAutomaticHide();
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

        private final Handler mScrollHandler = HandlerUtils.newHandler();
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

                    mGestureProcessor.onFling(dx, dy, velocityX, velocityY, mIsSelecting, mIsXScrolling, mIsYScrolling);
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
                mGestureProcessor.onDoubleTap();
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

                mGestureProcessor.onTap(height, width, posX, posY);
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
        private final HashSet<MotionEvent> mDesiredTouchEvents = HashUtil.HashSetInit(2);
        /** A list of events we sent to the WebView (to block double-processing) */
        private final HashSet<MotionEvent> mDispatchedTouchEvents = HashUtil.HashSetInit(2);

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

    protected boolean shouldDisplayMark() {
        return NoteService.isMarked(mCurrentCard.note());
    }

    protected <TResult extends Computation<? extends NextCard<?>>> TaskListenerBuilder<Unit, TResult> nextCardHandler() {
        return new TaskListenerBuilder<>(new NextCardHandler<>());
    }

    /**
     * @param dismiss An action to execute, to ignore current card and get another one
     * @return whether the action succeeded.
     */
    protected boolean dismiss(AnkiMethod<Computation<? extends NextCard<?>>> dismiss, Runnable executeAfter) {
        blockControls(false);
        dismiss.runWithHandler(nextCardHandler().alsoExecuteAfter((result) -> executeAfter.run()));
        return true;
    }


    public Lock getWriteLock() {
        return mCardLock.writeLock();
    }


    public WebView getWebView() {
        return mCardWebView;
    }

    public Card getCurrentCard() {
        return mCurrentCard;
    }

    /** Refreshes the WebView after a crash */
    public void destroyWebViewFrame() {
        // Destroy the current WebView (to ensure WebView is GCed).
        // Otherwise, we get the following error:
        // "crash wasn't handled by all associated webviews, triggering application crash"
        mCardFrame.removeAllViews();
        mCardFrameParent.removeView(mCardFrame);
        // destroy after removal from the view - produces logcat warnings otherwise
        destroyWebView(mCardWebView);
        mCardWebView = null;
        // inflate a new instance of mCardFrame
        mCardFrame = inflateNewView(R.id.flashcard);
        // Even with the above, I occasionally saw the above error. Manually trigger the GC.
        // I'll keep this line unless I see another crash, which would point to another underlying issue.
        System.gc();
    }


    public void recreateWebViewFrame() {
        //we need to add at index 0 so gestures still go through.
        mCardFrameParent.addView(mCardFrame, 0);

        recreateWebView();
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
                controlSound(url);
                return true;
            }
            if (url.startsWith("file") || url.startsWith("data:")) {
                return false; // Let the webview load files, i.e. local images.
            }
            if (url.startsWith("typeblurtext:")) {
                // Store the text the javascript has send us…
                mTypeAnswer.setInput(decodeUrl(url.replaceFirst("typeblurtext:", "")));
                // … and show the “SHOW ANSWER” button again.
                mFlipCardLayout.setVisibility(View.VISIBLE);
                return true;
            }
            if (url.startsWith("typeentertext:")) {
                // Store the text the javascript has send us…
                mTypeAnswer.setInput(decodeUrl(url.replaceFirst("typeentertext:", "")));
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
                if (!mAnkiDroidJsAPI.isInit(AnkiDroidJsAPIConstants.MARK_CARD, AnkiDroidJsAPIConstants.ankiJsErrorCodeMarkCard)) {
                    return true;
                }

                executeCommand(COMMAND_MARK);
                return true;
            }
            // flag card (blue, green, orange, red) using javascript from AnkiDroid webview
            if (url.startsWith("signal:flag_")) {
                if (!mAnkiDroidJsAPI.isInit(AnkiDroidJsAPIConstants.TOGGLE_FLAG, AnkiDroidJsAPIConstants.ankiJsErrorCodeFlagCard)) {
                    return true;
                }

                String flag = url.replaceFirst("signal:flag_","");
                switch (flag) {
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

        /**
         * Check if the user clicked on another audio icon or the audio itself finished
         * Also, Check if the user clicked on the running audio icon
         * @param url
         */
        private void controlSound(String url) {
            url = url.replaceFirst("playsound:", "");
            if (!url.equals(mSoundPlayer.getCurrentAudioUri()) || mSoundPlayer.isCurrentAudioFinished()) {
                onCurrentAudioChanged(url);
            } else {
                mSoundPlayer.playOrPauseSound();
            }
        }

        private void onCurrentAudioChanged(String url) {
            mSoundPlayer.playSound(url, null, null, getSoundErrorListener());
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
                Timber.d("New URL, triggering JS onPageFinished: %s", url);
                view.loadUrl("javascript:onPageFinished();");
            }
        }

        @Override
        @TargetApi(Build.VERSION_CODES.O)
        public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
            return mOnRenderProcessGoneDelegate.onRenderProcessGone(view, detail);
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
        return mTypeAnswer.getInput();
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

    @VisibleForTesting
    void loadInitialCard() {
        new SchedulerService.GetCard().runWithHandler(answerCardHandler(false));
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
        return mTypeAnswer.getCorrect();
    }

    @VisibleForTesting
    String getCardContent() {
        return mCardContent;
    }

    protected void showTagsDialog() {
        ArrayList<String> tags = new ArrayList<>(getCol().getTags().all());
        ArrayList<String> selTags = new ArrayList<>(mCurrentCard.note().getTags());
        TagsDialog dialog = mTagsDialogFactory.newTagsDialog().withArguments(TagsDialog.DialogType.EDIT_TAGS, selTags, tags);
        showDialogFragment(dialog);
    }

    @Override
    public void onSelectedTags(List<String> selectedTags, List<String> indeterminateTags, int option) {
        if (!mCurrentCard.note().getTags().equals(selectedTags)) {
            String tagString = TextUtils.join(" ", selectedTags);
            Note note = mCurrentCard.note();
            note.setTagsFromStr(tagString);
            note.flush();
            // Reload current card to reflect tag changes
            displayCardQuestion(true);
        }
    }

    public AnkiDroidJsAPI javaScriptFunction() {
        return new AnkiDroidJsAPI(this);
    }
}
