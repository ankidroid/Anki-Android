/****************************************************************************************
 *                                                                                      *
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

import android.app.Activity
import com.ichi2.anki.PromptBackgroundAdapter.Companion.toPromptBackground
import com.ichi2.themes.Themes
import com.ichi2.utils.DimmedPromptBackgroundDecorator
import uk.co.samuelwall.materialtaptargetprompt.MaterialTapTargetPrompt
import uk.co.samuelwall.materialtaptargetprompt.extras.backgrounds.CirclePromptBackground
import uk.co.samuelwall.materialtaptargetprompt.extras.backgrounds.RectanglePromptBackground
import uk.co.samuelwall.materialtaptargetprompt.extras.focals.CirclePromptFocal
import uk.co.samuelwall.materialtaptargetprompt.extras.focals.RectanglePromptFocal

class CustomMaterialTapTargetPromptBuilder<T>(val activity: Activity, private val featureIdentifier: T) : MaterialTapTargetPrompt.Builder(activity) where T : Enum<T>, T : OnboardingFlag {

    private fun createRectangle(): CustomMaterialTapTargetPromptBuilder<T> {
        promptFocal = RectanglePromptFocal()
        return this
    }

    fun createRectangleWithDimmedBackground(): CustomMaterialTapTargetPromptBuilder<T> {
        promptBackground = DimmedPromptBackgroundDecorator(RectanglePromptBackground()).toPromptBackground()
        return createRectangle()
    }

    fun createCircle(): CustomMaterialTapTargetPromptBuilder<T> {
        promptFocal = CirclePromptFocal()
        return this
    }

    fun createCircleWithDimmedBackground(): CustomMaterialTapTargetPromptBuilder<T> {
        promptBackground = DimmedPromptBackgroundDecorator(CirclePromptBackground()).toPromptBackground()
        return createCircle()
    }

    fun setFocalColourResource(focalColourRes: Int): CustomMaterialTapTargetPromptBuilder<T> {
        focalColour = activity.getColor(focalColourRes)
        return this
    }

    /**
     * Handle dismissal of a tutorial.
     * Currently it happens when the user clicks anywhere while a tutorial is being shown.
     * @param tutorialFunction Function which would be called when tutorial is dismissed.
     * @return Builder object to allow chaining of method calls
     */
    fun setDismissedListener(tutorialFunction: () -> Unit): CustomMaterialTapTargetPromptBuilder<T> {
        setPromptStateChangeListener { _, state ->
            if (state == MaterialTapTargetPrompt.STATE_DISMISSED) {
                tutorialFunction()
            }
        }

        return this
    }

    /**
     * Mark as visited and show the tutorial.
     */
    override fun show(): MaterialTapTargetPrompt? {
        /* Keep the focal colour as transparent for night mode
           so that the contents being highlighted are visible properly */
        if (Themes.currentTheme.isNightMode) {
            setFocalColourResource(R.color.transparent)
        }

        // Clicking outside the prompt area should not trigger a click on the underlying view
        // This will prevent click on any outside view when user tries to dismiss the feature prompt
        captureTouchEventOutsidePrompt = true

        OnboardingUtils.setVisited(featureIdentifier, activity)
        return super.show()
    }
}
