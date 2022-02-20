/*
 *  Copyright (c) 2022 Arthur Milchior <Arthur@Milchior.fr>
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

package com.ichi2.compat

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.testutils.createTransientDirectory
import com.ichi2.testutils.createTransientFile
import com.ichi2.testutils.withTempFile
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.not
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [21, 26])
class DirectoryContentTest {
    @Test
    fun empty_dir_test() {
        val directory = createTransientDirectory()
        CompatHelper.getCompat().contentOfDirectory(directory)!!.use {
            assertThat("Iterator should not have next", it.hasNext(), equalTo(false))
        }
    }

    @Test
    fun ensure_absolute_path() {
        // Relative paths caused me hours of debugging. Never again.
        val directory = createTransientDirectory()
            .withTempFile("zero")
        val iterator = CompatHelper.getCompat().contentOfDirectory(directory)!!
        val file = iterator.next()
        assertThat("Paths should be canonical", file.path, equalTo(file.canonicalPath))
    }

    @Test
    fun dir_test_three_files() {
        val directory = createTransientDirectory()
            .withTempFile("zero")
            .withTempFile("one")
            .withTempFile("two")
        val iterator = CompatHelper.getCompat().contentOfDirectory(directory)!!
        val found = Array(3) { false }
        for (i in 1..3) {
            assertThat("Iterator should have a $i-th element", iterator.hasNext(), equalTo(true))
            val file = iterator.next()
            val fileNumber = when (file.name) {
                "zero" -> 0
                "one" -> 1
                "two" -> 2
                else -> -1
            }
            assertThat("File ${file.name} should not be in ${directory.path}", fileNumber, not(equalTo(-1)))
            assertThat("File ${file.name} should not be listed twice", found[fileNumber], equalTo(false))
            found[fileNumber] = true
        }
        assertThat("Iterator should not have next anymore", iterator.hasNext(), equalTo(false))
        iterator.close()
    }

    @Test
    fun non_existent_dir_test() {
        val directory = createTransientDirectory()
        directory.delete()
        assertThat(
            "for non existent file, we expect nul",
            CompatHelper.getCompat().contentOfDirectory(directory), equalTo(null)
        )
    }

    @Test
    fun file_test() {
        val file = createTransientFile("foo")
        file.delete()
        assertThat(
            "for file which is not a folder, we expect null",
            CompatHelper.getCompat().contentOfDirectory(file), equalTo(null)
        )
    }
}
