/*
 *  Copyright (c) 2024 Mike Hardy <github@mikehardy.net>
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
package com.ichi2.utils

import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.core.IsEqual.equalTo
import org.junit.Test

class WebViewUtilsTest {

    @Test
    fun testWebviewVersionCodes() {
        assertThat(
            "Known old webview determined correctly",
            checkWebViewVersionComponents("com.google.android.webview", "53.0.2785.124", OLDEST_WORKING_WEBVIEW_VERSION_CODE - 1),
            equalTo(53)
        )

        assertThat(
            "Known good webview determined correctly",
            checkWebViewVersionComponents("com.google.android.webview", "131.0.6778.39", 677803933L),
            equalTo(null)
        )

        assertThat(
            "Known confusing webview determined incorrectly",
            checkWebViewVersionComponents("com.google.android.webview", "130.0.0.1", OLDEST_WORKING_WEBVIEW_VERSION_CODE - 1),
            equalTo(130)
        )
    }
}
