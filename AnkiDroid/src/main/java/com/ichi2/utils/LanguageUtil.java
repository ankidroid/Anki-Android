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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Utility call for proving language related functionality.
 */
public class LanguageUtil {

    /** A list of all languages supported by AnkiDroid
     * Please modify LanguageUtilsLanguageRegressionTest if changing */
    public static final String[] APP_LANGUAGES = {"af", "am", "ar", "az", "be", "bg", "bn", "ca", "ckb", "cs", "da",
            "de", "el", "en", "eo", "es-AR", "es-ES", "et", "eu", "fa", "fi", "fil", "fr", "fy-NL", "ga-IE", "gl", "got",
            "gu-IN", "he", "hi", "hr", "hu", "hy-AM", "id", "is", "it", "ja", "jv", "ka", "kk", "km", "ko", "ku",
            "ky", "lt", "lv", "mk", "mn", "mr", "ms", "my", "nl", "nn-NO", "no", "pa-IN", "pl", "pt-BR", "pt-PT",
            "ro", "ru", "sk", "sl", "sq", "sr", "ss", "sv-SE", "sw", "ta", "te", "tg", "th", "ti", "tl", "tn", "tr",
            "ts", "tt-RU", "uk", "ur-PK", "uz", "ve", "vi", "wo", "xh", "yu", "zh-CN", "zh-TW", "zu" };


    /**
     * Returns the {@link Locale} for the given code or the default locale, if no code is given.
     *
     * @return The {@link Locale} for the given code
     */
    @NonNull
    public static Locale getLocale() {
        return getLocale("");
    }

    /**
     * Returns the {@link Locale} for the given code or the default locale, if no code is given.
     *
     * @param localeCode The locale code of the language
     * @return The {@link Locale} for the given code
     */
    @NonNull
    public static Locale getLocale(@Nullable String localeCode) {
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


    @NonNull
    public static String getShortDateFormatFromMs(long ms) {
        return DateFormat.getDateInstance(DateFormat.SHORT, getLocale()).format(new Date(ms));
    }


    @NonNull
    public static String getShortDateFormatFromS(long s) {
        return DateFormat.getDateInstance(DateFormat.SHORT, getLocale()).format(new Date(s * 1000L));
    }

}
