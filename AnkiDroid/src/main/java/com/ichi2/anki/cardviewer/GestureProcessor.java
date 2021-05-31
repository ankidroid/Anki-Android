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

public class GestureProcessor {
    @ViewerCommand.ViewerCommandDef
    private int mGestureDoubleTap;
    @ViewerCommand.ViewerCommandDef
    private int mGestureLongclick;

    private final GestureMapper mGestureMapper = new GestureMapper();


    public void init(SharedPreferences preferences) {
        mGestureDoubleTap = Integer.parseInt(preferences.getString("gestureDoubleTap", "7"));
        mGestureLongclick = Integer.parseInt(preferences.getString("gestureLongclick", "11"));
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
        return this.mGestureMapper.gesture(dx, dy, velocityX, velocityY, isSelecting, isXScrolling, isYScrolling);
    }
}
