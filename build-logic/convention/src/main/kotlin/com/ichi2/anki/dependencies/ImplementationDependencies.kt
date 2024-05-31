/*
 *  Copyright (c) 2024 Abd-Elrahman Esam <abdelrahmanesam20000@gmail.com>
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

package com.ichi2.anki.dependencies

import com.ichi2.anki.implementation
import com.ichi2.anki.libs
import com.ichi2.anki.testImplementation
import org.gradle.api.Project
import org.gradle.kotlin.dsl.DependencyHandlerScope
import java.util.Properties

fun DependencyHandlerScope.implementationDependencies(target: Project) {
    with(target) {
        implementation(libs.androidx.work.runtime)

        // Backend libraries

        implementation(libs.protobuf.kotlin.lite) // This is required when loading from a file)

        val localProperties = Properties()
        if (project.rootProject.file("local.properties").exists()) {
            localProperties.load(
                project.rootProject.file("local.properties").inputStream()
            )
        }
        if (localProperties.getProperty("local_backend") == "true") {
            implementation(files("../../Anki-Android-Backend/rsdroid/build/outputs/aar/rsdroid-release.aar"))
            testImplementation(files("../../Anki-Android-Backend/rsdroid-testing/build/libs/rsdroid-testing.jar"))
        } else {
            implementation(libs.ankiBackend.backend)
            implementation(libs.ankiBackend.testing)
        }


        implementation(libs.androidx.activity)
        implementation(libs.androidx.annotation)
        implementation(libs.androidx.appcompat)
        implementation(libs.androidx.browser)
        implementation(libs.androidx.core.ktx)
        implementation(libs.androidx.exifinterface)
        implementation(libs.androidx.fragment.ktx)
        implementation(libs.androidx.media)
        implementation(libs.androidx.preference.ktx)
        implementation(libs.androidx.recyclerview)
        implementation(libs.androidx.sqlite.framework)
        implementation(libs.androidx.swiperefreshlayout)
        implementation(libs.androidx.viewpager2)
        implementation(libs.androidx.constraintlayout)
        implementation(libs.androidx.webkit)
        implementation(libs.google.material)
        implementation(libs.android.image.cropper)
        implementation(libs.nanohttpd)
        implementation(libs.kotlinx.serialization.json)
        implementation(libs.seismic)


        // May need a resolution strategy for support libs to our versions
        implementation(libs.acra.limiter)
        implementation(libs.acra.toast)
        implementation(libs.acra.dialog)
        implementation(libs.acra.http)
        implementation(libs.commons.compress)
        implementation(libs.commons.collections4) // SetUniqueList)
        implementation(libs.commons.io) // FileUtils.contentEquals)
        implementation(libs.mikehardy.google.analytics.java7)
        implementation(libs.okhttp)
        implementation(libs.slf4j.timber)
        implementation(libs.jakewharton.timber)
        implementation(libs.jsoup)
        implementation(libs.java.semver) // For AnkiDroid JS API Versioning)
        implementation(libs.drakeet.drawer)
        implementation(libs.tapTargetPrompt)
        implementation(libs.colorpicker)
        implementation(libs.kotlin.reflect)
        implementation(libs.kotlin.test)
        implementation(libs.search.preference)
        implementation(libs.androidx.work.testing)

        // Cannot use debugImplementation since classes need to be imported in AnkiDroidApp
        // and there's no no-op version for release build. Usage has been disabled for release
        // build via AnkiDroidApp.
        implementation(libs.leakcanary.android)
    }
}