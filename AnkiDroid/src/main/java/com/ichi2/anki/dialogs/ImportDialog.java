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
import com.ichi2.anki.R;

import java.net.URLDecoder;

import androidx.annotation.NonNull;
import timber.log.Timber;

public class ImportDialog extends AsyncDialogFragment {

    public static final int DIALOG_IMPORT_ADD_CONFIRM = 2;
    public static final int DIALOG_IMPORT_REPLACE_CONFIRM = 3;

    public interface ImportDialogListener {
        void importAdd(String importPath);
        void importReplace(String importPath);
        void dismissAllDialogFragments();
    }


    /**
     * A set of dialogs which deal with importing a file
     * 
     * @param dialogType An integer which specifies which of the sub-dialogs to show
     * @param dialogMessage An optional string which can be used to show a custom message
     * or specify import path
     */
    public static ImportDialog newInstance(int dialogType, String dialogMessage) {
        ImportDialog f = new ImportDialog();
        Bundle args = new Bundle();
        args.putInt("dialogType", dialogType);
        args.putString("dialogMessage", dialogMessage);
        f.setArguments(args);
        return f;
    }


    @NonNull
    @Override
    public MaterialDialog onCreateDialog(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        int type = getArguments().getInt("dialogType");
        Resources res = getResources();
        MaterialDialog.Builder builder = new MaterialDialog.Builder(getActivity());
        builder.cancelable(true);

        switch (type) {
            case DIALOG_IMPORT_ADD_CONFIRM: {
                String displayFileName = convertToDisplayName(getArguments().getString("dialogMessage"));
                return builder.title(res.getString(R.string.import_title))
                        .content(res.getString(R.string.import_message_add_confirm, filenameFromPath(displayFileName)))
                        .positiveText(R.string.import_message_add)
                        .negativeText(R.string.dialog_cancel)
                        .onPositive((dialog, which) -> {
                            ((ImportDialogListener) getActivity()).importAdd(getArguments().getString("dialogMessage"));
                            dismissAllDialogFragments();
                        })
                        .show();
            }
            case DIALOG_IMPORT_REPLACE_CONFIRM: {
                String displayFileName = convertToDisplayName(getArguments().getString("dialogMessage"));
                return builder.title(res.getString(R.string.import_title))
                        .content(res.getString(R.string.import_message_replace_confirm, displayFileName))
                        .positiveText(R.string.dialog_positive_replace)
                        .negativeText(R.string.dialog_cancel)
                        .onPositive((dialog, which) -> {
                            ((ImportDialogListener) getActivity()).importReplace(getArguments().getString("dialogMessage"));
                            dismissAllDialogFragments();
                        })
                        .show();
            }
            default:
                return null;
        }
    }


    private String convertToDisplayName(String name) {
        //ImportUtils URLEncodes names, which isn't great for display.
        //NICE_TO_HAVE: Pass in the DisplayFileName closer to the source of the bad data, rather than fixing it here.
        try {
            return URLDecoder.decode(name, "UTF-8");
        } catch (Exception e) {
            Timber.w(e,"Failed to convert filename to displayable string");
            return name;
        }
    }


    @Override
    public String getNotificationMessage() {
        return res().getString(R.string.import_interrupted);
    }

    @Override
    public String getNotificationTitle() {
        return res().getString(R.string.import_title);
    }
    
    public void dismissAllDialogFragments() {
        ((ImportDialogListener) getActivity()).dismissAllDialogFragments();        
    }

    private static String filenameFromPath (String path) {
        return path.split("/")[path.split("/").length - 1];
    }
}
