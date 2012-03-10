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

import android.graphics.RectF;

public class ClickableArea {
  private RectF rect;
  private double x;
  private double y;

  public ClickableArea(RectF rect, double x, double y) {
    super();
    this.rect = rect;
    this.x = x;
    this.y = y;
  }

  public RectF getRect() {
    return rect;
  }

  public double getX() {
    return x;
  }

  public double getY() {
    return y;
  }

}
