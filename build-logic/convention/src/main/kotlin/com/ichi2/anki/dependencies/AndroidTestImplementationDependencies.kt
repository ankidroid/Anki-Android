package com.ichi2.anki.dependencies

import com.ichi2.anki.androidTestImplementation
import com.ichi2.anki.libs
import org.gradle.api.Project
import org.gradle.kotlin.dsl.DependencyHandlerScope
import org.gradle.kotlin.dsl.exclude
import org.gradle.kotlin.dsl.project

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