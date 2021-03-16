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

package com.ichi2.libanki;

import com.ichi2.anki.RobolectricTest;
import com.ichi2.anki.exception.ImportExportException;
import com.ichi2.libanki.exception.EmptyMediaException;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import androidx.annotation.NonNull;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.assertEquals;

@RunWith(AndroidJUnit4.class)
public class AnkiPackageExporterTest extends RobolectricTest {

    @Override
    protected boolean useInMemoryDatabase() {
        return false;
    }


    @Test
    public void missingFileInDeckExportDoesSkipsFile() throws IOException, ImportExportException {
        // arrange
        File mediaFilePath = addTempFileToMediaAndNote();
        if (!mediaFilePath.delete()) {
            throw new IllegalStateException("need to delete temp file for test to pass");
        }

        AnkiPackageExporter exporter = getExporterForDeckWithMedia();
        Path tempExportDir = Files.createTempDirectory("AnkiDroid-missingFileInExportDoesNotThrowException-export");

        File exportedFile = new File(tempExportDir.toFile(), "export.apkg");

        // act
        exporter.exportInto(exportedFile.getAbsolutePath(), getTargetContext());

        // assert
        Path unzipDirectory = unzipFilesTo(tempExportDir, exportedFile);


        File[] files = unzipDirectory.toFile().listFiles();

        // confirm the files
        List<String> fileNames = Arrays.stream(files).map(File::getName).collect(Collectors.toList());
        assertThat(fileNames, containsInAnyOrder("collection.anki2", "media"));
        assertThat("Only two files should exist", fileNames, hasSize(2));
        checkMediaExportStringIs(files, "{}");
    }

    @Test
    public void fileInExportIsCopied() throws IOException, ImportExportException {
        // arrange
        File tempFileInCollection = addTempFileToMediaAndNote();

        AnkiPackageExporter exporter = getExporterForDeckWithMedia();
        Path tempExportDir = Files.createTempDirectory("AnkiDroid-missingFileInExportDoesNotThrowException-export");

        File exportedFile = new File(tempExportDir.toFile(), "export.apkg");

        // act
        exporter.exportInto(exportedFile.getAbsolutePath(), getTargetContext());

        // assert
        Path unzipDirectory = unzipFilesTo(tempExportDir, exportedFile);


        File[] files = unzipDirectory.toFile().listFiles();

        // confirm the files
        List<String> fileNames = Arrays.stream(files).map(File::getName).collect(Collectors.toList());
        assertThat(fileNames, containsInAnyOrder("collection.anki2", "media", "0"));
        assertThat("Three files should exist", fileNames, hasSize(3));

        // {"0":"filename.txt"}
        String expected = String.format("{\"0\":\"%s\"}", tempFileInCollection.getName());
        checkMediaExportStringIs(files, expected);
    }


    @Test
    public void stripHTML_will_remove_html_with_unicode_whitespace() {
        Exporter exporter = getExporterForDeckWithMedia();

        final String res = exporter.stripHTML(String.join(
                "\n",//delimiter
                "\n\u205F\t<[sound",
                ":test.mp3]>",
                "\n\u2029\t",
                "\u2004",
                "<!-- Comment \n \u1680 --> <",
                "tag\n>",
                "<style><s>"
        ));

        assertEquals("", res);
    }


    private void checkMediaExportStringIs(File[] files, String s) throws IOException {
        for (File f : files) {
            if (!"media".equals(f.getName())) {
                continue;
            }
            List<String> lines = Files.readAllLines(Paths.get(f.getAbsolutePath()));
            assertThat(lines, contains(s));
            return;
        }

        Assert.fail("media file not found");
    }



    @NonNull
    private AnkiPackageExporter getExporterForDeckWithMedia() {
        AnkiPackageExporter exporter = new AnkiPackageExporter(getCol(), 1L, true, true);
        return exporter;
    }


    private Path unzipFilesTo(Path tempDirWithPrefix, File fileToUnzip) throws IOException {
        org.apache.commons.compress.archivers.zip.ZipFile exportReader = new org.apache.commons.compress.archivers.zip.ZipFile(fileToUnzip.getAbsolutePath());

        Path unzipDirectory = tempDirWithPrefix.resolve("unzipped");
        if (!unzipDirectory.toFile().mkdir()) {
            throw new IllegalStateException(String.format("failed to make path %s", unzipDirectory));
        }

        // we need to unzip the zipped collection
        Utils.unzipAllFiles(exportReader, unzipDirectory.toAbsolutePath().toString());
        return unzipDirectory;
    }


    private File addTempFileToMediaAndNote() throws IOException {
        File temp = File.createTempFile("AnkiDroid-missingFileInExportDoesNotThrowException", ".txt");
        PrintWriter writer = new PrintWriter(temp);
        writer.println("unit test data");
        writer.close();

        String s = addFile(temp);
        temp.delete();

        File newFile = new File(getCol().getMedia().dir(), s);
        if (!newFile.exists()) {
            throw new IllegalStateException("Could not create temp file");
        }

        addNoteUsingBasicModel(String.format("<img src=\"%s\">", newFile.getName()), "Back");

        return newFile;
    }


    private String addFile(File temp) throws IOException {
        try {
            return getCol().getMedia().addFile(temp);
        } catch (EmptyMediaException e) {
            throw new RuntimeException(e);
        }
    }

}
