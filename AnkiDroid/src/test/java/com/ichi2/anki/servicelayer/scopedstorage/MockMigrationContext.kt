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

import com.ichi2.anki.servicelayer.scopedstorage.migrateuserdata.MigrateUserData.*
import com.ichi2.anki.servicelayer.scopedstorage.migrateuserdata.MigrateUserData.MigrationContext
import com.ichi2.anki.servicelayer.scopedstorage.migrateuserdata.MigrateUserData.Operation
import com.ichi2.anki.servicelayer.scopedstorage.migrateuserdata.NumberOfBytes

open class MockMigrationContext : MigrationContext() {
    /** set [logExceptions] to populate this property */
    val errors = mutableListOf<ReportedError>()
    val exceptions get() = errors.map { it.exception }
    var logExceptions: Boolean = false
    val progress = mutableListOf<NumberOfBytes>()

    /** A list of tasks which were passed into [execSafe] */
    val executed = mutableListOf<Operation>()

    override fun reportError(throwingOperation: Operation, ex: Exception) {
        if (!logExceptions) {
            throw ex
        }
        errors.add(ReportedError(throwingOperation, ex))
    }

    override fun reportProgress(transferred: NumberOfBytes) {
        progress.add(transferred)
    }

    data class ReportedError(val operation: Operation, val exception: Exception)

    override fun execSafe(
        operation: Operation,
        op: (Operation) -> Unit
    ) {
        this.executed.add(operation)
        super.execSafe(operation, op)
    }
}

/**
 * A [MockMigrationContext] which will call [Operation.retryOperations] once on
 * a failed operation, if any `retryOperations` are available.
 *
 * If a second failure occurs, or if no `retryOperations` are available, it will throw
 */
class RetryMigrationContext(val retry: (List<Operation>) -> Unit) : MockMigrationContext() {
    var retryCount = 0

    override fun reportError(throwingOperation: Operation, ex: Exception) {
        val opsForRetry = throwingOperation.retryOperations
        if (!opsForRetry.any()) {
            throw ex
        }
        retryCount++
        if (retryCount > 1) {
            throw ex
        }
        retry(opsForRetry)
    }

    override fun reportProgress(transferred: NumberOfBytes) {
    }
}
