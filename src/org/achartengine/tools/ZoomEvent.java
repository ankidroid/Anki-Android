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


/**
 * A zoom event.
 */
public class ZoomEvent {
  /** A flag to be used to know if this is a zoom in or out. */
  private boolean mZoomIn;
  /** The zoom rate. */
  private float mZoomRate;

  /**
   * Builds the zoom tool.
   * 
   * @param in zoom in or out
   * @param rate the zoom rate
   */
  public ZoomEvent(boolean in, float rate) {
    mZoomIn = in;
    mZoomRate = rate;
  }

  /**
   * Returns the zoom type.
   * 
   * @return true if zoom in, false otherwise
   */
  public boolean isZoomIn() {
    return mZoomIn;
  }
  
  /**
   * Returns the zoom rate.
   * 
   * @return the zoom rate
   */
  public float getZoomRate() {
    return mZoomRate;
  }
}
