package com.ichi2.anki;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.TreeSet;

import android.app.Activity;
import android.content.Intent;
import android.database.SQLException;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
//import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.SimpleAdapter;

/**
 * Allows the user to choose a deck from the filesystem.
 * 
 * @author Andrew Dubya
 *
 */
public class DeckPicker extends Activity {
	
	DeckPicker mSelf;
	SimpleAdapter mDeckListAdapter;
	ArrayList<HashMap<String, String>> mDeckList;
	ListView mDeckListView;
	
	AdapterView.OnItemClickListener mDeckSelHandler = new AdapterView.OnItemClickListener() {
		public void onItemClick(AdapterView parent, View v, int p, long id) {
			mSelf.handleDeckSelection(p);
		}
	};
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) throws SQLException {
        super.onCreate(savedInstanceState);
        mSelf = this;
        String deckPath = getIntent().getStringExtra("com.ichi2.anki.Ankidroid.DeckPath");
        setContentView(R.layout.main);
        
        mDeckList = new ArrayList<HashMap<String,String>>();
        mDeckListView = (ListView)findViewById(R.id.files);
		mDeckListAdapter = new SimpleAdapter(this,
        		mDeckList,
        		R.layout.deck_picker_list,
        		new String [] {"name","due","new"},
        		new int [] {R.id.DeckPickerName, R.id.DeckPickerDue, R.id.DeckPickerNew});
        mDeckListView.setOnItemClickListener(mDeckSelHandler);
		mDeckListView.setAdapter(mDeckListAdapter);
		
        populateDeckList(deckPath);
    }
    
    public void populateDeckList(String location)
    {
    	int len = 0;
    	File[] fileList;
    	TreeSet<HashMap<String,String>> tree = new TreeSet<HashMap<String,String>>(new HashMapCompare());
    	
    	File dir = new File(location);
    	fileList = dir.listFiles(new AnkiFilter());
    	
    	if (dir.exists() && dir.isDirectory() && fileList != null) {
	    	len = fileList.length;
    	}
    	
    	if (len > 0 && fileList != null) {
	    	for (int i=0; i<len; i++) {
	    		String absPath = fileList[i].getAbsolutePath();
	    		Deck deck;
	    		
	    		try {
	    			deck = Deck.openDeck(absPath);
	    		} catch (SQLException e) {
	    			Log.w("anki", "Could not open database " + absPath);
	    			continue;
	    		}
	    		
	    		int due = deck.failedSoonCount + deck.revCount;
	    		
		    	HashMap<String,String> data = new HashMap<String,String>();
		    	data.put("name", fileList[i].getName().replaceAll(".anki", ""));
		    	data.put("due", 
		    			String.valueOf(due)
		    			+ " of "
		    			+ String.valueOf(deck.cardCount) 
		    			+ " due");
		    	data.put("new", 
		    			String.valueOf(deck.newCountToday) 
		    			+ " new");
		    	data.put("filepath", absPath);
		    	data.put("mod", String.valueOf(deck.modified));
		    	
		    	deck.closeDeck();
		    	
		    	tree.add(data);
	    	}
    	}
    	else {
    		HashMap<String,String> data = new HashMap<String,String>();
	    	data.put("name", "No decks found.");
	    	data.put("cards", "");
	    	data.put("due", "");
	    	data.put("mod", "1");
	    	
	    	tree.add(data);
    	}
    
    	mDeckList.clear();
    	mDeckList.addAll(tree);
    	mDeckListView.clearChoices();
    	mDeckListAdapter.notifyDataSetChanged();
    }
    
    public static final class AnkiFilter implements FileFilter {

		//@Override
		public boolean accept(File pathname) {
			if (pathname.isFile() && pathname.getName().endsWith(".anki"))
				return true;
			return false;
		}
    	
    }
    
    public static final class HashMapCompare implements Comparator<HashMap<String,String>> {

		//@Override
		public int compare(HashMap<String, String> object1,
				HashMap<String, String> object2) {
			return (int) (Float.parseFloat(object2.get("mod")) - Float.parseFloat(object1.get("mod")));
		}
    	
    }
    
    public void handleDeckSelection(int id) {
    	HashMap<String,String> data = (HashMap<String,String>) mDeckListAdapter.getItem(id);

    	String deckFilename = data.get("filepath");
    	
    	if (deckFilename != null) {
    		Log.i("anki", "Selected " + deckFilename);
	    	Intent intent = this.getIntent();
			intent.putExtra(Ankidroid.OPT_DB, deckFilename);
			setResult(RESULT_OK, intent);
			
			finish();
    	}
    }
}
