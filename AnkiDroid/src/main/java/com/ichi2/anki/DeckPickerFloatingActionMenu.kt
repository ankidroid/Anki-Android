/*
 * Copyright (c) 2021 Akshay Jadhav <jadhavakshay0701@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki

import android.animation.Animator
import android.content.Context
import android.content.res.ColorStateList
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.ichi2.anki.dialogs.CreateDeckDialog
import com.ichi2.anki.ui.DoubleTapListener
import timber.log.Timber

class DeckPickerFloatingActionMenu(private val context: Context, view: View, private val deckPicker: DeckPicker) {
    private val mFabMain: FloatingActionButton = view.findViewById(R.id.fab_main)
    private val mAddSharedLayout: LinearLayout = view.findViewById(R.id.add_shared_layout)
    private val mAddDeckLayout: LinearLayout = view.findViewById(R.id.add_deck_layout)
    private val mFabBGLayout: View = view.findViewById(R.id.fabBGLayout)
    private val mLinearLayout: LinearLayout = view.findViewById(R.id.deckpicker_view) // Layout deck_picker.xml is attached here
    private val mStudyOptionsFrame: View? = view.findViewById(R.id.studyoptions_fragment)

    // Add Note Drawable Icon
    private val addNoteIcon: Int = R.drawable.ic_add_note
    // Add White Icon
    private val addWhiteIcon: Int = R.drawable.ic_add_white

    var isFABOpen = false

    @Suppress("unused")
    val isFragmented: Boolean
        get() = mStudyOptionsFrame != null

    private fun showFloatingActionMenu() {
        mLinearLayout.alpha = 0.5f
        mStudyOptionsFrame?.let { it.alpha = 0.5f }
        isFABOpen = true
        if (deckPicker.animationEnabled()) {
            // Show with animation
            mAddSharedLayout.visibility = View.VISIBLE
            mAddDeckLayout.visibility = View.VISIBLE
            mFabBGLayout.visibility = View.VISIBLE
            mFabMain.animate().apply {
                // Changes the background color of FAB
                mFabMain.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.material_indigo_700))
                // Rotates FAB to 180 degrees
                rotationBy(90f)
                duration = 50
                // Rise FAB animation
                scaleX(1.2f)
                scaleY(1.2f)
                withEndAction {
                    // At the end the Image is changed to Add Note Icon
                    mFabMain.setImageResource(addNoteIcon)
                    // Shrink back FAB
                    mFabMain.animate().rotation(180f).setDuration(50).scaleX(1f).scaleY(1f)
                        .start()
                }.start()
            }

            mAddSharedLayout.animate().translationY(0f).duration = 30
            mAddDeckLayout.animate().translationY(0f).duration = 50
            mAddDeckLayout.animate().alpha(1f).duration = 50
            mAddSharedLayout.animate().alpha(1f).duration = 30
        } else {
            // Show without animation
            mAddSharedLayout.visibility = View.VISIBLE
            mAddDeckLayout.visibility = View.VISIBLE
            mFabBGLayout.visibility = View.VISIBLE
            mAddSharedLayout.alpha = 1f
            mAddDeckLayout.alpha = 1f
            mAddSharedLayout.translationY = 0f
            mAddDeckLayout.translationY = 0f
        }
    }

    fun closeFloatingActionMenu() {
        mLinearLayout.alpha = 1f
        mStudyOptionsFrame?.let { it.alpha = 1f }
        isFABOpen = false
        mFabBGLayout.visibility = View.GONE
        if (deckPicker.animationEnabled()) {
            // Changes the background color of FAB to default
            mFabMain.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.material_light_blue_700))
            // Close with animation
            mFabMain.animate().apply {
                // Rotates FAB to 180 degrees
                rotation(90f)
                duration = 50
                // Rise FAB animation
                scaleX(1.2f)
                scaleY(1.2f)
                withEndAction {
                    // At the end the image is changed to Add White Icon
                    mFabMain.setImageResource(addWhiteIcon)
                    // Shrink back FAB
                    mFabMain.animate().rotation(90f).setDuration(50).scaleX(1f).scaleY(1f)
                        .start()
                }.start()
            }

            mAddSharedLayout.animate().translationY(200f).duration = 30
            mAddDeckLayout.animate().alpha(0f).duration = 50
            mAddSharedLayout.animate().alpha(0f).duration = 30
            mAddDeckLayout.animate().translationY(400f).setDuration(100).setListener(object : Animator.AnimatorListener {
                override fun onAnimationStart(animator: Animator) {}
                override fun onAnimationEnd(animator: Animator) {
                    if (!isFABOpen) {
                        mAddSharedLayout.visibility = View.GONE
                        mAddDeckLayout.visibility = View.GONE
                    }
                }

                override fun onAnimationCancel(animator: Animator) {}
                override fun onAnimationRepeat(animator: Animator) {}
            })
        } else {
            // Close without animation
            mAddSharedLayout.visibility = View.GONE
            mAddDeckLayout.visibility = View.GONE
        }
    }

    private fun closeFloatingActionMenuWhenChoosingCreateDeck() {
        mLinearLayout.alpha = 1f
        mStudyOptionsFrame?.let { it.alpha = 1f }
        isFABOpen = false
        mFabBGLayout.visibility = View.GONE
        if (animationEnabled()) {
            // Changes the background color of FAB to default
            mFabMain.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.material_light_blue_700))
            // Close with animation
            mFabMain.animate().apply {
                // Rotates FAB to 180 degrees
                rotation(90f)
                duration = 50
                withEndAction {
                    // At the end the image is changed to Add White Icon
                    mFabMain.setImageResource(addWhiteIcon)
                }.start()
            }

            mAddSharedLayout.animate().translationY(200f).duration = 30
            mAddDeckLayout.animate().alpha(0f).duration = 50
            mAddSharedLayout.animate().alpha(0f).duration = 30
            mAddDeckLayout.animate().translationY(400f).setDuration(100).setListener(object : Animator.AnimatorListener {
                override fun onAnimationStart(animator: Animator) {}
                override fun onAnimationEnd(animator: Animator) {
                    if (!isFABOpen) {
                        mAddSharedLayout.visibility = View.GONE
                        mAddDeckLayout.visibility = View.GONE
                    }
                }

                override fun onAnimationCancel(animator: Animator) {}
                override fun onAnimationRepeat(animator: Animator) {}
            })
        } else {
            // Close without animation
            mAddSharedLayout.visibility = View.GONE
            mAddDeckLayout.visibility = View.GONE
        }
    }

    fun showFloatingActionButton() {
        if (!mFabMain.isShown) {
            Timber.i("DeckPicker:: showFloatingActionButton()")
            mFabMain.visibility = View.VISIBLE
        }
    }

    fun hideFloatingActionButton() {
        if (mFabMain.isShown) {
            Timber.i("DeckPicker:: hideFloatingActionButton()")
            mFabMain.visibility = View.GONE
        }
    }

    init {
        val addSharedButton: FloatingActionButton = view.findViewById(R.id.add_shared_action)
        val addDeckButton: FloatingActionButton = view.findViewById(R.id.add_deck_action)
        val addSharedLabel: TextView = view.findViewById(R.id.add_shared_label)
        val addDeckLabel: TextView = view.findViewById(R.id.add_deck_label)
        mFabMain.setOnTouchListener(object : DoubleTapListener(context) {
            override fun onDoubleTap(e: MotionEvent?) {
                addNote()
            }

            override fun onUnconfirmedSingleTap(e: MotionEvent?) {
                // we use an unconfirmed tap as we don't want any visual delay in tapping the +
                // and opening the menu.
                if (!isFABOpen) {
                    showFloatingActionMenu()
                } else {
                    addNote()
                }
            }
        })
        mFabBGLayout.setOnClickListener { closeFloatingActionMenu() }
        val addDeckListener = View.OnClickListener {
            if (isFABOpen) {
                val createDeckDialog = CreateDeckDialog(context, R.string.new_deck, CreateDeckDialog.DeckDialogType.DECK, null)
                createDeckDialog.setOnNewDeckCreated { deckPicker.updateDeckList() }
                createDeckDialog.showDialog()
                closeFloatingActionMenuWhenChoosingCreateDeck()
            }
        }
        addDeckButton.setOnClickListener(addDeckListener)
        addDeckLabel.setOnClickListener(addDeckListener)
        val addSharedListener = View.OnClickListener {
            Timber.d("configureFloatingActionsMenu::addSharedButton::onClickListener - Adding Shared Deck")
            deckPicker.openAnkiWebSharedDecks()
        }
        addSharedButton.setOnClickListener(addSharedListener)
        addSharedLabel.setOnClickListener(addSharedListener)
    }

    /**
     * Closes the FAB menu and opens the [NoteEditor]
     * @see DeckPicker.addNote
     */
    private fun addNote() {
        deckPicker.addNote()
    }
}
