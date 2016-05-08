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
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.widget.Toolbar;
import android.text.Html;
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
import com.ichi2.anki.dialogs.CustomStudyDialog;
import com.ichi2.async.DeckTask;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Utils;
import com.ichi2.themes.StyledProgressDialog;

import org.json.JSONException;
import org.json.JSONObject;

import timber.log.Timber;

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
    public static final int CONTENT_STUDY_OPTIONS = 0;
    public static final int CONTENT_CONGRATS = 1;
    public static final int CONTENT_EMPTY = 2;

    // Threshold at which the total number of new cards is truncated by libanki
    private static final int NEW_CARD_COUNT_TRUNCATE_THRESHOLD = 1000;

    /**
     * Preferences
     */
    private int mCurrentContentView = CONTENT_STUDY_OPTIONS;

    /** Alerts to inform the user about different situations */
    private MaterialDialog mProgressDialog;

    /**
     * UI elements for "Study Options" view
     */
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

    StudyOptionsListener mListener;


    public interface StudyOptionsListener {
        void onRequireDeckListUpdate();
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        try {
            mListener = (StudyOptionsListener) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString() + " must implement StudyOptionsListener");
        }
    }


    /**
     * Callbacks for UI events
     */
    private View.OnClickListener mButtonClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            // long timeLimit = 0;
            switch (v.getId()) {
                case R.id.studyoptions_start:
                    Timber.i("StudyOptionsFragment:: start study button pressed");
                    if (mCurrentContentView != CONTENT_CONGRATS) {
                        openReviewer();
                    } else {
                        showCustomStudyContextMenu();
                    }
                    return;
                default:
            }
        }
    };

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
        getActivity().startActivityForResult(i, DECK_OPTIONS);
        ActivityTransitionAnimation.slide(getActivity(), ActivityTransitionAnimation.FADE);
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
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        if (container == null) {
            // Currently in a layout without a container, so no reason to create our view.
            return null;
        }
        restorePreferences();
        mStudyOptionsView = inflater.inflate(R.layout.studyoptions_fragment, container, false);
        mFragmented = getActivity().getClass() != StudyOptionsActivity.class;
        initAllContentViews();
        if (getArguments() != null) {
            mLoadWithDeckOptions = getArguments().getBoolean("withDeckOptions");
        }
        mToolbar = (Toolbar) mStudyOptionsView.findViewById(R.id.studyOptionsToolbar);
        mToolbar.inflateMenu(R.menu.study_options_fragment);
        if (mToolbar != null) {
            configureToolbar();
        }
        refreshInterface(true);
        return mStudyOptionsView;
    }


    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mFullNewCountThread != null) {
            mFullNewCountThread.interrupt();
        }
        Timber.d("onDestroy()");
    }


    @Override
    public void onResume() {
        super.onResume();
        Timber.d("onResume()");
        refreshInterface(true);
    }


    private void closeStudyOptions(int result) {
        Activity a = getActivity();
        if (!mFragmented && a != null) {
            a.setResult(result);
            a.finish();
            ActivityTransitionAnimation.slide(a, ActivityTransitionAnimation.RIGHT);
        } else if (a == null) {
            // getActivity() can return null if reference to fragment lingers after parent activity has been closed,
            // which is particularly relevant when using AsyncTasks.
            Timber.e("closeStudyOptions() failed due to getActivity() returning null");
        }
    }


    private void openReviewer() {
        Intent reviewer = new Intent(getActivity(), Reviewer.class);
        if (mFragmented) {
            getActivity().startActivityForResult(reviewer, AnkiActivity.REQUEST_REVIEW);
        } else {
            // Go to DeckPicker after studying when not tablet
            reviewer.setFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT);
            startActivity(reviewer);
            getActivity().finish();
        }
        animateLeft();
        getCol().startTimebox();
    }


    private void animateLeft() {
        ActivityTransitionAnimation.slide(getActivity(), ActivityTransitionAnimation.LEFT);
    }


    private void initAllContentViews() {
        if (mFragmented) {
            mStudyOptionsView.findViewById(R.id.studyoptions_gradient).setVisibility(View.VISIBLE);
        }
        mDeckInfoLayout = mStudyOptionsView.findViewById(R.id.studyoptions_deckinformation);
        mTextDeckName = (TextView) mStudyOptionsView.findViewById(R.id.studyoptions_deck_name);
        mTextDeckDescription = (TextView) mStudyOptionsView.findViewById(R.id.studyoptions_deck_description);
        // make links clickable
        mTextDeckDescription.setMovementMethod(LinkMovementMethod.getInstance());
        mButtonStart = (Button) mStudyOptionsView.findViewById(R.id.studyoptions_start);
        mTextCongratsMessage = (TextView) mStudyOptionsView.findViewById(R.id.studyoptions_congrats_message);
        // Code common to both fragmented and non-fragmented view
        mTextTodayNew = (TextView) mStudyOptionsView.findViewById(R.id.studyoptions_new);
        mTextTodayLrn = (TextView) mStudyOptionsView.findViewById(R.id.studyoptions_lrn);
        mTextTodayRev = (TextView) mStudyOptionsView.findViewById(R.id.studyoptions_rev);
        mTextNewTotal = (TextView) mStudyOptionsView.findViewById(R.id.studyoptions_total_new);
        mTextTotal = (TextView) mStudyOptionsView.findViewById(R.id.studyoptions_total);
        mTextETA = (TextView) mStudyOptionsView.findViewById(R.id.studyoptions_eta);
        mButtonStart.setOnClickListener(mButtonClickListener);
    }


    /**
     * Show the context menu for the custom study options
     */
    private void showCustomStudyContextMenu() {
        CustomStudyDialog d = CustomStudyDialog.newInstance(CustomStudyDialog.CONTEXT_MENU_STANDARD,
                getCol().getDecks().selected());
        ((AnkiActivity)getActivity()).showDialogFragment(d);
    }


    void setFragmentContentView(View newView) {
        ViewGroup parent = (ViewGroup) this.getView();
        parent.removeAllViews();
        parent.addView(newView);
    }


    @Override
    public boolean onMenuItemClick(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_undo:
                Timber.i("StudyOptionsFragment:: Undo button pressed");
                getCol().undo();
                openReviewer();
                return true;
            case R.id.action_deck_options:
                Timber.i("StudyOptionsFragment:: Deck options button pressed");
                if (getCol().getDecks().isDyn(getCol().getDecks().selected())) {
                    openFilteredDeckOptions();
                } else {
                    Intent i = new Intent(getActivity(), DeckOptions.class);
                    getActivity().startActivityForResult(i, DECK_OPTIONS);
                    ActivityTransitionAnimation.slide(getActivity(), ActivityTransitionAnimation.FADE);
                }
                return true;
            case R.id.action_custom_study:
                Timber.i("StudyOptionsFragment:: custom study button pressed");
                showCustomStudyContextMenu();
                return true;
            case R.id.action_unbury:
                Timber.i("StudyOptionsFragment:: unbury button pressed");
                getCol().getSched().unburyCardsForDeck();
                refreshInterfaceAndDecklist(true);
                item.setVisible(false);
                return true;
            case R.id.action_rebuild:
                Timber.i("StudyOptionsFragment:: rebuild cram deck button pressed");
                mProgressDialog = StyledProgressDialog.show(getActivity(), "",
                        getResources().getString(R.string.rebuild_cram_deck), true);
                DeckTask.launchDeckTask(DeckTask.TASK_TYPE_REBUILD_CRAM, getDeckTaskListener(true),
                        new DeckTask.TaskData(mFragmented));
                return true;
            case R.id.action_empty:
                Timber.i("StudyOptionsFragment:: empty cram deck button pressed");
                mProgressDialog = StyledProgressDialog.show(getActivity(), "",
                        getResources().getString(R.string.empty_cram_deck), false);
                DeckTask.launchDeckTask(DeckTask.TASK_TYPE_EMPTY_CRAM, getDeckTaskListener(true),
                        new DeckTask.TaskData(mFragmented));
                return true;
            case R.id.action_rename:
                ((DeckPicker) getActivity()).renameDeckDialog(getCol().getDecks().selected());
                return true;
            case R.id.action_delete:
                ((DeckPicker) getActivity()).confirmDeckDeletion(getCol().getDecks().selected());
                return true;
            case R.id.action_export:
                ((DeckPicker) getActivity()).exportDeck(getCol().getDecks().selected());
                return true;
            default:
                return false;
        }
    }

    public void configureToolbar() {
        mToolbar.setOnMenuItemClickListener(this);
        Menu menu = mToolbar.getMenu();
        // Switch on or off rebuild/empty/custom study depending on whether or not filtered deck
        if (getCol().getDecks().isDyn(getCol().getDecks().selected())) {
            menu.findItem(R.id.action_rebuild).setVisible(true);
            menu.findItem(R.id.action_empty).setVisible(true);
            menu.findItem(R.id.action_custom_study).setVisible(false);
        } else {
            menu.findItem(R.id.action_rebuild).setVisible(false);
            menu.findItem(R.id.action_empty).setVisible(false);
            menu.findItem(R.id.action_custom_study).setVisible(true);
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
        menu.findItem(R.id.action_unbury).setVisible(getCol().getSched().haveBuried());
        // Switch on or off undo depending on whether undo is available
        if (!getCol().undoAvailable()) {
            menu.findItem(R.id.action_undo).setVisible(false);
        } else {
            menu.findItem(R.id.action_undo).setVisible(true);
            Resources res = AnkiDroidApp.getAppResources();
            menu.findItem(R.id.action_undo).setTitle(res.getString(R.string.studyoptions_congrats_undo, getCol().undoName(res)));
        }
        // Set the back button listener
        if (!mFragmented) {
            mToolbar.setNavigationIcon(R.drawable.ic_arrow_back_white_24dp);
            mToolbar.setNavigationOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    ((AnkiActivity) getActivity()).finishWithAnimation(ActivityTransitionAnimation.RIGHT);
                }
            });
        }
    }


    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);
        Timber.d("onActivityResult (requestCode = %d, resultCode = %d)", requestCode, resultCode);

        // rebuild action bar
        configureToolbar();

        // boot back to deck picker if there was an error
        if (resultCode == DeckPicker.RESULT_DB_ERROR || resultCode == DeckPicker.RESULT_MEDIA_EJECTED) {
            closeStudyOptions(resultCode);
            return;
        }

        // perform some special actions depending on which activity we're returning from
        if (requestCode == STATISTICS || requestCode == BROWSE_CARDS) {
            // select original deck if the statistics or card browser were opened,
            // which can change the selected deck
            if (intent.hasExtra("originalDeck")) {
                getCol().getDecks().select(intent.getLongExtra("originalDeck", 0L));
            }
        }
        if (requestCode == DECK_OPTIONS) {
            if (mLoadWithDeckOptions) {
                mLoadWithDeckOptions = false;
                try {
                    JSONObject deck = getCol().getDecks().current();
                    if (deck.getInt("dyn") != 0 && deck.has("empty")) {
                        deck.remove("empty");
                    }
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
                    mProgressDialog = StyledProgressDialog.show(getActivity(), "",
                            getResources().getString(R.string.rebuild_cram_deck), true);
                    DeckTask.launchDeckTask(DeckTask.TASK_TYPE_REBUILD_CRAM, getDeckTaskListener(true),
                            new DeckTask.TaskData(mFragmented));
            } else {
                DeckTask.waitToFinish();
                refreshInterface(true);
            }
        } else if (requestCode == AnkiActivity.REQUEST_REVIEW) {
            if (resultCode == Reviewer.RESULT_NO_MORE_CARDS) {
                // If no more cards getting returned while counts > 0 (due to learn ahead limit) then show a snackbar
                int[] counts = getCol().getSched().counts();
                if ((counts[0]+counts[1]+counts[2])>0 && mStudyOptionsView != null) {
                    View rootLayout = mStudyOptionsView.findViewById(R.id.studyoptions_main);
                    UIUtils.showSnackbar(getActivity(), R.string.studyoptions_no_cards_due, false, 0, null, rootLayout);
                }
            }
        } else if (requestCode == STATISTICS && mCurrentContentView == CONTENT_CONGRATS) {
            mCurrentContentView = CONTENT_STUDY_OPTIONS;
            setFragmentContentView(mStudyOptionsView);
        }
    }

    private void dismissProgressDialog() {
        if (mStudyOptionsView != null && mStudyOptionsView.findViewById(R.id.progress_bar) != null) {
            mStudyOptionsView.findViewById(R.id.progress_bar).setVisibility(View.GONE);
        }
        // for rebuilding cram decks
        if (mProgressDialog != null && mProgressDialog.isShowing()) {
            try {
                mProgressDialog.dismiss();
            } catch (Exception e) {
                Timber.e("onPostExecute - Dialog dismiss Exception = " + e.getMessage());
            }
        }
    }


    public SharedPreferences restorePreferences() {
        SharedPreferences preferences = AnkiDroidApp.getSharedPrefs(getActivity().getBaseContext());
        return preferences;
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
        // Load the deck counts for the deck from Collection asynchronously
        DeckTask.launchDeckTask(DeckTask.TASK_TYPE_UPDATE_VALUES_FROM_DECK, getDeckTaskListener(resetDecklist),
                new DeckTask.TaskData(new Object[]{resetSched}));
    }


    /**
     * Returns a listener that rebuilds the interface after execute.
     *
     * @param refreshDecklist If true, the listener notifies the parent activity to update its deck list
     *                        to reflect the latest values.
     */
    private DeckTask.TaskListener getDeckTaskListener(final boolean refreshDecklist) {
        return new DeckTask.TaskListener() {
            @Override
            public void onPreExecute() {

            }

            @Override
            public void onPostExecute(DeckTask.TaskData result) {
                dismissProgressDialog();
                if (result != null) {
                    // Get the return values back from the AsyncTask
                    Object[] obj = result.getObjArray();
                    int newCards = (Integer) obj[0];
                    int lrnCards = (Integer) obj[1];
                    int revCards = (Integer) obj[2];
                    int totalNew = (Integer) obj[3];
                    int totalCards = (Integer) obj[4];
                    int eta = (Integer) obj[5];

                    // Don't do anything if the fragment is no longer attached to it's Activity or col has been closed
                    if (getActivity() == null) {
                        Timber.e("StudyOptionsFragment.mRefreshFragmentListener :: can't refresh");
                        return;
                    }
                    // Reinitialize controls incase changed to filtered deck
                    initAllContentViews();
                    // Set the deck name
                    String fullName;
                    JSONObject deck = getCol().getDecks().current();
                    try {
                        // Main deck name
                        fullName = deck.getString("name");
                        String[] name = fullName.split("::");
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

                    } catch (JSONException e) {
                        throw new RuntimeException(e);
                    }

                    // open cram deck option if deck is opened for the first time
                    if (mLoadWithDeckOptions) {
                        openFilteredDeckOptions(mLoadWithDeckOptions);
                        return;
                    }
                    // Switch between the empty view, the ordinary view, and the "congratulations" view
                    boolean isDynamic = deck.optInt("dyn", 0) != 0;
                    if (totalCards == 0 && !isDynamic) {
                        mCurrentContentView = CONTENT_EMPTY;
                        mDeckInfoLayout.setVisibility(View.VISIBLE);
                        mTextCongratsMessage.setVisibility(View.VISIBLE);
                        mTextCongratsMessage.setText(R.string.studyoptions_empty);
                        mButtonStart.setVisibility(View.GONE);
                    } else if (newCards + lrnCards + revCards == 0) {
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
                        mTextDeckDescription.setText(Html.fromHtml(desc));
                        mTextDeckDescription.setVisibility(View.VISIBLE);
                    } else {
                        mTextDeckDescription.setVisibility(View.GONE);
                    }

                    // Set new/learn/review card counts
                    mTextTodayNew.setText(String.valueOf(newCards));
                    mTextTodayLrn.setText(String.valueOf(lrnCards));
                    mTextTodayRev.setText(String.valueOf(revCards));

                    // Set the total number of new cards in deck
                    if (totalNew < NEW_CARD_COUNT_TRUNCATE_THRESHOLD) {
                        // if it hasn't been truncated by libanki then just set it usually
                        mTextNewTotal.setText(String.valueOf(totalNew));
                    } else {
                        // if truncated then make a thread to allow full count to load
                        mTextNewTotal.setText(">1000");
                        if (mFullNewCountThread != null) {
                            // a thread was previously made -- interrupt it
                            mFullNewCountThread.interrupt();
                        }
                        mFullNewCountThread = new Thread(new Runnable() {
                            @Override
                            public void run() {
                                Collection collection = getCol();
                                // TODO: refactor code to not rewrite this query, add to Sched.totalNewForCurrentDeck()
                                StringBuilder sbQuery = new StringBuilder();
                                sbQuery.append("SELECT count(*) FROM cards WHERE did IN ");
                                sbQuery.append(Utils.ids2str(collection.getDecks().active()));
                                sbQuery.append(" AND queue = 0");
                                final int fullNewCount = collection.getDb().queryScalar(sbQuery.toString());
                                if (fullNewCount > 0) {
                                    Runnable setNewTotalText = new Runnable() {
                                        @Override
                                        public void run() {
                                            mTextNewTotal.setText(String.valueOf(fullNewCount));
                                        }
                                    };
                                    if (!Thread.currentThread().isInterrupted()) {
                                        mTextNewTotal.post(setNewTotalText);
                                    }
                                }
                            }
                        });
                        mFullNewCountThread.start();
                    }

                    // Set total number of cards
                    mTextTotal.setText(String.valueOf(totalCards));
                    // Set estimated time remaining
                    if (eta != -1) {
                        mTextETA.setText(Integer.toString(eta));
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

            @Override
            public void onProgressUpdate(DeckTask.TaskData... values) {

            }

            @Override
            public void onCancelled() {

            }
        };
    }

    private Collection getCol() {
        return CollectionHelper.getInstance().getCol(getContext());
    }
}
