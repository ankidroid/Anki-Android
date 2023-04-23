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

package com.ichi2.exceptions

import com.ichi2.testutils.TestException
import com.ichi2.testutils.testExceptionWithStackTrace
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.instanceOf
import org.junit.Test
import kotlin.test.assertFailsWith

/**
 * Test for [AggregateException]
 */
class AggregateExceptionTest {
    @Test
    fun exceptionIfNoElementsProvided() {
        assertFailsWith<IllegalStateException> { AggregateException.raise("", emptyList()) }
    }

    @Test
    fun providedExceptionReturnedIfSingletonListProvided() {
        val expectedResult = TestException("a")
        val result = AggregateException.raise("", listOf(expectedResult))

        assertThat(result, equalTo(expectedResult))
    }

    @Test
    fun aggregateExceptionContainsAllExceptions() {
        val first = TestException("a")
        val second = TestException("b")
        val result = AggregateException.raise("[message]", listOf(first, second))

        assertThat(result, instanceOf(AggregateException::class.java))

        val asAggregateException = result as AggregateException

        assertThat(asAggregateException.message, containsString("[message]"))
        assertThat(asAggregateException.exceptions[0], equalTo(first))
        assertThat(asAggregateException.exceptions[1], equalTo(second))
    }

    @Test
    fun aggregateExceptionStackTrace() {
        val first = testExceptionWithStackTrace("[aa]")
        val second = testExceptionWithStackTrace("[bb]")
        val result = AggregateException.raise("message", listOf(first, second))

        val asAggregateException = result as AggregateException

        val stackTrace = asAggregateException.stackTraceToString()

        assertThat(stackTrace, containsString("[aa]"))
        assertThat(stackTrace, containsString("[bb]"))
        assertThat(stackTrace, containsString("message"))
        assertThat(stackTrace, containsString("testExceptionWithStackTrace"))
    }
}
