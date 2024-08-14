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

import androidx.annotation.DrawableRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ichi2.anki.R
import com.ichi2.libanki.CardId
import com.ichi2.libanki.sched.SetDueDateDays
import com.ichi2.libanki.undoableOp
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import timber.log.Timber

/**
 * 0 = today
 * @see SetDueDateDays
 */
typealias NumberOfDaysInFuture = Int

/**
 * [ViewModel] for [SetDueDateDialog]
 */
class SetDueDateViewModel : ViewModel() {
    /** Whether the value may be submitted */
    val isValidFlow = MutableStateFlow(false)

    /** The cards to change the due date of */
    lateinit var cardIds: List<CardId>

    /** The number of cards which will be affected */
    // primarily used for plurals
    val cardCount
        get() = cardIds.size

    /** The number of days in the future if we are on [Tab.SINGLE_DAY] */
    var nextSingleDayDueDate: NumberOfDaysInFuture? = null
        set(value) {
            field = if (value != null && value >= 0) {
                value
            } else {
                null
            }
            Timber.d("update SINGLE_DAY to %s", field)
            refreshIsValid()
        }

    var dateRange: DateRange = DateRange()
        private set

    internal var currentTab = Tab.SINGLE_DAY
        set(value) {
            Timber.i("selected tab %s", value)
            field = value
            refreshIsValid()
        }

    /** If `true`, the interval of the card is updated to match the calculated due date */
    var updateIntervalToMatchDueDate: Boolean = false
        set(value) {
            Timber.d("updateIntervalToMatchDueDate: %b", value)
            field = value
        }

    fun init(cardIds: LongArray) {
        this.cardIds = cardIds.toList()
    }

    fun setNextDateRangeStart(value: Int?) {
        Timber.d("updated date range start to %s", value)
        dateRange.start = value
        refreshIsValid()
    }

    fun setNextDateRangeEnd(value: Int?) {
        Timber.d("updated date range end to %s", value)
        dateRange.end = value
        refreshIsValid()
    }

    private fun refreshIsValid() {
        val isValid = when (currentTab) {
            Tab.SINGLE_DAY -> nextSingleDayDueDate.let { it != null && it >= 0 }
            Tab.DATE_RANGE -> dateRange.isValid()
        }
        isValidFlow.update { isValid }
    }

    fun calculateDaysParameter(): SetDueDateDays? {
        val dateRange = when (currentTab) {
            Tab.SINGLE_DAY -> nextSingleDayDueDate?.let { "$it" }
            Tab.DATE_RANGE -> dateRange.toDaysParameter()
        } ?: return null

        // add a "!" suffix if necessary
        val param = if (this.updateIntervalToMatchDueDate) "$dateRange!" else dateRange
        return SetDueDateDays(param)
    }

    /**
     * Updates the due date of [cardIds] based on the current state.
     * @return The number of cards affected, or `null` if an error occurred
     */
    fun updateDueDateAsync() = viewModelScope.async {
        val days = calculateDaysParameter() ?: return@async null
        // TODO: Provide a config parameter - we can use this to set a 'last used value' in the UI
        // when the screen is opened
        undoableOp { sched.setDueDate(cardIds, days) }
        return@async cardIds.size
    }

    enum class Tab(val position: Int, @DrawableRes val icon: Int) {
        /** Set the due date to a single day */
        SINGLE_DAY(0, R.drawable.calendar_single_day),

        /** Sets the due date randomly between a range of days */
        DATE_RANGE(1, R.drawable.calendar_date_range)
    }

    class DateRange(
        var start: NumberOfDaysInFuture? = null,
        var end: NumberOfDaysInFuture? = null
    ) {
        fun isValid(): Boolean {
            val start = start ?: return false
            val end = end ?: return false
            if (start < 0) return false // 0 is valid -> today
            return start <= end
        }

        fun toDaysParameter(): String? {
            if (!isValid()) return null
            if (start == end) return "$start"
            return "$start-$end"
        }

        override fun toString() = "$start – $end"
    }
}
