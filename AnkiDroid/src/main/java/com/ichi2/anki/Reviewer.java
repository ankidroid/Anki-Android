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
import android.widget.ImageView;
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
import androidx.core.content.res.ResourcesCompat;
import androidx.core.view.ActionProvider;
import androidx.core.view.MenuItemCompat;
import androidx.vectordrawable.graphics.drawable.VectorDrawableCompat;

import com.ichi2.anki.cardviewer.CardAppearance;
import com.ichi2.anki.cardviewer.Gesture;
import com.ichi2.anki.cardviewer.ViewerCommand;
import com.ichi2.anki.dialogs.ConfirmationDialog;
import com.ichi2.anki.multimediacard.AudioView;
import com.ichi2.anki.dialogs.RescheduleDialog;
import com.ichi2.anki.reviewer.AnswerButtons;
import com.ichi2.anki.reviewer.AnswerTimer;
import com.ichi2.anki.reviewer.AutomaticAnswerAction;
import com.ichi2.anki.reviewer.CardMarker;
import com.ichi2.anki.reviewer.FullScreenMode;
import com.ichi2.anki.reviewer.PeripheralKeymap;
import com.ichi2.anki.reviewer.ReviewerUi;
import com.ichi2.anki.servicelayer.NoteService;
import com.ichi2.anki.servicelayer.SchedulerService;
import com.ichi2.anki.servicelayer.SchedulerService.NextCard;
import com.ichi2.anki.servicelayer.TaskListenerBuilder;
import com.ichi2.anki.workarounds.FirefoxSnackbarWorkaround;
import com.ichi2.anki.reviewer.ActionButtons;
import com.ichi2.libanki.Card;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Consts;
import com.ichi2.libanki.Decks;
import com.ichi2.libanki.Utils;
import com.ichi2.libanki.sched.Counts;
import com.ichi2.themes.Themes;
import com.ichi2.utils.AndroidUiUtils;
import com.ichi2.utils.Computation;
import com.ichi2.utils.HandlerUtils;
import com.ichi2.utils.Permissions;
import com.ichi2.utils.ViewGroupUtils;
import com.ichi2.widget.WidgetStatus;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import kotlin.Unit;
import timber.log.Timber;

import static com.ichi2.anki.cardviewer.ViewerCommand.COMMAND_EXIT;
import static com.ichi2.anki.cardviewer.ViewerCommand.COMMAND_NOTHING;
import static com.ichi2.anki.reviewer.CardMarker.*;
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

    protected AnswerTimer mAnswerTimer;

    private boolean mPrefHideDueCount;

    // Whiteboard
    protected boolean mPrefWhiteboard;
    protected Whiteboard mWhiteboard;

    // Record Audio
    /** File of the temporary mic record **/
    protected AudioView mMicToolBar;
    protected String mTempAudioPath;

    // ETA
    private int mEta;
    private boolean mPrefShowETA;

    /** Handle Mark/Flag state of cards */
    private CardMarker mCardMarker;

    // Preferences from the collection
    private boolean mShowRemainingCardCount;

    private final ActionButtons mActionButtons = new ActionButtons(this);


    @VisibleForTesting
    protected final PeripheralKeymap mProcessor = new PeripheralKeymap(this, this);
    
    private final Onboarding.Reviewer mOnboarding = new Onboarding.Reviewer(this);

    protected <T extends Computation<? extends NextCard<? extends Card[]>>> TaskListenerBuilder<Unit, T>
    scheduleCollectionTaskHandler(@PluralsRes int toastResourceId) {
        return nextCardHandler().alsoExecuteAfter(result -> {
            // BUG: If the method crashes, this will crash
            invalidateOptionsMenu();
            int cardCount = result.getValue().getResult().length;
            UIUtils.showThemedToast(this,
                    getResources().getQuantityString(toastResourceId, cardCount, cardCount), true);
        });
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

        mColorPalette = findViewById(R.id.whiteboard_editor);
        mAnswerTimer = new AnswerTimer(findViewById(R.id.card_time));

        startLoadingCollection();
    }


    @Override
    protected void onPause() {
        mAnswerTimer.pause();
        super.onPause();
    }

    @Override
    protected void onResume() {
        mAnswerTimer.resume();
        super.onResume();
    }

    protected int getFlagToDisplay() {
        int actualValue = mCurrentCard.userFlag();
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

    protected void onMark(Card card) {
        if (card == null) {
            return;
        }
        NoteService.toggleMark(card.note());
        refreshActionBar();
        onMarkChanged();
    }


    private void onMarkChanged() {
        if (mCurrentCard == null) {
            return;
        }

        mCardMarker.displayMark(shouldDisplayMark());
    }


    protected void onFlag(Card card, int flag) {
        if (card == null) {
            return;
        }
        card.setUserFlag(flag);
        card.flush();
        refreshActionBar();
        /* Following code would allow to update value of {{cardFlag}}.
           Anki does not update this value when a flag is changed, so
           currently this code would do something that anki itself
           does not do. I hope in the future Anki will correct that
           and this code may becomes useful.

        card._getQA(true); //force reload. Useful iff {{cardFlag}} occurs in the template
        if (sDisplayAnswer) {
            displayCardAnswer();
        } else {
            displayCardQuestion();
            } */
        onFlagChanged();
    }


    private void onFlagChanged() {
        if (mCurrentCard == null) {
            return;
        }
        mCardMarker.displayFlag(getFlagToDisplay());
    }


    private void selectDeckFromExtra() {
        Bundle extras = getIntent().getExtras();
        if (extras == null || !extras.containsKey("deckId")) {
            // deckId is not set, load default
            return;
        }
        long did = extras.getLong("deckId", Long.MIN_VALUE);

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
    protected int getContentViewAttr(FullScreenMode fullscreenMode) {
        switch (fullscreenMode) {
            case BUTTONS_ONLY:
                return R.layout.reviewer_fullscreen;
            case FULLSCREEN_ALL_GONE:
                return R.layout.reviewer_fullscreen_noanswers;
            default:
                return R.layout.reviewer;
        }
    }

    @Override
    protected boolean fitsSystemWindows() {
        return !getFullscreenMode().isFullScreenReview();
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
        new SchedulerService.GetCard().runWithHandler(answerCardHandler(false));

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
                buryCard();
            }
        } else if (itemId == R.id.action_suspend) {
            Timber.i("Reviewer:: Suspend button pressed");
            if (!MenuItemCompat.getActionProvider(item).hasSubMenu()) {
                Timber.d("Suspend card due to no submenu");
                suspendCard();
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
                    String savedWhiteboardFileName = mWhiteboard.saveWhiteboard(getCol().getTime()).getPath();
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
        } else if (itemId == R.id.action_flag_five) {
            Timber.i("Reviewer:: Flag five");
            onFlag(mCurrentCard, FLAG_PINK);
        } else if (itemId == R.id.action_flag_six) {
            Timber.i("Reviewer:: Flag six");
            onFlag(mCurrentCard, FLAG_TURQUOISE);
        } else if (itemId == R.id.action_flag_seven) {
            Timber.i("Reviewer:: Flag seven");
            onFlag(mCurrentCard, FLAG_PURPLE);
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


    @Override
    protected void updateForNewCard() {
        super.updateForNewCard();
        if (mPrefWhiteboard && mWhiteboard != null) {
            mWhiteboard.clear();
        }
    }


    @Override
    protected void unblockControls() {
        if (mPrefWhiteboard && mWhiteboard != null) {
            mWhiteboard.setEnabled(true);
        }

        super.unblockControls();
    }


    @Override
    protected void blockControls(boolean quick) {
        if (mPrefWhiteboard && mWhiteboard != null) {
            mWhiteboard.setEnabled(false);
        }
        super.blockControls(quick);
    }


    @Override
    protected void closeReviewer(int result, boolean saveDeck) {
        // Stop the mic recording if still pending
        if (mMicToolBar != null) {
            mMicToolBar.notifyStopRecord();
        }
        // Remove the temporary audio file
        if (mTempAudioPath != null) {
            File tempAudioPathToDelete = new File(mTempAudioPath);
            if (tempAudioPathToDelete.exists()) {
                tempAudioPathToDelete.delete();
            }
        }

        super.closeReviewer(result, saveDeck);
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
            mMicToolBar = AudioView.createRecorderInstance(this, R.drawable.ic_play_arrow_white_24dp, R.drawable.ic_pause_white_24dp,
                        R.drawable.ic_stop_white_24dp, R.drawable.ic_rec, R.drawable.ic_rec_stop, mTempAudioPath);
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
        Consumer<Integer> runnable = days -> {
            List<Long> cardIds = Collections.singletonList(mCurrentCard.getId());
            new SchedulerService.RescheduleCards(cardIds, days).runWithHandler(scheduleCollectionTaskHandler(R.plurals.reschedule_cards_dialog_acknowledge));
        };
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
            List<Long> cardIds = Collections.singletonList(mCurrentCard.getId());
            new SchedulerService.ResetCards(cardIds).runWithHandler(scheduleCollectionTaskHandler(R.plurals.reset_cards_dialog_acknowledge));
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
    public boolean onMenuOpened(int featureId, Menu menu) {
        HandlerUtils.postOnNewHandler(() -> {
            for (int i = 0; i < menu.size(); i++) {
                MenuItem menuItem = menu.getItem(i);
                shouldUseDefaultColor(menuItem);
            }
        });
        return super.onMenuOpened(featureId, menu);
    }

    /**
     * This Method changes the color of icon if user taps in overflow button.
     */
    private void shouldUseDefaultColor(MenuItem menuItem) {
        Drawable drawable = menuItem.getIcon();

        if (drawable != null && !menuItem.hasSubMenu() && !isFlagResource(menuItem.getItemId())) {
            drawable.mutate();
            drawable.setTint(ResourcesCompat.getColor(getResources(), R.color.material_blue_600, null));
        }
    }


    @Override
    public void onPanelClosed(int featureId, @NonNull Menu menu) {
        HandlerUtils.postDelayedOnNewHandler(this::refreshActionBar, 100);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // NOTE: This is called every time a new question is shown via invalidate options menu
        getMenuInflater().inflate(R.menu.reviewer, menu);

        displayIconsOnTv(menu);
        displayIcons(menu);

        mActionButtons.setCustomButtonsStatus(menu);
        int alpha = (getControlBlocked() != ReviewerUi.ControlBlock.SLOW) ? Themes.ALPHA_ICON_ENABLED_LIGHT : Themes.ALPHA_ICON_DISABLED_LIGHT ;
        MenuItem markCardIcon = menu.findItem(R.id.action_mark_card);
        if (mCurrentCard != null && NoteService.isMarked(mCurrentCard.note())) {
            markCardIcon.setTitle(R.string.menu_unmark_note).setIcon(R.drawable.ic_star_white);
        } else {
            markCardIcon.setTitle(R.string.menu_mark_note).setIcon(R.drawable.ic_star_border_white);
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
                    case 5:
                        flag_icon.setIcon(R.drawable.ic_flag_pink);
                        break;
                    case 6:
                        flag_icon.setIcon(R.drawable.ic_flag_turquoise);
                        break;
                    case 7:
                        flag_icon.setIcon(R.drawable.ic_flag_purple);
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
            undoIconId = R.drawable.eraser;
            undoEnabled = !mWhiteboard.undoEmpty();
        } else {
            // We can arrive here even if `mShowWhiteboard &&
            // mWhiteboard != null` if no stroke had ever been made
            undoIconId = R.drawable.ic_undo_white;
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

        if (undoEnabled) {
            mOnboarding.onUndoButtonEnabled();
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

            Drawable whiteboardIcon = ContextCompat.getDrawable(this, R.drawable.ic_gesture_white).mutate();
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
        if (mTTS.isEnabled() && !mActionButtons.getStatus().selectTtsIsDisabled()) {
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
            suspend_icon.setIcon(R.drawable.ic_pause_circle_outline);
            suspend_icon.setTitle(R.string.menu_suspend_card);
        }
        if (buryNoteAvailable()) {
            bury_icon.setIcon(R.drawable.ic_flip_to_back_dropdown);
            bury_icon.setTitle(R.string.menu_bury);
        } else {
            bury_icon.setIcon(R.drawable.ic_flip_to_back_white);
            bury_icon.setTitle(R.string.menu_bury_card);
        }
        alpha = (getControlBlocked() != ReviewerUi.ControlBlock.SLOW) ? Themes.ALPHA_ICON_ENABLED_LIGHT : Themes.ALPHA_ICON_DISABLED_LIGHT ;
        bury_icon.getIcon().mutate().setAlpha(alpha);
        suspend_icon.getIcon().mutate().setAlpha(alpha);

        setupSubMenu(menu, R.id.action_schedule, new ScheduleProvider(this));
        mOnboarding.onCreate();
        return super.onCreateOptionsMenu(menu);
    }


    @SuppressLint("RestrictedApi")
    private void displayIcons(Menu menu) {
        try {
            if (menu instanceof MenuBuilder) {
                MenuBuilder m = (MenuBuilder) menu;
                m.setOptionalIconsVisible(true);
            }
        }catch (Exception | Error e) {
            Timber.w(e, "Failed to display icons in Over flow menu");
        }
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
        return itemId == R.id.action_flag_seven
                || itemId == R.id.action_flag_six
                || itemId == R.id.action_flag_five
                || itemId == R.id.action_flag_four
                || itemId == R.id.action_flag_three
                || itemId == R.id.action_flag_two
                || itemId == R.id.action_flag_one;
    }
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (answerFieldIsFocused()) {
            return super.onKeyDown(keyCode, event);
        }

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
        new SchedulerService.GetCard().runWithHandler(answerCardHandler(false));
    }


    @Override
    protected void displayAnswerBottomBar() {
        super.displayAnswerBottomBar();
        mOnboarding.onAnswerShown();
        int buttonCount;
        try {
            buttonCount = getButtonCount();
        } catch (RuntimeException e) {
            AnkiDroidApp.sendExceptionReport(e, "AbstractReviewer-showEaseButtons");
            closeReviewer(DeckPicker.RESULT_DB_ERROR, true);
            return;
        }

        // Set correct label and background resource for each button
        // Note that it's necessary to set the resource dynamically as the ease2 / ease3 buttons
        // (which libanki expects ease to be 2 and 3) can either be hard, good, or easy - depending on num buttons shown
        final int[] background = AnswerButtons.getBackgroundColors(this);
        final int[] textColor = AnswerButtons.getTextColors(this);
        mEaseButton1.setVisibility(View.VISIBLE);
        mEaseButton1.setColor(background[0]);
        mEaseButton4.setColor(background[3]);
        switch (buttonCount) {
            case 2:
                // Ease 2 is "good"
                mEaseButton2.setup(background[2], textColor[2], R.string.ease_button_good);
                mEaseButton2.requestFocus();
                break;
            case 3:
                // Ease 2 is good
                mEaseButton2.setup(background[2], textColor[2], R.string.ease_button_good);
                // Ease 3 is easy
                mEaseButton3.setup(background[3], textColor[3], R.string.ease_button_easy);
                mEaseButton2.requestFocus();
                break;
            default:
                // Ease 2 is "hard"
                mEaseButton2.setup(background[1], textColor[1], R.string.ease_button_hard);
                mEaseButton2.requestFocus();
                // Ease 3 is good
                mEaseButton3.setup(background[2], textColor[2], R.string.ease_button_good);

                mEaseButton4.setVisibility(View.VISIBLE);
                mEaseButton3.requestFocus();
                break;
        }

        // Show next review time
        if (shouldShowNextReviewTime()) {
            mEaseButton1.setNextTime(mSched.nextIvlStr(this, mCurrentCard, Consts.BUTTON_ONE));
            mEaseButton2.setNextTime(mSched.nextIvlStr(this, mCurrentCard, Consts.BUTTON_TWO));
            if (buttonCount > 2) {
                mEaseButton3.setNextTime(mSched.nextIvlStr(this, mCurrentCard, Consts.BUTTON_THREE));
            }
            if (buttonCount > 3) {
                mEaseButton4.setNextTime(mSched.nextIvlStr(this, mCurrentCard, Consts.BUTTON_FOUR));
            }
        }
    }

    public int getButtonCount() {
        return mSched.answerButtons(mCurrentCard);
    }


    @Override
    public void automaticShowQuestion(@NonNull AutomaticAnswerAction action) {
        // explicitly do not call super
        if (mEaseButton1.canPerformClick()) {
            action.execute(this);
        }
    }


    @Override
    protected SharedPreferences restorePreferences() {
        SharedPreferences preferences = super.restorePreferences();
        mPrefHideDueCount = preferences.getBoolean("hideDueCount", false);
        mPrefShowETA = preferences.getBoolean("showETA", true);
        this.mProcessor.setup();
        mPrefFullscreenReview = FullScreenMode.isFullScreenReview(preferences);
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
        onFlagChanged();
        onMarkChanged();
    }


    @Override
    public void displayCardQuestion() {
        // show timer, if activated in the deck's preferences
        mAnswerTimer.setupForCard(mCurrentCard);
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

        // can't move this into onCreate due to mTopBarLayout
        ImageView mark = mTopBarLayout.findViewById(R.id.mark_icon);
        ImageView flag = mTopBarLayout.findViewById(R.id.flag_icon);
        mCardMarker = new CardMarker(mark, flag);
    }

    @Override
    protected void switchTopBarVisibility(int visible) {
        super.switchTopBarVisibility(visible);
        mAnswerTimer.setVisibility(visible);
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


    @Override
    public boolean executeCommand(@NonNull ViewerCommand which) {
        //noinspection ConstantConditions
        if (which == null) {
            Timber.w("command should not be null");
            which = COMMAND_NOTHING;
        }
        if (isControlBlocked() && which != COMMAND_EXIT) {
            return false;
        }

        switch (which) {
            case COMMAND_TOGGLE_FLAG_RED:
                toggleFlag(FLAG_RED);
                return true;
            case COMMAND_TOGGLE_FLAG_ORANGE:
                toggleFlag(FLAG_ORANGE);
                return true;
            case COMMAND_TOGGLE_FLAG_GREEN:
                toggleFlag(FLAG_GREEN);
                return true;
            case COMMAND_TOGGLE_FLAG_BLUE:
                toggleFlag(FLAG_BLUE);
                return true;
            case COMMAND_TOGGLE_FLAG_PINK:
                toggleFlag(FLAG_PINK);
                return true;
            case COMMAND_TOGGLE_FLAG_TURQUOISE:
                toggleFlag(FLAG_TURQUOISE);
                return true;
            case COMMAND_TOGGLE_FLAG_PURPLE:
                toggleFlag(FLAG_PURPLE);
                return true;
            case COMMAND_UNSET_FLAG:
                onFlag(mCurrentCard, FLAG_NONE);
                return true;
            case COMMAND_MARK:
                onMark(mCurrentCard);
                return true;
        }

        return super.executeCommand(which);
    }


    private void toggleFlag(@FlagDef int flag) {
        if (mCurrentCard.userFlag() == flag) {
            Timber.i("Toggle flag: unsetting flag");
            onFlag(mCurrentCard, FLAG_NONE);
        } else {
            Timber.i("Toggle flag: Setting flag to %d", flag);
            onFlag(mCurrentCard, flag);
        }
    }

    protected void restoreCollectionPreferences(Collection col) {
        super.restoreCollectionPreferences(col);
        mShowRemainingCardCount = col.get_config_boolean("dueCounts");
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


    @Override
    protected void onCardEdited(Card card) {
        super.onCardEdited(card);
        if (mPrefWhiteboard && mWhiteboard != null) {
            mWhiteboard.clear();
        }
        if (!sDisplayAnswer) {
            // Editing the card may reuse mCurrentCard. If so, the scheduler won't call startTimer() to reset the timer
            // QUESTIONABLE(legacy code): Only perform this if editing the question
            card.startTimer();
        }
    }

    protected final Handler mFullScreenHandler = new Handler(HandlerUtils.getDefaultLooper()) {
        @Override
        public void handleMessage(@NonNull Message msg) {
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

    @SuppressWarnings("deprecation") // #9332: UI Visibility -> Insets
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
        FullScreenMode fullscreenMode = FullScreenMode.fromPreference(prefs);
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
                        if (fullscreenMode.equals(FullScreenMode.FULLSCREEN_ALL_GONE)) {
                            showViewWithAnimation(topbar);
                            showViewWithAnimation(answerButtons);
                        }
                    } else {
                        hideViewWithAnimation(toolbar);
                        if (fullscreenMode.equals(FullScreenMode.FULLSCREEN_ALL_GONE)) {
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

    @SuppressWarnings("deprecation") // #9332: UI Visibility -> Insets
    private boolean isImmersiveSystemUiVisible(AnkiActivity activity) {
        return (activity.getWindow().getDecorView().getSystemUiVisibility() & View.SYSTEM_UI_FLAG_HIDE_NAVIGATION) == 0;
    }

    private void createWhiteboard() {
        SharedPreferences sharedPrefs = AnkiDroidApp.getSharedPrefs(this);
        mWhiteboard = Whiteboard.createInstance(this, true, this);

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
        if (mGestureProcessor.isBound(Gesture.SWIPE_UP, Gesture.SWIPE_DOWN, Gesture.SWIPE_RIGHT)) {
            mHasDrawerSwipeConflicts = true;
            super.disableDrawerSwipe();
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

    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    public boolean hasDrawerSwipeConflicts() {
        return mHasDrawerSwipeConflicts;
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
                return suspendCard();
            } else if (itemId == R.id.action_suspend_note) {
                return suspendNote();
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
                return buryCard();
            } else if (itemId == R.id.action_bury_note) {
                return buryNote();
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

    @Override
    public AnkiDroidJsAPI javaScriptFunction() {
        return new ReviewerJavaScriptFunction(this);
    }

    public class ReviewerJavaScriptFunction extends AnkiDroidJsAPI {
        public ReviewerJavaScriptFunction(@NonNull AbstractFlashcardViewer activity) {
            super(activity);
        }

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

        @JavascriptInterface
        @Override
        public String ankiGetNextTime1() {
            return mEaseButton1.getNextTime();
        }

        @JavascriptInterface
        @Override
        public String ankiGetNextTime2() {
            return mEaseButton2.getNextTime();
        }

        @JavascriptInterface
        @Override
        public String ankiGetNextTime3() {
            return mEaseButton3.getNextTime();
        }

        @JavascriptInterface
        @Override
        public String ankiGetNextTime4() {
            return mEaseButton4.getNextTime();
        }

    }
}
