// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.testutil

import org.junit.Test
import org.junit.runner.Description
import org.junit.runners.model.MultipleFailureException
import org.junit.runners.model.Statement
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame

class TestInputMethodRuleTest {
    @Test
    fun testFailureSurvivesSuccessfulCleanup() {
        val shell = KeyboardShell()
        val original = AssertionError("test failed")

        val failure = assertFailsWith<AssertionError> { shell.evaluate { throw original } }

        assertSame(original, failure)
        assertEquals(shell.previous, shell.selected)
        assertNull(shell.enabledIme)
    }

    @Test
    fun testAndBothCleanupFailuresAreReported() {
        val original = AssertionError("test failed")
        val restoreFailure = IllegalStateException("restore failed")
        val disableFailure = IllegalStateException("disable failed")
        val shell = KeyboardShell(restoreFailure, disableFailure)

        val failure = assertFailsWith<MultipleFailureException> { shell.evaluate { throw original } }

        assertEquals(listOf(original, restoreFailure, disableFailure), failure.failures)
    }

    /** Models keyboard selection without changing the emulator's keyboard. */
    private class KeyboardShell(
        private val restoreFailure: Throwable? = null,
        private val disableFailure: Throwable? = null,
    ) {
        val previous = "previous.ime/.Keyboard"
        var selected = previous
        var enabledIme: String? = null

        fun evaluate(block: () -> Unit) {
            val statement =
                object : Statement() {
                    override fun evaluate() = block()
                }
            TestInputMethodRule(::execute).apply(statement, Description.EMPTY).evaluate()
        }

        private fun execute(command: String): String {
            when {
                command == "settings get secure default_input_method" -> return selected
                command == "ime list -s" -> return listOfNotNull(previous, enabledIme).joinToString("\n")
                command.startsWith("ime enable ") -> enabledIme = command.removePrefix("ime enable ")
                command.startsWith("ime set ") -> {
                    val ime = command.removePrefix("ime set ")
                    if (ime == previous) restoreFailure?.let { throw it }
                    selected = ime
                }
                command.startsWith("ime disable ") -> {
                    disableFailure?.let { throw it }
                    enabledIme = null
                }
                else -> error("Unexpected shell command: $command")
            }
            return ""
        }
    }
}
