// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.browser.CardBrowserFragment
import com.ichi2.anki.common.utils.dp
import com.ichi2.anki.settings.Prefs
import com.ichi2.testutils.dispatchInsets
import com.ichi2.testutils.withPref
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class CardBrowserSearchSideInsetsTest : RobolectricTest() {
    @Test
    fun `expanded search controls clear landscape system bars`() =
        runTest {
            withPref(Prefs::devUsingCardBrowserSearchView, true) {
                withCardBrowser(noteCount = 1) { browser ->
                    browser.dispatchInsets(navBarRight = 48.dp, cutoutLeft = 32.dp)
                    advanceRobolectricLooper()
                    val fragment = browser.supportFragmentManager.findFragmentById(R.id.card_browser_frame) as CardBrowserFragment
                    assertNotNull(fragment.searchView).show()
                    advanceRobolectricLooper()
                    val toggle = browser.findViewById<View>(R.id.toggle_advanced_search)
                    assertTrue(toggle.isShown, "The advanced search control must be visible")
                    val windowRight = browser.window.decorView.width
                    val safeRight = windowRight - 48.dp.toPx(targetContext)
                    val toggleRight = toggle.leftInWindow + toggle.width
                    assertTrue(
                        toggleRight <= safeRight,
                        "Search control ends at $toggleRight, beyond the system navigation edge at $safeRight",
                    )
                    val history = browser.findViewById<View>(R.id.search_history_label)
                    assertTrue(
                        history.leftInWindow + history.paddingLeft >= 32.dp.toPx(targetContext),
                        "Search history must clear the display cutout",
                    )
                }
            }
        }

    private val View.leftInWindow: Int
        get() = IntArray(2).also(::getLocationInWindow)[0]
}
