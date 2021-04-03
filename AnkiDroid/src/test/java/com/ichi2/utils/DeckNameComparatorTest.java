/*
 Copyright (c) 2021 Akshay Jadhav <jadhavakshay0701@gmail.com>
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

import junit.framework.TestCase;

import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class DeckNameComparatorTest extends TestCase {
    @Test
    public void testCompareSameNames() {
        String a = "Aaa", b = "Aaa";
        assertThat(DeckNameComparator.instance.compare(a,b), is(0));
    }
    @Test
    public void testCompareDifferentNames() {
        String a = "Aaa", b = "Zaa";
        assertThat(DeckNameComparator.instance.compare(a,b), is(-25));
    }
    @Test
    public void testCompare() {
        String a = "Zaa", b = "Aaa";
        assertThat(DeckNameComparator.instance.compare(a,b), is(25));
    }
}