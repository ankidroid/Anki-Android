plugins {
    id 'maven-publish'
    id 'com.android.library'
    id 'kotlin-android'
    id "org.jetbrains.dokka"
}

group = "com.ichi2.anki"
version = "2.0.0"

android {

    namespace 'com.ichi2.anki.api'
    compileSdk 33

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        minSdk 16
        //noinspection OldTargetApi
        targetSdk 32
        buildConfigField "String", "READ_WRITE_PERMISSION", '"com.ichi2.anki.permission.READ_WRITE_DATABASE"'
        buildConfigField "String", "AUTHORITY", '"com.ichi2.anki.flashcards"'
    }
    buildTypes {
        debug {
            buildConfigField "String", "READ_WRITE_PERMISSION", '"com.ichi2.anki.debug.permission.READ_WRITE_DATABASE"'
            buildConfigField "String", "AUTHORITY", '"com.ichi2.anki.debug.flashcards"'
        }
        release {
            minifyEnabled false
            proguardFiles getDefaultProguardFile('proguard-android.txt'), 'proguard-rules.pro'
        }
    }
    compileOptions {
        sourceCompatibility JavaVersion.VERSION_1_8
        targetCompatibility JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        // enable explicit api mode for additional checks related to the public api
        // see https://kotlinlang.org/docs/whatsnew14.html#explicit-api-mode-for-library-authors
        freeCompilerArgs += '-Xexplicit-api=strict'
        jvmTarget = JavaVersion.VERSION_1_8
    }
}

apply from: "../lint.gradle"

dependencies {
    implementation 'androidx.annotation:annotation:1.6.0'
    implementation "org.jetbrains.kotlin:kotlin-stdlib:$kotlin_version"
    testImplementation "org.junit.jupiter:junit-jupiter:$junit_version"
    testImplementation "org.junit.vintage:junit-vintage-engine:$junit_version"
    testImplementation "org.robolectric:robolectric:$robolectric_version"
    testImplementation "org.jetbrains.kotlin:kotlin-test:$kotlin_version"

    lintChecks project(":lint-rules")
}

task androidSourcesJar(type: Jar) {
    archiveClassifier.set('sources')
    // For Android libraries
    from android.sourceSets.main.java.srcDirs
}

task dokkaJavadocJar(type: Jar, dependsOn: dokkaJavadoc) {
    from(dokkaJavadoc)
    archiveClassifier.set("javadoc")
    doLast {
        println("API javadocs output directory: ${dokkaJavadoc.outputDirectory.get()}")
        println("API javadocs jar output directory: ${destinationDirectory.get()}")
    }
}

publishing {
    publications {
        mavenJava(MavenPublication) {
            artifactId = "api"

            artifact("$buildDir/outputs/aar/${project.getName()}-release.aar")
            artifact androidSourcesJar
            artifact dokkaJavadocJar

            versionMapping {
                usage('java-api') {
                    fromResolutionOf('runtimeClasspath')
                }
                usage('java-runtime') {
                    fromResolutionResult()
                }
            }
            pom {
                name = 'AnkiDroid API'
                description = 'A programmatic API exported by AnkiDroid'
                url = 'https://github.com/ankidroid/Anki-Android/tree/main/api'
                licenses {
                    license {
                        name = 'GNU LESSER GENERAL PUBLIC LICENSE, v3'
                        url = 'https://github.com/ankidroid/Anki-Android/blob/main/api/COPYING.LESSER'
                    }
                }
                scm {
                    connection = 'scm:git:git://github.com/ankidroid/Anki-Android.git'
                    url = 'https://github.com/ankidroid/Anki-Android'
                }
            }
        }
    }
    repositories {
        maven {
            // change URLs to point to your repos, e.g. http://my.org/repo
            def releasesRepoUrl = layout.buildDirectory.dir('repos/releases')
            def snapshotsRepoUrl = layout.buildDirectory.dir('repos/snapshots')
            url = version.endsWith('SNAPSHOT') ? snapshotsRepoUrl : releasesRepoUrl
        }
    }
}

task zipRelease(type: Zip) {
    from layout.buildDirectory.dir('repos/releases')
    destinationDirectory = buildDir
    archiveFileName = "${buildDir}/release-${archiveVersion.get()}.zip"
}

// Use this task to make a release you can send to someone
// You may like `./gradlew :api:publishToMavenLocal for development
task generateRelease {
    doLast {
        println "Release ${version} can be found at ${buildDir}/repos/releases/"
        println "Release ${version} zipped can be found ${buildDir}/release-${version}.zip"
    }
}

publishMavenJavaPublicationToMavenRepository.dependsOn(assemble)
publish.dependsOn(assemble)
generateRelease.dependsOn(publish)
generateRelease.dependsOn(zipRelease)
