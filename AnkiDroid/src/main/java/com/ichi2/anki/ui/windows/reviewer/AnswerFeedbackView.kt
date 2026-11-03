// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.ui.windows.reviewer

import android.content.Context
import android.util.AttributeSet
import android.view.animation.AnimationUtils
import androidx.appcompat.widget.AppCompatImageView
import com.ichi2.anki.common.android.Animations
import com.ichi2.anki.utils.ext.setDuration
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
                return@Runnable
            }
            animate()
                .alpha(0f)
                .setDuration(250.milliseconds)
                .setInterpolator(AnimationUtils.loadInterpolator(context, android.R.interpolator.fast_out_linear_in))
                .withEndAction { visibility = GONE }
                .start()
        }

    /**
     * Shows the feedback for 800ms
     * with a quick fade in, brief hold, then gentle fade out.
     */
    fun toggle() {
        animate().cancel()
        removeCallbacks(fadeOut)
        visibility = VISIBLE

        if (!Animations.areAnimationsEnabled(context)) {
            alpha = 1f
            postDelayed(fadeOut, 800.milliseconds)
            return
        }

        alpha = 0f
        animate()
            .alpha(1f)
            .setDuration(150.milliseconds)
            .setInterpolator(AnimationUtils.loadInterpolator(context, android.R.interpolator.fast_out_slow_in))
            .withEndAction { postDelayed(fadeOut, 400.milliseconds) }
            .start()
    }
}
