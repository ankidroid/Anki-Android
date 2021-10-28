//noinspection MissingCopyrightHeader #8659
package com.ichi2.anki.dialogs;

import android.os.Bundle;
import android.os.Message;
import android.text.TextUtils;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.afollestad.materialdialogs.MaterialDialog;
import com.ichi2.anki.R;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import androidx.annotation.NonNull;

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


    @NonNull
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
                final StringBuilder report = new StringBuilder();
                if (!invalid.isEmpty()) {
                    report.append(String.format(res().getString(R.string.check_media_invalid), invalid.size()));
                }
                if (!unused.isEmpty()) {
                    if (report.length() > 0) {
                        report.append("\n");
                    }
                    report.append(String.format(res().getString(R.string.check_media_unused), unused.size()));
                }
                if (!nohave.isEmpty()) {
                    if (report.length() > 0) {
                        report.append("\n");
                    }
                    report.append(String.format(res().getString(R.string.check_media_nohave), nohave.size()));
                }

                if (report.length() == 0) {
                    report.append(res().getString(R.string.check_media_no_unused_missing));
                }

                // We also prefix the report with a message about the media db being rebuilt, since
                // we do a full media scan and update the db on each media check on AnkiDroid.
                final String reportStr = res().getString(R.string.check_media_db_updated) + "\n\n" + report.toString();

                LinearLayout dialogBody = (LinearLayout) getLayoutInflater().inflate(R.layout.media_check_dialog_body, null);
                TextView reportTextView = dialogBody.findViewById(R.id.reportTextView);
                TextView fileListTextView = dialogBody.findViewById(R.id.fileListTextView);

                reportTextView.setText(reportStr);

                if (!unused.isEmpty()) {
                    reportTextView.append(getString(R.string.unused_strings));

                    fileListTextView.append(TextUtils.join("\n", unused));

                    fileListTextView.setScrollbarFadingEnabled(unused.size() <= fileListTextView.getMaxLines());
                    fileListTextView.setMovementMethod(ScrollingMovementMethod.getInstance());

                    builder.negativeText(res().getString(R.string.dialog_cancel))
                            .positiveText(res().getString(R.string.check_media_delete_unused))
                            .onNegative((dialog, which) -> ((MediaCheckDialogListener) getActivity())
                                    .dismissAllDialogFragments())
                            .onPositive((dialog, which) -> {
                                ((MediaCheckDialogListener) getActivity()).deleteUnused(unused);
                                dismissAllDialogFragments();
                            });
                } else {
                    fileListTextView.setVisibility(View.GONE);
                    builder.negativeText(res().getString(R.string.dialog_ok))
                            .onNegative((dialog, which) -> ((MediaCheckDialogListener) getActivity()).dismissAllDialogFragments());
                }
                return builder
                        .customView(dialogBody, false)
                        .cancelable(false)
                        .show();
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
        switch (getArguments().getInt("dialogType") ) {
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
        if (getArguments().getInt("dialogType") == DIALOG_CONFIRM_MEDIA_CHECK) {
            return res().getString(R.string.check_media_title);
        }
        return res().getString(R.string.app_name);
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
