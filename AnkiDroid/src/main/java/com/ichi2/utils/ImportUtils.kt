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
import android.os.Message
import android.provider.OpenableColumns
import androidx.annotation.CheckResult
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.os.bundleOf
import com.ichi2.anki.*
import com.ichi2.anki.dialogs.DialogHandler
import com.ichi2.anki.dialogs.DialogHandlerMessage
import com.ichi2.anki.dialogs.ImportDialog
import com.ichi2.compat.CompatHelper
import org.jetbrains.annotations.Contract
import timber.log.Timber
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.*

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

    fun getFileCachedCopy(context: Context, uri: Uri): String? {
        return FileImporter().getFileCachedCopy(context, uri)
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
            val importPathUri = getDataUri(intent)
            return if (importPathUri != null) {
                handleContentProviderFile(context, intent, importPathUri)
            } else {
                ImportResult.fromErrorString(context.getString(R.string.import_error_handle_exception))
            }
        }

        fun getFileCachedCopy(context: Context, uri: Uri): String? {
            val filename = ensureValidLength(getFileNameFromContentProvider(context, uri) ?: return null)
            val tempPath = Uri.fromFile(File(context.cacheDir, filename)).encodedPath!!
            return if (copyFileToCache(context, uri, tempPath)) {
                tempPath
            } else {
                null
            }
        }

        /**
         * Makes a cached copy of the file selected on [intent] and returns its path
         */
        fun getFileCachedCopy(context: Context, intent: Intent): String? {
            val uri = getDataUri(intent) ?: return null
            return getFileCachedCopy(context, uri)
        }

        private fun handleContentProviderFile(
            context: Context,
            intent: Intent,
            importPathUri: Uri
        ): ImportResult {
            // Note: intent.getData() can be null. Use data instead.
            if (!isValidImportType(context, importPathUri)) {
                return ImportResult.fromErrorString(context.getString(R.string.import_log_no_apkg))
            }
            // Get the original filename from the content provider URI
            var filename = getFileNameFromContentProvider(context, importPathUri)
            // Hack to fix bug where ContentResolver not returning filename correctly
            if (filename == null) {
                if (intent.type == "application/apkg" || intent.type == "application/zip") {
                    // Set a dummy filename if MIME type provided or is a valid zip file
                    filename = "unknown_filename.apkg"
                    Timber.w("Could not retrieve filename from ContentProvider, but was valid zip file so we try to continue")
                } else {
                    Timber.e("Could not retrieve filename from ContentProvider")
                    CrashReportService.sendExceptionReport(
                        RuntimeException("Could not import apkg from ContentProvider"),
                        "IntentHandler.java",
                        "apkg import failed; mime type ${intent.type}"
                    )
                    return ImportResult.fromErrorString(AnkiDroidApp.appResources.getString(R.string.import_error_content_provider, AnkiDroidApp.manualUrl + "#importing"))
                }
            }
            val tempOutDir: String
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
                tempOutDir = Uri.fromFile(File(context.cacheDir, filename)).encodedPath!!
                val errorMessage = if (copyFileToCache(context, importPathUri, tempOutDir)) {
                    null
                } else {
                    context.getString(R.string.import_error_copy_to_cache)
                }
                // Show import dialog
                if (errorMessage != null) {
                    CrashReportService.sendExceptionReport(
                        RuntimeException("Error importing apkg file"),
                        "IntentHandler.java",
                        "apkg import failed"
                    )
                    return ImportResult.fromErrorString(errorMessage)
                }
            }
            sendShowImportFileDialogMsg(tempOutDir)
            return ImportResult.fromSuccess()
        }

        private fun isValidImportType(context: Context, importPathUri: Uri): Boolean {
            val fileName = getFileNameFromContentProvider(context, importPathUri)
            return when {
                isDeckPackage(fileName) -> true
                isCollectionPackage(fileName) -> true
                else -> false
            }
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
            return AssetHelper.getFileExtensionFromFilePath(file.toString())
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
            AlertDialog.Builder(ContextThemeWrapper(activity, R.style.AlertDialogStyle)).show {
                title(text = title)
                message(text = errorMessage!!)
                setCancelable(false)
                positiveButton(R.string.dialog_ok) {
                    if (exitActivity) {
                        activity.finish()
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
            val inputStream: InputStream = try {
                context.contentResolver.openInputStream(data!!)
            } catch (e: FileNotFoundException) {
                Timber.e(e, "Could not open input stream to intent data")
                return false
            } ?: return false
            // Check non-null
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
            fun getDataUri(intent: Intent): Uri? {
                if (intent.data == null) {
                    Timber.i("No intent data. Attempting to read clip data.")
                    if (intent.clipData == null || intent.clipData!!.itemCount == 0) {
                        return null
                    }
                    return intent.clipData?.getItemAt(0)?.uri
                }
                // If Uri is of scheme which is supported by ContentResolver, read the contents
                val intentUriScheme = intent.data!!.scheme
                return if (intentUriScheme == ContentResolver.SCHEME_CONTENT || intentUriScheme == ContentResolver.SCHEME_FILE || intentUriScheme == ContentResolver.SCHEME_ANDROID_RESOURCE) {
                    Timber.i("Attempting to read data from intent.")
                    intent.data
                } else {
                    null
                }
            }

            /**
             * Send a Message to AnkiDroidApp so that the DialogMessageHandler shows the Import apkg dialog.
             * @param importPath path of to apkg file which will be imported
             */
            private fun sendShowImportFileDialogMsg(importPath: String) {
                // Get the filename from the path
                val filename = File(importPath).name

                val dialogMessage = if (isCollectionPackage(filename)) {
                    CollectionImportReplace(importPath)
                } else {
                    CollectionImportAdd(importPath)
                }
                // Store the message in AnkiDroidApp message holder, which is loaded later in AnkiActivity.onResume
                DialogHandler.storeMessage(dialogMessage.toMessage())
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
        }
    }

    /** Show confirmation dialog asking to confirm import with replace when file called "collection.apkg" */
    class CollectionImportReplace(private val importPath: String) : DialogHandlerMessage(
        which = WhichDialogHandler.MSG_SHOW_COLLECTION_IMPORT_REPLACE_DIALOG,
        analyticName = "ImportReplaceDialog"
    ) {
        override fun handleAsyncMessage(deckPicker: DeckPicker) {
            // Handle import of collection package APKG
            deckPicker.showImportDialog(ImportDialog.DIALOG_IMPORT_REPLACE_CONFIRM, importPath)
        }

        override fun toMessage(): Message = Message.obtain().apply {
            data = bundleOf("importPath" to importPath)
            what = this@CollectionImportReplace.what
        }

        companion object {
            fun fromMessage(message: Message): CollectionImportReplace =
                CollectionImportReplace(message.data.getString("importPath")!!)
        }
    }

    /** Show confirmation dialog asking to confirm import with add */
    class CollectionImportAdd(private val importPath: String) : DialogHandlerMessage(
        WhichDialogHandler.MSG_SHOW_COLLECTION_IMPORT_ADD_DIALOG,
        "ImportAddDialog"
    ) {
        override fun handleAsyncMessage(deckPicker: DeckPicker) {
            // Handle import of deck package APKG
            deckPicker.showImportDialog(ImportDialog.DIALOG_IMPORT_ADD_CONFIRM, importPath)
        }

        override fun toMessage(): Message = Message.obtain().apply {
            data = bundleOf("importPath" to importPath)
            what = this@CollectionImportAdd.what
        }

        companion object {
            fun fromMessage(message: Message): CollectionImportAdd =
                CollectionImportAdd(message.data.getString("importPath")!!)
        }
    }
}
