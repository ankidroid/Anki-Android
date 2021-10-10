//noinspection MissingCopyrightHeader #8659

package com.wildplot.android.rendering;

import org.mockito.ArgumentMatcher;

class FloatMatcher implements ArgumentMatcher<Float> {
    private final double mExpected;
    private final double mPrecision;

    private FloatMatcher(double expectedValue, double precision) {
        mExpected = expectedValue;
        mPrecision = precision;
    }

    static FloatMatcher closeTo(double expectedValue, double precision) {
        return new FloatMatcher(expectedValue, precision);
    }

    @Override
    public boolean matches(Float actualValue) {
        return Math.abs(((double) actualValue) - mExpected) <= mPrecision;
    }
}
