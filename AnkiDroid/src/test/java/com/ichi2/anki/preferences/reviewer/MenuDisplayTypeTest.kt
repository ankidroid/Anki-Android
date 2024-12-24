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
import androidx.core.content.edit
import com.github.ivanshafran.sharedpreferencesmock.SPMockBuilder
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.empty
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import kotlin.test.assertContentEquals

class MenuDisplayTypeTest {
    private lateinit var prefs: SharedPreferences

    @Before
    fun setUp() {
        prefs = SPMockBuilder().createSharedPreferences()
    }

    // Safeguard against changing the preference keys.
    // A preference upgrade is necessary if they are changed.
    @Test
    fun `preferenceKeys aren't changed`() {
        assertEquals(MenuDisplayType.ALWAYS.preferenceKey, "ReviewerMenuDisplayType_ALWAYS")
        assertEquals(MenuDisplayType.MENU_ONLY.preferenceKey, "ReviewerMenuDisplayType_MENU_ONLY")
        assertEquals(MenuDisplayType.DISABLED.preferenceKey, "ReviewerMenuDisplayType_DISABLED")
    }

    // commas can't be part of an enum object name, so they are safe as separators.
    // This test serves as a safeguard against changes in the separator, which would need
    // a preference upgrade
    @Test
    fun `setPreferenceValue stores actions as comma-separated string`() {
        val actions = listOf(ViewerAction.UNDO, ViewerAction.REDO)
        MenuDisplayType.ALWAYS.setPreferenceValue(prefs, actions)

        val expectedValue = "UNDO,REDO"
        assertEquals(expectedValue, prefs.getString(MenuDisplayType.ALWAYS.preferenceKey, null))
    }

    @Test
    fun `getConfiguredActions returns correct list of ViewerActions`() {
        prefs.edit {
            putString(MenuDisplayType.ALWAYS.preferenceKey, "UNDO,MARK")
        }
        val result = MenuDisplayType.ALWAYS.getConfiguredActions(prefs)
        assertEquals(listOf(ViewerAction.UNDO, ViewerAction.MARK), result)
    }

    @Test
    fun `getConfiguredActions returns empty list if preference is not set`() {
        val result = MenuDisplayType.ALWAYS.getConfiguredActions(prefs)
        assertEquals(emptyList<ViewerAction>(), result)
    }

    @Test
    fun `getAllNotConfiguredActions returns only actions with default display types if preference is not set`() {
        val result = MenuDisplayType.getAllNotConfiguredActions(prefs)
        val expected = ViewerAction.entries.filter { it.defaultDisplayType != null }
        assertEquals(expected, result)
    }

    @Test
    fun `getMenuItems returns correctly categorized items`() {
        // assuming that UNDO is the only action with ALWAYS as default, put it in another list
        MenuDisplayType.MENU_ONLY.setPreferenceValue(prefs, listOf(ViewerAction.UNDO))
        val result = MenuDisplayType.getMenuItems(prefs, MenuDisplayType.ALWAYS).getValue(MenuDisplayType.ALWAYS)
        assertThat(result, empty())
    }

    @Test
    fun `getMenuItems returns not configured items that have a default display type`() {
        // assuming that USER_ACTION_1 and USER_ACTION_2 don't have MENU_ONLY as default
        val userActions1and2 = listOf(ViewerAction.USER_ACTION_1, ViewerAction.USER_ACTION_2)
        MenuDisplayType.MENU_ONLY.setPreferenceValue(prefs, userActions1and2)
        val result = MenuDisplayType.getMenuItems(prefs, MenuDisplayType.MENU_ONLY).getValue(MenuDisplayType.MENU_ONLY)
        val expected = userActions1and2 + ViewerAction.entries.filter { it.defaultDisplayType == MenuDisplayType.MENU_ONLY }
        assertContentEquals(expected, result)
    }
}
