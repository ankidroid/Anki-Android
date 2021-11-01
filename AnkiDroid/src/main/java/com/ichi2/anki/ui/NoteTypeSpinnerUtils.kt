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
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.ichi2.anki.R
import com.ichi2.libanki.Model
import com.ichi2.themes.Themes.getColorFromAttr
import com.ichi2.utils.NamedJSONComparator
import java.util.*

/** Setup for a spinner which displays all note types */
object NoteTypeSpinnerUtils {
    @JvmStatic
    fun setupNoteTypeSpinner(context: Context, noteTypeSpinner: Spinner, col: com.ichi2.libanki.Collection): ArrayList<Long> {
        val models: List<Model> = col.models.all()
        Collections.sort(models, NamedJSONComparator.INSTANCE)
        val modelNames: MutableList<String> = ArrayList<String>(models.size)
        val allModelIds = ArrayList<Long>(models.size)
        for (m in models) {
            modelNames.add(m.getString("name"))
            allModelIds.add(m.getLong("id"))
        }

        val unselectedTextColor = getColorFromAttr(context, android.R.attr.textColorPrimary)
        val selectedTextColor = ContextCompat.getColor(context, R.color.note_editor_selected_item_text)
        val unselectedBackgroundColor = getColorFromAttr(context, android.R.attr.colorBackground)
        val selectedBackgroundColor = ContextCompat.getColor(context, R.color.note_editor_selected_item_background)
        val noteTypeAdapter: ArrayAdapter<String> = object : ArrayAdapter<String>(context, android.R.layout.simple_spinner_item, modelNames) {
            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                // Cast the drop down items (popup items) as text view
                val tv = super.getDropDownView(position, convertView, parent) as TextView

                val selectedPosition = noteTypeSpinner.selectedItemPosition
                val selected = position == selectedPosition
                // If this item is selected
                val backgroundColor = if (selected) selectedBackgroundColor else unselectedBackgroundColor
                val textColor = if (selected) selectedTextColor else unselectedTextColor
                tv.setBackgroundColor(backgroundColor)
                tv.setTextColor(textColor)

                // Return the modified view
                return tv
            }
        }
        noteTypeSpinner.adapter = noteTypeAdapter
        noteTypeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        return allModelIds
    }
}
