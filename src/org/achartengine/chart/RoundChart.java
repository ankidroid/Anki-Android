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
package org.achartengine.chart;

import org.achartengine.model.CategorySeries;
import org.achartengine.renderer.DefaultRenderer;
import org.achartengine.renderer.SimpleSeriesRenderer;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.Align;

/**
 * An abstract class to be extended by round like chart rendering classes.
 */
public abstract class RoundChart extends AbstractChart {
  /** The legend shape width. */
  protected static final int SHAPE_WIDTH = 10;
  /** The series dataset. */
  protected CategorySeries mDataset;
  /** The series renderer. */
  protected DefaultRenderer mRenderer;
  /** A no value constant. */
  protected static final int NO_VALUE = Integer.MAX_VALUE;
  /** The chart center X axis. */
  protected int mCenterX = NO_VALUE;
  /** The chart center y axis. */
  protected int mCenterY = NO_VALUE;

  /**
   * Round chart.
   * 
   * @param dataset the series dataset
   * @param renderer the series renderer
   */
  public RoundChart(CategorySeries dataset, DefaultRenderer renderer) {
    mDataset = dataset;
    mRenderer = renderer;
  }

  /**
   * The graphical representation of the round chart title.
   * 
   * @param canvas the canvas to paint to
   * @param x the top left x value of the view to draw to
   * @param y the top left y value of the view to draw to
   * @param width the width of the view to draw to
   * @param paint the paint
   */
  public void drawTitle(Canvas canvas, int x, int y, int width, Paint paint) {
    if (mRenderer.isShowLabels()) {
      paint.setColor(mRenderer.getLabelsColor());
      paint.setTextAlign(Align.CENTER);
      paint.setTextSize(mRenderer.getChartTitleTextSize());
      canvas.drawText(mRenderer.getChartTitle(), x + width / 2,
          y + mRenderer.getChartTitleTextSize(), paint);
    }
  }

  /**
   * Returns the legend shape width.
   * 
   * @param seriesIndex the series index
   * @return the legend shape width
   */
  public int getLegendShapeWidth(int seriesIndex) {
    return SHAPE_WIDTH;
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
    canvas.drawRect(x, y - SHAPE_WIDTH / 2, x + SHAPE_WIDTH, y + SHAPE_WIDTH / 2, paint);
  }

  /**
   * Returns the renderer.
   * 
   * @return the renderer
   */
  public DefaultRenderer getRenderer() {
    return mRenderer;
  }

  /**
   * Returns the center on X axis.
   * 
   * @return the center on X axis
   */
  public int getCenterX() {
    return mCenterX;
  }

  /**
   * Returns the center on Y axis.
   * 
   * @return the center on Y axis
   */
  public int getCenterY() {
    return mCenterY;
  }

  /**
   * Sets a new center on X axis.
   * 
   * @param centerX center on X axis
   */
  public void setCenterX(int centerX) {
    mCenterX = centerX;
  }

  /**
   * Sets a new center on Y axis.
   * 
   * @param centerY center on Y axis
   */
  public void setCenterY(int centerY) {
    mCenterY = centerY;
  }

}
