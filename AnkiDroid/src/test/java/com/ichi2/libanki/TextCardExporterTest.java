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
        // with a different col
        note = col.newNote();
        note.setItem("Front", "baz");
        note.setItem("Back", "qux");
        note.model().put("did", addDeck("new col"));
        col.addNote(note);
        noteList.add(note);
    }


    @Test
    public void textExportTestCard() throws IOException {
        Path tempExportDir = Files.createTempDirectory("AnkiDroid-test_export_textnote");
        File exportedFile = new File(tempExportDir.toFile(), "export.txt");

        // With HTML
        TextCardExporter exporterWithHTML = new TextCardExporter(col, true);
        exporterWithHTML.doExport(exportedFile.getAbsolutePath());
        String contentWithHTML = new String(Files.readAllBytes(Paths.get(exportedFile.getAbsolutePath())));
        String expectedWithHTML = "";
        // Optionally we can choose to strip styling from contentWithHTML
        expectedWithHTML += String.format(Locale.US, "<style>%s</style>", noteList.get(0).model().getString("css"));
        expectedWithHTML += "foo\tbar<br>\n";
        expectedWithHTML += String.format(Locale.US, "<style>%s</style>", noteList.get(1).model().getString("css"));
        expectedWithHTML += "baz\tqux\n";
        assertEquals(expectedWithHTML, contentWithHTML);

        // Without HTML
        TextCardExporter exporterWithoutHTML = new TextCardExporter(col, false);
        exporterWithoutHTML.doExport(exportedFile.getAbsolutePath());
        String contentWithoutHTML = new String(Files.readAllBytes(Paths.get(exportedFile.getAbsolutePath())));
        String expectedWithoutHTML = "foo\tbar\nbaz\tqux\n";
        assertEquals(expectedWithoutHTML, contentWithoutHTML);
    }
}
