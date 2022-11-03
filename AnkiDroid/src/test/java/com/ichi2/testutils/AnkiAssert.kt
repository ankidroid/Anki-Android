/*
 *  Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>
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
package com.ichi2.testutils

import com.ichi2.libanki.Card
import com.ichi2.libanki.sched.SchedV2
import com.ichi2.utils.KotlinCleanup
import com.ichi2.utils.ListUtil.Companion.assertListEquals
import org.junit.Assert
import kotlin.test.junit5.JUnit5Asserter

/** Assertion methods that aren't currently supported by our dependencies  */
object AnkiAssert {
    /** Helper to sort out "JUnit tests should include assert() or fail()" quality check  */
    fun assertDoesNotThrow(message: String, runnable: Runnable) {
        try {
            runnable.run()
        } catch (e: Exception) {
            throw AssertionError("$message\nmethod should not throw", e)
        }
    }

    /** Helper to sort out "JUnit tests should include assert() or fail()" quality check  */
    fun assertDoesNotThrow(runnable: Runnable) {
        try {
            runnable.run()
        } catch (e: Exception) {
            throw AssertionError("method should not throw", e)
        }
    }

    /** Helper to sort out "JUnit tests should include assert() or fail()" quality check  */
    // suspend variant of [assertDoesNotThrow]
    suspend fun assertDoesNotThrowSuspend(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            throw AssertionError("method should not throw", e)
        }
    }

    fun <T : Throwable?> assertThrows(r: Runnable, clazz: Class<T>): T {
        try {
            r.run()
            Assert.fail("Expected exception: " + clazz.simpleName + ". No exception thrown.")
        } catch (t: Throwable) {
            if (t.javaClass == clazz) {
                @Suppress("UNCHECKED_CAST")
                return t as T
            }
            if (t.message != null && t.message!!.startsWith("Expected exception: ")) {
                // We need to add a "throws" if we rethrow t, so fail with the same code.
                Assert.fail("Expected exception: " + clazz.simpleName + ". No exception thrown.")
            }
            throw AssertionError(
                "Expected '" + clazz.simpleName + "' got '" + t.javaClass.simpleName + "'",
                t
            )
        }
        throw IllegalStateException("unreachable")
    }

    fun <T> assertEqualsArrayList(expected: Array<T>, actual: List<T>?) {
        assertListEquals(expected.toList(), actual)
    }

    fun without_unicode_isolation(s: String): String {
        return s.replace("\u2068", "").replace("\u2069", "")
    }

    @KotlinCleanup("scope function")
    fun checkRevIvl(c: Card, targetIvl: Int): Boolean {
        val minMax = SchedV2._fuzzIvlRange(targetIvl)
        return minMax.first <= c.ivl && c.ivl <= minMax.second
    }
}

/** assertThrows, allowing for lambda shorthand
 *
 * ```kotlin
 * val exception = assertThrows<IllegalStateException> {
 *     foo()
 * }
 * ```
 *
 * @see TestException if a test-only exception is needed
 * */
inline fun <reified T : Throwable> assertThrows(r: Runnable): T =
    AnkiAssert.assertThrows(r, T::class.java)

/**
 * [assertThrows], accepting subclasses of the exception type
 *
 * ```kotlin
 * val exception = assertThrows<IllegalStateException> {
 *     foo()
 * }
 * ```
 *
 * @see TestException if a test-only exception is needed
 * */
inline fun <reified T : Throwable> assertThrowsSubclass(r: Runnable): T {
    try {
        r.run()
    } catch (t: Throwable) {
        // got the exception we want
        if (t is T) {
            return t
        }
        // We got an exception, but not the correct one
        throw AssertionError("Expected '" + T::class.simpleName + "' got '" + t.javaClass.simpleName + "'", t)
    }

    Assert.fail("Expected exception: " + T::class.simpleName + ". No exception thrown.")
    throw IllegalStateException("shouldn't reach here")
}

/** Asserts that the expression is `false` with an optional [message]. */
fun assertFalse(message: String? = null, actual: Boolean) {
    // This exists in JUnit, but we want to avoid JUnit as their `assertNotNull` does not use contracts
    // So, we want a method in a different namespace for `assertFalse`
    // JUnitAsserter doesn't contain it, so we add it in
    JUnit5Asserter.assertTrue(message, !actual)
}
