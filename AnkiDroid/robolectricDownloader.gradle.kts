import org.gradle.api.artifacts.type.ArtifactTypeDefinition
import org.gradle.api.tasks.Copy

/*
 * Downloads all android-all-instrumented dependencies and copies them to the mavenLocal() repository
 *
 * Once applied to your gradle project, can be executed with ./gradlew robolectricSdkDownload
 */

// The general idea of this was borrowed from https://gist.github.com/xian/05c4f27da6d4156b9827842217c2cd5c
// I then modified it heavily to allow easier addition of new SDK versions
// The full implementation is from https://gist.github.com/simtel12/13ff3e57c37e78e468502b51ebb0f4f2

// List from: https://github.com/robolectric/robolectric/blob/master/robolectric/src/main/java/org/robolectric/plugins/DefaultSdkProvider.java
// This list will need to be updated for new Android SDK versions that come out.
// Note: the PREINSTRUMENTED_VERSION constant is what you put on the end of the artifact

// Only the versions currently used in AnkiDroid Robolectric tests are active, the rest are commented out
// To update these versions, open a terminal in the Anki-Android directory and perform the following steps:
//   1. Run `rm -r ~/.m2` to delete the .m2 directory in your home directory.
//   2. Run `./gradlew jacocoUnitTestReport` to run all unit tests.
//   3. Run `find ~/.m2 -type d -name '*-robolectric-*'`.
//   4. Update the lines below to match the output from `find`.

val robolectricAndroidSdkVersions =
    listOf(
//        mapOf("androidVersion" to "6.0.1_r3", "frameworkSdkBuildVersion" to "r1"),
//        mapOf("androidVersion" to "7.0.0_r1", "frameworkSdkBuildVersion" to "r1"),
//        mapOf("androidVersion" to "7.1.0_r7", "frameworkSdkBuildVersion" to "r1"),
//        mapOf("androidVersion" to "8.0.0_r4", "frameworkSdkBuildVersion" to "r1"),
        mapOf("androidVersion" to "8.1.0", "frameworkSdkBuildVersion" to "4611349"),
        mapOf("androidVersion" to "9", "frameworkSdkBuildVersion" to "4913185-2"),
        mapOf("androidVersion" to "10", "frameworkSdkBuildVersion" to "5803371"),
        mapOf("androidVersion" to "11", "frameworkSdkBuildVersion" to "6757853"),
        mapOf("androidVersion" to "12", "frameworkSdkBuildVersion" to "7732740"),
        mapOf("androidVersion" to "12.1", "frameworkSdkBuildVersion" to "8229987"),
        mapOf("androidVersion" to "13", "frameworkSdkBuildVersion" to "9030017"),
        mapOf("androidVersion" to "14", "frameworkSdkBuildVersion" to "10818077"),
        mapOf("androidVersion" to "15", "frameworkSdkBuildVersion" to "13954326"), // current targetSdk
        mapOf("androidVersion" to "16", "frameworkSdkBuildVersion" to "13921718"), // used for `@Config(sdk = Build.VERSION_CODES.BAKLAVA)`
    )

// Base, public task - will be displayed in ./gradlew robolectricDownloader:tasks
val robolectricSdkDownload =
    tasks.register("robolectricSdkDownload") {
        group = "Dependencies"
        description = "Downloads all robolectric SDK dependencies into mavenLocal, for use with offline robolectric"
    }

// Generate the configuration and actual copy tasks.
robolectricAndroidSdkVersions.forEach { robolectricSdkVersion ->
    // the final part of this `-i<number>` comes from PREINSTRUMENTED_VERSION in upstream DefaultSdkProvider
    val version = "${robolectricSdkVersion["androidVersion"]}-robolectric-${robolectricSdkVersion["frameworkSdkBuildVersion"]}-i7"

    // Creating a configuration with a dependency allows Gradle to manage the actual resolution of
    // the jar file
    val sdkConfig = configurations.create(version)
    dependencies.add(version, "org.robolectric:android-all-instrumented:$version")

    // An ArtifactView on the existing configuration gives us the POM alongside the JAR so
    // maven local has both.
    val sdkPomFiles =
        sdkConfig.incoming
            .artifactView {
                attributes {
                    attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, "pom")
                }
                // if the POM can't be resolved, return an empty collection rather than failing the build
                isLenient = true
            }.files

    val mavenLocalFile = File(repositories.mavenLocal().url)
    val mavenRobolectric = File(mavenLocalFile, "org/robolectric/android-all-instrumented/$version")
    // Copying all files downloaded for the created configuration into maven local.
    val copyTask =
        tasks.register<Copy>("robolectricSdkDownload-$version") {
            from(sdkConfig)
            from(sdkPomFiles)
            into(mavenRobolectric)
        }
    robolectricSdkDownload.configure { dependsOn(copyTask) }
}
