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
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.scheduler.CustomStudyDefaultsResponse
import anki.scheduler.customStudyDefaultsResponse
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.dialogs.customstudy.CustomStudyDialog
import com.ichi2.anki.dialogs.customstudy.CustomStudyDialog.ContextMenuOption
import com.ichi2.anki.dialogs.customstudy.CustomStudyDialog.CustomStudyDefaults.Companion.toDomainModel
import com.ichi2.anki.dialogs.customstudy.CustomStudyDialog.CustomStudyListener
import com.ichi2.anki.dialogs.customstudy.CustomStudyDialogFactory
import com.ichi2.anki.dialogs.utils.performPositiveClick
import com.ichi2.annotations.NeedsTest
import com.ichi2.libanki.Collection
import com.ichi2.libanki.Consts
import com.ichi2.libanki.sched.Scheduler
import com.ichi2.testutils.AnkiFragmentScenario
import com.ichi2.testutils.ParametersUtils
import com.ichi2.testutils.isJsonEqual
import io.mockk.every
import io.mockk.mockk
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
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
    fun `new custom study decks have expected structure - issue 6289`() =
        runTest {
            val studyType = ContextMenuOption.STUDY_PREVIEW
            // we need a non-empty deck to custom study
            addNoteUsingBasicModel()

            withCustomStudyFragment(
                args = argumentsDisplayingSubscreen(studyType),
            ) { dialogFragment: CustomStudyDialog ->
                dialogFragment.submitSubscreenData()
            }

            val customStudy = col.decks.current()
            assertThat("Custom Study should be filtered", customStudy.isFiltered)

            // remove timestamps to allow us to compare JSON
            customStudy.remove("id")
            customStudy.remove("mod")
            customStudy.remove("name")

            // compare JSON
            @Language("json")
            val expected =
                """
                {
                    "browserCollapsed": false,
                    "collapsed": false,
                    "delays": null,
                    "desc": "",
                    "dyn": 1,
                    "lrnToday": [0, 0],
                    "newToday": [0, 0],
                    "previewDelay": 0,
                    "previewAgainSecs": 60,
                    "previewHardSecs": 600,
                    "previewGoodSecs": 0,
                    "resched": true,
                    "revToday": [0, 0],
                    "separate": true,
                    "terms": [
                        ["deck:\"Default\" prop:due<=1", 99999, 6]
                    ],
                    "timeToday": [0, 0],
                    "usn": -1
                }
                """.trimIndent()
            assertThat(customStudy, isJsonEqual(JSONObject(expected)))
        }

    @Test
    @NeedsTest("previous value for 'increase review card limit' is suggested")
    fun `previous value for 'increase new card limit' is suggested`() {
        // add cards to be sure we can extend successfully. Needs to be > 20
        repeat(23) {
            addNoteUsingBasicModel()
        }
        val newExtendByValue = 1

        assertThat("'new' default value", defaultsOfDefaultDeck.extendNew.initialValue, equalTo(0))

        // extend limits with a value of '1'
        withCustomStudyFragment(
            args = argumentsDisplayingSubscreen(ContextMenuOption.STUDY_NEW),
        ) { dialogFragment: CustomStudyDialog ->

            onSubscreenEditText()
                .perform(replaceText(newExtendByValue.toString()))

            dialogFragment.submitSubscreenData()
        }

        // ensure backend is updated
        assertThat(
            "'new' updated value",
            defaultsOfDefaultDeck.extendNew.initialValue,
            equalTo(newExtendByValue),
        )

        // ensure 'newExtendByValue' is used by our UI
        withCustomStudyFragment(
            args = argumentsDisplayingSubscreen(ContextMenuOption.STUDY_NEW),
        ) { dialogFragment: CustomStudyDialog ->
            onSubscreenEditText()
                .check(matches(withText(newExtendByValue.toString())))
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
            onView(withText(TR.customStudyIncreaseTodaysNewCardLimit()))
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
            onView(withText(TR.customStudyIncreaseTodaysNewCardLimit()))
                .inRoot(isDialog())
                .check(doesNotExist())
        }
    }

    @Test
    @Config(qualifiers = "en")
    fun `'increase review limit' is shown when there are new cards`() {
        val studyDefaults = customStudyDefaultsResponse { availableReview = 1 }

        withCustomStudyFragment(
            args = argumentsDisplayingMainScreen(),
            factory = dialogFactory(col = mockCollectionWithSchedulerReturning(studyDefaults)),
        ) { dialogFragment: CustomStudyDialog ->
            onView(withText(TR.customStudyIncreaseTodaysReviewCardLimit()))
                .inRoot(isDialog())
                .check(matches(isDisplayed()))
        }
    }

    @Test
    @Config(qualifiers = "en")
    fun `'increase review limit' is not shown when there are no new cards`() {
        val studyDefaults = customStudyDefaultsResponse { availableReview = 0 }

        withCustomStudyFragment(
            args = argumentsDisplayingMainScreen(),
            factory = dialogFactory(col = mockCollectionWithSchedulerReturning(studyDefaults)),
        ) { dialogFragment: CustomStudyDialog ->
            onView(withText(TR.customStudyIncreaseTodaysReviewCardLimit()))
                .inRoot(isDialog())
                .check(doesNotExist())
        }
    }

    /**
     * Runs [block] on a [CustomStudyDialog]
     */
    private fun withCustomStudyFragment(
        args: Bundle,
        factory: CustomStudyDialogFactory = dialogFactory(),
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

    private fun dialogFactory(col: Collection? = null) = CustomStudyDialogFactory({ col ?: this.col }, mockListener)

    private fun argumentsDisplayingMainScreen() =
        CustomStudyDialog(mock(), ParametersUtils.whatever())
            .displayingMainScreen()
            .requireArguments()

    @Suppress("SameParameterValue")
    private fun argumentsDisplayingSubscreen(subscreen: ContextMenuOption) =
        CustomStudyDialog(mock(), ParametersUtils.whatever())
            .displayingSubscreen(subscreen)
            .requireArguments()

    private fun CustomStudyDialog.displayingSubscreen(subscreen: ContextMenuOption) =
        withArguments(
            did = Consts.DEFAULT_DECK_ID,
            contextMenuAttribute = subscreen,
        )

    private fun CustomStudyDialog.displayingMainScreen() =
        withArguments(
            did = Consts.DEFAULT_DECK_ID,
        )

    private fun onSubscreenEditText() =
        onView(withId(R.id.custom_study_details_edittext2))
            .inRoot(isDialog())

    private fun CustomStudyDialog.submitSubscreenData() =
        assertNotNull(dialog as? AlertDialog?, "dialog").also { dialog ->
            dialog.performPositiveClick()
        }

    /**
     * The current backend value of [CustomStudyDialog.CustomStudyDefaults] for the default deck
     * */
    private val defaultsOfDefaultDeck
        get() = col.sched.customStudyDefaults(Consts.DEFAULT_DECK_ID).toDomainModel()
}
