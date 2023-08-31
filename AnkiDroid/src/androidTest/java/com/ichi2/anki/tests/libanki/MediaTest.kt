/****************************************************************************************
 * Copyright (c) 2014 Houssam Salem <houssam.salem.au@gmail.com>                        *
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
package com.ichi2.anki.tests.libanki

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.BackupManager
import com.ichi2.anki.tests.InstrumentedTest
import com.ichi2.anki.testutil.GrantStoragePermission
import com.ichi2.libanki.Collection
import com.ichi2.libanki.Media
import com.ichi2.libanki.exception.EmptyMediaException
import net.ankiweb.rsdroid.BackendFactory.defaultLegacySchema
import org.hamcrest.MatcherAssert
import org.hamcrest.Matchers
import org.junit.*
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Unit tests for [Media].
 */
@RunWith(AndroidJUnit4::class)
class MediaTest : InstrumentedTest() {
    private var mTestCol: Collection? = null

    @get:Rule
    var runtimePermissionRule = GrantStoragePermission.instance

    @Before
    @Throws(IOException::class)
    fun setUp() {
        mTestCol = emptyCol
    }

    @After
    fun tearDown() {
        mTestCol!!.close()
    }

    @Test
    @Throws(IOException::class, EmptyMediaException::class)
    fun testAdd() {
        // open new empty collection
        val dir = testDir
        BackupManager.removeDir(dir)
        assertTrue(dir.mkdirs())
        val path = File(dir, "foo.jpg")
        var os = FileOutputStream(path, false)
        os.write("hello".toByteArray())
        os.close()
        // new file, should preserve name
        val r = mTestCol!!.media.addFile(path)
        assertEquals("foo.jpg", r)
        // adding the same file again should not create a duplicate
        assertEquals("foo.jpg", mTestCol!!.media.addFile(path))
        // but if it has a different md5, it should
        os = FileOutputStream(path, false)
        os.write("world".toByteArray())
        os.close()
        assertNotEquals("foo.jpg", mTestCol!!.media.addFile(path))
    }

    @Test
    @Throws(IOException::class)
    fun testAddEmptyFails() {
        // open new empty collection
        val dir = testDir
        BackupManager.removeDir(dir)
        assertTrue(dir.mkdirs())
        val path = File(dir, "foo.jpg")
        assertTrue(path.createNewFile())

        // new file, should preserve name
        try {
            mTestCol!!.media.addFile(path)
            fail("exception should be thrown")
        } catch (mediaException: EmptyMediaException) {
            // all good
        }
    }

    @Test
    @Suppress("SpellCheckingInspection")
    fun testStrings() {
        val mid = mTestCol!!.models.getModels().entries.iterator().next().key

        var expected: List<String?> = emptyList<String>()
        var actual = mTestCol!!.media.filesInStr(mid, "aoeu").toMutableList()
        actual.retainAll(expected)
        assertEquals(expected.size, actual.size)

        expected = listOf("foo.jpg")
        actual = mTestCol!!.media.filesInStr(mid, "aoeu<img src='foo.jpg'>ao").toMutableList()
        actual.retainAll(expected)
        assertEquals(expected.size, actual.size)

        expected = listOf("foo.jpg", "bar.jpg")
        actual = mTestCol!!.media.filesInStr(mid, """aoeu<img src='foo.jpg'><img src="bar.jpg">ao""").toMutableList()
        actual.retainAll(expected)
        assertEquals(expected.size, actual.size)

        expected = listOf("foo.jpg")
        actual = mTestCol!!.media.filesInStr(mid, "aoeu<img src=foo.jpg style=bar>ao").toMutableList()
        actual.retainAll(expected)
        assertEquals(expected.size, actual.size)

        expected = listOf("one", "two")
        actual = mTestCol!!.media.filesInStr(mid, "<img src=one><img src=two>").toMutableList()
        actual.retainAll(expected)
        assertEquals(expected.size, actual.size)

        expected = listOf("foo.jpg")
        actual = mTestCol!!.media.filesInStr(mid, """aoeu<img src="foo.jpg">ao""").toMutableList()
        actual.retainAll(expected)
        assertEquals(expected.size, actual.size)

        expected = listOf("foo.jpg", "fo")
        actual =
            mTestCol!!.media.filesInStr(mid, """aoeu<img src="foo.jpg"><img class=yo src=fo>ao""").toMutableList()
        actual.retainAll(expected)
        assertEquals(expected.size, actual.size)

        expected = listOf("foo.mp3")
        actual = mTestCol!!.media.filesInStr(mid, "aou[sound:foo.mp3]aou").toMutableList()
        actual.retainAll(expected)
        assertEquals(expected.size, actual.size)

        assertEquals("aoeu", mTestCol!!.media.strip("aoeu"))
        assertEquals("aoeuaoeu", mTestCol!!.media.strip("aoeu[sound:foo.mp3]aoeu"))
        assertEquals("aoeu", mTestCol!!.media.strip("a<img src=yo>oeu"))
        assertEquals("aoeu", Media.escapeImages("aoeu"))
        assertEquals(
            "<img src='http://foo.com'>",
            Media.escapeImages("<img src='http://foo.com'>")
        )
        assertEquals(
            """<img src="foo%20bar.jpg">""",
            Media.escapeImages("""<img src="foo bar.jpg">""")
        )
    }

    @Test
    @Throws(IOException::class, EmptyMediaException::class)
    fun testDeckIntegration() {
        // create a media dir
        mTestCol!!.media.dir()
        // Put a file into it
        val file = createNonEmptyFile("fake.png")
        mTestCol!!.media.addFile(file)
        // add a note which references it
        var f = mTestCol!!.newNote()
        f.setField(0, "one")
        f.setField(1, "<img src='fake.png'>")
        mTestCol!!.addNote(f)
        // and one which references a non-existent file
        f = mTestCol!!.newNote()
        f.setField(0, "one")
        f.setField(1, "<img src='fake2.png'>")
        mTestCol!!.addNote(f)
        // and add another file which isn't used
        val os = FileOutputStream(File(mTestCol!!.media.dir(), "foo.jpg"), false)
        os.write("test".toByteArray())
        os.close()
        // check media
        val ret = mTestCol!!.media.check()
        var expected = listOf("fake2.png")
        var actual = ret.missingFileNames.toMutableList()
        actual.retainAll(expected)
        assertEquals(expected.size, actual.size)
        expected = listOf("foo.jpg")
        actual = ret.unusedFileNames.toMutableList()
        actual.retainAll(expected)
        assertEquals(expected.size, actual.size)
    }

    @Test
    @Suppress("SpellCheckingInspection")
    fun testAudioTags() {
        assertEquals("aoeu", mTestCol!!.media.strip("a<audio src=yo>oeu"))
        assertEquals("aoeu", mTestCol!!.media.strip("a<audio src='yo'>oeu"))
        assertEquals("aoeu", mTestCol!!.media.strip("""a<audio src="yo">oeu"""))
    }

    @Test
    @Suppress("SpellCheckingInspection")
    fun testObjectTags() {
        assertEquals("aoeu", mTestCol!!.media.strip("a<object data=yo>oeu"))
        assertEquals("aoeu", mTestCol!!.media.strip("a<object data='yo'>oeu"))
        assertEquals("aoeu", mTestCol!!.media.strip("""a<object data="yo">oeu"""))
    }

    private fun added(d: Collection?): List<String> {
        return d!!.media.db!!.queryStringList("select fname from media where csum is not null")
    }

    private fun removed(d: Collection?): List<String> {
        return d!!.media.db!!.queryStringList("select fname from media where csum is null")
    }

    @Test
    @Throws(IOException::class, EmptyMediaException::class)
    fun testChanges() {
        // legacy code, not used by backend
        Assume.assumeThat(defaultLegacySchema, Matchers.`is`(true))
        assertNotNull(mTestCol!!.media._changed())
        assertEquals(0, added(mTestCol).size)
        assertEquals(0, removed(mTestCol).size)
        // add a file
        val dir = testDir
        var path = File(dir, "foo.jpg")
        var os = FileOutputStream(path, false)
        os.write("hello".toByteArray())
        os.close()
        path = File(mTestCol!!.media.dir(), mTestCol!!.media.addFile(path))
        // should have been logged
        mTestCol!!.media.findChanges()
        MatcherAssert.assertThat(added(mTestCol).size, Matchers.`is`(Matchers.greaterThan(0)))
        assertEquals(0, removed(mTestCol).size)
        // if we modify it, the cache won't notice
        os = FileOutputStream(path, true)
        os.write("world".toByteArray())
        os.close()
        assertEquals(1, added(mTestCol).size)
        assertEquals(0, removed(mTestCol).size)
        // but if we add another file, it will
        path = File(path.absolutePath + "2")
        os = FileOutputStream(path, true)
        os.write("yo".toByteArray())
        os.close()
        mTestCol!!.media.findChanges(true)
        assertEquals(2, added(mTestCol).size)
        assertEquals(0, removed(mTestCol).size)
        // deletions should get noticed too
        assertTrue(path.delete())
        mTestCol!!.media.findChanges(true)
        assertEquals(1, added(mTestCol).size)
        assertEquals(1, removed(mTestCol).size)
    }

    @Test
    @Suppress("SpellCheckingInspection")
    fun testIllegal() {
        val aString = "a:b|cd\\e/f\u0000g*h\\[i\\]j"
        val good = "abcdefghij"
        assertEquals(good, mTestCol!!.media.stripIllegal(aString))
        for (element in aString) {
            val bad = mTestCol!!.media.hasIllegal("""something${element}morestring""")
            if (bad) {
                assertEquals(-1, good.indexOf(element))
            } else {
                assertNotEquals(-1, good.indexOf(element))
            }
        }
    }

    @Suppress("SpellCheckingInspection")
    @Throws(IOException::class)
    private fun createNonEmptyFile(@Suppress("SameParameterValue") fileName: String): File {
        val file = File(testDir, fileName)
        FileOutputStream(file, false).use { os -> os.write("a".toByteArray()) }
        return file
    }
}
