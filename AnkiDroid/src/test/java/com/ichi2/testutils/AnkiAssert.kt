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
import java.util.*

/** Assertion methods that aren't currently supported by our dependencies  */
@KotlinCleanup("IDE Lint")
@KotlinCleanup("combine with AnkiAssertKt")
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
        assertListEquals(Arrays.asList(*expected), actual)
    }

    @JvmStatic
    fun without_unicode_isolation(s: String): String {
        return s.replace("\u2068", "").replace("\u2069", "")
    }

    @JvmStatic
    @KotlinCleanup("scope function")
    fun checkRevIvl(c: Card, targetIvl: Int): Boolean {
        val min_max = SchedV2._fuzzIvlRange(targetIvl)
        return min_max.first <= c.ivl && c.ivl <= min_max.second
    }
}
