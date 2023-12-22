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
import com.ichi2.anki.servicelayer.scopedstorage.migrateuserdata.MigrateUserData.Operation
import com.ichi2.testutils.TestException
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import kotlin.test.assertFailsWith

class MockMigrationContextTest {
    @Test
    fun retry_migration_context_test_retry() {
        // if the lambda assigned to "retry" is called
        var retryCalled = 0
        val context = RetryMigrationContext { retryCalled++ }
        val throwIfUsed = OperationWhichThrowsIfUsed()

        fun reportError() = context.reportError(throwIfUsed, TestException("test ex"))

        reportError() // retryCount is 0 during test. It increments to 1
        assertFailsWith<TestException> { reportError() }

        assertThat("retry should be called", retryCalled, equalTo(1))
    }

    class OperationWhichThrowsIfUsed : Operation() {
        override fun execute(context: MigrationContext): List<Operation> = throw TestException("should not be called")

        override val retryOperations: List<Operation>
            get() = listOf(OperationWhichThrowsIfUsed())
    }
}
