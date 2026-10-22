// SPDX-License-Identifier: GPL-3.0-or-later
package com.ichi2.anki.pages

import android.os.Bundle
import android.os.Looper
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.commitNow
import androidx.lifecycle.Lifecycle
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.hasDescendant
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.scheduler.CongratsInfoResponse
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.StudyOptionsActivity
import com.ichi2.anki.dialogs.customstudy.CustomStudyDialog
import com.ichi2.anki.dialogs.tags.TagsDialog
import com.ichi2.anki.dialogs.utils.performPositiveClick
import com.ichi2.anki.ioDispatcher
import com.ichi2.anki.libanki.Consts
import com.ichi2.anki.observability.undoableOp
import com.ichi2.testutils.EmptyAnkiActivity
import com.ichi2.testutils.launchFragmentInContainer
import com.ichi2.testutils.uninitializeField
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import org.hamcrest.Matchers.allOf
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
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

    @Test
    fun `custom study input dialog survives activity recreation`() =
        runTest {
            updateDeckConfig(Consts.DEFAULT_DECK_ID) { new.perDay = 0 }
            addBasicNote()
            ioDispatcher = StandardTestDispatcher(testScheduler)

            launchFragmentInContainer<CongratsPage>().use { scenario ->
                scenario.onFragment { it.bridgeCommands.getValue("customStudy").invoke() }
                shadowOf(Looper.getMainLooper()).idle()
                advanceUntilIdle()
                onView(withText(TR.customStudyIncreaseTodaysNewCardLimit()))
                    .inRoot(isDialog())
                    .perform(click())
                advanceUntilIdle()

                scenario.recreate()

                onView(withId(R.id.details_edit_text_2))
                    .inRoot(isDialog())
                    .check(matches(isDisplayed()))
            }
        }

    @Test
    fun `custom study tag selection survives process death`() =
        runTest {
            // Keep the deck finished so the Congrats page stays open.
            updateDeckConfig(Consts.DEFAULT_DECK_ID) { new.perDay = 0 }
            val selectedNote = addBasicNote().update { addTag("selected") }
            val otherNote = addBasicNote().update { addTag("other") }

            val original = Robolectric.buildActivity(EmptyAnkiActivity::class.java)
            saveControllerForCleanup(original)
            original.setup()
            val page = CongratsPage()
            original.get().supportFragmentManager.commitNow {
                add(android.R.id.content, page, "congrats")
            }
            page.bridgeCommands.getValue("customStudy").invoke()
            shadowOf(Looper.getMainLooper()).idle()

            onView(withText(TR.customStudyStudyByCardStateOrTag()))
                .inRoot(isDialog())
                .perform(scrollTo(), click())
            onView(withId(android.R.id.button1))
                .inRoot(isDialog())
                .perform(click())
            shadowOf(Looper.getMainLooper()).idle()
            assertNotNull(original.get().supportFragmentManager.findFragmentByTag("TagsDialog") as? TagsDialog)

            // recreate() retains ViewModels and process-local defaults. Restore a fresh activity
            // from saved state and clear the defaults to model process death with the picker open.
            val savedState = Bundle()
            original
                .pause()
                .stop()
                .saveInstanceState(savedState)
                .destroy()
            uninitializeField<CustomStudyDialog>("deferredDefaults")

            val restored = Robolectric.buildActivity(EmptyAnkiActivity::class.java)
            saveControllerForCleanup(restored)
            restored.setup(savedState)
            shadowOf(Looper.getMainLooper()).idle()
            val tagsDialog =
                assertNotNull(restored.get().supportFragmentManager.findFragmentByTag("TagsDialog") as? TagsDialog)

            // Submit through the real picker and FragmentResult listener, without injecting a result.
            onView(allOf(withId(R.id.tags_dialog_tag_item), hasDescendant(withText("selected"))))
                .inRoot(isDialog())
                .perform(click())
            (tagsDialog.requireDialog() as AlertDialog).performPositiveClick()
            shadowOf(Looper.getMainLooper()).idle()

            val customStudyDeck = assertNotNull(col.decks.customStudySession)
            assertEquals(customStudyDeck.id, selectedNote.firstCard().did)
            assertEquals(Consts.DEFAULT_DECK_ID, otherNote.firstCard().did)
        }
}
