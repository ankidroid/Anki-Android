/****************************************************************************************
 * Copyright (c) 2009 Andrew Dubya <andrewdubya@gmail.com>                              *
 * Copyright (c) 2009 Nicolas Raoul <nicolas.raoul@gmail.com>                           *
 * Copyright (c) 2009 Edu Zamora <edu.zasu@gmail.com>                                   *
 * Copyright (c) 2009 Daniel Svärd <daniel.svard@gmail.com>                             *
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
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.text.SpannableStringBuilder;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.SimpleAdapter;

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

    private DeckPicker mSelf;

    private ProgressDialog mProgressDialog;
    private AlertDialog mSyncLogAlert;
    private RelativeLayout mSyncAllBar;
    private Button mSyncAllButton;

    private SimpleAdapter mDeckListAdapter;
    private ArrayList<HashMap<String, String>> mDeckList;
    private ListView mDeckListView;

    private File[] mFileList;

    private ReentrantLock mLock = new ReentrantLock();

    private Condition mCondFinished = mLock.newCondition();

    private boolean mIsFinished = true;

    private boolean mDeckIsSelected = false;

    private BroadcastReceiver mUnmountReceiver = null;

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
            Bundle data = msg.getData();
            Resources res = mSelf.getResources();

            String path = data.getString("absPath");
            String dueString = String.format(res.getString(R.string.deckpicker_due), data.getInt("due"),
                    data.getInt("total"));
            String newString = String.format(res.getString(R.string.deckpicker_new), data.getInt("new"));

            int count = mDeckListAdapter.getCount();
            for (int i = 0; i < count; i++) {
                @SuppressWarnings("unchecked")
                HashMap<String, String> map = (HashMap<String, String>) mDeckListAdapter.getItem(i);
                if (map.get("filepath").equals(path)) {
                    map.put("due", dueString);
                    map.put("new", newString);
                    map.put("showProgress", "false");
                }
            }

            mDeckListAdapter.notifyDataSetChanged();
            Log.i(AnkiDroidApp.TAG, "DeckPicker - mDeckList notified of changes");
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
                mProgressDialog = ProgressDialog.show(DeckPicker.this, (String) values[0], (String) values[1]);
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

            mSyncLogAlert.setMessage(getSyncLogMessage((ArrayList<HashMap<String, String>>) data.result));
            mSyncLogAlert.show();
        }
    };


    // ----------------------------------------------------------------------------
    // ANDROID METHODS
    // ----------------------------------------------------------------------------

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) throws SQLException {
        Log.i(AnkiDroidApp.TAG, "DeckPicker - onCreate");
        super.onCreate(savedInstanceState);

        mSelf = this;
        setContentView(R.layout.deck_picker);

        registerExternalStorageListener();
        initDialogs();

        mSyncAllBar = (RelativeLayout) findViewById(R.id.sync_all_bar);
        mSyncAllButton = (Button) findViewById(R.id.sync_all_button);
        mSyncAllButton.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                if (AnkiDroidApp.isUserLoggedIn()) {
                    SharedPreferences preferences = PrefSettings.getSharedPrefs(getBaseContext());
                    String username = preferences.getString("username", "");
                    String password = preferences.getString("password", "");
                    Connection.syncAllDecks(mSyncAllDecksListener, new Connection.Payload(new Object[] { username,
                            password, mDeckList }));
                } else {
                    showDialog(DIALOG_USER_NOT_LOGGED_IN);
                }
            }

        });

        mDeckList = new ArrayList<HashMap<String, String>>();
        mDeckListView = (ListView) findViewById(R.id.files);
        mDeckListAdapter = new SimpleAdapter(this, mDeckList, R.layout.deck_item, new String[] { "name", "due", "new",
                "showProgress" }, new int[] { R.id.DeckPickerName, R.id.DeckPickerDue, R.id.DeckPickerNew,
                R.id.DeckPickerProgress });

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
                return false;
            }
        });
        mDeckListView.setOnItemClickListener(mDeckSelHandler);
        mDeckListView.setAdapter(mDeckListAdapter);

        SharedPreferences preferences = PrefSettings.getSharedPrefs(getBaseContext());
        populateDeckList(preferences.getString("deckPath", AnkiDroidApp.getStorageDirectory()));
    }


    @Override
    public void onPause() {
        Log.i(AnkiDroidApp.TAG, "DeckPicker - onPause");

        super.onPause();
        waitForDeckLoaderThread();
    }


    @Override
    public void onDestroy() {
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
                builder.setMessage("The SD card could not be read. Please, turn off USB storage.");
                builder.setPositiveButton("OK", null);
                dialog = builder.create();
                break;

            case DIALOG_USER_NOT_LOGGED_IN:
                builder.setTitle(res.getString(R.string.connection_error_title));
                builder.setIcon(android.R.drawable.ic_dialog_alert);
                builder.setMessage(res.getString(R.string.no_user_password_error_message));
                builder.setPositiveButton(res.getString(R.string.log_in), new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Intent myAccount = new Intent(DeckPicker.this, MyAccount.class);
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

            default:
                dialog = null;
        }

        return dialog;
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
                    if (action.equals(Intent.ACTION_MEDIA_UNMOUNTED)) {
                        Log.i(AnkiDroidApp.TAG, "DeckPicker - mUnmountReceiver, Action = Media Unmounted");
                        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
                        String deckPath = preferences.getString("deckPath", AnkiDroidApp.getStorageDirectory());
                        populateDeckList(deckPath);
                    } else if (action.equals(Intent.ACTION_MEDIA_MOUNTED)) {
                        Log.i(AnkiDroidApp.TAG, "DeckPicker - mUnmountReceiver, Action = Media Mounted");
                        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
                        String deckPath = preferences.getString("deckPath", AnkiDroidApp.getStorageDirectory());
                        mDeckIsSelected = false;
                        setTitle(R.string.deckpicker_title);
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


    private void initDialogs() {
        // Sync Log dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getResources().getString(R.string.sync_log_tite));
        builder.setPositiveButton(getResources().getString(R.string.ok), null);
        mSyncLogAlert = builder.create();
    }


    private void populateDeckList(String location) {
        Log.i(AnkiDroidApp.TAG, "DeckPicker - populateDeckList");

        Resources res = getResources();
        int len = 0;
        File[] fileList;
        TreeSet<HashMap<String, String>> tree = new TreeSet<HashMap<String, String>>(new HashMapCompare());

        File dir = new File(location);
        fileList = dir.listFiles(new AnkiFilter());

        if (dir.exists() && dir.isDirectory() && fileList != null) {
            len = fileList.length;
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
                    data.put("due", res.getString(R.string.deckpicker_loaddeck));
                    data.put("new", "");
                    data.put("mod", String.format("%f", Deck.getLastModified(absPath)));
                    data.put("filepath", absPath);
                    data.put("showProgress", "true");

                    tree.add(data);

                } catch (SQLException e) {
                    Log.w(AnkiDroidApp.TAG, "DeckPicker - populateDeckList, File " + file.getName()
                            + " is not a real anki file");
                }
            }

            mSyncAllBar.setVisibility(View.VISIBLE);

            Thread thread = new Thread(this);
            thread.start();
        } else {
            Log.i(AnkiDroidApp.TAG, "populateDeckList - No decks found.");
            // There is no sd card attached (wrap this code in a function called something like isSdMounted()
            // and place it in a utils class
            if (!Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
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

                    try {
                        deck = Deck.openDeck(path);
                    } catch (SQLException e) {
                        Log.w(AnkiDroidApp.TAG, "Could not open database " + path);
                        continue;
                    }
                    int dueCards = deck.failedSoonCount + deck.revCount;
                    int totalCards = deck.cardCount;
                    int newCards = deck.newCountToday;
                    deck.closeDeck();

                    Bundle data = new Bundle();
                    data.putString("absPath", path);
                    data.putInt("due", dueCards);
                    data.putInt("total", totalCards);
                    data.putInt("new", newCards);
                    Message msg = Message.obtain();
                    msg.setData(data);

                    mHandler.sendMessage(msg);

                }
                mIsFinished = true;
                mCondFinished.signal();
            } finally {
                mLock.unlock();
            }
        }
    }


    private void handleDeckSelection(int id) {
        String deckFilename = null;

        waitForDeckLoaderThread();

        @SuppressWarnings("unchecked")
        HashMap<String, String> data = (HashMap<String, String>) mDeckListAdapter.getItem(id);
        deckFilename = data.get("filepath");

        if (deckFilename != null) {
            Log.i(AnkiDroidApp.TAG, "Selected " + deckFilename);
            Intent intent = this.getIntent();
            intent.putExtra(StudyOptions.OPT_DB, deckFilename);
            setResult(RESULT_OK, intent);

            finish();
        }
    }


    private void waitForDeckLoaderThread() {
        mDeckIsSelected = true;
        Log.i(AnkiDroidApp.TAG, "DeckPicker - waitForDeckLoaderThread(), mDeckIsSelected set to true");
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


    private CharSequence getSyncLogMessage(ArrayList<HashMap<String, String>> decksChangelogs) {
        SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder();
        int len = decksChangelogs.size();
        for (int i = 0; i < len; i++) {
            HashMap<String, String> deckChangelog = decksChangelogs.get(i);
            String deckName = deckChangelog.get("deckName");

            // Append deck name
            spannableStringBuilder.append(deckName);
            // Underline deck name
            spannableStringBuilder.setSpan(new UnderlineSpan(), spannableStringBuilder.length() - deckName.length(),
                    spannableStringBuilder.length(), 0);
            // Put deck name in bold style
            spannableStringBuilder.setSpan(new StyleSpan(android.graphics.Typeface.BOLD),
                    spannableStringBuilder.length() - deckName.length(), spannableStringBuilder.length(), 0);

            // Append sync message
            spannableStringBuilder.append("\n" + deckChangelog.get("message"));

            // If it is not the last element, add the proper separation
            if (i != (len - 1)) {
                spannableStringBuilder.append("\n\n");
            }
        }

        return spannableStringBuilder;
    }

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

    private static final class HashMapCompare implements Comparator<HashMap<String, String>> {
        @Override
        public int compare(HashMap<String, String> object1, HashMap<String, String> object2) {
            // Order by last modification date (last deck modified first)
            if (object2.get("mod").compareToIgnoreCase(object1.get("mod")) != 0) {
                return object2.get("mod").compareToIgnoreCase(object1.get("mod"));
                // But if there are two decks with the same date of modification, order them in alphabetical order
            } else {
                return object1.get("filepath").compareToIgnoreCase(object2.get("filepath"));
            }
        }
    }
}
