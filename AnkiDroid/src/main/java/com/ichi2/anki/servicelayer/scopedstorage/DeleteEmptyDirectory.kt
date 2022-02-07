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
import com.ichi2.compat.CompatHelper
import timber.log.Timber
import java.io.FileNotFoundException

data class DeleteEmptyDirectory(val directory: Directory) : MigrateUserData.Operation() {
    override fun execute(context: MigrateUserData.MigrationContext): List<MigrateUserData.Operation> {
        if (directory.hasFiles()) {
            context.reportError(this, MigrateUserData.DirectoryNotEmptyException(directory))
            return operationCompleted()
        }

        try {
            CompatHelper.getCompat().deleteFile(directory.directory)
            Timber.d("deleted $directory")
        } catch (ex: FileNotFoundException) { // API <26
            Timber.d("$directory already deleted")
        }

        return operationCompleted()
    }
}
