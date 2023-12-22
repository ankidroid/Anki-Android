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

import androidx.annotation.VisibleForTesting
import com.ichi2.anki.model.Directory
import com.ichi2.anki.model.DiskFile
import com.ichi2.anki.servicelayer.scopedstorage.migrateuserdata.MigrateUserData.*
import com.ichi2.anki.servicelayer.scopedstorage.migrateuserdata.operationCompleted
import com.ichi2.compat.CompatHelper
import com.ichi2.utils.FileUtil
import timber.log.Timber
import java.io.File

/**
 * [Operation] which safely moves a file at path `sourceFile` to path `destinationFile`.
 *
 * Note: thrown exceptions are passed to the context via [MigrationContext.reportError]
 *
 * @throws FileConflictException If the source and destination exist and are different
 * @throws EquivalentFileException sourceFile == destinationFile
 * @throws MissingDirectoryException if source or destination's directory is missing
 * @throws IllegalStateException File copy failed
 * @throws java.io.IOException Unknown file operation failed
 */
internal data class MoveFile(val sourceFile: DiskFile, val destinationFile: File) : Operation() {
    override fun execute(context: MigrationContext): List<Operation> {
        if (!sourceFile.file.exists()) {
            sourceDoesNotExists(context)
        } else if (destinationFile.exists()) {
            destinationExists(context)
        } else {
            normalMove(context)
        }
        return operationCompleted()
    }

    /**
     * Deals with non existing source.
     */
    private fun sourceDoesNotExists(context: MigrationContext) {
        ensureParentDirectoriesExist() // check to confirm nothing went wrong (SD card removal)
        Timber.d("no-op - source deleted: '$sourceFile'")
        // since the file was deleted, we can't know the size. Report 0 file size
        context.reportProgress(0)
    }

    /**
     * Assumes source and destination exists. Deals with this unexpected case in the following way:
     * * if both files are the same, according to canonical path, throws a EquivalentFileException
     * * if destination is a directory, report a a FileDirectoryConflictException
     * * if destination is a copy of the source, delete the source, as it's copied,
     * * if destination is a strict prefix of the source, delete the destination so that it can be copied entirely
     * * if destination seems to be distinct from source,
     *
     * @throws EquivalentFileException sourceFile == destinationFile
     * @throws MissingDirectoryException if source or destination's directory is missing
     */
    private fun destinationExists(context: MigrationContext) {
        if (sourceFile.file.canonicalPath == destinationFile.canonicalPath) {
            // Deletion is destructive if both files are the same
            throw EquivalentFileException(sourceFile.file, destinationFile)
        }

        if (destinationFile.isDirectory) {
            context.reportError(
                this,
                FileDirectoryConflictException(
                    sourceFile,
                    Directory.createInstanceUnsafe(destinationFile),
                ),
            )
            return
        }

        when (FileUtil.isPrefix(destinationFile, sourceFile.file)) {
            FileUtil.FilePrefix.EQUAL -> {
                // Both files exist and are identical. Delete source + report size
                context.execSafe(this) {
                    val fileSize = sourceFile.file.length()
                    deleteFile(sourceFile.file)
                    context.reportProgress(fileSize)
                }
            }
            FileUtil.FilePrefix.STRICT_PREFIX -> {
                val deletionSucceeded = destinationFile.delete()
                Timber.w("(conflict) Deleted partial file in destination. Deletion result: %b", deletionSucceeded)
                if (!deletionSucceeded) {
                    conflictingFileInDestination(context)
                } else {
                    normalMove(context)
                }
            }
            FileUtil.FilePrefix.STRICT_SUFFIX,
            FileUtil.FilePrefix.NOT_PREFIX,
            -> {
                conflictingFileInDestination(context)
            }
        }
    }

    // destination exists, and does NOT match content: throw an exception
    // this is intended to be handled by moving the file to a "conflict" directory
    private fun conflictingFileInDestination(context: MigrationContext) {
        // if the source file doesn't exist, but the destination does, we assume that the move
        // took place outside this "MoveFile" instance - possibly preempted by the
        // user requesting the file
        Timber.d("file already moved to $destinationFile")
        if (sourceFile.file.exists()) {
            context.reportError(
                this,
                FileConflictException(
                    sourceFile,
                    DiskFile.createInstance(destinationFile)!!,
                ),
            )
        }
    }

    /**
     * Deals with copying existing source to a non existing destination
     */
    private fun normalMove(context: MigrationContext) {
        // attempt a quick rename
        if (context.attemptRename) {
            if (sourceFile.renameTo(destinationFile)) {
                Timber.d("fast move successful from '$sourceFile' to '$destinationFile'")
                context.reportProgress(destinationFile.length())
                return
            } else {
                context.attemptRename = false
            }
        }

        // copy the file, and delete the source.
        // If the program crashes between "copy" and "delete", then the next time the operation is
        // run, the duplicate source file will be deleted
        copyFile(
            source = sourceFile.file,
            destination = destinationFile,
        )

        if (!destinationFile.exists()) {
            context.reportError(
                this,
                IllegalStateException("Failed to copy file to $destinationFile"),
            )
            return
        }

        // We've moved the file, so can delete the source file
        deleteFile(sourceFile.file)
        Timber.d("move successful from '$sourceFile' to '$destinationFile'")
        context.reportProgress(destinationFile.length())
    }

    /**
     * @throws MissingDirectoryException if source or destination's directory is missing
     */
    private fun ensureParentDirectoriesExist() {
        val exceptionBuilder = DirectoryValidator()
        exceptionBuilder.tryCreate("source - parent dir", sourceFile.file.parentFile!!)
        exceptionBuilder.tryCreate("destination - parent dir", destinationFile.parentFile!!)
        exceptionBuilder.throwIfNecessary()
    }

    @VisibleForTesting
    internal fun copyFile(
        source: File,
        destination: File,
    ) {
        Timber.d("copying: $source to $destination")
        CompatHelper.compat.copyFile(source.canonicalPath, destination.canonicalPath)
    }

    @VisibleForTesting
    internal fun deleteFile(file: File) {
        Timber.d("deleting '$file'")
        CompatHelper.compat.deleteFile(file)
    }
}
