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

package com.ichi2.anki.services;

import com.ichi2.anki.RobolectricTest;
import com.ichi2.anki.multimediacard.IMultimediaEditableNote;
import com.ichi2.anki.multimediacard.fields.AudioClipField;
import com.ichi2.anki.multimediacard.fields.ImageField;
import com.ichi2.anki.multimediacard.impl.MultimediaEditableNote;
import com.ichi2.anki.servicelayer.NoteService;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Model;
import com.ichi2.libanki.Note;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

@RunWith(AndroidJUnit4.class)
public class NoteServiceTest extends RobolectricTest {

    Collection mTestCol;
    @Before
    public void before() {
        mTestCol = getCol();
    }

    //temporary folder to test importMediaToDirectory function
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    //tests if the text fields of the notes are the same after calling updateJsonNoteFromMultimediaNote
    @Test
    public void updateJsonNoteTest() {
        Model testModel = mTestCol.getModels().byName("Basic");
        IMultimediaEditableNote multiMediaNote = NoteService.createEmptyNote(testModel);
        multiMediaNote.getField(0).setText("foo");
        multiMediaNote.getField(1).setText("bar");

        Note basicNote = new Note(mTestCol, testModel);
        basicNote.setField(0, "this should be changed to foo");
        basicNote.setField(1, "this should be changed to bar");


        NoteService.updateJsonNoteFromMultimediaNote(multiMediaNote, basicNote);
        assertEquals(basicNote.getFields()[0], multiMediaNote.getField(0).getText());
        assertEquals(basicNote.getFields()[1], multiMediaNote.getField(1).getText());

    }

    //tests if updateJsonNoteFromMultimediaNote throws a RuntimeException if the ID's of the notes don't match
    @Test
    public void updateJsonNoteRuntimeErrorTest() {
        //model with ID 42
        Model testModel = new Model("{\"flds\": [{\"name\": \"foo bar\", \"ord\": \"1\"}], \"id\": \"42\"}");
        IMultimediaEditableNote multiMediaNoteWithID42 = NoteService.createEmptyNote(testModel);

        //model with ID 45
        testModel = new Model("{\"flds\": [{\"name\": \"foo bar\", \"ord\": \"1\"}], \"id\": \"45\"}");
        Note noteWithID45 = new Note(mTestCol, testModel);

        Throwable expectedException = assertThrows(RuntimeException.class, () -> NoteService.updateJsonNoteFromMultimediaNote(multiMediaNoteWithID42, noteWithID45));
        assertEquals(expectedException.getMessage(), "Source and Destination Note ID do not match.");
    }

    @Test
    public void importAudioClipToDirectoryTest() throws IOException {

        File fileAudio = folder.newFile("testaudio.wav");

        //writes a line in the file so the file's length isn't 0
        try(FileWriter fileWriter = new FileWriter(fileAudio)) {
            fileWriter.write("line1");
        }

        MultimediaEditableNote testAudioClip = new MultimediaEditableNote();
        testAudioClip.setNumFields(1);

        AudioClipField audioField = new AudioClipField();
        audioField.setAudioPath(fileAudio.getAbsolutePath());
        testAudioClip.setField(0, audioField);

        NoteService.saveMedia(mTestCol, testAudioClip);

        File outFile = new File(mTestCol.getMedia().dir(), fileAudio.getName());

        assertEquals("path should be equal to the new file made in NoteService.saveMedia", outFile.getAbsolutePath(), audioField.getAudioPath());

    }

    //similar test like above, but with an imagefield instead of an audioclipfield
    @Test
    public void importImageToDirectoryTest() throws IOException {

        File fileImage = folder.newFile("testimage.png");

        //writes a line in the file so the file's length isn't 0
        try(FileWriter fileWriter = new FileWriter(fileImage)) {
            fileWriter.write("line1");
        }

        MultimediaEditableNote testImage = new MultimediaEditableNote();
        testImage.setNumFields(1);

        ImageField imgField = new ImageField();
        imgField.setImagePath(fileImage.getAbsolutePath());
        testImage.setField(0, imgField);

        NoteService.saveMedia(mTestCol, testImage);

        File outFile = new File(mTestCol.getMedia().dir(), fileImage.getName());

        assertEquals("path should be equal to the new file made in NoteService.saveMedia", outFile.getAbsolutePath(), imgField.getImagePath());
    }


}
