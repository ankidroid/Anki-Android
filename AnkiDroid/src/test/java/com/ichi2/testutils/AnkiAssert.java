package com.ichi2.testutils;

import androidx.annotation.NonNull;

import org.junit.Assert;

/** Assertion methods that aren't currently supported by our dependencies */
public class AnkiAssert {

    /** Helper to sort out "JUnit tests should include assert() or fail()" quality check */
    public static void assertDoesNotThrow(@NonNull Runnable runnable) {
        try {
            runnable.run();
        } catch (Exception e) {
            Assert.fail();
        }
    }
}
