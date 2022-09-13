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

package com.ichi2.libanki

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.utils.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class ConfigTest : RobolectricTest() {

    @Test
    fun string_serialization() {
        assertEquals(col.get_config_string("sortType"), "noteFld")

        assertEquals(col.conf.getString("sortType"), "noteFld")

        col.set_config("sortType", "noteFld2")

        assertEquals(col.get_config_string("sortType"), "noteFld2")
    }

    @Test
    fun has_config_not_null() {
        // empty
        assertFalse(col.has_config_not_null("aa"), "no key - false")

        col.set_config("aa", JSONObject.NULL)
        assertFalse(col.has_config_not_null("aa"), "has key but null - false")

        col.set_config("aa", "bb")
        assertTrue(col.has_config_not_null("aa"), "has key with value - true")
        col.remove_config("aa")

        assertFalse(col.has_config_not_null("aa"), "key removed")
    }

    @Test
    fun get_config_uses_default() {
        assertEquals(col.get_config("hello", 1L), 1L)

        col.set_config("hello", JSONObject.NULL)

        assertEquals(col.get_config("hello", 1L), 1L)
    }
}
