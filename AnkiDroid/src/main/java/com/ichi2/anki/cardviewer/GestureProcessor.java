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

package com.ichi2.anki.cardviewer;

import android.content.SharedPreferences;

import com.ichi2.anki.reviewer.GestureMapper;

import static com.ichi2.anki.cardviewer.ViewerCommand.COMMAND_NOTHING;

public class GestureProcessor {
    @ViewerCommand.ViewerCommandDef
    private int mGestureDoubleTap;
    @ViewerCommand.ViewerCommandDef
    private int mGestureLongclick;
    @ViewerCommand.ViewerCommandDef
    private int mGestureSwipeUp;
    @ViewerCommand.ViewerCommandDef
    private int mGestureSwipeDown;
    @ViewerCommand.ViewerCommandDef
    private int mGestureSwipeLeft;
    @ViewerCommand.ViewerCommandDef
    private int mGestureSwipeRight;

    private final GestureMapper mGestureMapper = new GestureMapper();


    public void init(SharedPreferences preferences) {
        mGestureDoubleTap = Integer.parseInt(preferences.getString("gestureDoubleTap", "7"));
        mGestureLongclick = Integer.parseInt(preferences.getString("gestureLongclick", "11"));

        mGestureSwipeUp = Integer.parseInt(preferences.getString("gestureSwipeUp", "9"));
        mGestureSwipeDown = Integer.parseInt(preferences.getString("gestureSwipeDown", "0"));
        mGestureSwipeLeft = Integer.parseInt(preferences.getString("gestureSwipeLeft", "8"));
        mGestureSwipeRight = Integer.parseInt(preferences.getString("gestureSwipeRight", "17"));

        mGestureMapper.init(preferences);
    }

    @ViewerCommand.ViewerCommandDef
    public int getCommandFromTap(int height, int width, float posX, float posY) {
        return mGestureMapper.getCommandFromTap(height, width, posX, posY);
    }

    @ViewerCommand.ViewerCommandDef
    public int getDoubleTap() {
        return mGestureDoubleTap;
    }


    public int onLongTap() {
        return mGestureLongclick;
    }

    @ViewerCommand.ViewerCommandDef
    public int getCommandFromFling(float dx, float dy, float velocityX, float velocityY, boolean isSelecting, boolean isXScrolling, boolean isYScrolling) {
        Gesture gesture = this.mGestureMapper.gesture(dx, dy, velocityX, velocityY, isSelecting, isXScrolling, isYScrolling);

        switch (gesture) {
            case SWIPE_UP: return mGestureSwipeUp;
            case SWIPE_DOWN: return mGestureSwipeDown;
            case SWIPE_LEFT: return mGestureSwipeLeft;
            case SWIPE_RIGHT: return mGestureSwipeRight;
            default: return COMMAND_NOTHING;
        }
    }
}
