package com.ichi2.anki;

import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
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
import com.ichi2.anki.services.ISharedDeckServiceCallback;
import com.ichi2.async.Connection;
import com.ichi2.async.Connection.Payload;
import com.tomgibara.android.veecheck.util.PrefSettings;

public class SharedDeckPicker extends Activity {

	private static final String TAG = "AnkiDroid";
	
	//private DownloadManager mDownloadManager;
	
	/**
	 * Broadcast that informs us when the sd card is about to be unmounted
	 */
	private BroadcastReceiver mUnmountReceiver = null;
	
	private ProgressDialog progressDialog;
	
	private AlertDialog noConnectionAlert;
	
	private AlertDialog connectionFailedAlert;
	
	/** The primary interface we will be calling on the service. */
	private IDownloadManagerService mService = null;
	
	List<Download> mSharedDeckDownloads;
	List<SharedDeck> mSharedDecks;
	
	List<Object> mAllSharedDecks;
	ListView mSharedDecksListView;
	//SimpleAdapter mSharedDecksAdapter;
	SharedDecksAdapter mSharedDecksAdapter;
	SharedDeck deckToDownload;
	
	private String username;
	private String password;
	private String deckName;
	private String deckPath;
	
	private Intent serviceIntent;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		setContentView(R.layout.download_deck_picker);
		
		registerExternalStorageListener();
		initAlertDialogs();
		
		SharedPreferences pref = PrefSettings.getSharedPrefs(getBaseContext());
		username = pref.getString("username", "");
		password = pref.getString("password", "");
		
		serviceIntent = new Intent(SharedDeckPicker.this, DownloadManagerService.class);
		serviceIntent.putExtra("username", username);
		serviceIntent.putExtra("password", password);
		serviceIntent.putExtra("destination", pref.getString("deckPath", AnkiDroidApp.getStorageDirectory()));
		// Needed when the incomplete downloads are resumed while entering SharedDeckPicker
		// if the Service gets shut down, we want it to be restarted automatically, so for this to happen it has to be started but not stopped
		startService(serviceIntent);
		bindService(serviceIntent, mConnection, Context.BIND_AUTO_CREATE);
		
		mSharedDeckDownloads = new ArrayList<Download>();
		mSharedDecks = new ArrayList<SharedDeck>();
		
		mAllSharedDecks = new ArrayList<Object>();
		mSharedDecksAdapter = new SharedDecksAdapter();
		mSharedDecksListView = (ListView)findViewById(R.id.list);
		mSharedDecksListView.setAdapter(mSharedDecksAdapter);
		mSharedDecksListView.setOnItemClickListener(new OnItemClickListener() {

			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) 
			{
				SharedPreferences preferences = PrefSettings.getSharedPrefs(getBaseContext());
				String deckPath = preferences.getString("deckPath", AnkiDroidApp.getStorageDirectory());
				
				deckToDownload = (SharedDeck) mAllSharedDecks.get(position);
				SharedDeckDownload sharedDeckDownload = new SharedDeckDownload(deckToDownload.getId(), deckToDownload.getTitle(), deckToDownload.getFileName(), deckToDownload.getSize());
				mSharedDeckDownloads.add(sharedDeckDownload);
				refreshSharedDecksList();
				
				//mDownloadManager.downloadFile(sharedDeckDownload);
				try {
					startService(serviceIntent);
					mService.downloadFile(sharedDeckDownload);
				} catch (RemoteException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		});
		//mDownloadManager = DownloadManager.getSharedInstance(getApplicationContext(), username, password, pref.getString("deckPath", AnkiDroidApp.getStorageDirectory()));
		Connection.getSharedDecks(getSharedDecksListener, new Connection.Payload(new Object[] {}));
	}
	
	@Override
	protected void onResume() {
		Log.i(TAG, "onResume");
		super.onResume();
		/*
		mDownloadManager.registerListener(DownloadManager.SHARED_DECK_DOWNLOAD, downloadListener);
		mSharedDeckDownloads.clear();
		mSharedDeckDownloads.addAll(mDownloadManager.getDownloadsList(DownloadManager.SHARED_DECK_DOWNLOAD));
		refreshSharedDecksList();
		*/
		try {
			if(mService != null)
			{
				mService.registerSharedDeckCallback(mCallback);
				setSharedDeckDownloads(mService.getSharedDeckDownloads());
			}
		} catch (RemoteException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	@Override
	protected void onPause() {
		super.onPause();
		if(mService != null)
		{
			try {
				mService.unregisterSharedDeckCallback(mCallback);
			} catch (RemoteException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

	@Override
    public void onDestroy()
    {
    	super.onDestroy();
    	if(mUnmountReceiver != null)
    	{
    		unregisterReceiver(mUnmountReceiver);
    	}
    	releaseService();
    	
    	// Needed in order to not try to show the alert when the Activity does not exist anymore
    	connectionFailedAlert = null;
    }
	
	private void releaseService()
	{
		if(mConnection != null)
    	{
    		unbindService(mConnection);
    		mConnection = null;
    	}
	}
	
	/**
     * Registers an intent to listen for ACTION_MEDIA_EJECT notifications.
     * The intent will call closeExternalStorageFiles() if the external media
     * is going to be ejected, so applications can clean up any files they have open.
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

    private void finishNoStorageAvailable()
    {
    	setResult(StudyOptions.CONTENT_NO_EXTERNAL_STORAGE);
		finish();
    }
    
	/**
	 * Create AlertDialogs used on all the activity
	 */
	private void initAlertDialogs()
	{
		Resources res = getResources();
		
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		
		builder.setMessage(res.getString(R.string.connection_needed));
		builder.setPositiveButton(res.getString(R.string.ok), null);
		noConnectionAlert = builder.create();
		
	    builder.setMessage(res.getString(R.string.connection_unsuccessful));
	    connectionFailedAlert = builder.create();
	}
	
	private void refreshSharedDecksList() 
	{
		mAllSharedDecks.clear();
		mAllSharedDecks.addAll(mSharedDeckDownloads);
		mAllSharedDecks.addAll(mSharedDecks);
		mSharedDecksAdapter.notifyDataSetChanged();
	}
	
	private void setSharedDeckDownloads(List<SharedDeckDownload> downloads)
	{
		mSharedDeckDownloads.clear();
		mSharedDeckDownloads.addAll(downloads);
		refreshSharedDecksList();
	}
	
	/**
	 * Listeners
	 */
	/*
	DownloadManager.DownloadsListener downloadListener = new DownloadManager.DownloadsListener() {
		
		@Override
		public void onStateChanged(List downloads) {
			mSharedDeckDownloads.clear();
			mSharedDeckDownloads.addAll(downloads);
			
			refreshSharedDecksList();
		}
		
	};
	*/
	
	/*
	SharedDownloadManager.DownloadsListener downloadListener = new SharedDownloadManager.DownloadsListener() {
		
		@Override
		public void onStateChanged(List downloads) {
			mSharedDeckDownloads.clear();
			mSharedDeckDownloads.addAll(downloads);
			
			refreshSharedDecksList();
		}
		
	};
	*/
	
	Connection.TaskListener getSharedDecksListener = new Connection.TaskListener() {

		@Override
		public void onDisconnected() {
			noConnectionAlert.show();
		}

		@SuppressWarnings("unchecked")
		@Override
		public void onPostExecute(Payload data) {
			//progressDialog.dismiss();
			if(data.success)
			{
				mSharedDecks.clear();
				mSharedDecks.addAll((List<SharedDeck>)data.result);
				refreshSharedDecksList();
			}
			else
			{
				connectionFailedAlert.show();
			}
		}

		@Override
		public void onPreExecute() {
			//Pass
		}

		@Override
		public void onProgressUpdate(Object... values) {
			//Pass
		}
		
	};
	
	Connection.TaskListener downloadSharedDeckListener = new Connection.TaskListener() {

		@Override
		public void onDisconnected() {
			noConnectionAlert.show();
		}

		@Override
		public void onPostExecute(Payload data) {
			//progressDialog.dismiss();
			if(data.success)
			{
				Intent intent = SharedDeckPicker.this.getIntent();
				// Return the name of the downloaded deck
				intent.putExtra(AnkiDroid.OPT_DB, (String)data.result);
				setResult(RESULT_OK, intent);

				finish();
			}
			else
			{
				connectionFailedAlert.show();
			}
		}

		@Override
		public void onPreExecute() {
			//progressDialog = ProgressDialog.show(SharedDeckPicker.this, "", getResources().getString(R.string.downloading_shared_deck));
		}

		@Override
		public void onProgressUpdate(Object... values) {
			//Pass
		}
		
	};
	
	/**
	 * Class for interacting with the main interface of the service.
	 */
	private ServiceConnection mConnection = new ServiceConnection() {

    	public void onServiceConnected(ComponentName className, IBinder service) {
            // This is called when the connection with the service has been
            // established, giving us the service object we can use to
            // interact with the service.  We are communicating with our
            // service through an IDL interface, so get a client-side
            // representation of that from the raw service object.
            mService = IDownloadManagerService.Stub.asInterface(service);

            Log.i(TAG, "onServiceConnected");
            // We want to monitor the service for as long as we are
            // connected to it.
            try {
                mService.registerSharedDeckCallback(mCallback);
            } catch (RemoteException e) {
                // In this case the service has crashed before we could even
                // do anything with it; we can count on soon being
                // disconnected (and then reconnected if it can be restarted)
                // so there is no need to do anything here.
            } 
        }

		@Override
		public void onServiceDisconnected(ComponentName name) {
			mService = null;
		}
	};
	
	// ----------------------------------------------------------------------
	// Code showing how to deal with callbacks.
	// ----------------------------------------------------------------------

	/**
	 * This implementation is used to receive callbacks from the remote
	 * service.
	 */
	private ISharedDeckServiceCallback mCallback = new ISharedDeckServiceCallback.Stub() {
		/**
		 * This is called by the remote service regularly to tell us about
		 * new values.  Note that IPC calls are dispatched through a thread
		 * pool running in each process, so the code executing here will
		 * NOT be running in our main thread like most other things -- so,
		 * to update the UI, we need to use a Handler to hop over there.
		 */
		@Override
		public void publishProgress(List<SharedDeckDownload> downloads) throws RemoteException {
			Log.i(TAG, "publishProgress");
			setSharedDeckDownloads(downloads);
		}
	};
	
	
	class ViewWrapper {
		
		View base;
		TextView headerTitle = null;
		TextView downloadTitle = null;
		ProgressBar progressBar = null;
		TextView progressBarText = null;
		TextView deckTitle = null;
		TextView deckFacts = null;
		
		ViewWrapper(View base) {
			this.base = base;
		}
		
		TextView getHeaderTitle() 
		{
			if(headerTitle == null)
			{
				headerTitle = (TextView) base.findViewById(R.id.header_title);
			}
			return headerTitle;
		}
		
		TextView getDownloadTitle() 
		{
			if(downloadTitle == null)
			{
				downloadTitle = (TextView) base.findViewById(R.id.download_title);
			}
			return downloadTitle;
		}
		
		ProgressBar getProgressBar()
		{
			if(progressBar == null)
			{
				progressBar = (ProgressBar) base.findViewById(R.id.progress_bar);
			}
			return progressBar;
		}
		
		TextView getProgressBarText()
		{
			if(progressBarText == null)
			{
				progressBarText = (TextView) base.findViewById(R.id.progress_text);
			}
			return progressBarText;
		}

		TextView getDeckTitle()
		{
			if(deckTitle == null)
			{
				deckTitle = (TextView) base.findViewById(R.id.deck_title);
			}
			return deckTitle;
		}
		
		TextView getDeckFacts()
		{
			if(deckFacts == null)
			{
				deckFacts = (TextView) base.findViewById(R.id.deck_facts);
			}
			return deckFacts;
		}
	}
	
	public class SharedDecksAdapter extends BaseAdapter {

		@Override
		public int getCount() {
			return mAllSharedDecks.size();
		}

		@Override
		public Object getItem(int position) {
			return mAllSharedDecks.get(position);
		}

		@Override
		public long getItemId(int position) {
			return position;
		}
		
		@Override
		public boolean isEnabled(int position) {
			return !(mAllSharedDecks.get(position) instanceof Download);
		}
		
		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			View row = convertView;
			ViewWrapper wrapper = null;
			
			if(row == null)
			{
				row = getLayoutInflater().inflate(R.layout.download_deck_item, null);
				wrapper = new ViewWrapper(row);
				row.setTag(wrapper);
			}
			else
			{
				wrapper = (ViewWrapper)row.getTag();
			}
			
			TextView headerTitle = wrapper.getHeaderTitle();
			TextView downloadingSharedDeckTitle = wrapper.getDownloadTitle();
			ProgressBar progressBar = wrapper.getProgressBar();
			TextView progressText = wrapper.getProgressBarText();
			TextView sharedDeckTitle = wrapper.getDeckTitle();
			TextView sharedDeckFacts = wrapper.getDeckFacts();
			
			Object obj = (Object) mAllSharedDecks.get(position);
			if(obj instanceof Download)
			{
				Download download = (Download) obj;
				
				sharedDeckTitle.setVisibility(View.GONE);
				sharedDeckFacts.setVisibility(View.GONE);
				
				if(position == 0)
				{
					headerTitle.setText("Currently downloading");
					headerTitle.setVisibility(View.VISIBLE);
				}
				else
				{
					headerTitle.setVisibility(View.GONE);
				}
				downloadingSharedDeckTitle.setText(download.getTitle());
				downloadingSharedDeckTitle.setVisibility(View.VISIBLE);
				progressBar.setVisibility(View.VISIBLE);
				switch(download.getStatus())
				{
					case Download.START:
						progressText.setText("Starting download...");
						break;
						
					case Download.DOWNLOADING:
						progressText.setText("Downloading...");
						break;
						
					case Download.PAUSED:
						progressText.setText("Paused");
						break;
					
					case Download.COMPLETE:
						progressText.setText("Downloaded");
						break;
					
					case SharedDeckDownload.UPDATE:
						progressText.setText("Updating...");
						break;
						
					default:
						progressText.setText("Error");
						break;
				}
				progressText.setVisibility(View.VISIBLE);
			}
			else
			{
				SharedDeck sharedDeck = (SharedDeck) obj;
				if(position > 0 && (mAllSharedDecks.get(position - 1) instanceof Download))
				{
					headerTitle.setText("Shared Decks");
					headerTitle.setVisibility(View.VISIBLE);
				}
				else
				{
					headerTitle.setVisibility(View.GONE);
				}
				downloadingSharedDeckTitle.setVisibility(View.GONE);
				progressBar.setVisibility(View.GONE);
				progressText.setVisibility(View.GONE);
				
				sharedDeckTitle.setText(sharedDeck.getTitle());
				sharedDeckTitle.setVisibility(View.VISIBLE);
				sharedDeckFacts.setText(sharedDeck.getFacts() + " facts");
				sharedDeckFacts.setVisibility(View.VISIBLE);
			}
			
			return row;
		}
		
	}
}
