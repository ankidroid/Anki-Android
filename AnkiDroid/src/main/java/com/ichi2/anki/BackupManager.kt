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
import android.text.format.DateFormat
import androidx.annotation.VisibleForTesting
import androidx.core.content.edit
import com.ichi2.compat.CompatHelper
import com.ichi2.libanki.Collection
import com.ichi2.libanki.Utils
import com.ichi2.libanki.utils.Time
import com.ichi2.libanki.utils.Time.Companion.utcOffset
import com.ichi2.libanki.utils.TimeManager
import com.ichi2.utils.FileUtil.getFreeDiskSpace
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
    /**
     * Attempts to create a backup in a background thread. Returns `true` if the process is started.
     *
     * Returns false:
     * * If backups are disabled
     * * If [interval] hours have not elapsed since the last backup
     * * If the filename creation failed
     * * If [interval] is 0, and the backup already exists
     * * If the user has insufficient space
     * * If the collection is too small to be valid
     *
     * @param colPath The path of the collection file
     * @param interval If this amount of hours has not elapsed since last backup, return false and do not create backup. See: [BACKUP_INTERVAL]
     *
     * @return Whether a thread was started to create a backup
     */
    @Suppress("PMD.NPathComplexity")
    fun performBackupInBackground(colPath: String, interval: Int, time: Time): Boolean {
        val prefs = AnkiDroidApp.getSharedPrefs(AnkiDroidApp.instance.baseContext)
        if (hasDisabledBackups(prefs)) {
            Timber.w("backups are disabled")
            return false
        }
        val colFile = File(colPath)
        val colBackups = getBackups(colFile)
        if (isBackupUnnecessary(colFile, colBackups)) {
            Timber.d("performBackup: No backup necessary due to no collection changes")
            return false
        }

        // Abort backup if one was already made less than [interval] hours ago (default: 5 hours - BACKUP_INTERVAL)
        val lastBackupDate = getLastBackupDate(colBackups)
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
            prefs.edit { putBoolean("noSpaceLeft", true) }
            return false
        }

        // Don't bother trying to do backup if the collection is too small to be valid
        if (collectionIsTooSmallToBeValid(colFile)) {
            Timber.d("performBackup: No backup created as the collection is too small to be valid")
            return false
        }

        // TODO: Probably not a good idea to do the backup while the collection is open
        if (CollectionHelper.instance.colIsOpen()) {
            Timber.w("Collection is already open during backup... we probably shouldn't be doing this")
        }

        // Backup collection as Anki package in new thread
        performBackupInNewThread(colFile, backupFile)
        return true
    }

    fun isBackupUnnecessary(colFile: File, colBackups: Array<File>): Boolean {
        val len = colBackups.size

        // If have no backups, then a backup is necessary
        return if (len <= 0) {
            false
        } else {
            colBackups[len - 1].lastModified() == colFile.lastModified()
        }

        // no collection changes means we don't need a backup
    }

    /**
     * @return last date in parsable file names or null if all names can't be parsed
     * Expects a sorted array of backups, as returned by getBackups()
     */
    fun getLastBackupDate(files: Array<File>): Date? {
        return files.lastOrNull()?.let {
            getBackupDate(it.name)
        }
    }

    fun getBackupFile(colFile: File, backupFilename: String): File {
        return File(getBackupDirectory(colFile.parentFile!!), backupFilename)
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
            CompatHelper.compat.copyFile(colPath, zos)
            zos.close()
            // Delete old backup files if needed
            val prefs = AnkiDroidApp.getSharedPrefs(AnkiDroidApp.instance.baseContext)
            deleteColBackups(colPath, prefs.getInt("backupMax", 8))
            // set timestamp of file in order to avoid creating a new backup unless its changed
            if (!backupFile.setLastModified(colFile.lastModified())) {
                Timber.w("performBackupInBackground() setLastModified() failed on file %s", backupFile.name)
                return false
            }
            Timber.i("Backup created successfully")
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
        /**
         * Number of MB of
         */
        private const val MIN_FREE_SPACE = 10
        private const val MIN_BACKUP_COL_SIZE = 10000 // threshold in bytes to backup a col file
        private const val BACKUP_SUFFIX = "backup"
        const val BROKEN_COLLECTIONS_SUFFIX = "broken"
        private val backupNameRegex: Regex by lazy {
            Regex("(?:collection|backup)-((\\d{4})-(\\d{2})-(\\d{2})-(\\d{2})[.-](\\d{2}))(?:\\.\\d{2})?.colpkg")
        }

        /** Number of hours after which a backup new backup is created  */
        private const val BACKUP_INTERVAL = 5
        private val legacyDateFormat = SimpleDateFormat("yyyy-MM-dd-HH-mm")
        private val newDateFormat = SimpleDateFormat("yyyy-MM-dd-HH.mm")
        val isActivated: Boolean
            get() = true

        fun getBackupDirectory(ankidroidDir: File): File {
            val directory = File(ankidroidDir, BACKUP_SUFFIX)
            if (!directory.isDirectory && !directory.mkdirs()) {
                Timber.w("getBackupDirectory() mkdirs on %s failed", ankidroidDir)
            }
            return directory
        }

        fun getBackupDirectoryFromCollection(colPath: String): String {
            return getBackupDirectory(File(colPath).parentFile!!).absolutePath
        }

        private fun getBrokenDirectory(ankidroidDir: File): File {
            val directory = File(ankidroidDir, BROKEN_COLLECTIONS_SUFFIX)
            if (!directory.isDirectory && !directory.mkdirs()) {
                Timber.w("getBrokenDirectory() mkdirs on %s failed", ankidroidDir)
            }
            return directory
        }

        fun performBackupInBackground(path: String, time: Time): Boolean {
            return BackupManager().performBackupInBackground(path, BACKUP_INTERVAL, time)
        }

        /**
         * @param colFile The current collection file to backup
         * @return the amount of free space required for a backup.
         */
        fun getRequiredFreeSpace(colFile: File): Long {
            // We add a minimum amount of free space to ensure against
            return colFile.length() + MIN_FREE_SPACE * 1024 * 1024
        }

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
        fun repairCollection(col: Collection): Boolean {
            val colPath = col.path
            val colFile = File(colPath)
            val time = TimeManager.time
            Timber.i("BackupManager - RepairCollection - Closing Collection")
            col.close()

            // repair file
            val execString = "sqlite3 $colPath .dump | sqlite3 $colPath.tmp"
            Timber.i("repairCollection - Execute: %s", execString)
            try {
                val cmd = arrayOf("/system/bin/sh", "-c", execString)
                val process = Runtime.getRuntime().exec(cmd)
                process.waitFor()
                if (!File("$colPath.tmp").exists()) {
                    Timber.e("repairCollection - dump to %s.tmp failed", colPath)
                    return false
                }
                if (!moveDatabaseToBrokenDirectory(colPath, false, time)) {
                    Timber.e("repairCollection - could not move corrupt file to broken directory")
                    return false
                }
                Timber.i("repairCollection - moved corrupt file to broken directory")
                val repairedFile = File("$colPath.tmp")
                return repairedFile.renameTo(colFile)
            } catch (e: IOException) {
                Timber.e(e, "repairCollection - error")
            } catch (e: InterruptedException) {
                Timber.e(e, "repairCollection - error")
            }
            return false
        }

        fun moveDatabaseToBrokenDirectory(colPath: String, moveConnectedFilesToo: Boolean, time: Time): Boolean {
            val colFile = File(colPath)

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
                val colName = colFile.name
                val directory = File(colFile.parent!!)
                for (f in directory.listFiles()!!) {
                    if (f.name.startsWith(colName) &&
                        !f.renameTo(File(getBrokenDirectory(colFile.parentFile!!), f.name.replace(colName, movedFilename)))
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
         * @return Its dateformat parsable string or null if it doesn't match naming pattern
         */
        fun getBackupTimeString(fileName: String): String? {
            return backupNameRegex.matchEntire(fileName)?.groupValues?.get(1)
        }

        /**
         * @return date in string if it matches backup naming pattern or null if not
         */
        fun parseBackupTimeString(timeString: String): Date? {
            return try {
                legacyDateFormat.parse(timeString)
            } catch (e: ParseException) {
                try {
                    newDateFormat.parse(timeString)
                } catch (e: ParseException) {
                    null
                }
            }
        }

        /**
         * @return date in fileName if it matches backup naming pattern or null if not
         */
        fun getBackupDate(fileName: String): Date? {
            return getBackupTimeString(fileName)?.let { parseBackupTimeString(it) }
        }

        /**
         * @return filename with pattern collection-yyyy-MM-dd-HH-mm based on given time parameter
         */
        fun getNameForNewBackup(time: Time): String? {
            /** Changes in the file name pattern should be updated as well in
             * [getBackupTimeString] and [com.ichi2.anki.dialogs.DatabaseErrorDialog.onCreateDialog] */
            val cal: Calendar = time.gregorianCalendar()
            val backupFilename: String = try {
                String.format(Utils.ENGLISH_LOCALE, "collection-%s.colpkg", legacyDateFormat.format(cal.time))
            } catch (e: UnknownFormatConversionException) {
                Timber.w(e, "performBackup: error on creating backup filename")
                return null
            }
            return backupFilename
        }

        /**
         * @return Array of files with names which matches the backup name pattern,
         * in order of creation.
         */
        fun getBackups(colFile: File): Array<File> {
            val files = getBackupDirectory(colFile.parentFile!!).listFiles() ?: arrayOf()
            val backups = files
                .mapNotNull { file ->
                    getBackupTimeString(file.name)?.let { time ->
                        Pair(time, file)
                    }
                }
                .sortedBy { it.first }
                .map { it.second }
            return backups.toTypedArray()
        }

        /**
         * Returns the most recent backup, or null if no backups exist matching [the backup name pattern][backupNameRegex]
         *
         * @return the most recent backup, or null if no backups exist
         */
        fun getLatestBackup(colFile: File): File? = getBackups(colFile).lastOrNull()

        /**
         * Deletes the first files until only the given number of files remain
         * @param colPath Path of collection file whose backups should be deleted
         * @param keepNumber How many files to keep
         */
        fun deleteColBackups(colPath: String, keepNumber: Int): Boolean {
            return deleteColBackups(getBackups(File(colPath)), keepNumber)
        }

        private fun deleteColBackups(backups: Array<File>, keepNumber: Int): Boolean {
            for (i in 0 until backups.size - keepNumber) {
                if (!backups[i].delete()) {
                    Timber.e("deleteColBackups() failed to delete %s", backups[i].absolutePath)
                } else {
                    Timber.i("deleteColBackups: backup file %s deleted.", backups[i].absolutePath)
                }
            }
            return true
        }

        /**
         * Delete backups as specified by [backupsToDelete],
         * throwing [IllegalArgumentException] if any of the files passed aren't actually backups.
         *
         * @return Whether all specified backups were successfully deleted.
         */
        @Throws(IllegalArgumentException::class)
        fun deleteBackups(col: Collection, backupsToDelete: List<File>): Boolean {
            val allBackups = getBackups(File(col.path))
            val invalidBackupsToDelete = backupsToDelete.toSet() - allBackups.toSet()

            if (invalidBackupsToDelete.isNotEmpty()) {
                throw IllegalArgumentException("Not backup files: $invalidBackupsToDelete")
            }

            return backupsToDelete.all { it.delete() }
        }

        fun removeDir(dir: File): Boolean {
            if (dir.isDirectory) {
                val files = dir.listFiles()
                for (aktFile in files!!) {
                    removeDir(aktFile)
                }
            }
            return dir.delete()
        }

        @VisibleForTesting(otherwise = VisibleForTesting.NONE)
        fun createInstance(): BackupManager {
            return BackupManager()
        }
    }
}

/**
 * Formatter that produces localized date & time strings for backups.
 * `getBestDateTimePattern` is used instead of `DateFormat.getInstance()` to produce dates
 * in format such as "02 Nov 2022" instead of "11/2/22" or "2/11/22", which can be confusing.
 */
class LocalizedUnambiguousBackupTimeFormatter {
    private val formatter = SimpleDateFormat(
        DateFormat.getBestDateTimePattern(Locale.getDefault(), "dd MMM yyyy HH:mm")
    )

    fun getTimeOfBackupAsText(file: File): String {
        val backupDate = BackupManager.getBackupDate(file.name) ?: return file.name
        return formatter.format(backupDate)
    }
}
