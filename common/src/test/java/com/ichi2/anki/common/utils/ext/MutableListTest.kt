/*
 *  Copyright (c) 2025 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki.common.utils.ext

import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.contains
import org.hamcrest.Matchers.empty
import org.junit.Test

class MutableListTest {
    @Test
    fun `replaceWith replaces contents`() {
        val list = mutableListOf(1, 2, 3)
        list.replaceWith(listOf(4, 5))

        assertThat(list, contains(4, 5))
    }

    @Test
    fun `replaceWith works on empty list`() {
        val list = mutableListOf<String>()
        list.replaceWith(listOf("a", "b"))

        assertThat(list, contains("a", "b"))
    }

    @Test
    fun `replaceWith works with empty input`() {
        val list = mutableListOf(1, 2, 3)
        list.replaceWith(emptyList())

        assertThat(list, empty())
    }
}
