/****************************************************************************************
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

import android.text.TextUtils;

import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.Preferences;

import java.text.DateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Utility call for proving language related functionality.
 */
public class LanguageUtil {

    /** A list of all languages supported by AnkiDroid */
    public static final String[] APP_LANGUAGES = { "ar", "bg", "ca", "cs", "de", "el", "en", "eo", "es-AR", "es-ES", "et", "fa",
            "fi", "fr", "got", "gl", "hi", "hu", "id", "it", "ja", "ko", "lt", "nl", "nn-NO", "no", "pl", "pt_PT", "pt_BR", "ro", "ru",
            "sk", "sl", "sr", "sv", "th", "tr", "tt-RU", "uk", "vi", "zh_CN", "zh_TW" };


    /**
     * Returns the {@link Locale} for the given code or the default locale, if no code is given.
     *
     * @return The {@link Locale} for the given code
     */
    public static Locale getLocale() {
        return getLocale("");
    }

    /**
     * Returns the {@link Locale} for the given code or the default locale, if no code is given.
     *
     * @param localeCode The locale code of the language
     * @return The {@link Locale} for the given code
     */
    public static Locale getLocale(String localeCode) {
        Locale locale;
        if (localeCode == null || TextUtils.isEmpty(localeCode)) {

            localeCode = AnkiDroidApp.getSharedPrefs(AnkiDroidApp.getInstance().getBaseContext()).getString(
                    Preferences.LANGUAGE, "");
            // If no code provided use the app language.
        }
        if (TextUtils.isEmpty(localeCode)) {
            locale = Locale.getDefault();
            // Fall back to (system) default only if that fails.
        } else if (localeCode.length() > 2) {
            try {
                locale = new Locale(localeCode.substring(0, 2), localeCode.substring(3, 5));
            } catch (StringIndexOutOfBoundsException e) {
                locale = new Locale(localeCode);
            }
        } else {
            locale = new Locale(localeCode);
        }
        return locale;
    }

    public static String getShortDateFormatFromMs(long ms) {
        return DateFormat.getDateInstance(DateFormat.SHORT, getLocale()).format(new Date(ms));
    }

    public static String getShortDateFormatFromS(long s) {
        return DateFormat.getDateInstance(DateFormat.SHORT, getLocale()).format(new Date(s * 1000L));
    }

}
