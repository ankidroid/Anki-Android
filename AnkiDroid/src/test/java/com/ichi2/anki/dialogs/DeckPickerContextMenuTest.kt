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

import android.os.Bundle
import androidx.core.content.edit
import androidx.fragment.app.testing.FragmentScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.*
import com.ichi2.testutils.BackupManagerTestUtilities.setupSpaceForBackup
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertFailsWith

@RunWith(AndroidJUnit4::class)
class DeckPickerContextMenuTest : RobolectricTest() {
    @Before
    fun before() {
        getPreferences().edit { putBoolean(IntroductionActivity.INTRODUCTION_SLIDES_SHOWN, true) }
        setupSpaceForBackup(this.targetContext)
    }

    @Test
    fun ensure_cannot_be_instantiated_without_expected_arguments() {
        // fails on deck id missing from arguments
        assertFailsWith<AssertionError> { startContextMenuWithMissingArgument("id") }
        // fails on deck name missing from arguments
        assertFailsWith<AssertionError> { startContextMenuWithMissingArgument("name") }
        // fails on deck dynamic status missing from arguments
        assertFailsWith<AssertionError> { startContextMenuWithMissingArgument("dynamic") }
        // fails on deck having buried status missing from arguments
        assertFailsWith<AssertionError> { startContextMenuWithMissingArgument("hasBuried") }
    }

    /**
     * Create a [Bundle] with static data to be passed as arguments for [DeckPickerContextMenu].
     * One option is excluded to simulate the argument missing.
     *
     * @param excluded a value from [id, name, dynamic, hasBuried] to be removed from the returned
     * bundle. See source code for [DeckPickerContextMenu] for options meaning.
     */
    private fun startContextMenuWithMissingArgument(excluded: String) {
        val arguments = Bundle().apply {
            if (excluded != "id") { DeckPickerContextMenu.ARG_DECK_ID to 1000L }
            if (excluded != "name") { DeckPickerContextMenu.ARG_DECK_NAME to "Deck" }
            if (excluded != "dynamic") { DeckPickerContextMenu.ARG_DECK_IS_DYN to false }
            if (excluded != "hasBuried") { DeckPickerContextMenu.ARG_DECK_HAS_BURIED_IN_DECK to false }
        }
        FragmentScenario.launch(DeckPickerContextMenu::class.java, arguments, R.style.Theme_Light)
    }
}
