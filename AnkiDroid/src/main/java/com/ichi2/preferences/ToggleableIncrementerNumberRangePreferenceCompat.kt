// SPDX-License-Identifier: GPL-3.0-or-later
package com.ichi2.preferences

import android.content.Context
import android.util.AttributeSet
import androidx.preference.PreferenceViewHolder
import com.google.android.material.materialswitch.MaterialSwitch
import com.ichi2.anki.R

/**
 * A [NumberRangePreferenceCompat] with a [MaterialSwitch] widget on the preference row.
 *
 * The switch reflects whether the value is non-zero (enabled) or zero (disabled).
 * Tapping the switch toggles between enabled/disabled without opening the dialog.
 * Tapping the title area opens the incrementer dialog only when enabled.
 *
 * No separate preference key is needed — the existing value `0` represents "disabled".
 */
class ToggleableIncrementerNumberRangePreferenceCompat :
    NumberRangePreferenceCompat,
    DialogFragmentProvider {
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

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)

        val switch = holder.findViewById(R.id.switch_widget) as MaterialSwitch
        switch.setOnCheckedChangeListener(null)
        switch.isChecked = getValue() != 0
        switch.setOnCheckedChangeListener { _, isChecked ->
            val newValue = if (isChecked) maxOf(1, min) else 0
            if (callChangeListener(newValue)) {
                setValue(newValue)
                notifyChanged()
            } else {
                switch.isChecked = !isChecked
            }
        }
    }

    override fun onClick() {
        if (getValue() == 0) return
        super.onClick()
    }

    override fun makeDialogFragment() = IncrementerNumberRangePreferenceCompat.IncrementerNumberRangeDialogFragmentCompat()
}
