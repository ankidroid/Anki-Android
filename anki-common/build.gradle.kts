// SPDX-License-Identifier: GPL-3.0-or-later

import com.android.build.api.dsl.LibraryExtension
import com.ichi2.anki.gradle.addAnkiBackendDependencies

plugins {
    id("ankidroid.android.library")
    id("org.jetbrains.kotlin.plugin.parcelize")
    alias(libs.plugins.kotlin.compose)
}

configure<LibraryExtension> {
    // code should live inside com.ichi2.anki
    // namespace must be unique for resources generation.
    namespace = "com.ichi2.anki.ankicommon"
    buildFeatures.buildConfig = false
    buildFeatures.compose = true
    testFixtures.enable = true
}

dependencies {
    implementation(project(":common"))
    implementation(project(":common:android"))
    implementation(project(":libanki"))
    implementation(project(":compat"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    addAnkiBackendDependencies(project)

    implementation(libs.androidx.activity)
    implementation(libs.androidx.sqlite.framework)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.jakewharton.timber)
    implementation(libs.kotlinx.coroutines.core)

    testFixturesImplementation(project(":common:android"))
    testFixturesImplementation(libs.androidx.core.ktx)
    testFixturesImplementation(libs.androidx.test.core)
    // The Kotlin Compose Compiler plugin attaches to every Kotlin compilation in the
    // module including testFixtures, which has no @Composable code and refuses to
    // run unless the Compose Runtime is on the classpath. compileOnly satisfies the
    // plugin's version check without shipping the runtime in the testFixtures output.
    testFixturesCompileOnly(platform(libs.androidx.compose.bom))
    testFixturesCompileOnly(libs.androidx.compose.runtime)
}
