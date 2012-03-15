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

import java.util.ArrayList;
import java.util.List;

import org.achartengine.model.MultipleCategorySeries;
import org.achartengine.renderer.DefaultRenderer;
import org.achartengine.renderer.SimpleSeriesRenderer;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.RectF;

/**
 * The doughnut chart rendering class.
 */
public class DoughnutChart extends RoundChart {
  /** The series dataset. */
  private MultipleCategorySeries mDataset;
  /** A step variable to control the size of the legend shape. */
  private int mStep;

  /**
   * Builds a new doughnut chart instance.
   * 
   * @param dataset the series dataset
   * @param renderer the series renderer
   */
  public DoughnutChart(MultipleCategorySeries dataset, DefaultRenderer renderer) {
    super(null, renderer);
    mDataset = dataset;
  }

  /**
   * The graphical representation of the doughnut chart.
   * 
   * @param canvas the canvas to paint to
   * @param x the top left x value of the view to draw to
   * @param y the top left y value of the view to draw to
   * @param width the width of the view to draw to
   * @param height the height of the view to draw to
   * @param paint the paint
   */
  @Override
  public void draw(Canvas canvas, int x, int y, int width, int height, Paint paint) {
    paint.setAntiAlias(mRenderer.isAntialiasing());
    paint.setStyle(Style.FILL);
    paint.setTextSize(mRenderer.getLabelsTextSize());
    int legendSize = getLegendSize(mRenderer, height / 5, 0);
    int left = x;
    int top = y;
    int right = x + width;
    int cLength = mDataset.getCategoriesCount();
    String[] categories = new String[cLength];
    for (int category = 0; category < cLength; category++) {
      categories[category] = mDataset.getCategory(category);
    }
    if (mRenderer.isFitLegend()) {
      legendSize = drawLegend(canvas, mRenderer, categories, left, right, y, width, height,
          legendSize, paint, true);
    }

    int bottom = y + height - legendSize;
    drawBackground(mRenderer, canvas, x, y, width, height, paint, false, DefaultRenderer.NO_COLOR);
    mStep = SHAPE_WIDTH * 3 / 4;

    int mRadius = Math.min(Math.abs(right - left), Math.abs(bottom - top));
    double rCoef = 0.35 * mRenderer.getScale();
    double decCoef = 0.2 / cLength;
    int radius = (int) (mRadius * rCoef);
    if (mCenterX == NO_VALUE) {
      mCenterX = (left + right) / 2;
    }
    if (mCenterY == NO_VALUE) {
      mCenterY = (bottom + top) / 2;
    }
    float shortRadius = radius * 0.9f;
    float longRadius = radius * 1.1f;
    List<RectF> prevLabelsBounds = new ArrayList<RectF>();
    for (int category = 0; category < cLength; category++) {
      int sLength = mDataset.getItemCount(category);
      double total = 0;
      String[] titles = new String[sLength];
      for (int i = 0; i < sLength; i++) {
        total += mDataset.getValues(category)[i];
        titles[i] = mDataset.getTitles(category)[i];
      }
      float currentAngle = mRenderer.getStartAngle();
      RectF oval = new RectF(mCenterX - radius, mCenterY - radius, mCenterX + radius, mCenterY
          + radius);
      for (int i = 0; i < sLength; i++) {
        paint.setColor(mRenderer.getSeriesRendererAt(i).getColor());
        float value = (float) mDataset.getValues(category)[i];
        float angle = (float) (value / total * 360);
        canvas.drawArc(oval, currentAngle, angle, true, paint);
        drawLabel(canvas, mDataset.getTitles(category)[i], mRenderer, prevLabelsBounds, mCenterX,
            mCenterY, shortRadius, longRadius, currentAngle, angle, left, right,
            mRenderer.getLabelsColor(), paint);
        currentAngle += angle;
      }
      radius -= (int) mRadius * decCoef;
      shortRadius -= mRadius * decCoef - 2;
      if (mRenderer.getBackgroundColor() != 0) {
        paint.setColor(mRenderer.getBackgroundColor());
      } else {
        paint.setColor(Color.WHITE);
      }
      paint.setStyle(Style.FILL);
      oval = new RectF(mCenterX - radius, mCenterY - radius, mCenterX + radius, mCenterY + radius);
      canvas.drawArc(oval, 0, 360, true, paint);
      radius -= 1;
    }
    prevLabelsBounds.clear();
    drawLegend(canvas, mRenderer, categories, left, right, y, width, height, legendSize, paint,
        false);
    drawTitle(canvas, x, y, width, paint);
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
    mStep--;
    canvas.drawCircle(x + SHAPE_WIDTH - mStep, y, mStep, paint);
  }

}
