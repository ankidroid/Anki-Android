/*
 *  Copyright (c) 2026 AnkiDroid Contributors
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
package com.ichi2.anki

import android.content.Intent
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.utils.AdaptionUtil
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.notNullValue
import org.hamcrest.Matchers.nullValue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
class InfoDonateUrlPolicyTest : RobolectricTest() {

    @Test
    fun playBlocksOpenCollectiveHostFromParsedUris() {
        assertThat(
            Info.shouldBlockChangelogDonateNavigation(
                showDonateLinks = false,
                host = Uri.parse("https://opencollective.com/ankidroid").host
            ),
            equalTo(true)
        )
        assertThat(
            Info.shouldBlockChangelogDonateNavigation(
                showDonateLinks = false,
                host = Uri.parse("http://opencollective.com/ankidroid").host
            ),
            equalTo(true)
        )
        assertThat(
            Info.shouldBlockChangelogDonateNavigation(
                showDonateLinks = false,
                host = Uri.parse("https://www.opencollective.com/ankidroid").host
            ),
            equalTo(true)
        )
    }

    @Test
    fun playDoesNotBlockOtherHostsFromParsedUris() {
        assertThat(
            Info.shouldBlockChangelogDonateNavigation(
                showDonateLinks = false,
                host = Uri.parse("https://docs.ankidroid.org/changelog.html").host
            ),
            equalTo(false)
        )
        assertThat(
            Info.shouldBlockChangelogDonateNavigation(
                showDonateLinks = false,
                host = Uri.parse("file:///android_asset/changelog.html").host
            ),
            equalTo(false)
        )
        assertThat(
            Info.shouldBlockChangelogDonateNavigation(showDonateLinks = false, host = null),
            equalTo(false)
        )
    }

    @Test
    fun flavorsThatShowDonateDoNotBlockParsedOpenCollective() {
        assertThat(
            Info.shouldBlockChangelogDonateNavigation(
                showDonateLinks = true,
                host = Uri.parse("https://opencollective.com/ankidroid").host
            ),
            equalTo(false)
        )
    }

    @Test
    @Suppress("DEPRECATION")
    fun bothWebViewClientOverloadsHonorDonateBlock() {
        val info = launchInfo()
        val webView = info.findViewById<WebView>(R.id.info)
        val client = info.ChangelogWebViewClient()
        val ocUrl = "https://opencollective.com/ankidroid"
        mockkObject(AdaptionUtil)
        try {
            every { AdaptionUtil.hasWebBrowser(any()) } returns true

            val stringOverridden = client.shouldOverrideUrlLoading(webView, ocUrl)
            assertThat(stringOverridden, equalTo(true))
            assertOpenCollectiveBrowserLaunch(info)

            val requestOverridden = client.shouldOverrideUrlLoading(webView, webResourceRequest(ocUrl))
            assertThat(requestOverridden, equalTo(true))
            assertOpenCollectiveBrowserLaunch(info)
        } finally {
            unmockkObject(AdaptionUtil)
        }
    }

    @Test
    @Suppress("DEPRECATION")
    fun bothOverloadsAllowChangelogUrlToLoadInWebView() {
        val info = launchInfo()
        val webView = info.findViewById<WebView>(R.id.info)
        val client = info.ChangelogWebViewClient()
        val changelog = "https://docs.ankidroid.org/changelog.html"

        assertThat(client.shouldOverrideUrlLoading(webView, changelog), equalTo(false))
        assertThat(shadowOf(info).peekNextStartedActivity(), nullValue())

        assertThat(
            client.shouldOverrideUrlLoading(webView, webResourceRequest(changelog)),
            equalTo(false)
        )
        assertThat(shadowOf(info).peekNextStartedActivity(), nullValue())
    }

    private fun assertOpenCollectiveBrowserLaunch(info: Info) {
        val started = shadowOf(info).nextStartedActivity
        if (BuildConfig.SHOW_DONATE_LINKS) {
            assertThat(started, notNullValue())
            assertThat(started.action, equalTo(Intent.ACTION_VIEW))
            assertThat(started.data!!.host!!.contains("opencollective.com"), equalTo(true))
        } else {
            assertThat(
                "Play must not start a browser for Open Collective",
                started,
                nullValue()
            )
        }
    }

    private fun launchInfo(): Info {
        val intent = Intent().putExtra(Info.TYPE_EXTRA, Info.TYPE_NEW_VERSION)
        return startActivityNormallyOpenCollectionWithIntent(Info::class.java, intent)
    }

    private fun webResourceRequest(url: String): WebResourceRequest {
        return object : WebResourceRequest {
            override fun getUrl(): Uri = Uri.parse(url)
            override fun isForMainFrame(): Boolean = true
            override fun isRedirect(): Boolean = false
            override fun hasGesture(): Boolean = false
            override fun getMethod(): String = "GET"
            override fun getRequestHeaders(): Map<String, String> = emptyMap()
        }
    }
}
