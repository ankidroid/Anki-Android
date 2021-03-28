
package com.ichi2.anki.dialogs;

import android.os.Bundle;
import android.os.Message;

import com.afollestad.materialdialogs.MaterialDialog;
import com.ichi2.anki.R;

import java.io.File;

import androidx.annotation.NonNull;

public class ExportCompleteDialog extends AsyncDialogFragment {

    public interface ExportCompleteDialogListener {
        void dismissAllDialogFragments();

        void emailFile(String path);

        void saveExportFile(String exportPath);
    }


    public static ExportCompleteDialog newInstance(String exportPath) {
        ExportCompleteDialog f = new ExportCompleteDialog();
        Bundle args = new Bundle();
        args.putString("exportPath", exportPath);
        f.setArguments(args);
        return f;
    }


    @NonNull
    @Override
    public MaterialDialog onCreateDialog(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final String exportPath = getArguments().getString("exportPath");
        MaterialDialog.Builder dialogBuilder = new MaterialDialog.Builder(getActivity())
                .title(getNotificationTitle())
                .content(getNotificationMessage())
                .iconAttr(R.attr.dialogSendIcon)
                .positiveText(R.string.export_send_button)
                .negativeText(R.string.export_save_button)
                .onPositive((dialog, which) -> {
                    ((ExportCompleteDialogListener) getActivity()).dismissAllDialogFragments();
                    ((ExportCompleteDialogListener) getActivity()).emailFile(exportPath);
                })
                .onNegative((dialog, which) -> {
                    ((ExportCompleteDialogListener) getActivity()).dismissAllDialogFragments();
                    ((ExportCompleteDialogListener) getActivity()).saveExportFile(exportPath);
                })
                .neutralText(R.string.dialog_cancel)
                .onNeutral((dialog, which) -> ((ExportCompleteDialogListener) getActivity()).dismissAllDialogFragments());
        return dialogBuilder.show();
    }
    
    public String getNotificationTitle() {
        return res().getString(R.string.export_successful_title);
    }


    public String getNotificationMessage() {
        File exportPath = new File(getArguments().getString("exportPath"));
        return res().getString(R.string.export_successful, exportPath.getName());
    }


    @Override
    public Message getDialogHandlerMessage() {
        Message msg = Message.obtain();
        msg.what = DialogHandler.MSG_SHOW_EXPORT_COMPLETE_DIALOG;
        Bundle b = new Bundle();
        b.putString("exportPath", getArguments().getString("exportPath"));
        msg.setData(b);
        return msg;
    } 
}
