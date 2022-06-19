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

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.collect.Sets
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.testutils.EmptyApplication
import com.ichi2.utils.LanguageUtil.getLocale
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.oneOf
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.*

@RunWith(AndroidJUnit4::class)
@Config(application = EmptyApplication::class)
class LanguageUtilsTest {
    @Test
    fun testNoLanguageIsRemoved() {
        val languages = LanguageUtil.APP_LANGUAGES.toHashSet()
        Collections.addAll(languages, *LanguageUtil.APP_LANGUAGES)
        val previousLanguages = PREVIOUS_LANGUAGES.toMutableList()
        previousLanguages.removeAll(previousLanguageExclusions)
        for (language in previousLanguages) {
            assertThat(languages, Matchers.hasItem(language))
        }
    }

    @Test
    fun testCurrentLanguagesHaveNotChanged() {
        val actual = LanguageUtil.APP_LANGUAGES.toList()
        assertThat(
            "Languages have been updated, please modify test variables: " +
                "PREVIOUS_LANGUAGES and CURRENT_LANGUAGES",
            actual,
            Matchers.contains(*CURRENT_LANGUAGES)
        )
    }

    @Test
    @Config(qualifiers = "en")
    fun localeTwoLetterCodeResolves() {
        assertThat(
            "A locale with a 3-letter code resolves correctly",
            getLocale("af").displayLanguage,
            equalTo("Afrikaans")
        )
    }

    @Test
    @Config(qualifiers = "en")
    fun localeThreeLetterCodeResolves() {
        assertThat(
            "A locale with a 3-letter code resolves correctly",
            getLocale("fil").displayLanguage,
            equalTo("Filipino")
        )
    }

    @Test
    @Config(qualifiers = "en")
    fun localeTwoLetterRegionalVariantResolves() {
        assertThat(
            "A locale with a 2-letter code and regional variant resolves correctly",
            getLocale("pt-BR").displayName,
            equalTo("Portuguese (Brazil)")
        )
        assertThat(
            "A locale with a 2-letter code and regional variant resolves correctly",
            getLocale("pt_BR").displayName,
            equalTo("Portuguese (Brazil)")
        )
    }

    @Test
    @Config(qualifiers = "en")
    fun localeThreeLetterRegionalVariantResolves() {
        assertThat(
            "A locale with a 2-letter code and regional variant resolves correctly",
            getLocale("yue-TW").displayName,
            oneOf("yue (Taiwan)", "Cantonese (Taiwan)")
        )
        assertThat(
            "A locale with a 2-letter code and regional variant resolves correctly",
            getLocale("yue_TW").displayName,
            oneOf("yue (Taiwan)", "Cantonese (Taiwan)")
        )
    }

    private fun getLocale(localeCode: String): Locale {
        val prefs = AnkiDroidApp.getSharedPrefs(ApplicationProvider.getApplicationContext())
        return getLocale(localeCode, prefs)
    }

    companion object {
        /** The value of CURRENT_LANGUAGES before the last language update  */
        private val PREVIOUS_LANGUAGES = arrayOf(
            "af", "am", "ar", "az", "be", "bg", "bn", "ca", "ckb", "cs", "da",
            "de", "el", "en", "eo", "es-AR", "es-ES", "et", "eu", "fa", "fi", "fil", "fr", "fy-NL", "ga-IE", "gl", "got",
            "gu-IN", "heb", "hi", "hr", "hu", "hy-AM", "ind", "is", "it", "ja", "jv", "ka", "kk", "km", "kn", "ko", "ku",
            "ky", "lt", "lv", "mk", "ml-IN", "mn", "mr", "ms", "my", "nl", "nn-NO", "no", "or", "pa-IN", "pl", "pt-BR", "pt-PT",
            "ro", "ru", "sat", "sk", "sl", "sq", "sr", "ss", "sv-SE", "sw", "ta", "te", "tg", "tgl", "th", "ti", "tn", "tr",
            "ts", "tt-RU", "uk", "ur-PK", "uz", "ve", "vi", "wo", "xh", "yue", "zh-CN", "zh-TW", "zu"
        )

        /**
         * This should match [LanguageUtil.APP_LANGUAGES]
         * Before updating this, copy the variable declaration to PREVIOUS_LANGUAGES
         */
        private val CURRENT_LANGUAGES = arrayOf(
            "af", "am", "ar", "az", "be", "bg", "bn", "ca", "ckb", "cs", "da",
            "de", "el", "en", "eo", "es-AR", "es-ES", "et", "eu", "fa", "fi", "fil", "fr", "fy-NL", "ga-IE", "gl", "got",
            "gu-IN", "heb", "hi", "hr", "hu", "hy-AM", "ind", "is", "it", "ja", "jv", "ka", "kk", "km", "kn", "ko", "ku",
            "ky", "lt", "lv", "mk", "ml-IN", "mn", "mr", "ms", "my", "nl", "nn-NO", "no", "or", "pa-IN", "pl", "pt-BR", "pt-PT",
            "ro", "ru", "sat", "sc", "sk", "sl", "sq", "sr", "ss", "sv-SE", "sw", "ta", "te", "tg", "tgl", "th", "ti", "tn", "tr",
            "ts", "tt-RU", "uk", "ur-PK", "uz", "ve", "vi", "wo", "xh", "yue", "zh-CN", "zh-TW", "zu"
        )

        /** Languages which were removed for good reason  */
        private val previousLanguageExclusions = Sets.newHashSet(
            "pt_PT", // pt-PT
            "pt_BR", // pt-BR
            "sv", // sv-SE
            "zh_CN", // zh-CN
            "zh_TW" // zh-TW
        )
    }
}
