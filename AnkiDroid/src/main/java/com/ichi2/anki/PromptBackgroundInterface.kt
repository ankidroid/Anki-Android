/****************************************************************************************
 *                                                                                      *
 * Copyright (c) 2021 David Allison <davidallisongithub@gmail.com>                      *
 * Copyright (c) 2021 Shridhar Goel <shridhar.goel@gmail.com>                           *
 *                                                                                      *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 *                                                                                      *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/

package com.ichi2.anki

import android.graphics.Canvas
import android.graphics.Rect
import androidx.annotation.ColorInt
import uk.co.samuelwall.materialtaptargetprompt.extras.PromptBackground
import uk.co.samuelwall.materialtaptargetprompt.extras.PromptOptions

/**
 * Exposes [PromptBackground] as an interface.
 *
 * [PromptBackground] is an abstract class with only abstract methods, this unnecessarily forces
 * inheritance from consumers if they want to extend it. This interface allows extension via implementation,
 * flattening the class hierarchy. It can be created by a [PromptBackgroundInterfaceAdapter] and
 * converted back to a [PromptBackground] via a [PromptBackgroundAdapter].
 */
interface PromptBackgroundInterface {
    /**
     * Prepares the background for drawing.
     * Set the screen bounds which require a dimmed background.
     *
     * @param options The options from which the prompt was created.
     * @param clipToBounds Should the prompt be clipped to the supplied clipBounds.
     * @param clipBounds The bounds to clip the drawing to.
     */
    fun prepare(
        options: PromptOptions<*>,
        clipToBounds: Boolean,
        clipBounds: Rect,
    )

    /**
     * Update the current prompt rendering state based on the prompt options and current reveal & alpha scales.
     * Set the alpha value of mDimPaint based on the value of alphaModifier.
     *
     * @param options The options used to create the prompt.
     * @param revealModifier The current size/revealed scale from 0 - 1.
     * @param alphaModifier The current colour alpha scale from 0 - 1.
     */
    fun update(
        options: PromptOptions<*>,
        revealModifier: Float,
        alphaModifier: Float,
    )

    /**
     * Draw the dimmed background and the prompt background.
     *
     * @param canvas The canvas to draw to.
     */
    fun draw(canvas: Canvas)

    /**
     * Check if the element contains the point.
     *
     * @param x x-coordinate.
     * @param y y-coordinate.
     * @return true if the element contains the point, false otherwise.
     */
    fun contains(
        x: Float,
        y: Float,
    ): Boolean

    /**
     * Sets the colour to use for the background.
     *
     * @param colour Colour integer representing the colour.
     */
    fun setColour(
        @ColorInt colour: Int,
    )
}

/**
 * Converts from abstract [PromptBackground] to [PromptBackgroundInterface] to allow extensions of a
 * [PromptBackground] without the requirement to inherit from an empty abstract class.
 */
class PromptBackgroundInterfaceAdapter(private val promptBackground: PromptBackground) : PromptBackgroundInterface {
    companion object {
        /**
         * Takes PromptBackground and returns PromptBackgroundInterfaceAdapter which
         * implements PromptBackgroundInterface.
         */
        fun PromptBackground.toInterface(): PromptBackgroundInterface {
            return PromptBackgroundInterfaceAdapter(this)
        }
    }

    override fun update(
        options: PromptOptions<out PromptOptions<*>>,
        revealModifier: Float,
        alphaModifier: Float,
    ) {
        promptBackground.update(options, revealModifier, alphaModifier)
    }

    override fun draw(canvas: Canvas) {
        promptBackground.draw(canvas)
    }

    override fun contains(
        x: Float,
        y: Float,
    ): Boolean {
        return promptBackground.contains(x, y)
    }

    override fun setColour(colour: Int) {
        promptBackground.setColour(colour)
    }

    override fun prepare(
        options: PromptOptions<out PromptOptions<*>>,
        clipToBounds: Boolean,
        clipBounds: Rect,
    ) {
        promptBackground.prepare(options, clipToBounds, clipBounds)
    }
}
