// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.pages

import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@RunWith(AndroidJUnit4::class)
class PageWebViewClientAssetTest : PageWebViewClientTestBase() {
    @Test
    fun `only the page server can serve bundled pages and assets`() =
        withStatistics { view, client ->
            val pageUrl = assertNotNull(view.url).toUri()
            val assetUrl = pageUrl.buildUpon().path("/_app/env.js").build()
            for (url in listOf(pageUrl, assetUrl)) {
                assertNotNull(client.shouldInterceptRequest(view, request(url.toString())), url.toString()).data.close()

                val otherOrigins =
                    listOf(
                        url.buildUpon().scheme("https").build(),
                        url.buildUpon().encodedAuthority("127.0.0.1:${url.port + 1}").build(),
                        url.buildUpon().encodedAuthority("example.org:${url.port}").build(),
                        url.buildUpon().encodedAuthority("user@${url.encodedAuthority}").build(),
                    )
                for (other in otherOrigins) {
                    assertNull(client.shouldInterceptRequest(view, request(other.toString())), other.toString())
                }
            }
        }
}
