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

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;

import com.afollestad.materialdialogs.MaterialDialog;
import com.ichi2.anki.R;

    /**
     * This is a reusable convenience class which makes it easy to show a confirmation dialog as a DialogFragment.
     * Create a new instance, call setArgs(...), setConfirm(), and setCancel() then show it via the fragment manager as usual.
     */
    public class ConfirmationDialog extends DialogFragment {
        private @NonNull Runnable mConfirm = () -> { }; // Do nothing by default
        private @NonNull Runnable mCancel = () -> { };  // Do nothing by default

        public void setArgs(String message) {
            setArgs("" , message);
        }

        public void setArgs(String title, String message) {
            Bundle args = new Bundle();
            args.putString("message", message);
            args.putString("title", title);
            setArguments(args);
        }

        public void setConfirm(@NonNull Runnable confirm) {
            mConfirm = confirm;
        }

        public void setCancel(@NonNull Runnable cancel) {
            mCancel = cancel;
        }

        @NonNull
        @Override
        public MaterialDialog onCreateDialog(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            Resources res = getActivity().getResources();
            String title = getArguments().getString("title");
            return new MaterialDialog.Builder(getActivity())
                .title("".equals(title) ? res.getString(R.string.app_name) : title)
                    .content(getArguments().getString("message"))
                    .positiveText(R.string.dialog_ok)
                    .negativeText(R.string.dialog_cancel)
                    .onPositive((dialog, which) -> mConfirm.run())
                    .onNegative((dialog, which) -> mCancel.run())
                    .show();
        }
    }
