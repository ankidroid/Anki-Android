plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.ichi2.anki.testlib"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
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

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11

        kotlinOptions {
            jvmTarget = "11"
            freeCompilerArgs += "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi"
        }

        packaging {
            resources {
                excludes += "META-INF/DEPENDENCIES"
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
