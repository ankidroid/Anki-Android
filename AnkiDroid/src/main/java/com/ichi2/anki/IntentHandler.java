package com.ichi2.anki;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Message;
import android.provider.OpenableColumns;
import android.support.v4.content.IntentCompat;

import com.afollestad.materialdialogs.MaterialDialog;

import com.ichi2.anim.ActivityTransitionAnimation;
import com.ichi2.anki.dialogs.DialogHandler;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import timber.log.Timber;

/**
 * Class which handles how the application responds to different intents, forcing it to always be single task,
 * but allowing custom behavior depending on the intent
 * 
 * @author Tim
 *
 */

public class IntentHandler extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.progress_bar);
        Intent intent = getIntent();
        Timber.v(intent.toString());
        Intent reloadIntent = new Intent(this, DeckPicker.class);
        reloadIntent.setDataAndType(getIntent().getData(), getIntent().getType());
        String action = intent.getAction();
        if (Intent.ACTION_VIEW.equals(action)) {
            // This intent is used for opening apkg package files
            // We want to go immediately to DeckPicker, clearing any history in the process
            Timber.i("IntentHandler/ User requested to view a file");
            boolean successful = false;
            String errorMessage = getResources().getString(R.string.import_error_content_provider, AnkiDroidApp.getManualUrl() + "#importing");
            // If the file is being sent from a content provider we need to read the content before we can open the file
            if (intent.getData().getScheme().equals("content")) {
                // Get the original filename from the content provider URI
                String filename = null;
                Cursor cursor = null;
                try {
                    cursor = this.getContentResolver().query(intent.getData(), new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null);
                    if (cursor != null && cursor.moveToFirst()) {
                        filename = cursor.getString(0);
                    }
                } finally {
                    if (cursor != null)
                        cursor.close();
                }

                // Hack to fix bug where ContentResolver not returning filename correctly
                if (filename == null) {
                    if (intent.getType().equals("application/apkg") || hasValidZipFile(intent)) {
                        // Set a dummy filename if MIME type provided or is a valid zip file
                        filename = "unknown_filename.apkg";
                        Timber.w("Could not retrieve filename from ContentProvider, but was valid zip file so we try to continue");
                    } else {
                        Timber.e("Could not retrieve filename from ContentProvider or read content as ZipFile");
                        AnkiDroidApp.sendExceptionReport(new RuntimeException("Could not import apkg from ContentProvider"), "IntentHandler.java", "apkg import failed");
                    }
                }

                if (filename != null && !filename.toLowerCase().endsWith(".apkg")) {
                    // Don't import if not apkg file
                    errorMessage = getResources().getString(R.string.import_error_not_apkg_extension, filename);
                } else if (filename != null) {
                    // Copy to temporary file
                    String tempOutDir = Uri.fromFile(new File(getCacheDir(), filename)).getEncodedPath();
                    successful = copyFileToCache(intent, tempOutDir);
                    // Show import dialog
                    if (successful) {
                        sendShowImportFileDialogMsg(tempOutDir);
                    } else {
                        AnkiDroidApp.sendExceptionReport(new RuntimeException("Error importing apkg file"), "IntentHandler.java", "apkg import failed");
                    }
                }
            } else if (intent.getData().getScheme().equals("file")) {
                // When the VIEW intent is sent as a file, we can open it directly without copying from content provider                
                String filename = intent.getData().getPath();
                if (filename != null && filename.endsWith(".apkg")) {
                    // If file has apkg extension then send message to show Import dialog
                    sendShowImportFileDialogMsg(filename);
                    successful = true;
                } else {
                    errorMessage = getResources().getString(R.string.import_error_not_apkg_extension, filename);
                }
            }
            // Start DeckPicker if we correctly processed ACTION_VIEW
            if (successful) {
                reloadIntent.setAction(action);
                reloadIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(reloadIntent);
                finishWithFade();
            } else {
                // Don't import the file if it didn't load properly or doesn't have apkg extension
                //Themes.showThemedToast(this, getResources().getString(R.string.import_log_no_apkg), true);
                String title = getResources().getString(R.string.import_log_no_apkg);
                new MaterialDialog.Builder(this)
                        .title(title)
                        .content(errorMessage)
                        .positiveText(getResources().getString(R.string.dialog_ok))
                        .callback(new MaterialDialog.ButtonCallback() {
                            @Override
                            public void onPositive(MaterialDialog dialog) {
                                finishWithFade();
                            }
                        })
                        .build().show();
            }
        } else if ("com.ichi2.anki.DO_SYNC".equals(action)) {
            sendDoSyncMsg();
            reloadIntent.setAction(action);
            reloadIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(reloadIntent);
            finishWithFade();
        } else {
            // Launcher intents should start DeckPicker if no other task exists,
            // otherwise go to previous task
            reloadIntent.setAction(Intent.ACTION_MAIN);
            reloadIntent.addCategory(Intent.CATEGORY_LAUNCHER);
            reloadIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | IntentCompat.FLAG_ACTIVITY_CLEAR_TASK);
            startActivityIfNeeded(reloadIntent, 0);
            finishWithFade();
        }
    }


    /**
     * Send a Message to AnkiDroidApp so that the DialogMessageHandler shows the Import apkg dialog.
     * @param path path to apkg file which will be imported
     */
    private void sendShowImportFileDialogMsg(String path) {
        // Get the filename from the path
        File f = new File(path);
        String filename = f.getName();

        // Create a new message for DialogHandler so that we see the appropriate import dialog in DeckPicker
        Message handlerMessage = Message.obtain();
        Bundle msgData = new Bundle();
        msgData.putString("importPath", path);
        handlerMessage.setData(msgData);
        if (filename.equals("collection.apkg")) {
            // Show confirmation dialog asking to confirm import with replace when file called "collection.apkg"
            handlerMessage.what = DialogHandler.MSG_SHOW_COLLECTION_IMPORT_REPLACE_DIALOG;
        } else {
            // Otherwise show confirmation dialog asking to confirm import with add
            handlerMessage.what = DialogHandler.MSG_SHOW_COLLECTION_IMPORT_ADD_DIALOG;
        }
        // Store the message in AnkiDroidApp message holder, which is loaded later in AnkiActivity.onResume
        DialogHandler.storeMessage(handlerMessage);
    }

    /**
     * Send a Message to AnkiDroidApp so that the DialogMessageHandler forces a sync
     */
    private void sendDoSyncMsg() {
        // Create a new message for DialogHandler
        Message handlerMessage = Message.obtain();
        handlerMessage.what = DialogHandler.MSG_DO_SYNC;
        // Store the message in AnkiDroidApp message holder, which is loaded later in AnkiActivity.onResume
        DialogHandler.storeMessage(handlerMessage);
    }

    /** Finish Activity using FADE animation **/
    private void finishWithFade() {
    	finish();
    	ActivityTransitionAnimation.slide(this, ActivityTransitionAnimation.UP);
    }

    /**
     * Check if the InputStream is to a valid non-empty zip file
     * @param intent intent from which to get input stream
     * @return whether or not valid zip file
     */
    private boolean hasValidZipFile(Intent intent) {
        // Get an input stream to the data in ContentProvider
        InputStream in = null;
        try {
            in = getContentResolver().openInputStream(intent.getData());
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
            } catch (IOException e) {
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
    private boolean copyFileToCache(Intent intent, String tempPath) {
        // Get an input stream to the data in ContentProvider
        InputStream in;
        try {
            in = getContentResolver().openInputStream(intent.getData());
        } catch (FileNotFoundException e) {
            Timber.e(e, "Could not open input stream to intent data");
            return false;
        }
        // Check non-null
        if (in == null) {
            return false;
        }
        // Create new output stream in temporary path
        OutputStream out;
        try {
            out = new FileOutputStream(tempPath);
        } catch (FileNotFoundException e) {
            Timber.e(e, "Could not access destination file %s", tempPath);
            return false;
        }

        try {
            // Copy the input stream to temporary file
            byte[] buf = new byte[1024];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
            in.close();
        } catch (IOException e) {
            Timber.e(e, "Could not copy file to %s", tempPath);
            return false;
        } finally {
            try {
                out.close();
            } catch (IOException e) {
                Timber.e(e, "Error closing tempOutDir %s", tempPath);
            }
        }
        return true;
    }
}