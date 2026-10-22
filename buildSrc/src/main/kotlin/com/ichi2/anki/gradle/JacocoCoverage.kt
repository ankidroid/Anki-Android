// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.gradle

import com.android.build.api.artifact.ScopedArtifact
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.api.variant.HostTestBuilder
import com.android.build.api.variant.LibraryAndroidComponentsExtension
import com.android.build.api.variant.ScopedArtifacts
import com.android.build.api.variant.Variant
import com.ichi2.anki.gradle.HasUnitTestSources.Companion.hasAndroidUnitTestSources
import org.gradle.api.DomainObjectSet
import org.gradle.api.Project
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.extra
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register
import org.gradle.testing.jacoco.tasks.JacocoReport

private class JacocoLibraryVariants(
    val variants: DomainObjectSet<Variant>,
)

/** Captures library variants before any library can finish configuring. */
fun Project.registerJacocoLibraryVariants() {
    allprojects {
        val variants = objects.domainObjectSet(Variant::class.java)
        extensions.add("jacocoLibraryVariants", JacocoLibraryVariants(variants))
        pluginManager.withPlugin("com.android.library") {
            extensions.configure<LibraryAndroidComponentsExtension> {
                onVariants(selector().withBuildType("debug")) { variant ->
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
    // Our merge report task
    val mergedReport =
        tasks.register<JacocoReport>("jacocoTestReport") {
            configureJacocoReportOutput(layout, openReportRequested, linuxHtmlCmd)
        }

    // A unit-test only report task
    val unitTestReport =
        tasks.register<JacocoReport>("jacocoUnitTestReport") {
            configureJacocoReportOutput(layout, openReportRequested, linuxHtmlCmd)
        }

    // A connected android tests only report task
    val androidTestReport =
        tasks.register<JacocoReport>("jacocoAndroidTestReport") {
            configureJacocoReportOutput(layout, openReportRequested, linuxHtmlCmd)
        }

    configureAndroidAppCoverage(
        unitTestReport,
        androidTestReport,
        mergedReport,
        rootProject.androidTestVariantName,
        fileFilter,
    )

    // Libraries may already be configured. Subscribe to the captured variants,
    // then select runtime dependencies when the report's graph is available.
    for (module in rootProject.allprojects.filter { it != project }) {
        val includeModule = { module in runtimeProjectDependencies("playDebugRuntimeClasspath") }
        module.configureAndroidLibraryCoverage(unitTestReport, fileFilter, includeModule)
        module.configureJvmCoverage(unitTestReport, fileFilter, includeModule)
    }
}

/**
 * Connects app reports to debug unit tests and the selected emulator-test variant.
 * The merged report uses the emulator variant's classes and both tests' execution data.
 */
private fun Project.configureAndroidAppCoverage(
    unitTestReport: TaskProvider<JacocoReport>,
    androidTestReport: TaskProvider<JacocoReport>,
    mergedReport: TaskProvider<JacocoReport>,
    androidTestVariantName: String,
    classExcludes: List<String>,
) {
    extensions.configure<ApplicationAndroidComponentsExtension> {
        // AGP enables unit tests only for testBuildType by default. These reports
        // always run debug unit tests, including when emulator tests target release.
        beforeVariants(selector().withName("playDebug")) { variant ->
            variant.hostTests[HostTestBuilder.UNIT_TEST_TYPE]?.enable = true
        }
        onVariants(selector().withName("playDebug")) { debugVariant ->
            includeAndroidClasses(unitTestReport, debugVariant, classExcludes)
            includeTestExecutionData(unitTestReport, "testPlayDebugUnitTest", "**/*.exec")
            includeTestExecutionData(mergedReport, "testPlayDebugUnitTest", "**/*.exec")
        }

        onVariants(selector().withName("play$androidTestVariantName")) { androidTestVariant ->
            val connectedTest = "connectedPlay${androidTestVariantName}AndroidTest"
            includeAndroidClasses(androidTestReport, androidTestVariant, classExcludes)
            includeTestExecutionData(androidTestReport, connectedTest, "**/*.ec")
            includeAndroidClasses(mergedReport, androidTestVariant, classExcludes)
            includeTestExecutionData(mergedReport, connectedTest, "**/*.ec")
        }
    }
}

/** Adds an Android library's debug classes and unit-test coverage to a report. */
private fun Project.configureAndroidLibraryCoverage(
    report: TaskProvider<JacocoReport>,
    classExcludes: List<String>,
    includeModule: () -> Boolean,
) {
    val coverageVariants = extensions.getByType(JacocoLibraryVariants::class.java)
    coverageVariants.variants.all {
        includeAndroidClasses(report, this, classExcludes, includeModule)
        // Classes from libraries without tests still count as uncovered.
        // Their empty test tasks may require resources supplied by the app.
        includeTestExecutionData(report, "testDebugUnitTest", "**/*.exec") {
            includeModule() && hasAndroidUnitTestSources()
        }
    }
}

/** Adds a JVM module's main classes and unit-test coverage to a report. */
private fun Project.configureJvmCoverage(
    report: TaskProvider<JacocoReport>,
    classExcludes: List<String>,
    includeModule: () -> Boolean,
) {
    pluginManager.withPlugin("java") {
        val mainSourceSet = extensions.getByType(SourceSetContainer::class.java).named("main")
        val sources = mainSourceSet.map { it.allSource.srcDirs }
        val classes =
            mainSourceSet.map { sourceSet ->
                sourceSet.output.classesDirs.asFileTree
                    .matching { exclude(classExcludes) }
            }
        report.configure {
            if (!includeModule()) return@configure
            sourceDirectories.from(sources)
            classDirectories.from(classes)
        }
        includeTestExecutionData(report, "test", "**/*.exec", includeModule)
    }
}

private fun Project.includeAndroidClasses(
    report: TaskProvider<JacocoReport>,
    variant: Variant,
    classExcludes: List<String>,
    includeModule: () -> Boolean = { true },
) {
    val classInputs = objects.newInstance(AndroidCoverageInputs::class.java)
    val projectArtifacts = variant.artifacts.forScope(ScopedArtifacts.Scope.PROJECT)
    projectArtifacts.use(report).toGet(
        ScopedArtifact.CLASSES,
        { classInputs.jars },
        { classInputs.directories },
    )
    report.configure {
        if (!includeModule()) return@configure
        classDirectories.from(classInputs.classes.matching { exclude(classExcludes) })
        sourceDirectories.from(variant.sources.java?.all, variant.sources.kotlin?.all)
    }
}

private fun Project.includeTestExecutionData(
    report: TaskProvider<JacocoReport>,
    taskName: String,
    pattern: String,
    includeModule: () -> Boolean = { true },
) {
    val coverageProject = this
    // Restrict the input to this producer's declared outputs. In particular, do
    // not pick up stale coverage from other flavors or build types.
    // AGP may register the producer later, so use a live task collection.
    val testTasks = tasks.named { it == taskName }
    val testTaskPath = "$path:$taskName"
    val coverageFiles = files(testTasks).asFileTree.matching { include(pattern) }
    report.configure {
        if (!includeModule()) return@configure
        executionData.from(coverageFiles)
        dependsOn(testTaskPath)
        if (pattern == "**/*.exec") {
            runUnitTestsWithoutCaching(this, coverageProject, taskName)
        }
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

private val Project.androidTestVariantName: String get() = extra["androidTestVariantName"] as String
