/*
 *  Copyright (c) 2025 Hari Srinivasan <harisrini21@gmail.com>
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

package com.ichi2.anki.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class ClozeUtilsTest {
    @Test
    fun `extractClozeNumbers returns empty set when no cloze deletions exist`() {
        val fields = listOf("Plain text", "More plain text")
        val result = ClozeUtils.extractClozeNumbers(fields)
        assertEquals(emptySet<Int>(), result)
    }

    @Test
    fun `extractClozeNumbers finds single cloze deletion`() {
        val fields = listOf("Text with {{c1::cloze}}")
        val result = ClozeUtils.extractClozeNumbers(fields)
        assertEquals(setOf(1), result)
    }

    @Test
    fun `extractClozeNumbers finds multiple cloze deletions of same number`() {
        val fields = listOf("{{c1::One}} and {{c1::another one}}")
        val result = ClozeUtils.extractClozeNumbers(fields)
        assertEquals(setOf(1), result)
    }

    @Test
    fun `extractClozeNumbers finds multiple different cloze numbers`() {
        val fields = listOf("{{c1::First}} and {{c2::second}} and {{c3::third}}")
        val result = ClozeUtils.extractClozeNumbers(fields)
        assertEquals(setOf(1, 2, 3), result)
    }

    @Test
    fun `extractClozeNumbers works with cloze hints`() {
        val fields = listOf("{{c1::deletion::hint}}")
        val result = ClozeUtils.extractClozeNumbers(fields)
        assertEquals(setOf(1), result)
    }

    @Test
    fun `extractClozeNumbers finds cloze numbers across multiple fields`() {
        val fields =
            listOf(
                "Field one with {{c1::cloze}}",
                "Field two with {{c2::another}} cloze",
                "Field three with no cloze",
            )
        val result = ClozeUtils.extractClozeNumbers(fields)
        assertEquals(setOf(1, 2), result)
    }

    @Test
    fun `extractClozeNumbers returns numbers in sorted order`() {
        val fields = listOf("{{c3::Third}} {{c1::first}} {{c2::second}}")
        val result = ClozeUtils.extractClozeNumbers(fields).toList()
        assertEquals(listOf(1, 2, 3), result)
    }
}
