// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.browser.search

import com.ichi2.anki.EmptyApplicationCategory
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.libanki.testutils.AnkiTest
import com.ichi2.testutils.EmptyApplication
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/** Test for [CardState] */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(application = EmptyApplication::class)
@Category(EmptyApplicationCategory::class)
class CardStateTest : RobolectricTest() {
    @ParameterizedRobolectricTestRunner.Parameter(0)
    @JvmField
    var stateParam: CardState? = null

    val state get() = stateParam!!

    @Test
    fun `search strings are valid`() {
        fun CardState.toExpectedSearchString() =
            when (this) {
                CardState.New -> "is:new"
                CardState.Learning -> "is:learn"
                CardState.Review -> "is:review"
                CardState.Buried -> "is:buried"
                CardState.Suspended -> "is:suspended"
            }

        assertEquals(state.toSearchString(), state.toExpectedSearchString())
    }

    @Test
    fun `backend label is unchanged`() {
        fun CardState.toExpectedLabel() =
            when (this) {
                CardState.New -> "New"
                CardState.Learning -> "Learning"
                CardState.Review -> "Review"
                CardState.Buried -> "Buried"
                CardState.Suspended -> "Suspended"
            }
        assertEquals(state.label, state.toExpectedLabel())
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun parameters(): Collection<Array<Any>> = CardState.entries.map { arrayOf(it) }
    }
}

context(test: AnkiTest)
fun CardState.toSearchString() = test.col.buildSearchString(listOf(this.toSearchNode()))
