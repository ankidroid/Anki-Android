/*
 *  Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>
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
package com.ichi2.anki.multimediacard.fields

import androidx.annotation.CheckResult
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.libanki.Collection
import com.ichi2.libanki.Media
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.kotlin.whenever
import java.io.File

@RunWith(AndroidJUnit4::class)
class ImageFieldTest {
    /** #5237 - quotation marks on Android differed from Windows  */
    @Test
    fun imageValueIsConsistentWithAnkiDesktop() {
        // Arrange
        val mockedFile = mock(File::class.java)
        whenever(mockedFile.exists()).thenReturn(true)
        whenever(mockedFile.name).thenReturn("paste-abc.jpg")

        // Act
        val actual = ImageField.formatImageFileName(mockedFile)

        // Assert
        // This differs between AnkDesktop Version 2.0.51 and 2.1.22
        // 2.0:  "<img src=\"paste-abc.jpg\" />";
        // 2.1: (note: no trailing slash or space)
        val expected = "<img src=\"paste-abc.jpg\">"
        assertThat(actual, equalTo(expected))
    }

    @Test
    fun validImageParses() {
        val goodImage = "<img src='img_202003291657428441724378214970132.png'/>"
        val imageSrc = ImageField.parseImageSrcFromHtml(goodImage)
        assertThat(imageSrc, equalTo("img_202003291657428441724378214970132.png"))
    }

    @Test
    fun testImageSubstringParsing() {
        // 5874 - previously failed
        val previouslyBadImage = "<img src='img_202003291657428441724378214970132.png'/>aaaa'/>"
        val imageSrc = ImageField.parseImageSrcFromHtml(previouslyBadImage)
        assertThat(imageSrc, equalTo("img_202003291657428441724378214970132.png"))
    }

    @Test
    fun firstImageIsSelected() {
        val goodImage = "<img src='1.png'/>aa<img src='2.png'/>"
        val imageSrc = ImageField.parseImageSrcFromHtml(goodImage)
        assertThat(imageSrc, equalTo("1.png"))
    }

    @Test
    fun testNoImage() {
        val knownBadImage = "<br />"
        val imageSrc = ImageField.parseImageSrcFromHtml(knownBadImage)
        assertThat(imageSrc, equalTo(""))
    }

    @Test
    fun testEmptyImage() {
        val knownBadImage = "<img />"
        val imageSrc = ImageField.parseImageSrcFromHtml(knownBadImage)
        assertThat(imageSrc, equalTo(""))
    }

    @Test
    fun testNoImagePathIsNothing() {
        val knownBadImage = "<br />"
        val col = collectionWithMediaDirectory("media")
        val imageSrc = ImageField.getImageFullPath(col, knownBadImage)
        assertThat("no media should return no paths", imageSrc, equalTo(""))
    }

    @Test
    fun testNoImagePathConcat() {
        val goodImage = "<img src='1.png'/>"
        val col = collectionWithMediaDirectory("media")
        val imageSrc = ImageField.getImageFullPath(col, goodImage)
        assertThat("Valid media should have path", imageSrc, equalTo("media/1.png"))
    }

    @CheckResult
    private fun collectionWithMediaDirectory(dir: String): Collection {
        val media = mock(Media::class.java)
        whenever(media.dir).thenReturn(dir)

        val collectionMock = mock(Collection::class.java)
        whenever(collectionMock.media).thenReturn(media)
        return collectionMock
    }
}
