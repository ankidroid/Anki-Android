/****************************************************************************************
 * Copyright (c) 2018 Mike Hardy <mike@mikehardy.net>                                   *
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

package com.ichi2.utils

import android.app.Activity
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Message
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.annotation.CheckResult
import com.afollestad.materialdialogs.MaterialDialog
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.CrashReportService
import com.ichi2.anki.R
import com.ichi2.anki.dialogs.DialogHandler
import com.ichi2.compat.CompatHelper
import org.apache.commons.compress.archivers.zip.ZipFile
import org.jetbrains.annotations.Contract
import timber.log.Timber
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.*
import java.util.zip.ZipException
import java.util.zip.ZipInputStream
import kotlin.collections.ArrayList

object ImportUtils {
    /* A filename should be shortened if over this threshold */
    private const val fileNameShorteningThreshold = 100

    /**
     * This code is used in multiple places to handle package imports
     *
     * @param context for use in resource resolution and path finding
     * @param intent contains the file to import
     * @return null if successful, otherwise error message
     */
    fun handleFileImport(context: Context, intent: Intent): ImportResult {
        return FileImporter().handleFileImport(context, intent)
    }

    /**
     * Makes a cached copy of the file selected on [intent] and returns its path
     */
    fun getFileCachedCopy(context: Context, intent: Intent): String? {
        return FileImporter().getFileCachedCopy(context, intent)
    }

    fun showImportUnsuccessfulDialog(activity: Activity, errorMessage: String?, exitActivity: Boolean) {
        FileImporter().showImportUnsuccessfulDialog(activity, errorMessage, exitActivity)
    }

    fun isCollectionPackage(filename: String?): Boolean {
        return filename != null && (filename.lowercase(Locale.ROOT).endsWith(".colpkg") || "collection.apkg" == filename)
    }

    /** @return Whether the file is either a deck, or a collection package */
    @Contract("null -> false")
    fun isValidPackageName(filename: String?): Boolean {
        return FileImporter.isDeckPackage(filename) || isCollectionPackage(filename)
    }

    /**
     * Whether importUtils can handle the given intent
     * Caused by #6312 - A launcher was sending ACTION_VIEW instead of ACTION_MAIN
     */
    fun isInvalidViewIntent(intent: Intent): Boolean {
        return intent.data == null && intent.clipData == null
    }

    fun isFileAValidDeck(fileName: String): Boolean {
        return FileImporter.hasExtension(fileName, "apkg") || FileImporter.hasExtension(fileName, "colpkg")
    }

    @SuppressWarnings("WeakerAccess")
    open class FileImporter {
        /**
         * This code is used in multiple places to handle package imports
         *
         * @param context for use in resource resolution and path finding
         * @param intent contains the file to import
         * @return null if successful, otherwise error message
         */
        fun handleFileImport(context: Context, intent: Intent): ImportResult {
            // This intent is used for opening apkg package files
            // We want to go immediately to DeckPicker, clearing any history in the process
            Timber.i("IntentHandler/ User requested to view a file")
            val extras = if (intent.extras == null) "none" else intent.extras!!.keySet().joinToString(", ")
            Timber.i("Intent: %s. Data: %s", intent, extras)
            return try {
                handleFileImportInternal(context, intent)
            } catch (e: Exception) {
                CrashReportService.sendExceptionReport(e, "handleFileImport")
                Timber.e(e, "failed to handle import intent")
                ImportResult.fromErrorString(context.getString(R.string.import_error_handle_exception, e.localizedMessage))
            }
        }

        private fun handleFileImportInternal(context: Context, intent: Intent): ImportResult {
            val dataList = getUris(intent)
            return if (dataList != null) {
                handleContentProviderFile(context, intent, dataList)
            } else {
                ImportResult.fromErrorString(context.getString(R.string.import_error_handle_exception))
            }
        }

        /**
         * Makes a cached copy of the file selected on [intent] and returns its path
         */
        fun getFileCachedCopy(context: Context, intent: Intent): String? {
            val uri = getUris(intent)?.get(0) ?: return null
            val filename = ensureValidLength(getFileNameFromContentProvider(context, uri) ?: return null)
            val tempPath = Uri.fromFile(File(context.cacheDir, filename)).encodedPath!!
            return if (copyFileToCache(context, uri, tempPath)) {
                tempPath
            } else {
                null
            }
        }

        private fun handleContentProviderFile(context: Context, intent: Intent, dataList: ArrayList<Uri>): ImportResult {
            // Note: intent.getData() can be null. Use data instead.

            validateImportTypes(context, dataList)?.let { errorMessage ->
                return ImportResult.fromErrorString(errorMessage)
            }

            val tempOutDirList: ArrayList<String> = ArrayList()

            for (data in dataList) {
                // Get the original filename from the content provider URI
                var filename = getFileNameFromContentProvider(context, data)

                // Hack to fix bug where ContentResolver not returning filename correctly
                if (filename == null) {
                    if (intent.type != null && ("application/apkg" == intent.type || hasValidZipFile(context, data))) {
                        // Set a dummy filename if MIME type provided or is a valid zip file
                        filename = "unknown_filename.apkg"
                        Timber.w("Could not retrieve filename from ContentProvider, but was valid zip file so we try to continue")
                    } else {
                        Timber.e("Could not retrieve filename from ContentProvider or read content as ZipFile")
                        CrashReportService.sendExceptionReport(RuntimeException("Could not import apkg from ContentProvider"), "IntentHandler.java", "apkg import failed")
                        return ImportResult.fromErrorString(AnkiDroidApp.appResources.getString(R.string.import_error_content_provider, AnkiDroidApp.manualUrl + "#importing"))
                    }
                }
                if (!isValidPackageName(filename)) {
                    return if (isAnkiDatabase(filename)) {
                        // .anki2 files aren't supported by Anki Desktop, we should eventually support them, because we can
                        // but for now, show a "nice" error.
                        ImportResult.fromErrorString(context.resources.getString(R.string.import_error_load_imported_database))
                    } else {
                        // Don't import if file doesn't have an Anki package extension
                        ImportResult.fromErrorString(context.resources.getString(R.string.import_error_not_apkg_extension, filename))
                    }
                } else {
                    // Copy to temporary file
                    filename = ensureValidLength(filename)
                    val tempOutDir = Uri.fromFile(File(context.cacheDir, filename)).encodedPath!!
                    val errorMessage = if (copyFileToCache(context, data, tempOutDir)) null else context.getString(R.string.import_error_copy_to_cache)
                    // Show import dialog
                    if (errorMessage != null) {
                        CrashReportService.sendExceptionReport(RuntimeException("Error importing apkg file"), "IntentHandler.java", "apkg import failed")
                        return ImportResult.fromErrorString(errorMessage)
                    }
                    val validateZipResult = validateZipFile(context, tempOutDir)
                    if (validateZipResult != null) {
                        File(tempOutDir).delete()
                        return validateZipResult
                    }
                    tempOutDirList.add(tempOutDir)
                }
                sendShowImportFileDialogMsg(tempOutDirList)
            }
            return ImportResult.fromSuccess()
        }

        private fun validateZipFile(ctx: Context, filePath: String): ImportResult? {
            val file = File(filePath)
            var zf: ZipFile? = null
            try {
                zf = ZipFile(file)
            } catch (e: Exception) {
                Timber.w(e, "Failed to validate zip")
                return ImportResult.fromInvalidZip(ctx, file, e)
            } finally {
                if (zf != null) {
                    try {
                        zf.close()
                    } catch (e: IOException) {
                        Timber.w(e, "Failed to close zip")
                    }
                }
            }
            return null
        }

        private fun validateImportTypes(context: Context, dataList: ArrayList<Uri>): String? {
            var apkgCount = 0
            var colpkgCount = 0

            for (data in dataList) {
                var fileName = getFileNameFromContentProvider(context, data)
                when {
                    isDeckPackage(fileName) -> {
                        apkgCount += 1
                    }
                    isCollectionPackage(fileName) -> {
                        colpkgCount += 1
                    }
                }
            }

            if (apkgCount > 0 && colpkgCount > 0) {
                Timber.i("Both apkg & colpkg selected.")
                return context.resources.getString(R.string.import_error_colpkg_apkg)
            } else if (colpkgCount > 1) {
                Timber.i("Multiple colpkg files selected.")
                return context.resources.getString(R.string.import_error_multiple_colpkg)
            }

            return null
        }

        private fun isAnkiDatabase(filename: String?): Boolean {
            return filename != null && hasExtension(filename, "anki2")
        }

        private fun ensureValidLength(fileName: String): String {
            // #6137 - filenames can be too long when URLEncoded
            return try {
                val encoded = URLEncoder.encode(fileName, "UTF-8")
                if (encoded.length <= fileNameShorteningThreshold) {
                    Timber.d("No filename truncation necessary")
                    fileName
                } else {
                    Timber.d("Filename was longer than %d, shortening", fileNameShorteningThreshold)
                    // take 90 instead of 100 so we don't get the extension
                    val substringLength = fileNameShorteningThreshold - 10
                    val shortenedFileName = encoded.substring(0, substringLength) + "..." + getExtension(fileName)
                    Timber.d("Shortened filename '%s' to '%s'", fileName, shortenedFileName)
                    // if we don't decode, % is double-encoded
                    URLDecoder.decode(shortenedFileName, "UTF-8")
                }
            } catch (e: Exception) {
                Timber.w(e, "Failed to shorten file: %s", fileName)
                fileName
            }
        }

        @CheckResult
        private fun getExtension(fileName: String): String {
            val file = Uri.fromFile(File(fileName))
            return MimeTypeMap.getFileExtensionFromUrl(file.toString())
        }

        protected open fun getFileNameFromContentProvider(context: Context, data: Uri): String? {
            var filename: String? = null
            context.contentResolver.query(data, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null).use { cursor ->
                if (cursor != null && cursor.moveToFirst()) {
                    filename = cursor.getString(0)
                    Timber.d("handleFileImport() Importing from content provider: %s", filename)
                }
            }
            return filename
        }

        fun showImportUnsuccessfulDialog(activity: Activity, errorMessage: String?, exitActivity: Boolean) {
            Timber.e("showImportUnsuccessfulDialog() message %s", errorMessage)
            val title = activity.resources.getString(R.string.import_title_error)
            MaterialDialog(activity).show {
                title(text = title)
                message(text = errorMessage!!)
                positiveButton(R.string.dialog_ok) {
                    if (exitActivity) {
                        AnkiActivity.finishActivityWithFade(activity)
                    }
                }
            }
        }

        /**
         * Copy the data from the intent to a temporary file
         * @param data intent from which to get input stream
         * @param tempPath temporary path to store the cached file
         * @return whether or not copy was successful
         */
        protected open fun copyFileToCache(context: Context, data: Uri?, tempPath: String): Boolean {
            // Get an input stream to the data in ContentProvider
            val inputStream: InputStream? = try {
                context.contentResolver.openInputStream(data!!)
            } catch (e: FileNotFoundException) {
                Timber.e(e, "Could not open input stream to intent data")
                return false
            }
            // Check non-null
            @Suppress("FoldInitializerAndIfToElvis")
            if (inputStream == null) {
                return false
            }
            try {
                CompatHelper.compat.copyFile(inputStream, tempPath)
            } catch (e: IOException) {
                Timber.e(e, "Could not copy file to %s", tempPath)
                return false
            } finally {
                try {
                    inputStream.close()
                } catch (e: IOException) {
                    Timber.e(e, "Error closing input stream")
                }
            }
            return true
        }

        companion object {
            fun getUris(intent: Intent): ArrayList<Uri>? {
                if (intent.data == null) {
                    Timber.i("No intent data. Attempting to read clip data.")
                    if (intent.clipData == null || intent.clipData!!.itemCount == 0) {
                        return null
                    }
                    val clipUriList: ArrayList<Uri> = ArrayList()
                    // Iterate over clipUri & create clipUriList
                    // Pass clipUri list.
                    for (i in 0 until intent.clipData!!.itemCount) {
                        intent.clipData?.getItemAt(i)?.let { clipUriList.add(it.uri) }
                    }
                    return clipUriList
                }

                // If Uri is of scheme which is supported by ContentResolver, read the contents
                val intentUriScheme = intent.data!!.scheme
                return if (intentUriScheme == ContentResolver.SCHEME_CONTENT || intentUriScheme == ContentResolver.SCHEME_FILE || intentUriScheme == ContentResolver.SCHEME_ANDROID_RESOURCE) {
                    Timber.i("Attempting to read content from intent.")
                    arrayListOf(intent.data!!)
                } else {
                    null
                }
            }

            /**
             * Send a Message to AnkiDroidApp so that the DialogMessageHandler shows the Import apkg dialog.
             * @param pathList list of path(s) to apkg file which will be imported
             */
            private fun sendShowImportFileDialogMsg(pathList: ArrayList<String>) {
                // Get the filename from the path
                val f = File(pathList.first())
                val filename = f.name

                // Create a new message for DialogHandler so that we see the appropriate import dialog in DeckPicker
                val handlerMessage = Message.obtain()
                val msgData = Bundle()
                msgData.putStringArrayList("importPath", pathList)
                handlerMessage.data = msgData
                if (isCollectionPackage(filename)) {
                    // Show confirmation dialog asking to confirm import with replace when file called "collection.apkg"
                    handlerMessage.what = DialogHandler.MSG_SHOW_COLLECTION_IMPORT_REPLACE_DIALOG
                } else {
                    // Otherwise show confirmation dialog asking to confirm import with add
                    handlerMessage.what = DialogHandler.MSG_SHOW_COLLECTION_IMPORT_ADD_DIALOG
                }
                // Store the message in AnkiDroidApp message holder, which is loaded later in AnkiActivity.onResume
                DialogHandler.storeMessage(handlerMessage)
            }

            internal fun isDeckPackage(filename: String?): Boolean {
                return filename != null && filename.lowercase(Locale.ROOT).endsWith(".apkg") && "collection.apkg" != filename
            }

            fun hasExtension(filename: String, extension: String?): Boolean {
                val fileParts = filename.split("\\.".toRegex()).toTypedArray()
                if (fileParts.size < 2) {
                    return false
                }
                val extensionSegment = fileParts[fileParts.size - 1]
                // either "apkg", or "apkg (1)".
                // COULD_BE_BETTE: accepts .apkgaa"
                return extensionSegment.lowercase(Locale.ROOT).startsWith(extension!!)
            }

            /**
             * Check if the InputStream is to a valid non-empty zip file
             * @param data uri from which to get input stream
             * @return whether or not valid zip file
             */
            private fun hasValidZipFile(context: Context, data: Uri?): Boolean {
                // Get an input stream to the data in ContentProvider
                var inputStream: InputStream? = null
                try {
                    inputStream = context.contentResolver.openInputStream(data!!)
                } catch (e: FileNotFoundException) {
                    Timber.e(e, "Could not open input stream to intent data")
                }
                // Make sure it's not null
                if (inputStream == null) {
                    Timber.e("Could not open input stream to intent data")
                    return false
                }
                // Open zip input stream
                val zis = ZipInputStream(inputStream)
                var ok = false
                try {
                    try {
                        val ze = zis.nextEntry
                        if (ze != null) {
                            // set ok flag to true if there are any valid entries in the zip file
                            ok = true
                        }
                    } catch (e: Exception) {
                        // don't set ok flag
                        Timber.d(e, "Error checking if provided file has a zip entry")
                    }
                } finally {
                    // close the input streams
                    try {
                        zis.close()
                        inputStream.close()
                    } catch (e: Exception) {
                        Timber.d(e, "Error closing the InputStream")
                    }
                }
                return ok
            }
        }
    }

    class ImportResult(val humanReadableMessage: String?) {
        val isSuccess: Boolean
            get() = humanReadableMessage == null

        companion object {
            fun fromErrorString(message: String?): ImportResult {
                return ImportResult(message)
            }

            fun fromSuccess(): ImportResult {
                return ImportResult(null)
            }

            fun fromInvalidZip(ctx: Context, file: File, e: Exception): ImportResult {
                return fromErrorString(getInvalidZipException(ctx, file, e))
            }

            private fun getInvalidZipException(ctx: Context, @Suppress("UNUSED_PARAMETER") file: File, e: Exception): String {
                // This occurs when there is random corruption in a zip file
                if (e is IOException && "central directory is empty, can't expand corrupt archive." == e.message) {
                    return ctx.getString(R.string.import_error_corrupt_zip, e.getLocalizedMessage())
                }
                // 7050 - this occurs when a file is truncated at the end (partial download/corrupt).
                if (e is ZipException && "archive is not a ZIP archive" == e.message) {
                    return ctx.getString(R.string.import_error_corrupt_zip, e.getLocalizedMessage())
                }

                // If we don't have a good string, send a silent exception that we can better handle this in the future
                CrashReportService.sendExceptionReport(e, "Import - invalid zip", "improve UI message here", true)
                return ctx.getString(R.string.import_log_failed_unzip, e.localizedMessage)
            }
        }
    }
}
