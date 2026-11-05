// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.lint.rules

import com.android.tools.lint.checks.infrastructure.TestFile
import com.android.tools.lint.checks.infrastructure.TestFiles.kotlin
import com.android.tools.lint.checks.infrastructure.TestLintResult
import com.android.tools.lint.checks.infrastructure.TestLintTask.lint
import com.ichi2.anki.lint.rules.TestNameLengthDetector.Companion.MAX_TEST_NAME_LENGTH
import org.junit.Test

class TestNameLengthDetectorTest {
    private val junit4 =
        kotlin(
            """
            package org.junit
            annotation class Test
            """.trimIndent(),
        )
    private val junit5 =
        kotlin(
            """
            package org.junit.jupiter.api
            annotation class Test
            """.trimIndent(),
        )
    private val suppressLint =
        kotlin(
            """
            package android.annotation
            annotation class SuppressLint(vararg val value: String)
            """.trimIndent(),
        )

    private fun methodName(
        length: Int,
        className: String = "FooTest",
    ) = "a".repeat(length - className.length - 1)

    private fun testFile(
        body: String,
        className: String = "FooTest",
        path: String = "test/com/ichi2/test/$className.kt",
    ) = kotlin(
        path,
        """
        package com.ichi2.test
        import android.annotation.SuppressLint
        import org.junit.Test

        class $className {
        $body
        }
        """.trimIndent(),
    )

    private fun run(vararg files: TestFile): TestLintResult =
        lint()
            .allowMissingSdk()
            .files(junit4, junit5, suppressLint, *files)
            .issues(TestNameLengthDetector.ISSUE)
            .run()

    @Test
    fun `name at the limit is allowed`() {
        run(testFile("@Test fun ${methodName(MAX_TEST_NAME_LENGTH)}() {}"))
            .expectClean()
    }

    @Test
    fun `name over the limit is reported with its length`() {
        run(testFile("@Test fun ${methodName(MAX_TEST_NAME_LENGTH + 1)}() {}"))
            .expectErrorCount(1)
            .expectContains("Test name is ${MAX_TEST_NAME_LENGTH + 1} characters (max $MAX_TEST_NAME_LENGTH)")
    }

    @Test
    fun `backtick names count spaces`() {
        val atLimit = "a b".repeat(30).padEnd(MAX_TEST_NAME_LENGTH - "FooTest_".length, 'c')
        run(testFile("@Test fun `$atLimit`() {}"))
            .expectClean()
        run(testFile("@Test fun `$atLimit `() {}"))
            .expectErrorCount(1)
            .expectContains("Test name is ${MAX_TEST_NAME_LENGTH + 1} characters")
    }

    @Test
    fun `long method without Test annotation is allowed`() {
        run(testFile("fun ${methodName(MAX_TEST_NAME_LENGTH + 20)}() {}"))
            .expectClean()
    }

    @Test
    fun `long test in main source is allowed`() {
        val path = "src/com/ichi2/test/FooTest.kt"
        run(testFile("@Test fun ${methodName(MAX_TEST_NAME_LENGTH + 20)}() {}", path = path))
            .expectClean()
    }

    @Test
    fun `class name counts towards the length`() {
        val method = methodName(MAX_TEST_NAME_LENGTH)
        run(testFile("@Test fun $method() {}", className = "FooTest"))
            .expectClean()
        run(testFile("@Test fun $method() {}", className = "FooBarTest"))
            .expectErrorCount(1)
    }

    @Test
    fun `suppressed test is allowed`() {
        val method = methodName(MAX_TEST_NAME_LENGTH + 1)
        run(testFile("@SuppressLint(\"${TestNameLengthDetector.ID}\") @Test fun $method() {}"))
            .expectClean()
        run(testFile("@Suppress(\"${TestNameLengthDetector.ID}\") @Test fun $method() {}"))
            .expectClean()
    }

    @Test
    fun `JUnit5 test over the limit is reported`() {
        run(testFile("@org.junit.jupiter.api.Test fun ${methodName(MAX_TEST_NAME_LENGTH + 1)}() {}"))
            .expectErrorCount(1)
    }
}
