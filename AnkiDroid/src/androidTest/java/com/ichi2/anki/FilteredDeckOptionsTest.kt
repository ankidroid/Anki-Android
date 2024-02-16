/*
 *  Copyright (c) 2024 David Allison <davidallisongithub@gmail.com>
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

import androidx.core.content.edit
import androidx.test.core.app.ActivityScenario
import com.ichi2.anki.tests.InstrumentedTest
import com.ichi2.anki.testutil.GrantStoragePermission
import com.ichi2.anki.testutil.grantPermissions
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class FilteredDeckOptionsTest : InstrumentedTest() {
    @get:Rule
    val runtimePermissionRule = grantPermissions(GrantStoragePermission.storagePermission)

    @Before
    fun before() {
        AnkiDroidApp.sharedPrefs().edit {
            putBoolean(IntroductionActivity.INTRODUCTION_SLIDES_SHOWN, true)
        }
    }

    @Test
    fun canOpenDeckOptions() {
        ActivityScenario.launch(DeckPicker::class.java).onActivity { deckPicker ->
            val deckId = col.decks.newDyn("Filtered Testing")
            deckPicker.showContextMenuDeckOptions(deckId)
        }.close()
    }
}
