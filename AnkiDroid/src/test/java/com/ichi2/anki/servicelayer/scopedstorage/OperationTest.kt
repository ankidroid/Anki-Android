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

package com.ichi2.anki.servicelayer.scopedstorage

import com.ichi2.anki.model.Directory
import com.ichi2.anki.servicelayer.scopedstorage.MigrateUserData.Operation
import com.ichi2.compat.CompatHelper
import com.ichi2.testutils.createTransientDirectory
import timber.log.Timber
import java.io.File

interface OperationTest {
    val executionContext: MockMigrationContext

    /** Helper function: executes an [Operation] and all sub-operations */
    fun executeAll(op: Operation) {
        val ops = ArrayDeque<Operation>()
        ops.addFirst(op)
        while (ops.any()) {
            val head = ops.removeFirst()
            Timber.d("executing $head")
            this.executionContext.execSafe(head) {
                ops.addAll(0, head.execute())
            }
        }
    }

    /**
     * Executes an [Operation] without executing the sub-operations
     * @return the sub-operations returned from the execution of the operation
     */

    fun Operation.execute(): List<Operation> = this.execute(executionContext)

    /** Return a new empty Directory, which will be deleted after the test. */
    fun createDirectory(): Directory =
        Directory.createInstance(createTransientDirectory())!!

    /** Creates an empty TMP directory to place the output files in */
    fun generateDestinationDirectoryRef(): File {
        val createDirectory = createDirectory()
        Timber.d("test: deleting $createDirectory")
        CompatHelper.getCompat().deleteFile(createDirectory.directory)
        return createDirectory.directory
    }
}
