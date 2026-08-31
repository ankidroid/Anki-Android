/*
 *  Copyright (c) 2026 Mike Hardy <github@mikehardy.net>
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

import android.net.Uri
import android.webkit.WebResourceRequest
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.testutils.EmptyApplication
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.notNullValue
import org.hamcrest.Matchers.nullValue
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

@RunWith(AndroidJUnit4::class)
@Config(application = EmptyApplication::class)
class ViewerResourceHandlerPathTraversalTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var handler: ViewerResourceHandler
    private lateinit var mediaDir: File

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<EmptyApplication>()
        val ankiDir = temporaryFolder.newFolder("AnkiDroid")
        CollectionHelper.ankiDroidDirectoryOverride = ankiDir.absolutePath
        mediaDir = CollectionHelper.getMediaDirectory(context)
        mediaDir.mkdirs()
        handler = ViewerResourceHandler(context)
    }

    @After
    fun tearDown() {
        CollectionHelper.ankiDroidDirectoryOverride = null
    }

    @Test
    fun pathTraversal_dotDotSlash_isBlocked() {
        val outside = File(mediaDir.parentFile, "secret-dotdot.txt").apply {
            writeText("should-not-be-served")
        }
        val request = mockRequest("/../${outside.name}")
        val response = handler.shouldInterceptRequest(request)
        assertThat(response, nullValue())
    }

    @Test
    fun pathTraversal_percentEncodedSlash_isBlocked() {
        val outside = File(mediaDir.parentFile, "secret-encoded.txt").apply {
            writeText("should-not-be-served")
        }
        val request = mockRequest("/..%2f${outside.name}")
        val response = handler.shouldInterceptRequest(request)
        assertThat(
            "Requesting a file outside mediaDir via ..%2f must be blocked",
            response,
            nullValue()
        )
    }

    @Test
    fun pathTraversal_mixedEncoding_isBlocked() {
        val request = mockRequest("/..%2F../%2e%2e/etc/passwd")
        val response = handler.shouldInterceptRequest(request)
        assertThat(response, nullValue())
    }

    @Test
    fun normalMediaFile_isServed() {
        val mediaFile = File(mediaDir, "sound.mp3").apply {
            writeBytes(byteArrayOf(1, 2, 3, 4))
        }
        val request = mockRequest("/${mediaFile.name}")
        val response = handler.shouldInterceptRequest(request)
        assertThat(
            "A file that exists in mediaDir must be served (non-null)",
            response,
            notNullValue()
        )
    }

    @Test
    fun pathTraversal_doubleEncoded_isBlocked() {
        val request = mockRequest("/..%252f..%252f..%252fetc/hosts")
        val response = handler.shouldInterceptRequest(request)
        assertThat(response, nullValue())
    }

    @Test
    fun pathTraversal_backslash_isBlocked() {
        val request = mockRequest("/..\\..\\..\\etc\\hosts")
        val response = handler.shouldInterceptRequest(request)
        assertThat(response, nullValue())
    }

    private fun mockRequest(path: String): WebResourceRequest {
        val url = "http://127.0.0.1:40001$path"
        return object : WebResourceRequest {
            override fun getUrl(): Uri = Uri.parse(url)
            override fun isForMainFrame(): Boolean = false
            override fun isRedirect(): Boolean = false
            override fun hasGesture(): Boolean = false
            override fun getMethod(): String? = "GET"
            override fun getRequestHeaders(): Map<String, String>? = emptyMap()
        }
    }
}
