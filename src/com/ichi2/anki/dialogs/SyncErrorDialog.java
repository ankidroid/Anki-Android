
package com.ichi2.anki.dialogs;

import android.app.Dialog;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;

import com.ichi2.anki.R;
import com.ichi2.libanki.Collection;
import com.ichi2.themes.StyledDialog;

public class SyncErrorDialog extends DialogFragment {
    private int mType = 0;

    public static final int DIALOG_USER_NOT_LOGGED_IN_SYNC = 0;
    public static final int DIALOG_CONNECTION_ERROR = 1;
    public static final int DIALOG_SYNC_CONFLICT_RESOLUTION = 2;
    public static final int DIALOG_SYNC_CONFLICT_CONFIRM_KEEP_LOCAL = 3;
    public static final int DIALOG_SYNC_CONFLICT_CONFIRM_KEEP_REMOTE = 4;
    public static final int DIALOG_NO_CONNECTION = 5;
    public static final int DIALOG_SYNC_SANITY_ERROR = 6;
    public static final int DIALOG_SYNC_SANITY_ERROR_CONFIRM_KEEP_LOCAL = 7;
    public static final int DIALOG_SYNC_SANITY_ERROR_CONFIRM_KEEP_REMOTE = 8;
    public static final int DIALOG_SYNC_LOG = 9;

    public interface SyncErrorDialogListener {
        public void showSyncErrorDialog(int dialogType);


        public void showSyncErrorDialog(int dialogType, String message);


        public void loginToSyncServer();


        public void sync();


        public void sync(String conflict, int syncMediaUsn);


        public int getSyncMediaUsn();


        public Collection getCol();


        public void dismissAllDialogFragments();
    }


    /**
     * A set of dialogs belonging to AnkiActivity which deal with sync problems
     * 
     * @param dialogType An integer which specifies which of the sub-dialogs to show
     * @param dialogMessage A string which can be optionally used to set the dialog message
     */
    public static SyncErrorDialog newInstance(int dialogType, String dialogMessage) {
        SyncErrorDialog f = new SyncErrorDialog();
        Bundle args = new Bundle();
        args.putInt("dialogType", dialogType);
        args.putString("dialogMessage", dialogMessage);
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

        switch (mType) {
            case DIALOG_USER_NOT_LOGGED_IN_SYNC:
                // User not logged in; take them to login screen
                builder.setTitle(res.getString(R.string.not_logged_in_title));
                builder.setIcon(R.drawable.ic_dialog_alert);
                builder.setMessage(res.getString(R.string.login_create_account_message));
                builder.setNegativeButton(res.getString(R.string.dialog_cancel), null);
                builder.setPositiveButton(res.getString(R.string.log_in), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        ((SyncErrorDialogListener) getActivity()).loginToSyncServer();
                    }
                });
                return builder.create();

            case DIALOG_CONNECTION_ERROR:
                // Connection error; allow user to retry or cancel
                builder.setIcon(R.drawable.ic_dialog_alert);
                builder.setMessage(res.getString(R.string.connection_error_message));
                builder.setPositiveButton(res.getString(R.string.retry), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        ((SyncErrorDialogListener) getActivity()).sync();
                        dismissAllDialogFragments();
                    }
                });
                builder.setNegativeButton(res.getString(R.string.dialog_cancel), clearAllDialogsClickListener);
                return builder.create();

            case DIALOG_SYNC_CONFLICT_RESOLUTION:
                // Sync conflict; allow user to cancel, or choose between local and remote versions
                builder.setTitle(res.getString(R.string.sync_conflict_title));
                builder.setIcon(android.R.drawable.ic_input_get);
                builder.setMessage(res.getString(R.string.sync_conflict_message));
                builder.setPositiveButton(res.getString(R.string.sync_conflict_local),
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                ((SyncErrorDialogListener) getActivity())
                                        .showSyncErrorDialog(DIALOG_SYNC_CONFLICT_CONFIRM_KEEP_LOCAL);
                            }
                        });
                builder.setNeutralButton(res.getString(R.string.sync_conflict_remote),
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                ((SyncErrorDialogListener) getActivity())
                                        .showSyncErrorDialog(DIALOG_SYNC_CONFLICT_CONFIRM_KEEP_REMOTE);
                            }
                        });
                builder.setNegativeButton(res.getString(R.string.dialog_cancel), clearAllDialogsClickListener);
                builder.setCancelable(true);
                return builder.create();

            case DIALOG_SYNC_CONFLICT_CONFIRM_KEEP_LOCAL:
                // Confirmation before pushing local collection to server after sync conflict
                builder.setPositiveButton(res.getString(R.string.dialog_positive_overwrite),
                        new Dialog.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                SyncErrorDialogListener activity = (SyncErrorDialogListener) getActivity();
                                activity.sync("upload", activity.getSyncMediaUsn());
                                dismissAllDialogFragments();
                            }
                        });
                builder.setNegativeButton(res.getString(R.string.dialog_cancel), null);
                builder.setMessage(res.getString(R.string.sync_conflict_local_confirm));
                return builder.create();

            case DIALOG_SYNC_CONFLICT_CONFIRM_KEEP_REMOTE:
                // Confirmation before overwriting local collection with server collection after sync conflict
                builder.setPositiveButton(res.getString(R.string.dialog_positive_overwrite),
                        new Dialog.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                SyncErrorDialogListener activity = (SyncErrorDialogListener) getActivity();
                                activity.sync("download", activity.getSyncMediaUsn());
                                dismissAllDialogFragments();
                            }
                        });
                builder.setNegativeButton(res.getString(R.string.dialog_cancel), null);
                builder.setMessage(res.getString(R.string.sync_conflict_remote_confirm));
                return builder.create();

            case DIALOG_NO_CONNECTION:
                // No sync connection -- cancel sync
                builder.setIcon(R.drawable.ic_dialog_alert);
                builder.setMessage(res.getString(R.string.youre_offline));
                builder.setPositiveButton(res.getString(R.string.dialog_ok), null);
                return builder.create();

            case DIALOG_SYNC_SANITY_ERROR:
                // Sync sanity check error; allow user to cancel, or choose between local and remote versions
                builder.setMessage(getArguments().getString("dialogMessage"));
                builder.setPositiveButton(getString(R.string.sync_sanity_local), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        ((SyncErrorDialogListener) getActivity())
                                .showSyncErrorDialog(DIALOG_SYNC_SANITY_ERROR_CONFIRM_KEEP_LOCAL);
                    }
                });
                builder.setNeutralButton(getString(R.string.sync_sanity_remote), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        ((SyncErrorDialogListener) getActivity())
                                .showSyncErrorDialog(DIALOG_SYNC_SANITY_ERROR_CONFIRM_KEEP_REMOTE);
                    }
                });
                builder.setNegativeButton(res.getString(R.string.dialog_cancel), null);
                return builder.create();

            case DIALOG_SYNC_SANITY_ERROR_CONFIRM_KEEP_LOCAL:
                // Confirmation before pushing local collection to server after sanity check error
                builder.setPositiveButton(res.getString(R.string.dialog_positive_overwrite),
                        new Dialog.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                SyncErrorDialogListener activity = (SyncErrorDialogListener) getActivity();
                                Collection col = activity.getCol();
                                if (col != null) {
                                    col.modSchema(true);
                                    col.setMod();
                                    activity.sync("upload", 0);
                                    dismissAllDialogFragments();
                                }
                            }
                        });
                builder.setNegativeButton(res.getString(R.string.dialog_cancel), null);
                builder.setMessage(res.getString(R.string.sync_conflict_local_confirm));
                return builder.create();

            case DIALOG_SYNC_SANITY_ERROR_CONFIRM_KEEP_REMOTE:
                // Confirmation before overwriting local collection with server collection after sanity check error
                builder.setPositiveButton(res.getString(R.string.dialog_positive_overwrite),
                        new Dialog.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                SyncErrorDialogListener activity = (SyncErrorDialogListener) getActivity();
                                Collection col = activity.getCol();
                                if (col != null) {
                                    col.modSchema(true);
                                    col.setMod();
                                    activity.sync("download", 0);
                                    dismissAllDialogFragments();
                                }
                            }
                        });
                builder.setNegativeButton(res.getString(R.string.dialog_cancel), null);
                builder.setMessage(res.getString(R.string.sync_conflict_remote_confirm));
                return builder.create();

            case DIALOG_SYNC_LOG:
                // Show log file
                builder.setMessage(getArguments().getString("dialogMessage"));
                builder.setPositiveButton(res.getString(R.string.dialog_ok), clearAllDialogsClickListener);
                return builder.create();

            default:
                return null;
        }
    }

    // Listener for cancel button which clears ALL previous dialogs on the back stack
    // Supply null instead of this listener in cases where we prefer to go back to last dialog
    private DialogInterface.OnClickListener clearAllDialogsClickListener = new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            dismissAllDialogFragments();
        }
    };


    public void dismissAllDialogFragments() {
        ((SyncErrorDialogListener) getActivity()).dismissAllDialogFragments();
    }
}
