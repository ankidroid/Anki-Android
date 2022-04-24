/*
 Copyright (c) 2021 Tarek Mohamed Abdalla <tarekkma@gmail.com>

 This program is free software; you can redistribute it and/or modify it under
 the terms of the GNU General Public License as published by the Free Software
 Foundation; either version 3 of the License, or (at your option) any later
 version.

 This program is distributed in the hope that it will be useful, but WITHOUT ANY
 WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 PARTICULAR PURPOSE. See the GNU General Public License for more details.

 You should have received a copy of the GNU General Public License along with
 this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.utils

import com.ichi2.utils.ListUtil.Companion.assertListEquals
import org.hamcrest.CoreMatchers.instanceOf
import org.hamcrest.CoreMatchers.not
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.collection.IsIterableContainingInOrder
import org.junit.Assert.*
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.never
import java.util.*

@KotlinCleanup("Use Kotlin stdlib instead of Java Collection static method call")
@KotlinCleanup("Use Kotlin's method instead of Arrays.asList")
class UniqueArrayListTest {
    private val mDupData = Arrays.asList(
        "55",
        "TEst",
        "TEst",
        "12",
        "TEst",
        "dsf23A",
        "dsf23A",
        "dsf23A",
        "dsf23A",
        "23",
        "12",
        "sd",
        "TEst",
        "55"
    )

    private val mNoDupData = Arrays.asList(
        "55",
        "TEst",
        "12",
        "dsf23A",
        "23",
        "sd"
    )

    private inline fun <reified E> assertNotSameLists(a: MutableList<E>, b: MutableList<E>) {
        assertThat(b, not(IsIterableContainingInOrder.contains<Any>(*a.toTypedArray())))
    }

    @Test
    fun testOrderIsMaintained() {
        val longs = Arrays.asList(1, 1, 2, 3, 4, 1, 5, 1, 6, 7, 8, 9, 10, 11, 1, 12, 13)
        val uniqueList = UniqueArrayList.from(longs)

        assertTrue(uniqueList.indexOf(5) > uniqueList.indexOf(1))
    }

    @Test
    @KotlinCleanup("")
    fun test_Sorting() {
        val longs = Arrays.asList(10, 9, 7, 3, 2, -1, 5, 1, 65, -656)
        val uniqueList = UniqueArrayList.from(longs)
        Collections.sort(longs)
        uniqueList.sort()
        assertListEquals(longs, uniqueList)
    }

    @Test // #8807
    fun test_sorting_will_not_call_collectionsSort() {
        val longs = Arrays.asList(10, 9, 7, 3, 2, -1, 5, 1, 65, -656)

        val sorted: ArrayList<Int> = ArrayList(longs)
        Collections.sort(sorted)

        mockStatic(Collections::class.java).use { MockCollection ->
            val uniqueList = UniqueArrayList.from(longs)
            uniqueList.sort()
            assertListEquals(sorted, uniqueList)

            MockCollection.verify({ Collections.sort(any(), any<Comparator<in Any>>()) }, never())
        }
    }

    @Test
    fun test_uniqueness_after_sorting() {
        val longs = Arrays.asList(10, 9, 7, 3, 2, -1, 5, 1, 65, -656)
        val uniqueList = UniqueArrayList.from(longs)
        Collections.sort(longs)
        uniqueList.sort()
        assertListEquals(longs, uniqueList)

        uniqueList.addAll(longs)
        uniqueList.add(10)
        uniqueList.add(5, 65)

        assertListEquals(longs, uniqueList)

        uniqueList.add(575757)
        assertNotSameLists(longs, uniqueList)
    }

    @Test
    fun test_comparator() {
        val list = Arrays.asList("TarekkMA", "TarekkMA", "TarekkmA", "tarekkma")
        val uniqueList = UniqueArrayList.from(list, String.CASE_INSENSITIVE_ORDER)

        assertEquals(1, uniqueList.size.toLong())
        assertEquals("TarekkMA", uniqueList[0])

        uniqueList.clear()
        Collections.reverse(list)
        uniqueList.addAll(list)

        assertEquals(1, uniqueList.size.toLong())
        assertEquals("tarekkma", uniqueList[0])
    }

    @Test
    fun test_add_unique_after_sorting() {
        val longs = Arrays.asList(10, 9, 7, 3, 2, -1, 5, 1, 65, -656)
        val uniqueList = UniqueArrayList.from(longs)
        Collections.sort(longs)
        uniqueList.sort()
        assertEquals(longs, uniqueList)
        uniqueList.addAll(longs)
        assertEquals(longs, uniqueList)
    }

    @Test
    fun testFromCollection() {
        var uniqueArrayList = UniqueArrayList.from(mDupData)
        assertEquals(mNoDupData, uniqueArrayList)

        uniqueArrayList = UniqueArrayList()
        assertTrue(uniqueArrayList.isEmpty())
    }

    @Test
    fun testFromEmptyCollection() {
        val uniqueArrayList = UniqueArrayList<String>()
        assertTrue(uniqueArrayList.isEmpty())
    }

    @Test
    fun test_add_not_existing() {
        val uniqueArrayList = UniqueArrayList<String>()

        uniqueArrayList.add("a")
        uniqueArrayList.add("Z")
        uniqueArrayList.add("f")

        assertListEquals(Arrays.asList("a", "Z", "f"), uniqueArrayList)
    }

    @Test
    fun test_add_existing() {
        val uniqueArrayList = UniqueArrayList<String>()
        uniqueArrayList.add("a")
        uniqueArrayList.add("Z")
        uniqueArrayList.add("f")

        assertListEquals(Arrays.asList("a", "Z", "f"), uniqueArrayList)

        uniqueArrayList.add("a")
        uniqueArrayList.add("Z")
        uniqueArrayList.add("f")
        uniqueArrayList.add("a")
        uniqueArrayList.add("Z")
        uniqueArrayList.add("f")
        uniqueArrayList.add("a")
        uniqueArrayList.add("Z")
        uniqueArrayList.add("f")

        assertListEquals(Arrays.asList("a", "Z", "f"), uniqueArrayList)
    }

    @Test
    fun test_set_not_existing() {
        val uniqueArrayList = UniqueArrayList<String>()

        uniqueArrayList.add("a")
        uniqueArrayList.add("Z")
        uniqueArrayList.add("f")

        val res = uniqueArrayList.set(1, "m")

        assertListEquals(Arrays.asList("a", "m", "f"), uniqueArrayList)
        assertEquals("Z", res)
    }

    @Test
    fun test_set_existing() {
        val uniqueArrayList = UniqueArrayList<String>()

        uniqueArrayList.add("a")
        uniqueArrayList.add("Z")
        uniqueArrayList.add("f")

        val res = uniqueArrayList.set(1, "a")

        assertListEquals(Arrays.asList("a", "f"), uniqueArrayList)
        assertEquals("Z", res)
    }

    @Test
    fun test_set_will_remove_replaced_item() {
        val uniqueArrayList = UniqueArrayList<String>()

        uniqueArrayList.add("a")
        uniqueArrayList[0] = "b"
        uniqueArrayList.add("a")

        assertListEquals(Arrays.asList("b", "a"), uniqueArrayList)
    }

    @Test
    fun test_addAll_no_change() {
        val uniqueArrayList = UniqueArrayList<String>()

        uniqueArrayList.add("a")
        uniqueArrayList.add("Z")
        uniqueArrayList.add("f")

        val res = uniqueArrayList.addAll(Arrays.asList("a", "Z", "f"))

        assertListEquals(Arrays.asList("a", "Z", "f"), uniqueArrayList)
        assertFalse(res)
    }

    @Test
    fun test_addAll_full_change() {
        val uniqueArrayList = UniqueArrayList<String>()

        uniqueArrayList.add("a")
        uniqueArrayList.add("Z")
        uniqueArrayList.add("f")

        val res = uniqueArrayList.addAll(Arrays.asList("w", "x", "y"))

        assertListEquals(Arrays.asList("a", "Z", "f", "w", "x", "y"), uniqueArrayList)
        assertTrue(res)
    }

    @Test
    fun test_addAll_partial_change() {
        val uniqueArrayList = UniqueArrayList<String>()

        uniqueArrayList.add("a")
        uniqueArrayList.add("Z")
        uniqueArrayList.add("f")

        val res = uniqueArrayList.addAll(Arrays.asList("f", "Y", "Z"))

        assertListEquals(Arrays.asList("a", "Z", "f", "Y"), uniqueArrayList)
        assertTrue(res)
    }

    @Test
    fun test_addAll_withIndex_no_change() {
        val uniqueArrayList = UniqueArrayList<String>()

        uniqueArrayList.add("a")
        uniqueArrayList.add("Z")
        uniqueArrayList.add("f")

        val res = uniqueArrayList.addAll(1, Arrays.asList("a", "Z", "f"))

        assertListEquals(Arrays.asList("a", "Z", "f"), uniqueArrayList)
        assertFalse(res)
    }

    @Test
    fun test_addAll_withIndex_full_change() {
        val uniqueArrayList = UniqueArrayList<String>()

        uniqueArrayList.add("a")
        uniqueArrayList.add("Z")
        uniqueArrayList.add("f")

        val res = uniqueArrayList.addAll(1, Arrays.asList("w", "x", "y"))

        assertListEquals(Arrays.asList("a", "w", "x", "y", "Z", "f"), uniqueArrayList)
        assertTrue(res)
    }

    @Test
    fun test_addAll_withIndex_partial_change() {
        val uniqueArrayList = UniqueArrayList<String>()

        uniqueArrayList.add("a")
        uniqueArrayList.add("Z")
        uniqueArrayList.add("f")

        val res = uniqueArrayList.addAll(1, Arrays.asList("f", "Y", "Z"))

        assertListEquals(Arrays.asList("a", "Y", "Z", "f"), uniqueArrayList)
        assertTrue(res)
    }

    @Test
    fun test_addAll_withIndex_last_position() {
        val uniqueArrayList = UniqueArrayList<String>()

        uniqueArrayList.add("a")
        uniqueArrayList.add("Z")
        uniqueArrayList.add("f")

        val res = uniqueArrayList.addAll(uniqueArrayList.size, Arrays.asList("w", "x", "y"))

        assertListEquals(Arrays.asList("a", "Z", "f", "w", "x", "y"), uniqueArrayList)
        assertTrue(res)
    }

    @Test
    fun test_addAll_order_1() {
        val uniqueArrayList = UniqueArrayList<Int>()

        val l1 = Arrays.asList(1, 2, 3, 4, 5)
        assertTrue(uniqueArrayList.addAll(l1))
        assertListEquals(l1, uniqueArrayList)

        val l2 = Arrays.asList(5, 4, 3, 2, 1, 0)
        assertTrue(uniqueArrayList.addAll(0, l2))

        val l3 = Arrays.asList(0, 1, 2, 3, 4, 5)
        assertListEquals(l3, uniqueArrayList)
    }

    @Test
    fun test_addAll_order_2() {
        val uniqueArrayList = UniqueArrayList<Int>()

        val l1 = Arrays.asList(1, 2, 3, 4, 5)
        assertTrue(uniqueArrayList.addAll(l1))
        assertListEquals(l1, uniqueArrayList)

        val l2 = Arrays.asList(0, 1, 2, 3, 4, 5)
        assertTrue(uniqueArrayList.addAll(0, l2))

        assertListEquals(l2, uniqueArrayList)
    }

    @Test
    fun test_clear() {
        val longs = Arrays.asList(1, 1, 2, 3, 4, 1, 5, 1, 6, 7, 8, 9, 10, 11, 1, 12, 13)
        val uniqueList = UniqueArrayList.from(longs)

        assertFalse(uniqueList.isEmpty())
        uniqueList.clear()
        assertTrue(uniqueList.isEmpty())
        uniqueList.addAll(longs)
        assertFalse(uniqueList.isEmpty())
    }

    @Test
    fun test_remove_object() {
        val longs = Arrays.asList(1L, 1L, 2L, 3L, 4L, 1L, 5L, 1L, 6L, 7L, 8L, 9L, 10L, 11L, 1L, 12L, 13L)
        val uniqueList = UniqueArrayList.from(longs)

        assertNotEquals(-1, uniqueList.indexOf(1L).toLong())
        uniqueList.remove(1L)
        assertEquals(-1, uniqueList.indexOf(1L).toLong())
        uniqueList[10] = 1L
        assertEquals(10, uniqueList.indexOf(1L).toLong())
    }

    @Test
    fun test_remove_index() {
        val longs = Arrays.asList(1L, 1L, 2L, 3L, 4L, 1L, 5L, 1L, 6L, 7L, 8L, 9L, 10L, 11L, 1L, 12L, 13L)
        val uniqueList = UniqueArrayList.from(longs)

        assertNotEquals(-1, uniqueList.indexOf(1L).toLong())
        uniqueList.removeAt(0 /*index of 1L*/)
        assertEquals(-1, uniqueList.indexOf(1L).toLong())
        uniqueList[10] = 1L
        assertEquals(10, uniqueList.indexOf(1L).toLong())
    }

    @Test
    fun test_contain() {
        val longs = Arrays.asList(1L, 1L, 2L, 3L, 4L, 1L, 5L, 1L, 6L, 7L, 8L, 9L, 10L, 11L, 1L, 12L, 13L)
        val uniqueList = UniqueArrayList.from(longs)

        assertTrue(uniqueList.contains(1L))
        assertFalse(uniqueList.contains(1502L))
    }

    @Test
    fun test_get_by_index() {
        val longs = Arrays.asList(1L, 1L, 2L, 3L, 4L, 1L, 5L, 1L, 6L, 7L, 8L, 9L, 10L, 11L, 1L, 12L, 13L)
        val uniqueList = UniqueArrayList.from(longs)

        assertEquals(1L, uniqueList[0])
        assertNotEquals(1L, uniqueList[1])
        assertEquals(13L, uniqueList[12])
        assertEquals(12L, uniqueList[11])
    }

    @Test
    fun test_indexOf_and_lastIndexOf() {
        val longs = Arrays.asList(1L, 1L, 2L, 3L, 4L, 1L, 5L, 1L, 6L, 7L, 8L, 9L, 10L, 11L, 1L, 12L, 13L)
        val uniqueList = UniqueArrayList.from(longs)

        assertEquals(0, uniqueList.indexOf(1L).toLong())
        assertEquals(5, uniqueList.indexOf(6L).toLong())
        assertEquals(12, uniqueList.indexOf(13L).toLong())
        assertEquals(9, uniqueList.indexOf(10L).toLong())
        assertEquals(2, uniqueList.indexOf(3L).toLong())

        assertEquals(0, uniqueList.lastIndexOf(1L).toLong())
        assertEquals(5, uniqueList.lastIndexOf(6L).toLong())
        assertEquals(12, uniqueList.lastIndexOf(13L).toLong())
        assertEquals(9, uniqueList.lastIndexOf(10L).toLong())
        assertEquals(2, uniqueList.lastIndexOf(3L).toLong())
    }

    @Test
    fun test_iterator() {
        val longs = Arrays.asList(1L, 1L, 2L, 3L, 4L, 1L, 5L, 1L, 6L, 7L, 8L, 9L, 10L, 11L, 1L, 12L, 13L)
        val uniqueList = UniqueArrayList.from(longs)

        val list: MutableList<Long> = ArrayList()
        uniqueList.iterator().forEachRemaining { e: Long -> list.add(e) }

        assertEquals(
            Arrays.asList(
                1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L, 11L, 12L, 13L
            ),
            list
        )
    }

    @Test
    fun test_listIterator() {
        val longs = Arrays.asList(1L, 1L, 2L, 3L, 4L, 1L, 5L, 1L, 6L, 7L, 8L, 9L, 10L, 11L, 1L, 12L, 13L)
        val uniqueList = UniqueArrayList.from(longs)

        val list: MutableList<Long> = ArrayList()
        uniqueList.listIterator().forEachRemaining { e: Long -> list.add(e) }

        assertEquals(
            Arrays.asList(
                1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L, 11L, 12L, 13L
            ),
            list
        )
    }

    @Test
    fun test_listIterator_index() {
        val longs = Arrays.asList(1L, 1L, 2L, 3L, 4L, 1L, 5L, 1L, 6L, 7L, 8L, 9L, 10L, 11L, 1L, 12L, 13L)
        val uniqueList = UniqueArrayList.from(longs)

        val list: MutableList<Long> = ArrayList()
        uniqueList.listIterator(5).forEachRemaining { e: Long -> list.add(e) }

        assertEquals(
            Arrays.asList(
                6L, 7L, 8L, 9L, 10L, 11L, 12L, 13L
            ),
            list
        )
    }

    @Test
    fun test_subList() {
        val longs = Arrays.asList(1L, 1L, 2L, 3L, 4L, 1L, 5L, 1L, 6L, 7L, 8L, 9L, 10L, 11L, 1L, 12L, 13L)
        val uniqueList = UniqueArrayList.from(longs)

        assertEquals(
            Arrays.asList(
                6L, 7L, 8L, 9L, 10L
            ),
            uniqueList.subList(5, 10)
        )
    }

    @Test
    fun test_containsAll() {
        val longs = Arrays.asList(1L, 1L, 2L, 3L, 4L, 1L, 5L, 1L, 6L, 7L, 8L, 9L, 10L, 11L, 1L, 12L, 13L)
        val uniqueList = UniqueArrayList.from(longs)

        assertTrue(uniqueList.containsAll(Arrays.asList(1L, 10L, 13L)))
        assertFalse(uniqueList.containsAll(Arrays.asList(1L, 130L, 13L)))
        assertFalse(uniqueList.containsAll(Arrays.asList(-1L, 130L, 1003L)))
    }

    @Test
    fun test_retainAll() {
        val longs = Arrays.asList(1L, 1L, 2L, 3L, 4L, 1L, 5L, 1L, 6L, 7L, 8L, 9L, 10L, 11L, 1L, 12L, 13L)
        val uniqueList = UniqueArrayList.from(longs)

        assertNotEquals(2, uniqueList.size.toLong())
        uniqueList.retainAll(Arrays.asList(1L, 3L))
        assertEquals(2, uniqueList.size.toLong())
        assertEquals(Arrays.asList(1L, 3L), uniqueList)
    }

    @Test
    fun test_isEmpty() {
        val longs = Arrays.asList(1L, 1L, 2L, 3L, 4L, 1L, 5L, 1L, 6L, 7L, 8L, 9L, 10L, 11L, 1L, 12L, 13L)
        val uniqueList = UniqueArrayList.from(longs)

        assertFalse(uniqueList.isEmpty())
        uniqueList.removeAll(
            Arrays.asList(
                1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L, 11L, 12L, 13L
            )
        )
        assertTrue(uniqueList.isEmpty())
    }

    @Test
    fun test_toArray() {
        val longs = Arrays.asList(1L, 1L, 2L, 3L, 4L, 1L, 5L, 1L, 6L, 7L, 8L, 9L, 10L, 11L, 1L, 12L, 13L)
        val uniqueList = UniqueArrayList.from(longs)

        val arr = Arrays.asList(
            1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L, 11L, 12L, 13L
        )

        assertListEquals(arr, uniqueList)
    }

    @Test
    fun test_toArrayType() {
        val longs = Arrays.asList(1L, 1L, 2L, 3L, 4L, 1L, 5L, 1L, 6L, 7L, 8L, 9L, 10L, 11L, 1L, 12L, 13L)
        val uniqueList = UniqueArrayList.from(longs)

        val arr = Arrays.asList(
            1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L, 11L, 12L, 13L
        )

        assertListEquals(arr, uniqueList)

        assertThat(uniqueList[0], instanceOf(Long::class.java))
    }
}
