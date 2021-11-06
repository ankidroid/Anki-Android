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

import com.afollestad.materialdialogs.MaterialDialog;
import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.R;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class SimpleMessageDialog extends AsyncDialogFragment {

    public interface SimpleMessageDialogListener {
        void dismissSimpleMessageDialog(boolean reload);
    }


    public static SimpleMessageDialog newInstance(String message, boolean reload) {
        return newInstance("" , message, reload);
    }


    public static SimpleMessageDialog newInstance(String title, @Nullable String message, boolean reload) {
        SimpleMessageDialog f = new SimpleMessageDialog();
        Bundle args = new Bundle();
        args.putString("title", title);
        args.putString("message", message);
        args.putBoolean("reload", reload);
        f.setArguments(args);
        return f;
    }


    @NonNull
    @Override
    public MaterialDialog onCreateDialog(Bundle savedInstanceState) {
        // FIXME this should be super.onCreateDialog(Bundle), no?
        super.onCreate(savedInstanceState);
        return new MaterialDialog.Builder(getActivity())
                .title(getNotificationTitle())
                .content(getNotificationMessage())
                .positiveText(res().getString(R.string.dialog_ok))
                .onPositive((dialog, which) -> ((SimpleMessageDialogListener) getActivity())
                        .dismissSimpleMessageDialog(getArguments().getBoolean(
                                "reload")))
                .show();
    }


    public String getNotificationTitle() {
        String title = getArguments().getString("title");
        if (!"".equals(title)) {
            return title;
        } else {
            return AnkiDroidApp.getAppResources().getString(R.string.app_name);
        }
    }


    public String getNotificationMessage() {
        return getArguments().getString("message");
    }
}
