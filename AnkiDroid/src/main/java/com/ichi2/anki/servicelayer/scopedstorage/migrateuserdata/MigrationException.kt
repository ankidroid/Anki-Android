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
import com.ichi2.anki.model.DiskFile
import java.io.File

/**
 * Exceptions that are expected to occur during migration, and that we can deal with.
 */
open class MigrationException(message: String) : RuntimeException(message)

/**
 * If a file exists in [destination] with different content than [source]
 *
 * If a file named `filename` exists in [destination] and in [source] with different content, move `source/filename` to `source/conflict/filename`.
 */
class FileConflictException(val source: DiskFile, val destination: DiskFile) : MigrationException("File $source can not be copied to $destination, destination exists and differs.")

/**
 * If [destination] is a directory. In this case, move `source/filename` to `source/conflict/filename`.
 */
class FileDirectoryConflictException(val source: DiskFile, val destination: Directory) : MigrationException("File $source can not be copied to $destination, as destination is a directory.")

/**
 * If one or more required directories were missing
 */
class MissingDirectoryException(val directories: List<MissingFile>) : MigrationException("Directories $directories are missing.") {
    init {
        if (directories.isEmpty()) {
            throw IllegalArgumentException("directories should not be empty")
        }
    }

    /**
     * A file which should exist, but did not
     * @param source The variable name/identifier of the file
     * @param file A [File] reference to the missing file
     */
    data class MissingFile(val source: String, val file: File)
}

/**
 * If during a file move, two files refer to the same path
 * This implies that the file move should be cancelled
 */
class EquivalentFileException(val source: File, val destination: File) : MigrationException("Source and destination path are the same")

/**
 * If a directory could not be deleted as it still contained files.
 */
class DirectoryNotEmptyException(val directory: Directory) : MigrationException("directory was not empty: $directory")

/**
 * If the number of retries was exceeded when resolving a file conflict via moving it to the
 * /conflict/ folder.
 */
class FileConflictResolutionFailedException(val sourceFile: DiskFile, val attemptedDestination: File) : MigrationException("Failed to move $sourceFile to $attemptedDestination")
