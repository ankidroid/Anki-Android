package com.ichi2.anki;

import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.AdapterView.OnItemClickListener;

import com.ichi2.async.Connection;
import com.ichi2.async.Connection.Payload;

public class SharedDeckPicker extends Activity {

	private ProgressDialog progressDialog;
	
	private AlertDialog noConnectionAlert;
	
	private AlertDialog connectionFailedAlert;
	
	List<SharedDeck> mSharedDecks;
	ListView mSharedDecksListView;
	SimpleAdapter mSharedDecksAdapter;
	
	SharedDeck downloadedDeck;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
		
		initAlertDialogs();
		
		mSharedDecks = new ArrayList<SharedDeck>();
		mSharedDecksAdapter = new SimpleAdapter(this, mSharedDecks, R.layout.shared_deck_item, new String[] {"title", "facts"}, new int[] {R.id.SharedDeckTitle, R.id.SharedDeckFacts});
		mSharedDecksListView = (ListView)findViewById(R.id.files);
		mSharedDecksListView.setAdapter(mSharedDecksAdapter);
		mSharedDecksListView.setOnItemClickListener(new OnItemClickListener() {

			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) 
			{
				downloadedDeck = mSharedDecks.get(position);
				Connection.downloadSharedDeck(downloadSharedDeckListener, new Connection.Payload(new Object[] {downloadedDeck}));
			}
			
		});
		Connection.getSharedDecks(getSharedDecksListener, new Connection.Payload(new Object[] {}));
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
			progressDialog.dismiss();
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
			progressDialog = ProgressDialog.show(SharedDeckPicker.this, "", getResources().getString(R.string.downloading_shared_deck));
		}

		@Override
		public void onProgressUpdate(Object... values) {
			//Pass
		}
		
	};
}
