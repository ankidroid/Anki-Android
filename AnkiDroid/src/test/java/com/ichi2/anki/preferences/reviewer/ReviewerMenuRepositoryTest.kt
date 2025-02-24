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
import com.github.ivanshafran.sharedpreferencesmock.SPMockBuilder
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class ReviewerMenuRepositoryTest {
    private lateinit var prefs: SharedPreferences
    private lateinit var repository: ReviewerMenuRepository

    @Before
    fun setUp() {
        prefs = SPMockBuilder().createSharedPreferences()
        repository = ReviewerMenuRepository(prefs)
    }

    // commas can't be part of an enum object name, so they are safe as separators.
    // This test serves as a safeguard against changes in the separator, which would need
    // a preference upgrade
    @Test
    fun `setPreferenceValue stores actions as comma-separated string`() {
        val actions = listOf(ViewerAction.UNDO, ViewerAction.REDO)
        repository.setDisplayTypeActions(alwaysShowActions = actions, emptyList(), emptyList())

        val expectedValue = "UNDO,REDO"
        assertEquals(expectedValue, prefs.getString(MenuDisplayType.ALWAYS.preferenceKey, null))
    }
}
