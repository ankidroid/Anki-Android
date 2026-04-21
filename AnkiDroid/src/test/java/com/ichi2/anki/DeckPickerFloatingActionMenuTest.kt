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

package com.ichi2.anki

import android.view.View
import androidx.core.content.edit
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.preferences.sharedPrefs
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Test for [DeckPickerFloatingActionMenu]
 */
@RunWith(AndroidJUnit4::class)
@Config
class DeckPickerFloatingActionMenuTest : RobolectricTest() {
    override fun setUp() {
        super.setUp()
        targetContext.sharedPrefs().edit {
            putBoolean(IntroductionActivity.INTRODUCTION_SLIDES_SHOWN, true)
        }
    }

    @Test
    fun showFloatingActionButtonMakesFabVisible() {
        val scenario = ActivityScenario.launch(DeckPicker::class.java)
        scenario.onActivity { deckPicker ->
            deckPicker.floatingActionMenu.hideFloatingActionButton()
            deckPicker.floatingActionMenu.showFloatingActionButton()
            assertEquals(View.VISIBLE, deckPicker.findViewById<View>(R.id.fab_main).visibility)
        }
        scenario.close()
    }

    @Test
    fun hideFloatingActionButtonMakesFabGone() {
        val scenario = ActivityScenario.launch(DeckPicker::class.java)
        scenario.onActivity { deckPicker ->
            deckPicker.floatingActionMenu.showFloatingActionButton()
            deckPicker.floatingActionMenu.hideFloatingActionButton()
            assertEquals(View.GONE, deckPicker.findViewById<View>(R.id.fab_main).visibility)
        }
        scenario.close()
    }
}
