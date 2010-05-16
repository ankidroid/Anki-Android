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
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.AdapterView.OnItemClickListener;

import com.ichi2.async.Connection;
import com.ichi2.async.Connection.Payload;
import com.tomgibara.android.veecheck.util.PrefSettings;

public class PersonalDeckPicker extends Activity {

	/**
	 * Broadcast that informs us when the sd card is about to be unmounted
	 */
	private BroadcastReceiver mUnmountReceiver = null;
	
	private ProgressDialog progressDialog;
	
	private AlertDialog noConnectionAlert;
	
	private AlertDialog connectionFailedAlert;
	
	private List<String> mPersonalDecks;
	private ListView mPersonalDecksListView;
	private ArrayAdapter<String> mPersonalDecksAdapter;
	
	private String username;
	private String password;
	private String deckName;
	private String deckPath;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		// Remove the status bar
		getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
		
		setContentView(R.layout.main);
		registerExternalStorageListener();
		
		initAlertDialogs();
		
		SharedPreferences pref = PrefSettings.getSharedPrefs(getBaseContext());
		username = pref.getString("username", "");
		password = pref.getString("password", "");
		
		mPersonalDecks = new ArrayList<String>();
		mPersonalDecksAdapter = new ArrayAdapter<String>(this, R.layout.personal_deck_item, R.id.PersonalDeckTitle, mPersonalDecks);
		mPersonalDecksListView = (ListView)findViewById(R.id.files);
		mPersonalDecksListView.setAdapter(mPersonalDecksAdapter);
		mPersonalDecksListView.setOnItemClickListener(new OnItemClickListener() {

			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) 
			{
				SharedPreferences pref = PrefSettings.getSharedPrefs(getBaseContext());
				deckName = mPersonalDecks.get(position);
				deckPath = pref.getString("deckPath", AnkiDroidApp.getStorageDirectory()) + "/" + deckName + ".anki";
				
				Connection.downloadPersonalDeck(downloadSharedDeckListener, new Connection.Payload(new Object[] {username, password, deckName, deckPath}));
			}
			
		});
		
		Connection.getPersonalDecks(getPersonalDecksListener, new Connection.Payload(new Object[] {username, password}));
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
				mPersonalDecksAdapter.notifyDataSetChanged();
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
			progressDialog.dismiss();
			if(data.success)
			{
				Intent intent = PersonalDeckPicker.this.getIntent();
				// Return the name of the downloaded deck
				intent.putExtra(AnkiDroid.OPT_DB, deckPath);
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
			progressDialog = ProgressDialog.show(PersonalDeckPicker.this, "", getResources().getString(R.string.downloading_shared_deck));
		}

		@Override
		public void onProgressUpdate(Object... values) {
			//Pass
		}
		
	};
}
