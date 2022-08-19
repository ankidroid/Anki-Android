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

import com.android.tools.lint.checks.infrastructure.TestFile.JavaTestFile
import com.android.tools.lint.checks.infrastructure.TestLintTask
import org.intellij.lang.annotations.Language
import org.junit.Assert
import org.junit.Test

@Suppress("UnstableApiUsage")
class InconsistentAnnotationUsageTest {
    @Language("JAVA")
    private val mNotNullUsage = """                                  
package java.util;                                             
                                                               
import org.jetbrains.annotations.NotNull;                      
"""

    @Language("JAVA")
    private val mNullable = """                                      
package java.util;                                             
                                                               
import org.jetbrains.annotations.Nullable;                     
"""

    // Should be OK
    @Language("JAVA")
    private val mContract = """                                      
package java.util;                                             
                                                               
import org.jetbrains.annotations.Contract;                     
"""

    @Test
    fun showsErrorForNotNull() {
        TestLintTask.lint()
            .allowMissingSdk()
            .allowCompilationErrors()
            .files(JavaTestFile.create(mNotNullUsage))
            .issues(InconsistentAnnotationUsage.ISSUE)
            .run()
            .expectErrorCount(1)
            .check({ output: String ->
                Assert.assertTrue(output.contains(InconsistentAnnotationUsage.ID))
                Assert.assertTrue(output.contains(InconsistentAnnotationUsage.DESCRIPTION))
            })
    }

    @Test
    fun showsErrorForNullable() {
        TestLintTask.lint()
            .allowMissingSdk()
            .allowCompilationErrors()
            .files(JavaTestFile.create(mNullable))
            .issues(InconsistentAnnotationUsage.ISSUE)
            .run()
            .expectErrorCount(1)
            .check({ output: String ->
                Assert.assertTrue(output.contains(InconsistentAnnotationUsage.ID))
                Assert.assertTrue(output.contains(InconsistentAnnotationUsage.DESCRIPTION))
            })
    }

    @Test
    fun noErrorForContract() {
        TestLintTask.lint()
            .allowMissingSdk()
            .allowCompilationErrors()
            .files(JavaTestFile.create(mContract))
            .issues(InconsistentAnnotationUsage.ISSUE)
            .run()
            .expectErrorCount(0)
    }
}
