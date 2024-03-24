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

import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.testing.launchFragment
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.tabs.TabLayout
import com.google.android.material.textfield.TextInputLayout
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.scheduling.SetDueDateViewModel.Tab
import com.ichi2.annotations.NeedsTest
import com.ichi2.libanki.CardId
import com.ichi2.utils.positiveButton
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith

@Ignore("selectTab(1) does not attach Ids")
@NeedsTest("get the tests working")
@RunWith(AndroidJUnit4::class)
class SetDueDateDialogTest : RobolectricTest() {
    @Test
    fun `switch tabs`() = testDialog {
        selectTab(0)
        assertThat(viewModel.currentTab, equalTo(Tab.SINGLE_DAY))
        selectTab(1)
        assertThat(viewModel.currentTab, equalTo(Tab.DATE_RANGE))
    }

    @Test
    fun `initial suffix is set`() = testDialog {
        selectTab(0)
        assertThat(singleDayTextLayout.suffixText, equalTo("days"))
        assertThat(dateRangeStartLayout.suffixText, equalTo("days"))
        assertThat(dateRangeEndLayout.suffixText, equalTo("days"))
    }

    @Test
    fun `set single day`() = testDialog {
        selectTab(0)
        assertThat(positiveButtonIsEnabled, equalTo(false))
        singleDayText.setText("1")
        assertThat(positiveButtonIsEnabled, equalTo(true))
    }

    @Test
    fun `set date range`() = testDialog {
        selectTab(1)
        assertThat(positiveButtonIsEnabled, equalTo(false))
        dateRangeStart.setText("1")
        dateRangeEnd.setText("5")
        assertThat(positiveButtonIsEnabled, equalTo(true))
    }

    @Test
    fun `set update interval`() = testDialog {
        assertThat(viewModel.updateIntervalToMatchDueDate, equalTo(false))
        changeInterval.isChecked = true
        assertThat(viewModel.updateIntervalToMatchDueDate, equalTo(true))
    }

    @Test
    fun `singular text`() = testDialog(cards = listOf(1)) {
        selectTab(0)
        assertThat(singleDayTextLayout.hint, equalTo("Show card in"))
        selectTab(1)
        assertThat(dateRangeLabel.text, equalTo("Show card in range"))
    }

    @Test
    fun `plural text`() = testDialog(cards = listOf(1, 2)) {
        selectTab(0)
        assertThat(singleDayTextLayout.hint, equalTo("Show cards in"))
        selectTab(1)
        assertThat(dateRangeLabel.text, equalTo("Show cards in range"))
    }

    @Test
    fun `integration test`() = testDialog {
        assertThat(viewModel.updateIntervalToMatchDueDate, equalTo(false))
        selectTab(1)
        dateRangeStart.setText("1")
        dateRangeEnd.setText("2")
        changeInterval.isChecked = true

        assertThat(viewModel.calculateDaysParameter(), equalTo("1-2!"))
    }

    private fun testDialog(
        cards: List<CardId> = listOf(1),
        action: SetDueDateDialog.() -> Unit
    ) = runTest {
        val dialog = SetDueDateDialog.newInstance(cards)
        launchFragment(
            themeResId = R.style.Base_Theme_Light,
            fragmentArgs = dialog.arguments
        ) {
            return@launchFragment dialog
        }.apply {
            moveToState(Lifecycle.State.CREATED)
            this.onFragment {
                action(it)
            }
        }
    }
}

fun TabLayout.selectTab(index: Int) = selectTab(getTabAt(index))

fun SetDueDateDialog.selectTab(index: Int) {
    val tabLayout = dialog!!.findViewById<TabLayout>(R.id.tab_layout)
    tabLayout.selectTab(index)
    if (index == 1) {
        TODO("Flaky: FragmentStateAdapter does not include views")
    }
}

val SetDueDateDialog.positiveButtonIsEnabled get() =
    (dialog as AlertDialog).positiveButton.isEnabled

val SetDueDateDialog.singleDayTextLayout: TextInputLayout get() =
    dialog!!.findViewById(R.id.set_due_date_single_day_text)

val SetDueDateDialog.singleDayText: EditText get() = singleDayTextLayout.editText!!

val SetDueDateDialog.dateRangeStartLayout: TextInputLayout get() =
    dialog!!.findViewById(R.id.date_range_start_layout)

val SetDueDateDialog.dateRangeStart: EditText get() =
    dateRangeStartLayout.editText!!

val SetDueDateDialog.dateRangeEndLayout: TextInputLayout get() =
    dialog!!.findViewById(R.id.date_range_end_layout)

val SetDueDateDialog.dateRangeEnd: EditText get() =
    dateRangeEndLayout.editText!!

val SetDueDateDialog.changeInterval: CheckBox get() =
    dialog!!.findViewById(R.id.change_interval)!!

val SetDueDateDialog.dateRangeLabel: TextView get() =
    dialog!!.findViewById(R.id.date_range_label)
