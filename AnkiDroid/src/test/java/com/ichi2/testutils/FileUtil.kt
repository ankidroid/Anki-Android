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

package com.ichi2.testutils

import com.ichi2.anki.model.Directory
import com.ichi2.anki.model.DiskFile
import org.acra.util.IOUtils
import java.io.File
import java.util.*

object FileUtil {
    /**
     * Reads the single line of content in a file
     * @return The single line of the file, as a string
     * @throws IllegalArgumentException if file has more than one line
     * @throws IllegalArgumentException if file does not exist
     * @throws NoSuchElementException file has no lines
     * */
    fun readSingleLine(
        base: File,
        vararg path: String,
    ): String {
        var file = base
        for (pathSegment in path) {
            file = File(file, pathSegment)
        }
        if (!file.exists()) {
            throw IllegalArgumentException("path: $file does not exist")
        }

        return readAllLines(file).single()
    }

    /**
     * Sequence of the lines of file
     */
    private fun readAllLines(file: File) =
        sequence {
            Scanner(file).use { scanner ->
                while (scanner.hasNextLine()) {
                    yield(scanner.nextLine().toString())
                }
            }
        }
}

fun DiskFile.length(): Long = this.file.length()

fun Directory.exists(): Boolean = this.directory.exists()

/** Adds a file to the directory with the provided name and content */
fun File.withTempFile(
    fileName: String,
    content: String = "default content",
): File {
    this.addTempFile(fileName, content)
    return this
}

/** Adds a file to the directory with the provided name and content. Return the new file. */
fun File.addTempFile(
    fileName: String,
    content: String = "default content",
): File {
    return File(this, fileName).also {
        IOUtils.writeStringToFile(it, content)
        it.deleteOnExit()
    }
}

/** Adds a directory to the directory with the provided name and content. Return the new directory. */
fun File.addTempDirectory(directoryName: String): Directory {
    val dir =
        File(this, directoryName).also {
            it.mkdir()
            it.deleteOnExit()
        }
    return Directory.createInstance(dir)!!
}

/** Adds a file to the directory with the provided name and content */
fun Directory.withTempFile(
    fileName: String,
    content: String = "default content",
): Directory {
    this.directory.withTempFile(fileName, content)
    return this
}
