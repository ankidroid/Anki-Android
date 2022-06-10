package com.wildplot.android.rendering

import org.mockito.ArgumentMatcher

internal class FloatMatcher private constructor(
    private val mExpected: Double,
    private val mPrecision: Double
) : ArgumentMatcher<Float> {
    override fun matches(actualValue: Float): Boolean {
        return Math.abs(actualValue.toDouble() - mExpected) <= mPrecision
    }

    companion object {
        fun closeTo(expectedValue: Double, precision: Double): FloatMatcher {
            return FloatMatcher(expectedValue, precision)
        }
    }
}
