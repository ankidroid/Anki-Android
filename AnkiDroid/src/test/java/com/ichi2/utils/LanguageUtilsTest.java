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

import android.os.Build;

import com.google.common.collect.Sets;
import com.ichi2.anki.RobolectricTest;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import static java.util.Arrays.asList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;

@RunWith(AndroidJUnit4.class)
@Config(sdk = { Build.VERSION_CODES.JELLY_BEAN,
                Build.VERSION_CODES.JELLY_BEAN_MR1,
                Build.VERSION_CODES.N })
public class LanguageUtilsTest extends RobolectricTest {

    private static final String[] PREVIOUS_LANGUAGES = { "ar", "bg", "ca", "cs", "de", "el", "en", "eo", "es-AR", "es-ES", "et", "fa",
            "fi", "fr", "got", "gl", "hi", "hu", "ind", "it", "ja", "ko", "lt", "nl", "nn-NO", "no", "pl", "pt_PT", "pt_BR", "ro", "ru",
            "sk", "sl", "sr", "sv", "th", "tr", "tt-RU", "uk", "vi", "zh_CN", "zh_TW" };

    private static final String[] CURRENT_LANGUAGES = {"af", "am", "ar", "az", "be", "bg", "bn", "ca", "ckb", "cs", "da",
            "de", "el", "en", "eo", "es-AR", "es-ES", "et", "eu", "fa", "fi", "fil", "fr", "fy-NL", "ga-IE", "gl", "got",
            "gu-IN", "heb", "hi", "hr", "hu", "hy-AM", "ind", "is", "it", "ja", "jv", "ka", "kk", "km", "ko", "ku",
            "ky", "lt", "lv", "mk", "mn", "mr", "ms", "my", "nl", "nn-NO", "no", "pa-IN", "pl", "pt-BR", "pt-PT",
            "ro", "ru", "sat", "sk", "sl", "sq", "sr", "ss", "sv-SE", "sw", "ta", "te", "tg", "tgl", "th", "ti", "tn", "tr",
            "ts", "tt-RU", "uk", "ur-PK", "uz", "ve", "vi", "wo", "xh", "yue", "zh-CN", "zh-TW", "zu" };

    /** Languages which were removed for good reason */
    private static final HashSet<String> previousLanguageExclusions = Sets.newHashSet(
            "pt_PT", //pt-PT
            "pt_BR", //pt-BR
            "sv",    //sv-SE
            "zh_CN", //zh-CN
            "zh_TW"  //zh-TW
    );

    @Test
    public void testNoLanguageIsRemoved() {
        HashSet<String> languages = new HashSet<>();
        Collections.addAll(languages, LanguageUtil.APP_LANGUAGES);

        List<String> previousLanguages = new ArrayList<>(asList(PREVIOUS_LANGUAGES));
        previousLanguages.removeAll(previousLanguageExclusions);

        for (String language: previousLanguages) {
            assertThat(languages, hasItem(language));
        }
    }

    @Test
    public void testCurrentLanguagesHaveNotChanged() {
        List<String> actual = asList(LanguageUtil.APP_LANGUAGES);
        assertThat("Languages have been updated, please modify test variables: " +
                "PREVIOUS_LANGUAGES and CURRENT_LANGUAGES", actual, contains(CURRENT_LANGUAGES));
    }

    @Test
    @Config(qualifiers = "en")
    public void localeTwoLetterCodeResolves() {
        assertThat("A locale with a 3-letter code resolves correctly",
                "Afrikaans",
                is(LanguageUtil.getLocale("af").getDisplayLanguage()));
    }

    @Test
    @Config(qualifiers = "en")
    public void localeThreeLetterCodeResolves() {
        assertThat("A locale with a 3-letter code resolves correctly",
                "Filipino",
                is(LanguageUtil.getLocale("fil").getDisplayLanguage()));
    }

    @Test
    @Config(qualifiers = "en")
    public void localeTwoLetterRegionalVariantResolves() {
        assertThat("A locale with a 2-letter code and regional variant resolves correctly",
                "Portuguese (Brazil)",
                is(LanguageUtil.getLocale("pt-BR").getDisplayName()));
        assertThat("A locale with a 2-letter code and regional variant resolves correctly",
                "Portuguese (Brazil)",
                is(LanguageUtil.getLocale("pt_BR").getDisplayName()));
    }

    @Test
    @Config(qualifiers = "en")
    public void localeThreeLetterRegionalVariantResolves() {
        assertThat("A locale with a 2-letter code and regional variant resolves correctly",
                "yue (Taiwan)",
                is(LanguageUtil.getLocale("yue-TW").getDisplayName()));
        assertThat("A locale with a 2-letter code and regional variant resolves correctly",
                "yue (Taiwan)",
                is(LanguageUtil.getLocale("yue_TW").getDisplayName()));
    }
}
