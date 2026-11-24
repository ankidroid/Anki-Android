// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import org.gradle.work.DisableCachingByDefault
import java.io.ByteArrayOutputStream
import javax.inject.Inject

/** A Gradle project path and the directory it owns relative to the repository root. */
data class ModuleDirectory(
    @get:Input val projectPath: GradleProjectPath,
    @get:Input val relativeDirectory: String,
)

/**
 * A direct dependency identified by its Gradle project path. Examples:
 *
 * * `:common`
 * * `:common:android`
 */
data class UnitTestProjectDependency(
    @get:Input val projectPath: GradleProjectPath,
)

/** Selects affected unit test suites from Git changes and the project dependency graph. */
@DisableCachingByDefault(because = "Git HEAD can change without Gradle inputs changing")
abstract class SelectAffectedTestModules : DefaultTask() {
    /** Repository in which to compare revisions. */
    @get:Internal
    abstract val repositoryDirectory: DirectoryProperty

    /** Git revision to compare with the checked-out commit. */
    @get:Input
    abstract val comparisonBase: Property<String>

    /** Source commit used to build the checked-out PR merge commit, when running on a pull request. */
    @get:Input
    abstract val pullRequestHead: Property<String>

    /** Project paths and their directories relative to the repository root. */
    @get:Nested
    abstract val moduleDirectories: ListProperty<ModuleDirectory>

    /** Direct project dependencies, keyed by the path of the depending project. */
    @get:Nested
    abstract val moduleDependencies: MapProperty<GradleProjectPath, List<UnitTestProjectDependency>>

    /** Properties file containing the selected modules for CI. */
    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @get:Inject
    abstract val execOperations: ExecOperations

    init {
        // Recompute even when a configuration-cache entry is reused after a commit.
        outputs.upToDateWhen { false }
    }

    /** Writes the suite selection for CI. An unavailable comparison selects the full suite. */
    @TaskAction
    fun select() {
        val modules =
            changedPaths()?.let { paths ->
                affectedTestModules(paths, moduleDirectories.get(), moduleDependencies.get())
            } ?: UnitTestModules.All
        val selection = testSelectionOutput(modules)
        logger.lifecycle("Test selection:\n$selection")
        outputFile.get().asFile.apply {
            parentFile.mkdirs()
            writeText(selection)
        }
    }

    /**
     * Compares [comparisonBase] with the checked-out commit, including both paths of a rename.
     * On PRs, also includes test changes that can affect coverage carried from earlier revisions.
     * Returns `null` if the comparison is unavailable. An empty list means Git found no changes.
     */
    @Suppress("ktlint:standard:comment-spacing")
    private fun changedPaths(): List<String>? {
        //DEFECT: Reverting a target-only test dependency can leave stale hits if no test files changed.
        val base = comparisonBase.get()
        if (base.isEmpty()) return null
        val head = pullRequestHead.get()
        val sha = Regex("[0-9a-fA-F]{40}")
        if (!base.matches(sha) || (head.isNotEmpty() && !head.matches(sha))) {
            logger.warn("Invalid CI revision; running all tests")
            return null
        }
        val changes = git("diff", "--name-only", "--no-renames", "-z", base, "HEAD", "--")?.split('\u0000') ?: return null
        val history = if (head.isEmpty()) emptyList() else historicalTestChanges(base, head) ?: return null
        return (changes + history).filter { it.isNotEmpty() }.distinct()
    }

    /**
     * Finds tests edited or deleted in the PR and in target branch updates made during the PR.
     * For example, a test added and then removed in the PR is absent from the current diff, but
     * Codecov may still count its coverage from an earlier run. Rerunning its suite refreshes that coverage.
     *
     * [base] is the current target commit and [head] is the PR head. Returns `null` if Git cannot
     * read the history, so the caller can select the full suite.
     */
    private fun historicalTestChanges(
        base: String,
        head: String,
    ): List<String>? {
        // The first parent of the oldest PR commit precedes target merges into the PR. Using
        // the current merge base could hide tests removed by the target before one of those merges.
        val commits = git("rev-list", "--first-parent", "$base..$head") ?: return null
        val oldest = commits.trimEnd().lineSequence().last()
        if (oldest.isEmpty()) return emptyList()

        // Look at edits and deletions on both branches, including tests that were added and reverted
        // between revisions. The current tree diff already picks up new tests in the PR.
        return git(
            "log",
            "--format=",
            "--name-only",
            "--no-renames",
            "-z",
            "--full-history",
            "--diff-merges=first-parent",
            "--diff-filter=a",
            "$oldest^..HEAD",
            "--",
            ":(glob)**/src/test*/**",
        )?.split('\u0000')
    }

    private fun git(vararg arguments: String): String? {
        val output = ByteArrayOutputStream()
        val errors = ByteArrayOutputStream()
        val result =
            execOperations.exec {
                workingDir(repositoryDirectory.get().asFile)
                commandLine("git", *arguments)
                standardOutput = output
                errorOutput = errors
                isIgnoreExitValue = true
            }
        if (result.exitValue != 0) {
            logger.warn("Could not read Git changes; running all tests: $errors")
            return null
        }
        return output.toString(Charsets.UTF_8)
    }
}

/** Formats the CI output. A `*` value selects all suites and an empty value selects none. */
internal fun testSelectionOutput(modules: UnitTestModules): String {
    val value =
        when (modules) {
            UnitTestModules.All -> "*"
            // Lint rules and baseline profiles have dedicated lint and emulator jobs.
            is UnitTestModules.Selected -> (modules.paths - setOf(":lint-rules", ":baselineprofile")).sorted().joinToString(",")
        }
    return "modules=$value\n"
}
