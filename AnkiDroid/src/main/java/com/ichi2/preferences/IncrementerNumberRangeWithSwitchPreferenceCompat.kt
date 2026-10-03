/*
 * Copyright (c) 2024 The AnkiDroid Open Source Project
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.preferences

import android.content.Context
import android.util.AttributeSet
import androidx.preference.PreferenceViewHolder
import com.google.android.material.materialswitch.MaterialSwitch
import com.ichi2.anki.R

class IncrementerNumberRangeWithSwitchPreferenceCompat : IncrementerNumberRangePreferenceCompat {
    @Suppress("unused")
    constructor(
        context: Context,
        attrs: AttributeSet?,
        defStyleAttr: Int,
        defStyleRes: Int,
    ) : super(context, attrs, defStyleAttr, defStyleRes) {
        widgetLayoutResource = R.layout.preference_widget_switch_with_separator
    }

    @Suppress("unused")
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        widgetLayoutResource = R.layout.preference_widget_switch_with_separator
    }

    @Suppress("unused")
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        widgetLayoutResource = R.layout.preference_widget_switch_with_separator
    }

    @Suppress("unused")
    constructor(context: Context) : super(context) {
        widgetLayoutResource = R.layout.preference_widget_switch_with_separator
    }

    private val canBeSwitchedOn get() = getValue() > 0

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)

        with(holder.findViewById(R.id.switch_widget) as MaterialSwitch) {
            isFocusable = canBeSwitchedOn
            isClickable = canBeSwitchedOn
            isChecked = canBeSwitchedOn
            setOnCheckedChangeListener { _, checked ->
                if (!checked) {
                    if (callChangeListener(0)) {
                        setValue(0)
                        notifyChanged()
                    }
                }
            }
        }
    }
}
