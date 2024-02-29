import com.android.build.gradle.internal.tasks.factory.dependsOn

plugins {
    id("maven-publish")
    id("com.android.library")
    id("kotlin-android")
    id("org.jetbrains.dokka")
}

group = "com.ichi2.anki"
version = "2.0.0"

kotlin {
    explicitApi()
}

android {
    namespace = "com.ichi2.anki.api"
    compileSdk = 34

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        minSdk = 16
        buildConfigField(
            "String",
            "READ_WRITE_PERMISSION",
            "\"com.ichi2.anki.permission.READ_WRITE_DATABASE\""
        )
        buildConfigField("String", "AUTHORITY", "\"com.ichi2.anki.flashcards\"")
    }
    buildTypes {
        debug {
            buildConfigField(
                "String",
                "READ_WRITE_PERMISSION",
                "\"com.ichi2.anki.debug.permission.READ_WRITE_DATABASE\""
            )
            buildConfigField("String", "AUTHORITY", "\"com.ichi2.anki.debug.flashcards\"")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        // enable explicit api mode for additional checks related to the public api
        // see https://kotlinlang.org/docs/whatsnew14.html#explicit-api-mode-for-library-authors
        freeCompilerArgs += "-Xexplicit-api=strict"
        jvmTarget = JavaVersion.VERSION_1_8.toString()
    }

    publishing {
        singleVariant("release") {
            withJavadocJar()
            withSourcesJar()
        }
    }
}

apply(from = "../lint.gradle")

dependencies {
    implementation("androidx.annotation:annotation:1.7.0")
    implementation("org.jetbrains.kotlin:kotlin-stdlib:${rootProject.extra["kotlin_version"]}")

    testImplementation("org.junit.jupiter:junit-jupiter:${rootProject.extra["junit_version"]}")
    testImplementation("org.junit.vintage:junit-vintage-engine:${rootProject.extra["junit_version"]}")
    testImplementation("org.robolectric:robolectric:${rootProject.extra["robolectric_version"]}")
    testImplementation("org.jetbrains.kotlin:kotlin-test:${rootProject.extra["kotlin_version"]}")

    lintChecks(project(":lint-rules"))
}

afterEvaluate {
    publishing {
        publications {
            register<MavenPublication>("release") {
                version = version
                groupId = group.toString()
                artifactId = "api"

                pom {
                    name = "AnkiDroid API"
                    description = "A programmatic API exported by AnkiDroid"
                    url = "https://github.com/ankidroid/Anki-Android/tree/main/api"
                    licenses {
                        license {
                            name = "GNU LESSER GENERAL PUBLIC LICENSE, v3"
                            url =
                                "https://github.com/ankidroid/Anki-Android/blob/main/api/COPYING.LESSER"
                        }
                    }
                    scm {
                        connection = "scm:git:git://github.com/ankidroid/Anki-Android.git"
                        url = "https://github.com/ankidroid/Anki-Android"
                    }
                }

                afterEvaluate {
                    from(components["release"])
                }
            }
        }
        repositories {
            maven {
                // change URLs to point to your repos, e.g. http://my.org/repo
                val releasesRepoUrl = layout.buildDirectory.dir("repos/releases")
                val snapshotsRepoUrl = layout.buildDirectory.dir("repos/snapshots")
                url = uri(
                    if (version.toString().endsWith("SNAPSHOT")) snapshotsRepoUrl else releasesRepoUrl
                )
            }
        }
    }
}

val zipReleaseProvider = tasks.register("zipRelease", Zip::class) {
    from(layout.buildDirectory.dir("repos/releases"))
    destinationDirectory = layout.buildDirectory
    archiveFileName = "${layout.buildDirectory.get()}/release-${archiveVersion.get()}.zip"
}

// Use this task to make a release you can send to someone
// You may like `./gradlew :api:publishToMavenLocal for development
val generateRelease: TaskProvider<Task> = tasks.register("generateRelease") {
    doLast {
        println("Release $version can be found at ${layout.buildDirectory.get()}/repos/releases/")
        println("Release $version zipped can be found ${layout.buildDirectory.get()}/release-$version.zip")
    }
}

// tasks.named("publishMavenJavaPublicationToMavenRepository").dependsOn(tasks.named("assemble"))
// tasks.named("publish").dependsOn(tasks.named("assemble"))
generateRelease.dependsOn(tasks.named("publish"))
generateRelease.dependsOn(zipReleaseProvider)
