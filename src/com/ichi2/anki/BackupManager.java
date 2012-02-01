/***************************************************************************************
 * Copyright (c) 2011 Norbert Nagold <norbert.nagold@gmail.com>                         *
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

package com.ichi2.anki;import com.ichi2.anki2.R;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.TimeZone;
import java.util.UnknownFormatConversionException;

import com.ichi2.libanki.Utils;
import com.tomgibara.android.veecheck.util.PrefSettings;

import android.content.SharedPreferences;
import android.os.StatFs;
import android.util.Log;

public class BackupManager {

	static String mBackupDirectoryPath;
	static String mBrokenDirectoryPath;
	static int mMaxBackups;
	static File mLastCreatedBackup;
	static File[] mLastDeckBackups;

	private static final int MIN_FREE_SPACE = 10;

	public final static int RETURN_BACKUP_CREATED = 0;
	public final static int RETURN_ERROR = 1;
	public final static int RETURN_TODAY_ALREADY_BACKUP_DONE = 2;
	public final static int RETURN_NOT_ENOUGH_SPACE = 3;
	public final static int RETURN_DECK_NOT_CHANGED = 4;
	public final static int RETURN_DECK_RESTORED = 5;
	public final static int RETURN_NULL = 6;
	public final static int RETURN_LOW_SYSTEM_SPACE = 7;
	public final static int RETURN_BACKUP_NEEDED = 8;

	public final static String BACKUP_SUFFIX = "/backup";
	public final static String BROKEN_DECKS_SUFFIX = "/broken";

	private static boolean mUseBackups = true;

 	/** Number of day, after which a backup is done on first non-studyoptions-opening (for safety reasons) */
	public static final int SAFETY_BACKUP_THRESHOLD = 3;

	
    /* Prevent class from being instantiated */
	private BackupManager() {
	}


	public static boolean isActivated() {
		return mUseBackups;
	}


	private static File getBackupDirectory() {
        if (mBackupDirectoryPath == null) {
        	SharedPreferences prefs = PrefSettings.getSharedPrefs(AnkiDroidApp.getInstance().getBaseContext());
    		mBackupDirectoryPath = prefs.getString("deckPath", AnkiDroidApp.getStorageDirectory()) + BACKUP_SUFFIX;
        	mMaxBackups = prefs.getInt("backupMax", 3);
        }
        File directory = new File(mBackupDirectoryPath);
        if (!directory.isDirectory()) {
        	directory.mkdirs();
        }
        return directory;
	}


	private static File getBrokenDirectory() {
        if (mBrokenDirectoryPath == null) {
        	SharedPreferences prefs = PrefSettings.getSharedPrefs(AnkiDroidApp.getInstance().getBaseContext());
        	mBrokenDirectoryPath = prefs.getString("deckPath", AnkiDroidApp.getStorageDirectory()) + BROKEN_DECKS_SUFFIX;
        }
        File directory = new File(mBrokenDirectoryPath);
        if (!directory.isDirectory()) {
        	directory.mkdirs();
        }
        return directory;
	}


	/** If deck has not been opened for a long time, we perform a backup here because Android deleted sometimes corrupted decks */
	public static boolean safetyBackupNeeded(String deckpath, int days) {
		return false;
//		if (mDeckPickerDecks == null) {
//			initBackup();
//		}
//		if (!mUseBackups || mDeckPickerDecks.contains(deckpath)) {
//			return false;
//		}
//	        File[] deckBackups = getDeckBackups(new File(deckpath));
//	        int len = deckBackups.length;
//		if (len == 0) {
//			// no backup available
//			return true;
//		}
//		String backupDateString = deckBackups[len - 1].getName().replaceAll("^.*-(\\d{4}-\\d{2}-\\d{2}).anki$", "$1");
//		Date backupDate;
//		try {
//			backupDate = new SimpleDateFormat("yyyy-MM-dd").parse(backupDateString);
//		} catch (ParseException e) {
//			Log.e(AnkiDroidApp.TAG, "BackupManager - safetyBackupNeeded - Error on parsing backups: " + e);
//			return true;
//		}
//        Date target = Utils.genToday(Utils.utcOffset() + (days * 86400));
//		if (backupDate.before(target)) {
//			return true;
//		} else {
//			mDeckPickerDecks.add(deckpath);
//			return false;
//		}
	}


	/** Restores the current deck from backup if Android deleted it */
	public static void restoreDeckIfMissing(String deckpath) {
//		if (mUseBackups && !(new File(deckpath)).exists()) {
//			Log.e(AnkiDroidApp.TAG, "BackupManager: Deck " + deckpath + " has been deleted by Android. Restoring it:");
//			File[] fl = BackupManager.getDeckBackups(new File(deckpath));
//			if (fl.length > 0) {
//				Log.e(AnkiDroidApp.TAG, "BackupManager: Deck " + deckpath + " successfully restored");
//				BackupManager.restoreDeckBackup(deckpath, fl[fl.length - 1].getAbsolutePath());					
//			} else {
//				Log.e(AnkiDroidApp.TAG, "BackupManager: Deck " + deckpath + " could not be restored");
//			}
//		}
	}


	public static void performBackup(String path) {
		if (!PrefSettings.getSharedPrefs(AnkiDroidApp.getInstance().getBaseContext()).getBoolean("useBackup", true)) {
			return;
		}
        File collectionFile = new File(path);
        File[] deckBackups = getBackups(collectionFile);
        int len = deckBackups.length;
        if (len > 0 && deckBackups[len - 1].lastModified() == collectionFile.lastModified()) {
        	Log.i(AnkiDroidApp.TAG, "performBackup: No backup necessary due to no collection changes");
        	return;
        }

        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd-HH-mm");
        Calendar cal = new GregorianCalendar();
        cal.setTimeInMillis(System.currentTimeMillis());

        Date lastBackupDate = null;
        while (lastBackupDate == null && len > 0) {
        	try {
        		len--;
        		lastBackupDate = df.parse(deckBackups[len].getName().replaceAll("^.*-(\\d{4}-\\d{2}-\\d{2}-\\d{2}-\\d{2}).anki2$", "$1"));
        	} catch (ParseException e) {
        		lastBackupDate = null;
        	}
        }
        if (lastBackupDate != null && lastBackupDate.getTime() + 5 * 3600000 > Utils.intNow(1000)) {
        	Log.i(AnkiDroidApp.TAG, "performBackup: No backup created. Last backup younger than 5 hours");
        	return;
        }

        String backupFilename;
        try {
        	backupFilename = String.format(Utils.ENGLISH_LOCALE, collectionFile.getName().replace(".anki2", "") + "-%s.anki2", df.format(cal.getTime()));
        } catch (UnknownFormatConversionException e) {
        	Log.e(AnkiDroidApp.TAG, "performBackup: error on creating backup filename: " + e);
        	return;
        }

        File backupFile = new File(getBackupDirectory().getPath(), backupFilename);
        if (backupFile.exists()) {
            Log.i(AnkiDroidApp.TAG, "performBackup: No new backup created. Already made one today");
            return;
        }

        if (getFreeDiscSpace(collectionFile) < collectionFile.length() + (MIN_FREE_SPACE * 1024 * 1024)) {
            Log.e(AnkiDroidApp.TAG, "performBackup: Not enough space on sd card to backup.");
        	return;
        }

        try {
            InputStream stream = new FileInputStream(collectionFile);
            Utils.writeToFile(stream, backupFile.getAbsolutePath());
            stream.close();

            // set timestamp of file in order to avoid creating a new backup unless its changed
            backupFile.setLastModified(collectionFile.lastModified());
        } catch (IOException e) {
            Log.e(AnkiDroidApp.TAG, Log.getStackTraceString(e));
            Log.e(AnkiDroidApp.TAG, "performBackup: Copying of file failed.");
            return;
        }
	}


	public static boolean enoughDiscSpace(String path) {
		if (getFreeDiscSpace(path) >= (MIN_FREE_SPACE * 1024 * 1024)) {
			return true;
		} else {
			return false;
		}
	}


	private static long getFreeDiscSpace(String path) {
		return getFreeDiscSpace(new File(path));
	}
	private static long getFreeDiscSpace(File file) {
		try {
			StatFs stat = new StatFs(file.getParentFile().getPath());
	    	long blocks = stat.getAvailableBlocks();
	    	long blocksize = stat.getBlockSize();
	    	return blocks * blocksize;
		} catch (IllegalArgumentException e) {
			Log.e(AnkiDroidApp.TAG, "Free space could not be retrieved: " + e);
			return MIN_FREE_SPACE * 1024 * 1024;
		}	
	}


	public static boolean cleanUpAfterBackupCreation(boolean deckLoaded) {
		if (deckLoaded) {
			return deleteDeckBackups(mLastDeckBackups, mMaxBackups - 1);
		} else if (mLastCreatedBackup != null) {
			return mLastCreatedBackup.delete();
		}
		return false;
	}


	public static int restoreDeckBackup(String deckpath, String backupPath) {
        // rename old file and move it to subdirectory
    	if ((new File(deckpath)).exists() && !moveDeckToBrokenFolder(deckpath)) {
    		return RETURN_ERROR;
    	}

    	// copy backup to new position and rename it
    	File backupFile = new File(backupPath);
    	File deckFile = new File(deckpath);
        if (getFreeDiscSpace(deckFile) < deckFile.length() + (MIN_FREE_SPACE * 1024 * 1024)) {
            Log.e(AnkiDroidApp.TAG, "Not enough space on sd card to restore " + deckFile.getName() + ".");
        	return RETURN_NOT_ENOUGH_SPACE;
        }
        try {
            InputStream stream = new FileInputStream(backupFile);
            Utils.writeToFile(stream, deckFile.getAbsolutePath());
            stream.close();

            // set timestamp of file in order to avoid creating a new backup unless its changed
            deckFile.setLastModified(backupFile.lastModified());
        } catch (IOException e) {
            Log.e(AnkiDroidApp.TAG, Log.getStackTraceString(e));
            Log.e(AnkiDroidApp.TAG, "Restore of file " + deckFile.getName() + " failed.");
            return RETURN_ERROR;
        }
		return RETURN_DECK_RESTORED;
	}


	public static boolean repairDeck(String deckPath) {
		File deckFile = new File(deckPath);
		AnkiDatabaseManager.closeDatabase(deckPath);

    	// repair file
    	String execString = "sqlite3 " + deckPath + " .dump | sqlite3 " + deckPath + ".tmp";
    	Log.i(AnkiDroidApp.TAG, "repairDeck - Execute: " + execString);
    	try {
    		String[] cmd = {"/system/bin/sh", "-c", execString };
    	    Process process = Runtime.getRuntime().exec(cmd);
    	    process.waitFor();

    		// move deck to broken folder
    		String brokenDirectory = getBrokenDirectory().getPath();
    		Date value = Utils.genToday(Utils.utcOffset());
            String movedFilename = String.format(Utils.ENGLISH_LOCALE, deckFile.getName().replace(".anki", "") + "-corrupt-%tF.anki", value);
            File movedFile = new File(brokenDirectory, movedFilename);
            int i = 1;
            while (movedFile.exists()) {
            	movedFile = new File(brokenDirectory, movedFilename.replace(".anki", "-" + Integer.toString(i) + ".anki"));
            	i++;
            }
            movedFilename = movedFile.getName();
        	if (!deckFile.renameTo(movedFile)) {
        		return false;
        	}
        	Log.i(AnkiDroidApp.TAG, "repairDeck - moved corrupt file to " + movedFile.getAbsolutePath());
        	File repairedFile = new File(deckPath + ".tmp");
        	if (!repairedFile.renameTo(deckFile)) {
        		return false;
        	}
        	return true;
    	} catch (IOException e) {
    		Log.e("AnkiDroidApp.TAG", "repairDeck - error: " + e);
    	} catch (InterruptedException e) {
    		Log.e("AnkiDroidApp.TAG", "repairDeck - error: " + e);
    	}
    	return false;
	}


	public static boolean moveDeckToBrokenFolder(String deckPath) {
		File deckFile = new File(deckPath);
		AnkiDatabaseManager.closeDatabase(deckPath);
        Date value = Utils.genToday(Utils.utcOffset());
        String movedFilename = String.format(Utils.ENGLISH_LOCALE, deckFile.getName().replace(".anki", "") + "-corrupt-%tF.anki", value);
        File movedFile = new File(getBrokenDirectory().getPath(), movedFilename);
        int i = 1;
        while (movedFile.exists()) {
        	movedFile = new File(getBrokenDirectory().getPath(), movedFilename.replace(".anki", "-" + Integer.toString(i) + ".anki"));
        	i++;
        }
        movedFilename = movedFile.getName();
    	if (!deckFile.renameTo(movedFile)) {
    		return false;
    	}
    	
    	// move all connected files (like journals, directories...) too
    	String deckName = deckFile.getName();
    	File directory = new File(deckFile.getParent());
    	for (File f : directory.listFiles()) {
    		if (f.getName().startsWith(deckName)) {
    			if (!f.renameTo(new File(getBrokenDirectory().getPath(), f.getName().replace(deckName, movedFilename)))) {
    				return false;
    			}
    		}
    	}
    	return true;
	}


	public static File[] getBackups(File colFile) {
		File[] files = getBackupDirectory().listFiles();
		ArrayList<File> deckBackups = new ArrayList<File>();
		for (File aktFile : files){
			if (aktFile.getName().replaceAll("^(.*)-\\d{4}-\\d{2}-\\d{2}-\\d{2}-\\d{2}.anki2$", "$1.anki2").equals(colFile.getName())) {
				deckBackups.add(aktFile);
			}
		}
		Collections.sort(deckBackups);
		File[] fileList = new File[deckBackups.size()];
		deckBackups.toArray(fileList);
		return fileList;
	}


	public static boolean removeDeck(File deckFile) {
    	String deckName = deckFile.getName();
    	File directory = new File(deckFile.getParent());
    	for (File f : directory.listFiles()) {
    		if (f.getName().startsWith(deckName)) {
    			if (!removeDir(f)) {
    				return false;
    			}
    		}
    	}
    	return true;
	}


	public static boolean deleteDeckBackups(String deckpath, int keepNumber) {
		return deleteDeckBackups(getBackups(new File(deckpath)), keepNumber);
	}
	public static boolean deleteDeckBackups(File deckFile, int keepNumber) {
		return deleteDeckBackups(getBackups(deckFile), keepNumber);
	}
	public static boolean deleteDeckBackups(File[] deckBackups, int keepNumber) {
    	if (deckBackups == null) {
    		return false;
    	}
		for (int i = 0; i < deckBackups.length - keepNumber; i++) {
    		deckBackups[i].delete();
    	}
		return true;
	}


	public static boolean deleteAllBackups() {
		return removeDir(getBackupDirectory());
	}


	public static boolean removeDir(File dir){
		if (dir.isDirectory()){
			File[] files = dir.listFiles();
			for (File aktFile: files){
				removeDir(aktFile);
			}
		}
		return dir.delete();
	}
}
