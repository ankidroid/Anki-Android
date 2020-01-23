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

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import androidx.core.content.ContextCompat;
import androidx.core.view.ActionProvider;
import androidx.core.view.MenuItemCompat;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.ichi2.anim.ActivityTransitionAnimation;
import com.ichi2.anki.dialogs.ConfirmationDialog;
import com.ichi2.anki.dialogs.IntegerDialog;
import com.ichi2.async.DeckTask;
import com.ichi2.compat.CompatHelper;
import com.ichi2.libanki.Card;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Collection.DismissType;
import com.ichi2.themes.Themes;
import com.ichi2.widget.WidgetStatus;

import org.json.JSONException;

import java.lang.ref.WeakReference;
import java.util.List;

import timber.log.Timber;

public class Reviewer extends AbstractFlashcardViewer {
    private boolean mHasDrawerSwipeConflicts = false;
    private boolean mShowWhiteboard = true;
    private boolean mBlackWhiteboard = true;
    private boolean mPrefFullscreenReview = false;
    private static final int ADD_NOTE = 12;


    private DeckTask.TaskListener mRescheduleCardHandler = new ScheduleDeckTaskListener() {
        protected int getToastResourceId() {
            return R.plurals.reschedule_cards_dialog_acknowledge;
        }
    };

    private DeckTask.TaskListener mResetProgressCardHandler = new ScheduleDeckTaskListener() {
        protected int getToastResourceId() {
            return R.plurals.reset_cards_dialog_acknowledge;
        }
    };

    /** We need to listen for and handle reschedules / resets very similarly */
    abstract class ScheduleDeckTaskListener extends NextCardHandler {

        abstract protected int getToastResourceId();


        @Override
        public void onPostExecute(DeckTask.TaskData result) {
            super.onPostExecute(result);
            invalidateOptionsMenu();
            int cardCount = result.getObjArray().length;
            UIUtils.showThemedToast(Reviewer.this,
                    getResources().getQuantityString(getToastResourceId(), cardCount, cardCount), true);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Timber.d("onCreate()");
        super.onCreate(savedInstanceState);

        if (Intent.ACTION_VIEW.equals(getIntent().getAction())) {
            Timber.d("onCreate() :: received Intent with action = %s", getIntent().getAction());
            selectDeckFromExtra();
        }

        startLoadingCollection();
    }

    private void selectDeckFromExtra() {
        Bundle extras = getIntent().getExtras();
        long did = Long.MIN_VALUE;
        if (extras != null) {
            did = extras.getLong("deckId", Long.MIN_VALUE);
        }

        if(did == Long.MIN_VALUE) {
            // deckId is not set, load default
            return;
        }

        Timber.d("selectDeckFromExtra() with deckId = %d", did);

        // Clear the undo history when selecting a new deck
        if (getCol().getDecks().selected() != did) {
            getCol().clearUndo();
        }
        // Select the deck
        getCol().getDecks().select(did);
        // Reset the schedule so that we get the counts for the currently selected deck
        getCol().getSched().reset();
    }

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
    protected int getContentViewAttr(int fullscreenMode) {
        if (CompatHelper.getSdkVersion() < Build.VERSION_CODES.KITKAT) {
            fullscreenMode = 0;     // The specific fullscreen layouts are only applicable for immersive mode
        }
        switch (fullscreenMode) {
            case 1:
                return R.layout.reviewer_fullscreen;
            case 2:
                return R.layout.reviewer_fullscreen_noanswers;
            default:
                return R.layout.reviewer;
        }
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
                if (mShowWhiteboard && mWhiteboard != null && mWhiteboard.undoSize() > 0) {
                    mWhiteboard.undo();
                } else {
                    undo();
                }
                break;

            case R.id.action_reset_card_progress:
                Timber.i("Reviewer:: Reset progress button pressed");
                showResetCardDialog();
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

            case R.id.action_bury:
                Timber.i("Reviewer:: Bury button pressed");
                if (!MenuItemCompat.getActionProvider(item).hasSubMenu()) {
                    Timber.d("Bury card due to no submenu");
                    dismiss(DismissType.BURY_CARD);
                }
                break;

            case R.id.action_suspend:
                Timber.i("Reviewer:: Suspend button pressed");
                if (!MenuItemCompat.getActionProvider(item).hasSubMenu()) {
                    Timber.d("Suspend card due to no submenu");
                    dismiss(DismissType.SUSPEND_CARD);
                }
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

            case R.id.action_flag_zero:
                Timber.i("Reviewer:: No flag");
                onFlag(mCurrentCard, 0);
                break;
            case R.id.action_flag_one:
                Timber.i("Reviewer:: Flag one");
                onFlag(mCurrentCard, 1);
                break;
            case R.id.action_flag_two:
                Timber.i("Reviewer:: Flag two");
                onFlag(mCurrentCard, 2);
                break;
            case R.id.action_flag_three:
                Timber.i("Reviewer:: Flag three");
                onFlag(mCurrentCard, 3);
                break;
            case R.id.action_flag_four:
                Timber.i("Reviewer:: Flag four");
                onFlag(mCurrentCard, 4);
                break;
            default:
                return super.onOptionsItemSelected(item);
        }
        return true;
    }

    private void showRescheduleCardDialog() {
        IntegerDialog rescheduleDialog = new IntegerDialog();
        rescheduleDialog.setArgs(
                getResources().getString(R.string.reschedule_card_dialog_title),
                getResources().getString(R.string.reschedule_card_dialog_message),
                4);
        rescheduleDialog.setCallbackRunnable(rescheduleDialog.new IntRunnable() {
            public void run() {
                DeckTask.launchDeckTask(DeckTask.TASK_TYPE_DISMISS_MULTI, mRescheduleCardHandler,
                        new DeckTask.TaskData(new Object[]{new long[]{mCurrentCard.getId()}, Collection.DismissType.RESCHEDULE_CARDS, this.getInt()}));
            }
        });
        showDialogFragment(rescheduleDialog);
    }


    private void showResetCardDialog() {
        // Show confirmation dialog before resetting card progress
        Timber.i("showResetCardDialog() Reset progress button pressed");
        // Show confirmation dialog before resetting card progress
        ConfirmationDialog dialog = new ConfirmationDialog();
        String title = getResources().getString(R.string.reset_card_dialog_title);
        String message = getResources().getString(R.string.reset_card_dialog_message);
        dialog.setArgs(title, message);
        Runnable confirm = () -> {
            Timber.i("NoteEditor:: ResetProgress button pressed");
            DeckTask.launchDeckTask(DeckTask.TASK_TYPE_DISMISS_MULTI, mResetProgressCardHandler,
                    new DeckTask.TaskData(new Object[]{new long[]{mCurrentCard.getId()}, Collection.DismissType.RESET_CARDS}));
        };
        dialog.setConfirm(confirm);
        showDialogFragment(dialog);
    }


    private void addNote() {
        Intent intent = new Intent(this, NoteEditor.class);
        intent.putExtra(NoteEditor.EXTRA_CALLER, NoteEditor.CALLER_REVIEWER_ADD);
        startActivityForResultWithAnimation(intent, ADD_NOTE, ActivityTransitionAnimation.LEFT);
    }


    private void setCustomButtons(Menu menu) {
        for(int itemId : mCustomButtons.keySet()) {
            if(mCustomButtons.get(itemId) != MENU_DISABLED) {
                menu.findItem(itemId).setShowAsAction(mCustomButtons.get(itemId));
            }
            else {
                menu.findItem(itemId).setVisible(false);
            }
        }
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // NOTE: This is called every time a new question is shown via invalidate options menu
        getMenuInflater().inflate(R.menu.reviewer, menu);
        setCustomButtons(menu);
        if (mCurrentCard != null && mCurrentCard.note().hasTag("marked")) {
            menu.findItem(R.id.action_mark_card).setTitle(R.string.menu_unmark_note).setIcon(R.drawable.ic_star_white_24dp);
        } else {
            menu.findItem(R.id.action_mark_card).setTitle(R.string.menu_mark_note).setIcon(R.drawable.ic_star_outline_white_24dp);
        }

        if (mCurrentCard != null) {
            switch (mCurrentCard.getUserFlag()) {
            case 1:
                menu.findItem(R.id.action_flag).setIcon(R.drawable.flag_red);
                break;
            case 2:
                menu.findItem(R.id.action_flag).setIcon(R.drawable.flag_orange);
                break;
            case 3:
                menu.findItem(R.id.action_flag).setIcon(R.drawable.flag_green);
                break;
            case 4:
                menu.findItem(R.id.action_flag).setIcon(R.drawable.flag_blue);
                break;
            default:
                menu.findItem(R.id.action_flag).setIcon(R.drawable.flag_transparent);
                break;
            }
        }

        if (mShowWhiteboard && mWhiteboard != null && mWhiteboard.undoSize() > 0) {
            // Whiteboard undo queue non-empty. Switch the undo icon to a whiteboard specific one.
            menu.findItem(R.id.action_undo).setIcon(R.drawable.ic_eraser_variant_white_24dp);
            menu.findItem(R.id.action_undo).setEnabled(true).getIcon().setAlpha(Themes.ALPHA_ICON_ENABLED_LIGHT);
        } else if (mShowWhiteboard && mWhiteboard != null && mWhiteboard.isUndoModeActive()) {
            // Whiteboard undo queue empty, but user has added strokes to it for current card. Disable undo button.
            menu.findItem(R.id.action_undo).setIcon(R.drawable.ic_eraser_variant_white_24dp);
            menu.findItem(R.id.action_undo).setEnabled(false).getIcon().setAlpha(Themes.ALPHA_ICON_DISABLED_LIGHT);
        } else if (colIsOpen() && getCol().undoAvailable()) {
            menu.findItem(R.id.action_undo).setIcon(R.drawable.ic_undo_white_24dp);
            menu.findItem(R.id.action_undo).setEnabled(true).getIcon().setAlpha(Themes.ALPHA_ICON_ENABLED_LIGHT);
        } else {
            menu.findItem(R.id.action_undo).setIcon(R.drawable.ic_undo_white_24dp);
            menu.findItem(R.id.action_undo).setEnabled(false).getIcon().setAlpha(Themes.ALPHA_ICON_DISABLED_LIGHT);
        }
        if (mPrefWhiteboard) {
            // Configure the whiteboard related items in the action bar
            menu.findItem(R.id.action_enable_whiteboard).setTitle(R.string.disable_whiteboard);
            if(mCustomButtons.get(R.id.action_hide_whiteboard) != MENU_DISABLED)
                menu.findItem(R.id.action_hide_whiteboard).setVisible(true);
            if(mCustomButtons.get(R.id.action_clear_whiteboard) != MENU_DISABLED)
                menu.findItem(R.id.action_clear_whiteboard).setVisible(true);

            Drawable whiteboardIcon = ContextCompat.getDrawable(this, R.drawable.ic_gesture_white_24dp);
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
        if (colIsOpen() && getCol().getDecks().isDyn(getParentDid())) {
            menu.findItem(R.id.action_open_deck_options).setVisible(false);
        }
        if (mSpeakText && mCustomButtons.get(R.id.action_select_tts) != MENU_DISABLED) {
                menu.findItem(R.id.action_select_tts).setVisible(true);
        }
        // Setup bury / suspend providers
        MenuItemCompat.setActionProvider(menu.findItem(R.id.action_suspend), new SuspendProvider(this));
        MenuItemCompat.setActionProvider(menu.findItem(R.id.action_bury), new BuryProvider(this));
        if (dismissNoteAvailable(DismissType.SUSPEND_NOTE)) {
            menu.findItem(R.id.action_suspend).setIcon(R.drawable.ic_action_suspend_dropdown);
            menu.findItem(R.id.action_suspend).setTitle(R.string.menu_suspend);
        } else {
            menu.findItem(R.id.action_suspend).setIcon(R.drawable.ic_action_suspend);
            menu.findItem(R.id.action_suspend).setTitle(R.string.menu_suspend_card);
        }
        if (dismissNoteAvailable(DismissType.BURY_NOTE)) {
            menu.findItem(R.id.action_bury).setIcon(R.drawable.ic_flip_to_back_white_24px_dropdown);
            menu.findItem(R.id.action_bury).setTitle(R.string.menu_bury);
        } else {
            menu.findItem(R.id.action_bury).setIcon(R.drawable.ic_flip_to_back_white_24dp);
            menu.findItem(R.id.action_bury).setTitle(R.string.menu_bury_card);
        }
        MenuItemCompat.setActionProvider(menu.findItem(R.id.action_schedule), new ScheduleProvider(this));
        return super.onCreateOptionsMenu(menu);
    }


    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        char keyPressed = (char) event.getUnicodeChar();
        if (mAnswerField != null && !mAnswerField.isFocused()) {
	        if (sDisplayAnswer) {
	            if (keyPressed == '1' || keyCode == KeyEvent.KEYCODE_BUTTON_Y) {
	                answerCard(EASE_1);
	                return true;
	            }
	            if (keyPressed == '2' || keyCode == KeyEvent.KEYCODE_BUTTON_X) {
	                answerCard(EASE_2);
	                return true;
	            }
	            if (keyPressed == '3' || keyCode == KeyEvent.KEYCODE_BUTTON_B) {
	                answerCard(EASE_3);
	                return true;
	            }
	            if (keyPressed == '4' || keyCode == KeyEvent.KEYCODE_BUTTON_A) {
	                answerCard(EASE_4);
	                return true;
	            }
	            if (keyCode == KeyEvent.KEYCODE_SPACE || keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) {
	                answerCard(getDefaultEase());
	                return true;
	            }
	        }
                else {
                    if (keyCode == KeyEvent.KEYCODE_BUTTON_Y || keyCode == KeyEvent.KEYCODE_BUTTON_X
                            || keyCode == KeyEvent.KEYCODE_BUTTON_B || keyCode == KeyEvent.KEYCODE_BUTTON_A) {
                        displayCardAnswer();
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
                dismiss(DismissType.BURY_CARD);
	            return true;
	        }
	        if (keyPressed == '=') {
                dismiss(DismissType.BURY_NOTE);
	            return true;
	        }
	        if (keyPressed == '@') {
                dismiss(DismissType.SUSPEND_CARD);
	            return true;
	        }
	        if (keyPressed == '!') {
                dismiss(DismissType.SUSPEND_NOTE);
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
        mPrefFullscreenReview = Integer.parseInt(preferences.getString("fullscreenMode", "0")) > 0;
        return preferences;
    }

    @Override
    public void fillFlashcard() {
        super.fillFlashcard();
        if (!sDisplayAnswer && mShowWhiteboard && mWhiteboard != null) {
            mWhiteboard.clear();
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

        if (!isFinishing() && colIsOpen() && mSched != null) {
            WidgetStatus.update(this);
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
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        mWhiteboard.setLayoutParams(lp2);
        FrameLayout fl = findViewById(R.id.whiteboard);
        fl.addView(mWhiteboard);

        mWhiteboard.setOnTouchListener((v, event) -> {
            if (!mShowWhiteboard || (mPrefFullscreenReview
                    && CompatHelper.getCompat().isImmersiveSystemUiVisible(Reviewer.this))) {
                // Bypass whiteboard listener when it's hidden or fullscreen immersive mode is temporarily suspended
                v.performClick();
                return getGestureDetector().onTouchEvent(event);
            }
            return mWhiteboard.handleTouchEvent(event);
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
    protected Long getCurrentCardId() {
        return mCurrentCard.getId();
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


    /**
     * Whether or not dismiss note is available for current card and specified DismissType
     * @param type Currently only SUSPEND_NOTE and BURY_NOTE supported
     * @return true if there is another card of same note that could be dismissed
     */
    private boolean dismissNoteAvailable(DismissType type) {
        if (mCurrentCard == null || mCurrentCard.note() == null || mCurrentCard.note().cards().size() < 2) {
            return false;
        }
        List<Card> cards = mCurrentCard.note().cards();
        for(Card card : cards) {
            if (card.getId() == mCurrentCard.getId()) continue;
            int queue = card.getQueue();
            if(type == DismissType.SUSPEND_NOTE && queue != Card.QUEUE_SUSP) {
                return true;
            } else if (type == DismissType.BURY_NOTE &&
                    queue != Card.QUEUE_SUSP && queue != Card.QUEUE_USER_BRD && queue != Card.QUEUE_SCHED_BRD) {
                return true;
            }
        }
        return false;
    }

    /**
     * Inner class which implements the submenu for the Suspend button
     */
    class SuspendProvider extends ActionProvider implements MenuItem.OnMenuItemClickListener {
        public SuspendProvider(Context context) {
            super(context);
        }

        @Override
        public View onCreateActionView() {
            return null;  // Just return null for a simple dropdown menu
        }

        @Override
        public boolean hasSubMenu() {
            return dismissNoteAvailable(DismissType.SUSPEND_NOTE);
        }

        @Override
        public void onPrepareSubMenu(SubMenu subMenu) {
            subMenu.clear();
            getMenuInflater().inflate(R.menu.reviewer_suspend, subMenu);
            for (int i = 0; i < subMenu.size(); i++) {
                subMenu.getItem(i).setOnMenuItemClickListener(this);
            }
        }

        @Override
        public boolean onMenuItemClick(MenuItem item) {
            switch (item.getItemId()) {
                case R.id.action_suspend_card:
                    dismiss(DismissType.SUSPEND_CARD);
                    return true;
                case R.id.action_suspend_note:
                    dismiss(DismissType.SUSPEND_NOTE);
                    return true;
                default:
                    return false;
            }
        }
    }

    /**
     * Inner class which implements the submenu for the Bury button
     */
    class BuryProvider extends ActionProvider implements MenuItem.OnMenuItemClickListener {
        public BuryProvider(Context context) {
            super(context);
        }

        @Override
        public View onCreateActionView() {
            return null;    // Just return null for a simple dropdown menu
        }

        @Override
        public boolean hasSubMenu() {
            return dismissNoteAvailable(DismissType.BURY_NOTE);
        }

        @Override
        public void onPrepareSubMenu(SubMenu subMenu) {
            subMenu.clear();
            getMenuInflater().inflate(R.menu.reviewer_bury, subMenu);
            for (int i = 0; i < subMenu.size(); i++) {
                subMenu.getItem(i).setOnMenuItemClickListener(this);
            }
        }

        @Override
        public boolean onMenuItemClick(MenuItem item) {
            switch (item.getItemId()) {
                case R.id.action_bury_card:
                    dismiss(DismissType.BURY_CARD);
                    return true;
                case R.id.action_bury_note:
                    dismiss(DismissType.BURY_NOTE);
                    return true;
                default:
                    return false;
            }
        }
    }


    /**
     * Inner class which implements the submenu for the Schedule button
     */
    class ScheduleProvider extends ActionProvider implements MenuItem.OnMenuItemClickListener {
        public ScheduleProvider(Context context) {
            super(context);
        }

        @Override
        public View onCreateActionView() {
            return null;    // Just return null for a simple dropdown menu
        }

        @Override
        public boolean hasSubMenu() {
            return true;
        }

        @Override
        public void onPrepareSubMenu(SubMenu subMenu) {
            subMenu.clear();
            getMenuInflater().inflate(R.menu.reviewer_schedule, subMenu);
            for (int i = 0; i < subMenu.size(); i++) {
                subMenu.getItem(i).setOnMenuItemClickListener(this);
            }
        }

        @Override
        public boolean onMenuItemClick(MenuItem item) {
            switch (item.getItemId()) {
                case R.id.action_reschedule_card:
                    showRescheduleCardDialog();
                    return true;
                case R.id.action_reset_card_progress:
                    showResetCardDialog();
                    return true;
                default:
                    return false;
            }
        }
    }
}
