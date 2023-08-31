//noinspection MissingCopyrightHeader #8659
package com.wildplot.android.rendering

import android.graphics.Color
import com.wildplot.android.rendering.graphics.wrapper.ColorWrap
import com.wildplot.android.rendering.graphics.wrapper.FontMetricsWrap
import com.wildplot.android.rendering.graphics.wrapper.GraphicsWrap
import com.wildplot.android.rendering.graphics.wrapper.RectangleWrap
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyFloat
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.floatThat
import org.mockito.Mock
import org.mockito.MockedStatic
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations
import java.lang.IllegalArgumentException

class PieChartTest {
    @Mock
    private val mGraphics: GraphicsWrap? = null

    @Mock
    private val mPlot: PlotSheet? = null

    private var mPieChart: PieChart? = null

    private var mColorMockedStatic: MockedStatic<Color>? = null

    @Before
    fun setUp() {
        mColorMockedStatic = mockStatic(Color::class.java)
        MockitoAnnotations.openMocks(this)
        `when`(Color.argb(anyInt(), anyInt(), anyInt(), anyInt())).thenReturn(0)
        `when`(mPlot!!.frameThickness).thenReturn(floatArrayOf(0f, 0f, 0f, 0f))

        val fm = mock(FontMetricsWrap::class.java)
        `when`(fm.height).thenReturn(10f)
        `when`(fm.stringWidth(anyString())).thenReturn(30f)
        `when`(mGraphics!!.fontMetrics).thenReturn(fm)
    }

    @After
    fun tearDown() {
        mColorMockedStatic!!.close()
    }

    @Test(expected = IllegalArgumentException::class)
    fun constructorShouldThrowIfSizesMismatch() {
        PieChart(mPlot!!, doubleArrayOf(1.0, 1.0), arrayOf(ColorWrap.RED))
    }

    @Test
    fun paintShouldNotDrawAnythingIfValuesAreZero() {
        mPieChart = PieChart(
            mPlot!!,
            doubleArrayOf(0.0, 0.0),
            arrayOf(
                ColorWrap.RED,
                ColorWrap.GREEN
            )
        )
        mPieChart!!.paint(mGraphics!!)
        verify(mGraphics, never()).fillArc(
            anyFloat(),
            anyFloat(),
            anyFloat(),
            anyFloat(),
            anyFloat(),
            anyFloat()
        )
    }

    @Test
    fun paintShouldDrawFullRedCircleIfOneValue() {
        mPieChart = PieChart(
            mPlot!!,
            doubleArrayOf(1.0),
            arrayOf(
                ColorWrap.RED
            )
        )
        val r = createRectangleMock(100, 100)
        `when`(mGraphics!!.clipBounds).thenReturn(r)
        mPieChart!!.paint(mGraphics)
        verify(mGraphics).color = ColorWrap.RED
        verify(mGraphics).fillArc(
            anyFloat(),
            anyFloat(),
            anyFloat(),
            anyFloat(),
            floatThat(closeTo(-90.0)),
            floatThat(closeTo(360.0))
        )
    }

    @Test
    fun paintShouldDrawTwoSectorsWithGivenColors() {
        mPieChart = PieChart(
            mPlot!!,
            doubleArrayOf(1.0, 1.0),
            arrayOf(
                ColorWrap.RED,
                ColorWrap.GREEN
            )
        )
        val r = createRectangleMock(100, 100)
        `when`(mGraphics!!.clipBounds).thenReturn(r)

        mPieChart!!.paint(mGraphics)
        verify(mGraphics).color = ColorWrap.RED
        verify(mGraphics).fillArc(
            anyFloat(),
            anyFloat(),
            anyFloat(),
            anyFloat(),
            floatThat(closeTo(-90.0)),
            floatThat(closeTo(180.0))
        )
        verify(mGraphics).color = ColorWrap.GREEN
        verify(mGraphics).fillArc(
            anyFloat(),
            anyFloat(),
            anyFloat(),
            anyFloat(),
            floatThat(closeTo(90.0)),
            floatThat(closeTo(180.0))
        )
    }

    companion object {
        private const val PRECISION = 1E-3
        fun createRectangleMock(width: Int, height: Int): RectangleWrap {
            val r = mock(RectangleWrap::class.java)
            r.width = width
            r.height = height
            `when`(r.width()).thenReturn(width)
            `when`(r.height()).thenReturn(height)
            return r
        }

        private fun closeTo(v: Double): (Float) -> Boolean {
            return FloatMatcher.closeTo(v, PRECISION)
        }
    }
}
