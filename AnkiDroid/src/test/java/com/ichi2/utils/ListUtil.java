/****************************************************************************************
 * Copyright (c) 2021 Arthur Milchior <arthur@milchior.fr>                              *
 *                                                                                      *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 *                                                                                      *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/
package com.ichi2.utils;

import org.junit.Test;
import org.junit.internal.ArrayComparisonFailure;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertThrows;

public class ListUtil {


    /**
     * Asserts that two object lists are equal (same size and components in same order). If they are not, an
     * {@link AssertionError} is thrown with the given message. It states "array" instead of list If
     * <code>expecteds</code> and <code>actuals</code> are <code>null</code>,
     * they are considered equal.
     *
     * @param message the identifying message for the {@link AssertionError} (<code>null</code>
     * okay)
     * @param expecteds Object list or list of arrays (multi-dimensional array) with
     * expected values.
     * @param actuals Object list or list of arrays (multi-dimensional array) with
     * actual values
     */
    public static <T>  void assertListEquals(String message, List<T> expecteds,
                                         List<T> actuals) throws ArrayComparisonFailure {
        Object[] expecteds_array = (expecteds == null) ? null : expecteds.toArray();
        Object[] actuals_array = (actuals == null) ? null : actuals.toArray();
        assertArrayEquals(message, expecteds_array, actuals_array);
    }

    /**
     * Asserts that two object arrays are equal. If they are not, an
     * {@link AssertionError} is thrown. If <code>expected</code> and
     * <code>actual</code> are <code>null</code>, they are considered
     * equal.
     *
     * @param expecteds Object list or list of arrays (multi-dimensional array) with
     * expected values
     * @param actuals Object list or list of arrays (multi-dimensional array) with
     * actual values
     */
    public static <T> void assertListEquals(List<T> expecteds,
                                        List<T> actuals) {
        assertListEquals(null, expecteds, actuals);
    }

    @Test
    public void EqualsTest() {
        assertThrows(ArrayComparisonFailure.class, () -> assertListEquals(Arrays.asList(2L, 3L), Arrays.asList(2L, 4L)));
        assertThrows(ArrayComparisonFailure.class, () -> assertListEquals(Arrays.asList(2L, 3L), Arrays.asList(2L)));
        assertThrows(ArrayComparisonFailure.class, () -> assertListEquals(Arrays.asList(2L, 3L), Arrays.asList(5L)));
        assertThrows(ArrayComparisonFailure.class, () -> assertListEquals(Arrays.asList(2L, 3L), Arrays.asList(2L, 3L, 5L)));
        assertThrows(AssertionError.class, () -> assertListEquals(Arrays.asList(2L, 3L), null));
        assertThrows(AssertionError.class, () -> assertListEquals(null, Arrays.asList(2L, 4L)));
        assertListEquals(null, null);
        assertListEquals(Arrays.asList(2L, 3L), Arrays.asList(2L, 3L));
    }

}
