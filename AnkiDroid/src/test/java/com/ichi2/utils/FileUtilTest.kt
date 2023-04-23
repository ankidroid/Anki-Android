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
package com.ichi2.utils

import com.ichi2.utils.FileUtil.isPrefix
import org.acra.util.IOUtils.writeStringToFile
import org.hamcrest.CoreMatchers
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.core.IsEqual.equalTo
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FileUtilTest {
    @get:Rule
    var temporaryDirectory = TemporaryFolder()
    private var testDirectorySize: Long = 0

    @Throws(Exception::class)
    private fun createSrcFilesForTest(temporaryRoot: File, testDirName: String): File {
        val grandParentDir = File(temporaryRoot, testDirName)
        val parentDir = File(grandParentDir, "parent")
        val childDir = File(parentDir, "child")
        val childDir2 = File(parentDir, "child2")
        val grandChildDir = File(childDir, "grandChild")
        val grandChild2Dir = File(childDir2, "grandChild2")
        val files = listOf(
            File(grandParentDir, "file1.txt"),
            File(parentDir, "file2.txt"),
            File(childDir, "file3.txt"),
            File(childDir2, "file4.txt"),
            File(grandChildDir, "file5.txt"),
            File(grandChildDir, "file6.txt")
        )
        grandChildDir.mkdirs()
        grandChild2Dir.mkdirs()
        files.forEachIndexed { i, file ->
            writeStringToFile(file, "File " + (i + 1) + " called " + file.name)
            this.testDirectorySize += file.length()
        }
        return grandParentDir
    }

    @Test
    @Throws(Exception::class)
    fun testDirectorySize() {
        // Create temporary root directory for holding test directories
        val temporaryRootDir = temporaryDirectory.newFolder("tempRootDir")

        // Test for success scenario
        val dir = createSrcFilesForTest(temporaryRootDir, "dir")
        val dirInfo = FileUtil.DirectoryContentInformation.fromDirectory(dir)
        assertEquals(testDirectorySize, dirInfo.totalBytes, "Directory size is not as expected")
        assertEquals(5, dirInfo.numberOfSubdirectories, "Wrong number of subdirectories")
        assertEquals(6, dirInfo.numberOfFiles, "Wrong number of files")

        // Test for failure scenario by passing a file as an argument instead of a directory
        assertThrows(IOException::class.java) { FileUtil.DirectoryContentInformation.fromDirectory(File(dir, "file1.txt")) }
    }

    @Test
    @Throws(Exception::class)
    fun ensureFileIsDirectoryTest() {
        // Create temporary root directory for holding test directories
        val temporaryRootDir = temporaryDirectory.newFolder("tempRootDir")

        // Create test data
        val testDir = createSrcFilesForTest(temporaryRootDir, "testDir")

        // Test for file which exists but isn't a directory
        assertThrows(IOException::class.java) { FileUtil.ensureFileIsDirectory(File(testDir, "file1.txt")) }

        // Test for file which exists and is a directory
        FileUtil.ensureFileIsDirectory(File(testDir, "parent"))

        // Test for directory which doesn't exist, but can be created
        FileUtil.ensureFileIsDirectory(File(testDir, "parent2"))

        // Test for directory which doesn't exist, and cannot be created
        assertThrows(IOException::class.java) {
            FileUtil.ensureFileIsDirectory(
                File(
                    testDir.absolutePath + File.separator + "file1.txt" +
                        File.separator + "impossibleDir"
                )
            )
        }
    }

    @Test
    @Throws(Exception::class)
    fun listFilesTest() {
        // Create temporary root directory for holding test directories
        val temporaryRootDir = temporaryDirectory.newFolder("tempRootDir")

        // Create valid input
        val testDir = createSrcFilesForTest(temporaryRootDir, "testDir")
        val expectedChildren = ArrayList<File>()
        expectedChildren.add(File(testDir, "parent"))
        expectedChildren.add(File(testDir, "file1.txt"))

        val testDirChildren = FileUtil.listFiles(testDir)

        // Check that listFiles lists all files in the directory
        for (testDirChild in testDirChildren) {
            assertTrue(expectedChildren.contains(testDirChild))
        }
        assertEquals(expectedChildren.size.toLong(), testDirChildren.size.toLong())

        // Create invalid input
        assertThrows(IOException::class.java) { FileUtil.listFiles(File(testDir, "file1.txt")) }
    }

    @Test
    fun testFileNameNull() {
        assertThat(FileUtil.getFileNameAndExtension(null), CoreMatchers.nullValue())
    }

    @Test
    fun testFileNameEmpty() {
        assertThat(FileUtil.getFileNameAndExtension(""), CoreMatchers.nullValue())
    }

    @Test
    fun testFileNameNoDot() {
        assertThat(FileUtil.getFileNameAndExtension("abc"), CoreMatchers.nullValue())
    }

    @Test
    fun testFileNameNormal() {
        val fileNameAndExtension = FileUtil.getFileNameAndExtension("abc.jpg")
        assertThat(fileNameAndExtension!!.key, equalTo("abc"))
        assertThat(fileNameAndExtension.value, equalTo(".jpg"))
    }

    @Test
    fun testFileNameTwoDot() {
        val fileNameAndExtension = FileUtil.getFileNameAndExtension("a.b.c")
        assertThat(fileNameAndExtension!!.key, equalTo("a.b"))
        assertThat(fileNameAndExtension.value, equalTo(".c"))
    }

    @Test
    @Throws(IOException::class)
    fun fileSizeTest() {
        fun sizeFromDir(file: File) = FileUtil.DirectoryContentInformation.fromDirectory(file).totalBytes
        val temporaryRootDir = temporaryDirectory.newFolder("tempRootDir")

        assertThat("empty directory should have 0 size", sizeFromDir(temporaryRootDir), equalTo(0L))

        val textFile = File(temporaryRootDir, "tmp.txt")
        writeStringToFile(textFile, "Hello World")

        val expectedLength = "Hello World".length.toLong()
        assertThrows("fromDirectory fails on files", IOException::class.java) {
            sizeFromDir(
                textFile
            )
        }

        assertThat("Should return file lengths", sizeFromDir(temporaryRootDir), equalTo(expectedLength))
    }

    @Test
    fun filePrefixTest() {
        val temporaryRootDir = temporaryDirectory.newFolder("tempRootDir")
        val prefixFile = File(temporaryRootDir, "prefix")
        writeStringToFile(prefixFile, "Hello ")
        val fullFile = File(temporaryRootDir, "full")
        writeStringToFile(fullFile, "Hello World")
        assertEquals(FileUtil.FilePrefix.STRICT_PREFIX, isPrefix(prefixFile, fullFile))
        assertEquals(FileUtil.FilePrefix.STRICT_SUFFIX, isPrefix(fullFile, prefixFile))
    }

    @Test
    fun fileNotPrefixTest() {
        val temporaryRootDir = temporaryDirectory.newFolder("tempRootDir")
        val prefixFile = File(temporaryRootDir, "prefix")
        writeStringToFile(prefixFile, "Hi World")
        val fullFile = File(temporaryRootDir, "full")
        writeStringToFile(fullFile, "Hello World")
        assertEquals(FileUtil.FilePrefix.NOT_PREFIX, isPrefix(prefixFile, fullFile))
    }

    @Test
    fun fileEqualTest() {
        val temporaryRootDir = temporaryDirectory.newFolder("tempRootDir")
        val prefixFile = File(temporaryRootDir, "prefix")
        writeStringToFile(prefixFile, "Hello World")
        val fullFile = File(temporaryRootDir, "full")
        writeStringToFile(fullFile, "Hello World")
        assertEquals(FileUtil.FilePrefix.EQUAL, isPrefix(prefixFile, fullFile))
    }
}
