/*
 Copyright (c) 2021 Kael Madar <itsybitsyspider@madarhome.com>

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
package com.ichi2.anki.services

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.multimediacard.IMultimediaEditableNote
import com.ichi2.anki.multimediacard.fields.ImageField
import com.ichi2.anki.multimediacard.fields.MediaClipField
import com.ichi2.anki.servicelayer.NoteService
import com.ichi2.libanki.Collection
import com.ichi2.libanki.Model
import com.ichi2.libanki.Note
import com.ichi2.testutils.createTransientFile
import com.ichi2.utils.KotlinCleanup
import org.hamcrest.CoreMatchers.*
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.io.FileMatchers.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File
import java.io.FileWriter
import java.io.IOException

@KotlinCleanup("See if we can remove JvmField from Rule")
@KotlinCleanup("have Model constructor accent @Language('JSON')")
@KotlinCleanup("fix typo: testimage -> test_image")
@KotlinCleanup("Add scope functions")
@RunWith(AndroidJUnit4::class)
class NoteServiceTest : RobolectricTest() {
    @KotlinCleanup("lateinit")
    var testCol: Collection? = null
    @Before
    fun before() {
        testCol = col
    }

    // temporary directory to test importMediaToDirectory function
    @Rule
    @JvmField
    var directory = TemporaryFolder()

    @Rule
    @JvmField
    var directory2 = TemporaryFolder()

    // tests if the text fields of the notes are the same after calling updateJsonNoteFromMultimediaNote
    @Test
    fun updateJsonNoteTest() {
        val testModel = testCol!!.models.byName("Basic")
        val multiMediaNote: IMultimediaEditableNote? = NoteService.createEmptyNote(testModel!!)
        multiMediaNote!!.getField(0)!!.text = "foo"
        multiMediaNote.getField(1)!!.text = "bar"

        val basicNote = Note(testCol!!, testModel)
        basicNote.setField(0, "this should be changed to foo")
        basicNote.setField(1, "this should be changed to bar")

        NoteService.updateJsonNoteFromMultimediaNote(multiMediaNote, basicNote)
        assertEquals(basicNote.fields[0], multiMediaNote.getField(0)!!.text)
        assertEquals(basicNote.fields[1], multiMediaNote.getField(1)!!.text)
    }

    // tests if updateJsonNoteFromMultimediaNote throws a RuntimeException if the ID's of the notes don't match
    @Test
    fun updateJsonNoteRuntimeErrorTest() {
        // model with ID 42
        var testModel = Model("{\"flds\": [{\"name\": \"foo bar\", \"ord\": \"1\"}], \"id\": \"42\"}")
        val multiMediaNoteWithID42: IMultimediaEditableNote? = NoteService.createEmptyNote(testModel)

        // model with ID 45
        testModel = Model("{\"flds\": [{\"name\": \"foo bar\", \"ord\": \"1\"}], \"id\": \"45\"}")
        val noteWithID45 = Note(testCol!!, testModel)
        val expectedException: Throwable = assertThrows(RuntimeException::class.java) { NoteService.updateJsonNoteFromMultimediaNote(multiMediaNoteWithID42, noteWithID45) }
        assertEquals(expectedException.message, "Source and Destination Note ID do not match.")
    }

    @Test
    @Throws(IOException::class)
    fun importAudioClipToDirectoryTest() {
        val fileAudio = directory.newFile("testaudio.wav")

        // writes a line in the file so the file's length isn't 0
        FileWriter(fileAudio).use { fileWriter -> fileWriter.write("line1") }

        val audioField = MediaClipField()
        audioField.audioPath = fileAudio.absolutePath

        NoteService.importMediaToDirectory(testCol!!, audioField)

        val outFile = File(testCol!!.media.dir(), fileAudio.name)

        assertThat("path should be equal to new file made in NoteService.importMediaToDirectory", outFile, aFileWithAbsolutePath(equalTo(audioField.audioPath)))
    }

    // Similar test like above, but with an ImageField instead of a MediaClipField
    @Test
    @Throws(IOException::class)
    fun importImageToDirectoryTest() {
        val fileImage = directory.newFile("testimage.png")

        // writes a line in the file so the file's length isn't 0
        FileWriter(fileImage).use { fileWriter -> fileWriter.write("line1") }

        val imgField = ImageField()
        imgField.imagePath = fileImage.absolutePath

        NoteService.importMediaToDirectory(testCol!!, imgField)

        val outFile = File(testCol!!.media.dir(), fileImage.name)

        assertThat("path should be equal to new file made in NoteService.importMediaToDirectory", outFile, aFileWithAbsolutePath(equalTo(imgField.imagePath)))
    }

    /**
     * Tests if after importing:
     *
     * * New file keeps its name
     * * File with same name, but different content, has its name changed
     * * File with same name and content don't have its name changed
     *
     * @throws IOException if new created files already exist on temp directory
     */
    @Test
    @Throws(IOException::class)
    fun importAudioWithSameNameTest() {
        val f1 = directory.newFile("audio.mp3")
        val f2 = directory2.newFile("audio.mp3")

        // writes a line in the file so the file's length isn't 0
        FileWriter(f1).use { fileWriter -> fileWriter.write("1") }
        // do the same to the second file, but with different data
        FileWriter(f2).use { fileWriter -> fileWriter.write("2") }

        val fld1 = MediaClipField()
        fld1.audioPath = f1.absolutePath

        val fld2 = MediaClipField()
        fld2.audioPath = f2.absolutePath

        // third field to test if name is kept after reimporting the same file
        val fld3 = MediaClipField()
        fld3.audioPath = f1.absolutePath

        NoteService.importMediaToDirectory(testCol!!, fld1)
        val o1 = File(testCol!!.media.dir(), f1.name)

        NoteService.importMediaToDirectory(testCol!!, fld2)
        val o2 = File(testCol!!.media.dir(), f2.name)

        NoteService.importMediaToDirectory(testCol!!, fld3)
        // creating a third outfile isn't necessary because it should be equal to the first one

        assertThat("path should be equal to new file made in NoteService.importMediaToDirectory", o1, aFileWithAbsolutePath(equalTo(fld1.audioPath)))
        assertThat("path should be different to new file made in NoteService.importMediaToDirectory", o2, aFileWithAbsolutePath(not(fld2.audioPath)))
        assertThat("path should be equal to new file made in NoteService.importMediaToDirectory", o1, aFileWithAbsolutePath(equalTo(fld3.audioPath)))
    }

    // Similar test like above, but with an ImageField instead of a MediaClipField
    @Test
    @Throws(IOException::class)
    fun importImageWithSameNameTest() {
        val f1 = directory.newFile("img.png")
        val f2 = directory2.newFile("img.png")

        // write a line in the file so the file's length isn't 0
        FileWriter(f1).use { fileWriter -> fileWriter.write("1") }
        // do the same to the second file, but with different data
        FileWriter(f2).use { fileWriter -> fileWriter.write("2") }

        val fld1 = ImageField()
        fld1.imagePath = f1.absolutePath

        val fld2 = ImageField()
        fld2.imagePath = f2.absolutePath

        // third field to test if name is kept after reimporting the same file
        val fld3 = ImageField()
        fld3.imagePath = f1.absolutePath

        NoteService.importMediaToDirectory(testCol!!, fld1)
        val o1 = File(testCol!!.media.dir(), f1.name)

        NoteService.importMediaToDirectory(testCol!!, fld2)
        val o2 = File(testCol!!.media.dir(), f2.name)

        NoteService.importMediaToDirectory(testCol!!, fld3)
        // creating a third outfile isn't necessary because it should be equal to the first one

        assertThat("path should be equal to new file made in NoteService.importMediaToDirectory", o1, aFileWithAbsolutePath(equalTo(fld1.imagePath)))
        assertThat("path should be different to new file made in NoteService.importMediaToDirectory", o2, aFileWithAbsolutePath(not(fld2.imagePath)))
        assertThat("path should be equal to new file made in NoteService.importMediaToDirectory", o1, aFileWithAbsolutePath(equalTo(fld3.imagePath)))
    }

    /**
     * Sometimes media files cannot be imported directly to the media directory,
     * so they are copied to cache then imported and deleted.
     * This tests if cached media are properly deleted after import.
     */
    @Test
    fun tempAudioIsDeletedAfterImport() {
        val file = createTransientFile("foo")

        val field = MediaClipField()
        field.audioPath = file.absolutePath
        field.setHasTemporaryMedia(true)

        NoteService.importMediaToDirectory(testCol!!, field)

        assertThat("Audio temporary file should have been deleted after importing", file, not(anExistingFile()))
    }

    // Similar test like above, but with an ImageField instead of a MediaClipField
    @Test
    fun tempImageIsDeletedAfterImport() {
        val file = createTransientFile("foo")

        val field = ImageField()
        field.imagePath = file.absolutePath
        field.setHasTemporaryMedia(true)

        NoteService.importMediaToDirectory(testCol!!, field)

        assertThat("Image temporary file should have been deleted after importing", file, not(anExistingFile()))
    }
}
