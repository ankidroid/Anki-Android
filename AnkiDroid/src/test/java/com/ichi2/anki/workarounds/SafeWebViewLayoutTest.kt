// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.workarounds

import android.webkit.WebView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

/**
 * [SafeWebViewLayout.onRenderProcessGone] requires API 26+; Robolectric's default SDK (targetSdk) satisfies that.
 */
@RunWith(AndroidJUnit4::class)
class SafeWebViewLayoutTest : RobolectricTest() {
    @Test
    fun `destroy then safeDestroy after early-return onRenderProcessGone do not destroy WebView twice`() {
        val layout = DestroyCountingSafeWebViewLayout(targetContext)
        val webView = layout.getChildAt(0) as WebView

        layout.onRenderProcessGone(webView)
        assertThat(shadowOf(webView).wasDestroyCalled(), equalTo(true))
        assertThat(layout.webViewDestroyCallCount, equalTo(1))

        layout.destroy()
        layout.safeDestroy()

        assertThat(
            "destroy then safeDestroy do not invoke WebView.destroy again",
            layout.webViewDestroyCallCount,
            equalTo(1),
        )
    }

    @Test
    fun `safeDestroy then destroy after early-return onRenderProcessGone do not destroy WebView twice`() {
        val layout = DestroyCountingSafeWebViewLayout(targetContext)
        val webView = layout.getChildAt(0) as WebView

        layout.onRenderProcessGone(webView)
        assertThat(layout.webViewDestroyCallCount, equalTo(1))

        layout.safeDestroy()
        layout.destroy()

        assertThat(
            "safeDestroy then destroy do not invoke WebView.destroy again",
            layout.webViewDestroyCallCount,
            equalTo(1),
        )
    }
}
