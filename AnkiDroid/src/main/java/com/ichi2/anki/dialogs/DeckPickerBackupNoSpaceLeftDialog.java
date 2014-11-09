package com.ichi2.anki.dialogs;

import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.res.Resources;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;

import com.ichi2.anki.BackupManager;
import com.ichi2.anki.DeckPicker;
import com.ichi2.anki.R;
import com.ichi2.themes.StyledDialog;

public class DeckPickerBackupNoSpaceLeftDialog extends DialogFragment {
    public static DeckPickerBackupNoSpaceLeftDialog newInstance() {
        DeckPickerBackupNoSpaceLeftDialog f = new DeckPickerBackupNoSpaceLeftDialog();
        return f;        
    }
    
    @Override
    public StyledDialog onCreateDialog(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        StyledDialog.Builder builder = new StyledDialog.Builder(getActivity());
        Resources res = getResources();
        builder.setTitle(res.getString(R.string.sd_card_almost_full_title));
        builder.setMessage(res.getString(R.string.sd_space_warning, BackupManager.MIN_FREE_SPACE));
        builder.setPositiveButton(res.getString(R.string.dialog_ok), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                ((DeckPicker)getActivity()).finishWithoutAnimation();
            }
        });
        builder.setCancelable(true);
        builder.setOnCancelListener(new OnCancelListener() {
            @Override
            public void onCancel(DialogInterface arg0) {
                ((DeckPicker)getActivity()).finishWithoutAnimation();
            }
        });
        return builder.create();
    }
}