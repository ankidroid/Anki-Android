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
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.SimpleAdapter;
import android.widget.Toast;
import android.widget.AdapterView.AdapterContextMenuInfo;

import com.ichi2.async.Connection;
import com.ichi2.async.Connection.Payload;
import com.tomgibara.android.veecheck.util.PrefSettings;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
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

	/**
	 * Menus
	 */
    private static final int MENU_ABOUT = 0;
    private static final int MENU_CREATE_DECK = 1;
	
	/**
	 * Message types
	 */
	private static final int MSG_UPGRADE_NEEDED = 0;
	private static final int MSG_UPGRADE_SUCCESS = 1;
	private static final int MSG_UPGRADE_FAILURE = 2;

	private DeckPicker mSelf;

	private ProgressDialog mProgressDialog;
	private AlertDialog mSyncLogAlert;
	private AlertDialog mUpgradeNotesAlert;
	private LinearLayout mSyncAllBar;
	private Button mSyncAllButton;
	private Button mStatisticsAllButton;

	private SimpleAdapter mDeckListAdapter;
	private ArrayList<HashMap<String, String>> mDeckList;
	private ListView mDeckListView;

	private File[] mFileList;

	private ReentrantLock mLock = new ReentrantLock();
	private Condition mCondFinished = mLock.newCondition();

	private boolean mIsFinished = true;
	private boolean mDeckIsSelected = false;

	private BroadcastReceiver mUnmountReceiver = null;

	private String mRemoveDeckFilename = null;
	private String mRemoveDeckPath = null;

	private int mTotalDueCards = 0;
	private int mTotalCards = 0;
	private int mTotalTime = 0;

	int mStatisticType;
	int mLoadingFinished;

	boolean mCompletionBarRestrictToActive = true; // set this to true in order to calculate completion bar only for active cards

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

			if (msgtype == DeckPicker.MSG_UPGRADE_NEEDED) {
				dueString = res.getString(R.string.deckpicker_upgrading);
				newString = "";
				showProgress = "true";
			} else if (msgtype == DeckPicker.MSG_UPGRADE_FAILURE) {
				dueString = "Upgrade failed!";
				newString = "";
				showProgress = "false";
			} else if (msgtype == DeckPicker.MSG_UPGRADE_SUCCESS) {
			    int due = data.getInt("due");
				dueString = res.getQuantityString(R.plurals.deckpicker_due, due, due, data.getInt("total"));
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
				}
			}

			mDeckListAdapter.notifyDataSetChanged();
			Log.i(AnkiDroidApp.TAG, "DeckPicker - mDeckList notified of changes");
			setTitleText();
			enableButtons(mLoadingFinished == 0);
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
			SharedPreferences preferences = PrefSettings.getSharedPrefs(getBaseContext());
			populateDeckList(preferences.getString("deckPath", AnkiDroidApp.getStorageDirectory()));
            mSyncAllButton.setClickable(true);
		}
	};

	// ----------------------------------------------------------------------------
	// ANDROID METHODS
	// ----------------------------------------------------------------------------

	/** Called when the activity is first created. */
	@Override
	protected void onCreate(Bundle savedInstanceState) throws SQLException {
		Log.i(AnkiDroidApp.TAG, "DeckPicker - onCreate");
		super.onCreate(savedInstanceState);

		setTitleText();
		
		mSelf = this;
		setContentView(R.layout.deck_picker);

		registerExternalStorageListener();
		initDialogs();

		mSyncAllBar = (LinearLayout) findViewById(R.id.sync_all_bar);
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
		mDeckListAdapter = new SimpleAdapter(this, mDeckList,
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
                    int mScreenWidth = mDeckListView.getWidth();
                    LinearLayout.LayoutParams lparam = new LinearLayout.LayoutParams(0, 0);
                    lparam.width = (int) (mScreenWidth * Integer.parseInt(text) / 100);
                    lparam.height = 2;
                    view.setLayoutParams(lparam);
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
		populateDeckList(preferences.getString("deckPath", AnkiDroidApp
				.getStorageDirectory()));
		
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
			builder.setMessage(String.format(res.getString(R.string.delete_deck_message), mRemoveDeckFilename));
			builder.setPositiveButton(res.getString(R.string.delete_deck_confirm),
					new DialogInterface.OnClickListener() {

						@Override
						public void onClick(DialogInterface dialog, int which) {
							removeDeck(mRemoveDeckPath);
							mRemoveDeckPath = null;
							mRemoveDeckFilename = null;
						}
					});
			builder.setNegativeButton(res.getString(R.string.cancel),
				new DialogInterface.OnClickListener() {

					@Override
					public void onClick(DialogInterface dialog, int which) {
						mRemoveDeckPath = null;
						mRemoveDeckFilename = null;
					}
				});
			builder.setOnCancelListener(
					new DialogInterface.OnCancelListener() {

						@Override
						public void onCancel(DialogInterface dialog) {
							mRemoveDeckPath = null;
							mRemoveDeckFilename = null;
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
		switch (id) {
		case DIALOG_DELETE_DECK:
			AlertDialog ad = (AlertDialog)dialog;
			ad.setMessage(String.format(res.getString(R.string.delete_deck_message), mRemoveDeckFilename));
		}		
	}


    private DialogInterface.OnClickListener mStatisticListener = new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
			if (mStatisticType == -1) {
				mStatisticType = which;
        		dialog.dismiss();
        		showDialog(DIALOG_SELECT_STATISTICS_PERIOD);
        	} else {
        		dialog.dismiss();
		    	Resources res = getResources();
		    	if (mFileList != null && mFileList.length > 0) {
					String[] deckPaths = new String[mFileList.length];
					int i = 0;
			    	for (File file : mFileList) {
			    		deckPaths[i] = file.getAbsolutePath();
			    		i++;
					}
			    	DeckTask.launchDeckTask(DeckTask.TASK_TYPE_LOAD_STATISTICS, mLoadStatisticsHandler, new DeckTask.TaskData(DeckPicker.this, deckPaths, mStatisticType, which));
		    	}
        	}
        }
    };


    @Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);
		int selectedPosition = ((AdapterView.AdapterContextMenuInfo)menuInfo).position;
		mRemoveDeckFilename = mDeckList.get(selectedPosition).get("name");
		menu.setHeaderTitle(mRemoveDeckFilename);
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.contextmenu_deckpicker, menu);
	}

	
	@Override
	public boolean onContextItemSelected(MenuItem item) {
		AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
		waitForDeckLoaderThread();
		
		@SuppressWarnings("unchecked")
		HashMap<String, String> data = (HashMap<String, String>) mDeckListAdapter.getItem(info.position);
		switch (item.getItemId()) {
		case R.id.delete_deck:
			mRemoveDeckPath = null;
			mRemoveDeckPath = data.get("filepath");
			showDialog(DIALOG_DELETE_DECK);
			return true;
		case R.id.reset_language:
			resetDeckLanguages(data.get("filepath"));
			return true;
		case R.id.optimize_deck:
			String deckPath = data.get("filepath");
			Deck deck = Deck.openDeck(deckPath);
	    	DeckTask.launchDeckTask(DeckTask.TASK_TYPE_OPTIMIZE_DECK, mOptimizeDeckHandler, new DeckTask.TaskData(deck, null));
			return true;
		default:
			return super.onContextItemSelected(item);
		}
	}

	
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event)  {
        if (keyCode == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0) {
        	Log.i(AnkiDroidApp.TAG, "DeckPicker - onBackPressed()");
        	closeDeckPicker();
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

	
	private void closeDeckPicker () {
    	finish();
    	if (Integer.valueOf(android.os.Build.VERSION.SDK) > 4) {
    		MyAnimation.slide(this, MyAnimation.LEFT);
    	}
	}


	private void enableButtons(boolean enabled) {
		mSyncAllButton.setEnabled(enabled);
		mStatisticsAllButton.setEnabled(enabled);		
	}


	private void initDialogs() {
		// Sync Log dialog
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle(getResources().getString(R.string.sync_log_title));
		builder.setPositiveButton(getResources().getString(R.string.ok), null);
		mSyncLogAlert = builder.create();
		// Upgrade notes dialog
		builder = new AlertDialog.Builder(this);
		builder.setTitle(getResources().getString(
				R.string.deckpicker_upgrade_notes_title));
		builder.setPositiveButton(getResources().getString(R.string.ok), null);
		mUpgradeNotesAlert = builder.create();
	}
	
	
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuItem item;
        item = menu.add(Menu.NONE, MENU_CREATE_DECK, Menu.NONE, R.string.menu_create_deck);
        item.setIcon(R.drawable.ic_menu_add);
        item = menu.add(Menu.NONE, MENU_ABOUT, Menu.NONE, R.string.menu_about);
        item.setIcon(R.drawable.ic_menu_info_details);
        return true;
    }

    
    /** Handles item selections */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_CREATE_DECK:
                startActivity(new Intent(DeckPicker.this, DeckCreator.class));;
                if (Integer.valueOf(android.os.Build.VERSION.SDK) > 4) {
                    MyAnimation.slide(DeckPicker.this, MyAnimation.RIGHT);
                }
                return true;

            case MENU_ABOUT:
                startActivity(new Intent(DeckPicker.this, About.class));
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }
    
	private void populateDeckList(String location) {
		Log.i(AnkiDroidApp.TAG, "DeckPicker - populateDeckList");

		mTotalDueCards = 0;
		mTotalCards = 0;
		setTitleText();
		
		Resources res = getResources();
		int len = 0;
		File[] fileList;
		TreeSet<HashMap<String, String>> tree = new TreeSet<HashMap<String, String>>(
				new HashMapCompare());

		File dir = new File(location);
		fileList = dir.listFiles(new AnkiFilter());

		if (dir.exists() && dir.isDirectory() && fileList != null) {
			len = fileList.length;
			mLoadingFinished = len;
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
                    data.put("rateOfCompletionMat", "0");
                    data.put("rateOfCompletionAll", "0");

					tree.add(data);

				} catch (SQLException e) {
					Log.w(AnkiDroidApp.TAG,
							"DeckPicker - populateDeckList, File "
									+ file.getName()
									+ " is not a real anki file");
				}
			}
		    
	        // Show "Sync all" button only if sync is enabled.
	        SharedPreferences preferences = PrefSettings.getSharedPrefs(getBaseContext());
	        Log.d(AnkiDroidApp.TAG, "syncEnabled=" + preferences.getBoolean("syncEnabled", false));
	        if (!preferences.getBoolean("syncEnabled", false)) {
	            mSyncAllButton.setVisibility(View.GONE);
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
            data.put("rateOfCompletionMat", "0");
            data.put("rateOfCompletionAll", "0");

			tree.add(data);

			mSyncAllBar.setVisibility(View.GONE);
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
				for (File file : mFileList) {

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
						deck = Deck.openDeck(path);
						version = deck.getVersion();
					} catch (SQLException e) {
						Log.w(AnkiDroidApp.TAG, "Could not open database "
								+ path);
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
						deck.closeDeck();
						msg.setData(data);
						mHandler.sendMessage(msg);
					} else {
						int dueCards = deck.getDueCount();
						int totalCards = deck.getCardCount();
						int newCards = deck.getNewCountToday();
						int totalNewCards = deck.getNewCount(mCompletionBarRestrictToActive);
						int matureCards = deck.getMatureCardCount(mCompletionBarRestrictToActive);
						int totalCardsCompletionBar = deck.getCardCount(mCompletionBarRestrictToActive);
						String upgradeNotes = Deck.upgradeNotesToMessages(deck, getResources());
						deck.closeDeck();

						data.putString("absPath", path);
						data.putInt("msgtype", MSG_UPGRADE_SUCCESS);
						data.putInt("due", dueCards);
						data.putInt("total", totalCards);
						data.putInt("new", newCards);
						data.putString("notes", upgradeNotes);

						int rateOfCompletionMat;
						int rateOfCompletionAll;
						if (totalCardsCompletionBar != 0) {
						    rateOfCompletionMat = (matureCards * 100) / totalCardsCompletionBar;
		                    rateOfCompletionAll = ((totalCardsCompletionBar - totalNewCards) * 100) / totalCardsCompletionBar; 
						} else {
						    rateOfCompletionMat = 0;
						    rateOfCompletionAll = 0;
						}
						data.putInt("rateOfCompletionMat", rateOfCompletionMat);
                        data.putInt("rateOfCompletionAll", Math.max(0, rateOfCompletionAll - rateOfCompletionMat));
						msg.setData(data);
						
						mTotalDueCards += dueCards;
						mTotalCards += totalCards;
						mTotalTime += Math.max(deck.getStats(Stats.TYPE_ETA)[0] / 60, 0);
						mLoadingFinished--;

						mHandler.sendMessage(msg);
					}
				}
				mIsFinished = true;
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
            Toast successReport = 
                Toast.makeText(this, 
                        getResources().getString(R.string.contextmenu_deckpicker_reset_reset_message), Toast.LENGTH_SHORT);
            successReport.show();
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

	
	public boolean removeDir(File dir){
		if (dir.isDirectory()){
			File[] files = dir.listFiles();
			for (File aktFile: files){
				removeDir(aktFile);
			}
		}
		return dir.delete();
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
				
				SharedPreferences preferences = PrefSettings.getSharedPrefs(getBaseContext());
				populateDeckList(preferences.getString("deckPath", AnkiDroidApp.getStorageDirectory()));
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
		    	Intent intent = new Intent(DeckPicker.this, com.ichi2.charts.ChartBuilder.class);
		    	startActivity(intent);
		        if (Integer.valueOf(android.os.Build.VERSION.SDK) > 4) {
		            MyAnimation.slide(DeckPicker.this, MyAnimation.DOWN);
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
            result.getDeck().closeDeck();
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

	private static final class AnkiFilter implements FileFilter {
		@Override
		public boolean accept(File pathname) {
			if (pathname.isFile() && pathname.getName().endsWith(".anki")) {
				return true;
			}
			return false;
		}
	}

	private static final class HashMapCompare implements
			Comparator<HashMap<String, String>> {
		@Override
		public int compare(HashMap<String, String> object1,
				HashMap<String, String> object2) {
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
		}
	}


    class MyGestureDetector extends SimpleOnGestureListener {	
    	@Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            if (mSwipeEnabled) {
                try {
       				if (e1.getX() - e2.getX() > StudyOptions.sSwipeMinDistance && Math.abs(velocityX) > StudyOptions.sSwipeThresholdVelocity && Math.abs(e1.getY() - e2.getY()) < StudyOptions.sSwipeMaxOffPath) {
       					closeDeckPicker();
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
