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

import com.google.common.collect.Sets;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import static java.util.Arrays.asList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;

public class LanguageUtilsLanguageRegressionTest {

    private static final String[] PREVIOUS_LANGUAGES = { "ar", "bg", "ca", "cs", "de", "el", "en", "eo", "es-AR", "es-ES", "et", "fa",
            "fi", "fr", "got", "gl", "hi", "hu", "id", "it", "ja", "ko", "lt", "nl", "nn-NO", "no", "pl", "pt_PT", "pt_BR", "ro", "ru",
            "sk", "sl", "sr", "sv", "th", "tr", "tt-RU", "uk", "vi", "zh_CN", "zh_TW" };

    private static final String[] CURRENT_LANGUAGES = {"af", "am", "ar", "az", "be", "bg", "bn", "ca", "ckb", "cs", "da",
            "de", "el", "en", "eo", "es-AR", "es-ES", "et", "eu", "fa", "fi", "fil", "fr", "fy-NL", "ga-IE", "gl", "got",
            "gu-IN", "he", "hi", "hr", "hu", "hy-AM", "id", "is", "it", "ja", "jv", "ka", "kk", "km", "ko", "ku",
            "ky", "lt", "lv", "mk", "mn", "mr", "ms", "my", "nl", "nn-NO", "no", "pa-IN", "pl", "pt-BR", "pt-PT",
            "ro", "ru", "sk", "sl", "sq", "sr", "ss", "sv-SE", "sw", "ta", "te", "tg", "th", "ti", "tl", "tn", "tr",
            "ts", "tt-RU", "uk", "ur-PK", "uz", "ve", "vi", "wo", "xh", "yu", "zh-CN", "zh-TW", "zu" };

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
}
