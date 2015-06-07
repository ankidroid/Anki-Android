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
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.res.Resources;
import android.database.SQLException;
import android.graphics.PixelFormat;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.afollestad.materialdialogs.MaterialDialog;
import com.github.amlcurran.showcaseview.OnShowcaseEventListener;
import com.github.amlcurran.showcaseview.ShowcaseView;
import com.github.amlcurran.showcaseview.targets.ViewTarget;
import com.ichi2.anim.ActivityTransitionAnimation;
import com.ichi2.anki.StudyOptionsFragment.StudyOptionsListener;
import com.ichi2.anki.dialogs.AsyncDialogFragment;
import com.ichi2.anki.dialogs.DatabaseErrorDialog;
import com.ichi2.anki.dialogs.DeckPickerBackupNoSpaceLeftDialog;
import com.ichi2.anki.dialogs.DeckPickerConfirmDeleteDeckDialog;
import com.ichi2.anki.dialogs.DeckPickerContextMenu;
import com.ichi2.anki.dialogs.DeckPickerExportCompleteDialog;
import com.ichi2.anki.dialogs.DeckPickerNoSpaceLeftDialog;
import com.ichi2.anki.dialogs.DialogHandler;
import com.ichi2.anki.dialogs.ExportDialog;
import com.ichi2.anki.dialogs.ImportDialog;
import com.ichi2.anki.dialogs.MediaCheckDialog;
import com.ichi2.anki.dialogs.SyncErrorDialog;
import com.ichi2.anki.exception.ConfirmModSchemaException;
import com.ichi2.anki.receiver.SdCardReceiver;
import com.ichi2.anki.stats.AnkiStatsTaskHandler;
import com.ichi2.anki.widgets.DeckAdapter;
import com.ichi2.async.Connection;
import com.ichi2.async.Connection.Payload;
import com.ichi2.async.DeckTask;
import com.ichi2.async.DeckTask.TaskData;
import com.ichi2.compat.CompatHelper;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Sched;
import com.ichi2.themes.StyledProgressDialog;
import com.ichi2.themes.Themes;
import com.ichi2.utils.VersionUtils;
import com.ichi2.widget.WidgetStatus;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import timber.log.Timber;

public class DeckPicker extends NavigationDrawerActivity implements OnShowcaseEventListener,
        StudyOptionsListener, DatabaseErrorDialog.DatabaseErrorDialogListener,
        SyncErrorDialog.SyncErrorDialogListener, ImportDialog.ImportDialogListener,
        MediaCheckDialog.MediaCheckDialogListener, ExportDialog.ExportDialogListener {

    public static final int CRAM_DECK_FRAGMENT = -1;

    private String mImportPath;

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

    
    private MaterialDialog mProgressDialog;
    private ShowcaseView mShowcaseDialog;

    private RecyclerView mRecyclerView;
    private LinearLayoutManager mRecyclerViewLayoutManager;
    private DeckAdapter mDeckListAdapter;
    private List<Sched.DeckDueTreeNode> mDeckList;

    private TextView mTodayTextView;
    private ImageButton mAddButton;

    private BroadcastReceiver mUnmountReceiver = null;

    private long mContextMenuDid;

    private EditText mDialogEditText;

    // active cards
    boolean mShowShowcaseView = false;
    // flag asking user to do a full sync which is used in upgrade path
    boolean mRecommendFullSync = false;

    /**
     * Keep track of which deck was last given focus in the deck list. If we find that this value
     * has changed between deck list refreshes, we need to recenter the deck list to the new current
     * deck (e.g., when a filtered deck was created in a study options fragment).
     */
    private Long mFocusedDeck;

    // ----------------------------------------------------------------------------
    // LISTENERS
    // ----------------------------------------------------------------------------

    private final OnClickListener mDeckExpanderClickListener = new OnClickListener() {
        @Override
        public void onClick(View view) {
            collapseContextMenuDeck((Long) view.getTag());
        }
    };

    private final OnClickListener mDeckClickListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            long deckId = (long) v.getTag();
            Timber.i("DeckPicker:: Selected deck with id %d", deckId);
            handleDeckSelection(deckId);
        }
    };

    private final View.OnLongClickListener mDeckLongClickListener = new View.OnLongClickListener() {
        @Override
        public boolean onLongClick(View v) {
            long deckId = (long) v.getTag();
            Timber.i("DeckPicker:: Long tapped on deck with id %d", deckId);
            if (mDeckList == null || mDeckList.size() == 0) {
                return true;
            }
            mContextMenuDid = deckId;
            String deckName = getCol().getDecks().name(mContextMenuDid);
            boolean hasSubdecks = getCol().getDecks().children(mContextMenuDid).size() > 0;
            boolean isCollapsed = getCol().getDecks().get(mContextMenuDid).
                    optBoolean("collapsed", false);
            showDialogFragment(DeckPickerContextMenu.newInstance(deckName, hasSubdecks,
                    isCollapsed));
            return true;
        }
    };

    DeckTask.TaskListener mImportAddListener = new DeckTask.TaskListener() {
        @SuppressWarnings("unchecked")
        @Override
        public void onPostExecute(DeckTask.TaskData result) {
            String message = "";
            Resources res = getResources();
            if (mProgressDialog != null && mProgressDialog.isShowing()) {
                mProgressDialog.dismiss();
            }
            if (result != null && result.getBoolean()) {
                int count = result.getInt();
                if (count < 0) {
                    if (count == -2) {
                        message = res.getString(R.string.import_log_no_apkg);
                    } else {
                        message = res.getString(R.string.import_log_error);
                    }
                    showSimpleMessageDialog(message, true);
                } else {
                    message = res.getString(R.string.import_log_success, count);
                    showSimpleMessageDialog(message);
                    updateDeckList();
                }
            } else {
                showSimpleMessageDialog(res.getString(R.string.import_log_error));
            }
            // delete temp file if necessary and reset import path so that it's not incorrectly imported next time
            // Activity starts
            if (getIntent().getBooleanExtra("deleteTempFile", false)) {
                new File(mImportPath).delete();
            }
            mImportPath = null;
        }


        @Override
        public void onPreExecute() {
            if (mProgressDialog == null || !mProgressDialog.isShowing()) {
                mProgressDialog = StyledProgressDialog.show(DeckPicker.this,
                        getResources().getString(R.string.import_title),
                        getResources().getString(R.string.import_importing), false);
            }
        }


        @Override
        public void onProgressUpdate(DeckTask.TaskData... values) {
            mProgressDialog.setContent(values[0].getString());
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
            if (result != null && result.getBoolean()) {
                int code = result.getInt();
                if (code == -2) {
                    // not a valid apkg file
                    showSimpleMessageDialog(res.getString(R.string.import_log_no_apkg));
                }
                updateDeckList();
                hideProgressBar();
            } else {
                showSimpleMessageDialog(res.getString(R.string.import_log_no_apkg), true);
            }
            // delete temp file if necessary and reset import path so that it's not incorrectly imported next time
            // Activity starts
            if (getIntent().getBooleanExtra("deleteTempFile", false)) {
                new File(mImportPath).delete();
            }
            mImportPath = null;
        }


        @Override
        public void onPreExecute() {
            if (mProgressDialog == null || !mProgressDialog.isShowing()) {
                mProgressDialog = StyledProgressDialog.show(DeckPicker.this,
                        getResources().getString(R.string.import_title),
                        getResources().getString(R.string.import_importing), false);
            }
        }


        @Override
        public void onProgressUpdate(DeckTask.TaskData... values) {
            mProgressDialog.setContent(values[0].getString());
        }


        @Override
        public void onCancelled() {
        }
    };

    DeckTask.TaskListener mExportListener = new DeckTask.TaskListener() {

        @Override
        public void onPreExecute() {
            mProgressDialog = StyledProgressDialog.show(DeckPicker.this, "",
                    getResources().getString(R.string.export_in_progress), false);
        }


        @Override
        public void onPostExecute(DeckTask.TaskData result) {
            if (mProgressDialog != null && mProgressDialog.isShowing()) {
                mProgressDialog.dismiss();
            }
            String exportPath = result.getString();
            if (exportPath != null) {
                showAsyncDialogFragment(DeckPickerExportCompleteDialog.newInstance(exportPath));
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

    /** Returns the navdrawer item that corresponds to this Activity. */
    @Override
    protected int getSelfNavDrawerItem() {
        return DRAWER_DECK_PICKER;
    }


    /** Called when the activity is first created. */
    @Override
    protected void onCreate(Bundle savedInstanceState) throws SQLException {
        Timber.d("onCreate()");
        Intent intent = getIntent();
        Themes.applyTheme(this);
        super.onCreate(savedInstanceState);

        SharedPreferences preferences = AnkiDroidApp.getSharedPrefs(getBaseContext());

        View mainView = getLayoutInflater().inflate(R.layout.deck_picker, null);
        setContentView(mainView);

        // check, if tablet layout
        View studyoptionsFrame = findViewById(R.id.studyoptions_fragment);
        // set protected variable from NavigationDrawerActivity
        mFragmented = studyoptionsFrame != null && studyoptionsFrame.getVisibility() == View.VISIBLE;

        Themes.setContentStyle(mFragmented ? mainView : mainView.findViewById(R.id.deckpicker_view),
                Themes.CALLER_DECKPICKER);
        sIsWholeCollection = !mFragmented;

        registerExternalStorageListener();

        // create inherited navigation drawer layout here so that it can be used by parent class
        initNavigationDrawer(mainView);
        if (savedInstanceState == null) {
            selectNavigationItem(DRAWER_DECK_PICKER);
        }

        setTitle(getResources().getString(R.string.app_name));

        mDeckList = new ArrayList<>();
        mRecyclerView = (RecyclerView) findViewById(R.id.files);
        mRecyclerView.addItemDecoration(new DividerItemDecoration(this, null, false, true));

        // specify a LinearLayoutManager for the RecyclerView
        mRecyclerViewLayoutManager = new LinearLayoutManager(this);
        mRecyclerView.setLayoutManager(mRecyclerViewLayoutManager);

        // create and set an adapter for the RecyclerView
        mDeckListAdapter = new DeckAdapter(mDeckList, getResources(), getLayoutInflater(), this);
        mDeckListAdapter.setDeckClickListener(mDeckClickListener);
        mDeckListAdapter.setDeckExpanderClickListener(mDeckExpanderClickListener);
        mDeckListAdapter.setDeckLongClickListener(mDeckLongClickListener);
        mRecyclerView.setAdapter(mDeckListAdapter);

        mTodayTextView = (TextView) findViewById(R.id.today_stats_text_view);
        final Resources res = getResources();
        mAddButton = (ImageButton) findViewById(R.id.fab);
        mAddButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                new MaterialDialog.Builder(DeckPicker.this)
                        .items(new String[] {res.getString(R.string.menu_add_note),
                                res.getString(R.string.menu_get_shared_decks),
                                res.getString(R.string.new_deck)})
                        .itemsCallback(new MaterialDialog.ListCallback() {
                            @Override
                            public void onSelection(MaterialDialog materialDialog, View view, int i,
                                                    CharSequence charSequence) {
                                switch(i) {
                                    case 0:
                                        hideShowcaseView();
                                        addNote();
                                        break;
                                    case 1:
                                        hideShowcaseView();
                                        if (colIsOpen())
                                            addSharedDeck();
                                        break;
                                    case 2:
                                        hideShowcaseView();
                                        mDialogEditText = new EditText(DeckPicker.this);
                                        mDialogEditText.setSingleLine(true);
                                        // mDialogEditText.setFilters(new InputFilter[] { mDeckNameFilter });
                                        new MaterialDialog.Builder(DeckPicker.this)
                                                .title(R.string.new_deck)
                                                .positiveText(R.string.dialog_ok)
                                                .customView(mDialogEditText, true)
                                                .callback(new MaterialDialog.ButtonCallback() {
                                                    @Override
                                                    public void onPositive(MaterialDialog dialog) {
                                                        String deckName = mDialogEditText.getText().toString()
                                                                .replaceAll("[\'\"\\n\\r\\[\\]\\(\\)]", "");
                                                        Timber.i("DeckPicker:: Creating new deck...");
                                                        getCol().getDecks().id(deckName, true);
                                                        updateDeckList();
                                                    }
                                                })
                                                .negativeText(R.string.dialog_cancel)
                                                .show();
                                }
                            }
                        })
                        .build().show();
            }
        });
        mTodayTextView = (TextView) findViewById(R.id.today_stats_text_view);
        // Show splash screen and load collection
        showStartupScreensAndDialogs(preferences, 0);
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

        // Show the welcome screen here if col empty to be sure that the action bar exists
        if (mShowShowcaseView && colIsOpen() && getCol().isEmpty() && mDeckList != null && mDeckList.size() <=1) {
            mShowShowcaseView = false;
            final Resources res = getResources();
            try {
                mShowcaseDialog = new ShowcaseView.Builder(this).setTarget(new ViewTarget(findViewById(R.id.fab)))
                        .setContentTitle(res.getString(R.string.studyoptions_welcome_title))
                        .setStyle(R.style.ShowcaseView_Light).setShowcaseEventListener(this)
                        .setOnClickListener(new OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                mShowcaseDialog.hide();
                                Intent helpIntent = new Intent("android.intent.action.VIEW", Uri.parse(res
                                        .getString(R.string.link_manual_getting_started)));
                                startActivityWithoutAnimation(helpIntent);
                            }
                        }).setContentText(res.getString(R.string.add_content_showcase_text)).hideOnTouchOutside().build();
                mShowcaseDialog.setButtonText(getResources().getString(R.string.help_title));
                RelativeLayout.LayoutParams lps = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lps.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
                lps.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
                int margin = ((Number) (getResources().getDisplayMetrics().density * 12)).intValue();
                lps.setMargins(margin, margin, margin, margin);
                mShowcaseDialog.setButtonPosition(lps);
            } catch (Exception e) {
                Timber.e(e, "Error showing ShowcaseView");
                Themes.showThemedToast(this, res.getString(R.string.add_content_showcase_text), false);
            }
        } else if (mShowcaseDialog != null && colIsOpen() && !getCol().isEmpty()) {
            hideShowcaseView();
        }

        // Hide import, export, and restore backup on ChromeOS as users
        // don't have access to the file system.
        if (CompatHelper.isChromebook()) {
            menu.findItem(R.id.action_restore_backup).setVisible(false);
            menu.findItem(R.id.action_import).setVisible(false);
            menu.findItem(R.id.action_export).setVisible(false);
        }

        return super.onCreateOptionsMenu(menu);
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
                Timber.i("DeckPicker:: Sync button pressed");
                sync();
                return true;

            case R.id.action_add_note_from_deck_picker:
                Timber.i("DeckPicker:: Add note button pressed");
                hideShowcaseView();
                addNote();
                return true;

            case R.id.action_shared_decks:
                Timber.i("DeckPicker:: Get shared deck button pressed");
                hideShowcaseView();
                if (colIsOpen()) {
                    addSharedDeck();
                }
                return true;

            case R.id.action_new_deck:
                Timber.i("DeckPicker:: Add deck button pressed");
                hideShowcaseView();
                mDialogEditText = new EditText(DeckPicker.this);
                mDialogEditText.setSingleLine(true);
                // mDialogEditText.setFilters(new InputFilter[] { mDeckNameFilter });
                new MaterialDialog.Builder(DeckPicker.this)
                        .title(R.string.new_deck)
                        .positiveText(R.string.create)
                        .customView(mDialogEditText, true)
                        .callback(new MaterialDialog.ButtonCallback() {
                            @Override
                            public void onPositive(MaterialDialog dialog) {
                                String deckName = mDialogEditText.getText().toString()
                                        .replaceAll("[\'\"\\n\\r\\[\\]\\(\\)]", "");
                                Timber.i("DeckPicker:: Creating new deck...");
                                getCol().getDecks().id(deckName, true);
                                updateDeckList();
                            }
                        })
                        .negativeText(R.string.dialog_cancel)
                        .show();
                return true;

            case R.id.action_import:
                Timber.i("DeckPicker:: Import button pressed");
                showImportDialog(ImportDialog.DIALOG_IMPORT_HINT);
                return true;

            case R.id.action_new_filtered_deck:
                Timber.i("DeckPicker:: New filtered deck button pressed");
                mDialogEditText = new EditText(DeckPicker.this);
                ArrayList<String> names = getCol().getDecks().allNames();
                int n = 1;
                String filteredDeckName = "Filtered Deck 1"; // TODO: needs to be a resource
                while (names.contains(filteredDeckName)) {
                    n++;
                    filteredDeckName = "Filtered Deck " + n;
                }
                mDialogEditText.setText(filteredDeckName);
                // mDialogEditText.setFilters(new InputFilter[] { mDeckNameFilter });
                new MaterialDialog.Builder(DeckPicker.this)
                        .title(res.getString(R.string.new_deck))
                        .customView(mDialogEditText, true)
                        .positiveText(res.getString(R.string.create))
                        .negativeText(res.getString(R.string.dialog_cancel))
                        .callback(new MaterialDialog.ButtonCallback() {
                            @Override
                            public void onPositive(MaterialDialog dialog) {
                                String filteredDeckName = mDialogEditText.getText().toString();
                                Timber.i("DeckPicker:: Creating filtered deck...");
                                getCol().getDecks().newDyn(filteredDeckName);
                                openStudyOptions(true);
                            }
                        })
                        .show();
                return true;

            case R.id.action_check_database:
                Timber.i("DeckPicker:: Check database button pressed");
                showDatabaseErrorDialog(DatabaseErrorDialog.DIALOG_CONFIRM_DATABASE_CHECK);
                return true;

            case R.id.action_check_media:
                Timber.i("DeckPicker:: Check media button pressed");
                showMediaCheckDialog(MediaCheckDialog.DIALOG_CONFIRM_MEDIA_CHECK);
                return true;

            case R.id.action_restore_backup:
                Timber.i("DeckPicker:: Restore from backup button pressed");
                showDatabaseErrorDialog(DatabaseErrorDialog.DIALOG_CONFIRM_RESTORE_BACKUP);
                return true;

            case R.id.action_export:
                Timber.i("DeckPicker:: Export collection button pressed");
                String msg = getResources().getString(R.string.confirm_apkg_export);
                showDialogFragment(ExportDialog.newInstance(msg));
                return true;
            default:
                return super.onOptionsItemSelected(item);

        }
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);

        if (resultCode == RESULT_MEDIA_EJECTED) {
            onSdCardNotMounted();
            return;
        } else if (resultCode == RESULT_DB_ERROR) {
            handleDbError();
            return;
        }

        if (requestCode == REPORT_ERROR) {
            showStartupScreensAndDialogs(AnkiDroidApp.getSharedPrefs(getBaseContext()), 4);
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
        } else if (requestCode == ADD_SHARED_DECKS) {
            if (intent != null) {
                mImportPath = intent.getStringExtra("importPath");
            }
            if (colIsOpen() && mImportPath != null) {
                DeckTask.launchDeckTask(DeckTask.TASK_TYPE_IMPORT, mImportAddListener, new TaskData(getCol(),
                        mImportPath, true));
                mImportPath = null;
            }
        }
    }


    @Override
    protected void onResume() {
        Timber.d("onResume()");
        super.onResume();
        if (colIsOpen()) {
            updateDeckList();
            hideProgressBar();
        }
        setTitle(getResources().getString(R.string.app_name));
        sIsWholeCollection = !mFragmented;
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
        Timber.d("onPause()");

        super.onPause();
    }


    @Override
    protected void onStop() {
        Timber.d("onStop()");
        super.onStop();
        if (colIsOpen()) {
            WidgetStatus.update(this);
            UIUtils.saveCollectionInBackground(this);
        }
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mUnmountReceiver != null) {
            unregisterReceiver(mUnmountReceiver);
        }
        if (mProgressDialog != null && mProgressDialog.isShowing()) {
            mProgressDialog.dismiss();
        }
        Timber.d("onDestroy()");
    }


    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0) {
            Timber.i("DeckPicker:: onBackPressed()");
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
            // Themes.showThemedToast(this, getResources().getString(R.string.backup_collection), true);
        }
        // Force a full sync if flag was set in upgrade path, asking the user to confirm if necessary
        if (mRecommendFullSync) {
            mRecommendFullSync = false;
            try {
                col.modSchema();
            } catch (ConfirmModSchemaException e) {
                // If libanki determines it's necessary to confirm the full sync then show a confirmation dialog
                // We have to show the dialog via the DialogHandler since this method is called via a Loader
                Resources res = getResources();
                Message handlerMessage = Message.obtain();
                handlerMessage.what = DialogHandler.MSG_SHOW_FORCE_FULL_SYNC_DIALOG;
                Bundle handlerMessageData = new Bundle();
                handlerMessageData.putString("message", res.getString(R.string.full_sync_confirmation_upgrade) +
                        "\n\n" + res.getString(R.string.full_sync_confirmation));
                handlerMessage.setData(handlerMessageData);
                getDialogHandler().sendMessage(handlerMessage);
            }
        }
        // prepare deck counts and mini-today-statistic
        updateDeckList();
        // Open StudyOptionsFragment if in fragmented mode
        if (mFragmented) {
            // Create the fragment in a new handler since Android won't let you perform fragment
            // transactions in a loader's onLoadFinished.
            new Handler().post(new Runnable() {
                public void run() {
                    loadStudyOptionsFragment(false);
                }
            });
        }
        hideProgressBar();
        // Show the ShowcaseView tutorial unless in tablet mode (which shows it after loading StudyOptionsFragment)
        if (!mFragmented) {
            reloadShowcaseView();
        }
    }


    @Override
    protected void onCollectionLoadError() {
        getDialogHandler().sendEmptyMessage(DialogHandler.MSG_SHOW_COLLECTION_LOADING_ERROR_DIALOG);
    }


    private void addNote() {
        Preferences.COMING_FROM_ADD = true;
        Intent intent = new Intent(DeckPicker.this, NoteEditor.class);
        intent.putExtra(NoteEditor.EXTRA_CALLER, NoteEditor.CALLER_DECKPICKER);
        startActivityForResultWithAnimation(intent, ADD_NOTE, ActivityTransitionAnimation.LEFT);
    }


    private void showStartupScreensAndDialogs(SharedPreferences preferences, int skip) {
        if (!AnkiDroidApp.isSdCardMounted()) {
            // SD card not mounted
            onSdCardNotMounted();
        } else if (!CollectionHelper.isCurrentAnkiDroidDirAccessible(this)) {
            // AnkiDroid directory inaccessible
            Intent i = new Intent(this, Preferences.class);
            startActivityWithoutAnimation(i);
            Themes.showThemedToast(this, getResources().getString(R.string.directory_inaccessible), false);
        } else if (!BackupManager.enoughDiscSpace(CollectionHelper.getCurrentAnkiDroidDirectory(this))) {
            // Not enough space to do backup
            showDialogFragment(DeckPickerNoSpaceLeftDialog.newInstance());
        } else if (preferences.getBoolean("noSpaceLeft", false)) {
            // No space left
            showDialogFragment(DeckPickerBackupNoSpaceLeftDialog.newInstance());
            preferences.edit().putBoolean("noSpaceLeft", false).commit();
        } else if (preferences.getString("lastVersion", "").equals("")) {
            // Fresh install
            preferences.edit().putString("lastVersion", VersionUtils.getPkgVersionName()).commit();
            startLoadingCollection();
        } else if (skip < 2 && !preferences.getString("lastVersion", "").equals(VersionUtils.getPkgVersionName())) {
            // AnkiDroid is being updated and a collection already exists. We check if we are upgrading
            // to a version that contains additions to the database integrity check routine that we would
            // like to run on all collections. A missing version number is assumed to be a fresh
            // installation of AnkiDroid and we don't run the check.
            int current = VersionUtils.getPkgVersionCode();
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
            // Delete the media database made by any version before 2.3 beta due to upgrade errors.
            // It is rebuilt on the next sync or media check
            if (previous < 20300200) {
                File mediaDb = new File(CollectionHelper.getCurrentAnkiDroidDirectory(this), "collection.media.ad.db2");
                if (mediaDb.exists()) {
                    mediaDb.delete();
                }
            }
            // Recommend the user to do a full-sync if they're upgrading from before 2.3.1beta8
            if (previous < 20301208) {
                mRecommendFullSync = true;
            }
            // Check if preference upgrade or database check required, otherwise go to new feature screen
            if (previous < AnkiDroidApp.CHECK_DB_AT_VERSION || previous < AnkiDroidApp.CHECK_PREFERENCES_AT_VERSION) {
                if (previous < AnkiDroidApp.CHECK_PREFERENCES_AT_VERSION) {
                    CompatHelper.removeHiddenPreferences(this.getApplicationContext());
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
                if (VersionUtils.isReleaseVersion()) {
                    Intent infoIntent = new Intent(this, Info.class);
                    infoIntent.putExtra(Info.TYPE_EXTRA, Info.TYPE_NEW_VERSION);

                    if (skip != 0) {
                        startActivityForResultWithAnimation(infoIntent, SHOW_INFO_NEW_VERSION,
                                ActivityTransitionAnimation.LEFT);
                    } else {
                        startActivityForResultWithoutAnimation(infoIntent, SHOW_INFO_NEW_VERSION);
                    }
                } else {
                    // Don't show new features dialog for development builds
                    preferences.edit().putString("lastVersion", VersionUtils.getPkgVersionName()).commit();
                    showStartupScreensAndDialogs(preferences, 2);
                }
            }
        } else {
            // This is the main call when there is nothing special required
            startLoadingCollection();
        }
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
            boolean safeDisplayMode = preferences.getBoolean("eInkDisplay", false) || CompatHelper.isNook()
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
        // reset swipeSensitivity from 2.4beta3
        if (previousVersionCode < 20400203) {
            preferences.edit().putInt("swipeSensitivity", 100).commit();
        }

        // when upgrading from before 2.5alpha33
        if (previousVersionCode < 20500133) {
            // Card zooming behaviour was changed the preferences renamed
            int oldCardZoom = preferences.getInt("relativeDisplayFontSize", 100);
            int oldImageZoom = preferences.getInt("relativeImageSize", 100);
            preferences.edit().putInt("cardZoom", oldCardZoom).commit();
            preferences.edit().putInt("imageZoom", oldImageZoom).commit();
        }
    }


    // Show dialogs to deal with database loading issues etc
    @Override
    public void showDatabaseErrorDialog(int id) {
        AsyncDialogFragment newFragment = DatabaseErrorDialog.newInstance(id);
        showAsyncDialogFragment(newFragment);
    }


    @Override
    public void showMediaCheckDialog(int id) {
        showAsyncDialogFragment(MediaCheckDialog.newInstance(id));
    }


    @Override
    public void showMediaCheckDialog(int id, List<List<String>> checkList) {
        showAsyncDialogFragment(MediaCheckDialog.newInstance(id, checkList));
    }


    // Show dialogs to deal with sync issues etc
    @Override
    public void showSyncErrorDialog(int id) {
        showSyncErrorDialog(id, "");
    }


    @Override
    public void showSyncErrorDialog(int id, String message) {
        AsyncDialogFragment newFragment = SyncErrorDialog.newInstance(id, message);
        showAsyncDialogFragment(newFragment);
    }

    /**
     *  Show log message after sync, using "Sync Error" as the dialog title, and reload activity
     * @param message
     */
    private void showSyncLogDialog(String message) {
        // Reload activity since collection always closed at end of sync
        showSyncLogDialog(message, true);
    }

    /**
     *  Show log message after sync, and reload activity
     * @param message
     * @param error Show "Sync Error" as dialog title if this flag is set, otherwise use no title
     */
    private void showSyncLogDialog(String message, boolean error) {
        // Reload activity since collection always closed at end of sync
        if (error) {
            String title = getResources().getString(R.string.sync_error);
            showSimpleMessageDialog(title, message, true);
        } else {
            showSimpleMessageDialog(message, true);
        }
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


    public void onSdCardNotMounted() {
        Themes.showThemedToast(this, getResources().getString(R.string.sd_card_not_mounted), false);
        finishWithoutAnimation();
    }


    // Callback method to submit error report
    @Override
    public void sendErrorReport() {
        AnkiDroidApp.sendExceptionReport(new RuntimeException(), "DeckPicker.sendErrorReport");
    }


    // Callback method to handle repairing deck
    @Override
    public void repairDeck() {
        DeckTask.launchDeckTask(DeckTask.TASK_TYPE_REPAIR_DECK, new DeckTask.TaskListener() {

            @Override
            public void onPreExecute() {
                mProgressDialog = StyledProgressDialog.show(DeckPicker.this, "",
                        getResources().getString(R.string.backup_repair_deck_progress), false);
            }


            @Override
            public void onPostExecute(DeckTask.TaskData result) {
                if (mProgressDialog != null && mProgressDialog.isShowing()) {
                    mProgressDialog.dismiss();
                }
                if (result != null && result.getBoolean()) {
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
        }, new DeckTask.TaskData(getCol(), CollectionHelper.getCollectionPath(this)));
    }


    // Callback method to handle database integrity check
    @Override
    public void integrityCheck() {
        DeckTask.launchDeckTask(DeckTask.TASK_TYPE_CHECK_DATABASE, new DeckTask.TaskListener() {
            @Override
            public void onPreExecute() {
                mProgressDialog = StyledProgressDialog.show(DeckPicker.this, "",
                        getResources().getString(R.string.check_db_message), false);
            }


            @Override
            public void onPostExecute(TaskData result) {
                if (mProgressDialog != null && mProgressDialog.isShowing()) {
                    mProgressDialog.dismiss();
                }
                if (result != null && result.getBoolean()) {
                    String msg = "";
                    long shrunk = Math.round(result.getLong() / 1024.0);
                    if (shrunk > 0.0) {
                        msg = String.format(Locale.getDefault(),
                                getResources().getString(R.string.check_db_acknowledge_shrunk), (int) shrunk);
                    } else {
                        msg = getResources().getString(R.string.check_db_acknowledge);
                    }
                    // Show result of database check and restart the app
                    showSimpleMessageDialog(msg, true);
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
                        getResources().getString(R.string.check_media_message), false);
            }


            @Override
            public void onPostExecute(TaskData result) {
                if (mProgressDialog != null && mProgressDialog.isShowing()) {
                    mProgressDialog.dismiss();
                }
                if (result != null && result.getBoolean()) {
                    @SuppressWarnings("unchecked")
                    List<List<String>> checkList = (List<List<String>>) result.getObjArray()[0];
                    showMediaCheckDialog(MediaCheckDialog.DIALOG_MEDIA_CHECK_RESULTS, checkList);
                } else {
                    showSimpleMessageDialog(getResources().getString(R.string.check_media_failed));
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
            m.removeFile(fname);
        }
        showSimpleMessageDialog(String.format(getResources().getString(R.string.check_media_deleted), unused.size()));
    }


    @Override
    public void exit() {
        CollectionHelper.getInstance().closeCollection(false);
        finishWithoutAnimation();
        System.exit(0);
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
        sync(null);
    }


    /**
     * The mother of all syncing attempts. This might be called from sync() as first attempt to sync a collection OR
     * from the mSyncConflictResolutionListener if the first attempt determines that a full-sync is required.
     *
     * @param syncConflictResolution Either "upload" or "download", depending on the user's choice.
     */
    @Override
    public void sync(String syncConflictResolution) {
        SharedPreferences preferences = AnkiDroidApp.getSharedPrefs(getBaseContext());
        String hkey = preferences.getString("hkey", "");
        if (hkey.length() == 0) {
            showSyncErrorDialog(SyncErrorDialog.DIALOG_USER_NOT_LOGGED_IN_SYNC);
        } else {
            Connection.sync(mSyncListener,
                    new Connection.Payload(new Object[] { hkey, preferences.getBoolean("syncFetchesMedia", true),
                            syncConflictResolution }, CollectionHelper.getInstance().getCol(this)));
        }
    }


    private Connection.TaskListener mSyncListener = new Connection.TaskListener() {

        String currentMessage;
        long countUp;
        long countDown;

        @Override
        public void onDisconnected() {
            showSyncLogDialog(getResources().getString(R.string.youre_offline));
        }


        @Override
        public void onPreExecute() {
            countUp = 0;
            countDown = 0;
            if (mProgressDialog == null || !mProgressDialog.isShowing()) {
                mProgressDialog = StyledProgressDialog
                        .show(DeckPicker.this, getResources().getString(R.string.sync_title),
                                getResources().getString(R.string.sync_title) + "\n"
                                        + getResources().getString(R.string.sync_up_down_size, countUp, countDown),
                                false);
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
                mProgressDialog.setContent(currentMessage + "\n"
                        + res
                        .getString(R.string.sync_up_down_size, countUp / 1024, countDown / 1024));
            }
        }


        @SuppressWarnings("unchecked")
        @Override
        public void onPostExecute(Payload data) {
            String dialogMessage = "";
            String syncMessage = "";
            Timber.d("Sync Listener onPostExecute()");
            Resources res = getResources();
            try {
                if (mProgressDialog != null && mProgressDialog.isShowing()) {
                    mProgressDialog.dismiss();
                }
            } catch (IllegalArgumentException e) {
                Timber.e(e, "Could not dismiss mProgressDialog. The Activity must have been destroyed while the AsyncTask was running");
                AnkiDroidApp.sendExceptionReport(e, "DeckPicker.onPostExecute", "Could not dismiss mProgressDialog");
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
                        // then show not logged in dialog
                        showSyncErrorDialog(SyncErrorDialog.DIALOG_USER_NOT_LOGGED_IN_SYNC);
                    } else if (resultType.equals("noChanges")) {
                        // show no changes message, use false flag so we don't show "sync error" as the Dialog title
                        dialogMessage = res.getString(R.string.sync_no_changes_message);
                        showSyncLogDialog(joinSyncMessages(dialogMessage, syncMessage), false);
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
                        showSyncLogDialog(joinSyncMessages(dialogMessage, syncMessage));
                    } else if (resultType.equals("fullSync")) {
                        if (getCol().isEmpty()) {
                            // don't prompt user to resolve sync conflict if local collection empty
                            sync("download");
                            // TODO: Also do reverse check to see if AnkiWeb collection is empty if Anki Desktop
                            // implements it
                        } else {
                            // If can't be resolved then automatically then show conflict resolution dialog
                            showSyncErrorDialog(SyncErrorDialog.DIALOG_SYNC_CONFLICT_RESOLUTION);
                        }
                    } else if (resultType.equals("dbError")  || resultType.equals("basicCheckFailed")) {
                        dialogMessage = res.getString(R.string.sync_corrupt_database, R.string.repair_deck);
                        showSyncLogDialog(joinSyncMessages(dialogMessage, syncMessage));
                    } else if (resultType.equals("overwriteError")) {
                        dialogMessage = res.getString(R.string.sync_overwrite_error);
                        showSyncLogDialog(joinSyncMessages(dialogMessage, syncMessage));
                    } else if (resultType.equals("remoteDbError")) {
                        dialogMessage = res.getString(R.string.sync_remote_db_error);
                        showSyncLogDialog(joinSyncMessages(dialogMessage, syncMessage));
                    } else if (resultType.equals("sdAccessError")) {
                        dialogMessage = res.getString(R.string.sync_write_access_error);
                        showSyncLogDialog(joinSyncMessages(dialogMessage, syncMessage));
                    } else if (resultType.equals("finishError")) {
                        dialogMessage = res.getString(R.string.sync_log_finish_error);
                        showSyncLogDialog(joinSyncMessages(dialogMessage, syncMessage));
                    } else if (resultType.equals("connectionError")) {
                        dialogMessage = res.getString(R.string.sync_connection_error);
                        showSyncLogDialog(joinSyncMessages(dialogMessage, syncMessage));
                    } else if (resultType.equals("IOException")) {
                        handleDbError();
                    } else if (resultType.equals("genericError")) {
                        dialogMessage = res.getString(R.string.sync_generic_error);
                        showSyncLogDialog(joinSyncMessages(dialogMessage, syncMessage));
                    } else if (resultType.equals("OutOfMemoryError")) {
                        dialogMessage = res.getString(R.string.error_insufficient_memory);
                        showSyncLogDialog(joinSyncMessages(dialogMessage, syncMessage));
                    } else if (resultType.equals("sanityCheckError")) {
                        dialogMessage = res.getString(R.string.sync_sanity_failed);
                        showSyncErrorDialog(SyncErrorDialog.DIALOG_SYNC_SANITY_ERROR,
                                joinSyncMessages(dialogMessage, syncMessage));
                    } else if (resultType.equals("serverAbort")) {
                        // syncMsg has already been set above, no need to fetch it here.
                        showSyncLogDialog(joinSyncMessages(dialogMessage, syncMessage));
                    } else {
                        if (result.length > 1 && result[1] instanceof Integer) {
                            int type = (Integer) result[1];
                            switch (type) {
                                case 501:
                                    dialogMessage = res.getString(R.string.sync_error_501_upgrade_required);
                                    break;
                                case 503:
                                    dialogMessage = res.getString(R.string.sync_too_busy);
                                    break;
                                case 409:
                                    dialogMessage = res.getString(R.string.sync_error_409);
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
                        showSyncLogDialog(joinSyncMessages(dialogMessage, syncMessage));
                    }
                }
            } else {
                if (data.data[2] != null) {
                    dialogMessage = (String) data.data[2];
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
                showSyncLogDialog(joinSyncMessages(dialogMessage, syncMessage), false);

                // Note: the interface is not refreshed since the activity is restarted after sync.
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
    public void exportApkg(String filename, Long did, boolean includeSched, boolean includeMedia) {
        // get export path
        File colPath = new File(getCol().getPath());
        File exportDir = new File(colPath.getParentFile(), "export");
        exportDir.mkdirs();
        File exportPath;
        if (filename != null) {
            // filename has been explicitly specified
            exportPath = new File(exportDir, filename);
        } else if (did != null) {
            // filename not explicitly specified, but a deck has been specified so use deck name
            try {
                exportPath = new File(exportDir, getCol().getDecks().get(did).getString("name").replaceAll("\\W+", "_") + ".apkg");
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
        } else if (!includeSched) {
            // full export without scheduling is assumed to be shared with someone else -- use "All Decks.apkg"
            exportPath = new File(exportDir, "All Decks.apkg");
        } else {
            // full collection export -- use "collection.apkg"
            exportPath = new File(exportDir, colPath.getName().replace(".anki2", ".apkg"));
        }
        // add input arguments to new generic structure
        Object[] inputArgs = new Object[5];
        inputArgs[0] = getCol();
        inputArgs[1] = exportPath.getPath();
        inputArgs[2] = did;
        inputArgs[3] = includeSched;
        inputArgs[4] = includeMedia;
        DeckTask.launchDeckTask(DeckTask.TASK_TYPE_EXPORT_APKG, mExportListener, new TaskData(inputArgs));
    }


    public void emailFile(String path) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("message/rfc822");
        intent.putExtra(Intent.EXTRA_SUBJECT, "AnkiDroid Apkg");
        File attachment = new File(path);
        if (attachment.exists()) {
            Uri uri = Uri.fromFile(attachment);
            intent.putExtra(Intent.EXTRA_STREAM, uri);
        }
        try {
            startActivityWithoutAnimation(intent);
        } catch (ActivityNotFoundException e) {
            Themes.showThemedToast(this, getResources().getString(R.string.no_email_client), false);
        }
    }


    /**
     * Load a new studyOptionsFragment. If withDeckOptions is true, the deck options activity will
     * be loaded on top of it. Use this flag when creating a new filtered deck to allow the user to
     * modify the filter settings before being shown the fragment. The fragment itself will handle
     * rebuilding the deck if the settings change.
     */
    private void loadStudyOptionsFragment(boolean withDeckOptions) {
        StudyOptionsFragment details = StudyOptionsFragment.newInstance(withDeckOptions);
        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
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
     * Show a message when the SD card is ejected
     */
    private void registerExternalStorageListener() {
        if (mUnmountReceiver == null) {
            mUnmountReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (intent.getAction().equals(SdCardReceiver.MEDIA_EJECT)) {
                        onSdCardNotMounted();
                    } else if (intent.getAction().equals(SdCardReceiver.MEDIA_MOUNT)) {
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
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(getResources().getString(R.string.shared_decks_url)));
        startActivityWithoutAnimation(intent);
    }


    private void openStudyOptions(boolean withDeckOptions) {
        if (mFragmented) {
            // The fragment will show the study options screen instead of launching a new activity.
            loadStudyOptionsFragment(withDeckOptions);
        } else {
            Intent intent = new Intent();
            intent.putExtra("withDeckOptions", withDeckOptions);
            intent.setClass(this, StudyOptionsActivity.class);
            startActivityForResultWithAnimation(intent, SHOW_STUDYOPTIONS, ActivityTransitionAnimation.LEFT);
        }
    }


    private void handleDeckSelection(long did) {
        getCol().getDecks().select(did);
        mFocusedDeck = did;
        openStudyOptions(false);
    }


    /**
     * Return true if deck is not a children of a collapsed deck
     */
    private boolean deckIsShown(List<Sched.DeckDueTreeNode> nodes, Long current) {
        for (Sched.DeckDueTreeNode node : nodes) {
            if (node.did == current) {
                return true;
            }
            if (!getCol().getDecks().get(node.did).optBoolean("collapsed", false) && deckIsShown(node.children, current)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Scroll the deck list so that it is centered on the current deck.
     *
     * @param did The deck ID of the deck to select.
     */
    private void scrollDecklistToDeck(long did) {
        int position = findDeckPositionWithId(mDeckList, did, 0);
        if (position > -1) {
            mRecyclerViewLayoutManager.scrollToPositionWithOffset(position, (mRecyclerView.getHeight() / 2));
        }
    }

    /**
     * Given a list of {@link Sched.DeckDueTreeNode} objects, find the list position matching the id.
     *
     * @param nodes The list of {@link Sched.DeckDueTreeNode} objects
     * @param did The deck id to find
     * @param position The current deck's position in the list
     *
     * @return The position, if found, and -1 otherwise.
     */
    private int findDeckPositionWithId(List<Sched.DeckDueTreeNode> nodes, long did, int position) {
        for (int i=0; i<nodes.size(); i++) {
            // return the position if the deck ids match
            if (nodes.get(i).did == did) {
                // if we're at depth zero, return this loop's value
                // otherwise return the position (the cardview we want)
                return (nodes.get(i).depth == 0) ? i : position;
            }

            // keep searching through this node's children
            if (nodes.get(i).children.size() > 0) {
                // if we find the deck id, return this child's parent position
                int childPosition = findDeckPositionWithId(nodes.get(i).children, did, (nodes.get(i).depth == 0) ? i : position);
                if (childPosition > -1) {
                    return childPosition;
                }
            }
        }

        return -1;
    }

    /**
     * Given a list of {@link Sched.DeckDueTreeNode} objects, find the node matching the deck id.
     *
     * @param nodes The list of {@link Sched.DeckDueTreeNode} objects
     * @param did The deck id to find
     *
     * @return The {@link Sched.DeckDueTreeNode}, if found, and null otherwise.
     */
    private Sched.DeckDueTreeNode findDeckWithId(List<Sched.DeckDueTreeNode> nodes, long did) {
        for (int i=0; i<nodes.size(); i++) {
            if (nodes.get(i).did == did) {
                return nodes.get(i);
            }

            if (nodes.get(i).children.size() > 0) {
                return findDeckWithId(nodes.get(i).children, did);
            }
        }

        return null;
    }


    /**
     * Launch an asynchronous task to rebuild the deck list and recalculate the deck counts. Use this
     * after any change to a deck (e.g., rename, collapse, add/delete) that needs to be reflected
     * in the deck list.
     *
     * This method also triggers an update for the widget to reflect the newly calculated counts.
     */
    private void updateDeckList() {
        DeckTask.launchDeckTask(DeckTask.TASK_TYPE_LOAD_DECK_COUNTS, new DeckTask.TaskListener() {
            // Totals accumulated as each deck is processed
            private int nNew;
            private int nLrn;
            private int nRev;

            @Override
            public void onPreExecute() {
                Timber.d("Refreshing deck list");
            }

            @Override
            public void onPostExecute(TaskData result) {
                if (result == null) {
                    Timber.e("null result loading deck counts");
                    onCollectionLoadError();
                    return;
                }
                List<Sched.DeckDueTreeNode> nodes = (List<Sched.DeckDueTreeNode>) result.getObjArray()[0];
                mDeckList.clear();
                _renderDeckTree(nodes);
                mDeckListAdapter.notifyDataSetChanged();

                // Set the "x due in y minutes" subtitle
                try {
                    int eta = getCol().getSched().eta(new int[]{nNew, nLrn, nRev});
                    int due = nNew + nLrn + nRev;
                    Resources res = getResources();
                    if (getCol().cardCount() != -1) {
                        String time = "-";
                        if (eta != -1) {
                            time = res.getQuantityString(R.plurals.deckpicker_title_minutes, eta, eta);
                        }
                        UIUtils.setSubtitle(DeckPicker.this,
                                res.getQuantityString(R.plurals.deckpicker_title, due, due, time));
                    }
                } catch (RuntimeException e) {
                    Timber.e(e, "RuntimeException setting time remaining");
                    onCollectionLoadError();
                    return;
                }

                Long current = getCol().getDecks().current().optLong("id");
                if (!deckIsShown(nodes, current)) {
                    // clear selection if deck is hidden
                    //                    mDeckListView.clearChoices(); // TODO AVP
                } else if (mFocusedDeck == null || !mFocusedDeck.equals(current)) {
                    scrollDecklistToDeck(current);
                    mFocusedDeck = current;
                }

                // update widget
                WidgetStatus.update(DeckPicker.this, nodes);
                // update options menu and clear welcome screen
                supportInvalidateOptionsMenu();
                // Update the mini statistics bar as well
                AnkiStatsTaskHandler.createSmallTodayOverview(getCol(), mTodayTextView);
            }

            @Override
            public void onProgressUpdate(TaskData... values) {
            }

            @Override
            public void onCancelled() {
            }


            private void _renderDeckTree(List<Sched.DeckDueTreeNode> nodes) {
                _renderDeckTree(nodes, 0);
            }


            private void _renderDeckTree(List<Sched.DeckDueTreeNode> nodes, int depth) {
                for (Sched.DeckDueTreeNode node : nodes) {
                    // if this node's parent is collapsed, don't add it to the deck list
                    for (JSONObject parent : getCol().getDecks().parents(node.did)) {
                        if (parent.optBoolean("collapsed")) {
                            return;
                        }
                    }

                    // add node to deck
                    mDeckList.add(node);

                    _deckRow(node, depth);
                }
            }


            /**
             * Create a row in the deck list using the given node.
             *
             * This method determines various visual elements of each row:
             *  - Indenting level based on depth if it's a subdeck
             *  - Addition of a unicode arrow to indicate subdeck status
             *  - Addition of an indicator to notify collapsed state
             *  - The type of padding given to each row
             *
             * NOTE: Each row in the list also contains the deck ID (did) which is later fetched
             * to perform operations on the correct deck when the user interacts with the list.
             *  @param node The row data.
             * @param depth The subdeck level this node is at.
             */
            private void _deckRow(Sched.DeckDueTreeNode node, int depth) {
                node.depth = depth;

                // Add this node's counts to the totals if it's a parent deck
                if (depth == 0) {
                    nNew += node.newCount;
                    nLrn += node.lrnCount;
                    nRev += node.revCount;
                }

                _renderDeckTree(node.children, depth + 1);
            }
        }, new TaskData(getCol()));
    }

    // Callback to collapse currently selected deck
    public void collapseContextMenuDeck() {
        collapseContextMenuDeck(mContextMenuDid);
    }

    public void collapseContextMenuDeck(long did) {
            try {
            JSONObject deck = getCol().getDecks().get(did);
            if (getCol().getDecks().children(did).size() > 0) {
                deck.put("collapsed", !deck.getBoolean("collapsed"));
                getCol().getDecks().save(deck);
                updateDeckList();
                dismissAllDialogFragments();
            }
        } catch (JSONException e1) {
            // do nothing
        }
    }


    // Callback to show study options for currently selected deck
    public void showContextMenuDeckOptions() {
        // open deck options
        if (getCol().getDecks().isDyn(mContextMenuDid)) {
            // open cram options if filtered deck
            Intent i = new Intent(DeckPicker.this, FilteredDeckOptions.class);
            i.putExtra("did", mContextMenuDid);
            startActivityWithAnimation(i, ActivityTransitionAnimation.FADE);
        } else {
            // otherwise open regular options
            Intent i = new Intent(DeckPicker.this, DeckOptions.class);
            i.putExtra("did", mContextMenuDid);
            startActivityWithAnimation(i, ActivityTransitionAnimation.FADE);
        }
    }


    // Callback to show export dialog for currently selected deck
    public void showContextMenuExportDialog() {
        Long did = mContextMenuDid;
        String msg;
        try {
            msg = getResources().getString(R.string.confirm_apkg_export_deck, getCol().getDecks().get(did).get("name"));
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        showDialogFragment(ExportDialog.newInstance(msg, did));
    }


    // Callback to show dialog to rename the current deck
    public void renameContextMenuDeckDialog() {
        Resources res = getResources();
        mDialogEditText = new EditText(DeckPicker.this);
        mDialogEditText.setSingleLine();
        mDialogEditText.setText(getCol().getDecks().name(mContextMenuDid));
        // mDialogEditText.setFilters(new InputFilter[] { mDeckNameFilter });

        new MaterialDialog.Builder(DeckPicker.this)
                .title(res.getString(R.string.contextmenu_deckpicker_rename_deck))
                .customView(mDialogEditText, true)
                .positiveText(res.getString(R.string.rename))
                .negativeText(res.getString(R.string.dialog_cancel))
                .callback(new MaterialDialog.ButtonCallback() {
                    @Override
                    public void onPositive(MaterialDialog dialog) {
                        String newName = mDialogEditText.getText().toString().replaceAll("\"", "");
                        Timber.i("DeckPicker:: Renaming deck...", newName);
                        Collection col = getCol();
                        if (col != null) {
                            if (col.getDecks().rename(col.getDecks().get(mContextMenuDid), newName)) {
                                Sched.DeckDueTreeNode node = findDeckWithId(mDeckList, mContextMenuDid);
                                if (node != null) {
                                    node.names = new String[]{newName};
                                }
                                updateDeckList();
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
                        mDeckListAdapter.notifyDataSetChanged();
                        updateDeckList();
                    }

                    @Override
                    public void onNegative(MaterialDialog dialog) {
                        dismissAllDialogFragments();
                    }
                })
                .build().show();
    }


    // Callback to show confirm deck deletion dialog before deleting currently selected deck
    public void confirmDeckDeletion(DialogFragment parent) {
        Resources res = getResources();
        if (!colIsOpen() || mDeckList == null || mDeckList.size() == 0) {
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
        //DeckPickerConfirmDeleteDeckDialog.newInstance(msg).show(parent.getChildFragmentManager(), "dialog");
        showDialogFragment(DeckPickerConfirmDeleteDeckDialog.newInstance(msg));
    }


    // Callback to delete currently selected deck
    public void deleteContextMenuDeck() {
        DeckTask.launchDeckTask(DeckTask.TASK_TYPE_DELETE_DECK, new DeckTask.TaskListener() {
            // Flag to indicate if the deck being deleted is the current deck.
            private boolean removingCurrent;

            @Override
            public void onPreExecute() {
                mProgressDialog = StyledProgressDialog.show(DeckPicker.this, "",
                        getResources().getString(R.string.delete_deck), false);
                if (mContextMenuDid == getCol().getDecks().current().optLong("id")) {
                    removingCurrent = true;
                }
            }


            @SuppressWarnings("unchecked")
            @Override
            public void onPostExecute(TaskData result) {
                if (result == null) {
                    return;
                }
                // In fragmented mode, if the deleted deck was the current deck, we need to reload
                // the study options fragment with a valid deck and re-center the deck list to the
                // new current deck. Otherwise we just update the list normally.
                if (mFragmented && removingCurrent) {
                    updateDeckList();
                    openStudyOptions(false);
                } else {
                    updateDeckList();
                }

                if (mProgressDialog != null && mProgressDialog.isShowing()) {
                    try {
                        mProgressDialog.dismiss();
                    } catch (Exception e) {
                        Timber.e(e, "onPostExecute - Exception dismissing dialog");
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


    @Override
    public void onAttachedToWindow() {

        if (!mFragmented) {
            Window window = getWindow();
            window.setFormat(PixelFormat.RGBA_8888);
        }
    }


    @SuppressLint("NewApi")
    @Override
    public void onShowcaseViewHide(ShowcaseView showcaseView) {
        // Undim the deck list when ShowcaseView is hidden
        if (CompatHelper.isHoneycomb()) {
            final float alpha = 1f;
            mTodayTextView.setAlpha(alpha);
            mRecyclerView.setAlpha(alpha);
        }
    }


    @Override
    public void onShowcaseViewDidHide(ShowcaseView showcaseView) {
    }


    @SuppressLint("NewApi")
    @Override
    public void onShowcaseViewShow(ShowcaseView showcaseView) {
        // Dim the deck list when ShowcaseView is shown
        if (CompatHelper.isHoneycomb()) {
            final float alpha = 0.1f;
            mTodayTextView.setAlpha(alpha);
            mRecyclerView.setAlpha(alpha);
        }
    }


    private void hideShowcaseView() {
        if (mShowcaseDialog != null) {
            mShowcaseDialog.hide();
        }
    }


    public void reloadShowcaseView() {
        hideShowcaseView();
        mShowShowcaseView = true;
        supportInvalidateOptionsMenu();
    }


    @Override
    public void onRequireDeckListUpdate() {
        updateDeckList();
    }

    @Override
    public void createFilteredDeck(JSONArray delays, Object[] terms, Boolean resched) {
        getFragment().createFilteredDeck(delays, terms, resched);
    }
}