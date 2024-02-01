plugins {
    id("com.android.library")
    id("kotlin-android")
}

android {
    namespace = "com.ichi2.anki.testlib"
    compileSdk = 34

    defaultConfig {
        minSdk = 23
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
    compileOnly("org.jetbrains.kotlinx:kotlinx-coroutines-core")
    compileOnly("org.hamcrest:hamcrest:" + rootProject.ext["hamcrest_version"])
    compileOnly("org.junit.jupiter:junit-jupiter:" + rootProject.ext["junit_version"])
    compileOnly("org.junit.jupiter:junit-jupiter-params:" + rootProject.ext["junit_version"])
    compileOnly("org.junit.vintage:junit-vintage-engine:" + rootProject.ext["junit_version"])
}
