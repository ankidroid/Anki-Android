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
import org.achartengine.renderer.DefaultRenderer;

/**
 * The zoom tool.
 */
public class Zoom extends AbstractTool {
  /** A flag to be used to know if this is a zoom in or out. */
  private boolean mZoomIn;
  /** The zoom rate. */
  private float mZoomRate;
  /** The zoom listeners. */
  private List<ZoomListener> mZoomListeners = new ArrayList<ZoomListener>();
  /** Zoom limits reached on the X axis. */
  private boolean limitsReachedX = false;
  /** Zoom limits reached on the Y axis. */
  private boolean limitsReachedY = false;

  /**
   * Builds the zoom tool.
   * 
   * @param chart the chart
   * @param in zoom in or out
   * @param rate the zoom rate
   */
  public Zoom(AbstractChart chart, boolean in, float rate) {
    super(chart);
    mZoomIn = in;
    setZoomRate(rate);
  }

  /**
   * Sets the zoom rate.
   * 
   * @param rate
   */
  public void setZoomRate(float rate) {
    mZoomRate = rate;
  }

  /**
   * Apply the zoom.
   */
  public void apply() {
    if (mChart instanceof XYChart) {
      int scales = mRenderer.getScalesCount();
      for (int i = 0; i < scales; i++) {
        double[] range = getRange(i);
        checkRange(range, i);
        double[] limits = mRenderer.getZoomLimits();
        boolean limited = limits != null && limits.length == 4;

        double centerX = (range[0] + range[1]) / 2;
        double centerY = (range[2] + range[3]) / 2;
        double newWidth = range[1] - range[0];
        double newHeight = range[3] - range[2];
        if (mZoomIn) {
          if (mRenderer.isZoomXEnabled()) {
            limitsReachedX = false;
            newWidth /= mZoomRate;
          }
          if (mRenderer.isZoomYEnabled()) {
            limitsReachedY = false;
            newHeight /= mZoomRate;
          }
        } else {
          if (mRenderer.isZoomXEnabled()) {
            if (limitsReachedX) {
              newWidth *= mZoomRate;
            }
          }
          if (mRenderer.isZoomYEnabled()) {
            if (limitsReachedY) {
              newHeight *= mZoomRate;
            }
          }
        }
        if (mRenderer.isZoomXEnabled()) {
          double newXMin = centerX - newWidth / 2;
          double newXMax = centerX + newWidth / 2;
          if (!limited || limits[0] <= newXMin && limits[1] >= newXMax) {
            setXRange(newXMin, newXMax, i);
          } else {
            limitsReachedX = true;
          }
        }
        if (mRenderer.isZoomYEnabled()) {
          double newYMin = centerY - newHeight / 2;
          double newYMax = centerY + newHeight / 2;
          if (!limited || limits[2] <= newYMin && limits[3] >= newYMax) {
            setYRange(newYMin, newYMax, i);
          } else {
            limitsReachedY = true;
          }
        }
      }
    } else {
      DefaultRenderer renderer = ((RoundChart) mChart).getRenderer();
      if (mZoomIn) {
        renderer.setScale(renderer.getScale() * mZoomRate);
      } else {
        renderer.setScale(renderer.getScale() / mZoomRate);
      }
    }
    notifyZoomListeners(new ZoomEvent(mZoomIn, mZoomRate));
  }

  /**
   * Notify the zoom listeners about a zoom change.
   * 
   * @param e the zoom event
   */
  private synchronized void notifyZoomListeners(ZoomEvent e) {
    for (ZoomListener listener : mZoomListeners) {
      listener.zoomApplied(e);
    }
  }

  /**
   * Notify the zoom listeners about a zoom reset.
   */
  public synchronized void notifyZoomResetListeners() {
    for (ZoomListener listener : mZoomListeners) {
      listener.zoomReset();
    }
  }

  /**
   * Adds a new zoom listener.
   * 
   * @param listener zoom listener
   */
  public synchronized void addZoomListener(ZoomListener listener) {
    mZoomListeners.add(listener);
  }

  /**
   * Removes a zoom listener.
   * 
   * @param listener zoom listener
   */
  public synchronized void removeZoomListener(ZoomListener listener) {
    mZoomListeners.add(listener);
  }

}
