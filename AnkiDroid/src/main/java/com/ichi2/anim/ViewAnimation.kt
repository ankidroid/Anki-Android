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
    const val FADE_IN = 0
    const val FADE_OUT = 1
    fun slide(type: Slide, duration: Int, offset: Int): Animation {
        val animation: Animation
        when (type) {
            SLIDE_IN_FROM_RIGHT -> {
                animation = TranslateAnimation(
                    Animation.RELATIVE_TO_SELF, +1.0f, Animation.RELATIVE_TO_SELF, 0.0f,
                    Animation.RELATIVE_TO_SELF, 0.0f, Animation.RELATIVE_TO_SELF, 0.0f
                )
                animation.setInterpolator(DecelerateInterpolator())
            }
            SLIDE_OUT_TO_RIGHT -> {
                animation = TranslateAnimation(
                    Animation.RELATIVE_TO_SELF, 0.0f, Animation.RELATIVE_TO_SELF, +1.0f,
                    Animation.RELATIVE_TO_SELF, 0.0f, Animation.RELATIVE_TO_SELF, 0.0f
                )
                animation.setInterpolator(AccelerateInterpolator())
            }
            SLIDE_IN_FROM_LEFT -> {
                animation = TranslateAnimation(
                    Animation.RELATIVE_TO_SELF, -1.0f, Animation.RELATIVE_TO_SELF, 0.0f,
                    Animation.RELATIVE_TO_SELF, 0.0f, Animation.RELATIVE_TO_SELF, 0.0f
                )
                animation.setInterpolator(DecelerateInterpolator())
            }
            SLIDE_OUT_TO_LEFT -> {
                animation = TranslateAnimation(
                    Animation.RELATIVE_TO_SELF, 0.0f, Animation.RELATIVE_TO_SELF, -1.0f,
                    Animation.RELATIVE_TO_SELF, 0.0f, Animation.RELATIVE_TO_SELF, 0.0f
                )
                animation.setInterpolator(AccelerateInterpolator())
            }
            SLIDE_IN_FROM_BOTTOM -> {
                animation = TranslateAnimation(
                    Animation.RELATIVE_TO_SELF, 0.0f, Animation.RELATIVE_TO_SELF, 0.0f,
                    Animation.RELATIVE_TO_SELF, +1.0f, Animation.RELATIVE_TO_SELF, 0.0f
                )
                animation.setInterpolator(DecelerateInterpolator())
            }
            SLIDE_IN_FROM_TOP -> {
                animation = TranslateAnimation(
                    Animation.RELATIVE_TO_SELF, 0.0f, Animation.RELATIVE_TO_SELF, 0.0f,
                    Animation.RELATIVE_TO_SELF, -1.0f, Animation.RELATIVE_TO_SELF, 0.0f
                )
                animation.setInterpolator(DecelerateInterpolator())
            }
        }
        animation.duration = duration.toLong()
        animation.startOffset = offset.toLong()
        return animation
    }

    @JvmStatic
    fun fade(type: Int, duration: Int, offset: Int): Animation {
        val animation: Animation = AlphaAnimation(type.toFloat(), 1.0f - type.toFloat())
        animation.duration = duration.toLong()
        if (type == FADE_IN) {
            animation.zAdjustment = Animation.ZORDER_TOP
        }
        animation.startOffset = offset.toLong()
        return animation
    }
}
