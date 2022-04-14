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
package com.ichi2.anki

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.content.*
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Color
import android.media.MediaPlayer
import android.net.Uri
import android.os.*
import android.text.TextUtils
import android.view.*
import android.view.GestureDetector.SimpleOnGestureListener
import android.view.View.OnTouchListener
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.*
import android.webkit.WebView.HitTestResult
import android.widget.*
import androidx.annotation.CheckResult
import androidx.annotation.IdRes
import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.webkit.WebViewAssetLoader
import com.afollestad.materialdialogs.DialogAction
import com.afollestad.materialdialogs.MaterialDialog
import com.drakeet.drawer.FullDraggableContainer
import com.google.android.material.snackbar.Snackbar
import com.ichi2.anim.ActivityTransitionAnimation
import com.ichi2.anki.UIUtils.getSnackbar
import com.ichi2.anki.UIUtils.saveCollectionInBackground
import com.ichi2.anki.UIUtils.showThemedToast
import com.ichi2.anki.cardviewer.*
import com.ichi2.anki.cardviewer.CardAppearance.Companion.isInNightMode
import com.ichi2.anki.cardviewer.CardHtml.Companion.legacyGetTtsTags
import com.ichi2.anki.cardviewer.HtmlGenerator.Companion.createInstance
import com.ichi2.anki.cardviewer.SoundPlayer.CardSoundConfig
import com.ichi2.anki.cardviewer.SoundPlayer.CardSoundConfig.Companion.create
import com.ichi2.anki.cardviewer.TypeAnswer.Companion.createInstance
import com.ichi2.anki.dialogs.tags.TagsDialog
import com.ichi2.anki.dialogs.tags.TagsDialogFactory
import com.ichi2.anki.dialogs.tags.TagsDialogListener
import com.ichi2.anki.receiver.SdCardReceiver
import com.ichi2.anki.reviewer.*
import com.ichi2.anki.reviewer.AutomaticAnswer.AutomaticallyAnswered
import com.ichi2.anki.reviewer.FullScreenMode.Companion.DEFAULT
import com.ichi2.anki.reviewer.FullScreenMode.Companion.fromPreference
import com.ichi2.anki.reviewer.ReviewerUi.ControlBlock
import com.ichi2.anki.servicelayer.AnkiMethod
import com.ichi2.anki.servicelayer.LanguageHintService.applyLanguageHint
import com.ichi2.anki.servicelayer.NoteService.isMarked
import com.ichi2.anki.servicelayer.SchedulerService.*
import com.ichi2.anki.servicelayer.TaskListenerBuilder
import com.ichi2.anki.servicelayer.UndoService.Undo
import com.ichi2.async.CollectionTask.PreloadNextCard
import com.ichi2.async.CollectionTask.UpdateNote
import com.ichi2.async.TaskListener
import com.ichi2.async.TaskManager
import com.ichi2.compat.CompatHelper.Companion.compat
import com.ichi2.libanki.*
import com.ichi2.libanki.Collection
import com.ichi2.libanki.Consts.BUTTON_TYPE
import com.ichi2.libanki.Sound.SoundSide
import com.ichi2.libanki.sched.AbstractSched
import com.ichi2.themes.Themes.getResFromAttr
import com.ichi2.ui.FixedEditText
import com.ichi2.utils.AdaptionUtil.hasWebBrowser
import com.ichi2.utils.AndroidUiUtils.isRunningOnTv
import com.ichi2.utils.AssetHelper.guessMimeType
import com.ichi2.utils.ClipboardUtil.getText
import com.ichi2.utils.Computation
import com.ichi2.utils.HandlerUtils.executeFunctionWithDelay
import com.ichi2.utils.HandlerUtils.newHandler
import com.ichi2.utils.HashUtil.HashSetInit
import com.ichi2.utils.KotlinCleanup
import com.ichi2.utils.MaxExecFunction
import com.ichi2.utils.WebViewDebugging.initializeDebugging
import net.ankiweb.rsdroid.RustCleanup
import timber.log.Timber
import java.io.*
import java.lang.ref.WeakReference
import java.net.URLDecoder
import java.util.*
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReadWriteLock
import java.util.concurrent.locks.ReentrantReadWriteLock
import java.util.function.Consumer
import java.util.function.Function
import java.util.function.Supplier
import kotlin.math.abs

@KotlinCleanup("lots to deal with")
abstract class AbstractFlashcardViewer :
    NavigationDrawerActivity(),
    ReviewerUi,
    ViewerCommand.CommandProcessor,
    TagsDialogListener,
    WhiteboardMultiTouchMethods,
    AutomaticallyAnswered,
    OnPageFinishedCallback {
    private var mTtsInitialized = false
    private var mReplayOnTtsInit = false
    private var mAnkiDroidJsAPI: AnkiDroidJsAPI? = null

    /**
     * Broadcast that informs us when the sd card is about to be unmounted
     */
    private var mUnmountReceiver: BroadcastReceiver? = null
    private var mTagsDialogFactory: TagsDialogFactory? = null

    /**
     * Variables to hold preferences
     */
    @KotlinCleanup("internal for AnkiDroidJsApi")
    internal var prefShowTopbar = false
    protected var fullscreenMode = DEFAULT
        private set
    private var mRelativeButtonSize = 0
    private var mDoubleScrolling = false
    private var mScrollingButtons = false
    private var mGesturesEnabled = false
    private var mLargeAnswerButtons = false
    private var mDoubleTapTimeInterval = DEFAULT_DOUBLE_TAP_TIME_INTERVAL

    // Android WebView
    @JvmField
    var mAutomaticAnswer = AutomaticAnswer.defaultInstance(this)
    protected var typeAnswer: TypeAnswer? = null

    /** Generates HTML content  */
    private var mHtmlGenerator: HtmlGenerator? = null

    // Default short animation duration, provided by Android framework
    private var shortAnimDuration = 0
    private var mBackButtonPressedToReturn = false

    // Preferences from the collection
    private var mShowNextReviewTime = false
    private var mIsSelecting = false
    private var mTouchStarted = false
    private var mInAnswer = false
    private var mAnswerSoundsAdded = false

    /**
     * Variables to hold layout objects that we need to update or handle events for
     */
    var webView: WebView? = null
        private set
    private var mCardFrame: FrameLayout? = null
    private var mTouchLayer: FrameLayout? = null
    @JvmField
    protected var mAnswerField: FixedEditText? = null
    protected var flipCardLayout: LinearLayout? = null
    protected var easeButtonsLayout: LinearLayout? = null
    @KotlinCleanup("internal for AnkiDroidJsApi")
    internal var easeButton1: EaseButton? = null
    @KotlinCleanup("internal for AnkiDroidJsApi")
    internal var easeButton2: EaseButton? = null
    @KotlinCleanup("internal for AnkiDroidJsApi")
    internal var easeButton3: EaseButton? = null
    @KotlinCleanup("internal for AnkiDroidJsApi")
    internal var easeButton4: EaseButton? = null
    @JvmField
    protected var mTopBarLayout: RelativeLayout? = null
    private val mClipboard: ClipboardManager? = null
    private var mPreviousAnswerIndicator: PreviousAnswerIndicator? = null

    /** set via [.setCurrentCard]  */
    @JvmField
    var mCurrentCard: Card? = null
    // set when mCurrentCard is
    private var mCardSoundConfig: CardSoundConfig? = null
    private var mCurrentEase = 0
    private var mInitialFlipCardHeight = 0
    private var mButtonHeightSet = false

    /**
     * A record of the last time the "show answer" or ease buttons were pressed. We keep track
     * of this time to ignore accidental button presses.
     */
    @JvmField
    @VisibleForTesting
    protected var mLastClickTime: Long = 0

    /**
     * Swipe Detection
     */
    var gestureDetector: GestureDetector? = null
        private set
    private var mGestureDetectorImpl: MyGestureDetector? = null
    private var mIsXScrolling = false
    private var mIsYScrolling = false

    /**
     * Gesture Allocation
     */
    protected val mGestureProcessor = GestureProcessor(this)

    @get:VisibleForTesting
    var cardContent: String? = null
        private set
    private var mBaseUrl: String? = null
    private var mViewerUrl: String? = null
    private var mAssetLoader: WebViewAssetLoader? = null
    private val mFadeDuration = 300
    @KotlinCleanup("made internal for tests")
    @VisibleForTesting(otherwise = VisibleForTesting.PROTECTED)
    internal var sched: AbstractSched? = null
    protected val mSoundPlayer = Sound()

    /**
     * Time taken to play all medias in mSoundPlayer
     * This is 0 if we have "Read card" enabled, as we can't calculate the duration.
     */
    private var mUseTimerDynamicMS: Long = 0

    /** Reference to the parent of the cardFrame to allow regeneration of the cardFrame in case of crash  */
    private var mCardFrameParent: ViewGroup? = null

    /** Lock to allow thread-safe regeneration of mCard  */
    private val mCardLock: ReadWriteLock = ReentrantReadWriteLock()

    /** whether controls are currently blocked, and how long we expect them to be  */
    override var controlBlocked = ControlBlock.SLOW

    /** Preference: Whether the user wants press back twice to return to the main screen"  */
    private var mExitViaDoubleTapBack = false

    @JvmField
    @VisibleForTesting
    val mOnRenderProcessGoneDelegate = OnRenderProcessGoneDelegate(this)
    protected val mTTS = TTS()

    // ----------------------------------------------------------------------------
    // LISTENERS
    // ----------------------------------------------------------------------------
    private val mLongClickHandler = newHandler()
    private val mLongClickTestRunnable = Runnable {
        Timber.i("AbstractFlashcardViewer:: onEmulatedLongClick")
        compat.vibrate(AnkiDroidApp.getInstance().applicationContext, 50)
        mLongClickHandler.postDelayed(mStartLongClickAction, 300)
    }
    private val mStartLongClickAction = Runnable { mGestureProcessor.onLongTap() }

    // Handler for the "show answer" button
    private val mFlipCardListener = View.OnClickListener {
        Timber.i("AbstractFlashcardViewer:: Show answer button pressed")
        // Ignore what is most likely an accidental double-tap.
        if (elapsedRealTime - mLastClickTime < mDoubleTapTimeInterval) {
            return@OnClickListener
        }
        mLastClickTime = elapsedRealTime
        mAutomaticAnswer.onShowAnswer()
        displayCardAnswer()
    }

    // Event handler for eases (answer buttons)
    inner class SelectEaseHandler : View.OnClickListener, OnTouchListener {
        private var mPrevCard: Card? = null
        private var mHasBeenTouched = false
        private var mTouchX = 0f
        private var mTouchY = 0f
        override fun onTouch(view: View, event: MotionEvent): Boolean {
            if (event.action == MotionEvent.ACTION_DOWN) {
                // Save states when button pressed
                mPrevCard = mCurrentCard
                mHasBeenTouched = true
                // We will need to check if a touch is followed by a click
                // Since onTouch always come before onClick, we should check if
                // the touch is going to be a click by storing the start coordinates
                // and comparing with the end coordinates of the touch
                mTouchX = event.rawX
                mTouchY = event.rawY
            } else if (event.action == MotionEvent.ACTION_UP) {
                val diffX = abs(event.rawX - mTouchX)
                val diffY = abs(event.rawY - mTouchY)
                // If a click is not coming then we reset the touch
                if (diffX > Companion.CLICK_ACTION_THRESHOLD || diffY > Companion.CLICK_ACTION_THRESHOLD) {
                    mHasBeenTouched = false
                }
            }
            return false
        }

        override fun onClick(view: View) {
            // Try to perform intended action only if the button has been pressed for current card,
            // or if the button was not touched,
            if (mPrevCard === mCurrentCard || !mHasBeenTouched) {
                // Only perform if the click was not an accidental double-tap
                if (elapsedRealTime - mLastClickTime >= mDoubleTapTimeInterval) {
                    // For whatever reason, performClick does not return a visual feedback anymore
                    if (!mHasBeenTouched) {
                        view.isPressed = true
                    }
                    mLastClickTime = elapsedRealTime
                    mAutomaticAnswer.onSelectEase()
                    when (view.id) {
                        R.id.flashcard_layout_ease1 -> {
                            Timber.i("AbstractFlashcardViewer:: EASE_1 pressed")
                            answerCard(Consts.BUTTON_ONE)
                        }
                        R.id.flashcard_layout_ease2 -> {
                            Timber.i("AbstractFlashcardViewer:: EASE_2 pressed")
                            answerCard(Consts.BUTTON_TWO)
                        }
                        R.id.flashcard_layout_ease3 -> {
                            Timber.i("AbstractFlashcardViewer:: EASE_3 pressed")
                            answerCard(Consts.BUTTON_THREE)
                        }
                        R.id.flashcard_layout_ease4 -> {
                            Timber.i("AbstractFlashcardViewer:: EASE_4 pressed")
                            answerCard(Consts.BUTTON_FOUR)
                        }
                        else -> mCurrentEase = 0
                    }
                    if (!mHasBeenTouched) {
                        view.isPressed = false
                    }
                }
            }
            // We will have to reset the touch after every onClick event
            // Do not return early without considering this
            mHasBeenTouched = false
        }
    }

    private val mEaseHandler = SelectEaseHandler()

    @get:VisibleForTesting
    protected open val elapsedRealTime: Long
        get() = SystemClock.elapsedRealtime()
    private val mGestureListener = OnTouchListener { _, event ->
        if (gestureDetector!!.onTouchEvent(event)) {
            return@OnTouchListener true
        }
        if (!mGestureDetectorImpl!!.eventCanBeSentToWebView(event)) {
            return@OnTouchListener false
        }
        // Gesture listener is added before mCard is set
        processCardAction { cardWebView: WebView? ->
            if (cardWebView == null) return@processCardAction
            cardWebView.dispatchTouchEvent(event)
        }
        false
    }

    // This is intentionally package-private as it removes the need for synthetic accessors
    @SuppressLint("CheckResult")
    fun processCardAction(cardConsumer: Consumer<WebView?>) {
        processCardFunction { cardWebView: WebView? ->
            cardConsumer.accept(cardWebView)
            true
        }
    }

    @CheckResult
    private fun <T> processCardFunction(cardFunction: Function<WebView?, T>): T {
        val readLock = mCardLock.readLock()
        return try {
            readLock.lock()
            cardFunction.apply(webView)
        } finally {
            readLock.unlock()
        }
    }

    private val mUpdateCardHandler: TaskListener<Card, Computation<*>> = object : TaskListener<Card, Computation<*>>() {
        private var mNoMoreCards = false
        override fun onPreExecute() {
            showProgressBar()
        }

        override fun onProgressUpdate(value: Card) {
            if (mCurrentCard !== value) {
                /*
                 * Before updating mCurrentCard, we check whether it is changing or not. If the current card changes,
                 * then we need to display it as a new card, without showing the answer.
                 */
                sDisplayAnswer = false
            }
            currentCard = value
            TaskManager.launchCollectionTask(PreloadNextCard()) // Tasks should always be launched from GUI. So in
            // listener and not in background
            if (mCurrentCard == null) {
                // If the card is null means that there are no more cards scheduled for review.
                mNoMoreCards = true
                showProgressBar()
                return
            }
            onCardEdited(mCurrentCard)
            if (sDisplayAnswer) {
                mSoundPlayer.resetSounds() // load sounds from scratch, to expose any edit changes
                mAnswerSoundsAdded = false // causes answer sounds to be reloaded
                generateQuestionSoundList() // questions must be intentionally regenerated
                displayCardAnswer()
            } else {
                displayCardQuestion()
            }
            hideProgressBar()
        }

        override fun onPostExecute(result: Computation<*>) {
            if (!result.succeeded()) {
                // RuntimeException occurred on update cards
                closeReviewer(DeckPicker.RESULT_DB_ERROR, false)
                return
            }
            if (mNoMoreCards) {
                closeReviewer(RESULT_NO_MORE_CARDS, true)
            }
        }
    }

    @KotlinCleanup("nullability")
    /** Operation after a card has been updated due to being edited. Called before display[Question/Answer]  */
    protected open fun onCardEdited(card: Card?) {
        // intentionally blank
    }

    /** Invoked by [CardViewerWebClient.onPageFinished] */
    override fun onPageFinished() {
        // intentionally blank
    }

    internal inner class NextCardHandler<Result : Computation<NextCard<*>>> :
        TaskListener<Unit, Result>() {
        override fun onPreExecute() {
            dealWithTimeBox()
        }

        @KotlinCleanup("remove _ variables")
        private fun dealWithTimeBox() {
            val res = resources
            val elapsed = col.timeboxReached()
            if (elapsed != null) {
                val nCards = elapsed.second
                val nMins = elapsed.first / 60
                val mins = res.getQuantityString(R.plurals.in_minutes, nMins, nMins)
                val timeboxMessage = res.getQuantityString(R.plurals.timebox_reached, nCards, nCards, mins)
                MaterialDialog.Builder(this@AbstractFlashcardViewer)
                    .title(res.getString(R.string.timebox_reached_title))
                    .content(timeboxMessage)
                    .positiveText(R.string.dialog_continue)
                    .negativeText(R.string.close)
                    .cancelable(true)
                    .onNegative { _: MaterialDialog?, _: DialogAction? -> finishWithAnimation(ActivityTransitionAnimation.Direction.END) }
                    .onPositive { _: MaterialDialog?, _: DialogAction? -> col.startTimebox() }
                    .cancelListener { col.startTimebox() }
                    .show()
            }
        }

        override fun onPostExecute(result: Result) {
            if (sched == null) {
                // TODO: proper testing for restored activity
                finishWithoutAnimation()
                return
            }
            val displaySuccess = result.succeeded()
            if (!displaySuccess) {
                // RuntimeException occurred on answering cards
                closeReviewer(DeckPicker.RESULT_DB_ERROR, false)
                return
            }
            val nextCardAndResult = result.value
            if (nextCardAndResult.hasNoMoreCards()) {
                closeReviewer(RESULT_NO_MORE_CARDS, true)
                return
            }
            currentCard = nextCardAndResult.nextScheduledCard()

            // Start reviewing next card
            hideProgressBar()
            unblockControls()
            this@AbstractFlashcardViewer.displayCardQuestion()
            // set the correct mark/unmark icon on action bar
            refreshActionBar()
            focusDefaultLayout()
        }
    }

    private fun focusDefaultLayout() {
        if (!isRunningOnTv(this)) {
            findViewById<View>(R.id.root_layout).requestFocus()
        } else {
            val flip = findViewById<View>(R.id.answer_options_layout) ?: return
            Timber.d("Requesting focus for flip button")
            flip.requestFocus()
        }
    }

    protected fun answerCardHandler(quick: Boolean): TaskListenerBuilder<Unit, Computation<NextCard<*>>> {
        return nextCardHandler<Computation<NextCard<*>>>()
            .alsoExecuteBefore { blockControls(quick) }
    }

    open val answerButtonCount: Int
        get() = col.sched.answerButtons(mCurrentCard!!)

    // ----------------------------------------------------------------------------
    // ANDROID METHODS
    // ----------------------------------------------------------------------------
    override fun onCreate(savedInstanceState: Bundle?) {
        Timber.d("onCreate()")
        restorePreferences()
        mTagsDialogFactory = TagsDialogFactory(this).attachToActivity<TagsDialogFactory>(this)
        super.onCreate(savedInstanceState)
        setContentView(getContentViewAttr(fullscreenMode))

        // Make ACTION_PROCESS_TEXT for in-app searching possible on > Android 4.0
        delegate.isHandleNativeActionModesEnabled = true
        val mainView = findViewById<View>(android.R.id.content)
        initNavigationDrawer(mainView)
        mPreviousAnswerIndicator = PreviousAnswerIndicator(findViewById(R.id.chosen_answer))
        shortAnimDuration = resources.getInteger(android.R.integer.config_shortAnimTime)
    }

    @KotlinCleanup("non-null")
    protected open fun getContentViewAttr(fullscreenMode: FullScreenMode?): Int {
        return R.layout.reviewer
    }

    @get:VisibleForTesting(otherwise = VisibleForTesting.PROTECTED)
    val isFullscreen: Boolean
        get() = !supportActionBar!!.isShowing

    override fun onConfigurationChanged(config: Configuration) {
        // called when screen rotated, etc, since recreating the Webview is too expensive
        super.onConfigurationChanged(config)
        refreshActionBar()
    }

    protected abstract fun setTitle()

    // Finish initializing the activity after the collection has been correctly loaded
    @Suppress("deprecation") // supportInvalidateOptionsMenu
    public override fun onCollectionLoaded(col: Collection) {
        super.onCollectionLoaded(col)
        sched = col.sched
        val mediaDir = col.media.dir()
        mBaseUrl = Utils.getBaseUrl(mediaDir)
        mViewerUrl = mBaseUrl + "__viewer__.html"
        mAssetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/") { path: String ->
                try {
                    val file = File(mediaDir, path)
                    val `is` = FileInputStream(file)
                    val mimeType = guessMimeType(path)
                    val headers = HashMap<String, String>()
                    headers["Access-Control-Allow-Origin"] = "*"
                    val response = WebResourceResponse(mimeType, null, `is`)
                    response.responseHeaders = headers
                    return@addPathHandler response
                } catch (e: Exception) {
                    Timber.w(e, "Error trying to open path in asset loader")
                }
                null
            }
            .build()
        registerExternalStorageListener()
        restoreCollectionPreferences(col)
        initLayout()
        setTitle()
        mHtmlGenerator = createInstance(this, typeAnswer!!, mBaseUrl!!)

        // Initialize text-to-speech. This is an asynchronous operation.
        mTTS.initialize(this, ReadTextListener())
        updateActionBar()
        supportInvalidateOptionsMenu()
    }

    // Saves deck each time Reviewer activity loses focus
    override fun onPause() {
        super.onPause()
        Timber.d("onPause()")
        mAutomaticAnswer.disable()
        mLongClickHandler.removeCallbacks(mLongClickTestRunnable)
        mLongClickHandler.removeCallbacks(mStartLongClickAction)
        mSoundPlayer.stopSounds()

        // Prevent loss of data in Cookies
        CookieManager.getInstance().flush()
    }

    override fun onResume() {
        super.onResume()
        // Set the context for the Sound manager
        mSoundPlayer.setContext(WeakReference(this))
        mAutomaticAnswer.enable()
        // Reset the activity title
        setTitle()
        updateActionBar()
        selectNavigationItem(-1)
    }

    override fun onDestroy() {
        super.onDestroy()
        // Tells the scheduler there is no more current cards. 0 is
        // not a valid id.
        if (sched != null) {
            sched!!.discardCurrentCard()
        }
        Timber.d("onDestroy()")
        mTTS.releaseTts(this)
        if (mUnmountReceiver != null) {
            unregisterReceiver(mUnmountReceiver)
        }
        // WebView.destroy() should be called after the end of use
        // http://developer.android.com/reference/android/webkit/WebView.html#destroy()
        if (mCardFrame != null) {
            mCardFrame!!.removeAllViews()
        }
        destroyWebView(webView) // OK to do without a lock
    }

    override fun onBackPressed() {
        if (isDrawerOpen) {
            super.onBackPressed()
        } else {
            Timber.i("Back key pressed")
            if (!mExitViaDoubleTapBack || mBackButtonPressedToReturn) {
                closeReviewer(RESULT_DEFAULT, false)
            } else {
                showThemedToast(this, getString(R.string.back_pressed_once_reviewer), true)
            }
            mBackButtonPressedToReturn = true
            executeFunctionWithDelay(Consts.SHORT_TOAST_DURATION) { mBackButtonPressedToReturn = false }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        return if (processCardFunction { cardWebView: WebView? -> processHardwareButtonScroll(keyCode, cardWebView) }) {
            true
        } else super.onKeyDown(keyCode, event)
    }

    public override fun getCurrentCardId(): Long? {
        return if (mCurrentCard == null) {
            null
        } else mCurrentCard!!.id
    }

    private fun processHardwareButtonScroll(keyCode: Int, card: WebView?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_PAGE_UP) {
            card!!.pageUp(false)
            if (mDoubleScrolling) {
                card.pageUp(false)
            }
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_PAGE_DOWN) {
            card!!.pageDown(false)
            if (mDoubleScrolling) {
                card.pageDown(false)
            }
            return true
        }
        if (mScrollingButtons && keyCode == KeyEvent.KEYCODE_PICTSYMBOLS) {
            card!!.pageUp(false)
            if (mDoubleScrolling) {
                card.pageUp(false)
            }
            return true
        }
        if (mScrollingButtons && keyCode == KeyEvent.KEYCODE_SWITCH_CHARSET) {
            card!!.pageDown(false)
            if (mDoubleScrolling) {
                card.pageDown(false)
            }
            return true
        }
        return false
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (answerFieldIsFocused()) {
            return super.onKeyUp(keyCode, event)
        }
        if (!sDisplayAnswer) {
            if (keyCode == KeyEvent.KEYCODE_SPACE || keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) {
                displayCardAnswer()
                return true
            }
        }
        return super.onKeyUp(keyCode, event)
    }

    protected open fun answerFieldIsFocused(): Boolean {
        return mAnswerField != null && mAnswerField!!.isFocused
    }

    protected fun clipboardHasText(): Boolean {
        return !TextUtils.isEmpty(getText(mClipboard))
    }

    /**
     * Returns the text stored in the clipboard or the empty string if the clipboard is empty or contains something that
     * cannot be converted to text.
     *
     * @return the text in clipboard or the empty string.
     */
    private fun clipboardGetText(): CharSequence {
        val text = getText(mClipboard)
        return text ?: ""
    }

    @Suppress("deprecation") // super.onActivityResult
    public override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == DeckPicker.RESULT_DB_ERROR) {
            closeReviewer(DeckPicker.RESULT_DB_ERROR, false)
        }
        if (resultCode == DeckPicker.RESULT_MEDIA_EJECTED) {
            finishNoStorageAvailable()
        }

        /* Reset the schedule and reload the latest card off the top of the stack if required.
           The card could have been rescheduled, the deck could have changed, or a change of
           note type could have lead to the card being deleted */
        if (data != null && data.hasExtra("reloadRequired")) {
            performReload()
        }
        if (requestCode == EDIT_CURRENT_CARD) {
            if (resultCode == RESULT_OK) {
                // content of note was changed so update the note and current card
                Timber.i("AbstractFlashcardViewer:: Saving card...")
                TaskManager.launchCollectionTask(
                    UpdateNote(editorCard, true, canAccessScheduler()),
                    mUpdateCardHandler
                )
                onEditedNoteChanged()
            } else if (resultCode == RESULT_CANCELED && !(data != null && data.hasExtra("reloadRequired"))) {
                // nothing was changed by the note editor so just redraw the card
                redrawCard()
            }
        } else if (requestCode == DECK_OPTIONS && resultCode == RESULT_OK) {
            performReload()
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
    open fun canAccessScheduler(): Boolean {
        return false
    }

    protected open fun onEditedNoteChanged() {}

    /** An action which may invalidate the current list of cards has been performed  */
    protected abstract fun performReload()

    // ----------------------------------------------------------------------------
    // CUSTOM METHODS
    // ----------------------------------------------------------------------------
    // Get the did of the parent deck (ignoring any subdecks)
    protected val parentDid: Long
        get() = col.decks.selected()

    private fun redrawCard() {
        // #3654 We can call this from ActivityResult, which could mean that the card content hasn't yet been set
        // if the activity was destroyed. In this case, just wait until onCollectionLoaded callback succeeds.
        if (hasLoadedCardContent()) {
            fillFlashcard()
        } else {
            Timber.i("Skipping card redraw - card still initialising.")
        }
    }

    /** Whether the callback to onCollectionLoaded has loaded card content  */
    private fun hasLoadedCardContent(): Boolean {
        return cardContent != null
    }

    /**
     * Show/dismiss dialog when sd card is ejected/remounted (collection is saved by SdCardReceiver)
     */
    private fun registerExternalStorageListener() {
        if (mUnmountReceiver == null) {
            mUnmountReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    if (intent.action == SdCardReceiver.MEDIA_EJECT) {
                        finishWithoutAnimation()
                    }
                }
            }
            val iFilter = IntentFilter()
            iFilter.addAction(SdCardReceiver.MEDIA_EJECT)
            registerReceiver(mUnmountReceiver, iFilter)
        }
    }

    open fun undo() {
        if (isUndoAvailable) {
            val res = resources
            val undoName = col.undoName(res)
            Undo().runWithHandler(
                answerCardHandler(false)
                    .alsoExecuteAfter {
                        showThemedToast(
                            this@AbstractFlashcardViewer,
                            res.getString(R.string.undo_succeeded, undoName),
                            true
                        )
                    }
            )
        }
    }

    private fun finishNoStorageAvailable() {
        this@AbstractFlashcardViewer.setResult(DeckPicker.RESULT_MEDIA_EJECTED)
        finishWithoutAnimation()
    }

    protected open fun editCard() {
        if (mCurrentCard == null) {
            // This should never occurs. It means the review button was pressed while there is no more card in the reviewer.
            return
        }
        val editCard = Intent(this@AbstractFlashcardViewer, NoteEditor::class.java)
        editCard.putExtra(NoteEditor.EXTRA_CALLER, NoteEditor.CALLER_REVIEWER)
        editorCard = mCurrentCard
        startActivityForResultWithAnimation(editCard, EDIT_CURRENT_CARD, ActivityTransitionAnimation.Direction.START)
    }

    fun generateQuestionSoundList() {
        val tags = Sound.extractTagsFromLegacyContent(mCurrentCard!!.qSimple())
        mSoundPlayer.addSounds(mBaseUrl, tags, SoundSide.QUESTION)
    }

    @KotlinCleanup("remove _ variables")
    protected fun showDeleteNoteDialog() {
        val res = resources
        MaterialDialog.Builder(this)
            .title(res.getString(R.string.delete_card_title))
            .iconAttr(R.attr.dialogErrorIcon)
            .content(
                res.getString(
                    R.string.delete_note_message,
                    Utils.stripHTML(mCurrentCard!!.q(true))
                )
            )
            .positiveText(R.string.dialog_positive_delete)
            .negativeText(R.string.dialog_cancel)
            .onPositive { _: MaterialDialog?, _: DialogAction? ->
                Timber.i("AbstractFlashcardViewer:: OK button pressed to delete note %d", mCurrentCard!!.nid)
                mSoundPlayer.stopSounds()
                deleteNoteWithoutConfirmation()
            }
            .build().show()
    }

    /** Consumers should use [.showDeleteNoteDialog]   */
    private fun deleteNoteWithoutConfirmation() {
        dismiss(DeleteNote(mCurrentCard!!)) { showThemedToast(this, R.string.deleted_note, true) }
    }

    private fun getRecommendedEase(easy: Boolean): Int {
        return try {
            when (answerButtonCount) {
                2 -> EASE_2
                3 -> if (easy) EASE_3 else EASE_2
                4 -> if (easy) EASE_4 else EASE_3
                else -> 0
            }
        } catch (e: RuntimeException) {
            AnkiDroidApp.sendExceptionReport(e, "AbstractReviewer-getRecommendedEase")
            closeReviewer(DeckPicker.RESULT_DB_ERROR, true)
            0
        }
    }

    open fun answerCard(@BUTTON_TYPE ease: Int) {
        if (mInAnswer) {
            return
        }
        mIsSelecting = false
        val buttonNumber = col.sched.answerButtons(mCurrentCard!!)
        // Detect invalid ease for current card (e.g. by using keyboard shortcut or gesture).
        if (buttonNumber < ease) {
            return
        }
        // Temporarily sets the answer indicator dots appearing below the toolbar
        mPreviousAnswerIndicator!!.displayAnswerIndicator(ease, buttonNumber)
        mSoundPlayer.stopSounds()
        mCurrentEase = ease
        AnswerAndGetCard(mCurrentCard!!, mCurrentEase).runWithHandler(answerCardHandler(true))
    }

    // Set the content view to the one provided and initialize accessors.
    @KotlinCleanup("Move a lot of these to onCreate()")
    protected open fun initLayout() {
        val cardContainer = findViewById<FrameLayout>(R.id.flashcard_frame)
        mTopBarLayout = findViewById(R.id.top_bar)
        mCardFrame = findViewById(R.id.flashcard)
        mCardFrameParent = mCardFrame!!.parent as ViewGroup
        mTouchLayer = findViewById(R.id.touch_layer)
        mTouchLayer!!.setOnTouchListener(mGestureListener)
        mCardFrame!!.removeAllViews()

        // Initialize swipe
        mGestureDetectorImpl = LinkDetectingGestureDetector()
        gestureDetector = GestureDetector(this, mGestureDetectorImpl)
        easeButtonsLayout = findViewById(R.id.ease_buttons)
        easeButton1 = EaseButton(EASE_1, findViewById(R.id.flashcard_layout_ease1), findViewById(R.id.ease1), findViewById(R.id.nextTime1))
        easeButton1!!.setListeners(mEaseHandler)
        easeButton2 = EaseButton(EASE_2, findViewById(R.id.flashcard_layout_ease2), findViewById(R.id.ease2), findViewById(R.id.nextTime2))
        easeButton2!!.setListeners(mEaseHandler)
        easeButton3 = EaseButton(EASE_3, findViewById(R.id.flashcard_layout_ease3), findViewById(R.id.ease3), findViewById(R.id.nextTime3))
        easeButton3!!.setListeners(mEaseHandler)
        easeButton4 = EaseButton(EASE_4, findViewById(R.id.flashcard_layout_ease4), findViewById(R.id.ease4), findViewById(R.id.nextTime4))
        easeButton4!!.setListeners(mEaseHandler)
        if (!mShowNextReviewTime) {
            easeButton1!!.hideNextReviewTime()
            easeButton2!!.hideNextReviewTime()
            easeButton3!!.hideNextReviewTime()
            easeButton4!!.hideNextReviewTime()
        }
        val flipCard = findViewById<Button>(R.id.flip_card)
        flipCardLayout = findViewById(R.id.flashcard_layout_flip)
        flipCardLayout!!.setOnClickListener(mFlipCardListener)
        if (animationEnabled()) {
            flipCard.setBackgroundResource(getResFromAttr(this, R.attr.hardButtonRippleRef))
        }
        if (!mButtonHeightSet && mRelativeButtonSize != 100) {
            val params = flipCardLayout!!.layoutParams
            params.height = params.height * mRelativeButtonSize / 100
            easeButton1!!.setButtonScale(mRelativeButtonSize)
            easeButton2!!.setButtonScale(mRelativeButtonSize)
            easeButton3!!.setButtonScale(mRelativeButtonSize)
            easeButton4!!.setButtonScale(mRelativeButtonSize)
            mButtonHeightSet = true
        }
        mInitialFlipCardHeight = flipCardLayout!!.layoutParams.height
        if (mLargeAnswerButtons) {
            val params = flipCardLayout!!.layoutParams
            params.height = mInitialFlipCardHeight * 2
        }
        mAnswerField = findViewById(R.id.answer_field)
        initControls()

        // Position answer buttons
        val answerButtonsPosition = AnkiDroidApp.getSharedPrefs(this).getString(
            getString(R.string.answer_buttons_position_preference),
            "bottom"
        )
        val answerArea = findViewById<LinearLayout>(R.id.bottom_area_layout)
        val answerAreaParams = answerArea.layoutParams as RelativeLayout.LayoutParams
        val cardContainerParams = cardContainer.layoutParams as RelativeLayout.LayoutParams
        when (answerButtonsPosition) {
            "top" -> {
                cardContainerParams.addRule(RelativeLayout.BELOW, R.id.bottom_area_layout)
                answerAreaParams.addRule(RelativeLayout.BELOW, R.id.mic_tool_bar_layer)
                answerArea.removeView(mAnswerField)
                answerArea.addView(mAnswerField, 1)
                answerArea.visibility = View.VISIBLE
            }
            "bottom" -> {
                cardContainerParams.addRule(RelativeLayout.ABOVE, R.id.bottom_area_layout)
                cardContainerParams.addRule(RelativeLayout.BELOW, R.id.mic_tool_bar_layer)
                answerAreaParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
                answerArea.visibility = View.VISIBLE
            }
            "none" -> answerArea.visibility = View.GONE
            else -> Timber.w("Unknown answerButtonsPosition: %s", answerButtonsPosition)
        }
        answerArea.layoutParams = answerAreaParams
        cardContainer.layoutParams = cardContainerParams
    }

    @SuppressLint("SetJavaScriptEnabled") // they request we review carefully because of XSS security, we have
    @KotlinCleanup("return non-null")
    protected open fun createWebView(): WebView? {
        val webView: WebView = MyWebView(this)
        webView.scrollBarStyle = View.SCROLLBARS_OUTSIDE_OVERLAY
        webView.settings.displayZoomControls = false
        webView.settings.builtInZoomControls = true
        webView.settings.setSupportZoom(true)
        // Start at the most zoomed-out level
        webView.settings.loadWithOverviewMode = true
        webView.settings.javaScriptEnabled = true
        webView.webChromeClient = AnkiDroidWebChromeClient()

        // This setting toggles file system access within WebView
        // The default configuration is already true in apps targeting API <= 29
        // Hence, this setting is only useful for apps targeting API >= 30
        webView.settings.allowFileAccess = true

        // Problems with focus and input tags is the reason we keep the old type answer mechanism for old Androids.
        webView.isFocusableInTouchMode = typeAnswer!!.useInputTag
        webView.isScrollbarFadingEnabled = true
        Timber.d("Focusable = %s, Focusable in touch mode = %s", webView.isFocusable, webView.isFocusableInTouchMode)
        webView.webViewClient = CardViewerWebClient(mAssetLoader, this)
        // Set transparent color to prevent flashing white when night mode enabled
        webView.setBackgroundColor(Color.argb(1, 0, 0, 0))

        // Javascript interface for calling AnkiDroid functions in webview, see card.js
        mAnkiDroidJsAPI = javaScriptFunction()
        webView.addJavascriptInterface(mAnkiDroidJsAPI!!, "AnkiDroidJS")
        return webView
    }

    /** If a card is displaying the question, flip it, otherwise answer it  */
    private fun flipOrAnswerCard(cardOrdinal: Int) {
        if (!sDisplayAnswer) {
            displayCardAnswer()
            return
        }
        performClickWithVisualFeedback(cardOrdinal)
    }

    // #5780 - Users could OOM the WebView Renderer. This triggers the same symptoms
    @VisibleForTesting
    fun crashWebViewRenderer() {
        loadUrlInViewer("chrome://crash")
    }

    /** Used to set the "javascript:" URIs for IPC  */
    private fun loadUrlInViewer(url: String) {
        processCardAction { cardWebView: WebView? -> cardWebView!!.loadUrl(url) }
    }

    private fun <T : View?> inflateNewView(@IdRes id: Int): T {
        val layoutId = getContentViewAttr(fullscreenMode)
        val content = LayoutInflater.from(this@AbstractFlashcardViewer).inflate(layoutId, null, false) as ViewGroup
        val ret: T = content.findViewById(id)
        (ret!!.parent as ViewGroup).removeView(ret) // detach the view from its parent
        content.removeAllViews()
        return ret
    }

    private fun destroyWebView(webView: WebView?) {
        try {
            if (webView != null) {
                webView.stopLoading()
                webView.webChromeClient = null
                webView.destroy()
            }
        } catch (npe: NullPointerException) {
            Timber.e(npe, "WebView became null on destruction")
        }
    }

    protected fun shouldShowNextReviewTime(): Boolean {
        return mShowNextReviewTime
    }

    protected open fun displayAnswerBottomBar() {
        flipCardLayout!!.isClickable = false
        easeButtonsLayout!!.visibility = View.VISIBLE
        if (mLargeAnswerButtons) {
            easeButtonsLayout!!.orientation = LinearLayout.VERTICAL
            easeButtonsLayout!!.removeAllViewsInLayout()
            easeButton1!!.detachFromParent()
            easeButton2!!.detachFromParent()
            easeButton3!!.detachFromParent()
            easeButton4!!.detachFromParent()
            val row1 = LinearLayout(baseContext)
            row1.orientation = LinearLayout.HORIZONTAL
            val row2 = LinearLayout(baseContext)
            row2.orientation = LinearLayout.HORIZONTAL
            when (answerButtonCount) {
                2 -> {
                    easeButton1!!.height = mInitialFlipCardHeight * 2
                    easeButton2!!.height = mInitialFlipCardHeight * 2
                    easeButton1!!.addTo(row2)
                    easeButton2!!.addTo(row2)
                    easeButtonsLayout!!.addView(row2)
                }
                3 -> {
                    easeButton3!!.addTo(row1)
                    easeButton1!!.addTo(row2)
                    easeButton2!!.addTo(row2)
                    val params: ViewGroup.LayoutParams
                    params = LinearLayout.LayoutParams(Resources.getSystem().displayMetrics.widthPixels / 2, easeButton4!!.height)
                    params.marginStart = Resources.getSystem().displayMetrics.widthPixels / 2
                    row1.layoutParams = params
                    easeButtonsLayout!!.addView(row1)
                    easeButtonsLayout!!.addView(row2)
                }
                else -> {
                    easeButton2!!.addTo(row1)
                    easeButton4!!.addTo(row1)
                    easeButton1!!.addTo(row2)
                    easeButton3!!.addTo(row2)
                    easeButtonsLayout!!.addView(row1)
                    easeButtonsLayout!!.addView(row2)
                }
            }
        }
        val after = Runnable { flipCardLayout!!.visibility = View.GONE }

        // hide "Show Answer" button
        if (animationDisabled()) {
            after.run()
        } else {
            flipCardLayout!!.alpha = 1f
            flipCardLayout!!.animate().alpha(0f).setDuration(shortAnimDuration.toLong()).withEndAction(after)
        }
    }

    protected open fun hideEaseButtons() {
        val after = Runnable { actualHideEaseButtons() }
        val easeButtonsVisible = easeButtonsLayout!!.visibility == View.VISIBLE
        flipCardLayout!!.isClickable = true
        flipCardLayout!!.visibility = View.VISIBLE
        if (animationDisabled() || !easeButtonsVisible) {
            after.run()
        } else {
            flipCardLayout!!.alpha = 0f
            flipCardLayout!!.animate().alpha(1f).setDuration(shortAnimDuration.toLong()).withEndAction(after)
        }
        focusAnswerCompletionField()
    }

    private fun actualHideEaseButtons() {
        easeButtonsLayout!!.visibility = View.GONE
        easeButton1!!.hide()
        easeButton2!!.hide()
        easeButton3!!.hide()
        easeButton4!!.hide()
    }

    /**
     * Focuses the appropriate field for an answer
     * And allows keyboard shortcuts to go to the default handlers.
     */
    private fun focusAnswerCompletionField() {
        // This does not handle mUseInputTag (the WebView contains an input field with a typable answer).
        // In this case, the user can use touch to focus the field if necessary.
        if (typeAnswer!!.autoFocusEditText()) {
            mAnswerField!!.focusWithKeyboard()
        } else {
            flipCardLayout!!.requestFocus()
        }
    }

    protected open fun switchTopBarVisibility(visible: Int) {
        mPreviousAnswerIndicator!!.setVisibility(visible)
    }

    @KotlinCleanup("collapse _ variables")
    protected open fun initControls() {
        mCardFrame!!.visibility = View.VISIBLE
        mPreviousAnswerIndicator!!.setVisibility(View.VISIBLE)
        flipCardLayout!!.visibility = View.VISIBLE
        mAnswerField!!.visibility = if (typeAnswer!!.validForEditText()) View.VISIBLE else View.GONE
        mAnswerField!!.setOnEditorActionListener { _: TextView?, actionId: Int, _: KeyEvent? ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                displayCardAnswer()
                return@setOnEditorActionListener true
            }
            false
        }
        mAnswerField!!.setOnKeyListener { _: View?, keyCode: Int, event: KeyEvent ->
            if (event.action == KeyEvent.ACTION_UP &&
                (keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER)
            ) {
                displayCardAnswer()
                return@setOnKeyListener true
            }
            false
        }
    }

    @KotlinCleanup("make non-null")
    protected open fun restorePreferences(): SharedPreferences? {
        val preferences = AnkiDroidApp.getSharedPrefs(baseContext)
        typeAnswer = createInstance(preferences)
        // mDeckFilename = preferences.getString("deckFilename", "");
        fullscreenMode = fromPreference(preferences)
        mRelativeButtonSize = preferences.getInt("answerButtonSize", 100)
        mTTS.enabled = preferences.getBoolean("tts", false)
        mScrollingButtons = preferences.getBoolean("scrolling_buttons", false)
        mDoubleScrolling = preferences.getBoolean("double_scrolling", false)
        prefShowTopbar = preferences.getBoolean("showTopbar", true)
        mLargeAnswerButtons = preferences.getBoolean("showLargeAnswerButtons", false)
        mDoubleTapTimeInterval = preferences.getInt(DOUBLE_TAP_TIME_INTERVAL, DEFAULT_DOUBLE_TAP_TIME_INTERVAL)
        mExitViaDoubleTapBack = preferences.getBoolean("exitViaDoubleTapBack", false)
        mGesturesEnabled = preferences.getBoolean(GestureProcessor.PREF_KEY, false)
        if (mGesturesEnabled) {
            mGestureProcessor.init(preferences)
        }
        if (preferences.getBoolean("keepScreenOn", false)) {
            this.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        return preferences
    }

    protected open fun restoreCollectionPreferences(col: Collection) {

        // These are preferences we pull out of the collection instead of SharedPreferences
        try {
            mShowNextReviewTime = col.get_config_boolean("estTimes")
            val preferences = AnkiDroidApp.getSharedPrefs(baseContext)
            mAutomaticAnswer = AutomaticAnswer.createInstance(this, preferences, col)
        } catch (ex: Exception) {
            Timber.w(ex)
            onCollectionLoadError()
        }
    }

    private fun setInterface() {
        if (mCurrentCard == null) {
            return
        }
        recreateWebView()
    }

    protected open fun recreateWebView() {
        if (webView == null) {
            webView = createWebView()
            initializeDebugging(AnkiDroidApp.getSharedPrefs(this))
            mCardFrame!!.addView(webView)
            mGestureDetectorImpl!!.onWebViewCreated(webView!!)
        }
        if (webView!!.visibility != View.VISIBLE) {
            webView!!.visibility = View.VISIBLE
        }
    }

    /** A new card has been loaded into the Viewer, or the question has been re-shown  */
    protected open fun updateForNewCard() {
        updateActionBar()

        // Clean answer field
        if (typeAnswer!!.validForEditText()) {
            mAnswerField!!.setText("")
        }
    }

    protected open fun updateActionBar() {
        updateDeckName()
    }

    private fun updateDeckName() {
        if (mCurrentCard == null) return
        val actionBar = supportActionBar
        if (actionBar != null) {
            val title = Decks.basename(col.decks.get(mCurrentCard!!.did).getString("name"))
            actionBar.title = title
        }
        if (!prefShowTopbar) {
            mTopBarLayout!!.visibility = View.GONE
        }
    }

    override fun automaticShowQuestion(action: AutomaticAnswerAction) {
        // Assume hitting the "Again" button when auto next question
        easeButton1!!.performSafeClick()
    }

    override fun automaticShowAnswer() {
        if (flipCardLayout!!.isEnabled && flipCardLayout!!.visibility == View.VISIBLE) {
            flipCardLayout!!.performClick()
        }
    }

    internal inner class ReadTextListener : ReadText.ReadTextListener {
        override fun onDone(playedSide: SoundSide?) {
            Timber.d("done reading text")
            if (playedSide == SoundSide.QUESTION) {
                mAutomaticAnswer.scheduleAutomaticDisplayAnswer()
            } else if (playedSide == SoundSide.ANSWER) {
                mAutomaticAnswer.scheduleAutomaticDisplayQuestion()
            }
        }
    }

    open fun displayCardQuestion() {
        displayCardQuestion(false)

        // js api initialisation / reset
        mAnkiDroidJsAPI!!.init()
    }

    private fun displayCardQuestion(reload: Boolean) {
        Timber.d("displayCardQuestion()")
        sDisplayAnswer = false
        mBackButtonPressedToReturn = false
        setInterface()
        typeAnswer!!.input = ""
        typeAnswer!!.updateInfo(mCurrentCard!!, resources)
        if (!mCurrentCard!!.isEmpty && typeAnswer!!.validForEditText()) {
            // Show text entry based on if the user wants to write the answer
            mAnswerField!!.visibility = View.VISIBLE
            mAnswerField!!.applyLanguageHint(typeAnswer!!.languageHint)
        } else {
            mAnswerField!!.visibility = View.GONE
        }
        val content = mHtmlGenerator!!.generateHtml(mCurrentCard!!, reload, Side.FRONT)
        updateCard(content)
        hideEaseButtons()
        mAutomaticAnswer.onDisplayQuestion()
        // If Card-based TTS is enabled, we "automatic display" after the TTS has finished as we don't know the duration
        if (!mTTS.enabled) {
            mAutomaticAnswer.scheduleAutomaticDisplayAnswer(mUseTimerDynamicMS)
        }
        Timber.i("AbstractFlashcardViewer:: Question successfully shown for card id %d", mCurrentCard!!.id)
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PROTECTED)
    open fun displayCardAnswer() {
        // #7294 Required in case the animation end action does not fire:
        actualHideEaseButtons()
        Timber.d("displayCardAnswer()")
        mMissingImageHandler.onCardSideChange()
        mBackButtonPressedToReturn = false

        // prevent answering (by e.g. gestures) before card is loaded
        if (mCurrentCard == null) {
            return
        }

        // TODO needs testing: changing a card's model without flipping it back to the front
        //  (such as editing a card, then editing the card template)
        typeAnswer!!.updateInfo(mCurrentCard!!, resources)

        // Explicitly hide the soft keyboard. It *should* be hiding itself automatically,
        // but sometimes failed to do so (e.g. if an OnKeyListener is attached).
        if (typeAnswer!!.validForEditText()) {
            val inputMethodManager = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            inputMethodManager.hideSoftInputFromWindow(mAnswerField!!.windowToken, 0)
        }
        sDisplayAnswer = true
        mSoundPlayer.stopSounds()
        mAnswerField!!.visibility = View.GONE
        // Clean up the user answer and the correct answer
        if (!typeAnswer!!.useInputTag) {
            typeAnswer!!.input = mAnswerField!!.text.toString()
        }
        mIsSelecting = false
        val answerContent = mHtmlGenerator!!.generateHtml(mCurrentCard!!, false, Side.BACK)
        updateCard(answerContent)
        displayAnswerBottomBar()
        mAutomaticAnswer.onDisplayAnswer()
        // If Card-based TTS is enabled, we "automatic display" after the TTS has finished as we don't know the duration
        if (!mTTS.enabled) {
            mAutomaticAnswer.scheduleAutomaticDisplayQuestion(mUseTimerDynamicMS)
        }
    }

    override fun scrollCurrentCardBy(dy: Int) {
        processCardAction { cardWebView: WebView? ->
            if (dy != 0 && cardWebView!!.canScrollVertically(dy)) {
                cardWebView.scrollBy(0, dy)
            }
        }
    }

    override fun tapOnCurrentCard(x: Int, y: Int) {
        // assemble suitable ACTION_DOWN and ACTION_UP events and forward them to the card's handler
        val eDown = MotionEvent.obtain(
            SystemClock.uptimeMillis(),
            SystemClock.uptimeMillis(), MotionEvent.ACTION_DOWN, x.toFloat(), y.toFloat(), 1f, 1f, 0, 1f, 1f, 0, 0
        )
        processCardAction { cardWebView: WebView? -> cardWebView!!.dispatchTouchEvent(eDown) }
        val eUp = MotionEvent.obtain(
            eDown.downTime,
            SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, x.toFloat(), y.toFloat(), 1f, 1f, 0, 1f, 1f, 0, 0
        )
        processCardAction { cardWebView: WebView? -> cardWebView!!.dispatchTouchEvent(eUp) }
    }

    @RustCleanup("mAnswerSoundsAdded is no longer necessary once we transition as it's a fast operation")
    private fun addAnswerSounds(answerSounds: Supplier<List<SoundOrVideoTag>>) {
        // don't add answer sounds multiple times, such as when reshowing card after exiting editor
        // additionally, this condition reduces computation time
        if (!mAnswerSoundsAdded) {
            mSoundPlayer.addSounds(mBaseUrl, answerSounds.get(), SoundSide.ANSWER)
            mAnswerSoundsAdded = true
        }
    }

    @KotlinCleanup("internal for AnkiDroidJsApi")
    internal val isInNightMode: Boolean
        get() = isInNightMode(AnkiDroidApp.getSharedPrefs(this))

    private fun updateCard(content: CardHtml) {
        Timber.d("updateCard()")
        mUseTimerDynamicMS = 0
        if (sDisplayAnswer) {
            addAnswerSounds { content.getSoundTags(Side.BACK) }
        } else {
            // reset sounds each time first side of card is displayed, which may happen repeatedly without ever
            // leaving the card (such as when edited)
            mSoundPlayer.resetSounds()
            mAnswerSoundsAdded = false
            mSoundPlayer.addSounds(mBaseUrl, content.getSoundTags(Side.FRONT), SoundSide.QUESTION)
            if (mAutomaticAnswer.isEnabled() && !mAnswerSoundsAdded && mCardSoundConfig!!.autoplay) {
                addAnswerSounds { content.getSoundTags(Side.BACK) }
            }
        }
        cardContent = content.getTemplateHtml()
        Timber.d("base url = %s", mBaseUrl)
        if (AnkiDroidApp.getSharedPrefs(this).getBoolean("html_javascript_debugging", false)) {
            try {
                FileOutputStream(
                    File(
                        CollectionHelper.getCurrentAnkiDroidDirectory(this),
                        "card.html"
                    )
                ).use { f -> f.write(cardContent!!.toByteArray()) }
            } catch (e: IOException) {
                Timber.d(e, "failed to save card")
            }
        }
        fillFlashcard()
        playSounds(false) // Play sounds if appropriate
    }

    /**
     * Plays sounds (or TTS, if configured) for currently shown side of card.
     *
     * @param doAudioReplay indicates an anki desktop-like replay call is desired, whose behavior is identical to
     * pressing the keyboard shortcut R on the desktop
     */
    protected open fun playSounds(doAudioReplay: Boolean) {
        val replayQuestion = mCardSoundConfig!!.replayQuestion
        if (mCardSoundConfig!!.autoplay || doAudioReplay) {
            // Use TTS if TTS preference enabled and no other sound source
            val useTTS = mTTS.enabled &&
                !(sDisplayAnswer && mSoundPlayer.hasAnswer()) && !(!sDisplayAnswer && mSoundPlayer.hasQuestion())
            // We need to play the sounds from the proper side of the card
            if (!useTTS) { // Text to speech not in effect here
                if (doAudioReplay && replayQuestion && sDisplayAnswer) {
                    // only when all of the above are true will question be played with answer, to match desktop
                    playSounds(SoundSide.QUESTION_AND_ANSWER)
                } else if (sDisplayAnswer) {
                    playSounds(SoundSide.ANSWER)
                    if (mAutomaticAnswer.isEnabled()) {
                        mUseTimerDynamicMS = mSoundPlayer.getSoundsLength(SoundSide.ANSWER)
                    }
                } else { // question is displayed
                    playSounds(SoundSide.QUESTION)
                    // If the user wants to show the answer automatically
                    if (mAutomaticAnswer.isEnabled()) {
                        mUseTimerDynamicMS = mSoundPlayer.getSoundsLength(SoundSide.QUESTION_AND_ANSWER)
                    }
                }
            } else { // Text to speech is in effect here
                // If the question is displayed or if the question should be replayed, read the question
                if (mTtsInitialized) {
                    if (!sDisplayAnswer || doAudioReplay && replayQuestion) {
                        readCardTts(SoundSide.QUESTION)
                    }
                    if (sDisplayAnswer) {
                        readCardTts(SoundSide.ANSWER)
                    }
                } else {
                    mReplayOnTtsInit = true
                }
            }
        }
    }

    private fun readCardTts(soundSide: SoundSide) {
        val tags = legacyGetTtsTags(mCurrentCard!!, soundSide, this)
        if (tags != null) {
            mTTS.readCardText(tags, mCurrentCard!!, soundSide)
        }
    }

    private fun playSounds(questionAndAnswer: SoundSide) {
        mSoundPlayer.playSounds(questionAndAnswer, soundErrorListener)
    }

    private val soundErrorListener: Sound.OnErrorListener
        get() = Sound.OnErrorListener { _: MediaPlayer?, what: Int, extra: Int, path: String? ->
            Timber.w("Media Error: (%d, %d). Calling OnCompletionListener", what, extra)
            try {
                val file = File(path!!)
                if (!file.exists()) {
                    mMissingImageHandler.processMissingSound(file) { filename: String? -> displayCouldNotFindMediaSnackbar(filename) }
                }
            } catch (e: Exception) {
                Timber.w(e)
                return@OnErrorListener false
            }
            false
        }

    /**
     * Shows the dialogue for selecting TTS for the current card and cardside.
     */
    protected fun showSelectTtsDialogue() {
        if (mTtsInitialized) {
            mTTS.selectTts(this, mCurrentCard!!, if (sDisplayAnswer) SoundSide.ANSWER else SoundSide.QUESTION)
        }
    }

    open fun fillFlashcard() {
        Timber.d("fillFlashcard()")
        Timber.d("base url = %s", mBaseUrl)
        if (cardContent == null) {
            Timber.w("fillFlashCard() called with no card content")
            return
        }
        val cardContent = cardContent!!
        processCardAction { cardWebView: WebView? -> loadContentIntoCard(cardWebView, cardContent) }
        mGestureDetectorImpl!!.onFillFlashcard()
        if (!sDisplayAnswer) {
            updateForNewCard()
        }
    }

    private fun loadContentIntoCard(card: WebView?, content: String) {
        if (card != null) {
            card.settings.mediaPlaybackRequiresUserGesture = !mCardSoundConfig!!.autoplay
            card.loadDataWithBaseURL(mViewerUrl, content, "text/html", "utf-8", null)
        }
    }

    protected open fun unblockControls() {
        controlBlocked = ControlBlock.UNBLOCKED
        mCardFrame!!.isEnabled = true
        flipCardLayout!!.isEnabled = true
        easeButton1!!.unblockBasedOnEase(mCurrentEase)
        easeButton2!!.unblockBasedOnEase(mCurrentEase)
        easeButton3!!.unblockBasedOnEase(mCurrentEase)
        easeButton4!!.unblockBasedOnEase(mCurrentEase)
        if (typeAnswer!!.validForEditText()) {
            mAnswerField!!.isEnabled = true
        }
        mTouchLayer!!.visibility = View.VISIBLE
        mInAnswer = false
        invalidateOptionsMenu()
    }

    /**
     * @param quick Whether we expect the control to come back quickly
     */
    @VisibleForTesting
    protected open fun blockControls(quick: Boolean) {
        controlBlocked = if (quick) {
            ControlBlock.QUICK
        } else {
            ControlBlock.SLOW
        }
        mCardFrame!!.isEnabled = false
        flipCardLayout!!.isEnabled = false
        mTouchLayer!!.visibility = View.INVISIBLE
        mInAnswer = true
        easeButton1!!.blockBasedOnEase(mCurrentEase)
        easeButton2!!.blockBasedOnEase(mCurrentEase)
        easeButton3!!.blockBasedOnEase(mCurrentEase)
        easeButton4!!.blockBasedOnEase(mCurrentEase)
        if (typeAnswer!!.validForEditText()) {
            mAnswerField!!.isEnabled = false
        }
        invalidateOptionsMenu()
    }

    /**
     * Select Text in the webview and automatically sends the selected text to the clipboard. From
     * http://cosmez.blogspot.com/2010/04/webview-emulateshiftheld-on-android.html
     */
    @Suppress("deprecation") // Tracked separately in Github as #5024
    private fun selectAndCopyText() {
        mIsSelecting = try {
            val shiftPressEvent = KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SHIFT_LEFT, 0, 0)
            processCardAction { receiver: WebView? -> shiftPressEvent.dispatch(receiver) }
            shiftPressEvent.isShiftPressed
            true
        } catch (e: Exception) {
            throw AssertionError(e)
        }
    }

    internal fun buryCard(): Boolean {
        return dismiss(BuryCard(mCurrentCard!!)) { showThemedToast(this, R.string.buried_card, true) }
    }

    internal fun suspendCard(): Boolean {
        return dismiss(SuspendCard(mCurrentCard!!)) { showThemedToast(this, R.string.suspended_card, true) }
    }

    internal fun suspendNote(): Boolean {
        return dismiss(SuspendNote(mCurrentCard!!)) { showThemedToast(this, R.string.suspended_note, true) }
    }

    internal fun buryNote(): Boolean {
        return dismiss(BuryNote(mCurrentCard!!)) { showThemedToast(this, R.string.buried_note, true) }
    }

    override fun executeCommand(which: ViewerCommand): Boolean {
        return if (isControlBlocked() && which !== ViewerCommand.COMMAND_EXIT) {
            false
        } else when (which) {
            ViewerCommand.COMMAND_NOTHING -> true
            ViewerCommand.COMMAND_SHOW_ANSWER -> {
                if (sDisplayAnswer) {
                    return false
                }
                displayCardAnswer()
                true
            }
            ViewerCommand.COMMAND_FLIP_OR_ANSWER_EASE1 -> {
                flipOrAnswerCard(EASE_1)
                true
            }
            ViewerCommand.COMMAND_FLIP_OR_ANSWER_EASE2 -> {
                flipOrAnswerCard(EASE_2)
                true
            }
            ViewerCommand.COMMAND_FLIP_OR_ANSWER_EASE3 -> {
                flipOrAnswerCard(EASE_3)
                true
            }
            ViewerCommand.COMMAND_FLIP_OR_ANSWER_EASE4 -> {
                flipOrAnswerCard(EASE_4)
                true
            }
            ViewerCommand.COMMAND_FLIP_OR_ANSWER_RECOMMENDED -> {
                flipOrAnswerCard(getRecommendedEase(false))
                true
            }
            ViewerCommand.COMMAND_FLIP_OR_ANSWER_BETTER_THAN_RECOMMENDED -> {
                flipOrAnswerCard(getRecommendedEase(true))
                true
            }
            ViewerCommand.COMMAND_EXIT -> {
                closeReviewer(RESULT_DEFAULT, false)
                true
            }
            ViewerCommand.COMMAND_UNDO -> {
                if (!isUndoAvailable) {
                    return false
                }
                undo()
                true
            }
            ViewerCommand.COMMAND_EDIT -> {
                editCard()
                true
            }
            ViewerCommand.COMMAND_CARD_INFO -> {
                openCardInfo()
                true
            }
            ViewerCommand.COMMAND_TAG -> {
                showTagsDialog()
                true
            }
            ViewerCommand.COMMAND_BURY_CARD -> buryCard()
            ViewerCommand.COMMAND_BURY_NOTE -> buryNote()
            ViewerCommand.COMMAND_SUSPEND_CARD -> suspendCard()
            ViewerCommand.COMMAND_SUSPEND_NOTE -> suspendNote()
            ViewerCommand.COMMAND_DELETE -> {
                showDeleteNoteDialog()
                true
            }
            ViewerCommand.COMMAND_PLAY_MEDIA -> {
                playSounds(true)
                true
            }
            ViewerCommand.COMMAND_PAGE_UP -> {
                onPageUp()
                true
            }
            ViewerCommand.COMMAND_PAGE_DOWN -> {
                onPageDown()
                true
            }
            ViewerCommand.COMMAND_ABORT_AND_SYNC -> {
                abortAndSync()
                true
            }
            ViewerCommand.COMMAND_RECORD_VOICE -> {
                recordVoice()
                true
            }
            ViewerCommand.COMMAND_REPLAY_VOICE -> {
                replayVoice()
                true
            }
            ViewerCommand.COMMAND_TOGGLE_WHITEBOARD -> {
                toggleWhiteboard()
                true
            }
            ViewerCommand.COMMAND_SHOW_HINT -> {
                loadUrlInViewer("javascript: showHint();")
                true
            }
            ViewerCommand.COMMAND_SHOW_ALL_HINTS -> {
                loadUrlInViewer("javascript: showAllHints();")
                true
            }
            else -> {
                Timber.w("Unknown command requested: %s", which)
                false
            }
        }
    }

    protected open fun replayVoice() {
        // intentionally blank
    }

    protected open fun recordVoice() {
        // intentionally blank
    }

    protected open fun toggleWhiteboard() {
        // intentionally blank
    }

    private fun abortAndSync() {
        closeReviewer(RESULT_ABORT_AND_SYNC, true)
    }

    protected fun openCardInfo() {
        if (mCurrentCard == null) {
            showThemedToast(this, getString(R.string.multimedia_editor_something_wrong), true)
            return
        }
        val intent = Intent(this, CardInfo::class.java)
        intent.putExtra("cardId", mCurrentCard!!.id)
        startActivityWithAnimation(intent, ActivityTransitionAnimation.Direction.FADE)
    }

    /** Displays a snackbar which does not obscure the answer buttons  */
    private fun showSnackbar(mainText: String?, @StringRes buttonText: Int, onClickListener: View.OnClickListener?) {
        // BUG: Moving from full screen to non-full screen obscures the buttons
        val sb = getSnackbar(this, mainText, Snackbar.LENGTH_LONG, buttonText, onClickListener, webView!!, null)
        val easeButtons = findViewById<View>(R.id.answer_options_layout)
        val previewButtons = findViewById<View>(R.id.preview_buttons_layout)
        val upperView = if (previewButtons != null && previewButtons.visibility != View.GONE) previewButtons else easeButtons

        // we need to check for View.GONE as setting the anchor does not seem to respect this property
        // (there's a gap even if the view is invisible)
        if (upperView != null && upperView.visibility != View.GONE) {
            val sbView = sb.view
            val layoutParams = sbView.layoutParams as CoordinatorLayout.LayoutParams
            layoutParams.anchorId = upperView.id
            layoutParams.anchorGravity = Gravity.TOP
            layoutParams.gravity = Gravity.TOP
            sbView.layoutParams = layoutParams
        }
        sb.show()
    }

    private fun onPageUp() {
        // pageUp performs a half scroll, we want a full page
        processCardAction { cardWebView: WebView? ->
            cardWebView!!.pageUp(false)
            cardWebView.pageUp(false)
        }
    }

    private fun onPageDown() {
        processCardAction { cardWebView: WebView? ->
            cardWebView!!.pageDown(false)
            cardWebView.pageDown(false)
        }
    }

    protected open fun performClickWithVisualFeedback(ease: Int) {
        // Delay could potentially be lower - testing with 20 left a visible "click"
        when (ease) {
            EASE_1 -> easeButton1!!.performClickWithVisualFeedback()
            EASE_2 -> easeButton2!!.performClickWithVisualFeedback()
            EASE_3 -> easeButton3!!.performClickWithVisualFeedback()
            EASE_4 -> easeButton4!!.performClickWithVisualFeedback()
        }
    }

    @get:VisibleForTesting
    protected open val isUndoAvailable: Boolean
        get() = col.undoAvailable()
    // ----------------------------------------------------------------------------
    // INNER CLASSES
    // ----------------------------------------------------------------------------
    /**
     * Provides a hook for calling "alert" from javascript. Useful for debugging your javascript.
     */
    class AnkiDroidWebChromeClient : WebChromeClient() {
        override fun onJsAlert(view: WebView, url: String, message: String, result: JsResult): Boolean {
            Timber.i("AbstractFlashcardViewer:: onJsAlert: %s", message)
            result.confirm()
            return true
        }
    }

    protected open fun closeReviewer(result: Int, saveDeck: Boolean) {
        mAutomaticAnswer.disable()
        mPreviousAnswerIndicator!!.stopAutomaticHide()
        mLongClickHandler.removeCallbacks(mLongClickTestRunnable)
        mLongClickHandler.removeCallbacks(mStartLongClickAction)
        this@AbstractFlashcardViewer.setResult(result)
        if (saveDeck) {
            saveCollectionInBackground()
        }
        finishWithAnimation(ActivityTransitionAnimation.Direction.END)
    }

    @Suppress("deprecation") // supportInvalidateOptionsMenu
    protected fun refreshActionBar() {
        supportInvalidateOptionsMenu()
    }

    /** Fixing bug 720: <input></input> focus, thanks to pablomouzo on android issue 7189  */
    internal inner class MyWebView(context: Context?) : WebView(context!!) {
        override fun loadDataWithBaseURL(baseUrl: String?, data: String, mimeType: String?, encoding: String?, historyUrl: String?) {
            if (!this@AbstractFlashcardViewer.isDestroyed) {
                super.loadDataWithBaseURL(baseUrl, data, mimeType, encoding, historyUrl)
            } else {
                Timber.w("Not loading card - Activity is in the process of being destroyed.")
            }
        }

        override fun onScrollChanged(horiz: Int, vert: Int, oldHoriz: Int, oldVert: Int) {
            super.onScrollChanged(horiz, vert, oldHoriz, oldVert)
            if (abs(horiz - oldHoriz) > abs(vert - oldVert)) {
                mIsXScrolling = true
                mScrollHandler.removeCallbacks(mScrollXRunnable)
                mScrollHandler.postDelayed(mScrollXRunnable, 300)
            } else {
                mIsYScrolling = true
                mScrollHandler.removeCallbacks(mScrollYRunnable)
                mScrollHandler.postDelayed(mScrollYRunnable, 300)
            }
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (event.action == MotionEvent.ACTION_DOWN) {
                val scrollParent = findScrollParent(this)
                scrollParent?.requestDisallowInterceptTouchEvent(true)
            }
            return super.onTouchEvent(event)
        }

        override fun onOverScrolled(scrollX: Int, scrollY: Int, clampedX: Boolean, clampedY: Boolean) {
            if (clampedX) {
                val scrollParent = findScrollParent(this)
                scrollParent?.requestDisallowInterceptTouchEvent(false)
            }
            super.onOverScrolled(scrollX, scrollY, clampedX, clampedY)
        }

        private fun findScrollParent(current: View): ViewParent? {
            val parent = current.parent ?: return null
            if (parent is FullDraggableContainer) {
                return parent
            } else if (parent is View) {
                return findScrollParent(parent as View)
            }
            return null
        }

        private val mScrollHandler = newHandler()
        private val mScrollXRunnable = Runnable { mIsXScrolling = false }
        private val mScrollYRunnable = Runnable { mIsYScrolling = false }
    }

    internal open inner class MyGestureDetector : SimpleOnGestureListener() {
        override fun onFling(e1: MotionEvent, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
            Timber.d("onFling")

            // #5741 - A swipe from the top caused delayedHide to be triggered,
            // accepting a gesture and quickly disabling the status bar, which wasn't ideal.
            // it would be lovely to use e1.getEdgeFlags(), but alas, it doesn't work.
            if (isTouchingEdge(e1)) {
                Timber.d("ignoring edge fling")
                return false
            }

            // Go back to immersive mode if the user had temporarily exited it (and then execute swipe gesture)
            this@AbstractFlashcardViewer.onFling()
            if (mGesturesEnabled) {
                try {
                    val dy = e2.y - e1.y
                    val dx = e2.x - e1.x
                    mGestureProcessor.onFling(dx, dy, velocityX, velocityY, mIsSelecting, mIsXScrolling, mIsYScrolling)
                } catch (e: Exception) {
                    Timber.e(e, "onFling Exception")
                }
            }
            return false
        }

        private fun isTouchingEdge(e1: MotionEvent): Boolean {
            val height = mTouchLayer!!.height
            val width = mTouchLayer!!.width
            val margin = Companion.NO_GESTURE_BORDER_DIP * resources.displayMetrics.density + 0.5f
            return e1.x < margin || e1.y < margin || height - e1.y < margin || width - e1.x < margin
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            if (mGesturesEnabled) {
                mGestureProcessor.onDoubleTap()
            }
            return true
        }

        override fun onSingleTapUp(e: MotionEvent): Boolean {
            if (mTouchStarted) {
                mLongClickHandler.removeCallbacks(mLongClickTestRunnable)
                mTouchStarted = false
            }
            return false
        }

        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            // Go back to immersive mode if the user had temporarily exited it (and ignore the tap gesture)
            if (onSingleTap()) {
                return true
            }
            executeTouchCommand(e)
            return false
        }

        protected open fun executeTouchCommand(e: MotionEvent) {
            if (mGesturesEnabled && !mIsSelecting) {
                val height = mTouchLayer!!.height
                val width = mTouchLayer!!.width
                val posX = e.x
                val posY = e.y
                mGestureProcessor.onTap(height, width, posX, posY)
            }
            mIsSelecting = false
        }

        open fun onWebViewCreated(webView: WebView) {
            // intentionally blank
        }

        open fun onFillFlashcard() {
            // intentionally blank
        }

        open fun eventCanBeSentToWebView(event: MotionEvent): Boolean {
            return true
        }
    }

    protected open fun onSingleTap(): Boolean {
        return false
    }

    protected open fun onFling() {}

    /** #6141 - blocks clicking links from executing "touch" gestures.
     * COULD_BE_BETTER: Make base class static and move this out of the CardViewer  */
    internal inner class LinkDetectingGestureDetector : MyGestureDetector() {
        /** A list of events to process when listening to WebView touches   */
        private val mDesiredTouchEvents = HashSetInit<MotionEvent>(2)

        /** A list of events we sent to the WebView (to block double-processing)  */
        private val mDispatchedTouchEvents = HashSetInit<MotionEvent>(2)
        override fun onFillFlashcard() {
            Timber.d("Removing pending touch events for gestures")
            mDesiredTouchEvents.clear()
            mDispatchedTouchEvents.clear()
        }

        override fun eventCanBeSentToWebView(event: MotionEvent): Boolean {
            // if we processed the event, we don't want to perform it again
            return !mDispatchedTouchEvents.remove(event)
        }

        override fun executeTouchCommand(e: MotionEvent) {
            e.action = MotionEvent.ACTION_DOWN
            val upEvent = MotionEvent.obtainNoHistory(e)
            upEvent.action = MotionEvent.ACTION_UP

            // mark the events we want to process
            mDesiredTouchEvents.add(e)
            mDesiredTouchEvents.add(upEvent)

            // mark the events to can guard against double-processing
            mDispatchedTouchEvents.add(e)
            mDispatchedTouchEvents.add(upEvent)
            Timber.d("Dispatching touch events")
            processCardAction { cardWebView: WebView? ->
                cardWebView!!.dispatchTouchEvent(e)
                cardWebView.dispatchTouchEvent(upEvent)
            }
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onWebViewCreated(webView: WebView) {
            Timber.d("Initializing WebView touch handler")
            webView.setOnTouchListener { webViewAsView: View, motionEvent: MotionEvent ->
                if (!mDesiredTouchEvents.remove(motionEvent)) {
                    return@setOnTouchListener false
                }

                // We need an associated up event so the WebView doesn't keep a selection
                // But we don't want to handle this as a touch event.
                if (motionEvent.action == MotionEvent.ACTION_UP) {
                    return@setOnTouchListener true
                }
                val cardWebView = webViewAsView as WebView
                val result: HitTestResult = try {
                    cardWebView.hitTestResult
                } catch (e: Exception) {
                    Timber.w(e, "Cannot obtain HitTest result")
                    return@setOnTouchListener true
                }
                if (isLinkClick(result)) {
                    Timber.v("Detected link click - ignoring gesture dispatch")
                    return@setOnTouchListener true
                }
                Timber.v("Executing continuation for click type: %d", result.type)
                super.executeTouchCommand(motionEvent)
                true
            }
        }

        private fun isLinkClick(result: HitTestResult?): Boolean {
            if (result == null) {
                return false
            }
            val type = result.type
            return (
                type == HitTestResult.SRC_ANCHOR_TYPE ||
                    type == HitTestResult.SRC_IMAGE_ANCHOR_TYPE
                )
        }
    }

    /**
     * Public method to start new video player activity
     */
    fun playVideo(path: String?) {
        Timber.i("Launching Video: %s", path)
        val videoPlayer = Intent(this, VideoPlayer::class.java)
        videoPlayer.putExtra("path", path)
        startActivityWithoutAnimation(videoPlayer)
    }

    /** Callback for when TTS has been initialized.  */
    fun ttsInitialized() {
        mTtsInitialized = true
        if (mReplayOnTtsInit) {
            playSounds(true)
        }
    }

    protected open fun shouldDisplayMark(): Boolean {
        return isMarked(mCurrentCard!!.note())
    }

    protected fun <TResult : Computation<NextCard<*>>> nextCardHandler(): TaskListenerBuilder<Unit, TResult> {
        return TaskListenerBuilder(NextCardHandler())
    }

    /**
     * @param dismiss An action to execute, to ignore current card and get another one
     * @return whether the action succeeded.
     */
    protected open fun dismiss(dismiss: AnkiMethod<Computation<NextCard<*>>>, executeAfter: Runnable): Boolean {
        blockControls(false)
        dismiss.runWithHandler(nextCardHandler<Computation<NextCard<*>>>().alsoExecuteAfter { executeAfter.run() })
        return true
    }

    val writeLock: Lock
        get() = mCardLock.writeLock()
    open var currentCard: Card?
        get() = mCurrentCard
        set(card) {
            mCurrentCard = card
            mCardSoundConfig = if (card == null) {
                null
            } else {
                create(col, card)
            }
        }

    /** Refreshes the WebView after a crash  */
    fun destroyWebViewFrame() {
        // Destroy the current WebView (to ensure WebView is GCed).
        // Otherwise, we get the following error:
        // "crash wasn't handled by all associated webviews, triggering application crash"
        mCardFrame!!.removeAllViews()
        mCardFrameParent!!.removeView(mCardFrame)
        // destroy after removal from the view - produces logcat warnings otherwise
        destroyWebView(webView)
        webView = null
        // inflate a new instance of mCardFrame
        mCardFrame = inflateNewView<FrameLayout>(R.id.flashcard)
        // Even with the above, I occasionally saw the above error. Manually trigger the GC.
        // I'll keep this line unless I see another crash, which would point to another underlying issue.
        System.gc()
    }

    fun recreateWebViewFrame() {
        // we need to add at index 0 so gestures still go through.
        mCardFrameParent!!.addView(mCardFrame, 0)
        recreateWebView()
    }

    /** Signals from a WebView represent actions with no parameters  */
    @VisibleForTesting
    internal object WebViewSignalParserUtils {
        /** A signal which we did not know how to handle  */
        const val SIGNAL_UNHANDLED = 0

        /** A known signal which should perform a noop  */
        const val SIGNAL_NOOP = 1
        const val TYPE_FOCUS = 2

        /** Tell the app that we no longer want to focus the WebView and should instead return keyboard focus to a
         * native answer input method.  */
        const val RELINQUISH_FOCUS = 3
        const val SHOW_ANSWER = 4
        const val ANSWER_ORDINAL_1 = 5
        const val ANSWER_ORDINAL_2 = 6
        const val ANSWER_ORDINAL_3 = 7
        const val ANSWER_ORDINAL_4 = 8
        @JvmStatic
        fun getSignalFromUrl(url: String): Int {
            when (url) {
                "signal:typefocus" -> return TYPE_FOCUS
                "signal:relinquishFocus" -> return RELINQUISH_FOCUS
                "signal:show_answer" -> return SHOW_ANSWER
                "signal:answer_ease1" -> return ANSWER_ORDINAL_1
                "signal:answer_ease2" -> return ANSWER_ORDINAL_2
                "signal:answer_ease3" -> return ANSWER_ORDINAL_3
                "signal:answer_ease4" -> return ANSWER_ORDINAL_4
                else -> {}
            }
            if (url.startsWith("signal:answer_ease")) {
                Timber.w("Unhandled signal: ease value: %s", url)
                return SIGNAL_NOOP
            }
            return SIGNAL_UNHANDLED // unknown, or not a signal.
        }
    }

    protected inner class CardViewerWebClient internal constructor(
        private val loader: WebViewAssetLoader?,
        private val onPageFinishedCallback: OnPageFinishedCallback? = null
    ) : WebViewClient() {
        @TargetApi(Build.VERSION_CODES.N)
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val url = request.url.toString()
            Timber.d("Obtained URL from card: '%s'", url)
            return filterUrl(url)
        }

        // required for lower APIs (I think)
        override fun shouldInterceptRequest(view: WebView, url: String): WebResourceResponse? {
            // response is null if nothing required
            if (isLoadedFromProtocolRelativeUrl(url)) {
                mMissingImageHandler.processInefficientImage { displayMediaUpgradeRequiredSnackbar() }
            }
            if (isLoadedFromHttpUrl(url)) {
                // shouldInterceptRequest is not running on the UI thread.
                runOnUiThread { mDisplayMediaLoadedFromHttpWarningSnackbar.execOnceForReference(mCurrentCard!!) }
            }
            return null
        }

        @TargetApi(Build.VERSION_CODES.N)
        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
            val url = request.url
            val result = loader!!.shouldInterceptRequest(url)
            if (result != null) {
                return result
            }
            if (!hasWebBrowser(baseContext)) {
                val scheme = url.scheme!!.trim { it <= ' ' }
                if ("http".equals(scheme, ignoreCase = true) || "https".equals(scheme, ignoreCase = true)) {
                    val response = resources.getString(R.string.no_outgoing_link_in_cardbrowser)
                    return WebResourceResponse("text/html", "utf-8", ByteArrayInputStream(response.toByteArray()))
                }
            }
            if (isLoadedFromProtocolRelativeUrl(request.url.toString())) {
                mMissingImageHandler.processInefficientImage { displayMediaUpgradeRequiredSnackbar() }
            }
            if (isLoadedFromHttpUrl(url)) {
                // shouldInterceptRequest is not running on the UI thread.
                runOnUiThread { mDisplayMediaLoadedFromHttpWarningSnackbar.execOnceForReference(mCurrentCard!!) }
            }
            return null
        }

        private fun isLoadedFromHttpUrl(url: String): Boolean {
            return url.trim { it <= ' ' }.lowercase(Locale.ROOT).startsWith("http")
        }

        private fun isLoadedFromHttpUrl(uri: Uri): Boolean {
            return uri.scheme.equals("http", ignoreCase = true)
        }

        private fun isLoadedFromProtocolRelativeUrl(url: String): Boolean {
            // a URL provided as "//wikipedia.org" is currently transformed to file://wikipedia.org, we can catch this
            // because <img src="x.png"> maps to file:///.../x.png
            return url.startsWith("file://") && !url.startsWith("file:///")
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            super.onReceivedError(view, request, error)
            mMissingImageHandler.processFailure(request) { filename: String? -> displayCouldNotFindMediaSnackbar(filename) }
        }

        override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse) {
            super.onReceivedHttpError(view, request, errorResponse)
            mMissingImageHandler.processFailure(request) { filename: String? -> displayCouldNotFindMediaSnackbar(filename) }
        }

        override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
            return filterUrl(url)
        }

        // Filter any links using the custom "playsound" protocol defined in Sound.java.
        // We play sounds through these links when a user taps the sound icon.
        fun filterUrl(url: String): Boolean {
            if (url.startsWith("playsound:")) {
                controlSound(url)
                return true
            }
            if (url.startsWith("file") || url.startsWith("data:")) {
                return false // Let the webview load files, i.e. local images.
            }
            if (url.startsWith("typeblurtext:")) {
                // Store the text the javascript has send us…
                typeAnswer!!.input = decodeUrl(url.replaceFirst("typeblurtext:".toRegex(), ""))
                // … and show the “SHOW ANSWER” button again.
                flipCardLayout!!.visibility = View.VISIBLE
                return true
            }
            if (url.startsWith("typeentertext:")) {
                // Store the text the javascript has send us…
                typeAnswer!!.input = decodeUrl(url.replaceFirst("typeentertext:".toRegex(), ""))
                // … and show the answer.
                flipCardLayout!!.performClick()
                return true
            }
            // Show options menu from WebView
            if (url.startsWith("signal:anki_show_options_menu")) {
                if (isFullscreen) {
                    openOptionsMenu()
                } else {
                    showThemedToast(this@AbstractFlashcardViewer, getString(R.string.ankidroid_turn_on_fullscreen_options_menu), true)
                }
                return true
            }

            // Show Navigation Drawer from WebView
            if (url.startsWith("signal:anki_show_navigation_drawer")) {
                if (isFullscreen) {
                    onNavigationPressed()
                } else {
                    showThemedToast(this@AbstractFlashcardViewer, getString(R.string.ankidroid_turn_on_fullscreen_nav_drawer), true)
                }
                return true
            }

            // card.html reload
            if (url.startsWith("signal:reload_card_html")) {
                redrawCard()
                return true
            }
            // mark card using javascript
            if (url.startsWith("signal:mark_current_card")) {
                if (!mAnkiDroidJsAPI!!.isInit(AnkiDroidJsAPIConstants.MARK_CARD, AnkiDroidJsAPIConstants.ankiJsErrorCodeMarkCard)) {
                    return true
                }
                executeCommand(ViewerCommand.COMMAND_MARK)
                return true
            }
            // flag card (blue, green, orange, red) using javascript from AnkiDroid webview
            if (url.startsWith("signal:flag_")) {
                if (!mAnkiDroidJsAPI!!.isInit(AnkiDroidJsAPIConstants.TOGGLE_FLAG, AnkiDroidJsAPIConstants.ankiJsErrorCodeFlagCard)) {
                    return true
                }
                return when (url.replaceFirst("signal:flag_".toRegex(), "")) {
                    "none" -> {
                        executeCommand(ViewerCommand.COMMAND_UNSET_FLAG)
                        true
                    }
                    "red" -> {
                        executeCommand(ViewerCommand.COMMAND_TOGGLE_FLAG_RED)
                        true
                    }
                    "orange" -> {
                        executeCommand(ViewerCommand.COMMAND_TOGGLE_FLAG_ORANGE)
                        true
                    }
                    "green" -> {
                        executeCommand(ViewerCommand.COMMAND_TOGGLE_FLAG_GREEN)
                        true
                    }
                    "blue" -> {
                        executeCommand(ViewerCommand.COMMAND_TOGGLE_FLAG_BLUE)
                        true
                    }
                    else -> {
                        Timber.d("No such Flag found.")
                        true
                    }
                }
            }

            // Show toast using JS
            if (url.startsWith("signal:anki_show_toast:")) {
                val msg = url.replaceFirst("signal:anki_show_toast:".toRegex(), "")
                val msgDecode = decodeUrl(msg)
                showThemedToast(this@AbstractFlashcardViewer, msgDecode, true)
                return true
            }
            when (val signalOrdinal = WebViewSignalParserUtils.getSignalFromUrl(url)) {
                WebViewSignalParserUtils.SIGNAL_UNHANDLED -> {}
                WebViewSignalParserUtils.SIGNAL_NOOP -> return true
                WebViewSignalParserUtils.TYPE_FOCUS -> {
                    // Hide the “SHOW ANSWER” button when the input has focus. The soft keyboard takes up enough
                    // space by itself.
                    flipCardLayout!!.visibility = View.GONE
                    return true
                }
                WebViewSignalParserUtils.RELINQUISH_FOCUS -> {
                    // #5811 - The WebView could be focused via mouse. Allow components to return focus to Android.
                    focusAnswerCompletionField()
                    return true
                }
                WebViewSignalParserUtils.SHOW_ANSWER -> {
                    // display answer when showAnswer() called from card.js
                    if (!Companion.sDisplayAnswer) {
                        displayCardAnswer()
                    }
                    return true
                }
                WebViewSignalParserUtils.ANSWER_ORDINAL_1 -> {
                    flipOrAnswerCard(EASE_1)
                    return true
                }
                WebViewSignalParserUtils.ANSWER_ORDINAL_2 -> {
                    flipOrAnswerCard(EASE_2)
                    return true
                }
                WebViewSignalParserUtils.ANSWER_ORDINAL_3 -> {
                    flipOrAnswerCard(EASE_3)
                    return true
                }
                WebViewSignalParserUtils.ANSWER_ORDINAL_4 -> {
                    flipOrAnswerCard(EASE_4)
                    return true
                }
                else -> {
                    // We know it was a signal, but forgot a case in the case statement.
                    // This is not the same as SIGNAL_UNHANDLED, where it isn't a known signal.
                    Timber.w("Unhandled signal case: %d", signalOrdinal)
                    return true
                }
            }
            var intent: Intent? = null
            try {
                if (url.startsWith("intent:")) {
                    intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
                } else if (url.startsWith("android-app:")) {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1) {
                        intent = Intent.parseUri(url, 0)
                        intent.data = null
                        intent.setPackage(Uri.parse(url).host)
                    } else {
                        intent = Intent.parseUri(url, Intent.URI_ANDROID_APP_SCHEME)
                    }
                }
                if (intent != null) {
                    if (packageManager.resolveActivity(intent, 0) == null) {
                        val packageName = intent.getPackage()
                        if (packageName == null) {
                            Timber.d("Not using resolved intent uri because not available: %s", intent)
                            intent = null
                        } else {
                            Timber.d("Resolving intent uri to market uri because not available: %s", intent)
                            intent = Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("market://details?id=$packageName")
                            )
                            if (packageManager.resolveActivity(intent, 0) == null) {
                                intent = null
                            }
                        }
                    } else {
                        // https://developer.chrome.com/multidevice/android/intents says that we should remove this
                        intent.addCategory(Intent.CATEGORY_BROWSABLE)
                    }
                }
            } catch (t: Throwable) {
                Timber.w("Unable to parse intent uri: %s because: %s", url, t.message)
            }
            if (intent == null) {
                Timber.d("Opening external link \"%s\" with an Intent", url)
                intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            } else {
                Timber.d("Opening resolved external link \"%s\" with an Intent: %s", url, intent)
            }
            try {
                startActivityWithoutAnimation(intent)
            } catch (e: ActivityNotFoundException) {
                Timber.w(e) // Don't crash if the intent is not handled
            }
            return true
        }

        /**
         * Check if the user clicked on another audio icon or the audio itself finished
         * Also, Check if the user clicked on the running audio icon
         * @param url
         */
        private fun controlSound(url: String) {
            val replacedUrl = url.replaceFirst("playsound:".toRegex(), "")
            if (replacedUrl != mSoundPlayer.currentAudioUri || mSoundPlayer.isCurrentAudioFinished) {
                onCurrentAudioChanged(replacedUrl)
            } else {
                mSoundPlayer.playOrPauseSound()
            }
        }

        private fun onCurrentAudioChanged(url: String) {
            mSoundPlayer.playSound(url, null, null, soundErrorListener)
        }

        private fun decodeUrl(url: String): String {
            try {
                return URLDecoder.decode(url, "UTF-8")
            } catch (e: UnsupportedEncodingException) {
                Timber.e(e, "UTF-8 isn't supported as an encoding?")
            } catch (e: Exception) {
                Timber.e(e, "Exception decoding: '%s'", url)
                showThemedToast(this@AbstractFlashcardViewer, getString(R.string.card_viewer_url_decode_error), true)
            }
            return ""
        }

        // Run any post-load events in javascript that rely on the window being completely loaded.
        override fun onPageFinished(view: WebView, url: String) {
            Timber.d("Java onPageFinished triggered: %s", url)

            // onPageFinished will be called multiple times if the WebView redirects by setting window.location.href
            if (url == mViewerUrl) {
                onPageFinishedCallback?.onPageFinished()
                Timber.d("New URL, triggering JS onPageFinished: %s", url)
                view.loadUrl("javascript:onPageFinished();")
            }
        }

        @TargetApi(Build.VERSION_CODES.O)
        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            return mOnRenderProcessGoneDelegate.onRenderProcessGone(view, detail)
        }
    }

    private val mDisplayMediaLoadedFromHttpWarningSnackbar = MaxExecFunction(3) {
        val onClickListener = View.OnClickListener { openUrl(Uri.parse(getString(R.string.link_faq_external_http_content))) }
        showSnackbar(getString(R.string.cannot_load_http_resource), R.string.help, onClickListener)
    }

    private fun displayCouldNotFindMediaSnackbar(filename: String?) {
        val onClickListener = View.OnClickListener { openUrl(Uri.parse(getString(R.string.link_faq_missing_media))) }
        showSnackbar(getString(R.string.card_viewer_could_not_find_image, filename), R.string.help, onClickListener)
    }

    private fun displayMediaUpgradeRequiredSnackbar() {
        val onClickListener = View.OnClickListener { openUrl(Uri.parse(getString(R.string.link_faq_invalid_protocol_relative))) }
        showSnackbar(getString(R.string.card_viewer_media_relative_protocol), R.string.help, onClickListener)
    }

    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    protected val typedInputText get() = typeAnswer!!.input

    @SuppressLint("WebViewApiAvailability")
    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    fun handleUrlFromJavascript(url: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // WebViewCompat recommended here, but I'll avoid the dependency as it's test code
            val c = webView?.webViewClient as? CardViewerWebClient?
                ?: throw IllegalStateException("Couldn't obtain WebView - maybe it wasn't created yet")
            c.filterUrl(url)
        } else {
            throw IllegalStateException("Can't get WebViewClient due to Android API")
        }
    }

    @VisibleForTesting
    fun loadInitialCard() {
        GetCard().runWithHandler(answerCardHandler(false))
    }

    override val isDisplayingAnswer
        get() = sDisplayAnswer

    override fun isControlBlocked(): Boolean {
        return controlBlocked !== ControlBlock.UNBLOCKED
    }

    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    @KotlinCleanup("move to test class as extension")
    val correctTypedAnswer get() = typeAnswer!!.correct

    internal fun showTagsDialog() {
        val tags = ArrayList(col.tags.all())
        val selTags = ArrayList(mCurrentCard!!.note().tags)
        val dialog = mTagsDialogFactory!!.newTagsDialog().withArguments(TagsDialog.DialogType.EDIT_TAGS, selTags, tags)
        showDialogFragment(dialog)
    }

    override fun onSelectedTags(selectedTags: List<String>, indeterminateTags: List<String>, option: Int) {
        if (mCurrentCard!!.note().tags != selectedTags) {
            val tagString = TextUtils.join(" ", selectedTags)
            val note = mCurrentCard!!.note()
            note.setTagsFromStr(tagString)
            note.flush()
            // Reload current card to reflect tag changes
            displayCardQuestion(true)
        }
    }

    open fun javaScriptFunction(): AnkiDroidJsAPI? {
        return AnkiDroidJsAPI(this)
    }

    companion object {
        /**
         * Result codes that are returned when this activity finishes.
         */
        const val RESULT_DEFAULT = 50
        const val RESULT_NO_MORE_CARDS = 52
        const val RESULT_ABORT_AND_SYNC = 53

        /**
         * Available options performed by other activities.
         */
        const val EDIT_CURRENT_CARD = 0
        const val DECK_OPTIONS = 1
        const val EASE_1 = 1
        const val EASE_2 = 2
        const val EASE_3 = 3
        const val EASE_4 = 4

        /**
         * Time to wait in milliseconds before resuming fullscreen mode
         *
         * Should be protected, using non-JVM static members protected in the superclass companion is unsupported yet
         */
        const val INITIAL_HIDE_DELAY = 200
        // I don't see why we don't do this by intent.
        /** to be sent to and from the card editor  */
        @JvmStatic
        @set:VisibleForTesting(otherwise = VisibleForTesting.NONE)
        var editorCard: Card? = null
        @JvmField
        internal var sDisplayAnswer = false
        const val DOUBLE_TAP_TIME_INTERVAL = "doubleTapTimeInterval"
        const val DEFAULT_DOUBLE_TAP_TIME_INTERVAL = 200

        /** Handle providing help for "Image Not Found"  */
        private val mMissingImageHandler = MissingImageHandler()

        @KotlinCleanup("moved from MyGestureDetector")
        // Android design spec for the size of the status bar.
        private const val NO_GESTURE_BORDER_DIP = 24

        @KotlinCleanup("moved from SelectEaseHandler")
        // maximum screen distance from initial touch where we will consider a click related to the touch
        private const val CLICK_ACTION_THRESHOLD = 200
    }
}
