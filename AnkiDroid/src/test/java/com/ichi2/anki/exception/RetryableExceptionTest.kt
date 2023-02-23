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

package com.ichi2.anki.exception

import com.ichi2.testutils.TestException
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import kotlin.test.assertFailsWith

/** Tests [RetryableException] */
class RetryableExceptionTest {
    var called = 0

    @Test
    fun non_throwing_function_works() {
        RetryableException.retryOnce { called++ }
        assertThat("function should only be called once", called, equalTo(1))
    }

    @Test
    fun conditionally_throwing_function_retries() {
        RetryableException.retryOnce { called++; if (called == 1) { throw RetryableException(TestException("throwing_function_retries")) } }
        assertThat("function should be called twice", called, equalTo(2))
    }

    @Test
    fun throwing_function_fails_with_inner() {
        val exception = assertFailsWith<TestException> {
            RetryableException.retryOnce { called++; throw RetryableException(TestException("throwing_function_retries")) }
        }
        assertThat("exception message is retained", exception.message, equalTo("throwing_function_retries"))
        assertThat("function should be called twice", called, equalTo(2))
    }
}
