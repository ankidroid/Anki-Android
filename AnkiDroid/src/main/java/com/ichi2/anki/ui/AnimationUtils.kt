/*
 * Copyright (c) 2025 Ankitects Pty Ltd <https://apps.ankiweb.net>
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

package com.ichi2.anki.ui

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.transition.AutoTransition
import android.transition.ChangeBounds
import android.transition.ChangeImageTransform
import android.transition.ChangeTransform
import android.transition.Transition
import android.transition.TransitionManager
import android.transition.TransitionSet
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.core.animation.doOnEnd
import androidx.core.view.ViewCompat
import androidx.interpolator.view.animation.FastOutLinearInInterpolator
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.interpolator.view.animation.LinearOutSlowInInterpolator
import com.ichi2.anki.preferences.sharedPrefs

/**
 * Utility class for providing smoother animations throughout the app.
 * Uses Material Design motion principles for better user experience.
 */
object AnimationUtils {
    // Material Design animation durations
    private const val DURATION_SHORT = 200L
    private const val DURATION_MEDIUM = 300L
    private const val DURATION_LONG = 500L

    // Standard Material Design interpolators
    private val FAST_OUT_SLOW_IN = FastOutSlowInInterpolator()
    private val FAST_OUT_LINEAR_IN = FastOutLinearInInterpolator()
    private val LINEAR_OUT_SLOW_IN = LinearOutSlowInInterpolator()
    private val ACCELERATE_DECELERATE = AccelerateDecelerateInterpolator()

    /**
     * Checks if animations should be disabled based on user preferences or system settings
     */
    fun shouldDisableAnimations(context: Context): Boolean {
        val prefs = context.sharedPrefs()
        return prefs.getBoolean("safeDisplay", false)
    }

    /**
     * Creates a smooth shared element-like transition between two views
     * @param fromView The view to transition from
     * @param toView The view to transition to
     * @param container The parent container
     * @param onComplete Callback when animation completes
     */
    fun createSharedElementTransition(
        fromView: View,
        toView: View,
        container: ViewGroup,
        onComplete: (() -> Unit)? = null,
    ) {
        if (shouldDisableAnimations(fromView.context)) {
            onComplete?.invoke()
            return
        }

        val transitionSet =
            TransitionSet().apply {
                addTransition(ChangeBounds())
                addTransition(ChangeTransform())
                addTransition(ChangeImageTransform())
                duration = DURATION_MEDIUM
                interpolator = FAST_OUT_SLOW_IN
            }

        transitionSet.addListener(
            object : Transition.TransitionListener {
                override fun onTransitionStart(transition: Transition) {}

                override fun onTransitionEnd(transition: Transition) {
                    onComplete?.invoke()
                }

                override fun onTransitionCancel(transition: Transition) {}

                override fun onTransitionPause(transition: Transition) {}

                override fun onTransitionResume(transition: Transition) {}
            },
        )

        TransitionManager.beginDelayedTransition(container, transitionSet)
    }

    /**
     * Creates a smooth slide transition for navigation between screens
     */
    fun createSlideTransition(
        view: View,
        direction: SlideDirection,
        duration: Long = DURATION_MEDIUM,
        onComplete: (() -> Unit)? = null,
    ) {
        if (shouldDisableAnimations(view.context)) {
            onComplete?.invoke()
            return
        }

        val distance =
            when (direction) {
                SlideDirection.LEFT, SlideDirection.RIGHT -> view.width.toFloat()
                SlideDirection.UP, SlideDirection.DOWN -> view.height.toFloat()
            }

        val startX =
            when (direction) {
                SlideDirection.LEFT -> distance
                SlideDirection.RIGHT -> -distance
                else -> 0f
            }

        val startY =
            when (direction) {
                SlideDirection.UP -> distance
                SlideDirection.DOWN -> -distance
                else -> 0f
            }

        view.translationX = startX
        view.translationY = startY
        view.alpha = 0f

        val animatorX = ObjectAnimator.ofFloat(view, "translationX", startX, 0f)
        val animatorY = ObjectAnimator.ofFloat(view, "translationY", startY, 0f)
        val animatorAlpha = ObjectAnimator.ofFloat(view, "alpha", 0f, 1f)

        val animatorSet =
            AnimatorSet().apply {
                playTogether(animatorX, animatorY, animatorAlpha)
                this.duration = duration
                interpolator = FAST_OUT_SLOW_IN
            }

        animatorSet.doOnEnd { onComplete?.invoke() }
        animatorSet.start()
    }

    /**
     * Creates a fade transition with scale for a more dynamic feel
     */
    fun createFadeScaleTransition(
        view: View,
        fadeIn: Boolean = true,
        duration: Long = DURATION_SHORT,
        onComplete: (() -> Unit)? = null,
    ) {
        if (shouldDisableAnimations(view.context)) {
            if (fadeIn) {
                view.alpha = 1f
                view.scaleX = 1f
                view.scaleY = 1f
                view.visibility = View.VISIBLE
            } else {
                view.visibility = View.GONE
            }
            onComplete?.invoke()
            return
        }

        if (fadeIn) {
            view.alpha = 0f
            view.scaleX = 0.8f
            view.scaleY = 0.8f
            view.visibility = View.VISIBLE

            val animatorSet =
                AnimatorSet().apply {
                    playTogether(
                        ObjectAnimator.ofFloat(view, "alpha", 0f, 1f),
                        ObjectAnimator.ofFloat(view, "scaleX", 0.8f, 1f),
                        ObjectAnimator.ofFloat(view, "scaleY", 0.8f, 1f),
                    )
                    this.duration = duration
                    interpolator = LINEAR_OUT_SLOW_IN
                }

            animatorSet.doOnEnd { onComplete?.invoke() }
            animatorSet.start()
        } else {
            val animatorSet =
                AnimatorSet().apply {
                    playTogether(
                        ObjectAnimator.ofFloat(view, "alpha", 1f, 0f),
                        ObjectAnimator.ofFloat(view, "scaleX", 1f, 0.8f),
                        ObjectAnimator.ofFloat(view, "scaleY", 1f, 0.8f),
                    )
                    this.duration = duration
                    interpolator = FAST_OUT_LINEAR_IN
                }

            animatorSet.doOnEnd {
                view.visibility = View.GONE
                onComplete?.invoke()
            }
            animatorSet.start()
        }
    }

    /**
     * Creates a smooth card elevation animation
     */
    fun animateCardElevation(
        view: View,
        fromElevation: Float,
        toElevation: Float,
        duration: Long = DURATION_SHORT,
    ) {
        if (shouldDisableAnimations(view.context)) {
            ViewCompat.setElevation(view, toElevation)
            return
        }

        val animator =
            ValueAnimator.ofFloat(fromElevation, toElevation).apply {
                this.duration = duration
                interpolator = FAST_OUT_SLOW_IN
                addUpdateListener { animation ->
                    ViewCompat.setElevation(view, animation.animatedValue as Float)
                }
            }
        animator.start()
    }

    /**
     * Creates a ripple-like expand animation
     */
    fun createExpandAnimation(
        view: View,
        duration: Long = DURATION_MEDIUM,
        onComplete: (() -> Unit)? = null,
    ) {
        if (shouldDisableAnimations(view.context)) {
            view.visibility = View.VISIBLE
            onComplete?.invoke()
            return
        }

        view.alpha = 0f
        view.scaleX = 0f
        view.scaleY = 0f
        view.visibility = View.VISIBLE

        val animatorSet =
            AnimatorSet().apply {
                playTogether(
                    ObjectAnimator.ofFloat(view, "alpha", 0f, 1f),
                    ObjectAnimator.ofFloat(view, "scaleX", 0f, 1f),
                    ObjectAnimator.ofFloat(view, "scaleY", 0f, 1f),
                )
                this.duration = duration
                interpolator = LinearOutSlowInInterpolator()
            }

        animatorSet.doOnEnd { onComplete?.invoke() }
        animatorSet.start()
    }

    /**
     * Creates a collapse animation
     */
    fun createCollapseAnimation(
        view: View,
        duration: Long = DURATION_SHORT,
        onComplete: (() -> Unit)? = null,
    ) {
        if (shouldDisableAnimations(view.context)) {
            view.visibility = View.GONE
            onComplete?.invoke()
            return
        }

        val animatorSet =
            AnimatorSet().apply {
                playTogether(
                    ObjectAnimator.ofFloat(view, "alpha", 1f, 0f),
                    ObjectAnimator.ofFloat(view, "scaleX", 1f, 0f),
                    ObjectAnimator.ofFloat(view, "scaleY", 1f, 0f),
                )
                this.duration = duration
                interpolator = FastOutLinearInInterpolator()
            }

        animatorSet.doOnEnd {
            view.visibility = View.GONE
            onComplete?.invoke()
        }
        animatorSet.start()
    }

    /**
     * Creates a smooth container transform between views
     */
    fun createContainerTransform(
        startView: View,
        endView: View,
        startContainer: ViewGroup,
        endContainer: ViewGroup,
        duration: Long = DURATION_MEDIUM,
    ) {
        if (shouldDisableAnimations(startView.context)) {
            return
        }

        val autoTransition =
            AutoTransition().apply {
                this.duration = duration
                interpolator = FAST_OUT_SLOW_IN
            }

        TransitionManager.beginDelayedTransition(endContainer, autoTransition)
    }

    enum class SlideDirection {
        LEFT,
        RIGHT,
        UP,
        DOWN,
    }
}
