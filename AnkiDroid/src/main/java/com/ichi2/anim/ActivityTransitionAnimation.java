package com.ichi2.anim;

import android.app.Activity;

import android.content.Context;
import android.util.LayoutDirection;
import com.ichi2.anki.R;

import androidx.core.app.ActivityOptionsCompat;

public class ActivityTransitionAnimation {
    public static void slide(Activity activity, Direction direction) {
        switch (direction) {
        case START:
            if (isRightToLeft(activity)) {
               activity.overridePendingTransition(R.anim.slide_right_in, R.anim.slide_right_out);
            } else {
               activity.overridePendingTransition(R.anim.slide_left_in, R.anim.slide_left_out);
            }
            break;
        case END:
            if (isRightToLeft(activity)) {
               activity.overridePendingTransition(R.anim.slide_left_in, R.anim.slide_left_out);
            } else {
               activity.overridePendingTransition(R.anim.slide_right_in, R.anim.slide_right_out);
            }
            break;
        case FADE:
            activity.overridePendingTransition(R.anim.fade_out, R.anim.fade_in);
            break;
        case UP:
            activity.overridePendingTransition(R.anim.slide_up_in, R.anim.slide_up_out);
            break;
        case DIALOG_EXIT:
            activity.overridePendingTransition(R.anim.none, R.anim.dialog_exit);
            break;
        case NONE:
            activity.overridePendingTransition(R.anim.none, R.anim.none);
            break;
        case DOWN:
        default:
            // this is the default animation, we shouldn't try to override it
        }
    }


    public static ActivityOptionsCompat getAnimationOptions(Activity activity, Direction direction) {
        switch (direction) {
            case START:
                return isRightToLeft(activity)
                    ? ActivityOptionsCompat.makeCustomAnimation(activity, R.anim.slide_right_in, R.anim.slide_right_out)
                    : ActivityOptionsCompat.makeCustomAnimation(activity, R.anim.slide_left_in, R.anim.slide_left_out);
            case END:
                return isRightToLeft(activity)
                    ? ActivityOptionsCompat.makeCustomAnimation(activity, R.anim.slide_left_in, R.anim.slide_left_out)
                    : ActivityOptionsCompat.makeCustomAnimation(activity, R.anim.slide_right_in, R.anim.slide_right_out);
            case FADE:
                return ActivityOptionsCompat.makeCustomAnimation(activity, R.anim.fade_out, R.anim.fade_in);
            case UP:
                return ActivityOptionsCompat.makeCustomAnimation(activity, R.anim.slide_up_in, R.anim.slide_up_out);
            case DIALOG_EXIT:
                return ActivityOptionsCompat.makeCustomAnimation(activity, R.anim.none, R.anim.dialog_exit);
            case NONE:
                return ActivityOptionsCompat.makeCustomAnimation(activity, R.anim.none, R.anim.none);
            case DOWN:
            default:
                // this is the default animation, we shouldn't try to override it
                return ActivityOptionsCompat.makeBasic();
        }
    }


    public enum Direction {
        START,
        END,
        FADE,
        UP,
        DOWN,
        DIALOG_EXIT,
        NONE
    }

    private static boolean isRightToLeft(Context c)
    {
        return c.getResources().getConfiguration().getLayoutDirection() == LayoutDirection.RTL;
    }
}
