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
import com.ichi2.anki.services.IPersonalDeckServiceCallback;
import com.ichi2.async.Connection;
import com.ichi2.async.Connection.Payload;
import com.tomgibara.android.veecheck.util.PrefSettings;

public class PersonalDeckPicker extends Activity {

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

	private List<Download> mPersonalDeckDownloads;
	private List<String> mPersonalDecks;
	
	private List<Object> mAllPersonalDecks;
	private ListView mPersonalDecksListView;
	//private ArrayAdapter<String> mPersonalDecksAdapter;
	private PersonalDecksAdapter mPersonalDecksAdapter;
	
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
		
		serviceIntent = new Intent(PersonalDeckPicker.this, DownloadManagerService.class);
		serviceIntent.putExtra("username", username);
		serviceIntent.putExtra("password", password);
		serviceIntent.putExtra("destination", pref.getString("deckPath", AnkiDroidApp.getStorageDirectory()));
		bindService(serviceIntent, mConnection, Context.BIND_AUTO_CREATE);
		
		mPersonalDeckDownloads = new ArrayList<Download>();
		mPersonalDecks = new ArrayList<String>();
		
		mAllPersonalDecks = new ArrayList<Object>();
		mPersonalDecksAdapter = new PersonalDecksAdapter();
		mPersonalDecksListView = (ListView)findViewById(R.id.list);
		mPersonalDecksListView.setAdapter(mPersonalDecksAdapter);
		mPersonalDecksListView.setOnItemClickListener(new OnItemClickListener() {

			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) 
			{
				SharedPreferences pref = PrefSettings.getSharedPrefs(getBaseContext());
				deckName = (String) mAllPersonalDecks.get(position);
				deckPath = pref.getString("deckPath", AnkiDroidApp.getStorageDirectory()) + "/" + deckName + ".anki";
				
				Download personalDeckDownload = new Download(deckName);
				mPersonalDeckDownloads.add(personalDeckDownload);
				refreshPersonalDecksList();
				//new Connection().downloadPersonalDeck(downloadSharedDeckListener, new Connection.Payload(new Object[] {username, password, deckName, deckPath}));
				//mDownloadManager.downloadFile(personalDeckDownload);
				try {
					startService(serviceIntent);
					mService.downloadFile(personalDeckDownload);
				} catch (RemoteException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			
		});
		
		//mDownloadManager = DownloadManager.getSharedInstance(getApplicationContext(), username, password, pref.getString("deckPath", AnkiDroidApp.getStorageDirectory()));
		Connection.getPersonalDecks(getPersonalDecksListener, new Connection.Payload(new Object[] {username, password}));
	}

	
	@Override
	protected void onResume() {
		super.onResume();
		Log.i(TAG, "onResume");
		/*
		mDownloadManager.registerListener(DownloadManager.PERSONAL_DECK_DOWNLOAD, downloadListener);
		mPersonalDecksDownloads.clear();
		mPersonalDecksDownloads.addAll(mDownloadManager.getDownloadsList(DownloadManager.PERSONAL_DECK_DOWNLOAD));
		refreshPersonalDecksList();
		*/
		try {
			if(mService != null)
			{
				mService.registerPersonalDeckCallback(mCallback);
				setPersonalDeckDownloads(mService.getPersonalDeckDownloads());
			}
		} catch (RemoteException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}


	@Override
	protected void onPause() {
		super.onPause();
		//mDownloadManager.unregisterListener(downloadListener);
		if(mService != null)
		{
			try {
				mService.unregisterPersonalDeckCallback(mCallback);
			} catch (RemoteException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

	@Override
    public void onDestroy()
    {
		Log.i(TAG, "onDestroy");
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
			Log.i(TAG, "Unbinding Service...");
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
	
	private void refreshPersonalDecksList() 
	{
		mAllPersonalDecks.clear();
		mAllPersonalDecks.addAll(mPersonalDeckDownloads);
		mAllPersonalDecks.addAll(mPersonalDecks);
		mPersonalDecksAdapter.notifyDataSetChanged();
	}
	
	private void setPersonalDeckDownloads(List<Download> downloads)
	{
		mPersonalDeckDownloads.clear();
		mPersonalDeckDownloads.addAll(downloads);
		refreshPersonalDecksList();
	}
	
	/**
	 * Listeners
	 */
	Connection.TaskListener getPersonalDecksListener = new Connection.TaskListener() {

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
				mPersonalDecks.clear();
				mPersonalDecks.addAll((List<String>)data.result);
				
				refreshPersonalDecksList();
			}
			else
			{
				if(connectionFailedAlert != null)
				{
					connectionFailedAlert.show();
				}
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
				// TODO: Decide what to do when a download is finished
				/*
				Intent intent = PersonalDeckPicker.this.getIntent();
				// Return the name of the downloaded deck
				intent.putExtra(AnkiDroid.OPT_DB, deckPath);
				setResult(RESULT_OK, intent);

				finish();
				*/
				Log.i("AnkiDroid", "Deck downloaded = " + data.data[2]);
			}
			else
			{
				connectionFailedAlert.show();
			}
		}

		@Override
		public void onPreExecute() {
			//progressDialog = ProgressDialog.show(PersonalDeckPicker.this, "", getResources().getString(R.string.downloading_shared_deck));
		}

		@Override
		public void onProgressUpdate(Object... values) {
			//Pass
		}
		
	};
	
	/*
	DownloadManager.DownloadsListener downloadListener = new DownloadManager.DownloadsListener() {
		
		@Override
		public void onStateChanged(List downloads) {
			mPersonalDeckDownloads.clear();
			mPersonalDeckDownloads.addAll(downloads);
			
			refreshPersonalDecksList();
		}
		
	};
	*/
	
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
                mService.registerPersonalDeckCallback(mCallback);
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
	private IPersonalDeckServiceCallback mCallback = new IPersonalDeckServiceCallback.Stub() {
		/**
		 * This is called by the remote service regularly to tell us about
		 * new values.  Note that IPC calls are dispatched through a thread
		 * pool running in each process, so the code executing here will
		 * NOT be running in our main thread like most other things -- so,
		 * to update the UI, we need to use a Handler to hop over there.
		 */
		@Override
		public void publishProgress(List<Download> downloads) throws RemoteException {
			Log.i(TAG, "publishProgress");
			setPersonalDeckDownloads(downloads);
		}
	};

	/**
	 * 
	 * Adapter with Holder pattern
	 */
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
			
			Object obj = (Object) mAllPersonalDecks.get(position);
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
						
					default:
						progressText.setText("Error");
						break;
				}
				progressText.setVisibility(View.VISIBLE);
			}
			else
			{
				String personalDeckTitle = (String) obj;
				if(position > 0 && (mAllPersonalDecks.get(position - 1) instanceof Download))
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
				
				sharedDeckTitle.setText(personalDeckTitle);
				sharedDeckTitle.setVisibility(View.VISIBLE);
				sharedDeckFacts.setVisibility(View.GONE);
			}
			
			return row;
		}
		
	}
	
}
