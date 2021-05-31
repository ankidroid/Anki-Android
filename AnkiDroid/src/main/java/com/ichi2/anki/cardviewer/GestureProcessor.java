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
    @ViewerCommand.ViewerCommandDef
    private int mGestureTapLeft;
    @ViewerCommand.ViewerCommandDef
    private int mGestureTapRight;
    @ViewerCommand.ViewerCommandDef
    private int mGestureTapTop;
    @ViewerCommand.ViewerCommandDef
    private int mGestureTapBottom;
    @ViewerCommand.ViewerCommandDef
    private int mGestureTapTopLeft;
    @ViewerCommand.ViewerCommandDef
    private int mGestureTapTopRight;
    @ViewerCommand.ViewerCommandDef
    private int mGestureTapCenter;
    @ViewerCommand.ViewerCommandDef
    private int mGestureTapBottomLeft;
    @ViewerCommand.ViewerCommandDef
    private int mGestureTapBottomRight;

    private final GestureMapper mGestureMapper = new GestureMapper();


    public void init(SharedPreferences preferences) {
        mGestureDoubleTap = Integer.parseInt(preferences.getString("gestureDoubleTap", "7"));
        mGestureLongclick = Integer.parseInt(preferences.getString("gestureLongclick", "11"));

        mGestureSwipeUp = Integer.parseInt(preferences.getString("gestureSwipeUp", "9"));
        mGestureSwipeDown = Integer.parseInt(preferences.getString("gestureSwipeDown", "0"));
        mGestureSwipeLeft = Integer.parseInt(preferences.getString("gestureSwipeLeft", "8"));
        mGestureSwipeRight = Integer.parseInt(preferences.getString("gestureSwipeRight", "17"));

        mGestureMapper.init(preferences);

        mGestureTapLeft = Integer.parseInt(preferences.getString("gestureTapLeft", "3"));
        mGestureTapRight = Integer.parseInt(preferences.getString("gestureTapRight", "6"));
        mGestureTapTop = Integer.parseInt(preferences.getString("gestureTapTop", "12"));
        mGestureTapBottom = Integer.parseInt(preferences.getString("gestureTapBottom", "2"));

        boolean useCornerTouch = preferences.getBoolean("gestureCornerTouch", false);
        if (useCornerTouch) {
            mGestureTapTopLeft = Integer.parseInt(preferences.getString("gestureTapTopLeft", "0"));
            mGestureTapTopRight = Integer.parseInt(preferences.getString("gestureTapTopRight", "0"));
            mGestureTapCenter = Integer.parseInt(preferences.getString("gestureTapCenter", "0"));
            mGestureTapBottomLeft = Integer.parseInt(preferences.getString("gestureTapBottomLeft", "0"));
            mGestureTapBottomRight = Integer.parseInt(preferences.getString("gestureTapBottomRight", "0"));
        }
    }

    @ViewerCommand.ViewerCommandDef
    public int getCommandFromTap(int height, int width, float posX, float posY) {
        Gesture gesture = mGestureMapper.gesture(height, width, posX, posY);

        if (gesture == null) {
            return COMMAND_NOTHING;
        }

        return mapGestureToCommand(gesture);
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

        return mapGestureToCommand(gesture);
    }


    protected int mapGestureToCommand(Gesture gesture) {
        switch (gesture) {
            case SWIPE_UP: return mGestureSwipeUp;
            case SWIPE_DOWN: return mGestureSwipeDown;
            case SWIPE_LEFT: return mGestureSwipeLeft;
            case SWIPE_RIGHT: return mGestureSwipeRight;
            case TAP_TOP: return mGestureTapTop;
            case TAP_TOP_LEFT: return mGestureTapTopLeft;
            case TAP_TOP_RIGHT: return mGestureTapTopRight;
            case TAP_LEFT: return mGestureTapLeft;
            case TAP_CENTER: return mGestureTapCenter;
            case TAP_RIGHT: return mGestureTapRight;
            case TAP_BOTTOM: return mGestureTapBottom;
            case TAP_BOTTOM_LEFT: return mGestureTapBottomLeft;
            case TAP_BOTTOM_RIGHT: return mGestureTapBottomRight;
            default: return COMMAND_NOTHING;
        }
    }
}
