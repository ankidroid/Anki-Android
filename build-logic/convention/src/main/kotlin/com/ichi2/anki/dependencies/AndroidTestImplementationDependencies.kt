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

import com.ichi2.anki.androidTestImplementation
import com.ichi2.anki.libs
import org.gradle.api.Project
import org.gradle.kotlin.dsl.DependencyHandlerScope
import org.gradle.kotlin.dsl.exclude

fun DependencyHandlerScope.androidTestImplementationDependencies(target: Project) {

    with(target) {
        configurations.getByName("androidTestImplementation") {
            exclude(module = "protobuf-lite")
        }
        androidTestImplementation(project(":testlib"))

        // May need a resolution strategy for support libs to our versions
        androidTestImplementation(libs.androidx.espresso.core)
        androidTestImplementation(libs.androidx.test.core)
        androidTestImplementation(libs.androidx.test.junit)
        androidTestImplementation(libs.androidx.test.rules)
        androidTestImplementation(libs.androidx.uiautomator)
        androidTestImplementation(libs.kotlin.test)
        androidTestImplementation(libs.kotlin.test.junit)
        androidTestImplementation(libs.androidx.fragment.testing)
    }
}