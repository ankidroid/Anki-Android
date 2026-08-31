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

import org.acra.util.IOUtils.writeStringToFile
import org.hamcrest.CoreMatchers
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.core.IsEqual.equalTo
import org.junit.Assert.assertThrows
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.nio.file.Files
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
    @Throws(Exception::class)
    fun withFileNameSafe_validChild_returnsFile() {
        val parentDir = temporaryDirectory.newFolder("parent")
        val childFile = File(parentDir, "child.txt").apply { createNewFile() }
        val result = parentDir.withFileNameSafe(childFile.name)
        assertTrue(result.exists())
        assertEquals(childFile.absolutePath, result.absolutePath)
    }

    @Test
    @Throws(Exception::class)
    fun withFileNameSafe_pathTraversal_dotdot_throwsSecurityException() {
        val parentDir = temporaryDirectory.newFolder("parent")
        val traversalName = "../outside/secret.txt"
        assertThrows(SecurityException::class.java) {
            parentDir.withFileNameSafe(traversalName)
        }
    }

    @Test
    @Throws(Exception::class)
    fun withFileNameSafe_nestedTraversal_throwsSecurityException() {
        val parentDir = temporaryDirectory.newFolder("parent")
        // Test deeply nested traversal that escapes via multiple levels
        val deepTraversalName = "../../outside/secret.txt"
        assertThrows(SecurityException::class.java) {
            parentDir.withFileNameSafe(deepTraversalName)
        }
    }

    @Test
    @Throws(Exception::class)
    fun withFileNameSafe_encodedTraversal_staysWithinBounds() {
        val parentDir = temporaryDirectory.newFolder("parent")
        // On most filesystems, literal "..%2f" is not decoded as path separator.
        // The canonical check should confirm it stays within the parent directory.
        val encodedName = "..%2f..%2foutside.txt"
        val result = parentDir.withFileNameSafe(encodedName)
        assertTrue(result.canonicalPath.startsWith(parentDir.canonicalPath))
    }

    @Test
    @Throws(Exception::class)
    fun withFileNameSafe_nestedChild_returnsFile() {
        val rootDir = temporaryDirectory.newFolder("root")
        val deepParent = File(rootDir, "deep/nested/parent").apply { mkdirs() }
        val result = deepParent.withFileNameSafe("child.txt")
        assertEquals(File(deepParent, "child.txt").canonicalPath, result.canonicalPath)
    }

    @Test
    @Throws(Exception::class)
    fun withFileNameSafe_symlinkEscape_throwsSecurityException() {
        val parentDir = temporaryDirectory.newFolder("parent")
        val outsideDir = temporaryDirectory.newFolder("outside")
        File(outsideDir, "secret.txt").createNewFile()
        val link = File(parentDir, "escape")
        try {
            Files.createSymbolicLink(link.toPath(), outsideDir.toPath())
        } catch (e: Exception) {
            assumeTrue("Symbolic links are not supported: ${e.message}", false)
        }
        assumeTrue("Symbolic link was not created", Files.isSymbolicLink(link.toPath()))
        assertThrows(SecurityException::class.java) {
            parentDir.withFileNameSafe("escape/secret.txt")
        }
    }
}
