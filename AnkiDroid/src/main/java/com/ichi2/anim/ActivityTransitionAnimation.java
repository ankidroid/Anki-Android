
package com.ichi2.anim;

import android.annotation.TargetApi;
import android.app.Activity;

import com.ichi2.anki.R;

public class ActivityTransitionAnimation {
    public static int LEFT = 0;
    public static int RIGHT = 1;
    public static int FADE = 2;
    public static int UP = 3;
    public static int DOWN = 4;
    public static int DIALOG_EXIT = 5;
    public static int NONE = 6;


    @TargetApi(5)
    public static void slide(Activity activity, int direction) {
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
}
