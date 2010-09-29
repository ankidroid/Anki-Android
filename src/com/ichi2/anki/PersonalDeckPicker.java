
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
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.ichi2.anki.services.DownloadManagerService;
import com.ichi2.anki.services.IDownloadManagerService;
import com.ichi2.anki.services.IPersonalDeckServiceCallback;
import com.ichi2.async.Connection;
import com.ichi2.async.Connection.Payload;
import com.tomgibara.android.veecheck.util.PrefSettings;

import java.util.ArrayList;
import java.util.List;

public class PersonalDeckPicker extends Activity {

    private static final String TAG = "AnkiDroid";

    /**
     * Broadcast that informs us when the sd card is about to be unmounted
     */
    private BroadcastReceiver mUnmountReceiver = null;

    private ProgressDialog mProgressDialog;

    private AlertDialog mNoConnectionAlert;

    private AlertDialog mConnectionErrorAlert;

    private Intent mDownloadManagerServiceIntent;
    // Service interface we will use to call the service
    private IDownloadManagerService mDownloadManagerService = null;

    private List<Download> mPersonalDeckDownloads;
    private List<String> mPersonalDecks;

    private List<Object> mAllPersonalDecks;
    private ListView mPersonalDecksListView;
    private PersonalDecksAdapter mPersonalDecksAdapter;


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
                String deckName = (String) mAllPersonalDecks.get(position);
                Download personalDeckDownload = new Download(deckName);
                mPersonalDeckDownloads.add(personalDeckDownload);
                refreshPersonalDecksList();
                try {
                    startService(mDownloadManagerServiceIntent);
                    mDownloadManagerService.downloadFile(personalDeckDownload);
                } catch (RemoteException e) {
                    // There is nothing special we need to do if the service has crashed
                    Log.e(TAG, "RemoteException = " + e.getMessage());
                    e.printStackTrace();
                }
            }

        });

        getPersonalDecks();
    }


    @Override
    protected void onResume() {
        super.onResume();
        Log.i(TAG, "onResume");
        if (mDownloadManagerService != null) {
            try {
                mDownloadManagerService.registerPersonalDeckCallback(mCallback);
                setPersonalDeckDownloads(mDownloadManagerService.getPersonalDeckDownloads());
            } catch (RemoteException e) {
                // There is nothing special we need to do if the service has crashed
                Log.e(TAG, "RemoteException = " + e.getMessage());
                e.printStackTrace();
            }
        }
    }


    @Override
    protected void onPause() {
        super.onPause();
        if (mDownloadManagerService != null) {
            try {
                mDownloadManagerService.unregisterPersonalDeckCallback(mCallback);
            } catch (RemoteException e) {
                // There is nothing special we need to do if the service has crashed
                Log.e(TAG, "RemoteException = " + e.getMessage());
                e.printStackTrace();
            }
        }
    }


    @Override
    public void onDestroy() {
        Log.i(TAG, "onDestroy");
        super.onDestroy();
        releaseService();
        releaseBroadcastReceiver();
        releaseDialogs();
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
            Log.i(TAG, "Unbinding Service...");
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
                finish();
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
                PersonalDeckPicker.this.finish();
            }

        });
        mConnectionErrorAlert = builder.create();
    }


    private void releaseDialogs() {
        // Needed in order to not try to show the alerts when the Activity does not exist anymore
        mProgressDialog = null;
        // mNoConnectionAlert = null;
        // mConnectionFailedAlert = null;
        mConnectionErrorAlert = null;
    }


    private void refreshPersonalDecksList() {
        mAllPersonalDecks.clear();
        mAllPersonalDecks.addAll(mPersonalDeckDownloads);
        mAllPersonalDecks.addAll(mPersonalDecks);
        mPersonalDecksAdapter.notifyDataSetChanged();
    }


    private void getPersonalDecks() {
        Log.i(TAG, "getPersonalDecks");
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
        finish();
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

            Log.i(TAG, "onServiceConnected");
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

    Connection.TaskListener getPersonalDecksListener = new Connection.TaskListener() {

        @Override
        public void onDisconnected() {
            Log.i(TAG, "onDisconnected");
            if (mNoConnectionAlert != null) {
                mNoConnectionAlert.show();
            }
        }


        @SuppressWarnings("unchecked")
        @Override
        public void onPostExecute(Payload data) {
            Log.i(TAG, "onPostExecute");
            if (mProgressDialog != null) {
                mProgressDialog.dismiss();
            }

            if (data.success) {
                mPersonalDecks.clear();
                mPersonalDecks.addAll((List<String>) data.result);

                refreshPersonalDecksList();
            } else {
                if (mConnectionErrorAlert != null) {
                    mConnectionErrorAlert.show();
                }
            }
        }


        @Override
        public void onPreExecute() {
            if (mProgressDialog == null || !mProgressDialog.isShowing()) {
                mProgressDialog = ProgressDialog.show(PersonalDeckPicker.this, "",
                        getResources().getString(R.string.loading_personal_decks));
            }
        }


        @Override
        public void onProgressUpdate(Object... values) {
            // Pass
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
            Log.i(TAG, "publishProgress");
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
                progressBar.setVisibility(View.VISIBLE);
                switch (download.getStatus()) {
                    case Download.START:
                        progressText.setText(res.getString(R.string.starting_download));
                        break;

                    case Download.DOWNLOADING:
                        progressText.setText(res.getString(R.string.downloading));
                        break;

                    case Download.PAUSED:
                        progressText.setText(res.getString(R.string.paused));
                        break;

                    case Download.COMPLETE:
                        progressText.setText(res.getString(R.string.downloaded));
                        break;

                    default:
                        progressText.setText(res.getString(R.string.error));
                        break;
                }
                progressText.setVisibility(View.VISIBLE);
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

                sharedDeckTitle.setText(personalDeckTitle);
                sharedDeckTitle.setVisibility(View.VISIBLE);
                sharedDeckFacts.setVisibility(View.GONE);
            }

            return row;
        }

    }

}
