//noinspection MissingCopyrightHeader #8659

package com.ichi2.anim

import android.view.animation.*

object ViewAnimation {
    enum class SlideIn {
        SLIDE_IN_FROM_RIGHT,
        SLIDE_OUT_TO_RIGHT,
        SLIDE_IN_FROM_LEFT,
        SLIDE_OUT_TO_LEFT,
        SLIDE_IN_FROM_BOTTOM,
        SLIDE_IN_FROM_TOP,
    }
    enum class Fade(val fromAlpha: Float) {
        FADE_IN(0f),
        FADE_OUT(1f),
    }

    fun translateAnimation(fromXValue: Float, toXValue: Float, fromYValue: Float, toYValue: Float, interpolator: Interpolator): TranslateAnimation {
        val animation = TranslateAnimation(Animation.RELATIVE_TO_SELF, fromXValue, Animation.RELATIVE_TO_SELF, toXValue, Animation.RELATIVE_TO_SELF, fromYValue, Animation.RELATIVE_TO_SELF, toYValue)
        animation.setInterpolator(interpolator)
        return animation
    }

    fun slide(type: SlideIn, duration: Int, offset: Int): Animation? {
        val animation: Animation?
        when (type) {
            SlideIn.SLIDE_IN_FROM_RIGHT -> {
                animation = translateAnimation(
                    fromXValue = +1.0f, toXValue = 0.0f,
                    fromYValue = 0.0f, toYValue = 0.0f, interpolator = DecelerateInterpolator()
                )
            }
            SlideIn.SLIDE_OUT_TO_RIGHT -> {
                animation = translateAnimation(
                    fromXValue = 0.0f, toXValue = +1.0f,
                    fromYValue = 0.0f, toYValue = 0.0f, interpolator = AccelerateInterpolator()
                )
            }
            SlideIn.SLIDE_IN_FROM_LEFT -> {
                animation = translateAnimation(
                    fromXValue = -1.0f, toXValue = 0.0f,
                    fromYValue = 0.0f, toYValue = 0.0f, interpolator = DecelerateInterpolator()
                )
            }
            SlideIn.SLIDE_OUT_TO_LEFT -> {
                animation = translateAnimation(
                    fromXValue = 0.0f, toXValue = -1.0f,
                    fromYValue = 0.0f, toYValue = 0.0f, interpolator = AccelerateInterpolator()
                )
            }
            SlideIn.SLIDE_IN_FROM_BOTTOM -> {
                animation = translateAnimation(
                    fromXValue = 0.0f, toXValue = 0.0f,
                    fromYValue = +1.0f, toYValue = 0.0f, interpolator = DecelerateInterpolator()
                )
            }
            SlideIn.SLIDE_IN_FROM_TOP -> {
                animation = translateAnimation(
                    fromXValue = 0.0f, toXValue = 0.0f,
                    fromYValue = -1.0f, toYValue = 0.0f, interpolator = DecelerateInterpolator()
                )
            }
        }
        animation.duration = duration.toLong()
        animation.startOffset = offset.toLong()
        return animation
    }

    @JvmStatic
    fun fade(type: Fade, duration: Int, offset: Int): Animation {
        val animation: Animation = AlphaAnimation(type.fromAlpha, 1.0f - type.fromAlpha)
        animation.duration = duration.toLong()
        if (type == Fade.FADE_IN) {
            animation.zAdjustment = Animation.ZORDER_TOP
        }
        animation.startOffset = offset.toLong()
        return animation
    }
}
