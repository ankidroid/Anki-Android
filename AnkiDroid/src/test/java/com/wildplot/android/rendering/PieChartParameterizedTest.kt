//noinspection MissingCopyrightHeader #8659
package com.wildplot.android.rendering

import android.graphics.Color
import com.wildplot.android.rendering.PieChartTest.Companion.createRectangleMock
import com.wildplot.android.rendering.graphics.wrapper.ColorWrap
import com.wildplot.android.rendering.graphics.wrapper.FontMetricsWrap
import com.wildplot.android.rendering.graphics.wrapper.GraphicsWrap
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.mockito.ArgumentMatchers.*
import org.mockito.Mock
import org.mockito.MockedStatic
import org.mockito.Mockito
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.whenever
import java.util.*

@RunWith(Parameterized::class)
class PieChartParameterizedTest {
    @Parameterized.Parameter
    lateinit var values: DoubleArray

    @Parameterized.Parameter(1)
    lateinit var startAngles: DoubleArray

    @Parameterized.Parameter(2)
    lateinit var arcLengths: DoubleArray

    @Parameterized.Parameter(3)
    lateinit var colors: Array<ColorWrap>

    @Mock
    var graphics: GraphicsWrap? = null

    @Mock
    var plot: PlotSheet? = null

    var pieChart: PieChart? = null

    private var colorMockedStatic: MockedStatic<Color>? = null

    @Before
    fun setUp() {
        colorMockedStatic = Mockito.mockStatic(Color::class.java)
        MockitoAnnotations.openMocks(this)
        whenever(Color.argb(anyInt(), anyInt(), anyInt(), anyInt())).thenReturn(0)
        whenever(plot!!.frameThickness).thenReturn(floatArrayOf(0f, 0f, 0f, 0f))

        val fm = mock(FontMetricsWrap::class.java)
        whenever(fm.height).thenReturn(10f)
        whenever(fm.stringWidth(any(String::class.java))).thenReturn(30f)
        whenever(graphics!!.fontMetrics).thenReturn(fm)

        val r = createRectangleMock(100, 100)
        whenever(graphics!!.clipBounds).thenReturn(r)
        pieChart = PieChart(plot!!, values, colors)
    }

    @After
    fun tearDown() {
        colorMockedStatic!!.close()
    }

    @Test
    fun testPaintDrawsAllArcs() {
        pieChart!!.paint(graphics!!)
        // ordered verification is used to prevent failures when there are tiny adjacent sectors
        val inOrder = inOrder(graphics)
        for (i in values.indices) {
            if (arcLengths[i] == 0.0) continue
            inOrder.verify(graphics)?.color = colors[i]
            inOrder.verify(graphics)?.fillArc(
                anyFloat(),
                anyFloat(),
                anyFloat(),
                anyFloat(),
                floatThat(closeTo(startAngles[i])),
                floatThat(closeTo(arcLengths[i]))
            )
        }
    }

    companion object {
        private const val PRECISION = (2 * 1E-3f).toDouble()
        private fun closeTo(v: Double): (argument: Float) -> Boolean {
            return FloatMatcher.closeTo(v, PRECISION)
        }

        @Parameterized.Parameters
        @JvmStatic // required for Parameters
        fun data(): Collection<Array<Any>> {
            return createParametersCollection(
                listOf(
                    values(1.0),
                    values(0.0, 1.0),
                    values(1.0, 0.0),
                    values(1.0, 0.0, 1.0),

                    // arrays of equal values of various sizes
                    equalValues(2),
                    equalValues(3),
                    equalValues(4),
                    equalValues(17),

                    // tiny only
                    values(1E-100),
                    values(1E-100, 1E-100, 1E-100),

                    // huge only
                    values(1E100),

                    // huge and tiny
                    values(1E100, 1E100, 1E100),
                    values(1E-100, 1E100),
                    values(1E100, 1E-100),
                    values(1E100, 1E-100, 1E100),
                    values(1E-100, 1E100, 1E-100),
                    values(1E100, 1E-100, 1E-100, 1E100)
                )
            )
        }

        private fun createParametersCollection(values: Collection<DoubleArray>): Collection<Array<Any>> {
            val parameters: MutableCollection<Array<Any>> = ArrayList(values.size)
            for (v in values) {
                parameters.add(createParameters(v))
            }
            return parameters
        }

        private fun createParameters(values: DoubleArray): Array<Any> {
            val builder = PieChartTestParametersBuilder(values, -90.0)
            return arrayOf(
                values,
                builder.startAngles,
                builder.arcLengths,
                builder.colors
            )
        }

        private fun values(vararg d: Double): DoubleArray {
            return d
        }

        private fun equalValues(numberOfValues: Int): DoubleArray {
            val v = DoubleArray(numberOfValues)
            Arrays.fill(v, 1.0)
            return v
        }
    }
}
