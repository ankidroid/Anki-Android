/***************************************************************************************
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
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.ichi2.anki.services.DownloadManagerService;
import com.ichi2.anki.services.IDownloadManagerService;
import com.ichi2.anki.services.IPersonalDeckServiceCallback;
import com.ichi2.async.Connection;
import com.ichi2.async.Connection.Payload;
import com.tomgibara.android.veecheck.util.PrefSettings;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class PersonalDeckPicker extends Activity {

    /**
     * Broadcast that informs us when the sd card is about to be unmounted
     */
    private BroadcastReceiver mUnmountReceiver = null;

    private ProgressDialog mProgressDialog;
    private AlertDialog mNoConnectionAlert;
    private AlertDialog mConnectionErrorAlert;
    private AlertDialog mDownloadOverwriteAlert;

    private Intent mDownloadManagerServiceIntent;
    // Service interface we will use to call the service
    private IDownloadManagerService mDownloadManagerService = null;

    private List<Download> mPersonalDeckDownloads;
    private List<String> mPersonalDecks;
    private List<Object> mAllPersonalDecks;
    private ListView mPersonalDecksListView;
    private PersonalDecksAdapter mPersonalDecksAdapter;
    private EditText mSearchEditText;
    private String mDestination;
    private Download mDeckToDownload;

    private boolean mDownloadSuccessful = false;

    /********************************************************************
     * Lifecycle methods *
     ********************************************************************/

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.download_deck_picker);

        initDownloadManagerService();
        registerExternalStorageListener();
        initDialogs();

        mPersonalDeckDownloads = new ArrayList<Download>();
        mPersonalDecks = new ArrayList<String>();

        mAllPersonalDecks = new ArrayList<Object>();
        mPersonalDecksAdapter = new PersonalDecksAdapter();
        mPersonalDecksListView = (ListView) findViewById(R.id.list);
        mPersonalDecksListView.setAdapter(mPersonalDecksAdapter);     
        mPersonalDecksListView.setOnItemClickListener(new OnItemClickListener() {

            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

                Object deckNameObject = mAllPersonalDecks.get(position);
                // If we click twice very fast, the second click tries to download the download that was triggered
                // by the first click and causes crash
                if (!(deckNameObject instanceof String)) {
                    return;
                }
                String deckName = (String) deckNameObject;
                Download personalDeckDownload = new Download(deckName);
                mDestination = PrefSettings.getSharedPrefs(getBaseContext()).getString("deckPath", AnkiDroidApp.getStorageDirectory());
                setDeckToDownload(personalDeckDownload);
                if (new File(mDestination + "/" + deckName + ".anki").exists()) {
                    mDownloadOverwriteAlert.setMessage(getResources().getString(R.string.download_message, deckName));
                    mDownloadOverwriteAlert.show();
                    // Log.d(AnkiDroidApp.TAG, "Download Deck already exists");
                } else {
                    downloadPersonalDeck(personalDeckDownload);
                    // Log.d(AnkiDroidApp.TAG, "Download Deck not exists");
                }
            }

        });

        mSearchEditText = (EditText) findViewById(R.id.shared_deck_download_search);
        mSearchEditText.addTextChangedListener(new TextWatcher() {
        	public void afterTextChanged(Editable s) {
    			List<String> foundDecks = new ArrayList<String>();
            	foundDecks.clear();
            	for (int i = 0; i < mPersonalDecks.size(); i++) {
            		if (mPersonalDecks.get(i).toLowerCase().indexOf(mSearchEditText.getText().toString().toLowerCase()) != -1) { 
            			foundDecks.add(mPersonalDecks.get(i));
            		}
            	}
                mAllPersonalDecks.clear();
                mAllPersonalDecks.addAll(mPersonalDeckDownloads);
                mAllPersonalDecks.addAll(foundDecks);
                mPersonalDecksAdapter.notifyDataSetChanged();
            }
            public void beforeTextChanged(CharSequence s, int start, int count, int after){}
            public void onTextChanged(CharSequence s, int start, int before, int count){}
        });
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        
        getPersonalDecks();
    }

    private void downloadPersonalDeck(Download personalDeck) {

        mPersonalDeckDownloads.add(personalDeck);
        refreshPersonalDecksList();
        try {
            startService(mDownloadManagerServiceIntent);
            mDownloadManagerService.downloadFile(personalDeck);
        } catch (RemoteException e) {
            // There is nothing special we need to do if the service has crashed
            Log.e(AnkiDroidApp.TAG, "RemoteException = " + e.getMessage());
            e.printStackTrace();
        }
    }


    @Override
    protected void onResume() {
        super.onResume();
        // Log.i(AnkiDroidApp.TAG, "onResume");
        if (mDownloadManagerService != null) {
            try {
                mDownloadManagerService.registerPersonalDeckCallback(mCallback);
                setPersonalDeckDownloads(mDownloadManagerService.getPersonalDeckDownloads());
            } catch (RemoteException e) {
                // There is nothing special we need to do if the service has crashed
                Log.e(AnkiDroidApp.TAG, "RemoteException = " + e.getMessage());
                e.printStackTrace();
            }
        }
    }


    @Override
    protected void onPause() {
        super.onPause();
        // Log.i(AnkiDroidApp.TAG, "onpause kostas");
        if (mDownloadManagerService != null) {
            try {
                mDownloadManagerService.unregisterPersonalDeckCallback(mCallback);
            } catch (RemoteException e) {
                // There is nothing special we need to do if the service has crashed
                Log.e(AnkiDroidApp.TAG, "RemoteException = " + e.getMessage());
                e.printStackTrace();
            }
        }
    }


    @Override
    protected void onDestroy() {
        // Log.i(AnkiDroidApp.TAG, "onDestroy");
        super.onDestroy();
        releaseService();
        releaseBroadcastReceiver();
        releaseDialogs();
    }


    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event)  {
        if (keyCode == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0) {
            // Log.i(AnkiDroidApp.TAG, "PersonalDeckPicker - onBackPressed()");
            closePersonalDeckPicker();
        }
        return super.onKeyDown(keyCode, event);
    }


    /********************************************************************
     * Custom methods *
     ********************************************************************/

    private void initDownloadManagerService() {
        mDownloadManagerServiceIntent = new Intent(PersonalDeckPicker.this, DownloadManagerService.class);
        // Needed when the incomplete downloads are resumed while entering SharedDeckPicker
        // if the Service gets shut down, we want it to be restarted automatically, so for this to happen it has to be
        // started but not stopped
        startService(mDownloadManagerServiceIntent);
        bindService(mDownloadManagerServiceIntent, mConnection, Context.BIND_AUTO_CREATE);
    }


    private void releaseService() {
        if (mConnection != null) {
            // Log.i(AnkiDroidApp.TAG, "Unbinding Service...");
            unbindService(mConnection);
            mConnection = null;
        }
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


    private void releaseBroadcastReceiver() {
        if (mUnmountReceiver != null) {
            unregisterReceiver(mUnmountReceiver);
            mUnmountReceiver = null;
        }
    }


    /**
     * Create AlertDialogs used on all the activity
     */
    private void initDialogs() {
        Resources res = getResources();

        // Init alert dialogs
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        builder.setTitle(res.getString(R.string.connection_error_title));
        builder.setIcon(android.R.drawable.ic_dialog_alert);
        builder.setMessage(res.getString(R.string.connection_needed));
        builder.setPositiveButton(res.getString(R.string.ok), new OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {
            	closePersonalDeckPicker();
            }

        });
        mNoConnectionAlert = builder.create();

        builder.setTitle(res.getString(R.string.connection_error_title));
        builder.setIcon(android.R.drawable.ic_dialog_alert);
        builder.setMessage(res.getString(R.string.connection_error_return_message));
        builder.setPositiveButton(res.getString(R.string.retry), new OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {
                getPersonalDecks();
            }
        });
        builder.setNegativeButton(res.getString(R.string.cancel), new OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {
            	closePersonalDeckPicker();
            }

        });
        mConnectionErrorAlert = builder.create();

        builder = new AlertDialog.Builder(this);
        builder.setTitle(res.getString(R.string.sync_conflict_title));
        builder.setIcon(android.R.drawable.ic_input_get);
        builder.setMessage(res.getString(R.string.download_message));
        builder.setPositiveButton(res.getString(R.string.download_overwrite), mSyncDublicateAlertListener);
        builder.setNegativeButton(res.getString(R.string.download_cancel), mSyncDublicateAlertListener);
        builder.setCancelable(false);
        mDownloadOverwriteAlert = builder.create();
    }


    private void releaseDialogs() {
        // Needed in order to not try to show the alerts when the Activity does not exist anymore
        mProgressDialog = null;
        // mNoConnectionAlert = null;
        // mConnectionFailedAlert = null;
        mConnectionErrorAlert = null;
    }

    public Download getDeckToDownload() {
        return mDeckToDownload;
    }

    public void setDeckToDownload(Download deckToDownload) {
        this.mDeckToDownload = deckToDownload;
    }

    private void refreshPersonalDecksList() {
        mAllPersonalDecks.clear();
        mAllPersonalDecks.addAll(mPersonalDeckDownloads);
        mAllPersonalDecks.addAll(mPersonalDecks);
        mPersonalDecksAdapter.notifyDataSetChanged();
    }


    private void getPersonalDecks() {
        // Log.i(AnkiDroidApp.TAG, "getPersonalDecks");
        SharedPreferences pref = PrefSettings.getSharedPrefs(getBaseContext());
        String username = pref.getString("username", "");
        String password = pref.getString("password", "");
        Connection.getPersonalDecks(getPersonalDecksListener, new Connection.Payload(
                new Object[] { username, password }));
    }


    private void setPersonalDeckDownloads(List<Download> downloads) {
        mPersonalDeckDownloads.clear();
        mPersonalDeckDownloads.addAll(downloads);
        refreshPersonalDecksList();
    }


    private void finishNoStorageAvailable() {
        setResult(StudyOptions.CONTENT_NO_EXTERNAL_STORAGE);
        closePersonalDeckPicker();
    }

    private void closePersonalDeckPicker() {
        if (mDownloadSuccessful) {
    		Intent intent = PersonalDeckPicker.this.getIntent();
    		setResult(RESULT_OK, intent);
            finish();
            if (Integer.valueOf(android.os.Build.VERSION.SDK) > 4) {
                MyAnimation.slide(this, MyAnimation.RIGHT);
            }
    	} else {
            finish();
            if (Integer.valueOf(android.os.Build.VERSION.SDK) > 4) {
                MyAnimation.slide(this, MyAnimation.LEFT);
            }
    	}
    }

    /********************************************************************
     * Service Connection *
     ********************************************************************/

    /**
     * Class for interacting with the main interface of the service.
     */
    private ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            // This is called when the connection with the service has been
            // established, giving us the service object we can use to
            // interact with the service. We are communicating with our
            // service through an IDL interface, so get a client-side
            // representation of that from the raw service object.
            mDownloadManagerService = IDownloadManagerService.Stub.asInterface(service);

            // Log.i(AnkiDroidApp.TAG, "onServiceConnected");
            // We want to monitor the service for as long as we are
            // connected to it.
            try {
                mDownloadManagerService.registerPersonalDeckCallback(mCallback);
            } catch (RemoteException e) {
                // In this case the service has crashed before we could even
                // do anything with it; we can count on soon being
                // disconnected (and then reconnected if it can be restarted)
                // so there is no need to do anything here.
            }
        }


        @Override
        public void onServiceDisconnected(ComponentName name) {
            mDownloadManagerService = null;
        }
    };

    /********************************************************************
     * Listeners *
     ********************************************************************/

    private Connection.TaskListener getPersonalDecksListener = new Connection.TaskListener() {

        @Override
        public void onDisconnected() {
            // Log.i(AnkiDroidApp.TAG, "onDisconnected");
            if (mNoConnectionAlert != null) {
                mNoConnectionAlert.show();
            }
        }


        @SuppressWarnings("unchecked")
        @Override
        public void onPostExecute(Payload data) {
            // Log.i(AnkiDroidApp.TAG, "onPostExecute");
            if (mProgressDialog != null) {
                mProgressDialog.dismiss();
            }

            if (data.success) {
                mPersonalDecks.clear();
                mPersonalDecks.addAll((List<String>) data.result);

                refreshPersonalDecksList();
            } else {
                if (mConnectionErrorAlert != null) {
                    if (data.result != null) {
                        // Known error
                        mConnectionErrorAlert.setMessage((String) data.result);
                    }
                    mConnectionErrorAlert.show();
                }
            }
        }


        @Override
        public void onPreExecute() {
            if (mProgressDialog == null || !mProgressDialog.isShowing()) {
                mProgressDialog = ProgressDialog.show(PersonalDeckPicker.this, "",
                        getResources().getString(R.string.loading_personal_decks), true, true, new DialogInterface.OnCancelListener() {
                	@Override
                	public void onCancel(DialogInterface dialog) {
                		Connection.cancelGetDecks();
                		closePersonalDeckPicker();
                	}
                });
            }
        }


        @Override
        public void onProgressUpdate(Object... values) {
            // Pass
        }

    };

    private OnClickListener mSyncDublicateAlertListener = new OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            switch (which) {
                case AlertDialog.BUTTON_POSITIVE:
                    downloadPersonalDeck(getDeckToDownload());
                    break;
                case AlertDialog.BUTTON_NEGATIVE:
                default:
            }
        }
    };

    /********************************************************************
     * Callbacks *
     ********************************************************************/

    /**
     * This implementation is used to receive callbacks from the remote service.
     */
    private IPersonalDeckServiceCallback mCallback = new IPersonalDeckServiceCallback.Stub() {
        @Override
        public void publishProgress(List<Download> downloads) throws RemoteException {
            setPersonalDeckDownloads(downloads);
        }
    };

    /********************************************************************
     * Adapters *
     ********************************************************************/

    public class PersonalDecksAdapter extends BaseAdapter {

        @Override
        public int getCount() {
            return mAllPersonalDecks.size();
        }


        @Override
        public Object getItem(int position) {
            return mAllPersonalDecks.get(position);
        }


        @Override
        public long getItemId(int position) {
            return position;
        }


        @Override
        public boolean isEnabled(int position) {
            return !(mAllPersonalDecks.get(position) instanceof Download);
        }


        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View row = convertView;
            DownloadViewWrapper wrapper = null;
            Resources res = getResources();

            if (row == null) {
                row = getLayoutInflater().inflate(R.layout.download_deck_item, null);
                wrapper = new DownloadViewWrapper(row);
                row.setTag(wrapper);
            } else {
                wrapper = (DownloadViewWrapper) row.getTag();
            }

            TextView headerTitle = wrapper.getHeaderTitle();
            TextView downloadingSharedDeckTitle = wrapper.getDownloadTitle();
            ProgressBar progressBar = wrapper.getProgressBar();
            TextView progressText = wrapper.getProgressBarText();
            TextView estimatedText = wrapper.getEstimatedTimeText();
            TextView sharedDeckTitle = wrapper.getDeckTitle();
            TextView sharedDeckFacts = wrapper.getDeckFacts();

            Object obj = mAllPersonalDecks.get(position);
            if (obj instanceof Download) {
                Download download = (Download) obj;

                sharedDeckTitle.setVisibility(View.GONE);
                sharedDeckFacts.setVisibility(View.GONE);

                if (position == 0) {
                    headerTitle.setText(res.getString(R.string.currently_downloading));
                    headerTitle.setVisibility(View.VISIBLE);
                } else {
                    headerTitle.setVisibility(View.GONE);
                }
                downloadingSharedDeckTitle.setText(download.getTitle());
                downloadingSharedDeckTitle.setVisibility(View.VISIBLE);

                //if (!progressBar.isIndeterminate()) {
                //    progressBar.setIndeterminate(true);
                //}
                progressBar.setVisibility(View.VISIBLE);
                long downloaded = 0l;
                switch (download.getStatus()) {
                    case Download.STATUS_STARTED:
                        progressText.setText(res.getString(R.string.starting_download));
                        break;

                    case Download.STATUS_DOWNLOADING:
                        progressText.setText(res.getString(R.string.downloading));
                        downloaded = download.getDownloaded();
                        estimatedText.setText(String.valueOf(downloaded / 1024) + " kB");
                        break;
                    case Download.STATUS_COMPLETE:
                        progressText.setText(res.getString(R.string.downloaded));
                        mDownloadSuccessful = true;
                        break;

                    default:
                        progressText.setText(res.getString(R.string.error));
                        break;
                }
                progressText.setVisibility(View.VISIBLE);
                estimatedText.setVisibility(View.VISIBLE);
            } else {
                String personalDeckTitle = (String) obj;
                if (position > 0 && (mAllPersonalDecks.get(position - 1) instanceof Download)) {
                    headerTitle.setText(res.getString(R.string.personal_decks));
                    headerTitle.setVisibility(View.VISIBLE);
                } else {
                    headerTitle.setVisibility(View.GONE);
                }
                downloadingSharedDeckTitle.setVisibility(View.GONE);
                progressBar.setVisibility(View.GONE);
                progressText.setVisibility(View.GONE);
                estimatedText.setVisibility(View.GONE);

                sharedDeckTitle.setText(personalDeckTitle);
                sharedDeckTitle.setVisibility(View.VISIBLE);
                sharedDeckFacts.setVisibility(View.GONE);
            }

            return row;
        }

    }

}
