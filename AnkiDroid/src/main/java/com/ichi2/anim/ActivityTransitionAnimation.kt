//noinspection MissingCopyrightHeader #8659

package com.ichi2.anim

import android.app.Activity
import android.content.Context
import android.util.LayoutDirection
import androidx.core.app.ActivityOptionsCompat
import com.ichi2.anki.R

object ActivityTransitionAnimation {
    @JvmStatic
    fun slide(activity: Activity, direction: Direction?) {
        when (direction) {
            Direction.START -> if (isRightToLeft(activity)) {
                activity.overridePendingTransition(R.anim.slide_right_in, R.anim.slide_right_out)
            } else {
                activity.overridePendingTransition(R.anim.slide_left_in, R.anim.slide_left_out)
            }
            Direction.END -> if (isRightToLeft(activity)) {
                activity.overridePendingTransition(R.anim.slide_left_in, R.anim.slide_left_out)
            } else {
                activity.overridePendingTransition(R.anim.slide_right_in, R.anim.slide_right_out)
            }
            Direction.FADE -> activity.overridePendingTransition(R.anim.fade_out, R.anim.fade_in)
            Direction.UP -> activity.overridePendingTransition(R.anim.slide_up_in, R.anim.slide_up_out)
            Direction.DIALOG_EXIT -> activity.overridePendingTransition(R.anim.none, R.anim.dialog_exit)
            Direction.NONE -> activity.overridePendingTransition(R.anim.none, R.anim.none)
            Direction.DOWN -> {
            }
            else -> {
            }
        }
    }

    @JvmStatic
    fun getAnimationOptions(activity: Activity, direction: Direction?): ActivityOptionsCompat {
        return when (direction) {
            Direction.START -> if (isRightToLeft(activity)) ActivityOptionsCompat.makeCustomAnimation(activity, R.anim.slide_right_in, R.anim.slide_right_out) else ActivityOptionsCompat.makeCustomAnimation(activity, R.anim.slide_left_in, R.anim.slide_left_out)
            Direction.END -> if (isRightToLeft(activity)) ActivityOptionsCompat.makeCustomAnimation(activity, R.anim.slide_left_in, R.anim.slide_left_out) else ActivityOptionsCompat.makeCustomAnimation(activity, R.anim.slide_right_in, R.anim.slide_right_out)
            Direction.FADE -> ActivityOptionsCompat.makeCustomAnimation(activity, R.anim.fade_out, R.anim.fade_in)
            Direction.UP -> ActivityOptionsCompat.makeCustomAnimation(activity, R.anim.slide_up_in, R.anim.slide_up_out)
            Direction.DIALOG_EXIT -> ActivityOptionsCompat.makeCustomAnimation(activity, R.anim.none, R.anim.dialog_exit)
            Direction.NONE -> ActivityOptionsCompat.makeCustomAnimation(activity, R.anim.none, R.anim.none)
            Direction.DOWN -> // this is the default animation, we shouldn't try to override it
                ActivityOptionsCompat.makeBasic()
            else -> ActivityOptionsCompat.makeBasic()
        }
    }

    private fun isRightToLeft(c: Context): Boolean {
        return c.resources.configuration.layoutDirection == LayoutDirection.RTL
    }

    enum class Direction {
        START, END, FADE, UP, DOWN, DIALOG_EXIT, NONE
    }
}
