/****************************************************************************************
 *                                                                                      *
 * Copyright (c) 2021 Shridhar Goel <shridhar.goel@gmail.com>                           *
 *                                                                                      *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 *                                                                                      *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/

package com.ichi2.utils;

import android.graphics.Color;

import com.brackeys.ui.editorkit.model.ColorScheme;
import com.brackeys.ui.language.base.model.SyntaxScheme;

public class ThemeUtils {
    public static ColorScheme lightColourScheme = new ColorScheme(
            Color.parseColor("#000000"),
            Color.parseColor("#FFFFFF"),
            Color.parseColor("#313335"),
            Color.parseColor("#555555"),
            Color.parseColor("#A4A3A3"),
            Color.parseColor("#616366"),
            Color.parseColor("#FFFFFF"),
            Color.parseColor("#E8E2B7"),
            Color.parseColor("#987DAC"),
            Color.parseColor("#33654B"),
            Color.parseColor("#33654B"),
            new SyntaxScheme(
                    Color.parseColor("#6897BB"),
                    Color.parseColor("#EC1D1D"),
                    Color.parseColor("#EC7600"),
                    Color.parseColor("#EC7600"),
                    Color.parseColor("#EC7600"),
                    Color.parseColor("#C9C54E"),
                    Color.parseColor("#9378A7"),
                    Color.parseColor("#FEC76C"),
                    Color.parseColor("#6E875A"),
                    Color.parseColor("#66747B"),
                    Color.parseColor("#E2C077"),
                    Color.parseColor("#E2C077"),
                    Color.parseColor("#BABABA"),
                    Color.parseColor("#ABC16D"),
                    Color.parseColor("#6897BB")
            )
    );
}
