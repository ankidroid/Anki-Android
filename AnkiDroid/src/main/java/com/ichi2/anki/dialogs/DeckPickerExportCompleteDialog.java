
package com.ichi2.anki.dialogs;

import android.os.Bundle;
import android.os.Message;

import com.afollestad.materialdialogs.MaterialDialog;
import com.ichi2.anki.CollectionHelper;
import com.ichi2.anki.DeckPicker;
import com.ichi2.anki.R;
import com.ichi2.compat.CompatHelper;

import java.io.File;

public class DeckPickerExportCompleteDialog extends AsyncDialogFragment {
    
    public static DeckPickerExportCompleteDialog newInstance(String exportPath) {
        DeckPickerExportCompleteDialog f = new DeckPickerExportCompleteDialog();
        Bundle args = new Bundle();
        args.putString("exportPath", exportPath);
        f.setArguments(args);
        return f;
    }


    @Override
    public MaterialDialog onCreateDialog(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final String exportPath = getArguments().getString("exportPath");
        MaterialDialog.Builder dialogBuilder = new MaterialDialog.Builder(getActivity())
                .title(getNotificationTitle())
                .content(getNotificationMessage())
                .iconAttr(R.attr.dialogSendIcon)
                .positiveText(R.string.export_send_button)
                .negativeText(R.string.dialog_cancel)
                .onPositive((dialog, which) -> {
                    ((DeckPicker) getActivity()).dismissAllDialogFragments();
                    ((DeckPicker) getActivity()).emailFile(exportPath);
                })
                .onNegative((dialog, which) -> ((DeckPicker) getActivity()).dismissAllDialogFragments());

        // If they have the system storage framework, allow for a save option
        if (CompatHelper.getSdkVersion() >= 19) {
            dialogBuilder
                    .neutralText(R.string.dialog_cancel)
                    .onNeutral((dialog, which) -> ((DeckPicker) getActivity()).dismissAllDialogFragments())
                    .negativeText(R.string.export_save_button)
                    .onNegative((dialog, which) -> {
                        ((DeckPicker) getActivity()).dismissAllDialogFragments();
                        ((DeckPicker) getActivity()).saveExportFile(exportPath);
                    });
        }

        return dialogBuilder.show();
    }
    
    public String getNotificationTitle() {
        return res().getString(R.string.export_successful_title);
    }


    public String getNotificationMessage() {
        if (CollectionHelper.getCurrentAnkiDroidDirectory(getContext()).equals(CollectionHelper.getDefaultAnkiDroidDirectory())) {
            // Show a relative path if the collection is stored in the default location
            File exportFile = new File("AnkiDroid/export/", new File(getArguments().getString("exportPath")).getName());
            return res().getString(R.string.export_successful, exportFile.getPath());
        } else {
            // Show the absolute path if the user has messed with the AnkiDroid directory
            return res().getString(R.string.export_successful, getArguments().getString("exportPath"));
        }
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
