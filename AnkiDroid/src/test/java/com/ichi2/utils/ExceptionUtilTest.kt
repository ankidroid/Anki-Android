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
package com.ichi2.utils

import com.ichi2.utils.ExceptionUtil.containsCause
import com.ichi2.utils.ExceptionUtil.getExceptionMessage
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.core.Is.`is`
import org.junit.Test

@KotlinCleanup("is -> equalTo")
class ExceptionUtilTest {
    @Test
    fun exceptionMessageSingle() {
        val e = Exception("Hello")

        val message = getExceptionMessage(e)

        assertThat(message, `is`("Hello"))
    }

    @Test
    fun exceptionMessageNested() {
        val inner = Exception("Inner")
        val e = Exception("Hello", inner)

        val message = getExceptionMessage(e)

        assertThat(message, `is`("Hello\nInner"))
    }

    @Test
    fun exceptionMessageNull() {
        val message = getExceptionMessage(null)

        assertThat(message, `is`(""))
    }

    @Test
    fun exceptionMessageNestedNull() {
        // a single null should be displayed, a nested null shouldn't be
        val inner = Exception()
        val e = Exception("Hello", inner)

        val message = getExceptionMessage(e)

        assertThat(message, `is`("Hello"))
    }

    @Test
    fun containsCauseExact() {
        val ex: Exception = IllegalStateException()
        assertThat(containsCause(ex, IllegalStateException::class.java), `is`(true))
    }

    @Test
    fun containsCauseNested() {
        val ex = Exception(IllegalStateException())
        assertThat(containsCause(ex, IllegalStateException::class.java), `is`(true))
    }

    @Test
    fun containsCauseMissing() {
        val ex = Exception()
        assertThat(containsCause(ex, IllegalStateException::class.java), `is`(false))
    }

    @Test
    fun containsCauseMissingNested() {
        val ex = Exception(Exception())
        assertThat(containsCause(ex, IllegalStateException::class.java), `is`(false))
    }
}
