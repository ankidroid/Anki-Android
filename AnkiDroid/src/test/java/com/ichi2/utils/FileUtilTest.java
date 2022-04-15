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

package com.ichi2.utils;


import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;


import static org.acra.util.IOUtils.writeStringToFile;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class FileUtilTest {

    @Rule
    public TemporaryFolder temporaryDirectory = new TemporaryFolder();
    long mTestDirectorySize;

    private File createSrcFilesForTest(File temporaryRoot, String testDirName) throws Exception {
        File grandParentDir = new File(temporaryRoot, testDirName);
        File parentDir = new File(grandParentDir, "parent");
        File childDir = new File(parentDir, "child");
        final File childDir2 = new File(parentDir, "child2");
        final File grandChildDir = new File(childDir, "grandChild");
        final File grandChild2Dir = new File(childDir2, "grandChild2");

        ArrayList<File> files = new ArrayList<>();
        files.add(new File(grandParentDir, "file1.txt"));
        files.add(new File(parentDir, "file2.txt"));
        files.add(new File(childDir, "file3.txt"));
        files.add(new File(childDir2, "file4.txt"));
        files.add(new File(grandChildDir, "file5.txt"));
        files.add(new File(grandChildDir, "file6.txt"));

        grandChildDir.mkdirs();
        grandChild2Dir.mkdirs();

        for (int i = 0; i < files.size(); ++i) {
            final File file = files.get(i);
            writeStringToFile(file, "File " + (i + 1) + " called " + file.getName());
            mTestDirectorySize += file.length();
        }

        return grandParentDir;
    }
    
    @Test
    public void testDirectorySize() throws Exception {
        // Create temporary root directory for holding test directories
        File temporaryRootDir = temporaryDirectory.newFolder("tempRootDir");

        // Test for success scenario
        File dir = createSrcFilesForTest(temporaryRootDir, "dir");
        assertEquals(FileUtil.getDirectorySize(dir), mTestDirectorySize);

        // Test for failure scenario by passing a file as an argument instead of a directory
        assertThrows(IOException.class, () -> FileUtil.getDirectorySize(new File(dir, "file1.txt")));
    }

    @Test
    public void ensureFileIsDirectoryTest() throws Exception {
        // Create temporary root directory for holding test directories
        File temporaryRootDir = temporaryDirectory.newFolder("tempRootDir");

        // Create test data
        File testDir = createSrcFilesForTest(temporaryRootDir, "testDir");

        // Test for file which exists but isn't a directory
        org.junit.Assert.assertThrows(IOException.class, () -> FileUtil.ensureFileIsDirectory(new File(testDir, "file1.txt")));

        // Test for file which exists and is a directory
        FileUtil.ensureFileIsDirectory(new File(testDir, "parent"));

        // Test for directory which doesn't exist, but can be created
        FileUtil.ensureFileIsDirectory(new File(testDir, "parent2"));

        // Test for directory which doesn't exist, and cannot be created
        Assert.assertThrows(IOException.class, () -> FileUtil.ensureFileIsDirectory(
                new File(testDir.getAbsolutePath() + File.separator + "file1.txt"
                        + File.separator + "impossibleDir")));
    }

    @Test
    public void listFilesTest() throws Exception {
        // Create temporary root directory for holding test directories
        File temporaryRootDir = temporaryDirectory.newFolder("tempRootDir");

        // Create valid input
        File testDir = createSrcFilesForTest(temporaryRootDir ,"testDir");
        ArrayList<File> expectedChildren = new ArrayList<>();
        expectedChildren.add(new File(testDir, "parent"));
        expectedChildren.add(new File(testDir, "file1.txt"));

        File[] testDirChildren = FileUtil.listFiles(testDir);

        // Check that listFiles lists all files in the directory
        for (final File testDirChild : testDirChildren) {
             assertTrue(expectedChildren.contains(testDirChild));
        }
        assertEquals(expectedChildren.size(), testDirChildren.length);

        // Create invalid input
        assertThrows(IOException.class, () -> FileUtil.listFiles(new File(testDir, "file1.txt")));
    }

    @Test
    public void testFileNameNull() {
        assertThat(FileUtil.getFileNameAndExtension(null), nullValue());
    }

    @Test
    public void testFileNameEmpty() {
        assertThat(FileUtil.getFileNameAndExtension(""), nullValue());
    }

    @Test
    public void testFileNameNoDot() {
        assertThat(FileUtil.getFileNameAndExtension("abc"), nullValue());
    }

    @Test
    public void testFileNameNormal() {
        Map.Entry<String, String> fileNameAndExtension = FileUtil.getFileNameAndExtension("abc.jpg");
        assertThat(fileNameAndExtension.getKey(), is("abc"));
        assertThat(fileNameAndExtension.getValue(), is(".jpg"));
    }

    @Test
    public void testFileNameTwoDot() {
        Map.Entry<String, String> fileNameAndExtension = FileUtil.getFileNameAndExtension("a.b.c");
        assertThat(fileNameAndExtension.getKey(), is("a.b"));
        assertThat(fileNameAndExtension.getValue(), is(".c"));
    }

    @Test
    public void fileSizeTest() throws IOException {
        assertThat("deleted file should have 0 size", FileUtil.getSize(new File("test.txt")), is(0L));

        File temporaryRootDir = temporaryDirectory.newFolder("tempRootDir");

        assertThat("empty directory should have 0 size", FileUtil.getSize(temporaryRootDir), is(0L));

        File textFile = new File(temporaryRootDir, "tmp.txt");
        writeStringToFile(textFile, "Hello World");

        long expectedLength = (long) "Hello World".length();
        assertThat("File size should return text length", FileUtil.getSize(textFile), is(expectedLength));

        assertThat("Should return file lengths", FileUtil.getSize(temporaryRootDir), is(expectedLength));
    }
}
