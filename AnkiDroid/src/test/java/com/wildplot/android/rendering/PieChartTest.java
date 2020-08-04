package com.wildplot.android.rendering;

import android.graphics.Color;

import com.wildplot.android.rendering.graphics.wrapper.ColorWrap;
import com.wildplot.android.rendering.graphics.wrapper.FontMetricsWrap;
import com.wildplot.android.rendering.graphics.wrapper.GraphicsWrap;
import com.wildplot.android.rendering.graphics.wrapper.RectangleWrap;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.floatThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class PieChartTest {
    private static final double PRECISION = 1E-3F;

    @Mock
    private GraphicsWrap graphics;

    @Mock
    private PlotSheet plot;

    private PieChart pieChart;

    private MockedStatic<Color> colorMockedStatic;


    @Before
    public void setUp() {
        colorMockedStatic = mockStatic(Color.class);
        MockitoAnnotations.openMocks(this);
        when(Color.argb(anyInt(), anyInt(), anyInt(), anyInt())).thenReturn(0);
        when(plot.getFrameThickness()).thenReturn(new float[]{0, 0, 0, 0});

        FontMetricsWrap fm = mock(FontMetricsWrap.class);
        when(fm.getHeight()).thenReturn(10f);
        when(fm.stringWidth(anyString())).thenReturn(30f);
        when(graphics.getFontMetrics()).thenReturn(fm);
    }

    @After
    public void tearDown() {
        colorMockedStatic.close();
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructorShouldThrowIfSizesMismatch() {
        new PieChart(plot, new double[]{1, 1}, new ColorWrap[]{ColorWrap.RED});
    }

    @Test
    public void paintShouldNotDrawAnythingIfValuesAreZero() {
        pieChart = new PieChart(plot, new double[]{0, 0}, new ColorWrap[]{
                ColorWrap.RED, ColorWrap.GREEN});
        pieChart.paint(graphics);
        verify(graphics, never()).fillArc(anyFloat(), anyFloat(), anyFloat(), anyFloat(),
                anyFloat(), anyFloat());
    }

    @Test
    public void paintShouldDrawFullRedCircleIfOneValue() {
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
    public void paintShouldDrawTwoSectorsWithGivenColors() {
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