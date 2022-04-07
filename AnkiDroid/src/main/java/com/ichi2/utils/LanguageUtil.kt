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
package com.ichi2.utils

import android.content.SharedPreferences
import android.content.res.Resources
import android.text.TextUtils
import androidx.core.os.ConfigurationCompat
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.Preferences
import timber.log.Timber
import java.text.DateFormat
import java.util.*

/**
 * Utility call for proving language related functionality.
 */
object LanguageUtil {
    /** A list of all languages supported by AnkiDroid
     * Please modify LanguageUtilsTest if changing
     * Please note 'yue' is special, it is 'yu' on CrowdIn, and mapped in import specially to 'yue' */
    @JvmField
    val APP_LANGUAGES = arrayOf(
        "af", "am", "ar", "az", "be", "bg", "bn", "ca", "ckb", "cs", "da",
        "de", "el", "en", "eo", "es-AR", "es-ES", "et", "eu", "fa", "fi", "fil", "fr", "fy-NL", "ga-IE", "gl", "got",
        "gu-IN", "heb", "hi", "hr", "hu", "hy-AM", "ind", "is", "it", "ja", "jv", "ka", "kk", "km", "kn", "ko", "ku",
        "ky", "lt", "lv", "mk", "ml-IN", "mn", "mr", "ms", "my", "nl", "nn-NO", "no", "or", "pa-IN", "pl", "pt-BR", "pt-PT",
        "ro", "ru", "sat", "sc", "sk", "sl", "sq", "sr", "ss", "sv-SE", "sw", "ta", "te", "tg", "tgl", "th", "ti", "tn", "tr",
        "ts", "tt-RU", "uk", "ur-PK", "uz", "ve", "vi", "wo", "xh", "yue", "zh-CN", "zh-TW", "zu"
    )

    /**
     * Returns the [Locale] for the given code or the default locale, if no code or preferences are given.
     *
     * @return The [Locale] for the given code
     */
    @JvmStatic
    val locale: Locale
        get() = getLocale("")

    /**
     * Returns the [Locale] for the given code or the default locale, if no preferences are given.
     *
     * @return The [Locale] for the given code
     */
    @JvmStatic
    fun getLocale(localeCode: String?): Locale {
        val prefs = AnkiDroidApp.getSharedPrefs(AnkiDroidApp.getInstance().baseContext)
        return getLocale(localeCode, prefs)
    }

    /**
     * Returns the [Locale] for the given code or the default locale, if no code is given.
     *
     * @param localeCode The locale code of the language
     * @return The [Locale] for the given code
     */
    @JvmStatic
    fun getLocale(localeCode: String?, prefs: SharedPreferences): Locale {
        var tempLocaleCode = localeCode
        if (tempLocaleCode == null || TextUtils.isEmpty(tempLocaleCode)) {
            tempLocaleCode = prefs.getString(Preferences.LANGUAGE, "")
            // If no code provided use the app language.
        }
        if (TextUtils.isEmpty(tempLocaleCode)) {
            // Fall back to (system) default only if that fails.
            tempLocaleCode = Locale.getDefault().toString()
        }
        // Language separators are '_' or '-' at different times in display/resource fetch
        val locale: Locale = if (tempLocaleCode != null && (tempLocaleCode.contains("_") || tempLocaleCode.contains("-"))) {
            try {
                val localeParts = tempLocaleCode.split("[_-]".toRegex(), 2).toTypedArray()
                Locale(localeParts[0], localeParts[1])
            } catch (e: ArrayIndexOutOfBoundsException) {
                Timber.w(e, "LanguageUtil::getLocale variant split fail, using code '%s' raw.", localeCode)
                Locale(tempLocaleCode)
            }
        } else {
            Locale(tempLocaleCode!!) // guaranteed to be non null
        }
        return locale
    }

    @JvmStatic
    fun getShortDateFormatFromMs(ms: Long): String {
        return DateFormat.getDateInstance(DateFormat.SHORT, locale).format(Date(ms))
    }

    @JvmStatic
    fun getShortDateFormatFromS(s: Long): String {
        return DateFormat.getDateInstance(DateFormat.SHORT, locale).format(Date(s * 1000L))
    }

    @JvmStatic
    fun getLocaleCompat(resources: Resources): Locale {
        return ConfigurationCompat.getLocales(resources.configuration)[0]
    }
}
