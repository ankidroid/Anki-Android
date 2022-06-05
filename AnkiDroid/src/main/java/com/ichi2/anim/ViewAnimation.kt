//noinspection MissingCopyrightHeader #8659

package com.ichi2.anim

import android.view.animation.AccelerateInterpolator
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.DecelerateInterpolator
import android.view.animation.TranslateAnimation
import com.ichi2.anim.ViewAnimation.Slide.*

object ViewAnimation {
    enum class Slide {
        SLIDE_IN_FROM_RIGHT,
        SLIDE_OUT_TO_RIGHT,
        SLIDE_IN_FROM_LEFT,
        SLIDE_OUT_TO_LEFT,
        SLIDE_IN_FROM_BOTTOM,
        SLIDE_IN_FROM_TOP;
    }
    fun slide(type: Slide, duration: Int, offset: Int): Animation {
        val animation: Animation
        when (type) {
            SLIDE_IN_FROM_RIGHT -> {
                animation = TranslateAnimation(
                    Animation.RELATIVE_TO_SELF, +1.0f, Animation.RELATIVE_TO_SELF, 0.0f,
                    Animation.RELATIVE_TO_SELF, 0.0f, Animation.RELATIVE_TO_SELF, 0.0f
                )
            }
            SLIDE_OUT_TO_RIGHT -> {
                animation = TranslateAnimation(
                    Animation.RELATIVE_TO_SELF, 0.0f, Animation.RELATIVE_TO_SELF, +1.0f,
                    Animation.RELATIVE_TO_SELF, 0.0f, Animation.RELATIVE_TO_SELF, 0.0f
                )
            }
            SLIDE_IN_FROM_LEFT -> {
                animation = TranslateAnimation(
                    Animation.RELATIVE_TO_SELF, -1.0f, Animation.RELATIVE_TO_SELF, 0.0f,
                    Animation.RELATIVE_TO_SELF, 0.0f, Animation.RELATIVE_TO_SELF, 0.0f
                )
            }
            SLIDE_OUT_TO_LEFT -> {
                animation = TranslateAnimation(
                    Animation.RELATIVE_TO_SELF, 0.0f, Animation.RELATIVE_TO_SELF, -1.0f,
                    Animation.RELATIVE_TO_SELF, 0.0f, Animation.RELATIVE_TO_SELF, 0.0f
                )
            }
            SLIDE_IN_FROM_BOTTOM -> {
                animation = TranslateAnimation(
                    Animation.RELATIVE_TO_SELF, 0.0f, Animation.RELATIVE_TO_SELF, 0.0f,
                    Animation.RELATIVE_TO_SELF, +1.0f, Animation.RELATIVE_TO_SELF, 0.0f
                )
            }
            SLIDE_IN_FROM_TOP -> {
                animation = TranslateAnimation(
                    Animation.RELATIVE_TO_SELF, 0.0f, Animation.RELATIVE_TO_SELF, 0.0f,
                    Animation.RELATIVE_TO_SELF, -1.0f, Animation.RELATIVE_TO_SELF, 0.0f
                )
            }
        }
        // can't factorize setInterpolator out due to some typing issue in API 21.
        when (type) {
            SLIDE_IN_FROM_BOTTOM,
            SLIDE_IN_FROM_LEFT,
            SLIDE_IN_FROM_RIGHT,
            SLIDE_IN_FROM_TOP -> {
                animation.setInterpolator(DecelerateInterpolator())
            }
            SLIDE_OUT_TO_LEFT, SLIDE_OUT_TO_RIGHT -> {
                animation.setInterpolator(AccelerateInterpolator())
            }
        }
        animation.duration = duration.toLong()
        animation.startOffset = offset.toLong()
        return animation
    }

    enum class Fade(val originalAlpha: Float) {
        FADE_IN(0f),
        FADE_OUT(1f);
    }
    @JvmStatic
    fun fade(type: Fade, duration: Int, offset: Int): Animation {
        val animation: Animation = AlphaAnimation(type.originalAlpha, 1.0f - type.originalAlpha)
        animation.duration = duration.toLong()
        if (type == Fade.FADE_IN) {
            animation.zAdjustment = Animation.ZORDER_TOP
        }
        animation.startOffset = offset.toLong()
        return animation
    }
}
