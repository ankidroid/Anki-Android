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

package com.ichi2.anki;

import android.content.SharedPreferences;
import android.os.StatFs;
import android.util.Log;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

public class BackupManager {

    public static final int MIN_FREE_SPACE = 10;

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

    /** Number of day after which a backup is done on first non-studyoptions-opening (for safety reasons) */
    public static final int SAFETY_BACKUP_THRESHOLD = 3;

    /** Number of hours after which a backup new backup is created */
    public static final int BACKUP_INTERVAL = 5;


    /* Prevent class from being instantiated */
    private BackupManager() {
    }


    public static boolean isActivated() {
        return mUseBackups;
    }


    private static File getBackupDirectory() {
        SharedPreferences prefs = AnkiDroidApp.getSharedPrefs(AnkiDroidApp.getInstance().getBaseContext());
        File directory = new File(prefs.getString("deckPath", AnkiDroidApp.getCurrentAnkiDroidDirectory()) + BACKUP_SUFFIX);
        if (!directory.isDirectory()) {
            directory.mkdirs();
        }
        return directory;
    }


    private static File getBrokenDirectory() {
        SharedPreferences prefs = AnkiDroidApp.getSharedPrefs(AnkiDroidApp.getInstance().getBaseContext());
        File directory = new File(prefs.getString("deckPath", AnkiDroidApp.getCurrentAnkiDroidDirectory()) + BROKEN_DECKS_SUFFIX);
        if (!directory.isDirectory()) {
            directory.mkdirs();
        }
        return directory;
    }


    public static boolean safetyBackupNeeded(String path) {
        return safetyBackupNeeded(path, SAFETY_BACKUP_THRESHOLD);
    }


    /**
     * If collection has not been opened for a long time, we perform a backup here because Android deleted sometimes
     * corrupted decks
     */
    public static boolean safetyBackupNeeded(String path, int days) {
        if (!AnkiDroidApp.getSharedPrefs(AnkiDroidApp.getInstance().getBaseContext()).getBoolean("useBackup", true)) {
            return false;
        }

        File collectionFile = new File(path);
        if (!collectionFile.exists()) {
            return false;
        }
        File[] deckBackups = getBackups(collectionFile);
        int len = deckBackups.length;
        if (len == 0) {
            // no backup available
            return true;
        } else if (deckBackups[len - 1].lastModified() == collectionFile.lastModified()) {
            return false;
        }

        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd-HH-mm");
        Calendar cal = new GregorianCalendar();
        cal.setTimeInMillis(System.currentTimeMillis());

        Date lastBackupDate = null;
        while (lastBackupDate == null && len > 0) {
            try {
                len--;
                lastBackupDate = df.parse(deckBackups[len].getName().replaceAll(
                        "^.*-(\\d{4}-\\d{2}-\\d{2}-\\d{2}-\\d{2}).anki2$", "$1"));
            } catch (ParseException e) {
                lastBackupDate = null;
            }
        }
        if (lastBackupDate == null) {
            return true;
        } else if (lastBackupDate.getTime() + days * 24 * 3600000 < Utils.intNow(1000)) {
            return true;
        } else {
            return false;
        }
    }


    /** Restores the collection from backup if Android deleted it */
    public static void restoreCollectionIfMissing(String path) {
        if (mUseBackups && !(new File(path)).exists()) {
            Log.e(AnkiDroidApp.TAG, "BackupManager: Collection " + path + " has been deleted by Android. Restoring it:");
            File[] fl = BackupManager.getBackups(new File(path));
            if (fl.length > 0) {
                BackupManager.restoreBackup(path, fl[fl.length - 1].getAbsolutePath());
                Log.e(AnkiDroidApp.TAG, "BackupManager: Collection " + path + " successfully restored");
            } else {
                Log.e(AnkiDroidApp.TAG, "BackupManager: Collection " + path + " could not be restored");
            }
        }
    }


    public static void performBackup(String path) {
        performBackup(path, BACKUP_INTERVAL, false);
    }


    public static void performBackup(String path, boolean force) {
        performBackup(path, BACKUP_INTERVAL, force);
    }


    public static void performBackup(String path, int interval) {
        performBackup(path, interval, false);
    }


    public static void performBackup(String path, int interval, boolean force) {
        SharedPreferences prefs = AnkiDroidApp.getSharedPrefs(AnkiDroidApp.getInstance().getBaseContext());
        if (!prefs.getBoolean("useBackup", true) && !force) {
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
                lastBackupDate = df.parse(deckBackups[len].getName().replaceAll(
                        "^.*-(\\d{4}-\\d{2}-\\d{2}-\\d{2}-\\d{2}).anki2$", "$1"));
            } catch (ParseException e) {
                lastBackupDate = null;
            }
        }
        if (lastBackupDate != null && lastBackupDate.getTime() + interval * 3600000 > Utils.intNow(1000) && !force) {
            Log.i(AnkiDroidApp.TAG, "performBackup: No backup created. Last backup younger than 5 hours");
            return;
        }

        String backupFilename;
        try {
            backupFilename = String.format(Utils.ENGLISH_LOCALE, collectionFile.getName().replace(".anki2", "")
                    + "-%s.anki2", df.format(cal.getTime()));
        } catch (UnknownFormatConversionException e) {
            Log.e(AnkiDroidApp.TAG, "performBackup: error on creating backup filename: " + e);
            return;
        }

        File backupFile = new File(getBackupDirectory().getPath(), backupFilename);
        if (backupFile.exists()) {
            Log.i(AnkiDroidApp.TAG, "performBackup: No new backup created. File already exists");
            return;
        }

        if (getFreeDiscSpace(collectionFile) < collectionFile.length() + (MIN_FREE_SPACE * 1024 * 1024)) {
            Log.e(AnkiDroidApp.TAG, "performBackup: Not enough space on sd card to backup.");
            prefs.edit().putBoolean("noSpaceLeft", true).commit();
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

        // delete old backups
        deleteDeckBackups(path, prefs.getInt("backupMax", 3));
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


    public static int restoreBackup(String path, String backupPath) {
        // rename old file and move it to subdirectory
        if (!(new File(path)).exists() || !moveDatabaseToBrokenFolder(path, false)) {
            return RETURN_ERROR;
        }

        // copy backup to new position and rename it
        File backupFile = new File(backupPath);
        File colFile = new File(path);
        if (getFreeDiscSpace(colFile) < colFile.length() + (MIN_FREE_SPACE * 1024 * 1024)) {
            Log.e(AnkiDroidApp.TAG, "Not enough space on sd card to restore " + colFile.getName() + ".");
            return RETURN_NOT_ENOUGH_SPACE;
        }
        try {
            InputStream stream = new FileInputStream(backupFile);
            Utils.writeToFile(stream, colFile.getAbsolutePath());
            stream.close();

            // set timestamp of file in order to avoid creating a new backup unless its changed
            colFile.setLastModified(backupFile.lastModified());
        } catch (IOException e) {
            Log.e(AnkiDroidApp.TAG, Log.getStackTraceString(e));
            Log.e(AnkiDroidApp.TAG, "Restore of file " + colFile.getName() + " failed.");
            return RETURN_ERROR;
        }
        return RETURN_DECK_RESTORED;
    }


    public static boolean repairDeck(String deckPath) {
        File deckFile = new File(deckPath);
        Collection col = AnkiDroidApp.getCol();
        if (col != null) {
            col.close();
        }
        AnkiDatabaseManager.closeDatabase(deckPath);

        // repair file
        String execString = "sqlite3 " + deckPath + " .dump | sqlite3 " + deckPath + ".tmp";
        Log.i(AnkiDroidApp.TAG, "repairDeck - Execute: " + execString);
        try {
            String[] cmd = { "/system/bin/sh", "-c", execString };
            Process process = Runtime.getRuntime().exec(cmd);
            process.waitFor();

            if (!new File(deckPath + ".tmp").exists()) {
            	return false;
            }

            if (!moveDatabaseToBrokenFolder(deckPath, false)) {
                return false;
            }
            Log.i(AnkiDroidApp.TAG, "repairDeck - moved corrupt file to broken folder");
            File repairedFile = new File(deckPath + ".tmp");
            if (!repairedFile.renameTo(deckFile)) {
                return false;
            }
            return true;
        } catch (IOException e) {
            Log.e("AnkiDroidApp.TAG", "repairCollection - error: " + e);
        } catch (InterruptedException e) {
            Log.e("AnkiDroidApp.TAG", "repairCollection - error: " + e);
        }
        return false;
    }


    public static boolean moveDatabaseToBrokenFolder(String colPath, boolean moveConnectedFilesToo) {
        File colFile = new File(colPath);

        // move file
        Date value = Utils.genToday(Utils.utcOffset());
        String movedFilename = String.format(Utils.ENGLISH_LOCALE, colFile.getName().replace(".anki2", "")
                + "-corrupt-%tF.anki2", value);
        File movedFile = new File(getBrokenDirectory().getPath(), movedFilename);
        int i = 1;
        while (movedFile.exists()) {
            movedFile = new File(getBrokenDirectory().getPath(), movedFilename.replace(".anki2",
                    "-" + Integer.toString(i) + ".anki2"));
            i++;
        }
        movedFilename = movedFile.getName();
        if (!colFile.renameTo(movedFile)) {
            return false;
        }

        if (moveConnectedFilesToo) {
            // move all connected files (like journals, directories...) too
            String deckName = colFile.getName();
            File directory = new File(colFile.getParent());
            for (File f : directory.listFiles()) {
                if (f.getName().startsWith(deckName)) {
                    if (!f.renameTo(new File(getBrokenDirectory().getPath(), f.getName().replace(deckName,
                            movedFilename)))) {
                        return false;
                    }
                }
            }
        }
        return true;
    }


    public static File[] getBackups(File colFile) {
        File[] files = getBackupDirectory().listFiles();
        if (files == null) {
        	files = new File[0];
        }
        ArrayList<File> deckBackups = new ArrayList<File>();
        for (File aktFile : files) {
            if (aktFile.getName().replaceAll("^(.*)-\\d{4}-\\d{2}-\\d{2}-\\d{2}-\\d{2}.anki2$", "$1.anki2")
                    .equals(colFile.getName())) {
                deckBackups.add(aktFile);
            }
        }
        Collections.sort(deckBackups);
        File[] fileList = new File[deckBackups.size()];
        deckBackups.toArray(fileList);
        return fileList;
    }


    public static boolean deleteDeckBackups(String colFile, int keepNumber) {
        return deleteDeckBackups(getBackups(new File(colFile)), keepNumber);
    }


    public static boolean deleteDeckBackups(File colFile, int keepNumber) {
        return deleteDeckBackups(getBackups(colFile), keepNumber);
    }


    public static boolean deleteDeckBackups(File[] backups, int keepNumber) {
        if (backups == null) {
            return false;
        }
        for (int i = 0; i < backups.length - keepNumber; i++) {
            backups[i].delete();
        }
        return true;
    }


    // public static boolean deleteAllBackups() {
    // return removeDir(getBackupDirectory());
    // }
    //

    public static boolean removeDir(File dir) {
        if (dir.isDirectory()) {
            File[] files = dir.listFiles();
            for (File aktFile : files) {
                removeDir(aktFile);
            }
        }
        return dir.delete();
    }
}
