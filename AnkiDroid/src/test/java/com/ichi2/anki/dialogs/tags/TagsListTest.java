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
package com.ichi2.anki.dialogs.tags;

import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;

// suppressed to have a symmetry in all tests, Arrays.asList(...) should be all you need.
@SuppressWarnings("ArraysAsListWithZeroOrOneArgument")
public class TagsListTest {


    final List<String> SORTED_TAGS = Arrays.asList(
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
    );

    final List<String> TAGS = Arrays.asList(
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
    );


    final List<String> CHECKED_TAGS = Arrays.asList(
            "programming",
            "faces",
            "colors"
    );


    final List<String> UNCHECKED_TAGS = Arrays.asList(
            "electrical",
            "meat",
            "programming",
            "faces"
    );


    final List<String> INDETERMINATE_TAGS = Arrays.asList(
            "programming",
            "faces"
    );


    TagsList TAGS_LIST;
    TagsList TAGS_LIST_WITH_INDETERMINATE;


    private static <E> List<E> join(List<E> l1, List<E> l2) {
        final List<E> joined = new ArrayList<>();
        joined.addAll(l1);
        joined.addAll(l2);
        return joined;
    }


    private static <E> List<E> join(List<E> l1, E e) {
        final List<E> joined = new ArrayList<>(l1);
        joined.add(e);
        return joined;
    }


    private static <E> List<E> minus(List<E> l1, E e) {
        final List<E> res = new ArrayList<>(l1);
        res.remove(e);
        return res;
    }


    private static <E> List<E> minus(List<E> l1, List<E> el) {
        final List<E> res = new ArrayList<>(l1);
        for (E e : el) {
            res.remove(e);
        }
        return res;
    }


    private static <E> void assertSameElementsIgnoreOrder(Collection<E> l1, Collection<E> l2) {
        assertSameElementsIgnoreOrder(null, l1, l2);
    }


    private static <E> void assertSameElementsIgnoreOrder(String message, Collection<E> l1, Collection<E> l2) {
        assertEquals(message, l1.size(), l2.size());
        assertTrue(message, l1.containsAll(l2));
    }


    @Before
    public void setUp() throws Exception {
        TAGS_LIST = new TagsList(TAGS, CHECKED_TAGS);
        TAGS_LIST_WITH_INDETERMINATE = new TagsList(TAGS, CHECKED_TAGS, UNCHECKED_TAGS);
    }


    @Test
    public void test_constructor_will_remove_dups() {
        TagsList list = new TagsList(
                Arrays.asList("a", "b", "a"),
                Arrays.asList("b", "b", "b")
        );

        assertEquals("All tags list should not contain any duplicates",
                Arrays.asList("a", "b"), list.copyOfAllTagList());
        assertEquals("Checked tags list should not contain any duplicates",
                Arrays.asList("b"), list.copyOfCheckedTagList());
    }


    @Test
    public void test_constructor_will_remove_dups_unchecked() {
        TagsList list = new TagsList(
                Arrays.asList("a", "b", "a", "c", "c", "d"),
                Arrays.asList("b", "b", "b"),
                Arrays.asList("c", "c", "d")
        );

        assertEquals("All tags list should not contain any duplicates",
                Arrays.asList("a", "b", "c", "d"), list.copyOfAllTagList());
        assertEquals("Checked tags list should not contain any duplicates",
                Arrays.asList("b"), list.copyOfCheckedTagList());
        assertEquals("indeterminate tags list should be empty",
                Arrays.asList(), list.copyOfIndeterminateTagList());
    }


    @Test
    public void test_constructor_will_ignore_casing() {
        TagsList list = new TagsList(
                Arrays.asList("aA", "bb", "aa"),
                Arrays.asList("bb", "Bb", "bB")
        );

        assertEquals("All tags list should not contain any duplicates (case insensitive)",
                Arrays.asList("aA", "bb"), list.copyOfAllTagList());
        assertEquals("Checked tags list should not contain any duplicates  (case insensitive)",
                Arrays.asList("bb"), list.copyOfCheckedTagList());
    }

    @Test
    public void test_constructor_will_ignore_casing_unchecked() {
        TagsList list = new TagsList(
                Arrays.asList("aA", "bb", "aa", "cc", "dd"),
                Arrays.asList("bb", "Bb", "bB", "dd", "ff"),
                Arrays.asList("BB", "cC", "cC", "dD", "CC")
        );

        assertEquals("All tags list should not contain any duplicates (case insensitive)",
                Arrays.asList("aA", "bb", "cc", "dd", "ff"), list.copyOfAllTagList());
        assertEquals("Checked tags list should not contain any duplicates  (case insensitive)",
                Arrays.asList("ff"), list.copyOfCheckedTagList());
        assertEquals("Checked tags list should not contain any duplicates  (case insensitive)\n" +
                "and IndeterminateTagList is correct",
                Arrays.asList("bb","dd"), list.copyOfIndeterminateTagList());
    }

    @Test
    public void test_constructor_will_add_checked_to_all() {
        TagsList list = new TagsList(
                Arrays.asList("aA", "bb", "aa"),
                Arrays.asList("bb", "Bb", "bB", "cc")
        );

        assertEquals("Extra tags in checked not found in all tags, must be added to all tags list",
                Arrays.asList("aA", "bb", "cc"), list.copyOfAllTagList());
        assertEquals("Extra tags in checked not found in all tags, must be found when retrieving checked tag list",
                Arrays.asList("bb","cc"), list.copyOfCheckedTagList());
    }


    @Test
    public void test_constructor_will_add_checked_and_unchecked_to_all() {
        TagsList list = new TagsList(
                Arrays.asList("aA", "bb", "aa"),
                Arrays.asList("bb", "Bb", "bB", "Cc", "zz"),
                Arrays.asList("BB", "cC", "cC", "dD", "CC")
        );

        assertEquals("Extra tags in checked not found in all tags, must be added to all tags list",
                Arrays.asList("aA", "bb", "Cc", "zz", "dD"), list.copyOfAllTagList());
        assertEquals("Extra tags in checked not found in all tags, must be found when retrieving checked tag list",
                Arrays.asList("zz"), list.copyOfCheckedTagList());
        assertEquals(Arrays.asList("bb", "Cc"), list.copyOfIndeterminateTagList());
    }


    @Test
    public void test_isChecked_index() {
        assertTrue("Tag at index 0 should be checked", TAGS_LIST.isChecked(0));
        assertTrue("Tag at index 3 should be checked", TAGS_LIST.isChecked(3));
        assertFalse("Tag at index 1 should be unchecked", TAGS_LIST.isChecked(1));
        assertFalse("Tag at index 6 should be unchecked", TAGS_LIST.isChecked(6));

        // indeterminate tags
        assertFalse("Tag at index 0 should be unchecked", TAGS_LIST_WITH_INDETERMINATE.isChecked(0));
        assertFalse("Tag at index 3 should be unchecked", TAGS_LIST_WITH_INDETERMINATE.isChecked(3));
    }


    @Test
    public void test_isChecked_object() {
        assertTrue("'programming' tag should be checked", TAGS_LIST.isChecked("programming"));
        assertTrue("'faces' tag should be checked", TAGS_LIST.isChecked("faces"));
        assertFalse("'cars' tag should be unchecked", TAGS_LIST.isChecked("cars"));
        assertFalse("'flags' tag should be unchecked", TAGS_LIST.isChecked("flags"));

        // indeterminate tags
        assertFalse("Tag at index 'programming' should be unchecked", TAGS_LIST_WITH_INDETERMINATE.isChecked("programming"));
        assertFalse("Tag at index 'faces' should be unchecked", TAGS_LIST_WITH_INDETERMINATE.isChecked("faces"));
    }


    @Test
    public void test_isIndeterminate_index() {
        assertFalse("Tag at index 0 should be checked (not indeterminate)", TAGS_LIST.isIndeterminate(0));
        assertFalse("Tag at index 3 should be checked (not indeterminate)", TAGS_LIST.isIndeterminate(3));
        assertFalse("Tag at index 1 should be unchecked (not indeterminate)", TAGS_LIST.isIndeterminate(1));
        assertFalse("Tag at index 6 should be unchecked (not indeterminate)", TAGS_LIST.isIndeterminate(6));


        assertTrue("Tag at index 0 should be indeterminate", TAGS_LIST_WITH_INDETERMINATE.isIndeterminate(0));
        assertTrue("Tag at index 3 should be indeterminate", TAGS_LIST_WITH_INDETERMINATE.isIndeterminate(3));
        assertFalse("Tag at index 1 should be unchecked (not indeterminate)", TAGS_LIST_WITH_INDETERMINATE.isIndeterminate(1));
        assertFalse("Tag at index 6 should be unchecked (not indeterminate)", TAGS_LIST_WITH_INDETERMINATE.isIndeterminate(6));
        assertFalse("Tag at index 6 should be unchecked (not indeterminate)", TAGS_LIST_WITH_INDETERMINATE.isIndeterminate(5));
    }


    @Test
    public void test_isIndeterminate_object() {
        assertFalse("'programming' tag should be checked (not indeterminate)", TAGS_LIST.isIndeterminate("programming"));
        assertFalse("'faces' tag should be checked (not indeterminate)", TAGS_LIST.isIndeterminate("faces"));
        assertFalse("'cars' tag should be unchecked (not indeterminate)", TAGS_LIST.isIndeterminate("cars"));
        assertFalse("'flags' tag should be unchecked (not indeterminate)", TAGS_LIST.isIndeterminate("flags"));

        assertTrue("Tag 'programming' should be indeterminate", TAGS_LIST_WITH_INDETERMINATE.isIndeterminate("programming"));
        assertTrue("Tag 'faces' should be indeterminate", TAGS_LIST_WITH_INDETERMINATE.isIndeterminate("faces"));
        assertFalse("Tag 'cars' should be unchecked (not indeterminate)", TAGS_LIST_WITH_INDETERMINATE.isIndeterminate("cars"));
        assertFalse("Tag 'flags' should be unchecked (not indeterminate)", TAGS_LIST_WITH_INDETERMINATE.isIndeterminate("flags"));
    }


    @Test
    public void test_add() {
        assertTrue("Adding 'anki' tag should return true, as the 'anki' is a new tag",
                TAGS_LIST.add("anki"));
        assertFalse("Adding 'colors' tag should return false, as the 'colors' is a already existing tag",
                TAGS_LIST.add("colors"));

        assertEquals("The newly added 'anki' tag should be found when retrieving all tags list",
                join(TAGS, "anki"), TAGS_LIST.copyOfAllTagList());
        assertSameElementsIgnoreOrder("Adding operations should have nothing to do with the checked status of tags",
                CHECKED_TAGS, TAGS_LIST.copyOfCheckedTagList());
    }


    @Test
    public void test_check() {
        assertFalse("Attempting to check tag 'anki' should return false, as 'anki' is not found in all tags list",
                TAGS_LIST.check("anki")); //not in the list
        assertFalse("Attempting to check tag 'colors' should return false, as 'colors' is already checked",
                TAGS_LIST.check("colors")); // already checked
        assertTrue("Attempting to check tag 'flags' should return true, as 'flags' is found in all tags and is not already checked",
                TAGS_LIST.check("flags"));


        assertEquals("Changing the status of tags to be checked should have noting to do with all tag list",
                TAGS, TAGS_LIST.copyOfAllTagList());//no change
        assertSameElementsIgnoreOrder("The checked 'flags' tag should be found when retrieving list of checked tag",
                join(CHECKED_TAGS, "flags"), TAGS_LIST.copyOfCheckedTagList());
    }


    @Test
    public void test_check_with_indeterminate_tags_list() {
        assertTrue("Attempting to check tag 'faces' should return true, as 'faces' is found in all tags and it have indeterminate state",
                TAGS_LIST_WITH_INDETERMINATE.check("faces"));

        assertEquals("Changing the status of tags to be checked should have noting to do with all tag list",
                TAGS, TAGS_LIST_WITH_INDETERMINATE.copyOfAllTagList());
        assertTrue("The checked 'faces' tag should be found when retrieving list of checked tag",
                TAGS_LIST_WITH_INDETERMINATE.copyOfCheckedTagList().contains("faces"));
        assertFalse("The checked 'faces' tag should not be found when retrieving list of indeterminate tags",
                TAGS_LIST_WITH_INDETERMINATE.copyOfIndeterminateTagList().contains("faces"));
    }


    @Test
    public void test_uncheck() {
        assertFalse("Attempting to uncheck tag 'anki' should return false, as 'anki' is not found in all tags list",
                TAGS_LIST.uncheck("anki")); //not in the list
        assertFalse("Attempting to uncheck tag 'flags' should return false, as 'flags' is already unchecked",
                TAGS_LIST.uncheck("flags")); // already unchecked
        assertTrue("Attempting to uncheck tag 'colors' should return true, as 'colors' is found in all tags and is checked",
                TAGS_LIST.uncheck("colors"));


        assertEquals("Changing the status of tags to be unchecked should have noting to do with all tag list",
                TAGS, TAGS_LIST.copyOfAllTagList());//no change
        assertSameElementsIgnoreOrder("The unchecked 'colors' tag should be not be found when retrieving list of checked tag",
                minus(CHECKED_TAGS, "colors"), TAGS_LIST.copyOfCheckedTagList());
    }


    @Test
    public void test_uncheck_indeterminate_tags_list() {
        assertTrue("Attempting to uncheck tag 'programming' should return true, as 'programming' is found in all tags and it have indeterminate state",
                TAGS_LIST_WITH_INDETERMINATE.uncheck("programming"));


        assertEquals("Changing the status of tags to be checked should have noting to do with all tag list",
                TAGS, TAGS_LIST_WITH_INDETERMINATE.copyOfAllTagList());
        assertFalse("Changing from indeterminate to unchecked should not affect checked tags",
                TAGS_LIST_WITH_INDETERMINATE.copyOfCheckedTagList().contains("programming"));
        assertFalse("The checked 'programming' tag should not be found when retrieving list of indeterminate tags",
                TAGS_LIST_WITH_INDETERMINATE.copyOfIndeterminateTagList().contains("programming"));
    }


    @Test
    public void test_toggleAllCheckedStatuses() {
        assertEquals(TAGS, TAGS_LIST.copyOfAllTagList());
        assertSameElementsIgnoreOrder(CHECKED_TAGS, TAGS_LIST.copyOfCheckedTagList());

        assertTrue(TAGS_LIST.toggleAllCheckedStatuses());

        assertEquals(TAGS, TAGS_LIST.copyOfAllTagList());
        assertSameElementsIgnoreOrder(TAGS, TAGS_LIST.copyOfCheckedTagList());

        assertTrue(TAGS_LIST.toggleAllCheckedStatuses());

        assertEquals(TAGS, TAGS_LIST.copyOfAllTagList());
        assertSameElementsIgnoreOrder(new ArrayList<>(), TAGS_LIST.copyOfCheckedTagList());
    }

    @Test
    public void test_toggleAllCheckedStatuses_indeterminate() {
        assertEquals(TAGS, TAGS_LIST_WITH_INDETERMINATE.copyOfAllTagList());
        assertSameElementsIgnoreOrder(
                minus(CHECKED_TAGS, INDETERMINATE_TAGS),
                TAGS_LIST_WITH_INDETERMINATE.copyOfCheckedTagList());

        assertNotEquals(Collections.emptyList(), TAGS_LIST_WITH_INDETERMINATE.copyOfIndeterminateTagList());

        assertTrue(TAGS_LIST_WITH_INDETERMINATE.toggleAllCheckedStatuses());

        assertEquals(TAGS, TAGS_LIST_WITH_INDETERMINATE.copyOfAllTagList());
        assertSameElementsIgnoreOrder(TAGS, TAGS_LIST_WITH_INDETERMINATE.copyOfCheckedTagList());
        assertEquals(Collections.emptyList(), TAGS_LIST_WITH_INDETERMINATE.copyOfIndeterminateTagList());

        assertTrue(TAGS_LIST_WITH_INDETERMINATE.toggleAllCheckedStatuses());

        assertEquals(TAGS, TAGS_LIST_WITH_INDETERMINATE.copyOfAllTagList());
        assertSameElementsIgnoreOrder(new ArrayList<>(), TAGS_LIST_WITH_INDETERMINATE.copyOfCheckedTagList());
        assertEquals(Collections.emptyList(), TAGS_LIST_WITH_INDETERMINATE.copyOfIndeterminateTagList());
    }


    @Test
    public void test_size_if_checked_have_no_extra_items_not_found_in_allTags() {
        assertEquals(TAGS.size(), TAGS_LIST.size());
        assertEquals(TAGS.size(), TAGS_LIST_WITH_INDETERMINATE.size());
    }

    @Test
    public void test_size_if_checked_have_extra_items_not_found_in_allTags() {
        TAGS_LIST = new TagsList(TAGS, join(CHECKED_TAGS, "NEW"));
        assertEquals(TAGS.size() + 1, TAGS_LIST.size());
    }


    @Test
    public void test_size_if_unchecked_and_checked_have_extra_items_not_found_in_allTags() {
        TAGS_LIST = new TagsList(TAGS, join(CHECKED_TAGS, "NEW"), join(UNCHECKED_TAGS, "ALSO_NEW"));
        assertEquals(TAGS.size() + 2, TAGS_LIST.size());
    }


    @Test
    public void test_sort() {
        assertEquals(TAGS, TAGS_LIST.copyOfAllTagList());
        TAGS_LIST.sort();
        assertEquals("Calling #sort on TagsList should result on sorting all tags",
                SORTED_TAGS, TAGS_LIST.copyOfAllTagList());
    }


    @Test
    public void test_sort_with_indeterminate_tags() {
        assertEquals(TAGS, TAGS_LIST_WITH_INDETERMINATE.copyOfAllTagList());
        TAGS_LIST_WITH_INDETERMINATE.sort();
        assertEquals("Calling #sort on TagsList should result on sorting all tags",
                SORTED_TAGS, TAGS_LIST_WITH_INDETERMINATE.copyOfAllTagList());
    }

    @Test //#8807
    public void test_sort_will_not_call_collectionsSort() {
        try (MockedStatic<Collections> MockCollection = mockStatic(Collections.class)) {

            assertEquals(TAGS, TAGS_LIST.copyOfAllTagList());
            TAGS_LIST.sort();
            assertEquals("Calling #sort on TagsList should result on sorting all tags",
                    SORTED_TAGS, TAGS_LIST.copyOfAllTagList());

            MockCollection.verify(() -> Collections.sort(any(), any()), never());
        }
    }
}