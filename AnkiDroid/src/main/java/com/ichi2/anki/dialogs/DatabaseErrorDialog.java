
package com.ichi2.anki.dialogs;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.os.Build;
import android.os.Bundle;
import android.os.Message;

import com.ichi2.anki.AnkiActivity;
import com.ichi2.anki.AnkiDatabaseManager;
import com.ichi2.anki.BackupManager;
import com.ichi2.anki.CollectionHelper;
import com.ichi2.anki.R;
import com.ichi2.compat.CompatHelper;
import com.ichi2.themes.StyledDialog;

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

    // public flag which lets us distinguish between inaccessible and corrupt database
    public static boolean databaseCorruptFlag = false;

    public interface DatabaseErrorDialogListener {
        public void showDatabaseErrorDialog(int dialogType);


        public void sendErrorReport();


        public boolean hasErrorFiles();


        public void startLoadingCollection();


        public void repairDeck();


        public void restoreFromBackup(String backupPath);


        public void integrityCheck();


        public int getSyncMediaUsn();


        public void sync(String conflict, int mediaUsn);


        public void exit();


        public void dismissAllDialogFragments();
    }


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
    public StyledDialog onCreateDialog(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mType = getArguments().getInt("dialogType");
        Resources res = getResources();
        StyledDialog.Builder builder = new StyledDialog.Builder(getActivity());
        setCancelable(true);
        builder.setTitle(getTitle());

        boolean sqliteInstalled = false;
        try {
            sqliteInstalled = Runtime.getRuntime().exec("sqlite3 --version").waitFor() == 0;
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        switch (mType) {
            case DIALOG_LOAD_FAILED:
                // Collection failed to load; give user the option of either choosing from repair options, or closing
                // the activity
                setCancelable(false);
                builder.setMessage(getMessage());
                builder.setIcon(R.drawable.ic_dialog_alert);
                builder.setPositiveButton(res.getString(R.string.error_handling_options),
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                ((DatabaseErrorDialogListener) getActivity())
                                        .showDatabaseErrorDialog(DIALOG_ERROR_HANDLING);
                            }
                        });
                builder.setNegativeButton(res.getString(R.string.close), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        ((DatabaseErrorDialogListener) getActivity()).exit();
                    }
                });
                return builder.create();

            case DIALOG_DB_ERROR:
                // Database Check failed to execute successfully; give user the option of either choosing from repair
                // options, submitting an error report, or closing the activity
                setCancelable(false);
                builder.setMessage(getMessage());
                builder.setIcon(R.drawable.ic_dialog_alert);
                builder.setPositiveButton(res.getString(R.string.error_handling_options),
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                ((DatabaseErrorDialogListener) getActivity())
                                        .showDatabaseErrorDialog(DIALOG_ERROR_HANDLING);
                            }
                        });
                builder.setNeutralButton(res.getString(R.string.answering_error_report),
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                ((DatabaseErrorDialogListener) getActivity()).sendErrorReport();
                                dismissAllDialogFragments();
                            }
                        });
                builder.setNegativeButton(res.getString(R.string.close), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        ((DatabaseErrorDialogListener) getActivity()).exit();
                    }
                });
                StyledDialog d = builder.create();
                // Disable the error report button if there are already unsent error reports
                d.getButton(DialogInterface.BUTTON_NEUTRAL).setEnabled(
                        ((DatabaseErrorDialogListener) getActivity()).hasErrorFiles());
                return d;

            case DIALOG_ERROR_HANDLING:
                // The user has asked to see repair options; allow them to choose one of the repair options or go back
                // to the previous dialog
                builder.setIcon(R.drawable.ic_dialog_alert);
                builder.setSingleChoiceItems(new String[] { "1" }, 0, null);
                builder.setNegativeButton(res.getString(R.string.dialog_cancel), null);

                StyledDialog dialog = builder.create();
                ArrayList<String> options = new ArrayList<String>();
                ArrayList<Integer> values = new ArrayList<Integer>();
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
                dialog.setItems(titles, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        switch (mRepairValues[which]) {
                            case 0:
                                ((DatabaseErrorDialogListener) getActivity()).startLoadingCollection();
                                return;
                            case 1:
                                ((DatabaseErrorDialogListener) getActivity())
                                        .showDatabaseErrorDialog(DIALOG_CONFIRM_DATABASE_CHECK);
                                return;
                            case 2:
                                ((DatabaseErrorDialogListener) getActivity())
                                        .showDatabaseErrorDialog(DIALOG_REPAIR_COLLECTION);
                                return;
                            case 3:
                                ((DatabaseErrorDialogListener) getActivity())
                                        .showDatabaseErrorDialog(DIALOG_RESTORE_BACKUP);
                                return;
                            case 4:
                                ((DatabaseErrorDialogListener) getActivity())
                                        .showDatabaseErrorDialog(DIALOG_FULL_SYNC_FROM_SERVER);
                                return;
                            case 5:
                                ((DatabaseErrorDialogListener) getActivity())
                                        .showDatabaseErrorDialog(DIALOG_NEW_COLLECTION);
                                return;
                        }
                    }
                });
                return dialog;

            case DIALOG_REPAIR_COLLECTION:
                // Allow user to run BackupManager.repairCollection()
                builder.setMessage(getMessage());
                builder.setIcon(R.drawable.ic_dialog_alert);
                builder.setPositiveButton(res.getString(R.string.dialog_positive_repair),
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                ((DatabaseErrorDialogListener) getActivity()).repairDeck();
                                dismissAllDialogFragments();
                            }
                        });
                builder.setNegativeButton(res.getString(R.string.dialog_cancel), null);
                return builder.create();

            case DIALOG_RESTORE_BACKUP:
                // Allow user to restore one of the backups
                String path = CollectionHelper.getInstance().getCollectionPath(getActivity());
                File[] files = BackupManager.getBackups(new File(path));
                mBackups = new File[files.length];
                for (int i = 0; i < files.length; i++) {
                    mBackups[i] = files[files.length - 1 - i];
                }
                if (mBackups.length == 0) {
                    builder.setTitle(getResources().getString(R.string.backup_restore));
                    builder.setMessage(getMessage());
                    builder.setPositiveButton(res.getString(R.string.dialog_ok), new Dialog.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            ((DatabaseErrorDialogListener) getActivity())
                                    .showDatabaseErrorDialog(DIALOG_ERROR_HANDLING);
                        }
                    });
                } else {
                    String[] dates = new String[mBackups.length];
                    for (int i = 0; i < mBackups.length; i++) {
                        dates[i] = mBackups[i].getName().replaceAll(
                                ".*-(\\d{4}-\\d{2}-\\d{2})-(\\d{2})-(\\d{2}).apkg", "$1 ($2:$3 h)");
                    }
                    builder.setTitle(res.getString(R.string.backup_restore_select_title));
                    builder.setIcon(android.R.drawable.ic_input_get);
                    builder.setSingleChoiceItems(dates, dates.length, new DialogInterface.OnClickListener() {

                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            if (mBackups[which].length() > 0) {
                                // restore the backup if it's valid
                                ((DatabaseErrorDialogListener) getActivity()).restoreFromBackup(mBackups[which]
                                        .getPath());
                                dismissAllDialogFragments();
                            } else {
                                // otherwise show an error dialog
                                Dialog invalidFileDialog = new AlertDialog.Builder(getActivity())
                                        .setTitle(R.string.backup_error).setMessage(R.string.backup_invalid_file_error)
                                        .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                                            @Override
                                            public void onClick(DialogInterface dialog, int which) {
                                            }
                                        }).create();
                                invalidFileDialog.show();
                            }
                        }
                    });
                }
                return builder.create();

            case DIALOG_NEW_COLLECTION:
                // Allow user to create a new empty collection
                builder.setMessage(getMessage());
                builder.setPositiveButton(res.getString(R.string.dialog_positive_create),
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                CollectionHelper.getInstance().closeCollection(false);
                                String path = CollectionHelper.getInstance().getCollectionPath(getActivity());
                                AnkiDatabaseManager.closeDatabase(path);
                                if (BackupManager.moveDatabaseToBrokenFolder(path, false)) {
                                    ((DatabaseErrorDialogListener) getActivity()).startLoadingCollection();
                                } else {
                                    ((DatabaseErrorDialogListener) getActivity())
                                            .showDatabaseErrorDialog(DIALOG_ERROR_HANDLING);
                                }
                                dismissAllDialogFragments();
                            }
                        });
                builder.setNegativeButton(res.getString(R.string.dialog_cancel), null);
                return builder.create();

            case DIALOG_CONFIRM_DATABASE_CHECK:
                // Confirmation dialog for database check
                builder.setMessage(getMessage());
                builder.setPositiveButton(res.getString(R.string.dialog_ok), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        ((DatabaseErrorDialogListener) getActivity()).integrityCheck();
                        dismissAllDialogFragments();
                    }
                });
                builder.setNegativeButton(res.getString(R.string.dialog_cancel), null);
                return builder.create();

            case DIALOG_CONFIRM_RESTORE_BACKUP:
                // Confirmation dialog for backup restore
                builder.setMessage(getMessage());
                builder.setPositiveButton(res.getString(R.string.dialog_continue),
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                ((DatabaseErrorDialogListener) getActivity())
                                        .showDatabaseErrorDialog(DIALOG_RESTORE_BACKUP);
                            }
                        });
                builder.setNegativeButton(res.getString(R.string.dialog_cancel), null);
                return builder.create();

            case DIALOG_FULL_SYNC_FROM_SERVER:
                // Allow user to do a full-sync from the server
                builder.setMessage(getMessage());
                builder.setPositiveButton(res.getString(R.string.dialog_positive_overwrite),
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {                                
                                DatabaseErrorDialogListener activity = (DatabaseErrorDialogListener) getActivity();
                                activity.sync("download", activity.getSyncMediaUsn());
                                dismissAllDialogFragments();
                            }
                        });
                builder.setNegativeButton(res.getString(R.string.dialog_cancel), null);
                return builder.create();

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
        ((DatabaseErrorDialogListener) getActivity()).dismissAllDialogFragments();
    }
}
