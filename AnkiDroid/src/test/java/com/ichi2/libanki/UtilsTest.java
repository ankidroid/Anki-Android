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
import java.util.Enumeration;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class UtilsTest {

    @Test
    public void testZipWithPathTraversal() {

        ClassLoader classLoader = getClass().getClassLoader();
        URL resource = classLoader.getResource("path-traversal.zip");
        File file = new File(resource.getPath());
        try {
            ZipFile zipFile = new ZipFile(file);
            Enumeration zipEntries = zipFile.entries();
            while (zipEntries.hasMoreElements()) {
                ZipEntry ze2 = (ZipEntry) zipEntries.nextElement();
                Utils.unzipFiles(zipFile, "/tmp", new String[]{ze2.getName()}, null);
            }
            Assert.fail("Expected an IOException");
        }
        catch (IOException e) {
            Assert.assertEquals(e.getMessage(), "File is outside extraction target directory.");
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
        URL resource = Objects.requireNonNull(getClass().getClassLoader()).getResource("path-traversal.zip");
        File copy = File.createTempFile("testCopyFileToStream", ".zip");
        copy.deleteOnExit();
        Utils.copyFile(new File(resource.getFile()), copy);
        Assert.assertEquals(TestUtils.getMD5(resource.getPath()), TestUtils.getMD5(copy.getCanonicalPath()));
    }
}
