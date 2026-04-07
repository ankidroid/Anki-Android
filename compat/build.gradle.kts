import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.ichi2.anki.compat"
    testFixtures.enable = true
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

dependencies {
    implementation(project(":common"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.jakewharton.timber)

    testImplementation(testFixtures(project(":common")))
    testImplementation(testFixtures(project(":compat")))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.junit.vintage.engine)
    testImplementation(libs.hamcrest)
    testImplementation(libs.junit.platform.launcher)
    testImplementation(libs.mockk)
    testImplementation(libs.mockito.kotlin)
    testImplementation(kotlin("test"))

    testFixturesImplementation(project(":common"))
    testFixturesImplementation(testFixtures(project(":common")))
    testFixturesImplementation(libs.mockk)
    testFixturesImplementation(libs.mockito.kotlin)
    testFixturesImplementation(libs.junit.vintage.engine)
    testFixturesImplementation(kotlin("test"))
    // Required so the ExperimentalCoroutinesApi opt-in (applied globally) doesn't cause
    // an "unresolved" warning, which is treated as an error due to allWarningsAsErrors
    testFixturesImplementation(libs.kotlinx.coroutines.core)
}
