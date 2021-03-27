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

public class CopyrightHeaderCheckerTest {

    @Language("JAVA")
    private final String mCopyrightHeader = "/*\n" +
            " *  Copyright (c) $today.year David Allison <davidallisongithub@gmail.com>\n" +
            " *\n" +
            " *  This program is free software; you can redistribute it and/or modify it under\n" +
            " *  the terms of the GNU General Public License as published by the Free Software\n" +
            " *  Foundation; either version 3 of the License, or (at your option) any later\n" +
            " *  version.\n" +
            " *\n" +
            " *  This program is distributed in the hope that it will be useful, but WITHOUT ANY\n" +
            " *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A\n" +
            " *  PARTICULAR PURPOSE. See the GNU General Public License for more details.\n" +
            " *\n" +
            " *  You should have received a copy of the GNU General Public License along with\n" +
            " *  this program.  If not, see <http://www.gnu.org/licenses/>.\n" +
            " */";




    @Test
    public void showsErrorForNoCopyrightHeader() {
        lint()
                .allowMissingSdk()
                .allowCompilationErrors()
                .files(create(mCopyrightHeader))
                .issues(CopyrightHeaderChecker.ISSUE)
                .run()
                .expectErrorCount(1)
                .check(output -> {
                    assertTrue(output.contains(CopyrightHeaderChecker.ID));
                    assertTrue(output.contains(CopyrightHeaderChecker.DESCRIPTION));
                });
    }
}