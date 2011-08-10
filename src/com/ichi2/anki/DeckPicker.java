/****************************************************************************************
 * Copyright (c) 2009 Andrew Dubya <andrewdubya@gmail.com>                              *
 * Copyright (c) 2009 Nicolas Raoul <nicolas.raoul@gmail.com>                           *
 * Copyright (c) 2009 Edu Zamora <edu.zasu@gmail.com>                                   *
 * Copyright (c) 2009 Daniel Svärd <daniel.svard@gmail.com>                             * 
 * Copyright (c) 2010 Norbert Nagold <norbert.nagold@gmail.com>                         *
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

package com.ichi2.anki;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.DialogInterface.OnCancelListener;
import android.content.res.Resources;
import android.database.SQLException;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.text.SpannableStringBuilder;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;
import android.util.Log;
import android.view.ContextMenu;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.SubMenu;
import android.view.View;
import android.view.ViewGroup;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.SimpleAdapter;
import android.widget.AdapterView.AdapterContextMenuInfo;

import com.ichi2.anim.ActivityTransitionAnimation;
import com.ichi2.anim.ViewAnimation;
import com.ichi2.anki.DeckTask.TaskData;
import com.ichi2.async.Connection;
import com.ichi2.async.Connection.Payload;
import com.tomgibara.android.veecheck.util.PrefSettings;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.TreeSet;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Allows the user to choose a deck from the filesystem.
 */
public class DeckPicker extends Activity implements Runnable {

	/**
	 * Dialogs
	 */
	private static final int DIALOG_NO_SDCARD = 0;
	private static final int DIALOG_USER_NOT_LOGGED_IN = 1;
	private static final int DIALOG_NO_CONNECTION = 2;
	private static final int DIALOG_DELETE_DECK = 3;
	private static final int DIALOG_SELECT_STATISTICS_TYPE = 4;
	private static final int DIALOG_SELECT_STATISTICS_PERIOD = 5;	
	private static final int DIALOG_OPTIMIZE_DATABASE = 6;
	private static final int DIALOG_DELETE_BACKUPS = 7;

	/**
	 * Menus
	 */
    private static final int MENU_ABOUT = 0;
    private static final int SUBMENU_DOWNLOAD = 1;
    private static final int MENU_CREATE_DECK = 2;
    private static final int MENU_DOWNLOAD_PERSONAL_DECK = 21;
    private static final int MENU_DOWNLOAD_SHARED_DECK = 22;
    private static final int MENU_PREFERENCES = 3;
    private static final int MENU_MY_ACCOUNT = 4;
    private static final int MENU_FEEDBACK = 5;

	/**
	 * Message types
	 */
	private static final int MSG_UPGRADE_NEEDED = 0;
	private static final int MSG_UPGRADE_SUCCESS = 1;
	private static final int MSG_UPGRADE_FAILURE = 2;

	/**
	 * Deck orders
	 */
	private static final int ORDER_BY_DATE = 0;
	private static final int ORDER_ALPHABETICAL = 1;
	private static final int ORDER_BY_DUE_CARDS = 2;
	private static final int ORDER_BY_TOTAL_CARDS = 3;
	private static final int ORDER_BY_REMAINING_NEW_CARDS = 4;

    /**
	* Available options performed by other activities
	*/
    private static final int PREFERENCES_UPDATE = 0;
    private static final int CREATE_DECK = 1;
    private static final int DOWNLOAD_PERSONAL_DECK = 2;
    private static final int DOWNLOAD_SHARED_DECK = 3;
    private static final int REPORT_FEEDBACK = 4;

	private DeckPicker mSelf;

	private ProgressDialog mProgressDialog;
	private AlertDialog mSyncLogAlert;
	private AlertDialog mUpgradeNotesAlert;
	private AlertDialog mMissingMediaAlert;
	private AlertDialog mDeckNotLoadedAlert;
	private AlertDialog mNoSpaceLeftAlert;
	private Button mSyncAllButton;
	private Button mStatisticsAllButton;
	private View mDeckpickerButtons;

	private File[] mBackups;
	private ArrayList<String> mBrokenDecks;
	private boolean mRestoredOrDeleted = false;
	private ArrayList<String> mAlreadyDealtWith;

	private SimpleAdapter mDeckListAdapter;
	private ArrayList<HashMap<String, String>> mDeckList;
	private ListView mDeckListView;

	private File[] mFileList;

	private ReentrantLock mLock = new ReentrantLock();
	private Condition mCondFinished = mLock.newCondition();

	private boolean mIsFinished = true;
	private boolean mDeckIsSelected = false;

	private BroadcastReceiver mUnmountReceiver = null;

	private String mPrefDeckPath = null;
	private int mPrefDeckOrder = 0;
	private boolean mPrefStartupDeckPicker = false;
	private String mCurrentDeckFilename = null;
	private String mCurrentDeckPath = null;

	private int mTotalDueCards = 0;
	private int mTotalCards = 0;
	private int mTotalTime = 0;

	int mStatisticType;

	boolean mCompletionBarRestrictToActive = false; // set this to true in order to calculate completion bar only for active cards

	/**
     * Swipe Detection
     */    
 	private GestureDetector gestureDetector;
 	View.OnTouchListener gestureListener;
 	private boolean mSwipeEnabled;
 	
	// ----------------------------------------------------------------------------
	// LISTENERS
	// ----------------------------------------------------------------------------

	private AdapterView.OnItemClickListener mDeckSelHandler = new AdapterView.OnItemClickListener() {
		@Override
		public void onItemClick(AdapterView<?> parent, View v, int p, long id) {
			mSelf.handleDeckSelection(p);
		}
	};

	private Handler mHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			Resources res = mSelf.getResources();
			Bundle data = msg.getData();
			String dueString = "";
			String newString = "";
			String showProgress = "false";
			String notes = data.getString("notes");
            String completionMat = Integer.toString(data.getInt("rateOfCompletionMat"));
            String completionAll = Integer.toString(data.getInt("rateOfCompletionAll"));

			String path = data.getString("absPath");
			int msgtype = data.getInt("msgtype");
			int due = data.getInt("due");
			int total = data.getInt("total");
			int totalNew = data.getInt("totalNew");

			if (msgtype == DeckPicker.MSG_UPGRADE_NEEDED) {
				dueString = res.getString(R.string.deckpicker_upgrading);
				newString = "";
				showProgress = "true";
			} else if (msgtype == DeckPicker.MSG_UPGRADE_FAILURE) {
				dueString = "Upgrade failed!";
				newString = "";
				showProgress = "false";
			} else if (msgtype == DeckPicker.MSG_UPGRADE_SUCCESS) {
				dueString = res.getQuantityString(R.plurals.deckpicker_due, due, due, total);
				newString = String
						.format(res.getString(R.string.deckpicker_new), data
								.getInt("new"));
				showProgress = "false";
			}

			int count = mDeckListAdapter.getCount();
			for (int i = 0; i < count; i++) {
				@SuppressWarnings("unchecked")
				HashMap<String, String> map = (HashMap<String, String>) mDeckListAdapter
						.getItem(i);
				if (map.get("filepath").equals(path)) {
					map.put("due", dueString);
					map.put("new", newString);
					map.put("showProgress", showProgress);
                    map.put("notes", notes);
                    map.put("rateOfCompletionMat", completionMat);                    
                    map.put("rateOfCompletionAll", completionAll);
                    map.put("dueInt", Integer.toString(due));                    
                    map.put("total", Integer.toString(total));                    
                    map.put("totalNew", Integer.toString(totalNew));                    
				}
			}

			Collections.sort(mDeckList, new HashMapCompare());
			mDeckListAdapter.notifyDataSetChanged();
			Log.i(AnkiDroidApp.TAG, "DeckPicker - mDeckList notified of changes");
			setTitleText();
            if (data.getBoolean("lastDeck")) {
                enableButtons(true);
                mRestoredOrDeleted = false;
                handleRestoreDecks(false);
            }
		}
	};

	private Connection.TaskListener mSyncAllDecksListener = new Connection.TaskListener() {

		@Override
		public void onDisconnected() {
			showDialog(DIALOG_NO_CONNECTION);
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
				mProgressDialog = ProgressDialog.show(DeckPicker.this,
						(String) values[0], (String) values[1]);
			} else {
				mProgressDialog.setTitle((String) values[0]);
				mProgressDialog.setMessage((String) values[1]);
			}
		}

		@Override
		public void onPostExecute(Payload data) {
			Log.i(AnkiDroidApp.TAG, "onPostExecute");
			if (mProgressDialog != null) {
				mProgressDialog.dismiss();
			}

			mSyncLogAlert
					.setMessage(getSyncLogMessage((ArrayList<HashMap<String, String>>) data.result));
			mSyncLogAlert.show();
			mDeckIsSelected = false;
			populateDeckList(mPrefDeckPath);
            mSyncAllButton.setClickable(true);
		}
	};

   private Connection.TaskListener mDownloadMediaListener = new Connection.TaskListener() {

        @Override
        public void onDisconnected() {
            showDialog(DIALOG_NO_CONNECTION);
        }

        @Override
        public void onPreExecute() {
            // Pass
        }

        @Override
        public void onProgressUpdate(Object... values) {
            int total = ((Integer)values[1]).intValue();
            int done = ((Integer)values[2]).intValue();
            if (!((Boolean)values[0]).booleanValue()) {
                // Initializing, just get the count of missing media
                if (mProgressDialog != null && mProgressDialog.isShowing()) {
                    mProgressDialog.dismiss();
                }
                mProgressDialog.setMax(total);
                mProgressDialog.show();
            } else {
                mProgressDialog.setProgress(done);
            }
        }

        @Override
        public void onPostExecute(Payload data) {
            Log.i(AnkiDroidApp.TAG, "onPostExecute");
            Resources res = getResources();
            if (mProgressDialog != null) {
                mProgressDialog.dismiss();
            }

            if (data.success) {
                int total = ((Integer)data.data[0]).intValue();
                if (total == 0) {
                    mMissingMediaAlert
                        .setMessage(res.getString(R.string.deckpicker_download_missing_none));
                } else {
                    int done = ((Integer)data.data[1]).intValue();
                    int missing = ((Integer)data.data[2]).intValue();
                    mMissingMediaAlert
                        .setMessage(res.getString(R.string.deckpicker_download_missing_success, done, missing));
                }
            } else {
                String failedFile = (String)data.data[0];
                mMissingMediaAlert
                    .setMessage(res.getString(R.string.deckpicker_download_missing_error, failedFile));
            }
            mMissingMediaAlert.show();
            
            Deck deck = (Deck) data.result;
            closeDeck(deck);
         }
    };


	// ----------------------------------------------------------------------------
	// ANDROID METHODS
	// ----------------------------------------------------------------------------

	/** Called when the activity is first created. */
	@Override
	protected void onCreate(Bundle savedInstanceState) throws SQLException {
		Log.i(AnkiDroidApp.TAG, "DeckPicker - onCreate");
		Themes.applyTheme(this);
		super.onCreate(savedInstanceState);

		setTitleText();

		mSelf = this;
		setContentView(R.layout.deck_picker);

		registerExternalStorageListener();

		initDialogs();
		mBrokenDecks = new ArrayList<String>();
		mAlreadyDealtWith = new ArrayList<String>();

		mDeckpickerButtons = (View) findViewById(R.id.deckpicker_buttons);
		mSyncAllButton = (Button) findViewById(R.id.sync_all_button);
		mSyncAllButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				if (AnkiDroidApp.isUserLoggedIn()) {
                    mSyncAllButton.setClickable(false);
					SharedPreferences preferences = PrefSettings
							.getSharedPrefs(getBaseContext());
					String username = preferences.getString("username", "");
					String password = preferences.getString("password", "");
					Connection.syncAllDecks(mSyncAllDecksListener,
							new Connection.Payload(new Object[] { username,
									password, mDeckList }));
				} else {
					showDialog(DIALOG_USER_NOT_LOGGED_IN);
				}
			}

		});

		mStatisticsAllButton = (Button) findViewById(R.id.statistics_all_button);
		mStatisticsAllButton.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				mStatisticType = -1;
				showDialog(DIALOG_SELECT_STATISTICS_TYPE);
			}
		});

		mDeckList = new ArrayList<HashMap<String, String>>();
        mDeckListView = (ListView) findViewById(R.id.files);
		mDeckListAdapter = new AlternatingAdapter(this, mDeckList,
				R.layout.deck_item, new String[] { "name", "due", "new",
						"showProgress", "notes", "rateOfCompletionMat", "rateOfCompletionAll" }, new int[] {
						R.id.DeckPickerName, R.id.DeckPickerDue,
						R.id.DeckPickerNew, R.id.DeckPickerProgress,
						R.id.DeckPickerUpgradeNotesButton,
						R.id.DeckPickerCompletionMat, R.id.DeckPickerCompletionAll });
		mDeckListAdapter.setViewBinder(new SimpleAdapter.ViewBinder() {
			@Override
			public boolean setViewValue(View view, Object data, String text) {
				if (view instanceof ProgressBar) {
					if (text.equals("true")) {
						view.setVisibility(View.VISIBLE);
					} else {
						view.setVisibility(View.GONE);
					}
					return true;
				}
				if (view.getId() == R.id.DeckPickerCompletionMat || view.getId() == R.id.DeckPickerCompletionAll) {
				    if (!text.equals("-1")) {
	                    Utils.updateProgressBars(DeckPicker.this, view, Double.parseDouble(text) / 100.0, mDeckListView.getWidth(), 2, false); 				        
				    } else {
				    	Themes.setContentStyle(view, Themes.CALLER_DECKPICKER_DECK);
				    }
                }
				if (view.getId() == R.id.DeckPickerUpgradeNotesButton) {
					if (text.equals("")) {
						view.setVisibility(View.GONE);
					} else {
						view.setVisibility(View.VISIBLE);
						view.setTag(text);
						view.setOnClickListener(new OnClickListener() {
							@Override
							public void onClick(View v) {
								String tag = (String) v.getTag();
								if (tag == null) {
									tag = "";
								}
								mUpgradeNotesAlert.setMessage(tag);
								mUpgradeNotesAlert.show();
							}
						});
					}
					return true;
				}
				return false;
			}
		});
		mDeckListView.setOnItemClickListener(mDeckSelHandler);
		mDeckListView.setAdapter(mDeckListAdapter);
		registerForContextMenu(mDeckListView);

		
		
		SharedPreferences preferences = PrefSettings
				.getSharedPrefs(getBaseContext());
		mPrefDeckPath = preferences.getString("deckPath", AnkiDroidApp.getStorageDirectory());
		mPrefDeckOrder = Integer.parseInt(preferences.getString("deckOrder", "0"));
		mPrefStartupDeckPicker = Integer.parseInt(preferences.getString("startup_mode", "2")) == StudyOptions.SUM_DECKPICKER;
		populateDeckList(mPrefDeckPath);

		mSwipeEnabled = preferences.getBoolean("swipe", false);
		gestureDetector = new GestureDetector(new MyGestureDetector());
        mDeckListView.setOnTouchListener(new View.OnTouchListener() {
        	public boolean onTouch(View v, MotionEvent event) {
        		if (gestureDetector.onTouchEvent(event)) {
        			return true;
        		}
        		return false;
        		}
        	});        
	}

	@Override
	protected void onPause() {
		Log.i(AnkiDroidApp.TAG, "DeckPicker - onPause");

		super.onPause();
		waitForDeckLoaderThread();
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		Log.i(AnkiDroidApp.TAG, "DeckPicker - onDestroy()");
		if (mUnmountReceiver != null) {
			unregisterReceiver(mUnmountReceiver);
		}
	}

	@Override
	protected Dialog onCreateDialog(int id) {
		Dialog dialog;
		Resources res = getResources();
		AlertDialog.Builder builder = new AlertDialog.Builder(this);

		switch (id) {
		case DIALOG_NO_SDCARD:
			builder
					.setMessage("The SD card could not be read. Please, turn off USB storage.");
			builder.setPositiveButton("OK", null);
			dialog = builder.create();
			break;

		case DIALOG_USER_NOT_LOGGED_IN:
			builder.setTitle(res.getString(R.string.connection_error_title));
			builder.setIcon(android.R.drawable.ic_dialog_alert);
			builder.setMessage(res
					.getString(R.string.no_user_password_error_message));
			builder.setPositiveButton(res.getString(R.string.log_in),
					new DialogInterface.OnClickListener() {

						@Override
						public void onClick(DialogInterface dialog, int which) {
							Intent myAccount = new Intent(DeckPicker.this,
									MyAccount.class);
							startActivity(myAccount);
						}
					});
			builder.setNegativeButton(res.getString(R.string.cancel), null);
			dialog = builder.create();
			break;

		case DIALOG_NO_CONNECTION:
			builder.setTitle(res.getString(R.string.connection_error_title));
			builder.setIcon(android.R.drawable.ic_dialog_alert);
			builder.setMessage(res.getString(R.string.connection_needed));
			builder.setPositiveButton(res.getString(R.string.ok), null);
			dialog = builder.create();
			break;

		case DIALOG_DELETE_DECK:
			builder.setTitle(res.getString(R.string.delete_deck_title));
			builder.setIcon(android.R.drawable.ic_dialog_alert);
			builder.setMessage(String.format(res.getString(R.string.delete_deck_message), mCurrentDeckFilename));
			builder.setPositiveButton(res.getString(R.string.delete_deck_confirm),
					new DialogInterface.OnClickListener() {

						@Override
						public void onClick(DialogInterface dialog, int which) {
							removeDeck(mCurrentDeckPath);
							mCurrentDeckPath = null;
							mCurrentDeckFilename = null;
						}
					});
			builder.setNegativeButton(res.getString(R.string.cancel),
				new DialogInterface.OnClickListener() {

					@Override
					public void onClick(DialogInterface dialog, int which) {
						mCurrentDeckPath = null;
						mCurrentDeckFilename = null;
					}
				});
			builder.setOnCancelListener(
					new DialogInterface.OnCancelListener() {

						@Override
						public void onCancel(DialogInterface dialog) {
							mCurrentDeckPath = null;
							mCurrentDeckFilename = null;
						}
					});					
			dialog = builder.create();
			break;
		case DIALOG_DELETE_BACKUPS:
			builder.setTitle(res.getString(R.string.backup_manager_title));
			builder.setIcon(android.R.drawable.ic_dialog_alert);
			builder.setMessage(String.format(res.getString(R.string.backup_delete_deck_backups_alert), mCurrentDeckFilename));
			builder.setPositiveButton(res.getString(R.string.delete_deck_confirm),
					new DialogInterface.OnClickListener() {

						@Override
						public void onClick(DialogInterface dialog, int which) {
							if (BackupManager.deleteDeckBackups(mCurrentDeckPath, 0)) {
								Themes.showThemedToast(DeckPicker.this, getResources().getString(R.string.backup_delete_deck_backups, mCurrentDeckFilename), true);
							}
							mCurrentDeckPath = null;
							mCurrentDeckFilename = null;
						}
					});
			builder.setNegativeButton(res.getString(R.string.cancel),
				new DialogInterface.OnClickListener() {

					@Override
					public void onClick(DialogInterface dialog, int which) {
						mCurrentDeckPath = null;
						mCurrentDeckFilename = null;
					}
				});
			builder.setOnCancelListener(
					new DialogInterface.OnCancelListener() {

						@Override
						public void onCancel(DialogInterface dialog) {
							mCurrentDeckPath = null;
							mCurrentDeckFilename = null;
						}
					});					
			dialog = builder.create();
			break;
		case DIALOG_SELECT_STATISTICS_TYPE:
	        builder.setTitle(res.getString(R.string.statistics_type_title));
	        builder.setIcon(android.R.drawable.ic_menu_sort_by_size);
	        builder.setSingleChoiceItems(getResources().getStringArray(R.array.statistics_type_labels), Statistics.TYPE_DUE, mStatisticListener);
	        dialog = builder.create();
			break;
		case DIALOG_SELECT_STATISTICS_PERIOD:
	        builder.setTitle(res.getString(R.string.statistics_period_title));
	        builder.setIcon(android.R.drawable.ic_menu_sort_by_size);
	        builder.setSingleChoiceItems(getResources().getStringArray(R.array.statistics_period_labels), 0, mStatisticListener);
	        dialog = builder.create();
			break;
		case DIALOG_OPTIMIZE_DATABASE:
    		builder.setTitle(res.getString(R.string.optimize_deck_title));
    		builder.setPositiveButton(res.getString(R.string.ok), null);
			builder.setIcon(android.R.drawable.ic_dialog_alert);
			dialog = builder.create();
			break;
		default:
			dialog = null;
		}

		return dialog;
	}
	
	@Override
	protected void onPrepareDialog(int id, Dialog dialog) {
		Resources res = getResources();
		AlertDialog ad = (AlertDialog)dialog;
		switch (id) {
		case DIALOG_DELETE_DECK:
			ad.setMessage(String.format(res.getString(R.string.delete_deck_message), mCurrentDeckFilename));
			break;
		case DIALOG_DELETE_BACKUPS:
			ad.setMessage(String.format(res.getString(R.string.backup_delete_deck_backups_alert), mCurrentDeckFilename));
			break;
		}		
	}


    private DialogInterface.OnClickListener mStatisticListener = new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
			if (mStatisticType == -1 && which != Statistics.TYPE_DECK_SUMMARY) {
				mStatisticType = which;
        		dialog.dismiss();
           		showDialog(DIALOG_SELECT_STATISTICS_PERIOD);
        	} else {
        		dialog.dismiss();
		    	if (mFileList != null && mFileList.length > 0) {
					String[] deckPaths = new String[mFileList.length];
					int i = 0;
			    	for (File file : mFileList) {
			    		deckPaths[i] = file.getAbsolutePath();
			    		i++;
					}
			    	if (mStatisticType == -1) {
			    		mStatisticType = Statistics.TYPE_DECK_SUMMARY;
				    	DeckTask.launchDeckTask(DeckTask.TASK_TYPE_LOAD_STATISTICS, mLoadStatisticsHandler, new DeckTask.TaskData(DeckPicker.this, deckPaths, mStatisticType, 0));			    		
			    	} else {
				    	DeckTask.launchDeckTask(DeckTask.TASK_TYPE_LOAD_STATISTICS, mLoadStatisticsHandler, new DeckTask.TaskData(DeckPicker.this, deckPaths, mStatisticType, which));
			    	}
		    	}
        	}
        }
    };


    @Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);
		int selectedPosition = ((AdapterView.AdapterContextMenuInfo)menuInfo).position;
		mCurrentDeckFilename = mDeckList.get(selectedPosition).get("name");
		menu.setHeaderTitle(mCurrentDeckFilename);
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.contextmenu_deckpicker, menu);
	}

	
    @Override
	public boolean onContextItemSelected(MenuItem item) {
		AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
		waitForDeckLoaderThread();
		
		@SuppressWarnings("unchecked")
		HashMap<String, String> data = (HashMap<String, String>) mDeckListAdapter.getItem(info.position);
		String deckPath = null;
		Deck deck = null;
		switch (item.getItemId()) {
		case R.id.delete_deck:
			mCurrentDeckPath = null;
			mCurrentDeckPath = data.get("filepath");
			showDialog(DIALOG_DELETE_DECK);
			return true;
		case R.id.reset_language:
			resetDeckLanguages(data.get("filepath"));
			return true;
		case R.id.optimize_deck:
			deckPath = data.get("filepath");
			deck = getDeck(deckPath);
	    	DeckTask.launchDeckTask(DeckTask.TASK_TYPE_OPTIMIZE_DECK, mOptimizeDeckHandler, new DeckTask.TaskData(deck, null));
			return true;
		case R.id.download_missing_media:
		    deckPath = data.get("filepath");
		    deck = getDeck(deckPath);
		    Reviewer.setupMedia(deck);
		    Connection.downloadMissingMedia(mDownloadMediaListener, new Connection.Payload(new Object[] {deck}));
		    return true;
		case R.id.remove_backups:
			mCurrentDeckPath = null;
			mCurrentDeckPath = data.get("filepath");
			showDialog(DIALOG_DELETE_BACKUPS);
			return true;
//		case R.id.restore_backups:
//			BackupManager.restoreDeckBackup(DeckPicker.this, data.get("filepath"));
//			return true;
		default:
			return super.onContextItemSelected(item);
		}
	}

	
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event)  {
        if (keyCode == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0) {
        	Log.i(AnkiDroidApp.TAG, "DeckPicker - onBackPressed()");
        	closeDeckPicker(true);
        	return true;
        }
        return super.onKeyDown(keyCode, event);
    }

	// ----------------------------------------------------------------------------
	// CUSTOM METHODS
	// ----------------------------------------------------------------------------

	/**
	 * Registers an intent to listen for ACTION_MEDIA_EJECT notifications. The
	 * intent will call closeExternalStorageFiles() if the external media is
	 * going to be ejected, so applications can clean up any files they have
	 * open.
	 */
	private void registerExternalStorageListener() {
		if (mUnmountReceiver == null) {
			mUnmountReceiver = new BroadcastReceiver() {
				@Override
				public void onReceive(Context context, Intent intent) {
					String action = intent.getAction();
					if (action.equals(Intent.ACTION_MEDIA_UNMOUNTED)) {
						Log
								.i(AnkiDroidApp.TAG,
										"DeckPicker - mUnmountReceiver, Action = Media Unmounted");
						SharedPreferences preferences = PreferenceManager
								.getDefaultSharedPreferences(getBaseContext());
						String deckPath = preferences.getString("deckPath",
								AnkiDroidApp.getStorageDirectory());
						populateDeckList(deckPath);
					} else if (action.equals(Intent.ACTION_MEDIA_MOUNTED)) {
						Log
								.i(AnkiDroidApp.TAG,
										"DeckPicker - mUnmountReceiver, Action = Media Mounted");
						SharedPreferences preferences = PreferenceManager
								.getDefaultSharedPreferences(getBaseContext());
						String deckPath = preferences.getString("deckPath",
								AnkiDroidApp.getStorageDirectory());
						mDeckIsSelected = false;
						setTitleText();
						populateDeckList(deckPath);
					}
				}
			};
			IntentFilter iFilter = new IntentFilter();
			iFilter.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
			iFilter.addAction(Intent.ACTION_MEDIA_MOUNTED);
			iFilter.addDataScheme("file");
			registerReceiver(mUnmountReceiver, iFilter);
		}
	}


	private void closeDeckPicker() {
		closeDeckPicker(false);
	}
	private void closeDeckPicker(boolean backPressed) {
		if (mPrefStartupDeckPicker && backPressed) {
    		setResult(StudyOptions.RESULT_CLOSE);
    		Deck deck = AnkiDroidApp.deck();
    		if (deck != null) {
    			deck.closeDeck();
    		}
    		finish();
		} else {
			finish();
			if (Integer.valueOf(android.os.Build.VERSION.SDK) > 4) {
	    		ActivityTransitionAnimation.slide(this, ActivityTransitionAnimation.LEFT);
	    	}			
		}
	}


	private void handleRestoreDecks(boolean reloadIfEmpty) {
		if (mBrokenDecks.size() != 0) {
			while (true) {
				mCurrentDeckPath = mBrokenDecks.remove(0);
				if (!mAlreadyDealtWith.contains(mCurrentDeckPath) || mBrokenDecks.size() == 0) {
					break;
				}
			}
        	mDeckNotLoadedAlert.setMessage(getResources().getString(R.string.open_deck_failed, new File(mCurrentDeckPath).getName().replace(".anki", ""), BackupManager.BROKEN_DECKS_SUFFIX.replace("/", ""), getResources().getString(R.string.repair_deck)));
			mDeckNotLoadedAlert.show();
		} else if (reloadIfEmpty) {
			if (mRestoredOrDeleted) {
				populateDeckList(mPrefDeckPath);				
			}
		}
	}


	private void enableButtons(boolean enabled) {
		if (enabled) {
			if (!(PrefSettings.getSharedPrefs(getBaseContext()).getInt("walWarning", AnkiDb.NO_WAL_WARNING) == AnkiDb.NO_WAL_WARNING)) {
				mSyncAllButton.setVisibility(View.GONE);
			}
			mDeckpickerButtons.setVisibility(View.VISIBLE);
			mDeckpickerButtons.setAnimation(ViewAnimation.fade(ViewAnimation.FADE_IN, 500, 0)); 
		} else {
			mDeckpickerButtons.setVisibility(View.INVISIBLE);
		}
	}


	private void initDialogs() {
		Resources res = getResources();
		// Sync Log dialog
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle(res.getString(R.string.sync_log_title));
		builder.setPositiveButton(res.getString(R.string.ok), null);
		mSyncLogAlert = builder.create();
		// Upgrade notes dialog
		builder = new AlertDialog.Builder(this);
		builder.setTitle(res.getString(
				R.string.deckpicker_upgrade_notes_title));
		builder.setPositiveButton(res.getString(R.string.ok), null);
		mUpgradeNotesAlert = builder.create();
		builder = new AlertDialog.Builder(this);
        builder.setTitle(res.getString(R.string.deckpicker_download_missing_title));
        builder.setPositiveButton(res.getString(R.string.ok), null);
        mMissingMediaAlert = builder.create();
        mProgressDialog = new ProgressDialog(DeckPicker.this);
        mProgressDialog.setTitle(R.string.deckpicker_download_missing_title);
        mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        mProgressDialog.setMax(100);
        mProgressDialog.setCancelable(false);

        // backup system restore dialog
        builder.setTitle(getResources().getString(R.string.backup_manager_title));
        builder.setIcon(android.R.drawable.ic_dialog_alert);
        builder.setMessage(getResources().getString(R.string.backup_deck_no_space_left));
		builder.setPositiveButton(getResources().getString(R.string.ok), null);
		mNoSpaceLeftAlert = builder.create();

        builder.setTitle(res.getString(R.string.backup_manager_title));
        builder.setIcon(android.R.drawable.ic_dialog_alert);
        builder.setPositiveButton(res.getString(R.string.backup_restore), new Dialog.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {
            	mBackups = BackupManager.getDeckBackups(new File(mCurrentDeckPath));
            	if (mBackups.length == 0) {
            		// show dialog
            		Themes.showThemedToast(DeckPicker.this, getResources().getString(R.string.backup_restore_no_backups), true);
            	} else {
            		CharSequence[] dates = new CharSequence[mBackups.length];
            		for (int i = 0; i < mBackups.length; i++) {
            			dates[i] = mBackups[i].getName().replaceAll(".*-(\\d{4}-\\d{2}-\\d{2}).anki", "$1");
            		}
            		AlertDialog.Builder builder = new AlertDialog.Builder(DeckPicker.this);
            		builder.setTitle(getResources().getString(R.string.backup_restore_select_title))
            			.setIcon(android.R.drawable.ic_input_get)
                    	.setSingleChoiceItems(dates, dates.length, new DialogInterface.OnClickListener(){

							@Override
							public void onClick(DialogInterface dialog, int which) {
								DeckTask.launchDeckTask(DeckTask.TASK_TYPE_RESTORE_DECK, mRestoreDeckHandler, new DeckTask.TaskData(null, new String[] {mCurrentDeckPath, mBackups[which].getPath()}, 0, 0));
								dialog.dismiss();
							}
						}).setCancelable(true).setOnCancelListener(new OnCancelListener() {

							@Override
							public void onCancel(DialogInterface arg0) {
								mDeckNotLoadedAlert.show();
							}
						}).show();
        		}
            }
        });
        builder.setNegativeButton(res.getString(R.string.delete_deck_title), new Dialog.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {
            	Resources res = getResources();
            	AlertDialog.Builder builder = new AlertDialog.Builder(DeckPicker.this);
            	builder.setCancelable(true).setTitle(res.getString(R.string.delete_deck_title))
            		.setIcon(android.R.drawable.ic_dialog_alert)
            		.setMessage(String.format(res.getString(R.string.delete_deck_message), new File(mCurrentDeckPath).getName().replace(".anki", "")))
            		.setPositiveButton(res.getString(R.string.delete_deck_confirm), new DialogInterface.OnClickListener() {

						@Override
						public void onClick(DialogInterface dialog, int which) {
							File file = new File(mCurrentDeckPath);
							boolean deleted = file.delete();
							if (deleted) {
								// remove media directory
								DeckPicker.removeDir(new File(mCurrentDeckPath.replace(".anki", ".media")));

								Log.i(AnkiDroidApp.TAG, "Broken deck " + mCurrentDeckPath + " has been deleted");
							} else {
								Log.e(AnkiDroidApp.TAG, "Broken deck " + mCurrentDeckPath + " could not be deleted");
							}
							Themes.showThemedToast(DeckPicker.this, getResources().getString(R.string.delete_deck_success, new File(mCurrentDeckPath).getName().replace(".anki", "")), true);
							mRestoredOrDeleted = true;
							handleRestoreDecks(true);
						}
					}).setNegativeButton(res.getString(R.string.cancel), new DialogInterface.OnClickListener() {

						@Override
						public void onClick(DialogInterface dialog, int which) {
							mDeckNotLoadedAlert.show();
						}
					}).setOnCancelListener(new DialogInterface.OnCancelListener() {

						@Override
						public void onCancel(DialogInterface dialog) {
							mDeckNotLoadedAlert.show();
						}
					}).show();
						
            }
        });
        builder.setCancelable(true);
        builder.setOnCancelListener(new OnCancelListener() {

			@Override
			public void onCancel(DialogInterface arg0) {
				mAlreadyDealtWith.add(mCurrentDeckPath);
				handleRestoreDecks(true);
			}
		});
        mDeckNotLoadedAlert = builder.create();
	}
	
	
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuItem item;
        SubMenu downloadDeckSubMenu = menu.addSubMenu(Menu.NONE, SUBMENU_DOWNLOAD, Menu.NONE,
                R.string.menu_download_deck);
        downloadDeckSubMenu.setIcon(R.drawable.ic_menu_download);
        downloadDeckSubMenu.add(
                Menu.NONE, MENU_DOWNLOAD_PERSONAL_DECK, Menu.NONE, R.string.menu_download_personal_deck);
        downloadDeckSubMenu.add(Menu.NONE, MENU_DOWNLOAD_SHARED_DECK, Menu.NONE, R.string.menu_download_shared_deck);
        item = menu.add(Menu.NONE, MENU_CREATE_DECK, Menu.NONE, R.string.menu_create_deck);
        item.setIcon(R.drawable.ic_menu_add);
        item = menu.add(Menu.NONE, MENU_PREFERENCES, Menu.NONE, R.string.menu_preferences);
        item.setIcon(R.drawable.ic_menu_preferences);
        item = menu.add(Menu.NONE, MENU_MY_ACCOUNT, Menu.NONE, R.string.menu_my_account);
        item.setIcon(R.drawable.ic_menu_home);
        item = menu.add(Menu.NONE, MENU_ABOUT, Menu.NONE, R.string.menu_about);
        item.setIcon(R.drawable.ic_menu_info_details);
        item = menu.add(Menu.NONE, MENU_FEEDBACK, Menu.NONE, R.string.studyoptions_feedback);
        item.setIcon(R.drawable.ic_menu_send);
        return true;
    }


    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        boolean sdCardAvailable = AnkiDroidApp.isSdCardMounted();
        menu.findItem(SUBMENU_DOWNLOAD).setEnabled(sdCardAvailable);
        menu.findItem(MENU_DOWNLOAD_PERSONAL_DECK).setVisible(sdCardAvailable);
        SharedPreferences preferences = PrefSettings.getSharedPrefs(getBaseContext());
        return true;
    }

    
    /** Handles item selections */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_CREATE_DECK:
                startActivityForResult(new Intent(DeckPicker.this, DeckCreator.class), CREATE_DECK);;
                if (Integer.valueOf(android.os.Build.VERSION.SDK) > 4) {
                    ActivityTransitionAnimation.slide(DeckPicker.this, ActivityTransitionAnimation.RIGHT);
                }
                return true;

            case MENU_ABOUT:
                // int i = 123/0; // Intentional Exception for feedback testing purpose
                startActivity(new Intent(DeckPicker.this, About.class));
                return true;

            case MENU_DOWNLOAD_PERSONAL_DECK:
                openPersonalDeckPicker();
                return true;

            case MENU_DOWNLOAD_SHARED_DECK:
                openSharedDeckPicker();
                return true;

            case MENU_MY_ACCOUNT:
                startActivity(new Intent(DeckPicker.this, MyAccount.class));
                return true;

            case MENU_PREFERENCES:
                startActivityForResult(
                        new Intent(DeckPicker.this, Preferences.class),
                        PREFERENCES_UPDATE);
                return true;

            case MENU_FEEDBACK:
                startActivityForResult(
                        new Intent(DeckPicker.this, Feedback.class),
                        REPORT_FEEDBACK);
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }
    

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);

        if (requestCode == PREFERENCES_UPDATE) {
            if (resultCode == StudyOptions.RESULT_RESTART) {
            	setResult(StudyOptions.RESULT_RESTART);
            	finish();
            } else {
            	SharedPreferences preferences = PrefSettings.getSharedPrefs(getBaseContext());
                if (!mPrefDeckPath.equals(preferences.getString("deckPath", AnkiDroidApp.getStorageDirectory())) || mPrefDeckOrder != Integer.parseInt(preferences.getString("deckOrder", "0"))) {
                    finish();
                    Intent i = new Intent(DeckPicker.this, DeckPicker.class);
                    startActivity(i);
                    if (Integer.valueOf(android.os.Build.VERSION.SDK) > 4) {
                        ActivityTransitionAnimation.slide(this, ActivityTransitionAnimation.NONE);
                    }
                }            	
            }
        } else if ((requestCode == CREATE_DECK || requestCode == DOWNLOAD_PERSONAL_DECK || requestCode == DOWNLOAD_SHARED_DECK) && resultCode == RESULT_OK) {
            finish();
            Intent i = new Intent(DeckPicker.this, DeckPicker.class);
            startActivity(i);
            if (Integer.valueOf(android.os.Build.VERSION.SDK) > 4) {
                ActivityTransitionAnimation.slide(this, ActivityTransitionAnimation.NONE);
            }
        } else if (requestCode == REPORT_FEEDBACK && resultCode == RESULT_OK) {
        }
    }


    private void populateDeckList(String location) {
		Log.i(AnkiDroidApp.TAG, "DeckPicker - populateDeckList");

		if (!location.equals(mPrefDeckPath)) {
		    mPrefDeckPath = location;
		}

		mTotalDueCards = 0;
		mTotalCards = 0;
		setTitleText();
		
		Resources res = getResources();
		int len = 0;
		File[] fileList;

		TreeSet<HashMap<String, String>> tree = new TreeSet<HashMap<String, String>>(new HashMapCompareLoad());

		File dir = new File(mPrefDeckPath);
		fileList = dir.listFiles(new AnkiFilter());

		if (dir.exists() && dir.isDirectory() && fileList != null) {
			len = fileList.length;
			enableButtons(false);
		}

		mFileList = fileList;
		if (len > 0 && fileList != null) {
			Log.i(AnkiDroidApp.TAG, "DeckPicker - populateDeckList, number of anki files = " + len);
			for (File file : fileList) {
				String absPath = file.getAbsolutePath();

				Log.i(AnkiDroidApp.TAG, "DeckPicker - populateDeckList, file:" + file.getName());

				try {
					HashMap<String, String> data = new HashMap<String, String>();
					data.put("name", file.getName().replaceAll(".anki", ""));
					data
							.put("due", res
									.getString(R.string.deckpicker_loaddeck));
					data.put("new", "");
					data.put("mod", String.format("%f", Deck
							.getLastModified(absPath)));
					data.put("filepath", absPath);
                    data.put("showProgress", "true");
                    data.put("rateOfCompletionMat", "-1");
                    data.put("rateOfCompletionAll", "-1");

					tree.add(data);

				} catch (SQLException e) {
					Log.w(AnkiDroidApp.TAG,
							"DeckPicker - populateDeckList, File "
									+ file.getName()
									+ " is not a real anki file");
				}
			}
		    
			Thread thread = new Thread(this);
			thread.start();
		} else {
			Log.i(AnkiDroidApp.TAG, "populateDeckList - No decks found.");
			if (!AnkiDroidApp.isSdCardMounted()) {
				Log.i(AnkiDroidApp.TAG, "populateDeckList - No sd card.");
				setTitle(R.string.deckpicker_title_nosdcard);
				showDialog(DIALOG_NO_SDCARD);
			}

			HashMap<String, String> data = new HashMap<String, String>();
			data.put("name", res.getString(R.string.deckpicker_nodeck));
			data.put("new", "");
			data.put("due", "");
			data.put("mod", "1");
			data.put("showProgress", "false");
            data.put("rateOfCompletionMat", "-1");
            data.put("rateOfCompletionAll", "-1");

			tree.add(data);
		}
		mDeckList.clear();
		mDeckList.addAll(tree);
		mDeckListView.clearChoices();
		mDeckListAdapter.notifyDataSetChanged();
		Log.i(AnkiDroidApp.TAG, "DeckPicker - populateDeckList, Ending");
	}

	@Override
	public void run() {
		Log.i(AnkiDroidApp.TAG, "Thread run - Beginning");

		if (mFileList != null && mFileList.length > 0) {
			mLock.lock();
			try {
				Log.i(AnkiDroidApp.TAG, "Thread run - Inside lock");

				mIsFinished = false;
				int i = 0;
				for (File file : mFileList) {
				    i++;
					// Don't load any more decks if one has already been
					// selected.
					Log.i(AnkiDroidApp.TAG, "Thread run - Before break mDeckIsSelected = " + mDeckIsSelected);
					if (mDeckIsSelected) {
						break;
					}

					String path = file.getAbsolutePath();
					Deck deck;

					// See if we need to upgrade the deck
					int version = 0;
					try {
						version = Deck.getDeckVersion(path);
					} catch (SQLException e) {
						Log.w(AnkiDroidApp.TAG, "Could not open database "
								+ path);
						mBrokenDecks.add(path);
						continue;
					}

					if (version < Deck.DECK_VERSION) {
						Bundle data = new Bundle();
						data.putString("absPath", path);
						data.putInt("msgtype", MSG_UPGRADE_NEEDED);
						data.putInt("version", version);
						data.putString("notes", "");
						Message msg = Message.obtain();
						msg.setData(data);
						mHandler.sendMessage(msg);
					}
					try {
						deck = getDeck(path);
						version = deck.getVersion();
					} catch (SQLException e) {
						Log.w(AnkiDroidApp.TAG, "Could not open database "
								+ path);
						mBrokenDecks.add(path);
						continue;
					}

					Bundle data = new Bundle();
					Message msg = Message.obtain();

					// Check if the upgrade failed
					if (version < Deck.DECK_VERSION) {
						data.putString("absPath", path);
						data.putInt("msgtype", MSG_UPGRADE_FAILURE);
						data.putInt("version", version);
						data.putString("notes", Deck.upgradeNotesToMessages(deck, getResources()));
						closeDeck(deck);
						msg.setData(data);
						mHandler.sendMessage(msg);
					} else {
						int dueCards = deck.getDueCount();
						int totalCards = deck.getCardCount();
						int newCards = deck.getNewCountToday();
						int totalNewCards = deck.getNewCount(mCompletionBarRestrictToActive);
						int matureCards = deck.getMatureCardCount(mCompletionBarRestrictToActive);
						int totalRevCards = deck.getTotalRevFailedCount(mCompletionBarRestrictToActive);
						int totalCardsCompletionBar = totalRevCards + totalNewCards;

						String upgradeNotes = Deck.upgradeNotesToMessages(deck, getResources());
						
						closeDeck(deck);

						data.putString("absPath", path);
						data.putInt("msgtype", MSG_UPGRADE_SUCCESS);
						data.putInt("due", dueCards);
						data.putInt("total", totalCards);
						data.putInt("new", newCards);
						data.putInt("totalNew", totalNewCards);
						data.putString("notes", upgradeNotes);

						int rateOfCompletionMat;
						int rateOfCompletionAll;
						if (totalCardsCompletionBar != 0) {
						    rateOfCompletionMat = (matureCards * 100) / totalCardsCompletionBar;
		                    rateOfCompletionAll = (totalRevCards * 100) / totalCardsCompletionBar; 
						} else {
						    rateOfCompletionMat = 0;
						    rateOfCompletionAll = 0;
						}
						data.putInt("rateOfCompletionMat", rateOfCompletionMat);
                        data.putInt("rateOfCompletionAll", Math.max(0, rateOfCompletionAll - rateOfCompletionMat));
                        if (i == mFileList.length) {
                            data.putBoolean("lastDeck", true);
                        } else {
                            data.putBoolean("lastDeck", false);
                        }
						msg.setData(data);
						
						mTotalDueCards += dueCards;
						mTotalCards += totalCards;
						mTotalTime += Math.max(deck.getETA(), 0);

						mHandler.sendMessage(msg);
					}
				}
				mIsFinished = true;
				mHandler.sendEmptyMessage(0);
				mCondFinished.signal();
			} catch (Exception e) {
				throw new RuntimeException(e);
			} finally {
				mLock.unlock();
			}
		}
	}
	
	
	private void setTitleText(){
		Resources res = getResources();
		String time = res.getQuantityString(R.plurals.deckpicker_title_minutes, mTotalTime, mTotalTime);
		setTitle(res.getQuantityString(R.plurals.deckpicker_title, mTotalDueCards, mTotalDueCards, mTotalCards, time));
	}


	private void resetDeckLanguages(String deckPath) {
		if (MetaDB.resetDeckLanguages(this, deckPath)) {
			Themes.showThemedToast(this, getResources().getString(R.string.contextmenu_deckpicker_reset_reset_message), true);
		}
	}


    public void openPersonalDeckPicker() {
        if (AnkiDroidApp.isUserLoggedIn()) {
            if (AnkiDroidApp.deck() != null)// && sdCardAvailable)
            {
                AnkiDroidApp.deck().closeDeck();
                AnkiDroidApp.setDeck(null);
            }
            startActivityForResult(new Intent(this, PersonalDeckPicker.class), DOWNLOAD_PERSONAL_DECK);
            if (Integer.valueOf(android.os.Build.VERSION.SDK) > 4) {
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
        }
        // deckLoaded = false;
        startActivityForResult(new Intent(this, SharedDeckPicker.class), DOWNLOAD_SHARED_DECK);
        if (Integer.valueOf(android.os.Build.VERSION.SDK) > 4) {
            ActivityTransitionAnimation.slide(this, ActivityTransitionAnimation.RIGHT);
        }
    }


	private void handleDeckSelection(int id) {
		String deckFilename = null;

		waitForDeckLoaderThread();

		@SuppressWarnings("unchecked")
		HashMap<String, String> data = (HashMap<String, String>) mDeckListAdapter
				.getItem(id);
		deckFilename = data.get("filepath");

		if (deckFilename != null) {
			Log.i(AnkiDroidApp.TAG, "Selected " + deckFilename);
			Intent intent = this.getIntent();
			intent.putExtra(StudyOptions.OPT_DB, deckFilename);
			setResult(RESULT_OK, intent);

			closeDeckPicker();
		}
	}

	
	public static boolean removeDir(File dir){
		if (dir.isDirectory()){
			File[] files = dir.listFiles();
			for (File aktFile: files){
				removeDir(aktFile);
			}
		}
		return dir.delete();
	} 


	public Deck getDeck(String filePath) {
		Deck loadedDeck = AnkiDroidApp.deck();
		if (loadedDeck != null && loadedDeck.getDeckPath().equals(filePath)) {
			return loadedDeck;
		} else {
			return Deck.openDeck(filePath, false);			
		}
	}


	public void closeDeck(Deck deck) {
		Deck loadedDeck = AnkiDroidApp.deck();
		if (!(loadedDeck != null && loadedDeck == deck)) {
			deck.closeDeck(false);
		}
		
	}


	private void removeDeck(String deckFilename) {
		if (deckFilename != null) {
			File file = new File(deckFilename);
			boolean deleted = file.delete();
			if (deleted) {
				Log.i(AnkiDroidApp.TAG, "DeckPicker - " + deckFilename + " deleted");
				mDeckIsSelected = false;

				// remove media directory
				String mediaDir = deckFilename.replace(".anki", ".media");
				boolean mediadeleted = removeDir(new File(mediaDir));
				if (mediadeleted) {
					Log.i(AnkiDroidApp.TAG, "DeckPicker - " + mediaDir + " deleted");					
				} else {
					Log.e(AnkiDroidApp.TAG, "Error: Could not delete " + mediaDir);										
				}

				// remove backups
				BackupManager.deleteDeckBackups(deckFilename, 0);
				Themes.showThemedToast(DeckPicker.this, getResources().getString(R.string.delete_deck_success, file.getName().replace(".anki", "")), true);

				populateDeckList(mPrefDeckPath);
			} else {
				Log.e(AnkiDroidApp.TAG, "Error: Could not delete "
						+ deckFilename);
			}
		}
	}

	private void waitForDeckLoaderThread() {
		mDeckIsSelected = true;
		Log
				.i(AnkiDroidApp.TAG,
						"DeckPicker - waitForDeckLoaderThread(), mDeckIsSelected set to true");
		mLock.lock();
		try {
			while (!mIsFinished) {
				mCondFinished.await();
			}
		} catch (InterruptedException e) {
			e.printStackTrace();
		} finally {
			mLock.unlock();
		}
	}

	private CharSequence getSyncLogMessage(
			ArrayList<HashMap<String, String>> decksChangelogs) {
		SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder();
		int len = decksChangelogs.size();
		for (int i = 0; i < len; i++) {
			HashMap<String, String> deckChangelog = decksChangelogs.get(i);
			String deckName = deckChangelog.get("deckName");

			// Append deck name
			spannableStringBuilder.append(deckName);
			// Underline deck name
			spannableStringBuilder.setSpan(new UnderlineSpan(),
					spannableStringBuilder.length() - deckName.length(),
					spannableStringBuilder.length(), 0);
			// Put deck name in bold style
			spannableStringBuilder.setSpan(new StyleSpan(
					android.graphics.Typeface.BOLD), spannableStringBuilder
					.length()
					- deckName.length(), spannableStringBuilder.length(), 0);

			// Append sync message
			spannableStringBuilder.append("\n" + deckChangelog.get("message"));

			// If it is not the last element, add the proper separation
			if (i != (len - 1)) {
				spannableStringBuilder.append("\n\n");
			}
		}

		return spannableStringBuilder;
	}


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
		    		Statistics.showDeckSummary(DeckPicker.this);
		    	} else {
			    	Intent intent = new Intent(DeckPicker.this, com.ichi2.charts.ChartBuilder.class);
			    	startActivity(intent);
			        if (Integer.valueOf(android.os.Build.VERSION.SDK) > 4) {
			            ActivityTransitionAnimation.slide(DeckPicker.this, ActivityTransitionAnimation.DOWN);
			        }	
		    	}
			}
		}

		@Override
		public void onPreExecute() {
            mProgressDialog = ProgressDialog.show(DeckPicker.this, "", getResources()
                    .getString(R.string.calculating_statistics), true);
		}

		@Override
		public void onProgressUpdate(DeckTask.TaskData... values) {
		}
    	
    };


    DeckTask.TaskListener mRestoreDeckHandler = new DeckTask.TaskListener() {

    	@Override
        public void onPreExecute() {
            mProgressDialog = ProgressDialog.show(DeckPicker.this, "", getResources()
                    .getString(R.string.backup_restore_deck), true);
        }


        @Override
        public void onPostExecute(DeckTask.TaskData result) {
			switch (result.getInt()) {
    		case BackupManager.RETURN_DECK_RESTORED:
    			mRestoredOrDeleted = true;
                handleRestoreDecks(true);
                break;    			
    		case BackupManager.RETURN_ERROR:
        		mDeckNotLoadedAlert.show();
        		Themes.showThemedToast(DeckPicker.this, getResources().getString(R.string.backup_restore_error), true);
    			break;
    		case BackupManager.RETURN_NOT_ENOUGH_SPACE:
    			mDeckNotLoadedAlert.show();
    			mNoSpaceLeftAlert.show();
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


    DeckTask.TaskListener mOptimizeDeckHandler = new DeckTask.TaskListener() {

		@Override
		public void onPostExecute(DeckTask.TaskData result) {
            if (mProgressDialog.isShowing()) {
                try {
                    mProgressDialog.dismiss();
                } catch (Exception e) {
                    Log.e(AnkiDroidApp.TAG, "onPostExecute - Dialog dismiss Exception = " + e.getMessage());
                }
            }
            closeDeck(result.getDeck());
    		AlertDialog dialog = (AlertDialog) onCreateDialog(DIALOG_OPTIMIZE_DATABASE);
    		dialog.setMessage(String.format(Utils.ENGLISH_LOCALE, getResources().getString(R.string.optimize_deck_message), Math.round(result.getLong() / 1024)));
    		dialog.show();
		}

		@Override
		public void onPreExecute() {
            mProgressDialog = ProgressDialog.show(DeckPicker.this, "", getResources()
                    .getString(R.string.optimize_deck_dialog), true);
		}

		@Override
		public void onProgressUpdate(DeckTask.TaskData... values) {
		}
    	
    };


    // ----------------------------------------------------------------------------
	// INNER CLASSES
	// ----------------------------------------------------------------------------


    public class AlternatingAdapter extends SimpleAdapter {
        private int[] colors;
    	 
    	    public AlternatingAdapter(Context context, ArrayList<HashMap<String, String>> items, int resource, String[] from, int[] to) {
    	        super(context, items, resource, from, to);
    	    }

    	    @Override
    	    public View getView(int position, View convertView, ViewGroup parent) {
	    	  View view = super.getView(position, convertView, parent);
	    	  Themes.setContentStyle(view, Themes.CALLER_DECKPICKER_DECK);
    	      return view;
    	    }
    }


	public static final class AnkiFilter implements FileFilter {
		@Override
		public boolean accept(File pathname) {
			if (pathname.isFile() && pathname.getName().endsWith(".anki")) {
				return true;
			}
			return false;
		}
	}


	private class HashMapCompareLoad implements
	Comparator<HashMap<String, String>> {
		@Override
		public int compare(HashMap<String, String> object1,
				HashMap<String, String> object2) {
			if (mPrefDeckOrder == ORDER_BY_DATE) {
	    		// Order by last modification date (last deck modified first)
				if (object2.get("mod").compareToIgnoreCase(object1.get("mod")) != 0) {
					return object2.get("mod").compareToIgnoreCase(
							object1.get("mod"));
					// But if there are two decks with the same date of
					// modification, order them in alphabetical order
				} else {
					return object1.get("filepath").compareToIgnoreCase(
							object2.get("filepath"));
				}
	    	} else {
	    		return object1.get("filepath").compareToIgnoreCase(object2.get("filepath"));
	    	}
		}
	}


	private class HashMapCompare implements
	Comparator<HashMap<String, String>> {
		@Override
		public int compare(HashMap<String, String> object1,
				HashMap<String, String> object2) {
		    try {
		    	if (mPrefDeckOrder == ORDER_BY_DUE_CARDS) {
					return - Integer.valueOf(object1.get("dueInt")).compareTo(Integer.valueOf(object2.get("dueInt")));
		    	} else if (mPrefDeckOrder == ORDER_BY_TOTAL_CARDS) {
		    		return - Integer.valueOf(object1.get("total")).compareTo(Integer.valueOf(object2.get("total")));
		    	} else if (mPrefDeckOrder == ORDER_BY_REMAINING_NEW_CARDS) {
		    		return - Integer.valueOf(object1.get("totalNew")).compareTo(Integer.valueOf(object2.get("totalNew")));
				} else {
					return 0;
				}
		    }
		    catch( Exception e ) {
		        return 0;
		    }
		}
	}


	class MyGestureDetector extends SimpleOnGestureListener {	
    	@Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            if (mSwipeEnabled) {
                try {
       				if (e1.getX() - e2.getX() > StudyOptions.sSwipeMinDistance && Math.abs(velocityX) > StudyOptions.sSwipeThresholdVelocity && Math.abs(e1.getY() - e2.getY()) < StudyOptions.sSwipeMaxOffPath) {
       					closeDeckPicker(true);
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
}
