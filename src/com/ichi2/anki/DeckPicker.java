/****************************************************************************************
 * Copyright (c) 2009 Andrew Dubya <andrewdubya@gmail.com>                              *
 * Copyright (c) 2009 Nicolas Raoul <nicolas.raoul@gmail.com>                           *
 * Copyright (c) 2009 Edu Zamora <edu.zasu@gmail.com>                                   *
 * Copyright (c) 2009 Daniel Svard <daniel.svard@gmail.com>                             *
 * Copyright (c) 2010 Norbert Nagold <norbert.nagold@gmail.com>                         *
 * Copyright (c) 2014 Timothy Rae <perceptualchaos2@gmail.com>
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

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.res.Resources;
import android.database.SQLException;
import android.graphics.PixelFormat;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.view.Window;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;

import com.ichi2.anim.ActivityTransitionAnimation;
import com.ichi2.anki.dialogs.DatabaseErrorDialog;
import com.ichi2.anki.dialogs.DeckPickerBackupNoSpaceLeftDialog;
import com.ichi2.anki.dialogs.DeckPickerConfirmDeleteDeckDialog;
import com.ichi2.anki.dialogs.DeckPickerContextMenu;
import com.ichi2.anki.dialogs.DeckPickerNoSpaceLeftDialog;
import com.ichi2.anki.dialogs.ImportDialog;
import com.ichi2.anki.dialogs.MediaCheckDialog;
import com.ichi2.anki.dialogs.SyncErrorDialog;
import com.ichi2.anki.receiver.SdCardReceiver;
import com.ichi2.anki.stats.AnkiStatsTaskHandler;
import com.ichi2.async.Connection;
import com.ichi2.async.Connection.OldAnkiDeckFilter;
import com.ichi2.async.Connection.Payload;
import com.ichi2.async.DeckTask;
import com.ichi2.async.DeckTask.TaskData;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Utils;
import com.ichi2.themes.StyledDialog;
import com.ichi2.themes.StyledOpenCollectionDialog;
import com.ichi2.themes.StyledProgressDialog;
import com.ichi2.themes.Themes;
import com.ichi2.widget.WidgetStatus;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;

public class DeckPicker extends NavigationDrawerActivity implements StudyOptionsFragment.OnStudyOptionsReloadListener,
        DatabaseErrorDialog.DatabaseErrorDialogListener, SyncErrorDialog.SyncErrorDialogListener,
        ImportDialog.ImportDialogListener, MediaCheckDialog.MediaCheckDialogListener {

    public static final int CRAM_DECK_FRAGMENT = -1;

    public static final String UPGRADE_OLD_COLLECTION_RENAME = "oldcollection.apkg";
    public static final String IMPORT_REPLACE_COLLECTION_NAME = "collection.apkg";

    private String mImportPath;
    private DialogHandler mHandler;

    public static final String EXTRA_START = "start";
    public static final String EXTRA_DECK_ID = "deckId";
    public static final int EXTRA_START_NOTHING = 0;
    public static final int EXTRA_START_REVIEWER = 1;
    public static final int EXTRA_START_DECKPICKER = 2;
    public static final int EXTRA_DB_ERROR = 3;

    public static final int RESULT_MEDIA_EJECTED = 202;
    public static final int RESULT_DB_ERROR = 203;
    public static final int RESULT_RESTART = 204;

    /**
     * Handler messages
     */
    private static final int MSG_SHOW_COLLECTION_LOADING_ERROR_DIALOG = 0;
    private static final int MSG_SHOW_COLLECTION_IMPORT_REPLACE_DIALOG = 1;
    private static final int MSG_SHOW_COLLECTION_IMPORT_ADD_DIALOG = 2;

    /**
     * Available options performed by other activities
     */
    // private static final int PREFERENCES_UPDATE = 0;
    // private static final int DOWNLOAD_SHARED_DECK = 3;
    public static final int REPORT_FEEDBACK = 4;
    // private static final int LOG_IN_FOR_DOWNLOAD = 5;
    private static final int LOG_IN_FOR_SYNC = 6;
    // private static final int STUDYOPTIONS = 7;
    private static final int SHOW_INFO_WELCOME = 8;
    private static final int SHOW_INFO_NEW_VERSION = 9;
    private static final int REPORT_ERROR = 10;
    public static final int SHOW_STUDYOPTIONS = 11;
    private static final int ADD_NOTE = 12;
    // private static final int LOG_IN = 13;
    private static final int BROWSE_CARDS = 14;
    private static final int ADD_SHARED_DECKS = 15;
    private static final int LOG_IN_FOR_SHARED_DECK = 16;
    private static final int ADD_CRAM_DECK = 17;
    private static final int SHOW_INFO_UPGRADE_DECKS = 18;
    private static final int REQUEST_REVIEW = 19;

    private StyledProgressDialog mProgressDialog;
    private StyledOpenCollectionDialog mNotMountedDialog;

    private int mSyncMediaUsn = 0;

    private SimpleAdapter mDeckListAdapter;
    private ArrayList<HashMap<String, String>> mDeckList;
    private ListView mDeckListView;

    private BroadcastReceiver mUnmountReceiver = null;

    private String mPrefDeckPath = null;
    private long mContextMenuDid;

    private EditText mDialogEditText;

    int mStatisticType;

    boolean mCompletionBarRestrictToActive = false; // set this to true in order to calculate completion bar only for
                                                    // active cards

    // ----------------------------------------------------------------------------
    // LISTENERS
    // ----------------------------------------------------------------------------

    private AdapterView.OnItemClickListener mDeckSelHandler = new AdapterView.OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> parent, View v, int p, long id) {
            handleDeckSelection(p);
        }
    };

    DeckTask.TaskListener mLoadCountsHandler = new DeckTask.TaskListener() {

        @SuppressWarnings("unchecked")
        @Override
        public void onPostExecute(DeckTask.TaskData result) {
            if (result == null) {
                return;
            }
            Object[] res = result.getObjArray();
            updateDecksList((TreeSet<Object[]>) res[0], (Integer) res[1], (Integer) res[2]);
            dismissOpeningCollectionDialog();
            try {
                // Ensure we have the correct deck selected in the deck list after we have updated it. Check first
                // if the collection is open since it might have been closed before this task completes.
                if (getCol() != null) {
                    setSelectedDeck(getCol().getDecks().current().getLong("id"));
                }
            } catch (JSONException e) {
                throw new RuntimeException();
            }
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

    DeckTask.TaskListener mImportAddListener = new DeckTask.TaskListener() {
        @SuppressWarnings("unchecked")
        @Override
        public void onPostExecute(DeckTask.TaskData result) {
            String message = "";
            if (mProgressDialog != null && mProgressDialog.isShowing()) {
                mProgressDialog.dismiss();
            }
            if (result.getBoolean()) {
                Resources res = getResources();
                int count = result.getInt();
                if (count < 0) {
                    if (count == -2) {
                        message = res.getString(R.string.import_log_no_apkg);
                    } else {
                        message = res.getString(R.string.import_log_error);
                    }
                    showLogDialog(message, true);
                } else {
                    message = res.getString(R.string.import_log_success, count);
                    showLogDialog(message);
                    Object[] info = result.getObjArray();
                    updateDecksList((TreeSet<Object[]>) info[0], (Integer) info[1], (Integer) info[2]);
                }
            } else {
                handleDbError();
            }
            // reset import path so that it's not incorrectly imported next time Activity starts
            mImportPath = null;
        }


        @Override
        public void onPreExecute() {
            if (mProgressDialog == null || !mProgressDialog.isShowing()) {
                mProgressDialog = StyledProgressDialog.show(DeckPicker.this,
                        getResources().getString(R.string.import_title),
                        getResources().getString(R.string.import_importing), true, false);
            }
        }


        @Override
        public void onProgressUpdate(DeckTask.TaskData... values) {
            mProgressDialog.setMessage(values[0].getString());
        }


        @Override
        public void onCancelled() {
        }
    };

    DeckTask.TaskListener mImportReplaceListener = new DeckTask.TaskListener() {
        @SuppressWarnings("unchecked")
        @Override
        public void onPostExecute(DeckTask.TaskData result) {
            if (mProgressDialog != null && mProgressDialog.isShowing()) {
                mProgressDialog.dismiss();
            }
            Resources res = getResources();
            if (result.getBoolean()) {
                int code = result.getInt();
                if (code == -2) {
                    // not a valid apkg file
                    showLogDialog(res.getString(R.string.import_log_no_apkg));
                }
                Object[] info = result.getObjArray();
                updateDecksList((TreeSet<Object[]>) info[0], (Integer) info[1], (Integer) info[2]);
                dismissOpeningCollectionDialog();
            } else {
                showLogDialog(res.getString(R.string.import_log_no_apkg), true);
            }
            // reset import path so that it's not incorrectly imported next time Activity starts
            mImportPath = null;
        }


        @Override
        public void onPreExecute() {
            if (mProgressDialog == null || !mProgressDialog.isShowing()) {
                mProgressDialog = StyledProgressDialog.show(DeckPicker.this,
                        getResources().getString(R.string.import_title),
                        getResources().getString(R.string.import_importing), true, false);
            }
        }


        @Override
        public void onProgressUpdate(DeckTask.TaskData... values) {
            mProgressDialog.setMessage(values[0].getString());
        }


        @Override
        public void onCancelled() {
        }
    };

    DeckTask.TaskListener mExportListener = new DeckTask.TaskListener() {

        @Override
        public void onPreExecute() {
            mProgressDialog = StyledProgressDialog.show(DeckPicker.this, "",
                    getResources().getString(R.string.export_in_progress), true);
        }


        @Override
        public void onPostExecute(DeckTask.TaskData result) {
            if (mProgressDialog != null && mProgressDialog.isShowing()) {
                mProgressDialog.dismiss();
            }
            if (result.getBoolean()) {
                showLogDialog(getResources().getString(R.string.export_successful), true);
            } else {
                Themes.showThemedToast(DeckPicker.this, getResources().getString(R.string.export_unsuccessful), true);
            }
        }


        @Override
        public void onProgressUpdate(TaskData... values) {
        }


        @Override
        public void onCancelled() {
        }
    };


    // ----------------------------------------------------------------------------
    // ANDROID ACTIVITY METHODS
    // ----------------------------------------------------------------------------

    /** Called when the activity is first created. */
    @Override
    protected void onCreate(Bundle savedInstanceState) throws SQLException {
        Log.i(AnkiDroidApp.TAG, "DeckPicker - onCreate");
        // Get the path to the apkg file if started via VIEW intent
        Intent intent = getIntent();
        if (intent.getData() != null) {
            mImportPath = getIntent().getData().getEncodedPath();
        }
        mHandler = new DialogHandler(this);
        // need to start this here in order to avoid showing deckpicker before splashscreen
        if (!AnkiDroidApp.colIsOpen()) {
            showOpeningCollectionDialog();
        }

        Themes.applyTheme(this);
        super.onCreate(savedInstanceState);

        setTitle(getResources().getString(R.string.app_name));

        SharedPreferences preferences = restorePreferences();

        View mainView = getLayoutInflater().inflate(R.layout.deck_picker, null);
        setContentView(mainView);

        // check, if tablet layout
        View studyoptionsFrame = findViewById(R.id.studyoptions_fragment);
        // set protected variable from NavigationDrawerActivity
        mFragmented = studyoptionsFrame != null && studyoptionsFrame.getVisibility() == View.VISIBLE;

        Themes.setContentStyle(mFragmented ? mainView : mainView.findViewById(R.id.deckpicker_view),
                Themes.CALLER_DECKPICKER);

        registerExternalStorageListener();

        // create inherited navigation drawer layout here so that it can be used by parent class
        initNavigationDrawer(mainView);
        if (savedInstanceState == null) {
            selectNavigationItem(DRAWER_DECK_PICKER);
        }
        // open the drawer on startup if it's never been opened voluntarily by the user (as per Android guidelines)
        if (!intent.getBooleanExtra("viaNavigationDrawer", false)
                && !preferences.getBoolean("navDrawerHasBeenOpened", false)) {
            getDrawerLayout().openDrawer(Gravity.LEFT);
        }

        mDeckList = new ArrayList<HashMap<String, String>>();
        mDeckListView = (ListView) findViewById(R.id.files);
        mDeckListAdapter = new SimpleAdapter(this, mDeckList, R.layout.deck_item, new String[] { "name", "new", "lrn",
                "rev", // "complMat", "complAll",
                "sep", "dyn" }, new int[] { R.id.DeckPickerName, R.id.deckpicker_new, R.id.deckpicker_lrn,
                R.id.deckpicker_rev, // R.id.deckpicker_bar_mat, R.id.deckpicker_bar_all,
                R.id.deckpicker_deck, R.id.DeckPickerName });
        mDeckListAdapter.setViewBinder(new SimpleAdapter.ViewBinder() {
            @Override
            public boolean setViewValue(View view, Object data, String text) {
                if (view.getId() == R.id.deckpicker_deck) {
                    if (text.equals("top")) {
                        view.setBackgroundResource(R.drawable.white_deckpicker_top);
                        return true;
                    } else if (text.equals("bot")) {
                        view.setBackgroundResource(R.drawable.white_deckpicker_bottom);
                        return true;
                    } else if (text.equals("ful")) {
                        view.setBackgroundResource(R.drawable.white_deckpicker_full);
                        return true;
                    } else if (text.equals("cen")) {
                        view.setBackgroundResource(R.drawable.white_deckpicker_center);
                        return true;
                    }
                } else if (view.getId() == R.id.DeckPickerName) {
                    if (text.equals("d0")) {
                        ((TextView) view).setTextColor(getResources().getColor(R.color.non_dyn_deck));
                        return true;
                    } else if (text.equals("d1")) {
                        ((TextView) view).setTextColor(getResources().getColor(R.color.dyn_deck));
                        return true;
                    }
                } else if (view.getId() == R.id.deckpicker_new) {
                    // Set the right color, light gray or blue.
                    ((TextView) view).setTextColor((text.equals("0")) ? getResources().getColor(R.color.zero_count)
                            : getResources().getColor(R.color.new_count));
                    return false; // Let SimpleAdapter take care of binding the number to the TextView.
                } else if (view.getId() == R.id.deckpicker_lrn) {
                    // ... or red.
                    ((TextView) view).setTextColor((text.equals("0")) ? getResources().getColor(R.color.zero_count)
                            : getResources().getColor(R.color.learn_count));
                    return false;
                } else if (view.getId() == R.id.deckpicker_rev) {
                    // ... or green.
                    ((TextView) view).setTextColor((text.equals("0")) ? getResources().getColor(R.color.zero_count)
                            : getResources().getColor(R.color.review_count));
                    return false;
                }
                return false;
            }
        });
        mDeckListView.setOnItemClickListener(mDeckSelHandler);
        mDeckListView.setOnItemLongClickListener(new OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> adapterView, View view, int position, long id) {
                if (!AnkiDroidApp.colIsOpen() || mDeckList == null || mDeckList.size() == 0) {
                    return true;
                }
                mContextMenuDid = Long.parseLong(mDeckList.get(position).get("did"));
                String deckName = getCol().getDecks().name(mContextMenuDid);
                boolean isCollapsed = getCol().getDecks().get(mContextMenuDid).optBoolean("collapsed", false);
                showDialogFragment(DeckPickerContextMenu.newInstance(deckName, isCollapsed));
                return true;
            }
        });
        mDeckListView.setAdapter(mDeckListAdapter);

        if (mFragmented) {
            mDeckListView.setChoiceMode(AbsListView.CHOICE_MODE_SINGLE);
        }

        if (getCol() == null) {
            // Show splash screen and load collection if it's null
            showStartupScreensAndDialogs(preferences, 0);
        } else {
            // Otherwise just update the deck list
            loadCounts();
        }
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.deck_picker, menu);
        boolean sdCardAvailable = AnkiDroidApp.isSdCardMounted();
        menu.findItem(R.id.action_sync).setEnabled(sdCardAvailable);
        menu.findItem(R.id.action_new_deck).setEnabled(sdCardAvailable);
        menu.findItem(R.id.action_new_filtered_deck).setEnabled(sdCardAvailable);
        menu.findItem(R.id.action_check_database).setEnabled(sdCardAvailable);
        menu.findItem(R.id.action_check_media).setEnabled(sdCardAvailable);
        return super.onCreateOptionsMenu(menu);
    }


    @Override
    public boolean onMenuOpened(int feature, Menu menu) {
        AnkiDroidApp.getCompat().invalidateOptionsMenu(this);
        return super.onMenuOpened(feature, menu);
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // The action bar home/up action should open or close the drawer.
        // ActionBarDrawerToggle will take care of this.
        if (getDrawerToggle().onOptionsItemSelected(item)) {
            return true;
        }

        Resources res = getResources();
        switch (item.getItemId()) {

            case R.id.action_sync:
                sync();
                return true;

            case R.id.action_add_note_from_deck_picker:
                addNote();
                return true;

            case R.id.action_shared_decks:
                if (getCol() != null) {
                    addSharedDeck();
                }
                return true;

            case R.id.action_new_deck:
                StyledDialog.Builder builder2 = new StyledDialog.Builder(DeckPicker.this);
                builder2.setTitle(res.getString(R.string.new_deck));
                mDialogEditText = new EditText(DeckPicker.this);
                // mDialogEditText.setFilters(new InputFilter[] { mDeckNameFilter });
                builder2.setView(mDialogEditText, false, false);
                builder2.setPositiveButton(res.getString(R.string.create), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String deckName = mDialogEditText.getText().toString()
                                .replaceAll("[\'\"\\n\\r\\[\\]\\(\\)]", "");
                        Log.i(AnkiDroidApp.TAG, "Creating deck: " + deckName);
                        getCol().getDecks().id(deckName, true);
                        loadCounts();
                    }
                });
                builder2.setNegativeButton(res.getString(R.string.dialog_cancel), null);
                builder2.create().show();
                return true;

            case R.id.action_import:
                showImportDialog(ImportDialog.DIALOG_IMPORT_HINT);
                return true;

            case R.id.action_new_filtered_deck:
                StyledDialog.Builder builder3 = new StyledDialog.Builder(DeckPicker.this);
                builder3.setTitle(res.getString(R.string.new_deck));
                mDialogEditText = new EditText(DeckPicker.this);
                ArrayList<String> names = getCol().getDecks().allNames();
                int n = 1;
                String cramDeckName = "Cram 1";
                while (names.contains(cramDeckName)) {
                    n++;
                    cramDeckName = "Cram " + n;
                }
                mDialogEditText.setText(cramDeckName);
                // mDialogEditText.setFilters(new InputFilter[] { mDeckNameFilter });
                builder3.setView(mDialogEditText, false, false);
                builder3.setPositiveButton(res.getString(R.string.create), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        long id = getCol().getDecks().newDyn(mDialogEditText.getText().toString());
                        openStudyOptions(id, new Bundle());
                    }
                });
                builder3.setNegativeButton(res.getString(R.string.dialog_cancel), null);
                builder3.create().show();
                return true;

            case R.id.action_check_database:
                showDatabaseErrorDialog(DatabaseErrorDialog.DIALOG_CONFIRM_DATABASE_CHECK);
                return true;

            case R.id.action_check_media:
                showMediaCheckDialog(MediaCheckDialog.DIALOG_CONFIRM_MEDIA_CHECK);
                return true;

            case R.id.action_tutorial:
                createTutorialDeck();
                return true;

            case R.id.action_restore_backup:
                showDatabaseErrorDialog(DatabaseErrorDialog.DIALOG_CONFIRM_RESTORE_BACKUP);
                return true;

            case R.id.action_export:
                // Export APKG file
                StyledDialog.Builder builder = new StyledDialog.Builder(this);
                builder.setMessage(res.getString(R.string.confirm_apkg_export));
                builder.setPositiveButton(res.getString(R.string.dialog_ok), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        boolean exportMedia = false; // TODO : Ask the user if they want to export media
                        Object[] inputArgs = new Object[3];
                        inputArgs[0] = getCol().getPath();
                        inputArgs[1] = getCol().getPath().replace(".anki2", ".apkg");
                        inputArgs[2] = exportMedia;
                        DeckTask.launchDeckTask(DeckTask.TASK_TYPE_EXPORT_APKG, mExportListener,
                                new TaskData(inputArgs));
                    }
                });
                builder.setNegativeButton(res.getString(R.string.dialog_cancel), null);
                builder.create().show();
                return true;
            default:
                return super.onOptionsItemSelected(item);

        }
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);

        if (resultCode == RESULT_MEDIA_EJECTED) {
            showSdCardNotMountedDialog();
            return;
        } else if (resultCode == RESULT_DB_ERROR) {
            handleDbError();
            return;
        }
        if (requestCode == SHOW_STUDYOPTIONS && resultCode == RESULT_OK) {
            loadCounts();
        } else if (requestCode == ADD_NOTE && resultCode != RESULT_CANCELED) {
            loadCounts();
        } else if (requestCode == BROWSE_CARDS
                && (resultCode == Activity.RESULT_OK || resultCode == Activity.RESULT_CANCELED)) {
            loadCounts();
        } else if (requestCode == ADD_CRAM_DECK) {
            // TODO: check, if ok has been clicked
            loadCounts();
        } else if (requestCode == REPORT_ERROR) {
            showStartupScreensAndDialogs(AnkiDroidApp.getSharedPrefs(getBaseContext()), 4);
        } else if (requestCode == SHOW_INFO_UPGRADE_DECKS) {
            if (intent != null && intent.hasExtra(Info.TYPE_UPGRADE_STAGE)) {
                int type = intent.getIntExtra(Info.TYPE_UPGRADE_STAGE, Info.UPGRADE_SCREEN_BASIC1);
                if (type == Info.UPGRADE_CONTINUE) {
                    showStartupScreensAndDialogs(AnkiDroidApp.getSharedPrefs(getBaseContext()), 3);
                } else {
                    showUpgradeScreen(true, type, !intent.hasExtra(Info.TYPE_ANIMATION_RIGHT));
                }
            } else {
                if (resultCode == RESULT_OK) {
                    dismissOpeningCollectionDialog();
                    if (AnkiDroidApp.colIsOpen()) {
                        AnkiDroidApp.closeCollection(true);
                    }
                    AnkiDroidApp.openCollection(AnkiDroidApp.getCollectionPath());
                    loadCounts();
                } else {
                    finishWithAnimation();
                }
            }
        } else if (requestCode == SHOW_INFO_WELCOME || requestCode == SHOW_INFO_NEW_VERSION) {
            if (resultCode == RESULT_OK) {
                showStartupScreensAndDialogs(AnkiDroidApp.getSharedPrefs(getBaseContext()),
                        requestCode == SHOW_INFO_WELCOME ? 2 : 3);
            } else {
                finishWithAnimation();
            }
        } else if (requestCode == REPORT_FEEDBACK && resultCode == RESULT_OK) {
        } else if (requestCode == LOG_IN_FOR_SYNC && resultCode == RESULT_OK) {
            sync();
        } else if (requestCode == LOG_IN_FOR_SHARED_DECK && resultCode == RESULT_OK) {
            addSharedDeck();
        } else if (requestCode == ADD_SHARED_DECKS) {
            if (intent != null) {
                mImportPath = intent.getStringExtra("importPath");
            }
            if (AnkiDroidApp.colIsOpen() && mImportPath != null) {
                DeckTask.launchDeckTask(DeckTask.TASK_TYPE_IMPORT, mImportAddListener, new TaskData(getCol(),
                        mImportPath, true));
                mImportPath = null;
            }
        } else if (requestCode == REQUEST_REVIEW) {
            Log.i(AnkiDroidApp.TAG, "Result code = " + resultCode);
            switch (resultCode) {
                default:
                    // do not reload counts, if activity is created anew because it has been before destroyed by android
                    loadCounts();
                    break;
                case AbstractFlashcardViewer.RESULT_NO_MORE_CARDS:
                    Intent i = new Intent();
                    i.setClass(this, StudyOptionsActivity.class);
                    startActivityForResultWithAnimation(i, SHOW_STUDYOPTIONS, ActivityTransitionAnimation.RIGHT);
                    break;
            }

        }
    }


    @Override
    protected void onResume() {
        Log.i(AnkiDroidApp.TAG, "DeckPicker - onResume");
        super.onResume();
        if (getCol() != null) {
            if (Utils.now() > getCol().getSched().getDayCutoff() && AnkiDroidApp.isSdCardMounted()) {
                loadCounts();
            }
        }
        selectNavigationItem(DRAWER_DECK_PICKER);
    }


    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        super.onSaveInstanceState(savedInstanceState);
        savedInstanceState.putLong("mContextMenuDid", mContextMenuDid);
    }


    @Override
    public void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        mContextMenuDid = savedInstanceState.getLong("mContextMenuDid");
    }


    protected void sendKey(int keycode) {
        this.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, keycode));
        this.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, keycode));
    }


    @Override
    protected void onPause() {
        Log.i(AnkiDroidApp.TAG, "DeckPicker - onPause");

        super.onPause();
    }


    @Override
    protected void onStop() {
        Log.i(AnkiDroidApp.TAG, "DeckPicker - onStop");
        super.onStop();
        UIUtils.saveCollectionInBackground();
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mUnmountReceiver != null) {
            unregisterReceiver(mUnmountReceiver);
        }
        Log.i(AnkiDroidApp.TAG, "DeckPicker - onDestroy()");
    }


    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0) {
            Log.i(AnkiDroidApp.TAG, "DeckPicker - onBackPressed()");
            finishWithAnimation();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }


    private void finishWithAnimation() {
        super.finishWithAnimation(ActivityTransitionAnimation.DIALOG_EXIT);
    }


    // ----------------------------------------------------------------------------
    // CUSTOM METHODS
    // ----------------------------------------------------------------------------

    @Override
    protected void onCollectionLoaded(Collection col) {
        // keep reference to collection in parent
        super.onCollectionLoaded(col);
        // create backup in background if needed
        Boolean started = BackupManager.performBackupInBackground(col.getPath());
        if (started) {
            Themes.showThemedToast(this, getResources().getString(R.string.backup_collection), true);
        }
        // select last loaded deck if any
        if (mFragmented) {
            long did = col.getDecks().selected();
            selectDeck(did);
        }
        // Start import process if apkg file was opened via another app
        if (AnkiDroidApp.colIsOpen() && mImportPath != null) {
            if (mHandler == null) {
                mHandler = new DialogHandler(this);
            }
            if (mImportPath.split("/")[mImportPath.split("/").length - 1].equals("collection.apkg")) {
                // Show confirmation dialog asking to confirm import with replace when file called "collection.apkg"
                mHandler.sendEmptyMessage(MSG_SHOW_COLLECTION_IMPORT_REPLACE_DIALOG);
            } else {
                // Otherwise show confirmation dialog asking to confirm import with add
                mHandler.sendEmptyMessage(MSG_SHOW_COLLECTION_IMPORT_ADD_DIALOG);
            }
        }
        // prepare deck counts and mini-today-statistic
        loadCounts();
    }


    @Override
    protected void onCollectionLoadError() {
        // Show dialogs for handling collection load error
        setOpeningCollectionDialogMessage(getResources().getString(R.string.col_load_failed));
        if (mHandler == null) {
            mHandler = new DialogHandler(this);
        }
        mHandler.sendEmptyMessage(MSG_SHOW_COLLECTION_LOADING_ERROR_DIALOG);
    }

    /**
     * We're not allowed to commit fragment transactions from Loader.onLoadCompleted() so we work around this by using a
     * handler to show the error dialog:
     * http://stackoverflow.com/questions/7746140/android-problems-using-fragmentactivity
     * -loader-to-update-fragmentstatepagera
     * 
     * @param DeckPicker activity the main activity
     */
    static class DialogHandler extends Handler {
        private final WeakReference<DeckPicker> mActivity;


        DialogHandler(DeckPicker activity) {
            // Use weak reference to main activity to prevent leaking the activity when it's closed
            mActivity = new WeakReference<DeckPicker>(activity);
        }


        @Override
        public void handleMessage(Message msg) {
            if (msg.what == MSG_SHOW_COLLECTION_LOADING_ERROR_DIALOG) {
                mActivity.get().showDatabaseErrorDialog(DatabaseErrorDialog.DIALOG_LOAD_FAILED);
            } else if (msg.what == MSG_SHOW_COLLECTION_IMPORT_REPLACE_DIALOG) {
                mActivity.get().showImportDialog(ImportDialog.DIALOG_IMPORT_REPLACE_CONFIRM, mActivity.get().mImportPath);
            } else if (msg.what == MSG_SHOW_COLLECTION_IMPORT_ADD_DIALOG) {
                mActivity.get().showImportDialog(ImportDialog.DIALOG_IMPORT_ADD_CONFIRM, mActivity.get().mImportPath);
            }
        }
    }


    // Load deck counts, and update the today overview
    public void loadCounts() {
        if (AnkiDroidApp.colIsOpen()) {
            DeckTask.launchDeckTask(DeckTask.TASK_TYPE_LOAD_DECK_COUNTS, mLoadCountsHandler, new TaskData(getCol()));
        }
        TextView textView = (TextView) findViewById(R.id.today_stats_text_view);
        textView.setVisibility(View.GONE);
        AnkiStatsTaskHandler.createSmallTodayOverview(textView);
    }


    private void addNote() {
        Preferences.COMING_FROM_ADD = true;
        Intent intent = new Intent(DeckPicker.this, CardEditor.class);
        intent.putExtra(CardEditor.EXTRA_CALLER, CardEditor.CALLER_DECKPICKER);
        startActivityForResultWithAnimation(intent, ADD_NOTE, ActivityTransitionAnimation.LEFT);
    }


    private void showUpgradeScreen(boolean animation, int stage) {
        showUpgradeScreen(animation, stage, true);
    }


    private void showUpgradeScreen(boolean animation, int stage, boolean left) {
        Intent upgradeIntent = new Intent(this, Info.class);
        upgradeIntent.putExtra(Info.TYPE_EXTRA, Info.TYPE_UPGRADE_DECKS);
        upgradeIntent.putExtra(Info.TYPE_UPGRADE_STAGE, stage);
        startActivityForResultWithAnimation(upgradeIntent, SHOW_INFO_UPGRADE_DECKS,
                left ? ActivityTransitionAnimation.LEFT : ActivityTransitionAnimation.RIGHT);
    }


    private boolean upgradeNeeded() {
        if (!AnkiDroidApp.isSdCardMounted()) {
            showSdCardNotMountedDialog();
            return false;
        }
        File dir = new File(AnkiDroidApp.getCurrentAnkiDroidDirectory());
        if (!dir.isDirectory()) {
            dir.mkdirs();
        }
        if ((new File(AnkiDroidApp.getCollectionPath())).exists()) {
            // collection file exists
            return false;
        }
        return dir.listFiles(new OldAnkiDeckFilter()).length > 0;
    }


    private SharedPreferences restorePreferences() {
        SharedPreferences preferences = AnkiDroidApp.getSharedPrefs(getBaseContext());
        mPrefDeckPath = AnkiDroidApp.getCurrentAnkiDroidDirectory();
        return preferences;
    }


    private void showStartupScreensAndDialogs(SharedPreferences preferences, int skip) {
        if (skip < 1 && preferences.getLong("lastTimeOpened", 0) == 0) {
            // First time to install the app
            Intent infoIntent = new Intent(this, Info.class);
            infoIntent.putExtra(Info.TYPE_EXTRA, Info.TYPE_WELCOME);
            if (skip != 0) {
                startActivityForResultWithAnimation(infoIntent, SHOW_INFO_WELCOME, ActivityTransitionAnimation.LEFT);
            } else {
                startActivityForResultWithoutAnimation(infoIntent, SHOW_INFO_WELCOME);
            }
        } else if (!AnkiDroidApp.isSdCardMounted()) {
            // SD Card mounted
            showSdCardNotMountedDialog();
        } else if (!BackupManager.enoughDiscSpace(mPrefDeckPath)) {
            // Not enough space to do backup
            showDialogFragment(DeckPickerNoSpaceLeftDialog.newInstance());
        } else if (preferences.getBoolean("noSpaceLeft", false)) {
            // No space left
            showDialogFragment(DeckPickerBackupNoSpaceLeftDialog.newInstance());
            preferences.edit().putBoolean("noSpaceLeft", false).commit();
        } else if (skip < 2 && !preferences.getString("lastVersion", "").equals(AnkiDroidApp.getPkgVersionName())) {
            // AnkiDroid is being updated and a collection already exists. We check if we are upgrading
            // to a version that contains additions to the database integrity check routine that we would
            // like to run on all collections. A missing version number is assumed to be a fresh
            // installation of AnkiDroid and we don't run the check.
            int current = AnkiDroidApp.getPkgVersionCode();
            int previous;
            if (!preferences.contains("lastUpgradeVersion")) {
                // Fresh install
                previous = current;
            } else {
                try {
                    previous = preferences.getInt("lastUpgradeVersion", current);
                } catch (ClassCastException e) {
                    // Previous versions stored this as a string.
                    String s = preferences.getString("lastUpgradeVersion", "");
                    // The last version of AnkiDroid that stored this as a string was 2.0.2.
                    // We manually set the version here, but anything older will force a DB
                    // check.
                    if (s.equals("2.0.2")) {
                        previous = 40;
                    } else {
                        previous = 0;
                    }
                }
            }
            preferences.edit().putInt("lastUpgradeVersion", current).commit();
            // Check if preference upgrade or database check required, otherwise go to new feature screen
            if (previous < AnkiDroidApp.CHECK_DB_AT_VERSION || previous < AnkiDroidApp.CHECK_PREFERENCES_AT_VERSION) {
                if (previous < AnkiDroidApp.CHECK_PREFERENCES_AT_VERSION) {
                    upgradePreferences(previous);
                }
                // Integrity check loads asynchronously and then restart deckpicker when finished
                if (previous < AnkiDroidApp.CHECK_DB_AT_VERSION) {
                    integrityCheck();
                } else if (previous < AnkiDroidApp.CHECK_PREFERENCES_AT_VERSION) {
                    // If integrityCheck() doesn't occur, but we did update preferences we should restart DeckPicker to
                    // proceed
                    restartActivity();
                }
            } else {
                // If no changes are required we go to the new features activity
                // There the "lastVersion" is set, so that this code is not reached again
                Intent infoIntent = new Intent(this, Info.class);
                infoIntent.putExtra(Info.TYPE_EXTRA, Info.TYPE_NEW_VERSION);

                if (skip != 0) {
                    startActivityForResultWithAnimation(infoIntent, SHOW_INFO_NEW_VERSION,
                            ActivityTransitionAnimation.LEFT);
                } else {
                    startActivityForResultWithoutAnimation(infoIntent, SHOW_INFO_NEW_VERSION);
                }
            }
        } else if (skip < 3 && upgradeNeeded()) {
            // Note that the "upgrade needed" refers to upgrading Anki 1.x decks, not to newer
            // versions of AnkiDroid.
            AnkiDroidApp.getSharedPrefs(AnkiDroidApp.getInstance().getBaseContext()).edit()
                    .putInt("lastUpgradeVersion", AnkiDroidApp.getPkgVersionCode()).commit();
            showUpgradeScreen(skip != 0, Info.UPGRADE_SCREEN_BASIC1);
        } else if (skip < 4 && hasErrorFiles()) {
            // Need to submit error reports
            Intent i = new Intent(this, Feedback.class);
            if (skip != 0) {
                startActivityForResultWithAnimation(i, REPORT_ERROR, ActivityTransitionAnimation.LEFT);
            } else {
                startActivityForResultWithoutAnimation(i, REPORT_ERROR);
            }
        } else {
            // This is the main call when there is nothing special required
            startLoadingCollection();
        }
    }


    /**
     * @return true if the device is a Nook
     */
    private boolean isNookDevice() {
        for (String s : new String[] { "nook" }) {
            if (android.os.Build.DEVICE.toLowerCase(Locale.US).contains(s)
                    || android.os.Build.MODEL.toLowerCase(Locale.US).contains(s)) {
                return true;
            }
        }
        return false;
    }


    private void upgradePreferences(int previousVersionCode) {
        SharedPreferences preferences = AnkiDroidApp.getSharedPrefs(getBaseContext());
        // when upgrading from before 2.1alpha08
        if (previousVersionCode < 20100108) {
            preferences.edit().putString("overrideFont", preferences.getString("defaultFont", "")).commit();
            preferences.edit().putString("defaultFont", "").commit();
        }
        // when upgrading from before 2.2alpha66
        if (previousVersionCode < 20200166) {
            // change name from swipe to gestures
            preferences.edit().putInt("swipeSensitivity", preferences.getInt("swipeSensibility", 100)).commit();
            preferences.edit().putBoolean("gestures", preferences.getBoolean("swipe", false)).commit();
            // set new safeDisplayMode preference based on old behavior
            boolean safeDisplayMode = preferences.getBoolean("eInkDisplay", false) || isNookDevice()
                    || !preferences.getBoolean("forceQuickUpdate", false);
            preferences.edit().putBoolean("safeDisplay", safeDisplayMode).commit();
            // set overrideFontBehavior based on old overrideFont settings
            String overrideFont = preferences.getString("overrideFont", "");
            if (!overrideFont.equals("")) {
                preferences.edit().putString("defaultFont", overrideFont).commit();
                preferences.edit().putString("overrideFontBehavior", "1").commit();
            } else {
                preferences.edit().putString("overrideFontBehavior", "0").commit();
            }
            // change typed answers setting from enable to disable
            preferences.edit().putBoolean("writeAnswersDisable", !preferences.getBoolean("writeAnswers", true))
                    .commit();
        }
        // when upgrading from before 2.3alpha30
        if (previousVersionCode < 20300130) {
            // Increase default number of backups
            preferences.edit().putInt("backupMax", 8).commit();
        }
    }


    public void showDialogFragment(DialogFragment newFragment) {
        // DialogFragment.show() will take care of adding the fragment
        // in a transaction. We also want to remove any currently showing
        // dialog, so make our own transaction and take care of that here.
        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
        Fragment prev = getSupportFragmentManager().findFragmentByTag("dialog");
        if (prev != null) {
            ft.remove(prev);
        }
        // save transaction to the back stack
        ft.addToBackStack("dialog");
        newFragment.show(ft, "dialog");
    }


    // Show dialogs to deal with database loading issues etc
    @Override
    public void showDatabaseErrorDialog(int id) {
        DialogFragment newFragment = DatabaseErrorDialog.newInstance(id);
        showDialogFragment(newFragment);
    }

    @Override
    public void showMediaCheckDialog(int id) {
        DialogFragment newFragment = MediaCheckDialog.newInstance(id);
        showDialogFragment(newFragment);
    }


    @Override
    public void showMediaCheckDialog(int id, List<List<String>> checkList) {
        DialogFragment newFragment = MediaCheckDialog.newInstance(id, checkList);
        showDialogFragment(newFragment);
    }


    // Show dialogs to deal with sync issues etc
    @Override
    public void showSyncErrorDialog(int id) {
        showSyncErrorDialog(id, "");
    }


    @Override
    public void showSyncErrorDialog(int id, String message) {
        DialogFragment newFragment = SyncErrorDialog.newInstance(id, message);
        showDialogFragment(newFragment);
    }


    private void showLogDialog(String message) {
        showLogDialog(message, false);
    }


    private void showLogDialog(String message, final boolean reload) {
        // Since this is called from onPostExecute() of an AsyncTask, we can't use a DialogFragment
        // as we can't make commits to the fragment manager while the Activity is closed
        StyledDialog.Builder builder = new StyledDialog.Builder(this);
        builder.setMessage(message);
        builder.setPositiveButton(getResources().getString(R.string.dialog_ok), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dismissAllDialogFragments();
                if (reload) {
                    Intent deckPicker = new Intent(DeckPicker.this, DeckPicker.class);
                    deckPicker.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivityWithAnimation(deckPicker, ActivityTransitionAnimation.LEFT);
                }
            }
        });
        builder.create().show();
    }


    @Override
    public void showImportDialog(int id) {
        showImportDialog(id, "");
    }


    @Override
    public void showImportDialog(int id, String message) {
        DialogFragment newFragment = ImportDialog.newInstance(id, message);
        showDialogFragment(newFragment);
    }


    public void showSdCardNotMountedDialog() {
        if (mNotMountedDialog == null || !mNotMountedDialog.isShowing()) {
            mNotMountedDialog = StyledOpenCollectionDialog.show(DeckPicker.this,
                    getResources().getString(R.string.sd_card_not_mounted), new OnCancelListener() {

                        @Override
                        public void onCancel(DialogInterface arg0) {
                            finishWithAnimation();
                        }
                    }, new View.OnClickListener() {

                        @Override
                        public void onClick(View v) {
                            startActivityForResultWithoutAnimation(new Intent(DeckPicker.this, Preferences.class),
                                    NavigationDrawerActivity.REQUEST_PREFERENCES_UPDATE);
                        }
                    });
        }
    }


    // Callback method to submit error report
    @Override
    public void sendErrorReport() {
        Intent i = new Intent(this, Feedback.class);
        i.putExtra("request", DeckPicker.RESULT_DB_ERROR);
        startActivityForResultWithAnimation(i, DeckPicker.REPORT_ERROR, ActivityTransitionAnimation.RIGHT);
    }


    // Callback method to handle repairing deck
    @Override
    public void repairDeck() {
        DeckTask.launchDeckTask(DeckTask.TASK_TYPE_REPAIR_DECK, new DeckTask.TaskListener() {

            @Override
            public void onPreExecute() {
                mProgressDialog = StyledProgressDialog.show(DeckPicker.this, "",
                        getResources().getString(R.string.backup_repair_deck_progress), true);
            }


            @Override
            public void onPostExecute(DeckTask.TaskData result) {
                if (mProgressDialog != null && mProgressDialog.isShowing()) {
                    mProgressDialog.dismiss();
                }
                if (result.getBoolean()) {
                    startLoadingCollection();
                } else {
                    Themes.showThemedToast(DeckPicker.this, getResources().getString(R.string.deck_repair_error), true);
                    onCollectionLoadError();
                }
            }


            @Override
            public void onProgressUpdate(TaskData... values) {
            }


            @Override
            public void onCancelled() {
            }
        }, new DeckTask.TaskData(AnkiDroidApp.getCol(), AnkiDroidApp.getCollectionPath()));
    }


    // Callback method to handle database integrity check
    @Override
    public void integrityCheck() {
        DeckTask.launchDeckTask(DeckTask.TASK_TYPE_CHECK_DATABASE, new DeckTask.TaskListener() {
            @Override
            public void onPreExecute() {
                mProgressDialog = StyledProgressDialog.show(DeckPicker.this, "",
                        getResources().getString(R.string.check_db_message), true);
            }


            @Override
            public void onPostExecute(TaskData result) {
                if (mProgressDialog.isShowing()) {
                    mProgressDialog.dismiss();
                }
                if (result.getBoolean()) {
                    String msg = "";
                    double shrunk = Math.round(result.getLong() / 1024.0);
                    if (shrunk > 0.0) {
                        msg = String.format(Locale.getDefault(),
                                getResources().getString(R.string.check_db_acknowledge_shrunk), shrunk);
                    } else {
                        msg = getResources().getString(R.string.check_db_acknowledge);
                    }
                    // Show result of database check and restart the app
                    showLogDialog(msg, true);
                } else {
                    handleDbError();
                }
            }


            @Override
            public void onProgressUpdate(TaskData... values) {
            }


            @Override
            public void onCancelled() {
            }
        }, new DeckTask.TaskData(getCol()));
    }


    @Override
    public void mediaCheck() {
        DeckTask.launchDeckTask(DeckTask.TASK_TYPE_CHECK_MEDIA, new DeckTask.TaskListener() {
            @Override
            public void onPreExecute() {
                mProgressDialog = StyledProgressDialog.show(DeckPicker.this, "",
                        getResources().getString(R.string.check_media_message), true);
            }


            @Override
            public void onPostExecute(TaskData result) {
                if (mProgressDialog.isShowing()) {
                    mProgressDialog.dismiss();
                }
                if (result.getBoolean()) {
                    @SuppressWarnings("unchecked")
                    List<List<String>> checkList = (List<List<String>>) result.getObjArray()[0];
                    showMediaCheckDialog(MediaCheckDialog.DIALOG_MEDIA_CHECK_RESULTS, checkList);
                } else {
                    showLogDialog(getResources().getString(R.string.check_media_failed));
                }
            }


            @Override
            public void onProgressUpdate(TaskData... values) {
            }


            @Override
            public void onCancelled() {
            }
        }, new DeckTask.TaskData(getCol()));
    }

    @Override
    public void deleteUnused(List<String> unused) {
        com.ichi2.libanki.Media m = getCol().getMedia();
        for (String fname : unused) {
            m.deleteFile(fname);
        }
        showLogDialog(String.format(getResources().getString(R.string.check_media_deleted), unused.size()));
    }

    @Override
    public void exit() {
        finishWithoutAnimation();
    }


    public void handleDbError() {
        showDatabaseErrorDialog(DatabaseErrorDialog.DIALOG_LOAD_FAILED);
    }


    @Override
    public void restoreFromBackup(String path) {
        importReplace(path);
    }


    // Helper function to check if there are any saved stacktraces
    @Override
    public boolean hasErrorFiles() {
        for (String file : this.fileList()) {
            if (file.endsWith(".stacktrace")) {
                return true;
            }
        }
        return false;
    }


    // Sync with Anki Web
    @Override
    public void sync() {
        sync(null, 0);
    }


    /**
     * The mother of all syncing attempts. This might be called from sync() as first attempt to sync a collection OR
     * from the mSyncConflictResolutionListener if the first attempt determines that a full-sync is required. In the
     * second case, we have passed the mediaUsn that was obtained during the first attempt.
     * 
     * @param syncConflictResolution Either "upload" or "download", depending on the user's choice.
     * @param syncMediaUsn The media Usn, as determined during the prior sync() attempt that determined that full
     *            syncing was required.
     */
    @Override
    public void sync(String syncConflictResolution, int syncMediaUsn) {
        SharedPreferences preferences = AnkiDroidApp.getSharedPrefs(getBaseContext());
        String hkey = preferences.getString("hkey", "");
        if (hkey.length() == 0) {
            showSyncErrorDialog(SyncErrorDialog.DIALOG_USER_NOT_LOGGED_IN_SYNC);
        } else {
            Connection.sync(mSyncListener,
                    new Connection.Payload(new Object[] { hkey, preferences.getBoolean("syncFetchesMedia", true),
                            syncConflictResolution, syncMediaUsn }));
        }
    }


    @Override
    public int getSyncMediaUsn() {
        return mSyncMediaUsn;
    }

    private Connection.TaskListener mSyncListener = new Connection.TaskListener() {

        String currentMessage;
        long countUp;
        long countDown;


        @Override
        public void onDisconnected() {
            showSyncErrorDialog(SyncErrorDialog.DIALOG_NO_CONNECTION);
        }


        @Override
        public void onPreExecute() {
            countUp = 0;
            countDown = 0;
            if (mProgressDialog == null || !mProgressDialog.isShowing()) {
                mProgressDialog = StyledProgressDialog
                        .show(DeckPicker.this, getResources().getString(R.string.sync_title),
                                getResources().getString(R.string.sync_prepare_syncing) + "\n"
                                        + getResources().getString(R.string.sync_up_down_size, countUp, countDown),
                                true, false);
            }
        }


        @Override
        public void onProgressUpdate(Object... values) {
            Resources res = getResources();
            if (values[0] instanceof Boolean) {
                // This is the part Download missing media of syncing
                int total = (Integer) values[1];
                int done = (Integer) values[2];
                values[0] = (values[3]);
                values[1] = res.getString(R.string.sync_downloading_media, done, total);
            } else if (values[0] instanceof Integer) {
                int id = (Integer) values[0];
                if (id != 0) {
                    currentMessage = res.getString(id);
                }
                if (values.length >= 3) {
                    countUp = (Long) values[1];
                    countDown = (Long) values[2];
                }
            } else if (values[0] instanceof String) {
                currentMessage = (String) values[0];
                if (values.length >= 3) {
                    countUp = (Long) values[1];
                    countDown = (Long) values[2];
                }
            }
            if (mProgressDialog != null && mProgressDialog.isShowing()) {
                // mProgressDialog.setTitle((String) values[0]);
                mProgressDialog.setMessage(currentMessage + "\n"
                        + res.getString(R.string.sync_up_down_size, countUp / 1024, countDown / 1024));
            }
        }


        @SuppressWarnings("unchecked")
        @Override
        public void onPostExecute(Payload data) {
            String dialogMessage = "";
            String syncMessage = "";
            Log.i(AnkiDroidApp.TAG, "onPostExecute");
            Resources res = getResources();
            if (mProgressDialog != null) {
                mProgressDialog.dismiss();
            }
            syncMessage = data.message;
            if (!data.success) {
                Object[] result = (Object[]) data.result;
                if (result[0] instanceof String) {
                    String resultType = (String) result[0];
                    if (resultType.equals("badAuth")) {
                        // delete old auth information
                        SharedPreferences preferences = AnkiDroidApp.getSharedPrefs(getBaseContext());
                        Editor editor = preferences.edit();
                        editor.putString("username", "");
                        editor.putString("hkey", "");
                        editor.commit();
                        // then show
                        // TODO: This will cause a crash in the rare case the activity has been stopped
                        showSyncErrorDialog(SyncErrorDialog.DIALOG_USER_NOT_LOGGED_IN_SYNC);
                    } else if (resultType.equals("noChanges")) {
                        dialogMessage = res.getString(R.string.sync_no_changes_message);
                        showLogDialog(joinSyncMessages(dialogMessage, syncMessage));
                    } else if (resultType.equals("clockOff")) {
                        long diff = (Long) result[1];
                        if (diff >= 86100) {
                            // The difference if more than a day minus 5 minutes acceptable by ankiweb error
                            dialogMessage = res.getString(R.string.sync_log_clocks_unsynchronized, diff,
                                    res.getString(R.string.sync_log_clocks_unsynchronized_date));
                        } else if (Math.abs((diff % 3600.0) - 1800.0) >= 1500.0) {
                            // The difference would be within limit if we adjusted the time by few hours
                            // It doesn't work for all timezones, but it covers most and it's a guess anyway
                            dialogMessage = res.getString(R.string.sync_log_clocks_unsynchronized, diff,
                                    res.getString(R.string.sync_log_clocks_unsynchronized_tz));
                        } else {
                            dialogMessage = res.getString(R.string.sync_log_clocks_unsynchronized, diff, "");
                        }
                        showLogDialog(joinSyncMessages(dialogMessage, syncMessage));
                    } else if (resultType.equals("fullSync")) {
                        if (data.data != null && data.data.length >= 1 && data.data[0] instanceof Integer) {
                            mSyncMediaUsn = (Integer) data.data[0];
                        }
                        showSyncErrorDialog(SyncErrorDialog.DIALOG_SYNC_CONFLICT_RESOLUTION);
                    } else if (resultType.equals("dbError")) {
                        dialogMessage = res.getString(R.string.sync_corrupt_database, R.string.repair_deck);
                        showLogDialog(joinSyncMessages(dialogMessage, syncMessage));
                    } else if (resultType.equals("overwriteError")) {
                        dialogMessage = res.getString(R.string.sync_overwrite_error);
                        showLogDialog(joinSyncMessages(dialogMessage, syncMessage));
                    } else if (resultType.equals("remoteDbError")) {
                        dialogMessage = res.getString(R.string.sync_remote_db_error);
                        showLogDialog(joinSyncMessages(dialogMessage, syncMessage));
                    } else if (resultType.equals("sdAccessError")) {
                        dialogMessage = res.getString(R.string.sync_write_access_error);
                        showLogDialog(joinSyncMessages(dialogMessage, syncMessage));
                    } else if (resultType.equals("finishError")) {
                        dialogMessage = res.getString(R.string.sync_log_finish_error);
                        showLogDialog(joinSyncMessages(dialogMessage, syncMessage));
                    } else if (resultType.equals("IOException")) {
                        handleDbError();
                    } else if (resultType.equals("genericError")) {
                        dialogMessage = res.getString(R.string.sync_generic_error);
                        showLogDialog(joinSyncMessages(dialogMessage, syncMessage));
                    } else if (resultType.equals("OutOfMemoryError")) {
                        dialogMessage = res.getString(R.string.error_insufficient_memory);
                        showLogDialog(joinSyncMessages(dialogMessage, syncMessage));
                    } else if (resultType.equals("sanityCheckError")) {
                        Collection col = getCol();
                        col.modSchema();
                        col.save();
                        dialogMessage = res.getString(R.string.sync_sanity_failed);
                        // TODO: This will cause a crash in the rare case the activity has been stopped
                        showSyncErrorDialog(SyncErrorDialog.DIALOG_SYNC_SANITY_ERROR,
                                joinSyncMessages(dialogMessage, syncMessage));
                    } else if (resultType.equals("serverAbort")) {
                        // syncMsg has already been set above, no need to fetch it here.
                        showLogDialog(joinSyncMessages(dialogMessage, syncMessage));
                    } else {
                        if (result.length > 1 && result[1] instanceof Integer) {
                            int type = (Integer) result[1];
                            switch (type) {
                                case 503:
                                    dialogMessage = res.getString(R.string.sync_too_busy);
                                    break;
                                default:
                                    dialogMessage = res.getString(R.string.sync_log_error_specific,
                                            Integer.toString(type), result[2]);
                                    break;
                            }
                        } else if (result[0] instanceof String) {
                            dialogMessage = res.getString(R.string.sync_log_error_specific, -1, result[0]);
                        } else {
                            dialogMessage = res.getString(R.string.sync_generic_error);
                        }
                        showLogDialog(joinSyncMessages(dialogMessage, syncMessage));
                    }
                }
            } else {
                updateDecksList((TreeSet<Object[]>) data.result, (Integer) data.data[2], (Integer) data.data[3]);

                if (data.data[4] != null) {
                    dialogMessage = (String) data.data[4];
                } else if (data.data.length > 0 && data.data[0] instanceof String
                        && ((String) data.data[0]).length() > 0) {
                    String dataString = (String) data.data[0];
                    if (dataString.equals("upload")) {
                        dialogMessage = res.getString(R.string.sync_log_uploading_message);
                    } else if (dataString.equals("download")) {
                        dialogMessage = res.getString(R.string.sync_log_downloading_message);
                        // set downloaded collection as current one
                    } else {
                        dialogMessage = res.getString(R.string.sync_database_acknowledge);
                    }
                } else {
                    dialogMessage = res.getString(R.string.sync_database_acknowledge);
                }

                // Synchronize the collection in AnkiActivity with the new one opened in AnkiDroidApp during the sync
                startLoadingCollection();
                showLogDialog(joinSyncMessages(dialogMessage, syncMessage));

                if (mFragmented) {
                    try {
                        // Pick the correct deck after sync. Updates the values in the fragment if same deck.
                        long did = getCol().getDecks().current().getLong("id");
                        selectDeck(did);
                    } catch (JSONException e) {
                        throw new RuntimeException();
                    }
                }
            }
        }
    };


    private String joinSyncMessages(String dialogMessage, String syncMessage) {
        // If both strings have text, separate them by a new line, otherwise return whichever has text
        if (!TextUtils.isEmpty(dialogMessage) && !TextUtils.isEmpty(syncMessage)) {
            return dialogMessage + "\n\n" + syncMessage;
        } else if (!TextUtils.isEmpty(dialogMessage)) {
            return dialogMessage;
        } else {
            return syncMessage;
        }
    }


    @Override
    public void loginToSyncServer() {
        Intent myAccount = new Intent(this, MyAccount.class);
        myAccount.putExtra("notLoggedIn", true);
        startActivityForResultWithAnimation(myAccount, LOG_IN_FOR_SYNC, ActivityTransitionAnimation.FADE);
    }


    // Callback to import a file -- adding it to existing collection
    @Override
    public void importAdd(String importPath) {
        DeckTask.launchDeckTask(DeckTask.TASK_TYPE_IMPORT, mImportAddListener,
                new TaskData(getCol(), importPath, false));
    }


    // Callback to import a file -- replacing the existing collection
    @Override
    public void importReplace(String importPath) {
        DeckTask.launchDeckTask(DeckTask.TASK_TYPE_IMPORT_REPLACE, mImportReplaceListener, new TaskData(getCol(),
                importPath));
    }


    @Override
    public void loadStudyOptionsFragment() {
        loadStudyOptionsFragment(0, null);
    }


    @Override
    public void loadStudyOptionsFragment(long deckId, Bundle cramConfig) {
        StudyOptionsFragment details = StudyOptionsFragment.newInstance(deckId, cramConfig);
        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
        ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_CLOSE);
        ft.replace(R.id.studyoptions_fragment, details);
        ft.commit();
    }


    public StudyOptionsFragment getFragment() {
        Fragment frag = getSupportFragmentManager().findFragmentById(R.id.studyoptions_fragment);
        if (frag != null && (frag instanceof StudyOptionsFragment)) {
            return (StudyOptionsFragment) frag;
        }
        return null;
    }


    /**
     * Show/dismiss dialog when sd card is ejected/remounted (collection is saved by SdCardReceiver)
     */
    private void registerExternalStorageListener() {
        if (mUnmountReceiver == null) {
            mUnmountReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (intent.getAction().equals(SdCardReceiver.MEDIA_EJECT)) {
                        showSdCardNotMountedDialog();
                    } else if (intent.getAction().equals(SdCardReceiver.MEDIA_MOUNT)) {
                        if (mNotMountedDialog != null && mNotMountedDialog.isShowing()) {
                            mNotMountedDialog.dismiss();
                        }
                        startLoadingCollection();
                    }
                }
            };
            IntentFilter iFilter = new IntentFilter();
            iFilter.addAction(SdCardReceiver.MEDIA_EJECT);
            iFilter.addAction(SdCardReceiver.MEDIA_MOUNT);
            registerReceiver(mUnmountReceiver, iFilter);
        }
    }


    /**
     * Creates an intent to load a deck given the full pathname of it. The constructed intent is equivalent (modulo the
     * extras) to the open used by the launcher shortcut, which means it will not open a new study options window but
     * bring the existing one to the front.
     */
    public static Intent getLoadDeckIntent(Context context, long deckId) {
        Intent loadDeckIntent = new Intent(context, DeckPicker.class);
        loadDeckIntent.setAction(Intent.ACTION_MAIN);
        loadDeckIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        loadDeckIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        loadDeckIntent.putExtra(EXTRA_DECK_ID, deckId);
        return loadDeckIntent;
    }


    private void addSharedDeck() {
        Intent intent = new Intent(DeckPicker.this, Info.class);
        intent.putExtra(Info.TYPE_EXTRA, Info.TYPE_SHARED_DECKS);
        startActivityForResultWithAnimation(intent, ADD_SHARED_DECKS, ActivityTransitionAnimation.RIGHT);
        // Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(res.getString(R.string.shared_decks_url)));
        startActivityWithAnimation(intent, ActivityTransitionAnimation.RIGHT);
    }


    private void openStudyOptions(long deckId) {
        openStudyOptions(deckId, null);
    }


    private void openStudyOptions(long deckId, Bundle cramInitialConfig) {
        if (mFragmented) {
            loadStudyOptionsFragment(deckId, cramInitialConfig);
        } else {
            Intent intent = new Intent();
            intent.putExtra("index", deckId);
            intent.putExtra("cramInitialConfig", cramInitialConfig);
            intent.setClass(this, StudyOptionsActivity.class);
            startActivityForResultWithAnimation(intent, SHOW_STUDYOPTIONS, ActivityTransitionAnimation.LEFT);
        }
    }


    /**
     * Programmatically click on a deck in the deck list.
     * 
     * @param did The deck ID of the deck to select.
     */
    private void selectDeck(long did) {
        Log.i(AnkiDroidApp.TAG, "Selected deck with ID " + did);
        for (int i = 0; i < mDeckList.size(); i++) {
            if (Long.parseLong(mDeckList.get(i).get("did")) == did) {
                final int lastPosition = i;
                mDeckListView.getViewTreeObserver().addOnGlobalLayoutListener(new OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        removeOnGlobalLayoutListener(mDeckListView, this);
                        mDeckListView.performItemClick(null, lastPosition, 0);
                        // Scroll the listView to the currently selected row, then offset it by half the
                        // listview's height so that it is centered.
                        mDeckListView.setSelectionFromTop(lastPosition, mDeckListView.getHeight() / 2);
                    }
                });
                break;
            }
        }
    }


    @SuppressLint("NewApi")
    @SuppressWarnings("deprecation")
    public static void removeOnGlobalLayoutListener(View v, ViewTreeObserver.OnGlobalLayoutListener listener) {
        if (AnkiDroidApp.SDK_VERSION < 16) {
            v.getViewTreeObserver().removeGlobalOnLayoutListener(listener);
        } else {
            v.getViewTreeObserver().removeOnGlobalLayoutListener(listener);
        }
    }


    /**
     * Set which deck is selected (highlighted) in the deck list.
     * <p>
     * Note that this method does not change the currently selected deck in the collection, only the highlighted deck in
     * the deck list. To select a deck, see {@link #selectDeck(long)}.
     * 
     * @param did The deck ID of the deck to select.
     */
    public void setSelectedDeck(long did) {
        for (int i = 0; i < mDeckList.size(); i++) {
            if (Long.parseLong(mDeckList.get(i).get("did")) == did) {
                mDeckListView.setItemChecked(i, true);
                break;
            }
        }
    }


    private void handleDeckSelection(int id) {
        if (!AnkiDroidApp.colIsOpen()) {
            startLoadingCollection();
        }

        String deckFilename = null;

        @SuppressWarnings("unchecked")
        HashMap<String, String> data = (HashMap<String, String>) mDeckListAdapter.getItem(id);
        Log.i(AnkiDroidApp.TAG, "Selected " + deckFilename);
        long deckId = Long.parseLong(data.get("did"));
        getCol().getDecks().select(deckId);
        openStudyOptions(deckId);
    }


    private void createTutorialDeck() {
        DeckTask.launchDeckTask(DeckTask.TASK_TYPE_LOAD_TUTORIAL, new DeckTask.TaskListener() {
            @Override
            public void onPreExecute() {
                mProgressDialog = StyledProgressDialog.show(DeckPicker.this, "",
                        getResources().getString(R.string.tutorial_load), true);
            }


            @Override
            public void onPostExecute(TaskData result) {
                if (result.getBoolean()) {
                    loadCounts();
                    // TODO: This will crash if we're using fragmented layout and activity has been stopped
                    openStudyOptions(getCol().getDecks().selected());
                } else {
                    Themes.showThemedToast(DeckPicker.this, getResources().getString(R.string.tutorial_loading_error),
                            false);
                }
                if (mProgressDialog.isShowing()) {
                    try {
                        mProgressDialog.dismiss();
                    } catch (Exception e) {
                        Log.e(AnkiDroidApp.TAG, "onPostExecute - Dialog dismiss Exception = " + e.getMessage());
                    }
                }
            }


            @Override
            public void onProgressUpdate(TaskData... values) {
            }


            @Override
            public void onCancelled() {
            }
        }, new DeckTask.TaskData(getCol()));
    }


    private void updateDecksList(TreeSet<Object[]> decks, int eta, int count) {
        if (decks == null) {
            Log.e(AnkiDroidApp.TAG, "updateDecksList: empty decks list");
            return;
        }
        mDeckList.clear();
        int due = 0;
        for (Object[] d : decks) {
            HashMap<String, String> m = new HashMap<String, String>();
            String[] name = ((String[]) d[0]);
            m.put("name", readableDeckName(name));
            m.put("did", ((Long) d[1]).toString());
            m.put("new", ((Integer) d[2]).toString());
            m.put("lrn", ((Integer) d[3]).toString());
            m.put("rev", ((Integer) d[4]).toString());
            m.put("dyn", ((Boolean) d[5]) ? "d1" : "d0");
            // m.put("complMat", ((Float)d[5]).toString());
            // m.put("complAll", ((Float)d[6]).toString());
            if (name.length == 1) {
                due += Integer.parseInt(m.get("new")) + Integer.parseInt(m.get("lrn")) + Integer.parseInt(m.get("rev"));
                // top position
                m.put("sep", "top");
                // correct previous deck
                if (mDeckList.size() > 0) {
                    HashMap<String, String> map = mDeckList.get(mDeckList.size() - 1);
                    if (map.get("sep").equals("top")) {
                        map.put("sep", "ful");
                    } else {
                        map.put("sep", "bot");
                    }
                }
            } else {
                // center position
                m.put("sep", "cen");
            }
            if (mDeckList.size() > 0 && mDeckList.size() == decks.size() - 1) {
                // bottom position
                if (name.length == 1) {
                    m.put("sep", "ful");
                } else {
                    m.put("sep", "bot");
                }
            }
            mDeckList.add(m);
        }
        mDeckListAdapter.notifyDataSetChanged();

        // set title
        Resources res = getResources();
        if (count != -1) {
            String time = "-";
            if (eta != -1) {
                time = res.getQuantityString(R.plurals.deckpicker_title_minutes, eta, eta);
            }
            AnkiDroidApp.getCompat().setSubtitle(this,
                    res.getQuantityString(R.plurals.deckpicker_title, due, due, time));
        }

        // update widget
        WidgetStatus.update(this, decks);
    }


    // Callback to collapse currently selected deck
    public void collapseContextMenuDeck() {
        try {
            JSONObject deck = getCol().getDecks().get(mContextMenuDid);
            if (getCol().getDecks().children(mContextMenuDid).size() > 0) {
                deck.put("collapsed", !deck.getBoolean("collapsed"));
                getCol().getDecks().save(deck);
                loadCounts();
            }
        } catch (JSONException e1) {
            // do nothing
        }
    }


    // Callback to show study options for currently selected deck
    public void showContextMenuDeckOptions() {
        getCol().getDecks().select(mContextMenuDid);
        if (mFragmented) {
            loadStudyOptionsFragment(mContextMenuDid, null);
        }
        // open deck options
        if (getCol().getDecks().isDyn(mContextMenuDid)) {
            // open cram options if filtered deck
            Intent i = new Intent(DeckPicker.this, CramDeckOptions.class);
            i.putExtra("cramInitialConfig", (String) null);
            startActivityWithAnimation(i, ActivityTransitionAnimation.FADE);
        } else {
            // otherwise open regular options
            Intent i = new Intent(DeckPicker.this, DeckOptions.class);
            startActivityWithAnimation(i, ActivityTransitionAnimation.FADE);
        }
    }


    // Callback to show dialog to rename the current deck
    public void renameContextMenuDeckDialog() {
        Resources res = getResources();
        StyledDialog.Builder builder2 = new StyledDialog.Builder(DeckPicker.this);
        builder2.setTitle(res.getString(R.string.contextmenu_deckpicker_rename_deck));

        mDialogEditText = new EditText(DeckPicker.this);
        mDialogEditText.setSingleLine();
        mDialogEditText.setText(getCol().getDecks().name(mContextMenuDid));
        // mDialogEditText.setFilters(new InputFilter[] { mDeckNameFilter });
        builder2.setView(mDialogEditText, false, false);
        builder2.setPositiveButton(res.getString(R.string.rename), new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {
                String newName = mDialogEditText.getText().toString().replaceAll("['\"]", "");
                Collection col = getCol();
                if (col != null) {
                    if (col.getDecks().rename(col.getDecks().get(mContextMenuDid), newName)) {
                        for (HashMap<String, String> d : mDeckList) {
                            if (d.get("did").equals(Long.toString(mContextMenuDid))) {
                                d.put("name", newName);
                            }
                        }
                        mDeckListAdapter.notifyDataSetChanged();
                        loadCounts();
                    } else {
                        try {
                            Themes.showThemedToast(
                                    DeckPicker.this,
                                    getResources().getString(R.string.rename_error,
                                            col.getDecks().get(mContextMenuDid).get("name")), false);
                        } catch (JSONException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }
            }
        });
        builder2.setNegativeButton(res.getString(R.string.dialog_cancel), null);
        builder2.create().show();
        return;
    }


    // Callback to show confirm deck deletion dialog before deleting currently selected deck
    public void confirmDeckDeletion() {
        Resources res = getResources();
        if (!AnkiDroidApp.colIsOpen() || mDeckList == null || mDeckList.size() == 0) {
            return;
        }
        String msg = "";
        boolean isDyn = getCol().getDecks().isDyn(mContextMenuDid);
        if (isDyn) {
            msg = String.format(res.getString(R.string.delete_cram_deck_message),
                    "\'" + getCol().getDecks().name(mContextMenuDid) + "\'");
        } else {
            msg = String.format(res.getString(R.string.delete_deck_message),
                    "\'" + getCol().getDecks().name(mContextMenuDid) + "\'");
        }
        showDialogFragment(DeckPickerConfirmDeleteDeckDialog.newInstance(msg));
    }


    // Callback to delete currently selected deck
    public void deleteContextMenuDeck() {
        DeckTask.launchDeckTask(DeckTask.TASK_TYPE_DELETE_DECK, new DeckTask.TaskListener() {
            @Override
            public void onPreExecute() {
                mProgressDialog = StyledProgressDialog.show(DeckPicker.this, "",
                        getResources().getString(R.string.delete_deck), true);
            }


            @SuppressWarnings("unchecked")
            @Override
            public void onPostExecute(TaskData result) {
                if (result == null) {
                    return;
                }
                Object[] res = result.getObjArray();
                updateDecksList((TreeSet<Object[]>) res[0], (Integer) res[1], (Integer) res[2]);
                if (mFragmented) {
                    selectDeck(getCol().getDecks().selected());
                }
                if (mProgressDialog.isShowing()) {
                    try {
                        mProgressDialog.dismiss();
                    } catch (Exception e) {
                        Log.e(AnkiDroidApp.TAG, "onPostExecute - Dialog dismiss Exception = " + e.getMessage());
                    }
                }
            }


            @Override
            public void onProgressUpdate(TaskData... values) {
            }


            @Override
            public void onCancelled() {
            }
        }, new TaskData(getCol(), mContextMenuDid));
    }


    // ----------------------------------------------------------------------------
    // INNER CLASSES
    // ----------------------------------------------------------------------------

    public static String readableDeckName(String[] name) {
        int len = name.length;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len; i++) {
            if (i == len - 1) {
                sb.append(name[i]);
            } else if (i == len - 2) {
                sb.append("\u21aa");
            } else {
                sb.append("    ");
            }
        }
        return sb.toString();
    }


    @Override
    public void onAttachedToWindow() {

        if (!mFragmented) {
            Window window = getWindow();
            window.setFormat(PixelFormat.RGBA_8888);
        }
    }
}
