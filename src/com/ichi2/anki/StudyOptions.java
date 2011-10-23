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
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.GestureDetector;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.view.inputmethod.InputMethodManager;
import android.webkit.WebView;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.ToggleButton;

import com.ichi2.anim.ActivityTransitionAnimation;
import com.ichi2.anim.ViewAnimation;
import com.ichi2.anki.DeckTask.TaskData;
import com.ichi2.async.Connection;
import com.ichi2.async.Connection.Payload;
import com.ichi2.compat.Compat;
import com.ichi2.compat.CompatV11;
import com.ichi2.compat.CompatV3;
import com.ichi2.themes.StyledDialog;
import com.ichi2.themes.Themes;
import com.tomgibara.android.veecheck.util.PrefSettings;
import com.zeemote.zc.Controller;
import com.zeemote.zc.event.ButtonEvent;
import com.zeemote.zc.event.IButtonListener;
import com.zeemote.zc.ui.android.ControllerAndroidUi;
import com.zeemote.zc.util.JoystickToButtonAdapter;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

public class StudyOptions extends Activity implements IButtonListener {
	
    /**
* Default database.
*/
    public static final String OPT_DB = "com.ichi2.anki.deckFilename";

    /**
* Filename of the sample deck to load
*/
    private static final String SAMPLE_DECK_NAME = "tutorial.anki";

    /**
* Menus
*/
    private static final int MENU_OPEN = 1;
    private static final int MENU_SYNC = 3;
    private static final int MENU_PREFERENCES = 5;
    private static final int MENU_ADD_FACT = 6;
    private static final int MENU_MORE_OPTIONS = 7;
    private static final int MENU_ROTATE = 8;
    private static final int MENU_ZEEMOTE = 9;

    /**
* Available options performed by other activities
*/
    private static final int PICK_DECK_REQUEST = 0;
    private static final int PREFERENCES_UPDATE = 1;
    private static final int REQUEST_REVIEW = 2;
    private static final int DOWNLOAD_PERSONAL_DECK = 3;
    private static final int DOWNLOAD_SHARED_DECK = 4;
    private static final int REPORT_ERROR = 5;
    private static final int ADD_FACT = 6;
    private static final int BROWSE_CARDS = 7;
    private static final int STATISTICS = 8;
    private static final int LOG_IN = 9;
    
    public static final int RESULT_RESTART = 100;
    public static final int RESULT_CLOSE = 101;
    public static final int RESULT_RELOAD_DECK = 102;
    public static final int RESULT_DONT_RELOAD_DECK = 103;

    /**
* Constants for selecting which content view to display
*/
    private static final int CONTENT_NO_DECK = 0;
    private static final int CONTENT_STUDY_OPTIONS = 1;
    private static final int CONTENT_CONGRATS = 2;
    private static final int CONTENT_DECK_NOT_LOADED = 3;
    private static final int CONTENT_SESSION_COMPLETE = 4;
    public static final int CONTENT_NO_EXTERNAL_STORAGE = 5;


    /** Startup Mode choices */
    public static final int SUM_STUDY_OPTIONS = 0;
    public static final int SUM_DECKPICKER = 1;
    public static final int SUM_DECKPICKER_ON_FIRST_START = 2;


    public static final String EXTRA_DECK = "deck";

    private static final int DIALOG_NO_CONNECTION = 0;
    private static final int DIALOG_USER_NOT_LOGGED_IN = 1;
    private static final int DIALOG_CONNECTION_ERROR = 2;
    private static final int DIALOG_SYNC_LOG = 3;
    private static final int DIALOG_NO_SPACE_LEFT = 4;
    private static final int DIALOG_BACKUP_ERROR = 5;
    private static final int DIALOG_DECK_NOT_LOADED = 6;
    private static final int DIALOG_SYNC_CONFLICT_RESOLUTION = 7;
    private static final int DIALOG_WELCOME = 8;
    private static final int DIALOG_STATISTIC_TYPE = 9;
    private static final int DIALOG_STATISTIC_PERIOD = 10;
    private static final int DIALOG_SWAP_QA = 11;
    private static final int DIALOG_MORE = 12;
    private static final int DIALOG_TAGS = 13;
    private static final int DIALOG_LIMIT_SESSION = 14;
    private static final int DIALOG_DOWNLOAD_SELECTOR = 15;
    private static final int DIALOG_CRAM = 16;
    private static final int DIALOG_BACKUP_NO_SPACE_LEFT = 17;
    private static final int DIALOG_ANSWERING_ERROR = 18;

    private String mCurrentDialogMessage;

    private StyledDialog mNewVersionAlert;
    private StyledDialog mWelcomeAlert;
    
    /** Zeemote messages */
    private static final int MSG_ZEEMOTE_BUTTON_A = 0x110;
    private static final int MSG_ZEEMOTE_BUTTON_B = MSG_ZEEMOTE_BUTTON_A+1;
    private static final int MSG_ZEEMOTE_BUTTON_C = MSG_ZEEMOTE_BUTTON_A+2;
    private static final int MSG_ZEEMOTE_BUTTON_D = MSG_ZEEMOTE_BUTTON_A+3;
    private static final int MSG_ZEEMOTE_STICK_UP = MSG_ZEEMOTE_BUTTON_A+4;
    private static final int MSG_ZEEMOTE_STICK_DOWN = MSG_ZEEMOTE_BUTTON_A+5;
    private static final int MSG_ZEEMOTE_STICK_LEFT = MSG_ZEEMOTE_BUTTON_A+6;
    private static final int MSG_ZEEMOTE_STICK_RIGHT = MSG_ZEEMOTE_BUTTON_A+7;
    

    /**
* Download Manager Service stub
*/
    // private IDownloadManagerService mService = null;

    /**
* Broadcast that informs us when the sd card is about to be unmounted
*/
    private BroadcastReceiver mUnmountReceiver = null;

    private boolean mSdCardAvailable = AnkiDroidApp.isSdCardMounted();

    /**
* Preferences
*/
    private String mPrefDeckPath;
    private boolean mPrefStudyOptions;
    // private boolean deckSelected;
    private boolean mInDeckPicker;
    private boolean mInReviewer;
    private String mDeckFilename;
    private int mStartupMode;
    private boolean mSwipeEnabled;

    private int mCurrentContentView;

    public static int mNewDayStartsAt = 4;
    private long mLastTimeOpened;
    boolean mInvertedColors = false;
    boolean mSwap = false;
    String mLocale;
    private boolean mZeemoteEnabled;

    /**
* Alerts to inform the user about different situations
*/
    private ProgressDialog mProgressDialog;

	/*
	* Limit session dialog
	*/
    private EditText mEditSessionTime;
    private EditText mEditSessionQuestions;
    private CheckBox mSessionLimitCheckBox;
    private CheckBox mLimitTagsCheckBox;
    private CheckBox mLimitTagNewActiveCheckBox;
    private CheckBox mLimitTagNewInactiveCheckBox;
    private CheckBox mLimitTagRevActiveCheckBox;
    private CheckBox mLimitTagRevInactiveCheckBox;
    private TextView mLimitSessionTv1;
    private TextView mLimitSessionTv2;
    private TextView mLimitTagTv1;
    private TextView mLimitTagTv2;
    private TextView mLimitTagTv3;
    private TextView mLimitTagTv4;
    private TextView mLimitTagTv5;
    private TextView mLimitTagTv6;
    private String mLimitNewActive;
    private String mLimitNewInactive;
    private String mLimitRevActive;
    private String mLimitRevInactive;
    private HashSet<String> mSelectedTags;
    private String[] allTags;
    private int mSelectedLimitTagText;
    private static final int LIMIT_NEW_ACTIVE = 0;
    private static final int LIMIT_NEW_INACTIVE = 1;
    private static final int LIMIT_REV_ACTIVE = 2;
    private static final int LIMIT_REV_INACTIVE = 3;

    /*
* Cram related
*/
//    private StyledDialog mCramTagsDialog;
    private String allCramTags[];
    private HashSet<String> activeCramTags;
    private String cramOrder;
    private static final String[] cramOrderList = {"type, modified", "created", "random()"};

    /**
* UI elements for "Study Options" view
*/
    private View mStudyOptionsView;
    private View mStudyOptionsMain;
    private Button mButtonStart;
    private ToggleButton mToggleCram;
    private ToggleButton mToggleLimit;
    private TextView mTextDeckName;
    private LinearLayout mStatisticsField;
    private TextView mTextReviewsDue;
    private TextView mTextNewToday;
    private TextView mTextETA;
    private TextView mTextNewTotal;
    private TextView mHelp;
    private CheckBox mNightMode;
    private CheckBox mSwapQA;
    private Button mCardBrowser;
    private Button mStatisticsButton;

    /**
* UI elements for "More Options" dialog
*/
    private Spinner mSpinnerNewCardOrder;
    private Spinner mSpinnerNewCardSchedule;
    private Spinner mSpinnerRevCardOrder;
    private Spinner mSpinnerFailCardOption;
    private EditText mEditNewPerDay;
    private EditText mEditMaxFailCard;

    private CheckBox mCheckBoxPerDay;
    private CheckBox mCheckBoxSuspendLeeches;

    /**
* UI elements for "No Deck" view
*/
    private View mNoDeckView;
    private TextView mTextNoDeckTitle;
    private TextView mTextNoDeckMessage;

    /**
* UI elements for "Congrats" view
*/
    private View mCongratsView;
    private TextView mTextCongratsMessage;
    private Button mButtonCongratsLearnMore;
    private Button mButtonCongratsReviewEarly;
    private Button mButtonCongratsSyncDeck;
    private Button mButtonCongratsOpenOtherDeck;
    private Button mButtonCongratsFinish;

    /**
* UI elements for "No External Storage Available" view
*/
    private View mNoExternalStorageView;

    /**
* UI elements for "Cram Tags" view
*/
//    private ListView mCramTagsListView;
//    private Spinner mSpinnerCramOrder;

    /**
    * Swipe Detection
    */
	private GestureDetector gestureDetector;
	View.OnTouchListener gestureListener;

	private static final int SWIPE_MIN_DISTANCE_DIP = 65;
    private static final int SWIPE_MAX_OFF_PATH_DIP = 120;
    private static final int SWIPE_THRESHOLD_VELOCITY_DIP = 120;

	public static int sSwipeMinDistance;
	public static int sSwipeMaxOffPath;
	public static int sSwipeThresholdVelocity;

    /**
	* Backup
	*/
	File[] mBackups;
	public static final int MIN_FREE_SPACE = 10;

	/**
	* Statistics
	*/
	public static int mStatisticType;
    private int mStatisticBarsMax = 0;
    private int mStatisticBarsHeight;
    private View mBarsMax;
    private View mDailyBar;
    private View mMatureBar;
    private View mGlobalLimitFrame;
    private View mGlobalLimitBar;
    private View mGlobalMatLimitBar;
    private View mGlobalBar;
    private View mGlobalMatBar;
    private double mProgressTodayYes;
    private double mProgressMatureYes;
    private double mProgressMatureLimit;
    private double mProgressAllLimit;
    private double mProgressMature;
    private double mProgressAll;

    private boolean mIsClosing = false;

    /** Used to perform operation in a platform specific way. */
    private Compat mCompat;
    
    /**
 	 * Zeemote controller
 	 */
	protected JoystickToButtonAdapter adapter;
 	ControllerAndroidUi controllerUi;
 	
	private boolean mShowRepairDialog = false;

    /**
* Callbacks for UI events
*/
    private View.OnClickListener mButtonClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            switch (v.getId()) {
                case R.id.studyoptions_start:
                    openReviewer();
                    return;
                case R.id.studyoptions_cram:
                    if (mToggleCram.isChecked()) {
                        mToggleCram.setChecked(!mToggleCram.isChecked());
                        activeCramTags.clear();
                        cramOrder = cramOrderList[0];
                        showDialog(DIALOG_CRAM);
                    } else {
                        onCramStop();
                        resetAndUpdateValuesFromDeck();
                    }
                    return;
                case R.id.studyoptions_limit:
                    mToggleLimit.setChecked(!mToggleLimit.isChecked());
        	       	showDialog(DIALOG_LIMIT_SESSION);
                    return;
                case R.id.studyoptions_congrats_learnmore:
                	startLearnMore();
                	return;
                case R.id.studyoptions_congrats_reviewearly:
                	startEarlyReview();
                    return;
                case R.id.studyoptions_congrats_syncdeck:
                	syncDeck(null);
                    return;
                case R.id.studyoptions_congrats_open_other_deck:
                    openDeckPicker();
                    return;
                case R.id.studyoptions_congrats_finish:
                	finishCongrats();
                    return;
                case R.id.studyoptions_load_sample_deck:
                    loadSampleDeck();
                    return;
                case R.id.studyoptions_load_other_deck:
                    openDeckPicker();
                    return;
                case R.id.studyoptions_download_deck:
                	showDialog(DIALOG_DOWNLOAD_SELECTOR);
                	return;
                case R.id.studyoptions_card_browser:
                    openCardBrowser();
                    return;
                case R.id.studyoptions_statistics:
                	mStatisticType = -1;
                	showDialog(DIALOG_STATISTIC_TYPE);
                	return;
                case R.id.studyoptions_congrats_message:
                	mStatisticType = 0;
                	openStatistics(0);
                	return;
                case R.id.studyoptions_nodeck_message:
                	if (!mTextNoDeckTitle.getText().equals(getResources().getString(R.string.studyoptions_welcome_title))) {
                        startActivityForResult(
                                new Intent(StudyOptions.this, Preferences.class),
                                PREFERENCES_UPDATE);
                	} else {
                    	if (Utils.isIntentAvailable(StudyOptions.this, "android.intent.action.VIEW")) {
                    		Intent intent = new Intent("android.intent.action.VIEW", Uri.parse(getResources().getString(R.string.link_help)));
                    		startActivity(intent);
                    	} else {
                    		startActivity(new Intent(StudyOptions.this, About.class));
                    	}
                	}
                	return;
                case R.id.studyoptions_help:
                    if (Utils.isIntentAvailable(StudyOptions.this, "android.intent.action.VIEW")) {
                        Intent intent = new Intent("android.intent.action.VIEW", Uri.parse(getResources().getString(R.string.link_help)));
                        startActivity(intent);
                    } else {
                        startActivity(new Intent(StudyOptions.this, About.class));
                    }
                    return;
                case R.id.studyoptions_limit_tag_tv2:
                    if (mLimitTagNewActiveCheckBox.isChecked()) {
                        mSelectedLimitTagText = LIMIT_NEW_ACTIVE;
                        showDialog(DIALOG_TAGS);
                    }
                    return;
                case R.id.studyoptions_limit_tag_tv3:
                    if (mLimitTagNewInactiveCheckBox.isChecked()) {
                        mSelectedLimitTagText = LIMIT_NEW_INACTIVE;
                        showDialog(DIALOG_TAGS);
                    }
                    return;
                case R.id.studyoptions_limit_tag_tv5:
                    if (mLimitTagRevActiveCheckBox.isChecked()) {
                        mSelectedLimitTagText = LIMIT_REV_ACTIVE;
                        showDialog(DIALOG_TAGS);
                    }
                    return;
                case R.id.studyoptions_limit_tag_tv6:
                    if (mLimitTagRevInactiveCheckBox.isChecked()) {
                        mSelectedLimitTagText = LIMIT_REV_INACTIVE;
                        showDialog(DIALOG_TAGS);
                    }
                    return;
                default:
                    return;
            }
        }
    };

    private CompoundButton.OnCheckedChangeListener mLimitTagCheckedChangeListener = new CompoundButton.OnCheckedChangeListener(){

        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            switch (buttonView.getId()) {
                case R.id.studyoptions_limit_tag_new_active_check:
                    mSelectedLimitTagText = LIMIT_NEW_ACTIVE;
                    break;
                case R.id.studyoptions_limit_tag_new_inactive_check:
                    mSelectedLimitTagText = LIMIT_NEW_INACTIVE;
                    break;
                case R.id.studyoptions_limit_tag_rev_active_check:
                    mSelectedLimitTagText = LIMIT_REV_ACTIVE;
                    break;
                case R.id.studyoptions_limit_tag_rev_inactive_check:
                    mSelectedLimitTagText = LIMIT_REV_INACTIVE;
                    break;
                default:
                    return;
            }
            if (isChecked) {
                showDialog(DIALOG_TAGS);
            } else {
                updateLimitTagText(mSelectedLimitTagText, "");
            }
            return;
        }

    };

	Handler ZeemoteHandler = new Handler() {
		public void handleMessage(Message msg){
			switch(msg.what){
			case MSG_ZEEMOTE_STICK_UP:
				//sendKey(KeyEvent.KEYCODE_DPAD_UP);
				break;
			case MSG_ZEEMOTE_STICK_DOWN:
				//sendKey(KeyEvent.KEYCODE_DPAD_DOWN);
				break;
			case MSG_ZEEMOTE_STICK_LEFT:
				//sendKey(KeyEvent.KEYCODE_DPAD_LEFT);
				break;
			case MSG_ZEEMOTE_STICK_RIGHT:
				//sendKey(KeyEvent.KEYCODE_DPAD_RIGHT);
				break;				
			case MSG_ZEEMOTE_BUTTON_A:
				//sendKey(KeyEvent.KEYCODE_ENTER);
				openReviewer();
				break;
			case MSG_ZEEMOTE_BUTTON_B:
				//sendKey(KeyEvent.KEYCODE_BACK);
	            if (mCurrentContentView == CONTENT_CONGRATS) {
	            	finishCongrats();
	            } else  {
	            	openDeckPicker();
	            } 
				break;
			case MSG_ZEEMOTE_BUTTON_C:
				sendKey(KeyEvent.KEYCODE_BACK);
				break;
			case MSG_ZEEMOTE_BUTTON_D:
				break;
			}
			super.handleMessage(msg);
		}
	};
    

    private Boolean isValidInt(String test) {
        try {
            Integer.parseInt(test);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }


    protected void sendKey(int keycode) {
	    
    	this.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,keycode));
		this.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,keycode));
		Log.d("Zeemote","dispatched key "+keycode);
	}


	private Boolean isValidLong(String test) {
        try {
            Long.parseLong(test);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }


    private DialogInterface.OnClickListener mDialogSaveListener = new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            Deck deck = AnkiDroidApp.deck();
            deck.setNewCardOrder(mSpinnerNewCardOrder.getSelectedItemPosition());
            deck.setNewCardSpacing(mSpinnerNewCardSchedule.getSelectedItemPosition());
            deck.setRevCardOrder(mSpinnerRevCardOrder.getSelectedItemPosition());
            // TODO: mSpinnerFailCardOption
            // FIXME: invalid entries set to zero(unlimited) for now, better to set to default?
            String maxFailText = mEditMaxFailCard.getText().toString();
            if (!maxFailText.equals(Integer.toString(deck.getFailedCardMax()))) {
                if (maxFailText.equals("")) {
                        deck.setFailedCardMax(0);
                } else if (isValidInt(maxFailText)) {
                        deck.setFailedCardMax(Integer.parseInt(maxFailText));
                } else {
                        mEditMaxFailCard.setText("0");
                }
            }
            String inputText = mEditNewPerDay.getText().toString();
            if (!inputText.equals(Integer.toString(deck.getNewCardsPerDay()))) {
            	if (inputText.equals("")) {
            		deck.setNewCardsPerDay(0);
            	} else if (isValidInt(inputText)) {
            		deck.setNewCardsPerDay(Integer.parseInt(inputText));
            	} else {
            		mEditNewPerDay.setText("0");
            	}
            	updateValuesFromDeck();
            }
            boolean perDayChanged = deck.getPerDay() ^ mCheckBoxPerDay.isChecked();
          	deck.setPerDay(mCheckBoxPerDay.isChecked());
          	deck.setSuspendLeeches(mCheckBoxSuspendLeeches.isChecked());
            dialog.dismiss();
            if (perDayChanged){
                deck.updateCutoff();
                resetAndUpdateValuesFromDeck();
            }
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Themes.applyTheme(this);
        super.onCreate(savedInstanceState);

        Log.i(AnkiDroidApp.TAG, "StudyOptions - OnCreate()");

        if (hasErrorFiles()) {
            Intent i = new Intent(this, Feedback.class);
            startActivityForResult(i, REPORT_ERROR);
        }

        SharedPreferences preferences = restorePreferences();
        BackupManager.initBackup();
        registerExternalStorageListener();

        activeCramTags = new HashSet<String>();
        mSelectedTags = new HashSet<String>();

        initAllContentViews();

        if ((AnkiDroidApp.deck() != null) && (AnkiDroidApp.deck().hasFinishScheduler())) {
            AnkiDroidApp.deck().finishScheduler();
        }

        Intent intent = getIntent();
        if (Intent.ACTION_VIEW.equalsIgnoreCase(intent.getAction())
                && intent.getDataString() != null) {
            mDeckFilename = Uri.parse(intent.getDataString()).getPath();
            Log.i(AnkiDroidApp.TAG, "onCreate - deckFilename from VIEW intent: " + mDeckFilename);
        } else if (Intent.ACTION_MAIN.equalsIgnoreCase(intent.getAction())
                && intent.hasExtra(EXTRA_DECK)) {
            mDeckFilename = intent.getStringExtra(EXTRA_DECK);
            Log.i(AnkiDroidApp.TAG, "onCreate - deckFilename from MAIN intent: " + mDeckFilename);
        } else if (savedInstanceState != null) {
            // Use the same deck as last time Ankidroid was used.
            mDeckFilename = savedInstanceState.getString("deckFilename");
            Log.i(AnkiDroidApp.TAG, "onCreate - deckFilename from savedInstanceState: " + mDeckFilename);
        } else {
            // Log.i(AnkiDroidApp.TAG, "onCreate - " + preferences.getAll().toString());
            mDeckFilename = preferences.getString("deckFilename", null);
            Log.i(AnkiDroidApp.TAG, "onCreate - deckFilename from preferences: " + mDeckFilename);
        }
        if (!mSdCardAvailable) {
            showContentView(CONTENT_NO_EXTERNAL_STORAGE);
        } else {
            if (mDeckFilename == null || !new File(mDeckFilename).exists()) {
                showContentView(CONTENT_NO_DECK);
            } else {
            	if ((showDeckPickerOnStartup() || getIntent().getBooleanExtra("startDeckpicker", false)) && (!hasErrorFiles())) {
            		openDeckPicker();
            	} else {
            		// Load previous deck.
            		loadPreviousDeck();
            	}
            }
        }

        gestureDetector = new GestureDetector(new MyGestureDetector());
       	gestureListener = new View.OnTouchListener() {
       		public boolean onTouch(View v, MotionEvent event) {
       			if (gestureDetector.onTouchEvent(event)) {
       				return true;
       			}
       			return false;
       		}
       	};

        if (getApiLevel() >= 11) {
            mCompat = new CompatV11();
        } else {
            mCompat = new CompatV3();
        }
        //Zeemote controller initialization
         
		 if (AnkiDroidApp.zeemoteController() == null) AnkiDroidApp.setZeemoteController(new Controller(Controller.CONTROLLER_1));     
		 controllerUi = new ControllerAndroidUi(this, AnkiDroidApp.zeemoteController());
		 com.zeemote.util.Strings zstrings = com.zeemote.util.Strings.getStrings();
		 if (zstrings.isLocaleAvailable(mLocale)){
			 Log.d("Zeemote","Zeemote locale "+mLocale+" is available. Setting.");
			 zstrings.setLocale(mLocale);
		 } else {
			 Log.d("Zeemote","Zeemote locale "+mLocale+" is not available.");
		 }
		 if (mZeemoteEnabled){
		 if (!AnkiDroidApp.zeemoteController().isConnected())
		 {
    		 Log.d("Zeemote","starting connection in onCreate");
			 controllerUi.startConnectionProcess();
		 }
		}
    }


    /** Returns the API level of this device. */
    public static int getApiLevel() {
        try {
            return Integer.parseInt(Build.VERSION.SDK);
        } catch (NumberFormatException e) {
            // If there is an error, return the minimum supported version.
            return 3;
        }
    }


    @Override
    public void onConfigurationChanged(Configuration newConfig){
    	super.onConfigurationChanged(newConfig);
       	setLanguage(mLocale);
    	hideDeckInformation();
        boolean cramChecked = mToggleCram.isChecked();
        boolean limitChecked = mToggleLimit.isChecked();
        boolean limitEnabled = mToggleLimit.isEnabled();
        boolean nightModeChecekd = mNightMode.isChecked();
        boolean swapQA = mSwapQA.isChecked();
        int limitBarVisibility = View.GONE;
        if (mDailyBar != null) {
            limitBarVisibility = mGlobalLimitFrame.getVisibility();
        }

    	initAllContentViews();

    	updateValuesFromDeck();
    	showContentView();
        mToggleCram.setChecked(cramChecked);
        mToggleLimit.setChecked(limitChecked);
        mToggleLimit.setEnabled(limitEnabled);
        mNightMode.setChecked(nightModeChecekd);
        mSwapQA.setChecked(swapQA);
        if (mDailyBar != null) {
            mGlobalLimitFrame.setVisibility(limitBarVisibility);
        }
        showDeckInformation(false);
    }


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
                        Log.i(AnkiDroidApp.TAG, "mUnmountReceiver - Action = Media Eject");
                        closeOpenedDeck();
                        showContentView(CONTENT_NO_EXTERNAL_STORAGE);
                        mSdCardAvailable = false;
                    } else if (action.equals(Intent.ACTION_MEDIA_MOUNTED)) {
                        Log.i(AnkiDroidApp.TAG, "mUnmountReceiver - Action = Media Mounted");
                        mSdCardAvailable = true;
                        if (!mInDeckPicker) {
                            loadPreviousDeck();
                        }
                    }
                }
            };
            IntentFilter iFilter = new IntentFilter();
            iFilter.addAction(Intent.ACTION_MEDIA_EJECT);
            iFilter.addAction(Intent.ACTION_MEDIA_MOUNTED);
            iFilter.addDataScheme("file");
            registerReceiver(mUnmountReceiver, iFilter);
        }
    }


    @Override
    protected void onDestroy() {
    	super.onDestroy();
        Log.i(AnkiDroidApp.TAG, "StudyOptions - onDestroy()");
        closeOpenedDeck();
        MetaDB.closeDB();
        if (mUnmountReceiver != null) {
            unregisterReceiver(mUnmountReceiver);
        }
        //Disconnect Zeemote if connected
        if ((AnkiDroidApp.zeemoteController() != null) && (AnkiDroidApp.zeemoteController().isConnected())){
        	try {
        		Log.d("Zeemote","trying to disconnect in onDestroy...");
        		AnkiDroidApp.zeemoteController().disconnect();
        	}
        	catch (IOException ex){
        		Log.e("Zeemote","Error on zeemote disconnection in onDestroy: "+ex.getMessage());
        	}
        }
    }


     @Override
     protected void onPause() {
         if ((AnkiDroidApp.zeemoteController() != null) && (AnkiDroidApp.zeemoteController().isConnected())){ 
         	Log.d("Zeemote","Removing listener in onPause");
         	AnkiDroidApp.zeemoteController().removeButtonListener(this);
         	AnkiDroidApp.zeemoteController().removeJoystickListener(adapter);
     		adapter.removeButtonListener(this);
     		adapter = null;
         }        
    	 
         super.onPause();
         // Update the widget when pausing this activity.
         if (!mInDeckPicker && !mInReviewer) {
             WidgetStatus.update(getBaseContext());
         }
     }


    @Override
	protected void onResume() {
    	super.onResume();
	      if ((AnkiDroidApp.zeemoteController() != null) && (AnkiDroidApp.zeemoteController().isConnected())){
	    	  Log.d("Zeemote","Adding listener in onResume");
	    	  AnkiDroidApp.zeemoteController().addButtonListener(this);
	      	  adapter = new JoystickToButtonAdapter();
	      	  AnkiDroidApp.zeemoteController().addJoystickListener(adapter);
	      	  adapter.addButtonListener(this);
	      }
	      if ((mCurrentContentView == CONTENT_STUDY_OPTIONS || mCurrentContentView == CONTENT_SESSION_COMPLETE) && (mProgressDialog == null || !mProgressDialog.isShowing())) {
	    	  if (mTextDeckName.getVisibility() != View.VISIBLE) {
			      showDeckInformation(true);
	    	  } else {
	    	      updateValuesFromDeck();
	    	  }
	      }
	      // check for new day and reset deck if yes
	      if (Utils.isNewDay(PrefSettings.getSharedPrefs(getBaseContext()).getLong("lastTimeOpened", 0)) && (mCurrentContentView == CONTENT_STUDY_OPTIONS || mCurrentContentView == CONTENT_SESSION_COMPLETE)) {
	    	  BackupManager.initBackup();
	    	  if (!DeckTask.taskIsRunning()) {
		    	  displayProgressDialogAndLoadDeck();
	    	  }
	      }
	      BroadcastMessages.showDialog();
	}


	@Override
    public boolean onKeyDown(int keyCode, KeyEvent event)  {
        if (keyCode == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0) {
            Log.i(AnkiDroidApp.TAG, "StudyOptions - onBackPressed()");
            if (mCurrentContentView == CONTENT_CONGRATS) {
            	finishCongrats();
            } else if (mStartupMode == SUM_DECKPICKER) {
            	openDeckPicker();
            } else {
                closeStudyOptions();            	
            }
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }


    private void closeStudyOptions() {
    	closeStudyOptions(true);
    }
    private void closeStudyOptions(boolean closeDeck) {
		mIsClosing = true;
        MetaDB.closeDB();
        if (closeDeck && AnkiDroidApp.deck() != null && mSdCardAvailable) {
        	DeckTask.launchDeckTask(DeckTask.TASK_TYPE_CLOSE_DECK, new DeckTask.TaskListener() {
		        @Override
		        public void onPreExecute() {
		        }
		        @Override
		        public void onPostExecute(DeckTask.TaskData result) {
		        }
		        @Override
		        public void onProgressUpdate(DeckTask.TaskData... values) {
		        }
			}, new DeckTask.TaskData(AnkiDroidApp.deck(), 0));
        }
        AnkiDroidApp.setDeck(null);
        mCompat.invalidateOptionsMenu(this);
        StudyOptions.this.finish();
    }


    private void restartApp() {
    	// restarts application in order to apply new themes or localisations
    	Intent i = getBaseContext().getPackageManager().getLaunchIntentForPackage(getBaseContext().getPackageName());
        if (AnkiDroidApp.deck() != null && mSdCardAvailable) {
        	AnkiDroidApp.deck().closeDeck();
        }
    	AnkiDroidApp.setDeck(null);
        mCompat.invalidateOptionsMenu(this);
        MetaDB.closeDB();
        StudyOptions.this.finish();
    	startActivity(i);
    }


    private void openReviewer() {
    	if (mCurrentContentView == CONTENT_STUDY_OPTIONS || mCurrentContentView == CONTENT_SESSION_COMPLETE) {
    		mInReviewer = true;
    		Intent reviewer = new Intent(StudyOptions.this, Reviewer.class);
            reviewer.putExtra("deckFilename", mDeckFilename);
    		startActivityForResult(reviewer, REQUEST_REVIEW);
        	if (getApiLevel() > 4) {
       			ActivityTransitionAnimation.slide(this, ActivityTransitionAnimation.LEFT);
        	}
    	} else if (mCurrentContentView == CONTENT_CONGRATS) {
    		startEarlyReview();
    	}
    }


    private void startEarlyReview() {
		Deck deck = AnkiDroidApp.deck();
        if (deck != null) {
    		mInReviewer = true;
            deck.setupReviewEarlyScheduler();
            deck.reset();
    		Intent reviewer = new Intent(StudyOptions.this, Reviewer.class);
            reviewer.putExtra("deckFilename", mDeckFilename);
            startActivityForResult(reviewer, REQUEST_REVIEW);
        	if (getApiLevel() > 4) {
       			ActivityTransitionAnimation.slide(this, ActivityTransitionAnimation.LEFT);
        	}
        }
    }


    private void startLearnMore() {
		Deck deck = AnkiDroidApp.deck();
        if (deck != null) {
    		mInReviewer = true;
            deck.setupLearnMoreScheduler();
            deck.reset();
    		Intent reviewer = new Intent(StudyOptions.this, Reviewer.class);
    		reviewer.putExtra("deckFilename", mDeckFilename);
        	startActivityForResult(reviewer, REQUEST_REVIEW);
    		if (getApiLevel() > 4) {
    			ActivityTransitionAnimation.slide(this, ActivityTransitionAnimation.LEFT);
    		}
        }
    }


    private void loadPreviousDeck() {
        Intent deckLoadIntent = new Intent();
        deckLoadIntent.putExtra(OPT_DB, mDeckFilename);
        onActivityResult(PICK_DECK_REQUEST, RESULT_OK, deckLoadIntent);
    }


    private void closeOpenedDeck() {
        if (AnkiDroidApp.deck() != null && mSdCardAvailable) {
            AnkiDroidApp.deck().closeDeck();
            AnkiDroidApp.setDeck(null);
            mCompat.invalidateOptionsMenu(this);
        }
    }


    private boolean hasErrorFiles() {
        for (String file : this.fileList()) {
            if (file.endsWith(".stacktrace")) {
                return true;
            }
        }

        return false;
    }


    private void initAllContentViews() {
        // The main study options view that will be used when there are reviews left.
        mStudyOptionsView = getLayoutInflater().inflate(R.layout.studyoptions, null);
        Themes.setContentStyle(mStudyOptionsView, Themes.CALLER_STUDYOPTIONS);

        mStudyOptionsMain = (View) mStudyOptionsView.findViewById(R.id.studyoptions_main);
        Themes.setWallpaper(mStudyOptionsMain);

        mTextDeckName = (TextView) mStudyOptionsView.findViewById(R.id.studyoptions_deck_name);
        Themes.setTitleStyle(mTextDeckName);

        mStatisticsField = (LinearLayout) mStudyOptionsView.findViewById(R.id.studyoptions_statistic_field);
        Themes.setTextViewStyle(mStatisticsField);

        Themes.setTitleStyle(mStudyOptionsView.findViewById(R.id.studyoptions_bottom));

        mButtonStart = (Button) mStudyOptionsView.findViewById(R.id.studyoptions_start);
        mToggleCram = (ToggleButton) mStudyOptionsView.findViewById(R.id.studyoptions_cram);

        mToggleLimit = (ToggleButton) mStudyOptionsView.findViewById(R.id.studyoptions_limit);

        mCardBrowser = (Button) mStudyOptionsView.findViewById(R.id.studyoptions_card_browser);
        mStatisticsButton = (Button) mStudyOptionsView.findViewById(R.id.studyoptions_statistics);

        mDailyBar = (View) mStudyOptionsView.findViewById(R.id.studyoptions_daily_bar);
        mMatureBar = (View) mStudyOptionsView.findViewById(R.id.studyoptions_mature_bar);
        mGlobalLimitFrame = (View) mStudyOptionsView.findViewById(R.id.studyoptions_global_limit_bars);
        mGlobalLimitBar = (View) mStudyOptionsView.findViewById(R.id.studyoptions_global_limit_bar);
        mGlobalMatLimitBar = (View) mStudyOptionsView.findViewById(R.id.studyoptions_global_mat_limit_bar);
        mGlobalBar = (View) mStudyOptionsView.findViewById(R.id.studyoptions_global_bar);
        mGlobalMatBar = (View) mStudyOptionsView.findViewById(R.id.studyoptions_global_mat_bar);
        mBarsMax = (View) mStudyOptionsView.findViewById(R.id.studyoptions_bars_max);
        if (mDailyBar != null) {
            ViewTreeObserver vto = mBarsMax.getViewTreeObserver();
            vto.addOnGlobalLayoutListener(new OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    mBarsMax.getViewTreeObserver().removeGlobalOnLayoutListener(this);
                    updateStatisticBars();
                }
            });
        }

        mTextReviewsDue = (TextView) mStudyOptionsView.findViewById(R.id.studyoptions_reviews_due);
        mTextReviewsDue.setText("    ");
        mTextNewToday = (TextView) mStudyOptionsView.findViewById(R.id.studyoptions_new_today);
        mTextETA = (TextView) mStudyOptionsView.findViewById(R.id.studyoptions_eta);
        mTextNewTotal = (TextView) mStudyOptionsView.findViewById(R.id.studyoptions_new_total);
        mNightMode = (CheckBox) mStudyOptionsView.findViewById(R.id.studyoptions_night_mode);
        mNightMode.setChecked(mInvertedColors);
        mNightMode.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            	@Override
            	public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            		if (mInvertedColors != isChecked) {
            			mInvertedColors = isChecked;
                    	savePreferences("invertedColors");
            		}
            	}
            });
        mSwapQA = (CheckBox) mStudyOptionsView.findViewById(R.id.studyoptions_swap);
        mSwapQA.setChecked(mSwap);
        mSwapQA.setOnClickListener(new View.OnClickListener() {

        	@Override
            public void onClick(View view) {
            	if (mSwapQA.isChecked()) {
            		showDialog(DIALOG_SWAP_QA);
            	} else if (mSwap){
            		mSwap = false;
            		savePreferences("swapqa");
            	}
        		mSwapQA.setChecked(false);				
            }
        });
        
        mHelp = (TextView) mStudyOptionsView.findViewById(R.id.studyoptions_help);
        mHelp.setOnClickListener(mButtonClickListener);

        mButtonStart.setOnClickListener(mButtonClickListener);
        mToggleCram.setOnClickListener(mButtonClickListener);
        mToggleLimit.setOnClickListener(mButtonClickListener);
        mCardBrowser.setOnClickListener(mButtonClickListener);
        mStatisticsButton.setOnClickListener(mButtonClickListener);

        // The view to use when there is no deck loaded yet.
        // TODO: Add and init view here.
        mNoDeckView = getLayoutInflater().inflate(R.layout.studyoptions_nodeck, null);
        Themes.setWallpaper(mNoDeckView);

        mTextNoDeckTitle = (TextView) mNoDeckView.findViewById(R.id.studyoptions_nodeck_title);
        Themes.setTitleStyle(mTextNoDeckTitle);
        mTextNoDeckMessage = (TextView) mNoDeckView.findViewById(R.id.studyoptions_nodeck_message);
        Themes.setTextViewStyle(mTextNoDeckMessage);
        mTextNoDeckMessage.setOnClickListener(mButtonClickListener);
        Themes.setTextViewStyle(mTextNoDeckMessage);

        mNoDeckView.findViewById(R.id.studyoptions_load_sample_deck).setOnClickListener(mButtonClickListener);
        mNoDeckView.findViewById(R.id.studyoptions_download_deck).setOnClickListener(mButtonClickListener);
        mNoDeckView.findViewById(R.id.studyoptions_load_other_deck).setOnClickListener(mButtonClickListener);

        // The view that shows the congratulations view.
        mCongratsView = getLayoutInflater().inflate(R.layout.studyoptions_congrats, null);

        Themes.setWallpaper(mCongratsView);
        Themes.setTitleStyle(mCongratsView.findViewById(R.id.studyoptions_congrats_title));

        mTextCongratsMessage = (TextView) mCongratsView.findViewById(R.id.studyoptions_congrats_message);
        Themes.setTextViewStyle(mTextCongratsMessage);

        mTextCongratsMessage.setOnClickListener(mButtonClickListener);
        mButtonCongratsLearnMore = (Button) mCongratsView.findViewById(R.id.studyoptions_congrats_learnmore);
        mButtonCongratsReviewEarly = (Button) mCongratsView.findViewById(R.id.studyoptions_congrats_reviewearly);
        mButtonCongratsSyncDeck = (Button) mCongratsView.findViewById(R.id.studyoptions_congrats_syncdeck);
        mButtonCongratsOpenOtherDeck = (Button) mCongratsView.findViewById(R.id.studyoptions_congrats_open_other_deck);
        mButtonCongratsFinish = (Button) mCongratsView.findViewById(R.id.studyoptions_congrats_finish);

        mButtonCongratsLearnMore.setOnClickListener(mButtonClickListener);
        mButtonCongratsReviewEarly.setOnClickListener(mButtonClickListener);
        mButtonCongratsSyncDeck.setOnClickListener(mButtonClickListener);
        mButtonCongratsOpenOtherDeck.setOnClickListener(mButtonClickListener);
        mButtonCongratsFinish.setOnClickListener(mButtonClickListener);

        // The view to use when there is no external storage available
        mNoExternalStorageView = getLayoutInflater().inflate(R.layout.studyoptions_nostorage, null);
        Themes.setWallpaper(mNoExternalStorageView);
        Themes.setTitleStyle(mNoExternalStorageView.findViewById(R.id.studyoptions_nostorage_title));
        Themes.setTextViewStyle(mNoExternalStorageView.findViewById(R.id.studyoptions_nostorage_message));
    }


    private OnClickListener mSyncConflictResolutionListener = new OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            switch (which) {
                case DialogInterface.BUTTON_POSITIVE:
                    syncDeck("keepLocal");
                    break;
                case DialogInterface.BUTTON_NEUTRAL:
                    syncDeck("keepRemote");
                    break;
                case DialogInterface.BUTTON_NEGATIVE:
                default:
            }
        }
    };


    private OnClickListener mStatisticListener = new OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
        	if (mStatisticType == -1) {
        		mStatisticType = which;
        		if (mStatisticType != Statistics.TYPE_DECK_SUMMARY) {
        			showDialog(DIALOG_STATISTIC_PERIOD);
        		} else {
        			openStatistics(0);
        		}
        	} else {
        		openStatistics(which);
        	}
        }
    };


	@Override
	protected Dialog onCreateDialog(int id) {
		StyledDialog dialog = null;
		Resources res = getResources();
		StyledDialog.Builder builder = new StyledDialog.Builder(this);

		switch (id) {
		case DIALOG_SYNC_LOG:
	        builder.setTitle(getResources().getString(R.string.sync_log_title));
			builder.setPositiveButton(getResources().getString(R.string.ok), null);
			dialog = builder.create();
			break;

		case DIALOG_NO_SPACE_LEFT:
	        builder.setNegativeButton(getResources().getString(R.string.dont_show_again), new OnClickListener() {

	            @Override
	            public void onClick(DialogInterface dialog, int which) {
	            	PrefSettings.getSharedPrefs(getBaseContext()).edit().putBoolean("dontShowLowMemory", true).commit();
	            }
	        });
		case DIALOG_BACKUP_NO_SPACE_LEFT:
	        builder.setTitle(getResources().getString(R.string.backup_manager_title));
	        builder.setIcon(android.R.drawable.ic_dialog_alert);
	        builder.setPositiveButton(getResources().getString(R.string.ok), null);
			dialog = builder.create();
			break;

		case DIALOG_BACKUP_ERROR:
	        builder.setTitle(getResources().getString(R.string.backup_manager_title));
	        builder.setIcon(android.R.drawable.ic_dialog_alert);
	        builder.setMessage(getResources().getString(R.string.backup_deck_error));
			builder.setPositiveButton(getResources().getString(R.string.ok), null);
			dialog = builder.create();
			break;

		case DIALOG_NO_CONNECTION:
			builder.setTitle(res.getString(R.string.connection_error_title));
	        builder.setIcon(android.R.drawable.ic_dialog_alert);
	        builder.setMessage(res.getString(R.string.connection_needed));
	        builder.setPositiveButton(res.getString(R.string.ok), null);
			dialog = builder.create();
			break;

		case DIALOG_USER_NOT_LOGGED_IN:
	        builder.setTitle(res.getString(R.string.connection_error_title));
	        builder.setIcon(android.R.drawable.ic_dialog_alert);
	        builder.setMessage(res.getString(R.string.no_user_password_error_message));
	        builder.setPositiveButton(res.getString(R.string.log_in), new OnClickListener() {

	            @Override
	            public void onClick(DialogInterface dialog, int which) {
	                Intent myAccount = new Intent(StudyOptions.this, MyAccount.class);
	                myAccount.putExtra("notLoggedIn", true);
	                startActivityForResult(myAccount, LOG_IN);
			        if (getApiLevel() > 4) {
			            ActivityTransitionAnimation.slide(StudyOptions.this, ActivityTransitionAnimation.LEFT);
			        }
	            }
	        });
	        builder.setNegativeButton(res.getString(R.string.cancel), null);
	        dialog = builder.create();
	        break;

		case DIALOG_SWAP_QA:
	        builder.setTitle(getResources().getString(R.string.swap_qa_title));
	        builder.setMessage(getResources().getString(R.string.swap_qa_text));
	        builder.setIcon(android.R.drawable.ic_dialog_alert);
	        builder.setPositiveButton(res.getString(R.string.yes), new OnClickListener() {

	            @Override
	            public void onClick(DialogInterface dialog, int which) {
	            	mSwapQA.setChecked(true);
	        		mSwap = true;
	        		savePreferences("swapqa");
	            }
	        });
	        builder.setNegativeButton(res.getString(R.string.cancel), null);
			dialog = builder.create();
			break;

		case DIALOG_CONNECTION_ERROR:
	        builder.setTitle(res.getString(R.string.connection_error_title));
	        builder.setIcon(android.R.drawable.ic_dialog_alert);
	        builder.setMessage(res.getString(R.string.connection_error_message));
	        builder.setPositiveButton(res.getString(R.string.retry), new OnClickListener() {

	            @Override
	            public void onClick(DialogInterface dialog, int which) {
	                syncDeck(null);
	            }
	        });
	        builder.setNegativeButton(res.getString(R.string.cancel), null);
			dialog = builder.create();
			break;

		case DIALOG_SYNC_CONFLICT_RESOLUTION:
	        builder.setTitle(res.getString(R.string.sync_conflict_title));
	        builder.setIcon(android.R.drawable.ic_input_get);
	        builder.setMessage(res.getString(R.string.sync_conflict_message));
	        builder.setPositiveButton(res.getString(R.string.sync_conflict_local), mSyncConflictResolutionListener);
	        builder.setNeutralButton(res.getString(R.string.sync_conflict_remote), mSyncConflictResolutionListener);
	        builder.setNegativeButton(res.getString(R.string.sync_conflict_cancel), mSyncConflictResolutionListener);
	        builder.setCancelable(false);
			dialog = builder.create();
			break;

		case DIALOG_STATISTIC_PERIOD:
	        builder.setTitle(res.getString(R.string.statistics_period_title));
	        builder.setIcon(android.R.drawable.ic_menu_sort_by_size);
	        builder.setSingleChoiceItems(getResources().getStringArray(R.array.statistics_period_labels), 0, mStatisticListener);
			dialog = builder.create();
			break;

		case DIALOG_STATISTIC_TYPE:
	        builder.setTitle(res.getString(R.string.statistics_type_title));
	        builder.setIcon(android.R.drawable.ic_menu_sort_by_size);
	        builder.setSingleChoiceItems(getResources().getStringArray(R.array.statistics_type_labels), Statistics.TYPE_DUE, mStatisticListener);
	        dialog = builder.create();
			break;

		case DIALOG_DECK_NOT_LOADED:
	        builder.setTitle(res.getString(R.string.backup_manager_title));
	        builder.setIcon(android.R.drawable.ic_dialog_alert);
	        builder.setPositiveButton(res.getString(R.string.retry), new OnClickListener() {

	            @Override
	            public void onClick(DialogInterface dialog, int which) {
	                displayProgressDialogAndLoadDeck();
	            }
	        });
	        builder.setNegativeButton(res.getString(R.string.backup_restore), new OnClickListener() {

	            @Override
	            public void onClick(DialogInterface dialog, int which) {
	            	Resources res = getResources();
	            	mBackups = BackupManager.getDeckBackups(new File(mDeckFilename));
	            	if (mBackups.length == 0) {
	            		StyledDialog.Builder builder = new StyledDialog.Builder(StudyOptions.this);
	            		builder.setTitle(res.getString(R.string.backup_manager_title))
	            			.setIcon(android.R.drawable.ic_dialog_alert)
	            			.setMessage(res.getString(R.string.backup_restore_no_backups))
	            			.setPositiveButton(res.getString(R.string.ok), new Dialog.OnClickListener() {

					            @Override
					            public void onClick(DialogInterface dialog, int which) {
					            	showDialog(DIALOG_DECK_NOT_LOADED);
					            }
						}).setCancelable(true).setOnCancelListener(new OnCancelListener() {

							@Override
							public void onCancel(DialogInterface arg0) {
								showDialog(DIALOG_DECK_NOT_LOADED);
							}
						}).show();
	            	} else {
	            		String[] dates = new String[mBackups.length];
	            		for (int i = 0; i < mBackups.length; i++) {
	            			dates[i] = mBackups[i].getName().replaceAll(".*-(\\d{4}-\\d{2}-\\d{2}).anki", "$1");
	            		}
	            		StyledDialog.Builder builder = new StyledDialog.Builder(StudyOptions.this);
	            		builder.setTitle(res.getString(R.string.backup_restore_select_title))
	            			.setIcon(android.R.drawable.ic_input_get)
	                    	.setSingleChoiceItems(dates, dates.length, new DialogInterface.OnClickListener(){

								@Override
								public void onClick(DialogInterface dialog, int which) {
									DeckTask.launchDeckTask(DeckTask.TASK_TYPE_RESTORE_DECK, mRestoreDeckHandler, new DeckTask.TaskData(null, new String[] {mDeckFilename, mBackups[which].getPath()}, 0, 0));
									dialog.dismiss();
								}
							}).setCancelable(true).setOnCancelListener(new OnCancelListener() {

								@Override
								public void onCancel(DialogInterface arg0) {
									showDialog(DIALOG_DECK_NOT_LOADED);
								}
							}).show();
	        		}
	            }
	        });
	        builder.setNeutralButton(res.getString(R.string.backup_repair_deck), new OnClickListener() {

	            @Override
	            public void onClick(DialogInterface dialog, int which) {
	            	DeckTask.launchDeckTask(DeckTask.TASK_TYPE_REPAIR_DECK, mRepairDeckHandler, new DeckTask.TaskData(mDeckFilename));
	            }
	        });
	        builder.setCancelable(true);
	        dialog = builder.create();
			break;
		case DIALOG_MORE:
	        View contentViewMore = getLayoutInflater().inflate(R.layout.studyoptions_more_dialog_contents, null);
	        mSpinnerNewCardOrder = (Spinner) contentViewMore.findViewById(R.id.studyoptions_new_card_order);
	        mSpinnerNewCardSchedule = (Spinner) contentViewMore.findViewById(R.id.studyoptions_new_card_schedule);
	        mSpinnerRevCardOrder = (Spinner) contentViewMore.findViewById(R.id.studyoptions_rev_card_order);
	        mSpinnerFailCardOption = (Spinner) contentViewMore.findViewById(R.id.studyoptions_fail_card_option);
	        mEditMaxFailCard = (EditText) contentViewMore.findViewById(R.id.studyoptions_max_fail_card);
	        mEditNewPerDay = (EditText) contentViewMore.findViewById(R.id.studyoptions_new_cards_per_day);
	        mCheckBoxPerDay = (CheckBox) contentViewMore.findViewById(R.id.studyoptions_per_day);
	        mCheckBoxSuspendLeeches = (CheckBox) contentViewMore.findViewById(R.id.studyoptions_suspend_leeches);

	        builder.setTitle(R.string.studyoptions_more_dialog_title);
	        builder.setPositiveButton(R.string.studyoptions_more_save, mDialogSaveListener);
	        builder.setView(contentViewMore, true);
	        dialog = builder.create();
	        break;

		case DIALOG_DOWNLOAD_SELECTOR:
	        builder.setTitle(res.getString(R.string.menu_download_deck));
	        String[] items;
	    	items = new String[2];
	    	items[0] = res.getString(R.string.menu_download_personal_deck);
	    	items[1] = res.getString(R.string.menu_download_shared_deck);
	        builder.setItems(items, new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int item) {
					if (item == 0) {
		            	openPersonalDeckPicker();
					} else {
		            	openSharedDeckPicker();
					}
			    }
			});
	        dialog = builder.create();
	        break;

		case DIALOG_LIMIT_SESSION:
	        // Custom view for the dialog content.
	        View contentView = getLayoutInflater().inflate(R.layout.studyoptions_limit_dialog_contents, null);
	        mEditSessionTime = (EditText) contentView.findViewById(R.id.studyoptions_session_minutes);
	        mEditSessionQuestions = (EditText) contentView.findViewById(R.id.studyoptions_session_questions);
	        mSessionLimitCheckBox = (CheckBox) contentView.findViewById(R.id.studyoptions_limit_session_check);
	        mLimitTagsCheckBox = (CheckBox) contentView.findViewById(R.id.studyoptions_limit_tag_check);
	        mLimitTagNewActiveCheckBox = (CheckBox) contentView.findViewById(R.id.studyoptions_limit_tag_new_active_check);
	        mLimitTagNewInactiveCheckBox = (CheckBox) contentView.findViewById(R.id.studyoptions_limit_tag_new_inactive_check);
	        mLimitTagRevActiveCheckBox = (CheckBox) contentView.findViewById(R.id.studyoptions_limit_tag_rev_active_check);
	        mLimitTagRevInactiveCheckBox = (CheckBox) contentView.findViewById(R.id.studyoptions_limit_tag_rev_inactive_check);
	        mLimitSessionTv1 = (TextView) contentView.findViewById(R.id.studyoptions_limit_session_tv1);
	        mLimitSessionTv2 = (TextView) contentView.findViewById(R.id.studyoptions_limit_session_tv2);
	        mLimitTagTv1 = (TextView) contentView.findViewById(R.id.studyoptions_limit_tag_tv1);
	        mLimitTagTv2 = (TextView) contentView.findViewById(R.id.studyoptions_limit_tag_tv2);
	        mLimitTagTv3 = (TextView) contentView.findViewById(R.id.studyoptions_limit_tag_tv3);
	        mLimitTagTv4 = (TextView) contentView.findViewById(R.id.studyoptions_limit_tag_tv4);
	        mLimitTagTv5 = (TextView) contentView.findViewById(R.id.studyoptions_limit_tag_tv5);
	        mLimitTagTv6 = (TextView) contentView.findViewById(R.id.studyoptions_limit_tag_tv6);
	        mLimitTagTv2.setOnClickListener(mButtonClickListener);
	        mLimitTagTv3.setOnClickListener(mButtonClickListener);
	        mLimitTagTv5.setOnClickListener(mButtonClickListener);
	        mLimitTagTv6.setOnClickListener(mButtonClickListener);

	        mSessionLimitCheckBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener(){
	            @Override
	            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
	                mEditSessionTime.setEnabled(isChecked);
	                mEditSessionQuestions.setEnabled(isChecked);
	                if (!isChecked) {
	                    mEditSessionTime.setText("");
	                    mEditSessionQuestions.setText("");
	                    mEditSessionTime.clearFocus();
	                    mEditSessionQuestions.clearFocus();
	                    ((InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(mEditSessionTime.getWindowToken(), 0);
	                }
	                int color = getResources().getColor((isChecked) ? R.color.studyoptions_foreground : R.color.studyoptions_foreground_deactivated);
	                mLimitSessionTv1.setTextColor(color);
	                mLimitSessionTv2.setTextColor(color);
	            }
	            });

	        mLimitTagsCheckBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener(){
	            @Override
	            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
	                mLimitTagNewActiveCheckBox.setEnabled(isChecked);
	                mLimitTagNewInactiveCheckBox.setEnabled(isChecked);
	                mLimitTagRevActiveCheckBox.setEnabled(isChecked);
	                mLimitTagRevInactiveCheckBox.setEnabled(isChecked);
	                if (!isChecked) {
	                    mLimitTagNewActiveCheckBox.setChecked(false);
	                    mLimitTagNewInactiveCheckBox.setChecked(false);
	                    mLimitTagRevActiveCheckBox.setChecked(false);
	                    mLimitTagRevInactiveCheckBox.setChecked(false);
	                }
	                int color = getResources().getColor((isChecked) ? R.color.studyoptions_foreground : R.color.studyoptions_foreground_deactivated);
	                mLimitTagTv1.setTextColor(color);
	                mLimitTagTv2.setTextColor(color);
	                mLimitTagTv3.setTextColor(color);
	                mLimitTagTv4.setTextColor(color);
	                mLimitTagTv5.setTextColor(color);
	                mLimitTagTv6.setTextColor(color);
	            }
	            });

	        mLimitTagNewActiveCheckBox.setOnCheckedChangeListener(mLimitTagCheckedChangeListener);
	        mLimitTagNewInactiveCheckBox.setOnCheckedChangeListener(mLimitTagCheckedChangeListener);
	        mLimitTagRevActiveCheckBox.setOnCheckedChangeListener(mLimitTagCheckedChangeListener);
	        mLimitTagRevInactiveCheckBox.setOnCheckedChangeListener(mLimitTagCheckedChangeListener);

	        builder.setTitle(R.string.studyoptions_limit_dialog_title);
	        builder.setPositiveButton(R.string.studyoptions_more_save, new DialogInterface.OnClickListener() {

	            @Override
	            public void onClick(DialogInterface dialog, int which) {
	                Deck deck = AnkiDroidApp.deck();
	                boolean changed = false;
	                String textTime = mEditSessionTime.getText().toString();
	                if (!textTime.equals(Long.toString(deck.getSessionTimeLimit() / 60))) {
	                  if (textTime.equals("")) {
	                      deck.setSessionTimeLimit(0);
	                  } else if (isValidLong(textTime)) {
	                      deck.setSessionTimeLimit(Long.parseLong(textTime) * 60);
	                  }
	                }
	                String textReps = mEditSessionQuestions.getText().toString();
	                if (!textReps.equals(Long.toString(deck.getSessionRepLimit()))) {
	                    if (textReps.equals("")) {
	                        deck.setSessionRepLimit(0);
	                    } else if (isValidLong(textReps)) {
	                        deck.setSessionRepLimit(Long.parseLong(textReps));
	                    }
	                    changed = true;
	                }
	                if (!deck.getVar("newActive").equals(mLimitNewActive)) {
	                    deck.setVar("newActive", mLimitNewActive);
	                    changed = true;
	                } 
	                if (!deck.getVar("newInactive").equals(mLimitNewInactive)) {
	                    deck.setVar("newInactive", mLimitNewInactive);
	                    changed = true;
	                } 
	                if (!deck.getVar("revActive").equals(mLimitRevActive)) {
	                    deck.setVar("revActive", mLimitRevActive);
	                    changed = true;
	                } 
	                if (!deck.getVar("revInactive").equals(mLimitRevInactive)) {
	                    deck.setVar("revInactive", mLimitRevInactive);
	                    changed = true;
	                }
	                if (changed) {
	                	resetAndUpdateValuesFromDeck();
	                }
	                mToggleLimit.setChecked((mSessionLimitCheckBox.isChecked() && !(textTime.length() == 0 && textReps.length() == 0)) || (mLimitTagsCheckBox.isChecked() && (mLimitTagNewActiveCheckBox.isChecked() || mLimitTagNewInactiveCheckBox.isChecked()
	                        || mLimitTagRevActiveCheckBox.isChecked() || mLimitTagRevInactiveCheckBox.isChecked())));
	            }
	        });
	        builder.setView(contentView, true);
	        dialog = builder.create();
	        break;
	        
		case DIALOG_TAGS:
			builder.setTitle(R.string.studyoptions_limit_select_tags);
	        builder.setPositiveButton(res.getString(R.string.select), new OnClickListener() {
	            @Override
	            public void onClick(DialogInterface dialog, int which) {
	                String readableText = mSelectedTags.toString();
	                updateLimitTagText(mSelectedLimitTagText, readableText.substring(1, readableText.length()-1));
	            }
	        });
	        builder.setNegativeButton(res.getString(R.string.cancel),  new OnClickListener() {
	            @Override
	            public void onClick(DialogInterface dialog, int which) {
	                updateLimitTagText(mSelectedLimitTagText, getSelectedTags(mSelectedLimitTagText));
	            }
	        });
	        builder.setOnCancelListener(new OnCancelListener() {
	            @Override
	            public void onCancel(DialogInterface dialog) {
	                updateLimitTagText(mSelectedLimitTagText, getSelectedTags(mSelectedLimitTagText));
	            }

	        });
	        dialog = builder.create();
	        break;
		case DIALOG_CRAM:
	        builder.setTitle(R.string.studyoptions_cram_dialog_title);
	        builder.setPositiveButton(res.getString(R.string.begin_cram), new OnClickListener() {
	            @Override
	            public void onClick(DialogInterface dialog, int which) {
	                mToggleCram.setChecked(true);
	                onCram();
	            }
	        });
	        builder.setNegativeButton(res.getString(R.string.cancel), null);

	        Spinner spinner = new Spinner(this);
	        
	        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this, R.array.cram_review_order_labels, android.R.layout.simple_spinner_item);
	        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
	        spinner.setAdapter(adapter);
	        spinner.setSelection(0);
	        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
	            @Override
	            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
	                cramOrder = cramOrderList[position];
	            }
	            @Override
	            public void onNothingSelected(AdapterView<?> arg0) {
	                return;
	            }
	        });

	        builder.setView(spinner, false, true);
	        dialog = builder.create();
	        break;

		case DIALOG_WELCOME:
			builder.setTitle(R.string.studyoptions_welcome_title);
	        WebView view = new WebView(this);
	        view.setBackgroundColor(res.getColor(Themes.getDialogBackgroundColor()));
	        view.loadDataWithBaseURL("", "<html><body text=\"#FFFFFF\" link=\"#E37068\" alink=\"#E37068\" vlink=\"#E37068\">" + res.getString(R.string.studyoptions_welcome_dialog).replace("\n", "<br>") + "</body></html>", "text/html", "UTF-8", "");
	        builder.setView(view, true);
    		builder.setCancelable(true);
			builder.setPositiveButton(res.getString(R.string.yes), new OnClickListener() {
	            @Override
	            public void onClick(DialogInterface dialog, int which) {
		        	PrefSettings.getSharedPrefs(StudyOptions.this.getBaseContext()).edit().putBoolean("firstStart", false).commit();
		        	mNewVersionAlert = null;
		        	loadSampleDeck();
	            }
	        });
	        builder.setNegativeButton(res.getString(R.string.no),  new OnClickListener() {
	            @Override
	            public void onClick(DialogInterface dialog, int which) {
		        	PrefSettings.getSharedPrefs(StudyOptions.this.getBaseContext()).edit().putBoolean("firstStart", false).commit();
		        	if (mNewVersionAlert != null) {
		        		mNewVersionAlert.show();
		        	} else {
		        		BroadcastMessages.checkForNewMessages(StudyOptions.this);
		        	}
	            }
	        });
	        builder.setOnCancelListener(new OnCancelListener() {
	            @Override
	            public void onCancel(DialogInterface dialog) {
		        	if (mNewVersionAlert != null) {
		        		mNewVersionAlert.show();
		        	} else {
		        		BroadcastMessages.checkForNewMessages(StudyOptions.this);
		        	}
	            }

	        });
	        dialog = builder.create();
	        mWelcomeAlert = dialog;
			break;

		case DIALOG_ANSWERING_ERROR:
			builder.setTitle(R.string.answering_error_title);
	        builder.setIcon(android.R.drawable.ic_dialog_alert);
			builder.setMessage(R.string.answering_error_message);
	        builder.setPositiveButton(res.getString(R.string.backup_repair_deck), new OnClickListener() {
	            @Override
	            public void onClick(DialogInterface dialog, int which) {
	            	DeckTask.launchDeckTask(DeckTask.TASK_TYPE_REPAIR_DECK, mRepairDeckHandler, new DeckTask.TaskData(mDeckFilename));
	            }
	        });
	        builder.setNeutralButton(res.getString(R.string.answering_error_report), new OnClickListener() {
	            @Override
	            public void onClick(DialogInterface dialog, int which) {
	                mShowRepairDialog = true;
	                Intent i = new Intent(StudyOptions.this, Feedback.class);
			dialog.dismiss();
	                startActivityForResult(i, REPORT_ERROR);
		        if (getApiLevel() > 4) {
			    ActivityTransitionAnimation.slide(StudyOptions.this, ActivityTransitionAnimation.FADE);
		        }
	            }
	        });	        	
			builder.setNegativeButton(res.getString(R.string.close), null);
	        builder.setCancelable(true);
		    dialog = builder.create();
			break;

		default:
			dialog = null;
		}

		dialog.setOwnerActivity(StudyOptions.this);
		return dialog;
	}


	@Override
	protected void onPrepareDialog(int id, Dialog dialog) {
		StyledDialog ad = (StyledDialog)dialog;
		switch (id) {
		case DIALOG_SYNC_CONFLICT_RESOLUTION:
		case DIALOG_NO_SPACE_LEFT:
		case DIALOG_DECK_NOT_LOADED:
		case DIALOG_SYNC_LOG:
			ad.setMessage(mCurrentDialogMessage);
			break;

		case DIALOG_MORE:
	        // Update spinner selections from deck prior to showing the dialog.
	        Deck deck = AnkiDroidApp.deck();
	        if (deck != null) {
		        mSpinnerNewCardOrder.setSelection(deck.getNewCardOrder());
		        mSpinnerNewCardSchedule.setSelection(deck.getNewCardSpacing());
		        mSpinnerRevCardOrder.setSelection(deck.getRevCardOrder());
		        mSpinnerFailCardOption.setVisibility(View.GONE); // TODO: Not implemented yet.
		        mEditMaxFailCard.setText(String.valueOf(deck.getFailedCardMax()));
		        mEditNewPerDay.setText(String.valueOf(deck.getNewCardsPerDay()));
		        mCheckBoxPerDay.setChecked(deck.getPerDay());
		        mCheckBoxSuspendLeeches.setChecked(deck.getSuspendLeeches());	        	
	        }
			break;
			
		case DIALOG_LIMIT_SESSION:
	        // Update spinner selections from deck prior to showing the dialog.
	        Deck deck2 = AnkiDroidApp.deck();
		if (deck2 == null) {
			ad.setEnabled(false);
			return;
		}
	        long timeLimit = deck2.getSessionTimeLimit() / 60;
	        long repLimit = deck2.getSessionRepLimit();
	        mSessionLimitCheckBox.setChecked(timeLimit + repLimit > 0);
	        if (timeLimit != 0) {
	            mEditSessionTime.setText(String.valueOf(timeLimit));
	        }
	        if (repLimit != 0) {
	            mEditSessionQuestions.setText(String.valueOf(repLimit));
	        }

	        updateLimitTagText(LIMIT_NEW_ACTIVE, deck2.getVar("newActive"));
	        updateLimitTagText(LIMIT_NEW_INACTIVE, deck2.getVar("newInactive"));
	        updateLimitTagText(LIMIT_REV_ACTIVE, deck2.getVar("revActive"));
	        updateLimitTagText(LIMIT_REV_INACTIVE, deck2.getVar("revInactive"));

	        mLimitTagsCheckBox.setChecked(mLimitTagNewActiveCheckBox.isChecked() || mLimitTagNewInactiveCheckBox.isChecked()
	                || mLimitTagRevActiveCheckBox.isChecked() || mLimitTagRevInactiveCheckBox.isChecked());
	        break;

		case DIALOG_TAGS:
			Deck deck3 = AnkiDroidApp.deck();
			if (deck3 == null) {
				ad.setEnabled(false);
				return;
			}
	        if (allTags == null) {
	            allTags = deck3.allTags_();
	            Log.i(AnkiDroidApp.TAG, "all tags: " + Arrays.toString(allTags));
		        if (allTags == null) {
		        	Themes.showThemedToast(StudyOptions.this, getResources().getString(R.string.error_insufficient_memory), false);
		        	ad.setEnabled(false);
		        	return;
		        }
	        }
	        mSelectedTags.clear();
	        List<String> selectedList = Arrays.asList(Utils.parseTags(getSelectedTags(mSelectedLimitTagText)));
	        int length = allTags.length;
	        boolean[] checked = new boolean[length];
	        for (int i = 0; i < length; i++) {
	            String tag = allTags[i];
	            if (selectedList.contains(tag)) {
	                checked[i] = true;
	                mSelectedTags.add(tag);
	            }
	        }
	        ad.setMultiChoiceItems(allTags, checked,
	                new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
	                        String tag = allTags[which];
	                        if (mSelectedTags.contains(tag)) {
	                            Log.i(AnkiDroidApp.TAG, "unchecked tag: " + tag);
	                            mSelectedTags.remove(tag);
	                        } else {
	                            Log.i(AnkiDroidApp.TAG, "checked tag: " + tag);
	                            mSelectedTags.add(tag);
	                        }							
						}
	                });
	        break;

		case DIALOG_CRAM:
			if (activeCramTags == null) {
				activeCramTags = new HashSet<String>();
			} else {
		        activeCramTags.clear();				
			}
	        allCramTags = AnkiDroidApp.deck().allTags_();
	        if (allCramTags == null) {
	        	Themes.showThemedToast(StudyOptions.this, getResources().getString(R.string.error_insufficient_memory), false);
	        	ad.setEnabled(false);
	        	return;
	        }
	        ad.setMultiChoiceItems(allCramTags, new boolean[allCramTags.length], 
	        		new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface arg0, int which) {
					String tag = allCramTags[which];
					if (activeCramTags.contains(tag)) {
	                    Log.i(AnkiDroidApp.TAG, "unchecked tag: " + tag);
	                    activeCramTags.remove(tag);						
					} else {
	                    Log.i(AnkiDroidApp.TAG, "checked tag: " + tag);
	                    activeCramTags.add(tag);						
					}
				}
	        });
			break;
		case DIALOG_ANSWERING_ERROR:
			ad.getButton(Dialog.BUTTON_NEUTRAL).setEnabled(hasErrorFiles() && !PrefSettings.getSharedPrefs(StudyOptions.this).getString("reportErrorMode", Feedback.REPORT_ASK).equals(Feedback.REPORT_NEVER));
			break;
		}
	}


	private void showContentView(int which) {
        mCurrentContentView = which;
        showContentView();
    }


    private void showContentView() {

        switch (mCurrentContentView) {
            case CONTENT_NO_DECK:
                setTitle(R.string.app_name);
                if (PrefSettings.getSharedPrefs(getBaseContext()).getBoolean("firstStart", true)) {
                    mTextNoDeckTitle.setText(R.string.studyoptions_welcome_title);
                    mTextNoDeckMessage.setText(String.format(
                            getResources().getString(R.string.studyoptions_welcome_message), mPrefDeckPath));
                	showDialog(DIALOG_WELCOME);
                } else {
                    mTextNoDeckTitle.setText(R.string.studyoptions_nodeck_title);
                    mTextNoDeckMessage.setText(String.format(
                            getResources().getString(R.string.studyoptions_nodeck_message), mPrefDeckPath));
                    if (mNewVersionAlert != null) {
                    	mNewVersionAlert.show();
                    }
                }
                setContentView(mNoDeckView);
                break;
            case CONTENT_DECK_NOT_LOADED:
                setTitle(R.string.app_name);
                mTextNoDeckTitle.setText(R.string.studyoptions_deck_not_loaded_title);
                mTextNoDeckMessage.setText(R.string.studyoptions_deck_not_loaded_message);
                setContentView(mNoDeckView);
            	mCurrentDialogMessage = getResources().getString(R.string.open_deck_failed, "\'" + new File(mDeckFilename).getName() + "\'", BackupManager.BROKEN_DECKS_SUFFIX.replace("/", ""), getResources().getString(R.string.repair_deck));
    			showDialog(DIALOG_DECK_NOT_LOADED);
                break;
            case CONTENT_STUDY_OPTIONS:
            case CONTENT_SESSION_COMPLETE:
                // Enable timeboxing in case it was disabled from the previous deck
                if ((AnkiDroidApp.deck() != null) && (AnkiDroidApp.deck().name().equals("cram"))) {
                    mToggleCram.setChecked(false);
                    mToggleLimit.setEnabled(true);
                }
                if (mCurrentContentView == CONTENT_STUDY_OPTIONS) {
                    mButtonStart.setText(R.string.studyoptions_start);
                } else {
                    mButtonStart.setText(R.string.studyoptions_continue);
                }
                if (updateValuesFromDeck()) {
                    setContentView(mStudyOptionsView);
                } else {
                	showContentView(CONTENT_DECK_NOT_LOADED);
                }
                break;
            case CONTENT_CONGRATS:
                setCongratsMessage();
                updateValuesFromDeck();
                setContentView(mCongratsView);
                break;
            case CONTENT_NO_EXTERNAL_STORAGE:
                setTitle(R.string.app_name);
                setContentView(mNoExternalStorageView);
                break;
        }
    }


    private void updateLimitTagText(int which, String tags) {
        Resources res = getResources();
        tags = tags.replaceAll(",", "");
        boolean checked = !tags.equals("");
        switch (which) {
            case LIMIT_NEW_ACTIVE:
                mLimitNewActive = tags;
                mLimitTagTv2.setText(res.getString(R.string.studyoptions_limit_tags_active, tags));
                mLimitTagNewActiveCheckBox.setChecked(checked);
                return;
            case LIMIT_NEW_INACTIVE:
                mLimitNewInactive = tags;
                mLimitTagTv3.setText(res.getString(R.string.studyoptions_limit_tags_inactive, tags));
                mLimitTagNewInactiveCheckBox.setChecked(checked);
                return;
            case LIMIT_REV_ACTIVE:
                mLimitRevActive = tags;
                mLimitTagTv5.setText(res.getString(R.string.studyoptions_limit_tags_active, tags));
                mLimitTagRevActiveCheckBox.setChecked(checked);
                return;
            case LIMIT_REV_INACTIVE:
                mLimitRevInactive = tags;
                mLimitTagTv6.setText(res.getString(R.string.studyoptions_limit_tags_inactive, tags));
                mLimitTagRevInactiveCheckBox.setChecked(checked);
                return;
            default:
                return;
        }
    }


    private String getSelectedTags(int which) {
        switch (which) {
            case LIMIT_NEW_ACTIVE:
                return mLimitNewActive;
            case LIMIT_NEW_INACTIVE:
                return mLimitNewInactive;
            case LIMIT_REV_ACTIVE:
                return mLimitRevActive;
            case LIMIT_REV_INACTIVE:
                return mLimitRevInactive;
            default:
                return "";
        }
    }


    private void setCongratsMessage() {
    	Resources res = getResources();
        Deck deck = AnkiDroidApp.deck();
        if (deck != null) {
    		int failedCards = deck.getFailedDelayedCount();
            int revCards = deck.getNextDueCards(1);
            int revFailedCards = failedCards + revCards;
            int newCards = deck.getNextNewCards();
            int eta = deck.getETA(failedCards, revCards, newCards, true);
            String newCardsText = res.getQuantityString(R.plurals.studyoptions_congrats_new_cards, newCards, newCards);
            String etaText = res.getQuantityString(R.plurals.studyoptions_congrats_eta, eta, eta);
            mTextCongratsMessage.setText(res.getQuantityString(R.plurals.studyoptions_congrats_message, revFailedCards, revFailedCards, newCardsText, etaText));
        }
    }


    private void resetAndUpdateValuesFromDeck() {
        Deck deck = AnkiDroidApp.deck();
        DeckTask.waitToFinish();
        if (deck != null) {
            deck.reset();
        	updateValuesFromDeck();        	
        }
    }


    private void hideDeckInformation() {
    	setTitle(getResources().getString(R.string.app_name));
        mTextDeckName.setVisibility(View.INVISIBLE);
        mStatisticsField.setVisibility(View.INVISIBLE);
    }


    private void showDeckInformation(boolean fade) {
        mTextDeckName.setVisibility(View.VISIBLE);
        if (fade) {
            mTextDeckName.setAnimation(ViewAnimation.fade(ViewAnimation.FADE_IN, 500, 0));        	
        }
        mStatisticsField.setVisibility(View.VISIBLE);
        if (fade) {
        	mStatisticsField.setAnimation(ViewAnimation.fade(ViewAnimation.FADE_IN, 500, 0));
        }
    }


    private boolean updateValuesFromDeck() {
        Deck deck = AnkiDroidApp.deck();
        Resources res = getResources();
        if (deck != null && !mIsClosing) {
            // TODO: updateActives() from anqiqt/ui/main.py
        	try {
            	int dueCount = deck.getDueCount();
                int newTodayCount = deck.getNewCountToday();
                int cardsCount = deck.getCardCount();
                setTitle(res.getQuantityString(R.plurals.studyoptions_window_title, dueCount + newTodayCount, deck.getDeckName(), dueCount + newTodayCount, cardsCount));

                mTextDeckName.setText(deck.getDeckName());

                mTextReviewsDue.setText(String.valueOf(dueCount));
                mTextNewToday.setText(String.valueOf(newTodayCount));
                String etastr = "-";
                int eta = deck.getETA();
                if (eta != -1) {
                	etastr = Integer.toString(eta);
                }
                mTextETA.setText(etastr);
                int totalNewCount = deck.getNewCount(false);
                mTextNewTotal.setText(String.valueOf(totalNewCount));

                // Progress bars are not shown on small screens
                if (mDailyBar != null) {
                    double totalCardsCount = cardsCount;
                    mProgressTodayYes = deck.getProgress(false);
                    mProgressMatureYes = deck.getProgress(true);
                    double mature = deck.getMatureCardCount(false);
                    mProgressMature = mature / totalCardsCount;
                    double allRev = deck.getTotalRevFailedCount(false);
                    mProgressAll = allRev / totalCardsCount;
                    if (deck.isLimitedByTag()) {
                    	if (mToggleCram.isChecked()) {
                    		mGlobalLimitFrame.setVisibility(View.GONE);
                    	} else {
                            mGlobalLimitFrame.setVisibility(View.VISIBLE);
                            mature = deck.getMatureCardCount(true);
                            allRev = deck.getTotalRevFailedCount(true);
                            totalCardsCount = allRev + deck.getNewCount(true);
                            mProgressMatureLimit = mature / totalCardsCount;
                            mProgressAllLimit = allRev / totalCardsCount;
                    	}
                    } else {
                        mGlobalLimitFrame.setVisibility(View.GONE);
                    }
                    updateStatisticBars();
                }
                return true;
            } catch (SQLiteException e) {
            	Log.e(AnkiDroidApp.TAG, "updateValuesFromDeck - error on retrieving deck information: " + e);
            	return false;
            }
        }
        return true;
    }


    private void updateStatisticBars() {
        if (mStatisticBarsMax == 0) {
            mStatisticBarsMax = mBarsMax.getWidth();
            mStatisticBarsHeight = mBarsMax.getHeight();
        }
        Utils.updateProgressBars(this, mDailyBar, mProgressTodayYes, mStatisticBarsMax, mStatisticBarsHeight, true);
        Utils.updateProgressBars(this, mMatureBar,mProgressMatureYes, mStatisticBarsMax, mStatisticBarsHeight, true);
        Utils.updateProgressBars(this, mGlobalMatLimitBar, mProgressMatureLimit, mStatisticBarsMax, mStatisticBarsHeight, false);
        Utils.updateProgressBars(this, mGlobalLimitBar, (mProgressAllLimit == 1.0) ? 1.0 : mProgressAllLimit - mProgressMatureLimit, mStatisticBarsMax, mStatisticBarsHeight, false);
        Utils.updateProgressBars(this, mGlobalMatBar, mProgressMature, mStatisticBarsMax, mStatisticBarsHeight, false);
        Utils.updateProgressBars(this, mGlobalBar, (mProgressAll == 1.0) ? 1.0 : mProgressAll - mProgressMature, mStatisticBarsMax, mStatisticBarsHeight, false);
    }


    /*
* Switch schedulers
*/

    //UNUSED and never used, introduced in commit e44130abcd84928a288ddb0f3512e984ada190e7
    /*
    private void reset() {
        reset(false);
    }
    private void reset(boolean priorities) {
        if (priorities) {
            AnkiDroidApp.deck().updateAllPriorities();
        }
        AnkiDroidApp.deck().reset();
    }
    */


    /**
* Enter cramming mode.
* Currently not supporting cramming from selection of cards, as we don't have a card list view anyway.
*/
    private void onCram() {
        AnkiDroidApp.deck().setupCramScheduler(activeCramTags.toArray(new String[activeCramTags.size()]), cramOrder);
        // Timeboxing only supported using the standard scheduler
        mToggleLimit.setEnabled(false);
        resetAndUpdateValuesFromDeck();
    }

    /**
* Exit cramming mode.
*/
    private void onCramStop() {
        AnkiDroidApp.deck().setupStandardScheduler();
        mToggleLimit.setEnabled(true);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        Log.i(AnkiDroidApp.TAG, "onSaveInstanceState: " + mDeckFilename);
        // Remember current deck's filename.
        if (mDeckFilename != null) {
            outState.putString("deckFilename", mDeckFilename);
        }
        Log.i(AnkiDroidApp.TAG, "onSaveInstanceState - Ending");
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        Utils.addMenuItemInActionBar(menu, Menu.NONE, MENU_OPEN, Menu.NONE, R.string.menu_open_deck,
                R.drawable.ic_menu_manage);
        Utils.addMenuItemInActionBar(menu, Menu.NONE, MENU_SYNC, Menu.NONE, R.string.menu_sync,
                R.drawable.ic_menu_refresh);        	
        Utils.addMenuItem(menu, Menu.NONE, MENU_ADD_FACT, Menu.NONE, R.string.menu_add_card,
                R.drawable.ic_menu_add);
        Utils.addMenuItem(menu, Menu.NONE, MENU_MORE_OPTIONS, Menu.NONE, R.string.studyoptions_more,
                R.drawable.ic_menu_archive);
        Utils.addMenuItem(menu, Menu.NONE, MENU_PREFERENCES, Menu.NONE, R.string.menu_preferences,
                R.drawable.ic_menu_preferences);
        Utils.addMenuItem(menu, Menu.NONE, MENU_ROTATE, Menu.NONE, R.string.menu_rotate,
                android.R.drawable.ic_menu_always_landscape_portrait);        
        Utils.addMenuItem(menu, Menu.NONE, MENU_ZEEMOTE, Menu.NONE, R.string.menu_zeemote,
                R.drawable.ic_menu_zeemote);
        return true;
    }


    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        boolean deckChangeable = (AnkiDroidApp.deck() != null) && mSdCardAvailable && !mToggleCram.isChecked();
        menu.findItem(MENU_OPEN).setEnabled(mSdCardAvailable);
        menu.findItem(MENU_ADD_FACT).setEnabled(deckChangeable);
        menu.findItem(MENU_MORE_OPTIONS).setEnabled(deckChangeable);
		menu.findItem(MENU_SYNC).setEnabled(deckChangeable);        	
        return true;
    }


    /** Handles item selections */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_OPEN:
                openDeckPicker();
                return true;

            case MENU_SYNC:
                syncDeck(null);
                return true;

            case MENU_MORE_OPTIONS:
            	showDialog(DIALOG_MORE);
                return true;

            case MENU_PREFERENCES:
                startActivityForResult(
                        new Intent(StudyOptions.this, Preferences.class),
                        PREFERENCES_UPDATE);
                return true;

            case MENU_ADD_FACT:
            	Intent intent = new Intent(StudyOptions.this, CardEditor.class);
            	intent.putExtra(CardEditor.CARD_EDITOR_ACTION, CardEditor.ADD_CARD);
            	startActivityForResult(intent, ADD_FACT);
                if (getApiLevel() > 4) {
                    ActivityTransitionAnimation.slide(StudyOptions.this, ActivityTransitionAnimation.LEFT);
                }
                return true;

            case MENU_ROTATE:
                if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                } else {
                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);                	
                }
                return true;
            case MENU_ZEEMOTE:
            	
            	Log.d("Zeemote","Locale: "+mLocale);
            	if ((AnkiDroidApp.zeemoteController() != null)) {
            		controllerUi.showControllerMenu();
            	}
            	return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }


    private void openDeckPicker() {
//        closeOpenedDeck();
        // deckLoaded = false;
        Intent decksPicker = new Intent(StudyOptions.this, DeckPicker.class);
        mInDeckPicker = true;
        startActivityForResult(decksPicker, PICK_DECK_REQUEST);
    	if (getApiLevel() > 4) {
    		ActivityTransitionAnimation.slide(this, ActivityTransitionAnimation.RIGHT);
    	}
        // Log.i(AnkiDroidApp.TAG, "openDeckPicker - Ending");
    }


    private void finishCongrats() {
        mStudyOptionsView.setVisibility(View.INVISIBLE);
        mCongratsView.setVisibility(View.INVISIBLE);
        mCongratsView.setAnimation(ViewAnimation.fade(ViewAnimation.FADE_OUT, 500, 0));
        showContentView(CONTENT_SESSION_COMPLETE);
        mCongratsView.setVisibility(View.VISIBLE);
        mStudyOptionsView.setVisibility(View.VISIBLE);
        mStudyOptionsView.setAnimation(ViewAnimation.fade(ViewAnimation.FADE_IN, 500, 0));
    }


    private void openCardBrowser() {
        Intent cardBrowser = new Intent(StudyOptions.this, CardBrowser.class);
        startActivityForResult(cardBrowser, BROWSE_CARDS);
        if (getApiLevel() > 4) {
            ActivityTransitionAnimation.slide(StudyOptions.this, ActivityTransitionAnimation.LEFT);
        }
    }


    private void openStatistics(int period) {
        if (AnkiDroidApp.deck() != null) {
            DeckTask.launchDeckTask(DeckTask.TASK_TYPE_LOAD_STATISTICS, mLoadStatisticsHandler, new DeckTask.TaskData(this, new String[]{""}, mStatisticType, period));
        }
    }


    public void openPersonalDeckPicker() {
        if (AnkiDroidApp.isUserLoggedIn()) {
            if (AnkiDroidApp.deck() != null)// && sdCardAvailable)
            {
                AnkiDroidApp.deck().closeDeck();
                AnkiDroidApp.setDeck(null);
                mCompat.invalidateOptionsMenu(this);
            }
            startActivityForResult(
                    new Intent(StudyOptions.this, PersonalDeckPicker.class), DOWNLOAD_PERSONAL_DECK);
        	if (getApiLevel() > 4) {
        		ActivityTransitionAnimation.slide(this, ActivityTransitionAnimation.RIGHT);
        	}
        } else {
        	showDialog(DIALOG_USER_NOT_LOGGED_IN);
        }
    }


    public void openSharedDeckPicker() {
        if (AnkiDroidApp.deck() != null)// && sdCardAvailable)
        {
            AnkiDroidApp.deck().closeDeck();
            AnkiDroidApp.setDeck(null);
            mCompat.invalidateOptionsMenu(this);
        }
        // deckLoaded = false;
        startActivityForResult(new Intent(StudyOptions.this, SharedDeckPicker.class), DOWNLOAD_SHARED_DECK);
    	if (getApiLevel() > 4) {
    		ActivityTransitionAnimation.slide(this, ActivityTransitionAnimation.RIGHT);
    	}
    }


    private void loadSampleDeck() {
        // If decks directory does not exist, create it.
        File decksDirectory = new File(mPrefDeckPath);
        AnkiDroidApp.createDecksDirectoryIfMissing(decksDirectory);

        File sampleDeckFile = new File(mPrefDeckPath, SAMPLE_DECK_NAME);
        if (!sampleDeckFile.exists()) {
            mDeckFilename = sampleDeckFile.getAbsolutePath();
            savePreferences("deckFilename");
        	DeckTask.launchDeckTask(DeckTask.TASK_TYPE_LOAD_TUTORIAL, mLoadDeckHandler, new DeckTask.TaskData(sampleDeckFile.getAbsolutePath()));
        } else {
        	Intent deckLoadIntent = new Intent();
        	deckLoadIntent.putExtra(OPT_DB, sampleDeckFile.getAbsolutePath());
        	onActivityResult(PICK_DECK_REQUEST, RESULT_OK, deckLoadIntent);
    	}
    }

    private void syncDeckWithPrompt() {
        if (AnkiDroidApp.isUserLoggedIn()) {
            Deck deck = AnkiDroidApp.deck();
            if (deck != null) {
                // Close existing sync progress dialog
                if (mProgressDialog != null && mProgressDialog.isShowing()) {
                    mProgressDialog.dismiss();
                }
                // Prompt user for conflict resolution
                mCurrentDialogMessage = String.format(getResources().getString(R.string.sync_conflict_message), deck.getDeckName());
                showDialog(DIALOG_SYNC_CONFLICT_RESOLUTION);
            }
        } else {
        	showDialog(DIALOG_USER_NOT_LOGGED_IN);
        }
    }


    private void syncDeck(String conflictResolution) {
        SharedPreferences preferences = PrefSettings.getSharedPrefs(getBaseContext());

        String username = preferences.getString("username", "");
        String password = preferences.getString("password", "");

        if (AnkiDroidApp.isUserLoggedIn()) {
            Deck deck = AnkiDroidApp.deck();
            if (deck != null) {
                Log.i(AnkiDroidApp.TAG, "Synchronizing deck " + mDeckFilename + ", conflict resolution: " + conflictResolution);
                Log.i(AnkiDroidApp.TAG, String.format(Utils.ENGLISH_LOCALE, "Before syncing - mod: %f, last sync: %f", deck.getModified(), deck.getLastSync()));
                Connection.syncDeck(mSyncListener, new Connection.Payload(new Object[] { username, password, deck, conflictResolution, true }));
            }
        } else {
        	showDialog(DIALOG_USER_NOT_LOGGED_IN);
        }
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);

        if (resultCode == CONTENT_NO_EXTERNAL_STORAGE) {
            showContentView(CONTENT_NO_EXTERNAL_STORAGE);
        } else if (requestCode == PICK_DECK_REQUEST || requestCode == DOWNLOAD_PERSONAL_DECK
                || requestCode == DOWNLOAD_SHARED_DECK) {
        	if (requestCode == PICK_DECK_REQUEST && resultCode == RESULT_CLOSE) {
        		closeStudyOptions(false);
        	} else if (requestCode == PICK_DECK_REQUEST && resultCode == RESULT_RESTART) {
        		restartApp();
        	}
            // Clean the previous card before showing the first of the new loaded deck (so the transition is not so
            // abrupt)
            // updateCard("");
            // hideSdError();
            // hideDeckErrors();
            mInDeckPicker = false;

            if (requestCode == PICK_DECK_REQUEST && resultCode == RESULT_OK) {
                showContentView(CONTENT_STUDY_OPTIONS);
            } else if ((requestCode == DOWNLOAD_SHARED_DECK || requestCode == DOWNLOAD_PERSONAL_DECK) && resultCode == RESULT_OK) {
            	openDeckPicker();
            	return;
            }
            if (resultCode != RESULT_OK) {
                Log.e(AnkiDroidApp.TAG, "onActivityResult - Deck browser returned with error");
                // Make sure we open the database again in onResume() if user pressed "back"
                // deckSelected = false;
                boolean fileNotDeleted = mDeckFilename != null && new File(mDeckFilename).exists();
            	if (!fileNotDeleted) {
                    AnkiDroidApp.setDeck(null);
                    mCompat.invalidateOptionsMenu(this);
                    showContentView(CONTENT_NO_DECK);
            	} else {
                	showContentView(CONTENT_STUDY_OPTIONS);
                    if (AnkiDroidApp.deck() == null || !AnkiDroidApp.deck().getDeckPath().equals(mDeckFilename)) {
                    	if (resultCode != RESULT_DONT_RELOAD_DECK) {
                            displayProgressDialogAndLoadDeck();
                        }
                    }
            	}
                return;
            }

            if (intent == null) {
                Log.e(AnkiDroidApp.TAG, "onActivityResult - Deck browser returned null intent");
                // Make sure we open the database again in onResume()
                // deckSelected = false;
                displayProgressDialogAndLoadDeck();
                return;
            }
            // A deck was picked. Save it in preferences and use it.
            Log.i(AnkiDroidApp.TAG, "onActivityResult = OK");
            mDeckFilename = intent.getExtras().getString(OPT_DB);
            savePreferences("deckFilename");
            if (mDeckFilename == null || !new File(mDeckFilename).exists()) {
            	showContentView(CONTENT_NO_DECK);
            }
            // Log.i(AnkiDroidApp.TAG, "onActivityResult - deckSelected = " + deckSelected);
            if (AnkiDroidApp.deck() == null || !AnkiDroidApp.deck().getDeckPath().equals(mDeckFilename)) {
                boolean updateAllCards = (requestCode == DOWNLOAD_SHARED_DECK);
                displayProgressDialogAndLoadDeck(updateAllCards);	
            }
        } else if (requestCode == PREFERENCES_UPDATE) {
            restorePreferences();
            BackupManager.initBackup();
            showContentView();
            if (resultCode == RESULT_RESTART) {
            	restartApp();
            } else if (resultCode == RESULT_RELOAD_DECK) {
            	displayProgressDialogAndLoadDeck();
            }
            // If there is no deck loaded the controls have not to be shown
            // if(deckLoaded && cardsToReview)
            // {
            // showOrHideControls();
            // showOrHideAnswerField();
            // }
        } else if (requestCode == REQUEST_REVIEW) {
            Log.i(AnkiDroidApp.TAG, "Result code = " + resultCode);
            // Return to standard scheduler
    		mInReviewer = false;
            switch (resultCode) {
                case Reviewer.RESULT_SESSION_COMPLETED:
                	showContentView(CONTENT_SESSION_COMPLETE);
                    break;
                case Reviewer.RESULT_NO_MORE_CARDS:
                	showContentView(CONTENT_CONGRATS);
                    break;
                case Reviewer.RESULT_ANSWERING_ERROR:
                	showContentView(CONTENT_STUDY_OPTIONS);
                	showDialog(DIALOG_ANSWERING_ERROR);
                    break;
                default:
                	showContentView(CONTENT_STUDY_OPTIONS);
                    break;
            }
        } else if (requestCode == ADD_FACT && resultCode != RESULT_CANCELED) {
        	resetAndUpdateValuesFromDeck();
        } else if (requestCode == BROWSE_CARDS && resultCode == RESULT_OK) {
        	resetAndUpdateValuesFromDeck();
        } else if (requestCode == LOG_IN && resultCode == RESULT_OK) {
        	syncDeck(null);
        } else if (requestCode == STATISTICS && mCurrentContentView == CONTENT_CONGRATS) {
        	showContentView(CONTENT_STUDY_OPTIONS);
        } else if (requestCode == REPORT_ERROR) {
  	      	if (mShowRepairDialog) {
  	      		showDialog(DIALOG_ANSWERING_ERROR);
  	      		mShowRepairDialog = false;
  	      	} else if ((showDeckPickerOnStartup() || getIntent().getBooleanExtra("startDeckpicker", false))) {
        		openDeckPicker();
        	} else {
        		// workaround for dialog problems when returning from error reporter
        		try {
      	      		if (mWelcomeAlert != null && mWelcomeAlert.isShowing()) {
      	      			mWelcomeAlert.dismiss();
          				mWelcomeAlert.show();
      	      		} else if (mNewVersionAlert != null && mNewVersionAlert.isShowing()) {
      	      			mNewVersionAlert.dismiss();
      	      			mNewVersionAlert.show();
      	      		}
      	      	} catch (IllegalArgumentException e) {
      	      		Log.e(AnkiDroidApp.TAG, "Error on dismissing and showing dialog: " + e);
      	      	}
        	}
        }
    }

    private boolean showDeckPickerOnStartup() {
    	switch (mStartupMode) {
    	case SUM_STUDY_OPTIONS:
            return false;

    	case SUM_DECKPICKER:
    		return true;

    	case SUM_DECKPICKER_ON_FIRST_START:
    		return Utils.isNewDay(mLastTimeOpened);

    	default:
    		return false;
    	}
    }

    private void savePreferences(String str) {
        SharedPreferences preferences = PrefSettings.getSharedPrefs(getBaseContext());
        Editor editor = preferences.edit();
        if (str.equals("deckFilename")) {
            editor.putString("deckFilename", mDeckFilename);
        } else if (str.equals("invertedColors")) {
            editor.putBoolean("invertedColors", mInvertedColors);
        } else if (str.equals("swapqa")) {
            editor.putBoolean("swapqa", mSwap);
        }
        editor.commit();
    }


    private SharedPreferences restorePreferences() {
        SharedPreferences preferences = PrefSettings.getSharedPrefs(getBaseContext());
        mPrefDeckPath = preferences.getString("deckPath", AnkiDroidApp.getStorageDirectory());
        mPrefStudyOptions = preferences.getBoolean("study_options", true);
        mStartupMode = Integer.parseInt(preferences.getString("startup_mode",
                Integer.toString(SUM_DECKPICKER_ON_FIRST_START)));
        mSwipeEnabled = preferences.getBoolean("swipe", false);

        mLastTimeOpened = preferences.getLong("lastTimeOpened", 0);
        BroadcastMessages.init(this, mLastTimeOpened);
       	preferences.edit().putLong("lastTimeOpened", System.currentTimeMillis()).commit();

        if (!preferences.getString("lastVersion", "").equals(getVersion())) {
           	mNewVersionAlert = Themes.htmlOkDialog(this, getResources().getString(R.string.new_version_title) + " " + getVersion(), getVersionMessage(), new DialogInterface.OnClickListener() {
    			@Override
    			public void onClick(DialogInterface dialog, int which) {
		        	PrefSettings.getSharedPrefs(StudyOptions.this.getBaseContext()).edit().putString("lastVersion", getVersion()).commit();
		        	mNewVersionAlert = null;
			        BroadcastMessages.checkForNewMessages(StudyOptions.this);
    			}
            }, new DialogInterface.OnCancelListener() {
    			@Override
    			public void onCancel(DialogInterface dialog) {
		        	mNewVersionAlert = null;
			        BroadcastMessages.checkForNewMessages(StudyOptions.this);
    			}
            }, true);
           	AnkiDroidApp.createNoMediaFileIfMissing(new File(mPrefDeckPath));
        } else if (!preferences.getBoolean("firstStart", true)) {
        	BroadcastMessages.checkForNewMessages(this);        		
        }

        // Convert dip to pixel, code in parts from http://code.google.com/p/k9mail/
        final float gestureScale = getResources().getDisplayMetrics().density;
        int sensibility = preferences.getInt("swipeSensibility", 100);
        if (sensibility != 100) {
            float sens = (200 - sensibility) / 100.0f;
            sSwipeMinDistance = (int)(SWIPE_MIN_DISTANCE_DIP * sens * gestureScale + 0.5f);
            sSwipeThresholdVelocity = (int)(SWIPE_THRESHOLD_VELOCITY_DIP * sens * gestureScale + 0.5f);
            sSwipeMaxOffPath = (int)(SWIPE_MAX_OFF_PATH_DIP * Math.sqrt(sens) * gestureScale + 0.5f);
        } else {
            sSwipeMinDistance = (int)(SWIPE_MIN_DISTANCE_DIP * gestureScale + 0.5f);
            sSwipeThresholdVelocity = (int)(SWIPE_THRESHOLD_VELOCITY_DIP * gestureScale + 0.5f);
            sSwipeMaxOffPath = (int)(SWIPE_MAX_OFF_PATH_DIP * gestureScale + 0.5f);
        }

        mInvertedColors = preferences.getBoolean("invertedColors", false);
        mSwap = preferences.getBoolean("swapqa", false);
        mLocale = preferences.getString("language", "");
        mZeemoteEnabled = preferences.getBoolean("zeemote", false);
       	setLanguage(mLocale);


        return preferences;
    }


    private String getVersion() {
    	String versionNumber;
    	try {
            String pkg = this.getPackageName();
            versionNumber = this.getPackageManager().getPackageInfo(pkg, 0).versionName;
        } catch (NameNotFoundException e) {
            versionNumber = "?";
        }
        return versionNumber;
    }


    private String getVersionMessage() {
    	Resources res = getResources();
        StringBuilder builder = new StringBuilder();
        builder.append(res.getString(R.string.new_version_message));
        builder.append("<ul>");
        String[] features = res.getStringArray(R.array.new_version_features);
        for (int i = 0; i < features.length; i++) {
        	builder.append("<li>");
        	builder.append(features[i]);
        	builder.append("</li>");
        }
        builder.append("</ul>");
    	return builder.toString();
    }


    private void setLanguage(String language) {
    	Locale locale;
    	if (language.equals("")) {
        	return;
    	} else {
        	locale = new Locale(language);
    	}
        Configuration config = new Configuration();
        config.locale = locale;
        this.getResources().updateConfiguration(config, this.getResources().getDisplayMetrics());
    }


    private void displayProgressDialogAndLoadDeck() {
        displayProgressDialogAndLoadDeck(false);
    }


    private void displayProgressDialogAndLoadDeck(boolean updateAllCards) {
        Log.i(AnkiDroidApp.TAG, "displayProgressDialogAndLoadDeck - Loading deck " + mDeckFilename);

        // Don't open database again in onResume() until we know for sure this attempt to load the deck is finished
        // deckSelected = true;

        // if(isSdCardMounted())
        // {
        if (mDeckFilename != null && new File(mDeckFilename).exists()) {
            // showControls(false);

        	mToggleCram.setChecked(false);
        	mToggleLimit.setEnabled(true);

            if (updateAllCards) {
                DeckTask.launchDeckTask(DeckTask.TASK_TYPE_LOAD_DECK_AND_UPDATE_CARDS, mLoadDeckHandler,
                        new DeckTask.TaskData(AnkiDroidApp.deck(), mDeckFilename));
            } else {
                DeckTask.launchDeckTask(DeckTask.TASK_TYPE_LOAD_DECK, mLoadDeckHandler, new DeckTask.TaskData(
                		AnkiDroidApp.deck(), mDeckFilename));
            }
        } else {
            if (mDeckFilename == null) {
                Log.i(AnkiDroidApp.TAG, "displayProgressDialogAndLoadDeck - SD card unmounted.");
            } else if (!new File(mDeckFilename).exists()) {
                Log.i(AnkiDroidApp.TAG, "displayProgressDialogAndLoadDeck - The deck " + mDeckFilename + " does not exist.");
            }

            // Show message informing that no deck has been loaded
            // displayDeckNotLoaded();
        }
        // } else
        // {
        // Log.i(AnkiDroidApp.TAG, "displayProgressDialogAndLoadDeck - SD card unmounted.");
        // deckSelected = false;
        // Log.i(AnkiDroidApp.TAG, "displayProgressDialogAndLoadDeck - deckSelected = " + deckSelected);
        // displaySdError();
        // }
    }


    DeckTask.TaskListener mRepairDeckHandler = new DeckTask.TaskListener() {

    	@Override
        public void onPreExecute() {
            mProgressDialog = ProgressDialog.show(StudyOptions.this, "", getResources()
                    .getString(R.string.backup_repair_deck_progress), true);
        }


        @Override
        public void onPostExecute(DeckTask.TaskData result) {
        	if (result.getBoolean()) {
        		displayProgressDialogAndLoadDeck();
        	} else {
        		Themes.showThemedToast(StudyOptions.this, getResources().getString(R.string.deck_repair_error), true);
        	}
        	if (mProgressDialog != null && mProgressDialog.isShowing()) {
        		mProgressDialog.dismiss();
        	}
        }
 
		@Override
		public void onProgressUpdate(TaskData... values) {
		}

    };


    DeckTask.TaskListener mRestoreDeckHandler = new DeckTask.TaskListener() {

    	@Override
        public void onPreExecute() {
            mProgressDialog = ProgressDialog.show(StudyOptions.this, "", getResources()
                    .getString(R.string.backup_restore_deck), true);
        }


        @Override
        public void onPostExecute(DeckTask.TaskData result) {
			switch (result.getInt()) {
    		case BackupManager.RETURN_DECK_RESTORED:
    			displayProgressDialogAndLoadDeck();
    			return;    			
    		case BackupManager.RETURN_ERROR:
    			showDialog(DIALOG_DECK_NOT_LOADED);
        		Themes.showThemedToast(StudyOptions.this, getResources().getString(R.string.backup_restore_error), true);
    			break;
    		case BackupManager.RETURN_NOT_ENOUGH_SPACE:
    			mCurrentDialogMessage = getResources().getString(R.string.backup_deck_no_space_left);
    			showDialog(DIALOG_NO_SPACE_LEFT);
    			break;
    		}
        	if (mProgressDialog != null && mProgressDialog.isShowing()) {
        		mProgressDialog.dismiss();
        	}
        }

		@Override
		public void onProgressUpdate(TaskData... values) {
		}

    };


    DeckTask.TaskListener mLoadDeckHandler = new DeckTask.TaskListener() {

        @Override
        public void onPreExecute() {
            // if(updateDialog == null || !updateDialog.isShowing())
            // {
        	if (mProgressDialog != null && mProgressDialog.isShowing()) {
        		mProgressDialog.setMessage(getResources().getString(R.string.loading_deck));
        	} else {
                mProgressDialog = ProgressDialog.show(StudyOptions.this, "", getResources()
                        .getString(R.string.loading_deck), true, true, new OnCancelListener() {

    						@Override
    						public void onCancel(DialogInterface dialog) {
    							mNewVersionAlert = null;
    				            closeOpenedDeck();
    				            MetaDB.closeDB();
    				            finish();
    						}
                });
        	}
	    	hideDeckInformation();
            // }
        }


        @Override
        public void onPostExecute(DeckTask.TaskData result) {

            // Close the previously opened deck.
            // if (AnkidroidApp.deck() != null)
            // {
            // AnkidroidApp.deck().closeDeck();
            // AnkidroidApp.setDeck(null);
            // mCompat.invalidateOptionsMenu(StudyOptions.this);
            // }
            allTags = null;

            switch (result.getInt()) {
                case DeckTask.DECK_LOADED:
                    // Set the deck in the application instance, so other activities
                    // can access the loaded deck.
                    AnkiDroidApp.setDeck(result.getDeck());
                    mCompat.invalidateOptionsMenu(StudyOptions.this);

                    showContentView(CONTENT_STUDY_OPTIONS);
                    showDeckInformation(true);

                    if (!mPrefStudyOptions) {
                		mInReviewer = true;
                        startActivityForResult(new Intent(StudyOptions.this, Reviewer.class), REQUEST_REVIEW);
                    }

                    break;

                case DeckTask.DECK_NOT_LOADED:
                	BackupManager.restoreDeckIfMissing(mDeckFilename);
                	showContentView(CONTENT_DECK_NOT_LOADED);
                    break;

                case DeckTask.DECK_EMPTY:
                    // displayNoCardsInDeck();
                    break;
                case DeckTask.TUTORIAL_NOT_CREATED:
                	Themes.showThemedToast(StudyOptions.this, getResources().getString(R.string.tutorial_loading_error), false);
                	break;
            }

            // This verification would not be necessary if onConfigurationChanged it's executed correctly (which seems
            // that emulator does not do)
            if (mProgressDialog.isShowing()) {
                try {
                    mProgressDialog.dismiss();
                } catch (Exception e) {
                    Log.e(AnkiDroidApp.TAG, "onPostExecute - Dialog dismiss Exception = " + e.getMessage());
                }
                if (mNewVersionAlert != null) {
                	try {
                        mNewVersionAlert.show();
                    } catch (Exception e) {
                        Log.e(AnkiDroidApp.TAG, "onPostExecute - Show new version dialog exception = " + e.getMessage());
                	}
                }
            }
            Deck deck = AnkiDroidApp.deck();
            if (deck != null) {
                mToggleLimit.setChecked(deck.isLimitedByTag() || deck.getSessionRepLimit() + deck.getSessionTimeLimit() > 0);
            }
        }


        @Override
        public void onProgressUpdate(DeckTask.TaskData... values) {
        	Resources res = getResources();
        	String message = values[0].getString();
        	if (message == null) {
				switch (values[0].getInt()) {
        		case BackupManager.RETURN_BACKUP_CREATED:
            		Themes.showThemedToast(StudyOptions.this, res.getString(R.string.backup_deck_success), true);
        		case BackupManager.RETURN_TODAY_ALREADY_BACKUP_DONE:
        		case BackupManager.RETURN_DECK_NOT_CHANGED:
            		if (mProgressDialog.isShowing()) {
                		mProgressDialog.setMessage(res.getString(R.string.loading_deck));
                	}
        			break;
        		case BackupManager.RETURN_ERROR:
        			showDialog(DIALOG_BACKUP_ERROR);
        			break;
        		case BackupManager.RETURN_NOT_ENOUGH_SPACE:
        			mCurrentDialogMessage = getResources().getString(R.string.backup_deck_no_space_left);
        			showDialog(DIALOG_BACKUP_NO_SPACE_LEFT);
        			break;
        		case BackupManager.RETURN_LOW_SYSTEM_SPACE:
        			if (!PrefSettings.getSharedPrefs(getBaseContext()).getBoolean("dontShowLowMemory", false)) {
            			mCurrentDialogMessage = getResources().getString(R.string.sd_space_warning, MIN_FREE_SPACE);
            			showDialog(DIALOG_NO_SPACE_LEFT);        				
        			}
        			break;
        		}
        	} else {
        		mProgressDialog.setMessage(message);
        	}
        }
    };


    Connection.TaskListener mSyncListener = new Connection.TaskListener() {

        @Override
        public void onDisconnected() {
        	showDialog(DIALOG_NO_CONNECTION);
        }


        @Override
        public void onPostExecute(Payload data) {
            Log.i(AnkiDroidApp.TAG, "onPostExecute");
            if (mProgressDialog != null) {
                mProgressDialog.dismiss();
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
            }
            if (data.success) {
            	mCurrentDialogMessage = ((HashMap<String, String>) data.result).get("message");
                AnkiDroidApp.deck().updateCutoff();
                resetAndUpdateValuesFromDeck();
                showDialog(DIALOG_SYNC_LOG);
            } else {
                if (data.returnType == AnkiDroidProxy.SYNC_CONFLICT_RESOLUTION) {
                    // Need to ask user for conflict resolution direction and re-run sync
                    syncDeckWithPrompt();
                } else {
                    String errorMessage = ((HashMap<String, String>) data.result).get("message");
                    if ((errorMessage != null) && (errorMessage.length() > 0)) {
                    	mCurrentDialogMessage = errorMessage;
                    }
                    showDialog(DIALOG_CONNECTION_ERROR);
                }
            }
        }


        @Override
        public void onPreExecute() {
            // Pass
        }


        @Override
        public void onProgressUpdate(Object... values) {
            if (values[0] instanceof Boolean) {
                // This is the part Download missing media of syncing
                Resources res = getResources();
                int total = ((Integer)values[1]).intValue();
                int done = ((Integer)values[2]).intValue();
                values[0] = ((String)values[3]);
                values[1] = res.getString(R.string.sync_downloading_media, done, total);
            }
            if (mProgressDialog == null || !mProgressDialog.isShowing()) {
                mProgressDialog = ProgressDialog.show(StudyOptions.this, (String) values[0], (String) values[1]);
                // Forbid orientation changes as long as progress dialog is shown
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_NOSENSOR);
            } else {
                mProgressDialog.setTitle((String) values[0]);
                mProgressDialog.setMessage((String) values[1]);
            }
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
		    	if (mStatisticType == Statistics.TYPE_DECK_SUMMARY) {
		    		Statistics.showDeckSummary(StudyOptions.this);
		    	} else {
	            	Intent intent = new Intent(StudyOptions.this, com.ichi2.charts.ChartBuilder.class);
			    	startActivityForResult(intent, STATISTICS);
			        if (getApiLevel() > 4) {
			            ActivityTransitionAnimation.slide(StudyOptions.this, ActivityTransitionAnimation.DOWN);
			        }
		    	}
			}
		}

		@Override
		public void onPreExecute() {
            mProgressDialog = ProgressDialog.show(StudyOptions.this, "", getResources()
                    .getString(R.string.calculating_statistics), true);
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
    				if (e1.getX() - e2.getX() > sSwipeMinDistance && Math.abs(velocityX) > sSwipeThresholdVelocity && Math.abs(e1.getY() - e2.getY()) < sSwipeMaxOffPath) {
                        // left
                    	openReviewer();
                    } else if (e2.getX() - e1.getX() > sSwipeMinDistance && Math.abs(velocityX) > sSwipeThresholdVelocity && Math.abs(e1.getY() - e2.getY()) < sSwipeMaxOffPath) {
                        // right
    					openDeckPicker();
                    } else if (e2.getY() - e1.getY() > sSwipeMinDistance && Math.abs(velocityY) > sSwipeThresholdVelocity && Math.abs(e1.getX() - e2.getX()) < sSwipeMaxOffPath) {
                        // down
                    	mStatisticType = 0;
                    	openStatistics(0);
                    }

                }
                catch (Exception e) {
                	Log.e(AnkiDroidApp.TAG, "onFling Exception = " + e.getMessage());
                }
            }
            return false;
        }
    }
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (gestureDetector.onTouchEvent(event))
	        return true;
	    else
	    	return false;
    }


    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        String deck = intent.getStringExtra(EXTRA_DECK);
        Log.d(AnkiDroidApp.TAG, "StudyOptions.onNewIntent: " + intent + ", deck=" + deck);
        if (deck != null && !deck.equals(mDeckFilename)) {
            mDeckFilename = deck;
            loadPreviousDeck();
        }
    }


    /**
     * Creates an intent to load a deck given the full pathname of it.
     * <p>
     * The constructed intent is equivalent (modulo the extras) to the open used by the launcher
     * shortcut, which means it will not open a new study options window but bring the existing one
     * to the front.
     */
    public static Intent getLoadDeckIntent(Context context, String deckPath) {
        Intent loadDeckIntent = new Intent(context, StudyOptions.class);
        loadDeckIntent.setAction(Intent.ACTION_MAIN);
        loadDeckIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        loadDeckIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        loadDeckIntent.putExtra(StudyOptions.EXTRA_DECK, deckPath);
        return loadDeckIntent;
    }


	@Override
	public void buttonPressed(ButtonEvent arg0) {
		// TODO Auto-generated method stub
		
	}


	@Override
	public void buttonReleased(ButtonEvent arg0) {
		Log.d("Zeemote","Button released, id: "+arg0.getButtonID());
		Message msg = Message.obtain();
		msg.what = MSG_ZEEMOTE_BUTTON_A + arg0.getButtonID(); //Button A = 0, Button B = 1...
		if ((msg.what >= MSG_ZEEMOTE_BUTTON_A) && (msg.what <= MSG_ZEEMOTE_BUTTON_D)) { //make sure messages from future buttons don't get throug
			this.ZeemoteHandler.sendMessage(msg);
		}
		if (arg0.getButtonID()==-1)
		{
			msg.what = MSG_ZEEMOTE_BUTTON_D+arg0.getButtonGameAction();
			if ((msg.what >= MSG_ZEEMOTE_STICK_UP) && (msg.what <= MSG_ZEEMOTE_STICK_RIGHT)) { //make sure messages from future buttons don't get throug
				this.ZeemoteHandler.sendMessage(msg);
			}
		}
	}
}
