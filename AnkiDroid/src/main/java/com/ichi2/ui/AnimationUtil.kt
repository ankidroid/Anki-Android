/*
 *  Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.ui;

import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.ScaleAnimation;

public class AnimationUtil {

    // please test this on Huawei devices (if possible) and not just on the emulator -
    // having view.setVisibility(View.VISIBLE); on the expand worked fine on the emulator, but looked bad on my phone

    /** This is a fast animation - We don't want the user incorrectly selecting the current position
     * for the next collapse operation */
    public static final int DURATION_MILLIS = 200;


    public static void collapseView(View view, boolean animationEnabled) {
        view.animate().cancel();

        if (!animationEnabled) {
            view.setVisibility(View.GONE);
            return;
        }

        AnimationSet set = new AnimationSet(true);
        ScaleAnimation expandAnimation = new ScaleAnimation(
                1f, 1f,
                1f, 0.5f);
        expandAnimation.setDuration(DURATION_MILLIS);

        expandAnimation.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {

            }


            @Override
            public void onAnimationEnd(Animation animation) {
                view.setVisibility(View.GONE);
            }


            @Override
            public void onAnimationRepeat(Animation animation) {

            }
        });

        AlphaAnimation alphaAnimation = new AlphaAnimation(1.0f, 0f);
        alphaAnimation.setDuration(DURATION_MILLIS);
        alphaAnimation.setFillAfter(true);
        set.addAnimation(expandAnimation);
        set.addAnimation(alphaAnimation);

        view.startAnimation(set);
    }


    public static void expandView(View view, boolean enableAnimation) {
        view.animate().cancel();
        if (!enableAnimation) {
            view.setVisibility(View.VISIBLE);
            view.setAlpha(1.0f);
            view.setScaleY(1.0f);
            return;
        }

        // Sadly this seems necessary - yScale didn't work.
        AnimationSet set = new AnimationSet(true);
        ScaleAnimation resetEditTextScale = new ScaleAnimation(
                1f, 1f,
                1f, 1f);
        resetEditTextScale.setDuration(DURATION_MILLIS);


        AlphaAnimation alphaAnimation = new AlphaAnimation(0.0f, 1.0f);
        alphaAnimation.setFillAfter(true);
        alphaAnimation.setDuration(DURATION_MILLIS);

        set.addAnimation(resetEditTextScale);
        set.addAnimation(alphaAnimation);
        view.startAnimation(set);
        view.setVisibility(View.VISIBLE);
    }
}
