// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.gradle

import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.kotlin.dsl.register

/**
 * Registers `selectAffectedTestModules`, which CI uses to run unit tests only for modules affected
 * by changes since `-PciTestBase`. The task needs each module's directory to identify changed modules
 * and its project dependencies to find dependent suites.
 *
 * Capture those directories and dependencies after all projects have been evaluated, so the task
 * sees dependencies declared by build scripts and convention plugins, including test fixtures.
 * The task then uses the captured values without accessing Gradle's project model during execution.
 */
fun Project.registerTestModuleSelection() {
    val selectionRoot = rootProject
    val selectionTask =
        tasks.register<SelectAffectedTestModules>("selectAffectedTestModules") {
            group = "verification"
            description = "Selects affected unit-test modules relative to -PciTestBase=<commit>."
            repositoryDirectory.set(selectionRoot.layout.projectDirectory)
            comparisonBase.convention(providers.gradleProperty("ciTestBase").orElse(""))
            outputFile.convention(selectionRoot.layout.buildDirectory.file("ci/affected-test-modules.properties"))
        }

    gradle.projectsEvaluated {
        selectionTask.configure {
            moduleDirectories.set(selectionRoot.moduleDirectories())
            moduleDependencies.set(selectionRoot.subprojects.associate { it.path to it.unitTestProjectDependencies() })
        }
    }
}

/** Finds the directories of projects with build scripts, relative to the repository root. */
@Suppress("ktlint:standard:comment-spacing")
private fun Project.moduleDirectories(): List<ModuleDirectory> =
    subprojects
        //DEFECT: A nested project configured by the root can miss its own tests if it has no build script.
        .filter { it.buildFile.isFile }
        .map { module ->
            ModuleDirectory(
                projectPath = module.path,
                relativeDirectory = module.projectDir.relativeTo(projectDir).invariantSeparatorsPath,
            )
        }

/** Collects dependency project paths from production, test and fixture configurations. */
private fun Project.unitTestProjectDependencies(): List<UnitTestProjectDependency> =
    configurations
        // Lint rules and baseline profile generators have their own CI jobs.
        .filterNot { it.name == "lintChecks" || it.name.endsWith("baselineProfile", ignoreCase = true) }
        .flatMap { configuration ->
            configuration.allDependencies.withType(ProjectDependency::class.java).map { UnitTestProjectDependency(it.path) }
        }.distinct()
