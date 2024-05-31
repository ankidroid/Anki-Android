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

import com.ichi2.anki.libs
import com.ichi2.anki.testImplementation
import org.gradle.api.Project
import org.gradle.kotlin.dsl.DependencyHandlerScope
import org.gradle.kotlin.dsl.exclude

fun DependencyHandlerScope.testImplementationDependencies(target: Project) {

    with(target) {
        configurations.getByName("testImplementation") {
            exclude(module = "protobuf-lite")
        }

        testImplementation(project(":testlib"))

        // A path for a testing library which provide Parameterized Test
        testImplementation(libs.junit.jupiter)
        testImplementation(libs.junit.jupiter.params)
        testImplementation(libs.junit.vintage.engine)
        testImplementation(libs.mockito.inline)
        testImplementation(libs.mockito.kotlin)
        testImplementation(libs.hamcrest)
        testImplementation(libs.androidx.work.testing)
        // robolectricDownloader.gradle *may* need a new SDK jar entry if they release one or if we change targetSdk. Instructions in that gradle file.
        testImplementation(libs.robolectric)
        testImplementation(libs.androidx.test.core)
        testImplementation(libs.androidx.test.junit)
        testImplementation(libs.kotlin.reflect)
        testImplementation(libs.kotlin.test)
        testImplementation(libs.kotlin.test.junit5)
        testImplementation(libs.kotlinx.coroutines.test)
        testImplementation(libs.mockk)
        testImplementation(libs.commons.exec) // obtaining the OS)
        testImplementation(libs.androidx.fragment.testing)
        // in a JvmTest we need org.json.JSONObject to not be mocked
        testImplementation(libs.json)
        testImplementation(libs.ivanshafran.shared.preferences.mock)
        testImplementation(libs.androidx.test.runner)
        testImplementation(libs.androidx.test.rules)
        testImplementation(libs.androidx.espresso.core)
        testImplementation(libs.androidx.espresso.contrib)
    }

}
