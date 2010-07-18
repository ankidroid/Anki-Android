/***************************************************************************************
* Copyright (c) 2009 Edu Zamora <edu.zasu@gmail.com>                                   *
*                                                                                      *
* This program is free software; you can redistribute it and/or modify it under        *
* the terms of the GNU General Public License as published by the Free Software        *
* Foundation; either version 3 of the License, or (at your option) any later           *
* version.                                                                             *
*                                                                                      *
* This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
* WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
* PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
*                                                                                      *
* You should have received a copy of the GNU General Public License along with         *
* this program.  If not, see <http://www.gnu.org/licenses/>.                           *
****************************************************************************************/

package com.ichi2.async;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.net.ConnectivityManager;
import android.os.AsyncTask;
import android.util.Log;

import com.ichi2.anki.AnkiDb;
import com.ichi2.anki.AnkiDroidProxy;
import com.ichi2.anki.Deck;
import com.ichi2.anki.R;
import com.ichi2.anki.SharedDeck;
import com.ichi2.anki.SyncClient;

public class Connection extends AsyncTask<Connection.Payload, Object, Connection.Payload>
{
    public static final String TAG = "Connection";
    public static Context context;
    
    public static final int TASK_TYPE_LOGIN = 0;
    public static final int TASK_TYPE_GET_SHARED_DECKS = 1;
    public static final int TASK_TYPE_DOWNLOAD_SHARED_DECK = 2;
    public static final int TASK_TYPE_GET_PERSONAL_DECKS = 3;
    public static final int TASK_TYPE_DOWNLOAD_PERSONAL_DECK = 4;
    public static final int TASK_TYPE_SYNC_DECK = 5;
    public static final int TASK_TYPE_SYNC_DECK_FROM_PAYLOAD = 6;

	private static Connection instance;
	private TaskListener listener;
		
	private int CurrentProgress;	

	private static Connection launchConnectionTask(TaskListener listener, Payload data)
	{
		/*if(!isOnline())
		{
			data.success = false;
			listener.onDisconnected();
			return null;
		}*/
		try
		{
			if ((instance != null) && (instance.getStatus() != AsyncTask.Status.FINISHED))
				instance.get();
		} catch (Exception e)
		{
			e.printStackTrace();
		}

		instance = new Connection();
		instance.listener = listener;

		return (Connection)instance.execute(data);
	}
	
	   /*
     * Runs on GUI thread
     */
    protected void onPreExecute() 
    {
    	listener.onPreExecute();
    }

    /*
     * Runs on GUI thread
     */
    public void onPostExecute(Payload data)
    {
    	listener.onPostExecute(data);
    }

    /*
     * Runs on GUI thread
     */
    public void onProgressUpdate(Object... values)
    {
    	listener.onProgressUpdate(values);
    }
    
    public static Connection login(TaskListener listener, Payload data)
    {
    	data.taskType = TASK_TYPE_LOGIN;
    	return launchConnectionTask(listener, data);
    }
    
	public static Connection getSharedDecks(TaskListener listener, Payload data)
	{
		data.taskType = TASK_TYPE_GET_SHARED_DECKS;
		return launchConnectionTask(listener, data);
	}
	
	public static Connection downloadSharedDeck(TaskListener listener, Payload data)
	{
		data.taskType = TASK_TYPE_DOWNLOAD_SHARED_DECK;
		return launchConnectionTask(listener, data);
	}
	
	public static Connection getPersonalDecks(TaskListener listener, Payload data)
	{
		data.taskType = TASK_TYPE_GET_PERSONAL_DECKS;
		return launchConnectionTask(listener, data);
	}
	
	public static Connection downloadPersonalDeck(TaskListener listener, Payload data)
	{
		data.taskType = TASK_TYPE_DOWNLOAD_PERSONAL_DECK;
		return launchConnectionTask(listener, data);
	}
	
	public static Connection syncDeck(TaskListener listener, Payload data)
	{
		data.taskType = TASK_TYPE_SYNC_DECK;
		return launchConnectionTask(listener, data);
	}
	
	public static Connection syncDeckFromPayload(TaskListener listener, Payload data)
	{
		data.taskType = TASK_TYPE_SYNC_DECK_FROM_PAYLOAD;
		return launchConnectionTask(listener, data);
	}
	
	@Override
	protected Payload doInBackground(Payload... params) {
		Payload data = params[0];
		
		CurrentProgress=0;
		publishProgress(CurrentProgress+=5,context.getResources().getString(R.string.determining_task));
		
		switch(data.taskType)
		{
			case TASK_TYPE_LOGIN:
				return doInBackgroundLogin(data);
				
			case TASK_TYPE_GET_SHARED_DECKS:
				return doInBackgroundGetSharedDecks(data);

			case TASK_TYPE_DOWNLOAD_SHARED_DECK:
				return doInBackgroundDownloadSharedDeck(data);
				
			case TASK_TYPE_GET_PERSONAL_DECKS:
				return doInBackgroundGetPersonalDecks(data);
			
			case TASK_TYPE_DOWNLOAD_PERSONAL_DECK:
				return doInBackgroundDownloadPersonalDeck(data);
				
			case TASK_TYPE_SYNC_DECK:
				return doInBackgroundSyncDeck(data);
				
			case TASK_TYPE_SYNC_DECK_FROM_PAYLOAD:
				return doInBackgroundSyncDeckFromPayload(data);
				
			default:
				return null;
		}
	}

	private Payload doInBackgroundLogin(Payload data)
	{
		try {
			String username = (String) data.data[0];
			String password = (String) data.data[1];
			AnkiDroidProxy server = new AnkiDroidProxy(username, password);
			
			int status = server.connect();
			if(status != AnkiDroidProxy.LOGIN_OK)
			{
				data.success = false;
				data.errorType = status;
			}
		} catch (Exception e) {
			data.success = false;
			data.exception = e;
			Log.e(TAG, "Error trying to log in");
		}
		return data;
	}
	
	private Payload doInBackgroundGetSharedDecks(Payload data)
	{
		try {
			data.result = AnkiDroidProxy.getSharedDecks();
		} catch (Exception e) {
			data.success = false;
			data.exception = e;
			Log.e(TAG, "Error getting shared decks = " + e.getMessage());
			e.printStackTrace();
		}
		return data;
	}

	private Payload doInBackgroundDownloadSharedDeck(Payload data)
	{
		try {
			data.result = AnkiDroidProxy.downloadSharedDeck((SharedDeck)data.data[0], (String)data.data[1]);
		} catch (Exception e) {
			data.success = false;
			data.exception = e;
			Log.e(TAG, "Error downloading shared deck = " + e.getMessage());
			e.printStackTrace();
		}
		return data;
	}
	
	private Payload doInBackgroundGetPersonalDecks(Payload data)
	{
		try {
			String username = (String)data.data[0];
			String password = (String)data.data[1];
			AnkiDroidProxy server = new AnkiDroidProxy(username, password);
			data.result = server.getPersonalDecks();
		} catch (Exception e) {
			data.success = false;
			data.exception = e;
			Log.e(TAG, "Error getting personal decks = " + e.getMessage());
			e.printStackTrace();
		}
		return data;
	}
	
	private Payload doInBackgroundDownloadPersonalDeck(Payload data)
	{
		try {
			String username = (String)data.data[0];
			String password = (String)data.data[1];
			String deckName = (String)data.data[2];
			String deckPath = (String)data.data[3];
			SyncClient.fullSyncFromServer(password, username, deckName, deckPath);
		} catch (Exception e) {
			data.success = false;
			data.exception = e;
			Log.e(TAG, "Error downloading shared deck = " + e.getMessage());
			e.printStackTrace();
		}
		return data;
	}
	
	private Payload doInBackgroundSyncDeck(Payload data)
	{
		
		publishProgress(CurrentProgress+=10,context.getResources().getString(R.string.starting_db_transaction));
		
		
		AnkiDb.database.beginTransaction();
		try {
			String username = (String)data.data[0];
			String password = (String)data.data[1];
			Deck deck = (Deck)data.data[2];
			String deckPath = (String)data.data[3];
			String syncName = deck.getSyncName();
			
			Log.i(TAG, "Starting sync: username = " + username + ", password = " + password + ", deckPath = " + deckPath + ", syncName = " + syncName);
			AnkiDroidProxy server = new AnkiDroidProxy(username, password);
			
			
			publishProgress(CurrentProgress+=10,context.getResources().getString(R.string.connecting_to_server));
			

			server.connect();
						
			if(!server.hasDeck(syncName))
			{
				Log.i(TAG, "AnkiOnline does not have this deck: Creating it...");
				
				publishProgress(CurrentProgress+=10,context.getResources().getString(R.string.creating_deck_at_server));
				
				server.createDeck(syncName);
			}
			int timediff = (int) (server.getTimestamp() - (System.currentTimeMillis() / 1000));
			if(timediff > 300)
			{
				Log.i(TAG, "");
				//TODO: Control what happens when the clocks are unsynchronized
			}
			SyncClient client = new SyncClient(deck);
			client.setServer(server);
			server.setDeckName(syncName);
			if(client.prepareSync())
			{
				
				publishProgress(CurrentProgress+=10,context.getResources().getString(R.string.calculating_summaries));
				
				
				JSONArray sums = client.summaries();
				
				
				publishProgress(CurrentProgress+=10,context.getResources().getString(R.string.determining_sync_type));
				
				
				if(client.needFullSync(sums))
				{
					Log.i(TAG, "DECK NEEDS FULL SYNC");
					String syncFrom = client.prepareFullSync();
					
					publishProgress(CurrentProgress+=10,context.getResources().getString(R.string.performing_full_sync));
					
					if("fromLocal".equalsIgnoreCase(syncFrom))
					{
						SyncClient.fullSyncFromLocal(password, username, syncName, deckPath);
					}
					else if("fromServer".equalsIgnoreCase(syncFrom))
					{
						SyncClient.fullSyncFromServer(password, username, syncName, deckPath);
					}
					
					publishProgress(CurrentProgress+=10,context.getResources().getString(R.string.setting_db_transaction_success));
					
					AnkiDb.database.setTransactionSuccessful();
					
					publishProgress(CurrentProgress+=10,context.getResources().getString(R.string.finishing_db_transaction));
					
					AnkiDb.database.endTransaction();
					
					publishProgress(CurrentProgress+=10,context.getResources().getString(R.string.closing_deck));
					
					deck.closeDeck();
					
					publishProgress(CurrentProgress+=10,context.getResources().getString(R.string.opening_deck_after_sync));
					
					deck = Deck.openDeck(deckPath);
					
					publishProgress(CurrentProgress+=10,context.getResources().getString(R.string.setting_deck_as_current));
					
					client.setDeck(deck);
				}
				else
				{
					Log.i(TAG, "DECK DOES NOT NEED FULL SYNC");
					
					publishProgress(CurrentProgress+=10,context.getResources().getString(R.string.generating_payload));
					
					JSONObject payload = client.genPayload(sums);
					
					publishProgress(CurrentProgress+=10,context.getResources().getString(R.string.getting_payload_reply));
					
					JSONObject payloadReply = client.getServer().applyPayload(payload);
					
					publishProgress(CurrentProgress+=10,context.getResources().getString(R.string.applying_payload_reply));
					
					client.applyPayloadReply(payloadReply);
					
					publishProgress(CurrentProgress+=10,context.getResources().getString(R.string.commiting_payload_to_db));
					
					deck.lastLoaded = deck.modified;
					deck.commitToDB();
					
					publishProgress(CurrentProgress+=10,context.getResources().getString(R.string.setting_db_transaction_success));
					
					AnkiDb.database.setTransactionSuccessful();
				}
			}
			else
			{
				Log.i(TAG, "NO CHANGES.");
			}
		} catch (Exception e) {
			data.success = false;
			data.exception = e;
			Log.e(TAG, "Error synchronizing deck = " + e.getMessage());
			e.printStackTrace();
		} finally {
			if(AnkiDb.database.inTransaction())
			{
				
				publishProgress(CurrentProgress+=10,context.getResources().getString(R.string.finishing_db_transaction));
				
				AnkiDb.database.endTransaction();
			}
		}
		return data;
	}
	
	private Payload doInBackgroundSyncDeckFromPayload(Payload data)
	{
		Log.i(TAG, "SyncDeckFromPayload");
		Deck deck = (Deck)data.data[0];
		SyncClient client = new SyncClient(deck);
		BufferedReader bufPython;
		try {
			bufPython = new BufferedReader(new FileReader("/sdcard/jsonObjectPython.txt"));
			JSONObject payloadReply = new JSONObject(bufPython.readLine());
			client.applyPayloadReply(payloadReply);
			deck.lastLoaded = deck.modified;
			deck.commitToDB();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (JSONException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		Log.i(TAG, "Synchronization from payload finished!");
		return data;
	}
	
	public static boolean isOnline() 
	{
		 ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
		 
		 if(cm.getActiveNetworkInfo() != null)
			 return cm.getActiveNetworkInfo().isConnectedOrConnecting();
		 else return false;
	}

	public static void setContext(Context applicationContext)
	{
		context = applicationContext;
	}
	
	public static interface TaskListener
	{
		public void onPreExecute();

		public void onProgressUpdate(Object... values);
		
		public void onPostExecute(Payload data);
		
		public void onDisconnected();
	}
	
    public static class Payload
    {
        public int taskType;
        public Object[] data;
        public Object result;
        public boolean success;
        public int errorType;
        public Exception exception;

        public Payload(Object[] data) {
        	this.data = data;
        	this.success = true;
        }
        
        public Payload(int taskType, Object[] data) {
            this.taskType = taskType;
            this.data = data;
            this.success = true;
        }
    }
}

