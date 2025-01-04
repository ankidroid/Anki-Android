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

package com.ichi2.anki.utils.ext
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.testutils.AndroidTest
import com.ichi2.testutils.EmptyApplication
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.apply
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@RunWith(AndroidJUnit4::class) // This is necessary, android and JVM differ on JSONObject.NULL
@Config(application = EmptyApplication::class)
class JSONObjectTest : AndroidTest {
    @Test
    fun `test getStringOrNull`() {
        fun test(value: Any) = JSONObject().apply { put("test", value) }.getStringOrNull("test")

        assertNull(JSONObject().getStringOrNull("test"), message = "{}")
        assertNull(test(JSONObject.NULL), message = "{ test: null }")
        // WARN: this differs between pure JVM and Robolectric/Android
        // On Robolectric/Android, this is {}.
        // On JVM following using the standard implementation it's null.
        assertNotNull(test(JSONObject()), message = "test: { }")
        assertNotNull(test("null"), message = """{ test: "null" }""")
        assertNotNull(test("1"), message = """{ test: "1" }""")
    }
}
