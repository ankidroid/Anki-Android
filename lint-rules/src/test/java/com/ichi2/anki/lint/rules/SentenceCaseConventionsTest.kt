/*
 *  Copyright (c) 2025 David Allison <davidallisongithub@gmail.com>
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
import com.android.tools.lint.checks.infrastructure.TestLintResult
import com.android.tools.lint.checks.infrastructure.TestLintTask.lint
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.MatcherAssert.assertThat
import org.intellij.lang.annotations.Language
import org.junit.Test

class SentenceCaseConventionsTest {
    @Test
    fun `valid file has no error`() {
        checkSentenceCase(
            """<resources>
           |<string name="sentence_valid">Valid string</string>
           |</resources>
            """.trimMargin(),
        ).expectClean()
    }

    @Test
    fun `missing 'sentence_' prefix emits error`() {
        checkSentenceCase(
            """<resources>
            |<string name="invalid_prefix">missing 'sentence_' prefix</string>
            |</resources>
            """.trimMargin(),
        ).expectErrorCount(1)
            .check({ output: String ->
                assertThat("ID", output, containsString(SentenceCaseConventions.ID))
                assertThat("message", output, containsString("the 'name' attribute: 'invalid_prefix' should be prefixed with 'sentence_'"))
            })
    }

    private fun checkSentenceCase(
        @Language("XML") input: String,
    ): TestLintResult =
        lint()
            .allowMissingSdk()
            .allowCompilationErrors()
            .files(TestFiles.xml("res/values/sentence-case.xml", input))
            .issues(SentenceCaseConventions.ISSUE)
            .run()
}
