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
import uk.co.samuelwall.materialtaptargetprompt.extras.PromptBackground
import uk.co.samuelwall.materialtaptargetprompt.extras.PromptOptions

/**
 * Used for changing PromptBackgroundInterface to PromptBackground.
 */
class PromptBackgroundAdapter(private val promptBackgroundInterface: PromptBackgroundInterface) : PromptBackground() {
    companion object {
        fun PromptBackgroundInterface.toPromptBackground(): PromptBackground {
            return PromptBackgroundAdapter(this)
        }
    }

    override fun update(
        options: PromptOptions<out PromptOptions<*>>,
        revealModifier: Float,
        alphaModifier: Float,
    ) {
        promptBackgroundInterface.update(options, revealModifier, alphaModifier)
    }

    override fun draw(canvas: Canvas) {
        promptBackgroundInterface.draw(canvas)
    }

    override fun contains(
        x: Float,
        y: Float,
    ): Boolean {
        return promptBackgroundInterface.contains(x, y)
    }

    override fun setColour(colour: Int) {
        promptBackgroundInterface.setColour(colour)
    }

    override fun prepare(
        options: PromptOptions<out PromptOptions<*>>,
        clipToBounds: Boolean,
        clipBounds: Rect,
    ) {
        promptBackgroundInterface.prepare(options, clipToBounds, clipBounds)
    }
}
