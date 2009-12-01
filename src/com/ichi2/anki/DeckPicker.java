/****************************************************************************************
* Copyright (c) 2009 																   *
* Edu Zamora <email@email.com>                                            			   *
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
package com.ichi2.anki;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.TreeSet;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.SQLException;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.SimpleAdapter;

/**
 * Allows the user to choose a deck from the filesystem.
 * 
 * @author Andrew Dubya
 * 
 */
public class DeckPicker extends Activity implements Runnable
{

	private static final String TAG = "Ankidroid";
	
	/**
	 * Dialogs
	 */
	private static final int DIALOG_NO_SDCARD = 0;
	
	private ProgressDialog dialog;
	
	private DeckPicker mSelf;

	private SimpleAdapter mDeckListAdapter;

	private ArrayList<HashMap<String, String>> mDeckList;

	private ListView mDeckListView;

	private File[] mFileList;

	private ReentrantLock mLock = new ReentrantLock();

	private Condition mCondFinished = mLock.newCondition();

	private boolean mIsFinished = true;

	private boolean mDeckIsSelected = false;
		
	private BroadcastReceiver mUnmountReceiver = null;

	AdapterView.OnItemClickListener mDeckSelHandler = new AdapterView.OnItemClickListener()
	{
		public void onItemClick(AdapterView<?> parent, View v, int p, long id)
		{
			mSelf.handleDeckSelection(p);
		}
	};

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) throws SQLException
	{
		Log.i(TAG, "DeckPicker - onCreate");
		super.onCreate(savedInstanceState);
		
		registerExternalStorageListener();
		
		mSelf = this;
		SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
		String deckPath = preferences.getString("deckPath", "/sdcard");
		setContentView(R.layout.main);

		mDeckList = new ArrayList<HashMap<String, String>>();
		mDeckListView = (ListView) findViewById(R.id.files);
		mDeckListAdapter = new SimpleAdapter(this, mDeckList, R.layout.deck_picker_list, new String[]
		{ "name", "due", "new", "showProgress" }, new int[]
		{ R.id.DeckPickerName, R.id.DeckPickerDue, R.id.DeckPickerNew, R.id.DeckPickerProgress });

		mDeckListAdapter.setViewBinder(new SimpleAdapter.ViewBinder()
		{
			public boolean setViewValue(View view, Object data, String text)
			{
				if (view instanceof ProgressBar)
				{
					if (text.equals("true"))
						view.setVisibility(View.VISIBLE);
					else
						view.setVisibility(View.GONE);
					return true;
				}
				return false;
			}
		});
		mDeckListView.setOnItemClickListener(mDeckSelHandler);
		mDeckListView.setAdapter(mDeckListAdapter);

		populateDeckList(deckPath);
	}

	public void onPause()
	{
		Log.i(TAG, "DeckPicker - onPause");

		super.onPause();
		waitForDeckLoaderThread();
	}

	protected Dialog onCreateDialog(int id)
	{
		Dialog dialog;
		switch(id)
		{
		case DIALOG_NO_SDCARD:
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setMessage("The SD card could not be read. Please, turn off USB storage.");
			builder.setPositiveButton("OK", null);
			dialog = builder.create();
			break;
		
		default:
			dialog = null;
		}
		
		return dialog;
	}
	
	private void populateDeckList(String location)
	{
		Log.i(TAG, "DeckPicker - populateDeckList");
	
		
		Resources res = getResources();
		int len = 0;
		File[] fileList;
		TreeSet<HashMap<String, String>> tree = new TreeSet<HashMap<String, String>>(new HashMapCompare());
		
		File dir = new File(location);
		fileList = dir.listFiles(new AnkiFilter());
		
		if (dir.exists() && dir.isDirectory() && fileList != null)
		{
			len = fileList.length;
		}
		mFileList = fileList;
		if (len > 0 && fileList != null)
		{
			Log.i(TAG, "DeckPicker - populateDeckList, number of anki files = " + len);
			for (int i = 0; i < len; i++)
			{
				String absPath = fileList[i].getAbsolutePath();

				Log.i(TAG, "DeckPicker - populateDeckList, file " + i + " :" + fileList[i].getName());
				
				try
				{
					HashMap<String, String> data = new HashMap<String, String>();
					data.put("name", fileList[i].getName().replaceAll(".anki", ""));
					data.put("due", res.getString(R.string.deckpicker_loaddeck));
					data.put("new", "");
					data.put("mod", String.format("%f", Deck.getLastModified(absPath)));
					data.put("filepath", absPath);
					data.put("showProgress", "true");

					boolean result = tree.add(data);
				} catch (SQLException e) 
				{
					Log.w(TAG, "DeckPicker - populateDeckList, File " + fileList[i].getName() + " is not a real anki file");
				}
			}

			Thread thread = new Thread(this);
			thread.start();
		} else
		{
			Log.i(TAG, "populateDeckList - No decks found.");
			//There is no sd card attached (wrap this code in a function called something like isSdMounted()
			//and place it in a utils class
			if(!Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState()))
			{
				Log.i(TAG, "populateDeckList - No sd card.");
				setTitle(R.string.deckpicker_title_nosdcard);
				showDialog(DIALOG_NO_SDCARD);
			}
			
			HashMap<String, String> data = new HashMap<String, String>();
			data.put("name", res.getString(R.string.deckpicker_nodeck));
			data.put("new", "");
			data.put("due", "");
			data.put("mod", "1");
			data.put("showProgress", "false");

			tree.add(data);
		}
		mDeckList.clear();
		mDeckList.addAll(tree);
		mDeckListView.clearChoices();
		mDeckListAdapter.notifyDataSetChanged();  
		Log.i(TAG, "DeckPicker - populateDeckList, Ending");
	}

	private static final class AnkiFilter implements FileFilter
	{
		public boolean accept(File pathname)
		{
			if (pathname.isFile() && pathname.getName().endsWith(".anki"))
				return true;
			return false;
		}

	}

	private static final class HashMapCompare implements Comparator<HashMap<String, String>>
	{
		public int compare(HashMap<String, String> object1, HashMap<String, String> object2)
		{	
			//Order by last modification date (last deck modified first)
			if((Float.parseFloat(object2.get("mod")) - Float.parseFloat(object1.get("mod"))) != 0)
				return (int) (Float.parseFloat(object2.get("mod")) - Float.parseFloat(object1.get("mod")));
			//But if there are two decks with the same date of modification, order them in alphabetical order
			else
				return object1.get("filepath").compareToIgnoreCase(object2.get("filepath"));
		}
	}

	private void handleDeckSelection(int id)
	{
		String deckFilename = null;

		waitForDeckLoaderThread();

		@SuppressWarnings("unchecked")
		HashMap<String, String> data = (HashMap<String, String>) mDeckListAdapter.getItem(id);
		deckFilename = data.get("filepath");

		if (deckFilename != null)
		{
			Log.i(TAG, "Selected " + deckFilename);
			Intent intent = this.getIntent();
			intent.putExtra(Ankidroid.OPT_DB, deckFilename);
			setResult(RESULT_OK, intent);

			finish();
		}
	}

	private void waitForDeckLoaderThread()
	{
		mDeckIsSelected = true;
		Log.i(TAG, "DeckPicker - waitForDeckLoaderThread(), mDeckIsSelected set to true");
		mLock.lock();
		try
		{
			while (!mIsFinished)
				mCondFinished.await();
		} catch (InterruptedException e)
		{
			e.printStackTrace();
		} finally
		{
			mLock.unlock();
		}
	}

	public void run()
	{
		Log.i(TAG, "Thread run - Beginning");
		int len = 0;
		if (mFileList != null)
			len = mFileList.length;

		if (len > 0 && mFileList != null)
		{
			mLock.lock();
			try
			{
				Log.i(TAG, "Thread run - Inside lock");

				mIsFinished = false;
				for (int i = 0; i < len; i++)
				{

					// Don't load any more decks if one has already been
					// selected.
					Log.i(TAG, "Thread run - Before break mDeckIsSelected = " + mDeckIsSelected);
					if (mDeckIsSelected)
						break;
					

					String path = mFileList[i].getAbsolutePath();
					Deck deck;

					try
					{
						deck = Deck.openDeck(path);
					} catch (SQLException e)
					{
						Log.w(TAG, "Could not open database " + path);
						continue;
					}
					int dueCards = deck.failedSoonCount + deck.revCount;
					int totalCards = deck.cardCount;
					int newCards = deck.newCountToday;
					deck.closeDeck();

					Bundle data = new Bundle();
					data.putString("absPath", path);
					data.putInt("due", dueCards);
					data.putInt("total", totalCards);
					data.putInt("new", newCards);
					Message msg = Message.obtain();
					msg.setData(data);

					handler.sendMessage(msg);
				}
				mIsFinished = true;
				mCondFinished.signal();
			} finally
			{
				mLock.unlock();
			}
		}
	}

	private Handler handler = new Handler()
	{
		public void handleMessage(Message msg)
		{
			Bundle data = msg.getData();
			Resources res = mSelf.getResources();

			String path = data.getString("absPath");
			String dueString = String.format(res.getString(R.string.deckpicker_due), data.getInt("due"), data
			        .getInt("total"));
			String newString = String.format(res.getString(R.string.deckpicker_new), data.getInt("new"));

			int count = mDeckListAdapter.getCount();
			for (int i = 0; i < count; i++)
			{
				@SuppressWarnings("unchecked")
				HashMap<String, String> map = (HashMap<String, String>) mDeckListAdapter.getItem(i);
				if (map.get("filepath").equals(path))
				{
					map.put("due", dueString);
					map.put("new", newString);
					map.put("showProgress", "false");
				}
			}

			mDeckListAdapter.notifyDataSetChanged();
			Log.i(TAG, "DeckPicker - mDeckList notified of changes");
		}
	};
	
	
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
                    if (action.equals(Intent.ACTION_MEDIA_UNMOUNTED)) {
                    	Log.i(TAG, "DeckPicker - mUnmountReceiver, Action = Media Unmounted");
                		SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
                		String deckPath = preferences.getString("deckPath", "/sdcard");
                    	populateDeckList(deckPath);
                    } else if (action.equals(Intent.ACTION_MEDIA_MOUNTED)) {
                    	Log.i(TAG, "DeckPicker - mUnmountReceiver, Action = Media Mounted");
                		SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
                		String deckPath = preferences.getString("deckPath", "/sdcard");
                		mDeckIsSelected = false;
                		setTitle(R.string.deckpicker_title);
                    	populateDeckList(deckPath);
                    }
                }
            };
            IntentFilter iFilter = new IntentFilter();
            iFilter.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
            iFilter.addAction(Intent.ACTION_MEDIA_MOUNTED);
            iFilter.addDataScheme("file");
            registerReceiver(mUnmountReceiver, iFilter);
        }
    }
    
    public void onStop()
    {
    	super.onStop();
    	Log.i(TAG, "DeckPicker - onStop()");
    	unregisterReceiver(mUnmountReceiver);
    }
    
	/*private void logTree(TreeSet<HashMap<String, String>> tree)
	{
		Iterator<HashMap<String, String>> it = tree.iterator();
		while(it.hasNext())
		{
			HashMap<String, String> map = it.next();
			Log.i(TAG, "logTree - " + map.get("name") + ", due = " + map.get("due") + ", new = " + map.get("new") + ", showProgress = " + map.get("showProgress") + ", filepath = " + map.get("filepath") + ", last modified = " + map.get("mod"));
		}
	}*/

}
