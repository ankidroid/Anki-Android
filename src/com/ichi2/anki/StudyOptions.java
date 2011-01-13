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
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CheckedTextView;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.ToggleButton;

import com.ichi2.async.Connection;
import com.ichi2.async.Connection.Payload;
import com.tomgibara.android.veecheck.util.PrefSettings;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashSet;

public class StudyOptions extends Activity {
    /**
* Default database.
*/
    public static final String OPT_DB = "com.ichi2.anki.deckFilename";

    /**
* Filename of the sample deck to load
*/
    private static final String SAMPLE_DECK_NAME = "country-capitals.anki";

    /**
* Menus
*/
    private static final int MENU_OPEN = 1;
    private static final int SUBMENU_DOWNLOAD = 2;
    private static final int MENU_DOWNLOAD_PERSONAL_DECK = 21;
    private static final int MENU_DOWNLOAD_SHARED_DECK = 22;
    private static final int MENU_SYNC = 3;
    private static final int MENU_MY_ACCOUNT = 4;
    private static final int MENU_PREFERENCES = 5;
    private static final int MENU_ADD_FACT = 6;
    private static final int MENU_ABOUT = 7;
    private static final int MENU_MORE_OPTIONS = 8;
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
    private static final int SUM_STUDY_OPTIONS = 0;
    private static final int SUM_DECKPICKER = 1;
    private static final int SUM_DECKPICKER_ON_FIRST_START = 2;

    
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
    private String mDeckFilename;
    private int mStartupMode;
    
    private int mCurrentContentView;
    
    private int mNewDayStartsAt = 4;
    private long mLastTimeOpened;
    boolean mSyncEnabled = false;
    
    /**
* Alerts to inform the user about different situations
*/
    private ProgressDialog mProgressDialog;
    private AlertDialog mNoConnectionAlert;
    private AlertDialog mUserNotLoggedInAlert;
    private AlertDialog mConnectionErrorAlert;

    /*
* Cram related
*/
    private AlertDialog mCramTagsDialog;
    private String allCramTags[];
    private HashSet<String> activeCramTags;
    private String cramOrder;
    private static final String[] cramOrderList = {"type, modified", "created", "random()"};

    /**
* UI elements for "Study Options" view
*/
    private View mStudyOptionsView;
    private Button mButtonStart;
    private ToggleButton mToggleCram;
    private TextView mTextTitle;
    private TextView mTextDeckName;
    private TextView mTextReviewsDue;
    private TextView mTextNewToday;
    private TextView mTextNewTotal;
    private EditText mEditNewPerDay;
    private EditText mEditSessionTime;
    private EditText mEditSessionQuestions;

    /**
* UI elements for "More Options" dialog
*/
    private AlertDialog mDialogMoreOptions;
    private Spinner mSpinnerNewCardOrder;
    private Spinner mSpinnerNewCardSchedule;
    private Spinner mSpinnerRevCardOrder;
    private Spinner mSpinnerFailCardOption;
    
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
    private Button mButtonCongratsOpenOtherDeck;
    private Button mButtonCongratsFinish;

    /**
* UI elements for "No External Storage Available" view
*/
    private View mNoExternalStorageView;

    /**
* UI elements for "Cram Tags" view
*/
    private ListView mCramTagsListView;
    private Spinner mSpinnerCramOrder;
    
    /**
* Callbacks for UI events
*/
    private View.OnClickListener mButtonClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            Intent reviewer = new Intent(StudyOptions.this, Reviewer.class);
            switch (v.getId()) {
                case R.id.studyoptions_start:
                    // finish();
                    reviewer.putExtra("deckFilename", mDeckFilename);
                    startActivityForResult(reviewer, REQUEST_REVIEW);
                    return;
                case R.id.studyoptions_cram:
                    if (mToggleCram.isChecked()) {
                        activeCramTags.clear();
                        cramOrder = cramOrderList[0];
                        recreateCramTagsDialog();
                        mCramTagsDialog.show();
                    } else {
                        onCramStop();
                        updateValuesFromDeck();
                    }
                    return;
                case R.id.studyoptions_congrats_learnmore:
                    onLearnMore();
                    reviewer.putExtra("deckFilename", mDeckFilename);
                    startActivityForResult(reviewer, REQUEST_REVIEW);
                    return;
                case R.id.studyoptions_congrats_reviewearly:
                    onReviewEarly();
                    reviewer.putExtra("deckFilename", mDeckFilename);
                    startActivityForResult(reviewer, REQUEST_REVIEW);
                    return;
                case R.id.studyoptions_congrats_open_other_deck:
                    openDeckPicker();
                    return;
                case R.id.studyoptions_congrats_finish:
                    showContentView(CONTENT_SESSION_COMPLETE);
                    return;
                case R.id.studyoptions_load_sample_deck:
                    loadSampleDeck();
                    return;
                case R.id.studyoptions_load_other_deck:
                    openDeckPicker();
                    return;
                case R.id.studyoptions_download_deck:
                	showDownloadSelector();
                	return;
                default:
                    return;
            }
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
            boolean perDayChanged = deck.getPerDay() ^ mCheckBoxPerDay.isChecked(); 
          	deck.setPerDay(mCheckBoxPerDay.isChecked());
          	deck.setSuspendLeeches(mCheckBoxSuspendLeeches.isChecked());
            // TODO: Update number of due cards after change of per day scheduling 
            dialog.dismiss();
            if (perDayChanged){
            	reloadDeck();
            }
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Log.i(AnkiDroidApp.TAG, "StudyOptions Activity");

        if (hasErrorFiles()) {
            Intent i = new Intent(this, ErrorReporter.class);
            startActivityForResult(i, REPORT_ERROR);
        }

        SharedPreferences preferences = restorePreferences();
        registerExternalStorageListener();

        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);

        activeCramTags = new HashSet<String>();

        initAllContentViews();
        initAllDialogs();
        
        if ((AnkiDroidApp.deck() != null) && (AnkiDroidApp.deck().hasFinishScheduler())) {
            AnkiDroidApp.deck().finishScheduler();
        }

        Intent intent = getIntent();
        if ("android.intent.action.VIEW".equalsIgnoreCase(intent.getAction()) && intent.getDataString() != null) {
            mDeckFilename = Uri.parse(intent.getDataString()).getPath();
            Log.i(AnkiDroidApp.TAG, "onCreate - deckFilename from intent: " + mDeckFilename);
        } else if (savedInstanceState != null) {
            // Use the same deck as last time Ankidroid was used.
            mDeckFilename = savedInstanceState.getString("deckFilename");
            Log.i(AnkiDroidApp.TAG, "onCreate - deckFilename from savedInstanceState: " + mDeckFilename);
        } else {
            Log.i(AnkiDroidApp.TAG, "onCreate - " + preferences.getAll().toString());
            mDeckFilename = preferences.getString("deckFilename", null);
            Log.i(AnkiDroidApp.TAG, "onCreate - deckFilename from preferences: " + mDeckFilename);
        }
        if (!mSdCardAvailable) {
            showContentView(CONTENT_NO_EXTERNAL_STORAGE);
        } else {
            if (mDeckFilename == null || !new File(mDeckFilename).exists()) {
                showContentView(CONTENT_NO_DECK);
            } else {
            	if (showDeckPickerOnStartup()) {
            		openDeckPicker();
            	} else {
            		// Load previous deck.
            		loadPreviousDeck();
            	}
            }
        }
    }

    
//    @Override 
//    public void onConfigurationChanged(Configuration newConfig){
//    	super.onConfigurationChanged(newConfig); 
//    	
//    	// TODO: Change layout without reloading deck
//    	//setContentView(R.layout.studyoptions); 
//    	//initAllContentViews();
//    }


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
        if (mUnmountReceiver != null) {
            unregisterReceiver(mUnmountReceiver);
        }
        savePreferences("lastOpened");
    }


    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event)  {
        if (keyCode == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0) {
            Log.i(AnkiDroidApp.TAG, "StudyOptions - onBackPressed()");
            closeOpenedDeck();
        }

        return super.onKeyDown(keyCode, event);
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


    private void showDownloadSelector(){
        Resources res = getResources();
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        builder.setTitle(res.getString(R.string.menu_download_deck));
        CharSequence[] items;
        if (mSyncEnabled) {
        	items = new CharSequence[2];
        	items[0] = res.getString(R.string.menu_download_personal_deck);
        	items[1] = res.getString(R.string.menu_download_shared_deck);
        } else {
        	items = new CharSequence[1];
        	items[0] = res.getString(R.string.menu_download_shared_deck);
        }
        builder.setItems(items, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int item) {
				if (item == 0 && mSyncEnabled) {
	            	openPersonalDeckPicker();
				} else {
	            	openSharedDeckPicker();
				}
		    }
		});
        AlertDialog alert = builder.create();
		alert.show();
    }


    private void initAllContentViews() {
        // The main study options view that will be used when there are reviews left.
        mStudyOptionsView = getLayoutInflater().inflate(R.layout.studyoptions, null);

        mTextTitle = (TextView) mStudyOptionsView.findViewById(R.id.studyoptions_title);
        mTextDeckName = (TextView) mStudyOptionsView.findViewById(R.id.studyoptions_deck_name);

        mButtonStart = (Button) mStudyOptionsView.findViewById(R.id.studyoptions_start);
        mToggleCram = (ToggleButton) mStudyOptionsView.findViewById(R.id.studyoptions_cram);

        mTextReviewsDue = (TextView) mStudyOptionsView.findViewById(R.id.studyoptions_reviews_due);
        mTextNewToday = (TextView) mStudyOptionsView.findViewById(R.id.studyoptions_new_today);
        mTextNewTotal = (TextView) mStudyOptionsView.findViewById(R.id.studyoptions_new_total);

        mEditNewPerDay = (EditText) mStudyOptionsView.findViewById(R.id.studyoptions_new_cards_per_day);
        mEditSessionTime = (EditText) mStudyOptionsView.findViewById(R.id.studyoptions_session_minutes);
        mEditSessionQuestions = (EditText) mStudyOptionsView.findViewById(R.id.studyoptions_session_questions);

        mButtonStart.setOnClickListener(mButtonClickListener);
        mToggleCram.setOnClickListener(mButtonClickListener);

        mEditNewPerDay.addTextChangedListener(new TextWatcher() {
        	public void afterTextChanged(Editable s) {
                Deck deck = AnkiDroidApp.deck();
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
        	}
        public void beforeTextChanged(CharSequence s, int start, int count, int after){}
        public void onTextChanged(CharSequence s, int start, int before, int count){}
        });
        
        mEditSessionTime.addTextChangedListener(new TextWatcher() {
        	public void afterTextChanged(Editable s) {
                Deck deck = AnkiDroidApp.deck();
                String inputText = mEditSessionTime.getText().toString();
                if (!inputText.equals(Long.toString(deck.getSessionTimeLimit() / 60))) {
                	if (inputText.equals("")) {
                		deck.setSessionTimeLimit(0);                		
                	} else if (isValidLong(inputText)) {
                		deck.setSessionTimeLimit(Long.parseLong(inputText) * 60);
                	} else {
                		mEditSessionTime.setText("0");
                	}
            		updateValuesFromDeck();
                }
        	}
        public void beforeTextChanged(CharSequence s, int start, int count, int after){}
        public void onTextChanged(CharSequence s, int start, int before, int count){}
        });
        
        mEditSessionQuestions.addTextChangedListener(new TextWatcher() {
        	public void afterTextChanged(Editable s) {
                Deck deck = AnkiDroidApp.deck();
                String inputText = mEditSessionQuestions.getText().toString();
                if (!inputText.equals(Long.toString(deck.getSessionRepLimit()))) {
                	if (inputText.equals("")) {
                		deck.setSessionRepLimit(0);                		
                	} else if (isValidLong(inputText)) {
                		deck.setSessionRepLimit(Long.parseLong(inputText));
                	} else {
                		mEditSessionQuestions.setText("0");
                	}
            		updateValuesFromDeck();
                }
        	}
        public void beforeTextChanged(CharSequence s, int start, int count, int after){}
        public void onTextChanged(CharSequence s, int start, int before, int count){}
        });

        mDialogMoreOptions = createMoreOptionsDialog();

        // The view to use when there is no deck loaded yet.
        // TODO: Add and init view here.
        mNoDeckView = getLayoutInflater().inflate(R.layout.studyoptions_nodeck, null);

        mTextNoDeckTitle = (TextView) mNoDeckView.findViewById(R.id.studyoptions_nodeck_title);
        mTextNoDeckMessage = (TextView) mNoDeckView.findViewById(R.id.studyoptions_nodeck_message);

        mNoDeckView.findViewById(R.id.studyoptions_load_sample_deck).setOnClickListener(mButtonClickListener);
        mNoDeckView.findViewById(R.id.studyoptions_download_deck).setOnClickListener(mButtonClickListener);
        mNoDeckView.findViewById(R.id.studyoptions_load_other_deck).setOnClickListener(mButtonClickListener);

        // The view that shows the congratulations view.
        mCongratsView = getLayoutInflater().inflate(R.layout.studyoptions_congrats, null);

        mTextCongratsMessage = (TextView) mCongratsView.findViewById(R.id.studyoptions_congrats_message);
        mButtonCongratsLearnMore = (Button) mCongratsView.findViewById(R.id.studyoptions_congrats_learnmore);
        mButtonCongratsReviewEarly = (Button) mCongratsView.findViewById(R.id.studyoptions_congrats_reviewearly);
        mButtonCongratsOpenOtherDeck = (Button) mCongratsView.findViewById(R.id.studyoptions_congrats_open_other_deck);
        mButtonCongratsFinish = (Button) mCongratsView.findViewById(R.id.studyoptions_congrats_finish);

        mButtonCongratsLearnMore.setOnClickListener(mButtonClickListener);
        mButtonCongratsReviewEarly.setOnClickListener(mButtonClickListener);
        mButtonCongratsOpenOtherDeck.setOnClickListener(mButtonClickListener);
        mButtonCongratsFinish.setOnClickListener(mButtonClickListener);
        
        // The view to use when there is no external storage available
        mNoExternalStorageView = getLayoutInflater().inflate(R.layout.studyoptions_nostorage, null);
    }


    /**
* Create AlertDialogs used on all the activity
*/
    private void initAllDialogs() {
        Resources res = getResources();

        // Init alert dialogs
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        builder.setTitle(res.getString(R.string.connection_error_title));
        builder.setIcon(android.R.drawable.ic_dialog_alert);
        builder.setMessage(res.getString(R.string.connection_needed));
        builder.setPositiveButton(res.getString(R.string.ok), null);
        mNoConnectionAlert = builder.create();

        builder.setTitle(res.getString(R.string.connection_error_title));
        builder.setIcon(android.R.drawable.ic_dialog_alert);
        builder.setMessage(res.getString(R.string.no_user_password_error_message));
        builder.setPositiveButton(res.getString(R.string.log_in), new OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {
                Intent myAccount = new Intent(StudyOptions.this, MyAccount.class);
                startActivity(myAccount);
            }
        });
        builder.setNegativeButton(res.getString(R.string.cancel), null);
        mUserNotLoggedInAlert = builder.create();

        builder.setTitle(res.getString(R.string.connection_error_title));
        builder.setIcon(android.R.drawable.ic_dialog_alert);
        builder.setMessage(res.getString(R.string.connection_error_message));
        builder.setPositiveButton(res.getString(R.string.retry), new OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {
                syncDeck();
            }
        });
        builder.setNegativeButton(res.getString(R.string.cancel), null);
        mConnectionErrorAlert = builder.create();
    }


    // This has to be called every time we open a new deck AND whenever we edit any tags.
    private void recreateCramTagsDialog() {
        Resources res = getResources();

        // Dialog for selecting the cram tags
        activeCramTags.clear();
        allCramTags = AnkiDroidApp.deck().allTags_();
        Log.i(AnkiDroidApp.TAG, "all cram tags: " + Arrays.toString(allCramTags));

        View contentView = getLayoutInflater().inflate(R.layout.studyoptions_cram_dialog_contents, null);
        mCramTagsListView = (ListView) contentView.findViewById(R.id.cram_tags_list);
        mCramTagsListView.setAdapter(new ArrayAdapter<String>(this, R.layout.studyoptions_cram_dialog_item,
                    allCramTags));
        mCramTagsListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (((CheckedTextView)view).isChecked()) {
                    Log.i(AnkiDroidApp.TAG, "unchecked tag: " + allCramTags[position]);
                    activeCramTags.remove(allCramTags[position]);
                } else {
                    Log.i(AnkiDroidApp.TAG, "checked tag: " + allCramTags[position]);
                    activeCramTags.add(allCramTags[position]);
                }
            }
        });
        mCramTagsListView.setItemsCanFocus(false);
        mSpinnerCramOrder = (Spinner) contentView.findViewById(R.id.cram_order_spinner);
        mSpinnerCramOrder.setSelection(0);
        mSpinnerCramOrder.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                cramOrder = cramOrderList[position];
            }
            @Override
            public void onNothingSelected(AdapterView<?> arg0) {
                return;
            }
        });
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.studyoptions_cram_dialog_title);
        builder.setPositiveButton(res.getString(R.string.begin_cram), new OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                onCram();
            }
        });
        builder.setNegativeButton(res.getString(R.string.cancel), new OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                mToggleCram.setChecked(false);
            }
        });
		builder.setOnCancelListener(new DialogInterface.OnCancelListener() {
			@Override
			public void onCancel(DialogInterface dialog) {
                mToggleCram.setChecked(false);
			}
		});					
        builder.setView(contentView);
        mCramTagsDialog = builder.create();
    }

    private AlertDialog createMoreOptionsDialog() {
        // Custom view for the dialog content.
        View contentView = getLayoutInflater().inflate(R.layout.studyoptions_more_dialog_contents, null);
        mSpinnerNewCardOrder = (Spinner) contentView.findViewById(R.id.studyoptions_new_card_order);
        mSpinnerNewCardSchedule = (Spinner) contentView.findViewById(R.id.studyoptions_new_card_schedule);
        mSpinnerRevCardOrder = (Spinner) contentView.findViewById(R.id.studyoptions_rev_card_order);
        mSpinnerFailCardOption = (Spinner) contentView.findViewById(R.id.studyoptions_fail_card_option);
        mCheckBoxPerDay = (CheckBox) contentView.findViewById(R.id.studyoptions_per_day);
        mCheckBoxSuspendLeeches = (CheckBox) contentView.findViewById(R.id.studyoptions_suspend_leeches);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.studyoptions_more_dialog_title);
        builder.setPositiveButton(R.string.studyoptions_more_save, mDialogSaveListener);
        builder.setView(contentView);

        return builder.create();
    }


    private void showMoreOptionsDialog() {
        // Update spinner selections from deck prior to showing the dialog.
        Deck deck = AnkiDroidApp.deck();
        mSpinnerNewCardOrder.setSelection(deck.getNewCardOrder());
        mSpinnerNewCardSchedule.setSelection(deck.getNewCardSpacing());
        mSpinnerRevCardOrder.setSelection(deck.getRevCardOrder());
        mSpinnerFailCardOption.setVisibility(View.GONE); // TODO: Not implemented yet.
        mCheckBoxPerDay.setChecked(deck.getPerDay());
        mCheckBoxSuspendLeeches.setChecked(deck.getSuspendLeeches());

        mDialogMoreOptions.show();
    }


    private void showContentView(int which) {
        mCurrentContentView = which;
        showContentView();
    }


    private void showContentView() {

        switch (mCurrentContentView) {
            case CONTENT_NO_DECK:
                setTitle(R.string.app_name);
                mTextNoDeckTitle.setText(R.string.studyoptions_nodeck_title);
                mTextNoDeckMessage.setText(String.format(
                        getResources().getString(R.string.studyoptions_nodeck_message), mPrefDeckPath));
                setContentView(mNoDeckView);
                break;
            case CONTENT_DECK_NOT_LOADED:
                setTitle(R.string.app_name);
                mTextNoDeckTitle.setText(R.string.studyoptions_deck_not_loaded_title);
                mTextNoDeckMessage.setText(R.string.studyoptions_deck_not_loaded_message);
                setContentView(mNoDeckView);
                break;
            case CONTENT_STUDY_OPTIONS:
            case CONTENT_SESSION_COMPLETE:
                // Enable timeboxing in case it was disabled from the previous deck
                if (AnkiDroidApp.deck().name().equals("cram")) {
                    mToggleCram.setChecked(false);
                    mEditNewPerDay.setEnabled(true);
                    mEditSessionTime.setEnabled(true);
                    mEditSessionQuestions.setEnabled(true);
                }
                // Return to standard scheduler
                if ((AnkiDroidApp.deck() != null) && (AnkiDroidApp.deck().hasFinishScheduler())) {
                    AnkiDroidApp.deck().finishScheduler();
                }
                updateValuesFromDeck();
                if (mCurrentContentView == CONTENT_STUDY_OPTIONS) {
                    mButtonStart.setText(R.string.studyoptions_start);
                    mTextTitle.setText(R.string.studyoptions_title);
                } else {
                    mButtonStart.setText(R.string.studyoptions_continue);
                    mTextTitle.setText(R.string.studyoptions_well_done);
                }
                setContentView(mStudyOptionsView);
                break;
            case CONTENT_CONGRATS:
                updateValuesFromDeck();
                setContentView(mCongratsView);
                break;
            case CONTENT_NO_EXTERNAL_STORAGE:
                setTitle(R.string.app_name);
                setContentView(mNoExternalStorageView);
                break;
        }
    }


    private void updateValuesFromDeck() {
        Deck deck = AnkiDroidApp.deck();
        DeckTask.waitToFinish();
        if (deck != null) {
            deck.reset();
            // TODO: updateActives() from anqiqt/ui/main.py
            int reviewCount = deck.getDueCount();
            String unformattedTitle = getResources().getString(R.string.studyoptions_window_title);
            setTitle(String.format(unformattedTitle, deck.getDeckName(), reviewCount, deck.getCardCount()));

            mTextDeckName.setText(deck.getDeckName());
            mTextReviewsDue.setText(String.valueOf(reviewCount));
            mTextNewToday.setText(String.valueOf(deck.getNewCountToday()));
            mTextNewTotal.setText(String.valueOf(deck.getNewCount()));

            if (!mEditNewPerDay.getText().toString().equals(String.valueOf(deck.getNewCardsPerDay())) && !mEditNewPerDay.getText().toString().equals("")) {
            	mEditNewPerDay.setText(String.valueOf(deck.getNewCardsPerDay()));
            }
            if (!mEditSessionTime.getText().toString().equals(String.valueOf(deck.getSessionTimeLimit() / 60)) && !mEditSessionTime.getText().toString().equals("")) {
            	mEditSessionTime.setText(String.valueOf(deck.getSessionTimeLimit() / 60));
            }
            if (!mEditSessionQuestions.getText().toString().equals(String.valueOf(deck.getSessionRepLimit())) && !mEditSessionQuestions.getText().toString().equals("")) {
            	mEditSessionQuestions.setText(String.valueOf(deck.getSessionRepLimit()));
            }
        }
    }

    /*
* Switch schedulers
*/

    private void reset() {
        reset(false);
    }
    private void reset(boolean priorities) {
        if (priorities) {
            AnkiDroidApp.deck().updateAllPriorities();
        }
        AnkiDroidApp.deck().reset();
    }


    private void onLearnMore() {
        Deck deck = AnkiDroidApp.deck();
        if (deck != null) {
            deck.setupLearnMoreScheduler();
            deck.reset();
        }
    }


    private void onReviewEarly() {
        Deck deck = AnkiDroidApp.deck();
        if (deck != null) {
            deck.setupReviewEarlyScheduler();
            deck.reset();
        }
    }


    /**
* Enter cramming mode.
* Currently not supporting cramming from selection of cards, as we don't have a card list view anyway.
*/
    private void onCram() {
        AnkiDroidApp.deck().setupCramScheduler(activeCramTags.toArray(new String[activeCramTags.size()]), cramOrder);
        // Timeboxing only supported using the standard scheduler
        mEditNewPerDay.setEnabled(false);
        mEditSessionTime.setEnabled(false);
        mEditSessionQuestions.setEnabled(false);
        updateValuesFromDeck();
    }

    /**
* Exit cramming mode.
*/
    private void onCramStop() {
        AnkiDroidApp.deck().setupStandardScheduler();
        mEditNewPerDay.setEnabled(true);
        mEditSessionTime.setEnabled(true);
        mEditSessionQuestions.setEnabled(true);
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
    	MenuItem item;
        item = menu.add(Menu.NONE, MENU_OPEN, Menu.NONE, R.string.menu_open_deck);
        item.setIcon(R.drawable.ic_menu_manage);
        SubMenu downloadDeckSubMenu = menu.addSubMenu(Menu.NONE, SUBMENU_DOWNLOAD, Menu.NONE,
                R.string.menu_download_deck);
        downloadDeckSubMenu.setIcon(R.drawable.ic_menu_download);
        downloadDeckSubMenu.add(
                Menu.NONE, MENU_DOWNLOAD_PERSONAL_DECK, Menu.NONE, R.string.menu_download_personal_deck);
        downloadDeckSubMenu.add(Menu.NONE, MENU_DOWNLOAD_SHARED_DECK, Menu.NONE, R.string.menu_download_shared_deck);
        item = menu.add(Menu.NONE, MENU_SYNC, Menu.NONE, R.string.menu_sync);
        item.setIcon(R.drawable.ic_menu_refresh);
        item = menu.add(Menu.NONE, MENU_ADD_FACT, Menu.NONE, R.string.menu_add_card);
        item.setIcon(R.drawable.ic_menu_add);
        item = menu.add(Menu.NONE, MENU_MORE_OPTIONS, Menu.NONE, R.string.studyoptions_more);
        item.setIcon(R.drawable.ic_menu_archive);
        item = menu.add(Menu.NONE, MENU_PREFERENCES, Menu.NONE, R.string.menu_preferences);
        item.setIcon(R.drawable.ic_menu_preferences);
        item = menu.add(Menu.NONE, MENU_MY_ACCOUNT, Menu.NONE, R.string.menu_my_account);
        item.setIcon(R.drawable.ic_menu_home);
        item = menu.add(Menu.NONE, MENU_ABOUT, Menu.NONE, R.string.menu_about);
        item.setIcon(R.drawable.ic_menu_info_details);

        return true;
    }


    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        boolean deckChangable = (AnkiDroidApp.deck() != null) && mSdCardAvailable && !mToggleCram.isChecked(); 
        menu.findItem(MENU_OPEN).setEnabled(mSdCardAvailable);
        menu.findItem(SUBMENU_DOWNLOAD).setEnabled(mSdCardAvailable);
        menu.findItem(MENU_ADD_FACT).setEnabled(deckChangable);
        menu.findItem(MENU_MORE_OPTIONS).setEnabled(deckChangable);
		menu.findItem(MENU_SYNC).setEnabled(deckChangable);

        // Show sync menu items only if sync is enabled.
		menu.findItem(MENU_SYNC).setVisible(mSyncEnabled);       
		menu.findItem(MENU_MY_ACCOUNT).setVisible(mSyncEnabled);
        menu.findItem(MENU_DOWNLOAD_PERSONAL_DECK).setVisible(mSyncEnabled);

        return true;
    }


    /** Handles item selections */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Log.i(AnkiDroidApp.TAG, "Item = " + item.getItemId());
        switch (item.getItemId()) {
            case MENU_OPEN:
                openDeckPicker();
                return true;

            case MENU_DOWNLOAD_PERSONAL_DECK:
            	openPersonalDeckPicker();
                return true;

            case MENU_DOWNLOAD_SHARED_DECK:
            	openSharedDeckPicker();
                return true;

            case MENU_SYNC:
                syncDeck();
                return true;

            case MENU_MY_ACCOUNT:
                startActivity(new Intent(StudyOptions.this, MyAccount.class));
                return true;

            case MENU_MORE_OPTIONS:
                showMoreOptionsDialog();
                return true;
                
            case MENU_PREFERENCES:
                startActivityForResult(
                        new Intent(StudyOptions.this, Preferences.class),
                        PREFERENCES_UPDATE);
                return true;

            case MENU_ADD_FACT:
            	startActivityForResult(new Intent(StudyOptions.this, FactAdder.class), ADD_FACT);
                return true;

            case MENU_ABOUT:
                startActivity(new Intent(StudyOptions.this, About.class));
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }


    private void openDeckPicker() {
        closeOpenedDeck();
        // deckLoaded = false;
        Intent decksPicker = new Intent(StudyOptions.this, DeckPicker.class);
        mInDeckPicker = true;
        startActivityForResult(decksPicker, PICK_DECK_REQUEST);
        // Log.i(AnkiDroidApp.TAG, "openDeckPicker - Ending");
    }

    public void openPersonalDeckPicker() {
        if (AnkiDroidApp.isUserLoggedIn()) {
            if (AnkiDroidApp.deck() != null)// && sdCardAvailable)
            {
                AnkiDroidApp.deck().closeDeck();
                AnkiDroidApp.setDeck(null);
            }
            startActivityForResult(
                    new Intent(StudyOptions.this, PersonalDeckPicker.class), DOWNLOAD_PERSONAL_DECK);
        } else {
            mUserNotLoggedInAlert.show();
        }
    }


    public void openSharedDeckPicker() {
        if (AnkiDroidApp.deck() != null)// && sdCardAvailable)
        {
            AnkiDroidApp.deck().closeDeck();
            AnkiDroidApp.setDeck(null);
        }
        // deckLoaded = false;
        startActivityForResult(new Intent(StudyOptions.this, SharedDeckPicker.class), DOWNLOAD_SHARED_DECK);
    }


    private void loadSampleDeck() {
        File sampleDeckFile = new File(mPrefDeckPath, SAMPLE_DECK_NAME);

        if (!sampleDeckFile.exists()) {
            // Create the deck.
            try {
                // Copy the sample deck from the assets to the SD card.
                InputStream stream = getResources().getAssets().open(SAMPLE_DECK_NAME);
                boolean written = Utils.writeToFile(stream, sampleDeckFile.getAbsolutePath());
                stream.close();
                if (!written) {
                    openDeckPicker();
                    Log.i(AnkiDroidApp.TAG, "onCreate - The copy of country-capitals.anki to the sd card failed.");
                    return;
                }
                Log.i(AnkiDroidApp.TAG, "onCreate - The copy of country-capitals.anki to the sd card was sucessful.");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        Intent deckLoadIntent = new Intent();
        deckLoadIntent.putExtra(OPT_DB, sampleDeckFile.getAbsolutePath());
        onActivityResult(PICK_DECK_REQUEST, RESULT_OK, deckLoadIntent);
    }


    private void syncDeck() {
        SharedPreferences preferences = PrefSettings.getSharedPrefs(getBaseContext());

        String username = preferences.getString("username", "");
        String password = preferences.getString("password", "");

        if (AnkiDroidApp.isUserLoggedIn()) {
            Deck deck = AnkiDroidApp.deck();

            Log.i(AnkiDroidApp.TAG,
                    "Synchronizing deck " + mDeckFilename + " with username " + username + " and password " + password);
            Log.i(AnkiDroidApp.TAG, String.format(Utils.ENGLISH_LOCALE, "before syncing - mod: %f, last sync: %f", deck.getModified(), deck.getLastSync()));
            Connection.syncDeck(syncListener, new Connection.Payload(new Object[] { username, password, deck,
                    mDeckFilename }));
        } else {
            mUserNotLoggedInAlert.show();
        }
    }

    private void reloadDeck() {
    	Deck deck = AnkiDroidApp.deck(); 
    	if (deck != null){
    		deck.closeDeck();
    		AnkiDroidApp.setDeck(null);
    	}
        DeckTask.launchDeckTask(DeckTask.TASK_TYPE_LOAD_DECK, mLoadDeckHandler, new DeckTask.TaskData(
                mDeckFilename));
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);

        if (resultCode == CONTENT_NO_EXTERNAL_STORAGE) {
            showContentView(CONTENT_NO_EXTERNAL_STORAGE);
        } else if (requestCode == PICK_DECK_REQUEST || requestCode == DOWNLOAD_PERSONAL_DECK
                || requestCode == DOWNLOAD_SHARED_DECK) {
            // Clean the previous card before showing the first of the new loaded deck (so the transition is not so
            // abrupt)
            // updateCard("");
            // hideSdError();
            // hideDeckErrors();
            mInDeckPicker = false;

            if (resultCode != RESULT_OK) {
                Log.e(AnkiDroidApp.TAG, "onActivityResult - Deck browser returned with error");
                // Make sure we open the database again in onResume() if user pressed "back"
                // deckSelected = false;
                boolean mFileNotDeleted = mDeckFilename != null && new File(mDeckFilename).exists();
            	if (!mFileNotDeleted) {
                    AnkiDroidApp.setDeck(null);
                    showContentView(CONTENT_NO_DECK);
            	}
                displayProgressDialogAndLoadDeck();
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

            // Log.i(AnkiDroidApp.TAG, "onActivityResult - deckSelected = " + deckSelected);
            boolean updateAllCards = (requestCode == DOWNLOAD_SHARED_DECK);
            displayProgressDialogAndLoadDeck(updateAllCards);

        } else if (requestCode == PREFERENCES_UPDATE) {
            restorePreferences();
            showContentView();
            // If there is no deck loaded the controls have not to be shown
            // if(deckLoaded && cardsToReview)
            // {
            // showOrHideControls();
            // showOrHideAnswerField();
            // }
        } else if (requestCode == REQUEST_REVIEW) {
            Log.i(AnkiDroidApp.TAG, "Result code = " + resultCode);
            AnkiDroidApp.deck().updateCutoff();
            AnkiDroidApp.deck().reset();
            switch (resultCode) {
                case Reviewer.RESULT_SESSION_COMPLETED:
                    showContentView(CONTENT_SESSION_COMPLETE);
                    break;
                case Reviewer.RESULT_NO_MORE_CARDS:
                    showContentView(CONTENT_CONGRATS);
                    break;
                default:
                    showContentView(CONTENT_STUDY_OPTIONS);
                    break;
            }
        } else if (requestCode == ADD_FACT && resultCode == RESULT_OK) {
            reloadDeck();
        }
    }

    private boolean showDeckPickerOnStartup() {
    	switch (mStartupMode) {
    	case SUM_STUDY_OPTIONS:
            return false;
    	
    	case SUM_DECKPICKER:
    		return true;
    	
    	case SUM_DECKPICKER_ON_FIRST_START:
            
    		Calendar cal = Calendar.getInstance();
    		if (cal.get(Calendar.HOUR_OF_DAY) < mNewDayStartsAt) {
                cal.add(Calendar.HOUR_OF_DAY, -cal.get(Calendar.HOUR_OF_DAY) - 24 + mNewDayStartsAt);
    		} else {
                cal.add(Calendar.HOUR_OF_DAY, -cal.get(Calendar.HOUR_OF_DAY) + mNewDayStartsAt);    			
    		}
            cal.add(Calendar.MINUTE, -cal.get(Calendar.MINUTE));
            cal.add(Calendar.SECOND, -cal.get(Calendar.SECOND));
            if (cal.getTimeInMillis() > mLastTimeOpened) {
            	return true;
            } else {
            	return false;
            }
    	default:
    		return false;
    	}        
    }
        
    private void savePreferences(String str) {
        SharedPreferences preferences = PrefSettings.getSharedPrefs(getBaseContext());
        Editor editor = preferences.edit();
        if (str == "deckFilename") {
            editor.putString("deckFilename", mDeckFilename);        
        } else if (str == "lastOpened") {
            editor.putLong("lastTimeOpened", System.currentTimeMillis());        	
        }
        editor.commit();
    }


    private SharedPreferences restorePreferences() {
        SharedPreferences preferences = PrefSettings.getSharedPrefs(getBaseContext());
        mPrefDeckPath = preferences.getString("deckPath", AnkiDroidApp.getStorageDirectory());
        mPrefStudyOptions = preferences.getBoolean("study_options", true);
        mStartupMode = Integer.parseInt(preferences.getString("startup_mode",
                Integer.toString(SUM_DECKPICKER_ON_FIRST_START)));
        mLastTimeOpened = preferences.getLong("lastTimeOpened", 0);
        mSyncEnabled = preferences.getBoolean("syncEnabled", false);
        return preferences;
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
            mEditNewPerDay.setEnabled(true);
            mEditSessionTime.setEnabled(true);
            mEditSessionQuestions.setEnabled(true);
            
            if (updateAllCards) {
                DeckTask.launchDeckTask(DeckTask.TASK_TYPE_LOAD_DECK_AND_UPDATE_CARDS, mLoadDeckHandler,
                        new DeckTask.TaskData(mDeckFilename));
            } else {
                DeckTask.launchDeckTask(DeckTask.TASK_TYPE_LOAD_DECK, mLoadDeckHandler, new DeckTask.TaskData(
                        mDeckFilename));
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

    DeckTask.TaskListener mLoadDeckHandler = new DeckTask.TaskListener() {

        @Override
        public void onPreExecute() {
            // if(updateDialog == null || !updateDialog.isShowing())
            // {
            mProgressDialog = ProgressDialog.show(StudyOptions.this, "", getResources()
                    .getString(R.string.loading_deck), true);
            // }
        }


        @Override
        public void onPostExecute(DeckTask.TaskData result) {

            // Close the previously opened deck.
            // if (AnkidroidApp.deck() != null)
            // {
            // AnkidroidApp.deck().closeDeck();
            // AnkidroidApp.setDeck(null);
            // }

            switch (result.getInt()) {
                case DeckTask.DECK_LOADED:
                    // Set the deck in the application instance, so other activities
                    // can access the loaded deck.
                    AnkiDroidApp.setDeck(result.getDeck());
                    if (mPrefStudyOptions) {
                        showContentView(CONTENT_STUDY_OPTIONS);
                    } else {
                        startActivityForResult(new Intent(StudyOptions.this, Reviewer.class), REQUEST_REVIEW);
                    }

                    break;

                case DeckTask.DECK_NOT_LOADED:
                    showContentView(CONTENT_DECK_NOT_LOADED);
                    break;

                case DeckTask.DECK_EMPTY:
                    // displayNoCardsInDeck();
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
            }
        }


        @Override
        public void onProgressUpdate(DeckTask.TaskData... values) {
            // Pass
        }
    };

    Connection.TaskListener syncListener = new Connection.TaskListener() {

        @Override
        public void onDisconnected() {
            if (mNoConnectionAlert != null) {
                mNoConnectionAlert.show();
            }
        }


        @Override
        public void onPostExecute(Payload data) {
            Log.i(AnkiDroidApp.TAG, "onPostExecute");
            if (mProgressDialog != null) {
                mProgressDialog.dismiss();
            }
            if (data.success) {
            	reloadDeck();
            } else {
                // connectionFailedAlert.show();
                if (mConnectionErrorAlert != null) {
                    mConnectionErrorAlert.show();
                }
            }

        }


        @Override
        public void onPreExecute() {
            // Pass
        }


        @Override
        public void onProgressUpdate(Object... values) {
            if (mProgressDialog == null || !mProgressDialog.isShowing()) {
                mProgressDialog = ProgressDialog.show(StudyOptions.this, (String) values[0], (String) values[1]);
            } else {
                mProgressDialog.setTitle((String) values[0]);
                mProgressDialog.setMessage((String) values[1]);
            }
        }

    };
}
