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
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.ichi2.anki.dialogs.CreateDeckDialog
import com.ichi2.utils.KotlinCleanup
import timber.log.Timber

class DeckPickerFloatingActionMenu(view: View, private val deckPicker: DeckPicker) {
    @KotlinCleanup("can be changed to view.findViewById<FloatingActionButton>")
    private val mFabMain: FloatingActionButton = view.findViewById<View>(R.id.fab_main) as FloatingActionButton
    private val mAddSharedLayout: LinearLayout = view.findViewById<View>(R.id.add_shared_layout) as LinearLayout
    private val mAddDeckLayout: LinearLayout = view.findViewById<View>(R.id.add_deck_layout) as LinearLayout
    private val mAddNoteLayout: LinearLayout = view.findViewById<View>(R.id.add_note_layout) as LinearLayout
    private val mFabBGLayout: View
    private val mStudyoptionsFrame: View?
    var isFABOpen = false
    private var mFragmented = false
    private val mLinearLayout: LinearLayout
    private fun animationEnabled(): Boolean {
        val preferences = AnkiDroidApp.getSharedPrefs(deckPicker)
        return !preferences.getBoolean("safeDisplay", false)
    }

    private fun showFloatingActionMenu() {
        mLinearLayout.alpha = 0.5f
        if (mFragmented) {
            mStudyoptionsFrame!!.alpha = 0.5f
        }
        isFABOpen = true
        if (animationEnabled()) {
            // Show with animation
            mAddNoteLayout.visibility = View.VISIBLE
            mAddSharedLayout.visibility = View.VISIBLE
            mAddDeckLayout.visibility = View.VISIBLE
            mFabBGLayout.visibility = View.VISIBLE
            mFabMain.animate().rotationBy(135f) // 135 = 90 + 45
            mAddNoteLayout.animate().translationY(0f).duration = 30
            mAddSharedLayout.animate().translationY(0f).duration = 50
            mAddDeckLayout.animate().translationY(0f).duration = 100
            mAddDeckLayout.animate().alpha(1f).duration = 100
            mAddSharedLayout.animate().alpha(1f).duration = 50
            mAddNoteLayout.animate().alpha(1f).duration = 30
        } else {
            // Show without animation
            mAddNoteLayout.visibility = View.VISIBLE
            mAddSharedLayout.visibility = View.VISIBLE
            mAddDeckLayout.visibility = View.VISIBLE
            mFabBGLayout.visibility = View.VISIBLE
            mAddNoteLayout.alpha = 1f
            mAddSharedLayout.alpha = 1f
            mAddDeckLayout.alpha = 1f
            mAddNoteLayout.translationY = 0f
            mAddSharedLayout.translationY = 0f
            mAddDeckLayout.translationY = 0f
        }
    }

    fun closeFloatingActionMenu() {
        mLinearLayout.alpha = 1f
        if (mFragmented) {
            mStudyoptionsFrame!!.alpha = 1f
        }
        isFABOpen = false
        mFabBGLayout.visibility = View.GONE
        if (animationEnabled()) {
            // Close with animation
            mFabMain.animate().rotation(0f)
            mAddNoteLayout.animate().translationY(200f).duration = 30
            mAddSharedLayout.animate().translationY(400f).duration = 50
            mAddDeckLayout.animate().alpha(0f).duration = 100
            mAddSharedLayout.animate().alpha(0f).duration = 50
            mAddNoteLayout.animate().alpha(0f).duration = 30
            mAddDeckLayout.animate().translationY(600f).setDuration(100).setListener(object : Animator.AnimatorListener {
                override fun onAnimationStart(animator: Animator) {}
                override fun onAnimationEnd(animator: Animator) {
                    if (!isFABOpen) {
                        mAddNoteLayout.visibility = View.GONE
                        mAddSharedLayout.visibility = View.GONE
                        mAddDeckLayout.visibility = View.GONE
                    }
                }

                override fun onAnimationCancel(animator: Animator) {}
                override fun onAnimationRepeat(animator: Animator) {}
            })
        } else {
            // Close without animation
            mAddNoteLayout.visibility = View.GONE
            mAddSharedLayout.visibility = View.GONE
            mAddDeckLayout.visibility = View.GONE
        }
    }

    init {
        val addNoteButton = view.findViewById<View>(R.id.add_note_action) as FloatingActionButton
        val addSharedButton = view.findViewById<View>(R.id.add_shared_action) as FloatingActionButton
        val addDeckButton = view.findViewById<View>(R.id.add_deck_action) as FloatingActionButton
        mFabBGLayout = view.findViewById(R.id.fabBGLayout)
        mLinearLayout = view.findViewById(R.id.deckpicker_view) // Layout deck_picker.xml is attached here

        // Check to see if the view was correctly located
        mStudyoptionsFrame = view.findViewById(R.id.studyoptions_fragment)
        mFragmented = mStudyoptionsFrame != null
        val addNoteLabel = view.findViewById<TextView>(R.id.add_note_label)
        val addSharedLabel = view.findViewById<TextView>(R.id.add_shared_label)
        val addDeckLabel = view.findViewById<TextView>(R.id.add_deck_label)
        mFabMain.setOnClickListener {
            if (!isFABOpen) {
                showFloatingActionMenu()
            } else {
                closeFloatingActionMenu()
            }
        }
        mFabBGLayout.setOnClickListener { closeFloatingActionMenu() }
        val addDeckListener = View.OnClickListener {
            if (isFABOpen) {
                closeFloatingActionMenu()
                val createDeckDialog = CreateDeckDialog(deckPicker, R.string.new_deck, CreateDeckDialog.DeckDialogType.DECK, null)
                createDeckDialog.setOnNewDeckCreated { deckPicker.updateDeckList() }
                createDeckDialog.showDialog()
            }
        }
        addDeckButton.setOnClickListener(addDeckListener)
        addDeckLabel.setOnClickListener(addDeckListener)
        val addSharedListener = View.OnClickListener {
            Timber.d("configureFloatingActionsMenu::addSharedButton::onClickListener - Adding Shared Deck")
            closeFloatingActionMenu()
            deckPicker.openAnkiWebSharedDecks()
        }
        addSharedButton.setOnClickListener(addSharedListener)
        addSharedLabel.setOnClickListener(addSharedListener)
        val addNoteListener = View.OnClickListener {
            Timber.d("configureFloatingActionsMenu::addNoteButton::onClickListener - Adding Note")
            closeFloatingActionMenu()
            deckPicker.addNote()
        }
        addNoteButton.setOnClickListener(addNoteListener)
        addNoteLabel.setOnClickListener(addNoteListener)
    }
}
