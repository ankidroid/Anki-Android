// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.gradle

import org.gradle.testkit.runner.GradleRunner
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class JacocoPluginTest {
    @TempDir
    lateinit var projectDir: File

    @BeforeEach
    fun setUp() {
        val fixture = File(requireNotNull(javaClass.getResource("/jacoco-project")).toURI())
        fixture.walkTopDown().filter { it.isFile && it.extension == "fixture" }.forEach { source ->
            writeFile(source.relativeTo(fixture).path.removeSuffix(".fixture"), source.readText())
        }
        for (module in listOf("app", "library", "nested/core", "empty", "tooling")) {
            File(projectDir, module).mkdirs()
        }
        File("../gradle/libs.versions.toml").copyTo(File(projectDir, "libs.versions.toml"))
        File("../local.properties").takeIf { it.isFile }?.copyTo(File(projectDir, "local.properties"))
        writeFile("gradle.properties", "org.gradle.workers.max=2\n")
        writeFile("library/src/test/java/ExampleTest.java", "class ExampleTest {}")
        writeFile("nested/core/src/test/kotlin/ExampleTest.kt", "class ExampleTest")
        // Resources alone must not cause a unit-test task to be scheduled.
        writeFile("empty/src/test/resources/example.txt", "Not a test source")
    }

    @Test
    fun `report covers runtime modules and skips empty library test tasks`() {
        val output = dryRunCoverage()

        for (module in listOf("app", "library", "nested/core", "empty")) {
            assertThat(output, containsString("$module/src/main/kotlin"))
        }
        assertThat(output, not(containsString("tooling/src/main")))

        assertThat(output, containsString(":app:testPlayDebugUnitTest SKIPPED"))
        assertThat(output, containsString(":library:testDebugUnitTest SKIPPED"))
        assertThat(output, containsString(":nested:core:test SKIPPED"))
        assertThat(output, not(containsString(":empty:testDebugUnitTest")))
        assertThat(output, not(containsString(":tooling:test")))
    }

    @Test
    fun `adding the first test in a custom source root updates the cached task graph`() {
        val before = dryRunCoverage()
        assertThat(before, not(containsString(":empty:testDebugUnitTest")))

        val cached = dryRunCoverage()
        assertThat(cached, containsString("Reusing configuration cache."))

        writeFile("empty/custom-tests/ExampleTest.kt", "class ExampleTest")
        val after = dryRunCoverage()
        assertThat(after, containsString(":empty:testDebugUnitTest SKIPPED"))
    }

    @Test
    fun `removing the last test updates the cached task graph`() {
        writeFile("empty/custom-tests/ExampleTest.kt", "class ExampleTest")
        val before = dryRunCoverage()
        assertThat(before, containsString(":empty:testDebugUnitTest SKIPPED"))

        assertThat(File(projectDir, "empty/custom-tests/ExampleTest.kt").delete(), equalTo(true))
        val after = dryRunCoverage()
        assertThat(after, not(containsString(":empty:testDebugUnitTest")))
    }

    private fun dryRunCoverage(): String =
        GradleRunner
            .create()
            .withProjectDir(projectDir)
            .withTestKitDir(File("build/test-kit").absoluteFile)
            .withPluginClasspath()
            .withArguments(":app:jacocoUnitTestReport", "--dry-run", "--configuration-cache", "--stacktrace")
            .build()
            .output

    private fun writeFile(
        path: String,
        contents: String,
    ) {
        File(projectDir, path).apply {
            parentFile.mkdirs()
            writeText(contents)
        }
    }
}
