import com.android.build.gradle.internal.api.ApkVariantOutputImpl
import com.android.build.gradle.internal.tasks.R8Task
import java.util.Properties

plugins {
    // Gradle plugin portal
    alias(libs.plugins.tripletPlay)
    id("ankidroid.android.app")
    id("ankidroid.plugins.jacoco")
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.keeper)
    alias(libs.plugins.roborazzi)
    id("idea")
}

repositories {
    google()
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
}

keeper {
    traceReferences {
        // Silence missing definitions
        arguments.set(listOf("--map-diagnostics:MissingDefinitionsDiagnostic", "error", "none"))
    }
}

idea {
    module {
        isDownloadJavadoc = System.getenv("CI") != "true"
        isDownloadSources = System.getenv("CI") != "true"
    }
}

val homePath: String = System.getProperty("user.home")

// https://docs.gradle.org/current/userguide/configuration_cache_requirements.html#config_cache:requirements:external_processes

/**
 * Calculates the current git hash, invalidates configuration cache if changed
 * @example edf739d95bad7b370a6ed4398d46723f8219b3cd
 */
val gitCommitHash =
    providers
        .exec {
            commandLine("git", "rev-parse", "HEAD")
        }.standardOutput.asText
        .map { it.trim() }

/**
 * Epoch millis of the build. Displayed on the 'About' screen.
 *
 * CI passes the real time via -PbuildTime=<epoch> # example: 1776422957
 *
 * A local build uses a stale value (captured in the configuration cache) so the configuration cache
 * is not invalidated on every build.
 */
val buildTimeMillis =
    providers
        .gradleProperty("buildTime")
        .orElse(providers.provider { System.currentTimeMillis().toString() })

@Suppress("deprecation") // convert to configuration<> after android.newDsl=true (#20988)
android {
    val app = this

    namespace = "com.ichi2.anki"

    buildFeatures {
        buildConfig = true
        aidl = true
        viewBinding = true
        resValues = true
    }

    testBuildType = if (rootProject.testReleaseBuild) "release" else "debug"

    defaultConfig {
        applicationId = "com.ichi2.anki"
        buildConfigField("Boolean", "CI", (System.getenv("CI") == "true").toString())
        buildConfigField("String", "ACRA_URL", "\"https://ankidroid.org/acra/report\"")
        buildConfigField("String", "BACKEND_VERSION", "\"${libs.versions.ankiBackend.get()}\"")
        buildConfigField("Boolean", "ENABLE_LEAK_CANARY", "false")
        buildConfigField("String", "GIT_COMMIT_HASH", "\"${gitCommitHash.get()}\"")
        buildConfigField("long", "BUILD_TIME", buildTimeMillis.get())
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
        versionCode = 22400300
        // If you change this to a new version, you probably also want to update .gradle/workflows/milestone.yml for the new version...
        versionName = "2.24.0"

        testApplicationId = "com.ichi2.anki.tests"
        vectorDrawables.useSupportLibrary = true
        testInstrumentationRunner = "com.ichi2.testutils.NewCollectionPathTestRunner"
    }
    signingConfigs {
        create("release") {
            val keystorePath: String? = System.getenv("KEYSTOREPATH")
            if (keystorePath != null && keystorePath.trim().isNotEmpty()) {
                storeFile = file(keystorePath)
                storePassword = System.getenv("KEYSTOREPWD") ?: System.getenv("KSTOREPWD")
                keyAlias = System.getenv("KEYALIAS")
                keyPassword = System.getenv("KEYPWD")
            } else {
                storeFile = file("$rootDir/tools/fallback-release-keystore.jks")
                storePassword = "Test@123"
                keyAlias = "my-key"
                keyPassword = "Test@123"
            }
        }
    }
    buildTypes {
        named("debug") {
            versionNameSuffix = "-debug"
            isDebuggable = true
            applicationIdSuffix = ".debug"
            app.splits.abi.isUniversalApk = true // Build universal APK for debug always
            // Check Crash Reports page on developer wiki for info on ACRA testing
            // buildConfigField "String", "ACRA_URL", '"https://918f7f55-f238-436c-b34f-c8b5f1331fe5-bluemix.cloudant.com/acra-ankidroid/_design/acra-storage/_update/report"'
            if (project.rootProject.file("local.properties").exists()) {
                val localProperties = Properties()
                localProperties.load(project.rootProject.file("local.properties").inputStream())
                // #6009 Allow optional disabling of JaCoCo for general build (assembleDebug).
                // jacocoDebug task was slow, hung, and wasn't required unless I wanted coverage
                enableAndroidTestCoverage = localProperties["enable_coverage"] != "false"
                // not profiled: optimization for build times
                if (localProperties["enable_languages"] == "false") {
                    app.defaultConfig.resConfigs("en")
                }
                // allow disabling leak canary
                if (localProperties["enable_leak_canary"] != null) {
                    buildConfigField("Boolean", "ENABLE_LEAK_CANARY", localProperties["enable_leak_canary"] as String)
                } else {
                    buildConfigField("Boolean", "ENABLE_LEAK_CANARY", "true")
                }
            } else {
                enableAndroidTestCoverage = true
            }

            // make the icon red if in debug mode
            resValue("color", "anki_foreground_icon_color_0", "#FFFF0000")
            resValue("color", "anki_foreground_icon_color_1", "#FFFF0000")
        }
        named("release") {
            enableAndroidTestCoverage = rootProject.testReleaseBuild
            val minifyEnv = System.getenv("MINIFY_ENABLED")
            isMinifyEnabled = if (!minifyEnv.isNullOrEmpty()) minifyEnv != "false" else true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            testProguardFile("proguard-test-rules.pro")
            app.splits.abi.isUniversalApk = rootProject.universalApkEnabled // Build universal APK for release with `-Duniversal-apk=true`
            signingConfig = signingConfigs.getByName("release")

            // syntax: assembleRelease -PcustomSuffix="suffix" -PcustomName="New name"
            if (project.hasProperty("customSuffix")) {
                // the suffix needs a '.' at the start
                applicationIdSuffix = (project.property("customSuffix") as String).replaceFirst(Regex("^\\.*"), ".")
            }
            if (project.hasProperty("customName")) {
                resValue("string", "app_name", project.property("customName") as String)
            }

            resValue("color", "anki_foreground_icon_color_0", "#FF29B6F6")
            resValue("color", "anki_foreground_icon_color_1", "#FF0288D1")
        }
    }

    /*
     * Product Flavors are used for Amazon App Store and Google Play Store.
     * This is because we cannot use Camera Permissions in Amazon App Store (for FireTv etc...)
     * Therefore, different AndroidManifest for Camera Permissions is used in Amazon flavor.
     */
    flavorDimensions += "appStore"
    productFlavors {
        create("play") {
            isDefault = true
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
            // universalApk enableUniversalApk  // set in debug + release config blocks above
            include("armeabi-v7a", "x86", "arm64-v8a", "x86_64")
        }
    }
    // applicationVariants are e.g. debug, release
    applicationVariants.configureEach {
        val variant = this
        // We want the same version stream for all ABIs in debug but for release we can split them
        if (variant.buildType.name == "release") {
            variant.outputs.configureEach {
                val output = this as ApkVariantOutputImpl

                // For each separate APK per architecture, set a unique version code as described here:
                // https://developer.android.com/studio/build/configure-apk-splits.html
                val versionCodes = mapOf("armeabi-v7a" to 1, "x86" to 2, "arm64-v8a" to 3, "x86_64" to 4)
                val outputFile = output.outputFile
                if (outputFile != null && outputFile.name.endsWith(".apk")) {
                    val abi = output.getFilter("ABI")
                    if (abi != null) { // null for the universal-debug, universal-release variants
                        //  From: https://developer.android.com/studio/publish/versioning#appversioning
                        //  "Warning: The greatest value Google Play allows for versionCode is 2100000000"
                        //  AnkiDroid versionCodes have a budget 8 digits (through AnkiDroid 9)
                        //  This style does ABI version code ranges with the 9th digit as 0-4.
                        //  This consumes ~20% of the version range space, w/50 years of versioning at our major-version pace
                        output.versionCodeOverride =
                            // ex:  321200106 = 3 * 100000000 + 21200106
                            versionCodes.getValue(abi) * 100000000 + defaultConfig.versionCode!!
                    }
                }
            }
        }
    }

    testOptions {
        animationsDisabled = true
        unitTests {
            all { test ->
                test.useJUnitPlatform {
                    // ./gradlew testFullDebugUnitTest -Pscreenshot
                    if (project.hasProperty("screenshot")) {
                        includeTags("com.ichi2.anki.ScreenshotTestCategory")
                    } else {
                        excludeTags("com.ichi2.anki.ScreenshotTestCategory")
                    }
                    // Ensures that no EmptyApplication test relies on AnkiDroidApp
                    //  by only running EmptyApplication tests
                    // ./gradlew testFullDebugUnitTest -PemptyApplication
                    if (project.hasProperty("emptyApplication")) {
                        includeTags("com.ichi2.anki.EmptyApplicationCategory")
                    }
                }
            }
        }
    }

    // https://docs.gradle.org/current/userguide/java_testing.html#sec:java_test_fixtures
    // https://developer.android.com/build/releases/agp-7-2-0-release-notes#test-fixtures
    // ⚠️ There was an in-IDE warning: "Kotlin is not configured" when editing the testFixtures
    // files. I ended up ignoring the warning after the 'Configure' button in Android Studio
    // added dependencies but didn't fix the issue.
    @Suppress("UnstableApiUsage")
    testFixtures {
        enable = true
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
    }

    packaging {
        resources {
            excludes += "META-INF/DEPENDENCIES"
        }
    }
}

play {
    serviceAccountCredentials.set(file("$homePath/src/AnkiDroid-GCP-Publish-Credentials.json"))
    track.set("beta")

    // any time we bump minSdk we want Play Store to retain the old artifacts by version code,
    // so that they remain available for older devices
    retain {
        artifacts.set(
            listOf(
                20700300L, // (2.7, minSdk 10, universal APK)
                20804300L, // (2.8.4, minSdk 10, universal APK)
                21004300L, // (2.10.4, minSdk 15, universal APK)
                // release-2.14 minSdk 16: missing and not re-publishable, see issue 17791
                121603300L, // (2.16.3, minSdk 21, ABI armeabi-v7a)
                221603300L, // (2.16.3, minSdk 21, ABI x86)
                321603300L, // (2.16.3, minSdk 21, ABI arm64-v8a)
                421603300L, // (2.16.3, minSdk 21, ABI x86_64)
                121905300L, // (2.19.5, minSdk 23, ABI armeabi-v7a)
                221905300L, // (2.19.5, minSdk 23, ABI x86)
                321905300L, // (2.19.5, minSdk 23, ABI arm64-v8a)
                421905300L, // (2.19.5, minSdk 23, ABI x86_64)
            ),
        )
    }

    // If you retain APKs in a release with different names as we do above,
    // the plugin + Play Store has no idea how to name the release except by date.
    // release name is developer only, but sane names really help, so set one
    @Suppress("DEPRECATION")
    releaseName.set(android.defaultConfig.versionName)
}

// Install Git pre-commit hook for Ktlint
// Resolve via git so worktrees (where `.git` is a file) are handled correctly.
val gitHooksDir =
    providers
        .exec {
            workingDir = rootProject.rootDir
            commandLine("git", "rev-parse", "--git-path", "hooks")
        }.standardOutput.asText
        .map {
            rootProject.rootDir
                .toPath()
                .resolve(it.trim())
                .toFile()
        }

tasks.register<Copy>("installGitHook") {
    from(File(rootProject.rootDir, "pre-commit"))
    into(gitHooksDir)
    filePermissions {
        user {
            read = true
            write = true
            execute = true
        }
    }
}
// to run manually: `./gradlew installGitHook`
tasks.named("preBuild").configure { dependsOn("installGitHook") }

// Issue 11078 - some emulators run, but run zero tests, and still report success
tasks.register("assertNonzeroAndroidTests") {
    // Resolve the directory at configuration time, which is Gradle Configuration Cache compatible
    val resultsDir = layout.buildDirectory.dir("outputs/androidTest-results/connected/flavors/play")
    doLast {
        // androidTest currently creates one .xml file per emulator with aggregate results in this dir
        val folder: File = resultsDir.get().asFile
        val listOfFiles: Array<File> = folder.listFiles { _, f -> f.matches(Regex(".*.xml")) } ?: emptyArray()
        for (file in listOfFiles) {
            // The aggregate results file currently contains a line with this pattern holding test count
            val matches = file.readLines().filter { it.contains("<testsuite") }
            if (matches.size != 1) {
                throw GradleException("Unable to determine count of tests executed for ${file.name}. Regex pattern out of date?")
            }
            if (!matches[0].matches(Regex(""".* tests="\d+" .*""")) || matches[0].contains("""tests="0"""")) {
                throw GradleException(
                    "androidTest executed 0 tests for ${file.name} - Probably a bug with the emulator. Try another image.",
                )
            }
        }
    }
}
afterEvaluate {
    tasks.named("connectedPlay${rootProject.androidTestVariantName}AndroidTest").configure {
        finalizedBy("assertNonzeroAndroidTests")
    }

    // AGP bug: when testFixtures is enabled on an application module, the androidTest R8 task's
    // TESTED_CODE scope only includes the app's dependency artifacts, not the app's own compiled
    // classes. This causes R8 to report "Missing class" errors for all app classes referenced from
    // androidTest code. Work around by adding the app classes as referenced (library) input.
    if (rootProject.testReleaseBuild) {
        tasks.named<R8Task>("minifyPlayReleaseAndroidTestWithR8").configure {
            referencedClasses.from(
                files("build/intermediates/compile_app_classes_jar/playRelease/bundlePlayReleaseClassesToCompileJar/classes.jar"),
            )
        }
    }
}

apply(from = "./robolectricDownloader.gradle")

configurations.configureEach {
    resolutionStrategy {
        // Timber has this as a dependency, but they are not up to date. We want to force our version.
        force(libs.jetbrains.annotations)
    }
}

dependencies {
    api(project(":api"))
    implementation(libs.androidx.work.runtime)
    lintChecks(project(":lint-rules"))
    coreLibraryDesugaring(libs.desugar.jdk.libs.nio)

    compileOnly(libs.jetbrains.annotations)
    compileOnly(libs.auto.service.annotations)
    annotationProcessor(libs.auto.service)

    // modules
    implementation(project(":common"))
    implementation(project(":common:android"))
    implementation(project(":compat"))
    implementation(project(":libanki"))
    implementation(project(":vbpd"))

    implementation(libs.androidx.activity)
    implementation(libs.androidx.annotation)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.draganddrop)
    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.media)
    implementation(libs.androidx.preference.ktx)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.sqlite.framework)
    implementation(libs.androidx.swiperefreshlayout)
    implementation(libs.androidx.viewpager2)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.webkit)
    implementation(libs.google.material)
    implementation(libs.android.image.cropper)
    implementation(libs.nanohttpd)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.seismic)

    debugImplementation(libs.androidx.fragment.testing.manifest)

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

    // May need a resolution strategy for support libs to our versions
    implementation(libs.acra.limiter)
    implementation(libs.acra.toast)
    implementation(libs.acra.dialog)
    implementation(libs.acra.http)

    implementation(libs.commons.compress)
    implementation(libs.commons.collections4) // SetUniqueList
    implementation(libs.commons.io) // FileUtils.contentEquals
    implementation(libs.mikehardy.google.analytics.java7)
    implementation(libs.okhttp)
    implementation(libs.slf4j.timber)
    implementation(libs.jakewharton.timber)
    implementation(libs.jsoup)
    implementation(libs.java.semver) // For AnkiDroid JS API Versioning
    implementation(libs.drakeet.drawer)
    implementation(libs.skydoves.colorpickerview)
    implementation(libs.kotlin.reflect)
    implementation(libs.kotlin.test)
    implementation(libs.search.preference)

    // Cannot use debugImplementation since classes need to be imported in AnkiDroidApp
    // and there's no no-op version for release build. Usage has been disabled for release
    // build via AnkiDroidApp.
    implementation(libs.leakcanary.android)

    testImplementation(testFixtures(project(":libanki")))
    testImplementation(testFixtures(project(":common")))
    testImplementation(testFixtures(project(":compat")))

    // A path for a testing library which provide Parameterized Test
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.junit.jupiter.params)
    testImplementation(libs.junit.vintage.engine)
    testImplementation(libs.mockito.inline)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.hamcrest)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.junit)
    testImplementation(libs.kotlin.reflect)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.androidx.fragment.testing)
    // in a JvmTest we need org.json.JSONObject to not be mocked
    testImplementation(libs.json)
    testImplementation(libs.ivanshafran.shared.preferences.mock)
    testImplementation(libs.androidx.test.runner)
    testImplementation(libs.androidx.test.rules)
    testImplementation(libs.androidx.espresso.core)
    testImplementation(libs.androidx.espresso.contrib) {
        exclude(module = "protobuf-lite")
    }
    testImplementation(libs.androidx.work.testing)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.rule)
    // for testing flows
    testImplementation(libs.cashapp.turbine)

    // May need a resolution strategy for support libs to our versions
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.espresso.contrib) {
        exclude(module = "protobuf-lite")
    }
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.uiautomator)
    androidTestImplementation(libs.kotlin.test)
    androidTestImplementation(libs.kotlin.test.junit)
    androidTestImplementation(libs.androidx.fragment.testing)

    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.dash)
    implementation(libs.androidx.media3.ui)

    // ---- testFixtures setup ----
    testFixturesImplementation(libs.kotlin.stdlib)
    testFixturesImplementation(libs.jakewharton.timber)
    testFixturesImplementation(libs.hamcrest)
    testFixturesImplementation(libs.androidx.test.junit)
    // Required so the ExperimentalCoroutinesApi opt-in (applied globally) doesn't cause
    // an "unresolved" warning, which is treated as an error due to allWarningsAsErrors
    testFixturesImplementation(libs.kotlinx.coroutines.core)
}

val Project.androidTestVariantName: String get() = extra["androidTestVariantName"] as String
val Project.testReleaseBuild: Boolean get() = extra["testReleaseBuild"] as Boolean
val Project.universalApkEnabled: Boolean get() = extra["universalApkEnabled"] as Boolean
