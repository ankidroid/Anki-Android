// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2025 Brayan Oliveira <69634269+brayandso@users.noreply.github.com>

package com.ichi2.anki.settings.enums

import android.content.Context
import androidx.annotation.StyleRes
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.R

sealed interface Theme : PrefEnum {
    @get:StyleRes
    val styleResId: Int
}

/** Values for the `day_theme` [ListPreference][androidx.preference.ListPreference]. */
enum class DayTheme(
    override val entryResId: Int,
    override val styleResId: Int,
    /** The label shown for this option in the preference's list. */
    val label: Context.() -> String,
) : Theme {
    LIGHT(R.string.theme_light_value, R.style.Theme_Light, { TR.preferencesThemeLight() }),
    PLAIN(R.string.theme_plain_value, R.style.Theme_Light_Plain, { getString(R.string.day_theme_plain) }),
    EINK(R.string.theme_eink_scheme_value, R.style.Theme_Light_Eink, { getString(R.string.day_theme_eink) }),
}
