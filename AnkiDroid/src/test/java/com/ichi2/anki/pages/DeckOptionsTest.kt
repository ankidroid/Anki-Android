// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.pages

import android.webkit.WebView
import androidx.core.view.children
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.SingleFragmentActivity
import com.ichi2.anki.libanki.Consts
import com.ichi2.anki.settings.Prefs
import com.ichi2.testutils.ext.clear
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.containsString
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
        get() =
            webViewLayout.children
                .filterIsInstance<WebView>()
                .single()
                .let { shadowOf(it).lastEvaluatedJavascript }
}
