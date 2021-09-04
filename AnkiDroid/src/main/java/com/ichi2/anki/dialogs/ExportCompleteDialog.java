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

import android.os.Bundle;
import android.os.Message;

import com.afollestad.materialdialogs.MaterialDialog;
import com.ichi2.anki.R;

import java.io.File;

import androidx.annotation.NonNull;

public class ExportCompleteDialog extends AsyncDialogFragment {

    public interface ExportCompleteDialogListener {
        void dismissAllDialogFragments();

        void emailFile(String path);

        void saveExportFile(String exportPath);
    }

    @NonNull
    private final ExportCompleteDialogListener mListener;

    public ExportCompleteDialog(@NonNull ExportCompleteDialogListener listener) {
        mListener = listener;
    }

    public ExportCompleteDialog withArguments(String exportPath) {
        Bundle args = this.getArguments();
        if (args == null) {
            args = new Bundle();
        }
        args.putString("exportPath", exportPath);
        this.setArguments(args);
        return this;
    }


    @NonNull
    @Override
    public MaterialDialog onCreateDialog(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final String exportPath = requireArguments().getString("exportPath");
        MaterialDialog.Builder dialogBuilder = new MaterialDialog.Builder(requireActivity())
                .title(getNotificationTitle())
                .content(getNotificationMessage())
                .iconAttr(R.attr.dialogSendIcon)
                .positiveText(R.string.export_send_button)
                .negativeText(R.string.export_save_button)
                .onPositive((dialog, which) -> {
                    mListener.dismissAllDialogFragments();
                    mListener.emailFile(exportPath);
                })
                .onNegative((dialog, which) -> {
                    mListener.dismissAllDialogFragments();
                    mListener.saveExportFile(exportPath);
                })
                .neutralText(R.string.dialog_cancel)
                .onNeutral((dialog, which) -> mListener.dismissAllDialogFragments());
        return dialogBuilder.show();
    }
    
    public String getNotificationTitle() {
        return res().getString(R.string.export_successful_title);
    }


    public String getNotificationMessage() {
        File exportPath = new File(requireArguments().getString("exportPath"));
        return res().getString(R.string.export_successful, exportPath.getName());
    }
}
