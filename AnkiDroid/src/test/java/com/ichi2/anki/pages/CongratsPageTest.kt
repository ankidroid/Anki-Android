// SPDX-License-Identifier: GPL-3.0-or-later
package com.ichi2.anki.pages

import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.scheduler.CongratsInfoResponse
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
import kotlin.test.assertFalse
import kotlin.test.assertNull

@RunWith(AndroidJUnit4::class)
class CongratsPageTest : RobolectricTest() {
    @Test
    fun `congrats polling does not require an attached activity`() =
        runTest {
            // Issues 15658 and 15679: a surviving page polled after its fragment detached.
            // requireActivity() turned each poll into a 500 response and a JavaScript alert.
            val fragment = CongratsPage()
            assertNull(fragment.activity)

            val response = fragment.handlePostRequest(PostRequestUri("/_anki/congratsInfo"), byteArrayOf())

            assertFalse(CongratsInfoResponse.parseFrom(response).reviewRemaining)
        }

    @Test
    fun `congrats polling succeeds while the page is stopped`() =
        runTest {
            launchFragmentInContainer<CongratsPage>().use { scenario ->
                lateinit var fragment: CongratsPage
                scenario.onFragment { fragment = it }
                scenario.moveToState(Lifecycle.State.CREATED)
                assertEquals(Lifecycle.State.CREATED, fragment.lifecycle.currentState)

                val response = fragment.handlePostRequest(PostRequestUri("/_anki/congratsInfo"), byteArrayOf())

                assertFalse(CongratsInfoResponse.parseFrom(response).reviewRemaining)
            }
        }

    // https://github.com/ankidroid/Anki-Android/pull/21409#pullrequestreview-3568785419
    @Test
    fun `resuming after a background rebuild refills the deck - navigates to study options`() =
        runTest {
            addBasicNote("Front", "Back")
            val deckId = addDynamicDeck("Filtered", "")
            withCol { sched.emptyFilteredDeck(deckId) }

            launchFragmentInContainer<CongratsPage>().use { scenario ->
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
}
