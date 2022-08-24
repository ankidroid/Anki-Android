//noinspection MissingCopyrightHeader #8659
package com.wildplot.android.rendering

object FloatMatcher {
    fun closeTo(expectedValue: Double, precision: Double): (argument: Float) -> Boolean =
        { Math.abs(it.toDouble() - expectedValue) <= precision }
}
