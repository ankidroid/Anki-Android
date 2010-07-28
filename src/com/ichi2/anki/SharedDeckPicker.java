package com.ichi2.anki;

import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.ichi2.async.Connection;
import com.ichi2.async.Connection.Payload;
import com.tomgibara.android.veecheck.util.PrefSettings;

public class SharedDeckPicker extends Activity {

	private DownloadManager mDownloadManager;
	//private SharedDownloadManager mDownloadManager;
	
	/**
	 * Broadcast that informs us when the sd card is about to be unmounted
	 */
	private BroadcastReceiver mUnmountReceiver = null;
	
	private ProgressDialog progressDialog;
	
	private AlertDialog noConnectionAlert;
	
	private AlertDialog connectionFailedAlert;
	
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
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		setContentView(R.layout.download_deck_picker);
		
		registerExternalStorageListener();
		initAlertDialogs();
		
		SharedPreferences pref = PrefSettings.getSharedPrefs(getBaseContext());
		username = pref.getString("username", "");
		password = pref.getString("password", "");
		
		mSharedDeckDownloads = new ArrayList<Download>();
		mSharedDecks = new ArrayList<SharedDeck>();
		
		mAllSharedDecks = new ArrayList<Object>();
		//mSharedDecksAdapter = new SimpleAdapter(this, mAllSharedDecks, R.layout.shared_deck_item, new String[] {"title", "title", "facts"}, new int[] {R.id.download_shared_deck_title, R.id.shared_deck_title, R.id.shared_deck_facts});
		mSharedDecksAdapter =new SharedDecksAdapter();
		mSharedDecksListView = (ListView)findViewById(R.id.list);
		mSharedDecksListView.setAdapter(mSharedDecksAdapter);
		mSharedDecksListView.setOnItemClickListener(new OnItemClickListener() {

			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) 
			{
				deckToDownload = (SharedDeck) mAllSharedDecks.get(position);
				
				SharedDeckDownload sharedDeckDownload = new SharedDeckDownload(deckToDownload.getId(), deckToDownload.getTitle(), deckToDownload.getFileName(), deckToDownload.getSize());
				mSharedDeckDownloads.add(sharedDeckDownload);
				refreshSharedDecksList();
				
				SharedPreferences preferences = PrefSettings.getSharedPrefs(getBaseContext());
				String deckPath = preferences.getString("deckPath", AnkiDroidApp.getStorageDirectory());
				mDownloadManager.downloadFile(sharedDeckDownload);
				//new Connection().downloadSharedDeck(downloadSharedDeckListener, new Connection.Payload(new Object[] {deckToDownload, deckPath}));
			}
		});
		mDownloadManager = DownloadManager.getSharedInstance(getApplicationContext(), username, password, pref.getString("deckPath", AnkiDroidApp.getStorageDirectory()));
		//mDownloadManager = SharedDownloadManager.getSharedInstance(getApplicationContext(), username, password, pref.getString("deckPath", AnkiDroidApp.getStorageDirectory()));
		Connection.getSharedDecks(getSharedDecksListener, new Connection.Payload(new Object[] {}));
	}
	
	@Override
	protected void onResume() {
		super.onResume();
		mDownloadManager.registerListener(DownloadManager.SHARED_DECK_DOWNLOAD, downloadListener);
		//mDownloadManager.registerListener(downloadListener);
		mSharedDeckDownloads.clear();
		mSharedDeckDownloads.addAll(mDownloadManager.getDownloadsList(DownloadManager.SHARED_DECK_DOWNLOAD));
		//mSharedDeckDownloads.addAll(mDownloadManager.getDownloadsList());
		refreshSharedDecksList();
	}
	
	@Override
	protected void onPause() {
		super.onPause();
	}

	@Override
    public void onDestroy()
    {
    	super.onDestroy();
    	if(mUnmountReceiver != null)
    		unregisterReceiver(mUnmountReceiver);
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
	
	/**
	 * Listeners
	 */
	DownloadManager.DownloadsListener downloadListener = new DownloadManager.DownloadsListener() {
		
		@Override
		public void onStateChanged(List downloads) {
			mSharedDeckDownloads.clear();
			mSharedDeckDownloads.addAll(downloads);
			
			refreshSharedDecksList();
		}
		
	};
	
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
				mAllSharedDecks.clear();
				mAllSharedDecks.addAll(mSharedDeckDownloads);
				mAllSharedDecks.addAll(mSharedDecks);
				mSharedDecksAdapter.notifyDataSetChanged();
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
