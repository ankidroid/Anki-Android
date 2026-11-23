// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.gradle

import com.android.build.api.dsl.UnitTestOptions
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test

/** Configures Android unit-test resources, Robolectric SDK prefetch, and JVM options. */
fun Project.configureRobolectricUnitTests(unitTests: UnitTestOptions) {
    unitTests.isIncludeAndroidResources = true

    val robolectricDependencies = project(":AnkiDroid").layout.buildDirectory.file("robolectric-deps.properties")
    unitTests.all { test ->
        // Resolve SDKs before forking tests: Robolectric's shared download lock races on Windows (Issue 22069).
        test.dependsOn(":AnkiDroid:robolectricSdkDownload")
        test.inputs.file(robolectricDependencies)
        test.systemProperty("robolectric-deps.properties", robolectricDependencies.get().asFile.absolutePath)
        test.minHeapSize = "1g"
        test.configureRobolectricJvm()
    }
}

/**
 * Opens the JDK internals required by Robolectric on Java 17 and higher.
 *
 * See https://robolectric.org/getting-started/#running-with-java-17-and-higher
 */
private fun Test.configureRobolectricJvm() {
    jvmArgs(
        "--add-opens=java.base/java.lang=ALL-UNNAMED",
        "--add-opens=java.base/java.util=ALL-UNNAMED",
        "--add-opens=java.base/java.io=ALL-UNNAMED",
        "--add-opens=java.base/java.net=ALL-UNNAMED",
        "--add-opens=java.base/java.security=ALL-UNNAMED",
        "--add-opens=java.base/java.text=ALL-UNNAMED",
        "--add-opens=java.base/jdk.internal.access=ALL-UNNAMED",
        "--add-opens=java.desktop/java.awt.font=ALL-UNNAMED",
        "--add-opens=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED",
    )
}
