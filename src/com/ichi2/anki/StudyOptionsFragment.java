/***************************************************************************************
 * This program is free software; you can redistribute it and/or modify it under *
 * the terms of the GNU General Public License as published by the Free Software *
 * Foundation; either version 3 of the License, or (at your option) any later *
 * version. *
 * *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details. *
 * *
 * You should have received a copy of the GNU General Public License along with *
 * this program. If not, see <http://www.gnu.org/licenses/>. *
 ****************************************************************************************/

package com.ichi2.anki;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Paint.Align;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.GestureDetector;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.ichi2.anim.ActivityTransitionAnimation;
import com.ichi2.anim.ViewAnimation;
import com.ichi2.async.DeckTask;
import com.ichi2.charts.ChartBuilder;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Stats;
import com.ichi2.libanki.Utils;
import com.ichi2.themes.StyledDialog;
import com.ichi2.themes.StyledOpenCollectionDialog;
import com.ichi2.themes.StyledProgressDialog;
import com.ichi2.themes.Themes;

import org.achartengine.ChartFactory;
import org.achartengine.GraphicalView;
import org.achartengine.chart.BarChart;
import org.achartengine.model.XYMultipleSeriesDataset;
import org.achartengine.model.XYSeries;
import org.achartengine.renderer.XYMultipleSeriesRenderer;
import org.achartengine.renderer.XYSeriesRenderer;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;

public class StudyOptionsFragment extends Fragment {

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

    private static final int DIALOG_STATISTIC_TYPE = 0;

    private HashMap<Integer, StyledDialog> mDialogs = new HashMap<Integer, StyledDialog>();

    /**
     * Preferences
     */
    private int mStartedByBigWidget;
    private boolean mSwipeEnabled;
    private int mCurrentContentView;
    boolean mInvertedColors = false;

    private boolean mDontSaveOnStop = false;

    /** Alerts to inform the user about different situations */
    private StyledProgressDialog mProgressDialog;
    private StyledOpenCollectionDialog mOpenCollectionDialog;

    /**
     * UI elements for "Study Options" view
     */
    private View mStudyOptionsView;
    private Button mButtonStart;
//    private Button mButtonUp;
//    private Button mButtonDown;
//    private ToggleButton mToggleLimitToggle;
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
    private ImageButton mAddNote;
    private ImageButton mCardBrowser;
    private ImageButton mDeckOptions;
    private ImageButton mStatisticsButton;

    /**
     * UI elements for "Congrats" view
     */
    private View mCongratsView;
    private TextView mTextCongratsMessage;
    private Button mButtonCongratsOpenOtherDeck;
    private Button mButtonCongratsFinish;

    /**
     * Swipe Detection
     */
    private GestureDetector gestureDetector;
    View.OnTouchListener gestureListener;

    /**
     * Statistics
     */
    public static int mStatisticType;
    private View mBarsMax;
    private View mGlobalBar;
    private View mGlobalMatBar;
    private double mProgressMature;
    private double mProgressAll;

    private boolean mFragmented;

    /**
     * Callbacks for UI events
     */
    private View.OnClickListener mButtonClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
        	Collection col = AnkiDroidApp.getCol();
//            long timeLimit = 0;
            switch (v.getId()) {
                case R.id.studyoptions_start:
                    openReviewer();
                    return;
//                case R.id.studyoptions_limitup:
//                    timeLimit = (mCol.getTimeLimit() / 60);
//                    mCol.setTimeLimit((timeLimit + 1) * 60);
//                    mToggleLimitToggle.setChecked(true);
//                    mToggleLimitToggle.setText(String.valueOf(timeLimit + 1));
//                    return;
//                case R.id.studyoptions_limitdown:
//                    timeLimit = (mCol.getTimeLimit() / 60);
//                    if (timeLimit > 1) {
//                        mCol.setTimeLimit((timeLimit - 1) * 60);
//                        mToggleLimitToggle.setChecked(true);
//                        mToggleLimitToggle.setText(String.valueOf(timeLimit - 1));
//                    } else if (timeLimit == 1) {
//                        mCol.setTimeLimit(0);
//                        mToggleLimitToggle.setChecked(false);
//                    }
//                    return;
//                case R.id.studyoptions_limittoggle:
//                    timeLimit = (mCol.getTimeLimit() / 60);
//                    if (timeLimit > 0) {
//                        mToggleLimitToggle.setChecked(false);
//                        mCol.setTimeLimit(0);
//                    } else {
//                        mToggleLimitToggle.setChecked(true);
//                        mToggleLimitToggle.setText("1");
//                        mCol.setTimeLimit(60);
//                    }
//                    return;
                case R.id.studyoptions_congrats_open_other_deck:
                    closeStudyOptions();
                    return;
                case R.id.studyoptions_congrats_finish:
                    finishCongrats();
                    return;
                case R.id.studyoptions_card_browser:
                    openCardBrowser();
                    return;
                case R.id.studyoptions_statistics:
                    showDialog(DIALOG_STATISTIC_TYPE);
                    return;
                case R.id.studyoptions_congrats_message:
                    DeckTask.launchDeckTask(DeckTask.TASK_TYPE_LOAD_STATISTICS, mLoadStatisticsHandler,
                            new DeckTask.TaskData(col, Stats.TYPE_MONTH, false));
                    return;
                case R.id.studyoptions_options:
                    if (col.getDecks().isDyn(col.getDecks().selected())) {
                        openCramDeckOptions();
                    } else {
                        Intent i = new Intent(getActivity(), DeckOptions.class);
                        startActivityForResult(i, DECK_OPTIONS);
                        if (AnkiDroidApp.SDK_VERSION > 4) {
                            ActivityTransitionAnimation.slide(getActivity(), ActivityTransitionAnimation.FADE);
                        }
                    }
                    return;
                case R.id.studyoptions_rebuild_cram:
                    rebuildCramDeck();
                    return;
                case R.id.studyoptions_empty_cram:
                    mProgressDialog = StyledProgressDialog.show(getActivity(), "",
                            getResources().getString(R.string.empty_cram_deck), true);
                    DeckTask.launchDeckTask(DeckTask.TASK_TYPE_EMPTY_CRAM, mUpdateValuesFromDeckListener,
                            new DeckTask.TaskData(col, col.getDecks().selected(), mFragmented));
                    return;
                case R.id.studyoptions_add:
                    addNote();
                    return;
                default:
                    return;
            }
        }
    };


    private void openCramDeckOptions() {
        Intent i = new Intent(getActivity(), CramDeckOptions.class);
        startActivityForResult(i, DECK_OPTIONS);
        if (AnkiDroidApp.SDK_VERSION > 4) {
            ActivityTransitionAnimation.slide(getActivity(), ActivityTransitionAnimation.FADE);
        }
    }


    private void rebuildCramDeck() {
        mProgressDialog = StyledProgressDialog.show(getActivity(), "",
                getResources().getString(R.string.rebuild_cram_deck), true);
        DeckTask.launchDeckTask(DeckTask.TASK_TYPE_REBUILD_CRAM, mUpdateValuesFromDeckListener, new DeckTask.TaskData(
        		AnkiDroidApp.getCol(), AnkiDroidApp.getCol().getDecks().selected(), mFragmented));
    }


    public static StudyOptionsFragment newInstance(long deckId, boolean onlyFnsMsg) {
        StudyOptionsFragment f = new StudyOptionsFragment();

        // Supply index input as an argument.
        Bundle args = new Bundle();
        args.putLong("deckId", deckId);
        args.putBoolean("onlyFnsMsg", onlyFnsMsg);
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

        // ScrollView scroller = new ScrollView(getActivity());
        // TextView text = new TextView(getActivity());
        // int padding = (int)TypedValue.applyDimension(
        // TypedValue.COMPLEX_UNIT_DIP,
        // 4, getActivity().getResources().getDisplayMetrics());
        // text.setPadding(padding, padding, padding, padding);
        // scroller.addView();
        return createView(inflater, savedInstanceState);
    }


    protected View createView(LayoutInflater inflater, Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Log.i(AnkiDroidApp.TAG, "StudyOptions - createView()");

        restorePreferences();

        mFragmented = getActivity().getClass() != StudyOptionsActivity.class;

        if (!AnkiDroidApp.colIsOpen()) {
            reloadCollection();
            return null;
        }

//        Intent intent = getActivity().getIntent();
//        if (intent != null && intent.hasExtra(DeckPicker.EXTRA_DECK_ID)) {
//            mCol.getDecks().select(intent.getLongExtra(DeckPicker.EXTRA_DECK_ID, 1));
//        }

        initAllContentViews(inflater);

        if (mSwipeEnabled) {
            gestureDetector = new GestureDetector(new MyGestureDetector());
            gestureListener = new View.OnTouchListener() {
                public boolean onTouch(View v, MotionEvent event) {
                    if (gestureDetector.onTouchEvent(event)) {
                        return true;
                    }
                    return false;
                }
            };
        }

        if (getArguments().getBoolean("onlyFnsMsg")) {
            mTextCongratsMessage.setText(AnkiDroidApp.getCol().getSched().finishedMsg(getActivity()));
            mButtonCongratsFinish.setVisibility(View.GONE);
            return mCongratsView;
        }

        resetAndUpdateValuesFromDeck();        	

        return mStudyOptionsView;
    }


    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        Log.i(AnkiDroidApp.TAG, "onConfigurationChanged");
        mDontSaveOnStop = true;
        CharSequence title = mTextDeckName.getText();
        CharSequence desc = mTextDeckDescription.getText();
        int descVisibility = mTextDeckDescription.getVisibility();
        CharSequence newToday = mTextTodayNew.getText();
        CharSequence lrnToday = mTextTodayLrn.getText();
        CharSequence revToday = mTextTodayRev.getText();
        CharSequence newTotal = mTextNewTotal.getText();
        CharSequence total = mTextTotal.getText();
        CharSequence eta = mTextETA.getText();
//        long timelimit = mCol.getTimeLimit() / 60;
        super.onConfigurationChanged(newConfig);
        mDontSaveOnStop = false;
//        initAllContentViews();
        if (mCurrentContentView == CONTENT_CONGRATS) {
            setFragmentContentView(mCongratsView);
        }
        mTextDeckName.setText(title);
        mTextDeckName.setVisibility(View.VISIBLE);
        mTextDeckDescription.setText(desc);
        mTextDeckDescription.setVisibility(descVisibility);
        mDeckCounts.setVisibility(View.VISIBLE);
        mTextTodayNew.setText(newToday);
        mTextTodayLrn.setText(lrnToday);
        mTextTodayRev.setText(revToday);
        mTextNewTotal.setText(newTotal);
        mTextTotal.setText(total);
        mTextETA.setText(eta);

//        mToggleLimitToggle.setChecked(timelimit > 0 ? true : false);
//        if (timelimit > 0) {
//            mToggleLimitToggle.setText(String.valueOf(timelimit));
//        }
        updateStatisticBars();
    }


    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i(AnkiDroidApp.TAG, "StudyOptions - onDestroy()");
        // if (mUnmountReceiver != null) {
        // unregisterReceiver(mUnmountReceiver);
        // }
    }


    @Override
    public void onPause() {
        super.onPause();
    }


    @Override
    public void onResume() {
        super.onResume();
        if (AnkiDroidApp.colIsOpen()) {
            if (Utils.now() > AnkiDroidApp.getCol().getSched().getDayCutoff()) {
                updateValuesFromDeck(true);
            }
        }
    }


    private void closeStudyOptions() {
        getActivity();
        closeStudyOptions(Activity.RESULT_OK);
    }


    private void closeStudyOptions(int result) {
        // mCompat.invalidateOptionsMenu(this);
        if (!mFragmented) {
            getActivity().setResult(result);
            getActivity().finish();
            if (AnkiDroidApp.SDK_VERSION > 4) {
                ActivityTransitionAnimation.slide(getActivity(), ActivityTransitionAnimation.RIGHT);
            }
        }
    }


    private void openReviewer() {
        mDontSaveOnStop = true;
        Intent reviewer = new Intent(getActivity(), Reviewer.class);
        startActivityForResult(reviewer, REQUEST_REVIEW);
        if (AnkiDroidApp.SDK_VERSION > 4) {
            ActivityTransitionAnimation.slide(getActivity(), ActivityTransitionAnimation.LEFT);
        }
        AnkiDroidApp.getCol().startTimebox();
    }


    private void addNote() {
        Intent intent = new Intent(getActivity(), CardEditor.class);
        intent.putExtra(CardEditor.EXTRA_CALLER, CardEditor.CALLER_STUDYOPTIONS);
        startActivityForResult(intent, ADD_NOTE);
        if (AnkiDroidApp.SDK_VERSION > 4) {
            ActivityTransitionAnimation.slide(getActivity(), ActivityTransitionAnimation.LEFT);
        }
    }


    public void reloadCollection() {
        DeckTask.launchDeckTask(
                DeckTask.TASK_TYPE_OPEN_COLLECTION,
                new DeckTask.TaskListener() {

                    @Override
                    public void onPostExecute(DeckTask.TaskData result) {
                        if (mOpenCollectionDialog.isShowing()) {
                            try {
                            	mOpenCollectionDialog.dismiss();
                            } catch (Exception e) {
                                Log.e(AnkiDroidApp.TAG, "onPostExecute - Dialog dismiss Exception = " + e.getMessage());
                            }
                        }
                        if (!AnkiDroidApp.colIsOpen()) {
                        	closeStudyOptions();
                        } else if (!mFragmented) {
                        	((StudyOptionsActivity)getActivity()).loadContent(false);
                        }
                    }


                    @Override
                    public void onPreExecute() {
                    	mOpenCollectionDialog = StyledOpenCollectionDialog.show(getActivity(), getResources().getString(R.string.open_collection), new OnCancelListener() {
                                    @Override
                                    public void onCancel(DialogInterface arg0) {
                                    	closeStudyOptions();
                                    }
                                });
                    }


                    @Override
                    public void onProgressUpdate(DeckTask.TaskData... values) {
                    }
                },
                new DeckTask.TaskData(AnkiDroidApp.getSharedPrefs(getActivity().getBaseContext()).getString("deckPath",
                        AnkiDroidApp.getDefaultAnkiDroidDirectory())
                        + AnkiDroidApp.COLLECTION_PATH));
    }


    private void initAllContentViews(LayoutInflater inflater) {
        mStudyOptionsView = inflater.inflate(R.layout.studyoptions, null);
        Themes.setContentStyle(mStudyOptionsView, Themes.CALLER_STUDYOPTIONS);
        mTextDeckName = (TextView) mStudyOptionsView.findViewById(R.id.studyoptions_deck_name);
        mTextDeckDescription = (TextView) mStudyOptionsView.findViewById(R.id.studyoptions_deck_description);
        mButtonStart = (Button) mStudyOptionsView.findViewById(R.id.studyoptions_start);
//        mButtonUp = (Button) mStudyOptionsView.findViewById(R.id.studyoptions_limitup);
//        mButtonDown = (Button) mStudyOptionsView.findViewById(R.id.studyoptions_limitdown);
//        mToggleLimitToggle = (ToggleButton) mStudyOptionsView.findViewById(R.id.studyoptions_limittoggle);
        // mToggleCram = (ToggleButton) mStudyOptionsView
        // .findViewById(R.id.studyoptions_cram);
        // mToggleNight = (ToggleButton) mStudyOptionsView
        // .findViewById(R.id.studyoptions_night);
        // mToggle.setChecked(mInvertedColors);

        if (AnkiDroidApp.colIsOpen() && AnkiDroidApp.getCol().getDecks().isDyn(AnkiDroidApp.getCol().getDecks().selected())) {
            Button rebBut = (Button) mStudyOptionsView.findViewById(R.id.studyoptions_rebuild_cram);
            rebBut.setOnClickListener(mButtonClickListener);
            Button emptyBut = (Button) mStudyOptionsView.findViewById(R.id.studyoptions_empty_cram);
            emptyBut.setOnClickListener(mButtonClickListener);
            ((LinearLayout) mStudyOptionsView.findViewById(R.id.studyoptions_cram_buttons)).setVisibility(View.VISIBLE);
        }

        if (mFragmented) {
            Button but = (Button) mStudyOptionsView.findViewById(R.id.studyoptions_options);
            but.setOnClickListener(mButtonClickListener);
        } else {
            mAddNote = (ImageButton) mStudyOptionsView.findViewById(R.id.studyoptions_add);
            mCardBrowser = (ImageButton) mStudyOptionsView.findViewById(R.id.studyoptions_card_browser);
            mStatisticsButton = (ImageButton) mStudyOptionsView.findViewById(R.id.studyoptions_statistics);
            mDeckOptions = (ImageButton) mStudyOptionsView.findViewById(R.id.studyoptions_options);
            mAddNote.setOnClickListener(mButtonClickListener);
            mCardBrowser.setOnClickListener(mButtonClickListener);
            mStatisticsButton.setOnClickListener(mButtonClickListener);
            mDeckOptions.setOnClickListener(mButtonClickListener);
        }

        mGlobalBar = (View) mStudyOptionsView.findViewById(R.id.studyoptions_global_bar);
        mGlobalMatBar = (View) mStudyOptionsView.findViewById(R.id.studyoptions_global_mat_bar);
        mBarsMax = (View) mStudyOptionsView.findViewById(R.id.studyoptions_progressbar_content);
        mTextTodayNew = (TextView) mStudyOptionsView.findViewById(R.id.studyoptions_new);
        mTextTodayLrn = (TextView) mStudyOptionsView.findViewById(R.id.studyoptions_lrn);
        mTextTodayRev = (TextView) mStudyOptionsView.findViewById(R.id.studyoptions_rev);
        mTextNewTotal = (TextView) mStudyOptionsView.findViewById(R.id.studyoptions_total_new);
        mTextTotal = (TextView) mStudyOptionsView.findViewById(R.id.studyoptions_total);
        mTextETA = (TextView) mStudyOptionsView.findViewById(R.id.studyoptions_eta);
        mSmallChart = (LinearLayout) mStudyOptionsView.findViewById(R.id.studyoptions_mall_chart);

        mGlobalMatBar.setVisibility(View.INVISIBLE);
        mGlobalBar.setVisibility(View.INVISIBLE);

        mDeckCounts = (LinearLayout) mStudyOptionsView.findViewById(R.id.studyoptions_deckcounts);
        mDeckChart = (LinearLayout) mStudyOptionsView.findViewById(R.id.studyoptions_chart);

        mButtonStart.setOnClickListener(mButtonClickListener);
//        mButtonUp.setOnClickListener(mButtonClickListener);
//        mButtonDown.setOnClickListener(mButtonClickListener);
//        mToggleLimitToggle.setOnClickListener(mButtonClickListener);
        // mToggleCram.setOnClickListener(mButtonClickListener);
        // mToggleNight.setOnClickListener(mButtonClickListener);

        // The view that shows the congratulations view.
        mCongratsView = inflater.inflate(R.layout.studyoptions_congrats, null);

        Themes.setWallpaper(mCongratsView);

        mTextCongratsMessage = (TextView) mCongratsView.findViewById(R.id.studyoptions_congrats_message);
        Themes.setTextViewStyle(mTextCongratsMessage);

        mTextCongratsMessage.setOnClickListener(mButtonClickListener);
        // mButtonCongratsLearnMore = (Button) mCongratsView
        // .findViewById(R.id.studyoptions_congrats_learnmore);
        // mButtonCongratsReviewEarly = (Button) mCongratsView
        // .findViewById(R.id.studyoptions_congrats_reviewearly);
        mButtonCongratsOpenOtherDeck = (Button) mCongratsView.findViewById(R.id.studyoptions_congrats_open_other_deck);
        if (mFragmented) {
            mButtonCongratsOpenOtherDeck.setVisibility(View.GONE);
        }
        mButtonCongratsFinish = (Button) mCongratsView.findViewById(R.id.studyoptions_congrats_finish);

        // mButtonCongratsLearnMore.setOnClickListener(mButtonClickListener);
        // mButtonCongratsReviewEarly.setOnClickListener(mButtonClickListener);
        mButtonCongratsOpenOtherDeck.setOnClickListener(mButtonClickListener);
        mButtonCongratsFinish.setOnClickListener(mButtonClickListener);
    }


    private void showDialog(int id) {
        if (!mDialogs.containsKey(id)) {
            mDialogs.put(id, onCreateDialog(id));
        }
        onPrepareDialog(id, mDialogs.get(id));
        mDialogs.get(id).show();
    }


    private void onPrepareDialog(int id, StyledDialog styledDialog) {
    }


    protected StyledDialog onCreateDialog(int id) {
        StyledDialog dialog = null;

        switch (id) {

            case DIALOG_STATISTIC_TYPE:
                dialog = ChartBuilder.getStatisticsDialog(getActivity(), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        DeckTask.launchDeckTask(DeckTask.TASK_TYPE_LOAD_STATISTICS, mLoadStatisticsHandler,
                                new DeckTask.TaskData(AnkiDroidApp.getCol(), which, false));
                    }
                }, mFragmented);
                break;

            default:
                dialog = null;
        }

        dialog.setOwnerActivity(getActivity());
        return dialog;
    }


    void setFragmentContentView(View newView) {
        ViewGroup parent = (ViewGroup) this.getView();        
        parent.removeAllViews();
        parent.addView(newView);
    }

    public void resetAndUpdateValuesFromDeck() {
        updateValuesFromDeck(true);
    }


    private void updateValuesFromDeck() {
        updateValuesFromDeck(false);
    }


    private void updateValuesFromDeck(boolean reset) {
        String fullName;
        if (!AnkiDroidApp.colIsOpen()) {
        	return;
        }
        JSONObject deck = AnkiDroidApp.getCol().getDecks().current();
        try {
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

            // open cram deck option if deck is opened for the first time
            if (deck.getInt("dyn") != 0 && deck.has("empty") && deck.getBoolean("empty")) {
                openCramDeckOptions();
                return;
            }
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }

        if (!mFragmented) {
            getActivity().setTitle(fullName);
        }

        String desc = AnkiDroidApp.getCol().getDecks().getActualDescription();
        if (desc.length() > 0) {
            mTextDeckDescription.setText(desc);
            mTextDeckDescription.setVisibility(View.VISIBLE);
        } else {
            mTextDeckDescription.setVisibility(View.GONE);
        }

        DeckTask.launchDeckTask(DeckTask.TASK_TYPE_UPDATE_VALUES_FROM_DECK, mUpdateValuesFromDeckListener,
                new DeckTask.TaskData(AnkiDroidApp.getCol(), new Object[]{reset, mSmallChart != null}));
    }


    private void updateStatisticBars() {
        mBarsMax.getViewTreeObserver().addOnGlobalLayoutListener(new OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                mBarsMax.getViewTreeObserver().removeGlobalOnLayoutListener(this);
                int maxWidth = mBarsMax.getWidth() + 1;
                int height = mBarsMax.getHeight();
                int mat = (int) (mProgressMature * maxWidth);
                Utils.updateProgressBars(mGlobalMatBar, mat, height);
                Utils.updateProgressBars(mGlobalBar, (int) (mProgressAll * maxWidth) - mat, height);
                if (mGlobalMatBar.getVisibility() == View.INVISIBLE) {
                    mGlobalMatBar.setVisibility(View.VISIBLE);
                    mGlobalMatBar.setAnimation(ViewAnimation.fade(ViewAnimation.FADE_IN, 100, 0));
                    mGlobalBar.setVisibility(View.VISIBLE);
                    mGlobalBar.setAnimation(ViewAnimation.fade(ViewAnimation.FADE_IN, 100, 0));
                }
            }
        });
    }


    private void updateChart(double[][] serieslist) {
        if (mSmallChart != null) {
            Resources res = getResources();
            XYMultipleSeriesDataset dataset = new XYMultipleSeriesDataset();
            XYMultipleSeriesRenderer renderer = new XYMultipleSeriesRenderer();
            XYSeriesRenderer r = new XYSeriesRenderer();
            r.setColor(res.getColor(R.color.stats_young));
            renderer.addSeriesRenderer(r);
            r = new XYSeriesRenderer();
            r.setColor(res.getColor(R.color.stats_mature));
            renderer.addSeriesRenderer(r);

            for (int i = 1; i < serieslist.length; i++) {
                XYSeries series = new XYSeries("");
                for (int j = 0; j < serieslist[i].length; j++) {
                    series.add(serieslist[0][j], serieslist[i][j]);
                }
                dataset.addSeries(series);
            }
            renderer.setBarSpacing(0.4);
            renderer.setShowLegend(false);
            renderer.setLabelsTextSize(13);
            renderer.setXAxisMin(-0.5);
            renderer.setXAxisMax(7.5);
            renderer.setYAxisMin(0);
            renderer.setGridColor(Color.LTGRAY);
            renderer.setShowGrid(true);
            renderer.setBackgroundColor(Color.WHITE);
            renderer.setMarginsColor(Color.WHITE);
            renderer.setAxesColor(Color.BLACK);
            renderer.setLabelsColor(Color.BLACK);
            renderer.setYLabelsColor(0, Color.BLACK);
            renderer.setYLabelsAngle(-90);
            renderer.setXLabelsColor(Color.BLACK);
            renderer.setXLabelsAlign(Align.CENTER);
            renderer.setYLabelsAlign(Align.CENTER);
            renderer.setZoomEnabled(false, false);
            // mRenderer.setMargins(new int[] { 15, 48, 30, 10 });
            renderer.setAntialiasing(true);
            renderer.setPanEnabled(true, false);
            GraphicalView chartView = ChartFactory.getBarChartView(getActivity(), dataset, renderer,
                    BarChart.Type.STACKED);
            mSmallChart.addView(chartView, new LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT));
            if (mDeckChart.getVisibility() == View.INVISIBLE) {
                mDeckChart.setVisibility(View.VISIBLE);
                mDeckChart.setAnimation(ViewAnimation.fade(ViewAnimation.FADE_IN, 500, 0));
            }
        }
    }


    private void finishCongrats() {
        mStudyOptionsView.setVisibility(View.INVISIBLE);
        mCongratsView.setVisibility(View.INVISIBLE);
        mCongratsView.setAnimation(ViewAnimation.fade(ViewAnimation.FADE_OUT, 500, 0));
        setFragmentContentView(mStudyOptionsView);
        mStudyOptionsView.setVisibility(View.VISIBLE);
        mStudyOptionsView.setAnimation(ViewAnimation.fade(ViewAnimation.FADE_IN, 500, 0));
        mCongratsView.setVisibility(View.VISIBLE);
    }


    private void openCardBrowser() {
        mDontSaveOnStop = true;
        Intent cardBrowser = new Intent(getActivity(), CardBrowser.class);
        startActivityForResult(cardBrowser, BROWSE_CARDS);
        if (AnkiDroidApp.SDK_VERSION > 4) {
            ActivityTransitionAnimation.slide(getActivity(), ActivityTransitionAnimation.LEFT);
        }
    }


    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);
        Log.i(AnkiDroidApp.TAG, "StudyOptionsFragment: onActivityResult");

        if (resultCode == DeckPicker.RESULT_DB_ERROR) {
            closeStudyOptions(DeckPicker.RESULT_DB_ERROR);
        }

        if (resultCode == AnkiDroidApp.RESULT_TO_HOME) {
            closeStudyOptions();
            return;
        }

        // TODO: proper integration of big widget
        if (resultCode == DeckPicker.RESULT_MEDIA_EJECTED) {
            closeStudyOptions(DeckPicker.RESULT_MEDIA_EJECTED);
        } else {
            if (!AnkiDroidApp.colIsOpen()) {
                reloadCollection();
                mDontSaveOnStop = false;
                return;
            }
        	if (requestCode == DECK_OPTIONS) {
                try {
                    JSONObject deck = AnkiDroidApp.getCol().getDecks().current();
                    if (deck.getInt("dyn") != 0 && deck.has("empty") && deck.getBoolean("empty")) {
                    	// deck is a cram deck and has to be filled for the first time
                        deck.remove("empty");
                        rebuildCramDeck();
                    } else {
                        resetAndUpdateValuesFromDeck();
                    }
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
            } else if (requestCode == ADD_NOTE && resultCode != Activity.RESULT_CANCELED) {
                resetAndUpdateValuesFromDeck();
            } else if (requestCode == REQUEST_REVIEW) {
                Log.i(AnkiDroidApp.TAG, "Result code = " + resultCode);
                // TODO: Return to standard scheduler
                // TODO: handle big widget
                switch (resultCode) {
                    case Reviewer.RESULT_SESSION_COMPLETED:
                    default:
                        // do not reload counts, if activity is created anew because it has been before destroyed by android
                        resetAndUpdateValuesFromDeck();
                        break;
                    case Reviewer.RESULT_NO_MORE_CARDS:
                        mTextCongratsMessage.setText(AnkiDroidApp.getCol().getSched().finishedMsg(getActivity()));
                        setFragmentContentView(mCongratsView);
                        break;
                }
                mDontSaveOnStop = false;
            } else if (requestCode == BROWSE_CARDS && resultCode == Activity.RESULT_OK) {
                mDontSaveOnStop = false;
                resetAndUpdateValuesFromDeck();
            } else if (requestCode == STATISTICS && mCurrentContentView == CONTENT_CONGRATS) {
                resetAndUpdateValuesFromDeck();
                setFragmentContentView(mStudyOptionsView);
            }        	
        }
    }


    public SharedPreferences restorePreferences() {
        SharedPreferences preferences = AnkiDroidApp.getSharedPrefs(getActivity().getBaseContext());

        mSwipeEnabled = AnkiDroidApp.initiateGestures(getActivity(), preferences);
        return preferences;
    }

	DeckTask.TaskListener mUpdateValuesFromDeckListener = new DeckTask.TaskListener() {
        @Override
        public void onPostExecute(DeckTask.TaskData result) {
        	if (result != null) {
                Object[] obj = result.getObjArray();
                int newCards = (Integer) obj[0];
                int lrnCards = (Integer) obj[1];
                int revCards = (Integer) obj[2];
                int totalNew = (Integer) obj[3];
                int totalCards = (Integer) obj[4];
                mProgressMature = (Double) obj[5];
                mProgressAll = (Double) obj[6];
                int eta = (Integer) obj[7];
                double[][] serieslist = (double[][]) obj[8];

                updateStatisticBars();
                updateChart(serieslist);

//                JSONObject conf = mCol.getConf();
//                long timeLimit = 0;
//                try {
//                    timeLimit = (conf.getLong("timeLim") / 60);
//                } catch (JSONException e) {
//                    throw new RuntimeException(e);
//                }
//                mToggleLimitToggle.setChecked(timeLimit > 0 ? true : false);
//                mToggleLimitToggle.setText(String.valueOf(timeLimit));

//                Activity act = getActivity();
//                if (act != null) {
//                    SharedPreferences preferences = AnkiDroidApp.getSharedPrefs(act.getBaseContext());
//                    mPrefHideDueCount = preferences.getBoolean("hideDueCount", true);
//                }

                mTextTodayNew.setText(String.valueOf(newCards));
                mTextTodayLrn.setText(String.valueOf(lrnCards));
//                if (mPrefHideDueCount) {
//                    mTextTodayRev.setText("???");
//                } else {
                    mTextTodayRev.setText(String.valueOf(revCards));
//                }
                mTextNewTotal.setText(String.valueOf(totalNew));
                mTextTotal.setText(String.valueOf(totalCards));
                if (eta != -1) {
                    mTextETA.setText(Integer.toString(eta));
                } else {
                    mTextETA.setText("-");
                }

                if (mDeckCounts.getVisibility() == View.INVISIBLE) {
                    mDeckCounts.setVisibility(View.VISIBLE);
                    mDeckCounts.setAnimation(ViewAnimation.fade(ViewAnimation.FADE_IN, 500, 0));
                }

                if (mFragmented) {
                    ((DeckPicker) getActivity()).loadCounts();
                }
        	}

            // for rebuilding cram decks
            if (mProgressDialog != null && mProgressDialog.isShowing()) {
                try {
                    mProgressDialog.dismiss();
                } catch (Exception e) {
                    Log.e(AnkiDroidApp.TAG, "onPostExecute - Dialog dismiss Exception = " + e.getMessage());
                }
            }
        }


        @Override
        public void onPreExecute() {
        }


        @Override
        public void onProgressUpdate(DeckTask.TaskData... values) {
        }
    };

    DeckTask.TaskListener mLoadStatisticsHandler = new DeckTask.TaskListener() {

        @Override
        public void onPostExecute(DeckTask.TaskData result) {
            if (mProgressDialog.isShowing()) {
                try {
                    mProgressDialog.dismiss();
                } catch (Exception e) {
                    Log.e(AnkiDroidApp.TAG, "onPostExecute - Dialog dismiss Exception = " + e.getMessage());
                }
            }
            if (result.getBoolean()) {
                // if (mStatisticType == Statistics.TYPE_DECK_SUMMARY) {
                // Statistics.showDeckSummary(getActivity());
                // } else {
                Intent intent = new Intent(getActivity(), com.ichi2.charts.ChartBuilder.class);
                startActivityForResult(intent, STATISTICS);
                if (AnkiDroidApp.SDK_VERSION > 4) {
                    ActivityTransitionAnimation.slide(getActivity(), ActivityTransitionAnimation.DOWN);
                }
                // }
            } else {
                // TODO: db error handling
            }
        }


        @Override
        public void onPreExecute() {
            mProgressDialog = StyledProgressDialog.show(getActivity(), "",
                    getResources().getString(R.string.calculating_statistics), true);
        }


        @Override
        public void onProgressUpdate(DeckTask.TaskData... values) {
        }

    };

    class MyGestureDetector extends SimpleOnGestureListener {
        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            if (mSwipeEnabled) {
                try {
                    if (e1.getX() - e2.getX() > AnkiDroidApp.sSwipeMinDistance
                            && Math.abs(velocityX) > AnkiDroidApp.sSwipeThresholdVelocity
                            && Math.abs(e1.getY() - e2.getY()) < AnkiDroidApp.sSwipeMaxOffPath) {
                        // left
                        openReviewer();
                    } else if (e2.getX() - e1.getX() > AnkiDroidApp.sSwipeMinDistance
                            && Math.abs(velocityX) > AnkiDroidApp.sSwipeThresholdVelocity
                            && Math.abs(e1.getY() - e2.getY()) < AnkiDroidApp.sSwipeMaxOffPath) {
                        // right
                        closeStudyOptions();
                    } else if (e2.getY() - e1.getY() > AnkiDroidApp.sSwipeMinDistance
                            && Math.abs(velocityY) > AnkiDroidApp.sSwipeThresholdVelocity
                            && Math.abs(e1.getX() - e2.getX()) < AnkiDroidApp.sSwipeMaxOffPath) {
                        // down
                        DeckTask.launchDeckTask(DeckTask.TASK_TYPE_LOAD_STATISTICS, mLoadStatisticsHandler,
                                new DeckTask.TaskData(AnkiDroidApp.getCol(), Stats.TYPE_FORECAST, false));
                    } else if (e1.getY() - e2.getY() > AnkiDroidApp.sSwipeMinDistance
                            && Math.abs(velocityY) > AnkiDroidApp.sSwipeThresholdVelocity
                            && Math.abs(e1.getX() - e2.getX()) < AnkiDroidApp.sSwipeMaxOffPath) {
                        // up
                        addNote();
                    }

                } catch (Exception e) {
                    Log.e(AnkiDroidApp.TAG, "onFling Exception = " + e.getMessage());
                }
            }
            return false;
        }
    }

    public boolean onTouchEvent(MotionEvent event) {
    	return mSwipeEnabled && gestureDetector.onTouchEvent(event);
    }

    public boolean dbSaveNecessary() {
    	return !mDontSaveOnStop;
    }
}
