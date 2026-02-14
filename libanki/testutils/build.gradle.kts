/*
 *  Copyright (c) 2025 David Allison <davidallisongithub@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */

import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.ichi2.anki.libanki.testutils"
    compileSdk =
        libs.versions.compileSdk
            .get()
            .toInt()

    defaultConfig {
        minSdk =
            libs.versions.minSdk
                .get()
                .toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17

        packaging {
            resources {
                // testutils is not compiled into the public apk
                excludes += "META-INF/DEPENDENCIES"
                excludes += "META-INF/LICENSE.md"
                excludes += "META-INF/LICENSE-notice.md"
            }
        }
    }
    kotlin {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_17
        }
    }
}

dependencies {
    implementation(project(":common"))
    implementation(project(":libanki"))

    val localProperties = Properties()
    if (project.rootProject.file("local.properties").exists()) {
        localProperties.load(project.rootProject.file("local.properties").inputStream())
    }
    if (localProperties["local_backend"] == "true") {
        implementation(files(rootProject.file("../Anki-Android-Backend/rsdroid/build/outputs/aar/rsdroid-release.aar")))
        testImplementation(files(rootProject.file("../Anki-Android-Backend/rsdroid-testing/build/libs/rsdroid-testing.jar")))
    } else {
        implementation(libs.ankiBackend.backend)
        implementation(libs.ankiBackend.testing)
    }

    implementation(libs.jakewharton.timber)
    implementation(libs.junit.vintage.engine)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.test)

    implementation(libs.androidx.sqlite.framework)
}
