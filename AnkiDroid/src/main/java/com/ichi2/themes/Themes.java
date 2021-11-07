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
import android.content.SharedPreferences;
import android.content.res.TypedArray;

import androidx.annotation.IntDef;
import androidx.core.content.ContextCompat;

import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.R;

import java.lang.annotation.Retention;

import static java.lang.annotation.RetentionPolicy.SOURCE;

public class Themes {
    public final static int ALPHA_ICON_ENABLED_LIGHT = 255; // 100%
    public final static int ALPHA_ICON_DISABLED_LIGHT = 76; // 31%
    public final static int ALPHA_ICON_ENABLED_DARK = 138; // 54%

    /**
     * Preferences for the night theme mode
     */
    private static final String DAY_THEME = "dayTheme";
    // Day themes
    public final static int THEME_DAY_LIGHT = 0;
    public final static int THEME_DAY_PLAIN = 1;
    @Retention(SOURCE)
    @IntDef({THEME_DAY_LIGHT, THEME_DAY_PLAIN})
    public @interface DAY_THEME {}


    /**
     * Preferences for the night theme mode
     */
    private static final String NIGHT_THEME = "nightTheme";
    // Night themes
    public final static int THEME_NIGHT_BLACK = 0;
    public final static int THEME_NIGHT_DARK = 1;

    @Retention(SOURCE)
    @IntDef({THEME_NIGHT_BLACK, THEME_NIGHT_DARK})
    public @interface NIGHT_THEME {}

    public static void setTheme(Context context) {
        SharedPreferences prefs = AnkiDroidApp.getSharedPrefs(context.getApplicationContext());
        if (prefs.getBoolean("invertedColors", false)) {
            int theme = Integer.parseInt(prefs.getString(NIGHT_THEME, Integer.toString(THEME_NIGHT_BLACK)));
            switch (theme) {
                case THEME_NIGHT_DARK:
                    context.setTheme(R.style.Theme_Dark_Compat);
                    break;
                case THEME_NIGHT_BLACK:
                    context.setTheme(R.style.Theme_Black_Compat);
                    break;
            }
        } else {
            int theme = Integer.parseInt(prefs.getString(DAY_THEME, Integer.toString(THEME_DAY_LIGHT)));
            switch (theme) {
                case THEME_DAY_LIGHT:
                    context.setTheme(R.style.Theme_Light_Compat);
                    break;
                case THEME_DAY_PLAIN:
                    context.setTheme(R.style.Theme_Plain_Compat);
                    break;
            }
        }
        disableXiaomiForceDarkMode(context);
    }

    public static void setThemeLegacy(Context context) {
        SharedPreferences prefs = AnkiDroidApp.getSharedPrefs(context.getApplicationContext());
        if (prefs.getBoolean("invertedColors", false)) {
            int theme = Integer.parseInt(prefs.getString(NIGHT_THEME, Integer.toString(THEME_NIGHT_BLACK)));
            switch (theme) {
                case THEME_NIGHT_DARK:
                    context.setTheme(R.style.LegacyActionBarDark);
                    break;
                case THEME_NIGHT_BLACK:
                    context.setTheme(R.style.LegacyActionBarBlack);
                    break;
            }
        } else {
            int theme = Integer.parseInt(prefs.getString(DAY_THEME, Integer.toString(THEME_DAY_LIGHT)));
            switch (theme) {
                case THEME_DAY_LIGHT:
                    context.setTheme(R.style.LegacyActionBarLight);
                    break;
                case THEME_DAY_PLAIN:
                    context.setTheme(R.style.LegacyActionBarPlain);
                    break;
            }
        }
        disableXiaomiForceDarkMode(context);
    }


    /**
     * #8150: Fix icons not appearing in Note Editor due to MIUI 12's "force dark" mode
     */
    public static void disableXiaomiForceDarkMode(Context context) {
        // Setting a theme is an additive operation, so this adds a single property.
        context.setTheme(R.style.ThemeOverlay_Xiaomi);
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
        for (int i = 0; i < attrs.length; i++) {
            attrs[i] = ta.getColor(i, ContextCompat.getColor(context, R.color.white));
        }
        ta.recycle();
        return attrs;
    }

    /**
     * Return the current integer code of the theme being used, taking into account
     * whether we are in day mode or night mode.
     */
    public static int getCurrentTheme(Context context) {
        SharedPreferences prefs = AnkiDroidApp.getSharedPrefs(context);
        if (prefs.getBoolean("invertedColors", false)) {
            return Integer.parseInt(prefs.getString(NIGHT_THEME, Integer.toString(THEME_NIGHT_BLACK)));
        } else {
            return Integer.parseInt(prefs.getString(DAY_THEME, Integer.toString(THEME_DAY_LIGHT)));
        }
    }
}
