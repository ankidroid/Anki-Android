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

import org.hamcrest.MatcherAssert.*
import org.hamcrest.Matchers.*
import org.junit.Assert
import org.junit.Test
import java.lang.AssertionError
import java.lang.IllegalArgumentException
import java.lang.IllegalStateException

class AnkiAssertTest {
    // We're not going to use the class under test to verify that these are working.
    @Test
    fun assertThrowsNoException() {
        try {
            AnkiAssert.assertThrows({}, IllegalStateException::class.java)
            Assert.fail("No exception thrown")
        } catch (e: AssertionError) {
            assertThat(e.message, containsString("Expected exception: IllegalStateException"))
            assertThat(e.message, containsString("No exception thrown"))
        }
    }

    @Test
    fun assertThrowsWrongException() {
        val toThrow = IllegalArgumentException()
        try {
            AnkiAssert.assertThrows({ throw toThrow }, IllegalStateException::class.java)
            Assert.fail("No exception thrown")
        } catch (e: AssertionError) {
            assertThat(e.message, containsString("Expected 'IllegalStateException' got 'IllegalArgumentException'"))
            assertThat(e.cause, notNullValue())
            assertThat(e.cause, sameInstance(toThrow))
        }
    }

    @Test
    fun assertThrowsSameException() {
        val ex = IllegalStateException()

        val exception = AnkiAssert.assertThrows({ throw ex }, IllegalStateException::class.java)

        assertThat(exception, sameInstance(ex))
    }
}
