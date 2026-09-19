// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.settings.enums

import androidx.annotation.StyleRes
import com.ichi2.anki.R

sealed interface Theme : PrefEnum {
    @get:StyleRes
    val styleResId: Int
}

/** [R.array.day_theme_values] */
enum class DayTheme(
    override val entryResId: Int,
    override val styleResId: Int,
) : Theme {
    LIGHT(R.string.theme_light_value, R.style.Theme_Light),
    PLAIN(R.string.theme_plain_value, R.style.Theme_Light_Plain),
    EINK(R.string.theme_eink_scheme_value, R.style.Theme_Light_Eink),
}
