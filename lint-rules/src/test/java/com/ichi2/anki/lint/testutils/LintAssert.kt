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

package com.ichi2.anki.lint.testutils

import com.android.tools.lint.checks.infrastructure.TestFiles
import com.android.tools.lint.checks.infrastructure.TestLintTask
import com.android.tools.lint.detector.api.Issue
import org.intellij.lang.annotations.Language
import org.junit.Assert.assertTrue

fun Issue.assertXmlStringsNoIssues(@Language("XML") xmlFile: String) {
    TestLintTask.lint()
        .allowMissingSdk()
        .allowCompilationErrors()
        .files(TestFiles.xml("res/values/constants.xml", xmlFile))
        .issues(this)
        .run()
        .expectClean()
}

fun Issue.assertXmlStringsHasErrorCount(@Language("XML") xmlFile: String, expectedErrorCount: Int) {
    assert(expectedErrorCount > 0) { "Use assertXmlStringsNoIssues" }
    TestLintTask.lint()
        .allowMissingSdk()
        .allowCompilationErrors()
        .files(TestFiles.xml("res/values/constants.xml", xmlFile))
        .issues(this)
        .run()
        .expectErrorCount(expectedErrorCount)
}

fun Issue.assertXmlStringsHasError(@Language("XML") xmlFile: String, expectedError: String) {
    TestLintTask.lint()
        .allowMissingSdk()
        .allowCompilationErrors()
        .files(TestFiles.xml("res/values/constants.xml", xmlFile))
        .issues(this)
        .run()
        .expectErrorCount(1)
        .check({ output: String ->
            assertTrue(
                "check should fail wth '$expectedError', but was '$output'",
                output.contains(expectedError)
            )
        })
}
