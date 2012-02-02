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

import org.achartengine.chart.AbstractChart;
import org.achartengine.chart.RoundChart;
import org.achartengine.chart.XYChart;
import org.achartengine.model.XYSeries;
import org.achartengine.renderer.DefaultRenderer;
import org.achartengine.util.MathHelper;

public class FitZoom extends AbstractTool {
  /**
   * Builds an instance of the fit zoom tool.
   * 
   * @param chart the XY chart
   */
  public FitZoom(AbstractChart chart) {
    super(chart);
  }

  /**
   * Apply the tool.
   */
  public void apply() {
    if (mChart instanceof XYChart) {
      if (((XYChart) mChart).getDataset() == null) {
        return;
      }
      int scales = mRenderer.getScalesCount();
      if (mRenderer.isInitialRangeSet()) {
        for (int i = 0; i < scales; i++) {
          if (mRenderer.isInitialRangeSet(i)) {
            mRenderer.setRange(mRenderer.getInitialRange(i), i);
          }
        }
      } else {
        XYSeries[] series = ((XYChart) mChart).getDataset().getSeries();
        double[] range = null;
        int length = series.length;
        if (length > 0) {
          for (int i = 0; i < scales; i++) {
            range = new double[] { MathHelper.NULL_VALUE, -MathHelper.NULL_VALUE,
                MathHelper.NULL_VALUE, -MathHelper.NULL_VALUE };
            for (int j = 0; j < length; j++) {
              if (i == series[j].getScaleNumber()) {
                range[0] = Math.min(range[0], series[j].getMinX());
                range[1] = Math.max(range[1], series[j].getMaxX());
                range[2] = Math.min(range[2], series[j].getMinY());
                range[3] = Math.max(range[3], series[j].getMaxY());
              }
            }
            double marginX = Math.abs(range[1] - range[0]) / 40;
            double marginY = Math.abs(range[3] - range[2]) / 40;
            mRenderer.setRange(new double[] { range[0] - marginX, range[1] + marginX,
                range[2] - marginY, range[3] + marginY }, i);
          }
        }
      }
    } else {
      DefaultRenderer renderer = ((RoundChart) mChart).getRenderer();
      renderer.setScale(renderer.getOriginalScale());
    }
  }
}
