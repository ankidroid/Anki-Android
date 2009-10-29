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
import android.content.Intent;
import android.content.res.Resources;
import android.database.SQLException;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
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

	private DeckPicker mSelf;

	private SimpleAdapter mDeckListAdapter;

	private ArrayList<HashMap<String, String>> mDeckList;

	private ListView mDeckListView;

	private File[] mFileList;

	private ReentrantLock mLock = new ReentrantLock();

	private Condition mCondFinished = mLock.newCondition();

	private boolean mIsFinished = true;

	private boolean mDeckIsSelected = false;

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
		super.onCreate(savedInstanceState);
		mSelf = this;
		String deckPath = getIntent().getStringExtra("com.ichi2.anki.Ankidroid.DeckPath");
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
		super.onPause();
		waitForDeckLoaderThread();
	}

	private void populateDeckList(String location)
	{
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
			for (int i = 0; i < len; i++)
			{
				String absPath = fileList[i].getAbsolutePath();

				HashMap<String, String> data = new HashMap<String, String>();
				data.put("name", fileList[i].getName().replaceAll(".anki", ""));
				data.put("due", res.getString(R.string.deckpicker_loaddeck));
				data.put("new", "");
				data.put("mod", String.format("%f", Deck.getLastModified(absPath)));
				data.put("filepath", absPath);
				data.put("showProgress", "true");

				tree.add(data);
			}

			Thread thread = new Thread(this);
			thread.start();
		} else
		{
			//There is no sd card attached (wrap this code in a function called something like isSdMounted()
			//and place it in a utils class
			if(!Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState()))
			{
				AlertDialog.Builder builder = new AlertDialog.Builder(this);
				builder.setMessage("The SD card could not be read. Please, turn off USB storage.");
				builder.setPositiveButton("OK", null);
				builder.show();
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
			return (int) (Float.parseFloat(object2.get("mod")) - Float.parseFloat(object1.get("mod")));
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
			Log.i("anki", "Selected " + deckFilename);
			Intent intent = this.getIntent();
			intent.putExtra(Ankidroid.OPT_DB, deckFilename);
			setResult(RESULT_OK, intent);

			finish();
		}
	}

	private void waitForDeckLoaderThread()
	{
		mDeckIsSelected = true;
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
		int len = 0;
		if (mFileList != null)
			len = mFileList.length;

		if (len > 0 && mFileList != null)
		{
			mLock.lock();
			try
			{
				mIsFinished = false;
				for (int i = 0; i < len; i++)
				{

					// Don't load any more decks if one has already been
					// selected.
					if (mDeckIsSelected)
						break;

					String path = mFileList[i].getAbsolutePath();
					Deck deck;

					try
					{
						deck = Deck.openDeck(path);
					} catch (SQLException e)
					{
						Log.w("anki", "Could not open database " + path);
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
		}
	};

}
