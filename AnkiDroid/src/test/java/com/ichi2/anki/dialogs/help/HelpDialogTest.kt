/****************************************************************************************
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
package com.ichi2.anki.dialogs.help

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.R
import com.ichi2.anki.dialogs.help.HelpItem.Action.Rate
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class HelpDialogTest {

    @Test
    fun `Support menu handles Rate item availability correctly`() {
        // if the system doesn't have an app to handle rate intents, don't show rate menu item
        val itemsWithoutRate = HelpDialog.newSupportInstance(false).requireArgsHelpEntries()
        assertFalse(
            itemsWithoutRate.any { it.action == Rate },
            "Found help menu item for Rate but system can't handle it"
        )
        // if the system has an app to handle rate intents, show rate menu item
        val itemsWithRate = HelpDialog.newSupportInstance(true).requireArgsHelpEntries()
        assertTrue(
            itemsWithRate.any { it.action == Rate },
            "Missing help menu item for Rate when system can handle it"
        )
    }

    @Test
    fun `Help contains the expected items at start`() {
        // checking the support menu
        val expectedSupportItems = listOf(
            R.string.help_item_support_opencollective_donate,
            R.string.multimedia_editor_trans_translate,
            R.string.help_item_support_develop_ankidroid,
            R.string.help_item_support_rate_ankidroid,
            R.string.help_item_support_other_ankidroid,
            R.string.send_feedback
        )
        val actualSupportItems =
            HelpDialog.newSupportInstance(true).requireArgsHelpEntries().map { it.titleResId }
        assertEquals(
            expectedSupportItems,
            actualSupportItems,
            "Unexpected support menu item at start"
        )
        // checking the help menu
        val expectedHelpItems = listOf(
            R.string.help_title_using_ankidroid,
            R.string.help_title_get_help,
            R.string.help_title_community,
            R.string.help_title_privacy
        )
        val actualHelpItems =
            HelpDialog.newHelpInstance().requireArgsHelpEntries().map { it.titleResId }
        assertEquals(
            expectedHelpItems,
            actualHelpItems,
            "Unexpected help menu item at start"
        )
    }

    @Test
    fun `Menu items IDs are consistent`() {
        // support menu items have unique ids
        assertEquals(
            supportMenuItems.size,
            supportMenuItems.map { it.id }.toSet().size,
            "Support menu has items with the same id"
        )
        // main help menu items have unique ids
        assertEquals(
            mainHelpMenuItems.size,
            mainHelpMenuItems.map { it.id }.toSet().size,
            "Main help menu has items with the same id"
        )
        // help menu child items have a non-null valid parent id
        val allFoundParentIds = childHelpMenuItems.map { it.parentId }
        assertFalse(
            allFoundParentIds
                .any { it == null || !mainHelpMenuItems.map { entry -> entry.id }.contains(it) },
            "Help item has an invalid parentId"
        )
    }
}
