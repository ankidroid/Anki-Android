
package com.ichi2.anki.dialogs;

import android.content.DialogInterface;
import android.os.Bundle;

import com.ichi2.anki.R;
import com.ichi2.themes.StyledDialog;

import java.util.ArrayList;
import java.util.List;

public class MediaCheckDialog extends AsyncDialogFragment {
    public static final int DIALOG_CONFIRM_MEDIA_CHECK = 0;
    public static final int DIALOG_MEDIA_CHECK_RESULTS = 1;
    public static String CLASS_NAME_TAG = "MediaCheckDialog";

    public interface MediaCheckDialogListener {
        public void showMediaCheckDialog(int dialogType);


        public void showMediaCheckDialog(int dialogType, List<List<String>> checkList);


        public void mediaCheck();


        public void deleteUnused(List<String> unused);


        public void dismissAllDialogFragments();
    }


    public static MediaCheckDialog newInstance(int dialogType) {
        MediaCheckDialog f = new MediaCheckDialog();
        Bundle args = new Bundle();
        args.putInt("dialogType", dialogType);
        f.setArguments(args);
        return f;
    }


    public static MediaCheckDialog newInstance(int dialogType, ArrayList<String> nohave, ArrayList<String> unused,
            ArrayList<String> invalid) {
        MediaCheckDialog f = new MediaCheckDialog();
        Bundle args = new Bundle();
        args.putStringArrayList("nohave", nohave);
        args.putStringArrayList("unused", unused);
        args.putStringArrayList("invalid", invalid);
        args.putInt("dialogType", dialogType);
        f.setArguments(args);
        return f;
    }


    public static MediaCheckDialog newInstance(int dialogType, List<List<String>> checkList) {
        return newInstance(dialogType, new ArrayList<String>(checkList.get(0)),
                new ArrayList<String>(checkList.get(1)), new ArrayList<String>(checkList.get(2)));
    }


    @Override
    public StyledDialog onCreateDialog(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        StyledDialog.Builder builder = new StyledDialog.Builder(getActivity());

        switch (getArguments().getInt("dialogType")) {
            case DIALOG_CONFIRM_MEDIA_CHECK:
                builder.setTitle(getNotificationTitle());
                builder.setMessage(getNotificationMessage());
                builder.setPositiveButton(res().getString(R.string.dialog_ok), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        ((MediaCheckDialogListener) getActivity()).mediaCheck();
                        ((MediaCheckDialogListener) getActivity()).dismissAllDialogFragments();
                    }
                });
                builder.setNegativeButton(res().getString(R.string.dialog_cancel), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        ((MediaCheckDialogListener) getActivity()).dismissAllDialogFragments();
                    }
                });
                setCancelable(true);
                return builder.create();
            case DIALOG_MEDIA_CHECK_RESULTS:
                builder.setTitle(getNotificationMessage());
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

                // If we have unused files, show a dialog with a "delete" button. Otherwise, the user only
                // needs to acknowledge the results, so show only an OK dialog.
                if (unused.size() > 0) {
                    builder.setMessage(report);
                    builder.setPositiveButton(res().getString(R.string.dialog_ok), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            ((MediaCheckDialogListener) getActivity()).dismissAllDialogFragments();
                        }
                    });
                    builder.setNegativeButton(res().getString(R.string.check_media_delete_unused),
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    ((MediaCheckDialogListener) getActivity()).deleteUnused(unused);
                                    dismissAllDialogFragments();
                                }
                            });
                } else {
                    builder.setMessage(report);
                    builder.setPositiveButton(res().getString(R.string.dialog_ok), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            ((MediaCheckDialogListener) getActivity()).dismissAllDialogFragments();
                        }
                    });
                }
                setCancelable(true);
                return builder.create();
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
            case DIALOG_MEDIA_CHECK_RESULTS:
                return res().getString(R.string.check_media_acknowledge);
            default:
                return res().getString(R.string.app_name);
        }
    }


    @Override
    public String getNotificationTitle() {
        switch (getArguments().getInt("dialogType")) {
            case DIALOG_CONFIRM_MEDIA_CHECK:
                return res().getString(R.string.check_media_title);
            default:
                return res().getString(R.string.app_name);
        }
    }


    @Override
    public Bundle getNotificationIntentExtras() {
        Bundle b = new Bundle();
        b.putAll(getArguments());
        b.putBoolean("showAsyncDialogFragment", true);
        b.putString("dialogClass", CLASS_NAME_TAG);
        return b;
    }
}
