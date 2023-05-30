/*
 *  Copyright (c) 2023 Ashish Yadav <mailtoashish693@gmail.com>
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

package com.ichi2.anki

import android.content.Context
import androidx.preference.ListPreference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.afollestad.materialdialogs.utils.MDUtil.getStringArray
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CustomButtonsSettingsFragmentTest {
    private lateinit var preferenceManager: PreferenceManager

    @Before
    fun setup() {
        preferenceManager = PreferenceManager(ApplicationProvider.getApplicationContext())
    }

    @Test
    fun customButtonPreferences_checkKeyValuesAndAttributes() {
        val preferenceScreen = preferenceManager.inflateFromResource(
            ApplicationProvider.getApplicationContext(),
            R.xml.preferences_custom_buttons,
            null
        )

        val deckConfGeneralCategory =
            preferenceScreen.findPreference<PreferenceCategory>(getString(R.string.deck_conf_general))
        val menuDismissNoteCategory =
            preferenceScreen.findPreference<PreferenceCategory>(getString(R.string.menu_dismiss_note))
        val prefCatWhiteboardCategory =
            preferenceScreen.findPreference<PreferenceCategory>(getString(R.string.pref_cat_whiteboard))

        Assert.assertEquals(getString(R.string.deck_conf_general), deckConfGeneralCategory?.title)
        Assert.assertEquals(getString(R.string.menu_dismiss_note), menuDismissNoteCategory?.title)
        Assert.assertEquals(
            getString(R.string.pref_cat_whiteboard),
            prefCatWhiteboardCategory?.title
        )

        Assert.assertEquals(12, deckConfGeneralCategory?.preferenceCount)
        Assert.assertEquals(3, menuDismissNoteCategory?.preferenceCount)
        Assert.assertEquals(6, prefCatWhiteboardCategory?.preferenceCount)

        // PreferenceCategory: deckConfGeneralCategory
        val undoPreference =
            deckConfGeneralCategory?.findPreference<ListPreference>(getString(R.string.custom_button_undo_key))
        Assert.assertEquals(getString(R.string.undo), undoPreference?.title)
        Assert.assertArrayEquals(
            getStringArray(R.array.custom_button_labels),
            undoPreference?.entries
        )

        Assert.assertArrayEquals(
            getStringArray(R.array.custom_button_values),
            undoPreference?.entryValues
        )
        Assert.assertEquals("2", undoPreference?.value)

        // PreferenceCategory: menuDismissNoteCategory
        val buryPreference =
            menuDismissNoteCategory?.findPreference<ListPreference>(getString(R.string.custom_button_bury_key))
        Assert.assertEquals(getString(R.string.menu_bury), buryPreference?.title)
        Assert.assertArrayEquals(
            getStringArray(R.array.custom_button_labels),
            buryPreference?.entries
        )
        Assert.assertArrayEquals(
            getStringArray(R.array.custom_button_values),
            buryPreference?.entryValues
        )
        buryPreference?.value = "3"
        Assert.assertEquals("3", buryPreference?.value)

        // PreferenceCategory: prefCatWhiteboardCategory
        val enableWhiteboardPreference =
            prefCatWhiteboardCategory?.findPreference<ListPreference>(getString(R.string.custom_button_enable_whiteboard_key))
        Assert.assertEquals(getString(R.string.enable_whiteboard), enableWhiteboardPreference?.title)
        Assert.assertArrayEquals(
            getStringArray(R.array.custom_button_labels),
            enableWhiteboardPreference?.entries
        )

        Assert.assertArrayEquals(
            getStringArray(R.array.custom_button_values),
            enableWhiteboardPreference?.entryValues
        )
        enableWhiteboardPreference?.value = "0"
        Assert.assertEquals("0", enableWhiteboardPreference?.value)
    }

    private fun getStringArray(resId: Int): Array<String> {
        return ApplicationProvider.getApplicationContext<Context>().getStringArray(resId)
    }

    private fun getString(resId: Int): String {
        return ApplicationProvider.getApplicationContext<Context>().getString(resId)
    }
}
