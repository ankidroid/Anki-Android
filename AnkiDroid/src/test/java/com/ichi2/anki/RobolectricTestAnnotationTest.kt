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

import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.core.StringContains.containsString
import org.junit.Test
import kotlin.test.assertFailsWith

// explicitly missing @RunWith(AndroidJUnit4.class)
//
// NOTE - this earned us a friendly warning from robolectric upstream when investigated, I quote:
// ----
// a word of caution: invoking Robolectric APIs outside of the context of a Robolectric ClassLoader
// is very unlikely to work :) Robolectric APIs often invoke underlying Android framework APIs,
// and those will not be available outside of Robolectric.
// ----
//
// ...so, this class may not work in the future as no test runner means no classloader, means
// referencing `RobolectricTest` may try to reference missing android APIs.
// See https://github.com/robolectric/robolectric/issues/8957#issuecomment-2032413796
class RobolectricTestAnnotationTest : RobolectricTest() {
    @Test
    fun readableErrorIfNotAnnotated() {
        val exception = assertFailsWith<IllegalStateException> {
            @Suppress("UNUSED_VARIABLE")
            val unused = this.targetContext
        }
        assertThat(exception.message, containsString("RobolectricTestAnnotationTest"))
        assertThat(exception.message, containsString("@RunWith(AndroidJUnit4.class)"))
    }
}
