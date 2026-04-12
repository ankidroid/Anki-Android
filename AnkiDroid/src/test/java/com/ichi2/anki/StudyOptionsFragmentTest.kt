/*
 * Copyright (c) 2026 Ashish Yadav <mailtoashish693@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki

import androidx.appcompat.view.menu.MenuBuilder
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.testutils.launchFragmentInContainer
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Fragment-level coverage for the closed-collection scenarios on tablets, where
 * [StudyOptionsFragment] is embedded inside [DeckPicker] and the collection can be
 * unusable during sync-on-startup.
 */
@RunWith(AndroidJUnit4::class)
class StudyOptionsFragmentTest : RobolectricTest() {
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

            runBlocking { fragment.viewModel.refreshData().join() }
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

    /** Wraps [block] with a closed collection, restoring it afterwards. */
    private inline fun withNullCollection(block: () -> Unit) {
        enableNullCollection()
        try {
            block()
        } finally {
            disableNullCollection()
        }
    }
}
