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
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ConfigTest : RobolectricTest() {

    @Test
    fun string_serialization() {
        assertThat(col.get_config_string("sortType"), equalTo("noteFld"))

        col.set_config("sortType", "noteFld2")

        assertThat(col.get_config_string("sortType"), equalTo("noteFld2"))
    }

    @Test
    fun has_config_not_null() {
        // empty
        assertThat("no key - false", col.has_config_not_null("aa"), equalTo(false))

        col.set_config("aa", JSONObject.NULL)
        assertThat("has key but null - false", col.has_config_not_null("aa"), equalTo(false))

        col.set_config("aa", "bb")
        assertThat("has key with value - true", col.has_config_not_null("aa"), equalTo(true))
        col.remove_config("aa")

        assertThat("key removed", col.has_config_not_null("aa"), equalTo(false))
    }

    @Test
    fun get_config_uses_default() {
        assertThat(col.get_config("hello", 1L), equalTo(1L))

        col.set_config("hello", JSONObject.NULL)

        assertThat(col.get_config("hello", 1L), equalTo(1L))
    }
}
