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
import org.junit.jupiter.api.Assertions.*
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowActivity

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
    private val caseSensitiveIcon: MenuItem
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
        assertTrue(arrayOf(nextIcon, prevIcon, caseSensitiveIcon).map { it.isVisible }.all { !it })

        searchIcon.expandActionView()
        advanceRobolectricLooperWithSleep()

        assertTrue(arrayOf(nextIcon, prevIcon, caseSensitiveIcon).map { it.isVisible }.all { it })

        searchIcon.collapseActionView()
        advanceRobolectricLooperWithSleep()

        assertTrue(arrayOf(nextIcon, prevIcon, caseSensitiveIcon).map { it.isVisible }.all { !it })
    }

    @Test
    fun normalSearchTest() {
        val modelText = "{{Front}}"
        editTextSearchbar.targetEditText.setText(modelText)
        advanceRobolectricLooper()

        // open search
        searchIcon.expandActionView()
        advanceRobolectricLooperWithSleep()

        // normal result finding (should find.      case sensitive ON)
        assertTrue(caseSensitiveIcon.isChecked)
        assertTrue(editTextSearchbar.caseSensitive)
        editTextSearchbar.querySearchbar.setQuery("Fr", true)
        assertEquals(2, editTextSearchbar.targetEditText.selectionStart)
        assertEquals(4, editTextSearchbar.targetEditText.selectionEnd)

        // normal result finding (shouldn't find.   case sensitive ON)
        editTextSearchbar.querySearchbar.setQuery("f", true)
        assertEquals(modelText.length, editTextSearchbar.targetEditText.selectionStart)
        assertEquals(modelText.length, editTextSearchbar.targetEditText.selectionEnd)

        // normal result finding (should find.      case sensitive OFF)
        caseSensitiveIcon.isChecked = false
        assertFalse(caseSensitiveIcon.isChecked)
        assertFalse(editTextSearchbar.caseSensitive)
        editTextSearchbar.querySearchbar.setQuery("fR", true)
        assertEquals(2, editTextSearchbar.targetEditText.selectionStart)
        assertEquals(4, editTextSearchbar.targetEditText.selectionEnd)
    }

    @Test
    fun nextButtonTest() {
        editTextSearchbar.targetEditText.setText("ff ff ff")

        // open search
        searchIcon.expandActionView()

        editTextSearchbar.querySearchbar.setQuery("ff", true)
        assertEquals(0, editTextSearchbar.targetEditText.selectionStart)
        assertEquals(2, editTextSearchbar.targetEditText.selectionEnd)

        shadowEditor.clickMenuItem(R.id.action_find_next)

        assertEquals(3, editTextSearchbar.targetEditText.selectionStart)
        assertEquals(5, editTextSearchbar.targetEditText.selectionEnd)

        shadowEditor.clickMenuItem(R.id.action_find_next)

        assertEquals(6, editTextSearchbar.targetEditText.selectionStart)
        assertEquals(8, editTextSearchbar.targetEditText.selectionEnd)

        shadowEditor.clickMenuItem(R.id.action_find_next)

        assertEquals(0, editTextSearchbar.targetEditText.selectionStart)
        assertEquals(2, editTextSearchbar.targetEditText.selectionEnd)

        // test start clicking "next" from middle
        editTextSearchbar.targetEditText.setText("ff ffa ff")

        editTextSearchbar.querySearchbar.setQuery("ffa", true)
        assertEquals(3, editTextSearchbar.targetEditText.selectionStart)
        assertEquals(6, editTextSearchbar.targetEditText.selectionEnd)

        editTextSearchbar.querySearchbar.setQuery("ff", false)
        shadowEditor.clickMenuItem(R.id.action_find_next)

        assertEquals(7, editTextSearchbar.targetEditText.selectionStart)
        assertEquals(9, editTextSearchbar.targetEditText.selectionEnd)

        shadowEditor.clickMenuItem(R.id.action_find_next)

        assertEquals(0, editTextSearchbar.targetEditText.selectionStart)
        assertEquals(2, editTextSearchbar.targetEditText.selectionEnd)

        // test start clicking "next" from end
        editTextSearchbar.targetEditText.setText("ff ff ffa")

        editTextSearchbar.querySearchbar.setQuery("ffa", true)
        assertEquals(6, editTextSearchbar.targetEditText.selectionStart)
        assertEquals(9, editTextSearchbar.targetEditText.selectionEnd)

        editTextSearchbar.querySearchbar.setQuery("ff", false)
        shadowEditor.clickMenuItem(R.id.action_find_next)

        assertEquals(0, editTextSearchbar.targetEditText.selectionStart)
        assertEquals(2, editTextSearchbar.targetEditText.selectionEnd)

        // test no next result
        editTextSearchbar.targetEditText.setText("ff ffa ff")

        editTextSearchbar.querySearchbar.setQuery("ffa", true)
        assertEquals(3, editTextSearchbar.targetEditText.selectionStart)
        assertEquals(6, editTextSearchbar.targetEditText.selectionEnd)

        shadowEditor.clickMenuItem(R.id.action_find_next)
        assertEquals(3, editTextSearchbar.targetEditText.selectionStart)
        assertEquals(6, editTextSearchbar.targetEditText.selectionEnd)
    }
}
