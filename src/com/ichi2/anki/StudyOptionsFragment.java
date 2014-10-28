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
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.text.Html;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.ichi2.anim.ActivityTransitionAnimation;
import com.ichi2.anim.ViewAnimation;
import com.ichi2.anki.dialogs.TagsDialog;
import com.ichi2.anki.dialogs.TagsDialog.TagsDialogListener;
import com.ichi2.anki.stats.AnkiStatsTaskHandler;
import com.ichi2.anki.stats.ChartView;
import com.ichi2.async.CollectionLoader;
import com.ichi2.async.DeckTask;
import com.ichi2.async.DeckTask.TaskData;

import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Consts;
import com.ichi2.libanki.Utils;
import com.ichi2.themes.StyledDialog;
import com.ichi2.themes.StyledOpenCollectionDialog;
import com.ichi2.themes.StyledProgressDialog;
import com.ichi2.themes.Themes;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.*;

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

    public static final int CUSTOM_STUDY_NEW = 1;
    public static final int CUSTOM_STUDY_REV = 2;
    public static final int CUSTOM_STUDY_FORGOT = 3;
    public static final int CUSTOM_STUDY_AHEAD = 4;
    public static final int CUSTOM_STUDY_RANDOM = 5;
    public static final int CUSTOM_STUDY_PREVIEW = 6;
    public static final int CUSTOM_STUDY_TAGS = 7;
    /**
     * Constants for selecting which content view to display
     */
    public static final int CONTENT_STUDY_OPTIONS = 0;
    public static final int CONTENT_CONGRATS = 1;

    private static final int DIALOG_STATISTIC_TYPE = 0;
    private static final int DIALOG_CUSTOM_STUDY = 1;
    private static final int DIALOG_CUSTOM_STUDY_DETAILS = 2;
    private static final int DIALOG_CUSTOM_STUDY_TAGS = 3;

    // Threshold at which the total number of new cards is truncated by libanki
    private static final int NEW_CARD_COUNT_TRUNCATE_THRESHOLD = 1000;

    private int mCustomDialogChoice;

    private HashMap<Integer, StyledDialog> mDialogs = new HashMap<Integer, StyledDialog>();

    /**
     * Preferences
     */
    private int mCurrentContentView = CONTENT_STUDY_OPTIONS;
    boolean mInvertedColors = false;

    /** Alerts to inform the user about different situations */
    private StyledProgressDialog mProgressDialog;

    /**
     * UI elements for "Study Options" view
     */
    private View mStudyOptionsView;
    private View mDeckInfoLayout;
    private Button mButtonStart;
    private Button mButtonCustomStudy;
    private Button mButtonUnbury;
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


    private View mCustomStudyDetailsView;
    private TextView mCustomStudyTextView1;
    private TextView mCustomStudyTextView2;
    private EditText mCustomStudyEditText;

    private String mSearchTerms;

    public Bundle mCramInitialConfig = null;

    private boolean mFragmented;
    private Collection mCollection;
    private StyledOpenCollectionDialog mOpenCollectionDialog;

    private Thread mFullNewCountThread = null;

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
                    openReviewer();
                    return;
                case R.id.studyoptions_custom:
                    showDialog(DIALOG_CUSTOM_STUDY);
                    return;
                case R.id.studyoptions_unbury:
                    col.getSched().unburyCardsForDeck();
                    resetAndRefreshInterface();
                    return;
                case R.id.studyoptions_options_cram:
                    openCramDeckOptions();
                    return;
                case R.id.studyoptions_options:
                    Intent i = new Intent(getActivity(), DeckOptions.class);
                    startActivityForResult(i, DECK_OPTIONS);
                    ActivityTransitionAnimation.slide(getActivity(), ActivityTransitionAnimation.FADE);
                    return;
                case R.id.studyoptions_rebuild_cram:
                    rebuildCramDeck();
                    return;
                case R.id.studyoptions_empty_cram:
                    mProgressDialog = StyledProgressDialog.show(getActivity(), "",
                            getResources().getString(R.string.empty_cram_deck), true);
                    DeckTask.launchDeckTask(DeckTask.TASK_TYPE_EMPTY_CRAM, getDeckTaskListener(true),
                            new DeckTask.TaskData(col, col.getDecks().selected(), mFragmented));
                    return;
                default:
                    return;
            }
        }
    };


    private void openCramDeckOptions() {
        openCramDeckOptions(null);
    }


    private void openCramDeckOptions(Bundle initialConfig) {
        Intent i = new Intent(getActivity(), CramDeckOptions.class);
        i.putExtra("cramInitialConfig", initialConfig);
        startActivityForResult(i, DECK_OPTIONS);
        ActivityTransitionAnimation.slide(getActivity(), ActivityTransitionAnimation.FADE);
    }


    private void rebuildCramDeck() {
        mProgressDialog = StyledProgressDialog.show(getActivity(), "",
                getResources().getString(R.string.rebuild_cram_deck), true);
        DeckTask.launchDeckTask(DeckTask.TASK_TYPE_REBUILD_CRAM, getDeckTaskListener(true), new DeckTask.TaskData(
                getCol(), getCol().getDecks().selected(), mFragmented));
    }


    public static StudyOptionsFragment newInstance(long deckId, Bundle cramInitialConfig) {
        StudyOptionsFragment f = new StudyOptionsFragment();

        // Supply index input as an argument.
        Bundle args = new Bundle();
        args.putLong("deckId", deckId);
        args.putBundle("cramInitialConfig", cramInitialConfig);
        f.setArguments(args);

        return f;
    }


    public long getShownIndex() {
        return getArguments().getLong("deckId", 0);
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        if (container == null) {
            // Currently in a layout without a container, so no reason to create our view.
            return null;
        }

        return createView(inflater, savedInstanceState);
    }


    protected View createView(LayoutInflater inflater, Bundle savedInstanceState) {
        // Log.i(AnkiDroidApp.TAG, "StudyOptions - createView()");
        restorePreferences();
        mStudyOptionsView = inflater.inflate(R.layout.studyoptions_fragment, null);
        mCustomStudyDetailsView = inflater.inflate(R.layout.styled_custom_study_details_dialog, null);
        mFragmented = getActivity().getClass() != StudyOptionsActivity.class;
        startLoadingCollection();
        return mStudyOptionsView;
    }
    
    // Called when the collection loader has finished
    // NOTE: Fragment transactions are NOT allowed to be called from here onwards
    private void onCollectionLoaded(Collection col) {
        mCollection = col;
        initAllContentViews();
        mCramInitialConfig = getArguments().getBundle("cramInitialConfig");
        resetAndRefreshInterface(false);
        setHasOptionsMenu(true);
        dismissCollectionLoadingDialog();
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
        // Log.i(AnkiDroidApp.TAG, "StudyOptions - onDestroy()");
    }


    @Override
    public void onResume() {
        super.onResume();
        if (colOpen() && !mFragmented) {
            // Log.i(AnkiDroidApp.TAG, "StudyOptionsFragment.onResume() -- refreshing interface");
            // If not in tablet mode then reload deck counts (reload is taken care of by DeckPicker when mFragmented)
            if (Utils.now() > getCol().getSched().getDayCutoff()) {
                resetAndRefreshInterface(false);
            } else {
                refreshInterface();
            }
        } else {
            // Log.i(AnkiDroidApp.TAG, "StudyOptionsFragment.onResume() -- skipping refresh of interface");
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
            Log.e(AnkiDroidApp.TAG, "closeStudyOptions() failed due to getActivity() returning null");
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
        mButtonStart = (Button) mStudyOptionsView.findViewById(R.id.studyoptions_start);
        mButtonCustomStudy = (Button) mStudyOptionsView.findViewById(R.id.studyoptions_custom);
        mDeckOptions = (Button) mStudyOptionsView.findViewById(R.id.studyoptions_options);
        mCramOptions = (Button) mStudyOptionsView.findViewById(R.id.studyoptions_options_cram);
        mCongratsLayout = mStudyOptionsView.findViewById(R.id.studyoptions_congrats_layout);
        mTextCongratsMessage = (TextView) mStudyOptionsView.findViewById(R.id.studyoptions_congrats_message);


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

        // The view that shows the learn more options
        mCustomStudyTextView1 = (TextView) mCustomStudyDetailsView.findViewById(R.id.custom_study_details_text1);
        mCustomStudyTextView2 = (TextView) mCustomStudyDetailsView.findViewById(R.id.custom_study_details_text2);
        mCustomStudyEditText = (EditText) mCustomStudyDetailsView.findViewById(R.id.custom_study_details_edittext2);

        /*
         * When creating a new filtered deck after reviewing, there are several options. For selecting several tags, we
         * need a new, different dialog, that allows to select a list of tags:
         */

    }


    private void showDialog(int id) {
        if (!mDialogs.containsKey(id)) {
            mDialogs.put(id, onCreateDialog(id));
        }
        onPrepareDialog(id, mDialogs.get(id));
        mDialogs.get(id).show();
    }


    private DialogFragment showDialogFragment(int id) {

        DialogFragment dialogFragment = null;
        String tag = null;
        switch(id) {
            case DIALOG_CUSTOM_STUDY_TAGS:
                /*
                 * This handles the case where we want to create a Custom Study Deck using tags. This dialog needs to be
                 * different from the normal Custom Study dialogs, because more information is required: --List of Tags
                 * to select. --Which cards to select --(New cards, Due cards, or all cards, as in the desktop version)
                 */

                TagsDialog dialog = com.ichi2.anki.dialogs.TagsDialog.newInstance(
                    TagsDialog.TYPE_CUSTOM_STUDY_TAGS, new ArrayList<String>(),
                    new ArrayList<String>(getCol().getTags().all()));

                dialog.setTagsDialogListener(new TagsDialogListener() {
                    @Override
                    public void onPositive(List<String> selectedTags, int option) {
                        /*
                         * Here's the method that gathers the final selection of tags, type of cards and generates the search
                         * screen for the custom study deck.
                         */
                        mCustomStudyEditText.setText("");
                        String tags = selectedTags.toString();
                        mCustomStudyEditText.setHint(getResources().getString(R.string.card_browser_tags_shown,
                                tags.substring(1, tags.length() - 1)));
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
                        createFilteredDeck(new JSONArray(), new Object[] { mSearchTerms, Consts.DYN_MAX_SIZE,
                                Consts.DYN_RANDOM }, false);
                    }
                });

                dialogFragment = dialog;
                break;
            default:
                break;
        }

        dialogFragment.show(getFragmentManager(), tag);
        return dialogFragment;
    }

    private void onPrepareDialog(int id, StyledDialog styledDialog) {
        Resources res = getResources();
        switch (id) {
            case DIALOG_CUSTOM_STUDY_DETAILS:
                styledDialog.setTitle(res.getStringArray(R.array.custom_study_options_labels)[mCustomDialogChoice]);
                switch (mCustomDialogChoice + 1) {
                    case CUSTOM_STUDY_NEW:
                        if (colOpen()) {
                            Collection col = getCol();
                            mCustomStudyTextView1.setText(res.getString(R.string.custom_study_new_total_new, col
                                    .getSched().totalNewForCurrentDeck()));
                        }
                        mCustomStudyTextView2.setText(res.getString(R.string.custom_study_new_extend));
                        mCustomStudyEditText.setText(Integer.toString(AnkiDroidApp.getSharedPrefs(getActivity())
                                .getInt("extendNew", 10)));
                        styledDialog.setButtonOnClickListener(Dialog.BUTTON_POSITIVE,
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        if (colOpen()) {
                                            try {
                                                int n = Integer.parseInt(mCustomStudyEditText.getText().toString());
                                                AnkiDroidApp.getSharedPrefs(getActivity()).edit()
                                                        .putInt("extendNew", n).commit();
                                                Collection col = getCol();
                                                JSONObject deck = col.getDecks().current();
                                                deck.put("extendNew", n);
                                                col.getDecks().save(deck);
                                                col.getSched().extendLimits(n, 0);
                                                resetAndRefreshInterface();
                                            } catch (NumberFormatException e) {
                                                // ignore non numerical values
                                                Themes.showThemedToast(getActivity().getBaseContext(), getResources()
                                                        .getString(R.string.custom_study_invalid_number), false);
                                            } catch (JSONException e) {
                                                throw new RuntimeException(e);
                                            }
                                        }
                                    }
                                });
                        break;

                    case CUSTOM_STUDY_REV:
                        if (colOpen()) {
                            Collection col = getCol();
                            mCustomStudyTextView1.setText(res.getString(R.string.custom_study_rev_total_rev, col
                                    .getSched().totalRevForCurrentDeck()));
                        }
                        mCustomStudyTextView2.setText(res.getString(R.string.custom_study_rev_extend));
                        mCustomStudyEditText.setText(Integer.toString(AnkiDroidApp.getSharedPrefs(getActivity())
                                .getInt("extendRev", 10)));
                        styledDialog.setButtonOnClickListener(Dialog.BUTTON_POSITIVE,
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        if (colOpen()) {
                                            try {
                                                int n = Integer.parseInt(mCustomStudyEditText.getText().toString());
                                                AnkiDroidApp.getSharedPrefs(getActivity()).edit()
                                                        .putInt("extendRev", n).commit();
                                                Collection col = getCol();
                                                JSONObject deck = col.getDecks().current();
                                                deck.put("extendRev", n);
                                                col.getDecks().save(deck);
                                                col.getSched().extendLimits(0, n);
                                                resetAndRefreshInterface();
                                            } catch (NumberFormatException e) {
                                                // ignore non numerical values
                                                Themes.showThemedToast(getActivity().getBaseContext(), getResources()
                                                        .getString(R.string.custom_study_invalid_number), false);
                                            } catch (JSONException e) {
                                                throw new RuntimeException(e);
                                            }
                                        }
                                    }
                                });
                        break;

                    case CUSTOM_STUDY_FORGOT:
                        mCustomStudyTextView1.setText("");
                        mCustomStudyTextView2.setText(res.getString(R.string.custom_study_forgotten));
                        mCustomStudyEditText.setText(Integer.toString(AnkiDroidApp.getSharedPrefs(getActivity())
                                .getInt("forgottenDays", 2)));
                        styledDialog.setButtonOnClickListener(Dialog.BUTTON_POSITIVE,
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        JSONArray ar = new JSONArray();
                                        try {
                                            int forgottenDays = Integer.parseInt(((EditText) mCustomStudyEditText)
                                                    .getText().toString());
                                            ar.put(0, 1);
                                            createFilteredDeck(
                                                    ar,
                                                    new Object[] {
                                                            String.format(Locale.US, "rated:%d:1", forgottenDays),
                                                            Consts.DYN_MAX_SIZE, Consts.DYN_RANDOM }, false);
                                        } catch (NumberFormatException e) {
                                            // ignore non numerical values
                                            Themes.showThemedToast(getActivity().getBaseContext(), getResources()
                                                    .getString(R.string.custom_study_invalid_number), false);
                                        } catch (JSONException e) {
                                            throw new RuntimeException(e);
                                        }
                                    }
                                });
                        break;

                    case CUSTOM_STUDY_AHEAD:
                        mCustomStudyTextView1.setText("");
                        mCustomStudyTextView2.setText(res.getString(R.string.custom_study_ahead));
                        mCustomStudyEditText.setText(Integer.toString(AnkiDroidApp.getSharedPrefs(getActivity())
                                .getInt("aheadDays", 1)));
                        styledDialog.setButtonOnClickListener(Dialog.BUTTON_POSITIVE,
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        try {
                                            int days = Integer.parseInt(((EditText) mCustomStudyEditText).getText()
                                                    .toString());
                                            createFilteredDeck(new JSONArray(),
                                                    new Object[] { String.format(Locale.US, "prop:due<=%d", days),
                                                            Consts.DYN_MAX_SIZE, Consts.DYN_DUE }, true);
                                        } catch (NumberFormatException e) {
                                            // ignore non numerical values
                                            Themes.showThemedToast(getActivity().getBaseContext(), getResources()
                                                    .getString(R.string.custom_study_invalid_number), false);
                                        }
                                    }
                                });
                        break;

                    case CUSTOM_STUDY_RANDOM:
                        mCustomStudyTextView1.setText("");
                        mCustomStudyTextView2.setText(res.getString(R.string.custom_study_random));
                        mCustomStudyEditText.setText(Integer.toString(AnkiDroidApp.getSharedPrefs(getActivity())
                                .getInt("randomCards", 100)));
                        styledDialog.setButtonOnClickListener(Dialog.BUTTON_POSITIVE,
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        try {
                                            int randomCards = Integer.parseInt(((EditText) mCustomStudyEditText)
                                                    .getText().toString());
                                            createFilteredDeck(new JSONArray(), new Object[] { "", randomCards,
                                                    Consts.DYN_RANDOM }, true);
                                        } catch (NumberFormatException e) {
                                            // ignore non numerical values
                                            Themes.showThemedToast(getActivity().getBaseContext(), getResources()
                                                    .getString(R.string.custom_study_invalid_number), false);
                                        }
                                    }
                                });
                        break;

                    case CUSTOM_STUDY_PREVIEW:
                        mCustomStudyTextView1.setText("");
                        mCustomStudyTextView2.setText(res.getString(R.string.custom_study_preview));
                        mCustomStudyEditText.setText(Integer.toString(AnkiDroidApp.getSharedPrefs(getActivity())
                                .getInt("previewDays", 1)));
                        styledDialog.setButtonOnClickListener(Dialog.BUTTON_POSITIVE,
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        String previewDays = ((EditText) mCustomStudyEditText).getText().toString();
                                        createFilteredDeck(new JSONArray(),
                                                new Object[] { "is:new added:" + previewDays, Consts.DYN_MAX_SIZE,
                                                        Consts.DYN_OLDEST }, false);
                                    }
                                });
                        break;
                }
        }
    }


    protected StyledDialog onCreateDialog(int id) {
        StyledDialog dialog = null;
        Resources res = getResources();
        StyledDialog.Builder builder1 = new StyledDialog.Builder(this.getActivity());

        switch (id) {
            case DIALOG_STATISTIC_TYPE:
                boolean selectAllDecksButton = false;
                if(!(getActivity() instanceof Statistics)) {
                    if ((getActivity() instanceof DeckPicker && !mFragmented)) {
                        selectAllDecksButton = true;
                    }
                    AnkiStatsTaskHandler.setIsWholeCollection(selectAllDecksButton);
                    Intent intent = new Intent(getActivity(), Statistics.class);
                    startActivity(intent);
                }
                break;

            case DIALOG_CUSTOM_STUDY:
                builder1.setTitle(res.getString(R.string.custom_study));
                builder1.setIcon(android.R.drawable.ic_menu_sort_by_size);
                builder1.setItems(res.getStringArray(R.array.custom_study_options_labels),
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                mCustomDialogChoice = which;
                                if (which == CUSTOM_STUDY_TAGS - 1) {
                                    /*
                                     * There is a special Dialog for CUSTOM STUDY, where instead of only collecting a
                                     * number, it is necessary to collect a list of tags. This case handles the creation
                                     * of that Dialog.
                                     */
                                    showDialogFragment(DIALOG_CUSTOM_STUDY_TAGS);
                                    return;
                                }
                                showDialog(DIALOG_CUSTOM_STUDY_DETAILS);
                            }
                        });
                builder1.setCancelable(true);
                dialog = builder1.create();
                break;

            case DIALOG_CUSTOM_STUDY_DETAILS:
                /*
                 * This is the normal case for creating a custom study deck, where the dialog requires only a numeric
                 * input.
                 */
                builder1.setContentView(mCustomStudyDetailsView);
                builder1.setCancelable(true);
                builder1.setNegativeButton(R.string.dialog_cancel, null);
                builder1.setPositiveButton(R.string.dialog_ok, null);
                dialog = builder1.create();
                break;

            default:
                dialog = null;
                break;
        }

        dialog.setOwnerActivity(getActivity());
        return dialog;
    }

    private void createFilteredDeck(JSONArray delays, Object[] terms, Boolean resched) {
        JSONObject dyn;
        if (colOpen()) {
            Collection col = getCol();
            try {
                String deckName = col.getDecks().current().getString("name");
                String customStudyDeck = getResources().getString(R.string.custom_study_deck_name);
                JSONObject cur = col.getDecks().byName(customStudyDeck);
                if (cur != null) {
                    if (cur.getInt("dyn") != 1) {
                        StyledDialog.Builder builder = new StyledDialog.Builder(getActivity());
                        builder.setMessage(R.string.custom_study_deck_exists);
                        builder.setNegativeButton(getResources().getString(R.string.dialog_cancel),
                                new OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        //
                                    }
                                });
                        builder.create().show();
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
                dyn.put("delays", delays);
                JSONArray ar = dyn.getJSONArray("terms");
                ar.getJSONArray(0).put(0,
                        new StringBuilder("deck:\"").append(deckName).append("\" ").append(terms[0]).toString());
                ar.getJSONArray(0).put(1, terms[1]);
                ar.getJSONArray(0).put(2, terms[2]);
                dyn.put("resched", resched);


                // Initial rebuild
                mProgressDialog = StyledProgressDialog.show(getActivity(), "",
                        getResources().getString(R.string.rebuild_custom_study_deck), true);
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


    private void resetAndRefreshInterface() {
        refreshInterface(true, true);
    }


    private void resetAndRefreshInterface(boolean updateDeckList) {
        refreshInterface(true, updateDeckList);
    }


    private void refreshInterface() {
        refreshInterface(false, true);
    }


    private void refreshInterface(boolean reset, boolean updateDeckList) {
        // Exit if collection not open
        if (!colOpen()) {
            Log.e(AnkiDroidApp.TAG, "StudyOptionsFragment.refreshInterface failed due to Collection being closed");
            return;
        }
        // Initialize the Views in the fragment if they haven't already been initialized (See issue 2166)
        if (mTextDeckName == null) {
            initAllContentViews();
            resetAndRefreshInterface(updateDeckList);
            return;
        }
        // Load the deck counts for the deck from Collection asynchronously
        DeckTask.launchDeckTask(DeckTask.TASK_TYPE_UPDATE_VALUES_FROM_DECK, getDeckTaskListener(updateDeckList),
                new DeckTask.TaskData(getCol(), new Object[] { reset, mSmallChart != null }));
    }


    private void updateChart(double[][] serieslist) {
        if (mSmallChart != null) {

            ChartView chartView = (ChartView) mSmallChart.findViewById(R.id.chart_view_small_chart);
            chartView.setBackgroundColor(Color.BLACK);
            AnkiStatsTaskHandler.createSmallDueChartChart(serieslist, chartView);
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
            menu.findItem(R.id.action_night_mode).setIcon(R.drawable.ic_menu_night_checked);
        } else {
            menu.findItem(R.id.action_night_mode).setIcon(R.drawable.ic_menu_night);
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
                if (colOpen()) {
                    getCol().undo();
                    resetAndRefreshInterface();
                    getActivity().supportInvalidateOptionsMenu();
                }
                return true;
            case R.id.action_night_mode:
                SharedPreferences preferences = AnkiDroidApp.getSharedPrefs(getActivity());
                if (preferences.getBoolean("invertedColors", false)) {
                    preferences.edit().putBoolean("invertedColors", false).commit();
                    item.setIcon(R.drawable.ic_menu_night);
                } else {
                    preferences.edit().putBoolean("invertedColors", true).commit();
                    item.setIcon(R.drawable.ic_menu_night_checked);
                }
                return true;

            case R.id.action_add_note_from_study_options:
                addNote();
                return true;

            default:
                return super.onOptionsItemSelected(item);

        }
    }


    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);
        // Log.i(AnkiDroidApp.TAG, "StudyOptionsFragment: onActivityResult");
        getActivity().supportInvalidateOptionsMenu();
        if (resultCode == DeckPicker.RESULT_DB_ERROR) {
            closeStudyOptions(DeckPicker.RESULT_DB_ERROR);
        }

        // TODO: proper integration of big widget
        if (resultCode == DeckPicker.RESULT_MEDIA_EJECTED) {
            closeStudyOptions(DeckPicker.RESULT_MEDIA_EJECTED);
        } else {
            if (!colOpen()) {
                startLoadingCollection();
                return;
            }
            if (requestCode == DECK_OPTIONS) {
                if (mCramInitialConfig != null) {
                    mCramInitialConfig = null;
                    try {
                        JSONObject deck = getCol().getDecks().current();
                        if (deck.getInt("dyn") != 0 && deck.has("empty")) {
                            deck.remove("empty");
                        }
                    } catch (JSONException e) {
                        throw new RuntimeException(e);
                    }
                    rebuildCramDeck();
                } else {
                    DeckTask.waitToFinish();
                    resetAndRefreshInterface();
                }
            } else if (requestCode == ADD_NOTE && resultCode != Activity.RESULT_CANCELED) {
                resetAndRefreshInterface();
            } else if (requestCode == REQUEST_REVIEW) {
                // Log.i(AnkiDroidApp.TAG, "Result code = " + resultCode);
                resetAndRefreshInterface();
            } else if (requestCode == BROWSE_CARDS
                    && (resultCode == Activity.RESULT_OK || resultCode == Activity.RESULT_CANCELED)) {
                resetAndRefreshInterface();
            } else if (requestCode == STATISTICS && mCurrentContentView == CONTENT_CONGRATS) {
                resetAndRefreshInterface();
                mCurrentContentView = CONTENT_STUDY_OPTIONS;
                setFragmentContentView(mStudyOptionsView);
            }
        }
    }

    private void dismissProgressDialog() {
        // for rebuilding cram decks
        if (mProgressDialog != null && mProgressDialog.isShowing()) {
            try {
                mProgressDialog.dismiss();
            } catch (Exception e) {
                Log.e(AnkiDroidApp.TAG, "onPostExecute - Dialog dismiss Exception = " + e.getMessage());
            }
        }
    }


    public SharedPreferences restorePreferences() {
        SharedPreferences preferences = AnkiDroidApp.getSharedPrefs(getActivity().getBaseContext());
        return preferences;
    }


    private DeckTask.TaskListener getDeckTaskListener(boolean updateDeckList) {
        if (mFragmented && updateDeckList) {
            return mRefreshWholeInterfaceListener;
        } else {
            return mRefreshFragmentListener;
        }
    }


    /** This listener does full refresh of the interface, which includes updating deck list 
     * when in tablet mode
     */
    DeckTask.TaskListener mRefreshWholeInterfaceListener = new DeckTask.TaskListener() {
        @Override
        public void onPostExecute(TaskData result) {
            dismissProgressDialog();
            ((DeckPicker) getActivity()).refreshMainInterface();
        }

        @Override
        public void onPreExecute() {
        }

        @Override
        public void onProgressUpdate(TaskData... values) {
        }

        @Override
        public void onCancelled() {
        }
    };

    /** This is the main code which sets all the elements in the Fragment.
     * It can be used by any async task which returns the parameters necessary
     * for rebuilding the interface
     */
    DeckTask.TaskListener mRefreshFragmentListener = new DeckTask.TaskListener() {
        @Override
        public void onPostExecute(DeckTask.TaskData result) {
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
                    Log.e(AnkiDroidApp.TAG, "StudyOptionsFragment.mUpdateValuesFromDeckListener -- returning without updating");
                    return;
                }

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
                        AnkiDroidApp.getCompat().setSubtitle(getActivity(), parts.get(parts.size() - 1));
                    }
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }

                // open cram deck option if deck is opened for the first time
                if (mCramInitialConfig != null) {
                    openCramDeckOptions(mCramInitialConfig);
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
                            final int fullNewCount = collection.getDb().queryScalar(sbQuery.toString(), false);
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
            // for rebuilding cram decks
            dismissProgressDialog();
        }


        @Override
        public void onPreExecute() {
        }


        @Override
        public void onProgressUpdate(DeckTask.TaskData... values) {
        }


        @Override
        public void onCancelled() {
        }
    };


    private Collection getCol() {
        return mCollection;
    }

    private boolean colOpen() {
        return getCol() != null && getCol().getDb() != null;
    }

    // Method for loading the collection which is inherited by all AnkiActivitys
    protected void startLoadingCollection() {
        // Initialize the open collection loader
        // Log.i(AnkiDroidApp.TAG, "StudyOptionsFragment.loadCollection()");
        if (AnkiDroidApp.getCol() == null) {
            showCollectionLoadingDialog();
        }
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
            AnkiDatabaseManager.closeDatabase(AnkiDroidApp.getCollectionPath());
            //showDialog(DIALOG_LOAD_FAILED);
        }
    }


    @Override
    public void onLoaderReset(Loader<Collection> arg0) {
        // We don't currently retain any references, so no need to free any data here
    }

    // Open collection dialog
    public void showCollectionLoadingDialog() {
        if (mOpenCollectionDialog == null || !mOpenCollectionDialog.isShowing()) {
            mOpenCollectionDialog = StyledOpenCollectionDialog.show(getActivity(), getResources().getString(R.string.open_collection), 
                    new OnCancelListener() {@Override public void onCancel(DialogInterface arg0) {}}
            );
        }
    }


    // Dismiss progress dialog
    public void dismissCollectionLoadingDialog() {
        if (mOpenCollectionDialog != null && mOpenCollectionDialog.isShowing()) {
            mOpenCollectionDialog.dismiss();
        }
    }
}
