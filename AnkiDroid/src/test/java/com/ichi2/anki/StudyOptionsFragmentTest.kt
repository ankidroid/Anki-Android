// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>
// SPDX-License-Identifier: GPL-3.0-or-later
package com.ichi2.anki

import androidx.appcompat.app.AlertDialog
import androidx.appcompat.view.menu.MenuBuilder
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.ui.internationalization.sentenceCase
import com.ichi2.testutils.ext.triggerDeckNotFoundInLimitsMap
import com.ichi2.testutils.launchFragmentInContainer
import com.ichi2.testutils.withFragment
import com.ichi2.utils.neutralButton
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowDialog
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Fragment-level coverage for the closed-collection scenarios on tablets, where
 * [StudyOptionsFragment] is embedded inside [DeckPicker] and the collection can be
 * unusable during sync-on-startup.
 */
@RunWith(AndroidJUnit4::class)
class StudyOptionsFragmentTest : RobolectricTest() {
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
            ).onFragment { fragment ->
                assertEquals(Lifecycle.State.RESUMED, fragment.lifecycle.currentState)
            }
        }
    }

    @Test
    fun `onResume after STARTED with a closed collection does not crash`() {
        withNullCollection {
            launchFragmentInContainer<StudyOptionsFragment>(
                initialState = Lifecycle.State.STARTED,
            ).run {
                moveToState(Lifecycle.State.RESUMED)
                onFragment { fragment ->
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
            ).run {
                recreate()
                onFragment { fragment ->
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
            ).onFragment { fragment ->
                val menu = MenuBuilder(fragment.requireContext())
                fragment.onCreateMenu(menu, fragment.requireActivity().menuInflater)
                fragment.onPrepareMenu(menu)
            }
        }
    }

    @Test
    fun `onPrepareMenu does not crash when collection closes after population`() {
        col

        val scenario =
            launchFragmentInContainer<StudyOptionsFragment>(
                initialState = Lifecycle.State.RESUMED,
            )
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
