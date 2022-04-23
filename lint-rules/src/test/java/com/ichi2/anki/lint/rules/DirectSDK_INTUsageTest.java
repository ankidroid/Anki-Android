package com.ichi2.anki.lint.rules;

import org.intellij.lang.annotations.Language;
import org.junit.Test;

import static com.android.tools.lint.checks.infrastructure.TestLintTask.lint;
import static org.junit.Assert.assertTrue;
import static com.android.tools.lint.checks.infrastructure.TestFile.JavaTestFile.create;

public class DirectSDK_INTUsageTest {
    @Language("JAVA")
    private final String stubBuild = "package android.os;\n" +
            "public class Build {                         \n" +
            "public static class VERSION {                \n" +
            "public static final int SDK_INT = 0;         \n" +
            "}                                            \n"+
            "}";

    @Language("JAVA")
    private final String JavaTestIllegal = "" +
            "package com.ichi2.anki.lint.rules;                             \n" +
            "import android.os.Build;          \n" +
            "public class TestJavaClass {                                   \n" +
            "    public static void main(String[] args) {                   \n" +
            "        int sdk=Build.VERSION.SDK_INT;                \n" +
            "    }                                                          \n" +
            "}                                                              \n";

    @Language("JAVA")
    private final String TestWithCompat = "" +
            "package com.ichi2.anki.lint.rules;\n" +
            "import android.os.Build;\n" +
            "public class CompatHelper {\n" +
            "public static int getSdkVersion() {\n" +
            "        return Build.VERSION.SDK_INT;\n" +
            "    }\n" +
            "}";


    @Test
    public void showsErrorsForInvalidUsage() {
        lint()
                .allowMissingSdk()
                .allowCompilationErrors()
                .files(create(stubBuild), create(JavaTestIllegal))
                .issues(DirectSDK_INTUsage.ISSUE)
                .run()
                .expectErrorCount(1)
                .check(output -> {
                    assertTrue(output.contains(DirectSDK_INTUsage.ID));
                    assertTrue(output.contains(DirectSDK_INTUsage.DESCRIPTION));
                });
    }


    @Test
    public void allowsUsageForCompat() {
        lint()
                .allowMissingSdk()
                .allowCompilationErrors()
                .files(create(stubBuild), create(TestWithCompat))
                .issues(DirectSDK_INTUsage.ISSUE)
                .run()
                .expectClean();
    }
}
