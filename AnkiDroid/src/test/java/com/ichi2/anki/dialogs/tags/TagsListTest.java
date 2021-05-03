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


    static final List<String> SORTED_TAGS = Arrays.asList(
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

    static final List<String> TAGS = Arrays.asList(
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


    static final List<String> CHECKED_TAGS = Arrays.asList(
            "programming",
            "faces",
            "colors"
    );


    static final List<String> UNCHECKED_TAGS = Arrays.asList(
            "electrical",
            "meat",
            "programming",
            "faces"
    );


    static final List<String> INDETERMINATE_TAGS = Arrays.asList(
            "programming",
            "faces"
    );


    TagsList mTagsList;
    TagsList mTagsListWithIndeterminate;


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
        mTagsList = new TagsList(TAGS, CHECKED_TAGS);
        mTagsListWithIndeterminate = new TagsList(TAGS, CHECKED_TAGS, UNCHECKED_TAGS);
    }


    @Test
    public void test_constructor_will_remove_dups() {
        List<String> allTags = Arrays.asList("a", "b", "a");
        List<String> checkedTags = Arrays.asList("b", "b", "b");
        TagsList list = new TagsList(
                allTags,
                checkedTags
        );

        assertEquals("All tags list should not contain any duplicates",
                Arrays.asList("a", "b"), list.copyOfAllTagList());
        assertEquals("Checked tags list should not contain any duplicates",
                Arrays.asList("b"), list.copyOfCheckedTagList());
    }


    @Test
    public void test_constructor_will_remove_dups_unchecked() {
        List<String> allTags = Arrays.asList("a", "b", "a", "c", "c", "d");
        List<String> checkedTags = Arrays.asList("b", "b", "b");
        List<String> uncheckedTags = Arrays.asList("c", "c", "d");
        TagsList list = new TagsList(
                allTags,
                checkedTags,
                uncheckedTags
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
        List<String> allTags = Arrays.asList("aA", "bb", "aa");
        List<String> checkedTags = Arrays.asList("bb", "Bb", "bB");
        TagsList list = new TagsList(
                allTags,
                checkedTags
        );

        assertEquals("All tags list should not contain any duplicates (case insensitive)",
                Arrays.asList("aA", "bb"), list.copyOfAllTagList());
        assertEquals("Checked tags list should not contain any duplicates  (case insensitive)",
                Arrays.asList("bb"), list.copyOfCheckedTagList());
    }

    @Test
    public void test_constructor_will_ignore_casing_unchecked() {
        List<String> allTags = Arrays.asList("aA", "bb", "aa", "cc", "dd");
        List<String> checkedTags = Arrays.asList("bb", "Bb", "bB", "dd", "ff");
        List<String> uncheckedTags = Arrays.asList("BB", "cC", "cC", "dD", "CC");
        TagsList list = new TagsList(
                allTags,
                checkedTags,
                uncheckedTags
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
        List<String> allTags = Arrays.asList("aA", "bb", "aa");
        List<String> checkedTags = Arrays.asList("bb", "Bb", "bB", "cc");
        TagsList list = new TagsList(
                allTags,
                checkedTags
        );

        assertEquals("Extra tags in checked not found in all tags, must be added to all tags list",
                Arrays.asList("aA", "bb", "cc"), list.copyOfAllTagList());
        assertEquals("Extra tags in checked not found in all tags, must be found when retrieving checked tag list",
                Arrays.asList("bb","cc"), list.copyOfCheckedTagList());
    }


    @Test
    public void test_constructor_will_add_checked_and_unchecked_to_all() {
        List<String> allTags = Arrays.asList("aA", "bb", "aa");
        List<String> checkedTags = Arrays.asList("bb", "Bb", "bB", "Cc", "zz");
        List<String> uncheckedTags = Arrays.asList("BB", "cC", "cC", "dD", "CC");
        TagsList list = new TagsList(
                allTags,
                checkedTags,
                uncheckedTags
        );

        assertEquals("Extra tags in checked not found in all tags, must be added to all tags list",
                Arrays.asList("aA", "bb", "Cc", "zz", "dD"), list.copyOfAllTagList());
        assertEquals("Extra tags in checked not found in all tags, must be found when retrieving checked tag list",
                Arrays.asList("zz"), list.copyOfCheckedTagList());
        assertEquals(Arrays.asList("bb", "Cc"), list.copyOfIndeterminateTagList());
    }


    @Test
    public void test_isChecked_index() {
        assertTrue("Tag at index 0 should be checked", mTagsList.isChecked(0));
        assertTrue("Tag at index 3 should be checked", mTagsList.isChecked(3));
        assertFalse("Tag at index 1 should be unchecked", mTagsList.isChecked(1));
        assertFalse("Tag at index 6 should be unchecked", mTagsList.isChecked(6));

        // indeterminate tags
        assertFalse("Tag at index 0 should be unchecked", mTagsListWithIndeterminate.isChecked(0));
        assertFalse("Tag at index 3 should be unchecked", mTagsListWithIndeterminate.isChecked(3));
    }


    @Test
    public void test_isChecked_object() {
        assertTrue("'programming' tag should be checked", mTagsList.isChecked("programming"));
        assertTrue("'faces' tag should be checked", mTagsList.isChecked("faces"));
        assertFalse("'cars' tag should be unchecked", mTagsList.isChecked("cars"));
        assertFalse("'flags' tag should be unchecked", mTagsList.isChecked("flags"));

        // indeterminate tags
        assertFalse("Tag at index 'programming' should be unchecked", mTagsListWithIndeterminate.isChecked("programming"));
        assertFalse("Tag at index 'faces' should be unchecked", mTagsListWithIndeterminate.isChecked("faces"));
    }


    @Test
    public void test_isIndeterminate_index() {
        assertFalse("Tag at index 0 should be checked (not indeterminate)", mTagsList.isIndeterminate(0));
        assertFalse("Tag at index 3 should be checked (not indeterminate)", mTagsList.isIndeterminate(3));
        assertFalse("Tag at index 1 should be unchecked (not indeterminate)", mTagsList.isIndeterminate(1));
        assertFalse("Tag at index 6 should be unchecked (not indeterminate)", mTagsList.isIndeterminate(6));


        assertTrue("Tag at index 0 should be indeterminate", mTagsListWithIndeterminate.isIndeterminate(0));
        assertTrue("Tag at index 3 should be indeterminate", mTagsListWithIndeterminate.isIndeterminate(3));
        assertFalse("Tag at index 1 should be unchecked (not indeterminate)", mTagsListWithIndeterminate.isIndeterminate(1));
        assertFalse("Tag at index 6 should be unchecked (not indeterminate)", mTagsListWithIndeterminate.isIndeterminate(6));
        assertFalse("Tag at index 6 should be unchecked (not indeterminate)", mTagsListWithIndeterminate.isIndeterminate(5));
    }


    @Test
    public void test_isIndeterminate_object() {
        assertFalse("'programming' tag should be checked (not indeterminate)", mTagsList.isIndeterminate("programming"));
        assertFalse("'faces' tag should be checked (not indeterminate)", mTagsList.isIndeterminate("faces"));
        assertFalse("'cars' tag should be unchecked (not indeterminate)", mTagsList.isIndeterminate("cars"));
        assertFalse("'flags' tag should be unchecked (not indeterminate)", mTagsList.isIndeterminate("flags"));

        assertTrue("Tag 'programming' should be indeterminate", mTagsListWithIndeterminate.isIndeterminate("programming"));
        assertTrue("Tag 'faces' should be indeterminate", mTagsListWithIndeterminate.isIndeterminate("faces"));
        assertFalse("Tag 'cars' should be unchecked (not indeterminate)", mTagsListWithIndeterminate.isIndeterminate("cars"));
        assertFalse("Tag 'flags' should be unchecked (not indeterminate)", mTagsListWithIndeterminate.isIndeterminate("flags"));
    }


    @Test
    public void test_add() {
        assertTrue("Adding 'anki' tag should return true, as the 'anki' is a new tag",
                mTagsList.add("anki"));
        assertFalse("Adding 'colors' tag should return false, as the 'colors' is a already existing tag",
                mTagsList.add("colors"));

        assertEquals("The newly added 'anki' tag should be found when retrieving all tags list",
                join(TAGS, "anki"), mTagsList.copyOfAllTagList());
        assertSameElementsIgnoreOrder("Adding operations should have nothing to do with the checked status of tags",
                CHECKED_TAGS, mTagsList.copyOfCheckedTagList());
    }


    @Test
    public void test_check() {
        assertFalse("Attempting to check tag 'anki' should return false, as 'anki' is not found in all tags list",
                mTagsList.check("anki")); //not in the list
        assertFalse("Attempting to check tag 'colors' should return false, as 'colors' is already checked",
                mTagsList.check("colors")); // already checked
        assertTrue("Attempting to check tag 'flags' should return true, as 'flags' is found in all tags and is not already checked",
                mTagsList.check("flags"));


        assertEquals("Changing the status of tags to be checked should have noting to do with all tag list",
                TAGS, mTagsList.copyOfAllTagList());//no change
        assertSameElementsIgnoreOrder("The checked 'flags' tag should be found when retrieving list of checked tag",
                join(CHECKED_TAGS, "flags"), mTagsList.copyOfCheckedTagList());
    }


    @Test
    public void test_check_with_indeterminate_tags_list() {
        assertTrue("Attempting to check tag 'faces' should return true, as 'faces' is found in all tags and it have indeterminate state",
                mTagsListWithIndeterminate.check("faces"));

        assertEquals("Changing the status of tags to be checked should have noting to do with all tag list",
                TAGS, mTagsListWithIndeterminate.copyOfAllTagList());
        assertTrue("The checked 'faces' tag should be found when retrieving list of checked tag",
                mTagsListWithIndeterminate.copyOfCheckedTagList().contains("faces"));
        assertFalse("The checked 'faces' tag should not be found when retrieving list of indeterminate tags",
                mTagsListWithIndeterminate.copyOfIndeterminateTagList().contains("faces"));
    }


    @Test
    public void test_uncheck() {
        assertFalse("Attempting to uncheck tag 'anki' should return false, as 'anki' is not found in all tags list",
                mTagsList.uncheck("anki")); //not in the list
        assertFalse("Attempting to uncheck tag 'flags' should return false, as 'flags' is already unchecked",
                mTagsList.uncheck("flags")); // already unchecked
        assertTrue("Attempting to uncheck tag 'colors' should return true, as 'colors' is found in all tags and is checked",
                mTagsList.uncheck("colors"));


        assertEquals("Changing the status of tags to be unchecked should have noting to do with all tag list",
                TAGS, mTagsList.copyOfAllTagList());//no change
        assertSameElementsIgnoreOrder("The unchecked 'colors' tag should be not be found when retrieving list of checked tag",
                minus(CHECKED_TAGS, "colors"), mTagsList.copyOfCheckedTagList());
    }


    @Test
    public void test_uncheck_indeterminate_tags_list() {
        assertTrue("Attempting to uncheck tag 'programming' should return true, as 'programming' is found in all tags and it have indeterminate state",
                mTagsListWithIndeterminate.uncheck("programming"));


        assertEquals("Changing the status of tags to be checked should have noting to do with all tag list",
                TAGS, mTagsListWithIndeterminate.copyOfAllTagList());
        assertFalse("Changing from indeterminate to unchecked should not affect checked tags",
                mTagsListWithIndeterminate.copyOfCheckedTagList().contains("programming"));
        assertFalse("The checked 'programming' tag should not be found when retrieving list of indeterminate tags",
                mTagsListWithIndeterminate.copyOfIndeterminateTagList().contains("programming"));
    }


    @Test
    public void test_toggleAllCheckedStatuses() {
        assertEquals(TAGS, mTagsList.copyOfAllTagList());
        assertSameElementsIgnoreOrder(CHECKED_TAGS, mTagsList.copyOfCheckedTagList());

        assertTrue(mTagsList.toggleAllCheckedStatuses());

        assertEquals(TAGS, mTagsList.copyOfAllTagList());
        assertSameElementsIgnoreOrder(TAGS, mTagsList.copyOfCheckedTagList());

        assertTrue(mTagsList.toggleAllCheckedStatuses());

        assertEquals(TAGS, mTagsList.copyOfAllTagList());
        assertSameElementsIgnoreOrder(new ArrayList<>(), mTagsList.copyOfCheckedTagList());
    }

    @Test
    public void test_toggleAllCheckedStatuses_indeterminate() {
        assertEquals(TAGS, mTagsListWithIndeterminate.copyOfAllTagList());
        assertSameElementsIgnoreOrder(
                minus(CHECKED_TAGS, INDETERMINATE_TAGS),
                mTagsListWithIndeterminate.copyOfCheckedTagList());

        assertNotEquals(Collections.emptyList(), mTagsListWithIndeterminate.copyOfIndeterminateTagList());

        assertTrue(mTagsListWithIndeterminate.toggleAllCheckedStatuses());

        assertEquals(TAGS, mTagsListWithIndeterminate.copyOfAllTagList());
        assertSameElementsIgnoreOrder(TAGS, mTagsListWithIndeterminate.copyOfCheckedTagList());
        assertEquals(Collections.emptyList(), mTagsListWithIndeterminate.copyOfIndeterminateTagList());

        assertTrue(mTagsListWithIndeterminate.toggleAllCheckedStatuses());

        assertEquals(TAGS, mTagsListWithIndeterminate.copyOfAllTagList());
        assertSameElementsIgnoreOrder(new ArrayList<>(), mTagsListWithIndeterminate.copyOfCheckedTagList());
        assertEquals(Collections.emptyList(), mTagsListWithIndeterminate.copyOfIndeterminateTagList());
    }


    @Test
    public void test_size_if_checked_have_no_extra_items_not_found_in_allTags() {
        assertEquals(TAGS.size(), mTagsList.size());
        assertEquals(TAGS.size(), mTagsListWithIndeterminate.size());
    }

    @Test
    public void test_size_if_checked_have_extra_items_not_found_in_allTags() {
        mTagsList = new TagsList(TAGS, join(CHECKED_TAGS, "NEW"));
        assertEquals(TAGS.size() + 1, mTagsList.size());
    }


    @Test
    public void test_size_if_unchecked_and_checked_have_extra_items_not_found_in_allTags() {
        mTagsList = new TagsList(TAGS, join(CHECKED_TAGS, "NEW"), join(UNCHECKED_TAGS, "ALSO_NEW"));
        assertEquals(TAGS.size() + 2, mTagsList.size());
    }


    @Test
    public void test_sort() {
        assertEquals(TAGS, mTagsList.copyOfAllTagList());
        mTagsList.sort();
        assertEquals("Calling #sort on TagsList should result on sorting all tags",
                SORTED_TAGS, mTagsList.copyOfAllTagList());
    }


    @Test
    public void test_sort_with_indeterminate_tags() {
        assertEquals(TAGS, mTagsListWithIndeterminate.copyOfAllTagList());
        mTagsListWithIndeterminate.sort();
        assertEquals("Calling #sort on TagsList should result on sorting all tags",
                SORTED_TAGS, mTagsListWithIndeterminate.copyOfAllTagList());
    }

    @Test //#8807
    public void test_sort_will_not_call_collectionsSort() {
        try (MockedStatic<Collections> MockCollection = mockStatic(Collections.class)) {

            assertEquals(TAGS, mTagsList.copyOfAllTagList());
            mTagsList.sort();
            assertEquals("Calling #sort on TagsList should result on sorting all tags",
                    SORTED_TAGS, mTagsList.copyOfAllTagList());

            MockCollection.verify(() -> Collections.sort(any(), any()), never());
        }
    }
}