import com.android.build.api.dsl.LibraryExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
}

configure<LibraryExtension> {
    // this cannot conflict with com.ichi2.anki
    // but we can define files in 'com.ichi2.anki' inside 'common'
    // even with this namespace
    namespace = "com.ichi2.anki.common"
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

apply(from = "../lint.gradle")
apply(from = "../jacocoSupport.gradle")

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.google.material)
    implementation(libs.jakewharton.timber)
    implementation(libs.slf4j.api)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.junit.vintage.engine)
    testImplementation(libs.hamcrest)
    testImplementation(libs.junit.platform.launcher)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    testImplementation(kotlin("test"))

    testFixturesImplementation(libs.hamcrest)
    testFixturesImplementation(libs.jakewharton.timber)
    testFixturesImplementation(libs.slf4j.api)
    testFixturesImplementation(libs.androidx.annotation)
    // Required so the ExperimentalCoroutinesApi opt-in (applied globally) doesn't cause
    // an "unresolved" warning, which is treated as an error due to allWarningsAsErrors
    testFixturesImplementation(libs.kotlinx.coroutines.core)
}
