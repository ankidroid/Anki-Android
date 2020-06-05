/*
 Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>

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

package com.ichi2.anki.dialogs;

import android.content.Context;

import com.afollestad.materialdialogs.MaterialDialog;
import com.ichi2.anki.R;

public class DiscardChangesDialog {

    public static MaterialDialog.Builder getDefault(Context context) {
        return new MaterialDialog.Builder(context)
                .content(R.string.discard_unsaved_changes)
                .positiveText(R.string.dialog_ok)
                .negativeText(R.string.dialog_cancel);
    }
}
