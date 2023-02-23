/*
 *  Copyright (c) 2022 Arthur Milchior <Arthur@milchior.fr>
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
import com.ichi2.anki.servicelayer.scopedstorage.migrateuserdata.MigrateUserData.MigrationContext
import com.ichi2.anki.servicelayer.scopedstorage.migrateuserdata.MigrateUserData.Operation
import com.ichi2.anki.servicelayer.scopedstorage.migrateuserdata.operationCompleted
import com.ichi2.compat.CompatHelper
import com.ichi2.compat.FileStream
import java.io.File
import java.io.IOException

/**
 * This operation moves content of source to destination. This Operation is called one time for each file in the directory, and one last time. Unless an exception occurs. Use the [createInstance] to instantiate the object.  It will convert the given Directory source to a FileStream. This conversion is a potentially long-running operation.
 * @param [source]: an iterator over File content, [source] will be closed in [execute] once the source is empty or if accessing its content raise an exception.
 * @param [destination]: a directory to copy the source content.
 * This is different than [MoveFile], [MoveDirectory] and [MoveFileOrDirectory] where destination is the new path of the copied element.
 * Because in this case, there is not a single destination path.
 */
class MoveDirectoryContent private constructor(val source: FileStream, val destination: File) : Operation() {
    companion object {
        /**
         * Return a [MoveDirectoryContent], moving the content of [source] to [destination].
         * Its running time is potentially proportional to the number of files in [source].
         * @param [source] a directory that should be moved
         * @param [destination] a directory, assumed to exists, to which [source] content will be transferred.
         * @throws [NoSuchFileException] if the file do not exists (starting at API 26)
         * @throws [java.nio.file.NotDirectoryException] if the file exists and is not a directory (starting at API 26)
         * @throws [FileNotFoundException] if the file do not exists (up to API 25)
         * @throws [IOException] if files can not be listed. On non existing or non-directory file up to API 25. This also occurred on an existing directory because of permission issue
         * that we could not reproduce. See https://github.com/ankidroid/Anki-Android/issues/10358
         * @throws [SecurityException] – If a security manager exists and its SecurityManager.checkRead(String) method denies read access to the directory
         */
        fun createInstance(source: Directory, destination: File): MoveDirectoryContent =
            MoveDirectoryContent(CompatHelper.compat.contentOfDirectory(source.directory), destination)
    }

    override fun execute(context: MigrationContext): List<Operation> {
        try {
            val hasNext = source.hasNext() // can throw an IOException
            if (!hasNext) {
                source.close()
                return operationCompleted()
            }
        } catch (e: IOException) {
            source.close()
            throw e
        }
        val nextFile = source.next() // Guaranteed not to throw since hasNext returned true.
        val moveNextFile = toMoveOperation(nextFile)
        val moveRemainingDirectoryContent = this
        return listOf(moveNextFile, moveRemainingDirectoryContent)
    }

    /**
     * @returns An operation to move file or directory [sourceFile] to [destination]
     */
    @VisibleForTesting
    internal fun toMoveOperation(sourceFile: File): Operation {
        return MoveFileOrDirectory(sourceFile, File(destination, sourceFile.name))
    }
}
