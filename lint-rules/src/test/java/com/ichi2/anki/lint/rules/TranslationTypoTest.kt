/*
 *  Copyright (c) 2024 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki.lint.rules

import com.ichi2.anki.lint.testutils.assertXmlStringsHasError
import com.ichi2.anki.lint.testutils.assertXmlStringsNoIssues
import org.junit.Test

/**
 * Test of [TranslationTypo]
 */
class TranslationTypoTest {

    @Test
    fun `JavaScript is valid casing`() {
        val validCasing = """<resources>
           <string name="hello">JavaScript</string>
        </resources>"""

        TranslationTypo.ISSUE.assertXmlStringsNoIssues(validCasing)
    }

    @Test
    fun `title case fails`() {
        val invalidTitleCase = """<resources>
           <string name="hello">Javascript</string>
        </resources>"""

        TranslationTypo.ISSUE.assertXmlStringsHasError(invalidTitleCase, "should be 'JavaScript'")
    }

    @Test
    fun `lowercase fails`() {
        val invalidLowerCase = """<resources>
           <string name="hello">javascript</string>
        </resources>"""

        TranslationTypo.ISSUE.assertXmlStringsHasError(invalidLowerCase, "should be 'JavaScript'")
    }

    @Test
    fun `vandalism fails`() {
        val stringRemoved = """<resources>
           <string name="hello"></string>
        </resources>"""

        TranslationTypo.ISSUE.assertXmlStringsHasError(stringRemoved, "should not be empty")
    }

    @Test
    fun `vandalism passes with empty_string key`() {
        val stringRemoved = """<resources>
           <string name="empty_string"></string>
        </resources>"""

        TranslationTypo.ISSUE.assertXmlStringsNoIssues(stringRemoved)
    }

    /** A link to the string on Crowdin should be provided */
    @Test
    fun crowdinEditLinkIsProvided() {
        // Use links in the form: https://crowdin.com/editor/ankidroid/7290/en-af#q=create_subdeck
        // where 7290 is 01-core.xml, `en-af` is Afrikaans, and `create_subdeck` is the key

        // The actual link is https://crowdin.com/editor/ankidroid/7290/en-af#6534818, but
        // we don't have context to map from `create_subdeck` to `6534818`

        // We do not use '...', as this is not checked for RTL languages
        val xmlWithIssue = """<resources>
           <string name="create_subdeck">javascript</string>
        </resources>"""

        // 'standard' test
        TranslationTypo.ISSUE.assertXmlStringsHasError(
            xmlWithIssue,
            expectedError = "https://crowdin.com/editor/ankidroid/7290/en-af#q=create_subdeck",
            fileName = "01-core",
            androidLanguageFolder = "af"
        )

        // 02-strings -> 7291
        TranslationTypo.ISSUE.assertXmlStringsHasError(
            xmlWithIssue,
            expectedError = "https://crowdin.com/editor/ankidroid/7291/en-af#q=create_subdeck",
            fileName = "02-strings",
            androidLanguageFolder = "af"
        )

        // custom mapping: yue -> yu
        TranslationTypo.ISSUE.assertXmlStringsHasError(
            xmlWithIssue,
            expectedError = "https://crowdin.com/editor/ankidroid/7290/en-yu#q=create_subdeck",
            fileName = "01-core",
            androidLanguageFolder = "yue"
        )

        // Used region specifier: Chinese
        TranslationTypo.ISSUE.assertXmlStringsHasError(
            xmlWithIssue,
            expectedError = "https://crowdin.com/editor/ankidroid/7290/en-zhcn#q=create_subdeck",
            fileName = "01-core",
            androidLanguageFolder = "zh-rCN"
        )

        // no -> nnno
        TranslationTypo.ISSUE.assertXmlStringsHasError(
            xmlWithIssue,
            expectedError = "https://crowdin.com/editor/ankidroid/7290/en-nnno#q=create_subdeck",
            fileName = "01-core",
            androidLanguageFolder = "nn"
        )

        // ur -> urpa
        TranslationTypo.ISSUE.assertXmlStringsHasError(
            xmlWithIssue,
            expectedError = "https://crowdin.com/editor/ankidroid/7290/en-urpk#q=create_subdeck",
            fileName = "01-core",
            androidLanguageFolder = "ur"
        )
    }
}
