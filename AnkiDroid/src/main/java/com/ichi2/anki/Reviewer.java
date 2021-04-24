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

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.SpannableString;
import android.text.style.UnderlineSpan;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.webkit.JavascriptInterface;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.CheckResult;
import androidx.annotation.DrawableRes;
import androidx.annotation.IdRes;
import androidx.annotation.MenuRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.PluralsRes;
import androidx.annotation.VisibleForTesting;
import androidx.appcompat.view.menu.MenuBuilder;
import androidx.appcompat.app.ActionBar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.ActionProvider;
import androidx.core.view.MenuItemCompat;
import androidx.vectordrawable.graphics.drawable.VectorDrawableCompat;

import com.ichi2.anki.cardviewer.CardAppearance;
import com.ichi2.anki.dialogs.ConfirmationDialog;
import com.ichi2.anki.multimediacard.AudioView;
import com.ichi2.anki.dialogs.RescheduleDialog;
import com.ichi2.anki.reviewer.PeripheralKeymap;
import com.ichi2.anki.reviewer.ReviewerUi;
import com.ichi2.anki.workarounds.FirefoxSnackbarWorkaround;
import com.ichi2.anki.reviewer.ActionButtons;
import com.ichi2.async.CollectionTask;
import com.ichi2.async.TaskManager;
import com.ichi2.libanki.Card;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Consts;
import com.ichi2.libanki.Decks;
import com.ichi2.libanki.Utils;
import com.ichi2.libanki.sched.Counts;
import com.ichi2.themes.Themes;
import com.ichi2.utils.AndroidUiUtils;
import com.ichi2.utils.FunctionalInterfaces.Consumer;
import com.ichi2.utils.PairWithBoolean;
import com.ichi2.utils.Permissions;
import com.ichi2.utils.ViewGroupUtils;
import com.ichi2.widget.WidgetStatus;

import java.lang.ref.WeakReference;
import java.util.Collections;

import timber.log.Timber;

import static com.ichi2.anki.reviewer.CardMarker.*;
import static com.ichi2.anki.cardviewer.ViewerCommand.COMMAND_NOTHING;
import static com.ichi2.anim.ActivityTransitionAnimation.Direction.*;


public class Reviewer extends AbstractFlashcardViewer {
    private boolean mHasDrawerSwipeConflicts = false;
    private boolean mShowWhiteboard = true;
    private boolean mPrefFullscreenReview = false;
    private static final int ADD_NOTE = 12;
    private static final int REQUEST_AUDIO_PERMISSION = 0;
    private LinearLayout mColorPalette;

    // TODO: Consider extracting to ViewModel
    // Card counts
    private SpannableString mNewCount;
    private SpannableString mLrnCount;
    private SpannableString mRevCount;

    private TextView mTextBarNew;
    private TextView mTextBarLearn;
    private TextView mTextBarReview;

    private boolean mPrefHideDueCount;

    // ETA
    private int mEta;
    private boolean mPrefShowETA;


    // Preferences from the collection
    private boolean mShowRemainingCardCount;

    private final ActionButtons mActionButtons = new ActionButtons(this);


    @VisibleForTesting
    protected final PeripheralKeymap mProcessor = new PeripheralKeymap(this, this);

    /** We need to listen for and handle reschedules / resets very similarly */
    class ScheduleCollectionTaskListener extends NextCardHandler<PairWithBoolean<Card[]>> {

        private final @PluralsRes int mToastResourceId;


        protected ScheduleCollectionTaskListener(@PluralsRes int toastResourceId) {
            mToastResourceId = toastResourceId;
        }


        @Override
        public void onPostExecute(PairWithBoolean<Card[]> result) {
            super.onPostExecute(result);
            invalidateOptionsMenu();
            int cardCount = result.other.length;
            UIUtils.showThemedToast(Reviewer.this,
                    getResources().getQuantityString(mToastResourceId, cardCount, cardCount), true);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (showedActivityFailedScreen(savedInstanceState)) {
            return;
        }
        Timber.d("onCreate()");
        super.onCreate(savedInstanceState);

        if (FirefoxSnackbarWorkaround.handledLaunchFromWebBrowser(getIntent(), this)) {
            this.setResult(RESULT_CANCELED);
            finishWithAnimation(END);
            return;
        }

        if (Intent.ACTION_VIEW.equals(getIntent().getAction())) {
            Timber.d("onCreate() :: received Intent with action = %s", getIntent().getAction());
            selectDeckFromExtra();
        }

        mColorPalette = findViewById(R.id.whiteboard_pen_color);

        startLoadingCollection();
    }


    @Override
    protected int getFlagToDisplay() {
        int actualValue = super.getFlagToDisplay();
        if (actualValue == FLAG_NONE) {
            return FLAG_NONE;
        }
        Boolean isShownInActionBar = mActionButtons.isShownInActionBar(ActionButtons.RES_FLAG);
        if (isShownInActionBar != null && isShownInActionBar) {
            return FLAG_NONE;
        }
        return actualValue;
    }


    @Override
    protected WebView createWebView() {
        WebView ret = super.createWebView();
        if (AndroidUiUtils.isRunningOnTv(this)) {
            ret.setFocusable(false);
        }
        return ret;
    }

    @Override
    protected void recreateWebView() {
        super.recreateWebView();
        ViewGroupUtils.setRenderWorkaround(this);
    }

    @Override
    protected boolean shouldDisplayMark() {
        boolean markValue = super.shouldDisplayMark();
        if (!markValue) {
            return false;
        }
        Boolean isShownInActionBar = mActionButtons.isShownInActionBar(ActionButtons.RES_MARK);
        //If we don't know, show it.
        //Otherwise, if it's in the action bar, don't show it again.
        return isShownInActionBar == null || !isShownInActionBar;
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
        getCol().getSched().deferReset();
    }

    @Override
    protected void setTitle() {
        String title;
        if (colIsOpen()) {
            title = Decks.basename(getCol().getDecks().current().getString("name"));
        } else {
            Timber.e("Could not set title in reviewer because collection closed");
            title = "";
        }
        getSupportActionBar().setTitle(title);
        super.setTitle(title);
        getSupportActionBar().setSubtitle("");
    }

    @Override
    protected int getContentViewAttr(int fullscreenMode) {
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
            //DEFECT: Slight inefficiency here, as we set the database using these methods
            boolean whiteboardVisibility = MetaDB.getWhiteboardVisibility(this, getParentDid());
            setWhiteboardEnabledState(true);
            setWhiteboardVisibility(whiteboardVisibility);
        }

        col.getSched().deferReset();     // Reset schedule in case card was previously loaded
        getCol().startTimebox();
        TaskManager.launchCollectionTask(new CollectionTask.GetCard(), mAnswerCardHandler(false));

        disableDrawerSwipeOnConflicts();
        // Add a weak reference to current activity so that scheduler can talk to to Activity
        mSched.setContext(new WeakReference<>(this));

        // Set full screen/immersive mode if needed
        if (mPrefFullscreenReview) {
            setFullScreen(this);
        }

        ViewGroupUtils.setRenderWorkaround(this);
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // 100ms was not enough on my device (Honor 9 Lite -  Android Pie)
        delayedHide(1000);
        if (getDrawerToggle().onOptionsItemSelected(item)) {
            return true;
        }
        int itemId = item.getItemId();
        if (itemId == android.R.id.home) {
            Timber.i("Reviewer:: Home button pressed");
            closeReviewer(RESULT_OK, true);
        } else if (itemId == R.id.action_undo) {
            Timber.i("Reviewer:: Undo button pressed");
            if (mShowWhiteboard && mWhiteboard != null && !mWhiteboard.undoEmpty()) {
                mWhiteboard.undo();
            } else {
                undo();
            }
        } else if (itemId == R.id.action_reset_card_progress) {
            Timber.i("Reviewer:: Reset progress button pressed");
            showResetCardDialog();
        } else if (itemId == R.id.action_mark_card) {
            Timber.i("Reviewer:: Mark button pressed");
            onMark(mCurrentCard);
        } else if (itemId == R.id.action_replay) {
            Timber.i("Reviewer:: Replay audio button pressed (from menu)");
            playSounds(true);
        } else if (itemId == R.id.action_toggle_mic_tool_bar) {
            Timber.i("Reviewer:: Record mic");
            // Check permission to record and request if not granted
            openOrToggleMicToolbar();
        } else if (itemId == R.id.action_tag) {
            Timber.i("Reviewer:: Tag button pressed");
            showTagsDialog();
        } else if (itemId == R.id.action_edit) {
            Timber.i("Reviewer:: Edit note button pressed");
            editCard();
            return true;
        } else if (itemId == R.id.action_bury) {
            Timber.i("Reviewer:: Bury button pressed");
            if (!MenuItemCompat.getActionProvider(item).hasSubMenu()) {
                Timber.d("Bury card due to no submenu");
                dismiss(new CollectionTask.BuryCard(mCurrentCard));
            }
        } else if (itemId == R.id.action_suspend) {
            Timber.i("Reviewer:: Suspend button pressed");
            if (!MenuItemCompat.getActionProvider(item).hasSubMenu()) {
                Timber.d("Suspend card due to no submenu");
                dismiss(new CollectionTask.SuspendCard(mCurrentCard));
            }
        } else if (itemId == R.id.action_delete) {
            Timber.i("Reviewer:: Delete note button pressed");
            showDeleteNoteDialog();
        } else if (itemId == R.id.action_change_whiteboard_pen_color) {
            Timber.i("Reviewer:: Pen Color button pressed");
            if (mColorPalette.getVisibility() == View.GONE) {
                mColorPalette.setVisibility(View.VISIBLE);
            } else {
                mColorPalette.setVisibility(View.GONE);
            }
        } else if (itemId == R.id.action_save_whiteboard) {
            Timber.i("Reviewer:: Save whiteboard button pressed");
            if (mWhiteboard != null) {
                try {
                    String savedWhiteboardFileName = mWhiteboard.saveWhiteboard(getCol().getTime());
                    UIUtils.showThemedToast(Reviewer.this, getString(R.string.white_board_image_saved, savedWhiteboardFileName), true);
                } catch (Exception e) {
                    Timber.w(e);
                    UIUtils.showThemedToast(Reviewer.this, getString(R.string.white_board_image_save_failed, e.getLocalizedMessage()), true);
                }
            }
        } else if (itemId == R.id.action_clear_whiteboard) {
            Timber.i("Reviewer:: Clear whiteboard button pressed");
            if (mWhiteboard != null) {
                mWhiteboard.clear();
            }
        } else if (itemId == R.id.action_hide_whiteboard) {// toggle whiteboard visibility
            Timber.i("Reviewer:: Whiteboard visibility set to %b", !mShowWhiteboard);
            setWhiteboardVisibility(!mShowWhiteboard);
            refreshActionBar();
        } else if (itemId == R.id.action_toggle_whiteboard) {
            toggleWhiteboard();
        } else if (itemId == R.id.action_search_dictionary) {
            Timber.i("Reviewer:: Search dictionary button pressed");
            lookUpOrSelectText();
        } else if (itemId == R.id.action_open_deck_options) {
            Intent i = new Intent(this, DeckOptions.class);
            startActivityForResultWithAnimation(i, DECK_OPTIONS, FADE);
        } else if (itemId == R.id.action_select_tts) {
            Timber.i("Reviewer:: Select TTS button pressed");
            showSelectTtsDialogue();
        } else if (itemId == R.id.action_add_note_reviewer) {
            Timber.i("Reviewer:: Add note button pressed");
            addNote();
        } else if (itemId == R.id.action_flag_zero) {
            Timber.i("Reviewer:: No flag");
            onFlag(mCurrentCard, FLAG_NONE);
        } else if (itemId == R.id.action_flag_one) {
            Timber.i("Reviewer:: Flag one");
            onFlag(mCurrentCard, FLAG_RED);
        } else if (itemId == R.id.action_flag_two) {
            Timber.i("Reviewer:: Flag two");
            onFlag(mCurrentCard, FLAG_ORANGE);
        } else if (itemId == R.id.action_flag_three) {
            Timber.i("Reviewer:: Flag three");
            onFlag(mCurrentCard, FLAG_GREEN);
        } else if (itemId == R.id.action_flag_four) {
            Timber.i("Reviewer:: Flag four");
            onFlag(mCurrentCard, FLAG_BLUE);
        } else if (itemId == R.id.action_card_info) {
            Timber.i("Card Viewer:: Card Info");
            openCardInfo();
        } else {
            return super.onOptionsItemSelected(item);
        }
        return true;
    }


    @Override
    protected void toggleWhiteboard() {
        mPrefWhiteboard = ! mPrefWhiteboard;
        Timber.i("Reviewer:: Whiteboard enabled state set to %b", mPrefWhiteboard);
        //Even though the visibility is now stored in its own setting, we want it to be dependent
        //on the enabled status
        setWhiteboardEnabledState(mPrefWhiteboard);
        setWhiteboardVisibility(mPrefWhiteboard);
        if (!mPrefWhiteboard) {
            mColorPalette.setVisibility(View.GONE);
        }
        refreshActionBar();
    }


    @Override
    protected void replayVoice() {
        if (!openMicToolbar()) {
            return;
        }

        // COULD_BE_BETTER: this shows "Failed" if nothing was recorded

        mMicToolBar.togglePlay();
    }


    @Override
    protected void recordVoice() {
        if (!openMicToolbar()) {
            return;
        }

        mMicToolBar.toggleRecord();
    }


    /**
     *
     * @return Whether the mic toolbar is usable
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    @VisibleForTesting
    public boolean openMicToolbar() {
        if (mMicToolBar == null || mMicToolBar.getVisibility() != View.VISIBLE) {
            openOrToggleMicToolbar();
        }
        return mMicToolBar != null;
    }


    protected void openOrToggleMicToolbar() {
        if (!Permissions.canRecordAudio(this)) {
            ActivityCompat.requestPermissions(this, new String[] {Manifest.permission.RECORD_AUDIO},
                    REQUEST_AUDIO_PERMISSION);
        } else {
            toggleMicToolBar();
        }
    }


    private void toggleMicToolBar() {
        if (mMicToolBar != null) {
            // It exists swap visibility status
            if (mMicToolBar.getVisibility() != View.VISIBLE) {
                mMicToolBar.setVisibility(View.VISIBLE);
            } else {
                mMicToolBar.setVisibility(View.GONE);
            }
        } else {
            // Record mic tool bar does not exist yet
            mTempAudioPath = AudioView.generateTempAudioFile(this);
            if (mTempAudioPath == null) {
                return;
            }
            mMicToolBar = AudioView.createRecorderInstance(this, R.drawable.av_play, R.drawable.av_pause,
                        R.drawable.av_stop, R.drawable.av_rec, R.drawable.av_rec_stop, mTempAudioPath);
            if (mMicToolBar == null) {
                mTempAudioPath = null;
                return;
            }
            FrameLayout.LayoutParams lp2 = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            mMicToolBar.setLayoutParams(lp2);
            LinearLayout micToolBarLayer = findViewById(R.id.mic_tool_bar_layer);
            micToolBarLayer.addView(mMicToolBar);
        }
    }

    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if ((requestCode == REQUEST_AUDIO_PERMISSION) &&
                (permissions.length >= 1) && (grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
            // Get get audio record permission, so we can create the record tool bar
            toggleMicToolBar();
        }
    }

    private void showRescheduleCardDialog() {
        Consumer<Integer> runnable = days ->
            TaskManager.launchCollectionTask(new CollectionTask.RescheduleCards(Collections.singletonList(mCurrentCard.getId()), days), new ScheduleCollectionTaskListener(R.plurals.reschedule_cards_dialog_acknowledge));
        RescheduleDialog dialog = RescheduleDialog.rescheduleSingleCard(getResources(), mCurrentCard, runnable);

        showDialogFragment(dialog);
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
            TaskManager.launchCollectionTask(new CollectionTask.ResetCards(Collections.singletonList(mCurrentCard.getId())),
                    new ScheduleCollectionTaskListener(R.plurals.reset_cards_dialog_acknowledge));
        };
        dialog.setConfirm(confirm);
        showDialogFragment(dialog);
    }


    private void addNote() {
        Intent intent = new Intent(this, NoteEditor.class);
        intent.putExtra(NoteEditor.EXTRA_CALLER, NoteEditor.CALLER_REVIEWER_ADD);
        startActivityForResultWithAnimation(intent, ADD_NOTE, START);
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // NOTE: This is called every time a new question is shown via invalidate options menu
        getMenuInflater().inflate(R.menu.reviewer, menu);

        displayIconsOnTv(menu);

        mActionButtons.setCustomButtonsStatus(menu);
        int alpha = (getControlBlocked() != ReviewerUi.ControlBlock.SLOW) ? Themes.ALPHA_ICON_ENABLED_LIGHT : Themes.ALPHA_ICON_DISABLED_LIGHT ;
        MenuItem markCardIcon = menu.findItem(R.id.action_mark_card);
        if (mCurrentCard != null && mCurrentCard.note().hasTag("marked")) {
            markCardIcon.setTitle(R.string.menu_unmark_note).setIcon(R.drawable.ic_star_white_24dp);
        } else {
            markCardIcon.setTitle(R.string.menu_mark_note).setIcon(R.drawable.ic_star_outline_white_24dp);
        }
        markCardIcon.getIcon().mutate().setAlpha(alpha);

        // 1643 - currently null on a TV
        @Nullable MenuItem flag_icon = menu.findItem(R.id.action_flag);
        if (flag_icon != null) {
            if (mCurrentCard != null) {
                switch (mCurrentCard.userFlag()) {
                    case 1:
                        flag_icon.setIcon(R.drawable.ic_flag_red);
                        break;
                    case 2:
                        flag_icon.setIcon(R.drawable.ic_flag_orange);
                        break;
                    case 3:
                        flag_icon.setIcon(R.drawable.ic_flag_green);
                        break;
                    case 4:
                        flag_icon.setIcon(R.drawable.ic_flag_blue);
                        break;
                    default:
                        flag_icon.setIcon(R.drawable.ic_flag_transparent);
                        break;
                }
            }
            flag_icon.getIcon().mutate().setAlpha(alpha);
        }

        // Undo button
        @DrawableRes int undoIconId;
        boolean undoEnabled;
        if (mShowWhiteboard && mWhiteboard != null && mWhiteboard.isUndoModeActive()) {
            // Whiteboard is here and strokes have been added at some point
            undoIconId = R.drawable.ic_eraser_variant_white_24dp;
            undoEnabled = !mWhiteboard.undoEmpty();
        } else {
            // We can arrive here even if `mShowWhiteboard &&
            // mWhiteboard != null` if no stroke had ever been made
            undoIconId = R.drawable.ic_undo_white_24dp;
            undoEnabled = (colIsOpen() && getCol().undoAvailable());
        }
        int alpha_undo = (undoEnabled && getControlBlocked() != ReviewerUi.ControlBlock.SLOW) ? Themes.ALPHA_ICON_ENABLED_LIGHT : Themes.ALPHA_ICON_DISABLED_LIGHT ;
        MenuItem undoIcon = menu.findItem(R.id.action_undo);
        undoIcon.setIcon(undoIconId);
        undoIcon.setEnabled(undoEnabled).getIcon().mutate().setAlpha(alpha_undo);
        undoIcon.getActionView().setEnabled(undoEnabled);
        if (colIsOpen()) { // Required mostly because there are tests where `col` is null
            undoIcon.setTitle(getResources().getString(R.string.studyoptions_congrats_undo, getCol().undoName(getResources())));
        }

        MenuItem toggle_whiteboard_icon = menu.findItem(R.id.action_toggle_whiteboard);
        MenuItem hide_whiteboard_icon = menu.findItem(R.id.action_hide_whiteboard);
        MenuItem change_pen_color_icon = menu.findItem(R.id.action_change_whiteboard_pen_color);
        // White board button
        if (mPrefWhiteboard) {
            // Configure the whiteboard related items in the action bar
            toggle_whiteboard_icon.setTitle(R.string.disable_whiteboard);
            // Always allow "Disable Whiteboard", even if "Enable Whiteboard" is disabled
            toggle_whiteboard_icon.setVisible(true);

            if (!mActionButtons.getStatus().hideWhiteboardIsDisabled()) {
                hide_whiteboard_icon.setVisible(true);
            }
            if (!mActionButtons.getStatus().clearWhiteboardIsDisabled()) {
                menu.findItem(R.id.action_clear_whiteboard).setVisible(true);
            }
            if (!mActionButtons.getStatus().saveWhiteboardIsDisabled()) {
                menu.findItem(R.id.action_save_whiteboard).setVisible(true);
            }
            if (!mActionButtons.getStatus().whiteboardPenColorIsDisabled()) {
                change_pen_color_icon.setVisible(true);
            }

            Drawable whiteboardIcon = ContextCompat.getDrawable(this, R.drawable.ic_gesture_white_24dp).mutate();
            Drawable whiteboardColorPaletteIcon = VectorDrawableCompat.create(getResources(), R.drawable.ic_color_lens_white_24dp, null).mutate();

            if (mShowWhiteboard) {
                whiteboardIcon.setAlpha(Themes.ALPHA_ICON_ENABLED_LIGHT);
                hide_whiteboard_icon.setIcon(whiteboardIcon);
                hide_whiteboard_icon.setTitle(R.string.hide_whiteboard);

                whiteboardColorPaletteIcon.setAlpha(Themes.ALPHA_ICON_ENABLED_LIGHT);
                change_pen_color_icon.setIcon(whiteboardColorPaletteIcon);
            } else {
                whiteboardIcon.setAlpha(Themes.ALPHA_ICON_DISABLED_LIGHT);
                hide_whiteboard_icon.setIcon(whiteboardIcon);
                hide_whiteboard_icon.setTitle(R.string.show_whiteboard);

                whiteboardColorPaletteIcon.setAlpha(Themes.ALPHA_ICON_DISABLED_LIGHT);
                change_pen_color_icon.setEnabled(false);
                change_pen_color_icon.setIcon(whiteboardColorPaletteIcon);
                mColorPalette.setVisibility(View.GONE);
            }
        } else {
            toggle_whiteboard_icon.setTitle(R.string.enable_whiteboard);
        }
        if (colIsOpen() && getCol().getDecks().isDyn(getParentDid())) {
            menu.findItem(R.id.action_open_deck_options).setVisible(false);
        }
        if (mSpeakText && !mActionButtons.getStatus().selectTtsIsDisabled()) {
            menu.findItem(R.id.action_select_tts).setVisible(true);
        }
        // Setup bury / suspend providers
        MenuItem suspend_icon = menu.findItem(R.id.action_suspend);
        MenuItem bury_icon = menu.findItem(R.id.action_bury);

        setupSubMenu(menu, R.id.action_suspend, new SuspendProvider(this));
        setupSubMenu(menu, R.id.action_bury, new BuryProvider(this));
        if (suspendNoteAvailable()) {
            suspend_icon.setIcon(R.drawable.ic_action_suspend_dropdown);
            suspend_icon.setTitle(R.string.menu_suspend);
        } else {
            suspend_icon.setIcon(R.drawable.ic_action_suspend);
            suspend_icon.setTitle(R.string.menu_suspend_card);
        }
        if (buryNoteAvailable()) {
            bury_icon.setIcon(R.drawable.ic_flip_to_back_white_24px_dropdown);
            bury_icon.setTitle(R.string.menu_bury);
        } else {
            bury_icon.setIcon(R.drawable.ic_flip_to_back_white_24dp);
            bury_icon.setTitle(R.string.menu_bury_card);
        }
        alpha = (getControlBlocked() != ReviewerUi.ControlBlock.SLOW) ? Themes.ALPHA_ICON_ENABLED_LIGHT : Themes.ALPHA_ICON_DISABLED_LIGHT ;
        bury_icon.getIcon().mutate().setAlpha(alpha);
        suspend_icon.getIcon().mutate().setAlpha(alpha);

        setupSubMenu(menu, R.id.action_schedule, new ScheduleProvider(this));
        return super.onCreateOptionsMenu(menu);
    }


    @SuppressLint("RestrictedApi") // setOptionalIconsVisible
    private void displayIconsOnTv(Menu menu) {
        if (!AndroidUiUtils.isRunningOnTv(this)) {
            return;
        }

        try {
            if (menu instanceof MenuBuilder) {
                MenuBuilder m = (MenuBuilder) menu;
                m.setOptionalIconsVisible(true);
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                for (int i = 0; i < menu.size(); i++) {
                    MenuItem m = menu.getItem(i);

                    if (m == null || isFlagResource(m.getItemId())) {
                        continue;
                    }

                    int color = Themes.getColorFromAttr(this, R.attr.navDrawerItemColor);
                    MenuItemCompat.setIconTintList(m, ColorStateList.valueOf(color));
                }
            }

        } catch (Exception | Error e) {
            Timber.w(e, "Failed to display icons");
        }
    }


    private boolean isFlagResource(int itemId) {
        return itemId == R.id.action_flag_four
                || itemId == R.id.action_flag_three
                || itemId == R.id.action_flag_two
                || itemId == R.id.action_flag_one;
    }
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (mProcessor.onKeyDown(keyCode, event) || super.onKeyDown(keyCode, event)) {
            return true;
        }

        if (!AndroidUiUtils.isRunningOnTv(this)) {
            return false;
        }

        // Process DPAD Up/Down to focus the TV Controls
        if (keyCode != KeyEvent.KEYCODE_DPAD_DOWN && keyCode != KeyEvent.KEYCODE_DPAD_UP) {
            return false;
        }

        // HACK: This shouldn't be required, as the navigation should handle this.
        if (isDrawerOpen()) {
            return false;
        }


        View view = keyCode == KeyEvent.KEYCODE_DPAD_UP ? findViewById(R.id.tv_nav_view) : findViewById(R.id.answer_options_layout);
        // HACK: We should be performing this in the base class, or allowing the view to be focused by the keyboard.
        // I couldn't get either to work
        if (view == null) {
            return false;
        }

        view.requestFocus();
        return true;
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (answerFieldIsFocused()) {
            return super.onKeyUp(keyCode, event);
        }
        if (mProcessor.onKeyUp(keyCode, event)) {
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }


    private <T extends ActionProvider & SubMenuProvider> void setupSubMenu(Menu menu, @IdRes int parentMenu, T subMenuProvider) {
        if (!AndroidUiUtils.isRunningOnTv(this)) {
            MenuItemCompat.setActionProvider(menu.findItem(parentMenu), subMenuProvider);
            return;
        }

        // Don't do anything if the menu is hidden (bury for example)
        if (!subMenuProvider.hasSubMenu()) {
            return;
        }

        // 7227 - If we're running on a TV, then we can't show submenus until AOSP is fixed
        menu.removeItem(parentMenu);
        int count = menu.size();
        // move the menu to the bottom of the page
        getMenuInflater().inflate(subMenuProvider.getSubMenu(), menu);
        for (int i = 0; i < menu.size() - count; i++) {
            MenuItem item = menu.getItem(count + i);
            item.setOnMenuItemClickListener(subMenuProvider);
        }
    }


    @Override
    protected boolean canAccessScheduler() {
        return true;
    }


    @Override
    protected void performReload() {
        getCol().getSched().deferReset();
        TaskManager.launchCollectionTask(new CollectionTask.GetCard(), mAnswerCardHandler(false));
    }


    @Override
    protected void displayAnswerBottomBar() {
        super.displayAnswerBottomBar();
        int buttonCount;
        try {
            buttonCount = mSched.answerButtons(mCurrentCard);
        } catch (RuntimeException e) {
            AnkiDroidApp.sendExceptionReport(e, "AbstractReviewer-showEaseButtons");
            closeReviewer(DeckPicker.RESULT_DB_ERROR, true);
            return;
        }

        // Set correct label and background resource for each button
        // Note that it's necessary to set the resource dynamically as the ease2 / ease3 buttons
        // (which libanki expects ease to be 2 and 3) can either be hard, good, or easy - depending on num buttons shown
        int[] backgroundIds;
        if (animationEnabled()) {
            backgroundIds = new int [] {
                    R.attr.againButtonRippleRef,
                    R.attr.hardButtonRippleRef,
                    R.attr.goodButtonRippleRef,
                    R.attr.easyButtonRippleRef};
        } else {
            backgroundIds = new int [] {
                    R.attr.againButtonRef,
                    R.attr.hardButtonRef,
                    R.attr.goodButtonRef,
                    R.attr.easyButtonRef};
        }
        final int[] background = Themes.getResFromAttr(this, backgroundIds);
        final int[] textColor = Themes.getColorFromAttr(this, new int [] {
                R.attr.againButtonTextColor,
                R.attr.hardButtonTextColor,
                R.attr.goodButtonTextColor,
                R.attr.easyButtonTextColor});
        mEase1Layout.setVisibility(View.VISIBLE);
        mEase1Layout.setBackgroundResource(background[0]);
        mEase4Layout.setBackgroundResource(background[3]);
        switch (buttonCount) {
            case 2:
                // Ease 2 is "good"
                mEase2Layout.setVisibility(View.VISIBLE);
                mEase2Layout.setBackgroundResource(background[2]);
                mEase2.setText(R.string.ease_button_good);
                mEase2.setTextColor(textColor[2]);
                mNext2.setTextColor(textColor[2]);
                mEase2Layout.requestFocus();
                break;
            case 3:
                // Ease 2 is good
                mEase2Layout.setVisibility(View.VISIBLE);
                mEase2Layout.setBackgroundResource(background[2]);
                mEase2.setText(R.string.ease_button_good);
                mEase2.setTextColor(textColor[2]);
                mNext2.setTextColor(textColor[2]);
                // Ease 3 is easy
                mEase3Layout.setVisibility(View.VISIBLE);
                mEase3Layout.setBackgroundResource(background[3]);
                mEase3.setText(R.string.ease_button_easy);
                mEase3.setTextColor(textColor[3]);
                mNext3.setTextColor(textColor[3]);
                mEase2Layout.requestFocus();
                break;
            default:
                mEase2Layout.setVisibility(View.VISIBLE);
                // Ease 2 is "hard"
                mEase2Layout.setVisibility(View.VISIBLE);
                mEase2Layout.setBackgroundResource(background[1]);
                mEase2.setText(R.string.ease_button_hard);
                mEase2.setTextColor(textColor[1]);
                mNext2.setTextColor(textColor[1]);
                mEase2Layout.requestFocus();
                // Ease 3 is good
                mEase3Layout.setVisibility(View.VISIBLE);
                mEase3Layout.setBackgroundResource(background[2]);
                mEase3.setText(R.string.ease_button_good);
                mEase3.setTextColor(textColor[2]);
                mNext3.setTextColor(textColor[2]);
                mEase4Layout.setVisibility(View.VISIBLE);
                mEase3Layout.requestFocus();
                break;
        }

        // Show next review time
        if (shouldShowNextReviewTime()) {
            mNext1.setText(mSched.nextIvlStr(this, mCurrentCard, Consts.BUTTON_ONE));
            mNext2.setText(mSched.nextIvlStr(this, mCurrentCard, Consts.BUTTON_TWO));
            if (buttonCount > 2) {
                mNext3.setText(mSched.nextIvlStr(this, mCurrentCard, Consts.BUTTON_THREE));
            }
            if (buttonCount > 3) {
                mNext4.setText(mSched.nextIvlStr(this, mCurrentCard, Consts.BUTTON_FOUR));
            }
        }
    }

    @Override
    protected SharedPreferences restorePreferences() {
        SharedPreferences preferences = super.restorePreferences();
        mPrefHideDueCount = preferences.getBoolean("hideDueCount", false);
        mPrefShowETA = preferences.getBoolean("showETA", true);
        this.mProcessor.setup();
        mPrefFullscreenReview = Integer.parseInt(preferences.getString("fullscreenMode", "0")) > 0;
        mActionButtons.setup(preferences);
        return preferences;
    }

    @Override
    protected void updateActionBar() {
        super.updateActionBar();
        updateScreenCounts();
    }

    protected void updateScreenCounts() {
        if (mCurrentCard == null) return;
        super.updateActionBar();
        ActionBar actionBar = getSupportActionBar();
        Counts counts = mSched.counts(mCurrentCard);

        if (actionBar != null) {
            if (mPrefShowETA) {
                mEta = mSched.eta(counts, false);
                actionBar.setSubtitle(Utils.remainingTime(AnkiDroidApp.getInstance(), mEta * 60));
            }
        }


        mNewCount = new SpannableString(String.valueOf(counts.getNew()));
        mLrnCount = new SpannableString(String.valueOf(counts.getLrn()));
        mRevCount = new SpannableString(String.valueOf(counts.getRev()));
        if (mPrefHideDueCount) {
            mRevCount = new SpannableString("???");
        }

        switch (mSched.countIdx(mCurrentCard)) {
            case NEW:
                mNewCount.setSpan(new UnderlineSpan(), 0, mNewCount.length(), 0);
                break;
            case LRN:
                mLrnCount.setSpan(new UnderlineSpan(), 0, mLrnCount.length(), 0);
                break;
            case REV:
                mRevCount.setSpan(new UnderlineSpan(), 0, mRevCount.length(), 0);
                break;
            default:
                Timber.w("Unknown card type %s", mSched.countIdx(mCurrentCard));
                break;
        }

        mTextBarNew.setText(mNewCount);
        mTextBarLearn.setText(mLrnCount);
        mTextBarReview.setText(mRevCount);
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
        delayedHide(100);
        super.displayCardQuestion();
    }

    @Override
    protected void displayCardAnswer() {
        delayedHide(100);
        super.displayCardAnswer();
    }

    @Override
    protected void initLayout() {
        mTextBarNew = findViewById(R.id.new_number);
        mTextBarLearn = findViewById(R.id.learn_number);
        mTextBarReview = findViewById(R.id.review_number);

        super.initLayout();

        if (!mShowRemainingCardCount) {
            mTextBarNew.setVisibility(View.GONE);
            mTextBarLearn.setVisibility(View.GONE);
            mTextBarReview.setVisibility(View.GONE);
        }
    }

    @Override
    protected void switchTopBarVisibility(int visible) {
        super.switchTopBarVisibility(visible);
        if (mShowRemainingCardCount) {
            mTextBarNew.setVisibility(visible);
            mTextBarLearn.setVisibility(visible);
            mTextBarReview.setVisibility(visible);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();

        if (!isFinishing() && colIsOpen() && mSched != null) {
            WidgetStatus.update(this);
        }
        UIUtils.saveCollectionInBackground();
    }


    @Override
    protected void initControls() {
        super.initControls();
        if (mPrefWhiteboard) {
            setWhiteboardVisibility(mShowWhiteboard);
        }
        if (mShowRemainingCardCount) {
            mTextBarNew.setVisibility(View.VISIBLE);
            mTextBarLearn.setVisibility(View.VISIBLE);
            mTextBarReview.setVisibility(View.VISIBLE);
        }
    }

    protected void restoreCollectionPreferences() {
        super.restoreCollectionPreferences();
        mShowRemainingCardCount = getCol().getConf().getBoolean("dueCounts");
    }

    @Override
    protected boolean onSingleTap() {
        if (mPrefFullscreenReview && isImmersiveSystemUiVisible(this)) {
            delayedHide(INITIAL_HIDE_DELAY);
            return true;
        }
        return false;
    }

    @Override
    protected void onFling() {
        if (mPrefFullscreenReview && isImmersiveSystemUiVisible(this)) {
            delayedHide(INITIAL_HIDE_DELAY);
        }
    }


    protected final Handler mFullScreenHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            if (mPrefFullscreenReview) {
                setFullScreen(Reviewer.this);
            }
        }
    };

    /** Hide the navigation if in full-screen mode after a given period of time */
    protected void delayedHide(int delayMillis) {
        Timber.d("Fullscreen delayed hide in %dms", delayMillis);
        mFullScreenHandler.removeMessages(0);
        mFullScreenHandler.sendEmptyMessageDelayed(0, delayMillis);
    }


    private void setWhiteboardEnabledState(boolean state) {
        mPrefWhiteboard = state;
        MetaDB.storeWhiteboardState(this, getParentDid(), state);
        if (state && mWhiteboard == null) {
            createWhiteboard();
        }
    }

    private static final int FULLSCREEN_ALL_GONE = 2;

    private void setFullScreen(final AbstractFlashcardViewer a) {
        // Set appropriate flags to enable Sticky Immersive mode.
        a.getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        //| View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION // temporarily disabled due to #5245
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LOW_PROFILE
                        | View.SYSTEM_UI_FLAG_IMMERSIVE
        );
        // Show / hide the Action bar together with the status bar
        SharedPreferences prefs = AnkiDroidApp.getSharedPrefs(a);
        final int fullscreenMode = Integer.parseInt(prefs.getString("fullscreenMode", "0"));
        a.getWindow().setStatusBarColor(Themes.getColorFromAttr(a, R.attr.colorPrimaryDark));
        View decorView = a.getWindow().getDecorView();
        decorView.setOnSystemUiVisibilityChangeListener
                (flags -> {
                    final View toolbar = a.findViewById(R.id.toolbar);
                    final View answerButtons = a.findViewById(R.id.answer_options_layout);
                    final View topbar = a.findViewById(R.id.top_bar);
                    if (toolbar == null || topbar == null || answerButtons == null) {
                        return;
                    }
                    // Note that system bars will only be "visible" if none of the
                    // LOW_PROFILE, HIDE_NAVIGATION, or FULLSCREEN flags are set.
                    boolean visible = (flags & View.SYSTEM_UI_FLAG_HIDE_NAVIGATION) == 0;
                    Timber.d("System UI visibility change. Visible: %b", visible);
                    if (visible) {
                        showViewWithAnimation(toolbar);
                        if (fullscreenMode >= FULLSCREEN_ALL_GONE) {
                            showViewWithAnimation(topbar);
                            showViewWithAnimation(answerButtons);
                        }
                    } else {
                        hideViewWithAnimation(toolbar);
                        if (fullscreenMode >= FULLSCREEN_ALL_GONE) {
                            hideViewWithAnimation(topbar);
                            hideViewWithAnimation(answerButtons);
                        }
                    }
                });
    }

    private static final int ANIMATION_DURATION = 200;
    private static final float TRANSPARENCY = 0.90f;


    private void showViewWithAnimation(final View view) {
        view.setAlpha(0.0f);
        view.setVisibility(View.VISIBLE);
        view.animate().alpha(TRANSPARENCY).setDuration(ANIMATION_DURATION).setListener(null);
    }

    private void hideViewWithAnimation(final View view) {
        view.animate()
                .alpha(0f)
                .setDuration(ANIMATION_DURATION)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        view.setVisibility(View.GONE);
                    }
                });
    }

    private boolean isImmersiveSystemUiVisible(AnkiActivity activity) {
        return (activity.getWindow().getDecorView().getSystemUiVisibility() & View.SYSTEM_UI_FLAG_HIDE_NAVIGATION) == 0;
    }

    private void createWhiteboard() {
        SharedPreferences sharedPrefs = AnkiDroidApp.getSharedPrefs(this);
        mWhiteboard = new Whiteboard(this, isInNightMode());
        FrameLayout.LayoutParams lp2 = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        mWhiteboard.setLayoutParams(lp2);
        FrameLayout fl = findViewById(R.id.whiteboard);
        fl.addView(mWhiteboard);

        // We use the pen color of the selected deck at the time the whiteboard is enabled.
        // This is how all other whiteboard settings are
        Integer whiteboardPenColor = MetaDB.getWhiteboardPenColor(this, getParentDid()).fromPreferences(sharedPrefs);
        if (whiteboardPenColor != null) {
            mWhiteboard.setPenColor(whiteboardPenColor);
        }

        mWhiteboard.setOnPaintColorChangeListener(color -> MetaDB.storeWhiteboardPenColor(this, getParentDid(), !CardAppearance.isInNightMode(sharedPrefs), color));

        mWhiteboard.setOnTouchListener((v, event) -> {
            //If the whiteboard is currently drawing, and triggers the system UI to show, we want to continue drawing.
            if (!mWhiteboard.isCurrentlyDrawing() && (!mShowWhiteboard || (mPrefFullscreenReview
                    && isImmersiveSystemUiVisible(Reviewer.this)))) {
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
        MetaDB.storeWhiteboardVisibility(this, getParentDid(), state);
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
            if (gestureSwipeUp != COMMAND_NOTHING ||
                    gestureSwipeDown != COMMAND_NOTHING ||
                    gestureSwipeRight != COMMAND_NOTHING) {
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
     * @return true if there is another card of same note that could be dismissed
     */
    private boolean suspendNoteAvailable() {
        if (mCurrentCard == null || isControlBlocked()) {
            return false;
        }
        // whether there exists a sibling not buried.
        return getCol().getDb().queryScalar("select 1 from cards where nid = ? and id != ? and queue != " + Consts.QUEUE_TYPE_SUSPENDED + " limit 1",
                mCurrentCard.getNid(), mCurrentCard.getId()) == 1;
    }

    private boolean buryNoteAvailable() {
        if (mCurrentCard == null || isControlBlocked()) {
            return false;
        }
        // Whether there exists a sibling which is neither susbended nor buried
        return getCol().getDb().queryScalar("select 1 from cards where nid = ? and id != ? and queue >=  " + Consts.QUEUE_TYPE_NEW + " limit 1",
                mCurrentCard.getNid(), mCurrentCard.getId()) == 1;
    }


    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    @CheckResult
    Whiteboard getWhiteboard() {
        return mWhiteboard;
    }

    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    public AudioView getAudioView() {
        return mMicToolBar;
    }

    /**
     * Inner class which implements the submenu for the Suspend button
     */
    class SuspendProvider extends ActionProvider implements SubMenuProvider {
        public SuspendProvider(Context context) {
            super(context);
        }

        @Override
        public View onCreateActionView() {
            return null;  // Just return null for a simple dropdown menu
        }


        @Override
        public int getSubMenu() {
            return R.menu.reviewer_suspend;
        }


        @Override
        public boolean hasSubMenu() {
            return suspendNoteAvailable();
        }

        @Override
        public void onPrepareSubMenu(SubMenu subMenu) {
            subMenu.clear();
            getMenuInflater().inflate(getSubMenu(), subMenu);
            for (int i = 0; i < subMenu.size(); i++) {
                subMenu.getItem(i).setOnMenuItemClickListener(this);
            }
        }

        @Override
        public boolean onMenuItemClick(MenuItem item) {
            int itemId = item.getItemId();
            if (itemId == R.id.action_suspend_card) {
                dismiss(new CollectionTask.SuspendCard(mCurrentCard));
                return true;
            } else if (itemId == R.id.action_suspend_note) {
                dismiss(new CollectionTask.SuspendNote(mCurrentCard));
                return true;
            }
            return false;
        }
    }

    /**
     * Inner class which implements the submenu for the Bury button
     */
    class BuryProvider extends ActionProvider implements SubMenuProvider {
        public BuryProvider(Context context) {
            super(context);
        }

        @Override
        public View onCreateActionView() {
            return null;    // Just return null for a simple dropdown menu
        }


        @Override
        public int getSubMenu() {
            return R.menu.reviewer_bury;
        }


        @Override
        public boolean hasSubMenu() {
            return buryNoteAvailable();
        }

        @Override
        public void onPrepareSubMenu(SubMenu subMenu) {
            subMenu.clear();
            getMenuInflater().inflate(getSubMenu(), subMenu);
            for (int i = 0; i < subMenu.size(); i++) {
                subMenu.getItem(i).setOnMenuItemClickListener(this);
            }
        }

        @Override
        public boolean onMenuItemClick(MenuItem item) {
            int itemId = item.getItemId();
            if (itemId == R.id.action_bury_card) {
                dismiss(new CollectionTask.BuryCard(mCurrentCard));
                return true;
            } else if (itemId == R.id.action_bury_note) {
                dismiss(new CollectionTask.BuryNote(mCurrentCard));
                return true;
            }
            return false;
        }
    }


    /**
     * Inner class which implements the submenu for the Schedule button
     */
    class ScheduleProvider extends ActionProvider implements SubMenuProvider {
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
            getMenuInflater().inflate(getSubMenu(), subMenu);
            for (int i = 0; i < subMenu.size(); i++) {
                subMenu.getItem(i).setOnMenuItemClickListener(this);
            }
        }

        @Override
        public boolean onMenuItemClick(MenuItem item) {
            int itemId = item.getItemId();
            if (itemId == R.id.action_reschedule_card) {
                showRescheduleCardDialog();
                return true;
            } else if (itemId == R.id.action_reset_card_progress) {
                showResetCardDialog();
                return true;
            }
            return false;
        }


        @Override
        public int getSubMenu() {
            return R.menu.reviewer_schedule;
        }
    }

    private interface SubMenuProvider extends MenuItem.OnMenuItemClickListener {
        @MenuRes int getSubMenu();
        boolean hasSubMenu();
    }

    public ReviewerJavaScriptFunction javaScriptFunction() {
        return new ReviewerJavaScriptFunction();
    }

    public class ReviewerJavaScriptFunction extends JavaScriptFunction {
        @JavascriptInterface
        @Override
        public String ankiGetNewCardCount() {
            return mNewCount.toString();
        }

        @JavascriptInterface
        @Override
        public String ankiGetLrnCardCount() {
            return mLrnCount.toString();
        }

        @JavascriptInterface
        @Override
        public String ankiGetRevCardCount() {
            return mRevCount.toString();
        }

        @JavascriptInterface
        @Override
        public int ankiGetETA() {
            return mEta;
        }
    }
}
