
package com.ichi2.anki;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.os.Vibrator;
import android.text.ClipboardManager;
import android.text.SpannableString;
import android.text.style.UnderlineSpan;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.webkit.JsResult;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.Chronometer;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.ichi2.utils.DiffEngine;
import com.ichi2.utils.RubyParser;
import com.tomgibara.android.veecheck.util.PrefSettings;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Reviewer extends Activity {

    /**
     * Tag for logging messages.
     */
    public static final String TAG = "AnkiDroid";

    /**
     * Result codes that are returned when this activity finishes.
     */
    public static final int RESULT_SESSION_COMPLETED = 1;
    public static final int RESULT_NO_MORE_CARDS = 2;

    /**
     * Available options performed by other activities.
     */
    public static final int EDIT_CURRENT_CARD = 0;

    /** Constant for class attribute signaling answer */
    static final String ANSWER_CLASS = "answer";

    /** Constant for class attribute signaling question */
    static final String QUESTION_CLASS = "question";

    /** Max size of the font for dynamic calculation of font size */
    private static final int DYNAMIC_FONT_MAX_SIZE = 14;

    /** Min size of the font for dynamic calculation of font size */
    private static final int DYNAMIC_FONT_MIN_SIZE = 3;
    private static final int DYNAMIC_FONT_FACTOR = 5;

    private static final int TOTAL_WIDTH_PADDING = 10;

    /**
     * Menus
     */
    private static final int MENU_WHITEBOARD = 0;
    private static final int MENU_CLEAR_WHITEBOARD = 1;
    private static final int MENU_EDIT = 2;
    private static final int MENU_SUSPEND = 3;
    private static final int MENU_SEARCH = 4;
    private static final int MENU_MARK = 5;

    /** Regex pattern used in removing tags from text before diff */
    private static final Pattern spanPattern = Pattern.compile("</?span[^>]*>");
    private static final Pattern brPattern = Pattern.compile("<br\\s?/?>");

    /** Hide Question In Answer choices */
    private static final int HQIA_DO_HIDE = 0;
    private static final int HQIA_DO_SHOW = 1;
    private static final int HQIA_CARD_MODEL = 2;

    private static Card sEditorCard; // To be assigned as the currentCard or a new card to be sent to and from editor

    /**
     * Variables used to track the time spent displaying a card.
     */
    private long mDisplayCardStartTime;
    private long mDisplayCardFinishTime;

    /**
     * Variables used to track the time spent updating and displaying a card.
     */
    private long mUpdateCardStartTime;
    private long mUpdateCardFinishTime;

    /** The percentage of the absolute font size specified in the deck. */
    private int mDisplayFontSize = 100;

    /**
     * Broadcast that informs us when the sd card is about to be unmounted
     */
    private BroadcastReceiver mUnmountReceiver = null;

    /**
     * Variables to hold preferences
     */
    private boolean mPrefTimer;
    private boolean mPrefWhiteboard;
    private boolean mPrefWriteAnswers;
    private boolean mPrefTextSelection;
    private boolean mPrefFullscreenReview;
    private boolean mPrefUseRubySupport; // Parse for ruby annotations
    private String mDeckFilename;
    private int mPrefHideQuestionInAnswer; // Hide the question when showing the answer

    @SuppressWarnings("unused")
    private boolean mUpdateNotifications; // TODO use Veecheck only if this is true

    private String mCardTemplate;

    /**
     * Variables to hold layout objects that we need to update or handle events for
     */
    private WebView mCard;
    private TextView mTextBarRed;
    private TextView mTextBarBlack;
    private TextView mTextBarBlue;
    private ToggleButton mFlipCard;
    private EditText mAnswerField;
    private Button mEase1;
    private Button mEase2;
    private Button mEase3;
    private Button mEase4;
    private Chronometer mCardTimer;
    private Whiteboard mWhiteboard;
    private ClipboardManager mClipboard;
    private ProgressDialog mProgressDialog;

    private Card mCurrentCard;
    private int mCurrentEase;
    private long mSessionTimeLimit;
    private int mSessionCurrReps;
    private float mScaleInPercent;
    private boolean mShowWhiteboard = false;

    // ----------------------------------------------------------------------------
    // LISTENERS
    // ----------------------------------------------------------------------------

    private Handler mHandler = new Handler() {

        @Override
        public void handleMessage(Message msg) {
            Sound.stopSounds();
            Sound.playSound((String) msg.obj);
        }
    };

    // Handler for the flip toggle button, between the question and the answer
    // of a card
    private CompoundButton.OnCheckedChangeListener mFlipCardHandler = new CompoundButton.OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean showAnswer) {
            Log.i(TAG, "Flip card changed:");
            Sound.stopSounds();

            if (showAnswer) {
                displayCardAnswer();
            } else {
                displayCardQuestion();
            }
        }
    };

    private View.OnClickListener mSelectEaseHandler = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            Sound.stopSounds();

            switch (view.getId()) {
                case R.id.ease1:
                    mCurrentEase = 1;
                    break;
                case R.id.ease2:
                    mCurrentEase = 2;
                    break;
                case R.id.ease3:
                    mCurrentEase = 3;
                    break;
                case R.id.ease4:
                    mCurrentEase = 4;
                    break;
                default:
                    mCurrentEase = 0;
                    return;
            }

            // Increment number reps counter
            mSessionCurrReps++;
            DeckTask.launchDeckTask(DeckTask.TASK_TYPE_ANSWER_CARD, mAnswerCardHandler, new DeckTask.TaskData(
                    mCurrentEase, AnkiDroidApp.deck(), mCurrentCard));
        }
    };

    private View.OnLongClickListener mLongClickHandler = new View.OnLongClickListener() {
        @Override
        public boolean onLongClick(View view) {
            Log.i(TAG, "onLongClick");
            Vibrator vibratorManager = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            vibratorManager.vibrate(50);
            selectAndCopyText();
            return true;
        }
    };

    private DeckTask.TaskListener mMarkCardHandler = new DeckTask.TaskListener() {
        @Override
        public void onPreExecute() {
            mProgressDialog = ProgressDialog.show(Reviewer.this, "", "Saving changes...", true);
        }


        @Override
        public void onProgressUpdate(DeckTask.TaskData... values) {
            mCurrentCard = values[0].getCard();
        }


        @Override
        public void onPostExecute(DeckTask.TaskData result) {
            mProgressDialog.dismiss();
        }
    };

    private DeckTask.TaskListener mUpdateCardHandler = new DeckTask.TaskListener() {
        @Override
        public void onPreExecute() {
            mProgressDialog = ProgressDialog.show(Reviewer.this, "", "Saving changes...", true);
        }


        @Override
        public void onProgressUpdate(DeckTask.TaskData... values) {
            mCurrentCard = values[0].getCard();
        }


        @Override
        public void onPostExecute(DeckTask.TaskData result) {

            // Set the correct value for the flip card button - That triggers the
            // listener which displays the question of the card
            mFlipCard.setChecked(false);

            if (mPrefWhiteboard) {
                mWhiteboard.clear();
            }

            if (mPrefTimer) {
                mCardTimer.setBase(SystemClock.elapsedRealtime());
                mCardTimer.start();
            }

            mProgressDialog.dismiss();
        }
    };

    private DeckTask.TaskListener mAnswerCardHandler = new DeckTask.TaskListener() {
        private boolean sessioncomplete;
        private boolean nomorecards;
        private long start;
        private long start2;


        @Override
        public void onPreExecute() {
            start = System.currentTimeMillis();
            start2 = start;
            Reviewer.this.setProgressBarIndeterminateVisibility(true);
            blockControls();
        }


        @Override
        public void onProgressUpdate(DeckTask.TaskData... values) {
            Resources res = getResources();
            sessioncomplete = false;
            nomorecards = false;

            // Check to see if session rep or time limit has been reached
            Deck deck = AnkiDroidApp.deck();
            long sessionRepLimit = deck.getSessionRepLimit();
            long sessionTime = deck.getSessionTimeLimit();
            Toast sessionMessage = null;

            if ((sessionRepLimit > 0) && (mSessionCurrReps >= sessionRepLimit)) {
                sessioncomplete = true;
                sessionMessage = Toast.makeText(Reviewer.this, res.getString(R.string.session_question_limit_reached),
                        Toast.LENGTH_SHORT);
            } else if ((sessionTime > 0) && (System.currentTimeMillis() >= mSessionTimeLimit)) {
                // session time limit reached, flag for halt once async task has completed.
                sessioncomplete = true;
                sessionMessage = Toast.makeText(Reviewer.this, res.getString(R.string.session_time_limit_reached),
                        Toast.LENGTH_SHORT);

            } else {
                // session limits not reached, show next card
                Card newCard = values[0].getCard();
                Log.w(TAG, "answerCard - get card (phase 1) in " + (System.currentTimeMillis() - start) + " ms.");
                start = System.currentTimeMillis();

                // If the card is null means that there are no more cards scheduled for review.
                if (newCard == null) {
                    nomorecards = true;
                    return;
                }

                Log.w(TAG, "onProgressUpdate - checked null " + (System.currentTimeMillis() - start) + " ms.");
                start = System.currentTimeMillis();
                // Start reviewing next card
                mCurrentCard = newCard;
                Reviewer.this.setProgressBarIndeterminateVisibility(false);
                Log.w(TAG, "onProgressUpdate - visibility " + (System.currentTimeMillis() - start) + " ms.");
                start = System.currentTimeMillis();
                // Reviewer.this.enableControls();
                Reviewer.this.unblockControls();
                Log.w(TAG, "onProgressUpdate - unblock ctrl " + (System.currentTimeMillis() - start) + " ms.");
                start = System.currentTimeMillis();
                Reviewer.this.reviewNextCard();
                Log.w(TAG, "onProgressUpdate - review next " + (System.currentTimeMillis() - start) + " ms.");
                start = System.currentTimeMillis();
            }

            Log.w(TAG, "answerCard - Checked times (phase 3) in " + (System.currentTimeMillis() - start) + " ms.");
            start = System.currentTimeMillis();

            // Show a message to user if a session limit has been reached.
            if (sessionMessage != null) {
                sessionMessage.show();
            }

            Log.w(TAG, "onProgressUpdate - New card received in " + (System.currentTimeMillis() - start2) + " ms.");
        }


        @Override
        public void onPostExecute(DeckTask.TaskData result) {
            // Check for no more cards before session complete. If they are both true,
            // no more cards will take precedence when returning to study options.
            if (nomorecards) {
                Reviewer.this.setResult(RESULT_NO_MORE_CARDS);
                Reviewer.this.finish();
            } else if (sessioncomplete) {
                Reviewer.this.setResult(RESULT_SESSION_COMPLETED);
                Reviewer.this.finish();
            }
        }
    };


    // ----------------------------------------------------------------------------
    // ANDROID METHODS
    // ----------------------------------------------------------------------------

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Log.i(TAG, "Reviewer - onCreate");

        // Make sure a deck is loaded before continuing.
        if (AnkiDroidApp.deck() == null) {
            setResult(StudyOptions.CONTENT_NO_EXTERNAL_STORAGE);
            finish();
        } else {
            restorePreferences();

            // Remove the status bar and title bar
            if (mPrefFullscreenReview) {
                getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                        WindowManager.LayoutParams.FLAG_FULLSCREEN);
                requestWindowFeature(Window.FEATURE_NO_TITLE);
            }

            requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);

            registerExternalStorageListener();

            initLayout(R.layout.flashcard_portrait);

            // Load the template for the card and set on it the available width for images
            try {
                mCardTemplate = Utils.convertStreamToString(getAssets().open("card_template.html"));
                mCardTemplate = mCardTemplate.replaceFirst("var availableWidth = \\d*;", "var availableWidth = "
                        + getAvailableWidthInCard() + ";");
            } catch (IOException e) {
                e.printStackTrace();
            }

            // Initialize session limits
            long timelimit = AnkiDroidApp.deck().getSessionTimeLimit() * 1000;
            Log.i(TAG, "SessionTimeLimit: " + timelimit + " ms.");
            mSessionTimeLimit = System.currentTimeMillis() + timelimit;
            mSessionCurrReps = 0;

            // Load the first card and start reviewing. Uses the answer card task to load a card, but since we send null
            // as the card to answer, no card will be answered.
            DeckTask.launchDeckTask(DeckTask.TASK_TYPE_ANSWER_CARD, mAnswerCardHandler, new DeckTask.TaskData(0,
                    AnkiDroidApp.deck(), null));
        }
    }


    // Saves deck each time Reviewer activity loses focus
    @Override
    public void onPause() {
        super.onPause();
        Log.i(TAG, "Reviewer - onPause()");
        // Save changes
        Deck deck = AnkiDroidApp.deck();
        deck.commitToDB();

        Sound.stopSounds();
    }


    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "Reviewer - onDestroy()");
        if (mUnmountReceiver != null) {
            unregisterReceiver(mUnmountReceiver);
        }
    }


    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        Log.i(TAG, "onConfigurationChanged");

        // Modify the card template to indicate the new available width and refresh card
        mCardTemplate = mCardTemplate.replaceFirst("var availableWidth = \\d*;", "var availableWidth = "
                + getAvailableWidthInCard() + ";");
        refreshCard();

        if (mPrefWhiteboard) {
            mWhiteboard.rotate();
        }
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuItem item;
        if (mPrefWhiteboard) {
            item = menu.add(Menu.NONE, MENU_WHITEBOARD, Menu.NONE, R.string.show_whiteboard);
            item.setIcon(R.drawable.ic_menu_compose);
            item = menu.add(Menu.NONE, MENU_CLEAR_WHITEBOARD, Menu.NONE, R.string.clear_whiteboard);
            item.setIcon(R.drawable.ic_menu_clear_playlist);
        }
        item = menu.add(Menu.NONE, MENU_EDIT, Menu.NONE, R.string.menu_edit_card);
        item.setIcon(android.R.drawable.ic_menu_edit);
        item = menu.add(Menu.NONE, MENU_SUSPEND, Menu.NONE, R.string.menu_suspend_card);
        item.setIcon(android.R.drawable.ic_menu_close_clear_cancel);
        if (mPrefTextSelection) {
            item = menu.add(Menu.NONE, MENU_SEARCH, Menu.NONE, R.string.menu_search);
            item.setIcon(R.drawable.ic_menu_search);
        }
        item = menu.add(Menu.NONE, MENU_MARK, Menu.NONE, R.string.menu_mark_card);
        return true;
    }


    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem item = menu.findItem(MENU_MARK);
        mCurrentCard.loadTags();
        if (mCurrentCard.hasTag(Deck.TAG_MARKED)) {
            item.setTitle(R.string.menu_marked);
            item.setIcon(R.drawable.star_big_on);
        } else {
            item.setTitle(R.string.menu_mark_card);
            item.setIcon(R.drawable.ic_menu_star);
        }
        if (mPrefTextSelection) {
            item = menu.findItem(MENU_SEARCH);
            Log.i(TAG, "Clipboard has text = " + mClipboard.hasText());
            Log.i(TAG,
                    "Is intent available = "
                            + Utils.isIntentAvailable(this, "sk.baka.aedict.action.ACTION_SEARCH_EDICT"));
            boolean lookupPossible = mClipboard.hasText()
                    && Utils.isIntentAvailable(this, "sk.baka.aedict.action.ACTION_SEARCH_EDICT");
            item.setEnabled(lookupPossible);
        }
        if (mPrefFullscreenReview) {
            // Temporarily remove top bar to avoid annoying screen flickering
            mTextBarRed.setVisibility(View.GONE);
            mTextBarBlack.setVisibility(View.GONE);
            mTextBarBlue.setVisibility(View.GONE);
            if (mPrefTimer) {
                mCardTimer.setVisibility(View.GONE);
            }

            getWindow().setFlags(0, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }
        return true;
    }


    @Override
    public void onOptionsMenuClosed(Menu menu) {
        if (mPrefFullscreenReview) {
            // Restore top bar
            mTextBarRed.setVisibility(View.VISIBLE);
            mTextBarBlack.setVisibility(View.VISIBLE);
            mTextBarBlue.setVisibility(View.VISIBLE);
            if (mPrefTimer) {
                mCardTimer.setVisibility(View.VISIBLE);
            }

            // Restore fullscreen preference
            getWindow()
                    .setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }
    }


    /** Handles item selections. */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_WHITEBOARD:
                // Toggle mShowWhiteboard value
                mShowWhiteboard = !mShowWhiteboard;
                if (mShowWhiteboard) {
                    // Show whiteboard
                    mWhiteboard.setVisibility(View.VISIBLE);
                    item.setTitle(R.string.hide_whiteboard);
                } else {
                    // Hide whiteboard
                    mWhiteboard.setVisibility(View.GONE);
                    item.setTitle(R.string.show_whiteboard);
                }
                return true;

            case MENU_CLEAR_WHITEBOARD:
                mWhiteboard.clear();
                return true;

            case MENU_EDIT:
                sEditorCard = mCurrentCard;
                Intent editCard = new Intent(Reviewer.this, CardEditor.class);
                startActivityForResult(editCard, EDIT_CURRENT_CARD);
                return true;

            case MENU_SUSPEND:
                mFlipCard.setChecked(true);
                DeckTask.launchDeckTask(DeckTask.TASK_TYPE_SUSPEND_CARD, mAnswerCardHandler, new DeckTask.TaskData(0,
                        AnkiDroidApp.deck(), mCurrentCard));
                return true;

            case MENU_SEARCH:
                if (mPrefTextSelection && mClipboard.hasText()
                        && Utils.isIntentAvailable(this, "sk.baka.aedict.action.ACTION_SEARCH_EDICT")) {
                    Intent aedictIntent = new Intent("sk.baka.aedict.action.ACTION_SEARCH_EDICT");
                    aedictIntent.putExtra("kanjis", mClipboard.getText());
                    startActivity(aedictIntent);
                    mClipboard.setText("");
                }
                return true;

            case MENU_MARK:
                DeckTask.launchDeckTask(DeckTask.TASK_TYPE_MARK_CARD, mMarkCardHandler, new DeckTask.TaskData(0,
                        AnkiDroidApp.deck(), mCurrentCard));
                return true;
        }
        return false;
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == EDIT_CURRENT_CARD) {
            if (resultCode == RESULT_OK) {
                Log.i(TAG, "Saving card...");
                DeckTask.launchDeckTask(DeckTask.TASK_TYPE_UPDATE_FACT, mUpdateCardHandler, new DeckTask.TaskData(0,
                        AnkiDroidApp.deck(), mCurrentCard));
                // TODO: code to save the changes made to the current card.
                mFlipCard.setChecked(true);
                displayCardQuestion();
            } else if (resultCode == StudyOptions.CONTENT_NO_EXTERNAL_STORAGE) {
                finishNoStorageAvailable();
            }
        }
    }


    // ----------------------------------------------------------------------------
    // CUSTOM METHODS
    // ----------------------------------------------------------------------------

    /**
     * Registers an intent to listen for ACTION_MEDIA_EJECT notifications. The intent will call
     * closeExternalStorageFiles() if the external media is going to be ejected, so applications can clean up any files
     * they have open.
     */
    public void registerExternalStorageListener() {
        if (mUnmountReceiver == null) {
            mUnmountReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    String action = intent.getAction();
                    if (action.equals(Intent.ACTION_MEDIA_EJECT)) {
                        Log.i(TAG, "mUnmountReceiver - Action = Media Eject");
                        finishNoStorageAvailable();
                    }
                }
            };
            IntentFilter iFilter = new IntentFilter();
            iFilter.addAction(Intent.ACTION_MEDIA_EJECT);
            iFilter.addDataScheme("file");
            registerReceiver(mUnmountReceiver, iFilter);
        }
    }


    private void finishNoStorageAvailable() {
        setResult(StudyOptions.CONTENT_NO_EXTERNAL_STORAGE);
        finish();
    }


    // Set the content view to the one provided and initialize accessors.
    private void initLayout(Integer layout) {
        setContentView(layout);

        mCard = (WebView) findViewById(R.id.flashcard);
        mCard.setScrollBarStyle(WebView.SCROLLBARS_OUTSIDE_OVERLAY);
        mCard.getSettings().setBuiltInZoomControls(true);
        mCard.getSettings().setJavaScriptEnabled(true);
        mCard.setWebViewClient(new AnkiDroidWebViewClient());
        mCard.setWebChromeClient(new AnkiDroidWebChromeClient());
        mCard.addJavascriptInterface(new JavaScriptInterface(), "interface");
        if (Integer.parseInt(android.os.Build.VERSION.SDK) > 7) {
            mCard.setFocusableInTouchMode(false);
        }
        Log.i(TAG,
                "Focusable = " + mCard.isFocusable() + ", Focusable in touch mode = " + mCard.isFocusableInTouchMode());
        if (mPrefTextSelection) {
            mCard.setOnLongClickListener(mLongClickHandler);
            mClipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        }

        mScaleInPercent = mCard.getScale();

        mEase1 = (Button) findViewById(R.id.ease1);
        mEase1.setOnClickListener(mSelectEaseHandler);

        mEase2 = (Button) findViewById(R.id.ease2);
        mEase2.setOnClickListener(mSelectEaseHandler);

        mEase3 = (Button) findViewById(R.id.ease3);
        mEase3.setOnClickListener(mSelectEaseHandler);

        mEase4 = (Button) findViewById(R.id.ease4);
        mEase4.setOnClickListener(mSelectEaseHandler);

        mFlipCard = (ToggleButton) findViewById(R.id.flip_card);
        mFlipCard.setChecked(true); // Fix for mFlipCardHandler not being called on first deck load.
        mFlipCard.setOnCheckedChangeListener(mFlipCardHandler);

        mTextBarRed = (TextView) findViewById(R.id.red_number);
        mTextBarBlack = (TextView) findViewById(R.id.black_number);
        mTextBarBlue = (TextView) findViewById(R.id.blue_number);

        if (mPrefTimer) {
            mCardTimer = (Chronometer) findViewById(R.id.card_time);
        }
        if (mPrefWhiteboard) {
            mWhiteboard = (Whiteboard) findViewById(R.id.whiteboard);
        }
        if (mPrefWriteAnswers) {
            mAnswerField = (EditText) findViewById(R.id.answer_field);
        }

        hideEaseButtons();
        showControls();
    }


    private void showEaseButtons() {
        Resources res = getResources();

        // Set correct label for each button
        if (learningButtons()) {
            mEase1.setText(res.getString(R.string.ease1_learning));
            mEase2.setText(res.getString(R.string.ease2_learning));
            mEase3.setText(res.getString(R.string.ease3_learning));
            mEase4.setText(res.getString(R.string.ease4_learning));
        } else {
            mEase1.setText(res.getString(R.string.ease1_successive));
            mEase2.setText(res.getString(R.string.ease2_successive));
            mEase3.setText(res.getString(R.string.ease3_successive));
            mEase4.setText(res.getString(R.string.ease4_successive));
        }

        // Show buttons
        mEase1.setVisibility(View.VISIBLE);
        mEase2.setVisibility(View.VISIBLE);
        mEase3.setVisibility(View.VISIBLE);
        mEase4.setVisibility(View.VISIBLE);

        // Focus default button
        if (learningButtons()) {
            mEase2.requestFocus();
        } else {
            mEase3.requestFocus();
        }
    }


    private void hideEaseButtons() {
        // GONE -> It allows to write until the very bottom
        // INVISIBLE -> The transition between the question and the answer seems more smooth
        mEase1.setVisibility(View.GONE);
        mEase2.setVisibility(View.GONE);
        mEase3.setVisibility(View.GONE);
        mEase4.setVisibility(View.GONE);
    }


    private void showControls() {
        mCard.setVisibility(View.VISIBLE);
        mTextBarRed.setVisibility(View.VISIBLE);
        mTextBarBlack.setVisibility(View.VISIBLE);
        mTextBarBlue.setVisibility(View.VISIBLE);
        mFlipCard.setVisibility(View.VISIBLE);

        if (mPrefTimer) {
            mCardTimer.setVisibility(View.VISIBLE);
        }

        if (mPrefWhiteboard && mShowWhiteboard) {
            mWhiteboard.setVisibility(View.VISIBLE);
        }

        if (mPrefWriteAnswers) {
            mAnswerField.setVisibility(View.VISIBLE);
        }
    }


    private boolean learningButtons() {
        return mCurrentCard.successive == 0;
    }


    private SharedPreferences restorePreferences() {
        SharedPreferences preferences = PrefSettings.getSharedPrefs(getBaseContext());
        mPrefTimer = preferences.getBoolean("timer", true);
        mPrefWhiteboard = preferences.getBoolean("whiteboard", true);
        mPrefWriteAnswers = preferences.getBoolean("writeAnswers", false);
        mPrefTextSelection = preferences.getBoolean("textSelection", false);
        mDeckFilename = preferences.getString("deckFilename", "");
        mPrefUseRubySupport = preferences.getBoolean("useRubySupport", false);
        mPrefFullscreenReview = preferences.getBoolean("fullscreenReview", true);
        mDisplayFontSize = Integer.parseInt(preferences.getString("displayFontSize", "100"));
        mPrefHideQuestionInAnswer = Integer.parseInt(preferences.getString("hideQuestionInAnswer",
                Integer.toString(HQIA_DO_SHOW)));

        // Redraw screen with new preferences
        refreshCard();

        return preferences;
    }


    private void refreshCard() {
        if (mFlipCard != null) {
            if (mFlipCard.isChecked()) {
                displayCardAnswer();
            } else {
                displayCardQuestion();
            }
        }
    }


    private void reviewNextCard() {
        long start = System.currentTimeMillis();
        updateCounts();
        Log.w(TAG, "reviewNextCard - update counts in " + (System.currentTimeMillis() - start) + " ms.");
        start = System.currentTimeMillis();
        mFlipCard.setChecked(false);
        Log.w(TAG, "reviewNextCard - check flipcard in " + (System.currentTimeMillis() - start) + " ms.");
        start = System.currentTimeMillis();

        // Clean answer field
        if (mPrefWriteAnswers) {
            mAnswerField.setText("");
        }
        Log.w(TAG, "reviewNextCard - clear answer field in " + (System.currentTimeMillis() - start) + " ms.");
        start = System.currentTimeMillis();

        if (mPrefWhiteboard) {
            mWhiteboard.clear();
        }
        Log.w(TAG, "reviewNextCard - clear whiteboard in " + (System.currentTimeMillis() - start) + " ms.");
        start = System.currentTimeMillis();

        if (mPrefTimer) {
            mCardTimer.setBase(SystemClock.elapsedRealtime());
            mCardTimer.start();
        }
        Log.w(TAG, "reviewNextCard - reset timer in " + (System.currentTimeMillis() - start) + " ms.");
        start = System.currentTimeMillis();
    }


    private void updateCounts() {
        Deck deck = AnkiDroidApp.deck();
        String unformattedTitle = getResources().getString(R.string.studyoptions_window_title);
        setTitle(String.format(unformattedTitle, deck.deckName, deck.revCount + deck.failedSoonCount, deck.cardCount));

        SpannableString failedSoonCount = new SpannableString(String.valueOf(deck.failedSoonCount));
        SpannableString revCount = new SpannableString(String.valueOf(deck.revCount));
        SpannableString newCount = new SpannableString(String.valueOf(deck.newCountToday));

        int isDue = mCurrentCard.isDue;
        int type = mCurrentCard.type;

        if ((isDue == 1) && (type == Deck.CARD_TYPE_NEW)) {
            newCount.setSpan(new UnderlineSpan(), 0, newCount.length(), 0);
        }
        if ((isDue == 1) && (type == Deck.CARD_TYPE_REV)) {
            revCount.setSpan(new UnderlineSpan(), 0, revCount.length(), 0);
        }
        if ((isDue == 1) && (type == Deck.CARD_TYPE_FAILED)) {
            failedSoonCount.setSpan(new UnderlineSpan(), 0, failedSoonCount.length(), 0);
        }

        mTextBarRed.setText(failedSoonCount);
        mTextBarBlack.setText(revCount);
        mTextBarBlue.setText(newCount);
    }


    private void displayCardQuestion() {
        hideEaseButtons();

        // If the user wants to write the answer
        if (mPrefWriteAnswers) {
            mAnswerField.setVisibility(View.VISIBLE);

            // Show soft keyboard
            InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            inputMethodManager.showSoftInput(mAnswerField, InputMethodManager.SHOW_FORCED);
        }

        mFlipCard.setVisibility(View.VISIBLE);
        mFlipCard.requestFocus();

        String displayString = enrichWithQASpan(mCurrentCard.question, false);
        // Show an horizontal line as separation when question is shown in answer
        // XXX Martin: is it really necessary on the question side?
        if (questionIsDisplayed()) {
            displayString = displayString + "<hr/>";
        }

        updateCard(displayString);
    }


    private void displayCardAnswer() {
        Log.i(TAG, "displayCardAnswer");

        if (mPrefTimer) {
            mCardTimer.stop();
        }

        String displayString = "";

        // If the user wrote an answer
        if (mPrefWriteAnswers) {
            mAnswerField.setVisibility(View.GONE);
            if (mCurrentCard != null) {
                // Obtain the user answer and the correct answer
                String userAnswer = mAnswerField.getText().toString();
                Matcher spanMatcher = spanPattern.matcher(mCurrentCard.answer);
                String correctAnswer = spanMatcher.replaceAll("");
                Matcher brMatcher = brPattern.matcher(correctAnswer);
                correctAnswer = brMatcher.replaceAll("\n");
                Log.i(TAG, "correct answer = " + correctAnswer);

                // Obtain the diff and send it to updateCard
                DiffEngine diff = new DiffEngine();

                displayString = enrichWithQASpan(diff.diff_prettyHtml(diff.diff_main(userAnswer, correctAnswer))
                        + "<br/>" + mCurrentCard.answer, true);
            }

            // Hide soft keyboard
            InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            inputMethodManager.hideSoftInputFromWindow(mAnswerField.getWindowToken(), 0);
        } else {
            displayString = enrichWithQASpan(mCurrentCard.answer, true);
        }

        // Depending on preferences do or do not show the question
        if (questionIsDisplayed()) {
            StringBuffer sb = new StringBuffer();
            sb.append(enrichWithQASpan(mCurrentCard.question, false));
            sb.append("<hr/>");
            sb.append(displayString);
            displayString = sb.toString();
            mFlipCard.setVisibility(View.GONE);
        }

        showEaseButtons();
        updateCard(displayString);
    }


    private void updateCard(String content) {
        Log.i(TAG, "updateCard");
        mUpdateCardStartTime = System.currentTimeMillis();

        // Log.i(TAG, "Initial content card = \n" + content);
        // content = Image.parseImages(deckFilename, content);
        // Log.i(TAG, "content after parsing images = \n" + content);
        content = Sound.parseSounds(mDeckFilename, content);

        // In order to display the bold style correctly, we have to change
        // font-weight to 700
        content = content.replace("font-weight:600;", "font-weight:700;");

        // If ruby annotation support is activated, then parse and add markup
        if (mPrefUseRubySupport) {
            content = RubyParser.ankiRubyToMarkup(content);
        }

        // Add CSS for font color and font size
        if (mCurrentCard != null) {
            Deck currentDeck = AnkiDroidApp.deck();
            Model myModel = Model.getModel(currentDeck, mCurrentCard.cardModelId, false);
            content = myModel.getCSSForFontColorSize(mCurrentCard.cardModelId, mDisplayFontSize) + content;
        } else {
            mCard.getSettings().setDefaultFontSize(calculateDynamicFontSize(content));
        }

        // Log.i(TAG, "content card = \n" + content);
        String card = mCardTemplate.replace("::content::", content);
        Log.i(TAG, "card html = \n" + card);
        mCard.loadDataWithBaseURL("file://" + mDeckFilename.replace(".anki", ".media/"), card, "text/html", "utf-8",
                null);

        Sound.playSounds();
    }


    private boolean questionIsDisplayed() {
        switch (mPrefHideQuestionInAnswer) {
            case HQIA_DO_HIDE:
                return false;

            case HQIA_DO_SHOW:
                return true;

            case HQIA_CARD_MODEL:
                return (Model.getModel(AnkiDroidApp.deck(), mCurrentCard.cardModelId, false).getCardModel(
                        mCurrentCard.cardModelId).questionInAnswer == 0);

            default:
                return true;
        }
    }


    /**
     * Adds a span html tag around the contents to have an indication, where answer/question is displayed
     *
     * @param content
     * @param isAnswer if true then the class attribute is set to "answer", "question" otherwise.
     * @return
     */
    private static String enrichWithQASpan(String content, boolean isAnswer) {
        StringBuffer sb = new StringBuffer();
        sb.append("<p class=\"");
        if (isAnswer) {
            sb.append(ANSWER_CLASS);
        } else {
            sb.append(QUESTION_CLASS);
        }
        sb.append("\">");
        sb.append(content);
        sb.append("</p>");
        return sb.toString();
    }


    /**
     * Calculates a dynamic font size depending on the length of the contents taking into account that the input string
     * contains html-tags, which will not be displayed and therefore should not be taken into account.
     *
     * @param htmlContents
     * @return font size respecting MIN_DYNAMIC_FONT_SIZE and MAX_DYNAMIC_FONT_SIZE
     */
    private static int calculateDynamicFontSize(String htmlContent) {
        // Replace each <br> with 15 spaces, each <hr> with 30 spaces, then
        // remove all html tags and spaces
        String realContent = htmlContent.replaceAll("\\<br.*?\\>", " ");
        realContent = realContent.replaceAll("\\<hr.*?\\>", " ");
        realContent = realContent.replaceAll("\\<.*?\\>", "");
        realContent = realContent.replaceAll("&nbsp;", " ");
        return Math.max(DYNAMIC_FONT_MIN_SIZE,
                DYNAMIC_FONT_MAX_SIZE - (int) (realContent.length() / DYNAMIC_FONT_FACTOR));
    }


    private void unblockControls() {
        mCard.setEnabled(true);
        mFlipCard.setEnabled(true);

        switch (mCurrentEase) {
            case 1:
                mEase1.setClickable(true);
                mEase2.setEnabled(true);
                mEase3.setEnabled(true);
                mEase4.setEnabled(true);
                break;

            case 2:
                mEase1.setEnabled(true);
                mEase2.setClickable(true);
                mEase3.setEnabled(true);
                mEase4.setEnabled(true);
                break;

            case 3:
                mEase1.setEnabled(true);
                mEase2.setEnabled(true);
                mEase3.setClickable(true);
                mEase4.setEnabled(true);
                break;

            case 4:
                mEase1.setEnabled(true);
                mEase2.setEnabled(true);
                mEase3.setEnabled(true);
                mEase4.setClickable(true);
                break;

            default:
                mEase1.setEnabled(true);
                mEase2.setEnabled(true);
                mEase3.setEnabled(true);
                mEase4.setEnabled(true);
                break;
        }

        if (mPrefTimer) {
            mCardTimer.setEnabled(true);
        }

        if (mPrefWhiteboard) {
            mWhiteboard.setEnabled(true);
        }

        if (mPrefWriteAnswers) {
            mAnswerField.setEnabled(true);
        }
    }


    private void blockControls() {
        mCard.setEnabled(false);
        mFlipCard.setEnabled(false);

        switch (mCurrentEase) {
            case 1:
                mEase1.setClickable(false);
                mEase2.setEnabled(false);
                mEase3.setEnabled(false);
                mEase4.setEnabled(false);
                break;

            case 2:
                mEase1.setEnabled(false);
                mEase2.setClickable(false);
                mEase3.setEnabled(false);
                mEase4.setEnabled(false);
                break;

            case 3:
                mEase1.setEnabled(false);
                mEase2.setEnabled(false);
                mEase3.setClickable(false);
                mEase4.setEnabled(false);
                break;

            case 4:
                mEase1.setEnabled(false);
                mEase2.setEnabled(false);
                mEase3.setEnabled(false);
                mEase4.setClickable(false);
                break;

            default:
                mEase1.setEnabled(false);
                mEase2.setEnabled(false);
                mEase3.setEnabled(false);
                mEase4.setEnabled(false);
                break;
        }

        if (mPrefTimer) {
            mCardTimer.setEnabled(false);
        }

        if (mPrefWhiteboard) {
            mWhiteboard.setEnabled(false);
        }

        if (mPrefWriteAnswers) {
            mAnswerField.setEnabled(false);
        }
    }


    public static Card getEditorCard() {
        return sEditorCard;
    }


    private int getAvailableWidthInCard() {
        // The width available is equals to
        // the screen's width divided by the default scale factor used by the WebView, because this scale factor will be
        // applied later
        // and minus the padding
        int availableWidth = (int) (AnkiDroidApp.getDisplayWidth() / mScaleInPercent) - TOTAL_WIDTH_PADDING;
        Log.i(TAG, "availableWidth = " + availableWidth);
        return availableWidth;
    }


    /**
     * Select Text in the webview and automatically sends the selected text to the clipboard From
     * http://cosmez.blogspot.com/2010/04/webview-emulateshiftheld-on-android.html
     */
    private void selectAndCopyText() {
        try {
            KeyEvent shiftPressEvent = new KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SHIFT_LEFT, 0, 0);
            shiftPressEvent.dispatch(mCard);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    // ----------------------------------------------------------------------------
    // INNER CLASSES
    // ----------------------------------------------------------------------------

    public final class AnkiDroidWebViewClient extends WebViewClient {

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            mDisplayCardStartTime = System.currentTimeMillis();
        }


        @Override
        public void onPageFinished(WebView view, String url) {
            mDisplayCardFinishTime = System.currentTimeMillis();
            mUpdateCardFinishTime = System.currentTimeMillis();

            Log.i(TAG, "Time spent displaying card = " + (mDisplayCardFinishTime - mDisplayCardStartTime)
                    + " milliseconds");
            Log.i(TAG, "Time spent updating card = " + (mUpdateCardFinishTime - mUpdateCardStartTime) + " milliseconds");
        }
    }

    /**
     * Provides a hook for calling "alert" from javascript. Useful for debugging your javascript.
     */
    public final class AnkiDroidWebChromeClient extends WebChromeClient {
        @Override
        public boolean onJsAlert(WebView view, String url, String message, JsResult result) {
            Log.i(TAG, message);
            result.confirm();
            return true;
        }
    }

    public final class JavaScriptInterface {

        JavaScriptInterface() {
        }


        /**
         * This is not called on the UI thread. Send a message that will be handled on the UI thread.
         */
        public void playSound(String soundPath) {
            Message msg = Message.obtain();
            msg.obj = soundPath;
            mHandler.sendMessage(msg);
        }
    }
}
