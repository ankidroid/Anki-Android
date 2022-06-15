package com.ichi2.anki.lint.rules;

import org.intellij.lang.annotations.Language;
import org.junit.Test;

import static com.android.tools.lint.checks.infrastructure.TestFile.JavaTestFile.create;
import static com.android.tools.lint.checks.infrastructure.TestLintTask.lint;
import static org.junit.Assert.assertTrue;

public class DirectSystemTimeInstantiationTest {
    @Language("JAVA")
    private final String stubTime = "                         \n" +
            "package com.ichi2.libanki.utils;                 \n" +
            "                                                 \n" +
            "public abstract class Time {                     \n" +
            "                                                 \n" +
            "}                                                \n";
    @Language("JAVA")
    private final String stubSystemTime = "                   \n" +
            "package com.ichi2.libanki.utils;                 \n" +
            "                                                 \n" +
            "public class SystemTime extends Time {           \n" +
            "                                                 \n" +
            "    public SystemTime() {                        \n" +
            "    }                                            \n" +
            "                                                 \n" +
            "}                                                \n";
    @Language("JAVA")
    private final String javaFileToBeTested = "               \n" +
            "package com.ichi2.anki.lint.rules;               \n" +
            "                                                 \n" +
            "import com.ichi2.libanki.utils.SystemTime;       \n" +
            "                                                 \n" +
            "public class TestJavaClass {                     \n" +
            "                                                 \n" +
            "    public static void main(String[] args) {     \n" +
            "        SystemTime st = new SystemTime();        \n" +
            "    }                                            \n" +
            "}                                                \n";
    @Language("JAVA")
    private final String javaFileWithStorage = "              \n" +
            "package com.ichi2.anki.lint.rules;               \n" +
            "                                                 \n" +
            "import com.ichi2.libanki.utils.SystemTime;       \n" +
            "                                                 \n" +
            "public class Storage {                           \n" +
            "                                                 \n" +
            "    public static void main(String[] args) {     \n" +
            "        SystemTime st = new SystemTime();        \n" +
            "    }                                            \n" +
            "}                                                \n";
    @Language("JAVA")
    private final String javaFileWithCollectionHelper = "     \n" +
            "package com.ichi2.anki.lint.rules;               \n" +
            "                                                 \n" +
            "import com.ichi2.libanki.utils.SystemTime;       \n" +
            "                                                 \n" +
            "public class CollectionHelper {                  \n" +
            "                                                 \n" +
            "    public static void main(String[] args) {     \n" +
            "        SystemTime st = new SystemTime();        \n" +
            "    }                                            \n" +
            "}                                                \n";


    @Test
    public void showsErrorsForInvalidUsage() {
        lint()
                .allowMissingSdk()
                .allowCompilationErrors()
                .files(create(stubTime), create(stubSystemTime), create(javaFileToBeTested))
                .issues(DirectSystemTimeInstantiation.ISSUE)
                .run()
                .expectErrorCount(1)
                .check(output -> {
                    assertTrue(output.contains(DirectSystemTimeInstantiation.ID));
                    assertTrue(output.contains(DirectSystemTimeInstantiation.DESCRIPTION));
                });
    }


    @Test
    public void doesNotShowErrorsWhenUsedInStorage() {
        lint().
                allowMissingSdk()
                .allowCompilationErrors()
                .files(create(stubTime), create(stubSystemTime), create(javaFileWithStorage))
                .issues(DirectSystemTimeInstantiation.ISSUE)
                .run()
                .expectClean();
    }


    @Test
    public void doesNotShowErrorsWhenUsedInCollectionHelper() {
        lint().
                allowMissingSdk()
                .allowCompilationErrors()
                .files(create(stubTime), create(stubSystemTime), create(javaFileWithCollectionHelper))
                .issues(DirectSystemTimeInstantiation.ISSUE)
                .run()
                .expectClean();
    }
}
