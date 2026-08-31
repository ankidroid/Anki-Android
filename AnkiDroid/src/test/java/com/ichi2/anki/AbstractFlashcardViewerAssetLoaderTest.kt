/*
 *  Copyright (c) 2026 Anki-Android Contributors
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

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.notNullValue
import org.hamcrest.Matchers.nullValue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File

/**
 * Exercises [AbstractFlashcardViewer.loadMediaFromAssetLoader], the production
 * WebViewAssetLoader path handler (this branch has no ViewerResourceHandler).
 *
 * Fail-without-fix: if the production guard is replaced with `File(mediaDir, path)`,
 * traversal to a sibling secret file would return a non-null response.
 */
@RunWith(AndroidJUnit4::class)
class AbstractFlashcardViewerAssetLoaderTest {

    @get:Rule
    var temporaryDirectory = TemporaryFolder()

    @Test
    fun pathTraversal_siblingSecret_isBlocked() {
        val mediaDir = temporaryDirectory.newFolder("media")
        val secret = File(temporaryDirectory.root, "secret.txt")
        secret.writeText("secret")

        val response = AbstractFlashcardViewer.loadMediaFromAssetLoader(mediaDir, "../secret.txt")

        assertThat(
            "Production asset loader must not serve files outside mediaDir",
            response,
            nullValue()
        )
    }

    @Test
    fun pathTraversal_absolute_dotDotSlash_isBlocked() {
        val mediaDir = temporaryDirectory.newFolder("media")
        val response = AbstractFlashcardViewer.loadMediaFromAssetLoader(mediaDir, "/../../../etc/hosts")
        assertThat(response, nullValue())
    }

    @Test
    fun pathTraversal_deep_dotDotSlash_isBlocked() {
        val mediaDir = temporaryDirectory.newFolder("media")
        val response = AbstractFlashcardViewer.loadMediaFromAssetLoader(mediaDir, "../../../../etc/passwd")
        assertThat(response, nullValue())
    }

    @Test
    fun legitimateFileUnderMedia_isAllowed() {
        val mediaDir = temporaryDirectory.newFolder("media")
        val testFile = File(mediaDir, "sound.mp3")
        testFile.writeBytes(byteArrayOf(1, 2, 3, 4))

        val response = AbstractFlashcardViewer.loadMediaFromAssetLoader(mediaDir, "sound.mp3")

        assertThat(
            "Legitimate files under the media directory must be served",
            response,
            notNullValue()
        )
    }

    @Test
    fun legitimateNestedFileUnderMedia_isAllowed() {
        val mediaDir = temporaryDirectory.newFolder("media")
        val subDir = File(mediaDir, "subdir")
        subDir.mkdirs()
        File(subDir, "nested.mp3").writeBytes(byteArrayOf(5, 6, 7, 8))

        val response = AbstractFlashcardViewer.loadMediaFromAssetLoader(mediaDir, "subdir/nested.mp3")

        assertThat(response, notNullValue())
    }
}
