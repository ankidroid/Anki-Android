package com.wildplot.android.rendering;

import android.graphics.Color;

import com.wildplot.android.rendering.graphics.wrapper.ColorWrap;
import com.wildplot.android.rendering.graphics.wrapper.FontMetricsWrap;
import com.wildplot.android.rendering.graphics.wrapper.GraphicsWrap;
import com.wildplot.android.rendering.graphics.wrapper.RectangleWrap;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.modules.junit4.PowerMockRunnerDelegate;

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
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.when;

@RunWith(PowerMockRunner.class)
@PowerMockRunnerDelegate(Parameterized.class)
@PrepareForTest({RectangleWrap.class, GraphicsWrap.class, ColorWrap.class, PlotSheet.class,
        Color.class})
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
    GraphicsWrap graphics;

    @Mock
    PlotSheet plot;

    PieChart pieChart;

    @Before
    public void setUp() {
        mockStatic(android.graphics.Color.class);
        MockitoAnnotations.initMocks(this);
        when(Color.argb(anyInt(), anyInt(), anyInt(), anyInt())).thenReturn(0);
        when(plot.getFrameThickness()).thenReturn(new float[]{0, 0, 0, 0});

        FontMetricsWrap fm = mock(FontMetricsWrap.class);
        when(fm.getHeight()).thenReturn(10f);
        when(fm.stringWidth(any(String.class))).thenReturn(30f);
        when(graphics.getFontMetrics()).thenReturn(fm);

        RectangleWrap r = createRectangleMock(100, 100);
        when(graphics.getClipBounds()).thenReturn(r);
        pieChart = new PieChart(plot, values, colors);
    }

    @Test
    public void testPaintDrawsAllArcs() {
        pieChart.paint(graphics);
        // ordered verification is used to prevent failures when there are tiny adjacent sectors
        InOrder inOrder = inOrder(graphics);
        for (int i = 0; i < values.length; i++) {
            if (arcLengths[i] == 0) continue;
            inOrder.verify(graphics).setColor(colors[i]);
            inOrder.verify(graphics).fillArc(anyFloat(), anyFloat(), anyFloat(), anyFloat(),
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