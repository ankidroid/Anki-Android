import com.android.build.configs.NdkConfig

plugins {
    id("com.android.application")
    kotlin("android")
    id("kotlin-parcelize")
    id("com.google.android.gms.oss-licenses-plugin")
    id("de.mannodermaus.android-junit5")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    compileSdk = 35
    namespace = "com.ichi2.anki"

    defaultConfig {
        applicationId = "com.ichi2.anki"
        minSdk = 21
        targetSdk = 34
        versionCode = 20190101
        versionName = "2.19alpha1"
        testInstrumentationRunner = "com.ichi2.test.utils.AnkiDroidTestRunner"
        vectorDrawables.useSupportLibrary = true

        // TODO: remove this once the minSdk is 26
        multiDexEnabled = true

        ndk {
            abiFilters.addAll(listOf("x86", "x86_64", "armeabi-v7a", "arm64-v8a"))
        }

        javaCompileOptions {
            annotationProcessorOptions {
                arguments(mapOf("room.schemaLocation" to "$projectDir/schemas"))
            }
        }
    }

    buildTypes {
        getByName("debug") {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            isJniDebuggable = true
        }
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    packagingOptions {
        jniLibs {
            useLegacyPackaging = true
        }
        resources {
            excludes.add("META-INF/LICENSE.txt")
            excludes.add("META-INF/NOTICE.txt")
            excludes.add("**/attach_hotspot_dot.png")
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.all {
            it.useJUnitPlatform()
        }
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.1"
    }
}

dependencies {
    implementation(project(":AnkiDroidApi"))
    implementation(project(":lint-rules"))
    implementation(libs.acra.mail)
    implementation(libs.acra.dialog)
    implementation(libs.acra.toast)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.cardview)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.customview.poolingcontainer)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.lifecycle.common.java8)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.multidex)
    implementation(libs.androidx.preference.ktx)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.startup.runtime)
    implementation(libs.androidx.viewpager2)
    implementation(libs.androidx.webkit)
    implementation(libs.compose.compiler)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material)
    implementation(libs.compose.material.icons.core)
    implementation(libs.compose.material.pullrefresh)
    implementation(libs.compose.material3)
    implementation(libs.compose.runtime)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.ui.tooling)
    implementation(libs.google.android.material)
    implementation(libs.google.code.gson)
    implementation(libs.google.dagger)
    implementation(libs.google.zxing.core)
    implementation(libs.jetbrain.kotlin.stdlib.jdk8)
    implementation(libs.jetbrain.kotlin.reflect)
    implementation(libs.jetbrain.kotlinx.collections.immutable)
    implementation(libs.jetbrain.kotlinx.coroutines.android)
    implementation(libs.jetbrain.kotlinx.coroutines.core)
    implementation(libs.jsoup)
    implementation(libs.legacy.support.v4)
    implementation(libs.mikepenz.aboutlibraries.core)
    implementation(libs.mikepenz.aboutlibraries)
    implementation(libs.slf4j.api)
    implementation(libs.timber)
    implementation(libs.vyshane.slf4j.timber)
    implementation(libs.websocket)
    testImplementation(project(":AnkiDroidApi-Test"))
    testImplementation(libs.androidx.arch.core.testing)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.espresso.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.test.runner)
    testImplementation(libs.google.dagger.testing)
    testImplementation(libs.jetbrain.kotlinx.coroutines.test)
    testImplementation(libs.junit)
    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.junit.jupiter.params)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.inline)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.robolectric)
    testImplementation(libs.strikt.core)
    testImplementation(libs.turbine)
    annotationProcessor(libs.google.dagger.compiler)
    ksp(libs.androidx.room.compiler)
    debugImplementation(libs.acra.limiter)
    debugImplementation(libs.square.leakcanary.android)
    releaseImplementation(libs.acra.noop)
}

// ./gradlew :AnkiDroid:googlePlayLicenses
apply(plugin = "com.google.android.gms.oss-licenses-plugin")

ossLicenses {
    // ./gradlew :AnkiDroid:generateOssLicensesMenuDebug
    // ./gradlew :AnkiDroid:generateOssLicensesMenuRelease
}
