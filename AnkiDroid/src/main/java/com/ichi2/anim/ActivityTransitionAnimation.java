package com.ichi2.anim;

import android.annotation.TargetApi;
import android.app.Activity;

import com.ichi2.anki.R;

import static com.ichi2.anim.ActivityTransitionAnimation.Direction.*;

public class ActivityTransitionAnimation {
    @TargetApi(5)
    public static void slide(Activity activity, Direction direction) {
        if (direction == LEFT) {
            activity.overridePendingTransition(R.anim.slide_left_in, R.anim.slide_left_out);
        } else if (direction == RIGHT) {
            activity.overridePendingTransition(R.anim.slide_right_in, R.anim.slide_right_out);
        } else if (direction == FADE) {
            activity.overridePendingTransition(R.anim.fade_out, R.anim.fade_in);
        } else if (direction == UP) {
            activity.overridePendingTransition(R.anim.slide_up_in, R.anim.slide_up_out);
        } else if (direction == DOWN) {
            // this is the default animation, we shouldn't try to override it
        } else if (direction == DIALOG_EXIT) {
            activity.overridePendingTransition(R.anim.none, R.anim.dialog_exit);
        } else if (direction == NONE) {
            activity.overridePendingTransition(R.anim.none, R.anim.none);
        }
    }


    public enum Direction {
        LEFT,
        RIGHT,
        FADE,
        UP,
        DOWN,
        DIALOG_EXIT,
        NONE;
    }
}
