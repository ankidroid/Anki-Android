/*
 *  Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki.lint.rules;

import org.intellij.lang.annotations.Language;
import org.junit.Test;

import static com.android.tools.lint.checks.infrastructure.TestLintTask.lint;
import static com.android.tools.lint.checks.infrastructure.TestFiles.xml;

public class DuplicateCrowdInStringsTest {

    /** Easiest test case: Two exact duplicates in the same file */
    @Language("XML")
    private final String selfInvalid = "<resources>\n" +
            "   <string name=\"hello\">a</string>\n" +
            "   <string name=\"hello2\">a</string>\n" +
            "</resources>";


    @Test
    public void duplicateStringsInSameFileDetected() {
        // This appears to be a bug in StringCasingDetector - string is self-referential.
        lint()
        .allowMissingSdk()
        .allowCompilationErrors()
        .files(xml("res/values/string.xml", selfInvalid))
        .issues(DuplicateCrowdInStrings.ISSUE)
        .run()
        .expectErrorCount(1);
    }
}
