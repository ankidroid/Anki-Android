package com.ichi2.preferences;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;

import static org.junit.Assert.assertEquals;

@RunWith(Parameterized.class)
public class TimePreferenceTest {
    private final String parsableHour;
    private final int expectedHour;


    public TimePreferenceTest(String parsableHour, int expectedHour) {
        this.parsableHour = parsableHour;
        this.expectedHour = expectedHour;
    }


    @Parameterized.Parameters
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {
                {"00:00", 0},
                {"01:00", 1},
                {"24:00", 24}
        });
    }
    
    @Test
    public void shouldParseHours() {
        int actualHour = TimePreference.parseHours(this.parsableHour);

        assertEquals(expectedHour, actualHour);
    }
}
