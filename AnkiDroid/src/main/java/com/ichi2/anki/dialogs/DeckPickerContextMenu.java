/****************************************************************************************
 * Copyright (c) 2015 Timothy Rae <perceptualchaos2@gmail.com>                          *
 *                                                                                      *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 *                                                                                      *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/
package com.ichi2.anki.dialogs;

import android.app.Dialog;
import android.content.res.Resources;
import android.os.Bundle;
import android.view.View;

import com.afollestad.materialdialogs.MaterialDialog;
import com.ichi2.anki.AnkiActivity;
import com.ichi2.anki.CollectionHelper;
import com.ichi2.anki.DeckPicker;
import com.ichi2.anki.R;
import com.ichi2.anki.StudyOptionsFragment;
import com.ichi2.anki.analytics.AnalyticsDialogFragment;
import com.ichi2.libanki.Collection;

import java.util.ArrayList;
import java.util.HashMap;

import timber.log.Timber;

public class DeckPickerContextMenu extends AnalyticsDialogFragment {
    /**
     * Context Menus
     */
    private static final int CONTEXT_MENU_RENAME_DECK = 0;
    private static final int CONTEXT_MENU_DECK_OPTIONS = 1;
    private static final int CONTEXT_MENU_CUSTOM_STUDY = 2;
    private static final int CONTEXT_MENU_DELETE_DECK = 3;
    private static final int CONTEXT_MENU_EXPORT_DECK = 4;
    private static final int CONTEXT_MENU_UNBURY = 5;
    private static final int CONTEXT_MENU_CUSTOM_STUDY_REBUILD = 6;
    private static final int CONTEXT_MENU_CUSTOM_STUDY_EMPTY = 7;


    public static DeckPickerContextMenu newInstance(long did) {
        DeckPickerContextMenu f = new DeckPickerContextMenu();
        Bundle args = new Bundle();
        args.putLong("did", did);
        f.setArguments(args);
        return f;
    }


    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        long did = getArguments().getLong("did");
        String title = CollectionHelper.getInstance().getCol(getContext()).getDecks().name(did);
        int[] itemIds = getListIds();
        return new MaterialDialog.Builder(getActivity())
                .title(title)
                .cancelable(true)
                .autoDismiss(false)
                .itemsIds(itemIds)
                .items(ContextMenuHelper.getValuesFromKeys(getKeyValueMap(), itemIds))
                .itemsCallback(mContextMenuListener)
                .build();
    }


    private HashMap<Integer, String> getKeyValueMap() {
        Resources res = getResources();
        HashMap<Integer, String> keyValueMap = new HashMap<>();
        keyValueMap.put(CONTEXT_MENU_RENAME_DECK, res.getString(R.string.rename_deck));
        keyValueMap.put(CONTEXT_MENU_DECK_OPTIONS, res.getString(R.string.study_options));
        keyValueMap.put(CONTEXT_MENU_CUSTOM_STUDY, res.getString(R.string.custom_study));
        keyValueMap.put(CONTEXT_MENU_DELETE_DECK, res.getString(R.string.contextmenu_deckpicker_delete_deck));
        keyValueMap.put(CONTEXT_MENU_EXPORT_DECK, res.getString(R.string.export_deck));
        keyValueMap.put(CONTEXT_MENU_UNBURY, res.getString(R.string.unbury));
        keyValueMap.put(CONTEXT_MENU_CUSTOM_STUDY_REBUILD, res.getString(R.string.rebuild_cram_label));
        keyValueMap.put(CONTEXT_MENU_CUSTOM_STUDY_EMPTY, res.getString(R.string.empty_cram_label));
        return keyValueMap;
    }

    /**
     * Retrieve the list of ids to put in the context menu list
     * @return the ids of which values to show
     */
    private int[] getListIds() {
        Collection col = CollectionHelper.getInstance().getCol(getContext());
        long did = getArguments().getLong("did");
        ArrayList<Integer> itemIds = new ArrayList<>();
        if (col.getDecks().isDyn(did)) {
            itemIds.add(CONTEXT_MENU_CUSTOM_STUDY_REBUILD);
            itemIds.add(CONTEXT_MENU_CUSTOM_STUDY_EMPTY);
        }
        itemIds.add(CONTEXT_MENU_RENAME_DECK);
        itemIds.add(CONTEXT_MENU_DECK_OPTIONS);
        if (!col.getDecks().isDyn(did)) {
            itemIds.add(CONTEXT_MENU_CUSTOM_STUDY);
        }
        itemIds.add(CONTEXT_MENU_DELETE_DECK);
        itemIds.add(CONTEXT_MENU_EXPORT_DECK);
        if (col.getSched().haveBuried(did)) {
            itemIds.add(CONTEXT_MENU_UNBURY);
        }
        return ContextMenuHelper.integerListToArray(itemIds);
    }

    // Handle item selection on context menu which is shown when the user long-clicks on a deck
    private MaterialDialog.ListCallback mContextMenuListener = new MaterialDialog.ListCallback() {
        @Override
        public void onSelection(MaterialDialog materialDialog, View view, int item,
                CharSequence charSequence) {
            switch (view.getId()) {
                case CONTEXT_MENU_DELETE_DECK:
                    Timber.i("Delete deck selected");
                    ((DeckPicker) getActivity()).confirmDeckDeletion();
                    break;

                case CONTEXT_MENU_DECK_OPTIONS:
                    Timber.i("Open deck options selected");
                    ((DeckPicker) getActivity()).showContextMenuDeckOptions();
                    ((AnkiActivity) getActivity()).dismissAllDialogFragments();
                    break;
                case CONTEXT_MENU_CUSTOM_STUDY: {
                    Timber.i("Custom study option selected");
                    long did = getArguments().getLong("did");
                    CustomStudyDialog d = CustomStudyDialog.newInstance(
                            CustomStudyDialog.CONTEXT_MENU_STANDARD, did);
                    ((AnkiActivity) getActivity()).showDialogFragment(d);
                    break;
                }
                case CONTEXT_MENU_RENAME_DECK:
                    Timber.i("Rename deck selected");
                    ((DeckPicker) getActivity()).renameDeckDialog();
                    break;

                case CONTEXT_MENU_EXPORT_DECK:
                    Timber.i("Export deck selected");
                    ((DeckPicker) getActivity()).showContextMenuExportDialog();
                    break;

                case CONTEXT_MENU_UNBURY: {
                    Timber.i("Unbury deck selected");
                    Collection col = CollectionHelper.getInstance().getCol(getContext());
                    col.getSched().unburyCardsForDeck(getArguments().getLong("did"));
                    ((StudyOptionsFragment.StudyOptionsListener) getActivity()).onRequireDeckListUpdate();
                    ((AnkiActivity) getActivity()).dismissAllDialogFragments();
                    break;
                }
                case CONTEXT_MENU_CUSTOM_STUDY_REBUILD: {
                    Timber.i("Empty deck selected");
                    ((DeckPicker) getActivity()).rebuildFiltered();
                    ((AnkiActivity) getActivity()).dismissAllDialogFragments();
                    break;
                }
                case CONTEXT_MENU_CUSTOM_STUDY_EMPTY: {
                    Timber.i("Empty deck selected");
                    ((DeckPicker) getActivity()).emptyFiltered();
                    ((AnkiActivity) getActivity()).dismissAllDialogFragments();
                    break;
                }
            }
        }
    };
}
