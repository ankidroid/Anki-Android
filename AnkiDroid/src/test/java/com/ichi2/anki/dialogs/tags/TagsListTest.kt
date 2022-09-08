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
package com.ichi2.anki.dialogs.tags

import com.ichi2.utils.KotlinCleanup
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

// suppressed to have a symmetry in all tests, Arrays.asList(...) should be all you need.
@KotlinCleanup("Use kotlin functions instead of Arrays.asList")
class TagsListTest {
    @KotlinCleanup("non-null")
    var tagsList: TagsList? = null
    @KotlinCleanup("non-null")
    var tagsListWithIndeterminate: TagsList? = null
    @Before
    @Throws(Exception::class)
    fun setUp() {
        tagsList = TagsList(TAGS, CHECKED_TAGS)
        tagsListWithIndeterminate = TagsList(TAGS, CHECKED_TAGS, UNCHECKED_TAGS)
    }

    @Test
    fun test_constructor_will_remove_dups() {
        val allTags = Arrays.asList("a", "b", "a")
        val checkedTags = Arrays.asList("b", "b", "b")
        val list = TagsList(
            allTags,
            checkedTags
        )

        assertEquals(
            Arrays.asList("a", "b"), list.copyOfAllTagList(),
            "All tags list should not contain any duplicates"
        )
        assertEquals(
            Arrays.asList("b"), list.copyOfCheckedTagList(),
            "Checked tags list should not contain any duplicates"
        )
    }

    @Test
    fun test_constructor_will_remove_dups_unchecked() {
        val allTags = Arrays.asList("a", "b", "a", "c", "c", "d")
        val checkedTags = Arrays.asList("b", "b", "b")
        val uncheckedTags = Arrays.asList("c", "c", "d")
        val list = TagsList(
            allTags,
            checkedTags,
            uncheckedTags
        )

        assertEquals(
            Arrays.asList("a", "b", "c", "d"), list.copyOfAllTagList(), "All tags list should not contain any duplicates"
        )
        assertEquals(
            Arrays.asList("b"), list.copyOfCheckedTagList(), "Checked tags list should not contain any duplicates"
        )
        assertEquals(
            Arrays.asList<Any>(), list.copyOfIndeterminateTagList(), "indeterminate tags list should be empty"
        )
    }

    @Test
    fun test_constructor_will_ignore_casing() {
        val allTags = Arrays.asList("aA", "bb", "aa")
        val checkedTags = Arrays.asList("bb", "Bb", "bB")
        val list = TagsList(
            allTags,
            checkedTags
        )

        assertEquals(

            Arrays.asList("aA", "bb"), list.copyOfAllTagList(), "All tags list should not contain any duplicates (case insensitive)"
        )
        assertEquals(

            Arrays.asList("bb"), list.copyOfCheckedTagList(), "Checked tags list should not contain any duplicates  (case insensitive)"
        )
    }

    @Test
    fun test_constructor_will_ignore_casing_unchecked() {
        val allTags = Arrays.asList("aA", "bb", "aa", "cc", "dd")
        val checkedTags = Arrays.asList("bb", "Bb", "bB", "dd", "ff")
        val uncheckedTags = Arrays.asList("BB", "cC", "cC", "dD", "CC")
        val list = TagsList(
            allTags,
            checkedTags,
            uncheckedTags
        )

        assertEquals(

            Arrays.asList("aA", "bb", "cc", "dd", "ff"), list.copyOfAllTagList(), "All tags list should not contain any duplicates (case insensitive)"
        )
        assertEquals(

            Arrays.asList("ff"), list.copyOfCheckedTagList(), "Checked tags list should not contain any duplicates  (case insensitive)"
        )
        assertEquals(
            Arrays.asList("bb", "dd"), list.copyOfIndeterminateTagList(),
            "Checked tags list should not contain any duplicates  (case insensitive)\n" +
                "and IndeterminateTagList is correct".trimIndent()
        )
    }

    @Test
    fun test_constructor_will_add_checked_to_all() {
        val allTags = Arrays.asList("aA", "bb", "aa")
        val checkedTags = Arrays.asList("bb", "Bb", "bB", "cc")
        val list = TagsList(
            allTags,
            checkedTags
        )

        assertEquals(

            Arrays.asList("aA", "bb", "cc"), list.copyOfAllTagList(), "Extra tags in checked not found in all tags, must be added to all tags list"
        )
        assertEquals(

            Arrays.asList("bb", "cc"), list.copyOfCheckedTagList(), "Extra tags in checked not found in all tags, must be found when retrieving checked tag list"
        )
    }

    @Test
    fun test_constructor_will_add_checked_and_unchecked_to_all() {
        val allTags = Arrays.asList("aA", "bb", "aa")
        val checkedTags = Arrays.asList("bb", "Bb", "bB", "Cc", "zz")
        val uncheckedTags = Arrays.asList("BB", "cC", "cC", "dD", "CC")
        val list = TagsList(
            allTags,
            checkedTags,
            uncheckedTags
        )

        assertEquals(Arrays.asList("aA", "bb", "Cc", "zz", "dD"), list.copyOfAllTagList(), "Extra tags in checked not found in all tags, must be added to all tags list")
        assertEquals(Arrays.asList("zz"), list.copyOfCheckedTagList(), "Extra tags in checked not found in all tags, must be found when retrieving checked tag list")
        assertEquals(Arrays.asList("bb", "Cc"), list.copyOfIndeterminateTagList())
    }

    @Test
    fun test_constructor_will_complete_hierarchy_for_all_tags() {
        val allTags = listOf("cat1", "cat2::aa", "cat3::aa::bb::cc::dd")
        val checkedTags = listOf("cat1::aa", "cat1::bb", "cat2::bb::aa", "cat2::bb::bb")
        val list = TagsList(
            allTags,
            checkedTags
        )
        list.sort()
        assertEquals(
            listOf(
                "cat1", "cat1::aa", "cat1::bb", "cat2", "cat2::aa", "cat2::bb", "cat2::bb::aa",
                "cat2::bb::bb", "cat3", "cat3::aa", "cat3::aa::bb", "cat3::aa::bb::cc",
                "cat3::aa::bb::cc::dd"
            ),
            list.copyOfAllTagList()
        )
        assertEquals(listOf("cat1::aa", "cat1::bb", "cat2::bb::aa", "cat2::bb::bb"), list.copyOfCheckedTagList())
        assertEquals(
            listOf("cat1", "cat2", "cat2::bb"), list.copyOfIndeterminateTagList(), "Ancestors of checked tags should be marked as indeterminate"
        )
    }

    @Test
    fun test_isChecked_index() {
        assertTrue(tagsList!!.isChecked(0), "Tag at index 0 should be checked")
        assertTrue(tagsList!!.isChecked(3), "Tag at index 3 should be checked")
        assertFalse(tagsList!!.isChecked(1), "Tag at index 1 should be unchecked")
        assertFalse(tagsList!!.isChecked(6), "Tag at index 6 should be unchecked")

        // indeterminate tags
        assertFalse(tagsListWithIndeterminate!!.isChecked(0), "Tag at index 0 should be unchecked")
        assertFalse(tagsListWithIndeterminate!!.isChecked(3), "Tag at index 3 should be unchecked")
    }

    @Test
    fun test_isChecked_object() {
        assertTrue(tagsList!!.isChecked("programming"), "'programming' tag should be checked")
        assertTrue(tagsList!!.isChecked("faces"), "'faces' tag should be checked")
        assertFalse(tagsList!!.isChecked("cars"), "'cars' tag should be unchecked")
        assertFalse(tagsList!!.isChecked("flags"), "'flags' tag should be unchecked")

        // indeterminate tags
        assertFalse(tagsListWithIndeterminate!!.isChecked("programming"), "Tag at index 'programming' should be unchecked")
        assertFalse(tagsListWithIndeterminate!!.isChecked("faces"), "Tag at index 'faces' should be unchecked")
    }

    @Test
    fun test_isIndeterminate_index() {
        assertFalse(tagsList!!.isIndeterminate(0), "Tag at index 0 should be checked (not indeterminate)")
        assertFalse(tagsList!!.isIndeterminate(3), "Tag at index 3 should be checked (not indeterminate)")
        assertFalse(tagsList!!.isIndeterminate(1), "Tag at index 1 should be unchecked (not indeterminate)")
        assertFalse(tagsList!!.isIndeterminate(6), "Tag at index 6 should be unchecked (not indeterminate)")

        assertTrue(tagsListWithIndeterminate!!.isIndeterminate(0), "Tag at index 0 should be indeterminate")
        assertTrue(tagsListWithIndeterminate!!.isIndeterminate(3), "Tag at index 3 should be indeterminate")
        assertFalse(tagsListWithIndeterminate!!.isIndeterminate(1), "Tag at index 1 should be unchecked (not indeterminate)")
        assertFalse(tagsListWithIndeterminate!!.isIndeterminate(6), "Tag at index 6 should be unchecked (not indeterminate)")
        assertFalse(tagsListWithIndeterminate!!.isIndeterminate(5), "Tag at index 6 should be unchecked (not indeterminate)")
    }

    @Test
    fun test_isIndeterminate_object() {
        assertFalse(tagsList!!.isIndeterminate("programming"), "'programming' tag should be checked (not indeterminate)")
        assertFalse(tagsList!!.isIndeterminate("faces"), "'faces' tag should be checked (not indeterminate)")
        assertFalse(tagsList!!.isIndeterminate("cars"), "'cars' tag should be unchecked (not indeterminate)")
        assertFalse(tagsList!!.isIndeterminate("flags"), "'flags' tag should be unchecked (not indeterminate)")

        assertTrue(tagsListWithIndeterminate!!.isIndeterminate("programming"), "Tag 'programming' should be indeterminate")
        assertTrue(tagsListWithIndeterminate!!.isIndeterminate("faces"), "Tag 'faces' should be indeterminate")
        assertFalse(tagsListWithIndeterminate!!.isIndeterminate("cars"), "Tag 'cars' should be unchecked (not indeterminate)")
        assertFalse(tagsListWithIndeterminate!!.isIndeterminate("flags"), "Tag 'flags' should be unchecked (not indeterminate)")
    }

    @Test
    fun test_add() {
        assertTrue(

            tagsList!!.add("anki"), "Adding 'anki' tag should return true, as the 'anki' is a new tag"
        )
        assertFalse(

            tagsList!!.add("colors"), "Adding 'colors' tag should return false, as the 'colors' is a already existing tag"
        )
        assertEquals(
            join(TAGS, "anki"), tagsList!!.copyOfAllTagList(),
            "The newly added 'anki' tag should be found when retrieving all tags list"
        )
        assertSameElementsIgnoreOrder(
            tagsList!!.copyOfCheckedTagList(),
            CHECKED_TAGS, "Adding operations should have nothing to do with the checked status of tags"
        )
    }

    @Test
    fun test_add_hierarchy_tag() {
        assertTrue(

            tagsList!!.add("language::english"), "Adding 'language::english' tag should return true"
        )
        assertTrue(

            tagsList!!.add("language::other::java"), "Adding 'language::other::java' tag should return true"
        )
        assertTrue(

            tagsList!!.add("language::other::kotlin"), "Adding 'language::other::kotlin' tag should return true"
        )
        assertFalse(

            tagsList!!.add("language::english"), "Repeatedly adding 'language::english' tag should return false"
        )
        assertFalse(

            tagsList!!.add("language::other")
        )
        assertTrue(tagsList!!.check("language::other::java"))
        assertTrue(
            tagsList!!.copyOfIndeterminateTagList().contains("language::other"), "Intermediate tags should marked as indeterminate"
        )
        assertTrue(tagsList!!.add("object::electronic"))
        assertTrue(tagsList!!.check("object::electronic"))
        assertTrue(tagsList!!.add("object::electronic::computer"))
        assertTrue(tagsList!!.check("object::electronic::computer"))
        assertFalse(
            tagsList!!.copyOfIndeterminateTagList().contains("object::electronic"),
            "Should not mark checked intermediate tags as indeterminate"
        )
    }

    @Test
    fun test_check() {
        assertFalse(
            tagsList!!.check("anki"), "Attempting to check tag 'anki' should return false, as 'anki' is not found in all tags list"
        ) // not in the list
        assertFalse(
            tagsList!!.check("colors"),
            "Attempting to check tag 'colors' should return false, as 'colors' is already checked"
        ) // already checked
        assertTrue(
            tagsList!!.check("flags"),
            "Attempting to check tag 'flags' should return true, as 'flags' is found in all tags and is not already checked"
        )
        assertEquals(
            TAGS, tagsList!!.copyOfAllTagList(),
            "Changing the status of tags to be checked should have noting to do with all tag list"
        ) // no change
        assertSameElementsIgnoreOrder(
            tagsList!!.copyOfCheckedTagList(),
            join(CHECKED_TAGS, "flags"), "The checked 'flags' tag should be found when retrieving list of checked tag"
        )
    }

    @Test
    fun test_check_with_indeterminate_tags_list() {
        assertTrue(

            tagsListWithIndeterminate!!.check("faces"), "Attempting to check tag 'faces' should return true, as 'faces' is found in all tags and it have indeterminate state"
        )
        assertEquals(
            TAGS, tagsListWithIndeterminate!!.copyOfAllTagList(),
            "Changing the status of tags to be checked should have noting to do with all tag list"
        )
        assertTrue(

            tagsListWithIndeterminate!!.copyOfCheckedTagList().contains("faces"), "The checked 'faces' tag should be found when retrieving list of checked tag"
        )
        assertFalse(

            tagsListWithIndeterminate!!.copyOfIndeterminateTagList().contains("faces"), "The checked 'faces' tag should not be found when retrieving list of indeterminate tags"
        )
    }

    @Test
    fun test_uncheck() {
        assertFalse(

            tagsList!!.uncheck("anki"), "Attempting to uncheck tag 'anki' should return false, as 'anki' is not found in all tags list"
        ) // not in the list
        assertFalse(

            tagsList!!.uncheck("flags"), "Attempting to uncheck tag 'flags' should return false, as 'flags' is already unchecked"
        ) // already unchecked
        assertTrue(

            tagsList!!.uncheck("colors"), "Attempting to uncheck tag 'colors' should return true, as 'colors' is found in all tags and is checked"
        )
        assertEquals(

            TAGS, tagsList!!.copyOfAllTagList(), "Changing the status of tags to be unchecked should have noting to do with all tag list"
        ) // no change
        assertSameElementsIgnoreOrder(
            tagsList!!.copyOfCheckedTagList(),
            minus(CHECKED_TAGS, "colors"), "The unchecked 'colors' tag should be not be found when retrieving list of checked tag"
        )
    }

    @Test
    fun test_uncheck_indeterminate_tags_list() {
        assertTrue(
            tagsListWithIndeterminate!!.uncheck("programming"), "Attempting to uncheck tag 'programming' should return true, as 'programming' is found in all tags and it have indeterminate state"
        )
        assertEquals(
            TAGS, tagsListWithIndeterminate!!.copyOfAllTagList(), "Changing the status of tags to be checked should have noting to do with all tag list"
        )
        assertFalse(
            tagsListWithIndeterminate!!.copyOfCheckedTagList().contains("programming"), "Changing from indeterminate to unchecked should not affect checked tags"
        )
        assertFalse(

            tagsListWithIndeterminate!!.copyOfIndeterminateTagList().contains("programming"), "The checked 'programming' tag should not be found when retrieving list of indeterminate tags"
        )
    }

    @Test
    fun test_toggleAllCheckedStatuses() {
        assertEquals(TAGS, tagsList!!.copyOfAllTagList())
        assertSameElementsIgnoreOrder(CHECKED_TAGS, tagsList!!.copyOfCheckedTagList())

        assertTrue(tagsList!!.toggleAllCheckedStatuses())

        assertEquals(TAGS, tagsList!!.copyOfAllTagList())
        assertSameElementsIgnoreOrder(TAGS, tagsList!!.copyOfCheckedTagList())

        assertTrue(tagsList!!.toggleAllCheckedStatuses())

        assertEquals(TAGS, tagsList!!.copyOfAllTagList())
        assertSameElementsIgnoreOrder(ArrayList(), tagsList!!.copyOfCheckedTagList())
    }

    @Test
    fun test_toggleAllCheckedStatuses_indeterminate() {
        assertEquals(TAGS, tagsListWithIndeterminate!!.copyOfAllTagList())
        assertSameElementsIgnoreOrder(
            minus(CHECKED_TAGS, INDETERMINATE_TAGS),
            tagsListWithIndeterminate!!.copyOfCheckedTagList()
        )

        assertNotEquals(emptyList<Any>(), tagsListWithIndeterminate!!.copyOfIndeterminateTagList())

        assertTrue(tagsListWithIndeterminate!!.toggleAllCheckedStatuses())

        assertEquals(TAGS, tagsListWithIndeterminate!!.copyOfAllTagList())
        assertSameElementsIgnoreOrder(TAGS, tagsListWithIndeterminate!!.copyOfCheckedTagList())
        assertEquals(emptyList<Any>(), tagsListWithIndeterminate!!.copyOfIndeterminateTagList())

        assertTrue(tagsListWithIndeterminate!!.toggleAllCheckedStatuses())
        assertEquals(TAGS, tagsListWithIndeterminate!!.copyOfAllTagList())
        assertSameElementsIgnoreOrder(ArrayList(), tagsListWithIndeterminate!!.copyOfCheckedTagList())
        assertEquals(emptyList<Any>(), tagsListWithIndeterminate!!.copyOfIndeterminateTagList())
    }

    @Test
    fun test_size_if_checked_have_no_extra_items_not_found_in_allTags() {
        assertEquals(TAGS.size, tagsList!!.size())
        assertEquals(TAGS.size, tagsListWithIndeterminate!!.size())
    }

    @Test
    fun test_size_if_checked_have_extra_items_not_found_in_allTags() {
        tagsList = TagsList(TAGS, join(CHECKED_TAGS, "NEW"))
        assertEquals((TAGS.size + 1), tagsList!!.size())
    }

    @Test
    fun test_size_if_unchecked_and_checked_have_extra_items_not_found_in_allTags() {
        tagsList = TagsList(TAGS, join(CHECKED_TAGS, "NEW"), join(UNCHECKED_TAGS, "ALSO_NEW"))
        assertEquals((TAGS.size + 2), tagsList!!.size())
    }

    @Test
    fun test_sort() {
        assertEquals(TAGS, tagsList!!.copyOfAllTagList())
        tagsList!!.sort()
        assertEquals(

            SORTED_TAGS, tagsList!!.copyOfAllTagList(), "Calling #sort on TagsList should result on sorting all tags"
        )
    }

    @Test
    fun test_sort_with_indeterminate_tags() {
        assertEquals(TAGS, tagsListWithIndeterminate!!.copyOfAllTagList())
        tagsListWithIndeterminate!!.sort()
        assertEquals(
            SORTED_TAGS, tagsListWithIndeterminate!!.copyOfAllTagList(),
            "Calling #sort on TagsList should result on sorting all tags"
        )
    }

    @Test // #8807
    @Ignore(
        "Collections.singletonList() triggers infinite recursion. " +
            "Need solution to only mock the sort() method."
    )
    fun test_sort_will_not_call_collectionsSort() {
        Mockito.mockStatic(Collections::class.java).use { MockCollection ->
            assertEquals(TAGS, tagsList!!.copyOfAllTagList())

            tagsList!!.sort()
            assertEquals(

                SORTED_TAGS, tagsList!!.copyOfAllTagList(), "Calling #sort on TagsList should result on sorting all tags"
            )

            MockCollection.verify({ Collections.sort(ArgumentMatchers.any(), ArgumentMatchers.any<Comparator<in Any>>()) }, Mockito.never())
        }
    }

    companion object {
        val SORTED_TAGS = Arrays.asList(
            "colors",
            "faces",
            "programming",
            "cars",
            "electrical",
            "flags",
            "learn",
            "meat",
            "names",
            "playground"
        )
        val TAGS = Arrays.asList(
            "programming",
            "learn",
            "names",
            "faces",
            "cars",
            "colors",
            "flags",
            "meat",
            "playground",
            "electrical"
        )
        val CHECKED_TAGS = Arrays.asList(
            "programming",
            "faces",
            "colors"
        )
        val UNCHECKED_TAGS = Arrays.asList(
            "electrical",
            "meat",
            "programming",
            "faces"
        )
        val INDETERMINATE_TAGS = Arrays.asList(
            "programming",
            "faces"
        )

        private fun <E> join(l1: List<E>, l2: List<E>): List<E> {
            val joined: MutableList<E> = ArrayList()
            joined.addAll(l1)
            joined.addAll(l2)
            return joined
        }

        private fun <E> join(l1: List<E>, e: E): List<E> {
            val joined: MutableList<E> = ArrayList(l1)
            joined.add(e)
            return joined
        }

        private fun <E> minus(l1: List<E>, e: E): List<E> {
            val res: MutableList<E> = ArrayList(l1)
            res.remove(e)
            return res
        }

        private fun <E> minus(l1: List<E>, el: List<E>): List<E> {
            val res: MutableList<E> = ArrayList(l1)
            for (e in el) {
                res.remove(e)
            }
            return res
        }

        private fun <E> assertSameElementsIgnoreOrder(l1: Collection<E>, l2: Collection<E>) {
            assertSameElementsIgnoreOrder(l2, l1, null)
        }

        private fun <E> assertSameElementsIgnoreOrder(
            l2: Collection<E>,
            l1: Collection<E>,
            message: String?
        ) {
            assertEquals(l1.size, l2.size, message)
            assertTrue(l1.containsAll(l2), message)
        }
    }
}
