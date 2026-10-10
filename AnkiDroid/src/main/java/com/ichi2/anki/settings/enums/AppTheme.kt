// SPDX-License-Identifier: GPL-3.0-or-later

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
