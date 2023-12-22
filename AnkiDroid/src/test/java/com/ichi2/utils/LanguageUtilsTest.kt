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

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.collect.Sets
import com.ichi2.testutils.EmptyApplication
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.*

@RunWith(AndroidJUnit4::class)
@Config(application = EmptyApplication::class)
class LanguageUtilsTest {
    @Test
    fun testNoLanguageIsRemoved() {
        val languages = LanguageUtil.APP_LANGUAGES.values
        val previousLanguages = PREVIOUS_LANGUAGES.toMutableList()
        previousLanguages.removeAll(previousLanguageExclusions)
        for (language in previousLanguages) {
            assertThat(languages, Matchers.hasItem(language))
        }
    }

    @Test
    fun testCurrentLanguagesHaveNotChanged() {
        val actual = LanguageUtil.APP_LANGUAGES.values
        assertThat(
            "Languages have been updated, please modify test variables: " +
                "PREVIOUS_LANGUAGES and CURRENT_LANGUAGES",
            actual,
            Matchers.contains(*CURRENT_LANGUAGES),
        )
    }

    companion object {
        /** The value of CURRENT_LANGUAGES before the last language update  */
        private val PREVIOUS_LANGUAGES =
            arrayOf(
                "af", "am", "ar", "az", "be", "bg", "bn", "ca", "ckb", "cs", "da",
                "de", "el", "en", "eo", "es-AR", "es-ES", "et", "eu", "fa", "fi", "fil", "fr", "fy-NL", "ga-IE", "gl", "got",
                "gu-IN", "heb", "hi", "hr", "hu", "hy-AM", "ind", "is", "it", "ja", "jv", "ka", "kk", "km", "kn", "ko", "ku",
                "ky", "lt", "lv", "mk", "ml-IN", "mn", "mr", "ms", "my", "nl", "nn-NO", "no", "or", "pa-IN", "pl", "pt-BR", "pt-PT",
                "ro", "ru", "sat", "sk", "sl", "sq", "sr", "ss", "sv-SE", "sw", "ta", "te", "tg", "tgl", "th", "ti", "tn", "tr",
                "ts", "tt-RU", "uk", "ur-PK", "uz", "ve", "vi", "wo", "xh", "yue", "zh-CN", "zh-TW", "zu",
            )

        /**
         * This should match [LanguageUtil.APP_LANGUAGES]
         * Before updating this, copy the variable declaration to PREVIOUS_LANGUAGES
         */
        private val CURRENT_LANGUAGES =
            arrayOf(
                "af", "am", "ar", "az", "be", "bg", "bn", "ca", "ckb", "cs", "da",
                "de", "el", "en", "eo", "es-AR", "es-ES", "et", "eu", "fa", "fi", "fil", "fr", "fy-NL", "ga-IE", "gl", "got",
                "gu-IN", "heb", "hi", "hr", "hu", "hy-AM", "ind", "is", "it", "ja", "jv", "ka", "kk", "km", "kn", "ko", "ku",
                "ky", "lt", "lv", "mk", "ml-IN", "mn", "mr", "ms", "my", "nl", "nn-NO", "no", "or", "pa-IN", "pl", "pt-BR", "pt-PT",
                "ro", "ru", "sat", "sc", "sk", "sl", "sq", "sr", "ss", "sv-SE", "sw", "ta", "te", "tg", "tgl", "th", "ti", "tn", "tr",
                "ts", "tt-RU", "uk", "ur-PK", "uz", "ve", "vi", "wo", "xh", "yue", "zh-CN", "zh-TW", "zu",
            )

        /** Languages mappings which were removed/obsoleted in favor of new mappings for good reason  */
        private val previousLanguageExclusions =
            Sets.newHashSet(
                "pt_PT",
                "pt_BR",
                "sv",
                "zh_CN",
                "zh_TW",
            )
    }
}
