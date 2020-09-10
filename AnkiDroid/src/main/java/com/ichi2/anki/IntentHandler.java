package com.ichi2.anki;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Message;

import com.ichi2.anki.dialogs.DialogHandler;
import com.ichi2.anki.services.ReminderService;
import com.ichi2.utils.FunctionalInterfaces.Consumer;
import com.ichi2.utils.ImportUtils;
import com.ichi2.utils.ImportUtils.ImportResult;
import com.ichi2.utils.Permissions;

import androidx.annotation.CheckResult;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
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
        //Note: This is our entry point from the launcher with intent: android.intent.action.MAIN
        Timber.d("onCreate()");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.progress_bar);
        Intent intent = getIntent();
        Timber.v(intent.toString());
        Intent reloadIntent = new Intent(this, DeckPicker.class);
        reloadIntent.setDataAndType(getIntent().getData(), getIntent().getType());
        String action = intent.getAction();
        // #6157 - We want to block actions that need permissions we don't have, but not the default case
        // as this requires nothing
        Consumer<Runnable> runIfStoragePermissions = (runnable) -> performActionIfStoragePermission(runnable, reloadIntent, action);
        LaunchType launchType = getLaunchType(intent);
        switch (launchType) {
            case FILE_IMPORT:
                runIfStoragePermissions.consume(() -> handleFileImport(intent, reloadIntent, action));
                break;
            case SYNC:
                runIfStoragePermissions.consume(() -> handleSyncIntent(reloadIntent, action));
                break;
            case REVIEW:
                runIfStoragePermissions.consume(() -> handleReviewIntent(intent));
                break;
            case DEFAULT_START_APP_IF_NEW:
                Timber.d("onCreate() performing default action");
                launchDeckPickerIfNoOtherTasks(reloadIntent);
                break;
            default:
                Timber.w("Unknown launch type: %s. Performing default action", launchType);
                launchDeckPickerIfNoOtherTasks(reloadIntent);
        }
    }

    private static boolean isValidViewIntent(@NonNull Intent intent) {
        // Negating a negative because we want to call specific attention to the fact that it's invalid
        // #6312 - Smart Launcher provided an empty ACTION_VIEW, no point in importing here.
        return !ImportUtils.isInvalidViewIntent(intent);
    }

    @VisibleForTesting
    @CheckResult
    static LaunchType getLaunchType(@NonNull Intent intent) {
        String action = intent.getAction();
        if (Intent.ACTION_VIEW.equals(action) && isValidViewIntent(intent)) {
            return LaunchType.FILE_IMPORT;
        } else if ("com.ichi2.anki.DO_SYNC".equals(action)) {
            return LaunchType.SYNC;
        } else if (intent.hasExtra(ReminderService.EXTRA_DECK_ID)) {
            return LaunchType.REVIEW;
        } else {
            return LaunchType.DEFAULT_START_APP_IF_NEW;
        }
    }


    private void performActionIfStoragePermission(Runnable runnable, Intent reloadIntent, String action) {
        if (Permissions.hasStorageAccessPermission(this)) {
            Timber.i("User has storage permissions. Running intent: %s", action);
            runnable.run();
        } else {
            //COULD_BE_BETTER: We could handle this failure in each activity individually, allowing us to pick up after
            //we get permission
            Timber.i("No Storage Permission, cancelling intent '%s'", action);
            UIUtils.showThemedToast(this, getString(R.string.intent_handler_failed_no_storage_permission), false);
            launchDeckPickerIfNoOtherTasks(reloadIntent);
        }
    }


    private void handleReviewIntent(Intent intent) {
        long deckId = intent.getLongExtra(ReminderService.EXTRA_DECK_ID, 0);
        Timber.i("Handling intent to review deck '%d'", deckId);
        final Intent reviewIntent = new Intent(this, Reviewer.class);

        CollectionHelper.getInstance().getCol(this).getDecks().select(deckId);
        startActivity(reviewIntent);
        AnkiActivity.finishActivityWithFade(this);
    }


    private void handleSyncIntent(Intent reloadIntent, String action) {
        Timber.i("Handling Sync Intent");
        sendDoSyncMsg();
        reloadIntent.setAction(action);
        reloadIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(reloadIntent);
        AnkiActivity.finishActivityWithFade(this);
    }


    private void handleFileImport(Intent intent, Intent reloadIntent, String action) {
        Timber.i("Handling file import");
        ImportResult importResult = ImportUtils.handleFileImport(this, intent);
        // Start DeckPicker if we correctly processed ACTION_VIEW
        if (importResult.isSuccess()) {
            Timber.d("onCreate() import successful");
            reloadIntent.setAction(action);
            reloadIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(reloadIntent);
            AnkiActivity.finishActivityWithFade(this);
        } else {
            Timber.i("File import failed");
            // Don't import the file if it didn't load properly or doesn't have apkg extension
            ImportUtils.showImportUnsuccessfulDialog(this, importResult.getHumanReadableMessage(), true);
        }
    }


    private void launchDeckPickerIfNoOtherTasks(Intent reloadIntent) {
        // Launcher intents should start DeckPicker if no other task exists,
        // otherwise go to previous task
        Timber.i("Launching DeckPicker");
        reloadIntent.setAction(Intent.ACTION_MAIN);
        reloadIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        reloadIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivityIfNeeded(reloadIntent, 0);
        finish();
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

    //COULD_BE_BETTER: Also extract the parameters into here to reduce coupling
    @VisibleForTesting
    enum LaunchType {
        DEFAULT_START_APP_IF_NEW,
        FILE_IMPORT,
        SYNC,
        REVIEW
    }
}