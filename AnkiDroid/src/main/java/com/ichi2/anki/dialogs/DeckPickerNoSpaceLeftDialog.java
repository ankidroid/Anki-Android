package com.ichi2.anki.dialogs;

import android.content.res.Resources;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;

import com.afollestad.materialdialogs.MaterialDialog;
import com.ichi2.anki.DeckPicker;
import com.ichi2.anki.R;

public class DeckPickerNoSpaceLeftDialog extends DialogFragment {
    public static DeckPickerNoSpaceLeftDialog newInstance() {
        DeckPickerNoSpaceLeftDialog f = new DeckPickerNoSpaceLeftDialog();
        return f;
    }
    
    @Override
    public MaterialDialog onCreateDialog(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Resources res = getResources();
        return new MaterialDialog.Builder(getActivity())
                .title(res.getString(R.string.sd_card_full_title))
                .content(res.getString(R.string.backup_deck_no_space_left))
                .cancelable(true)
                .positiveText(res.getString(R.string.dialog_ok))
                .onPositive((dialog, which) -> ((DeckPicker) getActivity()).startLoadingCollection())
                .cancelListener(dialog -> ((DeckPicker) getActivity()).startLoadingCollection())
                .show();
    }
}