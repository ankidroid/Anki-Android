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

package com.ichi2.anki.model;

import android.content.SharedPreferences;

import com.ichi2.anki.cardviewer.CardAppearance;

import androidx.annotation.CheckResult;
import androidx.annotation.Nullable;

public class WhiteboardPenColor {
    private final Integer mLightPenColor;
    private final Integer mDarkPenColor;


    public WhiteboardPenColor(Integer lightPenColor, Integer darkPenColor) {
        this.mLightPenColor = lightPenColor;
        this.mDarkPenColor = darkPenColor;
    }

    @CheckResult
    public static WhiteboardPenColor getDefault() {
        return new WhiteboardPenColor(null, null);
    }

    @Nullable
    public Integer getLightPenColor() {
        return mLightPenColor;
    }

    @Nullable
    public Integer getDarkPenColor() {
        return mDarkPenColor;
    }

    @Nullable
    public Integer fromPreferences(SharedPreferences sharedPrefs) {
        boolean isInNightMode = CardAppearance.isInNightMode(sharedPrefs);
        if (isInNightMode) {
            return getDarkPenColor();
        } else {
            return getLightPenColor();
        }
    }
}
