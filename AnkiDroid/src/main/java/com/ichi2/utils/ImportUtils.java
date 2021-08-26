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

package com.ichi2.utils;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Message;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.webkit.MimeTypeMap;

import com.afollestad.materialdialogs.MaterialDialog;
import com.ichi2.anki.AnkiActivity;
import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.R;
import com.ichi2.anki.dialogs.DialogHandler;
import com.ichi2.compat.CompatHelper;

import org.apache.commons.compress.archivers.zip.ZipFile;
import org.jetbrains.annotations.Contract;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;

import androidx.annotation.CheckResult;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import timber.log.Timber;

public class ImportUtils {

    /* A filename should be shortened if over this threshold */
    private static final int fileNameShorteningThreshold = 100;


    /**
     * This code is used in multiple places to handle package imports
     *
     * @param context for use in resource resolution and path finding
     * @param intent contains the file to import
     * @return null if successful, otherwise error message
     */
    @NonNull
    public static ImportResult handleFileImport(Context context, Intent intent) {
        return new FileImporter().handleFileImport(context, intent);
    }

    public static void showImportUnsuccessfulDialog(Activity activity, String errorMessage, boolean exitActivity) {
        new FileImporter().showImportUnsuccessfulDialog(activity, errorMessage, exitActivity);
    }

    public static boolean isCollectionPackage(String filename) {
        return filename != null && (filename.toLowerCase(Locale.ROOT).endsWith(".colpkg") || "collection.apkg".equals(filename));
    }

    /** @return Whether the file is either a deck, or a collection package */
    @Contract("null -> false")
    public static boolean isValidPackageName(@Nullable String filename) {
        return FileImporter.isDeckPackage(filename) || isCollectionPackage(filename);
    }

    /**
     * Whether importUtils can handle the given intent
     * Caused by #6312 - A launcher was sending ACTION_VIEW instead of ACTION_MAIN
     */
    public static boolean isInvalidViewIntent(@NonNull Intent intent) {
        return intent.getData() == null && intent.getClipData() == null;
    }
    
    public static boolean isFileAValidDeck(String fileName) {
        return FileImporter.hasExtension(fileName, "apkg") || FileImporter.hasExtension(fileName, "colpkg");
    }


    @SuppressWarnings("WeakerAccess")
    protected static class FileImporter {
        /**
         * This code is used in multiple places to handle package imports
         *
         * @param context for use in resource resolution and path finding
         * @param intent contains the file to import
         * @return null if successful, otherwise error message
         */
        @NonNull
        public ImportResult handleFileImport(Context context, Intent intent) {
            // This intent is used for opening apkg package files
            // We want to go immediately to DeckPicker, clearing any history in the process
            Timber.i("IntentHandler/ User requested to view a file");
            String extras = intent.getExtras() == null ? "none" : TextUtils.join(", ", intent.getExtras().keySet());
            Timber.i("Intent: %s. Data: %s", intent, extras);

            try {
                return handleFileImportInternal(context, intent);
            } catch (Exception e) {
                AnkiDroidApp.sendExceptionReport(e, "handleFileImport");
                Timber.e(e, "failed to handle import intent");
                return ImportResult.fromErrorString(context.getString(R.string.import_error_exception, e.getLocalizedMessage()));
            }
        }

        //Added to remove exception handlers
        @NonNull
        private ImportResult handleFileImportInternal(Context context, Intent intent) {
            if (intent.getData() == null) {
                Timber.i("No intent data. Attempting to read clip data.");
                if (intent.getClipData() == null
                        || intent.getClipData().getItemCount() == 0) {
                    return ImportResult.fromErrorString(context.getString(R.string.import_error_unhandled_request));
                }
                Uri clipUri = intent.getClipData().getItemAt(0).getUri();
                return handleContentProviderFile(context, intent, clipUri);
            }

            // If Uri is of scheme which is supported by ContentResolver, read the contents
            String intentUriScheme = intent.getData().getScheme();
            if (intentUriScheme.equals(ContentResolver.SCHEME_CONTENT) || intentUriScheme.equals(ContentResolver.SCHEME_FILE)
                    || intentUriScheme.equals(ContentResolver.SCHEME_ANDROID_RESOURCE)) {
                Timber.i("Attempting to read content from intent.");
                return handleContentProviderFile(context, intent, intent.getData());
            } else {
                return ImportResult.fromErrorString(context.getResources().getString(R.string.import_error_unhandled_scheme, intent.getData()));
            }
        }


        @NonNull
        private ImportResult handleContentProviderFile(Context context, Intent intent, Uri data) {
            //Note: intent.getData() can be null. Use data instead.

            // Get the original filename from the content provider URI
            String filename = getFileNameFromContentProvider(context, data);

            // Hack to fix bug where ContentResolver not returning filename correctly
            if (filename == null) {
                if (intent.getType() != null && ("application/apkg".equals(intent.getType()) || hasValidZipFile(context, data))) {
                    // Set a dummy filename if MIME type provided or is a valid zip file
                    filename = "unknown_filename.apkg";
                    Timber.w("Could not retrieve filename from ContentProvider, but was valid zip file so we try to continue");
                } else {
                    Timber.e("Could not retrieve filename from ContentProvider or read content as ZipFile");
                    AnkiDroidApp.sendExceptionReport(new RuntimeException("Could not import apkg from ContentProvider"), "IntentHandler.java", "apkg import failed");
                    return ImportResult.fromErrorString(AnkiDroidApp.getAppResources().getString(R.string.import_error_content_provider, AnkiDroidApp.getManualUrl() + "#importing"));
                }
            }

            if (!isValidPackageName(filename)) {
                if (isAnkiDatabase(filename)) {
                    //.anki2 files aren't supported by Anki Desktop, we should eventually support them, because we can
                    //but for now, show a "nice" error.
                    return ImportResult.fromErrorString(context.getResources().getString(R.string.import_error_load_imported_database));
                } else {
                    // Don't import if file doesn't have an Anki package extension
                    return ImportResult.fromErrorString(context.getResources().getString(R.string.import_error_not_apkg_extension, filename));
                }
            } else {
                // Copy to temporary file
                filename = ensureValidLength(filename);
                String tempOutDir = Uri.fromFile(new File(context.getCacheDir(), filename)).getEncodedPath();
                String errorMessage = copyFileToCache(context, data, tempOutDir) ? null : context.getString(R.string.import_error_copy_file_to_cache);
                // Show import dialog
                if (errorMessage != null) {
                    AnkiDroidApp.sendExceptionReport(new RuntimeException("Error importing apkg file"), "IntentHandler.java", "apkg import failed");
                    return ImportResult.fromErrorString(errorMessage);
                }

                ImportResult validateZipResult = validateZipFile(context, tempOutDir);
                if (validateZipResult != null) {
                    //noinspection ResultOfMethodCallIgnored
                    new File(tempOutDir).delete();
                    return validateZipResult;
                }

                sendShowImportFileDialogMsg(tempOutDir);
                return ImportResult.fromSuccess();
            }
        }

        @Nullable
        protected ImportResult validateZipFile(Context ctx, String filePath) {
            File file = new File(filePath);
            ZipFile zf = null;
            try {
                zf = new ZipFile(file);
            } catch (Exception e) {
                Timber.w(e, "Failed to validate zip");
                return ImportResult.fromInvalidZip(ctx, file, e);
            } finally {
                if (zf != null) {
                    try {
                        zf.close();
                    } catch (IOException e) {
                        Timber.w(e, "Failed to close zip");
                    }
                }
            }
            return null;
        }


        private boolean isAnkiDatabase(String filename) {
            return filename != null && hasExtension(filename, "anki2");
        }


        private String ensureValidLength(String fileName) {
            //#6137 - filenames can be too long when URLEncoded
            try {
                String encoded = URLEncoder.encode(fileName, "UTF-8");

                if (encoded.length() <= fileNameShorteningThreshold) {
                    Timber.d("No filename truncation necessary");
                    return fileName;
                } else {
                    Timber.d("Filename was longer than %d, shortening", fileNameShorteningThreshold);
                    //take 90 instead of 100 so we don't get the extension
                    int substringLength = fileNameShorteningThreshold - 10;
                    String shortenedFileName = encoded.substring(0, substringLength) + "..." + getExtension(fileName);
                    Timber.d("Shortened filename '%s' to '%s'", fileName, shortenedFileName);
                    //if we don't decode, % is double-encoded
                    return URLDecoder.decode(shortenedFileName, "UTF-8");
                }
            } catch (Exception e) {
                Timber.w(e, "Failed to shorten file: %s", fileName);
                return fileName;
            }
        }

        @CheckResult
        private String getExtension(String fileName) {
            Uri file = Uri.fromFile(new File(fileName));
            return MimeTypeMap.getFileExtensionFromUrl(file.toString());
        }


        @Nullable
        protected String getFileNameFromContentProvider(Context context, Uri data) {
            String filename = null;
            try (Cursor cursor = context.getContentResolver().query(data, new String[] {OpenableColumns.DISPLAY_NAME}, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    filename = cursor.getString(0);
                    Timber.d("handleFileImport() Importing from content provider: %s", filename);
                }
            }
            return filename;
        }


        public void showImportUnsuccessfulDialog(Activity activity, String errorMessage, boolean exitActivity) {
            Timber.e("showImportUnsuccessfulDialog() message %s", errorMessage);
            String title = activity.getResources().getString(R.string.import_log_no_apkg);
            new MaterialDialog.Builder(activity)
                    .title(title)
                    .content(errorMessage)
                    .positiveText(activity.getResources().getString(R.string.dialog_ok))
                    .onPositive((dialog, which) -> {
                        if (exitActivity) {
                            AnkiActivity.finishActivityWithFade(activity);
                        }
                    })
                    .build().show();
        }


        /**
         * Send a Message to AnkiDroidApp so that the DialogMessageHandler shows the Import apkg dialog.
         * @param path path to apkg file which will be imported
         */
        private static void sendShowImportFileDialogMsg(String path) {
            // Get the filename from the path
            File f = new File(path);
            String filename = f.getName();

            // Create a new message for DialogHandler so that we see the appropriate import dialog in DeckPicker
            Message handlerMessage = Message.obtain();
            Bundle msgData = new Bundle();
            msgData.putString("importPath", path);
            handlerMessage.setData(msgData);
            if (isCollectionPackage(filename)) {
                // Show confirmation dialog asking to confirm import with replace when file called "collection.apkg"
                handlerMessage.what = DialogHandler.MSG_SHOW_COLLECTION_IMPORT_REPLACE_DIALOG;
            } else {
                // Otherwise show confirmation dialog asking to confirm import with add
                handlerMessage.what = DialogHandler.MSG_SHOW_COLLECTION_IMPORT_ADD_DIALOG;
            }
            // Store the message in AnkiDroidApp message holder, which is loaded later in AnkiActivity.onResume
            DialogHandler.storeMessage(handlerMessage);
        }

        private static boolean isDeckPackage(String filename) {
            return filename != null && filename.toLowerCase(Locale.ROOT).endsWith(".apkg") && !"collection.apkg".equals(filename);
        }


        public static boolean hasExtension(@NonNull String filename, String extension) {
            String[] fileParts = filename.split("\\.");
            if (fileParts.length < 2) {
                return false;
            }
            String extensionSegment = fileParts[fileParts.length - 1];
            //either "apkg", or "apkg (1)".
            // COULD_BE_BETTE: accepts .apkgaa"
            return extensionSegment.toLowerCase(Locale.ROOT).startsWith(extension);
        }


        /**
         * Check if the InputStream is to a valid non-empty zip file
         * @param data uri from which to get input stream
         * @return whether or not valid zip file
         */
        private static boolean hasValidZipFile(Context context, Uri data) {
            // Get an input stream to the data in ContentProvider
            InputStream in = null;
            try {
                in = context.getContentResolver().openInputStream(data);
            } catch (FileNotFoundException e) {
                Timber.e(e, "Could not open input stream to intent data");
            }
            // Make sure it's not null
            if (in == null) {
                Timber.e("Could not open input stream to intent data");
                return false;
            }
            // Open zip input stream
            ZipInputStream zis = new ZipInputStream(in);
            boolean ok = false;
            try {
                try {
                    ZipEntry ze = zis.getNextEntry();
                    if (ze != null) {
                        // set ok flag to true if there are any valid entries in the zip file
                        ok = true;
                    }
                } catch (Exception e) {
                    // don't set ok flag
                    Timber.d(e, "Error checking if provided file has a zip entry");
                }
            } finally {
                // close the input streams
                try {
                    zis.close();
                    in.close();
                } catch (Exception e) {
                    Timber.d(e, "Error closing the InputStream");
                }
            }
            return ok;
        }


        /**
         * Copy the data from the intent to a temporary file
         * @param data intent from which to get input stream
         * @param tempPath temporary path to store the cached file
         * @return whether or not copy was successful
         */
        protected boolean copyFileToCache(Context context, Uri data, String tempPath) {
            // Get an input stream to the data in ContentProvider
            InputStream in;
            try {
                in = context.getContentResolver().openInputStream(data);
            } catch (FileNotFoundException e) {
                Timber.e(e, "Could not open input stream to intent data");
                return false;
            }
            // Check non-null
            if (in == null) {
                return false;
            }

            try {
                CompatHelper.getCompat().copyFile(in, tempPath);
            } catch (IOException e) {
                Timber.e(e, "Could not copy file to %s", tempPath);
                return false;
            } finally {
                try {
                    in.close();
                } catch (IOException e) {
                    Timber.e(e, "Error closing input stream");
                }
            }
            return true;
        }
    }

    public static class ImportResult {
        private final String mMessage;


        public ImportResult(String message) {
            this.mMessage = message;
        }

        public static ImportResult fromErrorString(String message) {
            return new ImportResult(message);
        }

        public static ImportResult fromSuccess() {
            return new ImportResult(null);
        }


        public static ImportResult fromInvalidZip(Context ctx, File file, Exception e) {
            return fromErrorString(getInvalidZipException(ctx, file, e));
        }


        private static String getInvalidZipException(Context ctx, @SuppressWarnings( {"unused", "RedundantSuppression"}) File file, Exception e) {
            // This occurs when there is random corruption in a zip file
            if (e instanceof IOException && "central directory is empty, can't expand corrupt archive.".equals(e.getMessage())) {
                return ctx.getString(R.string.import_error_corrupt_zip, e.getLocalizedMessage());
            }
            // 7050 - this occurs when a file is truncated at the end (partial download/corrupt).
            if (e instanceof ZipException && "archive is not a ZIP archive".equals(e.getMessage())) {
                return ctx.getString(R.string.import_error_corrupt_zip, e.getLocalizedMessage());
            }

            // If we don't have a good string, send a silent exception that we can better handle this in the future
            AnkiDroidApp.sendExceptionReport(e, "Import - invalid zip", "improve UI message here", true);
            return ctx.getString(R.string.import_log_failed_unzip, e.getLocalizedMessage());
        }


        public boolean isSuccess() {
            return mMessage == null;
        }

        public String getHumanReadableMessage() {
            return mMessage;
        }
    }
}
