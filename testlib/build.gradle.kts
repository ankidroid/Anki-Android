import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.ichi2.anki.testlib"
    compileSdk =
        libs.versions.compileSdk
            .get()
            .toInt()

    defaultConfig {
        minSdk =
            libs.versions.minSdk
                .get()
                .toInt()
    }

    flavorDimensions += "appStore"
    productFlavors {
        create("play") {
            dimension = "appStore"
        }
        create("amazon") {
            dimension = "appStore"
        }
        // A 'full' build has no restrictions on storage/camera. Distributed on GitHub/F-Droid
        create("full") {
            dimension = "appStore"
        }
    }
    kotlin {
        compilerOptions {
            freeCompilerArgs.add("-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi")
            jvmTarget = JvmTarget.JVM_17
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17

        packaging {
            resources {
                // testlib is not compiled into the public apk
                excludes += "META-INF/DEPENDENCIES"
                excludes += "META-INF/LICENSE.md"
                excludes += "META-INF/LICENSE-notice.md"
            }
        }
    }
}

apply(from = "../lint.gradle")

dependencies {
    implementation(project(":AnkiDroid"))
    implementation(libs.jakewharton.timber)
    implementation(libs.hamcrest)
    implementation(libs.hamcrest.library)
    implementation(libs.junit.jupiter)
    implementation(libs.androidx.test.junit)
    testImplementation(libs.junit.vintage.engine)
    testImplementation(libs.androidx.test.rules)
    testRuntimeOnly(libs.junit.platform.launcher)
}
