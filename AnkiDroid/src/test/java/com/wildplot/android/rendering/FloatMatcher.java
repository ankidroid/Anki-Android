package com.wildplot.android.rendering;

import org.mockito.ArgumentMatcher;

class FloatMatcher implements ArgumentMatcher<Float> {
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
    public boolean matches(Float actualValue) {
        return Math.abs(((double) actualValue) - expected) <= precision;
    }
}
