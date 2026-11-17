// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.gradle

/** A Gradle project path, such as `:common:android`. */
typealias GradleProjectPath = String

/**
 * Selects suites for changed repository paths. Private unit tests affect their own suite.
 * Changes to production code and shared fixtures also select dependent modules.
 */
@Suppress("ktlint:standard:comment-spacing")
internal fun affectedTestModules(
    paths: List<String>,
    moduleDirectories: List<ModuleDirectory>,
    dependencies: Map<GradleProjectPath, List<UnitTestProjectDependency>>,
): UnitTestModules {
    val modules = moduleDirectories.sortedByDescending { it.relativeDirectory.length }
    val changedTestModules = mutableSetOf<GradleProjectPath>()
    val changedSharedModules = mutableSetOf<GradleProjectPath>()
    for (path in paths) {
        // A build script change can remove an edge that the current graph no longer shows.
        if (path.endsWith(".gradle") || path.endsWith(".gradle.kts")) return UnitTestModules.All

        //DEFECT: Shared unit-test sources can skip suites outside the module that owns the directory.
        //DEFECT: Shared production roots can skip other consuming modules and their dependent suites.
        val module = modules.firstOrNull { path.startsWith("${it.relativeDirectory}/") }
        if (module == null) {
            if (path.startsWith("docs/") || ('/' !in path && path.endsWith(".md"))) continue
            //DEFECT: Build logic test edits trigger full unit runs even though CI tests buildSrc separately.
            // Run all suites for paths with no owning module, such as feature/README.md in implicit :feature.
            return UnitTestModules.All
        }

        //DEFECT: Private debug unit tests select dependent suites because they live under src/testDebug/.
        //DEFECT: Private tests in custom source roots also select dependent suites.
        if (path.startsWith("${module.relativeDirectory}/src/test/")) {
            changedTestModules.add(module.projectPath)
        } else {
            changedSharedModules.add(module.projectPath)
        }
    }
    return UnitTestModules.Selected(
        paths = changedTestModules + modulesAndDependents(changedSharedModules, dependencies),
    )
}

/** Returns [changedModules] and every module that depends on them directly or transitively. */
private fun modulesAndDependents(
    changedModules: Set<GradleProjectPath>,
    dependencies: Map<GradleProjectPath, List<UnitTestProjectDependency>>,
): Set<GradleProjectPath> {
    val affected = changedModules.toMutableSet()
    do {
        val dependents = dependencies.filterValues { requiredModules -> requiredModules.any { it.projectPath in affected } }.keys
    } while (affected.addAll(dependents))
    return affected
}
