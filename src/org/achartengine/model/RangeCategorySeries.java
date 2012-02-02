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

import java.util.ArrayList;
import java.util.List;
/**
 * A series for the range category charts like the range bar.
 */
public class RangeCategorySeries extends CategorySeries {
  /** The series values. */
  private List<Double> mMaxValues = new ArrayList<Double>();
  /**
   * Builds a new category series.
   * 
   * @param title the series title
   */
  public RangeCategorySeries(String title) {
    super(title);
  }
  /**
   * Adds new values to the series
   * 
   * @param minValue the new minimum value
   * @param maxValue the new maximum value
   */
  public synchronized void add(double minValue, double maxValue) {
    super.add(minValue);
    mMaxValues.add(maxValue);
  }

  /**
   * Adds new values to the series.
   * 
   * @param category the category
   * @param minValue the new minimum value
   * @param maxValue the new maximum value
   */
  public synchronized void add(String category, double minValue, double maxValue) {
    super.add(category, minValue);
    mMaxValues.add(maxValue);
  }

  /**
   * Removes existing values from the series.
   * 
   * @param index the index in the series of the values to remove
   */
  public synchronized void remove(int index) {
    super.remove(index);
    mMaxValues.remove(index);
  }

  /**
   * Removes all the existing values from the series.
   */
  public synchronized void clear() {
    super.clear();
    mMaxValues.clear();
  }

  /**
   * Returns the minimum value at the specified index.
   * 
   * @param index the index
   * @return the minimum value at the index
   */
  public double getMinimumValue(int index) {
    return getValue(index);
  }

  /**
   * Returns the maximum value at the specified index.
   * 
   * @param index the index
   * @return the maximum value at the index
   */
  public double getMaximumValue(int index) {
    return mMaxValues.get(index);
  }

  /**
   * Transforms the range category series to an XY series.
   * 
   * @return the XY series
   */
  public XYSeries toXYSeries() {
    XYSeries xySeries = new XYSeries(getTitle());
    int length = getItemCount();
    for (int k = 0; k < length; k++) {
      xySeries.add(k + 1, getMinimumValue(k));
      // the new fast XYSeries implementation doesn't allow 2 values at the same X,
      // so I had to do a hack until I find a better solution
      xySeries.add(k + 1.000001, getMaximumValue(k));
    }
    return xySeries;
  }
}
