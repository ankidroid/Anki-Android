/*
 *  Copyright (c) 2022 David Allison <davidallisongithub@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki

import com.ichi2.testutils.EmptyApplication
import com.ichi2.testutils.LintTests
import com.ichi2.utils.LanguageUtil
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.not
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.ParameterizedRobolectricTestRunner.Parameters
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import timber.log.Timber
import java.util.*

/**
 * Linting to ensure that all locales have valid strings
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Category(LintTests::class) // not yet supported by gradle: https://issues.gradle.org/browse/GRADLE-2111
@Config(application = EmptyApplication::class) // no point in Application init if we don't use it
class LocaleLinting(private val locale: Locale) : RobolectricTest() {
    @Before
    override fun setUp() {
        super.setUp()
        RuntimeEnvironment.setQualifiers(locale.language)
    }

    companion object {
        @Parameters(name = "{0}")
        @JvmStatic // required for initParameters
        @Suppress("unused")
        fun initParameters(): Collection<Locale> {
            return LanguageUtil.APP_LANGUAGES.values.map(::toLocale)
        }

        private fun toLocale(localeCode: String): Locale {
            return if (localeCode.contains("_") || localeCode.contains("-")) {
                try {
                    val localeParts: Array<String> = localeCode.split("[_-]".toRegex(), 2).toTypedArray()
                    Locale(localeParts[0], localeParts[1])
                } catch (e: ArrayIndexOutOfBoundsException) {
                    Timber.w(e, "LanguageUtil::getLocale variant split fail, using code '%s' raw.", localeCode)
                    Locale(localeCode)
                }
            } else {
                Locale(localeCode)
            }
        }
    }

    @Test
    fun sample_answer_has_different_second_word() {
        // the point of the sample answer is to show letter differences, not just extra words, for example:
        // an example -> exomple
        val sample = targetContext.getString(R.string.basic_answer_sample_text)
        val sampleUser = targetContext.getString(R.string.basic_answer_sample_text_user)

        assertThat(
            "'$sample' should differ from '$sampleUser'. " +
                "These strings are used in the type the answer diffs, and the user should see all examples of problems. " +
                "see: basic_answer_sample_text and basic_answer_sample_text_user",
            sample,
            not(equalTo(sampleUser)),
        )

        val lastWord = sample.split(" ").last()
        assertThat(
            "the last word of '$sample' should differ from '$sampleUser'. " +
                "These are used in the type the answer diffs, and the user should see all examples of problems " +
                "see: basic_answer_sample_text and basic_answer_sample_text_user",
            lastWord,
            not(equalTo(sampleUser)),
        )
    }
}
