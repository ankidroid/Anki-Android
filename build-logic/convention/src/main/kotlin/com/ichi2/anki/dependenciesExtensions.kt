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
package com.ichi2.anki

import org.gradle.api.artifacts.dsl.DependencyHandler
import  org.gradle.api.artifacts.Dependency

fun DependencyHandler.implementation(
    dependencyNotation: Any,
): Dependency? {
    return add("implementation", dependencyNotation)
}


fun DependencyHandler.testImplementation(
    dependencyNotation: Any,
): Dependency? {
    return add("testImplementation", dependencyNotation)
}

fun DependencyHandler.api(
    dependencyNotation: Any,
): Dependency? {
    return add("api", dependencyNotation)
}

fun DependencyHandler.debugImplementation(
    dependencyNotation: Any,
): Dependency? {
    return add("debugImplementation", dependencyNotation)
}
fun DependencyHandler.androidTestImplementation(
    dependencyNotation: Any,
): Dependency? {
    return add("androidTestImplementation", dependencyNotation)
}

fun DependencyHandler.compileOnly(
    dependencyNotation: Any,
): Dependency? {
    return add("compileOnly", dependencyNotation)
}

fun DependencyHandler.annotationProcessor(
    dependencyNotation: Any,
): Dependency? {
    return add("annotationProcessor", dependencyNotation)
}


fun DependencyHandler.lintChecks(
    dependencyNotation: Any,
): Dependency? {
    return add("lintChecks", dependencyNotation)
}

fun DependencyHandler.coreLibraryDesugaring(
    dependencyNotation: Any,
): Dependency? {
    return add("coreLibraryDesugaring", dependencyNotation)
}
