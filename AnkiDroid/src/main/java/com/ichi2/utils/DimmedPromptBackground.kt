/******************************************************************************************
 *   Copyright (c) 2021 Shridhar Goel <shridhar.goel@gmail.com>                           *
 *                                                                                        *
 *   This program is free software; you can redistribute it and/or modify it under        *
 *   the terms of the GNU General Public License as published by the Free Software        *
 *   Foundation; either version 3 of the License, or (at your option) any later           *
 *   version.                                                                             *
 *                                                                                        *
 *   This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 *   WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 *   PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 *                                                                                        *
 *   You should have received a copy of the GNU General Public License along with         *
 *   this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 *                                                                                        *
 *   This file incorporates work covered by the following copyright and                   *
 *   permission notice:                                                                   *
 *                                                                                        *
 *     The implementation used here has been taken from the documentation of the          *
 *     MaterialTapTargetPrompt library.                                                   *
 *     Link: https://sjwall.github.io/MaterialTapTargetPrompt/shapes.html                 *
 *                                                                                        *
 *     Copyright 2016-2021 Samuel Wall                                                    *
 *                                                                                        *
 *     Licensed under the Apache License, Version 2.0 (the "License");                    *
 *     you may not use this file except in compliance with the License.                   *
 *     You may obtain a copy of the License at                                            *
 *                                                                                        *
 *     http://www.apache.org/licenses/LICENSE-2.0                                         *
 *                                                                                        *
 *     Unless required by applicable law or agreed to in writing, software                *
 *     distributed under the License is distributed on an "AS IS" BASIS,                  *
 *     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.           *
 *     See the License for the specific language governing permissions and                *
 *     limitations under the License.                                                     *
 ******************************************************************************************/

package com.ichi2.utils

import android.content.res.Resources
import android.graphics.*
import uk.co.samuelwall.materialtaptargetprompt.extras.PromptBackground
import uk.co.samuelwall.materialtaptargetprompt.extras.PromptOptions

/**
 * Dims the background of the screen so that the highlighted view remains in focus.
 * Works with both CirclePromptBackground and RectanglePromptBackground.
 */

/*
This class inherits from PromptBackground without reusing any of the method of super.
Also, an instance of PromptBackground is being used as an argument in the constructor.

Here is the explanation for this:

It seems that this class could work exactly as well without inheriting. However, this would
forbid us to use this class with methods such as PromptOptions#setPromptBackground that expects
a PromptBackground. Inheriting allows such a call to by correctly typed, even if there is not
a single method in common.

It seems that mPromptBackground.foo() could be replaced by super.foo() in order to avoid
taking a parameter. This would in particular allow not to define setColour and contains.
However, setColour is abstract and contains comes from an interface. Clearly, not implementing
them is not an option. The reason why it works is that actually mPromptBackground is NOT an
object of type PromptBackground but is an object of concrete type inheriting from PromptBackground,
such as RectanglePromptBackground and CirclePromptBackground. If we were to use inheritance,
we would need to inherit independently from RectanglePromptBackground and CirclePromptBackground,
leading to code duplication or requiring multiple inheritance. Multiple inheritance is only an
option with interfaces. However, we can't add this code in an interface, as this code would call
super, requiring the interface to inherit from PromptBackground, which is impossible since
PromptBackground is not an interface.
*/
class DimmedPromptBackground(val mPromptBackground: PromptBackground) : PromptBackground() {
    private val mDimBounds = RectF()
    private val mDimPaint: Paint = Paint()

    init {
        mDimPaint.color = Color.BLACK
    }

    /**
     * Prepares the background for drawing.
     * Set the screen bounds which require a dimmed background.
     *
     * @param options The options from which the prompt was created.
     * @param clipToBounds Should the prompt be clipped to the supplied clipBounds.
     * @param clipBounds The bounds to clip the drawing to.
     */
    override fun prepare(options: PromptOptions<*>, clipToBounds: Boolean, clipBounds: Rect) {
        mPromptBackground.prepare(options, clipToBounds, clipBounds)
        val metrics = Resources.getSystem().displayMetrics
        // Set the bounds to display as dimmed to the screen bounds.
        mDimBounds.set(0f, 0f, metrics.widthPixels.toFloat(), (metrics.heightPixels * 2).toFloat())
        // Multiplying metrics.heightPixels by 2 to fix issue where bottom area of the screen does not become dimmed.
    }

    /**
     * Update the current prompt rendering state based on the prompt options and current reveal & alpha scales.
     * Set the alpha value of mDimPaint based on the value of alphaModifier.
     *
     * @param options        The options used to create the prompt.
     * @param revealModifier The current size/revealed scale from 0 - 1.
     * @param alphaModifier  The current colour alpha scale from 0 - 1.
     */
    override fun update(options: PromptOptions<*>, revealModifier: Float, alphaModifier: Float) {
        mPromptBackground.update(options, revealModifier, alphaModifier)
        // Allow for the dimmed background to fade in and out.
        mDimPaint.alpha = (150 * alphaModifier).toInt()
    }

    /**
     * Draw the dimmed background and the prompt background.
     *
     * @param canvas The canvas to draw to.
     */
    override fun draw(canvas: Canvas) {
        // Draw the dimmed background.
        canvas.drawRect(mDimBounds, mDimPaint)
        // Draw the background.
        mPromptBackground.draw(canvas)
    }

    /**
     * Check if the element contains the point.
     *
     * @param x x-coordinate.
     * @param y y-coordinate.
     * @return True if the element contains the point, false otherwise.
     */
    override fun contains(x: Float, y: Float): Boolean {
        return mPromptBackground.contains(x, y)
    }

    /**
     * Sets the colour to use for the background.
     *
     * @param colour Colour integer representing the colour.
     */
    override fun setColour(colour: Int) {
        mPromptBackground.setColour(colour)
    }
}
