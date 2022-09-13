/*
 *  Copyright (c) 2022 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki.model

import com.ichi2.compat.Test21And26
import com.ichi2.testutils.HamcrestUtils.containsInAnyOrder
import com.ichi2.testutils.assertThrows
import com.ichi2.testutils.withTempFile
import org.acra.util.IOUtils
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import java.io.File
import java.io.FileNotFoundException
import kotlin.io.path.createTempDirectory
import kotlin.io.path.pathString
import kotlin.test.*

/**
 * Tests for [Directory]
 */
class DirectoryTest : Test21And26() {
    @Test
    fun passes_if_existing_directory() {
        val path = createTempDirectory().pathString
        assertNotNull(
            Directory.createInstance(path),
            "Directory should work with valid directory",
        )
    }

    @Test
    fun fails_if_does_not_exist() {
        val subdirectory = File(createTempDirectory().pathString, "aa")
        assertNull(
            Directory.createInstance(subdirectory),
            "Directory requires an existing directory",
        )
    }

    @Test
    fun fails_if_file() {
        val dir = kotlin.io.path.createTempFile().pathString
        assertNull(
            Directory.createInstance(dir),
            "file should not become a Directory",
        )
    }

    @Test
    fun list_files_returns_valid_paths() {
        val dir = createValidTempDir()
            .withTempFile("foo.txt")
            .withTempFile("bar.xtx")
            .withTempFile("baz.xtx")

        val files = dir.listFiles()

        assertThat(
            "Directory should contain only three files",
            files.toList(),
            containsInAnyOrder(listOf("foo.txt", "bar.xtx", "baz.xtx").map { File(dir.directory, it) })
        )
    }

    @Test
    fun has_files_is_false_if_empty() {
        val dir = createValidTempDir()
        assertFalse(
            dir.hasFiles(),
            "empty directory should not have files",
        )
    }

    @Test
    fun has_files_throws_if_file_no_longer_exists() {
        val dir = createValidTempDir()
        dir.directory.delete()
        assertThrows<FileNotFoundException> { dir.hasFiles() }
    }

    @Test
    fun has_files_is_true_if_file() {
        val dir = createValidTempDir()
        IOUtils.writeStringToFile(File(dir.directory, "aa.txt"), "aa")
        assertTrue(
            dir.hasFiles(),
            "non-empty directory should have files",
        )
    }

    @Test
    fun test_create_from_string() {
        val path = File(createTempDirectory().pathString)

        val fromPath = Directory.createInstance(path.path)!!
        val fromFile = Directory.createInstance(path)!!

        assertEquals(fromFile.directory, fromPath.directory, "Equal result constructing from file or path")
    }

    /**
     * Reproduces https://github.com/ankidroid/Anki-Android/issues/10358
     * Where for some reason, `listFiles` returned null on an existing directory and
     * newDirectoryStream returned `AccessDeniedException`.
     */
    @Test
    fun reproduce_10358() {
        val permissionDenied = createPermissionDenied()
        permissionDenied.assertThrowsWhenPermissionDenied { permissionDenied.directory.hasFiles() }
    }

    private fun createValidTempDir(): Directory {
        return Directory.createInstance(createTempDirectory().pathString)!!
    }
}
