/***************************************************************************************
 * Copyright (c) 2011 Kostas Spyropoulos <inigo.aldana@gmail.com>                       *
 * Copyright (c) 2013 Jolta Technologies                                                *
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
package com.ichi2.anki

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import android.widget.TextView
import androidx.annotation.CheckResult
import androidx.annotation.VisibleForTesting
import androidx.appcompat.view.menu.MenuBuilder
import androidx.core.content.ContextCompat
import androidx.core.view.MenuItemCompat
import com.google.android.material.snackbar.Snackbar
import com.ichi2.anki.cardviewer.Gesture
import com.ichi2.anki.cardviewer.PreviewLayout
import com.ichi2.anki.cardviewer.ViewerCommand
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.libanki.Collection
import com.ichi2.libanki.utils.TimeManager
import com.ichi2.themes.Themes
import com.ichi2.utils.increaseHorizontalPaddingOfOverflowMenuIcons
import com.ichi2.utils.tintOverflowMenuIcons
import timber.log.Timber

/**
 * The previewer intent must supply an array of cards to show and the index in the list from where
 * to begin showing them. Special rules are applied if the list size is 1 (i.e., no scrolling
 * buttons will be shown).
 */

class Previewer : AbstractFlashcardViewer() {
    private lateinit var mCardList: LongArray
    private var mIndex = 0
    private var mShowingAnswer = false
    private lateinit var mProgressSeekBar: SeekBar
    private lateinit var mProgressText: TextView

    /** Communication with Browser  */
    private var mReloadRequired = false
    private var mNoteChanged = false
    private var previewLayout: PreviewLayout? = null

    /**  Whiteboard */
    private var mShowWhiteboard = true
    var prefWhiteboard = false
    private var toggleStylus = false
    private var mHasDrawerSwipeConflicts = false

    @get:CheckResult
    @get:VisibleForTesting(otherwise = VisibleForTesting.NONE)
    var whiteboard: Whiteboard? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        if (showedActivityFailedScreen(savedInstanceState)) {
            return
        }
        super.onCreate(savedInstanceState)
        mCardList = intent.getLongArrayExtra("cardList")!!
        mIndex = intent.getIntExtra("index", -1)
        if (savedInstanceState != null) {
            mIndex = savedInstanceState.getInt("index", mIndex)
            mShowingAnswer = savedInstanceState.getBoolean("showingAnswer", mShowingAnswer)
            mReloadRequired = savedInstanceState.getBoolean("reloadRequired")
            mNoteChanged = savedInstanceState.getBoolean("noteChanged")
        }
        if (mCardList.isEmpty() || mIndex < 0 || mIndex > mCardList.size - 1) {
            Timber.e("Previewer started with empty card list or invalid index")
            finishWithoutAnimation()
            return
        }
        showBackIcon()
        // Ensure navigation drawer can't be opened. Various actions in the drawer cause crashes.
        disableDrawerSwipe()
        startLoadingCollection()
        initPreviewProgress()
    }

    private fun initPreviewProgress() {
        mProgressSeekBar = findViewById(R.id.preview_progress_seek_bar)
        mProgressText = findViewById(R.id.preview_progress_text)
        val progressLayout = findViewById<LinearLayout>(R.id.preview_progress_layout)

        // Show layout only when the cardList is bigger than 1
        if (mCardList.size > 1) {
            progressLayout.visibility = View.VISIBLE
            mProgressSeekBar.max = mCardList.size - 1
            setSeekBarListener()
            updateProgress()
        }
    }

    private fun setSeekBarListener() {
        mProgressSeekBar.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    mIndex = progress
                    updateProgress()
                    currentCard = col.getCard(mCardList[mIndex])
                    displayCardQuestion()
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {
                // Mandatory override, but unused
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                if (mIndex >= 0 && mIndex < mCardList.size) {
                    currentCard = col.getCard(mCardList[mIndex])
                    displayCardQuestion()
                }
            }
        })
    }

    override fun onCollectionLoaded(col: Collection) {
        super.onCollectionLoaded(col)
        currentCard = col.getCard(mCardList[mIndex])
        displayCardQuestion()
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
        disableDrawerSwipeOnConflicts()

        if (mShowingAnswer) {
            displayCardAnswer()
        }
        showBackIcon()
    }

    private fun setWhiteboardEnabledState(state: Boolean) {
        prefWhiteboard = state
        MetaDB.storeWhiteboardState(this, parentDid, state)
        if (state && whiteboard == null) {
            createWhiteboard()
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

    private fun createWhiteboard() {
        whiteboard = Whiteboard.createInstance(this, true, this)

        // We use the pen color of the selected deck at the time the whiteboard is enabled.
        // This is how all other whiteboard settings are
        val whiteboardPenColor = MetaDB.getWhiteboardPenColor(this, parentDid).fromPreferences()
        if (whiteboardPenColor != null) {
            whiteboard!!.penColor = whiteboardPenColor
        }
        whiteboard!!.setOnPaintColorChangeListener(object : Whiteboard.OnPaintColorChangeListener {
            override fun onPaintColorChange(color: Int?) {
                MetaDB.storeWhiteboardPenColor(this@Previewer, parentDid, !Themes.currentTheme.isNightMode, color)
            }
        })
        whiteboard!!.setOnTouchListener { v: View, event: MotionEvent? ->
            if (event == null) return@setOnTouchListener false
            // If the whiteboard is currently drawing, and triggers the system UI to show, we want to continue drawing.
            if (!whiteboard!!.isCurrentlyDrawing && !mShowWhiteboard) {
                // Bypass whiteboard listener when it's hidden
                v.performClick()
                return@setOnTouchListener gestureDetector!!.onTouchEvent(event)
            }
            whiteboard!!.handleTouchEvent(event)
        }
    }

    private fun disableDrawerSwipeOnConflicts() {
        if (mGestureProcessor.isBound(Gesture.SWIPE_UP, Gesture.SWIPE_DOWN, Gesture.SWIPE_RIGHT)) {
            mHasDrawerSwipeConflicts = true
            super.disableDrawerSwipe()
        }
    }

    /** Given a new collection of card Ids, find the 'best' valid card given the current collection
     * We define the best as searching to the left, then searching to the right of the current element
     * This occurs as many cards can be deleted when editing a note (from the Card Template Editor)  */
    private fun getNextIndex(newCardList: List<Long>): Int {
        val validIndices = HashSet(newCardList)
        for (i in mIndex downTo 0) {
            if (validIndices.contains(mCardList[i])) {
                return newCardList.indexOf(mCardList[i])
            }
        }
        for (i in mIndex + 1 until validIndices.size) {
            if (validIndices.contains(mCardList[i])) {
                return newCardList.indexOf(mCardList[i])
            }
        }
        throw IllegalStateException("newCardList was empty")
    }

    override fun initLayout() {
        super.initLayout()
        topBarLayout!!.visibility = View.GONE
        findViewById<View>(R.id.answer_options_layout).visibility = View.GONE
        findViewById<View>(R.id.bottom_area_layout).visibility = View.VISIBLE
        previewLayout = PreviewLayout.createAndDisplay(this, mToggleAnswerHandler)
        previewLayout!!.setOnNextCard { changePreviewedCard(true) }
        previewLayout!!.setOnPreviousCard { changePreviewedCard(false) }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_edit) {
            if (prefWhiteboard && whiteboard != null) {
                whiteboard!!.clear()
            }
            editCard()
            return true
        }
        if (item.itemId == R.id.action_toggle_whiteboard) {
            toggleWhiteboard()
            return true
        }
        if (item.itemId == R.id.action_clear_whiteboard) {
            Timber.i("Reviewer:: Clear whiteboard button pressed")
            clearWhiteboard()
        }
        if (item.itemId == R.id.action_save_whiteboard) {
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
        if (item.itemId == R.id.action_toggle_stylus) { // toggle stylus mode
            Timber.i("Reviewer:: Stylus set to %b", !toggleStylus)
            toggleStylus = !toggleStylus
            whiteboard!!.toggleStylus = toggleStylus
            MetaDB.storeWhiteboardStylusState(this, parentDid, toggleStylus)
            invalidateOptionsMenu()
        }

        return super.onOptionsItemSelected(item)
    }

    public override fun clearWhiteboard() {
        if (whiteboard != null) {
            whiteboard!!.clear()
        }
    }

    override fun onBackPressed() {
        setResult(RESULT_OK, resultIntent)
        super.onBackPressed()
    }

    override fun onNavigationPressed() {
        setResult(RESULT_OK, resultIntent)
        super.onNavigationPressed()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.previewer, menu)

        displayIcons(menu)
        val whiteboardEditItem = menu.findItem(R.id.action_edit)
        MenuItemCompat.setIconTintList(
            whiteboardEditItem,
            ContextCompat.getColorStateList(
                this,
                R.color.white
            )
        )
        val toggleWhiteboardIcon = menu.findItem(R.id.action_toggle_whiteboard)
        val toggleWhiteboardAlphaIcon = ContextCompat.getDrawable(this, R.drawable.ic_gesture_white)!!.mutate()
        val toggleStylusIcon = menu.findItem(R.id.action_toggle_stylus)
        if (prefWhiteboard) {
            // Configure the whiteboard related items in the action bar
            toggleWhiteboardIcon.setTitle(R.string.disable_whiteboard)
            toggleWhiteboardAlphaIcon.alpha = Themes.ALPHA_ICON_ENABLED_LIGHT
            toggleWhiteboardIcon.icon = toggleWhiteboardAlphaIcon

            // Always allow "Disable Whiteboard", even if "Enable Whiteboard" is disabled
            toggleWhiteboardIcon.isVisible = true
            val whiteboardIcon = ContextCompat.getDrawable(this, R.drawable.ic_gesture_white)!!.mutate()
            val stylusIcon = ContextCompat.getDrawable(this, R.drawable.ic_gesture_stylus)!!.mutate()

            toggleStylusIcon.isVisible = true
            menu.findItem(R.id.action_clear_whiteboard).isVisible = true

            whiteboardIcon.alpha = Themes.ALPHA_ICON_ENABLED_LIGHT
            if (toggleStylus) {
                toggleStylusIcon.setTitle(R.string.disable_stylus)
                stylusIcon.alpha = Themes.ALPHA_ICON_ENABLED_LIGHT
            } else {
                toggleStylusIcon.setTitle(R.string.enable_stylus)
                stylusIcon.alpha = Themes.ALPHA_ICON_DISABLED_LIGHT
            }
            toggleStylusIcon.icon = stylusIcon
        } else {
            toggleWhiteboardIcon.setTitle(R.string.enable_whiteboard)
            toggleWhiteboardAlphaIcon.alpha = Themes.ALPHA_ICON_DISABLED_LIGHT
            toggleWhiteboardIcon.icon = toggleWhiteboardAlphaIcon
        }

        increaseHorizontalPaddingOfOverflowMenuIcons(menu)
        tintOverflowMenuIcons(menu)

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

    public override fun toggleWhiteboard() {
        prefWhiteboard = !prefWhiteboard
        Timber.i("Previewer:: Whiteboard enabled state set to %b", prefWhiteboard)
        // Even though the visibility is now stored in its own setting, we want it to be dependent
        // on the enabled status
        setWhiteboardEnabledState(prefWhiteboard)
        setWhiteboardVisibility(prefWhiteboard)
        invalidateOptionsMenu()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putLongArray("cardList", mCardList)
        outState.putInt("index", mIndex)
        outState.putBoolean("showingAnswer", mShowingAnswer)
        outState.putBoolean("reloadRequired", mReloadRequired)
        outState.putBoolean("noteChanged", mNoteChanged)
        super.onSaveInstanceState(outState)
    }

    override fun displayCardQuestion() {
        super.displayCardQuestion()
        mShowingAnswer = false
        updateButtonsState()
    }

    // Called via mFlipCardListener in parent class when answer button pressed
    @VisibleForTesting(otherwise = VisibleForTesting.PROTECTED)
    override fun displayCardAnswer() {
        super.displayCardAnswer()
        mShowingAnswer = true
        updateButtonsState()
    }

    override fun hideEaseButtons() {
        /* do nothing */
    }

    override fun displayAnswerBottomBar() {
        /* do nothing */
    }

    override fun executeCommand(which: ViewerCommand, fromGesture: Gesture?): Boolean {
        /* do nothing */
        return false
    }

    override fun performReload() {
        mReloadRequired = true
        val newCardList = col.filterToValidCards(mCardList)
        if (newCardList.isEmpty()) {
            finishWithoutAnimation()
            return
        }
        mIndex = getNextIndex(newCardList)
        mCardList = newCardList.toLongArray()
        currentCard = col.getCard(mCardList[mIndex])
        displayCardQuestion()
    }

    override fun onEditedNoteChanged() {
        super.onEditedNoteChanged()
        mNoteChanged = true
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal fun changePreviewedCard(nextCard: Boolean) {
        mIndex = if (nextCard) mIndex + 1 else mIndex - 1
        currentCard = col.getCard(mCardList[mIndex])
        clearWhiteboard()
        displayCardQuestion()
        updateProgress()
    }

    private val mToggleAnswerHandler = View.OnClickListener {
        if (mShowingAnswer) {
            displayCardQuestion()
        } else {
            displayCardAnswer()
        }
    }

    private fun updateButtonsState() {
        previewLayout!!.setShowingAnswer(mShowingAnswer)

        // If we are in single-card mode, we show the "Show Answer" button on the question side
        // and hide navigation buttons.
        if (mCardList.size == 1) {
            previewLayout!!.hideNavigationButtons()
            return
        }
        previewLayout!!.setPrevButtonEnabled(mIndex > 0)
        previewLayout!!.setNextButtonEnabled(mIndex < mCardList.size - 1)
    }

    private fun updateProgress() {
        if (mProgressSeekBar.progress != mIndex) {
            mProgressSeekBar.progress = mIndex
        }
        val progress = getString(R.string.preview_progress_bar_text, mIndex + 1, mCardList.size)
        mProgressText.text = progress
    }

    private val resultIntent: Intent
        get() {
            val intent = Intent()
            intent.putExtra("reloadRequired", mReloadRequired)
            intent.putExtra("noteChanged", mNoteChanged)
            return intent
        }

    companion object {
        @CheckResult
        fun getPreviewIntent(context: Context?, index: Int, cardList: LongArray?): Intent {
            val intent = Intent(context, Previewer::class.java)
            intent.putExtra("index", index)
            intent.putExtra("cardList", cardList)
            return intent
        }
    }
}
