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

import androidx.annotation.VisibleForTesting
import com.ichi2.anki.model.Directory
import com.ichi2.anki.servicelayer.scopedstorage.DeleteEmptyDirectory
import com.ichi2.anki.servicelayer.scopedstorage.MoveDirectoryContent
import com.ichi2.anki.servicelayer.scopedstorage.migrateuserdata.MigrateUserData.MigrationContext
import com.ichi2.anki.servicelayer.scopedstorage.migrateuserdata.MigrateUserData.Operation
import com.ichi2.compat.CompatHelper
import timber.log.Timber
import java.io.File

/**
 * [Operation] which safely moves a directory at path `source` to path `destination`.
 * `destination` is the new path of the directory.
 *
 * Note: thrown exceptions are passed to the context via [MigrateUserData.MigrationContext.reportError]
 *
 */

data class MoveDirectory(val source: Directory, val destination: File) : Operation() {
    override fun execute(context: MigrationContext): List<Operation> {
        if (!createDirectory(context)) {
            return operationCompleted()
        }

        val moveContentOperations = MoveDirectoryContent.createInstance(source, destination)
        // If DeleteEmptyDirectory fails, retrying is executing the MoveDirectory that spawned it
        val deleteOperation = DeleteEmptyDirectory(source).onRetryExecute(this)
        return listOf(moveContentOperations, deleteOperation)
    }

    /**
     * Create an empty directory at destination.
     * Return whether it was successful.
     */
    internal fun createDirectory(context: MigrationContext): Boolean {
        Timber.d("creating directory '$destination'")
        createDirectory(destination)

        val destinationDirectory = Directory.createInstance(destination)
        if (destinationDirectory == null) {
            context.reportError(this, IllegalStateException("Directory instantiation failed: '$destination'"))
            return false
        }
        return true
    }

    /** Creates a directory if it doesn't already exist */
    @VisibleForTesting
    internal fun createDirectory(directory: File) = CompatHelper.compat.createDirectories(directory)

    @VisibleForTesting
    internal fun rename(source: Directory, destination: File) = source.renameTo(destination)
}
