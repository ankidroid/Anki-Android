/*
 *  Copyright (c) 2024 Abd-Elrahman Esam <abdelrahmanesam20000@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki

import com.android.build.VariantOutput
import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.CommonExtension
import com.android.build.gradle.internal.api.ApkVariantOutputImpl
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.maven
import org.gradle.kotlin.dsl.provideDelegate
import org.gradle.kotlin.dsl.repositories
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Properties


internal fun Project.configureCommonFeaturesForApplicationPlugin(
    commonExtension: CommonExtension<*, *, *, *, *>,
) {

    commonExtension.apply {
        compileSdk = 34

        configureRepositories()
        configureSigningConfigs()
        configureBuildFeature()
        configureTestOptions()
        configureKotlinJvm()
        configureKotlin()
        configurePackaging()
        configureDefaultConfig()
        configureProductFlavors()
        configureSplitLogic()
        configureBuildVariants()
        configureBuildTypes()
    }
}


internal fun Project.configureCommonFeaturesForLibraryPlugin(
    commonExtension: CommonExtension<*, *, *, *, *>,
) {

    commonExtension.apply {
        compileSdk = 34
        configureKotlinJvm()
    }
}

internal fun Project.configureKotlinJvm() {
    extensions.configure<ApplicationExtension> {
        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
            isCoreLibraryDesugaringEnabled = true
        }
    }

}


internal fun Project.configureTestOptions() {
    extensions.configure<ApplicationExtension> {
        testOptions {
            animationsDisabled = true
        }
    }
}

internal fun Project.configurePackaging() {
    extensions.configure<ApplicationExtension> {
        packaging {
            resources {
                excludes += "META-INF/DEPENDENCIES"
            }
        }
    }
}

internal fun Project.configureBuildFeature() {
    extensions.configure<ApplicationExtension> {
        buildFeatures {
            buildConfig = true
            aidl = true
        }
    }
}

internal fun Project.configureKotlin() {
    tasks.withType<KotlinCompile>().configureEach {
        kotlinOptions {
            configureJvmTarget()
            val warningsAsErrors: String? by project
            allWarningsAsErrors = warningsAsErrors.toBoolean()
            freeCompilerArgs = freeCompilerArgs + listOf(
                // Enable experimental coroutines APIs, including Flow
                "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            )
        }
    }
}

internal fun KotlinCompile.configureJvmTarget() {
    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_17.toString()
    }
}


internal fun Project.configureBuildTypes() {
    extensions.configure<ApplicationExtension> {
        buildTypes {
            debug {
                versionNameSuffix = "-debug"
                isDebuggable = true
                applicationIdSuffix = ".debug"
                splits.abi.isUniversalApk = true // Build universal APK for debug always
                // Check Crash Reports page on developer wiki for info on ACRA testing
                // buildConfigField "String", "ACRA_URL", '"https://918f7f55-f238-436c-b34f-c8b5f1331fe5-bluemix.cloudant.com/acra-ankidroid/_design/acra-storage/_update/report"'
                if (project.rootProject.file("local.properties").exists()) {
                    val localProperties = Properties()
                    localProperties.load(
                        project.rootProject.file("local.properties").inputStream()
                    )
                    // #6009 Allow optional disabling of JaCoCo for general build (assembleDebug).
                    // jacocoDebug task was slow, hung, and wasn't required unless I wanted coverage
                    enableUnitTestCoverage = localProperties["enable_coverage"] != "false"
                    // not profiled: optimization for build times
                    if (localProperties["enable_languages"] == "false") {
                        defaultConfig.resourceConfigurations += "en"
                    }
                    // allows the scoped storage migration when the user is not logged in
                    if (localProperties["allow_unsafe_migration"] != null) {
                        buildConfigField(
                            "Boolean",
                            "ALLOW_UNSAFE_MIGRATION",
                            localProperties["allow_unsafe_migration"].toString()
                        )
                    }
                    // allow disabling leak canary
                    if (localProperties["enable_leak_canary"] != null) {
                        buildConfigField(
                            "Boolean",
                            "ENABLE_LEAK_CANARY",
                            localProperties["enable_leak_canary"].toString()
                        )
                    } else {
                        buildConfigField("Boolean", "ENABLE_LEAK_CANARY", "true")
                    }
                } else {
                    enableUnitTestCoverage = true
                }

                // make the icon red if in debug mode
                resValue("color", "anki_foreground_icon_color_0", "#FFFF0000")
                resValue("color", "anki_foreground_icon_color_1", "#FFFF0000")
                resValue(
                    "string",
                    "applicationId",
                    "${defaultConfig.applicationId}${applicationIdSuffix}"
                )
            }
            release {
                isMinifyEnabled = true
                splits.abi.isUniversalApk = "true" == System.getProperty(
                    "universal-apk",
                    "false"
                ) // Build universal APK for release with `-Duniversal-apk=true`
                proguardFiles(
                    getDefaultProguardFile("proguard-android.txt"),
                    "proguard-rules.pro"
                )
                signingConfig = signingConfigs.getByName("release")

                // syntax: assembleRelease -PcustomSuffix="suffix" -PcustomName="New name"
                if (project.hasProperty("customSuffix")) {
                    // the suffix needs a '.' at the start
                    applicationIdSuffix =
                        project.property("customSuffix").toString()
                            .replaceFirst("""/^\.*/""".toRegex(), ".")
                    resValue(
                        "string",
                        "applicationId",
                        "${defaultConfig.applicationId}${applicationIdSuffix}"
                    )
                } else {
                    resValue("string", "applicationId", defaultConfig.applicationId!!)
                }
                if (project.hasProperty("customName")) {
                    resValue(
                        "string",
                        "app_name",
                        project.property("customName").toString()
                    )
                }

                resValue("color", "anki_foreground_icon_color_0", "#FF29B6F6")
                resValue("color", "anki_foreground_icon_color_1", "#FF0288D1")
            }
        }
    }

}


internal fun Project.configureSigningConfigs() {
    extensions.configure<ApplicationExtension>
    {
        signingConfigs {
            create("release") {
                val homePath = System.getProperty("user.home")
                storeFile = file("$homePath/src/android-keystore")
                keyAlias = "nrkeystorealias"
                storePassword = System.getenv("KSTOREPWD")
                keyPassword = System.getenv("KEYPWD")
            }
        }
    }
}

internal fun Project.configureProductFlavors() {
    extensions.configure<ApplicationExtension>
    {
        /**
         * Product Flavors are used for Amazon App Store and Google Play Store.
         * This is because we cannot use Camera Permissions in Amazon App Store (for FireTv etc...)
         * Therefore, different AndroidManifest for Camera Permissions is used in Amazon flavor.
         *
         * This flavor block must stay in sync with the same block in testlib/build.gradle.kts
         */
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
    }
}


internal fun Project.configureSplitLogic() {
    extensions.configure<ApplicationExtension>
    {
        /**
         * Set this to true to create five separate APKs instead of one:
         *   - 2 APKs that only work on ARM/ARM64 devices
         *   - 2 APKs that only works on x86/x86_64 devices
         *   - a universal APK that works on all devices
         * The advantage is the size of most APKs is reduced by about 2.5MB.
         * Upload all the APKs to the Play Store and people will download
         * the correct one based on the CPU architecture of their device.
         */
        val enableSeparateBuildPerCPUArchitecture = true

        splits {
            abi {
                reset()
                isEnable = enableSeparateBuildPerCPUArchitecture
                //universalApk enableUniversalApk  // set in debug + release config blocks above
                include("armeabi-v7a", "x86", "arm64-v8a", "x86_64")
            }
        }
    }
}


internal fun Project.configureBuildVariants() {
    // applicationVariants are e.g. debug, release
    extensions.configure<ApplicationExtension>
    {
        abstractApp.applicationVariants.all {
            if (this.buildType.name == "release") {
                this.outputs.forEach { output ->

                    // For each separate APK per architecture, set a unique version code as described here:
                    // https://developer.android.com/studio/build/configure-apk-splits.html
                    val versionCodes = mapOf(
                        "armeabi-v7a" to 1,
                        "x86" to 2,
                        "arm64-v8a" to 3,
                        "x86_64" to 4
                    )
                    val outputFile = output.outputFile
                    if (outputFile.name.endsWith(".apk")) {
                        val abi =
                            output.filters.find { it.filterType == VariantOutput.ABI }?.identifier
                        abi?.let {
                            // null for the universal-debug, universal-release variants
                            //  From: https://developer.android.com/studio/publish/versioning#appversioning
                            //  "Warning: The greatest value Google Play allows for versionCode is 2100000000"
                            //  AnkiDroid versionCodes have a budget 8 digits (through AnkiDroid 9)
                            //  This style does ABI version code ranges with the 9th digit as 0-4.
                            //  This consumes ~20% of the version range space, w/50 years of versioning at our major-version pace

                            // ex:  321200106 = 3 * 100000000 + 21200106
                            (output as ApkVariantOutputImpl).versionCodeOverride =
                                    // ex:  321200106 = 3 * 100000000 + 21200106
                                (versionCodes[abi]?.times(100000000)
                                    ?: 0) + defaultConfig.versionCode!!


                        }
                    }
                }
            }
        }
    }
}

internal fun Project.configureRepositories() {
    extensions.configure<ApplicationExtension>
    {
        repositories {
            repositories.google()
            repositories.mavenCentral()
            repositories.maven(url = "https://jitpack.io")
        }
    }
}


internal fun Project.configureDefaultConfig() {
    extensions.configure<ApplicationExtension>
    {
        defaultConfig {
            applicationId = "com.ichi2.anki"
            targetSdk = 33
            minSdk = 23
            buildConfigField("Boolean", "CI", (System.getenv("CI") == "true").toString())
            buildConfigField("String", "ACRA_URL", "\"https://ankidroid.org/acra/report\"")
            buildConfigField(
                "String",
                "BACKEND_VERSION",
                "\"${libs.versions.ankiBackend.get()}\""
            )
            buildConfigField("Boolean", "ENABLE_LEAK_CANARY", "false")
            buildConfigField("Boolean", "ALLOW_UNSAFE_MIGRATION", "false")
            buildConfigField("String", "GIT_COMMIT_HASH", "\"${gitCommitHash()}\"")
            buildConfigField("long", "BUILD_TIME", System.currentTimeMillis().toString())
            resValue("string", "app_name", "AnkiDroid")

            // The version number is of the form:
            // <major>.<minor>.<maintenance>[dev|alpha<build>|beta<build>|]
            // The <build> is only present for alpha and beta releases (e.g., 2.0.4alpha2 or 2.0.4beta4), developer builds do
            // not have a build number (e.g., 2.0.4dev) and official releases only have three components (e.g., 2.0.4).
            //
            // The version code is derived from the version name as follows:
            // AbbCCtDD
            // A: 1-digit decimal number representing the major version
            // bb: 2-digit decimal number representing the minor version
            // CC: 2-digit decimal number representing the maintenance version
            // t: 1-digit decimal number representing the type of the build
            // 0: developer build
            // 1: alpha release
            // 2: beta release
            // 3: public release
            // DD: 2-digit decimal number representing the build
            // 00 for internal builds and public releases
            // alpha/beta build number for alpha/beta releases
            //
            // This ensures the correct ordering between the various types of releases (dev < alpha < beta < release) which is
            // needed for upgrades to be offered correctly.
            versionCode = 21800108
            versionName = "2.18alpha8"
            minSdk = 23 // also in testlib/build.gradle.kts
            // After #13695: change .tests_emulator.yml
            targetSdk =
                33 // also in [api|testlib]/build.gradle.kts and ../robolectricDownloader.gradle
            testApplicationId = "com.ichi2.anki.tests"
            vectorDrawables.useSupportLibrary = true
            testInstrumentationRunner = "com.ichi2.testutils.NewCollectionPathTestRunner"
        }
    }
}

internal fun gitCommitHash() {
    "git rev-parse HEAD".execute().trim()
}


internal fun String.execute(): String {
    val processBuilder = ProcessBuilder(this.split("\\s".toRegex()))
    val process = processBuilder.start()

    val reader = BufferedReader(InputStreamReader(process.inputStream))
    var line: String?
    val output = StringBuilder()

    while (reader.readLine().also { line = it } != null) {
        output.append(line).append('\n')
    }

    process.waitFor()

    return output.toString()
}
