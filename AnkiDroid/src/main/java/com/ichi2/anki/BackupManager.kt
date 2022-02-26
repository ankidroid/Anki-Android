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

package com.ichi2.anki

import android.content.SharedPreferences
import androidx.annotation.VisibleForTesting
import com.ichi2.anki.exception.OutOfSpaceException
import com.ichi2.compat.CompatHelper
import com.ichi2.libanki.Collection
import com.ichi2.libanki.Utils
import com.ichi2.libanki.utils.Time
import com.ichi2.libanki.utils.Time.Companion.utcOffset
import com.ichi2.utils.FileUtil.getFreeDiskSpace
import com.ichi2.utils.KotlinCleanup
import timber.log.Timber
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

open class BackupManager {
    val df = SimpleDateFormat("yyyy-MM-dd-HH-mm", Locale.US)
    @KotlinCleanup("make path non-null")
    @Throws(OutOfSpaceException::class)
    fun performDowngradeBackupInForeground(path: String?): Boolean {
        val colFile = File(path!!)
        if (!hasFreeDiscSpace(colFile)) {
            Timber.w("Could not backup: no free disc space")
            throw OutOfSpaceException()
        }
        val backupFile = getBackupFile(colFile, "ankiDroidv16.colpkg")
        return try {
            performBackup(colFile, backupFile)
        } catch (e: Exception) {
            Timber.w(e)
            AnkiDroidApp.sendExceptionReport(e, "performBackupInForeground")
            false
        }
    }

    @KotlinCleanup("make colPath non-null")
    @Suppress("PMD.NPathComplexity")
    fun performBackupInBackground(colPath: String?, interval: Int, time: Time): Boolean {
        val prefs = AnkiDroidApp.getSharedPrefs(AnkiDroidApp.getInstance().baseContext)
        if (hasDisabledBackups(prefs)) {
            Timber.w("backups are disabled")
            return false
        }
        val colFile = File(colPath!!)
        val deckBackups = getBackups(colFile)
        if (isBackupUnnecessary(colFile, deckBackups)) {
            Timber.d("performBackup: No backup necessary due to no collection changes")
            return false
        }

        // Abort backup if one was already made less than 5 hours ago
        val lastBackupDate = getLastBackupDate(deckBackups)
        if (lastBackupDate != null && lastBackupDate.time + interval * 3600000L > time.intTimeMS()) {
            Timber.d("performBackup: No backup created. Last backup younger than 5 hours")
            return false
        }
        val backupFilename = getNameForNewBackup(time) ?: return false

        // Abort backup if destination already exists (extremely unlikely)
        val backupFile = getBackupFile(colFile, backupFilename)
        if (backupFile.exists()) {
            Timber.d("performBackup: No new backup created. File already exists")
            return false
        }

        // Abort backup if not enough free space
        if (!hasFreeDiscSpace(colFile)) {
            Timber.e("performBackup: Not enough space on sd card to backup.")
            prefs.edit().putBoolean("noSpaceLeft", true).apply()
            return false
        }

        // Don't bother trying to do backup if the collection is too small to be valid
        if (collectionIsTooSmallToBeValid(colFile)) {
            Timber.d("performBackup: No backup created as the collection is too small to be valid")
            return false
        }

        // TODO: Probably not a good idea to do the backup while the collection is open
        if (CollectionHelper.getInstance().colIsOpen()) {
            Timber.w("Collection is already open during backup... we probably shouldn't be doing this")
        }

        // Backup collection as Anki package in new thread
        performBackupInNewThread(colFile, backupFile)
        return true
    }

    fun isBackupUnnecessary(colFile: File, deckBackups: Array<File>): Boolean {
        val len = deckBackups.size

        // If have no backups, then a backup is necessary
        return if (len <= 0) {
            false
        } else deckBackups[len - 1].lastModified() == colFile.lastModified()

        // no collection changes means we don't need a backup
    }

    /**
     * @return filename with pattern collection-yyyy-MM-dd-HH-mm based on given time parameter
     */
    fun getNameForNewBackup(time: Time): String? {
        /** Changes in the file name pattern should be updated as well in
         * [getBackupTimeStrings] and [com.ichi2.anki.dialogs.DatabaseErrorDialog.onCreateDialog] */
        val cal: Calendar = time.gregorianCalendar()
        val backupFilename: String = try {
            String.format(Utils.ENGLISH_LOCALE, "collection-%s.colpkg", df.format(cal.time))
        } catch (e: UnknownFormatConversionException) {
            Timber.w(e, "performBackup: error on creating backup filename")
            return null
        }
        return backupFilename
    }

    /**
     * @return date in fileName if it matches backup naming pattern or null if not
     */
    fun getBackupDate(fileName: String): Date? {
        return try {
            df.parse(fileName)
        } catch (e: ParseException) {
            null
        }
    }

    /**
     * @return last date in parseable file names or null if all names can't be parsed
     */
    fun getLastBackupDate(files: Array<File>): Date? {
        for (file in files.sortedDescending()) {
            getBackupTimeStrings(file.name)?.let { return getBackupDate(it[0]) }
        }
        return null
    }

    @KotlinCleanup("make backupFilename non-null")
    fun getBackupFile(colFile: File, backupFilename: String?): File {
        return File(getBackupDirectory(colFile.parentFile!!), backupFilename!!)
    }

    fun performBackupInNewThread(colFile: File, backupFile: File) {
        Timber.i("Launching new thread to backup %s to %s", colFile.absolutePath, backupFile.path)
        val thread: Thread = object : Thread() {
            override fun run() {
                performBackup(colFile, backupFile)
            }
        }
        thread.start()
    }

    private fun performBackup(colFile: File, backupFile: File): Boolean {
        val colPath = colFile.absolutePath
        // Save collection file as zip archive
        return try {
            val zos = ZipOutputStream(BufferedOutputStream(FileOutputStream(backupFile)))
            val ze = ZipEntry(CollectionHelper.COLLECTION_FILENAME)
            zos.putNextEntry(ze)
            CompatHelper.getCompat().copyFile(colPath, zos)
            zos.close()
            // Delete old backup files if needed
            val prefs = AnkiDroidApp.getSharedPrefs(AnkiDroidApp.getInstance().baseContext)
            deleteDeckBackups(colPath, prefs.getInt("backupMax", 8))
            // set timestamp of file in order to avoid creating a new backup unless its changed
            if (!backupFile.setLastModified(colFile.lastModified())) {
                Timber.w("performBackupInBackground() setLastModified() failed on file %s", backupFile.name)
                return false
            }
            Timber.i("Backup created succesfully")
            true
        } catch (e: IOException) {
            Timber.w(e)
            false
        }
    }

    fun collectionIsTooSmallToBeValid(colFile: File): Boolean {
        return (
            colFile.length()
                < MIN_BACKUP_COL_SIZE
            )
    }

    fun hasFreeDiscSpace(colFile: File): Boolean {
        return getFreeDiscSpace(colFile) >= getRequiredFreeSpace(colFile)
    }

    @VisibleForTesting
    fun hasDisabledBackups(prefs: SharedPreferences): Boolean {
        return prefs.getInt("backupMax", 8) == 0
    }

    companion object {
        private const val MIN_FREE_SPACE = 10
        private const val MIN_BACKUP_COL_SIZE = 10000 // threshold in bytes to backup a col file
        private const val BACKUP_SUFFIX = "backup"
        const val BROKEN_DECKS_SUFFIX = "broken"
        private val backupNameRegex = Regex("collection-((\\d{4})-(\\d{2})-(\\d{2})-(\\d{2})-(\\d{2})).colpkg")

        /** Number of hours after which a backup new backup is created  */
        private const val BACKUP_INTERVAL = 5
        val isActivated: Boolean
            get() = true

        @KotlinCleanup("make non-null - requires fixing unit tests - they accidentally use empty string as the path")
        @JvmStatic
        fun getBackupDirectory(ankidroidDir: File?): File {
            val directory = File(ankidroidDir, BACKUP_SUFFIX)
            if (!directory.isDirectory && !directory.mkdirs()) {
                Timber.w("getBackupDirectory() mkdirs on %s failed", ankidroidDir)
            }
            return directory
        }

        private fun getBrokenDirectory(ankidroidDir: File): File {
            val directory = File(ankidroidDir, BROKEN_DECKS_SUFFIX)
            if (!directory.isDirectory && !directory.mkdirs()) {
                Timber.w("getBrokenDirectory() mkdirs on %s failed", ankidroidDir)
            }
            return directory
        }

        @JvmStatic
        fun performBackupInBackground(path: String?, time: Time): Boolean {
            return BackupManager().performBackupInBackground(path, BACKUP_INTERVAL, time)
        }

        /**
         * @param colFile The current collection file to backup
         * @return the amount of free space required for a backup.
         */
        @KotlinCleanup("make colFile non-null")
        fun getRequiredFreeSpace(colFile: File?): Long {
            // We add a minimum amount of free space to ensure against
            return colFile!!.length() + MIN_FREE_SPACE * 1024 * 1024
        }

        @JvmStatic
        fun enoughDiscSpace(path: String?): Boolean {
            return getFreeDiscSpace(path) >= MIN_FREE_SPACE * 1024 * 1024
        }

        /**
         * Get free disc space in bytes from path to Collection
         */
        fun getFreeDiscSpace(path: String?): Long {
            return getFreeDiscSpace(File(path!!))
        }

        private fun getFreeDiscSpace(file: File): Long {
            return getFreeDiskSpace(file, (MIN_FREE_SPACE * 1024 * 1024).toLong())
        }

        /**
         * Run the sqlite3 command-line-tool (if it exists) on the collection to dump to a text file
         * and reload as a new database. Recently this command line tool isn't available on many devices
         *
         * @param col Collection
         * @return whether the repair was successful
         */
        @JvmStatic
        fun repairCollection(col: Collection): Boolean {
            val deckPath = col.path
            val deckFile = File(deckPath)
            val time = col.time
            Timber.i("BackupManager - RepairCollection - Closing Collection")
            col.close()

            // repair file
            val execString = "sqlite3 $deckPath .dump | sqlite3 $deckPath.tmp"
            Timber.i("repairCollection - Execute: %s", execString)
            try {
                val cmd = arrayOf("/system/bin/sh", "-c", execString)
                val process = Runtime.getRuntime().exec(cmd)
                process.waitFor()
                if (!File("$deckPath.tmp").exists()) {
                    Timber.e("repairCollection - dump to %s.tmp failed", deckPath)
                    return false
                }
                if (!moveDatabaseToBrokenFolder(deckPath, false, time)) {
                    Timber.e("repairCollection - could not move corrupt file to broken folder")
                    return false
                }
                Timber.i("repairCollection - moved corrupt file to broken folder")
                val repairedFile = File("$deckPath.tmp")
                return repairedFile.renameTo(deckFile)
            } catch (e: IOException) {
                Timber.e(e, "repairCollection - error")
            } catch (e: InterruptedException) {
                Timber.e(e, "repairCollection - error")
            }
            return false
        }

        @KotlinCleanup("make colPath non-null")
        fun moveDatabaseToBrokenFolder(colPath: String?, moveConnectedFilesToo: Boolean, time: Time): Boolean {
            val colFile = File(colPath!!)

            // move file
            val value: Date = time.genToday(utcOffset())
            var movedFilename = String.format(
                Utils.ENGLISH_LOCALE,
                colFile.name.replace(".anki2", "") +
                    "-corrupt-%tF.anki2",
                value
            )
            var movedFile = File(getBrokenDirectory(colFile.parentFile!!), movedFilename)
            var i = 1
            while (movedFile.exists()) {
                movedFile = File(
                    getBrokenDirectory(colFile.parentFile!!),
                    movedFilename.replace(
                        ".anki2",
                        "-$i.anki2"
                    )
                )
                i++
            }
            movedFilename = movedFile.name
            if (!colFile.renameTo(movedFile)) {
                return false
            }
            if (moveConnectedFilesToo) {
                // move all connected files (like journals, directories...) too
                val deckName = colFile.name
                val directory = File(colFile.parent!!)
                for (f in directory.listFiles()!!) {
                    if (f.name.startsWith(deckName) &&
                        !f.renameTo(File(getBrokenDirectory(colFile.parentFile!!), f.name.replace(deckName, movedFilename)))
                    ) {
                        return false
                    }
                }
            }
            return true
        }

        /**
         * Parses a string with backup naming pattern
         * @param fileName String with pattern "collection-yyyy-MM-dd-HH-mm.colpkg"
         * @return List with {dateformat, year, month, day, hours, minutes} or null if it doesn't match naming pattern
         */
        @JvmStatic
        fun getBackupTimeStrings(fileName: String): List<String>? {
            val m = backupNameRegex.matchEntire(fileName)
            return m?.groupValues?.subList(1, 7)
        }

        @JvmStatic
        /**
         * @return Array of files with names which matches the backup name pattern
         */
        fun getBackups(colFile: File): Array<File> {
            var files = getBackupDirectory(colFile.parentFile).listFiles()
            if (files == null) {
                files = arrayOf<File>()
            }
            val backups = mutableListOf<File>()
            for (aktFile in files) {
                if (getBackupTimeStrings(aktFile.name) != null) {
                    backups.add(aktFile)
                }
            }
            return backups.toTypedArray()
        }

        /**
         * Deletes the first files until only the given number of files remain
         * @param colPath Path of collection file whose backups should be deleted
         * @param keepNumber How many files to keep
         */
        @JvmStatic
        fun deleteDeckBackups(colPath: String, keepNumber: Int): Boolean {
            return deleteDeckBackups(getBackups(File(colPath)).sortedArray(), keepNumber)
        }

        private fun deleteDeckBackups(backups: Array<File>, keepNumber: Int): Boolean {
            for (i in 0 until backups.size - keepNumber) {
                if (!backups[i].delete()) {
                    Timber.e("deleteDeckBackups() failed to delete %s", backups[i].absolutePath)
                } else {
                    Timber.i("deleteDeckBackups: backup file %s deleted.", backups[i].absolutePath)
                }
            }
            return true
        }

        @JvmStatic
        fun removeDir(dir: File): Boolean {
            if (dir.isDirectory) {
                val files = dir.listFiles()
                for (aktFile in files!!) {
                    removeDir(aktFile)
                }
            }
            return dir.delete()
        }

        @JvmStatic
        @VisibleForTesting(otherwise = VisibleForTesting.NONE)
        fun createInstance(): BackupManager {
            return BackupManager()
        }
    }
}
