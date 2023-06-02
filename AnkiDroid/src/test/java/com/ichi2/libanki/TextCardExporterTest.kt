/*
 Copyright (c) 2021 Trung Dang <bill081001@gmail.com>
 This program is free software; you can redistribute it and/or modify it under
 the terms of the GNU General Public License as published by the Free Software
 Foundation; either version 3 of the License, or (at your option) any later
 version.
 This program is distributed in the hope that it will be useful, but WITHOUT ANY
 WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 PARTICULAR PURPOSE. See the GNU General Public License for more details.
 You should have received a copy of the GNU General Public License along with
 this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.libanki

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.utils.FileOperation.Companion.getFileContents
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException
import java.util.*
import kotlin.Throws

@RunWith(AndroidJUnit4::class)
class TextCardExporterTest : RobolectricTest() {
    private lateinit var mCol: Collection
    private val mNoteList: MutableList<Note> = ArrayList()

    @Before
    override fun setUp() {
        super.setUp()
        mCol = col
        var note = mCol.newNote()
        note.setItem("Front", "foo")
        note.setItem("Back", "bar<br>")
        note.setTagsFromStr(col, "tag, tag2")
        mCol.addNote(note)
        mNoteList.add(note)
        // with a different note
        note = mCol.newNote()
        note.setItem("Front", "baz")
        note.setItem("Back", "qux")
        note.model().put("did", addDeck("new col"))
        mCol.addNote(note)
        mNoteList.add(note)
    }

    @Test
    @Throws(IOException::class)
    fun testExportTextCardWithHTML() {
        val exportedFile = File.createTempFile("export", ".txt")

        val exporter = TextCardExporter(mCol, true)
        exporter.doExport(exportedFile.absolutePath)
        // Getting all the content of the file as a string
        val content = getFileContents(exportedFile)

        var expected = ""
        // Alternatively we can choose to strip styling from content, instead of adding styling to expected
        expected += String.format(
            Locale.US,
            "<style>%s</style>",
            mNoteList[0].model().getString("css")
        )
        expected += "foo\tbar<br>\n"
        expected += String.format(
            Locale.US,
            "<style>%s</style>",
            mNoteList[1].model().getString("css")
        )
        expected += "baz\tqux\n"
        assertEquals(expected, content)
    }

    @Test
    @Throws(IOException::class)
    fun testExportTextCardWithoutHTML() {
        val exportedFile = File.createTempFile("export", ".txt")

        val exporter = TextCardExporter(mCol, false)
        exporter.doExport(exportedFile.absolutePath)
        // Getting all the content of the file as a string
        val content = getFileContents(exportedFile)
        val expected = "foo\tbar\nbaz\tqux\n"
        assertEquals(expected, content)
    }
}
