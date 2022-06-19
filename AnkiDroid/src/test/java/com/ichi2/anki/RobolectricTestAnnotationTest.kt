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
package com.ichi2.anki

import com.ichi2.testutils.assertThrows
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.core.StringContains.containsString
import org.junit.Test

// explicitly missing @RunWith(AndroidJUnit4.class)
class RobolectricTestAnnotationTest : RobolectricTest() {
    @Test
    fun readableErrorIfNotAnnotated() {
        val exception = assertThrows<IllegalStateException> {
            @Suppress("UNUSED_VARIABLE")
            val unused = this.targetContext
        }
        assertThat(exception.message, containsString("RobolectricTestAnnotationTest"))
        assertThat(exception.message, containsString("@RunWith(AndroidJUnit4.class)"))
    }
}
