// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.gradle

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.greaterThan
import org.hamcrest.Matchers.not
import org.jacoco.core.tools.ExecFileLoader
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.xpath.XPathFactory

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
    fun `library without unit tests is included as uncovered in the report`() {
        // A valid, empty execution file represents a run that never touched this class.
        ExecFileLoader().save(File(projectDir, "app/build/jacoco/testPlayDebugUnitTest.exec"), false)

        // Run the real report with Android compilation, skipping only test execution.
        val result =
            runCoverage(
                "-x",
                ":app:testPlayDebugUnitTest",
                "-x",
                ":library:testDebugUnitTest",
                "-x",
                ":nested:core:test",
            )
        assertThat(result.task(":app:jacocoUnitTestReport")?.outcome, equalTo(TaskOutcome.SUCCESS))

        val report =
            DocumentBuilderFactory
                .newInstance()
                .apply { setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) }
                .newDocumentBuilder()
                .parse(File(projectDir, "app/build/reports/jacoco/jacocoUnitTestReport/jacocoUnitTestReport.xml"))
        val xpath = XPathFactory.newInstance().newXPath()
        val counter = "/report/package/class[@name='regression/Uncovered']/counter[@type='LINE']"
        assertThat(xpath.evaluate("$counter/@covered", report), equalTo("0"))
        assertThat(xpath.evaluate("$counter/@missed", report).toInt(), greaterThan(0))
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

    private fun dryRunCoverage(): String = runCoverage("--dry-run").output

    private fun runCoverage(vararg arguments: String): BuildResult =
        GradleRunner
            .create()
            .withProjectDir(projectDir)
            .withTestKitDir(File("build/test-kit").absoluteFile)
            .withPluginClasspath()
            .withArguments(":app:jacocoUnitTestReport", "--configuration-cache", "--stacktrace", *arguments)
            .build()

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
