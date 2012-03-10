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
import java.util.ArrayList;
import java.util.List;

import android.graphics.Color;
import android.graphics.Typeface;

/**
 * An abstract renderer to be extended by the multiple series classes.
 */
public class DefaultRenderer implements Serializable {
  /** The chart title. */
  private String mChartTitle = "";
  /** The chart title text size. */
  private float mChartTitleTextSize = 15;
  /** A no color constant. */
  public static final int NO_COLOR = 0;
  /** The default background color. */
  public static final int BACKGROUND_COLOR = Color.BLACK;
  /** The default color for text. */
  public static final int TEXT_COLOR = Color.LTGRAY;
  /** A text font for regular text, like the chart labels. */
  private static final Typeface REGULAR_TEXT_FONT = Typeface
      .create(Typeface.SERIF, Typeface.NORMAL);
  /** The typeface name for the texts. */
  private String mTextTypefaceName = REGULAR_TEXT_FONT.toString();
  /** The typeface style for the texts. */
  private int mTextTypefaceStyle = Typeface.NORMAL;
  /** The chart background color. */
  private int mBackgroundColor;
  /** If the background color is applied. */
  private boolean mApplyBackgroundColor;
  /** If the axes are visible. */
  private boolean mShowAxes = true;
  /** The axes color. */
  private int mAxesColor = TEXT_COLOR;
  /** If the labels are visible. */
  private boolean mShowLabels = true;
  /** The labels color. */
  private int mLabelsColor = TEXT_COLOR;
  /** The labels text size. */
  private float mLabelsTextSize = 10;
  /** If the legend is visible. */
  private boolean mShowLegend = true;
  /** The legend text size. */
  private float mLegendTextSize = 12;
  /** If the legend should size to fit. */
  private boolean mFitLegend = false;
  /** If the X axis grid should be displayed. */
  private boolean mShowGridX = false;
  /** If the Y axis grid should be displayed. */
  private boolean mShowGridY = false;
  /** If the custom text grid should be displayed. */
  private boolean mShowCustomTextGrid = false;
  /** The simple renderers that are included in this multiple series renderer. */
  private List<SimpleSeriesRenderer> mRenderers = new ArrayList<SimpleSeriesRenderer>();
  /** The antialiasing flag. */
  private boolean mAntialiasing = true;
  /** The legend height. */
  private int mLegendHeight = 0;
  /** The margins size. */
  private int[] mMargins = new int[] { 20, 30, 10, 20 };
  /** A value to be used for scaling the chart. */
  private float mScale = 1;
  /** A flag for enabling the pan. */
  private boolean mPanEnabled = true;
  /** A flag for enabling the zoom. */
  private boolean mZoomEnabled = true;
  /** A flag for enabling the visibility of the zoom buttons. */
  private boolean mZoomButtonsVisible = false;
  /** The zoom rate. */
  private float mZoomRate = 1.5f;
  /** A flag for enabling the external zoom. */
  private boolean mExternalZoomEnabled = false;
  /** The original chart scale. */
  private float mOriginalScale = mScale;
  /** A flag for enabling the click on elements. */
  private boolean mClickEnabled = false;
  /** The selectable radius around a clickable point. */
  private int selectableBuffer = 15;

  /**
   * A flag to be set if the chart is inside a scroll and doesn't need to shrink
   * when not enough space.
   */
  private boolean mInScroll;
  /** The start angle for circular charts such as pie, doughnut, etc. */
  private float mStartAngle = 0;

  /**
   * Returns the chart title.
   * 
   * @return the chart title
   */
  public String getChartTitle() {
    return mChartTitle;
  }

  /**
   * Sets the chart title.
   * 
   * @param title the chart title
   */
  public void setChartTitle(String title) {
    mChartTitle = title;
  }

  /**
   * Returns the chart title text size.
   * 
   * @return the chart title text size
   */
  public float getChartTitleTextSize() {
    return mChartTitleTextSize;
  }

  /**
   * Sets the chart title text size.
   * 
   * @param textSize the chart title text size
   */
  public void setChartTitleTextSize(float textSize) {
    mChartTitleTextSize = textSize;
  }

  /**
   * Adds a simple renderer to the multiple renderer.
   * 
   * @param renderer the renderer to be added
   */
  public void addSeriesRenderer(SimpleSeriesRenderer renderer) {
    mRenderers.add(renderer);
  }

  /**
   * Adds a simple renderer to the multiple renderer.
   * 
   * @param index the index in the renderers list
   * @param renderer the renderer to be added
   */
  public void addSeriesRenderer(int index, SimpleSeriesRenderer renderer) {
    mRenderers.add(index, renderer);
  }

  /**
   * Removes a simple renderer from the multiple renderer.
   * 
   * @param renderer the renderer to be removed
   */
  public void removeSeriesRenderer(SimpleSeriesRenderer renderer) {
    mRenderers.remove(renderer);
  }

  /**
   * Returns the simple renderer from the multiple renderer list.
   * 
   * @param index the index in the simple renderers list
   * @return the simple renderer at the specified index
   */
  public SimpleSeriesRenderer getSeriesRendererAt(int index) {
    return mRenderers.get(index);
  }

  /**
   * Returns the simple renderers count in the multiple renderer list.
   * 
   * @return the simple renderers count
   */
  public int getSeriesRendererCount() {
    return mRenderers.size();
  }

  /**
   * Returns an array of the simple renderers in the multiple renderer list.
   * 
   * @return the simple renderers array
   */
  public SimpleSeriesRenderer[] getSeriesRenderers() {
    return mRenderers.toArray(new SimpleSeriesRenderer[0]);
  }

  /**
   * Returns the background color.
   * 
   * @return the background color
   */
  public int getBackgroundColor() {
    return mBackgroundColor;
  }

  /**
   * Sets the background color.
   * 
   * @param color the background color
   */
  public void setBackgroundColor(int color) {
    mBackgroundColor = color;
  }

  /**
   * Returns if the background color should be applied.
   * 
   * @return the apply flag for the background color.
   */
  public boolean isApplyBackgroundColor() {
    return mApplyBackgroundColor;
  }

  /**
   * Sets if the background color should be applied.
   * 
   * @param apply the apply flag for the background color
   */
  public void setApplyBackgroundColor(boolean apply) {
    mApplyBackgroundColor = apply;
  }

  /**
   * Returns the axes color.
   * 
   * @return the axes color
   */
  public int getAxesColor() {
    return mAxesColor;
  }

  /**
   * Sets the axes color.
   * 
   * @param color the axes color
   */
  public void setAxesColor(int color) {
    mAxesColor = color;
  }


  /**
   * Returns the labels color.
   * 
   * @return the labels color
   */
  public int getLabelsColor() {
    return mLabelsColor;
  }
  
  /**
   * Sets the labels color.
   * 
   * @param color the labels color
   */
  public void setLabelsColor(int color) {
    mLabelsColor = color;
  }

  /**
   * Returns the labels text size.
   * 
   * @return the labels text size
   */
  public float getLabelsTextSize() {
    return mLabelsTextSize;
  }

  /**
   * Sets the labels text size.
   * 
   * @param textSize the labels text size
   */
  public void setLabelsTextSize(float textSize) {
    mLabelsTextSize = textSize;
  }

  /**
   * Returns if the axes should be visible.
   * 
   * @return the visibility flag for the axes
   */
  public boolean isShowAxes() {
    return mShowAxes;
  }

  /**
   * Sets if the axes should be visible.
   * 
   * @param showAxes the visibility flag for the axes
   */
  public void setShowAxes(boolean showAxes) {
    mShowAxes = showAxes;
  }

  /**
   * Returns if the labels should be visible.
   * 
   * @return the visibility flag for the labels
   */
  public boolean isShowLabels() {
    return mShowLabels;
  }

  /**
   * Sets if the labels should be visible.
   * 
   * @param showLabels the visibility flag for the labels
   */
  public void setShowLabels(boolean showLabels) {
    mShowLabels = showLabels;
  }

  /**
   * Returns if the X axis grid should be visible.
   * 
   * @return the visibility flag for the X axis grid
   */
  public boolean isShowGridX() {
    return mShowGridX;
  }

  /**
   * Returns if the Y axis grid should be visible.
   * 
   * @return the visibility flag for the Y axis grid
   */
  public boolean isShowGridY() {
    return mShowGridY;
  }

  /**
   * Sets if the X axis grid should be visible.
   * 
   * @param showGrid the visibility flag for the X axis grid
   */
  public void setShowGridX(boolean showGrid) {
    mShowGridX = showGrid;
  }

  /**
   * Sets if the Y axis grid should be visible.
   * 
   * @param showGrid the visibility flag for the Y axis grid
   */
  public void setShowGridY(boolean showGrid) {
    mShowGridY = showGrid;
  }

  /**
   * Sets if the grid should be visible.
   * 
   * @param showGrid the visibility flag for the grid
   */
  public void setShowGrid(boolean showGrid) {
    setShowGridX(showGrid);
    setShowGridY(showGrid);
  }

  /**
   * Returns if the grid should be visible for custom X or Y labels.
   * 
   * @return the visibility flag for the custom text grid
   */
  public boolean isShowCustomTextGrid() {
    return mShowCustomTextGrid;
  }

  /**
   * Sets if the grid for custom X or Y labels should be visible.
   * 
   * @param showGrid the visibility flag for the custom text grid
   */
  public void setShowCustomTextGrid(boolean showGrid) {
    mShowCustomTextGrid = showGrid;
  }

  /**
   * Returns if the legend should be visible.
   * 
   * @return the visibility flag for the legend
   */
  public boolean isShowLegend() {
    return mShowLegend;
  }

  /**
   * Sets if the legend should be visible.
   * 
   * @param showLegend the visibility flag for the legend
   */
  public void setShowLegend(boolean showLegend) {
    mShowLegend = showLegend;
  }

  /**
   * Returns if the legend should size to fit.
   * 
   * @return the fit behavior
   */
  public boolean isFitLegend() {
    return mFitLegend;
  }

  /**
   * Sets if the legend should size to fit.
   * 
   * @param fit the fit behavior
   */
  public void setFitLegend(boolean fit) {
    mFitLegend = fit;
  }

  /**
   * Returns the text typeface name.
   * 
   * @return the text typeface name
   */
  public String getTextTypefaceName() {
    return mTextTypefaceName;
  }

  /**
   * Returns the text typeface style.
   * 
   * @return the text typeface style
   */
  public int getTextTypefaceStyle() {
    return mTextTypefaceStyle;
  }

  /**
   * Returns the legend text size.
   * 
   * @return the legend text size
   */
  public float getLegendTextSize() {
    return mLegendTextSize;
  }

  /**
   * Sets the legend text size.
   * 
   * @param textSize the legend text size
   */
  public void setLegendTextSize(float textSize) {
    mLegendTextSize = textSize;
  }

  /**
   * Sets the text typeface name and style.
   * 
   * @param typefaceName the text typeface name
   * @param style the text typeface style
   */
  public void setTextTypeface(String typefaceName, int style) {
    mTextTypefaceName = typefaceName;
    mTextTypefaceStyle = style;
  }

  /**
   * Returns the antialiasing flag value.
   * 
   * @return the antialiasing value
   */
  public boolean isAntialiasing() {
    return mAntialiasing;
  }

  /**
   * Sets the antialiasing value.
   * 
   * @param antialiasing the antialiasing
   */
  public void setAntialiasing(boolean antialiasing) {
    mAntialiasing = antialiasing;
  }

  /**
   * Returns the value to be used for scaling the chart.
   * 
   * @return the scale value
   */
  public float getScale() {
    return mScale;
  }

  /**
   * Returns the original value to be used for scaling the chart.
   * 
   * @return the original scale value
   */
  public float getOriginalScale() {
    return mOriginalScale;
  }

  /**
   * Sets the value to be used for scaling the chart. It works on some charts
   * like pie, doughnut, dial.
   * 
   * @param scale the scale value
   */
  public void setScale(float scale) {
    mScale = scale;
  }

  /**
   * Returns the enabled state of the zoom.
   * 
   * @return if zoom is enabled
   */
  public boolean isZoomEnabled() {
    return mZoomEnabled;
  }

  /**
   * Sets the enabled state of the zoom.
   * 
   * @param enabled zoom enabled
   */
  public void setZoomEnabled(boolean enabled) {
    mZoomEnabled = enabled;
  }

  /**
   * Returns the visible state of the zoom buttons.
   * 
   * @return if zoom buttons are visible
   */
  public boolean isZoomButtonsVisible() {
    return mZoomButtonsVisible;
  }

  /**
   * Sets the visible state of the zoom buttons.
   * 
   * @param visible if the zoom buttons are visible
   */
  public void setZoomButtonsVisible(boolean visible) {
    mZoomButtonsVisible = visible;
  }

  /**
   * Returns the enabled state of the external (application implemented) zoom.
   * 
   * @return if external zoom is enabled
   */
  public boolean isExternalZoomEnabled() {
    return mExternalZoomEnabled;
  }

  /**
   * Sets the enabled state of the external (application implemented) zoom.
   * 
   * @param enabled external zoom enabled
   */
  public void setExternalZoomEnabled(boolean enabled) {
    mExternalZoomEnabled = enabled;
  }

  /**
   * Returns the zoom rate.
   * 
   * @return the zoom rate
   */
  public float getZoomRate() {
    return mZoomRate;
  }

  /**
   * Returns the enabled state of the pan.
   * 
   * @return if pan is enabled
   */
  public boolean isPanEnabled() {
    return mPanEnabled;
  }

  /**
   * Sets the enabled state of the pan.
   * 
   * @param enabled pan enabled
   */
  public void setPanEnabled(boolean enabled) {
    mPanEnabled = enabled;
  }

  /**
   * Sets the zoom rate.
   * 
   * @param rate the zoom rate
   */
  public void setZoomRate(float rate) {
    mZoomRate = rate;
  }

  /**
   * Returns the enabled state of the click.
   * 
   * @return if click is enabled
   */
  public boolean isClickEnabled() {
    return mClickEnabled;
  }

  /**
   * Sets the enabled state of the click.
   * 
   * @param enabled click enabled
   */
  public void setClickEnabled(boolean enabled) {
    mClickEnabled = enabled;
  }

  /**
   * Returns the selectable radius value around clickable points.
   * 
   * @return the selectable radius
   */
  public int getSelectableBuffer() {
    return selectableBuffer;
  }

  /**
   * Sets the selectable radius value around clickable points.
   * 
   * @param buffer the selectable radius
   */
  public void setSelectableBuffer(int buffer) {
    selectableBuffer = buffer;
  }

  /**
   * Returns the legend height.
   * 
   * @return the legend height
   */
  public int getLegendHeight() {
    return mLegendHeight;
  }

  /**
   * Sets the legend height, in pixels.
   * 
   * @param height the legend height
   */
  public void setLegendHeight(int height) {
    mLegendHeight = height;
  }

  /**
   * Returns the margin sizes. An array containing the margins in this order:
   * top, left, bottom, right
   * 
   * @return the margin sizes
   */
  public int[] getMargins() {
    return mMargins;
  }

  /**
   * Sets the margins, in pixels.
   * 
   * @param margins an array containing the margin size values, in this order:
   *          top, left, bottom, right
   */
  public void setMargins(int[] margins) {
    mMargins = margins;
  }

  /**
   * Returns if the chart is inside a scroll view and doesn't need to shrink.
   * 
   * @return if it is inside a scroll view
   */
  public boolean isInScroll() {
    return mInScroll;
  }

  /**
   * To be set if the chart is inside a scroll view and doesn't need to shrink
   * when not enough space.
   * 
   * @param inScroll if it is inside a scroll view
   */
  public void setInScroll(boolean inScroll) {
    mInScroll = inScroll;
  }

  /**
   * Returns the start angle for circular charts such as pie, doughnut. An angle
   * of 0 degrees correspond to the geometric angle of 0 degrees (3 o'clock on a
   * watch.)
   * 
   * @return the start angle
   */
  public float getStartAngle() {
    return mStartAngle;
  }

  /**
   * Sets the start angle for circular charts such as pie, doughnut, etc. An
   * angle of 0 degrees correspond to the geometric angle of 0 degrees (3
   * o'clock on a watch.)
   * 
   * @param startAngle the start angle
   */
  public void setStartAngle(float startAngle) {
    mStartAngle = startAngle;
  }

}
