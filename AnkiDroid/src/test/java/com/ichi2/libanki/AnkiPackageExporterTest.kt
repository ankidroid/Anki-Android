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
package com.ichi2.libanki

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.exception.ImportExportException
import com.ichi2.libanki.exception.EmptyMediaException
import com.ichi2.utils.CreateTempDir.Companion.tempDir
import com.ichi2.utils.FileOperation.Companion.getFileContents
import com.ichi2.utils.KotlinCleanup
import com.ichi2.utils.UnzipFile.Companion.unzip
import org.hamcrest.MatcherAssert.*
import org.hamcrest.Matchers.*
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException
import java.io.PrintWriter

@RunWith(AndroidJUnit4::class)
@KotlinCleanup("IDE Lint")
class AnkiPackageExporterTest : RobolectricTest() {
    override fun useInMemoryDatabase(): Boolean {
        return false
    }

    @Test
    @Throws(IOException::class, ImportExportException::class)
    fun missingFileInDeckExportDoesSkipsFile() {
        // Arrange
        val mediaFilePath = addTempFileToMediaAndNote()
        check(mediaFilePath.delete()) { "need to delete temp file for test to pass" }
        val exporter = exporterForDeckWithMedia
        val temp = tempDir("/AnkiDroid-missingFileInExportDoesNotThrowException-export")
        val exportedFile = File(temp.absolutePath + "/export.apkg")

        // Exporting
        exporter.exportInto(exportedFile.absolutePath, targetContext)

        // Unzipping the export.apkg file
        unzip(exportedFile, temp.absolutePath + "/unzipped")
        val unzipDirectory = temp.absolutePath + "/unzipped"

        // Storing paths of unzipped files in a list
        val files = listOf(*File(unzipDirectory).list()!!)
        val fileNames = arrayOfNulls<File>(2)
        for ((i, x) in files.withIndex()) {
            val f = File("$unzipDirectory/$x")
            fileNames[i] = f
        }

        // Checking the unzipped files
        assertThat(files, containsInAnyOrder("collection.anki2", "media"))
        assertThat("Only two files should exist", files, hasSize(2))
        checkMediaExportStringIs(fileNames, "{}")
    }

    @Test
    @Throws(IOException::class, ImportExportException::class)
    fun fileInExportIsCopied() {
        // Arrange
        val tempFileInCollection = addTempFileToMediaAndNote()
        val exporter = exporterForDeckWithMedia
        val temp = tempDir("/AnkiDroid-missingFileInExportDoesNotThrowException-export")
        val exportedFile = File(temp.absolutePath + "/export.apkg")

        // Exporting
        exporter.exportInto(exportedFile.absolutePath, targetContext)

        // Unzipping the export.apkg file
        unzip(exportedFile, temp.absolutePath + "/unzipped")
        val unzipDirectory = temp.absolutePath + "/unzipped"

        // Storing paths of unzipped files in a list
        val files = listOf(*File(unzipDirectory).list()!!)
        val fileNames = arrayOfNulls<File>(3)
        for ((i, x) in files.withIndex()) {
            val f = File("$unzipDirectory/$x")
            fileNames[i] = f
        }
        // Checking the unzipped files
        assertThat(
            files,
            containsInAnyOrder("collection.anki2", "media", "0")
        )
        assertThat("Three files should exist", files, hasSize(3))

        // {"0":"filename.txt"}
        val expected = "{\"0\":\"${tempFileInCollection.name}\"}"
        checkMediaExportStringIs(fileNames, expected)
    }

    @Test
    fun stripHTML_will_remove_html_with_unicode_whitespace() {
        val exporter: Exporter = exporterForDeckWithMedia
        val text = "\n" + "\n\u205F\t<[sound:test.mp3]>" +
            "\n\u2029" +
            "\t\u2004" +
            "<!-- Comment \n \u1680 --> <\"tag\\n><style><s>"
        val res = exporter.stripHTML(text)
        Assert.assertEquals("", res)
    }

    @Throws(IOException::class)
    private fun checkMediaExportStringIs(files: Array<File?>, s: String) {
        for (f in files) {
            if ("media" != f!!.name) {
                continue
            }
            val lines = listOf(
                *getFileContents(
                    f
                ).split("\n").toTypedArray()
            )
            assertThat(lines, contains(s))
            return
        }
        Assert.fail("media file not found")
    }

    private val exporterForDeckWithMedia: AnkiPackageExporter
        get() = AnkiPackageExporter(col, 1L, includeSched = true, includeMedia = true)

    @Throws(IOException::class)
    private fun addTempFileToMediaAndNote(): File {
        val temp = File.createTempFile("AnkiDroid-missingFileInExportDoesNotThrowException", ".txt")
        val writer = PrintWriter(temp)
        writer.println("unit test data")
        writer.close()
        val s = addFile(temp)
        temp.delete()
        val newFile = File(col.media.dir(), s)
        check(newFile.exists()) { "Could not create temp file" }
        addNoteUsingBasicModel("<img src=\"${newFile.name}\">", "Back")
        return newFile
    }

    @Throws(IOException::class)
    private fun addFile(temp: File): String {
        return try {
            col.media.addFile(temp)
        } catch (e: EmptyMediaException) {
            throw RuntimeException(e)
        }
    }
}
