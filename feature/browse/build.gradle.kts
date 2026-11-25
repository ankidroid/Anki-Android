// SPDX-License-Identifier: GPL-3.0-or-later

import com.android.build.api.dsl.LibraryExtension
import com.ichi2.anki.gradle.addAnkiBackendDependencies

plugins {
    id("ankidroid.android.library")
    id("ankidroid.android.compose")
    id("org.jetbrains.kotlin.plugin.parcelize")
    alias(libs.plugins.kotlin.serialization)
}

configure<LibraryExtension> {
    namespace = "com.ichi2.anki.browse"
    buildFeatures.viewBinding = true
}

dependencies {
    implementation(project(":anki-common"))
    implementation(project(":common"))
    implementation(project(":common:android"))
    implementation(project(":libanki"))

    implementation(libs.androidx.core)
    implementation(libs.androidx.lifecycle.viewmodel.core)
    implementation(libs.jakewharton.timber)
    implementation(libs.slf4j.api)

    addAnkiBackendDependencies(project)

    testImplementation(testFixtures(project(":anki-common")))
    testImplementation(testFixtures(project(":libanki")))
    testImplementation(libs.androidx.test.junit)
    testImplementation(libs.cashapp.turbine)
    testImplementation(libs.hamcrest)
    testImplementation(libs.ivanshafran.shared.preferences.mock)
    testImplementation(libs.json)
    testImplementation(libs.junit.platform.launcher)
    testImplementation(libs.junit.vintage.engine)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
}
