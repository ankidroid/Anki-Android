/****************************************************************************************
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
 * this program. If not, see <http://www.gnu.org/licenses/>.                            *
 ****************************************************************************************/

package com.ichi2.anki;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.text.HtmlCompat;
import androidx.fragment.app.Fragment;
import androidx.appcompat.widget.Toolbar;

import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import com.afollestad.materialdialogs.MaterialDialog;
import com.ichi2.anim.ActivityTransitionAnimation;
import com.ichi2.anki.dialogs.customstudy.CustomStudyDialog;
import com.ichi2.anki.servicelayer.SchedulerService.NextCard;
import com.ichi2.anki.servicelayer.UndoService;
import com.ichi2.async.CollectionTask;
import com.ichi2.async.TaskListener;
import com.ichi2.async.TaskManager;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Consts;
import com.ichi2.libanki.Decks;
import com.ichi2.libanki.Utils;
import com.ichi2.libanki.Deck;
import com.ichi2.themes.StyledProgressDialog;
import com.ichi2.utils.Computation;
import com.ichi2.utils.FragmentFactoryUtils;
import com.ichi2.utils.HtmlUtils;

import kotlin.Unit;
import timber.log.Timber;

import static com.ichi2.anim.ActivityTransitionAnimation.Direction.*;

public class StudyOptionsFragment extends Fragment implements Toolbar.OnMenuItemClickListener {


    /**
     * Available options performed by other activities
     */
    private static final int BROWSE_CARDS = 3;
    private static final int STATISTICS = 4;
    private static final int DECK_OPTIONS = 5;

    /**
     * Constants for selecting which content view to display
     */
    private static final int CONTENT_STUDY_OPTIONS = 0;
    private static final int CONTENT_CONGRATS = 1;
    private static final int CONTENT_EMPTY = 2;

    // Threshold at which the total number of new cards is truncated by libanki
    private static final int NEW_CARD_COUNT_TRUNCATE_THRESHOLD = 99999;

    /**
     * Preferences
     */
    private int mCurrentContentView = CONTENT_STUDY_OPTIONS;

    /** Alerts to inform the user about different situations */
    private MaterialDialog mProgressDialog;

    /** Whether we are closing in order to go to the reviewer. If it's the case, UPDATE_VALUES_FROM_DECK should not be
     cancelled as the counts will be used in review. */
    private boolean mToReviewer = false;

    /**
     * UI elements for "Study Options" view
     */
    @Nullable
    private View mStudyOptionsView;
    private View mDeckInfoLayout;
    private Button mButtonStart;
    private TextView mTextDeckName;
    private TextView mTextDeckDescription;
    private TextView mTextTodayNew;
    private TextView mTextTodayLrn;
    private TextView mTextTodayRev;
    private TextView mTextNewTotal;
    private TextView mTextTotal;
    private TextView mTextETA;
    private TextView mTextCongratsMessage;
    private Toolbar mToolbar;

    // Flag to indicate if the fragment should load the deck options immediately after it loads
    private boolean mLoadWithDeckOptions;

    private boolean mFragmented;

    private Thread mFullNewCountThread = null;

    private StudyOptionsListener mListener;

    /**
     * Callbacks for UI events
     */
    private final View.OnClickListener mButtonClickListener = v -> {
        if (v.getId() == R.id.studyoptions_start) {
            Timber.i("StudyOptionsFragment:: start study button pressed");
            if (mCurrentContentView != CONTENT_CONGRATS) {
                openReviewer();
            } else {
                showCustomStudyContextMenu();
            }
        }
    };
    
    public interface StudyOptionsListener {
        void onRequireDeckListUpdate();
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        try {
            mListener = (StudyOptionsListener) context;
        } catch (ClassCastException e) {
            throw new ClassCastException(context.toString() + " must implement StudyOptionsListener");
        }
    }

    private void openFilteredDeckOptions() {
        openFilteredDeckOptions(false);
    }

    /**
     * Open the FilteredDeckOptions activity to allow the user to modify the parameters of the
     * filtered deck.
     * @param defaultConfig If true, signals to the FilteredDeckOptions activity that the filtered
     *                      deck has no options associated with it yet and should use a default
     *                      set of values.
     */
    private void openFilteredDeckOptions(boolean defaultConfig) {
        Intent i = new Intent(getActivity(), FilteredDeckOptions.class);
        i.putExtra("defaultConfig", defaultConfig);
        Timber.i("openFilteredDeckOptions()");
        mOnDeckOptionsActivityResult.launch(i);
        ActivityTransitionAnimation.slide(getActivity(), FADE);
    }


    /**
     * Get a new instance of the fragment.
     * @param withDeckOptions If true, the fragment will load a new activity on top of itself
     *                        which shows the current deck's options. Set to true when programmatically
     *                        opening a new filtered deck for the first time.
     */
    public static StudyOptionsFragment newInstance(boolean withDeckOptions) {
        StudyOptionsFragment f = new StudyOptionsFragment();
        Bundle args = new Bundle();
        args.putBoolean("withDeckOptions", withDeckOptions);
        f.setArguments(args);
        return f;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        Timber.i("onCreate()");
        super.onCreate(savedInstanceState);
        //If we're being restored, don't launch deck options again.
        if (savedInstanceState == null && getArguments() != null) {
            mLoadWithDeckOptions = getArguments().getBoolean("withDeckOptions");
        }
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        Timber.i("onCreateView()");
        if (container == null) {
            // Currently in a layout without a container, so no reason to create our view.
            return null;
        }
        View studyOptionsView = inflater.inflate(R.layout.studyoptions_fragment, container, false);
        mStudyOptionsView = studyOptionsView;
        mFragmented = getActivity().getClass() != StudyOptionsActivity.class;
        initAllContentViews(studyOptionsView);
        mToolbar = studyOptionsView.findViewById(R.id.studyOptionsToolbar);
        if (mToolbar != null) {
            mToolbar.inflateMenu(R.menu.study_options_fragment);
            configureToolbar();
        }
        refreshInterface(true);
        return studyOptionsView;
    }


    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mFullNewCountThread != null) {
            mFullNewCountThread.interrupt();
        }
        Timber.i("onDestroy()");
    }


    @Override
    public void onResume() {
        super.onResume();
        Timber.i("onResume()");
        refreshInterface(true);
    }


    private void closeStudyOptions(int result) {
        Activity a = getActivity();
        if (!mFragmented && a != null) {
            a.setResult(result);
            a.finish();
            ActivityTransitionAnimation.slide(a, END);
        } else if (a == null) {
            // getActivity() can return null if reference to fragment lingers after parent activity has been closed,
            // which is particularly relevant when using AsyncTasks.
            Timber.e("closeStudyOptions() failed due to getActivity() returning null");
        }
    }


    private void openReviewer() {
        Timber.i("openReviewer()");
        Intent reviewer = new Intent(getActivity(), Reviewer.class);
        if (mFragmented) {
            mToReviewer = true;
            Timber.i("openReviewer() fragmented mode");
            mOnRequestReviewActivityResult.launch(reviewer);
            // TODO #8913 should we finish the activity here? when it comes back from review it's dead and mToolbar is null and it crashes
        } else {
            // Go to DeckPicker after studying when not tablet
            reviewer.setFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT);
            startActivity(reviewer);
            getActivity().finish();
        }
        animateLeft();
    }


    private void animateLeft() {
        ActivityTransitionAnimation.slide(getActivity(), START);
    }


    private void initAllContentViews(@NonNull View studyOptionsView) {
        if (mFragmented) {
            studyOptionsView.findViewById(R.id.studyoptions_gradient).setVisibility(View.VISIBLE);
        }
        mDeckInfoLayout = studyOptionsView.findViewById(R.id.studyoptions_deckinformation);
        mTextDeckName = studyOptionsView.findViewById(R.id.studyoptions_deck_name);
        mTextDeckDescription = studyOptionsView.findViewById(R.id.studyoptions_deck_description);
        // make links clickable
        mTextDeckDescription.setMovementMethod(LinkMovementMethod.getInstance());
        mButtonStart = studyOptionsView.findViewById(R.id.studyoptions_start);
        mTextCongratsMessage = studyOptionsView.findViewById(R.id.studyoptions_congrats_message);
        // Code common to both fragmented and non-fragmented view
        mTextTodayNew = studyOptionsView.findViewById(R.id.studyoptions_new);
        mTextTodayLrn = studyOptionsView.findViewById(R.id.studyoptions_lrn);
        mTextTodayRev = studyOptionsView.findViewById(R.id.studyoptions_rev);
        mTextNewTotal = studyOptionsView.findViewById(R.id.studyoptions_total_new);
        mTextTotal = studyOptionsView.findViewById(R.id.studyoptions_total);
        mTextETA = studyOptionsView.findViewById(R.id.studyoptions_eta);
        mButtonStart.setOnClickListener(mButtonClickListener);
    }


    /**
     * Show the context menu for the custom study options
     */
    private void showCustomStudyContextMenu() {
        final AnkiActivity ankiActivity = ((AnkiActivity) requireActivity());
        CustomStudyDialog d = FragmentFactoryUtils.instantiate(ankiActivity, CustomStudyDialog.class);
        d.withArguments(CustomStudyDialog.CONTEXT_MENU_STANDARD, getCol().getDecks().selected());
        ankiActivity.showDialogFragment(d);
    }


    void setFragmentContentView(View newView) {
        ViewGroup parent = (ViewGroup) this.getView();
        parent.removeAllViews();
        parent.addView(newView);
    }

    private final TaskListener<Unit, Computation<? extends NextCard<?>>> mUndoListener = new TaskListener<Unit, Computation<? extends NextCard<?>>>() {
        @Override
        public void onPreExecute() {

        }


        @Override
        public void onPostExecute(Computation<? extends NextCard<?>> v) {
            openReviewer();
        }
    };

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.action_undo) {
            Timber.i("StudyOptionsFragment:: Undo button pressed");
            new UndoService.Undo().runWithHandler(mUndoListener);
            return true;
        } else if (itemId == R.id.action_deck_or_study_options) {
            Timber.i("StudyOptionsFragment:: Deck or study options button pressed");
            if (getCol().getDecks().isDyn(getCol().getDecks().selected())) {
                openFilteredDeckOptions();
            } else {
                Intent i = new Intent(getActivity(), DeckOptions.class);
                Timber.i("Opening deck options for activity result");
                mOnDeckOptionsActivityResult.launch(i);
                ActivityTransitionAnimation.slide(getActivity(), FADE);
            }
            return true;
        } else if (itemId == R.id.action_custom_study) {
            Timber.i("StudyOptionsFragment:: custom study button pressed");
            showCustomStudyContextMenu();
            return true;
        } else if (itemId == R.id.action_unbury) {
            Timber.i("StudyOptionsFragment:: unbury button pressed");
            getCol().getSched().unburyCardsForDeck();
            refreshInterfaceAndDecklist(true);
            item.setVisible(false);
            return true;
        } else if (itemId == R.id.action_rebuild) {
            Timber.i("StudyOptionsFragment:: rebuild cram deck button pressed");
            mProgressDialog = StyledProgressDialog.show(getActivity(), null,
                    getResources().getString(R.string.rebuild_filtered_deck), true);
            TaskManager.launchCollectionTask(new CollectionTask.RebuildCram(), getCollectionTaskListener(true));
            return true;
        } else if (itemId == R.id.action_empty) {
            Timber.i("StudyOptionsFragment:: empty cram deck button pressed");
            mProgressDialog = StyledProgressDialog.show(getActivity(), null,
                    getResources().getString(R.string.empty_filtered_deck), false);
            TaskManager.launchCollectionTask(new CollectionTask.EmptyCram(), getCollectionTaskListener(true));
            return true;
        } else if (itemId == R.id.action_rename) {
            ((DeckPicker) getActivity()).renameDeckDialog(getCol().getDecks().selected());
            return true;
        } else if (itemId == R.id.action_delete) {
            ((DeckPicker) getActivity()).confirmDeckDeletion(getCol().getDecks().selected());
            return true;
        } else if (itemId == R.id.action_export) {
            ((DeckPicker) getActivity()).exportDeck(getCol().getDecks().selected());
            return true;
        }
        return false;
    }

    public void configureToolbar() {
        configureToolbarInternal(true);
    }

    // This will allow a maximum of one recur in order to workaround database closes
    // caused by sync on startup where this might be running then have the collection close
    private void configureToolbarInternal(boolean recur) {
        Timber.i("configureToolbarInternal()");
        try {
            mToolbar.setOnMenuItemClickListener(this);
            Menu menu = mToolbar.getMenu();
            // Switch on or off rebuild/empty/custom study depending on whether or not filtered deck
            if (getCol() != null && getCol().getDecks().isDyn(getCol().getDecks().selected())) {
                menu.findItem(R.id.action_rebuild).setVisible(true);
                menu.findItem(R.id.action_empty).setVisible(true);
                menu.findItem(R.id.action_custom_study).setVisible(false);
                menu.findItem(R.id.action_deck_or_study_options).setTitle(R.string.menu__study_options);
            } else {
                menu.findItem(R.id.action_rebuild).setVisible(false);
                menu.findItem(R.id.action_empty).setVisible(false);
                menu.findItem(R.id.action_custom_study).setVisible(true);
                menu.findItem(R.id.action_deck_or_study_options).setTitle(R.string.menu__deck_options);
            }
            // Don't show custom study icon if congrats shown
            if (mCurrentContentView == CONTENT_CONGRATS) {
                menu.findItem(R.id.action_custom_study).setVisible(false);
            }
            // Switch on rename / delete / export if tablet layout
            if (mFragmented) {
                menu.findItem(R.id.action_rename).setVisible(true);
                menu.findItem(R.id.action_delete).setVisible(true);
                menu.findItem(R.id.action_export).setVisible(true);
            } else {
                menu.findItem(R.id.action_rename).setVisible(false);
                menu.findItem(R.id.action_delete).setVisible(false);
                menu.findItem(R.id.action_export).setVisible(false);
            }
            // Switch on or off unbury depending on if there are cards to unbury
            menu.findItem(R.id.action_unbury).setVisible(getCol() != null && getCol().getSched().haveBuried());
            // Switch on or off undo depending on whether undo is available
            if (getCol() == null || !getCol().undoAvailable()) {
                menu.findItem(R.id.action_undo).setVisible(false);
            } else {
                menu.findItem(R.id.action_undo).setVisible(true);
                Resources res = AnkiDroidApp.getAppResources();
                menu.findItem(R.id.action_undo).setTitle(res.getString(R.string.studyoptions_congrats_undo, getCol().undoName(res)));
            }
            // Set the back button listener
            if (!mFragmented) {
                final Drawable icon = AppCompatResources.getDrawable(getContext(), R.drawable.ic_arrow_back_white);
                icon.setAutoMirrored(true);
                mToolbar.setNavigationIcon(icon);
                mToolbar.setNavigationOnClickListener(v -> ((AnkiActivity) getActivity()).finishWithAnimation(END));
            }
        } catch (IllegalStateException e) {
            if (!CollectionHelper.getInstance().colIsOpen()) {
                if (recur) {
                    Timber.i(e, "Database closed while working. Probably auto-sync. Will re-try after sleep.");
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ex) {
                        Timber.i(ex, "Thread interrupted while waiting to retry. Likely unimportant.");
                        Thread.currentThread().interrupt();
                    }
                    configureToolbarInternal(false);
                } else {
                    Timber.w(e, "Database closed while working. No re-tries left.");
                }
            }
        }
    }

    ActivityResultLauncher<Intent> mOnRequestReviewActivityResult = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        Timber.i("StudyOptionsFragment::mOnRequestReviewActivityResult");
        if (mToolbar != null) {
            configureToolbar(); // FIXME we were crashing here because mToolbar is null #8913
        } else {
            AnkiDroidApp.sendExceptionReport("mToolbar null after return from tablet review session? Issue 8913", "StudyOptionsFragment");
        }
        if ((result.getResultCode() == DeckPicker.RESULT_DB_ERROR) || (result.getResultCode() == DeckPicker.RESULT_MEDIA_EJECTED)) {
            closeStudyOptions(result.getResultCode());
            return;
        }
        if (result.getResultCode() == Reviewer.RESULT_NO_MORE_CARDS) {
            // If no more cards getting returned while counts > 0 (due to learn ahead limit) then show a snackbar
            if ((getCol().getSched().count() > 0) && (mStudyOptionsView != null)) {
                View rootLayout = mStudyOptionsView.findViewById(R.id.studyoptions_main);
                UIUtils.showSnackbar(getActivity(), R.string.studyoptions_no_cards_due, false, 0, null, rootLayout);
            }
        }
    });

    ActivityResultLauncher<Intent> mOnDeckOptionsActivityResult = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        Timber.i("StudyOptionsFragment::mOnDeckOptionsActivityResult");
        configureToolbar();
        if ((result.getResultCode() == DeckPicker.RESULT_DB_ERROR) || (result.getResultCode() == DeckPicker.RESULT_MEDIA_EJECTED)) {
            closeStudyOptions(result.getResultCode());
            return;
        }
        if (mLoadWithDeckOptions) {
            mLoadWithDeckOptions = false;
            Deck deck = getCol().getDecks().current();
            if (deck.isDyn() && deck.has("empty")) {
                deck.remove("empty");
            }
            mProgressDialog = StyledProgressDialog.show(getActivity(), null,
                    getResources().getString(R.string.rebuild_filtered_deck), true);
            TaskManager.launchCollectionTask(new CollectionTask.RebuildCram(), getCollectionTaskListener(true));
        } else {
            TaskManager.waitToFinish();
            refreshInterface(true);
        }
    });


    private void dismissProgressDialog() {
        if (mStudyOptionsView != null && mStudyOptionsView.findViewById(R.id.progress_bar) != null) {
            mStudyOptionsView.findViewById(R.id.progress_bar).setVisibility(View.GONE);
        }
        // for rebuilding cram decks
        if (mProgressDialog != null && mProgressDialog.isShowing()) {
            try {
                mProgressDialog.dismiss();
            } catch (Exception e) {
                Timber.e("onPostExecute - Dialog dismiss Exception = %s", e.getMessage());
            }
        }
    }

    private void refreshInterfaceAndDecklist(boolean resetSched) {
        refreshInterface(resetSched, true);
    }

    protected void refreshInterface() {
        refreshInterface(false, false);
    }

    protected void refreshInterface(boolean resetSched) {
        refreshInterface(resetSched, false);
    }

    /**
     * Rebuild the fragment's interface to reflect the status of the currently selected deck.
     *
     * @param resetSched    Indicates whether to rebuild the queues as well. Set to true for any
     *                      task that modifies queues (e.g., unbury or empty filtered deck).
     * @param resetDecklist Indicates whether to call back to the parent activity in order to
     *                      also refresh the deck list.
     */
    protected void refreshInterface(boolean resetSched, boolean resetDecklist) {
        Timber.d("Refreshing StudyOptionsFragment");
        TaskManager.cancelAllTasks(CollectionTask.UpdateValuesFromDeck.class);
        // Load the deck counts for the deck from Collection asynchronously
        TaskManager.launchCollectionTask(new CollectionTask.UpdateValuesFromDeck(resetSched), getCollectionTaskListener(resetDecklist));
    }


    public static class DeckStudyData {
        /**
         * The number of new card to see today in a deck, including subdecks.
         */
        public final int mNewCardsToday;
        /**
         * The number of (repetition of) card in learning to see today in a deck, including subdecks. The exact way cards with multiple steps are counted depends on the scheduler
         */
        public final int mLrnCardsToday;
        /**
         * The number of review card to see today in a deck, including subdecks.
         */
        public final int mRevCardsToday;
        /**
         * The number of new cards in this decks, and subdecks.
         */
        public final int mNumberOfNewCardsInDeck;
        /**
         * Number of cards in this decks and its subdecks.
         */
        public final int mNumberOfCardsInDeck;
        /**
         * Expected time spent today to review all due cards in this deck.
         */
        public final int mEta;


        public DeckStudyData(int newCardsToday, int lrnCardsToday, int revCardsToday, int numberOfNewCardsInDeck, int numberOfCardsInDeck, int eta) {
            this.mNewCardsToday = newCardsToday;
            this.mLrnCardsToday = lrnCardsToday;
            this.mRevCardsToday = revCardsToday;
            this.mNumberOfNewCardsInDeck = numberOfNewCardsInDeck;
            this.mNumberOfCardsInDeck = numberOfCardsInDeck;
            this.mEta = eta;
        }
    }

    /**
     * Returns a listener that rebuilds the interface after execute.
     *
     * @param refreshDecklist If true, the listener notifies the parent activity to update its deck list
     *                        to reflect the latest values.
     */
    private TaskListener<Void, DeckStudyData> getCollectionTaskListener(final boolean refreshDecklist) {
        return new TaskListener<Void, DeckStudyData>() {
            @Override
            public void onPreExecute() {
            }

            @Override
            public void onPostExecute(DeckStudyData data) {
                dismissProgressDialog();
                if (data != null) {

                    // Don't do anything if the fragment is no longer attached to it's Activity or col has been closed
                    if (getActivity() == null) {
                        Timber.e("StudyOptionsFragment.mRefreshFragmentListener :: can't refresh");
                        return;
                    }

                    //#5506 If we have no view, short circuit all UI logic
                    if (mStudyOptionsView == null) {
                        tryOpenCramDeckOptions();
                        return;
                    }

                    // Reinitialize controls incase changed to filtered deck
                    initAllContentViews(mStudyOptionsView);
                    // Set the deck name
                    Deck deck = getCol().getDecks().current();
                    // Main deck name
                    String fullName = deck.getString("name");
                    String[] name = Decks.path(fullName);
                    StringBuilder nameBuilder = new StringBuilder();
                    if (name.length > 0) {
                        nameBuilder.append(name[0]);
                    }
                    if (name.length > 1) {
                        nameBuilder.append("\n").append(name[1]);
                    }
                    if (name.length > 3) {
                        nameBuilder.append("...");
                    }
                    if (name.length > 2) {
                        nameBuilder.append("\n").append(name[name.length - 1]);
                    }
                    mTextDeckName.setText(nameBuilder.toString());


                    if (tryOpenCramDeckOptions()) {
                        return;
                    }

                    // Switch between the empty view, the ordinary view, and the "congratulations" view
                    boolean isDynamic = deck.isDyn();
                    if (data.mNumberOfCardsInDeck == 0 && !isDynamic) {
                        mCurrentContentView = CONTENT_EMPTY;
                        mDeckInfoLayout.setVisibility(View.VISIBLE);
                        mTextCongratsMessage.setVisibility(View.VISIBLE);
                        mTextCongratsMessage.setText(R.string.studyoptions_empty);
                        mButtonStart.setVisibility(View.GONE);
                    } else if (data.mNewCardsToday + data.mLrnCardsToday + data.mRevCardsToday == 0) {
                        mCurrentContentView = CONTENT_CONGRATS;
                        if (!isDynamic) {
                            mDeckInfoLayout.setVisibility(View.GONE);
                            mButtonStart.setVisibility(View.VISIBLE);
                            mButtonStart.setText(R.string.custom_study);
                        } else {
                            mButtonStart.setVisibility(View.GONE);
                        }
                        mTextCongratsMessage.setVisibility(View.VISIBLE);
                        mTextCongratsMessage.setText(getCol().getSched().finishedMsg(getActivity()));
                    } else {
                        mCurrentContentView = CONTENT_STUDY_OPTIONS;
                        mDeckInfoLayout.setVisibility(View.VISIBLE);
                        mTextCongratsMessage.setVisibility(View.GONE);
                        mButtonStart.setVisibility(View.VISIBLE);
                        mButtonStart.setText(R.string.studyoptions_start);
                    }

                    // Set deck description
                    String desc;
                    if (isDynamic) {
                        desc = getResources().getString(R.string.dyn_deck_desc);
                    } else {
                        desc = getCol().getDecks().getActualDescription();
                    }
                    if (desc.length() > 0) {
                        mTextDeckDescription.setText(formatDescription(desc));
                        mTextDeckDescription.setVisibility(View.VISIBLE);
                    } else {
                        mTextDeckDescription.setVisibility(View.GONE);
                    }

                    // Set new/learn/review card counts
                    mTextTodayNew.setText(String.valueOf(data.mNewCardsToday));
                    mTextTodayLrn.setText(String.valueOf(data.mLrnCardsToday));
                    mTextTodayRev.setText(String.valueOf(data.mRevCardsToday));

                    // Set the total number of new cards in deck
                    if (data.mNumberOfNewCardsInDeck < NEW_CARD_COUNT_TRUNCATE_THRESHOLD) {
                        // if it hasn't been truncated by libanki then just set it usually
                        mTextNewTotal.setText(String.valueOf(data.mNumberOfNewCardsInDeck));
                    } else {
                        // if truncated then make a thread to allow full count to load
                        mTextNewTotal.setText(">1000");
                        if (mFullNewCountThread != null) {
                            // a thread was previously made -- interrupt it
                            mFullNewCountThread.interrupt();
                        }
                        mFullNewCountThread = new Thread(() -> {
                            Collection collection = getCol();
                            // TODO: refactor code to not rewrite this query, add to Sched.totalNewForCurrentDeck()
                            String query = "SELECT count(*) FROM cards WHERE did IN " +
                                    Utils.ids2str(collection.getDecks().active()) +
                                    " AND queue = " + Consts.QUEUE_TYPE_NEW;
                            final int fullNewCount = collection.getDb().queryScalar(query);
                            if (fullNewCount > 0) {
                                Runnable setNewTotalText = () -> mTextNewTotal.setText(String.valueOf(fullNewCount));
                                if (!Thread.currentThread().isInterrupted()) {
                                    mTextNewTotal.post(setNewTotalText);
                                }
                            }
                        });
                        mFullNewCountThread.start();
                    }

                    // Set total number of cards
                    mTextTotal.setText(String.valueOf(data.mNumberOfCardsInDeck));
                    // Set estimated time remaining
                    if (data.mEta != -1) {
                        mTextETA.setText(Integer.toString(data.mEta));
                    } else {
                        mTextETA.setText("-");
                    }
                    // Rebuild the options menu
                    configureToolbar();
                }

                // If in fragmented mode, refresh the deck list
                if (mFragmented && refreshDecklist) {
                    mListener.onRequireDeckListUpdate();
                }
            }
        };
    }

    /** Open cram deck option if deck is opened for the first time
     * @return Whether we opened the deck options */
    private boolean tryOpenCramDeckOptions() {
        if (!mLoadWithDeckOptions) {
            return false;
        }

        openFilteredDeckOptions(true);
        mLoadWithDeckOptions = false;
        return true;
    }

    @VisibleForTesting()
    static Spanned formatDescription(String desc) {
        //#5715: In deck description, ignore what is in style and script tag
        //Since we don't currently execute the JS/CSS, it's not worth displaying.
        String withStrippedTags = Utils.stripHTMLScriptAndStyleTags(desc);
        //#5188 - fromHtml displays newlines as " "
        String withFixedNewlines = HtmlUtils.convertNewlinesToHtml(withStrippedTags);
        return HtmlCompat.fromHtml(withFixedNewlines, HtmlCompat.FROM_HTML_MODE_LEGACY);
    }

    private @Nullable Collection getCol() {
        try {
            return CollectionHelper.getInstance().getCol(getContext());
        } catch (Exception e) {
            // This may happen if the backend is locked or similar.
        }
        return null;
    }


    @Override
    public void onPause() {
        super.onPause();
        if (!mToReviewer) {
            // In the reviewer, we need the count. So don't cancel it. Otherwise, (e.g. go to browser, selecting another
            // deck) cancel counts.
            TaskManager.cancelAllTasks(CollectionTask.UpdateValuesFromDeck.class);
        }
    }
}
