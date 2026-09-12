/*
 * Copyright (c) 2025 Brayan Oliveira <69634269+brayandso@users.noreply.github.com>
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
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
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
