/*
 * Copyright (c) 2021 Nicola Dardanis <nicdard@gmail.com>
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

public class DirectToastMakeTextUsageTest {

    @Language("JAVA")
    private final String stubToast = "                                      \n" +
            "package android.widget;                                        \n" +
            "public class Toast {                                           \n" +
            "                                                               \n" +
            "    public static Toast makeText(Context context,              \n" +
            "                                String text,                   \n" +
            "                                int duration) {                \n" +
            "         // Stub                                               \n" +
            "    }                                                          \n" +
            "}                                                              \n";

    @Language("JAVA")
    private final String javaFileToBeTested = "                             \n" +
            "package com.ichi2.anki.lint.rules;                             \n" +
            "                                                               \n" +
            "import android.widget.Toast;                                   \n" +
            "                                                               \n" +
            "public class TestJavaClass {                                   \n" +
            "                                                               \n" +
            "    public static void main(String[] args) {                   \n" +
            "        Toast.makeText();                                      \n" +
            "    }                                                          \n" +
            "}                                                              \n";

    @Language("JAVA")
    private final String javaFileWithUIUtils = "                            \n" +
            "package com.ichi2.anki.lint.rules;                             \n" +
            "                                                               \n" +
            "import android.widget.Toast;                                   \n" +
            "                                                               \n" +
            "public class UIUtils {                                         \n" +
            "                                                               \n" +
            "    public static void main(String[] args) {                   \n" +
            "        Toast.makeText();                                      \n" +
            "    }                                                          \n" +
            "}                                                              \n";


    @Test
    public void showsErrorsForInvalidUsage() {
        lint()
                .allowMissingSdk()
                .allowCompilationErrors()
                .files(create(stubToast), create(javaFileToBeTested))
                .issues(DirectToastMakeTextUsage.ISSUE)
                .run()
                .expectErrorCount(1)
                .check(output -> {
                    assertTrue(output.contains(DirectToastMakeTextUsage.ID));
                    assertTrue(output.contains(DirectToastMakeTextUsage.DESCRIPTION));
                });
    }

    @Test
    public void allowsUsageForUIUtils() {
        lint()
                .allowMissingSdk()
                .allowCompilationErrors()
                .files(create(stubToast), create(javaFileWithUIUtils))
                .issues(DirectToastMakeTextUsage.ISSUE)
                .run()
                .expectClean();
    }
}
