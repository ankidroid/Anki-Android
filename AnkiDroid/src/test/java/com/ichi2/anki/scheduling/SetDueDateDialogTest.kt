// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.scheduling

import android.os.Bundle
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.testing.launchFragment
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.textfield.TextInputLayout
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.RobolectricTest.Companion.advanceRobolectricLooper
import com.ichi2.anki.browser.IdsFile
import com.ichi2.anki.common.annotations.NeedsTest
import com.ichi2.anki.libanki.CardId
import com.ichi2.anki.libanki.sched.SetDueDateDays
import com.ichi2.anki.scheduling.SetDueDateViewModel.Tab
import com.ichi2.anki.utils.ext.requireParcelable
import com.ichi2.utils.positiveButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.advanceUntilIdle
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.coroutines.coroutineContext

@NeedsTest("set interval to same value visibility with FSRS")
@RunWith(AndroidJUnit4::class)
class SetDueDateDialogTest : RobolectricTest() {
    @Test
    fun `switch tabs`() =
        testDialog {
            selectTab(0)
            assertThat(viewModel.currentTab, equalTo(Tab.SINGLE_DAY))
            selectTab(1)
            assertThat(viewModel.currentTab, equalTo(Tab.DATE_RANGE))
        }

    @Test
    fun `initial suffix is set`() =
        testDialog {
            selectTab(0)
            assertThat(singleDayTextLayout.suffixText, equalTo("days"))
            selectTab(1)
            assertThat(dateRangeStartLayout.suffixText, equalTo("days"))
            assertThat(dateRangeEndLayout.suffixText, equalTo("days"))
        }

    @Test
    fun `set single day`() =
        testDialog {
            selectTab(0)
            assertThat(positiveButtonIsEnabled, equalTo(false))
            singleDayText.setText("1")
            assertThat(positiveButtonIsEnabled, equalTo(true))
        }

    @Test
    fun `set date range`() =
        testDialog {
            selectTab(1)
            assertThat(positiveButtonIsEnabled, equalTo(false))
            dateRangeStart.setText("1")
            dateRangeEnd.setText("5")
            assertThat(positiveButtonIsEnabled, equalTo(true))
        }

    @Test
    fun `set update interval`() =
        testDialog {
            assertThat(viewModel.updateIntervalToMatchDueDate, equalTo(false))
            changeInterval.isChecked = true
            assertThat(viewModel.updateIntervalToMatchDueDate, equalTo(true))
        }

    @Test
    fun `singular text`() =
        testDialog(cardCount = 1) {
            selectTab(0)
            assertThat(dateSingleLabel.text, equalTo("Show card in"))
            selectTab(1)
            assertThat(dateRangeLabel.text, equalTo("Show card in range"))
        }

    @Test
    fun `plural text`() =
        testDialog(cardCount = 2) {
            selectTab(0)
            assertThat(dateSingleLabel.text, equalTo("Show cards in"))
            selectTab(1)
            assertThat(dateRangeLabel.text, equalTo("Show cards in range"))
        }

    @Test
    fun `integration test`() =
        testDialog {
            assertThat(viewModel.updateIntervalToMatchDueDate, equalTo(false))
            selectTab(1)
            dateRangeStart.setText("1")
            dateRangeEnd.setText("2")
            changeInterval.isChecked = true

            assertThat(viewModel.calculateDaysParameter(), equalTo(SetDueDateDays("1-2!")))
        }

    @Test
    fun `single day input limited to 5 digits`() =
        testDialog {
            selectTab(0)
            singleDayText.setText("123456")
            assertThat(singleDayText.text.toString(), equalTo("12345"))
        }

    @Test
    fun `range start input limited to 5 digits`() =
        testDialog {
            selectTab(1)
            dateRangeStart.setText("123456")
            assertThat(dateRangeStart.text.toString(), equalTo("12345"))
        }

    @Test
    fun `range end input limited to 5 digits`() =
        testDialog {
            selectTab(1)
            dateRangeEnd.setText("123456")
            assertThat(dateRangeEnd.text.toString(), equalTo("12345"))
        }

    @Test
    fun `card ids are readable after recreation`() =
        runTest {
            val cardIds = List(2) { addBasicNote().firstCard().id }
            launchFragment<SetDueDateDialog>(
                themeResId = R.style.Base_Theme_Light,
                fragmentArgs = setDueDateArgs(cardIds),
            ).use { scenario ->
                advanceRobolectricLooper()
                scenario.recreate()
                advanceRobolectricLooper()
                scenario.onFragment { fragment ->
                    assertThat(fragment.cardIds, equalTo(cardIds))
                }
            }
        }

    @Test
    fun `ids file is removed after the dialog is dismissed`() =
        runTest {
            val cardIds = List(2) { addBasicNote().firstCard().id }
            val args = setDueDateArgs(cardIds)
            val idsFile = args.requireParcelable<IdsFile>(SetDueDateDialog.ARG_IDS_FILE)

            launchFragment<SetDueDateDialog>(
                themeResId = R.style.Base_Theme_Light,
                fragmentArgs = args,
            ).use { scenario ->
                advanceRobolectricLooper()
                assertThat("kept while the dialog is open", idsFile.exists(), equalTo(true))
                scenario.onFragment { it.dismiss() }
                advanceRobolectricLooper()
            }

            assertThat("removed once dismissed", idsFile.exists(), equalTo(false))
        }

    @Test
    fun `unreadable ids file does not crash`() =
        runTest {
            val cardIds = List(2) { addBasicNote().firstCard().id }
            val args = setDueDateArgs(cardIds)
            args.requireParcelable<IdsFile>(SetDueDateDialog.ARG_IDS_FILE).writeBytes(ByteArray(0))

            launchFragment<SetDueDateDialog>(
                themeResId = R.style.Base_Theme_Light,
                fragmentArgs = args,
            ).use {
                advanceRobolectricLooper()
            }
        }

    @Test
    fun `cancelled caller leaves no ids file behind`() =
        runTest {
            val cardIds = List(2) { addBasicNote().firstCard().id }
            val cacheDir = File(targetContext.cacheDir, "set-due-date-cancelled").also { it.mkdirs() }

            CoroutineScope(coroutineContext + Job())
                .async {
                    coroutineContext.cancel()
                    SetDueDateDialog.newInstance(cacheDir, cardIds)
                }
            advanceUntilIdle()

            assertThat(cacheDir.listFiles()?.size, equalTo(0))
        }

    private suspend fun setDueDateArgs(cardIds: List<CardId>): Bundle =
        SetDueDateDialog
            .newInstance(targetContext.externalCacheDir ?: targetContext.cacheDir, cardIds)
            .requireArguments()

    private fun testDialog(
        cardCount: Int = 1,
        action: SetDueDateDialog.() -> Unit,
    ) = runTest {
        val cardIds = List(cardCount) { addBasicNote().firstCard().id }
        val dialog = SetDueDateDialog.newInstance(targetContext.externalCacheDir ?: targetContext.cacheDir, cardIds)
        launchFragment(
            themeResId = R.style.Base_Theme_Light,
            fragmentArgs = dialog.arguments,
        ) {
            return@launchFragment dialog
        }.use { scenario ->
            scenario.moveToState(Lifecycle.State.RESUMED)
            advanceRobolectricLooper()
            scenario.onFragment {
                action(it)
            }
        }
    }
}

/**
 * Selects a tab by index
 *
 * @throws IllegalArgumentException if index is invalid
 */
fun TabLayout.selectTab(index: Int) =
    requireNotNull(getTabAt(index))
        { "Tab $index not found" }
        .also { tab -> selectTab(tab) }

/**
 * Selects a tab by index, and waits for the [androidx.viewpager2.adapter.FragmentStateAdapter]
 * to attach the page's fragment view to the dialog's view hierarchy.
 */
fun SetDueDateDialog.selectTab(index: Int) {
    val viewPager = dialog!!.findViewById<ViewPager2>(R.id.set_due_date_pager)
    viewPager.setCurrentItem(index, false)
    // FragmentStateAdapter attaches fragments asynchronously via the main looper
    advanceRobolectricLooper()
}

val SetDueDateDialog.positiveButtonIsEnabled get() =
    (dialog as AlertDialog).positiveButton.isEnabled

val SetDueDateDialog.singleDayTextLayout: TextInputLayout get() =
    dialog!!.findViewById(R.id.set_due_date_single_day_input_layout)

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

val SetDueDateDialog.dateSingleLabel: TextView get() =
    dialog!!.findViewById(R.id.date_single_label)
