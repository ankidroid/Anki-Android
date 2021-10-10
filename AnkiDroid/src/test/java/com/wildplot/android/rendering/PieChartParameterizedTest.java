//noinspection MissingCopyrightHeader #8659

package com.wildplot.android.rendering;

import android.graphics.Color;

import com.wildplot.android.rendering.graphics.wrapper.ColorWrap;
import com.wildplot.android.rendering.graphics.wrapper.FontMetricsWrap;
import com.wildplot.android.rendering.graphics.wrapper.GraphicsWrap;
import com.wildplot.android.rendering.graphics.wrapper.RectangleWrap;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

import static com.wildplot.android.rendering.PieChartTest.createRectangleMock;
import static java.util.Arrays.asList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.floatThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(Parameterized.class)
@SuppressWarnings("WeakerAccess")
public class PieChartParameterizedTest {
    private static final double PRECISION = 2 * 1E-3F;

    @Parameter()
    public double[] values;
    @Parameter(1)
    public double[] startAngles;
    @Parameter(2)
    public double[] arcLengths;
    @Parameter(3)
    public ColorWrap[] colors;

    @Mock
    GraphicsWrap mGraphics;

    @Mock
    PlotSheet mPlot;

    PieChart mPieChart;

    private MockedStatic<Color> mColorMockedStatic;

    @Before
    public void setUp() {
        mColorMockedStatic = Mockito.mockStatic(Color.class);
        MockitoAnnotations.openMocks(this);
        when(Color.argb(anyInt(), anyInt(), anyInt(), anyInt())).thenReturn(0);
        when(mPlot.getFrameThickness()).thenReturn(new float[]{0, 0, 0, 0});

        FontMetricsWrap fm = mock(FontMetricsWrap.class);
        when(fm.getHeight()).thenReturn(10f);
        when(fm.stringWidth(any(String.class))).thenReturn(30f);
        when(mGraphics.getFontMetrics()).thenReturn(fm);

        RectangleWrap r = createRectangleMock(100, 100);
        when(mGraphics.getClipBounds()).thenReturn(r);
        mPieChart = new PieChart(mPlot, values, colors);
    }

    @After
    public void tearDown() {
        mColorMockedStatic.close();
    }

    @Test
    public void testPaintDrawsAllArcs() {
        mPieChart.paint(mGraphics);
        // ordered verification is used to prevent failures when there are tiny adjacent sectors
        InOrder inOrder = inOrder(mGraphics);
        for (int i = 0; i < values.length; i++) {
            if (arcLengths[i] == 0) continue;
            inOrder.verify(mGraphics).setColor(colors[i]);
            inOrder.verify(mGraphics).fillArc(anyFloat(), anyFloat(), anyFloat(), anyFloat(),
                    floatThat(closeTo(startAngles[i])),
                    floatThat(closeTo(arcLengths[i])));
        }
    }

    private static FloatMatcher closeTo(double v) {
        return FloatMatcher.closeTo(v, PRECISION);
    }

    @Parameterized.Parameters
    public static Collection<Object[]> data() {
        return createParametersCollection(asList(
                values(1),
                values(0, 1),
                values(1, 0),
                values(1, 0, 1),

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
                values(1E100, 1E100, 1E100),

                // huge and tiny
                values(1E-100, 1E100),
                values(1E100, 1E-100),
                values(1E100, 1E-100, 1E100),
                values(1E-100, 1E100, 1E-100),
                values(1E100, 1E-100, 1E-100, 1E100)
        ));
    }

    private static Collection<Object[]> createParametersCollection(Collection<double[]> values) {
        Collection<Object[]> parameters = new ArrayList<>(values.size());
        for (double[] v : values) {
            parameters.add(createParameters(v));
        }
        return parameters;
    }

    private static Object[] createParameters(double[] values) {
        PieChartTestParametersBuilder builder = new PieChartTestParametersBuilder(values, -90);
        return new Object[] {
                values,
                builder.getStartAngles(),
                builder.getArcLengths(),
                builder.getColors()
        };
    }

    private static double[] values(double... d) {
        return d;
    }

    private static double[] equalValues(int numberOfValues) {
        double[] v = new double[numberOfValues];
        Arrays.fill(v, 1);
        return v;
    }
}