package com.wildplot.android.rendering;

import android.graphics.Color;

import com.wildplot.android.rendering.graphics.wrapper.ColorWrap;
import com.wildplot.android.rendering.graphics.wrapper.FontMetricsWrap;
import com.wildplot.android.rendering.graphics.wrapper.GraphicsWrap;
import com.wildplot.android.rendering.graphics.wrapper.RectangleWrap;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyFloat;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.floatThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.when;

@RunWith(PowerMockRunner.class)
@PrepareForTest({RectangleWrap.class, GraphicsWrap.class, ColorWrap.class,
        android.graphics.Color.class})
public class PieChartTest {
    private static final double PRECISION = 1E-3F;
    @Mock
    GraphicsWrap graphics;

    @Mock
    PlotSheet plot;

    PieChart pieChart;

    @Before
    public void setUp() throws Exception {
        mockStatic(android.graphics.Color.class);
        when(Color.argb(anyInt(), anyInt(), anyInt(), anyInt())).thenReturn(0);
        when(plot.getFrameThickness()).thenReturn(new float[]{0, 0, 0, 0});

        FontMetricsWrap fm = mock(FontMetricsWrap.class);
        when(fm.getHeight()).thenReturn(10f);
        when(fm.stringWidth(any(String.class))).thenReturn(30f);
        when(graphics.getFontMetrics()).thenReturn(fm);
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructorShouldThrowIfSizesMismatch() throws Exception {
        new PieChart(plot, new double[]{1, 1}, new ColorWrap[]{ColorWrap.RED});
    }

    @Test
    public void paintShouldNotDrawAnythingIfValuesAreZero() throws Exception {
        pieChart = new PieChart(plot, new double[]{0, 0}, new ColorWrap[]{
                ColorWrap.RED, ColorWrap.GREEN});
        pieChart.paint(graphics);
        verify(graphics, never()).fillArc(anyFloat(), anyFloat(), anyFloat(), anyFloat(),
                anyFloat(), anyFloat());
    }

    @Test
    public void paintShouldDrawFullRedCircleIfOneValue() throws Exception {
        pieChart = new PieChart(plot, new double[]{1.}, new ColorWrap[]{
                ColorWrap.RED});
        RectangleWrap r = createRectangleMock(100, 100);
        when(graphics.getClipBounds()).thenReturn(r);
        pieChart.paint(graphics);
        verify(graphics).setColor(ColorWrap.RED);
        verify(graphics).fillArc(anyFloat(), anyFloat(), anyFloat(), anyFloat(),
                floatThat(closeTo(-90F)),
                floatThat(closeTo(360F)));
    }

    @Test
    public void paintShouldDrawTwoSectorsWithGivenColors() throws Exception {
        pieChart = new PieChart(plot, new double[]{1, 1}, new ColorWrap[]{
                ColorWrap.RED, ColorWrap.GREEN});
        RectangleWrap r = createRectangleMock(100, 100);
        when(graphics.getClipBounds()).thenReturn(r);

        pieChart.paint(graphics);

        verify(graphics).setColor(ColorWrap.RED);
        verify(graphics).fillArc(anyFloat(), anyFloat(), anyFloat(), anyFloat(),
                floatThat(closeTo(-90F)),
                floatThat(closeTo(180F)));

        verify(graphics).setColor(ColorWrap.GREEN);
        verify(graphics).fillArc(anyFloat(), anyFloat(), anyFloat(), anyFloat(),
                floatThat(closeTo(90F)),
                floatThat(closeTo(180F)));
    }

    public static RectangleWrap createRectangleMock(int width, int height) {
        RectangleWrap r = mock(RectangleWrap.class);
        r.width = width;
        r.height = height;
        when(r.width()).thenReturn(width);
        when(r.height()).thenReturn(height);
        return r;
    }

    private static FloatMatcher closeTo(double v) {
        return FloatMatcher.closeTo(v, PRECISION);
    }
}