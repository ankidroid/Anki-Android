// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.workarounds

import android.webkit.WebView
import androidx.core.os.bundleOf
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.multimedia.MultimediaActivity.Companion.EXTRA_MEDIA_OPTIONS
import com.ichi2.anki.multimedia.MultimediaImageFragment
import com.ichi2.testutils.launchFragmentInContainer
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.sameInstance
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

/**
 * [SafeWebViewLayout.onRenderProcessGone] requires API 26+; Robolectric's default SDK (targetSdk) satisfies that.
 */
@RunWith(AndroidJUnit4::class)
class SafeWebViewLayoutTest : RobolectricTest() {
    @Test
    fun `onRenderProcessGone with stale WebView destroys stale instance only`() {
        withMultimediaWebView { layout, fragment ->
            val currentWebView = layout.getChildAt(0) as WebView
            val staleWebView = WebView(fragment.requireContext())

            layout.onRenderProcessGone(staleWebView)

            assertThat(
                "stale WebView is destroyed",
                shadowOf(staleWebView).wasDestroyCalled(),
                equalTo(true),
            )
            assertThat(
                "current WebView is still attached",
                layout.getChildAt(0),
                sameInstance(currentWebView),
            )
            assertThat("current WebView is not destroyed", shadowOf(currentWebView).wasDestroyCalled(), equalTo(false))
        }
    }

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

    private fun withMultimediaWebView(block: (SafeWebViewLayout, MultimediaImageFragment) -> Unit) {
        launchFragmentInContainer<MultimediaImageFragment>(
            bundleOf(EXTRA_MEDIA_OPTIONS to MultimediaImageFragment.ImageOptions.GALLERY),
        ).use { scenario ->
            scenario.onFragment { fragment ->
                block(fragment.binding.multimediaWebView, fragment)
            }
        }
    }
}
