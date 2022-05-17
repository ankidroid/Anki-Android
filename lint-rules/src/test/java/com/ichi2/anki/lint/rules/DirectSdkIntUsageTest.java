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

package com.ichi2.anki.lint.rules;

import org.intellij.lang.annotations.Language;
import org.junit.Test;

import static com.android.tools.lint.checks.infrastructure.TestLintTask.lint;
import static org.junit.Assert.assertTrue;
import static com.android.tools.lint.checks.infrastructure.TestFile.JavaTestFile.create;

public class DirectSdkIntUsageTest {
    @Language("JAVA")
    private final String stubBuild = "                                      \n" +
            "package android.os;                                            \n" +
            "public class Build {                                           \n" +
            "    public static class VERSION {                              \n" +
            "        public static final int SDK_INT = 0;                   \n" +
            "    }                                                          \n" +
            "}";

    @Language("JAVA")
    private final String JavaTestIllegal = "                                \n" +
            "package com.ichi2.anki.lint.rules;                             \n" +
            "import android.os.Build;                                       \n" +
            "public class TestJavaClass {                                   \n" +
            "    public static void main(String[] args) {                   \n" +
            "        int sdk=Build.VERSION.SDK_INT;                         \n" +
            "    }                                                          \n" +
            "}                                                              \n";

    @Language("JAVA")
    private final String TestWithCompat = "                                 \n" +
            "package com.ichi2.anki.lint.rules;                             \n" +
            "import android.os.Build;                                       \n" +
            "public class CompatHelper {                                    \n" +
            "    public static int getSdkVersion() {                        \n" +
            "        return Build.VERSION.SDK_INT;                          \n" +
            "    }                                                          \n" +
            "}";


    @Test
    public void showsErrorsForInvalidUsage() {
        lint()
                .allowMissingSdk()
                .allowCompilationErrors()
                .files(create(stubBuild), create(JavaTestIllegal))
                .issues(DirectSdkIntUsage.ISSUE)
                .run()
                .expectErrorCount(1)
                .check(output -> {
                    assertTrue(output.contains(DirectSdkIntUsage.ID));
                    assertTrue(output.contains(DirectSdkIntUsage.DESCRIPTION));
                });
    }


    @Test
    public void allowsUsageForCompat() {
        lint()
                .allowMissingSdk()
                .allowCompilationErrors()
                .files(create(stubBuild), create(TestWithCompat))
                .issues(DirectSdkIntUsage.ISSUE)
                .run()
                .expectClean();
    }
}
