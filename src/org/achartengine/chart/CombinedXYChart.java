/**
 * Copyright (C) 2009 - 2012 SC 4ViewSoft SRL
 *  
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *  
 *      http://www.apache.org/licenses/LICENSE-2.0
 *  
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.achartengine.chart;

import java.util.List;

import org.achartengine.model.XYMultipleSeriesDataset;
import org.achartengine.model.XYSeries;
import org.achartengine.renderer.SimpleSeriesRenderer;
import org.achartengine.renderer.XYMultipleSeriesRenderer;
import org.achartengine.renderer.XYMultipleSeriesRenderer.Orientation;

import android.graphics.Canvas;
import android.graphics.Paint;

/**
 * The combined XY chart rendering class.
 */
public class CombinedXYChart extends XYChart {
  /** The embedded XY charts. */
  private XYChart[] mCharts;
  /** The supported charts for being combined. */
  private Class[] xyChartTypes = new Class[] { TimeChart.class, LineChart.class,
      CubicLineChart.class, BarChart.class, BubbleChart.class, ScatterChart.class,
      RangeBarChart.class };

  /**
   * Builds a new combined XY chart instance.
   * 
   * @param dataset the multiple series dataset
   * @param renderer the multiple series renderer
   * @param types the XY chart types
   */
  public CombinedXYChart(XYMultipleSeriesDataset dataset, XYMultipleSeriesRenderer renderer,
      String[] types) {
    super(dataset, renderer);
    int length = types.length;
    mCharts = new XYChart[length];
    for (int i = 0; i < length; i++) {
      try {
        mCharts[i] = getXYChart(types[i]);
      } catch (Exception e) {
        // ignore
      }
      if (mCharts[i] == null) {
        throw new IllegalArgumentException("Unknown chart type " + types[i]);
      } else {
        XYMultipleSeriesDataset newDataset = new XYMultipleSeriesDataset();
        newDataset.addSeries(dataset.getSeriesAt(i));
        XYMultipleSeriesRenderer newRenderer = new XYMultipleSeriesRenderer();
        // TODO: copy other parameters here
        newRenderer.setBarSpacing(renderer.getBarSpacing());
        newRenderer.setPointSize(renderer.getPointSize());
        int scale = dataset.getSeriesAt(i).getScaleNumber();
        if (renderer.isMinXSet(scale)) {
          newRenderer.setXAxisMin(renderer.getXAxisMin(scale));
        }
        if (renderer.isMaxXSet(scale)) {
          newRenderer.setXAxisMax(renderer.getXAxisMax(scale));
        }
        if (renderer.isMinYSet(scale)) {
          newRenderer.setYAxisMin(renderer.getYAxisMin(scale));
        }
        if (renderer.isMaxYSet(scale)) {
          newRenderer.setYAxisMax(renderer.getYAxisMax(scale));
        }
        newRenderer.addSeriesRenderer(renderer.getSeriesRendererAt(i));
        mCharts[i].setDatasetRenderer(newDataset, newRenderer);
      }
    }
  }

  /**
   * Returns a chart instance based on the provided type.
   * 
   * @param type the chart type
   * @return an instance of a chart implementation
   * @throws IllegalAccessException
   * @throws InstantiationException
   */
  private XYChart getXYChart(String type) throws IllegalAccessException, InstantiationException {
    XYChart chart = null;
    int length = xyChartTypes.length;
    for (int i = 0; i < length && chart == null; i++) {
      XYChart newChart = (XYChart) xyChartTypes[i].newInstance();
      if (type.equals(newChart.getChartType())) {
        chart = newChart;
      }
    }
    return chart;
  }

  /**
   * The graphical representation of a series.
   * 
   * @param canvas the canvas to paint to
   * @param paint the paint to be used for drawing
   * @param points the array of points to be used for drawing the series
   * @param seriesRenderer the series renderer
   * @param yAxisValue the minimum value of the y axis
   * @param seriesIndex the index of the series currently being drawn
   * @param startIndex the start index of the rendering points
   */
  public void drawSeries(Canvas canvas, Paint paint, float[] points,
      SimpleSeriesRenderer seriesRenderer, float yAxisValue, int seriesIndex, int startIndex) {
    mCharts[seriesIndex].setScreenR(getScreenR());
    mCharts[seriesIndex].setCalcRange(getCalcRange(mDataset.getSeriesAt(seriesIndex)
        .getScaleNumber()), 0);
    mCharts[seriesIndex].drawSeries(canvas, paint, points, seriesRenderer, yAxisValue, 0,
        startIndex);
  }

  @Override
  protected ClickableArea[] clickableAreasForPoints(float[] points, double[] values,
      float yAxisValue, int seriesIndex, int startIndex) {
    return mCharts[seriesIndex].clickableAreasForPoints(points, values, yAxisValue, 0, startIndex);
  }

  @Override
  protected void drawSeries(XYSeries series, Canvas canvas, Paint paint, List<Float> pointsList,
      SimpleSeriesRenderer seriesRenderer, float yAxisValue, int seriesIndex, Orientation or,
      int startIndex) {
    mCharts[seriesIndex].setScreenR(getScreenR());
    mCharts[seriesIndex].setCalcRange(getCalcRange(mDataset.getSeriesAt(seriesIndex)
        .getScaleNumber()), 0);
    mCharts[seriesIndex].drawSeries(series, canvas, paint, pointsList, seriesRenderer, yAxisValue,
        0, or, startIndex);
  }

  /**
   * Returns the legend shape width.
   * 
   * @param seriesIndex the series index
   * @return the legend shape width
   */
  public int getLegendShapeWidth(int seriesIndex) {
    return mCharts[seriesIndex].getLegendShapeWidth(0);
  }

  /**
   * The graphical representation of the legend shape.
   * 
   * @param canvas the canvas to paint to
   * @param renderer the series renderer
   * @param x the x value of the point the shape should be drawn at
   * @param y the y value of the point the shape should be drawn at
   * @param seriesIndex the series index
   * @param paint the paint to be used for drawing
   */
  public void drawLegendShape(Canvas canvas, SimpleSeriesRenderer renderer, float x, float y,
      int seriesIndex, Paint paint) {
    mCharts[seriesIndex].drawLegendShape(canvas, renderer, x, y, 0, paint);
  }

  /**
   * Returns the chart type identifier.
   * 
   * @return the chart type
   */
  public String getChartType() {
    return "Combined";
  }

}
