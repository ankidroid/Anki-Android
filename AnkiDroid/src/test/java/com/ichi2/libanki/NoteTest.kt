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
package com.ichi2.libanki

import com.ichi2.libanki.Note.ClozeUtils
import org.junit.Test
import kotlin.test.assertEquals

class NoteTest {
    @Test
    fun noFieldDataReturnsFirstClozeIndex() {
        val expected = ClozeUtils.getNextClozeIndex(emptyList())
        assertEquals(1, expected, "No data should return a cloze index of 1 the next.")
    }

    @Test
    fun negativeFieldIsIgnored() {
        val fieldValue = "{{c-1::foo}}"
        val actual = ClozeUtils.getNextClozeIndex(listOf(fieldValue))
        assertEquals(1, actual, "The next consecutive value should be returned.")
    }

    @Test
    fun singleFieldReturnsNextValue() {
        val fieldValue = "{{c2::bar}}{{c1::foo}}"
        val actual = ClozeUtils.getNextClozeIndex(listOf(fieldValue))
        assertEquals(3, actual, "The next consecutive value should be returned.")
    }

    @Test
    fun multiFieldIsHandled() {
        val fields = listOf("{{c1::foo}}", "{{c2::bar}}")
        val actual = ClozeUtils.getNextClozeIndex(fields)
        assertEquals(3, actual, "The highest of all fields should be used.")
    }

    @Test
    fun missingFieldIsSkipped() {
        // this mimics Anki Desktop
        val fields = listOf("{{c1::foo}}", "{{c3::bar}}{{c4::baz}}")
        val actual = ClozeUtils.getNextClozeIndex(fields)
        assertEquals(5, actual, "A missing cloze index should not be selected if there are higher values.")
    }
}
