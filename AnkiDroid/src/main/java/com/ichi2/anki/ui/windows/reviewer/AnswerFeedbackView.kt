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
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView
import androidx.interpolator.view.animation.FastOutLinearInInterpolator
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import com.ichi2.anki.common.android.Animations
import com.ichi2.anki.utils.postDelayed
import kotlin.time.Duration.Companion.milliseconds

/**
 * Brief ease-rating indicator overlaid on the reviewer WebView.
 *
 * Uses [android.view.ViewPropertyAnimator] (RenderNode alpha) rather than legacy
 * [android.view.animation.Animation]. Legacy [android.view.animation.AlphaAnimation] only affects
 * how the view is drawn each frame and does not update the RenderNode alpha used by HWUI. When
 * Frame style is Box ([com.ichi2.anki.previewer.setFrameStyle] clears elevation / rounded outline),
 * that drawing-time alpha composites against Chromium's `backdrop-filter` layers while the next
 * card loads, producing a dark smudge at the top edge (issue 21380). Elevated Card style isolates
 * children in a separate compositing layer and hides the seam. Property animation participates in
 * normal HWUI composition and avoids the artifact without permanent hardware layers or frame-style
 * changes.
 */
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
        cancelPendingFeedback()

        if (!Animations.areAnimationsEnabled(context)) {
            alpha = 1f
            visibility = VISIBLE
            fadeOutRunnable =
                Runnable {
                    hideFeedback()
                }.also {
                    handler.postDelayed(it, TOTAL_VISIBLE_DURATION)
                }
            return
        }

        alpha = 0f
        animate()
            .alpha(1f)
            .setDuration(FADE_IN_MS)
            .setInterpolator(FADE_IN_INTERPOLATOR)
            .withStartAction { visibility = VISIBLE }
            .withEndAction { scheduleFadeOut() }
            .start()
    }

    private fun scheduleFadeOut() {
        fadeOutRunnable =
            Runnable {
                fadeOutRunnable = null
                animate()
                    .alpha(0f)
                    .setDuration(FADE_OUT_MS)
                    .setInterpolator(FADE_OUT_INTERPOLATOR)
                    .withEndAction { hideFeedback() }
                    .start()
            }.also {
                handler.postDelayed(it, HOLD_MS.milliseconds)
            }
    }

    private fun hideFeedback() {
        visibility = GONE
        // Reset so a later non-animated show, or a cancelled mid-fade, starts from a clean alpha.
        alpha = 1f
        fadeOutRunnable = null
    }

    private fun cancelPendingFeedback() {
        animate().cancel()
        fadeOutRunnable?.let { handler.removeCallbacks(it) }
        fadeOutRunnable = null
    }

    override fun onDetachedFromWindow() {
        cancelPendingFeedback()
        super.onDetachedFromWindow()
    }

    companion object {
        private const val FADE_IN_MS = 150L
        private const val HOLD_MS = 400L
        private const val FADE_OUT_MS = 250L

        /** Matches prior fade-in + hold + fade-out when animations are disabled. */
        private val TOTAL_VISIBLE_DURATION = (FADE_IN_MS + HOLD_MS + FADE_OUT_MS).milliseconds

        private val FADE_IN_INTERPOLATOR = FastOutSlowInInterpolator()
        private val FADE_OUT_INTERPOLATOR = FastOutLinearInInterpolator()
    }
}
