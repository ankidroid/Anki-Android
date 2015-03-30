
package com.ichi2.anki.dialogs;

import android.app.Dialog;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.view.View;

import com.afollestad.materialdialogs.MaterialDialog;
import com.ichi2.anki.DeckPicker;
import com.ichi2.anki.R;
import com.ichi2.themes.Themes;

import timber.log.Timber;

public class DeckPickerContextMenu extends DialogFragment {
    /**
     * Context Menus
     */
    private static final int CONTEXT_MENU_RENAME_DECK = 0;
    private static final int CONTEXT_MENU_DECK_OPTIONS = 1;
    private static final int CONTEXT_MENU_DELETE_DECK = 2;
    private static final int CONTEXT_MENU_EXPORT_DECK = 3;
    private static final int CONTEXT_MENU_COLLAPSE_DECK = 4;


    public static DeckPickerContextMenu newInstance(String dialogTitle, boolean hasSubdecks,
            boolean isCollapsed) {
        DeckPickerContextMenu f = new DeckPickerContextMenu();
        Bundle args = new Bundle();
        args.putString("dialogTitle", dialogTitle);
        args.putBoolean("hasSubdecks", hasSubdecks);
        args.putBoolean("isCollapsed", isCollapsed);
        f.setArguments(args);
        return f;
    }


    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Resources res = getResources();
        Drawable icon = res.getDrawable(R.drawable.ic_settings_applications_black_36dp);
        icon.setAlpha(Themes.ALPHA_ICON_ENABLED_DARK);

        boolean hasSubdecks = getArguments().getBoolean("hasSubdecks");
        String[] entries = new String[hasSubdecks ? 5 : 4];
        entries[CONTEXT_MENU_RENAME_DECK] = res.getString(R.string.contextmenu_deckpicker_rename_deck);
        entries[CONTEXT_MENU_DECK_OPTIONS] = res.getString(R.string.study_options);
        entries[CONTEXT_MENU_DELETE_DECK] = res.getString(R.string.contextmenu_deckpicker_delete_deck);
        entries[CONTEXT_MENU_EXPORT_DECK] = res.getString(R.string.export);
        if (hasSubdecks) {
            entries[CONTEXT_MENU_COLLAPSE_DECK] = res.getString(
                    getArguments().getBoolean("isCollapsed") ?
                            R.string.contextmenu_deckpicker_inflate_deck :
                            R.string.contextmenu_deckpicker_collapse_deck);
        }
        return new MaterialDialog.Builder(getActivity())
                .title(getArguments().getString("dialogTitle"))
                .icon(icon)
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
                case CONTEXT_MENU_COLLAPSE_DECK:
                    Timber.i("Collapse deck selected");
                    ((DeckPicker) getActivity()).collapseContextMenuDeck();
                    return;
                case CONTEXT_MENU_DELETE_DECK:
                    Timber.i("Delete deck selected");
                    ((DeckPicker) getActivity()).confirmDeckDeletion(DeckPickerContextMenu.this);
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

            }
        }
    };

}
