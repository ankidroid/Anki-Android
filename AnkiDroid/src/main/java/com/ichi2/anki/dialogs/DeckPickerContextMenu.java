
package com.ichi2.anki.dialogs;

import android.app.Dialog;
import android.content.res.Resources;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.view.View;

import com.afollestad.materialdialogs.MaterialDialog;
import com.ichi2.anki.AnkiActivity;
import com.ichi2.anki.DeckPicker;
import com.ichi2.anki.R;

import timber.log.Timber;

public class DeckPickerContextMenu extends DialogFragment {
    /**
     * Context Menus
     */
    private static final int CONTEXT_MENU_RENAME_DECK = 0;
    private static final int CONTEXT_MENU_DECK_OPTIONS = 1;
    private static final int CONTEXT_MENU_CUSTOM_STUDY = 2;
    private static final int CONTEXT_MENU_DELETE_DECK = 3;
    private static final int CONTEXT_MENU_EXPORT_DECK = 4;



    public static DeckPickerContextMenu newInstance(String dialogTitle) {
        DeckPickerContextMenu f = new DeckPickerContextMenu();
        Bundle args = new Bundle();
        args.putString("dialogTitle", dialogTitle);
        f.setArguments(args);
        return f;
    }


    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Resources res = getResources();
        String[] entries = new String[5];
        entries[CONTEXT_MENU_RENAME_DECK] = res.getString(R.string.contextmenu_deckpicker_rename_deck);
        entries[CONTEXT_MENU_DECK_OPTIONS] = res.getString(R.string.study_options);
        entries[CONTEXT_MENU_CUSTOM_STUDY] = res.getString(R.string.custom_study);
        entries[CONTEXT_MENU_DELETE_DECK] = res.getString(R.string.contextmenu_deckpicker_delete_deck);
        entries[CONTEXT_MENU_EXPORT_DECK] = res.getString(R.string.export);

        return new MaterialDialog.Builder(getActivity())
                .title(getArguments().getString("dialogTitle"))
                .cancelable(true)
                .autoDismiss(false)
                .items(entries)
                .itemsCallback(mContextMenuListener)
                .build();
    }

    // Handle item selection on context menu which is shown when the user long-clicks on a deck
    private MaterialDialog.ListCallback mContextMenuListener = new MaterialDialog.ListCallback() {
        @Override
        public void onSelection(MaterialDialog materialDialog, View view, int item,
                CharSequence charSequence) {
            switch (item) {
                case CONTEXT_MENU_DELETE_DECK:
                    Timber.i("Delete deck selected");
                    ((DeckPicker) getActivity()).confirmDeckDeletion(DeckPickerContextMenu.this);
                    return;

                case CONTEXT_MENU_DECK_OPTIONS:
                    Timber.i("Open deck options selected");
                    ((DeckPicker) getActivity()).showContextMenuDeckOptions();
                    return;
                case CONTEXT_MENU_CUSTOM_STUDY:
                    // TODO: hide this option when it's a filtered deck
                    Timber.i("Custom study option selected");
                    CustomStudyDialog d = CustomStudyDialog.newInstance(
                            CustomStudyDialog.CONTEXT_MENU_STANDARD);
                    ((AnkiActivity) getActivity()).showDialogFragment(d);
                    return;

                case CONTEXT_MENU_RENAME_DECK:
                    Timber.i("Rename deck selected");
                    ((DeckPicker) getActivity()).renameContextMenuDeckDialog();
                    return;

                case CONTEXT_MENU_EXPORT_DECK:
                    Timber.i("Export deck selected");
                    ((DeckPicker) getActivity()).showContextMenuExportDialog();

            }
        }
    };
}
