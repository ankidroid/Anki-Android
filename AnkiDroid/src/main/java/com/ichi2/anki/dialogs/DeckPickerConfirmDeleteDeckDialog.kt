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

import com.afollestad.materialdialogs.MaterialDialog;
import com.ichi2.anki.DeckPicker;
import com.ichi2.anki.R;
import com.ichi2.anki.analytics.AnalyticsDialogFragment;

import androidx.annotation.NonNull;

public class DeckPickerConfirmDeleteDeckDialog extends AnalyticsDialogFragment {
    public static DeckPickerConfirmDeleteDeckDialog newInstance(String dialogMessage) {
        DeckPickerConfirmDeleteDeckDialog f = new DeckPickerConfirmDeleteDeckDialog();
        Bundle args = new Bundle();
        args.putString("dialogMessage", dialogMessage);
        f.setArguments(args);
        return f;
    }


    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Resources res = getResources();
        return new MaterialDialog.Builder(getActivity())
                .title(res.getString(R.string.delete_deck_title))
                .content(getArguments().getString("dialogMessage"))
                .iconAttr(R.attr.dialogErrorIcon)
                .positiveText(R.string.dialog_positive_delete)
                .negativeText(R.string.dialog_cancel)
                .cancelable(true)
                .onPositive((dialog, which) -> {
                    ((DeckPicker) getActivity()).deleteContextMenuDeck();
                    ((DeckPicker) getActivity()).dismissAllDialogFragments();
                })
                .onNegative((dialog, which) -> ((DeckPicker) getActivity()).dismissAllDialogFragments())
                .build();

    }
}
