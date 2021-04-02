package com.ichi2.testutils;

import com.ichi2.testutils.MockTime;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

public class MockTimeTest {
    @Test
    public void DateTest() {
        MockTime time = new MockTime(2020, 7, 7, 7, 0, 0, 0, 0);
        Assert.assertEquals(1596783600000L, time.intTimeMS());
        Assert.assertEquals(1596783600000L, MockTime.timeStamp(2020, 7, 7, 7, 0, 0));
    }
}
