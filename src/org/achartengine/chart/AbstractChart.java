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

import java.io.Serializable;
import java.util.List;

import org.achartengine.model.Point;
import org.achartengine.model.SeriesSelection;
import org.achartengine.renderer.DefaultRenderer;
import org.achartengine.renderer.SimpleSeriesRenderer;
import org.achartengine.renderer.XYMultipleSeriesRenderer;
import org.achartengine.renderer.XYMultipleSeriesRenderer.Orientation;
import org.achartengine.util.MathHelper;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.Paint.Style;
import android.graphics.Path;
import android.graphics.RectF;

/**
 * An abstract class to be implemented by the chart rendering classes.
 */
public abstract class AbstractChart implements Serializable {
  /**
   * The graphical representation of the chart.
   * 
   * @param canvas the canvas to paint to
   * @param x the top left x value of the view to draw to
   * @param y the top left y value of the view to draw to
   * @param width the width of the view to draw to
   * @param height the height of the view to draw to
   * @param paint the paint
   */
  public abstract void draw(Canvas canvas, int x, int y, int width, int height, Paint paint);

  /**
   * Draws the chart background.
   * 
   * @param renderer the chart renderer
   * @param canvas the canvas to paint to
   * @param x the top left x value of the view to draw to
   * @param y the top left y value of the view to draw to
   * @param width the width of the view to draw to
   * @param height the height of the view to draw to
   * @param paint the paint used for drawing
   * @param newColor if a new color is to be used
   * @param color the color to be used
   */
  protected void drawBackground(DefaultRenderer renderer, Canvas canvas, int x, int y, int width,
      int height, Paint paint, boolean newColor, int color) {
    if (renderer.isApplyBackgroundColor() || newColor) {
      if (newColor) {
        paint.setColor(color);
      } else {
        paint.setColor(renderer.getBackgroundColor());
      }
      paint.setStyle(Style.FILL);
      canvas.drawRect(x, y, x + width, y + height, paint);
    }
  }

  /**
   * Draws the chart legend.
   * 
   * @param canvas the canvas to paint to
   * @param renderer the series renderer
   * @param titles the titles to go to the legend
   * @param left the left X value of the area to draw to
   * @param right the right X value of the area to draw to
   * @param y the y value of the area to draw to
   * @param width the width of the area to draw to
   * @param height the height of the area to draw to
   * @param legendSize the legend size
   * @param paint the paint to be used for drawing
   * @param calculate if only calculating the legend size
   * 
   * @return the legend height
   */
  protected int drawLegend(Canvas canvas, DefaultRenderer renderer, String[] titles, int left,
      int right, int y, int width, int height, int legendSize, Paint paint, boolean calculate) {
    float size = 32;
    if (renderer.isShowLegend()) {
      float currentX = left;
      float currentY = y + height - legendSize + size;
      paint.setTextAlign(Align.LEFT);
      paint.setTextSize(renderer.getLegendTextSize());
      int sLength = Math.min(titles.length, renderer.getSeriesRendererCount());
      for (int i = 0; i < sLength; i++) {
        final float lineSize = getLegendShapeWidth(i);
        String text = titles[i];
        if (titles.length == renderer.getSeriesRendererCount()) {
          paint.setColor(renderer.getSeriesRendererAt(i).getColor());
        } else {
          paint.setColor(Color.LTGRAY);
        }
        float[] widths = new float[text.length()];
        paint.getTextWidths(text, widths);
        float sum = 0;
        for (float value : widths) {
          sum += value;
        }
        float extraSize = lineSize + 10 + sum;
        float currentWidth = currentX + extraSize;

        if (i > 0 && getExceed(currentWidth, renderer, right, width)) {
          currentX = left;
          currentY += renderer.getLegendTextSize();
          size += renderer.getLegendTextSize();
          currentWidth = currentX + extraSize;
        }
        if (getExceed(currentWidth, renderer, right, width)) {
          float maxWidth = right - currentX - lineSize - 10;
          if (isVertical(renderer)) {
            maxWidth = width - currentX - lineSize - 10;
          }
          int nr = paint.breakText(text, true, maxWidth, widths);
          text = text.substring(0, nr) + "...";
        }
        if (!calculate) {
          drawLegendShape(canvas, renderer.getSeriesRendererAt(i), currentX, currentY, i, paint);
          canvas.drawText(text, currentX + lineSize + 5, currentY + 5, paint);
        }
        currentX += extraSize;
      }
    }
    return Math.round(size + renderer.getLegendTextSize());
  }

  /**
   * Calculates if the current width exceeds the total width.
   * 
   * @param currentWidth the current width
   * @param renderer the renderer
   * @param right the right side pixel value
   * @param width the total width
   * @return if the current width exceeds the total width
   */
  protected boolean getExceed(float currentWidth, DefaultRenderer renderer, int right, int width) {
    boolean exceed = currentWidth > right;
    if (isVertical(renderer)) {
      exceed = currentWidth > width;
    }
    return exceed;
  }

  /**
   * Checks if the current chart is rendered as vertical.
   * 
   * @param renderer the renderer
   * @return if the chart is rendered as a vertical one
   */
  public boolean isVertical(DefaultRenderer renderer) {
    return renderer instanceof XYMultipleSeriesRenderer
        && ((XYMultipleSeriesRenderer) renderer).getOrientation() == Orientation.VERTICAL;
  }

  private static float[] calculateDrawPoints(float p1x, float p1y, float p2x, float p2y,
      int screenHeight, int screenWidth) {
    float drawP1x;
    float drawP1y;
    float drawP2x;
    float drawP2y;

    if (p1y > screenHeight) {
      // Intersection with the top of the screen
      float m = (p2y - p1y) / (p2x - p1x);
      drawP1x = (screenHeight - p1y + m * p1x) / m;
      drawP1y = screenHeight;

      if (drawP1x < 0) {
        // If Intersection is left of the screen we calculate the intersection
        // with the left border
        drawP1x = 0;
        drawP1y = p1y - m * p1x;
      } else if (drawP1x > screenWidth) {
        // If Intersection is right of the screen we calculate the intersection
        // with the right border
        drawP1x = screenWidth;
        drawP1y = m * screenWidth + p1y - m * p1x;
      }
    } else if (p1y < 0) {
      float m = (p2y - p1y) / (p2x - p1x);
      drawP1x = (-p1y + m * p1x) / m;
      drawP1y = 0;
      if (drawP1x < 0) {
        drawP1x = 0;
        drawP1y = p1y - m * p1x;
      } else if (drawP1x > screenWidth) {
        drawP1x = screenWidth;
        drawP1y = m * screenWidth + p1y - m * p1x;
      }
    } else {
      // If the point is in the screen use it
      drawP1x = p1x;
      drawP1y = p1y;
    }

    if (p2y > screenHeight) {
      float m = (p2y - p1y) / (p2x - p1x);
      drawP2x = (screenHeight - p1y + m * p1x) / m;
      drawP2y = screenHeight;
      if (drawP2x < 0) {
        drawP2x = 0;
        drawP2y = p1y - m * p1x;
      } else if (drawP2x > screenWidth) {
        drawP2x = screenWidth;
        drawP2y = m * screenWidth + p1y - m * p1x;
      }
    } else if (p2y < 0) {
      float m = (p2y - p1y) / (p2x - p1x);
      drawP2x = (-p1y + m * p1x) / m;
      drawP2y = 0;
      if (drawP2x < 0) {
        drawP2x = 0;
        drawP2y = p1y - m * p1x;
      } else if (drawP2x > screenWidth) {
        drawP2x = screenWidth;
        drawP2y = m * screenWidth + p1y - m * p1x;
      }
    } else {
      // If the point is in the screen use it
      drawP2x = p2x;
      drawP2y = p2y;
    }

    return new float[] { drawP1x, drawP1y, drawP2x, drawP2y };
  }

  /**
   * The graphical representation of a path.
   * 
   * @param canvas the canvas to paint to
   * @param points the points that are contained in the path to paint
   * @param paint the paint to be used for painting
   * @param circular if the path ends with the start point
   */
  protected void drawPath(Canvas canvas, float[] points, Paint paint, boolean circular) {
    Path path = new Path();
    int height = canvas.getHeight();
    int width = canvas.getWidth();

    float[] tempDrawPoints;
    if (points.length < 4) {
      return;
    }
    tempDrawPoints = calculateDrawPoints(points[0], points[1], points[2], points[3], height, width);
    path.moveTo(tempDrawPoints[0], tempDrawPoints[1]);
    path.lineTo(tempDrawPoints[2], tempDrawPoints[3]);

    for (int i = 4; i < points.length; i += 2) {
      if ((points[i - 1] < 0 && points[i + 1] < 0)
          || (points[i - 1] > height && points[i + 1] > height)) {
        continue;
      }
      tempDrawPoints = calculateDrawPoints(points[i - 2], points[i - 1], points[i], points[i + 1],
          height, width);
      if (!circular) {
        path.moveTo(tempDrawPoints[0], tempDrawPoints[1]);
      }
      path.lineTo(tempDrawPoints[2], tempDrawPoints[3]);
    }
    if (circular) {
      path.lineTo(points[0], points[1]);
    }
    canvas.drawPath(path, paint);
  }

  /**
   * Returns the legend shape width.
   * 
   * @param seriesIndex the series index
   * @return the legend shape width
   */
  public abstract int getLegendShapeWidth(int seriesIndex);

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
  public abstract void drawLegendShape(Canvas canvas, SimpleSeriesRenderer renderer, float x,
      float y, int seriesIndex, Paint paint);

  /**
   * Calculates the best text to fit into the available space.
   * 
   * @param text the entire text
   * @param width the width to fit the text into
   * @param paint the paint
   * @return the text to fit into the space
   */
  private String getFitText(String text, float width, Paint paint) {
    String newText = text;
    int length = text.length();
    int diff = 0;
    while (paint.measureText(newText) > width && diff < length) {
      diff++;
      newText = text.substring(0, length - diff) + "...";
    }
    if (diff == length) {
      newText = "...";
    }
    return newText;
  }

  /**
   * Calculates the current legend size.
   * 
   * @param renderer the renderer
   * @param defaultHeight the default height
   * @param extraHeight the added extra height
   * @return the legend size
   */
  protected int getLegendSize(DefaultRenderer renderer, int defaultHeight, float extraHeight) {
    int legendSize = renderer.getLegendHeight();
    if (renderer.isShowLegend() && legendSize == 0) {
      legendSize = defaultHeight;
    }
    if (!renderer.isShowLegend() && renderer.isShowLabels()) {
      legendSize = (int) (renderer.getLabelsTextSize() * 4 / 3 + extraHeight);
    }
    return legendSize;
  }

  /**
   * Draws a text label.
   * 
   * @param canvas the canvas
   * @param labelText the label text
   * @param renderer the renderer
   * @param prevLabelsBounds the previous rendered label bounds
   * @param centerX the round chart center on X axis
   * @param centerY the round chart center on Y axis
   * @param shortRadius the short radius for the round chart
   * @param longRadius the long radius for the round chart
   * @param currentAngle the current angle
   * @param angle the label extra angle
   * @param left the left side
   * @param right the right side
   * @param color the label color
   * @param paint the paint
   */
  protected void drawLabel(Canvas canvas, String labelText, DefaultRenderer renderer,
      List<RectF> prevLabelsBounds, int centerX, int centerY, float shortRadius, float longRadius,
      float currentAngle, float angle, int left, int right, int color, Paint paint) {
    if (renderer.isShowLabels()) {
      paint.setColor(color);
      double rAngle = Math.toRadians(90 - (currentAngle + angle / 2));
      double sinValue = Math.sin(rAngle);
      double cosValue = Math.cos(rAngle);
      int x1 = Math.round(centerX + (float) (shortRadius * sinValue));
      int y1 = Math.round(centerY + (float) (shortRadius * cosValue));
      int x2 = Math.round(centerX + (float) (longRadius * sinValue));
      int y2 = Math.round(centerY + (float) (longRadius * cosValue));

      float size = renderer.getLabelsTextSize();
      float extra = Math.max(size / 2, 10);
      paint.setTextAlign(Align.LEFT);
      if (x1 > x2) {
        extra = -extra;
        paint.setTextAlign(Align.RIGHT);
      }
      float xLabel = x2 + extra;
      float yLabel = y2;
      float width = right - xLabel;
      if (x1 > x2) {
        width = xLabel - left;
      }
      labelText = getFitText(labelText, width, paint);
      float widthLabel = paint.measureText(labelText);
      boolean okBounds = false;
      while (!okBounds) {
        boolean intersects = false;
        int length = prevLabelsBounds.size();
        for (int j = 0; j < length && !intersects; j++) {
          RectF prevLabelBounds = prevLabelsBounds.get(j);
          if (prevLabelBounds.intersects(xLabel, yLabel, xLabel + widthLabel, yLabel + size)) {
            intersects = true;
            yLabel = Math.max(yLabel, prevLabelBounds.bottom);
          }
        }
        okBounds = !intersects;
      }

      y2 = (int) (yLabel - size / 2);
      canvas.drawLine(x1, y1, x2, y2, paint);
      canvas.drawLine(x2, y2, x2 + extra, y2, paint);
      canvas.drawText(labelText, xLabel, yLabel, paint);
      prevLabelsBounds.add(new RectF(xLabel, yLabel, xLabel + widthLabel, yLabel + size));
    }
  }

  public boolean isNullValue(double value) {
    return Double.isNaN(value) || Double.isInfinite(value) || value == MathHelper.NULL_VALUE;
  }

  /**
   * Given screen coordinates, returns the series and point indexes of a chart
   * element. If there is no chart element (line, point, bar, etc) at those
   * coordinates, null is returned.
   * 
   * @param screenPoint
   * @return
   */
  public SeriesSelection getSeriesAndPointForScreenCoordinate(Point screenPoint) {
    return null;
  }

}
