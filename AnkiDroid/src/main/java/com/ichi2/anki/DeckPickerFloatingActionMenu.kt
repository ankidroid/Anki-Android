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
import timber.log.Timber

class DeckPickerFloatingActionMenu(view: View, private val mDeckPicker: DeckPicker) {
    private val mFabMain: FloatingActionButton
    private val mAddSharedLayout: LinearLayout
    private val mAddDeckLayout: LinearLayout
    private val mAddNoteLayout: LinearLayout
    private val mFabBGLayout: View
    var mIsFABOpen = false
    private val mLinearLayout: LinearLayout
    private fun animationEnabled(): Boolean {
        val preferences = AnkiDroidApp.getSharedPrefs(mDeckPicker)
        return !preferences.getBoolean("safeDisplay", false)
    }

    private fun showFloatingActionMenu() {
        mLinearLayout.alpha = 0.5f
        mIsFABOpen = true
        if (animationEnabled()) {
            // Show with animation
            mAddNoteLayout.visibility = View.VISIBLE
            mAddSharedLayout.visibility = View.VISIBLE
            mAddDeckLayout.visibility = View.VISIBLE
            mFabBGLayout.visibility = View.VISIBLE
            mFabMain.animate().rotationBy(140f)
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
        }
    }

    fun closeFloatingActionMenu() {
        mLinearLayout.alpha = 1f
        mIsFABOpen = false
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
                    if (!mIsFABOpen) {
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
        mAddNoteLayout = view.findViewById<View>(R.id.add_note_layout) as LinearLayout
        mAddSharedLayout = view.findViewById<View>(R.id.add_shared_layout) as LinearLayout
        mAddDeckLayout = view.findViewById<View>(R.id.add_deck_layout) as LinearLayout
        mFabMain = view.findViewById<View>(R.id.fab_main) as FloatingActionButton
        val addNoteButton = view.findViewById<View>(R.id.add_note_action) as FloatingActionButton
        val addSharedButton = view.findViewById<View>(R.id.add_shared_action) as FloatingActionButton
        val addDeckButton = view.findViewById<View>(R.id.add_deck_action) as FloatingActionButton
        mFabBGLayout = view.findViewById(R.id.fabBGLayout)
        mLinearLayout = view.findViewById(R.id.deckpicker_view)
        val addNoteLabel = view.findViewById<TextView>(R.id.add_note_label)
        val addSharedLabel = view.findViewById<TextView>(R.id.add_shared_label)
        val addDeckLabel = view.findViewById<TextView>(R.id.add_deck_label)
        mFabMain.setOnClickListener { v: View? ->
            if (!mIsFABOpen) {
                showFloatingActionMenu()
            } else {
                closeFloatingActionMenu()
            }
        }
        mFabBGLayout.setOnClickListener { v: View? -> closeFloatingActionMenu() }
        val addDeckListener = View.OnClickListener { addDeckButtonView: View? ->
            if (mIsFABOpen) {
                closeFloatingActionMenu()
                val createDeckDialog = CreateDeckDialog(mDeckPicker, R.string.new_deck, CreateDeckDialog.DeckDialogType.DECK, null)
                createDeckDialog.setOnNewDeckCreated { i: Long? -> mDeckPicker.updateDeckList() }
                createDeckDialog.showDialog()
            }
        }
        addDeckButton.setOnClickListener(addDeckListener)
        addDeckLabel.setOnClickListener(addDeckListener)
        val addSharedListener = View.OnClickListener { addSharedButtonView: View? ->
            Timber.d("configureFloatingActionsMenu::addSharedButton::onClickListener - Adding Shared Deck")
            closeFloatingActionMenu()
            mDeckPicker.addSharedDeck()
        }
        addSharedButton.setOnClickListener(addSharedListener)
        addSharedLabel.setOnClickListener(addSharedListener)
        val addNoteListener = View.OnClickListener { addNoteButtonView: View? ->
            Timber.d("configureFloatingActionsMenu::addNoteButton::onClickListener - Adding Note")
            closeFloatingActionMenu()
            mDeckPicker.addNote()
        }
        addNoteButton.setOnClickListener(addNoteListener)
        addNoteLabel.setOnClickListener(addNoteListener)
    }
}
