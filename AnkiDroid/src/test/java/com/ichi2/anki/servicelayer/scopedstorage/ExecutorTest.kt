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

import android.annotation.SuppressLint
import com.ichi2.anki.servicelayer.scopedstorage.MigrateUserData.Executor
import com.ichi2.anki.servicelayer.scopedstorage.MigrateUserData.Operation
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.hasSize
import org.junit.Test
import org.mockito.kotlin.mock

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
        val opOne = mock<Operation>()
        val opTwo = mock<Operation>()

        underTest.appendAll(listOf(opOne, opTwo))

        underTest.execute(executionContext)

        assertThat("first operation should be executed first", executionContext.executed[0], equalTo(opOne))
        assertThat("second operation should be executed second", executionContext.executed[1], equalTo(opTwo))
    }

    @Test
    fun `Execution succeeds with only preempted tasks`() {
        val opOne = mock<Operation>()
        val opTwo = mock<Operation>()

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
        val opOne = mock<Operation>()
        val opTwo = mock<Operation>()

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
    fun `A preempted element is executed before a regular element`() {
        val opOne = BlockedOperation()
        val opTwo = mock<Operation>()

        val preemptedOp = mock<Operation>()

        underTest.appendAll(listOf(opOne, opTwo))

        // start executing (blocked on op 1)
        executeInDifferentThreadThenWaitForCompletion {
            spinUntil { opOne.isExecuting }
            underTest.preempt(preemptedOp)
            opOne.isBlocked = false
        }

        assertThat("Initial operation should be executed first", executionContext.executed[0], equalTo(opOne))
        assertThat("Preemption should take priority over next over normal operation", executionContext.executed[1], equalTo(preemptedOp))
        assertThat("All operations should be executed", executionContext.executed[2], equalTo(opTwo))
    }

    /** add two preempted operations: terminate after the first and ensure that only one is executed */
    @Test
    fun `Termination does not continue executing preempted tasks`() {
        val blockingOp = BlockedOperation()
        val opTwo = mock<Operation>()

        underTest.preempt(blockingOp)
        underTest.preempt(opTwo)

        executeInDifferentThreadThenWaitForCompletion {
            spinUntil { blockingOp.isExecuting }
            underTest.terminate()
            blockingOp.isBlocked = false
        }

        assertThat(executionContext.executed[0], equalTo(blockingOp))
        assertThat(
            "a preempted operation is not run if terminate() is called",
            executionContext.executed, hasSize(1)
        )
    }

    /** add two normal operations: terminate after the first and ensure that only one is executed */
    @Test
    fun `Termination does not continue executing regular tasks`() {
        val blockingOp = BlockedOperation()
        val opTwo = mock<Operation>()

        underTest.appendAll(listOf(blockingOp, opTwo))

        executeInDifferentThreadThenWaitForCompletion {
            spinUntil { blockingOp.isExecuting }
            underTest.terminate()
            blockingOp.isBlocked = false
        }

        assertThat(executionContext.executed[0], equalTo(blockingOp))
        assertThat(
            "a regular operation is not run if terminate() is called",
            executionContext.executed, hasSize(1)
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
        var isBlocked = true
        var isExecuting = false
        override fun execute(context: MigrateUserData.MigrationContext): List<Operation> {
            this.isExecuting = true
            spinUntil { !isBlocked }
            return emptyList()
        }
    }

    companion object {
        private const val ONE_SECOND = 1000 * 1L
    }
}

/** Spins until the provided function is true */
@SuppressLint("DirectSystemCurrentTimeMillisUsage")
private fun spinUntil(f: (() -> Boolean)) {
    val timeoutMillis = 2000
    val startTime = System.currentTimeMillis()
    while (!f()) {
        // spin eternally: SPIN SPIN SPIN SPIN SPIN SPIN
        if (System.currentTimeMillis() - startTime > timeoutMillis) {
            throw IllegalStateException("spun until $timeoutMillis")
        }
    }
}
