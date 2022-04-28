//noinspection MissingCopyrightHeader #8659
package com.wildplot.android.rendering

import com.ichi2.utils.KotlinCleanup
import com.wildplot.android.rendering.graphics.wrapper.ColorWrap
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

@KotlinCleanup("`when` -> whenever")
internal class PieChartTestParametersBuilder(
    values: DoubleArray,
    firstSectorAngle: Double
) {
    private val mValues: DoubleArray
    private val mNumberOfValues: Int
    private val mSum: Double
    private val mFirstSectorAngle: Double
    val startAngles: DoubleArray
    val arcLengths: DoubleArray
    val colors: Array<ColorWrap?>
    @KotlinCleanup(" Use .sum()")
    private fun calcSum(values: DoubleArray): Double {
        var sum = 0.0
        for (v in values) {
            sum += v
        }
        return sum
    }

    private fun calcArcLengths() {
        for (i in 0 until mNumberOfValues) {
            arcLengths[i] = 360.0 * mValues[i] / mSum
        }
    }

    private fun calcStartAngles() {
        startAngles[0] = mFirstSectorAngle
        for (i in 1 until mNumberOfValues) {
            startAngles[i] = startAngles[i - 1] + arcLengths[i - 1]
        }
    }

    private fun fillColors() {
        for (i in 0 until mNumberOfValues) {
            colors[i] = createColorMock(i)
        }
    }

    private fun createColorMock(i: Int): ColorWrap {
        val c = mock(ColorWrap::class.java)
        `when`(c.colorValue).thenReturn(i)
        return c
    }

    init {
        @KotlinCleanup("move initialization to declaration whenever possible")
        @KotlinCleanup(".isNotEmpty()")
        require(values.size >= 1) { "Empty array of values" }
        mValues = values
        mNumberOfValues = values.size
        mSum = calcSum(values)
        require(mSum != 0.0) { String.format("All %d values are zero", values.size) }
        mFirstSectorAngle = firstSectorAngle
        startAngles = DoubleArray(mNumberOfValues)
        arcLengths = DoubleArray(mNumberOfValues)
        colors = arrayOfNulls(mNumberOfValues)
        calcArcLengths()
        calcStartAngles()
        fillColors()
    }
}
