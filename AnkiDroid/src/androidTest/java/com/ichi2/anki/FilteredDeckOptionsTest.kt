// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import androidx.test.core.app.ActivityScenario
import com.ichi2.anki.tests.InstrumentedTest
import com.ichi2.anki.testutil.GrantStoragePermission
import com.ichi2.anki.testutil.disableIntroductionSlide
import com.ichi2.anki.testutil.grantPermissions
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class FilteredDeckOptionsTest : InstrumentedTest() {
    @get:Rule
    val runtimePermissionRule = grantPermissions(GrantStoragePermission.storagePermission)

    @Before
    fun before() {
        disableIntroductionSlide()
    }

    @Test
    fun canOpenDeckOptions() {
        ActivityScenario.launch(DeckPicker::class.java).use { scenario ->
            scenario.onActivity { deckPicker ->
                val deckId = col.decks.newFiltered("Filtered Testing")
                deckPicker.viewModel.openDeckOptions(deckId)
            }
        }
    }
}
