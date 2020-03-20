package com.ichi2.anki;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Message;

import com.ichi2.anki.dialogs.DialogHandler;
import com.ichi2.anki.services.ReminderService;
import com.ichi2.utils.ImportUtils;

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
        Timber.d("onCreate()");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.progress_bar);
        Intent intent = getIntent();
        Timber.v(intent.toString());
        Intent reloadIntent = new Intent(this, DeckPicker.class);
        reloadIntent.setDataAndType(getIntent().getData(), getIntent().getType());
        String action = intent.getAction();
        if (Intent.ACTION_VIEW.equals(action)) {

            String errorMessage = ImportUtils.handleFileImport(this, intent);
            // Start DeckPicker if we correctly processed ACTION_VIEW
            if (errorMessage == null) {
                Timber.d("onCreate() import successful");
                reloadIntent.setAction(action);
                reloadIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(reloadIntent);
                AnkiActivity.finishActivityWithFade(this);
            } else {
                // Don't import the file if it didn't load properly or doesn't have apkg extension
                //Themes.showThemedToast(this, getResources().getString(R.string.import_log_no_apkg), true);
                ImportUtils.showImportUnsuccessfulDialog(this, errorMessage, true);
            }
        } else if ("com.ichi2.anki.DO_SYNC".equals(action)) {
            sendDoSyncMsg();
            reloadIntent.setAction(action);
            reloadIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(reloadIntent);
            AnkiActivity.finishActivityWithFade(this);
        } else if (intent.hasExtra(ReminderService.EXTRA_DECK_ID)) {
            final Intent reviewIntent = new Intent(this, Reviewer.class);

            CollectionHelper.getInstance().getCol(this).getDecks().select(intent.getLongExtra(ReminderService.EXTRA_DECK_ID, 0));
            startActivity(reviewIntent);
            AnkiActivity.finishActivityWithFade(this);
        } else {
            // Launcher intents should start DeckPicker if no other task exists,
            // otherwise go to previous task
            Timber.d("onCreate() performing default action");
            reloadIntent.setAction(Intent.ACTION_MAIN);
            reloadIntent.addCategory(Intent.CATEGORY_LAUNCHER);
            reloadIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivityIfNeeded(reloadIntent, 0);
            AnkiActivity.finishActivityWithFade(this);
        }
    }


    /**
     * Send a Message to AnkiDroidApp so that the DialogMessageHandler forces a sync
     */
    public static void sendDoSyncMsg() {
        // Create a new message for DialogHandler
        Message handlerMessage = Message.obtain();
        handlerMessage.what = DialogHandler.MSG_DO_SYNC;
        // Store the message in AnkiDroidApp message holder, which is loaded later in AnkiActivity.onResume
        DialogHandler.storeMessage(handlerMessage);
    }
}