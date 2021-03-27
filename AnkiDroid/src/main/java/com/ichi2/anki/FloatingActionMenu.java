package com.ichi2.anki;

import android.animation.Animator;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;

import com.afollestad.materialdialogs.MaterialDialog;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.ichi2.anki.exception.FilteredAncestor;
import com.ichi2.libanki.Deck;
import com.ichi2.libanki.Decks;
import com.ichi2.ui.FixedEditText;

import timber.log.Timber;

import static com.ichi2.anim.ActivityTransitionAnimation.Direction.LEFT;

public class FloatingActionMenu {

    private FloatingActionButton addDeckButton,addNoteButton,addSharedButton,fabMain;
    private LinearLayout addNoteLayout, addSharedLayout, addDeckLayout;
    private View fabBGLayout;
    private boolean mIsFABOpen = false;

    private Context mContext;
    private AnkiActivity ankiActivity;
    private DeckPicker deckPicker;

    public FloatingActionMenu(Context mContext, View mView, DeckPicker deckPicker) {
        this.mContext = mContext;
        this.ankiActivity = new AnkiActivity();
        this.deckPicker = deckPicker;
        addNoteLayout = (LinearLayout)mView.findViewById(R.id.add_note_layout);
        addSharedLayout = (LinearLayout)mView.findViewById(R.id.add_shared_layout);
        addDeckLayout = (LinearLayout)mView.findViewById(R.id.add_deck_layout);
        fabMain = (FloatingActionButton)mView.findViewById(R.id.fab_main);
        addNoteButton = (FloatingActionButton)mView.findViewById(R.id.add_note_action);
        addSharedButton = (FloatingActionButton)mView.findViewById(R.id.add_shared_action);
        addDeckButton = (FloatingActionButton)mView.findViewById(R.id.add_deck_action);
        fabBGLayout = mView.findViewById(R.id.fabBGLayout);

        fabMain.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!mIsFABOpen) {
                    showFloatingActionMenu();
                } else {
                    closeFloatingActionMenu();
                }
            }
        });

        fabBGLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                closeFloatingActionMenu();
            }
        });

        addDeckButton.setOnClickListener(view -> {
            if (mIsFABOpen) {
                closeFloatingActionMenu();
                EditText mDialogEditText = new FixedEditText(mContext);
                mDialogEditText.setSingleLine(true);
                new MaterialDialog.Builder(mContext)
                        .title(R.string.new_deck)
                        .positiveText(R.string.dialog_ok)
                        .customView(mDialogEditText, true)
                        .onPositive((dialog, which) -> {
                            String deckName = mDialogEditText.getText().toString();
                            if (Decks.isValidDeckName(deckName)) {
                                boolean creation_succeed = createNewDeck(deckName);
                                if (!creation_succeed) {
                                    return;
                                }
                            } else {
                                Timber.i("configureFloatingActionsMenu::addDeckButton::onPositiveListener - Not creating invalid deck name '%s'", deckName);
                                UIUtils.showThemedToast(mContext, mContext.getString(R.string.invalid_deck_name), false);
                            }
                        })
                        .negativeText(R.string.dialog_cancel)
                        .show();
            }
        });

        addSharedButton.setOnClickListener(view -> {
            Timber.i("Adding Shared Deck");
            closeFloatingActionMenu();
            deckPicker.addSharedDeck();
        });

        addNoteButton.setOnClickListener(view -> {
            Timber.i("Adding Note");
            closeFloatingActionMenu();
            deckPicker.addNote();
        });
    }


    /**
     * It can fail if an ancestor is a filtered deck.
     * @param deckName Create a deck with this name.
     * @return Whether creation succeeded.
     */
    private boolean createNewDeck(String deckName) {
        Timber.i("DeckPicker:: Creating new deck...");
        try {
            ankiActivity.getCol().getDecks().id(deckName);
        } catch (FilteredAncestor filteredAncestor) {
            UIUtils.showThemedToast(mContext, mContext.getString(R.string.decks_rename_filtered_nosubdecks), false);
            return false;
        }
        deckPicker.updateDeckList();
        return true;
    }

    private boolean animationDisabled(){
        SharedPreferences preferences = AnkiDroidApp.getSharedPrefs(mContext);
        return preferences.getBoolean("safeDisplay", false);
    }

    private void showFloatingActionMenu() {
        mIsFABOpen = true;
        if (!animationDisabled()) {
            // Show with animation
            addNoteLayout.setVisibility(View.VISIBLE);
            addSharedLayout.setVisibility(View.VISIBLE);
            addDeckLayout.setVisibility(View.VISIBLE);
            fabBGLayout.setVisibility(View.VISIBLE);
            fabMain.animate().rotationBy(140);
            addNoteLayout.animate().translationY(0).setDuration(30);
            addSharedLayout.animate().translationY(0).setDuration(50);
            addDeckLayout.animate().translationY(0).setDuration(100);
            addDeckLayout.animate().alpha(1f).setDuration(100);
            addSharedLayout.animate().alpha(1f).setDuration(50);
            addNoteLayout.animate().alpha(1f).setDuration(30);
        } else {
            // Show without animation
            addNoteLayout.setVisibility(View.VISIBLE);
            addSharedLayout.setVisibility(View.VISIBLE);
            addDeckLayout.setVisibility(View.VISIBLE);
            fabBGLayout.setVisibility(View.VISIBLE);
        }
    }

    protected void closeFloatingActionMenu() {
        mIsFABOpen = false;
        fabBGLayout.setVisibility(View.GONE);
        if (!animationDisabled()) {
            // Close with animation
            fabMain.animate().rotation(0);
            addNoteLayout.animate().translationY(200f).setDuration(30);
            addSharedLayout.animate().translationY(400f).setDuration(50);
            addDeckLayout.animate().alpha(0f).setDuration(100);
            addSharedLayout.animate().alpha(0f).setDuration(50);
            addNoteLayout.animate().alpha(0f).setDuration(30);
            addDeckLayout.animate().translationY(600f).setDuration(100).setListener(new Animator.AnimatorListener() {
                @Override
                public void onAnimationStart(Animator animator) { }

                @Override
                public void onAnimationEnd(Animator animator) {
                    if (!mIsFABOpen) {
                        addNoteLayout.setVisibility(View.GONE);
                        addSharedLayout.setVisibility(View.GONE);
                        addDeckLayout.setVisibility(View.GONE);
                    }
                }

                @Override
                public void onAnimationCancel(Animator animator) { }

                @Override
                public void onAnimationRepeat(Animator animator) { }
            });
        } else {
            // Close without animation
            addNoteLayout.setVisibility(View.GONE);
            addSharedLayout.setVisibility(View.GONE);
            addDeckLayout.setVisibility(View.GONE);
        }
    }
}
