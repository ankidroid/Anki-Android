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


import com.ichi2.compat.CompatHelper;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Utils;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.UnknownFormatConversionException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import timber.log.Timber;

public class BackupManager {

    private static final int MIN_FREE_SPACE = 10;
    private static final int MIN_BACKUP_COL_SIZE = 10000; // threshold in bytes to backup a col file

    private final static String BACKUP_SUFFIX = "backup";
    public final static String BROKEN_DECKS_SUFFIX = "broken";


    /** Number of hours after which a backup new backup is created */
    private static final int BACKUP_INTERVAL = 5;


    /* Prevent class from being instantiated */
    private BackupManager() {
        // do nothing
    }


    public static boolean isActivated() {
        return true;
    }


    private static File getBackupDirectory(File ankidroidDir) {
        File directory = new File(ankidroidDir, BACKUP_SUFFIX);
        if (!directory.isDirectory() && !directory.mkdirs()) {
            Timber.w("getBackupDirectory() mkdirs on %s failed", ankidroidDir);
        }
        return directory;
    }


    private static File getBrokenDirectory(File ankidroidDir) {
        File directory = new File(ankidroidDir, BROKEN_DECKS_SUFFIX);
        if (!directory.isDirectory() && !directory.mkdirs()) {
            Timber.w("getBrokenDirectory() mkdirs on %s failed", ankidroidDir);
        }
        return directory;
    }


    public static boolean performBackupInBackground(String path) {
        return performBackupInBackground(path, BACKUP_INTERVAL, false);
    }


    public static boolean performBackupInBackground(String path, boolean force) {
        return performBackupInBackground(path, BACKUP_INTERVAL, force);
    }


    @SuppressWarnings("PMD.NPathComplexity")
    private static boolean performBackupInBackground(final String colPath, int interval, boolean force) {
        SharedPreferences prefs = AnkiDroidApp.getSharedPrefs(AnkiDroidApp.getInstance().getBaseContext());
        if (prefs.getInt("backupMax", 8) == 0 && !force) {
            Timber.w("backups are disabled");
            return false;
        }
        final File colFile = new File(colPath);
        File[] deckBackups = getBackups(colFile);
        int len = deckBackups.length;
        if (len > 0 && deckBackups[len - 1].lastModified() == colFile.lastModified()) {
            Timber.d("performBackup: No backup necessary due to no collection changes");
            return false;
        }

        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd-HH-mm", Locale.US);
        Calendar cal = new GregorianCalendar();
        cal.setTimeInMillis(System.currentTimeMillis());

        // Abort backup if one was already made less than 5 hours ago
        Date lastBackupDate = null;
        while (lastBackupDate == null && len > 0) {
            try {
                len--;
                lastBackupDate = df.parse(deckBackups[len].getName().replaceAll(
                        "^.*-(\\d{4}-\\d{2}-\\d{2}-\\d{2}-\\d{2}).apkg$", "$1"));
            } catch (ParseException e) {
                lastBackupDate = null;
            }
        }
        if (lastBackupDate != null && lastBackupDate.getTime() + interval * 3600000L > Utils.intNow(1000) && !force) {
            Timber.d("performBackup: No backup created. Last backup younger than 5 hours");
            return false;
        }

        String backupFilename;
        try {
            backupFilename = String.format(Utils.ENGLISH_LOCALE, colFile.getName().replace(".anki2", "")
                    + "-%s.apkg", df.format(cal.getTime()));
        } catch (UnknownFormatConversionException e) {
            Timber.e(e, "performBackup: error on creating backup filename");
            return false;
        }

        // Abort backup if destination already exists (extremely unlikely)
        final File backupFile = new File(getBackupDirectory(colFile.getParentFile()), backupFilename);
        if (backupFile.exists()) {
            Timber.d("performBackup: No new backup created. File already exists");
            return false;
        }

        // Abort backup if not enough free space
        if (getFreeDiscSpace(colFile) < colFile.length() + (MIN_FREE_SPACE * 1024 * 1024)) {
            Timber.e("performBackup: Not enough space on sd card to backup.");
            prefs.edit().putBoolean("noSpaceLeft", true).apply();
            return false;
        }

        // Don't bother trying to do backup if the collection is too small to be valid
        if (colFile.length() < MIN_BACKUP_COL_SIZE) {
            Timber.d("performBackup: No backup created as the collection is too small to be valid");
            return false;
        }


        // TODO: Probably not a good idea to do the backup while the collection is open
        if (CollectionHelper.getInstance().colIsOpen()) {
            Timber.w("Collection is already open during backup... we probably shouldn't be doing this");
        }
        Timber.i("Launching new thread to backup %s to %s", colPath, backupFile.getPath());

        // Backup collection as apkg in new thread
        Thread thread = new Thread() {
            @Override
            public void run() {
                // Save collection file as zip archive
                try {
                    ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(backupFile)));
                    ZipEntry ze = new ZipEntry("collection.anki2");
                    zos.putNextEntry(ze);
                    CompatHelper.getCompat().copyFile(colPath, zos);
                    zos.close();
                    // Delete old backup files if needed
                    SharedPreferences prefs = AnkiDroidApp.getSharedPrefs(AnkiDroidApp.getInstance().getBaseContext());
                    deleteDeckBackups(colPath, prefs.getInt("backupMax", 8));
                    // set timestamp of file in order to avoid creating a new backup unless its changed
                    if (!backupFile.setLastModified(colFile.lastModified())) {
                        Timber.w("performBackupInBackground() setLastModified() failed on file %s", backupFilename);
                    }
                    Timber.i("Backup created succesfully");
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        };
        thread.start();
        return true;
    }


    public static boolean enoughDiscSpace(String path) {
        return getFreeDiscSpace(path) >= (MIN_FREE_SPACE * 1024 * 1024);
    }

    /**
     * Get free disc space in bytes from path to Collection
     */
    public static long getFreeDiscSpace(String path) {
        return getFreeDiscSpace(new File(path));
    }


    private static long getFreeDiscSpace(File file) {
        try {
            return CompatHelper.getCompat().getAvailableBytes(new StatFs(file.getParentFile().getPath()));
        } catch (IllegalArgumentException e) {
            Timber.e(e, "Free space could not be retrieved");
            return MIN_FREE_SPACE * 1024 * 1024;
        }
    }


    /**
     * Run the sqlite3 command-line-tool (if it exists) on the collection to dump to a text file
     * and reload as a new database. Recently this command line tool isn't available on many devices
     *
     * @param col Collection
     * @return whether the repair was successful
     */
    public static boolean repairCollection(Collection col) {
        String deckPath = col.getPath();
        File deckFile = new File(deckPath);
        col.close();

        // repair file
        String execString = "sqlite3 " + deckPath + " .dump | sqlite3 " + deckPath + ".tmp";
        Timber.i("repairCollection - Execute: %s", execString);
        try {
            String[] cmd = { "/system/bin/sh", "-c", execString };
            Process process = Runtime.getRuntime().exec(cmd);
            process.waitFor();

            if (!new File(deckPath + ".tmp").exists()) {
                Timber.e("repairCollection - dump to " + deckPath + ".tmp failed");
                return false;
            }

            if (!moveDatabaseToBrokenFolder(deckPath, false)) {
                Timber.e("repairCollection - could not move corrupt file to broken folder");
                return false;
            }
            Timber.i("repairCollection - moved corrupt file to broken folder");
            File repairedFile = new File(deckPath + ".tmp");
            return repairedFile.renameTo(deckFile);
        } catch (IOException | InterruptedException e) {
            Timber.e(e, "repairCollection - error");
        }
        return false;
    }


    public static boolean moveDatabaseToBrokenFolder(String colPath, boolean moveConnectedFilesToo) {
        File colFile = new File(colPath);

        // move file
        Date value = Utils.genToday(Utils.utcOffset());
        String movedFilename = String.format(Utils.ENGLISH_LOCALE, colFile.getName().replace(".anki2", "")
                + "-corrupt-%tF.anki2", value);
        File movedFile = new File(getBrokenDirectory(colFile.getParentFile()), movedFilename);
        int i = 1;
        while (movedFile.exists()) {
            movedFile = new File(getBrokenDirectory(colFile.getParentFile()), movedFilename.replace(".anki2",
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
                if (f.getName().startsWith(deckName) &&
                        !f.renameTo(new File(getBrokenDirectory(colFile.getParentFile()), f.getName().replace(deckName, movedFilename)))) {
                    return false;
                }
            }
        }
        return true;
    }


    public static File[] getBackups(File colFile) {
        File[] files = getBackupDirectory(colFile.getParentFile()).listFiles();
        if (files == null) {
            files = new File[0];
        }
        ArrayList<File> deckBackups = new ArrayList<>();
        for (File aktFile : files) {
            if (aktFile.getName().replaceAll("^(.*)-\\d{4}-\\d{2}-\\d{2}-\\d{2}-\\d{2}.apkg$", "$1.apkg")
                    .equals(colFile.getName().replace(".anki2",".apkg"))) {
                deckBackups.add(aktFile);
            }
        }
        Collections.sort(deckBackups);
        File[] fileList = new File[deckBackups.size()];
        deckBackups.toArray(fileList);
        return fileList;
    }


    private static boolean deleteDeckBackups(String colFile, int keepNumber) {
        return deleteDeckBackups(getBackups(new File(colFile)), keepNumber);
    }


    private static boolean deleteDeckBackups(File[] backups, int keepNumber) {
        if (backups == null) {
            return false;
        }
        for (int i = 0; i < backups.length - keepNumber; i++) {
            if (!backups[i].delete()) {
                Timber.e("deleteDeckBackups() failed to delete %s", backups[i].getAbsolutePath());
            } else {
                Timber.i("deleteDeckBackups: backup file %s deleted.", backups[i].getAbsolutePath());
            }
        }
        return true;
    }


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
