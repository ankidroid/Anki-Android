
package com.ichi2.anki.dialogs;

import android.content.DialogInterface;
import android.content.res.Resources;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;

import com.ichi2.anki.DeckPicker;
import com.ichi2.anki.R;
import com.ichi2.themes.StyledDialog;

public class DeckPickerConfirmDeleteDeckDialog extends DialogFragment {
    public static DeckPickerConfirmDeleteDeckDialog newInstance(String dialogMessage) {
        DeckPickerConfirmDeleteDeckDialog f = new DeckPickerConfirmDeleteDeckDialog();
        Bundle args = new Bundle();
        args.putString("dialogMessage", dialogMessage);
        f.setArguments(args);
        return f;
    }


    @Override
    public StyledDialog onCreateDialog(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        StyledDialog.Builder builder = new StyledDialog.Builder(getActivity());
        Resources res = getResources();
        builder.setTitle(res.getString(R.string.delete_deck_title));
        builder.setMessage(getArguments().getString("dialogMessage"));
        builder.setIcon(R.drawable.ic_dialog_alert);
        builder.setPositiveButton(res.getString(R.string.dialog_positive_delete), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        ((DeckPicker) getActivity()).deleteContextMenuDeck();
                        ((DeckPicker) getActivity()).dismissAllDialogFragments();
                    }
                });
        builder.setNegativeButton(res.getString(R.string.dialog_cancel), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                ((DeckPicker) getActivity()).dismissAllDialogFragments();
            }
        });
        
        setCancelable(true);
        return builder.create();

    }
}
