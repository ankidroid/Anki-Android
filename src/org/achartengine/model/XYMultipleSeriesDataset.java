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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * A series that includes 0 to many XYSeries.
 */
public class XYMultipleSeriesDataset implements Serializable {
  /** The included series. */
  private List<XYSeries> mSeries = new ArrayList<XYSeries>();

  /**
   * Adds a new XY series to the list.
   * 
   * @param series the XY series to ass
   */
  public synchronized void addSeries(XYSeries series) {
    mSeries.add(series);
  }

  /**
   * Adds a new XY series to the list.
   * 
   * @param index the index in the series list
   * @param series the XY series to ass
   */
  public synchronized void addSeries(int index, XYSeries series) {
    mSeries.add(index, series);
  }

  /**
   * Removes the XY series from the list.
   * 
   * @param index the index in the series list of the series to remove
   */
  public synchronized void removeSeries(int index) {
    mSeries.remove(index);
  }

  /**
   * Removes the XY series from the list.
   * 
   * @param series the XY series to be removed
   */
  public synchronized void removeSeries(XYSeries series) {
    mSeries.remove(series);
  }

  /**
   * Returns the XY series at the specified index.
   * 
   * @param index the index
   * @return the XY series at the index
   */
  public synchronized XYSeries getSeriesAt(int index) {
    return mSeries.get(index);
  }

  /**
   * Returns the XY series count.
   * 
   * @return the XY series count
   */
  public synchronized int getSeriesCount() {
    return mSeries.size();
  }

  /**
   * Returns an array of the XY series.
   * 
   * @return the XY series array
   */
  public synchronized XYSeries[] getSeries() {
    return mSeries.toArray(new XYSeries[0]);
  }

}
