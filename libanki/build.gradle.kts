import java.util.Properties

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
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

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

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
        implementation(files("../../Anki-Android-Backend/rsdroid/build/outputs/aar/rsdroid-release.aar"))
        testImplementation(files("../../Anki-Android-Backend/rsdroid-testing/build/libs/rsdroid-testing.jar"))
    } else {
        implementation(libs.ankiBackend.backend)
        testImplementation(libs.ankiBackend.testing)
    }

    // JVM dependencies
    implementation(libs.jakewharton.timber)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    // Android dependencies
    implementation(libs.androidx.annotation)
}
