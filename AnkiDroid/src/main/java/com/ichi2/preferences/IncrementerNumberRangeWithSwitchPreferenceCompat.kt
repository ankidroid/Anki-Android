/*
 * Copyright (c) 2026 Ibrahim Iqbal <ibrahim-iqbal@users.noreply.github.com>
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

/** Adds a trailing enable/disable switch to [IncrementerNumberRangePreferenceCompat]. A value of 0 is treated as disabled. */
class IncrementerNumberRangeWithSwitchPreferenceCompat :
    IncrementerNumberRangePreferenceCompat {
    @Suppress("unused")
    constructor(
        context: Context,
        attrs: AttributeSet?,
        defStyleAttr: Int,
        defStyleRes: Int,
    ) : super(context, attrs, defStyleAttr, defStyleRes)

    @Suppress("unused")
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    @Suppress("unused")
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    @Suppress("unused")
    constructor(context: Context) : super(context)

    init {
        widgetLayoutResource = R.layout.preference_widget_switch_with_separator
    }

    private val isFeatureEnabled: Boolean get() = getValue() > 0

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)

        val switch = holder.findViewById(R.id.switch_widget) as MaterialSwitch
        switch.setOnCheckedChangeListener(null)
        switch.isFocusable = isFeatureEnabled
        switch.isClickable = isFeatureEnabled
        switch.isChecked = isFeatureEnabled
        switch.setOnCheckedChangeListener { _, checked ->
            if (!checked && isFeatureEnabled) {
                if (callChangeListener(0)) {
                    setValue(0)
                    notifyChanged()
                }
            }
        }
    }
}
