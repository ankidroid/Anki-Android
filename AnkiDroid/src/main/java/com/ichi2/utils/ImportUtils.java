package com.ichi2.utils;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Message;
import android.provider.OpenableColumns;

import com.afollestad.materialdialogs.MaterialDialog;
import com.ichi2.anki.AnkiActivity;
import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.R;
import com.ichi2.anki.dialogs.DialogHandler;
import com.ichi2.compat.CompatHelper;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import timber.log.Timber;

public class ImportUtils {


    /**
     * This code is used in multiple places to handle package imports
     *
     * @param context for use in resource resolution and path finding
     * @param intent contains the file to import
     * @return null if successful, otherwise error message
     */
    @SuppressWarnings("PMD.NPathComplexity")
    public static String handleFileImport(Context context, Intent intent) {
        // This intent is used for opening apkg package files
        // We want to go immediately to DeckPicker, clearing any history in the process
        Timber.i("IntentHandler/ User requested to view a file");
        String errorMessage = null;
        // If the file is being sent from a content provider we need to read the content before we can open the file
        if (intent.getData().getScheme().equals("content")) {
            // Get the original filename from the content provider URI
            String filename = null;
            Cursor cursor = null;
            try {
                cursor = context.getContentResolver().query(intent.getData(), new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null);
                if (cursor != null && cursor.moveToFirst()) {
                    filename = cursor.getString(0);
                    Timber.d("handleFileImport() Importing from content provider: %s", filename);
                }
            } finally {
                if (cursor != null)
                    cursor.close();
            }

            // Hack to fix bug where ContentResolver not returning filename correctly
            if (filename == null) {
                if (intent.getType() != null && (intent.getType().equals("application/apkg") || ImportUtils.hasValidZipFile(context, intent))) {
                    // Set a dummy filename if MIME type provided or is a valid zip file
                    filename = "unknown_filename.apkg";
                    Timber.w("Could not retrieve filename from ContentProvider, but was valid zip file so we try to continue");
                } else {
                    Timber.e("Could not retrieve filename from ContentProvider or read content as ZipFile");
                    AnkiDroidApp.sendExceptionReport(new RuntimeException("Could not import apkg from ContentProvider"), "IntentHandler.java", "apkg import failed");
                    errorMessage = AnkiDroidApp.getAppResources().getString(R.string.import_error_content_provider, AnkiDroidApp.getManualUrl() + "#importing");
                }
            }

            if (!isValidPackageName(filename)) {
                // Don't import if file doesn't have an Anki package extension
                errorMessage = context.getResources().getString(R.string.import_error_not_apkg_extension, filename);
            } else if (filename != null) {
                // Copy to temporary file
                String tempOutDir = Uri.fromFile(new File(context.getCacheDir(), filename)).getEncodedPath();
                errorMessage = ImportUtils.copyFileToCache(context, intent, tempOutDir) ? null : "copyFileToCache() failed";
                // Show import dialog
                if (errorMessage == null) {
                    ImportUtils.sendShowImportFileDialogMsg(tempOutDir);
                } else {
                    AnkiDroidApp.sendExceptionReport(new RuntimeException("Error importing apkg file"), "IntentHandler.java", "apkg import failed");
                }
            }
        } else if (intent.getData().getScheme().equals("file")) {
            // When the VIEW intent is sent as a file, we can open it directly without copying from content provider
            String filename = intent.getData().getPath();
            Timber.d("Importing regular file: %s", filename);
            if (isValidPackageName(filename)) {
                // If file has apkg extension then send message to show Import dialog
                ImportUtils.sendShowImportFileDialogMsg(filename);
            } else {
                errorMessage = context.getResources().getString(R.string.import_error_not_apkg_extension, filename);
            }
        }
        return errorMessage;
    }


    public static void showImportUnsuccessfulDialog(Activity activity, String errorMessage, boolean exitActivity) {
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

    public static boolean isCollectionPackage(String filename) {
        return filename != null && (filename.toLowerCase().endsWith(".colpkg") || filename.equals("collection.apkg"));
    }

    private static boolean isDeckPackage(String filename) {
        return filename != null && filename.toLowerCase().endsWith(".apkg") && !filename.equals("collection.apkg");
    }

    public static boolean isValidPackageName(String filename) {
        return isDeckPackage(filename) || isCollectionPackage(filename);
    }

    /**
     * Check if the InputStream is to a valid non-empty zip file
     * @param intent intent from which to get input stream
     * @return whether or not valid zip file
     */
    private static boolean hasValidZipFile(Context context, Intent intent) {
        // Get an input stream to the data in ContentProvider
        InputStream in = null;
        try {
            in = context.getContentResolver().openInputStream(intent.getData());
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
     * @param intent intent from which to get input stream
     * @param tempPath temporary path to store the cached file
     * @return whether or not copy was successful
     */
    private static boolean copyFileToCache(Context context, Intent intent, String tempPath) {
        // Get an input stream to the data in ContentProvider
        InputStream in;
        try {
            in = context.getContentResolver().openInputStream(intent.getData());
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
