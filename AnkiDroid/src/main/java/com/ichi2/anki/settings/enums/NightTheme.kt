// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.settings.enums

import android.content.Context
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.R

/** Values for the `night_theme` [ListPreference][androidx.preference.ListPreference]. */
enum class NightTheme(
    override val entryResId: Int,
    override val styleResId: Int,
    /** The label shown for this option in the preference's list. */
    val label: Context.() -> String,
) : Theme {
    BLACK(R.string.theme_black_value, R.style.Theme_Dark_Black, { getString(R.string.night_theme_black) }),
    DARK(R.string.theme_dark_value, R.style.Theme_Dark, { TR.preferencesThemeDark() }),
}
