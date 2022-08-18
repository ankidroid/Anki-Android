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
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;

public class JavaConstantFieldDetectorTest {

    @Language("JAVA")
    private static final String BADLY_NAMED_VARIABLE = "public class Xx { " +
            "/**" +
            "*/" +
            "public static final int withCommentShows;" +
            "public static final int sThisHasPrefix;" +
            "public static final int mUsingMPrefix;" +
            "public static final int startsWithS;" +
            "public static final int maWithMStart;" +
            "}";

    @Test
    public void showsErrorForBadlyNamedConstant() {
        lint()
                .allowMissingSdk()
                .allowCompilationErrors()
                .files(create(BADLY_NAMED_VARIABLE))
                .issues(ConstantJavaFieldDetector.ISSUE)
                .run()
                .expectErrorCount(5)
                .check(output -> {
                    // check the suggestion is accurate
                    assertThat(output, containsString("'WITH_COMMENT_SHOWS'"));
                    assertThat(output, containsString("'THIS_HAS_PREFIX'"));
                    assertThat(output, containsString("'USING_M_PREFIX'"));
                    assertThat("s prefix is not stripped if in word", output, containsString("'STARTS_WITH_S'"));
                    assertThat("m prefix is not stripped if in word", output, containsString("'MA_WITH_M_START'"));
                });
    }

}
