package com.ichi2.anki.lint.rules;

import org.intellij.lang.annotations.Language;
import org.junit.Test;

import static com.android.tools.lint.checks.infrastructure.TestFile.JavaTestFile.create;
import static com.android.tools.lint.checks.infrastructure.TestLintTask.lint;
import static org.junit.Assert.assertTrue;

public class DirectDateInstantiationTest {
    @Language("JAVA")
    private final String stubDate = "                         \n" +
            "package java.util;                               \n" +
            "                                                 \n" +
            "public class Date {                              \n" +
            "                                                 \n" +
            "    public Date() {                              \n" +
            "                                                 \n" +
            "    }                                            \n" +
            "    public Date(long time) {                     \n" +
            "                                                 \n" +
            "    }                                            \n" +
            "}                                                \n";

    @Language("JAVA")
    private final String javaFileToBeTested = "               \n" +
            "package com.ichi2.anki.lint.rules;               \n" +
            "                                                 \n" +
            "import java.util.Date;                           \n" +
            "                                                 \n" +
            "public class TestJavaClass {                     \n" +
            "                                                 \n" +
            "    public static void main(String[] args) {     \n" +
            "        Date d = new Date();                     \n" +
            "    }                                            \n" +
            "}                                                \n";
    @Language("JAVA")
    private final String javaFileWithTime = "                 \n" +
            "package com.ichi2.anki.lint.rules;               \n" +
            "                                                 \n" +
            "import java.util.Date;                           \n" +
            "                                                 \n" +
            "public abstract class Time {                     \n" +
            "                                                 \n" +
            "    public static void main(String[] args) {     \n" +
            "        Date d = new Date();                     \n" +
            "    }                                            \n" +
            "}                                                \n";
    @Language("JAVA")
    private final String javaFileUsingDateWithLong = "        \n" +
            "package com.ichi2.anki.lint.rules;               \n" +
            "                                                 \n" +
            "import java.util.Date;                           \n" +
            "                                                 \n" +
            "public class TestJavaClass {                     \n" +
            "                                                 \n" +
            "    public static void main(String[] args) {     \n" +
            "        Date d = new Date(1L);                   \n" +
            "    }                                            \n" +
            "}                                                \n";


    @Test
    public void showsErrorsForInvalidUsage() {
        lint().
                allowMissingSdk().
                allowCompilationErrors()
                .files(create(stubDate), create(javaFileToBeTested))
                .issues(DirectDateInstantiation.ISSUE)
                .run()
                .expectErrorCount(1)
                .check(output -> {
                    assertTrue(output.contains(DirectDateInstantiation.ID));
                    assertTrue(output.contains(DirectDateInstantiation.DESCRIPTION));
                });
    }

    @Test
    public void allowsUsageInTimeClass() {
        lint().
                allowMissingSdk().
                allowCompilationErrors()
                .files(create(stubDate), create(javaFileWithTime))
                .issues(DirectDateInstantiation.ISSUE)
                .run()
                .expectClean();
    }

    @Test
    public void allowsUsageWithLongValue() {
        lint().
                allowMissingSdk().
                allowCompilationErrors()
                .files(create(stubDate), create(javaFileUsingDateWithLong))
                .issues(DirectDateInstantiation.ISSUE)
                .run()
                .expectClean();
    }
}
