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
package org.achartengine.tools;

import java.util.ArrayList;
import java.util.List;

import org.achartengine.chart.AbstractChart;
import org.achartengine.chart.RoundChart;
import org.achartengine.chart.XYChart;

/**
 * The pan tool.
 */
public class Pan extends AbstractTool {
  /** The pan listeners. */
  private List<PanListener> mPanListeners = new ArrayList<PanListener>();
  /** Pan limits reached on the X axis. */
  private boolean limitsReachedX = false;
  /** Pan limits reached on the X axis. */
  private boolean limitsReachedY = false;

  /**
   * Builds and instance of the pan tool.
   * 
   * @param chart the XY chart
   */
  public Pan(AbstractChart chart) {
    super(chart);
  }

  /**
   * Apply the tool.
   * 
   * @param oldX the previous location on X axis
   * @param oldY the previous location on Y axis
   * @param newX the current location on X axis
   * @param newY the current location on the Y axis
   */
  public void apply(float oldX, float oldY, float newX, float newY) {
    boolean notLimitedUp = true;
    boolean notLimitedBottom = true;
    boolean notLimitedLeft = true;
    boolean notLimitedRight = true;
    if (mChart instanceof XYChart) {
      int scales = mRenderer.getScalesCount();
      double[] limits = mRenderer.getPanLimits();
      boolean limited = limits != null && limits.length == 4;
      XYChart chart = (XYChart) mChart;
      for (int i = 0; i < scales; i++) {
        double[] range = getRange(i);
        double[] calcRange = chart.getCalcRange(i);
        if (limitsReachedX
            && limitsReachedY
            && (range[0] == range[1] && calcRange[0] == calcRange[1] || range[2] == range[3]
                && calcRange[2] == calcRange[3])) {
          return;
        }
        checkRange(range, i);

        double[] realPoint = chart.toRealPoint(oldX, oldY, i);
        double[] realPoint2 = chart.toRealPoint(newX, newY, i);
        double deltaX = realPoint[0] - realPoint2[0];
        double deltaY = realPoint[1] - realPoint2[1];
        double ratio = getAxisRatio(range);
        if (chart.isVertical(mRenderer)) {
          double newDeltaX = -deltaY * ratio;
          double newDeltaY = deltaX / ratio;
          deltaX = newDeltaX;
          deltaY = newDeltaY;
        }
        if (mRenderer.isPanXEnabled()) {
          if (limits != null) {
            if (notLimitedLeft) {
              notLimitedLeft = limits[0] <= range[0] + deltaX;
            }
            if (notLimitedRight) {
              notLimitedRight = limits[1] >= range[1] + deltaX;
            }
          }
          if (!limited || (notLimitedLeft && notLimitedRight)) {
            setXRange(range[0] + deltaX, range[1] + deltaX, i);
            limitsReachedX = false;
          } else {
            limitsReachedX = true;
          }
        }
        if (mRenderer.isPanYEnabled()) {
          if (notLimitedBottom && limits != null) {
            notLimitedBottom = limits[2] <= range[2] - deltaY;
          }
          if (notLimitedUp && limits != null) {
            notLimitedUp = limits[3] >= range[3] - deltaY;
          }
          if (limited && (!notLimitedBottom && !notLimitedUp)) {
            limitsReachedY = true;
          } else {
            if (!notLimitedUp && deltaY < 0) {
              setYRange(range[2] + deltaY, range[3] + deltaY, i);
              notLimitedUp = true;
            } else if (!notLimitedBottom && deltaY > 0) {
              setYRange(range[2] + deltaY, range[3] + deltaY, i);
              notLimitedBottom = true;
            } else if (notLimitedBottom && notLimitedUp) {
              setYRange(range[2] + deltaY, range[3] + deltaY, i);
            }
            limitsReachedY = false;
          }
        }
      }
    } else {
      RoundChart chart = (RoundChart) mChart;
      chart.setCenterX(chart.getCenterX() + (int) (newX - oldX));
      chart.setCenterY(chart.getCenterY() + (int) (newY - oldY));
    }
    notifyPanListeners();
  }

  /**
   * Return the X / Y axis range ratio.
   * 
   * @param range the axis range
   * @return the ratio
   */
  private double getAxisRatio(double[] range) {
    return Math.abs(range[1] - range[0]) / Math.abs(range[3] - range[2]);
  }

  /**
   * Notify the pan listeners about a pan.
   */
  private synchronized void notifyPanListeners() {
    for (PanListener listener : mPanListeners) {
      listener.panApplied();
    }
  }

  /**
   * Adds a new pan listener.
   * 
   * @param listener pan listener
   */
  public synchronized void addPanListener(PanListener listener) {
    mPanListeners.add(listener);
  }

  /**
   * Removes a pan listener.
   * 
   * @param listener pan listener
   */
  public synchronized void removePanListener(PanListener listener) {
    mPanListeners.add(listener);
  }

}
