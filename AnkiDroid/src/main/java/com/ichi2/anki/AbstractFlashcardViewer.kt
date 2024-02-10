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
import android.graphics.Bitmap
import android.graphics.Color
import android.hardware.SensorManager
import android.net.Uri
import android.os.*
import android.view.*
import android.view.GestureDetector.SimpleOnGestureListener
import android.view.View.OnTouchListener
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.*
import android.webkit.WebView.HitTestResult
import android.widget.*
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.CheckResult
import androidx.annotation.IdRes
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AlertDialog
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle.State.RESUMED
import androidx.webkit.WebViewAssetLoader
import anki.collection.OpChanges
import com.drakeet.drawer.FullDraggableContainer
import com.google.android.material.snackbar.Snackbar
import com.ichi2.anim.ActivityTransitionAnimation
import com.ichi2.anim.ActivityTransitionAnimation.getInverseTransition
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.UIUtils.showThemedToast
import com.ichi2.anki.cardviewer.*
import com.ichi2.anki.cardviewer.CardHtml.Companion.legacyGetTtsTags
import com.ichi2.anki.cardviewer.HtmlGenerator.Companion.createInstance
import com.ichi2.anki.cardviewer.TypeAnswer.Companion.createInstance
import com.ichi2.anki.dialogs.TtsVoicesDialogFragment
import com.ichi2.anki.dialogs.tags.TagsDialog
import com.ichi2.anki.dialogs.tags.TagsDialogFactory
import com.ichi2.anki.dialogs.tags.TagsDialogListener
import com.ichi2.anki.model.CardStateFilter
import com.ichi2.anki.pages.AnkiServer.Companion.LOCALHOST
import com.ichi2.anki.pages.CongratsPage
import com.ichi2.anki.preferences.sharedPrefs
import com.ichi2.anki.receiver.SdCardReceiver
import com.ichi2.anki.reviewer.*
import com.ichi2.anki.reviewer.AutomaticAnswer.AutomaticallyAnswered
import com.ichi2.anki.reviewer.FullScreenMode.Companion.DEFAULT
import com.ichi2.anki.reviewer.FullScreenMode.Companion.fromPreference
import com.ichi2.anki.servicelayer.LanguageHintService.applyLanguageHint
import com.ichi2.anki.servicelayer.NoteService.isMarked
import com.ichi2.anki.services.migrationServiceWhileStartedOrNull
import com.ichi2.anki.snackbar.BaseSnackbarBuilderProvider
import com.ichi2.anki.snackbar.SnackbarBuilder
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.anki.utils.OnlyOnce.Method.ANSWER_CARD
import com.ichi2.anki.utils.OnlyOnce.preventSimultaneousExecutions
import com.ichi2.annotations.NeedsTest
import com.ichi2.compat.CompatHelper.Companion.resolveActivityCompat
import com.ichi2.compat.ResolveInfoFlagsCompat
import com.ichi2.libanki.*
import com.ichi2.libanki.Collection
import com.ichi2.libanki.Consts.BUTTON_TYPE
import com.ichi2.libanki.Sound.getAvTag
import com.ichi2.themes.Themes
import com.ichi2.themes.Themes.getResFromAttr
import com.ichi2.ui.FixedEditText
import com.ichi2.utils.*
import com.ichi2.utils.ClipboardUtil.getText
import com.ichi2.utils.HandlerUtils.executeFunctionWithDelay
import com.ichi2.utils.HandlerUtils.newHandler
import com.ichi2.utils.HashUtil.hashSetInit
import com.ichi2.utils.WebViewDebugging.initializeDebugging
import com.squareup.seismic.ShakeDetector
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import java.io.*
import java.net.URLDecoder
import java.util.*
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReadWriteLock
import java.util.concurrent.locks.ReentrantReadWriteLock
import java.util.function.Consumer
import java.util.function.Function
import kotlin.math.abs

@KotlinCleanup("lots to deal with")
abstract class AbstractFlashcardViewer :
    NavigationDrawerActivity(),
    ViewerCommand.CommandProcessor,
    TagsDialogListener,
    WhiteboardMultiTouchMethods,
    AutomaticallyAnswered,
    OnPageFinishedCallback,
    BaseSnackbarBuilderProvider,
    ChangeManager.Subscriber {
    private var ttsInitialized = false
    private var replayOnTtsInit = false
    private var ankiDroidJsAPI: AnkiDroidJsAPI? = null

    /**
     * Broadcast that informs us when the sd card is about to be unmounted
     */
    private var unmountReceiver: BroadcastReceiver? = null
    private var tagsDialogFactory: TagsDialogFactory? = null

    /**
     * Variables to hold preferences
     */
    @KotlinCleanup("internal for AnkiDroidJsApi")
    internal var prefShowTopbar = false
    protected var fullscreenMode = DEFAULT
        private set
    private var relativeButtonSize = 0
    private var minimalClickSpeed = 0
    private var doubleScrolling = false
    private var gesturesEnabled = false
    private var largeAnswerButtons = false
    protected var answerButtonsPosition: String? = "bottom"
    private var doubleTapTimeInterval = DEFAULT_DOUBLE_TAP_TIME_INTERVAL

    // Android WebView
    var automaticAnswer = AutomaticAnswer.defaultInstance(this)

    @VisibleForTesting(otherwise = VisibleForTesting.PROTECTED)
    internal var typeAnswer: TypeAnswer? = null

    /** Generates HTML content  */
    private var htmlGenerator: HtmlGenerator? = null

    // Default short animation duration, provided by Android framework
    private var shortAnimDuration = 0
    private var backButtonPressedToReturn = false

    // Preferences from the collection
    private var showNextReviewTime = false
    private var isSelecting = false
    private var inAnswer = false

    /**
     * Variables to hold layout objects that we need to update or handle events for
     */
    var webView: WebView? = null
        private set
    private var cardFrame: FrameLayout? = null
    private var touchLayer: FrameLayout? = null
    protected var answerField: FixedEditText? = null
    protected var flipCardLayout: FrameLayout? = null
    private var easeButtonsLayout: LinearLayout? = null

    @KotlinCleanup("internal for AnkiDroidJsApi")
    internal var easeButton1: EaseButton? = null

    @KotlinCleanup("internal for AnkiDroidJsApi")
    internal var easeButton2: EaseButton? = null

    @KotlinCleanup("internal for AnkiDroidJsApi")
    internal var easeButton3: EaseButton? = null

    @KotlinCleanup("internal for AnkiDroidJsApi")
    internal var easeButton4: EaseButton? = null
    protected var topBarLayout: RelativeLayout? = null
    private val clipboard: ClipboardManager? = null
    private var previousAnswerIndicator: PreviousAnswerIndicator? = null

    private var currentEase = 0
    private var initialFlipCardHeight = 0
    private var buttonHeightSet = false

    /**
     * A record of the last time the "show answer" or ease buttons were pressed. We keep track
     * of this time to ignore accidental button presses.
     */
    @VisibleForTesting
    protected var lastClickTime: Long = 0

    /**
     * Swipe Detection
     */
    var gestureDetector: GestureDetector? = null
        private set
    private lateinit var gestureDetectorImpl: MyGestureDetector
    private var isXScrolling = false
    private var isYScrolling = false

    /**
     * Gesture Allocation
     */
    protected val gestureProcessor = GestureProcessor(this)

    /** Handle joysticks/pedals */
    // needs to be lateinit due to a reliance on Context
    protected lateinit var motionEventHandler: MotionEventHandler

    @get:VisibleForTesting
    var cardContent: String? = null
        private set
    open val baseUrl = "http://$LOCALHOST"
    open val webviewDomain = LOCALHOST
    private var viewerUrl: String? = null
    private val fadeDuration = 300

    @VisibleForTesting(otherwise = VisibleForTesting.PROTECTED)
    internal lateinit var soundPlayer: SoundPlayer

    /** Reference to the parent of the cardFrame to allow regeneration of the cardFrame in case of crash  */
    private var cardFrameParent: ViewGroup? = null

    /** Lock to allow thread-safe regeneration of mCard  */
    private val cardLock: ReadWriteLock = ReentrantReadWriteLock()

    /** Preference: Whether the user wants press back twice to return to the main screen"  */
    private var exitViaDoubleTapBack = false

    @VisibleForTesting
    val onRenderProcessGoneDelegate = OnRenderProcessGoneDelegate(this)
    protected val tts = TTS()

    // ----------------------------------------------------------------------------
    // LISTENERS
    // ----------------------------------------------------------------------------
    private val longClickHandler = newHandler()
    private val startLongClickAction = Runnable { gestureProcessor.onLongTap() }

    // Handler for the "show answer" button
    private val flipCardListener = View.OnClickListener {
        Timber.i("AbstractFlashcardViewer:: Show answer button pressed")
        // Ignore what is most likely an accidental double-tap.
        if (elapsedRealTime - lastClickTime < doubleTapTimeInterval) {
            return@OnClickListener
        }
        lastClickTime = elapsedRealTime
        automaticAnswer.onShowAnswer()
        displayCardAnswer()
    }

    internal val migrationService by migrationServiceWhileStartedOrNull()

    /**
     * Changes which were received when the viewer was in the background
     * which should be executed once the viewer is visible again
     * @see opExecuted
     * @see refreshIfRequired
     */
    private var refreshRequired: ViewerRefresh? = null

    private val editCurrentCardLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
        FlashCardViewerResultCallback { result, reloadRequired ->
            if (result.resultCode == RESULT_OK) {
                // content of note was changed so update the note and current card
                Timber.i("AbstractFlashcardViewer:: Saving card...")
                launchCatchingTask { saveEditedCard() }
                onEditedNoteChanged()
            } else if (result.resultCode == RESULT_CANCELED && !reloadRequired) {
                // nothing was changed by the note editor so just redraw the card
                redrawCard()
            }
        }
    )

    protected inner class FlashCardViewerResultCallback(
        private val callback: (result: ActivityResult, reloadRequired: Boolean) -> Unit = { _, _ -> }
    ) : ActivityResultCallback<ActivityResult> {
        override fun onActivityResult(result: ActivityResult) {
            if (result.resultCode == DeckPicker.RESULT_DB_ERROR) {
                closeReviewer(DeckPicker.RESULT_DB_ERROR)
            }
            if (result.resultCode == DeckPicker.RESULT_MEDIA_EJECTED) {
                finishNoStorageAvailable()
            }

            /* Reset the schedule and reload the latest card off the top of the stack if required.
               The card could have been rescheduled, the deck could have changed, or a change of
               note type could have lead to the card being deleted */
            val reloadRequired =
                result.data?.getBooleanExtra(NoteEditor.RELOAD_REQUIRED_EXTRA_KEY, false) == true
            if (reloadRequired) {
                performReload()
            }

            callback(result, reloadRequired)
        }
    }

    init {
        ChangeManager.subscribe(this)
    }

    // Event handler for eases (answer buttons)
    inner class SelectEaseHandler : View.OnClickListener, OnTouchListener {
        private var prevCard: Card? = null
        private var hasBeenTouched = false
        private var touchX = 0f
        private var touchY = 0f
        override fun onTouch(view: View, event: MotionEvent): Boolean {
            if (event.action == MotionEvent.ACTION_DOWN) {
                // Save states when button pressed
                prevCard = currentCard
                hasBeenTouched = true
                // We will need to check if a touch is followed by a click
                // Since onTouch always come before onClick, we should check if
                // the touch is going to be a click by storing the start coordinates
                // and comparing with the end coordinates of the touch
                touchX = event.rawX
                touchY = event.rawY
            } else if (event.action == MotionEvent.ACTION_UP) {
                val diffX = abs(event.rawX - touchX)
                val diffY = abs(event.rawY - touchY)
                // If a click is not coming then we reset the touch
                if (diffX > Companion.CLICK_ACTION_THRESHOLD || diffY > Companion.CLICK_ACTION_THRESHOLD) {
                    hasBeenTouched = false
                }
            }
            return false
        }

        override fun onClick(view: View) {
            // Try to perform intended action only if the button has been pressed for current card,
            // or if the button was not touched,
            if (prevCard === currentCard || !hasBeenTouched) {
                // Only perform if the click was not an accidental double-tap
                if (elapsedRealTime - lastClickTime >= doubleTapTimeInterval) {
                    // For whatever reason, performClick does not return a visual feedback anymore
                    if (!hasBeenTouched) {
                        view.isPressed = true
                    }
                    lastClickTime = elapsedRealTime
                    automaticAnswer.onSelectEase()
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

                        else -> currentEase = 0
                    }
                    if (!hasBeenTouched) {
                        view.isPressed = false
                    }
                }
            }
            // We will have to reset the touch after every onClick event
            // Do not return early without considering this
            hasBeenTouched = false
        }
    }

    private val easeHandler = SelectEaseHandler()

    @get:VisibleForTesting
    protected open val elapsedRealTime: Long
        get() = SystemClock.elapsedRealtime()
    private val gestureListener = OnTouchListener { _, event ->
        if (gestureDetector!!.onTouchEvent(event)) {
            return@OnTouchListener true
        }
        if (!gestureDetectorImpl.eventCanBeSentToWebView(event)) {
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
        val readLock = cardLock.readLock()
        return try {
            readLock.lock()
            cardFunction.apply(webView)
        } finally {
            readLock.unlock()
        }
    }

    suspend fun saveEditedCard() {
        val card = editorCard!!
        withProgress {
            undoableOp {
                updateNote(card.note())
            }
        }
        onCardUpdated(card)
    }

    private fun onCardUpdated(result: Card) {
        if (currentCard !== result) {
            /*
             * Before updating currentCard, we check whether it is changing or not. If the current card changes,
             * then we need to display it as a new card, without showing the answer.
             */
            displayAnswer = false
        }
        currentCard = result
        if (currentCard == null) {
            // If the card is null means that there are no more cards scheduled for review.
            showProgressBar()
            closeReviewer(RESULT_NO_MORE_CARDS)
        }
        onCardEdited(currentCard!!)
        if (displayAnswer) {
            displayCardAnswer()
        } else {
            displayCardQuestion()
        }
        hideProgressBar()
    }

    /** Operation after a card has been updated due to being edited. Called before display[Question/Answer]  */
    protected open fun onCardEdited(card: Card) {
        // intentionally blank
    }

    /** Invoked by [CardViewerWebClient.onPageFinished] */
    override fun onPageFinished(view: WebView) {
        // intentionally blank
    }

    /** Called after an undo or undoable operation takes place. * Should set currentCard to the current card to display. */
    open suspend fun updateCurrentCard() {
        // Legacy tests assume the current card will be grabbed from the collection,
        // despite that making no sense outside of Reviewer.kt
        currentCard = withCol {
            sched.card?.apply {
                renderOutput()
            }
        }
    }

    internal suspend fun updateCardAndRedraw() {
        refreshRequired = null // this method is called on refresh

        updateCurrentCard()

        if (currentCard == null) {
            closeReviewer(RESULT_NO_MORE_CARDS)
            // When launched with a shortcut, we want to display a message when finishing
            if (intent.getBooleanExtra(EXTRA_STARTED_WITH_SHORTCUT, false)) {
                startActivity(CongratsPage.getIntent(this))
            }
            return
        }

        // Start reviewing next card
        hideProgressBar()
        unblockControls()
        displayCardQuestion()
        // set the correct mark/unmark icon on action bar
        refreshActionBar()
        focusDefaultLayout()
    }

    private fun focusDefaultLayout() {
        findViewById<View>(R.id.root_layout).requestFocus()
    }

    // ----------------------------------------------------------------------------
    // ANDROID METHODS
    // ----------------------------------------------------------------------------
    override fun onCreate(savedInstanceState: Bundle?) {
        restorePreferences()
        tagsDialogFactory = TagsDialogFactory(this).attachToActivity<TagsDialogFactory>(this)
        super.onCreate(savedInstanceState)
        motionEventHandler = MotionEventHandler.createInstance(this)

        // Issue 14142: The reviewer had a focus highlight after answering using a keyboard.
        // This theme removes the highlight, but there is likely a better way.
        this.setTheme(R.style.ThemeOverlay_DisableKeyboardHighlight)

        setContentView(getContentViewAttr(fullscreenMode))

        // Make ACTION_PROCESS_TEXT for in-app searching possible on > Android 4.0
        delegate.isHandleNativeActionModesEnabled = true
        val mainView = findViewById<View>(android.R.id.content)
        initNavigationDrawer(mainView)
        previousAnswerIndicator = PreviousAnswerIndicator(findViewById(R.id.chosen_answer))
        shortAnimDuration = resources.getInteger(android.R.integer.config_shortAnimTime)
        gestureDetectorImpl = LinkDetectingGestureDetector()
        TtsVoicesFieldFilter.ensureApplied()
    }

    protected open fun getContentViewAttr(fullscreenMode: FullScreenMode): Int {
        return R.layout.reviewer
    }

    @get:VisibleForTesting(otherwise = VisibleForTesting.PROTECTED)
    val isFullscreen: Boolean
        get() = !supportActionBar!!.isShowing

    override fun onConfigurationChanged(newConfig: Configuration) {
        // called when screen rotated, etc, since recreating the Webview is too expensive
        super.onConfigurationChanged(newConfig)
        refreshActionBar()
    }

    // Finish initializing the activity after the collection has been correctly loaded
    public override fun onCollectionLoaded(col: Collection) {
        super.onCollectionLoaded(col)
        val mediaDir = col.media.dir
        soundPlayer = SoundPlayer.newInstance(this, getMediaBaseUrl(mediaDir))
        registerExternalStorageListener()
        restoreCollectionPreferences(col)
        initLayout()
        htmlGenerator = createInstance(this, col, typeAnswer!!)

        // Initialize text-to-speech. This is an asynchronous operation.
        tts.initialize(this, ReadTextListener())
        updateActionBar()
        invalidateOptionsMenu()
    }

    // Saves deck each time Reviewer activity loses focus
    override fun onPause() {
        super.onPause()
        automaticAnswer.disable()
        gestureDetectorImpl.stopShakeDetector()
        if (this::soundPlayer.isInitialized) {
            soundPlayer.isEnabled = false
        }
        longClickHandler.removeCallbacks(startLongClickAction)
        // Prevent loss of data in Cookies
        CookieManager.getInstance().flush()
    }

    override fun onResume() {
        super.onResume()
        automaticAnswer.enable()
        gestureDetectorImpl.startShakeDetector()
        if (this::soundPlayer.isInitialized) {
            soundPlayer.isEnabled = true
        }
        // Reset the activity title
        updateActionBar()
        selectNavigationItem(-1)
        refreshIfRequired(isResuming = true)
    }

    /**
     * If the activity is [RESUMED], or is called from [onResume] then execute the pending
     * operations in [refreshRequired].
     *
     * If the activity is NOT [RESUMED], wait until [onResume]
     */
    @NeedsTest("if opExecuted is called while activity is in the background, audio plays onResume")
    private fun refreshIfRequired(isResuming: Boolean = false) {
        // Defer the execution of `opExecuted` until the user is looking at the screen.
        // This ensures that audio/timers are not accidentally started
        if (isResuming || lifecycle.currentState.isAtLeast(RESUMED)) {
            refreshRequired?.let {
                Timber.d("refreshIfRequired: redraw")
                // if changing code, re-evaluate `refreshRequired = null` in `updateCardAndRedraw`
                launchCatchingTask { updateCardAndRedraw() }
                refreshRequired = null
            }
        } else if (refreshRequired != null) {
            // onResume() will execute this method
            Timber.d("deferred refresh as activity was not STARTED")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        tts.releaseTts(this)
        if (unmountReceiver != null) {
            unregisterReceiver(unmountReceiver)
        }
        // WebView.destroy() should be called after the end of use
        // http://developer.android.com/reference/android/webkit/WebView.html#destroy()
        if (cardFrame != null) {
            cardFrame!!.removeAllViews()
        }
        destroyWebView(webView) // OK to do without a lock
        if (this::soundPlayer.isInitialized) {
            soundPlayer.close()
        }
    }

    override fun onBackPressed() {
        if (isDrawerOpen) {
            super.onBackPressed()
        } else {
            Timber.i("Back key pressed")
            if (!exitViaDoubleTapBack || backButtonPressedToReturn) {
                closeReviewer(RESULT_DEFAULT)
            } else {
                showSnackbar(R.string.back_pressed_once_reviewer, Snackbar.LENGTH_SHORT)
            }
            backButtonPressedToReturn = true
            executeFunctionWithDelay(Consts.SHORT_TOAST_DURATION) {
                backButtonPressedToReturn = false
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (processCardFunction { cardWebView: WebView? ->
            processHardwareButtonScroll(
                    keyCode,
                    cardWebView
                )
        }
        ) {
            return true
        }

        // Subclasses other than 'Reviewer' have not been setup with Gestures/KeyPresses
        // so hardcode this functionality for now.
        // This is in onKeyDown to match the gesture processor in the Reviewer
        if (!displayAnswer && !answerFieldIsFocused()) {
            if (keyCode == KeyEvent.KEYCODE_SPACE || keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) {
                displayCardAnswer()
                return true
            }
        }

        if (webView.handledGamepadKeyDown(keyCode, event)) {
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (webView.handledGamepadKeyUp(keyCode, event)) {
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    public override val currentCardId: CardId? get() = currentCard?.id

    private fun processHardwareButtonScroll(keyCode: Int, card: WebView?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_PAGE_UP) {
            card!!.pageUp(false)
            if (doubleScrolling) {
                card.pageUp(false)
            }
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_PAGE_DOWN) {
            card!!.pageDown(false)
            if (doubleScrolling) {
                card.pageDown(false)
            }
            return true
        }
        return false
    }

    protected open fun answerFieldIsFocused(): Boolean {
        return answerField != null && answerField!!.isFocused
    }

    protected fun clipboardHasText(): Boolean {
        return !getText(clipboard).isNullOrEmpty()
    }

    /**
     * Returns the text stored in the clipboard or the empty string if the clipboard is empty or contains something that
     * cannot be converted to text.
     *
     * @return the text in clipboard or the empty string.
     */
    private fun clipboardGetText(): CharSequence {
        val text = getText(clipboard)
        return text ?: ""
    }

    val deckOptionsLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { _ ->
            Timber.i("Returned from deck options -> Restarting activity")
            performReload()
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
    protected val parentDid: DeckId
        get() = getColUnsafe.decks.selected()

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
        if (unmountReceiver == null) {
            unmountReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    if (intent.action == SdCardReceiver.MEDIA_EJECT) {
                        finish()
                    }
                }
            }
            val iFilter = IntentFilter()
            iFilter.addAction(SdCardReceiver.MEDIA_EJECT)
            registerReceiver(unmountReceiver, iFilter)
        }
    }

    open fun undo(): Job {
        return launchCatchingTask {
            undoAndShowSnackbar(duration = Reviewer.ACTION_SNACKBAR_TIME)
        }
    }

    private fun finishNoStorageAvailable() {
        this@AbstractFlashcardViewer.setResult(DeckPicker.RESULT_MEDIA_EJECTED)
        finish()
    }

    protected open fun editCard(fromGesture: Gesture? = null) {
        if (currentCard == null) {
            // This should never occurs. It means the review button was pressed while there is no more card in the reviewer.
            return
        }
        val editCard = Intent(this@AbstractFlashcardViewer, NoteEditor::class.java)
        val animation = getAnimationTransitionFromGesture(fromGesture)
        editCard.putExtra(NoteEditor.EXTRA_CALLER, NoteEditor.CALLER_REVIEWER_EDIT)
        editCard.putExtra(FINISH_ANIMATION_EXTRA, getInverseTransition(animation) as Parcelable)
        editorCard = currentCard
        editCurrentCardLauncher.launch(editCard)
    }

    protected fun showDeleteNoteDialog() {
        AlertDialog.Builder(this).show {
            title(R.string.delete_card_title)
            setIcon(R.drawable.ic_warning)
            message(
                text = resources.getString(
                    R.string.delete_note_message,
                    Utils.stripHTML(currentCard!!.question(getColUnsafe, true))
                )
            )
            positiveButton(R.string.dialog_positive_delete) {
                Timber.i(
                    "AbstractFlashcardViewer:: OK button pressed to delete note %d",
                    currentCard!!.nid
                )
                launchCatchingTask { soundPlayer.stopSounds() }
                deleteNoteWithoutConfirmation()
            }
            negativeButton(R.string.dialog_cancel)
        }
    }

    /** Consumers should use [.showDeleteNoteDialog]   */
    private fun deleteNoteWithoutConfirmation() {
        val cardId = currentCard!!.id
        launchCatchingTask {
            val noteCount = withProgress() {
                undoableOp {
                    removeNotes(cids = listOf(cardId))
                }.count
            }
            val deletedMessage = resources.getQuantityString(
                R.plurals.card_browser_cards_deleted,
                noteCount,
                noteCount
            )
            showSnackbar(deletedMessage, Snackbar.LENGTH_LONG) {
                setAction(R.string.undo) { launchCatchingTask { undoAndShowSnackbar() } }
            }
        }
    }

    open fun answerCard(@BUTTON_TYPE ease: Int) = preventSimultaneousExecutions(ANSWER_CARD) {
        launchCatchingTask {
            if (inAnswer) {
                return@launchCatchingTask
            }
            isSelecting = false
            if (previousAnswerIndicator == null) {
                // workaround for a broken ReviewerKeyboardInputTest
                return@launchCatchingTask
            }
            // Temporarily sets the answer indicator dots appearing below the toolbar
            previousAnswerIndicator?.displayAnswerIndicator(ease)
            soundPlayer.stopSounds()
            currentEase = ease

            answerCardInner(ease)
            updateCardAndRedraw()
        }
    }

    open suspend fun answerCardInner(@BUTTON_TYPE ease: Int) {
        // Legacy tests assume they can call answerCard() even outside of Reviewer
        withCol {
            sched.answerCard(currentCard!!, ease)
        }
    }

    // Set the content view to the one provided and initialize accessors.
    @KotlinCleanup("Move a lot of these to onCreate()")
    protected open fun initLayout() {
        topBarLayout = findViewById(R.id.top_bar)
        cardFrame = findViewById(R.id.flashcard)
        cardFrameParent = cardFrame!!.parent as ViewGroup
        touchLayer =
            findViewById<FrameLayout>(R.id.touch_layer).apply { setOnTouchListener(gestureListener) }
        cardFrame!!.removeAllViews()

        // Initialize swipe
        gestureDetector = GestureDetector(this, gestureDetectorImpl)
        easeButtonsLayout = findViewById(R.id.ease_buttons)
        easeButton1 = EaseButton(
            EASE_1,
            findViewById(R.id.flashcard_layout_ease1),
            findViewById(R.id.ease1),
            findViewById(R.id.nextTime1)
        ).apply { setListeners(easeHandler) }
        easeButton2 = EaseButton(
            EASE_2,
            findViewById(R.id.flashcard_layout_ease2),
            findViewById(R.id.ease2),
            findViewById(R.id.nextTime2)
        ).apply { setListeners(easeHandler) }
        easeButton3 = EaseButton(
            EASE_3,
            findViewById(R.id.flashcard_layout_ease3),
            findViewById(R.id.ease3),
            findViewById(R.id.nextTime3)
        ).apply { setListeners(easeHandler) }
        easeButton4 = EaseButton(
            EASE_4,
            findViewById(R.id.flashcard_layout_ease4),
            findViewById(R.id.ease4),
            findViewById(R.id.nextTime4)
        ).apply { setListeners(easeHandler) }
        if (!showNextReviewTime) {
            easeButton1!!.hideNextReviewTime()
            easeButton2!!.hideNextReviewTime()
            easeButton3!!.hideNextReviewTime()
            easeButton4!!.hideNextReviewTime()
        }
        flipCardLayout = findViewById(R.id.flashcard_layout_flip)
        flipCardLayout?.let { layout ->
            if (minimalClickSpeed == 0) {
                layout.setOnClickListener(flipCardListener)
            } else {
                val handler = Handler(Looper.getMainLooper())
                layout.setOnTouchListener { _, event ->
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            handler.postDelayed({
                                flipCardListener.onClick(layout)
                            }, minimalClickSpeed.toLong())
                            false
                        }

                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_HOVER_ENTER -> {
                            handler.removeCallbacksAndMessages(null)
                            false
                        }

                        else -> false
                    }
                }
            }
        }
        if (animationEnabled()) {
            flipCardLayout?.setBackgroundResource(getResFromAttr(this, R.attr.hardButtonRippleRef))
        }
        if (!buttonHeightSet && relativeButtonSize != 100) {
            val params = flipCardLayout!!.layoutParams
            params.height = params.height * relativeButtonSize / 100
            easeButton1!!.setButtonScale(relativeButtonSize)
            easeButton2!!.setButtonScale(relativeButtonSize)
            easeButton3!!.setButtonScale(relativeButtonSize)
            easeButton4!!.setButtonScale(relativeButtonSize)
            buttonHeightSet = true
        }
        initialFlipCardHeight = flipCardLayout!!.layoutParams.height
        if (largeAnswerButtons) {
            val params = flipCardLayout!!.layoutParams
            params.height = initialFlipCardHeight * 2
        }
        answerField = findViewById(R.id.answer_field)
        initControls()

        // Position answer buttons
        val answerButtonsPosition = this.sharedPrefs().getString(
            getString(R.string.answer_buttons_position_preference),
            "bottom"
        )
        this.answerButtonsPosition = answerButtonsPosition
        val answerArea = findViewById<LinearLayout>(R.id.bottom_area_layout)
        val answerAreaParams = answerArea.layoutParams as RelativeLayout.LayoutParams
        val whiteboardContainer = findViewById<FrameLayout>(R.id.whiteboard)
        val whiteboardContainerParams =
            whiteboardContainer.layoutParams as RelativeLayout.LayoutParams
        val flashcardContainerParams = cardFrame!!.layoutParams as RelativeLayout.LayoutParams
        val touchLayerContainerParams = touchLayer!!.layoutParams as RelativeLayout.LayoutParams
        when (answerButtonsPosition) {
            "top" -> {
                whiteboardContainerParams.addRule(RelativeLayout.BELOW, R.id.bottom_area_layout)
                flashcardContainerParams.addRule(RelativeLayout.BELOW, R.id.bottom_area_layout)
                touchLayerContainerParams.addRule(RelativeLayout.BELOW, R.id.bottom_area_layout)
                answerAreaParams.addRule(RelativeLayout.BELOW, R.id.mic_tool_bar_layer)
                answerArea.removeView(answerField)
                answerArea.addView(answerField, 1)
            }

            "bottom",
            "none" -> {
                whiteboardContainerParams.addRule(RelativeLayout.ABOVE, R.id.bottom_area_layout)
                whiteboardContainerParams.addRule(RelativeLayout.BELOW, R.id.mic_tool_bar_layer)
                flashcardContainerParams.addRule(RelativeLayout.ABOVE, R.id.bottom_area_layout)
                flashcardContainerParams.addRule(RelativeLayout.BELOW, R.id.mic_tool_bar_layer)
                touchLayerContainerParams.addRule(RelativeLayout.ABOVE, R.id.bottom_area_layout)
                touchLayerContainerParams.addRule(RelativeLayout.BELOW, R.id.mic_tool_bar_layer)
                answerAreaParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
            }

            else -> Timber.w("Unknown answerButtonsPosition: %s", answerButtonsPosition)
        }
        answerArea.visibility = if (answerButtonsPosition == "none") View.GONE else View.VISIBLE
        // workaround for #14419, iterate over the bottom area children and manually enable the
        // answer field while still hiding the other children
        if (answerButtonsPosition == "none") {
            answerArea.visibility = View.VISIBLE
            answerArea.children.forEach {
                it.visibility = if (it.id == R.id.answer_field) View.VISIBLE else View.GONE
            }
        }
        answerArea.layoutParams = answerAreaParams
        whiteboardContainer.layoutParams = whiteboardContainerParams
        cardFrame!!.layoutParams = flashcardContainerParams
        touchLayer!!.layoutParams = touchLayerContainerParams
    }

    protected open fun createWebView(): WebView {
        val assetLoader = getViewerAssetLoader(webviewDomain)
        val webView: WebView = MyWebView(this).apply {
            scrollBarStyle = View.SCROLLBARS_OUTSIDE_OVERLAY
            with(settings) {
                displayZoomControls = false
                builtInZoomControls = true
                setSupportZoom(true)
                loadWithOverviewMode = true
                javaScriptEnabled = true
                allowFileAccess = true
                // enable dom storage so that sessionStorage & localStorage can be used in webview
                domStorageEnabled = true
            }
            webChromeClient = AnkiDroidWebChromeClient()
            isFocusableInTouchMode = typeAnswer!!.autoFocus
            isScrollbarFadingEnabled = true
            // Set transparent color to prevent flashing white when night mode enabled
            setBackgroundColor(Color.argb(1, 0, 0, 0))
            webViewClient = CardViewerWebClient(assetLoader, this@AbstractFlashcardViewer)
        }
        Timber.d(
            "Focusable = %s, Focusable in touch mode = %s",
            webView.isFocusable,
            webView.isFocusableInTouchMode
        )

        // enable third party cookies so that cookies can be used in webview
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        return webView
    }

    /** If a card is displaying the question, flip it, otherwise answer it  */
    internal open fun flipOrAnswerCard(cardOrdinal: Int) {
        if (!displayAnswer) {
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
        val content = LayoutInflater.from(this@AbstractFlashcardViewer)
            .inflate(layoutId, null, false) as ViewGroup
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
        return showNextReviewTime
    }

    protected open fun displayAnswerBottomBar() {
        flipCardLayout!!.isClickable = false
        easeButtonsLayout!!.visibility = View.VISIBLE
        if (largeAnswerButtons) {
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
            easeButton2!!.addTo(row1)
            easeButton4!!.addTo(row1)
            easeButton1!!.addTo(row2)
            easeButton3!!.addTo(row2)
            easeButtonsLayout!!.addView(row1)
            easeButtonsLayout!!.addView(row2)
        }
        val after = Runnable { flipCardLayout!!.visibility = View.GONE }

        // hide "Show Answer" button
        if (animationDisabled()) {
            after.run()
        } else {
            flipCardLayout!!.alpha = 1f
            flipCardLayout!!.animate().alpha(0f).setDuration(shortAnimDuration.toLong())
                .withEndAction(after)
        }
    }

    protected open fun hideEaseButtons() {
        val after = Runnable { actualHideEaseButtons() }
        val easeButtonsVisible = easeButtonsLayout?.visibility == View.VISIBLE
        flipCardLayout?.isClickable = true
        flipCardLayout?.visibility = View.VISIBLE
        if (animationDisabled() || !easeButtonsVisible) {
            after.run()
        } else {
            flipCardLayout?.alpha = 0f
            flipCardLayout?.animate()?.alpha(1f)?.setDuration(shortAnimDuration.toLong())
                ?.withEndAction(after)
        }
        focusAnswerCompletionField()
    }

    private fun actualHideEaseButtons() {
        easeButtonsLayout?.visibility = View.GONE
        easeButton1?.hide()
        easeButton2?.hide()
        easeButton3?.hide()
        easeButton4?.hide()
    }

    /**
     * Focuses the appropriate field for an answer
     * And allows keyboard shortcuts to go to the default handlers.
     */
    private fun focusAnswerCompletionField() = runOnUiThread {
        // This does not handle mUseInputTag (the WebView contains an input field with a typable answer).
        // In this case, the user can use touch to focus the field if necessary.
        if (typeAnswer?.autoFocusEditText() == true) {
            answerField?.focusWithKeyboard()
        } else {
            flipCardLayout?.requestFocus()
        }
    }

    protected open fun switchTopBarVisibility(visible: Int) {
        previousAnswerIndicator!!.setVisibility(visible)
    }

    protected open fun initControls() {
        cardFrame!!.visibility = View.VISIBLE
        previousAnswerIndicator!!.setVisibility(View.VISIBLE)
        flipCardLayout!!.visibility = View.VISIBLE
        answerField!!.visibility = if (typeAnswer!!.validForEditText()) View.VISIBLE else View.GONE
        answerField!!.setOnEditorActionListener { _, actionId: Int, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                displayCardAnswer()
                return@setOnEditorActionListener true
            }
            false
        }
        answerField!!.setOnKeyListener { _, keyCode: Int, event: KeyEvent ->
            if (event.action == KeyEvent.ACTION_UP &&
                (keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER)
            ) {
                displayCardAnswer()
                return@setOnKeyListener true
            }
            false
        }
    }

    protected open fun restorePreferences(): SharedPreferences {
        val preferences = baseContext.sharedPrefs()
        typeAnswer = createInstance(preferences)
        // mDeckFilename = preferences.getString("deckFilename", "");
        minimalClickSpeed = preferences.getInt("showCardAnswerButtonTime", 0)
        fullscreenMode = fromPreference(preferences)
        relativeButtonSize = preferences.getInt("answerButtonSize", 100)
        tts.enabled = preferences.getBoolean("tts", false)
        doubleScrolling = preferences.getBoolean("double_scrolling", false)
        prefShowTopbar = preferences.getBoolean("showTopbar", true)
        largeAnswerButtons = preferences.getBoolean("showLargeAnswerButtons", false)
        doubleTapTimeInterval =
            preferences.getInt(DOUBLE_TAP_TIME_INTERVAL, DEFAULT_DOUBLE_TAP_TIME_INTERVAL)
        exitViaDoubleTapBack = preferences.getBoolean("exitViaDoubleTapBack", false)
        gesturesEnabled = preferences.getBoolean(GestureProcessor.PREF_KEY, false)
        if (gesturesEnabled) {
            gestureProcessor.init(preferences)
        }
        if (preferences.getBoolean("timeoutAnswer", false) || preferences.getBoolean(
                "keepScreenOn",
                false
            )
        ) {
            this.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        return preferences
    }

    protected open fun restoreCollectionPreferences(col: Collection) {
        // These are preferences we pull out of the collection instead of SharedPreferences
        try {
            showNextReviewTime = col.config.get("estTimes") ?: true
            val preferences = baseContext.sharedPrefs()
            automaticAnswer = AutomaticAnswer.createInstance(this, preferences, col)
        } catch (ex: Exception) {
            Timber.w(ex)
            onCollectionLoadError()
        }
    }

    private fun setInterface() {
        if (currentCard == null) {
            return
        }
        recreateWebView()
    }

    protected open fun recreateWebView() {
        if (webView == null) {
            webView = createWebView()
            initializeDebugging(this.sharedPrefs())
            cardFrame!!.addView(webView)
            gestureDetectorImpl.onWebViewCreated(webView!!)
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
            answerField!!.setText("")
        }
    }

    protected open fun updateActionBar() {
        updateDeckName()
    }

    private fun updateDeckName() {
        if (currentCard == null) return
        if (sharedPrefs().getBoolean("showDeckTitle", false)) {
            supportActionBar?.title = Decks.basename(getColUnsafe.decks.name(currentCard!!.did))
        }
        if (!prefShowTopbar) {
            topBarLayout!!.visibility = View.GONE
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

    private suspend fun automaticAnswerShouldWaitForAudio(): Boolean {
        return withCol {
            decks.confForDid(currentCard!!.did).optBoolean("waitForAudio", true)
        }
    }

    internal inner class ReadTextListener : ReadText.ReadTextListener {
        override fun onDone(playedSide: CardSide?) {
            Timber.d("done reading text")
            this@AbstractFlashcardViewer.onSoundGroupCompleted()
        }
    }

    open fun displayCardQuestion() {
        Timber.d("displayCardQuestion()")
        displayAnswer = false
        backButtonPressedToReturn = false
        setInterface()
        typeAnswer?.input = ""
        typeAnswer?.updateInfo(getColUnsafe, currentCard!!, resources)
        if (typeAnswer?.validForEditText() == true) {
            // Show text entry based on if the user wants to write the answer
            answerField?.visibility = View.VISIBLE
            answerField?.applyLanguageHint(typeAnswer?.languageHint)
        } else {
            answerField?.visibility = View.GONE
        }
        val content = htmlGenerator!!.generateHtml(getColUnsafe, currentCard!!, SingleCardSide.FRONT)
        automaticAnswer.onDisplayQuestion()
        launchCatchingTask {
            if (!automaticAnswerShouldWaitForAudio()) {
                automaticAnswer.scheduleAutomaticDisplayAnswer()
            }
        }
        updateCard(content)
        hideEaseButtons()
        // If Card-based TTS is enabled, we "automatic display" after the TTS has finished as we don't know the duration
        Timber.i(
            "AbstractFlashcardViewer:: Question successfully shown for card id %d",
            currentCard!!.id
        )
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PROTECTED)
    open fun displayCardAnswer() {
        // #7294 Required in case the animation end action does not fire:
        actualHideEaseButtons()
        Timber.d("displayCardAnswer()")
        mediaErrorHandler.onCardSideChange()
        backButtonPressedToReturn = false

        // prevent answering (by e.g. gestures) before card is loaded
        if (currentCard == null) {
            return
        }

        // TODO needs testing: changing a card's model without flipping it back to the front
        //  (such as editing a card, then editing the card template)
        typeAnswer!!.updateInfo(getColUnsafe, currentCard!!, resources)

        // Explicitly hide the soft keyboard. It *should* be hiding itself automatically,
        // but sometimes failed to do so (e.g. if an OnKeyListener is attached).
        if (typeAnswer!!.validForEditText()) {
            val inputMethodManager = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            inputMethodManager.hideSoftInputFromWindow(answerField!!.windowToken, 0)
        }
        displayAnswer = true
        answerField!!.visibility = View.GONE
        // Clean up the user answer and the correct answer
        if (!typeAnswer!!.useInputTag) {
            typeAnswer!!.input = answerField!!.text.toString()
        }
        isSelecting = false
        val answerContent = htmlGenerator!!.generateHtml(getColUnsafe, currentCard!!, SingleCardSide.BACK)
        automaticAnswer.onDisplayAnswer()
        launchCatchingTask {
            if (!automaticAnswerShouldWaitForAudio()) {
                automaticAnswer.scheduleAutomaticDisplayQuestion()
            }
        }
        updateCard(answerContent)
        displayAnswerBottomBar()
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
            SystemClock.uptimeMillis(),
            MotionEvent.ACTION_DOWN,
            x.toFloat(),
            y.toFloat(),
            1f,
            1f,
            0,
            1f,
            1f,
            0,
            0
        )
        processCardAction { cardWebView: WebView? -> cardWebView!!.dispatchTouchEvent(eDown) }
        val eUp = MotionEvent.obtain(
            eDown.downTime,
            SystemClock.uptimeMillis(),
            MotionEvent.ACTION_UP,
            x.toFloat(),
            y.toFloat(),
            1f,
            1f,
            0,
            1f,
            1f,
            0,
            0
        )
        processCardAction { cardWebView: WebView? -> cardWebView!!.dispatchTouchEvent(eUp) }
    }

    @KotlinCleanup("internal for AnkiDroidJsApi")
    internal val isInNightMode: Boolean
        get() = Themes.currentTheme.isNightMode

    private fun updateCard(content: CardHtml) {
        Timber.d("updateCard()")
        // TODO: This doesn't need to be blocking
        runBlocking {
            soundPlayer.loadCardSounds(currentCard!!)
        }
        cardContent = content.getTemplateHtml()
        fillFlashcard()
        playSounds(false) // Play sounds if appropriate
    }

    /**
     * Plays sounds (or TTS, if configured) for currently shown side of card.
     *
     * @param doAudioReplay indicates an anki desktop-like replay call is desired, whose behavior is identical to
     * pressing the keyboard shortcut R on the desktop
     */
    @NeedsTest("audio is not played if opExecuted occurs when viewer is in the background")
    protected open fun playSounds(doAudioReplay: Boolean) {
        // this can occur due to OpChanges when the viewer is on another screen
        if (!this.lifecycle.currentState.isAtLeast(RESUMED)) {
            Timber.w("sounds are not played as the activity is inactive")
            return
        }
        if (!soundPlayer.config.autoplay && !doAudioReplay) return
        // Use TTS if TTS preference enabled and no other sound source
        val useTTS = tts.enabled && !soundPlayer.hasSounds(displayAnswer)
        // We need to play the sounds from the proper side of the card
        if (!useTTS) {
            launchCatchingTask {
                val side = if (displayAnswer) SingleCardSide.BACK else SingleCardSide.FRONT
                when (doAudioReplay) {
                    true -> soundPlayer.replayAllSounds(side)
                    false -> soundPlayer.playAllSounds(side)
                }
            }
            return
        }

        val replayQuestion = soundPlayer.config.replayQuestion
        // Text to speech is in effect here
        // If the question is displayed or if the question should be replayed, read the question
        if (ttsInitialized) {
            if (!displayAnswer || doAudioReplay && replayQuestion) {
                readCardTts(SingleCardSide.FRONT)
            }
            if (displayAnswer) {
                readCardTts(SingleCardSide.BACK)
            }
        } else {
            replayOnTtsInit = true
        }
    }

    @VisibleForTesting
    fun readCardTts(side: SingleCardSide) {
        val tags = legacyGetTtsTags(getColUnsafe, currentCard!!, side, this)
        tts.readCardText(getColUnsafe, tags, currentCard!!, side.toCardSide())
    }

    /**
     * @see SoundPlayer.onSoundGroupCompleted
     */
    open fun onSoundGroupCompleted() {
        Timber.v("onSoundGroupCompleted")
        launchCatchingTask {
            if (automaticAnswerShouldWaitForAudio()) {
                if (isDisplayingAnswer) {
                    automaticAnswer.scheduleAutomaticDisplayQuestion()
                } else {
                    automaticAnswer.scheduleAutomaticDisplayAnswer()
                }
            }
        }
    }

    /**
     * Shows the dialogue for selecting TTS for the current card and cardside.
     */
    protected fun showSelectTtsDialogue() {
        if (ttsInitialized) {
            tts.selectTts(
                getColUnsafe,
                this,
                currentCard!!,
                if (displayAnswer) CardSide.ANSWER else CardSide.QUESTION
            )
        }
    }

    open fun fillFlashcard() {
        Timber.d("fillFlashcard()")
        if (cardContent == null) {
            Timber.w("fillFlashCard() called with no card content")
            return
        }
        processCardAction { cardWebView: WebView? -> loadContentIntoCard(cardWebView, cardContent!!) }
        gestureDetectorImpl.onFillFlashcard()
        if (!displayAnswer) {
            updateForNewCard()
        }
    }

    private fun loadContentIntoCard(card: WebView?, content: String) {
        if (card != null) {
            card.settings.mediaPlaybackRequiresUserGesture = !soundPlayer.config.autoplay
            card.loadDataWithBaseURL(
                baseUrl,
                content,
                "text/html",
                null,
                null
            )
        }
    }

    protected open fun unblockControls() {
        cardFrame!!.isEnabled = true
        flipCardLayout?.isEnabled = true
        easeButton1?.unblockBasedOnEase(currentEase)
        easeButton2?.unblockBasedOnEase(currentEase)
        easeButton3?.unblockBasedOnEase(currentEase)
        easeButton4?.unblockBasedOnEase(currentEase)
        if (typeAnswer?.validForEditText() == true) {
            answerField?.isEnabled = true
        }
        touchLayer?.visibility = View.VISIBLE
        inAnswer = false
        invalidateOptionsMenu()
    }

    fun buryCard(): Boolean {
        launchCatchingTask {
            withProgress {
                undoableOp {
                    sched.buryCards(listOf(currentCard!!.id))
                }
            }
            soundPlayer.stopSounds()
            showSnackbar(R.string.card_buried, Reviewer.ACTION_SNACKBAR_TIME)
        }
        return true
    }

    @VisibleForTesting
    open fun suspendCard(): Boolean {
        launchCatchingTask {
            withProgress {
                undoableOp {
                    sched.suspendCards(listOf(currentCard!!.id))
                }
            }
            soundPlayer.stopSounds()
            showSnackbar(TR.studyingCardSuspended(), Reviewer.ACTION_SNACKBAR_TIME)
        }
        return true
    }

    @VisibleForTesting
    open fun suspendNote(): Boolean {
        launchCatchingTask {
            val changed = withProgress {
                undoableOp {
                    sched.suspendNotes(listOf(currentCard!!.nid))
                }
            }
            val count = changed.count
            val noteSuspended = resources.getQuantityString(R.plurals.note_suspended, count, count)
            soundPlayer.stopSounds()
            showSnackbar(noteSuspended, Reviewer.ACTION_SNACKBAR_TIME)
        }
        return true
    }

    @VisibleForTesting
    open fun buryNote(): Boolean {
        launchCatchingTask {
            val changed = withProgress {
                undoableOp {
                    sched.buryNotes(listOf(currentCard!!.nid))
                }
            }
            soundPlayer.stopSounds()
            showSnackbar(TR.studyingCardsBuried(changed.count), Reviewer.ACTION_SNACKBAR_TIME)
        }
        return true
    }

    override fun executeCommand(which: ViewerCommand, fromGesture: Gesture?): Boolean {
        return when (which) {
            ViewerCommand.SHOW_ANSWER -> {
                if (displayAnswer) {
                    return false
                }
                displayCardAnswer()
                true
            }

            ViewerCommand.FLIP_OR_ANSWER_EASE1 -> {
                flipOrAnswerCard(EASE_1)
                true
            }

            ViewerCommand.FLIP_OR_ANSWER_EASE2 -> {
                flipOrAnswerCard(EASE_2)
                true
            }

            ViewerCommand.FLIP_OR_ANSWER_EASE3 -> {
                flipOrAnswerCard(EASE_3)
                true
            }

            ViewerCommand.FLIP_OR_ANSWER_EASE4 -> {
                flipOrAnswerCard(EASE_4)
                true
            }

            ViewerCommand.EXIT -> {
                closeReviewer(RESULT_DEFAULT)
                true
            }

            ViewerCommand.UNDO -> {
                undo()
                true
            }

            ViewerCommand.EDIT -> {
                editCard(fromGesture)
                true
            }

            ViewerCommand.TAG -> {
                showTagsDialog()
                true
            }

            ViewerCommand.BURY_CARD -> buryCard()
            ViewerCommand.BURY_NOTE -> buryNote()
            ViewerCommand.SUSPEND_CARD -> suspendCard()
            ViewerCommand.SUSPEND_NOTE -> suspendNote()
            ViewerCommand.DELETE -> {
                showDeleteNoteDialog()
                true
            }

            ViewerCommand.PLAY_MEDIA -> {
                playSounds(true)
                true
            }

            ViewerCommand.PAGE_UP -> {
                onPageUp()
                true
            }

            ViewerCommand.PAGE_DOWN -> {
                onPageDown()
                true
            }

            ViewerCommand.ABORT_AND_SYNC -> {
                abortAndSync()
                true
            }

            ViewerCommand.RECORD_VOICE -> {
                recordVoice()
                true
            }

            ViewerCommand.SAVE_VOICE -> {
                saveRecording()
                true
            }

            ViewerCommand.REPLAY_VOICE -> {
                replayVoice()
                true
            }

            ViewerCommand.TOGGLE_WHITEBOARD -> {
                toggleWhiteboard()
                true
            }

            ViewerCommand.CLEAR_WHITEBOARD -> {
                clearWhiteboard()
                true
            }

            ViewerCommand.CHANGE_WHITEBOARD_PEN_COLOR -> {
                changeWhiteboardPenColor()
                true
            }

            ViewerCommand.SHOW_HINT -> {
                loadUrlInViewer("javascript: showHint();")
                true
            }

            ViewerCommand.SHOW_ALL_HINTS -> {
                loadUrlInViewer("javascript: showAllHints();")
                true
            }

            else -> {
                Timber.w("Unknown command requested: %s", which)
                false
            }
        }
    }

    fun executeCommand(which: ViewerCommand): Boolean {
        return executeCommand(which, fromGesture = null)
    }

    protected open fun replayVoice() {
        // intentionally blank
    }

    protected open fun saveRecording() {
        // intentionally blank
    }

    protected open fun recordVoice() {
        // intentionally blank
    }

    protected open fun toggleWhiteboard() {
        // intentionally blank
    }

    protected open fun clearWhiteboard() {
        // intentionally blank
    }

    protected open fun changeWhiteboardPenColor() {
        // intentionally blank
    }

    private fun abortAndSync() {
        closeReviewer(RESULT_ABORT_AND_SYNC)
    }

    override val baseSnackbarBuilder: SnackbarBuilder = {
        // Configure the snackbar to avoid the bottom answer buttons
        if (answerButtonsPosition == "bottom") {
            val easeButtons = findViewById<View>(R.id.answer_options_layout)
            val previewButtons = findViewById<View>(R.id.preview_buttons_layout)
            anchorView = if (previewButtons.isVisible) previewButtons else easeButtons
        }
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

    // ----------------------------------------------------------------------------
    // INNER CLASSES
    // ----------------------------------------------------------------------------
    /**
     * Provides a hook for calling "alert" from javascript. Useful for debugging your javascript.
     */
    inner class AnkiDroidWebChromeClient : WebChromeClient() {
        override fun onJsAlert(
            view: WebView,
            url: String,
            message: String,
            result: JsResult
        ): Boolean {
            Timber.i("AbstractFlashcardViewer:: onJsAlert: %s", message)
            result.confirm()
            return true
        }

        private lateinit var customView: View

        // used for displaying `<video>` in fullscreen.
        // This implementation requires configChanges="orientation" in the manifest
        // to avoid destroying the View if the device is rotated
        override fun onShowCustomView(
            paramView: View,
            paramCustomViewCallback: CustomViewCallback?
        ) {
            customView = paramView
            (window.decorView as FrameLayout).addView(
                customView,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            )
            // hide system bars
            with(WindowInsetsControllerCompat(window, window.decorView)) {
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(WindowInsetsCompat.Type.systemBars())
            }
        }

        override fun onHideCustomView() {
            (window.decorView as FrameLayout).removeView(customView)
            // show system bars back
            with(WindowInsetsControllerCompat(window, window.decorView)) {
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
                show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    protected open fun closeReviewer(result: Int) {
        automaticAnswer.disable()
        previousAnswerIndicator!!.stopAutomaticHide()
        longClickHandler.removeCallbacks(startLongClickAction)
        this@AbstractFlashcardViewer.setResult(result)
        finish()
    }

    protected fun refreshActionBar() {
        invalidateOptionsMenu()
    }

    /**
     * Re-renders the content inside the WebView, retaining the side of the card to render
     *
     * To be used if card/note data has changed
     *
     * @see updateCardAndRedraw - also calls [updateCurrentCard] and resets the side
     * @see refreshIfRequired - calls through to [updateCurrentCard]
     */
    private fun reloadWebViewContent() {
        currentCard?.renderOutput(getColUnsafe, reload = true, browser = false)
        if (!isDisplayingAnswer) {
            Timber.d("displayCardQuestion()")
            displayAnswer = false
            backButtonPressedToReturn = false
            setInterface()
            typeAnswer?.input = ""
            typeAnswer?.updateInfo(getColUnsafe, currentCard!!, resources)
            if (typeAnswer?.validForEditText() == true) {
                // Show text entry based on if the user wants to write the answer
                answerField?.visibility = View.VISIBLE
                answerField?.applyLanguageHint(typeAnswer?.languageHint)
            } else {
                answerField?.visibility = View.GONE
            }
            val content = htmlGenerator!!.generateHtml(getColUnsafe, currentCard!!, SingleCardSide.FRONT)
            automaticAnswer.onDisplayQuestion()
            updateCard(content)
            hideEaseButtons()
            Timber.i(
                "AbstractFlashcardViewer:: Question successfully shown for card id %d",
                currentCard!!.id
            )
        } else {
            displayCardAnswer()
        }
    }

    /** Fixing bug 720: <input></input> focus, thanks to pablomouzo on android issue 7189  */
    internal inner class MyWebView(context: Context?) : WebView(context!!) {
        override fun loadDataWithBaseURL(
            baseUrl: String?,
            data: String,
            mimeType: String?,
            encoding: String?,
            historyUrl: String?
        ) {
            if (!this@AbstractFlashcardViewer.isDestroyed) {
                super.loadDataWithBaseURL(baseUrl, data, mimeType, encoding, historyUrl)
            } else {
                Timber.w("Not loading card - Activity is in the process of being destroyed.")
            }
        }

        override fun onScrollChanged(horiz: Int, vert: Int, oldHoriz: Int, oldVert: Int) {
            super.onScrollChanged(horiz, vert, oldHoriz, oldVert)
            if (abs(horiz - oldHoriz) > abs(vert - oldVert)) {
                isXScrolling = true
                scrollHandler.removeCallbacks(scrollXRunnable)
                scrollHandler.postDelayed(scrollXRunnable, 300)
            } else {
                isYScrolling = true
                scrollHandler.removeCallbacks(scrollYRunnable)
                scrollHandler.postDelayed(scrollYRunnable, 300)
            }
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (event.action == MotionEvent.ACTION_DOWN) {
                val scrollParent = findScrollParent(this)
                scrollParent?.requestDisallowInterceptTouchEvent(true)
            }
            return super.onTouchEvent(event)
        }

        override fun onOverScrolled(
            scrollX: Int,
            scrollY: Int,
            clampedX: Boolean,
            clampedY: Boolean
        ) {
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

        private val scrollHandler = newHandler()
        private val scrollXRunnable = Runnable { isXScrolling = false }
        private val scrollYRunnable = Runnable { isYScrolling = false }
    }

    internal open inner class MyGestureDetector : SimpleOnGestureListener() {
        override fun onFling(
            e1: MotionEvent?,
            e2: MotionEvent,
            velocityX: Float,
            velocityY: Float
        ): Boolean {
            Timber.d("onFling")

            // #5741 - A swipe from the top caused delayedHide to be triggered,
            // accepting a gesture and quickly disabling the status bar, which wasn't ideal.
            // it would be lovely to use e1.getEdgeFlags(), but alas, it doesn't work.
            if (e1 != null && isTouchingEdge(e1)) {
                Timber.d("ignoring edge fling")
                return false
            }

            // Go back to immersive mode if the user had temporarily exited it (and then execute swipe gesture)
            this@AbstractFlashcardViewer.onFling()
            if (e1 != null && gesturesEnabled) {
                try {
                    val dy = e2.y - e1.y
                    val dx = e2.x - e1.x
                    gestureProcessor.onFling(
                        dx,
                        dy,
                        velocityX,
                        velocityY,
                        isSelecting,
                        isXScrolling,
                        isYScrolling
                    )
                } catch (e: Exception) {
                    Timber.e(e, "onFling Exception")
                }
            }
            return false
        }

        private fun isTouchingEdge(e1: MotionEvent): Boolean {
            val height = touchLayer!!.height
            val width = touchLayer!!.width
            val margin = Companion.NO_GESTURE_BORDER_DIP * resources.displayMetrics.density + 0.5f
            return e1.x < margin || e1.y < margin || height - e1.y < margin || width - e1.x < margin
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            if (gesturesEnabled) {
                gestureProcessor.onDoubleTap()
            }
            return true
        }

        override fun onSingleTapUp(e: MotionEvent): Boolean {
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
            if (gesturesEnabled && !isSelecting) {
                val height = touchLayer!!.height
                val width = touchLayer!!.width
                val posX = e.x
                val posY = e.y
                gestureProcessor.onTap(height, width, posX, posY)
            }
            isSelecting = false
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

        open fun startShakeDetector() {
            // intentionally blank
        }

        open fun stopShakeDetector() {
            // intentionally blank
        }
    }

    protected open fun onSingleTap(): Boolean {
        return false
    }

    protected open fun onFling() {}

    /** #6141 - blocks clicking links from executing "touch" gestures.
     * COULD_BE_BETTER: Make base class static and move this out of the CardViewer  */
    internal inner class LinkDetectingGestureDetector() :
        MyGestureDetector(), ShakeDetector.Listener {
        private var shakeDetector: ShakeDetector? = null

        init {
            initShakeDetector()
        }

        private fun initShakeDetector() {
            Timber.d("Initializing shake detector")
            if (gestureProcessor.isBound(Gesture.SHAKE)) {
                val sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
                shakeDetector = ShakeDetector(this).apply {
                    start(sensorManager, SensorManager.SENSOR_DELAY_UI)
                }
            }
        }

        override fun stopShakeDetector() {
            shakeDetector?.stop()
            shakeDetector = null
        }

        override fun startShakeDetector() {
            if (shakeDetector == null) {
                initShakeDetector()
            }
        }

        /** A list of events to process when listening to WebView touches   */
        private val desiredTouchEvents = hashSetInit<MotionEvent>(2)

        /** A list of events we sent to the WebView (to block double-processing)  */
        private val dispatchedTouchEvents = hashSetInit<MotionEvent>(2)

        override fun hearShake() {
            Timber.d("Shake detected!")
            gestureProcessor.onShake()
        }

        override fun onFillFlashcard() {
            Timber.d("Removing pending touch events for gestures")
            desiredTouchEvents.clear()
            dispatchedTouchEvents.clear()
        }

        override fun eventCanBeSentToWebView(event: MotionEvent): Boolean {
            // if we processed the event, we don't want to perform it again
            return !dispatchedTouchEvents.remove(event)
        }

        override fun executeTouchCommand(e: MotionEvent) {
            e.action = MotionEvent.ACTION_DOWN
            val upEvent = MotionEvent.obtainNoHistory(e)
            upEvent.action = MotionEvent.ACTION_UP

            // mark the events we want to process
            desiredTouchEvents.add(e)
            desiredTouchEvents.add(upEvent)

            // mark the events to can guard against double-processing
            dispatchedTouchEvents.add(e)
            dispatchedTouchEvents.add(upEvent)
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
                if (!desiredTouchEvents.remove(motionEvent)) {
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

    /** Callback for when TTS has been initialized.  */
    fun ttsInitialized() {
        ttsInitialized = true
        if (replayOnTtsInit) {
            playSounds(true)
        }
    }

    protected open fun shouldDisplayMark(): Boolean {
        return isMarked(getColUnsafe, currentCard!!.note(getColUnsafe))
    }

    val writeLock: Lock
        get() = cardLock.writeLock()
    open var currentCard: Card? = null

    /** Refreshes the WebView after a crash  */
    fun destroyWebViewFrame() {
        // Destroy the current WebView (to ensure WebView is GCed).
        // Otherwise, we get the following error:
        // "crash wasn't handled by all associated webviews, triggering application crash"
        cardFrame!!.removeAllViews()
        cardFrameParent!!.removeView(cardFrame)
        // destroy after removal from the view - produces logcat warnings otherwise
        destroyWebView(webView)
        webView = null
        // inflate a new instance of mCardFrame
        cardFrame = inflateNewView<FrameLayout>(R.id.flashcard)
        // Even with the above, I occasionally saw the above error. Manually trigger the GC.
        // I'll keep this line unless I see another crash, which would point to another underlying issue.
        System.gc()
    }

    fun recreateWebViewFrame() {
        // we need to add at index 0 so gestures still go through.
        cardFrameParent!!.addView(cardFrame, 0)
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
        private var pageFinishedFired = true
        private val pageRenderStopwatch = Stopwatch.init("page render")

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val url = request.url.toString()
            Timber.d("Obtained URL from card: '%s'", url)
            return filterUrl(url)
        }

        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
            pageRenderStopwatch.reset()
            pageFinishedFired = false
        }

        override fun shouldInterceptRequest(
            view: WebView,
            request: WebResourceRequest
        ): WebResourceResponse? {
            val url = request.url
            if (request.method == "GET") {
                loader!!.shouldInterceptRequest(url)?.let { return it }
            }
            if (url.toString().startsWith("file://")) {
                url.path?.let { path -> migrationService?.migrateFileImmediately(File(path)) }
            }
            return null
        }

        override fun onReceivedError(
            view: WebView,
            request: WebResourceRequest,
            error: WebResourceError
        ) {
            super.onReceivedError(view, request, error)
            mediaErrorHandler.processFailure(request) { filename: String ->
                displayCouldNotFindMediaSnackbar(
                    filename
                )
            }
        }

        override fun onReceivedHttpError(
            view: WebView,
            request: WebResourceRequest,
            errorResponse: WebResourceResponse
        ) {
            super.onReceivedHttpError(view, request, errorResponse)
            mediaErrorHandler.processFailure(request) { filename: String ->
                displayCouldNotFindMediaSnackbar(
                    filename
                )
            }
        }

        // Filter any links using the custom "playsound" protocol defined in Sound.java.
        // We play sounds through these links when a user taps the sound icon.
        fun filterUrl(url: String): Boolean {
            if (url.startsWith("playsound:")) {
                launchCatchingTask {
                    controlSound(url)
                }
                return true
            }
            if (url.startsWith("state-mutation-error:")) {
                onStateMutationError()
                return true
            }
            if (url.startsWith("tts-voices:")) {
                showDialogFragment(TtsVoicesDialogFragment())
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

            // card.html reload
            if (url.startsWith("signal:reload_card_html")) {
                redrawCard()
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
                    if (!Companion.displayAnswer) {
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
                    intent = Intent.parseUri(url, Intent.URI_ANDROID_APP_SCHEME)
                }
                if (intent != null) {
                    if (packageManager.resolveActivityCompat(
                            intent,
                            ResolveInfoFlagsCompat.EMPTY
                        ) == null
                    ) {
                        val packageName = intent.getPackage()
                        if (packageName == null) {
                            Timber.d(
                                "Not using resolved intent uri because not available: %s",
                                intent
                            )
                            intent = null
                        } else {
                            Timber.d(
                                "Resolving intent uri to market uri because not available: %s",
                                intent
                            )
                            intent = Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("market://details?id=$packageName")
                            )
                            if (packageManager.resolveActivityCompat(
                                    intent,
                                    ResolveInfoFlagsCompat.EMPTY
                                ) == null
                            ) {
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
                startActivity(intent)
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
        @NeedsTest("14221: 'playsound' should play the sound from the start")
        @BlocksSchemaUpgrade("handle TTS tags")
        private suspend fun controlSound(url: String) {
            val avTag = when (val tag = currentCard?.let { getAvTag(it, url) }) {
                is SoundOrVideoTag -> tag
                is TTSTag -> tag
                // not currently supported
                else -> return
            }
            soundPlayer.playOneSound(avTag)
        }

        // Run any post-load events in javascript that rely on the window being completely loaded.
        override fun onPageFinished(view: WebView, url: String) {
            if (pageFinishedFired) {
                return
            }
            pageFinishedFired = true
            pageRenderStopwatch.logElapsed()
            Timber.d("Java onPageFinished triggered: %s", url)
            // onPageFinished will be called multiple times if the WebView redirects by setting window.location.href
            onPageFinishedCallback?.onPageFinished(view)
            view.loadUrl("javascript:onPageFinished();")
        }

        @TargetApi(Build.VERSION_CODES.O)
        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            return onRenderProcessGoneDelegate.onRenderProcessGone(view, detail)
        }
    }

    fun decodeUrl(url: String): String {
        try {
            return URLDecoder.decode(url, "UTF-8")
        } catch (e: UnsupportedEncodingException) {
            Timber.e(e, "UTF-8 isn't supported as an encoding?")
        } catch (e: Exception) {
            Timber.e(e, "Exception decoding: '%s'", url)
            showThemedToast(
                this@AbstractFlashcardViewer,
                getString(R.string.card_viewer_url_decode_error),
                true
            )
        }
        return ""
    }

    protected open fun onStateMutationError() {
        Timber.w("state mutation error, see console log")
    }

    internal fun displayCouldNotFindMediaSnackbar(filename: String?) {
        showSnackbar(getString(R.string.card_viewer_could_not_find_image, filename)) {
            setAction(R.string.help) { openUrl(Uri.parse(getString(R.string.link_faq_missing_media))) }
        }
    }

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

    val isDisplayingAnswer
        get() = displayAnswer

    internal fun showTagsDialog() {
        val tags = ArrayList(getColUnsafe.tags.all())
        val selTags = ArrayList(currentCard!!.note(getColUnsafe).tags)
        val dialog = tagsDialogFactory!!.newTagsDialog()
            .withArguments(TagsDialog.DialogType.EDIT_TAGS, selTags, tags)
        showDialogFragment(dialog)
    }

    @NeedsTest("14656: adding tags does not flip the card")
    override fun onSelectedTags(
        selectedTags: List<String>,
        indeterminateTags: List<String>,
        stateFilter: CardStateFilter
    ) {
        if (currentCard!!.note(getColUnsafe).tags != selectedTags) {
            val tagString = selectedTags.joinToString(" ")
            val note = currentCard!!.note(getColUnsafe)
            note.setTagsFromStr(getColUnsafe, tagString)
            // TODO move to a coroutine instead of using runBlocking
            runBlocking { undoableOp { updateNote(note) } }
            // Reload current card to reflect tag changes
            reloadWebViewContent()
        }
    }

    override fun opExecuted(changes: OpChanges, handler: Any?) {
        if (handler === this) return
        refreshRequired = ViewerRefresh.updateState(refreshRequired, changes)
        refreshIfRequired()
    }

    open fun getCardDataForJsApi(): AnkiDroidJsAPI.CardDataForJsApi {
        return AnkiDroidJsAPI.CardDataForJsApi()
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
        @set:VisibleForTesting(otherwise = VisibleForTesting.NONE)
        var editorCard: Card? = null
        internal var displayAnswer = false
        const val DOUBLE_TAP_TIME_INTERVAL = "doubleTapTimeInterval"
        const val DEFAULT_DOUBLE_TAP_TIME_INTERVAL = 200

        /** Handle providing help for "Image Not Found"  */
        internal val mediaErrorHandler = MediaErrorHandler()

        @KotlinCleanup("moved from MyGestureDetector")
        // Android design spec for the size of the status bar.
        private const val NO_GESTURE_BORDER_DIP = 24

        @KotlinCleanup("moved from SelectEaseHandler")
        // maximum screen distance from initial touch where we will consider a click related to the touch
        private const val CLICK_ACTION_THRESHOLD = 200

        /**
         * @return if [gesture] is a swipe, a transition to the same direction of the swipe
         * else return [ActivityTransitionAnimation.Direction.FADE]
         */
        fun getAnimationTransitionFromGesture(gesture: Gesture?): ActivityTransitionAnimation.Direction {
            return when (gesture) {
                Gesture.SWIPE_UP -> ActivityTransitionAnimation.Direction.UP
                Gesture.SWIPE_DOWN -> ActivityTransitionAnimation.Direction.DOWN
                Gesture.SWIPE_RIGHT -> ActivityTransitionAnimation.Direction.RIGHT
                Gesture.SWIPE_LEFT -> ActivityTransitionAnimation.Direction.LEFT
                else -> ActivityTransitionAnimation.Direction.FADE
            }
        }

        /**
         * @param mediaDir media directory path on SD card
         * @return path converted to file URL, properly UTF-8 URL encoded
         */
        fun getMediaBaseUrl(mediaDir: String): String {
            // Use android.net.Uri class to ensure whole path is properly encoded
            // File.toURL() does not work here, and URLEncoder class is not directly usable
            // with existing slashes
            if (mediaDir.isNotEmpty()) {
                val mediaDirUri = Uri.fromFile(File(mediaDir))
                return "$mediaDirUri/"
            }
            return ""
        }
    }
}
