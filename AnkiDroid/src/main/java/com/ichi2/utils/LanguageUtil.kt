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
import androidx.core.os.ConfigurationCompat
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.preferences.Preferences
import net.ankiweb.rsdroid.BackendFactory
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
    val APP_LANGUAGES = arrayOf(
        "af", // Afrikaans / Afrikaans
        "am", // Amharic / አማርኛ
        "ar", // Arabic / العربية
        "az", // Azerbaijani / azərbaycan
        "be", // Belarusian / беларуская
        "bg", // Bulgarian / български
        "bn", // Bangla / বাংলা
        "ca", // Catalan / català
        "ckb", // Central Kurdish / کوردیی ناوەندی
        "cs", // Czech / čeština
        "da", // Danish / dansk
        "de", // German / Deutsch
        "el", // Greek / Ελληνικά
        "en", // English / English
        "eo", // Esperanto / esperanto
        "es-AR", // Spanish (Argentina) / español (Argentina)
        "es-ES", // Spanish (Spain) / español (España)
        "et", // Estonian / eesti
        "eu", // Basque / euskara
        "fa", // Persian / فارسی
        "fi", // Finnish / suomi
        "fil", // Filipino / Filipino
        "fr", // French / français
        "fy-NL", // Western Frisian (Netherlands) / Frysk (Nederlân)
        "ga-IE", // Irish (Ireland) / Gaeilge (Éire)
        "gl", // Galician / galego
        "got", // Gothic / Gothic
        "gu-IN", // Gujarati (India) / ગુજરાતી (ભારત)
        "heb", // Hebrew / עברית
        "hi", // Hindi / हिन्दी
        "hr", // Croatian / hrvatski
        "hu", // Hungarian / magyar
        "hy-AM", // Armenian (Armenia) / հայերեն (Հայաստան)
        "ind", // Indonesian / Indonesia
        "is", // Icelandic / íslenska
        "it", // Italian / italiano
        "ja", // Japanese / 日本語
        "jv", // Javanese / Jawa
        "ka", // Georgian / ქართული
        "kk", // Kazakh / қазақ тілі
        "km", // Khmer / ខ្មែរ
        "kn", // Kannada / ಕನ್ನಡ
        "ko", // Korean / 한국어
        "ku", // Kurdish / kurdî
        "ky", // Kyrgyz / кыргызча
        "lt", // Lithuanian / lietuvių
        "lv", // Latvian / latviešu
        "mk", // Macedonian / македонски
        "ml-IN", // Malayalam (India) / മലയാളം (ഇന്ത്യ)
        "mn", // Mongolian / монгол
        "mr", // Marathi / मराठी
        "ms", // Malay / Melayu
        "my", // Burmese / မြန်မာ
        "nl", // Dutch / Nederlands
        "nn-NO", // Norwegian Nynorsk (Norway) / nynorsk (Noreg)
        "no", // Norwegian / norsk
        "or", // Odia / ଓଡ଼ିଆ
        "pa-IN", // Punjabi (India) / ਪੰਜਾਬੀ (ਭਾਰਤ)
        "pl", // Polish / polski
        "pt-BR", // Portuguese (Brazil) / português (Brasil)
        "pt-PT", // Portuguese (Portugal) / português (Portugal)
        "ro", // Romanian / română
        "ru", // Russian / русский
        "sat", // Santali / Santali
        "sc", // Sardinian / Sardinian
        "sk", // Slovak / slovenčina
        "sl", // Slovenian / slovenščina
        "sq", // Albanian / shqip
        "sr", // Serbian / српски
        "ss", // Swati / Swati
        "sv-SE", // Swedish (Sweden) / svenska (Sverige)
        "sw", // Swahili / Kiswahili
        "ta", // Tamil / தமிழ்
        "te", // Telugu / తెలుగు
        "tg", // Tajik / тоҷикӣ
        "tgl", // Tagalog / Tagalog
        "th", // Thai / ไทย
        "ti", // Tigrinya / ትግርኛ
        "tn", // Tswana / Tswana
        "tr", // Turkish / Türkçe
        "ts", // Tsonga / Tsonga
        "tt-RU", // Tatar (Russia) / татар (Россия)
        "uk", // Ukrainian / українська
        "ur-PK", // Urdu (Pakistan) / اردو (پاکستان)
        "uz", // Uzbek / o‘zbek
        "ve", // Venda / Venda
        "vi", // Vietnamese / Tiếng Việt
        "wo", // Wolof / Wolof
        "xh", // Xhosa / isiXhosa
        "yue", // Cantonese / 粵語
        "zh-CN", // Chinese (China) / 中文 (中国)
        "zh-TW", // Chinese (Taiwan) / 中文 (台灣)
        "zu" // Zulu / isiZulu
    )

    /** Backend languages; may not include recently added ones.
     * Found at https://i18n.ankiweb.net/teams/ */
    val BACKEND_LANGS = listOf(
        "af", // Afrikaans
        "ar", // العربية
        "be", // Беларуская мова
        "bg", // Български
        "ca", // Català
        "cs", // Čeština
        "da", // Dansk
        "de", // Deutsch
        "el", // Ελληνικά
        "en", // English (United States)
        "en-GB", // English (United Kingdom)
        "eo", // Esperanto
        "es", // Español
        "et", // Eesti
        "eu", // Euskara
        "fa", // فارسی
        "fi", // Suomi
        "fr", // Français
        "ga-IE", // Gaeilge
        "gl", // Galego
        "he", // עִבְרִית
        "hi-IN", // Hindi
        "hr", // Hrvatski
        "hu", // Magyar
        "hy-AM", // Հայերեն
        "id", // Indonesia
        "it", // Italiano
        "ja", // 日本語
        "jbo", // lo jbobau
        "ko", // 한국어
        "la", // Latin
        "mn", // Монгол хэл
        "ms", // Bahasa Melayu
        "nb", // Norsk
        "nb-NO", // norwegian
        "nl", // Nederlands
        "nn-NO", // norwegian
        "oc", // Lenga d'òc
        "or", // ଓଡ଼ିଆ
        "pl", // Polski
        "pt-BR", // Português Brasileiro
        "pt-PT", // Português
        "ro", // Română
        "ru", // Pусский язык
        "sk", // Slovenčina
        "sl", // Slovenščina
        "sr", // Српски
        "sv-SE", // Svenska
        "th", // ภาษาไทย
        "tr", // Türkçe
        "uk", // Yкраїнська мова
        "vi", // Tiếng Việt
        "zh-CN", // 简体中文
        "zh-TW" // 繁體中文
    )

    /**
     * Returns the [Locale] for the given code or the default locale, if no code or preferences are given.
     *
     * @return The [Locale] for the given code
     */
    val locale: Locale
        get() = getLocale("")

    /**
     * Returns the [Locale] for the given code or the default locale, if no preferences are given.
     *
     * @return The [Locale] for the given code
     */
    fun getLocale(localeCode: String?): Locale {
        val prefs = AnkiDroidApp.getSharedPrefs(AnkiDroidApp.instance.baseContext)
        return getLocale(localeCode, prefs)
    }

    /**
     * Returns the [Locale] for the given code or the default locale, if no code is given.
     *
     * @param localeCode The locale code of the language
     * @return The [Locale] for the given code
     */
    fun getLocale(localeCode: String?, prefs: SharedPreferences): Locale {
        var tempLocaleCode = localeCode
        if (tempLocaleCode.isNullOrEmpty()) {
            tempLocaleCode = prefs.getLanguage()
            // If no code provided use the app language.
        }
        if (tempLocaleCode.isNullOrEmpty()) {
            // Fall back to (system) default only if that fails.
            tempLocaleCode = Locale.getDefault().toString()
        }
        // Language separators are '_' or '-' at different times in display/resource fetch
        val locale: Locale = if ((tempLocaleCode.contains("_")) || (tempLocaleCode.contains("-"))) {
            try {
                val localeParts = tempLocaleCode.split("[_-]".toRegex(), 2).toTypedArray()
                Locale(localeParts[0], localeParts[1])
            } catch (e: ArrayIndexOutOfBoundsException) {
                Timber.w(e, "LanguageUtil::getLocale variant split fail, using code '%s' raw.", localeCode)
                Locale(tempLocaleCode)
            }
        } else {
            Locale(tempLocaleCode) // guaranteed to be non null
        }
        return locale
    }

    fun getShortDateFormatFromMs(ms: Long): String {
        return DateFormat.getDateInstance(DateFormat.SHORT, locale).format(Date(ms))
    }

    fun getShortDateFormatFromS(s: Long): String {
        return DateFormat.getDateInstance(DateFormat.SHORT, locale).format(Date(s * 1000L))
    }

    fun getLocaleCompat(resources: Resources): Locale? {
        return ConfigurationCompat.getLocales(resources.configuration)[0]
    }

    @JvmStatic
    fun getSystemLocale(): Locale = getLocaleCompat(Resources.getSystem())!!

    /** If locale is not provided, the current locale will be used. */
    fun setDefaultBackendLanguages(locale: String = "") {
        BackendFactory.defaultLanguages = listOf(localeToBackendCode(getLocale(locale)))
    }

    private fun localeToBackendCode(locale: Locale): String {
        return when (locale.language) {
            Locale("heb").language -> "he"
            Locale("ind").language -> "id"
            Locale("tgl").language -> "tl"
            Locale("hi").language -> "hi-IN"
            Locale("yue").language -> "zh-HK"
            else -> locale.toLanguageTag()
        }
    }

    /**
     * @return the language defined by the preferences, or the empty string.
     */
    fun SharedPreferences.getLanguage() = getString(Preferences.LANGUAGE, "")

    /**
     * @return the language defined by the preferences, or otherwise the default locale
     */
    fun SharedPreferences.getCurrentLanguage(): String = getString(Preferences.LANGUAGE, null) ?: Locale.getDefault().language
}
