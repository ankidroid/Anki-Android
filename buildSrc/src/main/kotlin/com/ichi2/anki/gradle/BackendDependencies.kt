// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.gradle

import org.gradle.api.Project
import org.gradle.kotlin.dsl.DependencyHandlerScope
import java.io.File
import java.util.Properties

// The Anki backend (rsdroid) is consumed either as locally-built artifacts in
// ../Anki-Android-Backend (when `local_backend=true` in local.properties) or as
// the published library from the version catalog.

// Paths within the `Anki-Android-Backend` checkout, which sits beside the repo root.
private const val RSDROID_AAR =
    "rsdroid/build/outputs/aar/rsdroid-release.aar"
private const val RSDROID_ANDROID_AAR =
    "rsdroid-android/build/outputs/aar/rsdroid-android-release.aar"
private const val RSDROID_TESTING_JAR =
    "rsdroid-testing/build/libs/rsdroid-testing.jar"

/**
 * Adds the Anki backend (rsdroid) dependencies, choosing between the locally-built
 * artifacts and the published version-catalog libraries based on `local_backend` in
 * `local.properties`.
 *
 * See https://github.com/ankidroid/Anki-Android-Backend/
 */
// TODO: use context parameters when we compile kts with Kotlin 2.4.0
fun DependencyHandlerScope.addAnkiBackendDependencies(project: Project) {
    with(project) {
        val useLocalBackend = localBackendEnabled()

        addBackendArtifact(
            configuration = "implementation",
            useLocalBackend = useLocalBackend,
            catalogAlias = "ankiBackend-backend",
            localPaths = arrayOf(RSDROID_AAR, RSDROID_ANDROID_AAR),
        )
        addBackendArtifact(
            configuration = "testImplementation",
            useLocalBackend = useLocalBackend,
            catalogAlias = "ankiBackend-testing",
            localPaths = arrayOf(RSDROID_TESTING_JAR),
        )

        // protobuf is required when loading from a file, regardless of the backend source
        dependencies.addProvider("implementation", libsLibrary("protobuf-kotlin-lite"))

        if (project.hasTestFixtures) {
            addBackendArtifact(
                configuration = "testFixturesImplementation",
                useLocalBackend = useLocalBackend,
                catalogAlias = "ankiBackend-backend",
                localPaths = arrayOf(RSDROID_AAR, RSDROID_ANDROID_AAR),
            )
            addBackendArtifact(
                configuration = "testFixturesImplementation",
                useLocalBackend = useLocalBackend,
                catalogAlias = "ankiBackend-testing",
                localPaths = arrayOf(RSDROID_TESTING_JAR),
            )
            dependencies.addProvider("testFixturesImplementation", libsLibrary("protobuf-kotlin-lite"))
        }
    }
}

private val Project.hasTestFixtures: Boolean
    get() = configurations.findByName("testFixturesImplementation") != null

private fun Project.localBackendEnabled(): Boolean {
    val localProperties = rootProject.layout.projectDirectory.file("local.properties")
    val content = providers.fileContents(localProperties).asText.orNull ?: return false
    val properties = Properties().apply { content.reader().use { load(it) } }
    return properties["local_backend"] == "true"
}

private fun Project.addBackendArtifact(
    configuration: String,
    useLocalBackend: Boolean,
    catalogAlias: String,
    vararg localPaths: String,
) {
    if (useLocalBackend) {
        // ../Anki-Android-Backend
        val backendCheckout = File(rootProject.projectDir.parentFile, "Anki-Android-Backend")
        // Local AARs have no Maven metadata, so both the API and Android artifacts are needed.
        dependencies.add(configuration, files(localPaths.map { File(backendCheckout, it) }))
    } else {
        dependencies.addProvider(configuration, libsLibrary(catalogAlias))
    }
}
