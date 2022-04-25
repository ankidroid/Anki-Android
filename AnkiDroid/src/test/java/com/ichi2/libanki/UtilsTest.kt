/****************************************************************************************
 * Copyright (c) 2018 Mike Hardy <mike@mikehardy.net>                                   *
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

package com.ichi2.libanki;

import com.ichi2.anki.TestUtils;

import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.junit.runner.RunWith;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import static com.ichi2.libanki.Utils.nonEmptyFields;
import static com.ichi2.utils.FileOperation.getFileResource;
import static org.junit.Assert.assertEquals;


@RunWith(AndroidJUnit4.class)
public class UtilsTest {

    @Test
    public void testZipWithPathTraversal() {

        ClassLoader classLoader = getClass().getClassLoader();
        URL resource = classLoader.getResource("path-traversal.zip");
        try {
            File file = new File(resource.toURI());
            ZipFile zipFile = new ZipFile(file);
            Enumeration<ZipArchiveEntry> zipEntries = zipFile.getEntries();
            while (zipEntries.hasMoreElements()) {
                ZipArchiveEntry ze2 = zipEntries.nextElement();
                Utils.unzipFiles(zipFile, "/tmp", new String[]{ze2.getName()}, null);
            }
            Assert.fail("Expected an IOException");
        }
        catch (Exception e) {
            Assert.assertEquals("File is outside extraction target directory.", e.getMessage());
        }
    }

    @Test
    public void testInvalidPaths() {
        try {
            File tmpDir = new File("/tmp");
            Assert.assertFalse(Utils.isInside(new File(tmpDir, "../foo"), tmpDir));
            Assert.assertFalse(Utils.isInside(new File(tmpDir, "/tmp/one/../../../foo"), tmpDir));
        } catch (IOException ioe) {
            Assert.fail("Unexpected exception: " + ioe);
        }
    }

    @Test
    public void testValidPaths() {
        try {
            File tmpDir = new File("/tmp");
            Assert.assertTrue(Utils.isInside(new File(tmpDir, "test/file/path/no/parent"), tmpDir));
            Assert.assertTrue(Utils.isInside(new File(tmpDir, "/tmp/absolute/path"), tmpDir));
            Assert.assertTrue(Utils.isInside(new File(tmpDir, "test/file/../../"), tmpDir));
        } catch (IOException ioe) {
            Assert.fail("Unexpected exception: " + ioe);
        }
    }

    @Test
    public void testCopyFile() throws Exception {
        String resourcePath = getFileResource("path-traversal.zip");
        File copy = File.createTempFile("testCopyFileToStream", ".zip");
        copy.deleteOnExit();
        Utils.copyFile(new File(resourcePath), copy);
        Assert.assertEquals(TestUtils.getMD5(resourcePath), TestUtils.getMD5(copy.getCanonicalPath()));
    }

    @Test
    public void testSplit() {
        Assert.assertArrayEquals(new String[]{"foo", "bar"}, Utils.splitFields("foobar"));
        Assert.assertArrayEquals(new String[]{"", "foo", "", "", ""}, Utils.splitFields("foo"));
    }

    @Test
    public void nonEmptyFieldsTest() {
        Map<String, String> m = new HashMap<>();
        Set<String> s = new HashSet<>();
        Assert.assertEquals(s, nonEmptyFields(m));
        m.put("miam", "");
        Assert.assertEquals(s, nonEmptyFields(m));
        m.put("foo", "   ");
        Assert.assertEquals(s, nonEmptyFields(m));
        m.put("bar", " plop  ");
        s.add("bar");
        Assert.assertEquals(s, nonEmptyFields(m));
    }


    @Test
    public void test_stripHTML_will_remove_tags() {
        final List<String> strings = Arrays.asList(
                "<>",
                "<1>",
                "<asdasd>",
                "<\n>",
                "<\\qwq>",
                "<aa\nsd\nas\n?\n>"
        );

        for (String s : strings) {
            assertEquals(s.replace("\n","\\n") + " should be removed.",
                    "", Utils.stripHTML(s));
        }
    }

    @Test
    public void test_stripHTML_will_remove_comments() {
        final List<String> strings = Arrays.asList(
                "<!---->",
                "<!--dd-->",
                "<!--asd asd asd-->",
                "<!--\n-->",
                "<!--\nsd-->",
                "<!--lkl\nklk\n-->"
        );

        for (String s : strings) {
            assertEquals(s.replace("\n","\\n") + " should be removed.",
                    "", Utils.stripHTML(s));
        }
    }
}
