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

import com.ichi2.utils.SequenceUtil.takeWhileIncludingFirstNonMatch
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.empty
import org.hamcrest.Matchers.equalTo
import org.junit.Test

class SequenceUtilTest {

    @Test
    fun test_take_while_including_first_non_match() {
        // empty works and returns empty
        val empty = listOf<String>().asSequence().takeWhileIncludingFirstNonMatch { true }.toList()

        assertThat(empty, empty())

        val none = listOf("a", "b").asSequence().takeWhileIncludingFirstNonMatch { false }.toList()

        // this is unintuitive
        assertThat(none, equalTo(listOf("a")))

        val second = listOf("a", "b").asSequence().takeWhileIncludingFirstNonMatch { it != "b" }.toList()

        assertThat(second, equalTo(listOf("a", "b")))
    }
}
