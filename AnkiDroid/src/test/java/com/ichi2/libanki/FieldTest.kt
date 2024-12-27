/*
 *  Copyright (c) 2024 David Allison <davidallisongithub@gmail.com>
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
import com.ichi2.testutils.AndroidTest
import com.ichi2.testutils.EmptyApplication
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(AndroidJUnit4::class) // required due to differing JSON implementation
@Config(application = EmptyApplication::class)
class FieldTest : AndroidTest {
    @Test
    fun `'tag' - null handling`() {
        val jsonObject = JSONObject()
        var field = Field(jsonObject)

        assertNull(field.imageOcclusionTag, message = "{ }")

        jsonObject.put("tag", JSONObject.NULL)
        assertNull(field.imageOcclusionTag, message = "{ tag: null }")

        jsonObject.put("tag", "1")
        assertEquals("1", field.imageOcclusionTag, message = """{ tag: 1 }""")
    }
}
