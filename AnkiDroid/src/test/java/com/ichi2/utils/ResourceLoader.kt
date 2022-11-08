/*
 * Copyright (c) 2014 Houssam Salem <houssam.salem.au@gmail.com>
 * Copyright (c) 2021 David Allison <davidallisongithub@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.utils

import android.content.Context
import com.ichi2.libanki.Utils
import org.junit.Assert.assertTrue
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream

object ResourceLoader {
    /**
     * Copy a file from the application's assets directory and return the absolute path of that
     * copy.
     *
     * Files located inside the application's assets collection are not stored on the file
     * system and can not return a usable path, so copying them to disk is a requirement.
     */
    private fun getTempFilePath(context: Context, name: String, newName: String?): String {
        try {
            val inputStream: InputStream = context.classLoader.getResourceAsStream(name)
                ?: throw FileNotFoundException("Could not find test file: $name")
            val file = File(getTestDir(context, name), newName!!)
            val dst = file.absolutePath
            Utils.writeToFile(inputStream, dst)
            file.deleteOnExit()
            return dst
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
    }

    fun getTempCollection(context: Context, name: String): String {
        return getTempFilePath(context, name, "collection.anki2")
    }

    /**
     * @param name An additional suffix to ensure the test directory is only used by a particular resource.
     * @return A File object pointing to a directory in which temporary test files can be placed. The directory is
     * emptied on every invocation of this method so it is suitable to use at the start of each test.
     * Only add files (and not subdirectories) to this directory.
     */
    private fun getTestDir(context: Context, name: String): File {
        val suffix = if (name.isNotEmpty()) {
            "-$name"
        } else {
            ""
        }
        val dir = File(context.cacheDir, "testfiles$suffix")
        if (!dir.exists()) {
            assertTrue(dir.mkdir())
        }
        val files = dir.listFiles()
            ?: // Had this problem on an API 16 emulator after a stress test - directory existed
            // but listFiles() returned null due to EMFILE (Too many open files)
            // Don't throw here - later file accesses will provide a better exception.
            // and the directory exists, even if it's unusable.
            return dir
        for (f in files) {
            assertTrue(f.delete())
        }
        return dir
    }
}
