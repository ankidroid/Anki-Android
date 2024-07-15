/*
 *  Copyright (c) 2024 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki.scheduling

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.scheduling.SetDueDateViewModel.DateRange
import com.ichi2.anki.scheduling.SetDueDateViewModel.Tab
import com.ichi2.libanki.CardId
import com.ichi2.libanki.sched.SetDueDateDays
import com.ichi2.testutils.JvmTest
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SetDueDateViewModelTest : JvmTest() {
    @Test
    fun `initial values`() = runViewModelTest(cardIds = listOf(1, 2)) {
        assertThat("card count", cardCount, equalTo(2))
        assertThat("is not valid", !isValid)
        assertThat("initial tab is single day", currentTab, equalTo(Tab.SINGLE_DAY))
    }

    @Test
    fun `single day validation`() = runViewModelTest {
        fun canSaveWithValue(input: NumberOfDaysInFuture?, expected: Boolean) {
            nextSingleDayDueDate = input
            assertThat("$input", isValid == expected, equalTo(true))
        }
        assertThat("is not valid", !isValid)
        canSaveWithValue(-1, false)
        canSaveWithValue(0, true)
        canSaveWithValue(null, false)
        canSaveWithValue(1, true)
    }

    @Test
    fun `date range validation`() = runViewModelTest {
        fun canSaveWithValue(
            start: NumberOfDaysInFuture?,
            end: NumberOfDaysInFuture?,
            expected: Boolean,
            message: String? = null
        ) {
            setNextDateRangeStart(start)
            setNextDateRangeEnd(end)
            assertThat(message ?: "${DateRange(start, end)}", isValid == expected, equalTo(true))
        }
        currentTab = Tab.DATE_RANGE
        assertThat("initially not valid", !isValid)
        canSaveWithValue(-1, -1, false)
        canSaveWithValue(-1, 0, false)
        canSaveWithValue(0, -1, false)

        canSaveWithValue(0, 0, true)

        canSaveWithValue(null, null, false)
        canSaveWithValue(null, 1, false)
        canSaveWithValue(1, null, false)

        canSaveWithValue(2, 3, true)
        canSaveWithValue(3, 2, false, "start must be <= end")
    }

    @Test
    fun `test string output`() = runViewModelTest {
        fun eq(s: String) = equalTo(SetDueDateDays(s))
        currentTab = Tab.SINGLE_DAY
        assertThat(calculateDaysParameter(), equalTo(null))

        nextSingleDayDueDate = 1
        assertThat(calculateDaysParameter(), eq("1"))

        updateIntervalToMatchDueDate = true
        assertThat(calculateDaysParameter(), eq("1!"))

        updateIntervalToMatchDueDate = false
        currentTab = Tab.DATE_RANGE
        assertThat(calculateDaysParameter(), equalTo(null))

        setNextDateRangeStart(0)
        setNextDateRangeEnd(0)
        assertThat(calculateDaysParameter(), eq("0"))

        setNextDateRangeEnd(1)
        assertThat(calculateDaysParameter(), eq("0-1"))

        updateIntervalToMatchDueDate = true
        assertThat(calculateDaysParameter(), eq("0-1!"))
    }

    private fun runViewModelTest(cardIds: List<CardId> = listOf(1, 2, 3), testBody: suspend SetDueDateViewModel.() -> Unit) = runTest {
        val viewModel = SetDueDateViewModel()
        viewModel.init(cardIds.toLongArray())
        testBody(viewModel)
    }
}

val SetDueDateViewModel.isValid: Boolean get() = isValidFlow.value
