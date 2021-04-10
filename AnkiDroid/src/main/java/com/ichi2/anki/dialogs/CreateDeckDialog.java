/****************************************************************************************
 * Copyright (c) 2021 Akshay Jadhav <jadhavakshay0701@gmail.com>                        *
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

import android.content.Context;
import android.widget.EditText;
import android.widget.TextView;

import com.afollestad.materialdialogs.MaterialDialog;
import com.ichi2.anki.AnkiActivity;
import com.ichi2.anki.R;
import com.ichi2.anki.UIUtils;
import com.ichi2.anki.exception.FilteredAncestor;
import com.ichi2.libanki.Decks;
import com.ichi2.ui.FixedEditText;

import java.util.ArrayList;
import java.util.Locale;
import java.util.function.Consumer;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import timber.log.Timber;

public class CreateDeckDialog {

    private final EditText mDialogEditText;
    private final MaterialDialog.Builder mBuilder;
    private final Context mContext;
    private final int mTitle;
    private final AnkiActivity mAnkiActivity;
    private final boolean mIsFilteredDeck;
    private final Long mParentId;
    public Consumer<Long> mOnNewDeckCreated;

    public CreateDeckDialog(@NonNull Context context, @NonNull int title, @NonNull boolean isFilteredDeck, @Nullable Long parentId) {
        this.mContext = context;
        this.mTitle = title;
        this.mParentId = parentId;
        this.mIsFilteredDeck = isFilteredDeck;
        this.mDialogEditText = new FixedEditText(context);
        mAnkiActivity = new AnkiActivity();
        mBuilder = new MaterialDialog.Builder(context);
    }

    public void showFilteredDeckDialog() {
        Timber.i("DeckPicker:: New filtered deck button pressed");
        ArrayList<String> names = mAnkiActivity.getCol().getDecks().allNames();
        int n = 1;
        String name = String.format(Locale.getDefault(), "%s %d", mContext.getResources().getString(R.string.filtered_deck_name), n);
        while (names.contains(name)) {
            n++;
            name = String.format(Locale.getDefault(), "%s %d", mContext.getResources().getString(R.string.filtered_deck_name), n);
        }
        mDialogEditText.setText(name);

        showDialog();
    }

    public String getDeckName() {
        return mDialogEditText.getText().toString();
    }

    public MaterialDialog showDialog() {
        mDialogEditText.setSingleLine(true);
        mDialogEditText.setId(R.id.action_edit);

        return mBuilder.title(mTitle)
                .positiveText(R.string.dialog_ok)
                .customView(mDialogEditText, true)
                .negativeText(R.string.dialog_cancel)
                .onPositive((dialog, which) -> onPositiveButtonClicked())
                .show();
    }

    public void closeDialog() {
        mBuilder.build().dismiss();
    }

    public void createSubDeck(@NonNull long did, @Nullable String deckName) {
        String deckNameWithParentName = mAnkiActivity.getCol().getDecks().getSubdeckName(did, deckName);
        createDeck(deckNameWithParentName);
    }

    public void createDeck(@NonNull String deckName) {
        if (Decks.isValidDeckName(deckName)) {
            createNewDeck(deckName);
        } else {
            Timber.d("configureFloatingActionsMenu::addDeckButton::onPositiveListener - Not creating invalid deck name '%s'", deckName);
            UIUtils.showThemedToast(mContext, mContext.getString(R.string.invalid_deck_name), false);
        }
        closeDialog();
    }

    public boolean createFilteredDeck(@NonNull String deckName) {
        try {
            // create filtered deck
            Timber.i("DeckPicker:: Creating filtered deck...");
            mOnNewDeckCreated.accept(mAnkiActivity.getCol().getDecks().newDyn(deckName));
        } catch (FilteredAncestor filteredAncestor) {
            UIUtils.showThemedToast(mContext, mContext.getString(R.string.decks_rename_filtered_nosubdecks), false);
            return false;
        }
        return true;
    }

    private boolean createNewDeck(@NonNull String deckName) {
        try {
            // create normal deck or sub deck
            Timber.i("DeckPicker:: Creating new deck...");
            mOnNewDeckCreated.accept(mAnkiActivity.getCol().getDecks().id(deckName));
        } catch (FilteredAncestor filteredAncestor) {
            Timber.w(filteredAncestor);
            return false;
        }
        return true;
    }

    private void onPositiveButtonClicked() {
        if(!getDeckName().isEmpty()) {
            if (mParentId != null) {
                // create sub deck
                createSubDeck(mParentId, getDeckName());
            } else if (mIsFilteredDeck) {
                // create filtered deck
                createFilteredDeck(getDeckName());
            } else {
                // create deck
                createDeck(getDeckName());
            }
        }
    }

    public void setOnNewDeckCreated(Consumer<Long> c) {
        this.mOnNewDeckCreated = c;
    }
}
