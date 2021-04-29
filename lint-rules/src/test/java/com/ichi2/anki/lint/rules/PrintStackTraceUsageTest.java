/*
 *  Copyright (c) 2021 David Allison <davidallisongithub@gmail.com>
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

public class PrintStackTraceUsageTest {

    @Language("JAVA")
    private static final String printStackTraceUsage =
            "import java.io.IOException;          \n" +
            "public class Test {                  \n" +
            "    public Test() {                  \n" +
            "        try {                        \n" +
            "        } catch (IOException ex) {   \n" +
            "            ex.printStackTrace();    \n" +
            "        }                            \n" +
            "    }                                \n" +
            "}";

    @Language("JAVA")
    private static final String printStackTraceWithMethodArgument =
            "import java.io.IOException;                            \n" +
            "import java.io.PrintWriter;                            \n" +
            "public class Test {                                    \n" +
            "    public Test() {                                    \n" +
            "        try {                                          \n" +
            "        } catch (IOException ex) {                     \n" +
            "            ex.printStackTrace(new PrintWriter(sw));   \n" +
            "        }                                              \n" +
            "    }                                                  \n" +
            "}";



    @Test
    public void showsErrorForUsageWithNoParam() {
        lint()
                .allowMissingSdk()
                .files(create(printStackTraceUsage))
                .issues(PrintStackTraceUsage.ISSUE)
                .run()
                .expectErrorCount(1)
                .check(output -> {
                    assertTrue(output.contains(PrintStackTraceUsage.ID));
                });
    }

    @Test
    public void noErrorIfParamUsage() {
        // .check() is not required for the code to execute
        // If we have a parameter, we're not writing to stdout, so it's OK
        lint()
                .allowMissingSdk()
                .files(create(printStackTraceWithMethodArgument))
                .issues(PrintStackTraceUsage.ISSUE)
                .run()
                .expectErrorCount(0);
    }
}
