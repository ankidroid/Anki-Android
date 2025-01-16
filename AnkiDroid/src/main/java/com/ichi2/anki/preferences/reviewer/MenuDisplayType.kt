/*
 *  Copyright (c) 2024 Brayan Oliveira <brayandso.dev@gmail.com>
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
package com.ichi2.anki.preferences.reviewer

import android.content.SharedPreferences
import android.view.MenuItem
import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import androidx.core.content.edit
import com.ichi2.anki.R

enum class MenuDisplayType(
    @StringRes val title: Int,
) {
    /** Shows the action as [MenuItem.SHOW_AS_ACTION_ALWAYS] */
    ALWAYS(R.string.custom_buttons_setting_always_show),

    /** Shows the action as [MenuItem.SHOW_AS_ACTION_NEVER] */
    MENU_ONLY(R.string.custom_buttons_setting_menu_only),

    /** Action isn't added to the menu */
    DISABLED(R.string.disabled),
    ;

    @VisibleForTesting
    val preferenceKey get() = "ReviewerMenuDisplayType_$name"

    /**
     * @return the configured actions for this menu display type.
     */
    @VisibleForTesting
    fun getConfiguredActions(preferences: SharedPreferences): List<ViewerAction> {
        val prefValue =
            preferences.getString(preferenceKey, null)
                ?: return emptyList()

        val actionsNames = prefValue.split(SEPARATOR)
        return actionsNames.mapNotNull { name ->
            ViewerAction.entries.firstOrNull { it.name == name }
        }
    }

    fun setPreferenceValue(
        preferences: SharedPreferences,
        actions: List<ViewerAction>,
    ) {
        val prefValue = actions.joinToString(SEPARATOR) { it.name }
        preferences.edit { putString(preferenceKey, prefValue) }
    }

    companion object {
        private const val SEPARATOR = ","

        /**
         * @return A list of all actions that aren't configured.
         * Not configured items that don't have a default display type aren't included.
         *
         * May happen if the user hasn't configured any of the menu actions,
         * or if a new action was implemented but not configured yet.
         */
        @VisibleForTesting
        fun getAllNotConfiguredActions(prefs: SharedPreferences): List<ViewerAction> {
            val mappedActions = MenuDisplayType.entries.flatMap { it.getConfiguredActions(prefs) }
            return ViewerAction.entries.filter {
                it.defaultDisplayType != null && it !in mappedActions
            }
        }

        fun getMenuItems(
            prefs: SharedPreferences,
            vararg selected: MenuDisplayType = MenuDisplayType.entries.toTypedArray(),
        ): Map<MenuDisplayType, List<ViewerAction>> {
            val notConfiguredActions = getAllNotConfiguredActions(prefs)

            return selected.toSet().associateWith { type ->
                type.getConfiguredActions(prefs) +
                    notConfiguredActions.filter { it.defaultDisplayType == type }
            }
        }
    }
}
