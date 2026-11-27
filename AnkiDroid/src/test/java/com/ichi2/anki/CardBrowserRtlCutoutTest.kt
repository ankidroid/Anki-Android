// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.common.utils.dp
import com.ichi2.testutils.dispatchInsets
import com.ichi2.testutils.withSplitPaneUi
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.lessThan
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CardBrowserRtlCutoutTest : RobolectricTest() {
    @Test
    fun `rtl split editor keeps fields outside a left cutout`() =
        withSplitPaneUi {
            ensureCollectionLoadIsSynchronous()
            setCardBrowserFragmented(true)
            val browser = getBrowserWithNotes(1)
            browser.window.decorView.layoutDirection = View.LAYOUT_DIRECTION_RTL
            browser.viewModel.setQuery("deck:Default", forceRefresh = true)
            browser.waitForSearchResults()
            advanceRobolectricLooper()
            val editor = requireNotNull(browser.fragment)
            advanceRobolectricLooper()
            browser.dispatchInsets(cutoutLeft = 100.dp)
            val content = browser.window.decorView
            content.measure(
                View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(2400, View.MeasureSpec.EXACTLY),
            )
            content.layout(0, 0, 1080, 2400)
            advanceRobolectricLooper()

            val field = requireNotNull(editor.editFieldAt(0))
            val origin = IntArray(2)
            field.getLocationOnScreen(origin)
            assertThat("the field has a measured width", field.width > 0, equalTo(true))
            assertThat("the editor uses RTL layout", editor.requireView().layoutDirection, equalTo(View.LAYOUT_DIRECTION_RTL))
            assertThat("the field is in the left pane", origin[0], lessThan(540))
            assertThat("the field at ${origin[0]} clears the cutout", origin[0] >= 100.dp.toPx(browser), equalTo(true))
        }
}
