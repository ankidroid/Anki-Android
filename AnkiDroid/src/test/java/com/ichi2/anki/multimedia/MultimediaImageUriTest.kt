/*
 * Copyright (c) 2026 Ashish Yadav <mailtoashish693@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.multimedia

import android.net.Uri
import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.nullValue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream

@RunWith(AndroidJUnit4::class)
class MultimediaImageUriTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `file URI is resolved directly without invoking the internalizer`() {
        val onDisk = tempFolder.newFile("photo.jpg")
        var internalizerCalls = 0

        val resolved =
            resolveFileFromUri(Uri.fromFile(onDisk)) {
                internalizerCalls++
                null
            }

        assertThat(resolved?.absolutePath, equalTo(onDisk.absolutePath))
        assertThat(internalizerCalls, equalTo(0))
    }

    @Test
    fun `content URI is delegated to the internalizer`() {
        val cached = tempFolder.newFile("cached.jpg")

        val resolved = resolveFileFromUri("content://media/external/images/1".toUri()) { cached }

        assertThat(resolved, equalTo(cached))
    }

    @Test
    fun `content URI returns null when the internalizer returns null`() {
        val resolved = resolveFileFromUri("content://media/external/images/1".toUri()) { null }
        assertThat(resolved, nullValue())
    }

    @Test
    fun `file URI without a path returns null without invoking the internalizer`() {
        val pathless = Uri.Builder().scheme("file").build()
        var internalizerCalls = 0

        val resolved =
            resolveFileFromUri(pathless) {
                internalizerCalls++
                null
            }

        assertThat(resolved, nullValue())
        assertThat(internalizerCalls, equalTo(0))
    }

    @Test
    fun `file URI is opened directly without invoking the fallback`() {
        val onDisk = tempFolder.newFile("svg.svg").apply { writeText("<svg/>") }
        var fallbackCalls = 0

        val stream =
            openImageInputStream(Uri.fromFile(onDisk)) {
                fallbackCalls++
                null
            }

        assertThat(stream?.use { it.bufferedReader().readText() }, equalTo("<svg/>"))
        assertThat(fallbackCalls, equalTo(0))
    }

    @Test
    fun `content URI is delegated to the fallback`() {
        val payload: InputStream = ByteArrayInputStream("<svg/>".toByteArray())

        val stream = openImageInputStream("content://media/external/images/1".toUri()) { payload }

        assertThat(stream, equalTo(payload))
    }

    @Test
    fun `content URI returns null when the fallback returns null`() {
        val stream = openImageInputStream("content://media/external/images/1".toUri()) { null }
        assertThat(stream, nullValue())
    }
}
