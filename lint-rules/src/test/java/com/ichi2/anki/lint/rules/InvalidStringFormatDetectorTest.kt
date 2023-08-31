/*
 *  Copyright (c) 2022 Divyansh Dwivedi <justdvnsh2208@gmail.com>
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

import com.android.tools.lint.checks.infrastructure.TestFiles
import com.android.tools.lint.checks.infrastructure.TestLintTask
import org.intellij.lang.annotations.Language
import org.junit.Test

/** Test for [InvalidStringFormatDetectorTest] */
class InvalidStringFormatDetectorTest {

    @Language("XML")
    private val invalid = """<resources>
        |<string name="testString">I am a test% String</string>
        |<string name="testString">test%</string>
        |<string name="testString3">test% string</string>
        |<plurals name="pluralTestString1">
            <item quantity="other">आज%  %1${'$'}'d' मध्ये% %2${'$'}'s' कार्डांचा अभ्यास केला</item>
        </plurals>
        |</resources>
    """.trimMargin()

    @Language("XML")
    private val valid = """<resources>
        |<string name="testString">I am a test String</string>
        |<string name="testString">test</string>
        |<string name="testString3">test string</string>
        ||<string name="testString4">test string %s</string>
        |<string name="testString5">%%</string>
        |<string name="testString6">%1\$'d' is expected</string>
        |<plurals name="PluralTestString1">
            <item quantity="one">%1$'d' card (0 due)</item>
            <item quantity="other">%1$'d' cards (0 due)</item>
        </plurals>
        |<plurals name="pluralTestString">
            <item quantity="one">आज %1${'$'}'d' मध्ये %2${'$'}'s' कार्डचा अभ्यास केला</item>
        </plurals>
        |<string name="testString7">XXX%</string>
        |</resources>
    """.trimMargin()

    @Test
    fun error_if_string_format_invalid() {
        TestLintTask.lint()
            .allowMissingSdk()
            .allowCompilationErrors()
            .allowDuplicates()
            .files(TestFiles.xml("res/values/string.xml", invalid))
            .issues(InvalidStringFormatDetector.ISSUE)
            .run()
            .expectErrorCount(4)
    }

    @Test
    fun no_error_if_string_format_valid() {
        TestLintTask.lint()
            .allowMissingSdk()
            .allowCompilationErrors()
            .files(TestFiles.xml("res/values/string.xml", valid))
            .issues(InvalidStringFormatDetector.ISSUE)
            .run()
            .expectClean()
    }
}
