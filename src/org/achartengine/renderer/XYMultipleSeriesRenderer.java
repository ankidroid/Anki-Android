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

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.achartengine.util.MathHelper;

import android.graphics.Color;
import android.graphics.Paint.Align;

/**
 * Multiple XY series renderer.
 */
public class XYMultipleSeriesRenderer extends DefaultRenderer {
  /** The X axis title. */
  private String mXTitle = "";
  /** The Y axis title. */
  private String[] mYTitle;
  /** The axis title text size. */
  private float mAxisTitleTextSize = 12;
  /** The start value in the X axis range. */
  private double[] mMinX;
  /** The end value in the X axis range. */
  private double[] mMaxX;
  /** The start value in the Y axis range. */
  private double[] mMinY;
  /** The end value in the Y axis range. */
  private double[] mMaxY;
  /** The approximative number of labels on the x axis. */
  private int mXLabels = 5;
  /** The approximative number of labels on the y axis. */
  private int mYLabels = 5;
  /** The current orientation of the chart. */
  private Orientation mOrientation = Orientation.HORIZONTAL;
  /** The X axis text labels. */
  private Map<Double, String> mXTextLabels = new HashMap<Double, String>();
  /** The Y axis text labels. */
  private Map<Integer, Map<Double, String>> mYTextLabels = new LinkedHashMap<Integer, Map<Double, String>>();
  /** A flag for enabling or not the pan on the X axis. */
  private boolean mPanXEnabled = true;
  /** A flag for enabling or not the pan on the Y axis. */
  private boolean mPanYEnabled = true;
  /** A flag for enabling or not the zoom on the X axis. */
  private boolean mZoomXEnabled = true;
  /** A flag for enabling or not the zoom on the Y axis . */
  private boolean mZoomYEnabled = true;
  /** The spacing between bars, in bar charts. */
  private double mBarSpacing = 0;
  /** The margins colors. */
  private int mMarginsColor = NO_COLOR;
  /** The pan limits. */
  private double[] mPanLimits;
  /** The zoom limits. */
  private double[] mZoomLimits;
  /** The X axis labels rotation angle. */
  private float mXLabelsAngle;
  /** The Y axis labels rotation angle. */
  private float mYLabelsAngle;
  /** The initial axis range. */
  private Map<Integer, double[]> initialRange = new LinkedHashMap<Integer, double[]>();
  /** The point size for charts displaying points. */
  private float mPointSize = 3;
  /** The grid color. */
  private int mGridColor = Color.argb(75, 200, 200, 200);
  /** The number of scales. */
  private int scalesCount;
  /** The X axis labels alignment. */
  private Align xLabelsAlign = Align.CENTER;
  /** The Y axis labels alignment. */
  private Align[] yLabelsAlign;
  /** The Y axis alignment. */
  private Align[] yAxisAlign;
  /** The X axis labels color. */
  private int mXLabelsColor = TEXT_COLOR;
  /** The Y axis labels color. */
  private int[] mYLabelsColor = new int[] { TEXT_COLOR };

  /**
   * An enum for the XY chart orientation of the X axis.
   */
  public enum Orientation {
    HORIZONTAL(0), VERTICAL(90);
    /** The rotate angle. */
    private int mAngle = 0;

    private Orientation(int angle) {
      mAngle = angle;
    }

    /**
     * Return the orientation rotate angle.
     * 
     * @return the orientaion rotate angle
     */
    public int getAngle() {
      return mAngle;
    }
  }

  public XYMultipleSeriesRenderer() {
    this(1);
  }

  public XYMultipleSeriesRenderer(int scaleNumber) {
    scalesCount = scaleNumber;
    initAxesRange(scaleNumber);
  }

  public void initAxesRange(int scales) {
    mYTitle = new String[scales];
    yLabelsAlign = new Align[scales];
    yAxisAlign = new Align[scales];
    mYLabelsColor = new int[scales];
    mMinX = new double[scales];
    mMaxX = new double[scales];
    mMinY = new double[scales];
    mMaxY = new double[scales];
    for (int i = 0; i < scales; i++) {
      mYLabelsColor[i] = TEXT_COLOR;
      initAxesRangeForScale(i);
    }
  }

  public void initAxesRangeForScale(int i) {
    mMinX[i] = MathHelper.NULL_VALUE;
    mMaxX[i] = -MathHelper.NULL_VALUE;
    mMinY[i] = MathHelper.NULL_VALUE;
    mMaxY[i] = -MathHelper.NULL_VALUE;
    double[] range = new double[] { mMinX[i], mMaxX[i], mMinY[i], mMaxY[i] };
    initialRange.put(i, range);
    mYTitle[i] = "";
    mYTextLabels.put(i, new HashMap<Double, String>());
    yLabelsAlign[i] = Align.CENTER;
    yAxisAlign[i] = Align.LEFT;
  }

  /**
   * Returns the current orientation of the chart X axis.
   * 
   * @return the chart orientation
   */
  public Orientation getOrientation() {
    return mOrientation;
  }

  /**
   * Sets the current orientation of the chart X axis.
   * 
   * @param orientation the chart orientation
   */
  public void setOrientation(Orientation orientation) {
    mOrientation = orientation;
  }

  /**
   * Returns the title for the X axis.
   * 
   * @return the X axis title
   */
  public String getXTitle() {
    return mXTitle;
  }

  /**
   * Sets the title for the X axis.
   * 
   * @param title the X axis title
   */
  public void setXTitle(String title) {
    mXTitle = title;
  }

  /**
   * Returns the title for the Y axis.
   * 
   * @return the Y axis title
   */
  public String getYTitle() {
    return getYTitle(0);
  }

  /**
   * Returns the title for the Y axis.
   * 
   * @param scale the renderer scale
   * @return the Y axis title
   */
  public String getYTitle(int scale) {
    return mYTitle[scale];
  }

  /**
   * Sets the title for the Y axis.
   * 
   * @param title the Y axis title
   */
  public void setYTitle(String title) {
    setYTitle(title, 0);
  }

  /**
   * Sets the title for the Y axis.
   * 
   * @param title the Y axis title
   * @param scale the renderer scale
   */
  public void setYTitle(String title, int scale) {
    mYTitle[scale] = title;
  }

  /**
   * Returns the axis title text size.
   * 
   * @return the axis title text size
   */
  public float getAxisTitleTextSize() {
    return mAxisTitleTextSize;
  }

  /**
   * Sets the axis title text size.
   * 
   * @param textSize the chart axis text size
   */
  public void setAxisTitleTextSize(float textSize) {
    mAxisTitleTextSize = textSize;
  }

  /**
   * Returns the start value of the X axis range.
   * 
   * @return the X axis range start value
   */
  public double getXAxisMin() {
    return getXAxisMin(0);
  }

  /**
   * Sets the start value of the X axis range.
   * 
   * @param min the X axis range start value
   */
  public void setXAxisMin(double min) {
    setXAxisMin(min, 0);
  }

  /**
   * Returns if the minimum X value was set.
   * 
   * @return the minX was set or not
   */
  public boolean isMinXSet() {
    return isMinXSet(0);
  }

  /**
   * Returns the end value of the X axis range.
   * 
   * @return the X axis range end value
   */
  public double getXAxisMax() {
    return getXAxisMax(0);
  }

  /**
   * Sets the end value of the X axis range.
   * 
   * @param max the X axis range end value
   */
  public void setXAxisMax(double max) {
    setXAxisMax(max, 0);
  }

  /**
   * Returns if the maximum X value was set.
   * 
   * @return the maxX was set or not
   */
  public boolean isMaxXSet() {
    return isMaxXSet(0);
  }

  /**
   * Returns the start value of the Y axis range.
   * 
   * @return the Y axis range end value
   */
  public double getYAxisMin() {
    return getYAxisMin(0);
  }

  /**
   * Sets the start value of the Y axis range.
   * 
   * @param min the Y axis range start value
   */
  public void setYAxisMin(double min) {
    setYAxisMin(min, 0);
  }

  /**
   * Returns if the minimum Y value was set.
   * 
   * @return the minY was set or not
   */
  public boolean isMinYSet() {
    return isMinYSet(0);
  }

  /**
   * Returns the end value of the Y axis range.
   * 
   * @return the Y axis range end value
   */
  public double getYAxisMax() {
    return getYAxisMax(0);
  }

  /**
   * Sets the end value of the Y axis range.
   * 
   * @param max the Y axis range end value
   */
  public void setYAxisMax(double max) {
    setYAxisMax(max, 0);
  }

  /**
   * Returns if the maximum Y value was set.
   * 
   * @return the maxY was set or not
   */
  public boolean isMaxYSet() {
    return isMaxYSet(0);
  }

  /**
   * Returns the start value of the X axis range.
   * 
   * @param scale the renderer scale
   * @return the X axis range start value
   */
  public double getXAxisMin(int scale) {
    return mMinX[scale];
  }

  /**
   * Sets the start value of the X axis range.
   * 
   * @param min the X axis range start value
   * @param scale the renderer scale
   */
  public void setXAxisMin(double min, int scale) {
    if (!isMinXSet(scale)) {
      initialRange.get(scale)[0] = min;
    }
    mMinX[scale] = min;
  }

  /**
   * Returns if the minimum X value was set.
   * 
   * @param scale the renderer scale
   * @return the minX was set or not
   */
  public boolean isMinXSet(int scale) {
    return mMinX[scale] != MathHelper.NULL_VALUE;
  }

  /**
   * Returns the end value of the X axis range.
   * 
   * @param scale the renderer scale
   * @return the X axis range end value
   */
  public double getXAxisMax(int scale) {
    return mMaxX[scale];
  }

  /**
   * Sets the end value of the X axis range.
   * 
   * @param max the X axis range end value
   * @param scale the renderer scale
   */
  public void setXAxisMax(double max, int scale) {
    if (!isMaxXSet(scale)) {
      initialRange.get(scale)[1] = max;
    }
    mMaxX[scale] = max;
  }

  /**
   * Returns if the maximum X value was set.
   * 
   * @param scale the renderer scale
   * @return the maxX was set or not
   */
  public boolean isMaxXSet(int scale) {
    return mMaxX[scale] != -MathHelper.NULL_VALUE;
  }

  /**
   * Returns the start value of the Y axis range.
   * 
   * @param scale the renderer scale
   * @return the Y axis range end value
   */
  public double getYAxisMin(int scale) {
    return mMinY[scale];
  }

  /**
   * Sets the start value of the Y axis range.
   * 
   * @param min the Y axis range start value
   * @param scale the renderer scale
   */
  public void setYAxisMin(double min, int scale) {
    if (!isMinYSet(scale)) {
      initialRange.get(scale)[2] = min;
    }
    mMinY[scale] = min;
  }

  /**
   * Returns if the minimum Y value was set.
   * 
   * @param scale the renderer scale
   * @return the minY was set or not
   */
  public boolean isMinYSet(int scale) {
    return mMinY[scale] != MathHelper.NULL_VALUE;
  }

  /**
   * Returns the end value of the Y axis range.
   * 
   * @param scale the renderer scale
   * @return the Y axis range end value
   */
  public double getYAxisMax(int scale) {
    return mMaxY[scale];
  }

  /**
   * Sets the end value of the Y axis range.
   * 
   * @param max the Y axis range end value
   * @param scale the renderer scale
   */
  public void setYAxisMax(double max, int scale) {
    if (!isMaxYSet(scale)) {
      initialRange.get(scale)[3] = max;
    }
    mMaxY[scale] = max;
  }

  /**
   * Returns if the maximum Y value was set.
   * 
   * @param scale the renderer scale
   * @return the maxY was set or not
   */
  public boolean isMaxYSet(int scale) {
    return mMaxY[scale] != -MathHelper.NULL_VALUE;
  }

  /**
   * Returns the approximate number of labels for the X axis.
   * 
   * @return the approximate number of labels for the X axis
   */
  public int getXLabels() {
    return mXLabels;
  }

  /**
   * Sets the approximate number of labels for the X axis.
   * 
   * @param xLabels the approximate number of labels for the X axis
   */
  public void setXLabels(int xLabels) {
    mXLabels = xLabels;
  }

  /**
   * Adds a new text label for the specified X axis value.
   * 
   * @param x the X axis value
   * @param text the text label
   * @deprecated use addXTextLabel instead
   */
  public void addTextLabel(double x, String text) {
    addXTextLabel(x, text);
  }

  /**
   * Adds a new text label for the specified X axis value.
   * 
   * @param x the X axis value
   * @param text the text label
   */
  public void addXTextLabel(double x, String text) {
    mXTextLabels.put(x, text);
  }

  /**
   * Returns the X axis text label at the specified X axis value.
   * 
   * @param x the X axis value
   * @return the X axis text label
   */
  public String getXTextLabel(Double x) {
    return mXTextLabels.get(x);
  }

  /**
   * Returns the X text label locations.
   * 
   * @return the X text label locations
   */
  public Double[] getXTextLabelLocations() {
    return mXTextLabels.keySet().toArray(new Double[0]);
  }

  /**
   * Clears the existing text labels.
   * 
   * @deprecated use clearXTextLabels instead
   */
  public void clearTextLabels() {
    clearXTextLabels();
  }

  /**
   * Clears the existing text labels on the X axis.
   */
  public void clearXTextLabels() {
    mXTextLabels.clear();
  }

  /**
   * Adds a new text label for the specified Y axis value.
   * 
   * @param y the Y axis value
   * @param text the text label
   */
  public void addYTextLabel(double y, String text) {
    addYTextLabel(y, text, 0);
  }

  /**
   * Adds a new text label for the specified Y axis value.
   * 
   * @param y the Y axis value
   * @param text the text label
   * @param scale the renderer scale
   */
  public void addYTextLabel(double y, String text, int scale) {
    mYTextLabels.get(scale).put(y, text);
  }

  /**
   * Returns the Y axis text label at the specified Y axis value.
   * 
   * @param y the Y axis value
   * @return the Y axis text label
   */
  public String getYTextLabel(Double y) {
    return getYTextLabel(y, 0);
  }

  /**
   * Returns the Y axis text label at the specified Y axis value.
   * 
   * @param y the Y axis value
   * @param scale the renderer scale
   * @return the Y axis text label
   */
  public String getYTextLabel(Double y, int scale) {
    return mYTextLabels.get(scale).get(y);
  }

  /**
   * Returns the Y text label locations.
   * 
   * @return the Y text label locations
   */
  public Double[] getYTextLabelLocations() {
    return getYTextLabelLocations(0);
  }

  /**
   * Returns the Y text label locations.
   * 
   * @param scale the renderer scale
   * @return the Y text label locations
   */
  public Double[] getYTextLabelLocations(int scale) {
    return mYTextLabels.get(scale).keySet().toArray(new Double[0]);
  }

  /**
   * Clears the existing text labels on the Y axis.
   */
  public void clearYTextLabels() {
    mYTextLabels.clear();
  }

  /**
   * Returns the approximate number of labels for the Y axis.
   * 
   * @return the approximate number of labels for the Y axis
   */
  public int getYLabels() {
    return mYLabels;
  }

  /**
   * Sets the approximate number of labels for the Y axis.
   * 
   * @param yLabels the approximate number of labels for the Y axis
   */
  public void setYLabels(int yLabels) {
    mYLabels = yLabels;
  }

  /**
   * Sets if the chart point values should be displayed as text.
   * 
   * @param display if the chart point values should be displayed as text
   * @deprecated use SimpleSeriesRenderer.setDisplayChartValues() instead
   */
  public void setDisplayChartValues(boolean display) {
    SimpleSeriesRenderer[] renderers = getSeriesRenderers();
    for (SimpleSeriesRenderer renderer : renderers) {
      renderer.setDisplayChartValues(display);
    }
  }

  /**
   * Sets the chart values text size.
   * 
   * @param textSize the chart values text size
   * @deprecated use SimpleSeriesRenderer.setChartValuesTextSize() instead
   */
  public void setChartValuesTextSize(float textSize) {
    SimpleSeriesRenderer[] renderers = getSeriesRenderers();
    for (SimpleSeriesRenderer renderer : renderers) {
      renderer.setChartValuesTextSize(textSize);
    }
  }

  /**
   * Returns the enabled state of the pan on at least one axis.
   * 
   * @return if pan is enabled
   */
  public boolean isPanEnabled() {
    return isPanXEnabled() || isPanYEnabled();
  }

  /**
   * Returns the enabled state of the pan on X axis.
   * 
   * @return if pan is enabled on X axis
   */
  public boolean isPanXEnabled() {
    return mPanXEnabled;
  }

  /**
   * Returns the enabled state of the pan on Y axis.
   * 
   * @return if pan is enabled on Y axis
   */
  public boolean isPanYEnabled() {
    return mPanYEnabled;
  }

  /**
   * Sets the enabled state of the pan.
   * 
   * @param enabledX pan enabled on X axis
   * @param enabledY pan enabled on Y axis
   */
  public void setPanEnabled(boolean enabledX, boolean enabledY) {
    mPanXEnabled = enabledX;
    mPanYEnabled = enabledY;
  }

  /**
   * Returns the enabled state of the zoom on at least one axis.
   * 
   * @return if zoom is enabled
   */
  public boolean isZoomEnabled() {
    return isZoomXEnabled() || isZoomYEnabled();
  }

  /**
   * Returns the enabled state of the zoom on X axis.
   * 
   * @return if zoom is enabled on X axis
   */
  public boolean isZoomXEnabled() {
    return mZoomXEnabled;
  }

  /**
   * Returns the enabled state of the zoom on Y axis.
   * 
   * @return if zoom is enabled on Y axis
   */
  public boolean isZoomYEnabled() {
    return mZoomYEnabled;
  }

  /**
   * Sets the enabled state of the zoom.
   * 
   * @param enabledX zoom enabled on X axis
   * @param enabledY zoom enabled on Y axis
   */
  public void setZoomEnabled(boolean enabledX, boolean enabledY) {
    mZoomXEnabled = enabledX;
    mZoomYEnabled = enabledY;
  }

  /**
   * Returns the spacing between bars, in bar charts.
   * 
   * @return the spacing between bars
   * @deprecated use getBarSpacing instead
   */
  public double getBarsSpacing() {
    return getBarSpacing();
  }

  /**
   * Returns the spacing between bars, in bar charts.
   * 
   * @return the spacing between bars
   */
  public double getBarSpacing() {
    return mBarSpacing;
  }

  /**
   * Sets the spacing between bars, in bar charts. Only available for bar
   * charts. This is a coefficient of the bar width. For instance, if you want
   * the spacing to be a half of the bar width, set this value to 0.5.
   * 
   * @param spacing the spacing between bars coefficient
   */
  public void setBarSpacing(double spacing) {
    mBarSpacing = spacing;
  }

  /**
   * Returns the margins color.
   * 
   * @return the margins color
   */
  public int getMarginsColor() {
    return mMarginsColor;
  }

  /**
   * Sets the color of the margins.
   * 
   * @param color the margins color
   */
  public void setMarginsColor(int color) {
    mMarginsColor = color;
  }

  /**
   * Returns the grid color.
   * 
   * @return the grid color
   */
  public int getGridColor() {
    return mGridColor;
  }

  /**
   * Sets the color of the grid.
   * 
   * @param color the grid color
   */
  public void setGridColor(int color) {
    mGridColor = color;
  }

  /**
   * Returns the pan limits.
   * 
   * @return the pan limits
   */
  public double[] getPanLimits() {
    return mPanLimits;
  }

  /**
   * Sets the pan limits as an array of 4 values. Setting it to null or a
   * different size array will disable the panning limitation. Values:
   * [panMinimumX, panMaximumX, panMinimumY, panMaximumY]
   * 
   * @param panLimits the pan limits
   */
  public void setPanLimits(double[] panLimits) {
    mPanLimits = panLimits;
  }

  /**
   * Returns the zoom limits.
   * 
   * @return the zoom limits
   */
  public double[] getZoomLimits() {
    return mZoomLimits;
  }

  /**
   * Sets the zoom limits as an array of 4 values. Setting it to null or a
   * different size array will disable the zooming limitation. Values:
   * [zoomMinimumX, zoomMaximumX, zoomMinimumY, zoomMaximumY]
   * 
   * @param zoomLimits the zoom limits
   */
  public void setZoomLimits(double[] zoomLimits) {
    mZoomLimits = zoomLimits;
  }

  /**
   * Returns the rotation angle of labels for the X axis.
   * 
   * @return the rotation angle of labels for the X axis
   */
  public float getXLabelsAngle() {
    return mXLabelsAngle;
  }

  /**
   * Sets the rotation angle (in degrees) of labels for the X axis.
   * 
   * @param angle the rotation angle of labels for the X axis
   */
  public void setXLabelsAngle(float angle) {
    mXLabelsAngle = angle;
  }

  /**
   * Returns the rotation angle of labels for the Y axis.
   * 
   * @return the approximate number of labels for the Y axis
   */
  public float getYLabelsAngle() {
    return mYLabelsAngle;
  }

  /**
   * Sets the rotation angle (in degrees) of labels for the Y axis.
   * 
   * @param angle the rotation angle of labels for the Y axis
   */
  public void setYLabelsAngle(float angle) {
    mYLabelsAngle = angle;
  }

  /**
   * Returns the size of the points, for charts displaying points.
   * 
   * @return the point size
   */
  public float getPointSize() {
    return mPointSize;
  }

  /**
   * Sets the size of the points, for charts displaying points.
   * 
   * @param size the point size
   */
  public void setPointSize(float size) {
    mPointSize = size;
  }

  public void setRange(double[] range) {
    setRange(range, 0);
  }

  /**
   * Sets the axes range values.
   * 
   * @param range an array having the values in this order: minX, maxX, minY,
   *          maxY
   * @param scale the renderer scale
   */
  public void setRange(double[] range, int scale) {
    setXAxisMin(range[0], scale);
    setXAxisMax(range[1], scale);
    setYAxisMin(range[2], scale);
    setYAxisMax(range[3], scale);
  }

  public boolean isInitialRangeSet() {
    return isInitialRangeSet(0);
  }

  /**
   * Returns if the initial range is set.
   * 
   * @param scale the renderer scale
   * @return the initial range was set or not
   */
  public boolean isInitialRangeSet(int scale) {
    return initialRange.get(scale) != null;
  }

  /**
   * Returns the initial range.
   * 
   * @return the initial range
   */
  public double[] getInitialRange() {
    return getInitialRange(0);
  }

  /**
   * Returns the initial range.
   * 
   * @param scale the renderer scale
   * @return the initial range
   */
  public double[] getInitialRange(int scale) {
    return initialRange.get(scale);
  }

  /**
   * Sets the axes initial range values. This will be used in the zoom fit tool.
   * 
   * @param range an array having the values in this order: minX, maxX, minY,
   *          maxY
   */
  public void setInitialRange(double[] range) {
    setInitialRange(range, 0);
  }

  /**
   * Sets the axes initial range values. This will be used in the zoom fit tool.
   * 
   * @param range an array having the values in this order: minX, maxX, minY,
   *          maxY
   * @param scale the renderer scale
   */
  public void setInitialRange(double[] range, int scale) {
    initialRange.put(scale, range);
  }
  
  /**
   * Returns the X axis labels color.
   * 
   * @return the X axis labels color
   */
  public int getXLabelsColor() {
    return mXLabelsColor;
  }

  /**
   * Returns the Y axis labels color.
   * 
   * @return the Y axis labels color
   */
  public int getYLabelsColor(int scale) {
    return mYLabelsColor[scale];
  }

  /**
   * Sets the X axis labels color.
   * 
   * @param color the X axis labels color
   */
  public void setXLabelsColor(int color) {
    mXLabelsColor = color;
  }

  /**
   * Sets the Y axis labels color.
   *
   * @param scale the renderer scale
   * @param color the Y axis labels color
   */
  public void setYLabelsColor(int scale, int color) {
    mYLabelsColor[scale] = color;
  }

  /**
   * Returns the X axis labels alignment.
   * 
   * @return X labels alignment
   */
  public Align getXLabelsAlign() {
    return xLabelsAlign;
  }

  /**
   * Sets the X axis labels alignment.
   * 
   * @param align the X labels alignment
   */
  public void setXLabelsAlign(Align align) {
    xLabelsAlign = align;
  }

  /**
   * Returns the Y axis labels alignment.
   * 
   * @param scale the renderer scale
   * @return Y labels alignment
   */
  public Align getYLabelsAlign(int scale) {
    return yLabelsAlign[scale];
  }

  public void setYLabelsAlign(Align align) {
    setYLabelsAlign(align, 0);
  }

  public Align getYAxisAlign(int scale) {
    return yAxisAlign[scale];
  }

  public void setYAxisAlign(Align align, int scale) {
    yAxisAlign[scale] = align;
  }

  /**
   * Sets the Y axis labels alignment.
   * 
   * @param align the Y labels alignment
   */
  public void setYLabelsAlign(Align align, int scale) {
    yLabelsAlign[scale] = align;
  }

  public int getScalesCount() {
    return scalesCount;
  }

}
