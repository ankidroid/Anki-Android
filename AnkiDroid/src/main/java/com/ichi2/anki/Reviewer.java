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
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;

import com.ichi2.async.DeckTask;
import com.ichi2.compat.CompatHelper;
import com.ichi2.libanki.Collection;
import com.ichi2.themes.Themes;
import com.ichi2.widget.WidgetStatus;

import org.json.JSONException;

import java.lang.ref.WeakReference;

import timber.log.Timber;

public class Reviewer extends AbstractFlashcardViewer {
    private boolean mHasDrawerSwipeConflicts = false;
    private boolean mShowWhiteboard = true;
    private boolean mBlackWhiteboard = true;

    @Override
    protected void setTitle() {
        try {
            String[] title = {""};
            if (colIsOpen()) {
                title = getCol().getDecks().current().getString("name").split("::");
            } else {
                Timber.e("Could not set title in reviewer because collection closed");
            }
            UIUtils.setTitle(this, title[title.length - 1]);
            super.setTitle(title[title.length - 1]);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        UIUtils.setSubtitle(this, "", mNightMode);
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
        DeckTask.launchDeckTask(DeckTask.TASK_TYPE_ANSWER_CARD, mAnswerCardHandler, new DeckTask.TaskData(getCol(), mSched, null,
                0));

        disableDrawerSwipeOnConflicts();
        // Add a weak reference to current activity so that scheduler can talk to to Activity
        mSched.setContext(new WeakReference<Activity>(this));
    }



    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // The action bar home/up action should open or close the drawer.
        // ActionBarDrawerToggle will take care of this.
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
                DeckTask.launchDeckTask(DeckTask.TASK_TYPE_MARK_CARD, mMarkCardHandler, new DeckTask.TaskData(
                        getCol(), mSched, mCurrentCard, 0));
                break;

            case R.id.action_replay:
                Timber.i("Reviewer:: Replay audio button pressed (from menu)");
                playSounds(true);
                break;

            case R.id.action_edit:
                Timber.i("Reviewer:: Edit note button pressed");
                return editCard();

            case R.id.action_bury_card:
                Timber.i("Reviewer:: Bury card button pressed");
                DeckTask.launchDeckTask(DeckTask.TASK_TYPE_DISMISS_NOTE, mDismissCardHandler, new DeckTask.TaskData(
                        getCol(), mSched, mCurrentCard, 4));
                break;

            case R.id.action_bury_note:
                Timber.i("Reviewer:: Bury note button pressed");
                DeckTask.launchDeckTask(DeckTask.TASK_TYPE_DISMISS_NOTE, mDismissCardHandler, new DeckTask.TaskData(
                        getCol(), mSched, mCurrentCard, 0));
                break;

            case R.id.action_suspend_card:
                Timber.i("Reviewer:: Suspend card button pressed");
                DeckTask.launchDeckTask(DeckTask.TASK_TYPE_DISMISS_NOTE, mDismissCardHandler, new DeckTask.TaskData(
                        getCol(), mSched, mCurrentCard, 1));
                break;

            case R.id.action_suspend_note:
                Timber.i("Reviewer:: Suspend note button pressed");
                DeckTask.launchDeckTask(DeckTask.TASK_TYPE_DISMISS_NOTE, mDismissCardHandler, new DeckTask.TaskData(
                        getCol(), mSched, mCurrentCard, 2));
                break;

            case R.id.action_delete:
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

            default:
                return super.onOptionsItemSelected(item);
        }
        return true;
    }


    @SuppressLint("NewApi")
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.reviewer, menu);
        Resources res = getResources();
        if (mCurrentCard != null && mCurrentCard.note().hasTag("marked")) {
            menu.findItem(R.id.action_mark_card).setTitle(R.string.menu_unmark_card).setIcon(R.drawable.ic_star_white_24dp);
        } else {
            menu.findItem(R.id.action_mark_card).setTitle(R.string.menu_mark_card).setIcon(R.drawable.ic_star_outline_white_24dp);
        }
        if (colIsOpen() && getCol().undoAvailable()) {
            menu.findItem(R.id.action_undo).setEnabled(true).getIcon().setAlpha(Themes.ALPHA_ICON_ENABLED_LIGHT);
        } else {
            menu.findItem(R.id.action_undo).setEnabled(false).getIcon().setAlpha(
                    Themes.ALPHA_ICON_DISABLED_LIGHT);
        }
        if (mPrefWhiteboard) {
            // Check if we can forceably squeeze in 3 items into the action bar, if not hide "show whiteboard"
            if (CompatHelper.getSdkVersion() >= 14 &&  !ViewConfiguration.get(this).hasPermanentMenuKey()) {
                // Android 4.x device with overflow menu in the action bar and small screen can't
                // support forcing 2 extra items into the action bar
                Display display = getWindowManager().getDefaultDisplay();
                DisplayMetrics outMetrics = new DisplayMetrics ();
                display.getMetrics(outMetrics);
                float density  = getResources().getDisplayMetrics().density;
                float dpWidth  = outMetrics.widthPixels / density;
                if (dpWidth < 360) {
                    menu.findItem(R.id.action_hide_whiteboard).setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
                }
            }
            // Configure the whiteboard related items in the action bar
            menu.findItem(R.id.action_enable_whiteboard).setTitle(R.string.disable_whiteboard);
            menu.findItem(R.id.action_hide_whiteboard).setVisible(true);
            menu.findItem(R.id.action_clear_whiteboard).setVisible(true);
            if (mShowWhiteboard) {
                if (mNightMode) {
                    menu.findItem(R.id.action_hide_whiteboard).setIcon(R.drawable.ic_action_whiteboard_enable_dark);
                } else {
                    menu.findItem(R.id.action_hide_whiteboard).setIcon(R.drawable.ic_action_whiteboard_enable_light);
                }
                //menu.findItem(R.id.action_clear_whiteboard).setIcon(R.drawable.ic_whiteboard_clear_enabled);
                menu.findItem(R.id.action_hide_whiteboard).setTitle(R.string.hide_whiteboard);
            } else {
                if (mNightMode) {
                    menu.findItem(R.id.action_hide_whiteboard).setIcon(R.drawable.ic_action_whiteboard_enable_dark_disabled);
                } else {
                    menu.findItem(R.id.action_hide_whiteboard).setIcon(R.drawable.ic_action_whiteboard_enable_light_disabled);
                }
                    //menu.findItem(R.id.action_clear_whiteboard).setIcon(R.drawable.ic_whiteboard_clear_disabled);
                menu.findItem(R.id.action_hide_whiteboard).setTitle(R.string.show_whiteboard);
            }
        } else {
            menu.findItem(R.id.action_enable_whiteboard).setTitle(R.string.enable_whiteboard);
        }
        if (!CompatHelper.isHoneycomb() && !mDisableClipboard) {
            menu.findItem(R.id.action_search_dictionary).setVisible(true).setEnabled(!(mPrefWhiteboard && mShowWhiteboard))
                    .setTitle(clipboardHasText() ? Lookup.getSearchStringTitle() : res.getString(R.string.menu_select));
        }
        return super.onCreateOptionsMenu(menu);
    }

    /*
     * Modify the options menu.
     * Pick the right icons for the whiteboard actions.

     *
     * @param menu The menu as is.
     * @return The result of
     */
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        // On old Androids this gets called each time the menu is opend, on newer ones (=> 3.0) only after it has been
        // invalidated. We do *that* on purpose when toggeling the night mode.
        if (mPrefWhiteboard) {
            if (mShowWhiteboard){
                if (mNightMode) {
                    menu.findItem(R.id.action_clear_whiteboard).setIcon(R.drawable.ic_action_cancel_dark);
                    menu.findItem(R.id.action_hide_whiteboard).setIcon(R.drawable.ic_action_whiteboard_enable_dark);
                } else {
                    menu.findItem(R.id.action_clear_whiteboard).setIcon(R.drawable.ic_action_cancel);
                    menu.findItem(R.id.action_hide_whiteboard).setIcon(R.drawable.ic_action_whiteboard_enable_light);

                }
            } else {
                if (mNightMode) {
                    menu.findItem(R.id.action_clear_whiteboard).setIcon(R.drawable.ic_action_cancel_dark);
                    menu.findItem(R.id.action_hide_whiteboard).setIcon(R.drawable.ic_action_whiteboard_enable_dark_disabled);
                } else {
                    menu.findItem(R.id.action_clear_whiteboard).setIcon(R.drawable.ic_action_cancel);
                    menu.findItem(R.id.action_hide_whiteboard).setIcon(R.drawable.ic_action_whiteboard_enable_light_disabled);
                }
            }
        }
        if (mNightMode) {
            menu.findItem(R.id.action_replay).setIcon(R.drawable.ic_action_replay_dark);
        }

        return super.onPrepareOptionsMenu(menu);
    }


    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        char keyPressed = (char) event.getUnicodeChar();
        if (mAnswerField != null && !mAnswerField.isFocused()) {
	        if (sDisplayAnswer) {
	            if (keyPressed == '1') {
	                answerCard(EASE_FAILED);
	                return true;
	            }
	            if (keyPressed == '2') {
	                answerCard(EASE_HARD);
	                return true;
	            }
	            if (keyPressed == '3') {
	                answerCard(EASE_MID);
	                return true;
	            }
	            if (keyPressed == '4') {
	                answerCard(EASE_EASY);
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
	            DeckTask.launchDeckTask(DeckTask.TASK_TYPE_MARK_CARD, mMarkCardHandler, new DeckTask.TaskData(
                        getCol(), mSched, mCurrentCard, 0));
	            return true;
	        }
	        if (keyPressed == '-') {
	            DeckTask.launchDeckTask(DeckTask.TASK_TYPE_DISMISS_NOTE, mDismissCardHandler, new DeckTask.TaskData(
                        getCol(), mSched, mCurrentCard, 4));
	            return true;
	        }
	        if (keyPressed == '=') {
	            DeckTask.launchDeckTask(DeckTask.TASK_TYPE_DISMISS_NOTE, mDismissCardHandler, new DeckTask.TaskData(
                        getCol(), mSched, mCurrentCard, 0));
	            return true;
	        }
	        if (keyPressed == '@') {
	            DeckTask.launchDeckTask(DeckTask.TASK_TYPE_DISMISS_NOTE, mDismissCardHandler, new DeckTask.TaskData(
                        getCol(), mSched, mCurrentCard, 1));
	            return true;
	        }
	        if (keyPressed == '!') {
	            DeckTask.launchDeckTask(DeckTask.TASK_TYPE_DISMISS_NOTE, mDismissCardHandler, new DeckTask.TaskData(
                        getCol(), mSched, mCurrentCard, 2));
	            return true;
	        }
	        if (keyPressed == 'r' || keyCode == KeyEvent.KEYCODE_F5) {
	            playSounds(true);
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


    @Override
    public void displayCardQuestion() {
        // show timer, if activated in the deck's preferences
        initTimer();
        super.displayCardQuestion();
    }

    @Override
    protected void onStop() {
        super.onStop();

        if (!isFinishing()) {
            if (colIsOpen()) {
                WidgetStatus.update(this, mSched.progressToday(null, mCurrentCard, true));
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
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_STATISTICS || requestCode == REQUEST_BROWSE_CARDS) {
            // select original deck if the statistics or card browser were opened,
            // which can change the selected deck
            if (data.hasExtra("originalDeck")) {
                getCol().getDecks().select(data.getLongExtra("originalDeck", 0L));
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }
}
