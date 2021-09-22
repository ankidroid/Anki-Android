/*
 *  Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.testutils;

import android.util.Pair;

import com.ichi2.libanki.Card;
import com.ichi2.libanki.Collection;

import java.util.Arrays;
import java.util.List;

import androidx.annotation.NonNull;

import static com.ichi2.utils.ListUtil.assertListEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/** Assertion methods that aren't currently supported by our dependencies */
public class AnkiAssert {

    /** Helper to sort out "JUnit tests should include assert() or fail()" quality check */
    public static void assertDoesNotThrow(String message, @NonNull Runnable runnable) {
        try {
            runnable.run();
        } catch (Exception e) {
            throw new AssertionError(message + "\nmethod should not throw", e);
        }
    }

    /** Helper to sort out "JUnit tests should include assert() or fail()" quality check */
    public static void assertDoesNotThrow(@NonNull Runnable runnable) {
        try {
            runnable.run();
        } catch (Exception e) {
            throw new AssertionError("method should not throw", e);
        }
    }

    @SuppressWarnings("unchecked")
    public static <T extends Throwable> T assertThrows(Runnable r, Class<T> clazz) {
        try {
            r.run();
            fail("Expected exception: " + clazz.getSimpleName() + ". No exception thrown.");
        } catch (Throwable t) {
            if (t.getClass().equals(clazz)) {
                return (T) t;
            }

            if (t.getMessage() != null && t.getMessage().startsWith("Expected exception: ")) {
                // We need to add a "throws" if we rethrow t, so fail with the same code.
                fail("Expected exception: " + clazz.getSimpleName() + ". No exception thrown.");
            }

            // We don't want to assert here as we want to include the exception.
            throw new AssertionError("Expected '" + clazz.getSimpleName() + "' got '" + t.getClass().getSimpleName() + "'", t);
        }
        throw new IllegalStateException("unreachable");
    }

    public static <T> void assertEqualsArrayList(T[] expected, List<T> actual) {
        assertListEquals(Arrays.asList(expected), actual);
    }


    public static String without_unicode_isolation(String s) {
        return s.replace("\u2068", "").replace("\u2069", "");
    }

    public static boolean checkRevIvl(Collection col, Card c, int targetIvl) {
        Pair<Integer, Integer> min_max = col.getSched()._fuzzIvlRange(targetIvl);
        return min_max.first <= c.getIvl() && c.getIvl() <= min_max.second;
    }


}
