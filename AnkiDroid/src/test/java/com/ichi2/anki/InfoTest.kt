// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.content.Intent
import android.os.Build
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.not
import org.hamcrest.Matchers.sameInstance
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.O)
class InfoTest : RobolectricTest() {
    @Test
    fun onRenderProcessGoneRecoversByRecreatingWebViewWithoutKillingActivity() {
        val activity =
            startActivityNormallyOpenCollectionWithIntent(
                Info::class.java,
                Intent(targetContext, Info::class.java),
            )
        val initialWebView = activity.findViewById<WebView>(R.id.web_view)
        val webViewClient = shadowOf(initialWebView).webViewClient
        val detail = mock(RenderProcessGoneDetail::class.java)

        val handled = webViewClient.onRenderProcessGone(initialWebView, detail)

        assertThat(handled, equalTo(true))
        assertThat(shadowOf(initialWebView).wasDestroyCalled(), equalTo(true))
        assertThat(activity.isFinishing, equalTo(false))

        val recoveredWebView = activity.findViewById<WebView>(R.id.web_view)
        assertThat(recoveredWebView, not(sameInstance(initialWebView)))
        assertThat(shadowOf(recoveredWebView).wasDestroyCalled(), equalTo(false))
    }
}
