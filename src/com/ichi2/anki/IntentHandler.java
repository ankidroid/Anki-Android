package com.ichi2.anki;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Message;
import android.provider.OpenableColumns;
import android.support.v4.content.IntentCompat;

import com.ichi2.anim.ActivityTransitionAnimation;
import com.ichi2.anki.dialogs.DialogHandler;
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
        Intent reloadIntent = new Intent(this, DeckPicker.class);
        reloadIntent.setDataAndType(getIntent().getData(), getIntent().getType());
        String action = intent.getAction();
        if (Intent.ACTION_VIEW.equals(action)) {
            // This intent is used for opening apkg package
            // We want to go immediately to DeckPicker, clearing any history in the process
            // TODO: Still one bug, where if AnkiDroid is launched via ACTION_VIEW,
            // then subsequent ACTION_VIEW events bypass IntentHandler. Prob need to do something onResume() of AnkiActivity

            /* When a VIEW intent is sent with a content provider URI, we copy to a temporary file that we can read directly */
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
                // Copy to temp file
                if (filename != null && filename.endsWith(".apkg")) {
                    Uri importUri = Uri.fromFile(new File(getCacheDir(), filename));
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
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    } catch (IOException e2) {
                        e2.printStackTrace();
                    }
                    // Create a new message for DialogHandler so that we see the appropriate import dialog in DeckPicker
                    Message handlerMessage = Message.obtain();
                    Bundle msgData = new Bundle();
                    msgData.putString("importPath", importUri.getEncodedPath());
                    handlerMessage.setData(msgData);
                    if (filename.equals("collection.apkg")) {
                        // Show confirmation dialog asking to confirm import with replace when file called "collection.apkg"
                        handlerMessage.what = DialogHandler.MSG_SHOW_COLLECTION_IMPORT_REPLACE_DIALOG;
                    } else {
                        // Otherwise show confirmation dialog asking to confirm import with add
                        handlerMessage.what = DialogHandler.MSG_SHOW_COLLECTION_IMPORT_ADD_DIALOG;
                    }
                    // Store the message in AnkiDroidApp message holder
                    AnkiDroidApp.setStoredDialogHandlerMessage(handlerMessage);
                } else {
                    // Don't import the file if it didn't load properly or doesn't have apkg extension
                    Themes.showThemedToast(this, getResources().getString(R.string.import_log_no_apkg), true);
                    finishWithFade();
                    return;
                }
            }

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

    /** Finish Activity using FADE animation **/
    private void finishWithFade() {
    	finish();
    	ActivityTransitionAnimation.slide(this, ActivityTransitionAnimation.FADE);
    }
}