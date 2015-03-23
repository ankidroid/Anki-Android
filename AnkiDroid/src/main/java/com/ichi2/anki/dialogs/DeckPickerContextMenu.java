
package com.ichi2.anki.dialogs;

import android.content.DialogInterface;
import android.content.res.Resources;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;

import com.ichi2.anki.DeckPicker;
import com.ichi2.anki.R;
import com.ichi2.themes.StyledDialog;

import timber.log.Timber;

public class DeckPickerContextMenu extends DialogFragment {
    /**
     * Context Menus
     */
    private static final int CONTEXT_MENU_COLLAPSE_DECK = 0;
    private static final int CONTEXT_MENU_RENAME_DECK = 1;
    private static final int CONTEXT_MENU_DECK_OPTIONS = 2;
    private static final int CONTEXT_MENU_DELETE_DECK = 3;
    private static final int CONTEXT_MENU_EXPORT_DECK = 4;


    public static DeckPickerContextMenu newInstance(String dialogTitle, boolean isCollapsed) {
        DeckPickerContextMenu f = new DeckPickerContextMenu();
        Bundle args = new Bundle();
        args.putString("dialogTitle", dialogTitle);
        args.putBoolean("isCollapsed", isCollapsed);
        f.setArguments(args);
        return f;
    }


    @Override
    public StyledDialog onCreateDialog(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        StyledDialog.Builder builder = new StyledDialog.Builder(getActivity());
        String[] entries = new String[5];
        Resources res = getResources();
        entries[CONTEXT_MENU_COLLAPSE_DECK] = res.getString(R.string.contextmenu_deckpicker_collapse_deck);
        entries[CONTEXT_MENU_RENAME_DECK] = res.getString(R.string.contextmenu_deckpicker_rename_deck);
        entries[CONTEXT_MENU_DECK_OPTIONS] = res.getString(R.string.study_options);
        entries[CONTEXT_MENU_DELETE_DECK] = res.getString(R.string.contextmenu_deckpicker_delete_deck);
        entries[CONTEXT_MENU_EXPORT_DECK] = res.getString(R.string.export);
        builder.setTitle("Context Menu");
        // No icon
        builder.setItems(entries, mContextMenuListener);
        builder.setTitle(getArguments().getString("dialogTitle"));
        StyledDialog dialog = builder.create();
        // Toggle the collapse / inflate deck item based on actual collapsed state
        dialog.changeListItem(
                CONTEXT_MENU_COLLAPSE_DECK,
                getResources().getString(
                        getArguments().getBoolean("isCollapsed") ? R.string.contextmenu_deckpicker_inflate_deck
                                : R.string.contextmenu_deckpicker_collapse_deck));

        setCancelable(true);
        return dialog;
    }

    // Handle item selection on context menu which is shown when the user long-clicks on a deck
    private DialogInterface.OnClickListener mContextMenuListener = new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int item) {
            switch (item) {
                case CONTEXT_MENU_COLLAPSE_DECK:
                    Timber.i("Collapse deck selected");
                    ((DeckPicker) getActivity()).collapseContextMenuDeck();
                    return;
                case CONTEXT_MENU_DELETE_DECK:
                    Timber.i("Delete deck selected");
                    ((DeckPicker) getActivity()).confirmDeckDeletion();
                    return;

                case CONTEXT_MENU_DECK_OPTIONS:
                    Timber.i("Open deck options selected");
                    ((DeckPicker) getActivity()).showContextMenuDeckOptions();
                    return;

                case CONTEXT_MENU_RENAME_DECK:
                    Timber.i("Rename deck selected");
                    ((DeckPicker) getActivity()).renameContextMenuDeckDialog();
                    return;

                case CONTEXT_MENU_EXPORT_DECK:
                    Timber.i("Export deck selected");
                    ((DeckPicker) getActivity()).showContextMenuExportDialog();
                    return;

            }
        }
    };

}
