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

import androidx.annotation.CheckResult
import androidx.annotation.VisibleForTesting
import com.ichi2.anki.model.Directory
import com.ichi2.anki.model.DiskFile
import com.ichi2.anki.model.RelativeFilePath
import com.ichi2.anki.servicelayer.scopedstorage.migrateuserdata.MigrateUserData.*
import com.ichi2.anki.servicelayer.scopedstorage.migrateuserdata.NumberOfBytes
import com.ichi2.anki.servicelayer.scopedstorage.migrateuserdata.operationCompleted
import com.ichi2.compat.CompatHelper
import java.io.File

/**
 * Moves a file from [sourceFile] to [proposedDestinationFile].
 *
 * Ensures that the folder underneath [proposedDestinationFile] exists, and renames the destination file.
 * if the file at [proposedDestinationFile] exists and has a distinct content, it will increment the filename, attempting to find a filename which is not in use.
 * if the file at [proposedDestinationFile] exists and has the same content, [sourceFile] is deleted and move is assumed to be successful.
 *
 * This does not handle other exceptions, such as a file existing as a directory in the relative path.
 *
 * Throws [FileConflictResolutionFailedException] if [proposedDestinationFile] exists, as do all
 * the other candidate destination files for conflict resolution.
 * This is unlikely to occur, and is expected to represent an application logic error.
 *
 * @see MoveFile for the definition of move
 */
/* In AnkiDroid planned use, proposedDestinationFile is assumed to be topLevel/conflict/relativePathOfSourceFile */
class MoveConflictedFile private constructor(
    val sourceFile: DiskFile,
    val proposedDestinationFile: File,
) : Operation() {

    override fun execute(context: MigrationContext): List<Operation> {
        // create the "conflict" folder if it didn't exist, and the relative path to the file
        // example: "AnkiDroid/conflict/collection.media/subfolder"
        createDirectory(proposedDestinationFile.parentFile!!)

        // wrap the context so we can handle internal file conflict exceptions, and set the correct
        // 'operation' if an error occurs.
        val wrappedContext = ContextHandlingFileConflictException(context, this)
        // loop from "filename.ext", then "filename (1).ext" to "filename (${MAX_RENAMES - 1}).ext" to ensure we transfer the file
        for (potentialDestinationFile in queryCandidateFilenames(proposedDestinationFile)) {
            return try {
                moveFile(potentialDestinationFile, wrappedContext)
                if (wrappedContext.handledFileConflictSinceLastReset) {
                    wrappedContext.reset()
                    continue // we had a conflict, try the next name
                }
                // the operation completed, with or without an error report. Don't try again
                operationCompleted()
            } catch (ex: Exception) {
                // We had an exception not handled by Operation,
                // or one that ContextHandlingFileConflictException decided to re-throw.
                // Don't try a different name
                wrappedContext.reportError(this, ex)
                operationCompleted()
            }
        }

        throw FileConflictResolutionFailedException(sourceFile, proposedDestinationFile)
    }

    @VisibleForTesting
    internal fun moveFile(potentialDestinationFile: File, wrappedContext: ContextHandlingFileConflictException) {
        MoveFile(sourceFile, potentialDestinationFile).execute(wrappedContext)
    }

    private fun createDirectory(folder: File) = CompatHelper.compat.createDirectories(folder)

    companion object {
        const val CONFLICT_DIRECTORY = "conflict"

        /**
         * @param sourceFile The file to move from
         * @param destinationTopLevel The top level directory to move to (non-relative path). "/storage/emulated/0/AnkiDroid/"
         * @param sourceRelativePath The relative path of the file. Does not start with /conflict/.
         * Is a suffix of [sourceFile]'s path: "/collection.media/image.jpg"
         */
        fun createInstance(
            sourceFile: DiskFile,
            destinationTopLevel: Directory,
            sourceRelativePath: RelativeFilePath,
        ): MoveConflictedFile {
            // we add /conflict/ to the path inside this method. If this already occurred, something was wrong
            if (sourceRelativePath.path.firstOrNull() == CONFLICT_DIRECTORY) {
                throw IllegalStateException("can't move from a root path of 'conflict': $sourceRelativePath")
            }

            val conflictedPath = sourceRelativePath.unsafePrependDirectory(CONFLICT_DIRECTORY)

            val destinationFile = conflictedPath.toFile(baseDir = destinationTopLevel)

            return MoveConflictedFile(sourceFile, destinationFile)
        }

        @VisibleForTesting
        @CheckResult
        internal fun queryCandidateFilenames(templateFile: File) = sequence {
            yield(templateFile)

            // examples from a file named: "helloWorld.tmp". the dot between name and extension isn't included
            val filename = templateFile.nameWithoutExtension // 'helloWorld'
            val extension = templateFile.extension // 'tmp'
            for (i in 1 until MAX_DESTINATION_NAMES) { // 1..4
                val newFileName = "$filename ($i).$extension" // 'helloWorld (1).tmp'
                yield(File(templateFile.parent, newFileName))
            }
        }

        /**
         * The max number of attempts to rename a file
         *
         * "filename.ext", then "filename (1).ext" ... "filename (4).ext"
         * where 4 = MAX_DESTINATION_NAMES - 1
         */
        const val MAX_DESTINATION_NAMES = 5
    }

    /**
     * Wrapper around [MigrateUserData.MigrationContext].
     * Ignores [FileConflictException], behaves as [MigrateUserData.MigrationContext] otherwise.
     * Reports errors using the provided [operation]
     */
    class ContextHandlingFileConflictException(
        private val wrappedContext: MigrationContext,
        private val operation: Operation,
    ) : MigrationContext() {

        /** Whether at least one [FileConflictException] was handled and ignored */
        var handledFileConflictSinceLastReset = false

        /** Resets the status of [handledFileConflictSinceLastReset] */
        fun reset() {
            handledFileConflictSinceLastReset = false
        }

        override fun reportError(throwingOperation: Operation, ex: Exception) {
            if (ex is FileConflictException || ex is FileDirectoryConflictException) {
                handledFileConflictSinceLastReset = true
                return
            }

            // report error using the operation passed into the ContextHandlingFileConflictException
            // (MoveConflictedFile)
            wrappedContext.reportError(operation, ex)
        }

        override fun reportProgress(transferred: NumberOfBytes) = wrappedContext.reportProgress(transferred)
    }
}
