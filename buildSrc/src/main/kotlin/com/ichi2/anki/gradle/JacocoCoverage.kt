// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.gradle

import org.gradle.api.Project
import org.gradle.kotlin.dsl.extra
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

/** Registers the merged, unit-test, and Android-test coverage reports. */
fun Project.registerJacocoCoverageReports(
    fileFilter: List<String>,
    androidModulesToUnitTest: List<String>,
    jvmModulesToUnitTest: List<String>,
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
        report.sourceDirectories.from(project.files("src/main/java"))
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
        dependsOn("testPlayDebugUnitTest")

        for (module in androidModulesToUnitTest) {
            includeUnitTestCoverage(report, project(module), moduleClassDir)
            dependsOn("$module:testDebugUnitTest")
        }

        for (module in jvmModulesToUnitTest) {
            includeUnitTestCoverage(report, project(module), "classes/kotlin/main")
            dependsOn("$module:test")
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

val Project.androidTestVariantName: String get() = extra["androidTestVariantName"] as String
