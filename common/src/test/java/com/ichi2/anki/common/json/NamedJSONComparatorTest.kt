/*
 * Copyright (c) 2021 Shridhar Goel <shridhar.goel@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.common.json

import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.lessThan
import org.junit.Test

class JSONNamedObject(
    override val name: String,
) : NamedObject

/** Tests [NamedJSONComparator] */
class NamedJSONComparatorTest {
    @Test
    fun checkIfReturnsCorrectValueForSameNames() {
        val firstObject = JSONNamedObject("TestName")
        val secondObject = JSONNamedObject("TestName")
        assertThat(NamedJSONComparator.INSTANCE.compare(firstObject, secondObject), equalTo(0))
    }

    @Test
    fun checkIfReturnsCorrectValueForDifferentNames() {
        val firstObject = JSONNamedObject("TestName1")
        val secondObject = JSONNamedObject("TestName2")
        assertThat(NamedJSONComparator.INSTANCE.compare(firstObject, secondObject), lessThan(0))
    }
}
