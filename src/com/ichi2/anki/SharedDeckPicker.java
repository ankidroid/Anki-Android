package com.ichi2.anki;

import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
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
	
	List<SharedDeck> mSharedDecks;
	ListView mSharedDecksListView;
	SimpleAdapter mSharedDecksAdapter;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
		
		mSharedDecks = new ArrayList<SharedDeck>();
		mSharedDecksAdapter = new SimpleAdapter(this, mSharedDecks, R.layout.shared_deck_item, new String[] {"title"}, new int[] {R.id.SharedDeckTitle});
		mSharedDecksListView = (ListView)findViewById(R.id.files);
		mSharedDecksListView.setAdapter(mSharedDecksAdapter);
		mSharedDecksListView.setOnItemClickListener(new OnItemClickListener() {

			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) 
			{
				
			}
			
		});
		Connection.getSharedDecks(getSharedDecksListener, new Connection.Payload(new Object[] {}));
	}

	
	/**
	 * Listeners
	 */
	Connection.TaskListener getSharedDecksListener = new Connection.TaskListener() {

		@Override
		public void onDisconnected() {
			// TODO Auto-generated method stub
			
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
}
