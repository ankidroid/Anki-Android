/*
 *  Copyright (c) 2026 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki.browser.search

import android.content.Intent
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CardBrowser
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.browser.SearchHistory
import com.ichi2.anki.settings.Prefs
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

/** Test of [StandardSearchFragment] */
@RunWith(AndroidJUnit4::class)
class StandardSearchFragmentTest : RobolectricTest() {
    @Before
    override fun setUp() {
        super.setUp()
        SearchHistory().clear()

        Prefs.devUsingCardBrowserSearchView = true
    }

    @After
    fun after() {
        Prefs.devUsingCardBrowserSearchView = false
    }

    @Test
    fun `test sample`() {
        withFragment {
            assertEquals(4, binding.searchHistory.count)
        }
    }

    fun withFragment(block: StandardSearchFragment.() -> Unit) =
        runTest {
            val cardBrowserIntent = Intent(targetContext, CardBrowser::class.java)

            ActivityScenario.launch<CardBrowser>(cardBrowserIntent).use { scenario ->
                scenario.moveToState(Lifecycle.State.RESUMED)

                scenario.onActivity { browser ->
                    assertThat("Activity is not finishing", !browser.isFinishing)
                    assertThat(browser.useSearchView, equalTo(true))

                    val browserFragment = browser.cardBrowserFragment

                    val targetFragment = browserFragment.requireChildFragment<StandardSearchFragment>(StandardSearchFragment.TAG)

                    block(targetFragment)
                }
            }
        }
}

fun <T : Fragment> Fragment.requireChildFragment(tag: String): T {
    @Suppress("UNCHECKED_CAST")
    return requireNotNull(this.childFragmentManager.findFragmentByTag(tag) as? T?)
    { "can't find tag $tag" }
}
