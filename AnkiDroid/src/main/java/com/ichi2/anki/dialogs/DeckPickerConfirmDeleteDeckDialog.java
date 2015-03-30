
package com.ichi2.anki.dialogs;

import android.app.Dialog;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;

import com.afollestad.materialdialogs.MaterialDialog;
import com.ichi2.anki.DeckPicker;
import com.ichi2.anki.R;
import com.ichi2.themes.Themes;

public class DeckPickerConfirmDeleteDeckDialog extends DialogFragment {
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
        Drawable icon = res.getDrawable(R.drawable.ic_warning_black_36dp);
        icon.setAlpha(Themes.ALPHA_ICON_ENABLED_DARK);
        return new MaterialDialog.Builder(getActivity())
                .title(res.getString(R.string.delete_deck_title))
                .content(getArguments().getString("dialogMessage"))
                .icon(icon)
                .positiveText(res.getString(R.string.dialog_positive_delete))
                .negativeText(res.getString(R.string.dialog_cancel))
                .cancelable(true)
                .callback(new MaterialDialog.ButtonCallback() {
                    @Override
                    public void onPositive(MaterialDialog dialog) {
                        ((DeckPicker) getActivity()).deleteContextMenuDeck();
                        ((DeckPicker) getActivity()).dismissAllDialogFragments();
                    }

                    @Override
                    public void onNegative(MaterialDialog dialog) {
                        ((DeckPicker) getActivity()).dismissAllDialogFragments();
                    }
                })
                .build();

    }
}
