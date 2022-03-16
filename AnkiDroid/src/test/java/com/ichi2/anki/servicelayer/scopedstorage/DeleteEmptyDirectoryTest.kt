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

package com.ichi2.anki.servicelayer.scopedstorage

import android.os.Build
import androidx.annotation.RequiresApi
import com.ichi2.anki.model.Directory
import com.ichi2.compat.Compat
import com.ichi2.compat.Test21And26
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.hasSize
import org.hamcrest.Matchers.instanceOf
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.io.path.pathString

/**
 * Tests for [DeleteEmptyDirectory]
 */
@RequiresApi(Build.VERSION_CODES.O) // This requirement is necessary for compilation. However, it still allows to test CompatV21
@RunWith(Parameterized::class)
class DeleteEmptyDirectoryTest(
    override val compat: Compat,
    /** Used in the "Test Results" Window */
    @Suppress("unused") private val unitTestDescription: String
) : Test21And26(compat, unitTestDescription), OperationTest {

    override val executionContext = MockMigrationContext()

    @Test
    fun succeeds_if_directory_is_empty() {
        val toDelete = createEmptyDirectory()

        val next = DeleteEmptyDirectory(toDelete).execute(executionContext)

        assertThat("no exceptions", executionContext.exceptions, hasSize(0))
        assertThat("no more operations", next, hasSize(0))
    }

    @Test
    fun fails_if_directory_is_not_empty() {
        val toDelete = createEmptyDirectory()
        File(toDelete.directory, "aa.txt").createNewFile()

        executionContext.logExceptions = true
        val next = DeleteEmptyDirectory(toDelete).execute(executionContext)

        assertThat("exception expected", executionContext.exceptions, hasSize(1))
        assertThat(executionContext.exceptions.single(), instanceOf(MigrateUserData.DirectoryNotEmptyException::class.java))
        assertThat("no more operations", next, hasSize(0))
    }

    @Test
    fun succeeds_if_directory_does_not_exist() {
        val directory = createTempDirectory()
        val dir = File(directory.pathString)
        val toDelete = Directory.createInstance(dir)!!
        dir.delete()

        val next = DeleteEmptyDirectory(toDelete).execute(executionContext)

        assertThat("no exceptions", executionContext.exceptions, hasSize(0))
        assertThat("no more operations", next, hasSize(0))
    }

    /**
     * Reproduces https://github.com/ankidroid/Anki-Android/issues/10358
     * Where for some reason, `listFiles` returned null on an existing directory and
     * newDirectoryStream returned `AccessDeniedException`.
     */
    @Test
    fun reproduce_10358() {
        val permissionDenied = createPermissionDenied()
        permissionDenied.assertThrowsWhenPermissionDenied { DeleteEmptyDirectory(permissionDenied.directory).execute(executionContext) }
    }

    private fun createEmptyDirectory() =
        Directory.createInstance(File(createTempDirectory().pathString))!!
}
