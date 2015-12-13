/***************************************************************************************
 * Copyright (c) 2011 Norbert Nagold <norbert.nagold@gmail.com>                         *
 * Copyright (c) 2015 Timothy Rae <perceptualchaos2@gmail.com>                          *
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

package com.ichi2.themes;


import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.widget.Toast;

import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.R;

public class Themes {
    public final static int ALPHA_ICON_ENABLED_LIGHT = 255; // 100%
    public final static int ALPHA_ICON_DISABLED_LIGHT = 76; // 31%
    public final static int ALPHA_ICON_ENABLED_DARK = 138; // 54%

    public static void showThemedToast(Context context, String text, boolean shortLength) {
        Toast.makeText(context, text, shortLength ? Toast.LENGTH_SHORT : Toast.LENGTH_LONG).show();
    }

    public static void setTheme(Context context) {
        if (AnkiDroidApp.getSharedPrefs(context.getApplicationContext()).getBoolean("invertedColors", false)) {
            context.setTheme(R.style.App_Theme_Dark);
        } else {
            context.setTheme(R.style.App_Theme_White);
        }
    }

    public static void setThemeLegacy(Context context) {
        if (AnkiDroidApp.getSharedPrefs(context.getApplicationContext()).getBoolean("invertedColors", false)) {
            context.setTheme(R.style.LegacyActionBarDark);
        } else {
            context.setTheme(R.style.LegacyActionBarWhite);
        }
    }


    public static int getResFromAttr(Context context, int resAttr) {
        int[] attrs = new int[] {resAttr};
        return getResFromAttr(context, attrs)[0];
    }

    public static int[] getResFromAttr(Context context, int[] attrs) {
        TypedArray ta = context.obtainStyledAttributes(attrs);
        for (int i = 0; i < attrs.length; i++) {
            attrs[i] = ta.getResourceId(i, 0);
        }
        ta.recycle();
        return attrs;
    }

    public static int getColorFromAttr(Context context, int colorAttr) {
        int[] attrs = new int[] {colorAttr};
        return getColorFromAttr(context, attrs)[0];
    }


    public static int[] getColorFromAttr(Context context, int[] attrs) {
        TypedArray ta = context.obtainStyledAttributes(attrs);
        Resources res = context.getResources();
        for (int i = 0; i < attrs.length; i++) {
            attrs[i] = ta.getColor(i, res.getColor(R.color.white));
        }
        ta.recycle();
        return attrs;
    }

}
