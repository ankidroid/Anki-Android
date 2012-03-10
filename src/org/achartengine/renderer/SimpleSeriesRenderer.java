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

import java.io.Serializable;

import android.graphics.Color;
import android.graphics.Paint.Align;

/**
 * A simple series renderer.
 */
public class SimpleSeriesRenderer implements Serializable {
  /** The series color. */
  private int mColor = Color.BLUE;
  /** If the values should be displayed above the chart points. */
  private boolean mDisplayChartValues;
  /** The chart values text size. */
  private float mChartValuesTextSize = 10;
  /** The chart values text alignment. */
  private Align mChartValuesTextAlign = Align.CENTER;
  /** The chart values spacing from the data point. */
  private float mChartValuesSpacing = 5f;
  /** The stroke style. */
  private BasicStroke mStroke;
  /** If gradient is enabled. */
  private boolean mGradientEnabled = false;
  /** The gradient start value. */
  private double mGradientStartValue; 
  /** The gradient start color. */
  private int mGradientStartColor;
  /** The gradient stop value. */
  private double mGradientStopValue; 
  /** The gradient stop color. */
  private int mGradientStopColor;
  
  /**
   * Returns the series color.
   * 
   * @return the series color
   */
  public int getColor() {
    return mColor;
  }

  /**
   * Sets the series color.
   * 
   * @param color the series color
   */
  public void setColor(int color) {
    mColor = color;
  }

  /**
   * Returns if the chart point values should be displayed as text.
   * 
   * @return if the chart point values should be displayed as text
   */
  public boolean isDisplayChartValues() {
    return mDisplayChartValues;
  }

  /**
   * Sets if the chart point values should be displayed as text.
   * 
   * @param display if the chart point values should be displayed as text
   */
  public void setDisplayChartValues(boolean display) {
    mDisplayChartValues = display;
  }

  /**
   * Returns the chart values text size.
   * 
   * @return the chart values text size
   */
  public float getChartValuesTextSize() {
    return mChartValuesTextSize;
  }

  /**
   * Sets the chart values text size.
   * 
   * @param textSize the chart values text size
   */
  public void setChartValuesTextSize(float textSize) {
    mChartValuesTextSize = textSize;
  }

  
  /**
   * Returns the chart values text align.
   * 
   * @return the chart values text align
   */
  public Align getChartValuesTextAlign() {
    return mChartValuesTextAlign;
  }

  /**
   * Sets the chart values text align.
   * 
   * @param align the chart values text align
   */
  public void setChartValuesTextAlign(Align align) {
    mChartValuesTextAlign = align;
  }
  
  /**
   * Returns the chart values spacing from the data point.
   * 
   * @return the chart values spacing
   */
  public float getChartValuesSpacing() {
    return mChartValuesSpacing;
  }

  /**
   * Sets the chart values spacing from the data point.
   * 
   * @param spacing the chart values spacing (in pixels) from the chart data point
   */
  public void setChartValuesSpacing(float spacing) {
    mChartValuesSpacing = spacing;
  }

  /**
   * Returns the stroke style.
   * 
   * @return the stroke style
   */
  public BasicStroke getStroke() {
    return mStroke;
  }

  /**
   * Sets the stroke style.
   * 
   * @param stroke the stroke style
   */
  public void setStroke(BasicStroke stroke) {
    mStroke = stroke;
  }
  
  /**
   * Returns the gradient is enabled value.
   * @return the gradient enabled
   */
  public boolean isGradientEnabled() {
    return mGradientEnabled;
  }
  
  /**
   * Sets the gradient enabled value.
   * @param enabled the gradient enabled
   */
  public void setGradientEnabled(boolean enabled) {
    mGradientEnabled = enabled;
  }

  /**
   * Returns the gradient start value.
   * @return the gradient start value
   */
  public double getGradientStartValue() {
    return mGradientStartValue;
  }
  
  /**
   * Returns the gradient start color.
   * @return the gradient start color
   */
  public int getGradientStartColor() {
    return mGradientStartColor;
  }
  
  /**
   * Sets the gradient start value and color.
   * @param start the gradient start value
   * @param color the gradient start color
   */
  public void setGradientStart(double start, int color) {
    mGradientStartValue = start;
    mGradientStartColor = color;
  }

  /**
   * Returns the gradient stop value.
   * @return the gradient stop value
   */
  public double getGradientStopValue() {
    return mGradientStopValue;
  }
  
  /**
   * Returns the gradient stop color.
   * @return the gradient stop color
   */
  public int getGradientStopColor() {
    return mGradientStopColor;
  }
  
  /**
   * Sets the gradient stop value and color.
   * @param start the gradient stop value
   * @param color the gradient stop color
   */
  public void setGradientStop(double start, int color) {
    mGradientStopValue = start;
    mGradientStopColor = color;
  }

}
