// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.settings.enums

import com.ichi2.anki.common.android.R as CommonR

/** [CommonR.array.night_theme_values] */
enum class NightTheme(
    override val entryResId: Int,
    override val styleResId: Int,
) : Theme {
    BLACK(CommonR.string.theme_black_value, CommonR.style.Theme_Dark_Black),
    DARK(CommonR.string.theme_dark_value, CommonR.style.Theme_Dark),
}
