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
import com.google.android.material.color.MaterialColors
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
    private val addNoteLabel: TextView = view.findViewById(R.id.add_note_label)

    // Colors values obtained from attributes
    private val fabNormalColor = MaterialColors.getColor(mFabMain, R.attr.fab_normal)
    private val fabPressedColor = MaterialColors.getColor(mFabMain, R.attr.fab_pressed)

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
            addNoteLabel.visibility = View.VISIBLE
            mFabMain.animate().apply {
                // Changes the background color of FAB
                mFabMain.backgroundTintList = ColorStateList.valueOf(fabPressedColor)
                // Rotates FAB to 90 degrees
                rotationBy(90f)
                duration = 30
                // Rise FAB animation
                scaleX(1.3f)
                scaleY(1.3f)
                withEndAction {
                    // At the end the Image is changed to Add Note Icon
                    mFabMain.setImageResource(addNoteIcon)
                    // Shrink back FAB
                    mFabMain.animate().rotation(180f).setDuration(70).scaleX(1f).scaleY(1f)
                        .start()
                }.start()
            }

            addNoteLabel.animate().translationX(0f).duration = 70
            mAddSharedLayout.animate().translationY(0f).duration = 70
            mAddDeckLayout.animate().translationY(0f).duration = 100
            addNoteLabel.animate().alpha(1f).duration = 70
            mAddSharedLayout.animate().alpha(1f).duration = 70
            mAddDeckLayout.animate().alpha(1f).duration = 100
        } else {
            // Show without animation
            mAddSharedLayout.visibility = View.VISIBLE
            mAddDeckLayout.visibility = View.VISIBLE
            mFabBGLayout.visibility = View.VISIBLE
            addNoteLabel.visibility = View.VISIBLE
            mAddSharedLayout.alpha = 1f
            mAddDeckLayout.alpha = 1f
            addNoteLabel.alpha = 1f
            mAddSharedLayout.translationY = 0f
            mAddDeckLayout.translationY = 0f
            addNoteLabel.translationY = 0f
        }
    }

    /**
     * This function takes a parameter which decides if we want to apply the rise and shrink animation
     * for FAB or not.
     *
     * Case 1: When the FAB is already opened and we close it by pressing the back button then we need to show
     * the rise and shrink animation and get back to the FAB with `+` icon.
     *
     * Case 2: When the user opens the side navigation drawer (without touching the FAB). In that case we don't
     * want to show any type of rise and shrink animation for the FAB so we put the value `false` for the parameter.
     */
    fun closeFloatingActionMenu(applyRiseAndShrinkAnimation: Boolean) {
        if (applyRiseAndShrinkAnimation) {
            mLinearLayout.alpha = 1f
            mStudyOptionsFrame?.let { it.alpha = 1f }
            isFABOpen = false
            mFabBGLayout.visibility = View.GONE
            addNoteLabel.visibility = View.GONE
            if (deckPicker.animationEnabled()) {
                // Changes the background color of FAB to default
                mFabMain.backgroundTintList = ColorStateList.valueOf(fabNormalColor)
                // Close with animation
                mFabMain.animate().apply {
                    // Rotates FAB to 180 degrees
                    rotation(-90f)
                    duration = 40
                    // Rise FAB animation
                    scaleX(1.3f)
                    scaleY(1.3f)
                    withEndAction {
                        // At the end the image is changed to Add White Icon
                        mFabMain.setImageResource(addWhiteIcon)
                        // Shrink back FAB
                        mFabMain.animate().rotation(-90f).setDuration(60).scaleX(1f).scaleY(1f)
                            .start()
                    }.start()
                }

                mAddSharedLayout.animate().alpha(0f).duration = 50
                addNoteLabel.animate().alpha(0f).duration = 70
                mAddDeckLayout.animate().alpha(0f).duration = 100
                mAddSharedLayout.animate().translationY(300f).duration = 50
                addNoteLabel.animate().translationX(180f).duration = 70
                mAddDeckLayout.animate().translationY(400f).setDuration(100)
                    .setListener(object : Animator.AnimatorListener {
                        override fun onAnimationStart(animator: Animator) {}
                        override fun onAnimationEnd(animator: Animator) {
                            if (!isFABOpen) {
                                mAddSharedLayout.visibility = View.GONE
                                mAddDeckLayout.visibility = View.GONE
                                addNoteLabel.visibility = View.GONE
                            }
                        }

                        override fun onAnimationCancel(animator: Animator) {}
                        override fun onAnimationRepeat(animator: Animator) {}
                    })
            } else {
                // Close without animation
                mAddSharedLayout.visibility = View.GONE
                mAddDeckLayout.visibility = View.GONE
                addNoteLabel.visibility = View.GONE
            }
        } else {
            mLinearLayout.alpha = 1f
            mStudyOptionsFrame?.let { it.alpha = 1f }
            isFABOpen = false
            mFabBGLayout.visibility = View.GONE
            addNoteLabel.visibility = View.GONE
            if (animationEnabled()) {
                // Changes the background color of FAB to default
                mFabMain.backgroundTintList = ColorStateList.valueOf(fabNormalColor)
                // Close with animation
                mFabMain.animate().apply {
                    // Rotates FAB to 90 degrees
                    rotation(-90f)
                    duration = 90
                    withEndAction {
                        // At the end the image is changed to Add White Icon
                        mFabMain.setImageResource(addWhiteIcon)
                    }.start()
                }

                mAddSharedLayout.animate().alpha(0f).duration = 70
                mAddDeckLayout.animate().alpha(0f).duration = 50
                addNoteLabel.animate().alpha(0f).duration = 50
                addNoteLabel.animate().translationX(180f).duration = 70
                mAddSharedLayout.animate().translationY(400f).duration = 50
                mAddDeckLayout.animate().translationY(600f).setDuration(100)
                    .setListener(object : Animator.AnimatorListener {
                        override fun onAnimationStart(animator: Animator) {}
                        override fun onAnimationEnd(animator: Animator) {
                            if (!isFABOpen) {
                                mAddSharedLayout.visibility = View.GONE
                                mAddDeckLayout.visibility = View.GONE
                                addNoteLabel.visibility = View.GONE
                            }
                        }

                        override fun onAnimationCancel(animator: Animator) {}
                        override fun onAnimationRepeat(animator: Animator) {}
                    })
            } else {
                // Close without animation
                mAddSharedLayout.visibility = View.GONE
                mAddDeckLayout.visibility = View.GONE
                addNoteLabel.visibility = View.GONE
            }
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
        val addNote: TextView = view.findViewById(R.id.add_note_label)
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
        mFabBGLayout.setOnClickListener { closeFloatingActionMenu(applyRiseAndShrinkAnimation = true) }
        val addDeckListener = View.OnClickListener {
            if (isFABOpen) {
                val createDeckDialog = CreateDeckDialog(context, R.string.new_deck, CreateDeckDialog.DeckDialogType.DECK, null)
                createDeckDialog.setOnNewDeckCreated { deckPicker.updateDeckList() }
                createDeckDialog.showDialog()
                closeFloatingActionMenu(applyRiseAndShrinkAnimation = false)
            }
        }
        addDeckButton.setOnClickListener(addDeckListener)
        addDeckLabel.setOnClickListener(addDeckListener)
        val addSharedListener = View.OnClickListener {
            Timber.d("configureFloatingActionsMenu::addSharedButton::onClickListener - Adding Shared Deck")
            deckPicker.openAnkiWebSharedDecks()
            mFabMain.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.material_light_blue_700))
            mFabMain.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_add_white))
        }
        addSharedButton.setOnClickListener(addSharedListener)
        addSharedLabel.setOnClickListener(addSharedListener)
        val addNoteLabelListener = View.OnClickListener {
            Timber.d("configureFloatingActionsMenu::addNoteLabel::onClickListener - Adding Note")
            addNote()
        }
        addNote.setOnClickListener(addNoteLabelListener)
    }

    /**
     * Closes the FAB menu and opens the [NoteEditor]
     * @see DeckPicker.addNote
     */
    private fun addNote() {
        deckPicker.addNote()
    }
}
