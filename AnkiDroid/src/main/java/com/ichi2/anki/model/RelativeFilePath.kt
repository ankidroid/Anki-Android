/*
 *  Copyright (c) 2022 David Allison <davidallisongithub@gmail.com>
 *  Copyright (c) 2022 Arthur Milchior <arthur@milchior.fr>
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

import java.io.File
import java.nio.file.Path

/**
 * A relative path, with the final component representing the filename.
 * During a recursive copy of a folder `source` to `destination`, this relative file path `relative`
 * can be used both on top of source and destination folder to get the path of `source/relative`
 * and `destination/relative`.
 * It can also be used to move `source/relative` to `source/conflict/relative` in case of conflict.
 */
class RelativeFilePath private constructor(
    /** Relative path, as a sequence of directory, excluding the file name. */
    val path: List<String>,
    val fileName: String,
) {
    /**
     * Combination of [baseDir] and this relative Path.
     */
    fun toFile(baseDir: Directory): File {
        var directory = baseDir.directory
        for (dirName in path) {
            directory = File(directory, dirName)
        }
        return File(directory, fileName)
    }

    /**
     * Adds a directory named [directoryName] to the start of [path]
     *
     * This is unsafe as it does not check for directory escapes or invalid characters.
     * Should only be supplied with constants
     *
     * Sample:
     * ```
     * "/foo/bar/baz.txt".unsafePrependDirectory("quz") = "/quz/foo/bar/baz.txt"
     * ```
     */
    fun unsafePrependDirectory(directoryName: String): RelativeFilePath {
        return RelativeFilePath(listOf(directoryName) + path, fileName)
    }

    companion object {

        /**
         * Return the relative path from Folder [baseDir] to file [file]. If [file]
         * is contained in [baseDir], return `null`.
         * Similar to [Path.relativize], but available in all APIs.
         */
        fun fromPaths(baseDir: Directory, file: DiskFile): RelativeFilePath? =
            fromPaths(baseDir.directory, file.file)
        fun fromPaths(baseDir: File, file: File): RelativeFilePath? =
            fromCanonicalFiles(baseDir.canonicalFile, file.canonicalFile)

        /**
         * Return the relative path from Folder [baseDir] to file [file]. If [file]
         * is contained in [baseDir], return `null`.
         * Assumes that [file] is actually a file and [baseDir] a directory, hence distinct.
         * Similar to [Path.relativize], but available in all APIs.
         * @param baseDir A directory.
         * @param file some file, assumed to be contained in baseDir.
         */
        private fun fromCanonicalFiles(baseDir: File, file: File): RelativeFilePath? {
            val name = file.name
            val directoryPath = mutableListOf<String>()
            var mutablePath = file.parentFile
            while (mutablePath != baseDir) {
                if (mutablePath == null) {
                    // File was not inside the directory
                    return null
                }
                directoryPath.add(mutablePath.name)
                mutablePath = mutablePath.parentFile
            }
            // attempt to create a relative file path
            return RelativeFilePath(directoryPath.reversed(), name)
        }
    }
}
