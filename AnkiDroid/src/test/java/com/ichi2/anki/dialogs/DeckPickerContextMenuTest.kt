/*
 *  Copyright (c) 2022 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki.dialogs

import androidx.fragment.app.testing.FragmentScenario
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.afollestad.materialdialogs.MaterialDialog
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.libanki.Consts
import com.ichi2.testutils.assertThrows
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeckPickerContextMenuTest : RobolectricTest() {
    @Test
    fun delete_deck_is_last_in_menu_issue_10283() {
        // "Delete deck" was previously close to "Custom study" which caused misclicks.
        // This is less likely at the bottom of the list
        testDialog(Consts.DEFAULT_DECK_ID) { dialog ->
            val lastItem = dialog.items!!.last()
            assertThat(
                "'Delete deck' should be last item in the menu",
                lastItem,
                equalTo(getResourceString(R.string.contextmenu_deckpicker_delete_deck))
            )
        }
    }

    @Test
    fun ensure_cannot_be_instantiated_without_arguments() {
        assertThrows<IllegalStateException> { DeckPickerContextMenu(col).deckId }
    }

    /**
     * Allows testing the [MaterialDialog] returned from the [DeckPickerContextMenu]
     *
     * @param deckId The deck ID to test
     * @param execAssertions the assertions to perform on the [MaterialDialog] under test
     */
    private fun testDialog(@Suppress("SameParameterValue") deckId: Long, execAssertions: (MaterialDialog) -> Unit) {
        val args = DeckPickerContextMenu(col)
            .withArguments(deckId)
            .arguments

        val factory = DeckPickerContextMenu.Factory { col }
        FragmentScenario.launch(DeckPickerContextMenu::class.java, args, R.style.Theme_AppCompat, factory)
            .use { scenario ->
                scenario.moveToState(Lifecycle.State.STARTED)
                scenario.onFragment { f: DeckPickerContextMenu ->
                    execAssertions(f.dialog as MaterialDialog)
                }
            }
    }
}
