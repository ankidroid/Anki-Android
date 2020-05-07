package com.ichi2.anki.dialogs;

import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;

import com.ichi2.anki.AnkiActivity;
import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.CollectionHelper;
import com.ichi2.anki.DeckPicker;
import com.ichi2.anki.NotificationChannels;
import com.ichi2.anki.R;
import com.ichi2.async.Connection;
import com.ichi2.libanki.Utils;
import com.ichi2.anki.analytics.UsageAnalytics;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

import timber.log.Timber;


/**
 * We're not allowed to commit fragment transactions from Loader.onLoadCompleted(),
 * and it's unsafe to commit them from an AsyncTask onComplete event, so we work 
 * around this by using a message handler.
 */
public class DialogHandler extends Handler {

    public static final long INTENT_SYNC_MIN_INTERVAL = 2*60000;    // 2min minimum sync interval

    /**
     * Handler messages
     */
    public static final int MSG_SHOW_COLLECTION_LOADING_ERROR_DIALOG = 0;
    public static final int MSG_SHOW_COLLECTION_IMPORT_REPLACE_DIALOG = 1;
    public static final int MSG_SHOW_COLLECTION_IMPORT_ADD_DIALOG = 2;
    public static final int MSG_SHOW_SYNC_ERROR_DIALOG = 3;
    public static final int MSG_SHOW_EXPORT_COMPLETE_DIALOG = 4;
    public static final int MSG_SHOW_MEDIA_CHECK_COMPLETE_DIALOG = 5;
    public static final int MSG_SHOW_DATABASE_ERROR_DIALOG = 6;
    public static final int MSG_SHOW_FORCE_FULL_SYNC_DIALOG = 7;
    public static final int MSG_DO_SYNC = 8;

    public static final String[] sMessageNameList = {
            "CollectionLoadErrorDialog",
            "ImportReplaceDialog",
            "ImportAddDialog",
            "SyncErrorDialog",
            "ExportCompleteDialog",
            "MediaCheckCompleteDialog",
            "DatabaseErrorDialog",
            "ForceFullSyncDialog",
            "DoSyncDialog"
    };


    WeakReference<AnkiActivity> mActivity;
    private static Message sStoredMessage;
    
    public DialogHandler(AnkiActivity activity) {
        // Use weak reference to main activity to prevent leaking the activity when it's closed
        mActivity = new WeakReference<>(activity);
    }


    @Override
    public void handleMessage(Message msg) {
        Bundle msgData = msg.getData();
        String messageName = sMessageNameList[msg.what];
        UsageAnalytics.sendAnalyticsScreenView(messageName);
        Timber.i("Handling Message: %s", messageName);
        if (msg.what == MSG_SHOW_COLLECTION_LOADING_ERROR_DIALOG) {
            // Collection could not be opened
            ((DeckPicker) mActivity.get()).showDatabaseErrorDialog(DatabaseErrorDialog.DIALOG_LOAD_FAILED);
        } else if (msg.what == MSG_SHOW_COLLECTION_IMPORT_REPLACE_DIALOG) {
            // Handle import of collection package APKG
            ((DeckPicker) mActivity.get()).showImportDialog(ImportDialog.DIALOG_IMPORT_REPLACE_CONFIRM, msgData.getString("importPath"));
        } else if (msg.what == MSG_SHOW_COLLECTION_IMPORT_ADD_DIALOG) {
            // Handle import of deck package APKG
            ((DeckPicker) mActivity.get()).showImportDialog(ImportDialog.DIALOG_IMPORT_ADD_CONFIRM, msgData.getString("importPath"));
        } else if (msg.what == MSG_SHOW_SYNC_ERROR_DIALOG) {
            int id = msgData.getInt("dialogType");
            String message = msgData.getString("dialogMessage");
            ((DeckPicker) mActivity.get()).showSyncErrorDialog(id, message);
        } else if (msg.what == MSG_SHOW_EXPORT_COMPLETE_DIALOG) {
            // Export complete
            AsyncDialogFragment f = DeckPickerExportCompleteDialog.newInstance(msgData.getString("exportPath"));
            mActivity.get().showAsyncDialogFragment(f);
        } else if (msg.what == MSG_SHOW_MEDIA_CHECK_COMPLETE_DIALOG) {            
            // Media check results
            int id = msgData.getInt("dialogType");
            if (id!=MediaCheckDialog.DIALOG_CONFIRM_MEDIA_CHECK) {
                List<List<String>> checkList = new ArrayList<>();
                checkList.add(msgData.getStringArrayList("nohave"));
                checkList.add(msgData.getStringArrayList("unused"));
                checkList.add(msgData.getStringArrayList("invalid"));
                ((DeckPicker) mActivity.get()).showMediaCheckDialog(id, checkList);
            }
        } else if (msg.what == MSG_SHOW_DATABASE_ERROR_DIALOG) {
            // Database error dialog
            ((DeckPicker) mActivity.get()).showDatabaseErrorDialog(msgData.getInt("dialogType"));
        } else if (msg.what == MSG_SHOW_FORCE_FULL_SYNC_DIALOG) {
            // Confirmation dialog for forcing full sync
            ConfirmationDialog dialog = new ConfirmationDialog ();
            Runnable confirm = new Runnable() {
                @Override
                public void run() {
                    // Bypass the check once the user confirms
                    CollectionHelper.getInstance().getCol(AnkiDroidApp.getInstance()).modSchemaNoCheck();
                }
            };
            dialog.setConfirm(confirm);
            dialog.setArgs(msgData.getString("message"));
            (mActivity.get()).showDialogFragment(dialog);
        } else if (msg.what == MSG_DO_SYNC) {
            SharedPreferences preferences = AnkiDroidApp.getSharedPrefs(mActivity.get());
            Resources res = mActivity.get().getResources();
            String hkey = preferences.getString("hkey", "");
            boolean limited = Utils.intTime(1000) - preferences.getLong("lastSyncTime", 0) < INTENT_SYNC_MIN_INTERVAL;
            if (!limited && hkey.length() > 0 && Connection.isOnline()) {
                ((DeckPicker) mActivity.get()).sync();
            } else {
                String err = res.getString(R.string.sync_error);
                if (limited) {
                    mActivity.get().showSimpleNotification(err, res.getString(R.string.sync_too_busy), NotificationChannels.Channel.SYNC);
                } else {
                    mActivity.get().showSimpleNotification(err, res.getString(R.string.youre_offline), NotificationChannels.Channel.SYNC);
                }
            }
            mActivity.get().finishWithoutAnimation();
        }
    }

    /**
     * Store a persistent message to static variable
     * @param message Message to store
     */
    public static void storeMessage(Message message) {
        Timber.d("Storing persistent message");
        sStoredMessage = message;
    }

    /**
     * Read and handle Message which was stored via storeMessage()
     */
    public void readMessage() {
        Timber.d("Reading persistent message");
        if (sStoredMessage != null) {
            Timber.i("Dispatching persistent message: %d", sStoredMessage.what);
            sendMessage(sStoredMessage);
        }
        sStoredMessage = null;
    }
}
