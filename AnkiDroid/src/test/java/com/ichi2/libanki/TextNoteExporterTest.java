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
package com.ichi2.libanki;


import android.text.TextUtils;

import com.ichi2.anki.RobolectricTest;
import com.ichi2.anki.exception.FilteredAncestor;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.ParameterizedRobolectricTestRunner;
import org.robolectric.ParameterizedRobolectricTestRunner.Parameters;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

@RunWith(ParameterizedRobolectricTestRunner.class)
public class TextNoteExporterTest extends RobolectricTest {

    @Parameters(name = "{index} id:{0}\ttags:{1}\thtml:{2}")
    public static Iterable<Object[]> data() {
        List<Object[]> data = new ArrayList<>();
        for (int id = 0; id <= 1; id++) {
            for (int tags = 0; tags <= 1; tags++) {
                for (int html = 0; html <= 1; html++) {
                    data.add(new Object[] {id != 0, tags != 0, html != 0});
                }
            }
        }
        return data;
    }


    private final boolean mIncludeId;
    private final boolean mIncludeTags;
    private final boolean mIncludeHTML;

    private Collection mCollection;
    private TextNoteExporter mExporter;
    private List<Note> mNoteList;


    public TextNoteExporterTest(boolean mIncludeId, boolean mIncludeTags, boolean mIncludeHTML) {
        this.mIncludeId = mIncludeId;
        this.mIncludeTags = mIncludeTags;
        this.mIncludeHTML = mIncludeHTML;
    }


    @Before
    public void setUp() {
        super.setUp();
        mCollection = getCol();
        mExporter = new TextNoteExporter(mCollection, mIncludeId, mIncludeTags, mIncludeHTML);

        final Note n1 = mCollection.newNote();
        n1.setItem("Front", "foo");
        n1.setItem("Back", "bar<br>");
        n1.addTags(new HashSet<>(Arrays.asList("tag", "tag2")));
        mCollection.addNote(n1);

        final Note n2 = mCollection.newNote();
        n2.setItem("Front", "baz");
        n2.setItem("Back", "qux");

        try {
            n2.model().put("did", mCollection.getDecks().id("new col"));
        } catch (FilteredAncestor filteredAncestor) {
            filteredAncestor.printStackTrace();
        }
        mCollection.addNote(n2);

        mNoteList = Arrays.asList(n1, n2);
    }


    @Test
    public void will_export_id_tags_html() throws IOException {
        Path tempExportDir = Files.createTempDirectory("AnkiDroid-test_export_textnote");
        File exportedFile = new File(tempExportDir.toFile(), "export.txt");

        mExporter.doExport(exportedFile.getAbsolutePath());

        String[] lines;
        try (BufferedReader reader = new BufferedReader(new FileReader(exportedFile))) {
            lines = reader.lines().collect(Collectors.joining("\n")).split("\\n");
        }

        assertEquals(mNoteList.size(), lines.length);

        for (int i = 0; i < mNoteList.size(); i++) {
            final Note note = mNoteList.get(i);
            final String line = lines[i];

            List<String> row = new ArrayList<>();
            if (mIncludeId) {
                row.add(note.getGuId());
            }
            for (String field : note.getFields()) {
                row.add(mExporter.processText(field));
            }
            if (mIncludeTags) {
                row.add(TextUtils.join(" ", note.getTags()));
            }

            final String expected = TextUtils.join("\t", row);
            assertEquals(expected, line);
        }
    }
}
