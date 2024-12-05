/*
 Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>

 This program is free software; you can redistribute it and/or modify it under
 the terms of the GNU General Public License as published by the Free Software
 Foundation; either version 3 of the License, or (at your option) any later
 version.

 This program is distributed in the hope that it will be useful, but WITHOUT ANY
 WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 PARTICULAR PURPOSE. See the GNU General Public License for more details.

 You should have received a copy of the GNU General Public License along with
 this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.dialogs

import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.Lifecycle
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.scheduler.CustomStudyDefaultsResponse
import anki.scheduler.customStudyDefaultsResponse
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.dialogs.customstudy.CustomStudyDialog
import com.ichi2.anki.dialogs.customstudy.CustomStudyDialog.CustomStudyListener
import com.ichi2.anki.dialogs.customstudy.CustomStudyDialogFactory
import com.ichi2.anki.dialogs.utils.performPositiveClick
import com.ichi2.libanki.Collection
import com.ichi2.libanki.Consts
import com.ichi2.libanki.sched.Scheduler
import com.ichi2.testutils.AnkiFragmentScenario
import com.ichi2.testutils.ParametersUtils
import com.ichi2.testutils.isJsonEqual
import io.mockk.every
import io.mockk.mockk
import org.hamcrest.CoreMatchers.notNullValue
import org.hamcrest.MatcherAssert
import org.intellij.lang.annotations.Language
import org.json.JSONObject
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.mockito.kotlin.mock
import org.robolectric.annotation.Config
import kotlin.test.assertNotNull

@RunWith(AndroidJUnit4::class)
class CustomStudyDialogTest : RobolectricTest() {
    private var mockListener: CustomStudyListener? = null

    override fun setUp() {
        super.setUp()
        mockListener = Mockito.mock(CustomStudyListener::class.java)
    }

    @After
    override fun tearDown() {
        super.tearDown()
        Mockito.reset(mockListener)
    }

    @Test
    fun learnAheadCardsRegressionTest() {
        // #6289 - Regression Test
        val args =
            CustomStudyDialog(mock(), ParametersUtils.whatever())
                .withArguments(
                    1,
                    contextMenuAttribute = CustomStudyDialog.ContextMenuOption.STUDY_AHEAD,
                ).arguments
        val factory = CustomStudyDialogFactory({ this.col }, mockListener)
        AnkiFragmentScenario.launch(CustomStudyDialog::class.java, args, factory).use { scenario ->
            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.onFragment { f: CustomStudyDialog ->
                val dialog = assertNotNull(f.dialog as AlertDialog?)
                dialog.performPositiveClick()
            }
            val customStudy = col.decks.current()
            MatcherAssert.assertThat("Custom Study should be dynamic", customStudy.isFiltered)
            MatcherAssert.assertThat("could not find deck: Custom study session", customStudy, notNullValue())
            customStudy.remove("id")
            customStudy.remove("mod")
            customStudy.remove("name")
            @Language("JSON")
            val expected =
                """{
                    "browserCollapsed":false,
                    "collapsed":false,
                    "delays":null,
                    "desc":"",
                    "dyn":1,
                    "lrnToday":[0,0],
                    "newToday":[0,0],
                    "previewDelay":0,
                    "previewAgainSecs":60,"previewHardSecs":600,"previewGoodSecs":0,
                    "resched":true,
                    "revToday":[0,0],
                    "separate":true,
                    "terms":[["deck:\"Default\" prop:due<=1",99999,6]],
                    "timeToday":[0,0],
                    "usn":-1
                    }
                """
            MatcherAssert.assertThat(customStudy, isJsonEqual(JSONObject(expected)))
        }
    }

    @Test
    @Config(qualifiers = "en")
    fun `'increase new limit' is shown when there are new cards`() {
        val studyDefaults = customStudyDefaultsResponse { availableNew = 1 }

        withCustomStudyFragment(
            args = argumentsDisplayingMainScreen(),
            factory = dialogFactory(col = mockCollectionWithSchedulerReturning(studyDefaults)),
        ) { dialogFragment: CustomStudyDialog ->
            assertNotNull(dialogFragment.dialog as? AlertDialog?, "dialog")
            onView(withText(R.string.custom_study_increase_new_limit))
                .inRoot(isDialog())
                .check(matches(isDisplayed()))
        }
    }

    @Test
    @Config(qualifiers = "en")
    fun `'increase new limit' is not shown when there are no new cards`() {
        val studyDefaults = customStudyDefaultsResponse { availableNew = 0 }

        withCustomStudyFragment(
            args = argumentsDisplayingMainScreen(),
            factory = dialogFactory(col = mockCollectionWithSchedulerReturning(studyDefaults)),
        ) { dialogFragment: CustomStudyDialog ->
            assertNotNull(dialogFragment.dialog as? AlertDialog?, "dialog")
            onView(withText(R.string.custom_study_increase_new_limit))
                .inRoot(isDialog())
                .check(doesNotExist())
        }
    }

    /**
     * Runs [block] on a [CustomStudyDialog]
     */
    private fun withCustomStudyFragment(
        args: Bundle,
        factory: CustomStudyDialogFactory,
        block: (CustomStudyDialog) -> Unit,
    ) {
        AnkiFragmentScenario.launch(CustomStudyDialog::class.java, args, factory).use { scenario ->
            scenario.onFragment { dialogFragment: CustomStudyDialog ->
                block(dialogFragment)
            }
        }
    }

    private fun mockCollectionWithSchedulerReturning(response: CustomStudyDefaultsResponse) =
        mockk<Collection>(relaxed = true) {
            every { sched } returns
                mockk<Scheduler> {
                    every { customStudyDefaults(Consts.DEFAULT_DECK_ID) } returns response
                }
        }

    private fun dialogFactory(col: Collection) = CustomStudyDialogFactory({ col }, mockListener)

    private fun argumentsDisplayingMainScreen() =
        CustomStudyDialog(mock(), ParametersUtils.whatever())
            .displayingMainScreen()
            .requireArguments()

    private fun CustomStudyDialog.displayingMainScreen() =
        withArguments(
            did = Consts.DEFAULT_DECK_ID,
        )
}
