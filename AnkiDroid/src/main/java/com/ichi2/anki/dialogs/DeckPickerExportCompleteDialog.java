
package com.ichi2.anki.dialogs;

import android.content.DialogInterface;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Message;

import com.ichi2.anki.DeckPicker;
import com.ichi2.anki.R;
import com.ichi2.themes.StyledDialog;

public class DeckPickerExportCompleteDialog extends AsyncDialogFragment {
    
    public static DeckPickerExportCompleteDialog newInstance(String exportPath) {
        DeckPickerExportCompleteDialog f = new DeckPickerExportCompleteDialog();
        Bundle args = new Bundle();
        args.putString("exportPath", exportPath);
        f.setArguments(args);
        return f;
    }


    @Override
    public StyledDialog onCreateDialog(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final String exportPath = getArguments().getString("exportPath");
        StyledDialog.Builder builder = new StyledDialog.Builder(getActivity());
        Resources res = getResources();
        builder.setTitle(getNotificationTitle());
        builder.setMessage(getNotificationMessage());
        builder.setIconID(R.drawable.ic_menu_send);
        builder.setPositiveButton(res.getString(R.string.dialog_ok), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                ((DeckPicker) getActivity()).dismissAllDialogFragments();
                ((DeckPicker) getActivity()).emailFile(exportPath);
            }
        });
        builder.setNegativeButton(res.getString(R.string.dialog_cancel), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                ((DeckPicker) getActivity()).dismissAllDialogFragments();
            }
        });
        return builder.create();
    }
    
    public String getNotificationTitle() {
        return res().getString(R.string.export_successful_title);
    }


    public String getNotificationMessage() {
        return res().getString(R.string.export_successful, getArguments().getString("exportPath"));
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
