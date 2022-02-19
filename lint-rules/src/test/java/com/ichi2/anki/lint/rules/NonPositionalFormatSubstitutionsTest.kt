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

package com.ichi2.anki.lint.rules

import com.android.tools.lint.checks.infrastructure.TestFiles
import com.android.tools.lint.checks.infrastructure.TestLintTask
import org.intellij.lang.annotations.Language
import org.junit.Test

/** Test for [NonPositionalFormatSubstitutions] */
class NonPositionalFormatSubstitutionsTest {

    /** One substitution is unambiguous  */
    @Language("XML")
    private val valid = """<resources>
       <string name="hello">%s</string>
    </resources>"""

    /** Easiest test case: Two exact duplicates in the same file  */
    @Language("XML")
    private val invalid = """<resources>
       <string name="hello">%s %s</string>
    </resources>"""

    @Language("XML")
    private val unambiguous = "<resources><string name=\"hello\">%1\$s %2\$s</string></resources>"

    /** %% is an encoded percentage */
    @Language("XML")
    private val encoded = "<resources><string name=\"hello\">%%</string></resources>"

    @Test
    fun errors_if_ambiguous() {
        TestLintTask.lint()
            .allowMissingSdk()
            .allowCompilationErrors()
            .files(TestFiles.xml("res/values/string.xml", invalid))
            .issues(NonPositionalFormatSubstitutions.ISSUE)
            .run()
            .expectErrorCount(1)
    }

    @Test
    fun no_errors_if_valid() {
        TestLintTask.lint()
            .allowMissingSdk()
            .allowCompilationErrors()
            .files(TestFiles.xml("res/values/string.xml", valid))
            .issues(NonPositionalFormatSubstitutions.ISSUE)
            .run()
            .expectClean()
    }

    @Test
    fun no_errors_if_unambiguous() {
        TestLintTask.lint()
            .allowMissingSdk()
            .allowCompilationErrors()
            .files(TestFiles.xml("res/values/string.xml", unambiguous))
            .issues(NonPositionalFormatSubstitutions.ISSUE)
            .run()
            .expectClean()
    }

    @Test
    fun no_errors_if_encoded() {
        TestLintTask.lint()
            .allowMissingSdk()
            .allowCompilationErrors()
            .files(TestFiles.xml("res/values/string.xml", encoded))
            .issues(NonPositionalFormatSubstitutions.ISSUE)
            .run()
            .expectClean()
    }
}
