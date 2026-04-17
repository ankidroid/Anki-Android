import org.jlleitschuh.gradle.ktlint.KtlintExtension

plugins {
    `kotlin-dsl`
    alias(libs.plugins.ktlint.gradle.plugin)
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

configure<KtlintExtension> {
    version.set(libs.versions.ktlint.get())
}
