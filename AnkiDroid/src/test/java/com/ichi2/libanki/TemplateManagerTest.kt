/*
 *  Copyright (c) 2024 Brayan Oliveira <brayandso.dev@gmail.com>
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
package com.ichi2.libanki

import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

@Suppress("HtmlUnknownTarget", "HtmlRequiredAltAttribute")
class TemplateManagerTest {

    /***********************************************************************************************
     * [parseSourcesToFileScheme] tests
     **********************************************************************************************/

    @Test
    fun `parseSourcesToFileScheme - img`() {
        val mediaDir = "storage/emulated/0/AnkiDroid/collection.media"
        val result = parseSourcesToFileScheme("<img src=magenta.png>", mediaDir)
        assertEquals("""<img src="file:///$mediaDir/magenta.png">""", result)
    }

    @Test
    fun `parseSourcesToFileScheme - audio`() {
        val mediaDir = "storage/emulated/0/AnkiDroid/collection.media"
        val result = parseSourcesToFileScheme("<audio controls src=FOO_bar.mp3 style=\"visibility: hidden;\"></audio>", mediaDir)
        assertEquals("""<audio controls src="file:///$mediaDir/FOO_bar.mp3" style="visibility: hidden;"></audio>""", result)
    }

    @Test
    fun `parseSourcesToFileScheme - video`() {
        val mediaDir = "storage/emulated/0/AnkiDroid/collection.media"
        val result = parseSourcesToFileScheme("""<video src="figaro.mp4"></video>""", mediaDir)
        assertEquals("""<video src="file:///$mediaDir/figaro.mp4"></video>""", result)
    }

    @Test
    fun `parseSourcesToFileScheme - object`() {
        val mediaDir = "storage/emulated/0/AnkiDroid/collection.media"
        val result = parseSourcesToFileScheme("<object data=\"ben.avi\"></object>", mediaDir)
        assertEquals("""<object data="file:///$mediaDir/ben.avi"></object>""", result)
    }

    @Test
    fun `parseSourcesToFileScheme - source`() {
        val mediaDir = "storage/emulated/0/AnkiDroid/collection.media"

        @Language("HTML")
        val content = """<video width="320" height="240" controls><source src="AnkiDroid.mp4" type="video/mp4"></video>"""

        @Language("HTML")
        val expected = """<video width="320" height="240" controls><source src="file:///$mediaDir/AnkiDroid.mp4" type="video/mp4"></video>"""

        val result = parseSourcesToFileScheme(content, mediaDir)
        assertEquals(expected, result)
    }

    @Test
    fun `parseSourcesToFileScheme - uppercase tag and attribute`() {
        @Language("HTML")
        val content = "<IMG SRC=magenta.png>"

        @Language("HTML")
        val expectedResult = """<img src="file:///storage/emulated/0/AnkiDroid/collection.media/magenta.png">"""
        val result = parseSourcesToFileScheme(content, "storage/emulated/0/AnkiDroid/collection.media")
        assertEquals(expectedResult, result)
    }

    @Test
    fun `parseSourcesToFileScheme - invalid sources not parsed`() {
        val result = parseSourcesToFileScheme("<img src=\"m#gent%nki.png\">", "any_directory")
        assertEquals("""<img src="m#gent%nki.png">""", result)
    }

    @Test
    fun `parseSourcesToFileScheme - strings without img,audio,video and object aren't parsed`() {
        val result = parseSourcesToFileScheme("<p> foo</p>", "any_directory")
        assertEquals("<p> foo</p>", result)
    }

    @Test
    fun `parseSourcesToFileScheme - HTTP scheme isn't parsed`() {
        val result = parseSourcesToFileScheme("<audio src=\"http://ankidroid.org/secret.mp3\"></audio>", "any_directory")
        assertEquals("""<audio src="http://ankidroid.org/secret.mp3"></audio>""", result)
    }

    @Test
    fun `parseSourcesToFileScheme - File scheme isn't parsed`() {
        val result = parseSourcesToFileScheme("""<audio src="file:///storage/lies.m4a"></audio>""", "any_directory")
        assertEquals("""<audio src="file:///storage/lies.m4a"></audio>""", result)
    }

    @Test
    fun `parseSourcesToFileScheme - tags without a source aren't parsed`() {
        val result = parseSourcesToFileScheme("""<audio id="ben"></audio>""", "any_directory")
        assertEquals("""<audio id="ben"></audio>""", result)
    }

    @Test
    fun `parseSourcesToFileScheme - full directory`() {
        val result = parseSourcesToFileScheme("<img src=magenta.png>", "storage/emulated/0/AnkiDroid/collection.media")
        assertEquals("""<img src="file:///storage/emulated/0/AnkiDroid/collection.media/magenta.png">""", result)
    }

    @Test
    fun `parseSourcesToFileScheme - play directory`() {
        val result = parseSourcesToFileScheme("<img src=magenta.png>", "storage/emulated/0/Android/data/com.ichi2.anki/files/AnkiDroid/collection.media")
        assertEquals("""<img src="file:///storage/emulated/0/Android/data/com.ichi2.anki/files/AnkiDroid/collection.media/magenta.png">""", result)
    }

    @Test
    fun `parseSourcesToFileScheme - full directory with slash at the beginning and end`() {
        val result = parseSourcesToFileScheme("<img src=magenta.png>", "/storage/emulated/0/AnkiDroid/collection.media/")
        assertEquals("""<img src="file:///storage/emulated/0/AnkiDroid/collection.media/magenta.png">""", result)
    }

    @Test
    fun `parseSourcesToFileScheme - path with special characters`() {
        val result = parseSourcesToFileScheme("<img src=magenta.png>", "storage/emulated/0/AnkiDroid@#$%/collection.media")
        assertEquals("""<img src="file:///storage/emulated/0/AnkiDroid@%23$%25/collection.media/magenta.png">""", result)
    }
}
