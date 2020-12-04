package com.ichi2.anki.lint.rules;

import org.intellij.lang.annotations.Language;
import org.junit.Test;

import static com.android.tools.lint.checks.infrastructure.TestFile.JavaTestFile.create;
import static com.android.tools.lint.checks.infrastructure.TestLintTask.lint;
import static org.junit.Assert.assertTrue;

public class DirectGregorianInstantiationTest {

    @Language("JAVA")
    private final String stubZoned = "                                      \n" +
            "package java.time;                                             \n" +
            "                                                               \n" +
            "public class ZonedDateTime {                                   \n" +
            "                                                               \n" +
            "                                                               \n" +
            "     public ZonedDateTime() {                                  \n" +
            "                                                               \n" +
            "     }                                                         \n" +
            "}                                                              \n";
    @Language("JAVA")
    private final String stubGregorian = "                                  \n" +
            "package java.util;                                             \n" +
            "                                                               \n" +
            "import java.time.ZonedDateTime;                                \n" +
            "                                                               \n" +
            "public class GregorianCalendar {                               \n" +
            "                                                               \n" +
            "    public GregorianCalendar() {                               \n" +
            "    }                                                          \n" +
            "                                                               \n" +
            "                                                               \n" +
            "    public static GregorianCalendar from(ZonedDateTime z) {    \n" +
            "        return null;                                           \n" +
            "    }                                                          \n" +
            "}                                                              \n";

    @Language("JAVA")
    private final String javaFileWithFromCall = "                           \n" +
            "package com.ichi2.anki.lint.rules;                             \n" +
            "                                                               \n" +
            "import java.util.GregorianCalendar;                            \n" +
            "                                                               \n" +
            "public class TestJavaClass {                                   \n" +
            "                                                               \n" +
            "    public static void main(String[] args) {                   \n" +
            "        GregorianCalendar gc = GregorianCalendar.from(null);   \n" +
            "    }                                                          \n" +
            "}                                                              \n";
    @Language("JAVA")
    private final String javaFileWithConstructorInvocation = "              \n" +
            "package com.ichi2.anki.lint.rules;                             \n" +
            "                                                               \n" +
            "import java.util.GregorianCalendar;                            \n" +
            "                                                               \n" +
            "public class TestJavaClass {                                   \n" +
            "                                                               \n" +
            "    public static void main(String[] args) {                   \n" +
            "        GregorianCalendar gc = new GregorianCalendar();        \n" +
            "    }                                                          \n" +
            "}                                                              \n";
    @Language("JAVA")
    private final String javaFileWithTime = "                               \n" +
            "package com.ichi2.anki.lint.rules;                             \n" +
            "                                                               \n" +
            "import java.util.GregorianCalendar;                            \n" +
            "                                                               \n" +
            "public class Time {                                            \n" +
            "                                                               \n" +
            "    public static void main(String[] args) {                   \n" +
            "        GregorianCalendar gc = new GregorianCalendar();        \n" +
            "    }                                                          \n" +
            "}                                                              \n";


    @Test
    public void showsErrorForInvalidUsage() {
        lint().
                allowMissingSdk().
                allowCompilationErrors()
                .files(create(stubZoned), create(stubGregorian), create(javaFileWithFromCall))
                .issues(DirectGregorianInstantiation.ISSUE)
                .run()
                .expectErrorCount(1)
                .check(output -> {
                    assertTrue(output.contains(DirectGregorianInstantiation.ID));
                    assertTrue(output.contains(DirectGregorianInstantiation.DESCRIPTION));
                });

        lint().
                allowMissingSdk().
                allowCompilationErrors()
                .files(create(stubZoned), create(stubGregorian), create(javaFileWithConstructorInvocation))
                .issues(DirectGregorianInstantiation.ISSUE)
                .run()
                .expectErrorCount(1)
                .check(output -> {
                    assertTrue(output.contains(DirectGregorianInstantiation.ID));
                    assertTrue(output.contains(DirectGregorianInstantiation.DESCRIPTION));
                });
    }


    @Test
    public void doesNotShowErrorsWhenUsedInTime() {
        lint().
                allowMissingSdk()
                .allowCompilationErrors()
                .files(create(stubGregorian), create(javaFileWithTime))
                .issues(DirectGregorianInstantiation.ISSUE)
                .run()
                .expectClean();
    }

}
