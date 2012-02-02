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

import org.achartengine.model.CategorySeries;
import org.achartengine.renderer.DefaultRenderer;
import org.achartengine.renderer.DialRenderer;
import org.achartengine.renderer.DialRenderer.Type;
import org.achartengine.util.MathHelper;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.Paint.Style;

/**
 * The dial chart rendering class.
 */
public class DialChart extends RoundChart {
  /** The radius of the needle. */
  private static final int NEEDLE_RADIUS = 10;
  /** The series renderer. */
  private DialRenderer mRenderer;

  /**
   * Builds a new dial chart instance.
   * 
   * @param dataset the series dataset
   * @param renderer the dial renderer
   */
  public DialChart(CategorySeries dataset, DialRenderer renderer) {
    super(dataset, renderer);
    mRenderer = renderer;
  }

  /**
   * The graphical representation of the dial chart.
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

    int sLength = mDataset.getItemCount();
    String[] titles = new String[sLength];
    for (int i = 0; i < sLength; i++) {
      titles[i] = mDataset.getCategory(i);
    }

    if (mRenderer.isFitLegend()) {
      legendSize = drawLegend(canvas, mRenderer, titles, left, right, y, width, height, legendSize,
          paint, true);
    }
    int bottom = y + height - legendSize;
    drawBackground(mRenderer, canvas, x, y, width, height, paint, false, DefaultRenderer.NO_COLOR);

    int mRadius = Math.min(Math.abs(right - left), Math.abs(bottom - top));
    int radius = (int) (mRadius * 0.35 * mRenderer.getScale());
    if (mCenterX == NO_VALUE) {
      mCenterX = (left + right) / 2;
    }
    if (mCenterY == NO_VALUE) {
      mCenterY = (bottom + top) / 2;
    }
    float shortRadius = radius * 0.9f;
    float longRadius = radius * 1.1f;
    double min = mRenderer.getMinValue();
    double max = mRenderer.getMaxValue();
    double angleMin = mRenderer.getAngleMin();
    double angleMax = mRenderer.getAngleMax();
    if (!mRenderer.isMinValueSet() || !mRenderer.isMaxValueSet()) {
      int count = mRenderer.getSeriesRendererCount();
      for (int i = 0; i < count; i++) {
        double value = mDataset.getValue(i);
        if (!mRenderer.isMinValueSet()) {
          min = Math.min(min, value);
        }
        if (!mRenderer.isMaxValueSet()) {
          max = Math.max(max, value);
        }
      }
    }
    if (min == max) {
      min = min * 0.5;
      max = max * 1.5;
    }

    paint.setColor(mRenderer.getLabelsColor());
    double minorTicks = mRenderer.getMinorTicksSpacing();
    double majorTicks = mRenderer.getMajorTicksSpacing();
    if (minorTicks == MathHelper.NULL_VALUE) {
      minorTicks = (max - min) / 30;
    }
    if (majorTicks == MathHelper.NULL_VALUE) {
      majorTicks = (max - min) / 10;
    }
    drawTicks(canvas, min, max, angleMin, angleMax, mCenterX, mCenterY, longRadius, radius,
        minorTicks, paint, false);
    drawTicks(canvas, min, max, angleMin, angleMax, mCenterX, mCenterY, longRadius, shortRadius,
        majorTicks, paint, true);

    int count = mRenderer.getSeriesRendererCount();
    for (int i = 0; i < count; i++) {
      double angle = getAngleForValue(mDataset.getValue(i), angleMin, angleMax, min, max);
      paint.setColor(mRenderer.getSeriesRendererAt(i).getColor());
      boolean type = mRenderer.getVisualTypeForIndex(i) == Type.ARROW;
      drawNeedle(canvas, angle, mCenterX, mCenterY, shortRadius, type, paint);
    }
    drawLegend(canvas, mRenderer, titles, left, right, y, width, height, legendSize, paint, false);
    drawTitle(canvas, x, y, width, paint);
  }

  /**
   * Returns the angle for a specific chart value.
   * 
   * @param value the chart value
   * @param minAngle the minimum chart angle value
   * @param maxAngle the maximum chart angle value
   * @param min the minimum chart value
   * @param max the maximum chart value
   * @return the angle
   */
  private double getAngleForValue(double value, double minAngle, double maxAngle, double min,
      double max) {
    double angleDiff = maxAngle - minAngle;
    double diff = max - min;
    return Math.toRadians(minAngle + (value - min) * angleDiff / diff);
  }

  /**
   * Draws the chart tick lines.
   * 
   * @param canvas the canvas
   * @param min the minimum chart value
   * @param max the maximum chart value
   * @param minAngle the minimum chart angle value
   * @param maxAngle the maximum chart angle value
   * @param centerX the center x value
   * @param centerY the center y value
   * @param longRadius the long radius
   * @param shortRadius the short radius
   * @param ticks the tick spacing
   * @param paint the paint settings
   * @param labels paint the labels
   * @return the angle
   */
  private void drawTicks(Canvas canvas, double min, double max, double minAngle, double maxAngle,
      int centerX, int centerY, double longRadius, double shortRadius, double ticks, Paint paint,
      boolean labels) {
    for (double i = min; i <= max; i += ticks) {
      double angle = getAngleForValue(i, minAngle, maxAngle, min, max);
      double sinValue = Math.sin(angle);
      double cosValue = Math.cos(angle);
      int x1 = Math.round(centerX + (float) (shortRadius * sinValue));
      int y1 = Math.round(centerY + (float) (shortRadius * cosValue));
      int x2 = Math.round(centerX + (float) (longRadius * sinValue));
      int y2 = Math.round(centerY + (float) (longRadius * cosValue));
      canvas.drawLine(x1, y1, x2, y2, paint);
      if (labels) {
        paint.setTextAlign(Align.LEFT);
        if (x1 <= x2) {
          paint.setTextAlign(Align.RIGHT);
        }
        String text = i + "";
        if (Math.round(i) == (long) i) {
          text = (long) i + "";
        }
        canvas.drawText(text, x1, y1, paint);
      }
    }
  }

  /**
   * Returns the angle for a specific chart value.
   * 
   * @param canvas the canvas
   * @param angle the needle angle value
   * @param centerX the center x value
   * @param centerY the center y value
   * @param radius the radius
   * @param arrow if a needle or an arrow to be painted
   * @param paint the paint settings
   * @return the angle
   */
  private void drawNeedle(Canvas canvas, double angle, int centerX, int centerY, double radius,
      boolean arrow, Paint paint) {
    double diff = Math.toRadians(90);
    int needleSinValue = (int) (NEEDLE_RADIUS * Math.sin(angle - diff));
    int needleCosValue = (int) (NEEDLE_RADIUS * Math.cos(angle - diff));
    int needleX = (int) (radius * Math.sin(angle));
    int needleY = (int) (radius * Math.cos(angle));
    int needleCenterX = centerX + needleX;
    int needleCenterY = centerY + needleY;
    float[] points;
    if (arrow) {
      int arrowBaseX = centerX + (int) (radius * 0.85 * Math.sin(angle));
      int arrowBaseY = centerY + (int) (radius * 0.85 * Math.cos(angle));
      points = new float[] { arrowBaseX - needleSinValue, arrowBaseY - needleCosValue,
          needleCenterX, needleCenterY, arrowBaseX + needleSinValue, arrowBaseY + needleCosValue };
      float width = paint.getStrokeWidth();
      paint.setStrokeWidth(5);
      canvas.drawLine(centerX, centerY, needleCenterX, needleCenterY, paint);
      paint.setStrokeWidth(width);
    } else {
      points = new float[] { centerX - needleSinValue, centerY - needleCosValue, needleCenterX,
          needleCenterY, centerX + needleSinValue, centerY + needleCosValue };
    }
    drawPath(canvas, points, paint, true);
  }

}
