
package com.ichi2.anki.dialogs;

import android.app.Dialog;
import android.content.res.Resources;
import android.os.Bundle;

import com.afollestad.materialdialogs.MaterialDialog;
import com.ichi2.anki.DeckPicker;
import com.ichi2.anki.R;
import com.ichi2.anki.analytics.AnalyticsDialogFragment;

public class DeckPickerConfirmDeleteDeckDialog extends AnalyticsDialogFragment {
    public static DeckPickerConfirmDeleteDeckDialog newInstance(String dialogMessage) {
        DeckPickerConfirmDeleteDeckDialog f = new DeckPickerConfirmDeleteDeckDialog();
        Bundle args = new Bundle();
        args.putString("dialogMessage", dialogMessage);
        f.setArguments(args);
        return f;
    }


    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Resources res = getResources();
        return new MaterialDialog.Builder(getActivity())
                .title(res.getString(R.string.delete_deck_title))
                .content(getArguments().getString("dialogMessage"))
                .iconAttr(R.attr.dialogErrorIcon)
                .positiveText(res.getString(R.string.dialog_positive_delete))
                .negativeText(res.getString(R.string.dialog_cancel))
                .cancelable(true)
                .onPositive((dialog, which) -> {
                    ((DeckPicker) getActivity()).deleteContextMenuDeck();
                    ((DeckPicker) getActivity()).dismissAllDialogFragments();
                })
                .onNegative((dialog, which) -> ((DeckPicker) getActivity()).dismissAllDialogFragments())
                .build();

    }
}
