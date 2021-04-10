/****************************************************************************************
 *                                                                                      *
 * Copyright (c) 2021 Shridhar Goel <shridhar.goel@gmail.com>                           *
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

package com.ichi2.anki;

import android.content.Context;
import android.widget.EditText;

import com.afollestad.materialdialogs.MaterialDialog;
import com.ichi2.utils.AndroidUiUtils;

import java.util.Objects;

import androidx.annotation.NonNull;

public class MaterialEditTextDialog extends MaterialDialog {
    protected MaterialEditTextDialog(Builder builder) {
        super(builder);
    }

    public static class Builder extends MaterialDialog.Builder {

        public EditText editText;

        public Builder(@NonNull Context context, EditText editText) {
            super(context);
            this.editText = editText;
        }

        @Override
        public MaterialDialog show() {
            customView(editText, true);
            MaterialDialog materialDialog = super.show();
            displayKeyboard(editText, materialDialog);
            return materialDialog;
        }

        /**
         * Method to display keyboard when dialog shows.
         * @param editText EditText present in the dialog.
         * @param materialDialog Dialog which contains the EditText and needs the keyboard to be displayed.
         */
        public void displayKeyboard(EditText editText, MaterialDialog materialDialog) {
            AndroidUiUtils.setFocusAndOpenKeyboard(editText, Objects.requireNonNull(materialDialog.getWindow()));
        }
    }
}
