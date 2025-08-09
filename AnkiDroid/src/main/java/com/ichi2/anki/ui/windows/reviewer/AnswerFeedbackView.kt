/*
 * Copyright (c) 2025 Brayan Oliveira <69634269+brayandso@users.noreply.github.com>
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
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.ui.windows.reviewer

import android.content.Context
import android.util.AttributeSet
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import androidx.appcompat.widget.AppCompatImageView
import com.ichi2.utils.HandlerUtils

class AnswerFeedbackView
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0,
    ) : AppCompatImageView(context, attrs, defStyleAttr) {
        /**
         * Fades in and fades out for a brief amount of time.
         *
         * TODO handle "safeDisplay" setting
         */
        fun toggle() {
            val fadeIn = AnimationUtils.loadAnimation(context, android.R.anim.fade_in)
            val fadeOut = AnimationUtils.loadAnimation(context, android.R.anim.fade_out)
            fadeIn.duration = 125
            fadeOut.duration = 175
            fadeIn.setAnimationListener(
                object : Animation.AnimationListener {
                    override fun onAnimationStart(animation: Animation) {
                        visibility = VISIBLE
                    }

                    override fun onAnimationEnd(animation: Animation) {
                        HandlerUtils.executeFunctionWithDelay(200) {
                            startAnimation(fadeOut)
                        }
                    }

                    override fun onAnimationRepeat(animation: Animation) {}
                },
            )
            fadeOut.setAnimationListener(
                object : Animation.AnimationListener {
                    override fun onAnimationStart(animation: Animation) {}

                    override fun onAnimationEnd(animation: Animation) {
                        visibility = INVISIBLE
                    }

                    override fun onAnimationRepeat(animation: Animation) {}
                },
            )
            startAnimation(fadeIn)
        }
    }
