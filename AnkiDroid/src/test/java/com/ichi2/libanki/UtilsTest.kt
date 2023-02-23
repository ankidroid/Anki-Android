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
import org.apache.commons.compress.archivers.zip.ZipFile
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException
import java.lang.Exception
import java.util.*
import kotlin.Throws

@RunWith(AndroidJUnit4::class)
class UtilsTest {
    @Test
    fun testZipWithPathTraversal() {
        val classLoader = javaClass.classLoader
        val resource = classLoader!!.getResource("path-traversal.zip")
        try {
            val file = File(resource.toURI())
            val zipFile = ZipFile(file)
            val zipEntries = zipFile.entries
            while (zipEntries.hasMoreElements()) {
                val ze2 = zipEntries.nextElement()
                Utils.unzipFiles(zipFile, "/tmp", arrayOf(ze2.name), null)
            }
            Assert.fail("Expected an IOException")
        } catch (e: Exception) {
            assertEquals("File is outside extraction target directory.", e.message)
        }
    }

    @Test
    fun testInvalidPaths() {
        try {
            val tmpDir = File("/tmp")
            Assert.assertFalse(Utils.isInside(File(tmpDir, "../foo"), tmpDir))
            Assert.assertFalse(Utils.isInside(File(tmpDir, "/tmp/one/../../../foo"), tmpDir))
        } catch (ioe: IOException) {
            Assert.fail("Unexpected exception: $ioe")
        }
    }

    @Test
    fun testValidPaths() {
        try {
            val tmpDir = File("/tmp")
            Assert.assertTrue(Utils.isInside(File(tmpDir, "test/file/path/no/parent"), tmpDir))
            Assert.assertTrue(Utils.isInside(File(tmpDir, "/tmp/absolute/path"), tmpDir))
            Assert.assertTrue(Utils.isInside(File(tmpDir, "test/file/../../"), tmpDir))
        } catch (ioe: IOException) {
            Assert.fail("Unexpected exception: $ioe")
        }
    }

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
    fun nonEmptyFieldsTest() {
        val m: MutableMap<String, String> = HashMap()
        val s: MutableSet<String> = HashSet()
        assertEquals(s, Utils.nonEmptyFields(m))
        m["baz"] = ""
        assertEquals(s, Utils.nonEmptyFields(m))
        m["foo"] = "   "
        assertEquals(s, Utils.nonEmptyFields(m))
        m["bar"] = " plop  "
        s.add("bar")
        assertEquals(s, Utils.nonEmptyFields(m))
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
