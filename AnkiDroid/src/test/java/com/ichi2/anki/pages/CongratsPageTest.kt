// SPDX-License-Identifier: GPL-3.0-or-later
package com.ichi2.anki.pages

import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.StudyOptionsActivity
import com.ichi2.anki.observability.undoableOp
import com.ichi2.testutils.launchFragmentInContainer
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class CongratsPageTest : RobolectricTest() {
    // https://github.com/ankidroid/Anki-Android/pull/21409#pullrequestreview-3568785419
    @Test
    fun `resuming after a background rebuild refills the deck - navigates to study options`() =
        runTest {
            addBasicNote("Front", "Back")
            val deckId = addDynamicDeck("Filtered", "")
            withCol { sched.emptyFilteredDeck(deckId) }

            val scenario = launchFragmentInContainer<CongratsPage>()
            scenario.moveToState(Lifecycle.State.CREATED)

            // rebuild happens while the screen isn't visible - nobody is collecting congratsRefreshState
            undoableOp { sched.rebuildFilteredDeck(deckId) }

            scenario.moveToState(Lifecycle.State.RESUMED)
            advanceUntilIdle()

            scenario.onFragment { fragment ->
                val next = shadowOf(fragment.requireActivity()).nextStartedActivity
                assertEquals(StudyOptionsActivity::class.java.name, next?.component?.className)
            }
        }
}
