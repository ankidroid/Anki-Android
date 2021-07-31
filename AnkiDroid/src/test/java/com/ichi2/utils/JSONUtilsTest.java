package com.ichi2.utils;

import androidx.annotation.Nullable;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

public class JSONUtilsTest {

    /**
     * Fail if e cause is not expected
     */
    public static void assertJSONExceptionEncapsulate(Class<? extends Throwable> expected, JSONException e) {
        Throwable cause = e.getCause();
        if (expected != null) {
            assertNotNull(cause);
            assertEquals(expected, cause.getClass());
        } else {
            assertNull(cause);
        }
    }


    /**
     * @param r fail if r don't throw a JSONException whose cause is org.json.JSONException
     */
    public static void assertThrowsJSONExceptionEncapsulating(Runnable r) {
        assertThrowsJSONExceptionEncapsulating(r, null);
    }

    /**
     * @param r fail if r don't throw a JSONException whose cause is expected
     */
    public static void assertThrowsJSONExceptionEncapsulating(Class<? extends Throwable> expected, Runnable r) {
        assertThrowsJSONExceptionEncapsulating(expected, r, null);
    }

    /**
     * @param r fail with reason if r don't throw a JSONException whose cause is org.json.JSONException
     */
    public static void assertThrowsJSONExceptionEncapsulating(Runnable r, @Nullable String reason) {
        assertThrowsJSONExceptionEncapsulating(org.json.JSONException.class, r, reason);
    }

    /**
     * @param r fail with reason if r don't throw a JSONException whose cause is expected
     */
    public static void assertThrowsJSONExceptionEncapsulating(Class<? extends Throwable> expected, Runnable r, @Nullable String reason) {
        try {
            r.run();
            fail(reason);
        } catch (JSONException e) {
            assertJSONExceptionEncapsulate(expected, e);
        }
    }

}