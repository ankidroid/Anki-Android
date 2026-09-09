// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2025 Brayan Oliveira <69634269+brayandso@users.noreply.github.com>

package com.ichi2.anki.ui.windows.reviewer

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import androidx.appcompat.widget.AppCompatImageView
import com.ichi2.anki.R
import com.ichi2.anki.common.android.Animations
import com.ichi2.anki.utils.postDelayed
import kotlin.time.Duration.Companion.milliseconds

class AnswerFeedbackView : AppCompatImageView {
    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    private var fadeOutRunnable: Runnable? = null
    private val handler = Handler(Looper.getMainLooper())

    /**
     * Shows the feedback for one second
     * with a quick fade in, brief hold, then gentle fade out.
     */
    fun toggle() {
        clearAnimation()

        fadeOutRunnable?.let {
            handler.removeCallbacks(it)
            fadeOutRunnable = null
        }

        if (!Animations.areAnimationsEnabled(context)) {
            visibility = VISIBLE
            fadeOutRunnable =
                Runnable {
                    visibility = GONE
                    fadeOutRunnable = null
                }.also {
                    handler.postDelayed(it, 800.milliseconds)
                }
            return
        }

        val fadeIn = AnimationUtils.loadAnimation(context, R.anim.answer_feedback_fade_in)
        val fadeOut = AnimationUtils.loadAnimation(context, R.anim.answer_feedback_fade_out)

        fadeIn.setAnimationListener(
            object : Animation.AnimationListener {
                override fun onAnimationStart(animation: Animation) {
                    visibility = VISIBLE
                }

                override fun onAnimationEnd(animation: Animation) {
                    fadeOutRunnable =
                        Runnable {
                            startAnimation(fadeOut)
                        }.also {
                            handler.postDelayed(it, 400)
                        }
                }

                override fun onAnimationRepeat(animation: Animation) {}
            },
        )
        fadeOut.setAnimationListener(
            object : Animation.AnimationListener {
                override fun onAnimationStart(animation: Animation) {}

                override fun onAnimationEnd(animation: Animation) {
                    visibility = GONE
                    fadeOutRunnable = null
                }

                override fun onAnimationRepeat(animation: Animation) {}
            },
        )
        startAnimation(fadeIn)
    }
}
