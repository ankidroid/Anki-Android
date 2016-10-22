package com.wildplot.android.rendering;

import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;

class FloatMatcher extends TypeSafeMatcher<Float> {
    private final double expected;
    private final double precision;

    private FloatMatcher(double expectedValue, double precision) {
        expected = expectedValue;
        this.precision = precision;
    }

    static FloatMatcher closeTo(double expectedValue, double precision) {
        return new FloatMatcher(expectedValue, precision);
    }

    @Override
    protected boolean matchesSafely(Float actualValue) {
        return Math.abs(((double) actualValue) - expected) <= precision;
    }


    @Override
    public void describeTo(Description description) {
        description.appendText(expected + " ± " + precision);
    }
}
