/*
 Copyright (c) 2021 Tarek Mohamed Abdalla <tarekkma@gmail.com>

 This program is free software; you can redistribute it and/or modify it under
 the terms of the GNU General Public License as published by the Free Software
 Foundation; either version 3 of the License, or (at your option) any later
 version.

 This program is distributed in the hope that it will be useful, but WITHOUT ANY
 WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 PARTICULAR PURPOSE. See the GNU General Public License for more details.

 You should have received a copy of the GNU General Public License along with
 this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.export;

import android.util.Pair;

import com.afollestad.materialdialogs.MaterialDialog;
import com.ichi2.anki.AnkiActivity;
import com.ichi2.anki.R;
import com.ichi2.anki.UIUtils;
import com.ichi2.anki.dialogs.ExportCompleteDialog;
import com.ichi2.async.TaskListenerWithContext;
import com.ichi2.themes.StyledProgressDialog;

import androidx.annotation.NonNull;
import timber.log.Timber;

class ExportListener extends TaskListenerWithContext<AnkiActivity, Void, Pair<Boolean, String>> {
    private final ExportDialogsFactory mDialogsFactory;

    private MaterialDialog mProgressDialog;


    public ExportListener(AnkiActivity activity, ExportDialogsFactory dialogsFactory) {
        super(activity);
        this.mDialogsFactory = dialogsFactory;
    }


    @Override
    public void actualOnPreExecute(@NonNull AnkiActivity activity) {
        mProgressDialog = StyledProgressDialog.show(activity, "",
                activity.getResources().getString(R.string.export_in_progress), false);
    }


    @Override
    public void actualOnPostExecute(@NonNull AnkiActivity activity, Pair<Boolean, String> result) {
        if (mProgressDialog != null && mProgressDialog.isShowing()) {
            mProgressDialog.dismiss();
        }

        // If boolean and string are both set, we are signalling an error message
        // instead of a successful result.
        if (result.first && result.second != null) {
            Timber.w("Export Failed: %s", result.second);
            activity.showSimpleMessageDialog(result.second);
        } else {
            Timber.i("Export successful");
            String exportPath = result.second;
            if (exportPath != null) {
                final ExportCompleteDialog dialog = mDialogsFactory.newExportCompleteDialog().withArguments(exportPath);
                activity.showAsyncDialogFragment(dialog);
            } else {
                UIUtils.showThemedToast(activity, activity.getResources().getString(R.string.export_unsuccessful), true);
            }
        }
    }
}
