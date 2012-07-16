///***************************************************************************************
// * This program is free software; you can redistribute it and/or modify it under        *
// * the terms of the GNU General Public License as published by the Free Software        *
// * Foundation; either version 3 of the License, or (at your option) any later           *
// * version.                                                                             *
// *                                                                                      *
// * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
// * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
// * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
// *                                                                                      *
// * You should have received a copy of the GNU General Public License along with         *
// * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
// ****************************************************************************************/
//
//package com.ichi2.anki;import com.ichi2.anki2.R;
//
//import android.app.Activity;
//import android.content.BroadcastReceiver;
//import android.content.ComponentName;
//import android.content.Context;
//import android.content.DialogInterface;
//import android.content.DialogInterface.OnClickListener;
//import android.content.Intent;
//import android.content.IntentFilter;
//import android.content.ServiceConnection;
//import android.content.res.Resources;
//import android.os.Bundle;
//import android.os.IBinder;
//import android.os.RemoteException;
//import android.text.Editable;
//import android.text.TextWatcher;
//import android.util.Log;
//import android.view.ContextMenu;
//import android.view.ContextMenu.ContextMenuInfo;
//import android.view.KeyEvent;
//import android.view.Menu;
//import android.view.MenuItem;
//import android.view.View;
//import android.view.ViewGroup;
//import android.view.WindowManager;
//import android.widget.AdapterView;
//import android.widget.AdapterView.AdapterContextMenuInfo;
//import android.widget.AdapterView.OnItemClickListener;
//import android.widget.BaseAdapter;
//import android.widget.EditText;
//import android.widget.ListView;
//import android.widget.ProgressBar;
//import android.widget.TextView;
//
//import com.ichi2.anim.ActivityTransitionAnimation;
//import com.ichi2.anki.services.DownloadManagerService;
//import com.ichi2.anki.services.IDownloadManagerService;
//import com.ichi2.anki.services.ISharedDeckServiceCallback;
//import com.ichi2.async.Connection;
//import com.ichi2.async.Connection.Payload;
//import com.ichi2.themes.StyledDialog;
//import com.ichi2.themes.StyledProgressDialog;
//import com.ichi2.themes.Themes;
//
//import java.util.ArrayList;
//import java.util.List;
//
//public class SharedDeckPicker extends Activity {
//
//    // Context menu options
//    private static final int MENU_CANCEL = Menu.FIRST + 1;
//    private static final int MENU_PAUSE  = Menu.FIRST + 2;
//    private static final int MENU_RESUME = Menu.FIRST + 3;
//
//    /**
//     * Broadcast that informs us when the sd card is about to be unmounted
//     */
//    private BroadcastReceiver mUnmountReceiver = null;
//
//    private StyledProgressDialog mProgressDialog;
//    private StyledDialog mNoConnectionAlert;
//    private StyledDialog mConnectionErrorAlert;
//
//    private Intent mDownloadManagerServiceIntent;
//    // Service interface we will use to call the service
//    private IDownloadManagerService mDownloadManagerService = null;
//
//    private List<Download> mSharedDeckDownloads;
//    private List<SharedDeck> mSharedDecks;
//    private List<SharedDeck> mFoundSharedDecks;
//    private ListView mSharedDecksListView;
//    private SharedDecksAdapter mSharedDecksAdapter;
//    private EditText mSearchEditText;
//
//    private boolean mDownloadSuccessful = false;
//
//
//    /********************************************************************
//     * Lifecycle methods *
//     ********************************************************************/
//
//    @Override
//    protected void onCreate(Bundle savedInstanceState) {
//      Themes.applyTheme(this);
//        super.onCreate(savedInstanceState);
//
//        setContentView(R.layout.download_deck_picker);
//
//        initDownloadManagerService();
//        registerExternalStorageListener();
//        initDialogs();
//
//        mSharedDeckDownloads = new ArrayList<Download>();
//        mSharedDecks = new ArrayList<SharedDeck>();
//        mFoundSharedDecks = new ArrayList<SharedDeck>();
//
//        mSharedDecksAdapter = new SharedDecksAdapter();
//        mSharedDecksListView = (ListView) findViewById(R.id.list);
//        mSharedDecksListView.setAdapter(mSharedDecksAdapter);
//        registerForContextMenu(mSharedDecksListView);
//
//        mSearchEditText = (EditText) findViewById(R.id.shared_deck_download_search);
//        mSearchEditText.addTextChangedListener(new TextWatcher() {
//          public void afterTextChanged(Editable s) {
//                findDecks();
//            }
//            public void beforeTextChanged(CharSequence s, int start, int count, int after){}
//            public void onTextChanged(CharSequence s, int start, int before, int count){}
//        });
//
//        mSharedDecksListView.setOnItemClickListener(new OnItemClickListener() {
//
//            @Override
//            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
//                Resources res = getResources();
//                Object obj = getSharedDeckFromList(position);
//                if (obj instanceof SharedDeck) {
//                    SharedDeck selectedDeck = (SharedDeck) obj;
//
//                    for (Download d : mSharedDeckDownloads) {
//                        if (d.getTitle().equals(selectedDeck.getTitle())) {
//                            // Duplicate downloads not allowed, sorry.
//                          Themes.showThemedToast(SharedDeckPicker.this, res.getString(R.string.duplicate_download), true);
//                            return;
//                        }
//                    }
//
//                    SharedDeckDownload sharedDeckDownload = new SharedDeckDownload(selectedDeck.getId(), selectedDeck
//                        .getTitle());
//                    sharedDeckDownload.setSize(selectedDeck.getSize());
//                    mSharedDeckDownloads.add(sharedDeckDownload);
//                    refreshSharedDecksList();
//
//                    try {
//                        startService(mDownloadManagerServiceIntent);
//                        mDownloadManagerService.downloadFile(sharedDeckDownload);
//                    } catch (RemoteException e) {
//                        // There is nothing special we need to do if the service has crashed
//                        Log.e(AnkiDroidApp.TAG, "RemoteException = " + e.getMessage());
//                        e.printStackTrace();
//                    }
//                }
//            }
//        });
//        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
//
//        Connection.getSharedDecks(mGetSharedDecksListener, new Connection.Payload(new Object[] {}));
//    }
//
//
//    @Override
//    public boolean onKeyDown(int keyCode, KeyEvent event)  {
//        if (keyCode == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0) {
//            Log.i(AnkiDroidApp.TAG, "SharedDeckPicker - onBackPressed()");
//            closeSharedDeckPicker();
//            return true;
//        }
//        return super.onKeyDown(keyCode, event);
//    }
//
//
//    @Override
//    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
//        Resources res = getResources();
//        int position = ((AdapterContextMenuInfo) menuInfo).position;
//        Object obj = getSharedDeckFromList(position);
//        if (obj instanceof Download) {
//            Download download = (Download) obj;
//            menu.setHeaderTitle(download.getTitle());
//            menu.add(Menu.NONE, MENU_CANCEL, Menu.NONE, res.getString(R.string.cancel_download));
//            if (download.getStatus() == SharedDeckDownload.STATUS_PAUSED) {
//                menu.add(Menu.NONE, MENU_RESUME, Menu.NONE, res.getString(R.string.resume_download));
//            } else if (download.getStatus() == SharedDeckDownload.STATUS_UPDATING) {
//                menu.add(Menu.NONE, MENU_PAUSE, Menu.NONE, res.getString(R.string.pause_download));
//            }
//       }
//    }
//
//
//    @Override
//    public boolean onContextItemSelected(MenuItem item) {
//        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
//        Object obj = getSharedDeckFromList(info.position);
//
//        if (obj instanceof Download) {
//            Download download = (Download) obj;
//
//            switch (item.getItemId()) {
//                case MENU_CANCEL:
//                    download.setStatus(SharedDeckDownload.STATUS_CANCELLED);
//                    break;
//                case MENU_RESUME:
//                    download.setStatus(SharedDeckDownload.STATUS_UPDATING);
//                    try {
//                        startService(mDownloadManagerServiceIntent);
//                        mDownloadManagerService.resumeDownloadUpdating(download);
//                    } catch (RemoteException e) {
//                        // There is nothing special we need to do if the service has crashed
//                        Log.e(AnkiDroidApp.TAG, "RemoteException = " + e.getMessage());
//                        e.printStackTrace();
//                    }
//                    break;
//                case MENU_PAUSE:
//                    download.setStatus(Download.STATUS_PAUSED);
//                    break;
//            }
//            mSharedDecksAdapter.notifyDataSetChanged();
//            return true;
//        }
//
//        return super.onOptionsItemSelected(item);
//    }
//
//
//    @Override
//    protected void onResume() {
//        Log.i(AnkiDroidApp.TAG, "onResume");
//        super.onResume();
//        if (mDownloadManagerService != null) {
//            try {
//
//                mDownloadManagerService.registerSharedDeckCallback(mCallback);
//                setSharedDeckDownloads(mDownloadManagerService.getSharedDeckDownloads());
//            } catch (RemoteException e) {
//                // There is nothing special we need to do if the service has crashed
//                Log.e(AnkiDroidApp.TAG, "RemoteException = " + e.getMessage());
//                e.printStackTrace();
//            }
//        }
//    }
//
//
//    @Override
//    protected void onPause() {
//        super.onPause();
//        if (mDownloadManagerService != null) {
//            try {
//                mDownloadManagerService.unregisterSharedDeckCallback(mCallback);
//            } catch (RemoteException e) {
//                // There is nothing special we need to do if the service has crashed
//                Log.e(AnkiDroidApp.TAG, "RemoteException = " + e.getMessage());
//                e.printStackTrace();
//            }
//        }
//    }
//
//
//    @Override
//    protected void onDestroy() {
//        super.onDestroy();
//        releaseBroadcastReceiver();
//        releaseService();
//        releaseDialogs();
//    }
//
//
//    /********************************************************************
//     * Custom methods *
//     ********************************************************************/
//
//    private void initDownloadManagerService() {
//        mDownloadManagerServiceIntent = new Intent(SharedDeckPicker.this, DownloadManagerService.class);
//        // Needed when the incomplete downloads are resumed while entering SharedDeckPicker
//        // if the Service gets shut down, we want it to be restarted automatically, so for this to happen it has to be
//        // started but not stopped
//        startService(mDownloadManagerServiceIntent);
//        bindService(mDownloadManagerServiceIntent, mConnection, Context.BIND_AUTO_CREATE);
//    }
//
//
//    private void releaseService() {
//        if (mConnection != null) {
//            unbindService(mConnection);
//            mConnection = null;
//        }
//    }
//
//
//    /**
//     * Registers an intent to listen for ACTION_MEDIA_EJECT notifications. The intent will call
//     * closeExternalStorageFiles() if the external media is going to be ejected, so applications can clean up any files
//     * they have open.
//     */
//    private void registerExternalStorageListener() {
//        if (mUnmountReceiver == null) {
//            mUnmountReceiver = new BroadcastReceiver() {
//                @Override
//                public void onReceive(Context context, Intent intent) {
//                    String action = intent.getAction();
//                    if (action.equals(Intent.ACTION_MEDIA_EJECT)) {
//                        finishNoStorageAvailable();
//                    }
//                }
//            };
//            IntentFilter iFilter = new IntentFilter();
//            iFilter.addAction(Intent.ACTION_MEDIA_EJECT);
//            iFilter.addDataScheme("file");
//            registerReceiver(mUnmountReceiver, iFilter);
//        }
//    }
//
//
//    private void releaseBroadcastReceiver() {
//        if (mUnmountReceiver != null) {
//            unregisterReceiver(mUnmountReceiver);
//            mUnmountReceiver = null;
//        }
//    }
//
//
//    /**
//     * Create AlertDialogs used on all the activity
//     */
//    private void initDialogs() {
//        Resources res = getResources();
//
//        // Init alert dialogs
//        StyledDialog.Builder builder = new StyledDialog.Builder(this);
//
//        builder.setTitle(res.getString(R.string.connection_error_title));
//        builder.setIcon(R.drawable.ic_dialog_alert);
//        builder.setMessage(res.getString(R.string.connection_needed));
//        builder.setPositiveButton(res.getString(R.string.ok), new OnClickListener() {
//
//            @Override
//            public void onClick(DialogInterface dialog, int which) {
//                closeSharedDeckPicker();
//            }
//
//        });
//        mNoConnectionAlert = builder.create();
//
//  builder = new StyledDialog.Builder(this);
//        builder.setTitle(res.getString(R.string.connection_error_title));
//        builder.setIcon(R.drawable.ic_dialog_alert);
//        builder.setMessage(res.getString(R.string.connection_error_return_message));
//        builder.setPositiveButton(res.getString(R.string.retry), new OnClickListener() {
//
//            @Override
//            public void onClick(DialogInterface dialog, int which) {
//                Connection.getSharedDecks(mGetSharedDecksListener, new Connection.Payload(new Object[] {}));
//            }
//        });
//        builder.setNegativeButton(res.getString(R.string.cancel), new OnClickListener() {
//
//            @Override
//            public void onClick(DialogInterface dialog, int which) {
//                closeSharedDeckPicker();
//            }
//
//        });
//        mConnectionErrorAlert = builder.create();
//    }
//
//
//    private void releaseDialogs() {
//        // Needed in order to not try to show the alerts when the Activity does not exist anymore
//        mProgressDialog = null;
//        // mNoConnectionAlert = null;
//        // mConnectionFailedAlert = null;
//        mConnectionErrorAlert = null;
//    }
//
//
//    private Object getSharedDeckFromList(int position) {
//        // The "list" consists of all mSharedDeckDownloads followed by all mFoundSharedDecks
//        if ( position < mSharedDeckDownloads.size() ) {
//            return mSharedDeckDownloads.get(position);
//        } else {
//            return mFoundSharedDecks.get(position - mSharedDeckDownloads.size());
//        }
//    }
//
//
//    private void findDecks() {
//        if (mSearchEditText.getText().length() == 0) {
//            mFoundSharedDecks = mSharedDecks;
//        } else {
//            List<SharedDeck> foundDecks = new ArrayList<SharedDeck>();
//            String searchText = mSearchEditText.getText().toString().toLowerCase();
//            for (SharedDeck sharedDeck : mSharedDecks) {
//              if (sharedDeck.matchesLowerCaseFilter(searchText)) {
//                    foundDecks.add(sharedDeck);
//                }
//            }
//            mFoundSharedDecks = foundDecks;
//        }
//        refreshSharedDecksList();
//    }
//
//
//    private void refreshSharedDecksList() {
//        mSharedDecksAdapter.notifyDataSetChanged();
//    }
//
//
//    private void setSharedDeckDownloads(List<SharedDeckDownload> downloads) {
//        mSharedDeckDownloads.clear();
//        mSharedDeckDownloads.addAll(downloads);
//        refreshSharedDecksList();
//    }
//
//
//    private void finishNoStorageAvailable() {
//        setResult(StudyOptions.CONTENT_NO_EXTERNAL_STORAGE);
//        closeSharedDeckPicker();
//    }
//
//
//    private void closeSharedDeckPicker() {
//      if (mDownloadSuccessful) {
//          Intent intent = SharedDeckPicker.this.getIntent();
//          setResult(RESULT_OK, intent);
//      }
//        finish();
//        if (Integer.valueOf(android.os.Build.VERSION.SDK) > 4) {
//            ActivityTransitionAnimation.slide(this, ActivityTransitionAnimation.LEFT);
//        }           
//    }
//
//    /********************************************************************
//     * Service Connection *
//     ********************************************************************/
//
//    /**
//     * Class for interacting with the main interface of the service.
//     */
//    private ServiceConnection mConnection = new ServiceConnection() {
//
//        @Override
//        public void onServiceConnected(ComponentName className, IBinder service) {
//            // This is called when the connection with the service has been
//            // established, giving us the service object we can use to
//            // interact with the service. We are communicating with our
//            // service through an IDL interface, so get a client-side
//            // representation of that from the raw service object.
//            mDownloadManagerService = IDownloadManagerService.Stub.asInterface(service);
//
//            Log.i(AnkiDroidApp.TAG, "onServiceConnected");
//            // We want to monitor the service for as long as we are
//            // connected to it.
//            try {
//                mDownloadManagerService.registerSharedDeckCallback(mCallback);
//            } catch (RemoteException e) {
//                // In this case the service has crashed before we could even
//                // do anything with it; we can count on soon being
//                // disconnected (and then reconnected if it can be restarted)
//                // so there is no need to do anything here.
//            }
//        }
//
//
//        @Override
//        public void onServiceDisconnected(ComponentName name) {
//            mDownloadManagerService = null;
//        }
//    };
//
//    /********************************************************************
//     * Listeners *
//     ********************************************************************/
//
//    private Connection.TaskListener mGetSharedDecksListener = new Connection.TaskListener() {
//
//        @Override
//        public void onDisconnected() {
//            Log.i(AnkiDroidApp.TAG, "onDisconnected");
//            if (mNoConnectionAlert != null) {
//                mNoConnectionAlert.show();
//            }
//        }
//
//
//        @SuppressWarnings("unchecked")
//        @Override
//        public void onPostExecute(Payload data) {
//            if (mProgressDialog != null) {
//                mProgressDialog.dismiss();
//            }
//            if (data.success) {
//                mSharedDecks.clear();
//                mSharedDecks.addAll((List<SharedDeck>) data.result);
//                findDecks();
//            } else {
//              if (data.returnType == Connection.RETURN_TYPE_OUT_OF_MEMORY) {
//                  Themes.showThemedToast(SharedDeckPicker.this, getResources().getString(R.string.error_insufficient_memory), false);
//                  finish();                   
//              } else if (mConnectionErrorAlert != null) {
//                    mConnectionErrorAlert.show();
//                }
//            }
//        }
//
//
//        @Override
//        public void onPreExecute() {
//            if (mProgressDialog == null || !mProgressDialog.isShowing()) {
//                mProgressDialog = StyledProgressDialog.show(SharedDeckPicker.this, "",
//                        getResources().getString(R.string.loading_shared_decks), true, true, new DialogInterface.OnCancelListener() {
//                    @Override
//                    public void onCancel(DialogInterface dialog) {
//                        Connection.cancelGetSharedDecks();
//                        closeSharedDeckPicker();
//                    }
//                });
//            }
//        }
//
//
//        @Override
//        public void onProgressUpdate(Object... values) {
//            // Pass
//        }
//
//    };
//
//
//    /********************************************************************
//     * Callbacks *
//     ********************************************************************/
//
//    /**
//     * This implementation is used to receive callbacks from the remote service.
//     */
//    private ISharedDeckServiceCallback mCallback = new ISharedDeckServiceCallback.Stub() {
//        /**
//         * This is called by the remote service regularly to tell us about new values. Note that IPC calls are
//         * dispatched through a thread pool running in each process, so the code executing here will NOT be running in
//         * our main thread like most other things -- so, to update the UI, we need to use a Handler to hop over there.
//         */
//        @Override
//        public void publishProgress(List<SharedDeckDownload> downloads) throws RemoteException {
//            Log.i(AnkiDroidApp.TAG, "publishProgress");
//            setSharedDeckDownloads(downloads);
//        }
//    };
//
//    /********************************************************************
//     * Adapters *
//     ********************************************************************/
//
//    public class SharedDecksAdapter extends BaseAdapter {
//
//        @Override
//        public int getCount() {
//            return mSharedDeckDownloads.size() + mFoundSharedDecks.size();
//        }
//
//
//        @Override
//        public Object getItem(int position) {
//            return getSharedDeckFromList(position);
//        }
//
//
//        @Override
//        public long getItemId(int position) {
//            return position;
//        }
//
//
//        //@Override
//        //public boolean isEnabled(int position) {
//        //    return !(getSharedDeckFromList(position) instanceof Download);
//        //}
//
//
//        @Override
//        public View getView(int position, View convertView, ViewGroup parent) {
//            View row = convertView;
//            DownloadViewWrapper wrapper = null;
//            Resources res = getResources();
//
//            if (row == null) {
//                row = getLayoutInflater().inflate(R.layout.download_deck_item, null);
//                Themes.setContentStyle(row, Themes.CALLER_DOWNLOAD_DECK);
//                wrapper = new DownloadViewWrapper(row);
//                row.setTag(wrapper);
//            } else {
//                wrapper = (DownloadViewWrapper) row.getTag();
//            }
//
//            TextView headerTitle = wrapper.getHeaderTitle();
//            TextView downloadingSharedDeckTitle = wrapper.getDownloadTitle();
//            ProgressBar progressBar = wrapper.getProgressBar();
//            TextView progressText = wrapper.getProgressBarText();
//            TextView estimatedText = wrapper.getEstimatedTimeText();
//            TextView sharedDeckTitle = wrapper.getDeckTitle();
//            TextView sharedDeckFacts = wrapper.getDeckFacts();
//
//            progressBar.setIndeterminate(false);
//            Object obj = getSharedDeckFromList(position);
//            if (obj instanceof Download) {
//                Download download = (Download) obj;
//
//                sharedDeckTitle.setVisibility(View.GONE);
//                sharedDeckFacts.setVisibility(View.GONE);
//
//                if (position == 0) {
//                    headerTitle.setText(res.getString(R.string.currently_downloading));
//                    headerTitle.setVisibility(View.VISIBLE);
//                } else {
//                    headerTitle.setVisibility(View.GONE);
//                }
//                downloadingSharedDeckTitle.setText(download.getTitle());
//                downloadingSharedDeckTitle.setVisibility(View.VISIBLE);
//                progressBar.setVisibility(View.VISIBLE);
//                switch (download.getStatus()) {
//                    case Download.STATUS_STARTED:
//                        progressText.setText(res.getString(R.string.starting_download));
//                        estimatedText.setText("");
//                        progressBar.setProgress(0);
//                        break;
//
//                    case Download.STATUS_DOWNLOADING:
//                        progressText.setText(res.getString(R.string.downloading));
//                        estimatedText.setText("");
//                        progressBar.setProgress(download.getProgress());
//                        break;
//
//                    case Download.STATUS_PAUSED:
//                        progressText.setText(res.getString(R.string.paused));
//                        estimatedText.setText("");
//                        progressBar.setProgress(download.getProgress());
//                        break;
//
//                    case Download.STATUS_COMPLETE:
//                        progressText.setText(res.getString(R.string.downloaded));
//                        estimatedText.setText("");
//                        progressBar.setProgress(0);
//                        mDownloadSuccessful = true;
//                        break;
//
//                    case SharedDeckDownload.STATUS_UPDATING:
//                        progressText.setText(res.getString(R.string.updating));
//                        estimatedText.setText(download.getEstTimeToCompletion());
//                        progressBar.setProgress(download.getProgress());
//                        break;
//
//                    case Download.STATUS_CANCELLED:
//                        progressText.setText(res.getString(R.string.cancelling));
//                        estimatedText.setText("");
//                        progressBar.setProgress(download.getProgress());
//                        break;
//
//                    default:
//                        progressText.setText(res.getString(R.string.error));
//                        estimatedText.setText("");
//                        break;
//                }
//                progressText.setVisibility(View.VISIBLE);
//                estimatedText.setVisibility(View.VISIBLE);
//            } else {
//                SharedDeck sharedDeck = (SharedDeck) obj;
//                if (position > 0 && (getSharedDeckFromList(position - 1) instanceof Download)) {
//                    headerTitle.setText(res.getString(R.string.shared_decks));
//                    headerTitle.setVisibility(View.VISIBLE);
//                } else {
//                    headerTitle.setVisibility(View.GONE);
//                }
//                downloadingSharedDeckTitle.setVisibility(View.GONE);
//                progressBar.setVisibility(View.GONE);
//                progressText.setVisibility(View.GONE);
//                estimatedText.setVisibility(View.GONE);
//
//                sharedDeckTitle.setText(sharedDeck.getTitle());
//                sharedDeckTitle.setVisibility(View.VISIBLE);
//                int numFacts = sharedDeck.getFacts();
//                if (numFacts == 1) {
//                    sharedDeckFacts.setText(numFacts + " " + res.getString(R.string.fact));
//                } else {
//                    sharedDeckFacts.setText(numFacts + " " + res.getString(R.string.facts));
//                }
//                sharedDeckFacts.setVisibility(View.VISIBLE);
//            }
//
//            return row;
//        }
//
//    }
// }
