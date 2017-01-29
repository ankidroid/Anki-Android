
package com.ichi2.anki.dialogs;

import android.os.Bundle;
import android.os.Message;

import com.afollestad.materialdialogs.MaterialDialog;
import com.ichi2.anki.CollectionHelper;
import com.ichi2.anki.DeckPicker;
import com.ichi2.anki.R;
import com.ichi2.anki.UIUtils;
import com.ichi2.utils.Files;

import java.io.File;

import timber.log.Timber;

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
        return new MaterialDialog.Builder(getActivity())
                .title(getNotificationTitle())
                .content(getNotificationMessage())
                .iconAttr(R.attr.dialogSendIcon)
                .positiveText(R.string.dialog_ok)
                .negativeText(R.string.dialog_cancel)
                .callback(new MaterialDialog.ButtonCallback() {
                    @Override
                    public void onPositive(MaterialDialog dialog) {
                        ((DeckPicker) getActivity()).dismissAllDialogFragments();
                        ((DeckPicker) getActivity()).emailFile(exportPath);
                    }

                    @Override
                    public void onNegative(MaterialDialog dialog) {
                        // Move the file to external storage so that it can be accessed by the user
                        File exportFile = new File(exportPath);
                        File colPath = new File(CollectionHelper.getCollectionPath(getContext())).getParentFile();
                        File newExportFile = new File(new File(colPath, "export"), exportFile.getName());
                        newExportFile.mkdirs();
                        if (!Files.move(exportFile, newExportFile)) {
                            Timber.e("Could not move exported apkg file to external storage");
                            UIUtils.showThemedToast(getContext(), getResources().getString(R.string.apk_share_error), false);
                        } else {
                            UIUtils.showThemedToast(getContext(), newExportFile.getAbsolutePath(), false);
                        }
                        ((DeckPicker) getActivity()).dismissAllDialogFragments();
                    }
                })
                .show();
    }
    
    public String getNotificationTitle() {
        return res().getString(R.string.export_successful_title);
    }


    public String getNotificationMessage() {
        String name = new File(getArguments().getString("exportPath")).getName();
        return res().getString(R.string.export_successful, name);
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
