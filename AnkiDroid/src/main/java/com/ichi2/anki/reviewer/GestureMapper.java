/*
 *  Copyright (c) 2021 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki.reviewer;

import android.content.SharedPreferences;
import android.view.ViewConfiguration;

import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.cardviewer.ViewerCommand;

import timber.log.Timber;

public class GestureMapper {

    private int mSwipeMinDistance = -1;
    private int mSwipeThresholdVelocity = -1;

    private static final int DEFAULT_SWIPE_MIN_DISTANCE;
    private static final int DEFAULT_SWIPE_THRESHOLD_VELOCITY;

    @ViewerCommand.ViewerCommandDef
    private int mGestureSwipeUp;
    @ViewerCommand.ViewerCommandDef
    private int mGestureSwipeDown;
    @ViewerCommand.ViewerCommandDef
    private int mGestureSwipeLeft;
    @ViewerCommand.ViewerCommandDef
    private int mGestureSwipeRight;

    static {
        // Set good default values for swipe detection
        final ViewConfiguration vc = ViewConfiguration.get(AnkiDroidApp.getInstance());
        DEFAULT_SWIPE_MIN_DISTANCE = vc.getScaledPagingTouchSlop();
        DEFAULT_SWIPE_THRESHOLD_VELOCITY = vc.getScaledMinimumFlingVelocity();
    }

    public void init(SharedPreferences preferences) {
        int sensitivity = preferences.getInt("swipeSensitivity", 100);
        if (sensitivity != 100) {
            float sens = 100.0f/sensitivity;
            mSwipeMinDistance = (int) (DEFAULT_SWIPE_MIN_DISTANCE * sens + 0.5f);
            mSwipeThresholdVelocity = (int) (DEFAULT_SWIPE_THRESHOLD_VELOCITY * sens  + 0.5f);
        } else {
            mSwipeMinDistance = DEFAULT_SWIPE_MIN_DISTANCE;
            mSwipeThresholdVelocity = DEFAULT_SWIPE_THRESHOLD_VELOCITY;
        }


        mGestureSwipeUp = Integer.parseInt(preferences.getString("gestureSwipeUp", "9"));
        mGestureSwipeDown = Integer.parseInt(preferences.getString("gestureSwipeDown", "0"));
        mGestureSwipeLeft = Integer.parseInt(preferences.getString("gestureSwipeLeft", "8"));
        mGestureSwipeRight = Integer.parseInt(preferences.getString("gestureSwipeRight", "17"));
    }

    @ViewerCommand.ViewerCommandDef
    public int gesture(float dx, float dy, float velocityX, float velocityY,
                           boolean isSelecting, boolean isXScrolling, boolean isYScrolling) {
        try {
            if (Math.abs(dx) > Math.abs(dy)) {
                // horizontal swipe if moved further in x direction than y direction
                if (dx > mSwipeMinDistance
                        && Math.abs(velocityX) > mSwipeThresholdVelocity
                        && !isXScrolling && !isSelecting) {
                    return mGestureSwipeRight;
                } else if (dx < -mSwipeMinDistance
                        && Math.abs(velocityX) > mSwipeThresholdVelocity
                        && !isXScrolling && !isSelecting) {
                    return mGestureSwipeLeft;
                }
            } else {
                // otherwise vertical swipe
                if (dy > mSwipeMinDistance
                        && Math.abs(velocityY) > mSwipeThresholdVelocity
                        && !isYScrolling) {
                    return mGestureSwipeDown;
                } else if (dy < -mSwipeMinDistance
                        && Math.abs(velocityY) > mSwipeThresholdVelocity
                        && !isYScrolling) {
                    return mGestureSwipeUp;
                }
            }
        } catch (Exception e) {
            Timber.e(e, "onFling Exception");
        }

        return ViewerCommand.COMMAND_NOTHING;
    }
}
