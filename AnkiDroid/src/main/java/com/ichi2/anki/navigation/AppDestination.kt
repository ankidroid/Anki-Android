/*
 *  Copyright (c) 2026 Tim Rae <perceptualchaos2@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.IdRes
import androidx.annotation.StringRes
import com.ichi2.anki.R

/**
 * An entry in the app-level navigation surface (drawer / rail).
 *
 * Carries only visual metadata and the legacy menu id used by
 * [com.ichi2.anki.NavigationDrawerActivity]. For the side-effect of selecting
 * an entry, see [handleAppDestination].
 */
enum class AppDestination(
    @IdRes val menuItemId: Int,
    val group: Group,
    @StringRes val titleRes: Int,
    @DrawableRes val iconRes: Int,
) {
    Decks(R.id.nav_decks, Group.Primary, R.string.decks, R.drawable.ic_list_black),
    Browser(R.id.nav_browser, Group.Primary, R.string.card_browser, R.drawable.ic_flashcard_black),
    Stats(R.id.nav_stats, Group.Primary, R.string.statistics, R.drawable.ic_bar_chart_black),
    Settings(R.id.nav_settings, Group.Utility, R.string.settings, R.drawable.ic_settings_black),
    Help(R.id.nav_help, Group.Utility, R.string.help, R.drawable.ic_help_black),
    Support(R.id.support_ankidroid, Group.Utility, R.string.help_title_support_ankidroid, R.drawable.ic_support_ankidroid),
    ;

    enum class Group { Primary, Utility }

    companion object {
        fun fromMenuId(
            @IdRes id: Int,
        ): AppDestination? = entries.find { it.menuItemId == id }
    }
}
