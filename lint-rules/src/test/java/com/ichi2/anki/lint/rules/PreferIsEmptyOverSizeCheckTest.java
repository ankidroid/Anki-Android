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

public class PreferIsEmptyOverSizeCheckTest {

    @Language("JAVA")
    private static final String stubListWithoutIsEmpty = "                  \n" +
            "package com.ichi2.anki.lint.rules;                             \n" +
            "import java.util.*;                                            \n" +
            "                                                               \n" +
            "class SizeNoEmpty {                                            \n" +
            "   private List<String> ls = new ArrayList<>();                \n" +
            "                                                               \n" +
            "   public boolean add(String s) {                              \n" +
            "       return this.ls.add(s);                                  \n" +
            "   }                                                           \n" +
            "                                                               \n" +
            "   public int size() {                                         \n" +
            "       return this.ls.size();                                  \n" +
            "   }                                                           \n" +
            "}                                                              \n";


    @Language("JAVA")
    private static final String discoverableFile = "                        \n" +
            "package com.ichi2.anki.lint.rules;                             \n" +
            "import java.util.*;                                            \n" +
            "                                                               \n" +
            "class MyTest {                                                 \n" +
            "   private List<String> ls = new ArrayList<>();                \n" +
            "                                                               \n" +
            "   public boolean isEmpty1() {                                 \n" +
            "       return this.ls.size() == 0;                             \n" +
            "   }                                                           \n" +
            "                                                               \n" +
            "   public boolean isEmpty5() {                                 \n" +
            "       return this.ls.size() < 1;                              \n" +
            "   }                                                           \n" +
            "                                                               \n" +
            "   public boolean isNotEmpty1() {                              \n" +
            "       return this.ls.size() > 0;                              \n" +
            "   }                                                           \n" +
            "                                                               \n" +
            "   public boolean isNotEmpty2() {                              \n" +
            "       return this.ls.size() >= 1;                             \n" +
            "   }                                                           \n" +
            "}                                                              \n";

    @Language("JAVA")
    private static final String missingIsEmptyFile = "                      \n" +
            "package com.ichi2.anki.lint.rules;                             \n" +
            "import com.ichi2.anki.lint.rules.MyList;                       \n" +
            "                                                               \n" +
            "class MyTest {                                                 \n" +
            "   private static final int ZERO = 0;                          \n" +
            "   private SizeNoEmpty ls = new SizeNoEmpty<>();               \n" +
            "                                                               \n" +
            "   public boolean isEmpty1() {                                 \n" +
            "       return this.ls.size() == 0;                             \n" +
            "   }                                                           \n" +
            "                                                               \n" +
            "   public boolean isEmpty2() {                                 \n" +
            "       return this.ls.size() == MyTest.ZERO;                   \n" +
            "   }                                                           \n" +
            "}                                                              \n";

    @Language("JAVA")
    private static final String greaterThenOneFile = "                      \n" +
            "package com.ichi2.anki.lint.rules;                             \n" +
            "import java.util.*;                                            \n" +
            "                                                               \n" +
            "class MyTest {                                                 \n" +
            "   private List<String> ls = new ArrayList<>();                \n" +
            "                                                               \n" +
            "   public boolean isEqual2() {                                 \n" +
            "       return this.ls.size() == 2;                             \n" +
            "   }                                                           \n" +
            "                                                               \n" +
            "   public boolean isEqualOrGrater0() {                         \n" +
            "       return this.ls.size() >= 2;                             \n" +
            "   }                                                           \n" +
            "                                                               \n" +
            "   public boolean isGreater1() {                               \n" +
            "       return this.ls.size() > 1;                              \n" +
            "   }                                                           \n" +
            "}                                                              \n";

    @Language("JAVA")
    private static final String nonLiteralExpressionFile = "                \n" +
            "package com.ichi2.anki.lint.rules;                             \n" +
            "import java.util.*;                                            \n" +
            "                                                               \n" +
            "class MyTest {                                                 \n" +
            "   private static final int ZERO = 0;                          \n" +
            "   private List<String> ls = new ArrayList<>();                \n" +
            "   private List<String> ls2 = new ArrayList<>();               \n" +
            "                                                               \n" +
            "   public boolean isEmptyNotLiteral1() {                       \n" +
            "       return this.ls.size() == MyTest.ZERO;                   \n" +
            "   }                                                           \n" +
            "                                                               \n" +
            "   public boolean isEmptyNotLiteral2() {                       \n" +
            "       List<String> s;                                         \n" +
            "       return (s != null ? s : this.ls).size() == MyTest.ZERO; \n" +
            "   }                                                           \n" +
            "                                                               \n" +
            "   public boolean isEmptyNotLiteral3() {                       \n" +
            "       return this.ls.size() <= MyTest.ZERO;                   \n" +
            "   }                                                           \n" +
            "                                                               \n" +
            "   public boolean isNotEmptyNotLiteral() {                     \n" +
            "       List<String> s;                                         \n" +
            "       return (s != null ? s : this.ls).size() > MyTest.ZERO;  \n" +
            "   }                                                           \n" +
            "                                                               \n" +
            "   public boolean sizesAreEqual0() {                           \n" +
            "       return this.ls.size() == this.ls2.size();               \n" +
            "   }                                                           \n" +
            "}                                                              \n";


    @Test
    public void showErrorsForSizeChecks() {
        lint()
            .allowMissingSdk()
            .allowCompilationErrors()
            .files(create(discoverableFile))
            .issues(PreferIsEmptyOverSizeCheck.ISSUE)
            .run()
            .expect("" +
                "src/com/ichi2/anki/lint/rules/MyTest.java:9: Error: Always prefer isEmpty instead of size comparison when possible [PreferIsEmptyOverSize]\n" +
                "       return this.ls.size() == 0;                             \n" +
                "              ~~~~~~~~~~~~~~~~~~~\n" +
                "src/com/ichi2/anki/lint/rules/MyTest.java:13: Error: Always prefer isEmpty instead of size comparison when possible [PreferIsEmptyOverSize]\n" +
                "       return this.ls.size() < 1;                              \n" +
                "              ~~~~~~~~~~~~~~~~~~\n" +
                "src/com/ichi2/anki/lint/rules/MyTest.java:17: Error: Always prefer isEmpty instead of size comparison when possible [PreferIsEmptyOverSize]\n" +
                "       return this.ls.size() > 0;                              \n" +
                "              ~~~~~~~~~~~~~~~~~~\n" +
                "src/com/ichi2/anki/lint/rules/MyTest.java:21: Error: Always prefer isEmpty instead of size comparison when possible [PreferIsEmptyOverSize]\n" +
                "       return this.ls.size() >= 1;                             \n" +
                "              ~~~~~~~~~~~~~~~~~~~\n" +
                "4 errors, 0 warnings"
            ).expectFixDiffs("" +
                "Fix for src/com/ichi2/anki/lint/rules/MyTest.java line 9: Replace with this.ls.isEmpty():\n" +
                "@@ -9 +9\n" +
                "-        return this.ls.size() == 0;                             \n" +
                "+        return this.ls.isEmpty();                             \n" +
                "Fix for src/com/ichi2/anki/lint/rules/MyTest.java line 13: Replace with this.ls.isEmpty():\n" +
                "@@ -13 +13\n" +
                "-        return this.ls.size() < 1;                              \n" +
                "+        return this.ls.isEmpty();                              \n" +
                "Fix for src/com/ichi2/anki/lint/rules/MyTest.java line 17: Replace with !this.ls.isEmpty():\n" +
                "@@ -17 +17\n" +
                "-        return this.ls.size() > 0;                              \n" +
                "+        return !this.ls.isEmpty();                              \n" +
                "Fix for src/com/ichi2/anki/lint/rules/MyTest.java line 21: Replace with !this.ls.isEmpty():\n" +
                "@@ -21 +21\n" +
                "-        return this.ls.size() >= 1;                             \n" +
                "+        return !this.ls.isEmpty();                             "
        );
    }

    @Test
    public void shouldIgnoreWhenIsEmptyMissing() {
        lint()
            .allowMissingSdk()
            .allowCompilationErrors()
            .files(create(stubListWithoutIsEmpty), create(missingIsEmptyFile))
            .issues(PreferIsEmptyOverSizeCheck.ISSUE)
            .run()
            .expect("No warnings.");
    }

    @Test
    public void shouldIgnoreWhenSizeCheckingGreaterOne() {
        lint()
            .allowMissingSdk()
            .allowCompilationErrors()
            .files(create(greaterThenOneFile))
            .issues(PreferIsEmptyOverSizeCheck.ISSUE)
            .run()
            .expect("No warnings.");
    }

    @Test
    public void shouldIgnoreWhenSizeCheckingWithNonLiteral() {
        lint()
            .allowMissingSdk()
            .allowCompilationErrors()
            .files(create(nonLiteralExpressionFile))
            .issues(PreferIsEmptyOverSizeCheck.ISSUE)
            .run()
            .expect("No warnings.");
    }
}
