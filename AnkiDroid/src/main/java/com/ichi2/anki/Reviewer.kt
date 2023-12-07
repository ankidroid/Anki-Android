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
import android.content.Context
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
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.view.menu.MenuBuilder
import androidx.appcompat.widget.Toolbar
import androidx.appcompat.widget.TooltipCompat
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.MenuItemCompat
import androidx.vectordrawable.graphics.drawable.VectorDrawableCompat
import com.google.android.material.color.MaterialColors
import com.google.android.material.snackbar.Snackbar
import com.ichi2.anim.ActivityTransitionAnimation
import com.ichi2.anim.ActivityTransitionAnimation.getInverseTransition
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.Whiteboard.Companion.createInstance
import com.ichi2.anki.Whiteboard.OnPaintColorChangeListener
import com.ichi2.anki.cardviewer.Gesture
import com.ichi2.anki.cardviewer.ViewerCommand
import com.ichi2.anki.dialogs.ConfirmationDialog
import com.ichi2.anki.dialogs.RescheduleDialog.Companion.rescheduleSingleCard
import com.ichi2.anki.multimediacard.AudioView
import com.ichi2.anki.multimediacard.AudioView.Companion.createRecorderInstance
import com.ichi2.anki.pages.CardInfo.Companion.toIntent
import com.ichi2.anki.pages.CardInfoDestination
import com.ichi2.anki.preferences.sharedPrefs
import com.ichi2.anki.reviewer.*
import com.ichi2.anki.reviewer.AnswerButtons.Companion.getBackgroundColors
import com.ichi2.anki.reviewer.AnswerButtons.Companion.getTextColors
import com.ichi2.anki.reviewer.CardMarker.FlagDef
import com.ichi2.anki.reviewer.FullScreenMode.Companion.fromPreference
import com.ichi2.anki.reviewer.FullScreenMode.Companion.isFullScreenReview
import com.ichi2.anki.servicelayer.NoteService.isMarked
import com.ichi2.anki.servicelayer.NoteService.toggleMark
import com.ichi2.anki.servicelayer.rescheduleCards
import com.ichi2.anki.servicelayer.resetCards
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.anki.utils.remainingTime
import com.ichi2.anki.workarounds.FirefoxSnackbarWorkaround.handledLaunchFromWebBrowser
import com.ichi2.annotations.NeedsTest
import com.ichi2.audio.AudioRecordingController.Companion.generateTempAudioFile
import com.ichi2.libanki.*
import com.ichi2.libanki.Collection
import com.ichi2.libanki.sched.Counts
import com.ichi2.libanki.sched.CurrentQueueState
import com.ichi2.libanki.utils.TimeManager
import com.ichi2.themes.Themes
import com.ichi2.themes.Themes.currentTheme
import com.ichi2.utils.*
import com.ichi2.utils.HandlerUtils.getDefaultLooper
import com.ichi2.utils.Permissions.canRecordAudio
import com.ichi2.utils.ViewGroupUtils.setRenderWorkaround
import com.ichi2.widget.WidgetStatus.updateInBackground
import timber.log.Timber
import java.io.File
import java.util.function.Consumer

@KotlinCleanup("too many to count")
@NeedsTest("#14709: Timebox shouldn't appear instantly when the Reviewer is opened")
open class Reviewer :
    AbstractFlashcardViewer(),
    ReviewerUi {
    var queueState: CurrentQueueState? = null
    val customSchedulingKey = TimeManager.time.intTimeMS().toString()
    private var mHasDrawerSwipeConflicts = false
    private var mShowWhiteboard = true
    private var mPrefFullscreenReview = false
    private lateinit var mColorPalette: LinearLayout
    private var toggleStylus = false

    // TODO: Consider extracting to ViewModel
    // Card counts
    private var mNewCount: SpannableString? = null
    private var mLrnCount: SpannableString? = null
    private var mRevCount: SpannableString? = null
    private lateinit var mTextBarNew: TextView
    private lateinit var mTextBarLearn: TextView
    private lateinit var mTextBarReview: TextView
    private lateinit var answerTimer: AnswerTimer
    private var mPrefHideDueCount = false

    // Whiteboard
    var prefWhiteboard = false

    @get:CheckResult
    @get:VisibleForTesting(otherwise = VisibleForTesting.NONE)
    var whiteboard: Whiteboard? = null
        protected set

    // Record Audio
    /** File of the temporary mic record  */
    @get:VisibleForTesting(otherwise = VisibleForTesting.NONE)
    var audioView: AudioView? = null
        protected set
    private var tempAudioPath: String? = null

    // ETA
    private var mEta = 0
    private var mPrefShowETA = false

    /** Handle Mark/Flag state of cards  */
    @VisibleForTesting
    internal var mCardMarker: CardMarker? = null

    // Preferences from the collection
    private var mShowRemainingCardCount = false
    private var stopTimerOnAnswer = false
    private val mActionButtons = ActionButtons(this)
    private lateinit var mToolbar: Toolbar

    @VisibleForTesting
    protected val mProcessor = PeripheralKeymap(this, this)
    private val mOnboarding = Onboarding.Reviewer(this)

    private val addNoteLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
        FlashCardViewerResultCallback()
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        if (showedActivityFailedScreen(savedInstanceState)) {
            return
        }
        super.onCreate(savedInstanceState)
        if (handledLaunchFromWebBrowser(intent, this)) {
            this.setResult(RESULT_CANCELED)
            finishWithAnimation(ActivityTransitionAnimation.Direction.END)
            return
        }
        mColorPalette = findViewById(R.id.whiteboard_editor)
        answerTimer = AnswerTimer(findViewById(R.id.card_time))
        mTextBarNew = findViewById(R.id.new_number)
        mTextBarLearn = findViewById(R.id.learn_number)
        mTextBarReview = findViewById(R.id.review_number)
        mToolbar = findViewById(R.id.toolbar)

        startLoadingCollection()
    }

    override fun onPause() {
        answerTimer.pause()
        super.onPause()
    }

    override fun onResume() {
        when {
            stopTimerOnAnswer && isDisplayingAnswer -> {}
            else -> answerTimer.resume()
        }
        super.onResume()
        if (answerField != null) {
            answerField!!.focusWithKeyboard()
        }
    }

    protected val flagToDisplay: Int
        get() {
            return FlagToDisplay(
                currentCard!!.userFlag(),
                mActionButtons.findMenuItem(ActionButtons.RES_FLAG)?.isActionButton ?: true,
                mPrefFullscreenReview
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
        val shownAsToolbarButton = mActionButtons.findMenuItem(ActionButtons.RES_MARK)?.isActionButton == true
        return !shownAsToolbarButton || mPrefFullscreenReview
    }

    protected open fun onMark(card: Card?) {
        if (card == null) {
            return
        }
        launchCatchingTask {
            toggleMark(card.note(), handler = this@Reviewer)
            refreshActionBar()
            onMarkChanged()
        }
    }

    private fun onMarkChanged() {
        if (currentCard == null) {
            return
        }
        mCardMarker!!.displayMark(shouldDisplayMark())
    }

    protected open fun onFlag(card: Card?, flag: Int) {
        if (card == null) {
            return
        }
        launchCatchingTask {
            card.setUserFlag(flag)
            withCol {
                setUserFlagForCards(listOf(card.id), flag)
            }
            refreshActionBar()
            onFlagChanged()
        }
    }

    private fun onFlagChanged() {
        if (currentCard == null) {
            return
        }
        mCardMarker!!.displayFlag(flagToDisplay)
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
        if (mPrefFullscreenReview) {
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
                if (mShowWhiteboard && whiteboard != null && !whiteboard!!.undoEmpty()) {
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
            R.id.action_bury -> {
                Timber.i("Reviewer:: Bury button pressed")
                MenuItemCompat.getActionProvider(item)?.hasSubMenu()?.let { isAvailable ->
                    if (!isAvailable) {
                        Timber.d("Bury card due to no submenu")
                        buryCard()
                    }
                } ?: Timber.w("Null ActionProvider for bury menu item in Reviewer!")
            }
            R.id.action_suspend -> {
                Timber.i("Reviewer:: Suspend button pressed")
                MenuItemCompat.getActionProvider(item)?.hasSubMenu()?.let { isAvailable ->
                    if (!isAvailable) {
                        Timber.d("Suspend card due to no submenu")
                        suspendCard()
                    }
                } ?: Timber.w("Null ActionProvider for suspend menu item in Reviewer!")
            }
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
                Timber.i("Reviewer:: Whiteboard visibility set to %b", !mShowWhiteboard)
                setWhiteboardVisibility(!mShowWhiteboard)
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
                onFlag(currentCard, CardMarker.FLAG_NONE)
            }
            R.id.action_flag_one -> {
                Timber.i("Reviewer:: Flag one")
                onFlag(currentCard, CardMarker.FLAG_RED)
            }
            R.id.action_flag_two -> {
                Timber.i("Reviewer:: Flag two")
                onFlag(currentCard, CardMarker.FLAG_ORANGE)
            }
            R.id.action_flag_three -> {
                Timber.i("Reviewer:: Flag three")
                onFlag(currentCard, CardMarker.FLAG_GREEN)
            }
            R.id.action_flag_four -> {
                Timber.i("Reviewer:: Flag four")
                onFlag(currentCard, CardMarker.FLAG_BLUE)
            }
            R.id.action_flag_five -> {
                Timber.i("Reviewer:: Flag five")
                onFlag(currentCard, CardMarker.FLAG_PINK)
            }
            R.id.action_flag_six -> {
                Timber.i("Reviewer:: Flag six")
                onFlag(currentCard, CardMarker.FLAG_TURQUOISE)
            }
            R.id.action_flag_seven -> {
                Timber.i("Reviewer:: Flag seven")
                onFlag(currentCard, CardMarker.FLAG_PURPLE)
            }
            R.id.action_card_info -> {
                Timber.i("Card Viewer:: Card Info")
                openCardInfo()
            }
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
            mColorPalette.visibility = View.GONE
        }
        refreshActionBar()
    }

    public override fun clearWhiteboard() {
        if (whiteboard != null) {
            whiteboard!!.clear()
        }
    }

    public override fun changeWhiteboardPenColor() {
        if (mColorPalette.visibility == View.GONE) {
            mColorPalette.visibility = View.VISIBLE
        } else {
            mColorPalette.visibility = View.GONE
        }
        updateWhiteboardEditorPosition()
    }

    override fun replayVoice() {
        if (!openMicToolbar()) {
            return
        }

        // COULD_BE_BETTER: this shows "Failed" if nothing was recorded
        audioView!!.togglePlay()
    }

    override fun recordVoice() {
        if (!openMicToolbar()) {
            return
        }
        audioView!!.toggleRecord()
    }

    override fun updateForNewCard() {
        super.updateForNewCard()
        if (prefWhiteboard && whiteboard != null) {
            whiteboard!!.clear()
        }
    }

    override fun unblockControls() {
        if (prefWhiteboard && whiteboard != null) {
            whiteboard!!.isEnabled = true
        }
        super.unblockControls()
    }

    public override fun blockControls(quick: Boolean) {
        if (prefWhiteboard && whiteboard != null) {
            whiteboard!!.isEnabled = false
        }
        super.blockControls(quick)
    }

    override fun closeReviewer(result: Int) {
        // Stop the mic recording if still pending
        audioView?.notifyStopRecord()

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
        if (audioView == null || audioView!!.visibility != View.VISIBLE) {
            openOrToggleMicToolbar()
        }
        return audioView != null
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
        audioView?.let {
            it.visibility = if (it.visibility != View.VISIBLE) View.VISIBLE else View.GONE
            return
        }
        // Record mic tool bar does not exist yet
        tempAudioPath = generateTempAudioFile(this)
        if (tempAudioPath == null) {
            return
        }
        audioView = createRecorderInstance(
            this,
            R.drawable.ic_play_arrow_white_24dp,
            R.drawable.ic_pause_white_24dp,
            R.drawable.ic_stop_white_24dp,
            R.drawable.ic_rec,
            R.drawable.ic_rec_stop,
            tempAudioPath!!
        )
        if (audioView == null) {
            tempAudioPath = null
            return
        }
        val lp2 = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        audioView!!.layoutParams = lp2
        val micToolBarLayer = findViewById<LinearLayout>(R.id.mic_tool_bar_layer)
        micToolBarLayer.addView(audioView)
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
        launchActivityForResultWithAnimation(intent, addNoteLauncher, animation)
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
        mActionButtons.setCustomButtonsStatus(menu)
        var alpha = if (super.controlBlocked !== ReviewerUi.ControlBlock.SLOW) Themes.ALPHA_ICON_ENABLED_LIGHT else Themes.ALPHA_ICON_DISABLED_LIGHT
        val markCardIcon = menu.findItem(R.id.action_mark_card)
        if (currentCard != null && isMarked(currentCard!!.note())) {
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
        val whiteboardIsShownAndHasStrokes = mShowWhiteboard && whiteboard?.undoEmpty() == false
        if (whiteboardIsShownAndHasStrokes) {
            undoIconId = R.drawable.eraser
            undoEnabled = true
        } else {
            undoIconId = R.drawable.ic_undo_white
            undoEnabled = colIsOpenUnsafe() && getColUnsafe.undoAvailable()
        }
        val alphaUndo = if (undoEnabled && super.controlBlocked !== ReviewerUi.ControlBlock.SLOW) Themes.ALPHA_ICON_ENABLED_LIGHT else Themes.ALPHA_ICON_DISABLED_LIGHT
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
            mOnboarding.onUndoButtonEnabled()
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
            if (!mActionButtons.status.toggleStylusIsDisabled()) {
                toggleStylusIcon.isVisible = true
            }
            if (!mActionButtons.status.hideWhiteboardIsDisabled()) {
                hideWhiteboardIcon.isVisible = true
            }
            if (!mActionButtons.status.clearWhiteboardIsDisabled()) {
                menu.findItem(R.id.action_clear_whiteboard).isVisible = true
            }
            if (!mActionButtons.status.saveWhiteboardIsDisabled()) {
                menu.findItem(R.id.action_save_whiteboard).isVisible = true
            }
            if (!mActionButtons.status.whiteboardPenColorIsDisabled()) {
                changePenColorIcon.isVisible = true
            }
            val whiteboardIcon = ContextCompat.getDrawable(this, R.drawable.ic_gesture_white)!!.mutate()
            val stylusIcon = ContextCompat.getDrawable(this, R.drawable.ic_gesture_stylus)!!.mutate()
            val whiteboardColorPaletteIcon = VectorDrawableCompat.create(resources, R.drawable.ic_color_lens_white_24dp, this.theme)!!.mutate()
            if (mShowWhiteboard) {
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
                mColorPalette.visibility = View.GONE
            }
        } else {
            toggleWhiteboardIcon.setTitle(R.string.enable_whiteboard)
        }
        if (colIsOpenUnsafe() && getColUnsafe.decks.isDyn(parentDid)) {
            menu.findItem(R.id.action_open_deck_options).isVisible = false
        }
        if (mTTS.enabled && !mActionButtons.status.selectTtsIsDisabled()) {
            menu.findItem(R.id.action_select_tts).isVisible = true
        }
        // Setup bury / suspend providers
        val suspendIcon = menu.findItem(R.id.action_suspend)
        val buryIcon = menu.findItem(R.id.action_bury)
        MenuItemCompat.setActionProvider(suspendIcon, SuspendProvider(this))
        MenuItemCompat.setActionProvider(buryIcon, BuryProvider(this))
        if (suspendNoteAvailable()) {
            suspendIcon.setIcon(R.drawable.ic_action_suspend_dropdown)
            suspendIcon.setTitle(R.string.menu_suspend)
        } else {
            suspendIcon.setIcon(R.drawable.ic_pause_circle_outline)
            suspendIcon.setTitle(R.string.menu_suspend_card)
        }
        if (buryNoteAvailable()) {
            buryIcon.setIcon(R.drawable.ic_flip_to_back_dropdown)
            buryIcon.setTitle(R.string.menu_bury)
        } else {
            buryIcon.setIcon(R.drawable.ic_flip_to_back_white)
            buryIcon.setTitle(R.string.menu_bury_card)
        }
        alpha = if (super.controlBlocked !== ReviewerUi.ControlBlock.SLOW) Themes.ALPHA_ICON_ENABLED_LIGHT else Themes.ALPHA_ICON_DISABLED_LIGHT
        buryIcon.iconAlpha = alpha
        suspendIcon.iconAlpha = alpha
        MenuItemCompat.setActionProvider(menu.findItem(R.id.action_schedule), ScheduleProvider(this))
        mOnboarding.onCreate()

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
        if (mProcessor.onKeyDown(keyCode, event) || super.onKeyDown(keyCode, event)) {
            return true
        }
        return false
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        return if (mProcessor.onKeyUp(keyCode, event)) {
            true
        } else {
            super.onKeyUp(keyCode, event)
        }
    }

    override fun canAccessScheduler(): Boolean {
        return true
    }

    override fun performReload() {
        launchCatchingTask { updateCardAndRedraw() }
    }

    override fun displayAnswerBottomBar() {
        super.displayAnswerBottomBar()
        mOnboarding.onAnswerShown()
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
        mPrefHideDueCount = preferences.getBoolean("hideDueCount", false)
        mPrefShowETA = preferences.getBoolean("showETA", true)
        mProcessor.setup()
        mPrefFullscreenReview = isFullScreenReview(preferences)
        mActionButtons.setup(preferences)
        return preferences
    }

    override fun updateActionBar() {
        super.updateActionBar()
        updateScreenCounts()
    }

    private fun updateWhiteboardEditorPosition() {
        mAnswerButtonsPosition = this.sharedPrefs()
            .getString("answerButtonPosition", "bottom")
        val layoutParams: RelativeLayout.LayoutParams
        when (mAnswerButtonsPosition) {
            "none", "top" -> {
                layoutParams = mColorPalette.layoutParams as RelativeLayout.LayoutParams
                layoutParams.removeRule(RelativeLayout.ABOVE)
                layoutParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
                mColorPalette.layoutParams = layoutParams
            }

            "bottom" -> {
                layoutParams = mColorPalette.layoutParams as RelativeLayout.LayoutParams
                layoutParams.removeRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
                layoutParams.addRule(RelativeLayout.ABOVE, R.id.bottom_area_layout)
                mColorPalette.layoutParams = layoutParams
            }
        }
    }

    private fun updateScreenCounts() {
        val queue = queueState ?: return
        super.updateActionBar()
        val actionBar = supportActionBar
        val counts = queue.counts
        if (actionBar != null) {
            if (mPrefShowETA) {
                launchCatchingTask {
                    mEta = withCol { sched.eta(counts, false) }
                    actionBar.subtitle = remainingTime(this@Reviewer, (mEta * 60).toLong())
                }
            }
        }
        mNewCount = SpannableString(counts.new.toString())
        mLrnCount = SpannableString(counts.lrn.toString())
        mRevCount = SpannableString(counts.rev.toString())
        if (mPrefHideDueCount) {
            mRevCount = SpannableString("???")
        }
        // if this code is run as a card is being answered, currentCard may be non-null but
        // the queues may be empty - we can't call countIdx() in such a case
        if (counts.count() != 0) {
            when (queue.countsIndex) {
                Counts.Queue.NEW -> mNewCount!!.setSpan(UnderlineSpan(), 0, mNewCount!!.length, 0)
                Counts.Queue.LRN -> mLrnCount!!.setSpan(UnderlineSpan(), 0, mLrnCount!!.length, 0)
                Counts.Queue.REV -> mRevCount!!.setSpan(UnderlineSpan(), 0, mRevCount!!.length, 0)
            }
        }
        mTextBarNew.text = mNewCount
        mTextBarLearn.text = mLrnCount
        mTextBarReview.text = mRevCount
    }

    override fun fillFlashcard() {
        super.fillFlashcard()
        if (!isDisplayingAnswer && mShowWhiteboard && whiteboard != null) {
            whiteboard!!.clear()
        }
    }

    override fun onPageFinished() {
        super.onPageFinished()
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
                state.topCard.load()
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
                finishWithAnimation(ActivityTransitionAnimation.Direction.END)
            }
            cancelable(true)
            setOnCancelListener { }
        }
    }

    override fun displayCardQuestion() {
        // show timer, if activated in the deck's preferences
        answerTimer.setupForCard(currentCard!!)
        delayedHide(100)
        super.displayCardQuestion()
    }

    @VisibleForTesting
    override fun displayCardAnswer() {
        delayedHide(100)
        if (stopTimerOnAnswer) {
            answerTimer.pause()
        }
        super.displayCardAnswer()
    }

    private fun runStateMutationHook() {
        val state = queueState ?: return
        if (state.customSchedulingJs.isEmpty()) {
            return
        }
        val key = customSchedulingKey
        val js = state.customSchedulingJs
        webView?.evaluateJavascript(
            """
        anki.mutateNextCardStates('$key', async (states, customData, ctx) => {{ $js }})
            .catch(err => console.log(err));
"""
        ) {}
    }

    override fun initLayout() {
        super.initLayout()
        if (!mShowRemainingCardCount) {
            mTextBarNew.visibility = View.GONE
            mTextBarLearn.visibility = View.GONE
            mTextBarReview.visibility = View.GONE
        }

        // can't move this into onCreate due to mTopBarLayout
        val mark = topBarLayout!!.findViewById<ImageView>(R.id.mark_icon)
        val flag = topBarLayout!!.findViewById<ImageView>(R.id.flag_icon)
        mCardMarker = CardMarker(mark, flag)
    }

    override fun switchTopBarVisibility(visible: Int) {
        super.switchTopBarVisibility(visible)
        answerTimer.setVisibility(visible)
        if (mShowRemainingCardCount) {
            mTextBarNew.visibility = visible
            mTextBarLearn.visibility = visible
            mTextBarReview.visibility = visible
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
            setWhiteboardVisibility(mShowWhiteboard)
        }
        if (mShowRemainingCardCount) {
            mTextBarNew.visibility = View.VISIBLE
            mTextBarLearn.visibility = View.VISIBLE
            mTextBarReview.visibility = View.VISIBLE
        }
    }

    override fun executeCommand(which: ViewerCommand, fromGesture: Gesture?): Boolean {
        if (isControlBlocked && which !== ViewerCommand.EXIT) {
            return false
        }
        when (which) {
            ViewerCommand.TOGGLE_FLAG_RED -> {
                toggleFlag(CardMarker.FLAG_RED)
                return true
            }
            ViewerCommand.TOGGLE_FLAG_ORANGE -> {
                toggleFlag(CardMarker.FLAG_ORANGE)
                return true
            }
            ViewerCommand.TOGGLE_FLAG_GREEN -> {
                toggleFlag(CardMarker.FLAG_GREEN)
                return true
            }
            ViewerCommand.TOGGLE_FLAG_BLUE -> {
                toggleFlag(CardMarker.FLAG_BLUE)
                return true
            }
            ViewerCommand.TOGGLE_FLAG_PINK -> {
                toggleFlag(CardMarker.FLAG_PINK)
                return true
            }
            ViewerCommand.TOGGLE_FLAG_TURQUOISE -> {
                toggleFlag(CardMarker.FLAG_TURQUOISE)
                return true
            }
            ViewerCommand.TOGGLE_FLAG_PURPLE -> {
                toggleFlag(CardMarker.FLAG_PURPLE)
                return true
            }
            ViewerCommand.UNSET_FLAG -> {
                onFlag(currentCard, CardMarker.FLAG_NONE)
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
            else -> return super.executeCommand(which, fromGesture)
        }
    }

    private fun toggleFlag(@FlagDef flag: Int) {
        if (currentCard!!.userFlag() == flag) {
            Timber.i("Toggle flag: unsetting flag")
            onFlag(currentCard, CardMarker.FLAG_NONE)
        } else {
            Timber.i("Toggle flag: Setting flag to %d", flag)
            onFlag(currentCard, flag)
        }
    }

    override fun restoreCollectionPreferences(col: Collection) {
        super.restoreCollectionPreferences(col)
        mShowRemainingCardCount = col.config.get("dueCounts") ?: true
        stopTimerOnAnswer = col.decks.confForDid(col.decks.current().id).getBoolean("stopTimerOnAnswer")
    }

    override fun onSingleTap(): Boolean {
        if (mPrefFullscreenReview && isImmersiveSystemUiVisible(this)) {
            delayedHide(INITIAL_HIDE_DELAY)
            return true
        }
        return false
    }

    override fun onFling() {
        if (mPrefFullscreenReview && isImmersiveSystemUiVisible(this)) {
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

    private val mFullScreenHandler: Handler = object : Handler(getDefaultLooper()) {
        override fun handleMessage(msg: Message) {
            if (mPrefFullscreenReview) {
                setFullScreen(this@Reviewer)
            }
        }
    }

    /** Hide the navigation if in full-screen mode after a given period of time  */
    protected open fun delayedHide(delayMillis: Int) {
        Timber.d("Fullscreen delayed hide in %dms", delayMillis)
        mFullScreenHandler.removeMessages(0)
        mFullScreenHandler.sendEmptyMessageDelayed(0, delayMillis.toLong())
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
                !mShowWhiteboard || (
                    mPrefFullscreenReview &&
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
        mShowWhiteboard = state
        MetaDB.storeWhiteboardVisibility(this, parentDid, state)
        if (state) {
            whiteboard!!.visibility = View.VISIBLE
            disableDrawerSwipe()
        } else {
            whiteboard!!.visibility = View.GONE
            if (!mHasDrawerSwipeConflicts) {
                enableDrawerSwipe()
            }
        }
    }

    private fun disableDrawerSwipeOnConflicts() {
        if (mGestureProcessor.isBound(Gesture.SWIPE_UP, Gesture.SWIPE_DOWN, Gesture.SWIPE_RIGHT)) {
            mHasDrawerSwipeConflicts = true
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
            mFullScreenHandler.removeMessages(0)
        }
    }

    /**
     * Whether or not dismiss note is available for current card and specified DismissType
     * @return true if there is another card of same note that could be dismissed
     */
    @KotlinCleanup("mCurrentCard handling")
    private fun suspendNoteAvailable(): Boolean {
        return if (currentCard == null || isControlBlocked) {
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
        return if (currentCard == null || isControlBlocked) {
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
        return mHasDrawerSwipeConflicts
    }

    /**
     * Inner class which implements the submenu for the Suspend button
     */
    internal inner class SuspendProvider(context: Context) : ActionProviderCompat(context), MenuItem.OnMenuItemClickListener {

        override fun onCreateActionView(forItem: MenuItem): View {
            return createActionViewWith(context, forItem, R.menu.reviewer_suspend, ::onMenuItemClick) {
                hasSubMenu()
            }
        }

        override fun hasSubMenu(): Boolean {
            return suspendNoteAvailable()
        }

        override fun onPrepareSubMenu(subMenu: SubMenu) {
            subMenu.clear()
            menuInflater.inflate(R.menu.reviewer_suspend, subMenu)
            for (i in 0 until subMenu.size()) {
                subMenu.getItem(i).setOnMenuItemClickListener(this)
            }
        }

        override fun onMenuItemClick(item: MenuItem): Boolean {
            val itemId = item.itemId
            if (itemId == R.id.action_suspend_card) {
                return suspendCard()
            } else if (itemId == R.id.action_suspend_note) {
                return suspendNote()
            }
            return false
        }
    }

    /**
     * Inner class which implements the submenu for the Bury button
     */
    internal inner class BuryProvider(context: Context) : ActionProviderCompat(context), MenuItem.OnMenuItemClickListener {

        override fun onCreateActionView(forItem: MenuItem): View {
            return createActionViewWith(context, forItem, R.menu.reviewer_bury, ::onMenuItemClick) {
                hasSubMenu()
            }
        }

        override fun hasSubMenu(): Boolean {
            return buryNoteAvailable()
        }

        override fun onPrepareSubMenu(subMenu: SubMenu) {
            subMenu.clear()
            menuInflater.inflate(R.menu.reviewer_bury, subMenu)
            for (i in 0 until subMenu.size()) {
                subMenu.getItem(i).setOnMenuItemClickListener(this)
            }
        }

        override fun onMenuItemClick(item: MenuItem): Boolean {
            val itemId = item.itemId
            if (itemId == R.id.action_bury_card) {
                return buryCard()
            } else if (itemId == R.id.action_bury_note) {
                return buryNote()
            }
            return false
        }
    }

    private fun createActionViewWith(
        context: Context,
        menuItem: MenuItem,
        @MenuRes subMenuRes: Int,
        onMenuItemSelection: (MenuItem) -> Boolean,
        showsSubMenu: () -> Boolean
    ): View = ImageButton(context, null, android.R.attr.actionButtonStyle).apply {
        TooltipCompat.setTooltipText(this, menuItem.title)
        menuItem.icon?.isAutoMirrored = true
        setImageDrawable(menuItem.icon)
        id = menuItem.itemId
        setOnClickListener {
            if (!menuItem.isEnabled) {
                return@setOnClickListener
            }
            if (showsSubMenu()) {
                PopupMenu(context, this).apply {
                    inflate(subMenuRes)
                    setOnMenuItemClickListener(onMenuItemSelection)
                    show()
                }
            } else {
                onOptionsItemSelected(menuItem)
            }
        }
    }

    /**
     * Inner class which implements the submenu for the Schedule button
     */
    internal inner class ScheduleProvider(context: Context) : ActionProviderCompat(context), MenuItem.OnMenuItemClickListener {

        override fun onCreateActionView(forItem: MenuItem): View {
            return createActionViewWith(context, forItem, R.menu.reviewer_schedule, ::onMenuItemClick) { true }
        }

        override fun hasSubMenu(): Boolean {
            return true
        }

        override fun onPrepareSubMenu(subMenu: SubMenu) {
            subMenu.clear()
            menuInflater.inflate(R.menu.reviewer_schedule, subMenu)
            for (i in 0 until subMenu.size()) {
                subMenu.getItem(i).setOnMenuItemClickListener(this)
            }
        }

        override fun onMenuItemClick(item: MenuItem): Boolean {
            val itemId = item.itemId
            if (itemId == R.id.action_reschedule_card) {
                showRescheduleCardDialog()
                return true
            } else if (itemId == R.id.action_reset_card_progress) {
                showResetCardDialog()
                return true
            }
            return false
        }
    }

    override fun javaScriptFunction(): AnkiDroidJsAPI {
        return AnkiDroidJsAPI(this)
    }

    override fun getCardDataForJsApi(): AnkiDroidJsAPI.CardDataForJsApi {
        val cardDataForJsAPI = AnkiDroidJsAPI.CardDataForJsApi().apply {
            newCardCount = mNewCount.toString()
            lrnCardCount = mLrnCount.toString()
            revCardCount = mRevCount.toString()
            nextTime1 = easeButton1!!.nextTime
            nextTime2 = easeButton2!!.nextTime
            nextTime3 = easeButton3!!.nextTime
            nextTime4 = easeButton4!!.nextTime
            eta = mEta
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
