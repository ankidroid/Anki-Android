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
import com.ichi2.anki.ui.internationalization.sentenceCase

/** Values for the `app_theme` [ListPreference][androidx.preference.ListPreference]. */
enum class AppTheme(
    override val entryResId: Int,
    /** The label shown for this option in the preference's list. */
    val label: Context.() -> String,
) : PrefEnum {
    FOLLOW_SYSTEM(R.string.theme_follow_system_value, { TR.sentenceCase.themeFollowSystem }),
    DAY(R.string.theme_day_scheme_value, { getString(R.string.theme_day) }),
    NIGHT(R.string.theme_night_scheme_value, { getString(R.string.theme_night) }),
}
