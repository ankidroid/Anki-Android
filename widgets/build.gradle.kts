// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>
// SPDX-License-Identifier: GPL-3.0-or-later
import com.android.build.api.dsl.LibraryExtension

plugins {
    id("ankidroid.android.library")
}

configure<LibraryExtension> {
    namespace = "com.ichi2.anki.widgets"
    buildFeatures.viewBinding = true
}

dependencies {
    implementation(project(":anki-common"))
    implementation(project(":common"))
    implementation(project(":common:android"))
    implementation(project(":libanki"))

    implementation(libs.androidx.annotation)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.recyclerview)
    implementation(libs.google.material)
    implementation(libs.jakewharton.timber)
    implementation(libs.kotlinx.coroutines.core)
}
