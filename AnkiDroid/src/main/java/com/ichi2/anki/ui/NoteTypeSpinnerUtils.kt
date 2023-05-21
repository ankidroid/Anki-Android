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

package com.ichi2.anki.ui

import android.content.Context
import android.widget.ArrayAdapter
import android.widget.Spinner
import com.ichi2.anki.R
import com.ichi2.libanki.Collection
import com.ichi2.utils.NamedJSONComparator

fun setupNoteTypeSpinner(col: Collection, context: Context, noteTypeSpinner: Spinner): List<Long> {
    val sortedModels = col.models.all().sortedWith(NamedJSONComparator.INSTANCE)
    val modelNames = sortedModels.map { it.getString("name") }

    noteTypeSpinner.adapter = ArrayAdapter(
        context,
        android.R.layout.simple_spinner_dropdown_item,
        modelNames
    ).apply {
        // The resource passed to the constructor is normally used for both the spinner view
        // and the dropdown list. This keeps the former and overrides the latter.
        setDropDownViewResource(R.layout.spinner_dropdown_item_with_radio)
    }

    return sortedModels.map { it.getLong("id") }
}
