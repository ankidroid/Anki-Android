/****************************************************************************************
 * Copyright (c) 2013 Flavio Lerda <flerda@gmail.com>                                   *
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

package com.ichi2.anki.reviewer;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.AnkiFont;
import com.ichi2.libanki.Utils;
import com.ichi2.utils.HashUtil;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ReviewerCustomFonts {

    private final String mCustomStyle;
    private String mDefaultFontStyle;
    private String mOverrideFontStyle;
    private String mThemeFontStyle;
    private String mDominantFontStyle;

    public ReviewerCustomFonts(Context context) {
        Map<String, AnkiFont> customFontsMap = getCustomFontsMap(context);
        mCustomStyle = getCustomFontsStyle(customFontsMap) + getDominantFontStyle(context, customFontsMap);
    }

    public void updateCssStyle(StringBuilder cssStyle) {
        cssStyle.append(mCustomStyle);
    }


    /**
     * Returns the CSS used to handle custom fonts.
     * <p>
     * Custom fonts live in fonts directory in the directory used to store decks.
     * <p>
     * Each font is mapped to the font family by the same name as the name of the font without the extension.
     */
    private static String getCustomFontsStyle(Map<String, AnkiFont> customFontsMap) {
        StringBuilder builder = new StringBuilder();
        for (AnkiFont font : customFontsMap.values()) {
            builder.append(font.getDeclaration());
            builder.append('\n');
        }
        return builder.toString();
    }


    /**
     * Returns the CSS used to set the theme font.
     * 
     * @return the font style, or the empty string if no font is set
     */
    private String getThemeFontStyle() {
        if (mThemeFontStyle == null) {
            String themeFontName = "OpenSans";
            if (TextUtils.isEmpty(themeFontName)) {
                mThemeFontStyle = "";
            } else {
                mThemeFontStyle = String.format(
                        "BODY {"
                        + "font-family: '%s';"
                        + "font-weight: normal;"
                        + "font-style: normal;"
                        + "font-stretch: normal;"
                        + "}\n", themeFontName);
            }
        }
        return mThemeFontStyle;
    }


    /**
     * Returns the CSS used to set the default font.
     * 
     * @return the default font style, or the empty string if no default font is set
     */
    private String getDefaultFontStyle(Context context, Map<String, AnkiFont> customFontsMap) {
        if (mDefaultFontStyle == null) {
            SharedPreferences preferences = AnkiDroidApp.getSharedPrefs(context);
            AnkiFont defaultFont = customFontsMap.get(preferences.getString("defaultFont", null));
            if (defaultFont != null) {
                mDefaultFontStyle = "BODY { " + defaultFont.getCSS(false) + " }\n";
            } else {
                mDefaultFontStyle = "";
            }
        }
        return mDefaultFontStyle;
    }


    /**
     * Returns the CSS used to set the override font.
     * 
     * @return the override font style, or the empty string if no override font is set
     */
    private String getOverrideFontStyle(Context context, Map<String, AnkiFont> customFontsMap) {
        if (mOverrideFontStyle == null) {
            SharedPreferences preferences = AnkiDroidApp.getSharedPrefs(context);
            AnkiFont defaultFont = customFontsMap.get(preferences.getString("defaultFont", null));
            boolean overrideFont = "1".equals(preferences.getString("overrideFontBehavior", "0"));
            if (defaultFont != null && overrideFont) {
                mOverrideFontStyle = "BODY, .card, * { " + defaultFont.getCSS(true) + " }\n";
            } else {
                mOverrideFontStyle = "";
            }
        }
        return mOverrideFontStyle;
    }


    /**
     * Returns the CSS that determines font choice in a global fashion.
     * 
     * @return the font style, or the empty string if none applies
     */
    private String getDominantFontStyle(Context context, Map<String, AnkiFont> customFontsMap) {
        if (mDominantFontStyle == null) {
            mDominantFontStyle = getOverrideFontStyle(context, customFontsMap);
            if (TextUtils.isEmpty(mDominantFontStyle)) {
                mDominantFontStyle = getDefaultFontStyle(context, customFontsMap);
                if (TextUtils.isEmpty(mDominantFontStyle)) {
                    mDominantFontStyle = getThemeFontStyle();
                }
            }
        }
        return mDominantFontStyle;
    }


    /**
     * Returns a map from custom fonts names to the corresponding {@link AnkiFont} object.
     * <p>
     * The list of constructed lazily the first time is needed.
     */
    private static Map<String, AnkiFont> getCustomFontsMap(Context context) {
        List<AnkiFont> fonts = Utils.getCustomFonts(context);
        Map<String, AnkiFont> customFontsMap = HashUtil.HashMapInit(fonts.size());
        for (AnkiFont f : fonts) {
            customFontsMap.put(f.getName(), f);
        }
        return customFontsMap;
    }

}
