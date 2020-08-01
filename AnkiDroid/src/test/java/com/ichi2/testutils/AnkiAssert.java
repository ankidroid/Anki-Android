package com.ichi2.testutils;

import android.util.Pair;

import com.ichi2.libanki.Card;
import com.ichi2.libanki.Collection;

import androidx.annotation.NonNull;
import timber.log.Timber;

import org.junit.Assert;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

/** Assertion methods that aren't currently supported by our dependencies */
public class AnkiAssert {

    /** Helper to sort out "JUnit tests should include assert() or fail()" quality check */
    public static void assertDoesNotThrow(@NonNull Runnable runnable) {
        try {
            runnable.run();
        } catch (Exception e) {
            Timber.e(e);
            Assert.fail();
        }
    }

    public static <T> void assertEqualsArrayList(T[] ar, List<T> l) {
        assertEquals(Arrays.asList(ar), l);
    }


    public static String without_unicode_isolation(String s) {
        return s.replace("\u2068", "").replace("\u2069", "");
    }

    public static boolean checkRevIvl(Collection col, Card c, int targetIvl) {
        Pair<Integer, Integer> min_max = col.getSched()._fuzzIvlRange(targetIvl);
        return min_max.first <= c.getIvl() && c.getIvl() <= min_max.second;
    }


}
