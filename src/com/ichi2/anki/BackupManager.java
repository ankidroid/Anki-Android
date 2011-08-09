package com.ichi2.anki;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Date;

import com.tomgibara.android.veecheck.util.PrefSettings;

import android.content.SharedPreferences;
import android.os.StatFs;
import android.util.Log;

public class BackupManager {

	static String mBackupDirectoryPath;
	
	public final static int RETURN_BACKUP_CREATED = 0;
	public final static int RETURN_ERROR = 1;
	public final static int RETURN_TODAY_ALREADY_BACKUP_DONE = 2;
	public final static int RETURN_NOT_ENOUGH_SPACE = 3;

    /* Prevent class from being instantiated */
	private BackupManager() {
	}


	public static int backupDeck(String deckpath) {
        if (mBackupDirectoryPath == null) {
        	SharedPreferences prefs = PrefSettings.getSharedPrefs(AnkiDroidApp.getInstance().getBaseContext());
        	mBackupDirectoryPath = prefs.getString("deckPath", AnkiDroidApp.getStorageDirectory()) + "/backup";        	
        }

        File backupDirectory = new File(mBackupDirectoryPath);
        if (!backupDirectory.isDirectory()) {
        	backupDirectory.mkdirs();
        }

        File deckFile = new File(deckpath);
        Date value = Utils.genToday(Utils.utcOffset());
        String backupFilename = String.format(Utils.ENGLISH_LOCALE, deckFile.getName().replace(".anki", "") + "-%tF.anki", value);

        File backupFile = new File(mBackupDirectoryPath, backupFilename);
        if (backupFile.exists()) {
            Log.i(AnkiDroidApp.TAG, "No new backup of " + deckFile.getName() + " created. Already made one today");
            return RETURN_TODAY_ALREADY_BACKUP_DONE;
        }

        if (deckFile.getUsableSpace() < deckFile.length()) {
            Log.e(AnkiDroidApp.TAG, "Not enough space on sd card to backup " + deckFile.getName() + ".");
        	return RETURN_NOT_ENOUGH_SPACE;
        }

        try {
            InputStream stream = new FileInputStream(deckFile);
            Utils.writeToFile(stream, backupFile.getAbsolutePath());
            stream.close();
        } catch (IOException e) {
            Log.e(AnkiDroidApp.TAG, Log.getStackTraceString(e));
            Log.e(AnkiDroidApp.TAG, "Backup file " + deckFile.getName() + " - Copying of file failed.");
            return RETURN_ERROR;
        }
        return RETURN_BACKUP_CREATED;
	}
}
