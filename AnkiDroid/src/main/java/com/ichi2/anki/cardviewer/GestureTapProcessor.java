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

import static com.ichi2.anki.cardviewer.ViewerCommand.COMMAND_NOTHING;

public class GestureTapProcessor {
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

    private boolean mUseCornerTouch;


    public void init(SharedPreferences preferences) {
        mGestureTapLeft = Integer.parseInt(preferences.getString("gestureTapLeft", "3"));
        mGestureTapRight = Integer.parseInt(preferences.getString("gestureTapRight", "6"));
        mGestureTapTop = Integer.parseInt(preferences.getString("gestureTapTop", "12"));
        mGestureTapBottom = Integer.parseInt(preferences.getString("gestureTapBottom", "2"));

        mUseCornerTouch = preferences.getBoolean("gestureCornerTouch", false);
        if (mUseCornerTouch) {
            mGestureTapTopLeft = Integer.parseInt(preferences.getString("gestureTapTopLeft", "0"));
            mGestureTapTopRight = Integer.parseInt(preferences.getString("gestureTapTopRight", "0"));
            mGestureTapCenter = Integer.parseInt(preferences.getString("gestureTapCenter", "0"));
            mGestureTapBottomLeft = Integer.parseInt(preferences.getString("gestureTapBottomLeft", "0"));
            mGestureTapBottomRight = Integer.parseInt(preferences.getString("gestureTapBottomRight", "0"));
        }
    }

    @ViewerCommand.ViewerCommandDef
    public int getCommandFromTap(int height, int width, float posX, float posY) {
        if (width == 0 || height == 0) {
            return COMMAND_NOTHING;
        }

        if (mUseCornerTouch) {
            return getCornerTouchCommand(height, width, posX, posY);
        }
        return getFourCornerTap(height, width, posX, posY);
    }


    public int getCornerTouchCommand(int height, int width, float posX, float posY) {
        GestureSegment segment = GestureSegment.fromTap(height, width, posX, posY);

        switch (segment) {
            case TOP_LEFT: return mGestureTapTopLeft;
            case TOP_CENTER: return mGestureTapTop;
            case TOP_RIGHT: return mGestureTapTopRight;
            case MIDDLE_LEFT: return mGestureTapLeft;
            case MIDDLE_CENTER: return mGestureTapCenter;
            case MIDDLE_RIGHT: return mGestureTapRight;
            case BOTTOM_LEFT: return mGestureTapBottomLeft;
            case BOTTOM_CENTER: return mGestureTapBottom;
            case BOTTOM_RIGHT: return mGestureTapBottomRight;
            default: throw new IllegalStateException("invalid switch");
        }

    }


    private int getFourCornerTap(int height, int width, float posX, float posY) {
        boolean gestureIsRight = posY > height * (1 - posX / width);
        if (posX > posY / height * width) {
            if (gestureIsRight) {
                return mGestureTapRight;
            } else {
                return mGestureTapTop;
            }
        } else {
            if (gestureIsRight) {
                return mGestureTapBottom;
            } else {
                return mGestureTapLeft;
            }
        }
    }


    public enum GestureSegment {
        TOP_LEFT,
        TOP_CENTER,
        TOP_RIGHT,
        MIDDLE_LEFT,
        MIDDLE_CENTER,
        MIDDLE_RIGHT,
        BOTTOM_LEFT,
        BOTTOM_CENTER,
        BOTTOM_RIGHT;

        public static GestureSegment fromTap(int height, int width, float posX, float posY) {

            double heightSegment = height / 3d;
            double widthSegment = width / 3d;

            TriState wSector = clamp(posX / widthSegment);
            TriState hSector = clamp(posY / heightSegment);

            switch (wSector) {
                case LOW:
                    //left
                    switch (hSector) {
                        case LOW:
                            return TOP_LEFT;
                        case MID:
                            return MIDDLE_LEFT;
                        case HIGH:
                            return BOTTOM_LEFT;
                    }
                    break;
                case MID:
                    //center
                    switch (hSector) {
                        case LOW:
                            return TOP_CENTER;
                        case MID:
                            return MIDDLE_CENTER;
                        case HIGH:
                            return BOTTOM_CENTER;
                    }
                    break;
                case HIGH:
                    //Right
                    switch (hSector) {
                        case LOW:
                            return TOP_RIGHT;
                        case MID:
                            return MIDDLE_RIGHT;
                        case HIGH:
                            return BOTTOM_RIGHT;
                    }
                    break;
                default:
                    throw new IllegalArgumentException("illegal switch state");
            }
            throw new IllegalArgumentException("illegal switch state");
        }

        // clamps the value from LOW-MID-HIGH
        private static TriState clamp(double value) {
            double val = Math.floor(value);
            if (val >= 2) {
                return TriState.HIGH;
            }
            if (val < 1) {
                return TriState.LOW;
            }

            return TriState.MID;
        }

        private enum TriState {
            LOW,
            MID,
            HIGH
        }
    }
}
