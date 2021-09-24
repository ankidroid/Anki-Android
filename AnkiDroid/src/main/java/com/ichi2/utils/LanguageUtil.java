/*
 Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>

 This program is free software; you can redistribute it and/or modify it under
 the terms of the GNU General Public License as published by the Free Software
 Foundation; either version 3 of the License, or (at your option) any later
 version.

 This program is distributed in the hope that it will be useful, but WITHOUT ANY
 WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 PARTICULAR PURPOSE. See the GNU General Public License for more details.

 You should have received a copy of the GNU General Public License along with
 this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package com.ichi2.utils;

import android.content.SharedPreferences;
import android.content.res.Resources;
import android.text.TextUtils;

import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.Preferences;

import java.text.DateFormat;
import java.util.Date;
import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.os.ConfigurationCompat;
import timber.log.Timber;

/**
 * Utility call for proving language related functionality.
 */
public class LanguageUtil {

    /** A list of all languages supported by AnkiDroid
     * Please modify LanguageUtilsTest if changing
     * Please note 'yue' is special, it is 'yu' on crowdin, and mapped in import specially to 'yue' */
    public static final String[] APP_LANGUAGES = {"af", "am", "ar", "az", "be", "bg", "bn", "ca", "ckb", "cs", "da",
            "de", "el", "en", "eo", "es-AR", "es-ES", "et", "eu", "fa", "fi", "fil", "fr", "fy-NL", "ga-IE", "gl", "got",
            "gu-IN", "heb", "hi", "hr", "hu", "hy-AM", "ind", "is", "it", "ja", "jv", "ka", "kk", "km", "kn", "ko", "ku",
            "ky", "lt", "lv", "mk", "ml-IN", "mn", "mr", "ms", "my", "nl", "nn-NO", "no", "or", "pa-IN", "pl", "pt-BR", "pt-PT",
            "ro", "ru", "sat", "sc", "sk", "sl", "sq", "sr", "ss", "sv-SE", "sw", "ta", "te", "tg", "tgl", "th", "ti", "tn", "tr",
            "ts", "tt-RU", "uk", "ur-PK", "uz", "ve", "vi", "wo", "xh", "yue", "zh-CN", "zh-TW", "zu" };


    /**
     * Returns the {@link Locale} for the given code or the default locale, if no code or preferences are given.
     *
     * @return The {@link Locale} for the given code
     */
    @NonNull
    public static Locale getLocale() {
        return getLocale("");
    }

    /**
     * Returns the {@link Locale} for the given code or the default locale, if no preferences are given.
     *
     * @return The {@link Locale} for the given code
     */
    @NonNull
    public static Locale getLocale(@Nullable String localeCode) {
        SharedPreferences prefs = AnkiDroidApp.getSharedPrefs(AnkiDroidApp.getInstance().getBaseContext());
        return getLocale(localeCode, prefs);
    }

    /**
     * Returns the {@link Locale} for the given code or the default locale, if no code is given.
     *
     * @param localeCode The locale code of the language
     * @return The {@link Locale} for the given code
     */
    @NonNull
    public static Locale getLocale(@Nullable String localeCode, @NonNull SharedPreferences prefs) {
        Locale locale;
        if (localeCode == null || TextUtils.isEmpty(localeCode)) {

            localeCode = prefs.getString(Preferences.LANGUAGE, "");
            // If no code provided use the app language.
        }
        if (TextUtils.isEmpty(localeCode)) {
            // Fall back to (system) default only if that fails.
            localeCode = Locale.getDefault().toString();
        }
        // Language separators are '_' or '-' at different times in display/resource fetch
        if (localeCode != null && (localeCode.contains("_") || localeCode.contains("-"))) {
            try {
                String[] localeParts = localeCode.split("[_-]", 2);
                locale = new Locale(localeParts[0], localeParts[1]);
            } catch (ArrayIndexOutOfBoundsException e) {
                Timber.w(e, "LanguageUtil::getLocale variant split fail, using code '%s' raw.", localeCode);
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


    public static Locale getLocaleCompat(Resources resources) {
        return ConfigurationCompat.getLocales(resources.getConfiguration()).get(0);
    }
}
