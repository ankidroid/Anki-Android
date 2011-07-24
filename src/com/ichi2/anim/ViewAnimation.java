package com.ichi2.anim;

import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.LayoutAnimationController;
import android.view.animation.LinearInterpolator;
import android.view.animation.TranslateAnimation;

public class ViewAnimation {

	public static final int SLIDE_IN_FROM_RIGHT = 0;
	public static final int SLIDE_OUT_TO_RIGHT = 1;
	public static final int SLIDE_IN_FROM_LEFT = 2;
	public static final int SLIDE_OUT_TO_LEFT = 3;

	public static final int FADE_IN = 0;
	public static final int FADE_OUT = 1;

	
	public static Animation slide(int type, int duration, int offset) {
        Animation animation;
        switch (type) {
        case SLIDE_IN_FROM_RIGHT:
            animation = new TranslateAnimation(
                    Animation.RELATIVE_TO_SELF, +1.0f, Animation.RELATIVE_TO_SELF, 0.0f,
                    Animation.RELATIVE_TO_SELF, 0.0f, Animation.RELATIVE_TO_SELF, 0.0f
                );
        	break;
        case SLIDE_OUT_TO_RIGHT:
            animation = new TranslateAnimation(
                    Animation.RELATIVE_TO_SELF, 0.0f, Animation.RELATIVE_TO_SELF, +1.0f,
                    Animation.RELATIVE_TO_SELF, 0.0f, Animation.RELATIVE_TO_SELF, 0.0f
                );
        	break;
        case SLIDE_IN_FROM_LEFT:
            animation = new TranslateAnimation(
                    Animation.RELATIVE_TO_SELF, -1.0f, Animation.RELATIVE_TO_SELF, 0.0f,
                    Animation.RELATIVE_TO_SELF, 0.0f, Animation.RELATIVE_TO_SELF, 0.0f
                );
        	break;
        case SLIDE_OUT_TO_LEFT:
            animation = new TranslateAnimation(
                    Animation.RELATIVE_TO_SELF, 0.0f, Animation.RELATIVE_TO_SELF, -1.0f,
                    Animation.RELATIVE_TO_SELF, 0.0f, Animation.RELATIVE_TO_SELF, 0.0f
                );
        	break;
    	default:
    		animation = null;
        }
        animation.setDuration(duration);
        animation.setStartOffset(offset);
        animation.setInterpolator(new LinearInterpolator());
        return animation;
	}


	public static Animation fade(int type, int duration, int offset) {
		float startValue = type;
        Animation animation = new AlphaAnimation(startValue, 1.0f - startValue);
        animation.setDuration(duration);
        animation.setStartOffset(offset);
        return animation;
	}
}
