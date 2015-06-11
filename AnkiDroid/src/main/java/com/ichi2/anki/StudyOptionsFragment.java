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
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.support.v7.app.AppCompatActivity;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.afollestad.materialdialogs.MaterialDialog;
import com.ichi2.anim.ActivityTransitionAnimation;
import com.ichi2.anim.ViewAnimation;
import com.ichi2.anki.dialogs.CustomStudyDialog;
import com.ichi2.anki.dialogs.TagsDialog;
import com.ichi2.anki.dialogs.TagsDialog.TagsDialogListener;
import com.ichi2.anki.stats.AnkiStatsTaskHandler;
import com.ichi2.anki.stats.ChartView;
import com.ichi2.async.CollectionLoader;
import com.ichi2.async.DeckTask;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Consts;
import com.ichi2.libanki.Utils;
import com.ichi2.themes.StyledProgressDialog;
import com.ichi2.themes.Themes;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import timber.log.Timber;

public class StudyOptionsFragment extends Fragment implements LoaderManager.LoaderCallbacks<Collection> {


    /**
     * Available options performed by other activities
     */
    public static final int PREFERENCES_UPDATE = 0;
    private static final int REQUEST_REVIEW = 1;
    private static final int ADD_NOTE = 2;
    private static final int BROWSE_CARDS = 3;
    private static final int STATISTICS = 4;
    private static final int DECK_OPTIONS = 5;

    /**
     * Constants for selecting which content view to display
     */
    public static final int CONTENT_STUDY_OPTIONS = 0;
    public static final int CONTENT_CONGRATS = 1;

    // Threshold at which the total number of new cards is truncated by libanki
    private static final int NEW_CARD_COUNT_TRUNCATE_THRESHOLD = 1000;

    /**
     * Preferences
     */
    private int mCurrentContentView = CONTENT_STUDY_OPTIONS;
    boolean mInvertedColors = false;

    /** Alerts to inform the user about different situations */
    private MaterialDialog mProgressDialog;

    /**
     * UI elements for "Study Options" view
     */
    private View mStudyOptionsView;
    private View mDeckInfoLayout;
    private Button mButtonStart;
    private Button mButtonCustomStudy;
    private Button mButtonUnbury;
    private ImageButton mFloatingActionButton;
    private TextView mTextDeckName;
    private TextView mTextDeckDescription;
    private TextView mTextTodayNew;
    private TextView mTextTodayLrn;
    private TextView mTextTodayRev;
    private TextView mTextNewTotal;
    private TextView mTextTotal;
    private TextView mTextETA;
    private LinearLayout mSmallChart;
    private LinearLayout mDeckCounts;
    private LinearLayout mDeckChart;
    private Button mDeckOptions;
    private Button mCramOptions;
    private TextView mTextCongratsMessage;
    private View mCongratsLayout;

    private String mSearchTerms;

    // Flag to indicate if the fragment should load the deck options immediately after it loads
    private boolean mLoadWithDeckOptions;

    private boolean mFragmented;
    private Collection mCollection;

    private Thread mFullNewCountThread = null;

    StudyOptionsListener mListener;


    public interface StudyOptionsListener {
        public void onRequireDeckListUpdate();
        public void createFilteredDeck(JSONArray delays, Object[] terms, Boolean resched);
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
            Collection col = getCol();
            // long timeLimit = 0;
            switch (v.getId()) {
                case R.id.studyoptions_start:
                    Timber.i("StudyOptionsFragment:: start study button pressed");
                    openReviewer();
                    return;
                case R.id.studyoptions_custom:
                    Timber.i("StudyOptionsFragment:: custom study button pressed");
                    showCustomStudyContextMenu();
                    return;
                case R.id.studyoptions_unbury:
                    Timber.i("StudyOptionsFragment:: unbury button pressed");
                    col.getSched().unburyCardsForDeck();
                    refreshInterfaceAndDecklist(true);
                    return;
                case R.id.studyoptions_options_cram:
                    Timber.i("StudyOptionsFragment:: cram deck options button pressed");
                    openFilteredDeckOptions();
                    return;
                case R.id.studyoptions_options:
                    Timber.i("StudyOptionsFragment:: deck options button pressed");
                    Intent i = new Intent(getActivity(), DeckOptions.class);
                    startActivityForResult(i, DECK_OPTIONS);
                    ActivityTransitionAnimation.slide(getActivity(), ActivityTransitionAnimation.FADE);
                    return;
                case R.id.studyoptions_rebuild_cram:
                    Timber.i("StudyOptionsFragment:: rebuild cram deck button pressed");
                    mProgressDialog = StyledProgressDialog.show(getActivity(), "",
                            getResources().getString(R.string.rebuild_cram_deck), true);
                    DeckTask.launchDeckTask(DeckTask.TASK_TYPE_REBUILD_CRAM, getDeckTaskListener(true),
                            new DeckTask.TaskData(getCol(), getCol().getDecks().selected(), mFragmented));
                    return;
                case R.id.studyoptions_empty_cram:
                    Timber.i("StudyOptionsFragment:: empty cram deck button pressed");
                    mProgressDialog = StyledProgressDialog.show(getActivity(), "",
                            getResources().getString(R.string.empty_cram_deck), false);
                    DeckTask.launchDeckTask(DeckTask.TASK_TYPE_EMPTY_CRAM, getDeckTaskListener(true),
                            new DeckTask.TaskData(col, col.getDecks().selected(), mFragmented));
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
        startActivityForResult(i, DECK_OPTIONS);
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
        NavigationDrawerActivity.setIsWholeCollection(false);
        startLoadingCollection();
        return mStudyOptionsView;
    }
    
    // Called when the collection loader has finished
    // NOTE: Fragment transactions are NOT allowed to be called from here onwards
    private void onCollectionLoaded(Collection col) {
        mCollection = col;
        initAllContentViews();
        if (getArguments() != null) {
            mLoadWithDeckOptions = getArguments().getBoolean("withDeckOptions");
        }
        refreshInterface(true);
        setHasOptionsMenu(true);
        ((AnkiActivity) getActivity()).hideProgressBar();
        // rebuild action bar so that Showcase works correctly
        if (mFragmented) {
            ((DeckPicker) getActivity()).reloadShowcaseView();
        }
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
        if (colOpen()) {
            Timber.d("onResume()");
            refreshInterface(true);
        }
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
        startActivityForResult(reviewer, REQUEST_REVIEW);
        animateLeft();
        getCol().startTimebox();
    }


    private void addNote() {
        Preferences.COMING_FROM_ADD = true;
        Intent intent = new Intent(getActivity(), NoteEditor.class);
        intent.putExtra(NoteEditor.EXTRA_CALLER, NoteEditor.CALLER_STUDYOPTIONS);
        startActivityForResult(intent, ADD_NOTE);
        animateLeft();
    }


    private void animateLeft() {
        ActivityTransitionAnimation.slide(getActivity(), ActivityTransitionAnimation.LEFT);
    }


    private void initAllContentViews() {
        Themes.setContentStyle(mStudyOptionsView, Themes.CALLER_STUDYOPTIONS);
        mDeckInfoLayout = mStudyOptionsView.findViewById(R.id.studyoptions_deckinformation);
        mTextDeckName = (TextView) mStudyOptionsView.findViewById(R.id.studyoptions_deck_name);
        mTextDeckDescription = (TextView) mStudyOptionsView.findViewById(R.id.studyoptions_deck_description);
        // make links clickable
        mTextDeckDescription.setMovementMethod(LinkMovementMethod.getInstance());
        mButtonStart = (Button) mStudyOptionsView.findViewById(R.id.studyoptions_start);
        mButtonCustomStudy = (Button) mStudyOptionsView.findViewById(R.id.studyoptions_custom);
        mDeckOptions = (Button) mStudyOptionsView.findViewById(R.id.studyoptions_options);
        mCramOptions = (Button) mStudyOptionsView.findViewById(R.id.studyoptions_options_cram);
        mCongratsLayout = mStudyOptionsView.findViewById(R.id.studyoptions_congrats_layout);
        mTextCongratsMessage = (TextView) mStudyOptionsView.findViewById(R.id.studyoptions_congrats_message);
        mFloatingActionButton = (ImageButton) mStudyOptionsView.findViewById(R.id.fab);


        if (getCol().getDecks().isDyn(getCol().getDecks().selected())) {
            Button rebBut = (Button) mStudyOptionsView.findViewById(R.id.studyoptions_rebuild_cram);
            rebBut.setOnClickListener(mButtonClickListener);
            Button emptyBut = (Button) mStudyOptionsView.findViewById(R.id.studyoptions_empty_cram);
            emptyBut.setOnClickListener(mButtonClickListener);
            // If dynamic deck then enable the cram buttons group, and disable the new filtered deck / ordinary study
            // options buttons group
            ((LinearLayout) mStudyOptionsView.findViewById(R.id.studyoptions_cram_buttons)).setVisibility(View.VISIBLE);
            ((LinearLayout) mStudyOptionsView.findViewById(R.id.studyoptions_regular_buttons)).setVisibility(View.GONE);
        }
        // Show the unbury button if there are cards to unbury
        mButtonUnbury = (Button) mStudyOptionsView.findViewById(R.id.studyoptions_unbury);
        mButtonUnbury.setOnClickListener(mButtonClickListener);

        // Code common to both fragmented and non-fragmented view
        mTextTodayNew = (TextView) mStudyOptionsView.findViewById(R.id.studyoptions_new);
        mTextTodayLrn = (TextView) mStudyOptionsView.findViewById(R.id.studyoptions_lrn);
        mTextTodayRev = (TextView) mStudyOptionsView.findViewById(R.id.studyoptions_rev);
        mTextNewTotal = (TextView) mStudyOptionsView.findViewById(R.id.studyoptions_total_new);
        mTextTotal = (TextView) mStudyOptionsView.findViewById(R.id.studyoptions_total);
        mTextETA = (TextView) mStudyOptionsView.findViewById(R.id.studyoptions_eta);
        mSmallChart = (LinearLayout) mStudyOptionsView.findViewById(R.id.studyoptions_mall_chart);

        mDeckCounts = (LinearLayout) mStudyOptionsView.findViewById(R.id.studyoptions_deckcounts);
        mDeckChart = (LinearLayout) mStudyOptionsView.findViewById(R.id.studyoptions_chart);

        mButtonStart.setOnClickListener(mButtonClickListener);
        mButtonCustomStudy.setOnClickListener(mButtonClickListener);
        mDeckOptions.setOnClickListener(mButtonClickListener);
        mCramOptions.setOnClickListener(mButtonClickListener);

        /*mFloatingActionButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                addNote();
            }
        });*/
    }


    /**
     * Special method to show the context menu for the custom study options
     * TODO: Turn this into a DialogFragment
     */
    private void showCustomStudyContextMenu() {
        Resources res = getResources();
        Drawable icon = res.getDrawable(R.drawable.ic_sort_black_36dp);
        icon.setAlpha(Themes.ALPHA_ICON_ENABLED_DARK);
        MaterialDialog dialog = new MaterialDialog.Builder(this.getActivity())
                .title(res.getString(R.string.custom_study))
                .icon(icon)
                .cancelable(true)
                .items(res.getStringArray(R.array.custom_study_options_labels))
                .itemsCallback(new MaterialDialog.ListCallback() {
                    @Override
                    public void onSelection(MaterialDialog materialDialog, View view, int which,
                                            CharSequence charSequence) {
                        DialogFragment dialogFragment;
                        if (which == CustomStudyDialog.CUSTOM_STUDY_TAGS) {
                            /*
                             * This is a special Dialog for CUSTOM STUDY, where instead of only collecting a
                             * number, it is necessary to collect a list of tags. This case handles the creation
                             * of that Dialog.
                             */
                            dialogFragment = com.ichi2.anki.dialogs.TagsDialog.newInstance(
                                    TagsDialog.TYPE_CUSTOM_STUDY_TAGS, new ArrayList<String>(),
                                    new ArrayList<String>(getCol().getTags().all()));

                            ((TagsDialog) dialogFragment).setTagsDialogListener(new TagsDialogListener() {
                                @Override
                                public void onPositive(List<String> selectedTags, int option) {
                                    /*
                                     * Here's the method that gathers the final selection of tags, type of cards and generates the search
                                     * screen for the custom study deck.
                                     */
                                    StringBuilder sb = new StringBuilder();
                                    switch (option) {
                                        case 1:
                                            sb.append("is:new ");
                                            break;
                                        case 2:
                                            sb.append("is:due ");
                                            break;
                                        default:
                                            // Logging here might be appropriate : )
                                            break;
                                    }
                                    int i = 0;
                                    for (String tag : selectedTags) {
                                        if (i != 0) {
                                            sb.append("or ");
                                        } else {
                                            sb.append("("); // Only if we really have selected tags
                                        }
                                        sb.append("tag:").append(tag).append(" ");
                                        i++;
                                    }
                                    if (i > 0) {
                                        sb.append(")"); // Only if we added anything to the tag list
                                    }
                                    mSearchTerms = sb.toString();
                                    createFilteredDeck(new JSONArray(), new Object[]{mSearchTerms, Consts.DYN_MAX_SIZE,
                                            Consts.DYN_RANDOM}, false);
                                }
                            });
                        } else {
                            // Show CustomStudyDialog for all options other than the tags dialog
                            dialogFragment = CustomStudyDialog.newInstance(which);
                            // If we increase limits, refresh the interface to reflect the new counts
                            ((CustomStudyDialog) dialogFragment).setCustomStudyDialogListener(
                                    new CustomStudyDialog.CustomStudyDialogListener() {
                                        @Override
                                        public void onPositive(int option) {
                                            if (option == CustomStudyDialog.CUSTOM_STUDY_NEW ||
                                                    option == CustomStudyDialog.CUSTOM_STUDY_REV) {
                                                refreshInterfaceAndDecklist(true);
                                            }
                                        }
                                    });
                        }
                        // Show the DialogFragment via Activity
                        ((AnkiActivity) getActivity()).showDialogFragment(dialogFragment);
                    }
                })
                .build();
        dialog.setOwnerActivity(getActivity());
        dialog.show();
    }

    public void createFilteredDeck(JSONArray delays, Object[] terms, Boolean resched) {
        JSONObject dyn;
        if (colOpen()) {
            Collection col = getCol();
            try {
                String deckName = col.getDecks().current().getString("name");
                String customStudyDeck = getResources().getString(R.string.custom_study_deck_name);
                JSONObject cur = col.getDecks().byName(customStudyDeck);
                if (cur != null) {
                    if (cur.getInt("dyn") != 1) {
                        new MaterialDialog.Builder(getActivity())
                                .content(R.string.custom_study_deck_exists)
                                .negativeText(R.string.dialog_cancel)
                                .build().show();
                        return;
                    } else {
                        // safe to empty
                        col.getSched().emptyDyn(cur.getLong("id"));
                        // reuse; don't delete as it may have children
                        dyn = cur;
                        col.getDecks().select(cur.getLong("id"));
                    }
                } else {
                    long did = col.getDecks().newDyn(customStudyDeck);
                    dyn = col.getDecks().get(did);
                }
                // and then set various options
                if (delays.length() > 0) {
                    dyn.put("delays", delays);
                } else {
                    dyn.put("delays", JSONObject.NULL);
                }
                JSONArray ar = dyn.getJSONArray("terms");
                ar.getJSONArray(0).put(0,
                        new StringBuilder("deck:\"").append(deckName).append("\" ").append(terms[0]).toString());
                ar.getJSONArray(0).put(1, terms[1]);
                ar.getJSONArray(0).put(2, terms[2]);
                dyn.put("resched", resched);


                // Initial rebuild
                mProgressDialog = StyledProgressDialog.show(getActivity(), "",
                        getResources().getString(R.string.rebuild_custom_study_deck), false);
                DeckTask.launchDeckTask(DeckTask.TASK_TYPE_REBUILD_CRAM, getDeckTaskListener(true),
                        new DeckTask.TaskData(getCol(), getCol().getDecks().selected(),
                                mFragmented));
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
        }
    }


    void setFragmentContentView(View newView) {
        ViewGroup parent = (ViewGroup) this.getView();
        parent.removeAllViews();
        parent.addView(newView);
    }


    private void updateChart(double[][] serieslist) {
        if (mSmallChart != null) {

            ChartView chartView = (ChartView) mSmallChart.findViewById(R.id.chart_view_small_chart);
            chartView.setBackgroundColor(Color.BLACK);
            Collection col = CollectionHelper.getInstance().getCol(getActivity());
            AnkiStatsTaskHandler.createSmallDueChartChart(col, serieslist, chartView);
            if (mDeckChart.getVisibility() == View.INVISIBLE) {
                mDeckChart.setVisibility(View.VISIBLE);
                mDeckChart.setAnimation(ViewAnimation.fade(ViewAnimation.FADE_IN, 500, 0));
            }

        }
    }


    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        getActivity().getMenuInflater().inflate(R.menu.study_options_fragment, menu);
        SharedPreferences preferences = AnkiDroidApp.getSharedPrefs(getActivity());
        if (preferences.getBoolean("invertedColors", false)) {
            menu.findItem(R.id.action_night_mode).setIcon(R.drawable.ic_brightness_3_white_24dp);
        } else {
            menu.findItem(R.id.action_night_mode).setIcon(R.drawable.ic_brightness_5_white_24dp);
        }

        if (!getCol().undoAvailable()) {
            menu.findItem(R.id.action_undo).setVisible(false);
        } else {
            menu.findItem(R.id.action_undo).setVisible(true);
            Resources res = AnkiDroidApp.getAppResources();
            menu.findItem(R.id.action_undo).setTitle(res.getString(R.string.studyoptions_congrats_undo, getCol().undoName(res)));
        }
        super.onCreateOptionsMenu(menu, inflater);
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_undo:
                Timber.i("StudyOptionsFragment:: Undo button pressed");
                if (colOpen()) {
                    getCol().undo();
                    refreshInterfaceAndDecklist(true);
                    getActivity().supportInvalidateOptionsMenu();
                }
                return true;
            case R.id.action_night_mode:
                SharedPreferences preferences = AnkiDroidApp.getSharedPrefs(getActivity());
                if (preferences.getBoolean("invertedColors", false)) {
                    Timber.i("StudyOptionsFragment:: Night mode was disabled");
                    preferences.edit().putBoolean("invertedColors", false).commit();
                    item.setIcon(R.drawable.ic_brightness_5_white_24dp);
                } else {
                    Timber.i("StudyOptionsFragment:: Night mode was enabled");
                    preferences.edit().putBoolean("invertedColors", true).commit();
                    item.setIcon(R.drawable.ic_brightness_3_white_24dp);
                }
                return true;

            case R.id.action_add_note_from_study_options:
                Timber.i("StudyOptionsFragment:: Add note button pressed");
                addNote();
                return true;

            default:
                return super.onOptionsItemSelected(item);

        }
    }


    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);
        Timber.d("onActivityResult (requestCode = %d, resultCode = %d)", requestCode, resultCode);

        // rebuild action bar
        getActivity().supportInvalidateOptionsMenu();

        // boot back to deck picker if there was an error
        if (resultCode == DeckPicker.RESULT_DB_ERROR || resultCode == DeckPicker.RESULT_MEDIA_EJECTED) {
            closeStudyOptions(resultCode);
            return;
        }

        // check that the collection is open before doing anything
        if (!colOpen()) {
            startLoadingCollection();
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
            if (mLoadWithDeckOptions == true) {
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
                            new DeckTask.TaskData(getCol(), getCol().getDecks().selected(), mFragmented));
            } else {
                DeckTask.waitToFinish();
                refreshInterface(true);
            }
        } else if (requestCode == REQUEST_REVIEW) {
            if (resultCode == Reviewer.RESULT_NO_MORE_CARDS) {
                // If no more cards getting returned while counts > 0 then show a toast
                int[] counts = getCol().getSched().counts();
                if ((counts[0]+counts[1]+counts[2])>0) {
                    Toast.makeText(getActivity(), R.string.studyoptions_no_cards_due , Toast.LENGTH_LONG).show();
                }
            }
        } else if (requestCode == STATISTICS && mCurrentContentView == CONTENT_CONGRATS) {
            mCurrentContentView = CONTENT_STUDY_OPTIONS;
            setFragmentContentView(mStudyOptionsView);
        }
    }

    private void dismissProgressDialog() {
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
        // Exit if collection not open
        if (!colOpen()) {
            Timber.e("StudyOptionsFragment.refreshInterface failed due to Collection being closed");
            return;
        }
        Timber.d("Refreshing StudyOptionsFragment");
        // Load the deck counts for the deck from Collection asynchronously
        DeckTask.launchDeckTask(DeckTask.TASK_TYPE_UPDATE_VALUES_FROM_DECK,
                getDeckTaskListener(resetDecklist),
                new DeckTask.TaskData(getCol(), new Object[]{resetSched, mSmallChart != null}));
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
                    int eta = (Integer) obj[7];
                    double[][] serieslist = (double[][]) obj[8];

                    // Don't do anything if the fragment is no longer attached to it's Activity or col has been closed
                    if (getActivity() == null || !colOpen()) {
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
                        // Also set deck name in activity title in action bar if not tablet mode
                        if (!mFragmented) {
                            getActivity().setTitle(getResources().getString(R.string.studyoptions_title));
                            List<String> parts = Arrays.asList(fullName.split("::"));
                            ((AppCompatActivity)getActivity()).getSupportActionBar().setSubtitle(parts.get(parts.size() - 1));
                        }
                    } catch (JSONException e) {
                        throw new RuntimeException(e);
                    }

                    // open cram deck option if deck is opened for the first time
                    if (mLoadWithDeckOptions == true) {
                        openFilteredDeckOptions(mLoadWithDeckOptions);
                        return;
                    }

                    // Switch between the ordinary view and "congratulations" view
                    if (newCards + lrnCards + revCards == 0) {
                        mCurrentContentView = CONTENT_CONGRATS;
                        mDeckInfoLayout.setVisibility(View.GONE);
                        mCongratsLayout.setVisibility(View.VISIBLE);
                        mTextCongratsMessage.setText(getCol().getSched().finishedMsg(getActivity()));
                        mButtonStart.setVisibility(View.GONE);
                    } else {
                        mDeckCounts.setVisibility(View.VISIBLE); // not working without this... why?
                        mCurrentContentView = CONTENT_STUDY_OPTIONS;
                        mDeckInfoLayout.setVisibility(View.VISIBLE);
                        mCongratsLayout.setVisibility(View.GONE);
                        mButtonStart.setVisibility(View.VISIBLE);
                    }

                    // Set deck description
                    String desc;
                    try {
                        if (deck.getInt("dyn") == 0) {
                            desc = getCol().getDecks().getActualDescription();
                        } else {
                            desc = getResources().getString(R.string.dyn_deck_desc);
                        }
                    } catch (JSONException e) {
                        throw new RuntimeException(e);
                    }
                    if (desc.length() > 0) {
                        mTextDeckDescription.setText(Html.fromHtml(desc));
                        mTextDeckDescription.setVisibility(View.VISIBLE);
                    } else {
                        mTextDeckDescription.setVisibility(View.GONE);
                    }

                    // Update the chart (for tablet view)
                    updateChart(serieslist);

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

                    // Show unbury button if necessary
                    if (mButtonUnbury != null) {
                        if (getCol().getSched().haveBuried()) {
                            mButtonUnbury.setVisibility(View.VISIBLE);
                        } else {
                            mButtonUnbury.setVisibility(View.GONE);
                        }
                    }
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
        return mCollection;
    }

    private boolean colOpen() {
        return getCol() != null && getCol().getDb() != null;
    }

    // Method for loading the collection which is inherited by all AnkiActivitys
    protected void startLoadingCollection() {
        // Initialize the open collection loader
        Timber.d("startLoadingCollection()");
        AnkiActivity activity = (AnkiActivity) getActivity();
        activity.showProgressBar();
        getLoaderManager().initLoader(0, null, this);  
    }


    // CollectionLoader Listener callbacks
    @Override
    public Loader<Collection> onCreateLoader(int id, Bundle args) {
        // Currently only using one loader, so ignore id
        return new CollectionLoader(getActivity());
    }

    @Override
    public void onLoadFinished(Loader<Collection> loader, Collection col) {
        if (col != null) {
            onCollectionLoaded(col);
        } else {
            AnkiDatabaseManager.closeDatabase(CollectionHelper.getCollectionPath(getActivity()));
            //showDialog(DIALOG_LOAD_FAILED);
        }
    }


    @Override
    public void onLoaderReset(Loader<Collection> arg0) {
        // We don't currently retain any references, so no need to free any data here
    }
}
