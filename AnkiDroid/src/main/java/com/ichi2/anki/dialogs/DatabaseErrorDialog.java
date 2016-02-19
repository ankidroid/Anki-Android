
package com.ichi2.anki.dialogs;

import android.content.res.Resources;
import android.os.Bundle;
import android.os.Message;
import android.view.View;

import com.afollestad.materialdialogs.MaterialDialog;

import com.ichi2.anki.AnkiActivity;
import com.ichi2.anki.BackupManager;
import com.ichi2.anki.CollectionHelper;
import com.ichi2.anki.DeckPicker;
import com.ichi2.anki.R;
import com.ichi2.compat.CompatHelper;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

public class DatabaseErrorDialog extends AsyncDialogFragment {
    private int mType = 0;
    private int[] mRepairValues;
    private File[] mBackups;

    public static final int DIALOG_LOAD_FAILED = 0;
    public static final int DIALOG_DB_ERROR = 1;
    public static final int DIALOG_ERROR_HANDLING = 2;
    public static final int DIALOG_REPAIR_COLLECTION = 3;
    public static final int DIALOG_RESTORE_BACKUP = 4;
    public static final int DIALOG_NEW_COLLECTION = 5;
    public static final int DIALOG_CONFIRM_DATABASE_CHECK = 6;
    public static final int DIALOG_CONFIRM_RESTORE_BACKUP = 7;
    public static final int DIALOG_FULL_SYNC_FROM_SERVER = 8;
    public static final int DIALOG_CURSOR_SIZE_LIMIT_EXCEEDED = 9;

    // public flag which lets us distinguish between inaccessible and corrupt database
    public static boolean databaseCorruptFlag = false;


    /**
     * A set of dialogs which deal with problems with the database when it can't load
     * 
     * @param dialogType An integer which specifies which of the sub-dialogs to show
     */
    public static DatabaseErrorDialog newInstance(int dialogType) {
        DatabaseErrorDialog f = new DatabaseErrorDialog();
        Bundle args = new Bundle();
        args.putInt("dialogType", dialogType);
        f.setArguments(args);
        return f;
    }


    @Override
    public MaterialDialog onCreateDialog(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mType = getArguments().getInt("dialogType");
        Resources res = getResources();
        MaterialDialog.Builder builder = new MaterialDialog.Builder(getActivity());
        builder.cancelable(true)
                .title(getTitle());

        boolean sqliteInstalled = false;
        try {
            sqliteInstalled = Runtime.getRuntime().exec("sqlite3 --version").waitFor() == 0;
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }

        switch (mType) {
            case DIALOG_CURSOR_SIZE_LIMIT_EXCEEDED:
            case DIALOG_LOAD_FAILED:
                // Collection failed to load; give user the option of either choosing from repair options, or closing
                // the activity
                return builder.cancelable(false)
                        .content(getMessage())
                        .iconAttr(R.attr.dialogErrorIcon)
                        .positiveText(res.getString(R.string.error_handling_options))
                        .negativeText(res.getString(R.string.close))
                        .callback(new MaterialDialog.ButtonCallback() {
                            @Override
                            public void onPositive(MaterialDialog dialog) {
                                ((DeckPicker) getActivity())
                                        .showDatabaseErrorDialog(DIALOG_ERROR_HANDLING);
                            }

                            @Override
                            public void onNegative(MaterialDialog dialog) {
                                ((DeckPicker) getActivity()).exit();
                            }
                        })
                        .show();

            case DIALOG_DB_ERROR:
                // Database Check failed to execute successfully; give user the option of either choosing from repair
                // options, submitting an error report, or closing the activity
                MaterialDialog dialog = builder
                        .cancelable(false)
                        .content(getMessage())
                        .iconAttr(R.attr.dialogErrorIcon)
                        .positiveText(res.getString(R.string.error_handling_options))
                        .negativeText(res.getString(R.string.answering_error_report))
                        .neutralText(res.getString(R.string.close))
                        .callback(new MaterialDialog.ButtonCallback() {
                            @Override
                            public void onPositive(MaterialDialog dialog) {
                                ((DeckPicker) getActivity())
                                        .showDatabaseErrorDialog(DIALOG_ERROR_HANDLING);
                            }

                            @Override
                            public void onNegative(MaterialDialog dialog) {
                                ((DeckPicker) getActivity()).sendErrorReport();
                                dismissAllDialogFragments();
                            }

                            @Override
                            public void onNeutral(MaterialDialog dialog) {
                                ((DeckPicker) getActivity()).exit();
                            }
                        })
                        .show();
                dialog.getCustomView().findViewById(R.id.buttonDefaultNegative).setEnabled(
                        ((DeckPicker) getActivity()).hasErrorFiles());
                return dialog;

            case DIALOG_ERROR_HANDLING:
                // The user has asked to see repair options; allow them to choose one of the repair options or go back
                // to the previous dialog
                ArrayList<String> options = new ArrayList<>();
                ArrayList<Integer> values = new ArrayList<>();
                if (!((AnkiActivity)getActivity()).colIsOpen()) {
                    // retry
                    options.add(res.getString(R.string.backup_retry_opening));
                    values.add(0);
                } else {
                    // fix integrity
                    options.add(res.getString(R.string.check_db));
                    values.add(1);
                }
                // repair db with sqlite
                if (sqliteInstalled) {
                    options.add(res.getString(R.string.backup_error_menu_repair));
                    values.add(2);
                }
                // // restore from backup
                options.add(res.getString(R.string.backup_restore));
                values.add(3);
                // delete old collection and build new one
                options.add(res.getString(R.string.backup_full_sync_from_server));
                values.add(4);
                // delete old collection and build new one
                options.add(res.getString(R.string.backup_del_collection));
                values.add(5);

                String[] titles = new String[options.size()];
                mRepairValues = new int[options.size()];
                for (int i = 0; i < options.size(); i++) {
                    titles[i] = options.get(i);
                    mRepairValues[i] = values.get(i);
                }

                dialog = builder.iconAttr(R.attr.dialogErrorIcon)
                        .negativeText(res.getString(R.string.dialog_cancel))
                        .items(titles)
                        .itemsCallback(new MaterialDialog.ListCallback() {
                            @Override
                            public void onSelection(MaterialDialog materialDialog, View view, int which,
                                    CharSequence charSequence) {
                                switch (mRepairValues[which]) {
                                    case 0:
                                        ((DeckPicker) getActivity()).restartActivity();
                                        return;
                                    case 1:
                                        ((DeckPicker) getActivity())
                                                .showDatabaseErrorDialog(DIALOG_CONFIRM_DATABASE_CHECK);
                                        return;
                                    case 2:
                                        ((DeckPicker) getActivity())
                                                .showDatabaseErrorDialog(DIALOG_REPAIR_COLLECTION);
                                        return;
                                    case 3:
                                        ((DeckPicker) getActivity())
                                                .showDatabaseErrorDialog(DIALOG_RESTORE_BACKUP);
                                        return;
                                    case 4:
                                        ((DeckPicker) getActivity())
                                                .showDatabaseErrorDialog(DIALOG_FULL_SYNC_FROM_SERVER);
                                        return;
                                    case 5:
                                        ((DeckPicker) getActivity())
                                                .showDatabaseErrorDialog(DIALOG_NEW_COLLECTION);
                                }
                            }
                        })
                        .show();
                return dialog;

            case DIALOG_REPAIR_COLLECTION:
                // Allow user to run BackupManager.repairCollection()
                return builder.content(getMessage())
                        .iconAttr(R.attr.dialogErrorIcon)
                        .positiveText(res.getString(R.string.dialog_positive_repair))
                        .negativeText(res.getString(R.string.dialog_cancel))
                        .callback(new MaterialDialog.ButtonCallback() {
                            @Override
                            public void onPositive(MaterialDialog dialog) {
                                ((DeckPicker) getActivity()).repairDeck();
                                dismissAllDialogFragments();
                            }
                        })
                        .show();

            case DIALOG_RESTORE_BACKUP:
                // Allow user to restore one of the backups
                String path = CollectionHelper.getInstance().getCollectionPath(getActivity());
                File[] files = BackupManager.getBackups(new File(path));
                mBackups = new File[files.length];
                for (int i = 0; i < files.length; i++) {
                    mBackups[i] = files[files.length - 1 - i];
                }
                if (mBackups.length == 0) {
                    builder.title(res.getString(R.string.backup_restore))
                            .content(getMessage())
                            .positiveText(res.getString(R.string.dialog_ok))
                            .callback(new MaterialDialog.ButtonCallback() {
                                @Override
                                public void onPositive(MaterialDialog dialog) {
                                    ((DeckPicker) getActivity())
                                            .showDatabaseErrorDialog(DIALOG_ERROR_HANDLING);
                                }
                            });
                } else {
                    String[] dates = new String[mBackups.length];
                    for (int i = 0; i < mBackups.length; i++) {
                        dates[i] = mBackups[i].getName().replaceAll(
                                ".*-(\\d{4}-\\d{2}-\\d{2})-(\\d{2})-(\\d{2}).apkg", "$1 ($2:$3 h)");
                    }
                    builder.title(res.getString(R.string.backup_restore_select_title))
                            .negativeText(res.getString(R.string.dialog_cancel))
                            .callback(new MaterialDialog.ButtonCallback() {
                                @Override
                                public void onNegative(MaterialDialog dialog) {
                                    dismissAllDialogFragments();
                                }
                            })
                            .items(dates)
                            .itemsCallbackSingleChoice(dates.length,
                                    new MaterialDialog.ListCallbackSingleChoice() {
                                @Override
                                public boolean onSelection(MaterialDialog materialDialog, View view,
                                        int which, CharSequence charSequence) {
                                    if (mBackups[which].length() > 0) {
                                        // restore the backup if it's valid
                                        ((DeckPicker) getActivity())
                                                .restoreFromBackup(mBackups[which]
                                                        .getPath());
                                        dismissAllDialogFragments();
                                    } else {
                                        // otherwise show an error dialog
                                        new MaterialDialog.Builder(getActivity())
                                                .title(R.string.backup_error)
                                                .content(R.string.backup_invalid_file_error)
                                                .positiveText(R.string.dialog_ok)
                                                .build().show();
                                    }
                                    return true;
                                }
                            });
                }
                return builder.show();

            case DIALOG_NEW_COLLECTION:
                // Allow user to create a new empty collection
                return builder.content(getMessage())
                        .positiveText(res.getString(R.string.dialog_positive_create))
                        .negativeText(res.getString(R.string.dialog_cancel))
                        .callback(new MaterialDialog.ButtonCallback() {
                            @Override
                            public void onPositive(MaterialDialog dialog) {
                                CollectionHelper.getInstance().closeCollection(false);
                                String path = CollectionHelper.getCollectionPath(getActivity());
                                if (BackupManager.moveDatabaseToBrokenFolder(path, false)) {
                                    ((DeckPicker) getActivity()).restartActivity();
                                } else {
                                    ((DeckPicker) getActivity()).showDatabaseErrorDialog(DIALOG_LOAD_FAILED);
                                }
                            }
                        })
                        .show();

            case DIALOG_CONFIRM_DATABASE_CHECK:
                // Confirmation dialog for database check
                return builder.content(getMessage())
                        .positiveText(res.getString(R.string.dialog_ok))
                        .negativeText(res.getString(R.string.dialog_cancel))
                        .callback(new MaterialDialog.ButtonCallback() {
                            @Override
                            public void onPositive(MaterialDialog dialog) {
                                ((DeckPicker) getActivity()).integrityCheck();
                                dismissAllDialogFragments();
                            }
                        })
                        .show();

            case DIALOG_CONFIRM_RESTORE_BACKUP:
                // Confirmation dialog for backup restore
                return builder.content(getMessage())
                        .positiveText(res.getString(R.string.dialog_continue))
                        .negativeText(res.getString(R.string.dialog_cancel))
                        .callback(new MaterialDialog.ButtonCallback() {
                            @Override
                            public void onPositive(MaterialDialog dialog) {
                                ((DeckPicker) getActivity())
                                        .showDatabaseErrorDialog(DIALOG_RESTORE_BACKUP);
                            }
                        })
                        .show();

            case DIALOG_FULL_SYNC_FROM_SERVER:
                // Allow user to do a full-sync from the server
                return builder.content(getMessage())
                        .positiveText(res.getString(R.string.dialog_positive_overwrite))
                        .negativeText(res.getString(R.string.dialog_cancel))
                        .callback(new MaterialDialog.ButtonCallback() {
                            @Override
                            public void onPositive(MaterialDialog dialog) {
                                ((DeckPicker) getActivity()).sync("download");
                                dismissAllDialogFragments();
                            }
                        })
                        .show();

            default:
                return null;
        }
    }


    private String getMessage() {
        switch (getArguments().getInt("dialogType")) {
            case DIALOG_LOAD_FAILED:
                if (!CompatHelper.isHoneycomb()) {
                    // Before honeycomb there's no way to know if the db has actually been corrupted
                    // so we show a non-specific message.
                    return res().getString(R.string.open_collection_failed_message, res().getString(R.string.repair_deck));
                } else if (databaseCorruptFlag) {
                    // The sqlite database has been corrupted (DatabaseErrorHandler.onCorrupt() was called)
                    // Show a specific message appropriate for the situation
                    return res().getString(R.string.corrupt_db_message, res().getString(R.string.repair_deck));

                } else {
                    // Generic message shown when a libanki task failed
                    return res().getString(R.string.access_collection_failed_message, res().getString(R.string.link_help));
                }
            case DIALOG_DB_ERROR:
                return res().getString(R.string.answering_error_message);
            case DIALOG_REPAIR_COLLECTION:
                return res().getString(R.string.repair_deck_dialog, BackupManager.BROKEN_DECKS_SUFFIX);
            case DIALOG_RESTORE_BACKUP:
                return res().getString(R.string.backup_restore_no_backups);
            case DIALOG_NEW_COLLECTION:
                return res().getString(R.string.backup_del_collection_question);
            case DIALOG_CONFIRM_DATABASE_CHECK:
                return res().getString(R.string.check_db_warning);
            case DIALOG_CONFIRM_RESTORE_BACKUP:
                return res().getString(R.string.restore_backup);
            case DIALOG_FULL_SYNC_FROM_SERVER:
                return res().getString(R.string.backup_full_sync_from_server_question);
            case DIALOG_CURSOR_SIZE_LIMIT_EXCEEDED:
                return res().getString(R.string.cursor_size_limit_exceeded);
            default:
                return getArguments().getString("dialogMessage");
        }
    }

    private String getTitle() {
        switch (getArguments().getInt("dialogType")) {
            case DIALOG_LOAD_FAILED:
                return res().getString(R.string.open_collection_failed_title);
            case DIALOG_DB_ERROR:
                return res().getString(R.string.answering_error_title);
            case DIALOG_ERROR_HANDLING:
                return res().getString(R.string.error_handling_title);
            case DIALOG_REPAIR_COLLECTION:
                return res().getString(R.string.backup_repair_deck);
            case DIALOG_RESTORE_BACKUP:
                return res().getString(R.string.backup_restore);
            case DIALOG_NEW_COLLECTION:
                return res().getString(R.string.backup_new_collection);
            case DIALOG_CONFIRM_DATABASE_CHECK:
                return res().getString(R.string.check_db_title);
            case DIALOG_CONFIRM_RESTORE_BACKUP:
                return res().getString(R.string.restore_backup_title);
            case DIALOG_FULL_SYNC_FROM_SERVER:
                return res().getString(R.string.backup_full_sync_from_server);
            case DIALOG_CURSOR_SIZE_LIMIT_EXCEEDED:
                return res().getString(R.string.open_collection_failed_title);
            default:
                return res().getString(R.string.answering_error_title);
        }        
    }


    @Override
    public String getNotificationMessage() {
        switch (getArguments().getInt("dialogType")) {
            default:
                return getMessage();
        }
    }


    @Override
    public String getNotificationTitle() {
        switch (getArguments().getInt("dialogType")) {
            default:
                return res().getString(R.string.answering_error_title);
        }
    }


    @Override
    public Message getDialogHandlerMessage() {
        Message msg = Message.obtain();
        msg.what = DialogHandler.MSG_SHOW_DATABASE_ERROR_DIALOG;
        Bundle b = new Bundle();
        b.putInt("dialogType", getArguments().getInt("dialogType"));
        msg.setData(b);
        return msg;
    }
    
    
    public void dismissAllDialogFragments() {
        ((DeckPicker) getActivity()).dismissAllDialogFragments();
    }
}
