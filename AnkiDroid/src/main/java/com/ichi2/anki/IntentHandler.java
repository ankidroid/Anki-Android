package com.ichi2.anki;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Message;
import android.provider.OpenableColumns;
import android.support.v4.content.IntentCompat;
import android.util.Log;

import com.ichi2.anim.ActivityTransitionAnimation;
import com.ichi2.anki.dialogs.DialogHandler;
import com.ichi2.themes.StyledDialog;
import com.ichi2.themes.Themes;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

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
        setContentView(R.layout.styled_open_collection_dialog);
        Intent intent = getIntent();
        Log.v(AnkiDroidApp.TAG, intent.toString());
        Intent reloadIntent = new Intent(this, DeckPicker.class);
        reloadIntent.setDataAndType(getIntent().getData(), getIntent().getType());
        String action = intent.getAction();
        if (Intent.ACTION_VIEW.equals(action)) {
            // This intent is used for opening apkg package files
            // We want to go immediately to DeckPicker, clearing any history in the process
            boolean successful = false;
            String errorMessage =getResources().getString(R.string.import_log_no_apkg);
            // If the file is being sent from a content provider we need to read the content before we can open the file
            if (intent.getData().getScheme().equals("content")) {
                // Get the original filename from the content provider URI and save to temporary cache dir
                String filename = null;
                Cursor cursor = null;
                try {
                    cursor = this.getContentResolver().query(intent.getData(), new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null );
                    if (cursor != null && cursor.moveToFirst()) {
                        filename = cursor.getString(0);
                    }
                } finally {
                    if (cursor != null)
                        cursor.close();
                }
                /* Querying the filename appears to fail for a small minority of users.
                   If the data type is apkg then we can assume that it's a shared deck from AnkiWeb
                   so we give it a dummy filename*/
                if (filename == null) {
                    Log.e(AnkiDroidApp.TAG, "Could not get filename from Content Provider. cursor = " + cursor);
                    if (intent.getType().equals("application/apkg")) {
                        filename = "unknown_filename.apkg";
                    }
                }
                if (filename != null && filename.endsWith(".apkg")) {
                    Uri importUri = Uri.fromFile(new File(getCacheDir(), filename));
                    Log.v(AnkiDroidApp.TAG, "IntentHandler copying apkg file to " + importUri.getEncodedPath());
                    // Copy to temp file
                    try {
                        // Get an input stream to the data in ContentProvider
                        InputStream in = getContentResolver().openInputStream(intent.getData());
                        // Create new output stream in temporary path
                        OutputStream out = new FileOutputStream(importUri.getEncodedPath());
                        // Copy the input stream to temporary file
                        byte[] buf = new byte[1024];
                        int len;
                        while ((len = in.read(buf)) > 0) {
                            out.write(buf, 0, len);
                        }
                        in.close();
                        out.close();
                        // Show import dialog
                        successful = sendShowImportFileDialogMsg(importUri.getEncodedPath());
                    } catch (FileNotFoundException e) {
                        errorMessage=e.getLocalizedMessage();
                        e.printStackTrace();
                    } catch (IOException e2) {
                        errorMessage=e2.getLocalizedMessage();
                        e2.printStackTrace();
                    }
                } else {
                    if (filename == null) {
                        errorMessage = "Could not retrieve filename from content resolver; try opening the apkg file with a file explorer";
                    } else {
                        errorMessage = "Filename " + filename + " does not have .apkg extension";
                    }
                }
            } else if (intent.getData().getScheme().equals("file")) {
                // When the VIEW intent is sent as a file, we can open it directly without copying from content provider                
                successful = sendShowImportFileDialogMsg(intent.getData().getPath());
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
                StyledDialog.Builder builder = new StyledDialog.Builder(this);
                builder.setTitle(title);
                builder.setMessage(errorMessage);
                builder.setPositiveButton(getResources().getString(R.string.dialog_ok), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        finishWithFade();
                    }
                });
                builder.create().show();
                return;
            }
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
     * @param path
     */
    private boolean sendShowImportFileDialogMsg(String path) {
        // Get the filename from the path
        File f = new File(path);
        String filename = f.getName();
        if (filename != null && filename.endsWith(".apkg")) {
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
            AnkiDroidApp.setStoredDialogHandlerMessage(handlerMessage);
            return true;
        } else {
            return false;
        }
    }

    /** Finish Activity using FADE animation **/
    private void finishWithFade() {
    	finish();
    	ActivityTransitionAnimation.slide(this, ActivityTransitionAnimation.FADE);
    }
}