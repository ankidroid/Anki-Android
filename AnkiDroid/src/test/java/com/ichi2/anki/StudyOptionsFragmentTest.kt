// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>
// SPDX-License-Identifier: GPL-3.0-or-later
package com.ichi2.anki

import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.view.menu.MenuBuilder
import androidx.fragment.app.commitNow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.ui.internationalization.sentenceCase
import com.ichi2.testutils.ext.triggerDeckNotFoundInLimitsMap
import com.ichi2.testutils.launchFragmentInContainer
import com.ichi2.testutils.withFragment
import com.ichi2.utils.neutralButton
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Tests for [StudyOptionsFragment].
 */
@RunWith(AndroidJUnit4::class)
class StudyOptionsFragmentTest : RobolectricTest() {
    /** Issue 22348: replacing the tablet panel can detach it before its initial refresh starts. */
    @Test
    @Config(qualifiers = "xlarge")
    fun `replacing study options before its refresh starts exposes lifecycle misuse`() =
        assertLifecycleFailure("not attached to an activity") {
            setIntroductionSlidesShown(true)
            addBasicNote()
            val controller = Robolectric.buildActivity(DeckPicker::class.java).setup()
            saveControllerForCleanup(controller)
            advanceUntilIdle()
            advanceRobolectricLooper()
            val activity = controller.get()
            assertTrue(activity.fragmented)
            val removedFragment = StudyOptionsFragment()
            val replacement = StudyOptionsFragment()
            lateinit var removedViewModel: StudyOptionsViewModel

            activity.lifecycleScope
                .launch {
                    // Main.immediate queues the nested refresh until this coroutine yields.
                    activity.supportFragmentManager.commitNow {
                        replace(R.id.studyoptions_fragment, removedFragment)
                    }
                    removedViewModel = removedFragment.viewModel
                    assertIs<StudyOptionsState.Loading>(removedViewModel.state)
                    activity.supportFragmentManager.commitNow {
                        replace(R.id.studyoptions_fragment, replacement)
                    }
                }.join()
            advanceUntilIdle()
            advanceRobolectricLooper()

            assertNull(removedFragment.activity)
            assertFalse(activity.isFinishing)
            assertIs<StudyOptionsState.Loading>(removedViewModel.state)
            assertSame(replacement, activity.fragment)
            assertEquals(Lifecycle.State.RESUMED, replacement.lifecycle.currentState)
            val state = assertIs<StudyOptionsState.StudyOptions>(replacement.viewModel.state)
            assertEquals(1, state.data.newCardsToday)
            val newCount = replacement.requireView().findViewById<TextView>(R.id.studyoptions_new_count)
            assertEquals("1", newCount.text.toString())
        }

    @Test
    fun `study options refresh queued before finishing exposes lifecycle misuse`() =
        assertLifecycleFailure("activity is finishing") {
            addBasicNote()
            val controller = Robolectric.buildActivity(StudyOptionsActivity::class.java).setup()
            saveControllerForCleanup(controller)
            advanceUntilIdle()
            advanceRobolectricLooper()
            val activity = controller.get()
            val fragment = assertIs<StudyOptionsFragment>(activity.supportFragmentManager.findFragmentById(R.id.studyoptions_frame))
            val initialState = assertIs<StudyOptionsState.StudyOptions>(fragment.viewModel.state)
            assertEquals(1, initialState.data.newCardsToday)
            addBasicNote()

            activity.lifecycleScope
                .launch {
                    fragment.refreshInterface()
                    activity.finish()
                }.join()
            advanceUntilIdle()
            advanceRobolectricLooper()

            assertTrue(activity.isFinishing)
            assertSame(activity, fragment.activity)
            assertSame(initialState, fragment.viewModel.state)
        }

    /** Issue 21981: a refresh must offer recovery when the deck hierarchy is corrupt. */
    @Test
    fun `corrupt deck hierarchy offers Check database instead of crashing`() =
        runTest {
            triggerDeckNotFoundInLimitsMap()
            throwOnShowError = false

            withStudyOptions {
                val dialog = assertIs<AlertDialog>(ShadowDialog.getLatestDialog())
                assertTrue(dialog.isShowing)
                assertEquals("deck not found in limits map", getAlertDialogText(checkDismissed = true))
                assertEquals(TR.sentenceCase.checkDatabase, dialog.neutralButton?.text)

                dialog.dismiss()
                col.fixIntegrity()
                refreshAndAwait()
                val state = assertIs<StudyOptionsState.StudyOptions>(viewModel.state)
                assertEquals(1, state.data.newCardsToday)
            }
        }

    @Test
    fun `fragment reaches RESUMED with a closed collection`() {
        withNullCollection {
            launchFragmentInContainer<StudyOptionsFragment>(
                initialState = Lifecycle.State.RESUMED,
            ).use { scenario ->
                scenario.onFragment { fragment ->
                    assertEquals(Lifecycle.State.RESUMED, fragment.lifecycle.currentState)
                }
            }
        }
    }

    @Test
    fun `onResume after STARTED with a closed collection does not crash`() {
        withNullCollection {
            launchFragmentInContainer<StudyOptionsFragment>(
                initialState = Lifecycle.State.STARTED,
            ).use { scenario ->
                scenario.moveToState(Lifecycle.State.RESUMED)
                scenario.onFragment { fragment ->
                    assertEquals(Lifecycle.State.RESUMED, fragment.lifecycle.currentState)
                }
            }
        }
    }

    @Test
    fun `recreate with a closed collection survives lifecycle restart`() {
        withNullCollection {
            launchFragmentInContainer<StudyOptionsFragment>(
                initialState = Lifecycle.State.RESUMED,
            ).use { scenario ->
                scenario.recreate()
                scenario.onFragment { fragment ->
                    assertEquals(Lifecycle.State.RESUMED, fragment.lifecycle.currentState)
                }
            }
        }
    }

    @Test
    fun `onPrepareMenu does not crash with a closed collection from start`() {
        withNullCollection {
            launchFragmentInContainer<StudyOptionsFragment>(
                initialState = Lifecycle.State.RESUMED,
            ).use { scenario ->
                scenario.onFragment { fragment ->
                    val menu = MenuBuilder(fragment.requireContext())
                    fragment.onCreateMenu(menu, fragment.requireActivity().menuInflater)
                    fragment.onPrepareMenu(menu)
                }
            }
        }
    }

    @Test
    fun `onPrepareMenu does not crash when collection closes after population`() {
        col

        launchFragmentInContainer<StudyOptionsFragment>(
            initialState = Lifecycle.State.RESUMED,
        ).use { scenario ->
            scenario.onFragment { fragment ->
                runBlocking { fragment.viewModel.refreshData() }
                assertIs<StudyOptionsState.Empty>(fragment.viewModel.state)
            }
            withNullCollection {
                scenario.onFragment { fragment ->
                    @Suppress("RestrictedApi")
                    val menu = MenuBuilder(fragment.requireContext())
                    fragment.onCreateMenu(menu, fragment.requireActivity().menuInflater)
                    fragment.onPrepareMenu(menu)
                }
            }
        }
    }

    private fun assertLifecycleFailure(
        message: String,
        block: suspend TestScope.() -> Unit,
    ) {
        val failure = assertFailsWith<IllegalStateException> { runTest(testBody = block) }
        assertContains(failure.message.orEmpty(), message)
    }

    private fun TestScope.withStudyOptions(block: StudyOptionsFragment.() -> Unit) =
        launchFragmentInContainer<StudyOptionsFragment>().use { scenario ->
            advanceUntilIdle()
            advanceRobolectricLooper()
            scenario.withFragment(block)
        }

    context(scope: TestScope)
    private fun StudyOptionsFragment.refreshAndAwait() {
        refreshInterface()
        scope.advanceUntilIdle()
        advanceRobolectricLooper()
    }
}
