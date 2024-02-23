/****************************************************************************************
 * Copyright (c) 2011 Kostas Spyropoulos <inigo.aldana@gmail.com>                       *
 * Copyright (c) 2014 Bruno Romero de Azevedo <brunodea@inf.ufsm.br>                    *
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

import android.Manifest
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.annotation.SuppressLint
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.os.Parcelable
import android.text.SpannableString
import android.text.style.UnderlineSpan
import android.view.*
import android.webkit.WebView
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.view.menu.MenuBuilder
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.vectordrawable.graphics.drawable.VectorDrawableCompat
import anki.frontend.SetSchedulingStatesRequest
import com.google.android.material.color.MaterialColors
import com.google.android.material.snackbar.Snackbar
import com.ichi2.anim.ActivityTransitionAnimation.getInverseTransition
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.UIUtils.showThemedToast
import com.ichi2.anki.Whiteboard.Companion.createInstance
import com.ichi2.anki.Whiteboard.OnPaintColorChangeListener
import com.ichi2.anki.cardviewer.Gesture
import com.ichi2.anki.cardviewer.ViewerCommand
import com.ichi2.anki.dialogs.ConfirmationDialog
import com.ichi2.anki.dialogs.RescheduleDialog.Companion.rescheduleSingleCard
import com.ichi2.anki.pages.AnkiServer.Companion.ANKIDROID_JS_PREFIX
import com.ichi2.anki.pages.AnkiServer.Companion.ANKI_PREFIX
import com.ichi2.anki.pages.CardInfo.Companion.toIntent
import com.ichi2.anki.pages.CardInfoDestination
import com.ichi2.anki.preferences.sharedPrefs
import com.ichi2.anki.reviewer.*
import com.ichi2.anki.reviewer.AnswerButtons.Companion.getBackgroundColors
import com.ichi2.anki.reviewer.AnswerButtons.Companion.getTextColors
import com.ichi2.anki.reviewer.FullScreenMode.Companion.fromPreference
import com.ichi2.anki.reviewer.FullScreenMode.Companion.isFullScreenReview
import com.ichi2.anki.servicelayer.NoteService.isMarked
import com.ichi2.anki.servicelayer.NoteService.toggleMark
import com.ichi2.anki.servicelayer.rescheduleCards
import com.ichi2.anki.servicelayer.resetCards
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.anki.utils.remainingTime
import com.ichi2.annotations.NeedsTest
import com.ichi2.audio.AudioRecordingController
import com.ichi2.audio.AudioRecordingController.Companion.generateTempAudioFile
import com.ichi2.audio.AudioRecordingController.Companion.isAudioRecordingSaved
import com.ichi2.audio.AudioRecordingController.Companion.isRecording
import com.ichi2.audio.AudioRecordingController.Companion.setEditorStatus
import com.ichi2.audio.AudioRecordingController.Companion.tempAudioPath
import com.ichi2.libanki.*
import com.ichi2.libanki.Collection
import com.ichi2.libanki.sched.Counts
import com.ichi2.libanki.sched.CurrentQueueState
import com.ichi2.libanki.utils.TimeManager
import com.ichi2.themes.Themes
import com.ichi2.themes.Themes.currentTheme
import com.ichi2.utils.*
import com.ichi2.utils.HandlerUtils.executeFunctionWithDelay
import com.ichi2.utils.HandlerUtils.getDefaultLooper
import com.ichi2.utils.Permissions.canRecordAudio
import com.ichi2.utils.ViewGroupUtils.setRenderWorkaround
import com.ichi2.widget.WidgetStatus.updateInBackground
import timber.log.Timber
import java.io.File
import java.util.function.Consumer

@Suppress("LeakingThis")
@KotlinCleanup("too many to count")
@NeedsTest("#14709: Timebox shouldn't appear instantly when the Reviewer is opened")
open class Reviewer :
    AbstractFlashcardViewer(),
    ReviewerUi {
    private var queueState: CurrentQueueState? = null
    private val customSchedulingKey = TimeManager.time.intTimeMS().toString()
    private var hasDrawerSwipeConflicts = false
    private var showWhiteboard = true
    private var prefFullscreenReview = false
    private lateinit var colorPalette: LinearLayout
    private var toggleStylus = false

    // A flag that determines if the SchedulingStates in CurrentQueueState are
    // safe to persist in the database when answering a card. This is used to
    // ensure that the custom JS scheduler has persisted its SchedulingStates
    // back to the Reviewer before we save it to the database. If the custom
    // scheduler has not been configured, then it is safe to immediately set
    // this to true
    //
    // This flag should be set to false when we show the front of the card
    // and only set to true once we know the custom scheduler has finished its
    // execution, or set to true immediately if the custom scheduler has not
    // been configured
    private var statesMutated = false

    // TODO: Consider extracting to ViewModel
    // Card counts
    private var newCount: SpannableString? = null
    private var lrnCount: SpannableString? = null
    private var revCount: SpannableString? = null
    private lateinit var textBarNew: TextView
    private lateinit var textBarLearn: TextView
    private lateinit var textBarReview: TextView
    private lateinit var answerTimer: AnswerTimer
    private var prefHideDueCount = false

    // Whiteboard
    var prefWhiteboard = false

    @get:CheckResult
    @get:VisibleForTesting(otherwise = VisibleForTesting.NONE)
    var whiteboard: Whiteboard? = null
        protected set

    // Record Audio
    private var isMicToolBarVisible = false
    private var audioRecordingController: AudioRecordingController? = null
    private var isAudioUIInitialized = false
    private lateinit var micToolBarLayer: LinearLayout

    // ETA
    private var eta = 0
    private var prefShowETA = false

    /** Handle Mark/Flag state of cards  */
    @VisibleForTesting
    internal var cardMarker: CardMarker? = null

    // Preferences from the collection
    private var showRemainingCardCount = false
    private var stopTimerOnAnswer = false
    private val actionButtons = ActionButtons()
    private lateinit var toolbar: Toolbar

    @VisibleForTesting
    protected val processor = PeripheralKeymap(this, this)
    private val onboarding = Onboarding.Reviewer(this)

    private val addNoteLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
        FlashCardViewerResultCallback()
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        if (showedActivityFailedScreen(savedInstanceState)) {
            return
        }
        super.onCreate(savedInstanceState)
        colorPalette = findViewById(R.id.whiteboard_editor)
        answerTimer = AnswerTimer(findViewById(R.id.card_time))
        textBarNew = findViewById(R.id.new_number)
        textBarLearn = findViewById(R.id.learn_number)
        textBarReview = findViewById(R.id.review_number)
        toolbar = findViewById(R.id.toolbar)
        micToolBarLayer = findViewById(R.id.mic_tool_bar_layer)
        if (sharedPrefs().getString("answerButtonPosition", "bottom") == "bottom") {
            setNavigationBarColor(R.attr.showAnswerColor)
        }
        if (!sharedPrefs().getBoolean("showDeckTitle", false)) {
            // avoid showing "AnkiDroid"
            supportActionBar?.title = ""
        }
        startLoadingCollection()
    }

    override fun onPause() {
        answerTimer.pause()
        super.onPause()
    }

    override fun onResume() {
        when {
            stopTimerOnAnswer && isDisplayingAnswer -> {}
            else -> launchCatchingTask { answerTimer.resume() }
        }
        super.onResume()
        if (typeAnswer?.autoFocusEditText() == true) {
            answerField?.focusWithKeyboard()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        server.stop()
    }

    protected val flagToDisplay: Int
        get() {
            return FlagToDisplay(
                currentCard!!.userFlag(),
                actionButtons.findMenuItem(ActionButtons.RES_FLAG)?.isActionButton ?: true,
                prefFullscreenReview
            ).get()
        }

    override fun recreateWebView() {
        super.recreateWebView()
        setRenderWorkaround(this)
    }

    @NeedsTest("is hidden if marked is on app bar")
    @NeedsTest("is not hidden if marked is not on app bar")
    @NeedsTest("is not hidden if marked is on app bar and fullscreen is enabled")
    override fun shouldDisplayMark(): Boolean {
        val markValue = super.shouldDisplayMark()
        if (!markValue) {
            return false
        }

        // If we don't know: assume it's not shown
        val shownAsToolbarButton = actionButtons.findMenuItem(ActionButtons.RES_MARK)?.isActionButton == true
        return !shownAsToolbarButton || prefFullscreenReview
    }

    protected open fun onMark(card: Card?) {
        if (card == null) {
            return
        }
        launchCatchingTask {
            toggleMark(card.note(getColUnsafe), handler = this@Reviewer)
            refreshActionBar()
            onMarkChanged()
        }
    }

    private fun onMarkChanged() {
        if (currentCard == null) {
            return
        }
        cardMarker!!.displayMark(shouldDisplayMark())
    }

    protected open fun onFlag(card: Card?, flag: Flag) {
        if (card == null) {
            return
        }
        launchCatchingTask {
            card.setUserFlag(flag.code)
            undoableOp(this@Reviewer) {
                setUserFlagForCards(listOf(card.id), flag.code)
            }
            refreshActionBar()
            onFlagChanged()
        }
    }

    private fun onFlagChanged() {
        if (currentCard == null) {
            return
        }
        cardMarker!!.displayFlag(Flag.fromCode(flagToDisplay))
    }

    private fun selectDeckFromExtra() {
        val extras = intent.extras
        if (extras == null || !extras.containsKey("deckId")) {
            // deckId is not set, load default
            return
        }
        val did = extras.getLong("deckId", Long.MIN_VALUE)
        Timber.d("selectDeckFromExtra() with deckId = %d", did)

        // deckId does not exist, load default
        if (getColUnsafe.decks.get(did) == null) {
            Timber.w("selectDeckFromExtra() deckId '%d' doesn't exist", did)
            return
        }
        // Select the deck
        getColUnsafe.decks.select(did)
    }

    override fun getContentViewAttr(fullscreenMode: FullScreenMode): Int {
        return when (fullscreenMode) {
            FullScreenMode.BUTTONS_ONLY -> R.layout.reviewer_fullscreen
            FullScreenMode.FULLSCREEN_ALL_GONE -> R.layout.reviewer_fullscreen_noanswers
            else -> R.layout.reviewer
        }
    }

    public override fun fitsSystemWindows(): Boolean {
        return !fullscreenMode.isFullScreenReview()
    }

    override fun onCollectionLoaded(col: Collection) {
        super.onCollectionLoaded(col)
        if (Intent.ACTION_VIEW == intent.action) {
            Timber.d("onCreate() :: received Intent with action = %s", intent.action)
            selectDeckFromExtra()
        }
        // Load the first card and start reviewing. Uses the answer card
        // task to load a card, but since we send null
        // as the card to answer, no card will be answered.
        prefWhiteboard = MetaDB.getWhiteboardState(this, parentDid)
        if (prefWhiteboard) {
            // DEFECT: Slight inefficiency here, as we set the database using these methods
            val whiteboardVisibility = MetaDB.getWhiteboardVisibility(this, parentDid)
            setWhiteboardEnabledState(true)
            setWhiteboardVisibility(whiteboardVisibility)
            toggleStylus = MetaDB.getWhiteboardStylusState(this, parentDid)
            whiteboard!!.toggleStylus = toggleStylus
        }
        launchCatchingTask {
            withCol { startTimebox() }
            updateCardAndRedraw()
        }
        disableDrawerSwipeOnConflicts()

        // Set full screen/immersive mode if needed
        if (prefFullscreenReview) {
            setFullScreen(this)
        }
        setRenderWorkaround(this)
    }

    fun redo() {
        launchCatchingTask { redoAndShowSnackbar(ACTION_SNACKBAR_TIME) }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // 100ms was not enough on my device (Honor 9 Lite -  Android Pie)
        delayedHide(1000)
        if (drawerToggle.onOptionsItemSelected(item)) {
            return true
        }
        when (item.itemId) {
            android.R.id.home -> {
                Timber.i("Reviewer:: Home button pressed")
                closeReviewer(RESULT_OK)
            }
            R.id.action_undo -> {
                Timber.i("Reviewer:: Undo button pressed")
                if (showWhiteboard && whiteboard != null && !whiteboard!!.undoEmpty()) {
                    whiteboard!!.undo()
                } else {
                    undo()
                }
            }
            R.id.action_redo -> {
                Timber.i("Reviewer:: Redo button pressed")
                redo()
            }
            R.id.action_reset_card_progress -> {
                Timber.i("Reviewer:: Reset progress button pressed")
                showResetCardDialog()
            }
            R.id.action_mark_card -> {
                Timber.i("Reviewer:: Mark button pressed")
                onMark(currentCard)
            }
            R.id.action_replay -> {
                Timber.i("Reviewer:: Replay audio button pressed (from menu)")
                playSounds(true)
            }
            R.id.action_toggle_mic_tool_bar -> {
                Timber.i("Reviewer:: Record mic")
                // Check permission to record and request if not granted
                openOrToggleMicToolbar()
            }
            R.id.action_tag -> {
                Timber.i("Reviewer:: Tag button pressed")
                showTagsDialog()
            }
            R.id.action_edit -> {
                Timber.i("Reviewer:: Edit note button pressed")
                editCard()
            }
            R.id.action_bury_card -> buryCard()
            R.id.action_bury_note -> buryNote()
            R.id.action_suspend_card -> suspendCard()
            R.id.action_suspend_note -> suspendNote()
            R.id.action_reschedule_card -> showRescheduleCardDialog()
            R.id.action_reset_card_progress -> showResetCardDialog()
            R.id.action_delete -> {
                Timber.i("Reviewer:: Delete note button pressed")
                showDeleteNoteDialog()
            }
            R.id.action_change_whiteboard_pen_color -> {
                Timber.i("Reviewer:: Pen Color button pressed")
                changeWhiteboardPenColor()
            }
            R.id.action_save_whiteboard -> {
                Timber.i("Reviewer:: Save whiteboard button pressed")
                if (whiteboard != null) {
                    try {
                        val savedWhiteboardFileName = whiteboard!!.saveWhiteboard(TimeManager.time).path
                        showSnackbar(getString(R.string.white_board_image_saved, savedWhiteboardFileName), Snackbar.LENGTH_SHORT)
                    } catch (e: Exception) {
                        Timber.w(e)
                        showSnackbar(getString(R.string.white_board_image_save_failed, e.localizedMessage), Snackbar.LENGTH_SHORT)
                    }
                }
            }
            R.id.action_clear_whiteboard -> {
                Timber.i("Reviewer:: Clear whiteboard button pressed")
                clearWhiteboard()
            }
            R.id.action_hide_whiteboard -> { // toggle whiteboard visibility
                Timber.i("Reviewer:: Whiteboard visibility set to %b", !showWhiteboard)
                setWhiteboardVisibility(!showWhiteboard)
                refreshActionBar()
            }
            R.id.action_toggle_stylus -> { // toggle stylus mode
                Timber.i("Reviewer:: Stylus set to %b", !toggleStylus)
                toggleStylus = !toggleStylus
                whiteboard!!.toggleStylus = toggleStylus
                MetaDB.storeWhiteboardStylusState(this, parentDid, toggleStylus)
                refreshActionBar()
            }
            R.id.action_toggle_whiteboard -> {
                toggleWhiteboard()
            }
            R.id.action_open_deck_options -> {
                val i = com.ichi2.anki.pages.DeckOptions.getIntent(this, getColUnsafe.decks.current().id)
                deckOptionsLauncher.launch(i)
            }
            R.id.action_select_tts -> {
                Timber.i("Reviewer:: Select TTS button pressed")
                showSelectTtsDialogue()
            }
            R.id.action_add_note_reviewer -> {
                Timber.i("Reviewer:: Add note button pressed")
                addNote()
            }
            R.id.action_flag_zero -> {
                Timber.i("Reviewer:: No flag")
                onFlag(currentCard, Flag.NONE)
            }
            R.id.action_flag_one -> {
                Timber.i("Reviewer:: Flag one")
                onFlag(currentCard, Flag.RED)
            }
            R.id.action_flag_two -> {
                Timber.i("Reviewer:: Flag two")
                onFlag(currentCard, Flag.ORANGE)
            }
            R.id.action_flag_three -> {
                Timber.i("Reviewer:: Flag three")
                onFlag(currentCard, Flag.GREEN)
            }
            R.id.action_flag_four -> {
                Timber.i("Reviewer:: Flag four")
                onFlag(currentCard, Flag.BLUE)
            }
            R.id.action_flag_five -> {
                Timber.i("Reviewer:: Flag five")
                onFlag(currentCard, Flag.PINK)
            }
            R.id.action_flag_six -> {
                Timber.i("Reviewer:: Flag six")
                onFlag(currentCard, Flag.TURQUOISE)
            }
            R.id.action_flag_seven -> {
                Timber.i("Reviewer:: Flag seven")
                onFlag(currentCard, Flag.PURPLE)
            }
            R.id.action_card_info -> {
                Timber.i("Card Viewer:: Card Info")
                openCardInfo()
            }
            R.id.user_action_1 -> userAction(1)
            R.id.user_action_2 -> userAction(2)
            R.id.user_action_3 -> userAction(3)
            R.id.user_action_4 -> userAction(4)
            R.id.user_action_5 -> userAction(5)
            R.id.user_action_6 -> userAction(6)
            R.id.user_action_7 -> userAction(7)
            R.id.user_action_8 -> userAction(8)
            R.id.user_action_9 -> userAction(9)
            else -> {
                return super.onOptionsItemSelected(item)
            }
        }
        return true
    }

    public override fun toggleWhiteboard() {
        prefWhiteboard = !prefWhiteboard
        Timber.i("Reviewer:: Whiteboard enabled state set to %b", prefWhiteboard)
        // Even though the visibility is now stored in its own setting, we want it to be dependent
        // on the enabled status
        setWhiteboardEnabledState(prefWhiteboard)
        setWhiteboardVisibility(prefWhiteboard)
        if (!prefWhiteboard) {
            colorPalette.visibility = View.GONE
        }
        refreshActionBar()
    }

    public override fun clearWhiteboard() {
        if (whiteboard != null) {
            whiteboard!!.clear()
        }
    }

    public override fun changeWhiteboardPenColor() {
        if (colorPalette.visibility == View.GONE) {
            colorPalette.visibility = View.VISIBLE
        } else {
            colorPalette.visibility = View.GONE
        }
        updateWhiteboardEditorPosition()
    }

    override fun replayVoice() {
        if (!openMicToolbar()) {
            return
        }
        if (isAudioRecordingSaved) {
            audioRecordingController?.playPausePlayer()
        } else {
            return
        }
    }

    override fun recordVoice() {
        if (!openMicToolbar()) {
            return
        }
        audioRecordingController?.toggleToRecorder()
    }

    override fun saveRecording() {
        if (!openMicToolbar()) {
            return
        }
        if (isRecording) {
            audioRecordingController?.toggleSave()
        } else {
            return
        }
    }

    override fun updateForNewCard() {
        super.updateForNewCard()
        if (prefWhiteboard && whiteboard != null) {
            whiteboard!!.clear()
        }
        audioRecordingController?.updateUIForNewCard()
    }

    override fun unblockControls() {
        if (prefWhiteboard && whiteboard != null) {
            whiteboard!!.isEnabled = true
        }
        super.unblockControls()
    }

    override fun closeReviewer(result: Int) {
        // Stop the mic recording if still pending
        if (isRecording) audioRecordingController?.stopAndSaveRecording()

        // Remove the temporary audio file
        tempAudioPath?.let {
            val tempAudioPathToDelete = File(it)
            if (tempAudioPathToDelete.exists()) {
                tempAudioPathToDelete.delete()
            }
        }
        super.closeReviewer(result)
    }

    /**
     *
     * @return Whether the mic toolbar is usable
     */
    @VisibleForTesting
    fun openMicToolbar(): Boolean {
        if (micToolBarLayer.visibility != View.VISIBLE || audioRecordingController == null) {
            openOrToggleMicToolbar()
        }
        return audioRecordingController != null
    }

    private fun openOrToggleMicToolbar() {
        if (!canRecordAudio(this)) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                REQUEST_AUDIO_PERMISSION
            )
        } else {
            toggleMicToolBar()
        }
    }

    private fun toggleMicToolBar() {
        tempAudioPath = generateTempAudioFile(this)
        if (isMicToolBarVisible) {
            micToolBarLayer.visibility = View.GONE
        } else {
            setEditorStatus(false)
            if (!isAudioUIInitialized) {
                try {
                    audioRecordingController = AudioRecordingController()
                    audioRecordingController?.createUI(this, micToolBarLayer)
                } catch (e: Exception) {
                    Timber.w(e, "unable to add the audio recorder to toolbar")
                    CrashReportService.sendExceptionReport(e, "Unable to create recorder tool bar")
                    showThemedToast(
                        this,
                        this.getText(R.string.multimedia_editor_audio_view_create_failed).toString(),
                        true
                    )
                }
                isAudioUIInitialized = true
            }
            micToolBarLayer.visibility = View.VISIBLE
        }
        isMicToolBarVisible = !isMicToolBarVisible
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_AUDIO_PERMISSION &&
            permissions.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            // Get get audio record permission, so we can create the record tool bar
            toggleMicToolBar()
        }
    }

    private fun showRescheduleCardDialog() {
        val runnable = Consumer { days: Int ->
            val cardIds = listOf(currentCard!!.id)
            launchCatchingTask {
                rescheduleCards(cardIds, days)
            }
        }
        val dialog = rescheduleSingleCard(resources, currentCard!!, runnable)
        showDialogFragment(dialog)
    }

    private fun showResetCardDialog() {
        // Show confirmation dialog before resetting card progress
        Timber.i("showResetCardDialog() Reset progress button pressed")
        // Show confirmation dialog before resetting card progress
        val dialog = ConfirmationDialog()
        val title = resources.getString(R.string.reset_card_dialog_title)
        val message = resources.getString(R.string.reset_card_dialog_message)
        dialog.setArgs(title, message)
        val confirm = Runnable {
            Timber.i("NoteEditor:: ResetProgress button pressed")
            val cardIds = listOf(currentCard!!.id)
            launchCatchingTask {
                resetCards(cardIds)
            }
        }
        dialog.setConfirm(confirm)
        showDialogFragment(dialog)
    }

    @NeedsTest("Starting animation from swipe is inverse to the finishing one")
    private fun addNote(fromGesture: Gesture? = null) {
        val intent = Intent(this, NoteEditor::class.java)
        val animation = getAnimationTransitionFromGesture(fromGesture)
        intent.putExtra(NoteEditor.EXTRA_CALLER, NoteEditor.CALLER_REVIEWER_ADD)
        intent.putExtra(FINISH_ANIMATION_EXTRA, getInverseTransition(animation) as Parcelable)
        addNoteLauncher.launch(intent)
    }

    @NeedsTest("Starting animation from swipe is inverse to the finishing one")
    protected fun openCardInfo(fromGesture: Gesture? = null) {
        if (currentCard == null) {
            showSnackbar(getString(R.string.multimedia_editor_something_wrong), Snackbar.LENGTH_SHORT)
            return
        }
        val intent = CardInfoDestination(currentCard!!.id).toIntent(this)
        val animation = getAnimationTransitionFromGesture(fromGesture)
        intent.putExtra(FINISH_ANIMATION_EXTRA, getInverseTransition(animation) as Parcelable)
        startActivityWithAnimation(intent, animation)
    }

    // Related to https://github.com/ankidroid/Anki-Android/pull/11061#issuecomment-1107868455
    @NeedsTest("Order of operations needs Testing around Menu (Overflow) Icons and their colors.")
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        Timber.d("onCreateOptionsMenu()")
        // NOTE: This is called every time a new question is shown via invalidate options menu
        menuInflater.inflate(R.menu.reviewer, menu)
        displayIcons(menu)
        actionButtons.setCustomButtonsStatus(menu)
        val alpha = Themes.ALPHA_ICON_ENABLED_LIGHT
        val markCardIcon = menu.findItem(R.id.action_mark_card)
        if (currentCard != null && isMarked(getColUnsafe, currentCard!!.note(getColUnsafe))) {
            markCardIcon.setTitle(R.string.menu_unmark_note).setIcon(R.drawable.ic_star_white)
        } else {
            markCardIcon.setTitle(R.string.menu_mark_note).setIcon(R.drawable.ic_star_border_white)
        }
        markCardIcon.iconAlpha = alpha

        val flagIcon = menu.findItem(R.id.action_flag)
        if (flagIcon != null) {
            if (currentCard != null) {
                when (currentCard!!.userFlag()) {
                    1 -> flagIcon.setIcon(R.drawable.ic_flag_red)
                    2 -> flagIcon.setIcon(R.drawable.ic_flag_orange)
                    3 -> flagIcon.setIcon(R.drawable.ic_flag_green)
                    4 -> flagIcon.setIcon(R.drawable.ic_flag_blue)
                    5 -> flagIcon.setIcon(R.drawable.ic_flag_pink)
                    6 -> flagIcon.setIcon(R.drawable.ic_flag_turquoise)
                    7 -> flagIcon.setIcon(R.drawable.ic_flag_purple)
                    else -> flagIcon.setIcon(R.drawable.ic_flag_transparent)
                }
            }
            flagIcon.iconAlpha = alpha
        }

        // Undo button
        @DrawableRes val undoIconId: Int
        val undoEnabled: Boolean
        val whiteboardIsShownAndHasStrokes = showWhiteboard && whiteboard?.undoEmpty() == false
        if (whiteboardIsShownAndHasStrokes) {
            undoIconId = R.drawable.eraser
            undoEnabled = true
        } else {
            undoIconId = R.drawable.ic_undo_white
            undoEnabled = colIsOpenUnsafe() && getColUnsafe.undoAvailable()
        }
        val alphaUndo = Themes.ALPHA_ICON_ENABLED_LIGHT
        val undoIcon = menu.findItem(R.id.action_undo)
        undoIcon.setIcon(undoIconId)
        undoIcon.setEnabled(undoEnabled).iconAlpha = alphaUndo
        undoIcon.actionView!!.isEnabled = undoEnabled
        if (colIsOpenUnsafe()) { // Required mostly because there are tests where `col` is null
            if (whiteboardIsShownAndHasStrokes) {
                undoIcon.title = resources.getString(R.string.undo_action_whiteboard_last_stroke)
            } else if (getColUnsafe.undoAvailable()) {
                undoIcon.title = getColUnsafe.undoLabel()
                //  e.g. Undo Bury, Undo Change Deck, Undo Update Note
            } else {
                // In this case, there is no object word for the verb, "Undo",
                // so in some languages such as Japanese, which have pre/post-positional particle with the object,
                // we need to use the string for just "Undo" instead of the string for "Undo %s".
                undoIcon.title = resources.getString(R.string.undo)
                undoIcon.iconAlpha = Themes.ALPHA_ICON_DISABLED_LIGHT
            }
            menu.findItem(R.id.action_redo)?.apply {
                if (getColUnsafe.redoAvailable()) {
                    title = getColUnsafe.redoLabel()
                    iconAlpha = Themes.ALPHA_ICON_ENABLED_LIGHT
                    isEnabled = true
                } else {
                    setTitle(R.string.redo)
                    iconAlpha = Themes.ALPHA_ICON_DISABLED_LIGHT
                    isEnabled = false
                }
            }
        }
        if (undoEnabled) {
            onboarding.onUndoButtonEnabled()
        }
        val toggleWhiteboardIcon = menu.findItem(R.id.action_toggle_whiteboard)
        val toggleStylusIcon = menu.findItem(R.id.action_toggle_stylus)
        val hideWhiteboardIcon = menu.findItem(R.id.action_hide_whiteboard)
        val changePenColorIcon = menu.findItem(R.id.action_change_whiteboard_pen_color)
        // White board button
        if (prefWhiteboard) {
            // Configure the whiteboard related items in the action bar
            toggleWhiteboardIcon.setTitle(R.string.disable_whiteboard)
            // Always allow "Disable Whiteboard", even if "Enable Whiteboard" is disabled
            toggleWhiteboardIcon.isVisible = true
            if (!actionButtons.status.toggleStylusIsDisabled()) {
                toggleStylusIcon.isVisible = true
            }
            if (!actionButtons.status.hideWhiteboardIsDisabled()) {
                hideWhiteboardIcon.isVisible = true
            }
            if (!actionButtons.status.clearWhiteboardIsDisabled()) {
                menu.findItem(R.id.action_clear_whiteboard).isVisible = true
            }
            if (!actionButtons.status.saveWhiteboardIsDisabled()) {
                menu.findItem(R.id.action_save_whiteboard).isVisible = true
            }
            if (!actionButtons.status.whiteboardPenColorIsDisabled()) {
                changePenColorIcon.isVisible = true
            }
            val whiteboardIcon = ContextCompat.getDrawable(this, R.drawable.ic_gesture_white)!!.mutate()
            val stylusIcon = ContextCompat.getDrawable(this, R.drawable.ic_gesture_stylus)!!.mutate()
            val whiteboardColorPaletteIcon = VectorDrawableCompat.create(resources, R.drawable.ic_color_lens_white_24dp, this.theme)!!.mutate()
            if (showWhiteboard) {
                whiteboardIcon.alpha = Themes.ALPHA_ICON_ENABLED_LIGHT
                hideWhiteboardIcon.icon = whiteboardIcon
                hideWhiteboardIcon.setTitle(R.string.hide_whiteboard)
                whiteboardColorPaletteIcon.alpha = Themes.ALPHA_ICON_ENABLED_LIGHT
                changePenColorIcon.icon = whiteboardColorPaletteIcon
                if (toggleStylus) {
                    toggleStylusIcon.setTitle(R.string.disable_stylus)
                    stylusIcon.alpha = Themes.ALPHA_ICON_ENABLED_LIGHT
                } else {
                    toggleStylusIcon.setTitle(R.string.enable_stylus)
                    stylusIcon.alpha = Themes.ALPHA_ICON_DISABLED_LIGHT
                }
                toggleStylusIcon.icon = stylusIcon
            } else {
                whiteboardIcon.alpha = Themes.ALPHA_ICON_DISABLED_LIGHT
                hideWhiteboardIcon.icon = whiteboardIcon
                hideWhiteboardIcon.setTitle(R.string.show_whiteboard)
                whiteboardColorPaletteIcon.alpha = Themes.ALPHA_ICON_DISABLED_LIGHT
                stylusIcon.alpha = Themes.ALPHA_ICON_DISABLED_LIGHT
                toggleStylusIcon.isEnabled = false
                toggleStylusIcon.icon = stylusIcon
                changePenColorIcon.isEnabled = false
                changePenColorIcon.icon = whiteboardColorPaletteIcon
                colorPalette.visibility = View.GONE
            }
        } else {
            toggleWhiteboardIcon.setTitle(R.string.enable_whiteboard)
        }
        if (colIsOpenUnsafe() && getColUnsafe.decks.isDyn(parentDid)) {
            menu.findItem(R.id.action_open_deck_options).isVisible = false
        }
        if (tts.enabled && !actionButtons.status.selectTtsIsDisabled()) {
            menu.findItem(R.id.action_select_tts).isVisible = true
        }
        if (!suspendNoteAvailable() && !actionButtons.status.suspendIsDisabled()) {
            menu.findItem(R.id.action_suspend).isVisible = false
            menu.findItem(R.id.action_suspend_card).isVisible = true
        }
        if (!buryNoteAvailable() && !actionButtons.status.buryIsDisabled()) {
            menu.findItem(R.id.action_bury).isVisible = false
            menu.findItem(R.id.action_bury_card).isVisible = true
        }

        onboarding.onCreate()

        increaseHorizontalPaddingOfOverflowMenuIcons(menu)
        tintOverflowMenuIcons(menu, skipIf = { isFlagResource(it.itemId) })

        return super.onCreateOptionsMenu(menu)
    }

    @SuppressLint("RestrictedApi")
    private fun displayIcons(menu: Menu) {
        try {
            if (menu is MenuBuilder) {
                menu.setOptionalIconsVisible(true)
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to display icons in Over flow menu")
        } catch (e: Error) {
            Timber.w(e, "Failed to display icons in Over flow menu")
        }
    }

    private fun isFlagResource(itemId: Int): Boolean {
        return itemId == R.id.action_flag_seven || itemId == R.id.action_flag_six || itemId == R.id.action_flag_five || itemId == R.id.action_flag_four || itemId == R.id.action_flag_three || itemId == R.id.action_flag_two || itemId == R.id.action_flag_one
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (answerFieldIsFocused()) {
            return super.onKeyDown(keyCode, event)
        }
        if (processor.onKeyDown(keyCode, event) || super.onKeyDown(keyCode, event)) {
            return true
        }
        return false
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        return if (processor.onKeyUp(keyCode, event)) {
            true
        } else {
            super.onKeyUp(keyCode, event)
        }
    }

    override fun onGenericMotionEvent(event: MotionEvent?): Boolean {
        if (motionEventHandler.onGenericMotionEvent(event)) {
            return true
        }
        return super.onGenericMotionEvent(event)
    }

    override fun canAccessScheduler(): Boolean {
        return true
    }

    override fun performReload() {
        launchCatchingTask { updateCardAndRedraw() }
    }

    override fun displayAnswerBottomBar() {
        super.displayAnswerBottomBar()
        onboarding.onAnswerShown()
        // Set correct label and background resource for each button
        // Note that it's necessary to set the resource dynamically as the ease2 / ease3 buttons
        // (which libanki expects ease to be 2 and 3) can either be hard, good, or easy - depending on num buttons shown
        val background = getBackgroundColors(this)
        val textColor = getTextColors(this)
        easeButton1!!.setVisibility(View.VISIBLE)
        easeButton1!!.setColor(background[0])
        easeButton4!!.setColor(background[3])
        // Ease 2 is "hard"
        easeButton2!!.setup(background[1], textColor[1], R.string.ease_button_hard)
        easeButton2!!.requestFocus()
        // Ease 3 is good
        easeButton3!!.setup(background[2], textColor[2], R.string.ease_button_good)
        easeButton4!!.setVisibility(View.VISIBLE)
        easeButton3!!.requestFocus()

        // Show next review time
        if (shouldShowNextReviewTime()) {
            val state = queueState!!
            launchCatchingTask {
                val labels = withCol { sched.describeNextStates(state.states) }
                easeButton1!!.nextTime = labels[0]
                easeButton2!!.nextTime = labels[1]
                easeButton3!!.nextTime = labels[2]
                easeButton4!!.nextTime = labels[3]
            }
        }
    }

    override fun automaticShowQuestion(action: AutomaticAnswerAction) {
        // explicitly do not call super
        if (easeButton1!!.canPerformClick) {
            action.execute(this)
        }
    }

    override fun restorePreferences(): SharedPreferences {
        val preferences = super.restorePreferences()
        prefHideDueCount = preferences.getBoolean("hideDueCount", false)
        prefShowETA = preferences.getBoolean("showETA", false)
        processor.setup()
        prefFullscreenReview = isFullScreenReview(preferences)
        actionButtons.setup(preferences)
        return preferences
    }

    override fun updateActionBar() {
        super.updateActionBar()
        updateScreenCounts()
    }

    private fun updateWhiteboardEditorPosition() {
        answerButtonsPosition = this.sharedPrefs()
            .getString("answerButtonPosition", "bottom")
        val layoutParams: RelativeLayout.LayoutParams
        when (answerButtonsPosition) {
            "none", "top" -> {
                layoutParams = colorPalette.layoutParams as RelativeLayout.LayoutParams
                layoutParams.removeRule(RelativeLayout.ABOVE)
                layoutParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
                colorPalette.layoutParams = layoutParams
            }

            "bottom" -> {
                layoutParams = colorPalette.layoutParams as RelativeLayout.LayoutParams
                layoutParams.removeRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
                layoutParams.addRule(RelativeLayout.ABOVE, R.id.bottom_area_layout)
                colorPalette.layoutParams = layoutParams
            }
        }
    }

    private fun updateScreenCounts() {
        val queue = queueState ?: return
        super.updateActionBar()
        val actionBar = supportActionBar
        val counts = queue.counts
        if (actionBar != null) {
            if (prefShowETA) {
                launchCatchingTask {
                    eta = withCol { sched.eta(counts, false) }
                    actionBar.subtitle = remainingTime(this@Reviewer, (eta * 60).toLong())
                }
            }
        }
        newCount = SpannableString(counts.new.toString())
        lrnCount = SpannableString(counts.lrn.toString())
        revCount = SpannableString(counts.rev.toString())
        if (prefHideDueCount) {
            revCount = SpannableString("???")
        }
        // if this code is run as a card is being answered, currentCard may be non-null but
        // the queues may be empty - we can't call countIdx() in such a case
        if (counts.count() != 0) {
            when (queue.countsIndex) {
                Counts.Queue.NEW -> newCount!!.setSpan(UnderlineSpan(), 0, newCount!!.length, 0)
                Counts.Queue.LRN -> lrnCount!!.setSpan(UnderlineSpan(), 0, lrnCount!!.length, 0)
                Counts.Queue.REV -> revCount!!.setSpan(UnderlineSpan(), 0, revCount!!.length, 0)
            }
        }
        textBarNew.text = newCount
        textBarLearn.text = lrnCount
        textBarReview.text = revCount
    }

    override fun fillFlashcard() {
        super.fillFlashcard()
        if (!isDisplayingAnswer && showWhiteboard && whiteboard != null) {
            whiteboard!!.clear()
        }
    }

    override fun onPageFinished(view: WebView) {
        super.onPageFinished(view)
        onFlagChanged()
        onMarkChanged()
        if (!displayAnswer) {
            runStateMutationHook()
        }
    }

    override suspend fun updateCurrentCard() {
        val state = withCol {
            sched.currentQueueState()?.apply {
                topCard.renderOutput(true)
            }
        }
        state?.timeboxReached?.let { dealWithTimeBox(it) }
        currentCard = state?.topCard
        queueState = state
    }

    override suspend fun answerCardInner(ease: Int) {
        val state = queueState!!
        Timber.d("answerCardInner: ${currentCard!!.id} $ease")
        var wasLeech = false
        undoableOp(this) {
            sched.answerCard(state, ease).also {
                wasLeech = sched.againIsLeech(state)
            }
        }.also {
            if (ease == Consts.BUTTON_ONE && wasLeech) {
                state.topCard.load(getColUnsafe)
                val leechMessage: String = if (state.topCard.queue < 0) {
                    resources.getString(R.string.leech_suspend_notification)
                } else {
                    resources.getString(R.string.leech_notification)
                }
                showSnackbar(leechMessage, Snackbar.LENGTH_SHORT)
            }
        }
    }

    private fun dealWithTimeBox(timebox: Collection.TimeboxReached) {
        val nCards = timebox.reps
        val nMins = timebox.secs / 60
        val mins = resources.getQuantityString(R.plurals.in_minutes, nMins, nMins)
        val timeboxMessage = resources.getQuantityString(R.plurals.timebox_reached, nCards, nCards, mins)
        AlertDialog.Builder(this).show {
            title(R.string.timebox_reached_title)
            message(text = timeboxMessage)
            positiveButton(R.string.dialog_continue) {}
            negativeButton(text = CollectionManager.TR.studyingFinish()) {
                finish()
            }
            cancelable(true)
            setOnCancelListener { }
        }
    }

    override fun displayCardQuestion() {
        statesMutated = false
        // show timer, if activated in the deck's preferences
        answerTimer.setupForCard(getColUnsafe, currentCard!!)
        delayedHide(100)
        super.displayCardQuestion()
    }

    @VisibleForTesting
    override fun displayCardAnswer() {
        if (queueState?.customSchedulingJs?.isEmpty() == true) {
            statesMutated = true
        }
        if (!statesMutated) {
            executeFunctionWithDelay(50) { displayCardAnswer() }
            return
        }

        delayedHide(100)
        if (stopTimerOnAnswer) {
            answerTimer.pause()
        }
        super.displayCardAnswer()
    }

    private fun runStateMutationHook() {
        val state = queueState ?: return
        if (state.customSchedulingJs.isEmpty()) {
            statesMutated = true
            return
        }
        val key = customSchedulingKey
        val js = state.customSchedulingJs
        webView?.evaluateJavascript(
            """
        anki.mutateNextCardStates('$key', async (states, customData, ctx) => { $js })
            .catch(err => { console.log(err); window.location.href = "state-mutation-error:"; });
"""
        ) { result ->
            if ("null" == result) {
                // eval failed, usually a syntax error
                // Note, we get "null" (string) and not null
                statesMutated = true
            }
        }
    }

    override fun onStateMutationError() {
        super.onStateMutationError()
        statesMutated = true
    }

    override fun initLayout() {
        super.initLayout()
        if (!showRemainingCardCount) {
            textBarNew.visibility = View.GONE
            textBarLearn.visibility = View.GONE
            textBarReview.visibility = View.GONE
        }

        // can't move this into onCreate due to mTopBarLayout
        val mark = topBarLayout!!.findViewById<ImageView>(R.id.mark_icon)
        val flag = topBarLayout!!.findViewById<ImageView>(R.id.flag_icon)
        cardMarker = CardMarker(mark, flag)
    }

    override fun switchTopBarVisibility(visible: Int) {
        super.switchTopBarVisibility(visible)
        answerTimer.setVisibility(visible)
        if (showRemainingCardCount) {
            textBarNew.visibility = visible
            textBarLearn.visibility = visible
            textBarReview.visibility = visible
        }
    }

    override fun onStop() {
        super.onStop()
        if (!isFinishing && colIsOpenUnsafe()) {
            updateInBackground(this)
        }
    }

    override fun initControls() {
        super.initControls()
        if (prefWhiteboard) {
            setWhiteboardVisibility(showWhiteboard)
        }
        if (showRemainingCardCount) {
            textBarNew.visibility = View.VISIBLE
            textBarLearn.visibility = View.VISIBLE
            textBarReview.visibility = View.VISIBLE
        }
    }

    override fun executeCommand(which: ViewerCommand, fromGesture: Gesture?): Boolean {
        when (which) {
            ViewerCommand.TOGGLE_FLAG_RED -> {
                toggleFlag(Flag.RED)
                return true
            }
            ViewerCommand.TOGGLE_FLAG_ORANGE -> {
                toggleFlag(Flag.ORANGE)
                return true
            }
            ViewerCommand.TOGGLE_FLAG_GREEN -> {
                toggleFlag(Flag.GREEN)
                return true
            }
            ViewerCommand.TOGGLE_FLAG_BLUE -> {
                toggleFlag(Flag.BLUE)
                return true
            }
            ViewerCommand.TOGGLE_FLAG_PINK -> {
                toggleFlag(Flag.PINK)
                return true
            }
            ViewerCommand.TOGGLE_FLAG_TURQUOISE -> {
                toggleFlag(Flag.TURQUOISE)
                return true
            }
            ViewerCommand.TOGGLE_FLAG_PURPLE -> {
                toggleFlag(Flag.PURPLE)
                return true
            }
            ViewerCommand.UNSET_FLAG -> {
                onFlag(currentCard, Flag.NONE)
                return true
            }
            ViewerCommand.MARK -> {
                onMark(currentCard)
                return true
            }
            ViewerCommand.REDO -> {
                redo()
                return true
            }
            ViewerCommand.ADD_NOTE -> {
                addNote(fromGesture)
                return true
            }
            ViewerCommand.CARD_INFO -> {
                openCardInfo(fromGesture)
                return true
            }
            ViewerCommand.RESCHEDULE_NOTE -> {
                showRescheduleCardDialog()
                return true
            }
            ViewerCommand.USER_ACTION_1 -> {
                userAction(1)
                return true
            }
            ViewerCommand.USER_ACTION_2 -> {
                userAction(2)
                return true
            }
            ViewerCommand.USER_ACTION_3 -> {
                userAction(3)
                return true
            }
            ViewerCommand.USER_ACTION_4 -> {
                userAction(4)
                return true
            }
            ViewerCommand.USER_ACTION_5 -> {
                userAction(5)
                return true
            }
            ViewerCommand.USER_ACTION_6 -> {
                userAction(6)
                return true
            }
            ViewerCommand.USER_ACTION_7 -> {
                userAction(7)
                return true
            }
            ViewerCommand.USER_ACTION_8 -> {
                userAction(8)
                return true
            }
            ViewerCommand.USER_ACTION_9 -> {
                userAction(9)
                return true
            }
            else -> return super.executeCommand(which, fromGesture)
        }
    }

    @Retention(AnnotationRetention.SOURCE)
    @IntDef(1, 2, 3, 4, 5, 6, 7, 8, 9)
    annotation class UserAction

    private fun userAction(@UserAction number: Int) {
        Timber.v("userAction%d", number)
        loadUrlInViewer("javascript: userAction($number);")
    }

    private fun toggleFlag(flag: Flag) {
        if (currentCard!!.userFlag() == flag.code) {
            Timber.i("Toggle flag: unsetting flag")
            onFlag(currentCard, Flag.NONE)
        } else {
            Timber.i("Toggle flag: Setting flag to %d", flag.code)
            onFlag(currentCard, flag)
        }
    }

    override fun restoreCollectionPreferences(col: Collection) {
        super.restoreCollectionPreferences(col)
        showRemainingCardCount = col.config.get("dueCounts") ?: true
        stopTimerOnAnswer = col.decks.confForDid(col.decks.current().id).getBoolean("stopTimerOnAnswer")
    }

    override fun onSingleTap(): Boolean {
        if (prefFullscreenReview && isImmersiveSystemUiVisible(this)) {
            delayedHide(INITIAL_HIDE_DELAY)
            return true
        }
        return false
    }

    override fun onFling() {
        if (prefFullscreenReview && isImmersiveSystemUiVisible(this)) {
            delayedHide(INITIAL_HIDE_DELAY)
        }
    }

    override fun onCardEdited(card: Card) {
        super.onCardEdited(card)
        if (prefWhiteboard && whiteboard != null) {
            whiteboard!!.clear()
        }
        if (!isDisplayingAnswer) {
            // Editing the card may reuse mCurrentCard. If so, the scheduler won't call startTimer() to reset the timer
            // QUESTIONABLE(legacy code): Only perform this if editing the question
            card.startTimer()
        }
    }

    private val fullScreenHandler: Handler = object : Handler(getDefaultLooper()) {
        override fun handleMessage(msg: Message) {
            if (prefFullscreenReview) {
                setFullScreen(this@Reviewer)
            }
        }
    }

    /** Hide the navigation if in full-screen mode after a given period of time  */
    protected open fun delayedHide(delayMillis: Int) {
        Timber.d("Fullscreen delayed hide in %dms", delayMillis)
        fullScreenHandler.removeMessages(0)
        fullScreenHandler.sendEmptyMessageDelayed(0, delayMillis.toLong())
    }

    private fun setWhiteboardEnabledState(state: Boolean) {
        prefWhiteboard = state
        MetaDB.storeWhiteboardState(this, parentDid, state)
        if (state && whiteboard == null) {
            createWhiteboard()
        }
    }

    @Suppress("deprecation") // #9332: UI Visibility -> Insets
    private fun setFullScreen(a: AbstractFlashcardViewer) {
        // Set appropriate flags to enable Sticky Immersive mode.
        a.window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE // | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION // temporarily disabled due to #5245
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LOW_PROFILE
                or View.SYSTEM_UI_FLAG_IMMERSIVE
            )
        // Show / hide the Action bar together with the status bar
        val prefs = a.sharedPrefs()
        val fullscreenMode = fromPreference(prefs)
        a.window.statusBarColor = MaterialColors.getColor(a, R.attr.appBarColor, 0)
        val decorView = a.window.decorView
        decorView.setOnSystemUiVisibilityChangeListener { flags: Int ->
            val toolbar = a.findViewById<View>(R.id.toolbar)
            val answerButtons = a.findViewById<View>(R.id.answer_options_layout)
            val topbar = a.findViewById<View>(R.id.top_bar)
            if (toolbar == null || topbar == null || answerButtons == null) {
                return@setOnSystemUiVisibilityChangeListener
            }
            // Note that system bars will only be "visible" if none of the
            // LOW_PROFILE, HIDE_NAVIGATION, or FULLSCREEN flags are set.
            val visible = flags and View.SYSTEM_UI_FLAG_HIDE_NAVIGATION == 0
            Timber.d("System UI visibility change. Visible: %b", visible)
            if (visible) {
                showViewWithAnimation(toolbar)
                if (fullscreenMode == FullScreenMode.FULLSCREEN_ALL_GONE) {
                    showViewWithAnimation(topbar)
                    showViewWithAnimation(answerButtons)
                }
            } else {
                hideViewWithAnimation(toolbar)
                if (fullscreenMode == FullScreenMode.FULLSCREEN_ALL_GONE) {
                    hideViewWithAnimation(topbar)
                    hideViewWithAnimation(answerButtons)
                }
            }
        }
    }

    private fun showViewWithAnimation(view: View) {
        view.alpha = 0.0f
        view.visibility = View.VISIBLE
        view.animate().alpha(TRANSPARENCY).setDuration(ANIMATION_DURATION.toLong()).setListener(null)
    }

    private fun hideViewWithAnimation(view: View) {
        view.animate()
            .alpha(0f)
            .setDuration(ANIMATION_DURATION.toLong())
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    view.visibility = View.GONE
                }
            })
    }

    @Suppress("deprecation") // #9332: UI Visibility -> Insets
    private fun isImmersiveSystemUiVisible(activity: AnkiActivity): Boolean {
        return activity.window.decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_HIDE_NAVIGATION == 0
    }

    override suspend fun handlePostRequest(uri: String, bytes: ByteArray): ByteArray? {
        return if (uri.startsWith(ANKI_PREFIX)) {
            when (val methodName = uri.substring(ANKI_PREFIX.length)) {
                "getSchedulingStatesWithContext" -> getSchedulingStatesWithContext()
                "setSchedulingStates" -> setSchedulingStates(bytes)
                else -> throw IllegalArgumentException("unhandled request: $methodName")
            }
        } else if (uri.startsWith(ANKIDROID_JS_PREFIX)) {
            jsApi.handleJsApiRequest(
                uri.substring(ANKIDROID_JS_PREFIX.length),
                bytes,
                returnDefaultValues = false
            )
        } else {
            Timber.d("unhandled request: %s", uri)
            null
        }
    }

    private fun getSchedulingStatesWithContext(): ByteArray {
        val state = queueState ?: return ByteArray(0)
        return state.schedulingStatesWithContext().toBuilder()
            .mergeStates(
                state.states.toBuilder().mergeCurrent(
                    state.states.current.toBuilder()
                        .setCustomData(state.topCard.toBackendCard().customData).build()
                ).build()
            )
            .build()
            .toByteArray()
    }

    private fun setSchedulingStates(bytes: ByteArray): ByteArray {
        val state = queueState
        if (state == null) {
            statesMutated = true
            return ByteArray(0)
        }
        val req = SetSchedulingStatesRequest.parseFrom(bytes)
        if (req.key == customSchedulingKey) {
            state.states = req.states
        }
        statesMutated = true
        return ByteArray(0)
    }

    private fun createWhiteboard() {
        whiteboard = createInstance(this, true, this)

        // We use the pen color of the selected deck at the time the whiteboard is enabled.
        // This is how all other whiteboard settings are
        val whiteboardPenColor = MetaDB.getWhiteboardPenColor(this, parentDid).fromPreferences()
        if (whiteboardPenColor != null) {
            whiteboard!!.penColor = whiteboardPenColor
        }
        whiteboard!!.setOnPaintColorChangeListener(object : OnPaintColorChangeListener {
            override fun onPaintColorChange(color: Int?) {
                MetaDB.storeWhiteboardPenColor(this@Reviewer, parentDid, !currentTheme.isNightMode, color)
            }
        })
        whiteboard!!.setOnTouchListener { v: View, event: MotionEvent? ->
            if (event == null) return@setOnTouchListener false
            // If the whiteboard is currently drawing, and triggers the system UI to show, we want to continue drawing.
            if (!whiteboard!!.isCurrentlyDrawing && (
                !showWhiteboard || (
                    prefFullscreenReview &&
                        isImmersiveSystemUiVisible(this@Reviewer)
                    )
                )
            ) {
                // Bypass whiteboard listener when it's hidden or fullscreen immersive mode is temporarily suspended
                v.performClick()
                return@setOnTouchListener gestureDetector!!.onTouchEvent(event)
            }
            whiteboard!!.handleTouchEvent(event)
        }
    }

    // Show or hide the whiteboard
    private fun setWhiteboardVisibility(state: Boolean) {
        showWhiteboard = state
        MetaDB.storeWhiteboardVisibility(this, parentDid, state)
        if (state) {
            whiteboard!!.visibility = View.VISIBLE
            disableDrawerSwipe()
        } else {
            whiteboard!!.visibility = View.GONE
            if (!hasDrawerSwipeConflicts) {
                enableDrawerSwipe()
            }
        }
    }

    private fun disableDrawerSwipeOnConflicts() {
        if (gestureProcessor.isBound(Gesture.SWIPE_UP, Gesture.SWIPE_DOWN, Gesture.SWIPE_RIGHT)) {
            hasDrawerSwipeConflicts = true
            super.disableDrawerSwipe()
        }
    }

    override val currentCardId: CardId?
        get() = currentCard!!.id

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // Restore full screen once we regain focus
        if (hasFocus) {
            delayedHide(INITIAL_HIDE_DELAY)
        } else {
            fullScreenHandler.removeMessages(0)
        }
    }

    /**
     * Whether or not dismiss note is available for current card and specified DismissType
     * @return true if there is another card of same note that could be dismissed
     */
    @KotlinCleanup("mCurrentCard handling")
    private fun suspendNoteAvailable(): Boolean {
        return if (currentCard == null) {
            false
        } else {
            getColUnsafe.db.queryScalar(
                "select 1 from cards where nid = ? and id != ? and queue != " + Consts.QUEUE_TYPE_SUSPENDED + " limit 1",
                currentCard!!.nid,
                currentCard!!.id
            ) == 1
        }
        // whether there exists a sibling not buried.
    }

    @KotlinCleanup("mCurrentCard handling")
    private fun buryNoteAvailable(): Boolean {
        return if (currentCard == null) {
            false
        } else {
            getColUnsafe.db.queryScalar(
                "select 1 from cards where nid = ? and id != ? and queue >=  " + Consts.QUEUE_TYPE_NEW + " limit 1",
                currentCard!!.nid,
                currentCard!!.id
            ) == 1
        }
        // Whether there exists a sibling which is neither suspended nor buried
    }

    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    fun hasDrawerSwipeConflicts(): Boolean {
        return hasDrawerSwipeConflicts
    }

    override fun getCardDataForJsApi(): AnkiDroidJsAPI.CardDataForJsApi {
        val cardDataForJsAPI = AnkiDroidJsAPI.CardDataForJsApi().apply {
            newCardCount = newCount.toString()
            lrnCardCount = lrnCount.toString()
            revCardCount = revCount.toString()
            nextTime1 = easeButton1!!.nextTime
            nextTime2 = easeButton2!!.nextTime
            nextTime3 = easeButton3!!.nextTime
            nextTime4 = easeButton4!!.nextTime
            eta = this@Reviewer.eta
        }
        return cardDataForJsAPI
    }

    companion object {
        private const val REQUEST_AUDIO_PERMISSION = 0
        private const val ANIMATION_DURATION = 200
        private const val TRANSPARENCY = 0.90f

        /** Default (500ms) time for action snackbars, such as undo, bury and suspend */
        const val ACTION_SNACKBAR_TIME = 500
    }
}
