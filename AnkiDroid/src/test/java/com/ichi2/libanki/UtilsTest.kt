/****************************************************************************************
 * Copyright (c) 2018 Mike Hardy <mike@mikehardy.net>                                   *
 *                                                                                      *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 *                                                                                      *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/

package com.ichi2.libanki

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.util.*

@RunWith(AndroidJUnit4::class)
class UtilsTest {
    @Test
    fun testSplit() {
        assertEquals(listOf("foo", "bar"), Utils.splitFields("foobar"))
        assertEquals(listOf("", "foo", "", "", ""), Utils.splitFields("foo"))
    }

    @Test
    fun test_stripHTML_will_remove_tags() {
        val strings =
            listOf(
                "<>",
                "<1>",
                "<foo>",
                "<\n>",
                "<\\qwq>",
                "<aa\nsd\nas\n?\n>",
            )
        for (s in strings) {
            assertEquals(
                s.replace("\n", "\\n") + " should be removed.",
                "",
                Utils.stripHTML(s),
            )
        }
    }

    @Test
    fun test_stripHTML_will_remove_comments() {
        val strings =
            listOf(
                "<!---->",
                "<!--dd-->",
                "<!--asd asd asd-->",
                "<!--\n-->",
                "<!--\nsd-->",
                "<!--lkl\nklk\n-->",
            )
        for (s in strings) {
            assertEquals(
                s.replace("\n", "\\n") + " should be removed.",
                "",
                Utils.stripHTML(s),
            )
        }
    }
}
