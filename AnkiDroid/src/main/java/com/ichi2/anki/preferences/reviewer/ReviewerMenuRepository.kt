/*
 * Copyright (c) 2025 Brayan Oliveira <brayandso.dev@gmail.com>
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
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.preferences.reviewer

import android.content.SharedPreferences
import androidx.core.content.edit

class ReviewerMenuRepository(
    private val preferences: SharedPreferences,
) {
    fun setDisplayTypeActions(
        alwaysShowActions: Iterable<ViewerAction>,
        menuOnlyActions: Iterable<ViewerAction>,
        disabledActions: Iterable<ViewerAction>,
    ) {
        fun Iterable<ViewerAction>.toPreferenceString() = this.joinToString(SEPARATOR) { it.name }
        preferences.edit {
            putString(MenuDisplayType.ALWAYS.preferenceKey, alwaysShowActions.toPreferenceString())
            putString(MenuDisplayType.MENU_ONLY.preferenceKey, menuOnlyActions.toPreferenceString())
            putString(MenuDisplayType.DISABLED.preferenceKey, disabledActions.toPreferenceString())
        }
    }

    companion object {
        private const val SEPARATOR = ","
    }
}
