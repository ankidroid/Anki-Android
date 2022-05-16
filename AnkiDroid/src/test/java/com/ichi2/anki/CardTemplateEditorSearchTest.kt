/****************************************************************************************
 * Copyright (c) 2022 Dorrin Sotoudeh <dorrinsotoudeh123@gmail.com>                     *
 *                                                                                      *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 *                                                                                      *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/

package com.ichi2.anki

import android.content.Intent
import android.view.MenuItem
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowActivity
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class CardTemplateEditorSearchTest() : RobolectricTest() {
    private lateinit var editor: CardTemplateEditor
    private val shadowEditor: ShadowActivity
        get() = shadowOf(editor)

    private val editTextSearchbar: EditTextSearchbar
        get() = editor.currentFragment!!.editTextSearchbar

    private val searchIcon: MenuItem
        get() = shadowEditor.optionsMenu.findItem(R.id.action_search_template)
    private val nextIcon: MenuItem
        get() = shadowEditor.optionsMenu.findItem(R.id.action_find_next)
    private val prevIcon: MenuItem
        get() = shadowEditor.optionsMenu.findItem(R.id.action_find_prev)
    private val caseSensitivityIcon: MenuItem
        get() = shadowEditor.optionsMenu.findItem(R.id.action_case_sensitive)

    @Before
    fun startupCardTemplateEditorForBasicModel() {
        // Start the CardTemplateEditor with a "Basic" model
        val col = getCurrentDatabaseModelCopy("Basic")
        val i = Intent(Intent.ACTION_VIEW)
        i.putExtra("modelId", col.getLong("id"))
        editor = startActivityNormallyOpenCollectionWithIntent(CardTemplateEditor::class.java, i)
    }

    @Test
    fun searchIconsVisibilityTest() {
        assertTrue(arrayOf(nextIcon, prevIcon, caseSensitivityIcon).map { it.isVisible }.all { false })

        searchIcon.expandActionView()
        advanceRobolectricLooperWithSleep()

        assertTrue(arrayOf(nextIcon, prevIcon, caseSensitivityIcon).map { it.isVisible }.all { true })

        searchIcon.collapseActionView()
        advanceRobolectricLooperWithSleep()

        assertTrue(arrayOf(nextIcon, prevIcon, caseSensitivityIcon).map { it.isVisible }.all { false })
    }

    /**
     * Tests if search finds result from start to if user hasn't interacted with template yet
     */
    @Test
    fun searchNoChangeTest() {
        shadowEditor.clickMenuItem(R.id.search)
        advanceRobolectricLooper()
    }
}
