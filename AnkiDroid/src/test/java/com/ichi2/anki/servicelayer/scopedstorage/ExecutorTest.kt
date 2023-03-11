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
import com.ichi2.anki.servicelayer.scopedstorage.migrateuserdata.MigrateUserData.Executor
import com.ichi2.anki.servicelayer.scopedstorage.migrateuserdata.MigrateUserData.Operation
import com.ichi2.testutils.Flaky
import com.ichi2.testutils.OS
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.hasSize
import org.junit.Test
import org.mockito.kotlin.mock
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

// TODO: Remove [MockExecutor] and now use a 'real' executor in tests

/** Test for [Executor] */
class ExecutorTest {

    /** the system under test: no initial operations */
    private val underTest = Executor(ArrayDeque())

    /** execution context: allows access to the order of execution */
    private val executionContext = MockMigrationContext()

    /**
     * pass in two elements to the [Executor]: they should be executed in the same order.
     */
    @Test
    fun `Regular operations are executed in order of addition`() {
        val opOne = mock<Operation>(name = "opOne")
        val opTwo = mock<Operation>(name = "opTwo")

        underTest.appendAll(listOf(opOne, opTwo))

        underTest.execute(executionContext)

        assertThat("first operation should be executed first", executionContext.executed[0], equalTo(opOne))
        assertThat("second operation should be executed second", executionContext.executed[1], equalTo(opTwo))
    }

    @Test
    fun `Execution succeeds with only preempted tasks`() {
        val opOne = mock<Operation>(name = "opOne")
        val opTwo = mock<Operation>(name = "opTwo")

        underTest.preempt(opOne)
        underTest.preempt(opTwo)

        underTest.execute(executionContext)

        assertThat("first operation should be executed first", executionContext.executed[0], equalTo(opOne))
        assertThat("second operation should be executed second", executionContext.executed[1], equalTo(opTwo))
    }

    /**
     * pass in one to the [Executor], then [prepend][Executor.prepend] one: the prepended should be executed first
     */
    @Test
    fun `Prepend adds an operation to the start of the list`() {
        val opOne = mock<Operation>(name = "opOne")
        val opTwo = mock<Operation>(name = "opTwo")

        underTest.append(opOne)
        underTest.prepend(opTwo)

        underTest.execute(executionContext)

        assertThat("prepended operation should be executed first", executionContext.executed[0], equalTo(opTwo))
        assertThat("regular operation should be executed after prepended operation", executionContext.executed[1], equalTo(opOne))
    }

    /**
     * Pass in two elements. While the first element is being executed, preempt an element
     * The preempted element should be added before the second 'initial' element.
     */
    @Test
    @Flaky(os = OS.WINDOWS, "Index 2 out of bounds for length 2")
    fun `A preempted element is executed before a regular element`() {
        val opOne = BlockedOperation()
        val opTwo = mock<Operation>(name = "opTwo")

        val preemptedOp = mock<Operation>(name = "preemptedOp")

        underTest.appendAll(listOf(opOne, opTwo))

        // start executing (blocked on op 1)
        executeInDifferentThreadThenWaitForCompletion {
            opOne.isExecuting.acquireInTwoSeconds()
            underTest.preempt(preemptedOp)
            opOne.isBlocked.release()
        }

        assertThat("Initial operation should be executed first", executionContext.executed[0], equalTo(opOne))
        assertThat("Preemption should take priority over next over normal operation", executionContext.executed[1], equalTo(preemptedOp))
        assertThat("All operations should be executed", executionContext.executed[2], equalTo(opTwo))
    }

    /** add two preempted operations: terminate after the first and ensure that only one is executed */
    @Test
    fun `Termination does not continue executing preempted tasks`() {
        val blockingOp = BlockedOperation()
        val opTwo = mock<Operation>(name = "opTwo")

        underTest.preempt(blockingOp)
        underTest.preempt(opTwo)

        executeInDifferentThreadThenWaitForCompletion {
            blockingOp.isExecuting.acquireInTwoSeconds()
            underTest.terminate()
            blockingOp.isBlocked.release()
        }

        assertThat(executionContext.executed[0], equalTo(blockingOp))
        assertThat(
            "a preempted operation is not run if terminate() is called",
            executionContext.executed,
            hasSize(1)
        )
    }

    /** add two normal operations: terminate after the first and ensure that only one is executed */
    @Test
    fun `Termination does not continue executing regular tasks`() {
        val blockingOp = BlockedOperation()
        val opTwo = mock<Operation>(name = "opTwo")

        underTest.appendAll(listOf(blockingOp, opTwo))

        executeInDifferentThreadThenWaitForCompletion {
            blockingOp.isExecuting.acquireInTwoSeconds()
            underTest.terminate()
            blockingOp.isBlocked.release()
        }

        assertThat(executionContext.executed[0], equalTo(blockingOp))
        assertThat(
            "a regular operation is not run if terminate() is called",
            executionContext.executed,
            hasSize(1)
        )
    }

    /**
     * Executes the executor in one thread, and executes the provided lambda in the main thread
     * Timeout: one second, either the operation is completed, or an exception is thrown
     */
    private fun executeInDifferentThreadThenWaitForCompletion(f: (() -> Unit)) {
        Thread { underTest.execute(executionContext) }
            .apply {
                start()
                f()
                join(ONE_SECOND)
            }
    }

    /**
     * An operation which spins until [isBlocked] is set to false
     */
    class BlockedOperation : Operation() {
        // Semaphore that can be acquired once the operation is not blocked anymore
        val isBlocked = Semaphore(1).apply { acquire() }

        // Semaphore that can be acquired after operation start executing
        var isExecuting = Semaphore(1).apply { acquire() }
        override fun execute(context: MigrationContext): List<Operation> {
            isExecuting.release()
            isBlocked.acquireInTwoSeconds()
            return emptyList()
        }
    }

    companion object {
        private const val ONE_SECOND = 1000 * 1L
    }
}

private fun Semaphore.acquireInTwoSeconds() { this.tryAcquire(2, TimeUnit.SECONDS) }
