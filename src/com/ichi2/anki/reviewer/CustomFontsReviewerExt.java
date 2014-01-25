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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.AnkiFont;
import com.ichi2.libanki.Utils;
import com.ichi2.themes.Themes;

public class CustomFontsReviewerExt implements ReviewerExt {

    private final String mCustomStyle;
    private final boolean mSupportsQuickUpdate;

    public CustomFontsReviewerExt(Context context) {
        Map<String, AnkiFont> customFontsMap = getCustomFontsMap(context);
        mCustomStyle = getCustomDefaultFontStyle(context, customFontsMap) + getCustomFontsStyle(customFontsMap);
        mSupportsQuickUpdate = customFontsMap.size() == 0;
    }


    @Override
    public void updateCssStyle(StringBuilder cssStyle) {
        cssStyle.append(mCustomStyle);
    }

    @Override
    public boolean supportsQuickUpdate() {
        return mSupportsQuickUpdate;
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


    /** Returns the CSS used to set the default font. */
    private static String getCustomDefaultFontStyle(Context context, Map<String, AnkiFont> customFontsMap) {
        SharedPreferences preferences = AnkiDroidApp.getSharedPrefs(context);
        AnkiFont defaultFont = customFontsMap.get(preferences.getString("defaultFont", null));
        if (defaultFont != null) {
            return "BODY { " + defaultFont.getCSS() + " }\n";
        } else {
            String defaultFontName = Themes.getReviewerFontName();
            if (TextUtils.isEmpty(defaultFontName)) {
                return "";
            } else {
                return String.format(
                        "BODY {"
                        + "font-family: '%s';"
                        + "font-weight: normal;"
                        + "font-style: normal;"
                        + "font-stretch: normal;"
                        + "}\n", defaultFontName);
            }
        }
    }


    /**
     * Returns a map from custom fonts names to the corresponding {@link AnkiFont} object.
     *
     * <p>The list of constructed lazily the first time is needed.
     */
    private static Map<String, AnkiFont> getCustomFontsMap(Context context) {
        List<AnkiFont> fonts = Utils.getCustomFonts(context);
        Map<String, AnkiFont> customFontsMap = new HashMap<String, AnkiFont>();
        for (AnkiFont f : fonts) {
            customFontsMap.put(f.getName(), f);
        }
        return customFontsMap;
    }


}
