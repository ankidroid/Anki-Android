
package com.ichi2.anki.dialogs;

import android.os.Bundle;
import android.os.Message;

import com.afollestad.materialdialogs.MaterialDialog;
import com.ichi2.anki.R;
import com.ichi2.libanki.Collection;

public class SyncErrorDialog extends AsyncDialogFragment {
    public static final int DIALOG_USER_NOT_LOGGED_IN_SYNC = 0;
    public static final int DIALOG_CONNECTION_ERROR = 1;
    public static final int DIALOG_SYNC_CONFLICT_RESOLUTION = 2;
    public static final int DIALOG_SYNC_CONFLICT_CONFIRM_KEEP_LOCAL = 3;
    public static final int DIALOG_SYNC_CONFLICT_CONFIRM_KEEP_REMOTE = 4;
    public static final int DIALOG_SYNC_SANITY_ERROR = 6;
    public static final int DIALOG_SYNC_SANITY_ERROR_CONFIRM_KEEP_LOCAL = 7;
    public static final int DIALOG_SYNC_SANITY_ERROR_CONFIRM_KEEP_REMOTE = 8;
    public static final int DIALOG_MEDIA_SYNC_ERROR = 9;

    public interface SyncErrorDialogListener {
        public void showSyncErrorDialog(int dialogType);


        public void showSyncErrorDialog(int dialogType, String message);


        public void loginToSyncServer();


        public void sync();


        public void sync(String conflict);


        public Collection getCol();


        public void mediaCheck();


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
    public MaterialDialog onCreateDialog(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        MaterialDialog.Builder builder = new MaterialDialog.Builder(getActivity())
                .title(getTitle())
                .content(getMessage())
                .cancelable(true);

        switch (getArguments().getInt("dialogType")) {
            case DIALOG_USER_NOT_LOGGED_IN_SYNC:
                // User not logged in; take them to login screen
                return builder.iconAttr(R.attr.dialogSyncErrorIcon)
                        .positiveText(res().getString(R.string.log_in))
                        .negativeText(res().getString(R.string.dialog_cancel))
                        .callback(new MaterialDialog.ButtonCallback() {
                            @Override
                            public void onPositive(MaterialDialog dialog) {
                                ((SyncErrorDialogListener) getActivity()).loginToSyncServer();
                            }
                        })
                        .show();

            case DIALOG_CONNECTION_ERROR:
                // Connection error; allow user to retry or cancel
                return builder.iconAttr(R.attr.dialogSyncErrorIcon)
                        .positiveText(res().getString(R.string.retry))
                        .negativeText(res().getString(R.string.dialog_cancel))
                        .callback(new MaterialDialog.ButtonCallback() {
                            @Override
                            public void onPositive(MaterialDialog dialog) {
                                ((SyncErrorDialogListener) getActivity()).sync();
                                dismissAllDialogFragments();
                            }

                            @Override
                            public void onNegative(MaterialDialog dialog) {
                                dismissAllDialogFragments();
                            }
                        })
                        .show();

            case DIALOG_SYNC_CONFLICT_RESOLUTION:
                // Sync conflict; allow user to cancel, or choose between local and remote versions
                return builder.iconAttr(R.attr.dialogSyncErrorIcon)
                        .positiveText(res().getString(R.string.sync_conflict_local))
                        .negativeText(res().getString(R.string.sync_conflict_remote))
                        .neutralText(res().getString(R.string.dialog_cancel))
                        .callback(new MaterialDialog.ButtonCallback() {
                            @Override
                            public void onPositive(MaterialDialog dialog) {
                                ((SyncErrorDialogListener) getActivity())
                                        .showSyncErrorDialog(DIALOG_SYNC_CONFLICT_CONFIRM_KEEP_LOCAL);
                            }

                            @Override
                            public void onNegative(MaterialDialog dialog) {
                                ((SyncErrorDialogListener) getActivity())
                                        .showSyncErrorDialog(DIALOG_SYNC_CONFLICT_CONFIRM_KEEP_REMOTE);
                            }

                            @Override
                            public void onNeutral(MaterialDialog dialog) {
                                dismissAllDialogFragments();
                            }
                        })
                        .show();

            case DIALOG_SYNC_CONFLICT_CONFIRM_KEEP_LOCAL:
                // Confirmation before pushing local collection to server after sync conflict
                return builder.positiveText(res().getString(R.string.dialog_positive_overwrite))
                        .negativeText(res().getString(R.string.dialog_cancel))
                        .callback(new MaterialDialog.ButtonCallback() {
                            @Override
                            public void onPositive(MaterialDialog dialog) {
                                SyncErrorDialogListener activity = (SyncErrorDialogListener) getActivity();
                                activity.sync("upload");
                                dismissAllDialogFragments();
                            }
                        })
                        .show();

            case DIALOG_SYNC_CONFLICT_CONFIRM_KEEP_REMOTE:
                // Confirmation before overwriting local collection with server collection after sync conflict
                return builder.positiveText(res().getString(R.string.dialog_positive_overwrite))
                        .negativeText(res().getString(R.string.dialog_cancel))
                        .callback(new MaterialDialog.ButtonCallback() {
                            @Override
                            public void onPositive(MaterialDialog dialog) {
                                SyncErrorDialogListener activity = (SyncErrorDialogListener) getActivity();
                                activity.sync("download");
                                dismissAllDialogFragments();
                            }
                        })
                        .show();

            case DIALOG_SYNC_SANITY_ERROR:
                // Sync sanity check error; allow user to cancel, or choose between local and remote versions
                return builder.positiveText(res().getString(R.string.sync_sanity_local))
                        .neutralText(res().getString(R.string.sync_sanity_remote))
                        .negativeText(res().getString(R.string.dialog_cancel))
                        .callback(new MaterialDialog.ButtonCallback() {
                            @Override
                            public void onPositive(MaterialDialog dialog) {
                                ((SyncErrorDialogListener) getActivity())
                                        .showSyncErrorDialog(DIALOG_SYNC_SANITY_ERROR_CONFIRM_KEEP_LOCAL);
                            }

                            @Override
                            public void onNeutral(MaterialDialog dialog) {
                                ((SyncErrorDialogListener) getActivity())
                                        .showSyncErrorDialog(DIALOG_SYNC_SANITY_ERROR_CONFIRM_KEEP_REMOTE);
                            }
                        })
                        .show();

            case DIALOG_SYNC_SANITY_ERROR_CONFIRM_KEEP_LOCAL:
                // Confirmation before pushing local collection to server after sanity check error
                return builder.positiveText(res().getString(R.string.dialog_positive_overwrite))
                        .negativeText(res().getString(R.string.dialog_cancel))
                        .callback(new MaterialDialog.ButtonCallback() {
                            @Override
                            public void onPositive(MaterialDialog dialog) {
                                ((SyncErrorDialogListener) getActivity()).sync("upload");
                                dismissAllDialogFragments();
                            }
                        })
                        .show();

            case DIALOG_SYNC_SANITY_ERROR_CONFIRM_KEEP_REMOTE:
                // Confirmation before overwriting local collection with server collection after sanity check error
                return builder.positiveText(res().getString(R.string.dialog_positive_overwrite))
                        .negativeText(res().getString(R.string.dialog_cancel))
                        .callback(new MaterialDialog.ButtonCallback() {
                            @Override
                            public void onPositive(MaterialDialog dialog) {
                                ((SyncErrorDialogListener) getActivity()).sync("download");
                                dismissAllDialogFragments();
                            }
                        })
                        .show();
            case DIALOG_MEDIA_SYNC_ERROR:
                return builder.positiveText(R.string.check_media)
                        .negativeText(R.string.cancel)
                        .callback(new MaterialDialog.ButtonCallback() {
                            @Override
                            public void onPositive(MaterialDialog dialog) {
                                ((SyncErrorDialogListener) getActivity()).mediaCheck();
                                dismissAllDialogFragments();
                            }
                        })
                        .show();
            default:
                return null;
        }
    }


    private String getTitle() {
        switch (getArguments().getInt("dialogType")) {
            case DIALOG_USER_NOT_LOGGED_IN_SYNC:
                return res().getString(R.string.not_logged_in_title);
            case DIALOG_SYNC_CONFLICT_CONFIRM_KEEP_LOCAL:
            case DIALOG_SYNC_CONFLICT_CONFIRM_KEEP_REMOTE:
            case DIALOG_SYNC_CONFLICT_RESOLUTION:
                return res().getString(R.string.sync_conflict_title);
            default:
                return res().getString(R.string.sync_error);
        }
    }


    /**
     * Get the title which is shown in notification bar when dialog fragment can't be shown
     * 
     * @return tile to be shown in notification in bar
     */
    @Override
    public String getNotificationTitle() {
        switch (getArguments().getInt("dialogType")) {
            case DIALOG_USER_NOT_LOGGED_IN_SYNC:
                return res().getString(R.string.sync_error);
            default:
                return getTitle();
        }
    }


    private String getMessage() {
        switch (getArguments().getInt("dialogType")) {
            case DIALOG_USER_NOT_LOGGED_IN_SYNC:
                return res().getString(R.string.login_create_account_message);
            case DIALOG_CONNECTION_ERROR:
                return res().getString(R.string.connection_error_message);
            case DIALOG_SYNC_CONFLICT_RESOLUTION:
                return res().getString(R.string.sync_conflict_message);
            case DIALOG_SYNC_CONFLICT_CONFIRM_KEEP_LOCAL:
                return res().getString(R.string.sync_conflict_local_confirm);
            case DIALOG_SYNC_CONFLICT_CONFIRM_KEEP_REMOTE:
                return res().getString(R.string.sync_conflict_remote_confirm);
            case DIALOG_SYNC_SANITY_ERROR_CONFIRM_KEEP_LOCAL:
                return res().getString(R.string.sync_conflict_local_confirm);
            case DIALOG_SYNC_SANITY_ERROR_CONFIRM_KEEP_REMOTE:
                return res().getString(R.string.sync_conflict_remote_confirm);
            default:
                return getArguments().getString("dialogMessage");
        }
    }


    /**
     * Get the message which is shown in notification bar when dialog fragment can't be shown
     * 
     * @return message to be shown in notification in bar
     */
    @Override
    public String getNotificationMessage() {
        switch (getArguments().getInt("dialogType")) {
            case DIALOG_USER_NOT_LOGGED_IN_SYNC:
                return res().getString(R.string.not_logged_in_title);
            default:
                return getMessage();
        }
    }

    @Override
    public Message getDialogHandlerMessage() {
        Message msg = Message.obtain();
        msg.what = DialogHandler.MSG_SHOW_SYNC_ERROR_DIALOG;
        Bundle b = new Bundle();
        b.putInt("dialogType", getArguments().getInt("dialogType"));
        b.putString("dialogMessage", getArguments().getString("dialogMessage"));
        msg.setData(b);
        return msg;
    }

    public void dismissAllDialogFragments() {
        ((SyncErrorDialogListener) getActivity()).dismissAllDialogFragments();
    }
}
