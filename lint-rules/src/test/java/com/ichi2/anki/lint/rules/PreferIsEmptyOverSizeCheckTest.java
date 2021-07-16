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

import java.io.IOException;

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
    private static final String fileToBeTested1 = "                         \n" +
            "package com.ichi2.anki.lint.rules;                             \n" +
            "import java.util.*;                                            \n" +
            "                                                               \n" +
            "class MyTest {                                                 \n" +
            "   private static final int ZERO = 0;                          \n" +
            "   private List<String> ls = new ArrayList<>();                \n" +
            "                                                               \n" +
            "   public boolean isEmpty1() {                                 \n" +
            "       return this.ls.size() == 0;                             \n" +
            "   }                                                           \n" +
            "                                                               \n" +
            "   public boolean isEmpty2() {                                 \n" +
            "       return this.ls.size() == MyTest.ZERO;                   \n" +
            "   }                                                           \n" +
            "                                                               \n" +
            "   public boolean isEmpty3() {                                 \n" +
            "       List<String> s;                                         \n" +
            "       return (s != null ? s : this.ls).size() == MyTest.ZERO; \n" +
            "   }                                                           \n" +
            "                                                               \n" +
            "   public boolean isEmpty4() {                                 \n" +
            "       List<String> s;                                         \n" +
            "       return this.ls.size() <= MyTest.ZERO;                   \n" +
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
            "                                                               \n" +
            "   public boolean isNotEmpty3() {                              \n" +
            "       List<String> s;                                         \n" +
            "       return (s != null ? s : this.ls).size() > MyTest.ZERO;  \n" +
            "   }                                                           \n" +
            "}                                                              \n";

    @Language("JAVA")
    private static final String fileToBeTested2 = "                         \n" +
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
    private static final String fileToBeTested3 = "                         \n" +
            "package com.ichi2.anki.lint.rules;                             \n" +
            "import java.util.*;                                            \n" +
            "                                                               \n" +
            "class MyTest {                                                 \n" +
            "   private List<String> ls = new ArrayList<>();                \n" +
            "   private List<String> ls2 = new ArrayList<>();               \n" +
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
            .files(create(fileToBeTested1))
            .issues(PreferIsEmptyOverSizeCheck.ISSUE)
            .run()
            .expect("" +
                "src/com/ichi2/anki/lint/rules/MyTest.java:10: Error: Always prefer isEmpty instead of size comparison when possible [PreferIsEmptyOverSizeCheck]\n" +
                "       return this.ls.size() == 0;                             \n" +
                "              ~~~~~~~~~~~~~~~~~~~\n" +
                "src/com/ichi2/anki/lint/rules/MyTest.java:14: Error: Always prefer isEmpty instead of size comparison when possible [PreferIsEmptyOverSizeCheck]\n" +
                "       return this.ls.size() == MyTest.ZERO;                   \n" +
                "              ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
                "src/com/ichi2/anki/lint/rules/MyTest.java:19: Error: Always prefer isEmpty instead of size comparison when possible [PreferIsEmptyOverSizeCheck]\n" +
                "       return (s != null ? s : this.ls).size() == MyTest.ZERO; \n" +
                "              ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
                "src/com/ichi2/anki/lint/rules/MyTest.java:24: Error: Always prefer isEmpty instead of size comparison when possible [PreferIsEmptyOverSizeCheck]\n" +
                "       return this.ls.size() <= MyTest.ZERO;                   \n" +
                "              ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
                "src/com/ichi2/anki/lint/rules/MyTest.java:28: Error: Always prefer isEmpty instead of size comparison when possible [PreferIsEmptyOverSizeCheck]\n" +
                "       return this.ls.size() < 1;                              \n" +
                "              ~~~~~~~~~~~~~~~~~~\n" +
                "src/com/ichi2/anki/lint/rules/MyTest.java:32: Error: Always prefer isEmpty instead of size comparison when possible [PreferIsEmptyOverSizeCheck]\n" +
                "       return this.ls.size() > 0;                              \n" +
                "              ~~~~~~~~~~~~~~~~~~\n" +
                "src/com/ichi2/anki/lint/rules/MyTest.java:36: Error: Always prefer isEmpty instead of size comparison when possible [PreferIsEmptyOverSizeCheck]\n" +
                "       return this.ls.size() >= 1;                             \n" +
                "              ~~~~~~~~~~~~~~~~~~~\n" +
                "src/com/ichi2/anki/lint/rules/MyTest.java:41: Error: Always prefer isEmpty instead of size comparison when possible [PreferIsEmptyOverSizeCheck]\n" +
                "       return (s != null ? s : this.ls).size() > MyTest.ZERO;  \n" +
                "              ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
                "8 errors, 0 warnings"
            ).expectFixDiffs("" +
                "Fix for src/com/ichi2/anki/lint/rules/MyTest.java line 10: Replace with this.ls.isEmpty():\n" +
                "@@ -10 +10\n" +
                "-        return this.ls.size() == 0;                             \n" +
                "+        return this.ls.isEmpty();                             \n" +
                "Fix for src/com/ichi2/anki/lint/rules/MyTest.java line 14: Replace with this.ls.isEmpty():\n" +
                "@@ -14 +14\n" +
                "-        return this.ls.size() == MyTest.ZERO;                   \n" +
                "+        return this.ls.isEmpty();                   \n" +
                "Fix for src/com/ichi2/anki/lint/rules/MyTest.java line 19: Replace with (s != null ? s : this.ls).isEmpty():\n" +
                "@@ -19 +19\n" +
                "-        return (s != null ? s : this.ls).size() == MyTest.ZERO; \n" +
                "+        return (s != null ? s : this.ls).isEmpty(); \n" +
                "Fix for src/com/ichi2/anki/lint/rules/MyTest.java line 24: Replace with this.ls.isEmpty():\n" +
                "@@ -24 +24\n" +
                "-        return this.ls.size() <= MyTest.ZERO;                   \n" +
                "+        return this.ls.isEmpty();                   \n" +
                "Fix for src/com/ichi2/anki/lint/rules/MyTest.java line 28: Replace with this.ls.isEmpty():\n" +
                "@@ -28 +28\n" +
                "-        return this.ls.size() < 1;                              \n" +
                "+        return this.ls.isEmpty();                              \n" +
                "Fix for src/com/ichi2/anki/lint/rules/MyTest.java line 32: Replace with !this.ls.isEmpty():\n" +
                "@@ -32 +32\n" +
                "-        return this.ls.size() > 0;                              \n" +
                "+        return !this.ls.isEmpty();                              \n" +
                "Fix for src/com/ichi2/anki/lint/rules/MyTest.java line 36: Replace with !this.ls.isEmpty():\n" +
                "@@ -36 +36\n" +
                "-        return this.ls.size() >= 1;                             \n" +
                "+        return !this.ls.isEmpty();                             \n" +
                "Fix for src/com/ichi2/anki/lint/rules/MyTest.java line 41: Replace with !(s != null ? s : this.ls).isEmpty():\n" +
                "@@ -41 +41\n" +
                "-        return (s != null ? s : this.ls).size() > MyTest.ZERO;  \n" +
                "+        return !(s != null ? s : this.ls).isEmpty();  "
        );
    }

    @Test
    public void shouldIgnoreWhenIsEmptyMissing() {
        lint()
            .allowMissingSdk()
            .allowCompilationErrors()
            .files(create(stubListWithoutIsEmpty), create(fileToBeTested2))
            .issues(PreferIsEmptyOverSizeCheck.ISSUE)
            .run()
            .expect("No warnings.");
    }

    @Test
    public void shouldIgnoreWhenSizeCheckingGreaterOne() {
        lint()
            .allowMissingSdk()
            .allowCompilationErrors()
            .files(create(fileToBeTested3))
            .issues(PreferIsEmptyOverSizeCheck.ISSUE)
            .run()
            .expect("No warnings.");
    }
}
