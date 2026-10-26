// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.settings.enums

import androidx.annotation.StyleRes
import com.ichi2.anki.common.android.R as CommonR

sealed interface Theme : PrefEnum {
    @get:StyleRes
    val styleResId: Int
}

/** [CommonR.array.day_theme_values] */
enum class DayTheme(
    override val entryResId: Int,
    override val styleResId: Int,
) : Theme {
    LIGHT(CommonR.string.theme_light_value, CommonR.style.Theme_Light),
    PLAIN(CommonR.string.theme_plain_value, CommonR.style.Theme_Light_Plain),
    EINK(CommonR.string.theme_eink_scheme_value, CommonR.style.Theme_Light_Eink),
}
