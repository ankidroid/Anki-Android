/*
 *  Copyright (c) 2023 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki.browser

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.testutils.JvmTest
import com.ichi2.testutils.createTransientDirectory
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CardBrowserViewModelTest : JvmTest() {
    @Test
    fun `delete search history - Issue 14989`() = runViewModelTest {
        saveSearch("hello", "aa")
        savedSearches().also { searches ->
            assertThat("filters after saving", searches.size, equalTo(1))
            assertThat("filters after saving", searches["hello"], equalTo("aa"))
        }
        removeSavedSearch("hello")
        assertThat("filters should be empty after removing", savedSearches().size, equalTo(0))
    }

    private fun runViewModelTest(testBody: suspend CardBrowserViewModel.() -> Unit) = runTest {
        val viewModel = CardBrowserViewModel(
            lastDeckIdRepository = SharedPreferencesLastDeckIdRepository(),
            cacheDir = createTransientDirectory(),
            preferences = AnkiDroidApp.sharedPreferencesProvider
        )
        testBody(viewModel)
    }
}
