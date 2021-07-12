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

package com.ichi2.anki.analytics

import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.not
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test

class CriticalExceptionTest {

    @Test
    fun equalityIsBasedOnExceptionMessageIfNotSingleReport() {
        val a = Exception("Hello World")
        val b = Exception("Hello World")

        val ex1 = CriticalException.from(a)
        val ex2 = CriticalException.from(b)

        assertThat(ex1.getIssue().onlyReportOnce(), `is`(false))

        assertThat(ex1.hashCode(), equalTo(ex2.hashCode()))
        assertThat(ex1, equalTo(ex2))
    }

    @Test
    fun equalityFailureMessage() {
        val a = Exception("Hello World")
        val b = Exception("Hello a World")

        val ex1 = CriticalException.from(a)
        val ex2 = CriticalException.from(b)

        assertThat(ex1.getIssue().onlyReportOnce(), `is`(false))

        assertThat(ex1.hashCode(), not(equalTo(ex2.hashCode())))
        assertThat(ex1, not(equalTo(ex2)))
    }

    @Test
    fun equalityIsBasedOnIssueIfSingleReport() {
        val a = UnsatisfiedLinkError(".dynamic section header was not found 1")
        val b = UnsatisfiedLinkError(".dynamic section header was not found 2")

        val ex1 = CriticalException.from(a)
        val ex2 = CriticalException.from(b)

        assertThat(ex1.getIssue().onlyReportOnce(), `is`(true))

        assertThat(ex1.hashCode(), equalTo(ex2.hashCode()))
        assertThat(ex1, equalTo(ex2))
    }

    @Test
    fun equalityFailureIssue() {
        val a = UnsatisfiedLinkError(".dynamic section header was not found 1")
        val b = UnsatisfiedLinkError(".dynamic section header was not found 2")

        val ex1 = CriticalException.from(a)
        val ex2 = CriticalException.from(b)

        assertThat(ex1.getIssue().onlyReportOnce(), `is`(true))

        assertThat(ex1.hashCode(), equalTo(ex2.hashCode()))
        assertThat(ex1, equalTo(ex2))
    }
}
