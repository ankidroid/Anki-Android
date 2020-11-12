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
    private final String mSelfInvalid = "<resources>\n" +
            "   <string name=\"hello\">a</string>\n" +
            "   <string name=\"hello2\">a</string>\n" +
            "</resources>";

    @Language("XML")
    private final String mFirstCommentButInvalid = "<resources>\n" +
            "   <string name=\"hello\" comment=\"hello\">a</string>\n" +
            "   <string name=\"hello2\">a</string>\n" +
            "</resources>";

    @Language("XML")
    private final String mSecondCommentButInvalid = "<resources>\n" +
            "   <string name=\"hello\">a</string>\n" +
            "   <string name=\"hello2\" comment=\"hello\">a</string>\n" +
            "</resources>";

    @Language("XML")
    private final String mDuplicateBothValid = "<resources>\n" +
            "   <string name=\"hello\" comment=\"hello\">a</string>\n" +
            "   <string name=\"hello2\" comment=\"hello\">a</string>\n" +
            "</resources>";

    @Test
    public void duplicateStringsInSameFileDetected() {
        // This appears to be a bug in StringCasingDetector - string is self-referential.
        lint()
        .allowMissingSdk()
        .allowCompilationErrors()
        .files(xml("res/values/string.xml", mSelfInvalid))
        .issues(DuplicateCrowdInStrings.ISSUE)
        .run()
        .expectErrorCount(1);
    }

    @Test
    public void invalidStringInNonEnglishPasses() {
        // We only want to check the base resource files, not the translated ones -
        // translators know if values are equivalent and do not require a comment explaining why.
        lint()
        .allowMissingSdk()
        .allowCompilationErrors()
        .files(xml("res/values-af/string.xml", mSelfInvalid))
        .issues(DuplicateCrowdInStrings.ISSUE)
        .run()
        .expectErrorCount(0);
    }

    @Test
    public void duplicateStringWithFirstCommentFails() {
        lint()
        .allowMissingSdk()
        .allowCompilationErrors()
        .files(xml("res/values/string.xml", mFirstCommentButInvalid))
        .issues(DuplicateCrowdInStrings.ISSUE)
        .run()
        .expectErrorCount(1);
    }

    @Test
    public void duplicateStringWithSecondCommentFails() {
        lint()
        .allowMissingSdk()
        .allowCompilationErrors()
        .files(xml("res/values/string.xml", mSecondCommentButInvalid))
        .issues(DuplicateCrowdInStrings.ISSUE)
        .run()
        .expectErrorCount(1);
    }

    @Test
    public void duplicateStringWithBothCommentsPasses() {
        lint()
        .allowMissingSdk()
        .allowCompilationErrors()
        .files(xml("res/values/string.xml", mDuplicateBothValid))
        .issues(DuplicateCrowdInStrings.ISSUE)
        .run()
        .expectErrorCount(0);
    }
}
