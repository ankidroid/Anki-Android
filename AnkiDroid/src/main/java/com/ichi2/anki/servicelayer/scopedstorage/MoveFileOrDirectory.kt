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

import com.ichi2.anki.model.Directory
import com.ichi2.anki.model.DiskFile
import com.ichi2.anki.servicelayer.scopedstorage.migrateuserdata.MigrateUserData.*
import com.ichi2.anki.servicelayer.scopedstorage.migrateuserdata.MigrateUserData.Operation
import com.ichi2.anki.servicelayer.scopedstorage.migrateuserdata.MoveDirectory
import com.ichi2.anki.servicelayer.scopedstorage.migrateuserdata.operationCompleted
import org.apache.commons.io.FileUtils.isSymlink
import timber.log.Timber
import java.io.File

/**
 * [MoveFileOrDirectory] checks whether a `File` object is actually a `File`, a `Directory` or something else.
 * The last case should not occur in AnkiDroid.
 * It then delegates to `MoveFile` or `MoveDirectory` the actual move.
 *
 *  Checking whether a `File` object is a file/directory requires an I/O operation, which means that
 *  it should not be done in a loop, as this would block preemption.
 *
 *  @param sourceFile is the file or directory to move
 *  @param destination the new path of the file or directory (not the directory containing it)
 *
 * @see [MoveDirectory]
 * @see [MoveFile]
 */
data class MoveFileOrDirectory(
    /** Source, known to exist */
    val sourceFile: File,
    /** Destination: known to exist */
    val destination: File,
) : Operation() {

    override fun execute(context: MigrationContext): List<Operation> {
        when {
            sourceFile.isFile -> {
                val fileToCreate = DiskFile.createInstanceUnsafe(sourceFile)
                return listOf(MoveFile(fileToCreate, destination))
            }
            sourceFile.isDirectory -> {
                if (isSymlink(sourceFile)) {
                    Timber.d("skipping symlink: $sourceFile")
                    return operationCompleted()
                }
                val directory = Directory.createInstanceUnsafe(sourceFile)
                return listOf(MoveDirectory(directory, destination))
            }
            else -> {
                if (!sourceFile.exists()) {
                    // probably already migrated
                    Timber.d("File no longer exists: $sourceFile")
                } else {
                    context.reportError(this, IllegalStateException("File was neither file nor directory '${sourceFile.canonicalPath}'"))
                }
            }
        }
        return operationCompleted()
    }
}
