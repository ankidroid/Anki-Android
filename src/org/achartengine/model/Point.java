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

/**
 * A class to encapsulate the definition of a point.
 */
public final class Point implements Serializable {
  /** The X axis coordinate value. */
  private float mX;
  /** The Y axis coordinate value. */
  private float mY;
  
  public Point() {
  }
  
  public Point(float x, float y) {
    mX = x;
    mY = y;
  }
  
  public float getX() {
    return mX;
  }

  public float getY() {
    return mY;
  }
  
  public void setX(float x) {
    mX = x;
  }
  
  public void setY(float y) {
    mY = y;
  }
}
