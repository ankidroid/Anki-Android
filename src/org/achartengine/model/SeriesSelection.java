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
package org.achartengine.model;

public class SeriesSelection {
  private int mSeriesIndex;

  private int mPointIndex;

  private double mXValue;

  private double mValue;

  public SeriesSelection(int seriesIndex, int pointIndex, double xValue, double value) {
    mSeriesIndex = seriesIndex;
    mPointIndex = pointIndex;
    mXValue = xValue;
    mValue = value;
  }

  public int getSeriesIndex() {
    return mSeriesIndex;
  }

  public int getPointIndex() {
    return mPointIndex;
  }

  public double getXValue() {
    return mXValue;
  }

  public double getValue() {
    return mValue;
  }
}