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
import com.ichi2.anki.servicelayer.scopedstorage.migrateuserdata.MigrateUserData.*
import com.ichi2.anki.servicelayer.scopedstorage.migrateuserdata.operationCompleted
import com.ichi2.compat.CompatHelper
import timber.log.Timber
import java.io.FileNotFoundException
import java.io.IOException

data class DeleteEmptyDirectory(val directory: Directory) : Operation() {
    override fun execute(context: MigrationContext): List<Operation> {
        val directoryContainsFiles =
            try {
                directory.hasFiles()
            } catch (ex: FileNotFoundException) {
                return noFile(ex)
            }
        if (directoryContainsFiles) {
            context.reportError(this, DirectoryNotEmptyException(directory))
            return operationCompleted()
        }

        try {
            CompatHelper.compat.deleteFile(directory.directory)
            Timber.d("deleted $directory")
        } catch (ex: FileNotFoundException) {
            Timber.d("$directory already deleted")
        }

        return operationCompleted()
    }

    private fun noFile(ex: IOException): List<Operation> {
        if (!directory.directory.exists()) {
            // If the directory is already deleted, the goal of the operation is reached,
            // hence we do not have to throw.
            // However, we could have obtained this exception during the directory deletion attempt, because the directory may have been deleted between the creation of [directory] and the execution of the operation.
            Timber.d("$directory already deleted")
            return operationCompleted()
        }
        throw ex
    }
}
