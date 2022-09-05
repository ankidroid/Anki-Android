/*
 Copyright (c) 2021 Tarek Mohamed Abdalla <tarekkma@gmail.com>
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

import android.text.TextUtils
import com.ichi2.anki.RobolectricTest
import com.ichi2.libanki.backend.exception.DeckRenameException
import com.ichi2.utils.FileOperation
import com.ichi2.utils.KotlinCleanup
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.util.*
import kotlin.Throws

@KotlinCleanup("lateinit wherever possible")
@KotlinCleanup("IDE-lint")
@RunWith(ParameterizedRobolectricTestRunner::class)
class TextNoteExporterTest(
    private val includeId: Boolean,
    private val includeTags: Boolean,
    private val includeHTML: Boolean
) : RobolectricTest() {
    private var mCollection: Collection? = null
    private var mExporter: TextNoteExporter? = null
    private var mNoteList: List<Note>? = null
    @Before
    override fun setUp() {
        super.setUp()
        mCollection = col
        mExporter = TextNoteExporter(mCollection!!, includeId, includeTags, includeHTML)
        val n1 = mCollection!!.newNote()
        n1.setItem("Front", "foo")
        n1.setItem("Back", "bar<br>")
        n1.addTags(HashSet(Arrays.asList("tag", "tag2")))
        mCollection!!.addNote(n1)
        val n2 = mCollection!!.newNote()
        n2.setItem("Front", "baz")
        n2.setItem("Back", "qux")
        try {
            n2.model().put("did", mCollection!!.decks.id("new col"))
        } catch (filteredAncestor: DeckRenameException) {
            Timber.e(filteredAncestor)
        }
        mCollection!!.addNote(n2)
        mNoteList = Arrays.asList(n1, n2)
    }

    @Test
    @Throws(IOException::class)
    fun will_export_id_tags_html() {
        val exportedFile = File.createTempFile("export", ".txt")
        mExporter!!.doExport(exportedFile.absolutePath)
        val lines = FileOperation.getFileContents(exportedFile).split("\n".toRegex()).toTypedArray()
        Assert.assertEquals(mNoteList!!.size.toLong(), lines.size.toLong())
        for (i in mNoteList!!.indices) {
            val note = mNoteList!![i]
            val line = lines[i]
            val row: MutableList<String?> = ArrayList()
            if (includeId) {
                row.add(note.guId)
            }
            for (field in note.fields) {
                row.add(mExporter!!.processText(field))
            }
            if (includeTags) {
                row.add(TextUtils.join(" ", note.tags))
            }
            val expected = TextUtils.join("\t", row)
            Assert.assertEquals(expected, line)
        }
    }

    companion object {
        @JvmStatic // required: Parameters
        @ParameterizedRobolectricTestRunner.Parameters(name = "{index} id:{0}\ttags:{1}\thtml:{2}")
        fun data(): Iterable<Array<Any>> {
            val data: MutableList<Array<Any>> = ArrayList()
            for (id in 0..1) {
                for (tags in 0..1) {
                    for (html in 0..1) {
                        data.add(arrayOf(id != 0, tags != 0, html != 0))
                    }
                }
            }
            return data
        }
    }
}
