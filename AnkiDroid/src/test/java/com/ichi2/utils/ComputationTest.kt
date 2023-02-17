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

package com.ichi2.utils

import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import kotlin.test.assertFailsWith

class ComputationTest {
    @Test
    fun valueIsSuccess() {
        val asNull = Computation.ok(1)
        assertThat(asNull.succeeded(), equalTo(true))
        assertThat(asNull.value, equalTo(1))
    }

    @Test
    fun errorIsFailure() {
        val asNull = Computation.err<Int>()
        assertThat(asNull.succeeded(), equalTo(false))
        assertFailsWith<IllegalStateException> {
            asNull.value
        }
    }
}
