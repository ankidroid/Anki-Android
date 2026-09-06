// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.pages

import androidx.core.view.isVisible
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.SingleFragmentActivity
import com.ichi2.anki.libanki.Consts
import com.ichi2.anki.settings.Prefs
import com.ichi2.testutils.ext.clear
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.equalTo
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

/** Test for [DeckOptions] */
@RunWith(AndroidJUnit4::class)
class DeckOptionsTest : RobolectricTest() {
    @After
    override fun tearDown() {
        super.tearDown()
        Prefs.clear()
    }

    @Test
    fun `double tap interval is sent to the page when it is ready`() {
        Prefs.putInt(R.string.double_tap_timeout_pref_key, 800)

        withDeckOptions {
            onWebViewReady()

            assertThat(lastEvaluatedJavascript, containsString("setParameterUnlockClickTimeoutMs"))
            assertThat(lastEvaluatedJavascript, containsString("800"))
        }
    }

    @Test
    fun `loading state is restored when the WebView is recreated`() {
        withDeckOptions {
            onWebViewReady()
            assertThat("WebView is shown when ready", webViewLayout.isVisible, equalTo(true))
            assertThat("loading indicator is hidden when ready", pageLoadingIndicator.isVisible, equalTo(false))

            onWebViewRecreated(webViewLayout.webView)

            assertThat("WebView is hidden while the page reloads", webViewLayout.isVisible, equalTo(false))
            assertThat("loading indicator is shown while the page reloads", pageLoadingIndicator.isVisible, equalTo(true))

            onWebViewReady()

            assertThat("WebView is shown when the reloaded page is ready", webViewLayout.isVisible, equalTo(true))
            assertThat("loading indicator is hidden when the reloaded page is ready", pageLoadingIndicator.isVisible, equalTo(false))
        }
    }

    private fun withDeckOptions(block: DeckOptions.() -> Unit) {
        val activity =
            startActivityNormallyOpenCollectionWithIntent(
                SingleFragmentActivity::class.java,
                DeckOptions.getIntent(targetContext, Consts.DEFAULT_DECK_ID),
            )
        advanceRobolectricLooper()
        block(activity.fragment as DeckOptions)
    }

    /** The last JavaScript evaluated by the WebView of [DeckOptions] */
    private val DeckOptions.lastEvaluatedJavascript: String?
        get() = shadowOf(webViewLayout.webView).lastEvaluatedJavascript
}
