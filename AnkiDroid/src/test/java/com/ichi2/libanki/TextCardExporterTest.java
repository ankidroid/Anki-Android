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

package com.ichi2.libanki;

import com.ichi2.anki.RobolectricTest;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import static org.junit.Assert.assertEquals;

@RunWith(AndroidJUnit4.class)
public class TextCardExporterTest extends RobolectricTest {
    private Collection col;
    private List<Note> noteList = new ArrayList<Note>();


    @Before
    public void setUp() {
        super.setUp();
        col = getCol();
        Note note = col.newNote();
        note.setItem("Front", "foo");
        note.setItem("Back", "bar<br>");
        note.setTagsFromStr("tag, tag2");
        col.addNote(note);
        noteList.add(note);
        // with a different note
        note = col.newNote();
        note.setItem("Front", "baz");
        note.setItem("Back", "qux");
        note.model().put("did", addDeck("new col"));
        col.addNote(note);
        noteList.add(note);
    }


    @Test
    public void testExportTextCardWithHTML() throws IOException {
        Path tempExportDir = Files.createTempDirectory("AnkiDroid-test_export_textcard");
        File exportedFile = new File(tempExportDir.toFile(), "export.txt");

        TextCardExporter exporter = new TextCardExporter(col, true);
        exporter.doExport(exportedFile.getAbsolutePath());
        String content = new String(Files.readAllBytes(Paths.get(exportedFile.getAbsolutePath())));
        String expected = "";
        // Alternatively we can choose to strip styling from content, instead of adding styling to expected
        expected += String.format(Locale.US, "<style>%s</style>", noteList.get(0).model().getString("css"));
        expected += "foo\tbar<br>\n";
        expected += String.format(Locale.US, "<style>%s</style>", noteList.get(1).model().getString("css"));
        expected += "baz\tqux\n";
        assertEquals(expected, content);
    }


    @Test
    public void testExportTextCardWithoutHTML() throws IOException {
        Path tempExportDir = Files.createTempDirectory("AnkiDroid-test_export_textcard");
        File exportedFile = new File(tempExportDir.toFile(), "export.txt");

        TextCardExporter exporter = new TextCardExporter(col, false);
        exporter.doExport(exportedFile.getAbsolutePath());
        String content = new String(Files.readAllBytes(Paths.get(exportedFile.getAbsolutePath())));
        String expected = "foo\tbar\nbaz\tqux\n";
        assertEquals(expected, content);
    }
}
