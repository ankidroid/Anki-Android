/*
 *  Copyright (c) 2021 David Allison <davidallisongithub@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.dialogs;

import android.content.Context;
import android.content.res.Resources;
import android.os.Bundle;

import com.afollestad.materialdialogs.MaterialDialog;
import com.ichi2.anki.BackupManager;
import com.ichi2.anki.DeckPicker;
import com.ichi2.anki.R;
import com.ichi2.anki.analytics.AnalyticsDialogFragment;

import java.io.File;

import androidx.annotation.NonNull;

public class DeckPickerNoSpaceToDowngradeDialog extends AnalyticsDialogFragment {
    private final FileSizeFormatter mFormatter;
    private final File mCollection;


    public DeckPickerNoSpaceToDowngradeDialog(FileSizeFormatter formatter, File collectionFile) {
        mFormatter = formatter;
        mCollection = collectionFile;
    }


    public static DeckPickerNoSpaceToDowngradeDialog newInstance(FileSizeFormatter formatter, File collectionFile) {
        return new DeckPickerNoSpaceToDowngradeDialog(formatter, collectionFile);
    }

    @NonNull
    @Override
    public MaterialDialog onCreateDialog(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Resources res = getResources();
        return new MaterialDialog.Builder(getActivity())
                .title(res.getString(R.string.no_space_to_downgrade_title))
                .content(res.getString(R.string.no_space_to_downgrade_content, getRequiredSpaceString()))
                .cancelable(false)
                .positiveText(R.string.close)
                .onPositive((dialog, which) -> ((DeckPicker) getActivity()).exit())
                .show();
    }

    private String getRequiredSpaceString() {
        return mFormatter.formatFileSize(getFreeSpaceRequired());
    }

    private long getFreeSpaceRequired() {
        return BackupManager.getRequiredFreeSpace(mCollection);
    }

    public static class FileSizeFormatter {
        private final Context mContext;

        public FileSizeFormatter(Context context) {
            mContext = context;
        }

        public String formatFileSize(long sizeInBytes) {
            return android.text.format.Formatter.formatShortFileSize(mContext, sizeInBytes);
        }
    }
}
