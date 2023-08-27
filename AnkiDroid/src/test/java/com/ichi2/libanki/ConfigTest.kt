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
        assertThat(col.config.getString("sortType"), equalTo("noteFld"))

        col.config.set("sortType", "noteFld2")

        assertThat(col.config.getString("sortType"), equalTo("noteFld2"))
    }

    @Test
    fun has_config_not_null() {
        // empty
        assertThat("no key - false", col.config.has_config_not_null("aa"), equalTo(false))

        col.config.set("aa", JSONObject.NULL)
        assertThat("has key but null - false", col.config.has_config_not_null("aa"), equalTo(false))

        col.config.set("aa", "bb")
        assertThat("has key with value - true", col.config.has_config_not_null("aa"), equalTo(true))
        col.config.remove("aa")

        assertThat("key removed", col.config.has_config_not_null("aa"), equalTo(false))
    }

    @Test
    fun get_config_uses_default() {
        assertThat(col.config.get("hello", 1L), equalTo(1L))

        col.config.set("hello", JSONObject.NULL)

        assertThat(col.config.get("hello", 1L), equalTo(1L))
    }

    @Test
    fun string_handling() {
        col.config.set("bb", JSONObject.NULL)
        assertThat(col.config.getString("bb"), equalTo("null"))
    }
}
