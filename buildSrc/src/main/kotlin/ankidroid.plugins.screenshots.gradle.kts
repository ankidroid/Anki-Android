// SPDX-License-Identifier: GPL-3.0-or-later

// Apply after the Android app/library plugin. Gradle runs these aliases in every
// participating module: ./gradlew compareScreenshots -Pscreenshot
plugins {
    id("io.github.takahirom.roborazzi")
}

val screenshotRun = providers.gradleProperty("screenshot").isPresent
val variant = if (plugins.hasPlugin("com.android.application")) "PlayDebug" else "Debug"

tasks.register("compileScreenshotTests") {
    group = "verification"
    description = "Compiles $variant screenshot tests without running them."
    dependsOn("compile${variant}UnitTestSources")
}

listOf("record", "compare", "verify").forEach { mode ->
    tasks.register("${mode}Screenshots") {
        group = "verification"
        description = "Runs Roborazzi $mode for $variant (requires -Pscreenshot)."
        if (screenshotRun) {
            dependsOn("${mode}Roborazzi$variant")
        } else {
            doLast { throw GradleException("Screenshot tasks require -Pscreenshot") }
        }
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform {
        if (screenshotRun) {
            includeTags("com.ichi2.anki.ScreenshotTestCategory")
        } else {
            excludeTags("com.ichi2.anki.ScreenshotTestCategory")
        }
    }
}
