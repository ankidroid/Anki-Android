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

import org.achartengine.model.Point;
import org.achartengine.model.XYMultipleSeriesDataset;
import org.achartengine.renderer.XYMultipleSeriesRenderer;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;

/**
 * The interpolated (cubic) line chart rendering class.
 */
public class CubicLineChart extends LineChart {
  /** The chart type. */
  public static final String TYPE = "Cubic";

  private float firstMultiplier;

  private float secondMultiplier;

  private Point p1 = new Point();

  private Point p2 = new Point();

  private Point p3 = new Point();

  public CubicLineChart() {
    // default is to have first control point at about 33% of the distance,
    firstMultiplier = 0.33f;
    // and the next at 66% of the distance.
    secondMultiplier = 1 - firstMultiplier;
  }

  /**
   * Builds a cubic line chart.
   * 
   * @param dataset the dataset
   * @param renderer the renderer
   * @param smoothness smoothness determines how smooth the curve should be,
   *          range [0->0.5] super smooth, 0.5, means that it might not get
   *          close to control points if you have random data // less smooth,
   *          (close to 0) means that it will most likely touch all control //
   *          points
   */
  public CubicLineChart(XYMultipleSeriesDataset dataset, XYMultipleSeriesRenderer renderer,
      float smoothness) {
    super(dataset, renderer);
    firstMultiplier = smoothness;
    secondMultiplier = 1 - firstMultiplier;
  }

  @Override
  protected void drawPath(Canvas canvas, float[] points, Paint paint, boolean circular) {
    Path p = new Path();
    float x = points[0];
    float y = points[1];
    p.moveTo(x, y);

    int length = points.length;
    if (circular) {
      length -= 4;
    }

    for (int i = 0; i < length; i += 2) {
      int nextIndex = i + 2 < length ? i + 2 : i;
      int nextNextIndex = i + 4 < length ? i + 4 : nextIndex;
      calc(points, p1, i, nextIndex, secondMultiplier);
      p2.setX(points[nextIndex]);
      p2.setY(points[nextIndex + 1]);
      calc(points, p3, nextIndex, nextNextIndex, firstMultiplier);
      // From last point, approaching x1/y1 and x2/y2 and ends up at x3/y3
      p.cubicTo(p1.getX(), p1.getY(), p2.getX(), p2.getY(), p3.getX(), p3.getY());
    }
    if (circular) {
      for (int i = length; i < length + 4; i += 2) {
        p.lineTo(points[i], points[i + 1]);
      }
      p.lineTo(points[0], points[1]);
    }
    canvas.drawPath(p, paint);
  }

  private void calc(float[] points, Point result, int index1, int index2, final float multiplier) {
    float p1x = points[index1];
    float p1y = points[index1 + 1];
    float p2x = points[index2];
    float p2y = points[index2 + 1];

    float diffX = p2x - p1x; // p2.x - p1.x;
    float diffY = p2y - p1y; // p2.y - p1.y;
    result.setX(p1x + (diffX * multiplier));
    result.setY(p1y + (diffY * multiplier));
  }

  /**
   * Returns the chart type identifier.
   * 
   * @return the chart type
   */
  public String getChartType() {
    return TYPE;
  }

}
