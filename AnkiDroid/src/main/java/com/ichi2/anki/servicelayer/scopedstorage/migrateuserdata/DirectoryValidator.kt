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

package com.ichi2.anki.servicelayer.scopedstorage.migrateuserdata

import com.ichi2.anki.model.Directory
import java.io.File

/**
 * Used to validate a number of [Directory] instances and produces a [MissingDirectoryException] with all
 * missing directories.
 *
 * Usage:
 * ```kotlin
 * val directoryValidator = DirectoryValidator()
 *
 * val sourceDirectory = directoryValidator.tryCreate(source)
 * val destinationDirectory = directoryValidator.tryCreate(destination)
 *
 * if (sourceDirectory == null || destinationDirectory == null) {
 *     throw directoryValidator.exceptionOnNullResult
 * }
 *
 * // `sourceDirectory` and `destinationDirectory` may now be used
 * ```
 *
 * Alternately (just validation without using values):
 * ```kotlin
 * val directoryValidator = DirectoryValidator()
 *
 * directoryValidator.tryCreate(source)
 * directoryValidator.tryCreate(destination)
 *
 * exceptionBuilder.throwIfNecessary()
 * ```
 */
class DirectoryValidator {
    /** Only valid if [tryCreate] returned null */
    val exceptionOnNullResult: MissingDirectoryException
        get() = MissingDirectoryException(failedFiles)

    /**
     * A list of files which failed to be created
     * Only valid if [tryCreate] returned null
     */
    private val failedFiles = mutableListOf<MissingDirectoryException.MissingFile>()

    /**
     * Tries to create a [Directory] object.
     *
     * If this returns null, [exceptionOnNullResult] should be thrown by the caller.
     * Example usages are provided in the [DirectoryValidator] documentation.
     *
     * @param [context] The "name" of the variable to test
     * @param [file] A file which may not point to a valid directory
     * @return A [Directory], or null if [file] did not point to an existing directory
     */
    fun tryCreate(context: String, file: File): Directory? {
        val ret = Directory.createInstance(file)
        if (ret == null) {
            failedFiles.add(MissingDirectoryException.MissingFile(context, file))
        }
        return ret
    }

    /**
     * If any directories were not created, throw a [MissingDirectoryException] listing the files
     * @throws MissingDirectoryException if any input files were invalid
     */
    fun throwIfNecessary() {
        if (failedFiles.any()) {
            throw MissingDirectoryException(failedFiles)
        }
    }
}
