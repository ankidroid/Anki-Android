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

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.core.os.ConfigurationCompat
import androidx.fragment.app.Fragment
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
    val APP_LANGUAGES = mapOf(
        "Afrikaans" to "af", // Afrikaans
        "አማርኛ" to "am", // Amharic
        "العربية" to "ar", // Arabic
        "azərbaycan" to "az", // Azerbaijani
        "беларуская" to "be", // Belarusian
        "български" to "bg", // Bulgarian
        "বাংলা" to "bn", // Bangla
        "català" to "ca", // Catalan
        "کوردیی ناوەندی" to "ckb", // Central Kurdish
        "čeština" to "cs", // Czech
        "dansk" to "da", // Danish
        "Deutsch" to "de", // German
        "Ελληνικά" to "el", // Greek
        "English" to "en", // English
        "esperanto" to "eo", // Esperanto
        "español (Argentina)" to "es-AR", // Spanish (Argentina)
        "español (España)" to "es-ES", // Spanish (Spain)
        "eesti" to "et", // Estonian
        "euskara" to "eu", // Basque
        "فارسی" to "fa", // Persian
        "suomi" to "fi", // Finnish
        "Filipino" to "fil", // Filipino
        "français" to "fr", // French
        "Frysk (Nederlân)" to "fy-NL", // Western Frisian (Netherlands)
        "Gaeilge (Éire)" to "ga-IE", // Irish (Ireland)
        "galego" to "gl", // Galician
        "Gothic" to "got", // Gothic
        "ગુજરાતી (ભારત)" to "gu-IN", // Gujarati (India)
        "עברית" to "heb", // Hebrew
        "हिन्दी" to "hi", // Hindi
        "hrvatski" to "hr", // Croatian
        "magyar" to "hu", // Hungarian
        "հայերեն (Հայաստան)" to "hy-AM", // Armenian (Armenia)
        "Indonesia" to "ind", // Indonesian
        "íslenska" to "is", // Icelandic
        "italiano" to "it", // Italian
        "日本語" to "ja", // Japanese
        "Jawa" to "jv", // Javanese
        "ქართული" to "ka", // Georgian
        "қазақ тілі" to "kk", // Kazakh
        "ខ្មែរ" to "km", // Khmer
        "ಕನ್ನಡ" to "kn", // Kannada
        "한국어" to "ko", // Korean
        "kurdî" to "ku", // Kurdish
        "кыргызча" to "ky", // Kyrgyz
        "lietuvių" to "lt", // Lithuanian
        "latviešu" to "lv", // Latvian
        "македонски" to "mk", // Macedonian
        "മലയാളം (ഇന്ത്യ)" to "ml-IN", // Malayalam (India)
        "монгол" to "mn", // Mongolian
        "मराठी" to "mr", // Marathi
        "Melayu" to "ms", // Malay
        "မြန်မာ" to "my", // Burmese
        "Nederlands" to "nl", // Dutch
        "nynorsk (Noreg)" to "nn-NO", // Norwegian Nynorsk (Norway)
        "norsk" to "no", // Norwegian
        "ଓଡ଼ିଆ" to "or", // Odia
        "ਪੰਜਾਬੀ (ਭਾਰਤ)" to "pa-IN", // Punjabi (India)
        "polski" to "pl", // Polish
        "Português (Brasil)" to "pt-BR", // Portuguese (Brazil)
        "Português (Portugal)" to "pt-PT", // Portuguese (Portugal)
        "română" to "ro", // Romanian
        "русский" to "ru", // Russian
        "Santali" to "sat", // Santali
        "Sardinian" to "sc", // Sardinian
        "slovenčina" to "sk", // Slovak
        "slovenščina" to "sl", // Slovenian
        "shqip" to "sq", // Albanian
        "српски" to "sr", // Serbian
        "Swati" to "ss", // Swati
        "svenska (Sverige)" to "sv-SE", // Swedish (Sweden)
        "Kiswahili" to "sw", // Swahili
        "தமிழ்" to "ta", // Tamil
        "తెలుగు" to "te", // Telugu
        "тоҷикӣ" to "tg", // Tajik
        "Tagalog" to "tgl", // Tagalog
        "ไทย" to "th", // Thai
        "ትግርኛ" to "ti", // Tigrinya
        "Tswana" to "tn", // Tswana
        "Türkçe" to "tr", // Turkish
        "Tsonga" to "ts", // Tsonga
        "татар (Россия)" to "tt-RU", // Tatar (Russia)
        "українська" to "uk", // Ukrainian
        "اردو (پاکستان)" to "ur-PK", // Urdu (Pakistan)
        "o‘zbek" to "uz", // Uzbek
        "Venda" to "ve", // Venda
        "Tiếng Việt" to "vi", // Vietnamese
        "Wolof" to "wo", // Wolof
        "isiXhosa" to "xh", // Xhosa
        "粵語" to "yue", // Cantonese
        "中文 (中国)" to "zh-CN", // Chinese (China)
        "中文 (台灣)" to "zh-TW", // Chinese (Taiwan)
        "isiZulu" to "zu" // Zulu

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

    /** @return string defined with [stringRes] on the specified [locale] */
    fun Context.getStringByLocale(@StringRes stringRes: Int, locale: Locale, vararg formatArgs: Any): String {
        val configuration = Configuration(resources.configuration)
        configuration.setLocale(locale)
        return createConfigurationContext(configuration).resources.getString(stringRes, *formatArgs)
    }

    /** @return string defined with [stringRes] on the specified [locale] */
    fun Fragment.getStringByLocale(@StringRes stringRes: Int, locale: Locale, vararg formatArgs: Any): String {
        return requireContext().getStringByLocale(stringRes, locale, *formatArgs)
    }
}
