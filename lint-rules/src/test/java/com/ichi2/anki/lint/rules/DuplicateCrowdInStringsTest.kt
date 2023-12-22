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
package com.ichi2.anki.lint.rules

import com.android.tools.lint.checks.infrastructure.TestFiles
import com.android.tools.lint.checks.infrastructure.TestLintTask.*
import org.intellij.lang.annotations.Language
import org.junit.Test

class DuplicateCrowdInStringsTest {
    /** Easiest test case: Two exact duplicates in the same file  */
    @Language("XML")
    private val mSelfInvalid = """<resources>
   <string name="hello">a</string>
   <string name="hello2">a</string>
</resources>"""

    @Language("XML")
    private val mFirstCommentButInvalid = """<resources>
   <string name="hello" comment="hello">a</string>
   <string name="hello2">a</string>
</resources>"""

    @Language("XML")
    private val mSecondCommentButInvalid = """<resources>
   <string name="hello">a</string>
   <string name="hello2" comment="hello">a</string>
</resources>"""

    @Language("XML")
    private val mDuplicateBothValid = """<resources>
   <string name="hello" comment="hello">a</string>
   <string name="hello2" comment="hello">a</string>
</resources>"""

    @Language("XML")
    private val mIgnoredFile =
        """<resources xmlns:tools="http://schemas.android.com/tools" tools:ignore="AAA,DuplicateCrowdInStrings">
   <string name="hello">a</string>
   <string name="hello2">a</string>
</resources>"""

    @Language("XML")
    private val mNotIgnored = """<resources>
   <string name="hello3">a</string>
</resources>"""

    @Test
    fun duplicateStringsInSameFileDetected() {
        // This appears to be a bug in StringCasingDetector - string is self-referential.
        lint()
            .allowMissingSdk()
            .allowCompilationErrors()
            .files(TestFiles.xml("res/values/string.xml", mSelfInvalid))
            .issues(DuplicateCrowdInStrings.ISSUE)
            .run()
            .expectErrorCount(1)
    }

    @Test
    fun invalidStringInNonEnglishPasses() {
        // We only want to check the base resource files, not the translated ones -
        // translators know if values are equivalent and do not require a comment explaining why.
        lint()
            .allowMissingSdk()
            .allowCompilationErrors()
            .files(TestFiles.xml("res/values-af/string.xml", mSelfInvalid))
            .issues(DuplicateCrowdInStrings.ISSUE)
            .run()
            .expectErrorCount(0)
    }

    @Test
    fun duplicateStringWithFirstCommentFails() {
        lint()
            .allowMissingSdk()
            .allowCompilationErrors()
            .files(TestFiles.xml("res/values/string.xml", mFirstCommentButInvalid))
            .issues(DuplicateCrowdInStrings.ISSUE)
            .run()
            .expectErrorCount(1)
    }

    @Test
    fun duplicateStringWithSecondCommentFails() {
        lint()
            .allowMissingSdk()
            .allowCompilationErrors()
            .files(TestFiles.xml("res/values/string.xml", mSecondCommentButInvalid))
            .issues(DuplicateCrowdInStrings.ISSUE)
            .run()
            .expectErrorCount(1)
    }

    @Test
    fun duplicateStringWithBothCommentsPasses() {
        lint()
            .allowMissingSdk()
            .allowCompilationErrors()
            .files(TestFiles.xml("res/values/string.xml", mDuplicateBothValid))
            .issues(DuplicateCrowdInStrings.ISSUE)
            .run()
            .expectErrorCount(0)
    }

    @Test
    fun ignoredFilePasses() {
        // TODO: this doesn't work over two files. If the tools:ignore is removed, only the single file fails
        lint()
            .allowMissingSdk()
            .allowCompilationErrors()
            .files(
                TestFiles.xml("res/values/constants.xml", mIgnoredFile),
                TestFiles.xml("res/values/strings.xml", mNotIgnored),
            )
            .issues(DuplicateCrowdInStrings.ISSUE)
            .run()
            .expectErrorCount(0)
    }
}
