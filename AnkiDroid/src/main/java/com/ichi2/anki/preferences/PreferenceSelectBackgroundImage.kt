/*
 *  Copyright (c) 2024 Mohd Raghib <raghib.khan76@gmail.com>
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
package com.ichi2.anki.preferences

import android.content.Context
import android.util.AttributeSet
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.ichi2.anki.R

interface PreferenceSelectBackgroundImageListener {
    fun onImageSelectClicked()
    fun onImageRemoveClicked()
}

class PreferenceSelectBackgroundImage @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet?,
    defStyleAttr: Int = 0
) : Preference(context, attrs, defStyleAttr) {

    private var preferenceSelectBackgroundImageListener: PreferenceSelectBackgroundImageListener? = null

    init {
        widgetLayoutResource = R.layout.preference_deck_picker_background
    }

    fun preferenceSelectBackgroundImageListener(listener: PreferenceSelectBackgroundImageListener) {
        preferenceSelectBackgroundImageListener = listener
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        holder.findViewById(R.id.deck_picker_background_image_selector).setOnClickListener {
            preferenceSelectBackgroundImageListener?.onImageSelectClicked()
        }

        holder.findViewById(R.id.deck_picker_background_image_remover).setOnClickListener {
            preferenceSelectBackgroundImageListener?.onImageRemoveClicked()
        }
    }
}
