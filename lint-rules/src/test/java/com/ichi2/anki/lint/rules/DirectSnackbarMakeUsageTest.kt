/*
 * Copyright (c) 2021 Mrudul Tora <mrudultora@gmail.com>
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

public class DirectSnackbarMakeUsageTest {
    @Language("JAVA")
    private final String stubSnackbar = "                                      \n" +
            "package com.google.android.material.snackbar;                     \n" +
            "public class Snackbar {                                           \n" +
            "                                                                  \n" +
            "    public static Snackbar make(View view,                        \n" +
            "                                CharSequence text,                \n" +
            "                                int duration) {                   \n" +
            "         // Stub                                                  \n" +
            "    }                                                             \n" +
            "}                                                                 \n";

    @Language("JAVA")
    private final String javaFileToBeTested = "                             \n" +
            "package com.ichi2.anki.lint.rules;                             \n" +
            "                                                               \n" +
            "import com.google.android.material.snackbar.Snackbar;          \n" +
            "                                                               \n" +
            "public class TestJavaClass {                                   \n" +
            "                                                               \n" +
            "    public static void main(String[] args) {                   \n" +
            "        Snackbar snackbar = Snackbar.make();                   \n" +
            "        snackbar.show();                                       \n" +
            "    }                                                          \n" +
            "}                                                              \n";

    @Language("JAVA")
    private final String javaFileWithUIUtils = "                            \n" +
            "package com.ichi2.anki.lint.rules;                             \n" +
            "                                                               \n" +
            "import com.google.android.material.snackbar.Snackbar;          \n" +
            "                                                               \n" +
            "public class UIUtils {                                         \n" +
            "                                                               \n" +
            "    public static void main(String[] args) {                   \n" +
            "        Snackbar snackbar = Snackbar.make();                   \n" +
            "        snackbar.show();                                       \n" +
            "    }                                                          \n" +
            "}                                                              \n";


    @Test
    public void showsErrorsForInvalidUsage() {
        lint()
                .allowMissingSdk()
                .allowCompilationErrors()
                .files(create(stubSnackbar), create(javaFileToBeTested))
                .issues(DirectSnackbarMakeUsage.ISSUE)
                .run()
                .expectErrorCount(1)
                .check(output -> {
                    assertTrue(output.contains(DirectSnackbarMakeUsage.ID));
                    assertTrue(output.contains(DirectSnackbarMakeUsage.DESCRIPTION));
                });
    }


    @Test
    public void allowsUsageForUIUtils() {
        lint()
                .allowMissingSdk()
                .allowCompilationErrors()
                .files(create(stubSnackbar), create(javaFileWithUIUtils))
                .issues(DirectSnackbarMakeUsage.ISSUE)
                .run()
                .expectClean();
    }
}