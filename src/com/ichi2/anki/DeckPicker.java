package com.ichi2.anki;

import java.io.File;
import java.io.IOException;

import android.app.Activity;
import android.content.Intent;
import android.database.SQLException;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import com.ichi2.anki.DeckPicker.FileBrowser.FileEntry;
import com.ichi2.anki.DeckPicker.FileBrowser.NotDirException;

/**
 * Allows the user to choose a deck from the filesystem.
 * 
 * @author Andrew Dubya
 *
 */
public class DeckPicker extends Activity {
	
	DeckPicker mSelf;
	FileBrowser mBrowser;
	ArrayAdapter<FileBrowser.FileEntry> mFileListAdapter;
	ListView mFileList;
		
	AdapterView.OnItemClickListener mFileSelHandler = new AdapterView.OnItemClickListener() {
		public void onItemClick(AdapterView parent, View v, int p, long id) {
			mSelf.handleFileSelection(p);
		}
	};
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) throws SQLException {
        super.onCreate(savedInstanceState);
        mSelf = this;
        setContentView(R.layout.main);
        
        mFileList = (ListView)findViewById(R.id.files);        
        mFileListAdapter = new ArrayAdapter<FileBrowser.FileEntry>(this, R.layout.main_fileentry);
        mFileList.setOnItemClickListener(mFileSelHandler);
        mFileList.setAdapter(mFileListAdapter);
        
        populateDirList();
    }
    
    public void handleFileSelection(int id) {
    	FileEntry ent = mFileListAdapter.getItem(id);
    	if (ent.mIsDir) {
    		try {
    			mBrowser.moveTo(ent.mFilename);
    			mFileListAdapter.clear();
        		populateDirList();
    		} catch (NotDirException e) {
    		}
    	} else if (ent.mIsFile) {
    		//startFlashCardWithDb(mBrowser.mCurrent.getAbsolutePath() + "/" + ent.mFilename);
    		String deckFilename = mBrowser.mCurrent.getAbsolutePath() + "/" + ent.mFilename;
    		Intent intent = this.getIntent();
    		//intent.putExtra(Intent.EXTRA_TEXT, deckFilename);
    		intent.putExtra(Ankidroid.OPT_DB, deckFilename);
    		setResult(RESULT_OK, intent);
    		finish();
    	}
    }
    
//    public void startFlashCardWithDb(String dbName) {
//    	Intent flashcard = new Intent(this, FlashCard.class) ;
//    	flashcard.putExtra(FlashCard.OPT_DB, dbName);
//    	startActivity(flashcard);
//    }
    
    public void populateDirList(){
    	ArrayAdapter<FileEntry> fa = mFileListAdapter;
    	fa.clear();
    	try {
    		if (mBrowser == null) {
    			mBrowser = new FileBrowser(null);
    		}
    	} catch (NotDirException e) {
    		FileEntry bad_ent = new FileEntry(false, false, "Error listing files");
    		bad_ent.mIsInfo = true;
    		fa.add(bad_ent);
    	}
    	FileEntry current;
    	try {
    		current = new FileEntry(false, false,
    			"Current: " + mBrowser.mCurrent.getCanonicalPath());
    	} catch (IOException e) {
    		current = new FileEntry(false, false, "Current: Unknown?!");
    	}
    	fa.add(current);
    	FileEntry[] list = mBrowser.mDirectories;
    	for (int i=0; i<list.length; i++) {
    		fa.add(list[i]);
    	}
    	
    	list = mBrowser.mFiles;
    	for (int i=0; i< list.length; i++) {
    		// Show only Anki deck files.
    		if (list[i].mFilename.endsWith(".anki")) {
    			fa.add(list[i]);
    		}
    	}
    	mFileList.clearChoices();
    }
    
    public static final class FileBrowser {
    	public File mCurrent;
    	public FileEntry[] mDirectories;
    	public FileEntry[] mFiles;
    	
    	public static final class FileEntry {
    		public boolean mIsDir, mIsFile, mIsInfo;
    		public String mFilename;
    		public FileEntry(File file) {
    			mIsDir = file.isDirectory();
    			mIsFile = file.isFile();
    			mFilename = file.getName();
    		}
    		public FileEntry(boolean isDir, boolean isFile, String filename) {
    			mIsDir = isDir;
    			mIsFile = isFile;
    			mFilename = filename;
    		}
    		
    		public String toString() {
    			if (mIsDir) {
    				return "(Dir) " + mFilename;
    			}
    			return mFilename;
    		}
    	}
    	
    	public static final class NotDirException extends Exception {
    	};
    	
    	public FileBrowser(String location) throws NotDirException {
    		if (location == null) {
    			if ( ! new File("/sdcard").exists()) {
    				mCurrent = new File("/sdcard");
    			}
    			else {
    				mCurrent = new File("/");
    			}
    		} else {
    			mCurrent = new File(location);
    		}
    		initEntries();
    	}
    	    	
    	public void moveTo(String new_location) throws NotDirException {
    		mCurrent = new File(mCurrent, new_location);
    		initEntries();
    	}
    	
    	public void initEntries() throws NotDirException {
    		if (!mCurrent.isDirectory() || mCurrent == null) {
    			throw new NotDirException();
    		}
    		
    		File[] filelist = mCurrent.listFiles();
    		int len = filelist.length, dirs=0;
    		
    		for (int i=0; i<len; i++) {
    			if (filelist[i].isDirectory()) {
    				dirs++;
    			}
    		}
    		mDirectories = new FileEntry[dirs+1];
    		mFiles = new FileEntry[len-dirs];
    		
    		int diridx = 0, fileidx = 0;
    		
    		mDirectories[diridx++] = new FileEntry(true, false, "..");
    		for (int i=0; i<len; i++) {
    			FileEntry new_ent = new FileEntry(filelist[i]);
    			if (filelist[i].isDirectory()) {
    				mDirectories[diridx++] = new_ent;
    			} else {
    				mFiles[fileidx++] = new_ent;
    			}
    		}
    	}   	
    }
}