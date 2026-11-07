// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.browser

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.SingleFragmentActivity
import com.ichi2.anki.common.crashreporting.CrashReportService
import com.ichi2.anki.common.crashreporting.CrashReporter
import com.ichi2.anki.filtered.FilteredDeckOptionsFragment
import com.ichi2.anki.libanki.DeckNameId
import com.ichi2.anki.throwOnShowError
import kotlinx.coroutines.flow.update
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.containsInAnyOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowDialog

@RunWith(AndroidJUnit4::class)
class CardBrowserFilteredDeckReviewRegressionTest : RobolectricTest() {
    @Test
    fun `filtered deck retains selected deck identity after its old name is reused`() =
        withCardBrowserFragment {
            val selected = addBasicNote("selected").apply { moveToDeck("Selected") }
            val selectedDeckId = requireNotNull(col.decks.idForName("Selected"))
            activityViewModel.searchRequestFlow.update { request ->
                request.copy(query = "").copyFilters {
                    it.copy(decks = listOf(DeckNameId("Selected", selectedDeckId)))
                }
            }

            // The collection changes while the browser retains its selected deck ID and name.
            col.decks.rename(selectedDeckId, "Renamed")
            addBasicNote("unrelated").apply { moveToDeck("Selected") }

            val search = filteredDeckSearch()

            assertThat(col.findCards(search), containsInAnyOrder(selected.firstCard().id))
        }

    @Test
    fun `invalid browser query is not reported as a crash when creating a filtered deck`() {
        val originalReporter = CrashReportService.getReporter()
        val originalThrowOnShowError = throwOnShowError
        val reporter = mock<CrashReporter>()
        CrashReportService.setReporter(reporter)
        try {
            withCardBrowserFragment {
                throwOnShowError = false
                activityViewModel.setQuery("and").join()
                advanceRobolectricLooper()

                showFilteredDeckScreen()
                advanceRobolectricLooper()
                // The error handler sends its crash report when the dialog is dismissed.
                ShadowDialog.getLatestDialog()?.dismiss()
                advanceRobolectricLooper()

                verify(reporter, never()).sendExceptionReport(any<Throwable>(), anyOrNull(), anyOrNull(), any())
            }
        } finally {
            CrashReportService.setReporter(originalReporter)
            throwOnShowError = originalThrowOnShowError
        }
    }

    private fun CardBrowserFragment.filteredDeckSearch(): String {
        showFilteredDeckScreen()

        val activity = shadowOf(requireActivity())
        advanceRobolectricLooperUntil { activity.peekNextStartedActivity() != null }
        val intent = requireNotNull(activity.nextStartedActivity)
        assertThat(
            intent.getStringExtra(SingleFragmentActivity.EXTRA_FRAGMENT_NAME),
            equalTo(FilteredDeckOptionsFragment::class.java.name),
        )
        val arguments = requireNotNull(intent.getBundleExtra(SingleFragmentActivity.EXTRA_FRAGMENT_ARGS))
        return requireNotNull(arguments.getString(FilteredDeckOptionsFragment.ARG_SEARCH))
    }
}
