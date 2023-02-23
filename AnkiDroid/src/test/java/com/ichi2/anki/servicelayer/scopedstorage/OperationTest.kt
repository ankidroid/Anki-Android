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

import com.ichi2.anki.servicelayer.scopedstorage.migrateuserdata.MigrateUserData.*
import com.ichi2.anki.servicelayer.scopedstorage.migrateuserdata.MigrateUserData.Operation
import com.ichi2.compat.CompatHelper
import com.ichi2.testutils.TestException
import com.ichi2.testutils.createTransientDirectory
import org.mockito.invocation.InvocationOnMock
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.spy
import org.mockito.kotlin.whenever
import timber.log.Timber
import java.io.File

interface OperationTest {
    val executionContext: MockMigrationContext

    /** Helper function: executes an [Operation] and all sub-operations */
    fun executeAll(vararg ops: Operation) =
        MockExecutor(ArrayDeque(ops.toList())) { executionContext }
            .execute()

    /**
     * Executes an [Operation] without executing the sub-operations
     * @return the sub-operations returned from the execution of the operation
     */

    fun Operation.execute(): List<Operation> = this.execute(executionContext)

    /** Creates an empty TMP directory to place the output files in */
    fun generateDestinationDirectoryRef(): File {
        val createDirectory = createTransientDirectory()
        Timber.d("test: deleting $createDirectory")
        CompatHelper.compat.deleteFile(createDirectory)
        return createDirectory
    }

    /**
     * Allow to get a MoveDirectoryContent that works for at most three files and fail on the second one.
     * It keeps track of which files is the failed one and which are before and after; since directory can list its file in
     * any order, it ensure that we know which file failed. It also allows to test that move still occurs after a failure.
     */
    class SpyMoveDirectoryContent(private val moveDirectoryContent: MoveDirectoryContent) {
        /**
         * The first file moved, before the failed file. Null if no moved occurred.
         */
        var beforeFile: File? = null
            private set

        /**
         * The second file, it moves fails. Null if no moved occurred.
         */
        var failedFile: File? = null // ensure the second file fails
            private set

        /**
         * The last file moved, after the failed file. Null if no moved occurred.
         */
        var afterFile: File? = null
            private set
        var movesProcessed = 0
            private set

        fun toMoveOperation(op: InvocationOnMock): Operation {
            val sourceFile = op.arguments[0] as File
            when (movesProcessed++) {
                0 -> beforeFile = sourceFile
                1 -> {
                    failedFile = sourceFile
                    return FailMove()
                }
                2 -> afterFile = sourceFile
                else -> throw IllegalStateException("only 3 files expected")
            }
            return op.callRealMethod() as Operation
        }

        /**
         * The [MoveDirectoryContent] that performs the action mentioned in the class description.
         */
        val spy: MoveDirectoryContent
            get() = spy(moveDirectoryContent) {
                doAnswer { toMoveOperation(it) }.whenever(it).toMoveOperation(any())
            }
    }
}

/** A move operation which fails */
class FailMove : Operation() {
    override fun execute(context: MigrationContext): List<Operation> {
        throw TestException("should fail but not crash")
    }
}
