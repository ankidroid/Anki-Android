/*
 *  Copyright (c) 2021 Almas Ahmad <ahmadalmas.786.aa@gmail.com>
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

import com.android.tools.lint.checks.infrastructure.TestFile.JavaTestFile.create
import com.android.tools.lint.checks.infrastructure.TestLintTask.lint
import com.google.common.annotations.Beta
import org.intellij.lang.annotations.Language
import org.junit.Assert.assertTrue
import org.junit.Test

@Suppress("UnstableApiUsage")
@Beta
class CopyrightHeaderExistsTest {
    @Language("JAVA")
    private val mCopyrightHeader = """/*
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
 */"""

    @Language("JAVA")
    private val mNoCopyrightHeader =
        """
        
        package com.ichi2.upgrade;
        
        import com.ichi2.libanki.Collection;
        
        import org.json.JSONException;
        import org.json.JSONObject;
        
        import timber.log.Timber;
        
        public class Upgrade {
        }
        """.trimIndent()

    @Test
    fun fileWithCopyrightHeaderPasses() {
        lint()
            .allowMissingSdk()
            .files(create(mCopyrightHeader))
            .issues(CopyrightHeaderExists.ISSUE)
            .run()
            .expectClean()
    }

    @Test
    fun fileWithNoCopyrightHeaderFails() {
        lint()
            .allowMissingSdk()
            .allowCompilationErrors() // import failures
            .files(create(mNoCopyrightHeader))
            .issues(CopyrightHeaderExists.ISSUE)
            .run()
            .expectErrorCount(1)
            .check({ output: String ->
                assertTrue(output.contains(CopyrightHeaderExists.ID))
                assertTrue(output.contains(CopyrightHeaderExists.DESCRIPTION))
            })
    }
}
