/**
 * Copyright (C) 2009, 2010 SC 4ViewSoft SRL
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
package org.achartengine.renderer;

import org.achartengine.chart.PointStyle;

import android.graphics.Color;

/**
 * A renderer for the XY type series.
 */
public class XYSeriesRenderer extends SimpleSeriesRenderer {
  /** If the chart points should be filled. */
  private boolean mFillPoints = false;
  /** If the chart should be filled below its line. */
  private boolean mFillBelowLine = false;
  /** The fill below the chart line color. */
  private int mFillColor = Color.argb(125, 0, 0, 200);
  /** The point style. */
  private PointStyle mPointStyle = PointStyle.POINT;
  /** The chart line width. */
  private float mLineWidth = 1;

  /**
   * Returns if the chart should be filled below the line.
   * 
   * @return the fill below line status
   */
  public boolean isFillBelowLine() {
    return mFillBelowLine;
  }

  /**
   * Sets if the line chart should be filled below its line. Filling below the
   * line transforms a line chart into an area chart.
   * 
   * @param fill the fill below line flag value
   */
  public void setFillBelowLine(boolean fill) {
    mFillBelowLine = fill;
  }

  /**
   * Returns if the chart points should be filled.
   * 
   * @return the points fill status
   */
  public boolean isFillPoints() {
    return mFillPoints;
  }

  /**
   * Sets if the chart points should be filled.
   * 
   * @param fill the points fill flag value
   */
  public void setFillPoints(boolean fill) {
    mFillPoints = fill;
  }

  /**
   * Returns the fill below line color.
   * 
   * @return the fill below line color
   */
  public int getFillBelowLineColor() {
    return mFillColor;
  }

  /**
   * Sets the fill below the line color.
   * 
   * @param color the fill below line color
   */
  public void setFillBelowLineColor(int color) {
    mFillColor = color;
  }

  /**
   * Returns the point style.
   * 
   * @return the point style
   */
  public PointStyle getPointStyle() {
    return mPointStyle;
  }

  /**
   * Sets the point style.
   * 
   * @param style the point style
   */
  public void setPointStyle(PointStyle style) {
    mPointStyle = style;
  }

  /**
   * Returns the chart line width.
   * 
   * @return the line width
   */
  public float getLineWidth() {
    return mLineWidth;
  }

  /**
   * Sets the chart line width.
   * 
   * @param lineWidth the line width
   */
  public void setLineWidth(float lineWidth) {
    mLineWidth = lineWidth;
  }
  
}
