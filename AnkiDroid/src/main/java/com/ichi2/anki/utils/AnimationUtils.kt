/*
 * Copyright (c) 2025 Sanjay Sargam <sargamsanjaykumar@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.utils

import android.content.Context
import android.provider.Settings

/**
 * Utility class for animation-related helper functions
 */
object AnimationUtils {
    /**
     * Checks if system animations are enabled by verifying all animation scale settings.
     *
     * This function returns false if any of the mentioned system animations are disabled (0f),
     * which addresses safe display mode and accessibility concerns.
     *
     * ANIMATION_DURATION_SCALE - controls app switching animation speed.
     * TRANSITION_ANIMATION_SCALE - controls app window opening and closing animation speed
     * WINDOW_ANIMATION_SCALE - controls pop-up window opening and closing animation speed
     *
     * @param context The context used to access system settings
     * @return true if all animation scales are non-zero, false otherwise
     */
    fun areSystemAnimationsEnabled(context: Context): Boolean =
        try {
            val animDuration =
                Settings.Global.getFloat(
                    context.contentResolver,
                    Settings.Global.ANIMATOR_DURATION_SCALE,
                    1f,
                )
            val animTransition =
                Settings.Global.getFloat(
                    context.contentResolver,
                    Settings.Global.TRANSITION_ANIMATION_SCALE,
                    1f,
                )
            val animWindow =
                Settings.Global.getFloat(
                    context.contentResolver,
                    Settings.Global.WINDOW_ANIMATION_SCALE,
                    1f,
                )
            animDuration != 0f && animTransition != 0f && animWindow != 0f
        } catch (e: Exception) {
            true // Default to animations enabled if unable to read settings
        }
}
