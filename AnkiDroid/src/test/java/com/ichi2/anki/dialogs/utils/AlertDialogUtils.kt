/*
 *  Copyright (c) 2024 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki.dialogs.utils

import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.ichi2.utils.getInputField

var AlertDialog.input
    get() = getInputField().text.toString()
    set(value) { getInputField().setText(value) }

val AlertDialog.title
    get() = requireNotNull(this.findViewById<TextView>(androidx.appcompat.R.id.alertTitle)) {
        "androidx.appcompat.R.id.alertTitle not found"
    }.text.toString()
