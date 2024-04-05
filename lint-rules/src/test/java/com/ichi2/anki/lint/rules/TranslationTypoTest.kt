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
}
