/*
 *  Copyright (c) 2025 David Allison <davidallisongithub@gmail.com>
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

import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.ichi2.anki.dialogs.EditDeckDescriptionDialogViewModel.Companion.ARG_DECK_ID
import com.ichi2.anki.dialogs.EditDeckDescriptionDialogViewModel.Companion.STATE_DESCRIPTION
import com.ichi2.anki.libanki.Consts.DEFAULT_DECK_ID
import com.ichi2.anki.libanki.testutils.AnkiTest
import com.ichi2.testutils.JvmTest
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests [EditDeckDescriptionDialogViewModel]
 */
@RunWith(AndroidJUnit4::class)
class EditDeckDescriptionDialogViewModelTest : JvmTest() {
    @Test
    fun `title is set`() =
        runViewModelTest {
            assertThat(windowTitle, equalTo("Default"))
        }

    @Test
    fun `default description is empty`() =
        runViewModelTest {
            assertThat(description, equalTo(""))
        }

    @Test
    fun `description is updated in database`() =
        runViewModelTest {
            description = "foo"
            assertThat("description not updated before save", defaultDeck.description, equalTo(""))

            saveAndExit()

            assertThat("description updated after save", defaultDeck.description, equalTo("foo"))
        }

    @Test
    fun `dialog dismissed if no changes`() =
        runViewModelTest {
            flowOfDismissDialog.test {
                onBackRequested()
                assertThat("dialog should be dismissed", expectMostRecentItem(), equalTo(true))
            }
        }

    @Test
    fun `dialog not immediately dismissed if going back with changes`() =
        runViewModelTest {
            flowOfDismissDialog.test {
                description = "foo"
                onBackRequested()
                assertThat("dialog should not be dismissed", expectMostRecentItem(), equalTo(false))
            }
        }

    @Test
    fun `'discard changes' shown if going back with changes`() =
        runViewModelTest {
            flowOfShowDiscardChanges.test {
                description = "foo"
                onBackRequested()
                assertThat("discard should be shown", expectMostRecentItem(), equalTo(Unit))
            }
        }

    @Test
    fun `'close without saving' closes even if changes are made`() =
        runViewModelTest {
            flowOfDismissDialog.test {
                description = "foo"
                closeWithoutSaving()
                assertThat("dialog should be dismissed", expectMostRecentItem(), equalTo(true))
            }
        }

    @Test
    fun `test state restoration`() =
        runViewModelTest(updatedDescription = "foo") {
            assertThat("database is unchanged", defaultDeck.description, equalTo(""))
            assertThat("dialog state is maintained", description, equalTo("foo"))
        }

    val AnkiTest.defaultDeck
        get() = col.decks.getLegacy(DEFAULT_DECK_ID)!!

    private fun runViewModelTest(
        updatedDescription: String? = null,
        testBody: suspend EditDeckDescriptionDialogViewModel.() -> Unit,
    ) = runTest {
        val viewModel =
            EditDeckDescriptionDialogViewModel(
                savedStateHandleOf(
                    ARG_DECK_ID to DEFAULT_DECK_ID,
                    STATE_DESCRIPTION to updatedDescription,
                ),
            )
        testBody(viewModel)
    }
}

fun savedStateHandleOf(vararg pairs: Pair<String, Any?>): SavedStateHandle = SavedStateHandle(mapOf(*pairs))
