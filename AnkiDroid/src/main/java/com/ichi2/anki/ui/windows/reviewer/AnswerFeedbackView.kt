// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2025 Brayan Oliveira <69634269+brayandso@users.noreply.github.com>

package com.ichi2.anki.ui.windows.reviewer

import android.content.Context
import android.util.AttributeSet
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

    private val fadeOut =
        Runnable {
            if (!Animations.areAnimationsEnabled(context)) {
                visibility = GONE
            } else {
                animate()
                    .alpha(0f)
                    .setDuration(250)
                    .setInterpolator(AnimationUtils.loadInterpolator(context, android.R.interpolator.fast_out_linear_in))
                    .withEndAction { visibility = GONE }
                    .start()
            }
        }

    /**
     * Shows the feedback with a quick fade in, brief hold, then gentle fade out.
     * uses ViewPropertyAnimator instead of legacy AlphaAnimation to avoid compositing
     * artifacts with WebView backdrop-filter when Frame style "Box" removes card elevation.
     */
    fun toggle() {
        animate().cancel()

        removeCallbacks(fadeOut)

        if (!Animations.areAnimationsEnabled(context)) {
            visibility = VISIBLE
            postDelayed(fadeOut, 800.milliseconds)
            return
        }

        alpha = 0f
        visibility = VISIBLE

        animate()
            .alpha(1f)
            .setDuration(150)
            .setInterpolator(AnimationUtils.loadInterpolator(context, android.R.interpolator.fast_out_slow_in))
            .withEndAction {
                postDelayed(fadeOut, 400.milliseconds)
            }.start()
    }
}
