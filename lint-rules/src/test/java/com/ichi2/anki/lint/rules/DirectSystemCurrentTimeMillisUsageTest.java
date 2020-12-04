package com.ichi2.anki.lint.rules;

import org.intellij.lang.annotations.Language;
import org.junit.Test;

import static com.android.tools.lint.checks.infrastructure.TestFile.JavaTestFile.create;
import static com.android.tools.lint.checks.infrastructure.TestLintTask.lint;
import static org.junit.Assert.assertTrue;

public class DirectSystemCurrentTimeMillisUsageTest {

    @Language("JAVA")
    private final String stubSystem = "                                     \n" +
            "package java.lang;                                             \n" +
            "                                                               \n" +
            "public class System {                                          \n" +
            "                                                               \n" +
            "    public static long currentTimeMillis() {                   \n" +
            "         return 1L;                                            \n" +
            "    }                                                          \n" +
            "}                                                              \n";

    @Language("JAVA")
    private final String javaFileToBeTested = "                             \n" +
            "package com.ichi2.anki.lint.rules;                             \n" +
            "                                                               \n" +
            "import java.lang.System;                                       \n" +
            "                                                               \n" +
            "public class TestJavaClass {                                   \n" +
            "                                                               \n" +
            "    public static void main(String[] args) {                   \n" +
            "        long time = System.currentTimeMillis();                \n" +
            "    }                                                          \n" +
            "}                                                              \n";
    @Language("JAVA")
    private final String javaFileWithSystemTime = "                         \n" +
            "package com.ichi2.anki.lint.rules;                             \n" +
            "                                                               \n" +
            "import java.lang.System;                                       \n" +
            "                                                               \n" +
            "public class SystemTime {                                      \n" +
            "                                                               \n" +
            "    public static void main(String[] args) {                   \n" +
            "        long time = System.currentTimeMillis();                \n" +
            "    }                                                          \n" +
            "}                                                              \n";


    @Test
    public void showsErrorsForInvalidUsage() {
        lint().
                allowMissingSdk().
                allowCompilationErrors()
                .files(create(stubSystem), create(javaFileToBeTested))
                .issues(DirectSystemCurrentTimeMillisUsage.ISSUE)
                .run()
                .expectErrorCount(1)
                .check(output -> {
                    assertTrue(output.contains(DirectSystemCurrentTimeMillisUsage.ID));
                    assertTrue(output.contains(DirectSystemCurrentTimeMillisUsage.DESCRIPTION));
                });
    }

    @Test
    public void allowsUsageForSystemTime() {
        lint().
                allowMissingSdk().
                allowCompilationErrors()
                .files(create(stubSystem), create(javaFileWithSystemTime))
                .issues(DirectSystemCurrentTimeMillisUsage.ISSUE)
                .run()
                .expectClean();
    }
}
