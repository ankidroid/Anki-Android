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
            "flags",
            "learn",
            "names"
    );

    final List<String> TAGS = Arrays.asList(
            "programming",
            "learn",
            "names",
            "faces",
            "cars",
            "colors",
            "flags"
    );


    final List<String> CHECKED_TAGS = Arrays.asList(
            "programming",
            "faces",
            "colors"
    );


    TagsList TAGS_LIST;


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
    public void test_isChecked_index() {
        assertTrue("Tag at index 0 should be checked", TAGS_LIST.isChecked(0));
        assertTrue("Tag at index 3 should be checked", TAGS_LIST.isChecked(3));
        assertFalse("Tag at index 1 should be unchecked", TAGS_LIST.isChecked(1));
        assertFalse("Tag at index 6 should be unchecked", TAGS_LIST.isChecked(6));
    }


    @Test
    public void test_isChecked_object() {
        assertTrue("'programming' tag should be checked", TAGS_LIST.isChecked("programming"));
        assertTrue("'faces' tag should be checked", TAGS_LIST.isChecked("faces"));
        assertFalse("'cars' tag should be unchecked", TAGS_LIST.isChecked("cars"));
        assertFalse("'flags' tag should be unchecked", TAGS_LIST.isChecked("flags"));
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
    public void test_size_if_checked_have_no_extra_items_not_found_in_allTags() {
        assertEquals(TAGS.size(), TAGS_LIST.size());
    }

    @Test
    public void test_size_if_checked_have_extra_items_not_found_in_allTags() {
        TAGS_LIST = new TagsList(TAGS, join(CHECKED_TAGS, "NEW"));
        assertEquals(TAGS.size() + 1, TAGS_LIST.size());
    }


    @Test
    public void test_sort() {
        assertEquals(TAGS, TAGS_LIST.copyOfAllTagList());
        TAGS_LIST.sort();
        assertEquals("Calling #sort on TagsList should result on sorting all tags",
                SORTED_TAGS, TAGS_LIST.copyOfAllTagList());
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