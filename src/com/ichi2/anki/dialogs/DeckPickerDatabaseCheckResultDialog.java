
package com.ichi2.anki.dialogs;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;

import com.ichi2.anim.ActivityTransitionAnimation;
import com.ichi2.anki.DeckPicker;
import com.ichi2.anki.R;
import com.ichi2.themes.StyledDialog;

public class DeckPickerDatabaseCheckResultDialog extends DialogFragment {
    public static DeckPickerDatabaseCheckResultDialog newInstance(String dialogMessage) {
        DeckPickerDatabaseCheckResultDialog f = new DeckPickerDatabaseCheckResultDialog();
        Bundle args = new Bundle();
        args.putString("dialogMessage", dialogMessage);
        f.setArguments(args);
        return f;
    }


    @Override
    public StyledDialog onCreateDialog(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        StyledDialog.Builder builder = new StyledDialog.Builder(getActivity());
        builder.setPositiveButton(R.string.dialog_ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                Intent deckPicker = new Intent(getActivity(), DeckPicker.class);
                deckPicker.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                ((DeckPicker) getActivity()).startActivityWithAnimation(deckPicker, ActivityTransitionAnimation.LEFT);                
            }
        });
        builder.setMessage(getArguments().getString("dialogMessage"));
        setCancelable(true);
        return builder.create();
    }
}
