// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.gradle

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.io.File
import java.util.Properties

class TestModuleSelectionPluginTest {
    @TempDir
    lateinit var directory: File

    private lateinit var base: String

    @BeforeEach
    fun setUp() {
        writeFile(".gitignore", "build/\n.gradle/\n")
        writeFile(
            "settings.gradle",
            """
            rootProject.name = 'test-module-selection'
            include ':AnkiDroid', ':common', ':common:android', ':feature:browse', ':widgets', ':lint-rules', ':baselineprofile'
            project(':widgets').projectDir = file('components/widgets')
            """.trimIndent(),
        )
        writeFile(
            "build.gradle",
            """
            plugins { id 'ankidroid.plugins.test-selection' }
            subprojects { apply plugin: 'java-library' }
            // A convention can add dependencies outside the module's build script.
            configure([project(':widgets')]) {
                configurations { conventionDependency }
                dependencies { conventionDependency project(':lint-rules') }
            }
            """.trimIndent(),
        )
        for (module in listOf(
            "AnkiDroid",
            "common",
            "common/android",
            "feature/browse",
            "components/widgets",
            "lint-rules",
            "baselineprofile",
        )) {
            writeFile("$module/build.gradle", "")
        }
        writeFile(
            "AnkiDroid/build.gradle",
            "dependencies { implementation project(':feature:browse'); implementation project(':widgets') }",
        )
        writeFile("common/build.gradle", "plugins { id 'java-test-fixtures' }")
        writeFile("common/android/build.gradle", "dependencies { implementation project(':common') }")
        // A computed reference cannot be read by a build-script parser.
        writeFile("feature/browse/build.gradle", "def target = ':common' + ':android'\ndependencies { implementation project(target) }")
        writeFile("components/widgets/build.gradle", "dependencies { testImplementation testFixtures(project(':common')) }")
        writeFile("common/src/main/Old.java", "class Old {}")
        writeFile("common/src/main/Deleted.java", "class Deleted {}")
        git("init", "--quiet")
        git("config", "user.name", "CI test")
        git("config", "user.email", "ci@example.invalid")
        commit()
        base = git("rev-parse", "HEAD")
    }

    @Test
    fun `uses configured dependencies test fixtures and remapped directories`() {
        writeFile("common/src/testFixtures/Fixture.java", "class Fixture {}")
        commit()
        runSelection()
        assertEquals(":AnkiDroid,:common,:common:android,:feature:browse,:widgets", selection()["modules"])
    }

    @Test
    fun `early task realization still sees dependencies from all projects`() {
        File(directory, "build.gradle").appendText("\ntasks.named('selectAffectedTestModules').get()\n")
        commit()
        base = git("rev-parse", "HEAD")
        writeFile("common/src/main/Example.java", "class Example {}")
        commit()
        runSelection()
        assertEquals(":AnkiDroid,:common,:common:android,:feature:browse,:widgets", selection()["modules"])
    }

    @Test
    fun `uses dependencies supplied by conventions`() {
        writeFile("lint-rules/src/main/Rule.java", "class Rule {}")
        commit()
        runSelection()
        assertEquals(":AnkiDroid,:widgets", selection()["modules"])
    }

    @ParameterizedTest
    @ValueSource(strings = ["lint-rules", "baselineprofile"])
    fun `lint and baseline profile dependencies do not select app unit tests`(module: String) {
        writeFile("build.gradle", "plugins { id 'ankidroid.plugins.test-selection' }\nsubprojects { apply plugin: 'java-library' }")
        File(directory, "AnkiDroid/build.gradle").appendText(
            "\nconfigurations { lintChecks; baselineProfile; fullReleaseBaselineProfile.extendsFrom(baselineProfile) }\n" +
                "dependencies { lintChecks project(':lint-rules'); baselineProfile project(':baselineprofile') }\n",
        )
        commit()
        base = git("rev-parse", "HEAD")
        writeFile("$module/src/test/Example.java", "class Example {}")
        commit()
        runSelection()
        assertEquals("", selection()["modules"])
    }

    @Test
    fun `recomputes after commits while reusing configuration cache`() {
        writeFile("AnkiDroid/src/main/Example.java", "class Example {}")
        commit()
        runSelection()
        assertEquals(":AnkiDroid", selection()["modules"])

        val cached = runSelection()
        assertTrue(cached.output.contains("Reusing configuration cache."), cached.output)
        writeFile("components/widgets/src/test/Example.java", "class Example {}")
        commit()
        val changed = runSelection()
        assertTrue(changed.output.contains("Reusing configuration cache."), changed.output)
        assertEquals(TaskOutcome.SUCCESS, changed.task(":selectAffectedTestModules")?.outcome)
        assertEquals(":AnkiDroid,:widgets", selection()["modules"])
    }

    @Test
    fun `includes deletions and both sides of renames`() {
        File(directory, "components/widgets/src/main").mkdirs()
        git("mv", "common/src/main/Old.java", "components/widgets/src/main/New.java")
        git("rm", "common/src/main/Deleted.java")
        commit()
        runSelection()
        assertEquals(":AnkiDroid,:common,:common:android,:feature:browse,:widgets", selection()["modules"])
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "invalid", "0000000000000000000000000000000000000000"])
    fun `missing or invalid comparison bases select the full suite`(comparisonBase: String) {
        runSelection(comparisonBase)
        assertEquals("*", selection()["modules"])
    }

    @Test
    fun `documentation only comparisons skip unit tests`() {
        writeFile("docs/example.md", "Documentation")
        commit()
        runSelection()
        assertEquals("", selection()["modules"])
    }

    @Test
    fun `changes in implicit parent projects fall back to the full suite`() {
        writeFile("feature/README.md", "Documentation")
        commit()
        runSelection()
        assertEquals("*", selection()["modules"])
    }

    private fun runSelection(comparisonBase: String = base): BuildResult =
        GradleRunner
            .create()
            .withProjectDir(directory)
            .withTestKitDir(File("build/test-kit").absoluteFile)
            .withPluginClasspath()
            .withArguments("selectAffectedTestModules", "-PciTestBase=$comparisonBase", "--configuration-cache", "--stacktrace")
            .build()

    private fun selection(): Properties =
        Properties().apply {
            File(directory, "build/ci/affected-test-modules.properties").inputStream().use { load(it) }
        }

    private fun writeFile(
        path: String,
        text: String,
    ) {
        File(directory, path).apply {
            parentFile.mkdirs()
            writeText(text)
        }
    }

    private fun commit() {
        git("add", ".")
        git("commit", "--quiet", "-m", "fixture change")
    }

    private fun git(vararg arguments: String): String {
        val process =
            ProcessBuilder("git", "-c", "core.hooksPath=", "-c", "commit.gpgsign=false", *arguments)
                .directory(directory)
                .redirectErrorStream(true)
                .start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        assertEquals(0, process.waitFor(), output)
        return output.trim()
    }
}
