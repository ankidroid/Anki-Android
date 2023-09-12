/****************************************************************************************
 * Copyright (c) 2018 Mike Hardy <mike@mikehardy.net>                                   *
 *                                                                                      *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 *                                                                                      *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/

package com.ichi2.libanki

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.TestUtils
import com.ichi2.utils.FileOperation.Companion.getFileResource
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.lang.Exception
import java.util.*
import kotlin.Throws

@RunWith(AndroidJUnit4::class)
class UtilsTest {
    @Test
    @Throws(Exception::class)
    fun testCopyFile() {
        val resourcePath = getFileResource("path-traversal.zip")
        val copy = File.createTempFile("testCopyFileToStream", ".zip")
        copy.deleteOnExit()
        Utils.copyFile(File(resourcePath), copy)
        assertEquals(TestUtils.getMD5(resourcePath), TestUtils.getMD5(copy.canonicalPath))
    }

    @Test
    fun testSplit() {
        Assert.assertArrayEquals(arrayOf("foo", "bar"), Utils.splitFields("foobar"))
        Assert.assertArrayEquals(arrayOf("", "foo", "", "", ""), Utils.splitFields("foo"))
    }

    @Test
    fun test_stripHTML_will_remove_tags() {
        val strings = listOf(
            "<>",
            "<1>",
            "<foo>",
            "<\n>",
            "<\\qwq>",
            "<aa\nsd\nas\n?\n>"
        )
        for (s in strings) {
            assertEquals(
                s.replace("\n", "\\n") + " should be removed.",
                "",
                Utils.stripHTML(s)
            )
        }
    }

    @Test
    fun test_stripHTML_will_remove_comments() {
        val strings = listOf(
            "<!---->",
            "<!--dd-->",
            "<!--asd asd asd-->",
            "<!--\n-->",
            "<!--\nsd-->",
            "<!--lkl\nklk\n-->"
        )
        for (s in strings) {
            assertEquals(
                s.replace("\n", "\\n") + " should be removed.",
                "",
                Utils.stripHTML(s)
            )
        }
    }
}
