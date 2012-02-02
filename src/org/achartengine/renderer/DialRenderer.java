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
package org.achartengine.renderer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.achartengine.util.MathHelper;

/**
 * Dial chart renderer.
 */
public class DialRenderer extends DefaultRenderer {
  /** The start angle in the dial range. */
  private double mAngleMin = 330;
  /** The end angle in the dial range. */
  private double mAngleMax = 30;
  /** The start value in dial range. */
  private double mMinValue = MathHelper.NULL_VALUE;
  /** The end value in dial range. */
  private double mMaxValue = -MathHelper.NULL_VALUE;
  /** The spacing for the minor ticks. */
  private double mMinorTickSpacing = MathHelper.NULL_VALUE;
  /** The spacing for the major ticks. */
  private double mMajorTickSpacing = MathHelper.NULL_VALUE;
  /** An array of the renderers types (default is NEEDLE). */
  private List<Type> mVisualTypes = new ArrayList<Type>();

  public enum Type {
    NEEDLE, ARROW;
  }

  /**
   * Returns the start angle value of the dial.
   * 
   * @return the angle start value
   */
  public double getAngleMin() {
    return mAngleMin;
  }

  /**
   * Sets the start angle value of the dial.
   * 
   * @param min the dial angle start value
   */
  public void setAngleMin(double min) {
    mAngleMin = min;
  }

  /**
   * Returns the end angle value of the dial.
   * 
   * @return the angle end value
   */
  public double getAngleMax() {
    return mAngleMax;
  }

  /**
   * Sets the end angle value of the dial.
   * 
   * @param max the dial angle end value
   */
  public void setAngleMax(double max) {
    mAngleMax = max;
  }

  /**
   * Returns the start value to be rendered on the dial.
   * 
   * @return the start value on dial
   */
  public double getMinValue() {
    return mMinValue;
  }

  /**
   * Sets the start value to be rendered on the dial.
   * 
   * @param min the start value on the dial
   */
  public void setMinValue(double min) {
    mMinValue = min;
  }

  /**
   * Returns if the minimum dial value was set.
   * 
   * @return the minimum dial value was set or not
   */
  public boolean isMinValueSet() {
    return mMinValue != MathHelper.NULL_VALUE;
  }

  /**
   * Returns the end value to be rendered on the dial.
   * 
   * @return the end value on the dial
   */
  public double getMaxValue() {
    return mMaxValue;
  }

  /**
   * Sets the end value to be rendered on the dial.
   * 
   * @param max the end value on the dial
   */
  public void setMaxValue(double max) {
    mMaxValue = max;
  }

  /**
   * Returns if the maximum dial value was set.
   * 
   * @return the maximum dial was set or not
   */
  public boolean isMaxValueSet() {
    return mMaxValue != -MathHelper.NULL_VALUE;
  }

  /**
   * Returns the minor ticks spacing.
   * 
   * @return the minor ticks spacing
   */
  public double getMinorTicksSpacing() {
    return mMinorTickSpacing;
  }

  /**
   * Sets the minor ticks spacing.
   * 
   * @param spacing the minor ticks spacing
   */
  public void setMinorTicksSpacing(double spacing) {
    mMinorTickSpacing = spacing;
  }

  /**
   * Returns the major ticks spacing.
   * 
   * @return the major ticks spacing
   */
  public double getMajorTicksSpacing() {
    return mMajorTickSpacing;
  }

  /**
   * Sets the major ticks spacing.
   * 
   * @param spacing the major ticks spacing
   */
  public void setMajorTicksSpacing(double spacing) {
    mMajorTickSpacing = spacing;
  }

  /**
   * Returns the visual type at the specified index.
   * 
   * @param index the index
   * @return the visual type
   */
  public Type getVisualTypeForIndex(int index) {
    if (index < mVisualTypes.size()) {
      return mVisualTypes.get(index);
    }
    return Type.NEEDLE;
  }

  /**
   * Sets the visual types.
   * 
   * @param types the visual types
   */
  public void setVisualTypes(Type[] types) {
    mVisualTypes.clear();
    mVisualTypes.addAll(Arrays.asList(types));
  }

}
