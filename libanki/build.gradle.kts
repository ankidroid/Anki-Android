import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.ichi2.anki.libanki"
    compileSdk =
        libs.versions.compileSdk
            .get()
            .toInt()

    defaultConfig {
        minSdk =
            libs.versions.minSdk
                .get()
                .toInt()

        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_17
        }
    }
}

apply(from = "../lint.gradle")
apply(from = "../jacocoSupport.gradle")

dependencies {
    // Project dependencies
    implementation(project(":common"))

    // Backend libraries
    implementation(libs.protobuf.kotlin.lite) // This is required when loading from a file

    val localProperties = Properties()
    if (project.rootProject.file("local.properties").exists()) {
        localProperties.load(project.rootProject.file("local.properties").inputStream())
    }
    if (localProperties["local_backend"] == "true") {
        implementation(files(rootProject.file("../Anki-Android-Backend/rsdroid/build/outputs/aar/rsdroid-release.aar")))
        testImplementation(files(rootProject.file("../Anki-Android-Backend/rsdroid-testing/build/libs/rsdroid-testing.jar")))
    } else {
        implementation(libs.ankiBackend.backend)
        testImplementation(libs.ankiBackend.testing)
    }

    // JVM dependencies
    implementation(libs.jakewharton.timber)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    // Android interface dependencies
    implementation(libs.androidx.annotation)
    implementation(libs.androidx.sqlite.framework)
    testImplementation(libs.androidx.sqlite.framework)
    testImplementation(libs.androidx.test.rules) // @SdkSuppress

    // test dependencies
    testImplementation(libs.hamcrest)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.junit.vintage.engine)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.json)

    testImplementation(project(":libanki:testutils"))

    // project lint checks
    // PERF: some rules do not need to be applied... but the full run was 3s
    lintChecks(project(":lint-rules"))
}
