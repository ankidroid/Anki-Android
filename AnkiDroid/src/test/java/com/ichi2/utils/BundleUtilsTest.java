package com.ichi2.utils;

import android.os.Bundle;

import com.ichi2.testutils.EmptyApplication;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.stubbing.Answer;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.*;

@RunWith(RobolectricTestRunner.class)
@Config(application = EmptyApplication.class)
public class BundleUtilsTest {

    public static final String KEY = "KEY";


    @Test
    public void test_GetNullableLong_NullBundle_ReturnsNull() {
        Long val = BundleUtils.getNullableLong(null, KEY);
        assertNull(val);
    }


    @Test
    public void test_GetNullableLong_NotFound_ReturnsNull() {
        final Bundle b = mock(Bundle.class);

        when(b.containsKey(anyString())).thenReturn(false);

        Long val = BundleUtils.getNullableLong(b, KEY);

        verify(b, times(0)).getLong(eq(KEY));

        assertNull(val);
    }


    @Test
    public void test_GetNullableLong_Found_ReturnIt() {
        final Long expected = new Random().nextLong();
        final Bundle b = mock(Bundle.class);

        when(b.containsKey(anyString())).thenReturn(true);

        when(b.getLong(anyString())).thenReturn(expected);

        Long val = BundleUtils.getNullableLong(b, KEY);

        verify(b).getLong(eq(KEY));

        assertEquals(expected, val);
    }
}