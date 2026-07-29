// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Tim Farrelly <timf34@gmail.com>

package com.ichi2.anki.blocker

import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.nullValue
import org.junit.Test

class SupportedBrowsersTest {
    @Test
    fun `bare hosts and full urls parse to the host`() {
        assertThat(SupportedBrowsers.parseHost("x.com"), equalTo("x.com"))
        assertThat(SupportedBrowsers.parseHost("x.com/home"), equalTo("x.com"))
        assertThat(SupportedBrowsers.parseHost("https://x.com/home?tab=1#top"), equalTo("x.com"))
        assertThat(SupportedBrowsers.parseHost("http://example.org:8080/path"), equalTo("example.org"))
    }

    @Test
    fun `www prefix and case are normalized`() {
        assertThat(SupportedBrowsers.parseHost("https://WWW.X.com"), equalTo("x.com"))
        assertThat(SupportedBrowsers.parseHost("Instagram.COM"), equalTo("instagram.com"))
    }

    @Test
    fun `placeholder text and searches are rejected`() {
        assertThat(SupportedBrowsers.parseHost("Search or type web address"), nullValue())
        assertThat(SupportedBrowsers.parseHost("how to cook rice"), nullValue())
        assertThat(SupportedBrowsers.parseHost(""), nullValue())
        assertThat(SupportedBrowsers.parseHost(null), nullValue())
        assertThat(SupportedBrowsers.parseHost("localhost"), nullValue())
    }

    @Test
    fun `ip hosts parse`() {
        assertThat(SupportedBrowsers.parseHost("192.168.0.1/admin"), equalTo("192.168.0.1"))
    }

    @Test
    fun `browser lookup by package`() {
        assertThat(SupportedBrowsers.byPackage("com.android.chrome")?.packageName, equalTo("com.android.chrome"))
        assertThat(SupportedBrowsers.byPackage("com.random.app"), nullValue())
        assertThat(SupportedBrowsers.byPackage(null), nullValue())
    }
}
