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

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import android.widget.TextView
import androidx.annotation.CheckResult
import androidx.annotation.VisibleForTesting
import com.ichi2.anki.cardviewer.Gesture
import com.ichi2.anki.cardviewer.PreviewLayout
import com.ichi2.anki.cardviewer.ViewerCommand
import com.ichi2.annotations.NeedsTest
import com.ichi2.libanki.Collection
import com.ichi2.utils.performClickIfEnabled
import timber.log.Timber

/**
 * The previewer intent must supply an array of cards to show and the index in the list from where
 * to begin showing them. Special rules are applied if the list size is 1 (i.e., no scrolling
 * buttons will be shown).
 */

class Previewer : AbstractFlashcardViewer() {
    private lateinit var cardList: LongArray
    private var index = 0
    private var showingAnswer = false
    private lateinit var progressSeekBar: SeekBar
    private lateinit var progressText: TextView

    /** Communication with Browser  */
    private var reloadRequired = false
    private var noteChanged = false
    private var previewLayout: PreviewLayout? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        if (showedActivityFailedScreen(savedInstanceState)) {
            return
        }
        super.onCreate(savedInstanceState)
        cardList = requireNotNull(intent.getLongArrayExtra("cardList")) { "'cardList' required" }
        index = intent.getIntExtra("index", -1)
        if (savedInstanceState != null) {
            index = savedInstanceState.getInt("index", index)
            showingAnswer = savedInstanceState.getBoolean("showingAnswer", showingAnswer)
            reloadRequired = savedInstanceState.getBoolean("reloadRequired")
            noteChanged = savedInstanceState.getBoolean("noteChanged")
        }
        if (cardList.isEmpty() || index < 0 || index > cardList.size - 1) {
            Timber.e("Previewer started with empty card list or invalid index")
            finish()
            return
        }
        showBackIcon()
        // Ensure navigation drawer can't be opened. Various actions in the drawer cause crashes.
        disableDrawerSwipe()
        startLoadingCollection()
        initPreviewProgress()
        setOkResult()
    }

    private fun initPreviewProgress() {
        progressSeekBar = findViewById(R.id.preview_progress_seek_bar)
        progressText = findViewById(R.id.preview_progress_text)
        val progressLayout = findViewById<LinearLayout>(R.id.preview_progress_layout)

        // Show layout only when the cardList is bigger than 1
        if (cardList.size > 1) {
            progressLayout.visibility = View.VISIBLE
            progressSeekBar.max = cardList.size - 1
            setSeekBarListener()
            updateProgress()
        }
    }

    private fun setSeekBarListener() {
        progressSeekBar.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    index = progress
                    updateProgress()
                    currentCard = getColUnsafe.getCard(cardList[index])
                    displayCardQuestion()
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {
                // Mandatory override, but unused
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                if (index >= 0 && index < cardList.size) {
                    currentCard = getColUnsafe.getCard(cardList[index])
                    displayCardQuestion()
                }
            }
        })
    }

    override fun onCollectionLoaded(col: Collection) {
        super.onCollectionLoaded(col)
        currentCard = col.getCard(cardList[index])
        displayCardQuestion()
        if (showingAnswer) {
            displayCardAnswer()
        }
        showBackIcon()
    }

    /** Given a new collection of card Ids, find the 'best' valid card given the current collection
     * We define the best as searching to the left, then searching to the right of the current element
     * This occurs as many cards can be deleted when editing a note (from the Card Template Editor)  */
    private fun getNextIndex(newCardList: List<Long>): Int {
        val validIndices = HashSet(newCardList)
        for (i in index downTo 0) {
            if (validIndices.contains(cardList[i])) {
                return newCardList.indexOf(cardList[i])
            }
        }
        for (i in index + 1 until validIndices.size) {
            if (validIndices.contains(cardList[i])) {
                return newCardList.indexOf(cardList[i])
            }
        }
        throw IllegalStateException("newCardList was empty")
    }

    override fun initLayout() {
        super.initLayout()
        topBarLayout!!.visibility = View.GONE
        findViewById<View>(R.id.answer_options_layout).visibility = View.GONE
        findViewById<View>(R.id.bottom_area_layout).visibility = View.VISIBLE
        previewLayout = PreviewLayout.createAndDisplay(this, toggleAnswerHandler)
        previewLayout!!.setOnNextCard { changePreviewedCard(true) }
        previewLayout!!.setOnPreviousCard { changePreviewedCard(false) }
    }

    @NeedsTest("handling: when valid and invalid")
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                Timber.i("Left pressed: previous card")
                previewLayout?.prevCard?.performClickIfEnabled()
                return true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                Timber.i("Right pressed: next card")
                previewLayout?.nextCard?.performClickIfEnabled()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_edit) {
            editCard()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onNavigationPressed() {
        setOkResult()
        super.onNavigationPressed()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.previewer, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putLongArray("cardList", cardList)
        outState.putInt("index", index)
        outState.putBoolean("showingAnswer", showingAnswer)
        outState.putBoolean("reloadRequired", reloadRequired)
        outState.putBoolean("noteChanged", noteChanged)
        super.onSaveInstanceState(outState)
    }

    override fun displayCardQuestion() {
        super.displayCardQuestion()
        showingAnswer = false
        updateButtonsState()
    }

    // Called via mFlipCardListener in parent class when answer button pressed
    @VisibleForTesting(otherwise = VisibleForTesting.PROTECTED)
    override fun displayCardAnswer() {
        super.displayCardAnswer()
        showingAnswer = true
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
        reloadRequired = true
        setOkResult()
        val newCardList = getColUnsafe.filterToValidCards(cardList)
        if (newCardList.isEmpty()) {
            finish()
            return
        }
        index = getNextIndex(newCardList)
        cardList = newCardList.toLongArray()
        currentCard = getColUnsafe.getCard(cardList[index])
        displayCardQuestion()
    }

    override fun onEditedNoteChanged() {
        super.onEditedNoteChanged()
        noteChanged = true
        setOkResult()
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal fun changePreviewedCard(nextCard: Boolean) {
        index = if (nextCard) index + 1 else index - 1
        currentCard = getColUnsafe.getCard(cardList[index])
        displayCardQuestion()
        updateProgress()
    }

    private val toggleAnswerHandler = View.OnClickListener {
        if (showingAnswer) {
            displayCardQuestion()
        } else {
            displayCardAnswer()
        }
    }

    private fun updateButtonsState() {
        previewLayout!!.setShowingAnswer(showingAnswer)

        // If we are in single-card mode, we show the "Show Answer" button on the question side
        // and hide navigation buttons.
        if (cardList.size == 1) {
            previewLayout!!.hideNavigationButtons()
            return
        }
        previewLayout!!.setPrevButtonEnabled(index > 0)
        previewLayout!!.setNextButtonEnabled(index < cardList.size - 1)
    }

    private fun updateProgress() {
        if (progressSeekBar.progress != index) {
            progressSeekBar.progress = index
        }
        val progress = getString(R.string.preview_progress_bar_text, index + 1, cardList.size)
        progressText.text = progress
    }

    private fun setOkResult() {
        setResult(
            RESULT_OK,
            Intent().apply {
                putExtra("reloadRequired", reloadRequired)
                putExtra("noteChanged", noteChanged)
            }
        )
    }

    companion object {
        @CheckResult
        fun PreviewDestination.toIntent(context: Context) =
            Intent(context, Previewer::class.java).apply {
                putExtra("index", index)
                putExtra("cardList", cardList)
            }
    }
}

class PreviewDestination(val index: Int, val cardList: LongArray)
