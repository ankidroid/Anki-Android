// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.gradle

import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.withType
import org.gradle.testing.jacoco.plugins.JacocoTaskExtension

/** Configures the JaCoCo agent for JVM tests. */
fun Project.configureJacocoAgent() {
    tasks.withType<Test>().configureEach {
        val jacoco = extensions.getByType(JacocoTaskExtension::class.java)
        jacoco.isIncludeNoLocationClasses = true
        jacoco.excludes = listOf("jdk.internal.*")
    }
}
