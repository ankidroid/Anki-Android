/****************************************************************************************
 * Copyright (c) 2021 Akshay Jadhav <jadhavakshay0701@gmail.com>                        *
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

package com.ichi2.anki;

import android.animation.Animator;
import android.content.SharedPreferences;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.ichi2.anki.dialogs.CreateDeckDialog;
import com.ichi2.libanki.Decks;
import com.ichi2.ui.FixedEditText;

import timber.log.Timber;

public class DeckPickerFloatingActionMenu {

    private final FloatingActionButton mAddDeckButton;
    private final FloatingActionButton mAddNoteButton;
    private final FloatingActionButton mAddSharedButton;
    private final FloatingActionButton mFabMain;
    private final LinearLayout mAddSharedLayout;
    private final LinearLayout mAddDeckLayout;
    private final LinearLayout mAddNoteLayout;
    private final View mFabBGLayout;
    private boolean mIsFABOpen = false;

    private final DeckPicker mDeckPicker;
    private LinearLayout mLinearLayout;

    public DeckPickerFloatingActionMenu(View view, DeckPicker deckPicker) {
        this.mDeckPicker = deckPicker;
        mAddNoteLayout = (LinearLayout)view.findViewById(R.id.add_note_layout);
        mAddSharedLayout = (LinearLayout)view.findViewById(R.id.add_shared_layout);
        mAddDeckLayout = (LinearLayout)view.findViewById(R.id.add_deck_layout);
        mFabMain = (FloatingActionButton)view.findViewById(R.id.fab_main);
        mAddNoteButton = (FloatingActionButton)view.findViewById(R.id.add_note_action);
        mAddSharedButton = (FloatingActionButton)view.findViewById(R.id.add_shared_action);
        mAddDeckButton = (FloatingActionButton)view.findViewById(R.id.add_deck_action);
        mFabBGLayout = view.findViewById(R.id.fabBGLayout);
        mLinearLayout = view.findViewById(R.id.deckpicker_view);

        mFabMain.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!mIsFABOpen) {
                    showFloatingActionMenu();
                } else {
                    closeFloatingActionMenu();
                }
            }
        });

        mFabBGLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                closeFloatingActionMenu();
            }
        });

        mAddDeckButton.setOnClickListener(addDeckButtonView -> {
            if (mIsFABOpen) {
                closeFloatingActionMenu();
                CreateDeckDialog createDeckDialog = new CreateDeckDialog(mDeckPicker, R.string.new_deck, CreateDeckDialog.DeckDialogType.DECK, null);
                createDeckDialog.setOnNewDeckCreated((i) -> {
                    deckPicker.updateDeckList();
                });
                createDeckDialog.showDialog();
            }
        });

        mAddSharedButton.setOnClickListener(addSharedButtonView -> {
            Timber.d("configureFloatingActionsMenu::addSharedButton::onClickListener - Adding Shared Deck");
            closeFloatingActionMenu();
            deckPicker.addSharedDeck();
        });

        mAddNoteButton.setOnClickListener(addNoteButtonView -> {
            Timber.d("configureFloatingActionsMenu::addNoteButton::onClickListener - Adding Note");
            closeFloatingActionMenu();
            deckPicker.addNote();
        });
    }

    private boolean animationDisabled() {
        SharedPreferences preferences = AnkiDroidApp.getSharedPrefs(mDeckPicker);
        return preferences.getBoolean("safeDisplay", false);
    }


    public boolean isFABOpen() {
        return mIsFABOpen;
    }


    public void setIsFABOpen(boolean mIsFABOpen) {
        this.mIsFABOpen = mIsFABOpen;
    }


    private void showFloatingActionMenu() {
        mLinearLayout.setAlpha(0.5f);
        mIsFABOpen = true;
        if (!animationDisabled()) {
            // Show with animation
            mAddNoteLayout.setVisibility(View.VISIBLE);
            mAddSharedLayout.setVisibility(View.VISIBLE);
            mAddDeckLayout.setVisibility(View.VISIBLE);
            mFabBGLayout.setVisibility(View.VISIBLE);
            mFabMain.animate().rotationBy(140);
            mAddNoteLayout.animate().translationY(0).setDuration(30);
            mAddSharedLayout.animate().translationY(0).setDuration(50);
            mAddDeckLayout.animate().translationY(0).setDuration(100);
            mAddDeckLayout.animate().alpha(1f).setDuration(100);
            mAddSharedLayout.animate().alpha(1f).setDuration(50);
            mAddNoteLayout.animate().alpha(1f).setDuration(30);
        } else {
            // Show without animation
            mAddNoteLayout.setVisibility(View.VISIBLE);
            mAddSharedLayout.setVisibility(View.VISIBLE);
            mAddDeckLayout.setVisibility(View.VISIBLE);
            mFabBGLayout.setVisibility(View.VISIBLE);
        }
    }

    protected void closeFloatingActionMenu() {
        mLinearLayout.setAlpha(1f);
        mIsFABOpen = false;
        mFabBGLayout.setVisibility(View.GONE);
        if (!animationDisabled()) {
            // Close with animation
            mFabMain.animate().rotation(0);
            mAddNoteLayout.animate().translationY(200f).setDuration(30);
            mAddSharedLayout.animate().translationY(400f).setDuration(50);
            mAddDeckLayout.animate().alpha(0f).setDuration(100);
            mAddSharedLayout.animate().alpha(0f).setDuration(50);
            mAddNoteLayout.animate().alpha(0f).setDuration(30);
            mAddDeckLayout.animate().translationY(600f).setDuration(100).setListener(new Animator.AnimatorListener() {
                @Override
                public void onAnimationStart(Animator animator) { }

                @Override
                public void onAnimationEnd(Animator animator) {
                    if (!mIsFABOpen) {
                        mAddNoteLayout.setVisibility(View.GONE);
                        mAddSharedLayout.setVisibility(View.GONE);
                        mAddDeckLayout.setVisibility(View.GONE);
                    }
                }

                @Override
                public void onAnimationCancel(Animator animator) { }

                @Override
                public void onAnimationRepeat(Animator animator) { }
            });
        } else {
            // Close without animation
            mAddNoteLayout.setVisibility(View.GONE);
            mAddSharedLayout.setVisibility(View.GONE);
            mAddDeckLayout.setVisibility(View.GONE);
        }
    }
}
