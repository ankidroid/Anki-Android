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
import android.provider.Settings
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.color.MaterialColors
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.ichi2.anki.dialogs.CreateDeckDialog
import com.ichi2.anki.ui.DoubleTapListener
import timber.log.Timber

class DeckPickerFloatingActionMenu(
    private val context: Context,
    view: View,
    private val deckPicker: DeckPicker
) {
    private val fabMain: FloatingActionButton = view.findViewById(R.id.fab_main)
    private val addSharedLayout: LinearLayout = view.findViewById(R.id.add_shared_layout)
    private val addDeckLayout: LinearLayout = view.findViewById(R.id.add_deck_layout)
    private val addFilteredDeckLayout: LinearLayout = view.findViewById(R.id.add_filtered_deck_layout)
    private val fabBGLayout: View = view.findViewById(R.id.fabBGLayout)
    private val linearLayout: LinearLayout =
        view.findViewById(R.id.deckpicker_view) // Layout deck_picker.xml is attached here
    private val studyOptionsFrame: View? = view.findViewById(R.id.studyoptions_fragment)
    private val addNoteLabel: TextView = view.findViewById(R.id.add_note_label)

    // Colors values obtained from attributes
    private val fabNormalColor = MaterialColors.getColor(fabMain, R.attr.fab_normal)
    private val fabPressedColor = MaterialColors.getColor(fabMain, R.attr.fab_pressed)

    // Add Note Drawable Icon
    private val addNoteIcon: Int = R.drawable.ic_add_note

    // Add White Icon
    private val addWhiteIcon: Int = R.drawable.ic_add_white

    var isFABOpen = false

    @Suppress("unused")
    val isFragmented: Boolean
        get() = studyOptionsFrame != null

    private fun showFloatingActionMenu() {
        deckPicker.activeSnackBar?.dismiss()
        linearLayout.alpha = 0.5f
        studyOptionsFrame?.let { it.alpha = 0.5f }
        isFABOpen = true
        if (deckPicker.animationEnabled()) {
            // Show with animation
            addSharedLayout.visibility = View.VISIBLE
            addDeckLayout.visibility = View.VISIBLE
            addFilteredDeckLayout.visibility = View.VISIBLE
            fabBGLayout.visibility = View.VISIBLE
            addNoteLabel.visibility = View.VISIBLE
            fabMain.animate().apply {
                /**
                 * If system animations are true changes the FAB color otherwise it remains the same
                 */
                if (areSystemAnimationsEnabled()) {
                    fabMain.backgroundTintList = ColorStateList.valueOf(fabPressedColor)
                } else {
                    // Changes the background color of FAB
                    fabMain.backgroundTintList = ColorStateList.valueOf(fabNormalColor)
                }
                duration = 90
                // Rise FAB animation
                scaleX(1.3f)
                scaleY(1.3f)
                withEndAction {
                    // At the end the Image is changed to Add Note Icon
                    fabMain.setImageResource(addNoteIcon)
                    // Shrink back FAB
                    fabMain.animate().setDuration(70).scaleX(1f).scaleY(1f).start()
                }.start()
            }

            addNoteLabel.animate().translationX(0f).duration = 70
            addSharedLayout.animate().translationY(0f).duration = 100
            addDeckLayout.animate().translationY(0f).duration = 70
            addFilteredDeckLayout.animate().translationY(0f).duration = 100
            addNoteLabel.animate().alpha(1f).duration = 70
            addSharedLayout.animate().alpha(1f).duration = 100
            addDeckLayout.animate().alpha(1f).duration = 70
            addFilteredDeckLayout.animate().alpha(1f).duration = 100
        } else {
            // Show without animation
            addSharedLayout.visibility = View.VISIBLE
            addDeckLayout.visibility = View.VISIBLE
            addFilteredDeckLayout.visibility = View.VISIBLE
            fabBGLayout.visibility = View.VISIBLE
            addNoteLabel.visibility = View.VISIBLE
            addSharedLayout.alpha = 1f
            addDeckLayout.alpha = 1f
            addFilteredDeckLayout.alpha = 1f
            addNoteLabel.alpha = 1f
            addSharedLayout.translationY = 0f
            addDeckLayout.translationY = 0f
            addFilteredDeckLayout.translationY = 0f
            addNoteLabel.translationX = 0f

            // During without animation maintain the original color of FAB
            fabMain.apply {
                backgroundTintList = ColorStateList.valueOf(fabNormalColor)
                setImageResource(addNoteIcon)
            }
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
            linearLayout.alpha = 1f
            studyOptionsFrame?.let { it.alpha = 1f }
            isFABOpen = false
            fabBGLayout.visibility = View.GONE
            addNoteLabel.visibility = View.GONE
            if (deckPicker.animationEnabled()) {
                // Changes the background color of FAB to default
                fabMain.backgroundTintList = ColorStateList.valueOf(fabNormalColor)
                // Close with animation
                fabMain.animate().apply {
                    duration = 90
                    // Rise FAB animation
                    scaleX(1.3f)
                    scaleY(1.3f)
                    withEndAction {
                        // At the end the image is changed to Add White Icon
                        fabMain.setImageResource(addWhiteIcon)
                        // Shrink back FAB
                        fabMain.animate().setDuration(60).scaleX(1f).scaleY(1f)
                            .start()
                    }.start()
                }

                addSharedLayout.animate().alpha(0f).duration = 50
                addNoteLabel.animate().alpha(0f).duration = 70
                addDeckLayout.animate().alpha(0f).duration = 100
                addFilteredDeckLayout.animate().alpha(0f).duration = 100
                addSharedLayout.animate().translationY(400f).duration = 100
                addNoteLabel.animate().translationX(180f).duration = 70
                addDeckLayout.animate().translationY(300f).setDuration(50)
                    .setListener(object : Animator.AnimatorListener {
                        override fun onAnimationStart(animator: Animator) {}
                        override fun onAnimationEnd(animator: Animator) {
                            if (!isFABOpen) {
                                addSharedLayout.visibility = View.GONE
                                addDeckLayout.visibility = View.GONE
                                addFilteredDeckLayout.visibility = View.GONE
                                addNoteLabel.visibility = View.GONE
                            }
                        }

                        override fun onAnimationCancel(animator: Animator) {}
                        override fun onAnimationRepeat(animator: Animator) {}
                    })
                addFilteredDeckLayout.animate().translationY(400f).setDuration(100)
                    .setListener(object : Animator.AnimatorListener {
                        override fun onAnimationStart(animator: Animator) {}
                        override fun onAnimationEnd(animator: Animator) {
                            if (!isFABOpen) {
                                addSharedLayout.visibility = View.GONE
                                addDeckLayout.visibility = View.GONE
                                addFilteredDeckLayout.visibility = View.GONE
                                addNoteLabel.visibility = View.GONE
                            }
                        }

                        override fun onAnimationCancel(animator: Animator) {}
                        override fun onAnimationRepeat(animator: Animator) {}
                    })
            } else {
                // Close without animation
                addSharedLayout.visibility = View.GONE
                addDeckLayout.visibility = View.GONE
                addFilteredDeckLayout.visibility = View.GONE
                addNoteLabel.visibility = View.GONE

                fabMain.setImageResource(addWhiteIcon)
            }
        } else {
            linearLayout.alpha = 1f
            studyOptionsFrame?.let { it.alpha = 1f }
            isFABOpen = false
            fabBGLayout.visibility = View.GONE
            addNoteLabel.visibility = View.GONE
            if (deckPicker.animationEnabled()) {
                // Changes the background color of FAB to default
                fabMain.backgroundTintList = ColorStateList.valueOf(fabNormalColor)
                // Close with animation
                fabMain.animate().apply {
                    duration = 90
                    withEndAction {
                        // At the end the image is changed to Add White Icon
                        fabMain.setImageResource(addWhiteIcon)
                    }.start()
                }

                addSharedLayout.animate().alpha(0f).duration = 70
                addDeckLayout.animate().alpha(0f).duration = 50
                addFilteredDeckLayout.animate().alpha(0f).duration = 50
                addNoteLabel.animate().alpha(0f).duration = 50
                addNoteLabel.animate().translationX(180f).duration = 70
                addSharedLayout.animate().translationY(600f).duration = 100
                addDeckLayout.animate().translationY(400f).setDuration(50)
                    .setListener(object : Animator.AnimatorListener {
                        override fun onAnimationStart(animator: Animator) {}
                        override fun onAnimationEnd(animator: Animator) {
                            if (!isFABOpen) {
                                addSharedLayout.visibility = View.GONE
                                addDeckLayout.visibility = View.GONE
                                addFilteredDeckLayout.visibility = View.GONE
                                addNoteLabel.visibility = View.GONE
                            }
                        }

                        override fun onAnimationCancel(animator: Animator) {}
                        override fun onAnimationRepeat(animator: Animator) {}
                    })
                addFilteredDeckLayout.animate().translationY(600f).setDuration(100)
                    .setListener(object : Animator.AnimatorListener {
                        override fun onAnimationStart(animator: Animator) {}
                        override fun onAnimationEnd(animator: Animator) {
                            if (!isFABOpen) {
                                addSharedLayout.visibility = View.GONE
                                addDeckLayout.visibility = View.GONE
                                addFilteredDeckLayout.visibility = View.GONE
                                addNoteLabel.visibility = View.GONE
                            }
                        }

                        override fun onAnimationCancel(animator: Animator) {}
                        override fun onAnimationRepeat(animator: Animator) {}
                    })
            } else {
                // Close without animation
                addSharedLayout.visibility = View.GONE
                addDeckLayout.visibility = View.GONE
                addFilteredDeckLayout.visibility = View.GONE
                addNoteLabel.visibility = View.GONE

                fabMain.setImageResource(addWhiteIcon)
            }
        }
    }

    fun showFloatingActionButton() {
        if (!fabMain.isShown) {
            Timber.i("DeckPicker:: showFloatingActionButton()")
            fabMain.visibility = View.VISIBLE
        }
    }

    fun hideFloatingActionButton() {
        if (fabMain.isShown) {
            Timber.i("DeckPicker:: hideFloatingActionButton()")
            fabMain.visibility = View.GONE
        }
    }

    /**
     * This function returns false if any of the mentioned system animations are disabled (0f)
     *
     * ANIMATION_DURATION_SCALE - controls app switching animation speed.
     * TRANSITION_ANIMATION_SCALE - controls app window opening and closing animation speed
     * WINDOW_ANIMATION_SCALE - controls pop-up window opening and closing animation speed
     */
    private fun areSystemAnimationsEnabled(): Boolean {
        val animDuration: Float = Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        )
        val animTransition: Float = Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.TRANSITION_ANIMATION_SCALE,
            1f
        )
        val animWindow: Float = Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.WINDOW_ANIMATION_SCALE,
            1f
        )
        return animDuration != 0f && animTransition != 0f && animWindow != 0f
    }

    init {
        val addSharedButton: FloatingActionButton = view.findViewById(R.id.add_shared_action)
        val addDeckButton: FloatingActionButton = view.findViewById(R.id.add_deck_action)
        val addFilteredDeckButton: FloatingActionButton = view.findViewById(R.id.add_filtered_deck_action)
        val addSharedLabel: TextView = view.findViewById(R.id.add_shared_label)
        val addDeckLabel: TextView = view.findViewById(R.id.add_deck_label)
        val addFilteredDeckLabel: TextView = view.findViewById(R.id.add_filtered_deck_label)
        val addNote: TextView = view.findViewById(R.id.add_note_label)
        fabMain.setOnTouchListener(object : DoubleTapListener(context) {
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
        fabBGLayout.setOnClickListener { closeFloatingActionMenu(applyRiseAndShrinkAnimation = true) }
        val addDeckListener = View.OnClickListener {
            if (isFABOpen) {
                closeFloatingActionMenu(applyRiseAndShrinkAnimation = false)
                val createDeckDialog = CreateDeckDialog(
                    context,
                    R.string.new_deck,
                    CreateDeckDialog.DeckDialogType.DECK,
                    null
                )
                createDeckDialog.setOnNewDeckCreated { deckPicker.updateDeckList() }
                createDeckDialog.showDialog()
            }
        }
        addDeckButton.setOnClickListener(addDeckListener)
        addDeckLabel.setOnClickListener(addDeckListener)
        val addFilteredDeckListener = View.OnClickListener {
            if (isFABOpen) {
                closeFloatingActionMenu(applyRiseAndShrinkAnimation = false)
                deckPicker.createFilteredDialog()
            }
        }
        addFilteredDeckButton.setOnClickListener(addFilteredDeckListener)
        addFilteredDeckLabel.setOnClickListener(addFilteredDeckListener)
        val addSharedListener = View.OnClickListener {
            if (isFABOpen) {
                closeFloatingActionMenu(applyRiseAndShrinkAnimation = false)
                Timber.d("configureFloatingActionsMenu::addSharedButton::onClickListener - Adding Shared Deck")
                deckPicker.openAnkiWebSharedDecks()
            }
        }
        addSharedButton.setOnClickListener(addSharedListener)
        addSharedLabel.setOnClickListener(addSharedListener)
        val addNoteLabelListener = View.OnClickListener {
            if (isFABOpen) {
                closeFloatingActionMenu(applyRiseAndShrinkAnimation = false)
                Timber.d("configureFloatingActionsMenu::addNoteLabel::onClickListener - Adding Note")
                addNote()
            }
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
