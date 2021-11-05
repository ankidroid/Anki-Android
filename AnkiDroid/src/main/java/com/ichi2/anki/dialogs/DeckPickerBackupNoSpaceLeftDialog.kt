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

import android.content.res.Resources;
import android.os.Bundle;

import com.afollestad.materialdialogs.MaterialDialog;
import com.ichi2.anki.BackupManager;
import com.ichi2.anki.CollectionHelper;
import com.ichi2.anki.DeckPicker;
import com.ichi2.anki.R;
import com.ichi2.anki.analytics.AnalyticsDialogFragment;

import androidx.annotation.NonNull;

public class DeckPickerBackupNoSpaceLeftDialog extends AnalyticsDialogFragment {
    public static DeckPickerBackupNoSpaceLeftDialog newInstance() {
        return new DeckPickerBackupNoSpaceLeftDialog();
    }
    
    @NonNull
    @Override
    public MaterialDialog onCreateDialog(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Resources res = getResources();
        long space = BackupManager.getFreeDiscSpace(CollectionHelper.getCollectionPath(getActivity()));
        return new MaterialDialog.Builder(getActivity())
                .title(res.getString(R.string.sd_card_almost_full_title))
                .content(res.getString(R.string.sd_space_warning, space/1024/1024))
                .positiveText(R.string.dialog_ok)
                .onPositive((dialog, which) -> ((DeckPicker) getActivity()).finishWithoutAnimation())
                .cancelable(true)
                .cancelListener(dialog -> ((DeckPicker) getActivity()).finishWithoutAnimation())
                .show();
    }
}