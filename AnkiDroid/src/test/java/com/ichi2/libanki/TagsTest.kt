/*
 *  Copyright (c) 2021 Vaibhavi Lokegaonkar <vaibhavilokegaonkar@gmail.com> Github Username: Vaibhavi1707
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
import com.ichi2.testutils.JvmTest
import junit.framework.TestCase.*
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TagsTest : JvmTest() {

    @Test
    fun test_split() {
        val tags = Tags(col)
        val tags_list1 = ArrayList<String>()
        tags_list1.add("Todo")
        tags_list1.add("todo")
        tags_list1.add("Needs revision")

        val tags_list2 = ArrayList<String>()
        tags_list2.add("Todo")
        tags_list2.add("todo")
        tags_list2.add("Needs")
        tags_list2.add("Revision")

        assertNotEquals(tags_list1, tags.split("Todo todo Needs Revision"))
        assertEquals(tags_list2, tags.split("Todo todo Needs Revision"))
        assertEquals(0, tags.split("").size)
    }

    @Test
    fun test_in_list() {
        val tags = Tags(col)

        val tags_list = ArrayList<String>()
        tags_list.add("Todo")
        tags_list.add("Needs revision")
        tags_list.add("Once more")
        tags_list.add("test1 content")

        assertFalse(tags.inList("Done", tags_list))
        assertTrue(tags.inList("Needs revision", tags_list))
        assertTrue(tags.inList("once More", tags_list))
        assertFalse(tags.inList("test1Content", tags_list))
        assertFalse(tags.inList("", ArrayList()))
    }
}
