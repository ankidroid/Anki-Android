// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.gradle

import com.android.build.api.variant.Variant
import org.gradle.api.Project
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.TaskProvider
import org.gradle.testing.jacoco.tasks.JacocoReport

/** Connects the aggregate unit test report to the app and its runtime modules. */
internal class UnitTestCoverageReports(
    private val app: Project,
    private val aggregate: TaskProvider<JacocoReport>,
) {
    private val runtimeModules by lazy { app.runtimeProjectDependencies("playDebugRuntimeClasspath").toSet() + app }

    fun registerAndroidSuite(
        module: Project,
        variant: Variant,
        classExcludes: List<String>,
        hasTests: () -> Boolean = { true },
    ) {
        registerSuite(
            module = module,
            taskName = "test${variant.name.replaceFirstChar { it.uppercase() }}UnitTest",
            sources = module.files(variant.sources.java?.all, variant.sources.kotlin?.all),
            classes =
                module.androidCoverageClasses(
                    report = aggregate,
                    variant = variant,
                    classExcludes = classExcludes,
                ),
            hasTests = hasTests,
        )
    }

    fun registerSuite(
        module: Project,
        taskName: String,
        sources: FileCollection,
        classes: FileCollection,
        hasTests: () -> Boolean = { true },
    ) {
        aggregate.configure {
            if (module in runtimeModules) {
                classDirectories.from(classes)
                sourceDirectories.from(sources)
            }
        }
        module.includeTestExecutionData(
            report = aggregate,
            taskName = taskName,
            pattern = "**/*.exec",
            includeModule = { module in runtimeModules && hasTests() },
        )
    }
}
