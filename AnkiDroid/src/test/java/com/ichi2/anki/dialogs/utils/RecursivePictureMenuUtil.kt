/*
 *  Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki.dialogs.utils;

import com.afollestad.materialdialogs.MaterialDialog;
import com.ichi2.anki.dialogs.RecursivePictureMenu;

import java.util.Objects;

import androidx.annotation.CheckResult;
import androidx.recyclerview.widget.RecyclerView;

public class RecursivePictureMenuUtil {

    @CheckResult
    public static RecyclerView getRecyclerViewFor(RecursivePictureMenu menu) {
        MaterialDialog dialog = (MaterialDialog) Objects.requireNonNull(menu.getDialog());
        return dialog.getRecyclerView();
    }
}
