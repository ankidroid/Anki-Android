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

package com.ichi2.anki;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.support.v4.view.MenuItemCompat;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

import com.ichi2.anim.ActivityTransitionAnimation;
import com.ichi2.async.DeckTask;
import com.ichi2.compat.CompatHelper;
import com.ichi2.libanki.Card;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Note;
import com.ichi2.themes.Themes;
import com.ichi2.widget.WidgetStatus;

import org.json.JSONException;

import java.lang.ref.WeakReference;
import java.util.ArrayList;

import timber.log.Timber;

public class Reviewer extends AbstractFlashcardViewer {
    private boolean mHasDrawerSwipeConflicts = false;
    private boolean mShowWhiteboard = true;
    private boolean mBlackWhiteboard = true;
    private boolean mPrefFullscreenReview = false;
    private Menu mMenu;
    private boolean mShowBuryActionbarOnlySubmenu = false;
    private boolean mShowSuspendActionbarOnlySubmenu = false;
    private static final int ADD_NOTE = 12;
    private Long mLastSelectedBrowserDid = null;

    @Override
    protected void setTitle() {
        try {
            String[] title = {""};
            if (colIsOpen()) {
                title = getCol().getDecks().current().getString("name").split("::");
            } else {
                Timber.e("Could not set title in reviewer because collection closed");
            }
            getSupportActionBar().setTitle(title[title.length - 1]);
            super.setTitle(title[title.length - 1]);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        getSupportActionBar().setSubtitle("");
    }


    @Override
    protected void onCollectionLoaded(Collection col) {
        super.onCollectionLoaded(col);
        // Load the first card and start reviewing. Uses the answer card
        // task to load a card, but since we send null
        // as the card to answer, no card will be answered.

        mPrefWhiteboard = MetaDB.getWhiteboardState(this, getParentDid());
        if (mPrefWhiteboard) {
            setWhiteboardEnabledState(true);
            setWhiteboardVisibility(true);
        }

        col.getSched().reset();     // Reset schedule incase card had previous been loaded
        DeckTask.launchDeckTask(DeckTask.TASK_TYPE_ANSWER_CARD, mAnswerCardHandler,
                new DeckTask.TaskData(null, 0));

        disableDrawerSwipeOnConflicts();
        // Add a weak reference to current activity so that scheduler can talk to to Activity
        mSched.setContext(new WeakReference<Activity>(this));

        // Set full screen/immersive mode if needed
        if (mPrefFullscreenReview) {
            CompatHelper.getCompat().setFullScreen(this);
        }
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (getDrawerToggle().onOptionsItemSelected(item)) {
            return true;
        }
        switch (item.getItemId()) {

            case android.R.id.home:
                Timber.i("Reviewer:: Home button pressed");
                closeReviewer(RESULT_OK, true);
                break;

            case R.id.action_undo:
                Timber.i("Reviewer:: Undo button pressed");
                undo();
                break;

            case R.id.action_mark_card:
                Timber.i("Reviewer:: Mark button pressed");
                onMark(mCurrentCard);
                break;

            case R.id.action_replay:
                Timber.i("Reviewer:: Replay audio button pressed (from menu)");
                playSounds(true);
                break;

            case R.id.action_edit:
                Timber.i("Reviewer:: Edit note button pressed");
                return editCard();

            case R.id.action_bury_actionbar_only:
                Timber.i("Reviewer:: Bury button pressed");
                if(!mShowBuryActionbarOnlySubmenu) {
                    // Don't show submenu, just bury the current card
                    mMenu.findItem(R.id.action_bury_actionbar_only).getSubMenu().setGroupVisible(R.id.group_menu_bury_actionbar_only, false);
                    DeckTask.launchDeckTask(DeckTask.TASK_TYPE_DISMISS_NOTE, mDismissCardHandler, new DeckTask.TaskData(mCurrentCard, 4));
                }
                else {
                    mMenu.findItem(R.id.action_bury_actionbar_only).getSubMenu().setGroupVisible(R.id.group_menu_bury_actionbar_only, true);
                }
                break;

            case R.id.action_bury_card:
                Timber.i("Reviewer:: Bury card button pressed");
                DeckTask.launchDeckTask(DeckTask.TASK_TYPE_DISMISS_NOTE, mDismissCardHandler, new DeckTask.TaskData(mCurrentCard, 4));
                break;

            case R.id.action_bury_note:
                Timber.i("Reviewer:: Bury note button pressed");
                DeckTask.launchDeckTask(DeckTask.TASK_TYPE_DISMISS_NOTE, mDismissCardHandler, new DeckTask.TaskData(mCurrentCard, 0));
                break;

            case R.id.action_suspend_card:
                Timber.i("Reviewer:: Suspend card button pressed");
                DeckTask.launchDeckTask(DeckTask.TASK_TYPE_DISMISS_NOTE, mDismissCardHandler, new DeckTask.TaskData(mCurrentCard, 1));
                break;

            case R.id.action_suspend_note:
                Timber.i("Reviewer:: Suspend note button pressed");
                DeckTask.launchDeckTask(DeckTask.TASK_TYPE_DISMISS_NOTE, mDismissCardHandler, new DeckTask.TaskData(mCurrentCard, 2));
                break;

            case R.id.action_suspend_actionbar_only:
                Timber.i("Reviewer:: Suspend button pressed");
                if(!mShowSuspendActionbarOnlySubmenu) {
                    // Don't show submenu, just suspend the current card
                    mMenu.findItem(R.id.action_suspend_actionbar_only).getSubMenu().setGroupVisible(R.id.group_menu_suspend_actionbar_only, false);
                    DeckTask.launchDeckTask(DeckTask.TASK_TYPE_DISMISS_NOTE, mDismissCardHandler, new DeckTask.TaskData(mCurrentCard, 1));
                }
                else {
                    mMenu.findItem(R.id.action_suspend_actionbar_only).getSubMenu().setGroupVisible(R.id.group_menu_suspend_actionbar_only, true);
                }
                break;

            case R.id.action_delete:
            case R.id.action_delete_actionbar_only:
                Timber.i("Reviewer:: Delete note button pressed");
                showDeleteNoteDialog();
                break;

            case R.id.action_clear_whiteboard:
                Timber.i("Reviewer:: Clear whiteboard button pressed");
                if (mWhiteboard != null) {
                    mWhiteboard.clear();
                }
                break;

            case R.id.action_hide_whiteboard:
                // toggle whiteboard visibility
                Timber.i("Reviewer:: Whiteboard visibility set to %b", !mShowWhiteboard);
                setWhiteboardVisibility(!mShowWhiteboard);
                refreshActionBar();
                break;

            case R.id.action_enable_whiteboard:
                // toggle whiteboard enabled state (and show/hide whiteboard item in action bar)
                mPrefWhiteboard = ! mPrefWhiteboard;
                Timber.i("Reviewer:: Whiteboard enabled state set to %b", mPrefWhiteboard);
                setWhiteboardEnabledState(mPrefWhiteboard);
                setWhiteboardVisibility(mPrefWhiteboard);
                refreshActionBar();
                break;

            case R.id.action_search_dictionary:
                Timber.i("Reviewer:: Search dictionary button pressed");
                lookUpOrSelectText();
                break;

            case R.id.action_open_deck_options:
                Intent i = new Intent(this, DeckOptions.class);
                startActivityForResultWithAnimation(i, DECK_OPTIONS, ActivityTransitionAnimation.FADE);
                break;

            case R.id.action_select_tts:
                Timber.i("Reviewer:: Select TTS button pressed");
                showSelectTtsDialogue();
                break;

            case R.id.action_add_note_reviewer:
                Timber.i("Reviewer:: Add note button pressed");
                addNote();
                break;

            default:
                return super.onOptionsItemSelected(item);
        }
        return true;
    }


    private void addNote() {
        Intent intent = new Intent(this, NoteEditor.class);
        intent.putExtra(NoteEditor.EXTRA_CALLER, NoteEditor.CALLER_REVIEWER_ADD);
        startActivityForResultWithAnimation(intent, ADD_NOTE, ActivityTransitionAnimation.LEFT);
    }


    private void setCustomButtons(Menu menu) {
        for(int itemId : mCustomButtons.keySet()) {
            if(mCustomButtons.get(itemId) != MENU_DISABLED) {
                MenuItemCompat.setShowAsAction(menu.findItem(itemId), mCustomButtons.get(itemId));
            }
            else {
                menu.findItem(itemId).setVisible(false);
            }
        }
        // Workaround for submenu items "If room".
        int relatedGroupId;
        for(int itemId : mCustomButtons_submenu_items.keySet()) {
            relatedGroupId = mCustomButtons_submenu_items_related.get(itemId);
            switch (mCustomButtons_submenu_items.get(itemId)) {
                case MENU_DISABLED:
                    menu.findItem(itemId).setVisible(false);
                    menu.findItem(R.id.action_dismiss).getSubMenu().setGroupVisible(relatedGroupId, false);
                    break;
                case MenuItemCompat.SHOW_AS_ACTION_ALWAYS:
                case MenuItemCompat.SHOW_AS_ACTION_IF_ROOM:
                    menu.findItem(R.id.action_dismiss).getSubMenu().setGroupVisible(relatedGroupId, false);
                    break;
                case MenuItemCompat.SHOW_AS_ACTION_NEVER:
                    menu.findItem(itemId).setVisible(false);
                    menu.findItem(R.id.action_dismiss).getSubMenu().setGroupVisible(relatedGroupId, true);
                    break;
            }
        }
        // If submenu is empty, hide it.
        if(!menu.findItem(R.id.action_dismiss).getSubMenu().hasVisibleItems()) {
            menu.findItem(R.id.action_dismiss).setVisible(false);
        }
        else {
            menu.findItem(R.id.action_dismiss).setVisible(true);
        }
    }


    @SuppressLint("NewApi")
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        mMenu = menu;
        getMenuInflater().inflate(R.menu.reviewer, menu);
        Resources res = getResources();
        setCustomButtons(menu);
        if (mCurrentCard != null && mCurrentCard.note().hasTag("marked")) {
            menu.findItem(R.id.action_mark_card).setTitle(R.string.menu_unmark_note).setIcon(R.drawable.ic_star_white_24dp);
        } else {
            menu.findItem(R.id.action_mark_card).setTitle(R.string.menu_mark_note).setIcon(R.drawable.ic_star_outline_white_24dp);
        }
        if (colIsOpen() && getCol().undoAvailable()) {
            menu.findItem(R.id.action_undo).setEnabled(true).getIcon().setAlpha(Themes.ALPHA_ICON_ENABLED_LIGHT);
        } else {
            menu.findItem(R.id.action_undo).setEnabled(false).getIcon().setAlpha(
                    Themes.ALPHA_ICON_DISABLED_LIGHT);
        }
        if (mPrefWhiteboard) {
            // Configure the whiteboard related items in the action bar
            menu.findItem(R.id.action_enable_whiteboard).setTitle(R.string.disable_whiteboard);
            if(mCustomButtons.get(R.id.action_hide_whiteboard) != MENU_DISABLED)
                menu.findItem(R.id.action_hide_whiteboard).setVisible(true);
            if(mCustomButtons.get(R.id.action_clear_whiteboard) != MENU_DISABLED)
                menu.findItem(R.id.action_clear_whiteboard).setVisible(true);

            Drawable whiteboardIcon = getResources().getDrawable(R.drawable.ic_gesture_white_24dp);
            if (mShowWhiteboard) {
                whiteboardIcon.setAlpha(255);
                menu.findItem(R.id.action_hide_whiteboard).setIcon(whiteboardIcon);
                menu.findItem(R.id.action_hide_whiteboard).setTitle(R.string.hide_whiteboard);
            } else {
                whiteboardIcon.setAlpha(77);
                menu.findItem(R.id.action_hide_whiteboard).setIcon(whiteboardIcon);
                menu.findItem(R.id.action_hide_whiteboard).setTitle(R.string.show_whiteboard);
            }
        } else {
            menu.findItem(R.id.action_enable_whiteboard).setTitle(R.string.enable_whiteboard);
        }
        if (!CompatHelper.isHoneycomb() && !mDisableClipboard) {
            menu.findItem(R.id.action_search_dictionary).setVisible(true).setEnabled(!(mPrefWhiteboard && mShowWhiteboard))
                    .setTitle(clipboardHasText() ? Lookup.getSearchStringTitle() : res.getString(R.string.menu_select));
        }
        if (getCol().getDecks().isDyn(getParentDid())) {
            menu.findItem(R.id.action_open_deck_options).setVisible(false);
        }
        if(mSpeakText){
            if(mCustomButtons.get(R.id.action_select_tts) != MENU_DISABLED)
                menu.findItem(R.id.action_select_tts).setVisible(true);
        }
        return super.onCreateOptionsMenu(menu);
    }


    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        char keyPressed = (char) event.getUnicodeChar();
        if (mAnswerField != null && !mAnswerField.isFocused()) {
	        if (sDisplayAnswer) {
	            if (keyPressed == '1') {
	                answerCard(EASE_1);
	                return true;
	            }
	            if (keyPressed == '2') {
	                answerCard(EASE_2);
	                return true;
	            }
	            if (keyPressed == '3') {
	                answerCard(EASE_3);
	                return true;
	            }
	            if (keyPressed == '4') {
	                answerCard(EASE_4);
	                return true;
	            }
	            if (keyCode == KeyEvent.KEYCODE_SPACE || keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) {
	                answerCard(getDefaultEase());
	                return true;
	            }
	        }
	        if (keyPressed == 'e') {
	            editCard();
	            return true;
	        }
	        if (keyPressed == '*') {
                onMark(mCurrentCard);
	            return true;
	        }
	        if (keyPressed == '-') {
	            DeckTask.launchDeckTask(DeckTask.TASK_TYPE_DISMISS_NOTE, mDismissCardHandler, new DeckTask.TaskData(mCurrentCard, 4));
	            return true;
	        }
	        if (keyPressed == '=') {
	            DeckTask.launchDeckTask(DeckTask.TASK_TYPE_DISMISS_NOTE, mDismissCardHandler, new DeckTask.TaskData(mCurrentCard, 0));
	            return true;
	        }
	        if (keyPressed == '@') {
	            DeckTask.launchDeckTask(DeckTask.TASK_TYPE_DISMISS_NOTE, mDismissCardHandler, new DeckTask.TaskData(mCurrentCard, 1));
	            return true;
	        }
	        if (keyPressed == '!') {
	            DeckTask.launchDeckTask(DeckTask.TASK_TYPE_DISMISS_NOTE, mDismissCardHandler, new DeckTask.TaskData(mCurrentCard, 2));
	            return true;
	        }
	        if (keyPressed == 'r' || keyCode == KeyEvent.KEYCODE_F5) {
	            playSounds(true);
	            return true;
	        }

            // different from Anki Desktop
            if (keyPressed == 'z') {
                undo();
                return true;
            }
        }
        return super.onKeyUp(keyCode, event);
    }


    @Override
    protected SharedPreferences restorePreferences() {
        super.restorePreferences();
        SharedPreferences preferences = AnkiDroidApp.getSharedPrefs(getBaseContext());
        mBlackWhiteboard = preferences.getBoolean("blackWhiteboard", true);
        mPrefFullscreenReview = Integer.parseInt(preferences.getString("fullscreenMode", "0")) >0;
        return preferences;
    }

    @Override
    public void fillFlashcard() {
        super.fillFlashcard();
        if (!sDisplayAnswer) {
            if (mShowWhiteboard && mWhiteboard != null) {
                mWhiteboard.clear();
            }
        }
    }

    private void checkActionbarSuspendBurySubmenu() {
        // Check if action bar suspend and bury submenu should be shown for current card (custom buttons)
        Note note = mCurrentCard.note();
        mShowBuryActionbarOnlySubmenu = false;
        mShowSuspendActionbarOnlySubmenu = false;
        if(note.cards().size() > 1) {
            ArrayList<Card> cards = note.cards();
            for(Card card : cards) {
                if(card.getQueue() != Card.QUEUE_SUSP && card.getQueue() != Card.QUEUE_USER_BRD && card.getQueue() != Card.QUEUE_SCHED_BRD && card.getId() != mCurrentCard.getId())
                    mShowBuryActionbarOnlySubmenu = true;
                if(card.getQueue() != Card.QUEUE_SUSP && card.getId() != mCurrentCard.getId())
                    mShowSuspendActionbarOnlySubmenu = true;
            }
        }
    }

    @Override
    public void displayCardQuestion() {
        // show timer, if activated in the deck's preferences
        initTimer();
        super.displayCardQuestion();
        if(mMenu.findItem(R.id.action_bury_actionbar_only).isVisible() || mMenu.findItem(R.id.action_suspend_actionbar_only).isVisible())
            checkActionbarSuspendBurySubmenu();
    }

    @Override
    protected void onStop() {
        super.onStop();

        if (!isFinishing()) {
            if (colIsOpen() && mSched != null) {
                WidgetStatus.update(this);
            }
        }
        UIUtils.saveCollectionInBackground(this);
    }


    @Override
    protected void initControls() {
        super.initControls();
        if (mPrefWhiteboard) {
            setWhiteboardVisibility(mShowWhiteboard);
        }
    }


    private void setWhiteboardEnabledState(boolean state) {
        mPrefWhiteboard = state;
        MetaDB.storeWhiteboardState(this, getParentDid(), state);
        if (state && mWhiteboard == null) {
            createWhiteboard();
        }
    }

    // Create the whiteboard
    private void createWhiteboard() {
        mWhiteboard = new Whiteboard(this, mNightMode, mBlackWhiteboard);
        FrameLayout.LayoutParams lp2 = new FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.FILL_PARENT, android.view.ViewGroup.LayoutParams.FILL_PARENT);
        mWhiteboard.setLayoutParams(lp2);
        FrameLayout fl = (FrameLayout) findViewById(R.id.whiteboard);
        fl.addView(mWhiteboard);

        mWhiteboard.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (mShowWhiteboard) {
                    return false;
                }
                return getGestureDetector().onTouchEvent(event);
            }
        });
        mWhiteboard.setEnabled(true);
    }

    // Show or hide the whiteboard
    private void setWhiteboardVisibility(boolean state) {
        mShowWhiteboard = state;
        if (state) {
            mWhiteboard.setVisibility(View.VISIBLE);
            disableDrawerSwipe();
        } else {
            mWhiteboard.setVisibility(View.GONE);
            if (!mHasDrawerSwipeConflicts) {
                enableDrawerSwipe();
            }
        }
    }


    private void disableDrawerSwipeOnConflicts() {
        SharedPreferences preferences = AnkiDroidApp.getSharedPrefs(getBaseContext());
        boolean gesturesEnabled = AnkiDroidApp.initiateGestures(preferences);
        if (gesturesEnabled) {
            int gestureSwipeUp = Integer.parseInt(preferences.getString("gestureSwipeUp", "9"));
            int gestureSwipeDown = Integer.parseInt(preferences.getString("gestureSwipeDown", "0"));
            int gestureSwipeRight = Integer.parseInt(preferences.getString("gestureSwipeRight", "17"));
            if (gestureSwipeUp != GESTURE_NOTHING ||
                    gestureSwipeDown != GESTURE_NOTHING ||
                    gestureSwipeRight != GESTURE_NOTHING) {
                mHasDrawerSwipeConflicts = true;
                super.disableDrawerSwipe();
            }
        }
    }


    @Override
    protected void openCardBrowser() {
        Intent cardBrowser = new Intent(this, CardBrowser.class);
        cardBrowser.putExtra("selectedDeck", getCol().getDecks().selected());
        if (mLastSelectedBrowserDid != null) {
            cardBrowser.putExtra("defaultDeckId", mLastSelectedBrowserDid);
        } else {
            cardBrowser.putExtra("defaultDeckId", getCol().getDecks().selected());
        }
        cardBrowser.putExtra("currentCard", mCurrentCard.getId());
        startActivityForResultWithAnimation(cardBrowser, REQUEST_BROWSE_CARDS, ActivityTransitionAnimation.LEFT);
    }


    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        // Restore full screen once we regain focus
        if (hasFocus) {
            delayedHide(INITIAL_HIDE_DELAY);
        } else {
            mFullScreenHandler.removeMessages(0);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_STATISTICS || requestCode == REQUEST_BROWSE_CARDS) {
            // Store the selected deck
            if (data != null && data.getBooleanExtra("allDecksSelected", false)) {
                mLastSelectedBrowserDid = -1L;
            } else {
                mLastSelectedBrowserDid = getCol().getDecks().selected();
            }
            // select original deck if the statistics or card browser were opened, which can change the selected deck
            if (data != null && data.hasExtra("originalDeck")) {
                getCol().getDecks().select(data.getLongExtra("originalDeck", 0L));
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }
}
