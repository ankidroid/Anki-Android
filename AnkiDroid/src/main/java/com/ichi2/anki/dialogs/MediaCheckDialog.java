
package com.ichi2.anki.dialogs;

import android.os.Bundle;
import android.os.Message;

import com.afollestad.materialdialogs.MaterialDialog;
import com.ichi2.anki.R;

import java.util.ArrayList;
import java.util.List;

public class MediaCheckDialog extends AsyncDialogFragment {
    public static final int DIALOG_CONFIRM_MEDIA_CHECK = 0;
    public static final int DIALOG_MEDIA_CHECK_RESULTS = 1;

    public interface MediaCheckDialogListener {
        void showMediaCheckDialog(int dialogType);
        void showMediaCheckDialog(int dialogType, List<List<String>> checkList);
        void mediaCheck();
        void deleteUnused(List<String> unused);
        void dismissAllDialogFragments();
    }


    public static MediaCheckDialog newInstance(int dialogType) {
        MediaCheckDialog f = new MediaCheckDialog();
        Bundle args = new Bundle();
        args.putInt("dialogType", dialogType);
        f.setArguments(args);
        return f;
    }


    public static MediaCheckDialog newInstance(int dialogType, List<List<String>> checkList) {
        MediaCheckDialog f = new MediaCheckDialog();
        Bundle args = new Bundle();
        args.putStringArrayList("nohave", new ArrayList<>(checkList.get(0)));
        args.putStringArrayList("unused", new ArrayList<>(checkList.get(1)));
        args.putStringArrayList("invalid", new ArrayList<>(checkList.get(2)));
        args.putInt("dialogType", dialogType);
        f.setArguments(args);
        return f;
    }


    @Override
    public MaterialDialog onCreateDialog(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        MaterialDialog.Builder builder = new MaterialDialog.Builder(getActivity());
        builder.title(getNotificationTitle());

        switch (getArguments().getInt("dialogType")) {
            case DIALOG_CONFIRM_MEDIA_CHECK: {
                return builder.content(getNotificationMessage())
                        .positiveText(res().getString(R.string.dialog_ok))
                        .negativeText(res().getString(R.string.dialog_cancel))
                        .cancelable(true)
                        .onPositive((dialog, which) -> {
                            ((MediaCheckDialogListener) getActivity()).mediaCheck();
                            ((MediaCheckDialogListener) getActivity())
                                    .dismissAllDialogFragments();
                        })
                        .onNegative((dialog, which) -> ((MediaCheckDialogListener) getActivity())
                                .dismissAllDialogFragments())
                        .show();
            }
            case DIALOG_MEDIA_CHECK_RESULTS: {
                final ArrayList<String> nohave = getArguments().getStringArrayList("nohave");
                final ArrayList<String> unused = getArguments().getStringArrayList("unused");
                final ArrayList<String> invalid = getArguments().getStringArrayList("invalid");
                // Generate report
                String report = "";
                if (invalid.size() > 0) {
                    report += String.format(res().getString(R.string.check_media_invalid), invalid.size());
                }
                if (unused.size() > 0) {
                    if (report.length() > 0) {
                        report += "\n";
                    }
                    report += String.format(res().getString(R.string.check_media_unused), unused.size());
                }
                if (nohave.size() > 0) {
                    if (report.length() > 0) {
                        report += "\n";
                    }
                    report += String.format(res().getString(R.string.check_media_nohave), nohave.size());
                }

                if (report.length() == 0) {
                    report = res().getString(R.string.check_media_no_unused_missing);
                }

                // We also prefix the report with a message about the media db being rebuilt, since
                // we do a full media scan and update the db on each media check on AnkiDroid.
                report = res().getString(R.string.check_media_db_updated) + "\n\n" + report;
                builder.content(report)
                        .cancelable(true);

                // If we have unused files, show a dialog with a "delete" button. Otherwise, the user only
                // needs to acknowledge the results, so show only an OK dialog.
                if (unused.size() > 0) {
                    builder.positiveText(res().getString(R.string.dialog_ok))
                            .negativeText(res().getString(R.string.check_media_delete_unused))
                            .onPositive((dialog, which) -> ((MediaCheckDialogListener) getActivity())
                                    .dismissAllDialogFragments())
                            .onNegative((dialog, which) -> {
                                ((MediaCheckDialogListener) getActivity()).deleteUnused(unused);
                                dismissAllDialogFragments();
                            });
                } else {
                    builder.positiveText(res().getString(R.string.dialog_ok))
                            .onPositive((dialog, which) -> ((MediaCheckDialogListener) getActivity()).dismissAllDialogFragments());
                }
                return builder.show();
            }
            default:
                return null;
        }
    }


    public void dismissAllDialogFragments() {
        ((MediaCheckDialogListener) getActivity()).dismissAllDialogFragments();
    }


    @Override
    public String getNotificationMessage() {
        switch (getArguments().getInt("dialogType")) {
            case DIALOG_CONFIRM_MEDIA_CHECK:
                return res().getString(R.string.check_media_warning);
            default:
                return res().getString(R.string.app_name);
        }
    }


    @Override
    public String getNotificationTitle() {
        switch (getArguments().getInt("dialogType")) {
            case DIALOG_CONFIRM_MEDIA_CHECK:
                return res().getString(R.string.check_media_title);
            case DIALOG_MEDIA_CHECK_RESULTS:
                return res().getString(R.string.check_media_acknowledge);
            default:
                return res().getString(R.string.app_name);
        }
    }


    @Override
    public Message getDialogHandlerMessage() {
        Message msg = Message.obtain();
        msg.what = DialogHandler.MSG_SHOW_MEDIA_CHECK_COMPLETE_DIALOG;
        Bundle b = new Bundle();
        b.putStringArrayList("nohave", getArguments().getStringArrayList("nohave"));
        b.putStringArrayList("unused", getArguments().getStringArrayList("unused"));
        b.putStringArrayList("invalid", getArguments().getStringArrayList("invalid"));
        b.putInt("dialogType", getArguments().getInt("dialogType"));
        msg.setData(b);
        return msg;
    }
}
