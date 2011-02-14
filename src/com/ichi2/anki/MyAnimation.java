package com.ichi2.anki;

import android.app.Activity;

public class MyAnimation {
	public static int LEFT = 1;
	public static int RIGHT = 2;
	public static int FADE = 3;
	
	public static void slide(Activity activity, int direction) {
		if (direction == LEFT) {
			activity.overridePendingTransition(R.anim.slide_left_in, R.anim.slide_left_out);
		} else if (direction == RIGHT) {
			activity.overridePendingTransition(R.anim.slide_right_in, R.anim.slide_right_out);
		} else if (direction == FADE) {
			activity.overridePendingTransition(R.anim.fade_out, R.anim.fade_in);
		}
	}
}