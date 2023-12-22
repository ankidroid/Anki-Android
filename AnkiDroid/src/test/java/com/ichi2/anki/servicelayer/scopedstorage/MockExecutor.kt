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

import com.ichi2.anki.servicelayer.scopedstorage.migrateuserdata.MigrateUserData.MigrationContext
import com.ichi2.anki.servicelayer.scopedstorage.migrateuserdata.MigrateUserData.Operation

/**
 * Functionality:
 *
 * * Execution of recursive tasks (folder copying etc..)
 *
 * This is a mock as it's not tested, but will eventually be moved to a real class with a slightly different API
 *
 * @param operations A collection of operations to be executed, to allow direct access in tests
 * @param contextSupplier A function providing context, used so [execute] only requires operations
 */
class MockExecutor(
    val operations: ArrayDeque<Operation> = ArrayDeque(),
    val contextSupplier: (() -> MigrationContext),
) {
    /**
     * Executes one, or a number of [operations][Operation].
     * Each operation in [operations] is executed after all previous operations
     * (and the operation they span) are completed.
     */
    fun execute(vararg operations: Operation) {
        this.operations.addAll(operations)
        val context = contextSupplier()
        while (this.operations.any()) {
            context.execSafe(this.operations.removeFirst()) {
                val replacements = it.execute(context)
                this.operations.addAll(0, replacements)
            }
        }
    }
}
