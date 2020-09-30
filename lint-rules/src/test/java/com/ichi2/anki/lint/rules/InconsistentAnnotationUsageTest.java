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

import static com.android.tools.lint.checks.infrastructure.TestFile.JavaTestFile.create;
import static com.android.tools.lint.checks.infrastructure.TestLintTask.lint;
import static org.junit.Assert.assertTrue;

public class InconsistentAnnotationUsageTest {

    @Language("JAVA")
    private final String mNotNullUsage = "                                  \n" +
            "package java.util;                                             \n" +
            "                                                               \n" +
            "import org.jetbrains.annotations.NotNull;                      \n";

    @Language("JAVA")
    private final String mNullable = "                                      \n" +
            "package java.util;                                             \n" +
            "                                                               \n" +
            "import org.jetbrains.annotations.Nullable;                     \n";

    // Should be OK
    @Language("JAVA")
    private final String mContract = "                                      \n" +
            "package java.util;                                             \n" +
            "                                                               \n" +
            "import org.jetbrains.annotations.Contract;                     \n";



    @Test
    public void showsErrorForNotNull() {
        lint()
                .allowMissingSdk()
                .allowCompilationErrors()
                .files(create(mNotNullUsage))
                .issues(InconsistentAnnotationUsage.ISSUE)
                .run()
                .expectErrorCount(1)
                .check(output -> {
                    assertTrue(output.contains(InconsistentAnnotationUsage.ID));
                    assertTrue(output.contains(InconsistentAnnotationUsage.DESCRIPTION));
                });
    }

    @Test
    public void showsErrorForNullable() {
        lint()
                .allowMissingSdk()
                .allowCompilationErrors()
                .files(create(mNullable))
                .issues(InconsistentAnnotationUsage.ISSUE)
                .run()
                .expectErrorCount(1)
                .check(output -> {
                    assertTrue(output.contains(InconsistentAnnotationUsage.ID));
                    assertTrue(output.contains(InconsistentAnnotationUsage.DESCRIPTION));
                });
    }

    @Test
    public void noErrorForContract() {
        lint()
                .allowMissingSdk()
                .allowCompilationErrors()
                .files(create(mContract))
                .issues(InconsistentAnnotationUsage.ISSUE)
                .run()
                .expectErrorCount(0);
    }
}
