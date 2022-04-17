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
package com.ichi2.utils

import org.junit.Assert
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.internal.ArrayComparisonFailure
import java.lang.AssertionError
import java.util.*
import kotlin.Throws

class ListUtil {
    @Test
    @KotlinCleanup("Use Kotlin's methods instead of Arrays.asList")
    @KotlinCleanup("Use AnkiAssert.assertThrows<>")
    fun EqualsTest() {
        assertThrows(ArrayComparisonFailure::class.java) { assertListEquals(Arrays.asList(2L, 3L), Arrays.asList(2L, 4L)) }
        assertThrows(ArrayComparisonFailure::class.java) { assertListEquals(Arrays.asList(2L, 3L), Arrays.asList(2L)) }
        assertThrows(ArrayComparisonFailure::class.java) { assertListEquals(Arrays.asList(2L, 3L), Arrays.asList(5L)) }
        assertThrows(ArrayComparisonFailure::class.java) { assertListEquals(Arrays.asList(2L, 3L), Arrays.asList(2L, 3L, 5L)) }
        assertThrows(AssertionError::class.java) { assertListEquals(Arrays.asList(2L, 3L), null) }
        assertThrows(AssertionError::class.java) { assertListEquals(null, Arrays.asList(2L, 4L)) }
        assertListEquals(null, null)
        assertListEquals(Arrays.asList(2L, 3L), Arrays.asList(2L, 3L))
    }

    companion object {
        /**
         * Asserts that two object lists are equal (same size and components in same order). If they are not, an
         * [AssertionError] is thrown with the given message. It states "array" instead of list If
         * `expecteds` and `actuals` are `null`,
         * they are considered equal.
         *
         * @param message the identifying message for the [AssertionError] (`null`
         * okay)
         * @param expecteds Object list or list of arrays (multi-dimensional array) with
         * expected values.
         * @param actuals Object list or list of arrays (multi-dimensional array) with
         * actual values
         */
        @Throws(ArrayComparisonFailure::class)
        fun assertListEquals(
            message: String?,
            expecteds: List<Any>?,
            actuals: List<Any>?
        ) {
            val expecteds_array: Array<Any>? = expecteds?.toTypedArray()
            val actuals_array: Array<Any>? = actuals?.toTypedArray()
            Assert.assertArrayEquals(message, expecteds_array, actuals_array)
        }

        /**
         * Asserts that two object arrays are equal. If they are not, an
         * [AssertionError] is thrown. If `expected` and
         * `actual` are `null`, they are considered
         * equal.
         *
         * @param expecteds Object list or list of arrays (multi-dimensional array) with
         * expected values
         * @param actuals Object list or list of arrays (multi-dimensional array) with
         * actual values
         */
        @JvmStatic
        fun assertListEquals(expecteds: List<Any>?, actuals: List<Any>?) {
            assertListEquals(null, expecteds, actuals)
        }
    }
}
