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

dependencies {
    implementation(project(":AnkiDroid"))
    implementation(libs.jakewharton.timber)
    compileOnly(libs.kotlinx.coroutines.core)
    compileOnly(libs.hamcrest)
    compileOnly(libs.junit.jupiter)
    compileOnly(libs.junit.jupiter.params)
    compileOnly(libs.junit.vintage.engine)
}
