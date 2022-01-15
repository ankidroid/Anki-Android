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
import com.ichi2.anki.RobolectricTest
import junit.framework.TestCase
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import java.util.*

@RunWith(AndroidJUnit4::class)
class TagsTest : RobolectricTest() {
    @Test
    fun test_split() {
        val col = col
        val tags = Tags(col)
        val tagsList1 = ArrayList<String>()
        tagsList1.add("Todo")
        tagsList1.add("todo")
        tagsList1.add("Needs revision")
        val tagsList2 = ArrayList<String>()
        tagsList2.add("Todo")
        tagsList2.add("todo")
        tagsList2.add("Needs")
        tagsList2.add("Revision")
        Assert.assertNotEquals(tagsList1, tags.split("Todo todo Needs Revision"))
        TestCase.assertEquals(tagsList2, tags.split("Todo todo Needs Revision"))
        TestCase.assertEquals(0, tags.split("").size)
    }

    @Test
    fun test_in_list() {
        val col = col
        val tags = Tags(col)
        val tagsList = ArrayList<String>()
        tagsList.add("Todo")
        tagsList.add("Needs revision")
        tagsList.add("Once more")
        tagsList.add("test1 content")
        TestCase.assertFalse(tags.inList("Done", tagsList))
        TestCase.assertTrue(tags.inList("Needs revision", tagsList))
        TestCase.assertTrue(tags.inList("once More", tagsList))
        TestCase.assertFalse(tags.inList("test1Content", tagsList))
        TestCase.assertFalse(tags.inList("", ArrayList()))
    }

    @Test
    fun test_add_to_str() {
        val col = col
        val tags = Tags(col)
        TestCase.assertEquals(" Needs Revision Todo ", tags.addToStr("todo", "Todo todo Needs Revision"))
        TestCase.assertEquals(" Todo ", tags.addToStr("Todo", ""))
        TestCase.assertEquals(" Needs Revision Todo ", tags.addToStr("", "Todo todo Needs Revision"))
    }
}
