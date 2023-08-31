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
import com.ichi2.utils.ListUtil.Companion.assertListEquals
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

    fun <T> assertEqualsArrayList(expected: Array<T>, actual: List<T>?) {
        assertListEquals(expected.toList(), actual)
    }

    fun without_unicode_isolation(s: String): String {
        return s.replace("\u2068", "").replace("\u2069", "")
    }

    fun checkRevIvl(c: Card, targetIvl: Int): Boolean {
        val minMax = SchedV2._fuzzIvlRange(targetIvl)
        return c.ivl in minMax.first..minMax.second
    }
}

/** Asserts that the expression is `false` with an optional [message]. */
fun assertFalse(message: String? = null, actual: Boolean) {
    // This exists in JUnit, but we want to avoid JUnit as their `assertNotNull` does not use contracts
    // So, we want a method in a different namespace for `assertFalse`
    // JUnitAsserter doesn't contain it, so we add it in
    JUnit5Asserter.assertTrue(message, !actual)
}
