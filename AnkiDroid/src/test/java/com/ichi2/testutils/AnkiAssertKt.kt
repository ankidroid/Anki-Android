/*
 *  Copyright (c) 2021 David Allison <davidallisongithub@gmail.com>
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

import org.junit.Assert
import kotlin.test.junit5.JUnit5Asserter

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
