package com.ichi2.anki;

import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
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
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
		
		initAlertDialogs();
		
		mSharedDecks = new ArrayList<SharedDeck>();
		mSharedDecksAdapter = new SimpleAdapter(this, mSharedDecks, R.layout.shared_deck_item, new String[] {"title"}, new int[] {R.id.SharedDeckTitle});
		mSharedDecksListView = (ListView)findViewById(R.id.files);
		mSharedDecksListView.setAdapter(mSharedDecksAdapter);
		mSharedDecksListView.setOnItemClickListener(new OnItemClickListener() {

			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) 
			{
				Connection.downloadSharedDeck(downloadSharedDeckListener, new Connection.Payload(new Object[] {mSharedDecks.get(position)}));
			}
			
		});
		Connection.getSharedDecks(getSharedDecksListener, new Connection.Payload(new Object[] {}));
	}

	/**
	 * Create AlertDialogs used on all the activity
	 */
	private void initAlertDialogs()
	{
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		
		builder.setMessage("No Internet connection.");
		builder.setPositiveButton("Ok", null);
		noConnectionAlert = builder.create();
		
	    builder.setMessage("The connection was unsuccessful. Check your connection settings and try again, please.");
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
			if(!data.success)
			{
				connectionFailedAlert.show();
			}
		}

		@Override
		public void onPreExecute() {
			progressDialog = ProgressDialog.show(SharedDeckPicker.this, "", "Downloading shared deck...");
		}

		@Override
		public void onProgressUpdate(Object... values) {
			//Pass
		}
		
	};
}
