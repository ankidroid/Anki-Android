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

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.text.format.DateFormat
import androidx.documentfile.provider.DocumentFile
import com.ichi2.libanki.Collection
import com.ichi2.libanki.Utils
import com.ichi2.libanki.utils.Time
import com.ichi2.libanki.utils.Time.Companion.utcOffset
import com.ichi2.libanki.utils.TimeManager
import com.ichi2.utils.FileUtil.getFreeDiskSpace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.io.use

open class BackupManager {
    // move folder from one SAF Uri to another, using persistedUri
    fun moveBackupFiles(
        context: Context,
        sourceUri: Uri,
        targetUri: Uri,
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            var success = false
            isBackupMoveInProgress = true
            try {
                val resolver = context.contentResolver
                val sourceFolder = DocumentFile.fromTreeUri(context, sourceUri)
                val targetFolder = DocumentFile.fromTreeUri(context, targetUri)

                if (sourceFolder?.isDirectory != true || targetFolder?.isDirectory != true) {
                    Timber.w("Invalid source or target folder")
                    return@launch
                }

                sourceFolder.listFiles().forEach { file ->
                    if (!file.isFile || !file.name.orEmpty().endsWith(".colpkg")) return@forEach

                    val targetFile =
                        targetFolder.createFile(file.type ?: "application/octet-stream", file.name ?: "unknown") ?: run {
                            return@forEach
                        }

                    resolver.openInputStream(file.uri)?.use { input ->
                        resolver.openOutputStream(targetFile.uri)?.use { output ->
                            input.copyTo(output)
                            if (!file.delete()) Timber.w("Failed to delete ${file.name}")
                        } ?: Timber.w("Failed to open output stream for target file: ${targetFile.name}")
                    } ?: Timber.w("Failed to open input stream for file: ${file.name}")
                }
                success = true
            } catch (e: Exception) {
                Timber.e(e, "Error moving files from the old directory to the new one.")
            }
            isBackupMoveInProgress = false
            withContext(Dispatchers.Main) {
                if (!success) {
                    showThemedToast(context, R.string.backup_move_failed, true)
                } else {
                    showThemedToast(context, R.string.backup_move_success, true)
                }
            }
        }
    }

    // move from default backup dir to custom one (SAF uri)
    // won't show success toast when backup is moved after creation
    fun moveBackupFilesFromDefault(
        context: Context,
        showSuccessToast: Boolean,
    ) {
        val sourceFolder = getBackupDirectory(File(CollectionHelper.getCollectionPath(context)).parentFile!!)

        CoroutineScope(Dispatchers.IO).launch {
            var success = false
            isBackupMoveInProgress = true
            try {
                val resolver = context.contentResolver
                val targetUri = resolver.persistedUriPermissions.firstOrNull()?.uri
                if (targetUri == null) {
                    Timber.w("No persisted URI found")
                    return@launch
                }

                val documentId = DocumentsContract.getTreeDocumentId(targetUri)
                val destinationFolderUri = DocumentsContract.buildDocumentUriUsingTree(targetUri, documentId)

                if (!sourceFolder.exists() || !sourceFolder.isDirectory) {
                    Timber.w("Source folder does not exist or is not a directory")
                    return@launch
                }

                sourceFolder.listFiles()?.forEach { sourceFile ->
                    if (!sourceFile.isFile) return@forEach

                    val destinationUri =
                        DocumentsContract.createDocument(
                            resolver,
                            destinationFolderUri,
                            "application/octet-stream",
                            sourceFile.name,
                        )

                    if (destinationUri == null) {
                        Timber.w("Failed to create file in destination for ${sourceFile.name}")
                        return@forEach
                    }

                    try {
                        resolver.openOutputStream(destinationUri)?.use { output ->
                            FileInputStream(sourceFile).use { input ->
                                input.copyTo(output)
                                if (!sourceFile.delete()) Timber.w("Failed to delete ${sourceFile.name}")
                            }
                        } ?: Timber.w("Failed to open output stream for file: ${sourceFile.name}")
                    } catch (e: Exception) {
                        Timber.e(e, "Error moving ${sourceFile.name}")
                    }
                }
                success = true
            } catch (e: Exception) {
                Timber.e(e, "Error moving files from default directory to custom directory")
            }
            isBackupMoveInProgress = false
            withContext(Dispatchers.Main) {
                if (!success) {
                    showThemedToast(context, R.string.backup_move_failed, true)
                } else if (showSuccessToast) {
                    showThemedToast(context, R.string.backup_move_success, true)
                }
            }
        }
    }

    // move from custom one (SAF uri) back to default
    fun moveBackupFilesToDefault(context: Context) {
        val targetFolder = getBackupDirectory(File(CollectionHelper.getCollectionPath(context)).parentFile!!)

        CoroutineScope(Dispatchers.IO).launch {
            var success = false
            isBackupMoveInProgress = true
            try {
                val resolver = context.contentResolver
                val sourceUri = resolver.persistedUriPermissions.firstOrNull()?.uri
                if (sourceUri == null) {
                    Timber.w("No persisted URI found")
                    return@launch
                }

                val sourceFolder = DocumentFile.fromTreeUri(context, sourceUri)
                if (sourceFolder?.isDirectory != true) {
                    Timber.w("Invalid source folder")
                    return@launch
                }

                if (!targetFolder.exists()) {
                    Timber.w("Target directory does not exist")
                    return@launch
                }

                sourceFolder.listFiles().forEach { file ->
                    if (!file.isFile || !file.name.orEmpty().endsWith(".colpkg")) return@forEach

                    val targetFile = File(targetFolder, file.name ?: "unknown")

                    try {
                        resolver.openInputStream(file.uri)?.use { input ->
                            FileOutputStream(targetFile).use { output ->
                                input.copyTo(output)
                                if (!file.delete()) Timber.w("Failed to delete ${file.name}")
                            }
                        } ?: Timber.w("Failed to open input stream for file: ${file.name}")
                    } catch (e: Exception) {
                        Timber.e(e, "Error moving ${file.name}")
                    }
                }

                resolver.persistedUriPermissions.forEach { permission ->
                    resolver.releasePersistableUriPermission(
                        permission.uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                    )
                }
                success = true
            } catch (e: Exception) {
                Timber.e(e, "Error moving files from custom directory to default directory")
            }
            isBackupMoveInProgress = false
            withContext(Dispatchers.Main) {
                if (!success) {
                    showThemedToast(context, R.string.backup_move_failed, true)
                } else {
                    showThemedToast(context, R.string.backup_move_success, true)
                }
            }
        }
    }

    companion object {
        private var isBackupMoveInProgress = false
        private const val MIN_FREE_SPACE = 10
        private const val BACKUP_SUFFIX = "backup"
        const val BROKEN_COLLECTIONS_SUFFIX = "broken"
        private val backupNameRegex: Regex by lazy {
            Regex("(?:collection|backup)-((\\d{4})-(\\d{2})-(\\d{2})-(\\d{2})[.-](\\d{2}))(?:\\.\\d{2})?.colpkg")
        }

        private val legacyDateFormat = SimpleDateFormat("yyyy-MM-dd-HH-mm")
        private val newDateFormat = SimpleDateFormat("yyyy-MM-dd-HH.mm")

        fun getBackupDirectory(ankidroidDir: File): File {
            val directory = File(ankidroidDir, BACKUP_SUFFIX)
            if (!directory.isDirectory && !directory.mkdirs()) {
                Timber.w("getBackupDirectory() mkdirs on %s failed", ankidroidDir)
            }
            return directory
        }

        fun getBackupDirectoryFromCollection(colPath: String): String = getBackupDirectory(File(colPath).parentFile!!).absolutePath

        private fun getBrokenDirectory(ankidroidDir: File): File {
            val directory = File(ankidroidDir, BROKEN_COLLECTIONS_SUFFIX)
            if (!directory.isDirectory && !directory.mkdirs()) {
                Timber.w("getBrokenDirectory() mkdirs on %s failed", ankidroidDir)
            }
            return directory
        }

        // since the import backend using File.absolutePath, get the path from DocumentFile using temp and then delete temp after importing
        fun getTempPathFromDocumentFile(
            context: Context,
            documentFile: DocumentFile,
        ): String {
            val tempFile = File.createTempFile("temp_backup", ".colpkg", context.cacheDir)
            context.contentResolver.openInputStream(documentFile.uri)!!.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            return tempFile.absolutePath
        }

        fun enoughDiscSpace(path: String?): Boolean = getFreeDiscSpace(path) >= MIN_FREE_SPACE * 1024 * 1024

        /**
         * Get free disc space in bytes from path to Collection
         */
        fun getFreeDiscSpace(path: String?): Long = getFreeDiscSpace(File(path!!))

        private fun getFreeDiscSpace(file: File): Long = getFreeDiskSpace(file, (MIN_FREE_SPACE * 1024 * 1024).toLong())

        /**
         * Run the sqlite3 command-line-tool (if it exists) on the collection to dump to a text file
         * and reload as a new database. Recently this command line tool isn't available on many devices
         *
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

        fun moveDatabaseToBrokenDirectory(
            colPath: String,
            moveConnectedFilesToo: Boolean,
            time: Time,
        ): Boolean {
            val colFile = File(colPath)

            // move file
            val value: Date = time.genToday(utcOffset())
            var movedFilename =
                String.format(
                    Utils.ENGLISH_LOCALE,
                    colFile.name.replace(".anki2", "") +
                        "-corrupt-%tF.anki2",
                    value,
                )
            var movedFile = File(getBrokenDirectory(colFile.parentFile!!), movedFilename)
            var i = 1
            while (movedFile.exists()) {
                movedFile =
                    File(
                        getBrokenDirectory(colFile.parentFile!!),
                        movedFilename.replace(
                            ".anki2",
                            "-$i.anki2",
                        ),
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
        fun getBackupTimeString(fileName: String): String? = backupNameRegex.matchEntire(fileName)?.groupValues?.get(1)

        /**
         * @return date in string if it matches backup naming pattern or null if not
         */
        fun parseBackupTimeString(timeString: String): Date? =
            try {
                legacyDateFormat.parse(timeString)
            } catch (e: ParseException) {
                try {
                    newDateFormat.parse(timeString)
                } catch (e: ParseException) {
                    null
                }
            }

        /**
         * @return date in fileName if it matches backup naming pattern or null if not
         */
        fun getBackupDate(fileName: String): Date? = getBackupTimeString(fileName)?.let { parseBackupTimeString(it) }

        /**
         * @return Array of files with names which matches the backup name pattern,
         * in order of creation.
         */
        fun getBackups(colFile: File): Array<File> {
            val files = getBackupDirectory(colFile.parentFile!!).listFiles() ?: arrayOf()
            val backups =
                files
                    .mapNotNull { file ->
                        getBackupTimeString(file.name)?.let { time ->
                            Pair(time, file)
                        }
                    }.sortedBy { it.first }
                    .map { it.second }
            return backups.toTypedArray()
        }

        /**
         * @return Array of DocumentFiles with names which matches the backup name pattern,
         * in order of creation.
         */
        // may break if persistedUri is used elsewhere
        fun getBackups(context: Context): Array<DocumentFile> {
            val persistedUri = context.contentResolver.persistedUriPermissions[0].uri

            val backupDir = DocumentFile.fromTreeUri(context, persistedUri) ?: return emptyArray()
            return backupDir
                .listFiles()
                .filter { it.isFile }
                .mapNotNull { file ->
                    getBackupTimeString(file.name ?: "")?.let { time -> Pair(time, file) }
                }.sortedBy { it.first }
                .map { it.second }
                .toTypedArray()
        }

        /**
         * Delete backups as specified by [backupsToDelete],
         * throwing [IllegalArgumentException] if any of the files passed aren't actually backups.
         *
         * @return Whether all specified backups were successfully deleted.
         */
        @Throws(IllegalArgumentException::class)
        fun deleteBackups(
            collection: Collection,
            backupsToDelete: List<File>,
        ): Boolean {
            val allBackups = getBackups(File(collection.path))
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

        fun isBackupMoveInProgress(): Boolean = isBackupMoveInProgress
    }
}

/**
 * Formatter that produces localized date & time strings for backups.
 * `getBestDateTimePattern` is used instead of `DateFormat.getInstance()` to produce dates
 * in format such as "02 Nov 2022" instead of "11/2/22" or "2/11/22", which can be confusing.
 */
class LocalizedUnambiguousBackupTimeFormatter {
    private val formatter =
        SimpleDateFormat(
            DateFormat.getBestDateTimePattern(Locale.getDefault(), "dd MMM yyyy HH:mm"),
        )

    fun getTimeOfBackupAsText(file: Any): String {
        val name =
            when (file) {
                is File -> file.name
                is DocumentFile -> file.name.orEmpty()
                else -> return "Unknown"
            }

        val backupDate = BackupManager.getBackupDate(name) ?: return name
        return formatter.format(backupDate)
    }
}
