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

//import com.ichi2.anki.DeckPicker.FileBrowser.FileEntry;
//import com.ichi2.anki.DeckPicker.FileBrowser.NotDirException;

/**
 * Allows the user to choose a deck from the filesystem.
 * 
 * @author Andrew Dubya
 *
 */
public class DeckPicker extends Activity {
	
	DeckPicker mSelf;
//	FileBrowser mBrowser;
//	ArrayAdapter<FileBrowser.FileEntry> mFileListAdapter;
//	ListView mFileList;
	SimpleAdapter mDeckListAdapter;
	ArrayList<HashMap<String, String>> mDeckList;
	ListView mDeckListView;
	
	AdapterView.OnItemClickListener mDeckSelHandler = new AdapterView.OnItemClickListener() {
		public void onItemClick(AdapterView parent, View v, int p, long id) {
			mSelf.handleDeckSelection(p);
		}
	};
	
//	AdapterView.OnItemClickListener mFileSelHandler = new AdapterView.OnItemClickListener() {
//		public void onItemClick(AdapterView parent, View v, int p, long id) {
//			mSelf.handleFileSelection(p);
//		}
//	};
	
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
        		new String [] {"name","cards","due"},
        		new int [] {R.id.DeckPickerName, R.id.DeckPickerCards, R.id.DeckPickerDue});
        mDeckListView.setOnItemClickListener(mDeckSelHandler);
		mDeckListView.setAdapter(mDeckListAdapter);
		
//        mFileList = (ListView)findViewById(R.id.files);        
//        mFileListAdapter = new ArrayAdapter<FileBrowser.FileEntry>(this, R.layout.main_fileentry);
//        mFileList.setOnItemClickListener(mFileSelHandler);
//        mFileList.setAdapter(mFileListAdapter);
        
        populateDeckList(deckPath);
//        populateDirList(deckPath);
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
		    	data.put("cards", 
		    			String.valueOf(due)
		    			+ " of "
		    			+ String.valueOf(deck.cardCount) 
		    			+ " due");
		    	data.put("due", 
		    			String.valueOf(deck.newCount) 
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

		@Override
		public boolean accept(File pathname) {
			if (pathname.isFile() && pathname.getName().endsWith(".anki"))
				return true;
			return false;
		}
    	
    }
    
    public static final class HashMapCompare implements Comparator<HashMap<String,String>> {

		@Override
		public int compare(HashMap<String, String> object1,
				HashMap<String, String> object2) {
			return (int) (Float.parseFloat(object2.get("mod")) - Float.parseFloat(object1.get("mod")));
		}
    	
    }
    
    public void handleDeckSelection(int id) {
    	HashMap<String,String> data = (HashMap<String,String>) mDeckListAdapter.getItem(id);
    	
    	String deckFilename = data.get("filepath");
    	
    	if (deckFilename != null) {
	    	Intent intent = this.getIntent();
			intent.putExtra(Ankidroid.OPT_DB, deckFilename);
			setResult(RESULT_OK, intent);
			finish();
    	}
    }
    
//    public void handleFileSelection(int id) {
//    	FileEntry ent = mFileListAdapter.getItem(id);
//    	if (ent.mIsDir) {
////    		try {
////    			mBrowser.moveTo(ent.mFilename);
////    			mFileListAdapter.clear();
////        		populateDirList();
////    		} catch (NotDirException e) {
////    		}
//    	} else if (ent.mIsFile) {
//    		//startFlashCardWithDb(mBrowser.mCurrent.getAbsolutePath() + "/" + ent.mFilename);
//    		String deckFilename = mBrowser.mCurrent.getAbsolutePath() + "/" + ent.mFilename;
//    		Intent intent = this.getIntent();
//    		//intent.putExtra(Intent.EXTRA_TEXT, deckFilename);
//    		intent.putExtra(Ankidroid.OPT_DB, deckFilename);
//    		setResult(RESULT_OK, intent);
//    		finish();
//    	}
//    }
    
//    public void startFlashCardWithDb(String dbName) {
//    	Intent flashcard = new Intent(this, FlashCard.class) ;
//    	flashcard.putExtra(FlashCard.OPT_DB, dbName);
//    	startActivity(flashcard);
//    }
    
//    public void populateDirList(String location){
//    	ArrayAdapter<FileEntry> fa = mFileListAdapter;
//    	fa.clear();
//    	try {
//    		if (mBrowser == null) {
//    			mBrowser = new FileBrowser(location);
//    		}
//    		else
//    			mBrowser.moveTo(location);
//    	} catch (NotDirException e) {
//    		FileEntry bad_ent = new FileEntry(false, false, "Error listing files");
//    		bad_ent.mIsInfo = true;
//    		fa.add(bad_ent);
//    	}
////    	FileEntry current;
////    	try {
////    		current = new FileEntry(false, false,
////    			"Current: " + mBrowser.mCurrent.getCanonicalPath());
////    	} catch (IOException e) {
////    		current = new FileEntry(false, false, "Current: Unknown?!");
////    	}
////    	fa.add(current);
////    	FileEntry[] list = mBrowser.mDirectories;
////    	for (int i=0; i<list.length; i++) {
////    		fa.add(list[i]);
////    	}
//    	
//    	FileEntry[] list = mBrowser.mFiles;
//    	
//    	for (int i=0; i< list.length; i++) {
//    		// Show only Anki deck files.
//    		if (list[i].mFilename.endsWith(".anki")) {
//    			fa.add(list[i]);
//    		}
//    	}
//    	
//    	if (fa.getCount() == 0)
//    		fa.add(new FileEntry(false, false, "No decks found. Make sure you have the correct path in the preferences."));
//    	
//    	mFileList.clearChoices();
//    }
    
//    public static final class FileBrowser {
//    	public File mCurrent;
//    	//public FileEntry[] mDirectories;
//    	public FileEntry[] mFiles;
//    	
//    	public static final class FileEntry {
//    		public boolean mIsDir, mIsFile, mIsInfo;
//    		public String mFilename;
//    		public FileEntry(File file) {
//    			mIsDir = file.isDirectory();
//    			mIsFile = file.isFile();
//    			mFilename = file.getName();
//    		}
//    		public FileEntry(boolean isDir, boolean isFile, String filename) {
//    			mIsDir = isDir;
//    			mIsFile = isFile;
//    			mFilename = filename;
//    		}
//    		
//    		public String toString() {
//    			if (mIsDir) {
//    				return "(Dir) " + mFilename;
//    			}
//    			return mFilename.replace(".anki", "");
//    		}
//    	}
//    	
//    	public static final class NotDirException extends Exception {
//    	};
//    	
//    	public FileBrowser(String location) throws NotDirException {
//    		if (location == null) {
//    			if (new File("/sdcard").exists()) {
//    				mCurrent = new File("/sdcard");
//    			}
//    			else {
//    				mCurrent = new File("/");
//    			}
//    		} else {
//    			if (new File(location).exists()) {
//    				mCurrent = new File(location);
//    			}
//    			else {
//    				mCurrent = new File("/");
//    			}
//    		}
//    		initEntries();
//    	}
//    	    	
//    	public void moveTo(String new_location) throws NotDirException {
//    		mCurrent = new File(mCurrent, new_location);
//    		initEntries();
//    	}
//    	
//    	public void initEntries() throws NotDirException {
//    		if (!mCurrent.isDirectory() || mCurrent == null) {
//    			throw new NotDirException();
//    		}
//    		
//    		File[] filelist = mCurrent.listFiles();
//    		int len = filelist.length, dirs=0;
//    		
//    		for (int i=0; i<len; i++) {
//    			if (filelist[i].isDirectory()) {
//    				dirs++;
//    			}
//    		}
//    		//mDirectories = new FileEntry[dirs+1];
//    		mFiles = new FileEntry[len-dirs];
//    		
//    		int diridx = 0, fileidx = 0;
//    		
//    		//mDirectories[diridx++] = new FileEntry(true, false, "..");
//    		for (int i=0; i<len; i++) {
//    			FileEntry new_ent = new FileEntry(filelist[i]);
//    			if (filelist[i].isDirectory()) {
//    				//mDirectories[diridx++] = new_ent;
//    			} else {
//    				mFiles[fileidx++] = new_ent;
//    			}
//    		}
//    	}   	
//    }
}