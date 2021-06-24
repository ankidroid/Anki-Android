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
    private ViewerCommand mGestureDoubleTap;
    private ViewerCommand mGestureLongclick;
    private ViewerCommand mGestureSwipeUp;
    private ViewerCommand mGestureSwipeDown;
    private ViewerCommand mGestureSwipeLeft;
    private ViewerCommand mGestureSwipeRight;
    private ViewerCommand mGestureTapLeft;
    private ViewerCommand mGestureTapRight;
    private ViewerCommand mGestureTapTop;
    private ViewerCommand mGestureTapBottom;
    private ViewerCommand mGestureTapTopLeft;
    private ViewerCommand mGestureTapTopRight;
    private ViewerCommand mGestureTapCenter;
    private ViewerCommand mGestureTapBottomLeft;
    private ViewerCommand mGestureTapBottomRight;

    private final GestureMapper mGestureMapper = new GestureMapper();

    private final ViewerCommand.CommandProcessor mProcessor;

    private boolean mEnabled = false;

    public GestureProcessor(ViewerCommand.CommandProcessor processor) {
        mProcessor = processor;
    }

    public void init(SharedPreferences preferences) {
        mEnabled = preferences.getBoolean("gestures", false);
        mGestureDoubleTap = ViewerCommand.fromString(preferences.getString("gestureDoubleTap", "7"));
        mGestureLongclick = ViewerCommand.fromString(preferences.getString("gestureLongclick", "11"));

        mGestureSwipeUp = ViewerCommand.fromString(preferences.getString("gestureSwipeUp", "9"));
        mGestureSwipeDown = ViewerCommand.fromString(preferences.getString("gestureSwipeDown", "0"));
        mGestureSwipeLeft = ViewerCommand.fromString(preferences.getString("gestureSwipeLeft", "8"));
        mGestureSwipeRight = ViewerCommand.fromString(preferences.getString("gestureSwipeRight", "17"));

        mGestureMapper.init(preferences);

        mGestureTapLeft = ViewerCommand.fromString(preferences.getString("gestureTapLeft", "3"));
        mGestureTapRight = ViewerCommand.fromString(preferences.getString("gestureTapRight", "6"));
        mGestureTapTop = ViewerCommand.fromString(preferences.getString("gestureTapTop", "12"));
        mGestureTapBottom = ViewerCommand.fromString(preferences.getString("gestureTapBottom", "2"));

        boolean useCornerTouch = preferences.getBoolean("gestureCornerTouch", false);
        if (useCornerTouch) {
            mGestureTapTopLeft = ViewerCommand.fromString(preferences.getString("gestureTapTopLeft", "0"));
            mGestureTapTopRight = ViewerCommand.fromString(preferences.getString("gestureTapTopRight", "0"));
            mGestureTapCenter = ViewerCommand.fromString(preferences.getString("gestureTapCenter", "0"));
            mGestureTapBottomLeft = ViewerCommand.fromString(preferences.getString("gestureTapBottomLeft", "0"));
            mGestureTapBottomRight = ViewerCommand.fromString(preferences.getString("gestureTapBottomRight", "0"));
        }
    }

    public boolean onTap(int height, int width, float posX, float posY) {
        Gesture gesture = mGestureMapper.gesture(height, width, posX, posY);

        if (gesture == null) {
            return false;
        }

        return execute(gesture);
    }

    public boolean onDoubleTap() {
        return execute(Gesture.DOUBLE_TAP);
    }


    public boolean onLongTap() {
        return execute(Gesture.LONG_TAP);
    }

    public boolean onFling(float dx, float dy, float velocityX, float velocityY, boolean isSelecting, boolean isXScrolling, boolean isYScrolling) {
        Gesture gesture = this.mGestureMapper.gesture(dx, dy, velocityX, velocityY, isSelecting, isXScrolling, isYScrolling);

        return execute(gesture);
    }


    protected boolean execute(Gesture gesture) {
        ViewerCommand command = mapGestureToCommand(gesture);
        return mProcessor.executeCommand(command);
    }


    private ViewerCommand mapGestureToCommand(Gesture gesture) {
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
            case DOUBLE_TAP: return mGestureDoubleTap;
            case LONG_TAP: return mGestureLongclick;
            default: return COMMAND_NOTHING;
        }
    }


    /**
     * Whether the class has been enabled.
     * This requires the "gestures" preference is enabled,
     * and {@link GestureProcessor#init(SharedPreferences)} has been called
     */
    public boolean isEnabled() {
        return mEnabled;
    }


    /**
     * Whether one of the provided gestures is bound
     * @param gestures the gestures to check
     * @return <code>false</code> if none of the gestures are bound. <code>true</code> otherwise
     */
    public boolean isBound(Gesture... gestures) {
        if (!isEnabled()) {
            return false;
        }

        for (Gesture gesture : gestures) {
            if (mapGestureToCommand(gesture) != COMMAND_NOTHING) {
                return true;
            }
        }

        return false;
    }
}
