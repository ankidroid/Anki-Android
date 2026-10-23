// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.gradle

import com.android.build.api.variant.LibraryAndroidComponentsExtension
import com.android.build.api.variant.Variant
import com.ichi2.anki.gradle.HasUnitTestSources.Companion.hasAndroidUnitTestSources
import org.gradle.api.DomainObjectSet
import org.gradle.api.Project
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.extra
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register
import org.gradle.testing.jacoco.tasks.JacocoReport

fun builtInKotlincClassDir(variant: String): String {
    // AGP9 built-in Kotlin compiler writes classes to
    // build/intermediates/built_in_kotlinc/<variant>/compile<Variant>Kotlin/classes
    return "intermediates/built_in_kotlinc/$variant/compile${variant.replaceFirstChar { it.uppercase() }}Kotlin/classes"
}

// Narrow coverage roots: a fileTree over the whole build/ dir makes Gradle treat every
// sibling task output under build/ as an undeclared input (e.g. lintFullDebug + this report).
val unitTestCoverageDirs = listOf("outputs/unit_test_code_coverage", "jacoco")
val androidTestCoverageDirs = listOf("outputs/code_coverage")

fun Project.coverageFileTree(
    relativeDirs: List<String>,
    includes: List<String>,
) = files(
    relativeDirs.map { relativeDir ->
        fileTree(
            mapOf(
                "dir" to layout.buildDirectory.dir(relativeDir),
                "includes" to includes,
            ),
        )
    },
)

private class JacocoLibraryVariants(
    val variants: DomainObjectSet<Variant>,
)

/** Captures library variants before any library can finish configuring. */
fun Project.registerJacocoLibraryVariants() {
    allprojects {
        val variants = objects.domainObjectSet(Variant::class.java)
        extensions.add("jacocoLibraryVariants", JacocoLibraryVariants(variants))
        pluginManager.withPlugin("com.android.library") {
            val androidTestBuildType = (rootProject.extensions.extraProperties["androidTestVariantName"] as String).lowercase()
            extensions.configure<LibraryAndroidComponentsExtension> {
                onVariants(selector().withBuildType(androidTestBuildType)) { variant ->
                    variants.add(variant)
                }
            }
        }
    }
}

/** Registers the merged, unit-test, and Android-test coverage reports. */
fun Project.registerJacocoCoverageReports(
    fileFilter: List<String>,
    openReportRequested: Boolean,
    linuxHtmlCmd: String?,
) {
    val flavorClassDir = builtInKotlincClassDir("play${rootProject.androidTestVariantName}")
    val moduleClassDir = builtInKotlincClassDir(rootProject.androidTestVariantName.lowercase())

    fun includeUnitTestCoverage(
        report: JacocoReport,
        project: Project,
        classDir: String,
    ) {
        report.sourceDirectories.from(project.files("src/main/java", "src/main/kotlin"))
        report.classDirectories.from(
            project.files(
                project.fileTree(
                    mapOf(
                        "dir" to project.layout.buildDirectory.dir(classDir),
                        "excludes" to fileFilter,
                    ),
                ),
            ),
        )
        report.executionData.from(project.coverageFileTree(unitTestCoverageDirs, listOf("**/*.exec")))
    }

    fun configureModuleUnitTestCoverage(
        report: JacocoReport,
        module: Project,
    ) {
        when {
            module.plugins.hasPlugin("com.android.library") -> {
                includeUnitTestCoverage(report, module, moduleClassDir)
                // Include classes exercised by app tests even when the library has no tests.
                // Avoid empty test tasks: their resource linking may require app resources.
                if (module.hasAndroidUnitTestSources()) {
                    runUnitTestsWithoutCaching(report, module, "testDebugUnitTest")
                }
            }

            module.plugins.hasPlugin("org.jetbrains.kotlin.jvm") -> {
                includeUnitTestCoverage(report, module, "classes/kotlin/main")
                runUnitTestsWithoutCaching(report, module, "test")
            }
        }
    }

    // Our merge report task
    tasks.register<JacocoReport>("jacocoTestReport") {
        configureJacocoReportOutput(layout, openReportRequested, linuxHtmlCmd)

        val kotlinClasses =
            project.fileTree(
                mapOf(
                    "dir" to project.layout.buildDirectory.dir(flavorClassDir),
                    "excludes" to fileFilter,
                ),
            )
        val mainSrc = "${project.projectDir}/src/main/java"

        sourceDirectories.setFrom(project.files(mainSrc))
        classDirectories.setFrom(project.files(kotlinClasses))
        executionData.setFrom(
            project.coverageFileTree(unitTestCoverageDirs, listOf("**/*.exec")),
            project.coverageFileTree(androidTestCoverageDirs, listOf("**/*.ec")),
        )
        dependsOn("testPlayDebugUnitTest")
        dependsOn("connectedPlay${rootProject.androidTestVariantName}AndroidTest")
    }

    // A unit-test only report task
    tasks.register<JacocoReport>("jacocoUnitTestReport") {
        val report = this
        configureJacocoReportOutput(layout, openReportRequested, linuxHtmlCmd)

        includeUnitTestCoverage(report, getProject(), flavorClassDir)
        runUnitTestsWithoutCaching(report, getProject(), "testPlayDebugUnitTest")

        for (module in project.runtimeProjectDependencies("playDebugRuntimeClasspath")) {
            configureModuleUnitTestCoverage(report, module)
        }
    }

    // A connected android tests only report task
    tasks.register<JacocoReport>("jacocoAndroidTestReport") {
        configureJacocoReportOutput(layout, openReportRequested, linuxHtmlCmd)

        val kotlinClasses =
            project.fileTree(
                mapOf(
                    "dir" to project.layout.buildDirectory.dir(flavorClassDir),
                    "excludes" to fileFilter,
                ),
            )
        val mainSrc = "${project.projectDir}/src/main/java"

        sourceDirectories.setFrom(project.files(mainSrc))
        classDirectories.setFrom(project.files(kotlinClasses))
        executionData.setFrom(project.coverageFileTree(androidTestCoverageDirs, listOf("**/*.ec")))
        dependsOn("connectedPlay${rootProject.androidTestVariantName}AndroidTest")
    }
}

// Follow the app's runtime graph, including transitive project dependencies.
// This excludes build tooling such as lint rules and baseline-profile generators.
fun Project.runtimeProjectDependencies(configurationName: String): List<Project> =
    configurations[configurationName]
        .incoming.resolutionResult.allComponents
        .mapNotNull { it.id as? ProjectComponentIdentifier }
        .filter { it.build.buildPath == ":" && it.projectPath != path }
        .map { project(it.projectPath) }

fun runUnitTestsWithoutCaching(
    report: JacocoReport,
    project: Project,
    taskName: String,
) {
    val test = project.tasks.named<Test>(taskName).get()
    report.dependsOn(test)
    // Preserve the existing coverage policy: always rerun these tests, bypassing
    // up-to-date checks and the build cache. Configuration caching is unaffected.
    // TODO: Revisit whether coverage reports need fresh test executions.
    test.outputs.upToDateWhen { false }
    test.outputs.cacheIf { false }
}

val Project.androidTestVariantName: String get() = extra["androidTestVariantName"] as String
