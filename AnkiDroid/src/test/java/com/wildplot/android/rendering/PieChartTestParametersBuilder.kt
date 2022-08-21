//noinspection MissingCopyrightHeader #8659
package com.wildplot.android.rendering

import com.wildplot.android.rendering.graphics.wrapper.ColorWrap
import org.mockito.Mockito.mock
import org.mockito.kotlin.whenever

internal class PieChartTestParametersBuilder(
    values: DoubleArray,
    firstSectorAngle: Double
) {
    private val mValues: DoubleArray = values
    private val mNumberOfValues: Int = values.size
    private val mSum: Double = calcSum(values)
    private val mFirstSectorAngle: Double = firstSectorAngle
    val startAngles: DoubleArray = DoubleArray(mNumberOfValues)
    val arcLengths: DoubleArray = DoubleArray(mNumberOfValues)
    val colors: Array<ColorWrap?> = arrayOfNulls(mNumberOfValues)
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
        whenever(c.colorValue).thenReturn(i)
        return c
    }

    init {
        require(values.isNotEmpty()) { "Empty array of values" }
        require(mSum != 0.0) { "All ${values.size} values are zero" }
        calcArcLengths()
        calcStartAngles()
        fillColors()
    }
}
