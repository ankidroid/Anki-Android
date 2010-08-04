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

public class SharedDeckPicker extends Activity {

	private static final String TAG = "AnkiDroid";
	
	/**
	 * Broadcast that informs us when the sd card is about to be unmounted
	 */
	private BroadcastReceiver mUnmountReceiver = null;
	
	private ProgressDialog mProgressDialog;
	
	private AlertDialog mNoConnectionAlert;
	
	private AlertDialog mConnectionFailedAlert;
	
	
	private Intent mDownloadManagerServiceIntent;
	// Service interface we will use to call the service
	private IDownloadManagerService mDownloadManagerService = null;
	
	List<Download> mSharedDeckDownloads;
	List<SharedDeck> mSharedDecks;
	
	List<Object> mAllSharedDecks;
	ListView mSharedDecksListView;
	SharedDecksAdapter mSharedDecksAdapter;
	
	
	/********************************************************************
	 * Lifecycle methods												*
	 ********************************************************************/
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		setContentView(R.layout.download_deck_picker);
		
		initDownloadManagerService();
		registerExternalStorageListener();
		initAlertDialogs();
		
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
				SharedDeck selectedDeck = (SharedDeck) mAllSharedDecks.get(position);
				
				SharedDeckDownload sharedDeckDownload = new SharedDeckDownload(selectedDeck.getId(), selectedDeck.getTitle());
				sharedDeckDownload.setSize(selectedDeck.getSize());
				mSharedDeckDownloads.add(sharedDeckDownload);
				refreshSharedDecksList();
				
				try {
					startService(mDownloadManagerServiceIntent);
					mDownloadManagerService.downloadFile(sharedDeckDownload);
				} catch (RemoteException e) {
					// There is nothing special we need to do if the service has crashed
					Log.e(TAG, "RemoteException = " + e.getMessage());
					e.printStackTrace();
				}
			}
		});
		
		Connection.getSharedDecks(getSharedDecksListener, new Connection.Payload(new Object[] {}));
	}
	
	@Override
	protected void onResume() {
		Log.i(TAG, "onResume");
		super.onResume();
		if(mDownloadManagerService != null)
		{
			try {
				
				mDownloadManagerService.registerSharedDeckCallback(mCallback);
				setSharedDeckDownloads(mDownloadManagerService.getSharedDeckDownloads());
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
		if(mDownloadManagerService != null)
		{
			try {
				mDownloadManagerService.unregisterSharedDeckCallback(mCallback);
			} catch (RemoteException e) {
				// There is nothing special we need to do if the service has crashed
				Log.e(TAG, "RemoteException = " + e.getMessage());
				e.printStackTrace();
			}
		}
	}

	@Override
	public void onDestroy()
	{
		super.onDestroy();
		releaseBroadcastReceiver();
		releaseService();
		releaseAlerts();
	}
	
	
	/********************************************************************
	 * Custom methods													*
	 ********************************************************************/
	
	private void initDownloadManagerService()
	{
		mDownloadManagerServiceIntent = new Intent(SharedDeckPicker.this, DownloadManagerService.class);
		// Needed when the incomplete downloads are resumed while entering SharedDeckPicker
		// if the Service gets shut down, we want it to be restarted automatically, so for this to happen it has to be started but not stopped
		startService(mDownloadManagerServiceIntent);
		bindService(mDownloadManagerServiceIntent, mConnection, Context.BIND_AUTO_CREATE);
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
	public void registerExternalStorageListener() 
	{
		if (mUnmountReceiver == null) 
		{
			mUnmountReceiver = new BroadcastReceiver() {
				@Override
				public void onReceive(Context context, Intent intent) {
					String action = intent.getAction();
					if (action.equals(Intent.ACTION_MEDIA_EJECT)) 
					{
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
	
	private void releaseBroadcastReceiver()
	{
		if(mUnmountReceiver != null)
		{
			unregisterReceiver(mUnmountReceiver);
			mUnmountReceiver = null;
		}
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
		mNoConnectionAlert = builder.create();
		
		builder.setMessage(res.getString(R.string.connection_unsuccessful));
		mConnectionFailedAlert = builder.create();
	}
	
	private void releaseAlerts()
	{
		// Needed in order to not try to show the alerts when the Activity does not exist anymore
		mNoConnectionAlert = null;
		mConnectionFailedAlert = null;
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
	
	private void finishNoStorageAvailable()
	{
		setResult(StudyOptions.CONTENT_NO_EXTERNAL_STORAGE);
		finish();
	}
	
	
	/********************************************************************
	 * Service Connection												*
	 ********************************************************************/
	
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
			mDownloadManagerService = IDownloadManagerService.Stub.asInterface(service);

			Log.i(TAG, "onServiceConnected");
			// We want to monitor the service for as long as we are
			// connected to it.
			try {
				mDownloadManagerService.registerSharedDeckCallback(mCallback);
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
	 * Listeners														*
	 ********************************************************************/
	
	Connection.TaskListener getSharedDecksListener = new Connection.TaskListener() {

		@Override
		public void onDisconnected() {
			if(mNoConnectionAlert != null)
			{
				mNoConnectionAlert.show();
			}
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
				if(mConnectionFailedAlert != null)
				{
					mConnectionFailedAlert.show();
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
	
	
	/********************************************************************
	 * Callbacks														*
	 ********************************************************************/

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
	
	
	/********************************************************************
	 * Adapters															*
	 ********************************************************************/
	
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
			DownloadViewWrapper wrapper = null;
			Resources res = getResources();
			
			if(row == null)
			{
				row = getLayoutInflater().inflate(R.layout.download_deck_item, null);
				wrapper = new DownloadViewWrapper(row);
				row.setTag(wrapper);
			}
			else
			{
				wrapper = (DownloadViewWrapper)row.getTag();
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
					headerTitle.setText(res.getString(R.string.currently_downloading));
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
					
					case SharedDeckDownload.UPDATE:
						progressText.setText(res.getString(R.string.updating));
						break;
						
					default:
						progressText.setText(res.getString(R.string.error));
						break;
				}
				progressText.setVisibility(View.VISIBLE);
			}
			else
			{
				SharedDeck sharedDeck = (SharedDeck) obj;
				if(position > 0 && (mAllSharedDecks.get(position - 1) instanceof Download))
				{
					headerTitle.setText(res.getString(R.string.shared_decks));
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
				int numFacts = sharedDeck.getFacts();
				if(numFacts == 1)
				{
					sharedDeckFacts.setText(numFacts + " " + res.getString(R.string.fact));
				}
				else
				{
					sharedDeckFacts.setText(numFacts + " " + res.getString(R.string.facts));
				}
				sharedDeckFacts.setVisibility(View.VISIBLE);
			}
			
			return row;
		}
		
	}
}
