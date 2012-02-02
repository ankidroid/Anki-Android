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
package org.achartengine;

import org.achartengine.tools.PanListener;
import org.achartengine.tools.ZoomListener;

import android.view.MotionEvent;

/**
 * The interface to be implemented by the touch handlers.
 */
public interface ITouchHandler {
  /**
   * Handles the touch event.
   * 
   * @param event the touch event
   * @return true if the event was handled
   */
  boolean handleTouch(MotionEvent event);
  
  /**
   * Adds a new zoom listener.
   * 
   * @param listener zoom listener
   */
  void addZoomListener(ZoomListener listener);

  /**
   * Removes a zoom listener.
   * 
   * @param listener zoom listener
   */
  void removeZoomListener(ZoomListener listener);
  
  /**
   * Adds a new pan listener.
   * 
   * @param listener pan listener
   */
  void addPanListener(PanListener listener);

  /**
   * Removes a pan listener.
   * 
   * @param listener pan listener
   */
  void removePanListener(PanListener listener);

}