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
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.text.SpannableString
import android.text.style.UnderlineSpan
import android.view.*
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.*
import androidx.appcompat.view.menu.MenuBuilder
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.ActionProvider
import androidx.core.view.MenuItemCompat
import androidx.vectordrawable.graphics.drawable.VectorDrawableCompat
import com.ichi2.anim.ActivityTransitionAnimation
import com.ichi2.anki.AnkiDroidJsAPIConstants.SET_CARD_DUE
import com.ichi2.anki.AnkiDroidJsAPIConstants.ankiJsErrorCodeDefault
import com.ichi2.anki.AnkiDroidJsAPIConstants.ankiJsErrorCodeSetDue
import com.ichi2.anki.UIUtils.saveCollectionInBackground
import com.ichi2.anki.UIUtils.showThemedToast
import com.ichi2.anki.Whiteboard.Companion.createInstance
import com.ichi2.anki.Whiteboard.OnPaintColorChangeListener
import com.ichi2.anki.cardviewer.CardAppearance.Companion.isInNightMode
import com.ichi2.anki.cardviewer.Gesture
import com.ichi2.anki.cardviewer.ViewerCommand
import com.ichi2.anki.dialogs.ConfirmationDialog
import com.ichi2.anki.dialogs.RescheduleDialog.rescheduleSingleCard
import com.ichi2.anki.multimediacard.AudioView
import com.ichi2.anki.multimediacard.AudioView.Companion.createRecorderInstance
import com.ichi2.anki.multimediacard.AudioView.Companion.generateTempAudioFile
import com.ichi2.anki.reviewer.*
import com.ichi2.anki.reviewer.AnswerButtons.Companion.getBackgroundColors
import com.ichi2.anki.reviewer.AnswerButtons.Companion.getTextColors
import com.ichi2.anki.reviewer.CardMarker.FlagDef
import com.ichi2.anki.reviewer.FullScreenMode.Companion.fromPreference
import com.ichi2.anki.reviewer.FullScreenMode.Companion.isFullScreenReview
import com.ichi2.anki.servicelayer.NoteService.isMarked
import com.ichi2.anki.servicelayer.NoteService.toggleMark
import com.ichi2.anki.servicelayer.SchedulerService.*
import com.ichi2.anki.servicelayer.TaskListenerBuilder
import com.ichi2.anki.workarounds.FirefoxSnackbarWorkaround.handledLaunchFromWebBrowser
import com.ichi2.annotations.NeedsTest
import com.ichi2.libanki.*
import com.ichi2.libanki.Collection
import com.ichi2.libanki.sched.Counts
import com.ichi2.themes.Themes
import com.ichi2.themes.Themes.getColorFromAttr
import com.ichi2.utils.AndroidUiUtils.isRunningOnTv
import com.ichi2.utils.Computation
import com.ichi2.utils.HandlerUtils.getDefaultLooper
import com.ichi2.utils.HandlerUtils.postDelayedOnNewHandler
import com.ichi2.utils.HandlerUtils.postOnNewHandler
import com.ichi2.utils.KotlinCleanup
import com.ichi2.utils.Permissions.canRecordAudio
import com.ichi2.utils.ViewGroupUtils.setRenderWorkaround
import com.ichi2.widget.WidgetStatus.update
import timber.log.Timber
import java.io.File
import java.lang.ref.WeakReference
import java.util.function.Consumer

@KotlinCleanup("too many to count")
open class Reviewer : AbstractFlashcardViewer() {
    private var mHasDrawerSwipeConflicts = false
    private var mShowWhiteboard = true
    private var mPrefFullscreenReview = false
    private var mColorPalette: LinearLayout? = null

    // TODO: Consider extracting to ViewModel
    // Card counts
    private var mNewCount: SpannableString? = null
    private var mLrnCount: SpannableString? = null
    private var mRevCount: SpannableString? = null
    private lateinit var mTextBarNew: TextView
    private lateinit var mTextBarLearn: TextView
    private lateinit var mTextBarReview: TextView
    protected lateinit var answerTimer: AnswerTimer
    private var mPrefHideDueCount = false

    // Whiteboard
    @JvmField
    var mPrefWhiteboard = false

    @get:CheckResult
    @get:VisibleForTesting(otherwise = VisibleForTesting.NONE)
    var whiteboard: Whiteboard? = null
        protected set
    // Record Audio
    /** File of the temporary mic record  */
    @get:VisibleForTesting(otherwise = VisibleForTesting.NONE)
    var audioView: AudioView? = null
        protected set
    protected var tempAudioPath: String? = null

    // ETA
    private var mEta = 0
    private var mPrefShowETA = false

    /** Handle Mark/Flag state of cards  */
    private var mCardMarker: CardMarker? = null

    // Preferences from the collection
    private var mShowRemainingCardCount = false
    private val mActionButtons = ActionButtons(this)

    @JvmField
    @VisibleForTesting
    protected val mProcessor = PeripheralKeymap(this, this)
    private val mOnboarding = Onboarding.Reviewer(this)
    protected fun <T : Computation<NextCard<Array<Card>>>> scheduleCollectionTaskHandler(@PluralsRes toastResourceId: Int): TaskListenerBuilder<Unit, T> {
        return nextCardHandler<Computation<NextCard<*>>>().alsoExecuteAfter { result: T ->
            // BUG: If the method crashes, this will crash
            invalidateOptionsMenu()
            val cardCount: Int = result.value.result.size
            showThemedToast(
                this,
                resources.getQuantityString(toastResourceId, cardCount, cardCount), true
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        if (showedActivityFailedScreen(savedInstanceState)) {
            return
        }
        Timber.d("onCreate()")
        super.onCreate(savedInstanceState)
        if (handledLaunchFromWebBrowser(intent, this)) {
            this.setResult(RESULT_CANCELED)
            finishWithAnimation(ActivityTransitionAnimation.Direction.END)
            return
        }
        if (Intent.ACTION_VIEW == intent.action) {
            Timber.d("onCreate() :: received Intent with action = %s", intent.action)
            selectDeckFromExtra()
        }
        mColorPalette = findViewById(R.id.whiteboard_editor)
        answerTimer = AnswerTimer(findViewById(R.id.card_time))
        mTextBarNew = findViewById(R.id.new_number)
        mTextBarLearn = findViewById(R.id.learn_number)
        mTextBarReview = findViewById(R.id.review_number)
        startLoadingCollection()
    }

    override fun onPause() {
        answerTimer.pause()
        super.onPause()
    }

    override fun onResume() {
        answerTimer.resume()
        super.onResume()
    }

    @NeedsTest("is hidden if flag is on app bar")
    @NeedsTest("is not hidden if flag is not on app bar")
    @NeedsTest("is not hidden if flag is on app bar and fullscreen is enabled")
    protected val flagToDisplay: Int
        get() {
            val actualValue = mCurrentCard!!.userFlag()
            if (actualValue == CardMarker.FLAG_NONE) {
                return CardMarker.FLAG_NONE
            }
            val isShownInActionBar = mActionButtons.isShownInActionBar(ActionButtons.RES_FLAG)
            return if (isShownInActionBar != null && isShownInActionBar && !mPrefFullscreenReview) {
                CardMarker.FLAG_NONE
            } else actualValue
        }

    override fun createWebView(): WebView {
        val ret = super.createWebView()!!
        if (isRunningOnTv(this)) {
            ret.isFocusable = false
        }
        return ret
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
        val isShownInActionBar = mActionButtons.isShownInActionBar(ActionButtons.RES_MARK)
        // If we don't know, show it.
        // Otherwise, if it's in the action bar, don't show it again.
        return isShownInActionBar == null || !isShownInActionBar || mPrefFullscreenReview
    }

    protected open fun onMark(card: Card?) {
        if (card == null) {
            return
        }
        toggleMark(card.note())
        refreshActionBar()
        onMarkChanged()
    }

    private fun onMarkChanged() {
        if (mCurrentCard == null) {
            return
        }
        mCardMarker!!.displayMark(shouldDisplayMark())
    }

    protected open fun onFlag(card: Card?, flag: Int) {
        if (card == null) {
            return
        }
        card.setUserFlag(flag)
        card.flush()
        refreshActionBar()
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
            } */onFlagChanged()
    }

    private fun onFlagChanged() {
        if (mCurrentCard == null) {
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

        // Clear the undo history when selecting a new deck
        if (col.decks.selected() != did) {
            col.clearUndo()
        }
        // Select the deck
        col.decks.select(did)
        // Reset the schedule so that we get the counts for the currently selected deck
        col.sched.deferReset()
    }

    override fun setTitle() {
        val title: String
        title = if (colIsOpen()) {
            Decks.basename(col.decks.current().getString("name"))
        } else {
            Timber.e("Could not set title in reviewer because collection closed")
            ""
        }
        supportActionBar!!.setTitle(title)
        super.setTitle(title)
        supportActionBar!!.setSubtitle("")
    }

    override fun getContentViewAttr(fullscreenMode: FullScreenMode?): Int {
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
        // Load the first card and start reviewing. Uses the answer card
        // task to load a card, but since we send null
        // as the card to answer, no card will be answered.
        mPrefWhiteboard = MetaDB.getWhiteboardState(this, parentDid)
        if (mPrefWhiteboard) {
            // DEFECT: Slight inefficiency here, as we set the database using these methods
            val whiteboardVisibility = MetaDB.getWhiteboardVisibility(this, parentDid)
            setWhiteboardEnabledState(true)
            setWhiteboardVisibility(whiteboardVisibility)
        }
        col.sched.deferReset() // Reset schedule in case card was previously loaded
        getCol().startTimebox()
        GetCard().runWithHandler(answerCardHandler(false))
        disableDrawerSwipeOnConflicts()
        // Add a weak reference to current activity so that scheduler can talk to to Activity
        sched!!.setContext(WeakReference(this))

        // Set full screen/immersive mode if needed
        if (mPrefFullscreenReview) {
            setFullScreen(this)
        }
        setRenderWorkaround(this)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // 100ms was not enough on my device (Honor 9 Lite -  Android Pie)
        delayedHide(1000)
        if (drawerToggle.onOptionsItemSelected(item)) {
            return true
        }
        val itemId = item.itemId
        when (itemId) {
            android.R.id.home -> {
                Timber.i("Reviewer:: Home button pressed")
                closeReviewer(RESULT_OK, true)
            }
            R.id.action_undo -> {
                Timber.i("Reviewer:: Undo button pressed")
                if (mShowWhiteboard && whiteboard != null && !whiteboard!!.undoEmpty()) {
                    whiteboard!!.undo()
                } else {
                    undo()
                }
            }
            R.id.action_reset_card_progress -> {
                Timber.i("Reviewer:: Reset progress button pressed")
                showResetCardDialog()
            }
            R.id.action_mark_card -> {
                Timber.i("Reviewer:: Mark button pressed")
                onMark(mCurrentCard)
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
                if (!MenuItemCompat.getActionProvider(item).hasSubMenu()) {
                    Timber.d("Bury card due to no submenu")
                    buryCard()
                }
            }
            R.id.action_suspend -> {
                Timber.i("Reviewer:: Suspend button pressed")
                if (!MenuItemCompat.getActionProvider(item).hasSubMenu()) {
                    Timber.d("Suspend card due to no submenu")
                    suspendCard()
                }
            }
            R.id.action_delete -> {
                Timber.i("Reviewer:: Delete note button pressed")
                showDeleteNoteDialog()
            }
            R.id.action_change_whiteboard_pen_color -> {
                Timber.i("Reviewer:: Pen Color button pressed")
                if (mColorPalette!!.visibility == View.GONE) {
                    mColorPalette!!.visibility = View.VISIBLE
                } else {
                    mColorPalette!!.visibility = View.GONE
                }
            }
            R.id.action_save_whiteboard -> {
                Timber.i("Reviewer:: Save whiteboard button pressed")
                if (whiteboard != null) {
                    try {
                        val savedWhiteboardFileName = whiteboard!!.saveWhiteboard(col.time).path
                        showThemedToast(this@Reviewer, getString(R.string.white_board_image_saved, savedWhiteboardFileName), true)
                    } catch (e: Exception) {
                        Timber.w(e)
                        showThemedToast(this@Reviewer, getString(R.string.white_board_image_save_failed, e.localizedMessage), true)
                    }
                }
            }
            R.id.action_clear_whiteboard -> {
                Timber.i("Reviewer:: Clear whiteboard button pressed")
                if (whiteboard != null) {
                    whiteboard!!.clear()
                }
            }
            R.id.action_hide_whiteboard -> { // toggle whiteboard visibility
                Timber.i("Reviewer:: Whiteboard visibility set to %b", !mShowWhiteboard)
                setWhiteboardVisibility(!mShowWhiteboard)
                refreshActionBar()
            }
            R.id.action_toggle_whiteboard -> {
                toggleWhiteboard()
            }
            R.id.action_open_deck_options -> {
                val i = Intent(this, DeckOptions::class.java)
                startActivityForResultWithAnimation(i, DECK_OPTIONS, ActivityTransitionAnimation.Direction.FADE)
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
                onFlag(mCurrentCard, CardMarker.FLAG_NONE)
            }
            R.id.action_flag_one -> {
                Timber.i("Reviewer:: Flag one")
                onFlag(mCurrentCard, CardMarker.FLAG_RED)
            }
            R.id.action_flag_two -> {
                Timber.i("Reviewer:: Flag two")
                onFlag(mCurrentCard, CardMarker.FLAG_ORANGE)
            }
            R.id.action_flag_three -> {
                Timber.i("Reviewer:: Flag three")
                onFlag(mCurrentCard, CardMarker.FLAG_GREEN)
            }
            R.id.action_flag_four -> {
                Timber.i("Reviewer:: Flag four")
                onFlag(mCurrentCard, CardMarker.FLAG_BLUE)
            }
            R.id.action_flag_five -> {
                Timber.i("Reviewer:: Flag five")
                onFlag(mCurrentCard, CardMarker.FLAG_PINK)
            }
            R.id.action_flag_six -> {
                Timber.i("Reviewer:: Flag six")
                onFlag(mCurrentCard, CardMarker.FLAG_TURQUOISE)
            }
            R.id.action_flag_seven -> {
                Timber.i("Reviewer:: Flag seven")
                onFlag(mCurrentCard, CardMarker.FLAG_PURPLE)
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
        mPrefWhiteboard = !mPrefWhiteboard
        Timber.i("Reviewer:: Whiteboard enabled state set to %b", mPrefWhiteboard)
        // Even though the visibility is now stored in its own setting, we want it to be dependent
        // on the enabled status
        setWhiteboardEnabledState(mPrefWhiteboard)
        setWhiteboardVisibility(mPrefWhiteboard)
        if (!mPrefWhiteboard) {
            mColorPalette!!.visibility = View.GONE
        }
        refreshActionBar()
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
        if (mPrefWhiteboard && whiteboard != null) {
            whiteboard!!.clear()
        }
    }

    override fun unblockControls() {
        if (mPrefWhiteboard && whiteboard != null) {
            whiteboard!!.isEnabled = true
        }
        super.unblockControls()
    }

    public override fun blockControls(quick: Boolean) {
        if (mPrefWhiteboard && whiteboard != null) {
            whiteboard!!.isEnabled = false
        }
        super.blockControls(quick)
    }

    @KotlinCleanup("tempAudioPath!!")
    override fun closeReviewer(result: Int, saveDeck: Boolean) {
        // Stop the mic recording if still pending
        if (audioView != null) {
            audioView!!.notifyStopRecord()
        }
        // Remove the temporary audio file
        if (tempAudioPath != null) {
            val tempAudioPathToDelete = File(tempAudioPath!!)
            if (tempAudioPathToDelete.exists()) {
                tempAudioPathToDelete.delete()
            }
        }
        super.closeReviewer(result, saveDeck)
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

    protected fun openOrToggleMicToolbar() {
        if (!canRecordAudio(this)) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.RECORD_AUDIO),
                REQUEST_AUDIO_PERMISSION
            )
        } else {
            toggleMicToolBar()
        }
    }

    @KotlinCleanup("maybe scope function")
    private fun toggleMicToolBar() {
        if (audioView != null) {
            // It exists swap visibility status
            if (audioView!!.visibility != View.VISIBLE) {
                audioView!!.visibility = View.VISIBLE
            } else {
                audioView!!.visibility = View.GONE
            }
        } else {
            // Record mic tool bar does not exist yet
            tempAudioPath = generateTempAudioFile(this)
            if (tempAudioPath == null) {
                return
            }
            audioView = createRecorderInstance(
                this, R.drawable.ic_play_arrow_white_24dp, R.drawable.ic_pause_white_24dp,
                R.drawable.ic_stop_white_24dp, R.drawable.ic_rec, R.drawable.ic_rec_stop, tempAudioPath!!
            )
            if (audioView == null) {
                tempAudioPath = null
                return
            }
            val lp2 = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
            audioView!!.layoutParams = lp2
            val micToolBarLayer = findViewById<LinearLayout>(R.id.mic_tool_bar_layer)
            micToolBarLayer.addView(audioView)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_AUDIO_PERMISSION &&
            permissions.size >= 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            // Get get audio record permission, so we can create the record tool bar
            toggleMicToolBar()
        }
    }

    private fun showRescheduleCardDialog() {
        val runnable = Consumer { days: Int ->
            val cardIds = listOf(mCurrentCard!!.id)
            RescheduleCards(cardIds, days).runWithHandler(scheduleCollectionTaskHandler(R.plurals.reschedule_cards_dialog_acknowledge))
        }
        val dialog = rescheduleSingleCard(resources, mCurrentCard!!, runnable)
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
            val cardIds = listOf(mCurrentCard!!.id)
            ResetCards(cardIds).runWithHandler(scheduleCollectionTaskHandler(R.plurals.reset_cards_dialog_acknowledge))
        }
        dialog.setConfirm(confirm)
        showDialogFragment(dialog)
    }

    private fun addNote() {
        val intent = Intent(this, NoteEditor::class.java)
        intent.putExtra(NoteEditor.EXTRA_CALLER, NoteEditor.CALLER_REVIEWER_ADD)
        startActivityForResultWithAnimation(intent, ADD_NOTE, ActivityTransitionAnimation.Direction.START)
    }

    override fun onMenuOpened(featureId: Int, menu: Menu): Boolean {
        postOnNewHandler {
            for (i in 0 until menu.size()) {
                val menuItem = menu.getItem(i)
                shouldUseDefaultColor(menuItem)
            }
        }
        return super.onMenuOpened(featureId, menu)
    }

    /**
     * This Method changes the color of icon if user taps in overflow button.
     */
    private fun shouldUseDefaultColor(menuItem: MenuItem) {
        val drawable = menuItem.icon
        if (drawable != null && !isFlagResource(menuItem.itemId)) {
            drawable.mutate()
            drawable.setTint(ResourcesCompat.getColor(resources, R.color.material_blue_600, null))
        }
    }

    override fun onPanelClosed(featureId: Int, menu: Menu) {
        postDelayedOnNewHandler({ refreshActionBar() }, 100)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // NOTE: This is called every time a new question is shown via invalidate options menu
        menuInflater.inflate(R.menu.reviewer, menu)
        displayIconsOnTv(menu)
        displayIcons(menu)
        mActionButtons.setCustomButtonsStatus(menu)
        var alpha = if (super.controlBlocked !== ReviewerUi.ControlBlock.SLOW) Themes.ALPHA_ICON_ENABLED_LIGHT else Themes.ALPHA_ICON_DISABLED_LIGHT
        val markCardIcon = menu.findItem(R.id.action_mark_card)
        if (mCurrentCard != null && isMarked(mCurrentCard!!.note())) {
            markCardIcon.setTitle(R.string.menu_unmark_note).setIcon(R.drawable.ic_star_white)
        } else {
            markCardIcon.setTitle(R.string.menu_mark_note).setIcon(R.drawable.ic_star_border_white)
        }
        markCardIcon.icon.mutate().alpha = alpha

        // 1643 - currently null on a TV
        val flag_icon = menu.findItem(R.id.action_flag)
        if (flag_icon != null) {
            if (mCurrentCard != null) {
                when (mCurrentCard!!.userFlag()) {
                    1 -> flag_icon.setIcon(R.drawable.ic_flag_red)
                    2 -> flag_icon.setIcon(R.drawable.ic_flag_orange)
                    3 -> flag_icon.setIcon(R.drawable.ic_flag_green)
                    4 -> flag_icon.setIcon(R.drawable.ic_flag_blue)
                    5 -> flag_icon.setIcon(R.drawable.ic_flag_pink)
                    6 -> flag_icon.setIcon(R.drawable.ic_flag_turquoise)
                    7 -> flag_icon.setIcon(R.drawable.ic_flag_purple)
                    else -> flag_icon.setIcon(R.drawable.ic_flag_transparent)
                }
            }
            flag_icon.icon.mutate().alpha = alpha
        }

        // Undo button
        @DrawableRes val undoIconId: Int
        val undoEnabled: Boolean
        if (mShowWhiteboard && whiteboard != null && whiteboard!!.isUndoModeActive) {
            // Whiteboard is here and strokes have been added at some point
            undoIconId = R.drawable.eraser
            undoEnabled = !whiteboard!!.undoEmpty()
        } else {
            // We can arrive here even if `mShowWhiteboard &&
            // mWhiteboard != null` if no stroke had ever been made
            undoIconId = R.drawable.ic_undo_white
            undoEnabled = colIsOpen() && col.undoAvailable()
        }
        val alpha_undo = if (undoEnabled && super.controlBlocked !== ReviewerUi.ControlBlock.SLOW) Themes.ALPHA_ICON_ENABLED_LIGHT else Themes.ALPHA_ICON_DISABLED_LIGHT
        val undoIcon = menu.findItem(R.id.action_undo)
        undoIcon.setIcon(undoIconId)
        undoIcon.setEnabled(undoEnabled).icon.mutate().alpha = alpha_undo
        undoIcon.actionView.isEnabled = undoEnabled
        if (colIsOpen()) { // Required mostly because there are tests where `col` is null
            undoIcon.title = resources.getString(R.string.studyoptions_congrats_undo, col.undoName(resources))
        }
        if (undoEnabled) {
            mOnboarding.onUndoButtonEnabled()
        }
        val toggle_whiteboard_icon = menu.findItem(R.id.action_toggle_whiteboard)
        val hide_whiteboard_icon = menu.findItem(R.id.action_hide_whiteboard)
        val change_pen_color_icon = menu.findItem(R.id.action_change_whiteboard_pen_color)
        // White board button
        if (mPrefWhiteboard) {
            // Configure the whiteboard related items in the action bar
            toggle_whiteboard_icon.setTitle(R.string.disable_whiteboard)
            // Always allow "Disable Whiteboard", even if "Enable Whiteboard" is disabled
            toggle_whiteboard_icon.isVisible = true
            if (!mActionButtons.status.hideWhiteboardIsDisabled()) {
                hide_whiteboard_icon.isVisible = true
            }
            if (!mActionButtons.status.clearWhiteboardIsDisabled()) {
                menu.findItem(R.id.action_clear_whiteboard).isVisible = true
            }
            if (!mActionButtons.status.saveWhiteboardIsDisabled()) {
                menu.findItem(R.id.action_save_whiteboard).isVisible = true
            }
            if (!mActionButtons.status.whiteboardPenColorIsDisabled()) {
                change_pen_color_icon.isVisible = true
            }
            val whiteboardIcon = ContextCompat.getDrawable(this, R.drawable.ic_gesture_white)!!.mutate()
            val whiteboardColorPaletteIcon = VectorDrawableCompat.create(resources, R.drawable.ic_color_lens_white_24dp, null)!!.mutate()
            if (mShowWhiteboard) {
                whiteboardIcon.alpha = Themes.ALPHA_ICON_ENABLED_LIGHT
                hide_whiteboard_icon.icon = whiteboardIcon
                hide_whiteboard_icon.setTitle(R.string.hide_whiteboard)
                whiteboardColorPaletteIcon.alpha = Themes.ALPHA_ICON_ENABLED_LIGHT
                change_pen_color_icon.icon = whiteboardColorPaletteIcon
            } else {
                whiteboardIcon.alpha = Themes.ALPHA_ICON_DISABLED_LIGHT
                hide_whiteboard_icon.icon = whiteboardIcon
                hide_whiteboard_icon.setTitle(R.string.show_whiteboard)
                whiteboardColorPaletteIcon.alpha = Themes.ALPHA_ICON_DISABLED_LIGHT
                change_pen_color_icon.isEnabled = false
                change_pen_color_icon.icon = whiteboardColorPaletteIcon
                mColorPalette!!.visibility = View.GONE
            }
        } else {
            toggle_whiteboard_icon.setTitle(R.string.enable_whiteboard)
        }
        if (colIsOpen() && col.decks.isDyn(parentDid)) {
            menu.findItem(R.id.action_open_deck_options).isVisible = false
        }
        if (mTTS.enabled && !mActionButtons.status.selectTtsIsDisabled()) {
            menu.findItem(R.id.action_select_tts).isVisible = true
        }
        // Setup bury / suspend providers
        val suspend_icon = menu.findItem(R.id.action_suspend)
        val bury_icon = menu.findItem(R.id.action_bury)
        setupSubMenu(menu, R.id.action_suspend, SuspendProvider(this))
        setupSubMenu(menu, R.id.action_bury, BuryProvider(this))
        if (suspendNoteAvailable()) {
            suspend_icon.setIcon(R.drawable.ic_action_suspend_dropdown)
            suspend_icon.setTitle(R.string.menu_suspend)
        } else {
            suspend_icon.setIcon(R.drawable.ic_pause_circle_outline)
            suspend_icon.setTitle(R.string.menu_suspend_card)
        }
        if (buryNoteAvailable()) {
            bury_icon.setIcon(R.drawable.ic_flip_to_back_dropdown)
            bury_icon.setTitle(R.string.menu_bury)
        } else {
            bury_icon.setIcon(R.drawable.ic_flip_to_back_white)
            bury_icon.setTitle(R.string.menu_bury_card)
        }
        alpha = if (super.controlBlocked !== ReviewerUi.ControlBlock.SLOW) Themes.ALPHA_ICON_ENABLED_LIGHT else Themes.ALPHA_ICON_DISABLED_LIGHT
        bury_icon.icon.mutate().alpha = alpha
        suspend_icon.icon.mutate().alpha = alpha
        setupSubMenu(menu, R.id.action_schedule, ScheduleProvider(this))
        mOnboarding.onCreate()
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

    @SuppressLint("RestrictedApi") // setOptionalIconsVisible
    private fun displayIconsOnTv(menu: Menu) {
        if (!isRunningOnTv(this)) {
            return
        }
        try {
            if (menu is MenuBuilder) {
                menu.setOptionalIconsVisible(true)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                for (i in 0 until menu.size()) {
                    val m = menu.getItem(i)
                    if (m == null || isFlagResource(m.itemId)) {
                        continue
                    }
                    val color = getColorFromAttr(this, R.attr.navDrawerItemColor)
                    MenuItemCompat.setIconTintList(m, ColorStateList.valueOf(color))
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to display icons")
        } catch (e: Error) {
            Timber.w(e, "Failed to display icons")
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
        if (!isRunningOnTv(this)) {
            return false
        }

        // Process DPAD Up/Down to focus the TV Controls
        if (keyCode != KeyEvent.KEYCODE_DPAD_DOWN && keyCode != KeyEvent.KEYCODE_DPAD_UP) {
            return false
        }

        // HACK: This shouldn't be required, as the navigation should handle this.
        if (isDrawerOpen) {
            return false
        }
        val view = (if (keyCode == KeyEvent.KEYCODE_DPAD_UP) findViewById(R.id.tv_nav_view) else findViewById<View>(R.id.answer_options_layout))
            ?: return false
        // HACK: We should be performing this in the base class, or allowing the view to be focused by the keyboard.
        // I couldn't get either to work
        view.requestFocus()
        return true
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        return if (mProcessor.onKeyUp(keyCode, event)) {
            true
        } else super.onKeyUp(keyCode, event)
    }

    private fun <T> setupSubMenu(menu: Menu, @IdRes parentMenu: Int, subMenuProvider: T) where T : ActionProvider?, T : SubMenuProvider? {
        if (!isRunningOnTv(this)) {
            MenuItemCompat.setActionProvider(menu.findItem(parentMenu), subMenuProvider)
            return
        }

        // Don't do anything if the menu is hidden (bury for example)
        if (!subMenuProvider!!.hasSubMenu()) {
            return
        }

        // 7227 - If we're running on a TV, then we can't show submenus until AOSP is fixed
        menu.removeItem(parentMenu)
        val count = menu.size()
        // move the menu to the bottom of the page
        menuInflater.inflate(subMenuProvider.subMenu, menu)
        for (i in 0 until menu.size() - count) {
            val item = menu.getItem(count + i)
            item.setOnMenuItemClickListener(subMenuProvider)
        }
    }

    override fun canAccessScheduler(): Boolean {
        return true
    }

    override fun performReload() {
        col.sched.deferReset()
        GetCard().runWithHandler(answerCardHandler(false))
    }

    override fun displayAnswerBottomBar() {
        super.displayAnswerBottomBar()
        mOnboarding.onAnswerShown()
        val buttonCount: Int
        buttonCount = try {
            this.buttonCount
        } catch (e: RuntimeException) {
            AnkiDroidApp.sendExceptionReport(e, "AbstractReviewer-showEaseButtons")
            closeReviewer(DeckPicker.RESULT_DB_ERROR, true)
            return
        }

        // Set correct label and background resource for each button
        // Note that it's necessary to set the resource dynamically as the ease2 / ease3 buttons
        // (which libanki expects ease to be 2 and 3) can either be hard, good, or easy - depending on num buttons shown
        val background = getBackgroundColors(this)
        val textColor = getTextColors(this)
        easeButton1!!.setVisibility(View.VISIBLE)
        easeButton1!!.setColor(background[0])
        easeButton4!!.setColor(background[3])
        when (buttonCount) {
            2 -> {
                // Ease 2 is "good"
                easeButton2!!.setup(background[2], textColor[2], R.string.ease_button_good)
                easeButton2!!.requestFocus()
            }
            3 -> {
                // Ease 2 is good
                easeButton2!!.setup(background[2], textColor[2], R.string.ease_button_good)
                // Ease 3 is easy
                easeButton3!!.setup(background[3], textColor[3], R.string.ease_button_easy)
                easeButton2!!.requestFocus()
            }
            else -> {
                // Ease 2 is "hard"
                easeButton2!!.setup(background[1], textColor[1], R.string.ease_button_hard)
                easeButton2!!.requestFocus()
                // Ease 3 is good
                easeButton3!!.setup(background[2], textColor[2], R.string.ease_button_good)
                easeButton4!!.setVisibility(View.VISIBLE)
                easeButton3!!.requestFocus()
            }
        }

        // Show next review time
        if (shouldShowNextReviewTime()) {
            fun nextIvlStr(button: Int) = sched!!.nextIvlStr(this, mCurrentCard!!, button)

            easeButton1!!.nextTime = nextIvlStr(Consts.BUTTON_ONE)
            easeButton2!!.nextTime = nextIvlStr(Consts.BUTTON_TWO)
            if (buttonCount > 2) {
                easeButton3!!.nextTime = nextIvlStr(Consts.BUTTON_THREE)
            }
            if (buttonCount > 3) {
                easeButton4!!.nextTime = nextIvlStr(Consts.BUTTON_FOUR)
            }
        }
    }

    val buttonCount: Int
        get() = sched!!.answerButtons(mCurrentCard!!)

    override fun automaticShowQuestion(action: AutomaticAnswerAction) {
        // explicitly do not call super
        if (easeButton1!!.canPerformClick) {
            action.execute(this)
        }
    }

    override fun restorePreferences(): SharedPreferences {
        val preferences = super.restorePreferences()!!
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

    protected fun updateScreenCounts() {
        if (mCurrentCard == null) return
        super.updateActionBar()
        val actionBar = supportActionBar
        val counts = sched!!.counts(mCurrentCard!!)
        if (actionBar != null) {
            if (mPrefShowETA) {
                mEta = sched!!.eta(counts, false)
                actionBar.setSubtitle(Utils.remainingTime(AnkiDroidApp.getInstance(), (mEta * 60).toLong()))
            }
        }
        mNewCount = SpannableString(counts.new.toString())
        mLrnCount = SpannableString(counts.lrn.toString())
        mRevCount = SpannableString(counts.rev.toString())
        if (mPrefHideDueCount) {
            mRevCount = SpannableString("???")
        }
        when (sched!!.countIdx(mCurrentCard!!)) {
            Counts.Queue.NEW -> mNewCount!!.setSpan(UnderlineSpan(), 0, mNewCount!!.length, 0)
            Counts.Queue.LRN -> mLrnCount!!.setSpan(UnderlineSpan(), 0, mLrnCount!!.length, 0)
            Counts.Queue.REV -> mRevCount!!.setSpan(UnderlineSpan(), 0, mRevCount!!.length, 0)
            else -> Timber.w("Unknown card type %s", sched!!.countIdx(mCurrentCard!!))
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
    }

    override fun displayCardQuestion() {
        // show timer, if activated in the deck's preferences
        answerTimer.setupForCard(mCurrentCard!!)
        delayedHide(100)
        super.displayCardQuestion()
    }

    @VisibleForTesting
    public override fun displayCardAnswer() {
        delayedHide(100)
        super.displayCardAnswer()
    }

    override fun initLayout() {
        super.initLayout()
        if (!mShowRemainingCardCount) {
            mTextBarNew.setVisibility(View.GONE)
            mTextBarLearn.setVisibility(View.GONE)
            mTextBarReview.setVisibility(View.GONE)
        }

        // can't move this into onCreate due to mTopBarLayout
        val mark = mTopBarLayout!!.findViewById<ImageView>(R.id.mark_icon)
        val flag = mTopBarLayout!!.findViewById<ImageView>(R.id.flag_icon)
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
        if (!isFinishing && colIsOpen() && sched != null) {
            update(this)
        }
        saveCollectionInBackground()
    }

    override fun initControls() {
        super.initControls()
        if (mPrefWhiteboard) {
            setWhiteboardVisibility(mShowWhiteboard)
        }
        if (mShowRemainingCardCount) {
            mTextBarNew.visibility = View.VISIBLE
            mTextBarLearn.visibility = View.VISIBLE
            mTextBarReview.visibility = View.VISIBLE
        }
    }

    override fun executeCommand(which: ViewerCommand): Boolean {
        if (isControlBlocked() && which !== ViewerCommand.COMMAND_EXIT) {
            return false
        }
        when (which) {
            ViewerCommand.COMMAND_TOGGLE_FLAG_RED -> {
                toggleFlag(CardMarker.FLAG_RED)
                return true
            }
            ViewerCommand.COMMAND_TOGGLE_FLAG_ORANGE -> {
                toggleFlag(CardMarker.FLAG_ORANGE)
                return true
            }
            ViewerCommand.COMMAND_TOGGLE_FLAG_GREEN -> {
                toggleFlag(CardMarker.FLAG_GREEN)
                return true
            }
            ViewerCommand.COMMAND_TOGGLE_FLAG_BLUE -> {
                toggleFlag(CardMarker.FLAG_BLUE)
                return true
            }
            ViewerCommand.COMMAND_TOGGLE_FLAG_PINK -> {
                toggleFlag(CardMarker.FLAG_PINK)
                return true
            }
            ViewerCommand.COMMAND_TOGGLE_FLAG_TURQUOISE -> {
                toggleFlag(CardMarker.FLAG_TURQUOISE)
                return true
            }
            ViewerCommand.COMMAND_TOGGLE_FLAG_PURPLE -> {
                toggleFlag(CardMarker.FLAG_PURPLE)
                return true
            }
            ViewerCommand.COMMAND_UNSET_FLAG -> {
                onFlag(mCurrentCard, CardMarker.FLAG_NONE)
                return true
            }
            ViewerCommand.COMMAND_MARK -> {
                onMark(mCurrentCard)
                return true
            }
            ViewerCommand.COMMAND_ADD_NOTE -> {
                addNote()
                return true
            }
            else -> return super.executeCommand(which)
        }
    }

    private fun toggleFlag(@FlagDef flag: Int) {
        if (mCurrentCard!!.userFlag() == flag) {
            Timber.i("Toggle flag: unsetting flag")
            onFlag(mCurrentCard, CardMarker.FLAG_NONE)
        } else {
            Timber.i("Toggle flag: Setting flag to %d", flag)
            onFlag(mCurrentCard, flag)
        }
    }

    override fun restoreCollectionPreferences(col: Collection) {
        super.restoreCollectionPreferences(col)
        mShowRemainingCardCount = col.get_config_boolean("dueCounts")
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

    override fun onCardEdited(card: Card?) {
        super.onCardEdited(card)
        if (mPrefWhiteboard && whiteboard != null) {
            whiteboard!!.clear()
        }
        if (!isDisplayingAnswer) {
            // Editing the card may reuse mCurrentCard. If so, the scheduler won't call startTimer() to reset the timer
            // QUESTIONABLE(legacy code): Only perform this if editing the question
            card!!.startTimer()
        }
    }

    protected val mFullScreenHandler: Handler = object : Handler(getDefaultLooper()) {
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
        mPrefWhiteboard = state
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
        val prefs = AnkiDroidApp.getSharedPrefs(a)
        val fullscreenMode = fromPreference(prefs)
        a.window.statusBarColor = getColorFromAttr(a, R.attr.colorPrimaryDark)
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
        val sharedPrefs = AnkiDroidApp.getSharedPrefs(this)
        whiteboard = createInstance(this, true, this)

        // We use the pen color of the selected deck at the time the whiteboard is enabled.
        // This is how all other whiteboard settings are
        val whiteboardPenColor = MetaDB.getWhiteboardPenColor(this, parentDid).fromPreferences(sharedPrefs)
        if (whiteboardPenColor != null) {
            whiteboard!!.penColor = whiteboardPenColor
        }
        whiteboard!!.setOnPaintColorChangeListener(object : OnPaintColorChangeListener {
            override fun onPaintColorChange(color: Int?) {
                MetaDB.storeWhiteboardPenColor(this@Reviewer, parentDid, !isInNightMode(sharedPrefs), color)
            }
        })
        whiteboard!!.setOnTouchListener { v: View, event: MotionEvent? ->
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
            whiteboard!!.handleTouchEvent(event!!)
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

    override fun getCurrentCardId(): Long? {
        return mCurrentCard!!.id
    }

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
        return if (mCurrentCard == null || isControlBlocked()) {
            false
        } else col.db.queryScalar(
            "select 1 from cards where nid = ? and id != ? and queue != " + Consts.QUEUE_TYPE_SUSPENDED + " limit 1",
            mCurrentCard!!.nid, mCurrentCard!!.id
        ) == 1
        // whether there exists a sibling not buried.
    }
    @KotlinCleanup("mCurrentCard handling")
    private fun buryNoteAvailable(): Boolean {
        return if (mCurrentCard == null || isControlBlocked()) {
            false
        } else col.db.queryScalar(
            "select 1 from cards where nid = ? and id != ? and queue >=  " + Consts.QUEUE_TYPE_NEW + " limit 1",
            mCurrentCard!!.nid, mCurrentCard!!.id
        ) == 1
        // Whether there exists a sibling which is neither suspended nor buried
    }

    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    fun hasDrawerSwipeConflicts(): Boolean {
        return mHasDrawerSwipeConflicts
    }

    /**
     * Inner class which implements the submenu for the Suspend button
     */
    internal inner class SuspendProvider(context: Context?) : ActionProvider(context), SubMenuProvider {
        override fun onCreateActionView(): View? {
            return null // Just return null for a simple dropdown menu
        }

        override val subMenu: Int
            get() = R.menu.reviewer_suspend

        override fun hasSubMenu(): Boolean {
            return suspendNoteAvailable()
        }

        override fun onPrepareSubMenu(subMenu: SubMenu) {
            subMenu.clear()
            menuInflater.inflate(this.subMenu, subMenu)
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
    internal inner class BuryProvider(context: Context?) : ActionProvider(context), SubMenuProvider {
        override fun onCreateActionView(): View? {
            return null // Just return null for a simple dropdown menu
        }

        override val subMenu: Int
            get() = R.menu.reviewer_bury

        override fun hasSubMenu(): Boolean {
            return buryNoteAvailable()
        }

        override fun onPrepareSubMenu(subMenu: SubMenu) {
            subMenu.clear()
            menuInflater.inflate(this.subMenu, subMenu)
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

    /**
     * Inner class which implements the submenu for the Schedule button
     */
    internal inner class ScheduleProvider(context: Context?) : ActionProvider(context), SubMenuProvider {
        override fun onCreateActionView(): View? {
            return null // Just return null for a simple dropdown menu
        }

        override fun hasSubMenu(): Boolean {
            return true
        }

        override fun onPrepareSubMenu(subMenu: SubMenu) {
            subMenu.clear()
            menuInflater.inflate(this.subMenu, subMenu)
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

        override val subMenu: Int
            get() = R.menu.reviewer_schedule
    }

    private interface SubMenuProvider : MenuItem.OnMenuItemClickListener {
        @get:MenuRes
        val subMenu: Int
        fun hasSubMenu(): Boolean
    }

    override fun javaScriptFunction(): AnkiDroidJsAPI {
        return ReviewerJavaScriptFunction(this)
    }

    inner class ReviewerJavaScriptFunction(activity: AbstractFlashcardViewer) : AnkiDroidJsAPI(activity) {
        @JavascriptInterface
        override fun ankiGetNewCardCount(): String? {
            return mNewCount.toString()
        }

        @JavascriptInterface
        override fun ankiGetLrnCardCount(): String? {
            return mLrnCount.toString()
        }

        @JavascriptInterface
        override fun ankiGetRevCardCount(): String? {
            return mRevCount.toString()
        }

        @JavascriptInterface
        override fun ankiGetETA(): Int {
            return mEta
        }

        @JavascriptInterface
        override fun ankiGetNextTime1(): String {
            return easeButton1!!.nextTime
        }

        @JavascriptInterface
        override fun ankiGetNextTime2(): String {
            return easeButton2!!.nextTime
        }

        @JavascriptInterface
        override fun ankiGetNextTime3(): String {
            return easeButton3!!.nextTime
        }

        @JavascriptInterface
        override fun ankiGetNextTime4(): String {
            return easeButton4!!.nextTime
        }

        @JavascriptInterface
        override fun ankiSetCardDue(days: Int): Boolean {
            val apiList = getJsApiListMap()!!
            if (!apiList[SET_CARD_DUE]!!) {
                showDeveloperContact(ankiJsErrorCodeDefault)
                return false
            }

            if (days < 1 || days > 9999) {
                showDeveloperContact(ankiJsErrorCodeSetDue)
                return false
            }

            val cardIds = listOf(currentCard!!.id)
            RescheduleCards(cardIds, days).runWithHandler(scheduleCollectionTaskHandler(R.plurals.reschedule_cards_dialog_acknowledge))
            return true
        }
    }

    companion object {
        private const val ADD_NOTE = 12
        private const val REQUEST_AUDIO_PERMISSION = 0
        private const val ANIMATION_DURATION = 200
        private const val TRANSPARENCY = 0.90f
    }
}
