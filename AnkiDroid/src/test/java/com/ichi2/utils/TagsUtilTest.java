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
package com.ichi2.utils;

import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static com.ichi2.utils.ListUtil.assertListEquals;
import static org.junit.Assert.*;

@RunWith(Enclosed.class)
public class TagsUtilTest {

    @RunWith(Parameterized.class)
    public static class GetUpdatedTagsTest {
        // suppressed to have a symmetry in all parameters, Arrays.asList(...) should be all you need.
        @SuppressWarnings("ArraysAsListWithZeroOrOneArgument")
        @Parameters
        public static Collection<Object[]> data() {
            return Arrays.asList(new Object[][] {
                    {
                            Arrays.asList(),
                            Arrays.asList(),
                            Arrays.asList(),
                            Arrays.asList(),
                    },
                    {
                            Arrays.asList("a"),
                            Arrays.asList("b", "c"),
                            Arrays.asList(),
                            Arrays.asList("b", "c"),
                    },
                    {
                            Arrays.asList("a"),
                            Arrays.asList("a", "b"),
                            Arrays.asList("c"),
                            Arrays.asList("a", "b"),
                    },
                    {
                            Arrays.asList("a"),
                            Arrays.asList("a", "b"),
                            Arrays.asList("c"),
                            Arrays.asList("a", "b"),
                    },
                    {
                            Arrays.asList("a","b", "c"),
                            Arrays.asList("a"),
                            Arrays.asList("b"),
                            Arrays.asList("a", "b"),
                    },
            });
        }


        @Parameter(0)
        public List<String> previous;
        @Parameter(1)
        public List<String> selected;
        @Parameter(2)
        public List<String> indeterminate;
        @Parameter(3)
        public List<String> updated;


        @Test
        public void test() {
            List<String> actual = TagsUtil.getUpdatedTags(previous, selected, indeterminate);
            assertListEquals(updated, actual);
        }
    }


    public static class HierarchyTagUtilsTest {
        @Test
        public void test_getTagParts() {
            assertEquals(Collections.singletonList("single"), TagsUtil.getTagParts("single"));
            assertEquals(Collections.singletonList(":single:"), TagsUtil.getTagParts(":single:"));

            assertEquals(Arrays.asList("first", "second"), TagsUtil.getTagParts("first::second"));
            assertEquals(Arrays.asList("first", "second:"), TagsUtil.getTagParts("first::second:"));
            assertEquals(Arrays.asList("first", "second", "blank"), TagsUtil.getTagParts("first::second::"));
            assertEquals(Arrays.asList("blank", "first", "blank", ":second", "blank"), TagsUtil.getTagParts("::first:::::second::"));
        }

        @Test
        public void test_getUniformedTag() {
            assertEquals("abc", TagsUtil.getUniformedTag("abc"));
            assertEquals("Should remove trailing '::'", "abc", TagsUtil.getUniformedTag("abc::"));
            assertEquals("Should replace empty immediate parts to 'blank'", "abc::def::blank", TagsUtil.getUniformedTag("abc::def::::"));
        }

        @Test
        public void test_getTagRoot() {
            assertEquals("abc", TagsUtil.getTagRoot("abc"));
            assertEquals("abc:", TagsUtil.getTagRoot("abc:"));
            assertEquals("abc", TagsUtil.getTagRoot("abc::def::ghi"));
            assertEquals("blank", TagsUtil.getTagRoot("::careless"));
        }

        @Test
        public void test_getTagAncestors() {
            assertEquals(Arrays.asList("foo", "foo::bar", "foo::bar::aaa"), TagsUtil.getTagAncestors("foo::bar::aaa::bbb"));
            assertEquals(Arrays.asList("foo", "foo::blank"), TagsUtil.getTagAncestors("foo::::aaa"));
            assertEquals(Arrays.asList("blank", "blank::foo"), TagsUtil.getTagAncestors("::foo::aaa"));
        }

        @Test
        public void test_compareTag() {
            assertTrue(TagsUtil.compareTag("aaa::foo", "bbb::bar") < 0);
            assertTrue(TagsUtil.compareTag("aaa::foo", "aaa::bar") > 0);
            assertTrue(TagsUtil.compareTag("aaa::foo", "aaa::foo::trailing") < 0);
            assertTrue(TagsUtil.compareTag("aaa::bbbb", "aaa::bbb::trailing") > 0);
            assertTrue(TagsUtil.compareTag("aaa::bbbz::foo", "aaa::bbb::bar") > 0);
            assertTrue(TagsUtil.compareTag("aaa::bbb9::foo", "aaa::bbb::bar") > 0);
            assertEquals(0, TagsUtil.compareTag("aaa::bbb", "aaa::bbb"));
            assertEquals(0, TagsUtil.compareTag("aAa::bbb", "aaa::bBb"));
        }
    }
}

